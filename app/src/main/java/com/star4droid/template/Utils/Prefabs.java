package com.star4droid.template.Utils;

import com.badlogic.gdx.Gdx;
import com.star4droid.template.Items.StageImp;

import java.util.WeakHashMap;

/**
 * Prefabs made while the game runs ("Create from prefab" blocks). The editor writes, in every scene whose blocks use a
 * prefab, a method that builds that prefab's elements (with their events and behaviors) and registers it here when the
 * scene starts. The elements are named NAME (the prefab's root) and NAME_child for the others; a name already used gets
 * a number.
 */
public final class Prefabs {
	private Prefabs() {
	}

	/** Builds a prefab of the scene: path like "enemies/Slime", the root at x / y (2D: px, 3D: meters) / z. */
	public interface Factory {
		PlayerItem make(String path, String name, float x, float y, float z);
	}

	private static final WeakHashMap<StageImp, Factory> FACTORIES = new WeakHashMap<StageImp, Factory>();
	private static StageImp last;
	private static int counter;

	/** Called by the generated scene code when the scene is created. */
	public static void register(StageImp stage, Factory f) {
		FACTORIES.put(stage, f);
		last = stage;
	}

	/** 2D: a copy of the prefab with its root's corner at x / y (px, like element positions in the editor). */
	public static PlayerItem spawn(String path, String name, float x, float y) {
		return spawn3D(path, name, x, y, 0);
	}

	/** 3D: a copy of the prefab with its root at x / y / z (meters). */
	public static PlayerItem spawn3D(String path, String name, float x, float y, float z) {
		StageImp s = last;
		Factory f = s == null ? null : FACTORIES.get(s);
		String p = normalize(path);
		if (f == null) {
			log("prefab " + p + ": this scene has no Create from prefab block for it");
			return null;
		}
		String n = name == null || name.trim().isEmpty() ? lastSegment(p) : name.trim();
		try {
			if (s.findItem(n) != null) {
				String base = n;
				do n = base + "_" + (++counter); while (s.findItem(n) != null);
			}
		} catch (Throwable ignored) {
		}
		try {
			PlayerItem it = f.make(p, n, x, y, z);
			if (it == null) log("prefab not found: " + p);
			return it;
		} catch (Throwable t) {
			log("prefab " + p + ": " + t);
			return null;
		}
	}

	static String normalize(String path) {
		String s = path == null ? "" : path.trim().replace('\\', '/');
		if (s.endsWith(".prefab")) s = s.substring(0, s.length() - 7);
		while (s.startsWith("/")) s = s.substring(1);
		if (s.startsWith("prefabs/")) s = s.substring(8);
		return s;
	}

	private static String lastSegment(String p) {
		int i = p.lastIndexOf('/');
		String n = i >= 0 ? p.substring(i + 1) : p;
		return n.replaceAll("[^A-Za-z0-9_]", "_");
	}

	private static void log(String m) {
		try {
			if (Gdx.app != null) Gdx.app.error("Star2D", m);
		} catch (Throwable ignored) {
		}
	}
}
