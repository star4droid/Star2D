package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

@BehaviorInfo(name = "Interactable 3D", icon = "hand-pointing-up", category = "Interaction", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onInteract()", "onEnterRange()", "onExitRange()"},
		desc = "Makes an object usable: when the player is close (and looks at it) your 2D button and prompt text appear; tapping the button (or the object, or E) raises onInteract. Notes, switches, levers, chests, computers. Many objects can share one USE button.")
public class Interactable3D extends Behavior {
	@Header("Who and when")
	@Param(desc = "The player (empty = the main camera, the first-person player).")
	public PlayerItem player;
	@Param(desc = "Usable within this distance.", unit = "m", min = 0.2, max = 100, step = 0.1)
	public float range = 2.5f;
	@Param(desc = "The camera must look at the object.")
	public boolean needLook = true;
	@Param(desc = "How exactly the camera must look at it (half angle).", unit = "deg", min = 2, max = 90)
	public float lookAngle = 25;
	@Param(desc = "Can be used only once.")
	public boolean once = false;

	@Header("Prompt")
	@Param(desc = "2D button shown while usable; tapping it uses the object (several objects may share it).")
	public PlayerItem button;
	@Param(desc = "2D text shown with the button (optional).")
	public PlayerItem promptText;
	@Param(desc = "What the text says.")
	public String message = "Tap to use";
	@Param(desc = "Tapping the 3D object itself also uses it (while in range).")
	public boolean tapObject = true;
	@Param(desc = "E key uses it (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Sound when used.")
	@Sound
	public String sound = "";
	@Param(desc = "Glow outline while usable.")
	public boolean highlight = true;

	private final Support3D.Prompt prompt = new Support3D.Prompt();
	private final Vector3 c = new Vector3();
	private boolean inRange, used, disabled, clicked, savedOutline;
	private int uses;

	@Block(label = "use object", desc = "Uses it now (raises onInteract), wherever the player is.", labelAr = "استخدم الكائن", descAr = "يستخدمه الآن (يطلق onInteract) أينما كان اللاعب.", category = "Interactable 3D", args = {})
	public void interact() {
		if (disabled || once && used) return;
		used = true;
		uses++;
		if (sound.length() > 0) Support3D.play(sound, 1, 1, 0);
		fire("onInteract");
	}

	@Block(label = "set prompt message", desc = "Text shown in the prompt.", labelAr = "اضبط رسالة التلميح", descAr = "النص الظاهر في التلميح.", category = "Interactable 3D", args = {"text"})
	public void setMessage(String text) {
		message = text == null ? "" : text;
	}

	@Block(label = "allow use", desc = "false = cannot be used (and the prompt hides).", labelAr = "اسمح بالاستخدام", descAr = "خطأ = لا يمكن استخدامه (ويختفي التلميح).", category = "Interactable 3D", args = {"allowed"})
	public void setAllowed(boolean on) {
		disabled = !on;
	}

	@Block(label = "player is in range", desc = "True while it can be used.", labelAr = "اللاعب في المدى", descAr = "صح أثناء إمكانية استخدامه.", category = "Interactable 3D", args = {})
	public boolean isInRange() {
		return inRange;
	}

	@Block(label = "times used", desc = "How many times it was used.", labelAr = "مرات الاستخدام", descAr = "عدد مرات استخدامه.", category = "Interactable 3D", args = {})
	public int getUses() {
		return uses;
	}

	@Override
	public void onClick() {
		if (tapObject) clicked = true;
	}

	@Override
	public void onStart() {
		Object3D o = obj();
		if (o != null) savedOutline = o.outline;
	}

	@Override
	public void onDisable() {
		prompt.hide();
		Object3D o = obj();
		if (o != null && highlight) o.outline = savedOutline;
		inRange = false;
	}

	@Override
	public void onDestroy() {
		prompt.dispose();
	}

	@Override
	public void onUpdate(float dt) {
		Object3D o = obj();
		World3D w = world3d();
		if (o == null || w == null) return;
		o.center(c);
		float d = disabled || once && used ? -1 : Support3D.reach(w, player, c, range, needLook, lookAngle);
		boolean now = d >= 0;
		if (now != inRange) {
			inRange = now;
			fire(inRange ? "onEnterRange" : "onExitRange");
			if (highlight) o.outline = inRange || savedOutline;
		}
		boolean tap = prompt.update(button, promptText, message, inRange, now ? d : Float.MAX_VALUE);
		if (inRange && prompt.isShown() && useKeyboard && Support3D.keyJust(Input.Keys.E)) tap = true;
		if (clicked && inRange) tap = true;
		clicked = false;
		if (tap) interact();
	}
}
