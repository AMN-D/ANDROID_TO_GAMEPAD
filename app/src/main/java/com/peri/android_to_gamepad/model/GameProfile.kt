package com.peri.android_to_gamepad.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.peri.android_to_gamepad.R
import com.peri.android_to_gamepad.layouts.GenshinGamepadScreen
import com.peri.android_to_gamepad.layouts.MinecraftGamepadScreen
import com.peri.android_to_gamepad.network.GamepadClient
import com.peri.android_to_gamepad.ui.theme.screens.CustomLayoutPickerScreen

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
    ),
    GameProfile(
        id = "minecraft",
        name = "Minecraft",
        iconRes = R.drawable.minecraft,
        layout = { client, onBack -> MinecraftGamepadScreen(client, onBack) }
    ),
    GameProfile(
        id = "custom",
        name = "Custom Layout",
        iconRes = android.R.drawable.ic_menu_add,
        layout = { client, onBack -> CustomLayoutPickerScreen(client, onBack) }
    )
)