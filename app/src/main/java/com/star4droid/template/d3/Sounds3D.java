package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * The one place that owns the sounds of the 3D behaviors and the B3 blocks (one-shots, loops such as the Car 3D engine, ambient
 * hums, footsteps). Everything loaded here is stopped and disposed when the 3D scene ends ({@link World3D#dispose()}, the editor
 * leaving play mode), paused with the scene and resumed with it, so no sound keeps playing (or comes back on the next app resume)
 * after the scene is closed. Loops ({@code Support3D.Loop}) notice a {@link #generation()} change and start again by themselves.
 */
public final class Sounds3D {
	private static final ObjectMap<String, Sound> CACHE = new ObjectMap<String, Sound>();
	private static int generation;
	private static boolean paused;

	private Sounds3D() {
	}

	/** A project sound (sounds folder; "step.mp3", "/step.mp3" or "sounds/step.mp3"), cached; null when missing. */
	public static synchronized Sound get(String path) {
		if (path == null || path.length() == 0) return null;
		if (CACHE.containsKey(path)) return CACHE.get(path);
		Sound s = null;
		try {
			String p = path.startsWith("sounds/") ? path.substring(7) : path;
			FileHandle f = Assets3D.file(p, "sounds");
			if (f != null && Gdx.audio != null) s = Gdx.audio.newSound(f);
		} catch (Throwable ignored) {
		}
		CACHE.put(path, s);
		return s;
	}

	/** Changes every time the sounds are released: a loop that started before must start again. */
	public static int generation() {
		return generation;
	}

	/** While paused, new sounds should not start (the scene is in the background). */
	public static boolean isPaused() {
		return paused;
	}

	/** Stops every sound (one-shots and loops) and frees them. The scene / world is closing. */
	public static synchronized void stopAll() {
		for (ObjectMap.Entry<String, Sound> e : CACHE) {
			if (e.value == null) continue;
			try {
				e.value.stop();
			} catch (Throwable ignored) {
			}
			try {
				e.value.dispose();
			} catch (Throwable ignored) {
			}
		}
		CACHE.clear();
		generation++;
		paused = false;
	}

	/** The scene goes to the background: every playing sound waits. */
	public static synchronized void pauseAll() {
		paused = true;
		for (ObjectMap.Entry<String, Sound> e : CACHE) {
			if (e.value == null) continue;
			try {
				e.value.pause();
			} catch (Throwable ignored) {
			}
		}
	}

	/** The scene comes back: the sounds paused by {@link #pauseAll()} go on. */
	public static synchronized void resumeAll() {
		if (!paused) return;
		paused = false;
		for (ObjectMap.Entry<String, Sound> e : CACHE) {
			if (e.value == null) continue;
			try {
				e.value.resume();
			} catch (Throwable ignored) {
			}
		}
	}

	/** New audio / GL context (Assets3D.checkContext): the old handles are dead, load again (no dispose of dead handles). */
	static synchronized void forget() {
		CACHE.clear();
		generation++;
		paused = false;
	}
}
