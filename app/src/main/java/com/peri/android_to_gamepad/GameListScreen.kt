package com.peri.android_to_gamepad

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Modern Abstract Palette (Colored) ──
private val Background = Color(0xFF030712) // Deep Gray 950
private val Surface    = Color(0xFF111827) // Deep Gray 900
private val Border     = Color(0xFF1F2937) // Deep Gray 800
private val TextMain   = Color(0xFFF9FAFB) // Gray 50
private val TextMuted  = Color(0xFF9CA3AF) // Gray 400
private val Accent     = Color(0xFF6366F1) // Indigo 500
private val AccentFg   = Color(0xFFFFFFFF) // White text on accent
private val Success    = Color(0xFF10B981) // Emerald 500
private val Destructive= Color(0xFFEF4444) // Red 500

@Composable
fun GameListScreen(
    client: GamepadClient,
    profiles: List<GameProfile>,
    onGameSelected: (GameProfile) -> Unit
) {
    var statusText by remember { mutableStateOf("Ready to Connect") }

    // NEW: Setup SharedPreferences to remember the IP address
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("GamepadPrefs", Context.MODE_PRIVATE) }
    var ipAddress by remember { mutableStateOf(sharedPrefs.getString("saved_ip", "127.0.0.1") ?: "127.0.0.1") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            // Slightly reduced vertical padding to give the cards maximum breathing room
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ── TOP BAR: Sleek Connection Pill ──
        ConnectionHeader(
            statusText = statusText,
            ipAddress = ipAddress,
            onIpChange = { newIp ->
                ipAddress = newIp
                // Save it immediately so you don't lose it if you restart
                sharedPrefs.edit().putString("saved_ip", newIp).apply()
            },
            onConnect = {
                statusText = "Connecting…"
                client.connect(ipAddress) { result -> statusText = result }
            }
        )

        // ── MAIN CONTENT: Cinematic Game Covers ──
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(profiles) { profile ->
                CinematicProfileCard(
                    modifier = Modifier.fillMaxHeight(),
                    profile = profile,
                    onClick = { onGameSelected(profile) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  CONNECTION HEADER (Floating Pill Dashboard)
// ─────────────────────────────────────────────
@Composable
private fun ConnectionHeader(
    statusText: String,
    ipAddress: String,
    onIpChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    val statusColor = when {
        statusText.contains("Connected", ignoreCase = true) -> Success
        statusText.contains("Connecting", ignoreCase = true) -> Accent
        statusText.contains("fail", ignoreCase = true) ||
                statusText.contains("error", ignoreCase = true) -> Destructive
        else -> TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .background(Surface, CircleShape)
                .border(1.dp, Border, CircleShape)
                .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Status Indicator & Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
                Text(
                    text = statusText.uppercase(),
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.widthIn(min = 100.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // NEW: IP Address Input Field (Sleek inner pill)
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .background(Background, RoundedCornerShape(12.dp))
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = ipAddress,
                    onValueChange = onIpChange,
                    textStyle = TextStyle(
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Accent)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Connect Action
            Button(
                onClick = onConnect,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = AccentFg
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = "CONNECT",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  PROFILE CARD (Cinematic Cover Flow)
// ─────────────────────────────────────────────
@Composable
private fun CinematicProfileCard(modifier: Modifier = Modifier, profile: GameProfile, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(0.65f)
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(24.dp))
            .clickable { onClick() }
    ) {
        // 1. Full Bleed Background Image
        Image(
            painter = painterResource(id = profile.iconRes),
            contentDescription = "${profile.name} cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Smooth Gradient Overlay (Darkens the bottom for text readability)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Background.copy(alpha = 0.4f),
                            Background.copy(alpha = 0.95f)
                        ),
                        startY = 100f
                    )
                )
        )

        // 3. Content at the bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = profile.name,
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = profile.desc,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = AccentFg
                )
            ) {
                Text(
                    text = "LAUNCH",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontSize = 13.sp
                )
            }
        }
    }
}