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
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Patrol 3D", icon = "shield-account", category = "AI", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onArrive(int point)"},
		desc = "Guards and monsters walk between patrol points (or wander around their start), stop, look around, then go on, playing walk and idle clips. Pauses by itself while a Chase 3D on the same element is chasing.")
@Exclusive("movement")
public class Patrol3D extends Behavior {
	@Header("Where")
	@Param(desc = "Patrol points in order (empty = wander at random around the start).")
	public List<PlayerItem> points = new ArrayList<PlayerItem>();
	@Param(desc = "Wander distance from the start when there are no points.", unit = "m", min = 0.5, max = 500, step = 0.5)
	public float wanderRadius = 6;
	@Param(desc = "Visit the points in random order.")
	public boolean randomOrder = false;

	@Header("How")
	@Param(desc = "Walking speed.", unit = "m/s", min = 0, max = 100000, step = 0.1)
	public float speed = 1.4f;
	@Param(desc = "Turning speed.", unit = "deg/s", min = 10, max = 100000, step = 10)
	public float turnSpeed = 180;
	@Param(desc = "Shortest stop at a point.", unit = "s", min = 0, max = 60, step = 0.1)
	public float waitMin = 1;
	@Param(desc = "Longest stop at a point.", unit = "s", min = 0, max = 60, step = 0.1)
	public float waitMax = 3;
	@Param(desc = "Looks left and right while stopped.", unit = "deg", min = 0, max = 180)
	public float lookAround = 50;
	@Param(desc = "Extra heading when the model faces +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	@Header("Clips")
	@Param(desc = "Clip while walking (empty = none).")
	public String walkClip = "walk";
	@Param(desc = "Clip while stopped (empty = none).")
	public String idleClip = "idle";

	private final Support3D.Walker walker = new Support3D.Walker();
	private final Support3D.Clips clips = new Support3D.Clips();
	private final Vector3 home = new Vector3(), goal = new Vector3();
	private int index = -1;
	private float wait, waitYaw, t;
	private boolean has, paused, homeSet;

	@Block(label = "set patrol speed", desc = "Walking speed (m/s).", labelAr = "اضبط سرعة الدورية", descAr = "سرعة المشي (م/ث).", category = "Patrol 3D", args = {"speed"})
	public void setSpeed(float v) {
		speed = Math.max(0, v);
	}

	@Block(label = "pause patrol", desc = "true = stand still.", labelAr = "أوقف الدورية", descAr = "صح = يقف ساكناً.", category = "Patrol 3D", args = {"paused"})
	public void setPaused(boolean on) {
		paused = on;
	}

	@Block(label = "is waiting", desc = "True while stopped at a point.", labelAr = "ينتظر", descAr = "صح أثناء التوقف عند نقطة.", category = "Patrol 3D", args = {})
	public boolean isWaiting() {
		return wait > 0;
	}

	@Block(label = "patrol point", desc = "Number of the point it walks to.", labelAr = "نقطة الدورية", descAr = "رقم النقطة التي يمشي إليها.", category = "Patrol 3D", args = {})
	public int getPoint() {
		return index;
	}

	private boolean chaserBusy() {
		Behavior b = item.getBehavior("Chase3D");
		return b instanceof Chase3D && b.enabled && ((Chase3D) b).isChasing();
	}

	private void pick() {
		if (!points.isEmpty()) {
			int n = points.size();
			index = randomOrder && n > 1 ? (index + 1 + MathUtils.random(n - 2)) % n : (index + 1) % n;
			if (!Behavior.pos3(points.get(index), goal)) goal.set(home);
		} else {
			float a = MathUtils.random(360f), r = MathUtils.random(0.3f, 1f) * wanderRadius;
			goal.set(home.x + MathUtils.cosDeg(a) * r, home.y, home.z + MathUtils.sinDeg(a) * r);
			index++;
		}
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		if (!homeSet) {
			homeSet = true;
			home.set(o.position);
		}
		if (chaserBusy()) {
			has = false; // pick a fresh point after the chase
			return;
		}
		if (!has) {
			pick();
			has = true;
		}
		if (paused || wait > 0) {
			if (wait > 0) {
				wait -= dt;
				t += dt;
				if (lookAround > 0) o.setYaw3(waitYaw + MathUtils.sin(t * 1.3f) * lookAround + modelYawOffset);
				if (wait <= 0) pick();
			}
			walker.stop(world3d(), item, dt, true, 0.3f);
			clips.play(o, idleClip, true, 1, 0.25f);
			return;
		}
		float left = walker.toward(world3d(), item, goal, speed, 5, turnSpeed, dt, true, 0, modelYawOffset, 0.3f);
		clips.play(o, walker.speedNow > 0.1f ? walkClip : idleClip, true, 1, 0.25f);
		if (left <= 0.35f) {
			fire("onArrive", index);
			wait = MathUtils.random(Math.min(waitMin, waitMax), Math.max(waitMin, waitMax));
			waitYaw = walker.yaw;
			t = 0;
			if (wait <= 0) pick();
		}
	}
}
