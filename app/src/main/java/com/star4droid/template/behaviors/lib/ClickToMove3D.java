package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Click To Move 3D", icon = "map-marker-radius", category = "Movement", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onTargetSet(float x, float y, float z)", "onArrived()"},
		desc = "Tap the ground and the character walks there (strategy, adventure and point-and-click games). Optional marker element placed where you tapped. Taps on buttons and joysticks are ignored.")
@Exclusive("movement")
public class ClickToMove3D extends Behavior {
	@Header("Tap")
	@Param(desc = "Element moved to the tapped point (a ring, an arrow), hidden on arrival. Optional.")
	public PlayerItem marker;
	@Param(desc = "Only taps on these elements count as ground (empty = anything solid, or the floor height).")
	public PlayerItem ground;
	@Param(desc = "Longest finger move that still counts as a tap (drags move the camera).", unit = "px", min = 2, max = 200, step = 1)
	public float tapSlop = 18;

	@Header("Walk")
	@Param(desc = "Walking speed.", unit = "m/s", min = 0, max = 50, step = 0.1)
	public float speed = 3;
	@Param(desc = "Turning speed.", unit = "deg/s", min = 30, max = 3000, step = 10)
	public float turnSpeed = 540;
	@Param(desc = "Stops this close to the point.", unit = "m", min = 0.05, max = 10, step = 0.05)
	public float stopDistance = 0.2f;
	@Param(desc = "Extra heading when the model faces +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;
	@Param(desc = "Clip while walking (empty = none).")
	public String walkClip = "walk";
	@Param(desc = "Clip while standing (empty = none).")
	public String idleClip = "idle";

	private final Support3D.Walker walker = new Support3D.Walker();
	private final Support3D.Clips clips = new Support3D.Clips();
	private final Vector3 goal = new Vector3(), hit = new Vector3();
	private boolean moving, down;
	private float downX, downY;

	@Block(label = "walk to point", desc = "Walks to x, y, z (meters).", labelAr = "امشِ إلى نقطة", descAr = "يمشي إلى x وy وz (أمتار).", category = "Click to move 3D", args = {"x", "y", "z"})
	public void walkTo(float x, float y, float z) {
		goal.set(x, y, z);
		moving = true;
		placeMarker();
		fire("onTargetSet", x, y, z);
	}

	@Block(label = "stop walking", desc = "Stops where it is.", labelAr = "توقف عن المشي", descAr = "يتوقف في مكانه.", category = "Click to move 3D", args = {})
	public void stopWalking() {
		moving = false;
		Support3D.show(marker, false);
	}

	@Block(label = "is walking to a point", desc = "True until it arrives.", labelAr = "يمشي لنقطة", descAr = "صح حتى يصل.", category = "Click to move 3D", args = {})
	public boolean isMoving() {
		return moving;
	}

	@Block(label = "target x", desc = "X of the point it walks to.", labelAr = "x الهدف", descAr = "x للنقطة التي يمشي إليها.", category = "Click to move 3D", args = {})
	public float getTargetX() {
		return goal.x;
	}

	@Block(label = "target z", desc = "Z of the point it walks to.", labelAr = "z الهدف", descAr = "z للنقطة التي يمشي إليها.", category = "Click to move 3D", args = {})
	public float getTargetZ() {
		return goal.z;
	}

	private void placeMarker() {
		if (marker == null) return;
		Support3D.setPos(marker, goal.x, goal.y + 0.02f, goal.z);
		Support3D.show(marker, true);
	}

	@Override
	public void onStart() {
		Support3D.show(marker, false);
	}

	/** True when a 2D button / joystick / UI is under the screen point (y down). */
	private boolean onUi(float sx, float sy) {
		if (stage == null) return false;
		Stage[] st = {stage.getUiStage(), stage.getGameStage()};
		for (Stage s : st) {
			if (s == null) continue;
			Vector2 v = s.screenToStageCoordinates(new Vector2(sx, sy));
			Actor a = s.hit(v.x, v.y, true);
			if (a != null && !(a instanceof Object3D) && !(a instanceof com.star4droid.template.d3.Camera3D) && !(a instanceof com.star4droid.template.d3.Light3D)) return true;
		}
		return false;
	}

	private boolean groundPoint(World3D w, float sx, float sy) {
		if (w == null || w.camera == null) return false;
		Ray r = w.camera.getPickRay(sx, sy);
		Object3D o = obj();
		if (w.hasPhysics() && w.physics().raycast(r.origin.x, r.origin.y, r.origin.z, r.direction.x, r.direction.y, r.direction.z, 1000, o)) {
			if (ground == null || w.physics().hitObject == ground) {
				hit.set(w.physics().hitPoint);
				return true;
			}
			if (ground != null) return false;
		}
		Object3D p = w.pickRay(r, o);
		if (p != null && (ground == null || p == ground)) {
			hit.set(w.pickPoint);
			if (ground == null && o != null) hit.y = Math.min(hit.y, o.getBottomY() + 0.6f);
			return true;
		}
		// flat floor at the feet
		float fy = o != null ? o.getBottomY() : 0;
		return Intersector.intersectRayPlane(r, new Plane(Vector3.Y, -fy), hit);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		if (Gdx.input != null) {
			if (Gdx.input.justTouched()) {
				down = !onUi(Gdx.input.getX(), Gdx.input.getY());
				downX = Gdx.input.getX();
				downY = Gdx.input.getY();
			} else if (down && !Gdx.input.isTouched()) {
				down = false;
				float ux = Gdx.input.getX(), uy = Gdx.input.getY();
				if (Math.abs(ux - downX) <= tapSlop && Math.abs(uy - downY) <= tapSlop && groundPoint(w, ux, uy)) walkTo(hit.x, hit.y, hit.z);
			}
		}
		if (!moving) {
			walker.stop(w, item, dt, true, 0.3f);
			clips.play(o, idleClip, true, 1, 0.2f);
			return;
		}
		float left = walker.toward(w, item, goal, speed, 8, turnSpeed, dt, true, 0, modelYawOffset, 0.3f);
		clips.play(o, walker.speedNow > 0.1f ? walkClip : idleClip, true, 1, 0.2f);
		if (left <= stopDistance) {
			moving = false;
			Support3D.show(marker, false);
			fire("onArrived");
		}
	}
}
