package com.star4droid.template;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidAudio;

/**
 * Android implementation of PlatformServices.
 */
public class AndroidPlatformServices implements PlatformServices {
    private final Context context;
    
    public AndroidPlatformServices(Context context) {
        this.context = context;
    }
    
    @Override
    public String getExternalStoragePath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }
    
    @Override
    public String getFilesDir() {
        return context.getFilesDir().getAbsolutePath();
    }
    
    @Override
    public String getCacheDir() {
        return context.getCacheDir().getAbsolutePath();
    }
    
    @Override
    public void pauseAudio() {
        if (Gdx.app != null && Gdx.app.getAudio() instanceof AndroidAudio) {
            ((AndroidAudio) Gdx.app.getAudio()).pause();
        }
    }
    
    @Override
    public void resumeAudio() {
        if (Gdx.app != null && Gdx.app.getAudio() instanceof AndroidAudio) {
            ((AndroidAudio) Gdx.app.getAudio()).resume();
        }
    }
    
    @Override
    public void openFilePicker(FilePickerCallback callback) {
        // File picker is typically handled via Activity result
        // This is a placeholder - actual implementation requires Activity interaction
        Gdx.app.postRunnable(() -> {
            if (callback != null) callback.onResult(null);
        });
    }
    
    @Override
    public void openFolderPicker(FilePickerCallback callback) {
        // Folder picker is typically handled via Activity result
        Gdx.app.postRunnable(() -> {
            if (callback != null) callback.onResult(null);
        });
    }
    
    @Override
    public void showToast(String message) {
        Gdx.app.postRunnable(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public boolean isDesktop() {
        return false;
    }
    
    @Override
    public boolean isAndroid() {
        return true;
    }
    
    @Override
    public String getPlatformName() {
        return "Android";
    }
}
