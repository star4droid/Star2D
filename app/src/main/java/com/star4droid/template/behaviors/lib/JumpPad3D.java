package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Jump Pad 3D", icon = "arrow-up-bold-circle", category = "Gameplay", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onLaunch(PlayerItem who)"},
		desc = "Throws whoever steps on it into the air (and forward if you like): trampolines, boost pads, launch ramps. Works for physics bodies, characters moved by controllers, and cars.")
public class JumpPad3D extends Behavior {
	@Header("Who")
	@Param(desc = "Elements it launches (empty = any moving element that steps on it).")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Counts as on the pad this far above its top.", unit = "m", min = 0.05, max = 5, step = 0.05)
	public float reach = 0.35f;

	@Header("Launch")
	@Param(desc = "Upward launch speed.", unit = "m/s", min = 0, max = 100000, step = 0.5)
	public float upSpeed = 9;
	@Param(desc = "Forward push along the pad's front (-Z).", unit = "m/s", min = -100000, max = 100000, step = 0.5)
	public float forwardSpeed = 0;
	@Param(desc = "Waits this long before launching the same element again.", unit = "s", min = 0, max = 10, step = 0.05)
	public float cooldown = 0.6f;
	@Param(desc = "Sound of a launch.")
	@Sound
	public String sound = "";

	private final Vector3 tmp = new Vector3();
	private final java.util.HashMap<PlayerItem, Float> last = new java.util.HashMap<PlayerItem, Float>();

	@Block(label = "set launch speed", desc = "Upward speed (m/s).", labelAr = "اضبط سرعة القذف", descAr = "السرعة للأعلى (م/ث).", category = "Jump pad 3D", args = {"speed"})
	public void setUpSpeed(float v) {
		upSpeed = v;
	}

	@Block(label = "launch element", desc = "Throws an element now, wherever it is.", labelAr = "اقذف عنصراً", descAr = "يقذف عنصراً الآن أينما كان.", category = "Jump pad 3D", args = {"element"})
	public void launch(PlayerItem it) {
		Object3D p = obj();
		if (p == null || it == null) return;
		Vector3 f = p.forward(tmp).scl(forwardSpeed);
		Object3D o = Support3D.o(it);
		if (o != null && o.isDynamic()) o.setVelocity3(o.getVelocityX3() + f.x, upSpeed, o.getVelocityZ3() + f.z);
		else {
			// characters moved by a controller: jump through it
			Behavior b = it.getBehavior("ThirdPersonController3D");
			if (b == null) b = it.getBehavior("FirstPersonController3D");
			boolean done = false;
			if (b instanceof ThirdPersonController3D) {
				float js = ((ThirdPersonController3D) b).jumpSpeed;
				((ThirdPersonController3D) b).jumpSpeed = upSpeed;
				((ThirdPersonController3D) b).jump();
				((ThirdPersonController3D) b).jumpSpeed = js;
				done = true;
			} else if (b instanceof FirstPersonController3D) {
				((FirstPersonController3D) b).launch(upSpeed);
				done = true;
			}
			if (!done && o != null) o.setVelocity3(f.x, upSpeed, f.z);
		}
		if (sound.length() > 0) Support3D.playAt(sound, world3d(), p.position.x, p.position.y, p.position.z, 1, 40, 1);
		fire("onLaunch", it);
	}

	@Block(label = "launches", desc = "How many launches so far.", labelAr = "عدد القذفات", descAr = "عدد مرات القذف حتى الآن.", category = "Jump pad 3D", args = {})
	public int getLaunches() {
		return launches;
	}

	private int launches;

	@Override
	public void onUpdate(float dt) {
		Object3D p = obj();
		World3D w = world3d();
		if (p == null || w == null) return;
		float top = p.position.y + p.localBounds().max.y * p.scale.y;
		float hx = p.getSizeX() * 0.5f, hz = p.getSizeZ() * 0.5f;
		float now = time();
		for (int i = 0; i < w.objects.size; i++) {
			Object3D o = w.objects.get(i);
			if (o == p || !o.isVisible() || o.hasBody() && o.isStaticBody()) continue;
			if (!targets.isEmpty() && !targets.contains(o)) continue;
			if (targets.isEmpty() && !o.isDynamic() && o.getScript() == null) continue;
			tmp.set(o.position).sub(p.position).mul(new com.badlogic.gdx.math.Quaternion(p.rotation).conjugate());
			if (Math.abs(tmp.x) > hx || Math.abs(tmp.z) > hz) continue;
			float b = o.getBottomY();
			if (b < top - 0.3f || b > top + reach) continue;
			Float l = last.get(o);
			if (l != null && now - l < cooldown) continue;
			last.put(o, now);
			launches++;
			launch(o);
		}
		// a first-person player that is a camera
		for (int i = 0; i < w.cameras.size; i++) {
			com.star4droid.template.d3.Camera3D c = w.cameras.get(i);
			Behavior fp = c.getBehavior("FirstPersonController3D");
			if (!(fp instanceof FirstPersonController3D) || !fp.enabled || !targets.isEmpty() && !targets.contains(c)) continue;
			tmp.set(c.position).sub(p.position).mul(new com.badlogic.gdx.math.Quaternion(p.rotation).conjugate());
			float b = c.position.y - ((FirstPersonController3D) fp).eyeHeight;
			if (Math.abs(tmp.x) > hx || Math.abs(tmp.z) > hz || b < top - 0.3f || b > top + reach) continue;
			Float l = last.get(c);
			if (l != null && now - l < cooldown) continue;
			last.put(c, now);
			launches++;
			launch(c);
		}
	}
}
