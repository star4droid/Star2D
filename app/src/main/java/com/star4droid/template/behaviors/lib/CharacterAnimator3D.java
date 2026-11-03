package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;

@BehaviorInfo(name = "Character Animator 3D", icon = "human-handsup", category = "Animation", targets = BehaviorInfo.Target.THREE_D,
		types = {"OBJ3D"}, events = {"onClipChanged(String clip)"},
		desc = "Plays the idle, walk, run, jump and fall clips of a model from how fast it really moves, with smooth blending. Works with any movement (your blocks, AI, physics). Clip names may be a part of the name (\"walk\").")
public class CharacterAnimator3D extends Behavior {
	@Header("Clips")
	@Param(desc = "Clip while standing still (part of the name is enough).")
	public String idleClip = "idle";
	@Param(desc = "Clip while walking.")
	public String walkClip = "walk";
	@Param(desc = "Clip while running (empty = the walk clip).")
	public String runClip = "run";
	@Param(desc = "Clip while going up in the air (empty = keep the ground clip).")
	public String jumpClip = "jump";
	@Param(desc = "Clip while falling (empty = the jump clip).")
	public String fallClip = "fall";

	@Header("Speeds")
	@Param(desc = "Speed of the walk clip as made (walking slower plays it slower).", unit = "m/s", min = 0.1, max = 100000, step = 0.1)
	public float walkSpeed = 2f;
	@Param(desc = "Speed from which the run clip plays.", unit = "m/s", min = 0.1, max = 100000, step = 0.1)
	public float runSpeed = 4.5f;
	@Param(desc = "Clip speed follows the moving speed (no sliding feet).")
	public boolean matchSpeed = true;
	@Param(desc = "Blend time between two clips.", unit = "s", min = 0, max = 1, step = 0.05)
	public float blendTime = 0.2f;
	@Param(desc = "Counts as in the air when nothing is this close under the feet.", unit = "m", min = 0.02, max = 2, step = 0.02)
	public float groundCheck = 0.15f;

	private final Support3D.Clips clips = new Support3D.Clips();
	private final Vector3 last = new Vector3();
	private float speed, vy, hold;
	private boolean started3, paused;
	private String forced = "";

	@Block(label = "play action clip", desc = "Plays a clip once over the movement clips (attack, wave, open).", labelAr = "شغّل مقطع حركة مرة", descAr = "يشغّل مقطعاً مرة واحدة فوق مقاطع الحركة (هجوم، تلويح، فتح).", category = "Character animator", args = {"clip"})
	public void playAction(String clip) {
		Object3D o = obj();
		if (o != null) o.playAnimationOnce(clip, 1);
	}

	@Block(label = "force clip", desc = "Plays this clip in a loop instead of the automatic ones (\"\" = automatic again).", labelAr = "ثبّت مقطعاً", descAr = "يشغّل هذا المقطع بتكرار بدل المقاطع التلقائية (\"\" = تلقائي مجدداً).", category = "Character animator", args = {"clip"})
	public void forceClip(String clip) {
		forced = clip == null ? "" : clip;
	}

	@Block(label = "pause animator", desc = "true = stop choosing clips.", labelAr = "أوقف المحرّك", descAr = "صح = يتوقف عن اختيار المقاطع.", category = "Character animator", args = {"paused"})
	public void setPaused(boolean on) {
		paused = on;
	}

	@Block(label = "current clip", desc = "Name of the clip chosen now.", labelAr = "المقطع الحالي", descAr = "اسم المقطع المختار الآن.", category = "Character animator", args = {})
	public String getClip() {
		return clips.current;
	}

	@Block(label = "measured speed", desc = "How fast the element moves (m/s, horizontal).", labelAr = "السرعة المقاسة", descAr = "سرعة حركة العنصر (م/ث، أفقياً).", category = "Character animator", args = {})
	public float getSpeed() {
		return speed;
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		if (o == null || dt <= 0) return;
		if (!started3) {
			started3 = true;
			last.set(o.position);
			return;
		}
		float dx = o.position.x - last.x, dz = o.position.z - last.z, dy = o.position.y - last.y;
		last.set(o.position);
		float s = (float) Math.sqrt(dx * dx + dz * dz) / dt;
		speed += (s - speed) * Support3D.smooth(10, dt);
		vy += (dy / dt - vy) * Support3D.smooth(10, dt);
		if (paused) return;
		String before = clips.current;
		if (forced.length() > 0) clips.play(o, forced, true, 1, blendTime);
		else {
			boolean grounded = o.isDynamic() ? o.isGrounded(groundCheck) : Math.abs(vy) < 0.6f;
			hold = grounded ? 0.12f : hold - dt; // tiny bumps do not count as jumps
			if (hold > 0) grounded = true;
			if (!grounded && (jumpClip.length() > 0 || fallClip.length() > 0)) {
				String c = vy > 0 ? jumpClip : (fallClip.length() > 0 ? fallClip : jumpClip);
				if (c.length() > 0) clips.play(o, c, vy <= 0, 1, blendTime);
			} else
				clips.locomotion(o, speed, true, walkSpeed, runSpeed, idleClip, walkClip, runClip, "", blendTime, matchSpeed);
		}
		if (!before.equals(clips.current)) fire("onClipChanged", clips.current);
	}
}
