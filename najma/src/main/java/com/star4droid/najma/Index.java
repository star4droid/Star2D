package com.star4droid.najma;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * What the compiler knows about Java classes: libGDX, Box2D, the engine runtime and a part of the JDK
 * (resource najma/index.txt.gz, made by najma/tools/gen_index.py), plus the classes written in Najma.
 * Used for the type of every expression and to find a member by its Arabic name.
 */
final class Index {
    /** A Java type. n = primitive name or class name ($ for nested classes); a = type arguments; d = array dimensions. */
    static final class T {
        final String n;
        final T[] a;
        final int d;
        /** True when the expression names the class itself (static access), not a value. */
        boolean stat;
        static final T[] NONE = new T[0];

        T(String n, T[] a, int d) { this.n = n; this.a = a == null ? NONE : a; this.d = d; }
        static T of(String n) { return new T(n, NONE, 0); }
        static final T INT = of("int"), FLOAT = of("float"), DOUBLE = of("double"), LONG = of("long"), BOOL = of("boolean"), CHAR = of("char"),
                STRING = of("java.lang.String"), VOID = of("void"), OBJECT = of("java.lang.Object"), NULL = of("null");

        boolean is(String name) { return d == 0 && n.equals(name); }
        boolean prim() { return d == 0 && PRIMS.containsKey(n); }
        boolean numeric() { return d == 0 && (n.equals("int") || n.equals("float") || n.equals("double") || n.equals("long") || n.equals("short") || n.equals("byte") || n.equals("char")); }
        boolean integral() { return numeric() && !n.equals("float") && !n.equals("double"); }
        T elem() { return new T(n, a, d - 1); }
        T array() { return new T(n, a, d + 1); }
        T asStatic() { T t = new T(n, a, d); t.stat = true; return t; }
        T boxed() { String b = d == 0 ? PRIMS.get(n) : null; return b == null ? this : of(b); }
        T unboxed() { if (d == 0) for (Map.Entry<String, String> e : PRIMS.entrySet()) if (e.getValue().equals(n)) return of(e.getKey()); return this; }

        String java() {
            StringBuilder b = new StringBuilder(n.replace('$', '.'));
            if (a.length > 0) {
                b.append('<');
                for (int i = 0; i < a.length; i++) b.append(i > 0 ? ", " : "").append(a[i] == null ? "java.lang.Object" : a[i].boxed().java());
                b.append('>');
            }
            for (int i = 0; i < d; i++) b.append("[]");
            return b.toString();
        }
        @Override public String toString() { return java(); }
    }

    static final Map<String, String> PRIMS = new LinkedHashMap<>();
    static {
        PRIMS.put("int", "java.lang.Integer"); PRIMS.put("float", "java.lang.Float"); PRIMS.put("double", "java.lang.Double");
        PRIMS.put("long", "java.lang.Long"); PRIMS.put("boolean", "java.lang.Boolean"); PRIMS.put("char", "java.lang.Character");
        PRIMS.put("short", "java.lang.Short"); PRIMS.put("byte", "java.lang.Byte");
    }

    static final class Member {
        String name, type;
        String[] params;
        boolean stat, method;
        /** Najma classes only: the declaration (its type may still be inferred). */
        Object node;
    }

    static final class Cls {
        String name, sup;
        char kind = 'c';
        String[] tparams = new String[0], ifaces = new String[0];
        final List<Member> members = new ArrayList<>();
        boolean user;
        private Map<String, String> arabic;
        private int from, to;
        private boolean loaded = true;
    }

    private final List<String> lines = new ArrayList<>();
    private final Map<String, Cls> classes = new HashMap<>();
    private final Map<String, String> simple = new HashMap<>();
    private final Data data;
    final Map<String, Cls> user = new HashMap<>();

    private static final String[] PRIORITY = {"java.lang.", "java.util.", "com.star4droid.template.Utils.", "com.star4droid.template.Items.", "com.badlogic.gdx.",
            "com.badlogic.gdx.math.", "com.badlogic.gdx.graphics.", "com.badlogic.gdx.graphics.g2d.", "com.badlogic.gdx.scenes.scene2d.", "com.badlogic.gdx.physics.box2d.",
            "com.badlogic.gdx.utils.", "com.badlogic.gdx.audio.", "com.badlogic.gdx.files.", "com.badlogic.gdx.scenes.scene2d.ui.", "com.badlogic.gdx.graphics.glutils."};

    Index(Data data) {
        this.data = data;
        try (InputStream in = Index.class.getResourceAsStream("/najma/index.txt.gz");
             BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), "UTF-8"))) {
            String s;
            Cls c = null;
            while ((s = r.readLine()) != null) {
                if (s.startsWith("C ")) {
                    if (c != null) c.to = lines.size();
                    c = new Cls();
                    c.loaded = false;
                    c.from = lines.size();
                    int sp = s.indexOf(' ', 2), lt = s.indexOf('<');
                    c.name = s.substring(2, lt > 0 && lt < sp ? lt : sp);
                    classes.put(c.name, c);
                }
                lines.add(s);
            }
            if (c != null) c.to = lines.size();
        } catch (Exception e) {
            throw new IllegalStateException("najma index missing", e);
        }
        for (String fqn : classes.keySet()) {
            String sn = fqn.substring(Math.max(fqn.lastIndexOf('.'), fqn.lastIndexOf('$')) + 1);
            String old = simple.get(sn);
            if (old == null || rank(fqn) < rank(old)) simple.put(sn, fqn);
        }
    }

    private static int rank(String fqn) {
        String pkg = fqn.substring(0, fqn.lastIndexOf('.') + 1);
        for (int i = 0; i < PRIORITY.length; i++) if (pkg.equals(PRIORITY[i]) && fqn.indexOf('$') < 0) return i;
        return PRIORITY.length + (fqn.indexOf('$') >= 0 ? 50 : 0) + (fqn.startsWith("com.badlogic") ? 0 : 10);
    }

    /** Full name of a class written by its simple Latin name (Vector2), or null. */
    String bySimple(String name) { return simple.get(name); }

    Cls get(String name) {
        Cls u = user.get(name);
        if (u != null) return u;
        Cls c = classes.get(name);
        if (c == null && name.indexOf('.') > 0) { // Outer.Inner written with dots
            int k = name.lastIndexOf('.');
            String alt = name.substring(0, k) + "$" + name.substring(k + 1);
            c = classes.get(alt);
        }
        if (c != null && !c.loaded) load(c);
        return c;
    }

    private void load(Cls c) {
        c.loaded = true;
        String[] h = lines.get(c.from).split(" ");
        int lt = h[1].indexOf('<');
        if (lt > 0) c.tparams = h[1].substring(lt + 1, h[1].length() - 1).split(",");
        c.kind = h[2].charAt(0);
        c.sup = h[3].equals("-") ? (c.kind == 'i' || c.name.equals("java.lang.Object") ? null : "java.lang.Object") : h[3];
        c.ifaces = h[4].equals("-") ? new String[0] : splitTop(h[4], ',');
        for (int i = c.from + 1; i < c.to; i++) {
            String[] p = lines.get(i).split(" ");
            Member m = new Member();
            m.name = p[1];
            m.type = p[2];
            m.method = p[0].equals("M");
            if (m.method) {
                String ps = p[3].substring(1, p[3].length() - 1);
                m.params = ps.isEmpty() ? new String[0] : ps.split(";");
                m.stat = p.length > 4;
            } else m.stat = p.length > 3;
            c.members.add(m);
        }
    }

    static String[] splitTop(String s, char sep) {
        List<String> r = new ArrayList<>();
        int d = 0, st = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '<') d++;
            else if (ch == '>') d--;
            else if (ch == sep && d == 0) { r.add(s.substring(st, i)); st = i + 1; }
        }
        r.add(s.substring(st));
        return r.toArray(new String[0]);
    }

    /** "java.util.List<java.lang.String>[]" to a T. Type variables are replaced using the receiver (c + recv), unknown ones become null. */
    T parse(String s, Cls c, T recv) {
        if (s == null || s.equals("?") || s.equals("<init>")) return null;
        int d = 0;
        while (s.endsWith("[]")) { d++; s = s.substring(0, s.length() - 2); }
        int lt = s.indexOf('<');
        String name = lt < 0 ? s : s.substring(0, lt);
        if (c != null)
            for (int i = 0; i < c.tparams.length; i++)
                if (c.tparams[i].equals(name)) {
                    T v = recv != null && i < recv.a.length ? recv.a[i] : null;
                    return v == null ? (d > 0 ? new T("java.lang.Object", null, d) : null) : new T(v.n, v.a, v.d + d);
                }
        if (name.indexOf('.') < 0 && !PRIMS.containsKey(name) && !name.equals("void") && !user.containsKey(name))
            return d > 0 ? new T("java.lang.Object", null, d) : null; // a method type variable
        T[] args = T.NONE;
        if (lt > 0) {
            String[] as = splitTop(s.substring(lt + 1, s.length() - 1), ',');
            args = new T[as.length];
            for (int i = 0; i < as.length; i++) args[i] = parse(as[i], c, recv);
        }
        return new T(name, args, d);
    }

    interface Visit { boolean at(Cls c, T as); }

    /** Walks t, its super classes and interfaces (each with its type arguments filled in). Stops when the visitor returns true. */
    boolean walk(T t, Visit v) { return walk(t, v, 0); }

    private boolean walk(T t, Visit v, int depth) {
        if (t == null || depth > 12) return false;
        if (t.d > 0 || t.prim()) return false;
        Cls c = get(t.n);
        if (c == null) return false;
        if (v.at(c, t)) return true;
        if (c.sup != null && walk(parse(c.sup, c, t), v, depth + 1)) return true;
        for (String i : c.ifaces) if (walk(parse(i, c, t), v, depth + 1)) return true;
        if (c.kind == 'i' && c.sup == null && !c.name.equals("java.lang.Object")) return walk(T.OBJECT, v, depth + 1);
        return false;
    }

    /** t seen as its super type "sup" (with type arguments), or null when t is not one. */
    T asSuper(T t, final String sup) {
        final T[] r = new T[1];
        walk(t, new Visit() { public boolean at(Cls c, T as) { if (c.name.equals(sup)) { r[0] = as; return true; } return false; } });
        return r[0];
    }

    boolean isA(T t, String sup) { return t != null && (t.is(sup) || asSuper(t, sup) != null); }

    static final class Found {
        Member m;
        Cls c;
        T owner;
        T type(Index ix) { return m.node != null ? ix.userType(m) : ix.parse(m.type, c, owner); }
        T param(Index ix, int i) { return m.params == null || i >= m.params.length ? null : ix.parse(m.params[i], c, owner); }
    }

    /** Najma members get their type from the generator (inferred types). */
    interface UserTypes { T typeOf(Member m); }
    UserTypes userTypes;
    T userType(Member m) { return userTypes == null ? null : userTypes.typeOf(m); }

    /** A field (argc < 0) or a method with argc arguments, by its JAVA name, searched up the hierarchy. */
    Found find(T recv, final String name, final int argc) {
        final Found f = new Found();
        walk(recv, new Visit() {
            public boolean at(Cls c, T as) {
                Member loose = null;
                for (Member m : c.members) {
                    if (!m.name.equals(name) || m.method != (argc >= 0)) continue;
                    if (!m.method || m.params.length == argc) { f.m = m; f.c = c; f.owner = as; return true; }
                    if (loose == null) loose = m;
                }
                if (loose != null && f.m == null) { f.m = loose; f.c = c; f.owner = as; }
                return false;
            }
        });
        return f.m == null ? null : f;
    }

    /** Every method called name with argc arguments, nearest class first (overloads). */
    List<Found> findAll(T recv, final String name, final int argc) {
        final List<Found> all = new ArrayList<>();
        walk(recv, new Visit() {
            public boolean at(Cls c, T as) {
                for (Member m : c.members)
                    if (m.method && m.name.equals(name) && m.params.length == argc) {
                        Found f = new Found();
                        f.m = m; f.c = c; f.owner = as;
                        all.add(f);
                    }
                return false;
            }
        });
        return all;
    }

    /** Java name of the member of recv called "ar" in Arabic (or already a Java name). method: 1 method, 0 field, -1 either. */
    String javaName(T recv, final String ar, final int method) {
        final String[] r = new String[1];
        walk(recv, new Visit() {
            public boolean at(Cls c, T as) {
                if (c.arabic == null) {
                    c.arabic = new HashMap<>();
                    List<Member> ordered = new ArrayList<>();
                    for (Member m : c.members) if (!m.stat) ordered.add(m); // «س» is the field x before the constant X
                    for (Member m : c.members) if (m.stat) ordered.add(m);
                    for (Member m : ordered) {
                        if (m.name.equals("<init>")) continue;
                        String key = (m.method ? "m" : "f");
                        if (c.user) { c.arabic.put(key + Data.canon(m.name), m.name); continue; }
                        for (String al : data.arabic(m.name, m.method && m.params.length == 0, m.method && m.params.length > 0))
                            if (!c.arabic.containsKey(key + al)) c.arabic.put(key + al, m.name);
                    }
                }
                for (String name : Data.variants(ar)) {
                    if (method != 0) { String j = c.arabic.get("m" + name); if (j != null) { r[0] = j; return true; } }
                    if (method != 1) { String j = c.arabic.get("f" + name); if (j != null) { r[0] = j; return true; } }
                }
                if (method != 0) for (Member m : c.members) if (m.method && m.name.equals(ar)) { r[0] = ar; return true; }
                if (method != 1) for (Member m : c.members) if (!m.method && m.name.equals(ar)) { r[0] = ar; return true; }
                return false;
            }
        });
        return r[0];
    }

    /** Every member reachable from t with its Arabic name (completion lists). */
    List<String[]> membersOf(T t) {
        final List<String[]> out = new ArrayList<>();
        final java.util.HashSet<String> seen = new java.util.HashSet<>();
        walk(t, new Visit() {
            public boolean at(Cls c, T as) {
                if (c.name.equals("java.lang.Object")) return false;
                for (Member m : c.members) {
                    if (m.name.equals("<init>")) continue;
                    List<String> ar = c.user ? java.util.Collections.singletonList(m.name) : data.arabic(m.name, m.method && m.params.length == 0, m.method && m.params.length > 0);
                    String label = ar.isEmpty() ? m.name : ar.get(0);
                    if (seen.add(label + (m.method ? "(" + m.params.length : ""))) out.add(new String[]{label, m.name, m.method ? String.valueOf(m.params.length) : "", m.type == null ? "" : m.type});
                }
                return false;
            }
        });
        return out;
    }

    /** The single abstract method of an interface (for a function written with the arrow). */
    Found sam(T t) {
        final Found f = new Found();
        final int[] count = {0};
        walk(t, new Visit() {
            public boolean at(Cls c, T as) {
                if (c.kind != 'i' && !c.user) return c.name.equals("java.lang.Object");
                for (Member m : c.members)
                    if (m.method && !m.stat && !m.name.equals("equals") && !m.name.equals("hashCode") && !m.name.equals("toString") && (c.kind == 'i' || c.user)) {
                        if (f.m == null || !f.m.name.equals(m.name)) count[0]++;
                        if (f.m == null) { f.m = m; f.c = c; f.owner = as; }
                    }
                return false;
            }
        });
        Cls c = t == null ? null : get(t.n);
        if (c != null && c.kind == 'c' && !c.user) { // abstract class with one abstract method is not visible in the index: common ones
            if (t.n.equals("com.badlogic.gdx.utils.Timer$Task")) { Member m = new Member(); m.name = "run"; m.type = "void"; m.params = new String[0]; m.method = true; f.m = m; f.c = c; f.owner = t; return f; }
            return null;
        }
        return count[0] == 1 ? f : null;
    }
}
