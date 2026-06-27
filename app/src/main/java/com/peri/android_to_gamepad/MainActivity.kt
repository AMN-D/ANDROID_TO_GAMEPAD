package com.peri.android_to_gamepad

import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    // Single shared client for the whole app lifecycle
    private val gamepadClient = GamepadClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
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

private enum class Screen {
    GameList,
    Gamepad
}

@Composable
private fun AppNavigation(client: GamepadClient) {
    var currentScreen by remember { mutableStateOf(Screen.GameList) }

    when (currentScreen) {
        Screen.GameList -> GameListScreen(
            onGameSelected = { currentScreen = Screen.Gamepad }
        )
        Screen.Gamepad -> GamepadScreen(
            client = client,
            onBack = { currentScreen = Screen.GameList }
        )
    }
}