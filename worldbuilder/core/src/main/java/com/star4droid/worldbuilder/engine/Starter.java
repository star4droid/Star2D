package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.Random;

/** Builds the worlds offered by "New world": an island with a plateau, a waterfall, a river, a lake and a village; or an empty meadow. */
public final class Starter {
    private Starter() {}

    static float hash(int x, int z) { int n = x * 374761393 + z * 668265263; n = (n ^ (n >>> 13)) * 1274126177; return ((n ^ (n >>> 16)) & 0xffffff) / (float) 0x1000000; }
    static float vnoise(float x, float z) {
        int ix = MathUtils.floor(x), iz = MathUtils.floor(z); float fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        return MathUtils.lerp(MathUtils.lerp(hash(ix, iz), hash(ix + 1, iz), fx), MathUtils.lerp(hash(ix, iz + 1), hash(ix + 1, iz + 1), fx), fz);
    }
    static float fbm(float x, float z) { return vnoise(x, z) * 0.55f + vnoise(x * 2.1f + 7, z * 2.1f + 3) * 0.3f + vnoise(x * 4.3f + 1, z * 4.3f + 9) * 0.15f; }
    static float ss(float a, float b, float x) { float t = MathUtils.clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }

    static float segDist(float px, float pz, float ax, float az, float bx, float bz) {
        float dx = bx - ax, dz = bz - az, t = MathUtils.clamp(((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz), 0, 1);
        float cx = ax + dx * t - px, cz = az + dz * t - pz;
        return (float) Math.sqrt(cx * cx + cz * cz);
    }

    // river from the foot of the waterfall to the lake and on to the sea
    static final float[] RIVER = {-4, -15.5f, -3, -6, 2, 5, 6, 16, 5, 29, 9, 44, 20, 62, 30, 90};
    static final float FALL_X = -4, CLIFF_Z = -15.5f, PLATEAU = 8.4f, WATER = 0.3f, FALL_Z = -15f;

    static float riverDist(float x, float z) {
        float d = 1e9f;
        for (int i = 0; i + 3 < RIVER.length; i += 2) d = Math.min(d, segDist(x, z, RIVER[i], RIVER[i + 1], RIVER[i + 2], RIVER[i + 3]));
        return d;
    }
    static float cliffLine(float x) { return CLIFF_Z + 7 * MathUtils.sin(x * 0.055f + 0.2f) * ss(7, 22, Math.abs(x - FALL_X)) + (fbm(x * 0.2f, 3) - 0.5f) * 3 * ss(5, 12, Math.abs(x - FALL_X)); }

    static float height(float x, float z) {
        float r = (float) Math.sqrt(x * x + z * z * 0.92f), n = fbm(x * 0.045f + 11, z * 0.045f + 5);
        float land = ss(88, 64, r + (n - 0.5f) * 22);
        float h = 0.95f + fbm(x * 0.07f, z * 0.07f) * 1.5f + (fbm(x * 0.25f + 4, z * 0.25f) - 0.5f) * 0.25f;
        // plateau behind the cliff line
        // at the waterfall the cliff is as steep as the ground grid allows, so the falling water hangs free in front of it
        float cl = cliffLine(x), notch = ss(6.5f, 3.6f, Math.abs(x - FALL_X));
        float up = ss(MathUtils.lerp(cl + 0.2f, FALL_Z, notch), MathUtils.lerp(cl - 2.0f, FALL_Z - 1.5f, notch), z);
        float top = PLATEAU + fbm(x * 0.06f + 2, z * 0.06f + 8) * 1.6f + ss(-30, -70, z) * 3f * fbm(x * 0.03f, z * 0.03f + 20);
        // channel of the upper river
        float ch = Math.abs(x - (FALL_X + MathUtils.sin(z * 0.09f) * 1.2f * ss(-22, -34, z)));
        top = MathUtils.lerp(PLATEAU - 0.45f, top, ss(1.9f, 4.2f, ch));
        h = MathUtils.lerp(h, top, up);
        // lower river, lake
        float rd = riverDist(x, z), lake = (float) Math.sqrt((x - 5) * (x - 5) + (z - 30) * (z - 30) * 1.25f) - 9.5f - (fbm(x * 0.15f, z * 0.15f) - 0.5f) * 5;
        float wd = Math.min(rd - 2.1f, lake);
        if (z > cl - 0.5f) h = MathUtils.lerp(Math.min(h, -1.5f + ss(-3, 2.2f, wd) * 0.9f), h, ss(0.2f, 3.4f, wd));
        // pool at the foot of the fall
        float pool = (float) Math.sqrt((x - FALL_X) * (x - FALL_X) + (z - CLIFF_Z - 3.2f) * (z - CLIFF_Z - 3.2f));
        if (z > MathUtils.lerp(cl + 0.4f, FALL_Z - 0.1f, notch)) h = MathUtils.lerp(Math.min(h, -1.4f), h, ss(3.6f, 6.2f, pool));
        return MathUtils.lerp(-7.5f, h, land);
    }

    /** A flat meadow by the sea (the "Empty" choice). */
    public static Scene empty(String name) {
        Scene s = new Scene();
        s.name = name;
        Terrain t = s.terrain;
        for (int iz = 0; iz <= Terrain.N; iz++) for (int ix = 0; ix <= Terrain.N; ix++) {
            float x = ix * Terrain.CELL - Terrain.HALF, z = iz * Terrain.CELL - Terrain.HALF, r = (float) Math.sqrt(x * x + z * z);
            float land = ss(90, 70, r + (fbm(x * 0.05f, z * 0.05f) - 0.5f) * 14);
            float h = MathUtils.lerp(-6, 1.2f + fbm(x * 0.06f, z * 0.06f) * 0.5f, land);
            t.h[iz * Terrain.W + ix] = h;
            if (h < WATER + 0.75f) t.splat[(iz * Terrain.W + ix) * 4 + 1] = (byte) (255 * ss(WATER + 0.75f, WATER + 0.3f, h));
        }
        s.env.waterLevel = WATER;
        t.markAllDirty();
        return s;
    }

    public static Scene island(String name) {
        Scene s = new Scene();
        s.name = name;
        Terrain t = s.terrain;
        Random rnd = new Random(7);
        for (int iz = 0; iz <= Terrain.N; iz++) for (int ix = 0; ix <= Terrain.N; ix++) {
            float x = ix * Terrain.CELL - Terrain.HALF, z = iz * Terrain.CELL - Terrain.HALF;
            t.h[iz * Terrain.W + ix] = height(x, z);
        }
        // paint: sand by the water, a dirt road through the village and to the bridge, darker earth under the forest
        float[] road = {4.5f, 3.2f, 14, 3, 24, 5, 34, 2, 24, 5, 26, 16, -1, 7.2f, -12, 9, -22, 12};
        for (int iz = 0; iz <= Terrain.N; iz++) for (int ix = 0; ix <= Terrain.N; ix++) {
            float x = ix * Terrain.CELL - Terrain.HALF, z = iz * Terrain.CELL - Terrain.HALF, h = t.h[iz * Terrain.W + ix];
            int i = (iz * Terrain.W + ix) * 4;
            float sand = ss(WATER + 0.85f, WATER + 0.25f, h + (fbm(x * 0.3f, z * 0.3f) - 0.5f) * 0.5f);
            float rdist = 1e9f;
            for (int k = 0; k + 3 < road.length; k += 2) { if (k == 6 || k == 10) continue; rdist = Math.min(rdist, segDist(x, z, road[k], road[k + 1], road[k + 2], road[k + 3])); }
            float dirt = ss(2.6f, 1.0f, rdist + (fbm(x * 0.5f, z * 0.5f) - 0.5f) * 1.6f) * (h < 5 ? 1 : 0);
            dirt = Math.max(dirt, ss(0.62f, 0.8f, fbm(x * 0.09f + 30, z * 0.09f)) * 0.55f);
            t.splat[i] = (byte) (255 * MathUtils.clamp(dirt, 0, 1));
            t.splat[i + 1] = (byte) (255 * sand);
        }
        t.markAllDirty();

        Env e = s.env;
        e.waterLevel = WATER; e.time = 10.2f; e.fog = 0.45f; e.clouds = 0.5f; e.wind = 0.55f; e.sunYaw = 215;
        s.camera[0] = 2; s.camera[1] = 1.5f; s.camera[2] = 2; s.camera[3] = 28; s.camera[4] = 27; s.camera[5] = 62;

        // upper river and the waterfall
        SceneObject up = put(s, "water_river", FALL_X, PLATEAU - 0.02f, CLIFF_Z - 2.3f - 23, 0, 1); up.scale.set(7.5f, 1, 46); up.name = "Upper river"; up.speed = 1.2f;
        SceneObject fall = put(s, "water_fall_wide", FALL_X, WATER - 0.05f, FALL_Z + 0.05f, 0, 1); fall.scale.set(4.6f, PLATEAU - WATER, 1);
        put(s, "fx_mist", FALL_X, WATER, CLIFF_Z + 1.5f, 0, 1).scale.set(4, 1.2f, 4);
        // rocks framing the fall and along the cliff
        String[] cliffs = {"cliff_block", "cliff_wall", "cliff_mossy", "rock_tall", "rock_boulder", "rock_mossy"};
        for (int k = -1; k <= 1; k += 2) {
            ground(s, "cliff_mossy", FALL_X + k * 4.3f, CLIFF_Z - 1.2f, k * 25 + 180, 1.25f, -0.6f);
            ground(s, "rock_mossy", FALL_X + k * 3.5f, CLIFF_Z + 1.1f, k * 70, 1.1f, -0.3f);
            ground(s, "rock_boulder", FALL_X + k * 3.3f, CLIFF_Z - 3.3f, k * 40, 1.0f, -0.25f);
            ground(s, "rock_round", FALL_X + k * 5.6f, CLIFF_Z + 2.6f, k * 110, 0.9f, -0.2f);
        }
        for (int i = 0; i < 26; i++) {
            float x = -66 + i * 5.2f + rnd.nextFloat() * 2;
            if (Math.abs(x - FALL_X) < 9) continue;
            float z = cliffLine(x) - 0.4f + rnd.nextFloat() * 1.6f;
            if (s.terrain.height(x, z + 2) < WATER) continue;
            ground(s, cliffs[rnd.nextInt(cliffs.length)], x, z, rnd.nextFloat() * 360, 0.8f + rnd.nextFloat() * 0.6f, -0.5f);
        }
        // river rocks, stepping stones, plants on the water
        float[] rr = {-3.6f, -9, -1.8f, -2, 1.2f, 2.4f, 5.2f, 11.5f, 6.4f, 19, -5.2f, -11.5f, 3.6f, 8.6f};
        for (int i = 0; i < rr.length; i += 2) ground(s, i % 4 == 0 ? "rock_round" : "rock_mossy_flat", rr[i] + (rnd.nextFloat() - 0.5f) * 2.4f, rr[i + 1], rnd.nextFloat() * 360, 0.55f + rnd.nextFloat() * 0.4f, -0.25f);
        SceneObject st = put(s, "stepping_stones", 4.6f, WATER - 0.05f, 21.5f, 12, 1.5f); st.scale.set(2.3f, 1.5f, 1.5f);
        for (int i = 0; i < 9; i++) {
            float a = rnd.nextFloat() * MathUtils.PI2, r = 2 + rnd.nextFloat() * 5.5f, x = 5 + MathUtils.cos(a) * r, z = 30 + MathUtils.sin(a) * r * 0.85f;
            if (s.terrain.height(x, z) > WATER - 0.35f) continue;
            put(s, i % 3 == 0 ? "lily_lotus" : "lily_pads", x, WATER + 0.03f, z, rnd.nextFloat() * 360, 0.9f + rnd.nextFloat() * 0.5f);
        }
        // bridge (stretched on one axis: more planks, same posts) and the road signs
        SceneObject br = put(s, "bridge_wood", 1.9f, 1.25f, 5.0f, 12, 1); br.scale.set(1.7f, 1, 1.15f);
        ground(s, "signpost", 8.4f, 4.6f, 200, 1, 0);
        ground(s, "lantern_post", 7.3f, 1.6f, 180, 1, 0);
        ground(s, "lantern_post", -3.6f, 8.6f, 0, 1, 0);

        // village
        float[][] houses = {{16, -4, 25}, {27, -3, -10}, {37, 6, -80}, {30, 14, 190}, {17, 13, 160}};
        String[] hid = {"house_cottage", "house_stone", "house_tall", "house_small", "barn"};
        float[][] wall = {{0.93f, 0.89f, 0.80f}, {0.74f, 0.72f, 0.70f}, {0.90f, 0.80f, 0.66f}, {0.86f, 0.90f, 0.92f}, {0.72f, 0.34f, 0.28f}};
        float[][] roof = {{0.72f, 0.30f, 0.22f}, {0.30f, 0.42f, 0.55f}, {0.42f, 0.50f, 0.34f}, {0.78f, 0.45f, 0.22f}, {0.36f, 0.30f, 0.30f}};
        for (int i = 0; i < houses.length; i++) {
            float[] h = houses[i];
            flatten(s, h[0], h[1], 6.2f);
            SceneObject o = ground(s, hid[i], h[0], h[1], h[2], 1, 0);
            System.arraycopy(wall[i], 0, o.tintA, 0, 3); System.arraycopy(roof[i], 0, o.tintB, 0, 3);
        }
        s.find(s.nextUid - 4).scale.set(1.45f, 1, 1);          // a longer stone house: the smart scale adds windows instead of stretching them
        ground(s, "well", 24.5f, 5.5f, 30, 1, 0);
        SceneObject stall = ground(s, "market_stall", 20.5f, 5.2f, 100, 1, 0); stall.tintA[0] = 0.85f; stall.tintA[1] = 0.25f; stall.tintA[2] = 0.22f;
        SceneObject stall2 = ground(s, "market_stall", 21.2f, 9.3f, 80, 1, 0); stall2.tintA[0] = 0.2f; stall2.tintA[1] = 0.5f; stall2.tintA[2] = 0.8f;
        ground(s, "cart", 28.5f, 7.2f, 40, 1, 0);
        ground(s, "barrel", 19.2f, 3.2f, 0, 1, 0); ground(s, "barrel", 19.9f, 2.6f, 50, 0.9f, 0); ground(s, "crate", 22.6f, 3.6f, 20, 0.8f, 0); ground(s, "crate", 23.4f, 3.2f, 70, 0.7f, 0);
        ground(s, "bench", 26.3f, 3.2f, 200, 1, 0);
        ground(s, "haystack", 13.4f, 17.5f, 0, 1, 0); ground(s, "haystack", 15.3f, 18.6f, 60, 0.85f, 0);
        ground(s, "lantern_post", 22.8f, 7.4f, 90, 1, 0); ground(s, "lantern_post", 31.2f, 4.2f, 270, 1, 0);
        ground(s, "flag", 25.2f, 8.2f, 20, 1, 0);
        // garden fence around the cottage (one fence piece stretched: more posts, not wider posts)
        SceneObject f1 = ground(s, "fence_wood", 14.6f, -9.4f, 25, 1, 0); f1.scale.set(2.4f, 1, 1);
        SceneObject f2 = ground(s, "fence_wood", 9.6f, -3.4f, 115, 1, 0); f2.scale.set(1.9f, 1, 1);
        SceneObject f3 = ground(s, "fence_picket", 33.4f, 16.6f, 10, 1, 0); f3.scale.set(1.6f, 1, 1);
        SceneObject w1 = ground(s, "wall_low", 39, -3, 60, 1, 0); w1.scale.set(2.2f, 1, 1);

        // camp on the west bank
        ground(s, "tent", -20, 12.5f, 140, 1.15f, 0);
        ground(s, "campfire", -16.2f, 10.4f, 0, 1, 0);
        ground(s, "log", -14.8f, 12.2f, 60, 1, 0); ground(s, "log_pile", -18.6f, 8.2f, 100, 1, 0); ground(s, "stump", -14.2f, 8.6f, 0, 1, 0);
        float cy = s.terrain.height(-16.2f, 10.4f);
        put(s, "fx_fire", -16.2f, cy + 0.12f, 10.4f, 0, 1); put(s, "fx_smoke", -16.2f, cy + 1.1f, 10.4f, 0, 1).scale.set(0.5f, 3.4f, 0.5f);
        // ruins with magic
        float rx = -34, rz = -2;
        flatten(s, rx, rz, 7);
        ground(s, "ruin_arch", rx, rz - 4.4f, 0, 1, 0); ground(s, "pillar", rx - 4.6f, rz - 1.4f, 0, 1, 0); ground(s, "pillar_broken", rx + 4.6f, rz - 1.4f, 30, 1, 0);
        ground(s, "pillar", rx - 4.6f, rz + 3.2f, 0, 1, 0); ground(s, "pillar_broken", rx + 4.4f, rz + 3.4f, 200, 0.9f, 0);
        SceneObject rw = ground(s, "ruin_wall", rx - 6.6f, rz - 4.6f, 70, 1, 0); rw.scale.set(1.3f, 1, 1);
        ground(s, "statue", rx, rz + 0.4f, 180, 1.1f, 0);
        float ry = s.terrain.height(rx, rz);
        put(s, "fx_circle", rx, ry + 0.02f, rz + 0.4f, 0, 1).scale.set(3.2f, 2.4f, 3.2f);
        put(s, "fx_pillar", rx, ry, rz + 0.4f, 0, 1).scale.set(0.9f, 14, 0.9f);
        put(s, "fx_cubes", rx, ry + 0.6f, rz + 0.4f, 0, 1).scale.set(3.4f, 3.2f, 3.4f);
        // tower on the plateau, with lights around it
        flatten(s, 26, -38, 7);
        ground(s, "tower", 26, -38, 20, 1, 0);
        put(s, "fx_area_lights", 26, s.terrain.height(26, -38), -38, 0, 1).scale.set(6.2f, 1.3f, 6.2f);
        ground(s, "tower_open", -30, -42, 0, 1, 0);
        ground(s, "dock", 13.4f, 32.5f, 195, 1, 0).pos.y = WATER + 0.25f;

        // forest, bushes, flowers, grass
        String[] trees = {"tree_oak", "tree_oak_big", "tree_round", "tree_maple", "tree_birch", "tree_young", "tree_oak", "tree_round"};
        String[] pines = {"tree_pine", "tree_pine_tall", "tree_fir_blue", "tree_pine"};
        int placed = 0;
        for (int i = 0; i < 900 && placed < 96; i++) {
            float x = (rnd.nextFloat() - 0.5f) * 150, z = (rnd.nextFloat() - 0.5f) * 150;
            if (!free(s, x, z, 3.2f) || nearVillage(x, z, 9)) continue;
            boolean high = s.terrain.height(x, z) > 5;
            float dens = fbm(x * 0.05f + 40, z * 0.05f + 12);
            if (dens < (high ? 0.42f : 0.5f)) continue;
            String id = high ? pines[rnd.nextInt(pines.length)] : (x < -8 && rnd.nextFloat() < 0.16f ? "tree_autumn" : trees[rnd.nextInt(trees.length)]);
            ground(s, id, x, z, rnd.nextFloat() * 360, 0.85f + rnd.nextFloat() * 0.5f, -0.08f);
            placed++;
        }
        float[][] special = {{-9.5f, 15.5f}, {-7, 24.5f}, {17.5f, 26}, {11, -8.5f}, {-11, -6.5f}};
        String[] sp = {"tree_willow", "tree_sakura", "tree_willow", "tree_sakura", "tree_sakura"};
        for (int i = 0; i < special.length; i++) ground(s, sp[i], special[i][0], special[i][1], i * 77, 1.1f, -0.08f);
        put(s, "fx_petals", -7, s.terrain.height(-7, 24.5f), 24.5f, 0, 1).scale.set(4, 4.5f, 4);
        put(s, "fx_fireflies", 5, WATER + 0.2f, 30, 0, 1).scale.set(10, 2.5f, 10);
        put(s, "fx_godrays", -24, s.terrain.height(-24, 24), 24, 0, 1).scale.set(5, 10, 5);
        ground(s, "tree_palm", 30, 52, 40, 1.1f, -0.1f); ground(s, "tree_palm", 34, 49, 160, 0.95f, -0.1f); ground(s, "tree_dead", -44, 30, 0, 1.1f, -0.05f);

        String[] bushes = {"bush", "bush_big", "bush_flower_pink", "bush_flower_white", "bush_berry", "bush"};
        placed = 0;
        for (int i = 0; i < 600 && placed < 46; i++) {
            float x = (rnd.nextFloat() - 0.5f) * 140, z = (rnd.nextFloat() - 0.5f) * 140;
            if (!free(s, x, z, 1.4f) || nearVillage(x, z, 5)) continue;
            ground(s, bushes[rnd.nextInt(bushes.length)], x, z, rnd.nextFloat() * 360, 0.8f + rnd.nextFloat() * 0.6f, 0.1f);
            placed++;
        }
        String[] rocks = {"rock_boulder", "rock_round", "rock_flat", "rock_mossy", "rock_pile", "rock_mossy_flat"};
        placed = 0;
        for (int i = 0; i < 500 && placed < 30; i++) {
            float x = (rnd.nextFloat() - 0.5f) * 150, z = (rnd.nextFloat() - 0.5f) * 150;
            if (!free(s, x, z, 1.6f) || nearVillage(x, z, 6)) continue;
            ground(s, rocks[rnd.nextInt(rocks.length)], x, z, rnd.nextFloat() * 360, 0.5f + rnd.nextFloat() * 0.8f, -0.15f);
            placed++;
        }
        String[] flowers = {"flower_daisy", "flower_poppy", "flower_blue", "flower_tulip", "flower_lavender", "flower_yellow", "flower_pink", "flower_sun", "mushroom_red", "fern"};
        for (int c = 0; c < 26; c++) {
            float cx = (rnd.nextFloat() - 0.5f) * 120, cz = (rnd.nextFloat() - 0.5f) * 120;
            String id = flowers[rnd.nextInt(flowers.length)];
            for (int i = 0; i < 6; i++) {
                float x = cx + (rnd.nextFloat() - 0.5f) * 4.5f, z = cz + (rnd.nextFloat() - 0.5f) * 4.5f;
                if (!free(s, x, z, 0.5f)) continue;
                ground(s, id, x, z, rnd.nextFloat() * 360, 0.9f + rnd.nextFloat() * 0.5f, 0);
            }
        }
        String[] grass = {"grass_tuft", "grass_tall", "grass_patch", "grass_tuft", "grass_patch"};
        placed = 0;
        for (int i = 0; i < 3000 && placed < 260; i++) {
            float x = (rnd.nextFloat() - 0.5f) * 110 + 2, z = (rnd.nextFloat() - 0.5f) * 110 + 4;
            if (!free(s, x, z, 0.4f)) continue;
            if (fbm(x * 0.12f + 3, z * 0.12f + 60) < 0.45f) continue;
            ground(s, grass[rnd.nextInt(grass.length)], x, z, rnd.nextFloat() * 360, 0.9f + rnd.nextFloat() * 0.7f, 0);
            placed++;
        }
        // reeds along the banks
        placed = 0;
        for (int i = 0; i < 1500 && placed < 46; i++) {
            float x = (rnd.nextFloat() - 0.5f) * 60 + 4, z = rnd.nextFloat() * 70 - 14, h = s.terrain.height(x, z);
            if (h > WATER + 0.12f || h < WATER - 0.4f) continue;
            put(s, "reeds", x, h - 0.05f, z, rnd.nextFloat() * 360, 0.8f + rnd.nextFloat() * 0.6f);
            placed++;
        }
        return s;
    }

    static boolean nearVillage(float x, float z, float pad) {
        return x > 9 - pad && x < 41 + pad && z > -9 - pad && z < 20 + pad && !(x > 41 || z > 20 || x < 9 || z < -9);
    }

    /** A spot where something can stand: dry, not steep, not on the road, not in the channel. */
    static boolean free(Scene s, float x, float z, float r) {
        Terrain t = s.terrain;
        if (!t.inside(x, z)) return false;
        float h = t.height(x, z);
        if (h < WATER + 0.55f) return false;
        if (Math.abs(t.height(x + r, z) - h) > r * 0.5f || Math.abs(t.height(x, z + r) - h) > r * 0.5f || Math.abs(t.height(x - r, z) - h) > r * 0.5f || Math.abs(t.height(x, z - r) - h) > r * 0.5f) return false;
        int i = (MathUtils.clamp(Math.round(Terrain.gx(z)), 0, Terrain.N) * Terrain.W + MathUtils.clamp(Math.round(Terrain.gx(x)), 0, Terrain.N)) * 4;
        if (r > 0.45f && (t.splat[i] & 0xff) > 150 && h < 5) return false;
        if (h > 5 && Math.abs(x - FALL_X) < 6.5f) return false;
        for (SceneObject o : s.objects) {
            if (o.def.kind != ModelDef.MESH || (o.def.flags & ModelDef.M_SMALL) != 0 && r < 1) continue;
            float need = r + Math.min(3.2f, Math.max(o.def.bounds.getWidth(), o.def.bounds.getDepth()) * 0.42f * Math.max(o.scale.x, o.scale.z));
            float dx = o.pos.x - x, dz = o.pos.z - z;
            if (dx * dx + dz * dz < need * need) return false;
        }
        return true;
    }

    static void flatten(Scene s, float x, float z, float r) {
        float target = s.terrain.height(x, z);
        for (int i = 0; i < 6; i++) s.terrain.brush(Terrain.FLATTEN, x, z, r, 2f, target, 0);
    }

    static SceneObject put(Scene s, String id, float x, float y, float z, float rot, float scale) {
        SceneObject o = s.create(id, x, y, z);
        if (o == null) throw new IllegalStateException("missing asset " + id);
        o.rotY = rot;
        if (o.def.kind == ModelDef.MESH) o.scale.set(scale, scale, scale);
        return o;
    }

    static SceneObject ground(Scene s, String id, float x, float z, float rot, float scale, float sink) {
        return put(s, id, x, s.terrain.height(x, z) + sink * scale, z, rot, scale);
    }
}
