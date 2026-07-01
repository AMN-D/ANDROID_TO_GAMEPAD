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

class MainActivity : ComponentActivity() {
    // Retained across orientation changes because of configChanges in AndroidManifest.xml
    private val gamepadClient = GamepadClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen awake while using the gamepad
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
        // Safely disconnect when the app is completely closed
        gamepadClient.disconnect()
    }
}

@Composable
private fun AppNavigation(client: GamepadClient) {
    // Holds the currently active profile.
    // Will not be reset on rotation because Manifest prevents Activity recreation.
    var selectedProfile by remember { mutableStateOf<GameProfile?>(null) }

    if (selectedProfile == null) {
        GameListScreen(
            client = client,
            profiles = GameProfiles,
            onGameSelected = { profile -> selectedProfile = profile }
        )
    } else {
        // Safe unwrap is fine here because of the null check above
        selectedProfile!!.layout(client) {
            // onBack callback triggers this to return to the game list
            selectedProfile = null
        }
    }
}