package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Device settings (kept between runs): quality and editor preferences. */
public final class Settings {
    public static int quality = 1;            // 0 low, 1 medium, 2 high, 3 ultra, 4 custom
    public static float renderDistance = 140;
    public static int shadows = 1;            // 0 off, 1 1024, 2 2048 + soft
    public static float resScale = 0.85f;
    public static boolean detail = true, waterFull = true, wind = true, showFps = false, grid = false, snap = false, groundSnap = true, autosave = true, helpShown = false, showColliders = false;
    public static float snapStep = 0.5f;
    public static float uiScale = 1f;
    public static String lastWorld = "";

    public static final String[] QUALITY = {"Low", "Medium", "High", "Ultra", "Custom"};

    public static void preset(int q) {
        quality = q;
        switch (q) {
            case 0: renderDistance = 80; shadows = 0; resScale = 0.6f; detail = false; waterFull = false; break;
            case 1: renderDistance = 140; shadows = 1; resScale = 0.8f; detail = true; waterFull = true; break;
            case 2: renderDistance = 200; shadows = 2; resScale = 1f; detail = true; waterFull = true; break;
            case 3: renderDistance = 300; shadows = 2; resScale = 1f; detail = true; waterFull = true; break;
        }
    }

    static Preferences prefs() { return Gdx.app.getPreferences("worldbuilder"); }

    public static void load() {
        Preferences p = prefs();
        if (!p.contains("quality")) { preset(Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop ? 2 : 1); return; }
        quality = p.getInteger("quality", 1); renderDistance = p.getFloat("renderDistance", 140); shadows = p.getInteger("shadows", 1); resScale = p.getFloat("resScale", 0.85f);
        detail = p.getBoolean("detail", true); waterFull = p.getBoolean("waterFull", true); wind = p.getBoolean("wind", true); showFps = p.getBoolean("showFps", false);
        grid = p.getBoolean("grid", false); snap = p.getBoolean("snap", false); groundSnap = p.getBoolean("groundSnap", true); autosave = p.getBoolean("autosave", true);
        snapStep = p.getFloat("snapStep", 0.5f); uiScale = p.getFloat("uiScale", 1f); lastWorld = p.getString("lastWorld", ""); helpShown = p.getBoolean("helpShown", false); showColliders = p.getBoolean("showColliders", false);
    }

    public static void save() {
        Preferences p = prefs();
        p.putInteger("quality", quality); p.putFloat("renderDistance", renderDistance); p.putInteger("shadows", shadows); p.putFloat("resScale", resScale);
        p.putBoolean("detail", detail); p.putBoolean("waterFull", waterFull); p.putBoolean("wind", wind); p.putBoolean("showFps", showFps);
        p.putBoolean("grid", grid); p.putBoolean("snap", snap); p.putBoolean("groundSnap", groundSnap); p.putBoolean("autosave", autosave);
        p.putFloat("snapStep", snapStep); p.putFloat("uiScale", uiScale); p.putString("lastWorld", lastWorld); p.putBoolean("helpShown", helpShown); p.putBoolean("showColliders", showColliders);
        p.flush();
    }
}
