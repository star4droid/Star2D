package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Buoyancy 3D", icon = "lifebuoy", category = "Water", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onEnterWater()", "onLeaveWater()"},
		desc = "Floats on a WATER element and rocks with its waves: boats, rafts, barrels, a bench washed by the sea, ducks. Without a body it rides the surface exactly; with a DYNAMIC body the waves push its corners so it tilts and bobs for real.")
public class Buoyancy3D extends Behavior {
	@Header("Water")
	@Param(desc = "The WATER element (empty = the water under it).")
	public PlayerItem water;
	@Param(desc = "How deep it sits in the water (negative = floats above).", unit = "m", min = -10, max = 10, step = 0.01)
	public float sinkDepth = 0.15f;

	@Header("Motion")
	@Param(desc = "How much it tilts with the waves (0 = stays level).", min = 0, max = 2, step = 0.05)
	public float tilt = 1;
	@Param(desc = "Follows the surface smoothly (higher = quicker).", unit = "1/s", min = 0.5, max = 30, step = 0.5)
	public float smoothing = 5;
	@Param(desc = "DYNAMIC: how strongly the water lifts it (1 = floats at the sink depth).", min = 0, max = 10, step = 0.05)
	public float lift = 1;
	@Param(desc = "DYNAMIC: water slows it down (drag).", min = 0, max = 10, step = 0.05)
	public float drag = 1.2f;
	@Param(desc = "Slow drift along the waves (no body).", unit = "m/s", min = 0, max = 10, step = 0.05)
	public float drift = 0;

	private final Vector3 v = new Vector3();
	private Object3D w3;
	private float y, pitch, roll, yaw, ex, ez;
	private boolean has, inWater, frozen;

	@Block(label = "set sink depth", desc = "How deep it sits (m).", labelAr = "اضبط عمق الغطس", descAr = "عمق جلوسه في الماء (م).", category = "Buoyancy 3D", args = {"meters"})
	public void setSinkDepth(float m) {
		sinkDepth = m;
	}

	@Block(label = "freeze floating", desc = "true = stop following the waves.", labelAr = "جمّد الطفو", descAr = "صح = يتوقف عن تتبع الأمواج.", category = "Buoyancy 3D", args = {"frozen"})
	public void setFrozen(boolean on) {
		frozen = on;
	}

	@Block(label = "is in water", desc = "True while on the water.", labelAr = "في الماء", descAr = "صح أثناء وجوده على الماء.", category = "Buoyancy 3D", args = {})
	public boolean isInWater() {
		return inWater;
	}

	@Block(label = "water height here", desc = "Height of the surface under it (m).", labelAr = "ارتفاع الماء هنا", descAr = "ارتفاع السطح تحته (م).", category = "Buoyancy 3D", args = {})
	public float getSurface() {
		Object3D o = obj();
		Object3D w = findWater(o);
		return o == null || w == null ? 0 : w.heightAt(o.position.x, o.position.z);
	}

	private Object3D findWater(Object3D o) {
		if (water instanceof Object3D) return (Object3D) water;
		if (w3 != null && !w3.isDestroyed()) return w3;
		World3D w = world3d();
		if (w == null || o == null) return null;
		for (int i = 0; i < w.objects.size; i++) {
			Object3D c = w.objects.get(i);
			if (!c.isWater()) continue;
			if (Math.abs(o.position.x - c.position.x) <= c.getSizeX() * 0.5f && Math.abs(o.position.z - c.position.z) <= c.getSizeZ() * 0.5f) {
				w3 = c;
				return c;
			}
		}
		return null;
	}

	private boolean over(Object3D w, float x, float z) {
		return Math.abs(x - w.position.x) <= w.getSizeX() * 0.5f && Math.abs(z - w.position.z) <= w.getSizeZ() * 0.5f;
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null || frozen || dt <= 0) return;
		Object3D w = findWater(o);
		boolean now = w != null && over(w, o.position.x, o.position.z);
		if (now != inWater) {
			inWater = now;
			fire(inWater ? "onEnterWater" : "onLeaveWater");
		}
		if (!now) return;
		BoundingBox b = o.localBounds();
		float hx = b.getWidth() * Math.abs(o.scale.x) * 0.4f, hz = b.getDepth() * Math.abs(o.scale.z) * 0.4f;
		float bottom = b.min.y * o.scale.y;
		if (!has) {
			has = true;
			y = o.position.y;
			yaw = o.getYaw3();
			ex = o.euler.x;
			ez = o.euler.z;
		}
		Vector3 f = o.forward(new Vector3()), r = o.right(new Vector3());
		f.y = 0;
		r.y = 0;
		f.nor();
		r.nor();
		float px = o.position.x, pz = o.position.z;
		float hF = w.heightAt(px + f.x * hz, pz + f.z * hz), hB = w.heightAt(px - f.x * hz, pz - f.z * hz);
		float hR = w.heightAt(px + r.x * hx, pz + r.z * hx), hL = w.heightAt(px - r.x * hx, pz - r.z * hx);
		float hC = (hF + hB + hR + hL) * 0.25f;
		if (o.isDynamic()) {
			float m = Math.max(0.001f, o.d().Mass);
			float g = world3d() == null ? 9.81f : -world3d().gravity.y;
			float[] hs = {hF, hB, hR, hL};
			float[][] pts = {{f.x * hz, f.z * hz}, {-f.x * hz, -f.z * hz}, {r.x * hx, r.z * hx}, {-r.x * hx, -r.z * hx}};
			boolean builtIn = w.d() != null && w.d().Buoyancy > 0;
			for (int i = 0; i < 4; i++) {
				float sx = px + pts[i][0], sz = pz + pts[i][1];
				float pointBottom = o.position.y + bottom + sinkDepth;
				float depth = hs[i] - pointBottom;
				if (depth <= -0.5f) continue;
				float sub = MathUtils.clamp(depth / Math.max(0.05f, b.getHeight() * Math.abs(o.scale.y)), -0.2f, 1f);
				// the world already lifts the center when the water has Buoyancy: only add the rocking part then
				float up = builtIn ? (hs[i] - hC) * m * g * 1.2f * tilt : sub * m * g * 1.6f * lift * 0.25f;
				o.velocityAt(sx, o.position.y, sz, v);
				float fy = up - v.y * m * drag * 0.25f * Math.max(0, sub);
				o.applyForceAt(-v.x * m * drag * 0.1f * Math.max(0, sub), fy, -v.z * m * drag * 0.1f * Math.max(0, sub), sx, o.position.y, sz);
			}
			return;
		}
		float k = Support3D.smooth(smoothing, dt);
		float target = hC - bottom - sinkDepth;
		y += (target - y) * k;
		float wantPitch = MathUtils.atan2(hF - hB, Math.max(0.01f, 2 * hz)) * MathUtils.radiansToDegrees * tilt;
		float wantRoll = -MathUtils.atan2(hR - hL, Math.max(0.01f, 2 * hx)) * MathUtils.radiansToDegrees * tilt;
		pitch += (wantPitch - pitch) * k;
		roll += (wantRoll - roll) * k;
		float nx = o.position.x, nz = o.position.z;
		if (drift > 0 && w.d() != null) {
			float dir = w.d().Wave_Direction;
			nx += MathUtils.cosDeg(dir) * drift * dt;
			nz += MathUtils.sinDeg(dir) * drift * dt;
		}
		o.setPosition3(nx, y, nz);
		o.setRotation3(ex + pitch, yaw, ez + roll);
	}
}
