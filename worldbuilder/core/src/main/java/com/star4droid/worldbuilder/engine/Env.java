package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Environment of a world: time of day (sun, sky and fog colors follow it), fog, clouds, wind and the water level. */
public final class Env {
    public float time = 10.5f, fog = 0.5f, clouds = 0.55f, wind = 0.6f, waterLevel = -1.2f, sunYaw = 40f;
    public boolean water = true;
    public final float[] shallow = {0.33f, 0.86f, 0.82f}, deep = {0.10f, 0.42f, 0.72f};

    public final Vector3 sunDir = new Vector3();
    public final float[] sunColor = new float[3], ambSky = new float[3], ambGround = new float[3], zenith = new float[3], horizon = new float[3], fogColor = new float[3];

    // hour, sun rgb, ambient sky rgb, zenith rgb, horizon rgb
    private static final float[][] KEYS = {
            {0f, 0.20f, 0.26f, 0.42f, 0.16f, 0.20f, 0.32f, 0.03f, 0.05f, 0.12f, 0.10f, 0.14f, 0.24f},
            {5f, 0.22f, 0.28f, 0.44f, 0.18f, 0.22f, 0.34f, 0.05f, 0.08f, 0.18f, 0.20f, 0.20f, 0.30f},
            {6.3f, 1.10f, 0.62f, 0.38f, 0.42f, 0.40f, 0.48f, 0.25f, 0.38f, 0.62f, 0.98f, 0.66f, 0.50f},
            {8f, 1.12f, 1.00f, 0.84f, 0.52f, 0.60f, 0.72f, 0.24f, 0.52f, 0.88f, 0.74f, 0.86f, 0.96f},
            {12f, 1.12f, 1.08f, 0.98f, 0.56f, 0.64f, 0.76f, 0.20f, 0.50f, 0.92f, 0.70f, 0.86f, 0.98f},
            {16.5f, 1.14f, 1.02f, 0.84f, 0.54f, 0.60f, 0.72f, 0.22f, 0.48f, 0.88f, 0.76f, 0.86f, 0.94f},
            {18.4f, 1.16f, 0.60f, 0.30f, 0.46f, 0.38f, 0.46f, 0.26f, 0.30f, 0.56f, 1.00f, 0.58f, 0.36f},
            {19.6f, 0.30f, 0.30f, 0.46f, 0.22f, 0.24f, 0.38f, 0.07f, 0.09f, 0.22f, 0.30f, 0.22f, 0.34f},
            {24f, 0.20f, 0.26f, 0.42f, 0.16f, 0.20f, 0.32f, 0.03f, 0.05f, 0.12f, 0.10f, 0.14f, 0.24f}};

    public void update() {
        float t = ((time % 24f) + 24f) % 24f;
        int i = 0;
        while (i < KEYS.length - 2 && KEYS[i + 1][0] <= t) i++;
        float[] a = KEYS[i], b = KEYS[i + 1];
        float f = MathUtils.clamp((t - a[0]) / (b[0] - a[0]), 0, 1);
        f = f * f * (3 - 2 * f);
        for (int k = 0; k < 3; k++) {
            sunColor[k] = MathUtils.lerp(a[1 + k], b[1 + k], f);
            ambSky[k] = MathUtils.lerp(a[4 + k], b[4 + k], f);
            zenith[k] = MathUtils.lerp(a[7 + k], b[7 + k], f);
            horizon[k] = MathUtils.lerp(a[10 + k], b[10 + k], f);
            ambGround[k] = ambSky[k] * 0.62f + 0.03f;
            fogColor[k] = horizon[k] * 0.86f + zenith[k] * 0.14f;
        }
        // sun (or moon at night) on an arc: east at 6, zenith at 12, west at 18
        boolean night = t < 5.6f || t > 19.2f;
        float day = night ? ((t < 12 ? t + 24 : t) - 19.2f) / 10.4f : (t - 5.6f) / 13.6f;
        float el = MathUtils.sin(day * MathUtils.PI) * (night ? 55f : 68f) + 9f, az = sunYaw + (day - 0.5f) * 150f;
        float ce = MathUtils.cosDeg(el);
        sunDir.set(ce * MathUtils.sinDeg(az), MathUtils.sinDeg(el), ce * MathUtils.cosDeg(az)).nor();
    }
}
