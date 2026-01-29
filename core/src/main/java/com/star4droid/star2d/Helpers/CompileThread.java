package com.star4droid.star2d.Helpers;

import com.badlogic.gdx.Gdx;

/**
 * Stub for CompileThread - desktop version.
 * Shows "SOON" for compilation features.
 */
public class CompileThread extends Thread {
    
    public interface OnStatusChanged {
        void onStatus(String s);
        void onEnd(String message);
        void onError(String error);
        void onSuccess(String message);
    }
    
    private OnStatusChanged listener;
    private String projectPath;
    private boolean unused;
    
    public CompileThread(String projectPath) {
        this.projectPath = projectPath;
    }
    
    public CompileThread(String projectPath, boolean unused) {
        this.projectPath = projectPath;
        this.unused = unused;
    }
    
    public void setOnChangeStatus(OnStatusChanged listener) {
        this.listener = listener;
    }
    
    @Override
    public void run() {
        System.out.println("[SOON] Compilation not available on desktop yet: " + projectPath);
        
        if (listener != null) {
            Gdx.app.postRunnable(() -> {
                listener.onStatus("Desktop compilation coming soon...");
                listener.onSuccess("Skipped compilation - desktop mode");
                listener.onEnd("Done");
            });
        }
    }
}
