package com.star4droid.star2d.ui.blocks;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.star4droid.star2d.blocks.BehaviorBlocks;
import com.star4droid.star2d.blocks.SceneDims;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * All blocks shown in the palette: the original catalog (assets/java/nodes.java), the user's
 * custom nodes (Nodes/nodes.json), and the extra built-in blocks below. Reporter blocks
 * (values inside fields) come from the old parameter dialog, Nodes/blocks.json and extras.
 */
public final class BlockCatalog {
    private BlockCatalog() {}

    /** A statement block template. */
    public static boolean isExplain(String title){ return "explain".equals(title) || "explainIf".equals(title) || "explainIfElse".equals(title); }

    public static class Def {
        public String category, title, code;
        public String[] fields;
        public String[] defaults;
        public boolean isIf;
        public int color;
        /** Behavior ids: shown only while the scene uses one of them (null = always shown). */
        public String[] showWhen;
        /** For which scenes: {@link SceneDims#D2}, {@link SceneDims#D3} or {@link SceneDims#BOTH} (0 = decided when added). */
        public int dims;

        public BlockModel.Node create() {
            BlockModel.Node n = new BlockModel.Node();
            n.title = title;
            n.code = code;
            n.isIf = isIf;
            n.color = -1;
            for (int i = 0; i < fields.length; i++)
                n.fields.add(new BlockModel.Field(fields[i], defaults != null && i < defaults.length ? defaults[i] : ""));
            return n;
        }
    }

    /** A reporter (value) block template. */
    public static class Reporter {
        public String group, display, template;
        /** Behavior ids: shown only while the scene uses one of them (null = always shown). */
        public String[] showWhen;
        /** For which scenes (see {@link Def#dims}). */
        public int dims = SceneDims.BOTH;
        /** Kinds of the ___ slots when known: e element, n number, t text, b condition (null = unknown). */
        public String slots;
        Reporter(String g, String d, String t) { group = g; display = d; template = t; }
    }

    public static final LinkedHashMap<String, ArrayList<Def>> CATEGORIES = new LinkedHashMap<>();
    public static final ArrayList<Reporter> REPORTERS = new ArrayList<>();
    private static final HashMap<String, Integer> CATEGORY_COLOR = new HashMap<>();
    /** Value-block group colors of the built-in 3D groups. */
    private static final HashMap<String, Integer> GROUP_COLOR = new HashMap<>();
    /** Field type (the part in brackets: "mode(followMode)") -> the values offered as chips. */
    private static final HashMap<String, String[]> CHOICES = new HashMap<>();
    private static boolean loaded = false;

    /** Values offered for a field type (null = none). */
    public static String[] choices(String type) {
        return type == null ? null : CHOICES.get(type);
    }

    public static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        CATEGORIES.clear();
        REPORTERS.clear();
        GROUP_OF.clear();
        TEMPLATE_COLOR.clear();
        CATEGORY_COLOR.clear();
        SHAPE_OF.clear();
        GEN_TEMPLATES.clear();
        BlockInfo.clearDescriptions();
        try (InputStream in = c.getAssets().open("java/nodes.java")) {
            byte[] b = readAll(in);
            parseDsl(new String(b, StandardCharsets.UTF_8), null);
        } catch (Exception ignored) {}
        overrideOriginals();
        addExtras();
        add3D();
        // user custom nodes / blocks (same files as the LibGDX node library)
        File base = c.getExternalFilesDir(null);
        if (base != null) {
            loadCustomNodes(new File(base, "Nodes/nodes.json"));
            loadCustomBlocks(new File(base, "Nodes/blocks.json"));
        }
        addReporters();
        // 3D value blocks after the shared ones (the value groups keep their order)
        REPORTERS.addAll(PENDING_3D);
        PENDING_3D.clear();
        applyBehaviorBlocks(behaviorProject, true);
    }

    /** Reload user libraries (after editing them). */
    public static synchronized void reload(Context c) {
        loaded = false;
        load(c);
        version++;
    }

    private static int version = 0;

    /** Changes each time the user libraries are reloaded. */
    public static int version() { return version; }

    public static int colorOf(String category) {
        Integer col = CATEGORY_COLOR.get(category);
        if (col != null) return col;
        return 0xFF607D8B;
    }

    /** Category of a block title (for colors of loaded nodes). */
    public static String categoryOfTitle(String title) {
        for (java.util.Map.Entry<String, ArrayList<Def>> e : CATEGORIES.entrySet())
            for (Def d : e.getValue()) if (d.title.equals(title)) return e.getKey();
        return null;
    }

    public static Def find(String title) {
        for (ArrayList<Def> l : CATEGORIES.values()) for (Def d : l) if (d.title.equals(title)) return d;
        return null;
    }

    // ------------------------------------------------------------------ original DSL

    private static void parseDsl(String text, String forcedCategory) {
        String category = forcedCategory == null ? "main" : forcedCategory;
        String[] chunks = text.split("(?m)^\\s*split\\s*$");
        for (String chunk : chunks) {
            String[] lines = chunk.split("\n", -1);
            int color = 0;
            boolean hasColor = false;
            int i = 0;
            // leading lines: category markers and color
            for (; i < lines.length; i++) {
                String l = lines[i].trim();
                if (l.isEmpty()) continue;
                if (l.startsWith("--")) { category = l.substring(2).trim(); continue; }
                if (l.startsWith("-color:")) {
                    try {
                        String hex = l.substring(l.indexOf('#') + 1, l.indexOf('•'));
                        color = 0xFF000000 | Integer.parseInt(hex, 16);
                        hasColor = true;
                    } catch (Exception ignored) {}
                    continue;
                }
                break;
            }
            if (!hasColor || i >= lines.length) continue;
            String header = lines[i].trim();
            int sep = -1;
            for (int k = i + 1; k < lines.length; k++) if (lines[k].trim().equals("<<=>>")) { sep = k; break; }
            if (sep < 0 || header.isEmpty()) continue;
            StringBuilder code = new StringBuilder();
            for (int k = sep + 1; k < lines.length; k++) {
                code.append(lines[k]);
                if (k < lines.length - 1) code.append("\n");
            }
            String codeStr = code.toString();
            // strip trailing empty line produced by the split
            while (codeStr.endsWith("\n")) codeStr = codeStr.substring(0, codeStr.length() - 1);
            String[] tokens = header.split("\\s+");
            Def d = new Def();
            d.category = category;
            d.isIf = tokens[0].contains("__star__if__");
            d.title = tokens[0].replace("__star__if__", "");
            d.fields = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, d.fields, 0, d.fields.length);
            d.code = codeStr;
            d.color = color;
            add(d);
        }
    }

    private static void add(Def d) {
        if (d.dims == 0) d.dims = defaultDims(d.category, d.title);
        ArrayList<Def> l = CATEGORIES.get(d.category);
        if (l == null) {
            l = new ArrayList<>();
            CATEGORIES.put(d.category, l);
            CATEGORY_COLOR.put(d.category, d.color != 0 ? d.color : 0xFF607D8B);
        }
        // a later definition with the same title replaces the earlier one (no duplicates in palette)
        for (int i = 0; i < l.size(); i++) if (l.get(i).title.equals(d.title)) { l.set(i, d); return; }
        l.add(d);
    }

    private static void def(String cat, int color, String title, String fields, String defaults, boolean isIf, String code) {
        Def d = new Def();
        d.category = cat;
        d.color = color;
        d.title = title;
        d.fields = fields.isEmpty() ? new String[0] : fields.split(" ");
        d.defaults = defaults == null || defaults.isEmpty() ? null : defaults.split("\\|", -1);
        d.isIf = isIf;
        d.code = code;
        add(d);
    }

    /** Find an original block by title and change it (new blocks only; saved blocks keep their own code). */
    private static void override(String title, String fields, boolean isIf, String code) {
        Def d = find(title);
        if (d == null) return;
        d.fields = fields.isEmpty() ? new String[0] : fields.split(" ");
        d.isIf = isIf;
        d.code = code;
    }

    /** try / timers / functions become C-blocks: their body goes inside, the next blocks run after. */
    private static void overrideOriginals() {
        override("try", "", true, "try {\n%1$s\n} catch(Throwable e){\n%2$s\n}\n%3$s");
        override("void", "name", true, "public void %1$s {\n%2$s\n}\n%4$s");
        override("_DoAfter", "delay name", true,
                "Timer.Task %2$s = Timer.schedule(new Timer.Task(){\n    public void run(){\n%3$s\n    }\n},(float)(%1$s));\n%5$s");
        override("_repeatEvery", "delay name", true,
                "Timer.Task %2$s = Timer.schedule(new Timer.Task(){\n    public void run(){\n%3$s\n    }\n},(float)(%1$s),(float)(%1$s));\n%5$s");
        override("cancelTimer", "name", false, "%1$s.cancel();\n%2$s");
    }

    // ------------------------------------------------------------------ value block shapes / colors

    private static final HashMap<String, String> GROUP_OF = new HashMap<>();

    /** Shape chosen explicitly for a value template: 'b' condition, 't' text, 'n' number / element. */
    private static final HashMap<String, Character> SHAPE_OF = new HashMap<>();

    public static boolean isBooleanTemplate(String t) {
        if (t == null) return false;
        Character k = SHAPE_OF.get(t);
        if (k != null) return k == 'b';
        return t.contains("==") || t.contains("!=") || t.contains(" < ") || t.contains(" > ") || t.contains("<=") || t.contains(">=")
                || t.contains("&&") || t.contains("||") || t.startsWith("(!(") || t.contains("checkCollision") || t.contains("isTouched")
                || t.contains("isVisible()") || t.contains(".equals(") || t.equals("true") || t.equals("false");
    }

    public static boolean isTextTemplate(String t) {
        if (t == null || t.endsWith(".length()")) return false;
        Character k = SHAPE_OF.get(t);
        if (k != null) return k == 't';
        return t.startsWith("String.valueOf(") || t.contains("getName()") || t.startsWith("getValue(") || t.startsWith("(String.valueOf(") || t.startsWith("\"");
    }

    public static int groupColor(String g) {
        if (g == null) return 0xFF00838F;
        if (g.startsWith("★")) return 0xFF6D4C41;
        if (g.startsWith(BehaviorBlocks.CATEGORY_PREFIX)) return behaviorColor(g);
        Integer gc = GROUP_COLOR.get(g);
        if (gc != null) return gc;
        switch (g) {
            case "Operators": return 0xFF43A047;
            case "Math": return 0xFF2E7D32;
            case "Logic": return 0xFF3F51B5;
            case "Convert": return 0xFF8D6E63;
            case "Text": return 0xFF8E24AA;
            case "Elements":
            case "PlayerItem": return 0xFF1E88E5;
            case "Box2D Body": return 0xFF00897B;
            case "Stage / Scene": return 0xFF546E7A;
            case "Joystick": return 0xFF7B1FA2;
            case "Input": return 0xFF3949AB;
            case "Data": return 0xFFEF6C00;
            case "Variables": return 0xFFE65100;
        }
        return 0xFF00838F;
    }

    public static String groupOf(String template) {
        return GROUP_OF.get(template);
    }

    private static final HashMap<String, Integer> TEMPLATE_COLOR = new HashMap<>();

    public static int reporterColor(String template) {
        Integer c = TEMPLATE_COLOR.get(template);
        if (c != null) return c;
        return groupColor(GROUP_OF.get(template));
    }

    // ------------------------------------------------------------------ extra built-in blocks

    private static void addExtras() {
        int ctrl = 0xFFE6A100, vars = 0xFFE65100, motion = 0xFF2E7D32, looks = 0xFF7B1FA2, input = 0xFF1565C0, phys = 0xFF00838F, dbg = 0xFF6D4C41;
        CATEGORY_COLOR.put("main", 0xFF018253);

        // Control
        def("Control", ctrl, "repeatTimes", "times var", "10|i", true,
                "for(int %2$s = 0; %2$s < (int)(%1$s); %2$s++){\n%3$s\n}\n%5$s");
        def("Control", ctrl, "ifThen", "boolean(Boolean)", "", true, "if(%1$s){\n%2$s\n}\n%4$s");
        def("Control", ctrl, "breakLoop", "", "", false, "break;\n%1$s");
        def("Control", ctrl, "stopEvent", "", "", false, "if(true) return;\n%1$s");
        def("Control", ctrl, "waitSeconds", "seconds", "1", true,
                "Timer.schedule(new Timer.Task(){\n    public void run(){\n        %2$s\n    }\n},(float)(%1$s));\n%4$s");
        def("Control", ctrl, "everySeconds", "seconds", "1", true,
                "Timer.schedule(new Timer.Task(){\n    public void run(){\n        %2$s\n    }\n},(float)(%1$s),(float)(%1$s));\n%4$s");
        def("Control", ctrl, "comment", "text", "note", false, "// %1$s\n%2$s");
        // explanation blocks: no code of their own, the (i) button shows the instructions the user wrote in them
        def("Control", ctrl, "explain", "note", "", false, "%2$s");
        def("Control", ctrl, "explainIf", "note", "", true, "{\n%2$s\n}\n%4$s");
        def("Control", ctrl, "explainIfElse", "note", "", true, "{\n%2$s\n}\nif(false){\n%3$s\n}\n%4$s");
        def("Control", ctrl, "codeLine", "code", "", false, "%1$s\n%2$s");

        // Variables & data
        def("Variables", vars, "setVariable", "variable value", "score|0", false, "%1$s = %2$s;\n%3$s");
        def("Variables", vars, "changeVariable", "variable by", "|1", false, "%1$s += %2$s;\n%3$s");
        def("Variables", vars, "newVariable", "type name value", "int|count|0", false, "%1$s %2$s = %3$s;\n%4$s");
        // object variables (docs/SCRIPTVARS.md): declared by a marker the code generator turns into a field of the element's event class
        def("Variables", vars, "objectVariable", "type name value", "int|health|0", false, "/*@objvar %1$s|%2$s|%3$s*/\n%4$s");
        def("Variables", vars, "setObjectVariable", "element(Body) variable value", "body2|health|0", false, "com.star4droid.template.Utils.ObjectVars.set(%1$s,\"%2$s\",%3$s);\n%4$s");
        def("Variables", vars, "changeObjectVariable", "element(Body) variable by", "body2|health|-1", false, "com.star4droid.template.Utils.ObjectVars.add(%1$s,\"%2$s\",%3$s);\n%4$s");
        def("Variables", vars, "saveData", "key value", "best|score", false, "setValue(\"%1$s\",String.valueOf(%2$s));\n%3$s");

        // Motion (distances in editor pixels, speeds in pixels per second)
        String ws = "com.star4droid.template.Items.StageImp.WORLD_SCALE";
        def("Motion", motion, "moveBy", "body(Body) dx dy", "current|10|0", false,
                "%1$s.getBody().setTransform(%1$s.getBody().getPosition().x+(float)(%2$s)*" + ws + ",%1$s.getBody().getPosition().y+(float)(%3$s)*" + ws + ",%1$s.getBody().getAngle());\n%4$s");
        def("Motion", motion, "goTo", "body(Body) x y", "current|0|0", false,
                "%1$s.getBody().setTransform((float)(%2$s)*" + ws + ",(float)(%3$s)*" + ws + ",%1$s.getBody().getAngle());\n%4$s");
        def("Motion", motion, "goToBody", "body(Body) target(Body)", "current|", false,
                "%1$s.getBody().setTransform(%2$s.getBody().getPosition(),%1$s.getBody().getAngle());\n%3$s");
        def("Motion", motion, "setVelocityX", "body(Body) speed", "current|200", false,
                "%1$s.getBody().setLinearVelocity((float)(%2$s)*" + ws + ",%1$s.getBody().getLinearVelocity().y);\n%3$s");
        def("Motion", motion, "setVelocityY", "body(Body) speed", "current|200", false,
                "%1$s.getBody().setLinearVelocity(%1$s.getBody().getLinearVelocity().x,(float)(%2$s)*" + ws + ");\n%3$s");
        def("Motion", motion, "stopMoving", "body(Body)", "current", false,
                "%1$s.getBody().setLinearVelocity(0,0);\n%1$s.getBody().setAngularVelocity(0);\n%2$s");
        def("Motion", motion, "jump", "body(Body) power", "current|400", false,
                "%1$s.getBody().setLinearVelocity(%1$s.getBody().getLinearVelocity().x,(float)(%2$s)*" + ws + ");\n%3$s");
        def("Motion", motion, "rotateBy", "body(Body) degrees", "current|15", false,
                "%1$s.getBody().setTransform(%1$s.getBody().getPosition(),%1$s.getBody().getAngle()+(float)Math.toRadians(%2$s));\n%3$s");
        def("Motion", motion, "lookAt", "body(Body) target(Body)", "current|", false,
                "%1$s.getBody().setTransform(%1$s.getBody().getPosition(),%2$s.angleTo(%1$s));\n%3$s");
        def("Motion", motion, "moveToward", "body(Body) target(Body) speed", "current||150", false,
                "{\n    float __a = %2$s.angleTo(%1$s);\n    %1$s.getBody().setLinearVelocity((float)Math.cos(__a)*(float)(%3$s)*" + ws + ",(float)Math.sin(__a)*(float)(%3$s)*" + ws + ");\n}\n%4$s");
        def("Motion", motion, "moveWithJoystick", "body(Body) joystick(Body) speed", "current||300", false,
                "%1$s.getBody().setLinearVelocity(%2$s.getJoyStickX()*(float)(%3$s)*" + ws + ",%2$s.getJoyStickY()*(float)(%3$s)*" + ws + ");\n%4$s");
        def("Motion", motion, "walkWithJoystick", "body(Body) joystick(Body) speed", "current||300", false,
                "%1$s.getBody().setLinearVelocity(%2$s.getJoyStickX()*(float)(%3$s)*" + ws + ",%1$s.getBody().getLinearVelocity().y);\n%4$s");

        // Looks
        def("Looks", looks, "show", "body(Body)", "", false, "%1$s.getActor().setVisible(true);\n%2$s");
        def("Looks", looks, "hide", "body(Body)", "", false, "%1$s.getActor().setVisible(false);\n%2$s");
        def("Looks", looks, "setTint", "body(Body) color", "|FFFFFFFF", false,
                "%1$s.getActor().setColor(com.badlogic.gdx.graphics.Color.valueOf(\"%2$s\"));\n%3$s");
        def("Looks", looks, "setOpacity", "body(Body) alpha", "|0.5", false,
                "%1$s.getActor().getColor().a = (float)(%2$s);\n%3$s");
        def("Looks", looks, "flipX", "body(Body)", "", false, "%1$s.getActor().setScaleX(-%1$s.getActor().getScaleX());\n%2$s");
        def("Looks", looks, "bringToFront", "body(Body)", "", false, "%1$s.getActor().toFront();\n%2$s");
        def("Looks", looks, "sendToBack", "body(Body)", "", false, "%1$s.getActor().toBack();\n%2$s");
        def("Looks", looks, "setAnimation", "body(Body) name", "|\"idle\"", false, "%1$s.setAnimation(String.valueOf(%2$s));\n%3$s");
        def("Looks", looks, "removeAnimation", "body(Body)", "", false, "%1$s.removeAnimation();\n%2$s");
        def("Looks", looks, "showText", "ui(Body) text", "|\"Hello\"", false, "%1$s.setItemText(String.valueOf(%2$s));\n%3$s");

        // Input
        def("Input", input, "ifTouching", "", "", true, "if(Gdx.input.isTouched()){\n%1$s\n} else {\n%2$s\n}\n%3$s");
        def("Input", input, "ifJoystickMoved", "joystick(Body)", "", true,
                "if(%1$s.getJoyStickX()!=0 || %1$s.getJoyStickY()!=0){\n%2$s\n} else {\n%3$s\n}\n%4$s");

        // Physics extras
        def("Physics", phys, "setBodyType", "body(Body) type", "|DynamicBody", false,
                "%1$s.getBody().setType(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.%2$s);\n%3$s");
        def("Physics", phys, "setGravityScaleOf", "body(Body) scale", "|1", false, "%1$s.getBody().setGravityScale((float)(%2$s));\n%3$s");
        def("Physics", phys, "destroyAfter", "body(Body) seconds", "|2", false,
                "Timer.schedule(new Timer.Task(){\n    public void run(){\n        %1$s.destroy();\n    }\n},(float)(%2$s));\n%3$s");
        def("Physics", phys, "spawnCopy", "body(Body) var newName x y", "|bullet|\"bullet\"|0|0", false,
                "PlayerItem %2$s = %1$s.getClone(%3$s);\n%2$s.getBody().setTransform((float)(%4$s)*" + ws + ",(float)(%5$s)*" + ws + ",0);\n%6$s");
        def("Physics", phys, "setWorldGravity", "strength", "600", false,
                "setSteps(1);\nsetGravity(0f,-(float)(%1$s)*" + ws + ");\n%2$s");

        // Behaviors (always shown): attach / detach / turn on-off / settings of a behavior of an element
        int beh = 0xFF6A1B9A;
        def("Behaviors", beh, "addBehavior", "body(Body) behavior(behavior)", "current|", false, "com.star4droid.template.behaviors.BehaviorOps.add(%1$s, \"%2$s\");\n%3$s");
        def("Behaviors", beh, "removeBehavior", "body(Body) behavior(behavior)", "current|", false, "com.star4droid.template.behaviors.BehaviorOps.remove(%1$s, \"%2$s\");\n%3$s");
        def("Behaviors", beh, "enableBehavior", "body(Body) behavior(behavior)", "current|", false, "com.star4droid.template.behaviors.BehaviorOps.setEnabled(%1$s, \"%2$s\", true);\n%3$s");
        def("Behaviors", beh, "disableBehavior", "body(Body) behavior(behavior)", "current|", false, "com.star4droid.template.behaviors.BehaviorOps.setEnabled(%1$s, \"%2$s\", false);\n%3$s");
        def("Behaviors", beh, "setBehaviorEnabled", "body(Body) behavior(behavior) on(Boolean)", "current||true", false, "com.star4droid.template.behaviors.BehaviorOps.setEnabled(%1$s, \"%2$s\", %3$s);\n%4$s");
        def("Behaviors", beh, "setBehaviorParam", "body(Body) behavior(behavior) setting value", "current|||0", false, "com.star4droid.template.behaviors.BehaviorOps.setParam(%1$s, \"%2$s\", \"%3$s\", %4$s);\n%5$s");

        // UI holders: change the inner elements of a .ui (by their id) and where the holder sits on the screen
        int uic = 0xFF88213B;
        def("Ui Nodes", uic, "uiSetText", "holder(Body) node text", "current||\"Hello\"", false, "UIHolder.ref(%1$s,\"%2$s\").setText(String.valueOf(%3$s));\n%4$s");
        def("Ui Nodes", uic, "uiSetValue", "holder(Body) node value", "current||50", false, "UIHolder.ref(%1$s,\"%2$s\").setValue((float)(%3$s));\n%4$s");
        def("Ui Nodes", uic, "uiSetChecked", "holder(Body) node on(Boolean)", "current||true", false, "UIHolder.ref(%1$s,\"%2$s\").setChecked(%3$s);\n%4$s");
        def("Ui Nodes", uic, "uiShow", "holder(Body) node", "current|", false, "UIHolder.ref(%1$s,\"%2$s\").setVisible(true);\n%3$s");
        def("Ui Nodes", uic, "uiHide", "holder(Body) node", "current|", false, "UIHolder.ref(%1$s,\"%2$s\").setVisible(false);\n%3$s");
        def("Ui Nodes", uic, "uiSetVisible", "holder(Body) node visible(Boolean)", "current||true", false, "UIHolder.ref(%1$s,\"%2$s\").setVisible(%3$s);\n%4$s");
        def("Ui Nodes", uic, "uiSetEnabled", "holder(Body) node on(Boolean)", "current||true", false, "UIHolder.ref(%1$s,\"%2$s\").setEnabled(%3$s);\n%4$s");
        def("Ui Nodes", uic, "uiSetAlpha", "holder(Body) node alpha", "current||0.5", false, "UIHolder.ref(%1$s,\"%2$s\").setAlpha((float)(%3$s));\n%4$s");
        def("Ui Nodes", uic, "uiSetImage", "holder(Body) node pic(image)", "current||", false, "UIHolder.ref(%1$s,\"%2$s\").setImage(\"%3$s\");\n%4$s");
        def("Ui Nodes", uic, "uiSetColor", "holder(Body) node color", "current||#FFFFFF", false, "UIHolder.ref(%1$s,\"%2$s\").setColor(\"%3$s\");\n%4$s");
        def("Ui Nodes", uic, "uiSetProperty", "holder(Body) node property value", "current||fontSize|32", false, "UIHolder.ref(%1$s,\"%2$s\").set(\"%3$s\", String.valueOf(%4$s));\n%5$s");
        def("Ui Nodes", uic, "uiScrollTo", "holder(Body) node x y", "current||0|0", false, "UIHolder.ref(%1$s,\"%2$s\").scrollTo((float)(%3$s), (float)(%4$s));\n%5$s");
        def("Ui Nodes", uic, "uiClick", "holder(Body) node", "current|", false, "UIHolder.ref(%1$s,\"%2$s\").click();\n%3$s");
        def("Ui Nodes", uic, "uiOnClick", "holder(Body) node", "current|", true, "UIHolder.ref(%1$s,\"%2$s\").onClick(new Runnable(){\n    public void run(){\n%3$s\n    }\n});\n%5$s");
        def("Ui Nodes", uic, "uiSetUi", "holder(Body) name", "current|menu", false, "if(%1$s instanceof com.star4droid.template.Items.UIHolder) ((com.star4droid.template.Items.UIHolder)%1$s).setUi(\"%2$s\");\n%3$s");
        def("Ui Nodes", uic, "uiPlace", "holder(Body) anchor marginX marginY", "current|center|0|0", false, "if(%1$s instanceof com.star4droid.template.Items.UIHolder) ((com.star4droid.template.Items.UIHolder)%1$s).place(\"%2$s\", (float)(%3$s), (float)(%4$s));\n%5$s");
        def("Ui Nodes", uic, "uiSizePercent", "holder(Body) widthPct heightPct", "current|100|100", false, "if(%1$s instanceof com.star4droid.template.Items.UIHolder) ((com.star4droid.template.Items.UIHolder)%1$s).sizePercent((float)(%2$s), (float)(%3$s));\n%4$s");
        def("Ui Nodes", uic, "uiStretch", "holder(Body) stretchH(Boolean) stretchV(Boolean)", "current|true|true", false, "if(%1$s instanceof com.star4droid.template.Items.UIHolder) ((com.star4droid.template.Items.UIHolder)%1$s).stretch(%2$s, %3$s);\n%4$s");

        // Language (game translations: strings/strings.json)
        int lang = 0xFF00897B;
        String GS = "com.star4droid.template.Utils.GameStrings";
        def("Language", lang, "setLanguage", "code", "\"ar\"", false, GS + ".setLanguage(String.valueOf(%1$s));\n%2$s");
        def("Language", lang, "nextLanguage", "", "", false, GS + ".nextLanguage();\n%1$s");
        def("Language", lang, "showTranslated", "ui(Body) key", "|\"play\"", false, "%1$s.setItemText(" + GS + ".tr(%2$s));\n%3$s");
        def("Language", lang, "ifLanguage", "code", "\"ar\"", true, "if(" + GS + ".language().equals(String.valueOf(%1$s))){\n%2$s\n} else {\n%3$s\n}\n%4$s");

        // Debug
        def("Debug", dbg, "toastValue", "value", "", false, "toast(String.valueOf(%1$s));\n%2$s");
        def("Debug", dbg, "logValue", "value", "score", false, "debug(String.valueOf(%1$s));\n%2$s");
    }

    // ------------------------------------------------------------------ reporters

    private static final String WS = "com.star4droid.template.Items.StageImp.WORLD_SCALE";

    private static void addReporters() {
        // original parameter dialog groups (same templates)
        r("Operators", "___ + ___", "(%1$s + %2$s)");
        r("Operators", "___ - ___", "(%1$s - %2$s)");
        r("Operators", "___ * ___", "(%1$s * %2$s)");
        r("Operators", "___ / ___", "(%1$s / %2$s)");
        r("Operators", "___ == ___", "(%1$s == %2$s)");
        r("Operators", "___ != ___", "(%1$s != %2$s)");
        r("Operators", "___ < ___", "(%1$s < %2$s)");
        r("Operators", "___ > ___", "(%1$s > %2$s)");
        r("Operators", "___ <= ___", "(%1$s <= %2$s)");
        r("Operators", "___ >= ___", "(%1$s >= %2$s)");
        r("Convert", "to int ___", "((int)(%1$s))");
        r("Convert", "to float ___", "((float)(%1$s))");
        r("Convert", "to string ___", "String.valueOf(%1$s)");
        r("PlayerItem", "___ .getX()", "%1$s.getX()");
        r("PlayerItem", "___ .getY()", "%1$s.getY()");
        r("PlayerItem", "___ .getZIndex()", "%1$s.getZIndex()");
        r("PlayerItem", "___ .getBody()", "%1$s.getBody()");
        r("PlayerItem", "___ .getActor()", "%1$s.getActor()");
        r("Box2D Body", "___ .getLinearVelocity()", "%1$s.getLinearVelocity()");
        r("Box2D Body", "___ .getAngularVelocity()", "%1$s.getAngularVelocity()");
        r("Box2D Body", "___ .getPosition()", "%1$s.getPosition()");
        r("Box2D Body", "___ .getMass()", "%1$s.getMass()");
        r("Stage / Scene", "getWidth()", "getWidth()");
        r("Stage / Scene", "getHeight()", "getHeight()");
        r("Stage / Scene", "getRoot()", "getRoot()");
        r("Stage / Scene", "getActors()", "getActors()");
        r("Stage / Scene", "getCamera()", "getCamera()");
        // new
        r("Logic", "___ and ___", "(%1$s && %2$s)");
        r("Logic", "___ or ___", "(%1$s || %2$s)");
        r("Logic", "not ___", "(!(%1$s))");
        r("Logic", "___ touches ___", "checkCollision(%1$s,%2$s)");
        r("Logic", "screen is touched", "Gdx.input.isTouched()");
        r("Language", "translate ___", "com.star4droid.template.Utils.GameStrings.tr(%1$s)");
        r("Language", "current language", "com.star4droid.template.Utils.GameStrings.language()");
        r("Math", "random ___ to ___", "random((int)(%1$s),(int)(%2$s))");
        r("Math", "___ mod ___", "(%1$s %% %2$s)");
        r("Math", "abs ___", "Math.abs(%1$s)");
        r("Math", "min ___ , ___", "Math.min(%1$s,%2$s)");
        r("Math", "max ___ , ___", "Math.max(%1$s,%2$s)");
        r("Math", "round ___", "Math.round(%1$s)");
        r("Math", "floor ___", "(float)Math.floor(%1$s)");
        r("Math", "sqrt ___", "(float)Math.sqrt(%1$s)");
        r("Math", "___ ^ ___", "(float)Math.pow(%1$s,%2$s)");
        r("Math", "sin ___°", "(float)Math.sin(Math.toRadians(%1$s))");
        r("Math", "cos ___°", "(float)Math.cos(Math.toRadians(%1$s))");
        r("Text", "join ___ ___", "(String.valueOf(%1$s) + String.valueOf(%2$s))");
        r("Text", "length of ___", "String.valueOf(%1$s).length()");
        r("Elements", "x of ___", "(%1$s.getBodyX()/" + WS + ")");
        r("Elements", "y of ___", "(%1$s.getBodyY()/" + WS + ")");
        r("Elements", "speed x of ___", "(%1$s.getBody().getLinearVelocity().x/" + WS + ")");
        r("Elements", "speed y of ___", "(%1$s.getBody().getLinearVelocity().y/" + WS + ")");
        r("Elements", "angle of ___", "(float)Math.toDegrees(%1$s.getBody().getAngle())");
        r("Elements", "distance ___ to ___", "(%1$s.distTo(%2$s)/" + WS + ")");
        r("Elements", "angle ___ to ___", "%1$s.angleTo(%2$s)");
        r("Elements", "name of ___", "%1$s.getName()");
        r("Elements", "width of ___", "%1$s.getActor().getWidth()");
        r("Elements", "height of ___", "%1$s.getActor().getHeight()");
        r("Elements", "is visible ___", "%1$s.getActor().isVisible()");
        r("Joystick", "joystick x ___", "%1$s.getJoyStickX()");
        r("Joystick", "joystick y ___", "%1$s.getJoyStickY()");
        r("Joystick", "power ___", "%1$s.getPower()");
        r("Joystick", "angle (deg) ___", "%1$s.getAngleDegrees()");
        r("Input", "touch x", "(getTouch().x/" + WS + ")");
        r("Input", "touch y", "(getTouch().y/" + WS + ")");
        r("Input", "frame time", "getDelta()");
        r("Input", "camera x", "(getCameraX()/" + WS + ")");
        r("Input", "camera y", "(getCameraY()/" + WS + ")");
        r("Data", "saved text ___", "getValue(\"%1$s\")");
        r("Data", "saved number ___", "getNumber(\"%1$s\")");
        r("Data", "number variable ___ of ___", "com.star4droid.template.Utils.ObjectVars.getFloat(%2$s,\"%1$s\")");
        shape("com.star4droid.template.Utils.ObjectVars.getFloat(%2$s,\"%1$s\")", 'n');
        r("Data", "text variable ___ of ___", "com.star4droid.template.Utils.ObjectVars.getString(%2$s,\"%1$s\")");
        shape("com.star4droid.template.Utils.ObjectVars.getString(%2$s,\"%1$s\")", 't');
        r("Data", "yes/no variable ___ of ___", "com.star4droid.template.Utils.ObjectVars.getBool(%2$s,\"%1$s\")");
        shape("com.star4droid.template.Utils.ObjectVars.getBool(%2$s,\"%1$s\")", 'b');
        r("Ui Nodes", "text of node ___ in ___", "UIHolder.ref(%2$s,\"%1$s\").getText()");
        shape("UIHolder.ref(%2$s,\"%1$s\").getText()", 't');
        r("Ui Nodes", "value of node ___ in ___", "UIHolder.ref(%2$s,\"%1$s\").getValue()");
        shape("UIHolder.ref(%2$s,\"%1$s\").getValue()", 'n');
        r("Ui Nodes", "node ___ in ___ is checked", "UIHolder.ref(%2$s,\"%1$s\").isChecked()");
        shape("UIHolder.ref(%2$s,\"%1$s\").isChecked()", 'b');
        r("Ui Nodes", "node ___ in ___ is visible", "UIHolder.ref(%2$s,\"%1$s\").isVisible()");
        shape("UIHolder.ref(%2$s,\"%1$s\").isVisible()", 'b');
        r("Ui Nodes", "node ___ in ___ is enabled", "UIHolder.ref(%2$s,\"%1$s\").isEnabled()");
        shape("UIHolder.ref(%2$s,\"%1$s\").isEnabled()", 'b');
        // behaviors
        r("Behaviors", "___ has behavior ___", "com.star4droid.template.behaviors.BehaviorOps.has(%1$s, \"%2$s\")");
        shape("com.star4droid.template.behaviors.BehaviorOps.has(%1$s, \"%2$s\")", 'b');
        r("Behaviors", "___ has behavior ___ turned on", "com.star4droid.template.behaviors.BehaviorOps.enabled(%1$s, \"%2$s\")");
        shape("com.star4droid.template.behaviors.BehaviorOps.enabled(%1$s, \"%2$s\")", 'b');
        r("Behaviors", "number setting ___ of behavior ___ on ___", "com.star4droid.template.behaviors.BehaviorOps.num(com.star4droid.template.behaviors.BehaviorOps.param(%3$s, \"%2$s\", \"%1$s\"))");
        shape("com.star4droid.template.behaviors.BehaviorOps.num(com.star4droid.template.behaviors.BehaviorOps.param(%3$s, \"%2$s\", \"%1$s\"))", 'n');
        r("Behaviors", "text setting ___ of behavior ___ on ___", "com.star4droid.template.behaviors.BehaviorOps.text(com.star4droid.template.behaviors.BehaviorOps.param(%3$s, \"%2$s\", \"%1$s\"))");
        shape("com.star4droid.template.behaviors.BehaviorOps.text(com.star4droid.template.behaviors.BehaviorOps.param(%3$s, \"%2$s\", \"%1$s\"))", 't');
        r("Behaviors", "yes/no setting ___ of behavior ___ on ___", "com.star4droid.template.behaviors.BehaviorOps.bool(com.star4droid.template.behaviors.BehaviorOps.param(%3$s, \"%2$s\", \"%1$s\"))");
        shape("com.star4droid.template.behaviors.BehaviorOps.bool(com.star4droid.template.behaviors.BehaviorOps.param(%3$s, \"%2$s\", \"%1$s\"))", 'b');
        r("Behaviors", "behavior ___ of ___", "com.star4droid.template.behaviors.BehaviorOps.of(%2$s, \"%1$s\")");
        r("Logic", "true", "true");
        r("Logic", "___ is named ___", "%1$s.getName().equals(\"%2$s\")");
        r("Logic", "___ equals ___", "String.valueOf(%1$s).equals(String.valueOf(%2$s))");
        r("Logic", "false", "false");
        r("Text", "\"___\"", "\"%1$s\"");
    }

    private static void shape(String template, char k) { SHAPE_OF.put(template, k); }

    private static Reporter r(String g, String d, String t) {
        Reporter rep = new Reporter(g, d, t);
        rep.dims = defaultReporterDims(g, d);
        REPORTERS.add(rep);
        if (!GROUP_OF.containsKey(t)) GROUP_OF.put(t, g);
        return rep;
    }

    // ------------------------------------------------------------------ user libraries

    private static void loadCustomNodes(File f) {
        if (!f.exists()) return;
        try {
            JsonArray sections = JsonParser.parseString(BlockModel.read(f)).getAsJsonArray();
            for (JsonElement se : sections) {
                JsonObject s = se.getAsJsonObject();
                String cat = "★ " + BlockModel.str(s, "name", "Custom");
                int secDims = SceneDims.of(s, SceneDims.BOTH);
                if (!s.has("nodes")) continue;
                for (JsonElement ne : s.getAsJsonArray("nodes")) {
                    JsonObject n = ne.getAsJsonObject();
                    Def d = new Def();
                    d.category = cat;
                    d.color = 0xFF5D4037;
                    try { if (n.has("color")) d.color = n.get("color").getAsInt(); } catch (Exception ignored) {}
                    String title = BlockModel.str(n, "title", "");
                    d.title = title.isEmpty() ? BlockModel.str(n, "name", "custom") : title;
                    d.code = BlockModel.str(n, "code", "%1$s");
                    try { d.isIf = n.get("isBooleanNode").getAsBoolean(); } catch (Exception e) { d.isIf = false; }
                    ArrayList<String> fs = new ArrayList<>(), dv = new ArrayList<>();
                    if (n.has("fields"))
                        for (JsonElement fe : n.getAsJsonArray("fields")) {
                            fs.add(BlockModel.str(fe.getAsJsonObject(), "name", "value"));
                            dv.add(BlockModel.str(fe.getAsJsonObject(), "value", ""));
                        }
                    d.fields = fs.toArray(new String[0]);
                    d.defaults = dv.toArray(new String[0]);
                    d.dims = SceneDims.of(n, secDims);
                    add(d);
                    if (n.has("desc")) BlockInfo.setDescription(d.title, parseDesc(n.get("desc")));
                    if (n.has("labels")) BlockInfo.setLabels(d.title, parseDesc(n.get("labels")));
                    loadFieldLabels(n);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void loadCustomBlocks(File f) {
        if (!f.exists()) return;
        try {
            JsonArray sections = JsonParser.parseString(BlockModel.read(f)).getAsJsonArray();
            for (JsonElement se : sections) {
                JsonObject s = se.getAsJsonObject();
                String g = "★ " + BlockModel.str(s, "name", "Custom");
                int secDims = SceneDims.of(s, SceneDims.BOTH);
                if (!s.has("blocks")) continue;
                for (JsonElement be : s.getAsJsonArray("blocks")) {
                    JsonObject b = be.getAsJsonObject();
                    String code = BlockModel.str(b, "code", "%1$s");
                    r(g, BlockModel.str(b, "title", "___"), code).dims = SceneDims.of(b, secDims);
                    if (b.has("desc")) BlockInfo.setDescription(BlockModel.str(b, "title", "___"), parseDesc(b.get("desc")));
                    if (b.has("labels")) BlockInfo.setLabels(BlockModel.str(b, "title", "___"), parseDesc(b.get("labels")));
                    try { if (b.has("color")) TEMPLATE_COLOR.put(code, b.get("color").getAsInt()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    /** Description stored in a custom block: {"en":"..","ar":".."} (a plain string counts as English). Never null. */
    /** "fieldLabels": {"field": {"lang": "text"}} of a custom block. */
    static void loadFieldLabels(JsonObject n) {
        try {
            if (!n.has("fieldLabels")) return;
            JsonObject o = n.getAsJsonObject("fieldLabels");
            for (java.util.Map.Entry<String, JsonElement> e : o.entrySet())
                BlockInfo.setFieldLabels(e.getKey(), parseDesc(e.getValue()));
        } catch (Exception ignored) {}
    }

    public static java.util.Map<String, String> parseDesc(JsonElement e) {
        return com.star4droid.star2d.blocks.BlockDesc.parse(e);
    }

    // ------------------------------------------------------------------ behavior blocks (@Block methods)

    private static String behaviorProject = null;
    private static final ArrayList<String> GEN_TEMPLATES = new ArrayList<>();
    private static java.util.Set<String> activeBehaviors = null;

    private static final int[] BEHAVIOR_COLORS = {0xFF6A1B9A, 0xFF00695C, 0xFFAD1457, 0xFF283593, 0xFF4E342E, 0xFF558B2F, 0xFFEF6C00, 0xFF00838F};

    public static int behaviorColor(String category) {
        return BEHAVIOR_COLORS[(category.hashCode() & 0x7FFFFFFF) % BEHAVIOR_COLORS.length];
    }

    /** True for the generated behavior blocks (they come and go with the scene). */
    private static boolean generated(Def d) { return d.showWhen != null; }

    /**
     * (Re)builds the blocks of the {@code @Block} methods of the built-in behaviors and of the project's own
     * ({@code <project>/java/com/star4droid/Game/Behaviors/*.java}). They are only shown when {@link #isVisible} says the scene uses
     * the behavior. Call it again after the project's behavior sources changed.
     */
    public static synchronized void applyBehaviorBlocks(String projectPath) {
        applyBehaviorBlocks(projectPath, false);
    }

    private static String behaviorSignature = null;

    private static void applyBehaviorBlocks(String projectPath, boolean force) {
        behaviorProject = projectPath;
        String sig = signature(projectPath);
        if (!force && sig.equals(behaviorSignature)) return;
        behaviorSignature = sig;
        for (java.util.Iterator<java.util.Map.Entry<String, ArrayList<Def>>> it = CATEGORIES.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<String, ArrayList<Def>> e = it.next();
            for (int i = e.getValue().size() - 1; i >= 0; i--) if (generated(e.getValue().get(i))) e.getValue().remove(i);
            if (e.getValue().isEmpty()) { CATEGORY_COLOR.remove(e.getKey()); it.remove(); }
        }
        for (int i = REPORTERS.size() - 1; i >= 0; i--) if (REPORTERS.get(i).showWhen != null) REPORTERS.remove(i);
        for (String t : GEN_TEMPLATES) { GROUP_OF.remove(t); SHAPE_OF.remove(t); }
        GEN_TEMPLATES.clear();
        BlockInfo.clearGenerated();
        try {
            for (java.util.List<BehaviorBlocks.Spec> list : BehaviorBlocks.all(projectPath).values())
                for (BehaviorBlocks.Spec s : list) {
                    String cat = s.paletteCategory();
                    String[] show = {s.behaviorId};
                    if (!s.isValue()) {
                        Def d = new Def();
                        d.category = cat;
                        d.color = behaviorColor(cat);
                        d.title = s.title;
                        d.fields = s.fields();
                        d.defaults = s.defaults();
                        d.code = s.statementCode();
                        d.showWhen = show;
                        add(d);
                        BlockInfo.registerGenerated(s.key(), s.label, s.labelAr, null, s.descMap());
                    } else {
                        String tpl = s.reporterTemplate();
                        Reporter rep = new Reporter(cat, s.reporterDisplay(false), tpl);
                        rep.showWhen = show;
                        REPORTERS.add(rep);
                        GROUP_OF.put(tpl, cat);
                        SHAPE_OF.put(tpl, s.shape().equals("bool") ? 'b' : s.shape().equals("text") ? 't' : 'n');
                        GEN_TEMPLATES.add(tpl);
                        BlockInfo.registerGenerated(s.key(), s.label, s.labelAr, s.reporterDisplay(true), s.descMap());
                    }
                }
        } catch (Throwable ignored) {}
        version++;
    }

    private static String signature(String projectPath) {
        StringBuilder sb = new StringBuilder(String.valueOf(projectPath));
        try {
            for (BehaviorBlocks.Spec s : BehaviorBlocks.flat(projectPath))
                sb.append('\n').append(s.title).append('|').append(s.label).append('|').append(s.labelAr).append('|').append(s.desc).append('|').append(s.descAr).append('|')
                        .append(s.category).append('|').append(s.statementCode());
        } catch (Throwable ignored) {}
        return sb.toString();
    }

    /** Which behaviors the scene being edited uses (null = no filter: every behavior block is shown). */
    public static synchronized void setActiveBehaviors(java.util.Set<String> ids) {
        if (ids == null ? activeBehaviors == null : ids.equals(activeBehaviors)) return;
        activeBehaviors = ids;
        version++;
    }

    public static synchronized boolean isVisible(String[] showWhen) {
        if (showWhen == null || activeBehaviors == null) return true;
        for (String id : showWhen) if (activeBehaviors.contains(id)) return true;
        return false;
    }

    /** Scene being edited: 0 = unknown (every 2D and 3D block shows), {@link SceneDims#D2} or {@link SceneDims#D3}. */
    private static int sceneMode = 0;

    /** 2D / 3D filter of the palette: pass {@link SceneDims#D2}, {@link SceneDims#D3} (or 0 for no filter). */
    public static synchronized void setSceneMode(int mode) {
        if (mode == sceneMode) return;
        sceneMode = mode;
        version++;
    }

    public static synchronized int sceneMode() { return sceneMode; }

    /** Shown in the palette: right scene type (2D / 3D) and, for behavior blocks, a behavior the scene uses. */
    public static boolean isVisible(Def d) { return SceneDims.visible(d.dims, sceneMode()) && isVisible(d.showWhen); }

    public static boolean isVisible(Reporter r) { return SceneDims.visible(r.dims, sceneMode()) && isVisible(r.showWhen); }

    /** True when a category has a block for this scene mode (0 = any). */
    public static boolean categoryFor(String category, int mode) {
        ArrayList<Def> l = CATEGORIES.get(category);
        if (l == null) return false;
        for (Def d : l) if (SceneDims.visible(d.dims, mode)) return true;
        return false;
    }

    // ------------------------------------------------------------------ 2D / 3D

    /** Built-in categories that only make sense in 2D scenes (Box2D bodies, joints, the 2D camera, sprite animations). */
    private static final java.util.Set<String> CATS_2D = new java.util.HashSet<>(java.util.Arrays.asList(
            "Camera", "BodyModify", "bodyControl", "bodyBooleans", "Joints", "Motion", "Physics"));
    /** Built-in blocks of shared categories that are 2D only. */
    private static final java.util.Set<String> TITLES_2D = new java.util.HashSet<>(java.util.Arrays.asList(
            "setsplits", "ifCollision", "setGravity", "setTint", "setOpacity", "flipX", "bringToFront", "sendToBack",
            "setAnimation", "removeAnimation"));
    /** Built-in blocks of 2D categories that work in 3D scenes too. */
    private static final java.util.Set<String> TITLES_BOTH = new java.util.HashSet<>(java.util.Arrays.asList("destroy", "destroyAfter"));
    private static final java.util.Set<String> GROUPS_2D = new java.util.HashSet<>(java.util.Arrays.asList("PlayerItem", "Box2D Body"));
    private static final java.util.Set<String> REPORTERS_2D = new java.util.HashSet<>(java.util.Arrays.asList(
            "___ touches ___", "x of ___", "y of ___", "speed x of ___", "speed y of ___", "angle of ___", "distance ___ to ___",
            "angle ___ to ___", "width of ___", "height of ___", "touch x", "touch y", "camera x", "camera y", "getCamera()"));

    static int defaultDims(String category, String title) {
        if (category == null || category.startsWith("★") || category.startsWith(BehaviorBlocks.CATEGORY_PREFIX)) return SceneDims.BOTH;
        if (TITLES_BOTH.contains(title)) return SceneDims.BOTH;
        if (CATS_2D.contains(category) || TITLES_2D.contains(title)) return SceneDims.D2;
        return SceneDims.BOTH;
    }

    static int defaultReporterDims(String group, String display) {
        if (GROUPS_2D.contains(group) || REPORTERS_2D.contains(display)) return SceneDims.D2;
        return SceneDims.BOTH;
    }

    private static final ArrayList<Reporter> PENDING_3D = new ArrayList<>();

    /** The 3D blocks (and Inventory) of {@link Blocks3D}, then the Navigation / Timeline / Network blocks of {@link BlocksSystems}. */
    private static void add3D() {
        Blocks3D.Sink sink = builtinSink();
        Blocks3D.register(sink);
        BlocksSystems.register(sink);
    }

    private static Blocks3D.Sink builtinSink() {
        return new Blocks3D.Sink() {
            @Override
            public void category(String name, int color, String en, String ar) {
                CATEGORY_COLOR.put(name, color);
                GROUP_COLOR.put(name, color);
                BlockInfo.builtinCategory(name, en, ar);
            }

            @Override
            public void def(String cat, int color, String title, String fields, String defaults, boolean isIf, String code, int dims,
                            String en, String ar, String infoEn, String infoAr) {
                Def d = new Def();
                d.category = cat;
                d.color = color;
                d.title = title;
                d.fields = fields.isEmpty() ? new String[0] : fields.split(" ");
                d.defaults = defaults == null || defaults.isEmpty() ? null : defaults.split("\\|", -1);
                d.isIf = isIf;
                d.code = code;
                d.dims = dims;
                add(d);
                BlockInfo.builtinBlock(title, en, ar, infoEn, infoAr);
            }

            @Override
            public void reporter(String group, String display, String template, char shape, String slots, int dims, String displayAr,
                                 String infoEn, String infoAr) {
                Reporter rep = new Reporter(group, display, template);
                rep.dims = dims;
                rep.slots = slots;
                PENDING_3D.add(rep);
                if (!GROUP_OF.containsKey(template)) GROUP_OF.put(template, group);
                SHAPE_OF.put(template, shape);
                BlockInfo.builtinReporter(display, displayAr, infoEn, infoAr);
            }

            @Override
            public void field(String name, String en, String ar) {
                BlockInfo.builtinField(name, en, ar);
            }

            @Override
            public void choices(String type, String[] values) {
                CHOICES.put(type, values);
            }
        };
    }

    /** Ids of every behavior that has blocks (built-in and the project's). */
    public static java.util.Set<String> behaviorsWithBlocks(String projectPath) {
        return new java.util.LinkedHashSet<>(BehaviorBlocks.all(projectPath).keySet());
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) != -1) bo.write(b, 0, r);
        return bo.toByteArray();
    }
}
