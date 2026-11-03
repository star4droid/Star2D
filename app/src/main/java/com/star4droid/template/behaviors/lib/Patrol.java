package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Patrol", icon = "swap-horizontal", category = "AI", targets = BehaviorInfo.Target.BOTH,
		events = {"onTurn()", "onReachWaypoint(int index)"},
		desc = "Walks back and forth by itself (enemies, guards, moving hazards): a distance to each side, or through waypoint elements. Waits at the ends, flips its image and can turn at walls and ledges. Pauses while a Chase behavior on the same element is chasing.")
public class Patrol extends Behavior {
	public enum Mode { DISTANCE, WAYPOINTS }

	public enum Axis { HORIZONTAL, VERTICAL }

	@Header("Route")
	@Param(desc = "DISTANCE: go back and forth around the start position. WAYPOINTS: visit the elements of the list in order.")
	public Mode mode = Mode.DISTANCE;
	@Param(desc = "DISTANCE only: left/right or up/down.")
	public Axis axis = Axis.HORIZONTAL;
	@Param(desc = "DISTANCE only: how far it walks to the left (or down) of the start.", unit = "px", min = 0, max = 10000)
	public float rangeBack = 100;
	@Param(desc = "DISTANCE only: how far it walks to the right (or up) of the start.", unit = "px", min = 0, max = 10000)
	public float rangeForward = 100;
	@Param(desc = "DISTANCE only: first walk to the left / down.")
	public boolean startBackwards = false;
	@Param(desc = "WAYPOINTS only: the elements to walk to, in order (they can stay invisible).")
	public List<PlayerItem> waypoints = new ArrayList<PlayerItem>();
	@Param(desc = "WAYPOINTS only: after the last one go back to the first (loop). Off = walk back along the route (ping-pong).")
	public boolean loopWaypoints = false;

	@Header("Motion")
	@Param(desc = "Walking speed.", unit = "px/s", min = 0, max = 100000)
	public float speed = 80;
	@Param(desc = "How long it waits at each end / waypoint.", unit = "s", min = 0, max = 60, step = 0.1)
	public float waitTime = 0.5f;
	@Param(desc = "Ignore gravity (birds, drones). Vertical routes and waypoints always ignore it.")
	public boolean flying = false;

	@Header("Turning")
	@Param(desc = "Mirror the image when it walks left.")
	public boolean flipOnTurn = true;
	@Param(desc = "The image is drawn facing left (so it is mirrored when walking right).")
	public boolean artFacesLeft = false;
	@Param(desc = "Turn around when a wall (static or moving platform) is right in front. Physical elements only.")
	public boolean turnAtWalls = true;
	@Param(desc = "Turn around instead of walking off a ledge. Physical elements only.")
	public boolean turnAtLedges = false;
	@Param(desc = "How far below its feet there must be ground to keep walking.", unit = "px", min = 1, max = 500)
	public float ledgeDepth = 14;
	@Param(desc = "Do nothing while a Chase behavior of this element is chasing.")
	public boolean yieldToChase = true;

	private float startX, startY, baseScaleX = 1, waitLeft, turnCooldown, savedGravity = 1;
	private int dir = 1, idx, idxStep = 1;
	private boolean waiting, gravityChanged, started;
	private final Vector2 p1 = new Vector2(), p2 = new Vector2();
	private boolean rayHit, raySolidOnly;
	private Body rayOwn;
	private final RayCastCallback ray = new RayCastCallback() {
		public float reportRayFixture(Fixture f, Vector2 point, Vector2 normal, float fraction) {
			if (f.isSensor() || f.getBody() == rayOwn) return -1f;
			if (raySolidOnly && f.getBody().getType() == BodyDef.BodyType.DynamicBody) return -1f;
			rayHit = true;
			return 0f;
		}
	};

	public void onStart() {
		startX = centerX();
		startY = centerY();
		baseScaleX = Math.abs(actor().getScaleX());
		dir = startBackwards ? -1 : 1;
		idx = 0;
		idxStep = 1;
		started = true;
		applyGravity();
		face(dir);
	}

	public void onEnable() {
		if (started) applyGravity();
	}

	private void applyGravity() {
		Body b = body();
		if (b != null && !gravityChanged && (flying || mode == Mode.WAYPOINTS || axis == Axis.VERTICAL)) {
			savedGravity = b.getGravityScale();
			b.setGravityScale(0);
			gravityChanged = true;
		}
	}

	public void onDisable() {
		Body b = body();
		if (b != null) {
			if (mode == Mode.DISTANCE && axis == Axis.HORIZONTAL && !flying) setVelocityX(0);
			else setVelocity(0, 0);
			if (gravityChanged) b.setGravityScale(savedGravity);
		}
		gravityChanged = false;
		waiting = false;
	}

	// ------------------------------------------------------------ blocks

	@Block(label = "set patrol speed", desc = "Walking speed (px/s).", labelAr = "اضبط سرعة الدورية", descAr = "سرعة المشي (بكسل/ثانية).", category = "Patrol", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set patrol wait time", desc = "Seconds to wait at each end.", labelAr = "اضبط زمن الانتظار", descAr = "الثواني التي ينتظرها عند كل طرف.", category = "Patrol", args = {"seconds"})
	public void setWaitTime(float seconds) {
		waitTime = seconds;
	}

	@Block(label = "set patrol range", desc = "Distance to each side of the start (px).", labelAr = "اضبط مدى الدورية", descAr = "المسافة إلى كل جانب من نقطة البداية (بكسل).", category = "Patrol", args = {"back", "forward"})
	public void setRange(float back, float forward) {
		rangeBack = back;
		rangeForward = forward;
	}

	@Block(label = "turn around", desc = "Walks the other way now.", labelAr = "استدر", descAr = "يمشي في الاتجاه المعاكس الآن.", category = "Patrol")
	public void turnAround() {
		turn();
	}

	@Block(label = "add waypoint", desc = "Adds an element to the route (WAYPOINTS mode).", labelAr = "أضف نقطة مسار", descAr = "يضيف عنصراً إلى المسار (نمط نقاط المسار).", category = "Patrol", args = {"waypoint"})
	public void addWaypoint(PlayerItem p) {
		if (p != null) waypoints.add(p);
	}

	@Block(label = "clear waypoints", desc = "Empties the route.", labelAr = "امسح نقاط المسار", descAr = "يفرّغ المسار.", category = "Patrol")
	public void clearWaypoints() {
		waypoints.clear();
		idx = 0;
	}

	@Block(label = "set patrol start here", desc = "Uses the current position as the middle of the route.", labelAr = "اجعل بداية الدورية هنا", descAr = "يستخدم الموضع الحالي منتصفاً للمسار.", category = "Patrol")
	public void startHere() {
		startX = centerX();
		startY = centerY();
	}

	@Block(label = "is waiting", desc = "True while it waits at an end.", labelAr = "ينتظر", descAr = "صحيح أثناء الانتظار عند طرف.", category = "Patrol")
	public boolean isWaiting() {
		return waiting;
	}

	@Block(label = "walking direction", desc = "1 = right/up, -1 = left/down.", labelAr = "اتجاه المشي", descAr = "1 = يمين/أعلى، -1 = يسار/أسفل.", category = "Patrol")
	public int getDirection() {
		return dir;
	}

	@Block(label = "patrol speed", desc = "Current walking speed setting (px/s).", labelAr = "سرعة الدورية", descAr = "إعداد سرعة المشي (بكسل/ثانية).", category = "Patrol")
	public float getSpeed() {
		return speed;
	}

	// ------------------------------------------------------------ logic

	private void face(int d) {
		if (flipOnTurn) actor().setScaleX(((d < 0) != artFacesLeft) ? -baseScaleX : baseScaleX);
	}

	private void turn() {
		dir = -dir;
		turnCooldown = 0.25f;
		waitLeft = waitTime;
		waiting = waitTime > 0;
		face(dir);
		fire("onTurn");
	}

	private boolean cast(Body b, float x1, float y1, float x2, float y2, boolean solidOnly) {
		p1.set(x1 * Behavior.W, y1 * Behavior.W);
		p2.set(x2 * Behavior.W, y2 * Behavior.W);
		if (p1.dst2(p2) < 0.0001f) return false;
		rayHit = false;
		raySolidOnly = solidOnly;
		rayOwn = b;
		b.getWorld().rayCast(ray, p1, p2);
		return rayHit;
	}

	private boolean wallAhead(Body b) {
		float x = centerX(), y = centerY(), reach = widthPx() * 0.5f + 3f;
		return cast(b, x, y, x + dir * reach, y, true);
	}

	private boolean groundAhead(Body b) {
		float x = centerX() + dir * (widthPx() * 0.5f + 2f), y = centerY();
		return cast(b, x, y, x, y - heightPx() * 0.5f - ledgeDepth, false);
	}

	private void stopAxis(Body b, boolean both) {
		if (b == null) return;
		if (both) setVelocity(0, 0);
		else if (axis == Axis.HORIZONTAL && !flying) setVelocityX(0);
		else if (axis == Axis.VERTICAL) setVelocityY(0);
		else setVelocity(0, 0);
	}

	public void onUpdate(float dt) {
		if (yieldToChase) {
			Behavior c = item.getBehavior("Chase");
			if (c instanceof Chase && c.isEnabled() && ((Chase) c).isChasing()) return;
		}
		Body b = body();
		if (turnCooldown > 0) turnCooldown -= dt;
		if (waiting) {
			waitLeft -= dt;
			stopAxis(b, mode == Mode.WAYPOINTS);
			if (waitLeft <= 0) waiting = false;
			return;
		}
		if (mode == Mode.WAYPOINTS) updateWaypoints(b, dt);
		else updateDistance(b, dt);
	}

	private void updateDistance(Body b, float dt) {
		boolean horizontal = axis == Axis.HORIZONTAL;
		float pos = horizontal ? centerX() : centerY();
		float lo = (horizontal ? startX : startY) - rangeBack, hi = (horizontal ? startX : startY) + rangeForward;
		if ((dir > 0 && pos >= hi) || (dir < 0 && pos <= lo)) {
			turn();
			stopAxis(b, false);
			return;
		}
		if (b != null && horizontal && turnCooldown <= 0) {
			if ((turnAtWalls && wallAhead(b)) || (turnAtLedges && !groundAhead(b))) {
				turn();
				stopAxis(b, false);
				return;
			}
		}
		if (b != null) {
			if (horizontal) setVelocityX(dir * speed);
			else setVelocityY(dir * speed);
		} else if (horizontal) setCenter(pos + dir * speed * dt, centerY());
		else setCenter(centerX(), pos + dir * speed * dt);
	}

	private void updateWaypoints(Body b, float dt) {
		int n = waypoints == null ? 0 : waypoints.size();
		if (n == 0) return;
		if (idx >= n) idx = n - 1;
		if (idx < 0) idx = 0;
		PlayerItem wp = waypoints.get(idx);
		if (!alive(wp)) {
			advance(n);
			return;
		}
		float dx = cx(wp) - centerX(), dy = cy(wp) - centerY();
		float dist = (float) Math.sqrt(dx * dx + dy * dy);
		if (dist <= Math.max(3f, speed * dt)) {
			if (b != null) setVelocity(0, 0);
			int reached = idx;
			advance(n);
			waitLeft = waitTime;
			waiting = waitTime > 0;
			fire("onReachWaypoint", reached);
			return;
		}
		if (Math.abs(dx) > 1f) {
			int d = dx < 0 ? -1 : 1;
			if (d != dir) {
				dir = d;
				face(dir);
			}
		}
		float ux = dx / dist, uy = dy / dist;
		if (b != null) setVelocity(ux * speed, uy * speed);
		else setCenter(centerX() + ux * speed * dt, centerY() + uy * speed * dt);
	}

	private void advance(int n) {
		if (n <= 1) {
			idx = 0;
			return;
		}
		if (loopWaypoints) {
			idx = (idx + 1) % n;
		} else {
			if (idx + idxStep >= n || idx + idxStep < 0) idxStep = -idxStep;
			idx += idxStep;
		}
	}
}
