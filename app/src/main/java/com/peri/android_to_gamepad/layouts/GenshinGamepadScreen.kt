package com.peri.android_to_gamepad.layouts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.peri.android_to_gamepad.CameraZone
import com.peri.android_to_gamepad.GamepadButton
import com.peri.android_to_gamepad.GamepadClient
import com.peri.android_to_gamepad.JoystickZone

@Composable
fun GenshinGamepadScreen(client: GamepadClient, onBack: () -> Unit) {
    BackHandler { onBack() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        JoystickZone(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f),
            onUpdate = { x, y ->
                client.sendCommand("ABS_X:${(x * 32767).toInt()}")
                client.sendCommand("ABS_Y:${(y * 32767).toInt()}")
            }
        )

        CameraZone(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(),
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            }
        )

        // --- UTILITY MENU BUTTONS ---

        GamepadButton(
            label = "St", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("BTN_START:1") },
            onUp = { client.sendCommand("BTN_START:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.06f - 22.dp, y = screenH * 0.12f - 22.dp)
        )

        GamepadButton(
            label = "Sel", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("BTN_SELECT:1") },
            onUp = { client.sendCommand("BTN_SELECT:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.13f - 22.dp, y = screenH * 0.12f - 22.dp)
        )

        GamepadButton(
            label = "LB", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("BTN_TL:1") },
            onUp = { client.sendCommand("BTN_TL:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 22.dp, y = screenH * 0.12f - 22.dp)
        )

        // --- CHARACTER SWITCHING ---

        GamepadButton(
            label = "↑", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_HAT0Y:-1") },
            onUp = { client.sendCommand("ABS_HAT0Y:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.72f - 22.dp, y = screenH * 0.26f - 22.dp)
        )

        GamepadButton(
            label = "→", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_HAT0X:1") },
            onUp = { client.sendCommand("ABS_HAT0X:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.79f - 22.dp, y = screenH * 0.26f - 22.dp)
        )

        GamepadButton(
            label = "←", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_HAT0X:-1") },
            onUp = { client.sendCommand("ABS_HAT0X:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.86f - 22.dp, y = screenH * 0.26f - 22.dp)
        )

        GamepadButton(
            label = "↓", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_HAT0Y:1") },
            onUp = { client.sendCommand("ABS_HAT0Y:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.93f - 22.dp, y = screenH * 0.26f - 22.dp)
        )

        // --- MAIN ACTION BUTTONS ---

        GamepadButton(
            label = "Y", accentColor = Color(0xFFE5C51C), diameter = 56.dp,
            onDown = { client.sendCommand("BTN_WEST:1") },
            onUp = { client.sendCommand("BTN_WEST:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.644f - 28.dp, y = screenH * 0.897f - 28.dp)
        )

        // RT — draggable for aiming
        GamepadButton(
            label = "RT", accentColor = Color.White, diameter = 64.dp,
            onDown = { client.sendCommand("ABS_RZ:255") },
            onUp = { client.sendCommand("ABS_RZ:0") },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.724f - 32.dp, y = screenH * 0.852f - 32.dp)
        )

        // B — draggable for aiming
        GamepadButton(
            label = "B", accentColor = Color(0xFFD7263D), diameter = 88.dp,
            onDown = { client.sendCommand("BTN_EAST:1") },
            onUp = { client.sendCommand("BTN_EAST:0") },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.833f - 44.dp, y = screenH * 0.711f - 44.dp)
        )

        // LT — draggable for aiming
        GamepadButton(
            label = "LT", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_Z:255") },
            onUp = { client.sendCommand("ABS_Z:0") },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.684f - 22.dp, y = screenH * 0.700f - 22.dp)
        )

        GamepadButton(
            label = "X", accentColor = Color(0xFF1B64E8), diameter = 44.dp,
            onDown = { client.sendCommand("BTN_NORTH:1") },
            onUp = { client.sendCommand("BTN_NORTH:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.660f - 22.dp, y = screenH * 0.560f - 22.dp)
        )

        GamepadButton(
            label = "A", accentColor = Color(0xFF28A745), diameter = 64.dp,
            onDown = { client.sendCommand("BTN_SOUTH:1") },
            onUp = { client.sendCommand("BTN_SOUTH:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 32.dp, y = screenH * 0.594f - 32.dp)
        )

        GamepadButton(
            label = "RB", accentColor = Color.White, diameter = 64.dp,
            onDown = { client.sendCommand("BTN_TR:1") },
            onUp = { client.sendCommand("BTN_TR:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 32.dp, y = screenH * 0.800f - 32.dp)
        )
    }
}