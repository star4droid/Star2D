package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Inventory;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Shooter 3D", icon = "pistol", category = "Combat", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D", "CAMERA3D"},
		events = {"onShoot()", "onHit(PlayerItem target)", "onEmpty()"},
		desc = "Fires when the fire button is tapped or held: instant hits along the aim (HITSCAN) or copies of a bullet template (with Projectile 3D). Aims where the camera looks or along the element's front; fire rate, spread, ammo from the inventory, sounds and camera kick.")
@Exclusive("weapon")
public class Shooter3D extends Behavior {
	public enum Mode { HITSCAN, PROJECTILE }

	@Header("Fire")
	@Param(desc = "2D fire button (any element).")
	public PlayerItem fireButton;
	@Param(desc = "Keeps firing while the button is held.")
	public boolean autoFire = true;
	@Param(desc = "Shots per second.", unit = "1/s", min = 0.1, max = 60, step = 0.1)
	public float fireRate = 4;
	@Param(desc = "Space bar or F fires (desktop, tests).")
	public boolean useKeyboard = true;

	@Header("Aim")
	@Param(desc = "Shoots where the camera looks (first person) instead of along the element's front.")
	public boolean aimWithCamera = true;
	@Param(desc = "Random spread of the shots.", unit = "deg", min = 0, max = 45, step = 0.1)
	public float spread = 1;
	@Param(desc = "Shots start this far in front of the element.", unit = "m", min = 0, max = 10, step = 0.05)
	public float muzzleDistance = 0.6f;
	@Param(desc = "Shots start this high above the element's position.", unit = "m", min = -10, max = 10, step = 0.05)
	public float muzzleHeight = 0;

	@Header("Bullets")
	@Param(desc = "HITSCAN: hits at once along the aim. PROJECTILE: fires copies of the bullet template.")
	public Mode mode = Mode.HITSCAN;
	@Param(desc = "Damage per hit (HITSCAN; projectiles use their own).", min = 0, max = 1000000, step = 1)
	public float damage = 25;
	@Param(desc = "Longest hit distance (HITSCAN).", unit = "m", min = 1, max = 5000, step = 1)
	public float range = 80;
	@Param(desc = "Bullet template copied for each shot (PROJECTILE). It is hidden at start.")
	public PlayerItem bulletTemplate;
	@Param(desc = "Bullet speed (PROJECTILE).", unit = "m/s", min = 0, max = 100000, step = 1)
	public float bulletSpeed = 40;
	@Param(desc = "Element copied at the hit point (HITSCAN: sparks, holes; removed after 2 s).")
	public PlayerItem impactEffect;

	@Header("Ammo and feel")
	@Param(desc = "Inventory item used as ammo, one per shot (\"\" = endless).")
	public String ammoItem = "";
	@Param(desc = "Camera kick per shot.", min = 0, max = 3, step = 0.05)
	public float recoil = 0.15f;
	@Param(desc = "Auto aim: turns to the nearest of these (name prefixes, comma separated) when shooting (empty = off).")
	public String autoAimTargets = "";
	@Param(desc = "Auto aim reach.", unit = "m", min = 1, max = 500, step = 1)
	public float autoAimRange = 20;
	@Param(desc = "Sound of a shot.")
	@Sound
	public String shootSound = "";
	@Param(desc = "Sound when out of ammo.")
	@Sound
	public String emptySound = "";

	private float cool;
	private boolean tapQueued, hidden;
	private EventListener tapL;
	private int shots;
	private final Vector3 o3 = new Vector3(), d3 = new Vector3();

	@Block(label = "shoot", desc = "Fires one shot now (ammo and rate rules apply).", labelAr = "أطلق", descAr = "يطلق طلقة الآن (تطبق قواعد الذخيرة والمعدل).", category = "Shooter 3D", args = {})
	public void shoot() {
		if (cool > 0) return;
		fireNow();
	}

	@Block(label = "set fire rate", desc = "Shots per second.", labelAr = "اضبط معدل الإطلاق", descAr = "طلقات في الثانية.", category = "Shooter 3D", args = {"rate"})
	public void setFireRate(float r) {
		fireRate = Math.max(0.1f, r);
	}

	@Block(label = "set shot damage", desc = "Damage per hit.", labelAr = "اضبط ضرر الطلقة", descAr = "الضرر لكل إصابة.", category = "Shooter 3D", args = {"damage"})
	public void setDamage(float d) {
		damage = Math.max(0, d);
	}

	@Block(label = "shots fired", desc = "How many shots so far.", labelAr = "الطلقات المطلقة", descAr = "عدد الطلقات حتى الآن.", category = "Shooter 3D", args = {})
	public int getShots() {
		return shots;
	}

	@Block(label = "ammo left", desc = "Ammo in the inventory (-1 = endless).", labelAr = "الذخيرة المتبقية", descAr = "الذخيرة في المخزون (-1 = لا نهائية).", category = "Shooter 3D", args = {})
	public int getAmmo() {
		return ammoItem == null || ammoItem.length() == 0 ? -1 : Inventory.count(ammoItem);
	}

	private void aim(World3D w) {
		Camera3D cam = camera3d() != null ? camera3d() : w.activeCamera;
		Object3D o = obj();
		if ((aimWithCamera || o == null) && cam != null) {
			o3.set(cam.cam.position);
			d3.set(cam.cam.direction);
			if (o != null) {
				// start at the gun but hit what the crosshair (screen center) shows
				Ray r = new Ray(new Vector3(cam.cam.position), new Vector3(cam.cam.direction));
				Vector3 aimAt = new Vector3(r.direction).scl(range).add(r.origin);
				if (w.hasPhysics() && w.physics().raycast(r.origin.x, r.origin.y, r.origin.z, r.direction.x, r.direction.y, r.direction.z, range, o)) aimAt.set(w.physics().hitPoint);
				o3.set(o.position).add(0, muzzleHeight, 0).add(o.forward(new Vector3()).scl(muzzleDistance));
				d3.set(aimAt).sub(o3).nor();
			} else o3.add(new Vector3(d3).scl(muzzleDistance)).add(0, muzzleHeight, 0);
		} else if (o != null) {
			o.forward(d3);
			Object3D t = autoAimTargets.length() > 0 ? nearest(w, o) : null;
			if (t != null) {
				float yaw = Support3D.yawOf(t.position.x - o.position.x, t.position.z - o.position.z);
				float turn = Support3D.angleDiff(Support3D.yawOf(d3.x, d3.z), yaw);
				float off = Support3D.angleDiff(Support3D.yawOf(d3.x, d3.z), o.getYaw3());
				com.star4droid.template.behaviors.Combat3D.face(item, o.getYaw3() + turn - off, off);
				o.forward(d3);
			}
			o3.set(o.position).add(0, muzzleHeight, 0).add(new Vector3(d3).scl(muzzleDistance));
			if (t != null) {
				float ty = (t.getBottomY() + t.getTopY()) * 0.5f;
				d3.set(t.position.x - o3.x, ty - o3.y, t.position.z - o3.z).nor();
			}
		}
		if (spread > 0) {
			d3.rotate(Vector3.Y, MathUtils.random(-spread, spread));
			Vector3 side = new Vector3(d3).crs(Vector3.Y);
			if (!side.isZero(0.001f)) d3.rotate(side.nor(), MathUtils.random(-spread, spread));
			d3.nor();
		}
	}

	private final GameplaySupport.NameMatcher aimNames = new GameplaySupport.NameMatcher();
	private final java.util.ArrayList<Object3D> aimFound = new java.util.ArrayList<Object3D>();

	private Object3D nearest(World3D w, Object3D o) {
		aimNames.set(autoAimTargets);
		com.star4droid.template.behaviors.Combat3D.targets(w, aimNames, item, o.position, autoAimRange, 360, 0, aimFound);
		Object3D best = null;
		float bd = Float.MAX_VALUE;
		for (Object3D e : aimFound) {
			float d = e.position.dst2(o.position);
			if (d < bd) {
				bd = d;
				best = e;
			}
		}
		return best;
	}

	private void fireNow() {
		World3D w = world3d();
		if (w == null) return;
		if (ammoItem != null && ammoItem.length() > 0) {
			if (!Inventory.remove(ammoItem, 1)) {
				if (emptySound.length() > 0) Support3D.play(emptySound, 1, 1, 0);
				cool = 1f / Math.max(0.1f, fireRate);
				fire("onEmpty");
				return;
			}
		}
		cool = 1f / Math.max(0.1f, fireRate);
		shots++;
		aim(w);
		if (shootSound.length() > 0) Support3D.play(shootSound, 1, MathUtils.random(0.94f, 1.06f), 0);
		Camera3D cam = w.activeCamera;
		if (recoil > 0 && cam != null) {
			cam.shake(recoil);
			cam.pitch = Math.min(85, cam.pitch + recoil * 4);
		}
		fire("onShoot");
		if (mode == Mode.PROJECTILE) {
			if (!(bulletTemplate instanceof Object3D)) return;
			Object3D t = (Object3D) bulletTemplate;
			PlayerItem c = t.getClone(t.getName() + "_b" + GameplaySupport.nextId());
			if (!(c instanceof Object3D)) return;
			Object3D b = (Object3D) c;
			b.setPosition3(o3.x, o3.y, o3.z);
			b.lookAt(o3.x + d3.x, o3.y + d3.y, o3.z + d3.z);
			b.setVisible(true);
			if (!b.hasBody() && t.d() != null && !"NONE".equals(t.d().Body)) b.setPhysicsEnabled(true);
			Behavior pb = b.getBehavior("Projectile3D");
			if (pb instanceof Projectile3D) ((Projectile3D) pb).launch(d3.x * bulletSpeed, d3.y * bulletSpeed, d3.z * bulletSpeed, item);
			else {
				Projectile3D p = new Projectile3D();
				p.launch(d3.x * bulletSpeed, d3.y * bulletSpeed, d3.z * bulletSpeed, item);
				p.damage = damage;
				b.addBehavior(p);
			}
			return;
		}
		// hitscan
		Object3D self = obj();
		PlayerItem hit = null;
		Vector3 at = null;
		if (w.hasPhysics() && w.physics().raycast(o3.x, o3.y, o3.z, d3.x, d3.y, d3.z, range, self)) {
			hit = w.physics().hitObject;
			at = new Vector3(w.physics().hitPoint);
		}
		Object3D picked = w.pickRay(new Ray(new Vector3(o3), new Vector3(d3)), self);
		if (picked != null && !picked.hasBody() && picked.getBehavior("Health3D") != null && w.pickPoint.dst(o3) <= range
				&& (at == null || w.pickPoint.dst(o3) < at.dst(o3))) {
			hit = picked;
			at = new Vector3(w.pickPoint);
		}
		if (at == null) return;
		if (hit != null && damage > 0) GameplaySupport.hit(hit, damage, item, 0, 0, 0, 0);
		if (impactEffect instanceof Object3D) {
			Object3D fx = (Object3D) impactEffect;
			PlayerItem c = fx.getClone(fx.getName() + "_hit" + GameplaySupport.nextId());
			if (c instanceof Object3D) {
				((Object3D) c).setPosition3(at.x, at.y, at.z);
				((Object3D) c).setVisible(true);
				Lifetime3D.after((Object3D) c, 2f);
			}
		}
		if (hit != null) fire("onHit", hit);
	}

	@Override
	public void onStart() {
		if (fireButton != null) tapL = Support3D.onTap(fireButton, new Runnable() {
			@Override
			public void run() {
				tapQueued = true;
			}
		});
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(fireButton, tapL);
	}

	@Override
	public void onUpdate(float dt) {
		if (!hidden) {
			hidden = true;
			Support3D.setActive3(bulletTemplate, false);
			Support3D.setActive3(impactEffect, false);
		}
		if (cool > 0) cool -= dt;
		boolean held = isPressed(fireButton) || useKeyboard && keyDown(KEY_SPACE, Input.Keys.F);
		if (cool <= 0 && (tapQueued || autoFire && held)) fireNow();
		tapQueued = false;
	}
}
