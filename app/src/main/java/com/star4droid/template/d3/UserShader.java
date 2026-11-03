package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Shaders written by the user (project folder shaders/NAME.glsl) or picked from the built-in list ("builtin:glow"...).
 *
 * A shader file only contains a function, the engine writes the rest:
 * <pre>
 * vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos) {
 *     return color * vec4(lambert(normal), 1.0);
 * }
 * // optional, moves the vertices:
 * vec3 displace(vec3 pos, vec3 normal) { return pos + normal * sin(u_time) * 0.05; }
 * </pre>
 * Ready to use: u_time, u_color (element Color), u_texture + hasTexture(), u_lightDir, u_lightColor, u_ambient, u_cameraPos,
 * lambert(normal). Element "Shader Params" ("speed=2;tint=#ff0000") become uniforms u_speed (float) and u_tint (vec4),
 * also changeable from blocks.
 */
public class UserShader implements Shader {
	/** Material attribute telling the providers to draw this renderable with a program. */
	public static class Attr extends Attribute {
		public static final String Alias = "s2dUserShader";
		public static final long Type = register(Alias);
		public final Program program;

		public Attr(Program p) {
			super(Type);
			program = p;
		}

		@Override
		public Attribute copy() {
			return new Attr(program);
		}

		@Override
		public int compareTo(Attribute o) {
			if (type != o.type) return type < o.type ? -1 : 1;
			return program.name.compareTo(((Attr) o).program.name);
		}
	}

	/** A compiled snippet + the values of one element. */
	public static class Program {
		public final String name, snippet;
		public final ObjectMap<String, float[]> params = new ObjectMap<String, float[]>();
		ShaderProgram compiled;
		String error;

		Program(String name, String snippet) {
			this.name = name;
			this.snippet = snippet;
		}

		public void setParam(String key, String value) {
			if (key == null || key.length() == 0) return;
			params.put(key.trim(), parseValue(value));
		}

		public void setParams(String all) {
			if (all == null) return;
			for (String part : all.split(";")) {
				int eq = part.indexOf('=');
				if (eq > 0) setParam(part.substring(0, eq), part.substring(eq + 1).trim());
			}
		}

		public String error() {
			return error;
		}
	}

	static float[] parseValue(String v) {
		if (v == null) return new float[]{0};
		v = v.trim();
		if (v.startsWith("#")) {
			try {
				Color c = Color.valueOf(v);
				return new float[]{c.r, c.g, c.b, c.a};
			} catch (Exception e) {
				return new float[]{1, 1, 1, 1};
			}
		}
		if (v.contains(",")) {
			String[] p = v.split(",");
			float[] out = new float[Math.min(4, p.length)];
			for (int i = 0; i < out.length; i++) {
				try {
					out[i] = Float.parseFloat(p[i].trim());
				} catch (Exception ignored) {
				}
			}
			return out;
		}
		try {
			return new float[]{Float.parseFloat(v)};
		} catch (Exception e) {
			return new float[]{0};
		}
	}

	// ------------------------------------------------------------------ built-in shaders

	public static final String[] BUILTINS = {"glow", "hologram", "dissolve", "wave", "rim", "toon", "gradient", "pulse", "xray", "flag", "lava", "ghost"};

	public static String builtin(String n) {
		if ("glow".equals(n))
			return "vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ float p = 0.6 + 0.4 * sin(u_time * 3.0); return vec4(color.rgb * lambert(normal) + color.rgb * p * 0.8, color.a); }";
		if ("hologram".equals(n))
			return "vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ float l = step(0.5, fract(worldPos.y * 12.0 - u_time * 2.0)); vec3 v = normalize(u_cameraPos - worldPos); float rim = 1.0 - abs(dot(v, normal)); return vec4(vec3(0.3, 0.9, 1.0) * (0.4 + rim), 0.35 + 0.3 * l + rim * 0.4); }";
		if ("dissolve".equals(n))
			return "uniform float u_amount;\nfloat h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }\nvec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ float n = h(floor(uv * 40.0)); if (n < u_amount) discard; vec3 edge = n < u_amount + 0.05 ? vec3(1.0, 0.5, 0.1) : vec3(0.0); return vec4(color.rgb * lambert(normal) + edge, color.a); }";
		if ("wave".equals(n))
			return "vec3 displace(vec3 pos, vec3 normal){ return pos + normal * sin(pos.x * 4.0 + u_time * 3.0) * 0.06; }\nvec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ return vec4(color.rgb * lambert(normal), color.a); }";
		if ("rim".equals(n))
			return "vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ vec3 v = normalize(u_cameraPos - worldPos); float rim = pow(1.0 - max(dot(v, normal), 0.0), 3.0); return vec4(color.rgb * lambert(normal) + vec3(rim), color.a); }";
		if ("toon".equals(n))
			return "vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ float d = max(dot(normal, -u_lightDir), 0.0); d = d > 0.6 ? 1.0 : (d > 0.25 ? 0.6 : 0.3); return vec4(color.rgb * (u_ambient + u_lightColor * d), color.a); }";
		if ("gradient".equals(n))
			return "uniform vec4 u_top;\nvec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ vec3 top = u_top.a > 0.0 ? u_top.rgb : vec3(1.0); return vec4(mix(color.rgb, top, clamp(uv.y, 0.0, 1.0)) * lambert(normal), color.a); }";
		if ("pulse".equals(n))
			return "vec3 displace(vec3 pos, vec3 normal){ return pos * (1.0 + 0.06 * sin(u_time * 6.0)); }\nvec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ return vec4(color.rgb * lambert(normal), color.a); }";
		if ("xray".equals(n))
			return "vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ vec3 v = normalize(u_cameraPos - worldPos); float rim = 1.0 - abs(dot(v, normal)); return vec4(color.rgb * rim * 1.5, rim * 0.9); }";
		if ("flag".equals(n))
			return "vec3 displace(vec3 pos, vec3 normal){ float w = (pos.x + 0.5); return pos + vec3(0.0, 0.0, sin(pos.x * 6.0 + u_time * 4.0) * 0.08 * w); }\nvec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ return vec4(color.rgb * lambert(normal), color.a); }";
		if ("lava".equals(n))
			return "vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ float t = u_time * 0.4; float v = sin(worldPos.x * 1.7 + t) + sin(worldPos.z * 2.3 - t * 1.3) + sin((worldPos.x + worldPos.z) * 1.1 + t * 0.7); v = v / 3.0 * 0.5 + 0.5; return vec4(mix(vec3(0.6, 0.05, 0.0), vec3(1.0, 0.75, 0.1), v), 1.0); }";
		if ("ghost".equals(n))
			return "vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ float a = 0.35 + 0.15 * sin(u_time * 2.0 + worldPos.y * 3.0); return vec4(color.rgb * lambert(normal) + 0.15, a); }";
		return null;
	}

	private static final ObjectMap<String, String> SNIPPETS = new ObjectMap<String, String>();

	/** Program of a shader name ("builtin:x" or a project file "shaders/x" / "x"). null = not found. */
	public static Program program(String name, String params) {
		if (name == null || name.trim().length() == 0) return null;
		name = name.trim();
		String src = SNIPPETS.get(name);
		if (src == null) {
			if (name.startsWith("builtin:")) src = builtin(name.substring(8));
			else {
				String n = name.endsWith(".glsl") ? name : name + ".glsl";
				FileHandle f = Assets3D.file(n.startsWith("shaders/") ? n.substring(8) : n, "shaders");
				if (f != null) src = f.readString("UTF-8");
			}
			if (src == null) return null;
			SNIPPETS.put(name, src);
		}
		Program p = new Program(name, src);
		p.setParams(params);
		return p;
	}

	/** Shader code given directly (blocks "set shader code"). */
	public static Program fromCode(String id, String code, String params) {
		SNIPPETS.put(id, code);
		Program p = new Program(id, code);
		p.setParams(params);
		return p;
	}

	public static void forget(String name) {
		SNIPPETS.remove(name);
	}

	static final String PRECISION = "#ifdef GL_ES\n#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n#endif\n";

	static String vertexSource(Program p, boolean tex) {
		boolean disp = p.snippet.contains("displace(");
		StringBuilder sb = new StringBuilder();
		sb.append("attribute vec3 a_position;\nattribute vec3 a_normal;\n");
		if (tex) sb.append("attribute vec2 a_texCoord0;\n");
		sb.append("uniform mat4 u_projViewTrans;\nuniform mat4 u_worldTrans;\nuniform mat3 u_normalMatrix;\nuniform float u_time;\n");
		sb.append("varying vec2 v_uv;\nvarying vec3 v_normal;\nvarying vec3 v_worldPos;\n");
		if (disp) {
			// the user's displace() only sees the uniforms it declares + u_time
			sb.append(extractFunction(p.snippet, "displace")).append('\n');
		}
		sb.append("void main(){\n vec3 pos = a_position;\n");
		if (disp) sb.append(" pos = displace(pos, a_normal);\n");
		sb.append(" vec4 wp = u_worldTrans * vec4(pos, 1.0);\n v_worldPos = wp.xyz;\n v_normal = normalize(u_normalMatrix * a_normal);\n");
		sb.append(tex ? " v_uv = a_texCoord0;\n" : " v_uv = vec2(0.0);\n");
		sb.append(" gl_Position = u_projViewTrans * wp;\n}\n");
		return sb.toString();
	}

	static String fragmentSource(Program p) {
		StringBuilder sb = new StringBuilder();
		// highp when the GPU has it: u_time (and the uniforms of displace) are also in the vertex shader (highp there),
		// and a uniform declared in both shaders with two precisions makes the link fail on many phones
		sb.append(PRECISION);
		sb.append("uniform float u_time;\nuniform vec4 u_color;\nuniform sampler2D u_texture;\nuniform float u_hasTexture;\n");
		sb.append("uniform vec3 u_lightDir;\nuniform vec3 u_lightColor;\nuniform vec3 u_ambient;\nuniform vec3 u_cameraPos;\nuniform vec2 u_resolution;\n");
		sb.append("varying vec2 v_uv;\nvarying vec3 v_normal;\nvarying vec3 v_worldPos;\n");
		sb.append("bool hasTexture(){ return u_hasTexture > 0.5; }\n");
		sb.append("vec3 lambert(vec3 n){ return u_ambient + u_lightColor * max(dot(normalize(n), -u_lightDir), 0.0); }\n");
		for (ObjectMap.Entry<String, float[]> e : p.params) {
			String u = "u_" + e.key;
			if (p.snippet.contains("uniform") && p.snippet.matches("(?s).*uniform\\s+\\w+\\s+" + u + "\\s*;.*")) continue;
			int n = e.value.length;
			sb.append("uniform ").append(n == 1 ? "float" : (n == 2 ? "vec2" : (n == 3 ? "vec3" : "vec4"))).append(' ').append(u).append(";\n");
		}
		String body = p.snippet;
		if (body.contains("displace(")) body = removeFunction(body, "displace");
		sb.append(body).append('\n');
		sb.append("void main(){\n vec4 c = u_color;\n if (hasTexture()) c *= texture2D(u_texture, v_uv);\n gl_FragColor = effect(c, v_uv, normalize(v_normal), v_worldPos);\n}\n");
		return sb.toString();
	}

	/** Text of "type name(...) { ... }" (braces balanced), "" when missing. */
	static String extractFunction(String src, String name) {
		int i = src.indexOf(name + "(");
		if (i < 0) return "";
		int start = src.lastIndexOf('\n', i) + 1;
		int b = src.indexOf('{', i);
		if (b < 0) return "";
		int depth = 0;
		for (int k = b; k < src.length(); k++) {
			char ch = src.charAt(k);
			if (ch == '{') depth++;
			else if (ch == '}') {
				depth--;
				if (depth == 0) {
					// uniforms used by displace must be visible in the vertex shader too
					StringBuilder u = new StringBuilder();
					for (String line : src.split("\n")) if (line.trim().startsWith("uniform")) u.append(line.trim()).append('\n');
					return u + src.substring(start, k + 1);
				}
			}
		}
		return "";
	}

	static String removeFunction(String src, String name) {
		int i = src.indexOf(name + "(");
		if (i < 0) return src;
		int start = src.lastIndexOf('\n', i) + 1;
		int b = src.indexOf('{', i);
		int depth = 0;
		for (int k = b; k >= 0 && k < src.length(); k++) {
			char ch = src.charAt(k);
			if (ch == '{') depth++;
			else if (ch == '}') {
				depth--;
				if (depth == 0) return src.substring(0, start) + src.substring(k + 1);
			}
		}
		return src;
	}

	// ------------------------------------------------------------------ the shader

	final Program program;
	ShaderProgram sp;
	Camera camera;
	RenderContext context;
	private final Matrix3 normal = new Matrix3();
	private final boolean textured;

	public UserShader(Renderable r, Program p) {
		this.program = p;
		this.textured = r.meshPart.mesh.getVertexAttribute(VertexAttributes.Usage.TextureCoordinates) != null;
	}

	/** Compiles the program; false (and {@link Program#error}) when the code does not compile. */
	boolean compile() {
		if (sp != null) return true;
		ShaderProgram.pedantic = false;
		ShaderProgram p = new ShaderProgram(vertexSource(program, textured), fragmentSource(program));
		if (!p.isCompiled()) {
			program.error = p.getLog();
			Gdx.app.error("Star2D-3D", "shader " + program.name + ":\n" + p.getLog());
			p.dispose();
			return false;
		}
		program.error = null;
		sp = p;
		return true;
	}

	@Override
	public void init() {
		if (!compile()) throw new GdxRuntimeException("shader " + program.name + ": " + program.error);
	}

	@Override
	public int compareTo(Shader other) {
		return 0;
	}

	@Override
	public boolean canRender(Renderable r) {
		if (r.material == null || !r.material.has(Attr.Type)) return false;
		Attr a = (Attr) r.material.get(Attr.Type);
		boolean tex = r.meshPart.mesh.getVertexAttribute(VertexAttributes.Usage.TextureCoordinates) != null;
		return a.program.name.equals(program.name) && tex == textured && r.bones == null;
	}

	@Override
	public void begin(Camera camera, RenderContext context) {
		this.camera = camera;
		this.context = context;
		sp.bind();
		sp.setUniformMatrix("u_projViewTrans", camera.combined);
		sp.setUniformf("u_time", World3D.time);
		sp.setUniformf("u_cameraPos", camera.position);
		sp.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		World3D w = World3D.drawing;
		if (w != null) {
			sp.setUniformf("u_lightDir", w.sunDir);
			sp.setUniformf("u_lightColor", w.sunColor.r, w.sunColor.g, w.sunColor.b);
			sp.setUniformf("u_ambient", w.ambient.r, w.ambient.g, w.ambient.b);
		} else {
			sp.setUniformf("u_lightDir", -0.4f, -1f, -0.3f);
			sp.setUniformf("u_lightColor", 1f, 1f, 1f);
			sp.setUniformf("u_ambient", 0.4f, 0.4f, 0.4f);
		}
		context.setDepthTest(GL20.GL_LEQUAL);
		context.setDepthMask(true);
	}

	@Override
	public void render(Renderable r) {
		Attr a = (Attr) r.material.get(Attr.Type);
		Program p = a.program;
		sp.setUniformMatrix("u_worldTrans", r.worldTransform);
		sp.setUniformMatrix("u_normalMatrix", normal.set(r.worldTransform).inv().transpose());
		ColorAttribute ca = (ColorAttribute) r.material.get(ColorAttribute.Diffuse);
		Color c = ca == null ? Color.WHITE : ca.color;
		sp.setUniformf("u_color", c.r, c.g, c.b, c.a);
		TextureAttribute ta = (TextureAttribute) r.material.get(TextureAttribute.Diffuse);
		if (ta != null && ta.textureDescription.texture != null) {
			Texture t = ta.textureDescription.texture;
			sp.setUniformi("u_texture", context.textureBinder.bind(t));
			sp.setUniformf("u_hasTexture", 1f);
		} else sp.setUniformf("u_hasTexture", 0f);
		for (ObjectMap.Entry<String, float[]> e : p.params) {
			int loc = sp.fetchUniformLocation("u_" + e.key, false);
			if (loc < 0) continue;
			float[] v = e.value;
			if (v.length == 1) sp.setUniformf(loc, v[0]);
			else if (v.length == 2) sp.setUniformf(loc, v[0], v[1]);
			else if (v.length == 3) sp.setUniformf(loc, v[0], v[1], v[2]);
			else sp.setUniformf(loc, v[0], v[1], v[2], v[3]);
		}
		BlendingAttribute b = (BlendingAttribute) r.material.get(BlendingAttribute.Type);
		boolean blend = (b != null && b.blended) || c.a < 1f || p.snippet.contains("discard") || p.name.contains("holo") || p.name.contains("ghost") || p.name.contains("xray");
		if (blend) {
			context.setBlending(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
			context.setDepthMask(!(p.name.contains("holo") || p.name.contains("ghost") || p.name.contains("xray")));
		} else {
			context.setBlending(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
			context.setDepthMask(true);
		}
		context.setCullFace(GL20.GL_BACK);
		r.meshPart.render(sp);
	}

	@Override
	public void end() {
	}

	@Override
	public void dispose() {
		if (sp != null) sp.dispose();
	}
}
