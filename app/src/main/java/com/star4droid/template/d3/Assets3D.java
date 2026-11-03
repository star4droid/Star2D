package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Loads and caches the files of a 3D scene: glTF / GLB models (with their animations) and textures.
 * Paths are project relative ("/car.glb" = models/car.glb, "/wood.png" = images/wood.png); absolute paths work too.
 * One cache for the whole game: two elements using the same model share the meshes and the textures.
 */
public final class Assets3D {
	private static final ObjectMap<String, SceneAsset> MODELS = new ObjectMap<String, SceneAsset>();
	private static final ObjectMap<String, Texture> TEXTURES = new ObjectMap<String, Texture>();
	private static final ObjectMap<String, String> ERRORS = new ObjectMap<String, String>();
	/** Folder of the project (set by the scene / the editor). */
	public static String projectPath = "";

	private Assets3D() {
	}

	public static FileHandle file(String path, String folder) {
		if (path == null || path.length() == 0) return null;
		path = path.replace(com.star4droid.template.Utils.Utils.seperator, "/");
		FileHandle f;
		if (path.startsWith("/") && Gdx.files.absolute(path).exists() && path.length() > 1 && path.indexOf('/', 1) > 0 && new java.io.File(path).isAbsolute() && path.startsWith(projectPath.length() > 0 ? projectPath : "/\u0000"))
			return Gdx.files.absolute(path);
		String rel = path.startsWith("/") ? path.substring(1) : path;
		if (projectPath != null && projectPath.length() > 0) {
			f = Gdx.files.absolute(projectPath + "/" + folder + "/" + rel);
			if (f.exists()) return f;
			f = Gdx.files.absolute(projectPath + "/" + rel);
			if (f.exists()) return f;
		}
		f = Gdx.files.absolute(path);
		if (f.exists()) return f;
		f = Gdx.files.internal(folder + "/" + rel);
		return f.exists() ? f : null;
	}

	/** The model file (null + an error message in {@link #error} when it cannot be read). */
	public static SceneAsset model(String path) {
		if (path == null || path.length() == 0) return null;
		SceneAsset a = MODELS.get(path);
		if (a != null) return a;
		if (ERRORS.containsKey(path)) return null;
		FileHandle f = file(path, "models");
		if (f == null) {
			ERRORS.put(path, "model not found: " + path);
			return null;
		}
		try {
			String ext = f.extension().toLowerCase();
			if (ext.equals("glb")) a = new GLBLoader().load(f, true);
			else if (ext.equals("gltf")) a = new GLTFLoader().load(f, true);
			else {
				ERRORS.put(path, "unsupported model format: " + ext + " (use .glb or .gltf)");
				return null;
			}
			Materials3D.fixImported(a.scene.model);
			MODELS.put(path, a);
			return a;
		} catch (Throwable t) {
			ERRORS.put(path, "model error " + path + ": " + t);
			return null;
		}
	}

	public static Model modelOf(String path) {
		SceneAsset a = model(path);
		return a == null ? null : a.scene.model;
	}

	/** Names of the animations of a model. */
	public static Array<String> animations(String path) {
		Array<String> out = new Array<String>();
		Model m = modelOf(path);
		if (m == null) return out;
		for (int i = 0; i < m.animations.size; i++) out.add(m.animations.get(i).id);
		return out;
	}

	public static String error(String path) {
		return ERRORS.get(path);
	}

	public static Texture texture(String path, boolean repeat) {
		if (path == null || path.length() == 0) return null;
		String key = path + (repeat ? "|r" : "");
		Texture t = TEXTURES.get(key);
		if (t != null) return t;
		FileHandle f = file(path, "images");
		if (f == null) return null;
		try {
			t = new Texture(f, true);
			t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
			if (repeat) t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
			TEXTURES.put(key, t);
			return t;
		} catch (Throwable e) {
			return null;
		}
	}

	public static Pixmap pixmap(String path) {
		FileHandle f = file(path, "images");
		if (f == null) return null;
		try {
			return new Pixmap(f);
		} catch (Throwable e) {
			return null;
		}
	}

	private static Object glOwner;

	/**
	 * The caches here (and the shared shapes / water grids / shader programs) hold GL objects of one GL context. A new
	 * activity (editor reopened, the game started again in the same process) has a new context where those handles mean
	 * nothing: forget them (no dispose, their context is gone) and build again.
	 */
	public static void checkContext() {
		Object app = Gdx.app;
		if (app == null || app == glOwner) return;
		boolean first = glOwner == null;
		glOwner = app;
		if (first) return;
		MODELS.clear();
		TEXTURES.clear();
		ERRORS.clear();
		Shapes3D.forgetGL();
		Water3D.forgetGL();
		Outline3D.OutlineShader.program = null;
		B3.forgetSounds();
	}

	/** Frees everything (game exit / editor project change). */
	public static void disposeAll() {
		for (SceneAsset a : MODELS.values()) {
			try {
				a.dispose();
			} catch (Throwable ignored) {
			}
		}
		for (Texture t : TEXTURES.values()) {
			try {
				t.dispose();
			} catch (Throwable ignored) {
			}
		}
		MODELS.clear();
		TEXTURES.clear();
		ERRORS.clear();
	}

	/** Forgets one model (the file changed). */
	public static void reload(String path) {
		SceneAsset a = MODELS.remove(path);
		ERRORS.remove(path);
		if (a != null) {
			try {
				a.dispose();
			} catch (Throwable ignored) {
			}
		}
	}
}
