package com.star4droid.star2d.Activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.star4droid.star2d.unityui.VisualScriptingCanvas

/**
 * Full-screen Jetpack Compose Activity hosting the Unity-styled Visual Scripting environment.
 */
class VisualScriptingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val codePath = intent.getStringExtra("codePath") ?: ""
        val hints = intent.getStringExtra("hints") ?: ""

        setContent {
            VisualScriptingCanvas(
                codePath = codePath,
                hints = hints,
                onExit = {
                    finish()
                }
            )
        }
    }
}
