package com.example.appprevent.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Text
import com.example.appprevent.presentation.theme.AppPreventTheme
import com.google.android.gms.location.*
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlin.math.sqrt
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.ToneGenerator
import android.media.AudioManager
var lastAccelerometerValues = floatArrayOf(0f, 0f, 0f, 0f)

class SpeedActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var locationCallback: LocationCallback? = null
    private var accelerometerListener: SensorEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        setContent {
            AppPreventTheme {
                SpeedScreen(
                    fusedLocationClient = fusedLocationClient,
                    setCallback = { callback -> locationCallback = callback },
                    registerFallDetection = { listener -> accelerometerListener = listener }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        accelerometerListener?.let {
            sensorManager.unregisterListener(it)
        }
    }
}

@Composable
fun SpeedScreen(
    fusedLocationClient: FusedLocationProviderClient,
    setCallback: (LocationCallback) -> Unit,
    registerFallDetection: (SensorEventListener) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var speedKmh by remember { mutableStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationUpdates(context, fusedLocationClient, coroutineScope) { speed, callback ->
                speedKmh = speed
                setCallback(callback)
            }
            registerAccelerometer(context, coroutineScope, registerFallDetection)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates(context, fusedLocationClient, coroutineScope) { speed, callback ->
                speedKmh = speed
                setCallback(callback)
            }
            registerAccelerometer(context, coroutineScope, registerFallDetection)
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(100.dp)
                .background(Color(0xFFEEEEEE), shape = RoundedCornerShape(20.dp))
        ) {
            Text(
                text = "Velocidad:\n${"%.2f".format(speedKmh)} km/h",
                fontSize = 22.sp
            )
        }
    }
}

fun registerAccelerometer(
    context: Context,
    coroutineScope: CoroutineScope,
    register: (SensorEventListener) -> Unit
) {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val shakeThreshold = 20f
    val lowMovementThreshold = 5f
    var fallDetected = false
    var fallTimeMillis = 0L

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                val acceleration = sqrt(x * x + y * y + z * z)
                lastAccelerometerValues = floatArrayOf(x, y, z, acceleration)

                val currentTime = System.currentTimeMillis()

                if (acceleration > shakeThreshold && !fallDetected) {
                    fallDetected = true
                    fallTimeMillis = currentTime
                    Log.d("FallDetection", "⚠️ Posible caída detectada")
                    coroutineScope.launch(Dispatchers.IO) {
                        sendFallDataToPhone(context)
                    }
                } else if (fallDetected && acceleration < lowMovementThreshold) {
                    if (currentTime - fallTimeMillis in 1000..4000) {
                        Log.d("FallDetection", "CAIDA")
                        coroutineScope.launch(Dispatchers.IO) {
                            sendConfirmedFallToPhone(context)
                        }
                        fallDetected = false
                    }
                } else if (fallDetected && currentTime - fallTimeMillis > 5000) {
                    Log.d("FallDetection", "❌ Falsa alarma, no fue caída.")
                    fallDetected = false
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    register(listener)
}

@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
fun startLocationUpdates(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    coroutineScope: CoroutineScope,
    onSpeedChanged: (Float, LocationCallback) -> Unit
) {
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val speedMps = location.speed
                val speedKmh = speedMps * 3.6f
                Log.d("SpeedActivity", "Velocidad: $speedKmh km/h")

                onSpeedChanged(speedKmh, this)

                // 🚨 ALERTA: Si supera 10 km/h, vibrar y sonar
                if (speedKmh > 1f) {
                    triggerVibrationAndSound(context)
                }

                coroutineScope.launch(Dispatchers.IO) {
                    sendSpeedToPhone(context, speedKmh)
                }
            }
        }
    }

    fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
}
fun triggerVibrationAndSound(context: Context) {
    // Vibrar
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
        vm.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }

    // Sonido
    val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
}

suspend fun sendSpeedToPhone(context: Context, speed: Float) {
    try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        val message = "%.2f".format(speed)
        nodes.forEach { node ->
            Wearable.getMessageClient(context).sendMessage(
                node.id,
                "/speed",
                message.toByteArray()
            ).await()
        }
    } catch (e: Exception) {
        Log.e("SpeedActivity", "Error al enviar velocidad al móvil: ${e.message}")
    }
}

suspend fun getCurrentLocation(context: Context): Location? {
    return try {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.await()
    } catch (e: Exception) {
        Log.e("Location", "No se pudo obtener la ubicación actual: ${e.message}")
        null
    }
}

suspend fun sendFallDataToPhone(context: Context) {
    try {
        val location = getCurrentLocation(context)
        val locationText = if (location != null) {
            "CAIDA en lat:${location.latitude}, lon:${location.longitude}"
        } else {
            "CAIDA (ubicación no disponible)"
        }

        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.forEach { node ->
            Wearable.getMessageClient(context).sendMessage(
                node.id,
                "/fall",
                locationText.toByteArray()
            ).await()
        }
    } catch (e: Exception) {
        Log.e("FallDetection", "Error al enviar aviso de posible caída: ${e.message}")
    }
}

suspend fun sendConfirmedFallToPhone(context: Context) {
    try {
        val location = getCurrentLocation(context)
        val locationText = if (location != null) {
            "POSIBLE CAIDA en lat:${location.latitude}, lon:${location.longitude}"
        } else {
            "POSIBLE CAIDA (ubicación no disponible)"
        }

        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.forEach { node ->
            Wearable.getMessageClient(context).sendMessage(
                node.id,
                "/confirmed_fall",
                locationText.toByteArray()
            ).await()
        }
    } catch (e: Exception) {
        Log.e("FallDetection", "Error al enviar aviso de caída confirmada: ${e.message}")
    }
}
