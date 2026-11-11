package com.star4droid.star2d.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.template.anim.Interp;
import com.star4droid.template.anim.Timeline;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Editing one timeline in the editor (Timeline panel): the elements show the values of the playhead time (preview), Record
 * turns the changes of an element (moved, turned, resized on the canvas or in its properties) into keys at the playhead,
 * and the scene itself keeps its own values: they are saved instead of the preview ones and put back when the timeline
 * closes. Every change of the timeline is saved to timelines/&lt;name&gt;.s2tl and is one undo step. GL thread.
 */
public final class TimelineSession implements LibgdxEditor.SaveFilter {
	public interface Listener {
		/** The timeline, the playhead or the play / record state changed (GL thread). */
		void changed(TimelineSession s, boolean timelineChanged);
	}

	/** Properties the editor records by itself (when the element has them). */
	public static final String[] AUTO_2D = {"x", "y", "rotation", "Scale X", "Scale Y", "width", "height", "radius", "Tint", "Visible", "Text", "image"};
	public static final String[] AUTO_3D = {"x", "y", "z", "Rotation X", "Rotation Y", "Rotation Z", "Scale X", "Scale Y", "Scale Z", "Color", "Opacity", "Visible"};

	final LibgdxEditor ed;
	public Timeline tl;
	public String file = "";
	public float time;
	public boolean playing, record;
	private int dir = 1;
	public Listener listener;
	/** The scene's own values of everything the preview may change: element -> property -> value. */
	private final HashMap<String, HashMap<String, String>> base = new HashMap<>();
	/** What the preview wrote last (a different value = the user changed it). */
	private final HashMap<String, HashMap<String, String>> shown = new HashMap<>();
	private final ArrayList<String> undo = new ArrayList<>(), redo = new ArrayList<>();
	private long lastFire;
	private boolean warnedRecord;
	/** A copied key (Copy / Paste). */
	public Timeline.Key clip;

	TimelineSession(LibgdxEditor ed) {
		this.ed = ed;
	}

	// ------------------------------------------------------------------ open / close

	public static String folder(LibgdxEditor ed) {
		return ed.getProject().getPath() + "/timelines/";
	}

	/** Names of the timelines of the project. */
	public static ArrayList<String> names(LibgdxEditor ed) {
		ArrayList<String> out = new ArrayList<>();
		File[] fs = new File(folder(ed)).listFiles();
		if (fs != null) for (File f : fs) if (f.getName().endsWith(Timeline.EXT)) out.add(f.getName().substring(0, f.getName().length() - Timeline.EXT.length()));
		java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	/** Opens (or creates) a timeline: the editor now shows its preview. */
	public static TimelineSession open(LibgdxEditor ed, String name) {
		if (ed.timeline != null) ed.timeline.close();
		TimelineSession s = new TimelineSession(ed);
		new File(folder(ed)).mkdirs();
		s.file = folder(ed) + name + Timeline.EXT;
		File f = new File(s.file);
		Timeline tl = null;
		if (f.isFile()) {
			try {
				tl = Timeline.parse(Gdx.files.absolute(s.file).readString("UTF-8"));
			} catch (Throwable t) {
				Gdx.app.error("Timeline", "read " + name + ": " + t);
			}
		}
		if (tl == null) {
			tl = new Timeline();
			tl.duration = 2f;
			tl.scene = ed.getScene();
		}
		tl.name = name;
		s.tl = tl;
		ed.timeline = s;
		ed.saveFilter = s;
		if (!f.isFile()) s.save();
		s.apply();
		return s;
	}

	/** Leaves the timeline: the scene's own values come back. */
	public void close() {
		playing = false;
		record = false;
		restoreAll();
		if (ed.timeline == this) ed.timeline = null;
		if (ed.saveFilter == this) ed.saveFilter = null;
		try {
			ed.getProject().save(ed);
		} catch (Throwable ignored) {
		}
		fire(true);
	}

	private void restoreAll() {
		for (Map.Entry<String, HashMap<String, String>> e : base.entrySet()) {
			Actor a = ed.findActor(e.getKey());
			if (!(a instanceof EditorItem)) continue;
			PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
			if (ps == null) continue;
			boolean heavy = false;
			for (Map.Entry<String, String> p : e.getValue().entrySet()) {
				if (p.getValue() == null || (!ps.containsKey(p.getKey()) && !(a instanceof Editor3DItem))) continue;
				ps.put(p.getKey(), p.getValue());
				heavy |= heavy(p.getKey());
			}
			refresh(a, ps, heavy);
			if (!(a instanceof Editor3DItem)) a.getColor().a = 1f;
		}
		base.clear();
		shown.clear();
	}

	public void save() {
		try {
			tl.fitDuration();
			Gdx.files.absolute(file).writeString(tl.toJson(), false, "UTF-8");
		} catch (Throwable t) {
			Gdx.app.error("Timeline", "save: " + t);
		}
	}

	// ------------------------------------------------------------------ editing (one undo step each)

	private void begin() {
		undo.add(tl.toJson());
		if (undo.size() > 80) undo.remove(0);
		redo.clear();
	}

	private void end() {
		save();
		apply();
		fire(true);
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	public void undo() {
		if (undo.isEmpty()) return;
		redo.add(tl.toJson());
		swap(undo.remove(undo.size() - 1));
	}

	public void redo() {
		if (redo.isEmpty()) return;
		undo.add(tl.toJson());
		swap(redo.remove(redo.size() - 1));
	}

	private void swap(String json) {
		String name = tl.name;
		tl = Timeline.parse(json);
		tl.name = name;
		end();
	}

	/** The element a track moves in the editor ("@self" = the timeline's preview element). */
	public String elementOf(Timeline.Track t) {
		String e = t.element;
		if (e == null || e.isEmpty() || e.equals("@self")) return tl.target == null ? "" : tl.target;
		return e;
	}

	/** Track name stored for an element (the preview element is saved as "@self": the timeline can play on any element). */
	private String trackElement(String element) {
		return tl.target != null && !tl.target.isEmpty() && tl.target.equals(element) ? "@self" : element;
	}

	public void setKey(String element, String property, float t, String value, String interp) {
		if (element == null || element.isEmpty() || property == null || property.isEmpty()) return;
		begin();
		Timeline.Track tr = tl.trackOrNew(trackElement(element), property);
		boolean first = tr.keys.isEmpty();
		ensureBase(element, property);
		if (first && t > 0.0005f) {
			// a new track starts from the scene's value at 0
			String b = baseValue(element, property);
			if (b != null) tr.put(0, rel(property, b, b), "linear");
		}
		tr.put(snapTime(t), rel(property, value, baseValue(element, property)), interp == null || interp.isEmpty() ? "smooth" : interp);
		end();
	}

	/** Adds a key at the playhead with the value the element shows now. */
	public void keyNow(String element, String property) {
		Actor a = ed.findActor(element);
		if (!(a instanceof EditorItem)) return;
		PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
		String v = current(a, ps, property);
		setKey(element, property, time, v, null);
	}

	public void deleteKey(int track, int key) {
		Timeline.Track tr = track(track);
		if (tr == null || key < 0 || key >= tr.keys.size()) return;
		begin();
		tr.keys.remove(key);
		if (tr.keys.isEmpty()) tl.tracks.remove(tr);
		end();
	}

	public void moveKey(int track, int key, float t) {
		Timeline.Track tr = track(track);
		if (tr == null || key < 0 || key >= tr.keys.size()) return;
		t = Math.max(0, snapTime(t));
		Timeline.Key k = tr.keys.get(key);
		if (Math.abs(k.t - t) < 0.0005f) return;
		begin();
		Timeline.Key other = tr.keyAt(t);
		if (other != null && other != k) tr.keys.remove(other);
		k.t = t;
		tr.sort();
		end();
	}

	public void setInterp(int track, int key, String interp) {
		Timeline.Track tr = track(track);
		if (tr == null || key < 0 || key >= tr.keys.size()) return;
		begin();
		tr.keys.get(key).interp = interp;
		end();
	}

	public void setKeyValue(int track, int key, String value) {
		Timeline.Track tr = track(track);
		if (tr == null || key < 0 || key >= tr.keys.size()) return;
		begin();
		tr.keys.get(key).v = value == null ? "" : value.trim();
		end();
	}

	public void copyKey(int track, int key) {
		Timeline.Track tr = track(track);
		if (tr != null && key >= 0 && key < tr.keys.size()) clip = tr.keys.get(key).copy();
	}

	/** Pastes the copied key on a track at the playhead. */
	public void pasteKey(int track) {
		Timeline.Track tr = track(track);
		if (tr == null || clip == null) return;
		begin();
		tr.put(snapTime(time), clip.v, clip.interp);
		end();
	}

	public void deleteTrack(int track) {
		Timeline.Track tr = track(track);
		if (tr == null) return;
		begin();
		tl.tracks.remove(tr);
		end();
		restoreProperty(elementOf(tr), tr.property);
	}

	public void addMarker(float t, String name) {
		begin();
		tl.markers.add(new Timeline.Marker(snapTime(t), name));
		end();
	}

	public void renameMarker(int i, String name) {
		if (i < 0 || i >= tl.markers.size()) return;
		begin();
		tl.markers.get(i).name = name;
		end();
	}

	public void deleteMarker(int i) {
		if (i < 0 || i >= tl.markers.size()) return;
		begin();
		tl.markers.remove(i);
		end();
	}

	/** Duration (seconds), loop ONCE / LOOP / PING_PONG, relative values, play when the scene starts, preview element. */
	public void settings(float duration, int loop, boolean relative, boolean playOnStart, String target) {
		begin();
		tl.duration = Math.max(0.05f, duration);
		tl.loop = loop;
		if (relative != tl.relative) convertRelative(relative);
		tl.playOnStart = playOnStart;
		if (playOnStart) tl.scene = ed.getScene();
		String old = tl.target == null ? "" : tl.target;
		String nt = target == null ? "" : target;
		if (!old.equals(nt)) {
			// tracks of the new preview element become "@self", the old "@self" tracks get the old element's name
			for (Timeline.Track tr : tl.tracks) {
				if (tr.element.equals("@self")) tr.element = old;
				else if (!nt.isEmpty() && tr.element.equals(nt)) tr.element = "@self";
			}
			tl.target = nt;
		}
		end();
	}

	/** Absolute values become offsets from the scene's values (or back). */
	private void convertRelative(boolean on) {
		for (Timeline.Track tr : tl.tracks) {
			String b = baseValue(elementOf(tr), tr.property);
			Float bf = num(b);
			if (bf == null) continue;
			for (Timeline.Key k : tr.keys) {
				Float v = num(k.v);
				if (v != null) k.v = fmt(on ? v - bf : v + bf);
			}
		}
		tl.relative = on;
	}

	private Timeline.Track track(int i) {
		return i >= 0 && i < tl.tracks.size() ? tl.tracks.get(i) : null;
	}

	/** Keys snap to 1/100 s. */
	static float snapTime(float t) {
		return Math.round(Math.max(0, t) * 100f) / 100f;
	}

	// ------------------------------------------------------------------ playhead

	public void seek(float t) {
		time = Math.max(0, Math.min(t, tl.duration));
		apply();
		fire(false);
	}

	public void setPlaying(boolean on) {
		playing = on;
		if (on && time >= tl.duration - 1e-4f && tl.loop == Timeline.ONCE) time = 0;
		dir = 1;
		fire(false);
	}

	public void setRecord(boolean on) {
		record = on;
		if (on) playing = false;
		fire(false);
	}

	/** Every frame (LibgdxEditor.draw): plays, then sees what the user changed. */
	public void frame(float dt) {
		if (tl == null) return;
		if (playing) {
			time += dt * dir;
			if (time >= tl.duration) {
				if (tl.loop == Timeline.LOOP) time = tl.duration <= 0 ? 0 : time % tl.duration;
				else if (tl.loop == Timeline.PING_PONG) {
					time = tl.duration;
					dir = -1;
				} else {
					time = tl.duration;
					playing = false;
				}
			} else if (time <= 0 && dir < 0) {
				time = 0;
				dir = 1;
			}
			apply();
			fireThrottled();
		}
		capture();
	}

	private void fireThrottled() {
		long now = System.currentTimeMillis();
		if (now - lastFire < 33) return;
		fire(false);
	}

	private void fire(boolean changed) {
		lastFire = System.currentTimeMillis();
		if (listener != null) listener.changed(this, changed);
	}

	// ------------------------------------------------------------------ preview

	private static boolean heavy(String prop) {
		return prop.equals("image") || prop.equals("Tint") || prop.equals("Text") || prop.equals("Color");
	}

	private void refresh(Actor a, PropertySet<String, Object> ps, boolean heavy) {
		try {
			if (heavy) ((EditorItem) a).setProperties(ps);
			else ((EditorItem) a).update();
		} catch (Throwable ignored) {
		}
	}

	/** Shows every track at the playhead time. */
	public void apply() {
		if (tl == null) return;
		HashMap<Actor, Boolean> touched = new HashMap<>();
		HashMap<Actor, Float> opacity = new HashMap<>();
		for (Timeline.Track tr : tl.tracks) {
			if (tr.keys.isEmpty() || tr.property.startsWith("var:")) continue;
			String el = elementOf(tr);
			Actor a = ed.findActor(el);
			if (!(a instanceof EditorItem)) continue;
			PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
			if (ps == null) continue;
			ensureBase(el, tr.property);
			String v = tr.valueAt(time);
			if (tl.relative) v = absolute(tr.property, v, baseValue(el, tr.property));
			if (tr.property.equals("Opacity") && !(a instanceof Editor3DItem)) {
				Float f = num(v);
				if (f != null) opacity.put(a, Math.max(0, Math.min(1, f)));
				shownMap(el).put(tr.property, v);
				continue;
			}
			if (!ps.containsKey(tr.property) && !(a instanceof Editor3DItem)) continue;
			Object cur = ps.get(tr.property);
			if (!same(cur == null ? null : cur.toString(), v)) {
				ps.put(tr.property, v);
				Boolean h = touched.get(a);
				touched.put(a, (h != null && h) || heavy(tr.property));
			}
			shownMap(el).put(tr.property, v);
		}
		for (Map.Entry<Actor, Boolean> e : touched.entrySet()) refresh(e.getKey(), ((EditorItem) e.getKey()).getPropertySet(), e.getValue());
		for (Map.Entry<Actor, Float> e : opacity.entrySet()) e.getKey().getColor().a = e.getValue();
	}

	/** A property whose track was deleted shows the scene's value again. */
	private void restoreProperty(String element, String prop) {
		HashMap<String, String> b = base.get(element);
		if (b == null || !b.containsKey(prop)) return;
		Actor a = ed.findActor(element);
		if (!(a instanceof EditorItem)) return;
		PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
		String v = b.get(prop);
		if (v != null && ps != null && (ps.containsKey(prop) || a instanceof Editor3DItem)) {
			ps.put(prop, v);
			refresh(a, ps, heavy(prop));
		}
		if (prop.equals("Opacity") && !(a instanceof Editor3DItem)) a.getColor().a = 1f;
		HashMap<String, String> sh = shown.get(element);
		if (sh != null) sh.remove(prop);
	}

	private HashMap<String, String> shownMap(String el) {
		HashMap<String, String> m = shown.get(el);
		if (m == null) shown.put(el, m = new HashMap<>());
		return m;
	}

	private void ensureBase(String element, String prop) {
		HashMap<String, String> m = base.get(element);
		if (m == null) base.put(element, m = new HashMap<>());
		if (m.containsKey(prop)) return;
		Actor a = ed.findActor(element);
		if (!(a instanceof EditorItem)) return;
		m.put(prop, current(a, ((EditorItem) a).getPropertySet(), prop));
	}

	private String baseValue(String element, String prop) {
		HashMap<String, String> m = base.get(element);
		return m == null ? null : m.get(prop);
	}

	/** The value an element shows now ("Opacity" of a 2D element = its alpha). */
	static String current(Actor a, PropertySet<String, Object> ps, String prop) {
		if (prop.equals("Opacity") && !(a instanceof Editor3DItem)) return fmt(a.getColor().a);
		if (ps == null) return "";
		Object v = ps.get(prop);
		if (v == null) {
			if (prop.equals("Opacity") || prop.startsWith("Scale")) return "1";
			if (prop.equals("Visible")) return "true";
			return "";
		}
		return v.toString();
	}

	// ------------------------------------------------------------------ record

	/** Sees what the user changed: Record on = keys at the playhead; off = the scene's value changed (or a warning). */
	private void capture() {
		// while a finger drags an element, wait: the change is recorded once, when it is released
		if (Gdx.input.isTouched()) return;
		Actor sel = ed.getSelectedActor();
		HashMap<String, Actor> watch = new HashMap<>();
		for (String n : base.keySet()) {
			Actor a = ed.findActor(n);
			if (a != null) watch.put(n, a);
		}
		if (sel instanceof EditorItem && sel.getName() != null) {
			watch.put(sel.getName(), sel);
			// the selected element's values are known before the user changes them
			for (String p : sel instanceof Editor3DItem ? AUTO_3D : AUTO_2D) {
				PropertySet<String, Object> ps = ((EditorItem) sel).getPropertySet();
				if (ps != null && (ps.containsKey(p) || sel instanceof Editor3DItem)) ensureBase(sel.getName(), p);
			}
		}
		boolean changed = false;
		for (Map.Entry<String, Actor> e : watch.entrySet()) {
			String el = e.getKey();
			Actor a = e.getValue();
			if (!(a instanceof EditorItem)) continue;
			PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
			HashMap<String, String> b = base.get(el);
			if (ps == null || b == null) continue;
			HashMap<String, String> sh = shown.get(el);
			for (String prop : new ArrayList<>(b.keySet())) {
				if (prop.equals("Opacity") && !(a instanceof Editor3DItem)) continue; // alpha is preview only
				String cur = current(a, ps, prop);
				String expected = sh != null && sh.containsKey(prop) ? sh.get(prop) : b.get(prop);
				if (expected == null || same(cur, expected)) continue;
				Timeline.Track tr = tl.track(trackElement(el), prop);
				if (record) {
					recordKey(el, prop, cur);
					changed = true;
				} else if (tr != null && !tr.keys.isEmpty()) {
					// an animated property changed without Record: the preview wins (the user is told once)
					ps.put(prop, expected);
					refresh(a, ps, heavy(prop));
					if (!warnedRecord) {
						warnedRecord = true;
						warn = true;
						if (listener != null) listener.changed(this, false);
					}
				} else {
					b.put(prop, cur); // a normal edit of the scene
				}
			}
		}
		if (changed) end();
	}

	/** Set when an animated property was changed without Record (the panel shows a hint once). */
	public boolean warn;

	private void recordKey(String el, String prop, String value) {
		undo.add(tl.toJson());
		if (undo.size() > 80) undo.remove(0);
		redo.clear();
		Timeline.Track tr = tl.trackOrNew(trackElement(el), prop);
		String b = baseValue(el, prop);
		if (tr.keys.isEmpty() && time > 0.0005f && b != null) tr.put(0, rel(prop, b, b), "linear");
		tr.put(snapTime(time), rel(prop, value, b), tr.keys.size() > 0 && tr.keyAt(snapTime(time)) != null ? null : "smooth");
		shownMap(el).put(prop, value);
		if (time > tl.duration) tl.duration = time;
	}

	// ------------------------------------------------------------------ save filter

	@Override
	public PropertySet<String, Object> forSave(Actor actor, PropertySet<String, Object> ps) {
		HashMap<String, String> b = actor == null || actor.getName() == null ? null : base.get(actor.getName());
		if (b == null || ps == null) return ps;
		PropertySet<String, Object> copy = new PropertySet<>();
		copy.putAll(ps);
		for (Map.Entry<String, String> e : b.entrySet())
			if (e.getValue() != null && ps.containsKey(e.getKey())) copy.put(e.getKey(), e.getValue());
		return copy;
	}

	// ------------------------------------------------------------------ values

	static Float num(String s) {
		if (s == null) return null;
		try {
			return Float.parseFloat(s.trim());
		} catch (RuntimeException e) {
			return null;
		}
	}

	static String fmt(float v) {
		return v == (int) v ? String.valueOf((int) v) : String.valueOf(Math.round(v * 1000f) / 1000f);
	}

	/** Two values are the same (numbers within 0.001). */
	static boolean same(String a, String b) {
		if (a == null || b == null) return a == b;
		if (a.equals(b)) return true;
		Float x = num(a), y = num(b);
		if (x != null && y != null) return Math.abs(x - y) < 1e-3f;
		return a.equalsIgnoreCase(b);
	}

	/** Value stored in a key: an offset in a relative timeline (numbers only). */
	private String rel(String prop, String value, String baseV) {
		if (!tl.relative) return value;
		Float v = num(value), b = num(baseV);
		return v != null && b != null ? fmt(v - b) : value;
	}

	private String absolute(String prop, String value, String baseV) {
		Float v = num(value), b = num(baseV);
		return v != null && b != null ? fmt(v + b) : value;
	}

	/** The interpolations offered (the game uses the same names). */
	public static String[] interps() {
		return Interp.NAMES;
	}
}
