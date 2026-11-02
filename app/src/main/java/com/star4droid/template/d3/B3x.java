package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;

/**
 * More static helpers for the 3D blocks (next to {@link B3}). Like B3, everything takes the element as Object and does
 * nothing (or returns 0 / "" / false) when it is not a 3D element or there is no 3D world, so a block never crashes the
 * game. Java 7 only; no Bullet / glTF types in the signatures.
 */
public final class B3x {
	private B3x() {
	}

	private static float dt() {
		return Gdx.graphics == null ? 1 / 60f : Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
	}

	// ---------------------------------------------------------------- motion

	/** Sideways by its own right (negative = left). */
	public static void moveRight(Object item, float meters) {
		Object3D o = B3.o(item);
		if (o != null) o.moveLocal(0, meters, 0);
		Camera3D c = B3.c(item);
		if (c != null) c.moveRight(meters);
	}

	/** Up along its own up direction (negative = down). */
	public static void moveUp(Object item, float meters) {
		Object3D o = B3.o(item);
		if (o != null) {
			o.moveLocal(0, 0, meters);
			return;
		}
		B3.move(item, 0, meters, 0);
	}

	public static void setX(Object item, float v) {
		B3.setPosition(item, v, B3.y(item), B3.z(item));
	}

	public static void setY(Object item, float v) {
		B3.setPosition(item, B3.x(item), v, B3.z(item));
	}

	public static void setZ(Object item, float v) {
		B3.setPosition(item, B3.x(item), B3.y(item), v);
	}

	/** Moves toward a point at a speed (meters per second); call it every frame (OnStep). Stops on the point. */
	public static void moveTowardPoint(Object item, float x, float y, float z, float speed) {
		float dx = x - B3.x(item), dy = y - B3.y(item), dz = z - B3.z(item);
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float step = speed * dt();
		if (d <= step || d < 0.0001f) {
			B3.setPosition(item, x, y, z);
			return;
		}
		B3.move(item, dx / d * step, dy / d * step, dz / d * step);
	}

	/** Moves toward another element at a speed (meters per second); call it every frame (OnStep). */
	public static void moveToward(Object item, Object target, float speed) {
		if (target == null) return;
		moveTowardPoint(item, B3.x(target), B3.y(target), B3.z(target), speed);
	}

	/** Turns around Y toward an element, at most {@code degreesPerSecond}; call it every frame. */
	public static void turnToward(Object item, Object target, float degreesPerSecond) {
		Object3D o = B3.o(item);
		if (o == null || target == null) return;
		float a = B3.angleTo(item, target);
		float max = degreesPerSecond * dt();
		float s = Math.max(-max, Math.min(max, a));
		// angleTo is positive to the right; turning right is a negative yaw
		o.turnLocal(0, -s, 0);
	}

	/** Places the element on a terrain (its y = ground height + offset). */
	public static void snapToGround(Object item, Object terrain, float offset) {
		B3.setPosition(item, B3.x(item), B3.groundHeight(terrain, B3.x(item), B3.z(item)) + offset, B3.z(item));
	}

	public static float distanceXZ(Object a, Object b) {
		float dx = B3.x(a) - B3.x(b), dz = B3.z(a) - B3.z(b);
		return (float) Math.sqrt(dx * dx + dz * dz);
	}

	public static float rightX(Object item) {
		Object3D o = B3.o(item);
		return o == null ? 1 : o.right(new Vector3()).x;
	}

	public static float rightZ(Object item) {
		Object3D o = B3.o(item);
		return o == null ? 0 : o.right(new Vector3()).z;
	}

	// ---------------------------------------------------------------- physics

	/** Sets the up speed (keeps the other two): a jump. {@code onlyOnGround}: only when standing on something. */
	public static void jump(Object item, float speed, boolean onlyOnGround) {
		if (onlyOnGround && !B3.isGrounded(item)) return;
		B3.setVelocity(item, B3.velX(item), speed, B3.velZ(item));
	}

	public static void stop(Object item) {
		B3.setVelocity(item, 0, 0, 0);
		B3.setAngularVelocity(item, 0, 0, 0);
	}

	/** Pushes along its own front (meters per second added at once, scaled by mass). */
	public static void pushForward(Object item, float strength) {
		B3.applyImpulse(item, B3.forwardX(item) * strength, B3.forwardY(item) * strength, B3.forwardZ(item) * strength);
	}

	/** Moves along its front at a speed, keeping the fall speed (character walking with physics). */
	public static void walkForward(Object item, float speed) {
		B3.setVelocityKeepY(item, B3.forwardX(item) * speed, B3.forwardZ(item) * speed);
	}

	/** Ray straight down from the element's center: true when something is under it within {@code dist} meters. */
	public static boolean raycastDown(Object item, float dist) {
		Object3D o = B3.o(item);
		World3D w = B3.world();
		if (o == null || w == null) return false;
		Vector3 c = o.center(new Vector3());
		return w.physics().raycast(c.x, c.y, c.z, 0, -1, 0, dist, o);
	}

	/** Ray from one element toward another: true when the first thing hit is the target (line of sight). */
	public static boolean canSee(Object item, Object target, float maxDistance) {
		Object3D o = B3.o(item);
		World3D w = B3.world();
		if (o == null || w == null || target == null) return false;
		Vector3 c = o.center(new Vector3());
		float dx = B3.x(target) - c.x, dy = B3.y(target) - c.y, dz = B3.z(target) - c.z;
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d > maxDistance || d < 0.0001f) return d < 0.0001f;
		if (!w.physics().raycast(c.x, c.y, c.z, dx, dy, dz, d + 0.5f, o)) return true;
		return B3.rayHit() == target;
	}

	public static float gravityX() {
		World3D w = B3.world();
		return w == null ? 0 : w.gravity.x;
	}

	public static float gravityY() {
		World3D w = B3.world();
		return w == null ? 0 : w.gravity.y;
	}

	public static float gravityZ() {
		World3D w = B3.world();
		return w == null ? 0 : w.gravity.z;
	}

	// ---------------------------------------------------------------- looks

	public static void toggleVisible(Object item) {
		if (item instanceof com.badlogic.gdx.scenes.scene2d.Actor) {
			com.badlogic.gdx.scenes.scene2d.Actor a = (com.badlogic.gdx.scenes.scene2d.Actor) item;
			a.setVisible(!a.isVisible());
		}
	}

	public static boolean isVisible(Object item) {
		return item instanceof com.badlogic.gdx.scenes.scene2d.Actor && ((com.badlogic.gdx.scenes.scene2d.Actor) item).isVisible();
	}

	public static float opacity(Object item) {
		Object3D o = B3.o(item);
		return o == null ? 1 : o.getOpacity();
	}

	/** Shader error of the element ("" when it compiled). */
	public static String shaderError(Object item) {
		Object3D o = B3.o(item);
		if (o == null) return "";
		String e = o.getShaderError();
		return e == null ? "" : e;
	}

	public static void clearShader(Object item) {
		B3.setShader(item, "", "");
	}

	public static boolean hasNode(Object item, String node) {
		Object3D o = B3.o(item);
		return o != null && o.hasNode(node);
	}

	public static int animationCount(Object item) {
		Object3D o = B3.o(item);
		return o == null ? 0 : o.getAnimationCount();
	}

	// ---------------------------------------------------------------- lights / sky

	public static boolean isLightOn(Object item) {
		Light3D l = B3.l(item);
		return l != null && l.isOn();
	}

	public static float lightIntensity(Object item) {
		Light3D l = B3.l(item);
		return l == null ? 0 : l.getIntensity();
	}

	/** Spot light opening (degrees). */
	public static void setConeAngle(Object item, float degrees) {
		Light3D l = B3.l(item);
		if (l != null) l.setConeAngle(degrees);
	}

	public static void fogOff() {
		World3D w = B3.world();
		if (w != null) w.fogEnabled = false;
	}

	/** Brightness of the whole picture (1 = normal). */
	public static void setExposure(float v) {
		World3D w = B3.world();
		if (w != null) w.exposure = v;
	}

	/** How far from the camera shadows are drawn (meters). */
	public static void setShadowDistance(float meters) {
		World3D w = B3.world();
		if (w != null) w.shadowDistance = meters;
	}

	public static String lightingPreset() {
		World3D w = B3.world();
		return w == null ? "" : w.preset;
	}

	public static String renderMode() {
		World3D w = B3.world();
		return w == null ? "" : w.renderMode;
	}

	public static void setPostAmount(float amount) {
		PostFx3D.amount = amount;
	}

	public static void postEffectOff() {
		B3.setPostEffect("NONE", 1);
	}

	public static String postEffect() {
		World3D w = B3.world();
		return w == null ? "NONE" : w.postEffect;
	}

	/** Seconds since the 3D scene started (the u_time of the shaders). */
	public static float time() {
		return World3D.time;
	}

	// ---------------------------------------------------------------- camera

	public static float fov() {
		Camera3D c = B3.cam();
		return c == null ? 0 : c.getFov();
	}

	public static String followMode() {
		Camera3D c = B3.cam();
		return c == null ? "NONE" : c.followMode;
	}

	/** Main camera moves to a point and looks at an element (cut scene). */
	public static void cameraLookAtItem(Object target) {
		Camera3D c = B3.cam();
		if (c != null && target instanceof PlayerItem) c.lookAtItem((PlayerItem) target);
	}

	public static void cameraTo(float x, float y, float z, float seconds) {
		Camera3D c = B3.cam();
		if (c == null) return;
		if (seconds <= 0) c.setPosition3(x, y, z);
		else c.moveTo(x, y, z, seconds);
	}

	// ---------------------------------------------------------------- quality

	public static void setPostEffectsAllowed(boolean on) {
		Quality3D.postEffects = on;
		Quality3D.custom();
		Quality3D.save();
	}

	public static float renderScale() {
		return Quality3D.renderScale;
	}

	public static float viewDistance() {
		return Quality3D.viewDistance;
	}

	public static int shadowQuality() {
		return Quality3D.shadowSize;
	}

	public static int fpsLimit() {
		return Quality3D.fpsLimit;
	}

	public static int waterQuality() {
		return Quality3D.waterLevel;
	}

	/** Next level LOW -> MEDIUM -> HIGH -> ULTRA -> LOW (a settings button). */
	public static void nextQuality() {
		String l = Quality3D.level;
		String n = Quality3D.LOW.equals(l) ? Quality3D.MEDIUM : Quality3D.MEDIUM.equals(l) ? Quality3D.HIGH : Quality3D.HIGH.equals(l) ? Quality3D.ULTRA : Quality3D.LOW;
		B3.setQuality(n);
	}

	// ---------------------------------------------------------------- objects

	public static boolean isDestroyed(Object item) {
		Object3D o = B3.o(item);
		return o == null || o.isDestroyed();
	}

	/** Copy of an element at the place of another one (bullet at the gun). */
	public static PlayerItem spawnAt(Object item, String newName, Object place) {
		return B3.spawn(item, newName, B3.x(place), B3.y(place), B3.z(place));
	}

	// ---------------------------------------------------------------- inventory

	public static int inventorySize() {
		return Inventory.size();
	}

	public static String inventoryAt(int index) {
		return Inventory.at(index);
	}

	public static void clearInventory() {
		Inventory.clear();
	}

	public static void selectItem(String id) {
		Inventory.selected = id == null ? "" : id;
	}

	public static String selectedItem() {
		return Inventory.selected;
	}

	/** Sets the count of an item (0 removes it). */
	public static void setItemCount(String id, int count) {
		Inventory.add(id, count - Inventory.count(id));
	}
}
