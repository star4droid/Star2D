package com.star4droid.star2d.Helpers;

import com.badlogic.gdx.Gdx;

/**
 * Platform-neutral FileUtil replacement.
 * Uses LibGDX file handles instead of Android-specific APIs.
 */
public class FileUtil {
    public static void writeFile(String path, String content) {
        try {
            Gdx.files.absolute(path).writeString(content, false);
        } catch (Exception e) {
            System.err.println("FileUtil.writeFile error: " + e.getMessage());
        }
    }
    
    public static String readFile(String path) {
        try {
            return Gdx.files.absolute(path).readString();
        } catch (Exception e) {
            return "";
        }
    }
    
    public static boolean exists(String path) {
        return Gdx.files.absolute(path).exists();
    }
    
    public static void deleteFile(String path) {
        try {
            Gdx.files.absolute(path).delete();
        } catch (Exception e) {
            System.err.println("FileUtil.deleteFile error: " + e.getMessage());
        }
    }
    
    public static void copyFile(String src, String dest) {
        try {
            Gdx.files.absolute(src).copyTo(Gdx.files.absolute(dest));
        } catch (Exception e) {
            System.err.println("FileUtil.copyFile error: " + e.getMessage());
        }
    }
}
