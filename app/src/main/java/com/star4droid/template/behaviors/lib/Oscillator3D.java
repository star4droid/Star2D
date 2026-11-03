package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Oscillator 3D", icon = "swap-vertical", category = "Motion", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onEnd(boolean atStart)"},
		desc = "Moves back and forth between its start and an offset: sliding walls, pistons, saws, hazards, elevators without riders. Smooth or linear, with a pause at both ends.")
public class Oscillator3D extends Behavior {
	public enum Motion { SMOOTH, LINEAR, SINE }

	@Header("Path")
	@Param(desc = "Offset of the far end on X from the start.", unit = "m", min = -1000, max = 1000, step = 0.1)
	public float offsetX = 0;
	@Param(desc = "Offset of the far end on Y (up).", unit = "m", min = -1000, max = 1000, step = 0.1)
	public float offsetY = 2;
	@Param(desc = "Offset of the far end on Z.", unit = "m", min = -1000, max = 1000, step = 0.1)
	public float offsetZ = 0;
	@Param(desc = "Offset in the element's own axes (turns with it) instead of the world axes.")
	public boolean localOffset = false;

	@Header("Timing")
	@Param(desc = "Seconds from one end to the other.", unit = "s", min = 0.05, max = 120, step = 0.05)
	public float duration = 2;
	@Param(desc = "Wait at each end.", unit = "s", min = 0, max = 60, step = 0.1)
	public float pause = 0.5f;
	@Param(desc = "How it speeds up and slows down.")
	public Motion motion = Motion.SMOOTH;
	@Param(desc = "Starting point of the cycle (0..1) so copies move out of step.", min = 0, max = 1, step = 0.05)
	public float phase = 0;

	private final Vector3 a = new Vector3(), b = new Vector3(), tmp = new Vector3();
	private float t, wait;
	private boolean forward = true, has, paused;

	@Block(label = "set travel time", desc = "Seconds from one end to the other.", labelAr = "اضبط زمن الرحلة", descAr = "ثوانٍ من طرف للآخر.", category = "Oscillator 3D", args = {"seconds"})
	public void setDuration(float s) {
		duration = Math.max(0.05f, s);
	}

	@Block(label = "pause oscillator", desc = "true = stop where it is.", labelAr = "أوقف التذبذب", descAr = "صح = يتوقف في مكانه.", category = "Oscillator 3D", args = {"paused"})
	public void setPaused(boolean on) {
		paused = on;
	}

	@Block(label = "is going out", desc = "True while moving toward the far end.", labelAr = "يتجه للطرف البعيد", descAr = "صح أثناء الحركة نحو الطرف البعيد.", category = "Oscillator 3D", args = {})
	public boolean isGoingOut() {
		return forward;
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o == null) return;
		a.set(o.position);
		if (localOffset) {
			Vector3 off = new Vector3(offsetX, offsetY, offsetZ).mul(o.rotation);
			b.set(a).add(off);
		} else b.set(a).add(offsetX, offsetY, offsetZ);
		t = clamp(phase, 0, 1) * 2f;
		if (t > 1) {
			t = 2 - t;
			forward = false;
		}
		has = true;
	}

	@Override
	public void onDisable() {
		Object3D o = obj();
		if (o != null && has) o.setPosition3(a.x, a.y, a.z);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null || !has || paused) return;
		if (wait > 0) {
			wait -= dt;
			return;
		}
		t += (forward ? 1 : -1) * dt / Math.max(0.05f, duration);
		if (t >= 1 || t <= 0) {
			t = clamp(t, 0, 1);
			forward = !forward;
			wait = pause;
			fire("onEnd", t <= 0);
		}
		float k;
		if (motion == Motion.LINEAR) k = t;
		else if (motion == Motion.SINE) k = 0.5f - 0.5f * MathUtils.cos(t * MathUtils.PI);
		else k = Interpolation.smooth.apply(t);
		tmp.set(a).lerp(b, k);
		o.setPosition3(tmp.x, tmp.y, tmp.z);
	}
}
