package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.star4droid.template.Items.Joystick;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Third Person Controller 3D", icon = "human", category = "Movement", targets = BehaviorInfo.Target.THREE_D,
		types = {"OBJ3D"}, events = {"onJump()", "onLand()"},
		desc = "A character seen from behind: the joystick moves it relative to the camera, it turns to where it walks, jumps, runs and plays its idle / walk / run / jump clips. Drag the screen to orbit the camera, pinch to zoom.")
@Exclusive("movement")
public class ThirdPersonController3D extends Behavior {
	public enum CameraMode { ORBIT_TOUCH, BEHIND, NONE }

	@Header("Controls")
	@Param(desc = "Joystick that moves the character (up = away from the camera).")
	public Joystick joystick;
	@Param(desc = "Jump button (any element).")
	public PlayerItem jumpButton;
	@Param(desc = "Run button: held = run.")
	public PlayerItem runButton;
	@Param(desc = "WASD / arrows move, space jumps, shift runs (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Runs when the joystick is pushed almost to the edge.")
	public boolean runAtFullPush = true;
	@Param(desc = "Joystick pushes smaller than this are ignored.", min = 0, max = 0.9, step = 0.05)
	public float deadZone = 0.12f;

	@Header("Camera")
	@Param(desc = "The camera (empty = the main camera).")
	public PlayerItem camera;
	@Param(desc = "ORBIT_TOUCH: drag to orbit, pinch to zoom. BEHIND: stays behind the character. NONE: the camera is left alone.")
	public CameraMode cameraMode = CameraMode.ORBIT_TOUCH;
	@Param(desc = "Camera distance from the character.", unit = "m", min = 1, max = 60, step = 0.5)
	public float cameraDistance = 6;
	@Param(desc = "Camera height (BEHIND) or the point it looks at above the feet (ORBIT_TOUCH).", unit = "m", min = 0, max = 20, step = 0.1)
	public float cameraHeight = 1.6f;
	@Param(desc = "Starting tilt of the orbit camera (negative = from above).", unit = "deg", min = -85, max = 60)
	public float cameraPitch = -18;

	@Header("Movement")
	@Param(desc = "Walking speed.", unit = "m/s", min = 0, max = 100000, step = 0.1)
	public float walkSpeed = 2.4f;
	@Param(desc = "Running speed.", unit = "m/s", min = 0, max = 100000, step = 0.1)
	public float runSpeed = 5.5f;
	@Param(desc = "How fast it reaches the wanted speed.", unit = "1/s", min = 0.5, max = 40, step = 0.5)
	public float acceleration = 10;
	@Param(desc = "How fast it turns toward where it walks.", unit = "deg/s", min = 30, max = 100000, step = 10)
	public float turnSpeed = 600;
	@Param(desc = "Jump take-off speed (0 = no jump).", unit = "m/s", min = 0, max = 100000, step = 0.1)
	public float jumpSpeed = 5f;
	@Param(desc = "Body radius used to stop at walls when it has no DYNAMIC body.", unit = "m", min = 0.05, max = 3, step = 0.05)
	public float radius = 0.35f;
	@Param(desc = "Steepest slope it can walk up (0 = any): hills of a terrain stop it.", unit = "deg", min = 0, max = 89, step = 1)
	public float maxSlope = 0;
	@Param(desc = "Extra heading of the model: 180 when the model faces +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	@Header("Animation clips")
	@Param(desc = "Clip while standing (part of the name is enough, empty = none).")
	public String idleClip = "idle";
	@Param(desc = "Clip while walking.")
	public String walkClip = "walk";
	@Param(desc = "Clip while running (empty = the walk clip).")
	public String runClip = "run";
	@Param(desc = "Clip in the air.")
	public String jumpClip = "jump";
	@Param(desc = "Blend time between clips.", unit = "s", min = 0, max = 1, step = 0.05)
	public float blendTime = 0.2f;

	private final Support3D.Mover mover = new Support3D.Mover();
	private final Support3D.Clips clips = new Support3D.Clips();
	private final float[] in = new float[2], dir = new float[2];
	private float vx, vz, heading, extForward, extRight;
	private boolean jumpQueued, wasGrounded = true, running, locked;
	private EventListener jumpL;
	private Camera3D followed;

	@Block(label = "jump", desc = "Jumps if standing on the ground.", labelAr = "اقفز", descAr = "يقفز إذا كان على الأرض.", category = "Third person", args = {})
	public void jump() {
		jumpQueued = true;
	}

	@Block(label = "set walk speed", desc = "Walking speed (m/s).", labelAr = "اضبط سرعة المشي", descAr = "سرعة المشي (م/ث).", category = "Third person", args = {"speed"})
	public void setWalkSpeed(float value) {
		walkSpeed = Math.max(0, value);
	}

	@Block(label = "set run speed", desc = "Running speed (m/s).", labelAr = "اضبط سرعة الجري", descAr = "سرعة الجري (م/ث).", category = "Third person", args = {"speed"})
	public void setRunSpeed(float value) {
		runSpeed = Math.max(0, value);
	}

	@Block(label = "move by code", desc = "Moves forward / right (-1..1) relative to the camera until set to 0, 0.", labelAr = "تحرك بالكود", descAr = "يتحرك للأمام / لليمين (-1..1) بالنسبة للكاميرا حتى تضبطها 0، 0.", category = "Third person", args = {"forward", "right"})
	public void move(float forward, float right) {
		extForward = clamp(forward, -1, 1);
		extRight = clamp(right, -1, 1);
	}

	@Block(label = "lock controls", desc = "true = the character ignores the controls.", labelAr = "أقفل التحكم", descAr = "صح = تتجاهل الشخصية أدوات التحكم.", category = "Third person", args = {"locked"})
	public void setLocked(boolean on) {
		locked = on;
	}

	@Block(label = "set camera distance", desc = "Distance of the camera (m).", labelAr = "اضبط بعد الكاميرا", descAr = "بعد الكاميرا (م).", category = "Third person", args = {"meters"})
	public void setCameraDistance(float m) {
		cameraDistance = Math.max(1, m);
		Camera3D c = cam();
		if (c != null && cameraMode != CameraMode.NONE) c.distance = cameraDistance;
	}

	@Block(label = "is on the ground", desc = "True while standing on something.", labelAr = "على الأرض", descAr = "صح أثناء الوقوف على شيء.", category = "Third person", args = {})
	public boolean isGrounded() {
		return mover.grounded;
	}

	@Block(label = "is moving", desc = "True while walking or running.", labelAr = "يتحرك", descAr = "صح أثناء المشي أو الجري.", category = "Third person", args = {})
	public boolean isMoving() {
		return getSpeed() > 0.2f;
	}

	@Block(label = "moving speed", desc = "Current horizontal speed (m/s).", labelAr = "سرعة الحركة", descAr = "السرعة الأفقية الحالية (م/ث).", category = "Third person", args = {})
	public float getSpeed() {
		return (float) Math.sqrt(vx * vx + vz * vz);
	}

	/** Turns the character to a heading now (degrees; attacks aiming at an enemy). */
	public void setHeading(float yaw) {
		heading = yaw;
		Object3D o = obj();
		if (o != null) o.setYaw3(heading + modelYawOffset);
	}

	private Camera3D cam() {
		return Support3D.cameraOr(camera, world3d());
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o == null) return;
		if (o.isDynamic()) o.lockRotation3(true, true, true);
		heading = o.getYaw3() - modelYawOffset;
		// the player walks through the grass of painted terrains: it bends away
		if (com.star4droid.template.d3.Foliage3D.bender == null) com.star4droid.template.d3.Foliage3D.bender = o;
		if (jumpButton != null) jumpL = Support3D.onTap(jumpButton, new Runnable() {
			@Override
			public void run() {
				jumpQueued = true;
			}
		});
		attachCamera(true);
	}

	@Override
	public void onEnable() {
		attachCamera(false);
	}

	private void attachCamera(boolean first) {
		Camera3D c = cam();
		if (c == null || cameraMode == CameraMode.NONE) return;
		followed = c;
		c.distance = cameraDistance;
		if (cameraMode == CameraMode.ORBIT_TOUCH) {
			c.follow(item, "ORBIT");
			c.lookHeight = cameraHeight;
			c.setTouchLook(true);
			if (first) {
				c.pitch = cameraPitch;
				c.yaw = heading;
			}
		} else {
			c.follow(item, "THIRD_PERSON");
			c.height = cameraHeight;
			c.lookHeight = cameraHeight * 0.6f;
			c.angle = -modelYawOffset;
		}
	}

	@Override
	public void onDisable() {
		if (followed != null) {
			followed.stopFollow();
			followed.setTouchLook(false);
		}
		Object3D o = obj();
		if (o != null && o.isDynamic()) o.setVelocityKeepY(0, 0);
		vx = vz = 0;
		clips.locomotion(o, 0, true, walkSpeed, runSpeed, idleClip, walkClip, runClip, jumpClip, blendTime, true);
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(jumpButton, jumpL);
	}

	@Override
	public void onUpdate(float dt) {
		World3D w = world3d();
		Object3D o = obj();
		if (w == null || o == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
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
		running = isPressed(runButton) || (useKeyboard && keyDown(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT)) || (runAtFullPush && len > 0.95f);
		float speed = (running ? runSpeed : walkSpeed) * Math.min(1f, len * 1.15f);
		Camera3D c = cam();
		float camYaw = c != null ? Support3D.cameraYaw(c) : heading;
		Support3D.relative(right, fwd, camYaw, dir);
		float k = Support3D.smooth(acceleration, dt);
		vx += (dir[0] * speed - vx) * k;
		vz += (dir[1] * speed - vz) * k;
		if (len < 0.01f && Math.abs(vx) + Math.abs(vz) < 0.02f) vx = vz = 0;
		if (len > 0.05f) heading = Support3D.approachAngle(heading, Support3D.yawOf(dir[0], dir[1]), turnSpeed * dt);
		o.setYaw3(heading + modelYawOffset);
		if (!locked && (jumpQueued || useKeyboard && keyDown(KEY_SPACE)) && jumpSpeed > 0) {
			if (mover.jump(item, jumpSpeed)) fire("onJump");
		}
		jumpQueued = false;
		mover.maxSlope = maxSlope;
		mover.step(w, item, vx, vz, dt, Support3D.Mover.feetOffset(item, 0), radius, true);
		if (mover.grounded && !wasGrounded) fire("onLand");
		wasGrounded = mover.grounded;
		if (c != null && cameraMode == CameraMode.ORBIT_TOUCH) c.distance = clamp(c.distance, 1.5f, Math.max(cameraDistance * 2.5f, 3f));
		clips.locomotion(o, getSpeed(), mover.grounded, walkSpeed, runSpeed, idleClip, walkClip, runClip, jumpClip, blendTime, true);
	}
}
