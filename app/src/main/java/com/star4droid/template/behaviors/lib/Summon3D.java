package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Combat3D;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Summon 3D", icon = "robot", category = "Combat", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onSummon(copy)"},
		desc = "Calls a helper next to the character for some seconds: a turret, a pet, a shield, a totem. The helper is a copy of a hidden template (give it Turret 3D, Chase 3D, Health 3D...). Button, cooldown, lifetime, how many at once.")
public class Summon3D extends Behavior {
	@Param(desc = "Summon button.")
	public PlayerItem button;
	@Param(desc = "U = summon (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Hidden element copied.")
	public PlayerItem template;
	@Param(desc = "Seconds the copy stays (0 = forever).", unit = "s", min = 0, max = 600, step = 0.5)
	public float lifetime = 8;
	@Param(desc = "Seconds before the next summon.", unit = "s", min = 0, max = 600, step = 0.5)
	public float cooldown = 12;
	@Param(desc = "Appears this far beside the character (+ right).", unit = "m", min = -20, max = 20, step = 0.1)
	public float side = 1.3f;
	@Param(desc = "Appears this far in front.", unit = "m", min = -20, max = 20, step = 0.1)
	public float front = 0.6f;
	@Param(desc = "Extra heading of the model (90 for models facing +X).", unit = "deg", min = -180, max = 180)
	public float modelYawOffset = 0;
	@Param(desc = "Clip played by the character.")
	public String clip = "";
	@Param(desc = "Effect copied where it appears (optional).")
	public PlayerItem effect;
	@Param(desc = "Sound of a summon.")
	public String sound = "";

	private EventListener l;
	private boolean queued;
	private float cd;

	@Block(label = "summon", desc = "Calls the helper now (cooldown applies).", labelAr = "استدعِ", descAr = "يستدعي المساعد الآن (مع وقت انتظار).", category = "Summon 3D", args = {})
	public void summon() {
		queued = true;
	}

	@Block(label = "summon ready", desc = "0..1, 1 = ready.", labelAr = "جاهزية الاستدعاء", descAr = "0 إلى 1، 1 = جاهز.", category = "Summon 3D", args = {})
	public float getReady() {
		return cooldown <= 0 ? 1 : 1f - MathUtils.clamp(cd / cooldown, 0, 1);
	}

	@Override
	public void onStart() {
		l = Support3D.onTap(button, new Runnable() {
			@Override
			public void run() {
				queued = true;
			}
		});
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(button, l);
	}

	@Override
	public void onUpdate(float dt) {
		cd = Math.max(0, cd - dt);
		if (useKeyboard && Support3D.keyJust(Input.Keys.U)) queued = true;
		Object3D o = obj();
		World3D w = world3d();
		if (queued && cd <= 0 && o != null && w != null && template instanceof Object3D) {
			cd = cooldown;
			float yaw = Combat3D.facing(item, modelYawOffset);
			float fx = -MathUtils.sinDeg(yaw), fz = -MathUtils.cosDeg(yaw), rx = MathUtils.cosDeg(yaw), rz = -MathUtils.sinDeg(yaw);
			float x = o.position.x + fx * front + rx * side, z = o.position.z + fz * front + rz * side;
			Object3D t = (Object3D) template;
			PlayerItem c = t.getClone(t.getName() + "_s" + GameplaySupport.nextId());
			if (c instanceof Object3D) {
				Object3D s = (Object3D) c;
				float g = Support3D.groundY(w, x, o.position.y + 3, z, 20, o);
				float feet = t.position.y - t.getBottomY();
				s.setPosition3(x, (Float.isNaN(g) ? o.getBottomY() : g) + feet, z);
				s.setYaw3(o.getYaw3());
				s.setVisible(true);
				if (lifetime > 0) Lifetime3D.after(s, lifetime);
				Combat3D.effect(effect, x, s.getBottomY(), z, 1);
				if (clip.length() > 0) o.playAnimationOnce(clip, 1);
				if (sound.length() > 0) Support3D.play(sound, 1, 1, 0);
				fire("onSummon", s);
			}
		}
		queued = false;
	}
}
