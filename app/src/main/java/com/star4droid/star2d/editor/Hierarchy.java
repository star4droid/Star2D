package com.star4droid.star2d.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.utils.EditorAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * The parent / child tree of the elements of a scene, as the editor needs it.
 * The source of truth is the "parent" property (a name); {@link PropertySet#getParent()} links are rebuilt from it by {@link #relink}.
 * Everything here runs on the GL thread.
 */
public final class Hierarchy {
    private Hierarchy() {}

    // ---------------------------------------------------------------- reading the tree

    public static boolean isItem(Actor a) {
        return a instanceof EditorItem && a.getName() != null;
    }

    public static PropertySet<String, Object> props(Actor a) {
        return a instanceof EditorItem ? ((EditorItem) a).getPropertySet() : null;
    }

    public static String parentOf(PropertySet<String, Object> ps) {
        if (ps == null || !ps.containsKey("parent")) return "";
        Object v = ps.get("parent");
        return v == null ? "" : v.toString();
    }

    public static String nameOf(PropertySet<String, Object> ps) {
        return ps == null || !ps.containsKey("name") ? "" : String.valueOf(ps.get("name"));
    }

    /** name -> actor of every element of the editor. */
    public static HashMap<String, Actor> byName(LibgdxEditor ed) {
        HashMap<String, Actor> m = new HashMap<>();
        for (Actor a : ed.getActors()) {
            if (!isItem(a)) continue;
            PropertySet<String, Object> ps = props(a);
            if (ps == null) continue;
            String n = nameOf(ps);
            if (!n.isEmpty()) m.put(n, a);
        }
        return m;
    }

    /** Direct children (by the "parent" property), in drawing order of the editor's actor list. */
    public static ArrayList<Actor> childrenOf(LibgdxEditor ed, String name) {
        ArrayList<Actor> out = new ArrayList<>();
        if (name == null || name.isEmpty()) return out;
        for (Actor a : ed.getActors()) {
            if (!isItem(a)) continue;
            PropertySet<String, Object> ps = props(a);
            if (ps != null && name.equals(parentOf(ps)) && !name.equals(nameOf(ps))) out.add(a);
        }
        return out;
    }

    /** All descendants (children, their children...), parents before children. Cycles are cut. */
    public static ArrayList<Actor> descendantsOf(LibgdxEditor ed, String name) {
        ArrayList<Actor> out = new ArrayList<>();
        if (name == null || name.isEmpty()) return out;
        HashMap<String, ArrayList<Actor>> kids = new HashMap<>();
        for (Actor a : ed.getActors()) {
            if (!isItem(a)) continue;
            PropertySet<String, Object> ps = props(a);
            if (ps == null) continue;
            String p = parentOf(ps);
            if (!p.isEmpty()) {
                ArrayList<Actor> l = kids.get(p);
                if (l == null) { l = new ArrayList<>(); kids.put(p, l); }
                l.add(a);
            }
        }
        HashSet<String> seen = new HashSet<>();
        seen.add(name);
        ArrayList<String> queue = new ArrayList<>();
        queue.add(name);
        for (int i = 0; i < queue.size(); i++) {
            ArrayList<Actor> l = kids.get(queue.get(i));
            if (l == null) continue;
            for (Actor c : l) {
                String cn = c.getName();
                if (!seen.add(cn)) continue;
                out.add(c);
                queue.add(cn);
            }
        }
        return out;
    }

    /** Is {@code maybeChild} below {@code ancestor} (or the same element)? */
    public static boolean isSelfOrDescendant(LibgdxEditor ed, String maybeChild, String ancestor) {
        if (maybeChild == null || ancestor == null) return false;
        if (maybeChild.equals(ancestor)) return true;
        HashMap<String, Actor> m = byName(ed);
        String cur = maybeChild;
        int guard = 0;
        while (cur != null && !cur.isEmpty() && guard++ < 1000) {
            Actor a = m.get(cur);
            if (a == null) return false;
            String p = parentOf(props(a));
            if (p.equals(ancestor)) return true;
            cur = p;
        }
        return false;
    }

    /** Rebuilds the PropertySet parent / child links from the "parent" names (names that point nowhere are kept but not linked). */
    public static void relink(LibgdxEditor ed) {
        HashMap<String, Actor> m = byName(ed);
        for (Actor a : m.values()) {
            PropertySet<String, Object> ps = props(a);
            if (ps != null) ps.setParent(null);
        }
        for (Actor a : m.values()) {
            PropertySet<String, Object> ps = props(a);
            if (ps == null) continue;
            ps.getChilds().clear();
        }
        for (Actor a : m.values()) {
            PropertySet<String, Object> ps = props(a);
            if (ps == null) continue;
            String p = parentOf(ps);
            if (p.isEmpty()) continue;
            Actor pa = m.get(p);
            if (pa == null || pa == a) continue; // the name points nowhere: no link (the name is kept)
            PropertySet<String, Object> pps = props(pa);
            if (pps != null && !ps.setParent(pps)) ps.put("parent", ""); // a cycle: cut it
        }
    }

    // ---------------------------------------------------------------- lock / visible cascades

    /** Locked itself or inside a locked group. */
    public static boolean isLocked(PropertySet<String, Object> ps) {
        int guard = 0;
        PropertySet cur = ps;
        while (cur != null && guard++ < 100) {
            if ("true".equals(String.valueOf(cur.get("lock")))) return true;
            cur = cur.getParent();
        }
        return false;
    }

    /** Hidden itself or inside a hidden group. */
    public static boolean isHidden(PropertySet<String, Object> ps) {
        int guard = 0;
        PropertySet cur = ps;
        while (cur != null && guard++ < 100) {
            if ("false".equals(String.valueOf(cur.get("Visible")))) return true;
            cur = cur.getParent();
        }
        return false;
    }

    /** Called every frame by the editor: items inside a hidden group are not drawn (their own "Visible" is untouched). */
    public static void applyVisibility(LibgdxEditor ed) {
        for (Actor a : ed.getActors()) {
            if (!(a instanceof EditorItem)) continue;
            PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
            if (ps == null || ps.getParent() == null) continue;
            boolean visible = !isHidden(ps);
            if (a.isVisible() != visible) a.setVisible(visible);
        }
    }

    // ---------------------------------------------------------------- moving the descendants with their parent

    private static float f(PropertySet<String, Object> ps, String k) {
        try { return Float.parseFloat(String.valueOf(ps.get(k))); } catch (Exception e) { return 0f; }
    }

    /** Moves the elements by (dx, dy) world pixels (the property x / y). */
    public static void move(List<Actor> kids, float dx, float dy) {
        if (dx == 0 && dy == 0) return;
        for (Actor a : kids) {
            PropertySet<String, Object> ps = props(a);
            if (ps == null) continue;
            ps.put("x", f(ps, "x") + dx);
            ps.put("y", f(ps, "y") + dy);
            ((EditorItem) a).update();
        }
    }

    /**
     * Rotates the elements around (cx, cy) by {@code propDelta} degrees of the "rotation" property
     * (the property turns clockwise, the actor turns the other way). Positions and rotations are updated.
     */
    public static void rotate(List<Actor> kids, float cx, float cy, float propDelta) {
        if (propDelta == 0) return;
        double phi = Math.toRadians(-propDelta);
        float cos = (float) Math.cos(phi), sin = (float) Math.sin(phi);
        for (Actor a : kids) {
            PropertySet<String, Object> ps = props(a);
            if (ps == null) continue;
            float w = a.getWidth(), h = a.getHeight();
            float ax = a.getX() + w * 0.5f - cx, ay = a.getY() + h * 0.5f - cy;
            float nx = cx + ax * cos - ay * sin, ny = cy + ax * sin + ay * cos;
            ps.put("x", nx - w * 0.5f);
            ps.put("y", ny - h * 0.5f);
            float r = f(ps, "rotation") + propDelta;
            while (r >= 360) r -= 360;
            while (r < 0) r += 360;
            ps.put("rotation", r);
            ((EditorItem) a).update();
        }
    }

    /** x, y, rotation of every element, as "name|x|y|rotation" (for undo). */
    public static ArrayList<String[]> snapshot(List<Actor> kids) {
        ArrayList<String[]> out = new ArrayList<>();
        for (Actor a : kids) {
            PropertySet<String, Object> ps = props(a);
            if (ps == null) continue;
            out.add(new String[]{a.getName(), ps.getString("x"), ps.getString("y"), ps.getString("rotation")});
        }
        return out;
    }

    /**
     * One undo step for everything that moved: the main element (already described by {@code main}, may be null)
     * and the descendants, compared with their snapshot taken before the move.
     */
    public static void pushMove(LibgdxEditor ed, EditorAction main, ArrayList<String[]> before, String message) {
        ArrayList<EditorAction> subs = new ArrayList<>();
        if (main != null) subs.add(main);
        for (String[] b : before) {
            Actor a = ed.findActor(b[0]);
            PropertySet<String, Object> ps = a == null ? null : props(a);
            if (ps == null) continue;
            if (ps.getString("x").equals(b[1]) && ps.getString("y").equals(b[2]) && ps.getString("rotation").equals(b[3])) continue;
            EditorAction act = EditorAction.propertiesChangedQuiet(ed, b[0], message,
                    new String[]{"x", ps.getString("x"), "y", ps.getString("y"), "rotation", ps.getString("rotation")},
                    new String[]{"x", b[1], "y", b[2], "rotation", b[3]});
            if (act != null) subs.add(act.updateItemProperties());
        }
        if (subs.isEmpty()) return;
        if (subs.size() == 1 && main != null) { ed.pushUndoEvent(main); return; }
        ed.pushUndoEvent(EditorAction.batch(ed, message, subs));
    }

    // ---------------------------------------------------------------- changing the parent

    /** null when {@code child} may get {@code parent} as parent (empty parent = none), else the reason. */
    public static String checkParent(LibgdxEditor ed, String child, String parent) {
        if (parent == null || parent.isEmpty()) return null;
        if (child.equals(parent)) return "An element can't be its own parent";
        HashMap<String, Actor> m = byName(ed);
        if (!m.containsKey(parent)) return "No element named '" + parent + "'";
        Actor pa = m.get(parent);
        PropertySet<String, Object> pps = props(pa);
        if (pps != null && "LIGHT".equals(String.valueOf(pps.get("TYPE")))) return "A light can't be a parent";
        if (isSelfOrDescendant(ed, parent, child)) return "'" + parent + "' is inside '" + child + "' (that would make a cycle)";
        return null;
    }

    /** Sets the parent of an element. Its world position does not change. Returns an error or null. Undoable (not pushed when {@code push} is false). */
    public static String setParent(LibgdxEditor ed, String child, String parent, boolean push) {
        Actor a = ed.findActor(child);
        PropertySet<String, Object> ps = a == null ? null : props(a);
        if (ps == null) return "No element named '" + child + "'";
        String err = checkParent(ed, child, parent);
        if (err != null) return err;
        String old = parentOf(ps);
        if (old.equals(parent == null ? "" : parent)) return null;
        ps.put("parent", parent == null ? "" : parent);
        relink(ed);
        if (push) {
            EditorAction act = EditorAction.propertyChangedRaw(ed, child, "parent", "parent", parent == null ? "" : parent, old);
            if (act != null) act.updateEditorProperties();
        }
        refreshLists(ed);
        return null;
    }

    public static void refreshLists(LibgdxEditor ed) {
        try {
            if (ed.getControlLayer() != null && ed.getControlLayer().getBodiesList() != null)
                ed.getControlLayer().getBodiesList().update(true);
        } catch (Throwable ignored) {}
    }

    /** A free element name that starts with {@code base} (Group1, Group2...). */
    public static String freeName(LibgdxEditor ed, String base) {
        int n = 1;
        while (ed.findActor(base + n) != null) n++;
        return base + n;
    }

    public static void log(String s) {
        try { com.star4droid.star2d.logs.Logs.editor("hierarchy", s + "\n"); } catch (Throwable ignored) {}
    }
}
