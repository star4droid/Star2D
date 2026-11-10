package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Chase 3D", icon = "ghost", category = "AI", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onSpotted()", "onLost()", "onAttack(PlayerItem target)"},
		desc = "An enemy that sees (range, field of view, walls block the view) or hears the player, runs after it, attacks when close, keeps searching the last seen place for a while, then goes home. Works with Patrol 3D on the same element.")
public class Chase3D extends Behavior {
	@Header("Target")
	@Param(desc = "Who to chase (empty = the main camera: the first-person player).")
	public PlayerItem target;
	@Param(desc = "Chases the nearest living element whose name starts with one of these (comma separated, e.g. Hero for a party of heroes). Replaces Target.")
	public String targetNames = "";

	@Header("Senses")
	@Param(desc = "Sees the target this far.", unit = "m", min = 0, max = 500, step = 0.5)
	public float sightRange = 14;
	@Param(desc = "Width of the view in front of it (360 = all around).", unit = "deg", min = 10, max = 360, step = 5)
	public float fieldOfView = 120;
	@Param(desc = "Walls (physics bodies) block the view.")
	public boolean needLineOfSight = true;
	@Param(desc = "Notices the target this close even behind it or behind walls.", unit = "m", min = 0, max = 100, step = 0.5)
	public float hearRange = 3;
	@Param(desc = "Eyes above its position.", unit = "m", min = -5, max = 10, step = 0.05)
	public float eyeHeight = 1.5f;
	@Param(desc = "Keeps hunting the last seen place this long after losing the target.", unit = "s", min = 0, max = 60, step = 0.5)
	public float searchTime = 4;

	@Header("Chase")
	@Param(desc = "Running speed while chasing.", unit = "m/s", min = 0, max = 100000, step = 0.1)
	public float chaseSpeed = 3.6f;
	@Param(desc = "Turning speed.", unit = "deg/s", min = 30, max = 100000, step = 10)
	public float turnSpeed = 360;
	@Param(desc = "Stops this close to the target.", unit = "m", min = 0, max = 20, step = 0.05)
	public float stopDistance = 1.1f;
	@Param(desc = "Walks back to where it started after giving up.")
	public boolean returnHome = true;
	@Param(desc = "Extra heading when the model faces +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;
	@Param(desc = "Flies this high above the ground (drones, ghosts, bats; 0 = walks).", unit = "m", min = 0, max = 100, step = 0.1)
	public float flyHeight = 0;

	@Header("Attack")
	@Param(desc = "Attacks when the target is this close.", unit = "m", min = 0, max = 50, step = 0.05)
	public float attackRange = 1.5f;
	@Param(desc = "Seconds between two attacks.", unit = "s", min = 0.1, max = 30, step = 0.1)
	public float attackInterval = 1.2f;
	@Param(desc = "Damage to the target's Health 3D per attack (0 = only the event).", min = 0, max = 100000, step = 1)
	public float damage = 10;
	@Param(desc = "Sound when it spots the target.")
	@Sound
	public String spotSound = "";
	@Param(desc = "Sound of an attack.")
	@Sound
	public String attackSound = "";

	@Header("Clips")
	@Param(desc = "Clip while chasing (empty = none).")
	public String runClip = "run";
	@Param(desc = "Clip played once per attack (empty = none).")
	public String attackClip = "attack";
	@Param(desc = "Clip while standing (searching, waiting).")
	public String idleClip = "idle";
	@Param(desc = "Clip while walking home.")
	public String walkClip = "walk";

	private final Support3D.Walker walker = new Support3D.Walker();
	private final Support3D.Clips clips = new Support3D.Clips();
	private final Vector3 me = new Vector3(), tp = new Vector3(), last = new Vector3(), home = new Vector3(), eye = new Vector3();
	private boolean chasing, sees, homeSet, goingHome, forced, blind;
	private float lostFor, cooldown;

	@Block(label = "start chasing", desc = "Chases the target now, seen or not.", labelAr = "ابدأ المطاردة", descAr = "يطارد الهدف الآن سواء رآه أم لا.", category = "Chase 3D", args = {})
	public void startChase() {
		forced = true;
	}

	@Block(label = "stop chasing", desc = "Gives up and goes home (or stands).", labelAr = "أوقف المطاردة", descAr = "يستسلم ويعود لمكانه (أو يقف).", category = "Chase 3D", args = {})
	public void stopChase() {
		forced = false;
		if (chasing) lose();
	}

	@Block(label = "set chase target", desc = "Who to chase.", labelAr = "اضبط هدف المطاردة", descAr = "من يطارد.", category = "Chase 3D", args = {"target"})
	public void setTarget(PlayerItem it) {
		target = it;
	}

	@Block(label = "make blind", desc = "true = cannot see or hear (hiding spots, cut scenes).", labelAr = "اجعله أعمى", descAr = "صح = لا يرى ولا يسمع (أماكن اختباء، مشاهد).", category = "Chase 3D", args = {"blind"})
	public void setBlind(boolean on) {
		blind = on;
	}

	@Block(label = "is chasing", desc = "True while running after the target or searching.", labelAr = "يطارد", descAr = "صح أثناء الجري خلف الهدف أو البحث.", category = "Chase 3D", args = {})
	public boolean isChasing() {
		return chasing;
	}

	@Block(label = "sees target", desc = "True while the target is in sight.", labelAr = "يرى الهدف", descAr = "صح أثناء رؤية الهدف.", category = "Chase 3D", args = {})
	public boolean seesTarget() {
		return sees;
	}

	@Block(label = "distance to chase target", desc = "Meters to the target.", labelAr = "المسافة لهدف المطاردة", descAr = "الأمتار حتى الهدف.", category = "Chase 3D", args = {})
	public float getDistance() {
		return me.dst(tp);
	}

	private boolean canSee(World3D w, Object3D o) {
		if (blind) return false;
		float d = me.dst(tp);
		if (d <= hearRange) return true;
		if (d > sightRange) return false;
		if (fieldOfView < 360) {
			Vector3 f = o.forward(new Vector3());
			if (modelYawOffset != 0) f.rotate(Vector3.Y, modelYawOffset);
			Vector3 to = new Vector3(tp).sub(me);
			to.y = 0;
			f.y = 0;
			if (!to.isZero(0.001f) && !f.isZero(0.001f) && f.nor().dot(to.nor()) < MathUtils.cosDeg(fieldOfView * 0.5f)) return false;
		}
		if (!needLineOfSight) return true;
		eye.set(o.position.x, o.position.y + eyeHeight, o.position.z);
		Vector3 aim = new Vector3(tp.x, tp.y + (target == null ? 0 : 1f), tp.z);
		return !Support3D.blocked(w, eye, aim, o, target);
	}

	private final GameplaySupport.NameMatcher names = new GameplaySupport.NameMatcher();
	private float scanT;

	private Object3D nearestNamed(World3D w, Object3D o) {
		names.set(targetNames);
		Object3D best = null;
		float bd = Float.MAX_VALUE;
		for (int i = 0; i < w.objects.size; i++) {
			Object3D c = w.objects.get(i);
			if (c == o || !c.isVisible() || !names.matches(c.getName()) || !Behavior.alive(c)) continue;
			com.star4droid.template.behaviors.Damageable dmg = GameplaySupport.damageable(c);
			if (dmg != null && dmg.isDead()) continue;
			float d = c.position.dst2(o.position);
			if (d < bd) {
				bd = d;
				best = c;
			}
		}
		return best;
	}

	private void lose() {
		chasing = false;
		goingHome = returnHome;
		fire("onLost");
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		if (!homeSet) {
			homeSet = true;
			home.set(o.position);
		}
		me.set(o.position);
		boolean noneFound = false;
		if (targetNames.length() > 0) {
			scanT -= dt;
			if (scanT <= 0 || target == null || !Support3D.alive3(target) || !(target instanceof Object3D) || !((Object3D) target).isVisible()) {
				scanT = 0.5f;
				target = nearestNamed(w, o);
			}
			noneFound = target == null;
		}
		boolean have = !noneFound && (target == null || Support3D.alive3(target)) && Support3D.posOr(target, w, tp);
		if (have && target == null) tp.y -= 1.5f; // the camera is at eye height: aim at the body
		sees = have && canSee(w, o);
		if (cooldown > 0) cooldown -= dt;
		if ((sees || forced && have) && !chasing) {
			chasing = true;
			goingHome = false;
			fire("onSpotted");
			if (spotSound.length() > 0) Support3D.playAt(spotSound, w, me.x, me.y, me.z, 1, 40, 1);
		}
		if (chasing) {
			if (sees || forced) {
				last.set(tp);
				lostFor = 0;
			} else {
				lostFor += dt;
				if (lostFor >= searchTime) {
					lose();
					return;
				}
			}
			float d = flyHeight > 0 ? (float) Math.sqrt((me.x - tp.x) * (me.x - tp.x) + (me.z - tp.z) * (me.z - tp.z)) : me.dst(tp);
			boolean close = sees && d <= attackRange;
			boolean ground = flyHeight <= 0;
			if (!ground) {
				float g = Support3D.groundY(w, last.x, Math.max(me.y, last.y) + 5, last.z, 80, o);
				last.y = (Float.isNaN(g) ? last.y : g) + flyHeight;
			}
			if (close) {
				walker.stop(w, item, dt, ground, 0.35f);
				o.setYaw3(Support3D.approachAngle(o.getYaw3() - modelYawOffset, Support3D.yawOf(tp.x - me.x, tp.z - me.z), turnSpeed * dt) + modelYawOffset);
				walker.setYaw(o.getYaw3() - modelYawOffset);
				if (cooldown <= 0) {
					cooldown = attackInterval;
					if (attackClip.length() > 0) o.playAnimationOnce(attackClip, 1);
					if (attackSound.length() > 0) Support3D.playAt(attackSound, w, me.x, me.y, me.z, 1, 30, MathUtils.random(0.95f, 1.05f));
					PlayerItem victim = target != null ? target : w.activeCamera;
					if (damage > 0 && victim != null) GameplaySupport.hit(victim, damage, item, 0, 0, 0, 0);
					fire("onAttack", target);
				}
				clips.play(o, idleClip, true, 1, 0.2f);
			} else {
				float left = walker.toward(w, item, last, chaseSpeed, 8, turnSpeed, dt, ground, sees ? stopDistance : 0.2f, modelYawOffset, 0.35f);
				clips.play(o, walker.speedNow > 0.2f ? runClip : idleClip, true, 1, 0.2f);
				if (!sees && left <= 0.4f) walker.stop(w, item, dt, ground, 0.35f);
			}
			return;
		}
		if (goingHome) {
			float left = walker.toward(w, item, home, chaseSpeed * 0.45f, 5, turnSpeed, dt, flyHeight <= 0, 0, modelYawOffset, 0.35f);
			clips.play(o, walkClip, true, 1, 0.25f);
			if (left <= 0.4f) goingHome = false;
			return;
		}
		// idle: only stand still when no other movement behavior (Patrol 3D...) drives the element
		if (Support3D.groupBehaviors(item, "movement", this).isEmpty()) {
			walker.stop(w, item, dt, flyHeight <= 0, 0.35f);
			clips.play(o, idleClip, true, 1, 0.25f);
		}
	}
}
