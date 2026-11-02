package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Screen effects of a 3D scene (drawn on the 3D picture, under the 2D HUD): VIGNETTE, NIGHT_VISION, GRAYSCALE, SEPIA,
 * HORROR, CHROMATIC, PIXELATE, BLOOM_LITE, UNDERWATER, DREAM, or CUSTOM:code where code is
 * {@code vec4 post(vec4 color, vec2 uv)} (u_time, u_texture, u_resolution, u_amount available).
 */
public class PostFx3D implements Disposable {
	public static final String[] EFFECTS = {"NONE", "VIGNETTE", "NIGHT_VISION", "GRAYSCALE", "SEPIA", "HORROR", "CHROMATIC", "PIXELATE", "BLOOM_LITE", "UNDERWATER", "DREAM"};

	private final SpriteBatch batch = new SpriteBatch(1);
	private final ObjectMap<String, ShaderProgram> programs = new ObjectMap<String, ShaderProgram>();
	/** Strength 0..1 of the effect (blocks). */
	public static float amount = 1f;

	static final String V = "attribute vec4 a_position;\nattribute vec4 a_color;\nattribute vec2 a_texCoord0;\nuniform mat4 u_projTrans;\nvarying vec2 v_uv;\nvoid main(){ v_uv = a_texCoord0; gl_Position = u_projTrans * a_position; }";

	static String body(String effect) {
		if ("VIGNETTE".equals(effect))
			return "vec4 post(vec4 c, vec2 uv){ float d = distance(uv, vec2(0.5)); return vec4(c.rgb * mix(1.0, smoothstep(0.85, 0.3, d), u_amount), 1.0); }";
		if ("NIGHT_VISION".equals(effect))
			return "float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233)) + u_time) * 43758.5453); }\nvec4 post(vec4 c, vec2 uv){ float l = dot(c.rgb, vec3(0.3, 0.59, 0.11)) * 1.8 + h(uv * u_resolution) * 0.12; float v = smoothstep(0.75, 0.25, distance(uv, vec2(0.5))); vec3 g = vec3(0.15, 1.0, 0.25) * l * v; return vec4(mix(c.rgb, g, u_amount), 1.0); }";
		if ("GRAYSCALE".equals(effect))
			return "vec4 post(vec4 c, vec2 uv){ float l = dot(c.rgb, vec3(0.3, 0.59, 0.11)); return vec4(mix(c.rgb, vec3(l), u_amount), 1.0); }";
		if ("SEPIA".equals(effect))
			return "vec4 post(vec4 c, vec2 uv){ vec3 s = vec3(dot(c.rgb, vec3(0.393, 0.769, 0.189)), dot(c.rgb, vec3(0.349, 0.686, 0.168)), dot(c.rgb, vec3(0.272, 0.534, 0.131))); return vec4(mix(c.rgb, s, u_amount), 1.0); }";
		if ("HORROR".equals(effect))
			return "float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233)) + floor(u_time * 24.0)) * 43758.5453); }\nvec4 post(vec4 c, vec2 uv){ float l = dot(c.rgb, vec3(0.3, 0.59, 0.11)); vec3 d = mix(c.rgb, vec3(l) * vec3(0.9, 0.95, 1.05), 0.55 * u_amount); float v = smoothstep(0.9, 0.25, distance(uv, vec2(0.5))); float n = (h(uv * u_resolution) - 0.5) * 0.09 * u_amount; float f = 1.0 - 0.04 * u_amount * step(0.97, fract(sin(floor(u_time * 9.0)) * 43758.5)); return vec4((d * mix(1.0, v, u_amount) + n) * f, 1.0); }";
		if ("CHROMATIC".equals(effect))
			return "vec4 post(vec4 c, vec2 uv){ vec2 o = (uv - 0.5) * 0.008 * u_amount; return vec4(texture2D(u_texture, uv + o).r, c.g, texture2D(u_texture, uv - o).b, 1.0); }";
		if ("PIXELATE".equals(effect))
			return "vec4 post(vec4 c, vec2 uv){ float px = mix(1.0, 6.0, u_amount); vec2 s = px / u_resolution; return vec4(texture2D(u_texture, (floor(uv / s) + 0.5) * s).rgb, 1.0); }";
		if ("BLOOM_LITE".equals(effect))
			return "vec4 post(vec4 c, vec2 uv){ vec2 s = 2.5 / u_resolution; vec3 b = vec3(0.0); for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) { vec3 t = texture2D(u_texture, uv + vec2(float(i), float(j)) * s).rgb; b += max(t - 0.7, 0.0); } return vec4(c.rgb + b / 25.0 * 2.5 * u_amount, 1.0); }";
		if ("UNDERWATER".equals(effect))
			return "vec4 post(vec4 c, vec2 uv){ vec2 w = uv + vec2(sin(uv.y * 20.0 + u_time * 2.0), cos(uv.x * 18.0 + u_time * 1.7)) * 0.004 * u_amount; vec3 t = texture2D(u_texture, w).rgb; return vec4(mix(t, t * vec3(0.4, 0.75, 1.0) + vec3(0.0, 0.05, 0.1), u_amount), 1.0); }";
		if ("DREAM".equals(effect))
			return "vec4 post(vec4 c, vec2 uv){ vec2 s = 3.0 / u_resolution; vec3 b = (texture2D(u_texture, uv + s).rgb + texture2D(u_texture, uv - s).rgb + texture2D(u_texture, uv + vec2(s.x, -s.y)).rgb + texture2D(u_texture, uv - vec2(s.x, -s.y)).rgb) * 0.25; return vec4(mix(c.rgb, max(c.rgb, b) * 1.1 + 0.03, u_amount), 1.0); }";
		return "vec4 post(vec4 c, vec2 uv){ return c; }";
	}

	private ShaderProgram program(String effect) {
		ShaderProgram p = programs.get(effect);
		if (p != null) return p;
		String b = effect.startsWith("CUSTOM:") ? effect.substring(7) : body(effect);
		String f = "#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec2 v_uv;\nuniform sampler2D u_texture;\nuniform float u_time;\nuniform float u_amount;\nuniform vec2 u_resolution;\n"
				+ b + "\nvoid main(){ gl_FragColor = post(texture2D(u_texture, v_uv), v_uv); }";
		ShaderProgram.pedantic = false;
		p = new ShaderProgram(V, f);
		if (!p.isCompiled()) {
			Gdx.app.error("Star2D-3D", "post effect " + effect + ": " + p.getLog());
			p.dispose();
			p = null;
			if (!"NONE".equals(effect)) return program("NONE");
		}
		programs.put(effect, p);
		return p;
	}

	public void draw(Texture t, String effect, String params, int w, int h) {
		if (effect == null) effect = "NONE";
		ShaderProgram p = program(effect);
		batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
		batch.setShader(p);
		batch.begin();
		if (p != null) {
			p.setUniformf("u_time", World3D.time);
			p.setUniformf("u_amount", amount);
			p.setUniformf("u_resolution", w, h);
		}
		batch.draw(t, 0, 0, w, h, 0, 0, 1, 1);
		batch.end();
		batch.setShader(null);
	}

	@Override
	public void dispose() {
		batch.dispose();
		for (ShaderProgram p : programs.values()) if (p != null) p.dispose();
		programs.clear();
	}
}
