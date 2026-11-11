package com.star4droid.template.d3;

import com.star4droid.template.anim.Anim;

/**
 * Smooth moves / turns / scales of 3D elements over time (blocks "move to in seconds"...). Since the timelines arrived they
 * are tweens of the shared animation system ({@link Anim}): the same "When a tween ends" event, the same update.
 */
public final class Tween3D {
	private Tween3D() {
	}

	public static void move(Object3D o, float x, float y, float z, float seconds) {
		if (o == null) return;
		Anim.tween(o, "x", x, seconds, "smooth");
		Anim.tween(o, "y", y, seconds, "smooth");
		Anim.tween(o, "z", z, seconds, "smooth");
	}

	public static void rotate(Object3D o, float x, float y, float z, float seconds) {
		if (o == null) return;
		// the shortest way around (350 -> 10 turns 20 degrees, not 340)
		Anim.tween(o, "Rotation X", o.euler.x + delta(o.euler.x, x), seconds, "smooth");
		Anim.tween(o, "Rotation Y", o.euler.y + delta(o.euler.y, y), seconds, "smooth");
		Anim.tween(o, "Rotation Z", o.euler.z + delta(o.euler.z, z), seconds, "smooth");
	}

	public static void scale(Object3D o, float x, float y, float z, float seconds) {
		if (o == null) return;
		Anim.tween(o, "Scale X", x, seconds, "smooth");
		Anim.tween(o, "Scale Y", y, seconds, "smooth");
		Anim.tween(o, "Scale Z", z, seconds, "smooth");
	}

	public static boolean busy(Object3D o) {
		return o != null && Anim.isTweening(o);
	}

	static float delta(float a, float b) {
		return ((b - a) % 360 + 540) % 360 - 180;
	}

	/** Kept for older code: the animation system updates the tweens now. */
	public static void update(float dt) {
	}

	public static void clear() {
	}
}
