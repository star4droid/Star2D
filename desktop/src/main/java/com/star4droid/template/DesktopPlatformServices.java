package com.star4droid.template;

import com.badlogic.gdx.Gdx;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.File;

/**
 * Desktop (Windows/Linux/Mac) implementation of PlatformServices.
 * Uses Swing for native dialogs.
 */
public class DesktopPlatformServices implements PlatformServices {
    
    @Override
    public String getExternalStoragePath() {
        return System.getProperty("user.home");
    }
    
    @Override
    public String getFilesDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            appData = System.getProperty("user.home") + "/.star2d";
        } else {
            appData = appData + "/Star2D";
        }
        new File(appData).mkdirs();
        return appData;
    }
    
    @Override
    public String getCacheDir() {
        String temp = System.getProperty("java.io.tmpdir");
        return temp + "/star2d_cache";
    }
    
    @Override
    public void pauseAudio() {
        // Desktop audio doesn't need special pause handling
        // LibGDX handles this automatically
    }
    
    @Override
    public void resumeAudio() {
        // Desktop audio doesn't need special resume handling
    }
    
    @Override
    public void openFilePicker(FilePickerCallback callback) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int result = chooser.showOpenDialog(null);
            
            Gdx.app.postRunnable(() -> {
                if (result == JFileChooser.APPROVE_OPTION) {
                    callback.onResult(chooser.getSelectedFile().getAbsolutePath());
                } else {
                    callback.onResult(null);
                }
            });
        });
    }
    
    @Override
    public void openFolderPicker(FilePickerCallback callback) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = chooser.showOpenDialog(null);
            
            Gdx.app.postRunnable(() -> {
                if (result == JFileChooser.APPROVE_OPTION) {
                    callback.onResult(chooser.getSelectedFile().getAbsolutePath());
                } else {
                    callback.onResult(null);
                }
            });
        });
    }
    
    @Override
    public void showToast(String message) {
        // On desktop, we can use a simple notification or just log it
        System.out.println("[Toast] " + message);
        // Optionally show a Swing dialog:
        // JOptionPane.showMessageDialog(null, message);
    }
    
    @Override
    public boolean isDesktop() {
        return true;
    }
    
    @Override
    public boolean isAndroid() {
        return false;
    }
    
    @Override
    public String getPlatformName() {
        return "Desktop";
    }
}
