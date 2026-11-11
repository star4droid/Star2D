package com.star4droid.template.nav;

/**
 * A path: a list of points (editor pixels, y up, in 2D scenes; x / z meters in 3D scenes). It is a variable type of the
 * Variables window and of the element variables, filled by the "find path" blocks, or drawn by hand in the editor as a fixed
 * patrol route ({@link #of}).
 *
 * <pre>
 * PathHolder patrol = PathHolder.of(100, 200, 400, 200, 400, 500);
 * com.star4droid.template.nav.Nav.follow(guard, patrol, 120, "LOOP");
 * </pre>
 */
public class PathHolder {
	public static final int EMPTY = 0, PENDING = 1, READY = 2, NO_PATH = 3;

	private float[] xy = new float[16];
	private int count;
	/** EMPTY, PENDING (search waiting for its turn), READY or NO_PATH. */
	public int state = EMPTY;
	/** Grid the path was found on (null for a drawn path) and its version then: changes of the grid may block the path. */
	public NavGrid grid;
	public int gridVersion;
	/** Where the search wanted to go. */
	public float goalX, goalY;
	/** True when the goal could not be reached and the path ends at the closest place instead. */
	public boolean partial;
	/** Grows on every change of the points (followers restart when the path they walk is replaced). */
	public int revision;

	public PathHolder() {
	}

	/** A path from x, y pairs: PathHolder.of(x1, y1, x2, y2, ...). */
	public static PathHolder of(float... points) {
		PathHolder p = new PathHolder();
		if (points != null) for (int i = 0; i + 1 < points.length; i += 2) p.add(points[i], points[i + 1]);
		return p;
	}

	/** A copy of another path (null = empty). */
	public static PathHolder copyOf(PathHolder other) {
		PathHolder p = new PathHolder();
		p.set(other);
		return p;
	}

	public void set(PathHolder other) {
		clear();
		if (other == null) return;
		for (int i = 0; i < other.count; i++) add(other.xy[i * 2], other.xy[i * 2 + 1]);
		state = other.state;
		grid = other.grid;
		gridVersion = other.gridVersion;
		goalX = other.goalX;
		goalY = other.goalY;
		partial = other.partial;
	}

	public void clear() {
		revision++;
		count = 0;
		state = EMPTY;
		grid = null;
		partial = false;
	}

	/** Adds a point at the end. */
	public PathHolder add(float x, float y) {
		if (count * 2 + 2 > xy.length) xy = java.util.Arrays.copyOf(xy, Math.max(16, xy.length * 2));
		xy[count * 2] = x;
		xy[count * 2 + 1] = y;
		count++;
		revision++;
		if (state == EMPTY || state == NO_PATH) state = READY;
		return this;
	}

	/** Inserts a point before the index (index = size() adds at the end). */
	public void insert(int index, float x, float y) {
		index = Math.max(0, Math.min(count, index));
		add(0, 0);
		System.arraycopy(xy, index * 2, xy, index * 2 + 2, (count - 1 - index) * 2);
		xy[index * 2] = x;
		xy[index * 2 + 1] = y;
	}

	public void remove(int index) {
		if (index < 0 || index >= count) return;
		System.arraycopy(xy, index * 2 + 2, xy, index * 2, (count - 1 - index) * 2);
		count--;
		revision++;
		if (count == 0 && state == READY) state = EMPTY;
	}

	/** Number of points. */
	public int size() {
		return count;
	}

	public boolean isEmpty() {
		return count == 0;
	}

	/** True when the points are ready (not waiting for a search, not failed). */
	public boolean isReady() {
		return state == READY && count > 0;
	}

	/** True while a search for this path waits for its turn. */
	public boolean isPending() {
		return state == PENDING;
	}

	/** True when the last search found no way. */
	public boolean notFound() {
		return state == NO_PATH;
	}

	public float x(int i) {
		return i >= 0 && i < count ? xy[i * 2] : 0;
	}

	public float y(int i) {
		return i >= 0 && i < count ? xy[i * 2 + 1] : 0;
	}

	public void setPoint(int i, float x, float y) {
		if (i < 0 || i >= count) return;
		xy[i * 2] = x;
		xy[i * 2 + 1] = y;
		revision++;
	}

	public float lastX() {
		return x(count - 1);
	}

	public float lastY() {
		return y(count - 1);
	}

	/** Total length (sum of the segments). */
	public float length() {
		float l = 0;
		for (int i = 1; i < count; i++) {
			float dx = xy[i * 2] - xy[i * 2 - 2], dy = xy[i * 2 + 1] - xy[i * 2 - 1];
			l += (float) Math.sqrt(dx * dx + dy * dy);
		}
		return l;
	}

	/** Length from the point x, y through the points from index {@code from} to the end. */
	public float lengthFrom(float x, float y, int from) {
		if (from >= count) return 0;
		from = Math.max(0, from);
		float dx = xy[from * 2] - x, dy = xy[from * 2 + 1] - y;
		float l = (float) Math.sqrt(dx * dx + dy * dy);
		for (int i = from + 1; i < count; i++) {
			float ex = xy[i * 2] - xy[i * 2 - 2], ey = xy[i * 2 + 1] - xy[i * 2 - 1];
			l += (float) Math.sqrt(ex * ex + ey * ey);
		}
		return l;
	}

	/** Index of the point closest to x, y (-1 when empty). */
	public int nearestIndex(float x, float y) {
		int best = -1;
		float bd = Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			float dx = xy[i * 2] - x, dy = xy[i * 2 + 1] - y, d = dx * dx + dy * dy;
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		return best;
	}

	/**
	 * The index to walk to first from x, y: the end of the segment the position is closest to ("the nearest point ahead").
	 * 0 when the position is before the first point.
	 */
	public int nearestAhead(float x, float y) {
		if (count <= 1) return 0;
		int best = 0;
		float bd = Float.MAX_VALUE;
		boolean beforeFirst = false;
		for (int i = 0; i + 1 < count; i++) {
			float ax = xy[i * 2], ay = xy[i * 2 + 1], bx = xy[i * 2 + 2], by = xy[i * 2 + 3];
			float ex = bx - ax, ey = by - ay, len2 = ex * ex + ey * ey;
			float t = len2 < 1e-9f ? 0 : ((x - ax) * ex + (y - ay) * ey) / len2;
			float tc = Math.max(0, Math.min(1, t));
			float px = ax + ex * tc - x, py = ay + ey * tc - y, d = px * px + py * py;
			if (d < bd - 1e-6f) {
				bd = d;
				best = i + 1;
				beforeFirst = i == 0 && t <= 0;
			}
		}
		return beforeFirst ? 0 : best;
	}

	/** X of the point closest to x, y. */
	public float nearestX(float x, float y) {
		return x(nearestIndex(x, y));
	}

	public float nearestY(float x, float y) {
		return y(nearestIndex(x, y));
	}

	/** Reverses the order of the points (walk it back). */
	public void reverse() {
		revision++;
		for (int i = 0, j = count - 1; i < j; i++, j--) {
			float tx = xy[i * 2], ty = xy[i * 2 + 1];
			xy[i * 2] = xy[j * 2];
			xy[i * 2 + 1] = xy[j * 2 + 1];
			xy[j * 2] = tx;
			xy[j * 2 + 1] = ty;
		}
	}

	/** Point at a distance along the path from its start into out[0], out[1] (clamped to the ends). */
	public void pointAt(float distance, float[] out) {
		if (count == 0) {
			out[0] = out[1] = 0;
			return;
		}
		float d = Math.max(0, distance);
		for (int i = 1; i < count; i++) {
			float ax = xy[i * 2 - 2], ay = xy[i * 2 - 1], bx = xy[i * 2], by = xy[i * 2 + 1];
			float l = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
			if (d <= l || i == count - 1) {
				float t = l < 1e-6f ? 1 : Math.min(1, d / l);
				out[0] = ax + (bx - ax) * t;
				out[1] = ay + (by - ay) * t;
				return;
			}
			d -= l;
		}
		out[0] = xy[0];
		out[1] = xy[1];
	}

	/** The points as "x1,y1,x2,y2" (editor storage, debugging). */
	public String toText() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count * 2; i++) {
			if (i > 0) sb.append(',');
			float v = xy[i];
			if (v == (int) v) sb.append((int) v);
			else sb.append(Math.round(v * 100) / 100f);
		}
		return sb.toString();
	}

	/** Reads "x1,y1,x2,y2" (spaces allowed). */
	public static PathHolder parse(String text) {
		PathHolder p = new PathHolder();
		if (text == null) return p;
		String[] parts = text.replace(';', ',').split(",");
		float[] v = new float[parts.length];
		int n = 0;
		for (String s : parts) {
			s = s.trim().replace("f", "");
			if (s.isEmpty()) continue;
			try {
				v[n++] = Float.parseFloat(s);
			} catch (NumberFormatException ignored) {
			}
		}
		for (int i = 0; i + 1 < n; i += 2) p.add(v[i], v[i + 1]);
		return p;
	}

	@Override
	public String toString() {
		return "PathHolder[" + count + " points, " + (state == PENDING ? "pending" : state == NO_PATH ? "no path" : state == READY ? "ready" : "empty") + "]";
	}
}
