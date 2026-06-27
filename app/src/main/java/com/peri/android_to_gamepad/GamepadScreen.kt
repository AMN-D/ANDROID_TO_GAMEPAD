package com.peri.android_to_gamepad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun GamepadScreen(client: GamepadClient, onBack: () -> Unit) {
    var statusText by remember { mutableStateOf("Ready to Connect") }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GamepadButton("LT", { client.sendCommand("ABS_Z:255") }, { client.sendCommand("ABS_Z:0") }, size = 45.dp, width = 80.dp)
                GamepadButton("LB", { client.sendCommand("BTN_TL:1") }, { client.sendCommand("BTN_TL:0") }, size = 45.dp, width = 80.dp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GamepadButton("Sel", { client.sendCommand("BTN_SELECT:1") }, { client.sendCommand("BTN_SELECT:0") }, size = 40.dp, width = 60.dp)
                GamepadButton("Start", { client.sendCommand("BTN_START:1") }, { client.sendCommand("BTN_START:0") }, size = 40.dp, width = 60.dp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GamepadButton("RB", { client.sendCommand("BTN_TR:1") }, { client.sendCommand("BTN_TR:0") }, size = 45.dp, width = 80.dp)
                GamepadButton("RT", { client.sendCommand("ABS_RZ:255") }, { client.sendCommand("ABS_RZ:0") }, size = 45.dp, width = 80.dp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GamepadButton("↑", { client.sendCommand("ABS_HAT0Y:-1") }, { client.sendCommand("ABS_HAT0Y:0") }, size = 45.dp)
                    Row {
                        GamepadButton("←", { client.sendCommand("ABS_HAT0X:-1") }, { client.sendCommand("ABS_HAT0X:0") }, size = 45.dp)
                        Spacer(modifier = Modifier.width(45.dp))
                        GamepadButton("→", { client.sendCommand("ABS_HAT0X:1") }, { client.sendCommand("ABS_HAT0X:0") }, size = 45.dp)
                    }
                    GamepadButton("↓", { client.sendCommand("ABS_HAT0Y:1") }, { client.sendCommand("ABS_HAT0Y:0") }, size = 45.dp)
                }

                Joystick(
                    size = 130.dp,
                    onUpdate = { x, y ->
                        val mappedX = (x * 32767).toInt()
                        val mappedY = (y * 32767).toInt()
                        client.sendCommand("ABS_X:$mappedX")
                        client.sendCommand("ABS_Y:$mappedY")
                    }
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = statusText, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                Button(onClick = {
                    statusText = "Connecting..."
                    client.connect { result -> statusText = result }
                }) {
                    Text("Connect")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Joystick(
                    size = 130.dp,
                    onUpdate = { x, y ->
                        val mappedX = (x * 32767).toInt()
                        val mappedY = (y * 32767).toInt()
                        client.sendCommand("ABS_RX:$mappedX")
                        client.sendCommand("ABS_RY:$mappedY")
                    }
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GamepadButton("Y", { client.sendCommand("BTN_NORTH:1") }, { client.sendCommand("BTN_NORTH:0") }, Color(0xFFE5C51C), size = 55.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(25.dp)) {
                        GamepadButton("X", { client.sendCommand("BTN_WEST:1") }, { client.sendCommand("BTN_WEST:0") }, Color(0xFF1B64E8), size = 55.dp)
                        GamepadButton("B", { client.sendCommand("BTN_EAST:1") }, { client.sendCommand("BTN_EAST:0") }, Color(0xFFD62A2A), size = 55.dp)
                    }
                    GamepadButton("A", { client.sendCommand("BTN_SOUTH:1") }, { client.sendCommand("BTN_SOUTH:0") }, Color(0xFF28A745), size = 55.dp)
                }
            }
        }
    }
}

@Composable
fun Joystick(
    onUpdate: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val radiusPx = with(density) { (size / 2).toPx() }
    val thumbRadiusPx = with(density) { 25.dp.toPx() }
    val maxDragPx = radiusPx - thumbRadiusPx

    fun updateFromRawPosition(rawX: Float, rawY: Float) {
        val x = rawX - radiusPx
        val y = rawY - radiusPx
        val distance = hypot(x, y)
        val clampedX = if (distance > maxDragPx) x * (maxDragPx / distance) else x
        val clampedY = if (distance > maxDragPx) y * (maxDragPx / distance) else y
        thumbOffset = Offset(clampedX, clampedY)
        onUpdate(clampedX / maxDragPx, clampedY / maxDragPx)
    }

    Box(
        modifier = modifier
            .size(size)
            .background(Color.DarkGray, CircleShape)
            // Native Compose pointer input: this gesture is scoped to this composable's
            // own pointer id, so it doesn't interfere with touches on other buttons/sticks.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateFromRawPosition(down.position.x, down.position.y)

                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        updateFromRawPosition(change.position.x, change.position.y)
                        change.consume()
                    }

                    thumbOffset = Offset.Zero
                    onUpdate(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(50.dp)
                .background(Color.LightGray, CircleShape)
        )
    }
}

@Composable
fun GamepadButton(
    text: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    baseColor: Color = Color.DarkGray,
    size: Dp = 75.dp,
    width: Dp = size,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(width = width, height = size)
            .background(if (pressed) Color.LightGray else baseColor, shape = RoundedCornerShape(50))
            // Each button tracks its own pointer independently, so holding this
            // button doesn't block presses on any other button or joystick.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onDown()

                    waitForUpOrCancellation()

                    pressed = false
                    onUp()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}