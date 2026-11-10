package com.star4droid.template.behaviors;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ObjectMap;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.d3.Assets3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.Sounds3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared code of the 3D behaviors (behaviors/lib/*3D): input, headings, ground, character motion, animation clips, sounds in
 * space, on-screen prompts. Units: meters, seconds, degrees; Y is up; the front of an element is its local -Z, so a heading
 * (yaw) of 0 looks along -Z and the forward vector of a heading is (-sin yaw, 0, -cos yaw).
 * Runtime code (Java 7, no android.*).
 */
public final class Support3D {
	private Support3D() {
	}

	// ------------------------------------------------------------------ elements

	public static Object3D o(PlayerItem it) {
		return it instanceof Object3D ? (Object3D) it : null;
	}

	public static Camera3D cam(PlayerItem it) {
		return it instanceof Camera3D ? (Camera3D) it : null;
	}

	/** The camera param, or the scene's main camera when it is empty. */
	public static Camera3D cameraOr(PlayerItem it, World3D w) {
		if (it instanceof Camera3D) return (Camera3D) it;
		return w == null ? null : w.activeCamera;
	}

	/** Position of any 3D element (or the main camera when it is null) into out; false when there is nothing to read. */
	public static boolean posOr(PlayerItem it, World3D w, Vector3 out) {
		if (it != null) return Behavior.pos3(it, out);
		Camera3D c = w == null ? null : w.activeCamera;
		if (c == null) return false;
		out.set(c.position);
		return true;
	}

	public static boolean alive3(PlayerItem it) {
		if (it instanceof Object3D) return !((Object3D) it).isDestroyed() && Behavior.alive(it);
		return Behavior.alive(it);
	}

	/** Shows / hides any element (2D or 3D). */
	public static void show(PlayerItem it, boolean on) {
		if (it == null || it.getActor() == null) return;
		if (it.getActor().isVisible() != on) it.getActor().setVisible(on);
	}

	/** The enabled behaviors of an element in an exclusive group ("movement"), e.g. to pause a controller while seated. */
	public static List<Behavior> groupBehaviors(PlayerItem it, String group, Behavior except) {
		ArrayList<Behavior> out = new ArrayList<Behavior>();
		if (it == null) return out;
		ItemScript s = it.getScript();
		if (s == null || s.getBehaviors() == null) return out;
		ArrayList<Behavior> bs = s.getBehaviors();
		for (int i = 0; i < bs.size(); i++) {
			Behavior b = bs.get(i);
			if (b != except && b.enabled && group.equals(b.group())) out.add(b);
		}
		return out;
	}

	// ------------------------------------------------------------------ input

	/**
	 * Movement input: out[0] = right (-1..1), out[1] = forward (-1..1). Joystick, four buttons (held) and arrows / WASD.
	 * Joystick pushes smaller than deadZone count as 0.
	 */
	public static void readMove(Behavior b, PlayerItem joystick, PlayerItem up, PlayerItem down, PlayerItem left, PlayerItem right,
			boolean keyboard, float deadZone, float[] out) {
		float x = 0, y = 0;
		if (joystick != null) {
			float jx = joystick.getJoyStickX(), jy = joystick.getJoyStickY();
			float len = (float) Math.sqrt(jx * jx + jy * jy);
			if (len > deadZone) {
				float k = Math.min(1f, (len - deadZone) / Math.max(0.0001f, 1f - deadZone)) / len;
				x = jx * k;
				y = jy * k;
			}
		}
		if (b.isPressed(up) || keyboard && Behavior.keyDown(Behavior.KEY_UP, Behavior.KEY_W)) y += 1;
		if (b.isPressed(down) || keyboard && Behavior.keyDown(Behavior.KEY_DOWN, Behavior.KEY_S)) y -= 1;
		if (b.isPressed(right) || keyboard && Behavior.keyDown(Behavior.KEY_RIGHT, Behavior.KEY_D)) x += 1;
		if (b.isPressed(left) || keyboard && Behavior.keyDown(Behavior.KEY_LEFT, Behavior.KEY_A)) x -= 1;
		float len = (float) Math.sqrt(x * x + y * y);
		if (len > 1) {
			x /= len;
			y /= len;
		}
		out[0] = x;
		out[1] = y;
	}

	/** Calls run on every tap (click) of a 2D element (a button, a UI box...). Returns the listener (to remove it). */
	public static EventListener onTap(PlayerItem el, final Runnable run) {
		if (el == null || el.getActor() == null) return null;
		Fx.enableTouch(el);
		ClickListener l = new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				run.run();
			}
		};
		el.getActor().addListener(l);
		return l;
	}

	public static void removeListener(PlayerItem el, EventListener l) {
		if (el != null && l != null && el.getActor() != null) el.getActor().removeListener(l);
	}

	// ------------------------------------------------------------------ angles

	/** Heading (degrees) that looks along (dx, dz). */
	public static float yawOf(float dx, float dz) {
		return MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees;
	}

	/** -180..180 difference b - a. */
	public static float angleDiff(float a, float b) {
		float d = ((b - a) % 360f + 540f) % 360f - 180f;
		return d;
	}

	/** Turns from a to b by at most maxStep degrees. */
	public static float approachAngle(float a, float b, float maxStep) {
		float d = angleDiff(a, b);
		if (Math.abs(d) <= maxStep) return b;
		return a + Math.signum(d) * maxStep;
	}

	public static float lerpAngle(float a, float b, float t) {
		return a + angleDiff(a, b) * t;
	}

	/** 1 - e^(-k dt): frame-rate independent smoothing factor. */
	public static float smooth(float k, float dt) {
		return k <= 0 ? 1f : 1f - (float) Math.exp(-k * dt);
	}

	/** Heading of the camera (degrees) from its real view direction. */
	public static float cameraYaw(Camera3D c) {
		if (c == null) return 0;
		Vector3 d = c.cam.direction;
		if (Math.abs(d.x) + Math.abs(d.z) < 0.0001f) return c.yaw;
		return yawOf(d.x, d.z);
	}

	/** Input (right, forward) seen from a heading -> world (x, z) into out[0], out[1]. */
	public static void relative(float right, float forward, float yawDeg, float[] out) {
		float s = MathUtils.sinDeg(yawDeg), c = MathUtils.cosDeg(yawDeg);
		// forward = (-sin, -cos), right = (cos, -sin)
		out[0] = -s * forward + c * right;
		out[1] = -c * forward - s * right;
	}

	// ------------------------------------------------------------------ ground

	/**
	 * Height of the ground under (x, z) searching down from y for at most maxDown meters: physics bodies (raycast) and
	 * terrains. NaN when there is nothing.
	 */
	public static float groundY(World3D w, float x, float y, float z, float maxDown, Object3D ignore) {
		if (w == null) return Float.NaN;
		float best = Float.NaN;
		if (w.hasPhysics()) {
			try {
				if (w.physics().raycast(x, y, z, 0, -1, 0, maxDown, ignore)) best = w.physics().hitPoint.y;
			} catch (Throwable ignored) {
			}
		}
		for (int i = 0; i < w.objects.size; i++) {
			Object3D t = w.objects.get(i);
			if (t == ignore || !t.isVisible() || t.d() == null || !"TERRAIN".equalsIgnoreCase(t.d().Mesh)) continue;
			if (Math.abs(x - t.position.x) > t.getSizeX() * 0.5f || Math.abs(z - t.position.z) > t.getSizeZ() * 0.5f) continue;
			float h = t.heightAt(x, z);
			if (h <= y + 0.01f && h >= y - maxDown && (Float.isNaN(best) || h > best)) best = h;
		}
		return best;
	}

	/** Something solid between two points (physics bodies only)? Ignores one element. */
	public static boolean blocked(World3D w, Vector3 from, Vector3 to, Object3D ignore, PlayerItem target) {
		if (w == null || !w.hasPhysics()) return false;
		float d = from.dst(to);
		if (d < 0.001f) return false;
		try {
			if (!w.physics().raycast(from.x, from.y, from.z, to.x - from.x, to.y - from.y, to.z - from.z, d, ignore)) return false;
			Object3D hit = w.physics().hitObject;
			return hit != null && hit != target && w.physics().hitDistance < d - 0.05f;
		} catch (Throwable t) {
			return false;
		}
	}

	// ------------------------------------------------------------------ character motion

	/**
	 * Walks an element (a 3D object with or without a body, or a 3D camera) on the ground: gravity, jumps, steps up to
	 * stepHeight, stops at walls (bodies). A DYNAMIC element is moved by velocity (its rotation should be locked), every
	 * other one by position (gravity simulated here).
	 */
	public static final class Mover {
		public float vy;
		public boolean grounded = true;
		public float stepHeight = 0.35f;
		/** Steepest ground it walks up (degrees, 0 = any): hills / cliffs of terrains stop it. */
		public float maxSlope = 0;
		/** Height of the feet when no ground is found at all (an empty scene): the start height. */
		public float floorY = Float.NaN;
		private float coyote;
		private final Vector3 p = new Vector3(), d = new Vector3();

		/** Distance from the element's position down to its feet. */
		public static float feetOffset(PlayerItem it, float eyeHeight) {
			Object3D o = o(it);
			if (o != null) return Math.max(0, o.position.y - o.getBottomY());
			return eyeHeight;
		}

		/** True when a jump started (on the ground, or just left it). */
		public boolean jump(PlayerItem it, float speed) {
			if (!grounded && coyote <= 0) return false;
			Object3D o = o(it);
			vy = speed;
			grounded = false;
			coyote = 0;
			if (o != null && o.isDynamic()) o.setVelocity3(o.getVelocityX3(), speed, o.getVelocityZ3());
			return true;
		}

		/** One frame: horizontal velocity vx, vz (m/s). */
		public void step(World3D w, PlayerItem it, float vx, float vz, float dt, float feet, float radius, boolean walls) {
			Object3D o = o(it);
			if (o != null && o.isDynamic()) {
				grounded = o.isGrounded(0.12f);
				if (grounded) coyote = 0.12f;
				else coyote -= dt;
				o.setVelocityKeepY(vx, vz);
				vy = o.getVelocityY3();
				return;
			}
			if (!Behavior.pos3(it, p)) return;
			float g = w == null ? 9.81f : -w.gravity.y;
			vy -= g * dt;
			if (vy < -50) vy = -50;
			float mx = vx * dt, mz = vz * dt;
			float feetY = p.y - feet;
			if (Float.isNaN(floorY)) floorY = feetY;
			if (walls && w != null && w.hasPhysics() && (mx != 0 || mz != 0)) {
				float len = (float) Math.sqrt(mx * mx + mz * mz);
				try {
					for (int pass = 0; pass < 2 && len > 0.00001f; pass++) {
						if (!w.physics().raycast(p.x, feetY + stepHeight + 0.05f, p.z, mx, 0, mz, len + radius, o)) break;
						d.set(w.physics().hitNormal);
						d.y = 0;
						if (d.isZero(0.0001f)) break;
						d.nor();
						float into = mx * d.x + mz * d.z;
						if (into >= 0) break;
						// slide along the wall
						mx -= d.x * into;
						mz -= d.z * into;
						len = (float) Math.sqrt(mx * mx + mz * mz);
					}
				} catch (Throwable ignored) {
				}
			}
			float nx = p.x + mx, nz = p.z + mz;
			float ny = feetY + vy * dt;
			float search = stepHeight + Math.max(0.6f, -vy * dt + 0.2f);
			float ground;
			if (maxSlope > 0 && grounded && (mx != 0 || mz != 0)) {
				// too steep uphill: test a bit ahead (a short step would hide the slope), stay where it is
				float len = (float) Math.sqrt(mx * mx + mz * mz), k = Math.max(len, 0.25f) / len;
				float ahead = groundY(w, p.x + mx * k, feetY + 3f, p.z + mz * k, 3f + search, o);
				if (!Float.isNaN(ahead) && ahead - feetY > Math.max(len, 0.25f) * (float) Math.tan(Math.toRadians(maxSlope))) {
					nx = p.x;
					nz = p.z;
				}
			}
			ground = groundY(w, nx, feetY + stepHeight, nz, search, o);
			if (Float.isNaN(ground) && (w == null || !w.hasPhysics()) && !hasTerrain(w)) ground = floorY;
			boolean wasGrounded = grounded;
			if (!Float.isNaN(ground) && ny <= ground + (wasGrounded && vy <= 0 ? stepHeight : 0.001f) && vy <= 0.001f) {
				ny = ground;
				vy = 0;
				grounded = true;
				coyote = 0.12f;
			} else {
				grounded = false;
				coyote -= dt;
			}
			setPos(it, nx, ny + feet, nz);
		}

		private static boolean hasTerrain(World3D w) {
			if (w == null) return false;
			for (int i = 0; i < w.objects.size; i++)
				if (w.objects.get(i).d() != null && "TERRAIN".equalsIgnoreCase(w.objects.get(i).d().Mesh)) return true;
			return false;
		}
	}

	/**
	 * Goes toward a point: on the ground (gravity, steps, walls through {@link Mover}) or flying straight in 3D, turning
	 * smoothly to where it goes. Used by path followers, patrols, chasers, tap-to-move.
	 */
	public static final class Walker {
		public final Mover mover = new Mover();
		public float yaw, speedNow;
		private boolean init;
		private float vx, vz;
		private final Vector3 p = new Vector3();

		/** Turns the heading at once (start, teleports). */
		public void setYaw(float y) {
			yaw = y;
			init = true;
		}

		/**
		 * One frame toward target. Returns the distance left (horizontal on the ground). speed in m/s, accel in 1/s (0 = at
		 * once), turnSpeed in deg/s (0 = never turn the element), yawOffset added to the element's heading (models facing +Z: 180).
		 */
		public float toward(World3D w, PlayerItem it, Vector3 target, float speed, float accel, float turnSpeed, float dt, boolean ground,
				float stopDistance, float yawOffset, float radius) {
			if (!Behavior.pos3(it, p)) return 0;
			if (!init) {
				init = true;
				yaw = yawOfItem(it) - yawOffset;
			}
			float dx = target.x - p.x, dy = ground ? 0 : target.y - p.y, dz = target.z - p.z;
			float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			float want = dist <= stopDistance ? 0 : speed;
			// slow down at the end instead of overshooting
			if (dist - stopDistance < want * 0.25f) want = Math.max(0, (dist - stopDistance) * 4f);
			float k = smooth(accel, dt);
			float tx = dist > 0.0001f ? dx / dist * want : 0, tz = dist > 0.0001f ? dz / dist * want : 0;
			vx += (tx - vx) * k;
			vz += (tz - vz) * k;
			speedNow = (float) Math.sqrt(vx * vx + vz * vz);
			if (turnSpeed > 0 && speedNow > 0.05f) {
				yaw = approachAngle(yaw, yawOf(vx, vz), turnSpeed * dt);
				Object3D o = o(it);
				if (o != null) o.setYaw3(yaw + yawOffset);
				else if (it instanceof Camera3D) ((Camera3D) it).yaw = yaw;
			}
			if (ground) mover.step(w, it, vx, vz, dt, Mover.feetOffset(it, 1.6f), radius, true);
			else {
				float ty = dist > 0.0001f ? dy / dist * want : 0;
				Object3D o = o(it);
				if (o != null && o.isDynamic()) o.setVelocity3(vx, ty, vz);
				else setPos(it, p.x + vx * dt, p.y + ty * dt, p.z + vz * dt);
				speedNow = (float) Math.sqrt(vx * vx + vz * vz + ty * ty);
			}
			return dist;
		}

		/** Stops (keeps falling when on the ground). */
		public void stop(World3D w, PlayerItem it, float dt, boolean ground, float radius) {
			vx = vz = 0;
			speedNow = 0;
			Object3D o = o(it);
			if (ground) mover.step(w, it, 0, 0, dt, Mover.feetOffset(it, 1.6f), radius, false);
			else if (o != null && o.isDynamic()) o.setVelocity3(0, 0, 0);
		}
	}

	public static void setPos(PlayerItem it, float x, float y, float z) {
		if (it instanceof Object3D) ((Object3D) it).setPosition3(x, y, z);
		else if (it instanceof Camera3D) ((Camera3D) it).position.set(x, y, z);
		else if (it instanceof com.star4droid.template.d3.Light3D) ((com.star4droid.template.d3.Light3D) it).setPosition3(x, y, z);
	}

	/** Heading of any 3D element (camera: its view). */
	public static float yawOfItem(PlayerItem it) {
		if (it instanceof Object3D) return ((Object3D) it).getYaw3();
		if (it instanceof Camera3D) return cameraYaw((Camera3D) it);
		return 0;
	}

	// ------------------------------------------------------------------ animation clips

	/** Plays model clips without restarting the one that already runs; crossfades between them. */
	public static final class Clips {
		public String current = "";

		public void play(Object3D o, String clip, boolean loop, float speed, float fade) {
			if (o == null || clip == null || clip.length() == 0) return;
			if (clip.equals(current)) {
				o.setAnimationSpeed(speed);
				return;
			}
			current = clip;
			if (fade > 0) o.crossFadeAnimation(clip, loop, speed, fade);
			else o.playAnimation(clip, loop, speed);
		}

		/** Idle / walk / run / jump from a horizontal speed (m/s); empty names are skipped (falls back to walk, then idle). */
		public void locomotion(Object3D o, float speed, boolean grounded, float walkSpeed, float runSpeed, String idle, String walk, String run,
				String jump, float fade, boolean scaleSpeed) {
			if (o == null || o.getAnimationCount() == 0) return;
			if (!grounded && jump != null && jump.length() > 0) {
				play(o, jump, false, 1, fade);
				return;
			}
			if (speed < Math.max(0.05f, walkSpeed * 0.25f)) {
				play(o, idle, true, 1, fade);
				return;
			}
			boolean running = run != null && run.length() > 0 && speed >= (walkSpeed + runSpeed) * 0.5f;
			String clip = running ? run : (walk != null && walk.length() > 0 ? walk : idle);
			float base = running ? runSpeed : walkSpeed;
			float k = scaleSpeed && base > 0 ? MathUtils.clamp(speed / base, 0.5f, 2f) : 1f;
			play(o, clip, true, k, fade);
		}
	}

	// ------------------------------------------------------------------ sounds

	/** A project sound (sounds folder; "step.mp3" or "sounds/step.mp3"), cached in {@link Sounds3D} (stopped when the scene ends); null when missing. */
	public static Sound sound(String path) {
		return Sounds3D.get(path);
	}

	/** Plays once; returns the id (-1 = not played). pitch 1 = normal, pan -1 left .. 1 right. */
	public static long play(String path, float volume, float pitch, float pan) {
		if (Sounds3D.isPaused()) return -1;
		Sound s = sound(path);
		if (s == null || volume <= 0.001f) return -1;
		try {
			return s.play(MathUtils.clamp(volume, 0, 1), MathUtils.clamp(pitch, 0.5f, 2f), MathUtils.clamp(pan, -1, 1));
		} catch (Throwable t) {
			return -1;
		}
	}

	/** Plays once at a point of the world: quieter far from the camera, panned left / right. */
	public static long playAt(String path, World3D w, float x, float y, float z, float volume, float maxDistance, float pitch) {
		if (w == null || w.camera == null) return play(path, volume, pitch, 0);
		float d = w.camera.position.dst(x, y, z);
		float vol = volume * MathUtils.clamp(1f - d / Math.max(0.1f, maxDistance), 0f, 1f);
		Vector3 right = new Vector3(w.camera.direction).crs(w.camera.up).nor();
		Vector3 to = new Vector3(x, y, z).sub(w.camera.position);
		float pan = to.isZero(0.001f) ? 0 : MathUtils.clamp(right.dot(to.nor()), -1, 1) * 0.8f;
		return play(path, vol, pitch, pan);
	}

	/** A looping sound (engine, hum): start / tune / stop. */
	public static final class Loop {
		private Sound s;
		private long id = -1;
		private String path = "";
		private int gen = Sounds3D.generation();

		public void update(String sound, boolean on, float volume, float pitch) {
			if (sound == null) sound = "";
			if (gen != Sounds3D.generation()) { // the sounds were released (scene closed / new context): start from scratch
				gen = Sounds3D.generation();
				s = null;
				id = -1;
				path = "";
			}
			if (Sounds3D.isPaused()) return;
			if (!sound.equals(path)) {
				stop();
				path = sound;
				s = sound(sound);
			}
			if (s == null) return;
			try {
				if (on && id < 0) id = s.loop(MathUtils.clamp(volume, 0, 1), MathUtils.clamp(pitch, 0.5f, 2f), 0);
				else if (!on && id >= 0) stop();
				else if (id >= 0) {
					s.setVolume(id, MathUtils.clamp(volume, 0, 1));
					s.setPitch(id, MathUtils.clamp(pitch, 0.5f, 2f));
				}
			} catch (Throwable ignored) {
			}
		}

		public void stop() {
			if (s != null && id >= 0 && gen == Sounds3D.generation()) {
				try {
					s.stop(id);
				} catch (Throwable ignored) {
				}
			}
			id = -1;
		}
	}

	// ------------------------------------------------------------------ prompts ("Tap to open")

	/**
	 * A 2D button / text that appears while the player is near (and looks at) something; one tap = one action. Several
	 * prompts may share one button ("USE"): the nearest one that wants it owns it, the others wait.
	 */
	public static final class Prompt {
		private static final java.util.HashMap<PlayerItem, Prompt> OWNERS = new java.util.HashMap<PlayerItem, Prompt>();
		private PlayerItem button, label, key;
		private EventListener listener;
		private boolean showing, tapped, init;
		private float score = Float.MAX_VALUE;
		private long frame;
		private String lastText = null;

		private static long frameId() {
			return Gdx.graphics == null ? 0 : Gdx.graphics.getFrameId();
		}

		private boolean live() {
			return showing && frameId() - frame <= 2;
		}

		/**
		 * Call every frame. score = distance (the smallest score wins a shared button). Returns true once per tap of the
		 * button while this prompt owns it.
		 */
		public boolean update(PlayerItem button, PlayerItem label, String text, boolean show, float score) {
			if (button != this.button) {
				Support3D.removeListener(this.button, listener);
				this.button = button;
				listener = Support3D.onTap(button, new Runnable() {
					@Override
					public void run() {
						if (owner() == Prompt.this && showing) tapped = true;
					}
				});
			}
			this.label = label;
			key = button != null ? button : label;
			frame = frameId();
			this.score = score;
			showing = show;
			if (key == null) {
				boolean t = tapped;
				tapped = false;
				return t;
			}
			Prompt owner = OWNERS.get(key);
			if (show) {
				if (owner == null || owner == this || !owner.live() || score < owner.score) {
					OWNERS.put(key, this);
					owner = this;
				}
			} else if (owner == this) {
				OWNERS.remove(key);
				Support3D.show(button, false);
				Support3D.show(label, false);
				lastText = null;
				owner = null;
			}
			if (!init) {
				init = true;
				if (owner == null) {
					Support3D.show(button, false);
					Support3D.show(label, false);
				}
			}
			if (owner == this) {
				Support3D.show(button, true);
				Support3D.show(label, true);
				if (label != null && text != null && text.length() > 0 && !text.equals(lastText)) {
					label.setItemText(text);
					lastText = text;
				}
			}
			boolean t = tapped;
			tapped = false;
			return t && owner == this;
		}

		private Prompt owner() {
			return key == null ? this : OWNERS.get(key);
		}

		public boolean isShown() {
			return key != null && OWNERS.get(key) == this;
		}

		public void hide() {
			showing = false;
			if (key != null && OWNERS.get(key) == this) {
				OWNERS.remove(key);
				Support3D.show(button, false);
				Support3D.show(label, false);
			}
			lastText = null;
		}

		public void dispose() {
			hide();
			Support3D.removeListener(button, listener);
			button = null;
			listener = null;
		}
	}

	/**
	 * "Can the player use it now?": distance from the player (empty = the main camera) to a point, and, when needLook, the
	 * camera must look at it. Returns the distance, or -1 when out of reach.
	 */
	public static float reach(World3D w, PlayerItem player, Vector3 point, float range, boolean needLook, float lookAngle) {
		Vector3 pp = new Vector3();
		if (!posOr(player, w, pp)) return -1;
		float d = pp.dst(point);
		if (player instanceof Object3D) {
			// a character: measure from its middle, not its feet
			Object3D o = (Object3D) player;
			float mid = (o.getBottomY() + o.position.y + o.getSizeY() * 0.5f) * 0.5f;
			d = Math.min(d, Vector3.dst(pp.x, mid, pp.z, point.x, point.y, point.z));
		}
		if (d > range) return -1;
		if (needLook && w != null && !looksAt(w.activeCamera, point, lookAngle)) return -1;
		return d;
	}

	/** Is the camera looking at the point (within maxAngle degrees of its view direction)? */
	public static boolean looksAt(Camera3D c, Vector3 point, float maxAngle) {
		if (c == null) return true;
		Vector3 to = new Vector3(point).sub(c.cam.position);
		if (to.isZero(0.001f)) return true;
		to.nor();
		return to.dot(c.cam.direction) >= MathUtils.cosDeg(maxAngle);
	}

	/** Makes an element ignore the world while hidden (no body) and come back. */
	public static void setActive3(PlayerItem it, boolean on) {
		Object3D o = o(it);
		if (o == null) return;
		o.setVisible(on);
		if (o.d() != null && !"NONE".equals(o.d().Body)) o.setPhysicsEnabled(on);
	}

	/** True when a debug/desktop key is pressed this frame. */
	public static boolean keyJust(int key) {
		return Gdx.input != null && Gdx.input.isKeyJustPressed(key);
	}
}
