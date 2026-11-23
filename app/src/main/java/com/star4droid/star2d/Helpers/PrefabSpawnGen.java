package com.star4droid.star2d.Helpers;

import com.google.gson.JsonElement;
import com.star4droid.star2d.prefab.PrefabModel;
import com.star4droid.star2d.prefab.PrefabStore;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Create from prefab" blocks: for the prefabs a scene's code asks for ({@code Prefabs.spawn("path", ...)}), the scene
 * class gets a method that builds the prefab's elements the way the scene builds its own (properties, events, behaviors),
 * and registers it with {@code com.star4droid.template.Utils.Prefabs} when the scene starts. The body script of a prefab
 * element is not attached to these copies.
 */
final class PrefabSpawnGen {
    private PrefabSpawnGen() {}

    static final class Out {
        /** Members of the scene class. */
        String members = "";
        /** Code for the scene's create method (registration). */
        String register = "";
    }

    private static final Pattern CALL = Pattern.compile("Prefabs\\.spawn(?:3D)?\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final List<String> SKIP = Arrays.asList("TYPE", "Shape", "lock", "parent", "prefab", "pid", "pbase", "Script",
            "behaviors", "scriptVars", "objectVars", "net", "name", "x", "y");
    private static final List<String> TYPES_3D = Arrays.asList("OBJ3D", "LIGHT3D", "CAMERA3D");
    private static final List<String> NO_ZINDEX = Arrays.asList("OBJ3D", "LIGHT3D", "CAMERA3D", "NAVGRID", "NETWORK");

    /** The prefab paths used by the code of a scene (events, body scripts, scene script). */
    static LinkedHashSet<String> used(com.star4droid.star2d.Helpers.editor.Project project, String scene) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String root = project.getPath();
        scan(new File(root + "/Events/" + scene), out, 0);
        scan(new File(project.getScriptsPath(scene)), out, 0);
        scan(new File(root + "/java/com/star4droid/Game/Scripts/" + scene.toLowerCase()), out, 0);
        scan(new File(project.getSceneScript(scene)), out, 0);
        return out;
    }

    private static void scan(File f, LinkedHashSet<String> out, int depth) {
        if (f == null || !f.exists() || depth > 8) return;
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) for (File c : fs) scan(c, out, depth + 1);
            return;
        }
        String n = f.getName();
        if (!(n.endsWith(".java") || n.endsWith(".code"))) return;
        String t = FileUtil.readFile(f.getAbsolutePath());
        if (t == null || !t.contains("Prefabs.spawn")) return;
        Matcher m = CALL.matcher(t);
        while (m.find()) {
            String p = m.group(1).replace("\\\"", "\"").trim();
            if (p.endsWith(".prefab")) p = p.substring(0, p.length() - 7);
            while (p.startsWith("/")) p = p.substring(1);
            if (p.startsWith("prefabs/")) p = p.substring(8);
            if (!p.isEmpty()) out.add(p);
        }
    }

    static Out generate(com.star4droid.star2d.Helpers.editor.Project project, String scene,
                        com.star4droid.star2d.behaviors.BehaviorCodeGen.Env env) {
        Out o = new Out();
        LinkedHashSet<String> paths = used(project, scene);
        if (paths.isEmpty()) return o;
        StringBuilder dispatch = new StringBuilder(), methods = new StringBuilder();
        int k = 0;
        for (String path : paths) {
            PrefabModel m;
            try {
                m = PrefabStore.load(project, path);
            } catch (Exception e) {
                methods.append("\n// prefab ").append(path).append(": ").append(String.valueOf(e.getMessage()).replace('\n', ' ')).append('\n');
                continue;
            }
            String method = "__prefab_" + (k++);
            dispatch.append("\t\tif (").append(ValueEmitCompat.quote(path)).append(".equals(__path)) return ").append(method).append("(__name, __x, __y, __z);\n");
            methods.append(build(method, path, m, env));
        }
        o.members = "\n\t/** Prefabs of the Create from prefab blocks of this scene. */\n"
                + "\tpublic com.star4droid.template.Utils.PlayerItem __prefab(String __path, String __name, float __x, float __y, float __z) {\n"
                + dispatch + "\t\treturn null;\n\t}\n" + methods;
        o.register = "\ncom.star4droid.template.Utils.Prefabs.register(this, new com.star4droid.template.Utils.Prefabs.Factory() {\n"
                + "\tpublic com.star4droid.template.Utils.PlayerItem make(String p, String n, float x, float y, float z) { return __prefab(p, n, x, y, z); }\n"
                + "});\n";
        return o;
    }

    private static String build(String method, String path, PrefabModel m, com.star4droid.star2d.behaviors.BehaviorCodeGen.Env env) {
        StringBuilder decl = new StringBuilder(), body = new StringBuilder(), after = new StringBuilder();
        LinkedHashMap<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < m.nodes.size(); i++) index.put(m.nodes.get(i).pid, i);
        String rootVar = null;
        for (int i = 0; i < m.nodes.size(); i++) {
            PrefabModel.Node n = m.nodes.get(i);
            String type = str(n.props.get("TYPE"));
            String defType = CodeGenerator.getType(type);
            if ("Unknown".equals(defType) || "LightDef".equals(defType)) {
                body.append("\t\t// ").append(n.name).append(": ").append(type).append(" is not made by Create from prefab\n");
                continue;
            }
            Class<?> cls;
            Object ins;
            try {
                cls = Class.forName("com.star4droid.star2d.ElementDefs." + defType);
                ins = cls.getConstructor().newInstance();
            } catch (Exception e) {
                body.append("\t\t// ").append(n.name).append(": ").append(e).append('\n');
                continue;
            }
            boolean is3d = TYPES_3D.contains(type);
            String d = "__d" + i, e = "__e" + i;
            decl.append("\t\t").append(defType).append(' ').append(d).append(" = null;\n");
            decl.append("\t\tcom.star4droid.template.Utils.PlayerItem ").append(e).append(" = null;\n");
            body.append("\t\t").append(d).append(" = new ").append(defType).append("();\n");
            for (Map.Entry<String, JsonElement> p : n.props.entrySet()) {
                String key = p.getKey();
                if (SKIP.contains(key) || (is3d && key.equals("z"))) continue;
                String val = str(p.getValue());
                if (val == null) continue;
                Field f;
                try {
                    f = cls.getField(key.replace(" ", "_"));
                } catch (NoSuchFieldException nsf) {
                    continue;
                }
                if (Modifier.isFinal(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) continue;
                String ft = f.getType().getName();
                try {
                    if (ft.equals("float")) {
                        float v = Float.parseFloat(val);
                        if (f.getFloat(ins) == v) continue;
                        body.append("\t\t").append(d).append('.').append(f.getName()).append(" = ").append(flt(v)).append(";\n");
                    } else if (ft.equals("boolean")) {
                        boolean v = val.equals("true");
                        if (f.getBoolean(ins) == v) continue;
                        body.append("\t\t").append(d).append('.').append(f.getName()).append(" = ").append(v).append(";\n");
                    } else if (ft.equals("java.lang.String")) {
                        Object def = f.get(ins);
                        if (def != null && def.toString().equals(val)) continue;
                        body.append("\t\t").append(d).append('.').append(f.getName()).append(" = ").append(ValueEmitCompat.quote(val)).append(";\n");
                    }
                } catch (Exception ignored) {
                    // a value that does not fit its field (hand edit): left at its default
                }
            }
            // name and place: the root is NAME at x / y (/ z), the others NAME_child at their offset from the root
            boolean root = PrefabModel.ROOT.equals(n.pid) || i == 0;
            body.append("\t\t").append(d).append(".name = ").append(root ? "__name" : "__name + " + ValueEmitCompat.quote("_" + n.name)).append(";\n");
            body.append("\t\t").append(d).append(".x = ").append(flt(num(n.props.get("x")))).append(" + __x;\n");
            body.append("\t\t").append(d).append(".y = ").append(flt(num(n.props.get("y")))).append(" + __y;\n");
            if (is3d) body.append("\t\t").append(d).append(".z = ").append(flt(num(n.props.get("z")))).append(" + __z;\n");
            String events = events(n);
            if (!events.isEmpty())
                body.append("\t\t").append(d).append(".elementEvents = new ElementEventAdapter(").append(d).append(".name) {\n").append(events).append("\t\t};\n");
            body.append("\t\t").append(e).append(" = (com.star4droid.template.Utils.PlayerItem) (").append(d).append(".build(this));\n");
            if (!NO_ZINDEX.contains(type)) body.append("\t\t").append(e).append(".getActor().setZIndex((int) (").append(d).append(".z));\n");
            if (root) rootVar = e;
            // behaviors of the element (their own event code lives with the scene's elements, so none here)
            try {
                String bhv = com.star4droid.star2d.behaviors.BehaviorCodeGen.forItem(env, e, str(n.props.get("behaviors")), null, null);
                if (bhv != null && !bhv.isEmpty()) after.append(bhv);
            } catch (Exception ex) {
                after.append("// behaviors of ").append(n.name).append(": ").append(ex).append('\n');
            }
            Integer pi = n.parentPid == null ? null : index.get(n.parentPid);
            if (pi != null && pi != i) after.append("\t\tif (__e").append(pi).append(" != null) __e").append(pi).append(".addChild(").append(e).append(");\n");
        }
        return "\n\t/** Prefab " + path.replace("*/", "* /") + " */\n"
                + "\tprivate com.star4droid.template.Utils.PlayerItem " + method + "(String __name, float __x, float __y, float __z) {\n"
                + decl + body + after
                + "\t\treturn " + (rootVar == null ? "null" : rootVar) + ";\n\t}\n";
    }

    /** The overridden event methods of a prefab element (only events with code), like the scene's elements. */
    private static String events(PrefabModel.Node n) {
        String[] names = {"onClick", "onTouchStart", "onTouchEnd", "OnBodyCreated", "OnBodyUpdate", "onBodyCollided", "onBodyCollideEnd"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            Map<String, String> files = n.events == null ? null : n.events.get(names[i]);
            if (files == null) continue;
            String code = files.get("java");
            if (code == null || code.trim().isEmpty()) code = files.get("code");
            if (code == null || code.trim().isEmpty()) continue;
            sb.append("\t\t\t@Override public void ").append(CodeGenerator.EVENT_SIGNATURES[i]).append(" {\n").append(code).append("\n\t\t\t}\n");
        }
        return sb.toString();
    }

    private static String str(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive()) return e.getAsString();
        return e.toString();
    }

    private static float num(JsonElement e) {
        try {
            String s = str(e);
            return s == null ? 0 : Float.parseFloat(s);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String flt(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return "0f";
        return Float.toString(v) + "f";
    }

    /** Java string literals. */
    static final class ValueEmitCompat {
        static String quote(String s) {
            return com.star4droid.star2d.behaviors.ValueEmit.javaLiteral(s);
        }
    }
}
