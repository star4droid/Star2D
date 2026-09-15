package com.star4droid.star2d.unityui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class ConnectingPortType {
    Next,
    TrueBranch,
    FalseBranch,
    AfterBranch
}

data class ActiveConnectingState(
    val sourceNodeId: String,
    val portType: ConnectingPortType
)

data class LivePinDrag(
    val sourceNodeId: String,
    val portType: ConnectingPortType,
    val startX: Float,
    val startY: Float,
    var currentX: Float,
    var currentY: Float
)

/**
 * Unity Visual Scripting (Bolt-style) Canvas with Draggable Nodes, Wires,
 * and Top Toolbar. Zero Material 3 components.
 */
@Composable
fun VisualScriptingCanvas(
    codePath: String,
    hints: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val visualPath = remember(codePath) {
        if (codePath.endsWith(".visual")) codePath else "$codePath.visual"
    }
    val javaPath = remember(codePath) {
        if (codePath.endsWith(".visual")) {
            codePath.substring(0, codePath.lastIndexOf(".")) + ".java"
        } else {
            "$codePath.java"
        }
    }

    // Nodes state
    val nodes = remember { mutableStateListOf<ScriptNode>() }
    var nextNodeIdCounter by remember { mutableIntStateOf(100) }

    // Canvas viewport pan & zoom
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    // Connection in progress
    var activeConnection by remember { mutableStateOf<ActiveConnectingState?>(null) }
    var livePinDrag by remember { mutableStateOf<LivePinDrag?>(null) }

    // Modals
    var showNodeLibrary by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var exportedCodeText by remember { mutableStateOf("") }
    var activeEditingField by remember { mutableStateOf<Pair<ScriptNode, ScriptNodeField>?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Load initial nodes
    LaunchedEffect(visualPath) {
        val loaded = ScriptNodeEngine.loadFromFile(visualPath)
        nodes.clear()
        nodes.addAll(loaded)
        // Find highest existing ID to prevent collision
        val maxId = loaded.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0
        nextNodeIdCounter = maxId + 1
    }

    fun saveNodes() {
        ScriptNodeEngine.saveToFile(visualPath, nodes)
        val code = ScriptNodeEngine.exportCode(nodes)
        try {
            java.io.File(javaPath).writeText(code)
            statusMessage = "Nodes & code saved successfully!"
        } catch (e: Exception) {
            statusMessage = "Saved nodes (code write warning: ${e.localizedMessage})"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnityColors.Background)
    ) {
        // Main Interactive Canvas Area (Pan & Zoom + Grid + Wires + Nodes)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panX += dragAmount.x
                        panY += dragAmount.y
                    }
                }
        ) {
            // 1. Technical Grid Background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridSize = 32f * zoomScale
                val majorGridSize = gridSize * 4

                val startX = (panX % gridSize)
                val startY = (panY % gridSize)

                var curX = startX
                while (curX < size.width) {
                    drawLine(
                        color = UnityColors.CanvasGridLine,
                        start = Offset(curX, 0f),
                        end = Offset(curX, size.height),
                        strokeWidth = 1f
                    )
                    curX += gridSize
                }

                var curY = startY
                while (curY < size.height) {
                    drawLine(
                        color = UnityColors.CanvasGridLine,
                        start = Offset(0f, curY),
                        end = Offset(size.width, curY),
                        strokeWidth = 1f
                    )
                    curY += gridSize
                }

                // Major grid lines
                var majorX = (panX % majorGridSize)
                while (majorX < size.width) {
                    drawLine(
                        color = UnityColors.CanvasMajorGridLine,
                        start = Offset(majorX, 0f),
                        end = Offset(majorX, size.height),
                        strokeWidth = 1.5f
                    )
                    majorX += majorGridSize
                }
                var majorY = (panY % majorGridSize)
                while (majorY < size.height) {
                    drawLine(
                        color = UnityColors.CanvasMajorGridLine,
                        start = Offset(0f, majorY),
                        end = Offset(size.width, majorY),
                        strokeWidth = 1.5f
                    )
                    majorY += majorGridSize
                }
            }

            // 2. Bézier Connection Wires
            Canvas(modifier = Modifier.fillMaxSize()) {
                val nodeMap = nodes.associateBy { it.id }

                fun drawWire(fromNode: ScriptNode, targetNodeId: String?, wireColor: Color, pinOffsetY: Float) {
                    if (targetNodeId == null) return
                    val target = nodeMap[targetNodeId] ?: return

                    val nodeWidth = 200f * zoomScale
                    val startX = (fromNode.x * zoomScale) + panX + nodeWidth
                    val startY = (fromNode.y * zoomScale) + panY + (pinOffsetY * zoomScale)

                    val endX = (target.x * zoomScale) + panX
                    val endY = (target.y * zoomScale) + panY + (20f * zoomScale)

                    val dx = kotlin.math.abs(endX - startX) * 0.5f
                    val control1 = Offset(startX + dx, startY)
                    val control2 = Offset(endX - dx, endY)

                    val path = Path().apply {
                        moveTo(startX, startY)
                        cubicTo(control1.x, control1.y, control2.x, control2.y, endX, endY)
                    }

                    drawPath(
                        path = path,
                        color = wireColor,
                        style = Stroke(width = 3f * zoomScale)
                    )

                    // Draw end arrow/circle indicator
                    drawCircle(
                        color = wireColor,
                        radius = 4f * zoomScale,
                        center = Offset(endX, endY)
                    )
                }

                for (node in nodes) {
                    if (node.isBooleanNode) {
                        drawWire(node, node.nextTrueId, UnityColors.WireTrue, 48f)
                        drawWire(node, node.nextFalseId, UnityColors.WireFalse, 80f)
                        drawWire(node, node.nextAfterBranchId, UnityColors.WireFlow, 112f)
                    } else {
                        drawWire(node, node.nextId, UnityColors.WireFlow, 40f)
                    }
                }

                // Draw Live Finger-drawn Wire
                livePinDrag?.let { drag ->
                    val startX = drag.startX
                    val startY = drag.startY
                    val endX = drag.currentX
                    val endY = drag.currentY

                    val dx = kotlin.math.abs(endX - startX) * 0.5f
                    val control1 = Offset(startX + dx, startY)
                    val control2 = Offset(endX - dx, endY)

                    val wireColor = when (drag.portType) {
                        ConnectingPortType.TrueBranch -> UnityColors.WireTrue
                        ConnectingPortType.FalseBranch -> UnityColors.WireFalse
                        else -> UnityColors.WireFlow
                    }

                    val path = Path().apply {
                        moveTo(startX, startY)
                        cubicTo(control1.x, control1.y, control2.x, control2.y, endX, endY)
                    }

                    // Outer glow
                    drawPath(
                        path = path,
                        color = wireColor.copy(alpha = 0.45f),
                        style = Stroke(width = 8f * zoomScale)
                    )
                    // Core wire
                    drawPath(
                        path = path,
                        color = wireColor,
                        style = Stroke(width = 3.5f * zoomScale)
                    )
                    // Tip cursor
                    drawCircle(
                        color = wireColor,
                        radius = 9f * zoomScale,
                        center = Offset(endX, endY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.5f * zoomScale,
                        center = Offset(endX, endY)
                    )
                }
            }

            // 3. Draggable Unity Node Cards
            for (node in nodes) {
                key(node.id) {
                    val screenX = (node.x * zoomScale) + panX
                    val screenY = (node.y * zoomScale) + panY
                    val cardWidthPx = 200f * zoomScale
                    val cardHeightPx = (60f + node.fields.size * 32f + 50f) * zoomScale

                    val isHoveredByWire = livePinDrag != null &&
                        livePinDrag!!.sourceNodeId != node.id &&
                        livePinDrag!!.currentX in (screenX - 20f)..(screenX + cardWidthPx + 20f) &&
                        livePinDrag!!.currentY in (screenY - 20f)..(screenY + cardHeightPx + 20f)

                    UnityNodeCard(
                        node = node,
                        screenX = screenX,
                        screenY = screenY,
                        zoom = zoomScale,
                        isConnectingSource = activeConnection?.sourceNodeId == node.id,
                        isHoveredByWire = isHoveredByWire,
                        onDrag = { dx, dy ->
                            node.x += dx / zoomScale
                            node.y += dy / zoomScale
                        },
                        onDelete = {
                            if (node.isDeletable) {
                                for (other in nodes) {
                                    if (other.nextId == node.id) other.nextId = null
                                    if (other.nextTrueId == node.id) other.nextTrueId = null
                                    if (other.nextFalseId == node.id) other.nextFalseId = null
                                    if (other.nextAfterBranchId == node.id) other.nextAfterBranchId = null
                                }
                                nodes.remove(node)
                            }
                        },
                        onPinDragStart = { portType, pinOffsetY ->
                            val startX = screenX + cardWidthPx - (12f * zoomScale)
                            val startY = screenY + (pinOffsetY * zoomScale)
                            livePinDrag = LivePinDrag(
                                sourceNodeId = node.id,
                                portType = portType,
                                startX = startX,
                                startY = startY,
                                currentX = startX,
                                currentY = startY
                            )
                        },
                        onPinDrag = { delta ->
                            livePinDrag?.let {
                                it.currentX += delta.x
                                it.currentY += delta.y
                            }
                        },
                        onPinDragEnd = {
                            livePinDrag?.let { drag ->
                                val target = nodes.firstOrNull { candidate ->
                                    if (candidate.id == drag.sourceNodeId) return@firstOrNull false
                                    val cX = (candidate.x * zoomScale) + panX
                                    val cY = (candidate.y * zoomScale) + panY
                                    val cW = 200f * zoomScale
                                    val cH = (60f + candidate.fields.size * 32f + 50f) * zoomScale
                                    drag.currentX in (cX - 24f)..(cX + cW + 24f) &&
                                    drag.currentY in (cY - 24f)..(cY + cH + 24f)
                                }
                                if (target != null) {
                                    val src = nodes.find { it.id == drag.sourceNodeId }
                                    if (src != null) {
                                        when (drag.portType) {
                                            ConnectingPortType.Next -> src.nextId = target.id
                                            ConnectingPortType.TrueBranch -> src.nextTrueId = target.id
                                            ConnectingPortType.FalseBranch -> src.nextFalseId = target.id
                                            ConnectingPortType.AfterBranch -> src.nextAfterBranchId = target.id
                                        }
                                        statusMessage = "Connected: ${src.title} ➔ ${target.title}"
                                    }
                                }
                            }
                            livePinDrag = null
                        },
                        onDisconnectPort = { portType ->
                            when (portType) {
                                ConnectingPortType.Next -> node.nextId = null
                                ConnectingPortType.TrueBranch -> node.nextTrueId = null
                                ConnectingPortType.FalseBranch -> node.nextFalseId = null
                                ConnectingPortType.AfterBranch -> node.nextAfterBranchId = null
                            }
                            statusMessage = "Disconnected port"
                        },
                        onPortClick = { portType ->
                            if (activeConnection == null) {
                                activeConnection = ActiveConnectingState(node.id, portType)
                                statusMessage = "Click another node to connect port [${portType.name}]"
                            } else {
                                activeConnection = null
                            }
                        },
                        onTargetNodeClick = {
                            if (activeConnection != null && activeConnection!!.sourceNodeId != node.id) {
                                val src = nodes.find { it.id == activeConnection!!.sourceNodeId }
                                if (src != null) {
                                    when (activeConnection!!.portType) {
                                        ConnectingPortType.Next -> src.nextId = node.id
                                        ConnectingPortType.TrueBranch -> src.nextTrueId = node.id
                                        ConnectingPortType.FalseBranch -> src.nextFalseId = node.id
                                        ConnectingPortType.AfterBranch -> src.nextAfterBranchId = node.id
                                    }
                                    statusMessage = "Connected -> ${node.title}"
                                }
                                activeConnection = null
                            }
                        },
                        onFieldClick = { field ->
                            activeEditingField = Pair(node, field)
                        }
                    )
                }
            }
        }

        // Top Unity Toolbar
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(UnityColors.Header)
                    .border(1.dp, UnityColors.Border, RoundedCornerShape(0.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Script Information & Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.NodeHeaderAction),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "⚡",
                            style = TextStyle(color = UnityColors.TextWhite, fontSize = 13.sp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        BasicText(
                            text = "Visual Scripting",
                            style = UnityTypography.Header.copy(color = UnityColors.TextWhite)
                        )
                        BasicText(
                            text = visualPath.substringAfterLast("/"),
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextMuted)
                        )
                    }
                }

                // Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Zoom Controls
                    UnityIconButton(
                        iconText = "-",
                        onClick = { zoomScale = (zoomScale - 0.1f).coerceIn(0.4f, 2.0f) },
                        size = 28.dp,
                        backgroundColor = UnityColors.SurfaceVariant,
                        borderColor = UnityColors.BorderLight
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicText(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    UnityIconButton(
                        iconText = "+",
                        onClick = { zoomScale = (zoomScale + 0.1f).coerceIn(0.4f, 2.0f) },
                        size = 28.dp,
                        backgroundColor = UnityColors.SurfaceVariant,
                        borderColor = UnityColors.BorderLight
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // + Add Node Button
                    UnityButton(
                        text = "+ Add Node",
                        onClick = { showNodeLibrary = true },
                        variant = UnityButtonVariant.Normal
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Code Preview Button
                    UnityButton(
                        text = "📄 Code",
                        onClick = {
                            exportedCodeText = ScriptNodeEngine.exportCode(nodes)
                            showCodeDialog = true
                        },
                        variant = UnityButtonVariant.Normal
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Save Button (Unity Green Accent)
                    UnityButton(
                        text = "💾 Save",
                        onClick = { saveNodes() },
                        variant = UnityButtonVariant.Success
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Exit Button
                    UnityButton(
                        text = "✕ Exit",
                        onClick = onExit,
                        variant = UnityButtonVariant.Normal
                    )
                }
            }

            // Notification / Status Banner
            if (statusMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UnityColors.SurfaceVariant)
                        .border(1.dp, UnityColors.AccentBlue, RoundedCornerShape(0.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(
                            text = statusMessage!!,
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextWhite)
                        )
                        UnityIconButton(
                            iconText = "✕",
                            onClick = { statusMessage = null },
                            size = 20.dp
                        )
                    }
                }
            }
        }

        // Draggable Unity Node Library Drawer / Modal
        if (showNodeLibrary) {
            UnityModal(onDismissRequest = { showNodeLibrary = false }) {
                UnityDraggableWindow(
                    title = "Node Library",
                    onClose = { showNodeLibrary = false },
                    width = 420.dp,
                    initialX = 120f,
                    initialY = 60f
                ) {
                    NodeLibraryContent(
                        context = context,
                        onSelectNode = { template ->
                            val newNode = ScriptNode(
                                id = (nextNodeIdCounter++).toString(),
                                title = template.displayName,
                                x = (-panX + 240f) / zoomScale,
                                y = (-panY + 160f) / zoomScale,
                                isBooleanNode = template.isBoolean,
                                code = template.codeTemplate,
                                isDeletable = true,
                                color = -16744192
                            )
                            for (field in template.fields) {
                                newNode.fields.add(ScriptNodeField(name = field, value = ""))
                            }
                            nodes.add(newNode)
                            showNodeLibrary = false
                            statusMessage = "Added node: ${template.displayName}"
                        }
                    )
                }
            }
        }

        // Draggable Unity Code Preview Modal
        if (showCodeDialog) {
            UnityModal(onDismissRequest = { showCodeDialog = false }) {
                UnityDraggableWindow(
                    title = "Generated Java Code",
                    onClose = { showCodeDialog = false },
                    width = 500.dp,
                    initialX = 80f,
                    initialY = 40f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(UnityColors.DarkBackground)
                                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                                .padding(10.dp)
                        ) {
                            BasicText(
                                text = if (exportedCodeText.isBlank()) "// No code generated" else exportedCodeText,
                                style = UnityTypography.Code.copy(color = UnityColors.TextPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            UnityButton(
                                text = "Copy to Clipboard",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("GeneratedCode", exportedCodeText)
                                    clipboard.setPrimaryClip(clip)
                                    statusMessage = "Code copied to clipboard!"
                                    showCodeDialog = false
                                },
                                variant = UnityButtonVariant.Primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            UnityButton(
                                text = "Close",
                                onClick = { showCodeDialog = false },
                                variant = UnityButtonVariant.Normal
                            )
                        }
                    }
                }
            }
        }

        // Draggable Field Value Editor Modal
        if (activeEditingField != null) {
            val (node, field) = activeEditingField!!
            UnityModal(onDismissRequest = { activeEditingField = null }) {
                UnityDraggableWindow(
                    title = "Edit Parameter: ${field.getDisplayName()}",
                    onClose = { activeEditingField = null },
                    width = 380.dp,
                    initialX = 140f,
                    initialY = 90f
                ) {
                    FieldEditorContent(
                        field = field,
                        hints = hints,
                        onApply = { newValue ->
                            field.value = newValue
                            activeEditingField = null
                        },
                        onCancel = { activeEditingField = null }
                    )
                }
            }
        }
    }
}

@Composable
fun UnityPin(
    isConnected: Boolean,
    color: Color,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(if (isConnected) color else UnityColors.DarkBackground)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

/**
 * Visual Node Card styled after Unity Visual Scripting (Bolt).
 */
@Composable
fun UnityNodeCard(
    node: ScriptNode,
    screenX: Float,
    screenY: Float,
    zoom: Float,
    isConnectingSource: Boolean,
    isHoveredByWire: Boolean,
    onDrag: (Float, Float) -> Unit,
    onDelete: () -> Unit,
    onPinDragStart: (ConnectingPortType, Float) -> Unit,
    onPinDrag: (Offset) -> Unit,
    onPinDragEnd: () -> Unit,
    onDisconnectPort: (ConnectingPortType) -> Unit,
    onPortClick: (ConnectingPortType) -> Unit,
    onTargetNodeClick: () -> Unit,
    onFieldClick: (ScriptNodeField) -> Unit
) {
    val headerColor = when {
        !node.isDeletable -> UnityColors.NodeHeaderEvent
        node.isBooleanNode -> UnityColors.NodeHeaderLogic
        node.title.lowercase().contains("event") -> UnityColors.NodeHeaderEvent
        node.title.lowercase().contains("math") -> UnityColors.NodeHeaderMath
        else -> UnityColors.NodeHeaderAction
    }

    val borderColor = when {
        isHoveredByWire -> UnityColors.AccentGreen
        isConnectingSource -> UnityColors.AccentBlue
        else -> UnityColors.BorderLight
    }
    val borderWidth = if (isHoveredByWire || isConnectingSource) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
            .width(200.dp)
            .shadow(8.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(UnityColors.Surface)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onTargetNodeClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Node Header Bar (Draggable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(headerColor)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // In-pin indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(UnityColors.AccentGreen)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicText(
                        text = node.title,
                        style = UnityTypography.Header.copy(
                            color = UnityColors.TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (node.isDeletable) {
                    UnityIconButton(
                        iconText = "✕",
                        onClick = onDelete,
                        size = 20.dp,
                        textColor = UnityColors.TextWhite
                    )
                }
            }

            // Node Body (Fields & Output Ports)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UnityColors.SurfaceVariant)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(8.dp)
            ) {
                // Fields
                for (field in node.fields) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(
                            text = field.getDisplayName(),
                            style = UnityTypography.BodySmall.copy(
                                color = UnityColors.TextSecondary,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Unity Styled Input Field Button
                        val displayVal = if (field.value.isEmpty()) "___" else field.value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(UnityColors.DarkBackground)
                                .border(1.dp, UnityColors.BorderLight, RoundedCornerShape(3.dp))
                                .clickable { onFieldClick(field) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .widthIn(min = 60.dp, max = 100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = displayVal,
                                style = UnityTypography.Code.copy(
                                    color = if (field.value.isEmpty()) UnityColors.TextMuted else UnityColors.AccentBlue,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Output Ports with Pins
                if (node.isBooleanNode) {
                    // True Branch Port
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "True",
                            style = UnityTypography.BodySmall.copy(
                                color = UnityColors.AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        UnityPin(
                            isConnected = node.nextTrueId != null,
                            color = UnityColors.WireTrue,
                            onDragStart = { onPinDragStart(ConnectingPortType.TrueBranch, 48f) },
                            onDrag = onPinDrag,
                            onDragEnd = onPinDragEnd,
                            onClick = {
                                if (node.nextTrueId != null) {
                                    onDisconnectPort(ConnectingPortType.TrueBranch)
                                } else {
                                    onPortClick(ConnectingPortType.TrueBranch)
                                }
                            }
                        )
                    }

                    // False Branch Port
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "False",
                            style = UnityTypography.BodySmall.copy(
                                color = UnityColors.AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        UnityPin(
                            isConnected = node.nextFalseId != null,
                            color = UnityColors.WireFalse,
                            onDragStart = { onPinDragStart(ConnectingPortType.FalseBranch, 80f) },
                            onDrag = onPinDrag,
                            onDragEnd = onPinDragEnd,
                            onClick = {
                                if (node.nextFalseId != null) {
                                    onDisconnectPort(ConnectingPortType.FalseBranch)
                                } else {
                                    onPortClick(ConnectingPortType.FalseBranch)
                                }
                            }
                        )
                    }

                    // After Branch Port
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "Next",
                            style = UnityTypography.BodySmall.copy(
                                color = UnityColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        UnityPin(
                            isConnected = node.nextAfterBranchId != null,
                            color = UnityColors.WireFlow,
                            onDragStart = { onPinDragStart(ConnectingPortType.AfterBranch, 112f) },
                            onDrag = onPinDrag,
                            onDragEnd = onPinDragEnd,
                            onClick = {
                                if (node.nextAfterBranchId != null) {
                                    onDisconnectPort(ConnectingPortType.AfterBranch)
                                } else {
                                    onPortClick(ConnectingPortType.AfterBranch)
                                }
                            }
                        )
                    }
                } else {
                    // Standard Flow Next Port
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "Flow",
                            style = UnityTypography.BodySmall.copy(
                                color = if (node.nextId != null) UnityColors.AccentBlue else UnityColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        UnityPin(
                            isConnected = node.nextId != null,
                            color = UnityColors.WireFlow,
                            onDragStart = { onPinDragStart(ConnectingPortType.Next, 40f) },
                            onDrag = onPinDrag,
                            onDragEnd = onPinDragEnd,
                            onClick = {
                                if (node.nextId != null) {
                                    onDisconnectPort(ConnectingPortType.Next)
                                } else {
                                    onPortClick(ConnectingPortType.Next)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Node Library Drawer Content.
 */
@Composable
fun NodeLibraryContent(
    context: Context,
    onSelectNode: (ScriptNodeTemplate) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val allTemplates = remember { NodeTemplateParser.parseNodes(context) }

    val categories = remember(allTemplates) {
        listOf("All") + allTemplates.map { it.category }.distinct()
    }

    val filteredTemplates = remember(searchQuery, selectedCategory, allTemplates) {
        allTemplates.filter { template ->
            (selectedCategory == "All" || template.category == selectedCategory) &&
                    (searchQuery.isBlank() || template.displayName.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Search
        UnityTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search nodes..."
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (cat in categories.take(5)) {
                val isSel = selectedCategory == cat
                UnityButton(
                    text = cat,
                    onClick = { selectedCategory = cat },
                    variant = if (isSel) UnityButtonVariant.Primary else UnityButtonVariant.Normal,
                    paddingHorizontal = 8.dp,
                    paddingVertical = 4.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Template List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(UnityColors.DarkBackground)
                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                .padding(4.dp)
        ) {
            items(filteredTemplates) { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { onSelectNode(template) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UnityBadge(text = if (template.isBoolean) "IF" else "ACT")
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = template.displayName,
                            style = UnityTypography.Body.copy(color = UnityColors.TextWhite)
                        )
                    }
                    BasicText(
                        text = template.category,
                        style = UnityTypography.BodySmall.copy(color = UnityColors.TextMuted)
                    )
                }
            }
        }
    }
}

/**
 * Parameter Field Editor Modal.
 */
@Composable
fun FieldEditorContent(
    field: ScriptNodeField,
    hints: String,
    onApply: (String) -> Unit,
    onCancel: () -> Unit
) {
    var textValue by remember { mutableStateOf(field.value) }
    val hintItems = remember(hints) {
        hints.lines().filter { it.isNotBlank() && !it.startsWith("- ") }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = "Enter value or select suggestion:",
            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        UnityTextField(
            value = textValue,
            onValueChange = { textValue = it },
            placeholder = "Value or expression..."
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (hintItems.isNotEmpty()) {
            BasicText(
                text = "Suggestions:",
                style = UnityTypography.BodySmall.copy(
                    color = UnityColors.TextMuted,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(UnityColors.DarkBackground)
                    .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                    .padding(4.dp)
            ) {
                items(hintItems) { hint ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { textValue = hint }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        BasicText(
                            text = hint,
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextPrimary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            UnityButton(
                text = "Cancel",
                onClick = onCancel,
                variant = UnityButtonVariant.Normal
            )
            Spacer(modifier = Modifier.width(8.dp))
            UnityButton(
                text = "Apply",
                onClick = { onApply(textValue) },
                variant = UnityButtonVariant.Primary
            )
        }
    }
}
