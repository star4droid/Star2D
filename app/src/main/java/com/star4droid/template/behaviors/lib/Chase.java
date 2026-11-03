package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Chase", icon = "run", category = "AI", targets = BehaviorInfo.Target.BOTH,
		events = {"onSeeTarget()", "onLoseTarget()", "onReachTarget()", "onResumeChase()"},
		desc = "Runs after a target (the player) when it comes close, gives up when it gets too far and can return home. Use it for enemies; put a Patrol on the same element for guards that chase when they see you.")
public class Chase extends Behavior {
	@Header("Target")
	@Param(desc = "The element to chase (usually the player).")
	public PlayerItem target;
	@Param(desc = "It starts chasing when the target is closer than this.", unit = "px", min = 0, max = 10000)
	public float sightRange = 300;
	@Param(desc = "It stops chasing when the target is farther than this (use more than the sight range).", unit = "px", min = 0, max = 10000)
	public float giveUpRange = 450;
	@Param(desc = "On: walls between it and the target hide the target (it only notices what it can see). Off: it senses the target through walls.")
	public boolean needLineOfSight = false;

	@Header("Motion")
	@Param(desc = "Running speed.", unit = "px/s", min = 0, max = 100000)
	public float speed = 120;
	@Param(desc = "How fast it reaches the speed. 0 = instantly.", unit = "px/s2", min = 0, max = 100000, step = 50)
	public float acceleration = 0;
	@Param(desc = "It stops this close to the target (and raises onReachTarget).", unit = "px", min = 0, max = 2000)
	public float stopDistance = 0;
	@Param(desc = "Only left/right (ground enemies that keep gravity). Off: it flies straight to the target and ignores gravity.")
	public boolean horizontalOnly = true;
	@Param(desc = "After giving up, walk back to where it started.")
	public boolean returnHome = false;

	@Header("Facing")
	@Param(desc = "Mirror the image toward the movement.")
	public boolean flipToDirection = true;
	@Param(desc = "The image is drawn facing left.")
	public boolean artFacesLeft = false;
	@Param(desc = "Turn the element toward the target (top-down enemies).")
	public boolean faceTarget = false;
	@Param(desc = "Angle of the image when it faces right (0 = right, 90 = up...).", unit = "deg", min = -360, max = 360)
	public float faceOffset = 0;

	private float homeX, homeY, vx, vy, baseScaleX = 1, savedGravity = 1;
	private boolean chasing, returning, reached, gravityChanged, savedFixed, fixedChanged, started;
	private final Vector2 p1 = new Vector2(), p2 = new Vector2();
	private boolean rayHit;
	private Body rayOwn;
	private final RayCastCallback ray = new RayCastCallback() {
		public float reportRayFixture(Fixture f, Vector2 point, Vector2 normal, float fraction) {
			if (f.isSensor() || f.getBody() == rayOwn) return -1f;
			if (f.getBody().getType() == BodyDef.BodyType.DynamicBody) return -1f; // only fixed scenery blocks the view
			rayHit = true;
			return 0f;
		}
	};

	public void onStart() {
		homeX = centerX();
		homeY = centerY();
		baseScaleX = Math.abs(actor().getScaleX());
		started = true;
		applyPhysics();
	}

	public void onEnable() {
		if (started) applyPhysics();
	}

	private void applyPhysics() {
		Body b = body();
		if (b == null) return;
		if (!horizontalOnly && !gravityChanged) {
			savedGravity = b.getGravityScale();
			b.setGravityScale(0);
			gravityChanged = true;
		}
		if (faceTarget && !fixedChanged) {
			savedFixed = b.isFixedRotation();
			b.setFixedRotation(true);
			fixedChanged = true;
		}
	}

	public void onDisable() {
		Body b = body();
		if (b != null) {
			if (horizontalOnly) setVelocityX(0);
			else setVelocity(0, 0);
			if (gravityChanged) b.setGravityScale(savedGravity);
			if (fixedChanged) b.setFixedRotation(savedFixed);
		}
		gravityChanged = false;
		fixedChanged = false;
		chasing = false;
		returning = false;
		vx = 0;
		vy = 0;
	}

	// ------------------------------------------------------------ blocks

	@Block(label = "set chase target", desc = "Chases another element.", labelAr = "اضبط هدف المطاردة", descAr = "يطارد عنصراً آخر.", category = "Chase", args = {"target"})
	public void setTarget(PlayerItem t) {
		target = t;
	}

	@Block(label = "set chase speed", desc = "Running speed (px/s).", labelAr = "اضبط سرعة المطاردة", descAr = "سرعة الجري (بكسل/ثانية).", category = "Chase", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set sight range", desc = "It starts chasing when the target is closer than this (px).", labelAr = "اضبط مدى الرؤية", descAr = "يبدأ المطاردة عندما يكون الهدف أقرب من هذه المسافة (بكسل).", category = "Chase", args = {"range"})
	public void setSightRange(float value) {
		sightRange = value;
	}

	@Block(label = "set give-up range", desc = "It stops chasing when the target is farther than this (px).", labelAr = "اضبط مدى الاستسلام", descAr = "يتوقف عن المطاردة عندما يكون الهدف أبعد من هذه المسافة (بكسل).", category = "Chase", args = {"range"})
	public void setGiveUpRange(float value) {
		giveUpRange = value;
	}

	@Block(label = "start chasing", desc = "Chases the target now, whatever the distance.", labelAr = "ابدأ المطاردة", descAr = "يطارد الهدف الآن مهما كانت المسافة.", category = "Chase")
	public void startChasing() {
		if (!chasing && alive(target)) {
			chasing = true;
			returning = false;
			reached = false;
			fire("onSeeTarget");
		}
	}

	@Block(label = "stop chasing", desc = "Gives up now.", labelAr = "أوقف المطاردة", descAr = "يستسلم الآن.", category = "Chase")
	public void stopChasing() {
		giveUp();
	}

	@Block(label = "is chasing", desc = "True while it runs after the target.", labelAr = "يطارد", descAr = "صحيح عندما يجري خلف الهدف.", category = "Chase")
	public boolean isChasing() {
		return chasing || returning;
	}

	@Block(label = "distance to target", desc = "Distance (px) to the target, -1 if there is none.", labelAr = "المسافة إلى الهدف", descAr = "المسافة (بكسل) إلى الهدف، -1 إن لم يوجد.", category = "Chase")
	public float getTargetDistance() {
		if (!alive(target)) return -1;
		float dx = cx(target) - centerX(), dy = cy(target) - centerY();
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	@Block(label = "chase speed", desc = "Current running speed setting (px/s).", labelAr = "سرعة المطاردة", descAr = "إعداد سرعة الجري (بكسل/ثانية).", category = "Chase")
	public float getSpeed() {
		return speed;
	}

	// ------------------------------------------------------------ logic

	private void giveUp() {
		if (!chasing) return;
		chasing = false;
		returning = returnHome;
		fire("onLoseTarget");
		if (!returning) halt();
	}

	private void halt() {
		vx = 0;
		vy = 0;
		if (body() != null) {
			if (horizontalOnly) setVelocityX(0);
			else setVelocity(0, 0);
		}
	}

	private boolean canSee(Body b) {
		if (!needLineOfSight || b == null) return true;
		p1.set(centerX() * Behavior.W, centerY() * Behavior.W);
		p2.set(cx(target) * Behavior.W, cy(target) * Behavior.W);
		if (p1.dst2(p2) < 0.0001f) return true;
		rayHit = false;
		rayOwn = b;
		b.getWorld().rayCast(ray, p1, p2);
		return !rayHit;
	}

	public void onUpdate(float dt) {
		Body b = body();
		if (!alive(target)) {
			if (chasing) giveUp();
			if (!returning) return;
		}
		float dist = alive(target) ? getTargetDistance() : Float.MAX_VALUE;
		if (!chasing && alive(target) && dist <= sightRange && canSee(b)) {
			chasing = true;
			returning = false;
			reached = false;
			fire("onSeeTarget");
		} else if (chasing && (dist > giveUpRange || !alive(target))) {
			giveUp();
		}
		float gx, gy; // where to go
		boolean go;
		if (chasing) {
			gx = cx(target);
			gy = cy(target);
			float stopAt = Math.max(stopDistance, 1f);
			go = horizontalOnly ? Math.abs(gx - centerX()) > stopAt : dist > stopAt;
			if (!go && !reached) {
				reached = true;
				fire("onReachTarget");
			} else if (go && reached) {
				reached = false;
				fire("onResumeChase"); // the target moved away again: run again
			}
		} else if (returning) {
			gx = homeX;
			gy = homeY;
			go = horizontalOnly ? Math.abs(gx - centerX()) > 3f : (Math.abs(gx - centerX()) > 3f || Math.abs(gy - centerY()) > 3f);
			if (!go) {
				returning = false;
				halt();
				return;
			}
		} else {
			return; // idle: other behaviors (Patrol...) are in charge
		}

		float dx = gx - centerX(), dy = horizontalOnly ? 0 : gy - centerY();
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		float tx = 0, ty = 0;
		if (go && len > 0.0001f) {
			tx = dx / len * speed;
			ty = dy / len * speed;
		}
		if (b != null) {
			float k = Behavior.W / Math.max(1, stage.getSteps());
			vx = b.getLinearVelocity().x / k;
			if (!horizontalOnly) vy = b.getLinearVelocity().y / k;
		}
		if (acceleration <= 0) {
			vx = tx;
			vy = ty;
		} else {
			float ddx = tx - vx, ddy = ty - vy, d = (float) Math.sqrt(ddx * ddx + ddy * ddy), step = acceleration * dt;
			if (d <= step) {
				vx = tx;
				vy = ty;
			} else {
				vx += ddx / d * step;
				vy += ddy / d * step;
			}
		}
		if (b != null) {
			if (horizontalOnly) setVelocityX(vx);
			else setVelocity(vx, vy);
		} else {
			setCenter(centerX() + vx * dt, centerY() + vy * dt);
		}
		if (flipToDirection && Math.abs(dx) > 1f) actor().setScaleX(((dx < 0) != artFacesLeft) ? -baseScaleX : baseScaleX);
		if (faceTarget && (dx != 0 || dy != 0)) {
			float rot = (float) Math.toDegrees(Math.atan2(gy - centerY(), gx - centerX())) - faceOffset;
			if (b != null) b.setTransform(b.getPosition(), (float) Math.toRadians(rot));
			else actor().setRotation(rot);
		}
	}
}
