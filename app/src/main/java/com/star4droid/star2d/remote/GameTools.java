package com.star4droid.star2d.remote;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.PixelCopy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.ScreenUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.CodeEditor.smart.BuildError;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.git.ProjectGit;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Build, run, look at and drive the game. The game always runs on the phone. */
final class GameTools {
    private GameTools() {}

    private static final CopyOnWriteArrayList<String> RUNTIME_ERRORS = new CopyOnWriteArrayList<>();
    private static boolean errorHookSet;

    private static synchronized void hookErrors() {
        if (errorHookSet) return;
        errorHookSet = true;
        TestApp.errorObserver = t -> {
            RUNTIME_ERRORS.add(t);
            while (RUNTIME_ERRORS.size() > 20) RUNTIME_ERRORS.remove(0);
        };
    }

    // ================================================================== build / run / stop

    private static final class BuildResult {
        boolean success, failed;
        String text = "";
        final List<BuildError> errors = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
    }

    private static JsonObject describeErrors(BuildResult r, Project p) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        JsonArray arr = new JsonArray();
        int n = 0;
        for (BuildError e : r.errors) {
            if (e.warning) continue;
            if (n++ >= 40) break;
            JsonObject j = new JsonObject();
            if (e.origin != null && !e.origin.isEmpty()) j.addProperty("where", e.origin);
            String f = e.target == BuildError.TARGET_NONE || e.targetFile == null ? e.file : e.targetFile;
            if (f != null) j.addProperty("file", p == null ? f : Proj.rel(p, new File(f)));
            j.addProperty("line", e.target == BuildError.TARGET_JAVA ? e.targetLine : e.line);
            j.addProperty("message", e.message);
            if (e.target == BuildError.TARGET_BLOCK) {
                j.addProperty("block", e.blockTitle);
                j.addProperty("block_index", e.blockIndex);
            }
            arr.add(j);
        }
        o.add("errors", arr);
        if (arr.size() == 0 && !r.text.isEmpty()) o.addProperty("text", r.text.length() > 3000 ? r.text.substring(0, 3000) + "…" : r.text);
        return o;
    }

    /** Compile (and optionally start). */
    static ToolResult buildOrRun(boolean start, JsonObject a) throws Exception {
        hookErrors();
        String scene = CoreTools.s(a, "scene");
        if (!scene.isEmpty()) Proj.openScene(scene);
        else Proj.waitLoaded(20_000);
        // a running game blocks the editor: stop it first
        if (Gl.run(() -> Bridge.app().isPlaying())) {
            Gl.run(() -> {
                Bridge.app().play((StageImp) null);
                return null;
            });
            Gl.sleep(300);
        }
        Gl.run(() -> {
            InputSim.uninstall();
            return null;
        });
        RUNTIME_ERRORS.clear();
        final BuildResult res = new BuildResult();
        final Project proj = Gl.run(Proj::project);
        ControlLayer.BuildObserver obs = new ControlLayer.BuildObserver() {
            @Override
            public void status(int state, String text) {
                if (state == 3) {
                    res.success = true;
                    res.done.countDown();
                } else if (state == 1) {
                    res.failed = true;
                    res.text = text == null ? "" : text;
                } else if (state == 2 && res.failed) {
                    res.done.countDown();
                }
            }

            @Override
            public void errors(List<BuildError> errors) {
                res.errors.clear();
                res.errors.addAll(errors);
            }
        };
        RemoteSession.setGameBusy(true);
        try {
            Gl.run(() -> {
                Proj.editor().getProject().save(Proj.editor());
                ControlLayer.buildObserver = obs;
                ControlLayer.buildOnly = !start;
                return null;
            });
            if (start) try { com.badlogic.gdx.files.FileHandle con = com.star4droid.template.Utils.GameLog.file("console"); if (con.exists()) com.star4droid.template.Utils.GameLog.delete(con); com.badlogic.gdx.files.FileHandle old = Gdx.files.external("game-logs"); if (old.exists()) old.deleteDirectory(); } catch (Throwable ignored) {}
            Gl.ui(() -> {
                if (EditorShell.current != null) EditorShell.current.play();
                else Bridge.gl(() -> Bridge.controls().actionPlay());
                return null;
            });
            if (!res.done.await(115, TimeUnit.SECONDS)) {
                return ToolResult.error("The build did not finish in time. Check the phone screen.");
            }
            if (res.failed) {
                JsonObject o = describeErrors(res, proj);
                o.addProperty("hint", "Fix the errors (events/blocks/java files) and call build again.");
                return ToolResult.text(o.toString());
            }
            if (!start) return ToolResult.text("{\"ok\":true,\"built\":true}");
            // wait for the game to be on screen
            long end = System.currentTimeMillis() + 20_000;
            boolean running = false;
            while (System.currentTimeMillis() < end) {
                running = Gl.run(() -> {
                    StageImp s = Bridge.app().getPlayingStage();
                    return s != null && s.initComplete();
                });
                if (running) break;
                Gl.sleep(100);
            }
            if (!running) {
                String err = RUNTIME_ERRORS.isEmpty() ? "" : RUNTIME_ERRORS.get(RUNTIME_ERRORS.size() - 1);
                return ToolResult.error("The game did not start." + (err.isEmpty() ? "" : "\n" + err));
            }
            long tReady = System.currentTimeMillis();
            boolean ready = waitReady(40_000);
            Gl.sleep(Math.max(0, Math.min(5000, CoreTools.i(a, "wait_ms", 200))));
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("running", true);
            o.addProperty("ready", ready);
            if (ready) o.addProperty("loaded_in_ms", System.currentTimeMillis() - tReady);
            else o.addProperty("warning", "Still on the loading screen after 40 s (big assets or a crash in OnCreate). Check game_logs, then screenshot.");
            if (!RUNTIME_ERRORS.isEmpty()) o.addProperty("runtime_error", RUNTIME_ERRORS.get(RUNTIME_ERRORS.size() - 1));
            o.addProperty("next", "The game is loaded and running: use screenshot / game_input / game_state / run_test. The game screen covers the editor until stop_game.");
            return ToolResult.text(o.toString());
        } finally {
            ControlLayer.buildObserver = null;
            ControlLayer.buildOnly = false;
            RemoteSession.setGameBusy(false);
        }
    }

    /** Waits (worker thread) until the running game finished loading (no loading screen, OnCreate ran, a few frames drawn). */
    static boolean waitReady(long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (true) {
            boolean ready = Gl.run(() -> {
                StageImp s = Bridge.app().getPlayingStage();
                return s != null && s.isReady();
            });
            if (ready) return true;
            if (System.currentTimeMillis() >= end) return false;
            Gl.sleep(80);
        }
    }

    static ToolResult stop() throws Exception {
        boolean was = Gl.run(() -> {
            boolean p = Bridge.app().isPlaying();
            if (p) Bridge.app().play((StageImp) null);
            return p;
        });
        InputSimStop();
        return ToolResult.text(was ? "stopped" : "no game was running");
    }

    private static void InputSimStop() throws Exception {
        Gl.run(() -> {
            InputSim.uninstall();
            return null;
        });
    }

    private static StageImp active() {
        StageImp s = Bridge.app().getPlayingStage();
        if (s == null) throw new Gl.ToolError("The game is not running. Call run_game first.");
        return s.activeStage();
    }

    // ================================================================== screenshot

    static ToolResult screenshot(JsonObject a) throws Exception {
        String target = CoreTools.s(a, "target");
        if (target.isEmpty()) target = "auto";
        int maxW = Math.max(240, Math.min(1440, CoreTools.i(a, "max_width", 720)));
        int q = Math.max(30, Math.min(90, CoreTools.i(a, "quality", 70)));
        boolean playing = Gl.run(() -> Bridge.app().isPlaying());
        if (target.equals("auto")) target = playing ? "game" : "editor";
        if (target.equals("game") && !playing) return ToolResult.error("The game is not running. Use run_game first, or target 'editor'.");
        if (playing && (target.equals("game") || target.equals("scene"))) waitReady(15_000);
        Bitmap bmp;
        String label = target;
        final boolean sceneView = target.equals("scene") && !playing;
        final boolean framed = sceneView && (a.has("camera") || a.has("region") || CoreTools.b(a, "clean", false));
        final double[] saved = new double[3];
        final Object[] savedUi = new Object[2]; // grids flag, selected actor
        if (framed) {
            label = Gl.run(() -> frameScene(a, saved, savedUi));
            Gl.sleep(220); // let the editor draw a frame with the new camera
        }
        try {
            if (target.equals("editor")) {
                bmp = windowShot();
                if (bmp == null) bmp = glShot();
            } else bmp = glShot(); // game or scene: the GL canvas
        } finally {
            if (framed) Gl.run(() -> {
                restoreScene(saved, savedUi);
                return null;
            });
        }
        if (bmp == null) return ToolResult.error("Could not capture the screen. Is the Star2D app visible on the phone? Ask the user to keep the editor open.");
        return image(bmp, maxW, q, label);
    }

    /** Moves the editor camera / hides overlays for a scene shot. GL thread. Returns the label. */
    private static String frameScene(JsonObject a, double[] saved, Object[] savedUi) throws Exception {
        com.star4droid.star2d.editor.LibgdxEditor ed = Proj.editor();
        com.badlogic.gdx.graphics.OrthographicCamera cam = (com.badlogic.gdx.graphics.OrthographicCamera) ed.getCamera();
        saved[0] = cam.position.x;
        saved[1] = cam.position.y;
        saved[2] = cam.zoom;
        String label = "scene";
        if (a.has("region") && a.get("region").isJsonObject()) {
            JsonObject r = a.getAsJsonObject("region");
            double w = Math.max(8, r.has("width") ? r.get("width").getAsDouble() : 400), h = Math.max(8, r.has("height") ? r.get("height").getAsDouble() : 300);
            double x = r.has("x") ? r.get("x").getAsDouble() : 0, y = r.has("y") ? r.get("y").getAsDouble() : 0;
            cam.position.x = (float) (x + w / 2);
            cam.position.y = (float) (y + h / 2);
            cam.zoom = (float) Math.max(w / cam.viewportWidth, h / cam.viewportHeight) * 1.03f;
            label = "scene region x=" + Math.round(x) + " y=" + Math.round(y) + " w=" + Math.round(w) + " h=" + Math.round(h);
        } else if (a.has("camera") && a.get("camera").isJsonObject()) {
            JsonObject c = a.getAsJsonObject("camera");
            if (c.has("x")) cam.position.x = c.get("x").getAsFloat();
            if (c.has("y")) cam.position.y = c.get("y").getAsFloat();
            if (c.has("zoom")) cam.zoom = Math.max(0.05f, c.get("zoom").getAsFloat());
            label = "scene camera x=" + Math.round(cam.position.x) + " y=" + Math.round(cam.position.y) + " zoom=" + cam.zoom;
        }
        cam.update();
        if (CoreTools.b(a, "clean", false)) {
            try {
                java.lang.reflect.Field f = ed.getClass().getDeclaredField("SHOW_GRIDS");
                f.setAccessible(true);
                savedUi[0] = f.getBoolean(ed);
                ed.showGrids(false);
                savedUi[1] = ed.getSelectedActor();
                if (savedUi[1] != null) ed.selectActor(null);
            } catch (Throwable ignored) {
            }
        }
        return label;
    }

    private static void restoreScene(double[] saved, Object[] savedUi) {
        try {
            com.star4droid.star2d.editor.LibgdxEditor ed = Proj.editor();
            com.badlogic.gdx.graphics.OrthographicCamera cam = (com.badlogic.gdx.graphics.OrthographicCamera) ed.getCamera();
            cam.position.x = (float) saved[0];
            cam.position.y = (float) saved[1];
            cam.zoom = (float) saved[2];
            cam.update();
            if (savedUi[0] != null) ed.showGrids((Boolean) savedUi[0]);
            if (savedUi[1] instanceof com.badlogic.gdx.scenes.scene2d.Actor) ed.selectActor((com.badlogic.gdx.scenes.scene2d.Actor) savedUi[1]);
        } catch (Throwable ignored) {
        }
    }

    static ToolResult image(Bitmap bmp, int maxW, int quality, String label) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int tw = Math.min(maxW, w);
        byte[] jpg = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            int th = Math.round(h * (tw / (float) w));
            Bitmap s = tw == w ? bmp : Bitmap.createScaledBitmap(bmp, tw, th, true);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            s.compress(Bitmap.CompressFormat.JPEG, quality, bo);
            if (s != bmp) s.recycle();
            jpg = bo.toByteArray();
            if (jpg.length * 4 / 3 < 640_000) break;
            tw = Math.round(tw * 0.8f);
            quality = Math.max(35, quality - 10);
        }
        bmp.recycle();
        ToolResult r = new ToolResult();
        r.addText("screenshot of " + label + " (" + w + "x" + h + " px on the phone, sent at " + tw + " px wide)");
        r.addImage(Base64.encodeToString(jpg, Base64.NO_WRAP), "image/jpeg");
        return r;
    }

    /** The GL canvas, read right after a frame was drawn. */
    static Bitmap glShot() throws Exception {
        final AtomicReference<Bitmap> out = new AtomicReference<>();
        final CountDownLatch l = new CountDownLatch(1);
        Gl.run(() -> {
            TestApp.frameHook = () -> {
                try {
                    int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
                    Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0, w, h);
                    ByteBuffer buf = pm.getPixels();
                    byte[] raw = new byte[w * h * 4];
                    buf.rewind();
                    buf.get(raw);
                    pm.dispose();
                    int[] px = new int[w * h];
                    for (int y = 0; y < h; y++) {
                        int src = (h - 1 - y) * w * 4; // GL rows are bottom-up
                        int dst = y * w;
                        for (int x = 0; x < w; x++) {
                            int i = src + x * 4;
                            px[dst + x] = 0xFF000000 | ((raw[i] & 255) << 16) | ((raw[i + 1] & 255) << 8) | (raw[i + 2] & 255);
                        }
                    }
                    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    b.setPixels(px, 0, w, 0, 0, w, h);
                    out.set(b);
                } catch (Throwable ignored) {
                } finally {
                    l.countDown();
                }
            };
            try { Gdx.graphics.requestRendering(); } catch (Throwable ignored) {}
            return null;
        });
        if (!l.await(6, TimeUnit.SECONDS)) {
            TestApp.frameHook = null;
            return null;
        }
        return out.get();
    }

    /** The whole app window (editor UI + canvas) with PixelCopy; null if impossible. */
    static Bitmap windowShot() throws Exception {
        if (Build.VERSION.SDK_INT < 26) return null;
        final Activity act = RemoteClient.get().activity();
        if (act == null) return null;
        final AtomicReference<Bitmap> out = new AtomicReference<>();
        final CountDownLatch l = new CountDownLatch(1);
        Gl.ui(() -> {
            android.view.View decor = act.getWindow().getDecorView();
            if (decor.getWidth() <= 0 || decor.getHeight() <= 0) {
                l.countDown();
                return null;
            }
            final Bitmap b = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                PixelCopy.request(act.getWindow(), b, code -> {
                    if (code == PixelCopy.SUCCESS) out.set(b);
                    l.countDown();
                }, new Handler(Looper.getMainLooper()));
            } catch (Throwable t) {
                l.countDown();
            }
            return null;
        });
        if (!l.await(6, TimeUnit.SECONDS)) return null;
        return out.get();
    }

    // ================================================================== input

    /** Screen position (pixels, y down) from a position object. GL thread. */
    private static int[] screenPos(JsonObject o) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        if (o.has("element")) {
            StageImp st = active();
            PlayerItem it = st.findItem(CoreTools.s(o, "element"));
            if (it == null) throw new Gl.ToolError("no element named '" + CoreTools.s(o, "element") + "' in the running scene");
            Actor ac = it.getActor();
            Stage stage = ac.getStage();
            if (stage == null) throw new Gl.ToolError("element '" + CoreTools.s(o, "element") + "' is not on screen");
            Vector2 v = ac.localToStageCoordinates(new Vector2(ac.getWidth() / 2f, ac.getHeight() / 2f));
            stage.stageToScreenCoordinates(v);
            return new int[]{Math.round(v.x), Math.round(v.y)};
        }
        if (o.has("px") || o.has("py")) return new int[]{CoreTools.i(o, "px", 0), CoreTools.i(o, "py", 0)};
        if (o.has("x") || o.has("y")) {
            double x = o.has("x") ? o.get("x").getAsDouble() : 0.5, y = o.has("y") ? o.get("y").getAsDouble() : 0.5;
            return new int[]{(int) Math.round(x * w), (int) Math.round(y * h)};
        }
        throw new Gl.ToolError("a position is needed: {x,y} (0-1 of the screen), {px,py} or {element}");
    }

    /** Runs input actions in real time. Worker thread. */
    static void runActions(JsonArray actions, long deadline, JsonArray log) throws Exception {
        Gl.run(() -> {
            InputSim.install();
            return null;
        });
        try {
            for (int i = 0; i < actions.size(); i++) {
                if (System.currentTimeMillis() > deadline) {
                    JsonObject o = new JsonObject();
                    o.addProperty("stopped", "time budget used up at action " + i);
                    log.add(o);
                    return;
                }
                JsonObject act = actions.get(i).getAsJsonObject();
                String d = CoreTools.s(act, "do");
                int pointer = CoreTools.i(act, "pointer", 0);
                int ms = Math.max(0, Math.min(10_000, CoreTools.i(act, "ms", 0)));
                switch (d) {
                    case "wait":
                        Gl.sleep(Math.max(1, ms));
                        break;
                    case "release":
                        Gl.run(() -> {
                            InputSim.uninstall();
                            InputSim.install();
                            return null;
                        });
                        break;
                    case "hold":
                    case "tap": {
                        final int[] p = Gl.run(() -> screenPos(act));
                        Gl.run(() -> {
                            InputSim.down(p[0], p[1], pointer);
                            return null;
                        });
                        Gl.sleep(ms > 0 ? ms : 70);
                        Gl.run(() -> {
                            InputSim.up(p[0], p[1], pointer);
                            return null;
                        });
                        break;
                    }
                    case "down": {
                        final int[] p = Gl.run(() -> screenPos(act));
                        Gl.run(() -> {
                            InputSim.down(p[0], p[1], pointer);
                            return null;
                        });
                        if (ms > 0) Gl.sleep(ms);
                        break;
                    }
                    case "move": {
                        final int[] p = Gl.run(() -> screenPos(act));
                        Gl.run(() -> {
                            InputSim.move(p[0], p[1], pointer);
                            return null;
                        });
                        if (ms > 0) Gl.sleep(ms);
                        break;
                    }
                    case "up": {
                        final int[] p = Gl.run(() -> screenPos(act));
                        Gl.run(() -> {
                            InputSim.up(p[0], p[1], pointer);
                            return null;
                        });
                        break;
                    }
                    case "swipe": {
                        if (!act.has("to") || !act.get("to").isJsonObject()) throw new Gl.ToolError("swipe needs 'to': {x,y}");
                        final int[] from = Gl.run(() -> screenPos(act));
                        final int[] to = Gl.run(() -> screenPos(act.getAsJsonObject("to")));
                        int dur = ms > 0 ? ms : 300, steps = Math.max(3, dur / 16);
                        Gl.run(() -> {
                            InputSim.down(from[0], from[1], pointer);
                            return null;
                        });
                        for (int s = 1; s <= steps; s++) {
                            final int x = from[0] + (to[0] - from[0]) * s / steps, y = from[1] + (to[1] - from[1]) * s / steps;
                            Gl.sleep(dur / steps);
                            Gl.run(() -> {
                                InputSim.move(x, y, pointer);
                                return null;
                            });
                        }
                        Gl.run(() -> {
                            InputSim.up(to[0], to[1], pointer);
                            return null;
                        });
                        break;
                    }
                    case "key": {
                        final int code = InputSim.keyCode(CoreTools.s(act, "key"));
                        Gl.run(() -> {
                            InputSim.keyDown(code);
                            return null;
                        });
                        Gl.sleep(ms > 0 ? ms : 80);
                        Gl.run(() -> {
                            InputSim.keyUp(code);
                            return null;
                        });
                        break;
                    }
                    case "joystick": {
                        final double jx = act.has("jx") ? act.get("jx").getAsDouble() : 0, jy = act.has("jy") ? act.get("jy").getAsDouble() : 0;
                        final int[] c = Gl.run(() -> screenPos(act));
                        final int[] r = Gl.run(() -> {
                            PlayerItem it = active().findItem(CoreTools.s(act, "element"));
                            if (it == null) throw new Gl.ToolError("joystick element not found");
                            Actor ac = it.getActor();
                            Stage stage = ac.getStage();
                            Vector2 e = ac.localToStageCoordinates(new Vector2(ac.getWidth(), ac.getHeight() / 2f));
                            stage.stageToScreenCoordinates(e);
                            return new int[]{Math.abs(Math.round(e.x) - c[0])};
                        });
                        int rad = Math.max(8, r[0]);
                        final int tx = (int) Math.round(c[0] + jx * rad * 0.9), ty = (int) Math.round(c[1] - jy * rad * 0.9);
                        Gl.run(() -> {
                            InputSim.down(c[0], c[1], pointer);
                            return null;
                        });
                        Gl.sleep(40);
                        Gl.run(() -> {
                            InputSim.move(tx, ty, pointer);
                            return null;
                        });
                        Gl.sleep(ms > 0 ? ms : 500);
                        Gl.run(() -> {
                            InputSim.up(tx, ty, pointer);
                            return null;
                        });
                        break;
                    }
                    default:
                        throw new Gl.ToolError("unknown action '" + d + "' (tap|hold|down|move|up|release|swipe|key|joystick|wait)");
                }
            }
        } finally {
            // a finger left pressed with 'down' stays pressed after this call (until 'up' or 'release'), so the AI can
            // look at the game (screenshot / game_state) while holding.
            Gl.run(() -> {
                if (!InputSim.holding()) InputSim.uninstall();
                return null;
            });
        }
    }

    static ToolResult gameInput(JsonObject a) throws Exception {
        if (!a.has("actions") || !a.get("actions").isJsonArray()) return ToolResult.error("actions (array) is required");
        Gl.run(() -> {
            active();
            return null;
        });
        JsonArray log = new JsonArray();
        if (!waitReady(30_000)) return ToolResult.error("The game is still loading (loading screen). Wait a few seconds and call again, or check game_logs.");
        long t0 = System.currentTimeMillis();
        runActions(a.getAsJsonArray("actions"), t0 + 45_000, log);
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        if (Gl.run(InputSim::holding)) o.addProperty("still_pressed", "a finger/key is still held down (from 'down'); send 'up' or 'release' to let go (auto-release after 60 s)");
        o.addProperty("took_ms", System.currentTimeMillis() - t0);
        if (log.size() > 0) o.add("notes", log);
        if (!RUNTIME_ERRORS.isEmpty()) o.addProperty("runtime_error", RUNTIME_ERRORS.get(RUNTIME_ERRORS.size() - 1));
        return ToolResult.text(o.toString());
    }

    // ================================================================== state / logs

    private static JsonObject itemState(PlayerItem it) {
        Actor ac = it.getActor();
        JsonObject o = new JsonObject();
        o.addProperty("name", ac.getName());
        o.addProperty("x", round(ac.getX()));
        o.addProperty("y", round(ac.getY()));
        o.addProperty("width", round(ac.getWidth()));
        o.addProperty("height", round(ac.getHeight()));
        o.addProperty("rotation", round(ac.getRotation()));
        o.addProperty("visible", ac.isVisible());
        try {
            if (it.getBody() != null) {
                o.addProperty("vx", round(it.getBody().getLinearVelocity().x));
                o.addProperty("vy", round(it.getBody().getLinearVelocity().y));
            }
        } catch (Throwable ignored) {
        }
        if (ac instanceof Label) o.addProperty("text", ((Label) ac).getText().toString());
        try {
            if (it.getProgress() != 0) o.addProperty("progress", it.getProgress());
        } catch (Throwable ignored) {
        }
        return o;
    }

    private static double round(float f) {
        return Math.round(f * 100.0) / 100.0;
    }

    private static void collectItems(Stage st, java.util.Set<String> want, JsonArray out, int[] total) {
        if (st == null) return;
        for (Actor ac : st.getActors()) {
            if (!(ac instanceof PlayerItem) || ac.getName() == null) continue;
            total[0]++;
            if (!want.isEmpty() && !want.contains(ac.getName())) continue;
            if (out.size() < 120) out.add(itemState((PlayerItem) ac));
        }
    }

    private static JsonObject variablesOf(StageImp st) {
        JsonObject vars = new JsonObject();
        ArrayList<Object> holders = new ArrayList<>();
        holders.add(st);
        try {
            Field f = StageImp.class.getDeclaredField("sceneScript");
            f.setAccessible(true);
            Object ss = f.get(st);
            if (ss != null) holders.add(ss);
        } catch (Throwable ignored) {
        }
        for (Object h : holders) {
            Class<?> c = h.getClass();
            if (c == StageImp.class) continue;
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                Class<?> t = f.getType();
                if (!(t.isPrimitive() || t == String.class)) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(h);
                    if (v instanceof Number) vars.addProperty(f.getName(), (Number) v);
                    else if (v instanceof Boolean) vars.addProperty(f.getName(), (Boolean) v);
                    else vars.addProperty(f.getName(), String.valueOf(v));
                } catch (Throwable ignored) {
                }
            }
        }
        return vars;
    }

    private static String tailLog(boolean clear) {
        try {
            com.badlogic.gdx.files.FileHandle f = com.star4droid.template.Utils.GameLog.file("console");
            if (!f.exists()) return "";
            String t = f.readString();
            if (clear) f.writeString("", false);
            return t.length() > 4000 ? "…" + t.substring(t.length() - 4000) : t;
        } catch (Throwable e) {
            return "";
        }
    }

    static JsonObject stateObject(JsonObject a) throws Exception {
        final JsonObject o = new JsonObject();
        Gl.run(() -> {
            StageImp st = active();
            java.util.HashSet<String> want = new java.util.HashSet<>();
            if (a.has("elements") && a.get("elements").isJsonArray()) for (JsonElement e : a.getAsJsonArray("elements")) want.add(e.getAsString());
            JsonArray items = new JsonArray();
            int[] total = {0};
            collectItems(st.getGameStage(), want, items, total);
            collectItems(st.getUiStage(), want, items, total);
            o.add("elements", items);
            o.addProperty("element_count", total[0]);
            if (CoreTools.b(a, "variables", true)) o.add("variables", variablesOf(st));
            return null;
        });
        if (CoreTools.b(a, "logs", true)) {
            String log = tailLog(false);
            if (!log.isEmpty()) o.addProperty("log", log);
        }
        if (!RUNTIME_ERRORS.isEmpty()) o.addProperty("runtime_error", RUNTIME_ERRORS.get(RUNTIME_ERRORS.size() - 1));
        return o;
    }

    static ToolResult gameState(JsonObject a) throws Exception {
        return ToolResult.text(stateObject(a).toString());
    }

    static ToolResult gameLogs(JsonObject a) throws Exception {
        boolean keep = CoreTools.b(a, "keep", false);
        JsonObject o = new JsonObject();
        String log = tailLog(!keep);
        o.addProperty("log", log);
        JsonArray errs = new JsonArray();
        for (String e : RUNTIME_ERRORS) errs.add(e.length() > 2500 ? e.substring(0, 2500) + "…" : e);
        o.add("runtime_errors", errs);
        if (!keep) RUNTIME_ERRORS.clear();
        return ToolResult.text(o.toString());
    }

    // ================================================================== run_test

    private static Double numeric(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    private static String valueOf(JsonObject st, JsonObject step) {
        String prop = CoreTools.s(step, "prop"), el = CoreTools.s(step, "element");
        if (prop.startsWith("var:")) {
            JsonObject v = st.has("variables") ? st.getAsJsonObject("variables") : new JsonObject();
            JsonElement e = v.get(prop.substring(4));
            return e == null ? null : e.getAsString();
        }
        for (JsonElement e : st.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if (el.equals(o.get("name").getAsString())) {
                JsonElement v = o.get(prop.equals("angle") ? "rotation" : prop);
                return v == null ? null : v.getAsString();
            }
        }
        return null;
    }

    static ToolResult runTest(JsonObject a) throws Exception {
        long t0 = System.currentTimeMillis(), deadline = t0 + 45_000;
        ToolResult result = new ToolResult();
        JsonArray report = new JsonArray();
        boolean allPass = true;
        if (CoreTools.b(a, "start", true)) {
            ToolResult started = buildOrRun(true, a);
            if (started.isError || !started.content.get(0).getAsJsonObject().get("text").getAsString().contains("\"running\":true")) {
                return started;
            }
            deadline = System.currentTimeMillis() + 45_000;
        } else {
            Gl.run(() -> {
                active();
                return null;
            });
            if (!waitReady(30_000)) return ToolResult.error("The game is still loading (loading screen). Wait a few seconds and call run_test again.");
        }
        JsonArray steps = a.has("steps") && a.get("steps").isJsonArray() ? a.getAsJsonArray("steps") : new JsonArray();
        int shots = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (System.currentTimeMillis() > deadline) {
                JsonObject o = new JsonObject();
                o.addProperty("step", i);
                o.addProperty("note", "time budget used up; remaining steps skipped");
                report.add(o);
                break;
            }
            JsonObject s = steps.get(i).getAsJsonObject();
            String d = CoreTools.s(s, "do");
            JsonObject r = new JsonObject();
            r.addProperty("step", i);
            r.addProperty("do", d);
            try {
                switch (d) {
                    case "screenshot": {
                        if (shots >= 4) {
                            r.addProperty("skipped", "at most 4 screenshots per test");
                            break;
                        }
                        Bitmap b = glShot();
                        if (b == null) {
                            r.addProperty("error", "capture failed");
                            break;
                        }
                        ToolResult img = image(b, 480, 60, "step " + i);
                        r.addProperty("label", CoreTools.s(s, "label"));
                        result.content.add(img.content.get(1));
                        shots++;
                        break;
                    }
                    case "read": {
                        JsonObject args = new JsonObject();
                        if (s.has("elements")) args.add("elements", s.get("elements"));
                        args.addProperty("logs", false);
                        r.add("state", stateObject(args));
                        break;
                    }
                    case "assert": {
                        JsonObject args = new JsonObject();
                        JsonArray one = new JsonArray();
                        if (!s.has("prop") || !CoreTools.s(s, "prop").startsWith("var:")) one.add(CoreTools.s(s, "element"));
                        args.add("elements", one);
                        args.addProperty("logs", false);
                        JsonObject st = stateObject(args);
                        String actual = valueOf(st, s);
                        String op = CoreTools.s(s, "op").isEmpty() ? "==" : CoreTools.s(s, "op");
                        String expect = CoreTools.s(s, "value");
                        boolean pass;
                        if (actual == null) pass = false;
                        else {
                            Double x = numeric(actual), y = numeric(expect);
                            double tol = s.has("tol") ? s.get("tol").getAsDouble() : 1.0;
                            if (x != null && y != null) {
                                switch (op) {
                                    case "==": pass = Math.abs(x - y) < 1e-6; break;
                                    case "!=": pass = Math.abs(x - y) >= 1e-6; break;
                                    case ">": pass = x > y; break;
                                    case "<": pass = x < y; break;
                                    case ">=": pass = x >= y; break;
                                    case "<=": pass = x <= y; break;
                                    case "near": pass = Math.abs(x - y) <= tol; break;
                                    default: throw new Gl.ToolError("unknown op '" + op + "'");
                                }
                            } else {
                                switch (op) {
                                    case "==": pass = actual.equals(expect); break;
                                    case "!=": pass = !actual.equals(expect); break;
                                    default: pass = false;
                                }
                            }
                        }
                        r.addProperty("assert", CoreTools.s(s, "element") + "." + CoreTools.s(s, "prop") + " " + op + " " + expect);
                        r.addProperty("actual", actual == null ? "(not found)" : actual);
                        r.addProperty("pass", pass);
                        if (!pass) allPass = false;
                        break;
                    }
                    default: {
                        // an input action or wait
                        JsonArray one = new JsonArray();
                        one.add(s);
                        runActions(one, deadline, new JsonArray());
                    }
                }
            } catch (Gl.ToolError e) {
                r.addProperty("error", e.getMessage());
                allPass = false;
            }
            report.add(r);
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("all_assertions_passed", allPass);
        summary.addProperty("took_ms", System.currentTimeMillis() - t0);
        summary.add("steps", report);
        if (!RUNTIME_ERRORS.isEmpty()) summary.addProperty("runtime_error", RUNTIME_ERRORS.get(RUNTIME_ERRORS.size() - 1));
        String logs = tailLog(false);
        if (!logs.isEmpty()) summary.addProperty("log", logs.length() > 1500 ? logs.substring(logs.length() - 1500) : logs);
        ToolResult finalResult = new ToolResult();
        finalResult.addText(summary.toString());
        for (JsonElement img : result.content) finalResult.content.add(img);
        Gl.run(() -> {
            InputSim.uninstall();
            return null;
        });
        if (CoreTools.b(a, "stop", false)) stop();
        return finalResult;
    }

    // ================================================================== git

    static ToolResult git(JsonObject a) throws Exception {
        final Activity act = RemoteClient.get().activity();
        if (act == null) return ToolResult.error("The app is not on screen.");
        final String action = CoreTools.s(a, "action");
        final Project p = Gl.run(Proj::project);
        final java.util.HashMap<String, String> before = Gl.run(() -> {
            Proj.editor().getProject().save(Proj.editor());
            return Bridge.app().openSceneFingerprints();
        });
        // regenerate STATE.md etc. like a manual commit would not; the user's flow stays the same
        JsonObject o = new JsonObject();
        try (GitRepo r = ProjectGit.open(act, new File(p.getPath()))) {
            switch (action) {
                case "status": {
                    JsonArray ch = new JsonArray();
                    int n = 0;
                    for (GitRepo.Change c : r.status()) {
                        if (n++ >= 100) break;
                        ch.add(c.kind + " " + c.path);
                    }
                    o.addProperty("branch", r.currentBranch());
                    o.addProperty("remote", r.hasRemote() ? r.remoteUrl() : "(none)");
                    o.add("changes", ch);
                    break;
                }
                case "commit":
                case "commit_push": {
                    String msg = CoreTools.s(a, "message");
                    if (msg.isEmpty()) msg = "Update from AI assistant";
                    if (r.status().isEmpty()) {
                        o.addProperty("info", "nothing to commit");
                    } else {
                        o.addProperty("commit", r.commit(msg, null));
                    }
                    if (action.equals("commit_push")) {
                        if (!r.hasRemote()) throw new Gl.ToolError("No GitHub repository is connected. Ask the user to connect GitHub in the Git panel.");
                        r.push(false, com.star4droid.star2d.git.Progress.NONE);
                        o.addProperty("pushed", true);
                    }
                    break;
                }
                case "push":
                    if (!r.hasRemote()) throw new Gl.ToolError("No GitHub repository is connected.");
                    r.push(false, com.star4droid.star2d.git.Progress.NONE);
                    o.addProperty("pushed", true);
                    break;
                case "pull": {
                    if (!r.hasRemote()) throw new Gl.ToolError("No GitHub repository is connected.");
                    GitRepo.MergeOutcome m = r.pull(false, com.star4droid.star2d.git.Progress.NONE);
                    o.addProperty("outcome", m.outcome.toString());
                    if (!m.message.isEmpty()) o.addProperty("message", m.message);
                    if (!m.paths.isEmpty()) o.add("paths", new com.google.gson.Gson().toJsonTree(m.paths));
                    String rep = Gl.run(() -> Bridge.app().applyExternalChanges(before));
                    if (!rep.isEmpty()) o.addProperty("editor", rep);
                    break;
                }
                case "log": {
                    JsonArray l = new JsonArray();
                    for (GitRepo.CommitInfo c : r.log(15)) l.add(c.shortId + " " + c.message);
                    o.add("log", l);
                    break;
                }
                default:
                    throw new Gl.ToolError("unknown action '" + action + "' (status|commit|push|pull|log|commit_push)");
            }
        } catch (com.star4droid.star2d.git.GitException e) {
            return ToolResult.error("git: " + e.getMessage());
        }
        return ToolResult.text(o.toString());
    }
}
