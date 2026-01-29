package com.star4droid.template;

/**
 * Platform abstraction interface for cross-platform editor support.
 * Implementations provided by each platform module (Android, Desktop).
 */
public interface PlatformServices {
    
    /**
     * Get the external storage path (e.g., /sdcard on Android, user.home on Desktop)
     */
    String getExternalStoragePath();
    
    /**
     * Get the app's internal files directory
     */
    String getFilesDir();
    
    /**
     * Get the app's cache directory
     */
    String getCacheDir();
    
    /**
     * Pause audio playback (platform-specific implementation)
     */
    void pauseAudio();
    
    /**
     * Resume audio playback
     */
    void resumeAudio();
    
    /**
     * Open a native file picker dialog
     * @param callback Called with the selected file path, or null if cancelled
     */
    void openFilePicker(FilePickerCallback callback);
    
    /**
     * Open a native folder picker dialog
     * @param callback Called with the selected folder path, or null if cancelled
     */
    void openFolderPicker(FilePickerCallback callback);
    
    /**
     * Show a native toast/notification message
     */
    void showToast(String message);
    
    /**
     * Check if running on desktop platform
     */
    boolean isDesktop();
    
    /**
     * Check if running on Android platform
     */
    boolean isAndroid();
    
    /**
     * Get the platform name (e.g., "Android", "Desktop")
     */
    String getPlatformName();
    
    /**
     * Callback for file/folder picker operations
     */
    interface FilePickerCallback {
        void onResult(String path);
    }
    
    // Singleton holder for global access
    class Holder {
        private static PlatformServices instance;
        
        public static void set(PlatformServices services) {
            instance = services;
        }
        
        public static PlatformServices get() {
            return instance;
        }
    }
}
