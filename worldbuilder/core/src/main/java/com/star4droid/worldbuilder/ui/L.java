package com.star4droid.worldbuilder.ui;

/**
 * Texts of the app. English only for now; every text on screen goes through {@link #t} and every row is laid out with the
 * start / end helpers of {@link Ui}, so another language (and a right-to-left one: set {@link #rtl}) can be added here
 * without touching the screens.
 */
public final class L {
    private L() {}
    public static boolean rtl = false;
    private static final java.util.HashMap<String, String> map = new java.util.HashMap<>();

    /** The text for a key; the key itself is the English text. */
    public static String t(String english) {
        String v = map.get(english);
        return v == null ? english : v;
    }

    public static String t(String english, Object... args) {
        String s = t(english);
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }
}
