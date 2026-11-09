package com.star4droid.star2d.suda.viewer;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;
import com.badlogic.gdx.graphics.g3d.shaders.DepthShader;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Pool;

import net.mgsx.gltf.data.GLTF;
import net.mgsx.gltf.data.texture.GLTFImage;
import net.mgsx.gltf.loaders.glb.BinaryDataFileResolver;
import net.mgsx.gltf.loaders.gltf.SeparatedDataFileResolver;
import net.mgsx.gltf.loaders.shared.GLTFLoaderBase;
import net.mgsx.gltf.loaders.shared.data.DataFileResolver;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFlagAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.shaders.PBRDepthShaderProvider;
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Backend-agnostic 3D model viewer (libGDX + gdx-gltf): GLB / glTF (static, skinned, animated) and OBJ + MTL.
 * Used by the Android viewer activity (own process) and by the desktop test harness.
 * <p>
 * Loading never blocks: the file is read, parsed and its pictures decoded on a background thread; the render thread
 * only builds the GL objects. Every public control method may be called from any thread (queued to the render thread).
 * Listener callbacks come on the render thread, except onProgress (also from the loading thread).
 */
public class ModelViewerCore implements ApplicationListener {

    public static final int MODE_MESH = 0, MODE_BONES = 1, MODE_BOTH = 2;

    /** Error codes passed to {@link Listener#onError}: "oom", "unsupported:<detail>", "error:<detail>". */
    public interface Listener {
        /** stage: "read", "parse", "textures", "upload"; p in 0..1. */
        void onProgress(float p, String stage);

        void onLoaded(Info info);

        void onError(String code);

        /** About 10 times per second while an animation is selected. */
        void onAnimation(int index, float time, float duration, boolean playing);
    }

    /** Model statistics. */
    public static class Info {
        public String fileName, format;
        public long fileSize;
        public int vertices, triangles, meshes, materials, textures, maxTextureWidth, maxTextureHeight, bones, nodes;
        public boolean skinned;
        public final List<String> animationNames = new ArrayList<>();
        public final List<Float> animationDurations = new ArrayList<>();
        public float sizeX, sizeY, sizeZ;
    }

    private final File file;
    private final Listener listener;
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    // state (render thread)
    private PerspectiveCamera cam;
    private ModelBatch batch, shadowBatch;
    private ShapeRenderer shapes;
    private final Environment env = new Environment();
    private DirectionalShadowLight sun;
    private com.badlogic.gdx.graphics.Cubemap iblDiffuse, iblSpecular;
    private Texture brdf;
    private Model floorModel;
    private ModelInstance floor;
    private SceneAsset asset;
    private Model objModel;
    private final Array<Pixmap> ownedPixmaps = new Array<>();
    private Scene scene;
    private ModelInstance instance;
    private AnimationController anim;
    private Array<Animation> animations = new Array<>();
    private final Array<Node> joints = new Array<>();
    private final ObjectMap<Mesh, WireMesh> wires = new ObjectMap<>();
    private Material wireMat;
    private volatile Prepared prepared;
    private boolean uploadAnnounced;
    private Info info;
    private String error;
    private float radius = 1f, gridStep = 0.1f;
    private final Vector3 target = new Vector3(), homeTarget = new Vector3();
    private float yaw = 35f, pitch = 20f, dist = 3f, homeDist = 3f;
    private int maxTextureSize = 4096;

    // options
    private int mode = MODE_MESH;
    private boolean wireframe, grid = true, shadows, loop = true, playing = true;
    private float speed = 1f;
    private int animIndex = -1;
    private float animReportT;
    private float uiScale = 1f;

    public ModelViewerCore(File file, Listener listener) {
        this.file = file;
        this.listener = listener;
    }

    // ------------------------------------------------------------------ public controls (any thread)

    public void post(Runnable r) { queue.add(r); }

    public void setMode(int m) { post(() -> mode = m); }
    public void setWireframe(boolean on) { post(() -> wireframe = on); }
    public void setGrid(boolean on) { post(() -> grid = on); }
    public void setShadows(boolean on) { post(() -> shadows = on); }
    public void setLoop(boolean on) { post(() -> { loop = on; if (anim != null && anim.current != null) anim.current.loopCount = on ? -1 : Math.max(anim.current.loopCount, 1); }); }
    public void setSpeed(float s) { post(() -> { speed = s; if (anim != null && anim.current != null) anim.current.speed = s; }); }
    public void setPlaying(boolean on) { post(() -> { playing = on; if (anim != null) { if (on && anim.current != null && anim.current.loopCount == 0) { anim.current.loopCount = loop ? -1 : 1; anim.current.time = 0; } anim.paused = !on; } }); }
    public void playAnimation(int index) { post(() -> selectAnimation(index)); }
    /** Jump to t (seconds) of the current animation. */
    public void seek(float t) { post(() -> doSeek(t)); }
    public void resetView() { post(this::frame); }
    /** Density factor for line widths / joint dots (1 on desktop, the screen density on phones). */
    public void setUiScale(float s) { uiScale = Math.max(1f, s); }
    public void setMaxTextureSize(int px) { maxTextureSize = Math.max(256, px); }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void create() {
        cam = new PerspectiveCamera(50, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes = new ShapeRenderer(20000);
        PBRShaderConfig pc = PBRShaderProvider.createDefaultConfig();
        pc.numBones = 32;
        pc.numDirectionalLights = 1;
        pc.numPointLights = 0;
        pc.numSpotLights = 0;
        batch = new ModelBatch(new BonesPbrProvider(pc));
        DepthShader.Config dc = PBRShaderProvider.createDefaultDepthConfig();
        dc.numBones = 32;
        shadowBatch = new ModelBatch(new BonesDepthProvider(dc));
        sun = new DirectionalShadowLight(2048, 2048, 10, 10, 0.1f, 100f);
        sun.direction.set(-0.5f, -1f, -0.35f).nor();
        sun.color.set(Color.WHITE);
        sun.intensity = 2.2f;
        try {
            IBLBuilder ib = IBLBuilder.createOutdoor(sun);
            iblDiffuse = ib.buildIrradianceMap(32);
            iblSpecular = ib.buildRadianceMap(6);
            ib.dispose();
            brdf = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
        } catch (Throwable t) {
            Gdx.app.error("ModelViewer", "IBL: " + t);
        }
        wireMat = new Material(PBRColorAttribute.createBaseColorFactor(new Color(0.55f, 0.85f, 1f, 1f)), new PBRFlagAttribute(PBRFlagAttribute.Unlit));
        try {
            int[] max = new int[1];
            java.nio.IntBuffer ib = BufferUtils.newIntBuffer(16);
            Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, ib);
            max[0] = ib.get(0);
            if (max[0] > 0) maxTextureSize = Math.min(maxTextureSize, max[0]);
        } catch (Throwable ignored) {}
        setupInput();
        startLoading();
    }

    private void startLoading() {
        final int maxTex = maxTextureSize;
        Thread t = new Thread(() -> {
            try {
                prepared = prepare(maxTex);
            } catch (OutOfMemoryError e) {
                fail("oom");
            } catch (Throwable e) {
                fail(errorCode(e));
            }
        }, "model-viewer-load");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.setDaemon(true);
        t.start();
    }

    private void fail(String code) { post(() -> { error = code; listener.onError(code); }); }

    private static String errorCode(Throwable e) {
        String n = e.getClass().getSimpleName();
        String m = e.getMessage() == null ? n : e.getMessage();
        if (n.contains("Unsupported")) return "unsupported:" + m;
        Throwable c = e.getCause();
        if (c instanceof OutOfMemoryError) return "oom";
        return "error:" + m;
    }

    @Override
    public void resize(int w, int h) {
        if (cam == null) return;
        cam.viewportWidth = w;
        cam.viewportHeight = h;
        updateCamera();
    }

    @Override
    public void render() {
        Runnable r;
        while ((r = queue.poll()) != null) {
            try { r.run(); } catch (Throwable t) { Gdx.app.error("ModelViewer", "task: " + t); }
        }
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.105f, 0.115f, 0.13f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        Prepared p = prepared;
        if (p != null && instance == null && error == null) {
            if (!uploadAnnounced) { // one frame to show the "uploading" stage before the GL work
                uploadAnnounced = true;
                listener.onProgress(0.95f, "upload");
            } else {
                prepared = null;
                upload(p);
            }
        }
        updateCamera();
        if (grid) drawGrid();
        if (instance == null) return;

        if (anim != null) {
            anim.update(dt);
            animReportT += dt;
            if (animReportT > 0.1f && anim.current != null && animIndex >= 0) {
                animReportT = 0;
                listener.onAnimation(animIndex, anim.current.time, anim.current.duration, !anim.paused && anim.current.loopCount != 0);
            }
        }

        boolean showMesh = mode != MODE_BONES;
        try {
            if (showMesh && shadows && !wireframe) {
                sun.setCenter(target);
                sun.begin();
                shadowBatch.begin(sun.getCamera());
                shadowBatch.render(instance);
                shadowBatch.end();
                sun.end();
                Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            }
            env.shadowMap = showMesh && shadows && !wireframe ? sun : null;
            batch.begin(cam);
            if (grid && floor != null && shadows && !wireframe) batch.render(floor, env);
            if (showMesh) batch.render(wireframe ? wireProvider : instance, env);
            batch.end();
        } catch (Throwable t) {
            try { batch.end(); } catch (Throwable ignored) {}
            if (error == null) {
                error = t instanceof OutOfMemoryError ? "oom" : "error:" + t.getMessage();
                listener.onError(error);
            }
        }
        if (mode != MODE_MESH) drawBones();
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void dispose() {
        if (asset != null) asset.dispose();
        if (objModel != null) objModel.dispose();
        for (Pixmap pm : ownedPixmaps) pm.dispose();
        for (WireMesh w : wires.values()) w.mesh.dispose();
        Prepared p = prepared;
        if (p != null) p.disposePixmaps();
        if (floorModel != null) floorModel.dispose();
        if (batch != null) batch.dispose();
        if (shadowBatch != null) shadowBatch.dispose();
        if (shapes != null) shapes.dispose();
        if (sun != null) sun.dispose();
        if (iblDiffuse != null) iblDiffuse.dispose();
        if (iblSpecular != null) iblSpecular.dispose();
        if (brdf != null) brdf.dispose();
    }

    // ------------------------------------------------------------------ background preparation

    /** Everything that can be done without GL. */
    private static final class Prepared {
        String format;
        DataFileResolver gltf;
        ModelData obj;
        final Map<Object, Pixmap> pixmaps = new HashMap<>();
        int maxW, maxH;

        void disposePixmaps() { for (Pixmap p : pixmaps.values()) try { p.dispose(); } catch (Throwable ignored) {} pixmaps.clear(); }
    }

    private Prepared prepare(int maxTex) throws Exception {
        String name = file.getName().toLowerCase(Locale.ROOT);
        Prepared p = new Prepared();
        listener.onProgress(0f, "read");
        if (name.endsWith(".glb")) {
            p.format = "GLB";
            byte[] bytes = readAll(file);
            listener.onProgress(0.35f, "parse");
            BinaryDataFileResolver r = new BinaryDataFileResolver();
            r.load(bytes);
            bytes = null;
            p.gltf = new PrefetchedResolver(r, p.pixmaps);
            decodeImages(p, r, maxTex);
        } else if (name.endsWith(".gltf")) {
            p.format = "glTF";
            listener.onProgress(0.2f, "parse");
            SeparatedDataFileResolver r = new SeparatedDataFileResolver();
            r.load(new FileHandle(file));
            p.gltf = new PrefetchedResolver(r, p.pixmaps);
            decodeImages(p, r, maxTex);
        } else if (name.endsWith(".obj")) {
            p.format = "OBJ";
            listener.onProgress(0.2f, "parse");
            p.obj = new ObjLoader().loadModelData(objHandle(file), new ObjLoader.ObjLoaderParameters(true));
            int total = 0;
            for (ModelMaterial m : p.obj.materials) if (m.textures != null) total += m.textures.size;
            int done = 0;
            for (ModelMaterial m : p.obj.materials) {
                if (m.textures == null) continue;
                for (ModelTexture t : m.textures) {
                    listener.onProgress(0.4f + 0.5f * done++ / Math.max(1, total), "textures");
                    if (p.pixmaps.containsKey(t.fileName)) continue;
                    File f = resolveTexture(t.fileName);
                    if (f == null) continue;
                    try {
                        byte[] b = readAll(f);
                        Pixmap pm = new Pixmap(b, 0, b.length);
                        p.maxW = Math.max(p.maxW, pm.getWidth());
                        p.maxH = Math.max(p.maxH, pm.getHeight());
                        p.pixmaps.put(t.fileName, fit(pm, maxTex));
                    } catch (OutOfMemoryError e) {
                        throw e;
                    } catch (Throwable ignored) {}
                }
            }
        } else throw new UnsupportedOperationException("Unsupported " + name);
        listener.onProgress(0.9f, "upload");
        return p;
    }

    private void decodeImages(Prepared p, DataFileResolver r, int maxTex) {
        GLTF root = r.getRoot();
        if (root.images == null) return;
        int n = root.images.size;
        for (int i = 0; i < n; i++) {
            listener.onProgress(0.45f + 0.45f * i / Math.max(1, n), "textures");
            GLTFImage img = root.images.get(i);
            try {
                Pixmap pm = r.load(img);
                if (pm == null) continue;
                p.maxW = Math.max(p.maxW, pm.getWidth());
                p.maxH = Math.max(p.maxH, pm.getHeight());
                p.pixmaps.put(img, fit(pm, maxTex));
            } catch (OutOfMemoryError e) {
                throw e;
            } catch (Throwable ignored) {
                // decoded again (and reported) by the loader on the render thread
            }
        }
    }

    /** Halves a picture until it fits the GPU / memory limit. */
    private static Pixmap fit(Pixmap pm, int max) {
        int w = pm.getWidth(), h = pm.getHeight();
        if (w <= max && h <= max) return pm;
        while (w > max || h > max) { w = Math.max(1, w / 2); h = Math.max(1, h / 2); }
        Pixmap s = new Pixmap(w, h, pm.getFormat());
        s.setFilter(Pixmap.Filter.BiLinear);
        s.setBlending(Pixmap.Blending.None);
        s.drawPixmap(pm, 0, 0, pm.getWidth(), pm.getHeight(), 0, 0, w, h);
        pm.dispose();
        return s;
    }

    private byte[] readAll(File f) throws Exception {
        long len = f.length();
        if (len > Integer.MAX_VALUE - 16) throw new OutOfMemoryError();
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(32, len));
            byte[] buf = new byte[256 * 1024];
            long done = 0;
            int n;
            boolean main = f.equals(file);
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (main && len > 0) listener.onProgress(0.35f * done / len, "read");
            }
            return out.toByteArray();
        }
    }

    private File resolveTexture(String path) {
        if (path == null) return null;
        File f = new File(path);
        if (f.isFile()) return f;
        String n = path.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        File g = new File(file.getParentFile(), n);
        return g.isFile() ? g : null;
    }

    /**
     * libGDX's ObjLoader keeps one material per group ("g"): files that only switch "usemtl" would get the last material
     * everywhere. A group is started at each usemtl that has none (same fix as the engine runtime).
     */
    private static FileHandle objHandle(final File src) {
        final FileHandle h = new FileHandle(src);
        try {
            String[] lines = h.readString("UTF-8").split("\n");
            StringBuilder sb = new StringBuilder();
            boolean groupSinceMtl = false;
            int n = 0;
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("g ") || t.startsWith("o ")) {
                    groupSinceMtl = true;
                    sb.append("g ").append(t.substring(2).trim()).append('_').append(n++).append('\n');
                    continue;
                }
                if (t.startsWith("usemtl ")) {
                    if (!groupSinceMtl) sb.append("g mat_").append(n++).append('\n');
                    groupSinceMtl = false;
                } else if (t.startsWith("f ")) groupSinceMtl = false;
                sb.append(line).append('\n');
            }
            final byte[] data = sb.toString().getBytes("UTF-8");
            return new FileHandle(src) {
                @Override public InputStream read() { return new java.io.ByteArrayInputStream(data); }
                @Override public FileHandle parent() { return h.parent(); }
            };
        } catch (Throwable t) {
            return h;
        }
    }

    /** Hands the pictures decoded in the background to the glTF loader. */
    private static final class PrefetchedResolver implements DataFileResolver {
        final DataFileResolver base;
        final Map<Object, Pixmap> pixmaps;

        PrefetchedResolver(DataFileResolver base, Map<Object, Pixmap> pixmaps) { this.base = base; this.pixmaps = pixmaps; }

        @Override public void load(FileHandle file) { base.load(file); }
        @Override public GLTF getRoot() { return base.getRoot(); }
        @Override public ByteBuffer getBuffer(int buffer) { return base.getBuffer(buffer); }
        @Override public Pixmap load(GLTFImage image) {
            Pixmap p = pixmaps.remove(image); // ownership goes to the loader (SceneAsset.pixmaps)
            return p != null ? p : base.load(image);
        }
    }

    // ------------------------------------------------------------------ GL upload

    private void upload(Prepared p) {
        try {
            if (p.gltf != null) {
                GLTFLoaderBase loader = new GLTFLoaderBase();
                asset = loader.load(p.gltf, false);
                p.disposePixmaps(); // pictures the loader did not use
                scene = new Scene(asset.scene);
                instance = scene.modelInstance;
                animations = asset.animations != null ? asset.animations : instance.animations;
                anim = scene.animationController != null ? scene.animationController : new AnimationController(instance);
            } else {
                final Map<Object, Pixmap> pm = p.pixmaps;
                objModel = new Model(p.obj, new TextureProvider() {
                    @Override public Texture load(String fileName) {
                        Pixmap x = pm.get(fileName);
                        if (x != null) {
                            Texture t = new Texture(x, true);
                            t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                            t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                            return t;
                        }
                        File f = resolveTexture(fileName);
                        if (f == null) throw new RuntimeException("missing texture " + fileName);
                        return new Texture(new FileHandle(f), true);
                    }
                });
                // kept for managed-texture reloads (context loss)
                for (Pixmap x : pm.values()) ownedPixmaps.add(x);
                pm.clear();
                for (Material m : objModel.materials) {
                    // the PBR shader expects its own texture attribute class
                    TextureAttribute d = (TextureAttribute) m.get(TextureAttribute.Diffuse);
                    Array<Attribute> plain = new Array<>();
                    for (Attribute a : m) if (a instanceof TextureAttribute && !(a instanceof PBRTextureAttribute)) plain.add(a);
                    for (Attribute a : plain) m.remove(a.type);
                    ColorAttribute kd = (ColorAttribute) m.get(ColorAttribute.Diffuse);
                    if (kd != null && !(kd instanceof PBRColorAttribute)) {
                        Color col = new Color(kd.color); // .mtl colours are display (sRGB) values, PBR wants linear
                        col.set((float) Math.pow(col.r, 2.2), (float) Math.pow(col.g, 2.2), (float) Math.pow(col.b, 2.2), col.a);
                        m.remove(ColorAttribute.Diffuse);
                        m.set(PBRColorAttribute.createBaseColorFactor(col));
                    }
                    if (d != null && d.textureDescription.texture instanceof Texture)
                        m.set(PBRTextureAttribute.createBaseColorTexture((Texture) d.textureDescription.texture));
                    if (!m.has(PBRFloatAttribute.Metallic)) m.set(PBRFloatAttribute.createMetallic(0f));
                    if (!m.has(PBRFloatAttribute.Roughness)) m.set(PBRFloatAttribute.createRoughness(0.85f));
                }
                instance = new ModelInstance(objModel);
                animations = instance.animations;
                anim = null;
            }
            collectJoints();
            info = buildInfo(p);
            placeModel();
            buildEnvironment();
            frame();
            if (anim != null && animations.size > 0) selectAnimation(0);
            listener.onProgress(1f, "upload");
            listener.onLoaded(info);
        } catch (OutOfMemoryError e) {
            p.disposePixmaps();
            instance = null;
            error = "oom";
            listener.onError(error);
        } catch (Throwable e) {
            p.disposePixmaps();
            instance = null;
            error = errorCode(e);
            listener.onError(error);
        }
    }

    private void collectJoints() {
        joints.clear();
        ObjectSet<Node> set = new ObjectSet<>();
        for (Node n : instance.nodes) collectJoints(n, set);
        for (Node n : set) joints.add(n);
    }

    private void collectJoints(Node n, ObjectSet<Node> set) {
        for (NodePart p : n.parts)
            if (p.invBoneBindTransforms != null) for (ObjectMap.Entry<Node, Matrix4> e : p.invBoneBindTransforms) set.add(e.key);
        for (Node c : n.getChildren()) collectJoints(c, set);
    }

    private Info buildInfo(Prepared p) {
        Info i = new Info();
        i.fileName = file.getName();
        i.format = p.format;
        i.fileSize = file.length();
        if (p.gltf != null && "glTF".equals(p.format)) {
            // + external buffers / pictures next to the .gltf
            GLTF root = p.gltf.getRoot();
            if (root.buffers != null) for (int k = 0; k < root.buffers.size; k++) {
                String u = root.buffers.get(k).uri;
                if (u != null && !u.startsWith("data:")) i.fileSize += new File(file.getParentFile(), u).length();
            }
            if (root.images != null) for (int k = 0; k < root.images.size; k++) {
                String u = root.images.get(k).uri;
                if (u != null && !u.startsWith("data:")) i.fileSize += new File(file.getParentFile(), u).length();
            }
        }
        Model m = instance.model;
        ObjectSet<Mesh> meshes = new ObjectSet<>();
        Array<MeshPart> parts = new Array<>();
        ObjectSet<MeshPart> seenParts = new ObjectSet<>();
        for (Node n : instance.nodes) collectParts(n, parts, seenParts);
        for (MeshPart mp : parts) {
            meshes.add(mp.mesh);
            if (mp.primitiveType == GL20.GL_TRIANGLES) i.triangles += mp.size / 3;
            else if (mp.primitiveType == GL20.GL_TRIANGLE_STRIP || mp.primitiveType == GL20.GL_TRIANGLE_FAN) i.triangles += Math.max(0, mp.size - 2);
        }
        for (Mesh mesh : meshes) i.vertices += mesh.getNumVertices();
        i.meshes = meshes.size;
        i.materials = m.materials.size;
        ObjectSet<Texture> tex = new ObjectSet<>();
        for (Material mat : m.materials)
            for (Attribute a : mat) if (a instanceof TextureAttribute && ((TextureAttribute) a).textureDescription.texture instanceof Texture)
                tex.add((Texture) ((TextureAttribute) a).textureDescription.texture);
        i.textures = tex.size;
        i.maxTextureWidth = p.maxW;
        i.maxTextureHeight = p.maxH;
        if (i.maxTextureWidth == 0) for (Texture t : tex) { i.maxTextureWidth = Math.max(i.maxTextureWidth, t.getWidth()); i.maxTextureHeight = Math.max(i.maxTextureHeight, t.getHeight()); }
        i.bones = joints.size;
        i.skinned = joints.size > 0;
        int[] nodes = {0};
        for (Node n : instance.nodes) countNodes(n, nodes);
        i.nodes = nodes[0];
        for (Animation a : animations) {
            i.animationNames.add(a.id == null || a.id.isEmpty() ? "#" + (i.animationNames.size() + 1) : a.id);
            i.animationDurations.add(a.duration);
        }
        return i;
    }

    private static void collectParts(Node n, Array<MeshPart> out, ObjectSet<MeshPart> seen) {
        for (NodePart p : n.parts) if (p.meshPart != null && seen.add(p.meshPart)) out.add(p.meshPart);
        for (Node c : n.getChildren()) collectParts(c, out, seen);
    }

    private static void countNodes(Node n, int[] c) {
        c[0]++;
        for (Node k : n.getChildren()) countNodes(k, c);
    }

    private final BoundingBox bounds = new BoundingBox();

    /** Model centred on x/z, standing on y = 0. */
    private void placeModel() {
        instance.transform.idt();
        instance.calculateTransforms();
        BoundingBox b = new BoundingBox();
        instance.calculateBoundingBox(b);
        if (joints.size > 1) {
            // skinned meshes are often stored in other units than their skeleton (e.g. cm mesh + 0.01 armature)
            BoundingBox jb = new BoundingBox();
            jb.inf();
            Vector3 v = new Vector3();
            for (Node j : joints) jb.ext(j.globalTransform.getTranslation(v));
            float bs = b.getDimensions(new Vector3()).len(), js = jb.getDimensions(new Vector3()).len();
            if (!b.isValid() || bs <= 0 || (js > 0 && (bs / js > 15f || bs / js < 0.06f))) {
                Vector3 d = jb.getDimensions(new Vector3()).scl(0.2f);
                b.set(jb.min.cpy().sub(d.x, 0, d.z), jb.max.cpy().add(d));
            }
        }
        if (!b.isValid() || b.getDimensions(new Vector3()).len() <= 1e-6f) b.set(new Vector3(-0.5f, 0, -0.5f), new Vector3(0.5f, 1, 0.5f));
        Vector3 c = b.getCenter(new Vector3());
        instance.transform.setToTranslation(-c.x, -b.min.y, -c.z);
        instance.calculateTransforms();
        Vector3 d = b.getDimensions(new Vector3());
        info.sizeX = d.x;
        info.sizeY = d.y;
        info.sizeZ = d.z;
        bounds.set(new Vector3(-d.x / 2, 0, -d.z / 2), new Vector3(d.x / 2, d.y, d.z / 2));
        radius = Math.max(1e-4f, d.len() / 2f);
        gridStep = (float) Math.pow(10, Math.floor(Math.log10(radius * 2f)) - 1);
        if (radius * 2f / gridStep > 40) gridStep *= 5;
        homeTarget.set(0, d.y / 2f, 0);
    }

    private void buildEnvironment() {
        env.clear();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.35f, 0.35f, 0.38f, 1f));
        if (iblDiffuse != null) {
            env.set(PBRCubemapAttribute.createDiffuseEnv(iblDiffuse));
            env.set(PBRCubemapAttribute.createSpecularEnv(iblSpecular));
            if (brdf != null) env.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdf));
        }
        env.set(new PBRFloatAttribute(PBRFloatAttribute.ShadowBias, 1f / 255f));
        float s = radius * 3f;
        sun.setViewport(s, s, radius * 0.05f, radius * 12f);
        env.add(sun);
        if (floorModel != null) floorModel.dispose();
        float fs = radius * 8f;
        ModelBuilder mb = new ModelBuilder();
        Material fm = new Material(PBRColorAttribute.createBaseColorFactor(new Color(0.07f, 0.075f, 0.085f, 1f)),
                PBRFloatAttribute.createMetallic(0f), PBRFloatAttribute.createRoughness(1f));
        floorModel = mb.createRect(-fs, -radius * 0.002f, fs, fs, -radius * 0.002f, fs, fs, -radius * 0.002f, -fs, -fs, -radius * 0.002f, -fs,
                0, 1, 0, fm, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        floor = new ModelInstance(floorModel);
    }

    // ------------------------------------------------------------------ animation

    private void selectAnimation(int index) {
        if (anim == null || index < 0 || index >= animations.size) return;
        animIndex = index;
        Animation a = animations.get(index);
        anim.setAnimation(a.id, loop ? -1 : 1, speed, null);
        anim.paused = !playing;
        if (!playing) doSeek(0);
    }

    private void doSeek(float t) {
        if (anim == null || anim.current == null) return;
        anim.current.loopCount = loop ? -1 : 1;
        anim.current.time = MathUtils.clamp(t, 0, anim.current.duration);
        boolean p = anim.paused;
        anim.paused = false;
        float s = anim.current.speed;
        anim.current.speed = 0f;
        anim.update(0.0001f);
        anim.current.speed = s;
        anim.paused = p;
        listener.onAnimation(animIndex, anim.current.time, anim.current.duration, !p);
    }

    // ------------------------------------------------------------------ camera + input

    private void frame() {
        target.set(homeTarget);
        float fov = cam.fieldOfView * MathUtils.degreesToRadians;
        homeDist = radius / (float) Math.sin(fov / 2f) * 1.05f;
        float aspect = cam.viewportWidth / Math.max(1f, cam.viewportHeight);
        if (aspect < 1f) homeDist /= Math.max(0.55f, aspect); // portrait: the horizontal fov is the narrow one
        dist = homeDist;
        yaw = 35f;
        pitch = 18f;
        updateCamera();
    }

    private void updateCamera() {
        if (cam == null) return;
        pitch = MathUtils.clamp(pitch, -89f, 89f);
        float cp = MathUtils.cosDeg(pitch);
        cam.position.set(target).add(MathUtils.sinDeg(yaw) * cp * dist, MathUtils.sinDeg(pitch) * dist, MathUtils.cosDeg(yaw) * cp * dist);
        cam.up.set(Vector3.Y);
        cam.lookAt(target);
        cam.near = Math.max(dist * 0.01f, radius * 0.001f);
        cam.far = dist * 20f + radius * 20f;
        cam.update();
    }

    private int dragButton;
    private final Vector2 pinchPrev1 = new Vector2(), pinchPrev2 = new Vector2();
    private boolean pinching;

    private void setupInput() {
        GestureDetector gd = new GestureDetector(new GestureDetector.GestureAdapter() {
            @Override
            public boolean tap(float x, float y, int count, int button) {
                if (count >= 2) frame();
                return true;
            }

            @Override
            public boolean pan(float x, float y, float dx, float dy) {
                if (pinching) return true;
                if (dragButton == Input.Buttons.RIGHT || dragButton == Input.Buttons.MIDDLE) panBy(dx, dy);
                else {
                    yaw -= dx * 0.4f;
                    pitch += dy * 0.4f;
                }
                return true;
            }

            @Override
            public boolean pinch(Vector2 i1, Vector2 i2, Vector2 p1, Vector2 p2) {
                if (!pinching) { pinching = true; pinchPrev1.set(p1); pinchPrev2.set(p2); return true; }
                float before = pinchPrev1.dst(pinchPrev2), now = p1.dst(p2);
                if (before > 1f && now > 1f) zoomBy(before / now);
                float mx = (p1.x + p2.x) / 2f - (pinchPrev1.x + pinchPrev2.x) / 2f;
                float my = (p1.y + p2.y) / 2f - (pinchPrev1.y + pinchPrev2.y) / 2f;
                panBy(mx, my);
                pinchPrev1.set(p1);
                pinchPrev2.set(p2);
                return true;
            }

            @Override public void pinchStop() { pinching = false; }
        });
        InputMultiplexer mux = new InputMultiplexer(new InputAdapter() {
            @Override public boolean touchDown(int x, int y, int pointer, int button) { if (pointer == 0) dragButton = button; return false; }
            @Override public boolean scrolled(float ax, float ay) { zoomBy(ay > 0 ? 1.12f : 1f / 1.12f); return true; }
        }, gd);
        Gdx.input.setInputProcessor(mux);
    }

    private void zoomBy(float f) { dist = MathUtils.clamp(dist * f, radius * 0.05f, homeDist * 20f + radius); }

    private final Vector3 tmp = new Vector3(), tmp2 = new Vector3();

    private void panBy(float dx, float dy) {
        float h = Math.max(1f, Gdx.graphics.getHeight());
        float scale = 2f * dist * (float) Math.tan(cam.fieldOfView * MathUtils.degreesToRadians / 2f) / h;
        tmp.set(cam.direction).crs(cam.up).nor().scl(-dx * scale);
        tmp2.set(cam.up).nor().scl(dy * scale);
        target.add(tmp).add(tmp2);
    }

    // ------------------------------------------------------------------ overlays

    private void drawGrid() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(cam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        float ext = gridStep * Math.round(radius * 3f / gridStep);
        int n = Math.round(ext / gridStep);
        for (int k = -n; k <= n; k++) {
            float v = k * gridStep;
            boolean major = k % 5 == 0;
            if (k == 0) shapes.setColor(0.85f, 0.3f, 0.3f, 0.8f);
            else shapes.setColor(1f, 1f, 1f, major ? 0.22f : 0.09f);
            shapes.line(-ext, 0, v, ext, 0, v);
            if (k == 0) shapes.setColor(0.3f, 0.45f, 0.95f, 0.8f);
            shapes.line(v, 0, -ext, v, 0, ext);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private final Vector3 jp = new Vector3(), pp = new Vector3();

    private void drawBones() {
        if (joints.size == 0) return;
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
        float w = Math.max(1.5f, 1.6f * uiScale);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1f, 0.78f, 0.2f, 0.95f);
        ObjectSet<Node> set = new ObjectSet<>();
        for (Node j : joints) set.add(j);
        for (Node j : joints) {
            Node parent = j.getParent();
            if (parent == null || !set.contains(parent)) continue;
            if (!project(j, jp) || !project(parent, pp)) continue;
            shapes.rectLine(pp.x, pp.y, jp.x, jp.y, w);
        }
        float r = Math.max(3f, 3.2f * uiScale);
        for (Node j : joints) {
            if (!project(j, jp)) continue;
            shapes.setColor(0.08f, 0.08f, 0.1f, 0.9f);
            shapes.circle(jp.x, jp.y, r + w * 0.8f, 14);
            shapes.setColor(j.getParent() != null && set.contains(j.getParent()) ? new Color(0.35f, 0.9f, 1f, 1f) : new Color(1f, 0.35f, 0.5f, 1f));
            shapes.circle(jp.x, jp.y, r, 14);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private final Matrix4 m4 = new Matrix4();

    private boolean project(Node n, Vector3 out) {
        m4.set(instance.transform).mul(n.globalTransform);
        m4.getTranslation(out);
        cam.project(out, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return out.z >= 0f && out.z <= 1f;
    }

    // ------------------------------------------------------------------ wireframe

    private static final class WireMesh {
        Mesh mesh;
        final IntMap<long[]> parts = new IntMap<>(); // part offset -> {wire offset, wire size}
    }

    private final RenderableProvider wireProvider = new RenderableProvider() {
        @Override
        public void getRenderables(Array<Renderable> out, Pool<Renderable> pool) {
            int start = out.size;
            instance.getRenderables(out, pool);
            for (int i = start; i < out.size; i++) {
                Renderable r = out.get(i);
                if (r.meshPart.primitiveType != GL20.GL_TRIANGLES) continue;
                WireMesh w = wire(r.meshPart.mesh);
                if (w == null) continue;
                long[] p = w.parts.get(r.meshPart.offset);
                if (p == null) continue;
                r.meshPart.mesh = w.mesh;
                r.meshPart.offset = (int) p[0];
                r.meshPart.size = (int) p[1];
                r.meshPart.primitiveType = GL20.GL_LINES;
                r.material = wireMat;
            }
        }
    };

    private WireMesh wire(Mesh m) {
        if (wires.containsKey(m)) return wires.get(m);
        WireMesh w = null;
        try {
            if (m.getNumVertices() <= 65535) {
                w = new WireMesh();
                w.mesh = buildWire(m, w);
            }
        } catch (Throwable t) {
            w = null;
        }
        wires.put(m, w);
        return w;
    }

    /** Copy of the vertices with line indices for every triangle part of the source mesh. */
    private Mesh buildWire(Mesh m, WireMesh w) {
        VertexAttributes va = m.getVertexAttributes();
        int nv = m.getNumVertices();
        float[] verts = new float[nv * va.vertexSize / 4];
        m.getVertices(verts);
        int ni = m.getNumIndices();
        short[] src = null;
        if (ni > 0) { src = new short[ni]; m.getIndices(src); }
        // every triangle part of the model that uses this mesh
        Array<int[]> ranges = new Array<>();
        ObjectSet<MeshPart> seen = new ObjectSet<>();
        Array<MeshPart> parts = new Array<>();
        for (Node n : instance.nodes) collectParts(n, parts, seen);
        int total = 0;
        for (MeshPart p : parts) if (p.mesh == m && p.primitiveType == GL20.GL_TRIANGLES) { ranges.add(new int[]{p.offset, p.size}); total += p.size * 2; }
        short[] lines = new short[Math.max(2, total)];
        int k = 0;
        for (int[] r : ranges) {
            int startK = k;
            for (int t = 0; t + 2 < r[1]; t += 3) {
                int a = idx(src, r[0] + t), b = idx(src, r[0] + t + 1), c = idx(src, r[0] + t + 2);
                lines[k++] = (short) a; lines[k++] = (short) b;
                lines[k++] = (short) b; lines[k++] = (short) c;
                lines[k++] = (short) c; lines[k++] = (short) a;
            }
            w.parts.put(r[0], new long[]{startK, k - startK});
        }
        Mesh out = new Mesh(true, nv, Math.max(2, k), va);
        out.setVertices(verts);
        short[] fin = new short[Math.max(2, k)];
        System.arraycopy(lines, 0, fin, 0, k);
        out.setIndices(fin);
        return out;
    }

    private static int idx(short[] src, int i) { return src == null ? i : (src[i] & 0xFFFF); }

    // ------------------------------------------------------------------ shader providers with any bone count

    private static final class BonesPbrProvider extends PBRShaderProvider {
        BonesPbrProvider(PBRShaderConfig c) { super(c); }

        @Override
        protected Shader createShader(Renderable r) {
            if (r.bones != null && r.bones.length > config.numBones) config.numBones = r.bones.length;
            return super.createShader(r);
        }
    }

    private static final class BonesDepthProvider extends PBRDepthShaderProvider {
        BonesDepthProvider(DepthShader.Config c) { super(c); }

        @Override
        protected Shader createShader(Renderable r) {
            if (r.bones != null && r.bones.length > config.numBones) config.numBones = r.bones.length;
            return super.createShader(r);
        }
    }

    // ------------------------------------------------------------------ helpers for UIs

    public static String fmtSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024f);
        return String.format(Locale.US, "%.1f MB", b / 1048576f);
    }
}
