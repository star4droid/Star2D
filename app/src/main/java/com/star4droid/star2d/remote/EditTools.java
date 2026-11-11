package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.physics.box2d.World;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.JointsHelper;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.editor.ui.ItemActions;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.editor.ui.variables.VarModel;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.ai.AiActions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Scenes, elements, joints, variables and editor-view tools. */
final class EditTools {
    private EditTools() {}

    private static final long BUDGET_MS = 45_000;
    private static final int CHUNK = 25;

    static JsonArray opsOf(JsonObject a) {
        if (a.has("ops") && a.get("ops").isJsonArray()) return a.getAsJsonArray("ops");
        throw new Gl.ToolError("'ops' (array of operations) is required");
    }

    private static JsonObject res(String op, String name, boolean ok, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        if (name != null && !name.isEmpty()) o.addProperty("name", name);
        o.addProperty("ok", ok);
        if (msg != null && !msg.isEmpty()) o.addProperty(ok ? "info" : "error", msg);
        return o;
    }

    private static String str(JsonElement e) {
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isNumber()) {
                double d = e.getAsDouble();
                return d == Math.rint(d) && Math.abs(d) < 1e9 ? String.valueOf((long) d) : String.valueOf((float) d);
            }
            return e.getAsString();
        }
        return e.toString();
    }

    // ================================================================== scenes

    static ToolResult scenes(JsonObject a) throws Exception {
        final String action = CoreTools.s(a, "action");
        final String name = CoreTools.s(a, "name");
        final String newName = CoreTools.s(a, "new_name");
        final JsonObject config = a.has("config") && a.get("config").isJsonObject() ? a.getAsJsonObject("config") : null;
        final JsonObject out = new JsonObject();
        switch (action) {
            case "list":
                Gl.run(() -> {
                    TestApp app = Bridge.app();
                    JsonArray l = new JsonArray();
                    for (String s : Proj.scenes(Proj.project())) l.add(s);
                    out.add("scenes", l);
                    out.addProperty("open", Proj.editor().getScene());
                    out.addProperty("frame", SceneInfo.describe(Proj.editor(), Proj.project(), Proj.editor().getScene()));
                    out.addProperty("project_orientation", Proj.projectOrientation(null));
                    JsonArray tabs = new JsonArray();
                    for (LibgdxEditor e : app.editors) tabs.add(e.getScene());
                    out.add("open_tabs", tabs);
                    return null;
                });
                return ToolResult.text(out.toString());
            case "open":
                if (name.isEmpty()) return ToolResult.error("name is required");
                Proj.openScene(name);
                return ToolResult.text("Scene '" + name + "' is open. " + Gl.run(() -> SceneInfo.describe(Proj.editor(), Proj.project(), Proj.editor().getScene())));
            case "create":
            case "copy":
            case "rename": {
                if (name.isEmpty()) return ToolResult.error("name is required");
                final String target = action.equals("create") ? name : newName;
                if (target.isEmpty()) return ToolResult.error("new_name is required");
                final String source = action.equals("create") ? null : name;
                if (source != null) Proj.openScene(source); // the engine copies/renames the OPEN scene
                // orientation of a NEW scene: explicit param, else config.or / config.orientation, else the PROJECT's (read from the config files)
                String asked = CoreTools.s(a, "orientation");
                if (asked.isEmpty() && config != null) asked = config.has("orientation") ? CoreTools.s(config, "orientation") : CoreTools.s(config, "or");
                asked = asked.trim().toLowerCase(Locale.ROOT);
                if (asked.equals("horizontal") || asked.equals("wide")) asked = "landscape";
                if (asked.equals("vertical") || asked.equals("tall")) asked = "portrait";
                if (!asked.isEmpty() && !asked.equals("landscape") && !asked.equals("portrait"))
                    return ToolResult.error("orientation must be 'landscape' or 'portrait' (got '" + asked + "')");
                final String wantOr = asked;
                // scene kind of a NEW scene: type "3D" (also scene_type / config.sceneType / 3d:true), default 2D
                String kind = CoreTools.s(a, "type");
                if (kind.isEmpty()) kind = CoreTools.s(a, "scene_type");
                if (kind.isEmpty()) kind = CoreTools.s(a, "sceneType");
                if (kind.isEmpty() && config != null) kind = CoreTools.s(config, "sceneType");
                if (kind.isEmpty() && CoreTools.b(a, "3d", false)) kind = "3D";
                kind = kind.trim().toUpperCase(Locale.ROOT);
                if (!kind.isEmpty() && !kind.equals("2D") && !kind.equals("3D"))
                    return ToolResult.error("type must be '2D' or '3D' (got '" + kind + "')");
                if (kind.equals("3D") && !action.equals("create"))
                    return ToolResult.error("type '3D' is only for create (copy keeps the kind of the copied scene)");
                final String sceneKind = kind.isEmpty() ? "2D" : kind;
                if (config != null) config.remove("sceneType");
                final String projectOr = action.equals("create") ? Gl.run(() -> Proj.projectOrientation(target)) : null;
                String err = Gl.run(() -> {
                    ControlLayer cl = Bridge.controls();
                    ControlLayer.SceneAction sa = action.equals("create") ? ControlLayer.SceneAction.CREATE
                            : action.equals("copy") ? ControlLayer.SceneAction.COPY : ControlLayer.SceneAction.RENAME;
                    Proj.editor().getProject().save(Proj.editor());
                    return cl.applySceneAction(target, sa, action.equals("create") && !wantOr.isEmpty() ? wantOr : null, sceneKind);
                });
                if (err != null) return ToolResult.error(err);
                Proj.waitLoaded(20_000);
                if (sceneKind.equals("3D")) Gl.sleep(400); // the starter ground, cube and camera are added right after loading
                if (config != null && config.size() > 0) configure(config);
                String extra = "";
                if (action.equals("create")) {
                    // verify: the config FILE and the live flag must say what was asked (or what the project is)
                    final String expect = wantOr.isEmpty() ? projectOr : wantOr;
                    extra = Gl.run(() -> {
                        LibgdxEditor ed = Proj.editor();
                        String fixed = "";
                        String stored = com.star4droid.star2d.editor.ProjectOrientation.stored(ed.getProject(), target);
                        ed.getConfig();
                        boolean wantLand = !expect.equals("portrait");
                        if (stored == null || !stored.equals(expect) || ed.isLandscape() != wantLand) {
                            ed.setLandscape(wantLand);
                            ed.saveConfig();
                            fixed = " (orientation corrected to " + expect.toUpperCase(Locale.ROOT) + ")";
                        }
                        String note = "";
                        if (!wantOr.isEmpty() && !wantOr.equals(projectOr))
                            note = " NOTE: the other scenes are " + projectOr.toUpperCase(Locale.ROOT) + "; an exported game has ONE screen orientation, keep all scenes the same unless you really mean it.";
                        if (sceneKind.equals("3D"))
                            note += " 3D scene: it starts with a ground PLANE, a CUBE and an active CAMERA3D (rename/replace them, see scene_dump). 3D elements: OBJ3D, LIGHT3D, CAMERA3D in METERS (y up); settings via configure (Render Mode, Lighting Preset, Sky, Fog, Gravity Y, Quality, Post Effect...). read_docs {topic:'scenes', section:'3D scenes'}.";
                        return fixed + note;
                    });
                }
                return ToolResult.text("Scene '" + target + "' " + (action.equals("create") ? "created" : action.equals("copy") ? "copied from " + name : "renamed from " + name) + " and open. "
                        + Gl.run(() -> SceneInfo.describe(Proj.editor(), Proj.project(), Proj.editor().getScene())) + extra);
            }
            case "delete": {
                if (name.isEmpty()) return ToolResult.error("name is required");
                Proj.openScene(name);
                String err = Gl.run(() -> Bridge.controls().actionDeleteCurrentScene());
                if (err != null) return ToolResult.error(err);
                Proj.waitLoaded(20_000);
                return ToolResult.text("Scene '" + name + "' deleted. Open scene is now '" + Gl.run(() -> Proj.editor().getScene()) + "'.");
            }
            case "configure": {
                if (!name.isEmpty()) Proj.openScene(name);
                if (config == null) return ToolResult.error("config is required");
                return ToolResult.text(configure(config) + " " + Gl.run(() -> SceneInfo.describe(Proj.editor(), Proj.project(), Proj.editor().getScene())));
            }
            default:
                return ToolResult.error("Unknown action '" + action + "'. Use list|open|create|rename|copy|delete|configure (create {type:'3D'} makes a 3D scene).");
        }
    }

    private static String configure(JsonObject c) throws Exception {
        return Gl.run(() -> {
            LibgdxEditor ed = Proj.editor();
            StringBuilder sb = new StringBuilder();
            float lw = -1, lh = -1;
            boolean swap = false, changed3D = false;
            boolean is3D = ed.is3DScene();
            for (Map.Entry<String, JsonElement> e : c.entrySet()) {
                String k = e.getKey(), v = str(e.getValue());
                String[] f3 = field3D(k);
                if (f3 != null) {
                    if (!is3D) {
                        sb.append("'").append(k).append("' is a 3D setting and this scene is 2D (ignored); ");
                        continue;
                    }
                    String err = check3D(f3, v);
                    if (err != null) {
                        sb.append(err).append("; ");
                        continue;
                    }
                    if (f3[1].equals("choice")) v = v.trim().toUpperCase(Locale.ROOT);
                    if (f3[1].equals("color") && !v.startsWith("#")) v = "#" + v;
                    ed.getConfig().put(f3[0], v);
                    changed3D = true;
                    continue;
                }
                switch (k) {
                    case "sceneType":
                        if (!v.equalsIgnoreCase(is3D ? "3D" : "2D"))
                            sb.append("sceneType cannot be changed (make a new scene with scenes create {type:'").append(is3D ? "2D" : "3D").append("'}); ");
                        break;
                    case "sceneColor": ed.setSceneColor(v.startsWith("#") ? v : "#" + v); break;
                    case "Ambient Light":
                    case "ambient":
                        ed.getConfig().put("Ambient Light", v.startsWith("#") ? v : "#" + v);
                        ed.saveConfig();
                        try { ed.setupLight(); } catch (Throwable ignored) {}
                        break;
                    case "or":
                    case "orientation":
                        ed.setLandscape(!(v.equalsIgnoreCase("portrait")));
                        break;
                    case "logicWidth": lw = Bridge.num(v); break;
                    case "logicHeight": lh = Bridge.num(v); break;
                    case "swap_size":
                    case "swapSize": swap = v.equalsIgnoreCase("true") || v.equals("1"); break;
                    default: sb.append("ignored unknown key '").append(k).append("'; ");
                }
            }
            if (lw > 0 || lh > 0) {
                // stored convention: logicWidth = LONG side, logicHeight = SHORT side (portrait swaps them when used).
                // The missing one comes from the STORED values (the display getters are swapped in portrait).
                float a = lw > 0 ? lw : ed.getRawLogicWidth(), b = lh > 0 ? lh : ed.getRawLogicHeight();
                float longSide = Math.max(a, b), shortSide = Math.min(a, b);
                if (a < b) sb.append("logicWidth is the LONG side: stored as ").append((int) longSide).append(" x ").append((int) shortSide).append("; ");
                ed.setLogicalWH(longSide, shortSide);
                ed.saveConfig();
            }
            if (changed3D) {
                ed.getConfig().put("sceneType", "3D");
                ed.saveConfig();
                try {
                    if (ed.get3D() != null) ed.get3D().configure(ed.getConfig());
                } catch (Throwable ignored) {
                }
                if (!"CUSTOM".equalsIgnoreCase(String.valueOf(ed.getConfig().get("Lighting Preset")))) {
                    for (String k : c.keySet()) {
                        String[] f3 = field3D(k);
                        if (f3 != null && CUSTOM_ONLY.contains(f3[0])) {
                            sb.append("NOTE: Ambient / Sun / Sky Top / Sky Bottom / Fog values only apply with \"Lighting Preset\":\"CUSTOM\" (the other presets set them); ");
                            break;
                        }
                    }
                }
            }
            if (swap) {
                ed.swapLogicSize();
                sb.append("sizes swapped (logicWidth now ").append((int) ed.getRawLogicWidth()).append(", logicHeight ").append((int) ed.getRawLogicHeight()).append("); ");
            }
            Proj.project().save(ed);
            return "Scene '" + ed.getScene() + "' configured. " + sb;
        });
    }

    /** 3D scene settings that only apply with Lighting Preset CUSTOM. */
    private static final java.util.Set<String> CUSTOM_ONLY = new java.util.HashSet<>(java.util.Arrays.asList(
            "Ambient Color", "Ambient Intensity", "Sun", "Sun Color", "Sun Intensity", "Sun Pitch", "Sun Yaw", "Sky Top", "Sky Bottom", "Fog", "Fog Color", "Fog Near", "Fog Far"));
    /** Settings read by the 3D world but not shown in the settings dialog. */
    private static final String[][] EXTRA_3D = {{"Post Params", "text", "", ""}, {"Exposure", "float", "1", ""}};

    /** {key, kind, default, options} of a 3D scene setting (case-insensitive name), null = not one. */
    static String[] field3D(String key) {
        for (String[] f : com.star4droid.star2d.ui.Scene3DSettings.FIELDS) if (f[0].equalsIgnoreCase(key)) return f;
        for (String[] f : EXTRA_3D) if (f[0].equalsIgnoreCase(key)) return f;
        return null;
    }

    private static String check3D(String[] f, String v) {
        switch (f[1]) {
            case "choice":
                for (String o : f[3].split(",")) if (o.equalsIgnoreCase(v.trim())) return null;
                return f[0] + ": '" + v + "' is not one of " + f[3].replace(",", "|") + " (ignored)";
            case "float":
                try {
                    Float.parseFloat(v);
                    return null;
                } catch (Exception e) {
                    return f[0] + ": not a number (ignored)";
                }
            case "bool":
                return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false") ? null : f[0] + ": use true or false (ignored)";
            default:
                return null;
        }
    }

    // ================================================================== elements

    private static final String[] KINDS = {"BOX", "CIRCLE", "TEXT", "JOYSTICK", "PROGRESS", "CUSTOM", "PARTICLE", "CAMERA", "MAP", "GROUP", "UIHOLDER"};

    private static final String[] MESHES = {"CUBE", "SPHERE", "CYLINDER", "CAPSULE", "CONE", "PLANE", "TORUS", "RAMP", "STAIRS", "PYRAMID", "HEMISPHERE",
            "TUBE", "WEDGE", "QUAD", "DISC", "MODEL", "TERRAIN", "WATER", "EMPTY"};

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 3D kinds: {TYPE, preset} where TYPE is OBJ3D / LIGHT3D / CAMERA3D / CAMERAVIEW and preset the Mesh / Light Kind; null = a 2D type.
     * Accepts the TYPE itself, shape names ("cube", "sphere", "model", "terrain", "water"...), "light3d", "spot", "sun", "camera3d",
     * "cameraview"; in a 3D scene "LIGHT" and "CAMERA" mean the 3D ones and "EMPTY" an empty 3D object.
     */
    static String[] kind3D(String type, JsonObject props, boolean scene3D) {
        String t = upper(type).replace(" ", "").replace("_", "").replace("-", "");
        String mesh = props != null && props.has("Mesh") ? upper(str(props.get("Mesh"))) : "";
        if (t.equals("NAVGRID") || t.equals("PATHGRID") || t.equals("NAVMESH") || t.equals("PATHFINDING") || t.equals("NAV")) return new String[]{"NAVGRID", null};
        if (t.equals("NETWORK") || t.equals("MULTIPLAYER") || t.equals("NET") || t.equals("LAN")) return new String[]{"NETWORK", null};
        if (t.equals("CAMERAVIEW") || t.equals("CAMVIEW") || t.equals("MINIMAP") || t.equals("MIRROR")) return new String[]{"CAMERAVIEW", null};
        if (t.equals("CAMERA3D") || t.equals("3DCAMERA") || (scene3D && t.equals("CAMERA"))) return new String[]{"CAMERA3D", null};
        if (t.equals("LIGHT3D") || t.equals("3DLIGHT") || t.endsWith("LIGHT3D") || t.equals("SUN") || t.equals("SPOT") || t.equals("SPOTLIGHT")
                || (scene3D && t.contains("LIGHT"))) {
            String k = props != null && props.has("Light Kind") ? upper(str(props.get("Light Kind")))
                    : props != null && props.has("Light Type") ? upper(str(props.get("Light Type"))) : t;
            if (k.contains("SPOT") || k.contains("CONE")) k = "SPOT";
            else if (k.contains("DIRECTIONAL") || k.contains("SUN")) k = "DIRECTIONAL";
            else k = "POINT";
            return new String[]{"LIGHT3D", k};
        }
        if (t.equals("OBJ3D") || t.equals("OBJECT3D") || t.equals("3D") || t.equals("SHAPE3D") || t.equals("MESH")) {
            if (mesh.isEmpty() && props != null && props.has("Model") && !str(props.get("Model")).isEmpty()) mesh = "MODEL";
            return new String[]{"OBJ3D", mesh.isEmpty() ? "CUBE" : mesh};
        }
        if (t.equals("GLB") || t.equals("GLTF") || t.equals("MODEL3D") || t.equals("3DMODEL")) return new String[]{"OBJ3D", "MODEL"};
        if (t.equals("BALL") && scene3D) return new String[]{"OBJ3D", "SPHERE"};
        if (t.equals("GROUND") && scene3D) return new String[]{"OBJ3D", "PLANE"};
        for (String m : MESHES) {
            if (!t.equals(m)) continue;
            if (m.equals("EMPTY") && !scene3D) return null; // 2D: EMPTY = GROUP
            return new String[]{"OBJ3D", m};
        }
        return null;
    }

    /** Adds a 3D element ({TYPE, preset} from {@link #kind3D}) or a CAMERAVIEW; it is selected afterwards. Error text or null. GL thread. */
    static String add3D(TestApp app, LibgdxEditor ed, String[] k3) {
        Actor before = ed.getSelectedActor();
        if (k3[0].equals("CAMERAVIEW")) app.getControlLayer().actionAddCameraView();
        else if (k3[0].equals("NAVGRID")) app.getControlLayer().actionAddNavGrid();
        else if (k3[0].equals("NETWORK")) {
            if (!app.getControlLayer().actionAddNetwork())
                return "scene '" + ed.getScene() + "' already has a NETWORK element (one per scene): set its properties instead";
        } else {
            if (!ed.is3DScene())
                return k3[0] + " is a 3D element and scene '" + ed.getScene() + "' is 2D. Make a 3D scene first: scenes {action:'create', name:'...', type:'3D'}";
            if (k3[0].equals("OBJ3D") && !java.util.Arrays.asList(MESHES).contains(k3[1]))
                return "unknown Mesh '" + k3[1] + "'. Use " + String.join(", ", MESHES);
            app.getControlLayer().actionAdd3D(k3[0], k3[1]);
        }
        Actor now = ed.getSelectedActor();
        if (now == null || now == before) return "the editor did not create the " + k3[0] + " element";
        return null;
    }

    private static int kindIndex(String type) {
        String t = type.toUpperCase(Locale.ROOT).replace(" ", "");
        if (t.equals("TILEDMAP") || t.equals("TILEMAP")) t = "MAP";
        if (t.equals("CONTAINER") || t.equals("EMPTY") || t.equals("FOLDER")) t = "GROUP";
        if (t.equals("UI") || t.equals("UIMAKER") || t.equals("UI_HOLDER") || t.equals("SCREEN")) t = "UIHOLDER";
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(t)) return i;
        return -1;
    }

    /** Canonical property key (case-insensitive) or null. */
    private static String canon(Map<String, String> defaults, Map<String, String> live, String key) {
        for (String k : defaults.keySet()) if (k.equals(key)) return k;
        for (String k : live.keySet()) if (k.equals(key)) return k;
        for (String k : defaults.keySet()) if (k.equalsIgnoreCase(key)) return k;
        for (String k : live.keySet()) if (k.equalsIgnoreCase(key)) return k;
        return null;
    }

    private static List<String> orderKeys(JsonObject props) {
        ArrayList<String> first = new ArrayList<>(), rest = new ArrayList<>();
        for (String k : props.keySet()) {
            if (k.equals("type") || k.equals("Shape") || k.equals("Light Type") || k.equals("image") || k.equals("Pad Image") || k.equals("Button Image") || k.equals("Pressed Image") || k.equals("Touch Start Image") || k.equals("Touch End Image"))
                first.add(k);
            else rest.add(k);
        }
        first.addAll(rest);
        return first;
    }

    /** Applies props to the currently selected actor. Returns the list of problems (empty = ok). */
    private static List<String> applyProps(TestApp app, Actor actor, JsonObject props) {
        ArrayList<String> problems = new ArrayList<>();
        PropertySet<String, Object> ps = PropertySet.getPropertySet(actor);
        Map<String, String> live = Bridge.snapshot(ps);
        String type = live.get("TYPE"), lt = live.get("Light Type");
        Map<String, String> defs = ItemDefaults.of(type, lt);
        for (String raw : orderKeys(props)) {
            String key = canon(defs, live, raw);
            if (key == null) {
                problems.add("unknown property '" + raw + "' for " + type + " (see read_docs('elements'))");
                continue;
            }
            if (key.equals("TYPE")) {
                problems.add("TYPE cannot be changed; delete and re-add the element");
                continue;
            }
            String v = str(props.get(raw));
            if (key.equals("z") && !SceneInfo.is3DType(type)) { // 3D: z is a depth in meters, may be negative
                try {
                    if (Float.parseFloat(v) < 0) {
                        v = "0";
                        problems.add("z cannot be negative (it would crash the draw order); used 0. Convention: background 0, ground 10, actors 20, UI 100+");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            app.getEditor().selectActor(actor);
            String err = AiActions.applyProperty(app, key, v);
            if (err != null) problems.add(key + ": " + err);
        }
        return problems;
    }

    static ToolResult elements(JsonObject a) throws Exception {
        final String scene = CoreTools.s(a, "scene");
        if (!scene.isEmpty()) Proj.openScene(scene);
        else Proj.waitLoaded(20_000);
        final JsonArray ops = opsOf(a);
        final JsonArray results = new JsonArray();
        final long end = System.currentTimeMillis() + BUDGET_MS;
        int i = 0;
        while (i < ops.size()) {
            if (System.currentTimeMillis() > end) {
                JsonObject o = new JsonObject();
                o.add("results", results);
                o.addProperty("stopped_at_op", i);
                o.addProperty("note", "Time budget used up. Resend the remaining ops starting from index " + i + ".");
                return ToolResult.text(o.toString());
            }
            final int from = i, to = Math.min(ops.size(), i + CHUNK);
            Gl.run(() -> {
                for (int k = from; k < to; k++) results.add(runElementOp(ops.get(k)));
                return null;
            });
            i = to;
        }
        Gl.run(() -> {
            Proj.project().save(Proj.editor());
            return null;
        });
        int bad = 0;
        for (JsonElement r : results) if (!r.getAsJsonObject().get("ok").getAsBoolean()) bad++;
        JsonObject o = new JsonObject();
        o.addProperty("done", ops.size() - bad);
        o.addProperty("failed", bad);
        // keep the answer small: only failures and created names
        JsonArray details = new JsonArray();
        for (JsonElement r : results) {
            JsonObject ro = r.getAsJsonObject();
            if (!ro.get("ok").getAsBoolean() || ro.has("info")) details.add(ro);
        }
        if (details.size() > 0) o.add("details", details);
        final java.util.HashSet<String> touched = new java.util.HashSet<>();
        for (JsonElement r : results) {
            JsonObject ro = r.getAsJsonObject();
            if (ro.get("ok").getAsBoolean() && ro.has("name")) touched.add(ro.get("name").getAsString());
        }
        final List<String> warns = Gl.run(() -> {
            ArrayList<String> w = new ArrayList<>();
            LibgdxEditor ed = Proj.editor();
            w.addAll(Warnings.text(ed, touched));
            w.addAll(Warnings.bounds(ed, touched));
            return w;
        });
        if (!warns.isEmpty()) {
            JsonArray wa = new JsonArray();
            for (int k = 0; k < Math.min(10, warns.size()); k++) wa.add(warns.get(k));
            o.add("warnings", wa);
        }
        return bad == 0 ? ToolResult.text(o.toString()) : errorWith(o.toString());
    }

    private static ToolResult errorWith(String s) {
        // partial failure: the model must see it, but it is not a protocol error
        return ToolResult.text(s);
    }

    private static JsonObject runElementOp(JsonElement e) {
        String op = "?", name = "";
        try {
            if (!e.isJsonObject()) return res(op, name, false, "operation must be an object");
            JsonObject o = e.getAsJsonObject();
            op = CoreTools.s(o, "op");
            name = CoreTools.s(o, "name");
            TestApp app = Bridge.app();
            LibgdxEditor ed = Proj.editor();
            JsonObject props = o.has("props") && o.get("props").isJsonObject() ? o.getAsJsonObject("props") : new JsonObject();
            switch (op) {
                case "add": {
                    String type = CoreTools.s(o, "type");
                    if (type.isEmpty()) return res(op, name, false, "type is required (BOX, CIRCLE, TEXT, JOYSTICK, PROGRESS, CUSTOM, PARTICLE, CAMERA, MAP, GROUP, UIHOLDER, LIGHT, CAMERAVIEW, NAVGRID (path finding grid), NETWORK (Wi-Fi multiplayer); 3D scenes: OBJ3D, LIGHT3D, CAMERA3D)");
                    if (!name.isEmpty() && ed.getBodiesList().contains(name)) return res(op, name, false, "an element named '" + name + "' already exists (use set)");
                    if (!name.isEmpty() && !name.matches("[A-Za-z_][A-Za-z0-9_]*")) return res(op, name, false, "name must be letters, digits and _ (not starting with a digit)");
                    String t = type.toUpperCase(Locale.ROOT).replace(" ", "");
                    boolean scene3D = ed.is3DScene();
                    String[] k3 = kind3D(type, props, scene3D);
                    String note3D = "";
                    if (k3 != null) {
                        String err = add3D(app, ed, k3);
                        if (err != null) return res(op, name, false, err);
                        if (!t.equals(k3[0])) note3D = " as " + k3[0] + (k3[1] == null ? "" : " " + k3[1]);
                        props = props.deepCopy();
                        props.remove("Light Type");
                        if (k3[0].equals("OBJ3D")) props.remove("Mesh");
                        if (k3[0].equals("LIGHT3D")) props.remove("Light Kind");
                    } else if (t.equals("LIGHT") || t.contains("LIGHT")) {
                        String lt = props.has("Light Type") ? str(props.get("Light Type")).toLowerCase() : (t.contains("CONE") ? "cone" : t.contains("DIRECTIONAL") ? "directional" : "point");
                        int idx = lt.equals("directional") ? 1 : lt.equals("cone") ? 2 : 0;
                        app.getControlLayer().actionAddLight(idx);
                    } else {
                        int idx = kindIndex(type);
                        if (idx < 0) return res(op, name, false, "unknown type '" + type + "'. Use BOX, CIRCLE, TEXT, JOYSTICK, PROGRESS, CUSTOM, PARTICLE, CAMERA, MAP, GROUP, UIHOLDER, LIGHT, CAMERAVIEW, NAVGRID or NETWORK"
                                + (scene3D ? "; 3D: OBJ3D (or a shape: CUBE, SPHERE, CAPSULE, PLANE, MODEL, TERRAIN, WATER...), LIGHT3D, CAMERA3D" : "; 3D elements need a 3D scene (scenes create {type:'3D'})"));
                        app.getControlLayer().actionAddBody(idx);
                    }
                    Actor actor = ed.getSelectedActor();
                    if (actor == null) return res(op, name, false, "the editor did not create the element");
                    String created = actor.getName();
                    if (!name.isEmpty()) {
                        String err = PropertyEditing.applyString(app, "name", name);
                        if (err != null) {
                            ItemActions.delete(app, actor);
                            return res(op, name, false, "rename failed: " + err);
                        }
                        created = name;
                    }
                    JsonObject all = props.deepCopy();
                    if (o.has("parent")) all.addProperty("parent", CoreTools.s(o, "parent"));
                    all.remove("Light Type"); // chosen at creation
                    List<String> problems = applyProps(app, actor, all);
                    String zNote = ZPolicy.applyOnAdd(app, ed, actor, props);
                    String made = "created" + note3D + (zNote.isEmpty() ? "" : " (" + zNote + ")");
                    return problems.isEmpty() ? res(op, created, true, made) : res(op, created, true, made + ", but: " + String.join("; ", problems));
                }
                case "set": {
                    Actor actor = Bridge.actor(name);
                    if (actor == null) return res(op, name, false, "no element named '" + name + "' in scene '" + ed.getScene() + "'");
                    JsonArray resetList = o.has("reset") && o.get("reset").isJsonArray() ? o.getAsJsonArray("reset") : new JsonArray();
                    if (props.size() == 0 && resetList.size() == 0) return res(op, name, false, "props is empty (give props, or reset:[property names])");
                    // reset: put the named properties back to their default value (what a new element starts with)
                    if (resetList.size() > 0) {
                        Map<String, String> live = Bridge.snapshot(PropertySet.getPropertySet(actor));
                        Map<String, String> defs = ItemDefaults.of(live.get("TYPE"), live.get("Light Type"));
                        for (JsonElement re : resetList) {
                            String rk = canon(defs, live, str(re));
                            if (rk != null && defs.containsKey(rk) && !props.has(rk)) props.addProperty(rk, defs.get(rk));
                            else if (rk == null) props.addProperty(str(re), "unknown");
                        }
                    }
                    List<String> problems = applyProps(app, actor, props);
                    return problems.isEmpty() ? res(op, name, true, null) : res(op, name, false, String.join("; ", problems));
                }
                case "delete": {
                    Actor actor = Bridge.actor(name);
                    if (actor == null) return res(op, name, false, "no element named '" + name + "'");
                    ItemActions.delete(app, actor);
                    return res(op, name, true, null);
                }
                case "duplicate": {
                    Actor actor = Bridge.actor(name);
                    if (actor == null) return res(op, name, false, "no element named '" + name + "'");
                    Actor copy = ItemActions.duplicate(app, actor);
                    String nn = CoreTools.s(o, "new_name");
                    if (!nn.isEmpty()) {
                        String err = ItemActions.rename(app, copy, nn);
                        if (err != null) return res(op, copy.getName(), true, "duplicated as " + copy.getName() + " (rename failed: " + err + ")");
                    }
                    List<String> problems = props.size() > 0 ? applyProps(app, copy, props) : new ArrayList<String>();
                    return res(op, copy.getName(), true, "duplicated" + (problems.isEmpty() ? "" : ", but: " + String.join("; ", problems)));
                }
                case "rename": {
                    Actor actor = Bridge.actor(name);
                    if (actor == null) return res(op, name, false, "no element named '" + name + "'");
                    String err = ItemActions.rename(app, actor, CoreTools.s(o, "new_name"));
                    return err == null ? res(op, CoreTools.s(o, "new_name"), true, "renamed from " + name) : res(op, name, false, err);
                }
                case "select": {
                    Actor actor = Bridge.actor(name);
                    if (actor == null) return res(op, name, false, "no element named '" + name + "'");
                    ed.selectActor(actor);
                    return res(op, name, true, null);
                }
                case "order": {
                    Actor actor = Bridge.actor(name);
                    if (actor == null) return res(op, name, false, "no element named '" + name + "'");
                    if (SceneInfo.is3DType(String.valueOf(PropertySet.getPropertySet(actor).get("TYPE"))))
                        return res(op, name, false, "a 3D element has no draw order: its z is a depth in meters (use set {z}); Render Order orders transparent 3D objects");
                    ed.selectActor(actor);
                    int oz = CoreTools.i(o, "z", 0);
                    PropertyEditing.applyFloat(app, "z", Math.max(0, oz));
                    return res(op, name, true, oz < 0 ? "z cannot be negative: used 0" : null);
                }
                default:
                    return res(op, name, false, "unknown op '" + op + "' (add|set|delete|duplicate|rename|select|order)");
            }
        } catch (Throwable t) {
            return res(op, name, false, String.valueOf(t));
        }
    }

    // ================================================================== joints

    static JsonArray jointsOf(Project p, String scene) {
        JsonArray arr = new JsonArray();
        FileHandle dir = Gdx.files.absolute(p.getJoints(scene));
        if (!dir.exists()) return arr;
        FileHandle[] files = dir.list();
        java.util.Arrays.sort(files, (x, y) -> x.name().compareToIgnoreCase(y.name()));
        for (FileHandle f : files) {
            if (f.isDirectory() || !f.name().contains("-")) continue;
            int i = f.name().lastIndexOf('-');
            JsonObject j = new JsonObject();
            j.addProperty("name", f.name().substring(0, i));
            j.addProperty("type", f.name().substring(i + 1));
            JsonObject vals = new JsonObject();
            try {
                for (JsonElement e : JsonParser.parseString(f.readString()).getAsJsonArray()) {
                    JsonObject fo = e.getAsJsonObject();
                    vals.addProperty(fo.get("name").getAsString(), humanJointValue(fo.get("value").getAsString()));
                }
            } catch (Exception ignored) {
            }
            j.add("props", vals);
            arr.add(j);
        }
        return arr;
    }

    private static String humanJointValue(String v) {
        if (v.endsWith(".getBody().getWorldCenter()")) return v.substring(0, v.length() - ".getBody().getWorldCenter()".length());
        if (v.endsWith(".getBody()")) return v.substring(0, v.length() - ".getBody()".length());
        if (v.startsWith("new Vector2(")) return v.replace("new Vector2(", "").replace(")", "").replace("f", "").replace("&&", ",");
        return v.endsWith("f") && v.length() > 1 && Character.isDigit(v.charAt(0)) ? v.substring(0, v.length() - 1) : v;
    }

    static ToolResult joints(JsonObject a) throws Exception {
        final JsonArray ops = opsOf(a);
        final JsonArray results = new JsonArray();
        Gl.run(() -> {
            LibgdxEditor ed = Proj.editor();
            Project p = ed.getProject();
            for (JsonElement e : ops) {
                String op = "?", name = "";
                try {
                    JsonObject o = e.getAsJsonObject();
                    op = CoreTools.s(o, "op");
                    name = CoreTools.s(o, "name");
                    switch (op) {
                        case "list": {
                            JsonObject r = res(op, null, true, null);
                            r.add("joints", jointsOf(p, ed.getScene()));
                            results.add(r);
                            break;
                        }
                        case "delete": {
                            FileHandle dir = Gdx.files.absolute(p.getJoints(ed.getScene()));
                            boolean found = false;
                            if (dir.exists()) for (FileHandle f : dir.list()) if (f.name().startsWith(name + "-")) { f.delete(); found = true; }
                            results.add(found ? res(op, name, true, null) : res(op, name, false, "no joint named " + name));
                            break;
                        }
                        case "add":
                        case "set": {
                            results.add(writeJoint(p, ed, op, o));
                            break;
                        }
                        default:
                            results.add(res(op, name, false, "unknown op (add|set|delete|list)"));
                    }
                } catch (Throwable t) {
                    results.add(res(op, name, false, String.valueOf(t)));
                }
            }
            try { Bridge.controls().getJointsList().refresh(); } catch (Throwable ignored) {}
            return null;
        });
        return ToolResult.text(results.toString());
    }

    private static JsonObject writeJoint(Project p, LibgdxEditor ed, String op, JsonObject o) throws Exception {
        String name = CoreTools.s(o, "name");
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) return res(op, name, false, "name must be letters, digits and _");
        FileHandle dir = Gdx.files.absolute(p.getJoints(ed.getScene()));
        FileHandle existing = null;
        if (dir.exists()) for (FileHandle f : dir.list()) if (f.name().startsWith(name + "-")) existing = f;
        String type = CoreTools.s(o, "type").replace("Def", "");
        JsonObject props = o.has("props") && o.get("props").isJsonObject() ? o.getAsJsonObject("props") : new JsonObject();
        if (op.equals("add") && existing != null) return res(op, name, false, "joint '" + name + "' already exists (use set)");
        if (op.equals("set") && existing == null) return res(op, name, false, "no joint named '" + name + "'");
        if (existing != null && type.isEmpty()) type = existing.name().substring(existing.name().lastIndexOf('-') + 1);
        if (type.isEmpty()) return res(op, name, false, "type is required, e.g. RevoluteJoint");
        if (!type.endsWith("Joint")) type = type + "Joint";
        // old values (set keeps what is not mentioned)
        LinkedHashMap<String, String> old = new LinkedHashMap<>();
        if (existing != null) {
            try {
                for (JsonElement e : JsonParser.parseString(existing.readString()).getAsJsonArray())
                    old.put(e.getAsJsonObject().get("name").getAsString(), e.getAsJsonObject().get("value").getAsString());
            } catch (Exception ignored) {
            }
        }
        // bodyA/bodyB shortcuts
        if (o.has("bodyA")) props.addProperty("bodyA", CoreTools.s(o, "bodyA"));
        if (o.has("bodyB")) props.addProperty("bodyB", CoreTools.s(o, "bodyB"));

        JointsHelper.init();
        LinkedHashMap<String, String[]> fields = new LinkedHashMap<>(); // name -> {kind, default}
        String params = JointsHelper.get(type, "params"), types = JointsHelper.get(type, "types");
        if (params == null && JointsHelper.get(type + "Def", "params") != null) {
            params = JointsHelper.get(type + "Def", "params");
            types = JointsHelper.get(type + "Def", "types");
        }
        if (params == null) return res(op, name, false, "unknown joint type '" + type + "'. Types: Revolute, Prismatic, Distance, Pulley, Mouse, Wheel, Weld, Friction, Rope, Motor (+ 'Joint')");
        ArrayList<String> lower = new ArrayList<>();
        String[] names = params.split(","), ts = types.split(",");
        for (int i = 0; i < ts.length && i < names.length; i++) {
            lower.add(names[i].toLowerCase());
            String k = ts[i].toLowerCase();
            fields.put(names[i], new String[]{k.equals("vector2") ? "vec2" : (k.equals("body") ? "body" : "float"),
                    k.equals("vector2") ? "new Vector2(0f&&0f)" : (k.equals("float") ? "0" : "")});
        }
        Class<?> clazz = Class.forName("com.badlogic.gdx.physics.box2d.joints." + type + "Def");
        Object def = clazz.getConstructor().newInstance();
        for (Field f : clazz.getFields()) {
            if (Modifier.isFinal(f.getModifiers()) && !com.badlogic.gdx.math.Vector2.class.isAssignableFrom(f.getType())) continue;
            if (lower.contains(f.getName().toLowerCase())) continue;
            Object dv = f.get(def);
            if (f.getType() == float.class || f.getType() == Float.class) fields.put(f.getName(), new String[]{"float", String.valueOf(dv)});
            else if (com.badlogic.gdx.math.Vector2.class.isAssignableFrom(f.getType())) {
                com.badlogic.gdx.math.Vector2 vv = (com.badlogic.gdx.math.Vector2) dv;
                fields.put(f.getName(), new String[]{"vec2", "new Vector2(" + vv.x + "f&&" + vv.y + "f)"});
            } else if (f.getType() == boolean.class || f.getType() == Boolean.class) fields.put(f.getName(), new String[]{"bool", String.valueOf(dv)});
        }
        for (String k : props.keySet()) {
            boolean ok = false;
            for (String fn : fields.keySet()) if (fn.equals(k)) ok = true;
            if (!ok) return res(op, name, false, "unknown joint property '" + k + "' for " + type + ". Valid: " + fields.keySet());
        }
        ArrayList<LinkedHashMap<String, Object>> out = new ArrayList<>();
        ArrayList<String> bodies = ed.getBodiesList();
        for (Map.Entry<String, String[]> fe : fields.entrySet()) {
            String fname = fe.getKey(), kind = fe.getValue()[0], value;
            String code;
            if (props.has(fname)) {
                String given = str(props.get(fname)).trim();
                switch (kind) {
                    case "body": {
                        if (!bodies.contains(given)) return res(op, name, false, fname + ": no element named '" + given + "'. Elements: " + bodies);
                        value = given + ".getBody()";
                        code = value + ".getBody()";
                        break;
                    }
                    case "vec2": {
                        String[] xy = given.replace("[", "").replace("]", "").split("[,;& ]+");
                        float x, y;
                        try {
                            if (xy.length != 2) throw new NumberFormatException();
                            x = Float.parseFloat(xy[0]);
                            y = Float.parseFloat(xy[1]);
                            value = "new Vector2(" + xy[0] + "f&&" + xy[1] + "f)";
                        } catch (NumberFormatException nfe) {
                            if (!bodies.contains(given)) return res(op, name, false, fname + ": give 'x,y' numbers or the name of an element (its center is used)");
                            value = given + ".getBody().getWorldCenter()";
                        }
                        code = "\t\t\t%1$s." + fname + ".set(" + value.replace("&&", ",") + ");\n";
                        break;
                    }
                    case "float": {
                        try { Float.parseFloat(given); } catch (Exception ex) { return res(op, name, false, fname + ": not a number"); }
                        value = given;
                        code = "\t\t\t%1$s." + fname + "=" + value + "f;\n";
                        break;
                    }
                    default: {
                        value = String.valueOf(Boolean.parseBoolean(given));
                        code = "\n\t\t\t%1$s." + fname + "=" + value + ";\n";
                    }
                }
            } else if (old.containsKey(fname)) {
                value = old.get(fname);
                switch (kind) {
                    case "body": code = value + ".getBody()"; break;
                    case "vec2": code = "\t\t\t%1$s." + fname + ".set(" + value.replace("&&", ",") + ");\n"; break;
                    case "float": code = "\t\t\t%1$s." + fname + "=" + value + "f;\n"; break;
                    default: code = "\n\t\t\t%1$s." + fname + "=" + value + ";\n";
                }
            } else {
                value = fe.getValue()[1];
                if (kind.equals("body")) {
                    return res(op, name, false, "missing " + fname + " (an element name)");
                }
                switch (kind) {
                    case "vec2": if (value.isEmpty()) value = "new Vector2(0f&&0f)"; code = "\t\t\t%1$s." + fname + ".set(" + value.replace("&&", ",") + ");\n"; break;
                    case "float": if (value.isEmpty()) value = "0"; code = "\t\t\t%1$s." + fname + "=" + value + "f;\n"; break;
                    default: if (value.isEmpty()) value = "false"; code = "\n\t\t\t%1$s." + fname + "=" + value + ";\n";
                }
            }
            LinkedHashMap<String, Object> h = new LinkedHashMap<>();
            h.put("value", value);
            h.put("name", fname);
            h.put("code", code);
            out.add(h);
        }
        FileHandle target = Gdx.files.absolute(p.getJoints(ed.getScene()) + name + "-" + type);
        if (existing != null && !existing.name().equals(target.name())) existing.delete();
        target.writeString(new Gson().toJson(out), false);
        return res(op, name, true, null);
    }

    // ================================================================== variables

    static ToolResult variables(JsonObject a) throws Exception {
        final JsonArray ops = opsOf(a);
        final JsonArray results = new JsonArray();
        Gl.run(() -> {
            LibgdxEditor ed = Proj.editor();
            Project p = ed.getProject();
            // group by scene
            java.util.HashMap<String, ArrayList<VarModel>> cache = new java.util.HashMap<>();
            java.util.HashSet<String> dirty = new java.util.HashSet<>();
            for (JsonElement e : ops) {
                String op = "?", name = "";
                try {
                    JsonObject o = e.getAsJsonObject();
                    op = CoreTools.s(o, "op");
                    name = CoreTools.s(o, "name");
                    String scene = CoreTools.s(o, "scene");
                    if (scene.isEmpty()) scene = CoreTools.s(a, "scene");
                    if (scene.isEmpty()) scene = ed.getScene();
                    if (!Proj.scenes(p).contains(scene)) {
                        results.add(res(op, name, false, "no scene '" + scene + "'"));
                        continue;
                    }
                    ArrayList<VarModel> list = cache.get(scene);
                    if (list == null) {
                        list = new ArrayList<>();
                        FileHandle fh = Gdx.files.absolute(p.getVariables(scene));
                        if (fh.exists()) try { list = new Gson().fromJson(fh.readString(), new TypeToken<ArrayList<VarModel>>() {}.getType()); } catch (Exception ignored) {}
                        if (list == null) list = new ArrayList<>();
                        cache.put(scene, list);
                    }
                    VarModel found = null;
                    for (VarModel v : list) if (v.getName().equals(name)) found = v;
                    switch (op) {
                        case "list": {
                            JsonObject r = res(op, null, true, null);
                            r.addProperty("scene", scene);
                            r.add("variables", new Gson().toJsonTree(list));
                            results.add(r);
                            break;
                        }
                        case "add":
                            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) { results.add(res(op, name, false, "name must be a valid Java identifier")); break; }
                            if (found != null) { results.add(res(op, name, false, "variable already exists (use set)")); break; }
                            list.add(new VarModel().setType(CoreTools.s(o, "type").isEmpty() ? "int" : CoreTools.s(o, "type")).setName(name).setValue(str(o.get("value"))));
                            dirty.add(scene);
                            results.add(res(op, name, true, null));
                            break;
                        case "set":
                            if (found == null) { results.add(res(op, name, false, "no variable named '" + name + "'")); break; }
                            if (o.has("type")) found.setType(CoreTools.s(o, "type"));
                            if (o.has("value")) found.setValue(str(o.get("value")));
                            dirty.add(scene);
                            results.add(res(op, name, true, null));
                            break;
                        case "delete":
                            if (found == null) { results.add(res(op, name, false, "no variable named '" + name + "'")); break; }
                            list.remove(found);
                            dirty.add(scene);
                            results.add(res(op, name, true, null));
                            break;
                        default:
                            results.add(res(op, name, false, "unknown op (add|set|delete|list)"));
                    }
                } catch (Throwable t) {
                    results.add(res(op, name, false, String.valueOf(t)));
                }
            }
            for (String scene : dirty) {
                FileHandle fh = Gdx.files.absolute(p.getVariables(scene));
                fh.writeString(new Gson().toJson(cache.get(scene)), false);
                if (scene.equals(ed.getScene())) try { Bridge.controls().getVarsItem().setFileHandle(fh); } catch (Throwable ignored) {}
            }
            return null;
        });
        return ToolResult.text(results.toString());
    }

    // ================================================================== editor view

    static ToolResult editor(JsonObject a) throws Exception {
        final String action = CoreTools.s(a, "action");
        if (action.equals("open_scene")) {
            Proj.openScene(CoreTools.s(a, "name"));
            return ToolResult.text("opened");
        }
        return Gl.run(() -> {
            TestApp app = Bridge.app();
            LibgdxEditor ed = Proj.editor();
            switch (action) {
                case "camera": {
                    com.badlogic.gdx.graphics.OrthographicCamera cam = (com.badlogic.gdx.graphics.OrthographicCamera) ed.getCamera();
                    if (a.has("x")) cam.position.x = (float) a.get("x").getAsDouble();
                    if (a.has("y")) cam.position.y = (float) a.get("y").getAsDouble();
                    if (a.has("zoom")) ed.setScale((float) a.get("zoom").getAsDouble());
                    cam.update();
                    return ToolResult.text("camera x=" + cam.position.x + " y=" + cam.position.y + " zoom=" + cam.zoom);
                }
                case "center":
                    ed.centerCamera();
                    return ToolResult.text("centered");
                case "focus": {
                    Actor ac = Bridge.actor(CoreTools.s(a, "name"));
                    if (ac == null) return ToolResult.error("no element named " + CoreTools.s(a, "name"));
                    com.badlogic.gdx.graphics.OrthographicCamera cam = (com.badlogic.gdx.graphics.OrthographicCamera) ed.getCamera();
                    cam.position.x = ac.getX() + ac.getWidth() / 2f;
                    cam.position.y = ac.getY() + ac.getHeight() / 2f;
                    cam.update();
                    ed.selectActor(ac);
                    return ToolResult.text("focused on " + ac.getName());
                }
                case "select": {
                    boolean ok = ed.selectByName(CoreTools.s(a, "name"));
                    return ok ? ToolResult.text("selected") : ToolResult.error("no element named " + CoreTools.s(a, "name"));
                }
                case "undo": app.getControlLayer().actionUndo(); return ToolResult.text("undone");
                case "redo": app.getControlLayer().actionRedo(); return ToolResult.text("redone");
                case "save": ed.getProject().save(ed); return ToolResult.text("saved");
                case "set_tool": {
                    boolean ok = app.getControlLayer().actionSetTouchMode(CoreTools.s(a, "tool"));
                    return ToolResult.text(ok ? "tool set" : "tool needs a selected element; fell back to grid");
                }
                default: return ToolResult.error("unknown action '" + action + "'");
            }
        });
    }

    static JsonObject eventsOf(Project p, String scene) {
        return LogicTools.eventsOf(p, scene);
    }
}
