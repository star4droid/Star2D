package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Platform Mover 3D", icon = "stairs", category = "Movement", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onStop(int point)"},
		desc = "A moving platform, elevator or ferry: travels between its start and an offset (or through points), waits at each stop and carries whatever stands on it (characters, boxes, the first-person player). Give it a KINEMATIC body.")
@Exclusive("movement")
public class PlatformMover3D extends Behavior {
	@Header("Route")
	@Param(desc = "Stops after the start, in order (empty = the offset below).")
	public List<PlayerItem> points = new ArrayList<PlayerItem>();
	@Param(desc = "Far end on X when there are no points.", unit = "m", min = -1000, max = 1000, step = 0.1)
	public float offsetX = 0;
	@Param(desc = "Far end on Y (up) when there are no points.", unit = "m", min = -1000, max = 1000, step = 0.1)
	public float offsetY = 3;
	@Param(desc = "Far end on Z when there are no points.", unit = "m", min = -1000, max = 1000, step = 0.1)
	public float offsetZ = 0;

	@Header("Timing")
	@Param(desc = "Travel speed.", unit = "m/s", min = 0.01, max = 100000, step = 0.05)
	public float speed = 1.5f;
	@Param(desc = "Wait at each stop.", unit = "s", min = 0, max = 120, step = 0.1)
	public float waitTime = 1.5f;
	@Param(desc = "Only moves after a block calls \"go to next stop\" (elevator buttons).")
	public boolean waitForCall = false;
	@Param(desc = "Carries the elements standing on it.")
	public boolean carryRiders = true;

	private final ArrayList<Vector3> stops = new ArrayList<Vector3>();
	private final Vector3 from = new Vector3(), pos = new Vector3(), last = new Vector3();
	private int index, dir = 1, called;
	private float t, wait, legTime = 1;
	private boolean has, moving;

	@Block(label = "go to next stop", desc = "Leaves for the next stop now.", labelAr = "اذهب للمحطة التالية", descAr = "ينطلق نحو المحطة التالية الآن.", category = "Platform mover 3D", args = {})
	public void next() {
		called++;
		wait = 0;
	}

	@Block(label = "go to stop", desc = "Travels to the stop with this number (0 = start).", labelAr = "اذهب للمحطة", descAr = "ينتقل للمحطة بهذا الرقم (0 = البداية).", category = "Platform mover 3D", args = {"index"})
	public void goToStop(int i) {
		if (!has) return;
		int target = Math.max(0, Math.min(stops.size() - 1, i));
		if (target == index && !moving) return;
		startLeg(target);
	}

	@Block(label = "set platform speed", desc = "Meters per second.", labelAr = "اضبط سرعة المنصة", descAr = "أمتار في الثانية.", category = "Platform mover 3D", args = {"speed"})
	public void setSpeed(float v) {
		speed = Math.max(0.01f, v);
	}

	@Block(label = "platform is moving", desc = "True while travelling.", labelAr = "المنصة تتحرك", descAr = "صح أثناء الانتقال.", category = "Platform mover 3D", args = {})
	public boolean isMoving() {
		return moving;
	}

	@Block(label = "platform stop", desc = "Number of the stop it is at / going to.", labelAr = "محطة المنصة", descAr = "رقم المحطة التي عندها / يتجه إليها.", category = "Platform mover 3D", args = {})
	public int getStop() {
		return index;
	}

	private void startLeg(int target) {
		Object3D o = obj();
		from.set(o.position);
		index = target;
		legTime = Math.max(0.05f, from.dst(stops.get(index)) / Math.max(0.01f, speed));
		t = 0;
		moving = true;
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o == null) return;
		stops.clear();
		stops.add(new Vector3(o.position));
		Vector3 p = new Vector3();
		for (PlayerItem it : points) if (it != null && Behavior.pos3(it, p)) stops.add(new Vector3(p));
		if (stops.size() == 1) stops.add(new Vector3(o.position).add(offsetX, offsetY, offsetZ));
		last.set(o.position);
		index = 0;
		wait = waitTime;
		has = true;
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || !has || dt <= 0) return;
		if (!moving) {
			if (waitForCall && called <= 0) {
				carry(w, o);
				return;
			}
			wait -= dt;
			if (wait <= 0 || called > 0) {
				if (called > 0) called--;
				int n = stops.size();
				int next = index + dir;
				if (next >= n || next < 0) {
					dir = -dir;
					next = index + dir;
				}
				startLeg(Math.max(0, Math.min(n - 1, next)));
			}
		} else {
			t += dt;
			float k = Interpolation.smooth.apply(Math.min(1f, t / legTime));
			pos.set(from).lerp(stops.get(index), k);
			o.setPosition3(pos.x, pos.y, pos.z);
			if (t >= legTime) {
				moving = false;
				wait = waitTime;
				fire("onStop", index);
			}
		}
		carry(w, o);
	}

	/** Moves what stands on top by the platform's movement of this frame. */
	private void carry(World3D w, Object3D o) {
		float dx = o.position.x - last.x, dy = o.position.y - last.y, dz = o.position.z - last.z;
		last.set(o.position);
		if (!carryRiders || w == null || dx == 0 && dy == 0 && dz == 0) return;
		float top = o.position.y + Math.max(o.localBounds().max.y * o.scale.y, o.localBounds().min.y * o.scale.y) - dy;
		float hx = o.getSizeX() * 0.5f + 0.1f, hz = o.getSizeZ() * 0.5f + 0.1f;
		for (int i = 0; i < w.objects.size; i++) {
			Object3D r = w.objects.get(i);
			if (r == o || !r.isVisible() || r.isStaticBody() && r.hasBody()) continue;
			if (r.d() != null && ("TERRAIN".equalsIgnoreCase(r.d().Mesh) || "WATER".equalsIgnoreCase(r.d().Mesh))) continue;
			if (Math.abs(r.position.x - o.position.x + dx) > hx || Math.abs(r.position.z - o.position.z + dz) > hz) continue;
			float b = r.getBottomY();
			if (Math.abs(b - top) > 0.25f) continue;
			r.setPosition3(r.position.x + dx, r.position.y + dy, r.position.z + dz);
		}
		for (int i = 0; i < w.cameras.size; i++) {
			Camera3D c = w.cameras.get(i);
			Behavior fp = c.getBehavior("FirstPersonController3D");
			if (!(fp instanceof FirstPersonController3D) || !fp.enabled) continue;
			float feet = c.position.y - ((FirstPersonController3D) fp).eyeHeight;
			if (Math.abs(c.position.x - o.position.x + dx) > hx || Math.abs(c.position.z - o.position.z + dz) > hz || Math.abs(feet - top) > 0.3f) continue;
			c.position.add(dx, dy, dz);
		}
	}
}
