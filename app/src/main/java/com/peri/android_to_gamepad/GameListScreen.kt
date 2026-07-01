package com.peri.android_to_gamepad

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Monochrome / B&W Palette ──
private val Background  = Color(0xFF000000) // Deep Black
private val Surface     = Color(0xFF141414) // Dark Gray Card
private val Border      = Color(0xFF2E2E2E) // Medium-Dark Gray Border
private val TextMain    = Color(0xFFFFFFFF) // Crisp White
private val TextMuted   = Color(0xFF6B6B6B) // Dimmed Gray
private val Accent      = Color(0xFFE0E0E0) // Light Gray / Off-White Accent
private val AccentFg    = Color(0xFF000000) // Black text on Light Gray
// 2-3 dp for sharp, anti-aliased corners
private val SharpCorner = RoundedCornerShape(3.dp)

// Matches the card's corner radius, but only on the right side — used for the
// full-height connect button so it looks fused to the card's edge rather than
// a separate floating chip. Slightly larger radius so the right side reads
// as noticeably rounded compared to the sharp left/rest of the card.
private val RightSideCorner = RoundedCornerShape(
    topStart = 0.dp, bottomStart = 0.dp,
    topEnd = 14.dp, bottomEnd = 14.dp
)

@Composable
fun GameListScreen(
    client: GamepadClient,
    profiles: List<GameProfile>,
    onGameSelected: (GameProfile) -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
    val sharedPrefs = remember { context.getSharedPreferences("GamepadPrefs", Context.MODE_PRIVATE) }
    var ipAddress   by remember { mutableStateOf(sharedPrefs.getString("saved_ip", "127.0.0.1") ?: "127.0.0.1") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // ── TOP BAR (Floating Card to avoid notch, 2-rows) ────────────────────
        ConnectionHeader(
            connectionStatus = connectionStatus,
            ipAddress  = ipAddress,
            onIpChange = { newIp ->
                ipAddress = newIp
                sharedPrefs.edit().putString("saved_ip", newIp).apply()
            },
            onConnect = {
                client.connect(ipAddress) { result -> connectionStatus = result }
            }
        )

        // ── VERTICAL GAME LIST ────────────────────────────────────────────────
        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Slightly more space between hero cards
        ) {
            items(profiles) { profile ->
                GameProfileRow(
                    profile = profile,
                    onClick = { onGameSelected(profile) }
                )
            }
        }

        // ── FOOTER (Dimmed and stuck to absolute bottom) ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A)) // Barely visible gray background
                .border(1.dp, Color(0xFF151515)) // Ultra subtle top border
                .navigationBarsPadding() // Ensures it extends through the system navigation area
                .padding(vertical = 6.dp), // Thin padding
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Made with love by @Peri",
                color = TextMuted.copy(alpha = 0.4f), // Severely dimmed
                fontSize = 9.sp, // Very small text
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────
//  CONNECTION HEADER (single-row card layout: IP input + connect button)
// ─────────────────────────────────────────────
@Composable
private fun ConnectionHeader(
    connectionStatus: ConnectionStatus,
    ipAddress:  String,
    onIpChange: (String) -> Unit,
    onConnect:  () -> Unit
) {
    val isConnecting = connectionStatus is ConnectionStatus.Connecting
    val isConnected  = connectionStatus is ConnectionStatus.Connected
    val isFailed     = connectionStatus is ConnectionStatus.Error

    // Outer Row: left side holds the IP input (filling the full column),
    // right side is the Connect Button stretched to match that column's full
    // height via IntrinsicSize.Min.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding() // Avoids notch/status bar
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            .background(Surface, RightSideCorner)
            .border(1.dp, Border, RightSideCorner)
            .animateContentSize() // Adds a smooth transition when expanding/collapsing
            .height(IntrinsicSize.Min) // Lets the button match the content column's measured height
    ) {
        // IP input now fills the entire left column (status text removed)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = 52.dp)
                .background(Background, SharpCorner)
                .border(1.dp, Border, SharpCorner)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value       = ipAddress,
                onValueChange = onIpChange,
                textStyle   = TextStyle(
                    color         = TextMain,
                    fontSize      = 14.sp,
                    fontWeight    = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                singleLine    = true,
                cursorBrush   = SolidColor(Accent)
            )
        }

        // Connect button: spans the full height of the card (top to bottom),
        // flush against the right edge, narrow width, no text — icon only.
        // Rounded only on the outer (top-right / bottom-right) corners so it
        // reads as part of the card rather than a separate floating chip.
        ConnectButton(
            isConnecting = isConnecting,
            isConnected = isConnected,
            isFailed = isFailed,
            onClick = onConnect,
            modifier = Modifier
                .fillMaxHeight()
                .width(56.dp)
        )
    }
}

// ─────────────────────────────────────────────
//  CONNECT BUTTON (icon-only, fluid spinner)
// ─────────────────────────────────────────────
private enum class ButtonIconState { IDLE, CONNECTING, CONNECTED, FAILED }

@Composable
private fun ConnectButton(
    isConnecting: Boolean,
    isConnected: Boolean,
    isFailed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RightSideCorner)
            .background(Accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isConnecting,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val state = when {
            isConnecting -> ButtonIconState.CONNECTING
            isConnected  -> ButtonIconState.CONNECTED
            isFailed     -> ButtonIconState.FAILED
            else         -> ButtonIconState.IDLE
        }

        androidx.compose.animation.Crossfade(
            targetState = state,
            animationSpec = tween(300),
            label = "connect_icon_crossfade"
        ) { iconState ->
            when (iconState) {
                ButtonIconState.CONNECTING -> FluidSpinner(color = AccentFg, size = 16.dp)
                ButtonIconState.CONNECTED -> Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = "Connected",
                    tint = AccentFg,
                    modifier = Modifier.size(18.dp)
                )
                ButtonIconState.FAILED -> Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = "Connection failed",
                    tint = AccentFg,
                    modifier = Modifier.size(18.dp)
                )
                ButtonIconState.IDLE -> ArrowIcon()
            }
        }
    }
}

// Simple arrow-forward drawn manually to match the original hand-drawn look.
@Composable
private fun ArrowIcon() {
    Canvas(modifier = Modifier.size(14.dp)) {
        val strokeWidth = 2.dp.toPx()
        val w = size.width
        val h = size.height
        // Shaft
        drawLine(
            color = AccentFg,
            start = androidx.compose.ui.geometry.Offset(0f, h / 2f),
            end = androidx.compose.ui.geometry.Offset(w * 0.75f, h / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // Arrowhead
        drawLine(
            color = AccentFg,
            start = androidx.compose.ui.geometry.Offset(w * 0.45f, 0f),
            end = androidx.compose.ui.geometry.Offset(w, h / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = AccentFg,
            start = androidx.compose.ui.geometry.Offset(w * 0.45f, h),
            end = androidx.compose.ui.geometry.Offset(w, h / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

// A soft, "fluid" rotating arc — smoothly eases its sweep angle in and out
// rather than a rigid spinning line, giving it an organic feel.
@Composable
private fun FluidSpinner(color: Color, size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "fluid_spinner")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val sweep by transition.animateFloat(
        initialValue = 30f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = 2.dp.toPx()
        val diameter = size.toPx() - strokeWidth
        val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

// ─────────────────────────────────────────────
//  PROFILE ROW (Hero Banner look - Auto Adapts to Image)
// ─────────────────────────────────────────────
@Composable
private fun GameProfileRow(
    profile: GameProfile,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Height is removed! The Box will now wrap the exact cinematic aspect ratio of your image automatically.
            .clip(SharpCorner)
            .background(Surface)
            .border(1.dp, Border, SharpCorner)
            .clickable { onClick() }
    ) {
        // 1. Full-bleed background image determines the banner's true height
        Image(
            painter            = painterResource(id = profile.iconRes),
            contentDescription = "${profile.name} cover",
            contentScale       = ContentScale.FillWidth, // Matches width, adjusts height automatically
            modifier           = Modifier.fillMaxWidth()
        )

        // 2. Vertical gradient optimized for dynamic heights (dark at bottom)
        Box(
            modifier = Modifier
                .matchParentSize() // Matches the size of the Image dynamically without forcing a rigid height
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent, // Top area remains clear
                            Color.Transparent, // Mid area remains relatively clear
                            Background.copy(alpha = 0.95f) // Deep shadow at the text level
                        )
                    )
                )
        )

        // 3. Game title styled smaller, slightly transparent, and in monospace
        Text(
            text       = profile.name,
            color      = TextMain.copy(alpha = 0.7f), // Added subtle transparency
            fontSize   = 13.sp, // Made smaller and less prominent
            fontFamily = FontFamily.Monospace, // Android's default Monospace font
            fontWeight = FontWeight.SemiBold, // Slightly thinner than ExtraBold
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp) // Hugs closer to the corner
        )
    }
}