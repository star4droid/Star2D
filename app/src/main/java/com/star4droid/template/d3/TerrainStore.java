package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Terrain files of the project ("terrains" folder, path "/name.s2t"), read once and shared. The editor edits the
 * shared copy in place and writes it back with {@link #save}.
 */
public final class TerrainStore {
	private static final ObjectMap<String, TerrainData> CACHE = new ObjectMap<String, TerrainData>();

	private TerrainStore() {
	}

	private static String key(String path) {
		return (Assets3D.projectPath == null ? "" : Assets3D.projectPath) + "|" + norm(path);
	}

	static String norm(String path) {
		path = path.replace('\\', '/');
		if (!path.startsWith("/")) path = "/" + path;
		if (path.startsWith("/terrains/")) path = path.substring(9);
		return path;
	}

	public static TerrainData get(String path) {
		if (path == null || path.length() == 0) return null;
		String k = key(path);
		TerrainData d = CACHE.get(k);
		if (d != null) return d;
		FileHandle f = Assets3D.file(norm(path), "terrains");
		if (f == null) return null;
		try {
			d = TerrainData.load(f);
			CACHE.put(k, d);
			return d;
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "terrain " + path + ": " + t);
			return null;
		}
	}

	/** Uses this data for the path (editor: generated / new terrain) without writing it yet. */
	public static void put(String path, TerrainData d) {
		CACHE.put(key(path), d);
	}

	/** Project file of a terrain path. */
	public static java.io.File fileOf(String path) {
		return new java.io.File(Assets3D.projectPath + "/terrains" + norm(path));
	}

	public static boolean save(String path) {
		TerrainData d = CACHE.get(key(path));
		if (d == null) return false;
		try {
			d.save(fileOf(path));
			return true;
		} catch (Throwable t) {
			Gdx.app.error("Star2D-3D", "save terrain " + path + ": " + t);
			return false;
		}
	}

	/** Forget the shared copies (scene / project closed). */
	public static void clear() {
		CACHE.clear();
	}

	public static void forget(String path) {
		CACHE.remove(key(path));
	}
}
