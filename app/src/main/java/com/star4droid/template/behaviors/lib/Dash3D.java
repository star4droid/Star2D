package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Combat3D;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;

@BehaviorInfo(name = "Dash 3D", icon = "run-fast", category = "Movement", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onDash()", "onDashEnd()"},
		desc = "A quick dash / dodge for a 3D character: toward where the joystick points (or forward), stops at walls, follows the ground, can't be hurt while dashing, optional clip, cooldown and damage to enemies it passes through. Works with Third Person Controller 3D.")
public class Dash3D extends Behavior {
	@Header("Controls")
	@Param(desc = "Dash button.")
	public PlayerItem dashButton;
	@Param(desc = "Joystick: the dash goes where it points (empty = forward).")
	public PlayerItem joystick;
	@Param(desc = "Shift / E = dash (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "BACKWARD = a dodge away from where it faces.")
	public boolean backward = false;

	@Header("Dash")
	@Param(desc = "Distance of a dash.", unit = "m", min = 0.5, max = 50, step = 0.5)
	public float distance = 5;
	@Param(desc = "Seconds of a dash.", unit = "s", min = 0.05, max = 2, step = 0.05)
	public float duration = 0.25f;
	@Param(desc = "Seconds before the next dash.", unit = "s", min = 0, max = 30, step = 0.1)
	public float cooldown = 0.9f;
	@Param(desc = "Can't be hurt while dashing (Health 3D).")
	public boolean invincible = true;
	@Param(desc = "Clip played once (empty = none).")
	public String clip = "dash";
	@Param(desc = "Extra heading of the model (same as its controller: 90 for models facing +X).", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	@Header("Dash attack")
	@Param(desc = "Damage to the enemies it passes through (0 = none).", min = 0, max = 100000, step = 1)
	public float damage = 0;
	@Param(desc = "Enemies hurt by the dash (name prefixes, comma separated).")
	public String targets = "Enemy";
	@Param(desc = "Sound of the dash.")
	public String sound = "";

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final ArrayList<Object3D> found = new ArrayList<Object3D>(), hitList = new ArrayList<Object3D>();
	private final Support3D.Mover mover = new Support3D.Mover();
	private final float[] in = new float[2], dir = new float[2];
	private EventListener l;
	private boolean queued, dashing, wasInv;
	private float t, cd, dx, dz;

	@Block(label = "dash", desc = "Dashes now (cooldown applies).", labelAr = "اندفع", descAr = "يندفع الآن (مع وقت انتظار).", category = "Dash 3D", args = {})
	public void dash() {
		queued = true;
	}

	@Block(label = "is dashing", desc = "True during a dash.", labelAr = "يندفع", descAr = "صح أثناء الاندفاع.", category = "Dash 3D", args = {})
	public boolean isDashing() {
		return dashing;
	}

	@Block(label = "dash ready", desc = "0..1, 1 = ready.", labelAr = "جاهزية الاندفاع", descAr = "0 إلى 1، 1 = جاهز.", category = "Dash 3D", args = {})
	public float getReady() {
		return cooldown <= 0 ? 1 : 1f - MathUtils.clamp(cd / cooldown, 0, 1);
	}

	@Override
	public void onStart() {
		l = Support3D.onTap(dashButton, new Runnable() {
			@Override
			public void run() {
				queued = true;
			}
		});
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(dashButton, l);
	}

	@Override
	public void onDisable() {
		if (dashing) stop();
		queued = false;
	}

	private Health3D health() {
		Behavior b = item.getBehavior("Health3D");
		return b instanceof Health3D ? (Health3D) b : null;
	}

	private void stop() {
		dashing = false;
		ThirdPersonController3D c = Combat3D.controller(item);
		if (c != null) c.setLocked(false);
		Health3D h = health();
		if (h != null && invincible && !wasInv) h.setInvincible(false);
		fire("onDashEnd");
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		cd = Math.max(0, cd - dt);
		if (useKeyboard && (Support3D.keyJust(Input.Keys.SHIFT_LEFT) || Support3D.keyJust(Input.Keys.E))) queued = true;
		Health3D h = health();
		if (h != null && h.isDead()) {
			if (dashing) stop();
			return;
		}
		if (queued && !dashing && cd <= 0) start(o);
		queued = false;
		if (!dashing) return;
		t += dt;
		float speed = distance / duration;
		mover.vy = 0;
		mover.grounded = true;
		mover.step(w, item, dx * speed, dz * speed, dt, Support3D.Mover.feetOffset(item, 0), 0.35f, true);
		if (damage > 0) {
			matcher.set(targets);
			Combat3D.targets(w, matcher, item, o.position, 1.2f, 360, 0, found);
			for (Object3D e : found) {
				if (hitList.contains(e)) continue;
				hitList.add(e);
				Combat3D.hit(e, damage, item, o.position, 5);
			}
		}
		if (t >= duration) stop();
	}

	private void start(Object3D o) {
		float yaw = Combat3D.facing(item, modelYawOffset);
		float fx = -MathUtils.sinDeg(yaw), fz = -MathUtils.cosDeg(yaw);
		dx = backward ? -fx : fx;
		dz = backward ? -fz : fz;
		if (joystick != null && !backward) {
			Support3D.readMove(this, joystick, null, null, null, null, false, 0.2f, in);
			if (in[0] * in[0] + in[1] * in[1] > 0.04f) {
				Camera3D c = mainCamera3d();
				Support3D.relative(in[0], in[1], c != null ? Support3D.cameraYaw(c) : yaw, dir);
				float len = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
				if (len > 0.01f) {
					dx = dir[0] / len;
					dz = dir[1] / len;
					Combat3D.face(item, Support3D.yawOf(dx, dz), modelYawOffset);
				}
			}
		}
		dashing = true;
		t = 0;
		cd = cooldown;
		hitList.clear();
		ThirdPersonController3D c = Combat3D.controller(item);
		if (c != null) c.setLocked(true);
		Health3D h = health();
		if (h != null && invincible) {
			wasInv = h.isInvincible();
			h.setInvincible(true);
		}
		if (clip.length() > 0) o.playAnimationOnce(clip, 1.4f);
		if (sound.length() > 0) Support3D.play(sound, 1, 1, 0);
		fire("onDash");
	}
}
