package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * physics_info: pure arithmetic about jumps and speeds in PIXELS, so an AI picks numbers that work instead of guessing.
 *
 * What is VERIFIED in the engine (BlockCatalog templates + StageImp):
 *  - WORLD_SCALE = 0.25: blocks that take "px" multiply by 0.25 before calling Box2D, so px/s in blocks = real px/s.
 *  - setWorldGravity(strength) generates: setSteps(1); setGravity(0f, -strength*0.25f). So gravity is strength px/s^2 DOWN
 *    (positive number = down) and the physics runs 1 step per frame with delta = Gdx delta (real time).
 *    Without it StageImp runs 6 steps per frame (steps=6, world.step(delta) each): 6x real speed, so jumps/speeds are wrong.
 *  - setVelocityY / jump(power): linearVelocity.y = power*0.25; Box2D y is UP, so a POSITIVE value moves UP, negative moves DOWN.
 *    setVelocityX: positive = RIGHT, negative = LEFT.
 *  - Box2D clamps movement to 2 world units (8 px) per step: max speed = 8 px * fps (480 px/s at 60 fps, 240 at 30 fps).
 * Formulas (constant gravity, no damping, Gravity Scale 1): height = v^2/(2g); time up = v/g; flat air time = 2v/g.
 * Box2D integrates semi-implicitly, so real numbers are within a few percent (a few px) of these.
 */
final class PhysicsInfo {
    private PhysicsInfo() {}

    private static final double DEFAULT_JUMP = 450; // px/s: default jump speed when none is given
    private static final Pattern GRAV = Pattern.compile("setGravity\\(\\s*[^,]+,\\s*-\\s*\\(float\\)\\(\\s*([0-9.]+)\\s*\\)");
    private static final Pattern STEPS = Pattern.compile("setSteps\\(\\s*(\\d+)\\s*\\)");

    private static double num(JsonObject a, String k, double def) {
        try {
            JsonElement e = a.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsDouble();
        } catch (Exception ex) {
            return def;
        }
    }

    private static double r1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    static ToolResult info(JsonObject a) throws Exception {
        final JsonObject out = new JsonObject();
        final String wantScene = CoreTools.s(a, "scene");
        final String element = CoreTools.s(a, "element");
        final double[] frameW = {0}, frameH = {0};
        final double[] gravity = {num(a, "gravity", -1)};
        final String[] gsrc = {gravity[0] > 0 ? "argument" : ""};
        final boolean[] is3D = {false};
        Gl.run(() -> {
            LibgdxEditor ed = Proj.editor();
            Project p = ed.getProject();
            String scene = wantScene.isEmpty() ? ed.getScene() : wantScene;
            if (!Proj.scenes(p).contains(scene)) throw new Gl.ToolError("No scene named '" + scene + "'. Scenes: " + Proj.scenes(p));
            SceneInfo.Frame f = SceneInfo.of(ed, p, scene);
            out.addProperty("frame", SceneInfo.describe(f));
            if (f.is3D) {
                // 3D scene: Bullet physics in meters, gravity from the scene settings (Gravity Y, default -9.81)
                is3D[0] = true;
                out.addProperty("sceneType", "3D");
                if (gravity[0] <= 0) {
                    double gy = -9.81;
                    try {
                        FileHandle cf = Gdx.files.absolute(p.getConfig(scene));
                        if (cf.exists()) {
                            JsonObject c = com.google.gson.JsonParser.parseString(cf.readString()).getAsJsonObject();
                            if (c.has("Gravity Y")) gy = Double.parseDouble(c.get("Gravity Y").getAsString());
                        }
                    } catch (Exception ignored) {
                    }
                    gravity[0] = Math.abs(gy) < 1e-6 ? 0 : -gy;
                    gsrc[0] = "scene setting Gravity Y (" + gy + " m/s2)";
                }
                if (!element.isEmpty()) {
                    try {
                        for (LinkedHashMap<String, String> m : (scene.equals(ed.getScene()) ? CoreTools.liveElements(ed) : CoreTools.fileElements(p, scene))) {
                            if (!element.equals(m.get("name"))) continue;
                            JsonObject eo = new JsonObject();
                            eo.addProperty("name", element);
                            for (String k : new String[]{"TYPE", "Mesh", "Body", "Collider", "Mass", "Friction", "Restitution", "Linear Damping", "Gravity Scale", "Scale X", "Scale Y", "Scale Z", "Lock Rotation X", "Lock Rotation Z"})
                                if (m.containsKey(k)) eo.addProperty(k, m.get(k));
                            out.add("element", eo);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                return null;
            }
            frameW[0] = f.width;
            frameH[0] = f.height;
            // gravity of the scene: parsed from its OnCreate code (setWorldGravity block)
            boolean steps1 = false, found = false;
            try {
                FileHandle fh = Gdx.files.absolute(p.getEventPath(scene, "", "OnCreate") + ".java");
                String code = fh.exists() ? fh.readString() : "";
                Matcher m = GRAV.matcher(code);
                if (m.find()) {
                    found = true;
                    if (gravity[0] <= 0) {
                        gravity[0] = Double.parseDouble(m.group(1));
                        gsrc[0] = "scene OnCreate (setWorldGravity)";
                    }
                }
                Matcher sm = STEPS.matcher(code);
                if (sm.find()) steps1 = sm.group(1).equals("1");
            } catch (Exception ignored) {
            }
            if (gravity[0] <= 0) {
                gravity[0] = 800;
                gsrc[0] = found ? "default 800" : "default 800 (this scene has NO setWorldGravity in OnCreate: add it first, see warning)";
            }
            if (!found && !"argument".equals(gsrc[0]))
                out.addProperty("warning", "No setWorldGravity block in OnCreate of '" + scene + "'. Without it the engine runs 6 physics steps per frame (6x real speed) and the numbers below do NOT apply. Add events {scene OnCreate: setWorldGravity strength 800} as the FIRST block (0 for top-down).");
            else if (found && !steps1 && !"argument".equals(gsrc[0]))
                out.addProperty("warning", "OnCreate sets gravity but not 1 physics step per frame: use the setWorldGravity block (it does both).");
            if (!element.isEmpty()) {
                try {
                    for (LinkedHashMap<String, String> m : (scene.equals(ed.getScene()) ? CoreTools.liveElements(ed) : CoreTools.fileElements(p, scene))) {
                        if (!element.equals(m.get("name"))) continue;
                        JsonObject eo = new JsonObject();
                        eo.addProperty("name", element);
                        eo.addProperty("type", m.get("TYPE") + "/" + m.get("type"));
                        float r = parse(m.get("radius"));
                        eo.addProperty("width", r > 0 && parse(m.get("width")) <= 0 ? 2 * r : parse(m.get("width")));
                        eo.addProperty("height", r > 0 && parse(m.get("height")) <= 0 ? 2 * r : parse(m.get("height")));
                        eo.addProperty("friction", m.get("friction"));
                        eo.addProperty("restitution", m.get("restitution"));
                        eo.addProperty("Fixed Rotation", m.get("Fixed Rotation"));
                        out.add("element", eo);
                    }
                } catch (Throwable ignored) {
                }
            }
            return null;
        });

        if (is3D[0]) return info3D(a, out, gravity[0], gsrc[0]);
        double g = gravity[0];
        out.addProperty("gravity_px_s2", g);
        out.addProperty("gravity_source", gsrc[0]);
        double move = num(a, "move_speed", 250);
        out.addProperty("move_speed_px_s", move);
        double jumpSpeed = num(a, "jump_speed", -1), jumpHeight = num(a, "jump_height", -1);
        if (jumpSpeed <= 0 && jumpHeight > 0) {
            double need = Math.sqrt(2 * g * jumpHeight);
            JsonObject w = new JsonObject();
            w.addProperty("wanted_height_px", jumpHeight);
            w.addProperty("needed_jump_speed_px_s", r1(need));
            jumpSpeed = need; // no speed limit: the engine splits the physics step for fast bodies
            out.add("for_height", w);
        }
        if (jumpSpeed <= 0) jumpSpeed = DEFAULT_JUMP;
        JsonObject jump = new JsonObject();
        double height = jumpSpeed * jumpSpeed / (2 * g), tUp = jumpSpeed / g, air = 2 * jumpSpeed / g;
        jump.addProperty("jump_speed_px_s", r1(jumpSpeed));
        jump.addProperty("height_px", r1(height));
        jump.addProperty("time_to_apex_s", r1(tUp * 100) / 100.0);
        jump.addProperty("air_time_flat_s", r1(air * 100) / 100.0);
        jump.addProperty("max_horizontal_reach_px", r1(move * air));
        jump.addProperty("how", "jump block power / setVelocityY = " + Math.round(jumpSpeed) + " (POSITIVE = UP)");
        out.add("jump", jump);
        if (frameW[0] > 0) {
            out.addProperty("scale", "jump height is " + Math.round(100 * height / frameH[0]) + "% of the scene height; the scene width is crossed in " + cross(frameW[0], move) + " at move_speed " + Math.round(move) + ", the height is fallen in about " + String.format(Locale.US, "%.1f s", Math.sqrt(2 * frameH[0] / g)));
        }

        // can each jump be made? gap = horizontal distance between the edge you leave and the edge you land on, rise = height of the target above the start (negative = lower)
        JsonArray jumps = new JsonArray();
        if (a.has("jumps") && a.get("jumps").isJsonArray()) {
            for (JsonElement je : a.getAsJsonArray("jumps")) {
                if (!je.isJsonObject()) continue;
                JsonObject j = je.getAsJsonObject();
                double gap = num(j, "gap", 0), rise = num(j, "rise", 0);
                JsonObject r = new JsonObject();
                if (j.has("name")) r.addProperty("name", CoreTools.s(j, "name"));
                r.addProperty("gap", gap);
                r.addProperty("rise", rise);
                double disc = jumpSpeed * jumpSpeed - 2 * g * rise;
                if (disc < 0) {
                    r.addProperty("reachable", false);
                    r.addProperty("why", "too high: the jump only reaches " + Math.round(height) + " px (needs at least " + Math.round(Math.sqrt(2 * g * rise)) + " px/s)");
                    r.addProperty("fix", "lower the platform to <= " + Math.round(height * 0.8) + " px above the start (80% margin), or lower the gravity, or raise jump_speed");
                } else {
                    double t = (jumpSpeed + Math.sqrt(disc)) / g; // time to come down onto that height
                    double reach = move * t;
                    r.addProperty("air_time_s", r1(t * 100) / 100.0);
                    r.addProperty("reach_px", r1(reach));
                    boolean ok = gap <= reach * 0.9;
                    r.addProperty("reachable", ok);
                    if (!ok) r.addProperty("fix", "gap too wide: max comfortable gap " + Math.round(reach * 0.8) + " px at move_speed " + Math.round(move) + " (or raise move_speed)");
                    else if (gap > reach * 0.75) r.addProperty("note", "possible but tight for a player; comfortable gap <= " + Math.round(reach * 0.7) + " px");
                }
                jumps.add(r);
            }
            out.add("jumps", jumps);
        }

        JsonObject safe = new JsonObject();
        safe.addProperty("speed_limit", "none (very fast small bodies: Bullet true against tunneling)");
        safe.addProperty("platform_rise_comfortable_px", Math.round(height * 0.7));
        safe.addProperty("platform_gap_comfortable_px", Math.round(move * air * 0.7));
        safe.addProperty("typical", "gravity 600-1000, walk 200-350, jump 400-600, bullets 400-1000, enemies 80-150; walls/ground >= 20 px thick");
        safe.addProperty("units", "everything in px and px/s. Y IS UP. setVelocityY/jump: +up -down. setVelocityX: +right -left. setWorldGravity strength: + = falls down.");
        out.add("safe_ranges", safe);
        return ToolResult.text(out.toString());
    }

    /** 3D scenes (Bullet): meters, m/s, y up. No 8 px per step cap; gravity is the scene's Gravity Y. */
    private static ToolResult info3D(JsonObject a, JsonObject out, double g, String gsrc) {
        out.addProperty("units", "METERS and m/s, Y is UP, z = depth. 1 unit = 1 m. Bullet physics (elements with Body STATIC / DYNAMIC / KINEMATIC / TRIGGER).");
        out.addProperty("gravity_m_s2", g);
        out.addProperty("gravity_source", gsrc);
        double move = num(a, "move_speed", 5);
        out.addProperty("move_speed_m_s", move);
        JsonObject jump = new JsonObject();
        double jumpSpeed = num(a, "jump_speed", -1), jumpHeight = num(a, "jump_height", -1);
        if (g <= 0) {
            jump.addProperty("note", "gravity is 0: things float, a jump never comes down");
            out.add("jump", jump);
            return ToolResult.text(out.toString());
        }
        if (jumpSpeed <= 0 && jumpHeight > 0) jumpSpeed = Math.sqrt(2 * g * jumpHeight);
        if (jumpSpeed <= 0) jumpSpeed = 5;
        double height = jumpSpeed * jumpSpeed / (2 * g), air = 2 * jumpSpeed / g;
        jump.addProperty("jump_speed_m_s", r1(jumpSpeed * 10) / 10.0);
        jump.addProperty("height_m", r1(height * 10) / 10.0);
        jump.addProperty("air_time_flat_s", r1(air * 100) / 100.0);
        jump.addProperty("max_horizontal_reach_m", r1(move * air * 10) / 10.0);
        jump.addProperty("how", "a DYNAMIC body: impulse or set velocity Y = " + String.format(Locale.US, "%.1f", jumpSpeed) + " m/s (positive = UP); only when grounded");
        out.add("jump", jump);
        JsonArray jumps = new JsonArray();
        if (a.has("jumps") && a.get("jumps").isJsonArray()) {
            for (JsonElement je : a.getAsJsonArray("jumps")) {
                if (!je.isJsonObject()) continue;
                JsonObject j = je.getAsJsonObject();
                double gap = num(j, "gap", 0), rise = num(j, "rise", 0);
                JsonObject r = new JsonObject();
                if (j.has("name")) r.addProperty("name", CoreTools.s(j, "name"));
                double disc = jumpSpeed * jumpSpeed - 2 * g * rise;
                if (disc < 0) {
                    r.addProperty("reachable", false);
                    r.addProperty("why", "too high: the jump reaches " + String.format(Locale.US, "%.2f", height) + " m");
                } else {
                    double reach = move * (jumpSpeed + Math.sqrt(disc)) / g;
                    r.addProperty("reach_m", r1(reach * 10) / 10.0);
                    r.addProperty("reachable", gap <= reach * 0.9);
                }
                jumps.add(r);
            }
            out.add("jumps", jumps);
        }
        JsonObject safe = new JsonObject();
        safe.addProperty("typical", "gravity 9.81 (Gravity Y -9.81), walk 3-6 m/s, run 8-10, car 15-40, jump 4-7 m/s (about 1-2.5 m high); a human is about 1.8 m tall (CAPSULE Scale Y ~0.9-1), a door 2 m, a floor 3 m");
        safe.addProperty("bodies", "DYNAMIC = moved by forces/gravity (Mass), STATIC = ground/walls, KINEMATIC = moved by code/tweens and pushes dynamics, TRIGGER = no push, only collision events (pickups, zones). Collider AUTO fits the shape; use CAPSULE for characters (Lock Rotation X/Z true so they do not tip over).");
        out.add("safe_ranges", safe);
        return ToolResult.text(out.toString());
    }

    private static float parse(String s) {
        try {
            return s == null || s.isEmpty() ? 0 : Float.parseFloat(s);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Crossing time of a frame side at a given speed (used in docs/answers). */
    static String cross(double side, double speed) {
        return String.format(Locale.US, "%.1f s", side / Math.max(1, speed));
    }
}
