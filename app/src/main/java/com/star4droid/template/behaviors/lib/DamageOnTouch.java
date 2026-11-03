package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Damageable;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Damage On Touch", icon = "skull-crossbones", category = "Combat", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onHit(PlayerItem target, float damage)", "onKill(PlayerItem target)"},
		desc = "Spikes, lava, enemies, saws: hurts the elements that touch it (they need a Health or Destructible behavior), once per touch or again and again, with knockback.")
@Requires(body = true, note = "Needs a physical element. Targets are found by contacts, so a Dynamic body must be involved; list targets are also detected by overlap.")
public class DamageOnTouch extends Behavior {
	@Header("Who gets hurt")
	@Param(desc = "Elements that get hurt. Leave both fields empty to hurt anything that has a Health behavior.")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Also hurt elements whose name starts with one of these words (separate with commas), e.g. player, hero.")
	public String targetPrefix = "player";

	@Header("Damage")
	@Param(desc = "Hit points taken away by one hit.", min = 0.1, max = 100000)
	public float damage = 10;
	@Param(desc = "0 = hurt once when the touch starts. Otherwise hurt again this often while the touch lasts (lava, fire).", unit = "s", min = 0, max = 10, step = 0.1)
	public float repeatEvery = 0;
	@Param(desc = "Give a target without Health this many hit points so it can die (0 = leave it alone).", min = 0, max = 100000)
	public float autoHealth = 0;
	@Param(desc = "This element disappears after hurting something (mines, thrown objects).")
	public boolean destroySelfOnHit = false;

	@Header("Reaction")
	@Param(desc = "Push the target away horizontally, in px/s (0 = none).", unit = "px/s", min = 0, max = 100000)
	public float knockback = 0;
	@Param(desc = "Push the target up, in px/s.", unit = "px/s", min = 0, max = 100000)
	public float knockbackUp = 0;
	@Param(desc = "The target's movement behaviors stay off for this long so the push is visible (see Health > Stun behaviors).", unit = "s", min = 0, max = 5, step = 0.05)
	public float stunTime = 0;
	@Sound
	@Param(desc = "Played when it hurts something.")
	public String hitSound = "";

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final GameplaySupport.Contacts contacts = new GameplaySupport.Contacts();
	private boolean used;

	private boolean accepts(PlayerItem o) {
		if (o == null) return false;
		matcher.set(targetPrefix);
		if (GameplaySupport.noFilter(targets, matcher)) return GameplaySupport.damageable(o) != null;
		return GameplaySupport.isTarget(o, targets, matcher);
	}

	@Block(label = "set damage", desc = "Hit points taken by one hit.", labelAr = "اضبط الضرر", descAr = "نقاط الحياة التي تنقص بضربة واحدة.", category = "Damage On Touch", args = {"damage"})
	public void setDamage(float value) {
		damage = value;
	}

	@Block(label = "add target", desc = "This element will be hurt too.", labelAr = "أضف هدفاً", descAr = "سيتأذى هذا العنصر أيضاً.", category = "Damage On Touch", args = {"target"})
	public void addTarget(PlayerItem target) {
		if (target != null && !targets.contains(target)) targets.add(target);
	}

	@Block(label = "hurt now", desc = "Hurts this element immediately with the damage and knockback of this behavior.", labelAr = "أؤذِ الآن", descAr = "يؤذي هذا العنصر فوراً بنفس الضرر والدفع.", category = "Damage On Touch", args = {"target"})
	public void hurt(PlayerItem target) {
		strike(target);
	}

	@Block(label = "damage", desc = "Current damage of one hit.", labelAr = "الضرر", descAr = "ضرر الضربة الواحدة حالياً.", category = "Damage On Touch")
	public float getDamage() {
		return damage;
	}

	public void onCollisionBegin(PlayerItem other) {
		if (accepts(other)) contacts.begin(other);
	}

	public void onCollisionEnd(PlayerItem other) {
		contacts.end(other);
	}

	public void onEnable() {
		contacts.clear();
	}

	private int strike(PlayerItem t) {
		if (!Behavior.alive(t)) return GameplaySupport.NOT_DAMAGEABLE;
		float dx = cx(t) - centerX();
		float side = dx >= 0 ? 1f : -1f;
		int r = GameplaySupport.hit(t, damage, item, side * knockback, knockbackUp, stunTime, autoHealth);
		if (r == GameplaySupport.HURT) {
			GameplaySupport.sound(stage, hitSound);
			fire("onHit", t, damage);
			Damageable d = GameplaySupport.damageable(t);
			if (d != null && d.isDead()) fire("onKill", t);
			if (destroySelfOnHit) used = true;
		}
		return r;
	}

	public void onUpdate(float dt) {
		if (used) {
			used = false;
			GameplaySupport.remove(item);
			return;
		}
		contacts.prune();
		contacts.poll(item, targets);
		for (int i = 0; i < contacts.size(); i++) {
			if (!contacts.live(i)) continue;
			PlayerItem t = contacts.item(i);
			if (contacts.takeFresh(i)) contacts.setTimer(i, 0);
			float left = contacts.timer(i) - dt;
			if (left <= 0) {
				int r = strike(t);
				if (r == GameplaySupport.HURT) left = repeatEvery > 0 ? repeatEvery : 1e9f; // again after the interval / never
				else if (r == GameplaySupport.IGNORED) left = 0; // invulnerable right now: try again next frame while touching
				else left = 1e9f;
			}
			contacts.setTimer(i, left);
			if (used) break;
		}
	}

	public void onDestroy() {
		contacts.clear();
	}
}
