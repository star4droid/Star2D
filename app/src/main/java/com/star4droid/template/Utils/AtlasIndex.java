package com.star4droid.template.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.Project;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Images merged into tileset pages at export time ("Merge images into tilesets"). The export writes
 * files/_atlas.json: { "pages": ["_atlas_0.png", ...], "images": { "folder/name.png": [page, x, y, w, h] } }
 * and removes the merged originals. Everything that used to load images/folder/name.png asks this index first.
 * Without the file (always the case in the editor) this class does nothing.
 */
public class AtlasIndex {
	private static final Map<String, int[]> images = new HashMap<>();
	private static String[] pages = new String[0];
	private static String loadedFor;
	/** The loader of the running game (holds the page textures) and its project. */
	private static ProjectAssetLoader gameLoader;
	private static Project gameProject;
	/** Images taken out of a page as their own texture (repeated / tiled pictures, 3D textures). */
	private static final Map<String, Texture> standalone = new HashMap<>();
	/** Page pictures read for {@link #pixmap}: freed after the frame that needed them. */
	private static final Map<Integer, com.badlogic.gdx.graphics.Pixmap> pagePixmaps = new HashMap<>();
	private static boolean releasePosted;

	public static void init(Project project, ProjectAssetLoader loader) {
		gameLoader = loader;
		gameProject = project;
		init(project);
	}

	/** Reads the index of the project (once per project). */
	public static void init(Project project) {
		String key = project.getImagesPath();
		if (key.equals(loadedFor)) return;
		loadedFor = key;
		images.clear();
		for (Texture t : standalone.values()) try { t.dispose(); } catch (Throwable ignored) {}
		standalone.clear();
		for (Texture t : pageTextures.values()) try { t.dispose(); } catch (Throwable ignored) {}
		pageTextures.clear();
		pages = new String[0];
		try {
			File f = new File(project.get("files") + "/_atlas.json");
			if (!f.isFile()) return;
			Map<String, Object> m = new Gson().fromJson(Gdx.files.absolute(f.getAbsolutePath()).readString(), new TypeToken<Map<String, Object>>() {}.getType());
			java.util.List<?> p = (java.util.List<?>) m.get("pages");
			pages = new String[p.size()];
			for (int i = 0; i < pages.length; i++) pages[i] = String.valueOf(p.get(i));
			Map<?, ?> im = (Map<?, ?>) m.get("images");
			for (Map.Entry<?, ?> e : im.entrySet()) {
				java.util.List<?> r = (java.util.List<?>) e.getValue();
				int[] a = new int[5];
				for (int i = 0; i < 5; i++) a[i] = ((Number) r.get(i)).intValue();
				images.put(String.valueOf(e.getKey()), a);
			}
		} catch (Throwable t) {
			images.clear();
		}
	}

	public static boolean isEmpty() { return images.isEmpty(); }

	/** [page, x, y, w, h] of an image path (absolute path inside the project, or relative to images/), or null. */
	public static int[] find(String path) {
		if (images.isEmpty() || path == null) return null;
		String p = path.replace('\\', '/');
		int i = p.indexOf("images/");
		while (i >= 0) {
			int[] r = images.get(p.substring(i + 7));
			if (r != null) return r;
			i = p.indexOf("images/", i + 1);
		}
		return images.get(p);
	}

	/** The picture of a merged image (optionally only a [x,y,w,h] part of it), or null when the image is not merged. */
	public static Drawable drawable(com.star4droid.template.Utils.ProjectAssetLoader loader, Project project, String path, int[] part) {
		int[] r = find(path);
		if (r == null) return null;
		Texture tex = pageTexture(loader, project, r[0]);
		if (tex == null) return null;
		if (part != null) return new TextureRegionDrawable(new TextureRegion(tex, r[1] + part[0], r[2] + part[1], part[2], part[3]));
		return new TextureRegionDrawable(new TextureRegion(tex, r[1], r[2], r[3], r[4]));
	}

	private static final Map<Integer, Texture> pageTextures = new HashMap<>();

	/** Texture of a tileset page: from the game's loader when it has it (path normalised), otherwise read straight from the file and cached. */
	private static Texture pageTexture(ProjectAssetLoader loader, Project project, int index) {
		if (index < 0 || index >= pages.length) return null;
		String page = (project.getImagesPath() + pages[index]).replace("//", "/");
		try {
			if (loader != null && loader.isLoaded(page)) return loader.get(page, Texture.class);
		} catch (Throwable ignored) {}
		Texture t = pageTextures.get(index);
		if (t != null) return t;
		try {
			com.badlogic.gdx.files.FileHandle fh = Gdx.files.absolute(page);
			if (!fh.exists()) return null;
			t = new Texture(fh);
			pageTextures.put(index, t);
			return t;
		} catch (Throwable e) {
			return null;
		}
	}

	/** The picture of a merged image through the game's loader, or null (not merged / not loaded yet). */
	public static Drawable drawable(String path) {
		if (gameLoader == null || gameProject == null || images.isEmpty()) return null;
		return drawable(gameLoader, gameProject, path, null);
	}

	/**
	 * A merged image as a texture of its own: needed when the picture is repeated (tiled boxes, 3D textures with
	 * repeat) because a part of a page cannot repeat. Cached; null when the image is not merged.
	 */
	public static Texture texture(String path) {
		int[] r = find(path);
		if (r == null) return null;
		String key = r[0] + ":" + r[1] + ":" + r[2] + ":" + r[3] + ":" + r[4];
		Texture t = standalone.get(key);
		if (t != null) return t;
		com.badlogic.gdx.graphics.Pixmap pm = pixmap(path);
		if (pm == null) return null;
		try {
			t = new Texture(pm);
			t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		} finally {
			pm.dispose();
		}
		standalone.put(key, t);
		return t;
	}

	/** A copy of a merged image's pixels (caller disposes it), or null. */
	public static com.badlogic.gdx.graphics.Pixmap pixmap(String path) {
		int[] r = find(path);
		if (r == null || gameProject == null || r[0] < 0 || r[0] >= pages.length) return null;
		try {
			com.badlogic.gdx.graphics.Pixmap page = pagePixmaps.get(r[0]);
			if (page == null) {
				page = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.absolute((gameProject.getImagesPath() + pages[r[0]]).replace("//", "/")));
				pagePixmaps.put(r[0], page);
				if (!releasePosted && Gdx.app != null) {
					releasePosted = true;
					// several tiled elements of one scene share the decoded page: free it once the scene is built
					Gdx.app.postRunnable(new Runnable() {
						@Override
						public void run() {
							releasePages();
						}
					});
				}
			}
			com.badlogic.gdx.graphics.Pixmap out = new com.badlogic.gdx.graphics.Pixmap(r[3], r[4], com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
			out.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
			out.drawPixmap(page, 0, 0, r[1], r[2], r[3], r[4]);
			return out;
		} catch (Throwable t) {
			return null;
		}
	}

	private static void releasePages() {
		releasePosted = false;
		for (com.badlogic.gdx.graphics.Pixmap p : pagePixmaps.values()) try { p.dispose(); } catch (Throwable ignored) {}
		pagePixmaps.clear();
	}

	/** Texture of a region: when it is a part of a merged page, its own repeatable texture; otherwise its texture. */
	public static Texture repeatable(String path, TextureRegion region) {
		Texture tex = region.getTexture();
		if (images.isEmpty()) return tex;
		boolean part = region.getRegionX() != 0 || region.getRegionY() != 0 || region.getRegionWidth() != tex.getWidth() || region.getRegionHeight() != tex.getHeight();
		if (!part) return tex;
		Texture own = texture(path);
		return own != null ? own : tex;
	}
}
