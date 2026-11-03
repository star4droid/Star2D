package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Footsteps 3D", icon = "footprint", category = "Audio", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D", "CAMERA3D"},
		events = {"onStep()"},
		desc = "Plays step sounds while the element walks, faster when it runs, quieter far from the camera: monsters you hear before you see, NPCs, the player. Picks a random sound of the list and varies the pitch so it never sounds repeated.")
public class Footsteps3D extends Behavior {
	@Header("Sounds")
	@Param(desc = "Step sounds; a random one each step.")
	@Sound
	public List<String> sounds = new ArrayList<String>();
	@Param(desc = "Loudness of the steps (0..1).", min = 0, max = 1, step = 0.05)
	public float volume = 0.7f;
	@Param(desc = "Random pitch change (0 = always the same).", min = 0, max = 0.5, step = 0.01)
	public float pitchVariation = 0.08f;
	@Param(desc = "Heard up to this distance from the camera (0 = always full volume).", unit = "m", min = 0, max = 500, step = 1)
	public float hearDistance = 25;

	@Header("Steps")
	@Param(desc = "Distance walked between two steps.", unit = "m", min = 0.1, max = 10, step = 0.05)
	public float stepLength = 0.7f;
	@Param(desc = "Slower than this counts as standing.", unit = "m/s", min = 0, max = 100000, step = 0.05)
	public float minSpeed = 0.4f;
	@Param(desc = "No steps while it moves up or down faster than this (jumping, falling).", unit = "m/s", min = 0.1, max = 100000, step = 0.1)
	public float airSpeed = 1.5f;

	private final Vector3 last = new Vector3(), p = new Vector3();
	private float walked;
	private boolean has, muted;
	private int steps;

	@Block(label = "mute footsteps", desc = "true = silent.", labelAr = "اكتم الخطوات", descAr = "صح = صامت.", category = "Footsteps 3D", args = {"muted"})
	public void setMuted(boolean on) {
		muted = on;
	}

	@Block(label = "set step volume", desc = "0..1.", labelAr = "اضبط صوت الخطوات", descAr = "0..1.", category = "Footsteps 3D", args = {"volume"})
	public void setVolume(float v) {
		volume = clamp(v, 0, 1);
	}

	@Block(label = "steps taken", desc = "How many steps so far.", labelAr = "الخطوات", descAr = "عدد الخطوات حتى الآن.", category = "Footsteps 3D", args = {})
	public int getSteps() {
		return steps;
	}

	@Override
	public void onUpdate(float dt) {
		if (dt <= 0 || !Behavior.pos3(item, p)) return;
		if (!has) {
			has = true;
			last.set(p);
			return;
		}
		float dx = p.x - last.x, dz = p.z - last.z, dy = p.y - last.y;
		last.set(p);
		float speed = (float) Math.sqrt(dx * dx + dz * dz) / dt;
		if (speed < minSpeed || Math.abs(dy) / dt > airSpeed) {
			walked = Math.min(walked, stepLength * 0.5f);
			return;
		}
		walked += speed * dt;
		if (walked < stepLength) return;
		walked -= stepLength;
		steps++;
		fire("onStep");
		if (muted || sounds.isEmpty()) return;
		String s = sounds.get(MathUtils.random(sounds.size() - 1));
		float pitch = 1f + MathUtils.random(-pitchVariation, pitchVariation);
		boolean self = item == mainCamera3d() || mainCamera3d() != null && mainCamera3d().target == item && "FIRST_PERSON".equals(mainCamera3d().followMode);
		if (hearDistance <= 0 || self) Support3D.play(s, volume, pitch, 0);
		else Support3D.playAt(s, world3d(), p.x, p.y, p.z, volume, hearDistance, pitch);
	}
}
