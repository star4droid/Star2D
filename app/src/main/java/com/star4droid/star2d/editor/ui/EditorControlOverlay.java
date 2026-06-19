package com.star4droid.star2d.editor.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.CompileThread;
import com.star4droid.star2d.Helpers.FilesChangeDetector;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.sub.JointsList;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.star2d.evo.R;

import java.util.concurrent.Executors;

import static com.star4droid.star2d.editor.utils.Lang.getTrans;

public class EditorControlOverlay extends FrameLayout {

    private TestApp app;
    private ControlLayer controlLayer;
    private Runnable onReady;

    private LinearLayout topContainer, bottomContainer, sceneTabsContainer;
    private FrameLayout floatingPanelsContainer;
    private TextView indexingLabel;

    private ImageButton btnBodies, btnProperties, btnJoints, btnEvents, btnVars, btnAI;
    private ImageButton gridBtn, moveBtn, scaleBtn, rotateBtn, undoBtn, redoBtn, lockBtn;
    private String clickedAction = "grid";

    private boolean initialized = false;
    private boolean appReady = false;

    public EditorControlOverlay(Context context) { super(context); init(); }
    public EditorControlOverlay(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public EditorControlOverlay(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        if (initialized) return;
        initialized = true;
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) { return false; }
    @Override
    public boolean onTouchEvent(MotionEvent ev) { return false; }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initViews();
        delayedSetup();
    }

    private void initViews() {
        if (topContainer != null) return;
        topContainer = findViewById(R.id.top_container);
        bottomContainer = findViewById(R.id.bottom_container);
        sceneTabsContainer = findViewById(R.id.scene_tabs_container);
        floatingPanelsContainer = findViewById(R.id.floating_panels_container);
        indexingLabel = findViewById(R.id.indexing_label);
        btnBodies = findViewById(R.id.btn_bodies);
        btnProperties = findViewById(R.id.btn_properties);
        btnJoints = findViewById(R.id.btn_joints);
        btnEvents = findViewById(R.id.btn_events);
        btnVars = findViewById(R.id.btn_variables);
        btnAI = findViewById(R.id.btn_ai);
    }

    private void delayedSetup() {
        post(() -> {
            app = TestApp.getCurrentApp();
            if (app != null) {
                controlLayer = app.getControlLayer();
                appReady = true;
                buildToolbars();
                setupSideButtons();
                if (onReady != null) onReady.run();
            } else {
                postDelayed(this::delayedSetup, 200);
            }
        });
    }

    public void setOnReady(Runnable r) { this.onReady = r; }

    public void setApp(TestApp testApp) {
        this.app = testApp;
        if (testApp != null) {
            controlLayer = testApp.getControlLayer();
            appReady = true;
            buildToolbars();
            setupSideButtons();
            if (onReady != null) onReady.run();
        }
    }

    private LibgdxEditor getEditor() {
        return app != null ? app.getEditor() : null;
    }

    private Project getProject() {
        LibgdxEditor ed = getEditor();
        return ed != null ? ed.getProject() : null;
    }

    private void buildToolbars() {
        buildTopToolbar();
        buildBottomToolbar();
    }

    private void buildTopToolbar() {
        topContainer.removeAllViews();
        addGap(topContainer);
        addTopBtn(R.drawable.ic_play_arrow_black, v -> runOnGdx(() -> onPlay()));
        addTopBtn(R.drawable.save_icon, v -> runOnGdx(() -> onSave()));
        addTopBtn(R.drawable.back_arrow, v -> {
            if (app == null) return;
            new ConfirmDialog(getTrans("exit"), getTrans("exitConfirm"), ok -> {
                if (ok) runOnGdx(() -> app.closeProject());
            }).show(app.getUiStage());
        });
        addTopBtn(R.drawable.ic_files, v -> {
            if (app == null || app.getFileBrowser() == null) return;
            runOnGdx(() -> app.getFileBrowser().setVisible(!app.getFileBrowser().isVisible()));
        });
        addTopBtn(R.drawable.ic_logs, v -> {
            if (app == null || app.getFileBrowser() == null) return;
            runOnGdx(() -> {
                app.getFileBrowser().setRootDir(Gdx.files.external("logs"));
                app.getFileBrowser().setVisible(true);
                app.getFileBrowser().toFront();
            });
        });
        addTopBtn(R.drawable.ic_color_pal, v -> {
            if (app == null || controlLayer == null) return;
            runOnGdx(() -> {
                if (controlLayer.getColorPicker() != null) controlLayer.getColorPicker().remove();
            });
        });
        addTopBtn(R.drawable.ic_scene, v -> {
            if (controlLayer == null || controlLayer.sceneActionsBtn == null) return;
            runOnGdx(() -> controlLayer.sceneActionsBtn.fire(
                    new com.badlogic.gdx.scenes.scene2d.InputEvent()));
        });
        addTopBtn(R.drawable.screen_rotation, v -> {
            LibgdxEditor ed = getEditor();
            if (ed == null || app == null) return;
            new ConfirmDialog(getTrans("changeOrienation"), getTrans("editorRestartNote"),
                    ok -> { if (ok) runOnGdx(() -> app.setOrienation(!ed.isLandscape())); })
                    .show(app.getUiStage());
        });
    }

    private void buildBottomToolbar() {
        bottomContainer.removeAllViews();
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setBackgroundResource(R.drawable.editor_toolbar_card);
        card.setPadding(8, 4, 8, 4);
        card.setGravity(Gravity.CENTER_VERTICAL);
        bottomContainer.addView(card);

        gridBtn = makeToolBtn(R.drawable.grid_icon);
        gridBtn.setOnClickListener(v -> setToolMode("grid", gridBtn));
        card.addView(gridBtn); addToolGap(card);

        moveBtn = makeToolBtn(R.drawable.move_icon);
        moveBtn.setOnClickListener(v -> setToolMode("move", moveBtn));
        card.addView(moveBtn); addToolGap(card);

        scaleBtn = makeToolBtn(R.drawable.scale_icon);
        scaleBtn.setOnClickListener(v -> setToolMode("scale", scaleBtn));
        card.addView(scaleBtn); addToolGap(card);

        rotateBtn = makeToolBtn(R.drawable.rotate_icon);
        rotateBtn.setOnClickListener(v -> setToolMode("rotate", rotateBtn));
        card.addView(rotateBtn); addToolGap(card);

        lockBtn = makeToolBtn(R.drawable.lock);
        lockBtn.setOnClickListener(v -> onLockToggle());
        card.addView(lockBtn); addToolGap(card);

        ImageButton delBtn = makeToolBtn(R.drawable.del_icon);
        delBtn.setOnClickListener(v -> onDelete());
        card.addView(delBtn); addToolGap(card);

        undoBtn = makeToolBtn(R.drawable.ic_undo);
        undoBtn.setOnClickListener(v -> { LibgdxEditor ed = getEditor(); if (ed != null && ed.canUndo()) runOnGdx(() -> ed.undo()); });
        card.addView(undoBtn); addToolGap(card);

        redoBtn = makeToolBtn(R.drawable.ic_redo);
        redoBtn.setOnClickListener(v -> { LibgdxEditor ed = getEditor(); if (ed != null && ed.canRedo()) runOnGdx(() -> ed.redo()); });
        card.addView(redoBtn); addToolGap(card);

        ImageButton camBtn = makeToolBtn(R.drawable.center_camera);
        camBtn.setOnClickListener(v -> { LibgdxEditor ed = getEditor(); if (ed != null) runOnGdx(() -> ed.centerCamera()); });
        card.addView(camBtn); addToolGap(card);

        ImageButton setBtn = makeToolBtn(R.drawable.properties);
        setBtn.setOnClickListener(v -> {
            if (app == null || app.getProjectsStage() == null) return;
            runOnGdx(() -> app.getProjectsStage().settingsDialog.show(app.getUiStage()));
        });
        card.addView(setBtn);

        setToolMode("grid", gridBtn);
    }

    private ImageButton makeToolBtn(int iconRes) {
        ImageButton btn = new ImageButton(getContext());
        btn.setLayoutParams(new LinearLayout.LayoutParams(40, 40));
        btn.setBackgroundResource(R.drawable.editor_toolbar_button_bg);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(8, 8, 8, 8);
        btn.setImageResource(iconRes);
        btn.setAlpha(0.7f);
        return btn;
    }

    private void addToolGap(LinearLayout parent) {
        View gap = new View(getContext());
        gap.setLayoutParams(new LinearLayout.LayoutParams(5, 0));
        parent.addView(gap);
    }

    private void setToolMode(String mode, ImageButton activeBtn) {
        clickedAction = mode;
        ImageButton[] all = {gridBtn, moveBtn, scaleBtn, rotateBtn};
        for (ImageButton b : all) {
            if (b != null) { b.clearColorFilter(); b.setAlpha(0.7f); }
        }
        activeBtn.setColorFilter(Color.parseColor("#FFB781"));
        activeBtn.setAlpha(1f);
        LibgdxEditor ed = getEditor();
        if (ed == null) return;
        LibgdxEditor.TOUCHMODE tm;
        switch (mode) {
            case "grid": tm = LibgdxEditor.TOUCHMODE.GRID; break;
            case "move": tm = LibgdxEditor.TOUCHMODE.MOVE; break;
            case "scale": tm = LibgdxEditor.TOUCHMODE.SCALE; break;
            default: tm = LibgdxEditor.TOUCHMODE.ROTATE; break;
        }
        runOnGdx(() -> ed.setTouchMode(tm));
    }

    private void onLockToggle() {
        LibgdxEditor ed = getEditor();
        if (ed == null || ed.getSelectedActor() == null) return;
        PropertySet<String, Object> ps = PropertySet.getPropertySet(ed.getSelectedActor());
        boolean isLock = "true".equals(ps.getString("lock"));
        ps.put("lock", String.valueOf(!isLock));
        lockBtn.setImageResource(!isLock ? R.drawable.unlock : R.drawable.lock);
        if (ed.getSelectedActor() instanceof EditorItem) {
            runOnGdx(() -> ((EditorItem) ed.getSelectedActor()).update());
        }
    }

    private void onDelete() {
        LibgdxEditor ed = getEditor();
        if (ed == null || ed.getSelectedActor() == null) return;
        String name = PropertySet.getPropertySet(ed.getSelectedActor()).get("name").toString();
        Actor actor = ed.getSelectedActor();
        runOnGdx(() -> {
            ed.getProject().deleteBody(name, ed.getScene());
            actor.remove();
            EditorAction.itemRemoved(ed, actor);
            ed.selectActor(null);
            if (controlLayer != null) controlLayer.getBodiesList().update();
        });
    }

    private void onSave() {
        LibgdxEditor ed = getEditor();
        if (ed == null || app == null) return;
        ed.getProject().save(ed);
        CodeGenerator.generateFor(ed, cd ->
                Gdx.files.absolute(ed.getProject().getCodesPath(ed.getScene())).writeString(cd, false));
        post(() -> app.toast(getTrans("saved")));
    }

    private void onPlay() {
        final LibgdxEditor ed = getEditor();
        final TestApp a = app;
        if (ed == null || a == null) return;
        post(() -> showCompilerOutput(getTrans("generatingCode"), false));
        CodeGenerator.generateFor(ed, code -> {
            FileHandle sceneFile = Gdx.files.absolute(ed.getProject().getCodesPath(ed.getScene()));
            CompileThread ct = new CompileThread(ed.getProject().get("java"), false);
            ct.setOnChangeStatus(new CompileThread.OnStatusChanged() {
                public void onStatus(String s) { post(() -> showCompilerOutput(s, false)); }
                public void onEnd(String m) { post(() -> hideCompilerOutput()); }
                public void onError(String e) { post(() -> showCompilerOutput(e, true)); }
                public void onSuccess(String m) {
                    FileHandle f = new FileHandle(ed.getProject().getDex());
                    if (f.exists()) f.file().setWritable(true);
                    f.writeString("", false);
                    Gdx.files.absolute(ed.getProject().getPath() + "/java/classes.dex").moveTo(f);
                    post(() -> {
                        hideCompilerOutput();
                        a.play(ed.getProject().getPath(), ed.getScene());
                    });
                }
            });
            if (!sceneFile.exists() || !sceneFile.readString().equals(code)) {
                ct.start();
                sceneFile.writeString(code, false);
            } else {
                post(() -> showCompilerOutput(getTrans("checkJavaFilesChanges"), false));
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        boolean changed = FilesChangeDetector.detect(
                                Gdx.files.absolute(ed.getProject().get("java")).file().toPath(),
                                Gdx.files.absolute(ed.getProject().getChangesJson()).file().toPath());
                        if (!changed) {
                            post(() -> { hideCompilerOutput(); a.play(ed.getProject().getPath(), ed.getScene()); });
                        } else ct.start();
                    } catch (Exception e) { ct.start(); }
                });
            }
        });
    }

    private void setupSideButtons() {
        if (btnBodies == null) return;
        OnClickListener toggle = v -> {
            if (app == null || controlLayer == null) return;
            int id = v.getId();
            runOnGdx(() -> {
                if (id == R.id.btn_bodies) toggleLibGdxWindow(controlLayer.getBodiesList(), btnBodies);
                else if (id == R.id.btn_properties) toggleLibGdxWindow(controlLayer.getPropertiesItem(), btnProperties);
                else if (id == R.id.btn_joints) toggleLibGdxWindow(controlLayer.getJointsList(), btnJoints);
                else if (id == R.id.btn_events) toggleLibGdxWindow(controlLayer.getEventsItem(), btnEvents);
                else if (id == R.id.btn_variables) toggleLibGdxWindow(controlLayer.getVarsItem(), btnVars);
                else if (id == R.id.btn_ai) toggleLibGdxWindow(controlLayer.getEditorAI(), btnAI);
            });
        };
        btnBodies.setOnClickListener(toggle);
        btnProperties.setOnClickListener(toggle);
        btnJoints.setOnClickListener(toggle);
        btnEvents.setOnClickListener(toggle);
        btnVars.setOnClickListener(toggle);
        btnAI.setOnClickListener(toggle);
    }

    private void toggleLibGdxWindow(Actor item, ImageButton btn) {
        if (item == null || app == null || controlLayer == null) return;
        String name = btn != null && btn.getContentDescription() != null
                ? getTrans(btn.getContentDescription().toString().replace("-", ""))
                : getTrans("youCanDrag");
        if (item.getParent() instanceof com.kotcrab.vis.ui.widget.VisWindow) {
            com.kotcrab.vis.ui.widget.VisWindow win =
                    (com.kotcrab.vis.ui.widget.VisWindow) item.getParent();
            if (win.getStage() != null) {
                win.remove();
                post(() -> setSideBtnActive(btn, false));
                return;
            }
        }
        com.kotcrab.vis.ui.widget.VisWindow win = new com.kotcrab.vis.ui.widget.VisWindow(name + " " + getTrans("youCanDrag"));
        win.setKeepWithinStage(false);
        win.setResizable(true);
        win.setMovable(true);
        win.add(item).grow().pad(3).center();
        win.addCloseButton();
        float w = Gdx.graphics.getWidth() * (app.getEditor() != null && app.getEditor().isLandscape() ? 0.4f : 0.85f);
        float h = app.getEditor() != null && app.getEditor().isLandscape()
                ? Gdx.graphics.getHeight() * 0.6f
                : Gdx.graphics.getHeight() * 0.3f;
        win.setSize(w, h);
        win.setPosition((Gdx.graphics.getWidth() - w) / 2, (Gdx.graphics.getHeight() - h) / 3);
        app.getUiStage().addActor(win);
        win.toFront();
        post(() -> setSideBtnActive(btn, true));
    }

    private void setSideBtnActive(ImageButton btn, boolean active) {
        if (btn == null) return;
        btn.setBackgroundResource(active ? R.drawable.editor_side_button_bg_active : R.drawable.editor_side_button_bg);
        if (active) btn.setColorFilter(Color.parseColor("#301400"));
        else btn.clearColorFilter();
    }

    private void addTopBtn(int iconRes, OnClickListener listener) {
        ImageButton btn = new ImageButton(getContext());
        btn.setLayoutParams(new LinearLayout.LayoutParams(42, 42));
        btn.setBackgroundResource(R.drawable.editor_toolbar_button_bg);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(9, 9, 9, 9);
        btn.setImageResource(iconRes);
        btn.setOnClickListener(listener);
        topContainer.addView(btn);
        addGap(topContainer);
    }

    private void addGap(LinearLayout c) {
        View g = new View(getContext());
        g.setLayoutParams(new LinearLayout.LayoutParams(5, 0));
        c.addView(g);
    }

    public void updateSceneTabs() {
        if (sceneTabsContainer == null || app == null || app.editors == null) return;
        sceneTabsContainer.removeAllViews();
        for (int i = 0; i < app.editors.size; i++) {
            LibgdxEditor ed = app.editors.get(i);
            String sn = ed.getScene();
            boolean active = getEditor() != null && getEditor().getScene().equalsIgnoreCase(sn);
            LinearLayout tab = new LinearLayout(getContext());
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 38));
            tab.setPadding(10, 0, 4, 0);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setBackgroundResource(active ? R.drawable.editor_side_button_bg_active : R.drawable.editor_toolbar_button_bg);
            int ei = i;
            tab.setOnClickListener(v -> runOnGdx(() -> app.setCurrentEditor(ei)));
            TextView tv = new TextView(getContext());
            tv.setText(sn);
            tv.setTextSize(12);
            tv.setTextColor(active ? Color.parseColor("#301400") : Color.parseColor("#FFFBFF"));
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            tab.addView(tv);
            ImageButton cb = new ImageButton(getContext());
            cb.setImageResource(R.drawable.ic_delete_black);
            cb.setBackgroundResource(android.R.color.transparent);
            cb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cb.setPadding(4, 4, 4, 4);
            cb.setLayoutParams(new LinearLayout.LayoutParams(32, 32));
            cb.setAlpha(0.6f);
            cb.setOnClickListener(v -> {
                if (app.editors.size > 1) {
                    runOnGdx(() -> {
                        app.editors.get(ei).dispose();
                        app.editors.removeIndex(ei);
                        if (getEditor() != null && getEditor().getScene().equalsIgnoreCase(sn))
                            app.setCurrentEditor(0);
                        post(this::updateSceneTabs);
                    });
                }
            });
            tab.addView(cb);
            sceneTabsContainer.addView(tab);
            View g = new View(getContext());
            g.setLayoutParams(new LinearLayout.LayoutParams(5, 0));
            sceneTabsContainer.addView(g);
        }
    }

    public void updateUndoRedo() {
        LibgdxEditor ed = getEditor();
        if (undoBtn != null) { boolean u = ed != null && ed.canUndo(); undoBtn.setEnabled(u); undoBtn.setAlpha(u ? 1f : 0.3f); }
        if (redoBtn != null) { boolean r = ed != null && ed.canRedo(); redoBtn.setEnabled(r); redoBtn.setAlpha(r ? 1f : 0.3f); }
    }

    public void bodySelected() {
        LibgdxEditor ed = getEditor();
        if (ed != null && ed.getSelectedActor() != null && lockBtn != null) {
            String l = PropertySet.getPropertySet(ed.getSelectedActor()).getString("lock");
            lockBtn.setImageResource("true".equals(l) ? R.drawable.lock : R.drawable.unlock);
        }
    }

    public void setIndexing(boolean v) { if (indexingLabel != null) indexingLabel.setVisibility(v ? VISIBLE : GONE); }

    public void showCompilerOutput(String text, boolean isError) {
        if (floatingPanelsContainer == null) return;
        floatingPanelsContainer.removeAllViews();
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(isError ? Color.parseColor("#FFB4AB") : Color.parseColor("#FFFBFF"));
        tv.setBackgroundColor(Color.parseColor("#201A17"));
        tv.setPadding(24, 24, 24, 24);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(15);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.CENTER;
        tv.setLayoutParams(lp);
        floatingPanelsContainer.addView(tv);
        floatingPanelsContainer.setVisibility(VISIBLE);
    }

    public void hideCompilerOutput() {
        if (floatingPanelsContainer != null) {
            floatingPanelsContainer.removeAllViews();
            floatingPanelsContainer.setVisibility(GONE);
        }
    }

    private void runOnGdx(Runnable r) {
        Gdx.app.postRunnable(r);
    }
}