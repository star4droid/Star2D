package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.NumberUtils;
import com.badlogic.gdx.utils.ObjectMap;

import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Textures and the asset library (mesh models from models.bin, plus the built-in water and effect kinds). */
public final class Assets {
    public static final Array<ModelDef> defs = new Array<>();
    public static final ObjectMap<String, ModelDef> byId = new ObjectMap<>();
    public static Texture grass, dirt, sand, rock, snow, noise, foliage, white, sprites;
    /** Detail texture, triplanar scale and shine per material id. */
    public static final Texture[] detail = new Texture[12];
    public static final float[] detailScale = {0, 0.42f, 0.8f, 0.5f, 0.42f, 0.3f, 0.55f, 0, 0.5f, 0, 0, 0};
    /** How strongly the detail texture shows. */
    public static final float[] detailAmount = {0, 0.34f, 0.7f, 0.62f, 0.6f, 0.5f, 0.62f, 0, 0.6f, 0, 0, 0};
    public static final float[] spec = {0.02f, 0.04f, 0, 0.03f, 0.03f, 0, 0.06f, 0, 0, 0.6f, 0, 0.9f};
    public static final ObjectMap<String, String> fxShaders = new ObjectMap<>();
    public static final Array<String> fxNames = new Array<>();
    /** Per shader name: {title, group, description}. */
    public static final ObjectMap<String, String[]> fxInfo = new ObjectMap<>();

    private static byte[] modelBytes;
    private static ByteBuffer mb;
    private static int modelsLeft = -1;

    /** Every file of World Builder sits in assets/wb/, so it never mixes with the files of an app that includes it. */
    public static FileHandle file(String path) { return Gdx.files.internal("wb/" + path); }

    static Texture tex(String name, boolean repeat) {
        Texture t = new Texture(Assets.file("tex/" + name), true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        if (repeat) t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return t;
    }

    public static void loadTextures() {
        owner = Gdx.app;
        grass = tex("t_grass.png", true); dirt = tex("t_dirt.png", true); sand = tex("t_sand.png", true); rock = tex("t_rock.png", true); snow = tex("t_snow.png", true);
        noise = tex("noise.png", true); foliage = tex("foliage.png", false);
        detail[1] = tex("d_rock.png", true); detail[2] = tex("d_bark.png", true); detail[3] = tex("d_planks.png", true); detail[4] = tex("d_bricks.png", true);
        detail[5] = tex("d_plaster.png", true); detail[6] = tex("d_tiles.png", true); detail[8] = tex("d_thatch.png", true); detail[7] = foliage;
        Pixmap p = new Pixmap(2, 2, Pixmap.Format.RGBA8888); p.setColor(1, 1, 1, 1); p.fill(); white = new Texture(p); p.dispose();
        sprites = makeSprites();
    }

    /** Textures made in code are not restored by libGDX when Android drops the graphics context: make them again. */
    public static boolean generatedLost() { return white != null && !Gdx.gl.glIsTexture(white.getTextureObjectHandle()); }
    public static void remakeGenerated() {
        Pixmap p = new Pixmap(2, 2, Pixmap.Format.RGBA8888); p.setColor(1, 1, 1, 1); p.fill(); white = new Texture(p); p.dispose();
        sprites = makeSprites();
    }

    /** 4 cells in a row (64 px each): soft glow, hard spark, ring, vertical ray. */
    static Texture makeSprites() {
        int n = 64; Pixmap p = new Pixmap(n * 4, n, Pixmap.Format.RGBA8888); p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            float dx = (x + 0.5f) / n * 2 - 1, dy = (y + 0.5f) / n * 2 - 1, d = (float) Math.sqrt(dx * dx + dy * dy);
            float glow = (float) Math.pow(Math.max(0, 1 - d), 2.2);
            float spark = Math.max((float) Math.pow(Math.max(0, 1 - d), 6), Math.max(0, 1 - Math.abs(dx) * 14) * Math.max(0, 1 - Math.abs(dy)) * 0.9f + Math.max(0, 1 - Math.abs(dy) * 14) * Math.max(0, 1 - Math.abs(dx)) * 0.9f) * Math.max(0, 1 - d);
            float ring = (float) Math.exp(-Math.pow((d - 0.8f) / 0.07f, 2)) + 0.25f * (float) Math.exp(-Math.pow((d - 0.8f) / 0.18f, 2)) * (d < 0.98f ? 1 : 0);
            float ray = (float) Math.exp(-Math.pow(dx / 0.42f, 2)) * Math.max(0, 1 - Math.abs(dx)) * Math.min(1, (1 - (y + 0.5f) / n) * 5f) * (float) Math.pow((y + 0.5f) / n, 0.7f);
            float[] v = {glow, spark, ring, ray};
            for (int c = 0; c < 4; c++) p.drawPixel(c * n + x, y, ((255 << 24) | (255 << 16) | (255 << 8) | Math.min(255, (int) (v[c] * 255))) );
        }
        // drawPixel takes RGBA8888: build explicitly
        for (int y = 0; y < n; y++) for (int x = 0; x < n * 4; x++) { int a = p.getPixel(x, y) & 0xff; p.drawPixel(x, y, 0xffffff00 | a); }
        Texture t = new Texture(p, true); p.dispose();
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        return t;
    }

    /** Starts reading models.bin; then call {@link #loadSomeModels} until it returns 1. */
    public static void beginModels() {
        modelBytes = Assets.file("models/models.bin").readBytes();
        mb = ByteBuffer.wrap(modelBytes).order(ByteOrder.LITTLE_ENDIAN);
        mb.position(4);
        modelsDone = false;
        modelsLeft = mb.getShort() & 0xffff;
        total = modelsLeft;
    }
    private static int total;

    private static String str() { int n = mb.getShort() & 0xffff; byte[] b = new byte[n]; mb.get(b); return new String(b, java.nio.charset.StandardCharsets.UTF_8); }

    /** Parses a few models; returns the progress 0..1. */
    public static float loadSomeModels(int count) {
        for (int k = 0; k < count && modelsLeft > 0; k++, modelsLeft--) {
            ModelDef d = new ModelDef();
            d.id = str(); d.name = str(); d.cat = str(); d.sub = str();
            d.flags = mb.get() & 0xff;
            int nt = mb.get() & 0xff;
            d.tintNames = new String[nt]; d.tintDefaults = new float[nt][];
            for (int i = 0; i < nt; i++) { d.tintNames[i] = str(); d.tintDefaults[i] = new float[]{(mb.get() & 0xff) / 255f, (mb.get() & 0xff) / 255f, (mb.get() & 0xff) / 255f}; }
            for (int a = 0; a < 3; a++) { d.zoneA[a] = mb.getFloat(); d.zoneB[a] = mb.getFloat(); }
            int np = mb.getShort() & 0xffff;
            d.parts = new ModelDef.Part[np];
            for (int i = 0; i < np; i++) {
                ModelDef.Part p = new ModelDef.Part();
                p.mat = mb.get() & 0xff; p.flags = mb.get() & 0xff; p.tint = mb.get() & 0xff; p.tint2 = mb.get() & 0xff; mb.get(); mb.get();
                for (int a = 0; a < 3; a++) p.mode[a] = mb.get() & 0xff;
                for (int a = 0; a < 3; a++) { p.repA[a] = mb.getFloat(); p.repB[a] = mb.getFloat(); p.repN[a] = mb.get() & 0xff; }
                p.slope = mb.getFloat(); p.sref = mb.getFloat();
                int nv = mb.getInt(), ni = mb.getInt();
                p.v = new float[nv * ModelDef.STRIDE];
                for (int v = 0; v < nv; v++) {
                    int o = v * ModelDef.STRIDE;
                    for (int j = 0; j < 6; j++) p.v[o + j] = mb.getFloat();
                    int r = mb.get() & 0xff, g = mb.get() & 0xff, b = mb.get() & 0xff, a = mb.get() & 0xff;
                    p.v[o + 6] = NumberUtils.intToFloatColor((a << 24) | (b << 16) | (g << 8) | r);
                    p.v[o + 7] = mb.getFloat(); p.v[o + 8] = mb.getFloat();
                }
                p.idx = new short[ni];
                for (int j = 0; j < ni; j++) p.idx[j] = mb.getShort();
                d.parts[i] = p;
            }
            d.finish();
            add(d);
        }
        if (modelsLeft == 0) { modelBytes = null; mb = null; modelsLeft = -1; addBuiltIns(); modelsDone = true; return 1f; }
        return 1f - modelsLeft / (float) Math.max(1, total);
    }

    static void add(ModelDef d) { defs.add(d); byId.put(d.id, d); }

    static ModelDef virtual(String id, String name, String cat, String sub, int kind, float scale, String[] tints, float[][] defaults, String icon) {
        ModelDef d = new ModelDef();
        d.id = id; d.name = name; d.cat = cat; d.sub = sub; d.kind = kind; d.defScale = scale; d.tintNames = tints; d.tintDefaults = defaults; d.icon = icon;
        d.finish();
        add(d);
        return d;
    }

    static float[] c(int rgb) { return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f}; }

    /** Water bodies and effects are generated in code (see Water / Effects). */
    static void addBuiltIns() {
        String[] wt = {"Shallow", "Deep"};
        float[][] wd = {c(0x4fd8d2), c(0x1b6fb8)};
        virtual("water_pond", "Pond", "water", "Still water", ModelDef.WATER, 4f, wt, wd, "water");
        virtual("water_lake", "Lake", "water", "Still water", ModelDef.WATER, 9f, wt, wd, "water");
        virtual("water_square", "Pool", "water", "Still water", ModelDef.WATER, 4f, wt, wd, "water");
        virtual("water_river", "River", "water", "Flowing water", ModelDef.WATER, 1f, wt, wd, "water");
        virtual("water_stream", "Stream", "water", "Flowing water", ModelDef.WATER, 1f, wt, wd, "water");
        virtual("water_fall", "Waterfall", "water", "Waterfalls", ModelDef.FALL, 1f, wt, new float[][]{c(0x8fe4ec), c(0x2a86c8)}, "water");
        virtual("water_fall_wide", "Wide Waterfall", "water", "Waterfalls", ModelDef.FALL, 1f, wt, new float[][]{c(0x8fe4ec), c(0x2a86c8)}, "water");
        virtual("water_fall_thin", "Thin Cascade", "water", "Waterfalls", ModelDef.FALL, 1f, wt, new float[][]{c(0x8fe4ec), c(0x2a86c8)}, "water");
        String[] ft = {"Color", "Second color"};
        virtual("fx_pillar", "Light Pillar", "effects", "Light", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xfff0a0), c(0xffc040)}, "fx");
        virtual("fx_godrays", "Sun Rays", "effects", "Light", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xfff6d0), c(0xffe090)}, "fx");
        virtual("fx_orb", "Glowing Orb", "effects", "Light", ModelDef.EFFECT, 1f, ft, new float[][]{c(0x7fe8ff), c(0xffffff)}, "fx");
        virtual("fx_area_lights", "Area Lights", "effects", "Light", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xffe48a), c(0xff9a3c)}, "fx");
        virtual("fx_fireflies", "Fireflies", "effects", "Ambient", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xd8ff6a), c(0x8cff3a)}, "fx");
        virtual("fx_sparkles", "Magic Sparkles", "effects", "Ambient", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xffffff), c(0xa8d8ff)}, "fx");
        virtual("fx_petals", "Falling Petals", "effects", "Ambient", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xffb8d8), c(0xffe0f0)}, "fx");
        virtual("fx_mist", "Ground Mist", "effects", "Ambient", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xe8f2ff), c(0xbcd4f0)}, "fx");
        virtual("fx_cubes", "Floating Cubes", "effects", "Magic", ModelDef.EFFECT, 1f, ft, new float[][]{c(0x6ad8ff), c(0xb07aff)}, "fx");
        virtual("fx_circle", "Magic Circle", "effects", "Magic", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xb07aff), c(0x6ad8ff)}, "fx");
        virtual("fx_portal", "Portal", "effects", "Magic", ModelDef.EFFECT, 1f, ft, new float[][]{c(0x8a5cff), c(0x40e8ff)}, "fx");
        virtual("fx_aura", "Rising Aura", "effects", "Magic", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xffd24a), c(0xfff6c0)}, "fx");
        virtual("fx_fire", "Fire", "effects", "Fire", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xffa020), c(0xff4a10)}, "fx");
        virtual("fx_smoke", "Smoke", "effects", "Fire", ModelDef.EFFECT, 1f, ft, new float[][]{c(0x8a8a8a), c(0x505050)}, "fx");
        virtual("fx_embers", "Embers", "effects", "Fire", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xffb040), c(0xff5a10)}, "fx");
        virtual("fx_fountain", "Water Spray", "effects", "Fire", ModelDef.EFFECT, 1f, ft, new float[][]{c(0xdff6ff), c(0x8fd8ff)}, "fx");
    }

    /** Element shaders: the same snippet format as the Star2D engine (effect() and optional displace()). */
    public static void loadFxShaders() {
        FileHandle list = Assets.file("fx/list.txt");
        if (!list.exists()) return;
        for (String line : list.readString("UTF-8").split("\n")) {
            String[] c = line.trim().split("\\|");
            if (c.length < 4) continue;
            String n = c[0];
            FileHandle f = Assets.file("fx/" + n + ".glsl");
            if (f.exists()) { fxShaders.put(n, f.readString("UTF-8")); fxNames.add(n); fxInfo.put(n, new String[]{c[1], c[2], c[3]}); }
        }
    }

    /** The app whose graphics context holds the loaded textures and meshes (a context cannot use another one's). */
    private static Object owner;

    /** True when everything is loaded for the current app. */
    public static boolean ready() { return owner == Gdx.app && white != null && modelsDone; }
    private static boolean modelsDone;

    /**
     * Loads everything at once for the current app (used when a world is shown inside a game or the Star2D editor).
     * What another app (another graphics context) loaded is dropped without freeing it: it is not usable here.
     */
    public static void ensureLoaded() {
        if (ready()) return;
        if (owner != Gdx.app) forget();
        if (white == null) { loadTextures(); loadFxShaders(); }
        if (!modelsDone) {
            if (mb == null) beginModels();
            while (loadSomeModels(64) < 1) { }
        }
    }

    /** Drops every reference without freeing (the objects belong to another graphics context). */
    public static void forget() {
        defs.clear(); byId.clear();
        grass = dirt = sand = rock = snow = noise = foliage = white = sprites = null;
        java.util.Arrays.fill(detail, null);
        fxShaders.clear(); fxNames.clear(); fxInfo.clear();
        modelBytes = null; mb = null; modelsLeft = -1; owner = null; modelsDone = false;
        Shaders.forget();
    }

    /** Frees everything, so World Builder can be opened again in the same process (inside Star2D). */
    public static void dispose() {
        for (ModelDef d : defs) d.dispose();
        defs.clear(); byId.clear();
        java.util.HashSet<Texture> all = new java.util.HashSet<>(java.util.Arrays.asList(grass, dirt, sand, rock, snow, noise, foliage, white, sprites));
        all.addAll(java.util.Arrays.asList(detail));
        for (Texture t : all) if (t != null) t.dispose();
        grass = dirt = sand = rock = snow = noise = foliage = white = sprites = null;
        java.util.Arrays.fill(detail, null);
        fxShaders.clear(); fxNames.clear(); fxInfo.clear();
        modelBytes = null; mb = null; modelsLeft = -1; owner = null; modelsDone = false;
    }
}
