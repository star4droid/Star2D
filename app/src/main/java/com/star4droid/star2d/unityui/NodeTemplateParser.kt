package com.star4droid.star2d.unityui

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.BufferedReader
import java.io.InputStreamReader

data class ScriptNodeTemplate(
    val category: String,
    val name: String,
    val displayName: String,
    val isBoolean: Boolean,
    val codeTemplate: String,
    val color: Color,
    val fields: List<String>
)

object NodeTemplateParser {
    fun parseNodes(context: Context): List<ScriptNodeTemplate> {
        val templates = mutableListOf<ScriptNodeTemplate>()
        try {
            val inputStream = context.assets.open("java/nodes.java")
            val reader = BufferedReader(InputStreamReader(inputStream))
            var currentCategory = "General"
            var currentColor = UnityColors.NodeHeaderAction
            var nodeDetails: String? = null
            var inCodeSection = false
            val codeBuilder = StringBuilder()

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed == "split") {
                    if (nodeDetails != null) {
                        val parts = nodeDetails!!.split(" ")
                        val rawName = parts[0]
                        val isBool = rawName.contains("__star__if__")
                        val cleanName = rawName.replace("__star__if__", "")
                        val fields = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()

                        templates.add(
                            ScriptNodeTemplate(
                                category = currentCategory,
                                name = rawName,
                                displayName = cleanName,
                                isBoolean = isBool,
                                codeTemplate = codeBuilder.toString().trimEnd(),
                                color = currentColor,
                                fields = fields
                            )
                        )
                    }
                    inCodeSection = false
                    codeBuilder.clear()
                    nodeDetails = null
                    return@forEachLine
                }

                if (inCodeSection) {
                    codeBuilder.append(line).append("\n")
                    return@forEachLine
                }

                if (trimmed.startsWith("--")) {
                    currentCategory = trimmed.substring(2).trim().replaceFirstChar { it.uppercase() }
                    return@forEachLine
                }

                if (trimmed.startsWith("-color:")) {
                    val colorHex = trimmed.replace("-color:", "").replace("•", "").trim()
                    try {
                        val parsed = android.graphics.Color.parseColor(colorHex)
                        currentColor = Color(parsed)
                    } catch (e: Exception) {
                        currentColor = UnityColors.NodeHeaderAction
                    }
                    return@forEachLine
                }

                if (trimmed == "<<=>>") {
                    inCodeSection = true
                    return@forEachLine
                }

                if (nodeDetails == null && trimmed.isNotEmpty()) {
                    nodeDetails = trimmed
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return templates
    }
}
