package com.star4droid.template.nav;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;

import java.util.WeakHashMap;

/**
 * Path finding for blocks and scripts (category "Navigation"). Units: editor pixels (y up) in 2D scenes, meters on the
 * ground (x, z) in 3D scenes. A "grid" argument is a NavGrid element (or its name; null / "" = the scene's grid).
 *
 * <pre>
 * PathHolder way = new PathHolder();
 * Nav.findPath(grid, enemy, player, way);   // way is filled now, or a few frames later when many searches wait
 * Nav.follow(enemy, way, 120, "ONCE");      // walks it (physical elements by velocity), then raises "When path ends"
 * Nav.chase(enemy2, grid, player, 140);     // finds its way again whenever the player moves to another cell
 * </pre>
 */
public final class Nav {
	private Nav() {
	}

	/** Searches allowed per frame (the others wait for the next frames) and cells they may expand in a frame. */
	public static int searchesPerFrame = 4;
	public static int expandPerFrame = 40000;

	private static final WeakHashMap<StageImp, NavWorld> WORLDS = new WeakHashMap<StageImp, NavWorld>();
	private static StageImp last;

	/** The navigation of a scene (created on first use). */
	public static synchronized NavWorld world(StageImp stage) {
		if (stage == null) stage = last;
		if (stage == null) return null;
		NavWorld w = WORLDS.get(stage);
		if (w == null) {
			w = new NavWorld(stage);
			WORLDS.put(stage, w);
		}
		return w;
	}

	private static NavWorld worldOf(Object item) {
		StageImp s = null;
		if (item instanceof NavGridItem) s = ((NavGridItem) item).stage;
		else if (item instanceof PlayerItem) s = StageImp.of((PlayerItem) item);
		return world(s);
	}

	/** Engine: once per frame from the running scene (searches waiting, moving obstacles, walking elements). */
	public static void update(StageImp stage, float dt, boolean playing) {
		last = stage;
		NavWorld w;
		synchronized (Nav.class) {
			w = WORLDS.get(stage);
		}
		if (w != null) w.update(dt, playing);
	}

	/** Engine: the scene closed. */
	public static synchronized void dispose(StageImp stage) {
		WORLDS.remove(stage);
		if (last == stage) last = null;
	}

	// ------------------------------------------------------------------ grids

	/** The grid of a NavGrid element, of an element name, or the scene's grid for null / "". */
	public static NavGrid grid(Object grid) {
		if (grid instanceof NavGrid) return (NavGrid) grid;
		if (grid instanceof NavGridItem) {
			NavGridItem it = (NavGridItem) grid;
			it.resolve();
			return it.grid();
		}
		NavWorld w = worldOf(grid);
		if (w == null) return null;
		if (grid instanceof PlayerItem) return w.grid(((PlayerItem) grid).getName());
		String name = grid == null ? "" : String.valueOf(grid);
		if (name.isEmpty() || name.equals("null")) return w.anyGrid();
		NavGrid g = w.grid(name);
		return g != null ? g : w.anyGrid();
	}

	private static NavWorld worldFor(Object grid, Object other) {
		NavWorld w = worldOf(grid);
		if (w == null) w = worldOf(other);
		if (w == null) w = world(null);
		return w;
	}

	// ------------------------------------------------------------------ searches

	/** Path from one element to another into {@code out}. */
	public static void findPath(Object grid, PlayerItem from, PlayerItem to, PathHolder out) {
		if (from == null || to == null || out == null) return;
		NavWorld w = worldFor(grid, from);
		if (w == null) return;
		w.request(grid(grid), NavAgent.x(from), NavAgent.y(from), NavAgent.x(to), NavAgent.y(to), out, from, false);
	}

	/** Path between two points into {@code out}. */
	public static void findPathXY(Object grid, float fromX, float fromY, float toX, float toY, PathHolder out) {
		if (out == null) return;
		NavWorld w = worldFor(grid, null);
		if (w == null) return;
		w.request(grid(grid), fromX, fromY, toX, toY, out, null, false);
	}

	/** Path from an element to a point into {@code out}. */
	public static void findPathTo(Object grid, PlayerItem from, float toX, float toY, PathHolder out) {
		if (from == null || out == null) return;
		NavWorld w = worldFor(grid, from);
		if (w == null) return;
		w.request(grid(grid), NavAgent.x(from), NavAgent.y(from), toX, toY, out, from, true);
	}

	// ------------------------------------------------------------------ cells and obstacles

	/** The element blocks the cells it covers (follows it when it moves, opens them when it is destroyed). */
	public static void addObstacle(Object grid, PlayerItem obstacle) {
		NavWorld w = worldFor(grid, obstacle);
		if (w != null) w.addObstacle(grid(grid), obstacle);
	}

	public static void removeObstacle(Object grid, PlayerItem obstacle) {
		NavWorld w = worldFor(grid, obstacle);
		if (w != null) w.removeObstacle(grid == null || "".equals(grid) ? null : grid(grid), obstacle);
	}

	/** Closes (true) or opens (false) the cell under a point. */
	public static void setCell(Object grid, float x, float y, boolean blocked) {
		NavGrid g = grid(grid);
		if (g != null) g.setWall(g.cellAt(x, y), blocked);
	}

	/** Cost of the cell under a point: 1 normal ... 9 very slow (mud, water). */
	public static void setCellCost(Object grid, float x, float y, float cost) {
		NavGrid g = grid(grid);
		if (g != null) g.setCost(g.cellAt(x, y), Math.round(cost));
	}

	/** True when an agent can stand at the point. */
	public static boolean walkable(Object grid, float x, float y) {
		NavGrid g = grid(grid);
		return g != null && g.walkableAt(x, y);
	}

	// ------------------------------------------------------------------ walking

	static int mode(String mode) {
		if (mode == null) return NavAgent.ONCE;
		String m = mode.trim().toUpperCase().replace("-", "_").replace(" ", "_");
		if (m.startsWith("LOOP") || m.equals("REPEAT")) return NavAgent.LOOP;
		if (m.startsWith("PING") || m.equals("BACK_AND_FORTH") || m.equals("YOYO")) return NavAgent.PING_PONG;
		return NavAgent.ONCE;
	}

	/**
	 * Walks the element along the path at {@code speed} (px/s in 2D, m/s in 3D), starting from the nearest point ahead.
	 * mode: ONCE (then "When path ends"), LOOP or PING_PONG. Waits by itself while the path is still being searched.
	 */
	public static void follow(PlayerItem item, PathHolder path, float speed, String mode) {
		NavWorld w = worldOf(item);
		if (w == null || item == null) return;
		NavAgent a = w.agent(item, true);
		a.startFollow(path, speed, mode(mode));
	}

	public static void follow(PlayerItem item, PathHolder path, float speed) {
		follow(item, path, speed, "ONCE");
	}

	/**
	 * Walks to a point along the shortest way around the walls (finds the path, then follows it once; "When path ends" at the
	 * end). A point inside a wall: it stops at the nearest place it can reach.
	 */
	public static void goTo(PlayerItem item, Object grid, float x, float y, float speed) {
		if (item == null) return;
		NavWorld w = worldFor(grid, item);
		if (w == null) return;
		PathHolder p = new PathHolder();
		w.request(grid(grid), NavAgent.x(item), NavAgent.y(item), x, y, p, item, true);
		w.agent(item, true).startFollow(p, speed, NavAgent.ONCE);
	}

	/** Walks to where another element is now (once; use chase to keep following it). */
	public static void goToElement(PlayerItem item, Object grid, PlayerItem target, float speed) {
		if (item == null || target == null) return;
		goTo(item, grid, NavAgent.x(target), NavAgent.y(target), speed);
	}

	/** Runs after a target on the grid: the path is found again when the target moves to another cell or the grid changes. */
	public static NavAgent chase(PlayerItem item, Object grid, PlayerItem target, float speed) {
		NavWorld w = worldFor(grid, item);
		if (w == null || item == null) return null;
		NavAgent a = w.agent(item, true);
		a.startChase(grid(grid), target, speed, false);
		return a;
	}

	/** Runs away from a target on the grid (to the farthest place it can reach around it). */
	public static NavAgent flee(PlayerItem item, Object grid, PlayerItem target, float speed) {
		NavWorld w = worldFor(grid, item);
		if (w == null || item == null) return null;
		NavAgent a = w.agent(item, true);
		a.startChase(grid(grid), target, speed, true);
		return a;
	}

	public static void pause(PlayerItem item) {
		NavAgent a = agentOf(item);
		if (a != null && !a.paused) {
			a.paused = true;
			a.halt();
		}
	}

	public static void resume(PlayerItem item) {
		NavAgent a = agentOf(item);
		if (a != null) a.paused = false;
	}

	/** Stops walking for good (the element keeps its place). */
	public static void stop(PlayerItem item) {
		NavAgent a = agentOf(item);
		if (a != null) a.stop();
	}

	/** True while the element walks a path (not paused, not arrived). */
	public static boolean isFollowing(PlayerItem item) {
		NavAgent a = agentOf(item);
		return a != null && a.isMoving();
	}

	/** Changes the walking speed of a following / chasing element. */
	public static void setSpeed(PlayerItem item, float speed) {
		NavAgent a = agentOf(item);
		if (a != null) a.speed = speed;
	}

	static NavAgent agentOf(PlayerItem item) {
		NavWorld w = worldOf(item);
		return w == null ? null : w.agent(item, false);
	}

	// ------------------------------------------------------------------ positions (blocks)

	/** Center x of an element (px in 2D scenes, meters in 3D). */
	public static float xOf(PlayerItem item) {
		return item == null ? 0 : NavAgent.x(item);
	}

	/** Center y of an element in 2D (px), its z on the ground in 3D (meters). */
	public static float yOf(PlayerItem item) {
		return item == null ? 0 : NavAgent.y(item);
	}

	/** Distance between the centers of two elements (on the ground in 3D). */
	public static float distance(PlayerItem a, PlayerItem b) {
		if (a == null || b == null) return 0;
		float dx = NavAgent.x(a) - NavAgent.x(b), dy = NavAgent.y(a) - NavAgent.y(b);
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	/** Puts the center of an element at a point (px in 2D; meters x, z in 3D, keeping its height) and stops it. */
	public static void place(PlayerItem item, float x, float y) {
		if (item == null) return;
		if (item instanceof com.star4droid.template.d3.Object3D) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) item;
			o.setPosition3(x, o.position.y, y);
			return;
		}
		com.badlogic.gdx.physics.box2d.Body b = null;
		try {
			b = item.getBody();
		} catch (RuntimeException ignored) {
		}
		if (b != null) {
			float w = com.star4droid.template.behaviors.Behavior.W;
			if (!b.getWorld().isLocked()) {
				b.setTransform(x * w, y * w, b.getAngle());
				b.setLinearVelocity(0, 0);
				b.setAwake(true);
			}
			return;
		}
		com.badlogic.gdx.scenes.scene2d.Actor a = item.getActor();
		if (a != null) a.setPosition(x - a.getWidth() * 0.5f, y - a.getHeight() * 0.5f);
	}

	// ------------------------------------------------------------------ path values (blocks)

	public static float length(PathHolder p) {
		return p == null ? 0 : p.length();
	}

	public static int size(PathHolder p) {
		return p == null ? 0 : p.size();
	}

	public static float pointX(PathHolder p, float index) {
		return p == null ? 0 : p.x(Math.round(index));
	}

	public static float pointY(PathHolder p, float index) {
		return p == null ? 0 : p.y(Math.round(index));
	}

	/** Index of the point of the path closest to an element. */
	public static int nearest(PathHolder p, PlayerItem item) {
		if (p == null || item == null) return -1;
		return p.nearestIndex(NavAgent.x(item), NavAgent.y(item));
	}

	public static boolean found(PathHolder p) {
		return p != null && p.isReady();
	}

	public static boolean notFound(PathHolder p) {
		return p != null && p.notFound();
	}

	public static void reverse(PathHolder p) {
		if (p != null) p.reverse();
	}

	public static void clear(PathHolder p) {
		if (p != null) p.clear();
	}

	public static void addPoint(PathHolder p, float x, float y) {
		if (p != null) p.add(x, y);
	}

	/** Distance left along the path for an element walking it. */
	public static float remaining(PlayerItem item) {
		NavAgent a = agentOf(item);
		if (a == null || a.path == null) return 0;
		return a.path.lengthFrom(a.x(), a.y(), a.index);
	}
}
