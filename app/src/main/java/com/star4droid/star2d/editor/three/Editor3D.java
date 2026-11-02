package com.star4droid.star2d.editor.three;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.d3.Assets3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.Quality3D;
import com.star4droid.template.d3.World3D;

/**
 * The 3D viewport of the editor: renders the scene's {@link World3D} with an orbit camera, draws the grid, the icons of
 * lights / cameras and the gizmo of the selected element (move / scale / rotate arrows and rings), and turns touches
 * into camera moves, selection and gizmo drags (with undo).
 *
 * Touch: tap = select (empty = deselect), drag on empty space = orbit, two fingers = pan + pinch zoom,
 * double tap = focus, drag on an arrow / ring = edit along that axis, drag on the center = move on the ground plane.
 */
public class Editor3D {
	public final LibgdxEditor editor;
	public final World3D world;
	public final PerspectiveCamera cam = new PerspectiveCamera(60, 16, 9);
	private final ShapeRenderer shapes = new ShapeRenderer();
	/** Orbit camera: target point, angles (degrees), distance. */
	public final Vector3 target = new Vector3(0, 0.5f, 0);
	public float yaw = 35, pitch = -28, distance = 14;
	/** Snap moves to 0.25 m, rotations to 15 degrees, scales to 0.1. */
	public boolean snap = false;
	/** Gizmo axes follow the element's rotation (move / scale). */
	public boolean localAxes = false;
	/** Touches go to the 2D layer (HUD / UI elements) instead of the 3D world. */
	public boolean edit2D = false;
	public boolean showGrid = true;
	/** Editor viewport quality (independent of the game's). */
	public static String editorQuality = "MEDIUM";

	public Editor3D(LibgdxEditor editor) {
		this.editor = editor;
		world = new World3D(null);
		world.editor = true;
		world.editorCameraLocked = true;
		cam.near = 0.05f;
		cam.far = 1000f;
		Assets3D.projectPath = editor.getProject().getPath();
		updateCamera();
	}

	public void configure(PropertySet<String, Object> config) {
		try {
			com.star4droid.template.Utils.PropertySet<String, Object> p = new com.star4droid.template.Utils.PropertySet<String, Object>();
			if (config != null) p.putAll(config);
			world.configure(p);
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "editor config: " + t);
		}
	}

	public void updateCamera() {
		pitch = MathUtils.clamp(pitch, -89, 89);
		distance = MathUtils.clamp(distance, 0.5f, 600f);
		float cp = MathUtils.cosDeg(pitch), sp = MathUtils.sinDeg(pitch);
		cam.position.set(target.x + MathUtils.sinDeg(yaw) * cp * distance, target.y - sp * distance, target.z + MathUtils.cosDeg(yaw) * cp * distance);
		cam.up.set(Vector3.Y);
		cam.lookAt(target);
		cam.viewportWidth = Math.max(1, Gdx.graphics.getWidth());
		cam.viewportHeight = Math.max(1, Gdx.graphics.getHeight());
		cam.far = Math.max(200f, distance * 20f);
		cam.update();
	}

	// ------------------------------------------------------------------ views

	public void viewTop() {
		pitch = -89;
		yaw = 0;
		updateCamera();
	}

	public void viewFront() {
		pitch = -5;
		yaw = 0;
		updateCamera();
	}

	public void viewSide() {
		pitch = -5;
		yaw = 90;
		updateCamera();
	}

	public void viewPerspective() {
		pitch = -28;
		yaw = 35;
		updateCamera();
	}

	public void focusSelected() {
		Editor3DItem it = selected3D();
		if (it == null) {
			target.set(0, 0.5f, 0);
		} else if (it.obj != null) {
			it.obj.center(target);
			distance = Math.max(3f, it.obj.getSizeX() + it.obj.getSizeY() + it.obj.getSizeZ()) * 1.6f;
		} else target.set(it.px(), it.py(), it.pz());
		updateCamera();
	}

	public Editor3DItem selected3D() {
		Actor a = editor.getSelectedActor();
		return a instanceof Editor3DItem ? (Editor3DItem) a : null;
	}

	// ------------------------------------------------------------------ draw

	private final Vector3 tmp = new Vector3(), tmp2 = new Vector3(), tmp3 = new Vector3();

	public void render() {
		float dt = Math.min(0.05f, Gdx.graphics.getDeltaTime());
		updateCamera();
		String q = Quality3D.level;
		Quality3D.setLevel(editorQuality);
		for (Actor a : editor.getActors()) {
			if (a instanceof Editor3DItem) {
				Editor3DItem it = (Editor3DItem) a;
				if (it.obj != null) it.obj.selected = (a == editor.getSelectedActor());
			}
		}
		world.camera = cam;
		world.update(dt, false);
		world.render(cam, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
		Quality3D.level = q;
		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		shapes.setProjectionMatrix(cam.combined);
		if (showGrid) drawGrid();
		drawIcons();
		Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
		drawGizmo();
		Gdx.gl.glEnable(GL20.GL_BLEND);
	}

	private void drawGrid() {
		float step = distance > 60 ? 10 : (distance > 25 ? 2 : 1);
		int n = 40;
		float cx = MathUtils.round(target.x / step) * step, cz = MathUtils.round(target.z / step) * step;
		shapes.begin(ShapeRenderer.ShapeType.Line);
		for (int i = -n; i <= n; i++) {
			float a = i % 5 == 0 ? 0.35f : 0.16f;
			shapes.setColor(0.7f, 0.72f, 0.78f, a);
			shapes.line(cx + i * step, 0, cz - n * step, cx + i * step, 0, cz + n * step);
			shapes.line(cx - n * step, 0, cz + i * step, cx + n * step, 0, cz + i * step);
		}
		shapes.setColor(0.95f, 0.3f, 0.3f, 0.8f);
		shapes.line(cx - n * step, 0, 0, cx + n * step, 0, 0);
		shapes.setColor(0.3f, 0.5f, 0.95f, 0.8f);
		shapes.line(0, 0, cz - n * step, 0, 0, cz + n * step);
		shapes.end();
	}

	private void drawIcons() {
		shapes.begin(ShapeRenderer.ShapeType.Line);
		for (Actor a : editor.getActors()) {
			if (!(a instanceof Editor3DItem)) continue;
			Editor3DItem it = (Editor3DItem) a;
			boolean sel = a == editor.getSelectedActor();
			float s = iconSize(it.px(), it.py(), it.pz());
			if (it.light != null) {
				Color c = it.light.color;
				shapes.setColor(sel ? Color.WHITE : c);
				Vector3 p = it.light.position;
				shapes.box(p.x - s * 0.5f, p.y - s * 0.5f, p.z + s * 0.5f, s, s, s);
				if (!"POINT".equals(it.light.type)) {
					tmp.set(it.light.direction).scl(s * 4f).add(p);
					shapes.line(p, tmp);
				} else {
					shapes.circle(p.x, p.y, s, 12);
				}
			} else if (it.camera != null) {
				shapes.setColor(sel ? Color.WHITE : new Color(1f, 0.75f, 0.2f, 1f));
				it.camera.resize(16, 9);
				it.camera.cam.position.set(it.camera.position);
				com.badlogic.gdx.math.Quaternion qq = new com.badlogic.gdx.math.Quaternion().setEulerAngles(it.camera.yaw, it.camera.pitch, it.camera.roll);
				it.camera.cam.direction.set(0, 0, -1).mul(qq);
				it.camera.cam.up.set(0, 1, 0).mul(qq);
				float far = it.camera.cam.far;
				it.camera.cam.far = s * 6f;
				it.camera.cam.update();
				Vector3[] pts = it.camera.cam.frustum.planePoints;
				Vector3 o = it.camera.position;
				for (int i = 4; i < 8; i++) shapes.line(o, pts[i]);
				shapes.line(pts[4], pts[5]);
				shapes.line(pts[5], pts[6]);
				shapes.line(pts[6], pts[7]);
				shapes.line(pts[7], pts[4]);
				it.camera.cam.far = far;
			} else if (it.obj != null && it.obj.instance == null) {
				// EMPTY: a small cross
				shapes.setColor(sel ? Color.WHITE : Color.LIGHT_GRAY);
				Vector3 p = it.obj.position;
				shapes.line(p.x - s, p.y, p.z, p.x + s, p.y, p.z);
				shapes.line(p.x, p.y - s, p.z, p.x, p.y + s, p.z);
				shapes.line(p.x, p.y, p.z - s, p.x, p.y, p.z + s);
			}
		}
		shapes.end();
	}

	private float iconSize(float x, float y, float z) {
		return Math.max(0.05f, cam.position.dst(x, y, z) * 0.035f);
	}

	// ------------------------------------------------------------------ gizmo

	/** MOVE, SCALE, ROTATE or NONE (from the editor's touch mode). */
	private String mode() {
		String m = editor.getTouchModeName();
		return m == null ? "MOVE" : m;
	}

	private final Vector3 origin = new Vector3();
	private final Vector3[] axes = {new Vector3(), new Vector3(), new Vector3()};
	private static final Color[] AXIS_COLORS = {new Color(0.95f, 0.3f, 0.3f, 1), new Color(0.35f, 0.85f, 0.35f, 1), new Color(0.3f, 0.55f, 1f, 1)};
	private float gizmoLen;

	private boolean gizmoFrame() {
		Editor3DItem it = selected3D();
		if (it == null || "GRID".equals(mode())) return false;
		origin.set(it.px(), it.py(), it.pz());
		if (it.obj != null && it.obj.instance != null) it.obj.center(origin);
		gizmoLen = cam.position.dst(origin) * 0.18f;
		boolean local = (localAxes || "SCALE".equals(mode())) && it.obj != null;
		axes[0].set(1, 0, 0);
		axes[1].set(0, 1, 0);
		axes[2].set(0, 0, 1);
		if (local) for (Vector3 a : axes) a.mul(it.obj.rotation);
		return true;
	}

	private void drawGizmo() {
		if (!gizmoFrame()) return;
		String m = mode();
		Gdx.gl.glLineWidth(4f);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		for (int i = 0; i < 3; i++) {
			Color c = dragAxis == i ? Color.YELLOW : AXIS_COLORS[i];
			shapes.setColor(c);
			if ("ROTATE".equals(m)) {
				ring(origin, axes[i], gizmoLen * 0.9f, 40);
			} else {
				tmp.set(axes[i]).scl(gizmoLen).add(origin);
				shapes.line(origin, tmp);
				float h = gizmoLen * 0.08f;
				if ("SCALE".equals(m)) shapes.box(tmp.x - h, tmp.y - h, tmp.z + h, h * 2, h * 2, h * 2);
				else {
					// arrow head
					Vector3 side = tmp2.set(axes[i]).crs(Math.abs(axes[i].y) > 0.9f ? Vector3.X : Vector3.Y).nor().scl(h);
					tmp3.set(axes[i]).scl(-h * 2.2f).add(tmp);
					shapes.line(tmp, tmp3.cpy().add(side));
					shapes.line(tmp, tmp3.cpy().sub(side));
				}
			}
		}
		if (!"ROTATE".equals(m)) {
			shapes.setColor(dragAxis == 3 ? Color.YELLOW : Color.WHITE);
			float h = gizmoLen * 0.1f;
			shapes.box(origin.x - h, origin.y - h, origin.z + h, h * 2, h * 2, h * 2);
		}
		shapes.end();
		Gdx.gl.glLineWidth(1f);
	}

	private void ring(Vector3 c, Vector3 axis, float r, int seg) {
		Vector3 u = new Vector3(axis).crs(Math.abs(axis.y) > 0.9f ? Vector3.X : Vector3.Y).nor();
		Vector3 v = new Vector3(axis).crs(u).nor();
		Vector3 prev = null;
		for (int i = 0; i <= seg; i++) {
			float a = (float) i / seg * MathUtils.PI2;
			Vector3 p = new Vector3(u).scl(MathUtils.cos(a) * r).add(new Vector3(v).scl(MathUtils.sin(a) * r)).add(c);
			if (prev != null) shapes.line(prev, p);
			prev = p;
		}
	}

	// ------------------------------------------------------------------ touch

	private int dragAxis = -1; // 0..2 axis, 3 = center (free), -1 none
	private boolean orbiting, panning, moved;
	private float lastX, lastY, downX, downY, pinchDist = -1;
	private final Vector2 lastMid = new Vector2();
	private long lastTap;
	private String[] startTransform;
	private final Vector3 dragStartWorld = new Vector3(), startPos = new Vector3(), startScale = new Vector3(), startRot = new Vector3();
	private float accum;

	private Vector2 toScreen(Vector3 world, Vector2 out) {
		Vector3 p = tmp3.set(world);
		cam.project(p);
		return out.set(p.x, Gdx.graphics.getHeight() - p.y);
	}

	/** Distance (pixels) from point p to segment ab. */
	private static float segDist(Vector2 p, Vector2 a, Vector2 b) {
		return Intersector.distanceSegmentPoint(a, b, p);
	}

	private int hitGizmo(float x, float y) {
		if (!gizmoFrame()) return -1;
		String m = mode();
		Vector2 p = new Vector2(x, y), o = toScreen(origin, new Vector2());
		float tol = Math.max(28f, Gdx.graphics.getWidth() * 0.025f);
		if (!"ROTATE".equals(m) && p.dst(o) < tol * 1.1f) return 3;
		int best = -1;
		float bestD = tol;
		for (int i = 0; i < 3; i++) {
			if ("ROTATE".equals(m)) {
				// sample the ring
				Vector3 u = new Vector3(axes[i]).crs(Math.abs(axes[i].y) > 0.9f ? Vector3.X : Vector3.Y).nor();
				Vector3 v = new Vector3(axes[i]).crs(u).nor();
				for (int k = 0; k < 32; k++) {
					float a = (float) k / 32 * MathUtils.PI2;
					Vector3 w = new Vector3(u).scl(MathUtils.cos(a) * gizmoLen * 0.9f).add(new Vector3(v).scl(MathUtils.sin(a) * gizmoLen * 0.9f)).add(origin);
					float d = toScreen(w, new Vector2()).dst(p);
					if (d < bestD) {
						bestD = d;
						best = i;
					}
				}
			} else {
				Vector2 e = toScreen(new Vector3(axes[i]).scl(gizmoLen).add(origin), new Vector2());
				float d = segDist(p, o, e);
				if (d < bestD) {
					bestD = d;
					best = i;
				}
			}
		}
		return best;
	}

	public boolean touchDown(int x, int y, int pointer) {
		moved = false;
		if (pointer == 1) {
			// second finger: pan + pinch, cancel a gizmo drag
			cancelDrag();
			orbiting = false;
			panning = true;
			pinchDist = Vector2.dst(Gdx.input.getX(0), Gdx.input.getY(0), Gdx.input.getX(1), Gdx.input.getY(1));
			lastMid.set((Gdx.input.getX(0) + Gdx.input.getX(1)) * 0.5f, (Gdx.input.getY(0) + Gdx.input.getY(1)) * 0.5f);
			return true;
		}
		if (pointer > 1) return true;
		lastX = downX = x;
		lastY = downY = y;
		dragAxis = editor.isLocked3D() ? -1 : hitGizmo(x, y);
		if (dragAxis >= 0) beginDrag(x, y);
		else orbiting = true;
		return true;
	}

	public boolean touchDragged(int x, int y, int pointer) {
		if (Gdx.input.isTouched(1) && panning) {
			float d = Vector2.dst(Gdx.input.getX(0), Gdx.input.getY(0), Gdx.input.getX(1), Gdx.input.getY(1));
			if (pinchDist > 0 && d > 0) distance *= pinchDist / d;
			pinchDist = d;
			float mx = (Gdx.input.getX(0) + Gdx.input.getX(1)) * 0.5f, my = (Gdx.input.getY(0) + Gdx.input.getY(1)) * 0.5f;
			float k = distance / Math.max(1, Gdx.graphics.getHeight()) * 1.1f;
			Vector3 right = new Vector3(cam.direction).crs(cam.up).nor();
			Vector3 up = new Vector3(right).crs(cam.direction).nor();
			target.add(right.scl(-(mx - lastMid.x) * k)).add(up.scl((my - lastMid.y) * k));
			lastMid.set(mx, my);
			moved = true;
			updateCamera();
			return true;
		}
		if (pointer != 0) return true;
		float dx = x - lastX, dy = y - lastY;
		if (Math.abs(x - downX) > 8 || Math.abs(y - downY) > 8) moved = true;
		lastX = x;
		lastY = y;
		if (dragAxis >= 0) {
			drag(x, y, dx, dy);
			return true;
		}
		if (orbiting && moved) {
			yaw -= dx * 0.3f;
			pitch -= dy * 0.3f;
			updateCamera();
		}
		return true;
	}

	public boolean touchUp(int x, int y, int pointer) {
		if (pointer == 1 || panning) {
			if (!Gdx.input.isTouched()) panning = false;
			pinchDist = -1;
			return true;
		}
		if (dragAxis >= 0) {
			endDrag();
			dragAxis = -1;
			return true;
		}
		orbiting = false;
		if (!moved) {
			long now = System.currentTimeMillis();
			Actor hit = pick(x, y);
			if (now - lastTap < 320 && hit != null && hit == editor.getSelectedActor()) {
				focusSelected();
			} else if (hit != null) {
				editor.selectActor(hit);
			} else {
				editor.deselect3D();
			}
			lastTap = now;
		}
		return true;
	}

	/** Element under a screen point: lights / cameras icons first, then models. */
	public Actor pick(float x, float y) {
		Ray ray = cam.getPickRay(x, y);
		Actor best = null;
		float bestD = Float.MAX_VALUE;
		for (Actor a : editor.getActors()) {
			if (!(a instanceof Editor3DItem)) continue;
			Editor3DItem it = (Editor3DItem) a;
			if (it.light == null && it.camera == null && (it.obj == null || it.obj.instance != null)) continue;
			Vector3 p = tmp.set(it.px(), it.py(), it.pz());
			float s = iconSize(p.x, p.y, p.z) * 1.4f;
			if (Intersector.intersectRaySphere(ray, p, s, tmp2)) {
				float d = tmp2.dst2(ray.origin);
				if (d < bestD) {
					bestD = d;
					best = a;
				}
			}
		}
		if (best != null) return best;
		Object3D o = world.pickRay(ray, null);
		if (o == null) return null;
		for (Actor a : editor.getActors()) if (a instanceof Editor3DItem && ((Editor3DItem) a).obj == o) return a;
		return null;
	}

	private void beginDrag(float x, float y) {
		Editor3DItem it = selected3D();
		if (it == null) {
			dragAxis = -1;
			return;
		}
		PropertySet<String, Object> ps = it.getPropertySet();
		startTransform = transform(ps);
		startPos.set(ps.getFloat("x"), ps.getFloat("y"), ps.getFloat("z"));
		startRot.set(ps.getFloat("Rotation X"), ps.getFloat("Rotation Y"), ps.getFloat("Rotation Z"));
		startScale.set(ps.getFloat("Scale X"), ps.getFloat("Scale Y"), ps.getFloat("Scale Z"));
		accum = 0;
		groundPoint(x, y, startPos.y, dragStartWorld);
	}

	private boolean groundPoint(float x, float y, float height, Vector3 out) {
		Ray r = cam.getPickRay(x, y);
		Plane p = new Plane(Vector3.Y, -height);
		return Intersector.intersectRayPlane(r, p, out);
	}

	private void drag(float x, float y, float dx, float dy) {
		Editor3DItem it = selected3D();
		if (it == null) return;
		PropertySet<String, Object> ps = it.getPropertySet();
		String m = mode();
		if (dragAxis == 3) {
			if ("SCALE".equals(m)) {
				accum += (dx - dy) * 0.01f;
				float k = Math.max(0.01f, 1f + accum);
				put(ps, "Scale X", snapS(startScale.x * k));
				put(ps, "Scale Y", snapS(startScale.y * k));
				put(ps, "Scale Z", snapS(startScale.z * k));
			} else {
				Vector3 now = new Vector3();
				if (groundPoint(x, y, startPos.y, now)) {
					now.sub(dragStartWorld);
					put(ps, "x", snapM(startPos.x + now.x));
					put(ps, "z", snapM(startPos.z + now.z));
				}
			}
		} else {
			Vector3 axis = axes[dragAxis];
			Vector2 o = toScreen(origin, new Vector2()), e = toScreen(new Vector3(axis).scl(gizmoLen).add(origin), new Vector2());
			Vector2 sdir = new Vector2(e).sub(o);
			float slen = Math.max(1f, sdir.len());
			sdir.scl(1f / slen);
			float along = dx * sdir.x + dy * sdir.y; // pixels along the axis on screen
			if ("ROTATE".equals(m)) {
				accum += (dx - dy) * 0.5f;
				String key = dragAxis == 0 ? "Rotation X" : (dragAxis == 1 ? "Rotation Y" : "Rotation Z");
				float base = dragAxis == 0 ? startRot.x : (dragAxis == 1 ? startRot.y : startRot.z);
				put(ps, key, snapR(base + accum));
			} else if ("SCALE".equals(m)) {
				accum += along / slen;
				String key = dragAxis == 0 ? "Scale X" : (dragAxis == 1 ? "Scale Y" : "Scale Z");
				float base = dragAxis == 0 ? startScale.x : (dragAxis == 1 ? startScale.y : startScale.z);
				put(ps, key, snapS(base * Math.max(0.01f, 1f + accum)));
			} else {
				accum += along / slen * gizmoLen;
				Vector3 np = new Vector3(axis).scl(accum).add(startPos);
				put(ps, "x", snapM(np.x));
				put(ps, "y", snapM(np.y));
				put(ps, "z", snapM(np.z));
			}
		}
		it.update();
	}

	private float snapM(float v) {
		return snap ? MathUtils.round(v * 4f) / 4f : MathUtils.round(v * 1000f) / 1000f;
	}

	private float snapR(float v) {
		return snap ? MathUtils.round(v / 15f) * 15f : MathUtils.round(v * 10f) / 10f;
	}

	private float snapS(float v) {
		return snap ? Math.max(0.1f, MathUtils.round(v * 10f) / 10f) : MathUtils.round(v * 1000f) / 1000f;
	}

	private static void put(PropertySet<String, Object> ps, String k, float v) {
		ps.put(k, String.valueOf(v));
	}

	private static String[] transform(PropertySet<String, Object> ps) {
		return new String[]{"x", ps.getString("x"), "y", ps.getString("y"), "z", ps.getString("z"), "Rotation X", ps.getString("Rotation X"), "Rotation Y", ps.getString("Rotation Y"),
				"Rotation Z", ps.getString("Rotation Z"), "Scale X", ps.getString("Scale X"), "Scale Y", ps.getString("Scale Y"), "Scale Z", ps.getString("Scale Z")};
	}

	private void endDrag() {
		Editor3DItem it = selected3D();
		if (it == null || startTransform == null) return;
		String[] end = transform(it.getPropertySet());
		String m = mode();
		String msg = "ROTATE".equals(m) ? "rotation" : ("SCALE".equals(m) ? "scale" : "position");
		try {
			EditorAction a = EditorAction.propertiesChanged(editor, it.getName(), msg, end, startTransform);
			if (a != null) a.updateEditorProperties().updateItemProperties();
		} catch (Throwable ignored) {
		}
		editor.getSaveState();
		editor.afterEdit3D();
		startTransform = null;
	}

	private void cancelDrag() {
		if (dragAxis < 0) return;
		Editor3DItem it = selected3D();
		if (it != null && startTransform != null) {
			PropertySet<String, Object> ps = it.getPropertySet();
			for (int i = 0; i < startTransform.length; i += 2) ps.put(startTransform[i], startTransform[i + 1]);
			it.update();
		}
		dragAxis = -1;
		startTransform = null;
	}

	/** A point in front of the camera on the ground (new elements appear there). */
	public Vector3 spawnPoint() {
		Vector3 p = new Vector3(target);
		p.y = Math.max(0, p.y);
		return p;
	}

	public void dispose() {
		world.dispose();
		shapes.dispose();
	}

	/** Editor element of a 3D element by name (blocks / lists). */
	public static boolean is3DItem(Actor a) {
		return a instanceof Editor3DItem;
	}

	public static boolean is3DType(String type) {
		return "OBJ3D".equals(type) || "LIGHT3D".equals(type) || "CAMERA3D".equals(type);
	}

	static boolean isEditorItem(Actor a) {
		return a instanceof EditorItem;
	}
}
