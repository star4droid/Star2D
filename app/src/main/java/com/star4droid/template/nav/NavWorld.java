package com.star4droid.template.nav;

import com.badlogic.gdx.Gdx;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * The navigation state of one running scene: its grids, the obstacles on them, the walking elements and the queue of path
 * searches. At most {@link Nav#searchesPerFrame} searches run in a frame; the others wait for their turn (their path is
 * PENDING meanwhile), so fifty enemies do not freeze the game.
 */
public final class NavWorld {
	final StageImp stage;
	final HashMap<String, NavGrid> grids = new HashMap<String, NavGrid>();
	final IdentityHashMap<NavGrid, NavGridItem> gridItems = new IdentityHashMap<NavGrid, NavGridItem>();
	final ArrayList<NavObstacle> obstacles = new ArrayList<NavObstacle>();
	final IdentityHashMap<PlayerItem, NavAgent> agents = new IdentityHashMap<PlayerItem, NavAgent>();
	private final ArrayDeque<Request> queue = new ArrayDeque<Request>();
	private int searchesLeft, expandLeft;

	static final class Request {
		NavGrid grid;
		float sx, sy, tx, ty;
		PathHolder out;
		PlayerItem owner;
		boolean partial;
	}

	NavWorld(StageImp stage) {
		this.stage = stage;
		resetBudget();
	}

	private void resetBudget() {
		searchesLeft = Math.max(1, Nav.searchesPerFrame);
		expandLeft = Math.max(2000, Nav.expandPerFrame);
	}

	// ------------------------------------------------------------------ grids

	void addGrid(String name, NavGrid g, NavGridItem item) {
		g.name = name == null ? "" : name;
		grids.put(g.name, g);
		if (item != null) gridItems.put(g, item);
	}

	NavGrid grid(String name) {
		NavGrid g = grids.get(name);
		if (g != null) resolve(g);
		return g;
	}

	/** The only grid of the scene, or the first one (blocks with an empty grid field). */
	NavGrid anyGrid() {
		for (NavGrid g : grids.values()) {
			resolve(g);
			return g;
		}
		return null;
	}

	void resolve(NavGrid g) {
		NavGridItem it = gridItems.get(g);
		if (it != null) it.resolve();
	}

	// ------------------------------------------------------------------ obstacles

	void addObstacle(NavGrid g, PlayerItem item) {
		if (g == null || item == null) return;
		for (NavObstacle o : obstacles) if (o.grid == g && o.item == item) return;
		NavObstacle o = new NavObstacle(g, item);
		obstacles.add(o);
		o.update();
	}

	void removeObstacle(NavGrid g, PlayerItem item) {
		for (int i = obstacles.size() - 1; i >= 0; i--) {
			NavObstacle o = obstacles.get(i);
			if ((g == null || o.grid == g) && o.item == item) {
				o.release();
				obstacles.remove(i);
			}
		}
	}

	// ------------------------------------------------------------------ agents

	NavAgent agent(PlayerItem item, boolean create) {
		NavAgent a = agents.get(item);
		if (a == null && create && item != null) {
			a = new NavAgent(this, item);
			agents.put(item, a);
		}
		return a;
	}

	// ------------------------------------------------------------------ searches

	/** Finds a path now when this frame has budget left, otherwise queues it (out is PENDING until then). */
	void request(NavGrid g, float sx, float sy, float tx, float ty, PathHolder out, PlayerItem owner, boolean partial) {
		if (out == null) return;
		if (g == null) {
			out.clear();
			out.state = PathHolder.NO_PATH;
			if (owner != null) fireNoPath(owner);
			return;
		}
		// a newer request for the same holder replaces a waiting one
		for (Request r : queue) {
			if (r.out == out) {
				r.grid = g;
				r.sx = sx;
				r.sy = sy;
				r.tx = tx;
				r.ty = ty;
				r.owner = owner;
				r.partial = partial;
				return;
			}
		}
		Request r = new Request();
		r.grid = g;
		r.sx = sx;
		r.sy = sy;
		r.tx = tx;
		r.ty = ty;
		r.out = out;
		r.owner = owner;
		r.partial = partial;
		if (searchesLeft > 0 && expandLeft > 0 && queue.isEmpty()) run(r);
		else {
			if (out.state != PathHolder.READY) out.state = PathHolder.PENDING;
			queue.add(r);
		}
	}

	private void run(Request r) {
		searchesLeft--;
		int res = r.grid.findPath(r.sx, r.sy, r.tx, r.ty, r.out, true, r.partial);
		expandLeft -= r.grid.lastExpanded;
		if (res == NavGrid.NONE) {
			r.out.clear();
			r.out.state = PathHolder.NO_PATH;
			r.out.grid = r.grid;
			r.out.goalX = r.tx;
			r.out.goalY = r.ty;
			// an agent reports it itself when it reads the path; a plain search reports it now
			if (r.owner != null && agents.get(r.owner) == null) fireNoPath(r.owner);
		}
	}

	// ------------------------------------------------------------------ frame

	void update(float dt, boolean playing) {
		resetBudget();
		while (!queue.isEmpty() && searchesLeft > 0 && expandLeft > 0) run(queue.poll());
		for (int i = obstacles.size() - 1; i >= 0; i--) {
			if (!obstacles.get(i).update()) obstacles.remove(i);
		}
		if (!playing) return;
		if (!agents.isEmpty()) {
			ArrayList<NavAgent> list = new ArrayList<NavAgent>(agents.values());
			for (NavAgent a : list) {
				if (!NavAgent.alive(a.item)) {
					agents.remove(a.item);
					continue;
				}
				try {
					a.update(dt);
				} catch (RuntimeException e) {
					Gdx.app.error("Nav", "agent " + a.item.getName() + ": " + e);
				}
			}
		}
	}

	// ------------------------------------------------------------------ events (scene events "When path ends" ...)

	void fireEnd(PlayerItem item) {
		try {
			if (stage != null) stage.onPathEnd(item);
		} catch (RuntimeException e) {
			Gdx.app.error("Nav", "onPathEnd: " + e);
		}
	}

	void fireNoPath(PlayerItem item) {
		try {
			if (stage != null) stage.onNoPath(item);
		} catch (RuntimeException e) {
			Gdx.app.error("Nav", "onNoPath: " + e);
		}
	}

	void fireBlocked(PlayerItem item) {
		try {
			if (stage != null) stage.onPathBlocked(item);
		} catch (RuntimeException e) {
			Gdx.app.error("Nav", "onPathBlocked: " + e);
		}
	}
}
