package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.NumberUtils;

/** Height map terrain in chunks: sculpt (raise, lower, smooth, flatten) and paint (dirt, sand, rock, snow over grass). */
public final class Terrain {
    public static final int N = 128, CH = 16, CN = N / CH, W = N + 1;
    public static final float CELL = 1.5f, SIZE = N * CELL, HALF = SIZE / 2;
    public static final int RAISE = 0, LOWER = 1, SMOOTH = 2, FLATTEN = 3, PAINT = 4, ERASE = 5;
    public static final int GRASS = 0, DIRT = 1, SAND = 2, ROCK = 3, SNOW = 4;

    public final float[] h = new float[W * W];
    public final byte[] splat = new byte[W * W * 4];
    private final Mesh[] mesh = new Mesh[CN * CN];
    private final BoundingBox[] box = new BoundingBox[CN * CN];
    public final boolean[] dirty = new boolean[CN * CN];
    /** Bumped on every height change (water and stuck elements follow). */
    public int version;
    private final float[] verts = new float[(CH + 1) * (CH + 1) * 7];
    private static short[] indices;

    public Terrain() {
        for (int i = 0; i < dirty.length; i++) { dirty[i] = true; box[i] = new BoundingBox(); }
    }

    public static float gx(float x) { return (x + HALF) / CELL; }

    public float heightAt(int ix, int iz) { return h[MathUtils.clamp(iz, 0, N) * W + MathUtils.clamp(ix, 0, N)]; }

    public float height(float x, float z) {
        float fx = MathUtils.clamp(gx(x), 0, N - 0.001f), fz = MathUtils.clamp(gx(z), 0, N - 0.001f);
        int ix = (int) fx, iz = (int) fz;
        float tx = fx - ix, tz = fz - iz;
        float a = h[iz * W + ix], b = h[iz * W + ix + 1], c = h[(iz + 1) * W + ix], d = h[(iz + 1) * W + ix + 1];
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    public Vector3 normal(float x, float z, Vector3 out) {
        float e = CELL * 0.6f;
        return out.set(height(x - e, z) - height(x + e, z), 2 * e, height(x, z - e) - height(x, z + e)).nor();
    }

    private void vnormal(int ix, int iz, Vector3 out) {
        out.set(heightAt(ix - 1, iz) - heightAt(ix + 1, iz), 2 * CELL, heightAt(ix, iz - 1) - heightAt(ix, iz + 1)).nor();
    }

    public boolean inside(float x, float z) { return x > -HALF && x < HALF && z > -HALF && z < HALF; }

    /** First point of the ray on the terrain. */
    public boolean raycast(Ray ray, Vector3 out) {
        float t = 0, step = 0.75f, max = 900;
        Vector3 p = new Vector3();
        float prev = 0; boolean havePrev = false;
        while (t < max) {
            p.set(ray.direction).scl(t).add(ray.origin);
            if (inside(p.x, p.z)) {
                float d = p.y - height(p.x, p.z);
                if (d <= 0) {
                    if (!havePrev) { out.set(p); out.y = height(p.x, p.z); return true; }
                    float lo = t - step, hi = t;
                    for (int i = 0; i < 12; i++) {
                        float m = (lo + hi) / 2; p.set(ray.direction).scl(m).add(ray.origin);
                        if (p.y - height(p.x, p.z) > 0) lo = m; else hi = m;
                    }
                    out.set(ray.direction).scl(hi).add(ray.origin); out.y = height(out.x, out.z);
                    return true;
                }
                prev = d; havePrev = true;
                step = MathUtils.clamp(d * 0.4f, 0.4f, 6f);
            } else { havePrev = false; step = 2f; if (ray.direction.y >= 0 && p.y > 200) return false; }
            t += step;
        }
        return false;
    }

    void markDirty(int ix0, int iz0, int ix1, int iz1) {
        int c0 = MathUtils.clamp((ix0 - 1) / CH, 0, CN - 1), c1 = MathUtils.clamp((ix1 + 1) / CH, 0, CN - 1), d0 = MathUtils.clamp((iz0 - 1) / CH, 0, CN - 1), d1 = MathUtils.clamp((iz1 + 1) / CH, 0, CN - 1);
        for (int cz = d0; cz <= d1; cz++) for (int cx = c0; cx <= c1; cx++) dirty[cz * CN + cx] = true;
    }

    public void markAllDirty() { for (int i = 0; i < dirty.length; i++) dirty[i] = true; version++; }

    /**
     * One brush dab. strength is already multiplied by the frame time.
     * @return the grid rectangle touched {ix0, iz0, ix1, iz1}
     */
    public int[] brush(int mode, float x, float z, float radius, float strength, float target, int layer) {
        int ix0 = MathUtils.clamp((int) Math.floor(gx(x - radius)), 0, N), ix1 = MathUtils.clamp((int) Math.ceil(gx(x + radius)), 0, N);
        int iz0 = MathUtils.clamp((int) Math.floor(gx(z - radius)), 0, N), iz1 = MathUtils.clamp((int) Math.ceil(gx(z + radius)), 0, N);
        float[] tmp = null;
        if (mode == SMOOTH) tmp = h.clone();
        for (int iz = iz0; iz <= iz1; iz++) for (int ix = ix0; ix <= ix1; ix++) {
            float wx = ix * CELL - HALF, wz = iz * CELL - HALF, d = (float) Math.sqrt((wx - x) * (wx - x) + (wz - z) * (wz - z));
            if (d > radius) continue;
            float f = 1 - d / radius; f = f * f * (3 - 2 * f);
            int i = iz * W + ix;
            switch (mode) {
                case RAISE: h[i] += strength * f; break;
                case LOWER: h[i] -= strength * f; break;
                case FLATTEN: h[i] += (target - h[i]) * Math.min(1, strength * f * 0.6f); break;
                case SMOOTH: {
                    float s = 0; int n = 0;
                    for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) { s += tmp[MathUtils.clamp(iz + a, 0, N) * W + MathUtils.clamp(ix + b, 0, N)]; n++; }
                    h[i] += (s / n - h[i]) * Math.min(1, strength * f * 0.8f);
                    break;
                }
                case PAINT: case ERASE: {
                    float amt = Math.min(1, strength * f * 0.5f);
                    for (int k = 0; k < 4; k++) {
                        float v = (splat[i * 4 + k] & 0xff) / 255f;
                        float want = (mode == PAINT && layer == k + 1) ? 1 : 0;
                        // painting grass (layer 0) clears the others; painting a layer lowers the ones above it too
                        v += (want - v) * amt;
                        splat[i * 4 + k] = (byte) Math.round(MathUtils.clamp(v, 0, 1) * 255);
                    }
                    break;
                }
            }
            h[i] = MathUtils.clamp(h[i], -30f, 80f);
        }
        markDirty(ix0, iz0, ix1, iz1);
        if (mode <= FLATTEN) version++;
        return new int[]{ix0, iz0, ix1, iz1};
    }

    /** Rebuilds up to max dirty chunk meshes; returns how many are still dirty. */
    public int rebuild(int max) {
        int left = 0;
        for (int c = 0; c < dirty.length; c++) {
            if (!dirty[c]) continue;
            if (max-- <= 0) { left++; continue; }
            buildChunk(c % CN, c / CN);
            dirty[c] = false;
        }
        return left;
    }

    private void buildChunk(int cx, int cz) {
        int n = CH + 1, k = 0;
        Vector3 nor = new Vector3();
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
            int ix = cx * CH + x, iz = cz * CH + z, i = iz * W + ix;
            float y = h[i];
            vnormal(ix, iz, nor);
            verts[k++] = ix * CELL - HALF; verts[k++] = y; verts[k++] = iz * CELL - HALF;
            verts[k++] = nor.x; verts[k++] = nor.y; verts[k++] = nor.z;
            verts[k++] = NumberUtils.intToFloatColor(((splat[i * 4 + 3] & 0xff) << 24) | ((splat[i * 4 + 2] & 0xff) << 16) | ((splat[i * 4 + 1] & 0xff) << 8) | (splat[i * 4] & 0xff));
            lo = Math.min(lo, y); hi = Math.max(hi, y);
        }
        if (indices == null) {
            indices = new short[CH * CH * 6]; int q = 0;
            for (int z = 0; z < CH; z++) for (int x = 0; x < CH; x++) {
                short a = (short) (z * n + x), b = (short) (a + 1), c = (short) (a + n), d = (short) (c + 1);
                if (((x + z) & 1) == 0) { indices[q++] = a; indices[q++] = c; indices[q++] = b; indices[q++] = b; indices[q++] = c; indices[q++] = d; }
                else { indices[q++] = a; indices[q++] = c; indices[q++] = d; indices[q++] = a; indices[q++] = d; indices[q++] = b; }
            }
        }
        int c = cz * CN + cx;
        if (mesh[c] == null) {
            mesh[c] = new Mesh(false, n * n, indices.length, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorPacked());
            mesh[c].setIndices(indices);
        }
        mesh[c].setVertices(verts, 0, k);
        box[c].set(new Vector3(cx * CH * CELL - HALF, lo, cz * CH * CELL - HALF), new Vector3((cx + 1) * CH * CELL - HALF, hi + 0.01f, (cz + 1) * CH * CELL - HALF));
    }

    public BoundingBox chunkBox(int c) { return box[c]; }

    public int render(ShaderProgram sp, Frustum fr) {
        int drawn = 0;
        for (int c = 0; c < mesh.length; c++) {
            if (mesh[c] == null || (fr != null && !fr.boundsInFrustum(box[c]))) continue;
            mesh[c].render(sp, GL20.GL_TRIANGLES);
            drawn++;
        }
        return drawn;
    }

    public void dispose() {
        for (int i = 0; i < mesh.length; i++) if (mesh[i] != null) { mesh[i].dispose(); mesh[i] = null; }
    }
}
