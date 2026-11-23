package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.NumberUtils;
import com.badlogic.gdx.utils.ShortArray;

/**
 * Water. The world water level fills every part of the terrain that is lower than it (dig to make a pond or a river bed);
 * water elements (pond, lake, river, waterfall) are separate meshes for water above that level.
 * Vertex color: r = depth ((d + 0.5) / 4.5), g = extra foam, b = wave amount, a = edge fade.
 */
public final class Water {
    private static final int STEP = 2, CW = Terrain.CH / STEP + 1;
    private final Mesh[] mesh = new Mesh[Terrain.CN * Terrain.CN];
    private final boolean[] has = new boolean[mesh.length];
    private final int[] builtVersion = new int[mesh.length];
    private float builtLevel = Float.NaN;
    private final float[] verts = new float[CW * CW * 6];
    private static short[] indices;

    static float col(float depth, float foam, float wave, float alpha) {
        int r = (int) (MathUtils.clamp((depth + 0.5f) / 4.5f, 0, 1) * 255), g = (int) (MathUtils.clamp(foam, 0, 1) * 255), b = (int) (MathUtils.clamp(wave, 0, 1) * 255), a = (int) (MathUtils.clamp(alpha, 0, 1) * 255);
        return NumberUtils.intToFloatColor((a << 24) | (b << 16) | (g << 8) | r);
    }

    /** Rebuilds the chunks whose terrain changed (or all of them when the level changed). */
    public void update(Terrain t, float level, boolean[] terrainDirty, int version) {
        boolean all = level != builtLevel;
        builtLevel = level;
        for (int c = 0; c < mesh.length; c++) {
            if (!all && builtVersion[c] == version) continue;
            build(t, level, c % Terrain.CN, c / Terrain.CN);
            builtVersion[c] = version;
        }
    }

    /** Marks chunks to rebuild after a brush stroke over the grid rectangle. */
    public void touch(int ix0, int iz0, int ix1, int iz1) {
        int c0 = MathUtils.clamp((ix0 - 2) / Terrain.CH, 0, Terrain.CN - 1), c1 = MathUtils.clamp((ix1 + 2) / Terrain.CH, 0, Terrain.CN - 1), d0 = MathUtils.clamp((iz0 - 2) / Terrain.CH, 0, Terrain.CN - 1), d1 = MathUtils.clamp((iz1 + 2) / Terrain.CH, 0, Terrain.CN - 1);
        for (int cz = d0; cz <= d1; cz++) for (int cx = c0; cx <= c1; cx++) builtVersion[cz * Terrain.CN + cx] = -1;
    }

    private void build(Terrain t, float level, int cx, int cz) {
        int k = 0; boolean any = false;
        for (int z = 0; z < CW; z++) for (int x = 0; x < CW; x++) {
            int ix = cx * Terrain.CH + x * STEP, iz = cz * Terrain.CH + z * STEP;
            float d = level - t.heightAt(ix, iz);
            if (d > -0.3f) any = true;
            float wx = ix * Terrain.CELL - Terrain.HALF, wz = iz * Terrain.CELL - Terrain.HALF;
            verts[k++] = wx; verts[k++] = level; verts[k++] = wz;
            verts[k++] = col(d, 0, 1, 1); verts[k++] = wx; verts[k++] = wz;
        }
        int c = cz * Terrain.CN + cx;
        has[c] = any;
        if (!any) return;
        if (indices == null) {
            int n = CW - 1; indices = new short[n * n * 6]; int q = 0;
            for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
                short a = (short) (z * CW + x), b = (short) (a + 1), cc = (short) (a + CW), d = (short) (cc + 1);
                indices[q++] = a; indices[q++] = cc; indices[q++] = b; indices[q++] = b; indices[q++] = cc; indices[q++] = d;
            }
        }
        if (mesh[c] == null) {
            mesh[c] = new Mesh(false, CW * CW, indices.length, VertexAttribute.Position(), VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0));
            mesh[c].setIndices(indices);
        }
        mesh[c].setVertices(verts, 0, k);
    }

    private Mesh outer;
    private float outerLevel = Float.NaN;

    /** The open sea around the terrain square. */
    private void buildOuter(float level) {
        float a = Terrain.HALF, b = 2600;
        float[][] q = {{-b, -b, b, -a}, {-b, a, b, b}, {-b, -a, -a, a}, {a, -a, b, a}};
        float[] v = new float[4 * 4 * 6]; short[] idx = new short[24]; int k = 0;
        float c = col(4f, 0, 1, 1);
        for (int i = 0; i < 4; i++) {
            float[] r = q[i];
            float[] xs = {r[0], r[2], r[2], r[0]}, zs = {r[1], r[1], r[3], r[3]};
            for (int j = 0; j < 4; j++) { v[k++] = xs[j]; v[k++] = level; v[k++] = zs[j]; v[k++] = c; v[k++] = xs[j]; v[k++] = zs[j]; }
            int o = i * 4; idx[i * 6] = (short) o; idx[i * 6 + 1] = (short) (o + 2); idx[i * 6 + 2] = (short) (o + 1); idx[i * 6 + 3] = (short) o; idx[i * 6 + 4] = (short) (o + 3); idx[i * 6 + 5] = (short) (o + 2);
        }
        if (outer == null) { outer = new Mesh(false, 16, 24, VertexAttribute.Position(), VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0)); outer.setIndices(idx); }
        outer.setVertices(v);
        outerLevel = level;
    }

    public void render(ShaderProgram sp, Terrain t, Frustum fr) {
        if (outerLevel != builtLevel) buildOuter(builtLevel);
        outer.render(sp, GL20.GL_TRIANGLES);
        for (int c = 0; c < mesh.length; c++) {
            if (!has[c] || mesh[c] == null || !fr.boundsInFrustum(t.chunkBox(c).getCenterX(), builtLevel, t.chunkBox(c).getCenterZ(), Terrain.CH * Terrain.CELL / 2, 1, Terrain.CH * Terrain.CELL / 2)) continue;
            mesh[c].render(sp, GL20.GL_TRIANGLES);
        }
    }

    public void dispose() { for (int i = 0; i < mesh.length; i++) if (mesh[i] != null) { mesh[i].dispose(); mesh[i] = null; } if (outer != null) { outer.dispose(); outer = null; } }

    // ------------------------------------------------------------------ water elements (local meshes, depth from the terrain under them)
    private static final Vector3 tmp = new Vector3();

    /** A still or flowing water surface in local units (x, z in -0.5..0.5 scaled by the element): disc, rounded lake, square, river strip. */
    public static Mesh surface(String id, Matrix4 world, Terrain t, Mesh reuse) {
        FloatArray v = new FloatArray(); ShortArray idx = new ShortArray();
        boolean river = id.equals("water_river") || id.equals("water_stream");
        if (river) {
            int nx = 6, nz = 24;
            for (int z = 0; z <= nz; z++) for (int x = 0; x <= nx; x++) {
                float fx = x / (float) nx - 0.5f, fz = z / (float) nz - 0.5f;
                float edge = 1 - Math.abs(fx) * 2, ends = Math.min(1, (0.5f - Math.abs(fz)) * 12);
                put(v, world, t, fx, fz, MathUtils.clamp(edge * 2.4f, 0, 1) * MathUtils.clamp(ends, 0, 1), edge < 0.2f ? 0.5f : 0f);
            }
            grid(idx, nx, nz);
        } else if (id.equals("water_square")) {
            int n = 10;
            for (int z = 0; z <= n; z++) for (int x = 0; x <= n; x++) put(v, world, t, x / (float) n - 0.5f, z / (float) n - 0.5f, 1, 0);
            grid(idx, n, n);
        } else {
            // disc with rings; the lake outline wobbles
            int rings = 7, seg = 28; boolean lake = id.equals("water_lake");
            put(v, world, t, 0, 0, 1, 0);
            for (int r = 1; r <= rings; r++) for (int s = 0; s < seg; s++) {
                float a = s / (float) seg * MathUtils.PI2, rad = 0.5f * r / rings;
                if (lake) rad *= 0.82f + 0.12f * MathUtils.sin(a * 3 + 0.6f) + 0.07f * MathUtils.sin(a * 5 + 2.1f);
                put(v, world, t, MathUtils.cos(a) * rad, MathUtils.sin(a) * rad, r == rings ? 0 : 1, 0);
            }
            for (int s = 0; s < seg; s++) { idx.add(0); idx.add(1 + (s + 1) % seg); idx.add(1 + s); }
            for (int r = 1; r < rings; r++) for (int s = 0; s < seg; s++) {
                int a = 1 + (r - 1) * seg + s, b = 1 + (r - 1) * seg + (s + 1) % seg, c = a + seg, d = b + seg;
                idx.add(a); idx.add(b); idx.add(c); idx.add(b); idx.add(d); idx.add(c);
            }
        }
        return toMesh(v, idx, reuse);
    }

    private static void put(FloatArray v, Matrix4 world, Terrain t, float x, float z, float alpha, float foam) {
        tmp.set(x, 0, z).mul(world);
        float d = t == null ? 2f : tmp.y - t.height(tmp.x, tmp.z);
        v.add(x, 0, z); v.add(col(d, foam, 0.6f, alpha)); v.add(x, z);
    }

    private static void grid(ShortArray idx, int nx, int nz) {
        for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) {
            int a = z * (nx + 1) + x, b = a + 1, c = a + nx + 1, d = c + 1;
            idx.add(a); idx.add(c); idx.add(b); idx.add(b); idx.add(c); idx.add(d);
        }
    }

    private static Mesh toMesh(FloatArray v, ShortArray idx, Mesh reuse) {
        Mesh m = reuse;
        if (m == null || m.getMaxVertices() < v.size / 6 || m.getMaxIndices() < idx.size) {
            if (m != null) m.dispose();
            m = new Mesh(false, v.size / 6, idx.size, VertexAttribute.Position(), VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0));
        }
        m.setVertices(v.items, 0, v.size);
        m.setIndices(idx.items, 0, idx.size);
        return m;
    }

    /**
     * Waterfall sheet in local units already at its real size (w wide, h high): flows over the lip at the top (toward +z),
     * falls and spreads a little at the foot. uv is in meters so the streaks keep their size and speed at any scale.
     */
    /** How far the water runs back over the edge before it falls (it overlaps the river or pond above). */
    public static final float LIP = 2.4f;

    public static Mesh fall(float w, float h, Mesh reuse) {
        FloatArray v = new FloatArray(); ShortArray idx = new ShortArray();
        int nx = Math.max(4, Math.min(24, Math.round(w * 2.2f))), ny = 14;
        float len = 0, py = h, pz = -LIP;
        for (int j = 0; j <= ny; j++) {
            float f = j / (float) ny, y, z;
            if (f < 0.14f) { float k = f / 0.14f; y = h + 0.02f - k * k * k * 0.25f; z = -LIP + k * (LIP + 0.15f); }
            else { float k = (f - 0.14f) / 0.86f; y = (h - 0.23f) * (1 - k); z = 0.15f + k * 0.4f + MathUtils.sin(k * MathUtils.PI) * 0.12f; }
            len += (float) Math.sqrt((y - py) * (y - py) + (z - pz) * (z - pz)); py = y; pz = z;
            for (int i = 0; i <= nx; i++) {
                float fx = i / (float) nx, x = (fx - 0.5f) * w * (1 + (f > 0.14f ? (f - 0.14f) * 0.12f : 0));
                float edge = MathUtils.clamp(Math.min(fx, 1 - fx) * w * 2.2f, 0, 1);
                float foam = f > 0.86f ? (f - 0.86f) / 0.14f : 0;
                float top = MathUtils.clamp(f * 12f, 0, 1);
                v.add(x, y, z);
                v.add(NumberUtils.intToFloatColor(((int) (top * 255) << 24) | (255 << 16) | ((int) (foam * 255) << 8) | (int) (edge * 255)));
                v.add(x, len);
            }
        }
        grid(idx, nx, ny);
        return toMesh(v, idx, reuse);
    }
}
