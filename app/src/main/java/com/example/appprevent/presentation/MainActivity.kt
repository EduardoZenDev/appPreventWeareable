package com.example.appprevent.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.appprevent.presentation.theme.AppPreventTheme
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

// Variable global para controlar envío de datos
var isSendingData = true

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Evita que la pantalla se apague
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Escuchar mensajes desde el teléfono
        Wearable.getMessageClient(this).addListener { messageEvent: MessageEvent ->
            val message = String(messageEvent.data)
            if (messageEvent.path == "/detener_envio") {
                when (message) {
                    "STOP" -> {
                        DataSenderControl.isSendingData = false
                        android.util.Log.d("MainActivity", "📡 Envío de datos detenido por el teléfono")

                        // Regresar a MainActivity, cerrando actividades superiores
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    "START" -> {
                        DataSenderControl.isSendingData = true
                        android.util.Log.d("MainActivity", "📡 Envío de datos reanudado por el teléfono")
                        // Similar aquí si quieres regresar a MainActivity también
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                }
            }
        }


//
        setContent {
            AppPreventTheme {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2193b0), Color(0xFF6dd5ed))
                            )
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                startActivity(Intent(this@MainActivity, SpeedActivity::class.java))
                            },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("Iniciar")
                        }
                    }
                }
            }
        }
    }
}
