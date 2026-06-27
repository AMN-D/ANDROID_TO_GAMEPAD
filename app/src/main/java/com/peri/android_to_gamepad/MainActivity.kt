package com.peri.android_to_gamepad

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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val gamepadClient = GamepadClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TWEAK 1: Keep the screen on so it doesn't sleep during cutscenes
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()

        // TWEAK 2: Enable Immersive Fullscreen (Hide Battery & Nav Bar)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

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
            client = client,
            onGameSelected = { currentScreen = Screen.Gamepad }
        )
        Screen.Gamepad -> GamepadScreen(
            client = client,
            onBack = { currentScreen = Screen.GameList }
        )
    }
}