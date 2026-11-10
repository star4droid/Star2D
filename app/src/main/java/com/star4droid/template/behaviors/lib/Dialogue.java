package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Support3D;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Dialogue", icon = "message-text", category = "UI",
		events = {"onLine(section, index)", "onEnd(section)"},
		desc = "Story conversations: sections of lines (who speaks, the name shown, the text, a voice sound). Shows the speaker's portrait, types the text letter by letter, tap to continue, skip button. Script format: a line [section] then lines speaker|Name|text|voice.mp3 (voice optional).")
public class Dialogue extends Behavior {
	@Header("Elements")
	@Param(desc = "Shown while talking (a box / group; hidden when done).")
	public PlayerItem panel;
	@Param(desc = "Text element for the speaker's name.")
	public PlayerItem nameText;
	@Param(desc = "Text element for what is said.")
	public PlayerItem bodyText;
	@Param(desc = "Portraits: the one whose name contains the speaker id is shown.")
	public List<PlayerItem> portraits = new ArrayList<PlayerItem>();
	@Param(desc = "Next button (empty = tapping the panel continues).")
	public PlayerItem nextButton;
	@Param(desc = "Skip button: ends the conversation.")
	public PlayerItem skipButton;
	@Param(desc = "Also shown / hidden with the panel (buttons, frames).")
	public List<PlayerItem> extras = new ArrayList<PlayerItem>();

	@Header("Script")
	@Param(desc = "[section] lines, then speaker|Name|text|voice per line.")
	public String script = "[intro]\nhero|Hero|Hello!|";
	@Param(desc = "Letters per second (0 = all at once).", unit = "letters/s", min = 0, max = 500, step = 1)
	public float speed = 45;
	@Param(desc = "Goes on by itself this long after a line is typed (0 = waits for a tap).", unit = "s", min = 0, max = 30, step = 0.5)
	public float autoNext = 0;
	@Param(desc = "Starts this section when the scene starts (empty = with the block).")
	public String startSection = "";

	static final class Line {
		String speaker = "", name = "", text = "", voice = "";
	}

	private final java.util.LinkedHashMap<String, ArrayList<Line>> sections = new java.util.LinkedHashMap<String, ArrayList<Line>>();
	private String parsed;
	private ArrayList<Line> cur;
	private String curName = "";
	private int index = -1;
	private float shown, wait;
	private boolean open;
	private EventListener nl, sl, pl;
	private final ArrayList<String> queue = new ArrayList<String>();

	@Block(label = "start dialogue", desc = "Plays a section of the script (waits if one is playing).", labelAr = "ابدأ الحوار", descAr = "يعرض قسماً من النص (ينتظر إن كان حوار آخر يعمل).", category = "Dialogue", args = {"section"})
	public void start(String section) {
		if (open) {
			queue.add(section);
			return;
		}
		parse();
		ArrayList<Line> l = sections.get(section);
		if (l == null || l.isEmpty()) {
			fire("onEnd", section);
			return;
		}
		cur = l;
		curName = section;
		open = true;
		show(true);
		go(0);
	}

	@Block(label = "is talking", desc = "True while a dialogue is shown.", labelAr = "يتحدث", descAr = "صح أثناء عرض حوار.", category = "Dialogue", args = {})
	public boolean isOpen() {
		return open;
	}

	@Block(label = "skip dialogue", desc = "Ends the conversation now.", labelAr = "تخطَّ الحوار", descAr = "ينهي المحادثة الآن.", category = "Dialogue", args = {})
	public void skip() {
		if (open) finish();
	}

	private void parse() {
		if (script.equals(parsed)) return;
		parsed = script;
		sections.clear();
		ArrayList<Line> l = null;
		for (String raw : script.split("\n")) {
			String s = raw.trim();
			if (s.length() == 0) continue;
			if (s.startsWith("[") && s.endsWith("]")) {
				l = new ArrayList<Line>();
				sections.put(s.substring(1, s.length() - 1).trim(), l);
				continue;
			}
			if (l == null) {
				l = new ArrayList<Line>();
				sections.put("", l);
			}
			String[] p = s.split("\\|", -1);
			Line ln = new Line();
			ln.speaker = p.length > 0 ? p[0].trim() : "";
			ln.name = p.length > 1 ? p[1].trim() : ln.speaker;
			ln.text = p.length > 2 ? p[2].trim() : "";
			ln.voice = p.length > 3 ? p[3].trim() : "";
			l.add(ln);
		}
	}

	private static void vis(PlayerItem it, boolean on) {
		if (it != null && it.getActor() != null) it.getActor().setVisible(on);
	}

	private void show(boolean on) {
		vis(panel, on);
		vis(nameText, on);
		vis(bodyText, on);
		vis(nextButton, on);
		vis(skipButton, on);
		for (PlayerItem e : extras) vis(e, on);
		if (!on) for (PlayerItem p : portraits) vis(p, false);
	}

	private void go(int i) {
		index = i;
		Line l = cur.get(i);
		shown = speed <= 0 ? l.text.length() : 0;
		wait = 0;
		if (nameText != null) nameText.setItemText(l.name);
		if (bodyText != null) bodyText.setItemText(speed <= 0 ? l.text : "");
		for (PlayerItem p : portraits) vis(p, p != null && p.getName() != null && l.speaker.length() > 0 && p.getName().toLowerCase().contains(l.speaker.toLowerCase()));
		if (l.voice.length() > 0) Support3D.play(l.voice, 1, 1, 0);
		fire("onLine", curName, i + 1);
	}

	private void finish() {
		open = false;
		show(false);
		String done = curName;
		fire("onEnd", done);
		if (!queue.isEmpty()) start(queue.remove(0));
	}

	private void tap() {
		if (!open) return;
		Line l = cur.get(index);
		if (shown < l.text.length()) {
			shown = l.text.length();
			if (bodyText != null) bodyText.setItemText(l.text);
			return;
		}
		if (index + 1 < cur.size()) go(index + 1);
		else finish();
	}

	@Override
	public void onStart() {
		Runnable t = new Runnable() {
			@Override
			public void run() {
				tap();
			}
		};
		nl = Support3D.onTap(nextButton, t);
		pl = nextButton == null ? Support3D.onTap(panel, t) : null;
		sl = Support3D.onTap(skipButton, new Runnable() {
			@Override
			public void run() {
				skip();
			}
		});
		if (!open) show(false);
		if (startSection.length() > 0) start(startSection);
	}

	@Override
	public void onDestroy() {
		Support3D.removeListener(nextButton, nl);
		Support3D.removeListener(panel, pl);
		Support3D.removeListener(skipButton, sl);
	}

	@Override
	public void onUpdate(float dt) {
		if (!open || cur == null) return;
		Line l = cur.get(index);
		if (shown < l.text.length()) {
			int before = (int) shown;
			shown = Math.min(l.text.length(), shown + speed * dt);
			if ((int) shown != before && bodyText != null) bodyText.setItemText(l.text.substring(0, (int) shown));
		} else if (autoNext > 0) {
			wait += dt;
			if (wait >= autoNext) tap();
		}
		if (Gdx.input != null && Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER)) tap();
	}
}
