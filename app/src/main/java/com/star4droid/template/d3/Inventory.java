package com.star4droid.template.d3;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;

/**
 * Items the player carries (keys, coins, tools). Shared by every scene of the game (2D or 3D). Listeners hear changes
 * (a HUD updates its slots). Optionally saved between runs.
 */
public final class Inventory {
	private Inventory() {
	}

	public interface Listener {
		void changed(String id, int count);
	}

	private static final ObjectIntMap<String> ITEMS = new ObjectIntMap<String>();
	private static final Array<String> ORDER = new Array<String>();
	private static final Array<Listener> LISTENERS = new Array<Listener>();
	/** Selected slot id ("" = none). */
	public static String selected = "";

	public static void add(String id, int count) {
		if (id == null || id.length() == 0 || count == 0) return;
		int n = ITEMS.get(id, 0) + count;
		if (n <= 0) {
			ITEMS.remove(id, 0);
			ORDER.removeValue(id, false);
			n = 0;
		} else {
			ITEMS.put(id, n);
			if (!ORDER.contains(id, false)) ORDER.add(id);
		}
		fire(id, n);
	}

	public static boolean remove(String id, int count) {
		int n = ITEMS.get(id, 0);
		if (n < count) return false;
		add(id, -count);
		return true;
	}

	public static int count(String id) {
		return ITEMS.get(id, 0);
	}

	public static boolean has(String id) {
		return count(id) > 0;
	}

	/** Number of different items. */
	public static int size() {
		return ORDER.size;
	}

	/** Id of the n-th item (order of pickup), "" when out of range. */
	public static String at(int i) {
		return i >= 0 && i < ORDER.size ? ORDER.get(i) : "";
	}

	public static void clear() {
		ITEMS.clear();
		ORDER.clear();
		selected = "";
		fire("", 0);
	}

	public static void addListener(Listener l) {
		if (!LISTENERS.contains(l, true)) LISTENERS.add(l);
	}

	public static void removeListener(Listener l) {
		LISTENERS.removeValue(l, true);
	}

	static void fire(String id, int n) {
		for (int i = 0; i < LISTENERS.size; i++) {
			try {
				LISTENERS.get(i).changed(id, n);
			} catch (Throwable ignored) {
			}
		}
	}

	/** "key=1;coin=12" */
	public static String save() {
		StringBuilder sb = new StringBuilder();
		for (String id : ORDER) {
			if (sb.length() > 0) sb.append(';');
			sb.append(id).append('=').append(ITEMS.get(id, 0));
		}
		return sb.toString();
	}

	public static void load(String s) {
		ITEMS.clear();
		ORDER.clear();
		if (s == null) return;
		for (String p : s.split(";")) {
			int eq = p.indexOf('=');
			if (eq <= 0) continue;
			try {
				int n = Integer.parseInt(p.substring(eq + 1).trim());
				if (n > 0) {
					ITEMS.put(p.substring(0, eq), n);
					ORDER.add(p.substring(0, eq));
				}
			} catch (Exception ignored) {
			}
		}
		fire("", 0);
	}
}
