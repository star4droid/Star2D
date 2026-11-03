package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
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
import com.star4droid.template.d3.Vehicle3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Car 3D", icon = "car-sports", category = "Vehicles", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onFlip()", "onDriftStart()", "onDriftEnd()"},
		desc = "A drivable 3D car with real suspension (Bullet raycast vehicle): gas, brake / reverse, steering, handbrake drifts, top speed in km/h. Wheels can be nodes of the car model (\"wheel-front-left\"...), separate elements, or invisible. Needs a DYNAMIC body.")
@Exclusive("movement")
public class Car3D extends Behavior {
	public enum WheelMode { AUTO, MODEL_NODES, ELEMENTS, INVISIBLE }

	public enum Drive { REAR, FRONT, ALL }

	@Header("Controls")
	@Param(desc = "Joystick: left/right steers, up is gas, down is brake / reverse.")
	public Joystick joystick;
	@Param(desc = "Gas button (held).")
	public PlayerItem gasButton;
	@Param(desc = "Brake button (held): brakes, then reverses when stopped.")
	public PlayerItem brakeButton;
	@Param(desc = "Steer left button (held).")
	public PlayerItem leftButton;
	@Param(desc = "Steer right button (held).")
	public PlayerItem rightButton;
	@Param(desc = "Handbrake button (held): locks the rear wheels to drift.")
	public PlayerItem handbrakeButton;
	@Param(desc = "Arrows / WASD drive, space is the handbrake (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Tilt the phone to steer.")
	public boolean useTilt = false;
	@Param(desc = "Always pressing the gas (endless runners: steer only).")
	public boolean autoGas = false;
	@Param(desc = "Joystick pushes smaller than this are ignored.", min = 0, max = 0.9, step = 0.05)
	public float deadZone = 0.12f;

	@Header("Engine and brakes")
	@Param(desc = "Top speed forward.", unit = "km/h", min = 1, max = 100000)
	public float maxSpeed = 140;
	@Param(desc = "Top speed in reverse.", unit = "km/h", min = 0, max = 100000)
	public float reverseSpeed = 25;
	@Param(desc = "How fast the engine speeds the car up.", unit = "m/s2", min = 0.5, max = 100000, step = 0.5)
	public float acceleration = 5.5f;
	@Param(desc = "How hard the brakes slow it down.", unit = "m/s2", min = 1, max = 100000, step = 0.5)
	public float brakeStrength = 14;
	@Param(desc = "Which wheels the engine turns.")
	public Drive drive = Drive.REAR;
	@Param(desc = "Looping engine sound; its pitch follows the speed (empty = silent).")
	@Sound
	public String engineSound = "";
	@Param(desc = "Volume of the engine sound.", min = 0, max = 1, step = 0.05)
	public float engineVolume = 0.5f;

	@Header("Steering")
	@Param(desc = "Largest angle of the front wheels.", unit = "deg", min = 5, max = 60)
	public float maxSteerAngle = 32;
	@Param(desc = "How fast the wheels turn.", unit = "deg/s", min = 20, max = 100000, step = 10)
	public float steerSpeed = 140;
	@Param(desc = "Steers less at top speed (0 = never, 1 = almost straight).", min = 0, max = 0.95, step = 0.05)
	public float steerReduction = 0.55f;

	@Header("Grip and suspension")
	@Param(desc = "Tire grip (higher = sticks to the road, lower = slides).", min = 0.3, max = 20, step = 0.1)
	public float grip = 3.2f;
	@Param(desc = "Rear grip while the handbrake is held (small = drift).", min = 0.1, max = 10, step = 0.1)
	public float driftGrip = 0.9f;
	@Param(desc = "Sideways speed that counts as a drift (events).", unit = "m/s", min = 0.5, max = 100000, step = 0.5)
	public float driftSpeed = 3;
	@Param(desc = "Free length of the springs.", unit = "m", min = 0.05, max = 2, step = 0.01)
	public float suspensionLength = 0.25f;
	@Param(desc = "Spring stiffness (higher = harder ride).", min = 5, max = 200, step = 1)
	public float stiffness = 40;
	@Param(desc = "Spring damping (higher = less bouncing).", min = 0.1, max = 20, step = 0.1)
	public float damping = 4f;
	@Param(desc = "How much the body leans in turns (0 = flat, 1 = real, can roll over).", min = 0, max = 1, step = 0.05)
	public float bodyRoll = 0.15f;

	@Header("Wheels")
	@Param(desc = "AUTO: nodes of the model if found, else wheel elements, else invisible wheels at the corners.")
	public WheelMode wheelMode = WheelMode.AUTO;
	@Param(desc = "Front left wheel node of the car model.")
	public String frontLeftNode = "wheel-front-left";
	@Param(desc = "Front right wheel node of the car model.")
	public String frontRightNode = "wheel-front-right";
	@Param(desc = "Rear left wheel node of the car model.")
	public String rearLeftNode = "wheel-back-left";
	@Param(desc = "Rear right wheel node of the car model.")
	public String rearRightNode = "wheel-back-right";
	@Param(desc = "Front left wheel element (ELEMENTS mode).")
	public PlayerItem frontLeftWheel;
	@Param(desc = "Front right wheel element (ELEMENTS mode).")
	public PlayerItem frontRightWheel;
	@Param(desc = "Rear left wheel element (ELEMENTS mode).")
	public PlayerItem rearLeftWheel;
	@Param(desc = "Rear right wheel element (ELEMENTS mode).")
	public PlayerItem rearRightWheel;
	@Param(desc = "Wheel radius (0 = measured from the wheels).", unit = "m", min = 0, max = 3, step = 0.01)
	public float wheelRadius = 0;
	@Param(desc = "Shrinks the body collider above the wheels so the springs carry the car (recommended).")
	public boolean fitCollider = true;

	@Header("Camera and safety")
	@Param(desc = "The camera follows behind the car (empty camera = the main one).")
	public boolean followCamera = true;
	@Param(desc = "Camera that follows (empty = the main camera).")
	public PlayerItem camera;
	@Param(desc = "Camera distance behind the car.", unit = "m", min = 1, max = 60, step = 0.5)
	public float cameraDistance = 7;
	@Param(desc = "Camera height above the car.", unit = "m", min = 0, max = 30, step = 0.1)
	public float cameraHeight = 2.6f;
	@Param(desc = "Puts the car back on its wheels after it stays upside down this long (0 = never).", unit = "s", min = 0, max = 20, step = 0.5)
	public float flipResetTime = 2.5f;

	private static final int FL = 0, FR = 1, RL = 2, RR = 3;
	private Vehicle3D vehicle;
	private WheelMode mode;
	private final String[] nodes = new String[4];
	private final Object3D[] wheelEls = new Object3D[4];
	private final Quaternion[] wheelBase = new Quaternion[4];
	/** Wheel element centers in the body's space (ELEMENTS mode). */
	private final Vector3[] elOff = {new Vector3(), new Vector3(), new Vector3(), new Vector3()};
	private final float[] cy = new float[4];
	private float radius = 0.35f, steer, spin, extThrottle, extSteer, flipTime, buildRetry;
	private boolean extBrake, extHandbrake, drifting, collFitted, camDone;
	private final float[] in = new float[2], tilt = new float[2];
	private final Support3D.Loop engine = new Support3D.Loop();
	private float engineSign = 1, wrongWay, lastForce, lastKmh;

	// ------------------------------------------------------------ blocks

	@Block(label = "set gas", desc = "Gas from code: 1 = full, -1 = reverse, 0 = buttons again.", labelAr = "اضبط الوقود", descAr = "الوقود من الكود: 1 = كامل، -1 = للخلف، 0 = الأزرار مجدداً.", category = "Car 3D", args = {"amount"})
	public void setGas(float amount) {
		extThrottle = clamp(amount, -1, 1);
	}

	@Block(label = "set steering", desc = "Steering from code: -1 = left, 1 = right, 0 = buttons again.", labelAr = "اضبط التوجيه", descAr = "التوجيه من الكود: -1 = يسار، 1 = يمين، 0 = الأزرار مجدداً.", category = "Car 3D", args = {"amount"})
	public void setSteering(float amount) {
		extSteer = clamp(amount, -1, 1);
	}

	@Block(label = "hold brake", desc = "true = brake from code.", labelAr = "اضغط الفرامل", descAr = "صح = فرملة من الكود.", category = "Car 3D", args = {"on"})
	public void setBrake(boolean on) {
		extBrake = on;
	}

	@Block(label = "hold handbrake", desc = "true = handbrake from code (drift).", labelAr = "اسحب فرامل اليد", descAr = "صح = فرامل اليد من الكود (انجراف).", category = "Car 3D", args = {"on"})
	public void setHandbrake(boolean on) {
		extHandbrake = on;
	}

	@Block(label = "set top speed", desc = "Top speed (km/h).", labelAr = "اضبط السرعة القصوى", descAr = "السرعة القصوى (كم/س).", category = "Car 3D", args = {"km/h"})
	public void setMaxSpeed(float kmh) {
		maxSpeed = Math.max(1, kmh);
	}

	@Block(label = "set acceleration", desc = "Engine power as acceleration (m/s2).", labelAr = "اضبط التسارع", descAr = "قوة المحرك كتسارع (م/ث²).", category = "Car 3D", args = {"m/s2"})
	public void setAcceleration(float v) {
		acceleration = Math.max(0, v);
	}

	@Block(label = "put car back on wheels", desc = "Upright at the same place, stopped.", labelAr = "أعد السيارة على عجلاتها", descAr = "تعتدل في نفس المكان وتتوقف.", category = "Car 3D", args = {})
	public void resetCar() {
		Object3D o = obj();
		if (o == null) return;
		float yaw = o.getYaw3();
		o.setPosition3(o.position.x, o.position.y + 1.2f, o.position.z);
		o.setRotation3(0, yaw, 0);
		o.setVelocity3(0, 0, 0);
		o.setAngularVelocity3(0, 0, 0);
		flipTime = 0;
	}

	@Block(label = "speed km/h", desc = "Speed along the car (negative = reversing).", labelAr = "السرعة كم/س", descAr = "السرعة على طول السيارة (سالبة = للخلف).", category = "Car 3D", args = {})
	public float getSpeedKmh() {
		return vehicle == null ? 0 : vehicle.speedKmh();
	}

	@Block(label = "speed text", desc = "\"87 km/h\" for a speedometer text.", labelAr = "نص السرعة", descAr = "\"87 كم/س\" لنص عداد السرعة.", category = "Car 3D", args = {})
	public String getSpeedText() {
		return Math.round(Math.abs(getSpeedKmh())) + " km/h";
	}

	@Block(label = "is drifting", desc = "True while sliding sideways.", labelAr = "ينجرف", descAr = "صح أثناء الانزلاق جانبياً.", category = "Car 3D", args = {})
	public boolean isDrifting() {
		return drifting;
	}

	@Block(label = "wheels on the ground", desc = "True when at least two wheels touch the ground.", labelAr = "العجلات على الأرض", descAr = "صح عندما تلمس عجلتان على الأقل الأرض.", category = "Car 3D", args = {})
	public boolean isGrounded() {
		if (vehicle == null) return false;
		int n = 0;
		for (int i = 0; i < vehicle.wheelCount(); i++) if (vehicle.inContact(i)) n++;
		return n >= 2;
	}

	// ------------------------------------------------------------ setup

	@Override
	public void onStart() {
		build();
	}

	@Override
	public void onEnable() {
		camDone = false;
		build();
	}

	@Override
	public void onDisable() {
		engine.stop();
		if (vehicle != null) {
			for (int i = 0; i < vehicle.wheelCount(); i++) {
				vehicle.setEngineForce(i, 0);
				vehicle.setBrake(i, brakeImpulse() * 0.5f);
			}
		}
		if (followCamera) {
			Camera3D c = Support3D.cameraOr(camera, world3d());
			if (c != null && c.target == item) c.stopFollow();
		}
	}

	@Override
	public void onDestroy() {
		engine.stop();
		if (vehicle != null) vehicle.dispose();
		vehicle = null;
	}

	private boolean anyNode(Object3D o) {
		return o.hasNode(frontLeftNode) || o.hasNode(frontRightNode) || o.hasNode(rearLeftNode) || o.hasNode(rearRightNode);
	}

	private void build() {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || !o.isDynamic() || !w.hasPhysics()) return;
		if (vehicle != null && vehicle.valid()) return;
		if (vehicle != null) vehicle.dispose();
		vehicle = null;
		mode = wheelMode;
		if (mode == WheelMode.AUTO) {
			if (anyNode(o)) mode = WheelMode.MODEL_NODES;
			else if (frontLeftWheel != null || rearLeftWheel != null) mode = WheelMode.ELEMENTS;
			else mode = WheelMode.INVISIBLE;
		}
		nodes[FL] = frontLeftNode;
		nodes[FR] = frontRightNode;
		nodes[RL] = rearLeftNode;
		nodes[RR] = rearRightNode;
		PlayerItem[] els = {frontLeftWheel, frontRightWheel, rearLeftWheel, rearRightWheel};
		BoundingBox lb = o.localBounds();
		Vector3 s = o.scale, c = new Vector3(), size = new Vector3();
		float[] pts = new float[12];
		boolean[] have = new boolean[4];
		float r = wheelRadius;
		float measured = 0;
		int count = 0;
		for (int i = 0; i < 4; i++) {
			boolean ok = false;
			if (mode == WheelMode.MODEL_NODES && o.getNodeCenter(nodes[i], c)) {
				c.scl(s);
				if (o.getNodeSize(nodes[i], size)) measured = Math.max(measured, size.y * Math.abs(s.y) * 0.5f);
				ok = true;
			} else if (mode == WheelMode.ELEMENTS && els[i] instanceof Object3D) {
				Object3D we = (Object3D) els[i];
				we.detach3D();
				we.setPhysicsEnabled(false);
				c.set(we.position).sub(o.position).mul(new Quaternion(o.rotation).conjugate());
				measured = Math.max(measured, we.getSizeY() * 0.5f);
				wheelEls[i] = we;
				wheelBase[i] = new Quaternion(o.rotation).conjugate().mul(we.rotation);
				elOff[i].set(c);
				ok = true;
			}
			if (ok) {
				pts[i * 3] = c.x;
				pts[i * 3 + 1] = c.y;
				pts[i * 3 + 2] = c.z;
				have[i] = true;
				count++;
			}
		}
		if (r <= 0) r = measured > 0.02f ? measured : Math.max(0.12f, lb.getHeight() * Math.abs(s.y) * 0.22f);
		radius = r;
		// missing wheels: corners of the body (front = -Z)
		float hx = lb.getWidth() * Math.abs(s.x) * 0.5f - r * 0.6f, minZ = lb.min.z * s.z + r * 1.4f, maxZ = lb.max.z * s.z - r * 1.4f;
		float cxm = lb.getCenterX() * s.x, wy = lb.min.y * s.y + r;
		for (int i = 0; i < 4; i++) {
			if (have[i]) continue;
			pts[i * 3] = cxm + (i == FL || i == RL ? -hx : hx);
			pts[i * 3 + 1] = wy;
			pts[i * 3 + 2] = i < 2 ? Math.min(minZ, maxZ) : Math.max(minZ, maxZ);
		}
		if (count == 0 && mode != WheelMode.INVISIBLE) mode = WheelMode.INVISIBLE;
		// the springs hang the wheel below the attachment point: lift the points so the car rests near the drawn wheels
		float rest = suspensionLength;
		float lowest = Float.MAX_VALUE, topAttach = -Float.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			cy[i] = pts[i * 3 + 1];
			lowest = Math.min(lowest, cy[i]);
			pts[i * 3 + 1] += rest * 0.75f;
			topAttach = Math.max(topAttach, pts[i * 3 + 1]);
		}
		if (fitCollider && !collFitted && o.d() != null) {
			collFitted = true;
			String col = o.d().Collider == null ? "AUTO" : o.d().Collider.toUpperCase();
			if (col.equals("AUTO") || col.equals("BOX")) {
				float minY = lb.min.y * s.y, maxY = lb.max.y * s.y, h = maxY - minY;
				// the wheel rays start at the attachment points: they must start BELOW the body's collider, a ray that starts inside
				// it hits the car itself now and then (the wheels flicker off the road, the car bounces and drifts)
				float bottom = Math.max(Math.min(maxY - h * 0.25f, lowest + r * 0.15f), topAttach + 0.03f);
				if (bottom > maxY - h * 0.15f) bottom = maxY - h * 0.15f;
				if (h > 0.01f && bottom > minY) {
					o.d().Collider_Scale_Y = (maxY - bottom) / h;
					o.d().Collider_Offset_Y = (bottom - minY) * 0.5f;
					o.d().Collider_Scale_X *= 0.95f;
					o.setBodyType("DYNAMIC");
				}
			}
		}
		boolean[] front = {true, true, false, false};
		vehicle = Vehicle3D.create(o, pts, front, r, rest, stiffness, damping, damping * 1.6f, grip, bodyRoll, rest * 0.9f);
		if (vehicle != null) o.lockRotation3(false, false, false);
		attachCamera();
	}

	private void attachCamera() {
		if (!followCamera || camDone) return;
		Camera3D c = Support3D.cameraOr(camera, world3d());
		if (c == null) return;
		camDone = true;
		c.follow(item, "THIRD_PERSON");
		c.setFollowDistance(cameraDistance, cameraHeight);
		c.lookHeight = 1f;
		c.angle = 0;
	}

	/** The body's real mass (Bullet), else the element's Mass: the engine force is acceleration x mass. */
	private float mass() {
		float m = vehicle != null ? vehicle.mass() : 0;
		if (m > 0) return m;
		Object3D o = obj();
		return o == null || o.d() == null ? 1 : Math.max(0.001f, o.d().Mass);
	}

	private float brakeImpulse() {
		return brakeStrength * mass() / 60f / 4f;
	}

	// ------------------------------------------------------------ frame

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null || dt <= 0) return;
		if (vehicle == null || !vehicle.valid()) {
			buildRetry -= dt;
			if (buildRetry <= 0) {
				buildRetry = 0.5f;
				vehicle = null;
				build();
			}
			if (vehicle == null) return;
		}
		// input
		float throttle = 0, steerIn = 0;
		Support3D.readMove(this, joystick, null, null, null, null, false, deadZone, in);
		throttle += in[1];
		steerIn += in[0];
		if (isPressed(gasButton) || useKeyboard && keyDown(KEY_UP, KEY_W)) throttle += 1;
		if (isPressed(brakeButton) || useKeyboard && keyDown(KEY_DOWN, KEY_S) || extBrake) throttle -= 1;
		if (isPressed(leftButton) || useKeyboard && keyDown(KEY_LEFT, KEY_A)) steerIn -= 1;
		if (isPressed(rightButton) || useKeyboard && keyDown(KEY_RIGHT, KEY_D)) steerIn += 1;
		if (useTilt && TiltMove.read(TiltMove.Orientation.AUTO, tilt) && Math.abs(tilt[0]) > 0.6f) steerIn += clamp(tilt[0] / 4f, -1, 1);
		if (extThrottle != 0) throttle = extThrottle;
		if (extSteer != 0) steerIn = extSteer;
		if (autoGas && throttle >= 0) throttle = 1;
		throttle = clamp(throttle, -1, 1);
		steerIn = clamp(steerIn, -1, 1);
		boolean hand = isPressed(handbrakeButton) || useKeyboard && keyDown(KEY_SPACE) || extHandbrake;

		float kmh = vehicle.speedKmh();
		float m = mass();
		int driven = drive == Drive.ALL ? 4 : 2;
		// the body's linear damping takes this much speed away every second: the engine gives it back, so the top speed is reached
		float damp = vehicle.linearDamping();
		float dampAcc = damp > 0 && damp < 1 ? (float) -Math.log(1 - damp) * Math.abs(kmh) / 3.6f : 0;
		float force = 0, brake = 0;
		// sanity: if the gas makes it roll backwards the wheel setup is mirrored -> flip the engine once (no braking meanwhile)
		boolean grounded = isGrounded();
		if (throttle > 0.5f && lastForce > 0 && grounded && kmh < -3f && kmh < lastKmh) {
			wrongWay += dt;
			if (wrongWay > 0.8f) {
				engineSign = -engineSign;
				wrongWay = 0;
			}
		} else if (throttle <= 0.5f || kmh >= 0) wrongWay = 0;
		if (throttle > 0.01f) {
			if (kmh < -2f && wrongWay <= 0) brake = brakeImpulse() * throttle; // rolling back: brake first
			else {
				float room = clamp((maxSpeed - kmh) / 4f, 0, 1); // eases into the top speed instead of hitting it
				force = (acceleration * throttle + dampAcc) * room * m / driven;
			}
		} else if (throttle < -0.01f) {
			if (kmh > 2f) brake = brakeImpulse() * -throttle;
			else {
				float room = clamp((reverseSpeed + kmh) / 3f, 0, 1);
				force = (acceleration * 0.6f * throttle - dampAcc) * room * m / driven;
			}
		} else brake = brakeImpulse() * 0.06f; // rolling resistance
		if (throttle > 0.01f && kmh > maxSpeed + 1f || throttle < -0.01f && -kmh > reverseSpeed + 1f)
			brake = Math.max(brake, brakeImpulse() * 0.35f); // over the top speed (lowered by a block, downhill): engine braking
		lastForce = force > 0 ? force : 0;
		lastKmh = kmh;
		float limit = maxSteerAngle * (1f - steerReduction * Math.min(1f, Math.abs(kmh) / Math.max(1f, maxSpeed)));
		steer = Support3D.approachAngle(steer, -steerIn * limit, steerSpeed * dt);
		for (int i = 0; i < 4; i++) {
			boolean isFront = i < 2;
			boolean powered = drive == Drive.ALL || (drive == Drive.FRONT) == isFront;
			vehicle.setEngineForce(i, powered ? force * engineSign : 0);
			float b = brake;
			if (hand && !isFront) b = Math.max(b, brakeImpulse() * 2f);
			vehicle.setBrake(i, b);
			vehicle.setFrictionSlip(i, hand && !isFront ? driftGrip : grip);
			vehicle.setSteering(i, isFront ? steer * MathUtils.degreesToRadians : 0);
		}
		// drift
		Vector3 right = o.right(new Vector3());
		float side = Math.abs(o.getVelocityX3() * right.x + o.getVelocityY3() * right.y + o.getVelocityZ3() * right.z);
		boolean nowDrift = side > driftSpeed && isGrounded();
		if (nowDrift != drifting) {
			drifting = nowDrift;
			fire(drifting ? "onDriftStart" : "onDriftEnd");
		}
		// flipped?
		if (flipResetTime > 0 && o.up(new Vector3()).y < 0.35f && Math.abs(kmh) < 8) {
			flipTime += dt;
			if (flipTime >= flipResetTime) {
				resetCar();
				fire("onFlip");
			}
		} else flipTime = 0;
		// wheels look
		spin += kmh / 3.6f * dt / Math.max(0.05f, radius) * MathUtils.radiansToDegrees;
		spin %= 360f;
		for (int i = 0; i < 4; i++) {
			float drop = vehicle.suspensionLength(i) - suspensionLength * 0.75f;
			float st = i < 2 ? steer : 0;
			if (mode == WheelMode.MODEL_NODES) {
				float sy = Math.abs(o.scale.y) < 0.0001f ? 1 : o.scale.y;
				o.setNodeOffset(nodes[i], 0, -drop / sy, 0, -spin, st, 0);
			} else if (mode == WheelMode.ELEMENTS && wheelEls[i] != null && !wheelEls[i].isDestroyed()) {
				Object3D we = wheelEls[i];
				Vector3 wp = tmpA.set(elOff[i].x, elOff[i].y - drop, elOff[i].z).mul(o.rotation).add(o.position);
				we.setPosition3(wp.x, wp.y, wp.z);
				Quaternion wr = tmpQ.set(o.rotation).mul(tmpQ2.setEulerAngles(st, -spin, 0)).mul(wheelBase[i]);
				we.setRotation3(wr.getPitch(), wr.getYaw(), wr.getRoll());
			}
		}
		// sound
		float k = Math.min(1f, Math.abs(kmh) / Math.max(1f, maxSpeed));
		engine.update(engineSound, engineSound.length() > 0, engineVolume * (0.55f + 0.45f * Math.max(k, Math.abs(throttle))), 0.7f + 1.1f * k);
	}

	private final Vector3 tmpA = new Vector3();
	private final Quaternion tmpQ = new Quaternion(), tmpQ2 = new Quaternion();
}
