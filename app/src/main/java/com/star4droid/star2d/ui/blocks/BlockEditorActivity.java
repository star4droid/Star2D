package com.star4droid.star2d.ui.blocks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.editor.ui.variables.VarModel;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.FilePickerDialog;
import com.star4droid.star2d.ui.NumberPad;
import com.star4droid.star2d.ui.IconMenu;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.tutorial.Tut;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Native block editor for visual scripts (.visual + generated .java), replacing the LibGDX
 * node editor. Drag blocks from the palette (hold or swipe toward the canvas), tap a field to
 * type a value or drop value blocks into it, (!) for help. The palette can be resized and the
 * whole editor mirrored (right to left) per project.
 */
public class BlockEditorActivity extends AppCompatActivity implements BlockCanvas.Listener {

    private static String pendingFile, pendingHints;
    private static int pendingBlock = -1;
    private static final java.util.HashMap<String, String> LAST_HINTS = new java.util.HashMap<>();
    public static BlockEditorActivity current;

    /** Open the editor and fly to a block (position in the block list), e.g. from a compiler error. */
    public static boolean open(Activity a, String file, String hints, int block) {
        pendingBlock = block;
        return open(a, file, hints);
    }

    /** Open the editor for a .visual file. Always handled natively. */
    public static boolean open(Activity a, String file, String hints) {
        pendingFile = file;
        if (hints != null && !hints.isEmpty()) LAST_HINTS.put(file, hints);
        pendingHints = hints != null && !hints.isEmpty() ? hints : (LAST_HINTS.containsKey(file) ? LAST_HINTS.get(file) : "");
        Class<?> cls = gameIsLandscape(file) ? BlockEditorLandscapeActivity.class : BlockEditorActivity.class;
        a.runOnUiThread(() -> a.startActivity(new Intent(a, cls)));
        return true;
    }

    /** The scene's orientation (configs/<scene>.json "or": "" / "landscape" = landscape). */
    static boolean gameIsLandscape(String visualFile) {
        try {
            int i = visualFile.indexOf("/Events/");
            if (i < 0) return false;
            String root = visualFile.substring(0, i), rest = visualFile.substring(i + 8);
            String scene = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
            File cfg = new File(root + "/configs/" + scene + ".json");
            if (!cfg.exists()) cfg = new File(root + "/configs/scene1.json");
            if (!cfg.exists()) return true;
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(BlockModel.read(cfg)).getAsJsonObject();
            String or = o.has("or") ? o.get("or").getAsString() : "";
            return or.isEmpty() || or.equals("landscape");
        } catch (Exception e) {
            return false;
        }
    }

    private static final String VALUES_CAT = "ƒ values";

    private File file;
    private String eventName, projectKey;
    private boolean rtl, mirror;
    private BlockModel model;
    private BlockCanvas canvas;
    private final LinkedHashMap<String, ArrayList<String>> hints = new LinkedHashMap<>();
    private final ArrayList<String> variables = new ArrayList<>();
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private boolean changed = false;
    private ImageView undoBtn, redoBtn;
    private String currentCategory;
    private final ArrayList<Object> paletteItems = new ArrayList<>();
    private RecyclerView paletteList;
    private LinearLayout categoryRow, selectionBar, paletteBox, blocksPane, valuePane;
    private EditText search;
    private boolean paletteOpen = true;
    private FrameLayout rootFrame;
    private TextView ghost;
    private View handle;

    // value editing (field tapped)
    private BlockModel.Node editNode;
    private BlockModel.Field editField;
    private EditText valueInput;
    private String valueGroup;
    private final ArrayList<Object> valueItems = new ArrayList<>();
    private RecyclerView valueList;
    private LinearLayout valueGroups;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        com.star4droid.star2d.ui.SystemBars.install(this);
        if (EngineSettings.get() == null) EngineSettings.init(this);
        Tr.init(this);
        rtl = Ui.isRtl(this);
        if (pendingFile == null) {
            finish();
            return;
        }
        current = this;
        file = new File(pendingFile);
        eventName = file.getName().replace(".visual", "");
        String p = file.getAbsolutePath();
        int ev = p.indexOf("/Events/");
        projectKey = ev > 0 ? p.substring(0, ev) : file.getParent();
        mirror = Ui.prefs(this).getBoolean("blocks_mirror:" + projectKey, rtl);
        parseHints(pendingHints);
        loadVariables();
        BlockCatalog.load(this);
        refreshBehaviorBlocks();
        catalogSeen = BlockCatalog.version();
        model = BlockModel.load(file);
        canvas = new BlockCanvas(this, this, rtl);
        canvas.setMirror(mirror);
        canvas.setRootParams(eventParams(file));
        canvas.setModel(model, rootLabel());
        for (Map.Entry<String, ArrayList<BlockCatalog.Def>> ce : BlockCatalog.CATEGORIES.entrySet())
            if (hasVisibleBlocks(ce.getValue())) {
                currentCategory = ce.getKey();
                break;
            }
        build();
        canvas.post(canvas::home);
        runCheck(false);
        if (pendingBlock >= 0) {
            final int target = pendingBlock;
            pendingBlock = -1;
            canvas.postDelayed(() -> {
                BlockModel.Node n = model.nodeAt(target);
                if (n != null) canvas.animateTo(n);
            }, 450);
        }
    }

    // =============================================================== compiler check

    private BlockChecker.Result lastCheck;
    private int checkRun;

    /** Check the saved script in the background and paint the faulty blocks red. */
    private void runCheck(final boolean thenExitDialog) {
        final int run = ++checkRun;
        final File f = file;
        final BlockModel m = model;
        new Thread(() -> {
            final BlockChecker.Result r = BlockChecker.check(f.getAbsolutePath(), m);
            runOnUiThread(() -> {
                if (run != checkRun || isFinishing()) return;
                lastCheck = r;
                applyCheck(r);
                if (thenExitDialog) {
                    if (r.hasErrors()) askAboutErrors();
                    else finish();
                }
            });
        }, "block-check").start();
    }

    private void applyCheck(BlockChecker.Result r) {
        java.util.IdentityHashMap<BlockModel.Node, String> map = new java.util.IdentityHashMap<>();
        for (Map.Entry<Integer, String> e : r.byBlock.entrySet()) {
            BlockModel.Node n = model.nodeAt(e.getKey());
            if (n != null) map.put(n, e.getValue());
        }
        canvas.setErrors(map);
    }

    /** "There are errors in the blocks": Show me / Ignore / Cancel. */
    private void askAboutErrors() {
        int n = lastCheck == null ? 0 : lastCheck.byBlock.size();
        new AlertDialog.Builder(this)
                .setTitle(Tr.t("blk_errors_title", "There are errors in the blocks"))
                .setMessage(Tr.t("blk_errors_msg", "Some blocks have errors and the game may not compile.") + "  (" + n + ")")
                .setPositiveButton(Tr.t("blk_show_me", "Show me"), (d, w) -> showFirstError())
                .setNegativeButton(Tr.t("blk_ignore", "Ignore"), (d, w) -> finish())
                .setNeutralButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    private void showFirstError() {
        if (lastCheck == null || lastCheck.byBlock.isEmpty()) return;
        Integer first = lastCheck.byBlock.keySet().iterator().next();
        BlockModel.Node node = model.nodeAt(first);
        if (node != null) canvas.animateTo(node);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBehaviorBlocks();
        // blocks made in the block creator appear right away
        if (catalogSeen != BlockCatalog.version() && paletteList != null) {
            catalogSeen = BlockCatalog.version();
            build();
        }
    }

    private int catalogSeen = -1;

    /**
     * Behavior blocks (@Block methods): rebuilt from the project's behaviors, and shown only for the behaviors the scene of this
     * event uses (or that this event already uses / adds with addBehavior).
     */
    private void refreshBehaviorBlocks() {
        // 2D or 3D scene (configs/<scene>.json "sceneType"): the palette shows only the blocks made for it
        try {
            BlockCatalog.setSceneMode(com.star4droid.star2d.blocks.SceneDims.modeOfEventPath(file.getAbsolutePath()));
        } catch (Throwable ignored) {}
        try {
            BlockCatalog.applyBehaviorBlocks(projectKey);
            java.util.HashSet<String> ids = new java.util.HashSet<>(com.star4droid.star2d.blocks.SceneBehaviors.inProject(projectKey,
                    com.star4droid.star2d.blocks.SceneBehaviors.sceneOfEventPath(file.getAbsolutePath())));
            try {
                ids.addAll(com.star4droid.star2d.blocks.SceneBehaviors.mentionedIn(BlockModel.read(file), BlockCatalog.behaviorsWithBlocks(projectKey)));
            } catch (Exception ignored) {}
            BlockCatalog.setActiveBehaviors(ids);
        } catch (Throwable ignored) {}
    }

    private static boolean hasVisibleBlocks(ArrayList<BlockCatalog.Def> l) {
        for (BlockCatalog.Def d : l) if (BlockCatalog.isVisible(d)) return true;
        return false;
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    /** For tutorials. */
    public BlockCanvas canvas() { return canvas; }
    public BlockModel model() { return model; }

    // =============================================================== data

    private void parseHints(String all) {
        hints.clear();
        String section = null;
        for (String line : all.split("\n")) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("- ")) {
                section = l.substring(2).trim();
                if (!hints.containsKey(section)) hints.put(section, new ArrayList<>());
            } else if (section != null) hints.get(section).add(l);
        }
        try (java.io.InputStream in = getAssets().open("java/hints.java")) {
            String txt = new String(BlockCatalog.readAll(in), "UTF-8");
            String sec = null;
            for (String line : txt.split("\n")) {
                String l = line.trim();
                if (l.isEmpty()) continue;
                if (l.startsWith("- ")) {
                    sec = "ƒ " + l.substring(2).trim();
                    hints.put(sec, new ArrayList<>());
                } else if (sec != null) hints.get(sec).add(l);
            }
        } catch (Exception ignored) {}
    }

    private void loadVariables() {
        variables.clear();
        String p = file.getAbsolutePath();
        int i = p.indexOf("/Events/");
        if (i < 0) return;
        String root = p.substring(0, i);
        String rest = p.substring(i + 8);
        String scene = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        File vf = new File(root + "/Events/" + scene + "/public/variables.json");
        try {
            if (vf.exists()) {
                ArrayList<VarModel> l = new Gson().fromJson(BlockModel.read(vf), new TypeToken<ArrayList<VarModel>>() {}.getType());
                if (l != null) for (VarModel m : l) variables.add(m.getName());
            }
        } catch (Exception ignored) {}
        // object variables of the element: the ones of its properties (sent by the editor as hints) and the ones
        // created by "object variable" blocks in any of its events (all events of the element share them)
        if (hints.containsKey(OBJECT_VARS_HINT))
            for (String n : hints.get(OBJECT_VARS_HINT)) if (!variables.contains(n)) variables.add(n);
        if (isElementEventPath(p) && file.getParentFile() != null) {
            File[] evs = file.getParentFile().listFiles();
            if (evs != null) for (File f : evs) {
                if (!f.getName().endsWith(".java")) continue;
                try {
                    for (com.star4droid.star2d.behaviors.ObjectVarsGen.Var v : com.star4droid.star2d.behaviors.ObjectVarsGen.fromCode(BlockModel.read(f)))
                        if (!variables.contains(v.name)) variables.add(v.name);
                } catch (Exception ignored) {}
            }
        }
    }

    /** Hints section with the element's object variables (VisualScriptingDialog). */
    public static final String OBJECT_VARS_HINT = "Object variables";

    // =============================================================== layout

    private void build() {
        Context c = this;
        boolean land = Ui.isLandscape(c);
        rootFrame = new FrameLayout(c);
        rootFrame.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(c);
        root.setLayoutDirection(mirror ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        rootFrame.addView(root, Ui.match());

        // top bar
        LinearLayout top = Ui.row(c);
        top.setBackgroundColor(0xFF181A1E);
        top.setPadding(Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3));
        ImageView back = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("close", "Close"), v -> onBackPressed());
        back.setScaleX(mirror ? -1 : 1);
        Tut.tag(back, "blocks:back");
        top.addView(back);
        LinearLayout titles = Ui.column(c);
        titles.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        TextView t = Ui.text(c, eventTitle(), 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(t);
        TextView sub = Ui.text(c, Tr.t("visualScripting", "Blocks"), 11.5f, Ui.TEXT_3);
        sub.setMaxLines(1);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(sub);
        top.addView(titles, Ui.weight(1));
        undoBtn = Ui.iconButton(c, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> undo());
        redoBtn = Ui.iconButton(c, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> redo());
        top.addView(undoBtn);
        top.addView(redoBtn);
        ImageView flip = Ui.iconButton(c, R.drawable.ic_s2_flip, Tr.t("blk_mirror", "Mirror blocks (right to left)"), v -> toggleMirror());
        flip.setImageTintList(ColorStateList.valueOf(mirror ? Ui.ACCENT : Ui.TEXT_2));
        top.addView(flip);
        top.addView(Ui.iconButton(c, R.drawable.ic_s2_code, Tr.t("ui_show_code", "Show code"), v ->
                Dialogs.longText(c, Tr.t("ui_generated_code", "Generated code"), model.exportCode())));
        ImageView pal = Ui.iconButton(c, R.drawable.ic_s2_blocks, Tr.t("ui_palette", "Blocks palette"), v -> togglePalette());
        Tut.tag(pal, "blocks:palette");
        top.addView(pal);
        ImageView save = Ui.iconButton(c, R.drawable.ic_s2_save, Tr.t("save", "Save"), v -> save(false));
        save.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        Tut.tag(save, "blocks:save");
        top.addView(save);
        Ui.fitIconRow(c, top, 64); // all 6 buttons stay on screen at any icon size; the title keeps 64 dp
        root.addView(top, Ui.matchWrap());

        // body
        LinearLayout body = land ? Ui.row(c) : Ui.column(c);
        View palette = buildPalette(c);
        FrameLayout canvasBox = new FrameLayout(c);
        if (canvas.getParent() != null) ((ViewGroup) canvas.getParent()).removeView(canvas);
        canvasBox.addView(canvas, Ui.match());
        Tut.tag(canvas, "blocks:canvas");
        LinearLayout zoom = Ui.column(c);
        zoom.setBackground(Ui.rounded(0xCC202328, Ui.dp(c, 10), Ui.BORDER, 1));
        zoom.addView(Ui.iconButton(c, R.drawable.ic_s2_add, "Zoom in", v -> canvas.zoomBy(1.2f)));
        TextView minus = Ui.text(c, "—", 18, Ui.TEXT);
        minus.setGravity(Gravity.CENTER);
        int s = Ui.dp(c, Ui.buttonDp(c));
        minus.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        minus.setOnClickListener(v -> canvas.zoomBy(1 / 1.2f));
        zoom.addView(minus);
        zoom.addView(Ui.iconButton(c, R.drawable.ic_s2_home, Tr.t("ui_go_start", "Go to start"), v -> canvas.home()));
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(-2, -2);
        zl.gravity = Gravity.TOP | Gravity.END;
        zl.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), 0);
        canvasBox.addView(zoom, zl);

        selectionBar = Ui.row(c);
        selectionBar.setBackground(Ui.rounded(0xF0202328, Ui.dp(c, 14), Ui.BORDER, 1));
        selectionBar.setPadding(Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2));
        selectionBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2);
        sl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sl.bottomMargin = Ui.dp(c, 12);
        canvasBox.addView(selectionBar, sl);

        handle = buildHandle(c, land);
        if (land) {
            int w = Ui.prefs(c).getInt("blocks_pal_w", Ui.dp(c, 280));
            body.addView(palette, new LinearLayout.LayoutParams(clampW(w), ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(handle, new LinearLayout.LayoutParams(Ui.dp(c, 14), ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(canvasBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        } else {
            int h = Ui.prefs(c).getInt("blocks_pal_h", (int) (getResources().getDisplayMetrics().heightPixels * 0.38f));
            body.addView(canvasBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            body.addView(handle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 16)));
            body.addView(palette, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, clampH(h)));
        }
        palette.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
        handle.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ghost = Ui.text(c, "", 13, 0xFFFFFFFF);
        ghost.setTypeface(Typeface.DEFAULT_BOLD);
        ghost.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        ghost.setVisibility(View.GONE);
        ghost.setElevation(Ui.dp(c, 12));
        rootFrame.addView(ghost, new FrameLayout.LayoutParams(-2, -2));

        setContentView(rootFrame);
        Ui.insets(this, 0xFF181A1E);
        refreshUndo();
        if (editField != null) showValuePane();
    }

    private int clampH(int h) {
        int sh = getResources().getDisplayMetrics().heightPixels;
        return Math.max(Ui.dp(this, 150), Math.min(h, (int) (sh * 0.75f)));
    }

    private int clampW(int w) {
        int sw = getResources().getDisplayMetrics().widthPixels;
        return Math.max(Ui.dp(this, 200), Math.min(w, (int) (sw * 0.7f)));
    }

    /** Grip between the canvas and the palette: drag to resize the palette. */
    @SuppressLint("ClickableViewAccessibility")
    private View buildHandle(Context c, boolean land) {
        FrameLayout h = new FrameLayout(c);
        h.setBackgroundColor(0xFF1C1F24);
        View grip = new View(c);
        grip.setBackground(Ui.rounded(0xFF5A626D, Ui.dp(c, 3)));
        FrameLayout.LayoutParams gl = land ? new FrameLayout.LayoutParams(Ui.dp(c, 4), Ui.dp(c, 40)) : new FrameLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 4));
        gl.gravity = Gravity.CENTER;
        h.addView(grip, gl);
        h.setContentDescription(Tr.t("blk_resize", "Drag to resize the palette"));
        Tut.tag(h, "blocks:handle");
        final float[] start = new float[2];
        h.setOnTouchListener((v, e) -> {
            ViewGroup.LayoutParams lp = paletteBox.getLayoutParams();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = land ? e.getRawX() : e.getRawY();
                    start[1] = land ? lp.width : lp.height;
                    grip.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(c, 3)));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (land) {
                        float dx = e.getRawX() - start[0];
                        // grow toward the handle's side: the handle is right of the palette in LTR, left of it in RTL
                        if (v.getX() < paletteBox.getX()) dx = -dx;
                        lp.width = clampW((int) (start[1] + dx));
                    } else {
                        lp.height = clampH((int) (start[1] - (e.getRawY() - start[0])));
                    }
                    paletteBox.setLayoutParams(lp);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    grip.setBackground(Ui.rounded(0xFF5A626D, Ui.dp(c, 3)));
                    Ui.prefs(c).edit().putInt(land ? "blocks_pal_w" : "blocks_pal_h", land ? lp.width : lp.height).apply();
                    return true;
            }
            return true;
        });
        return h;
    }

    private void togglePalette() {
        paletteOpen = !paletteOpen;
        paletteBox.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
        handle.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
    }

    private void toggleMirror() {
        commitValue();
        mirror = !mirror;
        Ui.prefs(this).edit().putBoolean("blocks_mirror:" + projectKey, mirror).apply();
        canvas.setMirror(mirror);
        build();
        Snack.show(mirror ? Tr.t("blk_mirror_on", "Blocks: right to left") : Tr.t("blk_mirror_off", "Blocks: left to right"));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        commitValue();
        build();
    }

    // =============================================================== palette

    private View buildPalette(Context c) {
        paletteBox = Ui.column(c);
        paletteBox.setBackgroundColor(0xFF1C1F24);
        blocksPane = Ui.column(c);
        blocksPane.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), 0);
        search = Ui.input(c, "", Tr.t("ui_search_blocks", "Search blocks… (e.g. jump, sound)"));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { fillPalette(); }
        });
        LinearLayout searchRow = Ui.row(c);
        searchRow.addView(search, Ui.weight(1));
        ImageView create = Ui.iconButton(c, R.drawable.ic_s2_wand, Tr.t("bc_title", "Block creator"), v -> BlockCreatorActivity.open(this));
        create.setImageTintList(ColorStateList.valueOf(0xFFFFCA28));
        searchRow.addView(create);
        blocksPane.addView(searchRow, Ui.matchWrap());
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        categoryRow = Ui.row(c);
        categoryRow.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        hs.addView(categoryRow);
        blocksPane.addView(hs, Ui.matchWrap());
        ArrayList<String> cats = new ArrayList<>();
        for (Map.Entry<String, ArrayList<BlockCatalog.Def>> ce : BlockCatalog.CATEGORIES.entrySet()) if (hasVisibleBlocks(ce.getValue())) cats.add(ce.getKey());
        cats.add(1 < cats.size() ? 1 : cats.size(), VALUES_CAT);
        for (String cat : cats) {
            String name = cat.equals(VALUES_CAT) ? "ƒ " + Tr.t("blk_values", "Values") : BlockInfo.category(cat, rtl);
            TextView chip = Ui.text(c, name, 12.5f, Ui.TEXT);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            chip.setTag(cat);
            Tut.tag(chip, "blocks:cat:" + cat);
            chip.setOnClickListener(v -> {
                currentCategory = cat;
                search.setText("");
                fillPalette();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(c, 5));
            categoryRow.addView(chip, lp);
        }
        TextView hint = Ui.text(c, Tr.t("blk_palette_hint", "Tap to add · hold, then drag onto the canvas · (!) for help"), 11, Ui.TEXT_3);
        hint.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 4));
        if (!Ui.isLandscape(c)) blocksPane.addView(hint);
        paletteList = new RecyclerView(c);
        paletteList.setLayoutManager(new LinearLayoutManager(c));
        paletteList.setAdapter(new PaletteAdapter(paletteItems));
        blocksPane.addView(paletteList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        paletteBox.addView(blocksPane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        valuePane = Ui.column(c);
        valuePane.setVisibility(View.GONE);
        paletteBox.addView(valuePane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        fillPalette();
        return paletteBox;
    }

    private void fillPalette() {
        paletteItems.clear();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ArrayList<BlockCatalog.Def>> e : BlockCatalog.CATEGORIES.entrySet()) {
            for (BlockCatalog.Def d : e.getValue()) {
                if (!BlockCatalog.isVisible(d)) continue;
                if (q.isEmpty()) {
                    if (e.getKey().equals(currentCategory)) paletteItems.add(d);
                } else {
                    String hay = (d.title + " " + BlockInfo.label(d.title, true) + " " + BlockInfo.label(d.title, false)).toLowerCase(Locale.ROOT);
                    if (hay.contains(q)) paletteItems.add(d);
                }
            }
        }
        if (VALUES_CAT.equals(currentCategory) || !q.isEmpty()) {
            String lastGroup = null;
            for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) {
                if (!BlockCatalog.isVisible(r)) continue;
                if (!q.isEmpty() && !(r.display + " " + r.group).toLowerCase(Locale.ROOT).contains(q)) continue;
                if (!r.group.equals(lastGroup)) {
                    paletteItems.add(r.group);
                    lastGroup = r.group;
                }
                paletteItems.add(r);
            }
        }
        for (int i = 0; i < categoryRow.getChildCount(); i++) {
            TextView chip = (TextView) categoryRow.getChildAt(i);
            String cat = (String) chip.getTag();
            boolean sel = q.isEmpty() && cat.equals(currentCategory);
            int col = cat.equals(VALUES_CAT) ? 0xFF00838F : BlockCatalog.colorOf(cat);
            chip.setBackground(sel ? Ui.rounded(col, Ui.dp(this, 14)) : Ui.rounded(0x00000000, Ui.dp(this, 14), col, Ui.dp(this, 1.5f)));
        }
        if (paletteList != null && paletteList.getAdapter() != null) paletteList.getAdapter().notifyDataSetChanged();
    }

    /** Value block background shapes (same as on the canvas). */
    static class ShapeBg extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int shape;
        final float r;

        ShapeBg(int color, int shape, float r) {
            p.setColor(color);
            this.shape = shape;
            this.r = r;
        }

        @Override
        public void draw(@NonNull Canvas c) {
            Rect b = getBounds();
            float h = b.height();
            if (shape == BlockCanvas.SHAPE_HEX) {
                float k = h * 0.42f;
                Path path = new Path();
                path.moveTo(b.left, b.centerY());
                path.lineTo(b.left + k, b.top);
                path.lineTo(b.right - k, b.top);
                path.lineTo(b.right, b.centerY());
                path.lineTo(b.right - k, b.bottom);
                path.lineTo(b.left + k, b.bottom);
                path.close();
                c.drawPath(path, p);
            } else {
                float rr = shape == BlockCanvas.SHAPE_PILL ? h / 2 : r;
                c.drawRoundRect(b.left, b.top, b.right, b.bottom, rr, rr, p);
            }
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static int shapeOf(BlockCatalog.Reporter r) {
        return BlockCatalog.isBooleanTemplate(r.template) ? BlockCanvas.SHAPE_HEX
                : (BlockCatalog.isTextTemplate(r.template) ? BlockCanvas.SHAPE_RECT : BlockCanvas.SHAPE_PILL);
    }

    static String reporterText(BlockCatalog.Reporter r) {
        return BlockInfo.reporter(r.display).replace("___", " ◯ ").replace("  ", " ").trim();
    }

    /** Palette rows: statement blocks (Def), value blocks (Reporter) and group headers (String). */
    private class PaletteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final ArrayList<Object> items;

        PaletteAdapter(ArrayList<Object> items) { this.items = items; }

        @Override
        public int getItemViewType(int position) {
            Object o = items.get(position);
            return o instanceof String ? 2 : (o instanceof BlockCatalog.Reporter ? 1 : 0);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            if (viewType == 2) {
                TextView h = Ui.text(c, "", 11.5f, Ui.TEXT_3);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 2));
                h.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(h) {};
            }
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 2), Ui.dp(c, 3), Ui.dp(c, 2), Ui.dp(c, 3));
            TextView block = Ui.text(c, "", 13, 0xFFFFFFFF);
            block.setTypeface(Typeface.DEFAULT_BOLD);
            block.setPadding(Ui.dp(c, viewType == 1 ? 16 : 12), Ui.dp(c, 9), Ui.dp(c, viewType == 1 ? 16 : 12), Ui.dp(c, 9));
            block.setMaxLines(2);
            if (viewType == 1) row.addView(block, new LinearLayout.LayoutParams(-2, -2));
            else row.addView(block, Ui.weight(1));
            if (viewType == 1) row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
            TextView info = Ui.text(c, "!", 15, Ui.TEXT);
            info.setTypeface(Typeface.DEFAULT_BOLD);
            info.setGravity(Gravity.CENTER);
            info.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, 16)));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32));
            il.setMarginStart(Ui.dp(c, 6));
            row.addView(info, il);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(row) {};
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            Object o = items.get(pos);
            Context c = h.itemView.getContext();
            if (o instanceof String) {
                ((TextView) h.itemView).setText(groupName((String) o));
                return;
            }
            LinearLayout row = (LinearLayout) h.itemView;
            TextView block = (TextView) row.getChildAt(0);
            TextView info = (TextView) row.getChildAt(row.getChildCount() - 1);
            if (o instanceof BlockCatalog.Reporter) {
                BlockCatalog.Reporter r = (BlockCatalog.Reporter) o;
                block.setText(reporterText(r));
                block.setBackground(new ShapeBg(BlockCatalog.groupColor(r.group), shapeOf(r), Ui.dp(c, 6)));
                info.setOnClickListener(v -> showReporterInfo(r));
                block.setOnClickListener(v -> putReporter(r));
                block.setOnTouchListener(new PaletteDrag(null, r));
                Tut.tag(block, "blocks:value:" + r.display);
                return;
            }
            BlockCatalog.Def d = (BlockCatalog.Def) o;
            StringBuilder sb = new StringBuilder(BlockInfo.label(d.title, rtl));
            for (String f : d.fields) sb.append("  [").append(BlockInfo.field(BlockModel.Field.labelOf(f), rtl)).append("]");
            if (d.isIf) sb.append("  ⤵");
            block.setText(sb.toString());
            int col = BlockCatalog.colorOf(d.category);
            block.setBackground(Ui.rounded(col, Ui.dp(c, 8)));
            info.setOnClickListener(v -> showInfo(d.title, d));
            block.setOnClickListener(v -> addBlock(d));
            block.setOnTouchListener(new PaletteDrag(d, null));
            Tut.tag(block, "blocks:def:" + d.title);
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private String groupName(String g) {
        return Tr.t("blk_group_" + g.toLowerCase(Locale.ROOT).replace(" / ", "_").replace(' ', '_'), g);
    }

    /**
     * Drag from the palette onto the canvas: long-press the block, then drag (same as blocks on the canvas).
     * A swipe before the long press scrolls the list; a tap adds the block.
     */
    private class PaletteDrag implements View.OnTouchListener {
        final BlockCatalog.Def def;
        final BlockCatalog.Reporter rep;
        float downX, downY, lastX, lastY;
        boolean dragging, armed;
        View view;
        final Runnable hold = () -> {
            if (armed && !dragging && view != null) start(view);
        };

        PaletteDrag(BlockCatalog.Def d, BlockCatalog.Reporter r) {
            def = d;
            rep = r;
        }

        private float[] toCanvas(float rx, float ry) {
            int[] loc = new int[2];
            canvas.getLocationOnScreen(loc);
            return new float[]{rx - loc[0], ry - loc[1]};
        }

        private void start(View v) {
            dragging = true;
            armed = false;
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null) p.requestDisallowInterceptTouchEvent(true);
            v.setPressed(false);
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            float[] cp = toCanvas(lastX, lastY);
            if (def != null) canvas.beginExternalDrag(def.create(), cp[0], cp[1]);
            else canvas.beginExternalParamDrag(new BlockModel.Param(rep.template, rep.display), cp[0], cp[1]);
            ghost.setText(((TextView) v).getText());
            ghost.setBackground(def != null ? Ui.rounded(BlockCatalog.colorOf(def.category), Ui.dp(v.getContext(), 8))
                    : new ShapeBg(BlockCatalog.groupColor(rep.group), shapeOf(rep), Ui.dp(v.getContext(), 6)));
            moveGhost();
        }

        private void moveGhost() {
            int[] cl = new int[2], rl = new int[2];
            canvas.getLocationOnScreen(cl);
            rootFrame.getLocationOnScreen(rl);
            boolean inCanvas = lastX >= cl[0] && lastX <= cl[0] + canvas.getWidth() && lastY >= cl[1] && lastY <= cl[1] + canvas.getHeight();
            ghost.setVisibility(inCanvas ? View.GONE : View.VISIBLE);
            ghost.setX(lastX - rl[0] - Ui.dp(ghost.getContext(), 20));
            ghost.setY(lastY - rl[1] - Ui.dp(ghost.getContext(), 50));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            lastX = e.getRawX();
            lastY = e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view = v;
                    downX = lastX;
                    downY = lastY;
                    dragging = false;
                    armed = true;
                    handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) {
                        float dx = lastX - downX, dy = lastY - downY;
                        float slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                            armed = false;
                            handler.removeCallbacks(hold);
                            // blocks are dragged only after a long press; any earlier move scrolls the list
                        }
                        return dragging;
                    }
                    float[] cp = toCanvas(lastX, lastY);
                    canvas.externalMove(cp[0], cp[1]);
                    moveGhost();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    armed = false;
                    handler.removeCallbacks(hold);
                    if (dragging) {
                        float[] up = toCanvas(lastX, lastY);
                        canvas.externalUp(up[0], up[1], e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                        dragging = false;
                        ghost.setVisibility(View.GONE);
                        v.setPressed(false);
                        return true;
                    }
                    return false;
            }
            return dragging;
        }
    }

    private void addBlock(BlockCatalog.Def d) {
        canvas.insert(d.create());
        Snack.show(BlockInfo.label(d.title, rtl) + " ✓");
    }

    // =============================================================== value pane (a field is being edited)

    @Override
    public void onFieldTap(BlockModel.Node node, BlockModel.Field field) {
        commitValue();
        editNode = node;
        editField = field;
        canvas.setActiveField(field);
        onSelect(node);
        if (!paletteOpen) togglePalette();
        showValuePane();
    }

    private void closeValuePane() {
        commitValue();
        editField = null;
        editNode = null;
        canvas.setActiveField(null);
        hideKeyboard();
        if (valuePane != null) {
            valuePane.setVisibility(View.GONE);
            blocksPane.setVisibility(View.VISIBLE);
        }
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
    }

    /** Apply the typed value to the field being edited (one undo step). */
    private void commitValue() {
        if (editField == null || valueInput == null) return;
        String nv = valueInput.getText().toString();
        if (isGlsl(editField)) nv = escapeGlsl(nv);
        if (nv.equals(editField.value)) return;
        beforeChange();
        editField.value = nv;
        if (!nv.isEmpty()) editField.block = null; // a typed value replaces the value block
        canvas.changed();
        afterChange();
    }

    /** A shader code field ("code(glsl)"): typed on several lines, kept as the inside of a Java string. */
    static boolean isGlsl(BlockModel.Field f) {
        return f != null && "glsl".equalsIgnoreCase(f.type());
    }

    static String escapeGlsl(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\') sb.append("\\\\");
            else if (ch == '"') sb.append("\\\"");
            else if (ch == '\n') sb.append("\\n");
            else if (ch != '\r') sb.append(ch);
        }
        return sb.toString();
    }

    static String unescapeGlsl(String v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '\\' && i + 1 < v.length()) {
                char n = v.charAt(++i);
                sb.append(n == 'n' ? '\n' : n);
            } else sb.append(ch);
        }
        return sb.toString();
    }

    private static final java.util.HashSet<String> NUMERIC = new java.util.HashSet<>(java.util.Arrays.asList(
            "x", "y", "dx", "dy", "speed", "power", "seconds", "delay", "times", "by", "strength", "degrees", "alpha",
            "scale", "scaleX", "scaleY", "zoom", "angle", "velocity", "xVelocity", "yVelocity", "impulse", "torque",
            "offsetX", "offsetY", "GravityScale", "progress", "max", "splits", "step", "pointX", "pointY", "frequency",
            "ratio", "torque", "ms", "repeat"));

    /** The value block that holds this field, or null for a field of the block itself. */
    private static BlockModel.Param parentOf(BlockModel.Node n, BlockModel.Field f) {
        if (n == null) return null;
        for (BlockModel.Field x : n.fields) {
            BlockModel.Param p = parentIn(x, f);
            if (p != null) return p;
        }
        return null;
    }

    private static BlockModel.Param parentIn(BlockModel.Field x, BlockModel.Field f) {
        if (x.block == null) return null;
        for (BlockModel.Field i : x.block.inner) {
            if (i == f) return x.block;
            BlockModel.Param p = parentIn(i, f);
            if (p != null) return p;
        }
        return null;
    }

    /** Fields that take a number get the keypad. */
    private boolean isNumericField(BlockModel.Node n, BlockModel.Field f) {
        if (!f.type().isEmpty()) return false;
        if (!f.value.trim().isEmpty()) return NumberPad.isNumber(f.value);
        if (NUMERIC.contains(f.label())) return true;
        if (f.name.equals("___")) {
            BlockModel.Param p = parentOf(n, f);
            String g = p == null ? null : BlockCatalog.groupOf(p.template);
            return g != null && (g.equals("Math") || g.equals("Operators") || g.equals("Convert"));
        }
        return false;
    }

    private void openPad(String label) {
        if (valueInput == null) return;
        EditText input = valueInput;
        NumberPad.show(this, label, input.getText().toString(), v -> {
            input.setText(v);
            commitValue();
        }, () -> {
            // type text instead (a variable name, an expression…)
            input.setFocusableInTouchMode(true);
            input.setFocusable(true);
            input.setOnClickListener(null);
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    /** Project folder to browse for this field, or null. */
    private static String browseFolder(BlockModel.Field f) {
        String t = f.type().toLowerCase(Locale.ROOT), l = f.label().toLowerCase(Locale.ROOT);
        switch (t) {
            case "image": return "images";
            case "sound": return "sounds";
            case "animation": return "anims";
            case "scene": return "scenes";
            case "model": return "models";
            case "shader": return "shaders";
        }
        if (l.equals("path") || l.equals("file")) return "files";
        if (l.equals("image")) return "images";
        if (l.equals("sound")) return "sounds";
        return null;
    }

    private void browse(BlockModel.Field f, String folder, String title) {
        File root = new File(projectKey, folder);
        String[] ext = folder.equals("images") ? new String[]{"png", "jpg", "jpeg"}
                : folder.equals("sounds") ? new String[]{"mp3", "ogg", "wav", "m4a"}
                : folder.equals("models") ? new String[]{"glb", "gltf", "obj"}
                : folder.equals("shaders") ? new String[]{"glsl"} : null;
        FilePickerDialog.show(this, title, root.getAbsolutePath(), ext, folder.equals("images"), (file, rel) -> {
            String v = folder.equals("anims") || folder.equals("scenes") ? file.getName() : rel;
            if (folder.equals("shaders")) v = "shaders/" + (rel.endsWith(".glsl") ? rel.substring(0, rel.length() - 5) : rel);
            if (folder.equals("files")) v = "\"" + new File(root, rel).getAbsolutePath().replace("\"", "") + "\"";
            if (valueInput != null && editField == f) {
                valueInput.setText(v);
                commitValue();
            } else tutorialSetField(f, v);
        });
    }

    private boolean isCodeField(BlockModel.Node node, BlockModel.Field f) {
        return f.label().equals("code") || isGlsl(f) || (node != null && (node.title.equals("addSource") || node.title.equals("codeLine")));
    }

    private void showValuePane() {
        Context c = this;
        BlockModel.Field field = editField;
        if (field == null || valuePane == null) return;
        blocksPane.setVisibility(View.GONE);
        valuePane.setVisibility(View.VISIBLE);
        valuePane.removeAllViews();
        valuePane.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), 0);
        boolean code = isCodeField(editNode, field);
        String label = field.name.equals("___") ? Tr.t("blk_value", "Value") : BlockInfo.field(field.label(), rtl);

        // header
        LinearLayout head = Ui.row(c);
        head.addView(Ui.smallIcon(c, R.drawable.ic_s2_keyboard, Ui.ACCENT, 18));
        TextView title = Ui.text(c, label + (field.type().isEmpty() ? "" : "  ·  " + field.type()), 13.5f, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        title.setMaxLines(1);
        head.addView(title, Ui.weight(1));
        ImageView close = Ui.iconButton(c, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> closeValuePane());
        Tut.tag(close, "blocks:value_close");
        head.addView(close);
        valuePane.addView(head, Ui.matchWrap());

        // typed value
        LinearLayout in = Ui.row(c);
        valueInput = Ui.input(c, isGlsl(field) ? unescapeGlsl(field.value) : field.value, field.block != null ? "ƒ " + field.block.text() : label);
        Tut.tag(valueInput, "blocks:value_input");
        if (code) {
            valueInput.setSingleLine(false);
            valueInput.setMinLines(2);
            valueInput.setMaxLines(5);
            valueInput.setTypeface(Typeface.MONOSPACE);
            valueInput.setGravity(Gravity.TOP | Gravity.START);
            valueInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else {
            valueInput.setSingleLine(true);
            valueInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            valueInput.setOnEditorActionListener((v, a, ev) -> {
                commitValue();
                hideKeyboard();
                return true;
            });
        }
        in.addView(valueInput, Ui.weight(1));
        ImageView ok = Ui.iconButton(c, R.drawable.ic_s2_check, Tr.t("ok", "OK"), v -> {
            commitValue();
            hideKeyboard();
        });
        ok.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        Tut.tag(ok, "blocks:value_ok");
        LinearLayout.LayoutParams okl = new LinearLayout.LayoutParams(-2, -2);
        okl.setMarginStart(Ui.dp(c, 6));
        in.addView(ok, okl);
        // numbers: a keypad dialog instead of the keyboard
        if (!code && isNumericField(editNode, field)) {
            valueInput.setFocusable(false);
            valueInput.setOnClickListener(v -> openPad(label));
            valueInput.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_dialpad, 0);
            valueInput.setCompoundDrawableTintList(ColorStateList.valueOf(Ui.TEXT_3));
        }
        // files: browse the right folder of the project
        String folder = browseFolder(field);
        if (folder != null) {
            ImageView br = Ui.iconButton(c, R.drawable.ic_s2_folder_open, Tr.t("blk_browse", "Browse"), v -> browse(field, folder, label));
            br.setImageTintList(ColorStateList.valueOf(0xFFFFCA28));
            Tut.tag(br, "blocks:value_browse");
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
            bl.setMarginStart(Ui.dp(c, 4));
            in.addView(br, bl);
        }
        valuePane.addView(in, Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 0));

        if (field.block != null) {
            LinearLayout br = Ui.row(c);
            TextView bt = Ui.text(c, "ƒ " + field.block.text(), 12.5f, 0xFF80DEEA);
            bt.setMaxLines(1);
            br.addView(bt, Ui.weight(1));
            TextView rm = Ui.text(c, Tr.t("blk_remove_value", "Remove value block"), 12.5f, 0xFFFF8A80);
            rm.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            rm.setBackground(Ui.ripple(0, Ui.dp(c, 12)));
            rm.setOnClickListener(v -> {
                beforeChange();
                editField.block = null;
                canvas.changed();
                afterChange();
                showValuePane();
            });
            br.addView(rm);
            valuePane.addView(br, Ui.margins(Ui.matchWrap(), c, 2, 4, 0, 0));
        }

        // quick values (elements, variables, images…)
        LinkedHashMap<String, ArrayList<String>> sugg = suggestionsFor(field.type(), field.label());
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(c);
        chips.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
        for (Map.Entry<String, ArrayList<String>> e : sugg.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            TextView g = Ui.text(c, e.getKey() + ":", 11, Ui.TEXT_3);
            g.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            chips.addView(g);
            for (String v : e.getValue()) {
                TextView ch = Ui.text(c, v, 12.5f, Ui.TEXT);
                Tut.tag(ch, "blocks:chip:" + v);
                ch.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
                ch.setBackground(Ui.ripple(Ui.SURFACE_3, Ui.dp(c, 14)));
                ch.setOnClickListener(x -> {
                    String cur = valueInput.getText().toString();
                    boolean append = v.startsWith(".") || v.endsWith("(") || code;
                    valueInput.setText(append ? cur + v : v);
                    valueInput.setSelection(valueInput.getText().length());
                    if (!append) commitValue();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMarginEnd(Ui.dp(c, 5));
                chips.addView(ch, lp);
            }
        }
        hs.addView(chips);
        if (chips.getChildCount() > 0) valuePane.addView(hs, Ui.matchWrap());

        // value blocks: tap = put in this field, hold + drag = drop onto any field
        TextView vh = Ui.text(c, Tr.t("blk_values_hint", "Value blocks: tap to put it here, or hold and drag it onto any field"), 11, Ui.TEXT_3);
        vh.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), 0, Ui.dp(c, 2));
        valuePane.addView(vh);
        HorizontalScrollView gs = new HorizontalScrollView(c);
        gs.setHorizontalScrollBarEnabled(false);
        valueGroups = Ui.row(c);
        valueGroups.setPadding(0, Ui.dp(c, 3), 0, Ui.dp(c, 3));
        ArrayList<String> groups = new ArrayList<>();
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) if (BlockCatalog.isVisible(r) && !groups.contains(r.group)) groups.add(r.group);
        if (valueGroup == null || !groups.contains(valueGroup)) valueGroup = preferredGroup(field, groups);
        for (String g : groups) {
            TextView chip = Ui.text(c, groupName(g), 12, Ui.TEXT);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
            chip.setTag(g);
            Tut.tag(chip, "blocks:group:" + g);
            chip.setOnClickListener(v -> {
                valueGroup = g;
                fillValues();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(c, 5));
            valueGroups.addView(chip, lp);
        }
        gs.addView(valueGroups);
        valuePane.addView(gs, Ui.matchWrap());
        valueList = new RecyclerView(c);
        valueList.setLayoutManager(new LinearLayoutManager(c));
        valueList.setAdapter(new PaletteAdapter(valueItems));
        valuePane.addView(valueList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        fillValues();
    }

    private String preferredGroup(BlockModel.Field f, ArrayList<String> groups) {
        String t = f.type().toLowerCase(Locale.ROOT);
        String want = t.equals("boolean") ? "Logic" : (t.equals("body") ? "Elements" : "Operators");
        if (groups.contains(want)) return want;
        return groups.isEmpty() ? null : groups.get(0);
    }

    private void fillValues() {
        valueItems.clear();
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) if (r.group.equals(valueGroup) && BlockCatalog.isVisible(r)) valueItems.add(r);
        for (int i = 0; i < valueGroups.getChildCount(); i++) {
            TextView chip = (TextView) valueGroups.getChildAt(i);
            String g = (String) chip.getTag();
            int col = BlockCatalog.groupColor(g);
            chip.setBackground(g.equals(valueGroup) ? Ui.rounded(col, Ui.dp(this, 14)) : Ui.rounded(0, Ui.dp(this, 14), col, Ui.dp(this, 1.5f)));
        }
        if (valueList.getAdapter() != null) valueList.getAdapter().notifyDataSetChanged();
    }

    /** Tap on a value block: put it into the field being edited (or tell how to use it). */
    private void putReporter(BlockCatalog.Reporter r) {
        if (editField == null) {
            Snack.show(Tr.t("blk_tap_field_first", "Tap a field in a block first, or hold and drag this value onto a field."));
            return;
        }
        beforeChange();
        BlockModel.Param p = new BlockModel.Param(r.template, r.display);
        editField.block = p;
        editField.value = "";
        canvas.changed();
        afterChange();
        // continue with the first empty inner field
        if (!p.inner.isEmpty()) {
            editField = p.inner.get(0);
            canvas.setActiveField(editField);
        }
        showValuePane();
    }

    private void showReporterInfo(BlockCatalog.Reporter r) {
        String own = BlockInfo.reporterInfo(r.display);
        TextView t = Ui.text(this, (own != null ? own : Tr.t("blk_value_info", "A value block: it gives a value to the field it is dropped in. Empty circles are inner fields you can fill or nest more value blocks into."))
                + "\n\n" + Tr.t("ui_generated_code", "Generated code") + ":\n" + r.template, 13.5f, Ui.TEXT);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        t.setTextIsSelectable(true);
        new AlertDialog.Builder(this).setTitle("ƒ " + reporterText(r)).setView(t)
                .setPositiveButton(Tr.t("ok", "OK"), null).show();
    }

    // =============================================================== canvas callbacks

    @Override
    public void onInfo(BlockModel.Node node) {
        if (BlockCatalog.isExplain(node.title) && !node.fields.isEmpty()) {
            showExplain(node);
            return;
        }
        String err = canvas.errorOf(node);
        if (err != null) {
            new AlertDialog.Builder(this)
                    .setTitle(Tr.t("blk_error", "Error in this block"))
                    .setMessage(err)
                    .setPositiveButton(Tr.t("ok", "OK"), null)
                    .setNeutralButton("ⓘ", (d, w) -> showInfo(node.title, BlockCatalog.find(node.title)))
                    .show();
            return;
        }
        showInfo(node.title, BlockCatalog.find(node.title));
    }

    /** The instructions written inside an explanation block, with an Edit (pencil) button. */
    private void showExplain(BlockModel.Node node) {
        BlockModel.Field f = node.fields.get(0);
        String text = f.value == null ? "" : f.value.trim();
        TextView t = Ui.text(this, text.isEmpty() ? Tr.t("blk_explain_empty", "No instructions yet. Tap Edit to write them.") : text, 15, text.isEmpty() ? Ui.TEXT_3 : Ui.TEXT);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 4));
        t.setTextIsSelectable(true);
        TextView hint = Ui.text(this, node.title.equals("explain") ? Tr.t("blk_explain_hint", "This block has no code: it only explains.")
                : node.title.equals("explainIf") ? Tr.t("blk_explain_hint_if", "No code of its own: the blocks inside just run.")
                : Tr.t("blk_explain_hint_else", "No code of its own: the first part just runs, the else part never runs."), 12, Ui.TEXT_3);
        hint.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        LinearLayout box = Ui.column(this);
        box.addView(t);
        box.addView(hint);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("ⓘ  " + BlockInfo.label(node.title, rtl)).setView(sv)
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .setNeutralButton("✎  " + Tr.t("edit", "Edit"), (d, w) -> com.star4droid.star2d.ui.TextDialog.show(this, Tr.t("edit", "Edit"), "", f.value == null ? "" : f.value, nv -> {
                    f.value = nv;
                    canvas.changed();
                    onInfo(node);
                }));
        b.show();
    }

    private void showInfo(String title, BlockCatalog.Def def) {
        String info = BlockInfo.info(title, rtl);
        StringBuilder sb = new StringBuilder();
        String note = info == null ? com.star4droid.star2d.remote.RemoteDocs.noteFor(title) : null;
        sb.append(info != null ? info : note != null ? note : Tr.t("blk_custom", "Custom block."));
        if (def != null && def.fields.length > 0) {
            sb.append("\n\n").append(Tr.t("blk_fields", "Fields")).append(": ");
            for (int i = 0; i < def.fields.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(BlockInfo.field(BlockModel.Field.labelOf(def.fields[i]), rtl));
            }
        }
        if (def != null && def.isIf) sb.append("\n\n").append(Tr.t("blk_c_block", "Put blocks inside its opening. Blocks under it run after it."));
        String code = def != null ? def.code : null;
        if (code == null) {
            BlockModel.Node sel = canvas.selected();
            if (sel != null && sel.title.equals(title)) code = sel.code;
        }
        TextView t = Ui.text(this, sb.toString(), 14, Ui.TEXT);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        LinearLayout box = Ui.column(this);
        box.addView(t);
        if (code != null) {
            TextView ct = Ui.text(this, Tr.t("ui_generated_code", "Generated code") + ":\n" + code, 12, Ui.TEXT_3);
            ct.setTypeface(Typeface.MONOSPACE);
            ct.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), 0);
            ct.setTextIsSelectable(true);
            box.addView(ct);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("ⓘ  " + BlockInfo.label(title, rtl)).setView(sv)
                .setPositiveButton(Tr.t("ok", "OK"), null);
        if (def != null) b.setNeutralButton(Tr.t("ui_add", "Add"), (d, w) -> addBlock(def));
        b.show();
    }

    @Override
    public void onSelect(BlockModel.Node node) {
        selectionBar.removeAllViews();
        if (node == null) {
            selectionBar.setVisibility(View.GONE);
            if (editField != null) closeValuePane();
            return;
        }
        Context c = this;
        TextView name = Ui.text(c, node.isRoot() ? BlockInfo.label("First >", rtl) : BlockInfo.label(node.title, rtl), 13, Ui.TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
        name.setMaxWidth(Ui.dp(c, 140));
        name.setMaxLines(1);
        selectionBar.addView(name);
        selectionBar.addView(Ui.iconButton(c, R.drawable.ic_s2_info, Tr.t("blk_info", "Info"), v -> onInfo(node)));
        if (!node.isRoot()) {
            selectionBar.addView(Ui.iconButton(c, R.drawable.ic_s2_copy, Tr.t("ui_duplicate", "Duplicate"), v -> canvas.duplicate(node)));
            ImageView del = Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> {
                canvas.deleteSingle(node);
                onSelect(null);
            });
            del.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
            selectionBar.addView(del);
        }
        if (canPromote(node))
            selectionBar.addView(Ui.iconButton(c, R.drawable.ic_s2_variables, Tr.t("ov_promote", "Make it an object variable"), v -> promoteToObjectVar(node)));
        selectionBar.addView(Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("ui_add_after", "Add after"), v -> {
            closeValuePane();
            if (!paletteOpen) togglePalette();
        }));
        selectionBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLongPress(BlockModel.Node node, float rawX, float rawY) {
        onSelect(node);
        IconMenu m = new IconMenu(this);
        m.add(R.drawable.ic_s2_info, Ui.ACCENT, Tr.t("blk_info", "Info"), () -> onInfo(node));
        if (!node.isRoot()) {
            m.add(R.drawable.ic_s2_copy, Tr.t("ui_duplicate", "Duplicate"), () -> canvas.duplicate(node));
            m.add(R.drawable.ic_s2_delete, 0xFFFF8A80, Tr.t("delete", "Delete"), () -> {
                canvas.deleteSingle(node);
                onSelect(null);
            });
        }
        if (canPromote(node))
            m.add(R.drawable.ic_s2_add, 0xFFE65100, Tr.t("ov_promote", "Make it an object variable"), () -> promoteToObjectVar(node));
        m.add(R.drawable.ic_s2_code, 0xFFAB47BC, Tr.t("ui_show_code", "Show code"), () -> {
            BlockModel tmp = new BlockModel();
            BlockModel.Node r = BlockModel.newRoot();
            BlockModel.Node copy = node.copySingle();
            if (node.yes != null) copy.yes = node.yes.copyChain();
            if (node.no != null) copy.no = node.no.copyChain();
            r.next = node.isRoot() ? node.next : copy;
            tmp.heads.add(r);
            Dialogs.longText(this, BlockInfo.label(node.title, rtl), tmp.exportCode());
        });
        m.showAt(rootFrame, rawX, rawY);
    }

    /** An element event (Events/<scene>/private/<script>/...): its local variables can become object variables. */
    private boolean isElementEvent() {
        return isElementEventPath(file == null ? null : file.getAbsolutePath());
    }

    /** Events/<scene>/private/<script>/<event>(.visual): not the scene events, not the inner nodes of a UI holder. */
    static boolean isElementEventPath(String p) {
        if (p == null) return false;
        int i = p.indexOf("/private/");
        if (i < 0) return false;
        String rest = p.substring(i + 9);
        return rest.indexOf('/') > 0 && rest.indexOf('/') == rest.lastIndexOf('/');
    }

    private boolean canPromote(BlockModel.Node node) {
        return node != null && "newVariable".equals(node.title) && isElementEvent() && BlockCatalog.find("objectVariable") != null;
    }

    /** Local variable block -> object variable block (same type, name and value): usable in every event of the object. */
    private void promoteToObjectVar(BlockModel.Node node) {
        BlockCatalog.Def d = BlockCatalog.find("objectVariable");
        if (d == null) return;
        beforeChange();
        node.title = d.title;
        node.code = d.code;
        node.color = d.color;
        canvas.changed();
        afterChange();
        loadVariables();
        String name = node.fields.size() > 1 ? node.fields.get(1).value : "";
        if (name != null && !name.isEmpty() && !variables.contains(name)) variables.add(name);
        onSelect(node);
        Snack.show(Tr.t("ov_promoted", "Now an object variable: every event of this object can use it."));
    }

    @Override
    public void onRootParam(String name) {
        String what;
        switch (name) {
            case "current": what = Tr.t("blk_p_current", "the element that owns this event"); break;
            case "body2": what = Tr.t("blk_p_body2", "the other body in the collision"); break;
            case "body1": what = Tr.t("blk_p_body1", "the first body in the collision"); break;
            case "event": what = Tr.t("blk_p_event", "the touch event (position, pointer…)"); break;
            default: what = name;
        }
        Snack.show(name + " = " + what + ". " + Tr.t("blk_p_drag", "Long-press it and drag a copy into any field."));
    }

    /** Title of the first block: the translated event name ("When tapped"…). */
    private String rootLabel() {
        return "▶ " + eventTitle();
    }

    /** The event's name in the app language: engine events through their translation, behavior events through the behavior's own names. */
    private String eventTitle() {
        String ev = null;
        try {
            ev = com.star4droid.star2d.ui.behaviors.BehaviorEvents.displayName(file);
        } catch (Throwable ignored) {
        }
        if (ev == null) ev = Tr.t(eventName);
        if (ev == null || ev.isEmpty()) ev = eventName;
        return ev;
    }

    /** Parameters of an event, from its file name (element events live in /private/). */
    static String[] eventParams(File f) {
        String[] bp = com.star4droid.star2d.ui.behaviors.BehaviorEvents.params(f);
        if (bp != null) return bp;
        String e = f.getName().replace(".visual", "");
        boolean element = f.getAbsolutePath().contains("/private/");
        if (element) {
            switch (e) {
                case "onTouchStart":
                case "onTouchEnd": return new String[]{"current", "event"};
                case "onBodyCollided":
                case "onBodyCollideEnd": return new String[]{"current", "body2"};
                default: return new String[]{"current"};
            }
        }
        if (e.equals("onCollisionStart") || e.equals("onCollisionEnd")) return new String[]{"body1", "body2"};
        return new String[0];
    }

    @Override
    public void onDropHint() {
        Snack.show(Tr.t("blk_drop_hint", "Drop value blocks onto a field (the white slots in a block)."));
    }

    @Override
    public void beforeChange() {
        undo.push(model.toJson());
        if (undo.size() > 60) undo.removeLast();
        redo.clear();
    }

    @Override
    public void afterChange() {
        changed = true;
        refreshUndo();
    }

    private void refreshUndo() {
        if (undoBtn == null) return;
        undoBtn.setAlpha(undo.isEmpty() ? 0.35f : 1f);
        redoBtn.setAlpha(redo.isEmpty() ? 0.35f : 1f);
    }

    private void restore(String json) {
        try {
            editField = null;
            editNode = null;
            if (valuePane != null) {
                valuePane.setVisibility(View.GONE);
                blocksPane.setVisibility(View.VISIBLE);
            }
            File tmp = File.createTempFile("blocks", ".visual", getCacheDir());
            BlockModel.write(tmp, json);
            BlockModel m = BlockModel.load(tmp);
            tmp.delete();
            // keep positions as they are on screen (load() normalizes file coordinates)
            model = m;
            canvas.setModel(model, rootLabel());
            onSelect(null);
        } catch (Exception e) {
            Snack.show(e.toString());
        }
    }

    private void undo() {
        commitValue();
        if (undo.isEmpty()) return;
        redo.push(model.toJson());
        restore(undo.pop());
        changed = true;
        refreshUndo();
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(model.toJson());
        restore(redo.pop());
        changed = true;
        refreshUndo();
    }

    /** Ids of every element of the project's .ui files (folder UI/), for the "node" field of the UI blocks. */
    private ArrayList<String> uiNodeIds() {
        ArrayList<String> ids = new ArrayList<>();
        try {
            String p = file.getAbsolutePath();
            int i = p.indexOf("/Events/");
            if (i < 0) return ids;
            collectUiIds(new File(p.substring(0, i), "UI"), ids, 0);
        } catch (Throwable ignored) {
        }
        return ids;
    }

    private static void collectUiIds(File dir, ArrayList<String> ids, int depth) {
        File[] fs = dir.listFiles();
        if (fs == null || depth > 3) return;
        for (File f : fs) {
            if (f.isDirectory()) collectUiIds(f, ids, depth + 1);
            else if (f.getName().endsWith(".ui")) {
                try {
                    com.star4droid.template.ui.UIDocument doc = com.star4droid.template.ui.UIDocument.read(f);
                    if (doc != null) for (com.star4droid.template.ui.UINode n : doc.allNodes()) if (n.id != null && !ids.contains(n.id) && !n.id.equals("root")) ids.add(n.id);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private LinkedHashMap<String, ArrayList<String>> suggestionsFor(String type, String label) {
        LinkedHashMap<String, ArrayList<String>> out = new LinkedHashMap<>();
        String t = type.toLowerCase(Locale.ROOT);
        String l = label.toLowerCase(Locale.ROOT);
        ArrayList<String> items = hints.containsKey("Items") ? hints.get("Items") : new ArrayList<>();
        String elements = Tr.t("blk_elements", "Elements"), vars = Tr.t("blk_variables", "Variables");
        String[] choices = BlockCatalog.choices(type);
        if (choices != null) {
            // a list of values (camera modes, lighting presets, render styles…)
            out.put(Tr.t("blk_options", "Options"), new ArrayList<>(java.util.Arrays.asList(choices)));
            return out;
        }
        if (t.equals("body") || l.equals("joystick") || l.equals("target") || l.equals("ui") || l.equals("holder")) {
            ArrayList<String> s = new ArrayList<>();
            s.add("current");
            s.add("body1");
            s.add("body2");
            s.addAll(items);
            out.put(elements, s);
        } else if (l.equals("node")) {
            // ids of the inner elements of the .ui files of the project
            ArrayList<String> s = uiNodeIds();
            if (!s.isEmpty()) out.put(Tr.t("blk_ui_nodes", "UI elements"), s);
        } else if (t.equals("behavior")) {
            ArrayList<String> s = new ArrayList<>();
            try {
                for (com.star4droid.star2d.behaviors.BehaviorMeta m : com.star4droid.star2d.behaviors.BehaviorCatalog.all(projectKey)) s.add(m.id);
            } catch (Throwable ignored) {}
            out.put(Tr.t("blk_behaviors", "Behaviors"), s);
        } else if (t.equals("boolean")) {
            ArrayList<String> s = new ArrayList<>();
            s.add("true");
            s.add("false");
            out.put(Tr.t("blk_values", "Values"), s);
            out.put(vars, variables);
        } else if (t.equals("image")) out.put(Tr.t("blk_images", "Images"), hints.get("Images"));
        else if (t.equals("scene")) out.put(Tr.t("scenes", "Scenes"), hints.get("Scenes"));
        else if (t.equals("sound")) out.put(Tr.t("blk_sounds", "Sounds"), hints.get("Sounds"));
        else if (t.equals("animation")) out.put(Tr.t("blk_animations", "Animations"), hints.get("Animations"));
        else if (l.equals("joint")) out.put(Tr.t("blk_joints", "Joints"), hints.get("Joints"));
        else if (l.equals("type") && !t.isEmpty()) { /* nothing */ } else {
            out.put(vars, variables);
            ArrayList<String> el = new ArrayList<>();
            el.add("current");
            el.addAll(items);
            out.put(elements, el);
            for (Map.Entry<String, ArrayList<String>> e : hints.entrySet())
                if (e.getKey().startsWith("ƒ")) out.put(e.getKey(), e.getValue());
            if (l.equals("type")) {
                ArrayList<String> s = new ArrayList<>();
                for (String x : new String[]{"int", "float", "String", "boolean", "DynamicBody", "StaticBody", "KinematicBody"}) s.add(x);
                out.put(Tr.t("blk_types", "Types"), s);
            }
        }
        java.util.Iterator<Map.Entry<String, ArrayList<String>>> it = out.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() == null) it.remove();
        return out;
    }

    // =============================================================== save / exit

    private void save(boolean thenFinish) {
        commitValue();
        try {
            model.save(file);
            changed = false;
            Snack.show(Tr.t("saved", "Saved") + " ✓");
            runCheck(thenFinish);
        } catch (Exception e) {
            Dialogs.longText(this, "Save error", e.toString());
        }
    }

    /** Used by tutorials to know whether the script was saved. */
    public boolean hasUnsavedChanges() { return changed; }

    // =============================================================== tutorial helpers

    public File file() { return file; }
    public boolean isEditingField() { return editField != null; }
    public BlockModel.Field editingField() { return editField; }

    public void saveNow() { save(false); }

    public void saveAndClose() {
        commitValue();
        save(true);
    }

    /** Show the palette category that holds this block and scroll to it. */
    public void revealPalette(String title) {
        if (!paletteOpen) togglePalette();
        if (editField != null) closeValuePane();
        String cat = null;
        for (Map.Entry<String, ArrayList<BlockCatalog.Def>> e : BlockCatalog.CATEGORIES.entrySet())
            for (BlockCatalog.Def d : e.getValue()) if (d.title.equals(title)) cat = e.getKey();
        if (cat == null) return;
        if (!cat.equals(currentCategory) || !search.getText().toString().isEmpty()) {
            currentCategory = cat;
            search.setText("");
            fillPalette();
        }
        scrollPaletteTo(title);
    }

    /** Scroll the palette (block list) so the block with this title is visible. */
    public void scrollPaletteTo(String title) {
        for (int i = 0; i < paletteItems.size(); i++) {
            Object o = paletteItems.get(i);
            if (o instanceof BlockCatalog.Def && ((BlockCatalog.Def) o).title.equals(title)) {
                ((LinearLayoutManager) paletteList.getLayoutManager()).scrollToPositionWithOffset(i, Ui.dp(this, 8));
                return;
            }
        }
    }

    /** Scroll the value list so a value block is visible. */
    public void scrollValuesTo(String group, String display) {
        if (valueList == null) return;
        if (group != null && !group.equals(valueGroup)) {
            valueGroup = group;
            fillValues();
        }
        for (int i = 0; i < valueItems.size(); i++) {
            Object o = valueItems.get(i);
            if (o instanceof BlockCatalog.Reporter && ((BlockCatalog.Reporter) o).display.equals(display)) {
                ((LinearLayoutManager) valueList.getLayoutManager()).scrollToPositionWithOffset(i, Ui.dp(this, 8));
                return;
            }
        }
    }

    public String currentCategory() { return currentCategory; }
    public String valueGroup() { return valueGroup; }

    /** Insert a block: after the last block of the root stack, or inside a C-block (slot 1 then, 2 else). */
    public BlockModel.Node tutorialInsert(String title, BlockModel.Node parent, int slot) {
        BlockCatalog.Def d = BlockCatalog.find(title);
        if (d == null) return null;
        BlockModel.Node n = d.create();
        beforeChange();
        if (parent == null) {
            BlockModel.Node root = model.root();
            if (root != null) root.tail().next = n;
        } else if (slot == 1) {
            if (parent.yes == null) parent.yes = n; else parent.yes.tail().next = n;
        } else if (slot == 2) {
            if (parent.no == null) parent.no = n; else parent.no.tail().next = n;
        } else {
            n.next = parent.next;
            parent.next = n;
        }
        canvas.changed();
        afterChange();
        return n;
    }

    /** Set a field value (one undo step). */
    public void tutorialSetField(BlockModel.Field f, String value) {
        if (f == null) return;
        beforeChange();
        f.value = value;
        f.block = null;
        canvas.changed();
        afterChange();
        if (f == editField && valueInput != null) valueInput.setText(value);
        else if (editField != null && valueInput != null) valueInput.setText(editField.value);
    }

    /** Put a value block into a field; returns it. */
    public BlockModel.Param tutorialSetParam(BlockModel.Field f, String display) {
        if (f == null) return null;
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS)
            if (r.display.equals(display)) {
                beforeChange();
                f.block = new BlockModel.Param(r.template, r.display);
                f.value = "";
                canvas.changed();
                afterChange();
                if (f == editField) {
                    // keep the value pane in sync (else its old text would replace the block)
                    if (!f.block.inner.isEmpty()) editField = f.block.inner.get(0);
                    canvas.setActiveField(editField);
                    showValuePane();
                }
                return f.block;
            }
        return null;
    }

    /** Open the value pane for a field (as if it was tapped). */
    public void tutorialEdit(BlockModel.Node n, BlockModel.Field f) {
        onFieldTap(n, f);
    }

    public void tutorialCloseValues() {
        if (editField != null) closeValuePane();
    }

    @Override
    public void onBackPressed() {
        if (canvas != null && canvas.isDragging()) return;
        if (com.star4droid.star2d.ui.tutorial.Tutorial.onBack(this)) return;
        if (editField != null) {
            closeValuePane();
            return;
        }
        if (!changed) {
            if (lastCheck != null && lastCheck.hasErrors()) {
                askAboutErrors();
                return;
            }
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(Tr.t("ui_unsaved", "Unsaved changes"))
                .setMessage(Tr.t("ui_save_before_exit", "Save your blocks before closing?"))
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> save(true))
                .setNegativeButton(Tr.t("ui_discard", "Discard"), (d, w) -> finish())
                .setNeutralButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }
}
