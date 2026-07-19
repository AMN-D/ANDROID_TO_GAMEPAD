package com.peri.android_to_gamepad.ui.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
sealed class ComponentTool(val name: String, val type: String, val label: String = "") {
    object Joystick : ComponentTool("Joystick", "JOY")
    object Camera : ComponentTool("Camera", "CAM")
    object SwipePad : ComponentTool("D-Pad", "SWP")
    object ButtonA : ComponentTool("Button A", "BTN_A", "A")
    object ButtonB : ComponentTool("Button B", "BTN_B", "B")
    object ButtonX : ComponentTool("Button X", "BTN_X", "X")
    object ButtonY : ComponentTool("Button Y", "BTN_Y", "Y")
    object TriggerL : ComponentTool("L Trigger", "TRG_L", "LT")
    object TriggerR : ComponentTool("R Trigger", "TRG_R", "RT")
    object BumperL : ComponentTool("L Bumper", "BMP_L", "LB")
    object BumperR : ComponentTool("R Bumper", "BMP_R", "RB")
    object Select : ComponentTool("Select", "SEL", "Sel")
    object Start : ComponentTool("Start", "STA", "St")
}

val ALL_TOOLS = listOf(
    ComponentTool.Joystick, ComponentTool.Camera, ComponentTool.SwipePad,
    ComponentTool.ButtonA, ComponentTool.ButtonB, ComponentTool.ButtonX, ComponentTool.ButtonY,
    ComponentTool.TriggerL, ComponentTool.TriggerR, ComponentTool.BumperL, ComponentTool.BumperR,
    ComponentTool.Select, ComponentTool.Start
)

@Composable
fun ComponentCatalogCard(tool: ComponentTool, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                when (tool) {
                    is ComponentTool.Joystick -> JoystickVisual(baseDiameter = 30.dp, thumbDiameter = 12.dp, thumbOffset = Offset(9f, 9f), isEditing = true)
                    is ComponentTool.Camera -> CameraVisual(modifier = Modifier.size(30.dp), alpha = 0.5f, isEditing = true)
                    is ComponentTool.SwipePad -> SwipeVisual(modifier = Modifier.size(32.dp, 18.dp), accentColor = Color.White, borderAlpha = 0.5f, textAlpha = 0.7f, textScale = 1f, displayText = "⊕", isEditing = true)
                    else -> ButtonVisual(label = tool.label, diameter = 30.dp, accentColor = Color.White, fillAlpha = 0.1f, borderAlpha = 0.4f, scale = 1f, textAlpha = 0.6f, isEditing = true)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = tool.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
fun ComponentCatalogGrid(onToolSelected: (ComponentTool) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(ALL_TOOLS) { tool ->
            ComponentCatalogCard(tool = tool, onClick = { onToolSelected(tool) })
        }
    }
}