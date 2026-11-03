package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.utils.Array;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sets;

@BehaviorInfo(name = "Moving platform", icon = "elevator-passenger", category = "Movement", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onReachA()", "onReachB()"},
		desc = "A platform that glides between two points (lifts, moving bridges, sliding doors) and carries whatever stands on it. Becomes kinematic while it runs.")
@Requires(body = true, note = "Needs a physical element (it becomes kinematic while the game runs).")
@Sets({"type=KINEMATIC", "gravityScale=0", "fixedRotation=true"})
public class MovingPlatform extends Behavior {
	public enum Axis { HORIZONTAL, VERTICAL }

	@Header("Route")
	@Param(desc = "Range mode: move left/right or up/down from the start.")
	public Axis axis = Axis.HORIZONTAL;
	@Param(desc = "Range mode: how far it moves from the start to the other end (negative = left / down).", unit = "px", min = -10000, max = 10000)
	public float distance = 200;
	@Param(desc = "Points mode: an element marking the other end. When set, it replaces the range.")
	public PlayerItem pointB;
	@Param(desc = "Points mode: an element marking the first end. Empty = the start position of this platform.")
	public PlayerItem pointA;

	@Header("Motion")
	@Param(desc = "Speed along the route.", unit = "px/s", min = 0, max = 100000)
	public float speed = 80;
	@Param(desc = "How long it waits at each end.", unit = "s", min = 0, max = 60, step = 0.1)
	public float pause = 0.5f;
	@Param(desc = "Slows down smoothly near the ends.")
	public boolean smooth = true;
	@Param(desc = "Moves what stands on it sideways (needed for a player that sets its own speed).")
	public boolean carryRiders = true;

	private float ax, ay, bx, by, u, dirSign = 1, pauseLeft, prevX, prevY;
	private boolean havePrev, moving = true;

	public void onStart() {
		ax = centerX();
		ay = centerY();
		prevX = ax;
		prevY = ay;
		havePrev = true;
	}

	public void onDisable() {
		setVelocity(0, 0);
		havePrev = false;
	}

	public void onEnable() {
		havePrev = false;
	}

	@Block(label = "set platform speed", desc = "Speed (px/s).", labelAr = "اضبط سرعة المنصة", descAr = "السرعة (بكسل/ثانية).", category = "Moving platform", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set platform distance", desc = "How far it moves from the start (px).", labelAr = "اضبط مسافة المنصة", descAr = "المسافة التي تقطعها من البداية (بكسل).", category = "Moving platform", args = {"distance"})
	public void setDistance(float value) {
		distance = value;
	}

	@Block(label = "set platform pause", desc = "Seconds it waits at each end.", labelAr = "اضبط توقف المنصة", descAr = "الثواني التي تنتظرها عند كل طرف.", category = "Moving platform", args = {"seconds"})
	public void setPause(float seconds) {
		pause = seconds;
	}

	@Block(label = "platform go", desc = "true = moves, false = stands still (switches, levers).", labelAr = "تحريك المنصة", descAr = "true = تتحرك، false = تقف (للمفاتيح).", category = "Moving platform", args = {"move"})
	public void setMoving(boolean on) {
		moving = on;
		if (!on) setVelocity(0, 0);
	}

	@Block(label = "send platform to the other end", desc = "Reverses the direction now.", labelAr = "أرسل المنصة للطرف الآخر", descAr = "يعكس الاتجاه الآن.", category = "Moving platform")
	public void reverse() {
		dirSign = -dirSign;
		pauseLeft = 0;
	}

	@Block(label = "platform is moving", desc = "True while the platform is gliding.", labelAr = "المنصة تتحرك", descAr = "صحيح أثناء حركة المنصة.", category = "Moving platform")
	public boolean isMoving() {
		return moving && pauseLeft <= 0;
	}

	@Block(label = "platform progress", desc = "Position between the ends: 0 = first end, 1 = other end.", labelAr = "تقدم المنصة", descAr = "الموضع بين الطرفين: 0 = الطرف الأول، 1 = الطرف الآخر.", category = "Moving platform")
	public float getProgress() {
		return u;
	}

	private void resolveEnds() {
		if (pointA != null && alive(pointA)) {
			ax = cx(pointA);
			ay = cy(pointA);
		}
		if (pointB != null && alive(pointB)) {
			bx = cx(pointB);
			by = cy(pointB);
		} else if (axis == Axis.HORIZONTAL) {
			bx = ax + distance;
			by = ay;
		} else {
			bx = ax;
			by = ay + distance;
		}
	}

	public void onUpdate(float dt) {
		Body b = body();
		if (b == null) return;
		resolveEnds();
		float len = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
		if (moving && len > 0.5f) {
			if (pauseLeft > 0) {
				pauseLeft -= dt;
			} else if (speed > 0) {
				u += dirSign * speed * dt / len;
				if (u >= 1f) {
					u = 1f;
					dirSign = -1;
					pauseLeft = pause;
					fire("onReachB");
				} else if (u <= 0f) {
					u = 0f;
					dirSign = 1;
					pauseLeft = pause;
					fire("onReachA");
				}
			}
		}
		float e = smooth ? u * u * (3f - 2f * u) : u;
		float wantX = ax + (bx - ax) * e, wantY = ay + (by - ay) * e;
		float curX = centerX(), curY = centerY();
		// drive with a velocity (so things on top are pushed), closed loop on the real position
		boolean still = !moving || pauseLeft > 0 && Math.abs(wantX - curX) < 0.5f && Math.abs(wantY - curY) < 0.5f;
		if (still) setVelocity(0, 0);
		else setVelocity((wantX - curX) / Math.max(dt, 0.0001f), (wantY - curY) / Math.max(dt, 0.0001f));
		if (carryRiders && havePrev) carry(b, curX - prevX, curY - prevY);
		prevX = curX;
		prevY = curY;
		havePrev = true;
	}

	/** Moves bodies standing on top by the platform's sideways move, unless they already travel along with it. */
	private void carry(Body platform, float dxPx, float dyPx) {
		if (dxPx > -0.001f && dxPx < 0.001f) return;
		float k = Behavior.W / Math.max(1, stage.getSteps());
		float platVx = platform.getLinearVelocity().x / k;
		Array<Contact> contacts = platform.getWorld().getContactList();
		for (int i = 0; i < contacts.size; i++) {
			Contact c = contacts.get(i);
			if (!c.isTouching() || c.getFixtureA().isSensor() || c.getFixtureB().isSensor()) continue;
			boolean platIsA = c.getFixtureA().getBody() == platform;
			if (!platIsA && c.getFixtureB().getBody() != platform) continue;
			Body rider = platIsA ? c.getFixtureB().getBody() : c.getFixtureA().getBody();
			if (rider.getType() != BodyDef.BodyType.DynamicBody) continue;
			float ny = c.getWorldManifold().getNormal().y; // from A to B
			if (platIsA ? ny < 0.5f : ny > -0.5f) continue; // the rider must be on top
			float relVx = rider.getLinearVelocity().x / k - platVx;
			float weight = Math.abs(platVx) < 0.001f ? 1f : Math.min(1f, Math.abs(relVx) / Math.abs(platVx));
			if (weight < 0.05f) continue;
			rider.setTransform(rider.getPosition().x + dxPx * Behavior.W * weight, rider.getPosition().y, rider.getAngle());
		}
	}
}
