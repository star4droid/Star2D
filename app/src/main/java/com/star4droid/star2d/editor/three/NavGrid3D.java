package com.star4droid.star2d.editor.three;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.NavPaint;
import com.star4droid.star2d.editor.items.EditorNavGridItem;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.nav.NavGrid;

/**
 * NavGrid elements in the 3D view: drawn on the ground (outline always, cells when selected or painted), selected by a tap
 * on their border or corner flag, moved on the ground by dragging inside the selected grid (MOVE mode), painted with
 * {@link NavPaint}.
 */
final class NavGrid3D {
	private final Editor3D e;
	private final Vector3 tmp = new Vector3(), tmp2 = new Vector3();
	private final Vector2 g2 = new Vector2(), s1 = new Vector2(), s2 = new Vector2();
	/** 1 = a canvas tool (painting, path drawing), 2 = moving the selected grid, 0 = idle. */
	private int mode;
	private float startX, startZ, grabX, grabZ, lastGX, lastGZ;

	NavGrid3D(Editor3D e) {
		this.e = e;
	}

	private NavPaint paint() {
		return e.editor.navPaint;
	}

	private com.star4droid.star2d.editor.CanvasTool tool() {
		return e.editor.activeTool();
	}

	boolean busy() {
		return mode != 0;
	}

	// ------------------------------------------------------------------ draw

	void draw(ShapeRenderer shapes) {
		EditorNavGridItem painted = paint().active() ? paint().item : null;
		boolean any = false;
		for (Actor a : e.editor.getActors()) if (a instanceof EditorNavGridItem) any = true;
		if (!any) return;
		Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		for (Actor a : e.editor.getActors()) {
			if (!(a instanceof EditorNavGridItem) || !a.isVisible()) continue;
			EditorNavGridItem it = (EditorNavGridItem) a;
			NavGrid g = it.grid();
			if (g == null) continue;
			boolean sel = a == e.editor.getSelectedActor() || a == painted;
			float lift = Math.min(0.06f, 0.002f * e.distance) + 0.01f;
			// grid units (x, y) on the ground plane: x -> x, y -> z (rotate the XY plane onto XZ at the ground height)
			shapes.identity();
			shapes.translate(0, g.groundY + lift, 0);
			shapes.rotate(1, 0, 0, 90);
			if (sel) {
				float upp = e.distance / Math.max(1, Gdx.graphics.getHeight()) * 1.2f;
				float r = e.distance * 2.5f;
				EditorNavGridItem.drawCells(shapes, g, 1f, e.target.x - r, e.target.z - r, e.target.x + r, e.target.z + r, upp);
			}
			Gdx.gl.glLineWidth(sel ? 3f : 2f);
			shapes.begin(ShapeRenderer.ShapeType.Line);
			shapes.setColor(0.35f, 0.85f, 1f, sel ? 1f : 0.6f);
			shapes.rect(g.x0, g.y0, g.areaWidth(), g.areaHeight());
			if (!Float.isNaN(it.cursorX)) {
				shapes.setColor(1f, 1f, 1f, 0.9f);
				shapes.circle(it.cursorX, it.cursorY, Math.max(it.cursorR, 0.05f), 40);
			}
			if (!Float.isNaN(it.boxX0)) {
				shapes.setColor(1f, 0.85f, 0.2f, 1f);
				shapes.rect(Math.min(it.boxX0, it.boxX1), Math.min(it.boxY0, it.boxY1), Math.abs(it.boxX1 - it.boxX0), Math.abs(it.boxY1 - it.boxY0));
			}
			shapes.end();
			Gdx.gl.glLineWidth(1f);
			shapes.identity();
			// a flag at the corner: a post with a little square (tap it to select)
			float s = flag(g);
			shapes.begin(ShapeRenderer.ShapeType.Line);
			shapes.setColor(sel ? 1f : 0.35f, sel ? 1f : 0.85f, 1f, 1f);
			shapes.line(g.x0, g.groundY, g.y0, g.x0, g.groundY + s * 2.2f, g.y0);
			shapes.end();
			shapes.begin(ShapeRenderer.ShapeType.Filled);
			shapes.setColor(0.15f, sel ? 0.6f : 0.45f, sel ? 0.85f : 0.65f, 0.95f);
			shapes.box(g.x0, g.groundY + s * 1.4f, g.y0 + 0.01f, s, s * 0.8f, 0.02f);
			shapes.end();
		}
		shapes.identity();
		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
	}

	/** Size of the corner flag (meters), constant on screen. */
	private float flag(NavGrid g) {
		return Math.max(0.05f, e.cam.position.dst(g.x0, g.groundY, g.y0) * 0.035f);
	}

	// ------------------------------------------------------------------ pick

	/** A NavGrid whose border or flag is under the screen point, else null. */
	Actor pick(float x, float y) {
		float tol = Math.max(24f, Gdx.graphics.getWidth() * 0.022f);
		Actor best = null;
		float bestD = tol;
		Vector2 p = new Vector2(x, y);
		for (Actor a : e.editor.getActors()) {
			if (!(a instanceof EditorNavGridItem) || !a.isVisible()) continue;
			NavGrid g = ((EditorNavGridItem) a).grid();
			if (g == null) continue;
			float y0 = g.groundY, x0 = g.x0, z0 = g.y0, x1 = g.x0 + g.areaWidth(), z1 = g.y0 + g.areaHeight();
			float[][] c = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
			float d = Float.MAX_VALUE;
			for (int i = 0; i < 4; i++) {
				int j = (i + 1) % 4;
				if (!screen(c[i][0], y0, c[i][1], s1) || !screen(c[j][0], y0, c[j][1], s2)) continue;
				d = Math.min(d, Intersector.distanceSegmentPoint(s1, s2, p));
			}
			// the flag counts a little more than the border (bigger target)
			float s = flag(g);
			if (screen(x0 + s * 0.5f, y0 + s * 1.8f, z0, s1)) d = Math.min(d, s1.dst(p) * 0.66f);
			if (d < bestD) {
				bestD = d;
				best = a;
			}
		}
		return best;
	}

	/** Screen point (y down) of a world point; false behind the camera. */
	private boolean screen(float x, float y, float z, Vector2 out) {
		tmp.set(x, y, z);
		tmp2.set(tmp).sub(e.cam.position);
		if (tmp2.dot(e.cam.direction) <= 0) return false;
		e.cam.project(tmp);
		out.set(tmp.x, Gdx.graphics.getHeight() - tmp.y);
		return true;
	}

	// ------------------------------------------------------------------ touch

	private boolean ground(float sx, float sy, float height, Vector2 out) {
		Ray r = e.cam.getPickRay(sx, sy);
		if (!Intersector.intersectRayPlane(r, new Plane(Vector3.Y, -height), tmp)) return false;
		out.set(tmp.x, tmp.z);
		return true;
	}

	/** First finger down: paints, or grabs the selected grid to move it. True when it took the touch. */
	boolean touchDown(int x, int y) {
		mode = 0;
		com.star4droid.star2d.editor.CanvasTool t = tool();
		if (t != null) {
			if (t.navigate()) return false;
			if (!ground(x, y, t.groundHeight(), g2)) return true;
			lastGX = g2.x;
			lastGZ = g2.y;
			t.down(g2.x, g2.y);
			mode = 1;
			return true;
		}
		Actor sel = e.editor.getSelectedActor();
		if (!(sel instanceof EditorNavGridItem) || !"MOVE".equals(e.editor.getTouchModeName()) || e.editor.isLocked3D()) return false;
		EditorNavGridItem it = (EditorNavGridItem) sel;
		NavGrid g = it.grid();
		if (g == null || !ground(x, y, g.groundY, g2)) return false;
		if (g2.x < g.x0 || g2.y < g.y0 || g2.x > g.x0 + g.areaWidth() || g2.y > g.y0 + g.areaHeight()) return false;
		PropertySet<String, Object> ps = it.getPropertySet();
		startX = ps.getFloat("x");
		startZ = ps.getFloat("z");
		grabX = g2.x;
		grabZ = g2.y;
		mode = 2;
		return true;
	}

	void touchDragged(int x, int y) {
		if (mode == 1) {
			com.star4droid.star2d.editor.CanvasTool t = tool();
			if (t != null && ground(x, y, t.groundHeight(), g2)) {
				lastGX = g2.x;
				lastGZ = g2.y;
				t.drag(g2.x, g2.y);
			}
		} else if (mode == 2) {
			Actor sel = e.editor.getSelectedActor();
			if (!(sel instanceof EditorNavGridItem)) return;
			EditorNavGridItem it = (EditorNavGridItem) sel;
			if (!ground(x, y, it.groundY(), g2)) return;
			PropertySet<String, Object> ps = it.getPropertySet();
			float nx = startX + g2.x - grabX, nz = startZ + g2.y - grabZ;
			if (e.snap) {
				nx = MathUtils.round(nx * 4f) / 4f;
				nz = MathUtils.round(nz * 4f) / 4f;
			}
			ps.put("x", String.valueOf(MathUtils.round(nx * 1000f) / 1000f));
			ps.put("z", String.valueOf(MathUtils.round(nz * 1000f) / 1000f));
		}
	}

	void touchUp() {
		if (mode == 1) {
			com.star4droid.star2d.editor.CanvasTool t = tool();
			if (t != null) t.up(lastGX, lastGZ);
		} else if (mode == 2) {
			Actor sel = e.editor.getSelectedActor();
			if (sel instanceof EditorNavGridItem) {
				PropertySet<String, Object> ps = ((EditorNavGridItem) sel).getPropertySet();
				EditorAction a = EditorAction.propertiesChanged(e.editor, sel.getName(), "position", new String[]{"x", ps.getString("x"), "z", ps.getString("z")},
						new String[]{"x", String.valueOf(startX), "z", String.valueOf(startZ)});
				if (a != null) a.updateEditorProperties().updateItemProperties();
				e.editor.getSaveState();
				e.editor.afterEdit3D();
			}
		}
		mode = 0;
	}

	/** Second finger: the stroke / move stops (the view moves instead). */
	void cancel() {
		if (mode == 1) {
			com.star4droid.star2d.editor.CanvasTool t = tool();
			if (t != null) t.cancel();
		} else if (mode == 2) {
			Actor sel = e.editor.getSelectedActor();
			if (sel instanceof EditorNavGridItem) {
				PropertySet<String, Object> ps = ((EditorNavGridItem) sel).getPropertySet();
				ps.put("x", String.valueOf(startX));
				ps.put("z", String.valueOf(startZ));
			}
		}
		mode = 0;
	}
}
