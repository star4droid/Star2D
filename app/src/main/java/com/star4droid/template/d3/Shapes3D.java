package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Ready-made shapes, built once per detail level and shared by every element (an element only owns a ModelInstance).
 * Every shape is 1 x 1 x 1 meter centered on its origin; the element's Scale gives its size.
 */
public final class Shapes3D {
	public static final String CUBE = "CUBE", SPHERE = "SPHERE", CYLINDER = "CYLINDER", CAPSULE = "CAPSULE", CONE = "CONE",
			PLANE = "PLANE", TORUS = "TORUS", RAMP = "RAMP", STAIRS = "STAIRS", PYRAMID = "PYRAMID", HEMISPHERE = "HEMISPHERE",
			TUBE = "TUBE", WEDGE = "WEDGE", QUAD = "QUAD", DISC = "DISC";
	public static final String[] ALL = {CUBE, SPHERE, CYLINDER, CAPSULE, CONE, PLANE, TORUS, RAMP, STAIRS, PYRAMID, HEMISPHERE, TUBE, WEDGE, QUAD, DISC};

	private static final ObjectMap<String, Model> CACHE = new ObjectMap<String, Model>();
	private static final long ATTR = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;

	private Shapes3D() {
	}

	public static boolean isShape(String s) {
		for (String a : ALL) if (a.equals(s)) return true;
		return false;
	}

	/** Shared model of a shape. detail: 0 low, 1 normal, 2 high (segments of round shapes). */
	public static Model get(String shape, int detail, int steps) {
		if (shape == null) shape = CUBE;
		String key = shape + "|" + detail + "|" + steps;
		Model m = CACHE.get(key);
		if (m == null) {
			m = build(shape, detail, steps);
			CACHE.put(key, m);
		}
		return m;
	}

	/** New GL context: the cached models belong to the old one (see Assets3D.checkContext). */
	static void forgetGL() {
		CACHE.clear();
	}

	public static void disposeAll() {
		for (Model m : CACHE.values()) {
			try {
				m.dispose();
			} catch (Throwable ignored) {
			}
		}
		CACHE.clear();
	}

	private static Model build(String shape, int detail, int steps) {
		int seg = detail <= 0 ? 12 : (detail == 1 ? 24 : 40);
		ModelBuilder mb = new ModelBuilder();
		mb.begin();
		MeshPartBuilder b = mb.part("shape", GL20.GL_TRIANGLES, ATTR, new Material());
		if (CUBE.equals(shape)) {
			b.box(1f, 1f, 1f);
		} else if (SPHERE.equals(shape)) {
			b.sphere(1f, 1f, 1f, seg, seg / 2 + 2);
		} else if (HEMISPHERE.equals(shape)) {
			b.sphere(1f, 1f, 1f, seg, seg / 2 + 2, 0, 360, 0, 90);
		} else if (CYLINDER.equals(shape)) {
			b.cylinder(1f, 1f, 1f, seg);
		} else if (CAPSULE.equals(shape)) {
			b.capsule(0.5f, 1f, seg);
		} else if (CONE.equals(shape)) {
			b.cone(1f, 1f, 1f, seg);
		} else if (PLANE.equals(shape)) {
			b.box(1f, 0.0001f, 1f);
		} else if (QUAD.equals(shape)) {
			b.rect(-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0, 0, 0, 1);
		} else if (DISC.equals(shape)) {
			b.ellipse(1f, 1f, seg, 0, 0, 0, 0, 1, 0);
		} else if (TORUS.equals(shape)) {
			torus(b, 0.35f, 0.15f, seg, Math.max(8, seg / 2));
		} else if (TUBE.equals(shape)) {
			tube(b, 0.5f, 0.35f, seg);
		} else if (RAMP.equals(shape) || WEDGE.equals(shape)) {
			ramp(b);
		} else if (STAIRS.equals(shape)) {
			stairs(b, Math.max(2, steps <= 0 ? 6 : steps));
		} else if (PYRAMID.equals(shape)) {
			pyramid(b);
		} else {
			b.box(1f, 1f, 1f);
		}
		return mb.end();
	}

	// ---------------------------------------------------------------- custom shapes

	private static final Vector3 t1 = new Vector3(), t2 = new Vector3(), n = new Vector3();

	private static void quad(MeshPartBuilder b, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
		t1.set(x2 - x1, y2 - y1, z2 - z1);
		t2.set(x4 - x1, y4 - y1, z4 - z1);
		n.set(t1).crs(t2).nor();
		b.setUVRange(0, 0, 1, 1);
		b.rect(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, n.x, n.y, n.z);
	}

	private static void tri(MeshPartBuilder b, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		t1.set(x2 - x1, y2 - y1, z2 - z1);
		t2.set(x3 - x1, y3 - y1, z3 - z1);
		n.set(t1).crs(t2).nor();
		MeshPartBuilder.VertexInfo a = new MeshPartBuilder.VertexInfo().setPos(x1, y1, z1).setNor(n).setUV(0, 0);
		MeshPartBuilder.VertexInfo c = new MeshPartBuilder.VertexInfo().setPos(x2, y2, z2).setNor(n).setUV(1, 0);
		MeshPartBuilder.VertexInfo d = new MeshPartBuilder.VertexInfo().setPos(x3, y3, z3).setNor(n).setUV(0.5f, 1);
		b.triangle(a, c, d);
	}

	/** Ramp: high side at -Z, slope down toward +Z. */
	private static void ramp(MeshPartBuilder b) {
		float h = 0.5f;
		// bottom
		quad(b, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h);
		// back (vertical, -Z)
		quad(b, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h);
		// slope
		quad(b, -h, -h, h, h, -h, h, h, h, -h, -h, h, -h);
		// sides
		tri(b, -h, -h, h, -h, h, -h, -h, -h, -h);
		tri(b, h, -h, h, h, -h, -h, h, h, -h);
	}

	private static void pyramid(MeshPartBuilder b) {
		float h = 0.5f;
		quad(b, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h);
		tri(b, -h, -h, h, h, -h, h, 0, h, 0);
		tri(b, h, -h, h, h, -h, -h, 0, h, 0);
		tri(b, h, -h, -h, -h, -h, -h, 0, h, 0);
		tri(b, -h, -h, -h, -h, -h, h, 0, h, 0);
	}

	/** Stairs rising toward -Z, steps count. */
	private static void stairs(MeshPartBuilder b, int steps) {
		float h = 0.5f, sh = 1f / steps;
		for (int i = 0; i < steps; i++) {
			float z0 = h - i * sh, z1 = z0 - sh, top = -h + (i + 1) * sh;
			// tread
			quad(b, -h, top, z0, h, top, z0, h, top, z1, -h, top, z1);
			// riser
			float bottom = top - sh;
			quad(b, -h, bottom, z0, h, bottom, z0, h, top, z0, -h, top, z0);
			// sides (column under this step)
			quad(b, -h, -h, z1, -h, -h, z0, -h, top, z0, -h, top, z1);
			quad(b, h, -h, z0, h, -h, z1, h, top, z1, h, top, z0);
		}
		// back and bottom
		quad(b, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h);
		quad(b, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h);
	}

	private static void torus(MeshPartBuilder b, float R, float r, int seg, int sides) {
		MeshPartBuilder.VertexInfo[][] v = new MeshPartBuilder.VertexInfo[seg + 1][sides + 1];
		for (int i = 0; i <= seg; i++) {
			float u = (float) i / seg * MathUtils.PI2;
			float cu = MathUtils.cos(u), su = MathUtils.sin(u);
			for (int j = 0; j <= sides; j++) {
				float w = (float) j / sides * MathUtils.PI2;
				float cw = MathUtils.cos(w), sw = MathUtils.sin(w);
				MeshPartBuilder.VertexInfo vi = new MeshPartBuilder.VertexInfo();
				vi.setPos((R + r * cw) * cu, r * sw, (R + r * cw) * su);
				vi.setNor(cw * cu, sw, cw * su);
				vi.setUV((float) i / seg, (float) j / sides);
				v[i][j] = vi;
			}
		}
		for (int i = 0; i < seg; i++)
			for (int j = 0; j < sides; j++)
				b.rect(v[i][j], v[i][j + 1], v[i + 1][j + 1], v[i + 1][j]);
	}

	private static void tube(MeshPartBuilder b, float ro, float ri, int seg) {
		float h = 0.5f;
		for (int i = 0; i < seg; i++) {
			float a0 = (float) i / seg * MathUtils.PI2, a1 = (float) (i + 1) / seg * MathUtils.PI2;
			float c0 = MathUtils.cos(a0), s0 = MathUtils.sin(a0), c1 = MathUtils.cos(a1), s1 = MathUtils.sin(a1);
			quad(b, ro * c0, -h, ro * s0, ro * c0, h, ro * s0, ro * c1, h, ro * s1, ro * c1, -h, ro * s1);
			quad(b, ri * c1, -h, ri * s1, ri * c1, h, ri * s1, ri * c0, h, ri * s0, ri * c0, -h, ri * s0);
			quad(b, ri * c0, h, ri * s0, ri * c1, h, ri * s1, ro * c1, h, ro * s1, ro * c0, h, ro * s0);
			quad(b, ro * c0, -h, ro * s0, ro * c1, -h, ro * s1, ri * c1, -h, ri * s1, ri * c0, -h, ri * s0);
		}
	}
}
