package com.star4droid.worldbuilder.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.star4droid.worldbuilder.WorldBuilder;

/** Desktop launcher. Options: --size=WxH, --shots=dir (automatic screenshots for testing, then exit). */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        int w = 1280, h = 720;
        for (String a : args) {
            if (a.startsWith("--size=")) { String[] p = a.substring(7).split("x"); w = Integer.parseInt(p[0]); h = Integer.parseInt(p[1]); }
            if (a.startsWith("--shots=")) WorldBuilder.shotsDir = a.substring(8);
            if (a.startsWith("--script=")) WorldBuilder.shotScript = a.substring(9);
            if (a.startsWith("--density=")) com.star4droid.worldbuilder.ui.Ui.forceDensity = Float.parseFloat(a.substring(10));
        }
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("World Builder");
        c.setWindowedMode(w, h);
        c.useVsync(true);
        c.setBackBufferConfig(8, 8, 8, 8, 24, 0, 2);
        new Lwjgl3Application(new WorldBuilder(), c);
    }
}
