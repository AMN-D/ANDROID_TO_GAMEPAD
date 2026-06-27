package com.peri.android_to_gamepad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameListScreen(
    client: GamepadClient,
    profiles: List<GameProfile>,          // injected, not hardcoded
    onGameSelected: (GameProfile) -> Unit // passes back which one was tapped
) {
    var statusText by remember { mutableStateOf("Ready to Connect") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Select a Profile",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = statusText,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                statusText = "Connecting..."
                client.connect { result -> statusText = result }
            },
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text("Connect")
        }

        // ── One card per profile, automatically ──
        profiles.forEach { profile ->
            Card(
                modifier = Modifier
                    .size(width = 350.dp, height = 100.dp)
                    .padding(bottom = 16.dp)
                    .clickable { onGameSelected(profile) },
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}