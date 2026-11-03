package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Trigger 3D", icon = "select", category = "Interaction", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onEnter(PlayerItem who)", "onExit(PlayerItem who)"},
		desc = "An invisible zone that reports who walks in and out: jump scares, checkpoints, cut scenes, music changes, finish lines. The zone is the element's box or a radius; watches chosen elements, a name prefix, or the first-person camera.")
public class Trigger3D extends Behavior {
	public enum Shape { BOX, RADIUS }

	@Header("Zone")
	@Param(desc = "BOX: inside the element's box (turns with it). RADIUS: within a distance of its center.")
	public Shape shape = Shape.BOX;
	@Param(desc = "Distance for RADIUS.", unit = "m", min = 0.05, max = 1000, step = 0.1)
	public float radius = 2;
	@Param(desc = "Hides the element when the game starts (zone markers).")
	public boolean hideZone = true;

	@Header("Who")
	@Param(desc = "Elements it watches (empty = the name prefix below, or the player camera).")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Also watches every element whose name starts with this (\"enemy\", \"car\"; empty = none).")
	public String namePrefix = "";
	@Param(desc = "The main camera counts (the first-person player).")
	public boolean watchCamera = true;

	@Header("Once")
	@Param(desc = "Works only the first time someone enters.")
	public boolean once = false;
	@Param(desc = "Sound when someone enters.")
	@Sound
	public String enterSound = "";

	private final ArrayList<PlayerItem> inside = new ArrayList<PlayerItem>();
	private final ArrayList<PlayerItem> now = new ArrayList<PlayerItem>();
	private final Vector3 c = new Vector3(), p = new Vector3();
	private int enters;
	private boolean hidden, armed = true;

	@Block(label = "someone inside", desc = "True while a watched element is in the zone.", labelAr = "يوجد أحد بالداخل", descAr = "صح أثناء وجود عنصر مراقب في المنطقة.", category = "Trigger 3D", args = {})
	public boolean isOccupied() {
		return !inside.isEmpty();
	}

	@Block(label = "count inside", desc = "How many watched elements are inside.", labelAr = "العدد بالداخل", descAr = "عدد العناصر المراقبة بالداخل.", category = "Trigger 3D", args = {})
	public int getCount() {
		return inside.size();
	}

	@Block(label = "is inside zone", desc = "True when this element is inside.", labelAr = "داخل المنطقة", descAr = "صح عندما يكون هذا العنصر بالداخل.", category = "Trigger 3D", args = {"element"})
	public boolean contains(PlayerItem it) {
		return inside.contains(it);
	}

	@Block(label = "re-arm trigger", desc = "A once-only trigger works again.", labelAr = "أعد تفعيل المنطقة", descAr = "المنطقة ذات المرة الواحدة تعمل مجدداً.", category = "Trigger 3D", args = {})
	public void rearm() {
		armed = true;
	}

	@Block(label = "times entered", desc = "How many times someone entered.", labelAr = "مرات الدخول", descAr = "عدد مرات الدخول.", category = "Trigger 3D", args = {})
	public int getEnters() {
		return enters;
	}

	private boolean inZone(Object3D zone, Vector3 point) {
		if (shape == Shape.RADIUS) return point.dst(c) <= radius;
		BoundingBox b = zone.localBounds();
		p.set(point).sub(zone.position).mul(new Quaternion(zone.rotation).conjugate());
		float sx = Math.abs(zone.scale.x), sy = Math.abs(zone.scale.y), sz = Math.abs(zone.scale.z);
		return p.x >= b.min.x * sx && p.x <= b.max.x * sx && p.y >= b.min.y * sy && p.y <= b.max.y * sy && p.z >= b.min.z * sz && p.z <= b.max.z * sz;
	}

	private void test(Object3D zone, PlayerItem it, Vector3 point) {
		if (inZone(zone, point) && !now.contains(it)) now.add(it);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D zone = obj();
		World3D w = world3d();
		if (zone == null || w == null) return;
		if (!hidden) {
			hidden = true;
			// hidden zones still need updates: shrink the drawing instead of hiding the actor
			if (hideZone) zone.setOpacity(0);
		}
		zone.center(c);
		now.clear();
		boolean any = false;
		for (int i = 0; i < targets.size(); i++) {
			PlayerItem t = targets.get(i);
			if (t != null && Support3D.alive3(t) && Behavior.pos3(t, p)) {
				any = true;
				Vector3 q = new Vector3(p);
				Object3D o = Support3D.o(t);
				if (o != null) q.y = (o.getBottomY() + o.position.y) * 0.5f + 0.05f;
				test(zone, t, q);
			}
		}
		if (namePrefix != null && namePrefix.length() > 0) {
			any = true;
			for (int i = 0; i < w.objects.size; i++) {
				Object3D o = w.objects.get(i);
				if (o == zone || o.getName() == null || !o.getName().startsWith(namePrefix)) continue;
				test(zone, o, new Vector3(o.position.x, (o.getBottomY() + o.position.y) * 0.5f + 0.05f, o.position.z));
			}
		}
		if (watchCamera && (!any || targets.isEmpty())) {
			Camera3D cam = w.activeCamera;
			if (cam != null) test(zone, cam, new Vector3(cam.position).sub(0, 0.8f, 0));
		}
		for (int i = 0; i < now.size(); i++) {
			PlayerItem it = now.get(i);
			if (inside.contains(it)) continue;
			inside.add(it);
			if (!armed) continue;
			enters++;
			if (enterSound.length() > 0) Support3D.play(enterSound, 1, 1, 0);
			fire("onEnter", it);
			if (once) armed = false;
		}
		for (int i = inside.size() - 1; i >= 0; i--) {
			PlayerItem it = inside.get(i);
			if (now.contains(it)) continue;
			inside.remove(i);
			fire("onExit", it);
		}
	}
}
