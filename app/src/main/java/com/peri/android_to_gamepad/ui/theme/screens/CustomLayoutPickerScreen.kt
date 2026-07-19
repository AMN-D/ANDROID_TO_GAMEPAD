package com.peri.android_to_gamepad.ui.theme.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.peri.android_to_gamepad.network.GamepadClient

@Composable
fun CustomLayoutPickerScreen(client: GamepadClient, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CustomLayout", Context.MODE_PRIVATE) }
    val presetNames = remember { mutableStateListOf<String>() }
    var selectedLayout by remember { mutableStateOf<String?>(null) }
    var startInEditMode by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun refreshPresets() {
        val list = prefs.getString("preset_list", "Default") ?: "Default"
        presetNames.clear()
        presetNames.addAll(list.split(";").filter { it.isNotEmpty() })
    }

    LaunchedEffect(Unit) { refreshPresets() }

    BackHandler {
        if (selectedLayout != null) selectedLayout = null
        else onBack()
    }

    if (selectedLayout != null) {
        CustomLayoutScreen(
            client = client,
            initialPreset = selectedLayout!!,
            startInEditMode = startInEditMode,
            onBack = { selectedLayout = null }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
            Text(
                "CUSTOM LAYOUTS",
                color = Color.White.copy(0.7f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(presetNames) { name ->
                    LayoutCard(
                        name = name,
                        onClick = { 
                            startInEditMode = false
                            selectedLayout = name 
                        },
                        onEdit = {
                            startInEditMode = true
                            selectedLayout = name
                        },
                        onDelete = {
                            if (name != "Default") {
                                presetNames.remove(name)
                                prefs.edit().putString("preset_list", presetNames.joinToString(";")).apply()
                                prefs.edit().remove("layout_$name").apply()
                            }
                        }
                    )
                }

                item {
                    NewLayoutCard { showCreateDialog = true }
                }
            }
        }
    }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF111111),
            title = { Text("NEW LAYOUT", color = Color.White.copy(0.7f), fontSize = 14.sp, fontFamily = FontFamily.Monospace) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        focusedBorderColor = Color.White.copy(0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        val list = (presetNames + newName).distinct().joinToString(";")
                        prefs.edit().putString("preset_list", list).apply()
                        refreshPresets()
                        startInEditMode = true
                        selectedLayout = newName
                        showCreateDialog = false
                    }
                }) { Text("CREATE", color = Color.White) }
            }
        )
    }
}

@Composable
private fun LayoutCard(name: String, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFF141414), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Gamepad, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Text(name, color = Color.White.copy(0.8f), fontSize = 16.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, null, tint = Color.White.copy(0.3f))
        }

        if (name != "Default") {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.White.copy(0.3f))
            }
        }
    }
}

@Composable
private fun NewLayoutCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, null, tint = Color.White.copy(0.6f))
            Spacer(Modifier.width(8.dp))
            Text("NEW SCHEMATIC", color = Color.White.copy(0.6f), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
