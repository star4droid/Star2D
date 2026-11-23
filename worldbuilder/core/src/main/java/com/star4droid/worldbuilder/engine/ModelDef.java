package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ShortArray;

/**
 * One library asset. Mesh assets come from models.bin as parts; every part says, per axis, what happens to it when the
 * element is scaled on that axis only ("smart scale"): stretch, stay glued to the low or high side, keep its size in the
 * middle, or repeat (windows, fence posts, planks). {@link #build} applies that and merges the parts into draw groups.
 */
public final class ModelDef {
    public static final int MESH = 0, WATER = 1, FALL = 2, EFFECT = 3;
    public static final int M_SMALL = 1, M_SCATTER = 2, M_ALIGN = 4, M_SMART = 8, M_FLOAT = 16;
    public static final int F_ALPHA = 1, F_DOUBLE = 2, F_WIND = 4, F_EMISSIVE = 8, F_SLOPE = 16, F_NOSHADOW = 32;
    public static final int S = 0, LO = 1, HI = 2, MID = 3, REP = 4;
    public static final int STRIDE = 9;   // x y z nx ny nz color u v

    public String id, name, cat, sub = "";
    public int kind = MESH, flags;
    public String[] tintNames = new String[0];
    public float[][] tintDefaults = new float[0][];
    public Part[] parts = new Part[0];
    public final BoundingBox bounds = new BoundingBox();
    /** Extents of the model at its natural size (smart scale anchors). */
    float minX, maxX, minZ, maxZ, maxY;
    /** Per axis: the span that stretches when that axis alone is scaled (NaN = the whole model scales). */
    final float[] zoneA = {Float.NaN, Float.NaN, Float.NaN}, zoneB = {Float.NaN, Float.NaN, Float.NaN};
    private Built base;
    private final IntMap<Built> smartCache = new IntMap<>();
    /** Effects / water: default size hint and parameters. */
    public float defScale = 1f;
    public String icon;

    public static final class Part {
        int mat, flags, tint, tint2;
        final int[] mode = new int[3];
        final float[] repA = new float[3], repB = new float[3];
        final int[] repN = {1, 1, 1};
        float slope, sref;
        float[] v;
        short[] idx;
        final float[] c = new float[3];   // center of the part
        int nv() { return v.length / STRIDE; }
    }

    public static final class Group {
        public Mesh mesh;
        public int mat, flags, tint, tint2;
    }

    public static final class Built {
        public Group[] groups;
        public final BoundingBox box = new BoundingBox();
        public int triangles;
        int refs;
    }

    void finish() {
        minX = minZ = Float.MAX_VALUE; maxX = maxZ = maxY = -Float.MAX_VALUE;
        for (Part p : parts) {
            float ax = Float.MAX_VALUE, bx = -Float.MAX_VALUE, ay = ax, by = bx, az = ax, bz = bx;
            for (int i = 0; i < p.v.length; i += STRIDE) {
                float x = p.v[i], y = p.v[i + 1], z = p.v[i + 2];
                ax = Math.min(ax, x); bx = Math.max(bx, x); ay = Math.min(ay, y); by = Math.max(by, y); az = Math.min(az, z); bz = Math.max(bz, z);
            }
            p.c[0] = (ax + bx) / 2; p.c[1] = (ay + by) / 2; p.c[2] = (az + bz) / 2;
            minX = Math.min(minX, ax); maxX = Math.max(maxX, bx); minZ = Math.min(minZ, az); maxZ = Math.max(maxZ, bz); maxY = Math.max(maxY, by);
        }
        if (parts.length == 0) { minX = minZ = -0.5f; maxX = maxZ = 0.5f; maxY = 1; }
        bounds.set(new Vector3(minX, 0, minZ), new Vector3(maxX, maxY, maxZ));
    }

    public boolean smart() { return (flags & M_SMART) != 0; }
    public boolean hasWind() { for (Part p : parts) if ((p.flags & F_WIND) != 0) return true; return false; }

    /** The draw groups for a scale ratio (1,1,1 = the shared default meshes). Call {@link #release} when done with a smart one. */
    public Built build(float rx, float ry, float rz) {
        boolean plain = !smart() || (Math.abs(rx - 1) < 0.02f && Math.abs(ry - 1) < 0.02f && Math.abs(rz - 1) < 0.02f);
        if (plain) {
            if (base == null) base = make(1, 1, 1);
            return base;
        }
        int key = (Math.round(rx * 20) & 1023) | (Math.round(ry * 20) & 1023) << 10 | (Math.round(rz * 20) & 1023) << 20;
        Built b = smartCache.get(key);
        if (b == null) { b = make(Math.round(rx * 20) / 20f, Math.round(ry * 20) / 20f, Math.round(rz * 20) / 20f); smartCache.put(key, b); }
        b.refs++;
        return b;
    }

    public void release(Built b) {
        if (b == null || b == base) return;
        if (--b.refs <= 0) {
            int k = smartCache.findKey(b, true, -1);
            if (k != -1) smartCache.remove(k);
            for (Group g : b.groups) g.mesh.dispose();
        }
    }

    private static final class Acc { FloatArray v = new FloatArray(); ShortArray i = new ShortArray(); int mat, flags, tint, tint2, n; }

    /**
     * Where a coordinate goes when one axis is scaled: below the stretch span things keep their distance to the low end,
     * above it their distance to the high end, and the span itself takes all the extra length. Without a span it is a plain scale.
     */
    private static float map(float p, float lo, float hi, float za, float zb, float r) {
        if (Float.isNaN(za) || zb - za < 0.01f) return p * r;
        if (p <= za) return p + (r - 1) * lo;
        if (p >= zb) return p + (r - 1) * hi;
        float a2 = za + (r - 1) * lo, b2 = zb + (r - 1) * hi;
        return a2 + (p - za) * (b2 - a2) / (zb - za);
    }

    private Built make(float rx, float ry, float rz) {
        Array<Acc> accs = new Array<>();
        float[] r = {rx, ry, rz}, lo = {minX, 0, minZ}, hi = {maxX, maxY, maxZ};
        float[] q = new float[3], nq = new float[3];
        for (Part p : parts) {
            int rflags = p.flags & (F_ALPHA | F_DOUBLE | F_WIND | F_EMISSIVE | F_NOSHADOW);
            boolean slope = (p.flags & F_SLOPE) != 0;
            // roofs: the part above the walls grows with the depth of the walls under it, so the pitch stays
            float sk = 1, sbase = 0;
            if (slope) {
                sk = p.sref > 0.01f ? map(p.sref, lo[2], hi[2], zoneA[2], zoneB[2], r[2]) / p.sref : r[2];
                sbase = map(p.slope, lo[1], hi[1], zoneA[1], zoneB[1], r[1]);
            }
            int[] n = {1, 1, 1};
            float[] a2 = new float[3], b2 = new float[3];
            for (int ax = 0; ax < 3; ax++) {
                if (p.mode[ax] != REP || slope && ax > 0) continue;
                float a = p.repA[ax], b = p.repB[ax];
                a2[ax] = map(a, lo[ax], hi[ax], zoneA[ax], zoneB[ax], r[ax]); b2[ax] = map(b, lo[ax], hi[ax], zoneA[ax], zoneB[ax], r[ax]);
                float span = Math.max(b - a, 0.001f);
                n[ax] = Math.max(1, Math.round(p.repN[ax] * (b2[ax] - a2[ax]) / span));
            }
            for (int ix = 0; ix < n[0]; ix++) for (int iy = 0; iy < n[1]; iy++) for (int iz = 0; iz < n[2]; iz++) {
                int[] ci = {ix, iy, iz};
                float[] off = new float[3];
                for (int ax = 0; ax < 3; ax++) {
                    switch (p.mode[ax]) {
                        case LO: off[ax] = (r[ax] - 1) * lo[ax]; break;
                        case HI: off[ax] = (r[ax] - 1) * hi[ax]; break;
                        case MID: off[ax] = map(p.c[ax], lo[ax], hi[ax], zoneA[ax], zoneB[ax], r[ax]) - p.c[ax]; break;
                        case REP: off[ax] = a2[ax] + (ci[ax] + 0.5f) * (b2[ax] - a2[ax]) / n[ax] - p.c[ax]; break;
                    }
                }
                Acc acc = null;
                int nv = p.nv();
                for (Acc c : accs) if (c.mat == p.mat && c.flags == rflags && c.tint == p.tint && c.tint2 == p.tint2 && c.n + nv < 65000) { acc = c; break; }
                if (acc == null) { acc = new Acc(); acc.mat = p.mat; acc.flags = rflags; acc.tint = p.tint; acc.tint2 = p.tint2; accs.add(acc); }
                int baseIndex = acc.n;
                float[] v = p.v;
                for (int i = 0; i < v.length; i += STRIDE) {
                    for (int ax = 0; ax < 3; ax++) {
                        float c = v[i + ax];
                        if (slope && ax == 1) { q[ax] = sbase + (c - p.slope) * sk; nq[ax] = v[i + 4] / sk; }
                        else if (slope && ax == 2) { q[ax] = c * sk; nq[ax] = v[i + 5] / sk; }
                        else if (p.mode[ax] == S) {
                            q[ax] = map(c, lo[ax], hi[ax], zoneA[ax], zoneB[ax], r[ax]);
                            // the normal follows the local stretch
                            float st = Float.isNaN(zoneA[ax]) || zoneB[ax] - zoneA[ax] < 0.01f ? r[ax] : (c > zoneA[ax] && c < zoneB[ax] ? (zoneB[ax] - zoneA[ax] + (r[ax] - 1) * (hi[ax] - lo[ax])) / (zoneB[ax] - zoneA[ax]) : 1);
                            nq[ax] = v[i + 3 + ax] / Math.max(0.05f, st);
                        } else { q[ax] = c + off[ax]; nq[ax] = v[i + 3 + ax]; }
                    }
                    float l = (float) Math.sqrt(nq[0] * nq[0] + nq[1] * nq[1] + nq[2] * nq[2]);
                    if (l < 1e-6f) l = 1;
                    acc.v.add(q[0], q[1], q[2]); acc.v.add(nq[0] / l, nq[1] / l, nq[2] / l); acc.v.add(v[i + 6], v[i + 7], v[i + 8]);
                }
                for (short sh : p.idx) acc.i.add((short) (baseIndex + (sh & 0xffff)));
                acc.n += nv;
            }
        }
        Built out = new Built();
        out.groups = new Group[accs.size];
        out.box.inf();
        for (int k = 0; k < accs.size; k++) {
            Acc a = accs.get(k);
            Group g = new Group();
            g.mat = a.mat; g.flags = a.flags; g.tint = a.tint; g.tint2 = a.tint2;
            g.mesh = new Mesh(true, a.n, a.i.size, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0));
            g.mesh.setVertices(a.v.items, 0, a.v.size);
            g.mesh.setIndices(a.i.items, 0, a.i.size);
            for (int i = 0; i < a.v.size; i += STRIDE) out.box.ext(a.v.items[i], a.v.items[i + 1], a.v.items[i + 2]);
            out.triangles += a.i.size / 3;
            out.groups[k] = g;
        }
        if (accs.size == 0) out.box.set(bounds);
        return out;
    }

    public void dispose() {
        if (base != null) for (Group g : base.groups) g.mesh.dispose();
        for (Built b : smartCache.values()) for (Group g : b.groups) g.mesh.dispose();
        base = null; smartCache.clear();
    }
}
