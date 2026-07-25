package com.peri.android_to_gamepad.ui.theme.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peri.android_to_gamepad.GamepadConnectionManager
import com.peri.android_to_gamepad.R
import com.peri.android_to_gamepad.model.GameProfile
import com.peri.android_to_gamepad.network.ConnectionStatus
import com.peri.android_to_gamepad.network.GamepadClient

private val Background  = Color(0xFF000000)
private val Surface     = Color(0xFF141414)
private val SurfaceConnected = Color(0xFF1C1F1C)
private val Border      = Color(0xFF2E2E2E)
private val TextMain    = Color(0xFFFFFFFF)
private val TextMuted   = Color(0xFF6B6B6B)
private val Accent      = Color(0xFFE0E0E0)
private val StatusOk     = Color(0xFF6FBF73)
private val StatusFailed = Color(0xFFBF6F6F)
private val SharpCorner = RoundedCornerShape(3.dp)

@Composable
fun GameListScreen(
    client: GamepadClient,
    profiles: List<GameProfile>,
    onGameSelected: (GameProfile) -> Unit
) {
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
    }

    var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
    val connectionManager = remember { GamepadConnectionManager(context, client) }

    fun startAutoConnect(pin: String) {
        connectionManager.startAutoConnect(
            pin = pin,
            onStatus = { connectionStatus = it },
            onTimeout = { if (connectionStatus is ConnectionStatus.Connecting) connectionStatus = ConnectionStatus.Idle }
        )
    }

    LaunchedEffect(Unit) { 
        if (connectionStatus is ConnectionStatus.Idle) showPinDialog = true 
    }
    
    DisposableEffect(Unit) { onDispose { connectionManager.cancelDiscovery() } }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        ConnectionHeader(
            connectionStatus = connectionStatus,
            onRetryDiscovery = { 
                connectionManager.cancelDiscovery()
                client.disconnect()
                connectionStatus = ConnectionStatus.Idle
                showPinDialog = true 
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(profiles) { profile ->
                GameProfileRow(profile = profile, onClick = { onGameSelected(profile) })
            }
        }

        Footer()
    }

    if (showPinDialog) {
        var pinInputText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = Surface,
            title = { Text("CONNECT TO RECEIVER", color = TextMain, fontSize = 14.sp, fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the 4-digit PIN shown on your PC receiver to authenticate.", color = TextMuted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = pinInputText,
                        onValueChange = { if (it.length <= 4) pinInputText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Border,
                            focusedBorderColor = Accent,
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("CLOSE", color = TextMuted) }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInputText.length == 4) {
                        showPinDialog = false
                        startAutoConnect(pinInputText)
                    }
                }) { Text("CONNECT", color = TextMain) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectionHeader(
    connectionStatus: ConnectionStatus,
    onRetryDiscovery: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isConnecting = connectionStatus is ConnectionStatus.Connecting
    val isConnected = connectionStatus is ConnectionStatus.Connected || connectionStatus is ConnectionStatus.Authenticated
    val isFailed = connectionStatus is ConnectionStatus.Error || connectionStatus is ConnectionStatus.Unauthorized

    val headerColor by animateColorAsState(if (isConnected) SurfaceConnected else Surface, label = "header_color")
    val statusText = when (connectionStatus) {
        is ConnectionStatus.Idle -> "Not connected — tap to pair"
        is ConnectionStatus.Connecting -> "Authenticating…"
        is ConnectionStatus.Connected -> "Handshake in progress…"
        is ConnectionStatus.Authenticated -> "Connected"
        is ConnectionStatus.Unauthorized -> "Auth Failed — check PIN"
        is ConnectionStatus.Error -> "Connection error — retry"
    }
    val dotColor = when {
        connectionStatus is ConnectionStatus.Authenticated -> StatusOk
        isConnected -> Accent
        isFailed -> StatusFailed
        else -> TextMuted
    }

    Surface(color = headerColor, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .animateContentSize()
                    .combinedClickable(
                        onClick = { onRetryDiscovery() },
                        onLongClick = onLongClick
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
                    if (isConnecting) {
                        FluidSpinner(color = TextMuted, size = 14.dp)
                    } else {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                    }
                }
                Text(
                    text = statusText,
                    color = TextMain.copy(alpha = if (isConnected) 0.9f else 0.7f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(thickness = 1.dp, color = Border)
        }
    }
}

@Composable
private fun FluidSpinner(color: Color, size: Dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rot")
    val sweep by transition.animateFloat(30f, 300f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sweep")

    Canvas(modifier = Modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val diam = size.toPx() - stroke
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(diam, diam),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun GameProfileRow(profile: GameProfile, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(21f / 9f)
            .clip(SharpCorner)
            .background(Surface)
            .border(1.dp, Border, SharpCorner)
            .clickable { onClick() }
    ) {
        if (profile.iconRes != android.R.color.transparent && profile.iconRes != android.R.drawable.ic_menu_add) {
            Image(
                painter = painterResource(id = profile.iconRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Surface, Color(0xFF1A1A1A)))), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (profile.iconRes == android.R.drawable.ic_menu_add) Icons.Default.Add else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Border,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Background.copy(alpha = 0.8f)))))
        if (profile.name.isNotEmpty()) {
            Text(
                text = profile.name,
                color = TextMain.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun Footer() {
    val uri = LocalUriHandler.current
    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF151515))
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = buildAnnotatedString {
                    append("Made with ")
                    withStyle(SpanStyle(color = Color(0xFF9E3E3E).copy(alpha = 0.5f))) { append("love") }
                    append(" by @Peri")
                },
                color = TextMuted.copy(alpha = 0.4f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            Image(
                painter = painterResource(id = R.drawable.github),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Accent.copy(alpha = 0.3f)),
                modifier = Modifier.size(16.dp).align(Alignment.CenterEnd).clickable { uri.openUri("https://github.com/AMN-D/ANDROID_TO_GAMEPAD") }
            )
        }
    }
}