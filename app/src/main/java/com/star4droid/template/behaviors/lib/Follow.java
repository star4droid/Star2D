package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Follow", icon = "crosshairs-gps", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		events = {"onArrive()", "onLeave()"},
		desc = "Keeps the element next to another one, with an offset and smooth catching up: pets, health bars above a character, a camera-like chaser, a shadow. Works for UI and physical elements.")
public class Follow extends Behavior {
	@Header("Target")
	@Param(desc = "The element to follow.")
	public PlayerItem target;
	@Param(desc = "Where to stay, relative to the target's center (x, right is positive).", unit = "px", min = -5000, max = 5000)
	public float offsetX = 0;
	@Param(desc = "Where to stay, relative to the target's center (y, up is positive).", unit = "px", min = -5000, max = 5000)
	public float offsetY = 0;
	@Param(desc = "Follow on the left/right axis.")
	public boolean followX = true;
	@Param(desc = "Follow on the up/down axis (a physical element then ignores gravity).")
	public boolean followY = true;
	@Param(desc = "Mirror the x offset when the target faces left (its image is flipped).")
	public boolean mirrorOffset = false;

	@Header("Motion")
	@Param(desc = "How tightly it follows: higher = catches up faster. 0 = glued to the target (jumps there).", unit = "1/s", min = 0, max = 50, step = 0.5)
	public float stiffness = 6;
	@Param(desc = "Speed limit. 0 = no limit.", unit = "px/s", min = 0, max = 100000, step = 10)
	public float maxSpeed = 0;
	@Param(desc = "Stops moving when closer than this to the wanted spot.", unit = "px", min = 0, max = 2000)
	public float stopDistance = 2;
	@Param(desc = "Copy the target's rotation too.")
	public boolean matchRotation = false;

	private boolean arrived = true, gravityChanged;
	private float savedGravity = 1;

	public void onStart() {
		applyGravity();
	}

	public void onEnable() {
		applyGravity();
	}

	private void applyGravity() {
		Body b = body();
		if (b != null && followY && !gravityChanged) {
			savedGravity = b.getGravityScale();
			b.setGravityScale(0);
			gravityChanged = true;
		}
	}

	public void onDisable() {
		Body b = body();
		if (b != null) {
			setVelocity(0, 0);
			if (gravityChanged) b.setGravityScale(savedGravity);
		}
		gravityChanged = false;
	}

	@Block(label = "set follow target", desc = "Follows another element.", labelAr = "اضبط هدف التتبع", descAr = "يتبع عنصراً آخر.", category = "Follow", args = {"target"})
	public void setTarget(PlayerItem t) {
		target = t;
		arrived = false;
	}

	@Block(label = "stop following", desc = "Clears the target.", labelAr = "توقف عن التتبع", descAr = "يمسح الهدف.", category = "Follow")
	public void clearTarget() {
		target = null;
		if (!isUI()) setVelocity(0, 0);
	}

	@Block(label = "set follow offset", desc = "Where to stay relative to the target (px).", labelAr = "اضبط إزاحة التتبع", descAr = "مكان البقاء بالنسبة للهدف (بكسل).", category = "Follow", args = {"x", "y"})
	public void setOffset(float x, float y) {
		offsetX = x;
		offsetY = y;
	}

	@Block(label = "set follow stiffness", desc = "Higher = catches up faster, 0 = glued.", labelAr = "اضبط قوة التتبع", descAr = "الأعلى = يلحق أسرع، 0 = ملتصق.", category = "Follow", args = {"stiffness"})
	public void setStiffness(float value) {
		stiffness = value;
	}

	@Block(label = "set follow max speed", desc = "Speed limit (px/s), 0 = none.", labelAr = "اضبط أقصى سرعة للتتبع", descAr = "حد السرعة (بكسل/ثانية)، 0 = بلا حد.", category = "Follow", args = {"speed"})
	public void setMaxSpeed(float value) {
		maxSpeed = value;
	}

	@Block(label = "distance to follow spot", desc = "Distance (px) to the spot it wants to reach.", labelAr = "المسافة إلى نقطة التتبع", descAr = "المسافة (بكسل) إلى النقطة التي يريد بلوغها.", category = "Follow")
	public float getDistance() {
		if (!alive(target)) return 0;
		float dx = wantX() - centerX(), dy = wantY() - centerY();
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	@Block(label = "has reached the target", desc = "True when it is at the wanted spot.", labelAr = "وصل إلى الهدف", descAr = "صحيح عندما يكون في النقطة المطلوبة.", category = "Follow")
	public boolean hasArrived() {
		return arrived;
	}

	private float wantX() {
		float ox = offsetX;
		if (mirrorOffset && target.getActor().getScaleX() < 0) ox = -ox;
		return followX ? cx(target) + ox : centerX();
	}

	private float wantY() {
		return followY ? cy(target) + offsetY : centerY();
	}

	public void onUpdate(float dt) {
		if (!alive(target)) return;
		Body b = body();
		float px = centerX(), py = centerY();
		float dx = wantX() - px, dy = wantY() - py;
		float dist = (float) Math.sqrt(dx * dx + dy * dy);
		boolean near = dist <= stopDistance;
		if (near != arrived) {
			arrived = near;
			fire(near ? "onArrive" : "onLeave");
		}
		if (matchRotation) {
			float rot = target.getBody() != null ? (float) Math.toDegrees(target.getBody().getAngle()) : target.getActor().getRotation();
			if (b != null) b.setTransform(b.getPosition(), (float) Math.toRadians(rot));
			else actor().setRotation(rot);
		}
		if (near) {
			if (b != null) {
				if (followX && followY) setVelocity(0, 0);
				else if (followX) setVelocityX(0);
				else if (followY) setVelocityY(0);
			}
			return;
		}
		if (stiffness <= 0) {
			setCenter(px + dx, py + dy);
			if (b != null) setVelocity(0, 0);
			return;
		}
		float speed = dist * stiffness;
		float limit = maxSpeed > 0 ? maxSpeed : Float.MAX_VALUE;
		if (speed > limit) speed = limit;
		float ux = dx / dist, uy = dy / dist;
		if (b != null) {
			if (followX && followY) setVelocity(ux * speed, uy * speed);
			else if (followX) setVelocityX(ux * speed);
			else setVelocityY(uy * speed);
		} else {
			float move = Math.min(dist, speed * dt);
			setCenter(px + ux * move, py + uy * move);
		}
	}
}
