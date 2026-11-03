package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sets;

@BehaviorInfo(name = "Spin", icon = "autorenew", category = "Motion", targets = BehaviorInfo.Target.BOTH,
		desc = "Rotates the element continuously (coins, saws, loading icons). Physical elements become kinematic while it runs.")
@Sets({"type=KINEMATIC"})
public class Spin extends Behavior {
	@Param(desc = "Degrees per second.", unit = "deg/s", min = 0, max = 100000)
	public float speed = 120;
	public boolean clockwise = true;

	private float applied; // rotation added on top of the saved one (editor degrees, clockwise positive)

	private void rotateBy(float deg) {
		Body b = body();
		if (b != null) b.setTransform(b.getPosition(), b.getAngle() - (float) Math.toRadians(deg));
		else actor().setRotation(actor().getRotation() - deg);
	}

	@Block(label = "set spin speed", desc = "Degrees per second.", labelAr = "اضبط سرعة الدوران", descAr = "الدرجات في الثانية.", category = "Spin", args = {"degrees"})
	public void setSpinSpeed(float degPerSecond) {
		speed = degPerSecond;
	}

	@Block(label = "reverse spin", desc = "Turns the other way.", labelAr = "اعكس الدوران", descAr = "يدور في الاتجاه المعاكس.", category = "Spin")
	public void reverse() {
		clockwise = !clockwise;
	}

	@Block(label = "spin clockwise", desc = "true = clockwise, false = counter-clockwise.", labelAr = "دوران مع عقارب الساعة", descAr = "true = مع عقارب الساعة، false = عكسها.", category = "Spin", args = {"clockwise"})
	public void setClockwise(boolean value) {
		clockwise = value;
	}

	@Block(label = "is spinning clockwise", desc = "True while it turns clockwise.", labelAr = "يدور مع عقارب الساعة", descAr = "صحيح عندما يدور مع عقارب الساعة.", category = "Spin")
	public boolean isClockwise() {
		return clockwise;
	}

	@Block(label = "spin speed", desc = "Current spin speed (degrees per second).", labelAr = "سرعة الدوران", descAr = "سرعة الدوران الحالية (درجة/ثانية).", category = "Spin")
	public float getSpinSpeed() {
		return speed;
	}

	public void onEnable() {
		applied = 0;
	}

	public void onDisable() {
		rotateBy(-applied);
		applied = 0;
	}

	public void onUpdate(float dt) {
		float d = (clockwise ? 1 : -1) * speed * dt;
		rotateBy(d);
		applied += d;
	}
}
