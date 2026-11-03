package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Items.Joystick;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Joystick movement", icon = "joystick", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		desc = "Moves the element with a joystick: top-down (all directions) or side-scroller (left/right only).")
@Exclusive("movement")
public class JoystickMovement extends Behavior {
	public enum Mode { TOP_DOWN, LEFT_RIGHT }

	@Param(desc = "The joystick element that drives this one.")
	public Joystick joystick;
	public Mode mode = Mode.TOP_DOWN;
	@Param(unit = "px/s", min = 0, max = 100000)
	public float speed = 200;
	@Param(desc = "Ignore tiny joystick movements.", min = 0, max = 0.9, step = 0.05)
	public float deadZone = 0.1f;
	@Param(desc = "Turn the element to face where it moves.")
	public boolean rotateToDirection = false;
	@Param(desc = "Angle of the image when it faces right (0 = right, 90 = up...).", unit = "deg", min = -360, max = 360)
	public float faceOffset = 0;

	@Block(label = "set move speed", desc = "Changes the speed (px/s).", labelAr = "اضبط سرعة الحركة", descAr = "يغيّر السرعة (بكسل/ثانية).", category = "Joystick movement", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set joystick", desc = "Uses another joystick to move this element.", labelAr = "اضبط عصا التحكم", descAr = "يستخدم عصا تحكم أخرى لتحريك هذا العنصر.", category = "Joystick movement", args = {"joystick"})
	public void setJoystick(Joystick j) {
		joystick = j;
	}

	@Block(label = "set joystick dead zone", desc = "Joystick pushes smaller than this are ignored (0 to 0.9).", labelAr = "اضبط المنطقة الميتة للعصا", descAr = "يتجاهل حركات العصا الأصغر من هذه القيمة (0 إلى 0.9).", category = "Joystick movement", args = {"zone"})
	public void setDeadZone(float value) {
		deadZone = value;
	}

	@Block(label = "turn to face movement", desc = "On: the element turns toward where it moves.", labelAr = "التفت نحو الحركة", descAr = "مفعّل: يلتفت العنصر نحو اتجاه حركته.", category = "Joystick movement", args = {"on"})
	public void setRotateToDirection(boolean value) {
		rotateToDirection = value;
	}

	@Block(label = "move speed", desc = "Current speed (px/s).", labelAr = "سرعة الحركة", descAr = "السرعة الحالية (بكسل/ثانية).", category = "Joystick movement")
	public float getMoveSpeed() {
		return speed;
	}

	public void onDisable() {
		if (!isUI()) setVelocity(0, 0);
	}

	public void onUpdate(float dt) {
		if (joystick == null) return;
		float jx = joystick.getJoyStickX(), jy = joystick.getJoyStickY();
		if (Math.sqrt(jx * jx + jy * jy) < deadZone) {
			jx = 0;
			jy = 0;
		}
		if (mode == Mode.LEFT_RIGHT) jy = 0;
		if (isUI()) {
			setCenter(centerX() + jx * speed * dt, centerY() + jy * speed * dt);
		} else if (mode == Mode.LEFT_RIGHT) {
			setVelocityX(jx * speed);
		} else {
			setVelocity(jx * speed, jy * speed);
		}
		if (rotateToDirection && body() != null) body().setFixedRotation(true);
		if (rotateToDirection && (jx != 0 || jy != 0)) {
			float rot = (float) Math.toDegrees(Math.atan2(jy, jx)) - faceOffset; // counter-clockwise degrees
			if (body() != null) body().setTransform(body().getPosition(), (float) Math.toRadians(rot));
			else actor().setRotation(rot);
		}
	}
}
