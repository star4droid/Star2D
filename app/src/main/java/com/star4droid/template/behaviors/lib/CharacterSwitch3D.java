package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Combat3D;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;
import com.star4droid.template.d3.Foliage3D;
import com.star4droid.template.d3.Object3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Character Switch 3D", icon = "account-switch", category = "Gameplay", targets = BehaviorInfo.Target.THREE_D, types = {"OBJ3D"},
		events = {"onSwitch(character, index)", "onPartyDefeated()"},
		desc = "A party of playable characters: one is in the world at a time, the switch button swaps to the next living one at the same place and heading (the camera follows it). The others are hidden and their behaviors paused. Put it on any element (an Empty).")
public class CharacterSwitch3D extends Behavior {
	@Header("Party")
	@Param(desc = "The playable characters, the first one starts.")
	public List<PlayerItem> characters = new ArrayList<PlayerItem>();
	@Param(desc = "Switch button (tap = next character).")
	public PlayerItem switchButton;
	@Param(desc = "Q = next character (desktop, tests).")
	public boolean useKeyboard = true;
	@Param(desc = "Seconds before switching again.", unit = "s", min = 0, max = 10, step = 0.1)
	public float cooldown = 0.6f;
	@Param(desc = "Effect copied where the new character appears (optional).")
	public PlayerItem switchEffect;
	@Param(desc = "When the played character dies, the next living one takes over after this delay (0 = no).", unit = "s", min = 0, max = 30, step = 0.1)
	public float autoSwitchOnDeath = 1.5f;

	private float deadFor;
	private boolean defeated;

	private int index = -1;
	private float cd;
	private boolean queued;
	private EventListener l;

	@Block(label = "next character", desc = "Switches to the next living character.", labelAr = "الشخصية التالية", descAr = "يبدل إلى الشخصية الحية التالية.", category = "Character switch", args = {})
	public void next() {
		queued = true;
	}

	@Block(label = "switch to character", desc = "Switches to this character of the party.", labelAr = "بدّل إلى شخصية", descAr = "يبدل إلى هذه الشخصية من الفريق.", category = "Character switch", args = {"character"})
	public void switchTo(PlayerItem c) {
		int i = characters.indexOf(c);
		if (i >= 0) activate(i);
	}

	@Block(label = "current character", desc = "The character being played.", labelAr = "الشخصية الحالية", descAr = "الشخصية التي يتم اللعب بها.", category = "Character switch", args = {})
	public PlayerItem getCurrent() {
		return index >= 0 && index < characters.size() ? characters.get(index) : null;
	}

	@Block(label = "current character number", desc = "1 = the first of the party.", labelAr = "رقم الشخصية الحالية", descAr = "1 = الأولى في الفريق.", category = "Character switch", args = {})
	public int getIndex() {
		return index + 1;
	}

	@Override
	public void onStart() {
		l = Support3D.onTap(switchButton, new Runnable() {
			@Override
			public void run() {
				queued = true;
			}
		});
		if (characters.size() > 0) activate(0);
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(switchButton, l);
	}

	private static boolean dead(PlayerItem c) {
		Behavior b = c.getBehavior("Health3D");
		return b instanceof Health3D && ((Health3D) b).isDead();
	}

	private static void enableAll(PlayerItem c, boolean on) {
		ItemScript s = c.getScript();
		if (s == null) return;
		ArrayList<Behavior> list = new ArrayList<Behavior>(s.getBehaviors());
		// controllers last when turning on (they take the camera), first when turning off
		for (Behavior b : list) {
			if (b instanceof Health3D) continue;
			if (on == (b instanceof ThirdPersonController3D)) continue;
			b.setEnabled(on);
		}
		for (Behavior b : list) if (b instanceof ThirdPersonController3D) b.setEnabled(on);
	}

	private void activate(int i) {
		if (i < 0 || i >= characters.size()) return;
		PlayerItem next = characters.get(i);
		PlayerItem prev = getCurrent();
		Object3D pn = Support3D.o(next), pp = Support3D.o(prev);
		if (prev != null && prev != next && pp != null && pn != null) {
			pn.setPosition3(pp.position.x, pp.position.y + 0.05f, pp.position.z);
			pn.setYaw3(pp.getYaw3());
		}
		for (int k = 0; k < characters.size(); k++) {
			PlayerItem c = characters.get(k);
			if (c == next || c == null) continue;
			enableAll(c, false);
			Support3D.setActive3(c, false);
		}
		Support3D.setActive3(next, true);
		enableAll(next, true);
		index = i;
		cd = cooldown;
		if (pn != null) {
			Foliage3D.bender = pn;
			if (prev != null && prev != next) Combat3D.effect(switchEffect, pn.position.x, pn.position.y, pn.position.z, 1.2f);
		}
		fire("onSwitch", next, i + 1);
	}

	@Override
	public void onUpdate(float dt) {
		cd = Math.max(0, cd - dt);
		PlayerItem cur = getCurrent();
		if (cur != null && dead(cur) && autoSwitchOnDeath > 0 && !defeated) {
			deadFor += dt;
			if (deadFor >= autoSwitchOnDeath) {
				deadFor = 0;
				boolean any = false;
				for (PlayerItem c : characters) if (c != null && !dead(c)) any = true;
				if (any) {
					cd = 0;
					queued = true;
				} else {
					defeated = true;
					fire("onPartyDefeated");
				}
			}
		} else deadFor = 0;
		if (useKeyboard && Support3D.keyJust(Input.Keys.Q)) queued = true;
		if (!queued || cd > 0) {
			queued = false;
			return;
		}
		queued = false;
		int n = characters.size();
		for (int s = 1; s <= n; s++) {
			int k = (index + s) % n;
			PlayerItem c = characters.get(k);
			if (c != null && !dead(c)) {
				if (k != index) activate(k);
				return;
			}
		}
	}
}
