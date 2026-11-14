package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * 3D particle effects: camera facing (or motion stretched) sprites simulated in world space, drawn after the solid
 * elements with depth test and no depth writes. An effect is a .p3d file (JSON) of emitters:
 * <pre>
 * {"emitters":[{"img":"glow","blend":"add","rate":40,"life":[0.6,1],"shape":"disc","shapeSize":[0.4,0,0.4],
 *   "dir":[0,1,0],"spread":10,"speed":[1,2],"size":[0.3,0.5],"sizeCurve":[[0,0.5],[0.3,1],[1,0]],
 *   "tint":[[0,"#ffffff"],[0.3,"#ffa020"],[1,"#400000"]],"alpha":[[0,0],[0.1,1],[1,0]]}]}
 * </pre>
 * Fields (all optional): img (png name or list, next to the .p3d), blend add|alpha, duration (s), loop, delay, burst,
 * rate (per second), rateCurve (over the duration), max, life [min,max], shape point|sphere|box|disc|ring|line,
 * shapeSize [x,y,z] (sphere / disc / ring: radius in x and z), surface (on the edge only), offset [x,y,z],
 * dir [x,y,z], spread (degrees around dir, 180 = every direction), speed [min,max] m/s, speedCurve, outward
 * (away from the center; negative speed = toward it), gravity [x,y,z] m/s2 (world), drag, turbulence, orbit [min,max]
 * deg/s around the effect's up axis, size [min,max] m, sizeCurve, stretch (motion stretch, s), rot [min,max],
 * spin [min,max] deg/s, tint [[t,"#rrggbb"]...], alpha [[t,a]...], local (particles move with the element).
 * Curves are [[t,value]...] with t from 0 (birth) to 1 (death).
 */
public final class Particles3D {

	// ------------------------------------------------------------------ definition

	static final class Curve {
		final float[] t, v;

		Curve(float[] t, float[] v) {
			this.t = t;
			this.v = v;
		}

		float at(float x) {
			if (t.length == 0) return 1;
			if (x <= t[0]) return v[0];
			for (int i = 1; i < t.length; i++) {
				if (x <= t[i]) {
					float d = t[i] - t[i - 1];
					return d <= 0 ? v[i] : v[i - 1] + (v[i] - v[i - 1]) * (x - t[i - 1]) / d;
				}
			}
			return v[v.length - 1];
		}

		static final Curve ONE = new Curve(new float[]{0}, new float[]{1});
	}

	static final class Gradient {
		final float[] t, rgb;

		Gradient(float[] t, float[] rgb) {
			this.t = t;
			this.rgb = rgb;
		}

		void at(float x, Color out) {
			int n = t.length;
			if (n == 0) {
				out.set(1, 1, 1, 1);
				return;
			}
			if (x <= t[0] || n == 1) {
				out.set(rgb[0], rgb[1], rgb[2], 1);
				return;
			}
			for (int i = 1; i < n; i++) {
				if (x <= t[i]) {
					float d = t[i] - t[i - 1], k = d <= 0 ? 1 : (x - t[i - 1]) / d;
					int a = (i - 1) * 3, b = i * 3;
					out.set(rgb[a] + (rgb[b] - rgb[a]) * k, rgb[a + 1] + (rgb[b + 1] - rgb[a + 1]) * k, rgb[a + 2] + (rgb[b + 2] - rgb[a + 2]) * k, 1);
					return;
				}
			}
			int a = (n - 1) * 3;
			out.set(rgb[a], rgb[a + 1], rgb[a + 2], 1);
		}
	}

	/** One emitter of a .p3d file. */
	static final class Spec {
		Texture[] tex;
		float[] texAspect;
		boolean additive = true, loop = true, surface, outward, local;
		float duration = 1, delay, rate = 20, drag, turbulence, stretch;
		int burst, max = 200;
		float lifeMin = 1, lifeMax = 1, speedMin, speedMax, sizeMin = 0.2f, sizeMax = 0.2f, rotMin, rotMax, spinMin, spinMax, orbitMin, orbitMax, spread;
		String shape = "point";
		final Vector3 shapeSize = new Vector3(), offset = new Vector3(), dir = new Vector3(0, 1, 0), gravity = new Vector3();
		Curve rateCurve = Curve.ONE, speedCurve = Curve.ONE, sizeCurve = Curve.ONE, alpha = new Curve(new float[]{0, 0.1f, 0.7f, 1}, new float[]{0, 1, 1, 0});
		Gradient tint = new Gradient(new float[]{0}, new float[]{1, 1, 1});
	}

	/** A parsed .p3d file (cached by path). */
	static final class Effect {
		final Array<Spec> emitters = new Array<Spec>();
	}

	private static final ObjectMap<String, Effect> EFFECTS = new ObjectMap<String, Effect>();
	private static final ObjectMap<String, String> STAMPS = new ObjectMap<String, String>();
	private static final ObjectMap<String, Texture> TEXTURES = new ObjectMap<String, Texture>();

	/** Built in effect of an element without a file: a small campfire. */
	static final String DEFAULT = "{\"emitters\":["
			+ "{\"img\":\"@glow\",\"rate\":45,\"life\":[0.6,0.9],\"shape\":\"disc\",\"shapeSize\":[0.25,0,0.25],\"speed\":[0.8,1.3],\"spread\":8,"
			+ "\"size\":[0.45,0.6],\"sizeCurve\":[[0,0.5],[0.25,1],[1,0.15]],\"tint\":[[0,\"#fff6d0\"],[0.25,\"#ffb020\"],[0.65,\"#e03000\"],[1,\"#200800\"]],"
			+ "\"alpha\":[[0,0],[0.12,0.8],[0.6,0.6],[1,0]],\"turbulence\":0.6},"
			+ "{\"img\":\"@glow\",\"rate\":8,\"life\":[1.2,2],\"shape\":\"disc\",\"shapeSize\":[0.3,0,0.3],\"speed\":[1,1.8],\"spread\":20,\"size\":[0.04,0.07],"
			+ "\"tint\":[[0,\"#ffc040\"],[1,\"#ff3000\"]],\"alpha\":[[0,1],[0.6,1],[1,0]],\"turbulence\":1.5},"
			+ "{\"img\":\"@smoke\",\"blend\":\"alpha\",\"rate\":5,\"life\":[2,2.6],\"offset\":[0,0.7,0],\"shape\":\"disc\",\"shapeSize\":[0.2,0,0.2],\"speed\":[0.5,0.8],"
			+ "\"spread\":10,\"size\":[0.6,1],\"sizeCurve\":[[0,0.3],[1,1]],\"rot\":[0,360],\"spin\":[-40,40],\"tint\":[[0,\"#303030\"]],\"alpha\":[[0,0],[0.25,0.35],[1,0]]}]}";

	/** The effect of a project file ("/fx3d/fire/campfire.p3d"; "" = built in), read again when the file changes. */
	static Effect effect(String path) {
		String key = path == null ? "" : path;
		FileHandle f = key.isEmpty() ? null : Assets3D.file(key, "files");
		String stamp = f == null ? "-" : f.lastModified() + ":" + f.length();
		Effect e = EFFECTS.get(Assets3D.projectPath + "|" + key);
		if (e != null && stamp.equals(STAMPS.get(Assets3D.projectPath + "|" + key))) return e;
		e = new Effect();
		try {
			JsonValue root = new JsonReader().parse(f == null ? DEFAULT : f.readString("UTF-8"));
			JsonValue list = root.isArray() ? root : root.get("emitters");
			for (JsonValue j = list == null ? null : list.child; j != null; j = j.next) e.emitters.add(spec(j, f == null ? null : f.parent()));
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "particles " + key + ": " + t);
			if (f != null) {
				try {
					JsonValue root = new JsonReader().parse(DEFAULT);
					for (JsonValue j = root.get("emitters").child; j != null; j = j.next) e.emitters.add(spec(j, null));
				} catch (Throwable ignored) {
				}
			}
		}
		EFFECTS.put(Assets3D.projectPath + "|" + key, e);
		STAMPS.put(Assets3D.projectPath + "|" + key, stamp);
		return e;
	}

	private static Spec spec(JsonValue j, FileHandle folder) {
		Spec s = new Spec();
		JsonValue img = j.get("img");
		Array<String> names = new Array<String>();
		if (img == null) names.add("@glow");
		else if (img.isArray()) for (JsonValue v = img.child; v != null; v = v.next) names.add(v.asString());
		else names.add(img.asString());
		s.tex = new Texture[names.size];
		s.texAspect = new float[names.size];
		for (int i = 0; i < names.size; i++) {
			s.tex[i] = texture(names.get(i), folder);
			s.texAspect[i] = s.tex[i] == null ? 1 : s.tex[i].getHeight() / (float) s.tex[i].getWidth();
		}
		s.additive = !"alpha".equalsIgnoreCase(j.getString("blend", "add"));
		s.duration = Math.max(0.05f, j.getFloat("duration", 1));
		s.loop = j.getBoolean("loop", true);
		s.delay = j.getFloat("delay", 0);
		s.burst = j.getInt("burst", 0);
		s.rate = j.getFloat("rate", s.burst > 0 ? 0 : 20);
		s.max = Math.max(1, Math.min(2000, j.getInt("max", Math.max(s.burst, 200))));
		float[] r = range(j, "life", 1, 1);
		s.lifeMin = Math.max(0.02f, r[0]);
		s.lifeMax = Math.max(s.lifeMin, r[1]);
		s.shape = j.getString("shape", "point").toLowerCase();
		vec(j, "shapeSize", s.shapeSize);
		vec(j, "offset", s.offset);
		if (j.has("dir")) vec(j, "dir", s.dir);
		if (s.dir.isZero()) s.dir.set(0, 1, 0);
		s.dir.nor();
		s.spread = j.getFloat("spread", 0);
		r = range(j, "speed", 0, 0);
		s.speedMin = r[0];
		s.speedMax = r[1];
		s.outward = j.getBoolean("outward", false);
		s.surface = j.getBoolean("surface", false);
		s.local = j.getBoolean("local", false);
		vec(j, "gravity", s.gravity);
		s.drag = j.getFloat("drag", 0);
		s.turbulence = j.getFloat("turbulence", 0);
		s.stretch = j.getFloat("stretch", 0);
		r = range(j, "orbit", 0, 0);
		s.orbitMin = r[0];
		s.orbitMax = r[1];
		r = range(j, "size", 0.2f, 0.2f);
		s.sizeMin = r[0];
		s.sizeMax = r[1];
		r = range(j, "rot", 0, 0);
		s.rotMin = r[0];
		s.rotMax = r[1];
		r = range(j, "spin", 0, 0);
		s.spinMin = r[0];
		s.spinMax = r[1];
		s.rateCurve = curve(j.get("rateCurve"), Curve.ONE);
		s.speedCurve = curve(j.get("speedCurve"), Curve.ONE);
		s.sizeCurve = curve(j.get("sizeCurve"), Curve.ONE);
		s.alpha = curve(j.get("alpha"), s.alpha);
		JsonValue tint = j.get("tint");
		if (tint != null) {
			if (tint.isString()) {
				Color c = hex(tint.asString());
				s.tint = new Gradient(new float[]{0}, new float[]{c.r, c.g, c.b});
			} else {
				float[] t = new float[tint.size], rgb = new float[tint.size * 3];
				int i = 0;
				for (JsonValue p = tint.child; p != null; p = p.next, i++) {
					t[i] = p.getFloat(0);
					Color c = hex(p.getString(1));
					rgb[i * 3] = c.r;
					rgb[i * 3 + 1] = c.g;
					rgb[i * 3 + 2] = c.b;
				}
				s.tint = new Gradient(t, rgb);
			}
		}
		return s;
	}

	/** "#rrggbb" / "rrggbb" / "#rrggbbaa" (white when it can't be read). */
	static Color hex(String s) {
		try {
			String h = s.trim();
			if (h.startsWith("#")) h = h.substring(1);
			if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
			if (h.length() == 6) h += "ff";
			return Color.valueOf(h);
		} catch (Throwable t) {
			return new Color(Color.WHITE);
		}
	}

	private static float[] range(JsonValue j, String name, float a, float b) {
		JsonValue v = j.get(name);
		if (v == null) return new float[]{a, b};
		if (v.isArray()) return new float[]{v.getFloat(0), v.size > 1 ? v.getFloat(1) : v.getFloat(0)};
		float f = v.asFloat();
		return new float[]{f, f};
	}

	private static void vec(JsonValue j, String name, Vector3 out) {
		JsonValue v = j.get(name);
		if (v == null) return;
		if (v.isArray()) out.set(v.size > 0 ? v.getFloat(0) : 0, v.size > 1 ? v.getFloat(1) : 0, v.size > 2 ? v.getFloat(2) : 0);
		else out.set(v.asFloat(), v.asFloat(), v.asFloat());
	}

	private static Curve curve(JsonValue v, Curve def) {
		if (v == null || !v.isArray() || v.size == 0) return def;
		float[] t = new float[v.size], val = new float[v.size];
		int i = 0;
		for (JsonValue p = v.child; p != null; p = p.next, i++) {
			t[i] = p.getFloat(0);
			val[i] = p.getFloat(1);
		}
		return new Curve(t, val);
	}

	/** Picture of an emitter: next to the .p3d file, then the project's images, then a built in soft dot (@glow, @smoke). */
	private static Texture texture(String name, FileHandle folder) {
		String n = name.endsWith(".png") || name.endsWith(".jpg") ? name : name + ".png";
		FileHandle f = null;
		if (!name.startsWith("@")) {
			if (folder != null && folder.child(n).exists()) f = folder.child(n);
			else f = Assets3D.file(n, "images");
		}
		String key = f == null ? "@" + (name.contains("smoke") ? "smoke" : "glow") : f.path() + ":" + f.lastModified();
		Texture t = TEXTURES.get(key);
		if (t != null) return t;
		try {
			if (f != null) t = new Texture(f, true);
			else t = new Texture(builtIn(key.equals("@smoke")), true);
			t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
			TEXTURES.put(key, t);
		} catch (Throwable e) {
			Gdx.app.error("Star2D-3D", "particle image " + n + ": " + e);
			if (f != null) return texture("@glow", null);
		}
		return t;
	}

	private static com.badlogic.gdx.graphics.Pixmap builtIn(boolean smoke) {
		int s = 64;
		com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(s, s, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
		java.util.Random rnd = new java.util.Random(7);
		for (int y = 0; y < s; y++) {
			for (int x = 0; x < s; x++) {
				float dx = (x + 0.5f) / s * 2 - 1, dy = (y + 0.5f) / s * 2 - 1;
				float r = (float) Math.sqrt(dx * dx + dy * dy), a = Math.max(0, 1 - r);
				a = smoke ? (float) Math.pow(a, 1.3) * (0.6f + 0.4f * rnd.nextFloat()) : a * a;
				p.drawPixel(x, y, Color.rgba8888(1, 1, 1, Math.min(1, a)));
			}
		}
		return p;
	}

	// ------------------------------------------------------------------ instance

	private static final class P {
		float x, y, z, vx, vy, vz, age, life, size, rot, spin, orbit, speed;
		int tex;
		float depth;
	}

	private static final class Emitter {
		final Spec s;
		final P[] p;
		int count;
		float time, acc, delayLeft;
		boolean first = true, done;

		Emitter(Spec s) {
			this.s = s;
			p = new P[s.max];
			for (int i = 0; i < p.length; i++) p[i] = new P();
			delayLeft = s.delay;
		}
	}

	private Effect effect;
	private String file = "\u0000";
	private Emitter[] emitters = new Emitter[0];
	/** Scale of the whole effect (sizes, speeds, spawn area, gravity) and time speed. */
	public float scale = 1, speed = 1;
	/** false: no new particles (the ones alive finish their life). */
	public boolean emitting = true;
	private long frame = -1;
	private final Vector3 origin = new Vector3(), lastOrigin = new Vector3();
	private final Quaternion rotation = new Quaternion();

	/** Sets the .p3d file ("" = built in campfire); same file = no change. */
	public void setFile(String path) {
		String p = path == null ? "" : path;
		Effect e = effect(p);
		if (p.equals(file) && e == effect) return;
		file = p;
		effect = e;
		emitters = new Emitter[e.emitters.size];
		for (int i = 0; i < emitters.length; i++) emitters[i] = new Emitter(e.emitters.get(i));
	}

	/** Starts the effect again from its beginning (bursts play again). */
	public void restart() {
		for (int i = 0; i < emitters.length; i++) emitters[i] = new Emitter(emitters[i].s);
	}

	/** Every emitter finished (no loop) and no particle is alive. */
	public boolean isComplete() {
		for (Emitter e : emitters) if (!e.done || e.count > 0) return false;
		return true;
	}

	public int particleCount() {
		int n = 0;
		for (Emitter e : emitters) n += e.count;
		return n;
	}

	private final Vector3 t1 = new Vector3(), t2 = new Vector3(), t3 = new Vector3();

	/** Advances the effect once per frame (several cameras drawing the same frame don't speed it up). */
	void update(Vector3 pos, Quaternion rot, float elementScale) {
		long f = Gdx.graphics.getFrameId();
		if (f == frame) return;
		boolean firstFrame = frame < 0;
		frame = f;
		if (effect != null && effect != effect(file)) {
			String keep = file;
			file = "\u0000";
			setFile(keep);
		}
		lastOrigin.set(firstFrame ? pos : origin);
		origin.set(pos);
		rotation.set(rot);
		float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f) * speed;
		if (dt <= 0) return;
		float sc = scale * elementScale;
		for (Emitter e : emitters) step(e, dt, sc);
	}

	private void step(Emitter e, float dt, float sc) {
		Spec s = e.s;
		if (e.delayLeft > 0) {
			e.delayLeft -= dt;
			return;
		}
		if (!e.done) {
			if (e.first) {
				e.first = false;
				if (emitting) spawn(e, s.burst, sc, 0);
			}
			e.time += dt;
			if (e.time >= s.duration) {
				if (s.loop) {
					e.time -= s.duration;
					if (s.burst > 0 && emitting) spawn(e, s.burst, sc, 0);
				} else e.done = true;
			}
			if (!e.done && emitting && s.rate > 0) {
				e.acc += s.rate * s.rateCurve.at(e.time / s.duration) * Quality3D.effects * dt;
				int n = (int) e.acc;
				e.acc -= n;
				spawn(e, n, sc, dt);
			}
		}
		float dragK = s.drag > 0 ? Math.max(0, 1 - s.drag * dt) : 1;
		float gx = s.gravity.x * sc * dt, gy = s.gravity.y * sc * dt, gz = s.gravity.z * sc * dt;
		float turb = s.turbulence * sc * dt;
		boolean variable = s.speedCurve != Curve.ONE;
		for (int i = 0; i < e.count; ) {
			P p = e.p[i];
			p.age += dt;
			if (p.age >= p.life) {
				e.count--;
				e.p[i] = e.p[e.count];
				e.p[e.count] = p;
				continue;
			}
			float k = p.age / p.life;
			if (variable) {
				float len = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
				if (len > 1e-5f) {
					float want = p.speed * s.speedCurve.at(k) / len;
					p.vx *= want;
					p.vy *= want;
					p.vz *= want;
				}
			}
			p.vx = p.vx * dragK + gx;
			p.vy = p.vy * dragK + gy;
			p.vz = p.vz * dragK + gz;
			if (turb > 0) {
				p.vx += MathUtils.random(-turb, turb);
				p.vy += MathUtils.random(-turb, turb) * 0.5f;
				p.vz += MathUtils.random(-turb, turb);
			}
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.z += p.vz * dt;
			if (p.orbit != 0) {
				// turn around the effect's up axis through its origin
				t1.set(0, 1, 0).mul(rotation);
				float a = p.orbit * dt;
				t2.set(p.x - origin.x, p.y - origin.y, p.z - origin.z).rotate(t1, a);
				p.x = origin.x + t2.x;
				p.y = origin.y + t2.y;
				p.z = origin.z + t2.z;
				t3.set(p.vx, p.vy, p.vz).rotate(t1, a);
				p.vx = t3.x;
				p.vy = t3.y;
				p.vz = t3.z;
			}
			if (s.local) {
				p.x += origin.x - lastOrigin.x;
				p.y += origin.y - lastOrigin.y;
				p.z += origin.z - lastOrigin.z;
			}
			p.rot += p.spin * dt;
			i++;
		}
	}

	private void spawn(Emitter e, int n, float sc, float dt) {
		Spec s = e.s;
		for (int i = 0; i < n && e.count < s.max; i++) {
			P p = e.p[e.count++];
			p.age = 0;
			p.life = MathUtils.random(s.lifeMin, s.lifeMax);
			p.size = MathUtils.random(s.sizeMin, s.sizeMax) * sc;
			p.rot = MathUtils.random(s.rotMin, s.rotMax);
			p.spin = MathUtils.random(s.spinMin, s.spinMax);
			p.orbit = MathUtils.random(s.orbitMin, s.orbitMax);
			p.tex = s.tex.length > 1 ? MathUtils.random(s.tex.length - 1) : 0;
			// local spawn point
			float lx = 0, ly = 0, lz = 0, sx = s.shapeSize.x, sy = s.shapeSize.y, sz = s.shapeSize.z;
			switch (s.shape) {
				case "box":
					lx = MathUtils.random(-sx, sx) * 0.5f;
					ly = MathUtils.random(-sy, sy) * 0.5f;
					lz = MathUtils.random(-sz, sz) * 0.5f;
					break;
				case "line":
					float k = MathUtils.random(-0.5f, 0.5f);
					lx = sx * k;
					ly = sy * k;
					lz = sz * k;
					break;
				case "sphere": {
					t1.setToRandomDirection();
					float r = s.surface ? 1 : (float) Math.cbrt(MathUtils.random());
					lx = t1.x * sx * r;
					ly = t1.y * (sy > 0 ? sy : sx) * r;
					lz = t1.z * (sz > 0 ? sz : sx) * r;
					break;
				}
				case "disc":
				case "ring": {
					float a = MathUtils.random(MathUtils.PI2);
					float r = "ring".equals(s.shape) || s.surface ? 1 : (float) Math.sqrt(MathUtils.random());
					lx = MathUtils.cos(a) * sx * r;
					lz = MathUtils.sin(a) * (sz > 0 ? sz : sx) * r;
					ly = sy > 0 ? MathUtils.random(-sy, sy) * 0.5f : 0;
					break;
				}
			}
			lx = (lx + s.offset.x) * sc;
			ly = (ly + s.offset.y) * sc;
			lz = (lz + s.offset.z) * sc;
			t1.set(lx, ly, lz).mul(rotation);
			// spawn along the path the element moved this frame: a moving emitter leaves a smooth trail
			float along = dt > 0 ? MathUtils.random() : 1;
			p.x = lastOrigin.x + (origin.x - lastOrigin.x) * along + t1.x;
			p.y = lastOrigin.y + (origin.y - lastOrigin.y) * along + t1.y;
			p.z = lastOrigin.z + (origin.z - lastOrigin.z) * along + t1.z;
			// direction
			if (s.outward) {
				t2.set(lx - s.offset.x * sc, ly - s.offset.y * sc, lz - s.offset.z * sc);
				if (t2.isZero(1e-6f)) t2.setToRandomDirection();
				t2.nor().mul(rotation);
			} else {
				randomInCone(s.dir, s.spread, t2);
				t2.mul(rotation);
			}
			p.speed = MathUtils.random(s.speedMin, s.speedMax) * sc;
			float v = p.speed * s.speedCurve.at(0);
			p.vx = t2.x * v;
			p.vy = t2.y * v;
			p.vz = t2.z * v;
			if (s.local) {
				// nothing else: the particles get the element's motion while they live
			}
		}
	}

	private final Vector3 c1 = new Vector3(), c2 = new Vector3();

	/** A random unit vector at most spread degrees away from dir. */
	private void randomInCone(Vector3 dir, float spread, Vector3 out) {
		if (spread <= 0) {
			out.set(dir);
			return;
		}
		if (spread >= 180) {
			out.setToRandomDirection();
			return;
		}
		float cosMax = MathUtils.cosDeg(spread);
		float z = MathUtils.random(cosMax, 1f), phi = MathUtils.random(MathUtils.PI2), r = (float) Math.sqrt(Math.max(0, 1 - z * z));
		// basis around dir
		c1.set(Math.abs(dir.y) < 0.99f ? Vector3.Y : Vector3.X).crs(dir).nor();
		c2.set(dir).crs(c1).nor();
		out.set(dir).scl(z).mulAdd(c1, r * MathUtils.cos(phi)).mulAdd(c2, r * MathUtils.sin(phi)).nor();
	}

	// ------------------------------------------------------------------ drawing

	private static final int MAX_QUADS = 2048;
	private static Mesh mesh;
	private static ShaderProgram shader;
	private static float[] verts;
	private static boolean failed;
	private static final Color col = new Color();
	private static final Vector3 right = new Vector3(), up = new Vector3(), camPos = new Vector3(), ax = new Vector3(), side = new Vector3(), tv = new Vector3();

	private static final String VS = "attribute vec4 a_position;\nattribute vec4 a_color;\nattribute vec2 a_texCoord0;\nuniform mat4 u_proj;\n"
			+ "varying vec4 v_color;\nvarying vec2 v_uv;\nvoid main(){ v_color = a_color; v_uv = a_texCoord0; gl_Position = u_proj * a_position; }\n";
	private static final String FS = "#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec4 v_color;\nvarying vec2 v_uv;\nuniform sampler2D u_tex;\n"
			+ "void main(){ gl_FragColor = texture2D(u_tex, v_uv) * v_color; }\n";

	private static boolean ensureGl() {
		if (failed) return false;
		if (mesh != null) return true;
		try {
			ShaderProgram.pedantic = false;
			shader = new ShaderProgram(VS, FS);
			if (!shader.isCompiled()) throw new RuntimeException(shader.getLog());
			mesh = new Mesh(false, MAX_QUADS * 4, MAX_QUADS * 6, new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
					new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
					new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));
			short[] idx = new short[MAX_QUADS * 6];
			for (int i = 0, v = 0; i < idx.length; i += 6, v += 4) {
				idx[i] = (short) v;
				idx[i + 1] = (short) (v + 1);
				idx[i + 2] = (short) (v + 2);
				idx[i + 3] = (short) (v + 2);
				idx[i + 4] = (short) (v + 3);
				idx[i + 5] = (short) v;
			}
			mesh.setIndices(idx);
			verts = new float[MAX_QUADS * 4 * 6];
			return true;
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "particles shader: " + t);
			failed = true;
			return false;
		}
	}

	private static int quads;
	private static Texture bound;

	private static final Array<Object[]> order = new Array<Object[]>();
	private static final Array<P> sorted = new Array<P>();

	/** Draws the effects of the given elements (after the solid ones): alpha blended emitters far to near, then the additive ones. */
	static void renderAll(Camera cam, Array<Object3D> objects, boolean editor) {
		order.clear();
		for (int i = 0; i < objects.size; i++) {
			Object3D o = objects.get(i);
			if (o.particles == null || !o.isVisible()) continue;
			o.particles.update(o.position, o.rotation, (Math.abs(o.scale.x) + Math.abs(o.scale.y) + Math.abs(o.scale.z)) / 3f);
			if (o.particles.particleCount() == 0) continue;
			float maxD = o.def.Draw_Distance > 0 ? o.def.Draw_Distance : Quality3D.viewDistance;
			float d = o.position.dst(cam.position);
			if (d > maxD && !editor) continue;
			for (Emitter e : o.particles.emitters) if (e.count > 0) order.add(new Object[]{e, d});
		}
		if (order.size == 0 || !ensureGl()) return;
		order.sort((a, b) -> Float.compare((Float) b[1], (Float) a[1]));
		right.set(cam.direction).crs(cam.up).nor();
		up.set(right).crs(cam.direction).nor();
		camPos.set(cam.position);
		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
		Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
		Gdx.gl.glDepthMask(false);
		Gdx.gl.glDisable(GL20.GL_CULL_FACE);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shader.bind();
		shader.setUniformMatrix("u_proj", cam.combined);
		shader.setUniformi("u_tex", 0);
		bound = null;
		quads = 0;
		try {
			for (int pass = 0; pass < 2; pass++) {
				boolean additive = pass == 1;
				if (additive) Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
				else Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
				for (Object[] it : order) {
					Emitter e = (Emitter) it[0];
					if (e.s.additive != additive) continue;
					draw(e, additive);
				}
				flush();
			}
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "particles: " + t);
		}
		order.clear();
		Gdx.gl.glDepthMask(true);
		Gdx.gl.glDisable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
	}

	private static void draw(Emitter e, boolean additive) {
		Spec s = e.s;
		sorted.clear();
		for (int i = 0; i < e.count; i++) {
			P p = e.p[i];
			if (!additive) p.depth = tv.set(p.x, p.y, p.z).dst2(camPos);
			sorted.add(p);
		}
		if (!additive) sorted.sort((a, b) -> Float.compare(b.depth, a.depth));
		for (int i = 0; i < sorted.size; i++) {
			P p = sorted.get(i);
			Texture t = s.tex[p.tex];
			if (t == null) continue;
			if (t != bound) {
				flush();
				t.bind(0);
				bound = t;
			}
			float k = p.age / p.life;
			float a = s.alpha.at(k);
			if (a <= 0.003f) continue;
			s.tint.at(k, col);
			col.a = Math.min(1, a);
			float c = col.toFloatBits();
			float size = p.size * s.sizeCurve.at(k);
			if (size <= 0) continue;
			float hw, hh;
			if (s.stretch > 0) {
				float sp = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
				if (sp > 1e-4f) ax.set(p.vx / sp, p.vy / sp, p.vz / sp);
				else ax.set(up);
				side.set(ax).crs(tv.set(camPos).sub(p.x, p.y, p.z)).nor();
				hw = (size + sp * s.stretch) * 0.5f; // along the motion
				hh = size * 0.5f; // across the motion
				quad(p.x, p.y, p.z, ax, side, hw, hh, c);
			} else {
				hw = size * 0.5f;
				hh = hw * s.texAspect[p.tex];
				if (p.rot != 0) {
					float cs = MathUtils.cosDeg(p.rot), sn = MathUtils.sinDeg(p.rot);
					ax.set(right).scl(cs).mulAdd(up, sn);
					side.set(up).scl(cs).mulAdd(right, -sn);
				} else {
					ax.set(right);
					side.set(up);
				}
				quad(p.x, p.y, p.z, ax, side, hw, hh, c);
			}
		}
	}

	private static void quad(float x, float y, float z, Vector3 u, Vector3 v, float hu, float hv, float color) {
		if (quads >= MAX_QUADS) flush();
		int o = quads * 24;
		float ux = u.x * hu, uy = u.y * hu, uz = u.z * hu, vx = v.x * hv, vy = v.y * hv, vz = v.z * hv;
		put(o, x - ux - vx, y - uy - vy, z - uz - vz, color, 0, 1);
		put(o + 6, x + ux - vx, y + uy - vy, z + uz - vz, color, 1, 1);
		put(o + 12, x + ux + vx, y + uy + vy, z + uz + vz, color, 1, 0);
		put(o + 18, x - ux + vx, y - uy + vy, z - uz + vz, color, 0, 0);
		quads++;
	}

	private static void put(int o, float x, float y, float z, float c, float u, float v) {
		verts[o] = x;
		verts[o + 1] = y;
		verts[o + 2] = z;
		verts[o + 3] = c;
		verts[o + 4] = u;
		verts[o + 5] = v;
	}

	private static void flush() {
		if (quads == 0) return;
		mesh.setVertices(verts, 0, quads * 24);
		mesh.render(shader, GL20.GL_TRIANGLES, 0, quads * 6);
		quads = 0;
	}

	/** The GL context was lost / recreated: build the mesh and shader again on the next draw. */
	public static void invalidateGl() {
		mesh = null;
		shader = null;
		failed = false;
	}
}
