package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Camera Orbit Touch 3D", icon = "camera-switch", category = "Camera", targets = BehaviorInfo.Target.THREE_D, types = {"CAMERA3D"},
		events = {"onIdle()"},
		desc = "Drag to turn the camera around a target and pinch to zoom, with limits so it never goes under the floor: model viewers, garages and car pickers, strategy maps, menus. Turns slowly by itself when nobody touches the screen.")
@Exclusive("camera")
public class CameraOrbitTouch3D extends Behavior {
	@Header("Target")
	@Param(desc = "Element to turn around (empty = the point the camera looks at when the game starts).")
	public PlayerItem target;
	@Param(desc = "Looks at this height above the target.", unit = "m", min = -100, max = 100, step = 0.05)
	public float lookHeight = 0.8f;

	@Header("Limits")
	@Param(desc = "Distance at start (0 = the distance at start).", unit = "m", min = 0, max = 500, step = 0.1)
	public float distance = 0;
	@Param(desc = "Closest zoom.", unit = "m", min = 0.3, max = 500, step = 0.1)
	public float minDistance = 2;
	@Param(desc = "Farthest zoom.", unit = "m", min = 0.5, max = 2000, step = 0.5)
	public float maxDistance = 25;
	@Param(desc = "Lowest view angle (negative = from above).", unit = "deg", min = -89, max = 89)
	public float minPitch = -80;
	@Param(desc = "Highest view angle (0 = level with the target).", unit = "deg", min = -89, max = 89)
	public float maxPitch = -5;

	@Header("Feel")
	@Param(desc = "Degrees per dragged pixel.", unit = "deg/px", min = 0.02, max = 2, step = 0.01)
	public float sensitivity = 0.3f;
	@Param(desc = "Turns by itself while nobody touches (0 = never).", unit = "deg/s", min = -100000, max = 100000, step = 1)
	public float autoRotate = 12;
	@Param(desc = "Seconds without touches before turning by itself.", unit = "s", min = 0, max = 60, step = 0.5)
	public float idleDelay = 3;

	private final Vector3 pivot = new Vector3(), tp = new Vector3();
	private float idle, dist;
	private boolean has, idleFired;

	@Block(label = "set zoom", desc = "Camera distance (m), kept within the limits.", labelAr = "اضبط التكبير", descAr = "بعد الكاميرا (م) ضمن الحدود.", category = "Camera orbit 3D", args = {"meters"})
	public void setZoom(float m) {
		dist = clamp(m, minDistance, maxDistance);
		Camera3D c = camera3d();
		if (c != null) c.distance = dist;
	}

	@Block(label = "set orbit angles", desc = "Heading and tilt of the view (degrees).", labelAr = "اضبط زوايا المدار", descAr = "اتجاه وميل النظر (بالدرجات).", category = "Camera orbit 3D", args = {"yaw", "pitch"})
	public void setAngles(float yaw, float pitch) {
		Camera3D c = camera3d();
		if (c == null) return;
		c.yaw = yaw;
		c.pitch = clamp(pitch, minPitch, maxPitch);
	}

	@Block(label = "set orbit target", desc = "Element to turn around.", labelAr = "اضبط هدف المدار", descAr = "العنصر الذي يدور حوله.", category = "Camera orbit 3D", args = {"target"})
	public void setTarget(PlayerItem it) {
		target = it;
		has = false;
	}

	@Block(label = "zoom distance", desc = "Current distance (m).", labelAr = "مسافة التكبير", descAr = "المسافة الحالية (م).", category = "Camera orbit 3D", args = {})
	public float getZoom() {
		Camera3D c = camera3d();
		return c == null ? dist : c.distance;
	}

	private void setup(Camera3D c) {
		has = true;
		if (target instanceof Object3D) {
			Behavior.pos3(target, tp);
			pivot.set(tp);
		} else {
			float d = distance > 0 ? distance : 8;
			pivot.set(c.cam.direction).scl(d).add(c.position).sub(0, lookHeight, 0);
		}
		Vector3 eye = new Vector3(pivot).add(0, lookHeight, 0);
		dist = distance > 0 ? distance : Math.max(minDistance, c.position.dst(eye));
		dist = clamp(dist, minDistance, maxDistance);
		Vector3 d = new Vector3(c.position).sub(eye);
		c.yaw = MathUtils.atan2(d.x, d.z) * MathUtils.radiansToDegrees;
		c.pitch = clamp(-MathUtils.asin(MathUtils.clamp(d.y / Math.max(0.001f, d.len()), -1, 1)) * MathUtils.radiansToDegrees, minPitch, maxPitch);
		c.distance = dist;
		c.lookHeight = lookHeight;
		c.sensitivity = sensitivity;
		c.setTouchLook(true);
		if (target instanceof Object3D) c.follow(target, "ORBIT");
		else c.stopFollow();
	}

	@Override
	public void onEnable() {
		has = false;
	}

	@Override
	public void onDisable() {
		Camera3D c = camera3d();
		if (c != null) {
			c.setTouchLook(false);
			if (c.target == target) c.stopFollow();
		}
	}

	@Override
	public void onUpdate(float dt) {
		Camera3D c = camera3d();
		World3D w = world3d();
		if (c == null || w == null) return;
		if (!has) setup(c);
		c.sensitivity = sensitivity;
		c.pitch = clamp(c.pitch, minPitch, maxPitch);
		c.distance = clamp(c.distance, minDistance, maxDistance);
		if (w.touchingScreen) {
			idle = 0;
			idleFired = false;
		} else {
			idle += dt;
			if (idle >= idleDelay) {
				if (!idleFired) {
					idleFired = true;
					fire("onIdle");
				}
				c.yaw += autoRotate * dt;
			}
		}
		if (!(target instanceof Object3D)) {
			// no element: orbit the fixed point ourselves (camera "free" mode reads yaw / pitch)
			float p = c.pitch, d = c.distance;
			c.position.set(pivot.x + MathUtils.sinDeg(c.yaw) * MathUtils.cosDeg(-p) * d, pivot.y + lookHeight + MathUtils.sinDeg(-p) * d,
					pivot.z + MathUtils.cosDeg(c.yaw) * MathUtils.cosDeg(-p) * d);
		}
	}
}
