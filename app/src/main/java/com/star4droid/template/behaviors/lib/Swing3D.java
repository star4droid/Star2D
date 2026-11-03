package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Swing 3D", icon = "axe-swing", category = "Motion", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		desc = "Swings like a pendulum around a pivot above (or beside) the element: hanging lamps, signs, swings, axe traps. Can slowly die out and be pushed again from blocks.")
public class Swing3D extends Behavior {
	public enum Axis { X, Z, Y }

	@Header("Swing")
	@Param(desc = "Largest angle on each side.", unit = "deg", min = 0, max = 180, step = 1)
	public float angle = 25;
	@Param(desc = "Seconds for one full swing (there and back).", unit = "s", min = 0.1, max = 30, step = 0.05)
	public float period = 2;
	@Param(desc = "X / Z: swings like a pendulum, Y: turns left and right like a fan head.")
	public Axis axis = Axis.X;
	@Param(desc = "How fast the swing dies out (0 = forever).", unit = "1/s", min = 0, max = 5, step = 0.01)
	public float damping = 0;

	@Header("Pivot")
	@Param(desc = "Pivot above the element's position (the rope length of a lamp).", unit = "m", min = -100, max = 100, step = 0.05)
	public float pivotHeight = 1;
	@Param(desc = "Start of the cycle (0..1), so copies swing out of step.", min = 0, max = 1, step = 0.05)
	public float phase = 0;

	private final Vector3 base = new Vector3(), baseRot = new Vector3(), pivot = new Vector3(), tmp = new Vector3();
	private float t, amp = 1;
	private boolean has;

	@Block(label = "push swing", desc = "Swings fully again (after it died out).", labelAr = "ادفع التأرجح", descAr = "يتأرجح بالكامل مجدداً (بعد أن خفت).", category = "Swing 3D", args = {})
	public void push() {
		amp = 1;
	}

	@Block(label = "set swing angle", desc = "Largest angle (degrees).", labelAr = "اضبط زاوية التأرجح", descAr = "أكبر زاوية (درجة).", category = "Swing 3D", args = {"degrees"})
	public void setAngle(float a) {
		angle = a;
	}

	@Block(label = "swing angle now", desc = "Current angle (degrees).", labelAr = "زاوية التأرجح الآن", descAr = "الزاوية الحالية (درجة).", category = "Swing 3D", args = {})
	public float getAngleNow() {
		return MathUtils.sin(t / Math.max(0.1f, period) * MathUtils.PI2) * angle * amp;
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o == null) return;
		base.set(o.position);
		baseRot.set(o.euler);
		pivot.set(base).add(0, pivotHeight, 0);
		t = phase * period;
		has = true;
	}

	@Override
	public void onDisable() {
		Object3D o = obj();
		if (o != null && has) {
			o.setPosition3(base.x, base.y, base.z);
			o.setRotation3(baseRot.x, baseRot.y, baseRot.z);
		}
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null || !has) return;
		t += dt;
		if (damping > 0) amp *= (float) Math.exp(-damping * dt);
		float a = getAngleNow();
		if (axis == Axis.Y) {
			o.setRotation3(baseRot.x, baseRot.y + a, baseRot.z);
			return;
		}
		// rotate the hanging offset around the pivot (axis X: swings toward Z; axis Z: toward X)
		tmp.set(base).sub(pivot);
		if (axis == Axis.X) tmp.rotate(Vector3.X, a);
		else tmp.rotate(Vector3.Z, a);
		tmp.add(pivot);
		o.setPosition3(tmp.x, tmp.y, tmp.z);
		if (axis == Axis.X) o.setRotation3(baseRot.x + a, baseRot.y, baseRot.z);
		else o.setRotation3(baseRot.x, baseRot.y, baseRot.z + a);
	}
}
