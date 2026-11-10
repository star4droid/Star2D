package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.ElementDefs.Camera3DDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

/**
 * Camera of a 3D scene: free, or following an element (third / first person, orbit, top-down, side, look-at),
 * with smooth moves (moveTo / turnTo), shake and touch look.
 */
public class Camera3D extends Actor implements PlayerItem {
	public StageImp stage;
	public World3D world;
	Camera3DDef def;
	ElementEvent elementEvent;
	ChildsHolder childsHolder = new ChildsHolder(this);
	ItemScript itemScript;

	public final PerspectiveCamera persp = new PerspectiveCamera(60, 16, 9);
	public final OrthographicCamera ortho = new OrthographicCamera();
	public Camera cam = persp;
	/** pitch (x), yaw (y), roll (z) in degrees. */
	public float pitch, yaw, roll;
	public final Vector3 position = new Vector3();
	public String followMode = "NONE";
	public PlayerItem target;
	public float distance = 6, height = 2.5f, smooth = 6, lookHeight = 1, angle;
	public boolean touchLook;
	public float sensitivity = 0.25f;
	public float shake;
	private float shakeTime;
	// tweens
	private final Vector3 moveFrom = new Vector3(), moveTo = new Vector3();
	private float moveT = -1, moveDur;
	private float turnFromP, turnFromY, turnFromR, turnToP, turnToY, turnToR, turnT = -1, turnDur;
	private float fovFrom, fovTo, fovT = -1, fovDur;

	public Camera3D(StageImp s) {
		stage = s;
	}

	public static Camera3D create(StageImp stage, Camera3DDef def, ElementEvent ev) {
		Camera3D c = new Camera3D(stage);
		c.def = def;
		c.elementEvent = ev;
		c.world = stage == null ? null : stage.world3d();
		c.setup();
		return c;
	}

	public static Camera3D forWorld(World3D w, Camera3DDef def) {
		Camera3D c = new Camera3D(null);
		c.def = def;
		c.world = w;
		c.setup();
		return c;
	}

	private void setup() {
		setName(def.name);
		setSize(0, 0);
		apply();
		if (world != null) world.addCamera(this);
		if (stage != null) stage.addActor(this);
		if (stage != null && stage.onCreateCalled()) {
			if (elementEvent != null) elementEvent.onBodyCreated(this);
			if (getScript() != null) getScript().bodyCreated();
		}
	}

	public void apply() {
		position.set(def.x, def.y, def.z);
		pitch = def.Rotation_X;
		yaw = def.Rotation_Y;
		roll = def.Rotation_Z;
		persp.fieldOfView = def.FOV;
		persp.near = def.Near;
		persp.far = def.Far;
		ortho.near = def.Near;
		ortho.far = def.Far;
		cam = def.Orthographic ? ortho : persp;
		followMode = def.Follow_Mode == null ? "NONE" : def.Follow_Mode.toUpperCase();
		distance = def.Follow_Distance;
		height = def.Follow_Height;
		smooth = def.Follow_Smooth;
		lookHeight = def.Look_Height;
		angle = def.Follow_Angle;
		touchLook = def.Touch_Look;
		sensitivity = def.Look_Sensitivity;
		shake = def.Shake;
		target = null;
		setVisible(def.Visible);
	}

	/** Viewport size in pixels (aspect). */
	public void resize(float w, float h) {
		persp.viewportWidth = w;
		persp.viewportHeight = h;
		float size = def == null ? 10 : def.Ortho_Size;
		ortho.viewportWidth = size * w / Math.max(1, h);
		ortho.viewportHeight = size;
	}

	private final Vector3 tmp = new Vector3(), want = new Vector3(), look = new Vector3();

	/** Every frame: follow, tweens, shake, then the libGDX camera. */
	public void frame(float dt) {
		if (target == null && def.Follow != null && def.Follow.length() > 0 && stage != null) target = stage.findItem(def.Follow);
		if (moveT >= 0) {
			moveT += dt;
			float a = Interpolation.smooth.apply(Math.min(1f, moveT / Math.max(0.0001f, moveDur)));
			position.set(moveFrom).lerp(moveTo, a);
			if (moveT >= moveDur) moveT = -1;
		}
		if (turnT >= 0) {
			turnT += dt;
			float a = Interpolation.smooth.apply(Math.min(1f, turnT / Math.max(0.0001f, turnDur)));
			pitch = MathUtils.lerp(turnFromP, turnToP, a);
			yaw = lerpAngle(turnFromY, turnToY, a);
			roll = MathUtils.lerp(turnFromR, turnToR, a);
			if (turnT >= turnDur) turnT = -1;
		}
		if (fovT >= 0) {
			fovT += dt;
			float a = Interpolation.smooth.apply(Math.min(1f, fovT / Math.max(0.0001f, fovDur)));
			persp.fieldOfView = MathUtils.lerp(fovFrom, fovTo, a);
			if (fovT >= fovDur) fovT = -1;
		}
		Object3D t = target instanceof Object3D ? (Object3D) target : null;
		float k = smooth <= 0 ? 1f : Math.min(1f, smooth * dt);
		boolean lookAtTarget = false;
		if (t != null && moveT < 0) {
			Vector3 tp = t.position;
			if ("THIRD_PERSON".equals(followMode)) {
				float ty = t.euler.y + angle;
				t.forward(tmp);
				float tyaw = MathUtils.atan2(-tmp.x, -tmp.z) * MathUtils.radiansToDegrees + angle;
				want.set(tp.x + MathUtils.sinDeg(tyaw) * distance, tp.y + height, tp.z + MathUtils.cosDeg(tyaw) * distance);
				position.lerp(want, k);
				lookAtTarget = true;
			} else if ("FIRST_PERSON".equals(followMode)) {
				position.set(tp.x, tp.y + height, tp.z);
				if (!touchLook) yaw = t.euler.y;
			} else if ("ORBIT".equals(followMode)) {
				float p = MathUtils.clamp(pitch, -85, 85);
				want.set(tp.x + MathUtils.sinDeg(yaw) * MathUtils.cosDeg(-p) * distance, tp.y + lookHeight + MathUtils.sinDeg(-p) * distance, tp.z + MathUtils.cosDeg(yaw) * MathUtils.cosDeg(-p) * distance);
				position.lerp(want, k);
				lookAtTarget = true;
			} else if ("TOP_DOWN".equals(followMode)) {
				want.set(tp.x, tp.y + height + distance, tp.z + distance * 0.35f);
				position.lerp(want, k);
				lookAtTarget = true;
			} else if ("SIDE".equals(followMode)) {
				want.set(tp.x, tp.y + height, tp.z + distance);
				position.lerp(want, k);
				lookAtTarget = true;
			} else if ("LOOK_AT".equals(followMode)) {
				lookAtTarget = true;
			}
		}
		cam.position.set(position);
		// a following camera never goes under a terrain (hills behind the player, a slope while orbiting)
		if (t != null && world != null && !"FIRST_PERSON".equals(followMode)) {
			for (int i = 0; i < world.objects.size; i++) {
				Object3D g = world.objects.get(i);
				if (g.terrain() == null || !g.isVisible()) continue;
				if (Math.abs(cam.position.x - g.position.x) > g.getSizeX() * 0.5f || Math.abs(cam.position.z - g.position.z) > g.getSizeZ() * 0.5f) continue;
				float min = g.heightAt(cam.position.x, cam.position.z) + 0.45f;
				if (cam.position.y < min) {
					cam.position.y = min;
					position.y = min;
				}
			}
		}
		if (lookAtTarget && t != null) {
			look.set(t.position.x, t.position.y + lookHeight, t.position.z).sub(cam.position);
			// camera on the look point: keep the last direction (a zero direction makes a NaN view)
			if (!look.isZero(0.0001f)) cam.direction.set(look).nor();
			// straight down / up: Y can't be the up vector
			if (Math.abs(cam.direction.y) > 0.999f) cam.up.set(0, 0, cam.direction.y > 0 ? 1 : -1);
			else cam.up.set(Vector3.Y);
			if (!"ORBIT".equals(followMode)) {
				// keep the angles in step so a later free camera continues from here
				pitch = MathUtils.asin(MathUtils.clamp(cam.direction.y, -1, 1)) * MathUtils.radiansToDegrees;
				yaw = MathUtils.atan2(-cam.direction.x, -cam.direction.z) * MathUtils.radiansToDegrees;
			}
		} else {
			Quaternion q = new Quaternion().setEulerAngles(yaw, pitch, roll);
			cam.direction.set(0, 0, -1).mul(q);
			cam.up.set(0, 1, 0).mul(q);
		}
		if (shake > 0) {
			shakeTime += dt;
			float s = shake * 0.08f;
			cam.position.add(MathUtils.random(-s, s), MathUtils.random(-s, s), MathUtils.random(-s, s));
			shake = Math.max(0, shake - dt * 2.5f);
		}
		cam.update();
	}

	private static float lerpAngle(float a, float b, float t) {
		float d = ((b - a) % 360 + 540) % 360 - 180;
		return a + d * t;
	}

	/** Touch drag (pixels) from the world: orbit / first person look. */
	void drag(float dx, float dy) {
		if (!touchLook) return;
		yaw -= dx * sensitivity;
		pitch = MathUtils.clamp(pitch - dy * sensitivity, -85, 85);
	}

	void pinch(float amount) {
		if ("ORBIT".equals(followMode) || "THIRD_PERSON".equals(followMode)) distance = MathUtils.clamp(distance * amount, 1.5f, 60f);
	}

	// ------------------------------------------------------------------ API

	public void setActive() {
		if (world != null) world.setActiveCamera(this);
	}

	public void setPosition3(float x, float y, float z) {
		position.set(x, y, z);
		moveT = -1;
	}

	public float getX3() {
		return position.x;
	}

	public float getY3() {
		return position.y;
	}

	public float getZ3() {
		return position.z;
	}

	public void setRotation3(float x, float y, float z) {
		pitch = x;
		yaw = y;
		roll = z;
		turnT = -1;
	}

	public float getPitch() {
		return pitch;
	}

	public float getYaw() {
		return yaw;
	}

	/** Smooth move to a point in the given seconds. */
	public void moveTo(float x, float y, float z, float seconds) {
		moveFrom.set(position);
		moveTo.set(x, y, z);
		moveDur = Math.max(0.0001f, seconds);
		moveT = 0;
	}

	/** Smooth turn to angles (degrees) in the given seconds. */
	public void turnTo(float pitchDeg, float yawDeg, float rollDeg, float seconds) {
		turnFromP = pitch;
		turnFromY = yaw;
		turnFromR = roll;
		turnToP = pitchDeg;
		turnToY = yawDeg;
		turnToR = rollDeg;
		turnDur = Math.max(0.0001f, seconds);
		turnT = 0;
	}

	public void zoomTo(float fov, float seconds) {
		fovFrom = persp.fieldOfView;
		fovTo = fov;
		fovDur = Math.max(0.0001f, seconds);
		fovT = 0;
	}

	public boolean isMoving() {
		return moveT >= 0 || turnT >= 0;
	}

	public void setFov(float fov) {
		persp.fieldOfView = fov;
	}

	public float getFov() {
		return persp.fieldOfView;
	}

	public void follow(PlayerItem item, String mode) {
		target = item;
		followMode = mode == null ? "THIRD_PERSON" : mode.toUpperCase();
	}

	public void stopFollow() {
		target = null;
		followMode = "NONE";
	}

	public void setFollowDistance(float d, float h) {
		distance = d;
		height = h;
	}

	public void setTouchLook(boolean on) {
		touchLook = on;
	}

	public void shake(float strength) {
		shake = Math.max(shake, strength);
	}

	public void lookAt(float x, float y, float z) {
		Vector3 d = new Vector3(x, y, z).sub(position).nor();
		pitch = MathUtils.asin(MathUtils.clamp(d.y, -1, 1)) * MathUtils.radiansToDegrees;
		yaw = MathUtils.atan2(-d.x, -d.z) * MathUtils.radiansToDegrees;
		roll = 0;
	}

	public void lookAtItem(PlayerItem item) {
		if (item instanceof Object3D) {
			Vector3 p = ((Object3D) item).position;
			lookAt(p.x, p.y + lookHeight, p.z);
		}
	}

	public void moveForward(float d) {
		Quaternion q = new Quaternion().setEulerAngles(yaw, pitch, roll);
		Vector3 f = new Vector3(0, 0, -1).mul(q).scl(d);
		position.add(f);
	}

	public void moveRight(float d) {
		Quaternion q = new Quaternion().setEulerAngles(yaw, 0, 0);
		Vector3 r = new Vector3(1, 0, 0).mul(q).scl(d);
		position.add(r);
	}

	// ------------------------------------------------------------------ PlayerItem

	@Override
	public void update() {
		if (getScript() != null) getScript().bodyUpdate();
		else if (elementEvent != null) elementEvent.onBodyUpdate(this);
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		if (!StageImp.secondaryPass) update();
	}

	@Override
	public Body getBody() {
		return null;
	}

	@Override
	public ChildsHolder getChildsHolder() {
		if (childsHolder == null) childsHolder = new ChildsHolder(this);
		return childsHolder;
	}

	@Override
	public void setScript(ItemScript script) {
		itemScript = script;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends ItemScript> T getScript() {
		return (T) itemScript;
	}

	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties() {
		return def;
	}

	public ElementEvent getElementEvents() {
		return elementEvent;
	}

	@Override
	public PlayerItem getClone(String newName) {
		return create(stage, def.getClone(newName), elementEvent);
	}

	@Override
	public float getActorX() {
		return position.x;
	}

	@Override
	public float getActorY() {
		return position.y;
	}

	public Camera3DDef getDef() {
		return def;
	}
}
