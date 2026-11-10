package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Combat3D;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;

@BehaviorInfo(name = "Turret 3D", icon = "turret", category = "Combat", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onAim(target)"},
		desc = "Turns toward the nearest enemy in range (by name) and fires its Shooter 3D when it faces it: turrets, towers, drones, auto-aim. Put Shooter 3D on the same element (auto fire off, aim with camera off).")
public class Turret3D extends Behavior {
	@Param(desc = "Shoots at these (name prefixes, comma separated).")
	public String targets = "Enemy";
	@Param(desc = "Sees enemies this far.", unit = "m", min = 1, max = 500, step = 1)
	public float range = 16;
	@Param(desc = "Turning speed.", unit = "deg/s", min = 10, max = 100000, step = 10)
	public float turnSpeed = 360;
	@Param(desc = "Fires when the aim is this close to the enemy.", unit = "deg", min = 1, max = 180, step = 1)
	public float fireAngle = 12;
	@Param(desc = "Extra heading of the model (front along -Z = 0).", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final ArrayList<Object3D> found = new ArrayList<Object3D>();
	private Object3D aim;
	private float scan;

	@Block(label = "turret target", desc = "The enemy it aims at (may be empty).", labelAr = "هدف البرج", descAr = "العدو الذي يصوب عليه (قد يكون فارغاً).", category = "Turret 3D", args = {})
	public PlayerItem getTarget() {
		return aim;
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		scan -= dt;
		if (scan <= 0 || aim == null || !aim.isVisible()) {
			scan = 0.25f;
			matcher.set(targets);
			Combat3D.targets(w, matcher, item, o.position, range, 360, 0, found);
			Object3D best = null;
			float bd = Float.MAX_VALUE;
			for (Object3D e : found) {
				float d = e.position.dst2(o.position);
				if (d < bd) {
					bd = d;
					best = e;
				}
			}
			if (best != aim && best != null) fire("onAim", best);
			aim = best;
		}
		if (aim == null) return;
		float want = Support3D.yawOf(aim.position.x - o.position.x, aim.position.z - o.position.z);
		float cur = o.getYaw3() - modelYawOffset;
		float nw = Support3D.approachAngle(cur, want, turnSpeed * dt);
		o.setYaw3(nw + modelYawOffset);
		if (Math.abs(Support3D.angleDiff(nw, want)) <= fireAngle) {
			Behavior s = item.getBehavior("Shooter3D");
			if (s instanceof Shooter3D) ((Shooter3D) s).shoot();
		}
		MathUtils.clamp(0, 0, 1);
	}
}
