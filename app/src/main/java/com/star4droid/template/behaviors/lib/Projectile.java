package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Projectile", icon = "bullet", category = "Combat", targets = BehaviorInfo.Target.BOTH,
		events = {"onHit(PlayerItem target)", "onExpire()"},
		desc = "Turns an element into a bullet, arrow or fireball: flies in a direction, hurts what it hits, stops at walls and disappears after its lifetime. Pair it with a Spawner to fire copies.")
public class Projectile extends Behavior {
	@Header("Flight")
	@Param(desc = "Direction: 0 = right, 90 = up, 180 = left, -90 = down.", unit = "deg", min = -360, max = 360)
	public float angle = 0;
	@Param(desc = "Fly where the element points instead (rotate the element or its spawner to aim).")
	public boolean useElementRotation = false;
	@Param(unit = "px/s", min = 0, max = 100000)
	public float speed = 500;
	@Param(desc = "Speeds up (or slows down with a negative value) every second.", unit = "px/s2", min = -100000, max = 100000)
	public float acceleration = 0;
	@Param(desc = "Gravity bends the flight (grenades, arrows). The speed is then only the launch speed.")
	public boolean gravity = false;
	@Param(desc = "Disappears after this long. 0 = never.", unit = "s", min = 0, max = 60, step = 0.1)
	public float lifetime = 3;
	@Param(desc = "Start flying when the element appears. Off: call launch yourself.")
	public boolean autoLaunch = true;

	@Header("Damage")
	@Param(desc = "Elements it hurts. Leave both fields empty to hurt anything that has Health.")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words (separate with commas), e.g. enemy.")
	public String targetPrefix = "";
	@Param(desc = "Hit points taken by one hit.", min = 0.1, max = 100000)
	public float damage = 1;
	@Param(desc = "Give a target without Health this many hit points (0 = leave it alone).", min = 0, max = 100000)
	public float autoHealth = 0;
	@Param(desc = "How many extra targets it can pass through before it stops.", min = 0, max = 100)
	public int pierce = 0;
	@Param(desc = "Push the hit target along the flight, in px/s.", unit = "px/s", min = 0, max = 100000)
	public float knockback = 0;
	@Sound
	@Param(desc = "Played when it hits a target.")
	public String hitSound = "";

	@Header("Obstacles")
	@Param(desc = "Walls / ground that stop it.")
	public List<PlayerItem> obstacles = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words, e.g. wall, ground.")
	public String obstaclePrefix = "";
	@Param(desc = "Disappear when it touches an obstacle.")
	public boolean destroyOnObstacle = true;

	private final GameplaySupport.NameMatcher tMatch = new GameplaySupport.NameMatcher();
	private final GameplaySupport.NameMatcher oMatch = new GameplaySupport.NameMatcher();
	private final GameplaySupport.Contacts contacts = new GameplaySupport.Contacts();
	private float vx, vy, curSpeed, life, dirDeg;
	private int hits;
	private boolean flying, obstacleHit, done, expirePending;

	public void onStart() {
		Body b = body();
		if (b != null && !gravity) b.setGravityScale(0f);
		if (autoLaunch) launchNow();
	}

	@Block(label = "launch", desc = "Starts flying in the given direction (0 = right, 90 = up).", labelAr = "أطلق", descAr = "يبدأ الطيران في الاتجاه المحدد (0 = يمين، 90 = أعلى).", category = "Projectile", args = {"angle"})
	public void launch(float degrees) {
		angle = degrees;
		useElementRotation = false;
		launchNow();
	}

	@Block(label = "set projectile speed", desc = "Speed in px/s (takes effect on the next launch, or now when it is flying without gravity).", labelAr = "اضبط سرعة المقذوف", descAr = "السرعة بالبكسل/ثانية.", category = "Projectile", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
		curSpeed = value;
		if (flying && !gravity) aim();
	}

	@Block(label = "set projectile damage", desc = "Damage of one hit.", labelAr = "اضبط ضرر المقذوف", descAr = "ضرر الضربة الواحدة.", category = "Projectile", args = {"damage"})
	public void setDamage(float value) {
		damage = value;
	}

	@Block(label = "remove projectile", desc = "Makes it disappear now.", labelAr = "أزل المقذوف", descAr = "يجعله يختفي الآن.", category = "Projectile")
	public void expire() {
		expirePending = true;
	}

	@Block(label = "flying direction", desc = "Current direction in degrees (0 = right, 90 = up).", labelAr = "اتجاه الطيران", descAr = "الاتجاه الحالي بالدرجات.", category = "Projectile")
	public float getDirection() {
		return dirDeg;
	}

	private void launchNow() {
		if (useElementRotation) {
			Body b = body();
			dirDeg = b != null ? (float) Math.toDegrees(b.getAngle()) : -actor().getRotation();
		} else {
			dirDeg = angle;
		}
		curSpeed = speed;
		life = lifetime;
		hits = 0;
		done = false;
		flying = true;
		contacts.clear();
		aim();
	}

	private void aim() {
		double r = Math.toRadians(dirDeg);
		vx = (float) Math.cos(r) * curSpeed;
		vy = (float) Math.sin(r) * curSpeed;
		Body b = body();
		if (b != null && b.getType() != BodyDef.BodyType.StaticBody) setVelocity(vx, vy);
	}

	public void onCollisionBegin(PlayerItem other) {
		if (other == null || done) return;
		oMatch.set(obstaclePrefix);
		if (destroyOnObstacle && oMatch.matches(other.getName())) {
			obstacleHit = true;
			return;
		}
		tMatch.set(targetPrefix);
		if (GameplaySupport.noFilter(targets, tMatch) ? GameplaySupport.damageable(other) != null : GameplaySupport.isTarget(other, targets, tMatch))
			contacts.begin(other);
	}

	public void onCollisionEnd(PlayerItem other) {
		contacts.end(other);
	}

	public void onUpdate(float dt) {
		if (expirePending) {
			expirePending = false;
			finish(true);
			return;
		}
		if (!flying || done) return;
		if (lifetime > 0) {
			life -= dt;
			if (life <= 0) {
				finish(true);
				return;
			}
		}
		if (acceleration != 0 && !gravity) {
			curSpeed = Math.max(0f, curSpeed + acceleration * dt);
			aim();
		}
		Body b = body();
		if (b == null || b.getType() == BodyDef.BodyType.StaticBody) {
			setCenter(centerX() + vx * dt, centerY() + vy * dt);
		} else if (!gravity) {
			setVelocity(vx, vy); // keep the exact speed whatever the physics did
		}
		if (destroyOnObstacle && obstacles != null) {
			for (int i = 0; i < obstacles.size(); i++) {
				PlayerItem o = obstacles.get(i);
				if (alive(o) && o != item && overlaps(item, o)) obstacleHit = true;
			}
		}
		if (obstacleHit) {
			finish(false);
			return;
		}
		contacts.prune();
		contacts.poll(item, targets);
		for (int i = 0; i < contacts.size() && !done; i++) {
			if (!contacts.live(i) || !contacts.takeFresh(i)) continue;
			PlayerItem t = contacts.item(i);
			float len = (float) Math.sqrt(vx * vx + vy * vy);
			float kx = len > 0 ? vx / len * knockback : 0, ky = len > 0 ? vy / len * knockback : 0;
			int r = GameplaySupport.hit(t, damage, item, kx, ky, 0, autoHealth);
			if (r == GameplaySupport.HURT || (r == GameplaySupport.NOT_DAMAGEABLE && !GameplaySupport.noFilter(targets, tMatch))) {
				GameplaySupport.sound(stage, hitSound);
				fire("onHit", t);
				hits++;
				if (hits > pierce) finish(false);
			}
		}
	}

	private void finish(boolean expired) {
		if (done) return;
		done = true;
		flying = false;
		if (expired) fire("onExpire");
		GameplaySupport.remove(item);
	}

	public void onDestroy() {
		contacts.clear();
	}
}
