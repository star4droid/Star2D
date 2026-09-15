package com.star4droid.star2d.unityui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star4droid.star2d.Activities.DonateActivity
import com.star4droid.star2d.Helpers.FileUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class UnityProjectItem(
    val name: String,
    val path: String,
    val lastModified: Long,
    val formattedDate: String
)

data class UnityTemplateItem(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val assetFolder: String
)

/**
 * Copies assets recursively to target directory.
 */
fun copyAssetFolder(context: Context, assetFolderPath: String, targetDir: File): Boolean {
    return try {
        val assetManager = context.assets
        val files = assetManager.list(assetFolderPath) ?: return false
        if (!targetDir.exists()) targetDir.mkdirs()
        for (filename in files) {
            val fullAssetPath = if (assetFolderPath.isEmpty()) filename else "$assetFolderPath/$filename"
            val subFiles = assetManager.list(fullAssetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(context, fullAssetPath, File(targetDir, filename))
            } else {
                val targetFile = File(targetDir, filename)
                assetManager.open(fullAssetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Ignore if no activity can handle
    }
}

/**
 * Unity Hub Project Creating & Management Screen.
 * Fully adapted for both phone screens and tablets.
 * Includes: Projects, Templates, Community, and App Settings.
 * ZERO Material 3 components.
 */
@Composable
fun UnityProjectHub(
    onOpenProject: (String, String) -> Unit,
    onImportProject: () -> Unit,
    onExportProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("Projects") }
    var projectsList by remember { mutableStateOf(listOf<UnityProjectItem>()) }
    var activeActionProject by remember { mutableStateOf<UnityProjectItem?>(null) }
    var templateToCreate by remember { mutableStateOf<UnityTemplateItem?>(null) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    fun refreshProjects() {
        val projectsDir = File(context.filesDir, "projects")
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }
        val items = mutableListOf<UnityProjectItem>()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val files = projectsDir.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.isDirectory) {
                    items.add(
                        UnityProjectItem(
                            name = f.name,
                            path = f.absolutePath,
                            lastModified = f.lastModified(),
                            formattedDate = dateFormat.format(Date(f.lastModified()))
                        )
                    )
                }
            }
        }
        items.sortByDescending { it.lastModified }
        projectsList = items
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    val filteredProjects = remember(searchQuery, projectsList) {
        if (searchQuery.isBlank()) {
            projectsList
        } else {
            projectsList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val hubTabs = listOf("Projects", "Templates", "Community", "Settings")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(UnityColors.Background)
    ) {
        val isMobileCompact = maxWidth < 620.dp

        if (isMobileCompact) {
            // Mobile Layout: Top Navigation Bar + Full Width Content
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(UnityColors.SidebarBackground)
                        .border(0.5.dp, UnityColors.Border, RoundedCornerShape(0.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(UnityColors.AccentBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "2D",
                                style = TextStyle(
                                    color = UnityColors.TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = "Star2D Engine",
                            style = UnityTypography.Header.copy(
                                color = UnityColors.TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                // Mobile Horizontal Tab Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UnityColors.DarkBackground)
                        .border(0.5.dp, UnityColors.Border, RoundedCornerShape(0.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (tab in hubTabs) {
                        val isActive = selectedTab == tab
                        val tabBg = if (isActive) UnityColors.SurfaceVariant else Color.Transparent
                        val tabTextColor = if (isActive) UnityColors.TextWhite else UnityColors.TextSecondary

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(tabBg)
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = tab,
                                style = UnityTypography.BodySmall.copy(
                                    color = tabTextColor,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }

                // Main Content View
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        "Projects" -> ProjectsView(
                            projects = filteredProjects,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            onNewProject = { showNewProjectDialog = true },
                            onImportProject = onImportProject,
                            onOpenProject = onOpenProject,
                            onActionProject = { activeActionProject = it }
                        )
                        "Templates" -> TemplatesView(
                            context = context,
                            onSelectTemplate = { templateToCreate = it }
                        )
                        "Community" -> CommunityView(context = context)
                        "Settings" -> SettingsView(
                            context = context,
                            onStatus = { statusFeedback = it }
                        )
                    }
                }
            }
        } else {
            // Tablet / Desktop Layout: Left Sidebar + Main Content Area
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Sidebar
                Column(
                    modifier = Modifier
                        .width(190.dp)
                        .fillMaxHeight()
                        .background(UnityColors.SidebarBackground)
                        .border(1.dp, UnityColors.Border, RoundedCornerShape(0.dp))
                        .padding(vertical = 16.dp, horizontal = 12.dp)
                ) {
                    // Logo & Brand
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(UnityColors.AccentBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "2D",
                                style = TextStyle(
                                    color = UnityColors.TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            BasicText(
                                text = "Star2D Engine",
                                style = UnityTypography.Header.copy(
                                    color = UnityColors.TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Navigation Tabs
                    for (tab in hubTabs) {
                        val isActive = selectedTab == tab
                        val tabBg = if (isActive) UnityColors.SurfaceVariant else Color.Transparent
                        val tabTextColor = if (isActive) UnityColors.TextWhite else UnityColors.TextSecondary

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(tabBg)
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(18.dp)
                                            .background(UnityColors.AccentBlue)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                BasicText(
                                    text = tab,
                                    style = UnityTypography.Button.copy(
                                        color = tabTextColor,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Version Footer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UnityColors.DarkBackground)
                            .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            BasicText(
                                text = "Editor Version",
                                style = UnityTypography.BodySmall.copy(color = UnityColors.TextMuted)
                            )
                            BasicText(
                                text = "Star2D 2.0.0-Evo",
                                style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
                            )
                        }
                    }
                }

                // Main Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(UnityColors.Background)
                ) {
                    when (selectedTab) {
                        "Projects" -> ProjectsView(
                            projects = filteredProjects,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            onNewProject = { showNewProjectDialog = true },
                            onImportProject = onImportProject,
                            onOpenProject = onOpenProject,
                            onActionProject = { activeActionProject = it }
                        )
                        "Templates" -> TemplatesView(
                            context = context,
                            onSelectTemplate = { templateToCreate = it }
                        )
                        "Community" -> CommunityView(context = context)
                        "Settings" -> SettingsView(
                            context = context,
                            onStatus = { statusFeedback = it }
                        )
                    }
                }
            }
        }

        // Status Toast Message
        if (statusFeedback != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(UnityColors.Surface)
                    .border(1.dp, UnityColors.AccentBlue, RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                BasicText(
                    text = statusFeedback!!,
                    style = UnityTypography.Body.copy(color = UnityColors.TextWhite)
                )
            }
            LaunchedEffect(statusFeedback) {
                kotlinx.coroutines.delay(2500)
                statusFeedback = null
            }
        }

        // Draggable Unity "New Project" Window (Adapted for phone screen bounds)
        if (showNewProjectDialog) {
            UnityModal(onDismissRequest = { showNewProjectDialog = false }) {
                UnityDraggableWindow(
                    title = "New Project",
                    onClose = { showNewProjectDialog = false },
                    width = 360.dp,
                    initialX = 16f,
                    initialY = 24f
                ) {
                    NewProjectDialogContent(
                        context = context,
                        onCancel = { showNewProjectDialog = false },
                        onCreate = { newName, newPath ->
                            showNewProjectDialog = false
                            refreshProjects()
                            onOpenProject(newName, newPath)
                        }
                    )
                }
            }
        }

        // Create Project From Template Dialog
        if (templateToCreate != null) {
            val template = templateToCreate!!
            var tplProjName by remember { mutableStateOf(template.id) }
            val defaultDir = remember { File(context.filesDir, "projects").absolutePath }
            val isValidTplName = tplProjName.isNotBlank() && !tplProjName.contains("/") && !tplProjName.contains("\\")

            UnityModal(onDismissRequest = { templateToCreate = null }) {
                UnityDraggableWindow(
                    title = "Create from: ${template.name}",
                    onClose = { templateToCreate = null },
                    width = 360.dp,
                    initialX = 16f,
                    initialY = 24f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        BasicText(
                            text = template.description,
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        BasicText(
                            text = "Project Name",
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        UnityTextField(
                            value = tplProjName,
                            onValueChange = { tplProjName = it },
                            placeholder = "Enter project name..."
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            UnityButton(
                                text = "Cancel",
                                onClick = { templateToCreate = null },
                                variant = UnityButtonVariant.Normal
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            UnityButton(
                                text = "Create & Open",
                                enabled = isValidTplName,
                                onClick = {
                                    val cleanName = tplProjName.trim()
                                    val targetDir = File(defaultDir, cleanName)
                                    targetDir.mkdirs()
                                    // Copy from assets
                                    copyAssetFolder(context, "files/examples/${template.assetFolder}", targetDir)
                                    // Ensure required directories
                                    File(targetDir, "sounds").mkdirs()
                                    File(targetDir, "anims").mkdirs()
                                    File(targetDir, "images").mkdirs()
                                    File(targetDir, "files").mkdirs()
                                    File(targetDir, "scenes").mkdirs()
                                    File(targetDir, "joints").mkdirs()
                                    File(targetDir, "icon").mkdirs()

                                    templateToCreate = null
                                    refreshProjects()
                                    onOpenProject(cleanName, targetDir.absolutePath)
                                },
                                variant = UnityButtonVariant.Primary
                            )
                        }
                    }
                }
            }
        }

        // Action Menu Dialog for Project (Export, Backup, Delete)
        if (activeActionProject != null) {
            val proj = activeActionProject!!
            UnityModal(onDismissRequest = { activeActionProject = null }) {
                UnityDraggableWindow(
                    title = "Project: ${proj.name}",
                    onClose = { activeActionProject = null },
                    width = 280.dp,
                    initialX = 24f,
                    initialY = 40f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicText(
                            text = "Actions for \"${proj.name}\":",
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        UnityButton(
                            text = "Open Project",
                            onClick = {
                                val p = activeActionProject
                                activeActionProject = null
                                if (p != null) onOpenProject(p.name, p.path)
                            },
                            variant = UnityButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        UnityButton(
                            text = "Export APK / ZIP",
                            onClick = {
                                val p = activeActionProject
                                activeActionProject = null
                                if (p != null) onExportProject(p.path)
                            },
                            variant = UnityButtonVariant.Normal,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        UnityButton(
                            text = "Delete Project",
                            onClick = {
                                val p = activeActionProject
                                activeActionProject = null
                                if (p != null) {
                                    onDeleteProject(p.path)
                                    refreshProjects()
                                }
                            },
                            variant = UnityButtonVariant.Danger,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Projects View content tab.
 */
@Composable
fun ProjectsView(
    projects: List<UnityProjectItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onNewProject: () -> Unit,
    onImportProject: () -> Unit,
    onOpenProject: (String, String) -> Unit,
    onActionProject: (UnityProjectItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                BasicText(
                    text = "Projects",
                    style = UnityTypography.Title.copy(
                        color = UnityColors.TextWhite,
                        fontSize = 18.sp
                    )
                )
                BasicText(
                    text = "${projects.size} project(s) in workspace",
                    style = UnityTypography.BodySmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                UnityButton(
                    text = "Open",
                    onClick = onImportProject,
                    variant = UnityButtonVariant.Normal
                )
                Spacer(modifier = Modifier.width(6.dp))
                UnityButton(
                    text = "New project",
                    onClick = onNewProject,
                    variant = UnityButtonVariant.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        UnityTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Search projects...",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Projects List Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(UnityColors.Surface)
                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
        ) {
            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(
                            text = "No projects found",
                            style = UnityTypography.Header.copy(color = UnityColors.TextMuted)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicText(
                            text = "Click \"New project\" or choose from \"Templates\" to start",
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(projects, key = { it.path }) { item ->
                        ProjectTableRow(
                            project = item,
                            onClick = { onOpenProject(item.name, item.path) },
                            onActionClick = { onActionProject(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Templates / Examples View tab.
 */
@Composable
fun TemplatesView(
    context: Context,
    onSelectTemplate: (UnityTemplateItem) -> Unit
) {
    val templates = remember {
        listOf(
            UnityTemplateItem(
                id = "StarValley",
                name = "Star Valley RPG",
                category = "RPG / Adventure",
                description = "Drive your vehicle with on-screen joystick, collect collectibles, Day/Night lighting system and compass navigation.",
                assetFolder = "StarValley"
            ),
            UnityTemplateItem(
                id = "Platformer2D",
                name = "2D Platformer",
                category = "Action / Platformer",
                description = "Classic 2D platformer starter kit with jump physics, obstacle colliders, and level mechanics.",
                assetFolder = "platformer"
            ),
            UnityTemplateItem(
                id = "CarsExample",
                name = "Vehicle Physics",
                category = "Simulation / Physics",
                description = "2D car steering simulation utilizing Box2D joint physics, friction, and suspension tuning.",
                assetFolder = "CarsExample"
            ),
            UnityTemplateItem(
                id = "PaulAdventure",
                name = "Character Demo",
                category = "Platformer",
                description = "Animated player character controller demonstrating state machine actions and sprite animations.",
                assetFolder = "Paul"
            ),
            UnityTemplateItem(
                id = "BasicCore",
                name = "2D Starter Core",
                category = "Starter",
                description = "Clean empty scene with 2D camera viewport, physics world initialization, and touch listeners.",
                assetFolder = "example1"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        BasicText(
            text = "Templates & Examples",
            style = UnityTypography.Title.copy(
                color = UnityColors.TextWhite,
                fontSize = 18.sp
            )
        )
        BasicText(
            text = "Jumpstart your game with ready-to-run starter projects and engine features",
            style = UnityTypography.BodySmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(templates, key = { it.id }) { tpl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(UnityColors.Surface)
                        .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(UnityColors.SurfaceVariant)
                                        .border(1.dp, UnityColors.BorderLight, RoundedCornerShape(3.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = "2D",
                                        style = TextStyle(color = UnityColors.AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicText(
                                    text = tpl.name,
                                    style = UnityTypography.Header.copy(
                                        color = UnityColors.TextWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            UnityBadge(text = tpl.category)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        BasicText(
                            text = tpl.description,
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            UnityButton(
                                text = "+ Use Template",
                                onClick = { onSelectTemplate(tpl) },
                                variant = UnityButtonVariant.Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Community Links View tab.
 */
@Composable
fun CommunityView(context: Context) {
    val links = remember {
        listOf(
            Triple("Facebook Group", "Join the Star2D developers community on Facebook", "https://facebook.com/groups/995354408402399/"),
            Triple("WhatsApp Group", "Live chat with developers and get quick assistance", "https://chat.whatsapp.com/Bxu3kM0b1oE9UL0iZhti4E?mode=ems_copy_c"),
            Triple("Telegram Channel", "News, releases, asset packs, and updates", "https://t.me/+pE4VREnP04s5NDNk"),
            Triple("Discord Community", "Discuss game dev, share scripts, and collaborate", "https://discord.gg/9gxPUTEP"),
            Triple("GitHub Repository", "Star2D Engine open source code and issue tracking", "https://github.com/star4droid/Star2D/"),
            Triple("YouTube Channel", "Game engine tutorials, guides, and feature showcases", "https://youtube.com/@star4droid?si=wNDqN_fbRIzf3KSQ")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BasicText(
            text = "Community & Support",
            style = UnityTypography.Title.copy(
                color = UnityColors.TextWhite,
                fontSize = 18.sp
            )
        )
        BasicText(
            text = "Connect with the Star2D developer community across the globe",
            style = UnityTypography.BodySmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Donate Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(UnityColors.Header)
                .border(1.dp, UnityColors.AccentBlue, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = "Support Engine Development",
                        style = UnityTypography.Header.copy(
                            color = UnityColors.TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = "Star2D is developed with passion. Help keep updates and features coming!",
                        style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                UnityButton(
                    text = "Donate",
                    onClick = {
                        try {
                            val intent = Intent(context, DonateActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            openExternalUrl(context, "https://github.com/star4droid/Star2D/")
                        }
                    },
                    variant = UnityButtonVariant.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Community Links Cards
        for ((title, desc, url) in links) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(UnityColors.Surface)
                    .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = title,
                            style = UnityTypography.Body.copy(
                                color = UnityColors.TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        BasicText(
                            text = desc,
                            style = UnityTypography.BodySmall.copy(color = UnityColors.TextMuted)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    UnityButton(
                        text = "Join",
                        onClick = { openExternalUrl(context, url) },
                        variant = UnityButtonVariant.Normal
                    )
                }
            }
        }
    }
}

/**
 * App Preferences & Settings View tab.
 */
@Composable
fun SettingsView(
    context: Context,
    onStatus: (String) -> Unit
) {
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }

    var codeCompletion by remember { mutableStateOf(prefs.getBoolean("Auto Completion", true)) }
    var autoSave by remember { mutableStateOf(prefs.getBoolean("AutoSave", true)) }
    var saveUndoRedo by remember { mutableStateOf(prefs.getBoolean("SaveUndoRedo", true)) }

    val languages = listOf("English", "العربيه", "Français", "Português", "Русский", "Español")
    val langCodes = listOf("en", "ar", "fr", "br", "ru", "es")
    val currentLangCode = prefs.getString("lang", "en") ?: "en"
    val initialLangIndex = langCodes.indexOf(currentLangCode).coerceAtLeast(0)
    var selectedLangIndex by remember { mutableIntStateOf(initialLangIndex) }

    val compilers = listOf("javac", "ecj")
    val currentCompiler = prefs.getString("compiler", "javac") ?: "javac"
    val initialCompIndex = compilers.indexOf(currentCompiler).coerceAtLeast(0)
    var selectedCompilerIndex by remember { mutableIntStateOf(initialCompIndex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BasicText(
            text = "Engine Settings",
            style = UnityTypography.Title.copy(
                color = UnityColors.TextWhite,
                fontSize = 18.sp
            )
        )
        BasicText(
            text = "Configure editor behaviors, code generation, and preferences",
            style = UnityTypography.BodySmall
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(UnityColors.Surface)
                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = "EDITOR & COMPILER",
                    style = UnityTypography.BodySmall.copy(
                        color = UnityColors.TextMuted,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Language
                UnityDropdown(
                    label = "Interface Language",
                    options = languages,
                    selectedIndex = selectedLangIndex,
                    onSelectIndex = { idx ->
                        selectedLangIndex = idx
                        val code = langCodes.getOrElse(idx) { "en" }
                        prefs.edit().putString("lang", code).apply()
                        onStatus("Language set to ${languages[idx]}")
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Compiler
                UnityDropdown(
                    label = "Java Compiler Backend",
                    options = compilers,
                    selectedIndex = selectedCompilerIndex,
                    onSelectIndex = { idx ->
                        selectedCompilerIndex = idx
                        prefs.edit().putString("compiler", compilers[idx]).apply()
                        onStatus("Compiler set to ${compilers[idx]}")
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Auto completion
                UnityToggleSwitch(
                    checked = codeCompletion,
                    onCheckedChange = { checked ->
                        codeCompletion = checked
                        prefs.edit().putBoolean("Auto Completion", checked).apply()
                        onStatus("Auto-completion ${if (checked) "enabled" else "disabled"}")
                    },
                    label = "Script Code Completion"
                )

                // Auto save
                UnityToggleSwitch(
                    checked = autoSave,
                    onCheckedChange = { checked ->
                        autoSave = checked
                        prefs.edit().putBoolean("AutoSave", checked).apply()
                        onStatus("Auto-save ${if (checked) "enabled" else "disabled"}")
                    },
                    label = "Automatic Scene Saving"
                )

                // Save Undo/Redo
                UnityToggleSwitch(
                    checked = saveUndoRedo,
                    onCheckedChange = { checked ->
                        saveUndoRedo = checked
                        prefs.edit().putBoolean("SaveUndoRedo", checked).apply()
                        onStatus("Undo history saving ${if (checked) "enabled" else "disabled"}")
                    },
                    label = "Persist Undo/Redo History"
                )
            }
        }
    }
}

/**
 * Mobile-responsive Project Table Row.
 */
@Composable
fun ProjectTableRow(
    project: UnityProjectItem,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHovered) UnityColors.CardHover else UnityColors.DarkBackground)
            .border(0.5.dp, UnityColors.Separator, RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Project 2D Icon
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(UnityColors.SurfaceVariant)
                .border(1.dp, UnityColors.BorderLight, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "2D",
                style = TextStyle(
                    color = UnityColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Project Info (Name & Date)
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = project.name,
                style = UnityTypography.Body.copy(
                    color = UnityColors.TextWhite,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = project.formattedDate,
                style = UnityTypography.BodySmall.copy(color = UnityColors.TextMuted, fontSize = 10.sp),
                maxLines = 1
            )
        }

        // Action Menu Button
        UnityIconButton(
            iconText = "⋮",
            onClick = onActionClick,
            textColor = UnityColors.TextSecondary,
            size = 28.dp
        )
    }
}

/**
 * Responsive Unity "New Project" dialog content:
 * Template selection & Project Name settings, scrollable on phones.
 */
@Composable
fun NewProjectDialogContent(
    context: Context,
    onCancel: () -> Unit,
    onCreate: (name: String, path: String) -> Unit
) {
    var projectName by remember { mutableStateOf("New 2D Project") }
    var selectedTemplate by remember { mutableStateOf("2D Core") }
    val defaultDir = remember { File(context.filesDir, "projects").absolutePath }

    val isValidName = projectName.isNotBlank() && !projectName.contains("/") && !projectName.contains("\\")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        BasicText(
            text = "PROJECT TEMPLATE",
            style = UnityTypography.BodySmall.copy(
                color = UnityColors.TextMuted,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val templates = listOf(
            "2D Core" to "Empty scene with 2D physics & camera",
            "2D Platformer" to "Starter kit with player & platforms"
        )

        for ((tpl, desc) in templates) {
            val isSelected = selectedTemplate == tpl
            val tplBg = if (isSelected) UnityColors.SurfaceVariant else Color.Transparent
            val tplBorder = if (isSelected) UnityColors.AccentBlue else Color.Transparent

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tplBg)
                    .border(1.dp, tplBorder, RoundedCornerShape(3.dp))
                    .clickable { selectedTemplate = tpl }
                    .padding(8.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UnityBadge(text = "2D")
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicText(
                            text = tpl,
                            style = UnityTypography.Body.copy(
                                color = if (isSelected) UnityColors.TextWhite else UnityColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = desc,
                        style = UnityTypography.BodySmall.copy(
                            color = UnityColors.TextMuted,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        BasicText(
            text = "Project Name",
            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        UnityTextField(
            value = projectName,
            onValueChange = { projectName = it },
            placeholder = "Enter project name..."
        )

        Spacer(modifier = Modifier.height(10.dp))

        BasicText(
            text = "Location",
            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(UnityColors.DarkBackground)
                .border(1.dp, UnityColors.Border, RoundedCornerShape(3.dp))
                .padding(8.dp)
        ) {
            BasicText(
                text = "$defaultDir/$projectName",
                style = UnityTypography.Code.copy(color = UnityColors.TextSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnityButton(
                text = "Cancel",
                onClick = onCancel,
                variant = UnityButtonVariant.Normal
            )

            Spacer(modifier = Modifier.width(8.dp))

            UnityButton(
                text = "Create project",
                enabled = isValidName,
                onClick = {
                    val cleanName = projectName.trim()
                    val targetDir = File(defaultDir, cleanName)
                    targetDir.mkdirs()
                    // Create default engine subfolders
                    File(targetDir, "sounds").mkdirs()
                    File(targetDir, "anims").mkdirs()
                    File(targetDir, "images").mkdirs()
                    File(targetDir, "files").mkdirs()
                    File(targetDir, "scenes").mkdirs()
                    File(targetDir, "joints").mkdirs()
                    File(targetDir, "icon").mkdirs()

                    if (selectedTemplate == "2D Platformer") {
                        copyAssetFolder(context, "files/examples/platformer", targetDir)
                    } else {
                        val scene1 = File(targetDir, "scene1.scene")
                        if (!scene1.exists()) {
                            FileUtil.writeFile(scene1.absolutePath, "{}")
                        }
                    }
                    onCreate(cleanName, targetDir.absolutePath)
                },
                variant = UnityButtonVariant.Primary
            )
        }
    }
}
