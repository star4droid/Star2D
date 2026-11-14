package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.PlayerItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;

/**
 * Makes the parent / child links of the runtime real: a child keeps its place inside the parent
 * (position and rotation) and is hidden when the parent is hidden.
 *
 * Rules (see assets/ai/docs/groups.md):
 *  - the offset of a child is recorded when the link is first seen (so it keeps the world position it had),
 *  - world child of world parent: center = parent center + rotate(offset, parent rotation); rotation = parent rotation + relative rotation,
 *  - STATIC / KINEMATIC bodies are moved with setTransform, DYNAMIC bodies are never driven,
 *  - UI child of a world parent is world-anchored: it follows the projected position of the parent on the screen (no rotation),
 *  - world child of a UI parent is not driven,
 *  - UI child of UI parent follows with rotation.
 *
 * Three hooks are called by StageImp.draw(): {@link #placeUi()} at the start (after the camera moved),
 * {@link #prepare()} right before the physics step and {@link #follow()} after the step and the scene's onDraw.
 * Any exception is logged once and switches the follower off, the game keeps running.
 */
public class GroupFollower {
    private static final int MODE_NONE = 0, MODE_SAME = 1, MODE_UI_ON_WORLD = 2;

    private static class Node {
        PlayerItem item;
        Actor actor;
        Node parent;
        boolean recorded;
        int mode = MODE_NONE;
        float ox, oy, rel;
        boolean ui;
        // last known pose (in the space of the node: world units for world nodes, ui pixels for ui nodes)
        float cx, cy, rot;
        // visibility cascade
        boolean ownVisible = true, hidden, bodyWasActive = true, eff = true;
        boolean warned;
        int depth;
    }

    private final StageImp stage;
    private boolean disabled;
    private final ArrayList<Node> nodes = new ArrayList<>();
    private final IdentityHashMap<PlayerItem, Node> map = new IdentityHashMap<>();
    private int builtVersion = -1, builtActors = -1;
    private final Vector3 tmp3 = new Vector3();
    private final Vector2 tmp2 = new Vector2();

    public GroupFollower(StageImp stage) {
        this.stage = stage;
    }

    // ---------------------------------------------------------------- hooks

    /** Start of the frame, after the camera was moved: world anchored UI children (and the ui subtree) are placed. */
    public void placeUi() {
        if (disabled || nodes.isEmpty()) return;
        try {
            for (int i = 0; i < nodes.size(); i++) {
                Node n = nodes.get(i);
                if (!n.ui || !alive(n)) continue;
                step(n, true);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Right before the physics step: rebuild the graph if a link changed and record the offsets of new links. */
    public void prepare() {
        if (disabled) return;
        try {
            int actors = count();
            if (builtVersion != ChildsHolder.linkVersion || builtActors != actors) rebuild();
            builtActors = actors;
            for (int i = 0; i < nodes.size(); i++) {
                Node n = nodes.get(i);
                if (!n.recorded && alive(n)) record(n);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** After the physics step and the scene's onDraw: children are put where their parents are. */
    public void follow() {
        if (disabled || nodes.isEmpty()) return;
        try {
            for (int i = 0; i < nodes.size(); i++) {
                Node n = nodes.get(i);
                if (!alive(n)) continue;
                step(n, false);
                cascadeVisible(n);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ---------------------------------------------------------------- graph

    private int count() {
        int c = 0;
        if (stage.GameStage != null) c += stage.GameStage.getActors().size;
        if (stage.UiStage != null) c += stage.UiStage.getActors().size;
        return c;
    }

    private boolean alive(Node n) {
        return n.actor != null && n.actor.getStage() != null && !n.item.getChildsHolder().destroyed;
    }

    private boolean involved(PlayerItem item) {
        ChildsHolder h = item.getChildsHolder();
        return h != null && (h.getParentHolder() != null || !h.childs.isEmpty());
    }

    private void collect(Stage st, ArrayList<PlayerItem> out) {
        if (st == null) return;
        for (Actor a : st.getActors()) {
            if (a instanceof PlayerItem) out.add((PlayerItem) a);
        }
    }

    private void rebuild() {
        builtVersion = ChildsHolder.linkVersion;
        ArrayList<PlayerItem> all = new ArrayList<>();
        collect(stage.GameStage, all);
        collect(stage.UiStage, all);

        IdentityHashMap<PlayerItem, Node> fresh = new IdentityHashMap<>();
        ArrayList<Node> list = new ArrayList<>();
        for (PlayerItem item : all) {
            if (!involved(item) || item.getChildsHolder().destroyed) continue;
            Node n = map.get(item);
            if (n == null) {
                n = new Node();
                n.item = item;
                n.actor = item.getActor();
            }
            n.ui = stage.UiStage != null && n.actor.getStage() == stage.UiStage;
            fresh.put(item, n);
            list.add(n);
        }
        // parents
        for (Node n : list) {
            Node oldParent = n.parent;
            ChildsHolder ph = n.item.getChildsHolder().getParentHolder();
            n.parent = ph == null ? null : fresh.get(ph.getPlayerItem());
            if (n.parent != oldParent) {
                restore(n);
                n.recorded = false;
                n.mode = MODE_NONE;
            }
        }
        // nodes that left the graph get their visibility back
        for (Node old : map.values()) {
            if (!fresh.containsKey(old.item)) restore(old);
        }
        // depth (parents first), refuse cycles
        for (Node n : list) {
            int d = 0;
            Node p = n.parent;
            while (p != null && d < 64) { d++; p = p.parent; }
            if (d >= 64) { n.parent = null; n.recorded = true; n.mode = MODE_NONE; d = 0; }
            n.depth = d;
        }
        Collections.sort(list, (a, b) -> Integer.compare(a.depth, b.depth));
        map.clear();
        map.putAll(fresh);
        nodes.clear();
        nodes.addAll(list);
    }

    /** Gives a node hidden by its parent its own visibility back. */
    private void restore(Node n) {
        if (!n.hidden) return;
        try {
            Body b = n.item.getBody();
            if (b != null) b.setActive(n.bodyWasActive);
            n.actor.setVisible(n.ownVisible);
        } catch (Throwable ignored) {}
        n.hidden = false;
    }

    // ---------------------------------------------------------------- poses

    /** Reads the real pose of the item (the body when it has one, the actor otherwise). */
    private void observe(Node n) {
        Body b = n.item.getBody();
        if (b != null) {
            float[] off = n.item.getBodyOffset();
            n.cx = b.getPosition().x - off[0];
            n.cy = b.getPosition().y - off[1];
            n.rot = (float) Math.toDegrees(b.getAngle());
        } else {
            // visual center of the actor: it turns around its origin (not always the middle)
            Actor a = n.actor;
            float ox = a.getOriginX(), oy = a.getOriginY();
            double rad = Math.toRadians(a.getRotation());
            float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
            float dx = a.getWidth() * 0.5f - ox, dy = a.getHeight() * 0.5f - oy;
            n.cx = a.getX() + ox + c * dx - s * dy;
            n.cy = a.getY() + oy + s * dx + c * dy;
            n.rot = a.getRotation();
        }
    }

    private boolean project(float wx, float wy, Vector2 out) {
        if (stage.GameStage == null || stage.UiStage == null) return false;
        Camera cam = stage.GameStage.getCamera();
        Viewport gv = stage.GameStage.getViewport();
        tmp3.set(wx, wy, 0);
        cam.project(tmp3, gv.getScreenX(), gv.getScreenY(), gv.getScreenWidth(), gv.getScreenHeight());
        out.set(tmp3.x, Gdx.graphics.getHeight() - 1 - tmp3.y);
        stage.UiStage.getViewport().unproject(out);
        return true;
    }

    private boolean drivable(Node n) {
        Body b = n.item.getBody();
        return b == null || b.getType() != BodyDef.BodyType.DynamicBody;
    }

    /** Remembers where the child is inside its parent. */
    private void record(Node n) {
        n.recorded = true;
        n.mode = MODE_NONE;
        if (n.parent == null) return;
        Node p = n.parent;
        observe(n);
        observe(p);
        if (!drivable(n)) {
            warnOnce(n, "dynamic child '" + n.actor.getName() + "' is not driven by its parent");
            return;
        }
        if (n.ui == p.ui) {
            double a = Math.toRadians(-p.rot);
            float dx = n.cx - p.cx, dy = n.cy - p.cy;
            n.ox = (float) (Math.cos(a) * dx - Math.sin(a) * dy);
            n.oy = (float) (Math.sin(a) * dx + Math.cos(a) * dy);
            n.rel = n.rot - p.rot;
            n.mode = MODE_SAME;
        } else if (n.ui) {
            if (!project(p.cx, p.cy, tmp2)) { n.recorded = false; return; }
            n.ox = n.cx - tmp2.x;
            n.oy = n.cy - tmp2.y;
            n.rel = 0;
            n.mode = MODE_UI_ON_WORLD;
        } else {
            warnOnce(n, "world child '" + n.actor.getName() + "' of the UI parent '" + p.actor.getName() + "' is not driven");
        }
    }

    /** Computes (and applies) the pose of a node from its parent. Roots and not driven nodes are only observed. */
    private void step(Node n, boolean uiPass) {
        Node p = n.parent;
        if (p == null || !n.recorded || n.mode == MODE_NONE || p.actor.getStage() == null || !drivable(n)) {
            observe(n);
            return;
        }
        float wx, wy, wr;
        if (n.mode == MODE_UI_ON_WORLD) {
            if (!uiPass) return; // placed at the start of the next frame, when the camera is final
            if (!project(p.cx, p.cy, tmp2)) return;
            wx = tmp2.x + n.ox;
            wy = tmp2.y + n.oy;
            wr = n.actor.getRotation();
        } else {
            double a = Math.toRadians(p.rot);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            wx = p.cx + cos * n.ox - sin * n.oy;
            wy = p.cy + sin * n.ox + cos * n.oy;
            wr = p.rot + n.rel;
        }
        place(n, wx, wy, wr);
    }

    private void place(Node n, float wx, float wy, float wr) {
        Body b = n.item.getBody();
        if (b != null) {
            float[] off = n.item.getBodyOffset();
            float bx = wx + off[0], by = wy + off[1];
            float ang = (float) Math.toRadians(wr);
            if (Math.abs(b.getPosition().x - bx) > 1e-5f || Math.abs(b.getPosition().y - by) > 1e-5f
                    || Math.abs(b.getAngle() - ang) > 1e-5f) {
                b.setTransform(bx, by, ang);
            }
            if (b.getType() == BodyDef.BodyType.KinematicBody) {
                b.setLinearVelocity(0, 0);
                b.setAngularVelocity(0);
            }
        }
        Actor a = n.actor;
        float ox = a.getOriginX(), oy = a.getOriginY();
        double rad = Math.toRadians(wr);
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        float dx = a.getWidth() * 0.5f - ox, dy = a.getHeight() * 0.5f - oy;
        a.setPosition(wx - ox - (c * dx - s * dy), wy - oy - (s * dx + c * dy));
        a.setRotation(wr);
        n.cx = wx;
        n.cy = wy;
        n.rot = wr;
    }

    // ---------------------------------------------------------------- visibility

    private void cascadeVisible(Node n) {
        boolean parentEff = n.parent == null || n.parent.eff;
        Actor a = n.actor;
        if (parentEff) {
            if (n.hidden) {
                restore(n);
            } else {
                n.ownVisible = a.isVisible();
            }
            n.eff = a.isVisible();
        } else {
            if (!n.hidden) {
                n.ownVisible = a.isVisible();
                n.hidden = true;
                Body b = n.item.getBody();
                if (b != null) {
                    n.bodyWasActive = b.isActive();
                    b.setActive(false);
                }
                a.setVisible(false);
            } else if (a.isVisible()) {
                // someone showed it on purpose while the parent is hidden: remember it, keep it hidden for now
                n.ownVisible = true;
                a.setVisible(false);
            }
            n.eff = false;
        }
    }

    // ---------------------------------------------------------------- logging

    private void warnOnce(Node n, String msg) {
        if (n.warned) return;
        n.warned = true;
        try { Gdx.app.log("GroupFollower", msg); } catch (Throwable ignored) {}
    }

    private void fail(Throwable t) {
        disabled = true;
        try {
            Gdx.app.error("GroupFollower", "parent following switched off: " + t);
            com.star4droid.template.Utils.GameLog.write("group_follower", "GroupFollower disabled: " + android.util.Log.getStackTraceString(t) + "\n");
        } catch (Throwable ignored) {}
    }
}
