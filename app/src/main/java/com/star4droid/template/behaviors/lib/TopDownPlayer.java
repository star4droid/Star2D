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

@BehaviorInfo(name = "Top-down player", icon = "walk", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		events = {"onStartMoving()", "onStopMoving()"},
		desc = "Moves the element in every direction (RPG, shooter, maze): joystick, 4 buttons or the keyboard, with acceleration, friction, 4/8 direction snapping and optional turning toward the movement. Use gravity 0 in the scene.")
@Exclusive("movement")
@Sets({"gravityScale=0", "fixedRotation=true"})
public class TopDownPlayer extends Behavior {
	public enum Directions { ANY, EIGHT, FOUR }

	@Header("Controls")
	@Param(desc = "Joystick element that drives this one (optional).")
	public Joystick joystick;
	@Param(desc = "Button (any element) that moves up while held.")
	public PlayerItem upButton;
	@Param(desc = "Button (any element) that moves down while held.")
	public PlayerItem downButton;
	@Param(desc = "Button (any element) that moves left while held.")
	public PlayerItem leftButton;
	@Param(desc = "Button (any element) that moves right while held.")
	public PlayerItem rightButton;
	@Param(desc = "Arrow keys / WASD also move the element (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Joystick pushes smaller than this are ignored.", min = 0, max = 0.9, step = 0.05)
	public float deadZone = 0.1f;

	@Header("Movement")
	@Param(desc = "ANY = smooth joystick directions, EIGHT = snaps to 8 directions, FOUR = up/down/left/right only.")
	public Directions directions = Directions.ANY;
	@Param(desc = "Top speed.", unit = "px/s", min = 0, max = 100000)
	public float speed = 200;
	@Param(desc = "How fast it reaches full speed. 0 = instantly.", unit = "px/s2", min = 0, max = 100000, step = 50)
	public float acceleration = 1500;
	@Param(desc = "How fast it stops when nothing is pressed. 0 = instantly.", unit = "px/s2", min = 0, max = 100000, step = 50)
	public float friction = 1500;

	@Header("Facing")
	@Param(desc = "Turn the element toward where it moves.")
	public boolean rotateToDirection = false;
	@Param(desc = "Angle of the image when it faces right (0 = right, 90 = up...).", unit = "deg", min = -360, max = 360)
	public float faceOffset = 0;
	@Param(desc = "How fast it turns. 0 = instantly.", unit = "deg/s", min = 0, max = 100000, step = 10)
	public float turnSpeed = 0;
	@Param(desc = "Mirror the image when moving left (for side views).")
	public boolean flipToDirection = false;

	private float vx, vy, baseScaleX = 1;
	private boolean moving;

	public void onStart() {
		baseScaleX = Math.abs(actor().getScaleX());
	}

	public void onDisable() {
		if (!isUI()) setVelocity(0, 0);
		vx = 0;
		vy = 0;
		moving = false;
	}

	@Block(label = "set move speed", desc = "Changes the top speed (px/s).", labelAr = "اضبط سرعة الحركة", descAr = "يغيّر السرعة القصوى (بكسل/ثانية).", category = "Top-down player", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set acceleration", desc = "How fast it reaches full speed (px/s2, 0 = instantly).", labelAr = "اضبط التسارع", descAr = "سرعة الوصول إلى السرعة القصوى (بكسل/ث²، 0 = فوراً).", category = "Top-down player", args = {"acceleration"})
	public void setAcceleration(float value) {
		acceleration = value;
	}

	@Block(label = "set friction", desc = "How fast it stops when nothing is pressed (px/s2, 0 = instantly).", labelAr = "اضبط الاحتكاك", descAr = "سرعة التوقف عند عدم الضغط (بكسل/ث²، 0 = فوراً).", category = "Top-down player", args = {"friction"})
	public void setFriction(float value) {
		friction = value;
	}

	@Block(label = "set joystick", desc = "Uses another joystick to move this element.", labelAr = "اضبط عصا التحكم", descAr = "يستخدم عصا تحكم أخرى لتحريك هذا العنصر.", category = "Top-down player", args = {"joystick"})
	public void setJoystick(Joystick j) {
		joystick = j;
	}

	@Block(label = "turn to face movement", desc = "On: the element turns toward where it moves.", labelAr = "التفت نحو الحركة", descAr = "مفعّل: يلتفت العنصر نحو اتجاه حركته.", category = "Top-down player", args = {"on"})
	public void setRotateToDirection(boolean on) {
		rotateToDirection = on;
	}

	@Block(label = "stop moving", desc = "Stops the element at once.", labelAr = "توقف عن الحركة", descAr = "يوقف العنصر فوراً.", category = "Top-down player")
	public void stopNow() {
		vx = 0;
		vy = 0;
		if (!isUI()) setVelocity(0, 0);
	}

	@Block(label = "is moving", desc = "True while the element moves.", labelAr = "يتحرك", descAr = "صحيح عندما يتحرك العنصر.", category = "Top-down player")
	public boolean isMoving() {
		return moving;
	}

	@Block(label = "current speed", desc = "Current speed (px/s).", labelAr = "السرعة الحالية", descAr = "السرعة الحالية (بكسل/ثانية).", category = "Top-down player")
	public float getCurrentSpeed() {
		return (float) Math.sqrt(vx * vx + vy * vy);
	}

	@Block(label = "move speed", desc = "Top speed setting (px/s).", labelAr = "سرعة الحركة", descAr = "إعداد السرعة القصوى (بكسل/ثانية).", category = "Top-down player")
	public float getSpeed() {
		return speed;
	}

	public void onUpdate(float dt) {
		Body b = body();
		// 1. input -> direction (length 0..1)
		float ix = 0, iy = 0;
		if (joystick != null) {
			float jx = joystick.getJoyStickX(), jy = joystick.getJoyStickY();
			if (jx * jx + jy * jy >= deadZone * deadZone) {
				ix = jx;
				iy = jy;
			}
		}
		if (isPressed(leftButton) || (useKeyboard && keyDown(KEY_LEFT, KEY_A))) ix -= 1;
		if (isPressed(rightButton) || (useKeyboard && keyDown(KEY_RIGHT, KEY_D))) ix += 1;
		if (isPressed(downButton) || (useKeyboard && keyDown(KEY_DOWN, KEY_S))) iy -= 1;
		if (isPressed(upButton) || (useKeyboard && keyDown(KEY_UP, KEY_W))) iy += 1;
		float len = (float) Math.sqrt(ix * ix + iy * iy);
		if (len > 1f) {
			ix /= len;
			iy /= len;
			len = 1f;
		}
		if (len > 0.0001f) {
			if (directions == Directions.FOUR) {
				if (Math.abs(ix) >= Math.abs(iy)) {
					ix = ix < 0 ? -1 : 1;
					iy = 0;
				} else {
					iy = iy < 0 ? -1 : 1;
					ix = 0;
				}
			} else if (directions == Directions.EIGHT) {
				float ang = (float) Math.atan2(iy, ix);
				float snapped = Math.round(ang / (float) (Math.PI / 4)) * (float) (Math.PI / 4);
				ix = (float) Math.cos(snapped);
				iy = (float) Math.sin(snapped);
			}
		}
		boolean hasInput = len > 0.0001f;

		// 2. current velocity (physical: what the engine really has, so walls stop us)
		if (b != null) {
			float k = Behavior.W / Math.max(1, stage.getSteps());
			vx = b.getLinearVelocity().x / k;
			vy = b.getLinearVelocity().y / k;
		}
		float tx = ix * speed, ty = iy * speed;
		float rate = hasInput ? acceleration : friction;
		if (rate <= 0) {
			vx = tx;
			vy = ty;
		} else {
			float dx = tx - vx, dy = ty - vy, d = (float) Math.sqrt(dx * dx + dy * dy), step = rate * dt;
			if (d <= step) {
				vx = tx;
				vy = ty;
			} else {
				vx += dx / d * step;
				vy += dy / d * step;
			}
		}
		if (b != null) setVelocity(vx, vy);
		else setCenter(centerX() + vx * dt, centerY() + vy * dt);

		// 3. facing / events
		boolean nowMoving = vx * vx + vy * vy > 4f;
		if (nowMoving != moving) {
			moving = nowMoving;
			fire(moving ? "onStartMoving" : "onStopMoving");
		}
		if (hasInput) {
			if (flipToDirection && Math.abs(ix) > 0.01f) actor().setScaleX(ix < 0 ? -baseScaleX : baseScaleX);
			if (rotateToDirection) {
				float target = (float) Math.toDegrees(Math.atan2(iy, ix)) - faceOffset;
				float cur = currentAngle();
				float diff = wrap(target - cur);
				float step = turnSpeed * dt;
				setAngle(turnSpeed <= 0 || Math.abs(diff) <= step ? cur + diff : cur + (diff < 0 ? -step : step));
			}
		}
	}

	private float currentAngle() {
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
}
