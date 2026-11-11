package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.nav.Nav;
import com.star4droid.template.nav.NavAgent;
import com.star4droid.template.nav.NavGridItem;

@BehaviorInfo(name = "Smart Chase 3D", icon = "map-marker-path", category = "AI", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onSeeTarget()", "onLoseTarget()", "onReachTarget()", "onNoPath()", "onPathBlocked()"},
		desc = "Walks after a target along the shortest way around obstacles on a NavGrid laid on the ground (path finding); finds a new way when the target moves or the way gets blocked, or runs away. Dynamic bodies keep falling with gravity.")
public class SmartChase3D extends Behavior {
	@Header("Target")
	@Param(desc = "The element to chase (usually the player).")
	public PlayerItem target;
	@Param(desc = "The NavGrid element to walk on (empty = the grid of the scene).")
	public NavGridItem navGrid;
	@Param(desc = "Starts chasing when the target is closer than this (0 = always chases).", unit = "m", min = 0, max = 100000, step = 0.5)
	public float sightRange = 0;
	@Param(desc = "Gives up when the target is farther than this (0 = never gives up).", unit = "m", min = 0, max = 100000, step = 0.5)
	public float giveUpRange = 0;
	@Param(desc = "Runs away from the target instead of after it.")
	public boolean flee = false;
	@Param(desc = "When fleeing: how far it tries to get from the target.", unit = "m", min = 1, max = 100000, step = 0.5)
	public float fleeDistance = 15;

	@Header("Motion")
	@Param(desc = "Walking speed.", unit = "m/s", min = 0, max = 1000, step = 0.1)
	public float speed = 3;
	@Param(desc = "Stops this close to the target and raises onReachTarget (0 = right next to it).", unit = "m", min = 0, max = 1000, step = 0.1)
	public float stopDistance = 1.2f;
	@Param(desc = "Looks for a new way at most this often while the target moves.", unit = "s", min = 0.05, max = 10, step = 0.05)
	public float repathTime = 0.4f;
	@Param(desc = "Turns to face where it walks.")
	public boolean faceDirection = true;
	@Param(desc = "Extra heading when the model faces +Z instead of -Z.", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;

	private boolean chasing;
	private NavAgent agent;
	private final Vector3 a = new Vector3(), b = new Vector3();

	private final NavAgent.Listener events = new NavAgent.Listener() {
		public void onReach() {
			fire("onReachTarget");
		}

		public void onNoPath() {
			fire("onNoPath");
		}

		public void onBlocked() {
			fire("onPathBlocked");
		}

		public void onEnd() {
		}
	};

	@Override
	public void onUpdate(float dt) {
		if (target == null || !pos3(target, b) || !pos3(item, a)) {
			if (chasing) lose();
			return;
		}
		if (target instanceof com.star4droid.template.d3.Object3D && ((com.star4droid.template.d3.Object3D) target).isDestroyed()) {
			if (chasing) lose();
			return;
		}
		float dx = b.x - a.x, dz = b.z - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
		if (!chasing && (sightRange <= 0 || d <= sightRange)) start();
		else if (chasing && giveUpRange > 0 && d > giveUpRange) lose();
		if (chasing && agent != null) {
			agent.setSpeed(speed);
			agent.setStopDistance(stopDistance);
		}
	}

	private void start() {
		agent = flee ? Nav.flee(item, navGrid, target, speed) : Nav.chase(item, navGrid, target, speed);
		if (agent == null) return;
		agent.setListener(events);
		agent.setFacing(faceDirection ? 2 : 0, false, modelYawOffset);
		agent.setStopDistance(stopDistance);
		agent.setFleeDistance(fleeDistance);
		agent.setRepathTime(repathTime);
		chasing = true;
		fire("onSeeTarget");
	}

	private void lose() {
		chasing = false;
		Nav.stop(item);
		fire("onLoseTarget");
	}

	@Override
	public void onDisable() {
		if (chasing) Nav.stop(item);
		chasing = false;
	}

	@Block(label = "set smart chase 3D target", desc = "Chases another element.", labelAr = "اضبط هدف المطاردة الذكية ثلاثية الأبعاد", descAr = "يطارد عنصراً آخر.", category = "Smart chase 3D", args = {"target"})
	public void setTarget(PlayerItem t) {
		target = t;
		if (chasing) {
			chasing = false;
			Nav.stop(item);
		}
	}

	@Block(label = "set smart chase 3D speed", desc = "Walking speed (m/s).", labelAr = "اضبط سرعة المطاردة الذكية ثلاثية الأبعاد", descAr = "سرعة المشي (م/ث).", category = "Smart chase 3D", args = {"speed"})
	public void setSpeed(float v) {
		speed = v;
	}

	@Block(label = "start smart chase 3D", desc = "Chases now, whatever the distance.", labelAr = "ابدأ المطاردة الذكية ثلاثية الأبعاد", descAr = "يطارد الآن مهما كانت المسافة.", category = "Smart chase 3D")
	public void startChasing() {
		if (!chasing && target != null) start();
	}

	@Block(label = "stop smart chase 3D", desc = "Stops and stands still.", labelAr = "أوقف المطاردة الذكية ثلاثية الأبعاد", descAr = "يتوقف ويقف مكانه.", category = "Smart chase 3D")
	public void stopChasing() {
		if (chasing) lose();
	}

	@Block(label = "is smart chasing 3D", desc = "True while it walks after (or away from) the target.", labelAr = "يطارد بذكاء ثلاثي الأبعاد", descAr = "صحيح عندما يمشي خلف الهدف (أو هارباً منه).", category = "Smart chase 3D")
	public boolean isChasing() {
		return chasing;
	}
}
