package com.star4droid.star2d.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.Utils.Pivot;

import java.util.Locale;

/**
 * Editing the pivot of the selected element (pencil beside Pivot in 2D, beside Anchor in 3D). 2D: the Pivot point
 * (0..1 of the size) is dragged with one finger anywhere on the canvas. 3D: the Anchor (model units) moves along the
 * element's own axes by dragging its arrows; the element stays where it is on screen (its position follows the anchor
 * when the change is applied). "Bounds" keeps the point inside the element. Done applies with one undo step, Cancel
 * changes nothing. Rendering thread.
 */
public final class PivotEdit implements CanvasTool {
    /** Values changed (text for the overlay). */
    public interface Listener { void changed(String info); }

    private final LibgdxEditor ed;
    public volatile Listener listener;
    private boolean active, d3;
    private Actor target;
    /** 2D: pivot as 0..1 of the element's size (y up). */
    private float fx, fy;
    private String[] start2D;
    /** 3D: anchor in model units. */
    private final Vector3 anchor = new Vector3(), startAnchor = new Vector3();
    public boolean bounds = true;
    private final Vector2 grab = new Vector2(), tmp2 = new Vector2();

    PivotEdit(LibgdxEditor ed) {
        this.ed = ed;
    }

    @Override public boolean active() { return active; }
    public boolean is3D() { return d3; }
    @Override public boolean navigate() { return false; }
    @Override public float groundHeight() { return 0; }

    /** Result of {@link #start}: null = nothing to edit, else {3D?, bounds on?}. */
    public boolean[] start() {
        Actor a = ed.getSelectedActor();
        com.star4droid.star2d.editor.three.Editor3D e3 = ed.get3D();
        if (a instanceof Editor3DItem) {
            Editor3DItem it = (Editor3DItem) a;
            if (it.obj == null || e3 == null || e3.edit2D) return null;
            target = a;
            d3 = true;
            it.obj.getAnchor(anchor);
            startAnchor.set(anchor);
            BoundingBox b = it.obj.localBounds();
            bounds = b.contains(anchor) || anchor.isZero();
            // fly to the element: it fills about half the view
            Vector3 c = it.obj.center(new Vector3());
            float size = Math.max(0.5f, Math.max(it.obj.getSizeX(), Math.max(it.obj.getSizeY(), it.obj.getSizeZ())));
            e3.flyTo(c, size * 2.2f);
        } else if (a instanceof EditorItem && a.getWidth() > 0 && a.getHeight() > 0) {
            PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
            if (ps == null || !ps.containsKey("Pivot")) return null;
            target = a;
            d3 = false;
            float[] f = Pivot.fraction(ps.getString("Pivot"), num(ps.getString("Pivot X"), 0.5f), num(ps.getString("Pivot Y"), 0.5f));
            fx = f[0];
            fy = f[1];
            start2D = new String[]{"Pivot", ps.getString("Pivot"), "Pivot X", ps.getString("Pivot X"), "Pivot Y", ps.getString("Pivot Y")};
            bounds = fx >= -1e-4f && fx <= 1.0001f && fy >= -1e-4f && fy <= 1.0001f;
            Vector2 c = a.localToStageCoordinates(new Vector2(a.getWidth() / 2f, a.getHeight() / 2f));
            OrthographicCamera cam = (OrthographicCamera) ed.getCamera();
            float zw = a.getWidth() * Math.abs(a.getScaleX()) / Math.max(1f, cam.viewportWidth * 0.5f);
            float zh = a.getHeight() * Math.abs(a.getScaleY()) / Math.max(1f, cam.viewportHeight * 0.45f);
            ed.cameraController.moveTo(c.x, c.y, MathUtils.clamp(Math.max(zw, zh), 0.05f, 20f), 0.45f);
        } else return null;
        active = true;
        changed();
        return new boolean[]{d3, bounds};
    }

    private static float num(String s, float def) {
        try { return Float.parseFloat(s); } catch (Throwable e) { return def; }
    }

    private Object3D obj() {
        return target instanceof Editor3DItem ? ((Editor3DItem) target).obj : null;
    }

    /** Stops without changing anything. */
    public void cancel() {
        // CanvasTool.cancel (a second finger): only the started drag is dropped, the session goes on
    }

    public void stop() {
        active = false;
        target = null;
        listener = null;
    }

    // ------------------------------------------------------------------ values

    /** Chooses min (0), center (1) or max (2) of the element on one axis (0 x, 1 y, 2 z). */
    public void preset(int axis, int which) {
        if (!active) return;
        if (d3) {
            Object3D o = obj();
            if (o == null) return;
            BoundingBox b = o.localBounds();
            float lo = axis == 0 ? b.min.x : (axis == 1 ? b.min.y : b.min.z), hi = axis == 0 ? b.max.x : (axis == 1 ? b.max.y : b.max.z);
            float v = which == 0 ? lo : (which == 2 ? hi : (lo + hi) / 2f);
            if (axis == 0) anchor.x = v; else if (axis == 1) anchor.y = v; else anchor.z = v;
        } else {
            float v = which * 0.5f;
            if (axis == 0) fx = v; else if (axis == 1) fy = v;
        }
        changed();
    }

    /** Which preset the current value matches on an axis: 0, 1, 2 or -1. */
    public int presetOf(int axis) {
        if (d3) {
            Object3D o = obj();
            if (o == null) return -1;
            BoundingBox b = o.localBounds();
            float lo = axis == 0 ? b.min.x : (axis == 1 ? b.min.y : b.min.z), hi = axis == 0 ? b.max.x : (axis == 1 ? b.max.y : b.max.z);
            float v = axis == 0 ? anchor.x : (axis == 1 ? anchor.y : anchor.z), e = Math.max(1e-4f, (hi - lo) * 0.002f);
            if (Math.abs(v - lo) < e) return 0;
            if (Math.abs(v - (lo + hi) / 2f) < e) return 1;
            if (Math.abs(v - hi) < e) return 2;
            return -1;
        }
        if (axis > 1) return -1;
        float v = axis == 0 ? fx : fy;
        for (int i = 0; i < 3; i++) if (Math.abs(v - i * 0.5f) < 1e-3f) return i;
        return -1;
    }

    public void setBounds(boolean on) {
        bounds = on;
        clamp();
        changed();
    }

    private void clamp() {
        if (!bounds) return;
        if (d3) {
            Object3D o = obj();
            if (o == null) return;
            BoundingBox b = o.localBounds();
            anchor.set(MathUtils.clamp(anchor.x, b.min.x, b.max.x), MathUtils.clamp(anchor.y, b.min.y, b.max.y), MathUtils.clamp(anchor.z, b.min.z, b.max.z));
        } else {
            fx = MathUtils.clamp(fx, 0f, 1f);
            fy = MathUtils.clamp(fy, 0f, 1f);
        }
    }

    private void changed() {
        Listener l = listener;
        if (l != null) l.changed(info());
    }

    public String info() {
        if (d3) return String.format(Locale.US, "X %.3f   Y %.3f   Z %.3f", anchor.x, anchor.y, anchor.z);
        return String.format(Locale.US, "X %d%%   Y %d%%", Math.round(fx * 100), Math.round(fy * 100));
    }

    // ------------------------------------------------------------------ 3D

    /** Where the new anchor is in the world now (the element's current matrix). */
    public Vector3 anchorWorld(Vector3 out) {
        Object3D o = obj();
        if (o == null) return out.setZero();
        o.sync3D();
        return out.set(anchor).mul(o.transform);
    }

    /** Axis i of the element (rotated, unit length). */
    public Vector3 axis(int i, Vector3 out) {
        Object3D o = obj();
        out.set(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0);
        if (o != null) out.mul(o.rotation);
        return out;
    }

    /** Moves the anchor by {@code meters} along the element's axis i (the drawn arrow). */
    public void moveAlong(int i, float meters) {
        Object3D o = obj();
        if (o == null) return;
        float s = i == 0 ? o.scale.x : (i == 1 ? o.scale.y : o.scale.z);
        if (Math.abs(s) < 1e-4f) s = s < 0 ? -1e-4f : 1e-4f;
        float d = meters / s;
        if (i == 0) anchor.x += d; else if (i == 1) anchor.y += d; else anchor.z += d;
        clamp();
        changed();
    }

    public BoundingBox bounds3D() {
        Object3D o = obj();
        return o == null ? null : o.localBounds();
    }

    public Object3D object3D() {
        return obj();
    }

    // ------------------------------------------------------------------ 2D (CanvasTool: stage coordinates)

    private Vector2 pivotStage(Vector2 out) {
        return target.localToStageCoordinates(out.set(fx * target.getWidth(), fy * target.getHeight()));
    }

    @Override
    public void down(float x, float y) {
        if (!active || d3 || target == null) return;
        // one finger anywhere: the point follows the finger's movement (no need to hit the small handle)
        pivotStage(tmp2);
        grab.set(tmp2.x - x, tmp2.y - y);
    }

    @Override
    public void drag(float x, float y) {
        if (!active || d3 || target == null) return;
        Vector2 l = target.stageToLocalCoordinates(tmp2.set(x + grab.x, y + grab.y));
        fx = l.x / target.getWidth();
        fy = l.y / target.getHeight();
        clamp();
        changed();
    }

    @Override
    public void up(float x, float y) {
    }

    /** Element frame and the pivot handle (2D canvas). */
    public void draw2D(ShapeRenderer sr, OrthographicCamera cam) {
        if (!active || d3 || target == null) return;
        float w = target.getWidth(), h = target.getHeight(), z = cam.zoom;
        Vector2 a = target.localToStageCoordinates(new Vector2(0, 0)), b = target.localToStageCoordinates(new Vector2(w, 0)),
                c = target.localToStageCoordinates(new Vector2(w, h)), d = target.localToStageCoordinates(new Vector2(0, h));
        Vector2 p = pivotStage(new Vector2());
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glLineWidth(2f);
        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(bounds ? BOUNDS_ON : BOUNDS_OFF);
        sr.line(a, b); sr.line(b, c); sr.line(c, d); sr.line(d, a);
        // thirds: helps aiming at the edges and the middle
        sr.setColor(0.6f, 0.75f, 1f, 0.25f);
        for (float t : new float[]{0.5f}) {
            sr.line(target.localToStageCoordinates(new Vector2(w * t, 0)), target.localToStageCoordinates(new Vector2(w * t, h)));
            sr.line(target.localToStageCoordinates(new Vector2(0, h * t)), target.localToStageCoordinates(new Vector2(w, h * t)));
        }
        float r = 16f * z;
        sr.setColor(Color.WHITE);
        sr.line(p.x - r * 1.8f, p.y, p.x + r * 1.8f, p.y);
        sr.line(p.x, p.y - r * 1.8f, p.x, p.y + r * 1.8f);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(Color.WHITE);
        sr.circle(p.x, p.y, r + 3f * z, 28);
        sr.setColor(HANDLE);
        sr.circle(p.x, p.y, r, 28);
        sr.setColor(Color.WHITE);
        sr.circle(p.x, p.y, r * 0.35f, 16);
        sr.end();
        Gdx.gl.glLineWidth(1f);
    }

    static final Color HANDLE = new Color(1f, 0.6f, 0.1f, 1f), BOUNDS_ON = new Color(0.35f, 0.85f, 1f, 0.9f), BOUNDS_OFF = new Color(0.7f, 0.7f, 0.7f, 0.6f);

    // ------------------------------------------------------------------ apply

    /** Applies the edit (one undo step) and stops. Returns an error text or null. */
    public String commit() {
        if (!active || target == null) {
            stop();
            return null;
        }
        try {
            if (d3) {
                Editor3DItem it = (Editor3DItem) target;
                PropertySet<String, Object> ps = it.getPropertySet();
                Vector3 np = anchorWorld(new Vector3());   // the element stays: its position goes where the anchor is
                String[] old = {"Anchor X", ps.getString("Anchor X"), "Anchor Y", ps.getString("Anchor Y"), "Anchor Z", ps.getString("Anchor Z"),
                        "x", ps.getString("x"), "y", ps.getString("y"), "z", ps.getString("z")};
                String[] now = {"Anchor X", r(anchor.x), "Anchor Y", r(anchor.y), "Anchor Z", r(anchor.z), "x", r(np.x), "y", r(np.y), "z", r(np.z)};
                for (int i = 0; i < now.length; i += 2) ps.put(now[i], now[i + 1]);
                it.update();
                EditorAction a = EditorAction.propertiesChanged(ed, it.getName(), "anchor", now, old);
                if (a != null) a.updateEditorProperties().updateItemProperties();
                ed.getSaveState();
                ed.afterEdit3D();
            } else {
                EditorItem it = (EditorItem) target;
                PropertySet<String, Object> ps = it.getPropertySet();
                String name = "custom";
                for (String n : Pivot.NAMES) {
                    if ("custom".equals(n)) continue;
                    float[] f = Pivot.fraction(n, 0, 0);
                    if (Math.abs(f[0] - fx) < 1e-3f && Math.abs(f[1] - fy) < 1e-3f) { name = n; break; }
                }
                String[] now = {"Pivot", name, "Pivot X", r(fx), "Pivot Y", r(fy)};
                for (int i = 0; i < now.length; i += 2) ps.put(now[i], now[i + 1]);
                it.update();
                EditorAction a = EditorAction.propertiesChanged(ed, target.getName(), "pivot", now, start2D);
                if (a != null) a.updateEditorProperties().updateItemProperties();
                ed.getSaveState();
            }
            return null;
        } catch (Throwable t) {
            return String.valueOf(t.getMessage());
        } finally {
            stop();
        }
    }

    private static String r(float v) {
        return String.valueOf(Math.round(v * 10000f) / 10000f);
    }
}
