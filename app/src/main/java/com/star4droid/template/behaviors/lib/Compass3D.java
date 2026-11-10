package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Compass 3D", icon = "navigation", category = "Gameplay", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onArrive()"},
		desc = "A guide arrow: stays above / in front of the player and points to a target (the next quest place), bobbing gently; hides near the target and tells it. Put it on an arrow model or a Cone. Change the target with the block.")
public class Compass3D extends Behavior {
	@Header("Guide")
	@Param(desc = "Who carries the arrow (the player; change it with the block).")
	public PlayerItem anchor;
	@Param(desc = "Where it points (empty = hidden).")
	public PlayerItem target;
	@Param(desc = "Height above the anchor.", unit = "m", min = -10, max = 20, step = 0.1)
	public float height = 0.3f;
	@Param(desc = "Distance in front of the anchor, toward the target.", unit = "m", min = 0, max = 20, step = 0.1)
	public float ahead = 1.6f;
	@Param(desc = "Hidden when the anchor is this close to the target.", unit = "m", min = 0, max = 200, step = 0.5)
	public float arriveDistance = 6;
	@Param(desc = "Extra heading of the arrow model (its point along -Z = 0).", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;
	@Param(desc = "Up and down motion.", unit = "m", min = 0, max = 2, step = 0.05)
	public float bob = 0.12f;

	private final Vector3 a = new Vector3(), t = new Vector3();
	private boolean arrived;
	private float time, yaw;

	@Block(label = "point compass to", desc = "New target of the guide arrow (empty = hide).", labelAr = "وجّه البوصلة إلى", descAr = "هدف جديد لسهم الإرشاد (فارغ = إخفاء).", category = "Compass 3D", args = {"target"})
	public void setTarget(PlayerItem it) {
		target = it;
		arrived = false;
	}

	@Block(label = "set compass carrier", desc = "Who carries the arrow.", labelAr = "اضبط حامل البوصلة", descAr = "من يحمل السهم.", category = "Compass 3D", args = {"anchor"})
	public void setAnchor(PlayerItem it) {
		anchor = it;
	}

	@Block(label = "compass distance", desc = "Meters from the carrier to the target (-1 = no target).", labelAr = "مسافة البوصلة", descAr = "الأمتار من الحامل إلى الهدف (-1 = لا هدف).", category = "Compass 3D", args = {})
	public float getDistance() {
		if (anchor == null || target == null || !pos3(anchor, a) || !pos3(target, t)) return -1;
		return (float) Math.sqrt((t.x - a.x) * (t.x - a.x) + (t.z - a.z) * (t.z - a.z));
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null) return;
		time += dt;
		if (anchor == null || target == null || !pos3(anchor, a) || !pos3(target, t) || !Support3D.alive3(anchor)) {
			o.setVisible(false);
			return;
		}
		float dx = t.x - a.x, dz = t.z - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
		if (d < arriveDistance) {
			o.setVisible(false);
			if (!arrived) {
				arrived = true;
				fire("onArrive");
			}
			return;
		}
		arrived = false;
		o.setVisible(true);
		float want = Support3D.yawOf(dx, dz);
		yaw = Support3D.approachAngle(yaw, want, 360 * dt);
		float k = Math.min(ahead, d * 0.5f);
		float top = Support3D.o(anchor) != null ? Support3D.o(anchor).getTopY() : a.y + 1;
		o.setPosition3(a.x - MathUtils.sinDeg(yaw) * k, top + height + MathUtils.sin(time * 3f) * bob, a.z - MathUtils.cosDeg(yaw) * k);
		o.setYaw3(yaw + modelYawOffset);
	}
}
