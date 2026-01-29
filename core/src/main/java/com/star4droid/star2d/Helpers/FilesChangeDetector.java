package com.star4droid.star2d.Helpers;

import java.nio.file.Path;

/**
 * Stub for FilesChangeDetector - desktop version.
 */
public class FilesChangeDetector {
    public interface Listener {
        void onFilesChanged();
    }
    
    public FilesChangeDetector(String path) {
    }
    
    public void setListener(Listener listener) {
    }
    
    public void start() {
    }
    
    public void stop() {
    }
    
    /**
     * Static method to detect file changes.
     * Returns false (no changes) as stub.
     */
    public static boolean detect(Path sourcePath, Path changesJsonPath) {
        return false; // Stub - assume no changes
    }
}
