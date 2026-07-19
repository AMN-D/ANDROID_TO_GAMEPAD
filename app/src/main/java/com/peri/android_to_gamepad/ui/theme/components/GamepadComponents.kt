package com.peri.android_to_gamepad.ui.theme.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val STICK_DEADZONE  = 0.08f
private const val STICK_SNAP      = 0.95f
private const val DELTA_THRESHOLD = 0.012f
private val DimWhite = Color.White.copy(0.7f)

private fun applyStickCurve(rawX: Float, rawY: Float): Pair<Float, Float> {
    val magnitude = hypot(rawX, rawY).coerceIn(0f, 1f)
    if (magnitude < STICK_DEADZONE) return Pair(0f, 0f)
    val normalized = (magnitude - STICK_DEADZONE) / (1f - STICK_DEADZONE)
    val curved = normalized * normalized
    val final = if (curved > STICK_SNAP) 1f else curved
    val scale = final / magnitude
    return Pair((rawX * scale).coerceIn(-1f, 1f), (rawY * scale).coerceIn(-1f, 1f))
}

@Composable
fun JoystickVisual(
    modifier: Modifier = Modifier,
    baseDiameter: Dp,
    thumbDiameter: Dp,
    thumbOffset: Offset,
    alpha: Float = 1f,
    isEditing: Boolean = false
) {
    val finalAlpha = if (isEditing) 0.8f else alpha
    Box(modifier = modifier.size(baseDiameter).alpha(finalAlpha).background(Color.White.copy(if(isEditing) 0.15f else 0.08f), CircleShape).border(1.5.dp, DimWhite.copy(if(isEditing) 0.5f else 0.28f), CircleShape)) {
        Box(modifier = Modifier.offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }.size(thumbDiameter).background(if(isEditing) DimWhite else Color(0xFFE6E6E6).copy(0.7f), CircleShape))
    }
}

@Composable
fun ButtonVisual(
    modifier: Modifier = Modifier,
    label: String,
    diameter: Dp,
    accentColor: Color,
    fillAlpha: Float,
    borderAlpha: Float,
    scale: Float,
    textAlpha: Float,
    isEditing: Boolean = false
) {
    val bAlpha = if (isEditing) 0.6f else borderAlpha
    val fAlpha = if (isEditing) 0.2f else fillAlpha
    val tAlpha = if (isEditing) 0.8f else textAlpha
    
    Box(
        modifier = modifier.size(diameter).graphicsLayer { scaleX = scale; scaleY = scale }.background(Color.Black.copy(fAlpha), CircleShape).border(1.5.dp, DimWhite.copy(bAlpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = DimWhite.copy(tAlpha), fontSize = (diameter.value * 0.34f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SwipeVisual(
    modifier: Modifier = Modifier,
    accentColor: Color,
    borderAlpha: Float,
    textAlpha: Float,
    textScale: Float,
    displayText: String,
    isEditing: Boolean = false
) {
    val bAlpha = if (isEditing) 0.5f else borderAlpha
    val tAlpha = if (isEditing) 0.7f else textAlpha
    Box(modifier = modifier.border(1.dp, DimWhite.copy(bAlpha)).background(if(isEditing) Color.White.copy(0.05f) else Color.Transparent), contentAlignment = Alignment.Center) {
        Text(text = displayText, color = DimWhite.copy(tAlpha), modifier = Modifier.graphicsLayer { scaleX = textScale; scaleY = textScale }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CameraVisual(modifier: Modifier = Modifier, alpha: Float = 0.2f, isEditing: Boolean = false) {
    val finalAlpha = if (isEditing) 0.5f else alpha
    Box(
        modifier = modifier.fillMaxSize().border(1.dp, DimWhite.copy(finalAlpha)).background(Color.White.copy(finalAlpha / 4)),
        contentAlignment = Alignment.Center
    ) {
        Text("CAM", color = DimWhite.copy(finalAlpha), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DPadVisual(modifier: Modifier = Modifier, isEditing: Boolean = false) {
    val alpha = if (isEditing) 0.5f else 0.2f
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().border(1.dp, DimWhite.copy(alpha)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(8.dp).background(DimWhite.copy(alpha)))
            Row {
                Box(modifier = Modifier.size(8.dp).background(DimWhite.copy(alpha)))
                Spacer(modifier = Modifier.size(8.dp))
                Box(modifier = Modifier.size(8.dp).background(DimWhite.copy(alpha)))
            }
            Box(modifier = Modifier.size(8.dp).background(DimWhite.copy(alpha)))
        }
    }
}

@Composable
fun PaddleVisual(label: String, isEditing: Boolean = false) {
    val alpha = if (isEditing) 0.5f else 0.2f
    Box(
        modifier = Modifier.size(24.dp, 40.dp).background(Color.Black.copy(0.1f), RoundedCornerShape(4.dp)).border(1.dp, DimWhite.copy(alpha), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = DimWhite.copy(alpha), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CameraZone(modifier: Modifier = Modifier, sensitivity: Float = 0.200f, onUpdate: (x: Float, y: Float) -> Unit) {
    Box(modifier = modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(true); down.consume(); onUpdate(0f, 0f)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val outX = ((change.position.x - change.previousPosition.x) * sensitivity).coerceIn(-1f, 1f)
                val outY = ((change.position.y - change.previousPosition.y) * sensitivity).coerceIn(-1f, 1f)
                if (abs(outX) > DELTA_THRESHOLD || abs(outY) > DELTA_THRESHOLD) onUpdate(outX, outY)
                change.consume()
            }
            onUpdate(0f, 0f)
        }
    })
}

@Composable
fun JoystickZone(modifier: Modifier = Modifier, baseRadiusFraction: Float = 0.38f, thumbSizeRatio: Float = 0.42f, restPaddingRatio: Float = 0.25f, isRightSide: Boolean = false, isEditing: Boolean = false, onUpdate: (x: Float, y: Float) -> Unit) {
    var zoneSize by remember { mutableStateOf(IntSize.Zero) }
    var baseCenter by remember { mutableStateOf<Offset?>(null) }
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(modifier = modifier.onSizeChanged { zoneSize = it }.pointerInput(isEditing) {
        if (isEditing) return@pointerInput
        awaitEachGesture {
            val size = zoneSize
            if (size.width == 0 || size.height == 0) return@awaitEachGesture
            val baseRadiusPx = minOf(size.width, size.height) * baseRadiusFraction
            val thumbRadiusPx = baseRadiusPx * thumbSizeRatio
            val maxDragPx = baseRadiusPx - thumbRadiusPx
            val down = awaitFirstDown(true)
            val clampedCenter = Offset(down.position.x.coerceIn(baseRadiusPx, size.width - baseRadiusPx), down.position.y.coerceIn(baseRadiusPx, size.height - baseRadiusPx))
            baseCenter = clampedCenter; thumbOffset = Offset.Zero; onUpdate(0f, 0f)
            var lastX = 0f; var lastY = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - clampedCenter.x; val dy = change.position.y - clampedCenter.y
                val dist = hypot(dx, dy)
                val cx = if (dist > maxDragPx) dx * (maxDragPx / dist) else dx
                val cy = if (dist > maxDragPx) dy * (maxDragPx / dist) else dy
                thumbOffset = Offset(cx, cy)
                val (outX, outY) = applyStickCurve(cx / maxDragPx, cy / maxDragPx)
                if (abs(outX - lastX) > DELTA_THRESHOLD || abs(outY - lastY) > DELTA_THRESHOLD) { onUpdate(outX, outY); lastX = outX; lastY = outY }
                change.consume()
            }
            baseCenter = null; thumbOffset = Offset.Zero; onUpdate(0f, 0f)
        }
    }) {
        if (zoneSize.width > 0 && zoneSize.height > 0) {
            val baseRadiusPx = minOf(zoneSize.width, zoneSize.height) * baseRadiusFraction
            val thumbRadiusPx = baseRadiusPx * thumbSizeRatio
            val restX = if (isRightSide) zoneSize.width - baseRadiusPx - (baseRadiusPx * restPaddingRatio) else baseRadiusPx + (baseRadiusPx * restPaddingRatio)
            val restCenter = Offset(restX, zoneSize.height - baseRadiusPx - (baseRadiusPx * restPaddingRatio))
            val center = baseCenter ?: restCenter
            val baseDp = with(density) { (baseRadiusPx * 2).toDp() }
            val thumbDp = with(density) { (thumbRadiusPx * 2).toDp() }
            JoystickVisual(
                modifier = Modifier.offset { IntOffset((center.x - baseRadiusPx).roundToInt(), (center.y - baseRadiusPx).roundToInt()) },
                baseDiameter = baseDp, thumbDiameter = thumbDp,
                thumbOffset = Offset(thumbOffset.x + baseRadiusPx - thumbRadiusPx, thumbOffset.y + baseRadiusPx - thumbRadiusPx),
                alpha = if (baseCenter != null) 1f else 0.4f,
                isEditing = isEditing
            )
        }
    }
}

@Composable
fun GamepadButton(label: String, onDown: () -> Unit, onUp: () -> Unit, accentColor: Color = Color.White, diameter: Dp = 58.dp, onUpdate: ((x: Float, y: Float) -> Unit)? = null, maxDragDp: Dp = 80.dp, isEditing: Boolean = false, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val fillAlpha by animateFloatAsState(if (pressed) 0.35f else 0.10f, label = "fA")
    val borderAlpha by animateFloatAsState(if (pressed) 0.60f else 0.25f, label = "bA")
    val scale by animateFloatAsState(if (pressed) 0.91f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "s")
    val view = LocalView.current; val density = LocalDensity.current
    val haptic = if (diameter > 70.dp) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.KEYBOARD_TAP

    ButtonVisual(
        modifier = modifier.pointerInput(onUpdate, isEditing) {
            if (isEditing) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(false); down.consume(); pressed = true
                view.performHapticFeedback(haptic, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); onDown()
                if (onUpdate != null) {
                    val maxPx = with(density) { maxDragDp.toPx() }
                    var sx = down.position.x; var sy = down.position.y
                    var lx = 0f; var ly = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        var dx = change.position.x - sx; var dy = change.position.y - sy; val dist = hypot(dx, dy)
                        if (dist > maxPx && dist > 0) { val r = (dist - maxPx) / dist; sx += dx * r; sy += dy * r; dx = change.position.x - sx; dy = change.position.y - sy }
                        val ox = (dx / maxPx).coerceIn(-1f, 1f); val oy = (dy / maxPx).coerceIn(-1f, 1f)
                        if (abs(ox - lx) > DELTA_THRESHOLD || abs(oy - ly) > DELTA_THRESHOLD) { onUpdate(ox, oy); lx = ox; ly = oy }
                        change.consume()
                    }
                    onUpdate(0f, 0f)
                } else waitForUpOrCancellation()?.consume()
                pressed = false; onUp()
            }
        },
        label = label, diameter = diameter, accentColor = DimWhite, fillAlpha = fillAlpha, borderAlpha = borderAlpha, scale = scale, textAlpha = if (pressed) 1f else 0.4f, isEditing = isEditing
    )
}

enum class DPadDirection { NONE, UP, RIGHT, DOWN, LEFT }

@Composable
fun SwipeDPad(modifier: Modifier = Modifier, swipeThresholdDp: Dp = 18.dp, idleBorderAlpha: Float = 0.10f, accentColor: Color = Color.White, isEditing: Boolean = false, onDirectionChange: (x: Int, y: Int) -> Unit) {
    val density = LocalDensity.current; val scope = rememberCoroutineScope(); val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(DPadDirection.NONE) }
    val bA by animateFloatAsState(if (pressed) 0.45f else idleBorderAlpha, label = "b")
    val tA by animateFloatAsState(if (!pressed) 0.13f else if (active != DPadDirection.NONE) 0.90f else 0.40f, label = "tA")
    val tS by animateFloatAsState(if (pressed && active != DPadDirection.NONE) 1.50f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "tS")
    val txt = when (active) { DPadDirection.UP -> "↑"; DPadDirection.DOWN -> "↓"; DPadDirection.LEFT -> "←"; DPadDirection.RIGHT -> "→"; else -> if (pressed) "•" else "⊕" }

    SwipeVisual(
        modifier = modifier.pointerInput(isEditing) {
            if (isEditing) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(false); down.consume(); pressed = true; active = DPadDirection.NONE
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                val sx = down.position.x; val sy = down.position.y; val thr = with(density) { swipeThresholdDp.toPx() }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - sx; val dy = change.position.y - sy; val dist = hypot(dx, dy)
                    val next = if (dist > thr) { val angle = (atan2(dy, dx) * 180 / PI).toFloat(); when { angle >= -45f && angle <= 45f -> DPadDirection.RIGHT; angle > 45f && angle < 135f -> DPadDirection.DOWN; angle >= 135f || angle <= -135f -> DPadDirection.LEFT; else -> DPadDirection.UP } } else DPadDirection.NONE
                    if (next != active) { if (active != DPadDirection.NONE) onDirectionChange(0, 0); active = next; if (next != DPadDirection.NONE) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); onDirectionChange(if (next == DPadDirection.RIGHT) 1 else if (next == DPadDirection.LEFT) -1 else 0, if (next == DPadDirection.DOWN) 1 else if (next == DPadDirection.UP) -1 else 0) } }
                    change.consume()
                }
                pressed = false; active = DPadDirection.NONE; scope.launch { delay(40); onDirectionChange(0, 0) }
            }
        },
        accentColor = DimWhite, borderAlpha = bA, textAlpha = tA, textScale = tS, displayText = txt, isEditing = isEditing
    )
}

@Composable
fun TraditionalDPad(modifier: Modifier = Modifier, buttonSize: Dp = 60.dp, spacing: Dp = 50.dp, isEditing: Boolean = false, onDirectionChange: (x: Int, y: Int) -> Unit) {
    val density = LocalDensity.current
    var currentX by remember { mutableIntStateOf(0) }
    var currentY by remember { mutableIntStateOf(0) }

    fun updateDirection(x: Int, y: Int, isDown: Boolean) {
        if (isDown) {
            currentX = x
            currentY = y
        } else {
            if (currentX == x) currentX = 0
            if (currentY == y) currentY = 0
        }
        onDirectionChange(currentX, currentY)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val offsetPx = with(density) { spacing.toPx() }
        GamepadButton(label = "↑", onDown = { updateDirection(0, -1, true) }, onUp = { updateDirection(0, -1, false) }, diameter = buttonSize, isEditing = isEditing, modifier = Modifier.offset { IntOffset(0, -offsetPx.roundToInt()) })
        GamepadButton(label = "↓", onDown = { updateDirection(0, 1, true) }, onUp = { updateDirection(0, 1, false) }, diameter = buttonSize, isEditing = isEditing, modifier = Modifier.offset { IntOffset(0, offsetPx.roundToInt()) })
        GamepadButton(label = "←", onDown = { updateDirection(-1, 0, true) }, onUp = { updateDirection(-1, 0, false) }, diameter = buttonSize, isEditing = isEditing, modifier = Modifier.offset { IntOffset(-offsetPx.roundToInt(), 0) })
        GamepadButton(label = "→", onDown = { updateDirection(1, 0, true) }, onUp = { updateDirection(1, 0, false) }, diameter = buttonSize, isEditing = isEditing, modifier = Modifier.offset { IntOffset(offsetPx.roundToInt(), 0) })
    }
}
