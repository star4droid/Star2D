package com.star4droid.template.behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.lib.Health3D;
import com.star4droid.template.behaviors.lib.Lifetime3D;
import com.star4droid.template.behaviors.lib.ThirdPersonController3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;

/**
 * Shared helpers of the 3D melee / area / boss behaviors (not a behavior): who is in front of an attacker, area hits,
 * hit effects, the movement controller of a character.
 */
public final class Combat3D {
	private Combat3D() {
	}

	/** Facing yaw of an element (degrees, Support3D convention) without the model's own turn. */
	public static float facing(PlayerItem it, float modelYawOffset) {
		return Support3D.yawOfItem(it) - modelYawOffset;
	}

	/** Living elements whose name matches, within range (XZ) and an arc (degrees, 360 = all around) of a facing. */
	public static void targets(World3D w, GameplaySupport.NameMatcher m, PlayerItem self, Vector3 from, float range, float arc, float facingYaw,
			ArrayList<Object3D> out) {
		out.clear();
		if (w == null || m.isEmpty()) return;
		for (int i = 0; i < w.objects.size; i++) {
			Object3D o = w.objects.get(i);
			if (o == self || !o.isVisible() || !Behavior.alive(o) || !m.matches(o.getName())) continue;
			Damageable d = GameplaySupport.damageable(o);
			if (d == null || d.isDead()) continue;
			float dx = o.position.x - from.x, dz = o.position.z - from.z;
			float r = Math.max(0.2f, Math.min(o.getSizeX(), o.getSizeZ()) * 0.45f);
			float dist = (float) Math.sqrt(dx * dx + dz * dz);
			if (dist - r > range) continue;
			if (Math.abs(o.position.y - from.y) > range + o.getSizeY()) continue;
			if (arc < 359 && dist > 0.3f) {
				float a = Support3D.angleDiff(facingYaw, Support3D.yawOf(dx, dz));
				if (Math.abs(a) > arc * 0.5f) continue;
			}
			out.add(o);
		}
	}

	/** Damage + push away from a point (horizontal, m/s). */
	public static boolean hit(Object3D target, float damage, PlayerItem source, Vector3 from, float push) {
		int r = GameplaySupport.hit(target, damage, source, 0, 0, 0, 0);
		if (r == GameplaySupport.HURT && push > 0 && target.isDynamic()) {
			float dx = target.position.x - from.x, dz = target.position.z - from.z, l = (float) Math.sqrt(dx * dx + dz * dz);
			if (l > 0.001f) target.setVelocity3(dx / l * push, Math.max(target.getVelocityY3(), push * 0.35f), dz / l * push);
		}
		return r == GameplaySupport.HURT;
	}

	/** A copy of an effect element shown at a point for some seconds (null template = nothing). */
	public static Object3D effect(PlayerItem template, float x, float y, float z, float seconds) {
		if (!(template instanceof Object3D)) return null;
		Object3D fx = (Object3D) template;
		PlayerItem c = fx.getClone(fx.getName() + "_fx" + GameplaySupport.nextId());
		if (!(c instanceof Object3D)) return null;
		Object3D o = (Object3D) c;
		o.setPosition3(x, y, z);
		o.setVisible(true);
		Lifetime3D.after(o, seconds);
		return o;
	}

	private static final java.util.WeakHashMap<PlayerItem, boolean[]> SLEEPING = new java.util.WeakHashMap<PlayerItem, boolean[]>();

	/**
	 * A template that only exists to be copied: hidden, no body, its behaviors paused (a hidden enemy template must not
	 * chase or shoot). Copies made with {@link #cloneLive} get the behaviors as they were.
	 */
	public static void sleep(PlayerItem t) {
		if (t == null || SLEEPING.containsKey(t)) return;
		com.star4droid.template.Utils.ItemScript sc = t.getScript();
		java.util.ArrayList<Behavior> all = sc == null ? new java.util.ArrayList<Behavior>() : sc.getBehaviors();
		boolean[] st = new boolean[all.size()];
		for (int i = 0; i < all.size(); i++) {
			st[i] = all.get(i).isEnabled();
			all.get(i).setEnabled(false);
		}
		SLEEPING.put(t, st);
		Support3D.setActive3(t, false);
	}

	/** A copy of a template with its behaviors running (also for templates put to sleep). */
	public static PlayerItem cloneLive(Object3D t, String name) {
		boolean[] st = SLEEPING.get(t);
		com.star4droid.template.Utils.ItemScript sc = t.getScript();
		java.util.ArrayList<Behavior> all = sc == null ? null : sc.getBehaviors();
		if (st != null && all != null) for (int i = 0; i < all.size() && i < st.length; i++) all.get(i).enabled = st[i];
		PlayerItem c;
		try {
			c = t.getClone(name);
		} finally {
			if (st != null && all != null) for (int i = 0; i < all.size() && i < st.length; i++) all.get(i).enabled = false;
		}
		return c;
	}

	/** The third person controller of an element (null when it has none). */
	public static ThirdPersonController3D controller(PlayerItem it) {
		if (it == null) return null;
		Behavior b = it.getBehavior("ThirdPersonController3D");
		return b instanceof ThirdPersonController3D ? (ThirdPersonController3D) b : null;
	}

	/** Turns an element to a heading (degrees, without the model's turn); its controller keeps it. */
	public static void face(PlayerItem it, float yaw, float modelYawOffset) {
		ThirdPersonController3D c = controller(it);
		if (c != null) {
			c.setHeading(yaw);
			return;
		}
		Object3D o = Support3D.o(it);
		if (o != null) o.setYaw3(yaw + modelYawOffset);
	}

	/** Standing on the ground (controller, else a short ray / terrain test). */
	public static boolean grounded(PlayerItem it, World3D w) {
		ThirdPersonController3D c = controller(it);
		if (c != null) return c.isGrounded();
		Object3D o = Support3D.o(it);
		if (o == null) return true;
		float g = Support3D.groundY(w, o.position.x, o.getBottomY() + 0.3f, o.position.z, 0.6f, o);
		return !Float.isNaN(g);
	}

	/** Health 0..1 of an element (1 when it has no health). */
	public static float healthPercent(PlayerItem it) {
		if (it == null) return 1;
		Behavior b = it.getBehavior("Health3D");
		return b instanceof Health3D ? ((Health3D) b).getHealthPercent() / 100f : 1;
	}

	static float clamp(float v, float a, float b) {
		return MathUtils.clamp(v, a, b);
	}
}
