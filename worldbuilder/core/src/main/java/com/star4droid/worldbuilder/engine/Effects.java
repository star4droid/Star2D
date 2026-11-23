package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * The effect elements. Every effect is a pure function of time (nothing to simulate or save): it emits sprites into the
 * additive batch (light) or the blended batch (smoke, mist, petals). scale.x = radius, scale.y = height, amount = density,
 * speed = animation speed, tintA / tintB = the two colors.
 */
public final class Effects {
    private Effects() {}
    private static final Vector3 hx = new Vector3(), hy = new Vector3(), p = new Vector3();

    static float h(int i, int k) { int n = i * 374761393 + k * 668265263; n = (n ^ (n >>> 13)) * 1274126177; return ((n ^ (n >>> 16)) & 0xffffff) / (float) 0x1000000; }
    static float fract(float x) { return x - (float) Math.floor(x); }
    static float col(float[] a, float[] b, float t, float alpha) { return FxBatch.color(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, alpha); }
    static float smooth(float e0, float e1, float x) { float t = MathUtils.clamp((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); }

    public static void emit(SceneObject o, FxBatch add, FxBatch blend, float time) {
        float t = time * o.speed, R = Math.max(o.scale.x, o.scale.z), H = o.scale.y, x = o.pos.x, y = o.pos.y, z = o.pos.z;
        float[] A = o.tintA, B = o.tintB;
        int seed = o.uid * 31;
        switch (o.def.id) {
            case "fx_pillar": {
                float pulse = 0.82f + 0.18f * MathUtils.sin(t * 2.2f);
                add.beam(x, y, z, R * 2.6f, H, 3, col(A, B, 0.5f, 0.35f * pulse * o.amount));
                add.beam(x, y, z, R * 1.2f, H * 0.96f, 3, col(A, B, 0f, 0.75f * pulse * o.amount));
                add.beam(x, y, z, R * 0.45f, H * 0.9f, 3, FxBatch.color(1, 1, 1, 0.7f * pulse));
                add.flat(x, y + 0.06f, z, R * 4.5f, t * 0.5f, 2, col(A, B, 0.3f, 0.8f));
                add.flat(x, y + 0.05f, z, R * 6f, 0, 0, col(A, B, 1f, 0.5f));
                int n = (int) (26 * o.amount);
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * (0.18f + h(seed + i, 2) * 0.2f)), a = h(seed + i, 3) * MathUtils.PI2 + t * 0.6f, r = R * (0.3f + h(seed + i, 4) * 0.9f);
                    add.sprite(x + MathUtils.cos(a) * r, y + f * H * 0.9f, z + MathUtils.sin(a) * r, 0.12f + 0.12f * h(seed + i, 5), 1, col(A, B, h(seed + i, 6), smooth(0, 0.15f, f) * (1 - f)));
                }
                break;
            }
            case "fx_godrays": {
                int n = (int) (7 * o.amount) + 2;
                for (int i = 0; i < n; i++) {
                    float a = h(seed + i, 1) * MathUtils.PI2, r = R * (float) Math.sqrt(h(seed + i, 2)), w = 0.7f + h(seed + i, 3) * 1.4f;
                    float pulse = 0.45f + 0.55f * (0.5f + 0.5f * MathUtils.sin(t * (0.5f + h(seed + i, 4)) + i));
                    float px = x + MathUtils.cos(a) * r, pz = z + MathUtils.sin(a) * r;
                    hx.set(add.camPos.x - px, 0, add.camPos.z - pz).nor().crs(Vector3.Y).scl(w * 0.5f);
                    hy.set(0.28f, 1, 0.12f).nor().scl(H * 0.5f);
                    add.quad(px + hy.x, y + H * 0.5f, pz + hy.z, hx, hy, 3, col(A, B, h(seed + i, 5), 0.34f * pulse));
                }
                int m = (int) (30 * o.amount);
                for (int i = 0; i < m; i++) {
                    float f = fract(h(seed + i, 7) + t * 0.03f * (0.5f + h(seed + i, 8)));
                    add.sprite(x + (h(seed + i, 9) - 0.5f) * 2 * R, y + (1 - f) * H * 0.8f, z + (h(seed + i, 10) - 0.5f) * 2 * R, 0.05f + 0.06f * h(seed + i, 11), 0, col(A, B, 0, 0.7f * MathUtils.sin(f * MathUtils.PI)));
                }
                break;
            }
            case "fx_orb": {
                float bob = MathUtils.sin(t * 1.4f) * 0.15f, cy = y + H * 0.6f + bob, pulse = 0.85f + 0.15f * MathUtils.sin(t * 3f);
                add.sprite(x, cy, z, R * 7f * pulse, 0, col(A, B, 0, 0.35f * o.amount));
                add.sprite(x, cy, z, R * 3f * pulse, 0, col(A, B, 0.3f, 0.9f));
                add.sprite(x, cy, z, R * 1.3f, 0, FxBatch.color(1, 1, 1, 1));
                add.sprite(x, cy, z, R * 4.5f, 1, col(B, A, 0.5f, 0.6f * pulse));
                for (int i = 0; i < 10; i++) {
                    float a = t * (1.2f + h(seed + i, 1)) + i * 0.63f, tilt = h(seed + i, 2) * MathUtils.PI, r = R * (1.6f + h(seed + i, 3));
                    add.sprite(x + MathUtils.cos(a) * r * MathUtils.cos(tilt), cy + MathUtils.sin(a) * r * 0.6f, z + MathUtils.cos(a) * r * MathUtils.sin(tilt) + MathUtils.sin(a) * r * 0.5f, 0.12f, 0, col(B, A, h(seed + i, 4), 0.9f));
                }
                break;
            }
            case "fx_area_lights": {
                int n = Math.max(4, (int) (10 * o.amount * Math.max(1, R / 4)));
                for (int i = 0; i < n; i++) {
                    float a = i / (float) n * MathUtils.PI2 + t * 0.12f, px = x + MathUtils.cos(a) * R, pz = z + MathUtils.sin(a) * R;
                    float pulse = 0.6f + 0.4f * MathUtils.sin(t * 2.2f - i * 0.9f), py = y + H * (0.55f + 0.12f * MathUtils.sin(t * 1.3f + i));
                    add.sprite(px, py, pz, 1.5f * pulse, 0, col(A, B, 0.5f, 0.5f));
                    add.sprite(px, py, pz, 0.38f, 0, col(A, B, 0, 1f));
                    add.sprite(px, py, pz, 0.9f, 1, col(A, B, 0.2f, 0.5f * pulse));
                    add.beam(px, y, pz, 0.5f, H * 0.6f, 3, col(A, B, 1f, 0.22f));
                    add.flat(px, y + 0.05f, pz, 2.2f, 0, 0, col(A, B, 1f, 0.3f * pulse));
                }
                add.flat(x, y + 0.04f, z, R * 2.08f, t * 0.12f, 2, col(A, B, 0.5f, 0.35f));
                break;
            }
            case "fx_fireflies": {
                int n = (int) (26 * o.amount * Math.max(1, R * R / 25));
                n = Math.min(n, 220);
                for (int i = 0; i < n; i++) {
                    float ph = h(seed + i, 1) * 50, px = x + (h(seed + i, 2) - 0.5f) * 2 * R + MathUtils.sin(t * 0.5f + ph) * 0.9f, pz = z + (h(seed + i, 3) - 0.5f) * 2 * R + MathUtils.cos(t * 0.43f + ph * 1.3f) * 0.9f;
                    float py = y + 0.3f + h(seed + i, 4) * H + MathUtils.sin(t * 0.7f + ph) * 0.35f;
                    float blink = smooth(0.25f, 0.6f, MathUtils.sin(t * (1.1f + h(seed + i, 5)) + ph) * 0.5f + 0.5f);
                    add.sprite(px, py, pz, 0.55f, 0, col(A, B, h(seed + i, 6), 0.55f * blink));
                    add.sprite(px, py, pz, 0.12f, 0, FxBatch.color(1, 1, 0.9f, blink));
                }
                break;
            }
            case "fx_sparkles": {
                int n = Math.min(260, (int) (40 * o.amount * Math.max(1, R * R / 25)));
                for (int i = 0; i < n; i++) {
                    float life = fract(h(seed + i, 1) + t * (0.25f + 0.3f * h(seed + i, 2))); int gen = (int) Math.floor(h(seed + i, 1) + t * (0.25f + 0.3f * h(seed + i, 2)));
                    float px = x + (h(seed + i + gen * 7, 3) - 0.5f) * 2 * R, pz = z + (h(seed + i + gen * 7, 4) - 0.5f) * 2 * R, py = y + 0.2f + h(seed + i + gen * 7, 5) * H + life * 0.4f;
                    float s = MathUtils.sin(life * MathUtils.PI);
                    add.sprite(px, py, pz, (0.18f + 0.3f * h(seed + i, 6)) * s, 1, col(A, B, h(seed + i, 7), s));
                }
                break;
            }
            case "fx_petals": {
                int n = Math.min(240, (int) (40 * o.amount * Math.max(1, R * R / 25)));
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * (0.08f + 0.06f * h(seed + i, 2)));
                    float px = x + (h(seed + i, 3) - 0.5f) * 2 * R + MathUtils.sin(t * 0.8f + i) * 0.6f + f * 1.5f, pz = z + (h(seed + i, 4) - 0.5f) * 2 * R + MathUtils.cos(t * 0.6f + i * 1.7f) * 0.6f;
                    float py = y + (1 - f) * H, rot = t * (1 + h(seed + i, 5) * 2) + i;
                    hx.set(blend.right).scl(0.09f * MathUtils.cos(rot)).mulAdd(blend.up, 0.09f * MathUtils.sin(rot));
                    hy.set(blend.up).scl(0.05f * MathUtils.cos(rot)).mulAdd(blend.right, -0.05f * MathUtils.sin(rot));
                    blend.quad(px, py, pz, hx, hy, 0, col(A, B, h(seed + i, 6), 0.95f * smooth(0, 0.1f, f) * smooth(1, 0.9f, f)));
                }
                break;
            }
            case "fx_mist": {
                int n = Math.min(60, (int) (14 * o.amount * Math.max(1, R / 6)));
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * 0.03f * (0.5f + h(seed + i, 2)));
                    float px = x + (h(seed + i, 3) - 0.5f) * 2 * R + f * 2.5f, pz = z + (h(seed + i, 4) - 0.5f) * 2 * R;
                    blend.sprite(px, y + 0.5f + h(seed + i, 5) * H, pz, 4.5f + 3 * h(seed + i, 6), 0, col(A, B, h(seed + i, 7), 0.2f * MathUtils.sin(f * MathUtils.PI)));
                }
                break;
            }
            case "fx_cubes": {
                int n = Math.min(40, (int) (9 * o.amount) + 2);
                for (int i = 0; i < n; i++) {
                    float a = h(seed + i, 1) * MathUtils.PI2 + t * (0.25f + 0.3f * h(seed + i, 2)) * (i % 2 == 0 ? 1 : -1), r = R * (0.35f + 0.65f * h(seed + i, 3));
                    float px = x + MathUtils.cos(a) * r, pz = z + MathUtils.sin(a) * r, py = y + 0.5f + h(seed + i, 4) * H + MathUtils.sin(t * 1.1f + i) * 0.25f;
                    float s = 0.16f + 0.26f * h(seed + i, 5), spin = t * (0.6f + h(seed + i, 6)) + i;
                    cube(add, px, py, pz, s, spin, spin * 0.7f, col(A, B, h(seed + i, 7), 0.5f), col(A, B, h(seed + i, 7), 0.95f));
                    add.sprite(px, py, pz, s * 5f, 0, col(A, B, h(seed + i, 7), 0.25f));
                }
                break;
            }
            case "fx_circle": {
                add.flat(x, y + 0.06f, z, R * 2.2f, t * 0.4f, 2, col(A, B, 0, 0.95f));
                add.flat(x, y + 0.07f, z, R * 1.5f, -t * 0.7f, 2, col(A, B, 1, 0.9f));
                add.flat(x, y + 0.05f, z, R * 3f, 0, 0, col(A, B, 0.5f, 0.45f));
                int n = 14;
                for (int i = 0; i < n; i++) {
                    float a = i / (float) n * MathUtils.PI2 + t * 0.4f;
                    add.sprite(x + MathUtils.cos(a) * R * 0.88f, y + 0.12f, z + MathUtils.sin(a) * R * 0.88f, 0.3f, 1, col(A, B, i % 2, 1f));
                    add.beam(x + MathUtils.cos(a) * R * 0.88f, y, z + MathUtils.sin(a) * R * 0.88f, 0.3f, H * (0.35f + 0.25f * MathUtils.sin(t * 2 + i)), 3, col(A, B, i % 2, 0.3f));
                }
                int m = (int) (24 * o.amount);
                for (int i = 0; i < m; i++) {
                    float f = fract(h(seed + i, 1) + t * (0.2f + 0.2f * h(seed + i, 2))), a = h(seed + i, 3) * MathUtils.PI2, r = R * (float) Math.sqrt(h(seed + i, 4)) * 0.85f;
                    add.sprite(x + MathUtils.cos(a) * r, y + f * H, z + MathUtils.sin(a) * r, 0.14f, 0, col(A, B, h(seed + i, 5), 1 - f));
                }
                break;
            }
            case "fx_portal": {
                float cy = y + H * 0.5f, ca = MathUtils.cosDeg(o.rotY), sa = MathUtils.sinDeg(o.rotY);
                hx.set(ca, 0, -sa).scl(R); hy.set(0, H * 0.5f, 0);
                add.quad(x, cy, z, hx, hy, 0, col(A, B, 0.2f, 0.75f));
                int n = (int) (60 * o.amount);
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * 0.25f), a = h(seed + i, 2) * MathUtils.PI2 + t * (1.5f + f * 2), r = 1 - f * 0.85f;
                    float lx = MathUtils.cos(a) * r * R, ly = MathUtils.sin(a) * r * H * 0.5f;
                    add.sprite(x + ca * lx, cy + ly, z - sa * lx, 0.16f + 0.3f * (1 - f), i % 3 == 0 ? 1 : 0, col(A, B, f, 0.85f));
                }
                for (int i = 0; i < 28; i++) {
                    float a = i / 28f * MathUtils.PI2 + t * 0.8f, lx = MathUtils.cos(a) * R, ly = MathUtils.sin(a) * H * 0.5f;
                    add.sprite(x + ca * lx, cy + ly, z - sa * lx, 0.55f, 0, col(B, A, 0.5f + 0.5f * MathUtils.sin(a * 3 + t * 2), 0.9f));
                }
                break;
            }
            case "fx_aura": {
                add.flat(x, y + 0.05f, z, R * 2.4f, t, 2, col(A, B, 0, 0.8f));
                int n = (int) (34 * o.amount);
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * (0.35f + 0.3f * h(seed + i, 2))), a = h(seed + i, 3) * MathUtils.PI2 + t * 0.8f, r = R * (0.75f + 0.25f * h(seed + i, 4)) * (1 - f * 0.35f);
                    add.beam(x + MathUtils.cos(a) * r, y + f * H, z + MathUtils.sin(a) * r, 0.22f, 0.6f + 0.6f * h(seed + i, 5), 3, col(A, B, f, 0.8f * MathUtils.sin(f * MathUtils.PI)));
                }
                add.sprite(x, y + H * 0.4f, z, R * 4, 0, col(A, B, 0.5f, 0.18f));
                break;
            }
            case "fx_fire": {
                int n = (int) (22 * o.amount) + 4;
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * (0.7f + 0.5f * h(seed + i, 2))), a = h(seed + i, 3) * MathUtils.PI2, r = R * 0.6f * h(seed + i, 4) * (1 - f * 0.6f);
                    float px = x + MathUtils.cos(a) * r + MathUtils.sin(t * 3 + i) * 0.05f, pz = z + MathUtils.sin(a) * r, py = y + 0.1f + f * H;
                    float s = R * (1.5f - f * 1.1f) * (0.7f + 0.5f * h(seed + i, 5));
                    add.sprite(px, py, pz, s, 0, FxBatch.color(MathUtils.lerp(1f, B[0], f), MathUtils.lerp(0.95f, B[1], f) * (1 - f * 0.4f) * (0.5f + A[1] * 0.5f), MathUtils.lerp(0.6f, B[2], f) * (1 - f), 0.6f * smooth(0, 0.1f, f) * (1 - f)));
                }
                add.sprite(x, y + H * 0.25f, z, R * 6f, 0, col(A, B, 0.3f, 0.22f + 0.05f * MathUtils.sin(t * 9)));
                for (int i = 0; i < 8; i++) {
                    float f = fract(h(seed + i, 6) + t * 0.5f);
                    add.sprite(x + (h(seed + i, 7) - 0.5f) * R * 1.4f + MathUtils.sin(t * 2 + i) * 0.15f * f, y + f * H * 1.7f, z + (h(seed + i, 8) - 0.5f) * R * 1.4f, 0.07f, 0, col(A, B, 0, 1 - f));
                }
                break;
            }
            case "fx_smoke": {
                int n = (int) (14 * o.amount) + 3;
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * (0.12f + 0.06f * h(seed + i, 2)));
                    float px = x + (h(seed + i, 3) - 0.5f) * R + f * f * 1.2f + MathUtils.sin(t * 0.7f + i) * 0.2f * f, pz = z + (h(seed + i, 4) - 0.5f) * R;
                    blend.sprite(px, y + 0.2f + f * H, pz, R * (1.2f + f * 3f), 0, col(B, A, f, 0.42f * smooth(0, 0.12f, f) * (1 - f)));
                }
                break;
            }
            case "fx_embers": {
                int n = Math.min(200, (int) (40 * o.amount));
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * (0.12f + 0.12f * h(seed + i, 2)));
                    float px = x + (h(seed + i, 3) - 0.5f) * 2 * R + MathUtils.sin(t * 1.3f + i) * 0.3f, pz = z + (h(seed + i, 4) - 0.5f) * 2 * R + MathUtils.cos(t * 1.1f + i * 2) * 0.3f;
                    float fl = 0.6f + 0.4f * MathUtils.sin(t * 9 + i * 3);
                    add.sprite(px, y + f * H, pz, 0.07f + 0.07f * h(seed + i, 5), 0, col(A, B, f, fl * smooth(0, 0.1f, f) * (1 - f)));
                }
                break;
            }
            case "fx_fountain": {
                int n = Math.min(260, (int) (70 * o.amount));
                for (int i = 0; i < n; i++) {
                    float f = fract(h(seed + i, 1) + t * 0.55f), a = h(seed + i, 2) * MathUtils.PI2, sp = 0.5f + 0.5f * h(seed + i, 3);
                    float r = R * sp * f, py = y + 0.1f + H * 4 * sp * (f - f * f);
                    blend.sprite(x + MathUtils.cos(a) * r, py, z + MathUtils.sin(a) * r, 0.16f + 0.12f * f, 0, col(A, B, f, 0.85f * (1 - f * 0.7f)));
                }
                blend.flat(x, y + 0.06f, z, R * 2.3f, 0, 2, col(A, B, 1, 0.6f));
                break;
            }
        }
    }

    /** Mist and spray at the foot of a waterfall. */
    public static void fallMist(SceneObject o, FxBatch blend, float time) {
        float w = o.scale.x, ca = MathUtils.cosDeg(o.rotY), sa = MathUtils.sinDeg(o.rotY), t = time * o.speed;
        int n = Math.min(40, (int) (w * 4 * o.amount) + 4), seed = o.uid * 17;
        for (int i = 0; i < n; i++) {
            float f = fract(h(seed + i, 1) + t * (0.35f + 0.25f * h(seed + i, 2))), lx = (h(seed + i, 3) - 0.5f) * w * 1.05f, lz = 0.55f + f * 1.2f * h(seed + i, 4);
            float px = o.pos.x + ca * lx + sa * lz, pz = o.pos.z - sa * lx + ca * lz;
            blend.sprite(px, o.pos.y + 0.15f + MathUtils.sin(f * MathUtils.PI) * (0.5f + h(seed + i, 5)), pz, 0.9f + 1.4f * f, 0, FxBatch.color(1, 1, 1, 0.38f * (1 - f) * smooth(0, 0.1f, f)));
        }
    }

    private static final Vector3 ax = new Vector3(), ay = new Vector3(), az = new Vector3();

    /** A glowing cube made of 6 additive quads (soft faces) plus brighter edges through the ring sprite. */
    static void cube(FxBatch b, float x, float y, float z, float s, float yaw, float pitch, float face, float edge) {
        float cy = MathUtils.cos(yaw), sy = MathUtils.sin(yaw), cp = MathUtils.cos(pitch), sp = MathUtils.sin(pitch);
        ax.set(cy, 0, -sy).scl(s); ay.set(sy * sp, cp, cy * sp).scl(s); az.set(sy * cp, -sp, cy * cp).scl(s);
        Vector3[] n = {ax, ay, az};
        for (int i = 0; i < 3; i++) {
            Vector3 u = n[(i + 1) % 3], v = n[(i + 2) % 3], w = n[i];
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                b.quad(x + w.x * sgn, y + w.y * sgn, z + w.z * sgn, hx.set(u).scl(1.9f), hy.set(v).scl(1.9f), 0, face);
                b.quad(x + w.x * sgn, y + w.y * sgn, z + w.z * sgn, hx.set(u).scl(1.2f), hy.set(v).scl(1.2f), 2, edge);
            }
        }
    }
}
