package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Traffic 3D", icon = "traffic-light", category = "Vehicles", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onBlocked()", "onWaypoint(int index)", "onDespawn()"},
		desc = "A city car (or bus, boat) driven by the game: follows its lane through waypoint elements in a loop, slows down and stops behind other cars and the player, honks, turns its wheel nodes, and can vanish far from the camera and start again.")
@Exclusive("movement")
public class Traffic3D extends Behavior {
	@Header("Route")
	@Param(desc = "Lane points in order, driven in a loop (empty = straight ahead).")
	public List<PlayerItem> waypoints = new ArrayList<PlayerItem>();
	@Param(desc = "Starts at the nearest point instead of the first one.")
	public boolean startAtNearest = true;
	@Param(desc = "Keeps the wheels on the ground (raycast) on hills and bridges.")
	public boolean followGround = true;

	@Header("Driving")
	@Param(desc = "Cruising speed.", unit = "km/h", min = 1, max = 300, step = 1)
	public float speed = 40;
	@Param(desc = "Speeding up.", unit = "m/s2", min = 0.2, max = 30, step = 0.1)
	public float acceleration = 2.5f;
	@Param(desc = "Braking.", unit = "m/s2", min = 0.5, max = 60, step = 0.5)
	public float braking = 8;
	@Param(desc = "Largest turning rate at cruising speed.", unit = "deg/s", min = 5, max = 360, step = 1)
	public float turnRate = 60;
	@Param(desc = "Extra heading when the model faces +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	@Header("Obstacles")
	@Param(desc = "Looks this far ahead for cars and people.", unit = "m", min = 1, max = 100, step = 0.5)
	public float lookAhead = 12;
	@Param(desc = "Gap kept to the obstacle in front.", unit = "m", min = 0.2, max = 30, step = 0.1)
	public float safeGap = 2.5f;
	@Param(desc = "The player (car or character) it stops for (empty = the main camera's target).")
	public PlayerItem player;
	@Param(desc = "Honk after waiting this long behind something (0 = never).", unit = "s", min = 0, max = 60, step = 0.5)
	public float honkAfter = 2.5f;
	@Param(desc = "Horn sound.")
	@Sound
	public String hornSound = "";

	@Header("Wheels and despawn")
	@Param(desc = "Wheel nodes of the model to spin, separated by commas.")
	public String wheelNodes = "wheel-front-left,wheel-front-right,wheel-back-left,wheel-back-right";
	@Param(desc = "Wheel radius for the spin (0 = measured).", unit = "m", min = 0, max = 3, step = 0.01)
	public float wheelRadius = 0;
	@Param(desc = "Vanishes this far from the camera (0 = never).", unit = "m", min = 0, max = 5000, step = 5)
	public float despawnDistance = 0;
	@Param(desc = "After vanishing it starts again at its first point (else it is removed).")
	public boolean restartAfterDespawn = true;

	private final Vector3 tp = new Vector3(), start = new Vector3(), fwd = new Vector3(), d = new Vector3(), p = new Vector3();
	private float v, yaw, spin, blockedFor, honkWait, radius = 0.35f, startYaw;
	private int index;
	private boolean has, stopped, blocked;
	private String[] nodes = new String[0];

	@Block(label = "set traffic speed", desc = "Cruising speed (km/h).", labelAr = "اضبط سرعة المرور", descAr = "سرعة السير (كم/س).", category = "Traffic 3D", args = {"km/h"})
	public void setSpeed(float kmh) {
		speed = Math.max(1, kmh);
	}

	@Block(label = "stop car", desc = "true = brakes and waits (traffic lights), false = drives on.", labelAr = "أوقف السيارة", descAr = "صح = يفرمل وينتظر (إشارات المرور)، خطأ = يكمل.", category = "Traffic 3D", args = {"stop"})
	public void setStopped(boolean on) {
		stopped = on;
	}

	@Block(label = "is blocked", desc = "True while something in front makes it wait.", labelAr = "محجوز", descAr = "صح أثناء انتظاره بسبب شيء أمامه.", category = "Traffic 3D", args = {})
	public boolean isBlocked() {
		return blocked;
	}

	@Block(label = "traffic speed now", desc = "Current speed (km/h).", labelAr = "سرعة المرور الآن", descAr = "السرعة الحالية (كم/س).", category = "Traffic 3D", args = {})
	public float getSpeedKmh() {
		return v * 3.6f;
	}

	private void setup(Object3D o) {
		has = true;
		start.set(o.position);
		yaw = startYaw = o.getYaw3() - modelYawOffset;
		ArrayList<String> ns = new ArrayList<String>();
		for (String s : wheelNodes.split(",")) if (s.trim().length() > 0 && o.hasNode(s.trim())) ns.add(s.trim());
		nodes = ns.toArray(new String[0]);
		radius = wheelRadius;
		if (radius <= 0 && nodes.length > 0) {
			Vector3 sz = new Vector3();
			if (o.getNodeSize(nodes[0], sz)) radius = Math.max(0.05f, sz.y * Math.abs(o.scale.y) * 0.5f);
		}
		if (radius <= 0) radius = 0.35f;
		index = 0;
		if (startAtNearest && !waypoints.isEmpty()) {
			float best = Float.MAX_VALUE;
			for (int i = 0; i < waypoints.size(); i++) {
				if (waypoints.get(i) == null || !Behavior.pos3(waypoints.get(i), tp)) continue;
				float dd = tp.dst2(o.position);
				if (dd < best) {
					best = dd;
					index = i;
				}
			}
		}
	}

	/** Distance to the nearest obstacle ahead (cars, the player, bodies), or MAX. */
	private float obstacle(World3D w, Object3D o) {
		o.forward(fwd);
		fwd.y = 0;
		fwd.nor();
		if (modelYawOffset != 0) fwd.rotate(Vector3.Y, modelYawOffset);
		float half = Math.max(0.6f, o.getSizeX() * 0.5f), front = Math.max(o.getSizeZ(), o.getSizeX()) * 0.5f;
		float best = Float.MAX_VALUE;
		PlayerItem pl = player != null ? player : (w.activeCamera != null && w.activeCamera.target != null ? w.activeCamera.target : w.activeCamera);
		for (int i = 0; i < w.objects.size; i++) {
			Object3D c = w.objects.get(i);
			if (c == o || !c.isVisible()) continue;
			boolean car = c.getBehavior("Traffic3D") != null || c.getBehavior("Car3D") != null;
			if (!car && c != pl && !(c.isDynamic() && c.getSizeY() > 0.5f)) continue;
			best = Math.min(best, ahead(o, c.position, half + Math.max(0.5f, c.getSizeX() * 0.5f), front + Math.max(c.getSizeZ(), c.getSizeX()) * 0.5f));
		}
		if (pl != null && !(pl instanceof Object3D) && Behavior.pos3(pl, p)) best = Math.min(best, ahead(o, p, half + 0.4f, front + 0.4f));
		return best;
	}

	private float ahead(Object3D o, Vector3 at, float lateral, float sizes) {
		d.set(at).sub(o.position);
		d.y = 0;
		float along = d.dot(fwd);
		if (along <= 0 || along > lookAhead + sizes) return Float.MAX_VALUE;
		float side = Math.abs(d.x * -fwd.z + d.z * fwd.x);
		if (side > lateral) return Float.MAX_VALUE;
		return Math.max(0, along - sizes);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		if (!has) setup(o);
		// where to
		float wantYaw = yaw;
		if (!waypoints.isEmpty()) {
			PlayerItem wp = waypoints.get(Math.min(index, waypoints.size() - 1));
			if (wp != null && Behavior.pos3(wp, tp)) {
				float dx = tp.x - o.position.x, dz = tp.z - o.position.z;
				float dist = (float) Math.sqrt(dx * dx + dz * dz);
				if (dist < Math.max(1.5f, v * 0.35f)) {
					fire("onWaypoint", index);
					index = (index + 1) % waypoints.size();
				} else wantYaw = Support3D.yawOf(dx, dz);
			} else index = (index + 1) % waypoints.size();
		}
		// speed: cruise, slow for turns and obstacles
		float cruise = speed / 3.6f;
		float turn = Math.abs(Support3D.angleDiff(yaw, wantYaw));
		if (turn > 25) cruise *= MathUtils.clamp(1.2f - turn / 120f, 0.35f, 1f);
		float gap = obstacle(w, o) - safeGap;
		blocked = gap < lookAhead * 0.5f && gap < Float.MAX_VALUE / 2;
		if (gap < Float.MAX_VALUE / 2) cruise = Math.min(cruise, Math.max(0, (float) Math.sqrt(Math.max(0, 2 * braking * gap))));
		if (stopped) cruise = 0;
		v = v < cruise ? Math.min(cruise, v + acceleration * dt) : Math.max(cruise, v - braking * dt);
		if (blocked && v < 0.5f) {
			float before = blockedFor;
			blockedFor += dt;
			if (before < 0.3f && blockedFor >= 0.3f) fire("onBlocked");
			if (honkAfter > 0 && blockedFor >= honkAfter && honkWait <= 0 && hornSound.length() > 0) {
				Support3D.playAt(hornSound, w, o.position.x, o.position.y, o.position.z, 1, 50, MathUtils.random(0.95f, 1.05f));
				honkWait = 4f;
			}
		} else blockedFor = 0;
		if (honkWait > 0) honkWait -= dt;
		// steer and move
		float rate = turnRate * MathUtils.clamp(v / Math.max(1f, speed / 3.6f), 0.25f, 1.3f);
		if (v > 0.05f) yaw = Support3D.approachAngle(yaw, wantYaw, rate * dt);
		float nx = o.position.x - MathUtils.sinDeg(yaw) * v * dt, nz = o.position.z - MathUtils.cosDeg(yaw) * v * dt, ny = o.position.y;
		if (followGround) {
			float feet = o.position.y - o.getBottomY();
			float g = Support3D.groundY(w, nx, o.getBottomY() + 1.2f, nz, 3f, o);
			if (!Float.isNaN(g)) ny += (g + feet - ny) * Support3D.smooth(12, dt);
		}
		if (o.isDynamic()) o.setVelocity3((nx - o.position.x) / dt, o.getVelocityY3(), (nz - o.position.z) / dt);
		else o.setPosition3(nx, ny, nz);
		o.setYaw3(yaw + modelYawOffset);
		spin = (spin + v * dt / radius * MathUtils.radiansToDegrees) % 360f;
		for (int i = 0; i < nodes.length; i++) o.setNodeOffset(nodes[i], 0, 0, 0, -spin, 0, 0);
		// far from the camera?
		if (despawnDistance > 0 && w.camera != null && w.camera.position.dst(o.position) > despawnDistance
				&& (!restartAfterDespawn || w.camera.position.dst(start) < despawnDistance * 0.9f)) {
			fire("onDespawn");
			if (restartAfterDespawn) {
				o.setPosition3(start.x, start.y, start.z);
				yaw = startYaw;
				v = 0;
				index = 0;
				has = false;
			} else o.destroy();
		}
	}
}
