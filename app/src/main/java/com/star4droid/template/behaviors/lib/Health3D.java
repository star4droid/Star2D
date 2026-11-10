package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Items.ProgressItem;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Damageable;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Health 3D", icon = "heart-flash", category = "Combat", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D", "CAMERA3D"},
		events = {"onDamage(float amount, PlayerItem source)", "onHeal(float amount)", "onDeath()"},
		desc = "Hit points for 3D players, enemies and breakable things: takes damage from Chase 3D, Shooter 3D and Projectile 3D, flashes invincible after a hit, can regenerate, shows a 2D bar, and on death plays a clip, hides or is removed.")
public class Health3D extends Behavior implements Damageable {
	public enum OnDeath { NOTHING, HIDE, DESTROY, PLAY_CLIP }

	@Header("Hit points")
	@Param(desc = "Most hit points.", min = 1, max = 1000000, step = 1)
	public float maxHealth = 100;
	@Param(desc = "Hit points at start (0 = full).", min = 0, max = 1000000, step = 1)
	public float startHealth = 0;
	@Param(desc = "Cannot be hurt again for this long after a hit.", unit = "s", min = 0, max = 10, step = 0.05)
	public float invincibleTime = 0.5f;
	@Param(desc = "Hit points healed per second (0 = none).", min = 0, max = 10000, step = 0.5)
	public float regenPerSecond = 0;
	@Param(desc = "Healing waits this long after the last hit.", unit = "s", min = 0, max = 60, step = 0.5)
	public float regenDelay = 4;

	@Header("Display")
	@Param(desc = "2D progress bar showing the health (optional).")
	public ProgressItem healthBar;
	@Param(desc = "2D text showing the hit points (optional).")
	public PlayerItem healthText;
	@Param(desc = "Shows the maximum after the hit points, like 100 / 100.")
	public boolean showMax = false;
	@Param(desc = "Shakes the camera when the camera's player is hurt.", min = 0, max = 5, step = 0.1)
	public float hurtShake = 0.6f;

	@Header("Reaction")
	@Param(desc = "Pushed away from the attacker at this speed (DYNAMIC bodies).", unit = "m/s", min = 0, max = 100000, step = 0.5)
	public float knockback = 0;
	@Param(desc = "Clip played once when hurt (empty = none).")
	public String hurtClip = "";
	@Param(desc = "Sound when hurt.")
	@Sound
	public String hurtSound = "";

	@Header("Death")
	@Param(desc = "What happens at 0 hit points.")
	public OnDeath onDeath = OnDeath.PLAY_CLIP;
	@Param(desc = "Clip for PLAY_CLIP (\"death\", \"die\").")
	public String deathClip = "death";
	@Param(desc = "Removes / hides the element this long after death (0 = stays, for PLAY_CLIP).", unit = "s", min = 0, max = 60, step = 0.5)
	public float removeAfter = 0;
	@Param(desc = "Sound of death.")
	@Sound
	public String deathSound = "";

	private float hp, inv, sinceHit = 99, deadFor;
	private boolean dead, init, removed;
	private PlayerItem attacker;

	// ------------------------------------------------------------ blocks

	@Block(label = "hurt", desc = "Takes this much damage.", labelAr = "اجرح", descAr = "يتلقى هذا القدر من الضرر.", category = "Health 3D", args = {"amount"})
	public void damage(float amount) {
		takeDamage(amount, null, 0, 0, 0);
	}

	@Block(label = "heal", desc = "Gets hit points back (not above the most).", labelAr = "عالِج", descAr = "يستعيد نقاط الحياة (حتى الحد الأقصى).", category = "Health 3D", args = {"amount"})
	public void heal(float amount) {
		ensure();
		if (dead || amount <= 0) return;
		float before = hp;
		hp = Math.min(maxHealth, hp + amount);
		if (hp > before) fire("onHeal", hp - before);
		show();
	}

	@Block(label = "kill", desc = "Health to 0 now.", labelAr = "اقتل", descAr = "الحياة إلى 0 الآن.", category = "Health 3D", args = {})
	public void kill() {
		ensure();
		if (dead) return;
		hp = 0;
		die();
	}

	@Block(label = "revive", desc = "Back to life with full health.", labelAr = "أحيِ", descAr = "يعود للحياة بصحة كاملة.", category = "Health 3D", args = {})
	public void revive() {
		ensure();
		dead = false;
		removed = false;
		deadFor = 0;
		hp = maxHealth;
		Object3D o = obj();
		if (o != null) {
			o.setVisible(true);
			o.stopAnimation();
		}
		show();
	}

	@Block(label = "set max health", desc = "Most hit points (health is kept in range).", labelAr = "اضبط أقصى صحة", descAr = "أقصى نقاط حياة (تبقى الصحة ضمن المدى).", category = "Health 3D", args = {"max"})
	public void setMaxHealth(float m) {
		ensure();
		maxHealth = Math.max(1, m);
		hp = Math.min(hp, maxHealth);
		show();
	}

	@Block(label = "set invincible", desc = "true = takes no damage.", labelAr = "اجعله منيعاً", descAr = "صح = لا يتلقى ضرراً.", category = "Health 3D", args = {"on"})
	public void setInvincible(boolean on) {
		inv = on ? Float.MAX_VALUE : 0;
	}

	/** True while set invincible by a block / a dash (not the short time after a hit). */
	public boolean isInvincible() {
		return inv > 1e30f;
	}

	@Block(label = "health", desc = "Hit points now.", labelAr = "الصحة", descAr = "نقاط الحياة الآن.", category = "Health 3D", args = {})
	public float getHealth() {
		ensure();
		return hp;
	}

	@Block(label = "health percent", desc = "0..100.", labelAr = "نسبة الصحة", descAr = "0..100.", category = "Health 3D", args = {})
	public float getHealthPercent() {
		ensure();
		return hp * 100f / Math.max(1, maxHealth);
	}

	@Block(label = "is dead", desc = "True at 0 hit points.", labelAr = "ميت", descAr = "صح عند 0 نقاط حياة.", category = "Health 3D", args = {})
	public boolean isDead() {
		return dead;
	}

	@Block(label = "last attacker", desc = "Who hurt it last (may be empty).", labelAr = "آخر مهاجم", descAr = "من جرحه آخر مرة (قد يكون فارغاً).", category = "Health 3D", args = {})
	public PlayerItem getAttacker() {
		return attacker;
	}

	// ------------------------------------------------------------ Damageable

	@Override
	public boolean takeDamage(float amount, PlayerItem source, float pushX, float pushY, float stunSeconds) {
		ensure();
		if (dead || !enabled || amount <= 0 || inv > 0) return false;
		hp = Math.max(0, hp - amount);
		inv = invincibleTime;
		sinceHit = 0;
		attacker = source;
		fire("onDamage", amount, source);
		Object3D o = obj();
		if (hurtSound.length() > 0) Support3D.play(hurtSound, 1, MathUtils.random(0.95f, 1.05f), 0);
		if (o != null && hurtClip.length() > 0 && hp > 0) o.playAnimationOnce(hurtClip, 1);
		Camera3D cam = mainCamera3d();
		if (hurtShake > 0 && cam != null && (item == cam || cam.target == item)) cam.shake(hurtShake);
		if (knockback > 0 && o != null && o.isDynamic()) {
			Vector3 from = new Vector3();
			if (Behavior.pos3(source, from)) {
				Vector3 d = new Vector3(o.position).sub(from);
				d.y = 0;
				if (!d.isZero(0.001f)) {
					d.nor().scl(knockback);
					o.setVelocity3(d.x, Math.max(o.getVelocityY3(), knockback * 0.3f), d.z);
				}
			}
		}
		show();
		if (hp <= 0) die();
		return true;
	}

	// ------------------------------------------------------------ engine

	private void ensure() {
		if (init) return;
		init = true;
		hp = startHealth > 0 ? Math.min(startHealth, maxHealth) : maxHealth;
	}

	private void show() {
		if (healthBar != null) {
			healthBar.setMax(100);
			healthBar.setProgress(Math.round(getHealthPercent()));
		}
		if (healthText != null) healthText.setItemText(showMax ? (int) Math.ceil(hp) + " / " + (int) Math.ceil(maxHealth) : String.valueOf((int) Math.ceil(hp)));
	}

	private void die() {
		dead = true;
		deadFor = 0;
		if (deathSound.length() > 0) Support3D.play(deathSound, 1, 1, 0);
		Object3D o = obj();
		if (o != null && onDeath == OnDeath.PLAY_CLIP && deathClip.length() > 0) o.playAnimation(deathClip, false, 1);
		for (Behavior b : Support3D.groupBehaviors(item, "movement", this)) b.setEnabled(false);
		Behavior ch = item.getBehavior("Chase3D");
		if (ch != null) ch.setEnabled(false);
		fire("onDeath");
		if (onDeath == OnDeath.HIDE && removeAfter <= 0) Support3D.setActive3(item, false);
		if (onDeath == OnDeath.DESTROY && removeAfter <= 0) destroyNow();
	}

	private void destroyNow() {
		if (removed) return;
		removed = true;
		Object3D o = obj();
		if (o != null) o.destroy();
	}

	@Override
	public void onStart() {
		ensure();
		show();
	}

	@Override
	public void onUpdate(float dt) {
		ensure();
		if (inv > 0 && inv != Float.MAX_VALUE) inv = Math.max(0, inv - dt);
		if (dead) {
			deadFor += dt;
			if (removeAfter > 0 && deadFor >= removeAfter && !removed) {
				if (onDeath == OnDeath.DESTROY) destroyNow();
				else if (onDeath != OnDeath.NOTHING) {
					removed = true;
					Support3D.setActive3(item, false);
				}
			}
			return;
		}
		sinceHit += dt;
		if (regenPerSecond > 0 && sinceHit >= regenDelay && hp < maxHealth) {
			hp = Math.min(maxHealth, hp + regenPerSecond * dt);
			show();
		}
	}
}
