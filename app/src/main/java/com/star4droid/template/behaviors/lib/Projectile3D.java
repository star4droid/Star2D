package com.star4droid.template.behaviors.lib;

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

@BehaviorInfo(name = "Projectile 3D", icon = "arrow-projectile", category = "Combat", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onHit(PlayerItem target)", "onExpire()"},
		desc = "A bullet, arrow, fireball or thrown ball: flies forward (its -Z) with optional gravity, never tunnels through thin walls, hurts what it hits (Health 3D), shows an impact effect and disappears. Put it on the template that Shooter 3D or Spawner 3D copies.")
public class Projectile3D extends Behavior {
	@Header("Flight")
	@Param(desc = "Speed along its front at start (Shooter 3D sets its own).", unit = "m/s", min = 0, max = 100000, step = 0.5)
	public float speed = 30;
	@Param(desc = "How much gravity pulls it down (0 = straight, 1 = a thrown ball).", min = 0, max = 10, step = 0.05)
	public float gravity = 0;
	@Param(desc = "Disappears after this many seconds.", unit = "s", min = 0.05, max = 120, step = 0.05)
	public float lifetime = 3;
	@Param(desc = "Turns to face where it flies (arrows).")
	public boolean faceVelocity = true;

	@Header("Hit")
	@Param(desc = "Damage to the Health 3D of what it hits.", min = 0, max = 1000000, step = 1)
	public float damage = 10;
	@Param(desc = "Only hurts elements whose name starts with this (empty = anything with health).")
	public String targetPrefix = "";
	@Param(desc = "Disappears when it hits something.")
	public boolean destroyOnHit = true;
	@Param(desc = "Element copied at the hit point (sparks, decal; removed after 2 s). Optional.")
	public PlayerItem impactEffect;
	@Param(desc = "Sound at the hit point.")
	@Sound
	public String impactSound = "";

	private final Vector3 vel = new Vector3(), from = new Vector3(), dir = new Vector3();
	private float age;
	private boolean launched, done;
	private PlayerItem owner;

	/** Shooter 3D / Spawner 3D: starts the flight with a velocity (m/s) and remembers who fired it (never hit). */
	public void launch(float vx, float vy, float vz, PlayerItem shooter) {
		vel.set(vx, vy, vz);
		owner = shooter;
		launched = true;
		age = 0;
		done = false;
	}

	@Block(label = "set projectile speed", desc = "New speed along where it flies (m/s).", labelAr = "اضبط سرعة المقذوف", descAr = "السرعة الجديدة في اتجاه طيرانه (م/ث).", category = "Projectile 3D", args = {"speed"})
	public void setSpeed(float s) {
		if (vel.isZero(0.0001f)) speed = s;
		else vel.nor().scl(s);
	}

	@Block(label = "set projectile damage", desc = "Damage on hit.", labelAr = "اضبط ضرر المقذوف", descAr = "الضرر عند الإصابة.", category = "Projectile 3D", args = {"damage"})
	public void setDamage(float d) {
		damage = Math.max(0, d);
	}

	@Block(label = "remove projectile", desc = "Disappears now.", labelAr = "أزل المقذوف", descAr = "يختفي الآن.", category = "Projectile 3D", args = {})
	public void remove() {
		if (done) return;
		done = true;
		Object3D o = obj();
		if (o != null) o.destroy();
	}

	@Block(label = "flight time", desc = "Seconds since it was fired.", labelAr = "زمن الطيران", descAr = "ثوانٍ منذ إطلاقه.", category = "Projectile 3D", args = {})
	public float getAge() {
		return age;
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o == null || launched) return;
		o.forward(vel).scl(speed);
		launched = true;
	}

	@Override
	public void onCollisionBegin(PlayerItem other) {
		if (other == owner || done) return;
		Object3D o = obj();
		if (o != null && o.isDynamic()) hit(other, o.position);
	}

	private void hit(PlayerItem other, Vector3 at) {
		if (done) return;
		if (other != null && damage > 0 && (targetPrefix.length() == 0 || other.getName() != null && other.getName().startsWith(targetPrefix)))
			GameplaySupport.hit(other, damage, owner != null ? owner : item, 0, 0, 0, 0);
		World3D w = world3d();
		if (impactSound.length() > 0) Support3D.playAt(impactSound, w, at.x, at.y, at.z, 1, 40, 1);
		if (impactEffect instanceof Object3D) {
			Object3D fx = (Object3D) impactEffect;
			PlayerItem c = fx.getClone(fx.getName() + "_hit" + GameplaySupport.nextId());
			if (c instanceof Object3D) {
				((Object3D) c).setPosition3(at.x, at.y, at.z);
				((Object3D) c).setVisible(true);
				expire((Object3D) c, 2f);
			}
		}
		fire("onHit", other);
		if (destroyOnHit) remove();
	}

	/** Removes an effect copy after some seconds (shared by the 3D combat behaviors). */
	static void expire(Object3D o, float seconds) {
		Lifetime3D.after(o, seconds);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || done || dt <= 0) return;
		age += dt;
		if (age >= lifetime) {
			fire("onExpire");
			remove();
			return;
		}
		if (o.isDynamic()) {
			if (age <= dt * 1.5f) o.setVelocity3(vel.x, vel.y, vel.z);
			return;
		}
		vel.y += w.gravity.y * gravity * dt;
		from.set(o.position);
		float step = vel.len() * dt;
		if (step <= 0) return;
		dir.set(vel).nor();
		// what is in the way this frame?
		PlayerItem hitWho = null;
		Vector3 at = null;
		if (w.hasPhysics() && w.physics().raycast(from.x, from.y, from.z, dir.x, dir.y, dir.z, step, o)) {
			Object3D h = w.physics().hitObject;
			if (h != owner && !(h != null && h.getBehavior("Projectile3D") != null)) {
				hitWho = h;
				at = new Vector3(w.physics().hitPoint);
			}
		}
		if (at == null) {
			for (int i = 0; i < w.objects.size; i++) {
				Object3D t = w.objects.get(i);
				if (t == o || t == owner || !t.isVisible() || t.hasBody() || t.getBehavior("Health3D") == null) continue;
				Vector3 c = t.center(new Vector3());
				float r = Math.max(0.2f, Math.min(t.getSizeX(), t.getSizeZ()) * 0.5f);
				// distance from the center to this frame's segment
				Vector3 toC = new Vector3(c).sub(from);
				float along = Math.max(0, Math.min(step, toC.dot(dir)));
				Vector3 near = new Vector3(dir).scl(along).add(from);
				if (near.dst(c) <= r && Math.abs(near.y - c.y) <= t.getSizeY() * 0.5f + 0.1f) {
					hitWho = t;
					at = near;
					break;
				}
			}
		}
		if (at != null) {
			o.setPosition3(at.x, at.y, at.z);
			hit(hitWho, at);
			return;
		}
		o.setPosition3(from.x + vel.x * dt, from.y + vel.y * dt, from.z + vel.z * dt);
		if (faceVelocity) o.lookAt(o.position.x + vel.x, o.position.y + vel.y, o.position.z + vel.z);
	}
}
