package com.star4droid.template.anim;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;

/** Headless checks of the timelines (tools/test_anim.py): file format, keys, interpolation, loops, markers, relative values, tweens. */
public class AnimTest {
	static int fails = 0, checks = 0;

	static void check(boolean ok, String msg) {
		checks++;
		if (!ok) {
			fails++;
			System.out.println("FAIL: " + msg);
		}
	}

	static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.02f;
	}

	/** A screen element without a scene (50 x 50 actor). */
	static final class Fake extends com.badlogic.gdx.scenes.scene2d.Actor implements com.star4droid.template.Utils.PlayerItem {
		final com.star4droid.template.Utils.ChildsHolder holder = new com.star4droid.template.Utils.ChildsHolder(this);
		final com.star4droid.star2d.ElementDefs.BoxDef def = new com.star4droid.star2d.ElementDefs.BoxDef();

		Fake(String n) {
			setName(n);
			setSize(50, 50);
			def.type = "UI";
		}

		public void update() {
		}

		public com.badlogic.gdx.physics.box2d.Body getBody() {
			return null;
		}

		public void setScript(com.star4droid.template.Utils.ItemScript s) {
		}

		public <T extends com.star4droid.template.Utils.ItemScript> T getScript() {
			return null;
		}

		public com.star4droid.template.Utils.ChildsHolder getChildsHolder() {
			return holder;
		}

		public com.star4droid.star2d.ElementDefs.ItemDef getProperties() {
			return def;
		}

		public com.star4droid.template.Utils.PlayerItem getClone(String n) {
			return new Fake(n);
		}

		public com.star4droid.star2d.ElementDefs.ElementEvent getElementEvents() {
			return null;
		}
	}

	public static void main(String[] a) {
		Anim.spy = new ArrayList<String>();
		// ---------------- file format
		Timeline tl = new Timeline();
		tl.name = "intro";
		tl.duration = 2;
		tl.target = "panel";
		tl.scene = "menu";
		tl.playOnStart = true;
		Timeline.Track x = tl.trackOrNew("panel", "x");
		x.put(0, "0", "linear");
		x.put(1, "100", "linear");
		x.put(2, "100", "step");
		tl.markers.add(new Timeline.Marker(0.5f, "half"));
		String json = tl.toJson();
		Timeline back = Timeline.parse(json);
		check(back.name.equals("intro") && back.duration == 2 && back.target.equals("panel") && back.playOnStart && back.scene.equals("menu"), "header round trip");
		check(back.tracks.size() == 1 && back.tracks.get(0).keys.size() == 3 && back.tracks.get(0).keys.get(1).v.equals("100"), "tracks round trip");
		check(back.markers.size() == 1 && back.markers.get(0).name.equals("half"), "markers round trip");
		check(Timeline.parse(json.replace("\"text\"", "x")).tracks.size() == 1, "parse again");
		Timeline q = new Timeline();
		q.trackOrNew("t", "Text").put(0, "a \"quoted\"\nline", "step");
		check(Timeline.parse(q.toJson()).tracks.get(0).keys.get(0).v.equals("a \"quoted\"\nline"), "text values escaped");
		check(Timeline.loopOf("ping-pong") == Timeline.PING_PONG && Timeline.loopOf("loop") == Timeline.LOOP && Timeline.loopOf("x") == Timeline.ONCE, "loop names");

		// ---------------- interpolation
		check(Interp.apply("linear", 0.5f) == 0.5f && Interp.apply("step", 0.99f) == 0 && Interp.apply("step", 1f) == 1, "linear / step");
		check(near(Interp.apply("bounce", 1f), 1) && Interp.apply("easeIn", 0.5f) < 0.5f && Interp.apply("easeOut", 0.5f) > 0.5f, "curves");
		for (String n : Interp.NAMES) check(near(Interp.apply(n, 1), 1) && Interp.apply(n, 0) == 0, "ends of " + n);

		// ---------------- playing on an element (the timeline's own element is the target)
		Fake panel = new Fake("panel");
		TimelinePlayer p = new TimelinePlayer(null, tl, panel);
		p.apply();
		check(near(panel.getX(), 0), "starts at the first key");
		p.update(0.25f);
		check(near(panel.getX(), 25), "linear quarter: " + panel.getX());
		check(!Anim.spy.contains("marker:intro:half"), "no marker yet");
		p.update(0.5f);
		check(near(panel.getX(), 75) && Anim.spy.contains("marker:intro:half"), "marker passed at 0.5: " + Anim.spy);
		p.update(0.5f);
		check(near(panel.getX(), 100), "second key");
		boolean alive = p.update(5f);
		check(!alive && Anim.spy.contains("end:intro"), "ends once (ONCE)");
		check(near(panel.getX(), 100), "stays at the last key");

		// ---------------- loop and ping-pong
		Timeline lp = new Timeline();
		lp.name = "lp";
		lp.duration = 1;
		lp.loop = Timeline.LOOP;
		Timeline.Track lt = lp.trackOrNew("@self", "y");
		lt.put(0, "0", "linear");
		lt.put(1, "10", "linear");
		Fake f = new Fake("f");
		p = new TimelinePlayer(null, lp, f);
		p.update(1.25f);
		check(near(f.getY(), 2.5f), "loop wraps: " + f.getY());
		lp.loop = Timeline.PING_PONG;
		p = new TimelinePlayer(null, lp, f);
		p.update(1.25f);
		check(near(f.getY(), 7.5f) && p.dir == -1, "ping-pong comes back: " + f.getY());
		p.update(1f);
		check(near(f.getY(), 2.5f) && p.dir == 1, "ping-pong goes again: " + f.getY());

		// ---------------- relative values, colors, yes/no, text variables
		Timeline rel = new Timeline();
		rel.name = "rel";
		rel.duration = 1;
		rel.relative = true;
		rel.trackOrNew("@self", "x").put(0, "0", "linear");
		rel.trackOrNew("@self", "x").put(1, "40", "linear");
		Fake r = new Fake("r");
		r.setX(200);
		p = new TimelinePlayer(null, rel, r);
		p.update(1f);
		check(near(r.getX(), 240), "relative: added to where it was (" + r.getX() + ")");
		Timeline col = new Timeline();
		col.name = "col";
		col.duration = 1;
		col.trackOrNew("@self", "Tint").put(0, "#FFFFFFFF", "linear");
		col.trackOrNew("@self", "Tint").put(1, "#FF0000FF", "linear");
		col.trackOrNew("@self", "Visible").put(0, "true", "step");
		col.trackOrNew("@self", "Visible").put(1, "false", "step");
		col.trackOrNew("@self", "var:label").put(0, "\"go\"", "step");
		col.trackOrNew("@self", "var:label").put(0.5f, "ready", "step");
		col.trackOrNew("@self", "Opacity").put(0, "1", "linear");
		col.trackOrNew("@self", "Opacity").put(1, "0", "linear");
		Fake c = new Fake("c");
		p = new TimelinePlayer(null, col, c);
		p.update(0.5f);
		Color cc = c.getColor();
		check(near(cc.r, 1) && near(cc.g, 0.5f) && near(cc.b, 0.5f) && near(cc.a, 0.5f), "color + opacity halfway: " + cc);
		check(c.isVisible(), "visible until the key");
		check("ready".equals(com.star4droid.template.Utils.ObjectVars.get(c, "label")), "text variable jumps at its key: " + com.star4droid.template.Utils.ObjectVars.get(c, "label"));
		p.update(0.6f);
		check(!c.isVisible(), "hidden at the last key");

		// ---------------- tweens
		Fake t = new Fake("t");
		t.setRotation(-10); // editor rotation 10
		TimelinePlayer tw = Anim.tween(t, "rotation", 90, 1f, "linear");
		check(tw != null && tw.isTween, "tween made");
		tw.update(0.5f);
		check(near(-t.getRotation(), 50), "tween from the current value: " + (-t.getRotation()));
		boolean on = tw.update(0.6f);
		check(!on && near(-t.getRotation(), 90) && Anim.spy.contains("tween:rotation"), "tween ends with its event");
		Fake d = new Fake("d");
		tw = Anim.tween(d, "y", 100, 1f, "linear", 0.5f, 1, true);
		tw.update(0.4f);
		check(near(d.getY(), 0), "delay waits");
		tw.update(0.6f); // 0.5 s after the delay
		check(near(d.getY(), 50), "then moves: " + d.getY());
		tw.update(1.4f); // 1.9 s: there and most of the way back (yoyo, repeat 1)
		check(near(d.getY(), 10), "yoyo comes back: " + d.getY());
		on = tw.update(1f);
		check(!on && near(d.getY(), 0), "yoyo with repeat 1 ends at the start: " + d.getY());

		System.out.println(fails == 0 ? ("ANIM OK (" + checks + " checks)") : (fails + " of " + checks + " checks FAILED"));
		System.exit(fails == 0 ? 0 : 1);
	}
}
