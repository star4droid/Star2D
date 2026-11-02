package com.star4droid.template.behaviors;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A reusable piece of behavior you attach to ANY element (UI or physical), several per element.
 *
 * <pre>
 * &#64;BehaviorInfo(name="Floating", icon="wave", desc="Moves up and down.", targets=BehaviorInfo.Target.BOTH)
 * public class Floating extends Behavior {
 *     public float amplitude = 12;   // every public non-static non-final field becomes a parameter in the inspector
 *     public float speed = 2;
 *     float t, startY;
 *     public void onStart()          { startY = centerY(); }
 *     public void onUpdate(float dt) { t += dt * speed; setCenterY(startY + (float) Math.sin(t) * amplitude); }
 * }
 * </pre>
 *
 * Units: ALL positions, sizes and speeds in a behavior are editor PIXELS (y up, origin bottom-left) and px/s, like the
 * blocks. {@link #W} converts to Box2D world units when you touch the Body directly (world = px * 0.25 for physical
 * elements; screen-space UI elements have no body and use px as they are).
 *
 * Lifecycle (called by the engine, never call them yourself): onStart (once, when the element exists and the scene runs),
 * onUpdate(dt) every frame while the game is playing and the behavior is enabled, touch / click / collision callbacks,
 * onEnable / onDisable (setEnabled), onDestroy (element destroyed).
 *
 * Runtime code, no android.* imports (also runs on the desktop templates).
 */
public abstract class Behavior {
	/** Pixels -> Box2D world units (same constant as the engine, see StageImp.WORLD_SCALE). */
	public static final float W = 0.25f;

	/** The element this behavior is attached to. */
	public PlayerItem item;
	/** The running scene. */
	public StageImp stage;
	/** Turn it off/on from code: setEnabled(false). */
	@Hidden public boolean enabled = true;

	// ------------------------------------------------------------------ lifecycle (override what you need)

	public void onStart() {}
	public void onUpdate(float dt) {}
	public void onTouchStart(InputEvent event) {}
	public void onTouchEnd(InputEvent event) {}
	public void onClick() {}
	public void onCollisionBegin(PlayerItem other) {}
	public void onCollisionEnd(PlayerItem other) {}
	public void onEnable() {}
	public void onDisable() {}
	public void onDestroy() {}

	// ------------------------------------------------------------------ events raised to the user's blocks/code

	/** A handler the generated scene code registers for an event of this behavior. */
	public interface Handler {
		/** current = the element, args = the values passed to {@link #fire}. */
		void run(PlayerItem current, Object[] args);
	}

	private HashMap<String, ArrayList<Handler>> handlers;

	/** Registers user code for an event (done by the generated scene code from Events/&lt;scene&gt;/private/&lt;element&gt;/behaviors/...). */
	public final Behavior on(String event, Handler handler) {
		if (handlers == null) handlers = new HashMap<String, ArrayList<Handler>>();
		ArrayList<Handler> list = handlers.get(event);
		if (list == null) {
			list = new ArrayList<Handler>();
			handlers.put(event, list);
		}
		list.add(handler);
		return this;
	}

	/** Raises one of the events declared in {@link BehaviorInfo#events()}: fire("onDeath") / fire("onHit", enemy, bullet). */
	protected final void fire(String event, Object... args) {
		if (handlers == null) return;
		ArrayList<Handler> list = handlers.get(event);
		if (list == null) return;
		Object[] a = args == null ? new Object[0] : args;
		for (int i = 0; i < list.size(); i++) {
			try {
				list.get(i).run(item, a);
			} catch (RuntimeException ex) {
				Gdx.app.error("Behavior", getClass().getSimpleName() + "." + event + ": " + ex);
			}
		}
	}

	/** Used when an element is cloned: the clone's behaviors raise the same user events. */
	public final void copyHandlersFrom(Behavior other) {
		if (other == null || other.handlers == null) return;
		handlers = new HashMap<String, ArrayList<Handler>>();
		for (java.util.Map.Entry<String, ArrayList<Handler>> e : other.handlers.entrySet())
			handlers.put(e.getKey(), new ArrayList<Handler>(e.getValue()));
	}

	// ------------------------------------------------------------------ state

	private boolean started, attached;
	private float time;

	/** Seconds since the behavior started (only while playing). */
	public final float time() {
		return time;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	/**
	 * Switch the behavior on/off at any time (also item.setBehaviorEnabled("Car", true)). Turning it off calls onDisable(), which
	 * must undo what the behavior did to the element (it never rewrites the saved properties). Prefer
	 * {@code item.setBehaviorEnabled(id, true)}: it also switches off the other behaviors of the same {@link Exclusive} group.
	 */
	public final void setEnabled(boolean on) {
		if (on == enabled) return;
		enabled = on;
		if (on) {
			if (!started) engineStart();
			onEnable();
		} else if (started) onDisable();
	}

	/** Attaches this behavior to an element (generated code: {@code bh.attach(player, this);}). Safe to call once. */
	public final Behavior attach(PlayerItem target, StageImp scene) {
		if (attached) return this;
		attached = true;
		item = target;
		stage = scene;
		ItemScript script = target.getScript();
		if (script == null) {
			script = new BehaviorHost(target);
			script.setItem(target).setStage(scene);
			target.setScript(script);
		}
		if (script.stage == null) script.setStage(scene);
		script.addBehavior(this);
		return this;
	}

	/** The id stored in the scene = the class name. */
	public final String id() {
		return getClass().getSimpleName();
	}

	/** Name of the {@link Exclusive} group ("movement"...), "" when it has none. */
	public final String group() {
		Exclusive e = getClass().getAnnotation(Exclusive.class);
		return e == null ? "" : e.value();
	}

	// engine hooks (called by ItemScript)
	public final void engineStart() {
		if (started || !enabled) return; // a disabled behavior starts when it is first enabled
		started = true;
		try {
			applySets();
			onStart();
		} catch (RuntimeException ex) {
			Gdx.app.error("Behavior", getClass().getSimpleName() + ".onStart: " + ex);
		}
	}

	public final void engineDestroy() {
		try {
			if (started && enabled) onDisable();
			onDestroy();
		} catch (RuntimeException ex) {
			Gdx.app.error("Behavior", getClass().getSimpleName() + ".onDestroy: " + ex);
		}
	}

	public final void engineUpdate() {
		if (!enabled) return;
		if (!started) engineStart();
		if (stage != null && !stage.isPlaying()) return;
		float dt = Gdx.graphics.getDeltaTime();
		time += dt;
		onUpdate(dt);
	}

	/** Physics settings from {@link Sets}, applied when the behavior starts (never saved). */
	private void applySets() {
		Sets sets = getClass().getAnnotation(Sets.class);
		Body b = body();
		if (sets == null || b == null) return;
		for (String kv : sets.value()) {
			int eq = kv.indexOf('=');
			if (eq < 0) continue;
			String k = kv.substring(0, eq).trim(), v = kv.substring(eq + 1).trim();
			try {
				if (k.equals("gravityScale")) b.setGravityScale(Float.parseFloat(v));
				else if (k.equals("fixedRotation")) b.setFixedRotation(Boolean.parseBoolean(v));
				else if (k.equals("bullet")) b.setBullet(Boolean.parseBoolean(v));
				else if (k.equals("linearDamping")) b.setLinearDamping(Float.parseFloat(v));
				else if (k.equals("angularDamping")) b.setAngularDamping(Float.parseFloat(v));
				else if (k.equals("type")) {
					String t = v.toUpperCase();
					b.setType(t.startsWith("K") ? com.badlogic.gdx.physics.box2d.BodyDef.BodyType.KinematicBody
							: (t.startsWith("S") ? com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody : com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody));
				} else if (k.equals("sensor")) {
					boolean on = Boolean.parseBoolean(v);
					for (int i = 0; i < b.getFixtureList().size; i++) b.getFixtureList().get(i).setSensor(on);
				}
			} catch (RuntimeException ignored) {
			}
		}
	}

	// ------------------------------------------------------------------ helpers for authors

	public final Body body() {
		try {
			return item == null ? null : item.getBody();
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public final Actor actor() {
		return item == null ? null : item.getActor();
	}

	/** True for screen-space UI elements (no physics body). */
	public final boolean isUI() {
		return body() == null;
	}

	// ------------------------------------------------------------------ 3D helpers (3D scenes: meters, Y up, degrees, front = local -Z)

	/** The element as a 3D object (OBJ3D), null when it is not one. */
	protected final com.star4droid.template.d3.Object3D obj() {
		return item instanceof com.star4droid.template.d3.Object3D ? (com.star4droid.template.d3.Object3D) item : null;
	}

	/** The element as a 3D camera (CAMERA3D), null when it is not one. */
	protected final com.star4droid.template.d3.Camera3D camera3d() {
		return item instanceof com.star4droid.template.d3.Camera3D ? (com.star4droid.template.d3.Camera3D) item : null;
	}

	/** The element as a 3D light (LIGHT3D), null when it is not one. */
	protected final com.star4droid.template.d3.Light3D light3d() {
		return item instanceof com.star4droid.template.d3.Light3D ? (com.star4droid.template.d3.Light3D) item : null;
	}

	/** True when the element lives in a 3D world (OBJ3D, LIGHT3D, CAMERA3D). */
	protected final boolean is3D() {
		return item instanceof com.star4droid.template.d3.Object3D || item instanceof com.star4droid.template.d3.Camera3D
				|| item instanceof com.star4droid.template.d3.Light3D;
	}

	/** The 3D world of the scene (null in a 2D scene; never creates one). */
	protected final com.star4droid.template.d3.World3D world3d() {
		if (stage != null && stage.hasWorld3D()) return stage.world3d();
		return com.star4droid.template.d3.World3D.current;
	}

	/** The main (active) camera of the 3D scene, null when there is none. */
	protected final com.star4droid.template.d3.Camera3D mainCamera3d() {
		com.star4droid.template.d3.World3D w = world3d();
		return w == null ? null : w.activeCamera;
	}

	/** Position (meters) of any 3D element into out; false (out untouched) for a 2D element or null. */
	protected static boolean pos3(PlayerItem it, com.badlogic.gdx.math.Vector3 out) {
		if (it instanceof com.star4droid.template.d3.Object3D) out.set(((com.star4droid.template.d3.Object3D) it).position);
		else if (it instanceof com.star4droid.template.d3.Camera3D) out.set(((com.star4droid.template.d3.Camera3D) it).position);
		else if (it instanceof com.star4droid.template.d3.Light3D) out.set(((com.star4droid.template.d3.Light3D) it).position);
		else return false;
		return true;
	}

	public final PlayerItem findItem(String name) {
		return stage == null ? null : stage.findItem(name);
	}

	/** Element center in editor pixels. */
	public final float centerX() {
		return cx(item);
	}

	public final float centerY() {
		return cy(item);
	}

	/** Element size in editor pixels. */
	public final float widthPx() {
		return wPx(item);
	}

	public final float heightPx() {
		return hPx(item);
	}

	// same measures for ANY element (enemies, targets...), editor pixels
	public static float cx(PlayerItem it) {
		Body b = it.getBody();
		if (b != null) return b.getPosition().x / W;
		Actor a = it.getActor();
		return a.getX() + a.getWidth() * 0.5f;
	}

	public static float cy(PlayerItem it) {
		Body b = it.getBody();
		if (b != null) return b.getPosition().y / W;
		Actor a = it.getActor();
		return a.getY() + a.getHeight() * 0.5f;
	}

	public static float wPx(PlayerItem it) {
		Actor a = it.getActor();
		return it.getBody() == null ? a.getWidth() : a.getWidth() / W;
	}

	public static float hPx(PlayerItem it) {
		Actor a = it.getActor();
		return it.getBody() == null ? a.getHeight() : a.getHeight() / W;
	}

	/** False for null, removed (destroyed) elements. */
	public static boolean alive(PlayerItem it) {
		return it != null && it.getActor() != null && it.getActor().getStage() != null;
	}

	/**
	 * The real box of an element in editor px {minX, minY, maxX, maxY}: the union of its collider shapes when it has a
	 * body (so a 192 px sprite with a 64 px collider counts as 64 px), otherwise the element box.
	 */
	public static void box(PlayerItem it, float[] out) {
		Body b = it.getBody();
		if (b != null && b.getFixtureList().size > 0) {
			float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
			Vector2 p = new Vector2();
			boolean any = false;
			for (com.badlogic.gdx.physics.box2d.Fixture f : b.getFixtureList()) {
				com.badlogic.gdx.physics.box2d.Shape sh = f.getShape();
				if (sh instanceof com.badlogic.gdx.physics.box2d.PolygonShape) {
					com.badlogic.gdx.physics.box2d.PolygonShape ps = (com.badlogic.gdx.physics.box2d.PolygonShape) sh;
					for (int i = 0; i < ps.getVertexCount(); i++) {
						ps.getVertex(i, p);
						p.set(b.getWorldPoint(p));
						x0 = Math.min(x0, p.x); x1 = Math.max(x1, p.x);
						y0 = Math.min(y0, p.y); y1 = Math.max(y1, p.y);
						any = true;
					}
				} else if (sh instanceof com.badlogic.gdx.physics.box2d.CircleShape) {
					com.badlogic.gdx.physics.box2d.CircleShape cs = (com.badlogic.gdx.physics.box2d.CircleShape) sh;
					p.set(b.getWorldPoint(cs.getPosition()));
					float r = cs.getRadius();
					x0 = Math.min(x0, p.x - r); x1 = Math.max(x1, p.x + r);
					y0 = Math.min(y0, p.y - r); y1 = Math.max(y1, p.y + r);
					any = true;
				}
			}
			if (any) {
				out[0] = x0 / W; out[1] = y0 / W; out[2] = x1 / W; out[3] = y1 / W;
				return;
			}
		}
		float cx = cx(it), cy = cy(it), hw = wPx(it) * 0.5f, hh = hPx(it) * 0.5f;
		out[0] = cx - hw; out[1] = cy - hh; out[2] = cx + hw; out[3] = cy + hh;
	}

	/** Colliders (or boxes, for elements without a body) of two elements overlap (axis aligned, rotation ignored). */
	public static boolean overlaps(PlayerItem a, PlayerItem b) {
		float[] p = new float[4], q = new float[4];
		box(a, p);
		box(b, q);
		return p[0] < q[2] && q[0] < p[2] && p[1] < q[3] && q[1] < p[3];
	}

	/** True if the element is held down by a finger. null -> false. */
	public final boolean isPressed(PlayerItem other) {
		return other != null && pressedOf(other).isDown();
	}

	private static com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable whiteDrawable;

	/** A plain white square drawable (tint it): bullets, particles, bars without an image. */
	public static com.badlogic.gdx.scenes.scene2d.utils.Drawable white() {
		if (whiteDrawable == null) {
			com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(2, 2, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
			pm.setColor(1f, 1f, 1f, 1f);
			pm.fill();
			com.badlogic.gdx.graphics.Texture tex = new com.badlogic.gdx.graphics.Texture(
					new com.badlogic.gdx.graphics.glutils.PixmapTextureData(pm, null, false, false, true));
			whiteDrawable = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(tex));
		}
		return whiteDrawable;
	}

	/** Moves the element so its center is at (x, y) pixels. Physical elements keep their velocity (use on static/kinematic ones). */
	public final void setCenter(float x, float y) {
		Body b = body();
		if (b != null) {
			b.setTransform(x * W, y * W, b.getAngle());
		} else {
			Actor a = actor();
			a.setPosition(x - a.getWidth() * 0.5f, y - a.getHeight() * 0.5f);
		}
	}

	public final void setCenterX(float x) {
		setCenter(x, centerY());
	}

	public final void setCenterY(float y) {
		setCenter(centerX(), y);
	}

	/** Box2D world units per frame-second for one px/s, taking the engine's physics sub-steps into account. */
	private float vk() {
		int st = stage == null ? 1 : Math.max(1, stage.getSteps());
		return W / st;
	}

	/** Velocity in px/s (physical elements only; UI elements are moved by their behavior). Positive y = up. */
	public final void setVelocity(float vx, float vy) {
		Body b = body();
		if (b != null) b.setLinearVelocity(vx * vk(), vy * vk());
	}

	public final void setVelocityX(float vx) {
		Body b = body();
		if (b != null) b.setLinearVelocity(vx * vk(), b.getLinearVelocity().y);
	}

	public final void setVelocityY(float vy) {
		Body b = body();
		if (b != null) b.setLinearVelocity(b.getLinearVelocity().x, vy * vk());
	}

	/** Current velocity in px/s (0,0 for UI). */
	public final Vector2 velocity() {
		Body b = body();
		return b == null ? new Vector2() : new Vector2(b.getLinearVelocity()).scl(1f / vk());
	}

	/** Pressed state of any element (a button, a UI box...). Adds one touch listener the first time. */
	public static final class Pressed {
		private int fingers;
		public boolean isDown() {
			return fingers > 0;
		}
	}

	private HashMap<PlayerItem, Pressed> pressedMap;

	public final Pressed pressedOf(PlayerItem other) {
		if (other == null) return new Pressed();
		if (pressedMap == null) pressedMap = new HashMap<PlayerItem, Pressed>();
		Pressed p = pressedMap.get(other);
		if (p != null) return p;
		final Pressed pr = new Pressed();
		pressedMap.put(other, pr);
		other.getActor().addListener(new InputListener() {
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				pr.fingers++;
				// true so scene2d also delivers touchUp to this listener (the counter would never go down otherwise);
				// other listeners of the element still receive their own touchDown/touchUp events
				return true;
			}

			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
				if (pr.fingers > 0) pr.fingers--;
			}
		});
		return pr;
	}

	/** Keyboard (desktop/tests): true while any of the key codes of com.badlogic.gdx.Input.Keys is held. */
	public static boolean keyDown(int... keys) {
		for (int k : keys) if (Gdx.input.isKeyPressed(k)) return true;
		return false;
	}

	public static final int KEY_LEFT = Input.Keys.LEFT, KEY_RIGHT = Input.Keys.RIGHT, KEY_UP = Input.Keys.UP, KEY_DOWN = Input.Keys.DOWN,
			KEY_A = Input.Keys.A, KEY_D = Input.Keys.D, KEY_W = Input.Keys.W, KEY_S = Input.Keys.S, KEY_SPACE = Input.Keys.SPACE;

	public static float clamp(float v, float min, float max) {
		return v < min ? min : (v > max ? max : v);
	}

	/** Sets a public field by name; numbers are converted, an element field accepts the element's name, enums a constant name, Color a hex string. */
	public final boolean setParam(String field, Object value) {
		try {
			Field f = getClass().getField(field);
			if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) return false;
			Class<?> t = f.getType();
			Object v = value;
			if (value instanceof Number) {
				Number n = (Number) value;
				if (t == int.class || t == Integer.class) v = n.intValue();
				else if (t == long.class || t == Long.class) v = n.longValue();
				else if (t == short.class || t == Short.class) v = n.shortValue();
				else if (t == byte.class || t == Byte.class) v = n.byteValue();
				else if (t == float.class || t == Float.class) v = n.floatValue();
				else if (t == double.class || t == Double.class) v = n.doubleValue();
			} else if (value instanceof String) {
				String s = (String) value;
				if (t.isEnum()) {
					for (Object c : t.getEnumConstants()) if (((Enum<?>) c).name().equalsIgnoreCase(s)) v = c;
				} else if (t == com.badlogic.gdx.graphics.Color.class) v = com.badlogic.gdx.graphics.Color.valueOf(s.replace("#", ""));
				else if (PlayerItem.class.isAssignableFrom(t)) v = findItem(s);
				else if (t == float.class || t == double.class || t == int.class || t == long.class) return setParam(field, Double.valueOf(s));
				else if (t == boolean.class) v = Boolean.valueOf(s);
			} else if (value == null && t.isPrimitive()) return false;
			f.set(this, v);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	/** Value of a public field by name (null if there is none). */
	public final Object getParam(String field) {
		try {
			return getClass().getField(field).get(this);
		} catch (Exception ex) {
			return null;
		}
	}

	/** Copies every public field value (the parameters) from another instance of the same class; used when an element is cloned. */
	public final void copyParamsFrom(Behavior other) {
		if (other == null || other.getClass() != getClass()) return;
		for (Field f : getClass().getFields()) {
			int m = f.getModifiers();
			if (Modifier.isStatic(m) || Modifier.isFinal(m)) continue;
			try {
				f.set(this, f.get(other));
			} catch (Exception ignored) {
			}
		}
		enabled = other.enabled;
	}
}
