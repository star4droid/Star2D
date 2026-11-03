package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Rotator 3D", icon = "rotate-360", category = "Motion", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onTurn(int turns)"},
		desc = "Turns the element forever around X, Y and Z: fans, wheels, coins, windmills, radar dishes. Can speed up smoothly and count full turns.")
public class Rotator3D extends Behavior {
	@Header("Speed")
	@Param(desc = "Turning speed around X (pitch).", unit = "deg/s", min = -100000, max = 100000, step = 5)
	public float speedX = 0;
	@Param(desc = "Turning speed around Y (heading).", unit = "deg/s", min = -100000, max = 100000, step = 5)
	public float speedY = 90;
	@Param(desc = "Turning speed around Z (roll).", unit = "deg/s", min = -100000, max = 100000, step = 5)
	public float speedZ = 0;
	@Param(desc = "Around its own axes (true) or the world axes (false).")
	public boolean localAxes = true;
	@Param(desc = "Seconds to reach full speed when it starts (0 = at once).", unit = "s", min = 0, max = 30, step = 0.1)
	public float spinUpTime = 0;
	@Param(desc = "Turning when the game starts (blocks can stop and start it).")
	public boolean running = true;

	private float k, turned;
	private int turns;

	@Block(label = "set rotation speed", desc = "Degrees per second around X, Y, Z.", labelAr = "اضبط سرعة الدوران", descAr = "درجات في الثانية حول X وY وZ.", category = "Rotator 3D", args = {"x", "y", "z"})
	public void setSpeed(float x, float y, float z) {
		speedX = x;
		speedY = y;
		speedZ = z;
	}

	@Block(label = "start rotating", desc = "Starts (true) or stops (false) turning.", labelAr = "ابدأ الدوران", descAr = "يبدأ (صح) أو يوقف (خطأ) الدوران.", category = "Rotator 3D", args = {"on"})
	public void setRunning(boolean on) {
		running = on;
	}

	@Block(label = "reverse rotation", desc = "Turns the other way.", labelAr = "اعكس الدوران", descAr = "يدور في الاتجاه الآخر.", category = "Rotator 3D", args = {})
	public void reverse() {
		speedX = -speedX;
		speedY = -speedY;
		speedZ = -speedZ;
	}

	@Block(label = "full turns", desc = "How many full turns it made.", labelAr = "عدد الدورات", descAr = "عدد الدورات الكاملة.", category = "Rotator 3D", args = {})
	public int getTurns() {
		return turns;
	}

	@Override
	public void onStart() {
		k = spinUpTime > 0 ? 0 : 1;
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null) return;
		float target = running ? 1 : 0;
		if (spinUpTime > 0) k += (target > k ? 1 : -1) * dt / spinUpTime;
		else k = target;
		k = clamp(k, 0, 1);
		if (k <= 0) return;
		float dx = speedX * k * dt, dy = speedY * k * dt, dz = speedZ * k * dt;
		if (localAxes) o.turnLocal(dx, dy, dz);
		else o.rotate3(dx, dy, dz);
		turned += Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
		while (turned >= 360f) {
			turned -= 360f;
			turns++;
			fire("onTurn", turns);
		}
	}
}
