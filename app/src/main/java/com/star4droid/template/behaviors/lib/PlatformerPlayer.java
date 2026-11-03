package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.utils.Array;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Exclusive;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sets;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Platformer player", icon = "run-fast", category = "Movement", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onJump()", "onLand()"},
		desc = "Run and jump with on-screen buttons or the keyboard: double jump, coyote time and jump buffering. Needs gravity in the scene.")
@Exclusive("movement")
@Requires(body = true, type = "DYNAMIC", note = "Needs a DYNAMIC physical element and gravity in the scene.")
@Sets({"fixedRotation=true"})
public class PlatformerPlayer extends Behavior {
	@Header("Controls")
	@Param(desc = "Button (any element) that moves left while it is held.")
	public PlayerItem leftButton;
	public PlayerItem rightButton;
	public PlayerItem jumpButton;
	@Param(desc = "Arrow keys / A D W / space also work (desktop, tests).")
	public boolean useKeyboard = true;

	@Header("Movement")
	@Param(unit = "px/s", min = 0, max = 100000)
	public float speed = 220;
	@Param(desc = "0 = instant, otherwise how fast it reaches the speed.", unit = "px/s2", min = 0, max = 100000)
	public float acceleration = 0;
	@Param(desc = "Mirror the image when walking left.")
	public boolean flipToDirection = true;

	@Header("Jump")
	@Param(unit = "px/s", min = 0, max = 100000)
	public float jumpSpeed = 420;
	@Param(desc = "2 = double jump, 1 = single.", min = 1, max = 10, step = 1)
	public int maxJumps = 2;
	@Param(desc = "Time after leaving a platform in which a jump still works.", unit = "s", min = 0, max = 1, step = 0.01)
	public float coyoteTime = 0.1f;
	@Param(desc = "A jump pressed slightly before landing is remembered for this long.", unit = "s", min = 0, max = 1, step = 0.01)
	public float jumpBuffer = 0.12f;

	private float coyote, buffer, baseScaleX = 1;
	private int jumpsUsed;
	private boolean wasGrounded, jumpWasDown;

	public void onStart() {
		baseScaleX = Math.abs(actor().getScaleX());
	}

	public void onDisable() {
		setVelocityX(0);
	}

	@Block(label = "jump", desc = "Makes the player jump now (uses the jump limit).", labelAr = "اقفز", descAr = "يجعل اللاعب يقفز الآن (حسب حد القفزات).", category = "Platformer")
	public void jump() {
		if (body() == null) return;
		if (coyote > 0 || jumpsUsed < maxJumps) {
			setVelocityY(jumpSpeed);
			jumpsUsed = coyote > 0 ? 1 : jumpsUsed + 1;
			coyote = 0;
			fire("onJump");
		}
	}

	@Block(label = "is on the ground", desc = "True while standing on something.", labelAr = "على الأرض", descAr = "صحيح عندما يقف على شيء.", category = "Platformer")
	public boolean isGrounded() {
		Body b = body();
		return b != null && onGround(b);
	}

	@Block(label = "set run speed", desc = "Changes the running speed (px/s).", labelAr = "اضبط سرعة الجري", descAr = "يغيّر سرعة الجري (بكسل/ثانية).", category = "Platformer", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set jump speed", desc = "Changes the jump strength (px/s).", labelAr = "اضبط قوة القفز", descAr = "يغيّر قوة القفز (بكسل/ثانية).", category = "Platformer", args = {"speed"})
	public void setJumpSpeed(float value) {
		jumpSpeed = value;
	}

	@Block(label = "set max jumps", desc = "How many jumps in a row are allowed (2 = double jump, 1 = single).", labelAr = "اضبط عدد القفزات", descAr = "كم قفزة متتالية مسموحة (2 = قفزة مزدوجة، 1 = مفردة).", category = "Platformer", args = {"jumps"})
	public void setMaxJumps(int value) {
		maxJumps = value;
	}

	@Block(label = "enable double jump", desc = "On: two jumps in a row. Off: a single jump.", labelAr = "فعّل القفز المزدوج", descAr = "مفعّل: قفزتان متتاليتان. معطّل: قفزة واحدة.", category = "Platformer", args = {"on"})
	public void enableDoubleJump(boolean on) {
		maxJumps = on ? 2 : 1;
	}

	@Block(label = "use keyboard", desc = "Arrow keys / A D W / space also move the player.", labelAr = "استخدم لوحة المفاتيح", descAr = "مفاتيح الأسهم و A D W والمسافة تحرك اللاعب أيضاً.", category = "Platformer", args = {"on"})
	public void setUseKeyboard(boolean on) {
		useKeyboard = on;
	}

	@Block(label = "set coyote time", desc = "Seconds after leaving a platform in which a jump still works.", labelAr = "اضبط مهلة القفز بعد الحافة", descAr = "الثواني التي يعمل فيها القفز بعد مغادرة المنصة.", category = "Platformer", args = {"seconds"})
	public void setCoyoteTime(float seconds) {
		coyoteTime = seconds;
	}

	@Block(label = "run speed", desc = "Current running speed (px/s).", labelAr = "سرعة الجري", descAr = "سرعة الجري الحالية (بكسل/ثانية).", category = "Platformer")
	public float getSpeed() {
		return speed;
	}

	@Block(label = "jump speed", desc = "Current jump strength (px/s).", labelAr = "قوة القفز", descAr = "قوة القفز الحالية (بكسل/ثانية).", category = "Platformer")
	public float getJumpSpeed() {
		return jumpSpeed;
	}

	private boolean onGround(Body b) {
		Array<Contact> contacts = b.getWorld().getContactList();
		for (int i = 0; i < contacts.size; i++) {
			Contact c = contacts.get(i);
			boolean mineIsA = c.getFixtureA().getBody() == b;
			if (!mineIsA && c.getFixtureB().getBody() != b) continue;
			if (!c.isTouching() || c.getFixtureA().isSensor() || c.getFixtureB().isSensor()) continue;
			Vector2 n = c.getWorldManifold().getNormal(); // points from A to B
			if ((mineIsA && n.y < -0.5f) || (!mineIsA && n.y > 0.5f)) return true;
		}
		return false;
	}

	public void onUpdate(float dt) {
		Body b = body();
		if (b == null) return;
		float dir = 0;
		if (isPressed(leftButton) || (useKeyboard && keyDown(KEY_LEFT, KEY_A))) dir -= 1;
		if (isPressed(rightButton) || (useKeyboard && keyDown(KEY_RIGHT, KEY_D))) dir += 1;

		float vx = velocity().x, target = dir * speed;
		if (acceleration > 0) {
			float step = acceleration * dt;
			vx = vx < target ? Math.min(target, vx + step) : Math.max(target, vx - step);
		} else vx = target;
		setVelocityX(vx);
		if (flipToDirection && dir != 0) actor().setScaleX(dir < 0 ? -baseScaleX : baseScaleX);

		boolean grounded = onGround(b);
		if (grounded) {
			coyote = coyoteTime;
			jumpsUsed = 0;
			if (!wasGrounded) fire("onLand");
		} else {
			coyote -= dt;
			if (coyote <= 0 && jumpsUsed == 0) jumpsUsed = 1; // walked off a ledge: the first jump is gone
		}
		wasGrounded = grounded;

		boolean jumpDown = isPressed(jumpButton) || (useKeyboard && keyDown(KEY_UP, KEY_W, KEY_SPACE));
		if (jumpDown && !jumpWasDown) buffer = jumpBuffer;
		jumpWasDown = jumpDown;
		buffer -= dt;
		if (buffer > 0 && (coyote > 0 || jumpsUsed < maxJumps)) {
			setVelocityY(jumpSpeed);
			jumpsUsed = coyote > 0 ? 1 : jumpsUsed + 1;
			coyote = 0;
			buffer = 0;
			fire("onJump");
		}
	}
}
