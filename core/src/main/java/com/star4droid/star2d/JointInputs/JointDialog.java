package com.star4droid.star2d.JointInputs;

import com.star4droid.star2d.editor.ui.PointPicker;
import com.badlogic.gdx.Gdx;

/**
 * Stub for JointDialog - desktop version.
 * Shows "SOON" message for unavailable features.
 */
public class JointDialog {
    
    private String type;
    private String name;
    
    public JointDialog() {
    }
    
    public JointDialog(String type, String name) {
        this.type = type;
        this.name = name;
        showSoon();
    }
    
    public void setValue(String value) {
        // Stub
    }
    
    public void onDone(String string, String name) {
        // Override in subclass
    }
    
    public static void showJointListDialog(Runnable callback) {
        showSoon();
        if (callback != null) callback.run();
    }
    
    public static void showJointListDialog(PointPicker pointPicker, Runnable callback) {
        showSoon();
        if (callback != null) callback.run();
    }
    
    private static void showSoon() {
        System.out.println("[SOON] Joint dialog not available on desktop yet");
        try {
            com.star4droid.star2d.editor.TestApp app = com.star4droid.star2d.editor.TestApp.getCurrentApp();
            if (app != null) {
                Gdx.app.postRunnable(() -> app.toast("SOON - Joints feature coming to desktop"));
            }
        } catch (Exception e) {}
    }
}
