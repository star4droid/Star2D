package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.nav.Nav;
import com.star4droid.template.nav.NavAgent;
import com.star4droid.template.nav.NavGridItem;

@BehaviorInfo(name = "Smart Chase", icon = "map-marker-path", category = "AI", targets = BehaviorInfo.Target.BOTH,
		events = {"onSeeTarget()", "onLoseTarget()", "onReachTarget()", "onNoPath()", "onPathBlocked()"},
		desc = "Runs after a target along the shortest way around the walls of a NavGrid (path finding): it finds a new way when the target moves or a door closes, and can run away instead. Physical elements move by velocity and still collide.")
public class SmartChase extends Behavior {
	public enum Facing { NONE, MIRROR, TURN }

	@Header("Target")
	@Param(desc = "The element to chase (usually the player).")
	public PlayerItem target;
	@Param(desc = "The NavGrid element to walk on (empty = the grid of the scene).")
	public NavGridItem navGrid;
	@Param(desc = "Starts chasing when the target is closer than this (0 = always chases).", unit = "px", min = 0, max = 100000)
	public float sightRange = 0;
	@Param(desc = "Gives up when the target is farther than this (0 = never gives up).", unit = "px", min = 0, max = 100000)
	public float giveUpRange = 0;
	@Param(desc = "Runs away from the target instead of after it.")
	public boolean flee = false;
	@Param(desc = "When fleeing: how far it tries to get from the target.", unit = "px", min = 10, max = 100000)
	public float fleeDistance = 400;

	@Header("Motion")
	@Param(desc = "Running speed.", unit = "px/s", min = 0, max = 100000)
	public float speed = 120;
	@Param(desc = "Stops this close to the target and raises onReachTarget (0 = right next to it).", unit = "px", min = 0, max = 100000)
	public float stopDistance = 0;
	@Param(desc = "Looks for a new way at most this often while the target moves.", unit = "s", min = 0.05, max = 10, step = 0.05)
	public float repathTime = 0.35f;

	@Header("Facing")
	@Param(desc = "MIRROR: flip the image toward the movement (side views). TURN: rotate the element toward it (top-down).")
	public Facing facing = Facing.MIRROR;
	@Param(desc = "The image is drawn facing left.")
	public boolean artFacesLeft = false;
	@Param(desc = "TURN: angle of the image when it faces right (0 = right, 90 = up).", unit = "deg", min = -360, max = 360)
	public float faceOffset = 0;

	private boolean chasing;
	private NavAgent agent;

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
		if (!alive(target)) {
			if (chasing) lose();
			return;
		}
		float d = distance();
		if (!chasing && (sightRange <= 0 || d <= sightRange)) start();
		else if (chasing && giveUpRange > 0 && d > giveUpRange) lose();
		if (chasing && agent != null) {
			agent.setSpeed(speed);
			agent.setStopDistance(stopDistance);
		}
	}

	private float distance() {
		float dx = cx(target) - centerX(), dy = cy(target) - centerY();
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	private void start() {
		Object grid = navGrid != null ? navGrid : null;
		agent = flee ? Nav.flee(item, grid, target, speed) : Nav.chase(item, grid, target, speed);
		if (agent == null) return;
		agent.setListener(events);
		agent.setFacing(facing == Facing.MIRROR ? 1 : facing == Facing.TURN ? 2 : 0, artFacesLeft, faceOffset);
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

	// ------------------------------------------------------------ blocks

	@Block(label = "set smart chase target", desc = "Chases another element.", labelAr = "اضبط هدف المطاردة الذكية", descAr = "يطارد عنصراً آخر.", category = "Smart chase", args = {"target"})
	public void setTarget(PlayerItem t) {
		target = t;
		if (chasing) {
			chasing = false;
			Nav.stop(item);
		}
	}

	@Block(label = "set smart chase speed", desc = "Running speed (px/s).", labelAr = "اضبط سرعة المطاردة الذكية", descAr = "سرعة الجري (بكسل/ثانية).", category = "Smart chase", args = {"speed"})
	public void setSpeed(float v) {
		speed = v;
	}

	@Block(label = "start smart chase", desc = "Chases now, whatever the distance.", labelAr = "ابدأ المطاردة الذكية", descAr = "يطارد الآن مهما كانت المسافة.", category = "Smart chase")
	public void startChasing() {
		if (!chasing && alive(target)) start();
	}

	@Block(label = "stop smart chase", desc = "Stops and stands still.", labelAr = "أوقف المطاردة الذكية", descAr = "يتوقف ويقف مكانه.", category = "Smart chase")
	public void stopChasing() {
		if (chasing) lose();
	}

	@Block(label = "switch to fleeing", desc = "Runs away from the target (false: chases it again).", labelAr = "بدّل إلى الهروب", descAr = "يهرب من الهدف (خطأ: يطارده من جديد).", category = "Smart chase", args = {"on"})
	public void setFlee(boolean on) {
		if (flee == on) return;
		flee = on;
		if (chasing) {
			chasing = false;
			start();
		}
	}

	@Block(label = "is smart chasing", desc = "True while it runs after (or away from) the target.", labelAr = "يطارد بذكاء", descAr = "صحيح عندما يجري خلف الهدف (أو هارباً منه).", category = "Smart chase")
	public boolean isChasing() {
		return chasing;
	}
}
