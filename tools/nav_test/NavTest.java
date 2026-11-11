import com.star4droid.template.nav.NavGrid;
import com.star4droid.template.nav.PathHolder;

/** Headless checks of the path finding core (tools/test_nav.py): square / hex grids, walls, costs, agent size, data. */
public class NavTest {
	static int fails = 0, checks = 0;

	static void check(boolean ok, String msg) {
		checks++;
		if (!ok) {
			fails++;
			System.out.println("FAIL: " + msg);
		}
	}

	static boolean allWalkable(NavGrid g, PathHolder p, float sx, float sy) {
		float px = sx, py = sy;
		for (int i = 0; i < p.size(); i++) {
			if (!g.walkableAt(p.x(i), p.y(i))) return false;
			px = p.x(i);
			py = p.y(i);
		}
		return true;
	}

	/** Every segment of the path stays on walkable cells (sampled finely). */
	static boolean segmentsClear(NavGrid g, PathHolder p, float sx, float sy) {
		float px = sx, py = sy;
		for (int i = 0; i < p.size(); i++) {
			float qx = p.x(i), qy = p.y(i);
			float len = (float) Math.hypot(qx - px, qy - py);
			int steps = Math.max(1, (int) (len / 1f));
			for (int k = 1; k < steps; k++) {
				float t = k / (float) steps;
				int c = g.cellAt(px + (qx - px) * t, py + (qy - py) * t);
				if (c >= 0 && !g.walkable(c)) return false;
			}
			px = qx;
			py = qy;
		}
		return true;
	}

	public static void main(String[] a) {
		// ---------------- open square grid: one straight line after smoothing
		NavGrid g = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 100, 10, 10);
		check(g.cols == 10 && g.rows == 10, "cols/rows " + g.cols + "x" + g.rows);
		PathHolder p = new PathHolder();
		int r = g.findPath(5, 5, 95, 95, p, true, false);
		check(r == NavGrid.FOUND, "open path found");
		check(p.size() == 1 && Math.abs(p.x(0) - 95) < 0.01f && Math.abs(p.y(0) - 95) < 0.01f, "open path smoothed to the goal: " + p.toText());
		check(p.isReady() && p.grid == g, "holder ready");

		// ---------------- wall with a gap: the path goes through the gap
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 100, 10, 10);
		for (int row = 0; row < 10; row++) if (row != 7) g.setWall(g.index(5, row), true);
		r = g.findPath(15, 15, 85, 15, p, true, false);
		check(r == NavGrid.FOUND, "path through gap");
		check(segmentsClear(g, p, 15, 15), "segments clear of the wall: " + p.toText());
		boolean viaGap = false;
		for (int i = 0; i < p.size(); i++) if (p.y(i) > 65) viaGap = true;
		check(viaGap, "path climbs to the gap row: " + p.toText());

		// ---------------- no corner cutting between two diagonal walls
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 30, 30, 10, 10);
		g.setWall(g.index(1, 0), true);
		g.setWall(g.index(0, 1), true);
		r = g.findPath(5, 5, 25, 25, p, false, false);
		check(r == NavGrid.NONE, "no squeezing between diagonal walls (cutCorners off), got " + r + " " + p.toText());
		g.cutCorners = true;
		g.recomputeAll();
		r = g.findPath(5, 5, 25, 25, p, false, false);
		check(r == NavGrid.NONE, "both orthogonals blocked: still no way with cutCorners");
		g.setWall(g.index(0, 1), false);
		r = g.findPath(5, 5, 25, 25, p, false, false);
		check(r == NavGrid.FOUND, "cut corner allowed with one open side");

		// ---------------- unreachable goal: NONE, partial goes as close as possible
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 100, 10, 10);
		for (int row = 0; row < 10; row++) g.setWall(g.index(5, row), true);
		r = g.findPath(15, 15, 85, 15, p, true, false);
		check(r == NavGrid.NONE && p.size() == 0, "walled off: no path");
		r = g.findPath(15, 15, 85, 15, p, true, true);
		check(r == NavGrid.PARTIAL && p.size() > 0 && p.lastX() < 50 && p.partial, "partial path ends near the wall: " + p.toText());

		// ---------------- costs: a mud band is avoided when a detour is cheaper
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 200, 100, 10, 10);
		for (int row = 0; row < 9; row++) g.setCost(g.index(10, row), 9);
		r = g.findPath(5, 45, 195, 45, p, false, false);
		check(r == NavGrid.FOUND, "path with costs");
		boolean usedTop = false;
		for (int i = 0; i < p.size(); i++) if (g.cellAt(p.x(i), p.y(i)) == g.index(10, 9)) usedTop = true;
		check(usedTop, "goes round the mud through the cheap cell: " + p.toText());

		// ---------------- start / goal inside a wall snap to the nearest open cell
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 100, 10, 10);
		g.setWall(g.index(9, 9), true);
		r = g.findPath(5, 5, 95, 95, p, true, false);
		check(r == NavGrid.FOUND && g.walkableAt(p.lastX(), p.lastY()), "goal in a wall snaps to a neighbour: " + p.toText());

		// ---------------- agent size: a 1-cell gap is too narrow for a 2-cell agent
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 100, 10, 10);
		for (int row = 0; row < 10; row++) if (row != 5) g.setWall(g.index(5, row), true);
		r = g.findPath(15, 50, 85, 50, p, true, false);
		check(r == NavGrid.FOUND, "small agent fits the gap");
		g.setAgentSize(20);
		r = g.findPath(15, 50, 85, 50, p, true, false);
		check(r == NavGrid.NONE, "big agent does not fit a 1-cell gap");
		g.setAgentSize(12);
		check(!g.walkable(g.index(4, 2)) && g.walkable(g.index(3, 2)), "inflation marks the cells next to walls");
		g.setAgentSize(8);
		check(g.walkable(g.index(4, 2)), "a small agent may stand next to a wall");

		// ---------------- hexagons: geometry round trip, 6 neighbours, paths
		NavGrid h = new NavGrid(NavGrid.HEX, 0, 0, 400, 300, 40, 34.64f);
		check(h.cols > 3 && h.rows > 3, "hex size " + h.cols + "x" + h.rows);
		boolean round = true;
		for (int row = 0; row < h.rows; row++)
			for (int c = 0; c < h.cols; c++) if (h.cellAt(h.centerX(c, row), h.centerY(c, row)) != h.index(c, row)) round = false;
		check(round, "hex cellAt(center) round trip");
		int[] nb = new int[8];
		check(h.neighbours(h.index(3, 3), nb) == 6 && h.neighbours(h.index(3, 4), nb) == 6, "6 neighbours inside");
		// neighbour centers are all about one cell width away
		boolean dist = true;
		for (int row = 2; row <= 3; row++) {
			int n = h.neighbours(h.index(3, row), nb);
			for (int k = 0; k < n; k++) {
				float d = (float) Math.hypot(h.centerX(nb[k] % h.cols, nb[k] / h.cols) - h.centerX(3, row), h.centerY(nb[k] % h.cols, nb[k] / h.cols) - h.centerY(3, row));
				if (Math.abs(d - 40) > 0.6f) dist = false;
			}
		}
		check(dist, "hex neighbours are one cell apart");
		for (int row = 0; row < h.rows - 1; row++) h.setWall(h.index(4, row), true);
		r = h.findPath(h.centerX(1, 1), h.centerY(1, 1), h.centerX(7, 1), h.centerY(7, 1), p, true, false);
		check(r == NavGrid.FOUND && segmentsClear(h, p, h.centerX(1, 1), h.centerY(1, 1)), "hex path around a wall: " + p.toText());
		check(h.cellAt(-50, -50) == -1 && h.cellAt(1000, 1000) == -1, "outside the hex grid");

		// ---------------- painted data
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 50, 10, 10);
		g.setWall(g.index(2, 1), true);
		g.setWall(g.index(3, 1), true);
		g.setCost(g.index(7, 4), 5);
		String enc = g.encode();
		check(enc.startsWith("v1:10,5:"), "encode header: " + enc);
		NavGrid g2 = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 50, 10, 10);
		g2.decode(enc);
		check(g2.isWall(g2.index(2, 1)) && g2.isWall(g2.index(3, 1)) && !g2.isWall(g2.index(4, 1)) && g2.costOf(g2.index(7, 4)) == 5, "decode round trip");
		check(new NavGrid(NavGrid.SQUARE, 0, 0, 100, 50, 10, 10).encode().isEmpty(), "empty grid encodes to nothing");
		NavGrid g3 = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 50, 5, 5);
		g3.decode(enc);
		check(g3.isWall(g3.cellAt(25, 15)) && !g3.isWall(g3.cellAt(55, 15)), "decode scales to a finer grid");

		// ---------------- obstacle-style rect / polygon / circle tests
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 100, 100, 10, 10);
		int[] buf = new int[200];
		int n = g.cellsInRect(20, 20, 40, 40, buf);
		check(n == 4, "rect 20..40 covers 4 cells, got " + n);
		check(g.cellTouchesCircle(5, 5, 55, 55, 3) && !g.cellTouchesCircle(8, 8, 55, 55, 3), "circle cells");
		float[] tri = {0, 0, 50, 0, 0, 50};
		check(g.cellTouchesPolygon(1, 1, tri, 3) && !g.cellTouchesPolygon(7, 7, tri, 3), "polygon cells");

		// ---------------- PathHolder helpers
		PathHolder q = PathHolder.of(0, 0, 100, 0, 100, 100);
		check(Math.abs(q.length() - 200) < 0.01f, "length");
		check(q.nearestIndex(90, 10) == 1, "nearest index");
		check(q.nearestAhead(50, 2) == 1, "nearest ahead on the first segment");
		check(q.nearestAhead(-20, 0) == 0, "before the first point");
		check(q.nearestAhead(98, 60) == 2, "on the second segment");
		q.reverse();
		check(q.x(0) == 100 && q.y(0) == 100, "reverse");
		float[] pt = new float[2];
		PathHolder.of(0, 0, 100, 0).pointAt(25, pt);
		check(Math.abs(pt[0] - 25) < 0.01f, "pointAt");
		check(PathHolder.parse(q.toText()).size() == 3, "text round trip " + q.toText());
		int rev = q.revision;
		q.add(5, 5);
		check(q.revision != rev, "revision grows");

		// ---------------- speed: 160x120 random walls, 200 searches
		java.util.Random rnd = new java.util.Random(7);
		g = new NavGrid(NavGrid.SQUARE, 0, 0, 1600, 1200, 10, 10);
		for (int i = 0; i < g.size(); i++) if (rnd.nextFloat() < 0.22f) g.setWall(i, true);
		g.setAgentSize(6);
		long t0 = System.nanoTime();
		int found = 0;
		for (int i = 0; i < 200; i++) {
			int res = g.findPath(rnd.nextFloat() * 1600, rnd.nextFloat() * 1200, rnd.nextFloat() * 1600, rnd.nextFloat() * 1200, p, true, true);
			if (res != NavGrid.NONE) found++;
		}
		double ms = (System.nanoTime() - t0) / 1e6;
		System.out.println(String.format("200 searches on 160x120 (22%% walls): %.1f ms (%.2f ms each), %d with a path", ms, ms / 200, found));
		check(ms / 200 < 25, "a search stays fast");

		System.out.println(fails == 0 ? ("NAV OK (" + checks + " checks)") : (fails + " of " + checks + " checks FAILED"));
		System.exit(fails == 0 ? 0 : 1);
	}
}
