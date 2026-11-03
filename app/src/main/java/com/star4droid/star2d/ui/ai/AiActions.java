package com.star4droid.star2d.ui.ai;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.ItemActions;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.remote.AiBridge;
import com.star4droid.star2d.editor.ui.variables.VarModel;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.blocks.BlockModel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and runs the `star2d-actions` blocks the assistant emits. Execution runs on the GL thread. */
public final class AiActions {
    private AiActions() {}

    public static final Pattern BLOCK = Pattern.compile("```star2d-actions\\s*\\n([\\s\\S]*?)```");

    /** Any fenced block (```json, ```, ```star2d-actions ...). */
    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z0-9_-]*[ \\t]*\\r?\\n?([\\s\\S]*?)```");

    /**
     * Finds the actions in a reply. Strict `star2d-actions` blocks first; weaker models (free OpenRouter ones...) also answer with a
     * ```json fence, a bare ``` fence, or just the raw JSON inside the text, so those are accepted too (only objects that
     * carry an "action" key, or {"name":..,"arguments":..} tool-call shapes).
     */
    public static JsonArray parse(String text) {
        JsonArray all = new JsonArray();
        if (text == null || text.isEmpty()) return all;
        Matcher m = BLOCK.matcher(text);
        while (m.find()) collect(m.group(1), all, false);
        if (all.size() > 0) return all;
        m = FENCE.matcher(text);
        while (m.find()) collect(m.group(1), all, true);
        if (all.size() > 0) return all;
        // raw JSON inside prose: scan for balanced {...} / [...] segments
        int i = 0, n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (ch == '{' || ch == '[') {
                int end = balancedEnd(text, i);
                if (end > i) {
                    int before = all.size();
                    collect(text.substring(i, end + 1), all, true);
                    i = all.size() > before ? end + 1 : i + 1;
                    continue;
                }
            }
            i++;
        }
        return all;
    }

    /** Index of the bracket closing the one at `start` (strings and escapes respected), or -1. */
    private static int balancedEnd(String t, int start) {
        int depth = 0;
        boolean str = false, esc = false;
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
            if (str) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') str = false;
                continue;
            }
            if (c == '"') str = true;
            else if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static void collect(String raw, JsonArray out, boolean needAction) {
        try {
            JsonElement e = JsonParser.parseString(raw.trim());
            if (e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) addAction(x, out, needAction);
            } else addAction(e, out, needAction);
        } catch (Exception ignored) {}
    }

    private static void addAction(JsonElement e, JsonArray out, boolean needAction) {
        if (e == null || !e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        if (!o.has("action")) {
            // tool-call shapes: {"name":"add_element","arguments":{...}} / {"tool":..,"args":..} / {"function":{...}}
            JsonObject f = o.has("function") && o.get("function").isJsonObject() ? o.getAsJsonObject("function") : o;
            String nm = f.has("name") && f.get("name").isJsonPrimitive() ? f.get("name").getAsString()
                    : f.has("tool") && f.get("tool").isJsonPrimitive() ? f.get("tool").getAsString() : null;
            JsonElement ar = f.has("arguments") ? f.get("arguments") : f.has("args") ? f.get("args") : f.has("parameters") ? f.get("parameters") : null;
            if (nm != null && ar != null) {
                try {
                    if (ar.isJsonPrimitive()) ar = JsonParser.parseString(ar.getAsString());
                    if (ar.isJsonObject()) {
                        JsonObject c = ar.getAsJsonObject().deepCopy();
                        c.addProperty("action", nm);
                        out.add(c);
                    }
                } catch (Exception ignored) {}
            }
            if (!needAction && o.size() > 0) out.add(o);
            return;
        }
        out.add(o);
    }

    public static boolean isReadOnly(JsonObject a) {
        String t = s(a, "action");
        return t.equals("read_file") || t.equals("list_files") || t.equals("get_element")
                || t.equals("physics_info") || t.equals("list_templates") || t.equals("list_buttons")
                || t.equals("search_icons") || t.equals("layout_check") || t.equals("list_font_presets") || t.equals("list_behaviors")
                || t.equals("list_ui") || t.equals("get_ui") || t.equals("check_ui") || t.equals("describe_ui")
                || t.equals("behavior_info") || t.equals("get_script_vars");
    }

    /**
     * Actions that are connector tools (physics_info, templates, buttons, icons, scenes...). They wait for the GL thread themselves,
     * so they must run on a WORKER thread: use {@link #runAny}, never {@link #run}, for them.
     */
    public static boolean needsWorker(JsonObject a) {
        switch (s(a, "action")) {
            case "physics_info": case "list_templates": case "apply_template": case "list_buttons": case "create_button":
            case "search_icons": case "import_icon": case "layout_check": case "create_scene":
            case "list_font_presets": case "create_font": case "list_behaviors": case "add_behavior":
            case "list_ui": case "get_ui": case "check_ui": case "create_ui": case "edit_ui": case "describe_ui": case "ui_event": case "build_ui":
            case "behavior_info": case "set_behavior": case "remove_behavior": case "get_script_vars": case "set_script_vars":
                return true;
        }
        return false;
    }

    /** Runs any action from a WORKER thread (not the GL thread): GL actions are posted to the GL thread and awaited, tool actions run here. */
    public static String runAny(JsonObject a) {
        try {
            if (needsWorker(a)) return runTool(a);
            return onGl(() -> run(a));
        } catch (Throwable t) {
            return "Error: " + t.getMessage();
        }
    }

    private static <T> T onGl(Callable<T> c) throws Exception {
        if (Gdx.app == null) throw new Exception("The editor is not ready yet.");
        final AtomicReference<Object> out = new AtomicReference<>();
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Gdx.app.postRunnable(() -> {
            try { out.set(c.call()); } catch (Throwable t) { err.set(t); } finally { done.countDown(); }
        });
        if (!done.await(90, TimeUnit.SECONDS)) throw new Exception("The editor did not answer in time.");
        if (err.get() != null) throw new Exception(String.valueOf(err.get()));
        @SuppressWarnings("unchecked") T r = (T) out.get();
        return r;
    }

    private static JsonObject args(JsonObject a, String... drop) {
        JsonObject o = a.deepCopy();
        o.remove("action");
        for (String d : drop) o.remove(d);
        return o;
    }

    /** The tool behind an action (WORKER thread). Same tools, validation and warnings as the connector. */
    private static String runTool(JsonObject a) {
        String t = s(a, "action");
        JsonObject o = args(a);
        switch (t) {
            case "physics_info": return AiBridge.runTool("physics_info", o);
            case "layout_check": return AiBridge.runTool("layout_check", o);
            case "list_templates": {
                JsonObject x = new JsonObject();
                x.addProperty("action", "list");
                return AiBridge.runTool("templates", x);
            }
            case "apply_template": {
                o.addProperty("action", "apply");
                return AiBridge.runTool("templates", o);
            }
            case "list_buttons": {
                JsonObject x = new JsonObject();
                x.addProperty("action", "list");
                return AiBridge.runTool("buttons", x);
            }
            case "create_button": {
                o.addProperty("action", "create");
                return AiBridge.runTool("buttons", o);
            }
            case "search_icons": {
                o.addProperty("action", "search");
                return AiBridge.runTool("icons", o);
            }
            case "import_icon": {
                o.addProperty("action", "import");
                return AiBridge.runTool("icons", o);
            }
            case "create_scene": {
                o.addProperty("action", "create");
                return AiBridge.runTool("scenes", o);
            }
            case "list_font_presets": {
                JsonObject x = new JsonObject();
                x.addProperty("action", "list");
                return AiBridge.runTool("fonts", x);
            }
            case "create_font": {
                o.addProperty("action", "create");
                return AiBridge.runTool("fonts", o);
            }
            case "list_behaviors": {
                o.addProperty("action", "list");
                return AiBridge.runTool("behaviors", o);
            }
            case "add_behavior": {
                o.addProperty("action", "add");
                return AiBridge.runTool("behaviors", o);
            }
            case "build_ui": {
                o.addProperty("action", "build");
                return AiBridge.runTool("ui", o);
            }
            case "list_ui": case "get_ui": case "check_ui": case "create_ui": case "edit_ui": case "describe_ui": case "ui_event": {
                o.addProperty("action", t.equals("list_ui") ? "list" : t.equals("get_ui") ? "get" : t.equals("check_ui") ? "check" : t.equals("create_ui") ? "create"
                        : t.equals("describe_ui") ? "describe" : t.equals("ui_event") ? "event" : "set");
                return AiBridge.runTool("ui", o);
            }
            case "behavior_info": case "set_behavior": case "remove_behavior": {
                o.addProperty("action", t.equals("behavior_info") ? "info" : t.equals("set_behavior") ? "set" : "remove");
                return AiBridge.runTool("behaviors", o);
            }
            case "get_script_vars": case "set_script_vars": {
                o.addProperty("action", "vars");
                if (t.equals("get_script_vars")) o.remove("values");
                return AiBridge.runTool("behaviors", o);
            }
        }
        return "Unknown action " + t;
    }

    /** One-line human description for the confirmation card. */
    public static String describe(JsonObject a, boolean ar) {
        String t = s(a, "action");
        switch (t) {
            case "read_file": return (ar ? "📄 قراءة " : "📄 Read ") + s(a, "path");
            case "list_files": return (ar ? "📁 عرض ملفات " : "📁 List ") + s(a, "path");
            case "get_element": return (ar ? "🔍 خصائص " : "🔍 Inspect ") + s(a, "name");
            case "set_event_code": return (ar ? "⚡ كتابة الحدث " : "⚡ Set event ") + s(a, "event") + (s(a, "element").isEmpty() ? (ar ? " (المشهد)" : " (scene)") : " · " + s(a, "element"));
            case "add_element": return (ar ? "➕ إضافة " : "➕ Add ") + s(a, "type") + " " + s(a, "name");
            case "set_property": return (ar ? "⚙ " : "⚙ ") + s(a, "element") + "." + s(a, "property") + " = " + s(a, "value");
            case "delete_element": return (ar ? "🗑 حذف " : "🗑 Delete ") + s(a, "element");
            case "add_variable": return (ar ? "𝑥 متغير " : "𝑥 Variable ") + s(a, "type") + " " + s(a, "name") + " = " + s(a, "value");
            case "write_file": return (ar ? "💾 كتابة ملف " : "💾 Write ") + s(a, "path");
            case "apply_template": return (ar ? "🧩 تطبيق قالب " : "🧩 Apply template ") + s(a, "id") + (s(a, "scene").isEmpty() ? "" : " → " + s(a, "scene"));
            case "create_button": return (ar ? "🔘 إنشاء زر " : "🔘 Create button ") + s(a, "name") + (s(a, "preset").isEmpty() ? "" : " (" + s(a, "preset") + ")");
            case "import_icon": return (ar ? "🖼 استيراد أيقونة " : "🖼 Import icon ") + s(a, "id") + (s(a, "name").isEmpty() ? "" : " → " + s(a, "name"));
            case "create_scene": return (ar ? "🎬 مشهد جديد " : "🎬 New scene ") + s(a, "name") + (s(a, "orientation").isEmpty() ? "" : " (" + s(a, "orientation") + ")");
            case "physics_info": return (ar ? "📐 حساب الفيزياء" : "📐 Physics info");
            case "list_templates": return (ar ? "🧩 قائمة القوالب" : "🧩 List templates");
            case "list_buttons": return (ar ? "🔘 أنماط الأزرار" : "🔘 Button presets");
            case "search_icons": return (ar ? "🔎 بحث أيقونات " : "🔎 Search icons ") + s(a, "query");
            case "layout_check": return (ar ? "📏 فحص التخطيط" : "📏 Layout check");
            case "list_font_presets": return (ar ? "🔤 قوالب الخطوط" : "🔤 Font presets");
            case "list_behaviors": return (ar ? "🧩 قائمة السلوكيات" : "🧩 List behaviors");
            case "add_behavior": return (ar ? "🧩 سلوك " : "🧩 Behavior ") + s(a, "id") + " → " + s(a, "element");
            case "list_ui": return (ar ? "🖼 قائمة الواجهات" : "🖼 List UIs");
            case "get_ui": return (ar ? "🖼 قراءة الواجهة " : "🖼 Read UI ") + s(a, "name");
            case "check_ui": return (ar ? "📏 فحص الواجهة " : "📏 Check UI ") + s(a, "name");
            case "build_ui": return (ar ? "🖼 بناء شاشة احترافية " : "🖼 Build screen ") + s(a, "name") + " (" + s(a, "screen") + ")";
            case "create_ui": return (ar ? "🖼 إنشاء واجهة " : "🖼 Create UI ") + s(a, "name") + (s(a, "template").isEmpty() ? "" : " (" + s(a, "template") + ")");
            case "edit_ui": return (ar ? "🖼 تعديل الواجهة " : "🖼 Edit UI ") + s(a, "name");
            case "describe_ui": return (ar ? "🖼 خصائص عناصر الواجهة " : "🖼 UI widget properties ") + s(a, "type");
            case "ui_event": return (ar ? "⚡ كود زر في الواجهة " : "⚡ UI event ") + s(a, "holder") + "." + s(a, "node") + " " + s(a, "event");
            case "behavior_info": return (ar ? "🧩 تفاصيل السلوك " : "🧩 Behavior info ") + s(a, "id");
            case "set_behavior": return (ar ? "🧩 ضبط السلوك " : "🧩 Set behavior ") + s(a, "id") + " → " + s(a, "element");
            case "remove_behavior": return (ar ? "🧩 حذف السلوك " : "🧩 Remove behavior ") + s(a, "id") + " ← " + s(a, "element");
            case "get_script_vars": return (ar ? "𝑥 متغيرات السكربت " : "𝑥 Script variables ") + s(a, "element");
            case "set_script_vars": return (ar ? "𝑥 ضبط متغيرات السكربت " : "𝑥 Set script variables ") + (s(a, "element").isEmpty() ? (ar ? "(المشهد)" : "(scene)") : s(a, "element"));
            case "create_font": return (ar ? "🔤 إنشاء خط " : "🔤 Create font ") + s(a, "name") + (s(a, "preset").isEmpty() ? "" : " (" + s(a, "preset") + ")");
        }
        return "? " + t;
    }

    /** Run one action on the GL thread; returns a short result text. */
    public static String run(JsonObject a) {
        try {
            TestApp app = Bridge.app();
            LibgdxEditor ed = Bridge.editor();
            Project p = ed.getProject();
            String t = s(a, "action");
            switch (t) {
                case "read_file": {
                    File f = safe(p, s(a, "path"));
                    if (!f.exists()) return "File not found: " + s(a, "path");
                    String txt = Gdx.files.absolute(f.getAbsolutePath()).readString();
                    return "Content of " + s(a, "path") + ":\n```\n" + AiContext.trim(txt, 12000) + "\n```";
                }
                case "list_files": {
                    File f = safe(p, s(a, "path"));
                    if (!f.isDirectory()) return "Not a folder: " + s(a, "path");
                    StringBuilder sb = new StringBuilder("Files in " + s(a, "path") + ":\n");
                    File[] l = f.listFiles();
                    if (l != null) for (File x : l) sb.append(x.isDirectory() ? "[dir] " : "- ").append(x.getName()).append("\n");
                    return sb.toString();
                }
                case "get_element": {
                    Actor ac = Bridge.actor(s(a, "name"));
                    if (ac == null) return "No element named " + s(a, "name");
                    return "Properties of " + s(a, "name") + ": " + new Gson().toJson(Bridge.snapshot(PropertySet.getPropertySet(ac)));
                }
                case "set_event_code": {
                    String event = s(a, "event"), el = s(a, "element"), code = s(a, "code");
                    String scene = ed.getScene(), body = "";
                    if (!el.isEmpty()) {
                        Actor ac = Bridge.actor(el);
                        if (ac == null) return "No element named " + el;
                        PropertySet ps = PropertySet.getPropertySet(ac);
                        body = ps.getString(ps.containsKey("Script") ? "Script" : "name");
                        if (body.contains("/")) {
                            String[] v = body.split("/");
                            scene = v[0];
                            body = v[1];
                        }
                    }
                    String path = p.getEventPath(scene, body, event);
                    BlockModel m = new BlockModel();
                    BlockModel.Node root = BlockModel.newRoot();
                    root.x = 40;
                    root.y = 40;
                    BlockModel.Node src = new BlockModel.Node();
                    src.title = "addSource";
                    src.code = "%1$s\n%2$s";
                    src.fields.add(new BlockModel.Field("code", code));
                    root.next = src;
                    m.heads.add(root);
                    m.save(new File(path + ".visual"));
                    return "Event " + event + " written.";
                }
                case "add_element": {
                    String type = s(a, "type").toLowerCase(Locale.ROOT).replace(" ", "");
                    JsonObject props0 = a.has("properties") && a.get("properties").isJsonObject() ? a.getAsJsonObject("properties") : new JsonObject();
                    // 3D scenes: OBJ3D (or a shape name: cube, sphere, model, terrain, water...), LIGHT3D, CAMERA3D; CAMERAVIEW in both kinds
                    String[] k3 = com.star4droid.star2d.remote.AiBridge.kind3D(ed, s(a, "type"), props0);
                    int light = k3 != null ? -1 : type.contains("point") ? 0 : type.contains("directional") ? 1 : type.contains("cone") ? 2 : -1;
                    if (k3 != null) {
                        String err3 = com.star4droid.star2d.remote.AiBridge.add3D(app, ed, k3);
                        if (err3 != null) return "Error: " + err3;
                    } else if (light >= 0) app.getControlLayer().actionAddLight(light);
                    else {
                        String[] kinds = {"box", "circle", "text", "joystick", "progress", "custom", "particle", "camera", "map", "group", "uiholder"};
                        int pos = 0;
                        for (int i = 0; i < kinds.length; i++) if (type.contains(kinds[i])) pos = i;
                        if (type.contains("tiled")) pos = 8;
                        app.getControlLayer().actionAddBody(pos);
                    }
                    StringBuilder res = new StringBuilder("Added " + s(a, "type"));
                    String name = s(a, "name");
                    if (!name.isEmpty()) {
                        String err = PropertyEditing.applyString(app, "name", name);
                        res.append(err == null ? " named " + name : " (rename failed: " + err + ")");
                    }
                    JsonObject props = a.has("properties") && a.get("properties").isJsonObject() ? a.getAsJsonObject("properties") : new JsonObject();
                    for (Map.Entry<String, JsonElement> e : props.entrySet()) {
                        if (k3 != null && (e.getKey().equals("Light Type") || e.getKey().equals("Mesh") || e.getKey().equals("Light Kind"))) continue; // chosen at creation
                        String err = applyProperty(app, e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString());
                        if (err != null) res.append("; ").append(e.getKey()).append(": ").append(err);
                    }
                    Actor added = ed.getSelectedActor();
                    if (added != null) {
                        // same z policy and bounds feedback as the connector tools (background 0, ground 10, actors 20, UI 100...)
                        String zNote = AiBridge.afterAdd(app, ed, added, props);
                        if (!zNote.isEmpty()) res.append("; auto ").append(zNote);
                        appendBounds(res, ed, added.getName());
                    }
                    return res.toString();
                }
                case "set_property": {
                    Actor ac = Bridge.actor(s(a, "element"));
                    if (ac == null) return "No element named " + s(a, "element");
                    ed.selectActor(ac);
                    String prop = s(a, "property"), val = s(a, "value");
                    String clamp = "";
                    if (prop.equalsIgnoreCase("z")) {
                        try {
                            if (Float.parseFloat(val) < 0) {
                                val = "0";
                                clamp = " (negative z is not allowed: set to 0; background 0, ground 10, actors 20, UI 100+)";
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    String err = applyProperty(app, prop, val);
                    if (err != null) return err;
                    StringBuilder out = new StringBuilder("Set " + prop + clamp);
                    String pl = prop.toLowerCase(Locale.ROOT);
                    if (pl.equals("x") || pl.equals("y") || pl.equals("width") || pl.equals("height") || pl.equals("radius"))
                        appendBounds(out, ed, s(a, "element"));
                    return out.toString();
                }
                case "delete_element": {
                    Actor ac = Bridge.actor(s(a, "element"));
                    if (ac == null) return "No element named " + s(a, "element");
                    ItemActions.delete(app, ac);
                    return "Deleted " + s(a, "element");
                }
                case "add_variable": {
                    FileHandle fh = Gdx.files.absolute(p.getVariables(ed.getScene()));
                    ArrayList<VarModel> list = null;
                    if (fh.exists()) try { list = new Gson().fromJson(fh.readString(), new TypeToken<ArrayList<VarModel>>() {}.getType()); } catch (Exception ignored) {}
                    if (list == null) list = new ArrayList<>();
                    for (VarModel v : list) if (v.getName().equals(s(a, "name"))) return "Variable already exists: " + s(a, "name");
                    list.add(new VarModel().setType(s(a, "type").isEmpty() ? "int" : s(a, "type")).setName(s(a, "name")).setValue(s(a, "value")));
                    fh.writeString(new Gson().toJson(list), false);
                    try { app.getControlLayer().getVarsItem().setFileHandle(fh); } catch (Throwable ignored) {}
                    return "Variable " + s(a, "name") + " added";
                }
                case "write_file": {
                    File f = safe(p, s(a, "path"));
                    Gdx.files.absolute(f.getAbsolutePath()).writeString(s(a, "content"), false);
                    return "Wrote " + s(a, "path");
                }
            }
            return "Unknown action " + t;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static void appendBounds(StringBuilder sb, LibgdxEditor ed, String name) {
        try {
            java.util.List<String> w = AiBridge.bounds(ed, java.util.Collections.singleton(name));
            for (String x : w) sb.append("; WARNING: ").append(x);
        } catch (Throwable ignored) {}
    }

    private static HashMap<String, String> types;

    /** Apply a property by its schema type (GL thread, acts on the selected element). */
    public static String applyProperty(TestApp app, String field, String value) {
        if (types == null) {
            types = new HashMap<>();
            try {
                JsonObject o = JsonParser.parseString(Gdx.files.internal("types.json").readString()).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet())
                    for (String f : e.getValue().getAsString().split(",")) if (!f.trim().isEmpty()) types.put(f.trim(), e.getKey());
            } catch (Exception ignored) {}
        }
        if (app.getEditor().getSelectedActor() == null) return "nothing selected";
        String type = types.get(field);
        if (type == null) type = "spinner";
        switch (type) {
            case "float":
                try { PropertyEditing.applyFloat(app, field, Float.parseFloat(value)); } catch (NumberFormatException e) { return "not a number"; }
                return null;
            case "boolean": PropertyEditing.applyBoolean(app, field, String.valueOf(Boolean.parseBoolean(value))); return null;
            case "string": return PropertyEditing.applyString(app, field, value);
            case "color": PropertyEditing.applyColor(app, field, value.replace("#", "")); return null;
            case "image": {
                // "", "reset", "default" or "none" = back to the default picture (empty path), not "/"
                String lv = value == null ? "" : value.trim();
                if (lv.isEmpty() || lv.equalsIgnoreCase("reset") || lv.equalsIgnoreCase("default") || lv.equalsIgnoreCase("none")) lv = "";
                else if (!lv.startsWith("/")) lv = "/" + lv;
                PropertyEditing.applyImage(app, field, lv);
                return null;
            }
            case "File": PropertyEditing.applyFile(app, field, value.startsWith("/") ? value : "/" + value); return null;
            case "body": return PropertyEditing.applyBody(app, field, value);
            case "script": PropertyEditing.applyScript(app, field, value); return null;
            case "static": case "points": return "read-only here";
            default: PropertyEditing.applySpinner(app, field, value); return null;
        }
    }

    private static File safe(Project p, String rel) throws Exception {
        File root = new File(p.getPath()).getCanonicalFile();
        File f = new File(root, rel.startsWith("/") ? rel.substring(1) : rel).getCanonicalFile();
        if (!f.getPath().startsWith(root.getPath())) throw new Exception("Path outside the project: " + rel);
        return f;
    }

    static String s(JsonObject o, String k) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? "" : (e.isJsonPrimitive() ? e.getAsString() : e.toString());
        } catch (Exception ex) {
            return "";
        }
    }
}
