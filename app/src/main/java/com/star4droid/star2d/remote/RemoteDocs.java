package com.star4droid.star2d.remote;

import android.content.Context;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.ui.blocks.BlockCatalog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The documentation the app serves to the AI (read_docs) and writes into every project (.star2d/docs).
 * Topic files live in assets/ai/docs/&lt;id&gt;.md. Two placeholders are expanded from the real engine data:
 * {{ELEMENT_TABLES}} (assets/items/*.json, types.json, spinners.json) and {{BLOCK_CATALOG}} (BlockCatalog).
 */
public final class RemoteDocs {
    private RemoteDocs() {}

    private static final String DIR = "ai/docs";

    /** Preferred order of the topics in the index; topics not listed come after, alphabetically. */
    private static final String[] ORDER = {
            "index", "overview", "scenes", "elements", "physics", "events", "blocks", "blocks-examples", "blocks-catalog",
            "variables", "ui-input", "assets", "joints", "java-api", "recipes", "recipes-2", "testing", "pitfalls"};

    // ------------------------------------------------------------------ public API

    /** The index topic (golden rules + topic list). Topics that exist but are not mentioned in it are appended. */
    public static String index(Context c) {
        String text = read(c, "index");
        if (text == null) return "The documentation index is missing from this build of the app.";
        StringBuilder extra = new StringBuilder();
        for (String[] t : topics(c)) {
            if (t[0].equals("index")) continue;
            if (!text.contains("`" + t[0] + "`")) extra.append("- `").append(t[0]).append("`: ").append(t[1]).append("\n");
        }
        if (extra.length() > 0) text = text + "\n\n## More topics\n" + extra;
        return expand(c, text);
    }

    /** One topic with the placeholders expanded; an unknown id returns an error text listing the valid ids. */
    public static String topic(Context c, String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (key.endsWith(".md")) key = key.substring(0, key.length() - 3);
        if (key.isEmpty() || key.equals("index")) return index(c);
        String text = null;
        for (String[] t : topics(c)) if (t[0].equals(key)) text = read(c, key);
        if (text == null) {
            StringBuilder ids = new StringBuilder();
            for (String[] t : topics(c)) ids.append(ids.length() == 0 ? "" : ", ").append(t[0]);
            return "Unknown topic '" + id + "'. Valid topics: " + ids + ". Call read_docs without a topic for the index.";
        }
        return expand(c, text);
    }

    /** {id, title} of every topic, index first. The title is the first Markdown heading of the file. */
    public static List<String[]> topics(Context c) {
        ArrayList<String> ids = new ArrayList<>();
        try {
            String[] names = c.getAssets().list(DIR);
            if (names != null)
                for (String n : names) if (n.endsWith(".md")) ids.add(n.substring(0, n.length() - 3));
        } catch (Exception ignored) {
        }
        Collections.sort(ids);
        final List<String> order = Arrays.asList(ORDER);
        Collections.sort(ids, (a, b) -> {
            int ia = order.indexOf(a), ib = order.indexOf(b);
            if (ia < 0) ia = 1000;
            if (ib < 0) ib = 1000;
            return ia != ib ? Integer.compare(ia, ib) : a.compareTo(b);
        });
        ArrayList<String[]> out = new ArrayList<>();
        for (String id : ids) out.add(new String[]{id, titleOf(read(c, id), id)});
        return out;
    }

    // ------------------------------------------------------------------ partial reading (read_docs)

    /** A topic bigger than this is answered with its table of contents unless {@code full} or a section is asked for. */
    private static final int WHOLE_TOPIC_LIMIT = 5000;
    private static final int MAX_REPLY = 14000;
    private static final HashMap<String, String> EXPANDED = new HashMap<>();

    private static final class Head {
        final int level, start;
        int end;
        final String title;

        Head(int level, int start, String title) {
            this.level = level;
            this.start = start;
            this.title = title;
        }
    }

    private static synchronized String expanded(Context c, String id) {
        String t = EXPANDED.get(id);
        if (t == null) {
            String raw = read(c, id);
            if (raw == null) return null;
            t = expand(c, raw);
            EXPANDED.put(id, t);
        }
        return t;
    }

    /** Headings of levels 2-4 with the char range of their sections (a section ends at the next heading of the same or a higher level). */
    private static List<Head> heads(String text) {
        ArrayList<Head> hs = new ArrayList<>();
        int pos = 0;
        boolean code = false;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("```")) code = !code;
            if (!code && line.startsWith("##")) {
                int lv = 0;
                while (lv < line.length() && line.charAt(lv) == '#') lv++;
                if (lv >= 2 && lv <= 4 && lv < line.length() && line.charAt(lv) == ' ')
                    hs.add(new Head(lv, pos, line.substring(lv + 1).trim()));
            }
            pos += line.length() + 1;
        }
        for (int i = 0; i < hs.size(); i++) {
            Head h = hs.get(i);
            h.end = text.length();
            for (int j = i + 1; j < hs.size(); j++)
                if (hs.get(j).level <= h.level) {
                    h.end = hs.get(j).start;
                    break;
                }
        }
        return hs;
    }

    private static String normId(String t) {
        String key = t == null ? "" : t.trim().toLowerCase(Locale.ROOT);
        return key.endsWith(".md") ? key.substring(0, key.length() - 3) : key;
    }

    private static String validIds(Context c) {
        StringBuilder ids = new StringBuilder();
        for (String[] t : topics(c)) ids.append(ids.length() == 0 ? "" : ", ").append(t[0]);
        return ids.toString();
    }

    /** read_docs: no arguments = the map; topic(s) = whole small topics or the table of contents of big ones; section = just that part; search = best matching parts. */
    public static String read(Context c, String topic, String section, String search, boolean full) {
        if (search != null && !search.trim().isEmpty()) return search(c, search.trim(), normId(topic));
        boolean noTopic = topic == null || topic.trim().isEmpty();
        boolean noSection = section == null || section.trim().isEmpty();
        if (noTopic && noSection) return index(c);
        if (noTopic) return sectionEverywhere(c, section.trim());
        StringBuilder out = new StringBuilder();
        for (String raw : topic.split(",")) {
            String id = normId(raw);
            if (id.isEmpty()) continue;
            if (id.equals("index")) {
                out.append(index(c));
                continue;
            }
            String text = expanded(c, id);
            if (text == null) {
                out.append("Unknown topic '").append(raw.trim()).append("'. Valid topics: ").append(validIds(c)).append(".\n");
                continue;
            }
            if (out.length() > 0) out.append("\n\n---\n\n");
            if (!noSection) out.append(sectionOf(id, text, section.trim()));
            else if (full || text.length() <= WHOLE_TOPIC_LIMIT) out.append(text);
            else out.append(toc(id, text));
        }
        String r = out.toString();
        if (r.length() > MAX_REPLY && noSection == false)
            r = r.substring(0, MAX_REPLY) + "\n…(cut: ask a narrower section)";
        return r;
    }

    private static String toc(String id, String text) {
        List<Head> hs = heads(text);
        StringBuilder sb = new StringBuilder();
        String first = text.split("\n", 2)[0];
        sb.append(first).append("\n\n");
        sb.append("This topic is long (").append(text.length() / 4).append(" tokens). Read only what you need with read_docs {topic:'").append(id)
                .append("', section:'<words of a heading>'} (or full:true for everything). Sections:\n");
        int n = 0;
        for (Head h : hs) {
            if (h.level > 3) continue;
            if (n++ >= 60) {
                sb.append("- …\n");
                break;
            }
            sb.append(h.level == 2 ? "- " : "  - ").append(h.title).append(" (").append((h.end - h.start) / 4).append(" tok)\n");
        }
        if (n == 0) sb.append("(no sections; use full:true)\n");
        return sb.toString();
    }

    private static String sectionOf(String id, String text, String query) {
        List<Head> hs = heads(text);
        String q = query.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        int lastEnd = -1, found = 0;
        for (Head h : hs) {
            if (h.start < lastEnd) continue; // already inside a section we returned
            if (!h.title.toLowerCase(Locale.ROOT).contains(q)) continue;
            sb.append(text, h.start, h.end).append("\n");
            lastEnd = h.end;
            if (++found >= 6) break;
        }
        if (found == 0) {
            StringBuilder names = new StringBuilder();
            for (Head h : hs) if (h.level <= 3) names.append(names.length() == 0 ? "" : " | ").append(h.title);
            return "No section containing '" + query + "' in topic '" + id + "'. Sections: " + names;
        }
        return sb.toString();
    }

    private static String sectionEverywhere(Context c, String query) {
        StringBuilder sb = new StringBuilder();
        for (String[] t : topics(c)) {
            if (t[0].equals("index")) continue;
            String text = expanded(c, t[0]);
            if (text == null) continue;
            String r = sectionOf(t[0], text, query);
            if (!r.startsWith("No section")) sb.append("[").append(t[0]).append("]\n").append(r).append("\n");
            if (sb.length() > MAX_REPLY) break;
        }
        if (sb.length() == 0) return "No section containing '" + query + "' in any topic. Try read_docs {search:'" + query + "'}.";
        return sb.length() > MAX_REPLY ? sb.substring(0, MAX_REPLY) + "\n…(cut: pass topic too)" : sb.toString();
    }

    /** Best matching sections for some words, as addresses with a one-line hint (cheap); then read_docs {topic, section} for the body. */
    private static String search(Context c, String query, String onlyTopic) {
        String[] tok = query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+");
        ArrayList<Object[]> hits = new ArrayList<>(); // {score, topic, title, snippet}
        for (String[] t : topics(c)) {
            if (t[0].equals("index") || (!onlyTopic.isEmpty() && !onlyTopic.equals(t[0]))) continue;
            String text = expanded(c, t[0]);
            if (text == null) continue;
            for (Head h : heads(text)) {
                if (h.level > 3) continue;
                String body = text.substring(h.start, h.end).toLowerCase(Locale.ROOT);
                String title = h.title.toLowerCase(Locale.ROOT);
                int score = 0;
                for (String k : tok) {
                    if (k.length() < 2) continue;
                    if (title.contains(k)) score += 6;
                    int from = 0, n = 0;
                    while (n < 20 && (from = body.indexOf(k, from)) >= 0) {
                        n++;
                        from += k.length();
                    }
                    score += n;
                }
                if (score <= 0) continue;
                String snippet = "";
                for (String line : text.substring(h.start, h.end).split("\n")) {
                    String l = line.trim();
                    if (l.isEmpty() || l.startsWith("#") || l.startsWith("|---") || l.startsWith("```")) continue;
                    snippet = l.length() > 140 ? l.substring(0, 140) + "…" : l;
                    break;
                }
                hits.add(new Object[]{score, t[0], h.title, snippet});
            }
        }
        if (hits.isEmpty()) return "Nothing found for '" + query + "'. Topics: " + validIds(c) + ".";
        Collections.sort(hits, (a, b) -> Integer.compare((Integer) b[0], (Integer) a[0]));
        StringBuilder sb = new StringBuilder("Best matches (read one with read_docs {topic, section}):\n");
        for (int i = 0; i < Math.min(7, hits.size()); i++) {
            Object[] h = hits.get(i);
            sb.append("- ").append(h[1]).append(" › ").append(h[2]).append(": ").append(h[3]).append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ element tables

    private static final String[][] TYPES = {
            {"BOX", "box.json", "Sprite / rectangle with a Box2D body (the workhorse: player, platforms, enemies, bullets, coins)"},
            {"CIRCLE", "circle.json", "Round body: radius instead of width/height (balls, coins, round enemies)"},
            {"CUSTOM", "custom.json", "Body with a polygon outline from Points (hollow ChainShape loop: use STATIC for terrain)"},
            {"TEXT", "text.json", "A text label (HUD, score, titles). type UI = fixed on screen"},
            {"PROGRESS", "progress.json", "A progress bar (health, loading). type UI = fixed on screen"},
            {"JOYSTICK", "joystick.json", "Touch joystick (read it with the joystick reporters). type UI = fixed on screen"},
            {"PARTICLE", "particle.json", "libGDX particle effect from a .p file in files/ (empty file = default smoke)"},
            {"CAMERA", "camera.json", "CAMERA element: in this version its settings are not applied; control the camera with the Camera blocks"},
            {"MAP", "map.json", "Tiled map (.tmx under images/), drawn only: it has no collision"},
            {"GROUP", "group.json", "GROUP: empty container, draws nothing. Elements with parent = the group follow it (move, rotate, hide, delete). See topic groups"},
            {"UIHOLDER", "uiholder.json", "UIHOLDER: shows a screen made in the UI Maker (file UI/<ui>.ui) as ONE block (HUD, menu, dialog). ui = file name without .ui, fit = stretch|contain|cover. type UI = fixed on screen. layout = free (x,y,width,height) | screen (placed by the screen box: anchor topleft|top|topright|left|center|right|bottomleft|bottom|bottomright, Stretch X / Stretch Y = fill width / height, Width Percent / Height Percent = 0-100 of the screen (0 = use width/height), Margin X / Margin Y px from the anchored edges). A full-screen background is its own holder (screen, both stretches, low z). Inner buttons have their own events (UI events), not editable as scene elements"},
            {"LIGHT point", "Lights/point.json", "Point light (needs a dark Ambient Light in the scene config to be visible)"},
            {"LIGHT cone", "Lights/cone.json", "Cone light"},
            {"LIGHT directional", "Lights/directional.json", "Directional light"},
            {"CAMERAVIEW", "cameraview.json", "CAMERAVIEW (2D element, both scene kinds, px like any UI element): live picture of a camera on the screen (mirror, minimap, rear view, security cam). Camera = name of a CAMERA3D or CAMERA element (\"\" = the main camera), Zoom, Resolution 0.25..1, Refresh Rate per second (0 = every frame), Touch = touches go through to the picture. type UI = fixed on screen"},
            {"OBJ3D", "obj3d.json", "3D SCENES ONLY. A 3D object in METERS (x right, y UP, z depth, ground at y 0; Rotation X/Y/Z degrees; Scale X/Y/Z = size in m for primitives, a CUBE of scale 1 is 1x1x1 m). Mesh = the shape (CUBE SPHERE CYLINDER CAPSULE CONE PLANE TORUS RAMP STAIRS PYRAMID HEMISPHERE TUBE WEDGE QUAD DISC EMPTY), MODEL (Model = /car.glb in models/, with its animations), TERRAIN (hills: Terrain Noise / Heightmap) or WATER (waves, things float). Body NONE|STATIC|DYNAMIC|KINEMATIC|TRIGGER = Bullet physics, Collider AUTO fits the shape. You may also give the shape as the type: {\"op\":\"add\",\"type\":\"SPHERE\"}. See topic scenes, section 3D scenes"},
            {"LIGHT3D", "light3d.json", "3D SCENES ONLY. A 3D light (the scene already has a sun from its Lighting Preset): Light Kind POINT (bulb, Range m), SPOT (cone: Cone Angle, aims along its rotation, Rotation X -90 = down) or DIRECTIONAL (extra sun: only the rotation matters). Intensity, Color, Cast Shadows (costly, use few), Flicker, Attach To = follow an element"},
            {"CAMERA3D", "camera3d.json", "3D SCENES ONLY. A 3D camera; the one with Active true is the main camera. FOV degrees, Near/Far m, Orthographic + Ortho Size. Follow = element to follow with Follow Mode THIRD_PERSON|FIRST_PERSON|ORBIT|TOP_DOWN|SIDE|LOOK_AT (Follow Distance, Follow Height, Follow Smooth, Look Height, Follow Angle); Touch Look = drag to turn (orbit / first person)"},
    };

    private static String tablesCache;

    /** One Markdown table per element type: name | kind | default | allowed values. */
    public static synchronized String elementTables(Context c) {
        if (tablesCache != null) return tablesCache;
        String t = elementTables(path -> readAsset(c, path));
        if (!t.startsWith("(element tables could not")) tablesCache = t;
        return t;
    }

    /** Source of asset files (so the generators can also run outside Android, e.g. in tests). */
    interface Assets {
        String read(String path) throws Exception;
    }

    static String elementTables(Assets a) {
        StringBuilder sb = new StringBuilder();
        try {
            String defaults = a.read("items/default.json");
            Map<String, String> kinds = kinds(a);
            JsonObject spinners = JsonParser.parseString(a.read("spinners.json")).getAsJsonObject();
            for (String[] t : TYPES) {
                boolean light2d = t[0].startsWith("LIGHT "); // "LIGHT point"...; LIGHT3D is its own type
                String typeName = light2d ? "LIGHT" : t[0];
                String own = "";
                try {
                    own = a.read("items/" + t[1]).trim();
                } catch (Exception ignored) {
                }
                JsonObject o = JsonParser.parseString(defaults.replace("--other--", own.isEmpty() ? "" : "," + own)).getAsJsonObject();
                sb.append("### ").append(t[0]).append("\n").append(t[2]).append("\n\n");
                if (light2d)
                    sb.append("Add it with {\"op\":\"add\",\"type\":\"LIGHT\",\"props\":{\"Light Type\":\"").append(t[0].substring(6)).append("\"}}.\n\n");
                else
                    sb.append("Add it with {\"op\":\"add\",\"type\":\"").append(typeName).append("\",\"name\":\"...\",\"props\":{...}}.\n\n");
                sb.append("| name | kind | default | allowed values |\n|---|---|---|---|\n");
                boolean el3d = t[0].equals("OBJ3D") || t[0].equals("LIGHT3D") || t[0].equals("CAMERA3D");
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    String k = e.getKey();
                    // 2D-only keys a 3D element carries but never uses (2D body type, 2D angle, 2D pivot)
                    if (el3d && (k.equals("type") || k.equals("rotation") || k.startsWith("Pivot"))) continue;
                    String v = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString();
                    if (v.equals("ItemName")) v = "<element name>";
                    String kind = kinds.get(k);
                    if (kind == null && k.equals("Map")) kind = "File";
                    boolean internal = kind == null;
                    if (internal) kind = "internal";
                    sb.append("| ").append(esc(k)).append(" | ").append(kind).append(" | ").append(v.isEmpty() ? "(empty)" : esc(v))
                            .append(" | ").append(internal ? "leave as is (must not be empty)" : allowed(k, kind, spinners)).append(" |\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            return "(element tables could not be generated: " + e + ")\n";
        }
        return sb.toString();
    }

    private static Map<String, String> kinds(Assets a) throws Exception {
        HashMap<String, String> m = new HashMap<>();
        JsonObject o = JsonParser.parseString(a.read("types.json")).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : o.entrySet())
            for (String f : e.getValue().getAsString().split(",")) if (!f.trim().isEmpty()) m.put(f.trim(), e.getKey());
        return m;
    }

    private static String allowed(String key, String kind, JsonObject spinners) {
        if (spinners.has(key)) return spinners.get(key).getAsString().replace(",", " \\| ");
        switch (kind) {
            case "boolean": return "true \\| false";
            case "color": return "#RRGGBB or #RRGGBBAA";
            case "float": return "number";
            case "image": return "/name.png (relative to images/)";
            case "File": return key.equals("font") ? "project path of a .s2df font" : key.equals("Map") ? "/level.tmx (a .tmx under images/)" : "/effect.p (a particle file under files/)";
            case "string": return key.equals("name") ? "letters, digits, _" : "text";
            case "body": return key.equals("Collision") ? "e.g. (a)(b): names of elements that do not collide" : "element name";
            case "script": return "name of the event script";
            case "points": return "read-only in the elements tool";
            case "model": return "/name.glb, /name.gltf or /name.obj (a file of the models/ folder); sets Mesh MODEL";
            case "shader": return "\"\" (the scene's Render Mode), builtin:glow|hologram|dissolve|wave|rim|toon|gradient|pulse|xray|flag|lava|ghost, or shaders/NAME (file shaders/NAME.glsl)";
            case "static": return "read-only (fixed when the element is added)";
            default: return "";
        }
    }

    private static String esc(String s) {
        return s.replace("|", "/").replace("\n", " ");
    }

    // ------------------------------------------------------------------ block catalog

    /** Short notes for blocks that do not do what their name suggests (verified against the block templates). */
    private static final HashMap<String, String> BLOCK_NOTES = new HashMap<>();
    private static final HashMap<String, String> REPORTER_NOTES = new HashMap<>();

    /** English note of a built-in block (what it really does / how to fill it), or null; also shown by the block editor's (i) button when the block has no description. */
    public static String noteFor(String title) {
        return BLOCK_NOTES.get(title);
    }

    static {
        BLOCK_NOTES.put("saveValue", "BROKEN: generates a method that does not exist, use saveData");
        BLOCK_NOTES.put("setsplits", "BROKEN: generates a method that does not exist, use setWorldGravity");
        BLOCK_NOTES.put("setAlpha", "NO-OP (comment only), use setOpacity");
        BLOCK_NOTES.put("moveTowardBody", "NO-OP (comment only), use moveToward");
        BLOCK_NOTES.put("void", "only for custom script files, not for events");
        BLOCK_NOTES.put("cancelTimer", "only works in the same event that created the timer");
        BLOCK_NOTES.put("_DoAfter", "like waitSeconds but the timer is named; locals used inside must be final");
        BLOCK_NOTES.put("_repeatEvery", "like everySeconds but the timer is named");
        BLOCK_NOTES.put("try", "do = try part, else = catch part");
        BLOCK_NOTES.put("setX", "world units (px*0.25); prefer goTo");
        BLOCK_NOTES.put("setY", "world units (px*0.25); prefer goTo");
        BLOCK_NOTES.put("setAngle", "radians; prefer rotateBy");
        BLOCK_NOTES.put("setTransform", "world units (px*0.25), angle in radians; prefer goTo");
        BLOCK_NOTES.put("setLinearVelocity", "raw Box2D world units; prefer setVelocityX / setVelocityY");
        BLOCK_NOTES.put("setGravityScale", "value is raw Java: write 0.5f or 0, not 0.5");
        BLOCK_NOTES.put("setGravity", "raw Java floats (-9.8f, 0f); prefer setWorldGravity");
        BLOCK_NOTES.put("setCameraXY", "x,y in WORLD units (px*0.25), not px");
        BLOCK_NOTES.put("setCameraX", "WORLD units (px*0.25)");
        BLOCK_NOTES.put("setCameraY", "WORLD units (px*0.25)");
        BLOCK_NOTES.put("setCameraCenter", "WORLD units (px*0.25)");
        BLOCK_NOTES.put("setZoom", "bigger = closer; the default view is 4");
        BLOCK_NOTES.put("cameraFollow", "centers the camera once, it does not follow by itself: put it in OnStep");
        BLOCK_NOTES.put("copyBody", "clone of the element; prefer spawnCopy");
        BLOCK_NOTES.put("setText", "text is a Java expression: quote literals, e.g. \"Score: \" + score");
        BLOCK_NOTES.put("showText", "text is a Java expression (default \"Hello\" has quotes)");
        BLOCK_NOTES.put("Toast", "message is a Java expression: quote literals");
        BLOCK_NOTES.put("debug", "message is a Java expression: quote literals");
        BLOCK_NOTES.put("setProgress", "value is cast to int");
        BLOCK_NOTES.put("setVisibility", "visibility: true | false");
        BLOCK_NOTES.put("setTint", "color without quotes, e.g. FF0000FF (RRGGBBAA)");
        BLOCK_NOTES.put("setWorldGravity", "also sets physics to 1 step per frame: speeds in px/s become true px/s. Call first in OnCreate");
        BLOCK_NOTES.put("OpenScene", "scene name without quotes");
        BLOCK_NOTES.put("destroyAfter", "destroying the same element twice can crash: prefer the destroyLater helper (topic blocks)");
        BLOCK_NOTES.put("destroy", "never inside a collision event, never twice on one element: prefer destroyLater (topic blocks)");
        BLOCK_NOTES.put("destroyLater", "helper from topic blocks: safe delayed destroy, handles final");
        BLOCK_NOTES.put("spawnCopy", "clone of the element at x,y px; var = Java variable name, newName = Java expression (quoted)");
        BLOCK_NOTES.put("ifCollision", "same test as the reporter ___ touches ___");
        BLOCK_NOTES.put("setBodyType", "DynamicBody | StaticBody | KinematicBody");
        BLOCK_NOTES.put("setSpringFrequencyHz", "WheelJoint only");
        BLOCK_NOTES.put("setSpringDampingRatio", "WheelJoint only");
        BLOCK_NOTES.put("setMotorSpeed", "joint = the joint's name; Revolute/Prismatic/Wheel; radians per second for hinges");
        BLOCK_NOTES.put("setMaxMotorTorque", "joint = the joint's name; Revolute/Wheel");
        BLOCK_NOTES.put("saveData", "persistent; read it back with the reporter saved number ___ / saved text ___");

        REPORTER_NOTES.put("x of ___", "center x in px");
        REPORTER_NOTES.put("y of ___", "center y in px, y up");
        REPORTER_NOTES.put("speed x of ___", "px per simulated second");
        REPORTER_NOTES.put("speed y of ___", "px per simulated second");
        REPORTER_NOTES.put("angle of ___", "degrees");
        REPORTER_NOTES.put("distance ___ to ___", "px");
        REPORTER_NOTES.put("angle ___ to ___", "radians");
        REPORTER_NOTES.put("touch x", "px");
        REPORTER_NOTES.put("touch y", "px");
        REPORTER_NOTES.put("camera x", "px");
        REPORTER_NOTES.put("camera y", "px");
        REPORTER_NOTES.put("frame time", "seconds");
        REPORTER_NOTES.put("___ .getX()", "actor x in WORLD units (px*0.25), bottom-left");
        REPORTER_NOTES.put("___ .getY()", "actor y in WORLD units (px*0.25), bottom-left");
        REPORTER_NOTES.put("width of ___", "actor width in WORLD units for world elements");
        REPORTER_NOTES.put("height of ___", "actor height in WORLD units for world elements");
        REPORTER_NOTES.put("angle (deg) ___", "points OPPOSITE to the stick direction, avoid; use joystick x / y");
        REPORTER_NOTES.put("power ___", "squared distance of the stick");
        REPORTER_NOTES.put("___ equals ___", "compares the two values as text");
        REPORTER_NOTES.put("random ___ to ___", "integer, both ends included");
        REPORTER_NOTES.put("saved number ___", "key is a plain string without quotes");
        REPORTER_NOTES.put("saved text ___", "key is a plain string without quotes");
        REPORTER_NOTES.put("___ is named ___", "second slot is a plain name without quotes");
        REPORTER_NOTES.put("\"___\"", "a text literal; the slot is the plain text");
    }

    /** All statement blocks per category (one compact line each) and the value blocks (reporters) by group. */
    public static String blockCatalog(Context c) {
        Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        BlockCatalog.load(app);
        return blockCatalogText();
    }

    /** The text for the blocks currently loaded in {@link BlockCatalog}. */
    static String blockCatalogText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Statement blocks. Line format: `title(field, field:Type) [container] - defaults`. ")
                .append("Use the field name before the colon as the key in \"fields\". ")
                .append("[container] blocks take \"do\" (and \"else\" when listed).\n\n");
        for (Map.Entry<String, ArrayList<BlockCatalog.Def>> e : BlockCatalog.CATEGORIES.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append("### ").append(e.getKey());
            boolean only2 = true, only3 = true;
            for (BlockCatalog.Def d : e.getValue()) {
                if (d.dims != com.star4droid.star2d.blocks.SceneDims.D2) only2 = false;
                if (d.dims != com.star4droid.star2d.blocks.SceneDims.D3) only3 = false;
            }
            sb.append(only3 ? " (3D scenes only)" : only2 ? " (2D scenes only)" : "").append("\n");
            for (BlockCatalog.Def d : e.getValue()) {
                sb.append("- `").append(d.title).append("(");
                StringBuilder defs = new StringBuilder();
                for (int i = 0; i < d.fields.length; i++) {
                    String f = d.fields[i];
                    int a = f.indexOf('(');
                    String label = a > 0 ? f.substring(0, a) : f;
                    String type = a > 0 && f.lastIndexOf(')') > a ? f.substring(a + 1, f.lastIndexOf(')')) : "";
                    if (i > 0) sb.append(", ");
                    sb.append(label);
                    if (!type.isEmpty()) sb.append(":").append(type);
                    String dv = d.defaults != null && i < d.defaults.length ? d.defaults[i] : "";
                    if (!dv.isEmpty()) defs.append(defs.length() == 0 ? "" : ", ").append(label).append("=").append(dv);
                }
                sb.append(")");
                if (d.isIf) {
                    boolean hasElse = false;
                    try {
                        hasElse = d.create().hasElse();
                    } catch (Throwable ignored) {
                    }
                    if (d.title.equals("while")) hasElse = false;
                    sb.append(hasElse ? " [container, else]" : " [container]");
                }
                sb.append("`");
                if (defs.length() > 0) sb.append(" - defaults ").append(defs);
                if (d.dims == com.star4droid.star2d.blocks.SceneDims.D2 && !only2) sb.append(" [2D only]");
                String note = BLOCK_NOTES.get(d.title);
                if (note != null) sb.append(" (").append(note).append(")");
                sb.append("\n");
            }
            sb.append("\n");
        }
        sb.append("## Value blocks (reporters)\n\n");
        sb.append("Use as a field value: {\"r\":\"<exact text>\",\"args\":[...]}. One arg per ___ in order; args may be strings or nested reporters. ")
                .append("Tags: [bool] yields a boolean, [text] a String, otherwise a number or object.\n\n");
        LinkedHashMap<String, ArrayList<BlockCatalog.Reporter>> groups = new LinkedHashMap<>();
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) {
            ArrayList<BlockCatalog.Reporter> l = groups.get(r.group);
            if (l == null) {
                l = new ArrayList<>();
                groups.put(r.group, l);
            }
            l.add(r);
        }
        for (Map.Entry<String, ArrayList<BlockCatalog.Reporter>> g : groups.entrySet()) {
            sb.append("### ").append(g.getKey()).append("\n");
            for (BlockCatalog.Reporter r : g.getValue()) {
                sb.append("- `").append(r.display).append("`");
                if (r.dims == com.star4droid.star2d.blocks.SceneDims.D3) sb.append(" [3D]");
                else if (r.dims == com.star4droid.star2d.blocks.SceneDims.D2) sb.append(" [2D]");
                if (BlockCatalog.isBooleanTemplate(r.template)) sb.append(" [bool]");
                else if (BlockCatalog.isTextTemplate(r.template)) sb.append(" [text]");
                String note = REPORTER_NOTES.get(r.display);
                if (note != null) sb.append(" - ").append(note);
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static String expand(Context c, String text) {
        if (text.contains("{{ELEMENT_TABLES}}")) text = text.replace("{{ELEMENT_TABLES}}", elementTables(c));
        if (text.contains("{{BLOCK_CATALOG}}")) text = text.replace("{{BLOCK_CATALOG}}", blockCatalog(c));
        return text;
    }

    private static String titleOf(String text, String id) {
        if (text != null)
            for (String line : text.split("\n", 12)) {
                String t = line.trim();
                if (t.startsWith("#")) return t.replaceFirst("^#+\\s*", "");
            }
        return id;
    }

    /** Raw topic file, or null. */
    private static String read(Context c, String id) {
        try {
            return readAsset(c, DIR + "/" + id + ".md");
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAsset(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) != -1) bo.write(b, 0, r);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
