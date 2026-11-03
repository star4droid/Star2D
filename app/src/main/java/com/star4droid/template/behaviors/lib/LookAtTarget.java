package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sets;

@BehaviorInfo(name = "Look at target", icon = "eye-target", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		events = {"onAligned()"},
		desc = "Turns the element to face another element or the finger, with a turning speed: turrets, eyes, arrows, aiming guns.")
@Sets({"fixedRotation=true"})
public class LookAtTarget extends Behavior {
	public enum Source { TARGET, FINGER }

	@Param(desc = "TARGET: face the element below. FINGER: face the point you touch.")
	public Source source = Source.TARGET;
	@Param(desc = "The element to face (TARGET).")
	public PlayerItem target;
	@Param(desc = "Angle of the image when it faces right (0 = right, 90 = up...).", unit = "deg", min = -360, max = 360)
	public float faceOffset = 0;
	@Param(desc = "How fast it turns. 0 = instantly.", unit = "deg/s", min = 0, max = 100000, step = 10)
	public float turnSpeed = 360;
	@Param(desc = "FINGER only: keep facing the last touch after the finger is lifted.")
	public boolean keepLastTouch = true;
	@Param(desc = "Counts as aligned when the angle difference is smaller than this (onAligned).", unit = "deg", min = 0, max = 90, step = 0.5)
	public float tolerance = 3;

	private final Vector2 tmp = new Vector2();
	private float lastX, lastY;
	private boolean haveTouch, aligned;

	@Block(label = "set look target", desc = "Faces another element.", labelAr = "اضبط هدف النظر", descAr = "يواجه عنصراً آخر.", category = "Look at target", args = {"target"})
	public void setTarget(PlayerItem t) {
		target = t;
		source = Source.TARGET;
	}

	@Block(label = "look at finger", desc = "true = face the finger, false = face the target element.", labelAr = "انظر إلى الإصبع", descAr = "true = يواجه الإصبع، false = يواجه العنصر الهدف.", category = "Look at target", args = {"finger"})
	public void setLookAtFinger(boolean finger) {
		source = finger ? Source.FINGER : Source.TARGET;
	}

	@Block(label = "set turn speed", desc = "Degrees per second, 0 = instantly.", labelAr = "اضبط سرعة الالتفات", descAr = "درجة في الثانية، 0 = فوراً.", category = "Look at target", args = {"degrees"})
	public void setTurnSpeed(float value) {
		turnSpeed = value;
	}

	@Block(label = "look at point", desc = "Faces a point (px) from now on, until a finger touches the screen.", labelAr = "انظر إلى نقطة", descAr = "يواجه نقطة (بكسل) من الآن حتى يلمس إصبع الشاشة.", category = "Look at target", args = {"x", "y"})
	public void lookAtPoint(float x, float y) {
		lastX = x;
		lastY = y;
		haveTouch = true;
		source = Source.FINGER;
		keepLastTouch = true;
	}

	@Block(label = "angle to target", desc = "Angle (degrees) from this element to what it faces.", labelAr = "الزاوية إلى الهدف", descAr = "الزاوية (بالدرجات) من هذا العنصر إلى ما يواجهه.", category = "Look at target")
	public float getAngleToTarget() {
		return (float) Math.toDegrees(Math.atan2(lastY - centerY(), lastX - centerX()));
	}

	@Block(label = "is facing the target", desc = "True when it is turned toward the target.", labelAr = "يواجه الهدف", descAr = "صحيح عندما يكون متجهاً نحو الهدف.", category = "Look at target")
	public boolean isAligned() {
		return aligned;
	}

	private float angle() {
		Body b = body();
		return b != null ? (float) Math.toDegrees(b.getAngle()) : actor().getRotation();
	}

	private void setAngle(float deg) {
		Body b = body();
		if (b != null) b.setTransform(b.getPosition(), (float) Math.toRadians(deg));
		else actor().setRotation(deg);
	}

	private static float wrap(float deg) {
		while (deg > 180) deg -= 360;
		while (deg < -180) deg += 360;
		return deg;
	}

	/** Reads the finger in the space of this element (px). */
	private boolean finger() {
		if (Gdx.input == null || !Gdx.input.isTouched()) return false;
		tmp.set(Gdx.input.getX(), Gdx.input.getY());
		if (isUI()) {
			stage.UiStage.screenToStageCoordinates(tmp);
			lastX = tmp.x;
			lastY = tmp.y;
		} else {
			stage.GameStage.screenToStageCoordinates(tmp);
			lastX = tmp.x / Behavior.W;
			lastY = tmp.y / Behavior.W;
		}
		return true;
	}

	public void onUpdate(float dt) {
		if (source == Source.TARGET) {
			if (!alive(target)) return;
			lastX = cx(target);
			lastY = cy(target);
		} else {
			boolean touched = finger();
			if (touched) haveTouch = true;
			else if (!keepLastTouch || !haveTouch) return;
		}
		float dx = lastX - centerX(), dy = lastY - centerY();
		if (dx * dx + dy * dy < 1f) return;
		float want = (float) Math.toDegrees(Math.atan2(dy, dx)) - faceOffset;
		float cur = angle(), diff = wrap(want - cur), step = turnSpeed * dt;
		if (turnSpeed <= 0 || Math.abs(diff) <= step) setAngle(cur + diff);
		else setAngle(cur + (diff < 0 ? -step : step));
		boolean now = Math.abs(wrap(want - angle())) <= tolerance;
		if (now != aligned) {
			aligned = now;
			if (now) fire("onAligned");
		}
	}
}
