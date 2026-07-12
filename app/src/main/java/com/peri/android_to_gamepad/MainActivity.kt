package com.peri.android_to_gamepad

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.peri.android_to_gamepad.model.GameProfile
import com.peri.android_to_gamepad.model.GameProfiles
import com.peri.android_to_gamepad.network.ConnectionStatus
import com.peri.android_to_gamepad.network.GamepadClient
import com.peri.android_to_gamepad.network.UdpDiscovery
import com.peri.android_to_gamepad.ui.theme.screens.GameListScreen

class GamepadConnectionManager(
    context: Context,
    private val client: GamepadClient,
) {
    private val discovery = UdpDiscovery(context)

    fun startAutoConnect(
        timeoutMs: Long = 15_000,
        onStatus: (ConnectionStatus) -> Unit,
        onTimeout: () -> Unit = {},
    ) {
        onStatus(ConnectionStatus.Connecting)
        discovery.start(
            timeoutMs = timeoutMs,
            onFound = { server -> client.connect(server, onResult = onStatus) },
            onTimeout = onTimeout,
        )
    }

    fun connectManually(ip: String, port: Int = 5005, onStatus: (ConnectionStatus) -> Unit) {
        discovery.stop()
        client.connect(ip = ip, port = port, onResult = onStatus)
    }

    fun cancelDiscovery() {
        discovery.stop()
    }
}

class MainActivity : ComponentActivity() {
    private val gamepadClient = GamepadClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    AppNavigation(client = gamepadClient)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gamepadClient.disconnect()
    }
}

@Composable
private fun AppNavigation(client: GamepadClient) {
    var selectedProfile by remember { mutableStateOf<com.peri.android_to_gamepad.model.GameProfile?>(null) }

    if (selectedProfile == null) {
        GameListScreen(
            client = client,
            profiles = GameProfiles,
            onGameSelected = { profile -> selectedProfile = profile }
        )
    } else {
        selectedProfile!!.layout(client) {
            selectedProfile = null
        }
    }
}