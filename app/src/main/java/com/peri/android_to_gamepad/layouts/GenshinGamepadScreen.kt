package com.peri.android_to_gamepad.layouts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.peri.android_to_gamepad.ui.theme.components.CameraZone
import com.peri.android_to_gamepad.ui.theme.components.GamepadButton
import com.peri.android_to_gamepad.network.GamepadClient
import com.peri.android_to_gamepad.ui.theme.components.JoystickZone
import com.peri.android_to_gamepad.ui.theme.components.SwipeDPad

@Composable
fun GenshinGamepadScreen(client: GamepadClient, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            listOf("ABS_X:0", "ABS_Y:0", "ABS_RX:0", "ABS_RY:0", "ABS_Z:0", "ABS_RZ:0", "ABS_HAT0X:0", "ABS_HAT0Y:0",
                "BTN_START:0", "BTN_SELECT:0", "BTN_TL:0", "BTN_TR:0", "BTN_WEST:0", "BTN_EAST:0", "BTN_NORTH:0", "BTN_SOUTH:0"
            ).forEach { client.sendCommand(it) }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenW = maxWidth
        val screenH = maxHeight

        JoystickZone(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.5f).fillMaxHeight(0.5f).border(1.dp, Color.White.copy(0.15f)),
            onUpdate = { x, y ->
                client.sendCommand("ABS_X:${(x * 32767).toInt()}")
                client.sendCommand("ABS_Y:${(y * 32767).toInt()}")
            }
        )

        CameraZone(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.5f).fillMaxHeight().border(1.dp, Color.White.copy(0.15f)),
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            }
        )

        val dim = Color.White.copy(0.3f)
        GamepadButton("St", { client.sendCommand("BTN_START:1") }, { client.sendCommand("BTN_START:0") }, dim, 44.dp,
            modifier = Modifier.align(Alignment.TopStart).zIndex(1f).offset(screenW * 0.43f - 22.dp, screenH * 0.08f - 22.dp))
        GamepadButton("Sel", { client.sendCommand("BTN_SELECT:1") }, { client.sendCommand("BTN_SELECT:0") }, dim, 44.dp,
            modifier = Modifier.align(Alignment.TopStart).zIndex(1f).offset(screenW * 0.50f - 22.dp, screenH * 0.08f - 22.dp))
        GamepadButton("LB", { client.sendCommand("BTN_TL:1") }, { client.sendCommand("BTN_TL:0") }, dim, 44.dp,
            modifier = Modifier.align(Alignment.TopStart).zIndex(1f).offset(screenW * 0.57f - 22.dp, screenH * 0.08f - 22.dp))

        SwipeDPad(
            accentColor = dim,
            onDirectionChange = { x, y ->
                client.sendCommand("ABS_HAT0X:$x")
                client.sendCommand("ABS_HAT0Y:$y")
            },
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.5f).fillMaxHeight(0.5f)
        )

        GamepadButton("Y", { client.sendCommand("BTN_WEST:1") }, { client.sendCommand("BTN_WEST:0") }, Color(0xFFE5C51C).copy(0.3f), 56.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.644f - 28.dp, screenH * 0.897f - 28.dp))

        GamepadButton("LT", { client.sendCommand("ABS_Z:255") }, { client.sendCommand("ABS_Z:0") }, dim, 76.dp,
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.833f - 38.dp, screenH * 0.26f - 38.dp))

        GamepadButton("LB+X", { scope.launch { client.sendCommand("BTN_TL:1"); delay(35); client.sendCommand("BTN_NORTH:1") } },
            { scope.launch { client.sendCommand("BTN_NORTH:0"); delay(35); client.sendCommand("BTN_TL:0") } }, dim, 56.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.735f - 28.dp, screenH * 0.15f - 28.dp))

        GamepadButton("RT", { client.sendCommand("ABS_RZ:255") }, { client.sendCommand("ABS_RZ:0") }, dim, 64.dp,
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.7385f - 32.dp, screenH * 0.852f - 32.dp))

        GamepadButton("B", { client.sendCommand("BTN_EAST:1") }, { client.sendCommand("BTN_EAST:0") }, Color(0xFFD7263D).copy(0.3f), 88.dp,
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.833f - 44.dp, screenH * 0.711f - 44.dp))

        GamepadButton("X", { client.sendCommand("BTN_NORTH:1") }, { client.sendCommand("BTN_NORTH:0") }, Color(0xFF1B64E8).copy(0.3f), 64.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.644f - 32.dp, screenH * 0.35f - 32.dp))

        GamepadButton("A", { client.sendCommand("BTN_SOUTH:1") }, { client.sendCommand("BTN_SOUTH:0") }, Color(0xFF28A745).copy(0.3f), 64.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.934f - 32.dp, screenH * 0.594f - 32.dp))

        GamepadButton("RB", { client.sendCommand("BTN_TR:1") }, { client.sendCommand("BTN_TR:0") }, dim, 64.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.934f - 32.dp, screenH * 0.800f - 32.dp))
    }
}