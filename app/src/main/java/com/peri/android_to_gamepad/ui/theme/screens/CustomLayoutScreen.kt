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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.peri.android_to_gamepad.network.GamepadClient
import com.peri.android_to_gamepad.ui.theme.DimWhite
import com.peri.android_to_gamepad.ui.theme.components.*
import kotlin.math.roundToInt

data class PlacedComponent(
    val id: Long,
    val type: String,
    val offset: MutableState<Offset>,
    val width: MutableState<Dp>,
    val height: MutableState<Dp>,
    val isRightAnchored: MutableState<Boolean> = mutableStateOf(false),
    val dpadButtonSize: MutableState<Dp> = mutableStateOf(60.dp),
    val dpadSpacing: MutableState<Dp> = mutableStateOf(50.dp),
    val isHorizontal: MutableState<Boolean> = mutableStateOf(false)
)

private val GridColor = Color.White.copy(0.18f)
private const val GRID_SIZE_DP = 20
// v3: type,x,y,w,h,isRightAnchored,bs,sp,isHorizontal
private const val LAYOUT_VERSION_PREFIX = "v3|"
private const val MIN_CONTROL_DP = 40f
private val RELEASE_ALL_COMMANDS = listOf(
    "ABS_X:0", "ABS_Y:0", "ABS_RX:0", "ABS_RY:0", "ABS_Z:0", "ABS_RZ:0",
    "ABS_HAT0X:0", "ABS_HAT0Y:0",
    "BTN_START:0", "BTN_SELECT:0", "BTN_TL:0", "BTN_TR:0",
    "BTN_WEST:0", "BTN_EAST:0", "BTN_NORTH:0", "BTN_SOUTH:0",
    "BTN_MODE:0", "BTN_THUMBL:0", "BTN_THUMBR:0", "BTN_C:0", "BTN_Z:0"
)

// Legacy default: type,x,y,w,h,isLocked,isRightAnchored,bs,sp
private const val DEFAULT_LAYOUT_STRING =
    "JOY,0.0,80.0,325.0,150.0,true,false,60,50;DPD,140.0,180.0,183.0,183.0,true,false,60,50;RJOY,440.0,180.0,325.0,183.0,true,false,60,50;BTN_Y,660.0,100.0,50.0,50.0,true,false,60,50;BTN_X,620.0,140.0,50.0,50.0,true,false,60,50;BTN_B,700.0,140.0,50.0,50.0,true,false,60,50;BTN_A,660.0,180.0,50.0,50.0,true,false,60,50;SEL,350.0,40.0,40.0,40.0,true,false,60,50;STA,410.0,40.0,40.0,40.0,true,false,60,50;BMP_L,120.0,0.0,65.0,65.0,true,false,60,50;BMP_R,600.0,0.0,65.0,65.0,true,false,60,50;TRG_L,40.0,0.0,65.0,65.0,true,false,60,50;TRG_R,680.0,0.0,65.0,65.0,true,false,60,50"

@Composable
fun CustomLayoutScreen(
    client: GamepadClient,
    initialPreset: String = "Default",
    startInEditMode: Boolean = false,
    onBack: () -> Unit
) {
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
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val components = remember { mutableStateListOf<PlacedComponent>() }
    var nextId by remember { mutableLongStateOf(1L) }

    val drawerWidth = 180.dp
    val drawerOffset by animateDpAsState(
        targetValue = if (showDrawer && isEditing) (-drawerWidth) else 0.dp,
        label = "d"
    )

    fun snap(value: Float): Float =
        if (showGrid) (value / gridSizePx).roundToInt() * gridSizePx else value

    fun clampOffset(x: Float, y: Float, widthPx: Float, heightPx: Float): Offset {
        val maxX = (canvasSize.width - widthPx).coerceAtLeast(0f)
        val maxY = (canvasSize.height - heightPx).coerceAtLeast(0f)
        return Offset(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
    }

    fun commitPosition(pc: PlacedComponent) {
        val wPx = with(density) { pc.width.value.toPx() }
        val hPx = with(density) { pc.height.value.toPx() }
        val snapped = Offset(snap(pc.offset.value.x), snap(pc.offset.value.y))
        pc.offset.value = clampOffset(snapped.x, snapped.y, wPx, hPx)
    }

    fun commitSize(pc: PlacedComponent) {
        val minPx = with(density) { MIN_CONTROL_DP.dp.toPx() }
        var wPx = with(density) { pc.width.value.toPx() }.coerceAtLeast(minPx)
        var hPx = with(density) { pc.height.value.toPx() }.coerceAtLeast(minPx)
        wPx = snap(wPx).coerceAtLeast(minPx)
        hPx = snap(hPx).coerceAtLeast(minPx)
        if (canvasSize.width > 0) wPx = wPx.coerceAtMost(canvasSize.width.toFloat())
        if (canvasSize.height > 0) hPx = hPx.coerceAtMost(canvasSize.height.toFloat())
        pc.width.value = with(density) { wPx.toDp() }
        pc.height.value = with(density) { hPx.toDp() }
        commitPosition(pc)
    }

    fun releaseAllControls() {
        RELEASE_ALL_COMMANDS.forEach { client.sendCommand(it) }
    }

    fun saveLayout(name: String) {
        val body = components.joinToString(";") {
            val xDp = with(density) { it.offset.value.x.toDp().value }
            val yDp = with(density) { it.offset.value.y.toDp().value }
            "${it.type},$xDp,$yDp,${it.width.value.value},${it.height.value.value}," +
                "${it.isRightAnchored.value},${it.dpadButtonSize.value.value},${it.dpadSpacing.value.value},${it.isHorizontal.value}"
        }
        prefs.edit().putString("layout_$name", LAYOUT_VERSION_PREFIX + body).apply()
        Toast.makeText(context, "Layout Saved: $name", Toast.LENGTH_SHORT).show()
    }

    fun parseItem(parts: List<String>): PlacedComponent? {
        if (parts.size < 5) return null
        return try {
            val xPx = with(density) { parts[1].toFloat().dp.toPx() }
            val yPx = with(density) { parts[2].toFloat().dp.toPx() }
            val w = parts[3].toFloat().dp
            val h = parts[4].toFloat().dp

            // v3: type,x,y,w,h,isRight,bs,sp,isHorizontal (9 fields)
            // v2: type,x,y,w,h,isRight,bs,sp (8 fields)
            // legacy locked: type,x,y,w,h,isLocked,isRight,bs,sp (9 fields, parts[8] is float not bool)
            val part8IsBool = parts.getOrNull(8)?.let { it == "true" || it == "false" } ?: false
            val isRight: Boolean
            val bs: Dp
            val sp: Dp
            val isHoriz: Boolean
            when {
                parts.size >= 9 && part8IsBool -> {
                    isRight = parts[5].toBoolean()
                    bs = parts[6].toFloat().dp
                    sp = parts[7].toFloat().dp
                    isHoriz = parts[8].toBoolean()
                }
                parts.size >= 9 -> {
                    isRight = parts[6].toBoolean()
                    bs = parts[7].toFloat().dp
                    sp = parts[8].toFloat().dp
                    isHoriz = false
                }
                parts.size >= 8 -> {
                    isRight = parts[5].toBoolean()
                    bs = parts[6].toFloat().dp
                    sp = parts[7].toFloat().dp
                    isHoriz = false
                }
                else -> {
                    isRight = false
                    bs = 60.dp
                    sp = 50.dp
                    isHoriz = false
                }
            }

            val id = nextId++
            PlacedComponent(
                id = id,
                type = parts[0],
                offset = mutableStateOf(Offset(xPx, yPx)),
                width = mutableStateOf(w),
                height = mutableStateOf(h),
                isRightAnchored = mutableStateOf(isRight),
                dpadButtonSize = mutableStateOf(bs),
                dpadSpacing = mutableStateOf(sp),
                isHorizontal = mutableStateOf(isHoriz)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseAndLoad(serialized: String) {
        val body = when {
            serialized.startsWith("v3|") -> serialized.removePrefix("v3|")
            serialized.startsWith("v2|") -> serialized.removePrefix("v2|")
            else -> serialized
        }
        body.split(";").forEach { item ->
            if (item.isBlank()) return@forEach
            parseItem(item.split(","))?.let { components.add(it) }
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

    fun spawnComponent(type: String) {
        val defaultSize = when (type) {
            "JOY", "CAM", "RJOY" -> 150.dp
            "DPD" -> 160.dp
            "TRG_L_ANALOG", "TRG_R_ANALOG" -> 50.dp
            "PDL", "PDR" -> 60.dp
            else -> 70.dp
        }
        val defaultHeight = when (type) {
            "PDL", "PDR" -> 100.dp
            "TRG_L_ANALOG", "TRG_R_ANALOG" -> 120.dp
            else -> defaultSize
        }
        val wPx = with(density) { defaultSize.toPx() }
        val hPx = with(density) { defaultHeight.toPx() }
        val centerX = if (canvasSize.width > 0) (canvasSize.width - wPx) / 2f else with(density) { 120.dp.toPx() }
        val centerY = if (canvasSize.height > 0) (canvasSize.height - hPx) / 2f else with(density) { 80.dp.toPx() }
        val id = nextId++
        components.add(
            PlacedComponent(
                id = id,
                type = type,
                offset = mutableStateOf(clampOffset(centerX, centerY, wPx, hPx)),
                width = mutableStateOf(defaultSize),
                height = mutableStateOf(defaultHeight),
                isRightAnchored = mutableStateOf(type == "RJOY"),
                dpadButtonSize = mutableStateOf(60.dp),
                dpadSpacing = mutableStateOf(50.dp),
                isHorizontal = mutableStateOf(false)
            )
        )
        selectedId = id
        showDrawer = false
    }

    LaunchedEffect(Unit) {
        loadLayout(initialPreset)
    }

    DisposableEffect(Unit) {
        onDispose { releaseAllControls() }
    }

    BackHandler {
        when {
            showDpadSettingsId != -1L -> showDpadSettingsId = -1L
            showDrawer -> showDrawer = false
            else -> {
                releaseAllControls()
                onBack()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { canvasSize = it }
    ) {
        // Empty-canvas tap deselects
        if (isEditing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { selectedId = -1L }
                    }
            )
        }

        if (isEditing && showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = gridSizePx
                if (step <= 0f) return@Canvas
                for (x in 0..(size.width / step).toInt()) {
                    drawLine(GridColor, Offset(x * step, 0f), Offset(x * step, size.height), 1f)
                }
                for (y in 0..(size.height / step).toInt()) {
                    drawLine(GridColor, Offset(0f, y * step), Offset(size.width, y * step), 1f)
                }
            }
        }

        val ordered = if (selectedId == -1L) {
            components.toList()
        } else {
            components.filter { it.id != selectedId } + components.filter { it.id == selectedId }
        }

        ordered.forEach { pc ->
            key(pc.id) {
                val isSelected = selectedId == pc.id
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                pc.offset.value.x.roundToInt(),
                                pc.offset.value.y.roundToInt()
                            )
                        }
                        .size(pc.width.value, pc.height.value)
                        .zIndex(if (isSelected) 10f else 0f)
                        .then(
                            if (isEditing) {
                                Modifier
                                    .border(
                                        1.dp,
                                        if (isSelected) DimWhite.copy(0.5f) else DimWhite.copy(0.15f),
                                        RoundedCornerShape(2.dp)
                                    )
                                    .pointerInput(pc.id) {
                                        detectTapGestures { selectedId = pc.id }
                                    }
                            } else Modifier
                        )
                ) {
                    when (pc.type) {
                        "JOY" -> JoystickZone(
                            modifier = Modifier.fillMaxSize(),
                            isRightSide = pc.isRightAnchored.value,
                            isEditing = isEditing,
                            onUpdate = { x, y ->
                                client.sendCommand("ABS_X:${(x * 32767).toInt()}")
                                client.sendCommand("ABS_Y:${(y * 32767).toInt()}")
                            }
                        )
                        "CAM" -> CameraZone(
                            modifier = Modifier.fillMaxSize(),
                            isEditing = isEditing,
                            onUpdate = { x, y ->
                                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
                            }
                        )
                        "RJOY" -> JoystickZone(
                            modifier = Modifier.fillMaxSize(),
                            isRightSide = pc.isRightAnchored.value,
                            isEditing = isEditing,
                            onUpdate = { x, y ->
                                client.sendCommand("ABS_RX:${(x * 32767).toInt()}")
                                client.sendCommand("ABS_RY:${(y * 32767).toInt()}")
                            }
                        )
                        "SWP" -> SwipeDPad(
                            modifier = Modifier.fillMaxSize(),
                            isEditing = isEditing,
                            onDirectionChange = { x, y ->
                                client.sendCommand("ABS_HAT0X:$x")
                                client.sendCommand("ABS_HAT0Y:$y")
                            }
                        )
                        "DPD" -> TraditionalDPad(
                            modifier = Modifier.fillMaxSize(),
                            buttonSize = pc.dpadButtonSize.value,
                            spacing = pc.dpadSpacing.value,
                            isEditing = isEditing,
                            onDirectionChange = { x, y ->
                                client.sendCommand("ABS_HAT0X:$x")
                                client.sendCommand("ABS_HAT0Y:$y")
                            }
                        )
                        "BTN_A" -> GamepadButton("A", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_SOUTH:1") }, onUp = { client.sendCommand("BTN_SOUTH:0") })
                        "BTN_B" -> GamepadButton("B", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_EAST:1") }, onUp = { client.sendCommand("BTN_EAST:0") })
                        "BTN_X" -> GamepadButton("X", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_NORTH:1") }, onUp = { client.sendCommand("BTN_NORTH:0") })
                        "BTN_Y" -> GamepadButton("Y", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("BTN_WEST:1") }, onUp = { client.sendCommand("BTN_WEST:0") })
                        "TRG_L" -> GamepadButton("LT", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("ABS_Z:255") }, onUp = { client.sendCommand("ABS_Z:0") })
                        "TRG_R" -> GamepadButton("RT", modifier = Modifier.fillMaxSize(), isEditing = isEditing, onDown = { client.sendCommand("ABS_RZ:255") }, onUp = { client.sendCommand("ABS_RZ:0") })
                        "TRG_L_ANALOG" -> AnalogSlider(label = "LT", modifier = Modifier.fillMaxSize(), isHorizontal = pc.isHorizontal.value, isEditing = isEditing, onValueChange = { client.sendCommand("ABS_Z:${(it * 255).toInt()}") })
                        "TRG_R_ANALOG" -> AnalogSlider(label = "RT", modifier = Modifier.fillMaxSize(), isHorizontal = pc.isHorizontal.value, isEditing = isEditing, onValueChange = { client.sendCommand("ABS_RZ:${(it * 255).toInt()}") })
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pc.id, showGrid, canvasSize) {
                                    detectDragGestures(
                                        onDragStart = { selectedId = pc.id },
                                        onDragEnd = { commitPosition(pc) },
                                        onDragCancel = { commitPosition(pc) }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val wPx = with(density) { pc.width.value.toPx() }
                                        val hPx = with(density) { pc.height.value.toPx() }
                                        val next = pc.offset.value + dragAmount
                                        pc.offset.value = clampOffset(next.x, next.y, wPx, hPx)
                                    }
                                }
                        )

                        if (isSelected) {
                            if (pc.type == "TRG_L_ANALOG" || pc.type == "TRG_R_ANALOG") {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset((-8).dp, (-8).dp)
                                        .size(28.dp)
                                        .background(Color.Black.copy(0.6f), CircleShape)
                                        .border(1.dp, DimWhite.copy(0.5f), CircleShape)
                                        .clickable {
                                            pc.isHorizontal.value = !pc.isHorizontal.value
                                            val oldW = pc.width.value
                                            pc.width.value = pc.height.value
                                            pc.height.value = oldW
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.RotateRight, null, tint = DimWhite, modifier = Modifier.size(14.dp))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(8.dp, (-8).dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(0.6f), CircleShape)
                                    .border(1.dp, DimWhite.copy(0.5f), CircleShape)
                                    .clickable {
                                        components.removeAll { it.id == pc.id }
                                        selectedId = -1L
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, null, tint = DimWhite, modifier = Modifier.size(14.dp))
                            }

                            if (pc.type == "DPD") {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset((-8).dp, 8.dp)
                                        .size(28.dp)
                                        .background(Color.Black.copy(0.6f), CircleShape)
                                        .border(1.dp, DimWhite.copy(0.5f), CircleShape)
                                        .clickable { showDpadSettingsId = pc.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Settings, null, tint = DimWhite, modifier = Modifier.size(14.dp))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(8.dp, 8.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(0.6f), CircleShape)
                                    .border(1.dp, DimWhite.copy(0.5f), CircleShape)
                                    .pointerInput(pc.id, showGrid, canvasSize) {
                                        detectDragGestures(
                                            onDragEnd = { commitSize(pc) },
                                            onDragCancel = { commitSize(pc) }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            val minDp = MIN_CONTROL_DP.dp
                                            val newW = (pc.width.value + with(density) { dragAmount.x.toDp() }).coerceAtLeast(minDp)
                                            val newH = (pc.height.value + with(density) { dragAmount.y.toDp() }).coerceAtLeast(minDp)
                                            pc.width.value = newW
                                            pc.height.value = newH
                                            val wPx = with(density) { newW.toPx() }
                                            val hPx = with(density) { newH.toPx() }
                                            pc.offset.value = clampOffset(pc.offset.value.x, pc.offset.value.y, wPx, hPx)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AspectRatio, null, tint = DimWhite, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Top toolbar only in edit mode (play mode stays chrome-free)
        if (isEditing) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(Color.Black.copy(0.8f), RoundedCornerShape(20.dp))
                    .border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showGrid = !showGrid }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                        contentDescription = "Toggle grid",
                        tint = DimWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { saveLayout(activePreset) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = DimWhite, modifier = Modifier.size(20.dp))
                }
                if (activePreset == "Default") {
                    IconButton(
                        onClick = {
                            components.clear()
                            selectedId = -1L
                            parseAndLoad(DEFAULT_LAYOUT_STRING)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = DimWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        val selectedComp = components.find { it.id == selectedId }
        if (isEditing && selectedComp != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(0.5f), RoundedCornerShape(6.dp))
                    .border(1.dp, DimWhite.copy(0.08f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
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

        val dpadToEdit = components.find { it.id == showDpadSettingsId }
        if (dpadToEdit != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.4f))
                    .clickable { showDpadSettingsId = -1L },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                        .border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                        .clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("D-PAD SETTINGS", color = DimWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    PropertySlider("Button Size", dpadToEdit.dpadButtonSize.value, 30f, 120f) {
                        dpadToEdit.dpadButtonSize.value = it.dp
                    }
                    PropertySlider("Spacing", dpadToEdit.dpadSpacing.value, 10f, 100f) {
                        dpadToEdit.dpadSpacing.value = it.dp
                    }
                    Button(
                        onClick = { showDpadSettingsId = -1L },
                        colors = ButtonDefaults.buttonColors(containerColor = DimWhite.copy(0.1f))
                    ) {
                        Text("DONE", color = DimWhite, fontSize = 11.sp)
                    }
                }
            }
        }

        if (isEditing) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = drawerOffset)
                    .width(32.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(Color.Black.copy(0.8f))
                    .border(1.dp, DimWhite.copy(0.2f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .clickable { showDrawer = !showDrawer },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (showDrawer) Icons.Default.Close else Icons.Default.Build,
                    null,
                    tint = DimWhite.copy(0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .offset(x = drawerOffset + drawerWidth)
                    .background(Color.Black)
                    .border(1.dp, DimWhite.copy(0.2f))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                    Text(
                        "ADD CONTROL",
                        color = DimWhite.copy(0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(
                        color = DimWhite.copy(0.1f),
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                    ComponentCatalogGrid { tool -> spawnComponent(tool.type) }
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
            colors = SliderDefaults.colors(
                thumbColor = DimWhite,
                activeTrackColor = DimWhite,
                inactiveTrackColor = DimWhite.copy(0.1f)
            )
        )
    }
}
