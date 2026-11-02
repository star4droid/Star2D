package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * layout_check: geometry-only review of a scene (no picture, cheap). UI elements live in SCREEN space
 * (0,0 = bottom-left of the logic screen), so they are checked against the logic size; world elements are not.
 */
final class LayoutCheck {
    private LayoutCheck() {}

    private static final String[] TOUCH_EVENTS = {"onClick", "onTouchStart", "onTouchEnd"};
    private static final float MIN_TOUCH = 48f;

    private static float f(LinkedHashMap<String, String> m, String k, float def) {
        String v = m.get(k);
        if (v == null || v.isEmpty()) return def;
        try {
            return Float.parseFloat(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static String type(LinkedHashMap<String, String> m) {
        return String.valueOf(m.get("TYPE")).toUpperCase();
    }

    private static boolean isUi(LinkedHashMap<String, String> m) {
        if (type(m).equals("GROUP") || SceneInfo.is3DType(type(m))) return false; // 3D elements: meters, not screen pixels
        if (type(m).equals("UIHOLDER")) return true;
        return "UI".equalsIgnoreCase(m.get("type")) || type(m).equals("JOYSTICK");
    }

    private static float[] rect(LinkedHashMap<String, String> m) {
        float r = f(m, "radius", 0);
        float w = f(m, "width", r * 2), h = f(m, "height", r * 2);
        return new float[]{f(m, "x", 0), f(m, "y", 0), w, h};
    }

    private static boolean touchy(LibgdxEditor ed, Project p, String scene, LinkedHashMap<String, String> m) {
        if (type(m).equals("JOYSTICK")) return true;
        try {
            String[] sb = LogicTools.bodyOf(p, ed, scene, m.get("name"));
            for (String ev : TOUCH_EVENTS) if (LogicTools.hasCode(p.getEventPath(sb[0], sb[1], ev))) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    static ToolResult check(JsonObject a) throws Exception {
        final String want = CoreTools.s(a, "scene");
        final JsonObject out = new JsonObject();
        Gl.run(() -> {
            LibgdxEditor ed = Proj.editor();
            Project p = ed.getProject();
            String scene = want.isEmpty() ? ed.getScene() : want;
            if (!Proj.scenes(p).contains(scene)) throw new Gl.ToolError("No scene named '" + scene + "'. Scenes: " + Proj.scenes(p));
            boolean live = scene.equals(ed.getScene());
            if (live && ed.isLoading()) throw new Gl.ToolError("The scene is still loading, try again in a moment.");
            SceneInfo.Frame frame = SceneInfo.of(ed, p, scene); // orientation-aware (portrait swaps the stored sizes)
            float sw = frame.width, sh = frame.height;
            List<LinkedHashMap<String, String>> all = live ? CoreTools.liveElements(ed) : CoreTools.fileElements(p, scene);
            ArrayList<LinkedHashMap<String, String>> ui = new ArrayList<>();
            for (LinkedHashMap<String, String> m : all) {
                if ("false".equalsIgnoreCase(m.get("Visible"))) continue;
                if (isUi(m)) ui.add(m);
            }
            JsonArray problems = new JsonArray();
            // 1. UI outside the logic screen
            for (LinkedHashMap<String, String> m : ui) {
                float[] r = rect(m);
                StringBuilder sides = new StringBuilder();
                if (r[0] < -0.5f) sides.append("left ").append(Math.round(-r[0])).append("px ");
                if (r[1] < -0.5f) sides.append("bottom ").append(Math.round(-r[1])).append("px ");
                if (r[0] + r[2] > sw + 0.5f) sides.append("right ").append(Math.round(r[0] + r[2] - sw)).append("px ");
                if (r[1] + r[3] > sh + 0.5f) sides.append("top ").append(Math.round(r[1] + r[3] - sh)).append("px ");
                if (sides.length() > 0) add(problems, "outside_screen", m.get("name"), null, "sticks out of the " + Math.round(sw) + "x" + Math.round(sh) + " screen by " + sides.toString().trim() + ". Move/resize it with elements set.");
            }
            // 2. overlaps between UI elements, text under its box
            for (int i = 0; i < ui.size(); i++) {
                for (int j = i + 1; j < ui.size(); j++) {
                    LinkedHashMap<String, String> A = ui.get(i), B = ui.get(j);
                    float[] ra = rect(A), rb = rect(B);
                    float ix = Math.min(ra[0] + ra[2], rb[0] + rb[2]) - Math.max(ra[0], rb[0]);
                    float iy = Math.min(ra[1] + ra[3], rb[1] + rb[3]) - Math.max(ra[1], rb[1]);
                    if (ix <= 0 || iy <= 0) continue;
                    float inter = ix * iy, areaA = Math.max(1, ra[2] * ra[3]), areaB = Math.max(1, rb[2] * rb[3]);
                    boolean aSmall = areaA <= areaB;
                    LinkedHashMap<String, String> small = aSmall ? A : B, big = aSmall ? B : A;
                    float smallArea = aSmall ? areaA : areaB;
                    String st = type(small);
                    boolean label = (st.equals("TEXT") || st.equals("PROGRESS")) && inter >= 0.8f * smallArea; // a label/bar lying on a box is normal
                    if (label) {
                        if (st.equals("TEXT") && f(small, "z", 0) < f(big, "z", 0))
                            add(problems, "text_hidden", small.get("name"), big.get("name"), "TEXT is drawn UNDER '" + big.get("name") + "' (z " + Math.round(f(small, "z", 0)) + " < " + Math.round(f(big, "z", 0)) + "): raise its z.");
                        continue;
                    }
                    if (inter > 0.2f * smallArea)
                        add(problems, "overlap", A.get("name"), B.get("name"), Math.round(100 * inter / smallArea) + "% of the smaller one overlaps. Move one of them.");
                }
            }
            // 3. text too narrow (wraps)
            if (live) for (String w : Warnings.text(ed, null)) add(problems, "text_wraps", null, null, w);
            // 4. tiny touch targets
            for (LinkedHashMap<String, String> m : ui) {
                float[] r = rect(m);
                if ((r[2] < MIN_TOUCH || r[3] < MIN_TOUCH) && touchy(ed, p, scene, m))
                    add(problems, "small_touch", m.get("name"), null, "touch target is " + Math.round(r[2]) + "x" + Math.round(r[3]) + " px; fingers need >= " + Math.round(MIN_TOUCH) + " px. Enlarge it.");
            }
            // 5. draw order (z): background covering things, hidden elements, UI under the world (2D elements only)
            ZPolicy.check(all, sw, sh, problems);
            // 6. 3D scene: the 3D world is drawn first, the 2D layer on top; it needs a main camera
            int n3d = 0;
            if (frame.is3D) {
                boolean activeCam = false, anyCam = false;
                for (LinkedHashMap<String, String> m : all) {
                    if (!SceneInfo.is3DElement(m)) continue;
                    n3d++;
                    if (type(m).equals("CAMERA3D")) {
                        anyCam = true;
                        if (!"false".equalsIgnoreCase(m.get("Active"))) activeCam = true;
                    }
                }
                if (!anyCam)
                    add(problems, "no_camera3d", null, null, "3D scene without a CAMERA3D: add one (elements add {type:'CAMERA3D', props:{Active:true}}) so the game has a main camera that you control (position, Follow, Follow Mode).");
                else if (!activeCam)
                    add(problems, "no_active_camera3d", null, null, "No CAMERA3D has Active true: set Active true on the main camera.");
            }
            out.addProperty("scene", scene);
            out.addProperty("sceneType", frame.is3D ? "3D" : "2D");
            if (frame.is3D) {
                out.addProperty("elements_3d", n3d);
                out.addProperty("note_3d", "Only the 2D layer (px) and the 3D camera are checked here; 3D positions are meters. Look at the 3D world with screenshot.");
            }
            out.addProperty("frame", SceneInfo.describe(frame));
            out.addProperty("screen", Math.round(sw) + "x" + Math.round(sh));
            out.addProperty("ui_elements", ui.size());
            out.addProperty("ok", problems.size() == 0);
            if (problems.size() > 0) out.add("problems", problems);
            else out.addProperty("note", "No geometry problems. Look at it with screenshot (target scene).");
            return null;
        });
        return ToolResult.text(out.toString());
    }

    static void add(JsonArray arr, String kind, String a, String b, String msg) {
        if (arr.size() >= 40) return;
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        if (a != null) o.addProperty("a", a);
        if (b != null) o.addProperty("b", b);
        o.addProperty("msg", msg);
        arr.add(o);
    }
}
