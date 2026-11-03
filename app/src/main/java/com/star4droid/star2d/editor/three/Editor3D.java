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
	/** While editing the 2D layer: draw the 3D picture behind it (off = a normal 2D canvas with grid and frame). */
	public boolean show3DBehind = false;
	/** Draw the collider of the selected element (saved for the editor). */
	public static boolean showColliders = true;
	/** Collider editor on: handles on the collider faces instead of the gizmo. */
	public boolean colliderEdit = false;
	private Actor colliderTarget;
	private final ShapeRenderer screen = new ShapeRenderer();
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
		try {
			showColliders = Gdx.app.getPreferences("star2d_editor3d").getBoolean("showColliders", true);
		} catch (Throwable ignored) {
		}
		updateCamera();
	}

	public static void setShowColliders(boolean on) {
		showColliders = on;
		try {
			com.badlogic.gdx.Preferences p = Gdx.app.getPreferences("star2d_editor3d");
			p.putBoolean("showColliders", on);
			p.flush();
		} catch (Throwable ignored) {
		}
	}

	/** Switches between editing the 3D world and the 2D layer (HUD / UI); a selected element of the other side is released. */
	public void setEdit2D(boolean on) {
		if (edit2D == on) return;
		edit2D = on;
		colliderEdit = false;
		Actor sel = editor.getSelectedActor();
		if (sel != null && ((sel instanceof Editor3DItem) == on)) editor.deselect3D();
	}

	/** Turns the collider editor on / off for the selected element; false when it has no collider. */
	public boolean toggleColliderEdit() {
		Editor3DItem it = selected3D();
		if (colliderEdit || it == null || !hasCollider(it)) {
			colliderEdit = false;
			return false;
		}
		colliderEdit = true;
		colliderTarget = it;
		return true;
	}

	public static boolean hasCollider(Editor3DItem it) {
		return it != null && it.obj != null && it.obj.getDef() != null && it.obj.getDef().Body != null
				&& !"NONE".equalsIgnoreCase(it.obj.getDef().Body) && it.obj.getDef().Mesh != null && !"EMPTY".equalsIgnoreCase(it.obj.getDef().Mesh);
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
		Object[] gameQuality = Quality3D.snapshot();
		Quality3D.setLevel(editorQuality, false);
		Quality3D.renderScale = 1f;
		for (Actor a : editor.getActors()) {
			if (a instanceof Editor3DItem) {
				Editor3DItem it = (Editor3DItem) a;
				if (it.obj != null) it.obj.selected = (a == editor.getSelectedActor());
			}
		}
		world.camera = cam;
		world.update(dt, false);
		world.render(cam, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
		Quality3D.restore(gameQuality);
		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		shapes.setProjectionMatrix(cam.combined);
		if (showGrid) drawGrid();
		drawIcons();
		Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
		Editor3DItem sel = selected3D();
		if (colliderEdit && (sel == null || sel != colliderTarget || !hasCollider(sel))) colliderEdit = false;
		if ((showColliders || colliderEdit) && hasCollider(sel)) drawCollider(sel);
		if (colliderEdit) drawColliderHandles(sel);
		else drawGizmo();
		Gdx.gl.glEnable(GL20.GL_BLEND);
	}

	// ------------------------------------------------------------------ collider

	private static final Color COLLIDER = new Color(0.3f, 1f, 0.55f, 0.95f);
	private static final Color HANDLE_FILL = new Color(1f, 0.6f, 0.1f, 1f);
	private final Vector3 cHalf = new Vector3(), cCenter = new Vector3(), cw = new Vector3(), cw2 = new Vector3();

	/** Local collider point (meters, rotated with the element) to world. */
	private Vector3 cwp(Editor3DItem it, float x, float y, float z, Vector3 out) {
		return out.set(x, y, z).mul(it.obj.rotation).add(it.obj.position);
	}

	private void cline(Editor3DItem it, float x1, float y1, float z1, float x2, float y2, float z2) {
		shapes.line(cwp(it, x1, y1, z1, cw), cwp(it, x2, y2, z2, cw2));
	}

	/** Ring around the local Y axis (axis 1), X axis (0) or Z axis (2). */
	private void cring(Editor3DItem it, float cx, float cy, float cz, float r, int axis, float from, float to) {
		int seg = 28;
		float px = 0, py = 0, pz = 0;
		for (int i = 0; i <= seg; i++) {
			float a = from + (to - from) * i / seg;
			float u = MathUtils.cos(a) * r, v = MathUtils.sin(a) * r;
			float x = cx, y = cy, z = cz;
			if (axis == 1) { x += u; z += v; } else if (axis == 0) { y += v; z += u; } else { x += u; y += v; }
			if (i > 0) cline(it, px, py, pz, x, y, z);
			px = x; py = y; pz = z;
		}
	}

	private void drawCollider(Editor3DItem it) {
		it.obj.colliderBox(cHalf, cCenter);
		String k = it.obj.colliderKind();
		float hx = cHalf.x, hy = cHalf.y, hz = cHalf.z, cx = cCenter.x, cy = cCenter.y, cz = cCenter.z;
		Gdx.gl.glLineWidth(2.5f);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		shapes.setColor(COLLIDER);
		if ("SPHERE".equals(k)) {
			float r = Math.max(hx, Math.max(hy, hz));
			cring(it, cx, cy, cz, r, 0, 0, MathUtils.PI2);
			cring(it, cx, cy, cz, r, 1, 0, MathUtils.PI2);
			cring(it, cx, cy, cz, r, 2, 0, MathUtils.PI2);
		} else if ("CAPSULE".equals(k) || "CYLINDER".equals(k) || "CONE".equals(k)) {
			float r = "CYLINDER".equals(k) ? hx : Math.max(hx, hz);
			float h = "CAPSULE".equals(k) ? Math.max(0.005f, hy - r) : hy;
			if ("CONE".equals(k)) {
				cring(it, cx, cy - h, cz, r, 1, 0, MathUtils.PI2);
				cline(it, cx + r, cy - h, cz, cx, cy + h, cz);
				cline(it, cx - r, cy - h, cz, cx, cy + h, cz);
				cline(it, cx, cy - h, cz + r, cx, cy + h, cz);
				cline(it, cx, cy - h, cz - r, cx, cy + h, cz);
			} else {
				cring(it, cx, cy - h, cz, r, 1, 0, MathUtils.PI2);
				cring(it, cx, cy + h, cz, r, 1, 0, MathUtils.PI2);
				cline(it, cx + r, cy - h, cz, cx + r, cy + h, cz);
				cline(it, cx - r, cy - h, cz, cx - r, cy + h, cz);
				cline(it, cx, cy - h, cz + r, cx, cy + h, cz + r);
				cline(it, cx, cy - h, cz - r, cx, cy + h, cz - r);
				if ("CAPSULE".equals(k)) {
					cring(it, cx, cy + h, cz, r, 2, 0, MathUtils.PI);
					cring(it, cx, cy - h, cz, r, 2, MathUtils.PI, MathUtils.PI2);
					cring(it, cx, cy + h, cz, r, 0, 0, MathUtils.PI);
					cring(it, cx, cy - h, cz, r, 0, MathUtils.PI, MathUtils.PI2);
				}
			}
		} else {
			// BOX, and the bounds of MESH / CONVEX colliders (they follow the model)
			if (!"BOX".equals(k)) shapes.setColor(COLLIDER.r, COLLIDER.g, COLLIDER.b, 0.55f);
			for (int sy = -1; sy <= 1; sy += 2) {
				cline(it, cx - hx, cy + sy * hy, cz - hz, cx + hx, cy + sy * hy, cz - hz);
				cline(it, cx + hx, cy + sy * hy, cz - hz, cx + hx, cy + sy * hy, cz + hz);
				cline(it, cx + hx, cy + sy * hy, cz + hz, cx - hx, cy + sy * hy, cz + hz);
				cline(it, cx - hx, cy + sy * hy, cz + hz, cx - hx, cy + sy * hy, cz - hz);
			}
			cline(it, cx - hx, cy - hy, cz - hz, cx - hx, cy + hy, cz - hz);
			cline(it, cx + hx, cy - hy, cz - hz, cx + hx, cy + hy, cz - hz);
			cline(it, cx + hx, cy - hy, cz + hz, cx + hx, cy + hy, cz + hz);
			cline(it, cx - hx, cy - hy, cz + hz, cx - hx, cy + hy, cz + hz);
		}
		shapes.end();
		Gdx.gl.glLineWidth(1f);
	}

	/** Handle i (0..5 = +X -X +Y -Y +Z -Z) of the collider, local point. */
	private boolean handleLocal(Editor3DItem it, int i, Vector3 out) {
		it.obj.colliderBox(cHalf, cCenter);
		String k = it.obj.colliderKind();
		float hx = cHalf.x, hy = cHalf.y, hz = cHalf.z;
		if ("SPHERE".equals(k)) hx = hy = hz = Math.max(hx, Math.max(hy, hz));
		else if ("CYLINDER".equals(k)) hz = hx;
		else if ("CAPSULE".equals(k) || "CONE".equals(k)) hx = hz = Math.max(hx, hz);
		int axis = i / 2;
		float sign = i % 2 == 0 ? 1 : -1;
		out.set(cCenter);
		if (axis == 0) out.x += sign * hx;
		else if (axis == 1) out.y += sign * hy;
		else out.z += sign * hz;
		return true;
	}

	private int dragHandle = -1;
	private String[] startCollider;
	private float handleAccum;

	private float handleRadius() {
		return Math.max(16f, Gdx.graphics.getWidth() * 0.016f);
	}

	private void drawColliderHandles(Editor3DItem it) {
		screen.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		float r = handleRadius();
		Vector3 p = new Vector3();
		for (int pass = 0; pass < 2; pass++) {
			screen.begin(ShapeRenderer.ShapeType.Filled);
			for (int i = 0; i < 6; i++) {
				handleLocal(it, i, p);
				cwp(it, p.x, p.y, p.z, p);
				cam.project(p);
				if (p.z < 0 || p.z > 1) continue;
				if (pass == 0) {
					// stroke: a white ring around the colored center
					screen.setColor(Color.WHITE);
					screen.circle(p.x, p.y, r + 4f, 24);
				} else {
					screen.setColor(dragHandle == i ? Color.YELLOW : (i / 2 == 0 ? AXIS_COLORS[0] : (i / 2 == 1 ? AXIS_COLORS[1] : AXIS_COLORS[2])));
					screen.circle(p.x, p.y, r, 24);
					screen.setColor(HANDLE_FILL);
					screen.circle(p.x, p.y, r * 0.45f, 16);
				}
			}
			screen.end();
		}
	}

	private int hitHandle(float x, float y) {
		Editor3DItem it = selected3D();
		if (!colliderEdit || it == null || !hasCollider(it)) return -1;
		Vector3 p = new Vector3();
		float best = handleRadius() * 1.9f;
		int hit = -1;
		float sy = Gdx.graphics.getHeight() - y;
		for (int i = 0; i < 6; i++) {
			handleLocal(it, i, p);
			cwp(it, p.x, p.y, p.z, p);
			cam.project(p);
			if (p.z < 0 || p.z > 1) continue;
			float d = Vector2.dst(p.x, p.y, x, sy);
			if (d < best) {
				best = d;
				hit = i;
			}
		}
		return hit;
	}

	private static final String[] COLLIDER_KEYS = {"Collider Scale X", "Collider Scale Y", "Collider Scale Z", "Collider Offset X", "Collider Offset Y", "Collider Offset Z"};

	private static String[] colliderState(PropertySet<String, Object> ps) {
		String[] r = new String[COLLIDER_KEYS.length * 2];
		for (int i = 0; i < COLLIDER_KEYS.length; i++) {
			r[i * 2] = COLLIDER_KEYS[i];
			r[i * 2 + 1] = ps.getString(COLLIDER_KEYS[i]);
		}
		return r;
	}

	/** Moves handle h of the collider by d meters outwards (keeps the opposite face where it is). */
	private void moveHandle(Editor3DItem it, int h, float d) {
		if (Math.abs(d) < 1e-5f) return;
		com.star4droid.star2d.ElementDefs.Object3DDef def = it.obj.getDef();
		PropertySet<String, Object> ps = it.getPropertySet();
		String k = it.obj.colliderKind();
		com.badlogic.gdx.math.collision.BoundingBox bb = it.obj.localBounds();
		Vector3 bcv = bb.getCenter(new Vector3());
		float[] size = {bb.getWidth(), bb.getHeight(), bb.getDepth()};
		float[] bc = {bcv.x, bcv.y, bcv.z};
		float[] sc = {Math.max(1e-4f, Math.abs(it.obj.scale.x)), Math.max(1e-4f, Math.abs(it.obj.scale.y)), Math.max(1e-4f, Math.abs(it.obj.scale.z))};
		float[] cs = {def.Collider_Scale_X, def.Collider_Scale_Y, def.Collider_Scale_Z};
		float[] off = {def.Collider_Offset_X, def.Collider_Offset_Y, def.Collider_Offset_Z};
		it.obj.colliderBox(cHalf, cCenter);
		float[] half = {cHalf.x, cHalf.y, cHalf.z};
		float[] cen = {cCenter.x, cCenter.y, cCenter.z};
		int axis = h / 2;
		float sign = h % 2 == 0 ? 1 : -1;
		boolean mesh = "MESH".equals(k) || "CONVEX".equals(k);
		boolean round = "SPHERE".equals(k) || (axis != 1 && ("CAPSULE".equals(k) || "CYLINDER".equals(k) || "CONE".equals(k)));
		if (round) {
			// radius: all axes of a sphere, X and Z of a capsule / cylinder / cone, centered
			float r = "SPHERE".equals(k) ? Math.max(half[0], Math.max(half[1], half[2])) : ("CYLINDER".equals(k) ? half[0] : Math.max(half[0], half[2]));
			float nr = Math.max(0.01f, r + d);
			float f = nr / Math.max(1e-4f, r);
			for (int a = 0; a < 3; a++) {
				if (!"SPHERE".equals(k) && a == 1) continue;
				cs[a] = Math.max(0.001f, cs[a] * f);
			}
		} else if (mesh) {
			float nh = Math.max(0.005f, half[axis] + d);
			cs[axis] = Math.max(0.001f, cs[axis] * nh / Math.max(1e-4f, half[axis]));
		} else {
			float nh = Math.max(0.005f, half[axis] + d * 0.5f);
			cen[axis] += sign * (nh - half[axis]);
			cs[axis] = Math.max(0.001f, 2f * nh / Math.max(1e-4f, size[axis] * sc[axis]));
		}
		if (!mesh) for (int a = 0; a < 3; a++) off[a] = cen[a] - bc[a] * sc[a] * cs[a];
		for (int a = 0; a < 3; a++) {
			ps.put(COLLIDER_KEYS[a], String.valueOf(MathUtils.round(cs[a] * 1000f) / 1000f));
			ps.put(COLLIDER_KEYS[a + 3], String.valueOf(MathUtils.round(off[a] * 1000f) / 1000f));
		}
		it.update();
	}

	private void dragHandleBy(float dx, float dy) {
		Editor3DItem it = selected3D();
		if (it == null) return;
		Vector3 a = new Vector3(), b = new Vector3();
		handleLocal(it, dragHandle, a);
		b.set(a);
		int axis = dragHandle / 2;
		float sign = dragHandle % 2 == 0 ? 1 : -1;
		if (axis == 0) b.x += sign; else if (axis == 1) b.y += sign; else b.z += sign;
		cwp(it, a.x, a.y, a.z, a);
		cwp(it, b.x, b.y, b.z, b);
		Vector2 sa = toScreen(a, new Vector2()), sb = toScreen(b, new Vector2());
		Vector2 dir = new Vector2(sb).sub(sa);
		float pxPerMeter = Math.max(2f, dir.len());
		dir.nor();
		float meters = (dx * dir.x + dy * dir.y) / pxPerMeter;
		if (snap) {
			handleAccum += meters;
			float q = MathUtils.round(handleAccum * 10f) / 10f;
			if (q == 0) return;
			handleAccum -= q;
			meters = q;
		}
		moveHandle(it, dragHandle, meters);
	}

	private void endHandleDrag() {
		Editor3DItem it = selected3D();
		if (it != null && startCollider != null) {
			try {
				EditorAction a = EditorAction.propertiesChanged(editor, it.getName(), "collider", colliderState(it.getPropertySet()), startCollider);
				if (a != null) a.updateEditorProperties().updateItemProperties();
			} catch (Throwable ignored) {
			}
			editor.getSaveState();
			editor.afterEdit3D();
		}
		startCollider = null;
		dragHandle = -1;
	}

	private void drawGrid() {
		float step = distance > 60 ? 10 : (distance > 25 ? 2 : 1);
		int n = 40;
		float cx = MathUtils.round(target.x / step) * step, cz = MathUtils.round(target.z / step) * step;
		// a little above y = 0 and without depth writes: the lines no longer fight with a ground / road at 0
		float gy = Math.min(0.05f, 0.0015f * distance);
		shapes.translate(0, gy, 0);
		Gdx.gl.glDepthMask(false);
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
		shapes.translate(0, -gy, 0);
		Gdx.gl.glDepthMask(true);
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
			if (dragHandle >= 0) endHandleDrag();
			orbiting = false;
			panning = true;
			pinchDist = Vector2.dst(Gdx.input.getX(0), Gdx.input.getY(0), Gdx.input.getX(1), Gdx.input.getY(1));
			lastMid.set((Gdx.input.getX(0) + Gdx.input.getX(1)) * 0.5f, (Gdx.input.getY(0) + Gdx.input.getY(1)) * 0.5f);
			return true;
		}
		if (pointer > 1) return true;
		lastX = downX = x;
		lastY = downY = y;
		dragHandle = editor.isLocked3D() ? -1 : hitHandle(x, y);
		if (dragHandle >= 0) {
			startCollider = colliderState(selected3D().getPropertySet());
			handleAccum = 0;
			dragAxis = -1;
			return true;
		}
		dragAxis = editor.isLocked3D() || colliderEdit ? -1 : hitGizmo(x, y);
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
		if (dragHandle >= 0) {
			dragHandleBy(dx, dy);
			return true;
		}
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
		if (dragHandle >= 0) {
			endHandleDrag();
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
		screen.dispose();
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
