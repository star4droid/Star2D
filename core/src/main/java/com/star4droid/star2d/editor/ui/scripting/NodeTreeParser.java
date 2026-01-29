package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeTreeParser extends ApplicationAdapter {
    private Stage stage;
    public VisTable treeTable; // Replacing VisTree
    private Map<String, VisTable> treeCategories; // Each category has a nested table
    private UiStage uiStage;
    private NodeEditor editorStage;
   private StringBuilder code ;
    public NodeTreeParser(UiStage uiStage, NodeEditor editorStage) {
        this.editorStage = editorStage;
        this.uiStage = uiStage;
    }

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        treeCategories = new HashMap<>();
        parseNodesFile();
        createUI();
    }

    private void parseNodesFile() {
        FileHandle file = Gdx.files.internal("java/nodes.java");
        String fileContent = file.readString();
        String[] lines = fileContent.split("\n");

        String currentCategory = null;
         code = new StringBuilder();
        Color currentColor = null;
        boolean inCodeSection = false;
        List<String> fields;
        String nodeDetails = null;

        for (String line : lines) {
            if (line.trim().equals("split")) {
                if (currentColor != null && nodeDetails != null) {
                    String[] split = nodeDetails.split(" ");
                    String nodeName = split[0];
                    fields = new ArrayList<>();
                    for (int i = 1; i < split.length; i++) {
                        fields.add(split[i]);
                    }
                    addNodeToTable(currentCategory, nodeName, code.toString(), currentColor, fields);
                }
                inCodeSection = false;
                code = new StringBuilder();
                currentColor = null;
                nodeDetails = null;
                continue;
            }
            if (inCodeSection) {
                code.append(line).append("\n");
                continue;
            }
            if (line.startsWith("--")) {
                currentCategory = line.replace("--", "");
                continue;
            }
            if (line.startsWith("-color:")) {
                currentColor = parseColor(line);
                continue;
            }
            if (line.startsWith("<<=>>")) {
                inCodeSection = true;
                continue;
            }
            nodeDetails = line;
        }
    }

    private Color parseColor(String colorLine) {
        String hexColor = colorLine.substring(colorLine.indexOf("#") + 1, colorLine.indexOf("•"));
        return Color.valueOf(hexColor);
    }

    private void addNodeToTable(String category, String nodeName, String code, Color nodeColor, List<String> fields) {
        VisTable categoryTable = treeCategories.get(category);
        VisTable nodesTable;

        if (categoryTable == null) {
            categoryTable = new VisTable();
            categoryTable.top().left();
            categoryTable.pad(10); // more padding

            // Create collapsible nodes table
            nodesTable = new VisTable();
            nodesTable.top().left();
            nodesTable.padLeft(30);
            nodesTable.setVisible(true);

            // Button to toggle collapse/expand
            VisTextButton toggleButton = new VisTextButton(category + " [+]");
            categoryTable.add(toggleButton).left().padBottom(12).height(55).row();

            toggleButton.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    boolean visible = nodesTable.isVisible();
                    nodesTable.setVisible(!visible);
                    toggleButton.setText(category + (visible ? " [+]" : " [-]"));
                }
            });

            categoryTable.add(nodesTable).expandX().fillX().row();
            treeCategories.put(category, categoryTable);
            if (treeTable == null){
                treeTable = new VisTable();
                treeTable.add().height(200).row();
            }
            treeTable.add(categoryTable).row();
        } else {
            nodesTable = (VisTable) categoryTable.getChildren().get(1); // 2nd child is nodesTable
        }

        String nodeN = nodeName.contains("__star__if__") ? nodeName.replace("__star__if__", "") : nodeName;
        VisLabel nodeLabel = new VisLabel(nodeN.split(" ")[0]);
        //nodeLabel.setColor(nodeColor != null ? nodeColor : Color.WHITE);
        nodeLabel.setColor(Color.WHITE);

        nodeLabel.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                createVisualNode(nodeName, code, fields);
            }
        });

        nodesTable.add(nodeLabel)
                  .left()
                  .padTop(8).padBottom(8) // more vertical spacing
                  .minHeight(45)          // larger row height
                  .row();
    }

    private void createVisualNode(String nodeName, String code, List<String> fields) {
        VisualNode node;
        if (nodeName.contains("__star__if__")) {
            node = new VisualNode(nodeName.replace("__star__if__", ""), editorStage);
            node.setIsBooleanNode(true);
        } else {
            node = new VisualNode(nodeName, editorStage);
        }

        for (String field : fields) {
            node.add(new VisualNode.NodeField(field, "", uiStage));
        }
        
        editorStage.addActor(node);
        node.setCode(code);
        node.setPosition(editorStage.getCamera().position.x - node.getWidth() * 0.5f, editorStage.getCamera().position.y - node.getHeight() * 0.5f);
    }

    private void createUI() {
        VisTable mainTable = new VisTable();
        mainTable.setFillParent(true);
        if (treeTable == null){
            treeTable = new VisTable();
            treeTable.add().height(200).row();
        }
        treeTable.top().left();
        treeTable.pad(15);

        // 🔧 Optional: scale everything in one go
        // treeTable.setTransform(true);
        // treeTable.setScale(1.5f);

        ScrollPane scrollPane = new ScrollPane(treeTable);
        scrollPane.setFadeScrollBars(false);

        VisLabel titleLabel = new VisLabel("Node Tree Parser (VisUI - Table Version)");
        titleLabel.setColor(Color.CYAN);
        titleLabel.setFontScale(1.5f);

        mainTable.add(titleLabel).pad(15).row();
        mainTable.add(scrollPane).expand().fill().pad(15);

        stage.addActor(mainTable);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act();
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}