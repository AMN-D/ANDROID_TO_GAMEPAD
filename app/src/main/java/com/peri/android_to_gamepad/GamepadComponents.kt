package com.peri.android_to_gamepad

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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

private fun applyStickCurve(rawX: Float, rawY: Float): Pair<Float, Float> {
    val magnitude = hypot(rawX, rawY).coerceIn(0f, 1f)
    if (magnitude < STICK_DEADZONE) return Pair(0f, 0f)
    val normalized = (magnitude - STICK_DEADZONE) / (1f - STICK_DEADZONE)
    val curved = normalized * normalized
    val final = if (curved > STICK_SNAP) 1f else curved
    val scale = final / magnitude
    return Pair(
        (rawX * scale).coerceIn(-1f, 1f),
        (rawY * scale).coerceIn(-1f, 1f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CameraZone
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CameraZone(
    modifier: Modifier = Modifier,
    sensitivity: Float = 0.200f,
    onUpdate: (x: Float, y: Float) -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    down.consume()
                    onUpdate(0f, 0f)

                    val pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break

                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y

                        val outX = (dx * sensitivity).coerceIn(-1f, 1f)
                        val outY = (dy * sensitivity).coerceIn(-1f, 1f)

                        if (abs(outX) > DELTA_THRESHOLD || abs(outY) > DELTA_THRESHOLD) {
                            onUpdate(outX, outY)
                        }

                        change.consume()
                    }

                    onUpdate(0f, 0f)
                }
            }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// JoystickZone
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun JoystickZone(
    modifier: Modifier = Modifier,
    baseRadiusFraction: Float = 0.38f,
    thumbSizeRatio: Float = 0.42f,
    restPaddingRatio: Float = 0.25f,
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

                    val baseRadiusPx = minOf(size.width, size.height) * baseRadiusFraction
                    val thumbRadiusPx = baseRadiusPx * thumbSizeRatio
                    val maxDragPx = baseRadiusPx - thumbRadiusPx

                    val down = awaitFirstDown(requireUnconsumed = true)
                    val clampedCenter = Offset(
                        x = down.position.x.coerceIn(baseRadiusPx, size.width - baseRadiusPx),
                        y = down.position.y.coerceIn(baseRadiusPx, size.height - baseRadiusPx)
                    )
                    baseCenter = clampedCenter
                    thumbOffset = Offset.Zero
                    onUpdate(0f, 0f)

                    var lastSentX = 0f
                    var lastSentY = 0f
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
                        val (outX, outY) = applyStickCurve(cx / maxDragPx, cy / maxDragPx)

                        if (abs(outX - lastSentX) > DELTA_THRESHOLD ||
                            abs(outY - lastSentY) > DELTA_THRESHOLD
                        ) {
                            onUpdate(outX, outY)
                            lastSentX = outX
                            lastSentY = outY
                        }

                        change.consume()
                    }

                    baseCenter = null
                    thumbOffset = Offset.Zero
                    onUpdate(0f, 0f)
                }
            }
    ) {
        if (zoneSize.width > 0 && zoneSize.height > 0) {
            val baseRadiusPx = minOf(zoneSize.width, zoneSize.height) * baseRadiusFraction
            val thumbRadiusPx = baseRadiusPx * thumbSizeRatio
            val restPadding = baseRadiusPx * restPaddingRatio

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

// ─────────────────────────────────────────────────────────────────────────────
// GamepadButton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GamepadButton(
    label: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    accentColor: Color = Color.White,
    diameter: Dp = 58.dp,
    onUpdate: ((x: Float, y: Float) -> Unit)? = null,
    maxDragDp: Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }

    val fillAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.35f else 0.10f,
        label = "fillAlpha"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.60f else 0.25f,
        label = "borderAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )

    val view = LocalView.current
    val density = LocalDensity.current

    val hapticConstant = if (diameter > 70.dp) {
        HapticFeedbackConstants.VIRTUAL_KEY
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }

    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color.Black.copy(alpha = fillAlpha), CircleShape)
            .border(1.5.dp, accentColor.copy(alpha = borderAlpha), CircleShape)
            .pointerInput(onUpdate) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    view.performHapticFeedback(
                        hapticConstant,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                    onDown()

                    if (onUpdate != null) {
                        val maxDragPx = with(density) { maxDragDp.toPx() }
                        var startX = down.position.x
                        var startY = down.position.y
                        val pointerId = down.id
                        var lastSentX = 0f
                        var lastSentY = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) break

                            var dx = change.position.x - startX
                            var dy = change.position.y - startY
                            val distance = hypot(dx, dy)

                            if (distance > maxDragPx && distance > 0) {
                                val overage = distance - maxDragPx
                                val ratio = overage / distance
                                startX += dx * ratio
                                startY += dy * ratio
                                dx = change.position.x - startX
                                dy = change.position.y - startY
                            }

                            val outX = (dx / maxDragPx).coerceIn(-1f, 1f)
                            val outY = (dy / maxDragPx).coerceIn(-1f, 1f)

                            if (abs(outX - lastSentX) > DELTA_THRESHOLD ||
                                abs(outY - lastSentY) > DELTA_THRESHOLD
                            ) {
                                onUpdate(outX, outY)
                                lastSentX = outX
                                lastSentY = outY
                            }

                            change.consume()
                        }

                        onUpdate(0f, 0f)
                    } else {
                        val up = waitForUpOrCancellation()
                        up?.consume()
                    }

                    pressed = false
                    onUp()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accentColor.copy(alpha = if (pressed) 1f else 0.4f),
            fontSize = (diameter.value * 0.34f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DPadDirection (shared by RadialCharacterWheel and SwipeDPad)
// ─────────────────────────────────────────────────────────────────────────────

enum class DPadDirection { NONE, UP, RIGHT, DOWN, LEFT }

// ─────────────────────────────────────────────────────────────────────────────
// RadialCharacterWheel  (kept for reference — replaced by SwipeDPad in layout)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RadialCharacterWheel(
    label: String,
    onDirectionChange: (x: Int, y: Int) -> Unit,
    accentColor: Color = Color.White,
    centerDiameter: Dp = 64.dp,
    deadzoneDp: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var activeDirection by remember { mutableStateOf(DPadDirection.NONE) }

    val fillAlpha by animateFloatAsState(if (pressed) 0.35f else 0.10f, label = "fillAlpha")
    val borderAlpha by animateFloatAsState(if (pressed) 0.60f else 0.25f, label = "borderAlpha")
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )

    val view = LocalView.current
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(centerDiameter + 40.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    activeDirection = DPadDirection.NONE
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    val startX = down.position.x
                    val startY = down.position.y
                    val deadzonePx = with(density) { deadzoneDp.toPx() }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val dx = change.position.x - startX
                        val dy = change.position.y - startY
                        val distance = hypot(dx, dy)

                        val newDirection = if (distance > deadzonePx) {
                            val angle = (atan2(dy, dx) * 180 / PI).toFloat()
                            when {
                                angle >= -45f && angle <= 45f -> DPadDirection.RIGHT
                                angle > 45f && angle < 135f -> DPadDirection.DOWN
                                angle >= 135f || angle <= -135f -> DPadDirection.LEFT
                                else -> DPadDirection.UP
                            }
                        } else {
                            DPadDirection.NONE
                        }

                        if (newDirection != activeDirection) {
                            activeDirection = newDirection
                            if (newDirection != DPadDirection.NONE) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                        change.consume()
                    }

                    pressed = false
                    val finalDirection = activeDirection
                    activeDirection = DPadDirection.NONE

                    if (finalDirection != DPadDirection.NONE) {
                        val outX = when (finalDirection) { DPadDirection.RIGHT -> 1; DPadDirection.LEFT -> -1; else -> 0 }
                        val outY = when (finalDirection) { DPadDirection.DOWN -> 1; DPadDirection.UP -> -1; else -> 0 }

                        onDirectionChange(outX, outY)

                        scope.launch {
                            delay(40)
                            onDirectionChange(0, 0)
                        }
                    } else {
                        onDirectionChange(0, 0)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(centerDiameter)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(Color.Black.copy(alpha = fillAlpha), CircleShape)
                .border(1.5.dp, accentColor.copy(alpha = borderAlpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val displayText = when {
                !pressed -> label
                activeDirection == DPadDirection.UP -> "↑"
                activeDirection == DPadDirection.DOWN -> "↓"
                activeDirection == DPadDirection.LEFT -> "←"
                activeDirection == DPadDirection.RIGHT -> "→"
                else -> "•"
            }
            val fontSize = if (pressed && activeDirection != DPadDirection.NONE) {
                (centerDiameter.value * 0.5f).sp
            } else {
                (centerDiameter.value * 0.34f).sp
            }
            Text(
                text = displayText,
                color = accentColor.copy(alpha = if (pressed) 1f else 0.4f),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SwipeDPad  — claw-grip optimised swipeable D-pad zone
//
// Placement guide (landscape claw grip):
//   x  ~58% of screen width   — right index finger arc, right of centre
//   y  ~1.5% of screen height — hug the top bezel, where the finger curls over
//   size  145 × 68 dp         — wide + shallow to match the claw sweep arc
//
// OLED battery notes:
//   - Background is pure Color.Black  → pixels off on OLED, zero draw at rest
//   - Border/text are near-invisible while idle (idleBorderAlpha = 0.10f)
//   - Visuals only brighten for the instant an active direction is detected
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SwipeDPad(
    modifier: Modifier = Modifier,
    swipeThresholdDp: Dp = 18.dp,
    idleBorderAlpha: Float = 0.10f,
    accentColor: Color = Color.White,
    onDirectionChange: (x: Int, y: Int) -> Unit
) {
    val density = LocalDensity.current
    val scope   = rememberCoroutineScope()
    val view    = LocalView.current

    var pressed         by remember { mutableStateOf(false) }
    var activeDirection by remember { mutableStateOf(DPadDirection.NONE) }

    val borderAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.45f else idleBorderAlpha,
        label = "border"
    )
    val textAlpha by animateFloatAsState(
        targetValue = when {
            !pressed                              -> 0.13f
            activeDirection != DPadDirection.NONE -> 0.90f
            else                                  -> 0.40f
        },
        label = "txtAlpha"
    )
    val textScale by animateFloatAsState(
        targetValue = if (pressed && activeDirection != DPadDirection.NONE) 1.50f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "txtScale"
    )

    val displayText = when {
        !pressed                              -> "⊕"
        activeDirection == DPadDirection.UP    -> "↑"
        activeDirection == DPadDirection.DOWN  -> "↓"
        activeDirection == DPadDirection.LEFT  -> "←"
        activeDirection == DPadDirection.RIGHT -> "→"
        else                                  -> "•"
    }

    Box(
        modifier = modifier
            .border(1.dp, accentColor.copy(alpha = borderAlpha))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed         = true
                    activeDirection = DPadDirection.NONE
                    view.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )

                    val startX      = down.position.x
                    val startY      = down.position.y
                    val thresholdPx = with(density) { swipeThresholdDp.toPx() }

                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val dx       = change.position.x - startX
                        val dy       = change.position.y - startY
                        val distance = hypot(dx, dy)

                        val newDirection = if (distance > thresholdPx) {
                            val angle = (atan2(dy, dx) * 180 / PI).toFloat()
                            when {
                                angle >= -45f  && angle <= 45f   -> DPadDirection.RIGHT
                                angle >   45f  && angle <  135f  -> DPadDirection.DOWN
                                angle >= 135f  || angle <= -135f -> DPadDirection.LEFT
                                else                             -> DPadDirection.UP
                            }
                        } else DPadDirection.NONE

                        if (newDirection != activeDirection) {
                            if (activeDirection != DPadDirection.NONE) {
                                onDirectionChange(0, 0)
                            }
                            activeDirection = newDirection
                            if (newDirection != DPadDirection.NONE) {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.KEYBOARD_TAP,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                val outX = when (newDirection) {
                                    DPadDirection.RIGHT -> 1; DPadDirection.LEFT -> -1; else -> 0
                                }
                                val outY = when (newDirection) {
                                    DPadDirection.DOWN -> 1; DPadDirection.UP -> -1; else -> 0
                                }
                                onDirectionChange(outX, outY)
                            }
                        }
                        change.consume()
                    }

                    pressed         = false
                    activeDirection = DPadDirection.NONE
                    scope.launch {
                        delay(40)
                        onDirectionChange(0, 0)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = displayText,
            color      = accentColor.copy(alpha = textAlpha),
            fontSize   = (18f * textScale).sp,
            fontWeight = FontWeight.Bold
        )
    }
}