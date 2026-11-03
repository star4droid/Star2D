package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sets;

@BehaviorInfo(name = "Orbit", icon = "orbit", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		events = {"onLap()"},
		desc = "Circles around another element (planets, shields, satellites, rotating saws) or around a point, with an optional oval shape and spiral in/out. Physical elements stay physical (they push things) and ignore gravity.")
@Sets({"gravityScale=0", "fixedRotation=true"})
public class Orbit extends Behavior {
	@Param(desc = "The element to circle around. Empty = a point chosen so that the element starts on its own circle.")
	public PlayerItem center;
	@Param(desc = "Distance from the center. 0 = the current distance to the center element (100 when there is no center element).", unit = "px", min = 0, max = 10000)
	public float radius = 0;
	@Param(desc = "Degrees per second.", unit = "deg/s", min = 0, max = 100000, step = 5)
	public float speed = 90;
	@Param(desc = "Circle clockwise (off: counter-clockwise).")
	public boolean clockwise = false;
	@Param(desc = "Height of the oval compared to its width (1 = circle).", min = 0.1, max = 4, step = 0.05)
	public float ovalRatio = 1;
	@Param(desc = "The radius grows (positive) or shrinks (negative) by this much each second: spirals.", unit = "px/s", min = -100000, max = 100000, step = 1)
	public float spiral = 0;
	@Param(desc = "Smallest radius when spiralling in.", unit = "px", min = 0, max = 10000)
	public float minRadius = 0;
	@Param(desc = "Turn the element along its path (arrows, planes).")
	public boolean faceMovement = false;
	@Param(desc = "Angle of the image when it faces right (0 = right, 90 = up...).", unit = "deg", min = -360, max = 360)
	public float faceOffset = 0;

	private float angle, r, ox, oy, travelled;
	private boolean ready;

	public void onStart() {
		setup();
	}

	public void onEnable() {
		if (ready) setup();
	}

	private void setup() {
		float x = centerX(), y = centerY();
		if (alive(center)) {
			float dx = x - cx(center), dy = y - cy(center);
			float d = (float) Math.sqrt(dx * dx + dy * dy);
			r = radius > 0 ? radius : (d > 1f ? d : 100f);
			angle = d > 1f ? (float) Math.atan2(dy, dx) : 0f;
		} else {
			r = radius > 0 ? radius : 100f;
			angle = 0f;
			ox = x - r; // the start position is the right-most point of the circle
			oy = y;
		}
		travelled = 0;
		ready = true;
	}

	public void onDisable() {
		if (!isUI()) setVelocity(0, 0);
	}

	@Block(label = "set orbit speed", desc = "Degrees per second.", labelAr = "اضبط سرعة المدار", descAr = "الدرجات في الثانية.", category = "Orbit", args = {"degrees"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set orbit radius", desc = "Distance from the center (px).", labelAr = "اضبط نصف قطر المدار", descAr = "المسافة من المركز (بكسل).", category = "Orbit", args = {"radius"})
	public void setRadius(float value) {
		r = Math.max(0f, value);
		radius = value;
	}

	@Block(label = "set orbit center", desc = "Circles around another element.", labelAr = "اضبط مركز المدار", descAr = "يدور حول عنصر آخر.", category = "Orbit", args = {"center"})
	public void setCenterItem(PlayerItem c) {
		center = c;
		setup();
	}

	@Block(label = "reverse orbit", desc = "Circles the other way.", labelAr = "اعكس المدار", descAr = "يدور في الاتجاه المعاكس.", category = "Orbit")
	public void reverse() {
		clockwise = !clockwise;
	}

	@Block(label = "orbit angle", desc = "Where it is on the circle, in degrees (0 = right of the center, 90 = above).", labelAr = "زاوية المدار", descAr = "موضعه على الدائرة بالدرجات (0 = يمين المركز، 90 = أعلى).", category = "Orbit")
	public float getOrbitAngle() {
		return (float) Math.toDegrees(angle);
	}

	@Block(label = "orbit radius", desc = "Current distance from the center (px).", labelAr = "نصف قطر المدار", descAr = "المسافة الحالية من المركز (بكسل).", category = "Orbit")
	public float getRadius() {
		return r;
	}

	public void onUpdate(float dt) {
		if (!ready) setup();
		float dir = clockwise ? -1f : 1f;
		float da = (float) Math.toRadians(speed) * dir * dt;
		angle += da;
		travelled += Math.abs(da);
		if (travelled >= (float) (2 * Math.PI)) {
			travelled -= (float) (2 * Math.PI);
			fire("onLap");
		}
		if (spiral != 0) r = Math.max(minRadius, r + spiral * dt);
		float mx, my;
		if (alive(center)) {
			mx = cx(center);
			my = cy(center);
		} else {
			mx = ox;
			my = oy;
		}
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		float tx = mx + c * r, ty = my + s * r * ovalRatio;
		Body b = body();
		if (b != null) {
			float inv = 1f / Math.max(dt, 0.0001f);
			setVelocity((tx - centerX()) * inv, (ty - centerY()) * inv);
		} else {
			setCenter(tx, ty);
		}
		if (faceMovement) {
			// tangent of the path
			float tdx = -s * dir, tdy = c * dir * ovalRatio;
			float rot = (float) Math.toDegrees(Math.atan2(tdy, tdx)) - faceOffset;
			if (b != null) b.setTransform(b.getPosition(), (float) Math.toRadians(rot));
			else actor().setRotation(rot);
		}
	}
}
