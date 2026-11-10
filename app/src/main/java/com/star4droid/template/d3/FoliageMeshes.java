package com.star4droid.template.d3;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ShortArray;

/**
 * Low-poly stylized objects made in code for the terrain (no files): trees, bushes, rocks, flowers, mushrooms...
 * Flat shaded with vertex colors (one draw call per terrain chunk once merged), vertex alpha = how much it sways in
 * the wind (0 at the ground). Sizes are in meters at scale 1.
 */
public final class FoliageMeshes {
	/** Vertex: position (3), normal (3), color rgba (4). */
	public static final int STRIDE = 10;
	public static final String[] NAMES = {"tree_round", "tree_pine", "tree_autumn", "tree_birch", "tree_palm", "tree_dead", "bush", "bush_flowers",
			"rock", "rock_flat", "boulder", "flower_red", "flower_yellow", "flower_white", "flower_purple", "mushroom", "fern", "stump", "log", "reeds"};

	public static final class ProtoMesh {
		public float[] verts;
		public short[] idx;
		public float height;
		public int vertexCount() {
			return verts.length / STRIDE;
		}
	}

	private static final ObjectMap<String, ProtoMesh> CACHE = new ObjectMap<String, ProtoMesh>();

	private FoliageMeshes() {
	}

	public static boolean isBuiltin(String src) {
		return src != null && src.startsWith("builtin:");
	}

	public static ProtoMesh get(String name) {
		if (name.startsWith("builtin:")) name = name.substring(8);
		ProtoMesh m = CACHE.get(name);
		if (m != null) return m;
		B b = new B(name.hashCode());
		build(name, b);
		m = b.done();
		CACHE.put(name, m);
		return m;
	}

	/** Collider radius / height and the usual size of a kind (generator, new kinds in the editor). */
	public static float[] defaults(String name) {
		if (name.startsWith("builtin:")) name = name.substring(8);
		if (name.startsWith("tree_")) return new float[]{0.35f, 4f, 1};
		if (name.equals("rock") || name.equals("boulder")) return new float[]{0.7f, 1.2f, 1};
		if (name.equals("rock_flat")) return new float[]{0.6f, 0.5f, 1};
		if (name.equals("stump")) return new float[]{0.35f, 0.6f, 1};
		if (name.equals("log")) return new float[]{0.4f, 0.5f, 1};
		return new float[]{0, 1, 0}; // walk through (bushes, flowers, ferns, reeds, mushrooms)
	}

	static void build(String n, B b) {
		if ("tree_pine".equals(n)) {
			b.cylinder(0, 0, 0, 0.16f, 0.1f, 1.2f, 6, 0x6B4A2E, 0f);
			b.cone(0, 1.0f, 0, 1.25f, 1.7f, 7, 0x2F6B34, 0.25f);
			b.cone(0, 1.9f, 0, 0.98f, 1.5f, 7, 0x357A3A, 0.5f);
			b.cone(0, 2.7f, 0, 0.7f, 1.4f, 7, 0x3E8A41, 0.8f);
		} else if ("tree_autumn".equals(n)) {
			trunk(b);
			b.blob(0, 2.6f, 0, 1.25f, 0xD9822B, 0.4f);
			b.blob(0.75f, 2.2f, 0.25f, 0.85f, 0xE0A23A, 0.5f);
			b.blob(-0.6f, 2.35f, -0.35f, 0.9f, 0xC4552A, 0.5f);
			b.blob(0.1f, 3.3f, -0.1f, 0.8f, 0xE6B848, 0.7f);
		} else if ("tree_birch".equals(n)) {
			b.cylinder(0, 0, 0, 0.12f, 0.09f, 2.6f, 6, 0xE8E4D8, 0f);
			b.blob(0, 3.0f, 0, 0.95f, 0x8CC051, 0.5f);
			b.blob(0.45f, 2.55f, 0.2f, 0.7f, 0x7DB34A, 0.6f);
			b.blob(-0.35f, 3.5f, -0.15f, 0.65f, 0x9ACB5C, 0.8f);
		} else if ("tree_palm".equals(n)) {
			for (int k = 0; k < 5; k++) b.cylinder(0.08f * k, 0.8f * k, 0, 0.16f - k * 0.015f, 0.15f - k * 0.015f, 0.82f, 6, k % 2 == 0 ? 0x8A6A42 : 0x7A5C38, k * 0.08f);
			for (int k = 0; k < 7; k++) {
				float a = k * MathUtils.PI2 / 7;
				b.leaf(0.4f, 4.05f, 0, MathUtils.cos(a), MathUtils.sin(a), 2.1f, 0.42f, 0x4E9A3A, 0.9f);
			}
		} else if ("tree_dead".equals(n)) {
			b.cylinder(0, 0, 0, 0.18f, 0.08f, 3.0f, 5, 0x5C4632, 0.1f);
			b.cylinder(0, 1.6f, 0, 0.07f, 0.03f, 1.2f, 4, 0x5C4632, 0.3f, 0.7f, 0.6f, 0.3f);
			b.cylinder(0, 2.1f, 0, 0.06f, 0.03f, 1.0f, 4, 0x5C4632, 0.4f, -0.6f, 0.6f, -0.4f);
		} else if ("bush".equals(n)) {
			b.blob(0, 0.45f, 0, 0.62f, 0x3F8A35, 0.3f);
			b.blob(0.45f, 0.35f, 0.15f, 0.45f, 0x4C9A3C, 0.35f);
			b.blob(-0.4f, 0.32f, -0.2f, 0.42f, 0x367C30, 0.35f);
		} else if ("bush_flowers".equals(n)) {
			b.blob(0, 0.45f, 0, 0.6f, 0x3F8A35, 0.3f);
			b.blob(0.42f, 0.33f, 0.18f, 0.42f, 0x4C9A3C, 0.35f);
			for (int k = 0; k < 9; k++) {
				float a = k * 2.4f, r = 0.35f + (k % 3) * 0.12f;
				b.box(MathUtils.cos(a) * r, 0.55f + (k % 2) * 0.25f, MathUtils.sin(a) * r, 0.09f, k % 2 == 0 ? 0xF2A7C9 : 0xFFFFFF, 0.4f);
			}
		} else if ("rock".equals(n)) {
			b.rock(0, 0.35f, 0, 0.7f, 0.55f, 0x8A8A86, 0);
		} else if ("rock_flat".equals(n)) {
			b.rock(0, 0.12f, 0, 0.75f, 0.28f, 0x9A968E, 0);
		} else if ("boulder".equals(n)) {
			b.rock(0, 0.7f, 0, 1.3f, 1.05f, 0x7E7D78, 0);
			b.rock(0.9f, 0.3f, 0.4f, 0.55f, 0.45f, 0x8E8C86, 0);
		} else if (n.startsWith("flower_")) {
			int c = "flower_yellow".equals(n) ? 0xF6D23C : ("flower_white".equals(n) ? 0xF4F4F0 : ("flower_purple".equals(n) ? 0xA36BD6 : 0xE5483E));
			for (int k = 0; k < 3; k++) {
				float a = k * 2.1f, r = k == 0 ? 0 : 0.12f;
				float x = MathUtils.cos(a) * r, z = MathUtils.sin(a) * r, hgt = 0.26f + k * 0.05f;
				b.cylinder(x, 0, z, 0.012f, 0.01f, hgt, 3, 0x4E9A3A, 0f, 0, 0, 1f);
				b.flower(x, hgt, z, 0.07f, c, 1f);
			}
		} else if ("mushroom".equals(n)) {
			b.cylinder(0, 0, 0, 0.05f, 0.045f, 0.16f, 6, 0xF2EBDD, 0f);
			b.cone(0, 0.14f, 0, 0.16f, 0.11f, 8, 0xD23B2C, 0f);
			b.box(0.05f, 0.21f, 0.03f, 0.025f, 0xFFFFFF, 0f);
			b.box(-0.06f, 0.2f, -0.02f, 0.02f, 0xFFFFFF, 0f);
		} else if ("fern".equals(n)) {
			for (int k = 0; k < 7; k++) {
				float a = k * MathUtils.PI2 / 7;
				b.leaf(0, 0.05f, 0, MathUtils.cos(a), MathUtils.sin(a), 0.65f, 0.16f, 0x3E8C3A, 1f);
			}
		} else if ("stump".equals(n)) {
			b.cylinder(0, 0, 0, 0.36f, 0.32f, 0.5f, 7, 0x6B4A2E, 0f);
			b.disc(0, 0.5f, 0, 0.3f, 7, 0xC9A36E);
		} else if ("log".equals(n)) {
			b.cylinder(-0.9f, 0.28f, 0, 0.28f, 0.27f, 1.8f, 7, 0x6E4E30, 0f, 1, 0, 0);
		} else if ("reeds".equals(n)) {
			for (int k = 0; k < 9; k++) {
				float a = k * 2.4f, r = (k % 3) * 0.12f;
				b.cylinder(MathUtils.cos(a) * r, 0, MathUtils.sin(a) * r, 0.025f, 0.008f, 1.0f + (k % 4) * 0.2f, 3, k % 2 == 0 ? 0x6D8F3A : 0x86A24A, 0f, 0, 0, 1f);
			}
		} else { // tree_round
			trunk(b);
			b.blob(0, 2.7f, 0, 1.3f, 0x4C9B3A, 0.4f);
			b.blob(0.8f, 2.2f, 0.3f, 0.9f, 0x5BAE44, 0.5f);
			b.blob(-0.7f, 2.3f, -0.3f, 0.95f, 0x438C35, 0.5f);
			b.blob(0.1f, 3.45f, -0.1f, 0.85f, 0x63B84B, 0.75f);
		}
	}

	static void trunk(B b) {
		b.cylinder(0, 0, 0, 0.24f, 0.15f, 2.2f, 6, 0x6B4A2E, 0f);
	}

	/** Grass tuft for the grass layer: thin 2 sided blades leaning out (sway 0 at the root -> 1 at the tip). */
	public static ProtoMesh grassTuft() {
		ProtoMesh m = CACHE.get("#grass");
		if (m != null) return m;
		B b = new B(7);
		int blades = 7;
		for (int k = 0; k < blades; k++) {
			float a = k * MathUtils.PI2 / blades + b.r() * 0.6f;
			float r = 0.03f + b.r() * 0.1f;
			float x = MathUtils.cos(a) * r, z = MathUtils.sin(a) * r;
			float lean = 0.12f + b.r() * 0.25f, h = 0.65f + b.r() * 0.45f;
			float w = 0.035f + b.r() * 0.02f;
			// blade faces across its lean direction
			float px = -MathUtils.sin(a) * w, pz = MathUtils.cos(a) * w;
			b.blade(x - px, 0, z - pz, x + px, 0, z + pz, x + MathUtils.cos(a) * lean, h, z + MathUtils.sin(a) * lean);
		}
		m = b.done();
		CACHE.put("#grass", m);
		return m;
	}

	// ------------------------------------------------------------------ builder

	static final class B {
		final FloatArray v = new FloatArray();
		final ShortArray i = new ShortArray();
		final int seed;
		int rnd;
		float maxY;

		B(int seed) {
			this.seed = seed;
			this.rnd = seed;
		}

		float r() {
			rnd = rnd * 1103515245 + 12345;
			return ((rnd >>> 8) & 0xffff) / 65535f;
		}

		ProtoMesh done() {
			ProtoMesh m = new ProtoMesh();
			m.verts = v.toArray();
			m.idx = i.toArray();
			m.height = maxY;
			return m;
		}

		private final Vector3 n = new Vector3(), e1 = new Vector3(), e2 = new Vector3();

		void vert(float x, float y, float z, float nx, float ny, float nz, float r, float g, float bl, float a) {
			v.addAll(x, y, z, nx, ny, nz, r, g, bl, a);
			maxY = Math.max(maxY, y);
		}

		/** Flat shaded triangle; color shaded a little by the face direction (stylized look under any light). */
		void tri(Vector3 a, Vector3 b2, Vector3 c, int rgb, float sway) {
			e1.set(b2).sub(a);
			e2.set(c).sub(a);
			n.set(e1).crs(e2).nor();
			float k = 0.9f + 0.1f * n.y + (r() - 0.5f) * 0.08f;
			float cr = ((rgb >> 16) & 255) / 255f * k, cg = ((rgb >> 8) & 255) / 255f * k, cb = (rgb & 255) / 255f * k;
			short base = (short) (v.size / STRIDE);
			float sa = sway * MathUtils.clamp(a.y / 3f, 0, 1), sb = sway * MathUtils.clamp(b2.y / 3f, 0, 1), sc = sway * MathUtils.clamp(c.y / 3f, 0, 1);
			vert(a.x, a.y, a.z, n.x, n.y, n.z, cr, cg, cb, sa);
			vert(b2.x, b2.y, b2.z, n.x, n.y, n.z, cr, cg, cb, sb);
			vert(c.x, c.y, c.z, n.x, n.y, n.z, cr, cg, cb, sc);
			i.add(base);
			i.add((short) (base + 1));
			i.add((short) (base + 2));
		}

		final Vector3 p0 = new Vector3(), p1 = new Vector3(), p2 = new Vector3(), p3 = new Vector3();

		/** Tapered cylinder from (x, y, z), axis up (or along ax, ay, az). */
		void cylinder(float x, float y, float z, float r0, float r1, float hgt, int seg, int rgb, float sway) {
			cylinder(x, y, z, r0, r1, hgt, seg, rgb, sway, 0, 1, 0);
		}

		void cylinder(float x, float y, float z, float r0, float r1, float hgt, int seg, int rgb, float sway, float ax, float ay, float az) {
			Vector3 axis = new Vector3(ax, ay, az).nor();
			Vector3 u = Math.abs(axis.y) > 0.9f ? new Vector3(1, 0, 0) : new Vector3(0, 1, 0);
			Vector3 s = new Vector3(u).crs(axis).nor(), t = new Vector3(axis).crs(s).nor();
			Vector3 base = new Vector3(x, y, z), top = new Vector3(axis).scl(hgt).add(base);
			for (int k = 0; k < seg; k++) {
				float a0 = k * MathUtils.PI2 / seg, a1 = (k + 1) * MathUtils.PI2 / seg;
				p0.set(s).scl(MathUtils.cos(a0) * r0).mulAdd(t, MathUtils.sin(a0) * r0).add(base);
				p1.set(s).scl(MathUtils.cos(a1) * r0).mulAdd(t, MathUtils.sin(a1) * r0).add(base);
				p2.set(s).scl(MathUtils.cos(a1) * r1).mulAdd(t, MathUtils.sin(a1) * r1).add(top);
				p3.set(s).scl(MathUtils.cos(a0) * r1).mulAdd(t, MathUtils.sin(a0) * r1).add(top);
				tri(p0, p2, p1, rgb, sway);
				tri(p0, p3, p2, rgb, sway);
				if (r1 > 0.02f) tri(top, p2, p3, rgb, sway);
			}
		}

		void cone(float x, float y, float z, float r, float hgt, int seg, int rgb, float sway) {
			Vector3 tip = new Vector3(x, y + hgt, z), c = new Vector3(x, y, z);
			for (int k = 0; k < seg; k++) {
				float a0 = k * MathUtils.PI2 / seg, a1 = (k + 1) * MathUtils.PI2 / seg;
				float j0 = 1 + (r() - 0.5f) * 0.15f;
				p0.set(x + MathUtils.cos(a0) * r * j0, y + (r() - 0.5f) * 0.1f, z + MathUtils.sin(a0) * r * j0);
				p1.set(x + MathUtils.cos(a1) * r, y, z + MathUtils.sin(a1) * r);
				tri(p0, tip, p1, rgb, sway);
				tri(p0, p1, c, rgb, sway);
			}
		}

		void disc(float x, float y, float z, float r, int seg, int rgb) {
			Vector3 c = new Vector3(x, y, z);
			for (int k = 0; k < seg; k++) {
				float a0 = k * MathUtils.PI2 / seg, a1 = (k + 1) * MathUtils.PI2 / seg;
				p0.set(x + MathUtils.cos(a0) * r, y, z + MathUtils.sin(a0) * r);
				p1.set(x + MathUtils.cos(a1) * r, y, z + MathUtils.sin(a1) * r);
				tri(c, p1, p0, rgb, 0);
			}
		}

		void box(float x, float y, float z, float s, int rgb, float sway) {
			float[][] c = new float[8][];
			for (int k = 0; k < 8; k++) c[k] = new float[]{x + ((k & 1) == 0 ? -s : s), y + ((k & 2) == 0 ? -s : s), z + ((k & 4) == 0 ? -s : s)};
			int[][] f = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}};
			for (int[] q : f) {
				p0.set(c[q[0]][0], c[q[0]][1], c[q[0]][2]);
				p1.set(c[q[1]][0], c[q[1]][1], c[q[1]][2]);
				p2.set(c[q[2]][0], c[q[2]][1], c[q[2]][2]);
				p3.set(c[q[3]][0], c[q[3]][1], c[q[3]][2]);
				tri(p0, p1, p2, rgb, sway);
				tri(p0, p2, p3, rgb, sway);
			}
		}

		private static final float[][] ICO;
		private static final int[][] ICO_F = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
				{3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};

		static {
			float t = (1f + (float) Math.sqrt(5)) / 2f;
			float[][] p = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
			for (float[] q : p) {
				float l = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
				q[0] /= l;
				q[1] /= l;
				q[2] /= l;
			}
			ICO = p;
		}

		/** Jittered icosahedron (leaf blob / rock). */
		void ico(float x, float y, float z, float rx, float ry, float jitter, int rgb, float sway) {
			float[][] q = new float[12][3];
			for (int k = 0; k < 12; k++) {
				float j = 1 + (r() - 0.5f) * jitter;
				q[k][0] = x + ICO[k][0] * rx * j;
				q[k][1] = y + ICO[k][1] * ry * j;
				q[k][2] = z + ICO[k][2] * rx * j;
			}
			for (int[] f : ICO_F) {
				p0.set(q[f[0]][0], q[f[0]][1], q[f[0]][2]);
				p1.set(q[f[1]][0], q[f[1]][1], q[f[1]][2]);
				p2.set(q[f[2]][0], q[f[2]][1], q[f[2]][2]);
				tri(p0, p1, p2, rgb, sway);
			}
		}

		void blob(float x, float y, float z, float r, int rgb, float sway) {
			ico(x, y, z, r, r * 0.86f, 0.25f, rgb, sway);
		}

		void rock(float x, float y, float z, float rx, float ry, int rgb, float sway) {
			ico(x, y, z, rx, ry, 0.45f, rgb, sway);
		}

		/** Flat leaf from (x, y, z) toward dx, dz, drooping (palm fronds, ferns). */
		void leaf(float x, float y, float z, float dx, float dz, float len, float w, int rgb, float sway) {
			float px = -dz * w * 0.5f, pz = dx * w * 0.5f;
			Vector3 a = new Vector3(x, y, z), mid = new Vector3(x + dx * len * 0.5f, y + len * 0.12f, z + dz * len * 0.5f), tip = new Vector3(x + dx * len, y - len * 0.25f, z + dz * len);
			Vector3 l = new Vector3(mid.x + px, mid.y, mid.z + pz), rr = new Vector3(mid.x - px, mid.y, mid.z - pz);
			tri(a, l, mid, rgb, sway);
			tri(a, mid, rr, rgb, sway);
			tri(mid, l, tip, rgb, sway);
			tri(mid, tip, rr, rgb, sway);
			// back faces
			tri(a, mid, l, rgb, sway);
			tri(a, rr, mid, rgb, sway);
			tri(mid, tip, l, rgb, sway);
			tri(mid, rr, tip, rgb, sway);
		}

		void flower(float x, float y, float z, float r, int rgb, float sway) {
			Vector3 c = new Vector3(x, y + 0.01f, z);
			for (int k = 0; k < 5; k++) {
				float a0 = k * MathUtils.PI2 / 5, a1 = a0 + 0.9f;
				p0.set(x + MathUtils.cos(a0) * r, y, z + MathUtils.sin(a0) * r);
				p1.set(x + MathUtils.cos(a1) * r, y, z + MathUtils.sin(a1) * r);
				tri(c, p1, p0, rgb, sway);
				tri(c, p0, p1, rgb, sway);
			}
			box(x, y + 0.012f, z, r * 0.25f, 0xF8D648, sway);
		}

		/** One thin grass blade (triangle, drawn 2 sided): root darker, tip light and swaying. */
		void blade(float x0, float y0, float z0, float x1, float y1, float z1, float tx, float ty, float tz) {
			short base = (short) (v.size / STRIDE);
			// soft normals pointing up: grass reads as one lit carpet instead of flickering cards
			vert(x0, y0, z0, 0, 1, 0, 0.5f, 0.5f, 0.5f, 0);
			vert(x1, y1, z1, 0, 1, 0, 0.5f, 0.5f, 0.5f, 0);
			vert(tx, ty, tz, 0, 1, 0, 1.08f, 1.08f, 1.0f, 1);
			i.addAll(base, (short) (base + 1), (short) (base + 2));
		}
	}
}
