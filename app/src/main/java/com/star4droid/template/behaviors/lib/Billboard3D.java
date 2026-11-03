package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Billboard 3D", icon = "sign-direction", category = "Motion", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		desc = "Always turns its face to the camera: flat trees, name tags, health bars, sprites, signs. Upright mode only turns around Y so it never leans back.")
public class Billboard3D extends Behavior {
	@Header("Facing")
	@Param(desc = "Only turn around Y (stays upright).")
	public boolean upright = true;
	@Param(label = "Face is the +Z side", desc = "The element's face is its +Z side (quads, planes made to face +Z) instead of -Z.")
	public boolean faceIsPlusZ = true;
	@Param(desc = "Smooth turning (0 = at once).", unit = "1/s", min = 0, max = 60, step = 0.5)
	public float smoothing = 0;
	@Param(desc = "Stops turning farther than this from the camera (0 = always).", unit = "m", min = 0, max = 5000, step = 1)
	public float maxDistance = 0;

	private final Vector3 cp = new Vector3();
	private float yaw;
	private boolean frozen;

	@Block(label = "freeze billboard", desc = "true = stop turning to the camera.", labelAr = "جمّد اللوحة", descAr = "صح = يتوقف عن الالتفات للكاميرا.", category = "Billboard 3D", args = {"frozen"})
	public void setFrozen(boolean on) {
		frozen = on;
	}

	@Block(label = "is facing camera", desc = "True while it follows the camera.", labelAr = "يواجه الكاميرا", descAr = "صح أثناء التفاته للكاميرا.", category = "Billboard 3D", args = {})
	public boolean isFacing() {
		return !frozen;
	}

	@Block(label = "set billboard upright", desc = "true = only turn around Y.", labelAr = "اجعل اللوحة قائمة", descAr = "صح = يدور حول Y فقط.", category = "Billboard 3D", args = {"upright"})
	public void setUpright(boolean on) {
		upright = on;
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o != null) yaw = o.getYaw3();
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || w.camera == null || frozen) return;
		cp.set(w.camera.position);
		if (maxDistance > 0 && cp.dst(o.position) > maxDistance) return;
		float dx = cp.x - o.position.x, dz = cp.z - o.position.z;
		float target = Support3D.yawOf(dx, dz) + (faceIsPlusZ ? 180 : 0);
		yaw = smoothing > 0 ? Support3D.lerpAngle(yaw, target, Support3D.smooth(smoothing, dt)) : target;
		if (upright) o.setRotation3(o.euler.x, yaw, o.euler.z);
		else {
			o.lookAt(faceIsPlusZ ? 2 * o.position.x - cp.x : cp.x, faceIsPlusZ ? 2 * o.position.y - cp.y : cp.y, faceIsPlusZ ? 2 * o.position.z - cp.z : cp.z);
		}
	}
}
