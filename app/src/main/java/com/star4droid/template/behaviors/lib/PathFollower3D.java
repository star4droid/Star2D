package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Path Follower 3D", icon = "map-marker-path", category = "Movement", targets = BehaviorInfo.Target.THREE_D,
		types = {"OBJ3D", "LIGHT3D", "CAMERA3D"}, events = {"onWaypoint(int index)", "onPathEnd()"},
		desc = "Travels through a list of waypoint elements (empty objects or markers): in a loop, back and forth, or once. Walks on the ground or flies straight; waits at each point. Cameras make a cinematic fly-through.")
@Exclusive("movement")
public class PathFollower3D extends Behavior {
	public enum Loop { LOOP, PING_PONG, ONCE }

	@Header("Route")
	@Param(desc = "The points to visit, in order (any 3D elements; hide them or use EMPTY objects).")
	public List<PlayerItem> waypoints = new ArrayList<PlayerItem>();
	@Param(desc = "LOOP: back to the first point. PING_PONG: back and forth. ONCE: stops at the last one.")
	public Loop loop = Loop.LOOP;
	@Param(desc = "Walks on the ground (gravity, steps) instead of flying straight to each point.")
	public boolean onGround = true;

	@Header("Motion")
	@Param(desc = "Moving speed.", unit = "m/s", min = 0, max = 100000, step = 0.1)
	public float speed = 2.5f;
	@Param(desc = "How fast it reaches its speed (0 = at once).", unit = "1/s", min = 0, max = 40, step = 0.5)
	public float acceleration = 6;
	@Param(desc = "Turning speed toward the next point (0 = never turns).", unit = "deg/s", min = 0, max = 100000, step = 10)
	public float turnSpeed = 240;
	@Param(desc = "Counts as arrived this close to a point.", unit = "m", min = 0.02, max = 20, step = 0.05)
	public float arriveDistance = 0.3f;
	@Param(desc = "Wait at each point.", unit = "s", min = 0, max = 120, step = 0.1)
	public float waitTime = 0;
	@Param(desc = "Extra heading when the model faces +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	private final Support3D.Walker walker = new Support3D.Walker();
	private final Vector3 tp = new Vector3();
	private int index, dir = 1;
	private float wait;
	private boolean done, paused;

	@Block(label = "set path speed", desc = "Meters per second.", labelAr = "اضبط سرعة المسار", descAr = "أمتار في الثانية.", category = "Path follower 3D", args = {"speed"})
	public void setSpeed(float v) {
		speed = Math.max(0, v);
	}

	@Block(label = "go to waypoint", desc = "Heads to the waypoint with this number (0 = first).", labelAr = "اذهب لنقطة المسار", descAr = "يتجه لنقطة المسار بهذا الرقم (0 = الأولى).", category = "Path follower 3D", args = {"index"})
	public void goTo(int i) {
		if (waypoints.isEmpty()) return;
		index = Math.max(0, Math.min(waypoints.size() - 1, i));
		done = false;
		wait = 0;
	}

	@Block(label = "pause path", desc = "true = stops on the path, false = goes on.", labelAr = "أوقف المسار", descAr = "صح = يتوقف على المسار، خطأ = يكمل.", category = "Path follower 3D", args = {"paused"})
	public void setPaused(boolean on) {
		paused = on;
	}

	@Block(label = "add waypoint", desc = "Adds an element at the end of the route.", labelAr = "أضف نقطة مسار", descAr = "يضيف عنصراً في نهاية المسار.", category = "Path follower 3D", args = {"point"})
	public void addWaypoint(PlayerItem p) {
		if (p != null) waypoints.add(p);
		done = false;
	}

	@Block(label = "current waypoint", desc = "Number of the point it is heading to.", labelAr = "نقطة المسار الحالية", descAr = "رقم النقطة التي يتجه إليها.", category = "Path follower 3D", args = {})
	public int getIndex() {
		return index;
	}

	@Block(label = "path finished", desc = "True after the last point in ONCE mode.", labelAr = "انتهى المسار", descAr = "صح بعد آخر نقطة في وضع مرة واحدة.", category = "Path follower 3D", args = {})
	public boolean isFinished() {
		return done;
	}

	@Override
	public void onUpdate(float dt) {
		if (dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		boolean ground = onGround && obj() != null;
		if (waypoints.isEmpty() || done || paused || wait > 0) {
			if (wait > 0) wait -= dt;
			walker.stop(world3d(), item, dt, ground, 0.3f);
			return;
		}
		if (index >= waypoints.size()) index = 0;
		PlayerItem wp = waypoints.get(index);
		if (wp == null || !Behavior.pos3(wp, tp)) {
			advance();
			return;
		}
		float left = walker.toward(world3d(), item, tp, speed, acceleration, turnSpeed, dt, ground, 0, modelYawOffset, 0.3f);
		if (left <= arriveDistance) {
			fire("onWaypoint", index);
			wait = waitTime;
			advance();
		}
	}

	private void advance() {
		int n = waypoints.size();
		if (n <= 1) {
			if (loop == Loop.ONCE) finish();
			return;
		}
		if (loop == Loop.LOOP) index = (index + 1) % n;
		else if (loop == Loop.PING_PONG) {
			if (index + dir >= n || index + dir < 0) dir = -dir;
			index += dir;
		} else {
			if (index + 1 >= n) finish();
			else index++;
		}
	}

	private void finish() {
		done = true;
		fire("onPathEnd");
	}
}
