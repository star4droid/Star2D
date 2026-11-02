package com.star4droid.star2d.remote;

import android.app.Activity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.git.AiDocs;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Session, reading and overview tools. */
final class CoreTools {
    private CoreTools() {}

    static JsonObject obj() {
        return new JsonObject();
    }

    static String s(JsonObject a, String k) {
        JsonElement e = a.get(k);
        return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    static int i(JsonObject a, String k, int def) {
        try {
            JsonElement e = a.get(k);
            return e == null || e.isJsonNull() ? def : (int) Math.round(e.getAsDouble());
        } catch (Exception ex) {
            return def;
        }
    }

    static boolean b(JsonObject a, String k, boolean def) {
        try {
            JsonElement e = a.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsBoolean();
        } catch (Exception ex) {
            return def;
        }
    }

    // ------------------------------------------------------------------ status

    static ToolResult status() throws Exception {
        final Activity act = RemoteClient.get().activity();
        JsonObject o = new JsonObject();
        o.addProperty("connected", true);
        o.addProperty("app", "Star2D");
        o.addProperty("working", RemoteSession.isWorking());
        // write the plan, task notes and finish_work summary in the language the user writes to you in (this is the app's language)
        o.addProperty("app_language", com.star4droid.star2d.ui.Tr.lang());
        o.addProperty("plan_language", "Write set_plan title/tasks, update_task notes and the finish_work summary in the language of the user's messages to you (app language: " + com.star4droid.star2d.ui.Tr.lang() + ").");
        boolean ready = Gl.run(() -> Bridge.ready());
        o.addProperty("project_open", ready);
        if (!ready) {
            o.addProperty("hint", "No project is open. Ask the user to open or create the project to work on, and keep the editor on screen.");
            return ToolResult.text(o.toString());
        }
        Gl.run(() -> {
            TestApp app = Bridge.app();
            LibgdxEditor ed = app.getEditor();
            Project p = ed.getProject();
            o.addProperty("project", new File(p.getPath()).getName());
            o.addProperty("scene", ed.getScene());
            JsonArray sc = new JsonArray();
            for (String n : Proj.scenes(p)) sc.add(n);
            o.add("scenes", sc);
            JsonArray tabs = new JsonArray();
            for (LibgdxEditor e : app.editors) tabs.add(e.getScene());
            o.add("open_tabs", tabs);
            o.addProperty("playing", app.isPlaying());
            o.addProperty("loading", ed.isLoading());
            if (!ed.isLoading()) {
                List<LinkedHashMap<String, String>> live = liveElements(ed);
                o.addProperty("elements", live.size());
                // changes whenever anything in the open scene changes: compare with the value from your last call to skip re-reading
                o.addProperty("scene_rev", Integer.toHexString(live.toString().hashCode()));
            }
            ed.getConfig(); // the orientation flag follows the config file: reload before reading it
            o.addProperty("orientation", ed.isLandscape() ? "landscape" : "portrait");
            // plain words first: this is the frame every x/y/width/height you type lives in
            o.addProperty("frame", SceneInfo.describe(ed, p, ed.getScene()));
            o.addProperty("sceneType", !ed.isLoading() && ed.is3DScene() ? "3D" : "2D");
            o.addProperty("project_orientation", Proj.projectOrientation(null));
            JsonObject logic = new JsonObject();
            logic.addProperty("width", ed.getLogicWidth());
            logic.addProperty("height", ed.getLogicHeight());
            o.add("logic_size", logic);
            if (ed.logicSizeLooksSwapped()) o.addProperty("logic_size_warning", "stored logicWidth < logicHeight: the frame is reversed. Fix with scenes configure {swap_size:true}");
            JsonObject screen = new JsonObject();
            screen.addProperty("width", Gdx.graphics.getWidth());
            screen.addProperty("height", Gdx.graphics.getHeight());
            o.add("screen_px", screen);
            return null;
        });
        return ToolResult.text(o.toString());
    }

    // ------------------------------------------------------------------ docs

    static ToolResult readDocs(JsonObject a) throws Exception {
        Activity act = RemoteClient.get().activity();
        if (act == null) return ToolResult.error("The app is not on screen.");
        String topic = s(a, "topic").trim(), section = s(a, "section").trim(), search = s(a, "search").trim();
        boolean full = b(a, "full", false);
        return ToolResult.text(RemoteDocs.read(act, topic, section, search, full));
    }

    // ------------------------------------------------------------------ plan

    static ToolResult setPlan(JsonObject a) {
        String title = s(a, "title");
        List<String[]> tasks = new ArrayList<>();
        if (a.has("tasks") && a.get("tasks").isJsonArray()) {
            int n = 0;
            for (JsonElement e : a.getAsJsonArray("tasks")) {
                if (!e.isJsonObject()) continue;
                JsonObject t = e.getAsJsonObject();
                String id = s(t, "id");
                if (id.isEmpty()) id = "t" + (++n);
                tasks.add(new String[]{id, s(t, "title")});
            }
        }
        if (tasks.isEmpty()) return ToolResult.error("tasks is empty: give at least one task {id,title}");
        RemoteSession.setPlan(title, tasks);
        return ToolResult.text("Plan shown (" + tasks.size() + " tasks). The editor is now locked for the user. Use update_task as you progress and finish_work at the end.");
    }

    static ToolResult updateTask(JsonObject a) {
        StringBuilder unknown = new StringBuilder();
        if (a.has("updates") && a.get("updates").isJsonArray()) {
            for (JsonElement e : a.getAsJsonArray("updates")) {
                if (!e.isJsonObject()) continue;
                JsonObject u = e.getAsJsonObject();
                if (!RemoteSession.updateTask(s(u, "id"), s(u, "status"), i(u, "progress", -1), u.has("note") ? s(u, "note") : null))
                    unknown.append(s(u, "id")).append(' ');
            }
        }
        if (a.has("id") && !s(a, "id").isEmpty()) {
            if (!RemoteSession.updateTask(s(a, "id"), s(a, "status"), i(a, "progress", -1), a.has("note") ? s(a, "note") : null))
                unknown.append(s(a, "id")).append(' ');
        }
        return unknown.length() == 0 ? ToolResult.text("ok") : ToolResult.error("unknown task id(s): " + unknown.toString().trim());
    }

    static ToolResult finishWork(JsonObject a) {
        RemoteSession.finish(s(a, "summary"));
        return ToolResult.text("Work finished. The editor is unlocked for the user.");
    }

    // ------------------------------------------------------------------ project overview

    static ToolResult projectInfo() throws Exception {
        JsonObject o = new JsonObject();
        Gl.run(() -> {
            TestApp app = Bridge.app();
            LibgdxEditor ed = Proj.editor();
            Project p = ed.getProject();
            o.addProperty("project", new File(p.getPath()).getName());
            o.addProperty("open_scene", ed.getScene());
            ed.getConfig();
            o.addProperty("orientation", ed.isLandscape() ? "landscape" : "portrait");
            o.addProperty("frame", SceneInfo.describe(ed, p, ed.getScene()));
            o.addProperty("project_orientation", Proj.projectOrientation(null));
            JsonObject logic = new JsonObject();
            logic.addProperty("width", ed.getLogicWidth());
            logic.addProperty("height", ed.getLogicHeight());
            o.add("logic_size", logic);
            if (ed.logicSizeLooksSwapped()) o.addProperty("logic_size_warning", "stored logicWidth < logicHeight: the frame is reversed. Fix with scenes configure {swap_size:true}");
            JsonArray scenes = new JsonArray();
            for (String n : Proj.scenes(p)) {
                JsonObject so = new JsonObject();
                so.addProperty("name", n);
                FileHandle sf = Gdx.files.absolute(p.getScenesPath() + n);
                int count = 0;
                try {
                    String txt = sf.exists() ? sf.readString() : "";
                    if (!txt.isEmpty()) count = JsonParser.parseString(txt).getAsJsonArray().size();
                } catch (Exception ignored) {
                }
                if (n.equals(ed.getScene())) count = countItems(ed);
                so.addProperty("elements", count);
                FileHandle cf = Gdx.files.absolute(p.getConfig(n));
                if (cf.exists()) {
                    try {
                        JsonObject c = JsonParser.parseString(cf.readString()).getAsJsonObject();
                        if (c.has("or")) so.addProperty("orientation", c.get("or").getAsString().isEmpty() ? "landscape" : c.get("or").getAsString());
                        else so.addProperty("orientation", "landscape");
                    } catch (Exception ignored) {
                    }
                }
                so.addProperty("frame", SceneInfo.describe(ed, p, n));
                so.addProperty("sceneType", SceneInfo.is3DScene(ed, p, n) ? "3D" : "2D");
                scenes.add(so);
            }
            o.add("scenes", scenes);
            JsonArray tabs = new JsonArray();
            for (LibgdxEditor e : app.editors) tabs.add(e.getScene());
            o.add("open_tabs", tabs);
            o.add("assets", assets(p));
            File log = new File(p.getPath(), AiDocs.BUILD_LOG);
            if (log.exists()) {
                String t = Gdx.files.absolute(log.getAbsolutePath()).readString();
                o.addProperty("last_build", t.length() > 1500 ? t.substring(0, 1500) + "…" : t);
            }
            o.addProperty("git", new File(p.getPath(), ".git").exists());
            return null;
        });
        return ToolResult.text(o.toString());
    }

    private static int countItems(LibgdxEditor ed) {
        int n = 0;
        for (Actor a : ed.getActors()) if (a instanceof EditorItem) n++;
        return n;
    }

    private static JsonObject assets(Project p) {
        JsonObject o = new JsonObject();
        for (String dir : new String[]{"images", "sounds", "anims", "files", "scripts", "models", "shaders"}) {
            JsonArray arr = new JsonArray();
            ArrayList<String> names = new ArrayList<>();
            collect(Gdx.files.absolute(p.get(dir)), "", names, 150);
            for (String n : names) arr.add(n);
            o.add(dir, arr);
        }
        ArrayList<String> java = new ArrayList<>();
        collect(Gdx.files.absolute(p.get("java")), "", java, 60);
        JsonArray ja = new JsonArray();
        for (String n : java) if (n.endsWith(".java") && !n.startsWith("com/star4droid/Game/") ) ja.add(n);
        o.add("java_files", ja);
        return o;
    }

    private static void collect(FileHandle dir, String prefix, List<String> out, int max) {
        if (!dir.exists() || !dir.isDirectory()) return;
        FileHandle[] list = dir.list();
        java.util.Arrays.sort(list, (x, y) -> x.name().compareToIgnoreCase(y.name()));
        for (FileHandle f : list) {
            if (out.size() >= max) return;
            if (f.isDirectory()) collect(f, prefix + f.name() + "/", out, max);
            else out.add(prefix + f.name());
        }
    }

    // ------------------------------------------------------------------ scene contents

    /** Live properties of the elements of the open scene (name -> all props), in z order. GL thread. */
    static List<LinkedHashMap<String, String>> liveElements(LibgdxEditor ed) {
        ArrayList<LinkedHashMap<String, String>> out = new ArrayList<>();
        for (Actor a : ed.getActors()) {
            if (!(a instanceof EditorItem)) continue;
            PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
            if (ps == null || !ps.containsKey("name")) continue;
            out.add(Bridge.snapshot(ps));
        }
        java.util.Collections.sort(out, (x, y) -> Float.compare(Bridge.num(x.get("z")), Bridge.num(y.get("z"))));
        return out;
    }

    /** Elements of a scene that is not open, straight from its file. */
    static List<LinkedHashMap<String, String>> fileElements(Project p, String scene) {
        ArrayList<LinkedHashMap<String, String>> out = new ArrayList<>();
        try {
            FileHandle f = Gdx.files.absolute(p.getScenesPath() + scene);
            if (!f.exists() || f.length() == 0) return out;
            JsonArray arr = JsonParser.parseString(f.readString()).getAsJsonArray();
            for (JsonElement e : arr) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet())
                    m.put(en.getKey(), en.getValue().isJsonPrimitive() ? en.getValue().getAsString() : en.getValue().toString());
                out.add(m);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static JsonObject nonDefault(LinkedHashMap<String, String> props) {
        JsonObject o = new JsonObject();
        LinkedHashMap<String, String> def = ItemDefaults.of(props.get("TYPE"), props.get("Light Type"));
        for (Map.Entry<String, String> e : props.entrySet()) {
            String k = e.getKey();
            if (k.equals("name") || k.equals("TYPE")) {
                o.addProperty(k, e.getValue());
                continue;
            }
            if (k.equals("Version") || k.equals("pbase") || k.equals("pid")) continue;
            String d = def.get(k);
            if (d != null && ItemDefaults.same(e.getValue(), d)) continue;
            // "Script" defaults to the element's own name
            if (k.equals("Script") && e.getValue().equals(props.get("name"))) continue;
            o.addProperty(k, e.getValue());
        }
        return o;
    }

    static ToolResult sceneDump(JsonObject a) throws Exception {
        final String want = s(a, "scene");
        final JsonObject o = new JsonObject();
        Gl.run(() -> {
            LibgdxEditor ed = Proj.editor();
            Project p = ed.getProject();
            String scene = want.isEmpty() ? ed.getScene() : want;
            if (!Proj.scenes(p).contains(scene)) throw new Gl.ToolError("No scene named '" + scene + "'. Scenes: " + Proj.scenes(p));
            o.addProperty("scene", scene);
            boolean live = scene.equals(ed.getScene());
            if (live && ed.isLoading()) throw new Gl.ToolError("The scene is still loading, try again in a moment.");
            o.addProperty("frame", SceneInfo.describe(ed, p, scene));
            o.addProperty("sceneType", SceneInfo.is3DScene(ed, p, scene) ? "3D" : "2D");
            FileHandle cf = Gdx.files.absolute(p.getConfig(scene));
            try {
                if (cf.exists()) o.add("config", JsonParser.parseString(cf.readString()));
            } catch (Exception ignored) {
            }
            JsonArray els = new JsonArray();
            boolean brief = b(a, "brief", false);
            for (LinkedHashMap<String, String> m : live ? liveElements(ed) : fileElements(p, scene)) {
                if (brief && SceneInfo.is3DElement(m)) // meters: position x,y,z, rotation and scale
                    els.add(m.get("name") + " " + m.get("TYPE") + (m.containsKey("Mesh") ? " " + m.get("Mesh") : m.containsKey("Light Kind") ? " " + m.get("Light Kind") : "")
                            + " @" + m.get("x") + "," + m.get("y") + "," + m.get("z") + " m rot " + m.get("Rotation X") + "," + m.get("Rotation Y") + "," + m.get("Rotation Z")
                            + (m.containsKey("Scale X") ? " scale " + m.get("Scale X") + "," + m.get("Scale Y") + "," + m.get("Scale Z") : "")
                            + (m.get("Model") != null && !m.get("Model").isEmpty() ? " " + m.get("Model") : "")
                            + (m.get("Body") != null && !"NONE".equals(m.get("Body")) ? " body " + m.get("Body") : ""));
                else if (brief) els.add(m.get("name") + " " + m.get("TYPE") + " @" + m.get("x") + "," + m.get("y") + " " + m.get("width") + "x" + m.get("height")
                        + (m.get("image") != null && !m.get("image").isEmpty() ? " " + m.get("image") : ""));
                else els.add(nonDefault(m));
            }
            o.add("elements", els);
            o.add("joints", EditTools.jointsOf(p, scene));
            FileHandle vf = Gdx.files.absolute(p.getVariables(scene));
            try {
                if (vf.exists()) o.add("variables", JsonParser.parseString(vf.readString()));
            } catch (Exception ignored) {
            }
            o.add("events", EditTools.eventsOf(p, scene));
            return null;
        });
        return ToolResult.text(o.toString());
    }

    static ToolResult getElements(JsonObject a) throws Exception {
        final JsonObject o = new JsonObject();
        final String scene = s(a, "scene");
        if (!scene.isEmpty()) Proj.openScene(scene);
        Gl.run(() -> {
            LibgdxEditor ed = Proj.editor();
            if (ed.isLoading()) throw new Gl.ToolError("The scene is still loading, try again in a moment.");
            java.util.HashSet<String> names = new java.util.HashSet<>();
            if (a.has("names") && a.get("names").isJsonArray()) for (JsonElement e : a.getAsJsonArray("names")) names.add(e.getAsString());
            String type = s(a, "type").toUpperCase();
            java.util.HashSet<String> props = new java.util.HashSet<>();
            if (a.has("props") && a.get("props").isJsonArray()) for (JsonElement e : a.getAsJsonArray("props")) props.add(e.getAsString());
            JsonArray els = new JsonArray();
            ArrayList<String> missing = new ArrayList<>(names);
            for (LinkedHashMap<String, String> m : liveElements(ed)) {
                String n = m.get("name");
                if (!names.isEmpty() && !names.contains(n)) continue;
                if (!type.isEmpty() && !type.equals(String.valueOf(m.get("TYPE")).toUpperCase())) continue;
                missing.remove(n);
                JsonObject e = new JsonObject();
                if (props.isEmpty() && !b(a, "full", false)) {
                    els.add(nonDefault(m)); // only what differs from the defaults
                    continue;
                }
                for (Map.Entry<String, String> en : m.entrySet())
                    if (props.isEmpty() || props.contains(en.getKey()) || en.getKey().equals("name")) e.addProperty(en.getKey(), en.getValue());
                els.add(e);
            }
            o.add("elements", els);
            if (!missing.isEmpty()) o.add("not_found", new Gson().toJsonTree(missing));
            return null;
        });
        return ToolResult.text(o.toString());
    }
}
