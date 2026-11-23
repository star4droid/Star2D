package com.star4droid.worldbuilder;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;
import com.star4droid.worldbuilder.editor.Editor;
import com.star4droid.worldbuilder.engine.Assets;
import com.star4droid.worldbuilder.engine.ModelDef;
import com.star4droid.worldbuilder.engine.Renderer;
import com.star4droid.worldbuilder.engine.Scene;
import com.star4droid.worldbuilder.engine.SceneObject;
import com.star4droid.worldbuilder.engine.Settings;
import com.star4droid.worldbuilder.engine.Shaders;
import com.star4droid.worldbuilder.engine.Starter;
import com.star4droid.worldbuilder.engine.WorldIO;
import com.star4droid.worldbuilder.ui.EditorUI;
import com.star4droid.worldbuilder.ui.L;
import com.star4droid.worldbuilder.ui.Ui;

/** World Builder: a 3D world editor. Loads in small steps (a progress screen is shown), then runs the editor. */
public class WorldBuilder extends ApplicationAdapter {
    /** Desktop test options: folder for screenshots and a script of actions (see {@link TestScript}). */
    public static String shotsDir, shotScript;

    public Renderer renderer;
    public Editor editor;
    public EditorUI ui;
    private ShapeRenderer shapes;
    private InputMultiplexer input;
    private TestScript script;
    private float autosaveTimer;
    /** Frames per second measured here over the last half second (shown by "Show FPS"). */
    public int fps;
    private long fpsStart;
    private int fpsFrames;
    private boolean wantPicture, saveWhenReady;

    /** One step of a loading job; returns the progress 0..1 (1 = finished). */
    interface Step { float run(); }
    private static final class Job { final String text; final Step step; Job(String t, Step s) { text = t; step = s; } }
    private final Array<Job> jobs = new Array<>();
    private int jobsTotal;

    @Override public void create() {
        Settings.load();
        Ui.load();
        renderer = new Renderer();
        editor = new Editor(renderer);
        shapes = new ShapeRenderer();
        makeUi();
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        job("Loading textures", () -> { Assets.loadTextures(); Assets.loadFxShaders(); Assets.beginModels(); return 1; });
        job("Loading models", () -> Assets.loadSomeModels(4));
        job("Preparing the library", () -> renderer.thumbStep(5));
        String last = Settings.lastWorld;
        if (shotScript != null) script = new TestScript(this, shotScript, shotsDir);
        if (script != null && script.fresh || last.isEmpty() || !WorldIO.exists(last)) { saveWhenReady = script == null; buildWorld(() -> Starter.island(freeName("Island Village"))); }
        else loadWorld(last);
    }

    private void makeUi() {
        if (ui != null) ui.dispose();
        ui = new EditorUI(this, editor);
        input = new InputMultiplexer(ui.stage, new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key != Input.Keys.BACK && key != Input.Keys.ESCAPE) return false;
                if (ui.dialogs.back()) return true;
                if (key == Input.Keys.ESCAPE) return false;
                if (editor.placing != null || editor.selection.size > 0) { editor.placing = null; editor.clearSelection(); editor.onSelection.run(); return true; }
                ui.dialogs.confirm(L.t("Leave World Builder?"), L.t("Your world is saved before leaving."), L.t("Leave"), false, () -> { if (editor.scene != null) save(false); Gdx.app.exit(); });
                return true;
            }
        }, editor);
        Gdx.input.setInputProcessor(input);
        ui.setLoading(jobs.size > 0, "", 0);
    }

    public void rebuildUi() {
        Ui.load();
        makeUi();
        ui.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    // ------------------------------------------------------------------ loading jobs
    private void job(String text, Step s) { jobs.add(new Job(text, s)); jobsTotal++; }

    private interface Maker { Scene make() throws Exception; }

    /** Queues: make the world data, build its terrain and meshes a few per frame, then show it. */
    private void buildWorld(Maker maker) {
        Scene[] made = new Scene[1];
        int[] at = {0};
        job("Reading the world", () -> {
            try { made[0] = maker.make(); }
            catch (Exception e) {
                Gdx.app.error("WB", "world", e);
                jobs.clear(); jobsTotal = 0;
                if (editor.scene == null) buildWorld(() -> Starter.island(freeName("Island Village")));
                Gdx.app.postRunnable(() -> ui.dialogs.message(L.t("Could not open the world"), String.valueOf(e.getMessage())));
            }
            return 1;
        });
        job("Shaping the land", () -> { int left = made[0].terrain.rebuild(6); return 1 - left / 64f; });
        job("Placing elements", () -> {
            Scene s = made[0];
            int end = Math.min(s.objects.size, at[0] + 40);
            for (; at[0] < end; at[0]++) s.objects.get(at[0]).update(s);
            return s.objects.size == 0 ? 1 : at[0] / (float) s.objects.size;
        });
        job("Almost ready", () -> {
            Scene old = editor.scene;
            editor.setScene(made[0]);
            if (old != null) old.dispose();
            Settings.lastWorld = made[0].name; Settings.save();
            ui.worldChanged();
            autosaveTimer = 0;
            return 1;
        });
    }

    private void runJobs() {
        long t0 = System.nanoTime();
        while (jobs.size > 0 && System.nanoTime() - t0 < 14_000_000L) {
            Job j = jobs.first();
            float p = j.step.run();
            if (jobs.size == 0 || jobs.first() != j) break;      // the job list was replaced (an error)
            ui.setLoading(true, L.t(j.text), (jobsTotal - jobs.size + Math.min(1, p)) / Math.max(1, jobsTotal));
            if (p >= 1) jobs.removeIndex(0); else if (p < 0) break;
        }
        if (jobs.size == 0) {
            jobsTotal = 0;
            ui.setLoading(false, "", 1);
            if (saveWhenReady && editor.scene != null) { saveWhenReady = false; save(false); }
            if (script == null && !Settings.helpShown) { Settings.helpShown = true; Settings.save(); ui.dialogs.help(); }
        }
    }

    // ------------------------------------------------------------------ worlds
    String freeName(String base) {
        String n = base; int i = 2;
        while (WorldIO.exists(n)) n = base + " " + i++;
        return n;
    }

    public void newWorld(int template) {
        ui.setLoading(true, "", 0);
        buildWorld(() -> template == 0 ? Starter.island(freeName("Island Village")) : Starter.empty(freeName("My World")));
        saveWhenReady = true;
    }

    public void openWorld(String name) {
        if (editor.scene != null && editor.dirty && Settings.autosave) save(false);
        loadWorld(name);
    }

    private void loadWorld(String name) {
        ui.setLoading(true, "", 0);
        buildWorld(() -> WorldIO.load(name));
    }

    public void save(boolean tell) {
        if (editor.scene == null) return;
        try {
            editor.rig.store(editor.scene.camera);
            WorldIO.save(editor.scene);
            editor.dirty = false; autosaveTimer = 0;
            Settings.lastWorld = editor.scene.name; Settings.save();
            wantPicture = true;
            if (tell) ui.toast(L.t("Saved \"{0}\"", editor.scene.name));
        } catch (Exception e) {
            Gdx.app.error("WB", "save", e);
            ui.dialogs.message(L.t("Could not save"), String.valueOf(e.getMessage()));
        }
    }

    public void saveAs(String name) {
        editor.scene.name = freeName(name);
        save(true);
        ui.worldChanged();
    }

    public void rename(String name) {
        if (name.equals(editor.scene.name)) return;
        String old = editor.scene.name;
        boolean had = WorldIO.exists(old);
        editor.scene.name = freeName(name);
        if (had) { save(false); WorldIO.delete(old); }
        ui.worldChanged();
    }

    private void takePicture() {
        try {
            int bw = Gdx.graphics.getBackBufferWidth(), bh = Gdx.graphics.getBackBufferHeight();
            Pixmap full = Pixmap.createFromFrameBuffer(0, 0, bw, bh);
            int w = 304, h = 184;
            float ar = w / (float) h; int sw = bw, sh = Math.round(bw / ar);
            if (sh > bh) { sh = bh; sw = Math.round(bh * ar); }
            Pixmap small = new Pixmap(w, h, Pixmap.Format.RGB888);
            small.setFilter(Pixmap.Filter.BiLinear);
            small.drawPixmap(full, (bw - sw) / 2, (bh - sh) / 2, sw, sh, 0, 0, w, h);
            PixmapIO.writePNG(WorldIO.picture(editor.scene.name), small, 6, true);
            full.dispose(); small.dispose();
        } catch (Exception e) { Gdx.app.error("WB", "picture", e); }
    }

    // ------------------------------------------------------------------ frame
    @Override public void resize(int w, int h) { if (w > 0 && h > 0) ui.resize(w, h); }

    @Override public void render() {
        long now = System.nanoTime();
        if (fpsStart == 0) fpsStart = now;
        fpsFrames++;
        if (now - fpsStart >= 500_000_000L) { fps = Math.round(fpsFrames * 1e9f / (now - fpsStart)); fpsFrames = 0; fpsStart = now; }
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        if (script != null) dt = 1 / 30f;
        if (jobs.size > 0 || ui.loading) runJobs();
        else if (!renderer.thumbsDone() && renderer.thumbStep(6) >= 1) ui.build();
        GL20 gl = Gdx.gl;
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        if (editor.scene != null) {
            editor.update(dt);
            renderer.render(editor.scene, w, h, dt);
            if (wantPicture && !ui.loading) { wantPicture = false; takePicture(); }
            gl.glEnable(GL20.GL_BLEND); gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h); shapes.updateMatrices();
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (!ui.loading) editor.drawOverlay(shapes);
            shapes.end();
            if (Settings.autosave && editor.dirty && !ui.loading && (autosaveTimer += dt) > 75) save(false);
        } else {
            gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            gl.glClearColor(0.07f, 0.09f, 0.13f, 1); gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }
        ui.act(dt);
        ui.draw();
        if (script != null) script.frame();
    }

    public InputMultiplexer input() { return input; }

    @Override public void resume() {
        if (Assets.generatedLost()) {          // the graphics context was dropped while the app was in the background
            Assets.remakeGenerated();
            renderer.invalidateThumbs();
            rebuildUi();
        }
    }

    @Override public void pause() { if (editor != null && editor.scene != null && editor.dirty && Settings.autosave && jobs.size == 0) save(false); }

    @Override public void dispose() {
        if (editor.scene != null) editor.scene.dispose();
        ui.dispose(); Ui.dispose(); renderer.dispose(); shapes.dispose(); Assets.dispose(); Shaders.dispose();
    }
}
