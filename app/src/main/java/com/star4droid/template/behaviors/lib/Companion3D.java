package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Combat3D;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Companion 3D", icon = "account-heart", category = "AI", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onHeal(amount)", "onTeleport()"},
		desc = "A friend that follows a leader (the player, or whoever the leader is now), walks or runs to stay close, catches up when left far behind, and heals the leader when its health is low (Health 3D) with a clip, an effect and a sound.")
@Exclusive("movement")
public class Companion3D extends Behavior {
	@Header("Follow")
	@Param(desc = "Who to follow (change it with the block when the player switches characters).")
	public PlayerItem leader;
	@Param(desc = "Stays this far from the leader.", unit = "m", min = 0.5, max = 30, step = 0.1)
	public float followDistance = 2.2f;
	@Param(desc = "Side offset: stands a bit to the side (+ right, - left of the leader).", unit = "m", min = -10, max = 10, step = 0.1)
	public float sideOffset = 1.2f;
	@Param(desc = "Walking speed.", unit = "m/s", min = 0, max = 100, step = 0.1)
	public float walkSpeed = 2.4f;
	@Param(desc = "Running speed (far behind).", unit = "m/s", min = 0, max = 100, step = 0.1)
	public float runSpeed = 6;
	@Param(desc = "Farther than this: appears next to the leader.", unit = "m", min = 2, max = 500, step = 1)
	public float teleportDistance = 25;
	@Param(desc = "Extra heading of the model (90 for models facing +X).", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	@Header("Healing")
	@Param(desc = "Heals the leader when its health is below this part (0..1, 0 = never heals).", min = 0, max = 1, step = 0.05)
	public float healBelow = 0.7f;
	@Param(desc = "Hit points given each time.", min = 0, max = 100000, step = 1)
	public float healAmount = 25;
	@Param(desc = "Seconds between two heals.", unit = "s", min = 0, max = 120, step = 0.5)
	public float healCooldown = 6;
	@Param(desc = "Clip played when healing.")
	public String healClip = "heal";
	@Param(desc = "Effect element copied on the leader when healed (optional).")
	public PlayerItem healEffect;
	@Param(desc = "Sound of a heal.")
	public String healSound = "";

	@Header("Clips")
	@Param(desc = "Clip while standing.")
	public String idleClip = "idle";
	@Param(desc = "Clip while walking.")
	public String walkClip = "walk";
	@Param(desc = "Clip while running.")
	public String runClip = "run";

	private final Support3D.Walker walker = new Support3D.Walker();
	private final Support3D.Clips clips = new Support3D.Clips();
	private final Vector3 lp = new Vector3(), goal = new Vector3();
	private float healCd = 2, healAnim;
	private boolean running;

	@Block(label = "set leader", desc = "Who to follow and heal.", labelAr = "اضبط القائد", descAr = "من يتبع ويعالج.", category = "Companion 3D", args = {"leader"})
	public void setLeader(PlayerItem it) {
		leader = it;
	}

	@Block(label = "heal leader now", desc = "Heals the leader now (cooldown applies).", labelAr = "عالج القائد الآن", descAr = "يعالج القائد الآن (مع وقت انتظار).", category = "Companion 3D", args = {})
	public void healNow() {
		if (healCd <= 0) heal();
	}

	@Block(label = "heal ready", desc = "0..1, 1 = ready.", labelAr = "جاهزية العلاج", descAr = "0 إلى 1، 1 = جاهز.", category = "Companion 3D", args = {})
	public float getHealReady() {
		return healCooldown <= 0 ? 1 : 1f - MathUtils.clamp(healCd / healCooldown, 0, 1);
	}

	private Health3D leaderHealth() {
		if (leader == null) return null;
		Behavior b = leader.getBehavior("Health3D");
		return b instanceof Health3D ? (Health3D) b : null;
	}

	private void heal() {
		Health3D h = leaderHealth();
		if (h == null || h.isDead()) return;
		healCd = healCooldown;
		h.heal(healAmount);
		Object3D o = obj();
		if (o != null && healClip.length() > 0) {
			o.playAnimationOnce(healClip, 1);
			healAnim = 0.8f;
		}
		if (pos3(leader, lp)) Combat3D.effect(healEffect, lp.x, lp.y, lp.z, 1.5f);
		if (healSound.length() > 0) Support3D.play(healSound, 1, 1, 0);
		fire("onHeal", healAmount);
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o != null) walker.setYaw(o.getYaw3() - modelYawOffset);
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || dt <= 0) return;
		dt = Math.min(dt, 0.05f);
		healCd = Math.max(0, healCd - dt);
		healAnim = Math.max(0, healAnim - dt);
		if (leader == null || !pos3(leader, lp) || !Support3D.alive3(leader)) {
			walker.stop(w, item, dt, true, 0.3f);
			clips.locomotion(o, 0, true, walkSpeed, runSpeed, idleClip, walkClip, runClip, "", 0.2f, true);
			return;
		}
		float lyaw = Support3D.yawOfItem(leader);
		ThirdPersonController3D c = Combat3D.controller(leader);
		if (c != null) lyaw -= c.modelYawOffset;
		// a spot behind and beside the leader
		float bx = MathUtils.sinDeg(lyaw) * followDistance, bz = MathUtils.cosDeg(lyaw) * followDistance;
		float sx = MathUtils.cosDeg(lyaw) * sideOffset, sz = -MathUtils.sinDeg(lyaw) * sideOffset;
		goal.set(lp.x + bx + sx, lp.y, lp.z + bz + sz);
		float d = (float) Math.sqrt((goal.x - o.position.x) * (goal.x - o.position.x) + (goal.z - o.position.z) * (goal.z - o.position.z));
		if (d > teleportDistance) {
			o.setPosition3(goal.x, lp.y + 0.5f, goal.z);
			fire("onTeleport");
			return;
		}
		if (d > followDistance * 3) running = true;
		else if (d < followDistance * 1.2f) running = false;
		float speed = running ? runSpeed : walkSpeed;
		if (healAnim > 0) speed = 0;
		walker.toward(w, item, goal, speed, 6, 540, dt, true, 0.4f, modelYawOffset, 0.3f);
		if (walker.speedNow < 0.1f && healAnim <= 0) {
			// stands still: looks the same way as the leader
			o.setYaw3(Support3D.approachAngle(o.getYaw3(), lyaw + modelYawOffset, 180 * dt));
		}
		clips.locomotion(o, walker.speedNow, true, walkSpeed, runSpeed, idleClip, walkClip, runClip, "", 0.2f, true);
		Health3D h = leaderHealth();
		if (h != null && healBelow > 0 && healCd <= 0 && !h.isDead() && h.getHealthPercent() < healBelow * 100f) heal();
	}
}
