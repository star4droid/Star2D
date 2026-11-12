package com.star4droid.najma;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The vocabulary of the language, read from the resource najma/lang.txt: Arabic class names, ready functions,
 * properties («س اللاعب»), sentences («حرك اللاعب يمينا بسرعة 200»), events and the word dictionary that gives
 * every libGDX / engine member an Arabic name. Adding a line to that file adds a word to the language.
 */
final class Data {
    static final class Tmpl {
        /** Literal words (normalised); null = a value goes here. */
        String[] parts;
        String java, shown;
    }

    static final class Fn {
        String name, java, type;
        int arity;
        Map<Integer, String> argTypes = new HashMap<>();
        String shown;
    }

    static final class Prop {
        String name, recv, getter, setter, type;
    }

    static final class Global {
        String name, java, type;
    }

    static final class Event {
        String kind, phrase, header, shown;
        Map<String, String> vars = new LinkedHashMap<>();
    }

    private final Map<String, String> classAlias = new LinkedHashMap<>();
    private final Map<String, List<Tmpl>> sentences = new LinkedHashMap<>();
    private final Map<String, List<Fn>> functions = new LinkedHashMap<>();
    private final Map<String, List<Prop>> props = new LinkedHashMap<>();
    private final Map<String, Global> globals = new LinkedHashMap<>();
    final List<Event> events = new ArrayList<>();
    private final Map<String, String> words = new HashMap<>();
    private final Map<String, String> prefixes = new HashMap<>();
    private final Map<String, List<String>> memberNames = new HashMap<>();
    private final Map<String, List<String>> arabicCache = new HashMap<>();
    final Map<String, String> classShown = new LinkedHashMap<>();

    private static Data instance;

    static synchronized Data get() {
        if (instance == null) instance = new Data();
        return instance;
    }

    private Data() {
        try (InputStream in = Data.class.getResourceAsStream("/najma/lang.txt"); BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String s, section = "";
            while ((s = r.readLine()) != null) {
                s = s.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                if (s.startsWith("[") && s.endsWith("]")) { section = s.substring(1, s.length() - 1); continue; }
                int eq = s.indexOf(" = ");
                if (eq < 0) continue;
                add(section, s.substring(0, eq).trim(), s.substring(eq + 3).trim());
            }
        } catch (Exception e) {
            throw new IllegalStateException("najma lang.txt missing or broken", e);
        }
    }

    private static String[] names(String s) {
        String[] ns = s.split("\\|");
        for (int i = 0; i < ns.length; i++) ns[i] = ns[i].trim();
        return ns;
    }

    private void add(String section, String left, String right) {
        switch (section) {
            case "classes":
                for (String n : names(left)) { classAlias.put(key(n), right); if (!classShown.containsKey(right)) classShown.put(right, n); }
                break;
            case "globals": {
                int c = right.lastIndexOf(" : ");
                for (String n : names(left)) {
                    Global g = new Global();
                    g.name = n;
                    g.java = c < 0 ? right : right.substring(0, c);
                    g.type = c < 0 ? null : right.substring(c + 3).trim();
                    globals.put(key(n), g);
                }
                break;
            }
            case "functions": {
                int par = left.lastIndexOf('(');
                int arity = Integer.parseInt(left.substring(par + 1, left.indexOf(')', par)).trim());
                String[] at = right.split(" @");
                int c = at[0].lastIndexOf(" : ");
                for (String n : names(left.substring(0, par))) {
                    Fn f = new Fn();
                    f.name = key(n);
                    f.shown = n;
                    f.arity = arity;
                    f.java = c < 0 ? at[0] : at[0].substring(0, c);
                    f.type = c < 0 ? "void" : at[0].substring(c + 3).trim();
                    for (int i = 1; i < at.length; i++) { String[] kv = at[i].trim().split(" ", 2); f.argTypes.put(Integer.parseInt(kv[0]), kv[1].trim()); }
                    List<Fn> l = functions.get(f.name);
                    if (l == null) functions.put(f.name, l = new ArrayList<>());
                    l.add(f);
                }
                break;
            }
            case "props": {
                int c = left.indexOf(" : ");
                int tc = right.lastIndexOf(" : ");
                String[] gs = right.substring(0, tc).split(" ; ");
                for (String n : names(left.substring(0, c))) {
                    Prop pr = new Prop();
                    pr.name = key(n);
                    pr.recv = left.substring(c + 3).trim();
                    pr.getter = gs[0].trim();
                    pr.setter = gs.length > 1 ? gs[1].trim() : null;
                    pr.type = right.substring(tc + 3).trim();
                    List<Prop> l = props.get(pr.name);
                    if (l == null) props.put(pr.name, l = new ArrayList<>());
                    l.add(pr);
                }
                break;
            }
            case "sentences": {
                Tmpl t = new Tmpl();
                t.shown = left;
                t.java = right;
                String[] ws = left.split("\\s+");
                t.parts = new String[ws.length];
                for (int i = 0; i < ws.length; i++) t.parts[i] = ws[i].startsWith("{") ? null : key(ws[i]);
                List<Tmpl> l = sentences.get(t.parts[0]);
                if (l == null) sentences.put(t.parts[0], l = new ArrayList<>());
                l.add(t);
                // most specific first: more literal words, then longer
                Collections.sort(l, (x, y) -> lits(y) != lits(x) ? lits(y) - lits(x) : y.parts.length - x.parts.length);
                break;
            }
            case "events": {
                int bar = left.indexOf('|');
                String[] rv = right.split(" \\| ");
                for (String ph : names(left.substring(bar + 1))) {
                    Event e = new Event();
                    e.kind = left.substring(0, bar).trim();
                    e.shown = ph;
                    e.phrase = canonPhrase(Lexer.norm(ph));
                    e.header = rv[0].trim();
                    for (int i = 1; i < rv.length; i++) { String[] kv = rv[i].trim().split(":", 2); e.vars.put(Lexer.norm(kv[0].trim()), kv[1].trim()); }
                    events.add(e);
                }
                break;
            }
            case "words": words.put(left.toLowerCase(), key(right)); break;
            case "prefixes": prefixes.put(left, right.equals("-") ? "" : key(right)); break;
            case "members": {
                List<String> l = new ArrayList<>();
                for (String n : names(right)) l.add(key(n));
                memberNames.put(left, l);
                break;
            }
            default:
        }
    }

    private static int lits(Tmpl t) { int n = 0; for (String p : t.parts) if (p != null) n++; return n; }

    // ------------------------------------------------------------------ lookups
    static boolean same(String a, String b) { return a.equals(b) || canon(a).equals(canon(b)); }

    private static String key(String written) { return canon(Lexer.norm(written.trim())); }

    /** Comparison form of a name: «ال» dropped from every part, so اللاعب = لاعب and اجعل_الموضع = اجعل_موضع. */
    static String canon(String n) {
        if (n.isEmpty() || (n.indexOf("ال") < 0)) return n;
        StringBuilder b = new StringBuilder(n.length());
        int st = 0;
        while (st <= n.length()) {
            int us = n.indexOf('_', st);
            if (us < 0) us = n.length();
            String seg = n.substring(st, us);
            if (seg.startsWith("ال") && seg.length() > 3) seg = seg.substring(2);
            if (b.length() > 0 || st > 0) b.append('_');
            b.append(seg);
            st = us + 1;
        }
        return b.toString();
    }

    static String canonPhrase(String p) {
        StringBuilder b = new StringBuilder();
        for (String w : p.split(" ")) b.append(b.length() > 0 ? " " : "").append(canon(w));
        return b.toString();
    }

    /** The name as written, without «ال», with «ال». */
    static List<String> variants(String n) {
        return Collections.singletonList(canon(n));
    }

    String classAlias(String norm) {
        for (String v : variants(norm)) { String c = classAlias.get(v); if (c != null) return c; }
        return null;
    }

    List<Tmpl> sentences(String firstWord) {
        for (String v : variants(firstWord)) { List<Tmpl> l = sentences.get(v); if (l != null) return l; }
        return null;
    }

    List<Fn> functions(String norm) {
        for (String v : variants(norm)) { List<Fn> l = functions.get(v); if (l != null) return l; }
        return null;
    }

    List<Prop> props(String norm) {
        for (String v : variants(norm)) { List<Prop> l = props.get(v); if (l != null) return l; }
        return null;
    }

    Global global(String norm) {
        for (String v : variants(norm)) { Global g = globals.get(v); if (g != null) return g; }
        return null;
    }

    Event event(String kind, String phrase) {
        for (Event e : events) if (e.kind.equals(kind) && e.phrase.equals(phrase)) return e;
        return null;
    }

    Map<String, List<Fn>> allFunctions() { return functions; }
    Map<String, List<Tmpl>> allSentences() { return sentences; }
    Map<String, List<Prop>> allProps() { return props; }
    Map<String, Global> allGlobals() { return globals; }
    Map<String, String> allClasses() { return classAlias; }

    /** Arabic names of a Java member (setPosition gives اجعل_الموضع). Empty when a word of the name has no translation. */
    List<String> arabic(String javaName, boolean getter, boolean hasArgs) {
        String key = javaName + (getter ? "g" : hasArgs ? "a" : "f");
        List<String> r = arabicCache.get(key);
        if (r != null) return r;
        r = new ArrayList<>();
        List<String> fixed = memberNames.get(javaName);
        if (fixed != null) r.addAll(fixed);
        List<String> ws = splitCamel(javaName);
        if (!ws.isEmpty()) {
            String pre = null;
            if (ws.size() > 1 && prefixes.containsKey(ws.get(0))) { pre = prefixes.get(ws.get(0)); ws = ws.subList(1, ws.size()); }
            StringBuilder b = new StringBuilder();
            boolean ok = true;
            for (String w : ws) {
                String a = w.matches("\\d+") ? w : words.get(w.toLowerCase());
                if (a == null) { ok = false; break; }
                if (b.length() > 0) b.append('_');
                b.append(a);
            }
            if (ok) {
                String base = b.toString();
                if (pre == null || pre.isEmpty()) add(r, base);
                else {
                    add(r, pre + "_" + base);
                    if (pre.equals("هل")) add(r, base);
                }
            }
        }
        arabicCache.put(key, r);
        return r;
    }

    private static void add(List<String> l, String s) { if (!l.contains(s)) l.add(s); }

    static List<String> splitCamel(String n) {
        List<String> ws = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            boolean brk = false;
            if (c == '_') { if (b.length() > 0) { ws.add(b.toString()); b = new StringBuilder(); } continue; }
            if (b.length() > 0) {
                char p = n.charAt(i - 1);
                if (Character.isUpperCase(c) && !Character.isUpperCase(p)) brk = true;
                else if (Character.isUpperCase(c) && i + 1 < n.length() && Character.isLowerCase(n.charAt(i + 1))) brk = true;
                else if (Character.isDigit(c) != Character.isDigit(p)) brk = true;
            }
            if (brk) { ws.add(b.toString()); b = new StringBuilder(); }
            b.append(c);
        }
        if (b.length() > 0) ws.add(b.toString());
        return ws;
    }

    // ------------------------------------------------------------------ suggestions
    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++)
                cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1, prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** The closest of the candidates to the name (typing mistakes), or null. */
    static String closest(String name, Iterable<String> candidates) {
        String best = null;
        int bd = Math.max(1, name.length() / 3) + 1;
        String bare = name.startsWith("ال") ? name.substring(2) : name;
        for (String c : candidates) {
            String cb = c.startsWith("ال") ? c.substring(2) : c;
            int d = distance(bare, cb);
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    String suggestSentence(Lexer.Tok t) {
        if (t.type != Lexer.ID) return null;
        List<Tmpl> l = sentences(t.norm);
        if (l == null) { String c = closest(canon(t.norm), sentences.keySet()); if (c != null) l = sentences.get(c); }
        return l == null ? null : l.get(0).shown;
    }
}
