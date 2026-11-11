package com.star4droid.template.nav;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;

/**
 * An element that blocks the cells it covers on a grid. When it moves only its cells are updated; when it is destroyed its
 * cells open again by themselves (a door opened with a key, a crate pushed away).
 */
final class NavObstacle {
	final PlayerItem item;
	final NavGrid grid;
	private int[] cells = new int[0];
	private int count;
	private float lx = Float.NaN, ly, lrot, lw, lh;
	private boolean lvis;
	private final Vector2 v = new Vector2();
	private static final float[] POLY = new float[32];
	private int[] buf;

	NavObstacle(NavGrid grid, PlayerItem item) {
		this.grid = grid;
		this.item = item;
	}

	/** Removes its marks (element destroyed / obstacle removed). */
	void release() {
		if (count == 0) return;
		int[] box = bounds(cells, count);
		for (int i = 0; i < count; i++) grid.addDyn(cells[i], -1);
		count = 0;
		grid.changedRect(box[0], box[1], box[2], box[3]);
	}

	/** False when the element is gone (its cells are released then). Re-stamps the cells when it moved. */
	boolean update() {
		if (!Behavior.alive(item) && !is3DAlive()) {
			release();
			return false;
		}
		float x, y, rot, w, h;
		boolean vis;
		if (item instanceof com.star4droid.template.d3.Object3D) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) item;
			x = o.position.x;
			y = o.position.z;
			rot = o.euler.y;
			w = o.scale.x;
			h = o.scale.z;
			vis = o.isVisible();
		} else {
			x = Behavior.cx(item);
			y = Behavior.cy(item);
			Actor a = item.getActor();
			Body b = item.getBody();
			rot = b != null ? b.getAngle() * 57.29578f : (a == null ? 0 : a.getRotation());
			w = a == null ? 0 : a.getWidth() * a.getScaleX();
			h = a == null ? 0 : a.getHeight() * a.getScaleY();
			vis = true; // hidden walls still block (hide + destroyLater: destroy releases them)
		}
		boolean same = !Float.isNaN(lx) && Math.abs(x - lx) < 0.25f && Math.abs(y - ly) < 0.25f && Math.abs(rot - lrot) < 0.25f
				&& Math.abs(w - lw) < 0.25f && Math.abs(h - lh) < 0.25f && vis == lvis;
		if (same) return true;
		lx = x;
		ly = y;
		lrot = rot;
		lw = w;
		lh = h;
		lvis = vis;
		stamp();
		return true;
	}

	private boolean is3DAlive() {
		return item instanceof com.star4droid.template.d3.Object3D && !((com.star4droid.template.d3.Object3D) item).isDestroyed();
	}

	private void stamp() {
		int[] oldBox = count > 0 ? bounds(cells, count) : null;
		for (int i = 0; i < count; i++) grid.addDyn(cells[i], -1);
		if (buf == null) buf = new int[Math.min(grid.size(), 65536)];
		count = 0;
		collect(item, buf, 0);
		for (int i = 0; i < count; i++) grid.addDyn(cells[i], 1);
		int[] newBox = count > 0 ? bounds(cells, count) : null;
		if (oldBox == null && newBox == null) return;
		if (oldBox == null) grid.changedRect(newBox[0], newBox[1], newBox[2], newBox[3]);
		else if (newBox == null) grid.changedRect(oldBox[0], oldBox[1], oldBox[2], oldBox[3]);
		else grid.changedRect(Math.min(oldBox[0], newBox[0]), Math.min(oldBox[1], newBox[1]), Math.max(oldBox[2], newBox[2]), Math.max(oldBox[3], newBox[3]));
	}

	private void addCell(int c) {
		for (int i = 0; i < count; i++) if (cells[i] == c) return;
		if (count == cells.length) cells = java.util.Arrays.copyOf(cells, Math.max(16, cells.length * 2));
		cells[count++] = c;
	}

	/** Cells of an element (and of the children of a group). */
	private void collect(PlayerItem it, int[] buf, int depth) {
		if (it == null || depth > 8) return;
		if (it instanceof com.star4droid.template.Items.GroupItem) {
			for (PlayerItem kid : new java.util.ArrayList<PlayerItem>(it.getChildsHolder().childs)) collect(kid, buf, depth + 1);
			return;
		}
		if (it instanceof com.star4droid.template.d3.Object3D) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) it;
			if (o.isDestroyed()) return;
			BoundingBox bb = new BoundingBox(o.localBounds()).mul(o.transform);
			Vector3 mn = bb.min, mx = bb.max;
			// flat on the ground (floors, roads) or high above the walkers (bridges, roofs): not in the way
			if (mx.y <= grid.groundY + 0.1f || mn.y >= grid.groundY + 2.5f) return;
			int n = grid.cellsInRect(mn.x, mn.z, mx.x, mx.z, buf);
			for (int i = 0; i < n; i++) addCell(buf[i]);
			return;
		}
		Body b = null;
		try {
			b = it.getBody();
		} catch (RuntimeException ignored) {
		}
		if (b != null && b.getFixtureList().size > 0) {
			for (Fixture f : b.getFixtureList()) stampShape(b, f.getShape(), buf);
			return;
		}
		// no body (UI elements): the element box, rotation ignored
		float[] box = new float[4];
		Behavior.box(it, box);
		int n = grid.cellsInRect(box[0], box[1], box[2], box[3], buf);
		for (int i = 0; i < n; i++) addCell(buf[i]);
	}

	private void stampShape(Body b, Shape sh, int[] buf) {
		float W = Behavior.W;
		if (sh instanceof PolygonShape) {
			PolygonShape ps = (PolygonShape) sh;
			int vc = Math.min(16, ps.getVertexCount());
			float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
			for (int i = 0; i < vc; i++) {
				ps.getVertex(i, v);
				Vector2 w = b.getWorldPoint(v);
				float px = w.x / W, py = w.y / W;
				POLY[i * 2] = px;
				POLY[i * 2 + 1] = py;
				x0 = Math.min(x0, px);
				y0 = Math.min(y0, py);
				x1 = Math.max(x1, px);
				y1 = Math.max(y1, py);
			}
			int n = grid.cellsInRect(x0, y0, x1, y1, buf);
			for (int i = 0; i < n; i++) {
				int c = buf[i];
				if (grid.cellTouchesPolygon(c % grid.cols, c / grid.cols, POLY, vc)) addCell(c);
			}
		} else if (sh instanceof CircleShape) {
			CircleShape cs = (CircleShape) sh;
			Vector2 w = b.getWorldPoint(cs.getPosition());
			float cx = w.x / W, cy = w.y / W, r = cs.getRadius() / W;
			int n = grid.cellsInRect(cx - r, cy - r, cx + r, cy + r, buf);
			for (int i = 0; i < n; i++) {
				int c = buf[i];
				if (grid.cellTouchesCircle(c % grid.cols, c / grid.cols, cx, cy, r)) addCell(c);
			}
		} else if (sh instanceof EdgeShape) {
			EdgeShape es = (EdgeShape) sh;
			Vector2 a = new Vector2(), bb = new Vector2();
			es.getVertex1(a);
			es.getVertex2(bb);
			segment(b.getWorldPoint(a).cpy().scl(1 / W), b.getWorldPoint(bb).cpy().scl(1 / W));
		} else if (sh instanceof ChainShape) {
			ChainShape ch = (ChainShape) sh;
			Vector2 a = new Vector2(), bb = new Vector2();
			for (int i = 0; i + 1 < ch.getVertexCount(); i++) {
				ch.getVertex(i, a);
				ch.getVertex(i + 1, bb);
				segment(b.getWorldPoint(a).cpy().scl(1 / W), b.getWorldPoint(bb).cpy().scl(1 / W));
			}
		}
	}

	/** Cells crossed by a thin line (edges, chains). */
	private void segment(Vector2 a, Vector2 b) {
		float len = a.dst(b), step = Math.min(grid.cellW, grid.cellH) * 0.25f;
		int steps = Math.max(1, (int) Math.ceil(len / step));
		for (int s = 0; s <= steps; s++) {
			float t = s / (float) steps;
			int c = grid.cellAt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
			if (c >= 0) addCell(c);
		}
	}

	/** {minCol, minRow, maxCol, maxRow} of some cells. */
	private int[] bounds(int[] cs, int n) {
		int c0 = Integer.MAX_VALUE, r0 = Integer.MAX_VALUE, c1 = -1, r1 = -1;
		for (int i = 0; i < n; i++) {
			int c = cs[i] % grid.cols, r = cs[i] / grid.cols;
			c0 = Math.min(c0, c);
			r0 = Math.min(r0, r);
			c1 = Math.max(c1, c);
			r1 = Math.max(r1, r);
		}
		return new int[] {c0, r0, c1, r1};
	}
}
