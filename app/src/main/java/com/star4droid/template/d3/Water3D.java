package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Water surface: a grid moved by three waves in the vertex shader (Gerstner-like), colored by the view angle
 * (shallow / deep / sky), with sun glints and foam on the crests. The same waves are computed on the CPU for floating
 * elements ({@link #heightAt}). Quality: LOW = flat animated color, MEDIUM = waves, HIGH = waves + foam + glints.
 */
public final class Water3D {
	private Water3D() {
	}

	/** Settings of one water element (shared by its renderables). */
	public static class WaterAttribute extends Attribute {
		public static final long Type = register("s2dWater");
		public final Color shallow = new Color(0.18f, 0.56f, 0.85f, 1f), deep = new Color(0.04f, 0.24f, 0.42f, 1f), foam = new Color(Color.WHITE);
		public float height = 0.25f, speed = 1f, length = 8f, direction = 30f, clarity = 0.6f, foamAmount = 0.5f;
		/** 0 low, 1 medium, 2 high; -1 = follow Quality3D.waterLevel. */
		public int quality = -1;

		public WaterAttribute() {
			super(Type);
		}

		@Override
		public Attribute copy() {
			WaterAttribute w = new WaterAttribute();
			w.shallow.set(shallow);
			w.deep.set(deep);
			w.foam.set(foam);
			w.height = height;
			w.speed = speed;
			w.length = length;
			w.direction = direction;
			w.clarity = clarity;
			w.foamAmount = foamAmount;
			w.quality = quality;
			return w;
		}

		@Override
		public int compareTo(Attribute o) {
			return type == o.type ? 0 : (type < o.type ? -1 : 1);
		}

		public int level() {
			return quality < 0 ? Quality3D.waterLevel : quality;
		}
	}

	private static final ObjectMap<Integer, Model> GRIDS = new ObjectMap<Integer, Model>();

	/** A new GL context (new activity): the cached meshes / program belong to the old one. */
	static void forgetGL() {
		GRIDS.clear();
		WaterShader.program = null;
	}

	/** A 1 x 1 grid (scaled by the element) with res x res cells. */
	public static Model grid(int res) {
		res = MathUtils.clamp(res, 2, 160);
		Model m = GRIDS.get(res);
		if (m != null) return m;
		ModelBuilder mb = new ModelBuilder();
		mb.begin();
		MeshPartBuilder b = mb.part("water", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, new Material());
		// shared vertices (rect(VertexInfo...) adds 4 per cell: over 128 x 128 the builder throws "Too many vertices used")
		int n = res + 1;
		short[] idx = new short[n * n];
		MeshPartBuilder.VertexInfo vi = new MeshPartBuilder.VertexInfo();
		for (int j = 0; j <= res; j++)
			for (int i = 0; i <= res; i++)
				idx[j * n + i] = b.vertex(vi.setPos((float) i / res - 0.5f, 0, (float) j / res - 0.5f).setNor(0, 1, 0).setUV((float) i / res, (float) j / res));
		b.ensureRectangleIndices(res * res);
		for (int j = 0; j < res; j++)
			for (int i = 0; i < res; i++) b.rect(idx[(j + 1) * n + i], idx[(j + 1) * n + i + 1], idx[j * n + i + 1], idx[j * n + i]);
		m = mb.end();
		GRIDS.put(res, m);
		return m;
	}

	/** Height of the waves (meters, relative to the water's y) at a world point. Same formula as the shader. */
	public static float heightAt(WaterAttribute w, Matrix4 waterTransform, float x, float z, float time) {
		if (w.level() <= 0) return 0;
		float dir = w.direction * MathUtils.degreesToRadians;
		float h = 0;
		float[] dirs = {dir, dir + 0.9f, dir - 1.3f};
		float[] amps = {1f, 0.5f, 0.3f};
		float[] lens = {1f, 0.55f, 0.33f};
		for (int i = 0; i < 3; i++) {
			float k = MathUtils.PI2 / Math.max(0.5f, w.length * lens[i]);
			float d = x * MathUtils.cos(dirs[i]) + z * MathUtils.sin(dirs[i]);
			h += amps[i] * w.height * MathUtils.sin(k * d + time * w.speed * (1.5f + i * 0.6f));
		}
		return h / 1.8f;
	}

	public static class WaterShader implements Shader {
		private static ShaderProgram program;
		private RenderContext context;
		private Camera camera;

		public WaterShader(Renderable r) {
		}

		static final String V = "attribute vec3 a_position;\nattribute vec2 a_texCoord0;\n"
				+ "uniform mat4 u_projViewTrans;\nuniform mat4 u_worldTrans;\nuniform float u_time;\nuniform vec4 u_wave; // height, speed, length, direction\nuniform float u_level;\n"
				+ "varying vec3 v_world;\nvarying vec3 v_normal;\nvarying float v_crest;\nvarying vec2 v_uv;\n"
				+ "float wave(vec2 p, float dir, float amp, float len, float spd){ float k = 6.2831853 / max(0.5, u_wave.z * len); float d = dot(p, vec2(cos(dir), sin(dir))); return amp * u_wave.x * sin(k * d + u_time * u_wave.y * spd); }\n"
				+ "float height(vec2 p){ if (u_level < 0.5) return 0.0; float dir = u_wave.w; return (wave(p, dir, 1.0, 1.0, 1.5) + wave(p, dir + 0.9, 0.5, 0.55, 2.1) + wave(p, dir - 1.3, 0.3, 0.33, 2.7)) / 1.8; }\n"
				+ "void main(){ vec4 wp = u_worldTrans * vec4(a_position, 1.0); float h = height(wp.xz); float e = 0.15; float hx = height(wp.xz + vec2(e, 0.0)) - h; float hz = height(wp.xz + vec2(0.0, e)) - h;"
				+ " v_normal = normalize(vec3(-hx / e, 1.0, -hz / e)); wp.y += h; v_world = wp.xyz; v_crest = clamp(h / max(u_wave.x, 0.001), 0.0, 1.0); v_uv = a_texCoord0; gl_Position = u_projViewTrans * wp; }";

		// highp when available: u_time / u_level are in both shaders and must have the same precision to link
		static final String F = UserShader.PRECISION
				+ "uniform vec4 u_shallow;\nuniform vec4 u_deep;\nuniform vec4 u_foam;\nuniform vec4 u_sky;\nuniform vec3 u_sunDir;\nuniform vec3 u_sunColor;\nuniform vec3 u_cameraPos;\nuniform float u_time;\nuniform float u_clarity;\nuniform float u_foamAmount;\nuniform float u_level;\nuniform vec3 u_fogColor;\nuniform vec2 u_fogRange;\n"
				+ "varying vec3 v_world;\nvarying vec3 v_normal;\nvarying float v_crest;\nvarying vec2 v_uv;\n"
				+ "float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }\n"
				+ "float noise(vec2 p){ vec2 i = floor(p); vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f); return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y); }\n"
				+ "void main(){ vec3 v = normalize(u_cameraPos - v_world); vec3 n = normalize(v_normal);"
				+ " if (u_level > 1.5) { float r = noise(v_world.xz * 1.7 + u_time * 0.6) - noise(v_world.xz * 2.3 - u_time * 0.5); n = normalize(n + vec3(r * 0.12, 0.0, -r * 0.12)); }"
				+ " float fres = pow(1.0 - max(dot(v, n), 0.0), 3.0);"
				+ " vec3 col = mix(u_deep.rgb, u_shallow.rgb, 0.35 + 0.65 * max(dot(v, n), 0.0));"
				+ " col = mix(col, u_sky.rgb, fres * 0.65);"
				+ " if (u_level > 0.5) { vec3 h = normalize(-u_sunDir + v); float spec = pow(max(dot(n, h), 0.0), 120.0); col += u_sunColor * spec * 1.4; }"
				+ " if (u_level > 1.5) { float fo = smoothstep(0.55, 1.0, v_crest) * u_foamAmount; fo *= 0.6 + 0.4 * noise(v_world.xz * 4.0 + u_time); col = mix(col, u_foam.rgb, clamp(fo, 0.0, 1.0)); }"
				+ " else if (u_level < 0.5) { col += 0.04 * sin(v_world.x * 1.3 + u_time) * sin(v_world.z * 1.1 + u_time * 0.8); }"
				+ " float d = length(u_cameraPos - v_world); float fog = clamp((d - u_fogRange.x) / max(u_fogRange.y - u_fogRange.x, 0.001), 0.0, 1.0); col = mix(col, u_fogColor, fog);"
				+ " float a = mix(1.0 - u_clarity * 0.7, 1.0, fres); gl_FragColor = vec4(col, a); }";

		@Override
		public void init() {
			if (program != null) return;
			ShaderProgram.pedantic = false;
			program = new ShaderProgram(V, F);
			if (!program.isCompiled()) throw new GdxRuntimeException("water shader: " + program.getLog());
		}

		@Override
		public int compareTo(Shader other) {
			return 0;
		}

		@Override
		public boolean canRender(Renderable r) {
			return r.material != null && r.material.has(WaterAttribute.Type);
		}

		@Override
		public void begin(Camera camera, RenderContext context) {
			this.camera = camera;
			this.context = context;
			program.bind();
			program.setUniformMatrix("u_projViewTrans", camera.combined);
			program.setUniformf("u_time", World3D.time);
			program.setUniformf("u_cameraPos", camera.position);
			World3D w = World3D.drawing;
			Vector3 sun = w == null ? new Vector3(-0.4f, -1f, -0.3f).nor() : w.sunDir;
			program.setUniformf("u_sunDir", sun);
			if (w != null) {
				program.setUniformf("u_sunColor", w.sunColor.r, w.sunColor.g, w.sunColor.b);
				program.setUniformf("u_sky", w.skyTop);
				program.setUniformf("u_fogColor", w.fogEnabled ? w.fogColor.r : 0, w.fogEnabled ? w.fogColor.g : 0, w.fogEnabled ? w.fogColor.b : 0);
				program.setUniformf("u_fogRange", w.fogEnabled ? w.fogNear : 1e6f, w.fogEnabled ? w.fogFar : 1e7f);
			} else {
				program.setUniformf("u_sunColor", 1, 1, 1);
				program.setUniformf("u_sky", 0.6f, 0.8f, 1f, 1f);
				program.setUniformf("u_fogColor", 0, 0, 0);
				program.setUniformf("u_fogRange", 1e6f, 1e7f);
			}
			context.setDepthTest(GL20.GL_LEQUAL);
			context.setDepthMask(false);
			context.setBlending(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
			context.setCullFace(0);
		}

		@Override
		public void render(Renderable r) {
			WaterAttribute w = (WaterAttribute) r.material.get(WaterAttribute.Type);
			program.setUniformMatrix("u_worldTrans", r.worldTransform);
			program.setUniformf("u_wave", w.height, w.speed, w.length, w.direction * MathUtils.degreesToRadians);
			program.setUniformf("u_shallow", w.shallow);
			program.setUniformf("u_deep", w.deep);
			program.setUniformf("u_foam", w.foam);
			program.setUniformf("u_clarity", w.clarity);
			program.setUniformf("u_foamAmount", w.foamAmount);
			program.setUniformf("u_level", (float) w.level());
			r.meshPart.render(program);
		}

		@Override
		public void end() {
			if (context != null) {
				context.setDepthMask(true);
				context.setCullFace(GL20.GL_BACK);
			}
		}

		@Override
		public void dispose() {
		}
	}
}
