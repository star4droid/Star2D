package com.star4droid.template.nav;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.IntArray;

/**
 * Which cells of a grid a shape covers (Box2D shapes, polygons, circles, boxes, lines). Used by the editor ("walls from the
 * static bodies") and by scripts. {@code scale} = Box2D units per grid unit (1 in the editor, 0.25 in a running 2D scene).
 */
public final class NavShapes {
	private NavShapes() {
	}

	private static final Vector2 V = new Vector2(), A = new Vector2(), B = new Vector2();

	/** Adds the cells covered by one shape of a body. */
	public static void shapeCells(NavGrid g, Body b, Shape sh, float scale, IntArray out) {
		if (g == null || b == null || sh == null) return;
		float k = 1f / scale;
		if (sh instanceof PolygonShape) {
			PolygonShape ps = (PolygonShape) sh;
			int vc = Math.min(16, ps.getVertexCount());
			float[] poly = new float[vc * 2];
			for (int i = 0; i < vc; i++) {
				ps.getVertex(i, V);
				Vector2 w = b.getWorldPoint(V);
				poly[i * 2] = w.x * k;
				poly[i * 2 + 1] = w.y * k;
			}
			polygonCells(g, poly, vc, out);
		} else if (sh instanceof CircleShape) {
			CircleShape cs = (CircleShape) sh;
			Vector2 w = b.getWorldPoint(cs.getPosition());
			circleCells(g, w.x * k, w.y * k, cs.getRadius() * k, out);
		} else if (sh instanceof EdgeShape) {
			EdgeShape es = (EdgeShape) sh;
			es.getVertex1(A);
			es.getVertex2(B);
			Vector2 a = b.getWorldPoint(A).cpy(), c = b.getWorldPoint(B).cpy();
			lineCells(g, a.x * k, a.y * k, c.x * k, c.y * k, out);
		} else if (sh instanceof ChainShape) {
			ChainShape ch = (ChainShape) sh;
			int n = ch.getVertexCount();
			float[] pts = new float[n * 2];
			for (int i = 0; i < n; i++) {
				ch.getVertex(i, V);
				Vector2 w = b.getWorldPoint(V);
				pts[i * 2] = w.x * k;
				pts[i * 2 + 1] = w.y * k;
			}
			// a closed loop (custom shapes) covers its inside, an open chain only its line
			boolean loop = n > 2 && Math.abs(pts[0] - pts[n * 2 - 2]) < 1e-3f && Math.abs(pts[1] - pts[n * 2 - 1]) < 1e-3f;
			if (n > 2 && (loop || ch.isLooped())) polygonCells(g, pts, n, out);
			for (int i = 0; i + 1 < n; i++) lineCells(g, pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3], out);
			if (ch.isLooped() && n > 1) lineCells(g, pts[n * 2 - 2], pts[n * 2 - 1], pts[0], pts[1], out);
		}
	}

	/** Cells overlapping a polygon (x,y,x,y...). */
	public static void polygonCells(NavGrid g, float[] poly, int count, IntArray out) {
		float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			x0 = Math.min(x0, poly[i * 2]);
			y0 = Math.min(y0, poly[i * 2 + 1]);
			x1 = Math.max(x1, poly[i * 2]);
			y1 = Math.max(y1, poly[i * 2 + 1]);
		}
		int[] buf = new int[Math.min(g.size(), 262144)];
		int n = g.cellsInRect(x0, y0, x1, y1, buf);
		for (int i = 0; i < n; i++) {
			int c = buf[i];
			if (g.cellTouchesPolygon(c % g.cols, c / g.cols, poly, count)) add(out, c);
		}
	}

	/** Cells overlapping a circle. */
	public static void circleCells(NavGrid g, float cx, float cy, float r, IntArray out) {
		int[] buf = new int[Math.min(g.size(), 262144)];
		int n = g.cellsInRect(cx - r, cy - r, cx + r, cy + r, buf);
		for (int i = 0; i < n; i++) {
			int c = buf[i];
			if (g.cellTouchesCircle(c % g.cols, c / g.cols, cx, cy, r)) add(out, c);
		}
	}

	/** Cells overlapping an axis-aligned box. */
	public static void boxCells(NavGrid g, float minX, float minY, float maxX, float maxY, IntArray out) {
		int[] buf = new int[Math.min(g.size(), 262144)];
		int n = g.cellsInRect(minX, minY, maxX, maxY, buf);
		for (int i = 0; i < n; i++) add(out, buf[i]);
	}

	/** Cells crossed by a thin line. */
	public static void lineCells(NavGrid g, float ax, float ay, float bx, float by, IntArray out) {
		float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy), step = Math.min(g.cellW, g.cellH) * 0.25f;
		int steps = Math.max(1, (int) Math.ceil(len / step));
		for (int s = 0; s <= steps; s++) {
			float t = s / (float) steps;
			int c = g.cellAt(ax + dx * t, ay + dy * t);
			if (c >= 0) add(out, c);
		}
	}

	/** Cells may repeat (painting them twice changes nothing). */
	private static void add(IntArray out, int c) {
		out.add(c);
	}
}
