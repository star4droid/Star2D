package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

/** Handles saving and loading of the node editor state (Gson version, compatible with old JSON) */
public class NodeSerializer {

    private final NodeEditor editor;
    private final UiStage uiStage;
    private final Map<VisualNode, String> nodeIdMap = new HashMap<>();
    private final Map<String, VisualNode> idNodeMap = new HashMap<>();
    private int nextId = 0;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public NodeSerializer(NodeEditor editor, UiStage uiStage) {
        this.editor = editor;
        this.uiStage = uiStage;
    }

    /** Save all nodes to a JSON file */
    public void saveToFile(String filename) {
        try {
            JsonArray nodesArray = new JsonArray();
            nodeIdMap.clear();
            idNodeMap.clear();
            nextId = 0;

            // First pass: assign IDs
            for (Actor actor : editor.getRoot().getChildren()) {
                if (actor instanceof VisualNode) {
                    VisualNode node = (VisualNode) actor;
                    String id = String.valueOf(nextId++);
                    nodeIdMap.put(node, id);
                    idNodeMap.put(id, node);
                }
            }

            // Second pass: serialize
            for (Actor actor : editor.getRoot().getChildren()) {
                if (actor instanceof VisualNode) {
                    VisualNode node = (VisualNode) actor;
                    JsonObject nodeJson = serializeNode(node);
                    nodesArray.add(nodeJson);
                }
            }
            
            com.badlogic.gdx.Gdx.files.absolute(filename).writeString(gson.toJson(nodesArray), false);
            /*try (java.io.FileWriter writer = new java.io.FileWriter(
                    "/storage/emulated/0/Android/data/com.star4droid.starvalley/files/" + filename)) {
                gson.toJson(nodesArray, writer);
            }*/

            System.out.println("Saved " + nodesArray.size() + " nodes to " + filename);

        } catch (Exception e) {
            System.err.println("Error saving nodes: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void displayFirst(){
        editor.getRoot().clearChildren();
        VisualNode first = new VisualNode("First \u003e", editor);
        first.setCode("%1$s");
        first.setDeletable(false);
        first.setPosition(50, 450);
        first.setColor(Color.RED);
        editor.addNode(first);
        editor.getCamera().position.set(0,0,0);
        nodeIdMap.clear();
        idNodeMap.clear();
        idNodeMap.put("0", first);
        nodeIdMap.put(first, "0");
    }

    /** Load nodes from a JSON file */
    public void loadFromFile(String filename) {
        try {
            java.io.File file = new java.io.File(filename);
            if (!file.exists()) {
                System.err.println("File not found: " + filename);
                displayFirst();
                return;
            }

            JsonArray nodesArray;
            try (java.io.FileReader reader = new java.io.FileReader(file)) {
                nodesArray = JsonParser.parseReader(reader).getAsJsonArray();
            }
            if(nodesArray.size() == 0){
                displayFirst();
                return;
            }
            editor.getRoot().clearChildren();
            nodeIdMap.clear();
            idNodeMap.clear();
            
            // First pass: create nodes
            for (JsonElement elem : nodesArray) {
                JsonObject nodeJson = elem.getAsJsonObject();
                VisualNode node = deserializeNode(nodeJson);
                editor.addNode(node);

                String id = nodeJson.get("id").getAsString();
                idNodeMap.put(id, node);
                nodeIdMap.put(node, id);
            }

            // Second pass: restore connections
            for (JsonElement elem : nodesArray) {
                JsonObject nodeJson = elem.getAsJsonObject();
                String id = nodeJson.get("id").getAsString();
                VisualNode node = idNodeMap.get(id);
                restoreConnections(node, nodeJson);
            }

            System.out.println("Loaded " + nodesArray.size() + " nodes from " + filename);

        } catch (Exception e) {
            System.err.println("Error loading nodes: " + e.getMessage());
            e.printStackTrace();
            displayFirst();
        }
    }

    /** Serialize a single node */
    private JsonObject serializeNode(VisualNode node) {
        JsonObject json = new JsonObject();

        String nodeId = nodeIdMap.get(node);
        json.addProperty("id", nodeId);
        json.addProperty("title", node.getTitleLabel().getText().toString());
        json.addProperty("x", String.valueOf(node.getX()));
        json.addProperty("y", String.valueOf(node.getY()));

        VisualNode trueNode = node.getTrueNode();
        VisualNode falseNode = node.getFalseNode();
        VisualNode nextNode = node.getNextNode();

        boolean isBooleanNode = node.isBooleanNode();
        json.addProperty("else", String.valueOf(isBooleanNode));

        if (isBooleanNode) {
            json.addProperty("next", trueNode != null ? nodeIdMap.get(trueNode) : "null");
            json.addProperty("next_id", nextNode != null ? nodeIdMap.get(nextNode) : "null");
            json.addProperty("else_id", falseNode != null ? nodeIdMap.get(falseNode) : "");
        } else {
            json.addProperty("next", nextNode != null ? nodeIdMap.get(nextNode) : "null");
            json.addProperty("next_id", "null");
            json.addProperty("else_id", "");
        }

        // Fields (stringified JSON for backward compatibility)
        JsonArray fieldsArray = new JsonArray();
        for (Actor actor : node.getFieldsTable().getChildren()) {
            if (actor instanceof VisualNode.NodeField) {
                VisualNode.NodeField field = (VisualNode.NodeField) actor;
                JsonObject fieldJson = new JsonObject();
                fieldJson.addProperty("name", field.getFieldName());
                fieldJson.addProperty("value", field.value);
                fieldsArray.add(fieldJson);
            }
        }
        json.addProperty("nf", gson.toJson(fieldsArray));

        // Save code and close
        json.addProperty("code", node.getCode() != null ? node.getCode() : generateNodeCode(node));
        json.addProperty("color", node.getColor().toIntBits());
        json.addProperty("close", node.isDeletable() ? "true" : "false");

        return json;
    }

    /** Deserialize a single node */
    private VisualNode deserializeNode(JsonObject json) {
        String title = json.get("title").getAsString();
        float x = Float.parseFloat(json.get("x").getAsString());
        float y = Float.parseFloat(json.get("y").getAsString());

        VisualNode node = new VisualNode(title, editor);
        node.setPosition(x, y);

        // Restore code
        if (json.has("code")) {
            node.setCode(json.get("code").getAsString());
        } else throw new RuntimeException("Code Not Found!!\n"+json);

        // Restore close flag
        if (json.has("close")) {
            node.setDeletable(json.get("close").getAsString().equals("true"));
        }

        boolean hasElse = json.get("else").getAsString().equals("true");
        node.setIsBooleanNode(hasElse);

        // Fields (support both array and string)
        try {
            if (json.has("nf")) {
                JsonElement nfElem = json.get("nf");
                JsonArray fieldsArray;
                if (nfElem.isJsonArray()) {
                    fieldsArray = nfElem.getAsJsonArray();
                } else {
                    // old format: nf is a string containing JSON
                    fieldsArray = JsonParser.parseString(nfElem.getAsString()).getAsJsonArray();
                }
                for (JsonElement elem : fieldsArray) {
                    JsonObject fieldJson = elem.getAsJsonObject();
                    String name = fieldJson.get("name").getAsString();
                    String value = fieldJson.get("value").getAsString();
                    node.add(new VisualNode.NodeField(name, value, uiStage));
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing node fields: " + e.getMessage());
        }

        /*try {
            int colorInt = json.get("color").getAsInt();
            Color color = new Color();
            Color.abgr8888ToColor(color, colorInt); // because toIntBits stores ABGR
            node.setColor(color);
        } catch (Exception ignored) {}*/

        return node;
    }

    /** Restore connections */
    private void restoreConnections(VisualNode node, JsonObject json) {
        boolean isBooleanNode = json.get("else").getAsString().equals("true");

        if (isBooleanNode) {
            String trueId = json.get("next").getAsString();
            if (!trueId.equals("null") && !trueId.isEmpty() && idNodeMap.containsKey(trueId)) {
                node.setTrueNode(idNodeMap.get(trueId));
            }

            String falseId = json.has("else_id") ? json.get("else_id").getAsString() : "";
            if (!falseId.isEmpty() && !falseId.equals("null") && idNodeMap.containsKey(falseId)) {
                node.setFalseNode(idNodeMap.get(falseId));
            }

            String nextId = json.has("next_id") ? json.get("next_id").getAsString() : "";
            if (!nextId.isEmpty() && !nextId.equals("null") && idNodeMap.containsKey(nextId)) {
                node.setNextNode(idNodeMap.get(nextId));
            }

        } else {
            String nextId = json.get("next").getAsString();
            if (!nextId.equals("null") && !nextId.isEmpty() && idNodeMap.containsKey(nextId)) {
                node.setNextNode(idNodeMap.get(nextId));
            }
        }
    }

    /** Generate code placeholder if none is set */
    private String generateNodeCode(VisualNode node) {
        String title = node.getTitleLabel().getText().toString();
        int fieldCount = 0;
        for (Actor actor : node.getFieldsTable().getChildren()) {
            if (actor instanceof VisualNode.NodeField) fieldCount++;
        }

        StringBuilder code = new StringBuilder();
        for (int i = 1; i <= fieldCount; i++) {
            code.append("%").append(i).append("$s");
            if (i < fieldCount) code.append(", ");
        }

        if (node.getTrueNode() != null || node.getFalseNode() != null) {
            return "if(" + code + "){\n%" + (fieldCount + 1) + "$s\n} else {\n%" +
                    (fieldCount + 2) + "$s\n}\n%" + (fieldCount + 3) + "$s";
        } else {
            return title + "(" + code + ");\n%" + (fieldCount + 1) + "$s";
        }
    }
// --- Code Exporting ---

    public String exportCode() {
    StringBuilder code = new StringBuilder();

    // Find entry node (using helper)
    VisualNode entry = findEntryNode();
    if (entry != null) {
        generateCodeRecursive(entry, code, 0, new HashSet<>());
    }

    return code.toString();
}

    private void generateCodeRecursive(
            VisualNode node, StringBuilder code, int indent, Set<VisualNode> visited) {
    
        if (node == null || visited.contains(node)) return;
        visited.add(node);
    
        String indentStr = "    ".repeat(indent);
    
        // Get node code template
        String template = node.getCode();
        if (template == null || template.isEmpty()) {
            //template = node.getTitleLabel().getText().toString() + "(%1$s);\n%2$s"; // fallback
            throw new RuntimeException("no code found!!");
        }
    
        // Collect field values
        List<String> args = new ArrayList<>();
        for (Actor actor : node.getFieldsTable().getChildren()) {
            if (actor instanceof VisualNode.NodeField) {
                args.add(((VisualNode.NodeField) actor).value);
            }
        }
    
        // Recursive branches
        String trueCode = "";
        if (node.getTrueNode() != null) {
            StringBuilder trueBuilder = new StringBuilder();
            generateCodeRecursive(node.getTrueNode(), trueBuilder, indent + 1, visited);
            trueCode = trueBuilder.toString();
        }
    
        String falseCode = "";
        if (node.getFalseNode() != null) {
            StringBuilder falseBuilder = new StringBuilder();
            generateCodeRecursive(node.getFalseNode(), falseBuilder, indent + 1, visited);
            falseCode = falseBuilder.toString();
        }
    
        String nextCode = "";
        if (node.getNextNode() != null) {
            StringBuilder nextBuilder = new StringBuilder();
            generateCodeRecursive(node.getNextNode(), nextBuilder, indent, visited);
            nextCode = nextBuilder.toString();
        }
    
        // Add branch codes as arguments (order matters for %3$s, %4$s, %5$s etc.)
        if(node.isBooleanNode()){
            args.add(trueCode);
            args.add(falseCode);
        }
        args.add(nextCode);
    
        // Format final code
        String formatted = "";
        try {
            formatted = String.format(template, args.toArray());
        } catch(Exception ex){}
        code.append(indentStr).append(formatted.replace("\n", "\n" + indentStr));
    }

    /** Find the entry node (node with no incoming connections) */
    private VisualNode findEntryNode() {
        Map<VisualNode, Boolean> hasIncoming = new HashMap<>();

        // Mark all nodes as having no incoming connections initially
        for (Actor actor : editor.getRoot().getChildren()) {
            if (actor instanceof VisualNode) {
                VisualNode nd = (VisualNode) actor;
                if(!nd.isDeletable()){
                    hasIncoming.clear();
                    return nd;
                }
                hasIncoming.put(nd, false);
            }
        }

        // Mark nodes that have incoming connections
        for (Actor actor : editor.getRoot().getChildren()) {
            if (actor instanceof VisualNode) {
                VisualNode node = (VisualNode) actor;
                if (node.getNextNode() != null) {
                    hasIncoming.put(node.getNextNode(), true);
                }
                if (node.getTrueNode() != null) {
                    hasIncoming.put(node.getTrueNode(), true);
                }
                if (node.getFalseNode() != null) {
                    hasIncoming.put(node.getFalseNode(), true);
                }
            }
        }

        // Find first node without incoming connections
        for (Map.Entry<VisualNode, Boolean> entry : hasIncoming.entrySet()) {
            if (!entry.getValue()) {
                return entry.getKey();
            }
        }

        return null;
    }

    /** Recursively generate code from nodes */
    
}