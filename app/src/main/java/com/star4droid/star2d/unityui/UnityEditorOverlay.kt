package com.star4droid.star2d.unityui

import android.app.Activity
import android.content.Context
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
import com.star4droid.star2d.Adapters.VisualScriptingDialog
import com.star4droid.star2d.Helpers.CodeGenerator
import com.star4droid.star2d.Helpers.CompileThread
import com.star4droid.star2d.Helpers.FileUtil
import com.star4droid.star2d.Helpers.PropertySet
import com.star4droid.star2d.Items.Editor
import com.star4droid.star2d.editor.LibgdxEditor
import com.star4droid.star2d.editor.items.*
import com.star4droid.star2d.editor.utils.EditorAction
import com.star4droid.template.Items.StageImp
import java.io.File
import java.util.ArrayList

/**
 * Modern Jetpack Compose Game Editor Overlay for Star2D.
 * Integrates directly with the underlying Star2D LibGDX engine without modifying
 * engine system logic, physics, or serialization.
 *
 * Provides a complete editor toolbar, Add Object modal/drawer, Scene Manager,
 * Transform & Touch mode controls, Axis lock, Orientation toggle, Background Color picker,
 * Hierarchy and Properties Inspector, with the restored original Visual Scripting integration.
 *
 * When playtesting starts, the entire UI disappears so the game plays full-screen.
 */
@Composable
fun UnityEditorOverlay(
    editorView: Editor?,
    onExitToHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Dialog & Panel visibility states
    var showHierarchy by remember { mutableStateOf(false) }
    var showInspector by remember { mutableStateOf(false) }
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
                // Ignore transient GL thread query issues
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
            kotlinx.coroutines.delay(800)
        }
    }

    // -------------------------------------------------------------
    // IF GAME IS PLAYING: The entire editor overlay hides completely!
    // As requested: "وعند بدء اللعبه هي لا تختفي... و كذلك لو مثلا مشغل لعبة يبقى top bar الي انت ضفته عائمة المفروض يختفي"
    // -------------------------------------------------------------
    if (isPlaying) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            // Subtle translucent stop chip in corner so user can also stop on-screen
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xCCB71C1C))
                    .clickable {
                        editorView?.app?.let { app ->
                            Gdx.app.postRunnable {
                                try {
                                    app.play(null as StageImp?)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                BasicText(
                    text = "■ Stop Playtest",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        return
    }

    // -------------------------------------------------------------
    // NORMAL GAME EDITOR VIEW
    // -------------------------------------------------------------
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isNarrowPhone = maxWidth < 600.dp

        // Main Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // Primary Toolbar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(UnityColors.Header)
                    .border(0.5.dp, UnityColors.Border, RoundedCornerShape(0.dp))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Section 1: Exit, Scene Selector, Add Object
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Exit Button -> Triggers Confirmation Dialog via onExitToHub()
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(UnityColors.SurfaceVariant)
                            .clickable { onExitToHub() }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "← Exit",
                            style = TextStyle(
                                color = UnityColors.TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Scene Selector Dropdown Pill
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(UnityColors.DarkBackground)
                            .border(1.dp, UnityColors.AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .clickable {
                                refreshSceneList()
                                showSceneManager = true
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "🎬 $currentScene ▾",
                            style = TextStyle(
                                color = UnityColors.AccentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Add Object Button (+ Add)
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(UnityColors.AccentBlue)
                            .clickable { showAddObjectMenu = !showAddObjectMenu }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "+ Add",
                            style = TextStyle(
                                color = UnityColors.TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Section 2: Tools, Rotate, Color, Files, Undo, Redo, Save, Play
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Orientation toggle (Landscape / Portrait)
                    UnityIconButton(
                        iconText = "🔄",
                        onClick = {
                            val app = editorView?.app
                            if (app != null) {
                                val gdxEditor = app.editor
                                val isLand = gdxEditor?.isLandscape == true
                                app.setOrienation(!isLand)
                                statusMessage = if (!isLand) "Orientation: Landscape" else "Orientation: Portrait"
                            }
                        },
                        size = 30.dp
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    // Scene background color picker
                    UnityIconButton(
                        iconText = "🎨",
                        onClick = { showColorPicker = !showColorPicker },
                        size = 30.dp
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    // File Manager / Assets Browser
                    UnityIconButton(
                        iconText = "📁",
                        onClick = {
                            editorView?.app?.fileBrowser?.let { fb ->
                                Gdx.app.postRunnable {
                                    try {
                                        fb.isVisible = !fb.isVisible
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                        size = 30.dp
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    // Undo
                    UnityIconButton(
                        iconText = "↶",
                        onClick = {
                            editorView?.app?.editor?.let { ed ->
                                Gdx.app.postRunnable { ed.undo() }
                            }
                        },
                        size = 30.dp
                    )

                    // Redo
                    UnityIconButton(
                        iconText = "↷",
                        onClick = {
                            editorView?.app?.editor?.let { ed ->
                                Gdx.app.postRunnable { ed.redo() }
                            }
                        },
                        size = 30.dp
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    // Save Scene Button
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
                        size = 30.dp
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // PLAY TEST BUTTON
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(UnityColors.AccentGreen)
                            .clickable {
                                val app = editorView?.app
                                val gdxEditor = app?.editor
                                if (app != null && gdxEditor != null) {
                                    statusMessage = "Compiling & launching playtest..."
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
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "▶ Play",
                            style = TextStyle(
                                color = UnityColors.TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Hierarchy Toggle Button
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
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

                    Spacer(modifier = Modifier.width(3.dp))

                    // Inspector Toggle Button
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
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

            // Secondary Sub-Bar: Touch Mode Tools & Axis Lock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(UnityColors.DarkBackground)
                    .border(0.5.dp, UnityColors.Border, RoundedCornerShape(0.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "TOOLS:",
                    style = TextStyle(color = UnityColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.width(6.dp))

                val modes = listOf(
                    "GRID" to "⊞ Select",
                    "MOVE" to "✥ Move",
                    "SCALE" to "⤢ Scale",
                    "ROTATE" to "↻ Rotate"
                )

                for ((modeKey, modeLabel) in modes) {
                    val isActive = activeTouchMode == modeKey
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isActive) UnityColors.AccentBlue else UnityColors.SurfaceVariant)
                            .clickable {
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
                            }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = modeLabel,
                            style = TextStyle(
                                color = if (isActive) UnityColors.TextWhite else UnityColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Axis Lock selector
                val axisLocks = listOf(
                    "FREE" to "🔓 Free",
                    "LOCK_X" to "🔒 Lock X",
                    "LOCK_Y" to "🔒 Lock Y"
                )
                for ((lockKey, lockLabel) in axisLocks) {
                    val isLockActive = lockAxisMode == lockKey
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isLockActive) UnityColors.AccentOrange else UnityColors.SurfaceVariant)
                            .clickable {
                                lockAxisMode = lockKey
                                editorView?.app?.editor?.let { ed ->
                                    Gdx.app.postRunnable {
                                        ed.setLockX(lockKey == "LOCK_X")
                                        ed.setLockY(lockKey == "LOCK_Y")
                                    }
                                }
                            }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = lockLabel,
                            style = TextStyle(
                                color = if (isLockActive) UnityColors.TextWhite else UnityColors.TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Center camera button
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(UnityColors.SurfaceVariant)
                        .clickable {
                            editorView?.app?.editor?.let { ed ->
                                Gdx.app.postRunnable { ed.centerCamera() }
                            }
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "⊙ Center Cam",
                        style = TextStyle(color = UnityColors.TextWhite, fontSize = 9.sp)
                    )
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

        // -------------------------------------------------------------
        // ADD OBJECT MODAL SHEET
        // Adds objects directly into the LibGDX Stage and Box2D World!
        // -------------------------------------------------------------
        if (showAddObjectMenu) {
            UnityDraggableWindow(
                title = "Add Object to Scene",
                onClose = { showAddObjectMenu = false },
                width = if (isNarrowPhone) 290.dp else 340.dp,
                initialX = 12f,
                initialY = 82f
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

                    // Physics Bodies Category
                    CategoryHeader("PHYSICS BODIES (Box2D)")
                    AddObjectItem("📦 Box Body", "Rectangle rigid body with collision") { addObjectToEngine("box") }
                    AddObjectItem("⚪ Circle Body", "Circular rigid body with radius") { addObjectToEngine("circle") }
                    AddObjectItem("📐 Custom Body", "Polygon rigid body with custom vertices") { addObjectToEngine("custom") }

                    Spacer(modifier = Modifier.height(8.dp))

                    // UI & Gameplay Category
                    CategoryHeader("GAMEPLAY & CONTROLS")
                    AddObjectItem("🔤 Text Item", "Dynamic text label with custom font") { addObjectToEngine("text") }
                    AddObjectItem("🕹️ Virtual Joystick", "Touch joystick control for mobile games") { addObjectToEngine("joystick") }
                    AddObjectItem("📊 Progress Bar", "Health, mana, or stamina progress bar") { addObjectToEngine("progress") }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Visuals & Effects
                    CategoryHeader("VISUALS & ENVIRONMENT")
                    AddObjectItem("✨ Particle Effect", "2D particle emitter effect") { addObjectToEngine("particle") }
                    AddObjectItem("🎥 Camera", "Viewport follow camera") { addObjectToEngine("camera") }
                    AddObjectItem("🗺️ Tiled Map", "Tiled map rendering component") { addObjectToEngine("map") }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dynamic Lighting
                    CategoryHeader("DYNAMIC LIGHTING (Box2D Lights)")
                    AddObjectItem("💡 Point Light", "Omnidirectional 360° point light source") { addObjectToEngine("light_point") }
                    AddObjectItem("☀️ Directional Light", "Sun / global directional light rays") { addObjectToEngine("light_directional") }
                    AddObjectItem("🔦 Cone Light", "Spotlight / flashlight cone beam") { addObjectToEngine("light_cone") }
                }
            }
        }

        // -------------------------------------------------------------
        // SCENE MANAGER MODAL
        // -------------------------------------------------------------
        if (showSceneManager) {
            var newSceneName by remember { mutableStateOf("") }
            var isCreatingScene by remember { mutableStateOf(false) }

            UnityDraggableWindow(
                title = "Scene Manager",
                onClose = { showSceneManager = false },
                width = if (isNarrowPhone) 280.dp else 320.dp,
                initialX = 16f,
                initialY = 82f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    BasicText(
                        text = "Current Scene: $currentScene",
                        style = TextStyle(color = UnityColors.AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scenes List
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(UnityColors.DarkBackground)
                            .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(availableScenes) { sName ->
                                val isCurrent = sName.equals(currentScene, ignoreCase = true)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isCurrent) UnityColors.AccentBlue.copy(alpha = 0.35f) else Color.Transparent)
                                        .clickable {
                                            if (!isCurrent) {
                                                showSceneManager = false
                                                editorView?.app?.let { app ->
                                                    Gdx.app.postRunnable {
                                                        app.openSceneInNewEditor(sName)
                                                        refreshSceneState()
                                                    }
                                                }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BasicText(
                                            text = if (isCurrent) "▶ " else "  ",
                                            style = TextStyle(color = UnityColors.AccentBlue, fontSize = 10.sp)
                                        )
                                        BasicText(
                                            text = sName,
                                            style = TextStyle(
                                                color = if (isCurrent) UnityColors.TextWhite else UnityColors.TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    }

                                    // Delete scene (only if not scene1 and not current)
                                    if (!sName.equals("scene1", ignoreCase = true) && availableScenes.size > 1) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(UnityColors.AccentRed.copy(alpha = 0.8f))
                                                .clickable {
                                                    editorView?.app?.let { app ->
                                                        val proj = app.project ?: app.editor?.project
                                                        Gdx.app.postRunnable {
                                                            try {
                                                                proj?.deleteScene(sName)
                                                                app.openSceneInNewEditor("scene1")
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }
                                                        }
                                                    }
                                                    refreshSceneList()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(text = "✕", style = TextStyle(color = Color.White, fontSize = 9.sp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Create New Scene Input
                    if (isCreatingScene) {
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
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = newSceneName,
                                    onValueChange = { newSceneName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = UnityColors.TextWhite, fontSize = 11.sp),
                                    cursorBrush = SolidColor(UnityColors.AccentBlue)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            UnityButton(
                                text = "Create",
                                onClick = {
                                    val s = newSceneName.trim()
                                    if (s.isNotEmpty()) {
                                        editorView?.app?.let { app ->
                                            val proj = app.project ?: app.editor?.project
                                            if (proj != null) {
                                                Gdx.app.postRunnable {
                                                    try {
                                                        Gdx.files.absolute(proj.get("scenes") + "/" + s).writeString("", false)
                                                        app.openSceneInNewEditor(s)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                        newSceneName = ""
                                        isCreatingScene = false
                                        refreshSceneList()
                                    }
                                },
                                variant = UnityButtonVariant.Primary
                            )

                            Spacer(modifier = Modifier.width(2.dp))

                            UnityButton(
                                text = "✕",
                                onClick = { isCreatingScene = false },
                                variant = UnityButtonVariant.Normal
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            UnityButton(
                                text = "+ New Scene",
                                onClick = { isCreatingScene = true },
                                variant = UnityButtonVariant.Primary
                            )

                            // Duplicate Current Scene
                            UnityButton(
                                text = "Duplicate",
                                onClick = {
                                    editorView?.app?.let { app ->
                                        val proj = app.project ?: app.editor?.project
                                        val dupName = currentScene + "_copy"
                                        if (proj != null) {
                                            Gdx.app.postRunnable {
                                                try {
                                                    proj.copyScene(currentScene, dupName)
                                                    app.openSceneInNewEditor(dupName)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    }
                                    refreshSceneList()
                                },
                                variant = UnityButtonVariant.Normal
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SCENE COLOR PICKER MODAL
        // -------------------------------------------------------------
        if (showColorPicker) {
            val palette = listOf(
                "#1E1E1E", "#263238", "#000000", "#1A237E",
                "#0D47A1", "#004D40", "#1B5E20", "#3E2723",
                "#4A148C", "#880E4F", "#B71C1C", "#FFFFFF"
            )

            UnityDraggableWindow(
                title = "Scene Background Color",
                onClose = { showColorPicker = false },
                width = 240.dp,
                initialX = 50f,
                initialY = 82f
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "Select background color for $currentScene:",
                        style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(palette) { hex ->
                            val parsedColor = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                Color.DarkGray
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(parsedColor)
                                    .border(1.dp, UnityColors.Border, RoundedCornerShape(4.dp))
                                    .clickable {
                                        showColorPicker = false
                                        editorView?.app?.editor?.let { gdxEditor ->
                                            Gdx.app.postRunnable {
                                                gdxEditor.setSceneColor(hex)
                                            }
                                        }
                                        statusMessage = "Scene color updated to $hex"
                                    }
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // HIERARCHY WINDOW (Scene Actors List)
        // -------------------------------------------------------------
        if (showHierarchy) {
            UnityDraggableWindow(
                title = "Hierarchy",
                onClose = { showHierarchy = false },
                width = if (isNarrowPhone) 260.dp else 290.dp,
                initialX = 12f,
                initialY = 82f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    // Header Toolbar (+ Add, 🗑 Delete)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
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
                                    text = "Empty Scene\nTap '+ Add' to place an object",
                                    style = UnityTypography.BodySmall.copy(
                                        color = UnityColors.TextMuted,
                                        textAlign = TextAlign.Center
                                    )
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

        // -------------------------------------------------------------
        // INSPECTOR WINDOW (Properties of Selected Actor)
        // -------------------------------------------------------------
        if (showInspector) {
            UnityDraggableWindow(
                title = "Inspector: ${selectedActorName ?: "Scene"}",
                onClose = { showInspector = false },
                width = if (isNarrowPhone) 260.dp else 290.dp,
                initialX = if (isNarrowPhone) 40f else 320f,
                initialY = 82f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedActorName == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "Select an object in Hierarchy or tap it on screen to inspect its properties",
                                style = UnityTypography.BodySmall.copy(
                                    color = UnityColors.TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    } else {
                        BasicText(
                            text = "TRANSFORM & PHYSICS",
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
                        val densityVal = selectedActorProps["density"] ?: "1.0"
                        val frictionVal = selectedActorProps["friction"] ?: "0.2"
                        val restitutionVal = selectedActorProps["restitution"] ?: "0.0"

                        InspectorFieldRow(label = "Position X / Y", value = "$xVal, $yVal")
                        InspectorFieldRow(label = "Size W / H", value = "$wVal, $hVal")
                        InspectorFieldRow(label = "Rotation", value = "$rVal°")
                        InspectorFieldRow(label = "Z-Index", value = zVal)
                        InspectorFieldRow(label = "Body Type", value = typeVal)
                        InspectorFieldRow(label = "Density", value = densityVal)
                        InspectorFieldRow(label = "Friction", value = frictionVal)
                        InspectorFieldRow(label = "Restitution", value = restitutionVal)

                        Spacer(modifier = Modifier.height(12.dp))

                        // RESTORED ORIGINAL VISUAL SCRIPTING INTEGRATION:
                        // Opens original VisualScriptingDialog without any modified activities!
                        UnityButton(
                            text = "⚡ Visual Scripting",
                            onClick = {
                                val selected = selectedActorName
                                if (selected != null) {
                                    (context as? Activity)?.runOnUiThread {
                                        try {
                                            VisualScriptingDialog.showFor(
                                                selected,
                                                true, // isBody
                                                false // isScript
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
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
private fun CategoryHeader(title: String) {
    BasicText(
        text = title,
        style = TextStyle(
            color = UnityColors.AccentBlue,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun AddObjectItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(vertical = 5.dp, horizontal = 4.dp)
    ) {
        Column {
            BasicText(
                text = title,
                style = TextStyle(
                    color = UnityColors.TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            BasicText(
                text = subtitle,
                style = TextStyle(
                    color = UnityColors.TextMuted,
                    fontSize = 9.sp
                )
            )
        }
    }
}

@Composable
fun InspectorFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(
            text = label,
            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
        )
        Box(
            modifier = Modifier
                .widthIn(min = 70.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(UnityColors.DarkBackground)
                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.5.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            BasicText(
                text = value,
                style = UnityTypography.Code.copy(color = UnityColors.TextWhite, fontSize = 9.sp)
            )
        }
    }
}
