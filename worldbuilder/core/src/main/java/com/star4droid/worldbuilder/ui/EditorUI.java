package com.star4droid.worldbuilder.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.star4droid.worldbuilder.WorldBuilder;
import com.star4droid.worldbuilder.editor.Editor;
import com.star4droid.worldbuilder.engine.Assets;
import com.star4droid.worldbuilder.engine.Env;
import com.star4droid.worldbuilder.engine.ModelDef;
import com.star4droid.worldbuilder.engine.SceneObject;
import com.star4droid.worldbuilder.engine.Settings;
import com.star4droid.worldbuilder.engine.Terrain;

import static com.star4droid.worldbuilder.ui.Ui.dp;

/**
 * The editor screen. Landscape: library at the start side, properties at the end side. Portrait: one sheet at the bottom
 * with tabs. Both are built from the same pieces, so the screen can turn at any time.
 */
public final class EditorUI {
    public final Stage stage;
    public final WorldBuilder app;
    public final Editor ed;
    public final Dialogs dialogs;
    final Inspector inspector;
    public boolean portrait, loading;

    static final String[] CATS = {"nature", "rocks", "buildings", "fences", "props", "water", "effects"};
    static final String[] CAT_NAMES = {"Nature", "Rocks", "Buildings", "Fences", "Props", "Water", "Effects"};
    static final String[] CAT_ICONS = {"park", "filter_hdr", "cottage", "fence", "widgets", "water_drop", "auto_awesome"};
    static final float START_W = 258, END_W = 292;
    int cat, tab, sheetTab;                 // tab: 0 properties, 1 scene, 2 world
    boolean startOpen = true, endOpen = true, sheetOpen = true, firstBuild = true;
    final ObjectMap<String, Boolean> expanded = new ObjectMap<>();

    private Table root, center, toastHolder, loadLayer, toolBar;
    private Label fps, loadLabel, nameLabel, hint;
    private Image loadFill;
    private Table hintChip;
    private Button undoB, redoB;
    private final Button[] toolB = new Button[5];
    private Button multiB, snapB, groundB;
    private Table tabBody;
    // lists keep their scroll position when they are rebuilt
    private ScrollPane tabPane, libPane, catPane;
    private int tabPaneOf = -1;
    private final float[] tabScroll = new float[3];
    private float libScroll, catScroll;
    private boolean restoreScroll;
    private int lastW, lastH, lastChanges = -1;
    private float loadW;

    public EditorUI(WorldBuilder app, Editor ed) {
        this.app = app; this.ed = ed;
        stage = new Stage(new ScreenViewport());
        dialogs = new Dialogs(this);
        inspector = new Inspector(this);
        ed.onSelection = this::selectionChanged;
        ed.onValues = () -> inspector.refreshValues();
    }

    public void resize(int w, int h) {
        stage.getViewport().update(w, h, true);
        if (w != lastW || h != lastH) { lastW = w; lastH = h; build(); }
    }

    /** Wheel scrolling goes to the list under the mouse. */
    void hoverScroll(Actor a) {
        a.addListener(new InputListener() {
            @Override public void enter(InputEvent e, float x, float y, int pointer, Actor from) {
                Actor t = e.getTarget();
                while (t != null && !(t instanceof ScrollPane)) t = t.getParent();
                if (t != null) stage.setScrollFocus(t);
            }
            @Override public void exit(InputEvent e, float x, float y, int pointer, Actor to) { if (pointer == -1 && (to == null || !to.isDescendantOf(a))) stage.setScrollFocus(null); }
        });
    }

    private Table panel() {
        Table t = new Table();
        t.setBackground(Ui.flat(Ui.BG));
        t.setTouchable(Touchable.enabled);
        t.addListener(new InputListener() { @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; } });
        hoverScroll(t);
        return t;
    }

    // ------------------------------------------------------------------ layout
    public void build() {
        boolean wasLoading = loading;
        rememberScroll();
        tabPane = null; libPane = null; catPane = null; tabBody = null;
        dialogs.closeAll();
        stage.clear();
        float w = stage.getWidth(), h = stage.getHeight();
        portrait = h > w * 1.05f;
        ed.dp = Ui.scale;
        if (firstBuild) { firstBuild = false; endOpen = w / Ui.scale >= 1000; }
        root = new Table(); root.setFillParent(true); root.setTouchable(Touchable.childrenOnly);
        // keep clear of the camera notch and rounded corners (the 3D view still fills the screen)
        root.pad(Gdx.graphics.getSafeInsetTop(), Gdx.graphics.getSafeInsetLeft(), Gdx.graphics.getSafeInsetBottom(), Gdx.graphics.getSafeInsetRight());
        stage.addActor(root);
        center = new Table(); center.setTouchable(Touchable.childrenOnly);
        toastHolder = new Table(); toastHolder.setTouchable(Touchable.disabled);
        hintChip = new Table(); hintChip.setBackground(Ui.pill(Ui.BG)); hintChip.pad(dp(5), dp(12), dp(5), dp(6)); hintChip.setTouchable(Touchable.enabled);
        hint = Ui.label("");
        Button stop = Ui.iconButton("close", 26, 15, false, () -> { ed.placing = null; selectionChanged(); });
        Ui.row(hintChip, hint, stop);
        hintChip.getCell(stop).size(dp(26)).padLeft(dp(6));
        hintChip.addListener(new InputListener() { @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; } });
        fps = Ui.dim("");
        Table bar = topBar();
        toolBar = tools();
        if (portrait) {
            root.add(bar).growX().row();
            Table mid = new Table(); mid.setTouchable(Touchable.childrenOnly);
            mid.add(toolBar).pad(dp(6), dp(6), 0, dp(6)).row();
            mid.add(hintChip).padTop(dp(6)).row();
            mid.add(fps).expand().top().align(Align.top | Ui.end()).pad(dp(6)).row();
            mid.add(toastHolder).padBottom(dp(10));
            root.add(mid).grow().row();
            root.add(sheet()).growX();
        } else {
            root.add(bar).colspan(3).growX().row();
            Table startP = startOpen ? startPanel() : null, endP = endOpen ? endPanel() : null;
            Table mid = new Table(); mid.setTouchable(Touchable.childrenOnly);
            Table top = new Table(); top.setTouchable(Touchable.childrenOnly);
            Table tb = new Table(); tb.add(toolBar);
            if (L.rtl) { top.add(fps).top().pad(dp(8)); top.add(hintChip).expandX().top().padTop(dp(8)); top.add(tb).top().pad(dp(8)); }
            else { top.add(tb).top().pad(dp(8)); top.add(hintChip).expandX().top().padTop(dp(8)); top.add(fps).top().pad(dp(8)); }
            mid.add(top).grow().row();
            mid.add(toastHolder).padBottom(dp(14));
            Table a = L.rtl ? endP : startP, b = L.rtl ? startP : endP;
            if (a != null) root.add(a).width(a == startP ? dp(START_W) : dp(END_W)).growY(); else root.add();
            root.add(mid).grow();
            if (b != null) root.add(b).width(b == startP ? dp(START_W) : dp(END_W)).growY(); else root.add();
        }
        // loading layer on top of everything
        loadLayer = new Table(); loadLayer.setFillParent(true); loadLayer.setBackground(Ui.flat(Color.valueOf("121722")));
        loadLayer.setTouchable(Touchable.enabled);
        loadLayer.addListener(new InputListener() { @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; } });
        Label t = Ui.title(L.t("World Builder"));
        loadLabel = Ui.dim("");
        loadW = Math.min(dp(260), w - dp(60));
        Stack prog = new Stack();
        Image track = new Image(Ui.rr(4, Ui.LINE)); loadFill = new Image(Ui.rr(4, Ui.ACCENT));
        Table fillT = new Table(); fillT.left().add(loadFill).width(dp(8)).height(dp(8));
        prog.add(track); prog.add(fillT);
        loadLayer.add(Ui.icon("public", 46, Ui.ACCENT)).size(dp(46)).padBottom(dp(10)).row();
        loadLayer.add(t).padBottom(dp(18)).row();
        loadLayer.add(prog).size(loadW, dp(8)).padBottom(dp(10)).row();
        loadLayer.add(loadLabel);
        loadLayer.setVisible(wasLoading);
        stage.addActor(loadLayer);
        selectionChanged();
        syncTools();
    }

    private void rememberScroll() {
        if (tabPane != null && tabPaneOf >= 0 && tabPaneOf < 3) tabScroll[tabPaneOf] = tabPane.getScrollY();
        if (libPane != null) libScroll = libPane.getScrollY();
        if (catPane != null) catScroll = catPane.getScrollX();
        restoreScroll = true;
    }

    private void applyScroll() {
        restoreScroll = false;
        root.validate();
        if (tabPane != null && tabPaneOf >= 0 && tabPaneOf < 3) { tabPane.validate(); tabPane.setScrollY(Math.min(tabScroll[tabPaneOf], tabPane.getMaxY())); tabPane.updateVisualScroll(); }
        if (libPane != null) { libPane.validate(); libPane.setScrollY(Math.min(libScroll, libPane.getMaxY())); libPane.updateVisualScroll(); }
        if (catPane != null) { catPane.validate(); catPane.setScrollX(Math.min(catScroll, catPane.getMaxX())); catPane.updateVisualScroll(); }
    }

    public void setLoading(boolean on, String text, float progress) {
        if (loadLayer == null) return;
        loading = on;
        if (on) {
            if (!loadLayer.isVisible()) { loadLayer.clearActions(); loadLayer.getColor().a = 1; loadLayer.setVisible(true); loadLayer.toFront(); }
            loadLabel.setText(text);
            ((Table) loadFill.getParent()).getCell(loadFill).width(Math.max(dp(8), loadW * Math.min(1, progress)));
            ((Table) loadFill.getParent()).invalidate();
            ed.enabled = false;
        } else if (loadLayer.isVisible() && !loadLayer.hasActions()) {
            loadLayer.addAction(Actions.sequence(Actions.fadeOut(0.25f), Actions.visible(false)));
            dialogs.syncEditor();
        }
    }

    public void toast(String text) {
        toastHolder.clearChildren();
        Table t = new Table(); t.setBackground(Ui.pill(Ui.BG)); t.pad(dp(7), dp(14), dp(7), dp(14));
        t.add(Ui.label(text));
        toastHolder.add(t);
        t.getColor().a = 0;
        t.addAction(Actions.sequence(Actions.fadeIn(0.15f), Actions.delay(2.2f), Actions.fadeOut(0.3f), Actions.removeActor()));
    }

    // ------------------------------------------------------------------ top bar
    private Table topBar() {
        Table bar = panel();
        bar.pad(dp(4), dp(6), dp(4), dp(6));
        Button menu = Ui.iconButton("menu", this::menu);
        undoB = Ui.iconButton("undo", ed::doUndo); redoB = Ui.iconButton("redo", ed::doRedo);
        nameLabel = Ui.bold(ed.scene == null ? "" : ed.scene.name); nameLabel.setEllipsis(true);
        nameLabel.setTouchable(Touchable.enabled);
        Ui.click(nameLabel, this::rename);
        Table modes = new Table(); modes.setBackground(Ui.pill(Ui.FIELD)); modes.pad(dp(3));
        ButtonGroup<Button> g = new ButtonGroup<>();
        String[] mi = {"view_in_ar", "terrain", "grass"}, mn = {"Objects", "Terrain", "Scatter"};
        boolean text = stage.getWidth() / Ui.scale > 700;
        for (int i = 0; i < 3; i++) {
            int k = i;
            Button b = Ui.chip(text ? L.t(mn[i]) : "", mi[i]);
            b.getStyle().up = null;
            b.setChecked(ed.mode == i); g.add(b);
            Ui.click(b, () -> { if (ed.mode != k) { ed.mode = k; ed.placing = null; if (k != Editor.OBJECTS) { sheetTab = 0; startOpen = true; } build(); } });
            modes.add(b).padLeft(i == 0 ? 0 : dp(2));
        }
        Button save = Ui.iconButton("save", () -> app.save(true));
        Button set = Ui.iconButton("settings", dialogs::settings);
        Array<Actor> a = new Array<>();
        a.add(menu);
        if (!portrait) a.add(nameLabel);
        a.add(undoB, redoB, modes);
        Table spacer = new Table();
        a.add(spacer);
        if (!portrait) {
            Button lib = Ui.iconButton("category", () -> { startOpen = !startOpen; build(); }), prop = Ui.iconButton("tune", () -> { endOpen = !endOpen; build(); });
            lib.setChecked(startOpen); prop.setChecked(endOpen);
            lib.getStyle().checked = Ui.round(Ui.CARD_HI); prop.getStyle().checked = Ui.round(Ui.CARD_HI);
            a.add(lib, prop);
        }
        a.add(save, set);
        Ui.row(bar, a.toArray(Actor.class));
        for (Actor x : a) {
            if (x == spacer) bar.getCell(x).growX();
            else if (x == nameLabel) bar.getCell(x).width(dp(Math.min(170, stage.getWidth() / Ui.scale * 0.16f))).pad(0, dp(6), 0, dp(6));
            else if (x == modes) bar.getCell(x).pad(0, dp(6), 0, dp(6));
            else bar.getCell(x).size(dp(38)).pad(0, dp(1), 0, dp(1));
        }
        return bar;
    }

    private void menu() {
        dialogs.menu(ed.scene.name,
                new String[]{"add_circle", "folder_open", "save", "file_download", "edit", "settings", "help_outline"},
                new String[]{L.t("New world"), L.t("My worlds"), L.t("Save"), L.t("Save as a copy"), L.t("Rename world"), L.t("Settings"), L.t("How to use")},
                new Runnable[]{this::askNew, dialogs::worlds, () -> app.save(true),
                        () -> dialogs.prompt(L.t("Name of the copy"), ed.scene.name + " 2", false, s -> app.saveAs(s)), this::rename, dialogs::settings, dialogs::help});
    }

    void askNew() {
        if (ed.dirty) dialogs.confirm(L.t("New world"), L.t("Save the changes to \"{0}\" first?", ed.scene.name), L.t("Save"), false, () -> { app.save(false); dialogs.newWorld(); });
        else dialogs.newWorld();
    }

    private void rename() { dialogs.prompt(L.t("World name"), ed.scene.name, false, s -> app.rename(s)); }

    public void worldChanged() { build(); }

    // ------------------------------------------------------------------ tools
    private Table tools() {
        Table t = new Table();
        t.setBackground(Ui.round(Ui.BG)); t.pad(dp(4));
        t.setTouchable(Touchable.enabled);
        t.addListener(new InputListener() { @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; } });
        if (ed.mode != Editor.OBJECTS) { t.setVisible(false); return t; }
        String[] icons = {"near_me", "open_with", "autorenew", "open_in_full", "crop_free"};
        ButtonGroup<Button> g = new ButtonGroup<>();
        Array<Actor> all = new Array<>();
        for (int i = 0; i < 5; i++) {
            int k = i;
            toolB[i] = Ui.iconButton(icons[i], () -> ed.setTool(k));
            toolB[i].getStyle().checked = Ui.round(Ui.ACCENT);
            g.add(toolB[i]); all.add(toolB[i]);
        }
        Image sep1 = new Image(Ui.flat(Ui.LINE)), sep2 = new Image(Ui.flat(Ui.LINE));
        all.add(sep1);
        multiB = Ui.iconButton("select_all", () -> { ed.multi = !ed.multi; syncTools(); toast(L.t(ed.multi ? "Multi-select on: tap elements to add them" : "Multi-select off")); });
        snapB = Ui.iconButton("grid_on", () -> { Settings.snap = !Settings.snap; Settings.save(); syncTools(); toast(L.t(Settings.snap ? "Snap to grid on" : "Snap to grid off")); });
        groundB = Ui.iconButton("vertical_align_bottom", () -> { Settings.groundSnap = !Settings.groundSnap; Settings.save(); syncTools(); toast(L.t(Settings.groundSnap ? "Elements follow the ground" : "Free height")); });
        for (Button tb : new Button[]{multiB, snapB, groundB}) tb.getStyle().checked = Ui.round(Ui.ACCENT);
        all.add(multiB, snapB, groundB, sep2);
        all.add(Ui.iconButton("center_focus_strong", ed::focusSelection), Ui.iconButton("content_copy", ed::duplicateSelection), Ui.iconButton("delete", ed::deleteSelection));
        boolean twoCols = !portrait && stage.getHeight() / Ui.scale < 560;
        if (twoCols) { all.removeValue(sep1, true); all.removeValue(sep2, true); }
        int n = 0;
        // portrait: the eleven buttons share the width of the screen
        float pw = (float) Math.floor(Math.min(dp(40), (stage.getWidth() - Gdx.graphics.getSafeInsetLeft() - Gdx.graphics.getSafeInsetRight() - dp(12) - dp(8) - 2 * dp(7) - 11 * 2 * dp(1)) / 11f));
        for (Actor a : all) {
            boolean sep = a == sep1 || a == sep2;
            if (portrait) t.add(a).size(sep ? dp(1) : pw, sep ? dp(22) : Math.max(pw, dp(34))).pad(0, sep ? dp(3) : dp(1), 0, sep ? dp(3) : dp(1));
            else if (twoCols) { t.add(a).size(dp(38)).pad(dp(1)); if (++n % 2 == 0) t.row(); }
            else t.add(a).size(sep ? dp(24) : dp(38), sep ? dp(1) : dp(38)).pad(sep ? dp(4) : dp(1), 0, sep ? dp(4) : dp(1), 0).row();
        }
        return t;
    }

    void syncTools() {
        if (toolB[0] == null || ed.mode != Editor.OBJECTS) return;
        for (int i = 0; i < 5; i++) toolB[i].setChecked(ed.tool == i);
        multiB.setChecked(ed.multi); snapB.setChecked(Settings.snap); groundB.setChecked(Settings.groundSnap);
    }

    // ------------------------------------------------------------------ panels
    private Table startPanel() {
        Table p = panel();
        p.top();
        p.add(modeContent(3)).grow();
        return p;
    }

    private Table endPanel() {
        Table p = panel();
        p.top();
        p.add(tabs(new String[]{"Properties", "Scene", "World"}, new String[]{"tune", "layers", "wb_sunny"}, tab, i -> { tab = i; rebuildTab(); })).growX().pad(dp(6)).row();
        tabBody = new Table(); tabBody.top();
        p.add(tabBody).grow();
        rebuildTab();
        return p;
    }

    private Table sheet() {
        Table p = panel();
        String first = ed.mode == Editor.OBJECTS ? "Library" : ed.mode == Editor.TERRAIN ? "Terrain" : "Scatter";
        Table head = new Table();
        Table tb = tabs(new String[]{first, "Properties", "Scene", "World"}, new String[]{"category", "tune", "layers", "wb_sunny"}, sheetTab, i -> { sheetTab = i; sheetOpen = true; build(); });
        Button fold = Ui.iconButton(sheetOpen ? "expand_more" : "expand_less", 34, 20, false, () -> { sheetOpen = !sheetOpen; build(); });
        Ui.row(head, tb, fold);
        head.getCell(tb).growX(); head.getCell(fold).size(dp(34)).padLeft(dp(4));
        p.add(head).growX().pad(dp(6)).row();
        if (sheetOpen) {
            float hgt = Math.max(dp(230), stage.getHeight() * 0.36f);
            if (sheetTab == 0) p.add(modeContent(Math.max(3, (int) ((stage.getWidth() - dp(12)) / dp(80))))).growX().height(hgt);
            else { tabBody = new Table(); tabBody.top(); p.add(tabBody).growX().height(hgt); rebuildTab(); }
        }
        return p;
    }

    interface Pick { void pick(int i); }

    private Table tabs(String[] names, String[] icons, int selected, Pick pick) {
        Table t = new Table(); t.setBackground(Ui.pill(Ui.FIELD)); t.pad(dp(3));
        t.defaults().growX().uniformX();
        ButtonGroup<Button> g = new ButtonGroup<>();
        float each = (portrait ? stage.getWidth() / Ui.scale - 60 : END_W - 18) / names.length;
        boolean text = each > 66, icon = !text || each > 104;
        Array<Actor> bs = new Array<>();
        for (int i = 0; i < names.length; i++) {
            int k = i;
            Button b = Ui.chip(text ? L.t(names[i]) : "", icon ? icons[i] : null);
            b.pad(dp(6), dp(4), dp(6), dp(4));
            b.getStyle().up = null;
            b.setChecked(i == selected); g.add(b);
            Ui.click(b, () -> pick.pick(k));
            bs.add(b);
        }
        Ui.row(t, bs.toArray(Actor.class));
        return t;
    }

    void rebuildTab() {
        if (tabBody == null || ed.scene == null || tabBody.getStage() == null) return;
        int which = portrait ? sheetTab - 1 : tab;
        if (tabPane != null && tabPaneOf >= 0 && tabPaneOf < 3 && tabPane.getStage() != null) tabScroll[tabPaneOf] = tabPane.getScrollY();
        tabPane = null;
        tabBody.clearChildren();
        Table body = new Table(); body.top().pad(dp(4), dp(10), dp(12), dp(10));
        body.defaults().growX();
        if (which == 0) inspector.build(body);
        else if (which == 1) sceneList(body);
        else if (which == 2) world(body);
        else return;
        ScrollPane sp = Ui.scroll(body, false);
        tabBody.add(sp).grow();
        tabPane = sp; tabPaneOf = which; restoreScroll = true;
    }

    private Table modeContent(int cols) {
        Table t = new Table(); t.top();
        if (ed.mode == Editor.TERRAIN) { Table b = new Table(); b.top().pad(dp(8), dp(10), dp(12), dp(10)); b.defaults().growX(); terrain(b); t.add(Ui.scroll(b, false)).grow(); return t; }
        Table catBar = new Table();
        ButtonGroup<Button> g = new ButtonGroup<>();
        Table grid = new Table(); grid.top();
        ScrollPane gridPane = Ui.scroll(grid, false);
        for (int i = 0; i < CATS.length; i++) {
            int k = i;
            Button b = Ui.chip(L.t(CAT_NAMES[i]), CAT_ICONS[i]);
            b.setChecked(cat == i); g.add(b);
            Ui.click(b, () -> { cat = k; library(grid, cols); gridPane.setScrollY(0); libScroll = 0; });
            catBar.add(b).padRight(dp(5));
        }
        if (ed.mode == Editor.SCATTER) {
            Table sc = new Table(); sc.pad(dp(8), dp(10), 0, dp(10)); sc.defaults().growX();
            scatterControls(sc);
            t.add(sc).growX().row();
        }
        ScrollPane cp = Ui.scroll(catBar, true); cp.setScrollbarsVisible(false); cp.setScrollBarTouch(false);
        catPane = cp; libPane = gridPane;
        t.add(cp).growX().height(dp(34)).pad(dp(8), dp(8), dp(4), dp(8)).row();
        library(grid, cols);
        t.add(gridPane).grow();
        return t;
    }

    // ------------------------------------------------------------------ library
    private final Array<Button> tiles = new Array<>();

    private void library(Table grid, int cols) {
        grid.clearChildren(); tiles.clear();
        grid.pad(dp(2), dp(8), dp(10), dp(8));
        String last = null; int col = 0;
        float size = (float) Math.floor((portrait ? (stage.getWidth() - dp(16)) : (dp(START_W) - dp(16))) / cols - dp(6));
        Table rowT = null;
        for (ModelDef d : Assets.defs) {
            if (!d.cat.equals(CATS[cat])) continue;
            if (ed.mode == Editor.SCATTER && d.kind != ModelDef.MESH) continue;
            if (!d.sub.equals(last)) {
                last = d.sub; col = 0;
                Label s = Ui.section(L.t(d.sub));
                grid.add(s).growX().padTop(dp(8)).padBottom(dp(4)).row();
                rowT = new Table(); rowT.align(Ui.start());
                grid.add(rowT).growX().row();
            }
            if (col == cols) { col = 0; rowT = new Table(); rowT.align(Ui.start()); grid.add(rowT).growX().row(); }
            rowT.add(tile(d, size)).size(size, size + dp(18)).pad(dp(3));
            col++;
        }
        if (grid.getChildren().size == 0) { Label e = Ui.dim(L.t("Nothing to scatter in this category.")); e.setWrap(true); grid.add(e).growX().pad(dp(16)); }
        syncTiles();
    }

    private Button tile(ModelDef d, float size) {
        Button.ButtonStyle st = new Button.ButtonStyle(Ui.round(Ui.CARD), Ui.round(Ui.CARD_HI), Ui.round(Ui.ACCENT_DIM));
        Button b = new Button(st);
        TextureRegion tr = app.renderer.thumb(d.id);
        Image im = tr == null ? new Image() : new Image(new TextureRegionDrawable(tr), Scaling.fit);
        Label l = new Label(L.t(d.name), new Label.LabelStyle(Ui.small, Ui.TEXT)); l.setEllipsis(true); l.setAlignment(Align.center);
        b.add(im).size(size - dp(10)).padTop(dp(3)).row();
        b.add(l).width(size - dp(6)).padBottom(dp(3));
        b.setUserObject(d.id);
        b.setProgrammaticChangeEvents(false);
        Ui.click(b, () -> {
            if (ed.mode == Editor.SCATTER) { if (!ed.scatter.removeValue(d.id, false)) ed.scatter.add(d.id); ed.scatterErase = false; build(); }
            else { ed.placing = d.id.equals(ed.placing) ? null : d.id; if (ed.placing != null) ed.clearSelection(); selectionChanged(); }
        });
        tiles.add(b);
        return b;
    }

    private void syncTiles() {
        for (Button b : tiles) { String id = (String) b.getUserObject(); b.setChecked(ed.mode == Editor.SCATTER ? ed.scatter.contains(id, false) : id.equals(ed.placing)); }
    }

    private void selectionChanged() {
        syncTiles(); syncTools();
        if (hintChip != null) {
            ModelDef d = ed.placing == null ? null : Assets.byId.get(ed.placing);
            hintChip.setVisible(d != null);
            if (d != null) hint.setText(L.t("Tap the ground to place: {0}", L.t(d.name)));
        }
        rebuildTab();
    }

    // ------------------------------------------------------------------ terrain and scatter controls
    private void terrain(Table b) {
        b.add(Ui.section(L.t("Brush"))).padBottom(dp(6)).row();
        String[] names = {"Raise", "Lower", "Smooth", "Flatten", "Paint", "Erase"}, icons = {"trending_up", "trending_down", "blur_on", "horizontal_rule", "brush", "cleaning_services"};
        Table g = new Table(); g.defaults().growX().uniformX().space(dp(5));
        ButtonGroup<Button> bg = new ButtonGroup<>();
        int per = portrait ? 3 : 2;
        for (int i = 0; i < names.length; i++) {
            int k = i;
            Button x = Ui.button(L.t(names[i]), icons[i], Ui.CARD, null);
            x.getStyle().checked = Ui.round(Ui.ACCENT_DIM);
            x.setChecked(ed.brushMode == i); bg.add(x);
            Ui.click(x, () -> { boolean was = ed.brushMode == Terrain.PAINT; ed.brushMode = k; if (was != (k == Terrain.PAINT)) build(); });
            g.add(x).minWidth(0);
            if (i % per == per - 1) g.row();
        }
        b.add(g).padBottom(dp(10)).row();
        if (ed.brushMode == Terrain.PAINT) {
            b.add(Ui.section(L.t("Paint with"))).padBottom(dp(6)).row();
            String[] ln = {"Dirt", "Sand", "Rock", "Snow"};
            Color[] lc = {Color.valueOf("9a7650"), Color.valueOf("e6d199"), Color.valueOf("7b7d82"), Color.valueOf("eef2f7")};
            Table lt = new Table(); lt.defaults().growX().uniformX().space(dp(5));
            ButtonGroup<Button> lg = new ButtonGroup<>();
            for (int i = 0; i < 4; i++) {
                int k = i + 1;
                Button x = Ui.chip(L.t(ln[i]), null);
                Image dot = new Image(new TextureRegionDrawable(Ui.circle)); dot.setColor(lc[i]);
                x.clearChildren(); x.add(dot).size(dp(13)).padRight(dp(5)); x.add(Ui.label(L.t(ln[i])));
                x.setChecked(ed.brushLayer == k); lg.add(x);
                Ui.click(x, () -> ed.brushLayer = k);
                lt.add(x);
                if (!portrait && i == 1) lt.row();
            }
            b.add(lt).padBottom(dp(10)).row();
        }
        b.add(Ui.sliderRow(L.t("Size"), 1, 30, 0.5f, ed.brushRadius, " m", v -> ed.brushRadius = v, null, null)).padBottom(dp(6)).row();
        b.add(Ui.sliderRow(L.t("Strength"), 5, 100, 5, ed.brushStrength * 100, " %", v -> ed.brushStrength = v / 100f, null, null)).padBottom(dp(10)).row();
        Label tip = Ui.dim(L.t("Drag on the ground to shape it. Use two fingers (or the right mouse button) to move the view. Dig below the sea level to make lakes and rivers; rocky cliffs appear on steep slopes."));
        tip.setWrap(true); tip.setAlignment(Ui.start());
        b.add(tip).row();
    }

    private void scatterControls(Table b) {
        Table m = new Table(); m.defaults().growX().uniformX().space(dp(5));
        ButtonGroup<Button> g = new ButtonGroup<>();
        Button paint = Ui.button(L.t("Scatter"), "brush", Ui.CARD, null), erase = Ui.button(L.t("Erase"), "cleaning_services", Ui.CARD, null);
        paint.getStyle().checked = Ui.round(Ui.ACCENT_DIM); erase.getStyle().checked = Ui.round(Ui.ACCENT_DIM);
        paint.setChecked(!ed.scatterErase); erase.setChecked(ed.scatterErase); g.add(paint, erase);
        Ui.click(paint, () -> ed.scatterErase = false); Ui.click(erase, () -> ed.scatterErase = true);
        Ui.row(m, paint, erase);
        b.add(m).padBottom(dp(6)).row();
        Table s = new Table(); s.defaults().growX().uniformX().space(dp(10));
        Ui.row(s, Ui.sliderRow(L.t("Size"), 1, 20, 0.5f, ed.scatterRadius, " m", v -> ed.scatterRadius = v, null, null),
                Ui.sliderRow(L.t("Density"), 5, 100, 5, ed.scatterDensity * 100, " %", v -> ed.scatterDensity = v / 100f, null, null));
        b.add(s).row();
        Label tip = Ui.dim(ed.scatter.size == 0 ? L.t("Choose one or more elements below, then drag on the ground.") : L.t("{0} chosen. Drag on the ground to scatter.", ed.scatter.size));
        tip.setWrap(true); tip.setAlignment(Ui.start());
        b.add(tip).padTop(dp(2)).row();
    }

    // ------------------------------------------------------------------ world tab
    private void world(Table b) {
        Env e = ed.scene.env;
        Runnable touch = () -> ed.dirty = true;
        b.add(Ui.section(L.t("Sky and light"))).padTop(dp(6)).padBottom(dp(6)).row();
        Table time = Ui.sliderRow(L.t("Time of day"), 0, 24, 0.1f, e.time, " h", v -> { e.time = v; touch.run(); }, null, null);
        b.add(time).row();
        Table pre = new Table(); pre.defaults().growX().uniformX().space(dp(5));
        String[] pn = {"Dawn", "Day", "Sunset", "Night"}; float[] pv = {6.4f, 11f, 18.3f, 22f};
        Array<Actor> pb = new Array<>();
        for (int i = 0; i < 4; i++) { int k = i; pb.add(Ui.button(L.t(pn[i]), () -> ((com.badlogic.gdx.scenes.scene2d.ui.Slider) time.getUserObject()).setValue(pv[k]))); }
        Ui.row(pre, pb.toArray(Actor.class));
        for (Actor a : pb) { ((Button) a).pad(dp(6), dp(4), dp(6), dp(4)); pre.getCell(a).minWidth(0); }
        b.add(pre).padBottom(dp(8)).row();
        b.add(Ui.sliderRow(L.t("Sun direction"), 0, 360, 5, e.sunYaw, "°", v -> { e.sunYaw = v; touch.run(); }, null, null)).row();
        b.add(Ui.sliderRow(L.t("Clouds"), 0, 100, 5, e.clouds * 100, " %", v -> { e.clouds = v / 100f; touch.run(); }, null, null)).row();
        b.add(Ui.sliderRow(L.t("Fog"), 0, 100, 5, e.fog * 100, " %", v -> { e.fog = v / 100f; touch.run(); }, null, null)).row();
        b.add(Ui.sliderRow(L.t("Wind"), 0, 100, 5, e.wind * 100, " %", v -> { e.wind = v / 100f; touch.run(); }, null, null)).padBottom(dp(8)).row();
        b.add(Ui.section(L.t("Sea"))).padBottom(dp(6)).row();
        b.add(Ui.toggle(L.t("Sea and ground water"), e.water, v -> { e.water = v; touch.run(); })).padBottom(dp(6)).row();
        b.add(Ui.sliderRow(L.t("Water level"), -6, 12, 0.1f, e.waterLevel, " m", v -> { e.waterLevel = v; touch.run(); }, null, null)).row();
        b.add(inspector.colorRow(L.t("Shallow color"), e.shallow, new float[]{0.33f, 0.86f, 0.82f}, touch, null, touch)).padBottom(dp(4)).row();
        b.add(inspector.colorRow(L.t("Deep color"), e.deep, new float[]{0.10f, 0.42f, 0.72f}, touch, null, touch)).padBottom(dp(8)).row();
        Label tip = Ui.dim(L.t("The water fills every part of the ground that is lower than the water level."));
        tip.setWrap(true); tip.setAlignment(Ui.start());
        b.add(tip).row();
    }

    // ------------------------------------------------------------------ scene tab
    private void sceneList(Table b) {
        Array<SceneObject> all = ed.scene.objects;
        Label count = Ui.dim(L.t("{0} elements", all.size)); count.setAlignment(Ui.start());
        b.add(count).padTop(dp(6)).padBottom(dp(6)).row();
        ObjectMap<ModelDef, Array<SceneObject>> groups = new ObjectMap<>();
        Array<ModelDef> order = new Array<>();
        for (SceneObject o : all) { Array<SceneObject> g = groups.get(o.def); if (g == null) { g = new Array<>(); groups.put(o.def, g); order.add(o.def); } g.add(o); }
        order.sort((x, y) -> Assets.defs.indexOf(x, true) - Assets.defs.indexOf(y, true));
        for (ModelDef d : order) {
            Array<SceneObject> g = groups.get(d);
            boolean open = expanded.get(d.id, false);
            Table row = new Table(); row.setBackground(Ui.round(Ui.CARD)); row.pad(dp(4), dp(6), dp(4), dp(4));
            row.setTouchable(Touchable.enabled);
            TextureRegion tr = app.renderer.thumb(d.id);
            Image im = tr == null ? new Image() : new Image(new TextureRegionDrawable(tr), Scaling.fit);
            Label name = Ui.label(L.t(d.name)); name.setEllipsis(true); name.setAlignment(Ui.start());
            Label n = Ui.dim("×" + g.size);
            boolean anyVisible = false; for (SceneObject o : g) if (!o.hidden) anyVisible = true;
            boolean vis = anyVisible;
            Button eye = Ui.iconButton(vis ? "visibility" : "visibility_off", 32, 17, false, () -> { for (SceneObject o : g) o.hidden = vis; if (vis) for (SceneObject o : g) ed.selection.removeValue(o, true); ed.dirty = true; selectionChanged(); });
            Button sel = Ui.iconButton("select_all", 32, 17, false, () -> { ed.selection.clear(); for (SceneObject o : g) if (!o.hidden) ed.selection.add(o); ed.onSelection.run(); });
            Image arrow = Ui.icon(open ? "expand_more" : "chevron_right", 18, Ui.DIM);
            Ui.row(row, arrow, im, name, n, eye, sel);
            row.getCell(arrow).size(dp(18)); row.getCell(im).size(dp(30)).pad(0, dp(3), 0, dp(6)); row.getCell(name).growX().minWidth(0); row.getCell(n).pad(0, dp(6), 0, dp(2));
            row.getCell(eye).size(dp(32)); row.getCell(sel).size(dp(32));
            row.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                @Override public void clicked(InputEvent e, float x, float y) { if (e.getTarget().isDescendantOf(eye) || e.getTarget().isDescendantOf(sel)) return; expanded.put(d.id, !open); rebuildTab(); }
            });
            b.add(row).padBottom(dp(4)).row();
            if (!open) continue;
            int shown = 0;
            for (SceneObject o : g) {
                if (shown++ >= 150) { b.add(Ui.dim(L.t("and {0} more", g.size - 150))).padBottom(dp(4)).row(); break; }
                boolean on = ed.selection.contains(o, true);
                Table it = new Table(); it.setBackground(Ui.roundSmall(on ? Ui.ACCENT_DIM : Ui.FIELD)); it.pad(dp(5), dp(10), dp(5), dp(6));
                it.setTouchable(Touchable.enabled);
                Label nm = Ui.label(o.name.equals(d.name) ? L.t(d.name) + " " + o.uid : o.name); nm.setEllipsis(true); nm.setAlignment(Ui.start());
                if (o.hidden) nm.setColor(Ui.DIM);
                Button lock = Ui.iconButton(o.locked ? "lock" : "lock_open", 28, 15, false, () -> { o.locked = !o.locked; if (o.locked) ed.selection.removeValue(o, true); ed.dirty = true; selectionChanged(); });
                Ui.row(it, nm, lock);
                it.getCell(nm).growX().minWidth(0); it.getCell(lock).size(dp(28));
                it.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                    @Override public void clicked(InputEvent e, float x, float y) { if (e.getTarget().isDescendantOf(lock) || o.hidden) return; ed.select(o, ed.multi); ed.focusSelection(); }
                });
                b.add(it).padLeft(L.rtl ? 0 : dp(16)).padRight(L.rtl ? dp(16) : 0).padBottom(dp(3)).row();
            }
        }
    }

    // ------------------------------------------------------------------ frame
    public void act(float dt) {
        if (undoB != null) {
            if (ed.undo.changes != lastChanges) {
                lastChanges = ed.undo.changes;
                undoB.setDisabled(!ed.undo.canUndo()); redoB.setDisabled(!ed.undo.canRedo());
                undoB.getColor().a = ed.undo.canUndo() ? 1 : 0.35f; redoB.getColor().a = ed.undo.canRedo() ? 1 : 0.35f;
                if (!portrait && tab == 1 || portrait && sheetTab == 2) rebuildTab();
            }
            if (ed.toast != null) { toast(L.t(ed.toast)); ed.toast = null; }
            fps.setVisible(Settings.showFps);
            if (Settings.showFps) fps.setText(app.fps + " FPS • " + app.renderer.drawnObjects + " • " + app.renderer.drawnTris / 1000 + "k");
        }
        if (restoreScroll && root != null) applyScroll();
        stage.act(dt);
    }

    public void draw() { stage.getViewport().apply(); stage.draw(); }

    public void dispose() { dialogs.closeAll(); stage.dispose(); }
}
