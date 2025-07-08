package com.example.appprevent.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.example.appprevent.presentation.theme.AppPreventTheme
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class RandomNumberActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppPreventTheme {
                RandomNumberScreen()
            }
        }
    }
}

@Composable
fun RandomNumberScreen() {
    val context = LocalContext.current
    var randomNumber by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()

        while (true) {
            delay(100)
            randomNumber = Random.nextInt(130)

            // Enviar número a todos los nodos conectados (teléfono)
            for (node in nodes) {
                Wearable.getMessageClient(context).sendMessage(
                    node.id,
                    "/random-number",
                    randomNumber.toString().toByteArray()
                ).await()
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(text = "Número: $randomNumber", fontSize = 20.sp)
    }
}
