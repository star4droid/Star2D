package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.JointEdge;
import com.badlogic.gdx.physics.box2d.joints.WheelJoint;
import com.badlogic.gdx.physics.box2d.joints.WheelJointDef;
import com.badlogic.gdx.utils.Array;
import com.star4droid.template.Items.Joystick;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Car", icon = "car", category = "Vehicles", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onDriftStart()", "onDriftEnd()"},
		desc = "A drivable car with gas, brake, steering and handbrake. TOP_DOWN: a bicycle-model car seen from above (steering angle, wheelbase, grip, handbrake drift). SIDE_SCROLLER: drives the wheel elements of a car you built with joints.")
@Exclusive("movement")
@Requires(body = true, type = "DYNAMIC", note = "Needs a DYNAMIC physical element (the car body). Top-down: gravity 0 in the scene. Side-scroller: wheels joined to the body with joints.")
public class Car extends Behavior {
	public enum Mode { TOP_DOWN, SIDE_SCROLLER }

	@Header("Mode")
	@Param(desc = "TOP_DOWN: a car seen from above. SIDE_SCROLLER: the wheels list is driven by motors (build the car with joints in the editor).")
	public Mode mode = Mode.TOP_DOWN;

	@Header("Controls")
	@Param(desc = "Joystick: left/right steers (top-down) or drives (side), up/down is gas and brake.")
	public Joystick joystick;
	@Param(desc = "Gas button (any element). Held = accelerate.")
	public PlayerItem gasButton;
	@Param(desc = "Brake button. Top-down: brakes, then reverses. Side-scroller: locks the wheels.")
	public PlayerItem brakeButton;
	@Param(desc = "Top-down: steer left. Side-scroller: drive left / lean left in the air.")
	public PlayerItem leftButton;
	@Param(desc = "Top-down: steer right. Side-scroller: drive right / lean right in the air.")
	public PlayerItem rightButton;
	@Param(desc = "Locks the rear: the car slides and drifts (top-down) or the wheels stop (side-scroller).")
	public PlayerItem handbrakeButton;
	@Param(desc = "Arrow keys / WASD and space (handbrake) also drive (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Tilt the phone to steer (top-down).")
	public boolean useTilt = false;
	@Param(desc = "Joystick pushes smaller than this are ignored.", min = 0, max = 0.9, step = 0.05)
	public float deadZone = 0.15f;

	@Header("Engine and brakes")
	@Param(desc = "Top speed going forward.", unit = "px/s", min = 0, max = 100000)
	public float maxSpeed = 380;
	@Param(desc = "Top speed in reverse.", unit = "px/s", min = 0, max = 100000)
	public float reverseSpeed = 140;
	@Param(desc = "How fast the engine speeds the car up.", unit = "px/s2", min = 0, max = 100000, step = 10)
	public float acceleration = 300;
	@Param(desc = "How hard the brake slows the car.", unit = "px/s2", min = 0, max = 100000, step = 10)
	public float brakeForce = 700;
	@Param(desc = "Slowing down when nothing is pressed.", unit = "px/s2", min = 0, max = 100000, step = 10)
	public float rollingResistance = 120;

	@Header("Steering (top-down)")
	@Param(desc = "Largest angle of the front wheels.", unit = "deg", min = 1, max = 80)
	public float maxSteerAngle = 35;
	@Param(desc = "How fast the wheels turn to the wanted angle.", unit = "deg/s", min = 10, max = 100000, step = 10)
	public float steerSpeed = 160;
	@Param(desc = "Steers less at high speed (0 = no change, 1 = almost straight at top speed).", min = 0, max = 1, step = 0.05)
	public float steerReduction = 0.5f;
	@Param(desc = "Distance from the center of the car to the front axle. 0 (with the rear axle 0) = 35% of the longer side.", unit = "px", min = 0, max = 1000)
	public float frontAxle = 0;
	@Param(desc = "Distance from the center of the car to the rear axle. The wheelbase is front + rear axle.", unit = "px", min = 0, max = 1000)
	public float rearAxle = 0;
	@Param(desc = "Angle the front of the image points to when the element is not rotated (90 = up, 0 = right).", unit = "deg", min = -360, max = 360)
	public float noseAngle = 90;

	@Header("Grip (top-down)")
	@Param(desc = "How fast sideways sliding is stopped. High = on rails, low = slippery.", unit = "1/s", min = 0, max = 30, step = 0.5)
	public float grip = 8;
	@Param(desc = "Grip while the handbrake is on (small = drift).", unit = "1/s", min = 0, max = 30, step = 0.1)
	public float handbrakeGrip = 0.8f;
	@Param(desc = "Sideways speed that counts as a drift (onDriftStart).", unit = "px/s", min = 0, max = 100000)
	public float driftSpeed = 80;

	@Header("Wheels (side-scroller)")
	@Param(desc = "The wheel elements (physical circles joined to the car body with joints). The behavior spins them.")
	public List<PlayerItem> wheels = new ArrayList<PlayerItem>();
	@Param(desc = "Create the wheel joints for you (a WheelJoint from the car body to each wheel below). Turn off if you join the wheels yourself in the editor.")
	public boolean createJoints = true;
	@Param(desc = "The front wheel element. A wheel joint is created for it and the behavior spins it.")
	public PlayerItem frontWheel;
	@Param(desc = "The rear wheel element (the driven one). A wheel joint is created for it and the behavior spins it.")
	public PlayerItem rearWheel;
	@Param(desc = "Saved values of the front wheel joint (edit with the pencil button).")
	public String frontJoint = "";
	@Param(desc = "Saved values of the rear wheel joint (edit with the pencil button).")
	public String rearJoint = "";
	@Param(desc = "How fast the wheel motors speed up.", unit = "rad/s2", min = 1, max = 100000, step = 1)
	public float wheelAcceleration = 40;
	@Param(desc = "Rotation speed of the car body in the air while left/right is held.", unit = "deg/s", min = 0, max = 100000, step = 10)
	public float airSpin = 120;

	/** The wheel joint settings: key=value pairs split by ';'. A missing key keeps the default. */
	public static final String[] JOINT_KEYS = {"frequencyHz", "dampingRatio", "maxMotorTorque", "enableMotor", "motorSpeed", "axisX", "axisY", "anchorX", "anchorY", "collideConnected"};

	/** Defaults of the front (front = true) or rear wheel joint, in JOINT_KEYS order. Anchor offsets are from the wheel center (px). */
	public static float[] jointDefaults(boolean front) {
		return new float[]{10f, 0.7f, front ? 0f : 9999f, 0f, 0f, 0f, 1f, 0f, 0f, 0f};
	}

	/** Reads a saved string over the defaults. */
	public static float[] parseJoint(String s, boolean front) {
		float[] v = jointDefaults(front);
		if (s == null) return v;
		String[] parts = s.split(";");
		for (int i = 0; i < parts.length; i++) {
			int eq = parts[i].indexOf('=');
			if (eq <= 0) continue;
			String k = parts[i].substring(0, eq).trim();
			for (int j = 0; j < JOINT_KEYS.length; j++) {
				if (!JOINT_KEYS[j].equals(k)) continue;
				try {
					v[j] = Float.parseFloat(parts[i].substring(eq + 1).trim());
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return v;
	}

	/** Writes only the values that differ from the defaults ("" = all defaults). */
	public static String formatJoint(float[] v, boolean front) {
		float[] d = jointDefaults(front);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < JOINT_KEYS.length; i++) {
			if (v[i] == d[i]) continue;
			if (sb.length() > 0) sb.append(';');
			sb.append(JOINT_KEYS[i]).append('=').append(v[i]);
		}
		return sb.toString();
	}

	private WheelJoint frontJ, rearJ;
	private boolean jointsTried;
	private final float[] tilt = new float[2];
	private float vx, vy, steer, extThrottle, extSteer, savedGravity = 1, airTime;
	private boolean savedFixed, applied, drifting, grounded;

	public void onStart() {
		applyMode();
	}

	public void onEnable() {
		applyMode();
	}

	private void applyMode() {
		Body b = body();
		if (b == null || applied) return;
		if (mode == Mode.TOP_DOWN) {
			savedGravity = b.getGravityScale();
			savedFixed = b.isFixedRotation();
			b.setGravityScale(0);
			b.setFixedRotation(true);
			applied = true;
		}
	}

	public void onDisable() {
		Body b = body();
		if (b != null) {
			if (applied) {
				b.setGravityScale(savedGravity);
				b.setFixedRotation(savedFixed);
			}
			if (mode == Mode.TOP_DOWN) setVelocity(0, 0);
		}
		applied = false;
		steer = 0;
		drifting = false;
	}

	// ------------------------------------------------------------ blocks

	@Block(label = "set car max speed", desc = "Top speed forward (px/s).", labelAr = "اضبط أقصى سرعة للسيارة", descAr = "السرعة القصوى للأمام (بكسل/ثانية).", category = "Car", args = {"speed"})
	public void setMaxSpeed(float value) {
		maxSpeed = value;
	}

	@Block(label = "set car acceleration", desc = "Engine acceleration (px/s2).", labelAr = "اضبط تسارع السيارة", descAr = "تسارع المحرك (بكسل/ث²).", category = "Car", args = {"acceleration"})
	public void setAcceleration(float value) {
		acceleration = value;
	}

	@Block(label = "set steering angle", desc = "Largest wheel angle (degrees).", labelAr = "اضبط زاوية التوجيه", descAr = "أكبر زاوية للعجلات (درجة).", category = "Car", args = {"degrees"})
	public void setMaxSteerAngle(float value) {
		maxSteerAngle = value;
	}

	@Block(label = "set car grip", desc = "Sideways grip: high = on rails, low = slippery.", labelAr = "اضبط تماسك السيارة", descAr = "التماسك الجانبي: عالٍ = على سكة، منخفض = زلق.", category = "Car", args = {"grip"})
	public void setGrip(float value) {
		grip = value;
	}

	@Block(label = "drive input", desc = "Drives the car from your blocks (AI cars): gas -1..1 (negative = brake/reverse) and steering -1 (right)..1 (left). It stays until you call it again.", labelAr = "إدخال القيادة", descAr = "يقود السيارة من البلوكات (سيارات الذكاء الاصطناعي): الوقود -1..1 (سالب = فرامل/رجوع) والتوجيه -1 (يمين)..1 (يسار). يبقى حتى تستدعيه مجدداً.", category = "Car", args = {"gas", "steering"})
	public void setDriveInput(float gas, float steering) {
		extThrottle = clamp(gas, -1, 1);
		extSteer = clamp(steering, -1, 1);
	}

	@Block(label = "set driving mode", desc = "true = side-scroller (wheels), false = top-down.", labelAr = "اضبط نمط القيادة", descAr = "true = جانبي (عجلات)، false = من الأعلى.", category = "Car", args = {"sideScroller"})
	public void setSideScroller(boolean side) {
		Body b = body();
		if (b != null && applied && side) {
			b.setGravityScale(savedGravity);
			b.setFixedRotation(savedFixed);
			applied = false;
		}
		mode = side ? Mode.SIDE_SCROLLER : Mode.TOP_DOWN;
		applyMode();
	}

	@Block(label = "add wheel", desc = "Adds a wheel element for the side-scroller mode.", labelAr = "أضف عجلة", descAr = "يضيف عنصر عجلة لنمط القيادة الجانبي.", category = "Car", args = {"wheel"})
	public void addWheel(PlayerItem wheel) {
		if (wheel != null && !wheels.contains(wheel)) wheels.add(wheel);
	}

	@Block(label = "remove all wheels", desc = "Empties the wheels list.", labelAr = "أزل كل العجلات", descAr = "يفرّغ قائمة العجلات.", category = "Car")
	public void clearWheels() {
		wheels.clear();
	}

	@Block(label = "stop the car", desc = "Sets the speed to zero.", labelAr = "أوقف السيارة", descAr = "يجعل السرعة صفراً.", category = "Car")
	public void stopNow() {
		vx = 0;
		vy = 0;
		setVelocity(0, 0);
	}

	@Block(label = "car speed", desc = "Current forward speed (px/s, negative in reverse).", labelAr = "سرعة السيارة", descAr = "السرعة الأمامية الحالية (بكسل/ثانية، سالبة عند الرجوع).", category = "Car")
	public float getCarSpeed() {
		Body b = body();
		if (b == null) return 0;
		float k = Behavior.W / Math.max(1, stage.getSteps());
		if (mode == Mode.SIDE_SCROLLER) return b.getLinearVelocity().x / k;
		double h = b.getAngle() + Math.toRadians(noseAngle);
		return (float) ((b.getLinearVelocity().x * Math.cos(h) + b.getLinearVelocity().y * Math.sin(h)) / k);
	}

	@Block(label = "steering angle", desc = "Current front wheel angle in degrees (positive = left). Use it to turn wheel images.", labelAr = "زاوية التوجيه الحالية", descAr = "زاوية العجلات الأمامية الحالية بالدرجات (موجبة = يسار). استخدمها لتدوير صور العجلات.", category = "Car")
	public float getSteerAngle() {
		return steer;
	}

	@Block(label = "is drifting", desc = "True while the car slides sideways.", labelAr = "ينزلق جانبياً", descAr = "صحيح عندما تنزلق السيارة جانبياً.", category = "Car")
	public boolean isDrifting() {
		return drifting;
	}

	@Block(label = "wheelbase", desc = "Distance between the axles (px).", labelAr = "قاعدة العجلات", descAr = "المسافة بين المحورين (بكسل).", category = "Car")
	public float getWheelBase() {
		return front() + rear();
	}

	@Block(label = "wheels touch the ground", desc = "Side-scroller: true while a wheel touches something.", labelAr = "العجلات تلمس الأرض", descAr = "للنمط الجانبي: صحيح عندما تلمس عجلة شيئاً.", category = "Car")
	public boolean isGrounded() {
		return grounded;
	}

	// ------------------------------------------------------------ wheel joints

	private WheelJoint existing(Body car, Body wheel) {
		for (JointEdge je : wheel.getJointList()) {
			if (je.joint instanceof WheelJoint && (je.other == car)) return (WheelJoint) je.joint;
		}
		return null;
	}

	private WheelJoint makeJoint(Body car, PlayerItem w, boolean front) {
		if (w == null) return null;
		Body wb = w.getBody();
		if (wb == null) return null;
		WheelJoint old = existing(car, wb);
		if (old != null) return old; // joined in the editor already
		float[] v = parseJoint(front ? frontJoint : rearJoint, front);
		float ax = v[5], ay = v[6];
		if (ax == 0 && ay == 0) ay = 1;
		WheelJointDef def = new WheelJointDef();
		def.initialize(car, wb, wb.getWorldCenter().add(v[7] * Behavior.W, v[8] * Behavior.W), new Vector2(ax * Behavior.W, ay * Behavior.W));
		def.collideConnected = v[9] != 0;
		def.frequencyHz = v[0];
		def.dampingRatio = v[1];
		def.maxMotorTorque = v[2];
		def.enableMotor = v[3] != 0;
		def.motorSpeed = v[4];
		WheelJoint j = (WheelJoint) car.getWorld().createJoint(def);
		try {
			stage.addJoint(item.getName() + "_" + w.getName() + "_joint", j);
		} catch (RuntimeException ignored) {
		}
		return j;
	}

	/** Creates the wheel joints once (when every body exists). Returns true when done. */
	private boolean ensureJoints(Body car) {
		if (frontJ != null || rearJ != null) return true;
		if (!createJoints || jointsTried) return false;
		if ((frontWheel != null && frontWheel.getBody() == null) || (rearWheel != null && rearWheel.getBody() == null)) return false;
		jointsTried = true;
		frontJ = makeJoint(car, frontWheel, true);
		rearJ = makeJoint(car, rearWheel, false);
		return true;
	}

	@Block(label = "create wheel joints", desc = "Joins the front and rear wheels to the car body with wheel joints (the settings of the pencil button).", labelAr = "أنشئ روابط العجلات", descAr = "يربط العجلة الأمامية والخلفية بجسم السيارة بروابط عجلات (حسب إعدادات زر القلم).", category = "Car")
	public void createWheelJoints() {
		Body b = body();
		if (b == null) return;
		jointsTried = true;
		if (frontJ == null) frontJ = makeJoint(b, frontWheel, true);
		if (rearJ == null) rearJ = makeJoint(b, rearWheel, false);
	}

	@Block(label = "remove wheel joints", desc = "Destroys the wheel joints this behavior created.", labelAr = "أزل روابط العجلات", descAr = "يهدم روابط العجلات التي أنشأها السلوك.", category = "Car")
	public void removeWheelJoints() {
		Body b = body();
		if (b == null) return;
		if (frontJ != null) b.getWorld().destroyJoint(frontJ);
		if (rearJ != null) b.getWorld().destroyJoint(rearJ);
		frontJ = null;
		rearJ = null;
		jointsTried = true;
	}

	@Block(label = "set front wheel", desc = "Chooses the front wheel element.", labelAr = "اضبط العجلة الأمامية", descAr = "يحدد عنصر العجلة الأمامية.", category = "Car", args = {"wheel"})
	public void setFrontWheel(PlayerItem wheel) {
		frontWheel = wheel;
		jointsTried = false;
		frontJ = null;
	}

	@Block(label = "set rear wheel", desc = "Chooses the rear wheel element.", labelAr = "اضبط العجلة الخلفية", descAr = "يحدد عنصر العجلة الخلفية.", category = "Car", args = {"wheel"})
	public void setRearWheel(PlayerItem wheel) {
		rearWheel = wheel;
		jointsTried = false;
		rearJ = null;
	}

	private WheelJoint jointOf(boolean front) {
		return front ? frontJ : rearJ;
	}

	@Block(label = "set wheel suspension", desc = "Suspension of a wheel: frequency (Hz, higher = stiffer) and damping ratio. front = true for the front wheel, false for the rear.", labelAr = "اضبط تعليق العجلة", descAr = "تعليق العجلة: التردد (هرتز، الأعلى = أصلب) ونسبة التخميد. front = true للأمامية، false للخلفية.", category = "Car", args = {"front", "frequencyHz", "dampingRatio"})
	public void setWheelSuspension(boolean front, float frequencyHz, float dampingRatio) {
		float[] v = parseJoint(front ? frontJoint : rearJoint, front);
		v[0] = frequencyHz;
		v[1] = dampingRatio;
		store(front, v);
		WheelJoint j = jointOf(front);
		if (j != null) {
			j.setSpringFrequencyHz(frequencyHz);
			j.setSpringDampingRatio(dampingRatio);
		}
	}

	@Block(label = "set wheel motor torque", desc = "Strongest push of a wheel motor. front = true for the front wheel, false for the rear.", labelAr = "اضبط عزم محرك العجلة", descAr = "أقصى دفع لمحرك العجلة. front = true للأمامية، false للخلفية.", category = "Car", args = {"front", "torque"})
	public void setWheelTorque(boolean front, float torque) {
		float[] v = parseJoint(front ? frontJoint : rearJoint, front);
		v[2] = torque;
		store(front, v);
		WheelJoint j = jointOf(front);
		if (j != null) j.setMaxMotorTorque(torque);
	}

	@Block(label = "set wheel motor", desc = "Turns the motor of a wheel joint on/off and sets its speed (rad/s, negative = clockwise = drives right). front = true for the front wheel.", labelAr = "اضبط محرك العجلة", descAr = "يشغّل/يوقف محرك رابط العجلة ويحدد سرعته (راديان/ث، سالب = مع عقارب الساعة = يتجه لليمين). front = true للأمامية.", category = "Car", args = {"front", "enabled", "speed"})
	public void setWheelMotor(boolean front, boolean enabled, float speed) {
		float[] v = parseJoint(front ? frontJoint : rearJoint, front);
		v[3] = enabled ? 1f : 0f;
		v[4] = speed;
		store(front, v);
		WheelJoint j = jointOf(front);
		if (j != null) {
			j.enableMotor(enabled);
			j.setMotorSpeed(speed);
		}
	}

	@Block(label = "wheel spin speed", desc = "How fast a wheel turns (rad/s). front = true for the front wheel, false for the rear.", labelAr = "سرعة دوران العجلة", descAr = "سرعة دوران العجلة (راديان/ث). front = true للأمامية، false للخلفية.", category = "Car", args = {"front"})
	public float getWheelSpin(boolean front) {
		PlayerItem w = front ? frontWheel : rearWheel;
		Body wb = w == null ? null : w.getBody();
		return wb == null ? 0 : wb.getAngularVelocity();
	}

	private void store(boolean front, float[] v) {
		String s = formatJoint(v, front);
		if (front) frontJoint = s;
		else rearJoint = s;
	}

	// ------------------------------------------------------------ driving

	private float front() {
		if (frontAxle > 0 || rearAxle > 0) return Math.max(1f, frontAxle);
		return Math.max(1f, 0.35f * Math.max(widthPx(), heightPx()));
	}

	private float rear() {
		if (frontAxle > 0 || rearAxle > 0) return Math.max(1f, rearAxle);
		return Math.max(1f, 0.35f * Math.max(widthPx(), heightPx()));
	}

	private boolean handbrakeDown() {
		return isPressed(handbrakeButton) || (useKeyboard && keyDown(KEY_SPACE));
	}

	public void onUpdate(float dt) {
		Body b = body();
		if (b == null) return;
		if (mode == Mode.SIDE_SCROLLER) sideUpdate(b, dt);
		else topDownUpdate(b, dt);
	}

	private void topDownUpdate(Body b, float dt) {
		applyMode();
		float throttle = extThrottle, steerIn = extSteer;
		if (joystick != null) {
			float jx = joystick.getJoyStickX(), jy = joystick.getJoyStickY();
			if (jx * jx + jy * jy >= deadZone * deadZone) {
				steerIn -= jx;
				throttle += jy;
			}
		}
		if (isPressed(gasButton) || (useKeyboard && keyDown(KEY_UP, KEY_W))) throttle += 1;
		if (isPressed(brakeButton) || (useKeyboard && keyDown(KEY_DOWN, KEY_S))) throttle -= 1;
		if (isPressed(leftButton) || (useKeyboard && keyDown(KEY_LEFT, KEY_A))) steerIn += 1;
		if (isPressed(rightButton) || (useKeyboard && keyDown(KEY_RIGHT, KEY_D))) steerIn -= 1;
		if (useTilt && TiltMove.read(TiltMove.Orientation.AUTO, tilt) && Math.abs(tilt[0]) > 0.6f) steerIn -= clamp(tilt[0] / 4f, -1, 1);
		throttle = clamp(throttle, -1, 1);
		steerIn = clamp(steerIn, -1, 1);
		boolean hand = handbrakeDown();

		float k = Behavior.W / Math.max(1, stage.getSteps());
		vx = b.getLinearVelocity().x / k;
		vy = b.getLinearVelocity().y / k;
		float ang = (float) Math.toDegrees(b.getAngle());
		double h = Math.toRadians(ang + noseAngle);
		float fx = (float) Math.cos(h), fy = (float) Math.sin(h);
		float s = vx * fx + vy * fy;

		// steering wheels and heading (bicycle model)
		float limit = maxSteerAngle * (1f - steerReduction * Math.min(1f, Math.abs(s) / Math.max(1f, maxSpeed)));
		float want = steerIn * limit, step = steerSpeed * dt;
		steer += clamp(want - steer, -step, step);
		float wheelBase = front() + rear();
		float yaw = s / wheelBase * (float) Math.tan(Math.toRadians(steer)); // rad/s, counter-clockwise positive
		ang += (float) Math.toDegrees(yaw * dt);
		h = Math.toRadians(ang + noseAngle);
		fx = (float) Math.cos(h);
		fy = (float) Math.sin(h);

		// forward / sideways parts of the velocity in the new heading
		s = vx * fx + vy * fy;
		float l = -vx * fy + vy * fx;
		if (throttle > 0.01f) {
			if (s < -5f) s = Math.min(0f, s + brakeForce * throttle * dt);
			else s += acceleration * throttle * (1f - clamp(s / Math.max(1f, maxSpeed), 0f, 1f)) * dt;
		} else if (throttle < -0.01f) {
			if (s > 5f) s = Math.max(0f, s + brakeForce * throttle * dt);
			else s -= acceleration * -throttle * (1f - clamp(-s / Math.max(1f, reverseSpeed), 0f, 1f)) * dt;
		} else {
			float drop = rollingResistance * dt;
			s = Math.abs(s) <= drop ? 0f : s - Math.signum(s) * drop;
		}
		if (hand) {
			float drop = brakeForce * 0.3f * dt;
			s = Math.abs(s) <= drop ? 0f : s - Math.signum(s) * drop;
		}
		s = clamp(s, -reverseSpeed, maxSpeed);
		float wantL = yaw * rear(); // the center of the car swings around the rear axle
		float rate = Math.min(1f, (hand ? handbrakeGrip : grip) * dt);
		l += (wantL - l) * rate;
		vx = fx * s - fy * l;
		vy = fy * s + fx * l;
		b.setTransform(b.getPosition(), (float) Math.toRadians(ang));
		setVelocity(vx, vy);

		boolean nowDrift = Math.abs(l) > driftSpeed;
		if (nowDrift != drifting) {
			drifting = nowDrift;
			fire(drifting ? "onDriftStart" : "onDriftEnd");
		}
	}

	private void sideUpdate(Body b, float dt) {
		ensureJoints(b);
		if (frontWheel != null && !wheels.contains(frontWheel)) wheels.add(frontWheel);
		if (rearWheel != null && !wheels.contains(rearWheel)) wheels.add(rearWheel);
		float dir = extThrottle;
		boolean brake = false;
		if (joystick != null) {
			float jx = joystick.getJoyStickX(), jy = joystick.getJoyStickY();
			if (Math.abs(jx) >= deadZone) dir += jx;
			if (jy < -0.6f) brake = true;
		}
		if (isPressed(gasButton) || (useKeyboard && keyDown(KEY_UP, KEY_W))) dir += 1;
		if (isPressed(rightButton) || (useKeyboard && keyDown(KEY_RIGHT, KEY_D))) dir += 1;
		if (isPressed(leftButton) || (useKeyboard && keyDown(KEY_LEFT, KEY_A))) dir -= 1;
		if (isPressed(brakeButton) || (useKeyboard && keyDown(KEY_DOWN, KEY_S))) brake = true;
		dir = clamp(dir, -1, 1);
		boolean hand = handbrakeDown();

		if (wheels == null || wheels.isEmpty()) {
			// no wheels: push the body itself (a sled)
			float k = Behavior.W / Math.max(1, stage.getSteps());
			float cur = b.getLinearVelocity().x / k, target = (brake || hand) ? 0f : dir * (dir >= 0 ? maxSpeed : reverseSpeed);
			float a = ((brake || hand) ? brakeForce : acceleration) * dt;
			if (dir != 0 || brake || hand) setVelocityX(cur + clamp(target - cur, -a, a));
			grounded = true;
			airTime = 0;
			return;
		}
		boolean touching = false;
		for (int i = 0; i < wheels.size(); i++) {
			PlayerItem w = wheels.get(i);
			if (w == null) continue;
			Body wb = w.getBody();
			if (wb == null) continue;
			float radius = Math.max(1f, Math.min(wPx(w), hPx(w)) * 0.5f);
			float cur = wb.getAngularVelocity();
			if (hand) {
				wb.setAngularVelocity(0f);
			} else if (brake) {
				float a = wheelAcceleration * 3f * dt;
				wb.setAngularVelocity(cur + clamp(-cur, -a, a));
			} else if (dir != 0) {
				float target = -dir * (dir > 0 ? maxSpeed : reverseSpeed) / radius; // clockwise (negative) drives right
				float a = wheelAcceleration * dt;
				wb.setAngularVelocity(cur + clamp(target - cur, -a, a));
			}
			if (!touching) touching = wheelTouches(wb, b);
		}
		airTime = touching ? 0f : airTime + dt;
		grounded = airTime < 0.2f; // a short bump does not count as flying
		if (!grounded && dir != 0 && airSpin > 0) {
			// lean in the air: speeds the body's rotation up toward airSpin (counter-clockwise is positive, so right = clockwise = negative)
			float maxRate = (float) Math.toRadians(airSpin), cur = b.getAngularVelocity();
			float step = maxRate * 4f * dt;
			b.setAngularVelocity(cur + clamp(-dir * maxRate - cur, -step, step));
		}
	}

	/** True if the wheel body touches something that is not the car body or another wheel. */
	private boolean wheelTouches(Body wheel, Body chassis) {
		Array<Contact> contacts = wheel.getWorld().getContactList();
		for (int i = 0; i < contacts.size; i++) {
			Contact c = contacts.get(i);
			if (!c.isTouching() || c.getFixtureA().isSensor() || c.getFixtureB().isSensor()) continue;
			Body a = c.getFixtureA().getBody(), o = c.getFixtureB().getBody();
			Body other;
			if (a == wheel) other = o;
			else if (o == wheel) other = a;
			else continue;
			if (other == chassis || isWheel(other)) continue;
			return true;
		}
		return false;
	}

	private boolean isWheel(Body bd) {
		for (int i = 0; i < wheels.size(); i++) {
			PlayerItem w = wheels.get(i);
			if (w != null && w.getBody() == bd) return true;
		}
		return false;
	}
}
