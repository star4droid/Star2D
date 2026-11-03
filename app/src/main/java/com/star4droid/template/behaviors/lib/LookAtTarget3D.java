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
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Look At Target 3D", icon = "bullseye-arrow", category = "AI", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D", "LIGHT3D", "CAMERA3D"},
		events = {"onTargetSeen()", "onTargetLost()"},
		desc = "Turns smoothly toward a target while it is in range: turrets, security cameras, statues whose eyes follow you, a spot light tracking the player. Can sweep left and right while it sees nobody.")
public class LookAtTarget3D extends Behavior {
	@Header("Target")
	@Param(desc = "Element to look at (empty = the main camera, i.e. the player in first person).")
	public PlayerItem target;
	@Param(desc = "Only looks when the target is this close (0 = always).", unit = "m", min = 0, max = 5000, step = 0.5)
	public float range = 0;
	@Param(desc = "Look at this height above the target's position.", unit = "m", min = -50, max = 50, step = 0.05)
	public float heightOffset = 0;

	@Header("Turning")
	@Param(desc = "Only turn around Y (stays upright).")
	public boolean yawOnly = true;
	@Param(desc = "Turning speed (0 = at once).", unit = "deg/s", min = 0, max = 100000, step = 5)
	public float turnSpeed = 180;
	@Param(desc = "Extra heading when the model faces +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float yawOffset = 0;
	@Param(desc = "Sweeps left and right by this angle while it sees nothing (0 = stay still).", unit = "deg", min = 0, max = 180)
	public float idleSweep = 0;

	private final Vector3 me = new Vector3(), tp = new Vector3();
	private float yaw, pitch, baseYaw, t;
	private boolean seen, started3;

	@Block(label = "set look target", desc = "Element to look at.", labelAr = "اضبط هدف النظر", descAr = "العنصر الذي ينظر إليه.", category = "Look at target 3D", args = {"target"})
	public void setTarget(PlayerItem it) {
		target = it;
	}

	@Block(label = "sees target", desc = "True while the target is in range.", labelAr = "يرى الهدف", descAr = "صح أثناء وجود الهدف في المدى.", category = "Look at target 3D", args = {})
	public boolean isSeeing() {
		return seen;
	}

	@Block(label = "distance to look target", desc = "Meters to the target.", labelAr = "المسافة لهدف النظر", descAr = "الأمتار حتى الهدف.", category = "Look at target 3D", args = {})
	public float getDistance() {
		return me.dst(tp);
	}

	@Override
	public void onUpdate(float dt) {
		if (!Behavior.pos3(item, me)) return;
		if (!started3) {
			started3 = true;
			yaw = baseYaw = Support3D.yawOfItem(item) - yawOffset;
		}
		boolean ok = (target == null || Support3D.alive3(target)) && Support3D.posOr(target, world3d(), tp);
		tp.y += heightOffset;
		boolean inRange = ok && (range <= 0 || me.dst(tp) <= range);
		if (inRange != seen) {
			seen = inRange;
			fire(seen ? "onTargetSeen" : "onTargetLost");
		}
		float wantYaw, wantPitch = 0;
		if (seen) {
			float dx = tp.x - me.x, dy = tp.y - me.y, dz = tp.z - me.z;
			wantYaw = Support3D.yawOf(dx, dz);
			wantPitch = MathUtils.atan2(dy, (float) Math.sqrt(dx * dx + dz * dz)) * MathUtils.radiansToDegrees;
		} else if (idleSweep > 0) {
			t += dt;
			wantYaw = baseYaw + MathUtils.sin(t * 0.6f) * idleSweep;
		} else return;
		float step = turnSpeed <= 0 ? 1000 : turnSpeed * dt;
		yaw = Support3D.approachAngle(yaw, wantYaw, step);
		pitch = Support3D.approachAngle(pitch, yawOnly ? 0 : wantPitch, step);
		Object3D o = obj();
		if (o != null) o.setRotation3(pitch, yaw + yawOffset, 0);
		else if (camera3d() != null) {
			camera3d().yaw = yaw;
			camera3d().pitch = pitch;
		} else if (light3d() != null) {
			light3d().direction.set(-MathUtils.sinDeg(yaw) * MathUtils.cosDeg(pitch), MathUtils.sinDeg(pitch), -MathUtils.cosDeg(yaw) * MathUtils.cosDeg(pitch));
		}
	}
}
