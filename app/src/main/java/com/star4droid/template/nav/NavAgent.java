package com.star4droid.template.nav;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;

/**
 * Walks one element along a path: a path variable (follow), or a path it finds again and again toward a target (chase) or
 * away from it (flee). Physical elements move by velocity (they still collide), the others by position. 3D elements walk
 * on the ground (x, z), dynamic ones keep their falling speed.
 */
public final class NavAgent {
	public static final int ONCE = 0, LOOP = 1, PING_PONG = 2;
	public static final int FOLLOW = 0, CHASE = 1, FLEE = 2;

	final PlayerItem item;
	final NavWorld world;
	int kind = FOLLOW;
	PathHolder path;
	private int pathRevision = -1;
	int mode = ONCE;
	float speed = 100;
	int index, dir = 1;
	boolean paused, active = true;
	// chase / flee
	PlayerItem target;
	NavGrid grid;
	float stopDistance = 0, fleeDistance = 300, repathEvery = 0.35f;
	private float repathTimer;
	private int lastTargetCell = -2;
	private final PathHolder own = new PathHolder();
	private boolean noPathFired, reachedFired;
	private int checkedVersion = -1;
	/** A behavior (Smart Chase) that wants the events too. */
	Listener listener;
	/** Face the walking direction: 0 no, 1 mirror the image, 2 turn the element. */
	int facing;
	boolean artFacesLeft;
	float faceOffset;
	private float baseScaleX = Float.NaN;

	/** Events of one walking element (Smart Chase passes them on as its own events). */
	public interface Listener {
		void onReach();

		void onNoPath();

		void onBlocked();

		void onEnd();
	}

	public void setListener(Listener l) {
		listener = l;
	}

	/** Face the walking direction: 0 no, 1 mirror the image, 2 turn the element (art drawn facing right, offset in degrees). */
	public void setFacing(int mode, boolean artFacesLeft, float offset) {
		facing = mode;
		this.artFacesLeft = artFacesLeft;
		faceOffset = offset;
	}

	/** Chase: stop this close to the target (0 = when it can see it right next to it). */
	public void setStopDistance(float d) {
		stopDistance = Math.max(0, d);
	}

	public void setFleeDistance(float d) {
		fleeDistance = Math.max(1, d);
	}

	/** Chase: look for a new way at most this often (seconds). */
	public void setRepathTime(float s) {
		repathEvery = Math.max(0.05f, s);
	}

	public void setSpeed(float s) {
		speed = s;
	}

	public boolean isActive() {
		return active && !paused;
	}

	NavAgent(NavWorld world, PlayerItem item) {
		this.world = world;
		this.item = item;
	}

	// ------------------------------------------------------------------ position helpers (scene units)

	boolean is3D() {
		return item instanceof com.star4droid.template.d3.Object3D;
	}

	float x() {
		if (is3D()) return ((com.star4droid.template.d3.Object3D) item).position.x;
		return Behavior.cx(item);
	}

	float y() {
		if (is3D()) return ((com.star4droid.template.d3.Object3D) item).position.z;
		return Behavior.cy(item);
	}

	static float x(PlayerItem it) {
		if (it instanceof com.star4droid.template.d3.Object3D) return ((com.star4droid.template.d3.Object3D) it).position.x;
		return Behavior.cx(it);
	}

	static float y(PlayerItem it) {
		if (it instanceof com.star4droid.template.d3.Object3D) return ((com.star4droid.template.d3.Object3D) it).position.z;
		return Behavior.cy(it);
	}

	static boolean alive(PlayerItem it) {
		if (it instanceof com.star4droid.template.d3.Object3D) return !((com.star4droid.template.d3.Object3D) it).isDestroyed();
		return Behavior.alive(it);
	}

	// ------------------------------------------------------------------ control

	void startFollow(PathHolder p, float speed, int mode) {
		kind = FOLLOW;
		path = p;
		pathRevision = -1;
		this.speed = speed;
		this.mode = mode;
		paused = false;
		active = true;
		target = null;
		noPathFired = false;
		dir = 1;
	}

	void startChase(NavGrid g, PlayerItem t, float speed, boolean flee) {
		kind = flee ? FLEE : CHASE;
		grid = g;
		target = t;
		this.speed = speed;
		path = own;
		own.clear();
		pathRevision = -1;
		mode = ONCE;
		paused = false;
		active = true;
		repathTimer = 0;
		lastTargetCell = -2;
		noPathFired = false;
		reachedFired = false;
		dir = 1;
	}

	void stop() {
		active = false;
		halt();
	}

	/** Stops the element where it is (velocity 0 for physical elements). */
	void halt() {
		if (is3D()) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) item;
			if (o.isDynamic()) o.setVelocityKeepY(0, 0);
			return;
		}
		Body b = body();
		if (b != null && b.getType() != BodyDef.BodyType.StaticBody) {
			// top-down: stop dead; with gravity: stop walking but keep falling
			if (b.getType() == BodyDef.BodyType.KinematicBody || isTopDown(b)) b.setLinearVelocity(0, 0);
			else b.setLinearVelocity(0, b.getLinearVelocity().y);
		}
	}

	private boolean isTopDown(Body b) {
		StageImp s = world.stage;
		return s == null || s.world == null || s.world.getGravity().isZero(0.001f) || b.getGravityScale() == 0;
	}

	private Body body() {
		try {
			return item.getBody();
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ frame

	void update(float dt) {
		if (!active || paused || !alive(item)) return;
		if (kind != FOLLOW) updateChase(dt);
		PathHolder p = path;
		if (p == null) return;
		if (p.state == PathHolder.PENDING) return; // the search waits for its turn
		if (p.state == PathHolder.NO_PATH) {
			if (!noPathFired) {
				noPathFired = true;
				halt();
				if (listener != null) listener.onNoPath();
				world.fireNoPath(item);
			}
			return;
		}
		if (p.revision != pathRevision) {
			// a new path (or the user changed it): start from the nearest point ahead
			pathRevision = p.revision;
			index = kind == FOLLOW ? p.nearestAhead(x(), y()) : 0;
			dir = 1;
			noPathFired = false;
			checkedVersion = p.grid == null ? -1 : p.grid.version;
		}
		if (p.size() == 0) {
			halt();
			return;
		}
		// the grid changed: is the rest of the path still free?
		if (p.grid != null && p.grid.version != checkedVersion) {
			checkedVersion = p.grid.version;
			if (blockedAhead(p)) {
				if (listener != null) listener.onBlocked();
				world.fireBlocked(item);
				if (kind != FOLLOW) {
					repathTimer = 0; // find another way right now
					lastTargetCell = -2;
				} else if (p.grid != null) {
					// a found path: search again to the same goal
					world.request(p.grid, x(), y(), p.goalX, p.goalY, p, item, true);
					return;
				}
			}
		}
		walk(p, dt);
	}

	private boolean blockedAhead(PathHolder p) {
		NavGrid g = p.grid;
		float px = x(), py = y();
		int from = Math.max(0, Math.min(index, p.size() - 1));
		for (int i = from; i < p.size(); i++) {
			float qx = p.x(i), qy = p.y(i);
			if (!g.walkableAt(qx, qy) && g.cellAt(qx, qy) >= 0) return true;
			if (!g.lineWalkable(px, py, qx, qy) && g.walkableAt(px, py)) return true;
			px = qx;
			py = qy;
		}
		return false;
	}

	private void updateChase(float dt) {
		if (grid == null || !alive(target)) {
			if (path != null && path.size() > 0) {
				own.clear();
				halt();
			}
			return;
		}
		float tx = x(target), ty = y(target), mx = x(), my = y();
		float dx = tx - mx, dy = ty - my, dist = (float) Math.sqrt(dx * dx + dy * dy);
		if (kind == CHASE) {
			float stopAt = Math.max(stopDistance, 1f);
			if (dist <= stopAt && (stopDistance > 0 || grid.lineWalkable(mx, my, tx, ty))) {
				if (!reachedFired) {
					reachedFired = true;
					halt();
					own.clear();
					if (listener != null) listener.onReach();
				}
				return;
			}
			reachedFired = false;
		}
		repathTimer -= dt;
		int tc = grid.cellAt(tx, ty);
		boolean moved = tc != lastTargetCell;
		if (repathTimer <= 0 && (moved || own.size() == 0 || own.state != PathHolder.READY)) {
			repathTimer = repathEvery;
			lastTargetCell = tc;
			if (kind == CHASE) world.request(grid, mx, my, tx, ty, own, item, true);
			else {
				float[] goal = fleeGoal(mx, my, tx, ty);
				if (goal != null) world.request(grid, mx, my, goal[0], goal[1], own, item, true);
			}
		}
	}

	/** A walkable place away from the target (the farthest of a few candidates around the agent). */
	private float[] fleeGoal(float mx, float my, float tx, float ty) {
		float best = -1;
		float[] out = null;
		float away = (float) Math.atan2(my - ty, mx - tx);
		for (int k = 0; k < 16; k++) {
			float a = away + (float) ((k % 2 == 0 ? 1 : -1) * (k / 2) * Math.PI / 8);
			for (float d = fleeDistance; d >= Math.min(grid.cellW, grid.cellH); d *= 0.6f) {
				float gx = mx + (float) Math.cos(a) * d, gy = my + (float) Math.sin(a) * d;
				if (!grid.walkableAt(gx, gy)) continue;
				float sc = (gx - tx) * (gx - tx) + (gy - ty) * (gy - ty);
				if (sc > best) {
					best = sc;
					out = new float[] {gx, gy};
				}
				break;
			}
		}
		return out;
	}

	private void walk(PathHolder p, float dt) {
		int n = p.size();
		if (index >= n || index < 0) {
			if (!finishLeg(p)) return;
		}
		float mx = x(), my = y();
		float step = speed * Math.max(dt, 1e-4f);
		float tx = p.x(index), ty = p.y(index);
		float dx = tx - mx, dy = ty - my, dist = (float) Math.sqrt(dx * dx + dy * dy);
		float arrive = Math.max(step * 1.25f, is3D() ? 0.05f : 2f);
		int guard = 0;
		while (dist <= arrive && guard++ < 8) {
			index += dir;
			if (index >= n || index < 0) {
				if (!finishLeg(p)) return;
			}
			tx = p.x(index);
			ty = p.y(index);
			dx = tx - mx;
			dy = ty - my;
			dist = (float) Math.sqrt(dx * dx + dy * dy);
		}
		if (dist < 1e-5f) return;
		move(dx / dist, dy / dist, Math.min(step, dist), dt);
	}

	/** End of the path reached: loop, turn back or stop. False when it stopped. */
	private boolean finishLeg(PathHolder p) {
		int n = p.size();
		if (kind == FOLLOW && mode == LOOP && n > 1) {
			index = dir > 0 ? 0 : n - 1;
			return true;
		}
		if (kind == FOLLOW && mode == PING_PONG && n > 1) {
			dir = -dir;
			index = dir > 0 ? 1 : n - 2;
			return true;
		}
		// arrived
		index = n;
		halt();
		if (kind == FOLLOW) {
			active = false;
			if (listener != null) listener.onEnd();
			world.fireEnd(item);
		} else {
			own.clear();
			own.state = PathHolder.EMPTY;
		}
		return false;
	}

	private void move(float ux, float uy, float step, float dt) {
		face(ux, uy);
		if (is3D()) {
			com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) item;
			if (o.isDynamic()) o.setVelocityKeepY(ux * speed, uy * speed);
			else o.setPosition3(o.position.x + ux * step, o.position.y, o.position.z + uy * step);
			return;
		}
		Body b = body();
		if (b != null) {
			if (b.getType() == BodyDef.BodyType.StaticBody) {
				b.setTransform(b.getPosition().x + ux * step * Behavior.W, b.getPosition().y + uy * step * Behavior.W, b.getAngle());
				return;
			}
			int steps = world.stage == null ? 1 : Math.max(1, world.stage.getSteps());
			float k = Behavior.W / steps;
			boolean keepFall = b.getType() == BodyDef.BodyType.DynamicBody && !isTopDown(b);
			if (keepFall) b.setLinearVelocity(ux * speed * k, b.getLinearVelocity().y);
			else b.setLinearVelocity(ux * speed * k, uy * speed * k);
			b.setAwake(true);
			return;
		}
		com.badlogic.gdx.scenes.scene2d.Actor a = item.getActor();
		if (a != null) a.moveBy(ux * step, uy * step);
	}

	private void face(float ux, float uy) {
		if (facing == 0 || (Math.abs(ux) < 1e-3f && Math.abs(uy) < 1e-3f)) return;
		if (is3D()) {
			((com.star4droid.template.d3.Object3D) item).setYaw3((float) Math.toDegrees(Math.atan2(-ux, -uy)) + faceOffset);
			return;
		}
		com.badlogic.gdx.scenes.scene2d.Actor a = item.getActor();
		if (a == null) return;
		if (facing == 1) {
			if (Float.isNaN(baseScaleX)) baseScaleX = Math.abs(a.getScaleX());
			if (Math.abs(ux) > 0.05f) a.setScaleX(((ux < 0) != artFacesLeft) ? -baseScaleX : baseScaleX);
		} else {
			float rot = (float) Math.toDegrees(Math.atan2(uy, ux)) - faceOffset;
			Body b = body();
			if (b != null) b.setTransform(b.getPosition(), (float) Math.toRadians(rot));
			else a.setRotation(rot);
		}
	}

	boolean isMoving() {
		return active && !paused && path != null && path.state == PathHolder.READY && path.size() > 0 && index >= 0 && index < path.size();
	}
}
