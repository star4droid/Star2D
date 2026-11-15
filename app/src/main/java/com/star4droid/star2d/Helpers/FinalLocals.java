package com.star4droid.star2d.Helpers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The bundled compiler is Java 7: a local variable used inside an anonymous class (a Timer.Task of the "wait" / "destroy later"
 * blocks, a Runnable...) must be declared {@code final} ("local variable c is accessed from within inner class; needs to be
 * declared final"). Event code is written by people, blocks and AI that think in Java 8 ("effectively final"), so before the
 * code is compiled every local that is never assigned again gets {@code final}. That is always safe (it changes nothing for a
 * variable that is not reassigned), and keeps the line count, so error lines still point at the right place.
 */
public final class FinalLocals {
    private FinalLocals() {}

    // "<indent>Type name = ..." at the start of a line: Type is a primitive/String or starts with a capital (generics/arrays allowed)
    private static final Pattern DECL = Pattern.compile(
            "^([ \\t]*(?:/\\*@b\\d+\\*/)?)((?:int|float|double|long|boolean|char|byte|short|[A-Z][\\w.]*(?:<[\\w.<>, ?\\[\\]]*>)?)(?:\\[\\])*)[ \\t]+([\\p{L}_$][\\p{L}\\p{Mn}\\p{Nd}_$]*)[ \\t]*=(?!=)",
            Pattern.MULTILINE);

    public static String apply(String code) {
        return apply(code, "");
    }

    /** {@code sameMethod}: other code that ends up in the same method (a reassignment there also keeps the variable non-final). */
    public static String apply(String code, String sameMethod) {
        if (code == null || code.isEmpty() || code.indexOf('=') < 0) return code;
        StringBuilder out = new StringBuilder(code.length() + 64);
        Matcher m = DECL.matcher(code);
        int last = 0;
        while (m.find()) {
            String type = m.group(2), name = m.group(3);
            if (type.equals("final") || type.equals("return") || !safe(code, m.end(), name) || reassigned(sameMethod == null || sameMethod.isEmpty() ? code : code + "\n" + sameMethod, name)) continue;
            // "Type a = 1, b = 2;" : leave multi declarations alone
            if (multi(code, m.end())) continue;
            out.append(code, last, m.start(2)).append("final ");
            last = m.start(2);
        }
        if (last == 0) return code;
        out.append(code.substring(last));
        return out.toString();
    }

    /** The text before the declaration on the same line must be blank (not "for (int i = 0" etc.: that's enforced by ^). */
    private static boolean safe(String code, int end, String name) {
        return !name.equals("final");
    }

    private static boolean reassigned(String code, String name) {
        String n = Pattern.quote(name);
        // name = x (not ==), name += x ..., name++ / ++name / name-- / --name
        Pattern p = Pattern.compile("(?<![\\p{L}\\p{Mn}\\p{Nd}_$.])" + n + "\\s*(?:=(?!=)|[+\\-*/%&|^]=|<<=|>>>?=|\\+\\+|--)|(?:\\+\\+|--)\\s*" + n + "(?![\\p{L}\\p{Mn}\\p{Nd}_$])");
        Matcher m = p.matcher(code);
        int count = 0;
        while (m.find()) {
            // the declaration itself is one "name =" match
            if (++count > 1) return true;
        }
        // also a second declaration with the same name in another scope counts as "not sure"
        return false;
    }

    /** True when the initializer is followed (before its ';' at depth 0) by ", name =". */
    private static boolean multi(String code, int from) {
        int depth = 0;
        boolean str = false, chr = false, esc = false;
        for (int i = from; i < code.length(); i++) {
            char c = code.charAt(i);
            if (str || chr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (str && c == '"') str = false;
                else if (chr && c == '\'') chr = false;
                continue;
            }
            switch (c) {
                case '"': str = true; break;
                case '\'': chr = true; break;
                case '(': case '[': case '{': depth++; break;
                case ')': case ']': case '}': depth--; if (depth < 0) return false; break;
                case ';': if (depth == 0) return false; break;
                case ',': if (depth == 0) return true; break;
            }
        }
        return false;
    }
}
