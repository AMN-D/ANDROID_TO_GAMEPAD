package com.peri.android_to_gamepad

import androidx.compose.runtime.Composable
import com.peri.android_to_gamepad.layouts.GenshinGamepadScreen

data class GameProfile(
    val id: String,
    val name: String,
    val desc: String,
    val iconRes: Int,
    val layout: @Composable (client: GamepadClient, onBack: () -> Unit) -> Unit
)

val GameProfiles = listOf(
    GameProfile(
        id = "genshin",
        name = "Genshin Impact",
        desc = "Open-world action RPG with elemental combat",
        iconRes = R.drawable.genshin_thumb,
        layout = { client, onBack -> GenshinGamepadScreen(client, onBack) }
    )
)