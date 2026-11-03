package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Inventory;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Pickup 3D", icon = "key-variant", category = "Interaction", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onPickup(String itemId, int count)"},
		desc = "An item the player collects into the inventory: keys, batteries, coins, notes, ammo. Picked up by walking over it or by tapping a 2D button when close; plays a sound, flies to the player and disappears (or comes back later).")
public class Pickup3D extends Behavior {
	@Header("Item")
	@Param(desc = "Inventory id it adds (\"key_red\", \"battery\", \"coin\").")
	public String itemId = "item";
	@Param(desc = "How many it adds.", min = 1, max = 100000, step = 1)
	public int count = 1;

	@Header("Pick up")
	@Param(desc = "The player (empty = the main camera, the first-person player).")
	public PlayerItem player;
	@Param(desc = "Picked up within this distance.", unit = "m", min = 0.1, max = 50, step = 0.1)
	public float range = 1.4f;
	@Param(desc = "2D button to tap for picking up (empty = picked up just by walking into range).")
	public PlayerItem button;
	@Param(desc = "2D text shown with the button (optional).")
	public PlayerItem promptText;
	@Param(desc = "What the text says (\"Take the key\").")
	public String message = "Take";
	@Param(desc = "Tapping the object itself picks it up (while in range).")
	public boolean tapObject = true;
	@Param(desc = "Sound when picked up.")
	@Sound
	public String sound = "";

	@Header("After")
	@Param(desc = "Flies into the player instead of vanishing at once.")
	public boolean flyToPlayer = true;
	@Param(desc = "Comes back after this many seconds (0 = never).", unit = "s", min = 0, max = 3600, step = 0.5)
	public float respawnTime = 0;
	@Param(desc = "Removes the element from the scene when taken (no respawn).")
	public boolean destroyWhenTaken = false;

	private final Support3D.Prompt prompt = new Support3D.Prompt();
	private final Vector3 c = new Vector3(), start = new Vector3(), pp = new Vector3();
	private boolean taken, clicked, flying, has;
	private float back, fly;

	@Block(label = "pick up now", desc = "Gives the item to the player now.", labelAr = "التقط الآن", descAr = "يعطي العنصر للاعب الآن.", category = "Pickup 3D", args = {})
	public void pickUp() {
		if (taken) return;
		taken = true;
		Inventory.add(itemId, count);
		Object3D o = obj();
		if (sound.length() > 0) {
			if (o != null) Support3D.playAt(sound, world3d(), o.position.x, o.position.y, o.position.z, 1, 30, 1);
			else Support3D.play(sound, 1, 1, 0);
		}
		prompt.hide();
		fire("onPickup", itemId, count);
		if (flyToPlayer && o != null) {
			flying = true;
			fly = 0;
		} else vanish();
	}

	@Block(label = "set pickup item", desc = "Inventory id and count it gives.", labelAr = "اضبط عنصر الالتقاط", descAr = "معرّف المخزون والعدد الذي يعطيه.", category = "Pickup 3D", args = {"id", "count"})
	public void setItem(String id, int n) {
		itemId = id == null ? "" : id;
		count = Math.max(1, n);
	}

	@Block(label = "is taken", desc = "True after it was picked up (until it comes back).", labelAr = "تم التقاطه", descAr = "صح بعد التقاطه (حتى يعود).", category = "Pickup 3D", args = {})
	public boolean isTaken() {
		return taken;
	}

	@Block(label = "put pickup back", desc = "Shows it again where it started.", labelAr = "أعد العنصر", descAr = "يظهره مجدداً في مكانه الأول.", category = "Pickup 3D", args = {})
	public void respawn() {
		Object3D o = obj();
		if (o == null || o.isDestroyed()) return;
		taken = false;
		flying = false;
		o.setPosition3(start.x, start.y, start.z);
		o.setScale3(baseScale, baseScale, baseScale);
		Support3D.setActive3(o, true);
	}

	private float baseScale = 1;

	private void vanish() {
		Object3D o = obj();
		if (o == null) return;
		if (destroyWhenTaken && respawnTime <= 0) {
			o.destroy();
			return;
		}
		// keep updating while hidden so it can come back: move it far away instead of hiding the actor
		Support3D.setActive3(o, false);
		o.setVisible(true);
		o.setScale3(0.0001f, 0.0001f, 0.0001f);
		back = respawnTime;
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o == null) return;
		start.set(o.position);
		baseScale = o.scale.x;
		has = true;
	}

	@Override
	public void onClick() {
		if (tapObject) clicked = true;
	}

	@Override
	public void onDisable() {
		prompt.hide();
	}

	@Override
	public void onDestroy() {
		prompt.dispose();
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null || !has) return;
		if (flying) {
			fly += dt * 3f;
			if (Support3D.posOr(player, w, pp)) {
				pp.y -= player == null ? 0.4f : -0.6f;
				o.position.lerp(pp, Math.min(1f, fly));
				o.setPosition3(o.position.x, o.position.y, o.position.z);
			}
			float s = Math.max(0.0001f, baseScale * (1f - Math.min(1f, fly)));
			o.setScale3(s, s, s);
			if (fly >= 1) {
				flying = false;
				vanish();
			}
			return;
		}
		if (taken) {
			if (respawnTime > 0) {
				back -= dt;
				if (back <= 0) respawn();
			}
			return;
		}
		o.center(c);
		float d = Support3D.reach(w, player, c, range, false, 90);
		boolean near = d >= 0;
		if (button == null && promptText == null) {
			if (near || clicked && Support3D.reach(w, player, c, range * 2.5f, false, 90) >= 0) pickUp();
			clicked = false;
			return;
		}
		boolean tap = prompt.update(button, promptText, message, near, near ? d : Float.MAX_VALUE);
		if (clicked && near) tap = true;
		clicked = false;
		if (tap) pickUp();
	}
}
