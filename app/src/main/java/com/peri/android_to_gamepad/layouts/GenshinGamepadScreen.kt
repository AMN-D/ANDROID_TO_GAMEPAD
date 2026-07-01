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
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.peri.android_to_gamepad.CameraZone
import com.peri.android_to_gamepad.GamepadButton
import com.peri.android_to_gamepad.GamepadClient
import com.peri.android_to_gamepad.JoystickZone
import com.peri.android_to_gamepad.SwipeDPad

@Composable
fun GenshinGamepadScreen(client: GamepadClient, onBack: () -> Unit) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val dimAlpha = 0.3f

    // ── Cleanup on exit ─────────────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            // Release all active buttons and axes to prevent "stuck" inputs
            // when the composable leaves the screen.
            client.sendCommand("ABS_X:0")
            client.sendCommand("ABS_Y:0")
            client.sendCommand("ABS_RX:0")
            client.sendCommand("ABS_RY:0")
            client.sendCommand("ABS_Z:0")
            client.sendCommand("ABS_RZ:0")
            client.sendCommand("ABS_HAT0X:0")
            client.sendCommand("ABS_HAT0Y:0")

            client.sendCommand("BTN_START:0")
            client.sendCommand("BTN_SELECT:0")
            client.sendCommand("BTN_TL:0")
            client.sendCommand("BTN_TR:0")
            client.sendCommand("BTN_WEST:0")
            client.sendCommand("BTN_EAST:0")
            client.sendCommand("BTN_NORTH:0")
            client.sendCommand("BTN_SOUTH:0")
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        // ── Left half (bottom): Joystick ────────────────────────────────────
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

        // ── Right half (full height): Camera ────────────────────────────────
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

        // ── Utility menu buttons (top centre) ───────────────────────────────
        GamepadButton(
            label = "St", accentColor = Color.White.copy(alpha = dimAlpha), diameter = 44.dp,
            onDown = { client.sendCommand("BTN_START:1") },
            onUp   = { client.sendCommand("BTN_START:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .zIndex(1f)
                .offset(x = screenW * 0.43f - 22.dp, y = screenH * 0.08f - 22.dp)
        )
        GamepadButton(
            label = "Sel", accentColor = Color.White.copy(alpha = dimAlpha), diameter = 44.dp,
            onDown = { client.sendCommand("BTN_SELECT:1") },
            onUp   = { client.sendCommand("BTN_SELECT:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .zIndex(1f)
                .offset(x = screenW * 0.50f - 22.dp, y = screenH * 0.08f - 22.dp)
        )
        GamepadButton(
            label = "LB", accentColor = Color.White.copy(alpha = dimAlpha), diameter = 44.dp,
            onDown = { client.sendCommand("BTN_TL:1") },
            onUp   = { client.sendCommand("BTN_TL:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .zIndex(1f)
                .offset(x = screenW * 0.57f - 22.dp, y = screenH * 0.08f - 22.dp)
        )

        // ── SwipeDPad ────────────────────────────────────────────────────────
        SwipeDPad(
            swipeThresholdDp  = 18.dp,
            idleBorderAlpha   = 0.15f,
            accentColor       = Color.White.copy(alpha = dimAlpha),
            onDirectionChange = { x, y ->
                client.sendCommand("ABS_HAT0X:$x")
                client.sendCommand("ABS_HAT0Y:$y")
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f)
        )

        // ── Y ────────────────────────────────────────────────────────────────
        GamepadButton(
            label = "Y", accentColor = Color(0xFFE5C51C).copy(alpha = dimAlpha), diameter = 56.dp,
            onDown = { client.sendCommand("BTN_WEST:1") },
            onUp   = { client.sendCommand("BTN_WEST:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.644f - 28.dp, y = screenH * 0.897f - 28.dp)
        )

        // ── LT ──────────────────────────────────────────────────────────
        GamepadButton(
            label = "LT", accentColor = Color.White.copy(alpha = dimAlpha), diameter = 76.dp,
            onDown   = { client.sendCommand("ABS_Z:255") },
            onUp     = { client.sendCommand("ABS_Z:0")   },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.833f - 38.dp, y = screenH * 0.26f - 38.dp)
        )

        // ── LB + X macro ────────────────────────────────────────────────────
        GamepadButton(
            label = "LB+X", accentColor = Color.White.copy(alpha = dimAlpha), diameter = 44.dp,
            onDown = {
                scope.launch {
                    client.sendCommand("BTN_TL:1")
                    delay(35)
                    client.sendCommand("BTN_NORTH:1")
                }
            },
            onUp = {
                scope.launch {
                    client.sendCommand("BTN_NORTH:0")
                    delay(35)
                    client.sendCommand("BTN_TL:0")
                }
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.76f - 22.dp, y = screenH * 0.16f - 22.dp)
        )

        // ── RT ─────────────────────────────────────────────────────────────
        GamepadButton(
            label = "RT", accentColor = Color.White.copy(alpha = dimAlpha), diameter = 64.dp,
            onDown   = { client.sendCommand("ABS_RZ:255") },
            onUp     = { client.sendCommand("ABS_RZ:0")   },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.7385f - 32.dp, y = screenH * 0.852f - 32.dp)
        )

        // ── B ─────────────────────────────────────────────────────────────
        GamepadButton(
            label = "B", accentColor = Color(0xFFD7263D).copy(alpha = dimAlpha), diameter = 88.dp,
            onDown   = { client.sendCommand("BTN_EAST:1") },
            onUp     = { client.sendCommand("BTN_EAST:0") },
            onUpdate = { x, y ->
                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
            },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.833f - 44.dp, y = screenH * 0.711f - 44.dp)
        )

        // ── X ─────────────────────────────────────────────────────────────
        GamepadButton(
            label = "X", accentColor = Color(0xFF1B64E8).copy(alpha = dimAlpha), diameter = 64.dp,
            onDown = { client.sendCommand("BTN_NORTH:1") },
            onUp   = { client.sendCommand("BTN_NORTH:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 32.dp, y = screenH * 0.388f - 32.dp)
        )

        // ── A ─────────────────────────────────────────────────────────────
        GamepadButton(
            label = "A", accentColor = Color(0xFF28A745).copy(alpha = dimAlpha), diameter = 64.dp,
            onDown = { client.sendCommand("BTN_SOUTH:1") },
            onUp   = { client.sendCommand("BTN_SOUTH:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 32.dp, y = screenH * 0.594f - 32.dp)
        )

        // ── RB ────────────────────────────────────────────────────────────
        GamepadButton(
            label = "RB", accentColor = Color.White.copy(alpha = dimAlpha), diameter = 64.dp,
            onDown = { client.sendCommand("BTN_TR:1") },
            onUp   = { client.sendCommand("BTN_TR:0") },
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 32.dp, y = screenH * 0.800f - 32.dp)
        )
    }
}