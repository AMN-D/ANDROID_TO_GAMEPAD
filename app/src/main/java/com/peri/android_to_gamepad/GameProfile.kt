package com.peri.android_to_gamepad

import androidx.compose.runtime.Composable
import com.peri.android_to_gamepad.layouts.GenshinGamepadScreen

data class GameProfile(
    val id: String,
    val name: String,
    val layout: @Composable (client: GamepadClient, onBack: () -> Unit) -> Unit
)

// ── Add new games here only ──────────────────
val GameProfiles = listOf(
    GameProfile(
        id = "genshin",
        name = "Genshin Impact",
        layout = { client, onBack -> GenshinGamepadScreen(client, onBack) }
    )
    // GameProfile(
    //     id = "valorant",
    //     name = "Valorant",
    //     layout = { client, onBack -> ValorantGamepadScreen(client, onBack) }
    // )
)