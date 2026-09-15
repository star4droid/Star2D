package com.star4droid.star2d.unityui

import android.view.View
import androidx.compose.ui.platform.ComposeView
import com.star4droid.star2d.EditorActivity
import com.star4droid.star2d.Helpers.FileUtil
import java.io.File

object UnityHubBridge {

    @JvmStatic
    fun setupHub(
        activity: EditorActivity,
        composeView: ComposeView,
        onOpenProject: (name: String, path: String) -> Unit,
        onImportProject: () -> Unit,
        onExportProject: (path: String) -> Unit
    ) {
        composeView.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            UnityProjectHub(
                onOpenProject = { name, path ->
                    composeView.visibility = View.GONE
                    onOpenProject(name, path)
                },
                onImportProject = onImportProject,
                onExportProject = onExportProject,
                onDeleteProject = { path ->
                    try {
                        FileUtil.deleteFile(path)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
        }
        composeView.visibility = View.VISIBLE
    }

    @JvmStatic
    fun showHub(composeView: ComposeView) {
        composeView.visibility = View.VISIBLE
    }

    @JvmStatic
    fun hideHub(composeView: ComposeView) {
        composeView.visibility = View.GONE
    }

    @JvmStatic
    fun setupEditorOverlay(
        activity: EditorActivity,
        composeView: ComposeView,
        editorView: com.star4droid.star2d.Items.Editor?,
        onExitToHub: () -> Unit
    ) {
        composeView.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            UnityEditorOverlay(
                editorView = editorView,
                onExitToHub = onExitToHub
            )
        }
    }
}
