package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sets;

import java.util.Random;

@BehaviorInfo(name = "Bounce", icon = "tennis-ball", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		events = {"onBounce(PlayerItem other)", "onBounceEdge(String edge)"},
		desc = "A ball that never stops: keeps a constant speed and bounces off everything (Pong, Breakout, screen savers). Physical elements bounce off walls and other bodies; UI elements bounce off the screen edges.")
@Sets({"gravityScale=0", "linearDamping=0"})
public class Bounce extends Behavior {
	@Header("Ball")
	@Param(desc = "Speed of the ball.", unit = "px/s", min = 0, max = 100000)
	public float speed = 250;
	@Param(desc = "Keeps the speed constant after every bounce. Off: normal physics bounciness.")
	public boolean keepSpeed = true;
	@Param(desc = "The ball speeds up by this much at every bounce (up to the max speed).", unit = "px/s", min = 0, max = 100000, step = 1)
	public float speedGain = 0;
	@Param(desc = "Speed limit when the ball speeds up.", unit = "px/s", min = 0, max = 100000)
	public float maxSpeed = 450;
	@Param(desc = "Physical: how much energy a bounce keeps (1 = all).", min = 0, max = 1.5, step = 0.05)
	public float bounciness = 1;
	@Param(desc = "Never move almost straight along an axis: the path is kept at least this far from horizontal/vertical (stops endless ping-pong in Pong/Breakout).", unit = "deg", min = 0, max = 45, step = 1)
	public float minAngle = 12;

	@Header("Launch")
	@Param(desc = "Direction at the start: 0 = right, 90 = up, 45 = up-right.", unit = "deg", min = -360, max = 360)
	public float startAngle = 45;
	@Param(desc = "Pick a random diagonal direction at the start.")
	public boolean randomStart = false;
	@Param(desc = "Launch when the game starts. Off: call launch from your blocks.")
	public boolean launchOnStart = true;

	@Header("Screen edges")
	@Param(desc = "Physical: also bounce off the edges of the camera view (UI elements always bounce off the screen).")
	public boolean bounceOffView = false;

	private float current, vx, vy, savedRest = 0, savedFriction = 0.2f, savedGravity = 1;
	private boolean fixturesChanged, launched;
	private final Random random = new Random();
	private final float[] r = new float[4];

	public void onStart() {
		current = speed;
		Body b = body();
		if (b != null && b.getFixtureList().size > 0) {
			Fixture f0 = b.getFixtureList().get(0);
			savedRest = f0.getRestitution();
			savedFriction = f0.getFriction();
			for (int i = 0; i < b.getFixtureList().size; i++) {
				b.getFixtureList().get(i).setRestitution(bounciness);
				b.getFixtureList().get(i).setFriction(0f);
			}
			fixturesChanged = true;
		}
		if (launchOnStart) launch(randomStart ? randomAngle() : startAngle);
	}

	public void onDisable() {
		Body b = body();
		if (b != null) {
			if (fixturesChanged) {
				for (int i = 0; i < b.getFixtureList().size; i++) {
					b.getFixtureList().get(i).setRestitution(savedRest);
					b.getFixtureList().get(i).setFriction(savedFriction);
				}
			}
			setVelocity(0, 0);
		}
		fixturesChanged = false;
		launched = false;
		vx = 0;
		vy = 0;
	}

	public void onEnable() {
		Body b = body();
		if (b != null && !fixturesChanged && b.getFixtureList().size > 0) {
			Fixture f0 = b.getFixtureList().get(0);
			savedRest = f0.getRestitution();
			savedFriction = f0.getFriction();
			for (int i = 0; i < b.getFixtureList().size; i++) {
				b.getFixtureList().get(i).setRestitution(bounciness);
				b.getFixtureList().get(i).setFriction(0f);
			}
			fixturesChanged = true;
		}
		if (launchOnStart) launch(randomStart ? randomAngle() : startAngle);
	}

	private float randomAngle() {
		float a = 25f + random.nextFloat() * 40f; // 25..65 deg
		int q = random.nextInt(4);
		return a + q * 90f;
	}

	// ------------------------------------------------------------ blocks

	@Block(label = "launch ball", desc = "Sends the ball off in this direction (degrees, 0 = right, 90 = up).", labelAr = "أطلق الكرة", descAr = "يرسل الكرة في هذا الاتجاه (بالدرجات، 0 = يمين، 90 = أعلى).", category = "Bounce", args = {"degrees"})
	public void launch(float degrees) {
		current = speed;
		double a = Math.toRadians(degrees);
		vx = (float) Math.cos(a) * current;
		vy = (float) Math.sin(a) * current;
		launched = true;
		if (body() != null) setVelocity(vx, vy);
	}

	@Block(label = "launch ball randomly", desc = "Sends the ball off in a random diagonal direction.", labelAr = "أطلق الكرة عشوائياً", descAr = "يرسل الكرة في اتجاه قطري عشوائي.", category = "Bounce")
	public void launchRandom() {
		launch(randomAngle());
	}

	@Block(label = "set ball speed", desc = "Speed of the ball (px/s).", labelAr = "اضبط سرعة الكرة", descAr = "سرعة الكرة (بكسل/ثانية).", category = "Bounce", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
		current = value;
	}

	@Block(label = "reverse ball", desc = "Sends the ball back the way it came.", labelAr = "اعكس الكرة", descAr = "يعيد الكرة من حيث أتت.", category = "Bounce")
	public void reverse() {
		vx = -vx;
		vy = -vy;
		Body b = body();
		if (b != null) b.setLinearVelocity(-b.getLinearVelocity().x, -b.getLinearVelocity().y);
	}

	@Block(label = "ball speed", desc = "Current speed of the ball (px/s).", labelAr = "سرعة الكرة", descAr = "السرعة الحالية للكرة (بكسل/ثانية).", category = "Bounce")
	public float getBallSpeed() {
		return current;
	}

	public void onCollisionBegin(PlayerItem other) {
		if (speedGain > 0) current = Math.min(maxSpeed, current + speedGain);
		fire("onBounce", other);
	}

	public void onUpdate(float dt) {
		if (!launched) return;
		Body b = body();
		if (b != null) physicalUpdate(b);
		else screenUpdate(dt);
	}

	private void physicalUpdate(Body b) {
		float k = Behavior.W / Math.max(1, stage.getSteps());
		float x = b.getLinearVelocity().x / k, y = b.getLinearVelocity().y / k;
		if (bounceOffView) {
			WrapScreen.areaRect(this, 0, 0, 0, 0, 0, r);
			float hw = widthPx() * 0.5f, hh = heightPx() * 0.5f, cx = centerX(), cy = centerY();
			if (cx - hw < r[0] && x < 0) {
				x = -x;
				fire("onBounceEdge", "left");
			} else if (cx + hw > r[2] && x > 0) {
				x = -x;
				fire("onBounceEdge", "right");
			}
			if (cy - hh < r[1] && y < 0) {
				y = -y;
				fire("onBounceEdge", "bottom");
			} else if (cy + hh > r[3] && y > 0) {
				y = -y;
				fire("onBounceEdge", "top");
			}
		}
		float sp = (float) Math.sqrt(x * x + y * y);
		if (keepSpeed) {
			if (sp < 1f) {
				launch(startAngle); // stuck: send it off again
				return;
			}
			float s = (float) Math.sin(Math.toRadians(minAngle)), c = (float) Math.cos(Math.toRadians(minAngle));
			float ax = Math.abs(x) / sp, ay = Math.abs(y) / sp;
			if (ay < s) {
				ay = s;
				ax = c;
			} else if (ax < s) {
				ax = s;
				ay = c;
			}
			x = (x < 0 ? -ax : ax) * current;
			y = (y < 0 ? -ay : ay) * current;
		}
		setVelocity(x, y);
	}

	private void screenUpdate(float dt) {
		// UI element: its own velocity, bounces inside the screen
		float sp = (float) Math.sqrt(vx * vx + vy * vy);
		if (sp < 0.001f) return;
		vx = vx / sp * current;
		vy = vy / sp * current;
		float x = centerX() + vx * dt, y = centerY() + vy * dt;
		WrapScreen.areaRect(this, 0, 0, 0, 0, 0, r);
		float hw = widthPx() * 0.5f, hh = heightPx() * 0.5f;
		if (x - hw < r[0]) {
			x = r[0] + hw;
			vx = Math.abs(vx);
			bumped("left");
		} else if (x + hw > r[2]) {
			x = r[2] - hw;
			vx = -Math.abs(vx);
			bumped("right");
		}
		if (y - hh < r[1]) {
			y = r[1] + hh;
			vy = Math.abs(vy);
			bumped("bottom");
		} else if (y + hh > r[3]) {
			y = r[3] - hh;
			vy = -Math.abs(vy);
			bumped("top");
		}
		setCenter(x, y);
	}

	private void bumped(String edge) {
		if (speedGain > 0) current = Math.min(maxSpeed, current + speedGain);
		fire("onBounceEdge", edge);
	}
}
