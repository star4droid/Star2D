package com.star4droid.star2d.editor.ui.android;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import android.widget.PopupMenu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.star4droid.star2d.EditorActivity;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.CompileThread;
import com.star4droid.star2d.Helpers.FilesChangeDetector;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.star2d.evo.R;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class EditorUI {
    private final EditorActivity activity;
    private final Editor editorBridge;
    private TestApp app;

    // Root layouts
    private ViewGroup editorRoot;
    private ViewGroup topToolbar;
    private ViewGroup bottomControls;
    private ViewGroup bottomFilesPanel;
    private ViewGroup leftPanel;
    private ViewGroup rightPanel;
    private ViewGroup navigationRail;
    private ViewGroup editorFrame;

    // Top toolbar buttons
    private ImageView btnBack, btnRotate, btnAddItem, btnSceneActions, btnSave;
    private ImageView btnFiles, btnLogs, btnSceneColor, btnLock;
    private TextView indexingLabel;

    // Bottom control buttons
    private ImageView btnPlay, btnGrid, btnMove, btnScale, btnRotateMode;
    private ImageView btnDelete, btnUndo, btnRedo, btnCenterCamera, btnSettings, btnToggleFiles;

    // Right panel
    private TextView tabProperties, tabEvents, tabVariables, tabAI;
    private ViewGroup propertiesContent, eventsContent, variablesContent, aiContent, jointsSection;
    private ImageView btnCollapseRight;

    // Navigation rail
    private ImageView navBodies, navProperties, navJoints, navEvents, navVariables, navAI;
    private String selectedNav = "bodies";
    private String selectedTab = "properties";

    // Left panel
    private ListView bodiesList;
    private EditText searchBodies;
    private ImageView btnCollapseLeft;

    // Files manager
    private GridView fileGrid;
    private TextView fileCurrentPath;
    private ImageView fileBtnBack, fileBtnCreate, fileBtnImport, fileBtnViewToggle;
    private ImageView btnCollapseFiles, btnCloseFiles;
    private TextView fileTabFiles, fileTabImages, fileTabAnims, fileTabSounds;
    private String selectedFileTab = "files";
    private boolean fileGridMode = false;
    private FileHandle currentFileDir;
    private FileHandle rootFileDir;

    // State
    private boolean isVisible = false;
    private boolean leftPanelVisible = true;
    private boolean rightPanelVisible = true;
    private boolean filesPanelVisible = false;

    private static final int ANIM_DURATION = 250;

    public EditorUI(EditorActivity activity, Editor editorBridge) {
        this.activity = activity;
        this.editorBridge = editorBridge;
    }

    public void init() {
        editorRoot = activity.findViewById(R.id.editor_root);
        topToolbar = activity.findViewById(R.id.top_toolbar);
        bottomControls = activity.findViewById(R.id.bottom_controls);
        bottomFilesPanel = activity.findViewById(R.id.bottom_files_panel);
        leftPanel = activity.findViewById(R.id.left_panel);
        rightPanel = activity.findViewById(R.id.right_panel);
        navigationRail = activity.findViewById(R.id.navigation_rail);
        editorFrame = activity.findViewById(R.id.editor_frame);

        // Top toolbar
        btnBack = activity.findViewById(R.id.btn_back);
        btnRotate = activity.findViewById(R.id.btn_rotate);
        btnAddItem = activity.findViewById(R.id.btn_add_item);
        btnSceneActions = activity.findViewById(R.id.btn_scene_actions);
        btnSave = activity.findViewById(R.id.btn_save);
        btnFiles = activity.findViewById(R.id.btn_files);
        btnLogs = activity.findViewById(R.id.btn_logs);
        btnSceneColor = activity.findViewById(R.id.btn_scene_color);
        btnLock = activity.findViewById(R.id.btn_lock);
        indexingLabel = activity.findViewById(R.id.indexing_label);

        // Bottom controls
        btnPlay = activity.findViewById(R.id.btn_play);
        btnGrid = activity.findViewById(R.id.btn_grid);
        btnMove = activity.findViewById(R.id.btn_move);
        btnScale = activity.findViewById(R.id.btn_scale);
        btnRotateMode = activity.findViewById(R.id.btn_rotate_mode);
        btnDelete = activity.findViewById(R.id.btn_delete);
        btnUndo = activity.findViewById(R.id.btn_undo);
        btnRedo = activity.findViewById(R.id.btn_redo);
        btnCenterCamera = activity.findViewById(R.id.btn_center_camera);
        btnSettings = activity.findViewById(R.id.btn_settings);
        btnToggleFiles = activity.findViewById(R.id.btn_toggle_files);

        // Right panel
        tabProperties = activity.findViewById(R.id.tab_properties);
        tabEvents = activity.findViewById(R.id.tab_events);
        tabVariables = activity.findViewById(R.id.tab_variables);
        tabAI = activity.findViewById(R.id.tab_ai);
        propertiesContent = activity.findViewById(R.id.properties_content);
        eventsContent = activity.findViewById(R.id.events_content);
        variablesContent = activity.findViewById(R.id.variables_content);
        aiContent = activity.findViewById(R.id.ai_content);
        jointsSection = activity.findViewById(R.id.joints_section);
        btnCollapseRight = activity.findViewById(R.id.btn_collapse_right);

        // Navigation rail
        navBodies = activity.findViewById(R.id.nav_bodies);
        navProperties = activity.findViewById(R.id.nav_properties);
        navJoints = activity.findViewById(R.id.nav_joints);
        navEvents = activity.findViewById(R.id.nav_events);
        navVariables = activity.findViewById(R.id.nav_variables);
        navAI = activity.findViewById(R.id.nav_ai);

        // Left panel
        bodiesList = activity.findViewById(R.id.bodies_list);
        searchBodies = activity.findViewById(R.id.search_bodies);
        btnCollapseLeft = activity.findViewById(R.id.btn_collapse_left);

        // Files manager
        fileGrid = activity.findViewById(R.id.file_grid);
        fileCurrentPath = activity.findViewById(R.id.file_current_path);
        fileBtnBack = activity.findViewById(R.id.file_btn_back);
        fileBtnCreate = activity.findViewById(R.id.file_btn_create);
        fileBtnImport = activity.findViewById(R.id.file_btn_import);
        fileBtnViewToggle = activity.findViewById(R.id.file_btn_view_toggle);
        btnCollapseFiles = activity.findViewById(R.id.btn_collapse_files);
        btnCloseFiles = activity.findViewById(R.id.btn_close_files);
        fileTabFiles = activity.findViewById(R.id.file_tab_files);
        fileTabImages = activity.findViewById(R.id.file_tab_images);
        fileTabAnims = activity.findViewById(R.id.file_tab_anims);
        fileTabSounds = activity.findViewById(R.id.file_tab_sounds);

        setupClickListeners();
        hideAll();
    }

    public void setApp(TestApp testApp) {
        this.app = testApp;
    }

    public TestApp getApp() {
        return app;
    }

    // ─── Visibility Management ───

    public void show() {
        if (isVisible) return;
        isVisible = true;
        editorRoot.setVisibility(View.VISIBLE);
        animateSlideIn(topToolbar, 0, -topToolbar.getHeight(), 0);
        animateSlideIn(bottomControls, 0, bottomControls.getHeight(), 0);
        animateSlideIn(leftPanel, -leftPanel.getWidth(), 0, 0);
        animateSlideIn(navigationRail, navigationRail.getWidth(), 0, 0);
        animateSlideIn(rightPanel, rightPanel.getWidth(), 0, 0);
    }

    public void hide() {
        if (!isVisible) return;
        isVisible = false;
        animateSlideOut(topToolbar, 0, -topToolbar.getHeight(), () -> {});
        animateSlideOut(bottomControls, 0, bottomControls.getHeight(), () -> {});
        animateSlideOut(leftPanel, -leftPanel.getWidth(), 0, () -> {});
        animateSlideOut(navigationRail, navigationRail.getWidth(), 0, () -> {});
        animateSlideOut(rightPanel, rightPanel.getWidth(), 0, () -> {
            editorRoot.setVisibility(View.GONE);
        });
        bottomFilesPanel.setVisibility(View.GONE);
        filesPanelVisible = false;
    }

    public void showEditorUI() {
        show();
    }

    public void hideEditorUI() {
        hide();
    }

    public void toggle() {
        if (isVisible) hide();
        else show();
    }

    private void hideAll() {
        editorRoot.setVisibility(View.GONE);
    }

    // ─── Panel Animations ───

    private void animateSlideIn(final View view, float startX, float startY, final float endX) {
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
        view.setTranslationX(startX);
        view.setTranslationY(startY);
        view.animate()
            .translationX(endX)
            .translationY(0)
            .setDuration(ANIM_DURATION)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void animateSlideOut(final View view, float endX, float endY, final Runnable onEnd) {
        view.animate()
            .translationX(endX)
            .translationY(endY)
            .setDuration(ANIM_DURATION)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .withEndAction(() -> {
                view.setVisibility(View.GONE);
                view.setTranslationX(0);
                view.setTranslationY(0);
                if (onEnd != null) onEnd.run();
            })
            .start();
    }

    private void animateHeight(final View view, int from, int to, final Runnable onEnd) {
        ValueAnimator anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.height = (int) va.getAnimatedValue();
            view.setLayoutParams(lp);
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onEnd != null) onEnd.run();
            }
        });
        anim.start();
    }

    private void animateWidth(final View view, int from, int to, final Runnable onEnd) {
        ValueAnimator anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = (int) va.getAnimatedValue();
            view.setLayoutParams(lp);
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onEnd != null) onEnd.run();
            }
        });
        anim.start();
    }

    // ─── Click Listeners ───

    private void setupClickListeners() {
        // Top toolbar
        btnBack.setOnClickListener(v -> onBack());
        btnRotate.setOnClickListener(v -> onRotate());
        btnAddItem.setOnClickListener(v -> onAddItem());
        btnSceneActions.setOnClickListener(v -> onSceneActions());
        btnSave.setOnClickListener(v -> onSave());
        btnFiles.setOnClickListener(v -> onFiles());
        btnLogs.setOnClickListener(v -> onLogs());
        btnSceneColor.setOnClickListener(v -> onSceneColor());
        btnLock.setOnClickListener(v -> onLock());

        // Bottom controls
        btnPlay.setOnClickListener(v -> onPlay());
        btnGrid.setOnClickListener(v -> onGrid());
        btnMove.setOnClickListener(v -> onMove());
        btnScale.setOnClickListener(v -> onScale());
        btnRotateMode.setOnClickListener(v -> onRotateMode());
        btnDelete.setOnClickListener(v -> onDelete());
        btnUndo.setOnClickListener(v -> onUndo());
        btnRedo.setOnClickListener(v -> onRedo());
        btnCenterCamera.setOnClickListener(v -> onCenterCamera());
        btnSettings.setOnClickListener(v -> onSettings());
        btnToggleFiles.setOnClickListener(v -> toggleFilesPanel());

        // Right panel tabs
        tabProperties.setOnClickListener(v -> selectTab("properties"));
        tabEvents.setOnClickListener(v -> selectTab("events"));
        tabVariables.setOnClickListener(v -> selectTab("variables"));
        tabAI.setOnClickListener(v -> selectTab("ai"));
        btnCollapseRight.setOnClickListener(v -> toggleRightPanel());

        // Navigation rail
        navBodies.setOnClickListener(v -> selectNav("bodies"));
        navProperties.setOnClickListener(v -> selectNav("properties"));
        navJoints.setOnClickListener(v -> selectNav("joints"));
        navEvents.setOnClickListener(v -> selectNav("events"));
        navVariables.setOnClickListener(v -> selectNav("variables"));
        navAI.setOnClickListener(v -> selectNav("ai"));

        // Left panel
        btnCollapseLeft.setOnClickListener(v -> toggleLeftPanel());
        searchBodies.setOnEditorActionListener((tv, actionId, event) -> {
            onSearchBodies();
            return true;
        });

        // Files manager
        fileBtnBack.setOnClickListener(v -> onFileBack());
        fileBtnCreate.setOnClickListener(v -> onFileCreate());
        fileBtnImport.setOnClickListener(v -> onFileImport());
        fileBtnViewToggle.setOnClickListener(v -> toggleFileView());
        btnCollapseFiles.setOnClickListener(v -> toggleFilesPanel());
        btnCloseFiles.setOnClickListener(v -> hideFilesPanel());
        fileTabFiles.setOnClickListener(v -> selectFileTab("files"));
        fileTabImages.setOnClickListener(v -> selectFileTab("images"));
        fileTabAnims.setOnClickListener(v -> selectFileTab("anims"));
        fileTabSounds.setOnClickListener(v -> selectFileTab("sounds"));
    }

    // ─── Top Toolbar Actions ───

    private void onBack() {
        if (app == null) return;
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            new ConfirmDialog(com.star4droid.star2d.editor.utils.Lang.getTrans("exit"),
                com.star4droid.star2d.editor.utils.Lang.getTrans("areYouSure"), ok -> {
                if (ok) app.closeProject();
            }).show(app.getUiStage());
        });
    }

    private void onRotate() {
        if (app == null || app.getEditor() == null) return;
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            new ConfirmDialog(com.star4droid.star2d.editor.utils.Lang.getTrans("changeOrienation"),
                com.star4droid.star2d.editor.utils.Lang.getTrans("editorRestartNote"), ok -> {
                if (ok) app.setOrienation(!app.getEditor().isLandscape());
            }).show(app.getUiStage());
        });
    }

    private void onAddItem() {
        if (app == null || app.getControlLayer() == null) return;
        ControlLayer cl = app.getControlLayer();
        try {
            java.lang.reflect.Method m = ControlLayer.class.getDeclaredMethod("createBodiesMenu");
            m.setAccessible(true);
            Object menu = m.invoke(cl);
            if (menu instanceof com.kotcrab.vis.ui.widget.PopupMenu) {
                int[] loc = new int[2];
                btnAddItem.getLocationOnScreen(loc);
                ((com.kotcrab.vis.ui.widget.PopupMenu) menu).showMenu(app.getUiStage(), loc[0], loc[1]);
            }
        } catch (Exception e) {
            showToast("Add via ControlLayer");
        }
    }

    private void onSceneActions() {
        if (app == null || app.getControlLayer() == null) return;
        try {
            java.lang.reflect.Field f = ControlLayer.class.getDeclaredField("sceneActionsBtn");
            f.setAccessible(true);
            Object btn = f.get(app.getControlLayer());
            if (btn instanceof com.kotcrab.vis.ui.widget.VisImageButton) {
                com.kotcrab.vis.ui.widget.VisImageButton vib = (com.kotcrab.vis.ui.widget.VisImageButton) btn;
                vib.toggle();
            }
        } catch (Exception e) {
            showToast("Scene actions");
        }
    }

    private void onSave() {
        if (app == null || app.getEditor() == null) return;
        LibgdxEditor editor = app.getEditor();
        editor.getProject().save(editor);
        CodeGenerator.generateFor(editor, cd -> {
            com.badlogic.gdx.Gdx.files.absolute(editor.getProject().getCodesPath(editor.getScene())).writeString(cd, false);
        });
        showToast("Saved");
    }

    private void onFiles() {
        if (app == null) return;
        toggleFilesPanel();
    }

    private void onLogs() {
        if (app == null || app.getFileBrowser() == null) return;
        app.getFileBrowser().setRootDir(com.badlogic.gdx.Gdx.files.external("logs"));
        app.getFileBrowser().setVisible(!app.getFileBrowser().isVisible());
        if (app.getFileBrowser().isVisible()) {
            app.getFileBrowser().toFront();
            showToast("Showing logs folder");
        }
    }

    private void onSceneColor() {
        if (app == null || app.getControlLayer() == null) return;
        new com.star4droid.star2d.editor.ui.SettingsDialog(app.getUiStage(), app).show(app.getUiStage());
    }

    private void onLock() {
        if (app == null || app.getEditor() == null) return;
        LibgdxEditor editor = app.getEditor();
        if (editor.getSelectedActor() != null) {
            PropertySet<String, Object> ps = PropertySet.getPropertySet(editor.getSelectedActor());
            boolean isLock = !"true".equals(ps.getString("lock"));
            ps.put("lock", String.valueOf(isLock));
            btnLock.setImageResource(isLock ? R.drawable.lock : R.drawable.unlock);
        }
    }

    // ─── Bottom Control Actions ───

    private String currentAction = "grid";

    private void onPlay() {
        if (app == null || app.getEditor() == null) return;
        btnPlay.setEnabled(false);
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            String path = app.getEditor().getProject().getPath();
            final com.badlogic.gdx.files.FileHandle errorLog = com.badlogic.gdx.Gdx.files.external("logs/" + path.substring(path.lastIndexOf("/")) + "/compile error.txt");
            final com.badlogic.gdx.InputProcessor inputProcessor = com.badlogic.gdx.Gdx.input.getInputProcessor();
            com.badlogic.gdx.Gdx.input.setInputProcessor(null);

            CodeGenerator.generateFor(app.getEditor(), code -> {
                com.badlogic.gdx.files.FileHandle sceneFile = com.badlogic.gdx.Gdx.files.absolute(app.getEditor().getProject().getCodesPath(app.getEditor().getScene()));
                CompileThread compileThread = new CompileThread(app.getEditor().getProject().get("java"), false);
                compileThread.setOnChangeStatus(new CompileThread.OnStatusChanged() {
                    @Override
                    public void onStatus(String s) {
                        showToast(s);
                    }

                    @Override
                    public void onEnd(String message) {
                        com.badlogic.gdx.Gdx.input.setInputProcessor(inputProcessor);
                        activity.runOnUiThread(() -> btnPlay.setEnabled(true));
                    }

                    @Override
                    public void onError(String error) {
                        errorLog.writeString(error, false);
                    }

                    @Override
                    public void onSuccess(String message) {
                        if (errorLog.exists()) errorLog.delete();
                        com.badlogic.gdx.files.FileHandle fileHandle = new com.badlogic.gdx.files.FileHandle(app.getEditor().getProject().getDex());
                        if (fileHandle.exists()) fileHandle.file().setWritable(true);
                        fileHandle.writeString("", false);
                        com.badlogic.gdx.Gdx.files.absolute(app.getEditor().getProject().getPath() + "/java/classes.dex").moveTo(fileHandle);
                        app.play(app.getEditor().getProject().getPath(), app.getEditor().getScene());
                    }
                });

                if ((sceneFile.exists() && !sceneFile.readString().equals(code)) || !sceneFile.exists()) {
                    compileThread.start();
                    sceneFile.writeString(code, false);
                } else {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            boolean filesChanged = FilesChangeDetector.detect(
                                com.badlogic.gdx.Gdx.files.absolute(app.getEditor().getProject().get("java")).file().toPath(),
                                com.badlogic.gdx.Gdx.files.absolute(app.getEditor().getProject().getChangesJson()).file().toPath());
                            if (!filesChanged) {
                                com.badlogic.gdx.Gdx.app.postRunnable(() -> app.play(app.getEditor().getProject().getPath(), app.getEditor().getScene()));
                            } else compileThread.start();
                        } catch (Exception e) {
                            compileThread.start();
                        }
                    });
                }
            });
        });
    }

    private void setActiveControl(ImageView active, String action) {
        int activeBg = R.drawable.rounded_icon_bg_active;
        int inactiveBg = R.drawable.rounded_icon_bg;
        btnGrid.setBackgroundResource(action.equals("grid") ? activeBg : inactiveBg);
        btnMove.setBackgroundResource(action.equals("move") ? activeBg : inactiveBg);
        btnScale.setBackgroundResource(action.equals("scale") ? activeBg : inactiveBg);
        btnRotateMode.setBackgroundResource(action.equals("rotate") ? activeBg : inactiveBg);
        currentAction = action;
        if (app != null && app.getEditor() != null) {
            switch (action) {
                case "grid": app.getEditor().setTouchMode(LibgdxEditor.TOUCHMODE.GRID); break;
                case "move": app.getEditor().setTouchMode(LibgdxEditor.TOUCHMODE.MOVE); break;
                case "scale": app.getEditor().setTouchMode(LibgdxEditor.TOUCHMODE.SCALE); break;
                case "rotate": app.getEditor().setTouchMode(LibgdxEditor.TOUCHMODE.ROTATE); break;
            }
        }
    }

    private void onGrid() {
        if (currentAction.equals("grid")) {
            showXYMenu(btnGrid);
            return;
        }
        setActiveControl(btnGrid, "grid");
    }

    private void onMove() {
        if (currentAction.equals("move")) {
            showXYMenu(btnMove);
            return;
        }
        setActiveControl(btnMove, "move");
    }

    private void onScale() {
        if (currentAction.equals("scale")) {
            showXYMenu(btnScale);
            return;
        }
        setActiveControl(btnScale, "scale");
    }

    private void onRotateMode() {
        setActiveControl(btnRotateMode, "rotate");
    }

    private void showXYMenu(View anchor) {
        if (app == null) return;
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenu().add("No Lock").setOnMenuItemClickListener(item -> {
            if (app.getEditor() != null) {
                app.getEditor().setLockX(false);
                app.getEditor().setLockY(false);
            }
            return true;
        });
        popup.getMenu().add("X Lock").setOnMenuItemClickListener(item -> {
            if (app.getEditor() != null) {
                app.getEditor().setLockX(true);
                app.getEditor().setLockY(false);
            }
            return true;
        });
        popup.getMenu().add("Y Lock").setOnMenuItemClickListener(item -> {
            if (app.getEditor() != null) {
                app.getEditor().setLockX(false);
                app.getEditor().setLockY(true);
            }
            return true;
        });
        popup.show();
    }

    private void onDelete() {
        if (app == null || app.getEditor() == null) return;
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            LibgdxEditor editor = app.getEditor();
            if (editor.getSelectedActor() == null) return;
            com.badlogic.gdx.scenes.scene2d.Actor actor = editor.getSelectedActor();
            editor.getProject().deleteBody(PropertySet.getPropertySet(actor).get("name").toString(), editor.getScene());
            actor.remove();
            EditorAction.itemRemoved(editor, actor);
            editor.selectActor(null);
            refreshBodiesList();
        });
    }

    private void onUndo() {
        if (app != null && app.getEditor() != null && app.getEditor().canUndo())
            app.getEditor().undo();
    }

    private void onRedo() {
        if (app != null && app.getEditor() != null && app.getEditor().canRedo())
            app.getEditor().redo();
    }

    private void onCenterCamera() {
        if (app != null && app.getEditor() != null)
            app.getEditor().centerCamera();
    }

    private void onSettings() {
        if (app != null && app.getEditor() != null) {
            new com.star4droid.star2d.editor.ui.SettingsDialog(app.getUiStage(), app).show(app.getUiStage());
        }
    }

    // ─── Right Panel ───

    private void selectTab(String tab) {
        selectedTab = tab;
        tabProperties.setTextColor(tab.equals("properties") ? Color.parseColor("#00D4FF") : Color.parseColor("#8892A4"));
        tabEvents.setTextColor(tab.equals("events") ? Color.parseColor("#00D4FF") : Color.parseColor("#8892A4"));
        tabVariables.setTextColor(tab.equals("variables") ? Color.parseColor("#00D4FF") : Color.parseColor("#8892A4"));
        tabAI.setTextColor(tab.equals("ai") ? Color.parseColor("#00D4FF") : Color.parseColor("#8892A4"));

        propertiesContent.setVisibility(tab.equals("properties") ? View.VISIBLE : View.GONE);
        eventsContent.setVisibility(tab.equals("events") ? View.VISIBLE : View.GONE);
        variablesContent.setVisibility(tab.equals("variables") ? View.VISIBLE : View.GONE);
        aiContent.setVisibility(tab.equals("ai") ? View.VISIBLE : View.GONE);
    }

    private void toggleRightPanel() {
        rightPanelVisible = !rightPanelVisible;
        if (rightPanelVisible) {
            rightPanel.setVisibility(View.VISIBLE);
            btnCollapseRight.setRotation(270);
            animateWidth(rightPanel, 0, rightPanel.getWidth(), null);
        } else {
            btnCollapseRight.setRotation(90);
            animateWidth(rightPanel, rightPanel.getWidth(), 0, () -> rightPanel.setVisibility(View.GONE));
        }
    }

    // ─── Navigation Rail ───

    private void selectNav(String nav) {
        selectedNav = nav;
        int selectedTint = Color.parseColor("#FFFFFF");
        int unselectedTint = Color.parseColor("#AAB4C8");

        navBodies.setSelected(nav.equals("bodies"));
        navProperties.setSelected(nav.equals("properties"));
        navJoints.setSelected(nav.equals("joints"));
        navEvents.setSelected(nav.equals("events"));
        navVariables.setSelected(nav.equals("variables"));
        navAI.setSelected(nav.equals("ai"));

        navBodies.setColorFilter(nav.equals("bodies") ? selectedTint : unselectedTint);
        navProperties.setColorFilter(nav.equals("properties") ? selectedTint : unselectedTint);
        navJoints.setColorFilter(nav.equals("joints") ? selectedTint : unselectedTint);
        navEvents.setColorFilter(nav.equals("events") ? selectedTint : unselectedTint);
        navVariables.setColorFilter(nav.equals("variables") ? selectedTint : unselectedTint);
        navAI.setColorFilter(nav.equals("ai") ? selectedTint : unselectedTint);

        switch (nav) {
            case "bodies":
                leftPanel.setVisibility(View.VISIBLE);
                rightPanel.setVisibility(View.GONE);
                break;
            case "properties":
            case "events":
            case "variables":
            case "ai":
                rightPanel.setVisibility(View.VISIBLE);
                selectTab(nav.equals("properties") ? "properties" : nav.equals("events") ? "events" : nav.equals("variables") ? "variables" : "ai");
                leftPanel.setVisibility(View.GONE);
                break;
            case "joints":
                rightPanel.setVisibility(View.VISIBLE);
                jointsSection.setVisibility(View.VISIBLE);
                leftPanel.setVisibility(View.GONE);
                break;
        }
    }

    // ─── Left Panel ───

    private void toggleLeftPanel() {
        leftPanelVisible = !leftPanelVisible;
        if (leftPanelVisible) {
            leftPanel.setVisibility(View.VISIBLE);
            btnCollapseLeft.setRotation(0);
            animateWidth(leftPanel, 0, leftPanel.getWidth(), null);
        } else {
            btnCollapseLeft.setRotation(90);
            animateWidth(leftPanel, leftPanel.getWidth(), 0, () -> leftPanel.setVisibility(View.GONE));
        }
    }

    private void onSearchBodies() {
        String query = searchBodies.getText().toString().toLowerCase();
        refreshBodiesList(query);
    }

    public void refreshBodiesList() {
        refreshBodiesList("");
    }

    public void refreshBodiesList(String filter) {
        if (app == null || app.getEditor() == null) return;
        ArrayList<String> bodies = app.getEditor().getBodiesList();
        // TODO: Update ListView adapter with filtered bodies
    }

    // ─── Files Manager ───

    private void toggleFilesPanel() {
        filesPanelVisible = !filesPanelVisible;
        if (filesPanelVisible) {
            showFilesPanel();
        } else {
            hideFilesPanel();
        }
    }

    private void showFilesPanel() {
        filesPanelVisible = true;
        bottomFilesPanel.setVisibility(View.VISIBLE);
        if (currentFileDir == null && app != null && app.getEditor() != null) {
            currentFileDir = com.badlogic.gdx.Gdx.files.absolute(app.getEditor().getProject().getPath());
            rootFileDir = currentFileDir;
        }
        refreshFileList();
        animateHeight(bottomFilesPanel, 0, dpToPx(160), null);
    }

    private void hideFilesPanel() {
        filesPanelVisible = false;
        animateHeight(bottomFilesPanel, bottomFilesPanel.getHeight(), 0, () -> bottomFilesPanel.setVisibility(View.GONE));
    }

    private void selectFileTab(String tab) {
        selectedFileTab = tab;
        int selected = Color.parseColor("#00D4FF");
        int unselected = Color.parseColor("#8892A4");
        fileTabFiles.setTextColor(tab.equals("files") ? selected : unselected);
        fileTabImages.setTextColor(tab.equals("images") ? selected : unselected);
        fileTabAnims.setTextColor(tab.equals("anims") ? selected : unselected);
        fileTabSounds.setTextColor(tab.equals("sounds") ? selected : unselected);
        refreshFileList();
    }

    private void onFileBack() {
        if (currentFileDir != null && currentFileDir.parent() != null) {
            if (!currentFileDir.path().equals(rootFileDir.path())) {
                currentFileDir = currentFileDir.parent();
                refreshFileList();
            }
        }
    }

    private void onFileCreate() {
        if (currentFileDir == null) return;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle("Create New");
        final EditText input = new EditText(activity);
        input.setHint("Name");
        builder.setView(input);
        builder.setPositiveButton("File", (d, w) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                com.badlogic.gdx.Gdx.files.absolute(currentFileDir.path() + "/" + name).writeString("", false);
                refreshFileList();
            }
        });
        builder.setNeutralButton("Folder", (d, w) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                com.badlogic.gdx.Gdx.files.absolute(currentFileDir.path() + "/" + name).mkdirs();
                refreshFileList();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void onFileImport() {
        if (app != null && app.getFileBrowser() != null) {
            app.getFileBrowser().setVisible(true);
        }
    }

    private void toggleFileView() {
        fileGridMode = !fileGridMode;
        refreshFileList();
    }

    private void refreshFileList() {
        if (currentFileDir == null) return;
        fileCurrentPath.setText(currentFileDir.path());
        File[] files = currentFileDir.file().listFiles();
        if (files == null) files = new File[0];

        final File[] sortedFiles = files;
        java.util.Arrays.sort(sortedFiles, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        ArrayList<String> displayNames = new ArrayList<>();
        for (File f : sortedFiles) {
            String name = f.getName();
            if (selectedFileTab.equals("images") && !isImageFile(name)) continue;
            if (selectedFileTab.equals("anims") && !name.endsWith(".anim")) continue;
            if (selectedFileTab.equals("sounds") && !isSoundFile(name)) continue;
            displayNames.add((f.isDirectory() ? "� " : "  ") + name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, displayNames);
        fileGrid.setAdapter(null);
        fileGrid.setAdapter(adapter);
        fileGrid.setOnItemClickListener((parent, view, position, id) -> {
            String itemName = sortedFiles[position].getName();
            File clicked = sortedFiles[position];
            if (clicked.isDirectory()) {
                currentFileDir = com.badlogic.gdx.Gdx.files.absolute(clicked.getAbsolutePath());
                refreshFileList();
            } else {
                openFile(clicked);
            }
        });

        fileGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            showFileContextMenu(sortedFiles[position], view);
            return true;
        });
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp") || lower.endsWith(".gif");
    }

    private boolean isSoundFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".m4a");
    }

    private void openFile(File file) {
        if (app == null) return;
        String path = file.getAbsolutePath();
        if (file.getName().toLowerCase().endsWith(".java")) {
            activity.openJava(path);
        } else if (file.getName().toLowerCase().endsWith(".s2df")) {
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                if (app.getFileBrowser() != null) {
                    app.getFileBrowser().getBitmapFontEditor()
                        .setData(currentFileDir, com.badlogic.gdx.Gdx.files.absolute(path))
                        .show(app.getUiStage()).toFront();
                }
            });
        } else if (file.getName().toLowerCase().endsWith(".anim")) {
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                try {
                    new com.star4droid.star2d.editor.ui.AnimationEditor()
                        .setAssetLoader(app.getEditor().getAssetLoader())
                        .setPaths(currentFileDir.sibling("images").path(), path)
                        .refresh().show(app.getUiStage());
                } catch (Exception e) {
                    showToast("Error opening animation: " + e.getMessage());
                }
            });
        } else {
            showFileContent(file);
        }
    }

    private void showFileContent(File file) {
        if (app == null || app.getFileBrowser() == null) return;
        com.badlogic.gdx.files.FileHandle fh = com.badlogic.gdx.Gdx.files.absolute(file.getAbsolutePath());
        if (fh.length() < 1024 * 1024 * 0.25f) {
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                app.getFileBrowser().showText(fh.readString());
            });
        } else {
            showToast("File too large");
        }
    }

    private void showFileContextMenu(final File file, View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenu().add("Open").setOnMenuItemClickListener(item -> {
            openFile(file);
            return true;
        });
        popup.getMenu().add("Delete").setOnMenuItemClickListener(item -> {
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                new ConfirmDialog(com.star4droid.star2d.editor.utils.Lang.getTrans("delete"),
                    com.star4droid.star2d.editor.utils.Lang.getTrans("areYouSure"), ok -> {
                    if (ok) {
                        if (file.isDirectory()) {
                            com.badlogic.gdx.Gdx.files.absolute(file.getAbsolutePath()).deleteDirectory();
                        } else {
                            com.badlogic.gdx.Gdx.files.absolute(file.getAbsolutePath()).delete();
                        }
                        refreshFileList();
                    }
                }).show(app.getUiStage());
            });
            return true;
        });
        popup.getMenu().add("Rename").setOnMenuItemClickListener(item -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
            builder.setTitle("Rename");
            final EditText input = new EditText(activity);
            input.setText(file.getName());
            builder.setView(input);
            builder.setPositiveButton("OK", (d, w) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    com.badlogic.gdx.Gdx.files.absolute(file.getAbsolutePath())
                        .moveTo(com.badlogic.gdx.Gdx.files.absolute(file.getParent() + "/" + newName));
                    refreshFileList();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
            return true;
        });
        popup.show();
    }

    public void refreshFiles() {
        if (filesPanelVisible) refreshFileList();
    }

    // ─── Utilities ───

    public void showToast(String message) {
        if (app != null) app.toast(message);
    }

    private int dpToPx(int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density);
    }

    public String getCurrentAction() {
        return currentAction;
    }

    public boolean isUIVisible() {
        return isVisible;
    }

    public void setIndexingVisible(boolean visible) {
        if (indexingLabel != null) {
            indexingLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public ImageView getLockButton() {
        return btnLock;
    }

    public void updateUndoRedo() {
        if (app == null || app.getEditor() == null) return;
        btnUndo.setEnabled(app.getEditor().canUndo());
        btnRedo.setEnabled(app.getEditor().canRedo());
    }
}
