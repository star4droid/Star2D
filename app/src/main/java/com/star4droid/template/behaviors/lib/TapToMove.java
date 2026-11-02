package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Tap to move", icon = "cursor-pointer", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		events = {"onArrive()", "onTapPoint(float x, float y)"},
		desc = "Moves where you touch: TAP walks to the tapped point (strategy, point-and-click), HOLD follows the finger while it is down, DRAG_ITEM lets you grab the element itself and drag it (puzzle pieces, cards). Touches on the buttons/joystick of the ignore list are not used.")
@Exclusive("movement")
public class TapToMove extends Behavior {
	public enum Mode { TAP, HOLD, DRAG_ITEM }

	@Header("Touch")
	@Param(desc = "TAP: go to the tapped point. HOLD: follow the finger while it is down. DRAG_ITEM: touch the element and drag it.")
	public Mode mode = Mode.TAP;
	@Param(desc = "Touches that start on these elements (buttons, joystick, panels) are ignored.")
	public List<PlayerItem> ignoreTouchesOn = new ArrayList<PlayerItem>();
	@Param(desc = "Optional element shown at the tapped point (TAP mode); it hides when the element arrives.")
	public PlayerItem marker;

	@Header("Movement")
	@Param(desc = "Walking speed (no upper limit).", unit = "px/s", min = 0)
	public float speed = 220;
	@Param(desc = "Counts as arrived when this close.", unit = "px", min = 0, max = 500)
	public float stopDistance = 4;
	@Param(desc = "Only left/right (platformer-style characters keep gravity). Off: moves in every direction and ignores gravity.")
	public boolean horizontalOnly = false;
	@Param(desc = "DRAG_ITEM: how stiffly the element follows the finger (physical elements; higher = tighter).", unit = "1/s", min = 1, max = 60, step = 1)
	public float dragStiffness = 20;
	@Param(desc = "Turn the element toward where it goes.")
	public boolean rotateToDirection = false;
	@Param(desc = "Mirror the image when going left.")
	public boolean flipToDirection = false;
	@Param(desc = "Angle of the image when it faces right (0 = right, 90 = up...).", unit = "deg", min = -360, max = 360)
	public float faceOffset = 0;

	private final Vector2 tmp = new Vector2();
	private float destX, destY, grabX, grabY, baseScaleX = 1, savedGravity = 1;
	private boolean hasDest, dragging, ignoredTouch, wasTouched, gravityChanged, started;

	public void onStart() {
		baseScaleX = Math.abs(actor().getScaleX());
		started = true;
		applyGravity();
	}

	public void onEnable() {
		if (started) applyGravity();
	}

	private void applyGravity() {
		Body b = body();
		if (b != null && !horizontalOnly && mode != Mode.DRAG_ITEM && !gravityChanged) {
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
		hasDest = false;
		dragging = false;
		hideMarker();
	}

	@Block(label = "move to point", desc = "Walks to a point (px) from your blocks.", labelAr = "تحرك إلى نقطة", descAr = "يتحرك إلى نقطة (بكسل) من البلوكات.", category = "Tap to move", args = {"x", "y"})
	public void moveTo(float x, float y) {
		destX = x;
		destY = y;
		hasDest = true;
	}

	@Block(label = "stop moving to point", desc = "Forgets the destination.", labelAr = "ألغِ الوجهة", descAr = "ينسى الوجهة.", category = "Tap to move")
	public void stopMoving() {
		hasDest = false;
		if (!isUI()) {
			if (horizontalOnly) setVelocityX(0);
			else setVelocity(0, 0);
		}
		hideMarker();
	}

	@Block(label = "set tap move speed", desc = "Speed (px/s).", labelAr = "اضبط سرعة التحرك", descAr = "السرعة (بكسل/ثانية).", category = "Tap to move", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "ignore touches on", desc = "Touches that start on this element (a button...) will not move this one.", labelAr = "تجاهل اللمسات على", descAr = "اللمسات التي تبدأ على هذا العنصر (زر...) لن تحرك هذا العنصر.", category = "Tap to move", args = {"element"})
	public void ignoreTouchesOnItem(PlayerItem it) {
		if (it != null && !ignoreTouchesOn.contains(it)) ignoreTouchesOn.add(it);
	}

	@Block(label = "has a destination", desc = "True while it walks to a point.", labelAr = "لديه وجهة", descAr = "صحيح أثناء السير إلى نقطة.", category = "Tap to move")
	public boolean hasDestination() {
		return hasDest;
	}

	@Block(label = "destination x", desc = "x (px) of the point it walks to.", labelAr = "س الوجهة", descAr = "س (بكسل) للنقطة التي يسير إليها.", category = "Tap to move")
	public float getDestX() {
		return destX;
	}

	@Block(label = "destination y", desc = "y (px) of the point it walks to.", labelAr = "ص الوجهة", descAr = "ص (بكسل) للنقطة التي يسير إليها.", category = "Tap to move")
	public float getDestY() {
		return destY;
	}

	public void onTouchStart(InputEvent event) {
		if (mode != Mode.DRAG_ITEM || Gdx.input == null) return;
		if (readFinger(isUI())) {
			grabX = tmp.x - centerX();
			grabY = tmp.y - centerY();
			dragging = true;
		}
	}

	public void onTouchEnd(InputEvent event) {
		dragging = false;
		if (mode == Mode.DRAG_ITEM && !isUI()) setVelocity(0, 0);
	}

	/** Finger position in px of the UI space (ui = true) or of the world; false if not touching. */
	private boolean readFinger(boolean ui) {
		if (Gdx.input == null || !Gdx.input.isTouched()) return false;
		tmp.set(Gdx.input.getX(), Gdx.input.getY());
		if (ui) {
			stage.UiStage.screenToStageCoordinates(tmp);
		} else {
			stage.GameStage.screenToStageCoordinates(tmp);
			tmp.scl(1f / Behavior.W);
		}
		return true;
	}

	private boolean touchIsIgnored() {
		if (ignoreTouchesOn == null || ignoreTouchesOn.isEmpty()) return false;
		for (int i = 0; i < ignoreTouchesOn.size(); i++) {
			PlayerItem it = ignoreTouchesOn.get(i);
			if (!alive(it)) continue;
			if (!readFinger(it.getBody() == null)) return false;
			if (Math.abs(tmp.x - cx(it)) <= wPx(it) * 0.5f && Math.abs(tmp.y - cy(it)) <= hPx(it) * 0.5f) return true;
		}
		return false;
	}

	private void setMarker(float x, float y) {
		if (!alive(marker)) return;
		Body mb = marker.getBody();
		if (mb != null) mb.setTransform(x * Behavior.W, y * Behavior.W, mb.getAngle());
		else marker.getActor().setPosition(x - marker.getActor().getWidth() * 0.5f, y - marker.getActor().getHeight() * 0.5f);
		marker.getActor().setVisible(true);
	}

	private void hideMarker() {
		if (alive(marker)) marker.getActor().setVisible(false);
	}

	public void onUpdate(float dt) {
		boolean touched = Gdx.input != null && Gdx.input.isTouched();
		boolean pressedNow = touched && !wasTouched;
		wasTouched = touched;
		if (pressedNow) ignoredTouch = touchIsIgnored();

		if (mode == Mode.DRAG_ITEM) {
			dragUpdate(touched, dt);
			return;
		}
		if (touched && !ignoredTouch && readFinger(isUI())) {
			if (mode == Mode.TAP && pressedNow) {
				destX = tmp.x;
				destY = tmp.y;
				hasDest = true;
				setMarker(destX, destY);
				fire("onTapPoint", destX, destY);
			} else if (mode == Mode.HOLD) {
				destX = tmp.x;
				destY = tmp.y;
				hasDest = true;
			}
		} else if (mode == Mode.HOLD && hasDest) {
			hasDest = false; // finger lifted
			halt();
		}
		if (!hasDest) return;

		float px = centerX(), py = centerY();
		float dx = destX - px, dy = horizontalOnly ? 0 : destY - py;
		float dist = (float) Math.sqrt(dx * dx + dy * dy);
		if (dist <= Math.max(stopDistance, 0.5f)) {
			hasDest = mode == Mode.HOLD && touched && !ignoredTouch; // HOLD keeps following
			halt();
			hideMarker();
			fire("onArrive");
			return;
		}
		float sp = Math.min(speed, dist / Math.max(dt, 0.0001f));
		float vx = dx / dist * sp, vy = dy / dist * sp;
		Body b = body();
		if (b != null) {
			if (horizontalOnly) setVelocityX(vx);
			else setVelocity(vx, vy);
		} else {
			setCenter(px + vx * dt, py + vy * dt);
		}
		if (flipToDirection && Math.abs(dx) > 1f) actor().setScaleX(dx < 0 ? -baseScaleX : baseScaleX);
		if (rotateToDirection) {
			float rot = (float) Math.toDegrees(Math.atan2(dy, dx)) - faceOffset;
			if (b != null) b.setTransform(b.getPosition(), (float) Math.toRadians(rot));
			else actor().setRotation(rot);
		}
	}

	private void halt() {
		Body b = body();
		if (b == null) return;
		if (horizontalOnly) setVelocityX(0);
		else setVelocity(0, 0);
	}

	private void dragUpdate(boolean touched, float dt) {
		if (!dragging) return;
		if (!touched) {
			dragging = false;
			if (!isUI()) setVelocity(0, 0);
			return;
		}
		if (!readFinger(isUI())) return;
		float wx = tmp.x - grabX, wy = tmp.y - grabY;
		if (horizontalOnly) wy = centerY();
		Body b = body();
		if (b == null) {
			setCenter(wx, wy);
		} else {
			float vx = (wx - centerX()) * dragStiffness, vy = (wy - centerY()) * dragStiffness;
			float m = (float) Math.sqrt(vx * vx + vy * vy);
			float cap = Math.max(450f, speed); // the follow speed grows with the element speed, never below the old safety value
			if (m > cap) {
				vx *= cap / m;
				vy *= cap / m;
			}
			if (horizontalOnly) setVelocityX(vx);
			else setVelocity(vx, vy);
		}
	}
}
