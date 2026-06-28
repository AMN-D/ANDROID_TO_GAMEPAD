package com.peri.android_to_gamepad.layouts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.peri.android_to_gamepad.CameraZone
import com.peri.android_to_gamepad.GamepadButton
import com.peri.android_to_gamepad.GamepadClient
import com.peri.android_to_gamepad.JoystickZone

@Composable
fun GenshinGamepadScreen(client: GamepadClient, onBack: () -> Unit) {
    BackHandler { onBack() }

    // Coroutine scope specifically for handling button timing macros
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        // Left half (bottom): Joystick Zone with dim border
        JoystickZone(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
                .border(1.dp, Color.White.copy(alpha = 0.15f)),
            onUpdate = { x, y ->
                client.sendCommand("ABS_X:${(x * 32767).toInt()}")
                client.sendCommand("ABS_Y:${(y * 32767).toInt()}")
            }
        )

        // Right half (full height): Camera Zone with dim border
        CameraZone(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .border(1.dp, Color.White.copy(alpha = 0.15f)),
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            }
        )

        // --- UTILITY MENU BUTTONS (Moved to Top Center) ---

        GamepadButton(
            label = "St", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("BTN_START:1") },
            onUp = { client.sendCommand("BTN_START:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.43f - 22.dp, y = screenH * 0.08f - 22.dp)
        )

        GamepadButton(
            label = "Sel", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("BTN_SELECT:1") },
            onUp = { client.sendCommand("BTN_SELECT:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.50f - 22.dp, y = screenH * 0.08f - 22.dp)
        )

        GamepadButton(
            label = "LB", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("BTN_TL:1") },
            onUp = { client.sendCommand("BTN_TL:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.57f - 22.dp, y = screenH * 0.08f - 22.dp)
        )

        // --- CHARACTER SWITCHING (2x2 Grid) ---
        // Right column is locked to 0.934f to align exactly above the X button.
        // Grid spacing uses strict dp offsets (60.dp gap) for a perfect visual square.

        GamepadButton(
            label = "↑", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_HAT0Y:-1") },
            onUp = { client.sendCommand("ABS_HAT0Y:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 82.dp, y = screenH * 0.28f - 82.dp)
        )

        GamepadButton(
            label = "→", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_HAT0X:1") },
            onUp = { client.sendCommand("ABS_HAT0X:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 22.dp, y = screenH * 0.28f - 82.dp)
        )

        GamepadButton(
            label = "←", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_HAT0X:-1") },
            onUp = { client.sendCommand("ABS_HAT0X:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 82.dp, y = screenH * 0.28f - 22.dp)
        )

        GamepadButton(
            label = "↓", accentColor = Color.White, diameter = 44.dp,
            onDown = { client.sendCommand("ABS_HAT0Y:1") },
            onUp = { client.sendCommand("ABS_HAT0Y:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 22.dp, y = screenH * 0.28f - 22.dp)
        )

        // --- MAIN ACTION BUTTONS ---

        GamepadButton(
            label = "Y", accentColor = Color(0xFFE5C51C), diameter = 56.dp,
            onDown = { client.sendCommand("BTN_WEST:1") },
            onUp = { client.sendCommand("BTN_WEST:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.644f - 28.dp, y = screenH * 0.897f - 28.dp)
        )

        // LT — draggable for aiming (Moved to Top Left)
        GamepadButton(
            label = "LT", accentColor = Color.White, diameter = 76.dp,
            onDown = { client.sendCommand("ABS_Z:255") },
            onUp = { client.sendCommand("ABS_Z:0") },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.10f - 38.dp, y = screenH * 0.26f - 38.dp)
        )

        // LB + X combo — (Y axis aligned with top D-Pad row)
        GamepadButton(
            label = "LB+X", accentColor = Color.White, diameter = 44.dp,
            onDown = {
                scope.launch {
                    client.sendCommand("BTN_TL:1")    // 1. Press LB modifier
                    delay(35)                         // 2. Wait ~2 game frames
                    client.sendCommand("BTN_NORTH:1") // 3. Press X action
                }
            },
            onUp = {
                scope.launch {
                    client.sendCommand("BTN_NORTH:0") // 1. Release X first
                    delay(35)                         // 2. Wait
                    client.sendCommand("BTN_TL:0")    // 3. Release LB
                }
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.20f - 22.dp, y = screenH * 0.28f - 82.dp)
        )

        // RT — draggable for aiming (Moved slightly right to be exactly centered between Y and B)
        GamepadButton(
            label = "RT", accentColor = Color.White, diameter = 64.dp,
            onDown = { client.sendCommand("ABS_RZ:255") },
            onUp = { client.sendCommand("ABS_RZ:0") },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.7385f - 32.dp, y = screenH * 0.852f - 32.dp)
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

        // X — (Moved exactly between ↓ and A)
        GamepadButton(
            label = "X", accentColor = Color(0xFF1B64E8), diameter = 64.dp,
            onDown = { client.sendCommand("BTN_NORTH:1") },
            onUp = { client.sendCommand("BTN_NORTH:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 32.dp, y = screenH * 0.388f - 32.dp)
        )

        // A
        GamepadButton(
            label = "A", accentColor = Color(0xFF28A745), diameter = 64.dp,
            onDown = { client.sendCommand("BTN_SOUTH:1") },
            onUp = { client.sendCommand("BTN_SOUTH:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 32.dp, y = screenH * 0.594f - 32.dp)
        )

        // RB
        GamepadButton(
            label = "RB", accentColor = Color.White, diameter = 64.dp,
            onDown = { client.sendCommand("BTN_TR:1") },
            onUp = { client.sendCommand("BTN_TR:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 32.dp, y = screenH * 0.800f - 32.dp)
        )
    }
}