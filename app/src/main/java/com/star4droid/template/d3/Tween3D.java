package com.star4droid.template.d3;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** Smooth moves / turns / scales of 3D elements over time (blocks "move to in seconds"...). Updated by World3D. */
public final class Tween3D {
	private Tween3D() {
	}

	static final class T {
		Object3D o;
		int kind; // 0 move, 1 rotate, 2 scale
		final Vector3 from = new Vector3(), to = new Vector3();
		float t, dur;
	}

	static final Array<T> ALL = new Array<T>();

	private static T start(Object3D o, int kind, float seconds) {
		if (o == null) return null;
		for (int i = ALL.size - 1; i >= 0; i--) if (ALL.get(i).o == o && ALL.get(i).kind == kind) ALL.removeIndex(i);
		T t = new T();
		t.o = o;
		t.kind = kind;
		t.dur = Math.max(0.0001f, seconds);
		ALL.add(t);
		return t;
	}

	public static void move(Object3D o, float x, float y, float z, float seconds) {
		T t = start(o, 0, seconds);
		if (t == null) return;
		t.from.set(o.position);
		t.to.set(x, y, z);
	}

	public static void rotate(Object3D o, float x, float y, float z, float seconds) {
		T t = start(o, 1, seconds);
		if (t == null) return;
		t.from.set(o.euler);
		t.to.set(x, y, z);
	}

	public static void scale(Object3D o, float x, float y, float z, float seconds) {
		T t = start(o, 2, seconds);
		if (t == null) return;
		t.from.set(o.scale);
		t.to.set(x, y, z);
	}

	public static boolean busy(Object3D o) {
		for (T t : ALL) if (t.o == o) return true;
		return false;
	}

	static float angle(float a, float b, float k) {
		float d = ((b - a) % 360 + 540) % 360 - 180;
		return a + d * k;
	}

	public static void update(float dt) {
		for (int i = ALL.size - 1; i >= 0; i--) {
			T t = ALL.get(i);
			if (t.o.isDestroyed()) {
				ALL.removeIndex(i);
				continue;
			}
			t.t += dt;
			float k = Interpolation.smooth.apply(Math.min(1f, t.t / t.dur));
			if (t.kind == 0) t.o.setPosition3(MathUtils.lerp(t.from.x, t.to.x, k), MathUtils.lerp(t.from.y, t.to.y, k), MathUtils.lerp(t.from.z, t.to.z, k));
			else if (t.kind == 1) t.o.setRotation3(angle(t.from.x, t.to.x, k), angle(t.from.y, t.to.y, k), angle(t.from.z, t.to.z, k));
			else t.o.setScale3(MathUtils.lerp(t.from.x, t.to.x, k), MathUtils.lerp(t.from.y, t.to.y, k), MathUtils.lerp(t.from.z, t.to.z, k));
			if (t.t >= t.dur) ALL.removeIndex(i);
		}
	}

	public static void clear() {
		ALL.clear();
	}
}
