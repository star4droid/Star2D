package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.star4droid.template.Items.ProgressItem;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Inventory;
import com.star4droid.template.d3.Light3D;

@BehaviorInfo(name = "Flashlight 3D", icon = "flashlight", category = "Lights", targets = BehaviorInfo.Target.THREE_D, types = {"LIGHT3D"},
		events = {"onToggle(boolean on)", "onBatteryEmpty()", "onBatteryLow()"},
		desc = "Turns a SPOT light into the player's flashlight: it follows the camera with a little lag, a 2D button switches it on and off, and an optional battery drains, flickers when low, shows on a 2D bar and is refilled with battery items from the inventory.")
public class Flashlight3D extends Behavior {
	@Header("Holding")
	@Param(desc = "Camera it follows (empty = the main camera).")
	public PlayerItem camera;
	@Param(desc = "Offset to the right of the eyes.", unit = "m", min = -2, max = 2, step = 0.01)
	public float offsetRight = 0.25f;
	@Param(desc = "Offset below the eyes.", unit = "m", min = -2, max = 2, step = 0.01)
	public float offsetDown = 0.25f;
	@Param(desc = "Lag behind the camera turns (0 = none, higher = heavier).", min = 0, max = 0.95, step = 0.05)
	public float sway = 0.35f;

	@Header("Switch")
	@Param(desc = "2D button that switches it on / off.")
	public PlayerItem toggleButton;
	@Param(desc = "On when the game starts.")
	public boolean startOn = true;
	@Param(desc = "F key switches it (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Click sound of the switch.")
	@Sound
	public String clickSound = "";

	@Header("Battery")
	@Param(desc = "Battery used per second while on (0 = endless).", unit = "%/s", min = 0, max = 100, step = 0.1)
	public float drainPerSecond = 0;
	@Param(desc = "Inventory item that refills the battery when it is empty (\"\" = none).")
	public String batteryItem = "battery";
	@Param(desc = "2D progress bar showing the battery (optional).")
	public ProgressItem batteryBar;
	@Param(desc = "Starts flickering below this charge.", unit = "%", min = 0, max = 100, step = 1)
	public float lowLevel = 15;

	private float intensity = -1, battery = 100, flick = 1, flickT;
	private boolean on, lowFired, started3;
	private EventListener tapL;
	private final Vector3 dir = new Vector3(), pos = new Vector3(), right = new Vector3();

	@Block(label = "switch flashlight", desc = "true = on, false = off.", labelAr = "شغّل المصباح", descAr = "صح = تشغيل، خطأ = إطفاء.", category = "Flashlight 3D", args = {"on"})
	public void setOn(boolean v) {
		if (v && battery <= 0 && !refill()) v = false;
		if (v == on) return;
		on = v;
		if (clickSound.length() > 0) Support3D.play(clickSound, 0.8f, 1, 0);
		fire("onToggle", on);
	}

	@Block(label = "toggle flashlight", desc = "Switches it on / off.", labelAr = "بدّل المصباح", descAr = "يشغّله أو يطفئه.", category = "Flashlight 3D", args = {})
	public void toggle() {
		setOn(!on);
	}

	@Block(label = "charge battery", desc = "Adds charge (percent).", labelAr = "اشحن البطارية", descAr = "يضيف شحناً (نسبة مئوية).", category = "Flashlight 3D", args = {"percent"})
	public void charge(float pct) {
		battery = clamp(battery + pct, 0, 100);
		if (battery > lowLevel) lowFired = false;
		showBar();
	}

	@Block(label = "battery percent", desc = "Charge left (0..100).", labelAr = "نسبة البطارية", descAr = "الشحن المتبقي (0..100).", category = "Flashlight 3D", args = {})
	public float getBattery() {
		return battery;
	}

	@Block(label = "flashlight is on", desc = "True while lit.", labelAr = "المصباح يعمل", descAr = "صح أثناء الإضاءة.", category = "Flashlight 3D", args = {})
	public boolean isOn() {
		return on;
	}

	private boolean refill() {
		if (batteryItem == null || batteryItem.length() == 0 || !Inventory.remove(batteryItem, 1)) return false;
		battery = 100;
		lowFired = false;
		showBar();
		return true;
	}

	private void showBar() {
		if (batteryBar != null) {
			batteryBar.setMax(100);
			batteryBar.setProgress(Math.round(battery));
		}
	}

	@Override
	public void onStart() {
		Light3D l = light3d();
		if (l == null) return;
		intensity = l.intensity;
		if (l.getDef() != null) l.getDef().Attach_To = "";
		l.attachTo(null);
		on = startOn;
		if (toggleButton != null) tapL = Support3D.onTap(toggleButton, new Runnable() {
			@Override
			public void run() {
				toggle();
			}
		});
		showBar();
	}

	@Override
	public void onDisable() {
		Light3D l = light3d();
		if (l != null && intensity >= 0) {
			l.setIntensity(intensity);
			l.setOn(true);
		}
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(toggleButton, tapL);
	}

	@Override
	public void onUpdate(float dt) {
		Light3D l = light3d();
		Camera3D c = Support3D.cameraOr(camera, world3d());
		if (l == null || c == null || intensity < 0) return;
		if (useKeyboard && Support3D.keyJust(Input.Keys.F)) toggle();
		// follow the eyes
		Vector3 cd = c.cam.direction;
		right.set(cd).crs(c.cam.up).nor();
		pos.set(c.cam.position).mulAdd(right, offsetRight).mulAdd(c.cam.up, -offsetDown);
		if (!started3) {
			started3 = true;
			dir.set(cd);
		}
		float k = sway <= 0 ? 1 : Support3D.smooth((1f - sway) * 25f, dt);
		dir.lerp(cd, k).nor();
		l.position.set(pos);
		l.direction.set(dir);
		// battery
		if (on && drainPerSecond > 0) {
			battery = Math.max(0, battery - drainPerSecond * dt);
			showBar();
			if (battery <= lowLevel && !lowFired) {
				lowFired = true;
				fire("onBatteryLow");
			}
			if (battery <= 0 && !refill()) {
				on = false;
				fire("onBatteryEmpty");
				fire("onToggle", false);
			}
		}
		flick = 1;
		if (on && drainPerSecond > 0 && battery < lowLevel) {
			flickT -= dt;
			if (flickT <= 0) flickT = MathUtils.random(0.05f, 0.3f) * (0.3f + battery / Math.max(1f, lowLevel));
			if (flickT < 0.06f) flick = MathUtils.random(0.1f, 0.5f);
			flick *= 0.5f + 0.5f * battery / Math.max(1f, lowLevel);
		}
		l.setOn(on);
		l.setIntensity(intensity * flick);
	}
}
