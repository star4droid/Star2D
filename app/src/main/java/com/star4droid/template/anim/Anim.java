package com.star4droid.template.anim;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;

/**
 * Timelines and tweens for blocks and scripts (category "Animation").
 *
 * <pre>
 * Anim.play(this, "intro", panel);                       // timelines/intro.s2tl, its own element's tracks move "panel"
 * Anim.tween(coin, "y", 300, 0.6f, "bounce");             // a quick tween of one property
 * Anim.tween(coin, "Opacity", 0, 0.3f, "linear", 1f, 0, false); // after 1 s, fade out
 * </pre>
 * Events: "When a timeline ends", "When a timeline marker is reached", "When a tween ends" (scene events).
 */
public final class Anim {
	private Anim() {
	}

	private static final WeakHashMap<StageImp, ArrayList<TimelinePlayer>> PLAYERS = new WeakHashMap<StageImp, ArrayList<TimelinePlayer>>();
	private static final WeakHashMap<StageImp, Boolean> STARTED = new WeakHashMap<StageImp, Boolean>();
	private static final HashMap<String, Object[]> CACHE = new HashMap<String, Object[]>(); // path -> {mtime, Timeline}
	private static StageImp last;

	private static synchronized ArrayList<TimelinePlayer> list(StageImp stage) {
		if (stage == null) stage = last;
		if (stage == null) return new ArrayList<TimelinePlayer>();
		ArrayList<TimelinePlayer> l = PLAYERS.get(stage);
		if (l == null) {
			l = new ArrayList<TimelinePlayer>();
			PLAYERS.put(stage, l);
		}
		return l;
	}

	private static StageImp stageOf(Object o) {
		if (o instanceof StageImp) return (StageImp) o;
		if (o instanceof PlayerItem) {
			StageImp s = StageImp.of((PlayerItem) o);
			if (s != null) return s;
		}
		return last;
	}

	// ------------------------------------------------------------------ timeline files

	/** Folder of the timelines of a project. */
	public static String folder(String projectPath) {
		return projectPath + "/timelines/";
	}

	/** The timeline called {@code name} of the running project (cached until the file changes), null when missing. */
	public static Timeline timeline(StageImp stage, String name) {
		if (name == null || name.trim().isEmpty() || stage == null || stage.project == null) return null;
		String n = name.trim();
		if (n.endsWith(Timeline.EXT)) n = n.substring(0, n.length() - Timeline.EXT.length());
		File f = new File(folder(stage.project.getPath()) + n + Timeline.EXT);
		if (!f.isFile()) return null;
		String key = f.getAbsolutePath();
		synchronized (CACHE) {
			Object[] c = CACHE.get(key);
			if (c != null && (Long) c[0] == f.lastModified()) return (Timeline) c[1];
			try {
				byte[] b = new byte[(int) f.length()];
				java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(f));
				try {
					in.readFully(b);
				} finally {
					in.close();
				}
				Timeline tl = Timeline.parse(new String(b, "UTF-8"));
				if (tl.name.isEmpty()) tl.name = n;
				CACHE.put(key, new Object[] {f.lastModified(), tl});
				return tl;
			} catch (Exception e) {
				Gdx.app.error("Anim", "timeline " + n + ": " + e);
				return null;
			}
		}
	}

	/** Names of every timeline of the running project. */
	public static String[] names(StageImp stage) {
		if (stage == null || stage.project == null) return new String[0];
		File[] fs = new File(folder(stage.project.getPath())).listFiles();
		ArrayList<String> out = new ArrayList<String>();
		if (fs != null) for (File f : fs) if (f.getName().endsWith(Timeline.EXT)) out.add(f.getName().substring(0, f.getName().length() - Timeline.EXT.length()));
		java.util.Collections.sort(out);
		return out.toArray(new String[0]);
	}

	// ------------------------------------------------------------------ timelines

	/** Plays a timeline (from the start) on an element (null = on the elements named in its tracks). Returns the player. */
	public static TimelinePlayer play(Object scene, String name, PlayerItem target) {
		StageImp st = stageOf(scene != null ? scene : target);
		Timeline tl = timeline(st, name);
		if (tl == null) {
			if (st != null) Gdx.app.error("Anim", "no timeline \"" + name + "\" in timelines/");
			return null;
		}
		return play(st, tl, target);
	}

	/** Plays a timeline object (scripts can build one in code). */
	public static TimelinePlayer play(StageImp st, Timeline tl, PlayerItem target) {
		if (tl == null) return null;
		ArrayList<TimelinePlayer> l = list(st);
		// the same timeline on the same element starts again
		for (int i = l.size() - 1; i >= 0; i--) {
			TimelinePlayer p = l.get(i);
			if (!p.isTween && p.tl.name.equals(tl.name) && p.target == target) {
				p.stop();
				l.remove(i);
			}
		}
		TimelinePlayer p = new TimelinePlayer(st, tl, target);
		l.add(p);
		p.apply(); // first frame now (no one-frame jump)
		return p;
	}

	public static TimelinePlayer play(Object scene, String name) {
		return play(scene, name, null);
	}

	private static java.util.List<TimelinePlayer> find(Object scene, String name, PlayerItem target) {
		ArrayList<TimelinePlayer> out = new ArrayList<TimelinePlayer>();
		for (TimelinePlayer p : list(stageOf(scene != null ? scene : target)))
			if (!p.isTween && !p.finished && (name == null || p.tl.name.equals(name)) && (target == null || p.target == target)) out.add(p);
		return out;
	}

	/** Stops a timeline (every element it plays on when target is null). The elements stay where they are. */
	public static void stop(Object scene, String name, PlayerItem target) {
		ArrayList<TimelinePlayer> l = list(stageOf(scene != null ? scene : target));
		for (int i = l.size() - 1; i >= 0; i--) {
			TimelinePlayer p = l.get(i);
			if (!p.isTween && p.tl.name.equals(name) && (target == null || p.target == target)) {
				p.stop();
				l.remove(i);
			}
		}
	}

	public static void stop(Object scene, String name) {
		stop(scene, name, null);
	}

	public static void pause(Object scene, String name) {
		for (TimelinePlayer p : find(scene, name, null)) p.paused = true;
	}

	public static void resume(Object scene, String name) {
		for (TimelinePlayer p : find(scene, name, null)) p.paused = false;
	}

	/** Jumps to a time (seconds). */
	public static void seek(Object scene, String name, float seconds) {
		for (TimelinePlayer p : find(scene, name, null)) p.seek(seconds);
	}

	/** Speed (1 = normal, 2 = twice as fast, 0.5 = half). */
	public static void setSpeed(Object scene, String name, float speed) {
		for (TimelinePlayer p : find(scene, name, null)) p.speed = Math.max(0, speed);
	}

	/** Plays backward from where it is (again: forward). */
	public static void reverse(Object scene, String name) {
		for (TimelinePlayer p : find(scene, name, null)) p.dir = -p.dir;
	}

	public static boolean isPlaying(Object scene, String name) {
		for (TimelinePlayer p : find(scene, name, null)) if (!p.paused) return true;
		return false;
	}

	/** Time (seconds) of a playing timeline, 0 when it is not playing. */
	public static float time(Object scene, String name) {
		for (TimelinePlayer p : find(scene, name, null)) return p.time;
		return 0;
	}

	/** Duration (seconds) of a timeline of the project. */
	public static float duration(Object scene, String name) {
		Timeline tl = timeline(stageOf(scene), name);
		return tl == null ? 0 : tl.duration;
	}

	// ------------------------------------------------------------------ tweens

	/** Moves a property of an element to a value in {@code seconds} (text values for colors / images / texts). */
	public static TimelinePlayer tween(PlayerItem item, String property, Object value, float seconds, String interp) {
		return tween(item, property, value, seconds, interp, 0, 0, false);
	}

	/**
	 * Same, after {@code delay} seconds, played {@code repeat} more times (-1 forever), going back and forth with {@code yoyo}.
	 * A new tween of the same property of the same element replaces the old one.
	 */
	public static TimelinePlayer tween(PlayerItem item, String property, Object value, float seconds, String interp, float delay, int repeat, boolean yoyo) {
		if (item == null || property == null) return null;
		StageImp st = stageOf(item);
		ArrayList<TimelinePlayer> l = list(st);
		for (int i = l.size() - 1; i >= 0; i--) {
			TimelinePlayer p = l.get(i);
			if (p.isTween && p.target == item && property.equals(p.tweenProperty)) {
				p.stop();
				l.remove(i);
			}
		}
		Timeline tl = new Timeline();
		tl.name = "tween:" + property;
		tl.duration = Math.max(0.0001f, seconds);
		tl.loop = yoyo ? Timeline.PING_PONG : Timeline.LOOP;
		Timeline.Track tr = new Timeline.Track("@self", property);
		String target = valueText(value);
		tr.keys.add(new Timeline.Key(0, target, "linear")); // replaced by the start value when it starts
		tr.keys.add(new Timeline.Key(tl.duration, target, interp));
		tl.tracks.add(tr);
		TimelinePlayer p = new TimelinePlayer(st, tl, item);
		p.isTween = true;
		p.tweenProperty = property;
		p.repeatLeft = repeat;
		p.delay = Math.max(0, delay);
		if (p.delay <= 0) captureTweenStart(p);
		l.add(p);
		return p;
	}

	static String valueText(Object v) {
		if (v == null) return "0";
		if (v instanceof Color) return "#" + v.toString();
		if (v instanceof Float || v instanceof Double) {
			float f = ((Number) v).floatValue();
			return f == (int) f ? String.valueOf((int) f) : String.valueOf(f);
		}
		return String.valueOf(v);
	}

	/** The tween starts from the value the property has now. */
	static void captureTweenStart(TimelinePlayer p) {
		Timeline.Track tr = p.tl.tracks.get(0);
		PlayerItem it = p.target;
		String prop = tr.property;
		int kind = Props.kind(prop, tr.keys.get(1).v);
		String now;
		if (kind == Props.NUMBER) now = valueText(Props.getNumber(it, prop));
		else if (kind == Props.COLOR) now = "#" + Props.getColor(it, prop, new Color()).toString();
		else if (kind == Props.BOOL) now = String.valueOf(Props.getBool(it, prop));
		else now = tr.keys.get(1).v;
		tr.keys.get(0).v = now;
	}

	/** Stops every tween of an element (it stays where it is). */
	public static void stopTweens(PlayerItem item) {
		ArrayList<TimelinePlayer> l = list(stageOf(item));
		for (int i = l.size() - 1; i >= 0; i--) {
			TimelinePlayer p = l.get(i);
			if (p.isTween && p.target == item) {
				p.stop();
				l.remove(i);
			}
		}
	}

	/** True while a tween of the element runs (any property, or this one). */
	public static boolean isTweening(PlayerItem item, String property) {
		for (TimelinePlayer p : list(stageOf(item)))
			if (p.isTween && !p.finished && p.target == item && (property == null || property.isEmpty() || property.equals(p.tweenProperty))) return true;
		return false;
	}

	public static boolean isTweening(PlayerItem item) {
		return isTweening(item, null);
	}

	// ------------------------------------------------------------------ frame

	/** Engine: once per frame from the running scene. */
	public static void update(StageImp stage, float dt, boolean playing) {
		last = stage;
		if (stage == null) return;
		if (stage.onCreateCalled() && STARTED.get(stage) == null) {
			STARTED.put(stage, Boolean.TRUE);
			autoPlay(stage);
		}
		if (!playing) return;
		ArrayList<TimelinePlayer> l;
		synchronized (Anim.class) {
			l = PLAYERS.get(stage);
		}
		if (l == null || l.isEmpty()) return;
		Object[] arr = l.toArray();
		for (Object o : arr) {
			TimelinePlayer p = (TimelinePlayer) o;
			boolean alive;
			try {
				alive = p.update(dt);
			} catch (RuntimeException e) {
				Gdx.app.error("Anim", p.tl.name + ": " + e);
				alive = false;
			}
			if (!alive) l.remove(p);
		}
	}

	/** Timelines with "play on scene start". */
	private static void autoPlay(StageImp stage) {
		for (String n : names(stage)) {
			Timeline tl = timeline(stage, n);
			if (tl == null || !tl.playOnStart) continue;
			if (!tl.scene.isEmpty() && !tl.scene.equalsIgnoreCase(stageName(stage))) continue;
			PlayerItem t = tl.target.isEmpty() ? null : stage.findItem(tl.target);
			if (t == null && !tl.target.isEmpty() && stage.hasWorld3D()) t = stage.world3d().find(tl.target);
			play(stage, tl, t);
		}
	}

	private static String stageName(StageImp stage) {
		return stage.getClass().getSimpleName();
	}

	/** Engine: the scene closed. */
	public static synchronized void dispose(StageImp stage) {
		ArrayList<TimelinePlayer> l = PLAYERS.remove(stage);
		if (l != null) for (TimelinePlayer p : l) p.stop();
		STARTED.remove(stage);
		if (last == stage) last = null;
	}

	// ------------------------------------------------------------------ events

	/** Tests: sees the events ("end:name", "marker:name:marker", "tween:property"). */
	static java.util.List<String> spy;

	static void fireEnd(TimelinePlayer p) {
		if (spy != null) spy.add(p.isTween ? "tween:" + p.tweenProperty : "end:" + p.tl.name);
		StageImp st = p.stage;
		if (st == null) return;
		try {
			if (p.isTween) st.onTweenEnd(p.target, p.tweenProperty);
			else st.onTimelineEnd(p.tl.name, p.target);
		} catch (RuntimeException e) {
			Gdx.app.error("Anim", "end event: " + e);
		}
	}

	static void fireMarker(TimelinePlayer p, String marker) {
		if (spy != null && !p.isTween) spy.add("marker:" + p.tl.name + ":" + marker);
		StageImp st = p.stage;
		if (st == null || p.isTween) return;
		try {
			st.onTimelineMarker(p.tl.name, marker, p.target);
		} catch (RuntimeException e) {
			Gdx.app.error("Anim", "marker event: " + e);
		}
	}
}
