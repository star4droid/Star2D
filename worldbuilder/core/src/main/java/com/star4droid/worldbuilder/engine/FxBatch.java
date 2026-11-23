package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.NumberUtils;

/** Batches camera-facing sprites and free quads for the effects (cells of Assets.sprites: 0 glow, 1 spark, 2 ring, 3 ray). */
public final class FxBatch {
    private static final int MAX = 4000;
    private final float[] v = new float[MAX * 4 * 6];
    private final Mesh mesh;
    private int n;
    public final Vector3 right = new Vector3(), up = new Vector3(), camPos = new Vector3();
    private final Vector3 a = new Vector3(), b = new Vector3();

    public FxBatch() {
        mesh = new Mesh(false, MAX * 4, MAX * 6, VertexAttribute.Position(), VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0));
        short[] idx = new short[MAX * 6];
        for (int i = 0; i < MAX; i++) { int o = i * 4; idx[i * 6] = (short) o; idx[i * 6 + 1] = (short) (o + 1); idx[i * 6 + 2] = (short) (o + 2); idx[i * 6 + 3] = (short) o; idx[i * 6 + 4] = (short) (o + 2); idx[i * 6 + 5] = (short) (o + 3); }
        mesh.setIndices(idx);
    }

    public void begin(Camera cam) {
        n = 0;
        right.set(cam.direction).crs(cam.up).nor();
        up.set(right).crs(cam.direction).nor();
        camPos.set(cam.position);
    }

    public static float color(float r, float g, float b, float al) {
        return NumberUtils.intToFloatColor(((int) (Math.min(1, Math.max(0, al)) * 255) << 24) | ((int) (Math.min(1, Math.max(0, b)) * 255) << 16) | ((int) (Math.min(1, Math.max(0, g)) * 255) << 8) | (int) (Math.min(1, Math.max(0, r)) * 255));
    }

    private void vert(float x, float y, float z, float c, float u, float w) {
        int o = n * 24 + k * 6;
        v[o] = x; v[o + 1] = y; v[o + 2] = z; v[o + 3] = c; v[o + 4] = u; v[o + 5] = w;
        k++;
    }
    private int k;

    /** Quad from a center and two half-axis vectors. */
    public void quad(float cx, float cy, float cz, Vector3 hx, Vector3 hy, int cell, float color) {
        if (n >= MAX) return;
        float u0 = cell * 0.25f + 0.004f, u1 = (cell + 1) * 0.25f - 0.004f;
        k = 0;
        vert(cx - hx.x - hy.x, cy - hx.y - hy.y, cz - hx.z - hy.z, color, u0, 1);
        vert(cx + hx.x - hy.x, cy + hx.y - hy.y, cz + hx.z - hy.z, color, u1, 1);
        vert(cx + hx.x + hy.x, cy + hx.y + hy.y, cz + hx.z + hy.z, color, u1, 0);
        vert(cx - hx.x + hy.x, cy - hx.y + hy.y, cz - hx.z + hy.z, color, u0, 0);
        n++;
    }

    /** Sprite facing the camera. */
    public void sprite(float x, float y, float z, float size, int cell, float color) {
        quad(x, y, z, a.set(right).scl(size * 0.5f), b.set(up).scl(size * 0.5f), cell, color);
    }

    /** Upright quad that turns around the vertical axis to face the camera (beams, rays). Base at y. */
    public void beam(float x, float y, float z, float width, float height, int cell, float color) {
        a.set(camPos.x - x, 0, camPos.z - z);
        if (a.len2() < 1e-6f) a.set(0, 0, 1);
        a.nor().crs(Vector3.Y).scl(width * 0.5f);
        quad(x, y + height * 0.5f, z, a, b.set(0, height * 0.5f, 0), cell, color);
    }

    /** Flat quad on the ground plane, rotated by angle (radians). */
    public void flat(float x, float y, float z, float size, float angle, int cell, float color) {
        float c = (float) Math.cos(angle) * size * 0.5f, s = (float) Math.sin(angle) * size * 0.5f;
        quad(x, y, z, a.set(c, 0, s), b.set(-s, 0, c), cell, color);
    }

    public void flush(ShaderProgram sp) {
        if (n == 0) return;
        mesh.setVertices(v, 0, n * 24);
        mesh.render(sp, GL20.GL_TRIANGLES, 0, n * 6);
        n = 0;
    }

    public void dispose() { mesh.dispose(); }
}
