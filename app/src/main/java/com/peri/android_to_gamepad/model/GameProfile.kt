package com.peri.android_to_gamepad.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.peri.android_to_gamepad.R
import com.peri.android_to_gamepad.layouts.GenshinGamepadScreen
import com.peri.android_to_gamepad.network.GamepadClient

@Immutable
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
) + List(7) { index ->
    GameProfile(
        id = "dummy_$index",
        name = "",
        iconRes = android.R.color.transparent,
        layout = { _, onBack -> onBack() }
    )
}