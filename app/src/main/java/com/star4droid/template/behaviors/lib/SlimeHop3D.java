package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Slime Hop 3D", icon = "water", category = "Motion", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		desc = "Jelly hop: the body crouches, stretches in the air and squashes when it lands, again and again. Only changes the look (scale), not the movement.")
public class SlimeHop3D extends Behavior {
	@Header("Hop")
	@Param(desc = "Seconds for one full hop cycle.", unit = "s", min = 0.2, max = 10, step = 0.05)
	public float period = 1.0f;
	@Param(desc = "How strongly it squashes and stretches (0 = not at all).", min = 0, max = 1, step = 0.05)
	public float squash = 0.32f;
	@Param(desc = "Random start so a group does not hop in step.")
	public boolean randomPhase = true;

	private float t, bx, by, bz;
	private boolean got;

	@Override
	public void onStart() {
		if (randomPhase) t = MathUtils.random();
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null) return;
		if (!got) {
			bx = o.scale.x;
			by = o.scale.y;
			bz = o.scale.z;
			got = true;
		}
		t = (t + dt / Math.max(0.05f, period)) % 1f;
		float sy;
		if (t < 0.22f) sy = 1f - squash * (t / 0.22f);                          // crouch
		else if (t < 0.62f) sy = 1f - squash + (squash * 1.5f) * MathUtils.sin((t - 0.22f) / 0.4f * MathUtils.PI * 0.5f); // spring up, stretch
		else if (t < 0.78f) sy = 1f + squash * 0.5f - squash * 1.6f * ((t - 0.62f) / 0.16f); // land: squash
		else sy = 1f - squash * 0.6f + squash * 0.6f * MathUtils.sin((t - 0.78f) / 0.22f * MathUtils.PI * 0.5f) + MathUtils.sin(t * 40f) * 0.02f; // settle
		float sxz = 1f / (float) Math.sqrt(Math.max(0.3f, sy));
		o.scale.set(bx * sxz, by * sy, bz * sxz);
	}
}
