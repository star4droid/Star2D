package com.star4droid.star2d.blocks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.Locale;

/**
 * 2D / 3D filter of the blocks. A block (or a category, a custom block, a section of the block creator) is made for 2D
 * scenes, 3D scenes or both ("dims": "2D", "3D", "BOTH"; missing = BOTH so old files keep working). A scene is 3D when
 * {@code <project>/configs/<scene>.json} has {@code "sceneType": "3D"} (missing = 2D). No Android code.
 */
public final class SceneDims {
	private SceneDims() {
	}

	/** Bit flags: a block for 2D scenes, for 3D scenes, or both. */
	public static final int D2 = 1, D3 = 2, BOTH = 3;

	/** "2D", "3D", "BOTH" (also "ALL", "2D3D", "2D/3D", "any") -> flags; null / empty / unknown -> BOTH. */
	public static int parse(String s) {
		if (s == null) return BOTH;
		String t = s.trim().toUpperCase(Locale.ROOT).replace(" ", "");
		if (t.equals("2D") || t.equals("2")) return D2;
		if (t.equals("3D") || t.equals("3")) return D3;
		return BOTH;
	}

	/** Stored value (a string, or null / missing) -> flags. */
	public static int parse(JsonElement e) {
		try {
			if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return BOTH;
			return parse(e.getAsString());
		} catch (Exception ex) {
			return BOTH;
		}
	}

	/** "dims" of a stored block / section object, else {@code fallback}. */
	public static int of(JsonObject o, int fallback) {
		if (o == null || !o.has("dims")) return fallback;
		return parse(o.get("dims"));
	}

	public static String name(int dims) {
		return dims == D2 ? "2D" : dims == D3 ? "3D" : "BOTH";
	}

	/**
	 * Is a block with these flags shown in a scene of this mode? {@code sceneMode} 0 = unknown (no filter: everything shows),
	 * {@link #D2} or {@link #D3}. Flags 0 count as BOTH.
	 */
	public static boolean visible(int dims, int sceneMode) {
		if (sceneMode != D2 && sceneMode != D3) return true;
		if (dims == 0) dims = BOTH;
		return (dims & sceneMode) != 0;
	}

	/** True when {@code configs/<scene>.json} of the project says "sceneType": "3D". */
	public static boolean is3D(String projectPath, String scene) {
		if (projectPath == null || scene == null || scene.length() == 0) return false;
		try {
			File f = new File(new File(projectPath, "configs"), scene + ".json");
			if (!f.isFile()) return false;
			String text = BehaviorBlocks.readFile(f);
			JsonElement root = JsonParser.parseString(text);
			if (!root.isJsonObject()) return false;
			JsonElement t = root.getAsJsonObject().get("sceneType");
			return t != null && t.isJsonPrimitive() && "3D".equalsIgnoreCase(t.getAsString().trim());
		} catch (Throwable e) {
			return false;
		}
	}

	/** {@link #D3} or {@link #D2} for a scene of a project. */
	public static int modeOf(String projectPath, String scene) {
		return is3D(projectPath, scene) ? D3 : D2;
	}

	/** Mode of the scene of an event file (".../Events/<scene>/..."); {@link #D2} when it can't be told. */
	public static int modeOfEventPath(String visualPath) {
		return modeOf(SceneBehaviors.projectOfEventPath(visualPath), SceneBehaviors.sceneOfEventPath(visualPath));
	}
}
