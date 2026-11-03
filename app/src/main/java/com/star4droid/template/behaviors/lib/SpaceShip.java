package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.Items.Joystick;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sets;

@BehaviorInfo(name = "Space ship", icon = "spaceship", category = "Vehicles", targets = BehaviorInfo.Target.BOTH,
		events = {"onBoostStart()", "onBoostEnd()"},
		desc = "Asteroids-style ship: turn left/right, thrust forward in the facing direction, drag, speed limit and an optional boost. Steer with a joystick, buttons, the keyboard or by tilting the phone. Needs gravity 0.")
@Exclusive("movement")
@Sets({"gravityScale=0", "fixedRotation=true"})
public class SpaceShip extends Behavior {
	public enum Steering {
		/** Joystick left/right turns the ship, pushing it up thrusts. */
		ROTATE,
		/** The ship turns toward where the joystick points and thrusts while it is pushed. */
		POINT
	}

	@Header("Controls")
	@Param(desc = "Joystick element that drives the ship (optional).")
	public Joystick joystick;
	@Param(desc = "ROTATE: left/right turns, up thrusts. POINT: the ship turns toward the joystick direction.")
	public Steering steering = Steering.ROTATE;
	@Param(desc = "Button (any element) that turns the ship left while held.")
	public PlayerItem leftButton;
	@Param(desc = "Button (any element) that turns the ship right while held.")
	public PlayerItem rightButton;
	@Param(desc = "Button that thrusts forward while held.")
	public PlayerItem thrustButton;
	@Param(desc = "Button that slows the ship down while held.")
	public PlayerItem brakeButton;
	@Param(desc = "Button that starts the boost.")
	public PlayerItem boostButton;
	@Param(desc = "Arrow keys / A D W S / shift also fly the ship (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Tilt the phone left/right to turn.")
	public boolean useTilt = false;
	@Param(desc = "Joystick pushes smaller than this are ignored.", min = 0, max = 0.9, step = 0.05)
	public float deadZone = 0.15f;

	@Header("Flight")
	@Param(desc = "Forward acceleration.", unit = "px/s2", min = 0, max = 100000, step = 10)
	public float thrust = 450;
	@Param(desc = "Speed limit.", unit = "px/s", min = 0, max = 100000)
	public float maxSpeed = 320;
	@Param(desc = "Turning speed.", unit = "deg/s", min = 0, max = 100000, step = 10)
	public float rotateSpeed = 200;
	@Param(desc = "Slows the ship down by itself. 0 = keeps drifting forever (space), 1 = stops in about a second.", unit = "1/s", min = 0, max = 10, step = 0.1)
	public float drag = 0.3f;
	@Param(desc = "How hard the brake slows the ship.", unit = "1/s", min = 0, max = 20, step = 0.1)
	public float brakePower = 3;
	@Param(desc = "Angle the nose of the image points to when the element is not rotated (90 = up, 0 = right).", unit = "deg", min = -360, max = 360)
	public float noseAngle = 90;

	@Header("Boost")
	@Param(desc = "Thrust and speed limit are multiplied by this during the boost. 1 = no boost.", min = 1, max = 5, step = 0.1)
	public float boostMultiplier = 1.8f;
	@Param(desc = "How long the boost lasts.", unit = "s", min = 0, max = 30, step = 0.1)
	public float boostTime = 1.2f;
	@Param(desc = "Time before the next boost can start.", unit = "s", min = 0, max = 60, step = 0.1)
	public float boostCooldown = 2;

	private final float[] tilt = new float[2];
	private float vx, vy, boostLeft, cooldownLeft;
	private boolean boostWasDown, thrusting;

	public void onDisable() {
		if (!isUI()) setVelocity(0, 0);
		vx = 0;
		vy = 0;
		boostLeft = 0;
		thrusting = false;
	}

	@Block(label = "set thrust", desc = "Forward acceleration (px/s2).", labelAr = "اضبط قوة الدفع", descAr = "التسارع للأمام (بكسل/ث²).", category = "Space ship", args = {"thrust"})
	public void setThrust(float value) {
		thrust = value;
	}

	@Block(label = "set max speed", desc = "Speed limit (px/s).", labelAr = "اضبط السرعة القصوى", descAr = "حد السرعة (بكسل/ثانية).", category = "Space ship", args = {"speed"})
	public void setMaxSpeed(float value) {
		maxSpeed = value;
	}

	@Block(label = "set turn speed", desc = "Turning speed (degrees per second).", labelAr = "اضبط سرعة الدوران", descAr = "سرعة الدوران (درجة/ثانية).", category = "Space ship", args = {"degrees"})
	public void setRotateSpeed(float value) {
		rotateSpeed = value;
	}

	@Block(label = "set drag", desc = "How fast the ship slows down by itself (0 = never).", labelAr = "اضبط المقاومة", descAr = "سرعة تباطؤ المركبة تلقائياً (0 = أبداً).", category = "Space ship", args = {"drag"})
	public void setDrag(float value) {
		drag = value;
	}

	@Block(label = "boost now", desc = "Starts the boost if it is not cooling down.", labelAr = "فعّل التعزيز الآن", descAr = "يبدأ التعزيز إذا لم يكن في فترة الانتظار.", category = "Space ship")
	public void boost() {
		if (boostLeft <= 0 && cooldownLeft <= 0 && boostMultiplier > 1 && boostTime > 0) {
			boostLeft = boostTime;
			fire("onBoostStart");
		}
	}

	@Block(label = "stop the ship", desc = "Sets the speed to zero.", labelAr = "أوقف المركبة", descAr = "يجعل السرعة صفراً.", category = "Space ship")
	public void stopNow() {
		vx = 0;
		vy = 0;
		if (!isUI()) setVelocity(0, 0);
	}

	@Block(label = "use tilt", desc = "On: tilting the phone left/right turns the ship.", labelAr = "استخدم الميلان", descAr = "مفعّل: ميلان الهاتف يمين/يسار يدير المركبة.", category = "Space ship", args = {"on"})
	public void setUseTilt(boolean on) {
		useTilt = on;
	}

	@Block(label = "is boosting", desc = "True during the boost.", labelAr = "في وضع التعزيز", descAr = "صحيح أثناء التعزيز.", category = "Space ship")
	public boolean isBoosting() {
		return boostLeft > 0;
	}

	@Block(label = "is thrusting", desc = "True while the engine pushes.", labelAr = "المحرك يعمل", descAr = "صحيح أثناء دفع المحرك.", category = "Space ship")
	public boolean isThrusting() {
		return thrusting;
	}

	@Block(label = "ship speed", desc = "Current speed (px/s).", labelAr = "سرعة المركبة", descAr = "السرعة الحالية (بكسل/ثانية).", category = "Space ship")
	public float getShipSpeed() {
		return (float) Math.sqrt(vx * vx + vy * vy);
	}

	@Block(label = "heading", desc = "Direction the nose points to, in degrees (0 = right, 90 = up).", labelAr = "الاتجاه", descAr = "اتجاه مقدمة المركبة بالدرجات (0 = يمين، 90 = أعلى).", category = "Space ship")
	public float getHeading() {
		return angle() + noseAngle;
	}

	private float angle() {
		Body b = body();
		return b != null ? (float) Math.toDegrees(b.getAngle()) : actor().getRotation();
	}

	private void setAngle(float deg) {
		Body b = body();
		if (b != null) b.setTransform(b.getPosition(), (float) Math.toRadians(deg));
		else actor().setRotation(deg);
	}

	private static float wrap(float deg) {
		while (deg > 180) deg -= 360;
		while (deg < -180) deg += 360;
		return deg;
	}

	public void onUpdate(float dt) {
		Body b = body();
		float turn = 0; // -1 (clockwise / right) .. 1 (counter-clockwise / left)
		boolean thrustOn = false, brake = false, boostDown = false;
		float pointAngle = 0;
		boolean point = false;
		if (joystick != null) {
			float jx = joystick.getJoyStickX(), jy = joystick.getJoyStickY();
			float m2 = jx * jx + jy * jy;
			if (m2 >= deadZone * deadZone) {
				if (steering == Steering.POINT) {
					point = true;
					pointAngle = (float) Math.toDegrees(Math.atan2(jy, jx));
					thrustOn = true;
				} else {
					turn -= jx;
					if (jy > deadZone) thrustOn = true;
					else if (jy < -deadZone) brake = true;
				}
			}
		}
		if (isPressed(leftButton) || (useKeyboard && keyDown(KEY_LEFT, KEY_A))) turn += 1;
		if (isPressed(rightButton) || (useKeyboard && keyDown(KEY_RIGHT, KEY_D))) turn -= 1;
		if (isPressed(thrustButton) || (useKeyboard && keyDown(KEY_UP, KEY_W))) thrustOn = true;
		if (isPressed(brakeButton) || (useKeyboard && keyDown(KEY_DOWN, KEY_S))) brake = true;
		boostDown = isPressed(boostButton) || (useKeyboard && keyDown(com.badlogic.gdx.Input.Keys.SHIFT_LEFT, com.badlogic.gdx.Input.Keys.SHIFT_RIGHT));
		if (useTilt && TiltMove.read(TiltMove.Orientation.AUTO, tilt)) {
			float t = tilt[0];
			if (Math.abs(t) > 0.6f) turn -= clamp(t / 4f, -1, 1);
		}
		turn = clamp(turn, -1, 1);

		// boost
		if (cooldownLeft > 0) cooldownLeft -= dt;
		if (boostDown && !boostWasDown) boost();
		boostWasDown = boostDown;
		float mult = 1f;
		if (boostLeft > 0) {
			mult = boostMultiplier;
			boostLeft -= dt;
			if (boostLeft <= 0) {
				boostLeft = 0;
				cooldownLeft = boostCooldown;
				fire("onBoostEnd");
			}
		}

		// turning
		float ang = angle();
		if (point) {
			float diff = wrap(pointAngle - noseAngle - ang), step = rotateSpeed * dt;
			ang += Math.abs(diff) <= step || rotateSpeed <= 0 ? diff : (diff < 0 ? -step : step);
			setAngle(ang);
		} else if (turn != 0) {
			ang += turn * rotateSpeed * dt;
			setAngle(ang);
		}

		// velocity
		if (b != null) {
			float k = Behavior.W / Math.max(1, stage.getSteps());
			vx = b.getLinearVelocity().x / k;
			vy = b.getLinearVelocity().y / k;
		}
		thrusting = thrustOn;
		if (thrustOn) {
			double h = Math.toRadians(ang + noseAngle);
			float a = thrust * mult * dt;
			vx += (float) Math.cos(h) * a;
			vy += (float) Math.sin(h) * a;
		}
		float damp = drag + (brake ? brakePower : 0);
		if (damp > 0) {
			float f = Math.max(0f, 1f - damp * dt);
			vx *= f;
			vy *= f;
		}
		float limit = maxSpeed * mult, sp2 = vx * vx + vy * vy;
		if (sp2 > limit * limit && sp2 > 0) {
			float f = limit / (float) Math.sqrt(sp2);
			vx *= f;
			vy *= f;
		}
		if (b != null) setVelocity(vx, vy);
		else setCenter(centerX() + vx * dt, centerY() + vy * dt);
	}
}
