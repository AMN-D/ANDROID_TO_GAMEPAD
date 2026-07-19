package com.peri.android_to_gamepad.ui.theme.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peri.android_to_gamepad.network.GamepadClient
import com.peri.android_to_gamepad.ui.theme.components.*
import kotlin.math.roundToInt

@Immutable
data class PlacedComponent(
    val id: Long,
    val type: String,
    val offset: MutableState<Offset>,
    val width: MutableState<Dp>,
    val height: MutableState<Dp>,
    val isLocked: MutableState<Boolean> = mutableStateOf(true),
    val isRightAnchored: MutableState<Boolean> = mutableStateOf(false),
    val dpadButtonSize: MutableState<Dp> = mutableStateOf(60.dp),
    val dpadSpacing: MutableState<Dp> = mutableStateOf(50.dp)
)

private val DimWhite = Color.White.copy(0.7f)
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
    var showScaleControls by remember { mutableStateOf(false) }
    var activePreset by remember { mutableStateOf(initialPreset) }
    var selectedId by remember { mutableLongStateOf(-1L) }
    val components = remember { mutableStateListOf<PlacedComponent>() }

    val drawerWidth = 180.dp
    val drawerOffset by animateDpAsState(targetValue = if (showDrawer && isEditing) (-drawerWidth) else 0.dp, label = "d")

    fun snap(value: Float): Float = (value / gridSizePx).roundToInt() * gridSizePx

    fun saveLayout(name: String) {
        val serialized = components.joinToString(";") { 
            val xDp = with(density) { it.offset.value.x.toDp().value }
            val yDp = with(density) { it.offset.value.y.toDp().value }
            "${it.type},$xDp,$yDp,${it.width.value.value},${it.height.value.value},${it.isLocked.value},${it.isRightAnchored.value},${it.dpadButtonSize.value.value},${it.dpadSpacing.value.value}" 
        }
        prefs.edit().putString("layout_$name", serialized).apply()
    }

    fun parseAndLoad(serialized: String) {
        serialized.split(";").forEach { item ->
            val parts = item.split(",")
            if (parts.size >= 5) {
                val xPx = with(density) { parts[1].toFloat().dp.toPx() }
                val yPx = with(density) { parts[2].toFloat().dp.toPx() }
                components.add(PlacedComponent(
                    System.currentTimeMillis() + components.size,
                    parts[0],
                    mutableStateOf(Offset(xPx, yPx)),
                    mutableStateOf(parts[3].toFloat().dp),
                    mutableStateOf(parts[4].toFloat().dp),
                    mutableStateOf(parts.getOrNull(5)?.toBoolean() ?: true),
                    mutableStateOf(parts.getOrNull(6)?.toBoolean() ?: false),
                    mutableStateOf(parts.getOrNull(7)?.toFloat()?.dp ?: 60.dp),
                    mutableStateOf(parts.getOrNull(8)?.toFloat()?.dp ?: 50.dp)
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

    fun mirrorComponent(pc: PlacedComponent) {
        val screenWidthPx = context.resources.displayMetrics.widthPixels.toFloat()
        val centerX = screenWidthPx / 2f
        val mirroredX = centerX + (centerX - pc.offset.value.x - with(density){ pc.width.value.toPx() })
        components.add(PlacedComponent(
            System.currentTimeMillis(),
            pc.type,
            mutableStateOf(Offset(snap(mirroredX), pc.offset.value.y)),
            mutableStateOf(pc.width.value),
            mutableStateOf(pc.height.value),
            mutableStateOf(pc.isLocked.value),
            mutableStateOf(!pc.isRightAnchored.value),
            mutableStateOf(pc.dpadButtonSize.value),
            mutableStateOf(pc.dpadSpacing.value)
        ))
    }

    BackHandler { when { showDrawer -> showDrawer = false; else -> onBack() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isEditing) {
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
                        "CAM" -> CameraZone(modifier = Modifier.fillMaxSize(), onUpdate = { x, y -> client.sendCommand("ABS_RX:${(x * 32767).toInt()}"); client.sendCommand("ABS_RY:${(y * 32767).toInt()}") })
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
                            IconButton(onClick = { components.remove(pc); selectedId = -1L }, modifier = Modifier.align(Alignment.TopEnd).offset(12.dp, (-12).dp).size(20.dp).background(Color.Black.copy(0.6f), CircleShape).border(1.dp, DimWhite.copy(0.5f), CircleShape)) { Icon(Icons.Default.Close, null, tint = DimWhite, modifier = Modifier.size(12.dp)) }
                        }
                    }
                }
            }
        }

        // Top Toolbar
        if (isEditing) {
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.background(Color.Black.copy(0.8f), RoundedCornerShape(20.dp)).border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isEditing = !isEditing; if(!isEditing) { showDrawer = false; selectedId = -1L; showScaleControls = false } }) { Icon(if (isEditing) Icons.Default.PlayArrow else Icons.Default.Build, null, tint = DimWhite) }
                    if (isEditing) {
                        if (activePreset == "Default") {
                            IconButton(onClick = { components.clear(); parseAndLoad(DEFAULT_LAYOUT_STRING) }) { Icon(Icons.Default.Refresh, null, tint = DimWhite) }
                        }
                        IconButton(onClick = { saveLayout(activePreset) }) { Icon(Icons.Default.Save, null, tint = DimWhite) }
                    }
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = DimWhite.copy(0.6f)) }
                }
            }
        }

        // Selection Properties (Bottom Right)
        val selectedComp = components.find { it.id == selectedId }
        if (isEditing && selectedComp != null) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = if(showDrawer) 196.dp else 16.dp).background(Color.Black.copy(0.8f), RoundedCornerShape(12.dp)).border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(12.dp)).padding(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { mirrorComponent(selectedComp) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ContentCopy, null, tint = DimWhite) }
                        VerticalDivider(modifier = Modifier.height(20.dp), color = DimWhite.copy(0.1f))
                        CoordInput(label = "X", value = with(density){ selectedComp.offset.value.x.toDp().value }, density = density) { 
                            selectedComp.offset.value = Offset(with(density){ it.dp.toPx() }, selectedComp.offset.value.y)
                        }
                        CoordInput(label = "Y", value = with(density){ selectedComp.offset.value.y.toDp().value }, density = density) { 
                            selectedComp.offset.value = Offset(selectedComp.offset.value.x, with(density){ it.dp.toPx() })
                        }
                        IconButton(onClick = { showScaleControls = !showScaleControls }, modifier = Modifier.size(24.dp)) { Icon(if(showScaleControls) Icons.Default.KeyboardArrowDown else Icons.Default.AspectRatio, null, tint = DimWhite) }
                    }
                    if (showScaleControls) {
                        Spacer(Modifier.height(12.dp))
                        // Width Control
                        ScaleRow(label = "W", value = selectedComp.width.value, onValueChange = { 
                            selectedComp.width.value = it
                            if (selectedComp.isLocked.value) selectedComp.height.value = it
                        }, step = gridSizePx, density = density)
                        Spacer(Modifier.height(8.dp))
                        // Height Control
                        ScaleRow(label = "H", value = selectedComp.height.value, onValueChange = { 
                            selectedComp.height.value = it
                            if (selectedComp.isLocked.value) selectedComp.width.value = it
                        }, step = gridSizePx, density = density)
                        // Lock Toggle
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedComp.isLocked.value = !selectedComp.isLocked.value }) {
                            Checkbox(checked = selectedComp.isLocked.value, onCheckedChange = { selectedComp.isLocked.value = it }, colors = CheckboxDefaults.colors(checkedColor = DimWhite, checkmarkColor = Color.Black, uncheckedColor = DimWhite.copy(0.4f)))
                            Text("LOCK ASPECT", color = DimWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (selectedComp.type == "JOY" || selectedComp.type == "RJOY") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedComp.isRightAnchored.value = !selectedComp.isRightAnchored.value }) {
                                Checkbox(checked = selectedComp.isRightAnchored.value, onCheckedChange = { selectedComp.isRightAnchored.value = it }, colors = CheckboxDefaults.colors(checkedColor = DimWhite, checkmarkColor = Color.Black, uncheckedColor = DimWhite.copy(0.4f)))
                                Text("STICK TO RIGHT", color = DimWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        if (selectedComp.type == "DPD") {
                            Spacer(Modifier.height(12.dp))
                            ScaleRow(label = "BS", value = selectedComp.dpadButtonSize.value, onValueChange = { selectedComp.dpadButtonSize.value = it }, step = with(density){ 2.dp.toPx() }, min = 20f, max = 200f, density = density)
                            Spacer(Modifier.height(8.dp))
                            ScaleRow(label = "SP", value = selectedComp.dpadSpacing.value, onValueChange = { selectedComp.dpadSpacing.value = it }, step = with(density){ 1.dp.toPx() }, min = 0f, max = 100f, density = density)
                        }
                    }
                    // Nudge D-Pad
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.size(80.dp)) {
                        val nudge = { dx: Float, dy: Float -> 
                            selectedComp.offset.value = Offset(selectedComp.offset.value.x + (dx * gridSizePx), selectedComp.offset.value.y + (dy * gridSizePx))
                        }
                        IconButton(onClick = { nudge(0f, -1f) }, modifier = Modifier.align(Alignment.TopCenter).size(24.dp)) { Icon(Icons.Default.KeyboardArrowUp, null, tint = DimWhite) }
                        IconButton(onClick = { nudge(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).size(24.dp)) { Icon(Icons.Default.KeyboardArrowDown, null, tint = DimWhite) }
                        IconButton(onClick = { nudge(-1f, 0f) }, modifier = Modifier.align(Alignment.CenterStart).size(24.dp)) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = DimWhite) }
                        IconButton(onClick = { nudge(1f, 0f) }, modifier = Modifier.align(Alignment.CenterEnd).size(24.dp)) { Icon(Icons.Default.KeyboardArrowRight, null, tint = DimWhite) }
                    }
                }
            }
        }

        if (isEditing) Box(modifier = Modifier.align(Alignment.CenterEnd).offset(x = drawerOffset).width(32.dp).height(64.dp).clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)).background(Color.Black.copy(0.8f)).border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)).clickable { showDrawer = !showDrawer }, contentAlignment = Alignment.Center) { Icon(if(showDrawer) Icons.Default.Close else Icons.Default.Build, null, tint = DimWhite.copy(0.8f), modifier = Modifier.size(16.dp)) }

        Box(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(drawerWidth).offset(x = drawerOffset + drawerWidth).background(Color.Black).border(1.dp, DimWhite.copy(0.2f))) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 24.dp)) {
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
                        mutableStateOf(true),
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
private fun ScaleRow(label: String, value: Dp, onValueChange: (Dp) -> Unit, step: Float, min: Float = 40f, max: Float = 400f, density: androidx.compose.ui.unit.Density) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = DimWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(12.dp))
        IconButton(onClick = { 
            val n = (with(density){ value.toPx() } - step).coerceAtLeast(with(density){ min.dp.toPx() })
            onValueChange(with(density){ n.toDp() })
        }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Remove, null, tint = DimWhite) }
        
        Slider(value = value.value, onValueChange = { onValueChange(it.dp) }, valueRange = min..max, modifier = Modifier.width(70.dp), colors = SliderDefaults.colors(thumbColor = DimWhite, activeTrackColor = DimWhite, inactiveTrackColor = DimWhite.copy(0.2f)))
        
        IconButton(onClick = { 
            val n = (with(density){ value.toPx() } + step).coerceAtMost(with(density){ max.dp.toPx() })
            onValueChange(with(density){ n.toDp() })
        }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Add, null, tint = DimWhite) }

        CoordInput(label = "", value = value.value, density = density) { onValueChange(it.dp) }
    }
}

@Composable
private fun CoordInput(label: String, value: Float, density: androidx.compose.ui.unit.Density, onValueChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toInt().toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = DimWhite.copy(0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        BasicTextField(
            value = text,
            onValueChange = { 
                text = it
                it.toFloatOrNull()?.let { f -> onValueChange(f) }
            },
            textStyle = TextStyle(color = DimWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(DimWhite),
            modifier = Modifier.width(35.dp).background(Color.White.copy(0.05f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
