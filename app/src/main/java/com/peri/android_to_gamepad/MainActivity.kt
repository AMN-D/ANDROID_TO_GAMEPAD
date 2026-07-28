package com.peri.android_to_gamepad

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
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
import com.peri.android_to_gamepad.network.DeviceStorage
import com.peri.android_to_gamepad.network.DiscoveredServer
import com.peri.android_to_gamepad.network.GamepadClient
import com.peri.android_to_gamepad.network.UdpDiscovery
import com.peri.android_to_gamepad.ui.theme.ANDROID_TO_GAMEPADTheme
import com.peri.android_to_gamepad.ui.theme.screens.GameListScreen

class GamepadConnectionManager(
    context: Context,
    private val client: GamepadClient,
    private val storage: DeviceStorage
) {
    private val discovery = UdpDiscovery(context)

    fun startDiscovery(
        timeoutMs: Long = 15_000,
        onFound: (DiscoveredServer, String?) -> Unit,
        onStatus: (ConnectionStatus) -> Unit,
        onTimeout: () -> Unit = {},
    ) {
        onStatus(ConnectionStatus.Connecting)
        discovery.start(
            timeoutMs = timeoutMs,
            onFound = { server -> 
                val pin = storage.getPin(server.name)
                onFound(server, pin)
            },
            onTimeout = onTimeout,
        )
    }

    fun connect(server: DiscoveredServer, pin: String, onStatus: (ConnectionStatus) -> Unit) {
        client.connect(server, pin = pin, onResult = { status ->
            if (status is ConnectionStatus.Authenticated) {
                storage.savePin(server.name, pin)
            } else if (status is ConnectionStatus.Unauthorized) {
                storage.removePin(server.name)
            }
            onStatus(status)
        })
    }

    fun connectManually(ip: String, port: Int = 5005, pin: String = "", onStatus: (ConnectionStatus) -> Unit) {
        discovery.stop()
        client.connect(ip = ip, port = port, pin = pin, onResult = onStatus)
    }

    fun cancelDiscovery() {
        discovery.stop()
    }
}

class MainActivity : ComponentActivity() {
    private val gamepadClient = GamepadClient()
    private lateinit var deviceStorage: DeviceStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceStorage = DeviceStorage(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            ANDROID_TO_GAMEPADTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    AppNavigation(client = gamepadClient, storage = deviceStorage)
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
private fun AppNavigation(client: GamepadClient, storage: DeviceStorage) {
    var selectedProfile by remember { mutableStateOf<com.peri.android_to_gamepad.model.GameProfile?>(null) }

    if (selectedProfile == null) {
        GameListScreen(
            client = client,
            storage = storage,
            profiles = GameProfiles,
            onGameSelected = { profile -> selectedProfile = profile }
        )
    } else {
        selectedProfile!!.layout(client) {
            selectedProfile = null
        }
    }
}