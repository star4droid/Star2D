package com.star4droid.star2d.remote;

import android.content.Context;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.evo.star2dApp;
import com.star4droid.star2d.ui.blocks.BlockCatalog;
import com.star4droid.star2d.ui.blocks.BlockInfo;
import com.star4droid.star2d.ui.blocks.BlockModel;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Events (as visual blocks or raw Java), the block catalog and custom blocks. */
final class LogicTools {
    private LogicTools() {}

    static final String[] SCENE_EVENTS = {"OnCreate", "OnStep", "OnPause", "OnResume", "onCollisionStart", "onCollisionEnd",
            "onPathEnd", "onNoPath", "onPathBlocked", "onTimelineEnd", "onTimelineMarker", "onTweenEnd", "onPlayerJoined", "onPlayerLeft",
            "onGameFound", "onConnected", "onDisconnected", "onHostLeft", "onNetMessage", "onGameStarted", "onJoinFailed"};
    static final String[] ELEMENT_EVENTS = {"onClick", "OnBodyCreated", "OnBodyUpdate", "onTouchStart", "onTouchEnd", "onBodyCollided", "onBodyCollideEnd"};

    private static Context ctx() {
        Context c = RemoteClient.get().activity();
        if (c == null) c = star2dApp.getContext();
        if (c == null) throw new Gl.ToolError("The app is not on screen.");
        return c.getApplicationContext();
    }

    private static void ensureCatalog() {
        BlockCatalog.load(ctx());
        // blocks of the @Block methods of behaviors (built-in and the project's own sources)
        try {
            BlockCatalog.applyBehaviorBlocks(Proj.project().getPath());
        } catch (Throwable ignored) {
        }
    }

    // ================================================================== event names

    /** Canonical event name for the scope, accepting common aliases and any capitalization; throws a helpful error. */
    static String eventName(boolean element, String raw) {
        String[] valid = element ? ELEMENT_EVENTS : SCENE_EVENTS;
        for (String v : valid) if (v.equalsIgnoreCase(raw)) return v;
        String l = raw.toLowerCase(Locale.ROOT).replace("_", "");
        String alias = null;
        if (element) {
            if (l.equals("update") || l.equals("onupdate") || l.equals("onstep") || l.equals("step") || l.equals("onbodystep")) alias = "OnBodyUpdate";
            else if (l.equals("oncreate") || l.equals("create") || l.equals("onbodycreate") || l.equals("oncreated")) alias = "OnBodyCreated";
            else if (l.equals("ontouchdown") || l.equals("touchdown")) alias = "onTouchStart";
            else if (l.equals("ontouchup") || l.equals("touchup")) alias = "onTouchEnd";
            else if (l.equals("oncollisionstart") || l.equals("oncollisionbegin") || l.equals("oncollision") || l.equals("oncollide")) alias = "onBodyCollided";
            else if (l.equals("oncollisionend")) alias = "onBodyCollideEnd";
            else if (l.equals("ontap") || l.equals("click")) alias = "onClick";
        } else {
            if (l.equals("onupdate") || l.equals("update") || l.equals("onstep") || l.equals("step")) alias = "OnStep";
            else if (l.equals("oncreate") || l.equals("create") || l.equals("start") || l.equals("onstart")) alias = "OnCreate";
            else if (l.equals("oncollisionbegin")) alias = "onCollisionStart";
        }
        if (alias != null) return alias;
        throw new Gl.ToolError("Unknown " + (element ? "element" : "scene") + " event '" + raw + "'. Valid: " + String.join(", ", valid));
    }

    // ================================================================== locating event files

    /** [scene, body] for the element's events (body is the element's Script property, "scene/name" allowed). */
    static String[] bodyOf(Project p, LibgdxEditor ed, String scene, String element) {
        String script = null;
        if (scene.equals(ed.getScene())) {
            com.badlogic.gdx.scenes.scene2d.Actor ac = com.star4droid.star2d.ui.Bridge.actor(element);
            if (ac == null) throw new Gl.ToolError("No element named '" + element + "' in scene '" + scene + "'");
            com.star4droid.star2d.Helpers.PropertySet<String, Object> ps = com.star4droid.star2d.Helpers.PropertySet.getPropertySet(ac);
            script = ps.getString(ps.containsKey("Script") ? "Script" : "name");
        } else {
            boolean found = false;
            for (LinkedHashMap<String, String> m : CoreTools.fileElements(p, scene))
                if (element.equals(m.get("name"))) {
                    found = true;
                    script = m.containsKey("Script") ? m.get("Script") : m.get("name");
                }
            if (!found) throw new Gl.ToolError("No element named '" + element + "' in scene '" + scene + "'");
        }
        if (script == null || script.isEmpty()) script = element;
        String sc = scene, body = script;
        if (script.contains("/")) {
            String[] v = script.split("/");
            sc = v[0];
            body = v[1];
        }
        return new String[]{sc, body};
    }

    /**
     * Events a behavior raises (onDeath, onShoot...): the event name is 'behaviors/<Behavior>/<event>' (or '<Behavior>/<event>').
     * Returns [body, event] for Project.getEventPath, or null when {@code raw} is not such a name.
     */
    static String[] behaviorEvent(Project p, String element, String raw) {
        if (raw == null || raw.indexOf('/') < 0) return null;
        String[] parts = raw.split("/");
        int i = parts.length > 0 && parts[0].equalsIgnoreCase("behaviors") ? 1 : 0;
        if (parts.length - i != 2) return null;
        com.star4droid.star2d.behaviors.BehaviorMeta m = com.star4droid.star2d.behaviors.BehaviorCatalog.find(parts[i], p.getPath());
        if (m == null) throw new Gl.ToolError("No behavior '" + parts[i] + "'. behaviors {action:'list'} shows the ids.");
        for (com.star4droid.star2d.behaviors.BehaviorMeta.EventSpec e : m.events)
            if (e.name.equalsIgnoreCase(parts[i + 1]))
                return new String[]{com.star4droid.star2d.behaviors.BehaviorCodeGen.eventBody(element, m.id), e.name};
        StringBuilder ev = new StringBuilder();
        for (com.star4droid.star2d.behaviors.BehaviorMeta.EventSpec e : m.events) ev.append(ev.length() > 0 ? ", " : "").append(e.signature());
        throw new Gl.ToolError(m.id + " has no event '" + parts[i + 1] + "'. Its events: " + (ev.length() == 0 ? "none" : ev) + ". In the code, `current` is the element and the arguments are typed locals.");
    }

    static boolean hasCode(String path) {
        FileHandle j = Gdx.files.absolute(path + ".java"), v = Gdx.files.absolute(path + ".visual");
        return (j.exists() && j.length() > 0) || (v.exists() && v.length() > 2);
    }

    /** Which events of a scene (and its elements) have code. */
    static JsonObject eventsOf(Project p, String scene) {
        JsonObject o = new JsonObject();
        JsonArray sc = new JsonArray();
        for (String e : SCENE_EVENTS) if (hasCode(p.getEventPath(scene, "", e))) sc.add(e);
        o.add("scene", sc);
        JsonObject els = new JsonObject();
        FileHandle priv = Gdx.files.absolute(p.getEvents(scene) + "/private");
        if (priv.exists())
            for (FileHandle d : priv.list()) {
                if (!d.isDirectory()) continue;
                JsonArray ev = new JsonArray();
                for (String e : ELEMENT_EVENTS) if (hasCode(p.getEventPath(scene, d.name(), e))) ev.add(e);
                if (ev.size() > 0) els.add(d.name(), ev);
            }
        o.add("elements_by_script", els);
        return o;
    }

    // ================================================================== events tool

    static ToolResult events(JsonObject a) throws Exception {
        final String defScene = CoreTools.s(a, "scene");
        final JsonArray ops = EditTools.opsOf(a);
        final JsonArray results = new JsonArray();
        Gl.run(() -> {
            ensureCatalog();
            LibgdxEditor ed = Proj.editor();
            Project p = ed.getProject();
            for (JsonElement e : ops) {
                String op = "?", label = "";
                try {
                    JsonObject o = e.getAsJsonObject();
                    op = CoreTools.s(o, "op");
                    String scene = CoreTools.s(o, "scene");
                    if (scene.isEmpty()) scene = defScene;
                    if (scene.isEmpty()) scene = ed.getScene();
                    if (!Proj.scenes(p).contains(scene)) throw new Gl.ToolError("no scene '" + scene + "'");
                    String scope = CoreTools.s(o, "scope");
                    String element = CoreTools.s(o, "element");
                    boolean isEl = scope.equals("element") || (scope.isEmpty() && !element.isEmpty());
                    if (op.equals("list")) {
                        JsonObject r = new JsonObject();
                        r.addProperty("op", "list");
                        r.addProperty("ok", true);
                        r.add("events", eventsOf(p, scene));
                        results.add(r);
                        continue;
                    }
                    String[] bev = isEl ? behaviorEvent(p, element, CoreTools.s(o, "event")) : null;
                    String event = bev != null ? bev[1] : eventName(isEl, CoreTools.s(o, "event"));
                    String sc = scene, body = bev != null ? bev[0] : "";
                    if (isEl && bev == null) {
                        if (element.isEmpty()) throw new Gl.ToolError("element is required when scope is 'element'");
                        String[] sb = bodyOf(p, ed, scene, element);
                        sc = sb[0];
                        body = sb[1];
                    }
                    label = (isEl ? element + "." : "") + event;
                    String path = p.getEventPath(sc, body, event);
                    if (bev != null) new File(path).getParentFile().mkdirs();
                    switch (op) {
                        case "get": {
                            JsonObject r = new JsonObject();
                            r.addProperty("op", "get");
                            r.addProperty("name", label);
                            r.addProperty("ok", true);
                            File vf = new File(path + ".visual");
                            if (vf.exists() && vf.length() > 2) {
                                BlockModel m = BlockModel.load(vf);
                                r.add("blocks", BlockBuilder.describe(m));
                            }
                            FileHandle jf = Gdx.files.absolute(path + ".java");
                            if (jf.exists()) r.addProperty("java", jf.readString());
                            results.add(r);
                            break;
                        }
                        case "delete": {
                            Gdx.files.absolute(path + ".java").delete();
                            Gdx.files.absolute(path + ".visual").delete();
                            Gdx.files.absolute(path).delete();
                            results.add(ok(op, label, "deleted"));
                            break;
                        }
                        case "edit": {
                            // patch a raw-Java event instead of rewriting it
                            JsonArray edits = o.has("edits") && o.get("edits").isJsonArray() ? o.getAsJsonArray("edits") : FileTools.singleEdit(o);
                            File vf = new File(path + ".visual");
                            if (vf.exists() && vf.length() > 2) {
                                BlockModel m = BlockModel.load(vf);
                                BlockModel.Node src = m.heads.size() == 1 ? m.heads.get(0).next : null;
                                if (src == null || !"addSource".equals(src.title) || src.next != null || src.fields.isEmpty())
                                    throw new Gl.ToolError("'" + label + "' is made of blocks, not raw Java: change it with set (blocks), or events get to see it.");
                                src.fields.get(0).value = FileTools.applyEdits(src.fields.get(0).value, edits);
                                m.save(vf);
                                results.add(ok(op, label, "java edited"));
                            } else {
                                FileHandle jf = Gdx.files.absolute(path + ".java");
                                if (!jf.exists()) throw new Gl.ToolError("'" + label + "' has no code yet: use set");
                                jf.writeString(FileTools.applyEdits(jf.readString(), edits), false);
                                results.add(ok(op, label, "java edited"));
                            }
                            break;
                        }
                        case "set": {
                            BlockModel m;
                            String how;
                            if (o.has("blocks") && o.get("blocks").isJsonArray()) {
                                m = BlockBuilder.build(o.getAsJsonArray("blocks"));
                                how = m.heads.isEmpty() ? "empty" : "blocks";
                            } else if (o.has("java")) {
                                m = BlockBuilder.raw(CoreTools.s(o, "java"));
                                how = "java";
                            } else throw new Gl.ToolError("give 'blocks' (preferred) or 'java'");
                            m.save(new File(path + ".visual"));
                            if (isEl && o.has("also_on") && o.get("also_on").isJsonArray()) {
                                // same logic on other elements (typically the TEXT label lying on a button, which would swallow the touch)
                                for (JsonElement t : o.getAsJsonArray("also_on")) {
                                    String[] tb = bodyOf(p, ed, scene, t.getAsString());
                                    File tf = new File(p.getEventPath(tb[0], tb[1], event) + ".visual");
                                    if (tf.getParentFile() != null) tf.getParentFile().mkdirs();
                                    m.save(tf);
                                }
                            }
                            // the plain event file marks "this event exists" for older code paths
                            String speedWarn = "";
                            if (o.has("blocks")) {
                                java.util.List<String> sw = Warnings.blocks(o.get("blocks"));
                                if (!sw.isEmpty()) speedWarn = "; WARNING " + String.join(" | ", sw);
                            }
                            results.add(ok(op, label, how + " saved" + (m.formatFailures.isEmpty() ? "" : "; WARNING a block template failed to format") + speedWarn));
                            break;
                        }
                        default:
                            throw new Gl.ToolError("unknown op '" + op + "' (set|edit|get|delete|list)");
                    }
                } catch (Gl.ToolError t) {
                    results.add(fail(op, label, t.getMessage()));
                } catch (Throwable t) {
                    results.add(fail(op, label, String.valueOf(t)));
                }
            }
            return null;
        });
        return ToolResult.text(results.toString());
    }

    private static JsonObject ok(String op, String name, String info) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        o.addProperty("name", name);
        o.addProperty("ok", true);
        o.addProperty("info", info);
        return o;
    }

    private static JsonObject fail(String op, String name, String err) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        if (!name.isEmpty()) o.addProperty("name", name);
        o.addProperty("ok", false);
        o.addProperty("error", err);
        return o;
    }

    // ================================================================== block_catalog

    static ToolResult blockCatalog(JsonObject a) throws Exception {
        final String cat = CoreTools.s(a, "category").toLowerCase(Locale.ROOT);
        final String search = CoreTools.s(a, "search").toLowerCase(Locale.ROOT);
        final boolean detail = a.has("detail") && a.get("detail").isJsonPrimitive() && a.get("detail").getAsBoolean();
        final boolean showAll = a.has("all") && a.get("all").isJsonPrimitive() && a.get("all").getAsBoolean();
        final String sceneArg = CoreTools.s(a, "scene");
        final JsonObject out = new JsonObject();
        Gl.run(() -> {
            ensureCatalog();
            // behavior blocks (@Block methods) are listed only for behaviors the scene uses (all:true lists every behavior)
            java.util.Set<String> active = new java.util.HashSet<>();
            java.util.Set<String> hidden = new java.util.TreeSet<>();
            String projectPath = Proj.project().getPath();
            try {
                active = com.star4droid.star2d.blocks.SceneBehaviors.inProject(projectPath, Proj.sceneOrDefault(sceneArg));
            } catch (Throwable ignored) {
            }
            // 2D / 3D scene: only the blocks made for this kind of scene (all:true lists both)
            int mode = 0;
            try {
                mode = com.star4droid.star2d.blocks.SceneDims.modeOf(projectPath, Proj.sceneOrDefault(sceneArg));
            } catch (Throwable ignored) {
            }
            final int sceneMode = showAll ? 0 : mode;
            out.addProperty("scene_type", mode == com.star4droid.star2d.blocks.SceneDims.D3 ? "3D" : "2D");
            JsonArray statements = new JsonArray();
            JsonArray cats = new JsonArray();
            for (Map.Entry<String, ArrayList<BlockCatalog.Def>> e : BlockCatalog.CATEGORIES.entrySet()) {
                boolean any = false;
                for (BlockCatalog.Def d : e.getValue()) {
                    if (!com.star4droid.star2d.blocks.SceneDims.visible(d.dims, sceneMode)) continue;
                    if (d.showWhen == null || showAll || visibleFor(d.showWhen, active)) any = true;
                    else for (String id : d.showWhen) hidden.add(id);
                }
                if (any) cats.add(e.getKey());
                if (!cat.isEmpty() && !e.getKey().toLowerCase(Locale.ROOT).contains(cat)) continue;
                for (BlockCatalog.Def d : e.getValue()) {
                    if (!com.star4droid.star2d.blocks.SceneDims.visible(d.dims, sceneMode)) continue;
                    if (d.showWhen != null && !showAll && !visibleFor(d.showWhen, active)) continue;
                    if (!search.isEmpty() && !d.title.toLowerCase(Locale.ROOT).contains(search)
                            && !BlockInfo.label(d.title, false).toLowerCase(Locale.ROOT).contains(search)) continue;
                    JsonObject b = new JsonObject();
                    b.addProperty("block", d.title);
                    b.addProperty("category", e.getKey());
                    JsonArray fs = new JsonArray();
                    for (int i = 0; i < d.fields.length; i++) {
                        String f = d.fields[i];
                        String def = d.defaults != null && i < d.defaults.length ? d.defaults[i] : "";
                        fs.add(def.isEmpty() ? f : f + " = " + def);
                        String[] ch = BlockCatalog.choices(new BlockModel.Field(f, "").type());
                        if (ch != null) {
                            JsonArray ca = new JsonArray();
                            for (String x : ch) ca.add(x);
                            if (!b.has("choices")) b.add("choices", new JsonObject());
                            b.getAsJsonObject("choices").add(BlockModel.Field.labelOf(f), ca);
                        }
                    }
                    b.add("fields", fs);
                    if (d.dims != com.star4droid.star2d.blocks.SceneDims.BOTH) b.addProperty("dims", com.star4droid.star2d.blocks.SceneDims.name(d.dims));
                    if (d.isIf) b.addProperty("container", true);
                    if (d.showWhen != null) b.addProperty("label", BlockInfo.label(d.title, false));
                    if (detail) {
                        String info = englishInfo(d.title);
                        if (info != null) b.addProperty("desc", info);
                    }
                    b.addProperty("java", oneLine(d.code));
                    statements.add(b);
                }
            }
            JsonArray reporters = new JsonArray();
            for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) {
                if (!com.star4droid.star2d.blocks.SceneDims.visible(r.dims, sceneMode)) continue;
                if (r.showWhen != null && !showAll && !visibleFor(r.showWhen, active)) {
                    for (String id : r.showWhen) hidden.add(id);
                    continue;
                }
                if (!cat.isEmpty() && !r.group.toLowerCase(Locale.ROOT).contains(cat)) continue;
                if (!search.isEmpty() && !r.display.toLowerCase(Locale.ROOT).contains(search)) continue;
                JsonObject b = new JsonObject();
                b.addProperty("r", r.display);
                b.addProperty("group", r.group);
                if (detail) {
                    String info = englishInfo(r.display);
                    if (info != null) b.addProperty("desc", info);
                }
                b.addProperty("java", oneLine(r.template));
                if (r.slots != null && !r.slots.isEmpty()) b.addProperty("slots", r.slots);
                reporters.add(b);
            }
            out.add("categories", cats);
            out.add("blocks", statements);
            out.add("reporters", reporters);
            if (!hidden.isEmpty()) {
                JsonArray h = new JsonArray();
                for (String id : hidden) h.add(id);
                out.add("hidden_behavior_blocks", h);
                out.addProperty("hidden_hint", "These behaviors have blocks that appear once an element of the scene uses the behavior (or add them with the addBehavior block); all:true lists them anyway.");
            }
            out.addProperty("hint", "Use 'block' titles in events.blocks; value blocks go inside fields as {\"r\":\"<r text>\",\"args\":[...]}. Fields typed (Body) take an element name or 'current'. "
                    + "Only the blocks of this scene's type (scene_type 2D or 3D) are listed; 'choices' lists the values of a field; a code(glsl) field holds GLSL as the inside of a Java string (write \\n for new lines). slots: e element, n number, t text, b condition.");
            return null;
        });
        return ToolResult.text(out.toString());
    }

    private static boolean visibleFor(String[] showWhen, java.util.Set<String> active) {
        for (String id : showWhen) if (active.contains(id)) return true;
        return false;
    }

    /** Description of a block for the AI: English (or the first language written), null when there is none. */
    private static String englishInfo(String key) {
        java.util.Map<String, String> m = BlockInfo.descriptions(key);
        String v = m.get("en");
        if (v == null && !m.isEmpty()) v = m.values().iterator().next();
        if (v == null) v = BlockInfo.info(key, false);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        String t = s.replace("\n", "⏎").replaceAll("\\s+", " ");
        return t.length() > 220 ? t.substring(0, 220) + "…" : t;
    }

    // ================================================================== define_blocks

    private static int color(String hex, int def) {
        try {
            String h = hex.replace("#", "").trim();
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (Exception e) {
            return def;
        }
    }

    static ToolResult defineBlocks(JsonObject a) throws Exception {
        final String category = CoreTools.s(a, "category").trim();
        if (category.isEmpty()) return ToolResult.error("category is required");
        if (!a.has("blocks") || !a.get("blocks").isJsonArray()) return ToolResult.error("blocks is required");
        final int catColor = color(CoreTools.s(a, "color"), 0xFF5D4037);
        // "dims": "2D" | "3D" | "BOTH" (default) for the whole category, each block may override it
        final String catDims = com.star4droid.star2d.blocks.SceneDims.name(com.star4droid.star2d.blocks.SceneDims.parse(CoreTools.s(a, "dims")));
        final JsonArray defs = a.getAsJsonArray("blocks");
        final StringBuilder report = new StringBuilder();
        Gl.run(() -> {
            Context c = ctx();
            File base = c.getExternalFilesDir(null);
            File nodesFile = new File(base, "Nodes/nodes.json"), blocksFile = new File(base, "Nodes/blocks.json");
            JsonArray nodeSections = readArray(nodesFile), blockSections = readArray(blocksFile);
            JsonObject nodeSec = section(nodeSections, category, "nodes"), blockSec = section(blockSections, category, "blocks");
            int added = 0;
            for (JsonElement e : defs) {
                JsonObject d = e.getAsJsonObject();
                String title = CoreTools.s(d, "title"), kind = CoreTools.s(d, "kind"), code = CoreTools.s(d, "code");
                if (title.isEmpty() || code.isEmpty()) throw new Gl.ToolError("each block needs title and code");
                if (kind.isEmpty()) kind = "statement";
                ArrayList<String> fields = new ArrayList<>(), dflt = new ArrayList<>();
                if (d.has("fields")) for (JsonElement f : d.getAsJsonArray("fields")) fields.add(f.getAsString());
                if (d.has("defaults")) for (JsonElement f : d.getAsJsonArray("defaults")) dflt.add(f.getAsString());
                int n = fields.size();
                // sanity: the template must be formattable with n args (+ inner/next)
                if (kind.equals("value")) {
                    JsonObject b = new JsonObject();
                    String display = CoreTools.s(d, "display");
                    if (display.isEmpty()) {
                        StringBuilder sb = new StringBuilder(title);
                        for (int i = 0; i < n; i++) sb.append(" ___");
                        display = sb.toString();
                    }
                    int slots = count(display, "___");
                    if (slots != n) throw new Gl.ToolError("block '" + title + "': display has " + slots + " ___ slots but " + n + " fields");
                    b.addProperty("title", display);
                    b.addProperty("code", code);
                    b.addProperty("color", catColor);
                    b.addProperty("dims", dimsOf(d, catDims));
                    addDescription(b, d);
                    replaceBy(blockSec.getAsJsonArray("blocks"), "title", display, b);
                } else {
                    int extra = kind.equals("container") ? 3 : 1; // inner, else, next  |  next
                    int maxArg = maxPlaceholder(code);
                    if (maxArg > n + extra)
                        throw new Gl.ToolError("block '" + title + "': template uses %" + maxArg + "$s but there are only " + n + " fields" + (kind.equals("container") ? " + inner(%" + (n + 1) + ") + next(%" + (n + 2) + ")" : " + next(%" + (n + 1) + ")"));
                    String finalCode = code;
                    if (kind.equals("container")) {
                        // the engine passes (fields..., inner, else, next); authors write (fields..., inner, next)
                        finalCode = remap(code, n + 2, n + 3);
                    }
                    JsonObject b = new JsonObject();
                    b.addProperty("title", title);
                    b.addProperty("code", finalCode);
                    b.addProperty("isBooleanNode", kind.equals("container"));
                    b.addProperty("color", catColor);
                    JsonArray fa = new JsonArray();
                    for (int i = 0; i < n; i++) {
                        JsonObject f = new JsonObject();
                        f.addProperty("name", fields.get(i));
                        f.addProperty("value", i < dflt.size() ? dflt.get(i) : "");
                        fa.add(f);
                    }
                    b.add("fields", fa);
                    b.addProperty("dims", dimsOf(d, catDims));
                    addDescription(b, d);
                    replaceBy(nodeSec.getAsJsonArray("nodes"), "title", title, b);
                }
                added++;
                report.append(title).append(", ");
            }
            // drop empty sections we created only for symmetry
            nodeSections = prune(nodeSections);
            blockSections = prune(blockSections);
            BlockModel.write(nodesFile, nodeSections.toString());
            BlockModel.write(blocksFile, blockSections.toString());
            BlockCatalog.reload(c);
            return null;
        });
        return ToolResult.text("Defined in category '★ " + category + "': " + report + "available now in block_catalog and in the editor's block palette.");
    }

    private static String dimsOf(JsonObject def, String fallback) {
        String v = CoreTools.s(def, "dims");
        return v.trim().isEmpty() ? fallback : com.star4droid.star2d.blocks.SceneDims.name(com.star4droid.star2d.blocks.SceneDims.parse(v));
    }

    /** "description": "text" (English) or {"en":"..","ar":".."} -> "desc" {lang: text} on the stored block (shown by its (i) button). */
    private static void addDescription(JsonObject stored, JsonObject def) {
        if (!def.has("description")) return;
        java.util.Map<String, String> m = com.star4droid.star2d.blocks.BlockDesc.parse(def.get("description"));
        if (!m.isEmpty()) stored.add("desc", com.star4droid.star2d.blocks.BlockDesc.toJson(m));
    }

    private static JsonArray readArray(File f) {
        try {
            if (f.exists()) return JsonParser.parseString(BlockModel.read(f)).getAsJsonArray();
        } catch (Exception ignored) {
        }
        return new JsonArray();
    }

    private static JsonObject section(JsonArray all, String name, String listKey) {
        for (JsonElement e : all) {
            JsonObject o = e.getAsJsonObject();
            if (name.equals(CoreTools.s(o, "name"))) {
                if (!o.has(listKey)) o.add(listKey, new JsonArray());
                return o;
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.add(listKey, new JsonArray());
        all.add(o);
        return o;
    }

    private static JsonArray prune(JsonArray all) {
        JsonArray out = new JsonArray();
        for (JsonElement e : all) {
            JsonObject o = e.getAsJsonObject();
            JsonArray l = o.has("nodes") ? o.getAsJsonArray("nodes") : o.has("blocks") ? o.getAsJsonArray("blocks") : null;
            if (l != null && l.size() == 0) continue;
            out.add(o);
        }
        return out;
    }

    private static void replaceBy(JsonArray list, String key, String value, JsonObject neu) {
        for (int i = 0; i < list.size(); i++)
            if (value.equals(CoreTools.s(list.get(i).getAsJsonObject(), key))) {
                list.set(i, neu);
                return;
            }
        list.add(neu);
    }

    private static int count(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            c++;
            i += sub.length();
        }
        return c;
    }

    private static final Pattern PLACE = Pattern.compile("%(\\d+)\\$s");

    private static int maxPlaceholder(String code) {
        int m = 0;
        Matcher x = PLACE.matcher(code);
        while (x.find()) m = Math.max(m, Integer.parseInt(x.group(1)));
        return m;
    }

    private static String remap(String code, int from, int to) {
        Matcher x = PLACE.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (x.find()) {
            int n = Integer.parseInt(x.group(1));
            x.appendReplacement(sb, Matcher.quoteReplacement("%" + (n == from ? to : n) + "$s"));
        }
        x.appendTail(sb);
        return sb.toString();
    }
}
