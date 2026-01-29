package com.star4droid.star2d;

/**
 * Utility class for star2d editor.
 */
public class Utils {
    public static float getFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (Exception e) {
            return 0f;
        }
    }
    
    public static int getInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }
}
