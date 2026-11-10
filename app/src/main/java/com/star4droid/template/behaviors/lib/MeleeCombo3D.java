package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
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

@BehaviorInfo(name = "Melee Combo 3D", icon = "sword", category = "Combat", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onAttack(step)", "onHit(target, damage)", "onHeavy()"},
		desc = "Sword / fist fighting for a 3D character: tap attack for a combo of clips (each hit stronger), a heavy attack that hits all around, short lunge forward, hits the enemies in front (by name) that have Health 3D, hit effect, sound and camera shake. Works with Third Person Controller 3D (the character stops walking while it swings).")
public class MeleeCombo3D extends Behavior {
	@Header("Controls")
	@Param(desc = "Attack button (tap = next hit of the combo).")
	public PlayerItem attackButton;
	@Param(desc = "Heavy attack button (hits all around, has a cooldown).")
	public PlayerItem heavyButton;
	@Param(desc = "J = attack, K = heavy attack (desktop, tests).")
	public boolean useKeyboard = true;

	@Header("Targets")
	@Param(desc = "Hurts elements whose name starts with one of these (comma separated, e.g. Slime,Drone,Boss).")
	public String targets = "Enemy";
	@Param(desc = "Reach of a hit in front of the character.", unit = "m", min = 0.3, max = 20, step = 0.1)
	public float range = 2.2f;
	@Param(desc = "Width of the hit zone in front (360 = all around).", unit = "deg", min = 10, max = 360, step = 5)
	public float arc = 140;
	@Param(desc = "Extra heading of the model (same as its controller: 90 for models facing +X).", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	@Header("Combo")
	@Param(desc = "Clips of the combo, in order (comma separated).")
	public String comboClips = "attack1,attack2,attack3";
	@Param(desc = "Damage of each combo hit (comma separated, the last value repeats).")
	public String comboDamage = "20,24,38";
	@Param(desc = "Seconds of one combo hit (the next tap waits for it).", unit = "s", min = 0.1, max = 3, step = 0.05)
	public float hitTime = 0.42f;
	@Param(desc = "When the damage lands, part of the hit time.", min = 0, max = 1, step = 0.05)
	public float hitMoment = 0.45f;
	@Param(desc = "A tap within this time after a hit continues the combo.", unit = "s", min = 0.1, max = 3, step = 0.05)
	public float comboWindow = 0.7f;
	@Param(desc = "Speed of the step forward while hitting.", unit = "m/s", min = 0, max = 20, step = 0.5)
	public float lunge = 2.5f;
	@Param(desc = "Enemies are pushed back at this speed.", unit = "m/s", min = 0, max = 30, step = 0.5)
	public float knockback = 4;
	@Param(desc = "Clip speed.", min = 0.2, max = 4, step = 0.1)
	public float clipSpeed = 1.2f;

	@Header("Heavy attack")
	@Param(desc = "Clip of the heavy attack (empty = no heavy attack).")
	public String heavyClip = "heavy";
	@Param(desc = "Damage of the heavy attack.", min = 0, max = 100000, step = 1)
	public float heavyDamage = 45;
	@Param(desc = "Radius of the heavy attack (all around).", unit = "m", min = 0.5, max = 30, step = 0.1)
	public float heavyRadius = 3.2f;
	@Param(desc = "Seconds of the heavy attack.", unit = "s", min = 0.1, max = 5, step = 0.05)
	public float heavyTime = 0.9f;
	@Param(desc = "Seconds before the heavy attack can be used again.", unit = "s", min = 0, max = 60, step = 0.5)
	public float heavyCooldown = 3;

	@Header("Feedback")
	@Param(desc = "Effect element copied where a hit lands (optional).")
	public PlayerItem hitEffect;
	@Param(desc = "Sound of a swing.")
	public String swingSound = "";
	@Param(desc = "Sound when a hit lands.")
	public String hitSound = "";
	@Param(desc = "Camera shake when a hit lands.", min = 0, max = 3, step = 0.05)
	public float hitShake = 0.15f;

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final ArrayList<Object3D> found = new ArrayList<Object3D>();
	private final Vector3 tmp = new Vector3();
	private EventListener atkL, heavyL;
	private boolean queued, heavyQueued, hitDone, heavy;
	private int step = -1;
	private float t, since = 99, heavyCd, attackLen;
	private String[] clips = new String[0];
	private float[] damages = new float[0];
	private String clipsSrc, dmgSrc;

	@Block(label = "attack", desc = "Next hit of the combo.", labelAr = "اضرب", descAr = "الضربة التالية في السلسلة.", category = "Melee combo", args = {})
	public void attack() {
		queued = true;
	}

	@Block(label = "heavy attack", desc = "Hits all around (cooldown applies).", labelAr = "ضربة قوية", descAr = "يضرب من حوله (مع وقت انتظار).", category = "Melee combo", args = {})
	public void heavyAttack() {
		heavyQueued = true;
	}

	@Block(label = "is attacking", desc = "True while swinging.", labelAr = "يهاجم", descAr = "صح أثناء الضرب.", category = "Melee combo", args = {})
	public boolean isAttacking() {
		return step >= 0 || heavy;
	}

	@Block(label = "heavy attack ready", desc = "0..1, 1 = ready.", labelAr = "جاهزية الضربة القوية", descAr = "0 إلى 1، 1 = جاهزة.", category = "Melee combo", args = {})
	public float getHeavyReady() {
		return heavyCooldown <= 0 ? 1 : 1f - MathUtils.clamp(heavyCd / heavyCooldown, 0, 1);
	}

	@Block(label = "set melee damage", desc = "Multiplies every hit (1 = normal).", labelAr = "اضبط قوة الضرب", descAr = "يضاعف كل ضربة (1 = عادي).", category = "Melee combo", args = {"factor"})
	public void setPower(float k) {
		power = Math.max(0, k);
	}

	private float power = 1;

	@Override
	public void onStart() {
		atkL = Support3D.onTap(attackButton, new Runnable() {
			@Override
			public void run() {
				queued = true;
			}
		});
		heavyL = Support3D.onTap(heavyButton, new Runnable() {
			@Override
			public void run() {
				heavyQueued = true;
			}
		});
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(attackButton, atkL);
		Support3D.removeListener(heavyButton, heavyL);
	}

	@Override
	public void onDisable() {
		end();
		queued = heavyQueued = false;
	}

	private void parse() {
		if (!comboClips.equals(clipsSrc)) {
			clipsSrc = comboClips;
			clips = comboClips.split("\\s*,\\s*");
		}
		if (!comboDamage.equals(dmgSrc)) {
			dmgSrc = comboDamage;
			String[] p = comboDamage.split("\\s*,\\s*");
			damages = new float[p.length];
			for (int i = 0; i < p.length; i++) {
				try {
					damages[i] = Float.parseFloat(p[i]);
				} catch (Exception e) {
					damages[i] = 10;
				}
			}
		}
	}

	private void lock(boolean on) {
		ThirdPersonController3D c = Combat3D.controller(item);
		if (c != null) c.setLocked(on);
	}

	private void end() {
		if (step >= 0 || heavy) lock(false);
		step = -1;
		heavy = false;
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		parse();
		matcher.set(targets);
		if (useKeyboard) {
			if (Support3D.keyJust(Input.Keys.J)) queued = true;
			if (Support3D.keyJust(Input.Keys.K)) heavyQueued = true;
		}
		heavyCd = Math.max(0, heavyCd - dt);
		since += dt;
		Health3D h = item.getBehavior("Health3D") instanceof Health3D ? (Health3D) item.getBehavior("Health3D") : null;
		if (h != null && h.isDead()) {
			end();
			return;
		}
		if (heavy) {
			t += dt;
			if (!hitDone && t >= heavyTime * hitMoment) {
				hitDone = true;
				strike(o, w, heavyRadius, 360, heavyDamage * power);
			}
			if (t >= heavyTime) end();
			queued = false;
			heavyQueued = false;
			return;
		}
		if (heavyQueued && heavyCd <= 0 && heavyClip.length() > 0 && step < 0) {
			heavyQueued = false;
			heavy = true;
			t = 0;
			hitDone = false;
			heavyCd = heavyCooldown;
			lock(true);
			o.playAnimationOnce(heavyClip, clipSpeed);
			if (swingSound.length() > 0) Support3D.play(swingSound, 1, 0.85f, 0);
			fire("onHeavy");
			return;
		}
		heavyQueued = false;
		if (step >= 0) {
			t += dt;
			float fwd = lunge * (1f - MathUtils.clamp(t / attackLen, 0, 1));
			if (fwd > 0.01f && t < attackLen * 0.7f) {
				float yaw = Combat3D.facing(item, modelYawOffset);
				float dx = -MathUtils.sinDeg(yaw) * fwd * dt, dz = -MathUtils.cosDeg(yaw) * fwd * dt;
				// a short step forward unless something blocks
				tmp.set(o.position);
				if (!w.hasPhysics() || !w.physics().raycast(tmp.x, o.getBottomY() + 0.5f, tmp.z, dx, 0, dz, Math.abs(fwd * dt) + 0.4f, o))
					o.setPosition3(o.position.x + dx, o.position.y, o.position.z + dz);
			}
			if (!hitDone && t >= attackLen * hitMoment) {
				hitDone = true;
				float d = damages.length == 0 ? 10 : damages[Math.min(step, damages.length - 1)];
				strike(o, w, range, arc, d * power);
			}
			if (t >= attackLen) {
				lock(false);
				since = 0;
				if (queued && step + 1 < clips.length) {
					startHit(o, step + 1);
				} else {
					step = -1;
				}
				queued = false;
			}
			return;
		}
		if (queued) {
			queued = false;
			startHit(o, since <= comboWindow && lastStep + 1 < clips.length ? lastStep + 1 : 0);
		}
	}

	private int lastStep = -1;

	private void startHit(Object3D o, int s) {
		step = s;
		lastStep = s;
		t = 0;
		hitDone = false;
		attackLen = hitTime * (s == clips.length - 1 && clips.length > 1 ? 1.35f : 1f);
		lock(true);
		if (s < clips.length) o.playAnimationOnce(clips[s], clipSpeed);
		if (swingSound.length() > 0) Support3D.play(swingSound, 1, MathUtils.random(0.95f, 1.1f), 0);
		fire("onAttack", s + 1);
	}

	private void strike(Object3D o, World3D w, float reach, float width, float damage) {
		tmp.set(o.position);
		Combat3D.targets(w, matcher, item, tmp, reach, width, Combat3D.facing(item, modelYawOffset), found);
		boolean any = false;
		for (Object3D t : found) {
			if (Combat3D.hit(t, damage, item, tmp, knockback)) {
				any = true;
				Combat3D.effect(hitEffect, (t.position.x + tmp.x) * 0.5f, t.position.y + 0.2f, (t.position.z + tmp.z) * 0.5f, 0.6f);
				fire("onHit", t, damage);
			}
		}
		if (any) {
			if (hitSound.length() > 0) Support3D.play(hitSound, 1, MathUtils.random(0.9f, 1.1f), 0);
			Camera3D c = mainCamera3d();
			if (c != null && hitShake > 0) c.shake(hitShake);
		}
	}
}
