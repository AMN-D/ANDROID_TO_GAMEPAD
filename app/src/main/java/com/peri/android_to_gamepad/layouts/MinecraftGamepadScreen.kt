package com.peri.android_to_gamepad.layouts

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.peri.android_to_gamepad.ui.theme.components.CameraZone
import com.peri.android_to_gamepad.ui.theme.components.GamepadButton
import com.peri.android_to_gamepad.network.GamepadClient
import com.peri.android_to_gamepad.ui.theme.components.JoystickZone
import com.peri.android_to_gamepad.ui.theme.components.StickClickHandler

@Composable
fun MinecraftGamepadScreen(client: GamepadClient, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    BackHandler { onBack() }

    // Immersive mode setup
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

    // Global cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            listOf(
                "ABS_X:0", "ABS_Y:0", "ABS_RX:0", "ABS_RY:0", "ABS_Z:0", "ABS_RZ:0", 
                "ABS_HAT0X:0", "ABS_HAT0Y:0", "BTN_START:0", "BTN_SELECT:0", 
                "BTN_TL:0", "BTN_TR:0", "BTN_WEST:0", "BTN_EAST:0", "BTN_NORTH:0", "BTN_SOUTH:0",
                "BTN_THUMBL:0", "BTN_THUMBR:0"
            ).forEach { client.sendCommand(it) }
        }
    }

    // L3 Handler for Double-Tap-to-Toggle Sprint
    val l3Handler = remember { 
        StickClickHandler(view, onTrigger = { pressed ->
            client.sendCommand("BTN_THUMBL:${if (pressed) 1 else 0}")
        })
    }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenW = maxWidth
        val screenH = maxHeight

        // Left Stick Zone: Movement + L3 Sprint
        JoystickZone(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .border(1.dp, Color.White.copy(0.15f)),
            onClickDown = { l3Handler.handlePress() },
            onClickUp = { l3Handler.handleRelease() },
            onUpdate = { x, y ->
                client.sendCommand("ABS_X:${(x * 32767).toInt()}")
                client.sendCommand("ABS_Y:${(y * 32767).toInt()}")
            }
        )

        // Right Stick Zone: Camera Look + R3 Click
        CameraZone(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .border(1.dp, Color.White.copy(0.15f)),
            blend = 0f, // Force Linear
            sensitivity = 1.5f, // Increased sensitivity
            smoothing = 0.05f, // Even less lag
            maxExpectedDelta = 60f, // Lower threshold to reach max speed faster
            onClickDown = { 
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                client.sendCommand("BTN_THUMBR:1") 
            },
            onClickUp = { client.sendCommand("BTN_THUMBR:0") },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            }
        )

        val dim = Color.White.copy(0.3f)

        // System Buttons: Start (Pause) and Select (Player List)
        GamepadButton("St", { client.sendCommand("BTN_START:1") }, { client.sendCommand("BTN_START:0") }, dim, 44.dp,
            modifier = Modifier.align(Alignment.TopStart).zIndex(1f).offset(screenW * 0.43f - 22.dp, screenH * 0.08f - 22.dp))
        GamepadButton("Sel", { client.sendCommand("BTN_SELECT:1") }, { client.sendCommand("BTN_SELECT:0") }, dim, 44.dp,
            modifier = Modifier.align(Alignment.TopStart).zIndex(1f).offset(screenW * 0.50f - 22.dp, screenH * 0.08f - 22.dp))


        // Face Buttons (Xbox layout)
        // Y: Inventory (Top)
        GamepadButton("Y", { client.sendCommand("BTN_WEST:1") }, { client.sendCommand("BTN_WEST:0") }, Color(0xFFE5C51C).copy(0.3f), 56.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.644f - 28.dp, screenH * 0.897f - 28.dp))

        // RT: Attack (Action button at the "Right" face position)
        GamepadButton("RT", { client.sendCommand("ABS_RZ:255") }, { client.sendCommand("ABS_RZ:0") }, dim, 88.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.833f - 44.dp, screenH * 0.711f - 44.dp))

        // X: Swap Hands (Left)
        GamepadButton("X", { client.sendCommand("BTN_NORTH:1") }, { client.sendCommand("BTN_NORTH:0") }, Color(0xFF1B64E8).copy(0.3f), 64.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.644f - 32.dp, screenH * 0.35f - 32.dp))
        
        // A: Jump (Bottom)
        GamepadButton("A", { client.sendCommand("BTN_SOUTH:1") }, { client.sendCommand("BTN_SOUTH:0") }, Color(0xFF28A745).copy(0.3f), 64.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.934f - 32.dp, screenH * 0.594f - 32.dp))

        // Bumpers: LB=Prev Slot, RB=Next Slot
        GamepadButton("LB", { client.sendCommand("BTN_TL:1") }, { client.sendCommand("BTN_TL:0") }, dim, 44.dp,
            modifier = Modifier.align(Alignment.TopStart).zIndex(1f).offset(screenW * 0.36f - 22.dp, screenH * 0.08f - 22.dp))
        GamepadButton("RB", { client.sendCommand("BTN_TR:1") }, { client.sendCommand("BTN_TR:0") }, dim, 88.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.84f - 44.dp, screenH * 0.16f - 44.dp))

        // Triggers (Digital) & Sneak
        GamepadButton("LT", { client.sendCommand("ABS_Z:255") }, { client.sendCommand("ABS_Z:0") }, dim, 104.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.16f - 52.dp, screenH * 0.16f - 52.dp))

        // B: Sneak (Swapped to the bottom trigger position)
        GamepadButton("B", { client.sendCommand("BTN_EAST:1") }, { client.sendCommand("BTN_EAST:0") }, Color(0xFFD7263D).copy(0.3f), 64.dp,
            modifier = Modifier.align(Alignment.TopStart).offset(screenW * 0.934f - 32.dp, screenH * 0.800f - 32.dp))
    }
}
