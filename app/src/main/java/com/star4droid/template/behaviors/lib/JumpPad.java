package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Jump pad", icon = "rocket-launch", category = "Movement", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onLaunch(PlayerItem who)"},
		desc = "Launches whatever touches it (trampolines, springs, cannons, boost pads) in a chosen direction. Choose who it affects with a list or a name filter; it can work as a sensor you pass through.")
@Requires(body = true, note = "Needs a physical element (usually STATIC).")
public class JumpPad extends Behavior {
	@Header("Launch")
	@Param(desc = "Launch direction: 90 = up, 0 = right, 180 = left, 270 = down.", unit = "deg", min = -360, max = 360, step = 5)
	public float angle = 90;
	@Param(desc = "Launch speed.", unit = "px/s", min = 0, max = 100000, step = 10)
	public float power = 420;
	@Param(desc = "On: adds to the current speed. Off: replaces it (the same jump every time).")
	public boolean additive = false;
	@Param(desc = "Only launches things that come from the front (the side the arrow points to), not from behind.")
	public boolean onlyFromFront = true;
	@Param(desc = "Time before the pad can launch again.", unit = "s", min = 0, max = 10, step = 0.05)
	public float cooldown = 0.15f;

	@Header("Who")
	@Param(desc = "Only these elements are launched. Empty = every moving body.")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Only elements whose name contains this text are launched (a poor man's tag, e.g. \"Player\"). Empty = any.")
	public String nameFilter = "";
	@Param(desc = "Things pass through the pad and are launched while they overlap it (no solid surface).")
	public boolean sensor = false;

	private float wait;
	private boolean sensorApplied;
	private final java.util.ArrayList<Boolean> oldSensor = new java.util.ArrayList<Boolean>();

	public void onStart() {
		applySensor();
	}

	public void onEnable() {
		applySensor();
	}

	private void applySensor() {
		Body b = body();
		if (b == null || !sensor || sensorApplied) return;
		oldSensor.clear();
		for (int i = 0; i < b.getFixtureList().size; i++) {
			oldSensor.add(b.getFixtureList().get(i).isSensor());
			b.getFixtureList().get(i).setSensor(true);
		}
		sensorApplied = true;
	}

	public void onDisable() {
		Body b = body();
		if (b != null && sensorApplied) {
			for (int i = 0; i < b.getFixtureList().size && i < oldSensor.size(); i++) b.getFixtureList().get(i).setSensor(oldSensor.get(i));
		}
		sensorApplied = false;
	}

	@Block(label = "set pad power", desc = "Launch speed (px/s).", labelAr = "اضبط قوة المنصة", descAr = "سرعة الإطلاق (بكسل/ثانية).", category = "Jump pad", args = {"power"})
	public void setPower(float value) {
		power = value;
	}

	@Block(label = "set pad angle", desc = "Launch direction in degrees (90 = up).", labelAr = "اضبط زاوية المنصة", descAr = "اتجاه الإطلاق بالدرجات (90 = أعلى).", category = "Jump pad", args = {"degrees"})
	public void setAngle(float value) {
		angle = value;
	}

	@Block(label = "add launch target", desc = "This element will be launched too (when the list is not empty only listed ones are).", labelAr = "أضف هدفاً للإطلاق", descAr = "سيُطلق هذا العنصر أيضاً (إن كانت القائمة غير فارغة فالمدرجون فقط).", category = "Jump pad", args = {"target"})
	public void addTarget(PlayerItem t) {
		if (t != null && !targets.contains(t)) targets.add(t);
	}

	@Block(label = "launch element", desc = "Launches an element now, whatever the filters.", labelAr = "أطلق عنصراً", descAr = "يطلق عنصراً الآن مهما كانت المرشحات.", category = "Jump pad", args = {"element"})
	public void launch(PlayerItem who) {
		doLaunch(who);
	}

	private boolean accepts(PlayerItem other) {
		if (other == null || other.getBody() == null || other.getBody().getType() == BodyDef.BodyType.StaticBody) return false;
		if (targets != null && !targets.isEmpty() && !targets.contains(other)) return false;
		if (nameFilter != null && nameFilter.length() > 0) {
			String n = other.getName();
			if (n == null || !n.contains(nameFilter)) return false;
		}
		return true;
	}

	private void doLaunch(PlayerItem who) {
		if (who == null || who.getBody() == null) return;
		Body ob = who.getBody();
		double a = Math.toRadians(angle);
		float k = Behavior.W / Math.max(1, stage.getSteps());
		float vx = (float) Math.cos(a) * power * k, vy = (float) Math.sin(a) * power * k;
		if (additive) ob.setLinearVelocity(ob.getLinearVelocity().x + vx, ob.getLinearVelocity().y + vy);
		else ob.setLinearVelocity(vx, vy);
		ob.setAwake(true);
		wait = cooldown;
		fire("onLaunch", who);
	}

	public void onUpdate(float dt) {
		if (wait > 0) wait -= dt;
	}

	public void onCollisionBegin(PlayerItem other) {
		if (wait > 0 || !accepts(other)) return;
		if (onlyFromFront) {
			double a = Math.toRadians(angle);
			float dx = cx(other) - centerX(), dy = cy(other) - centerY();
			if (dx * (float) Math.cos(a) + dy * (float) Math.sin(a) <= 0) return;
		}
		doLaunch(other);
	}
}
