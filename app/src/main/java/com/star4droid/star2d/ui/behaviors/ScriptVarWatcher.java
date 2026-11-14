package com.star4droid.star2d.ui.behaviors;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.ScriptVarSync;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the stored script variables (element property {@code scriptVars}, scene {@code Events/<scene>/public/scriptvars.json}) in step
 * with the fields written in the Java code of the Body Script / Scene Script. {@link #onEditorClosed} runs when the code editor is left:
 * the scan happens on a background thread, only the final property write goes to the GL thread, and any failure is logged to
 * {@code logs/script variables.txt} and otherwise ignored. See docs/SCRIPTVARS.md.
 */
public final class ScriptVarWatcher {
	private ScriptVarWatcher() {}

	private static final Pattern BODY = Pattern.compile("^(.*)/java/com/star4droid/Game/Scripts/([^/]+)/([^/]+)Script\\.java$");
	private static final Pattern SCENE = Pattern.compile("^(.*)/java/com/star4droid/Game/SceneScript/([^/]+)Script\\.java$");

	/** Call when a file was closed / saved in the code editor (any thread). Files that are not scripts are ignored. */
	public static void onEditorClosed(String path) {
		if (path == null) return;
		final String p = path.replace('\\', '/');
		if (!p.endsWith("Script.java")) return;
		Thread t = new Thread(() -> {
			try {
				run(p);
			} catch (Throwable ex) {
				log(ex);
			}
		}, "script-vars-sync");
		t.setDaemon(true);
		t.start();
	}

	private static void run(String p) {
		Matcher m = BODY.matcher(p);
		boolean body = m.matches();
		if (!body) {
			m = SCENE.matcher(p);
			if (!m.matches()) return;
		}
		SourceScanner.Result r = SourceScanner.scanScript(BehaviorCatalog.readFile(new File(p)));
		if (!r.complete) return; // empty or half-typed file: keep what is stored
		final String root = m.group(1);
		final String scene = findScene(root, m.group(2));
		if (body) syncBody(root, scene, m.group(3), r.vars);
		else {
			syncSceneFile(SceneScriptVars.storage(root, scene), r.vars);
			Ui.ui(() -> {
				EditorShell s = EditorShell.current;
				if (s != null && s.variables != null) s.variables.refresh();
			});
		}
	}

	/** Scene folder names of scripts are lower case: the real scene name of the project. */
	private static String findScene(String root, String name) {
		String[] all = new File(root, "scenes").list();
		if (all != null) {
			for (String s : all) if (s.equals(name)) return s;
			for (String s : all) if (s.equalsIgnoreCase(name)) return s;
		}
		return name;
	}

	// ------------------------------------------------------------------------------------------------ scene script

	/** Syncs a scriptvars.json file with the fields of the script (deleted when nothing is left). Returns the new text. Any thread. */
	public static String syncSceneFile(File store, List<VarSpec> fields) {
		String json = store.isFile() ? BehaviorCatalog.readFile(store) : "";
		String now = ScriptVarSync.sync(json, fields);
		if (ScriptVarSync.canonical(json).equals(now)) return json;
		try {
			if (now.isEmpty()) {
				store.delete();
			} else {
				store.getParentFile().mkdirs();
				try (FileOutputStream o = new FileOutputStream(store)) {
					o.write(now.getBytes("UTF-8"));
				}
			}
		} catch (Exception ex) {
			log(ex);
			return json;
		}
		return now;
	}

	// ------------------------------------------------------------------------------------------------ body script

	/** An element uses the script when its Script property is the name, or "scene/name". Empty = the element's own name. */
	private static boolean uses(String script, String elementName, String liveScene, String scene, String name) {
		if (script == null || script.isEmpty()) script = elementName;
		String sc = liveScene;
		if (script.contains("/")) {
			String[] sp = script.split("/");
			sc = sp[0];
			script = sp.length > 1 ? sp[1] : "";
		}
		return script.equals(name) && sc != null && sc.equalsIgnoreCase(scene);
	}

	private static void syncBody(String root, String scene, String name, List<VarSpec> fields) {
		if (Bridge.ready()) {
			Bridge.gl(() -> {
				LibgdxEditor ed = Bridge.editor();
				if (ed == null || ed.getProject() == null || !sameDir(root, ed.getProject().getPath())) {
					syncSavedScene(root, scene, name, fields);
					return;
				}
				int changed = 0;
				for (Actor a : ed.getActors()) {
					PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
					if (ps == null || !uses(ps.getString("Script"), ps.getString("name"), ed.getScene(), scene, name)) continue;
					String cur = ps.getString("scriptVars");
					String now = ScriptVarSync.sync(cur, fields);
					if (!ScriptVarSync.canonical(cur).equals(now)) {
						ps.put("scriptVars", now);
						changed++;
					}
				}
				// the same script may belong to another scene
				if (!scene.equalsIgnoreCase(ed.getScene())) syncSavedScene(root, scene, name, fields);
				if (changed > 0) ed.getSaveState();
				Ui.ui(ScriptVarWatcher::refreshProperties);
			});
		} else syncSavedScene(root, scene, name, fields);
	}

	private static boolean sameDir(String a, String b) {
		try {
			return new File(a).getCanonicalFile().equals(new File(b).getCanonicalFile());
		} catch (Exception ex) {
			return false;
		}
	}

	/** The saved scene file (scenes/&lt;scene&gt;) of a scene that is not open in the editor: only the scriptVars strings are touched. */
	private static void syncSavedScene(String root, String scene, String name, List<VarSpec> fields) {
		try {
			File f = new File(root, "scenes/" + scene);
			if (!f.isFile()) return;
			String text = BehaviorCatalog.readFile(f);
			if (text.trim().isEmpty()) return;
			JsonArray all = new JsonParser().parse(text).getAsJsonArray();
			boolean changed = false;
			for (JsonElement e : all) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				String el = str(o, "name");
				if (!uses(str(o, "Script"), el, scene, scene, name)) continue;
				String cur = str(o, "scriptVars");
				String now = ScriptVarSync.sync(cur, fields);
				if (!ScriptVarSync.canonical(cur).equals(now)) {
					o.addProperty("scriptVars", now);
					changed = true;
				}
			}
			if (!changed) return;
			try (FileOutputStream out = new FileOutputStream(f)) {
				out.write(new com.google.gson.Gson().toJson(all).getBytes("UTF-8"));
			}
		} catch (Throwable t) {
			log(t);
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	// ------------------------------------------------------------------------------------------------ used by the Properties panel

	/** GL thread: the stored scriptVars of an element (null when unknown). */
	static String readVars(String element) {
		LibgdxEditor ed = Bridge.editor();
		if (ed == null) return null;
		for (Actor a : ed.getActors()) {
			if (!element.equals(a.getName())) continue;
			PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
			return ps == null ? null : ps.getString("scriptVars");
		}
		return null;
	}

	/** Writes scriptVars of an element without an undo step (bookkeeping only: the values the user sees do not change). */
	static void store(String element, String json) {
		Bridge.gl(() -> {
			LibgdxEditor ed = Bridge.editor();
			if (ed == null) return;
			for (Actor a : ed.getActors()) {
				if (!element.equals(a.getName())) continue;
				PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
				if (ps != null) ps.put("scriptVars", json);
				return;
			}
		});
	}

	private static void refreshProperties() {
		EditorShell s = EditorShell.current;
		if (s != null && s.properties != null) s.properties.refreshScriptVars();
	}

	static void log(Throwable t) {
		try {
			if (Gdx.files != null) com.star4droid.star2d.logs.Logs.editor("script_variables", android.util.Log.getStackTraceString(t) + "\n----\n");
		} catch (Throwable ignored) {
		}
	}
}
