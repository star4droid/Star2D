package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ObjectMap;
import com.star4droid.template.Utils.PlayerItem;

/**
 * Static helpers used by the 3D blocks (and handy in scripts). Everything takes the element (PlayerItem) and does
 * nothing when it is not a 3D element, so a block never crashes the game. Java 7 code only calls these.
 */
public final class B3 {
	private B3() {
	}

	public static World3D world() {
		return World3D.current;
	}

	public static Object3D o(Object item) {
		return item instanceof Object3D ? (Object3D) item : null;
	}

	public static Light3D l(Object item) {
		return item instanceof Light3D ? (Light3D) item : null;
	}

	public static Camera3D c(Object item) {
		if (item instanceof Camera3D) return (Camera3D) item;
		return null;
	}

	/** The main camera of the scene (null in 2D). */
	public static Camera3D cam() {
		World3D w = world();
		return w == null ? null : w.activeCamera;
	}

	// ---------------------------------------------------------------- transform

	public static void setPosition(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.setPosition3(x, y, z);
		Light3D l = l(item);
		if (l != null) l.setPosition3(x, y, z);
		Camera3D c = c(item);
		if (c != null) c.setPosition3(x, y, z);
	}

	public static void move(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.move(x, y, z);
		Camera3D c = c(item);
		if (c != null) c.setPosition3(c.position.x + x, c.position.y + y, c.position.z + z);
		Light3D l = l(item);
		if (l != null) l.setPosition3(l.position.x + x, l.position.y + y, l.position.z + z);
	}

	public static void moveForward(Object item, float meters) {
		Object3D o = o(item);
		if (o != null) o.moveLocal(meters, 0, 0);
		Camera3D c = c(item);
		if (c != null) c.moveForward(meters);
	}

	public static void moveLocal(Object item, float forward, float right, float up) {
		Object3D o = o(item);
		if (o != null) o.moveLocal(forward, right, up);
	}

	public static float x(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getX3();
		Camera3D c = c(item);
		if (c != null) return c.position.x;
		Light3D l = l(item);
		return l != null ? l.position.x : 0;
	}

	public static float y(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getY3();
		Camera3D c = c(item);
		if (c != null) return c.position.y;
		Light3D l = l(item);
		return l != null ? l.position.y : 0;
	}

	public static float z(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getZ3();
		Camera3D c = c(item);
		if (c != null) return c.position.z;
		Light3D l = l(item);
		return l != null ? l.position.z : 0;
	}

	public static void setRotation(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.setRotation3(x, y, z);
		Camera3D c = c(item);
		if (c != null) c.setRotation3(x, y, z);
		Light3D l = l(item);
		if (l != null) l.setRotation3(x, y, z);
	}

	public static void rotate(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.rotate3(x, y, z);
		Camera3D c = c(item);
		if (c != null) c.setRotation3(c.pitch + x, c.yaw + y, c.roll + z);
	}

	public static void turnLocal(Object item, float pitch, float yaw, float roll) {
		Object3D o = o(item);
		if (o != null) o.turnLocal(pitch, yaw, roll);
	}

	public static float rotX(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getRotationX3();
		Camera3D c = c(item);
		return c != null ? c.pitch : 0;
	}

	public static float rotY(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getRotationY3();
		Camera3D c = c(item);
		return c != null ? c.yaw : 0;
	}

	public static float rotZ(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getRotationZ3();
		Camera3D c = c(item);
		return c != null ? c.roll : 0;
	}

	public static void lookAt(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.lookAt(x, y, z);
		Camera3D c = c(item);
		if (c != null) c.lookAt(x, y, z);
		Light3D l = l(item);
		if (l != null) l.pointAt(x, y, z);
	}

	public static void lookAtItem(Object item, Object target) {
		lookAt(item, x(target), y(target), z(target));
	}

	public static void face(Object item, Object target) {
		Object3D o = o(item);
		if (o != null) o.faceTowards(x(target), z(target));
	}

	public static void setScale(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.setScale3(x, y, z);
	}

	public static float scaleX(Object item) {
		Object3D o = o(item);
		return o == null ? 1 : o.getScaleX3();
	}

	public static float scaleY(Object item) {
		Object3D o = o(item);
		return o == null ? 1 : o.getScaleY3();
	}

	public static float scaleZ(Object item) {
		Object3D o = o(item);
		return o == null ? 1 : o.getScaleZ3();
	}

	public static float distance(Object a, Object b) {
		return Vector3.dst(x(a), y(a), z(a), x(b), y(b), z(b));
	}

	public static float distanceToPoint(Object a, float x, float y, float z) {
		return Vector3.dst(x(a), y(a), z(a), x, y, z);
	}

	public static float forwardX(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getForwardX();
		Camera3D c = c(item);
		return c != null ? c.cam.direction.x : 0;
	}

	public static float forwardY(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getForwardY();
		Camera3D c = c(item);
		return c != null ? c.cam.direction.y : 0;
	}

	public static float forwardZ(Object item) {
		Object3D o = o(item);
		if (o != null) return o.getForwardZ();
		Camera3D c = c(item);
		return c != null ? c.cam.direction.z : -1;
	}

	/** Angle (degrees, around Y) from an element to another: 0 = straight ahead. */
	public static float angleTo(Object item, Object target) {
		Object3D o = o(item);
		if (o == null) return 0;
		Vector3 f = o.forward(new Vector3());
		float a1 = MathUtils.atan2(f.x, -f.z), a2 = MathUtils.atan2(x(target) - o.position.x, -(z(target) - o.position.z));
		float d = (a2 - a1) * MathUtils.radiansToDegrees;
		while (d > 180) d -= 360;
		while (d < -180) d += 360;
		return d;
	}

	public static void attach(Object child, Object parent) {
		Object3D o = o(child), p = o(parent);
		if (o != null) o.attachTo(p);
		Light3D l = l(child);
		if (l != null && parent instanceof PlayerItem) l.attachTo((PlayerItem) parent);
	}

	public static void detach(Object child) {
		Object3D o = o(child);
		if (o != null) o.detach3D();
		Light3D l = l(child);
		if (l != null) l.attachTo(null);
	}

	// ---------------------------------------------------------------- tweens

	public static void moveTo(Object item, float x, float y, float z, float seconds) {
		Camera3D c = c(item);
		if (c != null) {
			c.moveTo(x, y, z, seconds);
			return;
		}
		Tween3D.move(o(item), x, y, z, seconds);
	}

	public static void rotateTo(Object item, float x, float y, float z, float seconds) {
		Camera3D c = c(item);
		if (c != null) {
			c.turnTo(x, y, z, seconds);
			return;
		}
		Tween3D.rotate(o(item), x, y, z, seconds);
	}

	public static void scaleTo(Object item, float x, float y, float z, float seconds) {
		Tween3D.scale(o(item), x, y, z, seconds);
	}

	public static boolean isMoving(Object item) {
		Camera3D c = c(item);
		if (c != null) return c.isMoving();
		return Tween3D.busy(o(item));
	}

	// ---------------------------------------------------------------- look

	public static void setColor(Object item, String hex) {
		Object3D o = o(item);
		if (o != null) o.setColor(hex);
		Light3D l = l(item);
		if (l != null) l.setLightColor(hex);
	}

	public static void setTexture(Object item, String image) {
		Object3D o = o(item);
		if (o != null) o.setTexture(image);
	}

	public static void setOpacity(Object item, float a) {
		Object3D o = o(item);
		if (o != null) o.setOpacity(a);
	}

	public static void setEmissive(Object item, String hex, float intensity) {
		Object3D o = o(item);
		if (o != null) o.setEmissive(hex, intensity);
	}

	public static void setVisible(Object item, boolean v) {
		if (item instanceof com.badlogic.gdx.scenes.scene2d.Actor) ((com.badlogic.gdx.scenes.scene2d.Actor) item).setVisible(v);
	}

	public static void setOutline(Object item, boolean on, String hex, float width) {
		Object3D o = o(item);
		if (o != null) o.setOutline(on, hex, width);
	}

	public static void setShape(Object item, String shape) {
		Object3D o = o(item);
		if (o != null) o.setShape(shape);
	}

	public static void setModel(Object item, String path) {
		Object3D o = o(item);
		if (o != null) o.setModel(path);
	}

	public static void setShader(Object item, String name, String params) {
		Object3D o = o(item);
		if (o != null) o.setShader(name, params);
	}

	public static void setShaderCode(Object item, String code) {
		Object3D o = o(item);
		if (o != null) o.setShaderCode(code);
	}

	public static void setShaderParam(Object item, String name, String value) {
		Object3D o = o(item);
		if (o != null) o.setShaderParam(name, value);
	}

	public static void setShaderFloat(Object item, String name, float value) {
		Object3D o = o(item);
		if (o != null) o.setShaderParamFloat(name, value);
	}

	public static void setNodeVisible(Object item, String node, boolean v) {
		Object3D o = o(item);
		if (o != null) o.setNodeVisible(node, v);
	}

	public static void rotateNode(Object item, String node, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.rotateNode(node, x, y, z);
	}

	public static void setNodeRotation(Object item, String node, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.setNodeRotation(node, x, y, z);
	}

	// ---------------------------------------------------------------- animation

	public static void playAnimation(Object item, String name, boolean loop, float speed) {
		Object3D o = o(item);
		if (o != null) o.playAnimation(name, loop, speed);
	}

	public static void crossFade(Object item, String name, boolean loop, float speed, float seconds) {
		Object3D o = o(item);
		if (o != null) o.crossFadeAnimation(name, loop, speed, seconds);
	}

	public static void playOnce(Object item, String name, float speed) {
		Object3D o = o(item);
		if (o != null) o.playAnimationOnce(name, speed);
	}

	public static void stopAnimation(Object item) {
		Object3D o = o(item);
		if (o != null) o.stopAnimation();
	}

	public static void pauseAnimation(Object item) {
		Object3D o = o(item);
		if (o != null) o.pauseAnimation();
	}

	public static void resumeAnimation(Object item) {
		Object3D o = o(item);
		if (o != null) o.resumeAnimation();
	}

	public static void setAnimationSpeed(Object item, float s) {
		Object3D o = o(item);
		if (o != null) o.setAnimationSpeed(s);
	}

	public static boolean isAnimationPlaying(Object item) {
		Object3D o = o(item);
		return o != null && o.isAnimationPlaying();
	}

	public static String animation(Object item) {
		Object3D o = o(item);
		return o == null ? "" : o.getAnimation();
	}

	public static String animationNames(Object item) {
		Object3D o = o(item);
		return o == null ? "" : o.getAnimationNames();
	}

	// ---------------------------------------------------------------- physics

	public static void applyForce(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.applyForce(x, y, z);
	}

	public static void applyImpulse(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.applyImpulse(x, y, z);
	}

	public static void applyLocalForce(Object item, float forward, float right, float up) {
		Object3D o = o(item);
		if (o != null) o.applyLocalForce(forward, right, up);
	}

	public static void applyTorque(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.applyTorque(x, y, z);
	}

	public static void setVelocity(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.setVelocity3(x, y, z);
	}

	public static void setVelocityKeepY(Object item, float x, float z) {
		Object3D o = o(item);
		if (o != null) o.setVelocityKeepY(x, z);
	}

	public static float velX(Object item) {
		Object3D o = o(item);
		return o == null ? 0 : o.getVelocityX3();
	}

	public static float velY(Object item) {
		Object3D o = o(item);
		return o == null ? 0 : o.getVelocityY3();
	}

	public static float velZ(Object item) {
		Object3D o = o(item);
		return o == null ? 0 : o.getVelocityZ3();
	}

	public static float speed(Object item) {
		Object3D o = o(item);
		return o == null ? 0 : o.getSpeed3();
	}

	public static void setAngularVelocity(Object item, float x, float y, float z) {
		Object3D o = o(item);
		if (o != null) o.setAngularVelocity3(x, y, z);
	}

	public static void setBodyType(Object item, String type) {
		Object3D o = o(item);
		if (o != null) o.setBodyType(type);
	}

	public static void setMass(Object item, float m) {
		Object3D o = o(item);
		if (o != null) o.setMass(m);
	}

	public static void setGravityScale(Object item, float g) {
		Object3D o = o(item);
		if (o != null) o.setGravityScale3(g);
	}

	public static void setFriction(Object item, float f) {
		Object3D o = o(item);
		if (o != null) o.setFriction3(f);
	}

	public static void setBounce(Object item, float b) {
		Object3D o = o(item);
		if (o != null) o.setBounce3(b);
	}

	public static void setPhysics(Object item, boolean on) {
		Object3D o = o(item);
		if (o != null) o.setPhysicsEnabled(on);
	}

	public static boolean isGrounded(Object item) {
		Object3D o = o(item);
		return o != null && o.isGrounded(0.08f);
	}

	public static boolean touching(Object a, Object b) {
		Object3D o = o(a);
		return o != null && b instanceof PlayerItem && o.isTouching((PlayerItem) b);
	}

	public static void setGravity(float x, float y, float z) {
		World3D w = world();
		if (w != null) w.setGravity(x, y, z);
	}

	/** Ray from a point: true when it hits; then rayHit(), rayHitX()... */
	public static boolean raycast(float x, float y, float z, float dx, float dy, float dz, float dist) {
		World3D w = world();
		return w != null && w.physics().raycast(x, y, z, dx, dy, dz, dist, null);
	}

	/** Ray from an element's center along its front. */
	public static boolean raycastForward(Object item, float dist) {
		Object3D o = o(item);
		World3D w = world();
		if (o == null || w == null) return false;
		Vector3 c = o.center(new Vector3()), f = o.forward(new Vector3());
		return w.physics().raycast(c.x, c.y, c.z, f.x, f.y, f.z, dist, o);
	}

	/** Ray from the screen point (pixels, y down) through the main camera: true when an element is hit. */
	public static boolean raycastScreen(float sx, float sy) {
		World3D w = world();
		if (w == null) return false;
		Object3D o = w.pick(sx, sy);
		lastPick = o;
		if (o != null) {
			if (w.hasPhysics()) w.physics().hitObject = o;
			pickPoint.set(w.pickPoint);
		}
		return o != null;
	}

	static Object3D lastPick;
	static final Vector3 pickPoint = new Vector3();

	public static PlayerItem rayHit() {
		World3D w = world();
		if (w == null || !w.hasPhysics()) return lastPick;
		return w.physics().hitObject != null ? w.physics().hitObject : lastPick;
	}

	public static float rayHitX() {
		World3D w = world();
		return w != null && w.hasPhysics() ? w.physics().hitPoint.x : pickPoint.x;
	}

	public static float rayHitY() {
		World3D w = world();
		return w != null && w.hasPhysics() ? w.physics().hitPoint.y : pickPoint.y;
	}

	public static float rayHitZ() {
		World3D w = world();
		return w != null && w.hasPhysics() ? w.physics().hitPoint.z : pickPoint.z;
	}

	public static float rayHitDistance() {
		World3D w = world();
		return w != null && w.hasPhysics() ? w.physics().hitDistance : 0;
	}

	/** Element touched on the screen now (pixels of the touch), null = none. */
	public static PlayerItem elementAtScreen(float sx, float sy) {
		World3D w = world();
		return w == null ? null : w.pick(sx, sy);
	}

	// ---------------------------------------------------------------- terrain / water

	public static float groundHeight(Object terrain, float x, float z) {
		Object3D o = o(terrain);
		return o == null ? 0 : o.heightAt(x, z);
	}

	public static void setWaves(Object water, float height, float speed, float length) {
		Object3D o = o(water);
		if (o != null) o.setWaves(height, speed, length);
	}

	public static void setWaterColors(Object water, String shallow, String deep) {
		Object3D o = o(water);
		if (o != null) o.setWaterColors(shallow, deep);
	}

	// ---------------------------------------------------------------- lights

	public static void setLightOn(Object item, boolean on) {
		Light3D l = l(item);
		if (l != null) l.setOn(on);
	}

	public static void toggleLight(Object item) {
		Light3D l = l(item);
		if (l != null) l.toggle();
	}

	public static void setLightIntensity(Object item, float v) {
		Light3D l = l(item);
		if (l != null) l.setIntensity(v);
	}

	public static void setLightRange(Object item, float v) {
		Light3D l = l(item);
		if (l != null) l.setRange(v);
	}

	public static void setFlicker(Object item, float amount, float speed) {
		Light3D l = l(item);
		if (l != null) l.setFlicker(amount, speed);
	}

	public static void setAmbient(String hex, float intensity) {
		World3D w = world();
		if (w == null) return;
		w.ambient.set(Materials3D.color(hex, w.ambient));
		w.ambientIntensity = intensity;
	}

	public static void setSun(boolean on, String hex, float intensity) {
		World3D w = world();
		if (w == null) return;
		w.sunEnabled = on;
		w.sunColor.set(Materials3D.color(hex, w.sunColor));
		w.sunIntensity = intensity;
	}

	public static void setSunAngle(float pitch, float yaw) {
		World3D w = world();
		if (w != null) w.setSunAngles(pitch, yaw);
	}

	public static void setLightingPreset(String preset) {
		World3D w = world();
		if (w != null) w.applyPreset(preset);
	}

	public static void setFog(boolean on, String hex, float near, float far) {
		World3D w = world();
		if (w == null) return;
		w.fogEnabled = on;
		w.fogColor.set(Materials3D.color(hex, w.fogColor));
		w.fogNear = near;
		w.fogFar = far;
	}

	public static void setSky(String top, String bottom) {
		World3D w = world();
		if (w == null) return;
		w.skyMode = "GRADIENT";
		w.skyTop.set(Materials3D.color(top, w.skyTop));
		w.skyBottom.set(Materials3D.color(bottom, w.skyBottom));
	}

	public static void setSkyImage(String image) {
		World3D w = world();
		if (w == null) return;
		w.skyMode = "IMAGE";
		w.skyImage = image;
	}

	public static void setRenderMode(String mode) {
		World3D w = world();
		if (w != null) w.renderMode = mode == null ? "SIMPLE" : mode.toUpperCase();
	}

	public static void setShadows(boolean on) {
		World3D w = world();
		if (w != null) w.sunShadows = on;
	}

	public static void setPostEffect(String effect, float amount) {
		World3D w = world();
		if (w == null) return;
		w.postEffect = effect == null ? "NONE" : effect.toUpperCase();
		PostFx3D.amount = amount;
	}

	public static void setPostCode(String code) {
		World3D w = world();
		if (w != null) w.postEffect = "CUSTOM:" + code;
	}

	// ---------------------------------------------------------------- quality

	public static void setQuality(String level) {
		Quality3D.setLevel(level);
		Quality3D.save();
	}

	public static String quality() {
		return Quality3D.level;
	}

	public static void setRenderScale(float s) {
		Quality3D.renderScale = s;
		Quality3D.custom();
		Quality3D.save();
	}

	public static void setViewDistance(float d) {
		Quality3D.viewDistance = d;
		Quality3D.custom();
		Quality3D.save();
	}

	public static void setShadowQuality(int size) {
		Quality3D.shadowSize = size;
		Quality3D.custom();
		Quality3D.save();
	}

	public static void setFpsLimit(int fps) {
		Quality3D.fpsLimit = fps;
		Quality3D.custom();
		Quality3D.save();
	}

	public static void setWaterQuality(int level) {
		Quality3D.waterLevel = level;
		Quality3D.custom();
		Quality3D.save();
	}

	public static int fps() {
		return Gdx.graphics.getFramesPerSecond();
	}

	// ---------------------------------------------------------------- camera

	public static void setMainCamera(Object item) {
		Camera3D c = c(item);
		if (c != null) c.setActive();
	}

	public static void follow(Object camera, Object target, String mode) {
		Camera3D c = c(camera);
		if (c == null) c = cam();
		if (c != null && target instanceof PlayerItem) c.follow((PlayerItem) target, mode);
	}

	public static void stopFollow(Object camera) {
		Camera3D c = c(camera);
		if (c == null) c = cam();
		if (c != null) c.stopFollow();
	}

	public static void setFollowDistance(Object camera, float distance, float height) {
		Camera3D c = c(camera);
		if (c == null) c = cam();
		if (c != null) c.setFollowDistance(distance, height);
	}

	public static void setFov(Object camera, float fov) {
		Camera3D c = c(camera);
		if (c == null) c = cam();
		if (c != null) c.setFov(fov);
	}

	public static void zoomTo(Object camera, float fov, float seconds) {
		Camera3D c = c(camera);
		if (c == null) c = cam();
		if (c != null) c.zoomTo(fov, seconds);
	}

	public static void shake(Object camera, float strength) {
		Camera3D c = c(camera);
		if (c == null) c = cam();
		if (c != null) c.shake(strength);
	}

	public static void setTouchLook(Object camera, boolean on) {
		Camera3D c = c(camera);
		if (c == null) c = cam();
		if (c != null) c.setTouchLook(on);
	}

	/** Screen x (pixels from the left) of an element, -1 when behind the camera. */
	public static float screenX(Object item) {
		World3D w = world();
		if (w == null) return -1;
		Vector3 v = w.toScreen(x(item), y(item), z(item));
		return v.z > 1 || v.z < 0 ? -1 : v.x;
	}

	/** Screen y (pixels from the bottom). */
	public static float screenY(Object item) {
		World3D w = world();
		if (w == null) return -1;
		Vector3 v = w.toScreen(x(item), y(item), z(item));
		return v.z > 1 || v.z < 0 ? -1 : v.y;
	}

	public static boolean isOnScreen(Object item) {
		World3D w = world();
		Object3D o = o(item);
		if (w == null || o == null) return false;
		return w.camera.frustum.sphereInFrustum(o.center(new Vector3()), o.radius);
	}

	// ---------------------------------------------------------------- objects

	/** Copy of an element at a point (name = new name). */
	public static PlayerItem spawn(Object item, String newName, float x, float y, float z) {
		Object3D o = o(item);
		if (o == null) return null;
		Object3D c = (Object3D) o.getClone(newName);
		c.setPosition3(x, y, z);
		c.setVisible(true);
		return c;
	}

	public static void destroy(Object item) {
		Object3D o = o(item);
		if (o != null) o.destroy();
	}

	public static float sizeX(Object item) {
		Object3D o = o(item);
		return o == null ? 0 : o.getSizeX();
	}

	public static float sizeY(Object item) {
		Object3D o = o(item);
		return o == null ? 0 : o.getSizeY();
	}

	public static float sizeZ(Object item) {
		Object3D o = o(item);
		return o == null ? 0 : o.getSizeZ();
	}

	// ---------------------------------------------------------------- sound in space

	/** Plays a sound of the project (sounds folder) with a volume that fades with the distance to the camera. */
	public static void playSoundAt(String sound, Object item, float maxDistance) {
		World3D w = world();
		float d = w == null ? 0 : w.camera.position.dst(x(item), y(item), z(item));
		float vol = MathUtils.clamp(1f - d / Math.max(0.1f, maxDistance), 0f, 1f);
		if (vol <= 0.01f) return;
		Sound s = sound(sound);
		if (s == null) return;
		float pan = 0;
		if (w != null) {
			Vector3 right = new Vector3(w.camera.direction).crs(w.camera.up).nor();
			Vector3 to = new Vector3(x(item), y(item), z(item)).sub(w.camera.position).nor();
			pan = MathUtils.clamp(right.dot(to), -1, 1);
		}
		s.play(vol, 1f, pan);
	}

	public static void playSound(String sound, float volume) {
		Sound s = sound(sound);
		if (s != null) s.play(volume);
	}

	/** New audio / GL context (see Assets3D.checkContext). */
	static void forgetSounds() {
		Sounds3D.forget();
	}

	/** Cached in {@link Sounds3D}: stopped and freed when the 3D scene ends, paused with it. */
	static Sound sound(String path) {
		if (Sounds3D.isPaused()) return null;
		return Sounds3D.get(path);
	}

	// ---------------------------------------------------------------- inventory

	public static void addItem(String id, int count) {
		Inventory.add(id, count);
	}

	public static boolean removeItem(String id, int count) {
		return Inventory.remove(id, count);
	}

	public static boolean hasItem(String id) {
		return Inventory.count(id) > 0;
	}

	public static int itemCount(String id) {
		return Inventory.count(id);
	}
}
