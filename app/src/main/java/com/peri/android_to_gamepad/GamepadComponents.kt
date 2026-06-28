package com.peri.android_to_gamepad

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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

// ─────────────────────────────────────────────
//  STICK PROCESSING
//  All three improvements applied in one pass — no extra delay.
//
//  DEADZONE  : ignore the inner 8% of travel to prevent drift at rest
//  CURVE     : quadratic (x²) — precise near center, fast at edges
//  OUTER SNAP: anything past 95% curved output snaps to exactly 1.0
//              so sprinting / full-speed movement always fires cleanly
// ─────────────────────────────────────────────
private const val STICK_DEADZONE  = 0.08f   // 8% inner dead zone
private const val STICK_SNAP      = 0.95f   // snap to 1.0 beyond this
private const val DELTA_THRESHOLD = 0.012f  // skip send if change < ~1.2%

private fun applyStickCurve(rawX: Float, rawY: Float): Pair<Float, Float> {
    val magnitude = hypot(rawX, rawY).coerceIn(0f, 1f)

    // Inside deadzone → hard zero, no drift
    if (magnitude < STICK_DEADZONE) return Pair(0f, 0f)

    // Re-map so deadzone edge = 0.0, physical edge = 1.0
    val normalized = (magnitude - STICK_DEADZONE) / (1f - STICK_DEADZONE)

    // Quadratic curve: slow movements stay precise, fast ones reach max quickly
    val curved = normalized * normalized

    // Outer snap: guarantee max value when nearly at full tilt
    val final = if (curved > STICK_SNAP) 1f else curved

    // Re-apply scale back onto the original x/y direction
    val scale = final / magnitude
    return Pair(
        (rawX * scale).coerceIn(-1f, 1f),
        (rawY * scale).coerceIn(-1f, 1f)
    )
}

// ─────────────────────────────────────────────
//  CAMERA ZONE  —  pointer-delta approach
//
//  OLD approach: output = (finger position − origin) / maxDrag
//    Problem: holding still sends a constant non-zero value, so the
//    camera keeps panning even when your finger isn't moving. To look
//    around you have to physically hold your finger at a distance.
//    Every time you lift and re-plant there's a jarring origin reset.
//
//  NEW approach: output = (position this frame − position last frame) × sensitivity
//    Fast swipe  → large delta per frame → fast camera turn
//    Slow drag   → small delta per frame → precise slow turn
//    Hold still  → zero delta           → camera stops
//    Lift+retouch → no origin to reset, zero jump guaranteed
//
//  This is how every native mobile game (Genshin mobile, PUBG Mobile, etc.)
//  implements camera look — it's essentially a trackpad, not a joystick.
//
//  sensitivity: pixels of finger movement that map to 1.0 output.
//    Lower = more sensitive. Default 0.010 → 100px ≈ full tilt.
//    Tune this in GenshinGamepadScreen if it feels too fast or slow.
// ─────────────────────────────────────────────
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
                    // Tell the server the stick is centered when the finger lands
                    onUpdate(0f, 0f)

                    val pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break

                        // How far did the finger move since the last event?
                        // previousPosition is always valid in Compose gestures —
                        // on the first move event it equals the down position,
                        // so the very first delta is the movement from touch-down.
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y

                        // Scale by sensitivity and clamp.
                        // A fast swipe naturally produces a larger delta and therefore
                        // a larger output — no separate speed curve needed.
                        val outX = (dx * sensitivity).coerceIn(-1f, 1f)
                        val outY = (dy * sensitivity).coerceIn(-1f, 1f)

                        // Skip frames where the finger barely moved (e.g. touch noise)
                        if (abs(outX) > DELTA_THRESHOLD || abs(outY) > DELTA_THRESHOLD) {
                            onUpdate(outX, outY)
                        }

                        change.consume()
                    }

                    // Finger lifted — release the stick to center
                    onUpdate(0f, 0f)
                }
            }
    )
}

// ─────────────────────────────────────────────
//  JOYSTICK ZONE
//  + applyStickCurve (deadzone + curve + snap) on all output values
//  + Delta filtering on sends
//  Note: thumbOffset still uses raw cx/cy so the visual thumb
//        tracks your actual finger position, not the processed output.
// ─────────────────────────────────────────────
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

                        // Visual thumb follows raw finger position
                        thumbOffset = Offset(cx, cy)

                        // Sent value goes through deadzone + curve + snap
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

// ─────────────────────────────────────────────
//  GAMEPAD BUTTON
//  + Scale animation on press: snaps in fast (StiffnessHigh spring),
//    eases back out — feels like a physical click.
//    Touch area is NOT affected — graphicsLayer is visual only.
//  + Haptic weight tied to button size:
//    large (>70dp) → VIRTUAL_KEY (stronger pulse)
//    everything else → KEYBOARD_TAP
//  + Delta filtering on draggable mode
// ─────────────────────────────────────────────
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
    // Snappy press-in, smooth release — StiffnessHigh keeps it feeling instant
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )

    val view = LocalView.current
    val density = LocalDensity.current

    // Larger buttons get a stronger haptic pulse
    val hapticConstant = if (diameter > 70.dp) {
        HapticFeedbackConstants.VIRTUAL_KEY
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }

    Box(
        modifier = modifier
            .size(diameter)
            // graphicsLayer is purely visual — layout size (touch area) stays at `diameter`
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
                        // ── Draggable mode ──
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
                        // ── Plain tap mode ──
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