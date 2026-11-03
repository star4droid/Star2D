package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Floater 3D", icon = "floating-ghost", category = "Motion", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		desc = "Bobs up and down and slowly spins, like a pickup, a coin, a ghost or a boat at rest. Each copy starts at a different moment so a row of coins does not move together.")
public class Floater3D extends Behavior {
	@Header("Bob")
	@Param(desc = "Height of the bob above and below the start.", unit = "m", min = 0, max = 10, step = 0.01)
	public float height = 0.15f;
	@Param(desc = "Full up-and-down moves per second.", unit = "Hz", min = 0, max = 10, step = 0.05)
	public float frequency = 0.6f;
	@Param(desc = "Random start in the cycle so copies do not move together.")
	public boolean randomStart = true;

	@Header("Spin and sway")
	@Param(desc = "Turning speed around Y (0 = none).", unit = "deg/s", min = -100000, max = 100000, step = 5)
	public float spinSpeed = 60;
	@Param(desc = "Gentle tilt back and forth (boats, leaves).", unit = "deg", min = 0, max = 45, step = 0.5)
	public float sway = 0;

	private final Vector3 base = new Vector3(), baseRot = new Vector3();
	private float t, yaw;
	private boolean has;

	@Block(label = "set bob height", desc = "Bob height (m).", labelAr = "اضبط ارتفاع التمايل", descAr = "ارتفاع التمايل (م).", category = "Floater 3D", args = {"meters"})
	public void setHeight(float m) {
		height = Math.max(0, m);
	}

	@Block(label = "set spin speed", desc = "Degrees per second around Y.", labelAr = "اضبط سرعة اللف", descAr = "درجات في الثانية حول Y.", category = "Floater 3D", args = {"speed"})
	public void setSpinSpeed(float v) {
		spinSpeed = v;
	}

	@Block(label = "move float center", desc = "New resting point (m) to bob around.", labelAr = "انقل مركز الطفو", descAr = "نقطة الراحة الجديدة (م) للتمايل حولها.", category = "Floater 3D", args = {"x", "y", "z"})
	public void setCenter(float x, float y, float z) {
		base.set(x, y, z);
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o == null) return;
		base.set(o.position);
		baseRot.set(o.euler);
		yaw = o.euler.y;
		has = true;
		if (randomStart) t = MathUtils.random(0f, 10f);
	}

	@Override
	public void onDisable() {
		Object3D o = obj();
		if (o != null && has) {
			o.setPosition3(base.x, base.y, base.z);
			o.setRotation3(baseRot.x, o.euler.y, baseRot.z);
		}
	}

	@Override
	public void onEnable() {
		Object3D o = obj();
		if (o != null && has) base.set(o.position);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null || !has) return;
		t += dt;
		float y = base.y + MathUtils.sin(t * frequency * MathUtils.PI2) * height;
		o.setPosition3(base.x, y, base.z);
		if (spinSpeed != 0 || sway > 0) {
			yaw += spinSpeed * dt;
			float tilt = sway > 0 ? MathUtils.sin(t * frequency * MathUtils.PI2 * 0.7f) * sway : 0;
			float roll = sway > 0 ? MathUtils.cos(t * frequency * MathUtils.PI2 * 0.53f) * sway * 0.6f : 0;
			o.setRotation3(baseRot.x + tilt, yaw, baseRot.z + roll);
		}
	}
}
