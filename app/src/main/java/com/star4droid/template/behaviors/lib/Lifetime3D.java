package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Lifetime 3D", icon = "timer-off", category = "Gameplay", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onExpire()"},
		desc = "Removes the element after some seconds, shrinking or fading away at the end: spawned enemies, effects, debris, bullet holes, temporary platforms.")
public class Lifetime3D extends Behavior {
	@Header("Time")
	@Param(desc = "Seconds before it is removed.", unit = "s", min = 0.05, max = 3600, step = 0.05)
	public float lifetime = 5;
	@Param(desc = "Shrinks to nothing during the last part.", unit = "s", min = 0, max = 60, step = 0.05)
	public float shrinkTime = 0.3f;
	@Param(desc = "Fades out during the last part (transparent materials).", unit = "s", min = 0, max = 60, step = 0.05)
	public float fadeTime = 0;
	@Param(desc = "Only hides it (can be shown again) instead of removing it.")
	public boolean hideOnly = false;

	private float age, sx = 1, sy = 1, sz = 1, alpha = 1;
	private boolean done, got;

	/** Adds a Lifetime 3D to an element (effects copied by other behaviors). */
	public static void after(Object3D o, float seconds) {
		if (o == null) return;
		Lifetime3D l = new Lifetime3D();
		l.lifetime = Math.max(0.05f, seconds);
		l.shrinkTime = Math.min(0.3f, seconds * 0.5f);
		o.addBehavior(l);
	}

	@Block(label = "add life time", desc = "Lives this many seconds longer.", labelAr = "أضف وقت حياة", descAr = "يعيش هذه الثواني الإضافية.", category = "Lifetime 3D", args = {"seconds"})
	public void addTime(float s) {
		lifetime += s;
	}

	@Block(label = "time left", desc = "Seconds before it is removed.", labelAr = "الوقت المتبقي", descAr = "الثواني قبل إزالته.", category = "Lifetime 3D", args = {})
	public float getTimeLeft() {
		return Math.max(0, lifetime - age);
	}

	@Block(label = "restart life time", desc = "Counts from zero again.", labelAr = "أعد عداد الحياة", descAr = "يعد من الصفر مجدداً.", category = "Lifetime 3D", args = {})
	public void restart() {
		age = 0;
		done = false;
		Object3D o = obj();
		if (o != null && got) {
			o.setScale3(sx, sy, sz);
			if (fadeTime > 0) o.setOpacity(alpha);
			o.setVisible(true);
		}
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null || done) return;
		if (!got) {
			got = true;
			sx = o.scale.x;
			sy = o.scale.y;
			sz = o.scale.z;
			alpha = o.getOpacity();
		}
		age += dt;
		float left = lifetime - age;
		if (shrinkTime > 0 && left < shrinkTime && !o.hasBody()) {
			float k = Math.max(0.001f, left / shrinkTime);
			o.setScale3(sx * k, sy * k, sz * k);
		}
		if (fadeTime > 0 && left < fadeTime) o.setOpacity(alpha * Math.max(0, left / fadeTime));
		if (left <= 0) {
			done = true;
			fire("onExpire");
			if (hideOnly) o.setVisible(false);
			else o.destroy();
		}
	}
}
