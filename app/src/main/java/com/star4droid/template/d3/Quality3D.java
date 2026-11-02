package com.star4droid.template.d3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Graphics quality of the 3D scenes. One global level (LOW..ULTRA, or CUSTOM with the single settings), saved in the
 * game preferences so a settings screen of the game keeps the player's choice. Scenes read it every frame, so a change
 * applies at once.
 */
public final class Quality3D {
	public static final String LOW = "LOW", MEDIUM = "MEDIUM", HIGH = "HIGH", ULTRA = "ULTRA", CUSTOM = "CUSTOM";

	/** Current level. */
	public static String level = MEDIUM;
	/** 0.4..1: size of the 3D picture relative to the screen (lower = faster, a bit blurry). */
	public static float renderScale = 1f;
	/** Objects farther than this (meters) are not drawn. */
	public static float viewDistance = 150f;
	/** Shadow map size; 0 = no shadows. */
	public static int shadowSize = 1024;
	/** Per-pixel lighting (false = per-vertex, faster). */
	public static boolean pixelLighting = true;
	/** 0..1 multiplier of the level-of-detail distances. */
	public static float lodBias = 1f;
	/** Water: 0 = flat, 1 = waves, 2 = waves + foam + reflection tint. */
	public static int waterLevel = 1;
	/** Post effects (custom screen shaders, vignette...) allowed. */
	public static boolean postEffects = true;
	/** Frame limit (0 = no limit). */
	public static int fpsLimit = 60;
	/** Particle / effect amount 0..1. */
	public static float effects = 1f;

	private static boolean loaded;

	private Quality3D() {
	}

	public static void setLevel(String l) {
		setLevel(l, true);
	}

	/** Values of every setting (editor: borrow and give back the game's settings). */
	public static Object[] snapshot() {
		return new Object[]{level, renderScale, viewDistance, shadowSize, pixelLighting, lodBias, waterLevel, postEffects, fpsLimit, effects};
	}

	public static void restore(Object[] s) {
		level = (String) s[0];
		renderScale = (Float) s[1];
		viewDistance = (Float) s[2];
		shadowSize = (Integer) s[3];
		pixelLighting = (Boolean) s[4];
		lodBias = (Float) s[5];
		waterLevel = (Integer) s[6];
		postEffects = (Boolean) s[7];
		fpsLimit = (Integer) s[8];
		effects = (Float) s[9];
	}

	public static void setLevel(String l, boolean applyNow) {
		if (l == null) return;
		l = l.trim().toUpperCase();
		level = l;
		if (LOW.equals(l)) {
			renderScale = 0.6f; viewDistance = 70f; shadowSize = 0; pixelLighting = false; lodBias = 0.5f; waterLevel = 0; postEffects = false; fpsLimit = 30; effects = 0.4f;
		} else if (MEDIUM.equals(l)) {
			renderScale = 0.8f; viewDistance = 120f; shadowSize = 1024; pixelLighting = false; lodBias = 0.8f; waterLevel = 1; postEffects = true; fpsLimit = 60; effects = 0.7f;
		} else if (HIGH.equals(l)) {
			renderScale = 1f; viewDistance = 200f; shadowSize = 2048; pixelLighting = true; lodBias = 1f; waterLevel = 2; postEffects = true; fpsLimit = 60; effects = 1f;
		} else if (ULTRA.equals(l)) {
			renderScale = 1f; viewDistance = 400f; shadowSize = 4096; pixelLighting = true; lodBias = 1.5f; waterLevel = 2; postEffects = true; fpsLimit = 0; effects = 1f;
		} else level = CUSTOM;
		if (applyNow) apply();
	}

	/** Loads the saved choice of the player (once). The scene's own quality is used when nothing was saved. */
	public static void load(String sceneDefault) {
		if (loaded) return;
		loaded = true;
		try {
			Preferences p = Gdx.app.getPreferences("star2d_graphics");
			String l = p.getString("level", "");
			if (l.length() == 0) {
				setLevel(sceneDefault == null || sceneDefault.length() == 0 ? MEDIUM : sceneDefault);
				return;
			}
			setLevel(l);
			if (CUSTOM.equals(l)) {
				renderScale = p.getFloat("renderScale", renderScale);
				viewDistance = p.getFloat("viewDistance", viewDistance);
				shadowSize = p.getInteger("shadowSize", shadowSize);
				pixelLighting = p.getBoolean("pixelLighting", pixelLighting);
				lodBias = p.getFloat("lodBias", lodBias);
				waterLevel = p.getInteger("waterLevel", waterLevel);
				postEffects = p.getBoolean("postEffects", postEffects);
				fpsLimit = p.getInteger("fpsLimit", fpsLimit);
				effects = p.getFloat("effects", effects);
			}
		} catch (Throwable ignored) {
			setLevel(MEDIUM);
		}
	}

	/** Saves the current settings as the player's choice. */
	public static void save() {
		try {
			Preferences p = Gdx.app.getPreferences("star2d_graphics");
			p.putString("level", level);
			p.putFloat("renderScale", renderScale);
			p.putFloat("viewDistance", viewDistance);
			p.putInteger("shadowSize", shadowSize);
			p.putBoolean("pixelLighting", pixelLighting);
			p.putFloat("lodBias", lodBias);
			p.putInteger("waterLevel", waterLevel);
			p.putBoolean("postEffects", postEffects);
			p.putInteger("fpsLimit", fpsLimit);
			p.putFloat("effects", effects);
			p.flush();
		} catch (Throwable ignored) {
		}
	}

	/** A single setting changed: the level becomes CUSTOM. */
	public static void custom() {
		level = CUSTOM;
		apply();
	}

	static void apply() {
		if (renderScale < 0.3f) renderScale = 0.3f;
		if (renderScale > 1f) renderScale = 1f;
		try {
			if (Gdx.graphics != null) Gdx.graphics.setForegroundFPS(fpsLimit <= 0 ? 0 : fpsLimit);
		} catch (Throwable ignored) {
		}
	}
}
