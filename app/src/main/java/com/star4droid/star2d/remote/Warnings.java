package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.ui.Bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Cheap check run after editing, so the AI learns immediately that a TEXT too narrow for its content wraps onto several lines.
 * All methods: GL thread.
 */
final class Warnings {
    private Warnings() {}

    private static BitmapFont font;

    private static BitmapFont font() {
        if (font == null) font = new BitmapFont(Gdx.files.internal("files/default.fnt"));
        return font;
    }

    private static float f(LinkedHashMap<String, String> m, String k, float def) {
        String v = m.get(k);
        if (v == null || v.isEmpty()) return def;
        try {
            return Float.parseFloat(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean isText(LinkedHashMap<String, String> m) {
        return "TEXT".equalsIgnoreCase(String.valueOf(m.get("TYPE")));
    }

    /** Wrapping check for TEXT elements (all when names is null). The text is measured with the engine's default font. */
    static List<String> text(LibgdxEditor ed, Collection<String> names) {
        ArrayList<String> out = new ArrayList<>();
        try {
            for (LinkedHashMap<String, String> m : CoreTools.liveElements(ed)) {
                if (!isText(m)) continue;
                String name = m.get("name");
                if (names != null && !names.contains(name)) continue;
                String txt = m.get("Text") == null ? "" : m.get("Text");
                String ft = m.get("font");
                if (txt.isEmpty() || (ft != null && !ft.isEmpty())) continue; // custom fonts are not measured
                float fs = f(m, "Font Scale", 1f), w = f(m, "width", 0f), h = f(m, "height", 0f);
                if (w <= 0) continue;
                GlyphLayout gl = new GlyphLayout(font(), txt);
                float need = gl.width * fs, lineH = font().getLineHeight() * fs;
                int explicitLines = txt.split("\n", -1).length;
                if (need > w + 0.5f) {
                    int wraps = (int) Math.ceil(need / w);
                    out.add("TEXT '" + name + "' (\"" + shorten(txt) + "\", Font Scale " + trim(fs) + ") needs about " + Math.round(need + 6)
                            + " px of width but width is " + Math.round(w) + ": it will wrap onto ~" + wraps + " lines. Fix: set width >= " + Math.round(need + 6)
                            + " or Font Scale <= " + trim(fs * w / need * 0.95f) + ".");
                } else if (lineH * explicitLines > h + 0.5f && h > 0) {
                    out.add("TEXT '" + name + "' (\"" + shorten(txt) + "\") needs about " + Math.round(lineH * explicitLines) + " px of height but height is "
                            + Math.round(h) + " (it may be cut off or overlap what is below). Raise height or lower Font Scale.");
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /**
     * Bounds check for the elements just added/changed: completely outside the scene frame, sticking out a lot, wrong orientation
     * numbers (fits the frame of the OTHER orientation), absurd sizes. Groups/cameras/lights/maps and children of groups are skipped
     * (their coordinates are relative or the level may scroll). The scene frame is the START view: big scrolling levels may legitimately go beyond it.
     */
    static List<String> bounds(LibgdxEditor ed, Collection<String> names) {
        ArrayList<String> out = new ArrayList<>();
        try {
            float fw = ed.getLogicWidth(), fh = ed.getLogicHeight();
            if (fw <= 0 || fh <= 0) return out;
            boolean land = ed.isLandscape();
            String frame = Math.round(fw) + " x " + Math.round(fh) + " (" + (land ? "LANDSCAPE" : "PORTRAIT") + ", x 0.." + Math.round(fw) + ", y 0.." + Math.round(fh) + ", y up)";
            for (LinkedHashMap<String, String> m : CoreTools.liveElements(ed)) {
                String name = m.get("name");
                if (names != null && !names.contains(name)) continue;
                String k = String.valueOf(m.get("TYPE")).toUpperCase();
                if (!(k.equals("BOX") || k.equals("CIRCLE") || k.equals("CUSTOM") || k.equals("TEXT") || k.equals("PROGRESS") || k.equals("JOYSTICK"))) continue;
                String par = m.get("parent");
                if (par != null && !par.isEmpty() && !par.equals("null")) continue;
                float r = f(m, "radius", 0);
                float w = f(m, "width", r * 2), h = f(m, "height", r * 2), x = f(m, "x", 0), y = f(m, "y", 0);
                if (w <= 0 || h <= 0) {
                    if (k.equals("CIRCLE") || w > 0 || h > 0) out.add("'" + name + "' has size " + Math.round(w) + " x " + Math.round(h) + ": it is invisible/unusable. Set width and height (radius for CIRCLE).");
                    continue;
                }
                float ox = Math.min(x + w, fw) - Math.max(x, 0), oy = Math.min(y + h, fh) - Math.max(y, 0);
                boolean ui = "UI".equalsIgnoreCase(m.get("type")) || k.equals("JOYSTICK");
                // the numbers belong to the other orientation's frame?
                boolean fitsOther = x >= 0 && y >= 0 && x + w <= fh + 0.5f && y + h <= fw + 0.5f;
                boolean fitsMine = x >= -0.5f && y >= -0.5f && x + w <= fw + 0.5f && y + h <= fh + 0.5f;
                if (ox <= 0 || oy <= 0) {
                    String msg = "'" + name + "' (x " + Math.round(x) + ", y " + Math.round(y) + ", " + Math.round(w) + "x" + Math.round(h) + ") is completely OUTSIDE the scene frame " + frame + ".";
                    if (fitsOther && !fitsMine) msg += " These numbers fit a " + (land ? "PORTRAIT" : "LANDSCAPE") + " frame: you probably designed for the wrong orientation. Check status.frame, then reposition (x 0.." + Math.round(fw - w) + ", y 0.." + Math.round(fh - h) + ").";
                    else msg += ui ? " Move it inside." : " That is only right for a scrolling level (camera follows the player); otherwise move it inside.";
                    out.add(msg);
                } else if (ox * oy < 0.5f * w * h) {
                    out.add("'" + name + "' sticks out of the scene frame " + frame + ": only " + Math.round(100 * ox * oy / (w * h)) + "% of it is visible at the start." + (fitsOther && !fitsMine ? " Its numbers fit a " + (land ? "PORTRAIT" : "LANDSCAPE") + " frame: wrong orientation?" : ""));
                }
                float big = Math.max(fw, fh);
                if (!ui && !k.equals("TEXT") && (w > 8 * big || h > 8 * big))
                    out.add("'" + name + "' is " + Math.round(w) + " x " + Math.round(h) + " px, over 8x the scene: probably a typo in width/height.");
                else if (ui && (w > 1.5f * fw + 1 || h > 1.5f * fh + 1))
                    out.add("UI element '" + name + "' is " + Math.round(w) + " x " + Math.round(h) + " px, larger than the screen " + frame + ".");
                if (out.size() >= 10) break;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static final java.util.Set<String> SPEED_BLOCKS = new java.util.HashSet<>(java.util.Arrays.asList(
            "jump", "setVelocityX", "setVelocityY", "moveToward", "moveWithJoystick", "walkWithJoystick"));

    /** Sign check of the speed blocks of an events "set": jump power must be positive (up). */
    static List<String> blocks(com.google.gson.JsonElement tree) {
        ArrayList<String> out = new ArrayList<>();
        scanBlocks(tree, out);
        return out;
    }

    private static void scanBlocks(com.google.gson.JsonElement e, List<String> out) {
        if (e == null || out.size() >= 4) return;
        if (e.isJsonArray()) {
            for (com.google.gson.JsonElement x : e.getAsJsonArray()) scanBlocks(x, out);
        } else if (e.isJsonObject()) {
            com.google.gson.JsonObject o = e.getAsJsonObject();
            String b = o.has("block") && o.get("block").isJsonPrimitive() ? o.get("block").getAsString() : "";
            if (SPEED_BLOCKS.contains(b) && o.has("fields") && o.get("fields").isJsonObject()) {
                com.google.gson.JsonObject f = o.getAsJsonObject("fields");
                for (String k : new String[]{"power", "speed"}) {
                    if (!f.has(k) || !f.get(k).isJsonPrimitive() || !f.get(k).getAsJsonPrimitive().isNumber()) continue;
                    double v = f.get(k).getAsDouble();
                    if (b.equals("jump") && v <= 0)
                        out.add("block jump power " + trim((float) v) + ": Y is UP, so the jump power must be POSITIVE (this one moves the element " + (v < 0 ? "DOWN" : "nowhere") + "). Use about 400-600; physics_info {jump_height:N} gives the number.");
                }
            }
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> en : o.entrySet())
                if (!en.getKey().equals("fields")) scanBlocks(en.getValue(), out);
        }
    }

    private static String shorten(String s) {
        s = s.replace("\n", "\\n");
        return s.length() > 18 ? s.substring(0, 17) + "…" : s;
    }

    private static String trim(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.US, "%.2f", v);
    }
}
