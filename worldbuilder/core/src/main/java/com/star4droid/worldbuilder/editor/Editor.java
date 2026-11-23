package com.star4droid.worldbuilder.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.star4droid.worldbuilder.engine.Assets;
import com.star4droid.worldbuilder.engine.Collider;
import com.star4droid.worldbuilder.engine.ModelDef;
import com.star4droid.worldbuilder.engine.Renderer;
import com.star4droid.worldbuilder.engine.Scene;
import com.star4droid.worldbuilder.engine.SceneObject;
import com.star4droid.worldbuilder.engine.Settings;
import com.star4droid.worldbuilder.engine.Terrain;

/** The editing logic: camera control, selection, placing, the transform gizmo, terrain and scatter brushes, undo. */
public final class Editor implements InputProcessor {
    public static final int SELECT = 0, MOVE = 1, ROTATE = 2, SCALE = 3, COLLIDER = 4;
    public static final int OBJECTS = 0, TERRAIN = 1, SCATTER = 2;

    public Scene scene;
    public final Renderer renderer;
    public final CameraRig rig = new CameraRig();
    public final Undo undo = new Undo();
    public final Array<SceneObject> selection = new Array<>();
    public int mode = OBJECTS, tool = MOVE;
    public boolean multi;
    /** Library asset armed for placing (tap the ground), or null. */
    public String placing;
    public int brushMode = Terrain.RAISE, brushLayer = Terrain.DIRT;
    public float brushRadius = 6, brushStrength = 0.5f;
    public final Array<String> scatter = new Array<>();
    public float scatterRadius = 5, scatterDensity = 0.5f;
    public boolean scatterErase;
    /** Called when the selection (or the list of elements) changed, and when values of the selection changed. */
    public Runnable onSelection = () -> { }, onValues = () -> { };
    public float dp = 1;
    public boolean dirty, enabled = true;
    /** A side picker is open: the view can still be moved, but taps do not select or place. */
    public boolean navOnly;
    public String toast;

    private final PerspectiveCamera cam;
    private final Vector3 v1 = new Vector3(), v2 = new Vector3(), v3 = new Vector3(), hit = new Vector3();
    private final Plane plane = new Plane();

    public Editor(Renderer renderer) { this.renderer = renderer; cam = renderer.cam; }

    public void setScene(Scene s) {
        scene = s; selection.clear(); undo.clear(); placing = null; dirty = false; action = NONE;
        rig.set(s.camera);
        onSelection.run();
    }

    // ------------------------------------------------------------------ selection and edits
    public SceneObject single() { return selection.size == 1 ? selection.first() : null; }

    public void select(SceneObject o, boolean add) {
        if (!add) selection.clear();
        if (o != null) { if (selection.contains(o, true)) { if (add) selection.removeValue(o, true); } else selection.add(o); }
        onSelection.run();
    }

    public void clearSelection() { if (selection.size > 0) { selection.clear(); onSelection.run(); } }

    private SceneObject.State[] editBefore;

    /** Call before changing values of the selected elements (sliders call it once, then {@link #endEdit} on release). */
    public void beginEdit() { if (editBefore == null) editBefore = states(selection); }

    public void endEdit() {
        if (editBefore == null) return;
        SceneObject.State[] after = new SceneObject.State[editBefore.length];
        for (int i = 0; i < after.length; i++) { SceneObject o = scene.find(editBefore[i].uid); after[i] = o == null ? null : o.state(); }
        undo.push(new Undo.Objects(editBefore, after));
        editBefore = null; dirty = true;
    }

    public void edit(Runnable r) { beginEdit(); r.run(); for (SceneObject o : selection) o.markDirty(); endEdit(); onValues.run(); }

    static SceneObject.State[] states(Array<SceneObject> list) {
        SceneObject.State[] s = new SceneObject.State[list.size];
        for (int i = 0; i < s.length; i++) s[i] = list.get(i).state();
        return s;
    }

    public void deleteSelection() {
        if (selection.size == 0) return;
        SceneObject.State[] before = states(selection);
        for (SceneObject o : selection) scene.remove(o);
        undo.push(new Undo.Objects(before, new SceneObject.State[before.length]));
        selection.clear(); dirty = true; onSelection.run();
    }

    public void duplicateSelection() {
        if (selection.size == 0) return;
        SceneObject.State[] after = new SceneObject.State[selection.size];
        Array<SceneObject> made = new Array<>();
        for (int i = 0; i < selection.size; i++) {
            SceneObject.State st = selection.get(i).state();
            st.uid = scene.nextUid++;
            float off = Math.max(1.2f, selection.get(i).radius * 0.9f);
            st.px += off; st.pz += off * 0.4f;
            if (Settings.groundSnap && selection.get(i).def.kind == ModelDef.MESH) st.py += scene.terrain.height(st.px, st.pz) - scene.terrain.height(st.px - off, st.pz - off * 0.4f);
            after[i] = st;
            made.add(scene.restore(st));
        }
        undo.push(new Undo.Objects(new SceneObject.State[after.length], after));
        selection.clear(); selection.addAll(made); dirty = true; onSelection.run();
    }

    public void focusSelection() {
        if (selection.size == 0) return;
        pivot(v1);
        float r = 1;
        for (SceneObject o : selection) { o.update(scene); r = Math.max(r, o.center.dst(v1) + o.radius); }
        rig.focus(v1, r);
    }

    public void dropToGround() {
        edit(() -> { for (SceneObject o : selection) o.pos.y = scene.terrain.height(o.pos.x, o.pos.z); });
    }

    public void doUndo() { undo.undo(scene); afterHistory(); }
    public void doRedo() { undo.redo(scene); afterHistory(); }
    private void afterHistory() {
        for (int i = selection.size - 1; i >= 0; i--) if (!scene.objects.contains(selection.get(i), true)) selection.removeIndex(i);
        dirty = true; onSelection.run();
    }

    private void pivot(Vector3 out) {
        out.setZero();
        for (SceneObject o : selection) out.add(o.pos);
        if (selection.size > 0) out.scl(1f / selection.size);
    }

    // ------------------------------------------------------------------ placing
    private boolean groundPoint(float sx, float sy, Vector3 out) {
        Ray ray = cam.getPickRay(sx, sy);
        return scene.terrain.raycast(ray, out);
    }

    public SceneObject placeAt(float sx, float sy) {
        ModelDef d = Assets.byId.get(placing);
        if (d == null || !groundPoint(sx, sy, hit)) return null;
        float y = hit.y, wl = scene.env.water ? scene.env.waterLevel : -1000;
        if ((d.flags & ModelDef.M_FLOAT) != 0 && y < wl) y = wl + 0.03f;
        else if (d.kind == ModelDef.WATER) y = Math.max(y, wl) + 0.25f;
        else if (d.kind == ModelDef.EFFECT && y < wl) y = wl;
        SceneObject o = scene.create(placing, snap(hit.x), y, snap(hit.z));
        boolean natural = d.cat.equals("nature") || d.cat.equals("rocks");
        if (natural) { o.rotY = MathUtils.random(360f); float s = MathUtils.random(0.9f, 1.15f); o.scale.set(s, s, s); }
        else if (d.kind == ModelDef.FALL || d.kind == ModelDef.WATER) o.rotY = Math.round(rig.yaw / 15f) * 15f;
        else o.rotY = Math.round((rig.yaw + 180) / 45f) * 45f % 360;
        SceneObject.State st = o.state();
        undo.push(new Undo.Objects(new SceneObject.State[]{null}, new SceneObject.State[]{st}));
        dirty = true;
        selection.clear(); selection.add(o);
        onSelection.run();
        return o;
    }

    float snap(float v) { return Settings.snap ? Math.round(v / Settings.snapStep) * Settings.snapStep : v; }

    // ------------------------------------------------------------------ gizmo
    private static final int NONE = 0, ORBIT = 1, PAN = 2, GIZMO = 3, DRAG = 4, BRUSH = 5, SCATTERING = 6, TWO = 7, COL = 8;
    private int action, handle = -1, hover = -1;
    private final Vector3 piv = new Vector3(), pivStart = new Vector3(), sc = new Vector3();
    private final Vector3[] axes = {new Vector3(1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1)}, ends = {new Vector3(), new Vector3(), new Vector3()};
    private final float[][] ring = new float[3][98];
    private float gsize;
    private boolean gizmoVisible;
    private SceneObject.State[] startStates;
    private float t0, a0, d0;
    private final Vector3 p0 = new Vector3();
    private boolean changed;

    private boolean project(Vector3 world, Vector3 out) {
        if (v3.set(world).sub(cam.position).dot(cam.direction) < cam.near) return false;
        cam.project(out.set(world));
        return true;
    }

    private void layoutGizmo() {
        gizmoVisible = false;
        if (mode != OBJECTS || selection.size == 0 || tool == SELECT || tool == COLLIDER || placing != null) return;
        if (action != GIZMO) pivot(piv);
        if (!project(piv, sc)) return;
        float dist = cam.position.dst(piv);
        gsize = dist * (float) Math.tan(Math.toRadians(cam.fieldOfView * 0.5)) * 2 * (86 * dp / Gdx.graphics.getHeight());
        SceneObject one = single();
        if (tool == SCALE && one != null) {
            float[] m = one.world.val;
            axes[0].set(m[0], m[1], m[2]).nor(); axes[1].set(m[4], m[5], m[6]).nor(); axes[2].set(m[8], m[9], m[10]).nor();
        } else { axes[0].set(1, 0, 0); axes[1].set(0, 1, 0); axes[2].set(0, 0, 1); }
        for (int i = 0; i < 3; i++) {
            if (!project(v1.set(axes[i]).scl(gsize).add(piv), ends[i])) return;
            if (tool == ROTATE) {
                Vector3 u = axes[(i + 1) % 3], w = axes[(i + 2) % 3];
                float rr = gsize * (i == 1 ? 1f : 0.82f);
                for (int k = 0; k < 49; k++) {
                    float a = k / 48f * MathUtils.PI2;
                    v1.set(u).scl(MathUtils.cos(a) * rr).mulAdd(w, MathUtils.sin(a) * rr).add(piv);
                    if (!project(v1, v2)) return;
                    ring[i][k * 2] = v2.x; ring[i][k * 2 + 1] = v2.y;
                }
            }
        }
        gizmoVisible = true;
    }

    private static float segDist(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay, l = dx * dx + dy * dy, t = l < 1e-6f ? 0 : MathUtils.clamp(((px - ax) * dx + (py - ay) * dy) / l, 0, 1);
        float cx = ax + dx * t - px, cy = ay + dy * t - py;
        return (float) Math.sqrt(cx * cx + cy * cy);
    }

    /** Handle under the pointer: 0..2 axes / rings, 3 center; -1 none. sy is y-down. */
    private int gizmoHit(float sx, float sy) {
        if (!gizmoVisible) return -1;
        float x = sx, y = Gdx.graphics.getHeight() - sy, thr = 24 * dp, best = thr; int h = -1;
        if (tool == ROTATE) {
            for (int i = 0; i < 3; i++) for (int k = 0; k < 48; k++) {
                float d = segDist(x, y, ring[i][k * 2], ring[i][k * 2 + 1], ring[i][k * 2 + 2], ring[i][k * 2 + 3]) + (i == 1 ? 0 : 3 * dp);
                if (d < best) { best = d; h = i; }
            }
            return h;
        }
        if (Math.hypot(x - sc.x, y - sc.y) < 17 * dp) return 3;
        for (int i = 0; i < 3; i++) {
            float ax = sc.x + (ends[i].x - sc.x) * 0.22f, ay = sc.y + (ends[i].y - sc.y) * 0.22f;
            float d = Math.min(segDist(x, y, ax, ay, ends[i].x, ends[i].y), (float) Math.hypot(x - ends[i].x, y - ends[i].y) - 6 * dp);
            if (d < best) { best = d; h = i; }
        }
        return h;
    }

    private float axisParam(Ray ray, Vector3 origin, Vector3 dir) {
        v1.set(origin).sub(ray.origin);
        float b = dir.dot(ray.direction), d = dir.dot(v1), e = ray.direction.dot(v1), den = 1 - b * b;
        if (Math.abs(den) < 1e-4f) return Float.NaN;
        return (b * e - d) / den;
    }

    private float ringAngle(Ray ray, int axis) {
        plane.set(pivStart, axes[axis]);
        if (Math.abs(ray.direction.dot(axes[axis])) < 0.06f || !Intersector.intersectRayPlane(ray, plane, v1)) return Float.NaN;
        v1.sub(pivStart);
        switch (axis) {
            case 0: return MathUtils.atan2(v1.z, v1.y) * MathUtils.radDeg;
            case 1: return MathUtils.atan2(v1.x, v1.z) * MathUtils.radDeg;
            default: return MathUtils.atan2(v1.y, v1.x) * MathUtils.radDeg;
        }
    }

    private void gizmoBegin(int h, float sx, float sy) {
        handle = h; action = GIZMO; changed = false;
        startStates = states(selection);
        pivStart.set(piv);
        Ray ray = cam.getPickRay(sx, sy);
        if (tool == ROTATE) a0 = ringAngle(ray, h);
        else if (h == 3) {
            if (tool == MOVE) { plane.set(pivStart, Vector3.Y); if (!Intersector.intersectRayPlane(ray, plane, p0)) p0.set(pivStart); }
            else d0 = Math.max(18 * dp, (float) Math.hypot(sx - sc.x, Gdx.graphics.getHeight() - sy - sc.y));
        } else t0 = axisParam(ray, pivStart, axes[h]);
    }

    private float ground(float x, float z) { return scene.terrain.height(x, z); }

    private void gizmoDrag(float sx, float sy) {
        Ray ray = cam.getPickRay(sx, sy);
        if (tool == MOVE) {
            float dx = 0, dy = 0, dz = 0;
            if (handle == 3) {
                plane.set(pivStart, Vector3.Y);
                if (!Intersector.intersectRayPlane(ray, plane, v2) || v2.dst(cam.position) > 600) return;
                dx = snap(v2.x - p0.x); dz = snap(v2.z - p0.z);
            } else {
                float t = axisParam(ray, pivStart, axes[handle]);
                if (Float.isNaN(t) || Float.isNaN(t0)) return;
                float d = snap(MathUtils.clamp(t - t0, -400, 400));
                dx = axes[handle].x * d; dy = axes[handle].y * d; dz = axes[handle].z * d;
            }
            moveBy(dx, dy, dz, handle != 1);
        } else if (tool == ROTATE) {
            float a = ringAngle(ray, handle);
            if (Float.isNaN(a) || Float.isNaN(a0)) return;
            float d = a - a0;
            if (Settings.snap) d = Math.round(d / 15f) * 15f;
            float c = MathUtils.cosDeg(d), s = MathUtils.sinDeg(d);
            for (int i = 0; i < selection.size; i++) {
                SceneObject o = selection.get(i); SceneObject.State st = startStates[i];
                if (handle == 1) {
                    o.rotY = norm(st.ry + d);
                    float x = st.px - pivStart.x, z = st.pz - pivStart.z;
                    o.pos.x = pivStart.x + x * c + z * s; o.pos.z = pivStart.z - x * s + z * c;
                } else if (handle == 0) o.rotX = norm(st.rx + d); else o.rotZ = norm(st.rz + d);
                o.markDirty();
            }
            piv.set(pivStart);
        } else {
            float f;
            if (handle == 3) f = (float) Math.hypot(sx - sc.x, Gdx.graphics.getHeight() - sy - sc.y) / d0;
            else {
                float t = axisParam(ray, pivStart, axes[handle]);
                if (Float.isNaN(t) || Float.isNaN(t0) || Math.abs(t0) < 1e-3f) return;
                f = t / t0;
            }
            f = MathUtils.clamp(f, 0.05f, 40f);
            if (Settings.snap) f = Math.max(0.1f, Math.round(f * 10) / 10f);
            for (int i = 0; i < selection.size; i++) {
                SceneObject o = selection.get(i); SceneObject.State st = startStates[i];
                float fx = handle == 3 || handle == 0 ? f : 1, fy = handle == 3 || handle == 1 ? f : 1, fz = handle == 3 || handle == 2 ? f : 1;
                o.scale.set(MathUtils.clamp(st.sx * fx, 0.05f, 80), MathUtils.clamp(st.sy * fy, 0.05f, 80), MathUtils.clamp(st.sz * fz, 0.05f, 80));
                o.markDirty();
            }
            piv.set(pivStart);
        }
        changed = true;
        onValues.run();
    }
    static float norm(float a) { a %= 360; return a < -180 ? a + 360 : a > 180 ? a - 360 : a; }

    private void moveBy(float dx, float dy, float dz, boolean followGround) {
        for (int i = 0; i < selection.size; i++) {
            SceneObject o = selection.get(i); SceneObject.State st = startStates[i];
            o.pos.set(st.px + dx, st.py + dy, st.pz + dz);
            if (followGround && Settings.groundSnap) o.pos.y = st.py + ground(o.pos.x, o.pos.z) - ground(st.px, st.pz);
            o.markDirty();
        }
        piv.set(pivStart).add(dx, dy, dz);
        if (followGround && Settings.groundSnap && selection.size == 1) piv.y = selection.first().pos.y;
    }

    private void finishTransform() {
        if (startStates != null && changed) { undo.push(new Undo.Objects(startStates, states(selection))); dirty = true; }
        startStates = null; handle = -1;
    }

    private void cancelTransform() {
        if (startStates != null) for (int i = 0; i < selection.size && i < startStates.length; i++) selection.get(i).apply(startStates[i]);
        startStates = null; handle = -1;
        onValues.run();
    }

    // ------------------------------------------------------------------ colliders
    /** Picks a tool; the collider tool gives a box to a single element that has no collider yet. */
    public void setTool(int t) {
        tool = t; placing = null;
        SceneObject o = single();
        if (t == COLLIDER && o != null && o.colType == Collider.NONE) {
            o.update(scene);
            edit(() -> Collider.fit(o, Collider.BOX));
            toast = "Box collider added";
        }
        onSelection.run();
    }

    private final Matrix4 cm = new Matrix4();
    private final Quaternion cq = new Quaternion();
    private final Vector3 ux = new Vector3(), uy = new Vector3(), uz = new Vector3(), wc = new Vector3(), pa = new Vector3(), pb = new Vector3(), sa = new Vector3(), sb = new Vector3(), csc = new Vector3();
    private final Vector3[] chWorld = new Vector3[6], chDir = new Vector3[6], chScreen = new Vector3[6];
    private final int[] chAxis = new int[6], chSign = new int[6];
    private int chCount, colHandle = -1, colHover = -1;
    private float colT0;
    private final Vector3 colStartPoint = new Vector3();
    private static final Color COL_C = new Color(0.36f, 1f, 0.55f, 0.95f), COL_TRIG = new Color(1f, 0.78f, 0.3f, 0.95f), COL_MESH = new Color(0.75f, 0.6f, 1f, 0.9f);

    private void colAxes(SceneObject o) {
        cq.setEulerAngles(o.rotY, o.rotX, o.rotZ);
        cq.transform(ux.set(1, 0, 0)); cq.transform(uy.set(0, 1, 0)); cq.transform(uz.set(0, 0, 1));
        Collider.matrix(o, cm);
        Collider.scaleOf(o, csc);
        wc.set(o.colCenter).mul(cm);
    }

    /** Handles on the faces of the shape of the selected element (collider tool). */
    private void layoutColHandles() {
        chCount = 0;
        SceneObject o = single();
        if (mode != OBJECTS || tool != COLLIDER || o == null || o.hidden || placing != null) return;
        if (o.colType != Collider.BOX && o.colType != Collider.SPHERE && o.colType != Collider.CAPSULE) return;
        if (chWorld[0] == null) for (int i = 0; i < 6; i++) { chWorld[i] = new Vector3(); chDir[i] = new Vector3(); chScreen[i] = new Vector3(); }
        colAxes(o);
        Vector3[] ax = {ux, uy, uz};
        float wr = Collider.worldRadius(o), hh = Collider.worldHalfHeight(o);
        for (int a = 0; a < 3; a++) for (int sgn = -1; sgn <= 1; sgn += 2) {
            float dist;
            if (o.colType == Collider.BOX) dist = comp(o.colSize, a) / 2 * comp(csc, a);
            else if (o.colType == Collider.SPHERE) dist = wr;
            else dist = a == 1 ? hh : wr;
            int i = chCount;
            chWorld[i].set(ax[a]).scl(dist * sgn).add(wc);
            chDir[i].set(ax[a]).scl(sgn);
            chAxis[i] = a; chSign[i] = sgn;
            if (project(chWorld[i], chScreen[i])) chCount++;
        }
    }

    /** Screen position (y down) of a collider handle, or null (used by the desktop test scripts). */
    public float[] colHandleScreen(int i) { layoutColHandles(); return i < chCount ? new float[]{chScreen[i].x, Gdx.graphics.getHeight() - chScreen[i].y} : null; }

    private int colHit(float sx, float sy) {
        layoutColHandles();
        float x = sx, y = Gdx.graphics.getHeight() - sy, best = 26 * dp; int h = -1;
        for (int i = 0; i < chCount; i++) {
            float d = (float) Math.hypot(x - chScreen[i].x, y - chScreen[i].y);
            if (d < best) { best = d; h = i; }
        }
        return h;
    }

    private void colBegin(int h, float sx, float sy) {
        action = COL; colHandle = h; changed = false;
        startStates = states(selection);
        colStartPoint.set(chWorld[h]);
        colT0 = axisParam(cam.getPickRay(sx, sy), colStartPoint, chDir[h]);
    }

    private void colDrag(float sx, float sy) {
        SceneObject o = single();
        if (o == null || startStates == null || colHandle < 0) return;
        float t = axisParam(cam.getPickRay(sx, sy), colStartPoint, chDir[colHandle]);
        if (Float.isNaN(t) || Float.isNaN(colT0)) return;
        float d = MathUtils.clamp(t - colT0, -200, 200);
        if (Settings.snap) d = Math.round(d / (Settings.snapStep * 0.5f)) * (Settings.snapStep * 0.5f);
        SceneObject.State st = startStates[0];
        int a = chAxis[colHandle], sgn = chSign[colHandle];
        Collider.scaleOf(o, csc);
        float sA = Math.max(0.01f, comp(csc, a));
        o.colCenter.set(st.col[0], st.col[1], st.col[2]);
        if (o.colType == Collider.BOX) {
            float size = Math.max(0.05f, st.col[3 + a] + d / sA), grow = size - st.col[3 + a];
            o.colSize.set(st.col[3], st.col[4], st.col[5]);
            setAxis(o.colSize, a, size);
            setAxis(o.colCenter, a, st.col[a] + sgn * grow / 2);
        } else if (o.colType == Collider.SPHERE) {
            float m = Math.max(csc.x, Math.max(csc.y, csc.z));
            o.colRadius = Math.max(0.05f, st.col[6] + d / Math.max(0.01f, m));
        } else if (a == 1) {
            float h = Math.max(0.1f, st.col[7] + d / sA), grow = h - st.col[7];
            o.colHeight = h;
            o.colCenter.y = st.col[1] + sgn * grow / 2;
        } else {
            o.colRadius = Math.max(0.05f, st.col[6] + d / Math.max(0.01f, Math.max(csc.x, csc.z)));
        }
        changed = true;
        onValues.run();
    }

    private static float comp(Vector3 v, int a) { return a == 0 ? v.x : a == 1 ? v.y : v.z; }
    private static void setAxis(Vector3 v, int a, float val) { if (a == 0) v.x = val; else if (a == 1) v.y = val; else v.z = val; }

    private void seg(ShapeRenderer sr, Vector3 a, Vector3 b, float w) {
        if (!project(a, sa) || !project(b, sb)) return;
        sr.rectLine(sa.x, sa.y, sb.x, sb.y, w);
    }

    private void ring(ShapeRenderer sr, Vector3 c, Vector3 u, Vector3 v, float r, float a0, float a1, float w) {
        int n = 28;
        for (int k = 0; k < n; k++) {
            float t0 = a0 + (a1 - a0) * k / n, t1 = a0 + (a1 - a0) * (k + 1) / n;
            pa.set(c).mulAdd(u, MathUtils.cos(t0) * r).mulAdd(v, MathUtils.sin(t0) * r);
            pb.set(c).mulAdd(u, MathUtils.cos(t1) * r).mulAdd(v, MathUtils.sin(t1) * r);
            seg(sr, pa, pb, w);
        }
    }

    private final Vector3 k0 = new Vector3(), k1 = new Vector3();

    /** Outline of an element's collider. */
    private void drawCollider(ShapeRenderer sr, SceneObject o, boolean strong) {
        if (o.colType == Collider.NONE || o.hidden) return;
        Color c = o.colType == Collider.MESH ? COL_MESH : o.colTrigger ? COL_TRIG : COL_C;
        sr.setColor(c.r, c.g, c.b, strong ? c.a : c.a * 0.5f);
        float w = (strong ? 2f : 1.2f) * dp;
        colAxes(o);
        if (o.colType == Collider.BOX || o.colType == Collider.MESH) {
            BoundingBox bb = o.colType == Collider.MESH ? Collider.bounds(o, tmpBounds) : null;
            for (int i = 0; i < 8; i++) {
                float hx = o.colType == Collider.BOX ? o.colSize.x / 2 : bb.getWidth() / 2, hy = o.colType == Collider.BOX ? o.colSize.y / 2 : bb.getHeight() / 2, hz = o.colType == Collider.BOX ? o.colSize.z / 2 : bb.getDepth() / 2;
                if (o.colType == Collider.BOX) corners[i].set(o.colCenter); else bb.getCenter(corners[i]);
                corners[i].add((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz).mul(cm);
            }
            for (int e = 0; e < EDGES.length; e += 2) seg(sr, corners[EDGES[e]], corners[EDGES[e + 1]], w);
        } else if (o.colType == Collider.SPHERE) {
            float r = Collider.worldRadius(o);
            ring(sr, wc, ux, uz, r, 0, MathUtils.PI2, w); ring(sr, wc, ux, uy, r, 0, MathUtils.PI2, w); ring(sr, wc, uz, uy, r, 0, MathUtils.PI2, w);
        } else {
            float r = Collider.worldRadius(o), half = Collider.worldHalfHeight(o) - r;
            k0.set(wc).mulAdd(uy, half); k1.set(wc).mulAdd(uy, -half);
            ring(sr, k0, ux, uz, r, 0, MathUtils.PI2, w); ring(sr, k1, ux, uz, r, 0, MathUtils.PI2, w);
            ring(sr, k0, ux, uy, r, 0, MathUtils.PI, w); ring(sr, k0, uz, uy, r, 0, MathUtils.PI, w);
            ring(sr, k1, ux, uy, r, MathUtils.PI, MathUtils.PI2, w); ring(sr, k1, uz, uy, r, MathUtils.PI, MathUtils.PI2, w);
            Vector3[] side = {ux, uz};
            for (Vector3 sv : side) for (int sg = -1; sg <= 1; sg += 2) {
                pa.set(k0).mulAdd(sv, r * sg); pb.set(k1).mulAdd(sv, r * sg);
                if (project(pa, sa) && project(pb, sb)) sr.rectLine(sa.x, sa.y, sb.x, sb.y, w);
            }
        }
    }
    private final BoundingBox tmpBounds = new BoundingBox();

    private void drawColliders(ShapeRenderer sr) {
        if (Settings.showColliders)
            for (SceneObject o : scene.objects) {
                if (o.colType == Collider.NONE || o.hidden || selection.contains(o, true)) continue;
                if (o.center.dst2(cam.position) > Settings.renderDistance * Settings.renderDistance * 0.36f) continue;
                drawCollider(sr, o, false);
            }
        boolean colTool = tool == COLLIDER && mode == OBJECTS;
        for (int n = 0; n < selection.size && n < 60; n++) drawCollider(sr, selection.get(n), colTool || Settings.showColliders);
        if (!colTool) return;
        layoutColHandles();
        int active = action == COL ? colHandle : colHover;
        Color[] axc = {AXIS[0], AXIS[1], AXIS[2]};
        for (int i = 0; i < chCount; i++) {
            if (action == COL && i != colHandle) continue;
            sr.setColor(DARK); sr.circle(chScreen[i].x, chScreen[i].y, 10.5f * dp, 20);
            sr.setColor(i == active ? WHITE : axc[chAxis[i]]); sr.circle(chScreen[i].x, chScreen[i].y, 8 * dp, 20);
            sr.setColor(WHITE); sr.circle(chScreen[i].x, chScreen[i].y, 3 * dp, 12);
        }
    }

    // ------------------------------------------------------------------ brushes
    private float[] strokeH; private byte[] strokeS;
    private int bx0, bz0, bx1, bz1; private boolean strokeTouched;
    private float flattenTarget, curX, curY; private boolean pointerOn;
    private final Array<SceneObject.State> strokeBefore = new Array<>(), strokeAfter = new Array<>();
    private float scatterAcc;

    private void beginStroke(float sx, float sy) {
        strokeH = scene.terrain.h.clone(); strokeS = scene.terrain.splat.clone();
        bx0 = bz0 = Integer.MAX_VALUE; bx1 = bz1 = -1; strokeTouched = false;
        if (groundPoint(sx, sy, hit)) flattenTarget = hit.y;
    }

    private void applyBrush(float dt) {
        if (!groundPoint(curX, curY, hit)) return;
        float k = brushMode == Terrain.RAISE || brushMode == Terrain.LOWER ? 10 : brushMode == Terrain.SMOOTH ? 14 : 9;
        int[] r = scene.terrain.brush(brushMode, hit.x, hit.z, brushRadius, brushStrength * dt * k, flattenTarget, brushLayer);
        bx0 = Math.min(bx0, r[0]); bz0 = Math.min(bz0, r[1]); bx1 = Math.max(bx1, r[2]); bz1 = Math.max(bz1, r[3]);
        strokeTouched = true;
    }

    private float oldHeight(float x, float z) {
        float fx = MathUtils.clamp(Terrain.gx(x), 0, Terrain.N - 0.001f), fz = MathUtils.clamp(Terrain.gx(z), 0, Terrain.N - 0.001f);
        int ix = (int) fx, iz = (int) fz; float tx = fx - ix, tz = fz - iz; int W = Terrain.W;
        return (strokeH[iz * W + ix] * (1 - tx) + strokeH[iz * W + ix + 1] * tx) * (1 - tz) + (strokeH[(iz + 1) * W + ix] * (1 - tx) + strokeH[(iz + 1) * W + ix + 1] * tx) * tz;
    }

    private void endStroke() {
        if (strokeTouched && strokeH != null) {
            Undo.Cmd ground = new Undo.Ground(scene.terrain, strokeH, strokeS, bx0, bz0, bx1, bz1);
            // elements standing on the changed ground go up or down with it
            Array<SceneObject.State> before = new Array<>(), after = new Array<>();
            float x0 = bx0 * Terrain.CELL - Terrain.HALF, x1 = bx1 * Terrain.CELL - Terrain.HALF, z0 = bz0 * Terrain.CELL - Terrain.HALF, z1 = bz1 * Terrain.CELL - Terrain.HALF;
            if (Settings.groundSnap) for (SceneObject o : scene.objects) {
                if (o.locked || o.pos.x < x0 || o.pos.x > x1 || o.pos.z < z0 || o.pos.z > z1) continue;
                float was = oldHeight(o.pos.x, o.pos.z), now = scene.terrain.height(o.pos.x, o.pos.z);
                if (Math.abs(now - was) < 0.01f || Math.abs(o.pos.y - was) > 1.5f) continue;
                before.add(o.state()); o.pos.y += now - was; o.markDirty(); after.add(o.state());
            }
            if (before.size > 0) undo.push(new Undo.Multi(ground, new Undo.Objects(before.toArray(SceneObject.State.class), after.toArray(SceneObject.State.class))));
            else undo.push(ground);
            dirty = true; onValues.run();
        }
        strokeH = null; strokeS = null;
    }

    private void applyScatter(float dt) {
        if (!groundPoint(curX, curY, hit)) return;
        float cx = hit.x, cz = hit.z;
        if (scatterErase) {
            for (int i = scene.objects.size - 1; i >= 0; i--) {
                SceneObject o = scene.objects.get(i);
                if (o.locked || o.def.kind != ModelDef.MESH) continue;
                boolean match = scatter.size == 0 ? (o.def.flags & (ModelDef.M_SCATTER | ModelDef.M_SMALL)) != 0 || o.def.cat.equals("nature") : scatter.contains(o.def.id, false);
                if (!match || Vector3.dst2(o.pos.x, 0, o.pos.z, cx, 0, cz) > scatterRadius * scatterRadius) continue;
                strokeBefore.add(o.state()); strokeAfter.add(null);
                selection.removeValue(o, true);
                scene.remove(o);
            }
            return;
        }
        if (scatter.size == 0) return;
        scatterAcc += dt * (0.4f + scatterDensity * 2.2f) * Math.max(1, scatterRadius * scatterRadius * 0.35f);
        int guard = 0;
        while (scatterAcc >= 1 && guard++ < 12) {
            scatterAcc -= 1;
            ModelDef d = Assets.byId.get(scatter.random());
            if (d == null) continue;
            float a = MathUtils.random(MathUtils.PI2), r = (float) Math.sqrt(MathUtils.random()) * scatterRadius, x = cx + MathUtils.cos(a) * r, z = cz + MathUtils.sin(a) * r;
            if (!scene.terrain.inside(x, z)) continue;
            float y = ground(x, z), wl = scene.env.water ? scene.env.waterLevel : -1000;
            boolean floats = (d.flags & ModelDef.M_FLOAT) != 0;
            if (floats ? y > wl - 0.1f : y < wl + 0.05f) continue;
            float size = Math.max(d.bounds.getWidth(), d.bounds.getDepth()) * 0.5f, space = Math.max(0.25f, size * (1.9f - 1.5f * scatterDensity));
            boolean clash = false;
            for (int i = scene.objects.size - 1; i >= 0 && !clash; i--) {
                SceneObject o = scene.objects.get(i);
                if (o.def.kind != ModelDef.MESH) continue;
                float need = o.def == d || scatter.contains(o.def.id, false) ? space : Math.min(space, 0.5f) + Math.min(2.5f, Math.max(o.def.bounds.getWidth(), o.def.bounds.getDepth()) * 0.3f * o.uniform) * ((o.def.flags & ModelDef.M_SMALL) != 0 ? 0 : 1);
                if (Vector3.dst2(o.pos.x, 0, o.pos.z, x, 0, z) < need * need) clash = true;
            }
            if (clash) continue;
            SceneObject o = scene.create(d.id, x, floats ? wl + 0.03f : y - 0.03f, z);
            o.rotY = MathUtils.random(360f);
            float s = MathUtils.random(0.8f, 1.3f); o.scale.set(s, s, s);
            strokeBefore.add(null); strokeAfter.add(o.state());
        }
    }

    private void endScatter() {
        if (strokeAfter.size > 0) {
            undo.push(new Undo.Objects(strokeBefore.toArray(SceneObject.State.class), strokeAfter.toArray(SceneObject.State.class)));
            dirty = true; onSelection.run();
        }
        strokeBefore.clear(); strokeAfter.clear();
    }

    // ------------------------------------------------------------------ frame
    public void update(float dt) {
        if (scene == null) return;
        if (enabled && !Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
            float m = rig.distGoal * dt * 0.9f, r = 0, f = 0;
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) r -= m;
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) r += m;
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) f += m;
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) f -= m;
            if (r != 0 || f != 0) rig.pan(-r, f);
        }
        if (action == BRUSH && pointerOn) applyBrush(dt);
        if (action == SCATTERING && pointerOn) applyScatter(dt);
        rig.update(dt, scene.terrain, cam);
        cam.viewportWidth = Gdx.graphics.getWidth(); cam.viewportHeight = Gdx.graphics.getHeight();
        cam.update();
        renderer.focus.set(rig.target); renderer.focusDist = rig.dist;
        renderer.selected.clear();
        for (SceneObject o : selection) renderer.selected.add(o.uid);
        // brush ring
        boolean ringOn = mode != OBJECTS && (pointerOn || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) && enabled;
        if (ringOn && groundPoint(curX, curY, hit)) {
            renderer.brush[0] = hit.x; renderer.brush[1] = hit.z; renderer.brush[2] = mode == TERRAIN ? brushRadius : scatterRadius; renderer.brush[3] = 1;
            boolean red = mode == SCATTER ? scatterErase : brushMode == Terrain.LOWER || brushMode == Terrain.ERASE;
            renderer.brushColor[0] = red ? 1f : 1f; renderer.brushColor[1] = red ? 0.45f : 1f; renderer.brushColor[2] = red ? 0.4f : 1f;
        } else renderer.brush[3] = 0;
    }

    private static final Color[] AXIS = {new Color(1f, 0.36f, 0.38f, 1), new Color(0.38f, 0.86f, 0.42f, 1), new Color(0.3f, 0.62f, 1f, 1)};
    private static final Color SEL = new Color(0.45f, 0.8f, 1f, 0.95f), WHITE = new Color(1, 1, 1, 1), DARK = new Color(0, 0, 0, 0.35f);
    private final Vector3[] corners = new Vector3[8];
    private final Vector3[] cs = new Vector3[8];
    private static final int[] EDGES = {0, 1, 1, 3, 3, 2, 2, 0, 4, 5, 5, 7, 7, 6, 6, 4, 0, 4, 1, 5, 2, 6, 3, 7};

    /** Selection boxes and the gizmo, in screen space (ShapeRenderer already has a pixel projection; Filled). */
    public void drawOverlay(ShapeRenderer sr) {
        if (scene == null) return;
        if (corners[0] == null) for (int i = 0; i < 8; i++) { corners[i] = new Vector3(); cs[i] = new Vector3(); }
        for (int n = 0; n < selection.size && n < 60; n++) {
            SceneObject o = selection.get(n);
            if (o.hidden) continue;
            BoundingBox b = o.local;
            boolean ok = true;
            for (int i = 0; i < 8 && ok; i++) {
                corners[i].set((i & 1) == 0 ? b.min.x : b.max.x, (i & 2) == 0 ? b.min.y : b.max.y, (i & 4) == 0 ? b.min.z : b.max.z).mul(o.world);
                ok = project(corners[i], cs[i]);
            }
            if (!ok) continue;
            sr.setColor(SEL);
            for (int e = 0; e < EDGES.length; e += 2) sr.rectLine(cs[EDGES[e]].x, cs[EDGES[e]].y, cs[EDGES[e + 1]].x, cs[EDGES[e + 1]].y, 1.6f * dp);
        }
        drawColliders(sr);
        layoutGizmo();
        if (!gizmoVisible) return;
        int active = action == GIZMO ? handle : hover;
        if (tool == ROTATE) {
            for (int i = 0; i < 3; i++) {
                if (action == GIZMO && handle != i) continue;
                Color c = AXIS[i];
                float w = (active == i ? 4.6f : 3f) * dp;
                sr.setColor(DARK);
                for (int k = 0; k < 48; k++) sr.rectLine(ring[i][k * 2], ring[i][k * 2 + 1], ring[i][k * 2 + 2], ring[i][k * 2 + 3], w + 2 * dp);
                sr.setColor(active == i ? WHITE : c);
                for (int k = 0; k < 48; k++) sr.rectLine(ring[i][k * 2], ring[i][k * 2 + 1], ring[i][k * 2 + 2], ring[i][k * 2 + 3], w);
            }
            sr.setColor(WHITE); sr.circle(sc.x, sc.y, 4 * dp, 16);
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (action == GIZMO && handle != i && handle != 3) continue;
            float ex = ends[i].x, ey = ends[i].y, dx = ex - sc.x, dy = ey - sc.y, l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l < 6 * dp) continue;
            dx /= l; dy /= l;
            float w = (active == i ? 5f : 3.4f) * dp, head = 13 * dp;
            sr.setColor(DARK); sr.rectLine(sc.x + dx * 12 * dp, sc.y + dy * 12 * dp, ex, ey, w + 2.4f * dp);
            sr.setColor(active == i ? WHITE : AXIS[i]);
            sr.rectLine(sc.x + dx * 12 * dp, sc.y + dy * 12 * dp, ex - dx * head * 0.5f, ey - dy * head * 0.5f, w);
            if (tool == MOVE) sr.triangle(ex + dx * head * 0.55f, ey + dy * head * 0.55f, ex - dx * head * 0.6f - dy * head * 0.55f, ey - dy * head * 0.6f + dx * head * 0.55f, ex - dx * head * 0.6f + dy * head * 0.55f, ey - dy * head * 0.6f - dx * head * 0.55f);
            else sr.rect(ex - head * 0.5f, ey - head * 0.5f, head, head);
        }
        sr.setColor(DARK); sr.circle(sc.x, sc.y, 11.5f * dp, 24);
        sr.setColor(active == 3 ? WHITE : new Color(1f, 0.86f, 0.35f, 1)); sr.circle(sc.x, sc.y, 9.5f * dp, 24);
    }

    // ------------------------------------------------------------------ input
    private final float[] px = new float[2], py = new float[2];
    private final boolean[] down = new boolean[2];
    private float startX, startY, lastMx, lastMy, lastDist, lastAng;
    private long downTime;
    private boolean moved, dragStarted, ignoreUntilUp;
    private final Vector3 drag0 = new Vector3();

    private boolean shift() { return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT); }
    private boolean ctrl() { return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT); }

    private SceneObject pickAt(float sx, float sy) {
        Ray ray = cam.getPickRay(sx, sy);
        float max = scene.terrain.raycast(ray, hit) ? hit.dst(ray.origin) + 0.6f : 2000;
        return scene.pick(ray, max);
    }

    private void cancelAction() {
        if (action == GIZMO || action == DRAG || action == COL) cancelTransform();
        if (action == BRUSH) endStroke();
        if (action == SCATTERING) endScatter();
        action = NONE; pointerOn = false;
    }

    @Override public boolean touchDown(int x, int y, int pointer, int button) {
        if (!enabled || scene == null || pointer > 1) return false;
        px[pointer] = x; py[pointer] = y; down[pointer] = true;
        if (pointer == 1) {
            if (!down[0]) return true;
            cancelAction();
            action = TWO;
            lastMx = (px[0] + px[1]) / 2; lastMy = (py[0] + py[1]) / 2;
            lastDist = Math.max(10, (float) Math.hypot(px[1] - px[0], py[1] - py[0])); lastAng = MathUtils.atan2(py[1] - py[0], px[1] - px[0]) * MathUtils.radDeg;
            return true;
        }
        if (ignoreUntilUp) return true;
        startX = x; startY = y; moved = false; dragStarted = false; downTime = System.currentTimeMillis();
        curX = x; curY = y;
        if (button == Input.Buttons.RIGHT) { action = ORBIT; return true; }
        if (button == Input.Buttons.MIDDLE) { action = PAN; return true; }
        if (navOnly) { action = ORBIT; return true; }
        if (mode == TERRAIN) { action = BRUSH; pointerOn = true; beginStroke(x, y); return true; }
        if (mode == SCATTER) { action = SCATTERING; pointerOn = true; scatterAcc = 1; return true; }
        layoutGizmo();
        int ch = placing == null ? colHit(x, y) : -1;
        if (ch >= 0) { colBegin(ch, x, y); return true; }
        int h = placing == null ? gizmoHit(x, y) : -1;
        if (h >= 0) { gizmoBegin(h, x, y); return true; }
        if (placing == null && tool != SELECT && tool != COLLIDER && selection.size > 0) {
            SceneObject o = pickAt(x, y);
            if (o != null && selection.contains(o, true)) { action = DRAG; return true; }
        }
        action = ORBIT;
        return true;
    }

    @Override public boolean touchDragged(int x, int y, int pointer) {
        if (!enabled || scene == null || pointer > 1 || !down[pointer]) return false;
        float dx = x - px[pointer], dy = y - py[pointer];
        px[pointer] = x; py[pointer] = y;
        if (action == TWO) {
            if (!down[0] || !down[1]) return true;
            float mx = (px[0] + px[1]) / 2, my = (py[0] + py[1]) / 2, dist = Math.max(10, (float) Math.hypot(px[1] - px[0], py[1] - py[0])), ang = MathUtils.atan2(py[1] - py[0], px[1] - px[0]) * MathUtils.radDeg;
            float mpp = metersPerPixel();
            rig.pan((mx - lastMx) * mpp, (my - lastMy) * mpp / Math.max(0.35f, MathUtils.sinDeg(rig.pitch)));
            rig.zoom(lastDist / dist);
            float da = ang - lastAng; if (da > 180) da -= 360; if (da < -180) da += 360;
            rig.orbit(-da, 0);
            lastMx = mx; lastMy = my; lastDist = dist; lastAng = ang;
            return true;
        }
        if (pointer != 0 || ignoreUntilUp) return true;
        curX = x; curY = y;
        if (!moved && Math.hypot(x - startX, y - startY) > 9 * dp) moved = true;
        switch (action) {
            case ORBIT: if (moved) rig.orbit(dx * 0.28f / dp, dy * 0.28f / dp); break;
            case PAN: { float mpp = metersPerPixel(); rig.pan(dx * mpp, dy * mpp / Math.max(0.35f, MathUtils.sinDeg(rig.pitch))); break; }
            case GIZMO: gizmoDrag(x, y); break;
            case COL: colDrag(x, y); break;
            case DRAG:
                if (!moved) break;
                if (!dragStarted) {
                    dragStarted = true; changed = false;
                    startStates = states(selection); pivot(pivStart); piv.set(pivStart);
                    if (!groundPoint(startX, startY, drag0)) drag0.set(pivStart);
                }
                if (groundPoint(x, y, hit)) { moveBy(snap(hit.x - drag0.x), 0, snap(hit.z - drag0.z), true); changed = true; onValues.run(); }
                break;
        }
        return true;
    }

    private float metersPerPixel() { return rig.dist * 2 * (float) Math.tan(Math.toRadians(cam.fieldOfView * 0.5)) / Gdx.graphics.getHeight(); }

    @Override public boolean touchUp(int x, int y, int pointer, int button) {
        if (pointer > 1 || !down[pointer]) return false;
        down[pointer] = false;
        if (action == TWO) { action = NONE; ignoreUntilUp = down[0] || down[1]; return true; }
        if (pointer != 0) return true;
        if (ignoreUntilUp) { ignoreUntilUp = false; return true; }
        if (!enabled || scene == null) { action = NONE; return true; }
        boolean tap = !moved && System.currentTimeMillis() - downTime < 450;
        switch (action) {
            case GIZMO: case COL: finishTransform(); break;
            case DRAG: if (dragStarted) finishTransform(); else if (tap) tapSelect(x, y); break;
            case BRUSH: endStroke(); break;
            case SCATTERING: endScatter(); break;
            case ORBIT:
                if (tap && button != Input.Buttons.RIGHT && mode == OBJECTS && !navOnly) {
                    if (placing != null) { if (placeAt(x, y) == null) toast = "Tap on the ground to place"; }
                    else tapSelect(x, y);
                }
                break;
        }
        action = NONE; pointerOn = false;
        return true;
    }

    private void tapSelect(float x, float y) {
        SceneObject o = pickAt(x, y);
        boolean add = multi || shift();
        if (o == null) { if (!add) clearSelection(); }
        else select(o, add);
    }

    @Override public boolean touchCancelled(int x, int y, int pointer, int button) {
        if (pointer <= 1) down[pointer] = false;
        cancelAction(); ignoreUntilUp = false;
        return true;
    }

    @Override public boolean mouseMoved(int x, int y) {
        curX = x; curY = y;
        if (enabled && scene != null && mode == OBJECTS) { hover = gizmoHit(x, y); colHover = colHit(x, y); }
        return false;
    }

    @Override public boolean scrolled(float ax, float ay) {
        if (!enabled) return false;
        rig.zoom(1 + ay * 0.12f);
        return true;
    }

    @Override public boolean keyDown(int key) {
        if (!enabled || scene == null || navOnly) return false;
        switch (key) {
            case Input.Keys.FORWARD_DEL: case Input.Keys.DEL: deleteSelection(); return true;
            case Input.Keys.Z: if (ctrl()) { if (shift()) doRedo(); else doUndo(); return true; } break;
            case Input.Keys.Y: if (ctrl()) { doRedo(); return true; } break;
            case Input.Keys.D: if (ctrl()) { duplicateSelection(); return true; } break;
            case Input.Keys.Q: tool = SELECT; onSelection.run(); return true;
            case Input.Keys.W: tool = MOVE; onSelection.run(); return true;
            case Input.Keys.E: tool = ROTATE; onSelection.run(); return true;
            case Input.Keys.R: tool = SCALE; onSelection.run(); return true;
            case Input.Keys.T: setTool(COLLIDER); return true;
            case Input.Keys.F: focusSelection(); return true;
            case Input.Keys.ESCAPE: if (placing != null) { placing = null; onSelection.run(); } else clearSelection(); return true;
        }
        return false;
    }
    @Override public boolean keyUp(int key) { return false; }
    @Override public boolean keyTyped(char c) { return false; }
}
