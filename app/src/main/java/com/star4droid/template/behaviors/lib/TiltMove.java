package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Tilt move", icon = "cellphone", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		desc = "Moves the element by tilting the phone (accelerometer): a ball that rolls, a marble maze, a falling-objects catcher. Arrow keys simulate the tilt on a computer.")
@Exclusive("movement")
public class TiltMove extends Behavior {
	public enum Mode { VELOCITY, ROLL }

	/** How the phone is held, relative to its natural orientation; AUTO asks the system. */
	public enum Orientation { AUTO, NORMAL, ROTATED_90, UPSIDE_DOWN, ROTATED_270 }

	@Header("Tilt")
	@Param(desc = "VELOCITY: the tilt sets the speed. ROLL: the tilt pushes like gravity on a ball (it keeps rolling).")
	public Mode mode = Mode.VELOCITY;
	@Param(desc = "Tilt that gives full speed. 9.8 = the phone upright, about 4 = a comfortable tilt.", unit = "m/s2", min = 0.5, max = 9.8, step = 0.1)
	public float fullTilt = 4;
	@Param(desc = "Tilts smaller than this are ignored.", unit = "m/s2", min = 0, max = 4, step = 0.1)
	public float deadZone = 0.4f;
	@Param(desc = "Smooths the shaky sensor. Higher = smoother but slower to react.", unit = "s", min = 0, max = 1, step = 0.01)
	public float smoothing = 0.08f;
	@Param(desc = "AUTO follows the screen rotation. Set it by hand if the directions are wrong on your phone.")
	public Orientation orientation = Orientation.AUTO;
	@Param(desc = "Arrow keys / WASD simulate the tilt (desktop, tests).")
	public boolean useKeyboard = true;

	@Header("Movement")
	@Param(desc = "Move left/right.")
	public boolean horizontal = true;
	@Param(desc = "Move up/down (turns gravity off for this element while it runs).")
	public boolean vertical = true;
	@Param(desc = "Swap left and right.")
	public boolean invertX = false;
	@Param(desc = "Swap up and down.")
	public boolean invertY = false;
	@Param(desc = "Top speed.", unit = "px/s", min = 0, max = 100000)
	public float speed = 250;
	@Param(desc = "ROLL only: acceleration at full tilt.", unit = "px/s2", min = 0, max = 100000, step = 50)
	public float acceleration = 700;
	@Param(desc = "ROLL only: slows the ball down (0 = never).", unit = "1/s", min = 0, max = 10, step = 0.1)
	public float damping = 0.6f;

	private final float[] tilt = new float[2];
	private float fx, fy, vx, vy, savedGravity = 1;
	private boolean gravityChanged;

	/**
	 * Reads the accelerometer as a "roll direction" in screen axes: out[0] &gt; 0 when the right edge of the screen is lower, out[1] &gt; 0
	 * when the top edge is lower (a ball would roll right / up). Values in m/s2. Returns false if the phone has no accelerometer.
	 * Shared with the SpaceShip and Car behaviors.
	 */
	public static boolean read(Orientation o, float[] out) {
		out[0] = 0;
		out[1] = 0;
		if (Gdx.input == null || !Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)) return false;
		float ax = Gdx.input.getAccelerometerX(), ay = Gdx.input.getAccelerometerY();
		int rot;
		switch (o) {
			case NORMAL: rot = 0; break;
			case ROTATED_90: rot = 90; break;
			case UPSIDE_DOWN: rot = 180; break;
			case ROTATED_270: rot = 270; break;
			default: rot = Gdx.input.getRotation(); break;
		}
		// raw accelerometer: x is negative when the right edge is lower, y negative when the top edge is lower
		switch (((rot % 360) + 360) % 360) {
			case 90: out[0] = ay; out[1] = -ax; break;
			case 180: out[0] = ax; out[1] = ay; break;
			case 270: out[0] = -ay; out[1] = ax; break;
			default: out[0] = -ax; out[1] = -ay; break;
		}
		return true;
	}

	public void onStart() {
		Body b = body();
		if (b != null && vertical) {
			savedGravity = b.getGravityScale();
			b.setGravityScale(0);
			gravityChanged = true;
		}
	}

	public void onDisable() {
		Body b = body();
		if (b != null) {
			setVelocity(0, 0);
			if (gravityChanged) b.setGravityScale(savedGravity);
		}
		gravityChanged = false;
		vx = 0;
		vy = 0;
	}

	public void onEnable() {
		Body b = body();
		if (b != null && vertical && !gravityChanged) {
			savedGravity = b.getGravityScale();
			b.setGravityScale(0);
			gravityChanged = true;
		}
	}

	@Block(label = "set tilt speed", desc = "Top speed (px/s).", labelAr = "اضبط سرعة الميلان", descAr = "السرعة القصوى (بكسل/ثانية).", category = "Tilt move", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set tilt sensitivity", desc = "The tilt (m/s2) that gives full speed. Smaller = more sensitive.", labelAr = "اضبط حساسية الميلان", descAr = "الميلان (م/ث²) الذي يعطي السرعة الكاملة. الأصغر = أكثر حساسية.", category = "Tilt move", args = {"tilt"})
	public void setFullTilt(float value) {
		fullTilt = Math.max(0.5f, value);
	}

	@Block(label = "invert tilt", desc = "Reverses the left/right and up/down directions.", labelAr = "اعكس الميلان", descAr = "يعكس اتجاهي اليمين/اليسار وأعلى/أسفل.", category = "Tilt move")
	public void invertBoth() {
		invertX = !invertX;
		invertY = !invertY;
	}

	@Block(label = "tilt left/right", desc = "Current left/right tilt: -1 (left) to 1 (right).", labelAr = "ميلان يمين/يسار", descAr = "الميلان الحالي: -1 (يسار) إلى 1 (يمين).", category = "Tilt move")
	public float getTiltX() {
		return clamp(fx / fullTilt, -1, 1);
	}

	@Block(label = "tilt up/down", desc = "Current up/down tilt: -1 (down) to 1 (up).", labelAr = "ميلان أعلى/أسفل", descAr = "الميلان الحالي: -1 (أسفل) إلى 1 (أعلى).", category = "Tilt move")
	public float getTiltY() {
		return clamp(fy / fullTilt, -1, 1);
	}

	@Block(label = "stop rolling", desc = "Stops the element at once.", labelAr = "أوقف التدحرج", descAr = "يوقف العنصر فوراً.", category = "Tilt move")
	public void stopNow() {
		vx = 0;
		vy = 0;
		if (!isUI()) setVelocity(0, 0);
	}

	public void onUpdate(float dt) {
		Body b = body();
		float sx = 0, sy = 0;
		if (read(orientation, tilt)) {
			sx = tilt[0];
			sy = tilt[1];
		}
		if (useKeyboard) {
			if (keyDown(KEY_LEFT, KEY_A)) sx -= fullTilt;
			if (keyDown(KEY_RIGHT, KEY_D)) sx += fullTilt;
			if (keyDown(KEY_DOWN, KEY_S)) sy -= fullTilt;
			if (keyDown(KEY_UP, KEY_W)) sy += fullTilt;
		}
		if (invertX) sx = -sx;
		if (invertY) sy = -sy;
		float a = smoothing <= 0 ? 1f : Math.min(1f, dt / smoothing);
		fx += (sx - fx) * a;
		fy += (sy - fy) * a;
		float ux = tiltAxis(fx), uy = tiltAxis(fy); // -1..1 after dead zone

		float k = b == null ? 1f : Behavior.W / Math.max(1, stage.getSteps());
		if (b != null) {
			vx = b.getLinearVelocity().x / k;
			vy = b.getLinearVelocity().y / k;
		}
		if (mode == Mode.VELOCITY) {
			if (horizontal) vx = ux * speed;
			if (vertical) vy = uy * speed;
		} else {
			if (horizontal) vx += ux * acceleration * dt;
			if (vertical) vy += uy * acceleration * dt;
			float damp = Math.max(0f, 1f - damping * dt);
			if (horizontal) vx *= damp;
			if (vertical) vy *= damp;
			float sp = (float) Math.sqrt(vx * vx + vy * vy);
			if (sp > speed && sp > 0) {
				vx *= speed / sp;
				vy *= speed / sp;
			}
		}
		if (b != null) {
			if (horizontal && vertical) setVelocity(vx, vy);
			else if (horizontal) setVelocityX(vx);
			else if (vertical) setVelocityY(vy);
		} else {
			setCenter(centerX() + (horizontal ? vx * dt : 0), centerY() + (vertical ? vy * dt : 0));
		}
	}

	private float tiltAxis(float v) {
		float m = Math.abs(v);
		if (m <= deadZone) return 0;
		float u = Math.min(1f, (m - deadZone) / Math.max(0.01f, fullTilt - deadZone));
		return v < 0 ? -u : u;
	}
}
