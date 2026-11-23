package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;

/** Draws a world: shadow map, sky, terrain, elements, water, effects; optionally at a lower resolution. */
public final class Renderer {
    public final PerspectiveCamera cam = new PerspectiveCamera(50, 1, 1);
    private final OrthographicCamera light = new OrthographicCamera();
    private FrameBuffer shadowFbo, sceneFbo, thumbFbo;
    private final Mesh quad;
    public final FxBatch add = new FxBatch(), blend = new FxBatch();
    public float time;
    /** What the shadow map is centered on (the camera target) and how far the camera is from it. */
    public final Vector3 focus = new Vector3();
    public float focusDist = 40;
    /** Terrain brush ring: x, z, radius, visible. */
    public final float[] brush = new float[4], brushColor = {1, 1, 1};
    public final IntSet selected = new IntSet();
    public int drawnObjects, drawnTris;

    private final Matrix4 ident = new Matrix4();
    private final float[] ta = new float[3], tb = new float[3];
    private final ObjectSet<ShaderProgram> primed = new ObjectSet<>();
    private final IntMap<ShaderProgram> objCache = new IntMap<>();
    private ShaderProgram current;
    private Scene scene;
    private Env env;
    private boolean shadowOn;
    private static final Env THUMB_ENV = new Env();
    private final Vector3 t1 = new Vector3(), t2 = new Vector3();

    static final String DEPTH2_V = "attribute vec3 a_position;\nattribute vec4 a_color;\nattribute vec2 a_texCoord0;\nuniform mat4 u_world;\nuniform mat4 u_viewProj;\nuniform float u_time;\nuniform vec4 u_wind;\nvarying float v_d;\nvarying vec2 v_uv;\n"
            + "void main(){\n vec4 wp = u_world * vec4(a_position, 1.0);\n#ifdef WIND\n float w = a_color.a * u_wind.z;\n float ph = dot(wp.xz, vec2(0.31, 0.23)) + u_time * 1.7;\n float sw = sin(ph) + 0.45 * sin(ph * 2.3 + 1.7);\n wp.xz += u_wind.xy * sw * w * 0.16;\n#endif\n"
            + " vec4 p = u_viewProj * wp;\n v_d = p.z * 0.5 + 0.5;\n v_uv = a_texCoord0;\n gl_Position = p;\n}\n";
    static final String DEPTH2_F = "uniform sampler2D u_tex;\nvarying float v_d;\nvarying vec2 v_uv;\nvoid main(){\n#ifdef ALPHA\n if (texture2D(u_tex, v_uv).a < 0.45) discard;\n#endif\n"
            + " float d = clamp(v_d, 0.0, 0.9999);\n vec4 e = fract(d * vec4(1.0, 255.0, 65025.0, 16581375.0));\n e -= e.yzww * vec4(1.0/255.0, 1.0/255.0, 1.0/255.0, 0.0);\n gl_FragColor = e;\n}\n";
    static final String FX_HEAD = "uniform vec3 u_lightDir;\nuniform vec3 u_lightColor;\nuniform vec3 u_ambient;\nuniform vec3 u_cameraPos;\nuniform vec2 u_resolution;\n"
            + "bool hasTexture(){ return false; }\nvec3 lambert(vec3 n){ return u_ambient + u_lightColor * max(dot(normalize(n), -u_lightDir), 0.0); }\n";

    public Renderer() {
        quad = new Mesh(true, 4, 6, VertexAttribute.Position());
        quad.setVertices(new float[]{-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0});
        quad.setIndices(new short[]{0, 1, 2, 0, 2, 3});
        THUMB_ENV.time = 11f; THUMB_ENV.sunYaw = 20; THUMB_ENV.update();
    }

    private static boolean depth24 = true;

    /** Color + depth target; 24-bit depth where the GPU has it (16 bits flicker in the distance). */
    private static FrameBuffer fbo(int w, int h) {
        if (depth24) {
            try {
                com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder b = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder(w, h);
                b.addBasicColorTextureAttachment(Pixmap.Format.RGBA8888);
                b.addDepthRenderBuffer(0x81A6);      // GL_DEPTH_COMPONENT24
                return b.build();
            } catch (Exception e) { depth24 = false; Gdx.app.log("WB", "24-bit depth buffers are not available, using 16"); }
        }
        return new FrameBuffer(Pixmap.Format.RGBA8888, w, h, true);
    }

    // ------------------------------------------------------------------ programs
    private void use(ShaderProgram sp) {
        if (sp != current) { sp.bind(); current = sp; }
        if (primed.add(sp)) {
            sp.setUniformf("u_time", time);
            sp.setUniformf("u_sunDir", env.sunDir);
            sp.setUniformf("u_sunColor", env.sunColor[0], env.sunColor[1], env.sunColor[2]);
            sp.setUniformf("u_ambSky", env.ambSky[0], env.ambSky[1], env.ambSky[2]);
            sp.setUniformf("u_ambGround", env.ambGround[0], env.ambGround[1], env.ambGround[2]);
            sp.setUniformf("u_fogColor", env.fogColor[0], env.fogColor[1], env.fogColor[2]);
            sp.setUniformf("u_fog", fogStart, fogInv, fogCap, 0);
            sp.setUniformf("u_camPos", cam.position);
            sp.setUniformf("u_wind", 0.8f, 0.6f, Settings.wind ? env.wind : 0, 0);
            sp.setUniformMatrix("u_viewProj", viewProj);
            if (shadowOn) {
                sp.setUniformMatrix("u_shadowMat", light.combined);
                sp.setUniformi("u_shadowMap", 3);
                sp.setUniformf("u_shadowInfo", 1f / shadowFbo.getWidth(), 0.0022f, 0, 0);
                sp.setUniformf("u_shadowBias", shadowBias);
            }
        }
    }
    private float fogStart, fogInv, fogCap = 1, shadowBias;
    private int frameNo;
    private Matrix4 viewProj;

    private static String objDefines(int bits) {
        return ((bits & 1) != 0 ? "#define ALPHA\n" : "") + ((bits & 2) != 0 ? "#define DETAIL\n" : "") + ((bits & 4) != 0 ? "#define WIND\n" : "")
                + ((bits & 8) != 0 ? "#define SHADOW\n" : "") + ((bits & 16) != 0 ? "#define SHADOW_PCF\n" : "");
    }

    private ShaderProgram objProgram(int bits, int fx) {
        int key = bits | (fx + 1) << 8;
        if (objCache.containsKey(key)) return objCache.get(key);
        ShaderProgram sp;
        if (fx < 0) sp = Shaders.get("obj" + bits, objDefines(bits), Shaders.OBJ_V, Shaders.OBJ_F);
        else {
            String name = Assets.fxNames.get(fx), snip = Assets.fxShaders.get(name);
            boolean disp = snip.contains("displace(");
            String vs = Shaders.OBJ_V.replace("//DISPLACE", disp ? extractFunction(snip, "displace") : "");
            String fs = Shaders.OBJ_F.replace("//FX", FX_HEAD + (disp ? removeFunction(snip, "displace") : snip));
            sp = Shaders.get("obj" + bits + ":" + name, objDefines(bits) + "#define FX\n" + (disp ? "#define FXV\n" : ""), vs, fs);
        }
        objCache.put(key, sp);
        return sp;
    }

    /** Text of "type name(...) { ... }" with the uniforms it may use (same rule as the Star2D engine). */
    static String extractFunction(String src, String name) {
        int i = src.indexOf(name + "(");
        if (i < 0) return "";
        int start = src.lastIndexOf('\n', i) + 1, b = src.indexOf('{', i), depth = 0;
        for (int k = b; k >= 0 && k < src.length(); k++) {
            char ch = src.charAt(k);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) {
                StringBuilder u = new StringBuilder();
                for (String line : src.split("\n")) if (line.trim().startsWith("uniform")) u.append(line.trim()).append('\n');
                return u + src.substring(start, k + 1);
            }
        }
        return "";
    }

    static String removeFunction(String src, String name) {
        int i = src.indexOf(name + "(");
        if (i < 0) return src;
        int start = src.lastIndexOf('\n', i) + 1, b = src.indexOf('{', i), depth = 0;
        for (int k = b; k >= 0 && k < src.length(); k++) {
            char ch = src.charAt(k);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return src.substring(0, start) + src.substring(k + 1);
        }
        return src;
    }

    /** True when the element shader of this name compiles (the picker marks broken ones). */
    public boolean fxWorks(String name) {
        int fx = Assets.fxNames.indexOf(name, false);
        return fx >= 0 && objProgram(0, fx) != null;
    }

    // ------------------------------------------------------------------ elements
    private void drawMeshObject(SceneObject o, boolean fxPass, boolean thumb) {
        int fx = o.fx.isEmpty() ? -1 : Assets.fxNames.indexOf(o.fx, false);
        if ((fx >= 0) != fxPass) return;
        boolean sel = selected.contains(o.uid) && !thumb;
        GL20 gl = Gdx.gl;
        for (ModelDef.Group g : o.built.groups) {
            boolean alpha = (g.flags & ModelDef.F_ALPHA) != 0;
            boolean det = !alpha && Settings.detail && Assets.detail[g.mat] != null && Assets.detailScale[g.mat] > 0;
            int bits = (alpha ? 1 : 0) | (det ? 2 : 0) | ((g.flags & ModelDef.F_WIND) != 0 && o.wind && Settings.wind && !thumb ? 4 : 0) | (shadowOn ? 8 : 0) | (shadowOn && Settings.shadows == 2 ? 16 : 0);
            ShaderProgram sp = objProgram(bits, fx);
            if (sp == null && fx >= 0) sp = objProgram(bits, -1);
            if (sp == null) continue;
            use(sp);
            if ((g.flags & ModelDef.F_DOUBLE) != 0) gl.glDisable(GL20.GL_CULL_FACE); else gl.glEnable(GL20.GL_CULL_FACE);
            (alpha ? Assets.foliage : det ? Assets.detail[g.mat] : Assets.white).bind(0);
            sp.setUniformi("u_tex", 0);
            sp.setUniformMatrix("u_world", o.world);
            o.tintFactor(g.tint, ta); o.tintFactor(g.tint2, tb);
            sp.setUniformf("u_tintA", ta[0], ta[1], ta[2]);
            sp.setUniformf("u_tintB", tb[0], tb[1], tb[2]);
            sp.setUniformf("u_mat", Assets.detailScale[g.mat], (g.flags & ModelDef.F_EMISSIVE) != 0 ? 1 : 0, Assets.spec[g.mat], 1);
            sp.setUniformf("u_det", Assets.detailAmount[g.mat]);
            sp.setUniformf("u_sel", 0.55f, 0.82f, 1f, sel ? 0.2f + 0.1f * MathUtils.sin(time * 5f) : 0);
            if (fx >= 0) {
                sp.setUniformf("u_lightDir", -env.sunDir.x, -env.sunDir.y, -env.sunDir.z);
                sp.setUniformf("u_lightColor", env.sunColor[0], env.sunColor[1], env.sunColor[2]);
                sp.setUniformf("u_ambient", env.ambSky[0], env.ambSky[1], env.ambSky[2]);
                sp.setUniformf("u_cameraPos", cam.position);
                sp.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                sp.setUniformf("u_speed", o.speed);
                sp.setUniformf("u_amount", o.fxAmount);
                sp.setUniformf("u_tint", o.fxTint[0], o.fxTint[1], o.fxTint[2], o.fxTintOn ? 1 : 0);
            }
            g.mesh.render(sp, GL20.GL_TRIANGLES);
            drawnTris += g.mesh.getNumIndices() / 3;
        }
    }

    private boolean visible(SceneObject o, Frustum fr, float maxDist) {
        if (o.hidden) return false;
        float d = o.center.dst(cam.position);
        float lim = (o.def.flags & ModelDef.M_SMALL) != 0 ? maxDist * 0.6f : maxDist;
        return d - o.radius < lim && fr.sphereInFrustum(o.center, o.radius);
    }

    // ------------------------------------------------------------------ frame
    public void render(Scene scene, int width, int height, float delta) {
        this.scene = scene; env = scene.env;
        time += delta;
        env.update();
        GL20 gl = Gdx.gl;
        scene.terrain.rebuild(6);
        if (env.water) scene.water.update(scene.terrain, env.waterLevel, null, scene.terrain.version);
        for (SceneObject o : scene.objects) o.update(scene);

        float rd = Settings.renderDistance;
        cam.near = 0.3f; cam.far = rd * 1.6f + 60; cam.viewportWidth = width; cam.viewportHeight = height; cam.update();
        fogStart = rd * (0.28f + (1 - env.fog) * 0.5f); fogInv = 1f / Math.max(1, rd - fogStart); fogCap = 0.45f + 0.55f * env.fog;

        shadowOn = Settings.shadows > 0;
        // Normal shadows are redrawn every second frame (half the cost); Soft every frame
        if (shadowOn && (Settings.shadows == 2 || shadowFbo == null || (frameNo++ & 1) == 0)) shadowPass();
        current = null; primed.clear();
        viewProj = cam.combined;

        float rs = MathUtils.clamp(Settings.resScale, 0.4f, 1f);
        boolean scaled = rs < 0.99f;
        if (scaled) {
            int w = Math.max(64, Math.round(width * rs)), h = Math.max(64, Math.round(height * rs));
            if (sceneFbo == null || sceneFbo.getWidth() != w || sceneFbo.getHeight() != h) {
                if (sceneFbo != null) sceneFbo.dispose();
                sceneFbo = fbo(w, h);
                sceneFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            }
            sceneFbo.begin();
        } else gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());

        gl.glClearColor(env.fogColor[0], env.fogColor[1], env.fogColor[2], 1);
        gl.glDepthMask(true);
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL20.GL_BLEND);
        drawSky();
        if (shadowOn) { gl.glActiveTexture(GL20.GL_TEXTURE3); shadowFbo.getColorBufferTexture().bind(3); gl.glActiveTexture(GL20.GL_TEXTURE0); }
        gl.glEnable(GL20.GL_DEPTH_TEST); gl.glDepthFunc(GL20.GL_LEQUAL); gl.glDepthMask(true);
        gl.glEnable(GL20.GL_CULL_FACE); gl.glCullFace(GL20.GL_BACK);
        drawTerrain();

        drawnObjects = 0; drawnTris = 0;
        Frustum fr = cam.frustum;
        for (SceneObject o : scene.objects) {
            if (o.def.kind != ModelDef.MESH || !visible(o, fr, rd)) continue;
            drawMeshObject(o, false, false); drawnObjects++;
        }
        // elements with a shader: blended, after the solid ones
        gl.glEnable(GL20.GL_BLEND); gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        for (SceneObject o : scene.objects) {
            if (o.def.kind != ModelDef.MESH || o.fx.isEmpty() || !visible(o, fr, rd)) continue;
            drawMeshObject(o, true, false);
        }
        gl.glDisable(GL20.GL_CULL_FACE);
        gl.glDepthMask(false);
        drawWater(fr, rd);
        drawEffects(fr, rd);
        gl.glDepthMask(true);
        gl.glDisable(GL20.GL_BLEND);
        gl.glDisable(GL20.GL_DEPTH_TEST);

        if (scaled) {
            sceneFbo.end();
            gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            ShaderProgram sp = Shaders.get("blit", "", Shaders.BLIT_V, Shaders.BLIT_F);
            sp.bind();
            sceneFbo.getColorBufferTexture().bind(0);
            sp.setUniformi("u_tex", 0);
            quad.render(sp, GL20.GL_TRIANGLES);
        }
        gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    private void drawSky() {
        GL20 gl = Gdx.gl;
        gl.glDisable(GL20.GL_DEPTH_TEST); gl.glDepthMask(false); gl.glDisable(GL20.GL_CULL_FACE);
        ShaderProgram sp = Shaders.get("sky", "", Shaders.SKY_V, Shaders.SKY_F);
        if (sp == null) return;
        sp.bind(); current = sp;
        float th = (float) Math.tan(Math.toRadians(cam.fieldOfView * 0.5)), aspect = cam.viewportWidth / cam.viewportHeight;
        t1.set(cam.direction).crs(cam.up).nor();
        t2.set(t1).crs(cam.direction).nor();
        sp.setUniformf("u_right", t1.x * th * aspect, t1.y * th * aspect, t1.z * th * aspect);
        sp.setUniformf("u_up", t2.x * th, t2.y * th, t2.z * th);
        sp.setUniformf("u_fwd", cam.direction);
        sp.setUniformf("u_zenith", env.zenith[0], env.zenith[1], env.zenith[2]);
        sp.setUniformf("u_horizon", env.horizon[0], env.horizon[1], env.horizon[2]);
        sp.setUniformf("u_sunDir", env.sunDir);
        sp.setUniformf("u_sunColor", env.sunColor[0], env.sunColor[1], env.sunColor[2]);
        sp.setUniformf("u_time", time * (0.4f + env.wind));
        sp.setUniformf("u_cloud", env.clouds, 0, 0, 0);
        Assets.noise.bind(0); sp.setUniformi("u_noise", 0);
        quad.render(sp, GL20.GL_TRIANGLES);
    }

    private void drawTerrain() {
        String def = (Settings.detail ? "#define DETAIL\n" : "") + (shadowOn ? "#define SHADOW\n" : "") + (shadowOn && Settings.shadows == 2 ? "#define SHADOW_PCF\n" : "");
        ShaderProgram sp = Shaders.get("ter" + (Settings.detail ? 1 : 0) + (shadowOn ? Settings.shadows : 0), def, Shaders.TER_V, Shaders.TER_F);
        if (sp == null) return;
        use(sp);
        Assets.grass.bind(0); Assets.dirt.bind(1); Assets.sand.bind(2); Assets.rock.bind(4); Assets.snow.bind(5); Assets.noise.bind(6);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        sp.setUniformi("u_grass", 0); sp.setUniformi("u_dirt", 1); sp.setUniformi("u_sand", 2); sp.setUniformi("u_rock", 4); sp.setUniformi("u_snow", 5); sp.setUniformi("u_noise", 6);
        sp.setUniformf("u_brush", brush[0], brush[1], brush[2], brush[3]);
        sp.setUniformf("u_brushColor", brushColor[0], brushColor[1], brushColor[2], 1);
        sp.setUniformf("u_grid", Settings.grid ? 1 : 0, Math.max(0.25f, Settings.snapStep) * 2, 0, focusDist * 0.0012f);
        sp.setUniformf("u_waterInfo", env.water ? env.waterLevel : -1000, 0.5f, 0, 0);
        scene.terrain.render(sp, cam.frustum);
    }

    private void waterUniforms(ShaderProgram sp, float[] shallow, float[] deep, float fx, float fz, float speed) {
        Assets.noise.bind(0); sp.setUniformi("u_noise", 0);
        sp.setUniformf("u_shallow", shallow[0], shallow[1], shallow[2]);
        sp.setUniformf("u_deep", deep[0], deep[1], deep[2]);
        sp.setUniformf("u_flow", fx, fz, speed, 0);
        sp.setUniformf("u_skyColor", env.horizon[0] * 0.6f + env.zenith[0] * 0.4f, env.horizon[1] * 0.6f + env.zenith[1] * 0.4f, env.horizon[2] * 0.6f + env.zenith[2] * 0.4f);
    }

    private void drawWater(Frustum fr, float rd) {
        ShaderProgram sp = Shaders.get("water" + (Settings.waterFull ? 1 : 0), Settings.waterFull ? "#define FULL\n" : "", Shaders.WAT_V, Shaders.WAT_F);
        if (sp != null) {
            use(sp);
            if (env.water) {
                sp.setUniformMatrix("u_world", ident);
                waterUniforms(sp, env.shallow, env.deep, 0.6f, 0.3f, 0.5f + env.wind * 0.6f);
                scene.water.render(sp, scene.terrain, fr);
            }
            for (SceneObject o : scene.objects) {
                if (o.def.kind != ModelDef.WATER || o.ownMesh == null || !visible(o, fr, rd)) continue;
                boolean flow = o.def.id.equals("water_river") || o.def.id.equals("water_stream");
                sp.setUniformMatrix("u_world", o.world);
                waterUniforms(sp, o.tintA, o.tintB, flow ? -MathUtils.sinDeg(o.rotY) * 1.5f : 0.3f, flow ? -MathUtils.cosDeg(o.rotY) * 1.5f : 0.2f, o.speed);
                o.ownMesh.render(sp, GL20.GL_TRIANGLES);
            }
        }
        ShaderProgram fp = Shaders.get("fall", "", Shaders.WAT_V, Shaders.FALL_F);
        if (fp == null) return;
        boolean first = true;
        for (SceneObject o : scene.objects) {
            if (o.def.kind != ModelDef.FALL || o.ownMesh == null || !visible(o, fr, rd)) continue;
            if (first) { use(fp); first = false; }
            fp.setUniformMatrix("u_world", o.world);
            waterUniforms(fp, o.tintA, o.tintB, 0, 1, o.speed);
            o.ownMesh.render(fp, GL20.GL_TRIANGLES);
        }
    }

    private void drawEffects(Frustum fr, float rd) {
        add.begin(cam); blend.begin(cam);
        for (SceneObject o : scene.objects) {
            if (o.hidden) continue;
            if (o.def.kind == ModelDef.EFFECT) { if (visible(o, fr, rd)) Effects.emit(o, add, blend, time); }
            else if (o.def.kind == ModelDef.FALL && visible(o, fr, rd)) Effects.fallMist(o, blend, time);
        }
        flushEffects();
    }

    private void flushEffects() {
        ShaderProgram sp = Shaders.get("spr", "", Shaders.SPR_V, Shaders.SPR_F);
        if (sp == null) return;
        GL20 gl = Gdx.gl;
        sp.bind(); current = sp;
        sp.setUniformMatrix("u_viewProj", viewProj);
        Assets.sprites.bind(0); sp.setUniformi("u_tex", 0);
        gl.glEnable(GL20.GL_BLEND);
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        blend.flush(sp);
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        add.flush(sp);
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ------------------------------------------------------------------ shadow map
    private void shadowPass() {
        int size = Settings.shadows == 2 ? 2048 : 1024;
        if (shadowFbo == null || shadowFbo.getWidth() != size) {
            if (shadowFbo != null) shadowFbo.dispose();
            shadowFbo = fbo(size, size);
            shadowFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        float r = MathUtils.clamp(focusDist * 0.95f, 24f, Math.min(120f, Settings.renderDistance * 0.7f)), d = r + 70;
        light.viewportWidth = light.viewportHeight = r * 2; light.near = 1; light.far = d * 2;
        // move in whole texels so the shadow edges do not swim when the camera moves
        float texel = r * 2 / size;
        light.direction.set(env.sunDir).scl(-1);
        light.up.set(Math.abs(env.sunDir.y) > 0.98f ? Vector3.Z : Vector3.Y);
        t1.set(light.direction).crs(light.up).nor(); t2.set(t1).crs(light.direction).nor();
        float fxp = Math.round(focus.dot(t1) / texel) * texel, fyp = Math.round(focus.dot(t2) / texel) * texel, fzp = focus.dot(light.direction);
        light.position.set(t1).scl(fxp).mulAdd(t2, fyp).mulAdd(light.direction, fzp - d);
        light.up.set(t2);
        light.update();
        shadowBias = texel * 1.6f;

        GL20 gl = Gdx.gl;
        shadowFbo.begin();
        gl.glClearColor(1, 1, 1, 1);
        gl.glDepthMask(true);
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL20.GL_BLEND);
        gl.glEnable(GL20.GL_DEPTH_TEST); gl.glDepthFunc(GL20.GL_LEQUAL);
        gl.glEnable(GL20.GL_CULL_FACE); gl.glCullFace(GL20.GL_BACK);
        ShaderProgram dp = Shaders.get("depth", "", Shaders.DEPTH_V, Shaders.DEPTH_F);
        if (dp != null) {
            dp.bind();
            dp.setUniformMatrix("u_viewProj", light.combined);
            dp.setUniformMatrix("u_world", ident);
            scene.terrain.render(dp, light.frustum);
        }
        ShaderProgram[] ps = new ShaderProgram[4];
        ShaderProgram cur = null;
        for (SceneObject o : scene.objects) {
            if (o.def.kind != ModelDef.MESH || o.hidden || !o.shadow || !light.frustum.sphereInFrustum(o.center, o.radius)) continue;
            if ((o.def.flags & ModelDef.M_SMALL) != 0 && (Settings.shadows < 2 || o.center.dst(focus) > r * 0.6f)) continue;
            for (ModelDef.Group g : o.built.groups) {
                if ((g.flags & ModelDef.F_NOSHADOW) != 0) continue;
                boolean alpha = (g.flags & ModelDef.F_ALPHA) != 0, wind = (g.flags & ModelDef.F_WIND) != 0 && o.wind && Settings.wind;
                int k = (alpha ? 1 : 0) | (wind ? 2 : 0);
                if (ps[k] == null) ps[k] = Shaders.get("depth2_" + k, (alpha ? "#define ALPHA\n" : "") + (wind ? "#define WIND\n" : ""), DEPTH2_V, DEPTH2_F);
                ShaderProgram sp = ps[k];
                if (sp == null) continue;
                if (sp != cur) {
                    sp.bind(); cur = sp;
                    sp.setUniformMatrix("u_viewProj", light.combined);
                    sp.setUniformf("u_time", time);
                    sp.setUniformf("u_wind", 0.8f, 0.6f, env.wind, 0);
                    Assets.foliage.bind(0); sp.setUniformi("u_tex", 0);
                }
                if ((g.flags & ModelDef.F_DOUBLE) != 0) gl.glDisable(GL20.GL_CULL_FACE); else gl.glEnable(GL20.GL_CULL_FACE);
                sp.setUniformMatrix("u_world", o.world);
                g.mesh.render(sp, GL20.GL_TRIANGLES);
            }
        }
        shadowFbo.end();
    }

    // ------------------------------------------------------------------ library thumbnails
    public static final int THUMB = 128, THUMB_COLS = 16;
    private final ObjectMap<String, TextureRegion> thumbs = new ObjectMap<>();
    private int thumbNext;
    private Scene thumbScene;

    public TextureRegion thumb(String id) { return thumbs.get(id); }
    public void invalidateThumbs() { thumbNext = 0; }
    public boolean thumbsDone() { return thumbNext >= Assets.defs.size; }

    /** Renders the next few library pictures; returns the progress 0..1. */
    public float thumbStep(int count) {
        int total = Assets.defs.size, rows = (total + THUMB_COLS - 1) / THUMB_COLS;
        int th = MathUtils.nextPowerOfTwo(rows * THUMB);
        if (thumbFbo == null || thumbFbo.getHeight() != th) {
            if (thumbFbo != null) thumbFbo.dispose();
            thumbFbo = new FrameBuffer(Pixmap.Format.RGBA8888, THUMB_COLS * THUMB, th, true);
            thumbFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            thumbNext = 0;
        }
        if (thumbScene == null) { thumbScene = new Scene(); }
        if (thumbNext >= total) return 1;
        GL20 gl = Gdx.gl;
        Env saveEnv = env; Scene saveScene = scene; boolean saveShadow = shadowOn; Matrix4 saveVp = viewProj; float saveTime = time;
        float saveFs = fogStart, saveFi = fogInv;
        env = THUMB_ENV; scene = thumbScene; shadowOn = false; fogStart = 1e6f; fogInv = 0; time = 2.2f;
        PerspectiveCamera c = new PerspectiveCamera(28, THUMB, THUMB);
        Vector3 savePos = new Vector3(cam.position);
        thumbFbo.begin();
        gl.glEnable(GL20.GL_SCISSOR_TEST);
        for (int n = 0; n < count && thumbNext < total; n++, thumbNext++) {
            ModelDef d = Assets.defs.get(thumbNext);
            int tx = (thumbNext % THUMB_COLS) * THUMB, ty = (thumbNext / THUMB_COLS) * THUMB;
            gl.glViewport(tx, ty, THUMB, THUMB); gl.glScissor(tx, ty, THUMB, THUMB);
            boolean fxk = d.kind == ModelDef.EFFECT;
            if (fxk) gl.glClearColor(0.07f, 0.09f, 0.14f, 1); else gl.glClearColor(0, 0, 0, 0);
            gl.glDepthMask(true);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            SceneObject o = new SceneObject(d);
            o.uid = 7;
            Scene.defaultScale(d, o.scale);
            if (d.kind == ModelDef.WATER) o.pos.y = 1.6f;
            o.update(thumbScene);
            float rad = Math.max(0.3f, o.radius);
            Vector3 ctr = new Vector3(o.center);
            if (fxk) { ctr.set(0, o.scale.y * 0.5f, 0); rad = Math.max(o.scale.y * 0.62f, Math.max(o.scale.x, o.scale.z) * 1.15f); }
            float dist = rad / (float) Math.sin(Math.toRadians(14)) * (d.kind == ModelDef.MESH ? 0.9f : 1.0f);
            float pitch = d.kind == ModelDef.WATER ? 38 : (fxk ? 14 : 20), yaw = d.kind == ModelDef.FALL ? 20 : 32;
            c.position.set(MathUtils.sinDeg(yaw) * MathUtils.cosDeg(pitch), MathUtils.sinDeg(pitch), MathUtils.cosDeg(yaw) * MathUtils.cosDeg(pitch)).scl(dist).add(ctr);
            c.up.set(0, 1, 0); c.lookAt(ctr); c.near = Math.max(0.05f, dist - rad * 2); c.far = dist + rad * 3; c.update();
            viewProj = c.combined; cam.position.set(c.position);
            current = null; primed.clear();
            gl.glEnable(GL20.GL_DEPTH_TEST); gl.glDepthFunc(GL20.GL_LEQUAL);
            gl.glDisable(GL20.GL_BLEND);
            if (d.kind == ModelDef.MESH) {
                gl.glEnable(GL20.GL_CULL_FACE); gl.glCullFace(GL20.GL_BACK);
                drawMeshObject(o, false, true);
            } else {
                gl.glDisable(GL20.GL_CULL_FACE);
                gl.glEnable(GL20.GL_BLEND);
                gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
                if (d.kind == ModelDef.WATER) {
                    ShaderProgram sp = Shaders.get("water1", "#define FULL\n", Shaders.WAT_V, Shaders.WAT_F);
                    if (sp != null) { use(sp); sp.setUniformMatrix("u_world", o.world); waterUniforms(sp, o.tintA, o.tintB, 0.3f, 0.2f, 1); o.ownMesh.render(sp, GL20.GL_TRIANGLES); }
                } else if (d.kind == ModelDef.FALL) {
                    ShaderProgram sp = Shaders.get("fall", "", Shaders.WAT_V, Shaders.FALL_F);
                    if (sp != null) { use(sp); sp.setUniformMatrix("u_world", o.world); waterUniforms(sp, o.tintA, o.tintB, 0, 1, 1); o.ownMesh.render(sp, GL20.GL_TRIANGLES); }
                } else {
                    gl.glDepthMask(false);
                    add.begin(c); blend.begin(c);
                    Effects.emit(o, add, blend, 3.3f);
                    flushEffects();
                    gl.glDepthMask(true);
                }
            }
            o.dispose();
            TextureRegion reg = new TextureRegion(thumbFbo.getColorBufferTexture(), tx, ty, THUMB, THUMB);
            reg.flip(false, true);
            thumbs.put(d.id, reg);
        }
        gl.glDisable(GL20.GL_SCISSOR_TEST);
        thumbFbo.end();
        gl.glDisable(GL20.GL_BLEND); gl.glDisable(GL20.GL_DEPTH_TEST); gl.glDisable(GL20.GL_CULL_FACE);
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        env = saveEnv; scene = saveScene; shadowOn = saveShadow; viewProj = saveVp; time = saveTime; fogStart = saveFs; fogInv = saveFi;
        cam.position.set(savePos);
        current = null; primed.clear();
        return thumbNext / (float) total;
    }

    public void dispose() {
        if (shadowFbo != null) shadowFbo.dispose();
        if (sceneFbo != null) sceneFbo.dispose();
        if (thumbFbo != null) thumbFbo.dispose();
        quad.dispose(); add.dispose(); blend.dispose();
    }
}
