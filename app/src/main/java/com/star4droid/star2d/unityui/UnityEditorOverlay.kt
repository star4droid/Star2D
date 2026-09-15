package com.star4droid.star2d.unityui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.star4droid.star2d.Activities.VisualScriptingActivity
import com.star4droid.star2d.Helpers.CodeGenerator
import com.star4droid.star2d.Helpers.PropertySet
import com.star4droid.star2d.Items.Editor
import com.star4droid.star2d.editor.LibgdxEditor
import com.star4droid.star2d.editor.items.*
import com.star4droid.star2d.editor.utils.EditorAction
import java.io.File

/**
 * Modern Unity-styled Game Editor Overlay.
 * Directly integrates with the underlying Star2D LibGDX engine without modifying
 * engine system logic, physics, or serialization.
 * Fully adapted for mobile phones with collapsible, clamped floating panels.
 * ZERO Material 3 components.
 */
@Composable
fun UnityEditorOverlay(
    editorView: Editor?,
    onExitToHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showHierarchy by remember { mutableStateOf(false) }
    var showInspector by remember { mutableStateOf(false) }
    var showAddObjectMenu by remember { mutableStateOf(false) }
    var activeTouchMode by remember { mutableStateOf("GRID") }
    var currentScene by remember { mutableStateOf("scene1") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Live list of actors and selected actor state
    var actorNames by remember { mutableStateOf(listOf<String>()) }
    var selectedActorName by remember { mutableStateOf<String?>(null) }
    var selectedActorProps by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun refreshSceneState() {
        if (editorView == null || editorView.app == null) return
        val gdxEditor = editorView.app.editor ?: return

        Gdx.app.postRunnable {
            try {
                val list = ArrayList(gdxEditor.bodiesList ?: emptyList())
                val selected = gdxEditor.selectedActor
                val selName = selected?.name
                val propsMap = mutableMapOf<String, String>()

                if (selected != null) {
                    val ps = PropertySet.getPropertySet(selected)
                    if (ps != null) {
                        propsMap["name"] = selName ?: ""
                        propsMap["x"] = ps.getString("x") ?: ""
                        propsMap["y"] = ps.getString("y") ?: ""
                        propsMap["width"] = ps.getString("width") ?: ""
                        propsMap["height"] = ps.getString("height") ?: ""
                        propsMap["rotation"] = ps.getString("rotation") ?: "0"
                        propsMap["z"] = ps.getString("z") ?: "0"
                        propsMap["type"] = ps.getString("type") ?: "dynamic"
                    }
                }

                // Update Compose state on UI thread
                (context as? android.app.Activity)?.runOnUiThread {
                    actorNames = list
                    selectedActorName = selName
                    selectedActorProps = propsMap
                    currentScene = gdxEditor.scene ?: "scene1"
                }
            } catch (e: Exception) {
                // Ignore transient GL thread query issues
            }
        }
    }

    // Periodic state refresher while editor overlay is active
    LaunchedEffect(Unit) {
        while (true) {
            refreshSceneState()
            kotlinx.coroutines.delay(1000)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isNarrowPhone = maxWidth < 600.dp

        // Top Unity Toolbar
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(UnityColors.Header)
                    .border(0.5.dp, UnityColors.Border, RoundedCornerShape(0.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Actions: Hub Exit & Scene Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnityButton(
                        text = "← Hub",
                        onClick = {
                            if (editorView?.app != null) {
                                Gdx.app.postRunnable {
                                    editorView.app.closeProject()
                                }
                            }
                            onExitToHub()
                        },
                        variant = UnityButtonVariant.Normal
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    UnityBadge(
                        text = currentScene,
                        color = UnityColors.AccentBlue
                    )
                }

                // Center Actions: Play / Save / Undo / Redo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Play Test Button
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.AccentGreen)
                            .clickable {
                                val app = editorView?.app
                                if (app != null) {
                                    Gdx.app.postRunnable {
                                        try {
                                            if (app.isPlaying) {
                                                app.play(null)
                                            } else {
                                                app.editor?.let { ed ->
                                                    app.play(ed.project?.path, ed.scene)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    statusMessage = if (app.isPlaying) "Stopped playtest" else "Starting scene playtest..."
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = if (editorView?.app?.isPlaying == true) "■ Stop" else "▶ Play",
                            style = TextStyle(
                                color = UnityColors.TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Undo
                    UnityIconButton(
                        iconText = "↶",
                        onClick = {
                            editorView?.app?.editor?.let { gdxEditor ->
                                Gdx.app.postRunnable { gdxEditor.undo() }
                            }
                        },
                        size = 28.dp
                    )

                    // Redo
                    UnityIconButton(
                        iconText = "↷",
                        onClick = {
                            editorView?.app?.editor?.let { gdxEditor ->
                                Gdx.app.postRunnable { gdxEditor.redo() }
                            }
                        },
                        size = 28.dp
                    )

                    // Save Scene
                    UnityIconButton(
                        iconText = "💾",
                        onClick = {
                            editorView?.app?.editor?.let { gdxEditor ->
                                Gdx.app.postRunnable {
                                    try {
                                        gdxEditor.project?.save(gdxEditor)
                                        CodeGenerator.generateFor(gdxEditor) { _ -> }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                statusMessage = "Scene saved successfully"
                            }
                        },
                        size = 28.dp
                    )
                }

                // Right Actions: Transform Tools & Window Toggles
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Center Camera
                    UnityIconButton(
                        iconText = "⊙",
                        onClick = {
                            editorView?.app?.editor?.let { gdxEditor ->
                                Gdx.app.postRunnable { gdxEditor.centerCamera() }
                            }
                        },
                        size = 28.dp
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Toggle Hierarchy Window
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (showHierarchy) UnityColors.AccentBlue else UnityColors.SurfaceVariant)
                            .clickable { showHierarchy = !showHierarchy }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "☰ Hierarchy",
                            style = TextStyle(
                                color = UnityColors.TextWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Toggle Inspector Window
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (showInspector) UnityColors.AccentBlue else UnityColors.SurfaceVariant)
                            .clickable { showInspector = !showInspector }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "ⓘ Inspector",
                            style = TextStyle(
                                color = UnityColors.TextWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Status message strip
            if (statusMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UnityColors.SurfaceVariant)
                        .border(1.dp, UnityColors.AccentBlue, RoundedCornerShape(0.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = statusMessage!!,
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextWhite)
                        )
                        UnityIconButton(
                            iconText = "✕",
                            onClick = { statusMessage = null },
                            size = 18.dp
                        )
                    }
                }
            }
        }

        // Draggable Hierarchy Window
        if (showHierarchy) {
            UnityDraggableWindow(
                title = "Hierarchy",
                onClose = { showHierarchy = false },
                width = if (isNarrowPhone) 260.dp else 290.dp,
                initialX = 12f,
                initialY = 52f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    // Header Toolbar (+ Add, 🗑 Delete)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UnityButton(
                            text = "+ Add Object",
                            onClick = { showAddObjectMenu = !showAddObjectMenu },
                            variant = UnityButtonVariant.Primary
                        )

                        UnityButton(
                            text = "Delete",
                            enabled = selectedActorName != null,
                            onClick = {
                                editorView?.app?.editor?.let { gdxEditor ->
                                    Gdx.app.postRunnable {
                                        try {
                                            val selected = gdxEditor.selectedActor
                                            if (selected != null) {
                                                val name = PropertySet.getPropertySet(selected).get("name")?.toString() ?: ""
                                                gdxEditor.project?.deleteBody(name, gdxEditor.scene)
                                                selected.remove()
                                                EditorAction.itemRemoved(gdxEditor, selected)
                                                gdxEditor.selectActor(null)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        refreshSceneState()
                                    }
                                }
                            },
                            variant = UnityButtonVariant.Danger
                        )
                    }

                    // Add Object Popup
                    if (showAddObjectMenu) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(UnityColors.DarkBackground)
                                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                                .padding(4.dp)
                        ) {
                            val itemsToAdd = listOf(
                                "Box Body" to 0,
                                "Circle Body" to 1,
                                "Text Item" to 2,
                                "Joystick" to 3,
                                "Progress Bar" to 4,
                                "Custom Body" to 5,
                                "Particle Effect" to 6,
                                "Camera" to 7
                            )
                            for ((label, typeId) in itemsToAdd) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showAddObjectMenu = false
                                            editorView?.app?.editor?.let { gdxEditor ->
                                                Gdx.app.postRunnable {
                                                    try {
                                                        val actorItem: Actor? = when (typeId) {
                                                            0 -> BoxItem(gdxEditor).setDefault()
                                                            1 -> CircleItem(gdxEditor).setDefault()
                                                            2 -> EditorTextItem(gdxEditor)
                                                            3 -> JoyStickItem(gdxEditor)
                                                            4 -> EditorProgressItem(gdxEditor)
                                                            5 -> CustomItem(gdxEditor)
                                                            6 -> ParticleItem(gdxEditor)
                                                            7 -> EditorCameraItem(gdxEditor)
                                                            else -> null
                                                        }
                                                        if (actorItem != null) {
                                                            gdxEditor.addActor(actorItem)
                                                            actorItem.name = gdxEditor.getName(actorItem)
                                                            gdxEditor.selectActor(actorItem)
                                                            EditorAction.itemAdded(gdxEditor, actorItem)
                                                            refreshSceneState()
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                ) {
                                    BasicText(
                                        text = label,
                                        style = UnityTypography.BodySmall.copy(color = UnityColors.TextWhite)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Actor list in scene
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.DarkBackground)
                            .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                    ) {
                        if (actorNames.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = "Empty Scene",
                                    style = UnityTypography.BodySmall.copy(color = UnityColors.TextMuted)
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(actorNames) { name ->
                                    val isSelected = name == selectedActorName
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) UnityColors.AccentBlue.copy(alpha = 0.35f) else Color.Transparent)
                                            .clickable {
                                                editorView?.app?.editor?.let { gdxEditor ->
                                                    Gdx.app.postRunnable {
                                                        gdxEditor.selectByName(name)
                                                        refreshSceneState()
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicText(
                                            text = if (isSelected) "▶ " else "  ",
                                            style = TextStyle(color = UnityColors.AccentBlue, fontSize = 10.sp)
                                        )
                                        BasicText(
                                            text = name,
                                            style = UnityTypography.BodySmall.copy(
                                                color = if (isSelected) UnityColors.TextWhite else UnityColors.TextSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Draggable Inspector Window
        if (showInspector) {
            UnityDraggableWindow(
                title = "Inspector: ${selectedActorName ?: "Scene"}",
                onClose = { showInspector = false },
                width = if (isNarrowPhone) 260.dp else 290.dp,
                initialX = if (isNarrowPhone) 40f else 320f,
                initialY = 52f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedActorName == null) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "Select an object in Hierarchy or tap it in Scene to inspect properties",
                                style = UnityTypography.BodySmall.copy(color = UnityColors.TextMuted)
                            )
                        }
                    } else {
                        BasicText(
                            text = "TRANSFORM",
                            style = UnityTypography.BodySmall.copy(
                                color = UnityColors.TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val xVal = selectedActorProps["x"] ?: "0"
                        val yVal = selectedActorProps["y"] ?: "0"
                        val wVal = selectedActorProps["width"] ?: "0"
                        val hVal = selectedActorProps["height"] ?: "0"
                        val rVal = selectedActorProps["rotation"] ?: "0"
                        val zVal = selectedActorProps["z"] ?: "0"
                        val typeVal = selectedActorProps["type"] ?: "dynamic"

                        InspectorFieldRow(label = "Position X / Y", value = "$xVal, $yVal")
                        InspectorFieldRow(label = "Size W / H", value = "$wVal, $hVal")
                        InspectorFieldRow(label = "Rotation", value = "$rVal°")
                        InspectorFieldRow(label = "Z-Index", value = zVal)
                        InspectorFieldRow(label = "Body Type", value = typeVal)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Open Visual Scripting Button
                        UnityButton(
                            text = "⚡ Visual Scripting",
                            onClick = {
                                val proj = editorView?.app?.editor?.project
                                val scene = editorView?.app?.editor?.scene ?: "scene1"
                                val actorName = selectedActorName
                                if (proj != null && actorName != null) {
                                    val codePath = proj.getBodyScriptPath(actorName, scene)
                                    val intent = Intent(context, VisualScriptingActivity::class.java).apply {
                                        putExtra("codePath", codePath)
                                        putExtra("hints", "")
                                        putExtra("project", proj.path)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            variant = UnityButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InspectorFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(
            text = label,
            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
        )
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(UnityColors.DarkBackground)
                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            BasicText(
                text = value,
                style = UnityTypography.Code.copy(color = UnityColors.TextWhite, fontSize = 10.sp)
            )
        }
    }
}
