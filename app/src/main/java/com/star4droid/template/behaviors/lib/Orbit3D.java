package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;

@BehaviorInfo(name = "Orbit 3D", icon = "moon-orbit", category = "Motion", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D", "LIGHT3D", "CAMERA3D"},
		events = {"onLap(int laps)"},
		desc = "Circles around a center element or the start point: moons, guards around a tower, a camera turning around a car in a menu, a light circling a stage. Can face the way it moves or the center.")
public class Orbit3D extends Behavior {
	public enum Facing { MOVE_DIRECTION, CENTER, KEEP }

	@Header("Circle")
	@Param(desc = "Element to circle around (empty = the point where it starts minus the radius).")
	public PlayerItem center;
	@Param(desc = "Distance from the center (0 = the distance at start).", unit = "m", min = 0, max = 1000, step = 0.1)
	public float radius = 0;
	@Param(desc = "Turning speed (negative = the other way).", unit = "deg/s", min = -720, max = 720, step = 1)
	public float speed = 30;
	@Param(desc = "Height above the center.", unit = "m", min = -1000, max = 1000, step = 0.1)
	public float height = 0;
	@Param(desc = "Up and down wave while circling (0 = flat).", unit = "m", min = 0, max = 50, step = 0.05)
	public float wave = 0;
	@Param(desc = "Where it looks while circling.")
	public Facing facing = Facing.MOVE_DIRECTION;

	private final Vector3 c = new Vector3(), p = new Vector3();
	private float ang, done;
	private int laps;
	private boolean has;

	@Block(label = "set orbit speed", desc = "Degrees per second.", labelAr = "اضبط سرعة المدار", descAr = "درجات في الثانية.", category = "Orbit 3D", args = {"speed"})
	public void setSpeed(float v) {
		speed = v;
	}

	@Block(label = "set orbit radius", desc = "Distance from the center (m).", labelAr = "اضبط نصف قطر المدار", descAr = "البعد عن المركز (م).", category = "Orbit 3D", args = {"meters"})
	public void setRadius(float r) {
		radius = Math.max(0, r);
	}

	@Block(label = "set orbit center", desc = "Element to circle around.", labelAr = "اضبط مركز المدار", descAr = "العنصر الذي يدور حوله.", category = "Orbit 3D", args = {"center"})
	public void setCenter(PlayerItem it) {
		center = it;
	}

	@Block(label = "orbit laps", desc = "Full circles made.", labelAr = "دورات المدار", descAr = "عدد الدورات الكاملة.", category = "Orbit 3D", args = {})
	public int getLaps() {
		return laps;
	}

	@Override
	public void onStart() {
		if (!Behavior.pos3(item, p)) return;
		if (!Behavior.pos3(center, c)) c.set(p).sub(radius > 0 ? radius : 3, 0, 0);
		float dx = p.x - c.x, dz = p.z - c.z;
		if (radius <= 0) radius = Math.max(0.1f, (float) Math.sqrt(dx * dx + dz * dz));
		if (center != null) height = height == 0 ? p.y - c.y : height;
		else height = p.y - c.y;
		ang = MathUtils.atan2(dz, dx) * MathUtils.radiansToDegrees;
		has = true;
	}

	@Override
	public void onUpdate(float dt) {
		if (!has) return;
		if (center != null && Support3D.alive3(center)) Behavior.pos3(center, c);
		float step = speed * dt;
		ang += step;
		done += Math.abs(step);
		if (done >= 360) {
			done -= 360;
			laps++;
			fire("onLap", laps);
		}
		float y = c.y + height + (wave > 0 ? MathUtils.sinDeg(ang * 2) * wave : 0);
		float x = c.x + MathUtils.cosDeg(ang) * radius, z = c.z + MathUtils.sinDeg(ang) * radius;
		Support3D.setPos(item, x, y, z);
		if (facing == Facing.KEEP) return;
		float yaw;
		if (facing == Facing.CENTER) yaw = Support3D.yawOf(c.x - x, c.z - z);
		else {
			float s = Math.signum(speed == 0 ? 1 : speed);
			yaw = Support3D.yawOf(-MathUtils.sinDeg(ang) * s, MathUtils.cosDeg(ang) * s);
		}
		if (obj() != null) obj().setYaw3(yaw);
		else if (camera3d() != null) {
			if (facing == Facing.CENTER) camera3d().lookAt(c.x, c.y, c.z);
			else camera3d().yaw = yaw;
		} else if (light3d() != null && facing == Facing.CENTER) light3d().pointAt(c.x, c.y, c.z);
	}
}
