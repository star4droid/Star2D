package com.star4droid.star2d.editor;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.IntArray;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.items.EditorNavGridItem;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.nav.NavGrid;
import com.star4droid.template.nav.NavShapes;

/**
 * Painting the cells of a NavGrid on the canvas (2D) or on the ground of the 3D view: walls, open cells, slow cells and
 * boxes. Every stroke is one undo step of the editor (the "Cells" property). GL thread.
 */
public final class NavPaint implements CanvasTool {
	public static final int WALL = 0, ERASE = 1, SLOW = 2, BOX_WALL = 3, BOX_ERASE = 4;

	public interface Listener {
		/** The session changed (stroke done, element gone...). GL thread. */
		void changed(NavPaint p);
	}

	final LibgdxEditor editor;
	/** The grid being painted, null when the painter is off. */
	public EditorNavGridItem item;
	public int tool = WALL;
	/** Brush radius in cells (0 = only the cell under the finger). */
	public int brush = 0;
	/** Cost painted by the SLOW tool (2..9). */
	public int cost = 3;
	/** One finger moves the view instead of painting. */
	public boolean navigate;
	public Listener listener;
	/** Undo steps made by this painting session / undone ones (the painter's undo and redo buttons). */
	public int strokes, undone;

	private boolean stroking;
	private String before = "";
	private float lastX = Float.NaN, lastY;

	NavPaint(LibgdxEditor editor) {
		this.editor = editor;
	}

	@Override
	public boolean navigate() {
		return navigate;
	}

	@Override
	public float groundHeight() {
		return item == null ? 0 : item.groundY();
	}

	@Override
	public void up(float x, float y) {
		up();
	}

	@Override
	public boolean active() {
		if (item != null && item.getStage() == null) {
			// deleted (or undone) while painting
			item = null;
			stroking = false;
			fire();
		}
		return item != null;
	}

	/** Starts painting a grid (it becomes the selected element). Error text or null. */
	public String start(EditorNavGridItem it) {
		if (it == null) return com.star4droid.star2d.ui.Tr.t("nav_select_first", "Select a NavGrid element first");
		item = it;
		stroking = false;
		strokes = 0;
		undone = 0;
		editor.selectActor(it);
		return null;
	}

	public void stop() {
		if (stroking) up();
		if (item != null) {
			item.cursorX = Float.NaN;
			item.boxX0 = Float.NaN;
		}
		item = null;
		fire();
	}

	private void fire() {
		if (listener != null) listener.changed(this);
	}

	private boolean isBox() {
		return tool == BOX_WALL || tool == BOX_ERASE;
	}

	// ------------------------------------------------------------------ strokes (grid units: px in 2D, meters x / z in 3D)

	@Override
	public void down(float x, float y) {
		if (!active()) return;
		NavGrid g = item.grid();
		if (g == null) return;
		before = item.getPropertySet().getString("Cells");
		stroking = true;
		if (isBox()) {
			item.boxX0 = item.boxX1 = x;
			item.boxY0 = item.boxY1 = y;
			return;
		}
		lastX = x;
		lastY = y;
		dab(g, x, y);
	}

	@Override
	public void drag(float x, float y) {
		if (!stroking || !active()) return;
		NavGrid g = item.grid();
		if (isBox()) {
			item.boxX1 = x;
			item.boxY1 = y;
			return;
		}
		// dabs along the movement: no holes when the finger moves fast
		float step = Math.min(g.cellW, g.cellH) * 0.5f;
		float dx = x - lastX, dy = y - lastY;
		int n = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy) / step));
		for (int i = 1; i <= n; i++) dab(g, lastX + dx * i / n, lastY + dy * i / n);
		lastX = x;
		lastY = y;
	}

	public void up() {
		if (!stroking) return;
		stroking = false;
		if (!active()) return;
		NavGrid g = item.grid();
		if (isBox() && !Float.isNaN(item.boxX0)) {
			IntArray cells = new IntArray();
			NavShapes.boxCells(g, Math.min(item.boxX0, item.boxX1), Math.min(item.boxY0, item.boxY1), Math.max(item.boxX0, item.boxX1),
					Math.max(item.boxY0, item.boxY1), cells);
			// a tap without dragging: the cell under the finger
			if (cells.size == 0) {
				int c = g.cellAt(item.boxX0, item.boxY0);
				if (c >= 0) cells.add(c);
			}
			for (int i = 0; i < cells.size; i++) paint(g, cells.get(i), tool == BOX_WALL ? WALL : ERASE);
			item.boxX0 = Float.NaN;
		}
		item.cursorX = Float.NaN;
		commit(before, label(tool));
	}

	/** A second finger went down: the started stroke is dropped (the view is moved instead). */
	@Override
	public void cancel() {
		if (!stroking) return;
		stroking = false;
		if (item == null) return;
		item.getPropertySet().put("Cells", before); // the grid is rebuilt from it
		item.boxX0 = Float.NaN;
		item.cursorX = Float.NaN;
	}

	private static String label(int tool) {
		switch (tool) {
			case ERASE: return "erase cells";
			case SLOW: return "slow cells";
			case BOX_WALL: return "wall box";
			case BOX_ERASE: return "open box";
			default: return "wall cells";
		}
	}

	private void dab(NavGrid g, float x, float y) {
		float unit = Math.min(g.cellW, g.cellH);
		item.cursorX = x;
		item.cursorY = y;
		item.cursorR = (brush + 0.5f) * unit;
		if (brush <= 0) {
			paint(g, g.cellAt(x, y), tool);
			return;
		}
		float r = brush * unit;
		IntArray cells = new IntArray();
		NavShapes.boxCells(g, x - r - unit, y - r - unit, x + r + unit, y + r + unit, cells);
		for (int k = 0; k < cells.size; k++) {
			int i = cells.get(k), c = i % g.cols, rr = i / g.cols;
			float dx = g.centerX(c, rr) - x, dy = g.centerY(c, rr) - y;
			if (dx * dx + dy * dy <= (r + unit * 0.35f) * (r + unit * 0.35f)) paint(g, i, tool);
		}
	}

	private void paint(NavGrid g, int i, int what) {
		if (i < 0) return;
		switch (what) {
			case WALL:
				g.setWall(i, true);
				g.setCost(i, 1);
				break;
			case ERASE:
				g.setWall(i, false);
				g.setCost(i, 1);
				break;
			case SLOW:
				g.setWall(i, false);
				g.setCost(i, Math.max(2, Math.min(NavGrid.MAX_COST, cost)));
				break;
		}
	}

	/** Writes the grid into its "Cells" property as one undo step (when something changed), saves the scene. */
	private void commit(String old, String message) {
		if (item == null) return;
		item.storeCells();
		String now = item.getPropertySet().getString("Cells");
		if (!now.equals(old)) {
			EditorAction a = EditorAction.propertyChangedRaw(editor, item.getName(), message, "Cells", now, old);
			if (a != null) a.updateItemProperties();
			strokes++;
			undone = 0;
			editor.getSaveState();
			editor.afterEdit3D();
		}
		fire();
	}

	// ------------------------------------------------------------------ whole grid actions

	/** Every cell open (walls and slow cells removed). */
	public void clearAll() {
		if (!active()) return;
		String old = item.getPropertySet().getString("Cells");
		NavGrid g = item.grid();
		for (int i = 0; i < g.size(); i++) paint(g, i, ERASE);
		commit(old, "clear cells");
	}

	/** Walls on the border cells (keeps the walkers inside the area). */
	public void borderWalls() {
		if (!active()) return;
		String old = item.getPropertySet().getString("Cells");
		NavGrid g = item.grid();
		for (int r = 0; r < g.rows; r++)
			for (int c = 0; c < g.cols; c++)
				if (r == 0 || c == 0 || r == g.rows - 1 || c == g.cols - 1) paint(g, g.index(c, r), WALL);
		commit(old, "border walls");
	}

	/** Swaps walls and open cells. */
	public void invert() {
		if (!active()) return;
		String old = item.getPropertySet().getString("Cells");
		NavGrid g = item.grid();
		for (int i = 0; i < g.size(); i++) {
			boolean wall = g.isWall(i);
			paint(g, i, wall ? ERASE : WALL);
		}
		commit(old, "invert cells");
	}

	/**
	 * Paints walls under the static elements of the scene (2D: STATIC physical elements, not sensors, by their collider; 3D:
	 * STATIC objects that stand on the ground). Returns how many cells became walls.
	 */
	public int wallsFromStatic() {
		if (!active()) return 0;
		String old = item.getPropertySet().getString("Cells");
		NavGrid g = item.grid();
		IntArray cells = new IntArray();
		for (Actor a : editor.getActors()) {
			if (a == item || !(a instanceof EditorItem)) continue;
			PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
			if (ps == null || "false".equals(ps.getString("Visible"))) continue;
			if (a instanceof Editor3DItem) {
				if (item.is3D()) stamp3D((Editor3DItem) a, g, cells);
				continue;
			}
			if (item.is3D()) continue;
			if (!"STATIC".equalsIgnoreCase(ps.getString("type")) || "true".equals(ps.getString("isSensor"))) continue;
			Body b = null;
			try {
				b = ((EditorItem) a).getBody();
			} catch (Throwable ignored) {
			}
			if (b != null && b.getFixtureList().size > 0) {
				for (Fixture f : b.getFixtureList()) NavShapes.shapeCells(g, b, f.getShape(), 1f, cells);
			} else {
				// no helper body: the element's box, turned like it
				float[] poly = new float[8];
				Vector2 p = new Vector2();
				float[][] corners = {{0, 0}, {a.getWidth(), 0}, {a.getWidth(), a.getHeight()}, {0, a.getHeight()}};
				for (int k = 0; k < 4; k++) {
					a.localToStageCoordinates(p.set(corners[k][0], corners[k][1]));
					poly[k * 2] = p.x;
					poly[k * 2 + 1] = p.y;
				}
				NavShapes.polygonCells(g, poly, 4, cells);
			}
		}
		int n = 0;
		for (int k = 0; k < cells.size; k++) if (g.setWall(cells.get(k), true)) n++;
		commit(old, "walls from static bodies");
		return n;
	}

	private void stamp3D(Editor3DItem it, NavGrid g, IntArray cells) {
		if (it.obj == null || it.obj.getDef() == null) return;
		String body = String.valueOf(it.obj.getDef().Body), mesh = String.valueOf(it.obj.getDef().Mesh).toUpperCase();
		if (!"STATIC".equalsIgnoreCase(body) || mesh.equals("TERRAIN") || mesh.equals("WATER") || mesh.equals("PLANE") || mesh.equals("EMPTY")) return;
		BoundingBox bb = new BoundingBox(it.obj.localBounds()).mul(it.obj.transform);
		// flat on the ground (floors, roads) or high above the walkers (bridges): not in the way
		if (bb.max.y <= g.groundY + 0.1f || bb.min.y >= g.groundY + 2.5f) return;
		NavShapes.boxCells(g, bb.min.x, bb.min.z, bb.max.x, bb.max.z, cells);
	}

	// ------------------------------------------------------------------ 3D: screen to ground

	private final Vector3 hit = new Vector3();

	/** Ground point (x, z) under a screen point of the 3D view, null when the ray misses the ground plane. */
	public Vector2 groundAt(com.badlogic.gdx.graphics.Camera cam, float sx, float sy, Vector2 out) {
		if (item == null) return null;
		Ray r = cam.getPickRay(sx, sy);
		if (!Intersector.intersectRayPlane(r, new Plane(Vector3.Y, -item.groundY()), hit)) return null;
		return out.set(hit.x, hit.z);
	}
}
