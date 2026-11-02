package com.star4droid.star2d.remote;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.ui.Bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draw-order (z) policy for elements added by the AI.
 * VERIFIED in the engine: at runtime StageImp.orderBodies() merges the world actors and the UI actors and sorts them by the
 * element's z (ascending), then draws in that order: a HIGHER z is drawn ON TOP, UI elements are NOT automatically above the world.
 * Elements with equal z keep their creation order (a later element covers an earlier one). The default z of every new element is 0,
 * so a background added after the player covers it. z must be >= 0 (negative z crashes BoxItem in the editor).
 * Convention: background 0, scenery 5, ground/platforms/walls 10, actors (player, enemies, items) 20, effects 40,
 * UI box 100, TEXT/PROGRESS 110, JOYSTICK 120.
 * All methods: GL thread.
 */
final class ZPolicy {
    private ZPolicy() {}

    static final int BACKGROUND = 0, SCENERY = 5, GROUND = 10, ACTOR = 20, EFFECT = 40, UI = 100, LABEL = 110, JOYSTICK = 120;

    private static float f(Map<String, String> m, String k, float def) {
        String v = m.get(k);
        if (v == null || v.isEmpty()) return def;
        try {
            return Float.parseFloat(v);
        } catch (Exception e) {
            return def;
        }
    }

    static String kind(Map<String, String> m) {
        return String.valueOf(m.get("TYPE")).toUpperCase(Locale.ROOT);
    }

    static boolean isUiType(Map<String, String> m) {
        return "UI".equalsIgnoreCase(m.get("type")) || kind(m).equals("JOYSTICK");
    }

    static float[] rect(Map<String, String> m) {
        float r = f(m, "radius", 0);
        float w = f(m, "width", r * 2), h = f(m, "height", r * 2);
        return new float[]{f(m, "x", 0), f(m, "y", 0), w, h};
    }

    /**
     * Elements this policy cares about (the others keep their z): real visible things, not groups/cameras/lights.
     * 3D elements (OBJ3D, LIGHT3D, CAMERA3D) never count: their z is a depth in meters, not a draw order.
     */
    static boolean counts(Map<String, String> m) {
        String k = kind(m);
        if (SceneInfo.is3DType(k)) return false;
        return k.equals("CAMERAVIEW") || k.equals("BOX") || k.equals("CIRCLE") || k.equals("CUSTOM") || k.equals("MAP") || k.equals("TEXT") || k.equals("PROGRESS") || k.equals("JOYSTICK") || k.equals("PARTICLE");
    }

    /** A big element that acts as the scene background: covers >= 80% of the frame, is not a label/joystick. */
    static boolean isBackground(Map<String, String> m, float fw, float fh) {
        String k = kind(m);
        if (!(k.equals("BOX") || k.equals("CIRCLE") || k.equals("CUSTOM") || k.equals("MAP"))) return false;
        if (fw <= 0 || fh <= 0) return false;
        float[] r = rect(m);
        float ix = Math.min(r[0] + r[2], fw) - Math.max(r[0], 0), iy = Math.min(r[1] + r[3], fh) - Math.max(r[1], 0);
        if (ix <= 0 || iy <= 0) return false;
        return (ix * iy) >= 0.8f * fw * fh;
    }

    /** The conventional z of an element by its role, -1 = leave it alone. */
    static int roleZ(Map<String, String> m, float fw, float fh) {
        if (!counts(m)) return -1;
        String k = kind(m);
        if (isBackground(m, fw, fh)) return BACKGROUND;
        if (k.equals("PARTICLE")) return EFFECT;
        if (k.equals("JOYSTICK")) return JOYSTICK;
        if (k.equals("TEXT") || k.equals("PROGRESS")) return isUiType(m) ? LABEL : ACTOR + 5;
        if (isUiType(m)) return UI;
        if (k.equals("MAP")) return SCENERY;
        String t = String.valueOf(m.get("type")).toUpperCase(Locale.ROOT);
        return t.equals("DYNAMIC") ? ACTOR : GROUND;
    }

    private static boolean hasZ(JsonObject props) {
        for (String k : props.keySet()) if (k.equalsIgnoreCase("z")) return true;
        return false;
    }

    /**
     * Called right after an element was added and its props applied. When the caller gave no z, the element gets the z of its role
     * (so nothing is hidden by the default "everything is z 0, last created wins"). A background stays at z 0 and the OTHER
     * elements that still sit at z <= 0 are raised to their role z. Returns a short note ("" = nothing changed).
     */
    static String applyOnAdd(TestApp app, LibgdxEditor ed, Actor actor, JsonObject props) {
        try {
            if (hasZ(props)) return "";
            PropertySet<String, Object> ps = PropertySet.getPropertySet(actor);
            LinkedHashMap<String, String> live = Bridge.snapshot(ps);
            float fw = ed.getLogicWidth(), fh = ed.getLogicHeight();
            int z = roleZ(live, fw, fh);
            if (z < 0) return "";
            if (Math.round(f(live, "z", 0)) != z) {
                ed.selectActor(actor);
                PropertyEditing.applyFloat(app, "z", z);
            }
            String note = "z " + z + (z == BACKGROUND ? " (background)" : "");
            if (z == BACKGROUND) {
                // the background must be the lowest: raise every other counted element that is still at z <= 0
                String me = String.valueOf(live.get("name"));
                int raised = 0;
                for (LinkedHashMap<String, String> o : CoreTools.liveElements(ed)) {
                    String par = o.get("parent");
                    boolean child = par != null && !par.isEmpty() && !par.equals("null");
                    if (me.equals(o.get("name")) || !counts(o) || child) continue;
                    if (f(o, "z", 0) > 0) continue;
                    int rz = roleZ(o, fw, fh);
                    if (rz <= 0) continue; // another background / unknown: leave
                    Actor other = Bridge.actor(o.get("name"));
                    if (other == null) continue;
                    ed.selectActor(other);
                    PropertyEditing.applyFloat(app, "z", rz);
                    raised++;
                }
                ed.selectActor(actor);
                if (raised > 0) note += ", raised " + raised + " other element(s) from z 0 to their role z so the background stays behind";
            }
            return note;
        } catch (Throwable t) {
            return "";
        }
    }

    /** layout_check section: draw-order problems. {@code all} = every element of the scene (any order). */
    static void check(List<LinkedHashMap<String, String>> all, float fw, float fh, JsonArray problems) {
        ArrayList<LinkedHashMap<String, String>> els = new ArrayList<>();
        for (LinkedHashMap<String, String> m : all) {
            if ("false".equalsIgnoreCase(m.get("Visible")) || !counts(m)) continue;
            String par = m.get("parent");
            if (par != null && !par.isEmpty() && !par.equals("null")) continue; // children: z is relative to the root of their group/prefab
            els.add(m);
        }
        // 1. background covers (or ties with) the elements it lies under
        for (LinkedHashMap<String, String> bg : els) {
            if (!isBackground(bg, fw, fh)) continue;
            float zb = f(bg, "z", 0);
            ArrayList<String> over = new ArrayList<>(), tie = new ArrayList<>();
            for (LinkedHashMap<String, String> e : els) {
                if (e == bg || isBackground(e, fw, fh)) continue;
                float[] re = rect(e), rb = rect(bg);
                float ix = Math.min(re[0] + re[2], rb[0] + rb[2]) - Math.max(re[0], rb[0]);
                float iy = Math.min(re[1] + re[3], rb[1] + rb[3]) - Math.max(re[1], rb[1]);
                if (ix <= 0 || iy <= 0) continue;
                if (ix * iy < 0.5f * Math.max(1, re[2] * re[3])) continue;
                float ze = f(e, "z", 0);
                if (zb > ze) over.add(e.get("name") + " (z " + Math.round(ze) + ")");
                else if (zb == ze) tie.add(e.get("name"));
            }
            if (!over.isEmpty())
                LayoutCheck.add(problems, "background_covers", bg.get("name"), null, "'" + bg.get("name") + "' fills the scene and has z " + Math.round(zb) + ", HIGHER than " + names(over)
                        + ": it is drawn on top of them (they look missing). Fix: elements order {name:'" + bg.get("name") + "', z:0} and give the others z >= 10 (ground 10, actors 20, UI 100). Higher z = drawn on top.");
            if (!tie.isEmpty())
                LayoutCheck.add(problems, "background_ties", bg.get("name"), null, "'" + bg.get("name") + "' fills the scene and has the SAME z (" + Math.round(zb) + ") as " + names(tie)
                        + ": equal z is drawn in creation order, so the later one wins and may cover the other. Fix: keep the background at z 0 and give the others z >= 10 (ground 10, actors 20, UI 100).");
        }
        // 2. an element completely hidden behind a bigger one with a higher z
        int found = 0;
        for (LinkedHashMap<String, String> small : els) {
            if (found >= 6) break;
            String ks = kind(small);
            if (isBackground(small, fw, fh)) continue;
            float[] rs = rect(small);
            float areaS = Math.max(1, rs[2] * rs[3]);
            for (LinkedHashMap<String, String> big : els) {
                if (big == small) continue;
                String kb = kind(big);
                if (kb.equals("TEXT") || kb.equals("PROGRESS") || kb.equals("PARTICLE") || isBackground(big, fw, fh)) continue;
                if (isUiType(big) != isUiType(small)) continue; // different spaces (screen vs world): positions are not comparable
                float[] rb = rect(big);
                if (rb[2] * rb[3] <= areaS) continue;
                float ix = Math.min(rs[0] + rs[2], rb[0] + rb[2]) - Math.max(rs[0], rb[0]);
                float iy = Math.min(rs[1] + rs[3], rb[1] + rb[3]) - Math.max(rs[1], rb[1]);
                if (ix <= 0 || iy <= 0 || ix * iy < 0.9f * areaS) continue;
                float zs = f(small, "z", 0), zbig = f(big, "z", 0);
                if (ks.equals("TEXT") && !isUiType(small)) continue;
                if (zbig > zs) {
                    LayoutCheck.add(problems, "hidden_behind", small.get("name"), big.get("name"), "'" + small.get("name") + "' (z " + Math.round(zs) + ") lies inside '" + big.get("name") + "' (z " + Math.round(zbig)
                            + ") which is drawn on top of it. Fix: raise '" + small.get("name") + "' to z " + Math.round(zbig + 1) + " or lower '" + big.get("name") + "'.");
                    found++;
                    break;
                }
            }
        }
        // 3. UI drawn under world elements (UI and world are sorted together by z)
        float maxWorld = -1;
        for (LinkedHashMap<String, String> e : els) if (!isUiType(e) && !isBackground(e, fw, fh)) maxWorld = Math.max(maxWorld, f(e, "z", 0));
        if (maxWorld >= 0) {
            ArrayList<String> low = new ArrayList<>();
            for (LinkedHashMap<String, String> e : els) {
                if (!isUiType(e) || kind(e).equals("PARTICLE") || isBackground(e, fw, fh)) continue;
                if (f(e, "z", 0) <= maxWorld) low.add(e.get("name") + " (z " + Math.round(f(e, "z", 0)) + ")");
            }
            if (!low.isEmpty())
                LayoutCheck.add(problems, "ui_under_world", null, null, "UI elements " + names(low) + " have a z not above the highest world element (z " + Math.round(maxWorld)
                        + "). The engine draws UI and world together sorted by z, so UI can be hidden by game objects. Fix: give UI z >= " + UI + " (box 100, TEXT/PROGRESS 110, JOYSTICK 120).");
        }
    }

    private static String names(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < 6; i++) sb.append(i == 0 ? "" : ", ").append(l.get(i));
        if (l.size() > 6) sb.append(" and ").append(l.size() - 6).append(" more");
        return sb.toString();
    }
}
