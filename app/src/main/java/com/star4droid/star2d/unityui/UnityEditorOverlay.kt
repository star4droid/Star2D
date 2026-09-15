package com.star4droid.star2d.unityui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.star4droid.star2d.Activities.AnimationActivity
import com.star4droid.star2d.Adapters.VisualScriptingDialog
import com.star4droid.star2d.Helpers.CodeGenerator
import com.star4droid.star2d.Helpers.CompileThread
import com.star4droid.star2d.Helpers.FileUtil
import com.star4droid.star2d.Helpers.PropertySet
import com.star4droid.star2d.Items.Editor
import com.star4droid.star2d.JointInputs.JointDialog
import com.star4droid.star2d.editor.LibgdxEditor
import com.star4droid.star2d.editor.items.*
import com.star4droid.star2d.editor.utils.EditorAction
import com.star4droid.template.Items.StageImp
import java.io.File
import java.util.ArrayList
import java.util.HashMap

/**
 * Modern Jetpack Compose Game Editor for Star2D.
 * Clean professional IDE styling WITHOUT emojis on buttons.
 *
 * Features:
 * 1. Top Bar with horizontal scrolling (Exit, Scene Selector, Add Object, Orientation, Color, Undo, Redo, Save, Play).
 * 2. Bottom Tools Bar with horizontal scrolling (Select, Move, Scale, Rotate, Free/Lock X/Lock Y, Center Cam, Delete).
 * 3. Right-Side Tool Rail (docked panel toggles):
 *    - Bodies (Hierarchy)
 *    - Properties (Inspector)
 *    - Joints (JointsList)
 *    - Events (Scene/Body/Custom scripts)
 *    - Variables (Project variables manager)
 *    - AI (Game dev AI Assistant)
 *    - Animation (Sprite animation editor launcher)
 *    - Files (Project asset & file manager)
 * 4. Full-screen playtest mode with working hardware & software Back button returning to editor.
 */

enum class RightToolPanel {
    NONE,
    BODIES,
    PROPERTIES,
    JOINTS,
    EVENTS,
    VARIABLES,
    AI,
    ANIMATION,
    FILES
}

@Composable
fun UnityEditorOverlay(
    editorView: Editor?,
    onExitToHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Active tool panel on right side
    var activeRightTool by remember { mutableStateOf(RightToolPanel.NONE) }

    // Dialog visibility states
    var showAddObjectMenu by remember { mutableStateOf(false) }
    var showSceneManager by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Touch & Editing modes
    var activeTouchMode by remember { mutableStateOf("GRID") }
    var lockAxisMode by remember { mutableStateOf("FREE") } // FREE, LOCK_X, LOCK_Y
    var currentScene by remember { mutableStateOf("scene1") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // Live list of actors and selected actor state
    var actorNames by remember { mutableStateOf(listOf<String>()) }
    var selectedActorName by remember { mutableStateOf<String?>(null) }
    var selectedActorProps by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var availableScenes by remember { mutableStateOf(listOf<String>()) }

    fun refreshSceneList() {
        val proj = editorView?.app?.project ?: editorView?.app?.editor?.project ?: return
        val list = ArrayList<String>()
        FileUtil.listDir(proj.get("scenes"), list)
        val names = mutableListOf<String>()
        for (p in list) {
            val sName = android.net.Uri.parse(p).lastPathSegment ?: ""
            if (sName.isNotEmpty() && !names.contains(sName)) {
                names.add(sName)
            }
        }
        if (names.isEmpty()) names.add("scene1")
        (context as? Activity)?.runOnUiThread {
            availableScenes = names
        }
    }

    fun refreshSceneState() {
        if (editorView == null || editorView.app == null) return
        val app = editorView.app
        val gdxEditor = app.editor ?: return

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
                        propsMap["density"] = ps.getString("density") ?: "1.0"
                        propsMap["friction"] = ps.getString("friction") ?: "0.2"
                        propsMap["restitution"] = ps.getString("restitution") ?: "0.0"
                        propsMap["color"] = ps.getString("color") ?: "#FFFFFF"
                    }
                }

                (context as? Activity)?.runOnUiThread {
                    actorNames = list
                    selectedActorName = selName
                    selectedActorProps = propsMap
                    currentScene = gdxEditor.scene ?: "scene1"
                    isPlaying = app.isPlaying
                }
            } catch (e: Exception) {
                // Ignore transient thread issues
            }
        }
    }

    // Periodic state refresher while editor is active
    LaunchedEffect(Unit) {
        refreshSceneList()
        while (true) {
            val appPlaying = editorView?.app?.isPlaying == true
            if (isPlaying != appPlaying) {
                isPlaying = appPlaying
            }
            if (!appPlaying) {
                refreshSceneState()
            }
            kotlinx.coroutines.delay(600)
        }
    }

    // -------------------------------------------------------------
    // PLAYTEST MODE: FULL SCREEN WITH WORKING BACK BUTTON & EXIT HUD
    // -------------------------------------------------------------
    if (isPlaying) {
        // Intercept Android hardware / gesture back button
        BackHandler {
            editorView?.app?.let { app ->
                Gdx.app.postRunnable {
                    try {
                        app.play(null as? StageImp)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            isPlaying = false
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // High-contrast on-screen Exit button (NO emoji)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xD91F1F1F))
                    .border(1.dp, Color(0xFFE53935), RoundedCornerShape(6.dp))
                    .clickable {
                        editorView?.app?.let { app ->
                            Gdx.app.postRunnable {
                                try {
                                    app.play(null as? StageImp)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        isPlaying = false
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                BasicText(
                    text = "Exit Game (Back)",
                    style = TextStyle(
                        color = Color(0xFFFFCDD2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        return
    }

    // Handle back button when not playing: close open panel first, otherwise confirm exit
    BackHandler(enabled = activeRightTool != RightToolPanel.NONE || showAddObjectMenu || showSceneManager || showColorPicker) {
        if (showAddObjectMenu) showAddObjectMenu = false
        else if (showSceneManager) showSceneManager = false
        else if (showColorPicker) showColorPicker = false
        else if (activeRightTool != RightToolPanel.NONE) activeRightTool = RightToolPanel.NONE
    }

    // -------------------------------------------------------------
    // NORMAL GAME EDITOR VIEW
    // -------------------------------------------------------------
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val isNarrow = screenWidth < 500.dp

        // =========================================================
        // TOP BAR (Horizontally scrollable, NO emojis)
        // =========================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(UnityColors.Header)
                    .border(0.5.dp, UnityColors.Border, RoundedCornerShape(0.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit Project
                EditorTextButton(
                    text = "Exit",
                    onClick = onExitToHub,
                    backgroundColor = UnityColors.SurfaceVariant,
                    textColor = UnityColors.TextWhite
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Scene Selector
                EditorTextButton(
                    text = "Scene: $currentScene",
                    onClick = {
                        refreshSceneList()
                        showSceneManager = true
                    },
                    backgroundColor = UnityColors.DarkBackground,
                    textColor = UnityColors.AccentBlue,
                    borderColor = UnityColors.AccentBlue.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Add Object
                EditorTextButton(
                    text = "+ Object",
                    onClick = { showAddObjectMenu = !showAddObjectMenu },
                    backgroundColor = UnityColors.AccentBlue,
                    textColor = UnityColors.TextWhite
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Orientation toggle
                EditorTextButton(
                    text = "Orientation",
                    onClick = {
                        val app = editorView?.app
                        if (app != null) {
                            val gdxEditor = app.editor
                            val isLand = gdxEditor?.isLandscape == true
                            app.setOrienation(!isLand)
                            statusMessage = if (!isLand) "Orientation: Landscape" else "Orientation: Portrait"
                        }
                    },
                    backgroundColor = UnityColors.SurfaceVariant
                )

                Spacer(modifier = Modifier.width(3.dp))

                // Background Color
                EditorTextButton(
                    text = "Color",
                    onClick = { showColorPicker = !showColorPicker },
                    backgroundColor = UnityColors.SurfaceVariant
                )

                Spacer(modifier = Modifier.width(3.dp))

                // Undo
                EditorTextButton(
                    text = "Undo",
                    onClick = {
                        editorView?.app?.editor?.let { ed ->
                            Gdx.app.postRunnable { ed.undo() }
                        }
                    },
                    backgroundColor = UnityColors.SurfaceVariant
                )

                Spacer(modifier = Modifier.width(3.dp))

                // Redo
                EditorTextButton(
                    text = "Redo",
                    onClick = {
                        editorView?.app?.editor?.let { ed ->
                            Gdx.app.postRunnable { ed.redo() }
                        }
                    },
                    backgroundColor = UnityColors.SurfaceVariant
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Save Scene
                EditorTextButton(
                    text = "Save",
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
                    backgroundColor = Color(0xFF1E88E5),
                    textColor = Color.White
                )

                Spacer(modifier = Modifier.width(4.dp))

                // PLAY GAME
                EditorTextButton(
                    text = "Play",
                    onClick = {
                        val app = editorView?.app
                        val gdxEditor = app?.editor
                        if (app != null && gdxEditor != null) {
                            statusMessage = "Compiling & launching..."
                            Gdx.app.postRunnable {
                                try {
                                    CodeGenerator.generateFor(gdxEditor) { code ->
                                        val sceneFile = Gdx.files.absolute(gdxEditor.project.getCodesPath(gdxEditor.scene))
                                        val compileThread = CompileThread(gdxEditor.project.get("java"), false)
                                        compileThread.setOnChangeStatus(object : CompileThread.OnStatusChanged {
                                            override fun onStatus(s: String?) {}
                                            override fun onEnd(message: String?) {}
                                            override fun onError(error: String?) {
                                                (context as? Activity)?.runOnUiThread {
                                                    statusMessage = "Compile Error: $error"
                                                }
                                            }
                                            override fun onSuccess(message: String?) {
                                                try {
                                                    val fileHandle = FileHandle(gdxEditor.project.dex)
                                                    if (fileHandle.exists()) fileHandle.file().setWritable(true)
                                                    fileHandle.writeString("", false)
                                                    Gdx.files.absolute(gdxEditor.project.path + "/java/classes.dex").moveTo(fileHandle)
                                                    app.play(gdxEditor.project.path, gdxEditor.scene)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        })

                                        if (!sceneFile.exists() || sceneFile.readString() != code) {
                                            compileThread.start()
                                            sceneFile.writeString(code, false)
                                        } else {
                                            try {
                                                app.play(gdxEditor.project.path, gdxEditor.scene)
                                            } catch (e: Exception) {
                                                compileThread.start()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    backgroundColor = UnityColors.AccentGreen,
                    textColor = Color.White
                )
            }

            // Status message strip
            if (statusMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UnityColors.SurfaceVariant)
                        .border(1.dp, UnityColors.AccentBlue, RoundedCornerShape(0.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
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
                        EditorTextButton(
                            text = "Dismiss",
                            onClick = { statusMessage = null },
                            backgroundColor = Color.Transparent,
                            textColor = UnityColors.TextMuted,
                            paddingH = 4.dp
                        )
                    }
                }
            }
        }

        // =========================================================
        // BOTTOM TOOLS BAR (Select, Move, Scale, Rotate, Locks, Cam)
        // Horizontally scrollable, NO emojis!
        // =========================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(UnityColors.DarkBackground)
                .border(0.5.dp, UnityColors.Border, RoundedCornerShape(0.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "TOOLS:",
                    style = TextStyle(color = UnityColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Transform Modes
                val modes = listOf(
                    "GRID" to "Select",
                    "MOVE" to "Move",
                    "SCALE" to "Scale",
                    "ROTATE" to "Rotate"
                )

                for ((modeKey, modeLabel) in modes) {
                    val isActive = activeTouchMode == modeKey
                    EditorTextButton(
                        text = modeLabel,
                        onClick = {
                            activeTouchMode = modeKey
                            editorView?.app?.editor?.let { ed ->
                                Gdx.app.postRunnable {
                                    when (modeKey) {
                                        "GRID" -> ed.setTouchMode(LibgdxEditor.TOUCHMODE.GRID)
                                        "MOVE" -> ed.setTouchMode(LibgdxEditor.TOUCHMODE.MOVE)
                                        "SCALE" -> ed.setTouchMode(LibgdxEditor.TOUCHMODE.SCALE)
                                        "ROTATE" -> ed.setTouchMode(LibgdxEditor.TOUCHMODE.ROTATE)
                                    }
                                }
                            }
                        },
                        backgroundColor = if (isActive) UnityColors.AccentBlue else UnityColors.SurfaceVariant,
                        textColor = UnityColors.TextWhite
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Axis lock toggle
                val lockLabel = when (lockAxisMode) {
                    "LOCK_X" -> "Lock: X"
                    "LOCK_Y" -> "Lock: Y"
                    else -> "Lock: None"
                }
                EditorTextButton(
                    text = lockLabel,
                    onClick = {
                        lockAxisMode = when (lockAxisMode) {
                            "FREE" -> "LOCK_X"
                            "LOCK_X" -> "LOCK_Y"
                            else -> "FREE"
                        }
                        editorView?.app?.editor?.let { ed ->
                            Gdx.app.postRunnable {
                                when (lockAxisMode) {
                                    "LOCK_X" -> {
                                        ed.setLockX(true)
                                        ed.setLockY(false)
                                    }
                                    "LOCK_Y" -> {
                                        ed.setLockX(false)
                                        ed.setLockY(true)
                                    }
                                    else -> {
                                        ed.setLockX(false)
                                        ed.setLockY(false)
                                    }
                                }
                            }
                        }
                    },
                    backgroundColor = if (lockAxisMode != "FREE") UnityColors.AccentYellow else UnityColors.SurfaceVariant,
                    textColor = if (lockAxisMode != "FREE") Color.Black else UnityColors.TextWhite
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Center Camera
                EditorTextButton(
                    text = "Center Cam",
                    onClick = {
                        editorView?.app?.editor?.let { ed ->
                            Gdx.app.postRunnable { ed.centerCamera() }
                        }
                    },
                    backgroundColor = UnityColors.SurfaceVariant
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Delete selected
                if (selectedActorName != null) {
                    EditorTextButton(
                        text = "Delete",
                        onClick = {
                            val gdxEditor = editorView?.app?.editor ?: return@EditorTextButton
                            Gdx.app.postRunnable {
                                try {
                                    val sel = gdxEditor.selectedActor
                                    if (sel != null) {
                                        gdxEditor.project?.deleteBody(sel.name, gdxEditor.scene)
                                        sel.remove()
                                        EditorAction.itemRemoved(gdxEditor, sel)
                                        gdxEditor.selectActor(null)
                                        refreshSceneState()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        backgroundColor = Color(0xFFC62828),
                        textColor = Color.White
                    )
                }
            }
        }

        // =========================================================
        // RIGHT-SIDE TOOL RAIL (Clean vertical buttons, NO emojis)
        // NOT a drawer, direct tap-to-toggle just like LibGDX UI!
        // =========================================================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(bottom = 44.dp, top = 46.dp)
                .background(UnityColors.Header.copy(alpha = 0.95f))
                .border(0.5.dp, UnityColors.Border, RoundedCornerShape(4.dp))
                .padding(2.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val tools = listOf(
                RightToolPanel.BODIES to "Bodies",
                RightToolPanel.PROPERTIES to "Props",
                RightToolPanel.JOINTS to "Joints",
                RightToolPanel.EVENTS to "Events",
                RightToolPanel.VARIABLES to "Vars",
                RightToolPanel.AI to "AI",
                RightToolPanel.ANIMATION to "Anims",
                RightToolPanel.FILES to "Files"
            )

            for ((panelKey, label) in tools) {
                val isSelected = activeRightTool == panelKey
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isSelected) UnityColors.AccentBlue else UnityColors.SurfaceVariant)
                        .clickable {
                            activeRightTool = if (activeRightTool == panelKey) RightToolPanel.NONE else panelKey
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = label,
                        style = TextStyle(
                            color = UnityColors.TextWhite,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }

        // =========================================================
        // RIGHT-SIDE DOCKED TOOL PANEL (When a right rail tool is active)
        // =========================================================
        if (activeRightTool != RightToolPanel.NONE) {
            val panelWidth = if (isNarrow) screenWidth * 0.82f else 310.dp

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 52.dp, top = 46.dp, bottom = 44.dp)
                    .width(panelWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(UnityColors.Surface)
                    .border(1.dp, UnityColors.Border, RoundedCornerShape(6.dp))
            ) {
                when (activeRightTool) {
                    RightToolPanel.BODIES -> {
                        BodiesListPanel(
                            actorNames = actorNames,
                            selectedName = selectedActorName,
                            onSelect = { name ->
                                val gdxEditor = editorView?.app?.editor ?: return@BodiesListPanel
                                Gdx.app.postRunnable {
                                    for (act in gdxEditor.actors) {
                                        if (act.name == name) {
                                            gdxEditor.selectActor(act)
                                            break
                                        }
                                    }
                                    refreshSceneState()
                                }
                            },
                            onDelete = { name ->
                                val gdxEditor = editorView?.app?.editor ?: return@BodiesListPanel
                                Gdx.app.postRunnable {
                                    for (act in gdxEditor.actors) {
                                        if (act.name == name) {
                                            gdxEditor.project?.deleteBody(name, gdxEditor.scene)
                                            act.remove()
                                            EditorAction.itemRemoved(gdxEditor, act)
                                            gdxEditor.selectActor(null)
                                            break
                                        }
                                    }
                                    refreshSceneState()
                                }
                            },
                            onAddObject = { showAddObjectMenu = true },
                            onClose = { activeRightTool = RightToolPanel.NONE }
                        )
                    }
                    RightToolPanel.PROPERTIES -> {
                        PropertiesInspectorPanel(
                            selectedActorName = selectedActorName,
                            selectedProps = selectedActorProps,
                            currentScene = currentScene,
                            editorView = editorView,
                            onClose = { activeRightTool = RightToolPanel.NONE },
                            onOpenVisualScripting = {
                                (context as? Activity)?.runOnUiThread {
                                    try {
                                        VisualScriptingDialog.openCodeEditor()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        )
                    }
                    RightToolPanel.JOINTS -> {
                        JointsListPanel(
                            editorView = editorView,
                            currentScene = currentScene,
                            onClose = { activeRightTool = RightToolPanel.NONE }
                        )
                    }
                    RightToolPanel.EVENTS -> {
                        EventsManagerPanel(
                            editorView = editorView,
                            currentScene = currentScene,
                            selectedActorName = selectedActorName,
                            onClose = { activeRightTool = RightToolPanel.NONE }
                        )
                    }
                    RightToolPanel.VARIABLES -> {
                        VariablesManagerPanel(
                            editorView = editorView,
                            onClose = { activeRightTool = RightToolPanel.NONE }
                        )
                    }
                    RightToolPanel.AI -> {
                        AIAssistantPanel(
                            editorView = editorView,
                            selectedActor = selectedActorName,
                            onClose = { activeRightTool = RightToolPanel.NONE }
                        )
                    }
                    RightToolPanel.ANIMATION -> {
                        AnimationEditorLauncherPanel(
                            context = context,
                            editorView = editorView,
                            onClose = { activeRightTool = RightToolPanel.NONE }
                        )
                    }
                    RightToolPanel.FILES -> {
                        FilesManagerPanel(
                            editorView = editorView,
                            onClose = { activeRightTool = RightToolPanel.NONE }
                        )
                    }
                    else -> {}
                }
            }
        }

        // =========================================================
        // ADD OBJECT MODAL SHEET (NO emojis)
        // =========================================================
        if (showAddObjectMenu) {
            EditorFloatingDialog(
                title = "Add Object",
                onClose = { showAddObjectMenu = false },
                width = if (isNarrow) 290.dp else 340.dp,
                initialX = 8f,
                initialY = 48f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    fun addObjectToEngine(typeId: String) {
                        showAddObjectMenu = false
                        val gdxEditor = editorView?.app?.editor ?: return
                        Gdx.app.postRunnable {
                            try {
                                val item: Actor? = when (typeId) {
                                    "box" -> BoxItem(gdxEditor).setDefault()
                                    "circle" -> CircleItem(gdxEditor).setDefault()
                                    "custom" -> CustomItem(gdxEditor).setDefault()
                                    "text" -> EditorTextItem(gdxEditor).setDefault()
                                    "joystick" -> JoyStickItem(gdxEditor).setDefault()
                                    "progress" -> EditorProgressItem(gdxEditor).setDefault()
                                    "particle" -> ParticleItem(gdxEditor).setDefault()
                                    "camera" -> EditorCameraItem(gdxEditor).setDefault()
                                    "map" -> EditorMapItem(gdxEditor).setDefault()
                                    "light_point" -> LightItem(gdxEditor).setDefault("point")
                                    "light_directional" -> LightItem(gdxEditor).setDefault("directional")
                                    "light_cone" -> LightItem(gdxEditor).setDefault("cone")
                                    else -> null
                                }
                                if (item != null) {
                                    gdxEditor.addActor(item)
                                    item.name = gdxEditor.getName(item)
                                    (item as? EditorItem)?.propertySet?.put("z", gdxEditor.actors.size)
                                    gdxEditor.selectActor(item)
                                    EditorAction.itemAdded(gdxEditor, item)
                                    (item as? EditorItem)?.update()
                                    refreshSceneState()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    CategoryHeader("PHYSICS BODIES (Box2D)")
                    AddObjectItem("Box Body", "Rectangle rigid body with physics collision") { addObjectToEngine("box") }
                    AddObjectItem("Circle Body", "Circular rigid body with radius") { addObjectToEngine("circle") }
                    AddObjectItem("Custom Polygon Body", "Polygon body with customizable vertices") { addObjectToEngine("custom") }

                    Spacer(modifier = Modifier.height(6.dp))

                    CategoryHeader("GAMEPLAY & UI CONTROLS")
                    AddObjectItem("Text Item", "Dynamic UI text label") { addObjectToEngine("text") }
                    AddObjectItem("Virtual Joystick", "On-screen touch joystick for mobile controls") { addObjectToEngine("joystick") }
                    AddObjectItem("Progress Bar", "Health, stamina, or progress indicator") { addObjectToEngine("progress") }

                    Spacer(modifier = Modifier.height(6.dp))

                    CategoryHeader("VISUALS & ENVIRONMENT")
                    AddObjectItem("Particle Effect", "2D particle emitter component") { addObjectToEngine("particle") }
                    AddObjectItem("Camera", "Viewport follow camera component") { addObjectToEngine("camera") }
                    AddObjectItem("Tiled Map", "Tiled map renderer") { addObjectToEngine("map") }

                    Spacer(modifier = Modifier.height(6.dp))

                    CategoryHeader("DYNAMIC LIGHTS (Box2D Lights)")
                    AddObjectItem("Point Light", "Omnidirectional 360 point light source") { addObjectToEngine("light_point") }
                    AddObjectItem("Directional Light", "Sun / global directional light rays") { addObjectToEngine("light_directional") }
                    AddObjectItem("Cone Light", "Spotlight cone light beam") { addObjectToEngine("light_cone") }
                }
            }
        }

        // =========================================================
        // SCENE MANAGER MODAL (NO emojis)
        // =========================================================
        if (showSceneManager) {
            var newSceneName by remember { mutableStateOf("") }

            EditorFloatingDialog(
                title = "Scene Manager",
                onClose = { showSceneManager = false },
                width = if (isNarrow) 280.dp else 330.dp,
                initialX = 12f,
                initialY = 48f
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "Current Scene: $currentScene",
                        style = TextStyle(color = UnityColors.AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CategoryHeader("AVAILABLE SCENES")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        for (s in availableScenes) {
                            val isCurrent = s == currentScene
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isCurrent) UnityColors.AccentBlue.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable {
                                        showSceneManager = false
                                        editorView?.app?.let { app ->
                                            Gdx.app.postRunnable {
                                                try {
                                                    app.openSceneInNewEditor(s)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 5.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    text = s + if (isCurrent) " (Active)" else "",
                                    style = TextStyle(
                                        color = if (isCurrent) UnityColors.AccentBlue else UnityColors.TextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                )

                                if (availableScenes.size > 1 && !isCurrent) {
                                    EditorTextButton(
                                        text = "Delete",
                                        onClick = {
                                            val proj = editorView?.app?.project ?: return@EditorTextButton
                                            val sceneDir = File(proj.getScenesPath() + s)
                                            if (sceneDir.exists()) FileUtil.deleteFile(sceneDir.absolutePath)
                                            refreshSceneList()
                                        },
                                        backgroundColor = Color(0xFFB71C1C),
                                        paddingH = 6.dp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    CategoryHeader("CREATE NEW SCENE")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(UnityColors.DarkBackground)
                                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = newSceneName,
                                onValueChange = { newSceneName = it.replace(" ", "_") },
                                textStyle = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(UnityColors.AccentBlue),
                                decorationBox = { innerTextField ->
                                    if (newSceneName.isEmpty()) {
                                        BasicText(
                                            text = "New scene name...",
                                            style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        EditorTextButton(
                            text = "Create",
                            onClick = {
                                val sName = newSceneName.trim()
                                if (sName.isNotEmpty()) {
                                    val proj = editorView?.app?.project
                                    if (proj != null) {
                                        FileUtil.writeFile(proj.getScenesPath() + sName + "/scene.json", "{}")
                                        refreshSceneList()
                                        newSceneName = ""
                                        editorView.app.openSceneInNewEditor(sName)
                                        showSceneManager = false
                                    }
                                }
                            },
                            backgroundColor = UnityColors.AccentBlue
                        )
                    }
                }
            }
        }

        // =========================================================
        // SCENE COLOR PICKER MODAL (NO emojis)
        // =========================================================
        if (showColorPicker) {
            val palette = listOf(
                "#263238", "#1E1E1E", "#0D47A1", "#1B5E20",
                "#B71C1C", "#4A148C", "#E65100", "#004D40",
                "#37474F", "#000000", "#ECEFF1", "#F5F5F5"
            )

            EditorFloatingDialog(
                title = "Scene Background Color",
                onClose = { showColorPicker = false },
                width = 240.dp,
                initialX = 40f,
                initialY = 48f
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.height(130.dp)
                    ) {
                        items(palette) { hex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                Color.DarkGray
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                                    .border(1.dp, UnityColors.Border, RoundedCornerShape(4.dp))
                                    .clickable {
                                        editorView?.app?.editor?.let { ed ->
                                            Gdx.app.postRunnable {
                                                ed.setSceneColor(hex)
                                            }
                                        }
                                        showColorPicker = false
                                        statusMessage = "Scene Color: $hex"
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =================================================================
// RIGHT SIDE DOCKED PANELS IMPLEMENTATIONS
// =================================================================

/**
 * 1. Bodies (Hierarchy) Panel
 */
@Composable
private fun BodiesListPanel(
    actorNames: List<String>,
    selectedName: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddObject: () -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(actorNames, searchQuery) {
        if (searchQuery.isBlank()) actorNames
        else actorNames.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        PanelHeader(title = "Hierarchy (${actorNames.size})", onClose = onClose)

        Spacer(modifier = Modifier.height(6.dp))

        // Search box
        SearchInput(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Filter bodies..."
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Actor List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (filtered.isEmpty()) {
                item {
                    BasicText(
                        text = if (actorNames.isEmpty()) "No objects in scene.\nClick '+ Object' to create one." else "No matching objects.",
                        style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(filtered) { name ->
                    val isSelected = name == selectedName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) UnityColors.AccentBlue else UnityColors.SurfaceVariant)
                            .clickable { onSelect(name) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(
                            text = name,
                            style = TextStyle(
                                color = UnityColors.TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        EditorTextButton(
                            text = "Del",
                            onClick = { onDelete(name) },
                            backgroundColor = Color(0xFFC62828),
                            textColor = Color.White,
                            paddingH = 4.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        EditorTextButton(
            text = "+ Add Object to Scene",
            onClick = onAddObject,
            backgroundColor = UnityColors.AccentBlue,
            textColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 2. Properties (Inspector) Panel
 */
@Composable
private fun PropertiesInspectorPanel(
    selectedActorName: String?,
    selectedProps: Map<String, String>,
    currentScene: String,
    editorView: Editor?,
    onClose: () -> Unit,
    onOpenVisualScripting: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PanelHeader(title = "Inspector", onClose = onClose)

        Spacer(modifier = Modifier.height(6.dp))

        if (selectedActorName != null) {
            // Selected Actor Details
            BasicText(
                text = "Target: $selectedActorName",
                style = TextStyle(color = UnityColors.AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            CategoryHeader("TRANSFORM")
            PropertyDisplayRow("X Position", selectedProps["x"] ?: "0")
            PropertyDisplayRow("Y Position", selectedProps["y"] ?: "0")
            PropertyDisplayRow("Width", selectedProps["width"] ?: "0")
            PropertyDisplayRow("Height", selectedProps["height"] ?: "0")
            PropertyDisplayRow("Rotation", "${selectedProps["rotation"] ?: "0"} deg")
            PropertyDisplayRow("Z Order", selectedProps["z"] ?: "0")

            Spacer(modifier = Modifier.height(8.dp))

            CategoryHeader("PHYSICS (BOX2D)")
            PropertyDisplayRow("Body Type", selectedProps["type"] ?: "dynamic")
            PropertyDisplayRow("Density", selectedProps["density"] ?: "1.0")
            PropertyDisplayRow("Friction", selectedProps["friction"] ?: "0.2")
            PropertyDisplayRow("Restitution", selectedProps["restitution"] ?: "0.0")

            Spacer(modifier = Modifier.height(10.dp))

            // VISUAL SCRIPTING BUTTON (NO emoji!)
            EditorTextButton(
                text = "Visual Scripting",
                onClick = onOpenVisualScripting,
                backgroundColor = Color(0xFF673AB7),
                textColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Delete Actor Button
            EditorTextButton(
                text = "Delete Object",
                onClick = {
                    val gdxEditor = editorView?.app?.editor ?: return@EditorTextButton
                    Gdx.app.postRunnable {
                        try {
                            val sel = gdxEditor.selectedActor
                            if (sel != null) {
                                gdxEditor.project?.deleteBody(sel.name, gdxEditor.scene)
                                sel.remove()
                                EditorAction.itemRemoved(gdxEditor, sel)
                                gdxEditor.selectActor(null)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                backgroundColor = Color(0xFFB71C1C),
                textColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Scene Settings
            BasicText(
                text = "Scene: $currentScene",
                style = TextStyle(color = UnityColors.AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = "No object selected.\nTap an object on the canvas or in Hierarchy to inspect its properties.",
                style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp)
            )
        }
    }
}

/**
 * 3. Joints (JointsList) Panel
 */
@Composable
private fun JointsListPanel(
    editorView: Editor?,
    currentScene: String,
    onClose: () -> Unit
) {
    var jointsList by remember { mutableStateOf(listOf<String>()) }

    fun refreshJoints() {
        val proj = editorView?.app?.project ?: editorView?.app?.editor?.project ?: return
        val jointsDir = File(proj.getJoints(currentScene))
        if (jointsDir.exists() && jointsDir.isDirectory) {
            val names = jointsDir.listFiles()?.map { it.name } ?: emptyList()
            jointsList = names
        } else {
            jointsList = emptyList()
        }
    }

    LaunchedEffect(currentScene) {
        refreshJoints()
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        PanelHeader(title = "Joints List (${jointsList.size})", onClose = onClose)

        Spacer(modifier = Modifier.height(6.dp))

        EditorTextButton(
            text = "+ Add Joint",
            onClick = {
                val gdxEditor = editorView?.app?.editor ?: return@EditorTextButton
                Gdx.app.postRunnable {
                    JointDialog.showJointListDialog(gdxEditor) {
                        Gdx.app.postRunnable { refreshJoints() }
                    }
                }
            },
            backgroundColor = UnityColors.AccentBlue,
            textColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (jointsList.isEmpty()) {
                item {
                    BasicText(
                        text = "No joints created for scene '$currentScene'.\nClick '+ Add Joint' to connect bodies.",
                        style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(jointsList) { jointName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.SurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(
                            text = jointName,
                            style = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Edit joint
                        EditorTextButton(
                            text = "Edit",
                            onClick = {
                                val gdxEditor = editorView?.app?.editor ?: return@EditorTextButton
                                val proj = gdxEditor.project ?: return@EditorTextButton
                                val type = if (jointName.contains("-")) jointName.substring(jointName.indexOf("-") + 1) else jointName
                                Gdx.app.postRunnable {
                                    try {
                                        val dialog = object : JointDialog(type, jointName, gdxEditor) {
                                            override fun onDone(string: String?, name: String?) {
                                                Gdx.files.absolute(proj.getJoints(currentScene) + jointName).writeString(string, false)
                                                refreshJoints()
                                            }
                                        }
                                        val valStr = Gdx.files.absolute(proj.getJoints(currentScene) + jointName).readString()
                                        dialog.setValue(valStr)
                                        gdxEditor.uiStage.addActor(dialog)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            backgroundColor = UnityColors.AccentBlue,
                            textColor = Color.White,
                            paddingH = 6.dp
                        )

                        Spacer(modifier = Modifier.width(3.dp))

                        // Delete joint
                        EditorTextButton(
                            text = "Del",
                            onClick = {
                                val proj = editorView?.app?.project ?: return@EditorTextButton
                                val file = File(proj.getJoints(currentScene) + jointName)
                                if (file.exists()) file.delete()
                                refreshJoints()
                            },
                            backgroundColor = Color(0xFFC62828),
                            textColor = Color.White,
                            paddingH = 4.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

/**
 * 4. Events Panel (Scene Scripts, Body Script, Custom Scripts)
 */
@Composable
private fun EventsManagerPanel(
    editorView: Editor?,
    currentScene: String,
    selectedActorName: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var customScripts by remember { mutableStateOf(listOf<String>()) }
    var newScriptName by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refreshScripts() {
        val proj = editorView?.app?.project ?: editorView?.app?.editor?.project ?: return
        val scriptsDir = File(proj.get("scripts"))
        if (scriptsDir.exists() && scriptsDir.isDirectory) {
            val list = scriptsDir.listFiles()
                ?.filter { it.name.endsWith(".java") }
                ?.map { it.name.removeSuffix(".java") } ?: emptyList()
            customScripts = list
        } else {
            customScripts = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        refreshScripts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        PanelHeader(title = "Events & Scripts", onClose = onClose)

        Spacer(modifier = Modifier.height(8.dp))

        // Scene Script
        CategoryHeader("SCENE SCRIPT")
        EditorTextButton(
            text = "Open Scene Script ($currentScene)",
            onClick = {
                val proj = editorView?.app?.project ?: return@EditorTextButton
                VisualScriptingDialog.openSceneScript(currentScene, proj.getSceneScript(currentScene))
            },
            backgroundColor = Color(0xFF1E88E5),
            textColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Body Script
        CategoryHeader("SELECTED BODY SCRIPT")
        if (selectedActorName != null) {
            EditorTextButton(
                text = "Open Script for '$selectedActorName'",
                onClick = {
                    VisualScriptingDialog.openCodeEditor()
                },
                backgroundColor = Color(0xFF43A047),
                textColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            BasicText(
                text = "Select a body to edit its script.",
                style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Custom Scripts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryHeader("CUSTOM SCRIPTS (${customScripts.size})")
            EditorTextButton(
                text = "+ Add Script",
                onClick = { showAddDialog = true },
                backgroundColor = UnityColors.AccentBlue,
                textColor = Color.White,
                paddingH = 6.dp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (showAddDialog) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(UnityColors.DarkBackground)
                        .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = newScriptName,
                        onValueChange = { newScriptName = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                        textStyle = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(UnityColors.AccentBlue),
                        decorationBox = { inner ->
                            if (newScriptName.isEmpty()) {
                                BasicText("Script name...", style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp))
                            }
                            inner()
                        }
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                EditorTextButton(
                    text = "Save",
                    onClick = {
                        val s = newScriptName.trim()
                        if (s.isNotEmpty()) {
                            val proj = editorView?.app?.project
                            if (proj != null) {
                                val scriptFile = File(proj.get("scripts") + s + ".java")
                                val visualFile = File(proj.get("scripts") + s + ".visual")
                                if (!scriptFile.exists()) scriptFile.writeText("")
                                if (!visualFile.exists()) visualFile.writeText("[]")
                                refreshScripts()
                                newScriptName = ""
                                showAddDialog = false
                            }
                        }
                    },
                    backgroundColor = UnityColors.AccentBlue
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (customScripts.isEmpty()) {
                item {
                    BasicText(
                        text = "No custom scripts yet. Click '+ Add Script' to create one.",
                        style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(customScripts) { script ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.SurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(
                            text = "$script.java",
                            style = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
                            modifier = Modifier.weight(1f)
                        )

                        EditorTextButton(
                            text = "Open",
                            onClick = {
                                VisualScriptingDialog.showFor(script, false, true)
                            },
                            backgroundColor = UnityColors.AccentBlue,
                            paddingH = 6.dp
                        )

                        Spacer(modifier = Modifier.width(3.dp))

                        EditorTextButton(
                            text = "Del",
                            onClick = {
                                val proj = editorView?.app?.project ?: return@EditorTextButton
                                val f1 = File(proj.get("scripts") + script + ".java")
                                val f2 = File(proj.get("scripts") + script + ".visual")
                                if (f1.exists()) f1.delete()
                                if (f2.exists()) f2.delete()
                                refreshScripts()
                            },
                            backgroundColor = Color(0xFFC62828),
                            paddingH = 4.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

/**
 * 5. Variables Manager Panel
 */
@Composable
private fun VariablesManagerPanel(
    editorView: Editor?,
    onClose: () -> Unit
) {
    var varName by remember { mutableStateOf("") }
    var varType by remember { mutableStateOf("int") }
    var varVal by remember { mutableStateOf("0") }
    var varsList by remember { mutableStateOf(listOf<Map<String, String>>()) }

    fun getVarsFile(): File? {
        val proj = editorView?.app?.project ?: editorView?.app?.editor?.project ?: return null
        return File(proj.path + "/variables.json")
    }

    fun loadVars() {
        val file = getVarsFile() ?: return
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<Map<String, String>>>() {}.type
                val list: List<Map<String, String>> = Gson().fromJson(json, type) ?: emptyList()
                varsList = list
            } catch (e: Exception) {
                varsList = emptyList()
            }
        } else {
            varsList = emptyList()
        }
    }

    fun saveVars(list: List<Map<String, String>>) {
        val file = getVarsFile() ?: return
        try {
            file.writeText(Gson().toJson(list))
            varsList = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        loadVars()
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        PanelHeader(title = "Variables (${varsList.size})", onClose = onClose)

        Spacer(modifier = Modifier.height(6.dp))

        CategoryHeader("ADD NEW VARIABLE")

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(UnityColors.DarkBackground)
                    .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = varName,
                    onValueChange = { varName = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                    textStyle = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(UnityColors.AccentBlue),
                    decorationBox = { inner ->
                        if (varName.isEmpty()) BasicText("Var name...", style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp))
                        inner()
                    }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Type cycle button
            val types = listOf("int", "float", "String", "boolean")
            EditorTextButton(
                text = varType,
                onClick = {
                    val idx = types.indexOf(varType)
                    varType = types[(idx + 1) % types.size]
                    if (varType == "boolean") varVal = "true"
                    else if (varType == "String") varVal = "\"text\""
                    else varVal = "0"
                },
                backgroundColor = UnityColors.SurfaceVariant,
                textColor = UnityColors.AccentBlue
            )

            Spacer(modifier = Modifier.width(4.dp))

            EditorTextButton(
                text = "Add",
                onClick = {
                    val name = varName.trim()
                    if (name.isNotEmpty()) {
                        val updated = varsList.toMutableList()
                        updated.add(mapOf("name" to name, "type" to varType, "value" to varVal))
                        saveVars(updated)
                        varName = ""
                    }
                },
                backgroundColor = UnityColors.AccentBlue
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        CategoryHeader("EXISTING VARIABLES")

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (varsList.isEmpty()) {
                item {
                    BasicText(
                        text = "No variables declared yet.",
                        style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(varsList) { item ->
                    val name = item["name"] ?: ""
                    val type = item["type"] ?: "int"
                    val value = item["value"] ?: ""
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.SurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = "public $type $name = $value;",
                                style = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            )
                        }

                        EditorTextButton(
                            text = "Del",
                            onClick = {
                                val updated = varsList.filter { it["name"] != name }
                                saveVars(updated)
                            },
                            backgroundColor = Color(0xFFC62828),
                            paddingH = 4.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

/**
 * 6. AI Assistant Panel
 */
@Composable
private fun AIAssistantPanel(
    editorView: Editor?,
    selectedActor: String?,
    onClose: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<String?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    fun askPrompt(text: String) {
        prompt = text
        isThinking = true
        response = null
        // Provide standard game development template responses immediately
        val answer = when {
            text.contains("movement", ignoreCase = true) ->
                "// 2D Player Movement Script\n" +
                "float speed = 5.0f;\n" +
                "if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {\n" +
                "    body.setLinearVelocity(speed, body.getLinearVelocity().y);\n" +
                "} else if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {\n" +
                "    body.setLinearVelocity(-speed, body.getLinearVelocity().y);\n" +
                "} else {\n" +
                "    body.setLinearVelocity(0, body.getLinearVelocity().y);\n" +
                "}"
            text.contains("jump", ignoreCase = true) ->
                "// 2D Jump Script (Impulse)\n" +
                "float jumpForce = 8.0f;\n" +
                "if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {\n" +
                "    body.applyLinearImpulse(new Vector2(0, jumpForce), body.getWorldCenter(), true);\n" +
                "}"
            text.contains("coin", ignoreCase = true) || text.contains("collect", ignoreCase = true) ->
                "// Coin Collection Logic\n" +
                "public void onCollision(PlayerItem other) {\n" +
                "    if (other.getName().startsWith(\"coin\")) {\n" +
                "        score += 10;\n" +
                "        other.remove();\n" +
                "        playSound(\"pickup.wav\");\n" +
                "    }\n" +
                "}"
            text.contains("patrol", ignoreCase = true) ->
                "// Enemy Patrol Logic\n" +
                "float leftBound = 2.0f, rightBound = 10.0f;\n" +
                "float dir = 1.0f;\n" +
                "if (body.getPosition().x >= rightBound) dir = -1.0f;\n" +
                "if (body.getPosition().x <= leftBound) dir = 1.0f;\n" +
                "body.setLinearVelocity(dir * 2.5f, body.getLinearVelocity().y);"
            else ->
                "// Star2D Game Logic snippet for '${selectedActor ?: "Actor"}'\n" +
                "// Query: $text\n" +
                "public void onUpdate(float delta) {\n" +
                "    // Custom game logic running per frame\n" +
                "    Vector2 pos = getPosition();\n" +
                "}"
        }
        response = answer
        isThinking = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PanelHeader(title = "AI Assistant", onClose = onClose)

        Spacer(modifier = Modifier.height(6.dp))

        CategoryHeader("QUICK TEMPLATES")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EditorTextButton(text = "Movement", onClick = { askPrompt("Player movement script") }, backgroundColor = UnityColors.SurfaceVariant)
            EditorTextButton(text = "Jump", onClick = { askPrompt("Player jump physics") }, backgroundColor = UnityColors.SurfaceVariant)
            EditorTextButton(text = "Coins", onClick = { askPrompt("Coin collect logic") }, backgroundColor = UnityColors.SurfaceVariant)
            EditorTextButton(text = "Patrol", onClick = { askPrompt("Enemy patrol movement") }, backgroundColor = UnityColors.SurfaceVariant)
        }

        Spacer(modifier = Modifier.height(8.dp))

        CategoryHeader("CUSTOM QUESTION")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(UnityColors.DarkBackground)
                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = prompt,
                onValueChange = { prompt = it },
                textStyle = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
                singleLine = true,
                cursorBrush = SolidColor(UnityColors.AccentBlue),
                decorationBox = { inner ->
                    if (prompt.isEmpty()) BasicText("Ask AI to generate logic...", style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp))
                    inner()
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        EditorTextButton(
            text = "Generate Script",
            onClick = { if (prompt.isNotBlank()) askPrompt(prompt) },
            backgroundColor = UnityColors.AccentBlue,
            textColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        )

        if (response != null) {
            Spacer(modifier = Modifier.height(10.dp))
            CategoryHeader("GENERATED LOGIC")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(UnityColors.DarkBackground)
                    .border(1.dp, UnityColors.AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                BasicText(
                    text = response!!,
                    style = TextStyle(color = Color(0xFF81C784), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
            }
        }
    }
}

/**
 * 7. Animation Editor Launcher Panel
 */
@Composable
private fun AnimationEditorLauncherPanel(
    context: Context,
    editorView: Editor?,
    onClose: () -> Unit
) {
    var animsList by remember { mutableStateOf(listOf<String>()) }
    var newAnimName by remember { mutableStateOf("") }

    fun refreshAnims() {
        val proj = editorView?.app?.project ?: editorView?.app?.editor?.project ?: return
        val dir = File(proj.path + "/anims")
        if (dir.exists() && dir.isDirectory) {
            val names = dir.listFiles()?.map { it.name } ?: emptyList()
            animsList = names
        } else {
            animsList = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        refreshAnims()
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        PanelHeader(title = "Animation Editor (${animsList.size})", onClose = onClose)

        Spacer(modifier = Modifier.height(6.dp))

        CategoryHeader("CREATE NEW ANIMATION")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(UnityColors.DarkBackground)
                    .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = newAnimName,
                    onValueChange = { newAnimName = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                    textStyle = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(UnityColors.AccentBlue),
                    decorationBox = { inner ->
                        if (newAnimName.isEmpty()) BasicText("Anim name...", style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp))
                        inner()
                    }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            EditorTextButton(
                text = "Create",
                onClick = {
                    val name = newAnimName.trim()
                    if (name.isNotEmpty()) {
                        val proj = editorView?.app?.project
                        if (proj != null) {
                            val animFile = File(proj.path + "/anims/" + name)
                            if (!animFile.exists()) {
                                animFile.parentFile?.mkdirs()
                                animFile.writeText("[]")
                            }
                            refreshAnims()
                            newAnimName = ""
                            // Launch AnimationActivity
                            val intent = Intent(context, AnimationActivity::class.java).apply {
                                putExtra("path", animFile.absolutePath)
                                putExtra("imgs", proj.imagesPath)
                            }
                            context.startActivity(intent)
                        }
                    }
                },
                backgroundColor = UnityColors.AccentBlue
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        CategoryHeader("ANIMATION FILES")

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (animsList.isEmpty()) {
                item {
                    BasicText(
                        text = "No animation files yet.\nEnter a name above to create one.",
                        style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(animsList) { animName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.SurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(
                            text = animName,
                            style = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
                            modifier = Modifier.weight(1f)
                        )

                        EditorTextButton(
                            text = "Open",
                            onClick = {
                                val proj = editorView?.app?.project ?: return@EditorTextButton
                                val animFile = File(proj.path + "/anims/" + animName)
                                val intent = Intent(context, AnimationActivity::class.java).apply {
                                    putExtra("path", animFile.absolutePath)
                                    putExtra("imgs", proj.imagesPath)
                                }
                                context.startActivity(intent)
                            },
                            backgroundColor = UnityColors.AccentBlue,
                            paddingH = 6.dp
                        )

                        Spacer(modifier = Modifier.width(3.dp))

                        EditorTextButton(
                            text = "Del",
                            onClick = {
                                val proj = editorView?.app?.project ?: return@EditorTextButton
                                val file = File(proj.path + "/anims/" + animName)
                                if (file.exists()) file.delete()
                                refreshAnims()
                            },
                            backgroundColor = Color(0xFFC62828),
                            paddingH = 4.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

/**
 * 8. Files Manager Panel
 */
@Composable
private fun FilesManagerPanel(
    editorView: Editor?,
    onClose: () -> Unit
) {
    var currentDirPath by remember { mutableStateOf("") }
    var fileList by remember { mutableStateOf(listOf<File>()) }

    fun refreshDir(path: String) {
        val f = File(path)
        if (f.exists() && f.isDirectory) {
            currentDirPath = f.absolutePath
            fileList = f.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        }
    }

    LaunchedEffect(Unit) {
        val proj = editorView?.app?.project ?: editorView?.app?.editor?.project
        if (proj != null) {
            refreshDir(proj.path)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        PanelHeader(title = "Files Manager", onClose = onClose)

        Spacer(modifier = Modifier.height(4.dp))

        // Quick navigation chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val proj = editorView?.app?.project
            if (proj != null) {
                EditorTextButton(text = "Root", onClick = { refreshDir(proj.path) }, backgroundColor = UnityColors.SurfaceVariant)
                EditorTextButton(text = "Images", onClick = { refreshDir(proj.imagesPath) }, backgroundColor = UnityColors.SurfaceVariant)
                EditorTextButton(text = "Scripts", onClick = { refreshDir(proj.get("scripts")) }, backgroundColor = UnityColors.SurfaceVariant)
                EditorTextButton(text = "Scenes", onClick = { refreshDir(proj.scenesPath) }, backgroundColor = UnityColors.SurfaceVariant)
                EditorTextButton(text = "Logs", onClick = { refreshDir(Gdx.files.external("logs").file().absolutePath) }, backgroundColor = UnityColors.SurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Current path & Up button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                text = currentDirPath.substringAfterLast('/'),
                style = TextStyle(color = UnityColors.AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val parent = File(currentDirPath).parentFile
            if (parent != null && parent.exists()) {
                EditorTextButton(
                    text = "Up ..",
                    onClick = { refreshDir(parent.absolutePath) },
                    backgroundColor = UnityColors.SurfaceVariant,
                    paddingH = 6.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (fileList.isEmpty()) {
                item {
                    BasicText(
                        text = "Folder is empty.",
                        style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(fileList) { f ->
                    val isDir = f.isDirectory
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.SurfaceVariant)
                            .clickable {
                                if (isDir) refreshDir(f.absolutePath)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(
                            text = (if (isDir) "[D] " else "[F] ") + f.name,
                            style = TextStyle(
                                color = if (isDir) UnityColors.AccentYellow else UnityColors.TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (isDir) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        EditorTextButton(
                            text = "Del",
                            onClick = {
                                f.delete()
                                refreshDir(currentDirPath)
                            },
                            backgroundColor = Color(0xFFC62828),
                            paddingH = 4.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

// =================================================================
// REUSABLE UI HELPERS (Clean styling, NO emojis)
// =================================================================

@Composable
fun EditorTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = UnityColors.SurfaceVariant,
    textColor: Color = UnityColors.TextWhite,
    borderColor: Color? = null,
    paddingH: androidx.compose.ui.unit.Dp = 8.dp,
    paddingV: androidx.compose.ui.unit.Dp = 5.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(backgroundColor)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, RoundedCornerShape(3.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun PanelHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            style = TextStyle(color = UnityColors.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
        EditorTextButton(
            text = "Close",
            onClick = onClose,
            backgroundColor = Color.Transparent,
            textColor = UnityColors.TextMuted,
            paddingH = 4.dp
        )
    }
}

@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(UnityColors.DarkBackground)
            .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
            singleLine = true,
            cursorBrush = SolidColor(UnityColors.AccentBlue),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    BasicText(placeholder, style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp))
                }
                inner()
            }
        )
    }
}

@Composable
private fun PropertyDisplayRow(name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(text = name, style = TextStyle(color = UnityColors.TextMuted, fontSize = 11.sp))
        BasicText(text = value, style = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun CategoryHeader(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            color = UnityColors.AccentBlue,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        modifier = Modifier.padding(vertical = 3.dp)
    )
}

@Composable
fun AddObjectItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(UnityColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            BasicText(
                text = description,
                style = TextStyle(color = UnityColors.TextMuted, fontSize = 9.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        EditorTextButton(
            text = "Add",
            onClick = onClick,
            backgroundColor = UnityColors.AccentBlue,
            textColor = Color.White,
            paddingH = 6.dp
        )
    }
    Spacer(modifier = Modifier.height(3.dp))
}

@Composable
fun EditorFloatingDialog(
    title: String,
    onClose: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    initialX: Float = 16f,
    initialY: Float = 16f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .offset(x = initialX.dp, y = initialY.dp)
            .width(width)
            .clip(RoundedCornerShape(6.dp))
            .background(UnityColors.Surface)
            .border(1.dp, UnityColors.Border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Title Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(UnityColors.Header)
                    .border(0.5.dp, UnityColors.Border, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(
                    text = title,
                    style = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
                EditorTextButton(
                    text = "Close",
                    onClick = onClose,
                    backgroundColor = Color.Transparent,
                    textColor = UnityColors.TextMuted,
                    paddingH = 4.dp
                )
            }

            // Body
            Box(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}
