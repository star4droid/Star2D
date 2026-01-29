package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.DragListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class UiStage extends com.badlogic.gdx.scenes.scene2d.Stage {

    private final NodeEditor editor;
    private final NodeSerializer serializer;
    private final float MOVEMENT_SPEED = 0.75f;
    VisTable sidePanel;
    private boolean visible = false;
    private Vector2 dragStart = new Vector2();
    private String savePath = "", hints = "";
    private Vector2 camStart = new Vector2();
    final float panelWidth = 300;

    private float prevDistance = -1f;

    public UiStage(final NodeEditor editor) {
        super(new ScreenViewport());
        this.editor = editor;
        this.serializer = new NodeSerializer(editor, this);

        VisTable root = new VisTable();
        root.setFillParent(true);
        addActor(root);

        /* ------------ invisible camera pad (fills all space) ------------ */
        VisTextButton cameraPad = new VisTextButton("");
        cameraPad.setColor(0, 0, 0, 0);
        cameraPad.clearListeners();

        /* ------------ top buttons ------------ */
        VisTable top = new VisTable();
        top.setBackground(VisUI.getSkin().getDrawable("separator"));
        root.add(top).growX().pad(10).row();
        root.add(cameraPad).expand().fill().row();

        VisTextButton zoomIn = new VisTextButton("+");
        VisTextButton zoomOut = new VisTextButton("-");
        VisTextButton save = new VisTextButton("Save");
        VisTextButton load = new VisTextButton("Load");
        VisTextButton export = new VisTextButton("Code");
        VisTextButton exit = new VisTextButton("Cancel");
        
        // Add toggle button to top table
        VisTextButton toggleSidePanel = new VisTextButton("Nodes");
        top.add(toggleSidePanel).height(55).padLeft(20);
        
        //top.add(zoomIn).size(55, 55).padRight(8);
        //top.add(zoomOut).size(55, 55).padRight(8);
        top.add().growX();
        top.add(save).size(120, 45).padLeft(20);
        //top.add(load).size(120, 45).padLeft(8);
        top.add(export).size(120, 45).padLeft(8);
        top.add(exit).size(120, 45).padLeft(8);

        // Continuous zoom while pressed
        zoomIn.addListener(
                new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                    @Override
                    public boolean touchDown(
                            InputEvent event, float x, float y, int pointer, int button) {
                        editor.zoomSpeed = -0.35f;
                        return true;
                    }

                    @Override
                    public void touchUp(
                            InputEvent event, float x, float y, int pointer, int button) {
                        editor.zoomSpeed = 0;
                    }
                });
        zoomOut.addListener(
                new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                    @Override
                    public boolean touchDown(
                            InputEvent event, float x, float y, int pointer, int button) {
                        editor.zoomSpeed = 0.35f;
                        return true;
                    }

                    @Override
                    public void touchUp(
                            InputEvent event, float x, float y, int pointer, int button) {
                        editor.zoomSpeed = 0;
                    }
                });

        save.addListener(
                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                    @Override
                    public void changed(
                            ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        serializer.saveToFile(savePath);
                        String code = serializer.exportCode();
                        String codePath = savePath.substring(0, savePath.lastIndexOf("."))+".java";
                        Gdx.files.absolute(codePath).writeString(code, false);
                        showDialog("Save", "Nodes saved successfully!");
                    }
                });
        
        load.addListener(
                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                    @Override
                    public void changed(
                            ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        serializer.loadFromFile("nodes_save.json");
                        showDialog("Load", "Nodes loaded successfully!");
                    }
                });
        
        export.addListener(
                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                    @Override
                    public void changed(
                            ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        String code = serializer.exportCode();
                        showCodeDialog(code);
                    }
                });
        
        exit.addListener(
                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                    @Override
                    public void changed(
                            ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        new ConfirmDialog(getTrans("exit"),getTrans("areYouSure"),ok->{
						if(ok)
							com.star4droid.star2d.editor.TestApp.getCurrentApp().showVisualScripting(false);
					}).show(UiStage.this);
                    }
                });

        // Camera drag
        cameraPad.addListener(
                new DragListener() {
                    boolean zoomHappen = false;
                    @Override
                    public boolean touchDown(
                            InputEvent event, float x, float y, int pointer, int button) {
                        zoomHappen = false;
                        dragStart.set(x, y);
                        Vector2 editorXY =
                                editor.screenToStageCoordinates(
                                        new Vector2(Gdx.input.getX(), Gdx.input.getY()));
                        Object obj = editor.hit(editorXY.x, editorXY.y, true);
                        if (obj != null) return false;
                        camStart.set(editor.getCamera().position.x, editor.getCamera().position.y);
                        return true;
                    }

                    @Override
                    public void touchDragged(InputEvent event, float x, float y, int pointer) {
                        zoomHappen = zoomHappen || Gdx.input.isTouched(1);
                        if(zoomHappen) return;
                        float dx = x - dragStart.x;
                        float dy = y - dragStart.y;
                        dragStart.set(x, y);
                        camStart.set(editor.getCamera().position.x, editor.getCamera().position.y);
                        editor.moveCamera(dx * MOVEMENT_SPEED, -dy * MOVEMENT_SPEED);
                    }
                });

        /* ------------ NodeTreeParser Side Panel ------------ */
        sidePanel = new VisTable();
        sidePanel.setBackground(VisUI.getSkin().getDrawable("window-bg"));
        sidePanel.setSize(panelWidth, Gdx.graphics.getHeight());
        sidePanel.top().left();

        // Initialize NodeTreeParser tree
        NodeTreeParser nodeTree = new NodeTreeParser(this, editor);
        nodeTree.create();

        // Wrap tree in a scroll pane and add to panel
        ScrollPane treeScroll = new ScrollPane(nodeTree.treeTable);
        treeScroll.setFadeScrollBars(false);
        sidePanel.add(treeScroll).expand().fill();

        // Start off-screen on the RIGHT
        sidePanel.setPosition(Gdx.graphics.getWidth(), 0);
        addActor(sidePanel);
        
        // Scale up labels inside tree (optional)
        for (Actor a : nodeTree.treeTable.getChildren()) {
            if (a instanceof Label) {
                Label label = (Label)a;
            }
        }

        // Slide panel in/out on button press
        toggleSidePanel.addListener(
                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                    @Override
                    public void changed(
                            ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        visible = !visible;
                        if (visible) {
                            sidePanel.addAction(
                                    Actions.moveTo(
                                            Gdx.graphics.getWidth() - panelWidth,
                                            0,
                                            0.3f));
                        } else {
                            sidePanel.addAction(
                                    Actions.moveTo(Gdx.graphics.getWidth(), 0, 0.3f));
                        }
                    }
                });
    }
    
    public String getHints(){
        return hints;
    }
    
    public void setHints(String hints){
        this.hints = hints;
    }
    
    public NodeSerializer getNodeSerializer(){
        return serializer;
    }
    
    public void loadFrom(String file){
        serializer.loadFromFile(file);
        visible = false;
        
        if(sidePanel != null){
            sidePanel.setPosition(Gdx.graphics.getWidth(), 0);
            sidePanel.setSize(panelWidth, Gdx.graphics.getHeight());
        }
        this.savePath = file;
    }
    
    private void showDialog(String title, String message) {
        VisDialog dialog = new VisDialog(title);
        dialog.text(message);
        dialog.button("OK");
        dialog.show(this);
    }

    private void showCodeDialog(String code) {
        VisDialog dialog = new VisDialog("Exported Code");
        dialog.setFillParent(false);
        
        com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea = 
            new com.badlogic.gdx.scenes.scene2d.ui.TextArea(code, VisUI.getSkin());
        textArea.setPrefRows(20);
        
        ScrollPane scrollPane = new ScrollPane(textArea);
        scrollPane.setFadeScrollBars(false);
        
        dialog.getContentTable().add(scrollPane).size(600, 400).pad(10);
        
        VisTextButton copyButton = new VisTextButton("Copy to Clipboard");
        copyButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.getClipboard().setContents(code);
                showDialog("Success", "Code copied to clipboard!");
            }
        });
        
        dialog.button(copyButton);
        dialog.button("Close");
        dialog.show(this);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Two-finger pinch zoom
        if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1)) {
            float gx1 = Gdx.input.getX(1), gx0 = Gdx.input.getX(0);
            float gy1 = Gdx.input.getY(1), gy0 = Gdx.input.getY(0);
            OrthographicCamera cam = (OrthographicCamera) editor.getCamera();
            if(gx1 == 0 || gx0 == 0 || gy1 == 0 || gy0 == 0){
                cam.update();
                return;
            }
            float dx = Gdx.input.getX(0) - Gdx.input.getX(1);
            float dy = Gdx.input.getY(0) - Gdx.input.getY(1);
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (prevDistance != -1) {
                cam.zoom -= 0.001f * (distance - prevDistance);
                cam.zoom = MathUtils.clamp(cam.zoom, 0.1f, 3f);

                // Two-finger camera movement
                float dx0 = -Gdx.input.getDeltaX(0) * cam.zoom * 0.5f;
                float dy0 = Gdx.input.getDeltaY(0) * cam.zoom * 0.5f;
                float dx1 = -Gdx.input.getDeltaX(1) * cam.zoom * 0.5f;
                float dy1 = Gdx.input.getDeltaY(1) * cam.zoom * 0.5f;

                if (Math.signum(dx0) == Math.signum(dx1)) cam.position.x += (dx0 + dx1);
                if (Math.signum(dy0) == Math.signum(dy1)) cam.position.y += (dy0 + dy1);

                cam.update();
            }
            prevDistance = distance;
        } else {
            prevDistance = -1;
        }
    }
}