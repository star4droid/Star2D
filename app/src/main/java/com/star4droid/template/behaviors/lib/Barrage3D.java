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

@BehaviorInfo(name = "Barrage 3D", icon = "weather-pouring", category = "Combat", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onBarrage()", "onImpact(x, z)"},
		desc = "A rain of arrows, meteors or grenades on an area: on the nearest enemy (or in front), copies of a template fall from the sky, each hurting what is near where it lands. Button, cooldown, count, area, damage, impact effect.")
public class Barrage3D extends Behavior {
	@Header("Controls")
	@Param(desc = "Button that starts the barrage.")
	public PlayerItem button;
	@Param(desc = "L = barrage (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Seconds before the next barrage.", unit = "s", min = 0, max = 120, step = 0.5)
	public float cooldown = 6;

	@Header("Area")
	@Param(desc = "Aims at the nearest of these (name prefixes); none near = in front.")
	public String targets = "Enemy";
	@Param(desc = "Looks for a target this far.", unit = "m", min = 1, max = 200, step = 1)
	public float range = 18;
	@Param(desc = "Distance in front when there is no target.", unit = "m", min = 0, max = 100, step = 0.5)
	public float forwardDistance = 7;
	@Param(desc = "Radius of the rain.", unit = "m", min = 0.5, max = 50, step = 0.5)
	public float areaRadius = 3.5f;
	@Param(desc = "Extra heading of the model (90 for models facing +X).", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	@Header("Shots")
	@Param(desc = "Element copied for each falling shot (an arrow pointing down, a rock...).")
	public PlayerItem template;
	@Param(desc = "How many fall.", min = 1, max = 200, step = 1)
	public int count = 14;
	@Param(desc = "Seconds over which they fall.", unit = "s", min = 0, max = 10, step = 0.1)
	public float spread = 1.2f;
	@Param(desc = "Start height above the ground.", unit = "m", min = 1, max = 100, step = 0.5)
	public float height = 12;
	@Param(desc = "Falling speed.", unit = "m/s", min = 1, max = 200, step = 1)
	public float fallSpeed = 26;
	@Param(desc = "Damage near each impact.", min = 0, max = 100000, step = 1)
	public float damage = 14;
	@Param(desc = "Hit radius of each impact.", unit = "m", min = 0.1, max = 20, step = 0.1)
	public float impactRadius = 1.4f;
	@Param(desc = "Effect copied at each impact (optional).")
	public PlayerItem impactEffect;
	@Param(desc = "Clip played by the shooter.")
	public String clip = "";
	@Param(desc = "Sound of an impact.")
	public String impactSound = "";

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final ArrayList<Object3D> found = new ArrayList<Object3D>();
	private final ArrayList<Shot> shots = new ArrayList<Shot>();
	private final Vector3 c = new Vector3(), tmp = new Vector3();
	private EventListener l;
	private boolean queued;
	private float cd;

	static final class Shot {
		float x, z, y, delay, ground;
		Object3D o;
	}

	@Block(label = "start barrage", desc = "Rains now (cooldown applies).", labelAr = "ابدأ الوابل", descAr = "يمطر الآن (مع وقت انتظار).", category = "Barrage 3D", args = {})
	public void fireNow() {
		queued = true;
	}

	@Block(label = "barrage ready", desc = "0..1, 1 = ready.", labelAr = "جاهزية الوابل", descAr = "0 إلى 1، 1 = جاهز.", category = "Barrage 3D", args = {})
	public float getReady() {
		return cooldown <= 0 ? 1 : 1f - MathUtils.clamp(cd / cooldown, 0, 1);
	}

	@Override
	public void onStart() {
		l = Support3D.onTap(button, new Runnable() {
			@Override
			public void run() {
				queued = true;
			}
		});
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(button, l);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		cd = Math.max(0, cd - dt);
		if (useKeyboard && Support3D.keyJust(Input.Keys.L)) queued = true;
		if (queued && cd <= 0 && template instanceof Object3D) start(o, w);
		queued = false;
		for (int i = shots.size() - 1; i >= 0; i--) {
			Shot s = shots.get(i);
			if (s.delay > 0) {
				s.delay -= dt;
				if (s.delay <= 0) {
					PlayerItem cl = ((Object3D) template).getClone(((Object3D) template).getName() + "_b" + GameplaySupport.nextId());
					if (cl instanceof Object3D) {
						s.o = (Object3D) cl;
						s.o.setVisible(true);
						s.o.setPosition3(s.x, s.y, s.z);
					}
				}
				continue;
			}
			s.y -= fallSpeed * dt;
			if (s.o != null) s.o.setPosition3(s.x, s.y, s.z);
			if (s.y <= s.ground) {
				matcher.set(targets);
				tmp.set(s.x, s.ground, s.z);
				Combat3D.targets(w, matcher, item, tmp, impactRadius, 360, 0, found);
				for (Object3D e : found) Combat3D.hit(e, damage, item, tmp, 2);
				Combat3D.effect(impactEffect, s.x, s.ground + 0.05f, s.z, 0.8f);
				if (impactSound.length() > 0 && i % 3 == 0) Support3D.playAt(impactSound, w, s.x, s.ground, s.z, 0.7f, 40, MathUtils.random(0.9f, 1.1f));
				fire("onImpact", s.x, s.z);
				if (s.o != null) Lifetime3D.after(s.o, 0.6f);
				shots.remove(i);
			}
		}
	}

	private void start(Object3D o, World3D w) {
		cd = cooldown;
		matcher.set(targets);
		Combat3D.targets(w, matcher, item, o.position, range, 360, 0, found);
		Object3D best = null;
		float bd = Float.MAX_VALUE;
		for (Object3D e : found) {
			float d = e.position.dst2(o.position);
			if (d < bd) {
				bd = d;
				best = e;
			}
		}
		if (best != null) {
			c.set(best.position);
			o.setYaw3(Support3D.yawOf(c.x - o.position.x, c.z - o.position.z) + modelYawOffset);
		} else {
			float yaw = Combat3D.facing(item, modelYawOffset);
			c.set(o.position.x - MathUtils.sinDeg(yaw) * forwardDistance, o.position.y, o.position.z - MathUtils.cosDeg(yaw) * forwardDistance);
		}
		if (clip.length() > 0) o.playAnimationOnce(clip, 1);
		for (int i = 0; i < count; i++) {
			Shot s = new Shot();
			float a = MathUtils.random(MathUtils.PI2), r = (float) Math.sqrt(MathUtils.random()) * areaRadius;
			s.x = c.x + MathUtils.cos(a) * r;
			s.z = c.z + MathUtils.sin(a) * r;
			float g = Support3D.groundY(w, s.x, c.y + 20, s.z, 60, o);
			s.ground = Float.isNaN(g) ? c.y - 0.8f : g;
			s.y = s.ground + height + MathUtils.random(2f);
			s.delay = 0.0001f + spread * i / Math.max(1, count);
			shots.add(s);
		}
		Camera3D cam = mainCamera3d();
		if (cam != null) cam.shake(0.05f);
		fire("onBarrage");
	}
}
