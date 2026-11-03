package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.star4droid.template.Items.Joystick;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "First Person Controller 3D", icon = "eye-circle", category = "Movement", targets = BehaviorInfo.Target.THREE_D,
		types = {"OBJ3D", "CAMERA3D"}, events = {"onJump()", "onLand()", "onStep()"},
		desc = "Walk with a joystick and look around by dragging the screen, seen through the eyes of the player. Jump, run, head bob and footstep sounds. Give the player a capsule with a DYNAMIC body (rotation locked for you) or put it on a 3D camera.")
@Exclusive("movement")
public class FirstPersonController3D extends Behavior {
	@Header("Controls")
	@Param(desc = "Joystick that walks (up = forward).")
	public Joystick joystick;
	@Param(desc = "Optional second joystick that turns the view (instead of dragging).")
	public Joystick lookJoystick;
	@Param(desc = "Jump button (any element).")
	public PlayerItem jumpButton;
	@Param(desc = "Run button: held = run.")
	public PlayerItem runButton;
	@Param(desc = "Drag on the empty screen to look around.")
	public boolean dragToLook = true;
	@Param(desc = "WASD / arrows walk, Q/E turn, space jumps, shift runs (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Joystick pushes smaller than this are ignored.", min = 0, max = 0.9, step = 0.05)
	public float deadZone = 0.12f;

	@Header("Camera")
	@Param(desc = "The camera of the eyes (empty = the main camera). Ignored when this behavior is on a camera.")
	public PlayerItem camera;
	@Param(desc = "Eyes above the feet.", unit = "m", min = 0.2, max = 5, step = 0.05)
	public float eyeHeight = 1.6f;
	@Param(desc = "How fast dragging turns the view.", unit = "deg/px", min = 0.02, max = 2, step = 0.01)
	public float lookSensitivity = 0.22f;
	@Param(desc = "Turning speed with the look joystick or Q/E.", unit = "deg/s", min = 10, max = 720, step = 5)
	public float turnSpeed = 140;
	@Param(desc = "Field of view of the camera (0 = keep the camera's own).", unit = "deg", min = 0, max = 120)
	public float fieldOfView = 0;

	@Header("Movement")
	@Param(desc = "Walking speed.", unit = "m/s", min = 0, max = 20, step = 0.1)
	public float walkSpeed = 3.2f;
	@Param(desc = "Running speed.", unit = "m/s", min = 0, max = 30, step = 0.1)
	public float runSpeed = 6f;
	@Param(desc = "Runs when the joystick is pushed almost to the edge.")
	public boolean runAtFullPush = false;
	@Param(desc = "How fast it reaches the wanted speed (higher = snappier).", unit = "1/s", min = 0.5, max = 40, step = 0.5)
	public float acceleration = 12;
	@Param(desc = "Jump take-off speed (0 = no jump).", unit = "m/s", min = 0, max = 30, step = 0.1)
	public float jumpSpeed = 4.8f;
	@Param(desc = "Body radius used to stop at walls when it has no DYNAMIC body.", unit = "m", min = 0.05, max = 3, step = 0.05)
	public float radius = 0.35f;

	@Header("Feel")
	@Param(desc = "Head bob height while walking (0 = none).", unit = "m", min = 0, max = 0.3, step = 0.005)
	public float headBob = 0.045f;
	@Param(desc = "Distance between two steps (bob cycle and footstep sound).", unit = "m", min = 0.2, max = 5, step = 0.05)
	public float stepLength = 0.75f;
	@Param(desc = "Footstep sound (empty = silent).")
	@Sound
	public String footstepSound = "";
	@Param(label = "Second footstep sound", desc = "Second footstep sound, played in turn with the first one.")
	@Sound
	public String footstepSound2 = "";
	@Param(desc = "Sound when landing after a jump or a fall.")
	@Sound
	public String landSound = "";
	@Param(desc = "Volume of the footsteps.", min = 0, max = 1, step = 0.05)
	public float stepVolume = 0.6f;

	private final Support3D.Mover mover = new Support3D.Mover();
	private final float[] in = new float[2], dir = new float[2];
	private float vx, vz, stepDist, bob, lookPitch, lookYaw, extForward, extRight;
	private boolean jumpQueued, wasGrounded = true, running, extRun, locked;
	private int stepIndex;
	private com.badlogic.gdx.scenes.scene2d.EventListener jumpL;
	private Camera3D followed;

	// ------------------------------------------------------------ blocks

	@Block(label = "jump", desc = "Jumps if standing on the ground.", labelAr = "اقفز", descAr = "يقفز إذا كان واقفاً على الأرض.", category = "First person", args = {})
	public void jump() {
		jumpQueued = true;
	}

	@Block(label = "set walk speed", desc = "Walking speed (m/s).", labelAr = "اضبط سرعة المشي", descAr = "سرعة المشي (م/ث).", category = "First person", args = {"speed"})
	public void setWalkSpeed(float value) {
		walkSpeed = Math.max(0, value);
	}

	@Block(label = "set run speed", desc = "Running speed (m/s).", labelAr = "اضبط سرعة الجري", descAr = "سرعة الجري (م/ث).", category = "First person", args = {"speed"})
	public void setRunSpeed(float value) {
		runSpeed = Math.max(0, value);
	}

	@Block(label = "run", desc = "true = run without the run button.", labelAr = "اجرِ", descAr = "صح = يجري بدون زر الجري.", category = "First person", args = {"on"})
	public void setRunning(boolean on) {
		extRun = on;
	}

	@Block(label = "walk by code", desc = "Walks forward / right (-1..1) every frame until set to 0, 0.", labelAr = "امشِ بالكود", descAr = "يمشي للأمام / لليمين (-1..1) كل إطار حتى تضبطها 0، 0.", category = "First person", args = {"forward", "right"})
	public void walk(float forward, float right) {
		extForward = clamp(forward, -1, 1);
		extRight = clamp(right, -1, 1);
	}

	@Block(label = "lock controls", desc = "true = the player cannot move or look (cut scenes, menus).", labelAr = "أقفل التحكم", descAr = "صح = لا يستطيع اللاعب الحركة أو النظر (مشاهد، قوائم).", category = "First person", args = {"locked"})
	public void setLocked(boolean on) {
		locked = on;
	}

	@Block(label = "look at angles", desc = "Turns the view to a heading and pitch (degrees).", labelAr = "انظر إلى الزوايا", descAr = "يوجّه النظر إلى اتجاه وميل (بالدرجات).", category = "First person", args = {"yaw", "pitch"})
	public void lookAngles(float yaw, float pitch) {
		lookYaw = yaw;
		lookPitch = clamp(pitch, -85, 85);
		Camera3D c = cam();
		if (c != null) {
			c.yaw = lookYaw;
			c.pitch = lookPitch;
		}
	}

	@Block(label = "is on the ground", desc = "True while standing on something.", labelAr = "على الأرض", descAr = "صح أثناء الوقوف على شيء.", category = "First person", args = {})
	public boolean isGrounded() {
		return mover.grounded;
	}

	@Block(label = "is walking", desc = "True while moving.", labelAr = "يمشي", descAr = "صح أثناء الحركة.", category = "First person", args = {})
	public boolean isWalking() {
		return vx * vx + vz * vz > 0.04f;
	}

	@Block(label = "is running", desc = "True while running.", labelAr = "يجري", descAr = "صح أثناء الجري.", category = "First person", args = {})
	public boolean isRunning() {
		return running && isWalking();
	}

	@Block(label = "walking speed now", desc = "Current horizontal speed (m/s).", labelAr = "سرعة المشي الحالية", descAr = "السرعة الأفقية الحالية (م/ث).", category = "First person", args = {})
	public float getSpeed() {
		return (float) Math.sqrt(vx * vx + vz * vz);
	}

	/** Throws the player up (jump pads), on the ground or not. */
	public void launch(float upSpeed) {
		mover.grounded = true;
		mover.jump(item, upSpeed);
	}

	// ------------------------------------------------------------ engine

	private Camera3D cam() {
		Camera3D self = camera3d();
		if (self != null) return self;
		return Support3D.cameraOr(camera, world3d());
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o != null && o.isDynamic()) o.lockRotation3(true, true, true);
		Camera3D c = cam();
		lookYaw = o != null ? o.getYaw3() : (c != null ? c.yaw : 0);
		lookPitch = c != null && camera3d() != null ? c.pitch : 0;
		jumpL = null;
		if (jumpButton != null) jumpL = Support3D.onTap(jumpButton, new Runnable() {
			@Override
			public void run() {
				jumpQueued = true;
			}
		});
		attachCamera();
	}

	@Override
	public void onEnable() {
		Camera3D c = cam();
		if (c != null) lookYaw = c.yaw;
		attachCamera();
	}

	private void attachCamera() {
		Camera3D c = cam();
		if (c == null) return;
		followed = c;
		c.yaw = lookYaw;
		c.pitch = lookPitch;
		c.sensitivity = lookSensitivity;
		c.setTouchLook(dragToLook);
		if (fieldOfView > 0) c.setFov(fieldOfView);
		if (camera3d() == null) {
			c.follow(item, "FIRST_PERSON");
			c.height = eyeHeight - Support3D.Mover.feetOffset(item, eyeHeight);
		}
	}

	@Override
	public void onDisable() {
		if (followed != null && camera3d() == null) {
			followed.stopFollow();
			followed.setTouchLook(false);
		}
		Object3D o = obj();
		if (o != null && o.isDynamic()) o.setVelocityKeepY(0, 0);
		vx = vz = 0;
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(jumpButton, jumpL);
	}

	@Override
	public void onUpdate(float dt) {
		World3D w = world3d();
		Camera3D c = cam();
		if (w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		// look: drag (the camera turns itself), look joystick, keys
		if (c != null) {
			lookYaw = c.yaw;
			lookPitch = c.pitch;
			if (!locked) {
				float turn = 0, tilt = 0;
				if (lookJoystick != null) {
					turn -= lookJoystick.getJoyStickX();
					tilt += lookJoystick.getJoyStickY();
				}
				if (useKeyboard) {
					if (keyDown(Input.Keys.Q)) turn += 1;
					if (keyDown(Input.Keys.E)) turn -= 1;
				}
				lookYaw += turn * turnSpeed * dt;
				lookPitch = clamp(lookPitch + tilt * turnSpeed * 0.6f * dt, -85, 85);
			}
			c.yaw = lookYaw;
			c.pitch = lookPitch;
			c.setTouchLook(dragToLook && !locked);
			c.sensitivity = lookSensitivity;
		}
		// move
		float fwd = 0, right = 0;
		if (!locked) {
			Support3D.readMove(this, joystick, null, null, null, null, useKeyboard, deadZone, in);
			right = in[0] + extRight;
			fwd = in[1] + extForward;
		}
		float len = (float) Math.sqrt(fwd * fwd + right * right);
		if (len > 1) {
			fwd /= len;
			right /= len;
			len = 1;
		}
		running = extRun || isPressed(runButton) || (useKeyboard && keyDown(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT)) || (runAtFullPush && len > 0.95f);
		float speed = running ? runSpeed : walkSpeed;
		Support3D.relative(right, fwd, lookYaw, dir);
		float k = Support3D.smooth(acceleration, dt);
		vx += (dir[0] * speed - vx) * k;
		vz += (dir[1] * speed - vz) * k;
		if (len < 0.01f && Math.abs(vx) + Math.abs(vz) < 0.02f) vx = vz = 0;
		Object3D o = obj();
		if (o != null) o.setYaw3(lookYaw);
		if (!locked && (jumpQueued || useKeyboard && keyDown(KEY_SPACE)) && jumpSpeed > 0) {
			if (mover.jump(item, jumpSpeed)) fire("onJump");
		}
		jumpQueued = false;
		float feet = camera3d() != null ? eyeHeight : Support3D.Mover.feetOffset(item, eyeHeight);
		if (camera3d() != null) camera3d().position.y -= lastBob;
		mover.step(w, item, vx, vz, dt, feet, radius, true);
		if (mover.grounded && !wasGrounded) {
			fire("onLand");
			if (landSound.length() > 0) Support3D.play(landSound, stepVolume, 1, 0);
		}
		wasGrounded = mover.grounded;
		// head bob + footsteps
		float sp = getSpeed();
		if (mover.grounded && sp > 0.2f) {
			stepDist += sp * dt;
			if (stepDist >= stepLength) {
				stepDist -= stepLength;
				stepIndex++;
				String s = (stepIndex % 2 == 0 || footstepSound2.length() == 0) ? footstepSound : footstepSound2;
				if (s.length() > 0) Support3D.play(s, stepVolume * (running ? 1f : 0.8f), MathUtils.random(0.92f, 1.08f), 0);
				fire("onStep");
			}
		} else stepDist = Math.max(0, stepDist - dt);
		float target = mover.grounded && sp > 0.2f ? MathUtils.sin(stepDist / stepLength * MathUtils.PI2) * headBob * Math.min(1f, sp / Math.max(0.1f, walkSpeed)) : 0;
		bob += (target - bob) * Support3D.smooth(14, dt);
		if (c != null && camera3d() == null) {
			c.height = eyeHeight - feet + bob;
		} else if (c != null) {
			// the camera walks by itself: eyes = position; the bob is added on top and taken off before the next step
			c.position.y += bob;
		}
		lastBob = camera3d() != null ? bob : 0;
	}

	private float lastBob;
}
