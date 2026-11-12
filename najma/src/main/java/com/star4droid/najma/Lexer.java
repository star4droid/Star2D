package com.star4droid.najma;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns Najma source text into tokens. Arabic digits, punctuation and letter variants are normalised here,
 * so the rest of the compiler never sees them: ٠-٩ become 0-9, ، becomes , and ؛ becomes ; and so on.
 * Diacritics (tashkeel), tatweel and invisible direction marks are dropped.
 */
public final class Lexer {
    public static final int ID = 1, NUM = 2, STR = 3, CHR = 4, SYM = 5, NL = 6, RAW = 7, EOF = 8;

    public static final class Tok {
        public int type, line, col, end, pos, endPos;
        /** ID: the word as written (cleaned). SYM: the symbol. NUM: a Java literal. RAW: Java text. */
        public String text = "";
        /** ID: comparison form (hamza and alef maqsura unified). */
        public String norm = "";
        /** NUM: "int" / "float" / "double" / "long". STR: List of String | List of Tok. */
        public Object val;

        public boolean is(String symbol) { return type == SYM && text.equals(symbol); }
        public boolean word(String w) { return type == ID && norm.equals(w); }
        @Override public String toString() { return type == NL ? "نهاية السطر" : type == EOF ? "نهاية الملف" : text; }
    }

    private final String src;
    private final List<Najma.Diag> diags;
    private int i, line, col, depth;
    private final List<Tok> out = new ArrayList<>();

    private Lexer(String src, int line, int col, List<Najma.Diag> diags) {
        this.src = src;
        this.line = line;
        this.col = col;
        this.diags = diags;
    }

    public static List<Tok> lex(String src, List<Najma.Diag> diags) {
        Lexer l = new Lexer(src, 1, 1, diags);
        l.run();
        Tok e = l.tok(EOF);
        l.out.add(e);
        return l.out;
    }

    /** Hamza forms and alef maqsura unified; used to compare words. */
    public static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ') c = 'ا';
            else if (c == 'ى') c = 'ي';
            else if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            else if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            else if (isSkippable(c)) continue;
            b.append(c);
        }
        return b.toString();
    }

    static boolean isSkippable(char c) {
        return (c >= 'ً' && c <= 'ْ') || c == 'ٰ' || c == 'ـ' || c == '​' || c == '‌' || c == '‍' || c == '‎' || c == '‏'
                || (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩') || c == '﻿';
    }

    static boolean isArabicLetter(char c) {
        return (c >= 'ء' && c <= 'ي') || (c >= 'ٮ' && c <= 'ۓ') || c == 'ە';
    }

    static boolean idStart(char c) {
        return isArabicLetter(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    static boolean digit(char c) {
        return (c >= '0' && c <= '9') || (c >= '٠' && c <= '٩') || (c >= '۰' && c <= '۹');
    }

    static boolean idPart(char c) {
        return idStart(c) || digit(c) || isSkippable(c);
    }

    private Tok tok(int type) {
        Tok t = new Tok();
        t.type = type;
        t.line = line;
        t.col = col;
        t.pos = i;
        return t;
    }

    private void adv() {
        if (src.charAt(i) == '\n') { line++; col = 1; } else col++;
        i++;
    }

    private void err(Tok t, String msg) {
        if (diags != null) diags.add(new Najma.Diag(t.line, t.col, t.col + 1, msg));
    }

    private Tok last() { return out.isEmpty() ? null : out.get(out.size() - 1); }

    private void push(Tok t) {
        t.end = col;
        t.endPos = i;
        out.add(t);
    }

    private static final String CONT = "+ - * / % ÷ = == != < > <= >= && || , ( [ . += -= *= /= ← -> ? !";

    private void newline() {
        Tok p = last();
        if (depth > 0 || p == null || p.type == NL) return;
        if (p.type == SYM && (" " + CONT + " ").contains(" " + p.text + " ")) return;
        Tok t = tok(NL);
        t.text = "\n";
        push(t);
    }

    private void run() {
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') { newline(); adv(); continue; }
            if (c == ' ' || c == '\t' || c == '\r' || c == ' ' || isSkippable(c)) { adv(); continue; }
            if (c == '#' || (c == '/' && i + 1 < n && src.charAt(i + 1) == '/')) {
                while (i < n && src.charAt(i) != '\n') adv();
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                Tok t = tok(SYM);
                adv(); adv();
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) adv();
                if (i >= n) err(t, "تعليق بدأ بـ /* ولم يُغلق بـ */");
                else { adv(); adv(); }
                continue;
            }
            if (idStart(c)) { ident(); continue; }
            if (digit(c)) { number(); continue; }
            if (c == '"' || c == '«' || c == '“') { string(c == '"' ? '"' : c == '«' ? '»' : '”'); continue; }
            if (c == '\'') { chr(); continue; }
            symbol();
        }
    }

    private void ident() {
        Tok t = tok(ID);
        int s = i;
        while (i < src.length() && idPart(src.charAt(i))) adv();
        String raw = src.substring(s, i);
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < raw.length(); k++) {
            char c = raw.charAt(k);
            if (isSkippable(c)) continue;
            if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            else if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            b.append(c);
        }
        t.text = b.toString();
        t.norm = norm(t.text);
        if (t.norm.equals("جافا")) {
            int k = i;
            while (k < src.length() && (src.charAt(k) == ' ' || src.charAt(k) == '\t' || src.charAt(k) == '\n' || src.charAt(k) == '\r')) k++;
            if (k < src.length() && src.charAt(k) == '{') {
                while (i < k) adv();
                raw(t);
                return;
            }
        }
        push(t);
    }

    /** جافا { ... } : everything between the braces is copied to the output untouched. */
    private void raw(Tok t) {
        adv();
        int s = i, d = 1, n = src.length();
        while (i < n && d > 0) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                adv();
                while (i < n && src.charAt(i) != c && src.charAt(i) != '\n') { if (src.charAt(i) == '\\' && i + 1 < n) adv(); adv(); }
                if (i < n) adv();
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') { while (i < n && src.charAt(i) != '\n') adv(); continue; }
            if (c == '{') d++;
            if (c == '}') d--;
            if (d > 0) adv();
        }
        t.type = RAW;
        t.text = src.substring(s, Math.min(i, n));
        if (i >= n) err(t, "كتلة جافا { لم تُغلق بـ }");
        else adv();
        push(t);
    }

    private void number() {
        Tok t = tok(NUM);
        StringBuilder b = new StringBuilder();
        boolean dot = false, hex = false;
        if (src.charAt(i) == '0' && i + 1 < src.length() && (src.charAt(i + 1) == 'x' || src.charAt(i + 1) == 'X')) {
            hex = true;
            b.append("0x");
            adv(); adv();
        }
        while (i < src.length()) {
            char c = src.charAt(i);
            if (digit(c)) b.append(norm(String.valueOf(c)));
            else if (hex && ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) b.append(c);
            else if (c == '_') { /* 1_000 */ }
            else if (!dot && !hex && (c == '.' || c == '٫') && i + 1 < src.length() && digit(src.charAt(i + 1))) { dot = true; b.append('.'); }
            else break;
            adv();
        }
        String kind = dot ? "float" : "int";
        if (i < src.length() && !hex) {
            char c = src.charAt(i);
            if (c == 'f' || c == 'F') { kind = "float"; adv(); }
            else if (c == 'd' || c == 'D') { kind = "double"; adv(); }
            else if (c == 'L' || c == 'l') { kind = "long"; adv(); }
            else if (c == '٪') { // 30٪ = 0.3
                adv();
                try { b = new StringBuilder(String.valueOf(Double.parseDouble(b.toString()) / 100.0)); } catch (NumberFormatException ignored) { }
                kind = "float";
            }
        }
        if (kind.equals("int") && !hex) {
            try { if (Long.parseLong(b.toString()) > Integer.MAX_VALUE) kind = "long"; } catch (NumberFormatException e) { err(t, "رقم كبير جداً"); }
        }
        t.text = kind.equals("float") ? b + "f" : kind.equals("long") ? b + "L" : b.toString();
        t.val = kind;
        push(t);
    }

    private void chr() {
        Tok t = tok(CHR);
        adv();
        StringBuilder b = new StringBuilder();
        while (i < src.length() && src.charAt(i) != '\'' && src.charAt(i) != '\n') {
            if (src.charAt(i) == '\\' && i + 1 < src.length()) { b.append('\\'); adv(); }
            b.append(src.charAt(i));
            adv();
        }
        if (i < src.length() && src.charAt(i) == '\'') adv();
        else err(t, "الحرف يجب أن يُغلق بعلامة '");
        t.text = b.toString();
        if (t.text.replace("\\", "").length() != 1) { // 'text' with several letters is a string
            t.type = STR;
            List<Object> parts = new ArrayList<>();
            parts.add(t.text);
            t.val = parts;
        }
        push(t);
    }

    private void string(char close) {
        Tok t = tok(STR);
        adv();
        List<Object> parts = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean closed = false;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == close) { adv(); closed = true; break; }
            if (c == '\n') break;
            if (c == '\\' && i + 1 < src.length()) {
                char d = src.charAt(i + 1);
                if (d == '{' || d == '}') b.append(d);
                else b.append('\\').append(d);
                adv(); adv();
                continue;
            }
            if (c == '{') {
                int sl = line, sc = col + 1, s = i + 1, d = 1;
                adv();
                while (i < src.length() && d > 0 && src.charAt(i) != '\n') {
                    if (src.charAt(i) == '{') d++;
                    if (src.charAt(i) == '}') d--;
                    if (d > 0) adv();
                }
                if (d > 0) { err(t, "القوس { داخل النص لم يُغلق. لكتابة القوس نفسه استخدم \\{"); break; }
                String inner = src.substring(s, i);
                adv();
                if (inner.trim().isEmpty()) { b.append("{}"); continue; }
                if (b.length() > 0) { parts.add(b.toString()); b = new StringBuilder(); }
                Lexer sub = new Lexer(inner, sl, sc, diags);
                sub.depth = 1;
                sub.run();
                Tok e = sub.tok(EOF);
                sub.out.add(e);
                parts.add(sub.out);
                continue;
            }
            if (c == '"') b.append('\\');
            b.append(c);
            adv();
        }
        if (!closed) err(t, "النص لم يُغلق بعلامة التنصيص " + close);
        if (b.length() > 0 || parts.isEmpty()) parts.add(b.toString());
        t.val = parts;
        t.text = "\"…\"";
        push(t);
    }

    private static final String[] TWO = {"==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "->"};

    private void symbol() {
        Tok t = tok(SYM);
        char c = src.charAt(i);
        String two = i + 1 < src.length() ? src.substring(i, i + 2) : "";
        for (String s : TWO)
            if (s.equals(two)) {
                adv(); adv();
                t.text = s.equals("->") ? "←" : s;
                push(t);
                return;
            }
        adv();
        switch (c) {
            case '،': t.text = ","; break;
            case '؛': t.text = ";"; break;
            case '؟': t.text = "?"; break;
            case '×': t.text = "*"; break;
            case '≠': t.text = "!="; break;
            case '≤': t.text = "<="; break;
            case '≥': t.text = ">="; break;
            case '−': t.text = "-"; break;
            case '٪': t.text = "%"; break;
            case '٫': t.text = "."; break;
            default: t.text = String.valueOf(c);
        }
        if ("+-*/%÷=<>!()[]{},;:.?←@&|^~".indexOf(t.text.charAt(0)) < 0) {
            err(t, "رمز غير معروف: " + c);
            return;
        }
        if (t.text.equals("(") || t.text.equals("[")) depth++;
        if ((t.text.equals(")") || t.text.equals("]")) && depth > 0) depth--;
        if (t.text.equals(";")) { // ؛ ends a statement like a new line
            Tok p = last();
            if (p != null && p.type != NL) { t.type = NL; push(t); }
            return;
        }
        push(t);
    }
}
