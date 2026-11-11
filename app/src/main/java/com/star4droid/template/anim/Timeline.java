package com.star4droid.template.anim;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * A timeline: properties of elements animated over time with keyframes (file {@code timelines/<name>.s2tl}).
 *
 * <ul>
 * <li>Tracks: one element + one property ("x", "y", "rotation", "Scale X", "Opacity", "Tint", "image", "Text", "Visible",
 * "var:score", 3D "z", "Rotation Y" ...). Each key = a time, a value (text: "120", "#FF8800FF", "/coin.png", "true") and how
 * the value moves INTO it (linear, smooth, bounce, elastic, step...).</li>
 * <li>Markers: a name at a time; reaching it raises "When a timeline marker is reached" (play a sound, run blocks).</li>
 * <li>Loop: ONCE, LOOP or PING_PONG. Relative: number values are added to where the element was when the timeline started,
 * so one timeline can move any element.</li>
 * <li>Target: the element the timeline was made on. Played on another element, its tracks move that element instead.</li>
 * </ul>
 * Pure data + JSON (no GL): also used by the editor.
 */
public final class Timeline {
	public static final int ONCE = 0, LOOP = 1, PING_PONG = 2;
	public static final String EXT = ".s2tl";

	public String name = "";
	public float duration = 1f;
	public int loop = ONCE;
	public boolean relative = false, playOnStart = false;
	/** The element the tracks were made on ("" = none). */
	public String target = "";
	/** Scene it belongs to ("" = any): "play on start" only starts it in that scene. */
	public String scene = "";
	public final ArrayList<Track> tracks = new ArrayList<Track>();
	public final ArrayList<Marker> markers = new ArrayList<Marker>();

	public static final class Key {
		public float t;
		public String v = "";
		/** How the value moves from the previous key into this one. */
		public String interp = "smooth";

		public Key() {
		}

		public Key(float t, String v, String interp) {
			this.t = t;
			this.v = v == null ? "" : v;
			this.interp = interp == null || interp.isEmpty() ? "smooth" : interp;
		}

		public Key copy() {
			return new Key(t, v, interp);
		}
	}

	public static final class Track {
		public String element = "", property = "";
		public final ArrayList<Key> keys = new ArrayList<Key>();

		public Track() {
		}

		public Track(String element, String property) {
			this.element = element == null ? "" : element;
			this.property = property == null ? "" : property;
		}

		public void sort() {
			Collections.sort(keys, KEY_ORDER);
		}

		/** The key at this time (within 1 ms), or null. */
		public Key keyAt(float t) {
			for (Key k : keys) if (Math.abs(k.t - t) < 0.001f) return k;
			return null;
		}

		/** Sets (or adds) the key at this time. */
		public Key put(float t, String v, String interp) {
			Key k = keyAt(t);
			if (k == null) {
				k = new Key(t, v, interp);
				keys.add(k);
				sort();
			} else {
				k.v = v == null ? "" : v;
				if (interp != null && !interp.isEmpty()) k.interp = interp;
			}
			return k;
		}

		public Track copy() {
			Track t = new Track(element, property);
			for (Key k : keys) t.keys.add(k.copy());
			return t;
		}

		/**
		 * The value at a time as text, like the game plays it: numbers and #colors move with the transition of the next key,
		 * other values (texts, images, true / false) switch when their key is reached. "" without keys.
		 */
		public String valueAt(float t) {
			int n = keys.size();
			if (n == 0) return "";
			int i1 = 0;
			while (i1 < n && keys.get(i1).t <= t) i1++;
			Key k0 = i1 == 0 ? keys.get(0) : keys.get(i1 - 1), k1 = i1 >= n ? keys.get(n - 1) : keys.get(i1);
			if (k0 == k1 || k1.t <= k0.t) return k0.v;
			float a = Interp.apply(k1.interp, (t - k0.t) / (k1.t - k0.t));
			try {
				float v0 = Float.parseFloat(k0.v.trim()), v1 = Float.parseFloat(k1.v.trim());
				float v = v0 + (v1 - v0) * a;
				return v == (int) v ? String.valueOf((int) v) : String.valueOf(Math.round(v * 1000f) / 1000f);
			} catch (RuntimeException notNumbers) {
			}
			if (k0.v.startsWith("#") && k1.v.startsWith("#")) {
				com.badlogic.gdx.graphics.Color c0 = Props.parseColor(k0.v, new com.badlogic.gdx.graphics.Color()),
						c1 = Props.parseColor(k1.v, new com.badlogic.gdx.graphics.Color());
				return "#" + c0.lerp(c1, a).toString().toUpperCase();
			}
			return a >= 1f ? k1.v : k0.v;
		}
	}

	public static final class Marker {
		public float t;
		public String name = "";

		public Marker() {
		}

		public Marker(float t, String name) {
			this.t = t;
			this.name = name == null ? "" : name;
		}
	}

	static final Comparator<Key> KEY_ORDER = new Comparator<Key>() {
		public int compare(Key a, Key b) {
			return Float.compare(a.t, b.t);
		}
	};

	public Track track(String element, String property) {
		for (Track t : tracks) if (t.element.equals(element) && t.property.equals(property)) return t;
		return null;
	}

	public Track trackOrNew(String element, String property) {
		Track t = track(element, property);
		if (t == null) {
			t = new Track(element, property);
			tracks.add(t);
		}
		return t;
	}

	/** Duration grows to the last key / marker when it is shorter. */
	public void fitDuration() {
		float end = 0;
		for (Track t : tracks) for (Key k : t.keys) end = Math.max(end, k.t);
		for (Marker m : markers) end = Math.max(end, m.t);
		if (end > duration) duration = end;
	}

	public Timeline copy() {
		Timeline c = new Timeline();
		c.name = name;
		c.duration = duration;
		c.loop = loop;
		c.relative = relative;
		c.playOnStart = playOnStart;
		c.target = target;
		c.scene = scene;
		for (Track t : tracks) c.tracks.add(t.copy());
		for (Marker m : markers) c.markers.add(new Marker(m.t, m.name));
		return c;
	}

	public static String loopName(int loop) {
		return loop == LOOP ? "LOOP" : loop == PING_PONG ? "PING_PONG" : "ONCE";
	}

	public static int loopOf(String s) {
		if (s == null) return ONCE;
		String u = s.trim().toUpperCase().replace('-', '_').replace(' ', '_');
		if (u.startsWith("LOOP") || u.equals("REPEAT")) return LOOP;
		if (u.startsWith("PING") || u.equals("YOYO") || u.equals("BACK_AND_FORTH")) return PING_PONG;
		return ONCE;
	}

	// ------------------------------------------------------------------ JSON

	public static Timeline parse(String json) {
		Timeline tl = new Timeline();
		if (json == null || json.trim().isEmpty()) return tl;
		JsonValue root = new JsonReader().parse(json);
		tl.name = root.getString("name", "");
		tl.duration = Math.max(0.01f, root.getFloat("duration", 1f));
		tl.loop = loopOf(root.getString("loop", "ONCE"));
		tl.relative = root.getBoolean("relative", false);
		tl.playOnStart = root.getBoolean("playOnStart", false);
		tl.target = root.getString("target", "");
		tl.scene = root.getString("scene", "");
		JsonValue tracks = root.get("tracks");
		if (tracks != null)
			for (JsonValue t = tracks.child; t != null; t = t.next) {
				Track tr = new Track(t.getString("element", ""), t.getString("property", ""));
				JsonValue keys = t.get("keys");
				if (keys != null)
					for (JsonValue k = keys.child; k != null; k = k.next) {
						JsonValue v = k.get("v");
						String val = v == null ? "" : (v.isString() ? v.asString() : v.toString());
						tr.keys.add(new Key(k.getFloat("t", 0), val, k.getString("i", "smooth")));
					}
				tr.sort();
				if (!tr.property.isEmpty()) tl.tracks.add(tr);
			}
		JsonValue markers = root.get("markers");
		if (markers != null)
			for (JsonValue m = markers.child; m != null; m = m.next)
				tl.markers.add(new Marker(m.getFloat("t", 0), m.getString("name", "")));
		return tl;
	}

	public String toJson() {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n  \"name\": ").append(q(name)).append(",\n  \"duration\": ").append(num(duration))
				.append(",\n  \"loop\": ").append(q(loopName(loop))).append(",\n  \"relative\": ").append(relative)
				.append(",\n  \"playOnStart\": ").append(playOnStart).append(",\n  \"target\": ").append(q(target))
				.append(",\n  \"scene\": ").append(q(scene)).append(",\n  \"tracks\": [");
		for (int i = 0; i < tracks.size(); i++) {
			Track t = tracks.get(i);
			sb.append(i == 0 ? "\n" : ",\n").append("    {\"element\": ").append(q(t.element)).append(", \"property\": ").append(q(t.property)).append(", \"keys\": [");
			for (int j = 0; j < t.keys.size(); j++) {
				Key k = t.keys.get(j);
				sb.append(j == 0 ? "" : ", ").append("{\"t\": ").append(num(k.t)).append(", \"v\": ").append(q(k.v)).append(", \"i\": ").append(q(k.interp)).append('}');
			}
			sb.append("]}");
		}
		sb.append(tracks.isEmpty() ? "]" : "\n  ]").append(",\n  \"markers\": [");
		for (int i = 0; i < markers.size(); i++) {
			Marker m = markers.get(i);
			sb.append(i == 0 ? "" : ", ").append("{\"t\": ").append(num(m.t)).append(", \"name\": ").append(q(m.name)).append('}');
		}
		sb.append("]\n}\n");
		return sb.toString();
	}

	static String num(float f) {
		if (f == (int) f) return String.valueOf((int) f);
		String s = String.valueOf(Math.round(f * 10000f) / 10000f);
		return s;
	}

	static String q(String s) {
		if (s == null) s = "";
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"' || c == '\\') sb.append('\\').append(c);
			else if (c == '\n') sb.append("\\n");
			else if (c == '\r') sb.append("\\r");
			else if (c == '\t') sb.append("\\t");
			else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
			else sb.append(c);
		}
		return sb.append('"').toString();
	}
}
