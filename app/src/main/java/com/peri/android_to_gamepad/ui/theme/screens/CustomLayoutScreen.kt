package com.peri.android_to_gamepad.ui.theme.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peri.android_to_gamepad.network.GamepadClient
import com.peri.android_to_gamepad.ui.theme.DimWhite
import com.peri.android_to_gamepad.ui.theme.components.*
import kotlin.math.roundToInt

@Immutable
data class PlacedComponent(
    val id: Long,
    val type: String,
    val offset: MutableState<Offset>,
    val width: MutableState<Dp>,
    val height: MutableState<Dp>,
    val isRightAnchored: MutableState<Boolean> = mutableStateOf(false),
    val dpadButtonSize: MutableState<Dp> = mutableStateOf(60.dp),
    val dpadSpacing: MutableState<Dp> = mutableStateOf(50.dp)
)

private val GridColor = Color.White.copy(0.18f)
private const val GRID_SIZE_DP = 20
private const val DEFAULT_LAYOUT_STRING = "JOY,0.0,80.0,325.0,150.0,true,false,60,50;DPD,140.0,180.0,183.0,183.0,true,false,60,50;RJOY,440.0,180.0,325.0,183.0,true,false,60,50;BTN_Y,660.0,100.0,50.0,50.0,true,false,60,50;BTN_X,620.0,140.0,50.0,50.0,true,false,60,50;BTN_B,700.0,140.0,50.0,50.0,true,false,60,50;BTN_A,660.0,180.0,50.0,50.0,true,false,60,50;SEL,350.0,40.0,40.0,40.0,true,false,60,50;STA,410.0,40.0,40.0,40.0,true,false,60,50;BMP_L,120.0,0.0,65.0,65.0,true,false,60,50;BMP_R,600.0,0.0,65.0,65.0,true,false,60,50;TRG_L,40.0,0.0,65.0,65.0,true,false,60,50;TRG_R,680.0,0.0,65.0,65.0,true,false,60,50"

@Composable
fun CustomLayoutScreen(client: GamepadClient, initialPreset: String = "Default", startInEditMode: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CustomLayout", Context.MODE_PRIVATE) }
    val density = LocalDensity.current

    val gridSizePx = with(density) { GRID_SIZE_DP.dp.toPx() }
    var isEditing by remember { mutableStateOf(startInEditMode) }
    var showDrawer by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var activePreset by remember { mutableStateOf(initialPreset) }
    var selectedId by remember { mutableLongStateOf(-1L) }
    var showDpadSettingsId by remember { mutableLongStateOf(-1L) }
    val components = remember { mutableStateListOf<PlacedComponent>() }

    val drawerWidth = 180.dp
    val drawerOffset by animateDpAsState(targetValue = if (showDrawer && isEditing) (-drawerWidth) else 0.dp, label = "d")

    fun snap(value: Float): Float = if (showGrid) (value / gridSizePx).roundToInt() * gridSizePx else value

    fun saveLayout(name: String) {
        val serialized = components.joinToString(";") { 
            val xDp = with(density) { it.offset.value.x.toDp().value }
            val yDp = with(density) { it.offset.value.y.toDp().value }
            "${it.type},$xDp,$yDp,${it.width.value.value},${it.height.value.value},${it.isRightAnchored.value},${it.dpadButtonSize.value.value},${it.dpadSpacing.value.value}" 
        }
        prefs.edit().putString("layout_$name", serialized).apply()
        Toast.makeText(context, "Layout Saved: $name", Toast.LENGTH_SHORT).show()
    }

    fun parseAndLoad(serialized: String) {
        serialized.split(";").forEach { item ->
            val parts = item.split(",")
            if (parts.size >= 5) {
                val xPx = with(density) { parts[1].toFloat().dp.toPx() }
                val yPx = with(density) { parts[2].toFloat().dp.toPx() }
                // Migration: If parts[5] was isLocked, skip it. 
                // Original format: type,x,y,w,h,isLocked,isRightAnchored,bs,sp
                // New format: type,x,y,w,h,isRightAnchored,bs,sp
                val isRightIdx = if (parts.size > 6 && (parts[5] == "true" || parts[5] == "false")) 6 else 5
                
                components.add(PlacedComponent(
                    System.currentTimeMillis() + components.size,
                    parts[0],
                    mutableStateOf(Offset(xPx, yPx)),
                    mutableStateOf(parts[3].toFloat().dp),
                    mutableStateOf(parts[4].toFloat().dp),
                    mutableStateOf(parts.getOrNull(isRightIdx)?.toBoolean() ?: false),
                    mutableStateOf(parts.getOrNull(isRightIdx + 1)?.toFloat()?.dp ?: 60.dp),
                    mutableStateOf(parts.getOrNull(isRightIdx + 2)?.toFloat()?.dp ?: 50.dp)
                ))
            }
        }
    }

    fun loadLayout(name: String) {
        var saved = prefs.getString("layout_$name", "") ?: ""
        if (saved.isEmpty() && name == "Default") {
            saved = DEFAULT_LAYOUT_STRING
        }
        components.clear()
        selectedId = -1L
        activePreset = name
        if (saved.isNotEmpty()) {
            parseAndLoad(saved)
        }
    }

    LaunchedEffect(Unit) {
        loadLayout(initialPreset)
    }


    BackHandler { when { showDrawer -> showDrawer = false; else -> onBack() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isEditing && showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = gridSizePx
                for (x in 0..(size.width / step).toInt()) drawLine(GridColor, Offset(x * step, 0f), Offset(x * step, size.height), 1f)
                for (y in 0..(size.height / step).toInt()) drawLine(GridColor, Offset(0f, y * step), Offset(size.width, y * step), 1f)
            }
        }

        components.forEach { pc ->
            val isSelected = selectedId == pc.id
            var isDragging by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(pc.offset.value.x.roundToInt(), pc.offset.value.y.roundToInt()) }
                    .size(pc.width.value + 16.dp, pc.height.value + 16.dp)
                    .then(if(isEditing) Modifier.clickable { selectedId = pc.id } else Modifier)
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(pc.width.value, pc.height.value)
                        .then(if(isEditing) Modifier.border(1.dp, if(isSelected) DimWhite.copy(0.5f) else DimWhite.copy(0.15f), RoundedCornerShape(2.dp)) else Modifier)
                ) {
                    when (pc.type) {
                        "JOY" -> JoystickZone(modifier = Modifier.fillMaxSize(), isRightSide = pc.isRightAnchored.value, isEditing = isEditing, onUpdate = { x, y -> client.sendCommand("ABS_X:${(x * 32767).toInt()}"); client.sendCommand("ABS_Y:${(y * 32767).toInt()}") })
                        "CAM" -> CameraZone(modifier = Modifier.fillMaxSize(), isEditing = isEditing, onUpdate = { x, y -> client.sendCommand("ABS_RX:${(x * 32767).toInt()}"); client.sendCommand("ABS_RY:${(y * 32767).toInt()}") })
                        "RJOY" -> JoystickZone(modifier = Modifier.fillMaxSize(), isRightSide = pc.isRightAnchored.value, isEditing = isEditing, onUpdate = { x, y -> client.sendCommand("ABS_RX:${(x * 32767).toInt()}"); client.sendCommand("ABS_RY:${(y * 32767).toInt()}") })
                        "SWP" -> SwipeDPad(modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDirectionChange = { x, y -> client.sendCommand("ABS_HAT0X:$x"); client.sendCommand("ABS_HAT0Y:$y") })
                        "DPD" -> TraditionalDPad(modifier = Modifier.fillMaxSize(), buttonSize = pc.dpadButtonSize.value, spacing = pc.dpadSpacing.value, isEditing = isEditing, onDirectionChange = { x, y -> client.sendCommand("ABS_HAT0X:$x"); client.sendCommand("ABS_HAT0Y:$y") })
                        "BTN_A" -> GamepadButton("A", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_SOUTH:1") }, onUp = { client.sendCommand("BTN_SOUTH:0") })
                        "BTN_B" -> GamepadButton("B", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_EAST:1") }, onUp = { client.sendCommand("BTN_EAST:0") })
                        "BTN_X" -> GamepadButton("X", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_NORTH:1") }, onUp = { client.sendCommand("BTN_NORTH:0") })
                        "BTN_Y" -> GamepadButton("Y", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_WEST:1") }, onUp = { client.sendCommand("BTN_WEST:0") })
                        "TRG_L" -> GamepadButton("LT", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("ABS_Z:255") }, onUp = { client.sendCommand("ABS_Z:0") })
                        "TRG_R" -> GamepadButton("RT", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("ABS_RZ:255") }, onUp = { client.sendCommand("ABS_RZ:0") })
                        "BMP_L" -> GamepadButton("LB", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_TL:1") }, onUp = { client.sendCommand("BTN_TL:0") })
                        "BMP_R" -> GamepadButton("RB", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_TR:1") }, onUp = { client.sendCommand("BTN_TR:0") })
                        "SEL" -> GamepadButton("Sel", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_SELECT:1") }, onUp = { client.sendCommand("BTN_SELECT:0") })
                        "STA" -> GamepadButton("St", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_START:1") }, onUp = { client.sendCommand("BTN_START:0") })
                        "HOME" -> GamepadButton("H", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_MODE:1") }, onUp = { client.sendCommand("BTN_MODE:0") })
                        "LSB" -> GamepadButton("L", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_THUMBL:1") }, onUp = { client.sendCommand("BTN_THUMBL:0") })
                        "RSB" -> GamepadButton("R", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_THUMBR:1") }, onUp = { client.sendCommand("BTN_THUMBR:0") })
                        "PDL" -> GamepadButton("L4", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_C:1") }, onUp = { client.sendCommand("BTN_C:0") })
                        "PDR" -> GamepadButton("R4", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_Z:1") }, onUp = { client.sendCommand("BTN_Z:0") })
                    }
                    if (isEditing) {
                        Box(modifier = Modifier.fillMaxSize().pointerInput(pc.id) { detectDragGestures(onDragStart = { isDragging = true; selectedId = pc.id }, onDragEnd = { isDragging = false }) { change, dragAmount -> change.consume(); val next = pc.offset.value + dragAmount; pc.offset.value = Offset(snap(next.x), snap(next.y)) } })
                        if (isSelected) {
                            // Top-Right: Delete
                            IconButton(onClick = { components.remove(pc); selectedId = -1L }, modifier = Modifier.align(Alignment.TopEnd).offset(12.dp, (-12).dp).size(24.dp).background(Color.Black.copy(0.6f), CircleShape).border(1.dp, DimWhite.copy(0.5f), CircleShape)) { Icon(Icons.Default.Close, null, tint = DimWhite, modifier = Modifier.size(14.dp)) }
                            
                            // Bottom-Left: D-Pad Settings
                            if (pc.type == "DPD") {
                                IconButton(onClick = { showDpadSettingsId = pc.id }, modifier = Modifier.align(Alignment.BottomStart).offset((-12).dp, 12.dp).size(24.dp).background(Color.Black.copy(0.6f), CircleShape).border(1.dp, DimWhite.copy(0.5f), CircleShape)) { Icon(Icons.Default.Settings, null, tint = DimWhite, modifier = Modifier.size(14.dp)) }
                            }

                            // Bottom-Right: Resize Handle
                            Box(modifier = Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp).size(28.dp).background(Color.Black.copy(0.6f), CircleShape).border(1.dp, DimWhite.copy(0.5f), CircleShape).pointerInput(pc.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newW = (pc.width.value + with(density){ dragAmount.x.toDp() }).coerceAtLeast(40.dp)
                                    val newH = (pc.height.value + with(density){ dragAmount.y.toDp() }).coerceAtLeast(40.dp)
                                    
                                    pc.width.value = with(density) { snap(newW.toPx()).toDp() }
                                    pc.height.value = with(density) { snap(newH.toPx()).toDp() }
                                }
                            }, contentAlignment = Alignment.Center) { Icon(Icons.Default.AspectRatio, null, tint = DimWhite, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }

        // Top Right Info Overlay
        val selectedComp = components.find { it.id == selectedId }
        if (isEditing && selectedComp != null) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(6.dp)).border(1.dp, DimWhite.copy(0.08f), RoundedCornerShape(6.dp)).padding(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                val xDp = with(density) { selectedComp.offset.value.x.toDp().value.roundToInt() }
                val yDp = with(density) { selectedComp.offset.value.y.toDp().value.roundToInt() }
                val wDp = selectedComp.width.value.value.roundToInt()
                val hDp = selectedComp.height.value.value.roundToInt()
                
                Text("POS: $xDp, $yDp", color = DimWhite.copy(0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("SIZE: ${wDp}x${hDp}", color = DimWhite.copy(0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Top Toolbar (Now only Reset/Refresh if Default)
        if (isEditing && activePreset == "Default") {
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.background(Color.Black.copy(0.8f), RoundedCornerShape(20.dp)).border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { components.clear(); parseAndLoad(DEFAULT_LAYOUT_STRING) }) { Icon(Icons.Default.Refresh, null, tint = DimWhite) }
                }
            }
        }



        // D-Pad Settings Overlay
        val dpadToEdit = components.find { it.id == showDpadSettingsId }
        if (dpadToEdit != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable { showDpadSettingsId = -1L }, contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp)).border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(16.dp)).padding(20.dp).clickable(enabled = false) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("D-PAD SETTINGS", color = DimWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    PropertySlider("Button Size", dpadToEdit.dpadButtonSize.value, 30f, 120f) { dpadToEdit.dpadButtonSize.value = it.dp }
                    PropertySlider("Spacing", dpadToEdit.dpadSpacing.value, 10f, 100f) { dpadToEdit.dpadSpacing.value = it.dp }
                    
                    Button(onClick = { showDpadSettingsId = -1L }, colors = ButtonDefaults.buttonColors(containerColor = DimWhite.copy(0.1f))) {
                        Text("DONE", color = DimWhite, fontSize = 11.sp)
                    }
                }
            }
        }


        if (isEditing) Box(modifier = Modifier.align(Alignment.CenterEnd).offset(x = drawerOffset).width(32.dp).height(64.dp).clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)).background(Color.Black.copy(0.8f)).border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)).clickable { showDrawer = !showDrawer }, contentAlignment = Alignment.Center) { Icon(if(showDrawer) Icons.Default.Close else Icons.Default.Build, null, tint = DimWhite.copy(0.8f), modifier = Modifier.size(16.dp)) }

        Box(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(drawerWidth).offset(x = drawerOffset + drawerWidth).background(Color.Black).border(1.dp, DimWhite.copy(0.2f))) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                // Editor Controls at top of sidebar - Single Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showGrid = !showGrid }, modifier = Modifier.size(32.dp)) {
                        Icon(if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff, null, tint = DimWhite, modifier = Modifier.size(20.dp))
                    }
                    
                    IconButton(onClick = { saveLayout(activePreset) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Save, null, tint = DimWhite, modifier = Modifier.size(20.dp))
                    }
                }
                
                HorizontalDivider(color = DimWhite.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

                ComponentCatalogGrid { tool -> 
                    val defaultSize = when(tool.type) {
                        "JOY", "CAM", "RJOY" -> 150.dp
                        "DPD" -> 160.dp
                        "PDL", "PDR" -> 60.dp
                        else -> 70.dp
                    }
                    val defaultHeight = if(tool.type == "PDL" || tool.type == "PDR") 100.dp else defaultSize
                        components.add(PlacedComponent(
                            System.currentTimeMillis() + components.size, 
                            tool.type, 
                            mutableStateOf(Offset(snap(300f), snap(300f))), 
                            mutableStateOf(defaultSize), 
                            mutableStateOf(defaultHeight),
                            mutableStateOf(tool.type == "RJOY"),
                            mutableStateOf(60.dp),
                            mutableStateOf(50.dp)
                        )) 
                }
            }
        }
    }
}

@Composable
private fun PropertySlider(label: String, value: Dp, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(200.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = DimWhite.copy(0.6f), fontSize = 10.sp)
            Text("${value.value.toInt()}", color = DimWhite, fontSize = 10.sp)
        }
        Slider(
            value = value.value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = DimWhite, activeTrackColor = DimWhite, inactiveTrackColor = DimWhite.copy(0.1f))
        )
    }
}

