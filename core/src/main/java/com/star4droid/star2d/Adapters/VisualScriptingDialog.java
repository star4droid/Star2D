package com.star4droid.star2d.Adapters;

/**
 * Stub for VisualScriptingDialog - desktop version.
 * Shows "SOON" message for unavailable features.
 */
public class VisualScriptingDialog {
    public static void openSceneScript(String scene, String script) {
        System.out.println("[SOON] Visual scripting not available on desktop yet");
        showSoon();
    }
    
    public static void openCodeEditor() {
        System.out.println("[SOON] Code editor not available on desktop yet");
        showSoon();
    }
    
    public static void showFor(String name, boolean body, boolean script) {
        System.out.println("[SOON] Visual scripting dialog not available on desktop yet");
        showSoon();
    }
    
    private static void showSoon() {
        try {
            com.star4droid.star2d.editor.TestApp app = com.star4droid.star2d.editor.TestApp.getCurrentApp();
            if (app != null) {
                app.toast("SOON - Feature coming to desktop");
            }
        } catch (Exception e) {
            // Ignore if TestApp not available
        }
    }
}
