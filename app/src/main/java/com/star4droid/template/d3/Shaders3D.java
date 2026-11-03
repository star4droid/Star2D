package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;

import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;

/**
 * Shader providers of the render modes. SIMPLE (default) = libGDX default shader (per-vertex light, fastest),
 * TOON = the same shader with banded light, UNLIT = no light, PBR = gdx-gltf physically based shader.
 * Water and project shaders (Object3D "Shader") are picked per renderable in every mode.
 */
public final class Shaders3D {
	public static final String SIMPLE = "SIMPLE", TOON = "TOON", PBR = "PBR", UNLIT = "UNLIT", SOFT = "SOFT";
	public static final String[] MODES = {SIMPLE, SOFT, TOON, PBR, UNLIT};

	/** Fog range of the scene being drawn (meters), read by every shader of this class. */
	static float fogNear = 30, fogFar = 120;
	/** Bones of the skinned (animated) models the shaders are built for; a bigger skeleton gets its own shader. */
	public static final int NUM_BONES = 40;
	/** Number of toon bands (2..6). */
	public static float toonBands = 3;

	private static String vertexSrc, fragmentSimple, fragmentToon, fragmentSoft;

	private Shaders3D() {
	}

	private static void sources() {
		if (vertexSrc != null) return;
		String v = DefaultShader.getDefaultVertexShader();
		// fog from a near/far range (the default shader fades to the camera's far plane only)
		v = v.replace("float fog = dot(flen, flen) * u_cameraPosition.w;\n        v_fog = min(fog, 1.0);",
				"float fd = length(flen);\n        v_fog = clamp((fd - u_fogRange.x) / max(u_fogRange.y - u_fogRange.x, 0.001), 0.0, 1.0);");
		v = v.replace("uniform vec4 u_cameraPosition;", "uniform vec4 u_cameraPosition;\nuniform vec2 u_fogRange;");
		vertexSrc = v;
		String f = DefaultShader.getDefaultFragmentShader();
		fragmentSimple = f;
		String fn = "\nuniform float u_toonBands;\nvec3 toon(vec3 l){ float i = max(max(l.r, l.g), l.b); if (i <= 0.0) return l; float q = (floor(i * u_toonBands) + 0.5) / u_toonBands; return l * (min(q, 1.0) / i); }\n";
		String t = f.replace("void main()", fn + "void main()");
		t = t.replace("v_lightDiffuse)", "toon(v_lightDiffuse))").replace("* v_lightDiffuse", "* toon(v_lightDiffuse)");
		t = t.replace("(v_lightDiffuse +", "(toon(v_lightDiffuse) +");
		t = t.replace("* v_lightSpecular;", "* step(vec3(0.5), v_lightSpecular);").replace("= v_lightSpecular;", "= step(vec3(0.5), v_lightSpecular);");
		fragmentToon = t;
		// SOFT: a gentle wrap so the dark side is not black (friendly look, cheap)
		String sfn = "\nvec3 softL(vec3 l){ return l * 0.75 + vec3(0.18); }\n";
		String s = f.replace("void main()", sfn + "void main()");
		s = s.replace("v_lightDiffuse)", "softL(v_lightDiffuse))").replace("* v_lightDiffuse", "* softL(v_lightDiffuse)").replace("(v_lightDiffuse +", "(softL(v_lightDiffuse) +");
		fragmentSoft = s;
	}

	/** Default shader + fog range (+ toon / soft fragment). */
	public static class Lit extends DefaultShader {
		private final int uFog, uBands;

		public Lit(Renderable r, Config c) {
			super(r, c);
			uFog = register(new Uniform("u_fogRange"));
			uBands = register(new Uniform("u_toonBands"));
		}

		@Override
		public void begin(Camera camera, RenderContext context) {
			super.begin(camera, context);
			if (has(uFog)) set(uFog, fogNear, fogFar);
			if (has(uBands)) set(uBands, toonBands);
		}
	}

	public static DefaultShader.Config config(String mode, boolean pixel) {
		sources();
		DefaultShader.Config c = new DefaultShader.Config();
		c.numDirectionalLights = 2;
		c.numPointLights = pixel ? 6 : 4;
		c.numSpotLights = 3;
		c.numBones = NUM_BONES;
		c.vertexShader = vertexSrc;
		c.fragmentShader = TOON.equals(mode) ? fragmentToon : (SOFT.equals(mode) ? fragmentSoft : fragmentSimple);
		return c;
	}

	/** Provider of SIMPLE / TOON / SOFT / UNLIT. */
	public static class Provider extends DefaultShaderProvider {
		final String mode;

		public Provider(String mode, boolean pixel) {
			super(config(mode, pixel));
			this.mode = mode;
		}

		@Override
		protected Shader createShader(Renderable r) {
			Shader special = special(r);
			if (special != null) return special;
			DefaultShader.Config c = config;
			if (r.bones != null && r.bones.length > c.numBones) {
				// DefaultShader throws "too many bones" for a skeleton bigger than the config
				DefaultShader.Config big = config(mode, false);
				big.numPointLights = c.numPointLights;
				big.numBones = r.bones.length;
				c = big;
			}
			return new Lit(r, c);
		}
	}

	/** Shadow pass of SIMPLE / TOON / SOFT (the default depth config has 12 bones only). */
	public static class DepthProvider extends com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider {
		public DepthProvider() {
			super(depthConfig(NUM_BONES));
		}

		private static com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config depthConfig(int bones) {
			com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config c = new com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config();
			c.numBones = bones;
			return c;
		}

		@Override
		protected Shader createShader(Renderable r) {
			if (r.bones != null && r.bones.length > config.numBones)
				return new com.badlogic.gdx.graphics.g3d.shaders.DepthShader(r, depthConfig(r.bones.length));
			return super.createShader(r);
		}
	}

	/** Provider of PBR (gdx-gltf). */
	public static class PbrProvider extends PBRShaderProvider {
		public PbrProvider(PBRShaderConfig c) {
			super(c);
		}

		@Override
		protected Shader createShader(Renderable r) {
			Shader special = special(r);
			if (special != null) return special;
			return super.createShader(r);
		}
	}

	public static PbrProvider pbr() {
		PBRShaderConfig c = PBRShaderProvider.createDefaultConfig();
		c.numDirectionalLights = 2;
		c.numPointLights = 4;
		c.numSpotLights = 2;
		c.numBones = NUM_BONES;
		return new PbrProvider(c);
	}

	/** Water and project shaders. */
	static Shader special(Renderable r) {
		if (r.material == null) return null;
		if (r.material.has(Water3D.WaterAttribute.Type)) {
			Water3D.WaterShader s = new Water3D.WaterShader(r);
			return s;
		}
		if (r.material.has(UserShader.Attr.Type) && r.bones == null) {
			// (skinned meshes keep the normal shader: UserShader has no skinning and refuses them in canRender)
			UserShader.Attr a = (UserShader.Attr) r.material.get(UserShader.Attr.Type);
			try {
				UserShader us = new UserShader(r, a.program);
				// compiled now: a broken shader falls back to the normal one instead of throwing inside ModelBatch
				return us.compile() ? us : null;
			} catch (Throwable t) {
				Gdx.app.error("Star2D-3D", "shader " + a.program.name + ": " + t);
				return null;
			}
		}
		if (r.material.has(Outline3D.Attr.Type)) return new Outline3D.OutlineShader(r);
		return null;
	}
}
