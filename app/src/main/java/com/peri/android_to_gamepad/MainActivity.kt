package com.peri.android_to_gamepad

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Socket

// --- 1. THE SOCKET MANAGER ---
// This handles the low-latency TCP connection over the ADB reverse bridge
class GamepadClient {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    fun connect(onResult: (String) -> Unit) {
        // Run on a background thread (IO) so we don't freeze the UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 127.0.0.1 connects to the phone itself, which ADB routes to the PC
                socket = Socket("127.0.0.1", 5005)
                socket?.tcpNoDelay = true // Disables Nagle's algorithm for MAXIMUM speed
                outputStream = socket?.getOutputStream()
                onResult("Connected to PC!")
            } catch (e: Exception) {
                onResult("Failed: ${e.message}")
            }
        }
    }

    fun sendCommand(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send the command string followed by a newline character
                outputStream?.write(("$command\n").toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                // Silently ignore drops to prevent input stuttering
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {}
    }
}

// --- 2. THE MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    private val client = GamepadClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    GamepadApp(client)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.disconnect()
    }
}

// --- 3. THE UI LAYOUT ---
@Composable
fun GamepadApp(client: GamepadClient) {
    var statusText by remember { mutableStateOf("Ready to Connect") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT SIDE: D-PAD
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GamepadButton("↑", { client.sendCommand("KEY_UP:1") }, { client.sendCommand("KEY_UP:0") })
            Row {
                GamepadButton("←", { client.sendCommand("KEY_LEFT:1") }, { client.sendCommand("KEY_LEFT:0") })
                Spacer(modifier = Modifier.width(70.dp))
                GamepadButton("→", { client.sendCommand("KEY_RIGHT:1") }, { client.sendCommand("KEY_RIGHT:0") })
            }
            GamepadButton("↓", { client.sendCommand("KEY_DOWN:1") }, { client.sendCommand("KEY_DOWN:0") })
        }

        // CENTER: STATUS & CONNECT BUTTON
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = statusText, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
            Button(onClick = {
                statusText = "Connecting..."
                client.connect { result -> statusText = result }
            }) {
                Text("Connect")
            }
        }

        // RIGHT SIDE: ACTION BUTTONS (Matching your Python Script)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GamepadButton("Y", { client.sendCommand("KEY_I:1") }, { client.sendCommand("KEY_I:0") }, Color(0xFFE5C51C))
            Row {
                GamepadButton("X", { client.sendCommand("KEY_J:1") }, { client.sendCommand("KEY_J:0") }, Color(0xFF1B64E8))
                Spacer(modifier = Modifier.width(70.dp))
                GamepadButton("B", { client.sendCommand("KEY_L:1") }, { client.sendCommand("KEY_L:0") }, Color(0xFFD62A2A))
            }
            GamepadButton("A", { client.sendCommand("KEY_K:1") }, { client.sendCommand("KEY_K:0") }, Color(0xFF28A745))
        }
    }
}

// --- 4. THE CUSTOM TOUCH BUTTON ---
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadButton(
    text: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    baseColor: Color = Color.DarkGray,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(75.dp)
            .background(if (pressed) Color.LightGray else baseColor, shape = CircleShape)
            // pointerInteropFilter gives us raw screen touch events with zero delay
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        pressed = true
                        onDown()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        onUp()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}