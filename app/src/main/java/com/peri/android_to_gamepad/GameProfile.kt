package com.peri.android_to_gamepad

import androidx.compose.runtime.Composable
import com.peri.android_to_gamepad.layouts.GenshinGamepadScreen

data class GameProfile(
    val id: String,
    val name: String,
    val iconRes: Int,
    val layout: @Composable (client: GamepadClient, onBack: () -> Unit) -> Unit
)

val GameProfiles = listOf(
    GameProfile(
        id = "genshin",
        name = "Genshin Impact",
        iconRes = R.drawable.genshin_thumb,
        layout = { client, onBack -> GenshinGamepadScreen(client, onBack) }
    )
)