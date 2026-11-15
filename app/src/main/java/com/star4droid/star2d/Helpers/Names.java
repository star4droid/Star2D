package com.star4droid.star2d.Helpers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Names of elements and variables: they become Java identifiers in the generated scene code (fields, the element's
 * Script class), so English letters, digits and _ are allowed, and Arabic letters too (Java and the dex format accept
 * them): "لاعب", "عدد_النقاط", "enemy2". Not allowed: a digit first, spaces, symbols, invisible marks, Java keywords.
 */
public final class Names {
    private Names() {}

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do",
            "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var", "record", "yield"));

    /** Arabic letter (incl. Persian/Urdu letters of the Arabic block) usable to start a name. */
    static boolean arabicLetter(char c) {
        return (c >= 0x0620 && c <= 0x064A) || (c >= 0x066E && c <= 0x066F) || (c >= 0x0671 && c <= 0x06D3) || c == 0x06D5
                || (c >= 0x06EE && c <= 0x06EF) || (c >= 0x06FA && c <= 0x06FC) || c == 0x06FF;
    }

    /** Arabic character allowed after the first one: letters, harakat, tatweel, Arabic-Indic digits. */
    static boolean arabicPart(char c) {
        return arabicLetter(c) || (c >= 0x064B && c <= 0x065F) || c == 0x0670 || (c >= 0x0660 && c <= 0x0669) || (c >= 0x06F0 && c <= 0x06F9)
                || c == 0x0640;
    }

    static boolean asciiStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    static boolean asciiPart(char c) {
        return asciiStart(c) || (c >= '0' && c <= '9');
    }

    /** True for a valid element / variable name. */
    public static boolean valid(String n) {
        return problem(n) == null;
    }

    /**
     * Null when {@code n} is a valid name, else what is wrong: "empty", "keyword", "digit" (starts with a digit) or the
     * first character that is not allowed.
     */
    public static String problem(String n) {
        if (n == null || n.isEmpty()) return "empty";
        if (KEYWORDS.contains(n)) return "keyword";
        char f = n.charAt(0);
        if (!(asciiStart(f) || arabicLetter(f))) return asciiPart(f) || arabicPart(f) ? "digit" : String.valueOf(f);
        for (int i = 1; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(asciiPart(c) || arabicPart(c))) return String.valueOf(c);
        }
        return null;
    }

    /** Readable English message for {@link #problem}, or null. */
    public static String message(String n) {
        String p = problem(n);
        if (p == null) return null;
        switch (p) {
            case "empty": return "Enter a name";
            case "keyword": return "'" + n + "' is a Java word, choose another name";
            case "digit": return "A name cannot start with a digit";
            default: return "Use letters (English or Arabic), digits and _ ; not allowed: '" + p + "'";
        }
    }
}
