package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Respawn 3D", icon = "history", category = "Gameplay", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D", "CAMERA3D"},
		events = {"onRespawn(int count)", "onCheckpoint()"},
		desc = "Brings the player (or a car, a ball) back to the start or the last checkpoint when it falls off the world or dies (Health 3D): stopped, upright and facing the saved way. Checkpoints are saved from blocks or Trigger 3D events.")
public class Respawn3D extends Behavior {
	@Header("When")
	@Param(desc = "Respawns when it falls below this height.", unit = "m", min = -100000, max = 100000, step = 1)
	public float fallHeight = -25;
	@Param(desc = "Respawns after its Health 3D reaches 0 (and revives it).")
	public boolean onDeath = true;
	@Param(desc = "Waits this long after death.", unit = "s", min = 0, max = 60, step = 0.1)
	public float deathDelay = 2;

	@Header("Where")
	@Param(desc = "Lifts it this much above the saved point.", unit = "m", min = 0, max = 50, step = 0.05)
	public float lift = 0.5f;
	@Param(desc = "Sound on respawn.")
	@Sound
	public String sound = "";
	@Param(desc = "Shakes the camera on respawn (0 = no).", min = 0, max = 5, step = 0.1)
	public float shake = 0;

	private final Vector3 point = new Vector3();
	private float yaw, deadFor;
	private boolean has;
	private int count;

	@Block(label = "respawn now", desc = "Goes back to the saved point now.", labelAr = "أعد الظهور الآن", descAr = "يعود للنقطة المحفوظة الآن.", category = "Respawn 3D", args = {})
	public void respawn() {
		if (!has) return;
		Object3D o = obj();
		if (o != null) {
			o.setPosition3(point.x, point.y + lift, point.z);
			o.setRotation3(0, yaw, 0);
			o.setVelocity3(0, 0, 0);
			o.setAngularVelocity3(0, 0, 0);
		} else if (camera3d() != null) {
			Camera3D c = camera3d();
			c.position.set(point.x, point.y + lift, point.z);
			c.yaw = yaw;
		}
		Behavior h = item.getBehavior("Health3D");
		if (h instanceof Health3D && ((Health3D) h).isDead()) {
			((Health3D) h).revive();
			for (Behavior b : item.getScript().getBehaviors()) if (!b.enabled && ("movement".equals(b.group()) || b instanceof Chase3D)) b.setEnabled(true);
		}
		Behavior car = item.getBehavior("Car3D");
		if (car instanceof Car3D) ((Car3D) car).resetCar();
		count++;
		if (sound.length() > 0) Support3D.play(sound, 1, 1, 0);
		Camera3D cam = mainCamera3d();
		if (shake > 0 && cam != null) cam.shake(shake);
		fire("onRespawn", count);
	}

	@Block(label = "save checkpoint here", desc = "Respawns where it is now.", labelAr = "احفظ نقطة هنا", descAr = "يعود للظهور حيث هو الآن.", category = "Respawn 3D", args = {})
	public void checkpointHere() {
		if (!Behavior.pos3(item, point)) return;
		yaw = Support3D.yawOfItem(item);
		has = true;
		fire("onCheckpoint");
	}

	@Block(label = "save checkpoint at", desc = "Respawns at x, y, z (m).", labelAr = "احفظ نقطة عند", descAr = "يعود للظهور عند x وy وz (م).", category = "Respawn 3D", args = {"x", "y", "z"})
	public void checkpointAt(float x, float y, float z) {
		point.set(x, y, z);
		has = true;
		fire("onCheckpoint");
	}

	@Block(label = "respawn count", desc = "How many times it came back.", labelAr = "عدد مرات العودة", descAr = "عدد مرات العودة للظهور.", category = "Respawn 3D", args = {})
	public int getCount() {
		return count;
	}

	@Override
	public void onStart() {
		if (Behavior.pos3(item, point)) {
			yaw = Support3D.yawOfItem(item);
			has = true;
		}
	}

	@Override
	public void onUpdate(float dt) {
		Vector3 p = new Vector3();
		if (!Behavior.pos3(item, p)) return;
		if (p.y < fallHeight) {
			respawn();
			return;
		}
		Behavior h = onDeath ? item.getBehavior("Health3D") : null;
		if (h instanceof Health3D && ((Health3D) h).isDead()) {
			deadFor += dt;
			if (deadFor >= deathDelay) {
				deadFor = 0;
				respawn();
			}
		} else deadFor = 0;
	}
}
