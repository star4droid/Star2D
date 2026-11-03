package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
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

@BehaviorInfo(name = "Door 3D", icon = "door-closed", category = "Interaction", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onOpen()", "onClose()", "onLocked()", "onUnlock()"},
		desc = "A door that swings on its hinge, slides, or plays the model's open / close clips. The player opens it with your 2D button when close; it can need a key from the inventory, close by itself, and play open / close / locked sounds.")
public class Door3D extends Behavior {
	public enum Kind { HINGE, SLIDE, ANIMATION }

	public enum Side { LEFT, RIGHT, CENTER }

	@Header("How it opens")
	@Param(desc = "HINGE: swings around one edge. SLIDE: moves sideways. ANIMATION: plays the model's clips.")
	public Kind kind = Kind.HINGE;
	@Param(desc = "Swing angle (HINGE). The sign picks the way when it does not open away from the player.", unit = "deg", min = -180, max = 180)
	public float openAngle = 95;
	@Param(desc = "The edge the hinge is on (seen from the door's front, -Z).")
	public Side hinge = Side.LEFT;
	@Param(desc = "HINGE: swings away from the player (like a real push door).")
	public boolean openAwayFromPlayer = true;
	@Param(desc = "SLIDE: how far it slides along its own X (0 = its width; negative = the other way).", unit = "m", min = -50, max = 50, step = 0.05)
	public float slideDistance = 0;
	@Param(desc = "Seconds to open or close.", unit = "s", min = 0.05, max = 10, step = 0.05)
	public float openTime = 0.8f;
	@Param(desc = "ANIMATION: clip that opens it.")
	public String openClip = "open";
	@Param(desc = "ANIMATION: clip that closes it.")
	public String closeClip = "close";
	@Param(desc = "Closes by itself after this many seconds open (0 = stays open).", unit = "s", min = 0, max = 600, step = 0.5)
	public float autoClose = 0;
	@Param(desc = "Open when the game starts.")
	public boolean startOpen = false;

	@Header("Lock")
	@Param(desc = "Inventory item that unlocks it (\"\" = no key needed).")
	public String keyItem = "";
	@Param(desc = "The key is used up when the door is unlocked.")
	public boolean useUpKey = false;
	@Param(desc = "Locked at start (with no key item only blocks can unlock it).")
	public boolean locked = false;

	@Header("Player and prompt")
	@Param(desc = "The player (empty = the main camera, the first-person player).")
	public PlayerItem player;
	@Param(desc = "Usable within this distance.", unit = "m", min = 0.2, max = 50, step = 0.1)
	public float range = 2.2f;
	@Param(desc = "The camera must look at the door.")
	public boolean needLook = false;
	@Param(desc = "2D button shown when the player can use the door (several doors may share it).")
	public PlayerItem button;
	@Param(desc = "2D text shown with the button (optional).")
	public PlayerItem promptText;
	@Param(desc = "Prompt while closed.")
	public String openText = "Open";
	@Param(desc = "Prompt while open.")
	public String closeText = "Close";
	@Param(desc = "Prompt while locked.")
	public String lockedText = "Locked";
	@Param(desc = "Tapping the door itself also uses it (while in range).")
	public boolean tapObject = true;
	@Param(desc = "E key uses it (desktop, tests).")
	public boolean useKeyboard = true;

	@Header("Sounds")
	@Param(desc = "Sound when it opens.")
	@Sound
	public String openSound = "";
	@Param(desc = "Sound when it closes.")
	@Sound
	public String closeSound = "";
	@Param(desc = "Sound when the player tries a locked door.")
	@Sound
	public String lockedSound = "";
	@Param(desc = "Sound when it is unlocked.")
	@Sound
	public String unlockSound = "";

	private final Support3D.Prompt prompt = new Support3D.Prompt();
	private final Vector3 p0 = new Vector3(), e0 = new Vector3(), hingeP = new Vector3(), tmp = new Vector3(), c = new Vector3();
	private float amount, target, sign = 1, openFor, width = 1;
	private boolean has, open, clicked;

	// ------------------------------------------------------------ blocks

	@Block(label = "open door", desc = "Opens (if not locked).", labelAr = "افتح الباب", descAr = "يفتح (إن لم يكن مقفلاً).", category = "Door 3D", args = {})
	public void open() {
		if (open) return;
		if (locked && !tryKey()) {
			if (lockedSound.length() > 0) playSound(lockedSound);
			fire("onLocked");
			return;
		}
		open = true;
		target = 1;
		openFor = 0;
		chooseSide();
		if (openSound.length() > 0) playSound(openSound);
		if (kind == Kind.ANIMATION && obj() != null) obj().playAnimation(openClip, false, 1);
		fire("onOpen");
	}

	@Block(label = "close door", desc = "Closes.", labelAr = "أغلق الباب", descAr = "يغلق.", category = "Door 3D", args = {})
	public void close() {
		if (!open) return;
		open = false;
		target = 0;
		if (closeSound.length() > 0) playSound(closeSound);
		if (kind == Kind.ANIMATION && obj() != null) obj().playAnimation(closeClip, false, 1);
		fire("onClose");
	}

	@Block(label = "toggle door", desc = "Opens when closed, closes when open.", labelAr = "بدّل الباب", descAr = "يفتح إذا كان مغلقاً ويغلق إذا كان مفتوحاً.", category = "Door 3D", args = {})
	public void toggle() {
		if (open) close();
		else open();
	}

	@Block(label = "lock door", desc = "Locks it (closes first if needed).", labelAr = "أقفل الباب", descAr = "يقفله (يغلقه أولاً إن لزم).", category = "Door 3D", args = {})
	public void lock() {
		close();
		locked = true;
	}

	@Block(label = "unlock door", desc = "Unlocks it without a key.", labelAr = "افتح قفل الباب", descAr = "يفتح القفل بدون مفتاح.", category = "Door 3D", args = {})
	public void unlock() {
		if (!locked) return;
		locked = false;
		if (unlockSound.length() > 0) playSound(unlockSound);
		fire("onUnlock");
	}

	@Block(label = "door is open", desc = "True while open (or opening).", labelAr = "الباب مفتوح", descAr = "صح أثناء الفتح.", category = "Door 3D", args = {})
	public boolean isOpen() {
		return open;
	}

	@Block(label = "door is locked", desc = "True while locked.", labelAr = "الباب مقفل", descAr = "صح أثناء القفل.", category = "Door 3D", args = {})
	public boolean isLocked() {
		return locked;
	}

	// ------------------------------------------------------------ logic

	private boolean tryKey() {
		if (keyItem == null || keyItem.length() == 0 || !Inventory.has(keyItem)) return false;
		if (useUpKey) Inventory.remove(keyItem, 1);
		unlock();
		return true;
	}

	private void playSound(String s) {
		Object3D o = obj();
		if (o != null) Support3D.playAt(s, world3d(), o.position.x, o.position.y, o.position.z, 1, 30, 1);
		else Support3D.play(s, 1, 1, 0);
	}

	private void chooseSide() {
		sign = 1;
		if (kind != Kind.HINGE || !openAwayFromPlayer) return;
		Vector3 pp = new Vector3();
		if (!Support3D.posOr(player, world3d(), pp)) return;
		// the player's side of the door in its own space: front = -Z
		Quaternion inv = new Quaternion().setEulerAngles(e0.y, e0.x, e0.z).conjugate();
		pp.sub(p0).mul(inv);
		boolean playerInFront = pp.z < 0;
		// +angle swings a LEFT-hinged door toward its front: open away = toward the back
		float s = hinge == Side.RIGHT ? -1 : 1;
		sign = (playerInFront ? -1 : 1) * s * Math.signum(openAngle == 0 ? 1 : openAngle);
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o == null) return;
		p0.set(o.position);
		e0.set(o.euler);
		BoundingBox b = o.localBounds();
		width = b.getWidth() * Math.abs(o.scale.x);
		float hx = hinge == Side.LEFT ? b.min.x : (hinge == Side.RIGHT ? b.max.x : b.getCenterX());
		hingeP.set(hx * o.scale.x, 0, b.getCenterZ() * o.scale.z).mul(new Quaternion().setEulerAngles(e0.y, e0.x, e0.z)).add(p0);
		if (keyItem != null && keyItem.length() > 0 && !startOpen) locked = true;
		has = true;
		if (startOpen) {
			open = true;
			amount = target = 1;
			apply(o);
		}
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
	public void onClick() {
		if (tapObject) clicked = true;
	}

	private void apply(Object3D o) {
		float k = Interpolation.smooth.apply(amount);
		if (kind == Kind.HINGE) {
			float a = Math.abs(openAngle) * sign * k;
			tmp.set(p0).sub(hingeP).rotate(Vector3.Y, a).add(hingeP);
			o.setPosition3(tmp.x, tmp.y, tmp.z);
			o.setRotation3(e0.x, e0.y + a, e0.z);
		} else if (kind == Kind.SLIDE) {
			float d = (slideDistance == 0 ? width * 0.95f : slideDistance) * k;
			tmp.set(d, 0, 0).mul(new Quaternion().setEulerAngles(e0.y, e0.x, e0.z)).add(p0);
			o.setPosition3(tmp.x, tmp.y, tmp.z);
		}
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || !has || w == null) return;
		if (amount != target) {
			float step = dt / Math.max(0.05f, openTime);
			amount = target > amount ? Math.min(target, amount + step) : Math.max(target, amount - step);
			apply(o);
		}
		if (open && autoClose > 0) {
			openFor += dt;
			if (openFor >= autoClose) close();
		}
		o.center(c);
		if (kind == Kind.HINGE && amount > 0) c.set(p0).add(0, o.getSizeY() * 0.5f, 0); // measure from the closed spot
		float d = Support3D.reach(w, player, c, range, needLook, 35);
		boolean near = d >= 0;
		String text = locked && !(keyItem.length() > 0 && Inventory.has(keyItem)) ? lockedText : (open ? closeText : openText);
		boolean tap = prompt.update(button, promptText, text, near, near ? d : Float.MAX_VALUE);
		if (near && useKeyboard && Support3D.keyJust(Input.Keys.E)) tap = true;
		if (clicked && near) tap = true;
		clicked = false;
		if (tap) toggle();
	}
}
