package com.peri.android_to_gamepad

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun GamepadScreen(client: GamepadClient, onBack: () -> Unit) {
    BackHandler { onBack() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        // Bottom-left quadrant is entirely the left joystick's touch zone.
        JoystickZone(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.5f),
            onUpdate = { x, y ->
                val mappedX = (x * 32767).toInt()
                val mappedY = (y * 32767).toInt()
                client.sendCommand("ABS_X:$mappedX")
                client.sendCommand("ABS_Y:$mappedY")
            }
        )

        // Y - Elemental Burst
        GamepadButton(
            label = "Y",
            accentColor = Color(0xFFE5C51C),
            diameter = 58.dp,
            onDown = { client.sendCommand("BTN_NORTH:1") },
            onUp = { client.sendCommand("BTN_NORTH:0") },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = screenW * 0.644f - 29.dp, y = screenH * 0.897f - 29.dp)
        )

        // RT - Elemental Skill (was X)
        GamepadButton(
            label = "RT",
            accentColor = Color.White,
            diameter = 58.dp,
            onDown = { client.sendCommand("ABS_RZ:255") },
            onUp = { client.sendCommand("ABS_RZ:0") },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = screenW * 0.724f - 29.dp, y = screenH * 0.852f - 29.dp)
        )

        // B - Normal Attack (was RT)
        GamepadButton(
            label = "B",
            accentColor = Color(0xFFD7263D),
            diameter = 76.dp,
            onDown = { client.sendCommand("BTN_EAST:1") },
            onUp = { client.sendCommand("BTN_EAST:0") },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = screenW * 0.833f - 38.dp, y = screenH * 0.711f - 38.dp)
        )

        // LT - Switch Aiming Mode (was X position, nudged toward Y)
        GamepadButton(
            label = "LT",
            accentColor = Color.White,
            diameter = 58.dp,
            onDown = { client.sendCommand("ABS_Z:255") },
            onUp = { client.sendCommand("ABS_Z:0") },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = screenW * 0.684f - 29.dp, y = screenH * 0.730f - 29.dp)
        )

        // X - Pick Up / Interact (above LT, near where loot prompts appear)
        GamepadButton(
            label = "X",
            accentColor = Color(0xFF1B64E8),
            diameter = 58.dp,
            onDown = { client.sendCommand("BTN_WEST:1") },
            onUp = { client.sendCommand("BTN_WEST:0") },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = screenW * 0.684f - 29.dp, y = screenH * 0.550f - 29.dp)
        )

        // A - Jump
        GamepadButton(
            label = "A",
            accentColor = Color(0xFF28A745),
            diameter = 58.dp,
            onDown = { client.sendCommand("BTN_SOUTH:1") },
            onUp = { client.sendCommand("BTN_SOUTH:0") },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 29.dp, y = screenH * 0.594f - 29.dp)
        )

        // RB - Sprint
        GamepadButton(
            label = "RB",
            accentColor = Color.White,
            diameter = 58.dp,
            onDown = { client.sendCommand("BTN_TR:1") },
            onUp = { client.sendCommand("BTN_TR:0") },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = screenW * 0.934f - 29.dp, y = screenH * 0.800f - 29.dp)
        )
    }
}

@Composable
fun JoystickZone(
    modifier: Modifier = Modifier,
    onUpdate: (x: Float, y: Float) -> Unit
) {
    var zoneSize by remember { mutableStateOf(IntSize.Zero) }
    var baseCenter by remember { mutableStateOf<Offset?>(null) }
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .onSizeChanged { zoneSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val size = zoneSize
                    if (size.width == 0 || size.height == 0) return@awaitEachGesture

                    val baseRadiusPx = minOf(size.width, size.height) * 0.38f
                    val thumbRadiusPx = baseRadiusPx * 0.42f
                    val maxDragPx = baseRadiusPx - thumbRadiusPx

                    val down = awaitFirstDown(requireUnconsumed = false)
                    val clampedCenter = Offset(
                        x = down.position.x.coerceIn(baseRadiusPx, size.width - baseRadiusPx),
                        y = down.position.y.coerceIn(baseRadiusPx, size.height - baseRadiusPx)
                    )
                    baseCenter = clampedCenter
                    thumbOffset = Offset.Zero
                    onUpdate(0f, 0f)

                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break

                        val dx = change.position.x - clampedCenter.x
                        val dy = change.position.y - clampedCenter.y
                        val distance = hypot(dx, dy)
                        val cx = if (distance > maxDragPx) dx * (maxDragPx / distance) else dx
                        val cy = if (distance > maxDragPx) dy * (maxDragPx / distance) else dy

                        thumbOffset = Offset(cx, cy)
                        onUpdate(cx / maxDragPx, cy / maxDragPx)
                        change.consume()
                    }

                    baseCenter = null
                    thumbOffset = Offset.Zero
                    onUpdate(0f, 0f)
                }
            }
    ) {
        if (zoneSize.width > 0 && zoneSize.height > 0) {
            val baseRadiusPx = minOf(zoneSize.width, zoneSize.height) * 0.38f
            val thumbRadiusPx = baseRadiusPx * 0.42f
            val restPadding = baseRadiusPx * 0.25f

            val restCenter = Offset(
                x = baseRadiusPx + restPadding,
                y = zoneSize.height - baseRadiusPx - restPadding
            )
            val isActive = baseCenter != null
            val center = baseCenter ?: restCenter
            val visualAlpha = if (isActive) 1f else 0.4f

            val baseDiameterDp = with(density) { (baseRadiusPx * 2).toDp() }
            val thumbDiameterDp = with(density) { (thumbRadiusPx * 2).toDp() }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - baseRadiusPx).roundToInt(),
                            (center.y - baseRadiusPx).roundToInt()
                        )
                    }
                    .size(baseDiameterDp)
                    .background(Color.White.copy(alpha = 0.08f * visualAlpha), CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.28f * visualAlpha), CircleShape)
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x + thumbOffset.x - thumbRadiusPx).roundToInt(),
                            (center.y + thumbOffset.y - thumbRadiusPx).roundToInt()
                        )
                    }
                    .size(thumbDiameterDp)
                    .background(Color(0xFFE6E6E6).copy(alpha = visualAlpha), CircleShape)
            )
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
    label: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    accentColor: Color = Color.White,
    diameter: Dp = 58.dp,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val fillAlpha by animateFloatAsState(if (pressed) 0.55f else 0.28f, label = "fillAlpha")
    val borderAlpha by animateFloatAsState(if (pressed) 1f else 0.55f, label = "borderAlpha")

    Box(
        modifier = modifier
            .size(diameter)
            .background(Color.Black.copy(alpha = fillAlpha), CircleShape)
            .border(1.5.dp, accentColor.copy(alpha = borderAlpha), CircleShape)
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
        Text(
            text = label,
            color = accentColor,
            fontSize = (diameter.value * 0.34f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}