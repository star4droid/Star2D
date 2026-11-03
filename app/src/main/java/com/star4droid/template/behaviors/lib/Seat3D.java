package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Seat 3D", icon = "sofa", category = "Interaction", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onSit()", "onStand()"},
		desc = "A chair, bench or sofa the player can sit on: a 2D button appears when close, the camera glides smoothly to the seat (you can still look around) and the player's movement controller pauses; tap again to stand up in front of it.")
public class Seat3D extends Behavior {
	@Header("Player")
	@Param(desc = "The player element whose controller pauses (empty = what the main camera follows, or the camera itself).")
	public PlayerItem player;
	@Param(desc = "Camera that moves to the seat (empty = the main camera).")
	public PlayerItem camera;
	@Param(desc = "Usable within this distance.", unit = "m", min = 0.2, max = 20, step = 0.1)
	public float range = 1.8f;

	@Header("Seat pose")
	@Param(desc = "Eyes above the seat's position while sitting.", unit = "m", min = -5, max = 5, step = 0.01)
	public float eyeHeight = 1.1f;
	@Param(desc = "Eyes forward from the seat's center (along its front).", unit = "m", min = -5, max = 5, step = 0.01)
	public float eyeForward = 0.05f;
	@Param(desc = "Extra heading of the seat: 180 when the model's front is +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float seatYawOffset = 0;
	@Param(desc = "Looking down while seated (negative = down).", unit = "deg", min = -80, max = 60)
	public float seatedPitch = -5;
	@Param(desc = "Seconds the camera glides to and from the seat.", unit = "s", min = 0.05, max = 5, step = 0.05)
	public float moveTime = 0.7f;
	@Param(desc = "Drag to look around while seated.")
	public boolean lookWhileSeated = true;
	@Param(desc = "Stands up this far in front of the seat.", unit = "m", min = 0, max = 5, step = 0.05)
	public float standDistance = 0.8f;
	@Param(desc = "Third person: puts the player model on the seat and plays this clip (empty = keep the model where it is).")
	public String sitClip = "";

	@Header("Prompt")
	@Param(desc = "2D button to sit down / stand up (several seats may share it).")
	public PlayerItem button;
	@Param(desc = "2D text shown with the button (optional).")
	public PlayerItem promptText;
	@Param(desc = "Prompt to sit.")
	public String sitText = "Sit";
	@Param(desc = "Prompt to stand up.")
	public String standText = "Stand up";
	@Param(desc = "Tapping the seat itself also sits down.")
	public boolean tapObject = true;
	@Param(desc = "E key sits / stands (desktop, tests).")
	public boolean useKeyboard = true;

	private final Support3D.Prompt prompt = new Support3D.Prompt();
	private final List<Behavior> paused = new ArrayList<Behavior>();
	private final Vector3 c = new Vector3(), eye = new Vector3(), tmp = new Vector3();
	private boolean sitting, clicked, standing;
	private float standT;
	private PlayerItem who;
	private String prevMode = "NONE";
	private PlayerItem prevTarget;
	private boolean prevTouch;

	@Block(label = "sit down", desc = "Sits the player on this seat now.", labelAr = "اجلس", descAr = "يُجلس اللاعب على هذا المقعد الآن.", category = "Seat 3D", args = {})
	public void sit() {
		if (sitting) return;
		World3D w = world3d();
		Camera3D cam = Support3D.cameraOr(camera, w);
		Object3D seat = obj();
		if (seat == null) return;
		// the first-person player: the element the camera follows, or the camera itself
		who = player != null ? player : (cam != null && cam.target != null ? cam.target : cam);
		// pause the player's movement controllers
		paused.clear();
		if (who != null) {
			paused.addAll(Support3D.groupBehaviors(who, "movement", null));
			for (Behavior b : paused) b.setEnabled(false);
		}
		float yaw = seat.getYaw3() + seatYawOffset;
		seatEye(seat, yaw);
		Object3D body = Support3D.o(who);
		if (body != null && sitClip.length() > 0) {
			body.setPhysicsEnabled(false);
			body.setPosition3(seat.position.x, seat.position.y, seat.position.z);
			body.setYaw3(yaw);
			body.playAnimation(sitClip, true, 1);
		}
		if (cam != null) {
			prevMode = cam.followMode;
			prevTarget = cam.target;
			prevTouch = cam.touchLook;
			if (body == null || sitClip.length() == 0) {
				cam.stopFollow();
				cam.moveTo(eye.x, eye.y, eye.z, moveTime);
				cam.turnTo(seatedPitch, cam.yaw + Support3D.angleDiff(cam.yaw, yaw), 0, moveTime);
				cam.setTouchLook(lookWhileSeated);
			} else if (prevTarget != null) cam.follow(prevTarget, prevMode);
		}
		sitting = true;
		fire("onSit");
	}

	@Block(label = "stand up", desc = "Gets up and walks on.", labelAr = "قف", descAr = "ينهض ويكمل.", category = "Seat 3D", args = {})
	public void stand() {
		if (!sitting) return;
		Object3D seat = obj();
		World3D w = world3d();
		Camera3D cam = Support3D.cameraOr(camera, w);
		sitting = false;
		if (seat == null) return;
		float yaw = seat.getYaw3() + seatYawOffset;
		// stand point in front of the seat
		tmp.set(-com.badlogic.gdx.math.MathUtils.sinDeg(yaw), 0, -com.badlogic.gdx.math.MathUtils.cosDeg(yaw)).scl(standDistance).add(seat.position);
		Object3D body = Support3D.o(who);
		if (body != null) {
			float feet = body.position.y - body.getBottomY();
			float gy = Support3D.groundY(w, tmp.x, seat.position.y + 1.5f, tmp.z, 4f, body);
			float y = Float.isNaN(gy) ? body.position.y : gy + feet + 0.02f;
			body.setPhysicsEnabled(true);
			body.setPosition3(tmp.x, y, tmp.z);
			body.setVelocity3(0, 0, 0);
			body.setYaw3(yaw);
		}
		if (cam != null && (body == null || sitClip.length() == 0)) {
			float eh = 1.6f;
			if (who instanceof Camera3D) {
				Behavior fp = ((Camera3D) who).getBehavior("FirstPersonController3D");
				if (fp instanceof FirstPersonController3D) eh = ((FirstPersonController3D) fp).eyeHeight;
				float gy = Support3D.groundY(w, tmp.x, seat.position.y + 1.5f, tmp.z, 4f, null);
				cam.moveTo(tmp.x, (Float.isNaN(gy) ? seat.position.y : gy) + eh, tmp.z, moveTime);
			} else if (body != null) cam.moveTo(tmp.x, body.position.y + (eyeHeightOf(body) - (body.position.y - body.getBottomY())), tmp.z, moveTime);
			cam.turnTo(0, cam.yaw, 0, moveTime);
			cam.setTouchLook(prevTouch);
		}
		standing = true;
		standT = moveTime;
		fire("onStand");
	}

	private static float eyeHeightOf(Object3D body) {
		Behavior fp = body.getBehavior("FirstPersonController3D");
		return fp instanceof FirstPersonController3D ? ((FirstPersonController3D) fp).eyeHeight : 1.6f;
	}

	@Block(label = "toggle seat", desc = "Sits when standing, stands when seated.", labelAr = "بدّل الجلوس", descAr = "يجلس إن كان واقفاً وينهض إن كان جالساً.", category = "Seat 3D", args = {})
	public void toggle() {
		if (sitting) stand();
		else sit();
	}

	@Block(label = "is seated", desc = "True while the player sits here.", labelAr = "جالس", descAr = "صح أثناء جلوس اللاعب هنا.", category = "Seat 3D", args = {})
	public boolean isSitting() {
		return sitting;
	}

	private void seatEye(Object3D seat, float yaw) {
		eye.set(-com.badlogic.gdx.math.MathUtils.sinDeg(yaw), 0, -com.badlogic.gdx.math.MathUtils.cosDeg(yaw)).scl(eyeForward).add(seat.position).add(0, eyeHeight, 0);
	}

	private void resume() {
		for (Behavior b : paused) {
			if (b.item != null) b.item.setBehaviorEnabled(b.id(), true);
			else b.setEnabled(true);
		}
		paused.clear();
	}

	@Override
	public void onClick() {
		if (tapObject) clicked = true;
	}

	@Override
	public void onDisable() {
		if (sitting) {
			stand();
			resume();
			standing = false;
		}
		prompt.hide();
	}

	@Override
	public void onDestroy() {
		prompt.dispose();
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null) return;
		if (standing) {
			standT -= dt;
			if (standT <= 0) {
				standing = false;
				resume();
			}
			prompt.hide();
			return;
		}
		o.center(c);
		float d;
		if (sitting) d = 0;
		else {
			PlayerItem p = player != null ? player : null;
			d = Support3D.reach(w, p, c, range, false, 90);
		}
		boolean near = d >= 0;
		boolean tap = prompt.update(button, promptText, sitting ? standText : sitText, near, near ? d : Float.MAX_VALUE);
		if (near && useKeyboard && Support3D.keyJust(Input.Keys.E)) tap = true;
		if (clicked && near && !sitting) tap = true;
		clicked = false;
		if (tap) toggle();
	}
}
