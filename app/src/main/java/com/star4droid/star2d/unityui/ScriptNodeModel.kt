package com.star4droid.star2d.unityui

import com.google.gson.*
import java.io.File

/**
 * Field parameter model for Visual Scripting nodes.
 */
data class ScriptNodeField(
    val name: String,
    var value: String = "",
    var block: ScriptParameterBlock? = null
) {
    fun getDisplayName(): String {
        return if (name.contains("(") && name.contains(")")) {
            name.substring(name.indexOf("(") + 1, name.indexOf(")"))
        } else {
            name
        }
    }

    fun generateCode(): String {
        return if (block != null) {
            block!!.generateCode()
        } else {
            value
        }
    }
}

data class ScriptParameterBlock(
    val template: String,
    val displayTemplate: String,
    val innerFields: MutableList<ScriptNodeField> = mutableListOf()
) {
    fun generateCode(): String {
        val args = innerFields.map { it.generateCode() }
        return try {
            String.format(template, *args.toTypedArray())
        } catch (e: Exception) {
            template
        }
    }
}

/**
 * Node model matching NodeSerializer.java 100%.
 */
data class ScriptNode(
    var id: String,
    var title: String,
    var x: Float,
    var y: Float,
    var isBooleanNode: Boolean = false,
    var nextId: String? = null,
    var nextTrueId: String? = null,
    var nextFalseId: String? = null,
    var nextAfterBranchId: String? = null,
    var code: String = "",
    var color: Int = -16744192,
    var isDeletable: Boolean = true,
    val fields: MutableList<ScriptNodeField> = mutableListOf()
)

/**
 * Serialization and Code Generation Engine for Star2D Visual Scripting.
 * Fully compatible with NodeSerializer.java.
 */
object ScriptNodeEngine {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Load nodes from a .visual JSON file.
     * If file doesn't exist, initializes the root "First >" node.
     */
    fun loadFromFile(filePath: String): MutableList<ScriptNode> {
        val file = File(filePath)
        if (!file.exists()) {
            return mutableListOf(createFirstNode())
        }

        return try {
            val jsonString = file.readText()
            val jsonArray = JsonParser.parseString(jsonString).asJsonArray
            if (jsonArray.size() == 0) {
                return mutableListOf(createFirstNode())
            }

            val nodes = mutableListOf<ScriptNode>()
            for (elem in jsonArray) {
                val obj = elem.asJsonObject
                val id = if (obj.has("id")) obj.get("id").asString else "0"
                val title = if (obj.has("title")) obj.get("title").asString else "Node"
                val x = if (obj.has("x")) obj.get("x").asString.toFloatOrNull() ?: 100f else 100f
                val y = if (obj.has("y")) obj.get("y").asString.toFloatOrNull() ?: 100f else 100f
                val isBoolean = if (obj.has("else")) obj.get("else").asString == "true" else false
                val code = if (obj.has("code")) obj.get("code").asString else "%1\$s"
                val isDeletable = if (obj.has("close")) obj.get("close").asString == "true" else true
                val color = if (obj.has("color")) obj.get("color").asInt else -16744192

                val node = ScriptNode(
                    id = id,
                    title = title,
                    x = x,
                    y = y,
                    isBooleanNode = isBoolean,
                    code = code,
                    color = color,
                    isDeletable = isDeletable
                )

                // Restore connections
                if (isBoolean) {
                    val nextVal = if (obj.has("next")) obj.get("next").asString else "null"
                    node.nextTrueId = if (nextVal != "null" && nextVal.isNotEmpty()) nextVal else null

                    val elseVal = if (obj.has("else_id")) obj.get("else_id").asString else ""
                    node.nextFalseId = if (elseVal != "null" && elseVal.isNotEmpty()) elseVal else null

                    val nextIdVal = if (obj.has("next_id")) obj.get("next_id").asString else ""
                    node.nextAfterBranchId = if (nextIdVal != "null" && nextIdVal.isNotEmpty()) nextIdVal else null
                } else {
                    val nextVal = if (obj.has("next")) obj.get("next").asString else "null"
                    node.nextId = if (nextVal != "null" && nextVal.isNotEmpty()) nextVal else null
                }

                // Restore fields
                if (obj.has("nf")) {
                    try {
                        val nfElem = obj.get("nf")
                        val fieldsArray = if (nfElem.isJsonArray) {
                            nfElem.asJsonArray
                        } else {
                            JsonParser.parseString(nfElem.asString).asJsonArray
                        }
                        for (fElem in fieldsArray) {
                            val fObj = fElem.asJsonObject
                            val name = if (fObj.has("name")) fObj.get("name").asString else ""
                            val value = if (fObj.has("value")) fObj.get("value").asString else ""
                            node.fields.add(ScriptNodeField(name = name, value = value))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                nodes.add(node)
            }
            nodes
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf(createFirstNode())
        }
    }

    fun createFirstNode(): ScriptNode {
        return ScriptNode(
            id = "0",
            title = "First >",
            x = 80f,
            y = 180f,
            isBooleanNode = false,
            code = "%1\$s",
            isDeletable = false,
            color = -65536
        )
    }

    /**
     * Save nodes to .visual JSON file.
     */
    fun saveToFile(filePath: String, nodes: List<ScriptNode>) {
        val jsonArray = JsonArray()
        // Map nodes to sequential IDs
        val idMap = mutableMapOf<String, String>()
        for ((index, node) in nodes.withIndex()) {
            val oldId = node.id
            val newId = index.toString()
            idMap[oldId] = newId
            node.id = newId
        }

        for (node in nodes) {
            val obj = JsonObject()
            obj.addProperty("id", node.id)
            obj.addProperty("title", node.title)
            obj.addProperty("x", node.x.toString())
            obj.addProperty("y", node.y.toString())
            obj.addProperty("else", node.isBooleanNode.toString())

            if (node.isBooleanNode) {
                obj.addProperty("next", node.nextTrueId?.let { idMap[it] ?: it } ?: "null")
                obj.addProperty("next_id", node.nextAfterBranchId?.let { idMap[it] ?: it } ?: "null")
                obj.addProperty("else_id", node.nextFalseId?.let { idMap[it] ?: it } ?: "")
            } else {
                obj.addProperty("next", node.nextId?.let { idMap[it] ?: it } ?: "null")
                obj.addProperty("next_id", "null")
                obj.addProperty("else_id", "")
            }

            val fieldsArray = JsonArray()
            for (f in node.fields) {
                val fObj = JsonObject()
                fObj.addProperty("name", f.name)
                fObj.addProperty("value", f.value)
                fieldsArray.add(fObj)
            }
            obj.addProperty("nf", gson.toJson(fieldsArray))
            obj.addProperty("code", node.code)
            obj.addProperty("color", node.color)
            obj.addProperty("close", if (node.isDeletable) "true" else "false")

            jsonArray.add(obj)
        }

        val targetFile = File(filePath)
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(gson.toJson(jsonArray))
    }

    /**
     * Export Java code using recursive template substitution.
     * Matches NodeSerializer.exportCode() 100%.
     */
    fun exportCode(nodes: List<ScriptNode>): String {
        val entry = findEntryNode(nodes) ?: return ""
        val codeBuilder = StringBuilder()
        val visited = mutableSetOf<String>()
        val nodeMap = nodes.associateBy { it.id }

        generateCodeRecursive(entry, codeBuilder, 0, visited, nodeMap)
        return codeBuilder.toString()
    }

    private fun generateCodeRecursive(
        node: ScriptNode,
        code: StringBuilder,
        indent: Int,
        visited: MutableSet<String>,
        nodeMap: Map<String, ScriptNode>
    ) {
        if (visited.contains(node.id)) return
        visited.add(node.id)

        val indentStr = "    ".repeat(indent)
        val template = node.code
        if (template.isEmpty()) return

        val args = mutableListOf<String>()
        // Collect field values
        for (field in node.fields) {
            args.add(field.generateCode())
        }

        // Recursive branches
        if (node.isBooleanNode) {
            val trueCode = StringBuilder()
            val trueNode = node.nextTrueId?.let { nodeMap[it] }
            if (trueNode != null) {
                generateCodeRecursive(trueNode, trueCode, indent + 1, visited, nodeMap)
            }

            val falseCode = StringBuilder()
            val falseNode = node.nextFalseId?.let { nodeMap[it] }
            if (falseNode != null) {
                generateCodeRecursive(falseNode, falseCode, indent + 1, visited, nodeMap)
            }

            args.add(trueCode.toString())
            args.add(falseCode.toString())
        }

        // Next branch
        val nextCode = StringBuilder()
        val nextTargetId = if (node.isBooleanNode) node.nextAfterBranchId else node.nextId
        val nextNode = nextTargetId?.let { nodeMap[it] }
        if (nextNode != null) {
            generateCodeRecursive(nextNode, nextCode, indent, visited, nodeMap)
        }
        args.add(nextCode.toString())

        val formatted = try {
            String.format(template, *args.toTypedArray())
        } catch (e: Exception) {
            template
        }

        code.append(indentStr).append(formatted.replace("\n", "\n$indentStr"))
    }

    private fun findEntryNode(nodes: List<ScriptNode>): ScriptNode? {
        // Priority to non-deletable root node
        val nonDeletable = nodes.firstOrNull { !it.isDeletable }
        if (nonDeletable != null) return nonDeletable

        // Otherwise node with no incoming connections
        val incoming = mutableSetOf<String>()
        for (node in nodes) {
            node.nextId?.let { incoming.add(it) }
            node.nextTrueId?.let { incoming.add(it) }
            node.nextFalseId?.let { incoming.add(it) }
            node.nextAfterBranchId?.let { incoming.add(it) }
        }

        return nodes.firstOrNull { !incoming.contains(it.id) } ?: nodes.firstOrNull()
    }
}
