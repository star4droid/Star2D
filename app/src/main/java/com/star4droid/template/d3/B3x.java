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

	// ---------------------------------------------------------------- painted terrains

	private static Terrain3D painted(Object3D o) {
		return o == null || o.terrain == null || o.terrain.data == null ? null : o.terrain;
	}

	/** Digs a soft hole (depth m, negative = a mound) at world x, z on a terrain; collisions follow. */
	public static void terrainDig(Object terrain, float x, float z, float radius, float depth) {
		Object3D o = B3.o(terrain);
		Terrain3D t = painted(o);
		if (t == null || radius <= 0) return;
		float[] uv = new float[2];
		t.uvAt(o, x, 0, z, uv);
		float sx = Math.abs(o.scale.x), sz = Math.abs(o.scale.z), hm = Math.max(0.01f, t.maxHeight * Math.abs(o.scale.y));
		int res = t.res, n = res + 1;
		float ru = radius / sx, rv = radius / sz;
		int i0 = Math.max(0, (int) ((uv[0] - ru) * res) - 1), i1 = Math.min(res, (int) ((uv[0] + ru) * res) + 1);
		int j0 = Math.max(0, (int) ((uv[1] - rv) * res) - 1), j1 = Math.min(res, (int) ((uv[1] + rv) * res) + 1);
		if (i0 > i1 || j0 > j1) return;
		for (int j = j0; j <= j1; j++)
			for (int i = i0; i <= i1; i++) {
				float du = ((float) i / res - uv[0]) / ru, dv = ((float) j / res - uv[1]) / rv;
				float r = (float) Math.sqrt(du * du + dv * dv);
				if (r >= 1) continue;
				float w = (float) (0.5 + 0.5 * Math.cos(r * Math.PI));
				t.h[j * n + i] -= depth * w / hm;
			}
		t.heightsChanged(i0, j0, i1, j1);
		if (o.rigid != null && o.world != null) o.createBody();
	}

	/** Paints a layer (1..4) of a terrain around world x, z. */
	public static void terrainPaint(Object terrain, float x, float z, float radius, int layer) {
		Object3D o = B3.o(terrain);
		Terrain3D t = painted(o);
		if (t == null || !t.painted) return;
		float[] uv = new float[2];
		if (!t.uvAt(o, x, 0, z, uv)) return;
		if (TerrainEdit.paint(t, o, layer - 1, uv[0], uv[1], radius, 1, 0.5f, 1)) t.splatChanged();
	}

	/** Adds a tree / rock... ("tree_round", "rock", "builtin:bush" or a model "/tree.glb") at world x, z. */
	public static void terrainPlant(Object terrain, String kind, float x, float z, float scale) {
		Object3D o = B3.o(terrain);
		Terrain3D t = painted(o);
		if (t == null || !t.painted || kind == null || kind.length() == 0) return;
		float[] uv = new float[2];
		if (!t.uvAt(o, x, 0, z, uv)) return;
		String k = kind.startsWith("/") || kind.startsWith("builtin:") ? kind : "builtin:" + kind;
		int p = t.data.proto(k);
		t.data.inst.add(new TerrainData.Inst(p, uv[0], uv[1], com.badlogic.gdx.math.MathUtils.random(360f), scale <= 0 ? 1 : scale, com.badlogic.gdx.math.MathUtils.random(1 << 30)));
		t.data.revision++;
		if (t.foliage != null) t.foliage.instancesChanged();
		if (o.rigid != null && o.world != null && t.data.protos.get(p).collide > 0) o.createBody();
	}

	/** Removes the trees / rocks / objects of a terrain within radius of world x, z. */
	public static void terrainClear(Object terrain, float x, float z, float radius) {
		Object3D o = B3.o(terrain);
		Terrain3D t = painted(o);
		if (t == null || !t.painted) return;
		float[] uv = new float[2];
		t.uvAt(o, x, 0, z, uv);
		if (TerrainEdit.erase(t, o, -1, uv[0], uv[1], radius) > 0 && o.rigid != null && o.world != null) o.createBody();
	}

	/** Slope of a terrain at world x, z: 0 flat .. 1 vertical. */
	public static float groundSlope(Object terrain, float x, float z) {
		Object3D o = B3.o(terrain);
		Terrain3D t = painted(o);
		if (t == null) return 0;
		float[] uv = new float[2];
		t.uvAt(o, x, 0, z, uv);
		return t.data.slope(uv[0], uv[1], Math.abs(o.scale.x), Math.abs(o.scale.z), t.maxHeight * Math.abs(o.scale.y));
	}

	/** The most painted layer (1..4) of a terrain at world x, z (footstep sounds, speed on roads...). */
	public static int groundLayer(Object terrain, float x, float z) {
		Object3D o = B3.o(terrain);
		Terrain3D t = painted(o);
		if (t == null || !t.painted) return 1;
		float[] uv = new float[2];
		t.uvAt(o, x, 0, z, uv);
		int best = 0;
		float bw = -1;
		for (int l = 0; l < 4; l++) {
			float w = t.data.weight(l, uv[0], uv[1]);
			if (w > bw) {
				bw = w;
				best = l;
			}
		}
		return best + 1;
	}

	/** This element bends the grass around it (radius m, 0 = stop). The player controllers do it by themselves. */
	public static void grassBender(Object item, float radius) {
		Object3D o = B3.o(item);
		if (o == null) return;
		if (radius <= 0) {
			Foliage3D.bendPos.set(0, -9999, 0);
			Foliage3D.bender = null;
			return;
		}
		Foliage3D.bender = o;
		Foliage3D.bendRadius = radius;
	}

	/** A new random world on a terrain: biome MEADOW FOREST AUTUMN DESERT SNOW TROPICAL, shape HILLS MOUNTAINS ISLAND VALLEY PLAINS CANYON. */
	public static void generateTerrain(Object terrain, String biome, String shape, int seed) {
		Object3D o = B3.o(terrain);
		if (o == null || o.terrain == null) return;
		TerrainGen.Settings s = new TerrainGen.Settings();
		s.width = Math.abs(o.scale.x);
		s.length = Math.abs(o.scale.z);
		s.height = Math.max(1, o.def.Terrain_Height);
		s.biome = biome == null ? "MEADOW" : biome.toUpperCase();
		s.shape = shape == null ? "HILLS" : shape.toUpperCase();
		s.seed = seed;
		float area = s.width * s.length / 14400f;
		s.trees = (int) (120 * area);
		s.bushes = (int) (80 * area);
		s.rocks = (int) (50 * area);
		s.flowers = (int) (150 * area);
		TerrainGen.Result r = TerrainGen.generate(s);
		String path = o.def.Terrain_Data != null && o.def.Terrain_Data.length() > 0 ? o.def.Terrain_Data : "/runtime_" + o.getName() + ".s2t";
		TerrainStore.put(path, r.data);
		o.def.Terrain_Data = path;
		for (com.badlogic.gdx.utils.ObjectMap.Entry<String, String> e : r.props.entries()) {
			if (e.key.startsWith("Scale") || e.key.equals("Terrain Height")) continue;
			try {
				java.lang.reflect.Field f = com.star4droid.star2d.ElementDefs.Object3DDef.class.getField(e.key.replace(' ', '_'));
				if (f.getType() == float.class) f.setFloat(o.def, Float.parseFloat(e.value));
				else if (f.getType() == String.class) f.set(o.def, e.value);
			} catch (Exception ignored) {
			}
		}
		if (o.rigid != null && o.world != null) o.world.physics().removeBody(o);
		o.buildModel();
		if (o.world != null && o.world.physicsAllowed() && !"NONE".equals(o.def.Body)) o.createBody();
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

	// ---------------------------------------------------------------- particle effects (Mesh PARTICLES)

	public static void playParticles(Object item) {
		Object3D o = B3.o(item);
		if (o != null) o.restartParticles();
	}

	public static void setParticlesEmitting(Object item, boolean on) {
		Object3D o = B3.o(item);
		if (o != null) o.setEmitting(on);
	}

	public static void setParticleFile(Object item, String file) {
		Object3D o = B3.o(item);
		if (o != null) o.setParticleFile(file);
	}

	public static void setParticleScale(Object item, float scale) {
		Object3D o = B3.o(item);
		if (o != null) o.setParticleScale(scale);
	}

	public static boolean particlesFinished(Object item) {
		Object3D o = B3.o(item);
		return o == null || o.particlesFinished();
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

	/** Removes the element after some seconds (a Timer of the runtime, so the block needs no final variables). */
	public static void destroyAfter(final Object item, float seconds) {
		if (seconds <= 0) {
			B3.destroy(item);
			return;
		}
		com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
			@Override
			public void run() {
				B3.destroy(item);
			}
		}, seconds);
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
