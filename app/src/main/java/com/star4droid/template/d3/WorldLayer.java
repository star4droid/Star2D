package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape;
import com.badlogic.gdx.physics.bullet.collision.btHeightfieldTerrainShape;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.star4droid.worldbuilder.engine.WorldView;

import java.nio.FloatBuffer;
import java.util.IdentityHashMap;

/**
 * Mesh WORLD: a world made in World Builder (module worldbuilder:core) drawn read only inside a 3D scene, in the editor
 * and in games. Its ground and the colliders of its elements become one static Bullet shape. World Builder types stay
 * inside this class, so the classes games compile against never refer to them.
 */
public final class WorldLayer {
	private WorldLayer() {
	}

	private static final IdentityHashMap<Object3D, WorldView> views = new IdentityHashMap<Object3D, WorldView>();
	private static final IdentityHashMap<Object3D, String> files = new IdentityHashMap<Object3D, String>();
	private static final java.util.HashSet<String> reported = new java.util.HashSet<String>();
	private static final Matrix4 m = new Matrix4();

	/** The world file of an element (World File in the project's worlds folder), or null. */
	static FileHandle fileOf(Object3D o) {
		String p = o.d().World_File;
		if (p == null || p.length() == 0) return null;
		return Assets3D.file(p, "worlds");
	}

	/** The view of an element's world (made, or made again when World File changed), or null when it has none. */
	private static WorldView view(Object3D o) {
		String p = o.d().World_File == null ? "" : o.d().World_File;
		WorldView v = views.get(o);
		if (v != null && p.equals(files.get(o))) return v;
		dispose(o);
		FileHandle f = fileOf(o);
		if (f == null) {
			if (p.length() > 0 && reported.add(p)) Gdx.app.error("Star2D-3D", "world file not found: " + p);
			return null;
		}
		v = new WorldView(f);
		views.put(o, v);
		files.put(o, p);
		return v;
	}

	/** Position, rotation and scale of the element as one matrix (with its anchor). */
	private static Matrix4 placement(Object3D o) {
		m.set(o.position, o.rotation, o.scale);
		if (o.hasAnchor()) m.translate(-o.d().Anchor_X, -o.d().Anchor_Y, -o.d().Anchor_Z);
		return m;
	}

	/** Draws every visible WORLD element (call after the sky, before the scene's models). */
	public static void renderAll(Camera cam, Array<Object3D> objects) {
		float dt = Math.min(0.1f, Gdx.graphics.getDeltaTime());
		for (int i = 0; i < objects.size; i++) {
			Object3D o = objects.get(i);
			if (!o.isWorld() || !o.isVisible()) continue;
			WorldView v = view(o);
			if (v == null) continue;
			v.render(cam, placement(o), dt);
			String err = v.error();
			if (err != null && reported.add(o.d().World_File + err)) Gdx.app.error("Star2D-3D", "world " + o.d().World_File + ": " + err);
		}
	}

	/** Frees the world of an element (it is read again if the element is drawn later). */
	public static void dispose(Object3D o) {
		WorldView v = views.remove(o);
		files.remove(o);
		if (v != null) {
			try {
				v.dispose();
			} catch (Throwable ignored) {
			}
		}
	}

	/**
	 * Frees the textures and meshes shared by the worlds. Call on the rendering thread when another screen with its own
	 * graphics (World Builder, a game started from the editor) is about to open; the worlds load again when drawn.
	 */
	public static void releaseShared() {
		try {
			WorldView.releaseShared();
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "worlds release: " + t);
		}
	}

	/** One static shape for the world: its ground (heightfield) and every element collider that blocks. */
	static btCollisionShape shape(Object3D o, Array<Disposable> own) {
		btCompoundShape comp = new btCompoundShape();
		own.add(comp);
		WorldView v = view(o);
		if (v == null) return comp;
		float s = Math.abs(o.scale.x);
		float sy = Math.abs(o.scale.y);
		float[] g = v.groundHeights();
		if (g != null) {
			int n = (int) g[0] + 1;
			float cell = g[1];
			float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
			FloatBuffer fb = BufferUtils.newFloatBuffer(n * n);
			for (int i = 2; i < g.length; i++) {
				fb.put(g[i]);
				mn = Math.min(mn, g[i]);
				mx = Math.max(mx, g[i]);
			}
			fb.flip();
			if (mx - mn < 0.001f) mx = mn + 0.001f;
			btHeightfieldTerrainShape hf = new btHeightfieldTerrainShape(n, n, fb, 1f, mn, mx, 1, false);
			hf.setLocalScaling(new Vector3(cell * Math.abs(o.scale.x), sy, cell * Math.abs(o.scale.z)));
			comp.addChildShape(new Matrix4().setToTranslation(0, (mn + mx) * 0.5f * sy, 0), hf);
			own.add(hf);
			heights.put(hf, fb);      // the heightfield reads this buffer while it lives
		}
		FloatArray cs = v.colliders();
		Quaternion q = new Quaternion();
		for (int i = 0; i + 10 < cs.size; i += 11) {
			int type = (int) cs.get(i);
			btCollisionShape sh;
			if (type == 1) sh = new btBoxShape(new Vector3(Math.max(0.01f, cs.get(i + 8) * s), Math.max(0.01f, cs.get(i + 9) * s), Math.max(0.01f, cs.get(i + 10) * s)));
			else if (type == 2) sh = new btSphereShape(Math.max(0.01f, cs.get(i + 8) * s));
			else sh = new btCapsuleShape(Math.max(0.01f, cs.get(i + 8) * s), Math.max(0.01f, cs.get(i + 9) * 2 * s));
			q.set(cs.get(i + 4), cs.get(i + 5), cs.get(i + 6), cs.get(i + 7));
			comp.addChildShape(new Matrix4().set(new Vector3(cs.get(i + 1) * s, cs.get(i + 2) * s, cs.get(i + 3) * s), q), sh);
			own.add(sh);
		}
		return comp;
	}

	/** Buffers read by the Bullet heightfields (kept while the shapes may exist). */
	private static final IdentityHashMap<Object, FloatBuffer> heights = new IdentityHashMap<Object, FloatBuffer>();
}
