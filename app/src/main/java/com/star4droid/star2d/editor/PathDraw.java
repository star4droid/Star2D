package com.star4droid.star2d.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.FloatArray;

/**
 * Drawing the points of a path on the canvas (2D, px) or on the ground of the 3D view (meters x, z): a patrol route for a
 * "path" variable. Tap = add a point at the end, drag a point = move it. The text form is "x1,y1,x2,y2..." (the value of the
 * variable). GL thread.
 */
public final class PathDraw implements CanvasTool {
	public interface Listener {
		/** The points changed (GL thread). */
		void changed(PathDraw p);
	}

	final LibgdxEditor editor;
	public final FloatArray pts = new FloatArray();
	public boolean navigate;
	/** Preview only: the walker goes back to the first point (LOOP mode). */
	public boolean loop;
	public String title = "";
	public Listener listener;
	private boolean on;
	private int dragIndex = -1;
	private boolean moved;
	private float downX, downY, startPX, startPY;
	private final FloatArray before = new FloatArray();
	/** Undo steps (each one a copy of the points). */
	private final java.util.ArrayList<float[]> undo = new java.util.ArrayList<float[]>();

	PathDraw(LibgdxEditor editor) {
		this.editor = editor;
	}

	@Override
	public boolean active() {
		return on;
	}

	@Override
	public boolean navigate() {
		return navigate;
	}

	@Override
	public float groundHeight() {
		return 0;
	}

	/** Starts with the points of a text "x1,y1,x2,y2..." (numbers only; anything else is ignored). */
	public void start(String text, String title) {
		pts.clear();
		undo.clear();
		this.title = title == null ? "" : title;
		if (text != null) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?").matcher(text);
			FloatArray v = new FloatArray();
			while (m.find()) {
				try {
					v.add(Float.parseFloat(m.group()));
				} catch (NumberFormatException ignored) {
				}
			}
			for (int i = 0; i + 1 < v.size; i += 2) pts.add(v.get(i), v.get(i + 1));
		}
		navigate = false;
		on = true;
		fire();
	}

	public void stop() {
		on = false;
		dragIndex = -1;
		fire();
	}

	/** The points as text (whole numbers in 2D, centimeters in 3D). */
	public String text() {
		StringBuilder sb = new StringBuilder();
		boolean d3 = editor.is3DScene();
		for (int i = 0; i < pts.size; i++) {
			if (i > 0) sb.append(',');
			float v = pts.get(i);
			if (d3) {
				float r = Math.round(v * 100) / 100f;
				sb.append(r == (int) r ? String.valueOf((int) r) : String.valueOf(r));
			} else sb.append(Math.round(v));
		}
		return sb.toString();
	}

	public int count() {
		return pts.size / 2;
	}

	private void fire() {
		if (listener != null) listener.changed(this);
	}

	private void push() {
		undo.add(pts.toArray());
		if (undo.size() > 100) undo.remove(0);
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public void undoStep() {
		if (undo.isEmpty()) return;
		float[] last = undo.remove(undo.size() - 1);
		pts.clear();
		pts.addAll(last);
		fire();
	}

	public void clear() {
		if (pts.size == 0) return;
		push();
		pts.clear();
		fire();
	}

	public void reverse() {
		if (pts.size < 4) return;
		push();
		FloatArray r = new FloatArray();
		for (int i = pts.size - 2; i >= 0; i -= 2) r.add(pts.get(i), pts.get(i + 1));
		pts.clear();
		pts.addAll(r);
		fire();
	}

	/** World units per screen pixel (touch tolerance, sizes). */
	private float unitsPerPixel() {
		if (editor.get3D() != null && !editor.get3D().edit2D) return editor.get3D().distance / Math.max(1, Gdx.graphics.getHeight()) * 1.2f;
		Camera c = editor.getCamera();
		return c instanceof OrthographicCamera ? ((OrthographicCamera) c).zoom * c.viewportWidth / Math.max(1, Gdx.graphics.getWidth()) : 1f;
	}

	private int nearest(float x, float y, float tol) {
		int best = -1;
		float bd = tol * tol;
		for (int i = 0; i + 1 < pts.size; i += 2) {
			float dx = pts.get(i) - x, dy = pts.get(i + 1) - y, d = dx * dx + dy * dy;
			if (d <= bd) {
				bd = d;
				best = i / 2;
			}
		}
		return best;
	}

	@Override
	public void down(float x, float y) {
		if (!on) return;
		moved = false;
		downX = x;
		downY = y;
		before.clear();
		before.addAll(pts);
		dragIndex = nearest(x, y, unitsPerPixel() * 28f);
		if (dragIndex >= 0) {
			startPX = pts.get(dragIndex * 2);
			startPY = pts.get(dragIndex * 2 + 1);
		}
	}

	@Override
	public void drag(float x, float y) {
		if (!on) return;
		float tol = unitsPerPixel() * 10f;
		if (Math.abs(x - downX) > tol || Math.abs(y - downY) > tol) moved = true;
		if (dragIndex >= 0 && moved) {
			pts.set(dragIndex * 2, startPX + x - downX);
			pts.set(dragIndex * 2 + 1, startPY + y - downY);
		}
	}

	@Override
	public void up(float x, float y) {
		if (!on) return;
		if (dragIndex >= 0 && moved) {
			undo.add(before.toArray());
		} else if (!moved) {
			// a tap: a new point at the end (a tap on the last point removes nothing: drag it instead)
			push();
			pts.add(x, y);
		}
		dragIndex = -1;
		moved = false;
		fire();
	}

	@Override
	public void cancel() {
		if (dragIndex >= 0) {
			pts.clear();
			pts.addAll(before);
		}
		dragIndex = -1;
		moved = false;
	}

	// ------------------------------------------------------------------ drawing

	/** 2D canvas: the route, its points (first green, last red) and arrows showing the walking direction. */
	public void draw2D(ShapeRenderer sr, OrthographicCamera cam) {
		if (!on) return;
		sr.setProjectionMatrix(cam.combined);
		draw(sr, unitsPerPixel());
	}

	/** 3D view: on the ground plane (y = 0). */
	public void draw3D(ShapeRenderer sr, Camera cam) {
		if (!on || (editor.get3D() != null && editor.get3D().edit2D)) return;
		sr.setProjectionMatrix(cam.combined);
		sr.identity();
		sr.translate(0, groundHeight() + 0.03f, 0);
		sr.rotate(1, 0, 0, 90);
		Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
		draw(sr, unitsPerPixel());
		sr.identity();
		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
	}

	private void draw(ShapeRenderer sr, float upp) {
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		int n = count();
		float r = upp * 9f;
		Gdx.gl.glLineWidth(4f);
		sr.begin(ShapeRenderer.ShapeType.Line);
		sr.setColor(1f, 0.85f, 0.2f, 0.95f);
		for (int i = 0; i + 1 < n; i++) {
			float ax = pts.get(i * 2), ay = pts.get(i * 2 + 1), bx = pts.get(i * 2 + 2), by = pts.get(i * 2 + 3);
			sr.line(ax, ay, bx, by);
			arrow(sr, ax, ay, bx, by, upp * 12f);
		}
		if (loop && n > 2) {
			sr.setColor(1f, 0.85f, 0.2f, 0.4f);
			sr.line(pts.get(n * 2 - 2), pts.get(n * 2 - 1), pts.get(0), pts.get(1));
		}
		sr.end();
		Gdx.gl.glLineWidth(1f);
		sr.begin(ShapeRenderer.ShapeType.Filled);
		for (int i = 0; i < n; i++) {
			if (i == 0) sr.setColor(0.3f, 0.9f, 0.4f, 1f);
			else if (i == n - 1) sr.setColor(0.95f, 0.3f, 0.3f, 1f);
			else sr.setColor(1f, 0.85f, 0.2f, 1f);
			sr.circle(pts.get(i * 2), pts.get(i * 2 + 1), r * (i == dragIndex ? 1.5f : 1f), 20);
		}
		sr.end();
	}

	/** Arrow head in the middle of a segment. */
	private static void arrow(ShapeRenderer sr, float ax, float ay, float bx, float by, float size) {
		float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < size * 2.5f) return;
		dx /= len;
		dy /= len;
		float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f;
		float px = -dy, py = dx;
		sr.line(mx + dx * size * 0.5f, my + dy * size * 0.5f, mx - dx * size * 0.5f + px * size * 0.6f, my - dy * size * 0.5f + py * size * 0.6f);
		sr.line(mx + dx * size * 0.5f, my + dy * size * 0.5f, mx - dx * size * 0.5f - px * size * 0.6f, my - dy * size * 0.5f - py * size * 0.6f);
	}
}
