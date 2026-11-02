package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.environment.SpotLight;
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectSet;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.PropertySet;

import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.lights.PointLightEx;
import net.mgsx.gltf.scene3d.lights.SpotLightEx;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.shaders.PBRDepthShaderProvider;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;

/**
 * The 3D world of a 3D scene: elements, lights, cameras, sky, fog, physics, render modes, quality and post effects.
 * The scene ({@link StageImp}) renders it before its 2D stages, so HUDs, joysticks and UI holders stay on top.
 * The editor uses the same class for its viewport (editor = true: no physics, no scripts).
 */
public class World3D implements Disposable {
	/** Seconds since the start (shaders, water). */
	public static float time;
	/** World being drawn now (shaders read its sun / ambient). */
	public static World3D drawing;

	public final StageImp stage;
	public boolean editor;
	public final Array<Object3D> objects = new Array<Object3D>();
	public final Array<Light3D> lights = new Array<Light3D>();
	public final Array<Camera3D> cameras = new Array<Camera3D>();
	public Camera3D activeCamera;
	/** Camera used when the scene has none (and by the editor). */
	public final PerspectiveCamera defaultCamera = new PerspectiveCamera(60, 16, 9);
	public Camera camera = defaultCamera;
	private Physics3D physics;

	// ---- look
	public String renderMode = Shaders3D.SIMPLE, preset = "DAY", skyMode = "GRADIENT", postEffect = "NONE", postParams = "";
	public final Color ambient = new Color(0.45f, 0.47f, 0.52f, 1f), sunColor = new Color(1f, 0.96f, 0.88f, 1f);
	public final Color skyTop = new Color(0.36f, 0.62f, 0.95f, 1f), skyBottom = new Color(0.78f, 0.88f, 0.98f, 1f), fogColor = new Color(0.78f, 0.86f, 0.95f, 1f);
	public final Vector3 sunDir = new Vector3(-0.45f, -1f, -0.35f).nor();
	public float sunIntensity = 1f, ambientIntensity = 1f, fogNear = 40, fogFar = 160, shadowDistance = 40;
	public boolean sunEnabled = true, sunShadows = true, fogEnabled;
	public String skyImage = "";
	public String defaultQuality = "";
	public float exposure = 1f;

	// ---- render objects
	private ModelBatch batch, unlitBatch, shadowBatch, outlineBatch;
	private String batchMode = "";
	private boolean batchPixel;
	private final Environment env = new Environment();
	private DirectionalLight sun;
	private DirectionalShadowLight shadowLight;
	private int shadowSizeNow = -1;
	private ShapeRenderer shapes;
	private ModelInstance skyDome;
	private Texture skyTexture;
	private String skyTextureFor = "";
	private FrameBuffer fbo;
	private PostFx3D post;
	private final Array<Renderable> outlines = new Array<Renderable>();
	private final Array<Object3D> visible = new Array<Object3D>();
	private final Array<Object3D> waters = new Array<Object3D>();
	private com.badlogic.gdx.graphics.g3d.Attribute iblDiffuse, iblSpecular;
	private Texture brdfLut;

	public World3D(StageImp stage) {
		this.stage = stage;
		defaultCamera.position.set(0, 4, 10);
		defaultCamera.lookAt(0, 0.5f, 0);
		defaultCamera.near = 0.1f;
		defaultCamera.far = 500f;
		defaultCamera.update();
		if (stage != null && stage.getProject() != null) Assets3D.projectPath = stage.getProject().getPath();
	}

	// ------------------------------------------------------------------ registry

	public void add(Object3D o) {
		if (!objects.contains(o, true)) objects.add(o);
	}

	public void remove(Object3D o) {
		objects.removeValue(o, true);
		touchingPairs.removeIf(o.getName());
	}

	public void addLight(Light3D l) {
		if (!lights.contains(l, true)) lights.add(l);
	}

	public void removeLight(Light3D l) {
		lights.removeValue(l, true);
	}

	public void addCamera(Camera3D c) {
		if (!cameras.contains(c, true)) cameras.add(c);
		if (c.def != null && c.def.Active || activeCamera == null && !editor) setActiveCamera(c);
	}

	public void setActiveCamera(Camera3D c) {
		activeCamera = c;
	}

	/** Physics allowed (game, not the editor). */
	public boolean physicsAllowed() {
		return !editor;
	}

	public Physics3D physics() {
		if (physics == null) {
			physics = new Physics3D(this);
			physics.setGravity(gravity.x, gravity.y, gravity.z);
		}
		return physics;
	}

	public boolean hasPhysics() {
		return physics != null;
	}

	public final Vector3 gravity = new Vector3(0, -9.81f, 0);

	public void setGravity(float x, float y, float z) {
		gravity.set(x, y, z);
		if (physics != null) physics.setGravity(x, y, z);
	}

	public Object3D find(String name) {
		for (Object3D o : objects) if (name.equals(o.getName())) return o;
		return null;
	}

	// ------------------------------------------------------------------ scene settings

	private static String str(PropertySet<String, Object> p, String k, String def) {
		if (p == null || !p.containsKey(k) || p.get(k) == null) return def;
		String s = p.get(k).toString();
		return s.length() == 0 ? def : s;
	}

	private static float num(PropertySet<String, Object> p, String k, float def) {
		try {
			String s = str(p, k, null);
			return s == null ? def : Float.parseFloat(s);
		} catch (Exception e) {
			return def;
		}
	}

	private static boolean bool(PropertySet<String, Object> p, String k, boolean def) {
		String s = str(p, k, null);
		return s == null ? def : "true".equalsIgnoreCase(s);
	}

	/** Reads the 3D settings of the scene (configs/scene.json). */
	public void configure(PropertySet<String, Object> p) {
		renderMode = str(p, "Render Mode", Shaders3D.SIMPLE).toUpperCase();
		preset = str(p, "Lighting Preset", "DAY").toUpperCase();
		applyPreset(preset);
		if ("CUSTOM".equals(preset)) {
			ambient.set(Materials3D.color(str(p, "Ambient Color", "#737882"), ambient));
			ambientIntensity = num(p, "Ambient Intensity", 1f);
			sunEnabled = bool(p, "Sun", true);
			sunColor.set(Materials3D.color(str(p, "Sun Color", "#FFF5E0"), sunColor));
			sunIntensity = num(p, "Sun Intensity", 1f);
			setSunAngles(num(p, "Sun Pitch", -55), num(p, "Sun Yaw", 35));
			skyTop.set(Materials3D.color(str(p, "Sky Top", "#5C9EF2"), skyTop));
			skyBottom.set(Materials3D.color(str(p, "Sky Bottom", "#C7E0FA"), skyBottom));
			fogEnabled = bool(p, "Fog", false);
			fogColor.set(Materials3D.color(str(p, "Fog Color", "#C7DBF2"), fogColor));
			fogNear = num(p, "Fog Near", 40);
			fogFar = num(p, "Fog Far", 160);
		}
		skyMode = str(p, "Sky", skyMode).toUpperCase();
		skyImage = str(p, "Sky Image", "");
		sunShadows = bool(p, "Shadows", true);
		shadowDistance = num(p, "Shadow Distance", 40);
		gravity.set(num(p, "Gravity X", 0), num(p, "Gravity Y", -9.81f), num(p, "Gravity Z", 0));
		if (physics != null) physics.setGravity(gravity.x, gravity.y, gravity.z);
		postEffect = str(p, "Post Effect", "NONE").toUpperCase();
		postParams = str(p, "Post Params", "");
		Shaders3D.toonBands = num(p, "Toon Bands", 3);
		exposure = num(p, "Exposure", 1f);
		defaultQuality = str(p, "Quality", "MEDIUM");
		if (!editor) Quality3D.load(defaultQuality);
	}

	public void setSunAngles(float pitch, float yaw) {
		sunDir.set(0, 0, -1).mul(new com.badlogic.gdx.math.Quaternion().setEulerAngles(yaw, pitch, 0)).nor();
	}

	/** Ready-made lighting moods. */
	public void applyPreset(String p) {
		preset = p == null ? "DAY" : p.toUpperCase();
		fogEnabled = false;
		sunEnabled = true;
		if ("SUNSET".equals(preset)) {
			ambient.set(0.42f, 0.33f, 0.36f, 1);
			sunColor.set(1f, 0.62f, 0.36f, 1);
			sunIntensity = 1.1f;
			setSunAngles(-14, 60);
			skyTop.set(0.25f, 0.33f, 0.62f, 1);
			skyBottom.set(1f, 0.6f, 0.38f, 1);
			fogEnabled = true;
			fogColor.set(0.95f, 0.62f, 0.45f, 1);
			fogNear = 50;
			fogFar = 220;
		} else if ("NIGHT".equals(preset)) {
			ambient.set(0.12f, 0.14f, 0.24f, 1);
			sunColor.set(0.45f, 0.55f, 0.85f, 1);
			sunIntensity = 0.35f;
			setSunAngles(-40, -30);
			skyTop.set(0.02f, 0.03f, 0.08f, 1);
			skyBottom.set(0.07f, 0.09f, 0.18f, 1);
			fogEnabled = true;
			fogColor.set(0.05f, 0.06f, 0.12f, 1);
			fogNear = 20;
			fogFar = 90;
		} else if ("OVERCAST".equals(preset)) {
			ambient.set(0.55f, 0.57f, 0.6f, 1);
			sunColor.set(0.7f, 0.72f, 0.75f, 1);
			sunIntensity = 0.55f;
			setSunAngles(-70, 20);
			skyTop.set(0.6f, 0.63f, 0.68f, 1);
			skyBottom.set(0.78f, 0.8f, 0.82f, 1);
			fogEnabled = true;
			fogColor.set(0.75f, 0.77f, 0.8f, 1);
			fogNear = 30;
			fogFar = 140;
		} else if ("STUDIO".equals(preset)) {
			ambient.set(0.55f, 0.55f, 0.58f, 1);
			sunColor.set(1f, 1f, 1f, 1);
			sunIntensity = 0.9f;
			setSunAngles(-45, 45);
			skyTop.set(0.22f, 0.23f, 0.26f, 1);
			skyBottom.set(0.36f, 0.37f, 0.4f, 1);
		} else if ("HORROR".equals(preset)) {
			ambient.set(0.2f, 0.21f, 0.26f, 1);
			sunColor.set(0.55f, 0.6f, 0.78f, 1);
			sunIntensity = 0.45f;
			setSunAngles(-35, -60);
			skyTop.set(0.08f, 0.09f, 0.14f, 1);
			skyBottom.set(0.2f, 0.21f, 0.27f, 1);
			fogEnabled = true;
			fogColor.set(0.16f, 0.17f, 0.22f, 1);
			fogNear = 12;
			fogFar = 60;
		} else if ("INDOOR".equals(preset)) {
			ambient.set(0.5f, 0.47f, 0.42f, 1);
			sunColor.set(1f, 0.92f, 0.8f, 1);
			sunIntensity = 0.5f;
			setSunAngles(-60, 30);
			skyTop.set(0.3f, 0.28f, 0.26f, 1);
			skyBottom.set(0.4f, 0.37f, 0.34f, 1);
		} else if ("CARTOON".equals(preset)) {
			ambient.set(0.6f, 0.62f, 0.68f, 1);
			sunColor.set(1f, 0.98f, 0.92f, 1);
			sunIntensity = 0.9f;
			setSunAngles(-50, 30);
			skyTop.set(0.3f, 0.6f, 1f, 1);
			skyBottom.set(0.75f, 0.9f, 1f, 1);
		} else if (!"CUSTOM".equals(preset)) { // DAY
			ambient.set(0.45f, 0.47f, 0.52f, 1);
			sunColor.set(1f, 0.96f, 0.88f, 1);
			sunIntensity = 1f;
			setSunAngles(-55, 35);
			skyTop.set(0.36f, 0.62f, 0.95f, 1);
			skyBottom.set(0.78f, 0.88f, 0.98f, 1);
			fogColor.set(0.78f, 0.86f, 0.95f, 1);
			fogNear = 60;
			fogFar = 260;
		}
		ambientIntensity = 1f;
	}

	// ------------------------------------------------------------------ frame

	private boolean playing = true;

	/** Logic of a frame (physics, animations, cameras). The scene calls it before {@link #render()}. */
	public void update(float dt, boolean playing) {
		this.playing = playing;
		time += dt;
		if (playing && physics != null) {
			buoyancy(dt);
			physics.step(dt);
		}
		for (int i = 0; i < objects.size; i++) {
			Object3D o = objects.get(i);
			if (playing || editorAnimate) o.updateAnimation(dt);
		}
		int w = Math.max(1, Gdx.graphics.getWidth()), h = Math.max(1, Gdx.graphics.getHeight());
		if (activeCamera != null && !editor) {
			activeCamera.resize(w, h);
			activeCamera.frame(dt);
			camera = activeCamera.cam;
		} else if (!editorCameraLocked) {
			defaultCamera.viewportWidth = w;
			defaultCamera.viewportHeight = h;
			defaultCamera.update();
			camera = defaultCamera;
		}
		for (int i = 0; i < cameras.size; i++) {
			Camera3D c = cameras.get(i);
			if (c != activeCamera && !editor) c.frame(dt);
		}
		for (int i = 0; i < lights.size; i++) lights.get(i).frame(dt);
	}

	/** Editor: animations preview. */
	public boolean editorAnimate;
	/** Editor: camera set by the editor. */
	public boolean editorCameraLocked;

	private void buoyancy(float dt) {
		waters.clear();
		for (Object3D o : objects) if (o.water != null && o.isVisible()) waters.add(o);
		if (waters.size == 0) return;
		for (Object3D o : objects) {
			if (o.rigid == null || !"DYNAMIC".equals(o.def.Body)) continue;
			for (Object3D w : waters) {
				if (w.def.Buoyancy <= 0) continue;
				// inside the water's square?
				float hx = w.getSizeX() * 0.5f, hz = w.getSizeZ() * 0.5f;
				if (Math.abs(o.position.x - w.position.x) > hx || Math.abs(o.position.z - w.position.z) > hz) continue;
				float surface = w.heightAt(o.position.x, o.position.z);
				float half = o.getSizeY() * 0.5f;
				float bottom = o.position.y - half;
				float depth = surface - bottom;
				if (depth <= 0) continue;
				float sub = MathUtils.clamp(depth / Math.max(0.01f, half * 2), 0, 1);
				float mass = Math.max(0.001f, o.def.Mass);
				Vector3 v = o.rigid.getLinearVelocity();
				float lift = -physics.gravity.y * mass * sub * 1.6f * w.def.Buoyancy;
				o.applyForce(-v.x * mass * 0.8f * sub, lift - v.y * mass * 1.5f * sub, -v.z * mass * 0.8f * sub);
				o.rigid.setAngularVelocity(o.rigid.getAngularVelocity().scl(1f - Math.min(1f, 1.5f * dt * sub)));
				break;
			}
		}
	}

	// ------------------------------------------------------------------ render

	private void ensureBatches() {
		boolean pixel = Quality3D.pixelLighting;
		if (batch != null && renderMode.equals(batchMode) && pixel == batchPixel) return;
		if (batch != null) batch.dispose();
		if (Shaders3D.PBR.equals(renderMode)) {
			batch = new ModelBatch(Shaders3D.pbr());
			if (shadowBatch != null) shadowBatch.dispose();
			shadowBatch = new ModelBatch(new PBRDepthShaderProvider(PBRDepthShaderProvider.createDefaultConfig()));
			buildIbl();
		} else {
			batch = new ModelBatch(new Shaders3D.Provider(renderMode, pixel));
			if (shadowBatch != null) shadowBatch.dispose();
			shadowBatch = new ModelBatch(new DepthShaderProvider());
		}
		if (unlitBatch == null) unlitBatch = new ModelBatch(new Shaders3D.Provider(Shaders3D.UNLIT, false));
		if (outlineBatch == null) outlineBatch = new ModelBatch(new Shaders3D.Provider(Shaders3D.SIMPLE, false));
		batchMode = renderMode;
		batchPixel = pixel;
	}

	private void buildIbl() {
		if (iblDiffuse != null) return;
		try {
			DirectionalLight l = new DirectionalLight();
			l.direction.set(sunDir);
			l.color.set(sunColor);
			IBLBuilder ib = IBLBuilder.createOutdoor(l);
			com.badlogic.gdx.graphics.Cubemap diff = ib.buildIrradianceMap(64);
			com.badlogic.gdx.graphics.Cubemap spec = ib.buildRadianceMap(6);
			ib.dispose();
			iblDiffuse = PBRCubemapAttribute.createDiffuseEnv(diff);
			iblSpecular = PBRCubemapAttribute.createSpecularEnv(spec);
			brdfLut = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "IBL: " + t);
		}
	}

	private final Vector3 tmp = new Vector3(), tmp2 = new Vector3();

	private void buildEnvironment() {
		env.clear();
		float ai = ambientIntensity * (Shaders3D.UNLIT.equals(renderMode) ? 1 : 1);
		env.set(new ColorAttribute(ColorAttribute.AmbientLight, ambient.r * ai, ambient.g * ai, ambient.b * ai, 1f));
		if (fogEnabled) env.set(new ColorAttribute(ColorAttribute.Fog, fogColor));
		Shaders3D.fogNear = fogEnabled ? fogNear : 1e6f;
		Shaders3D.fogFar = fogEnabled ? fogFar : 1e7f;
		boolean pbr = Shaders3D.PBR.equals(renderMode);
		if (pbr && iblDiffuse != null) {
			env.set(iblDiffuse);
			env.set(iblSpecular);
			if (brdfLut != null) env.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLut));
			env.set(new PBRFloatAttribute(PBRFloatAttribute.ShadowBias, 1f / 255f));
		}
		DirectionalLightsAttribute dirs = new DirectionalLightsAttribute();
		PointLightsAttribute points = new PointLightsAttribute();
		SpotLightsAttribute spots = new SpotLightsAttribute();
		if (sunEnabled && sunIntensity > 0) {
			DirectionalLight d = shadowLight != null && useShadows() ? shadowLight : (sun == null ? (sun = new net.mgsx.gltf.scene3d.lights.DirectionalLightEx()) : sun);
			d.direction.set(sunDir);
			d.color.set(sunColor.r * sunIntensity, sunColor.g * sunIntensity, sunColor.b * sunIntensity, 1f);
			if (d instanceof net.mgsx.gltf.scene3d.lights.DirectionalLightEx) {
				((net.mgsx.gltf.scene3d.lights.DirectionalLightEx) d).intensity = pbr ? sunIntensity * 3f : 1f;
				d.color.set(sunColor.r * (pbr ? 1 : sunIntensity), sunColor.g * (pbr ? 1 : sunIntensity), sunColor.b * (pbr ? 1 : sunIntensity), 1f);
			}
			dirs.lights.add(d);
		}
		// lights: directional ones always, the nearest point / spot lights to the camera
		lightsTmp.clear();
		for (Light3D l : lights) {
			if (!l.on || !l.isVisible()) continue;
			float inten = l.frame(0);
			if (inten <= 0) continue;
			if ("DIRECTIONAL".equals(l.type)) {
				if (l.dir == null) l.dir = new net.mgsx.gltf.scene3d.lights.DirectionalLightEx();
				l.dir.direction.set(l.direction);
				l.dir.color.set(l.color.r * inten, l.color.g * inten, l.color.b * inten, 1f);
				l.dir.intensity = pbr ? inten * 3f : 1f;
				if (pbr) l.dir.color.set(l.color);
				if (dirs.lights.size < 2) dirs.lights.add(l.dir);
			} else lightsTmp.add(l);
		}
		final Vector3 cp = camera.position;
		lightsTmp.sort(new java.util.Comparator<Light3D>() {
			@Override
			public int compare(Light3D a, Light3D b) {
				return Float.compare(a.position.dst2(cp), b.position.dst2(cp));
			}
		});
		int maxPoint = Quality3D.pixelLighting ? 6 : 4, maxSpot = 3;
		for (Light3D l : lightsTmp) {
			float inten = l.frame(0);
			if ("SPOT".equals(l.type)) {
				if (spots.lights.size >= maxSpot) continue;
				if (l.spot == null) l.spot = new SpotLightEx();
				l.spot.position.set(l.position);
				l.spot.direction.set(l.direction);
				l.spot.cutoffAngle = l.cone;
				l.spot.exponent = 1f + (1f - l.softness) * 10f;
				l.spot.color.set(l.color);
				l.spot.intensity = pbr ? inten * 25f * l.range : inten * l.range * 1.2f;
				l.spot.range = l.range;
				spots.lights.add(l.spot);
			} else {
				if (points.lights.size >= maxPoint) continue;
				if (l.point == null) l.point = new PointLightEx();
				l.point.position.set(l.position);
				l.point.color.set(l.color);
				l.point.intensity = pbr ? inten * 25f * l.range : inten * l.range * 1.2f;
				l.point.range = l.range;
				points.lights.add(l.point);
			}
		}
		if (dirs.lights.size > 0) env.set(dirs);
		if (points.lights.size > 0) env.set(points);
		if (spots.lights.size > 0) env.set(spots);
		if (useShadows() && shadowLight != null) env.shadowMap = shadowLight;
		else env.shadowMap = null;
	}

	private final Array<Light3D> lightsTmp = new Array<Light3D>();

	private boolean useShadows() {
		return sunEnabled && sunShadows && Quality3D.shadowSize > 0 && !Shaders3D.UNLIT.equals(renderMode);
	}

	private void ensureShadow() {
		int size = useShadows() ? Quality3D.shadowSize : 0;
		if (size == shadowSizeNow) return;
		if (shadowLight != null) {
			shadowLight.dispose();
			shadowLight = null;
		}
		shadowSizeNow = size;
		if (size > 0) {
			float d = Math.max(10f, shadowDistance);
			shadowLight = new DirectionalShadowLight(size, size, d, d, 0.1f, d * 3f);
		}
	}

	/** Draws the world with its camera on the whole screen (or into the frame buffer for scale / post effects). */
	public void render() {
		render(camera, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
	}

	/**
	 * Draws the world with a camera into the current target (screen or a frame buffer of size w x h).
	 * main = the screen picture (render scale + post effects apply).
	 */
	public void render(Camera cam, int w, int h, boolean main) {
		drawing = this;
		ensureBatches();
		ensureShadow();
		if (!editor) Quality3D.apply();
		// visible elements
		float viewDist = Quality3D.viewDistance;
		visible.clear();
		for (int i = 0; i < objects.size; i++) {
			Object3D o = objects.get(i);
			o.sync();
			if (!o.isVisible() || o.instance == null) continue;
			o.center(tmp);
			float maxD = o.def.Draw_Distance > 0 ? o.def.Draw_Distance : (o.water != null || "TERRAIN".equals(o.def.Shape) ? viewDist * 4 : viewDist);
			float dist = tmp.dst(cam.position) - o.radius;
			if (dist > maxD && !editor) continue;
			if (!cam.frustum.sphereInFrustum(tmp, o.radius)) continue;
			visible.add(o);
		}
		buildEnvironment();
		// shadows (main picture only: the shadow pass binds its own frame buffer)
		if (main && useShadows() && shadowLight != null && visible.size > 0) {
			Vector3 center = tmp2.set(cam.direction).scl(shadowDistance * 0.4f).add(cam.position);
			shadowLight.setCenter(center);
			shadowLight.direction.set(sunDir);
			shadowLight.begin();
			shadowBatch.begin(shadowLight.getCamera());
			for (int i = 0; i < objects.size; i++) {
				Object3D o = objects.get(i);
				if (!o.isVisible() || o.instance == null || !o.def.Cast_Shadows || o.water != null || o.def.Opacity <= 0.5f) continue;
				if (o.position.dst(center) - o.radius > shadowDistance) continue;
				shadowBatch.render(o.instance);
			}
			shadowBatch.end();
			shadowLight.end();
		}
		boolean useFbo = main && !editor && (Quality3D.renderScale < 0.999f || (!"NONE".equals(postEffect) && Quality3D.postEffects));
		int fw = w, fh = h;
		if (useFbo) {
			fw = Math.max(16, (int) (w * Quality3D.renderScale));
			fh = Math.max(16, (int) (h * Quality3D.renderScale));
			if (fbo == null || fbo.getWidth() != fw || fbo.getHeight() != fh) {
				if (fbo != null) fbo.dispose();
				fbo = new FrameBuffer(Pixmap.Format.RGB888, fw, fh, true);
				fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
			}
			fbo.begin();
		}
		if (main) Gdx.gl.glViewport(0, 0, fw, fh);
		// sky
		Gdx.gl.glClearColor(skyBottom.r, skyBottom.g, skyBottom.b, 1f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		drawSky(cam, fw, fh);
		boolean unlitScene = Shaders3D.UNLIT.equals(renderMode);
		batch.begin(cam);
		for (Object3D o : visible) {
			ModelInstance mi = o.instance;
			if (o.lowInstance != null && o.def.LOD_Distance > 0 && tmp.set(o.position).dst(cam.position) > o.def.LOD_Distance * Quality3D.lodBias) mi = o.lowInstance;
			if (unlitScene || o.def.Unlit) continue;
			if (!o.def.Receive_Shadows && env.shadowMap != null) {
				com.badlogic.gdx.graphics.g3d.environment.ShadowMap sm = env.shadowMap;
				env.shadowMap = null;
				batch.render(mi, env);
				batch.flush();
				env.shadowMap = sm;
			} else batch.render(mi, env);
		}
		batch.end();
		// unlit elements
		unlitBatch.begin(cam);
		for (Object3D o : visible) if (unlitScene || o.def.Unlit) unlitBatch.render(o.instance);
		unlitBatch.end();
		// outlines (selection / Outline property)
		outlines.clear();
		for (Object3D o : visible) {
			if (o.selected) Outline3D.collect(o.instance, Color.WHITE, Math.max(0.02f, o.radius * 0.025f), outlines);
			else if (o.outline) Outline3D.collect(o.instance, o.outlineColor, o.outlineWidth, outlines);
		}
		if (outlines.size > 0) {
			outlineBatch.begin(cam);
			for (Renderable r : outlines) outlineBatch.render(r);
			outlineBatch.end();
			Outline3D.free(outlines);
		}
		if (useFbo) {
			fbo.end();
			if (post == null) post = new PostFx3D();
			Gdx.gl.glViewport(0, 0, w, h);
			post.draw(fbo.getColorBufferTexture(), Quality3D.postEffects ? postEffect : "NONE", postParams, w, h);
		}
		drawing = null;
	}

	private void drawSky(Camera cam, int w, int h) {
		if ("IMAGE".equals(skyMode) && skyImage.length() > 0) {
			if (!skyImage.equals(skyTextureFor)) {
				skyTextureFor = skyImage;
				skyTexture = Assets3D.texture(skyImage, false);
				skyDome = null;
			}
			if (skyTexture != null) {
				if (skyDome == null) {
					skyDome = new ModelInstance(Shapes3D.get(Shapes3D.SPHERE, 1, 0));
					for (com.badlogic.gdx.graphics.g3d.Material m : skyDome.materials) {
						m.clear();
						m.set(TextureAttribute.createDiffuse(skyTexture));
						m.set(IntAttribute.createCullFace(GL20.GL_FRONT));
						m.set(new DepthTestAttribute(0, false));
					}
				}
				float r = Math.min(cam.far * 0.9f, 400f) * 2f;
				skyDome.transform.setToTranslationAndScaling(cam.position.x, cam.position.y, cam.position.z, -r, r, r);
				ensureBatches();
				unlitBatch.begin(cam);
				unlitBatch.render(skyDome);
				unlitBatch.end();
				return;
			}
		}
		if ("COLOR".equals(skyMode)) return;
		// gradient: horizon color at the horizon line of the camera, top color above
		if (shapes == null) shapes = new ShapeRenderer();
		shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		float pitch = MathUtils.asin(MathUtils.clamp(cam.direction.y, -1, 1));
		float fov = cam instanceof PerspectiveCamera ? ((PerspectiveCamera) cam).fieldOfView * MathUtils.degreesToRadians : 1f;
		float horizon = 0.5f - pitch / Math.max(0.1f, fov);
		horizon = MathUtils.clamp(horizon, -1f, 2f) * h;
		if (horizon > 0) shapes.rect(0, 0, w, Math.min(h, horizon), skyBottom, skyBottom, skyBottom, skyBottom);
		float top = Math.max(0, horizon);
		if (top < h) {
			Color mid = tmpColor.set(skyBottom).lerp(skyTop, MathUtils.clamp((top - horizon) / h, 0, 1));
			shapes.rect(0, top, w, h - top, mid, mid, skyTop, skyTop);
		}
		shapes.end();
	}

	private final Color tmpColor = new Color();

	// ------------------------------------------------------------------ picking

	private final Ray ray = new Ray();
	private final BoundingBox wb = new BoundingBox();
	public final Vector3 pickPoint = new Vector3();

	/** Element under a screen point (pixels, y down), null = none. */
	public Object3D pick(float screenX, float screenY) {
		return pick(camera, screenX, screenY, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), null);
	}

	public Object3D pick(Camera cam, float sx, float sy, float vx, float vy, float vw, float vh, Object3D ignore) {
		Ray r = cam.getPickRay(sx, sy, vx, vy, vw, vh);
		return pickRay(r, ignore);
	}

	public Object3D pickRay(Ray r, Object3D ignore) {
		ray.set(r);
		Object3D best = null;
		float bestD = Float.MAX_VALUE;
		for (Object3D o : objects) {
			if (!o.isVisible() || o == ignore || o.instance == null && !editor) continue;
			wb.set(o.localBounds()).mul(o.transform);
			if (Intersector.intersectRayBounds(ray, wb, tmp)) {
				float d = tmp.dst2(ray.origin);
				// inside a big element (terrain, room): prefer smaller ones in front
				if (d < bestD) {
					bestD = d;
					best = o;
					pickPoint.set(tmp);
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ contacts

	private final PairSet touchingPairs = new PairSet();

	void contactBegin(Object3D a, Object3D b) {
		touchingPairs.add(a.getName(), b.getName());
		if (stage != null) stage.contact3D(a, b, true);
	}

	void contactEnd(Object3D a, Object3D b) {
		touchingPairs.remove(a.getName(), b.getName());
		if (stage != null) stage.contact3D(a, b, false);
	}

	public boolean touching(PlayerItem a, PlayerItem b) {
		return a != null && b != null && touchingPairs.has(a.getName(), b.getName());
	}

	static class PairSet {
		final ObjectSet<String> set = new ObjectSet<String>();

		void add(String a, String b) {
			set.add(a + "\u0001" + b);
			set.add(b + "\u0001" + a);
		}

		void remove(String a, String b) {
			set.remove(a + "\u0001" + b);
			set.remove(b + "\u0001" + a);
		}

		boolean has(String a, String b) {
			return set.contains(a + "\u0001" + b);
		}

		void removeIf(String name) {
			Array<String> rm = new Array<String>();
			for (String s : set) if (s.startsWith(name + "\u0001") || s.endsWith("\u0001" + name)) rm.add(s);
			for (String s : rm) set.remove(s);
		}
	}

	// ------------------------------------------------------------------ input

	private InputProcessor input;
	private final Object3D[] pressed = new Object3D[10];
	private final float[] downX = new float[10], downY = new float[10], lastX = new float[10], lastY = new float[10];
	private final boolean[] dragCam = new boolean[10];
	private float pinchStart = -1;

	/** Touches on the 3D elements (events onTouchStart / onTouchEnd / onClick) and the camera's touch look. */
	public InputProcessor input() {
		if (input != null) return input;
		input = new InputAdapter() {
			@Override
			public boolean touchDown(int x, int y, int pointer, int button) {
				if (pointer >= pressed.length) return false;
				downX[pointer] = lastX[pointer] = x;
				downY[pointer] = lastY[pointer] = y;
				Object3D o = pick(x, y);
				pressed[pointer] = o;
				dragCam[pointer] = true;
				if (o != null) {
					touchStart(o, x, y, pointer);
					dragCam[pointer] = activeCamera != null && activeCamera.touchLook;
				}
				lastTouchX = x;
				lastTouchY = y;
				touchingScreen = true;
				return o != null || (activeCamera != null && activeCamera.touchLook);
			}

			@Override
			public boolean touchDragged(int x, int y, int pointer) {
				if (pointer >= pressed.length) return false;
				float dx = x - lastX[pointer], dy = y - lastY[pointer];
				lastX[pointer] = x;
				lastY[pointer] = y;
				lastTouchX = x;
				lastTouchY = y;
				if (activeCamera != null && dragCam[pointer]) {
					if (Gdx.input.isTouched(1) && pointer <= 1) {
						float d = Vector3.dst(Gdx.input.getX(0), Gdx.input.getY(0), 0, Gdx.input.getX(1), Gdx.input.getY(1), 0);
						if (pinchStart > 0) activeCamera.pinch(pinchStart / Math.max(1f, d));
						pinchStart = d;
					} else {
						pinchStart = -1;
						activeCamera.drag(dx, dy);
					}
					return activeCamera.touchLook;
				}
				return false;
			}

			@Override
			public boolean touchUp(int x, int y, int pointer, int button) {
				if (pointer >= pressed.length) return false;
				Object3D o = pressed[pointer];
				pressed[pointer] = null;
				pinchStart = -1;
				touchingScreen = Gdx.input.isTouched();
				if (o != null) {
					touchEnd(o, x, y, pointer);
					if (Math.abs(x - downX[pointer]) < 20 && Math.abs(y - downY[pointer]) < 20 && pick(x, y) == o) click(o);
					return true;
				}
				return false;
			}
		};
		return input;
	}

	public float lastTouchX, lastTouchY;
	public boolean touchingScreen;

	private InputEvent event(float x, float y, int pointer, InputEvent.Type type) {
		InputEvent e = new InputEvent();
		e.setType(type);
		e.setStageX(x);
		e.setStageY(Gdx.graphics.getHeight() - y);
		e.setPointer(pointer);
		if (stage != null && stage.getGameStage() != null) e.setStage(stage.getGameStage());
		return e;
	}

	public void touchStart(Object3D o, float x, float y, int pointer) {
		InputEvent e = event(x, y, pointer, InputEvent.Type.touchDown);
		if (o.getScript() != null) o.getScript().touchStart(e);
		else if (o.elementEvent != null) o.elementEvent.onTouchStart(o, e);
	}

	public void touchEnd(Object3D o, float x, float y, int pointer) {
		InputEvent e = event(x, y, pointer, InputEvent.Type.touchUp);
		if (o.getScript() != null) o.getScript().touchEnd(e);
		else if (o.elementEvent != null) o.elementEvent.onTouchEnd(o, e);
	}

	public void click(Object3D o) {
		if (o.getScript() != null) o.getScript().click();
		else if (o.elementEvent != null) o.elementEvent.onClick(o);
	}

	// ------------------------------------------------------------------ helpers for blocks

	/** Screen position (pixels, y up like the UI) of a 3D point; z < 0 = behind the camera. */
	public Vector3 toScreen(float x, float y, float z) {
		Vector3 v = new Vector3(x, y, z);
		camera.project(v);
		return v;
	}

	public void dispose() {
		if (physics != null) physics.dispose();
		physics = null;
		if (batch != null) batch.dispose();
		if (unlitBatch != null) unlitBatch.dispose();
		if (shadowBatch != null) shadowBatch.dispose();
		if (outlineBatch != null) outlineBatch.dispose();
		if (shadowLight != null) shadowLight.dispose();
		if (shapes != null) shapes.dispose();
		if (fbo != null) fbo.dispose();
		if (post != null) post.dispose();
		if (brdfLut != null) brdfLut.dispose();
		batch = unlitBatch = shadowBatch = outlineBatch = null;
	}
}
