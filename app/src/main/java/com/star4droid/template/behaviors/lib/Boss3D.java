package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Combat3D;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;

@BehaviorInfo(name = "Boss 3D", icon = "ogre", category = "AI", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onLanded()", "onPunch()", "onSlam()", "onLeap()", "onPhase2()"},
		desc = "A big boss: can fall from the sky into the arena, walks to the player, punches up close, slams the ground sending shockwave rings the player must jump over, leaps onto the player from far, and gets faster and angrier under half health. Needs Health 3D; rings are copies of a ring element (a flat Torus).")
@Exclusive("movement")
public class Boss3D extends Behavior {
	@Header("Targets")
	@Param(desc = "Who it fights: name prefixes (comma separated). The nearest living one is chased.")
	public String targets = "Player";
	@Param(desc = "Starts fighting by itself when a target comes this close (0 = only with the blocks).", unit = "m", min = 0, max = 200, step = 1)
	public float wakeDistance = 0;
	@Param(desc = "Extra heading of the model (90 for models facing +X).", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	@Header("Moves")
	@Param(desc = "Walking speed.", unit = "m/s", min = 0, max = 50, step = 0.1)
	public float walkSpeed = 2.2f;
	@Param(desc = "Punches when the target is this close.", unit = "m", min = 0.5, max = 20, step = 0.1)
	public float punchRange = 3.2f;
	@Param(desc = "Damage of a punch.", min = 0, max = 100000, step = 1)
	public float punchDamage = 18;
	@Param(desc = "Damage of a shockwave ring.", min = 0, max = 100000, step = 1)
	public float waveDamage = 20;
	@Param(desc = "Rings sent by one ground slam.", min = 1, max = 10, step = 1)
	public int waveCount = 3;
	@Param(desc = "Speed of the rings.", unit = "m/s", min = 1, max = 60, step = 0.5)
	public float waveSpeed = 8;
	@Param(desc = "Rings vanish at this radius.", unit = "m", min = 2, max = 100, step = 1)
	public float waveRadius = 16;
	@Param(desc = "Seconds between two ground slams.", unit = "s", min = 1, max = 60, step = 0.5)
	public float slamCooldown = 7;
	@Param(desc = "Damage of landing on the target after a leap.", min = 0, max = 100000, step = 1)
	public float leapDamage = 25;
	@Param(desc = "Seconds between two leaps (0 = never leaps).", unit = "s", min = 0, max = 60, step = 0.5)
	public float leapCooldown = 10;
	@Param(desc = "Ring element copied for the shockwaves (a flat Torus, hidden; optional).")
	public PlayerItem ringTemplate;
	@Param(desc = "Effect copied where it lands (dust; optional).")
	public PlayerItem landEffect;

	@Header("Clips")
	@Param(desc = "Clip name (part of the name is enough).")
	public String idleClip = "idle";
	@Param(desc = "Clip name (part of the name is enough).")
	public String walkClip = "walk";
	@Param(desc = "Clip name (part of the name is enough).")
	public String punchClip = "punch";
	@Param(desc = "Clip name (part of the name is enough).")
	public String slamClip = "slam";
	@Param(desc = "Clip name (part of the name is enough).")
	public String jumpClip = "jump";
	@Param(desc = "Clip name (part of the name is enough).")
	public String landClip = "land";
	@Param(desc = "Clip name (part of the name is enough).")
	public String angryClip = "angry";
	@Param(desc = "Sound of a slam / landing.")
	public String slamSound = "";
	@Param(desc = "Sound of a punch.")
	public String punchSound = "";

	static final int OFF = 0, DROP = 1, ROAR = 2, CHASE = 3, PUNCH = 4, SLAM = 5, LEAP = 6, DEAD = 7;
	private int state = OFF;
	private float t, slamCd = 3, leapCd = 5, vy, speedK = 1, cdK = 1;
	private boolean hitDone, phase2;
	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final ArrayList<Object3D> found = new ArrayList<Object3D>();
	private final Support3D.Walker walker = new Support3D.Walker();
	private final Support3D.Clips clips = new Support3D.Clips();
	private final Vector3 tp = new Vector3(), from = new Vector3(), to = new Vector3();
	private Object3D focus;
	private final ArrayList<Wave> waves = new ArrayList<Wave>();

	static final class Wave {
		float x, y, z, r, delay;
		Object3D ring;
		final ArrayList<Object3D> hit = new ArrayList<Object3D>();
	}

	@Block(label = "boss drop in", desc = "Falls from this height onto the ground, roars, then fights.", labelAr = "أسقط الزعيم", descAr = "يسقط من هذا الارتفاع إلى الأرض ثم يزأر ويقاتل.", category = "Boss 3D", args = {"height"})
	public void dropIn(float height) {
		Object3D o = obj();
		if (o == null) return;
		o.setVisible(true);
		o.setPosition3(o.position.x, o.position.y + Math.max(1, height), o.position.z);
		state = DROP;
		vy = 0;
		t = 0;
		clips.play(o, jumpClip, true, 1, 0.1f);
	}

	@Block(label = "boss fight", desc = "true = fights now, false = stands still.", labelAr = "قتال الزعيم", descAr = "صح = يقاتل الآن، خطأ = يقف.", category = "Boss 3D", args = {"on"})
	public void setFighting(boolean on) {
		if (state == DEAD) return;
		state = on ? CHASE : OFF;
	}

	@Block(label = "boss is fighting", desc = "True while it fights.", labelAr = "الزعيم يقاتل", descAr = "صح أثناء القتال.", category = "Boss 3D", args = {})
	public boolean isFighting() {
		return state >= CHASE && state < DEAD;
	}

	private Health3D health() {
		Behavior b = item.getBehavior("Health3D");
		return b instanceof Health3D ? (Health3D) b : null;
	}

	private void pickFocus(World3D w, Object3D o) {
		matcher.set(targets);
		focus = null;
		float best = Float.MAX_VALUE;
		for (int i = 0; i < w.objects.size; i++) {
			Object3D c = w.objects.get(i);
			if (c == o || !c.isVisible() || !matcher.matches(c.getName()) || !Behavior.alive(c)) continue;
			Health3D h = c.getBehavior("Health3D") instanceof Health3D ? (Health3D) c.getBehavior("Health3D") : null;
			if (h != null && h.isDead()) continue;
			float d = c.position.dst2(o.position);
			if (d < best) {
				best = d;
				focus = c;
			}
		}
	}

	private void face(Object3D o, float dt, float speed) {
		if (focus == null) return;
		float yaw = Support3D.yawOf(focus.position.x - o.position.x, focus.position.z - o.position.z);
		o.setYaw3(Support3D.approachAngle(o.getYaw3(), yaw + modelYawOffset, speed * dt));
		walker.setYaw(o.getYaw3() - modelYawOffset);
	}

	private void shake(float s) {
		Camera3D c = mainCamera3d();
		if (c != null) c.shake(s);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		updateWaves(w, o, dt);
		Health3D h = health();
		if (h != null && h.isDead()) {
			state = DEAD;
			return;
		}
		pickFocus(w, o);
		if (state == OFF) {
			if (wakeDistance > 0 && focus != null && focus.position.dst(o.position) < wakeDistance) state = CHASE;
			else {
				walker.stop(w, item, dt, true, 0.8f);
				clips.play(o, idleClip, true, 1, 0.2f);
				return;
			}
		}
		if (!phase2 && h != null && h.getHealthPercent() < 50) {
			phase2 = true;
			speedK = 1.3f;
			cdK = 0.65f;
			o.playAnimationOnce(angryClip, 1);
			fire("onPhase2");
		}
		slamCd -= dt;
		leapCd -= dt;
		t += dt;
		switch (state) {
			case DROP: {
				vy -= 30 * dt;
				float ny = o.position.y + vy * dt;
				float g = Support3D.groundY(w, o.position.x, o.position.y + 0.5f, o.position.z, 200, o);
				float feet = Support3D.Mover.feetOffset(item, 0);
				if (!Float.isNaN(g) && ny - feet <= g) {
					o.setPosition3(o.position.x, g + feet, o.position.z);
					landed(o, w, 0);
					state = ROAR;
					t = 0;
					o.playAnimationOnce(landClip, 1);
					fire("onLanded");
				} else o.setPosition3(o.position.x, ny, o.position.z);
				break;
			}
			case ROAR:
				if (t > 1.2f && t - dt <= 1.2f) o.playAnimationOnce(angryClip, 1);
				if (t > 2.4f) state = CHASE;
				clips.play(o, idleClip, true, 1, 0.2f);
				walker.stop(w, item, dt, true, 0.8f);
				break;
			case CHASE: {
				if (focus == null) {
					walker.stop(w, item, dt, true, 0.8f);
					clips.play(o, idleClip, true, 1, 0.2f);
					break;
				}
				float d = focus.position.dst(o.position);
				if (d < punchRange) {
					start(PUNCH, punchClip);
					break;
				}
				if (slamCd <= 0 && d < waveRadius * 0.7f) {
					start(SLAM, slamClip);
					break;
				}
				if (leapCooldown > 0 && leapCd <= 0 && d > 8) {
					start(LEAP, jumpClip);
					from.set(o.position);
					// lands just short of the target (on it, the ground check would put the boss on the target's head)
					to.set(o.position).sub(focus.position);
					to.y = 0;
					if (to.isZero(0.001f)) to.set(0, 0, 1);
					to.nor().scl(Math.max(1.2f, punchRange * 0.7f)).add(focus.position);
					break;
				}
				tp.set(focus.position);
				walker.toward(w, item, tp, walkSpeed * speedK, 4, 200 * speedK, dt, true, punchRange * 0.7f, modelYawOffset, 0.8f);
				clips.play(o, walker.speedNow > 0.2f ? walkClip : idleClip, true, Math.max(0.7f, speedK), 0.2f);
				break;
			}
			case PUNCH:
				face(o, dt, 360);
				walker.stop(w, item, dt, true, 0.8f);
				if (!hitDone && t > 0.45f / speedK) {
					hitDone = true;
					matcher.set(targets);
					Combat3D.targets(w, matcher, item, o.position, punchRange + 0.6f, 120, o.getYaw3() - modelYawOffset, found);
					for (Object3D e : found) Combat3D.hit(e, punchDamage, item, o.position, 8);
					if (punchSound.length() > 0) Support3D.play(punchSound, 1, 1, 0);
					shake(0.25f);
					fire("onPunch");
				}
				if (t > 1.1f / speedK) state = CHASE;
				break;
			case SLAM:
				face(o, dt, 90);
				walker.stop(w, item, dt, true, 0.8f);
				if (!hitDone && t > 1.0f / speedK) {
					hitDone = true;
					for (int i = 0; i < waveCount; i++) addWave(o, i * 0.55f / speedK);
					if (slamSound.length() > 0) Support3D.play(slamSound, 1, 1, 0);
					shake(0.6f);
					fire("onSlam");
				}
				if (t > 2.2f / speedK) {
					slamCd = slamCooldown * cdK;
					state = CHASE;
				}
				break;
			case LEAP: {
				float dur = 1.15f;
				float k = MathUtils.clamp(t / dur, 0, 1);
				float x = MathUtils.lerp(from.x, to.x, k), z = MathUtils.lerp(from.z, to.z, k);
				float g = Support3D.groundY(w, x, Math.max(from.y, to.y) + 10, z, 60, o);
				float feet = Support3D.Mover.feetOffset(item, 0);
				float base = Float.isNaN(g) ? MathUtils.lerp(from.y, to.y, k) : g + feet;
				o.setPosition3(x, base + 4f * 4 * k * (1 - k), z);
				face(o, dt, 360);
				if (k >= 1) {
					landed(o, w, leapDamage);
					o.playAnimationOnce(landClip, 1.3f);
					leapCd = leapCooldown * cdK;
					state = CHASE;
					fire("onLeap");
				}
				break;
			}
			default:
				break;
		}
	}

	private void start(int s, String clip) {
		state = s;
		t = 0;
		hitDone = false;
		Object3D o = obj();
		if (o != null && clip.length() > 0) o.playAnimationOnce(clip, speedK);
	}

	private void landed(Object3D o, World3D w, float damage) {
		shake(0.9f);
		if (slamSound.length() > 0) Support3D.play(slamSound, 1, 0.8f, 0);
		Combat3D.effect(landEffect, o.position.x, o.getBottomY() + 0.1f, o.position.z, 1.5f);
		if (damage > 0) {
			matcher.set(targets);
			Combat3D.targets(w, matcher, item, o.position, 3.5f, 360, 0, found);
			for (Object3D e : found) Combat3D.hit(e, damage, item, o.position, 10);
		}
		addWave(o, 0);
	}

	private void addWave(Object3D o, float delay) {
		Wave wv = new Wave();
		wv.x = o.position.x;
		wv.z = o.position.z;
		wv.y = o.getBottomY();
		wv.r = 0.5f;
		wv.delay = delay;
		waves.add(wv);
	}

	private void updateWaves(World3D w, Object3D o, float dt) {
		for (int i = waves.size() - 1; i >= 0; i--) {
			Wave wv = waves.get(i);
			if (wv.delay > 0) {
				wv.delay -= dt;
				continue;
			}
			if (wv.ring == null && ringTemplate instanceof Object3D) {
				PlayerItem c = ((Object3D) ringTemplate).getClone(((Object3D) ringTemplate).getName() + "_w" + GameplaySupport.nextId());
				if (c instanceof Object3D) {
					wv.ring = (Object3D) c;
					wv.ring.setVisible(true);
				}
			}
			wv.r += waveSpeed * speedK * dt;
			if (wv.ring != null) {
				float base = Math.max(0.01f, ((Object3D) ringTemplate).getScaleX3());
				float s = wv.r * 2f / Math.max(0.01f, ((Object3D) ringTemplate).getSizeX() / base);
				wv.ring.setScale3(s, ((Object3D) ringTemplate).getScaleY3(), s);
				wv.ring.setPosition3(wv.x, wv.y + 0.15f, wv.z);
			}
			// hurts the targets standing on the ground where the ring passes (jump over it)
			matcher.set(targets);
			for (int k = 0; k < w.objects.size; k++) {
				Object3D e = w.objects.get(k);
				if (e == o || !e.isVisible() || !matcher.matches(e.getName()) || wv.hit.contains(e)) continue;
				float d = (float) Math.sqrt((e.position.x - wv.x) * (e.position.x - wv.x) + (e.position.z - wv.z) * (e.position.z - wv.z));
				if (Math.abs(d - wv.r) < 0.7f && e.getBottomY() < wv.y + 0.9f && Combat3D.grounded(e, w)) {
					wv.hit.add(e);
					Combat3D.hit(e, waveDamage, item, tp.set(wv.x, wv.y, wv.z), 7);
				}
			}
			if (wv.r > waveRadius) {
				if (wv.ring != null) wv.ring.destroy();
				waves.remove(i);
			}
		}
	}
}
