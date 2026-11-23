package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;

/**
 * A saved world shown inside another app (a Star2D scene, in the editor or in a game): read only, drawn with that app's
 * camera. Loading is spread over a few frames; {@link #loadNow()} finishes it at once (needed before physics shapes).
 */
public final class WorldView implements Disposable {
    public final FileHandle file;
    private Scene scene;
    private Renderer renderer;
    private long stamp;
    private int step, at;
    private String error;
    private static int checkEvery;

    /** Every view not yet disposed (see {@link #releaseShared()}). */
    private static final com.badlogic.gdx.utils.Array<WorldView> live = new com.badlogic.gdx.utils.Array<>();

    public WorldView(FileHandle file) {
        this.file = file;
        live.add(this);
    }

    /** Frees what this view built (meshes of its world), keeping it usable: the world is read again when drawn. */
    private void drop() {
        if (scene != null) scene.dispose();
        scene = null;
        if (renderer != null) renderer.dispose();
        renderer = null;
        if (step != 9) step = 0;
    }

    /** Last error (missing or broken file), or null. */
    public String error() { return error; }

    public boolean isReady() { return scene != null && step >= 3; }

    /** The world data (null until it is read). */
    public Scene scene() { return scene; }

    private static long stampOf(FileHandle f) { return f.exists() ? f.lastModified() * 31 + f.length() : 0; }

    /** One step of loading; returns true when the world can be drawn. */
    private boolean loadSome(boolean all) {
        do {
            switch (step) {
                case 0:
                    Assets.ensureLoaded();
                    if (renderer == null) renderer = new Renderer();
                    step = 1;
                    break;
                case 1:
                    try {
                        if (scene != null) scene.dispose();
                        stamp = stampOf(file);
                        scene = WorldIO.load(file);
                        error = null;
                    } catch (Exception e) {
                        scene = null;
                        error = e.getMessage() == null ? e.toString() : e.getMessage();
                        step = 9;
                        return false;
                    }
                    at = 0;
                    step = 2;
                    break;
                case 2: {
                    int left = scene.terrain.rebuild(all ? 1000 : 12);
                    int end = all ? scene.objects.size : Math.min(scene.objects.size, at + 80);
                    for (; at < end; at++) scene.objects.get(at).update(scene);
                    if (left == 0 && at >= scene.objects.size) step = 3;
                    else if (!all) return false;
                    break;
                }
                case 3:
                    return true;
                default:
                    return false;
            }
        } while (all || step < 2);
        return step == 3;
    }

    /** Finishes loading now. */
    public boolean loadNow() {
        if (step == 9) return false;
        return loadSome(true);
    }

    /** Reads the file again when it changed (the world was edited in World Builder). */
    public void checkFile() {
        if (step < 3 && step != 9) return;
        if (stampOf(file) != stamp) { step = 1; }
    }

    /** Draws the world placed by {@code transform}; call between the sky and the solid models of the outer picture. */
    public void render(Camera cam, Matrix4 transform, float delta) {
        if (!Assets.ready() && step >= 1 && step != 9) {       // another graphics context took the assets: load again here
            drop();                                                // (this view's own meshes are of this context: free them)
        }
        if (++checkEvery % 60 == 0) checkFile();
        if (!loadSome(false)) return;
        renderer.renderEmbedded(scene, cam, transform, delta);
    }

    /**
     * Collision shapes of the world, in the world's own units: the ground as heights plus one entry per element
     * collider that blocks. Ground: {cells, cell size, heights...}. Shapes: type, center xyz, rotation quaternion xyzw,
     * then box half sizes xyz / sphere radius / capsule radius and half height of its straight part.
     */
    public float[] groundHeights() {
        if (!loadNow()) return null;
        float[] out = new float[2 + scene.terrain.h.length];
        out[0] = Terrain.N; out[1] = Terrain.CELL;
        System.arraycopy(scene.terrain.h, 0, out, 2, scene.terrain.h.length);
        return out;
    }

    public FloatArray colliders() {
        FloatArray out = new FloatArray();
        if (!loadNow()) return out;
        Matrix4 m = new Matrix4();
        Quaternion q = new Quaternion();
        Vector3 c = new Vector3(), s = new Vector3();
        BoundingBox bb = new BoundingBox();
        for (SceneObject o : scene.objects) {
            if (o.hidden || o.colTrigger || o.colType == Collider.NONE) continue;
            Collider.matrix(o, m);
            q.setEulerAngles(o.rotY, o.rotX, o.rotZ);
            Collider.scaleOf(o, s);
            int type = o.colType;
            if (type == Collider.MESH) {                      // the element's own size as a box
                Collider.bounds(o, bb);
                bb.getCenter(c).mul(m);
                out.addAll(Collider.BOX, c.x, c.y, c.z, q.x, q.y, q.z, q.w, Math.abs(bb.getWidth() * s.x) / 2, Math.abs(bb.getHeight() * s.y) / 2, Math.abs(bb.getDepth() * s.z) / 2);
                continue;
            }
            c.set(o.colCenter).mul(m);
            if (type == Collider.BOX)
                out.addAll(type, c.x, c.y, c.z, q.x, q.y, q.z, q.w, Math.abs(o.colSize.x * s.x) / 2, Math.abs(o.colSize.y * s.y) / 2, Math.abs(o.colSize.z * s.z) / 2);
            else if (type == Collider.SPHERE)
                out.addAll(type, c.x, c.y, c.z, q.x, q.y, q.z, q.w, Collider.worldRadius(o), 0, 0);
            else {
                float r = Collider.worldRadius(o);
                out.addAll(type, c.x, c.y, c.z, q.x, q.y, q.z, q.w, r, Math.max(0, Collider.worldHalfHeight(o) - r), 0);
            }
        }
        return out;
    }

    /** Height of the ground (world units) at x, z of the world, or NaN. */
    public float groundAt(float x, float z) {
        return scene == null ? Float.NaN : scene.terrain.height(x, z);
    }

    @Override public void dispose() {
        drop();
        step = 0;
        live.removeValue(this, true);
    }

    /**
     * Frees the shared textures and meshes of every world view (they are loaded again when a world is drawn).
     * Call on the rendering thread before another app takes the screen (World Builder, a game started from the editor).
     */
    public static void releaseShared() {
        if (!Assets.ready()) return;
        for (WorldView v : live) v.drop();
        Assets.dispose();
        Shaders.dispose();
    }
}
