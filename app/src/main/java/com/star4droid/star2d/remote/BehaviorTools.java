package com.star4droid.star2d.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorData;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.editor.LibgdxEditor;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The `behaviors` tool: ready-made, configurable behaviours of elements (scale touch effect, floating, platformer player, joystick movement,
 * shooter...) and the public variables of scripts. Storage (same as the Properties panel): element property `behaviors` = JSON list of
 * {type, params, enabled}; element property `scriptVars` = JSON map name -> value; scene script values in Events/&lt;scene&gt;/public/scriptvars.json.
 * Actions: list | info | add | set | remove | reset | vars.
 */
final class BehaviorTools {
    private BehaviorTools() {}

    static ToolResult behaviors(JsonObject a) throws Exception {
        String action = CoreTools.s(a, "action");
        if (action.isEmpty()) action = CoreTools.s(a, "element").isEmpty() ? "list" : "list";
        switch (action) {
            case "list": return list(a);
            case "info": return info(a);
            case "add": return add(a);
            case "set": return set(a);
            case "remove": return remove(a);
            case "reset": return reset(a);
            case "vars": return vars(a);
            default: return ToolResult.error("Unknown action '" + action + "'. Use list | info | add | set | remove | reset | vars. See read_docs {topic:'behaviors'}.");
        }
    }

    // ------------------------------------------------------------------ element access

    private static final class Ctx {
        String projectPath, scene, name, type, script;
        /** the "type" property (DYNAMIC / KINEMATIC / STATIC / UI); type above is the TYPE (BOX, TEXT, OBJ3D...) */
        String bodyType = "";
        boolean ui;
        String behaviors, scriptVars;
        Project project;
    }

    private static Ctx ctx(JsonObject a, boolean needElement) throws Exception {
        final String sceneArg = CoreTools.s(a, "scene");
        if (!sceneArg.isEmpty()) Proj.openScene(sceneArg);
        else Proj.waitLoaded(20_000);
        final String el = CoreTools.s(a, "element");
        return Gl.run(() -> {
            LibgdxEditor ed = Proj.editor();
            Project p = ed.getProject();
            Ctx c = new Ctx();
            c.project = p;
            c.projectPath = p.getPath();
            c.scene = ed.getScene();
            if (el.isEmpty()) {
                if (needElement) throw new Gl.ToolError("element is required");
                return c;
            }
            LinkedHashMap<String, String> found = null;
            for (LinkedHashMap<String, String> m : CoreTools.liveElements(ed)) if (el.equals(m.get("name"))) found = m;
            if (found == null) throw new Gl.ToolError("No element named '" + el + "' in scene '" + c.scene + "'. Use scene_dump {brief:true} to see the names.");
            c.name = el;
            c.type = found.get("TYPE") == null ? "" : found.get("TYPE");
            c.ui = "UI".equals(found.get("type"));
            c.bodyType = found.get("type") == null ? "" : found.get("type");
            c.script = found.get("Script") == null || found.get("Script").isEmpty() ? el : found.get("Script");
            c.behaviors = found.get("behaviors") == null ? "" : found.get("behaviors");
            c.scriptVars = found.get("scriptVars") == null ? "" : found.get("scriptVars");
            return c;
        });
    }

    private static String bodyScriptFile(Ctx c) {
        String script = c.script, scene = c.scene;
        if (script.contains("/")) {
            String[] sp = script.split("/");
            scene = sp[0];
            script = sp[1];
        }
        return c.project.getBodyScriptPath(script, scene);
    }

    /** Writes the two JSON properties through the same path as `elements set` (undo, save, panel refresh). */
    private static String write(Ctx c, String behaviorsJson, String scriptVarsJson) throws Exception {
        JsonObject props = new JsonObject();
        if (behaviorsJson != null) props.addProperty("behaviors", behaviorsJson);
        if (scriptVarsJson != null) props.addProperty("scriptVars", scriptVarsJson);
        JsonObject op = new JsonObject();
        op.addProperty("op", "set");
        op.addProperty("name", c.name);
        op.add("props", props);
        JsonArray ops = new JsonArray();
        ops.add(op);
        JsonObject args = new JsonObject();
        args.add("ops", ops);
        ToolResult r = EditTools.elements(args);
        String txt = r.content.size() > 0 && r.content.get(0).getAsJsonObject().has("text") ? r.content.get(0).getAsJsonObject().get("text").getAsString() : "";
        if (r.isError || txt.contains("\"failed\":") && !txt.contains("\"failed\":0")) return txt.isEmpty() ? "could not write the element" : txt;
        return null;
    }

    // ------------------------------------------------------------------ describing

    private static String typeName(VarSpec v) {
        String t;
        switch (v.kind) {
            case REF: t = "element:" + (v.elemType.isEmpty() ? "PlayerItem" : v.elemType); break;
            case ENUM: t = "enum(" + String.join("|", v.options) + ")"; break;
            case COLOR: t = "color #RRGGBB"; break;
            case IMAGE: t = "image path"; break;
            case SOUND: t = "sound path"; break;
            default: t = v.kind.name().toLowerCase(Locale.ROOT);
        }
        if (v.list || v.array) t = "list of " + t;
        return t;
    }

    private static JsonObject varJson(VarSpec v, boolean full) {
        JsonObject o = new JsonObject();
        o.addProperty("name", v.name);
        o.addProperty("type", typeName(v));
        if (v.def != null) o.addProperty("default", String.valueOf(v.def));
        if (v.isStatic) o.addProperty("static", true);
        if (v.access != null && !v.access.equals("public")) o.addProperty("access", v.access);
        if (v.auto) o.addProperty("auto", true);
        if (full) {
            if (!v.desc.isEmpty()) o.addProperty("desc", v.desc);
            if (!v.unit.isEmpty()) o.addProperty("unit", v.unit);
            if (v.isNumber() && (v.min > -1e8 || v.max < 1e8)) o.addProperty("range", (v.min > -1e8 ? fmt(v.min) : "") + ".." + (v.max < 1e8 ? fmt(v.max) : ""));
        }
        return o;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static JsonObject metaJson(BehaviorMeta m, boolean full) {
        JsonObject o = new JsonObject();
        o.addProperty("id", m.id);
        o.addProperty("what", m.desc);
        o.addProperty("for", m.is3D() ? "3d" : m.targets.equals(BehaviorMeta.UI) ? "ui" : m.targets.equals(BehaviorMeta.PHYSICS) ? "physics" : "both");
        if (!m.types.isEmpty()) o.addProperty("element_types", m.joinTypes());
        o.addProperty("category", m.category);
        if (!m.builtin) o.addProperty("project", true);
        if (!m.exclusive.isEmpty()) o.addProperty("exclusive", m.exclusive);
        String req = m.requirementsText();
        if (req != null && !req.isEmpty()) o.addProperty("needs", req);
        if (!m.sets.isEmpty()) {
            JsonObject s = new JsonObject();
            for (Map.Entry<String, String> e : m.sets.entrySet()) s.addProperty(BehaviorMeta.propertyOf(e.getKey()) == null ? e.getKey() : BehaviorMeta.propertyOf(e.getKey()), e.getValue());
            o.add("sets", s);
        }
        if (!m.events.isEmpty()) {
            JsonArray ev = new JsonArray();
            for (BehaviorMeta.EventSpec e : m.events) ev.add(full ? e.signature() : e.name);
            o.add("events", ev);
        }
        JsonArray vars = new JsonArray();
        for (VarSpec v : m.vars) {
            if (full) vars.add(varJson(v, true));
            else vars.add(v.name + ":" + typeName(v) + (v.def != null ? "=" + v.def : ""));
        }
        o.add("params", vars);
        return o;
    }

    // ------------------------------------------------------------------ list / info

    private static ToolResult list(JsonObject a) throws Exception {
        Ctx c = ctx(a, false);
        JsonObject o = new JsonObject();
        if (c.name != null) {
            // what the element has now
            JsonArray arr = new JsonArray();
            List<BehaviorData.Entry> entries = BehaviorData.parse(c.behaviors);
            for (BehaviorData.Entry e : entries) {
                JsonObject x = new JsonObject();
                x.addProperty("id", e.type);
                x.addProperty("enabled", e.enabled);
                BehaviorMeta m = BehaviorCatalog.find(e.type, c.projectPath);
                if (m == null) x.addProperty("warning", "behavior not found (deleted project behavior?)");
                else {
                    String mis = m.misfit(c.bodyType, c.type);
                    if (mis != null) x.addProperty("warning", mis);
                }
                if (!e.params.isEmpty()) x.add("params", BehaviorData.gson().toJsonTree(e.params));
                arr.add(x);
            }
            o.addProperty("element", c.name);
            o.addProperty("type", c.type + (c.ui ? " (UI)" : ""));
            o.add("behaviors", arr);
            SourceScanner.Result r = SourceScanner.scanScript(BehaviorCatalog.readFile(new File(bodyScriptFile(c))));
            if (!r.vars.isEmpty()) {
                JsonArray bv = new JsonArray();
                for (VarSpec v : r.vars) bv.add(varJson(v, false));
                o.add("script_vars", bv);
                if (!c.scriptVars.isEmpty()) o.add("script_var_values", BehaviorData.gson().toJsonTree(com.star4droid.star2d.behaviors.ScriptVarSync.visible(BehaviorData.parseVars(c.scriptVars))));
            }
            return ToolResult.text(o.toString());
        }
        String target = CoreTools.s(a, "target").toLowerCase(Locale.ROOT);
        String q = CoreTools.s(a, "search").toLowerCase(Locale.ROOT);
        JsonArray arr = new JsonArray();
        for (BehaviorMeta m : BehaviorCatalog.all(c.projectPath)) {
            if ((target.equals("3d") || target.equals("three_d")) && !m.is3D()) continue;
            if (!target.isEmpty() && !target.equals("3d") && !target.equals("three_d") && !target.equals("all") && m.is3D()) continue;
            if (target.equals("ui") && !m.fits(true)) continue;
            if ((target.equals("physics") || target.equals("physical")) && !m.fits(false)) continue;
            if (!q.isEmpty() && !(m.id + " " + m.desc + " " + m.category).toLowerCase(Locale.ROOT).contains(q)) continue;
            arr.add(metaJson(m, false));
        }
        o.add("behaviors", arr);
        o.addProperty("how", "behaviors {action:'add', element, id, params:{...}} adds one; {action:'info', id} = every param with range/default and events; "
                + "{action:'vars', element, values:{...}} sets the public variables of the element's Body Script. Behaviors of the same `exclusive` group conflict (add refuses unless replace:true or force:true).");
        return ToolResult.text(o.toString());
    }

    private static ToolResult info(JsonObject a) throws Exception {
        Ctx c = ctx(a, false);
        String id = CoreTools.s(a, "id");
        BehaviorMeta m = BehaviorCatalog.find(id, c.projectPath);
        if (m == null) return ToolResult.error(notFound(id, c));
        return ToolResult.text(metaJson(m, true).toString());
    }

    private static String notFound(String id, Ctx c) {
        StringBuilder sb = new StringBuilder("No behavior '" + id + "'. Available: ");
        boolean first = true;
        for (BehaviorMeta m : BehaviorCatalog.all(c.projectPath)) {
            sb.append(first ? "" : ", ").append(m.id);
            first = false;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ value coercion

    /** Converts what the AI sent into the value model of the inspector (numbers Double, flags Boolean, text String, lists List). */
    private static Object coerce(VarSpec v, JsonElement e, List<String> warnings) {
        if (e == null || e.isJsonNull()) return null;
        if (v.list || v.array) {
            JsonArray arr = e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
            if (!e.isJsonArray()) arr.add(e);
            ArrayList<Object> out = new ArrayList<Object>();
            for (JsonElement x : arr) {
                Object o = scalar(v, x, warnings);
                if (o != null) out.add(o);
            }
            return out;
        }
        return scalar(v, e, warnings);
    }

    private static Object scalar(VarSpec v, JsonElement e, List<String> warnings) {
        if (!e.isJsonPrimitive()) throw new IllegalArgumentException(v.name + ": expected " + typeName(v));
        JsonPrimitive p = e.getAsJsonPrimitive();
        switch (v.kind) {
            case INT: case LONG: case SHORT: case BYTE: case FLOAT: case DOUBLE: {
                double d;
                try {
                    d = p.isNumber() ? p.getAsDouble() : Double.parseDouble(p.getAsString().trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(v.name + ": '" + p.getAsString() + "' is not a number");
                }
                if (v.isInteger()) d = Math.rint(d);
                double cl = Math.max(v.min, Math.min(v.max, d));
                if (cl != d) warnings.add(v.name + ": " + fmt(d) + " is outside " + fmt(v.min) + ".." + fmt(v.max) + ", used " + fmt(cl));
                return cl;
            }
            case BOOL:
                if (p.isBoolean()) return p.getAsBoolean();
                String b = p.getAsString().trim().toLowerCase(Locale.ROOT);
                if (b.equals("true") || b.equals("false")) return Boolean.valueOf(b);
                throw new IllegalArgumentException(v.name + ": expected true or false");
            case ENUM: {
                String s = p.getAsString().trim();
                for (String o : v.options) if (o.equalsIgnoreCase(s)) return o;
                throw new IllegalArgumentException(v.name + ": '" + s + "' is not one of " + String.join(", ", v.options));
            }
            case COLOR: {
                String s = p.getAsString().trim();
                if (!s.startsWith("#")) s = "#" + s;
                if (!s.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) throw new IllegalArgumentException(v.name + ": colour must be #RRGGBB or #RRGGBBAA");
                return s.toUpperCase(Locale.ROOT);
            }
            default:
                return p.getAsString();
        }
    }

    private static LinkedHashMap<String, Object> coerceParams(BehaviorMeta m, JsonObject params, List<String> warnings, List<String> errors) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        if (params == null) return out;
        for (Map.Entry<String, JsonElement> en : params.entrySet()) {
            VarSpec v = m.var(en.getKey());
            if (v == null) {
                errors.add("unknown param '" + en.getKey() + "' (params: " + names(m.vars) + ")");
                continue;
            }
            try {
                Object val = coerce(v, en.getValue(), warnings);
                if (val != null) out.put(v.name, val);
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }
        return out;
    }

    private static String names(List<VarSpec> vars) {
        StringBuilder sb = new StringBuilder();
        for (VarSpec v : vars) sb.append(sb.length() > 0 ? ", " : "").append(v.name);
        return sb.toString();
    }

    // ------------------------------------------------------------------ add / set / remove / reset

    private static ToolResult add(JsonObject a) throws Exception {
        Ctx c = ctx(a, true);
        String id = CoreTools.s(a, "id");
        BehaviorMeta m = BehaviorCatalog.find(id, c.projectPath);
        if (m == null) return ToolResult.error(notFound(id, c));
        boolean replace = a.has("replace") && a.get("replace").getAsBoolean();
        boolean force = a.has("force") && a.get("force").getAsBoolean();
        List<BehaviorData.Entry> entries = BehaviorData.parse(c.behaviors);
        for (BehaviorData.Entry e : entries)
            if (e.type.equals(m.id)) return ToolResult.error("'" + c.name + "' already has " + m.id + ". Use behaviors {action:'set', element, id, params} to change it.");
        ArrayList<String> warnings = new ArrayList<String>(), errors = new ArrayList<String>();
        String mis = m.misfit(c.bodyType, c.type);
        // 2D behaviors on 3D elements (and 3D ones on 2D elements) never work: refuse unless forced
        String mis3 = m.misfit3D(c.type);
        if (mis3 != null && !force && (m.is3D() != com.star4droid.star2d.behaviors.ElementTypes.is3D(c.type)))
            return ToolResult.error(m.id + " cannot go on '" + c.name + "' (" + c.type + "): " + mis3 + ". Pick a " + (m.is3D() ? "3D element (OBJ3D, LIGHT3D, CAMERA3D)" : "2D element") + ", or behaviors {action:'list', target:'" + (com.star4droid.star2d.behaviors.ElementTypes.is3D(c.type) ? "3d" : "physics") + "'} for the ones that fit.");
        if (mis != null) warnings.add(mis);

        // exclusive group: one movement / one weapon at a time
        ArrayList<BehaviorData.Entry> clash = new ArrayList<BehaviorData.Entry>();
        if (!m.exclusive.isEmpty()) {
            for (BehaviorData.Entry e : entries) {
                BehaviorMeta o = BehaviorCatalog.find(e.type, c.projectPath);
                if (o != null && m.exclusive.equals(o.exclusive) && e.enabled) clash.add(e);
            }
        }
        boolean enabled = !a.has("enabled") || a.get("enabled").getAsBoolean();
        if (!clash.isEmpty()) {
            String names = "";
            for (BehaviorData.Entry e : clash) names += (names.isEmpty() ? "" : ", ") + e.type;
            if (replace) {
                entries.removeAll(clash);
                warnings.add("replaced " + names + " (same exclusive group '" + m.exclusive + "')");
            } else if (force) {
                if (!a.has("enabled")) enabled = false;
                warnings.add("kept " + names + " too (exclusive group '" + m.exclusive + "'): the new " + m.id + " is " + (enabled ? "ENABLED, both run (probably wrong)" : "switched OFF; enable it with behaviors set {enabled:true} after disabling the other"));
            } else {
                return ToolResult.error("'" + c.name + "' already has " + names + " from the exclusive group '" + m.exclusive + "', which conflicts with " + m.id
                        + ". Ask yourself which one the user wants: pass replace:true to swap it, or force:true to keep both (the new one starts OFF).");
            }
        }
        BehaviorData.Entry en = new BehaviorData.Entry(m.id);
        en.enabled = enabled;
        en.params = coerceParams(m, a.has("params") && a.get("params").isJsonObject() ? a.getAsJsonObject("params") : null, warnings, errors);
        if (!errors.isEmpty()) return ToolResult.error(String.join("; ", errors));
        entries.add(en);
        String err = write(c, BehaviorData.toJson(entries), null);
        if (err != null) return ToolResult.error(err);
        return done(c, m, "added", warnings, en);
    }

    private static ToolResult set(JsonObject a) throws Exception {
        Ctx c = ctx(a, true);
        String id = CoreTools.s(a, "id");
        List<BehaviorData.Entry> entries = BehaviorData.parse(c.behaviors);
        BehaviorData.Entry en = BehaviorData.find(entries, id);
        if (en == null) return ToolResult.error("'" + c.name + "' has no behavior '" + id + "'. Add it first.");
        BehaviorMeta m = BehaviorCatalog.find(en.type, c.projectPath);
        if (m == null) return ToolResult.error("Behavior '" + id + "' is missing from the project (deleted?).");
        ArrayList<String> warnings = new ArrayList<String>(), errors = new ArrayList<String>();
        if (a.has("params") && a.get("params").isJsonObject()) en.params.putAll(coerceParams(m, a.getAsJsonObject("params"), warnings, errors));
        if (!errors.isEmpty()) return ToolResult.error(String.join("; ", errors));
        if (a.has("enabled")) {
            en.enabled = a.get("enabled").getAsBoolean();
            if (en.enabled && !m.exclusive.isEmpty()) {
                for (BehaviorData.Entry o : entries) {
                    BehaviorMeta om = BehaviorCatalog.find(o.type, c.projectPath);
                    if (o != en && o.enabled && om != null && m.exclusive.equals(om.exclusive)) {
                        o.enabled = false;
                        warnings.add("disabled " + o.type + " (same exclusive group '" + m.exclusive + "')");
                    }
                }
            }
        }
        String err = write(c, BehaviorData.toJson(entries), null);
        if (err != null) return ToolResult.error(err);
        return done(c, m, "updated", warnings, en);
    }

    private static ToolResult remove(JsonObject a) throws Exception {
        Ctx c = ctx(a, true);
        String id = CoreTools.s(a, "id");
        List<BehaviorData.Entry> entries = BehaviorData.parse(c.behaviors);
        BehaviorData.Entry en = BehaviorData.find(entries, id);
        if (en == null) return ToolResult.error("'" + c.name + "' has no behavior '" + id + "'.");
        entries.remove(en);
        String err = write(c, BehaviorData.toJson(entries), null);
        if (err != null) return ToolResult.error(err);
        JsonObject o = new JsonObject();
        o.addProperty("removed", id);
        o.addProperty("element", c.name);
        return ToolResult.text(o.toString());
    }

    private static ToolResult reset(JsonObject a) throws Exception {
        Ctx c = ctx(a, true);
        String id = CoreTools.s(a, "id");
        List<BehaviorData.Entry> entries = BehaviorData.parse(c.behaviors);
        BehaviorData.Entry en = BehaviorData.find(entries, id);
        if (en == null) return ToolResult.error("'" + c.name + "' has no behavior '" + id + "'.");
        if (a.has("param")) en.params.remove(CoreTools.s(a, "param"));
        else en.params.clear();
        String err = write(c, BehaviorData.toJson(entries), null);
        if (err != null) return ToolResult.error(err);
        JsonObject o = new JsonObject();
        o.addProperty("reset", a.has("param") ? id + "." + CoreTools.s(a, "param") : id);
        return ToolResult.text(o.toString());
    }

    private static ToolResult done(Ctx c, BehaviorMeta m, String what, List<String> warnings, BehaviorData.Entry en) {
        JsonObject o = new JsonObject();
        o.addProperty(what, m.id);
        o.addProperty("element", c.name);
        o.addProperty("enabled", en.enabled);
        if (!en.params.isEmpty()) o.add("params", BehaviorData.gson().toJsonTree(en.params));
        if (!m.sets.isEmpty())
            o.addProperty("note", m.id + " sets " + m.sets + " at run time (it does not change the saved properties).");
        if (!m.events.isEmpty()) {
            JsonArray ev = new JsonArray();
            for (BehaviorMeta.EventSpec e : m.events) ev.add(e.name);
            o.add("events", ev);
            o.addProperty("events_how", "Write them with events {scope:'element', element:'" + c.name + "', event:'behaviors/" + m.id + "/" + m.events.get(0).name + "', blocks|java} (see read_docs behaviors).");
        }
        if (!warnings.isEmpty()) {
            JsonArray w = new JsonArray();
            for (String x : warnings) w.add(x);
            o.add("warnings", w);
        }
        return ToolResult.text(o.toString());
    }

    // ------------------------------------------------------------------ public variables

    private static ToolResult vars(JsonObject a) throws Exception {
        Ctx c = ctx(a, false);
        boolean scene = c.name == null;
        String file = scene ? c.project.getSceneScript(c.scene) : bodyScriptFile(c);
        SourceScanner.Result r = SourceScanner.scanScript(BehaviorCatalog.readFile(new File(file)));
        if (r.vars.isEmpty()) return ToolResult.error((scene ? "The scene script" : "The Body Script of '" + c.name + "'") + " has no variables. Declare `public float speed = 5;` (or a package-private / static field, or `@Expose private int lives;`) in its Java code first.");
        File store = new File(c.projectPath + "/Events/" + c.scene + "/public/scriptvars.json");
        Map<String, Object> cur = BehaviorData.parseVars(scene ? BehaviorCatalog.readFile(store) : c.scriptVars);
        JsonObject values = a.has("values") && a.get("values").isJsonObject() ? a.getAsJsonObject("values") : null;
        JsonObject o = new JsonObject();
        if (values == null) {
            JsonArray arr = new JsonArray();
            for (VarSpec v : r.vars) arr.add(varJson(v, true));
            o.add("vars", arr);
            o.add("values", BehaviorData.gson().toJsonTree(com.star4droid.star2d.behaviors.ScriptVarSync.visible(cur)));
            return ToolResult.text(o.toString());
        }
        ArrayList<String> warnings = new ArrayList<String>(), errors = new ArrayList<String>();
        for (Map.Entry<String, JsonElement> en : values.entrySet()) {
            VarSpec v = null;
            for (VarSpec x : r.vars) if (x.name.equals(en.getKey())) v = x;
            if (v == null) {
                errors.add("no public variable '" + en.getKey() + "' (variables: " + names(r.vars) + ")");
                continue;
            }
            try {
                Object val = coerce(v, en.getValue(), warnings);
                if (val == null) cur.remove(v.name); else cur.put(v.name, val);
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }
        if (!errors.isEmpty()) return ToolResult.error(String.join("; ", errors));
        String json = BehaviorData.varsToJson(cur);
        if (scene) {
            store.getParentFile().mkdirs();
            java.nio.file.Files.write(store.toPath(), json.getBytes("UTF-8"));
        } else {
            String err = write(c, null, json);
            if (err != null) return ToolResult.error(err);
        }
        o.addProperty("set", scene ? "scene script" : c.name);
        o.add("values", BehaviorData.gson().toJsonTree(com.star4droid.star2d.behaviors.ScriptVarSync.visible(cur)));
        if (!warnings.isEmpty()) {
            JsonArray w = new JsonArray();
            for (String x : warnings) w.add(x);
            o.add("warnings", w);
        }
        return ToolResult.text(o.toString());
    }
}
