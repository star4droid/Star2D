package com.star4droid.template.nav;

/**
 * A navigation grid laid over an area of the scene: square or hexagon cells, each one open, blocked or open with a cost
 * (mud slower than road), and A* to find the shortest path between two points.
 *
 * <p>Units are the scene's: editor pixels (y up) in 2D scenes, meters on the ground (x, z) in 3D scenes. The area starts at
 * ({@link #x0}, {@link #y0}) (bottom-left corner) and is {@link #cols} x {@link #rows} cells.</p>
 *
 * <p>Square cells are {@link #cellW} x {@link #cellH}. Hexagon cells are "pointy-top", odd rows shifted half a cell to the
 * right: {@link #cellW} is the distance between two centers of a row, {@link #cellH} the distance between two rows
 * (a regular hexagon has cellH = cellW * 0.866). A square cell has 4 neighbours (8 with {@link #diagonal}), a hexagon 6.</p>
 *
 * <p>Walkable = not painted as a wall, not covered by an obstacle, and (when {@link #agentSize} &gt; 0) far enough from every
 * blocked cell for an agent of that diameter, so the paths keep clear of walls.</p>
 *
 * Pure logic (no GL, no android): also used by the tests and the editor.
 */
public final class NavGrid {
	public static final int SQUARE = 0, HEX = 1;
	/** Cell values of {@link #painted}. */
	public static final byte OPEN = 0, WALL = 1;
	/** Highest cell cost (1 = normal). */
	public static final int MAX_COST = 9;

	public String name = "";
	/** 3D scenes: height (meters) of the ground the grid lies on. */
	public float groundY = 0;
	public final int shape;
	public final float x0, y0, cellW, cellH;
	public final int cols, rows;
	public boolean diagonal = true, cutCorners = false;
	/** Diameter of the agents that walk on this grid (0 = paths may touch the walls). */
	private float agentSize = 0;

	/** Painted walls (editor / setCell). */
	final byte[] painted;
	/** Cost of each cell, 1..9. */
	final byte[] cost;
	/** Number of dynamic obstacles covering each cell. */
	final short[] dyn;
	/** Effective walkability for an agent of {@link #agentSize}. */
	final boolean[] walk;
	/** Grows each time the walkability changes: followers check their paths again. */
	public int version = 1;
	private float minCost = 1;

	public NavGrid(int shape, float x0, float y0, float width, float height, float cellW, float cellH) {
		this.shape = shape == HEX ? HEX : SQUARE;
		this.x0 = x0;
		this.y0 = y0;
		this.cellW = Math.max(1e-3f, cellW);
		this.cellH = Math.max(1e-3f, cellH);
		int c, r;
		if (this.shape == SQUARE) {
			c = (int) Math.ceil(width / this.cellW - 1e-4f);
			r = (int) Math.ceil(height / this.cellH - 1e-4f);
		} else {
			c = (int) Math.floor((width - this.cellW * 0.5f) / this.cellW + 1e-4f);
			r = (int) Math.floor((height - 2 * hexRv()) / this.cellH + 1e-4f) + 1;
		}
		cols = Math.max(1, Math.min(c, 2000));
		rows = Math.max(1, Math.min(r, 2000));
		int n = cols * rows;
		painted = new byte[n];
		cost = new byte[n];
		dyn = new short[n];
		walk = new boolean[n];
		java.util.Arrays.fill(cost, (byte) 1);
		java.util.Arrays.fill(walk, true);
	}

	/** Vertical radius of a hexagon cell (center to the top vertex). */
	public float hexRv() {
		return cellH * (2f / 3f);
	}

	public int size() {
		return cols * rows;
	}

	public int index(int c, int r) {
		return r * cols + c;
	}

	public boolean inside(int c, int r) {
		return c >= 0 && r >= 0 && c < cols && r < rows;
	}

	// ------------------------------------------------------------------ geometry

	/** Center x of a cell. */
	public float centerX(int c, int r) {
		if (shape == SQUARE) return x0 + (c + 0.5f) * cellW;
		return x0 + (c + 0.5f) * cellW + ((r & 1) == 1 ? cellW * 0.5f : 0f);
	}

	/** Center y of a cell. */
	public float centerY(int c, int r) {
		if (shape == SQUARE) return y0 + (r + 0.5f) * cellH;
		return y0 + hexRv() + r * cellH;
	}

	/** Index of the cell under a point, -1 outside the grid. */
	public int cellAt(float x, float y) {
		if (shape == SQUARE) {
			int c = (int) Math.floor((x - x0) / cellW), r = (int) Math.floor((y - y0) / cellH);
			return inside(c, r) ? index(c, r) : -1;
		}
		// hexagons: the nearest center among the candidates (Voronoi cells of the centers are the hexagons)
		int r0 = Math.round((y - y0 - hexRv()) / cellH);
		int best = -1;
		float bd = Float.MAX_VALUE;
		for (int r = r0 - 1; r <= r0 + 1; r++) {
			if (r < 0 || r >= rows) continue;
			int c0 = Math.round((x - x0 - cellW * 0.5f - ((r & 1) == 1 ? cellW * 0.5f : 0f)) / cellW);
			for (int c = c0 - 1; c <= c0 + 1; c++) {
				if (c < 0 || c >= cols) continue;
				float dx = centerX(c, r) - x, dy = centerY(c, r) - y, d = dx * dx + dy * dy;
				if (d < bd) {
					bd = d;
					best = index(c, r);
				}
			}
		}
		if (best < 0) return -1;
		// outside the outer edge of the border cells: not on the grid
		int bc = best % cols, br = best / cols;
		float dx = Math.abs(centerX(bc, br) - x), dy = Math.abs(centerY(bc, br) - y);
		if (dx > cellW * 0.5f + 1e-3f || dy > hexRv() + 1e-3f) return -1;
		return best;
	}

	/** The 6 corners of a hexagon cell {x0,y0,x1,y1,...} (also used by the editor to draw it). */
	public float[] hexCorners(int c, int r, float[] out) {
		if (out == null || out.length < 12) out = new float[12];
		float cx = centerX(c, r), cy = centerY(c, r), hw = cellW * 0.5f, rv = hexRv();
		out[0] = cx; out[1] = cy + rv;
		out[2] = cx + hw; out[3] = cy + rv * 0.5f;
		out[4] = cx + hw; out[5] = cy - rv * 0.5f;
		out[6] = cx; out[7] = cy - rv;
		out[8] = cx - hw; out[9] = cy - rv * 0.5f;
		out[10] = cx - hw; out[11] = cy + rv * 0.5f;
		return out;
	}

	/** Width / height of the whole grid area. */
	public float areaWidth() {
		return shape == SQUARE ? cols * cellW : cols * cellW + (rows > 1 ? cellW * 0.5f : 0f);
	}

	public float areaHeight() {
		return shape == SQUARE ? rows * cellH : (rows - 1) * cellH + 2 * hexRv();
	}

	// ------------------------------------------------------------------ cells

	public boolean isWall(int i) {
		return i >= 0 && i < painted.length && painted[i] == WALL;
	}

	public int costOf(int i) {
		return i >= 0 && i < cost.length ? cost[i] : 1;
	}

	/** True when an agent can stand on this cell. */
	public boolean walkable(int i) {
		return i >= 0 && i < walk.length && walk[i];
	}

	/** True when an agent can stand at this point (false outside the grid). */
	public boolean walkableAt(float x, float y) {
		return walkable(cellAt(x, y));
	}

	/** Blocked by a wall or an obstacle (ignores the agent size). */
	public boolean blockedRaw(int i) {
		return i < 0 || i >= painted.length || painted[i] == WALL || dyn[i] > 0;
	}

	/** Paints one cell (wall / open). Returns true when it changed. */
	public boolean setWall(int i, boolean wall) {
		if (i < 0 || i >= painted.length) return false;
		byte v = wall ? WALL : OPEN;
		if (painted[i] == v) return false;
		painted[i] = v;
		changed(i, i);
		return true;
	}

	/** Cost of one cell (1 = normal, up to 9 = very slow). */
	public boolean setCost(int i, int c) {
		if (i < 0 || i >= cost.length) return false;
		byte v = (byte) Math.max(1, Math.min(MAX_COST, c));
		if (cost[i] == v) return false;
		cost[i] = v;
		recomputeMinCost();
		version++;
		return true;
	}

	public float agentSize() {
		return agentSize;
	}

	public void setAgentSize(float s) {
		s = Math.max(0, s);
		if (s == agentSize) return;
		agentSize = s;
		recomputeAll();
	}

	/** One more (+1) or one less (-1) obstacle on a cell. */
	void addDyn(int i, int d) {
		if (i < 0 || i >= dyn.length) return;
		int v = dyn[i] + d;
		dyn[i] = (short) Math.max(0, Math.min(Short.MAX_VALUE, v));
	}

	private void recomputeMinCost() {
		int m = MAX_COST;
		for (byte b : cost) if (b < m) m = b;
		minCost = Math.max(1, m);
	}

	/** Recomputes the walkability of the whole grid. */
	public void recomputeAll() {
		recompute(0, 0, cols - 1, rows - 1);
		version++;
	}

	/** Cells changed between the indexes a and b (both included, any order): their walkability and the area around them. */
	void changed(int a, int b) {
		int ca = a % cols, ra = a / cols, cb = b % cols, rb = b / cols;
		int k = inflateCells() + 1;
		recompute(Math.min(ca, cb) - k, Math.min(ra, rb) - k, Math.max(ca, cb) + k, Math.max(ra, rb) + k);
		version++;
	}

	/** Recomputes a rectangle of cells (clamped) after changes. */
	void changedRect(int c0, int r0, int c1, int r1) {
		int k = inflateCells() + 1;
		recompute(c0 - k, r0 - k, c1 + k, r1 + k);
		version++;
	}

	private int inflateCells() {
		if (agentSize <= 0) return 0;
		return (int) Math.ceil(agentSize * 0.5f / Math.min(cellW, cellH)) + 1;
	}

	private void recompute(int c0, int r0, int c1, int r1) {
		c0 = Math.max(0, c0);
		r0 = Math.max(0, r0);
		c1 = Math.min(cols - 1, c1);
		r1 = Math.min(rows - 1, r1);
		float rad = agentSize * 0.5f;
		int k = inflateCells();
		for (int r = r0; r <= r1; r++)
			for (int c = c0; c <= c1; c++) {
				int i = index(c, r);
				if (blockedRaw(i)) {
					walk[i] = false;
					continue;
				}
				boolean ok = true;
				if (rad > 0) {
					float cx = centerX(c, r), cy = centerY(c, r);
					search:
					for (int rr = r - k; rr <= r + k; rr++) {
						if (rr < 0 || rr >= rows) continue;
						for (int cc = c - k; cc <= c + k; cc++) {
							if (cc < 0 || cc >= cols) continue;
							int j = index(cc, rr);
							if (!blockedRaw(j)) continue;
							if (distToCell(cc, rr, cx, cy) < rad - 1e-4f) {
								ok = false;
								break search;
							}
						}
					}
				}
				walk[i] = ok;
			}
	}

	/** Distance from a point to the inside of a cell (0 inside). Hexagons are measured as their inner circle. */
	public float distToCell(int c, int r, float x, float y) {
		float cx = centerX(c, r), cy = centerY(c, r);
		if (shape == SQUARE) {
			float dx = Math.max(0, Math.abs(x - cx) - cellW * 0.5f), dy = Math.max(0, Math.abs(y - cy) - cellH * 0.5f);
			return (float) Math.sqrt(dx * dx + dy * dy);
		}
		float inner = Math.min(cellW * 0.5f, hexRv() * 0.866f);
		float dx = x - cx, dy = y - cy;
		return Math.max(0, (float) Math.sqrt(dx * dx + dy * dy) - inner);
	}

	// ------------------------------------------------------------------ neighbours

	private static final int[] SQ_DC = {1, -1, 0, 0, 1, 1, -1, -1};
	private static final int[] SQ_DR = {0, 0, 1, -1, 1, -1, 1, -1};
	// odd-r offset hexagons: neighbours of an even row / an odd row
	private static final int[] HEX_DC_EVEN = {1, -1, -1, 0, -1, 0};
	private static final int[] HEX_DR_EVEN = {0, 0, 1, 1, -1, -1};
	private static final int[] HEX_DC_ODD = {1, -1, 0, 1, 0, 1};
	private static final int[] HEX_DR_ODD = {0, 0, 1, 1, -1, -1};

	/** Fills {@code out} with the walkable neighbours of a cell, returns how many. */
	public int neighbours(int i, int[] out) {
		int c = i % cols, r = i / cols, n = 0;
		if (shape == SQUARE) {
			int count = diagonal ? 8 : 4;
			for (int k = 0; k < count; k++) {
				int nc = c + SQ_DC[k], nr = r + SQ_DR[k];
				if (!inside(nc, nr)) continue;
				int j = index(nc, nr);
				if (!walk[j]) continue;
				if (k >= 4) {
					boolean a = walk[index(nc, r)], b = walk[index(c, nr)];
					if (cutCorners ? (!a && !b) : (!a || !b)) continue;
				}
				out[n++] = j;
			}
		} else {
			boolean odd = (r & 1) == 1;
			int[] dc = odd ? HEX_DC_ODD : HEX_DC_EVEN, dr = odd ? HEX_DR_ODD : HEX_DR_EVEN;
			for (int k = 0; k < 6; k++) {
				int nc = c + dc[k], nr = r + dr[k];
				if (!inside(nc, nr)) continue;
				int j = index(nc, nr);
				if (walk[j]) out[n++] = j;
			}
		}
		return n;
	}

	/** The nearest walkable cell to a point within {@code maxRings} cells (-1 when there is none). */
	public int nearestWalkable(float x, float y, int maxRings) {
		int start = cellAt(x, y);
		int sc, sr;
		if (start >= 0) {
			if (walk[start]) return start;
			sc = start % cols;
			sr = start / cols;
		} else {
			// outside the grid: start from the closest border cell
			sc = Math.max(0, Math.min(cols - 1, (int) Math.floor((x - x0) / cellW)));
			sr = shape == SQUARE ? Math.max(0, Math.min(rows - 1, (int) Math.floor((y - y0) / cellH)))
					: Math.max(0, Math.min(rows - 1, Math.round((y - y0 - hexRv()) / cellH)));
			int b = index(sc, sr);
			if (walk[b]) return b;
		}
		int best = -1;
		float bd = Float.MAX_VALUE;
		for (int ring = 1; ring <= maxRings; ring++) {
			for (int r = sr - ring; r <= sr + ring; r++) {
				if (r < 0 || r >= rows) continue;
				for (int c = sc - ring; c <= sc + ring; c++) {
					if (c < 0 || c >= cols) continue;
					if (Math.max(Math.abs(c - sc), Math.abs(r - sr)) != ring) continue;
					int j = index(c, r);
					if (!walk[j]) continue;
					float dx = centerX(c, r) - x, dy = centerY(c, r) - y, d = dx * dx + dy * dy;
					if (d < bd) {
						bd = d;
						best = j;
					}
				}
			}
			if (best >= 0) return best;
		}
		return -1;
	}

	// ------------------------------------------------------------------ A*

	private float[] g, f;
	private int[] parent, stamp, heap;
	private int heapSize, gen;
	private final int[] nb = new int[8];
	/** Cells expanded by the last search (tests / budget). */
	public int lastExpanded;
	/** Max cells one search may expand before it gives up (keeps huge grids from freezing a frame). */
	public int maxExpand = 60000;

	private void ensureArrays() {
		int n = size();
		if (g != null && g.length == n) return;
		g = new float[n];
		f = new float[n];
		parent = new int[n];
		stamp = new int[n];
		heap = new int[n + 1];
		gen = 0;
	}

	private float heuristic(int a, int goal) {
		float dx = centerX(a % cols, a / cols) - centerX(goal % cols, goal / cols);
		float dy = centerY(a % cols, a / cols) - centerY(goal % cols, goal / cols);
		return (float) Math.sqrt(dx * dx + dy * dy) * minCost;
	}

	private float stepCost(int a, int b) {
		float dx = centerX(a % cols, a / cols) - centerX(b % cols, b / cols);
		float dy = centerY(a % cols, a / cols) - centerY(b % cols, b / cols);
		return (float) Math.sqrt(dx * dx + dy * dy) * (cost[a] + cost[b]) * 0.5f;
	}

	private void heapPush(int i) {
		int k = ++heapSize;
		heap[k] = i;
		while (k > 1 && f[heap[k >> 1]] > f[heap[k]]) {
			int t = heap[k];
			heap[k] = heap[k >> 1];
			heap[k >> 1] = t;
			k >>= 1;
		}
	}

	private int heapPop() {
		int top = heap[1];
		heap[1] = heap[heapSize--];
		int k = 1;
		while (true) {
			int l = k << 1, r = l + 1, m = k;
			if (l <= heapSize && f[heap[l]] < f[heap[m]]) m = l;
			if (r <= heapSize && f[heap[r]] < f[heap[m]]) m = r;
			if (m == k) break;
			int t = heap[k];
			heap[k] = heap[m];
			heap[m] = t;
			k = m;
		}
		return top;
	}

	/** Result codes of {@link #findPath}. */
	public static final int FOUND = 1, PARTIAL = 2, NONE = 0;

	/**
	 * Shortest path from (sx, sy) to (tx, ty) into {@code out} (replaced). The start and the goal are moved to the nearest
	 * walkable cell when they stand on a blocked one. {@code allowPartial}: when the goal can't be reached the path goes to the
	 * reachable cell closest to it (result {@link #PARTIAL}). {@code smooth} removes the zig-zag (straight lines where the
	 * agent fits). The path does not contain the start point; its last point is the goal (or the end of a partial path).
	 */
	public int findPath(float sx, float sy, float tx, float ty, PathHolder out, boolean smooth, boolean allowPartial) {
		if (out != null) out.clear();
		int start = nearestWalkable(sx, sy, 6);
		int goal = nearestWalkable(tx, ty, 6);
		lastExpanded = 0;
		if (start < 0 || goal < 0) return NONE;
		ensureArrays();
		gen++;
		if (gen == Integer.MAX_VALUE) {
			java.util.Arrays.fill(stamp, 0);
			gen = 1;
		}
		heapSize = 0;
		// stamp: gen = in open set or closed with a g value, -gen = closed
		g[start] = 0;
		f[start] = heuristic(start, goal);
		parent[start] = -1;
		stamp[start] = gen;
		heapPush(start);
		int closest = start;
		float closestH = f[start];
		boolean found = false;
		while (heapSize > 0) {
			int cur = heapPop();
			if (stamp[cur] == -gen) continue; // stale heap entry
			stamp[cur] = -gen;
			lastExpanded++;
			if (cur == goal) {
				found = true;
				break;
			}
			float h = f[cur] - g[cur];
			if (h < closestH) {
				closestH = h;
				closest = cur;
			}
			if (lastExpanded > maxExpand) break;
			int n = neighbours(cur, nb);
			for (int k = 0; k < n; k++) {
				int j = nb[k];
				if (stamp[j] == -gen) continue;
				float ng = g[cur] + stepCost(cur, j);
				if (stamp[j] == gen && ng >= g[j]) continue;
				g[j] = ng;
				// tiny tie-break toward the goal keeps paths straighter on open ground
				float hj = heuristic(j, goal);
				f[j] = ng + hj * 1.0001f;
				parent[j] = cur;
				stamp[j] = gen;
				heapPush(j);
			}
		}
		int end;
		if (found) end = goal;
		else if (allowPartial && closest != start) end = closest;
		else return NONE;
		if (out == null) return found ? FOUND : PARTIAL;
		// cells from the end back to the start
		int count = 0;
		for (int i = end; i >= 0; i = parent[i]) count++;
		int[] cells = new int[count];
		int k = count;
		for (int i = end; i >= 0; i = parent[i]) cells[--k] = i;
		float[] pts = new float[count * 2 + 4];
		int np = 0;
		// first point: where the agent really is (when that is walkable), so smoothing can skip the start cell's center
		boolean startExact = walkableAt(sx, sy);
		pts[np++] = startExact ? sx : centerX(start % cols, start / cols);
		pts[np++] = startExact ? sy : centerY(start % cols, start / cols);
		for (int i = 1; i < count; i++) {
			pts[np++] = centerX(cells[i] % cols, cells[i] / cols);
			pts[np++] = centerY(cells[i] % cols, cells[i] / cols);
		}
		if (found && cellAt(tx, ty) == goal && walkableAt(tx, ty)) {
			// the exact goal instead of its cell's center
			if (count > 1) {
				pts[np - 2] = tx;
				pts[np - 1] = ty;
			} else {
				pts[np++] = tx;
				pts[np++] = ty;
			}
		}
		if (smooth) np = smooth(pts, np);
		// drop the start point
		for (int i = 2; i < np; i += 2) out.add(pts[i], pts[i + 1]);
		out.grid = this;
		out.gridVersion = version;
		out.goalX = tx;
		out.goalY = ty;
		out.partial = !found;
		out.state = PathHolder.READY;
		return found ? FOUND : PARTIAL;
	}

	/** Greedy string pulling: keeps a point only when the straight line from the last kept point to the next one is blocked. */
	private int smooth(float[] p, int n) {
		int pointCount = n / 2;
		if (pointCount <= 2) return n;
		float[] out = new float[n];
		int o = 0;
		out[o++] = p[0];
		out[o++] = p[1];
		int anchor = 0;
		int i = 1;
		while (i < pointCount) {
			// furthest point visible from the anchor
			int far = i;
			for (int j = pointCount - 1; j > i; j--) {
				if (lineWalkable(p[anchor * 2], p[anchor * 2 + 1], p[j * 2], p[j * 2 + 1])) {
					far = j;
					break;
				}
			}
			out[o++] = p[far * 2];
			out[o++] = p[far * 2 + 1];
			anchor = far;
			i = far + 1;
		}
		System.arraycopy(out, 0, p, 0, o);
		return o;
	}

	/** True when every point of the segment is walkable (sampled every quarter of a cell). Ends excluded. */
	public boolean lineWalkable(float ax, float ay, float bx, float by) {
		float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
		float step = Math.min(cellW, cellH) * 0.25f;
		int steps = (int) Math.ceil(len / step);
		int lastCell = -2;
		int a = cellAt(ax, ay);
		for (int s = 1; s < steps; s++) {
			float t = s / (float) steps;
			int c = cellAt(ax + dx * t, ay + dy * t);
			if (c == lastCell) continue;
			lastCell = c;
			if (c != a && !walkable(c)) return false;
			// a costly cell on a straight line could be slower than the zig-zag that avoids it
			if (c >= 0 && cost[c] > 1 && (a < 0 || cost[c] > cost[a])) return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ obstacles (stamping shapes on cells)

	/** Cells covered by an axis-aligned rectangle (min/max corners). Fills {@code out}, returns how many (capped by out.length). */
	public int cellsInRect(float minX, float minY, float maxX, float maxY, int[] out) {
		int n = 0;
		int c0 = (int) Math.floor((minX - x0) / cellW) - 1, c1 = (int) Math.floor((maxX - x0) / cellW) + 1;
		int r0, r1;
		if (shape == SQUARE) {
			r0 = (int) Math.floor((minY - y0) / cellH) - 1;
			r1 = (int) Math.floor((maxY - y0) / cellH) + 1;
		} else {
			r0 = (int) Math.floor((minY - y0 - hexRv()) / cellH) - 1;
			r1 = (int) Math.floor((maxY - y0 - hexRv()) / cellH) + 2;
		}
		for (int r = Math.max(0, r0); r <= Math.min(rows - 1, r1); r++)
			for (int c = Math.max(0, c0); c <= Math.min(cols - 1, c1); c++) {
				if (!cellTouchesRect(c, r, minX, minY, maxX, maxY)) continue;
				if (n < out.length) out[n++] = index(c, r);
			}
		return n;
	}

	/** True when the cell overlaps the rectangle (more than a hair: touching edges do not count). */
	public boolean cellTouchesRect(int c, int r, float minX, float minY, float maxX, float maxY) {
		float cx = centerX(c, r), cy = centerY(c, r);
		float hw = cellW * 0.5f, hh = shape == SQUARE ? cellH * 0.5f : hexRv() * 0.75f;
		float e = Math.min(cellW, cellH) * 0.02f;
		return cx + hw - e > minX && cx - hw + e < maxX && cy + hh - e > minY && cy - hh + e < maxY;
	}

	/** True when the cell overlaps a convex or concave polygon (world points x,y,x,y...). */
	public boolean cellTouchesPolygon(int c, int r, float[] poly, int count) {
		float cx = centerX(c, r), cy = centerY(c, r);
		float hw = cellW * 0.45f, hh = (shape == SQUARE ? cellH : hexRv() * 1.5f) * 0.45f;
		// samples: center, corners and edge middles of the cell (slightly inside)
		for (int sy = -1; sy <= 1; sy++)
			for (int sx = -1; sx <= 1; sx++)
				if (pointInPolygon(cx + sx * hw, cy + sy * hh, poly, count)) return true;
		// a thin polygon may cross the cell without covering a sample: any polygon vertex inside the cell
		for (int i = 0; i < count; i++) {
			float px = poly[i * 2], py = poly[i * 2 + 1];
			if (Math.abs(px - cx) < hw && Math.abs(py - cy) < hh) return true;
		}
		return false;
	}

	public static boolean pointInPolygon(float x, float y, float[] poly, int count) {
		boolean in = false;
		for (int i = 0, j = count - 1; i < count; j = i++) {
			float xi = poly[i * 2], yi = poly[i * 2 + 1], xj = poly[j * 2], yj = poly[j * 2 + 1];
			if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-12f) + xi) in = !in;
		}
		return in;
	}

	/** True when the cell overlaps a circle. */
	public boolean cellTouchesCircle(int c, int r, float x, float y, float radius) {
		return distToCell(c, r, x, y) < radius - Math.min(cellW, cellH) * 0.02f;
	}

	// ------------------------------------------------------------------ painted data (the "Cells" property)

	/**
	 * The painted cells as text: {@code v1:cols,rows:} then runs "count + letter" (o open, x wall, b..i cost 2..9),
	 * e.g. "v1:4,2:3o1x4b". Empty text = everything open.
	 */
	public String encode() {
		StringBuilder sb = new StringBuilder();
		sb.append("v1:").append(cols).append(',').append(rows).append(':');
		int n = size();
		boolean any = false;
		char run = 0;
		int count = 0;
		for (int i = 0; i < n; i++) {
			char ch = painted[i] == WALL ? 'x' : (cost[i] > 1 ? (char) ('a' + cost[i] - 1) : 'o');
			if (ch != 'o') any = true;
			if (ch == run) count++;
			else {
				if (count > 0) sb.append(count).append(run);
				run = ch;
				count = 1;
			}
		}
		if (count > 0) sb.append(count).append(run);
		return any ? sb.toString() : "";
	}

	/** Reads text made by {@link #encode}; a grid of another size is scaled onto this one (nearest cell). */
	public void decode(String s) {
		java.util.Arrays.fill(painted, OPEN);
		java.util.Arrays.fill(cost, (byte) 1);
		if (s != null && s.startsWith("v1:")) {
			try {
				int a = s.indexOf(':', 3);
				String[] dims = s.substring(3, a).split(",");
				int sc = Integer.parseInt(dims[0].trim()), sr = Integer.parseInt(dims[1].trim());
				byte[] src = new byte[Math.max(0, sc * sr)];
				int p = a + 1, idx = 0;
				while (p < s.length() && idx < src.length) {
					int q = p;
					while (q < s.length() && Character.isDigit(s.charAt(q))) q++;
					if (q >= s.length()) break;
					int count = q > p ? Integer.parseInt(s.substring(p, q)) : 1;
					char ch = s.charAt(q);
					byte v = ch == 'x' ? (byte) -1 : (ch >= 'b' && ch <= 'i' ? (byte) (ch - 'a' + 1) : (byte) 0);
					for (int k = 0; k < count && idx < src.length; k++) src[idx++] = v;
					p = q + 1;
				}
				for (int r = 0; r < rows; r++)
					for (int c = 0; c < cols; c++) {
						int ssc = sc == cols ? c : Math.min(sc - 1, (int) ((c + 0.5f) * sc / cols));
						int ssr = sr == rows ? r : Math.min(sr - 1, (int) ((r + 0.5f) * sr / rows));
						if (ssc < 0 || ssr < 0) continue;
						byte v = src[ssr * sc + ssc];
						int i = index(c, r);
						if (v == -1) painted[i] = WALL;
						else if (v > 1) cost[i] = v;
					}
			} catch (RuntimeException ignored) {
			}
		}
		recomputeMinCost();
		recomputeAll();
	}
}
