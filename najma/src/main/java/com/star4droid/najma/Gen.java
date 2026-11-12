package com.star4droid.najma;

import com.star4droid.najma.Index.Cls;
import com.star4droid.najma.Index.Found;
import com.star4droid.najma.Index.Member;
import com.star4droid.najma.Index.T;
import com.star4droid.najma.Parser.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tree to Java source (Java 7 level, what the on-phone compiler accepts). The output keeps the LINE of every
 * statement: line N of the .njm file is line N of the .java file, so compiler errors and crash traces point at
 * the right line without a source map. It runs several times: the first runs only learn types (inferred
 * variables, return types, which locals are captured by a timer or a function), the last one reports errors.
 */
final class Gen {
    static final class Var {
        String java, name;
        T type;
        Node decl;
        int level;
        boolean boxed;
    }

    static final class Ctx {
        String name;
        T self;
        boolean anon;
    }

    private static final String N = "najma.N";
    private static final String ITEM = "com.star4droid.template.Utils.PlayerItem";

    private final Najma.Options opt;
    private final Data data;
    private final Index ix;
    final List<Najma.Diag> diags = new ArrayList<>();
    boolean quiet = true, changed;

    // learned across the runs
    private final IdentityHashMap<Node, T> nodeType = new IdentityHashMap<>();
    private final Set<Node> reassigned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
    private final Set<Node> captured = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
    private final Map<String, String> imports = new HashMap<>();
    private final List<String> importLines = new ArrayList<>();

    /** Every variable seen (name to Java type): member suggestions in the editor. */
    final Map<String, String> names = new java.util.LinkedHashMap<>();

    private StringBuilder sb;
    private int line, closure, tmp;
    private final ArrayList<HashMap<String, Var>> scopes = new ArrayList<>();
    private final ArrayList<Ctx> classes = new ArrayList<>();
    private Node method;
    private T lastT;
    private boolean stageError;

    Gen(Najma.Options opt, Data data, Index ix) {
        this.opt = opt;
        this.data = data;
        this.ix = ix;
    }

    // ------------------------------------------------------------------ output
    private void w(String s) {
        sb.append(s);
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') line++;
    }

    private void at(int srcLine) {
        while (line < srcLine) { sb.append('\n'); line++; }
    }

    private void at(Node n) { at(n.line()); }

    private void err(Node n, String msg) { err(n.t, msg); }

    private void err(Lexer.Tok t, String msg) {
        if (quiet) return;
        for (Najma.Diag d : diags) if (d.line == t.line && d.col == t.col) return;
        diags.add(new Najma.Diag(t.line, t.col, Math.max(t.end, t.col + 1), msg));
    }

    private String str(Node e, T expected) {
        StringBuilder save = sb;
        int sl = line;
        sb = new StringBuilder();
        lastT = expr(e, expected);
        String s = sb.toString();
        sb = save;
        line = sl;
        return s;
    }

    private void learn(Node n, T t) {
        if (t == null || t == T.NULL) return;
        T old = nodeType.get(n);
        if (old != null && old.numeric() && t.numeric()) t = promote(old, t);
        if (old == null || !old.java().equals(t.java())) { nodeType.put(n, t); changed = true; }
    }

    // ------------------------------------------------------------------ scopes
    private void push() { scopes.add(new HashMap<String, Var>()); }
    private void pop() { scopes.remove(scopes.size() - 1); }

    private Var declare(Node decl, String name, T type) {
        Var v = new Var();
        v.java = name;
        v.type = type;
        v.decl = decl;
        v.level = closure;
        v.boxed = captured.contains(decl) && reassigned.contains(decl);
        v.name = name;
        if (type != null) names.put(name, sig(type));
        scopes.get(scopes.size() - 1).put(name, v);
        return v;
    }

    /** The exact name first; then a field with the exact name wins; then names that differ only by «ال». */
    private Var lookup(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Var x = scopes.get(i).get(name);
            if (x != null) return x;
        }
        for (int i = classes.size() - 1; i >= 0; i--) {
            Cls c = ix.user.get(classes.get(i).self.n);
            if (c != null) for (Member m : c.members) if (!m.method && m.name.equals(name)) return null;
        }
        String cn = Data.canon(name);
        for (int i = scopes.size() - 1; i >= 0; i--)
            for (Var x : scopes.get(i).values()) if (Data.canon(x.name).equals(cn)) return x;
        return null;
    }

    private static int rank(T t) {
        switch (t.n) { case "byte": return 1; case "short": case "char": return 2; case "int": return 3; case "long": return 4; case "float": return 5; case "double": return 6; default: return 0; }
    }

    private int fit(T p, T a) {
        if (p == null || a == null) return 1;
        if (p.d == 0 && p.numeric()) {
            T u = a.unboxed();
            if (a.d > 0 || !u.numeric()) return -100;
            return rank(u) == rank(p) ? 4 : rank(u) < rank(p) ? 3 : -100;
        }
        if (p.is("boolean")) return a.unboxed().is("boolean") ? 4 : -100;
        if (a == T.NULL) return 2;
        if (a.d != p.d) return p.is("java.lang.Object") ? 1 : -100;
        if (a.prim()) return a.boxed().n.equals(p.n) ? 3 : p.is("java.lang.Object") || p.is("java.lang.Number") ? 1 : -100;
        if (a.n.equals(p.n)) return 4;
        if (ix.get(a.n) == null || ix.get(p.n) == null) return 1;
        if (ix.isA(a, p.n)) return 3;
        return p.is("java.lang.Object") ? 1 : -100;
    }

    /** The overload of jn that fits the arguments best (their types come from a silent trial generation). */
    private Found pick(T recv, String jn, List<Node> as) {
        List<Found> all = ix.findAll(recv, jn, as.size());
        if (all.size() <= 1 || as.isEmpty()) return ix.find(recv, jn, as.size());
        boolean q = quiet;
        quiet = true;
        T[] at = new T[as.size()];
        for (int i = 0; i < at.length; i++) {
            if (as.get(i).k.equals("lambda")) continue;
            str(as.get(i), null);
            at[i] = lastT;
        }
        quiet = q;
        Found best = all.get(0);
        int bs = Integer.MIN_VALUE;
        for (Found f : all) {
            int s = 0;
            for (int i = 0; i < at.length; i++) s += fit(f.param(ix, i), at[i]);
            if (s > bs) { bs = s; best = f; }
        }
        return best;
    }

    private Ctx cls() { return classes.get(classes.size() - 1); }

    private String stage(Node at) {
        if ("item".equals(opt.kind)) return "stage";
        if ("scene".equals(opt.kind)) return "getStage()";
        if (!stageError) { stageError = true; err(at, "هذا الأمر يعمل داخل سكربت عنصر أو سكربت مشهد فقط"); }
        return "null";
    }

    // ------------------------------------------------------------------ types
    private static final Map<String, String> BUILTIN = new HashMap<>();
    static {
        String[] m = {"رقم_صحيح", "int", "رقم_طويل", "long", "رقم_قصير", "short", "بايت", "byte", "رقم_عشري", "float", "رقم", "float", "عشري_دقيق", "double",
                "منطقي", "boolean", "حرف", "char", "نص", "java.lang.String", "لا_ناتج", "void", "كائن", "java.lang.Object", "int", "int", "long", "long",
                "short", "short", "byte", "byte", "float", "float", "double", "double", "boolean", "boolean", "char", "char", "void", "void",
                "قائمة", "java.util.ArrayList", "قائمة_مرتبطة", "java.util.LinkedList", "قاموس", "java.util.HashMap", "قاموس_مرتب", "java.util.TreeMap",
                "مجموعة", "java.util.HashSet", "طابور", "java.util.ArrayDeque", "مكدس", "java.util.ArrayDeque", "قائمة_سريعة", "com.badlogic.gdx.utils.Array",
                "قاموس_سريع", "com.badlogic.gdx.utils.ObjectMap"};
        for (int i = 0; i < m.length; i += 2) BUILTIN.put(m[i], m[i + 1]);
    }

    private String className(String name, boolean latinOk) {
        String cn0 = Data.canon(name);
        for (String u : ix.user.keySet()) if (Data.canon(u).equals(cn0) && !u.equals(opt.className)) return u;
        String imp = imports.get(name);
        if (imp == null) imp = imports.get(Data.canon(name));
        if (imp != null) return imp;
        String al = data.classAlias(name);
        if (al != null) return al;
        if (!name.isEmpty() && name.charAt(0) < 0x80) {
            if (name.indexOf('.') > 0) { Cls c = ix.get(name); return c != null ? c.name : name; }
            String fq = ix.bySimple(name);
            if (fq != null) return fq;
            if (latinOk) return name;
        }
        return null;
    }

    private T type(Node n) {
        if (n == null) return null;
        int dims = n.x instanceof Integer ? (Integer) n.x : 0;
        String s = n.s;
        if (s.equals("مصفوفة")) {
            T el = n.list.isEmpty() ? T.OBJECT : type(n.list.get(0));
            return el == null ? null : new T(el.n, el.a, el.d + 1 + dims);
        }
        String b = BUILTIN.get(s);
        String name = b != null ? b : className(s, true);
        if (name == null) {
            String sug = Data.closest(s, BUILTIN.keySet());
            if (sug == null) sug = Data.closest(s, data.allClasses().keySet());
            err(n, "لا أعرف نوعاً اسمه «" + n.t.text + "»" + (sug != null ? "، هل تقصد «" + sug + "»؟" : ""));
            return null;
        }
        T[] args = new T[n.list.size()];
        for (int i = 0; i < args.length; i++) { T a = type(n.list.get(i)); args[i] = a == null ? T.OBJECT : a.boxed(); }
        if (args.length == 0 && b != null && b.startsWith("java.util.")) args = b.contains("Map") ? new T[]{T.OBJECT, T.OBJECT} : new T[]{T.OBJECT};
        if (args.length == 0 && b != null && b.endsWith(".Array")) args = new T[]{T.OBJECT};
        if (args.length == 0 && b != null && b.endsWith(".ObjectMap")) args = new T[]{T.OBJECT, T.OBJECT};
        return new T(name, args, dims);
    }

    private static T promote(T a, T b) {
        if (a == null || b == null) return null;
        if (a.is("double") || b.is("double")) return T.DOUBLE;
        if (a.is("float") || b.is("float")) return T.FLOAT;
        if (a.is("long") || b.is("long")) return T.LONG;
        return T.INT;
    }

    private T elemOf(T t) {
        if (t == null) return null;
        if (t.d > 0) return t.elem();
        if (t.is("java.lang.String")) return T.CHAR;
        T m = ix.asSuper(t, "java.util.Map");
        if (m != null) return m.a.length > 0 ? m.a[0] : null;
        T it = ix.asSuper(t, "java.lang.Iterable");
        return it != null && it.a.length > 0 ? it.a[0] : null;
    }

    private static String sig(T t) {
        if (t == null) return "java.lang.Object";
        StringBuilder b = new StringBuilder(t.n);
        if (t.a.length > 0) {
            b.append('<');
            for (int i = 0; i < t.a.length; i++) b.append(i > 0 ? "," : "").append(sig(t.a[i] == null ? null : t.a[i].boxed()));
            b.append('>');
        }
        for (int i = 0; i < t.d; i++) b.append("[]");
        return b.toString();
    }

    /** A type written in lang.txt: int, PlayerItem, T1 (type of value 1), E1 (type of its elements). */
    private T typeSpec(String spec, T[] argT) {
        if (spec == null || spec.equals("?")) return null;
        if (spec.equals("void")) return T.VOID;
        if (argT != null)
            for (int i = argT.length; i >= 1; i--) {
                T t = argT[i - 1];
                if (spec.equals("T" + i)) return t;
                if (spec.equals("E" + i)) return elemOf(t);
                if (spec.equals("N" + i)) return t != null && t.numeric() ? (t.is("int") || t.is("long") || t.is("double") ? t : T.FLOAT) : T.FLOAT;
                T el = elemOf(t);
                spec = spec.replace("E" + i, sig(el == null ? null : el.boxed())).replace("T" + i, sig(t == null ? null : t.boxed()));
            }
        return ix.parse(expand(spec), null, null);
    }

    private String expand(String spec) {
        if (spec.equals("String")) return "java.lang.String";
        int lt = spec.indexOf('<');
        String head = lt < 0 ? spec : spec.substring(0, lt);
        String arr = "";
        while (head.endsWith("[]")) { head = head.substring(0, head.length() - 2); arr += "[]"; }
        if (head.indexOf('.') < 0 && !Index.PRIMS.containsKey(head)) {
            String fq = head.equals("String") ? "java.lang.String" : ix.bySimple(head);
            if (fq != null) head = fq;
        }
        return head + (lt < 0 ? "" : spec.substring(lt)) + arr;
    }

    private void coerce(int mark, T want, T got) {
        if (want == null || got == null) return;
        if ((want.is("float") && got.is("double")) || (want.is("int") && got.is("long"))) {
            sb.insert(mark, "(" + want.n + ")(");
            sb.append(')');
        }
    }

    // ------------------------------------------------------------------ file
    String file(Node f) {
        sb = new StringBuilder();
        line = 1;
        tmp = 0;
        closure = 0;
        scopes.clear();
        classes.clear();
        stageError = false;
        imports.clear();
        importLines.clear();
        ix.user.clear();
        ix.userTypes = new Index.UserTypes() {
            public T typeOf(Member m) {
                Node n = (Node) m.node;
                T t = nodeType.get(n);
                if (t != null) return t;
                if (n.k.equals("method") && n.a == null) return T.VOID;
                boolean q = quiet;
                quiet = true;
                try { return n.a != null ? type(n.a) : null; } finally { quiet = q; }
            }
        };
        List<Node> loose = new ArrayList<>(), types = new ArrayList<>();
        for (Node m : f.list) {
            if (m.k.equals("import")) {
                String last = m.s.substring(m.s.lastIndexOf('.') + 1);
                if (!last.equals("*")) { imports.put(last, m.s); if (m.x != null) imports.put(Data.canon((String) m.x), m.s); }
                else importLines.add("import " + m.s + ";");
            } else if (m.k.equals("class") || m.k.equals("enum")) types.add(m);
            else loose.add(m);
        }
        boolean wrap = !"plain".equals(opt.kind) || !loose.isEmpty() || types.isEmpty();
        for (Node t : types) register(t, null);
        Cls main = null;
        if (wrap) {
            main = new Cls();
            main.name = opt.className;
            main.user = true;
            main.sup = opt.baseType != null ? opt.baseType : "java.lang.Object";
            addMembers(main, f.list);
            ix.user.put(main.name, main);
        }
        if (opt.pkg != null && !opt.pkg.isEmpty()) w("package " + opt.pkg + "; ");
        for (String il : importLines) w(il + " ");
        if (wrap) {
            w("public class " + opt.className + (opt.superClass != null ? " extends " + opt.superClass : "") + " { ");
            Ctx c = new Ctx();
            c.name = opt.className;
            c.self = T.of(main.name);
            classes.add(c);
            push();
            for (Node m : f.list) member(m, true);
            pop();
            classes.remove(c);
            if ("scene".equals(opt.kind)) { // a scene script must have all four (abstract in SceneScript): add the ones not written
                java.util.Set<String> have = new java.util.HashSet<>();
                for (Node m : f.list) if (m.k.equals("event")) { Data.Event ev = data.event("scene", Data.canonPhrase(m.s)); if (ev != null) have.add(ev.header); }
                for (String h : new String[]{"create", "draw", "pause", "resume"}) if (!have.contains("public void " + h + "()")) w(" public void " + h + "() { }");
            }
            w("\n}\n");
        } else {
            for (Node t : types) typeDecl(t, false, t.s.equals(opt.className));
            w("\n");
        }
        return sb.toString();
    }

    private void register(Node n, String outer) {
        Cls c = new Cls();
        c.name = n.s;
        c.user = true;
        if (n.k.equals("enum")) {
            c.kind = 'e';
            c.sup = "java.lang.Enum";
            for (Node k : n.list) {
                Member m = new Member();
                m.name = k.s;
                m.type = n.s;
                m.stat = true;
                c.members.add(m);
            }
        } else {
            c.kind = "interface".equals(n.x) ? 'i' : 'c';
            boolean q = quiet;
            quiet = true;
            T sup = n.a == null ? null : type(n.a);
            c.sup = sup == null ? (c.kind == 'i' ? null : "java.lang.Object") : sig(sup);
            List<String> ifs = new ArrayList<>();
            for (Node i : n.list) { T it = type(i); if (it != null) ifs.add(sig(it)); }
            quiet = q;
            c.ifaces = ifs.toArray(new String[0]);
            addMembers(c, n.list2);
        }
        ix.user.put(c.name, c);
        if (n.list2 != null) for (Node m : n.list2) if (m.k.equals("class") || m.k.equals("enum")) register(m, n.s);
    }

    private void addMembers(Cls c, List<Node> ms) {
        if (ms == null) return;
        for (Node n : ms) {
            if (!n.k.equals("field") && !n.k.equals("method")) continue;
            Member m = new Member();
            m.name = n.s;
            m.node = n;
            m.method = n.k.equals("method");
            m.stat = (n.mods & Parser.STATIC) != 0;
            if (m.method) {
                m.params = new String[n.list.size()];
                boolean q = quiet;
                quiet = true;
                for (int i = 0; i < m.params.length; i++) { T pt = n.list.get(i).a == null ? T.FLOAT : type(n.list.get(i).a); m.params[i] = sig(pt); }
                quiet = q;
            }
            c.members.add(m);
        }
    }

    private static String mods(int m, boolean defPublic) {
        StringBuilder b = new StringBuilder();
        if ((m & Parser.PRIVATE) != 0) b.append("private ");
        else if ((m & Parser.PROTECTED) != 0) b.append("protected ");
        else if (defPublic || (m & Parser.PUBLIC) != 0) b.append("public ");
        if ((m & Parser.STATIC) != 0) b.append("static ");
        if ((m & Parser.ABSTRACT) != 0) b.append("abstract ");
        return b.toString();
    }

    private void typeDecl(Node n, boolean nested, boolean pub) {
        at(n);
        if (n.k.equals("enum")) {
            w((nested ? "public static " : pub ? "public " : "") + "enum " + n.s + " { ");
            for (int i = 0; i < n.list.size(); i++) w((i > 0 ? ", " : "") + n.list.get(i).s);
            w(" } ");
            return;
        }
        boolean iface = "interface".equals(n.x);
        w((nested ? "public static " : pub ? "public " : "") + ((n.mods & Parser.ABSTRACT) != 0 ? "abstract " : "") + (iface ? "interface " : "class ") + n.s);
        if (n.a != null) { T s = type(n.a); w(" extends " + (s == null ? "Object" : s.java())); }
        for (int i = 0; i < n.list.size(); i++) {
            T it = type(n.list.get(i));
            w((i == 0 ? (iface ? " extends " : " implements ") : ", ") + (it == null ? "Object" : it.java()));
        }
        w(" { ");
        Ctx c = new Ctx();
        c.name = n.s;
        c.self = T.of(n.s);
        classes.add(c);
        ArrayList<HashMap<String, Var>> saved = new ArrayList<>(scopes);
        scopes.clear();
        push();
        int cl = closure;
        closure = 0;
        for (Node m : n.list2) member(m, false);
        closure = cl;
        scopes.clear();
        scopes.addAll(saved);
        classes.remove(classes.size() - 1);
        w(" } ");
    }

    private void member(Node n, boolean top) {
        switch (n.k) {
            case "import": return;
            case "raw": at(n); w(n.s); return;
            case "class": case "enum": typeDecl(n, true, false); return;
            case "field": field(n); return;
            case "method": method(n); return;
            case "ctor": {
                at(n);
                w(mods(n.mods, true) + cls().name + "(");
                push();
                params(n.list);
                w(") { ");
                Node pm = method;
                method = n;
                body(n.list2);
                method = pm;
                pop();
                w(" } ");
                return;
            }
            case "event": event(n); return;
            default: err(n, "لا يمكن كتابة هذا هنا");
        }
    }

    private void field(Node n) {
        at(n);
        T declared = n.a != null ? type(n.a) : null;
        int m = sb.length();
        T init = null;
        if (n.b != null) {
            init = expr(n.b, declared);
            coerce(m, declared, init);
        }
        T t = declared != null ? declared : init;
        if (t == T.NULL || (t != null && t.is("void"))) t = null;
        if (declared == null) learn(n, t);
        if (t != null) names.put(n.s, sig(t));
        if (t == null) {
            err(n, "لم أستطع معرفة نوع «" + n.t.text + "». اكتب النوع بدل كلمة متغير، مثل: رقم_عشري " + n.t.text + " = ...");
            t = T.OBJECT;
        }
        boolean iface = ix.user.get(cls().name) != null && ix.user.get(cls().name).kind == 'i';
        sb.insert(m, (iface ? "" : mods(n.mods, true)) + ((n.mods & Parser.FINAL) != 0 ? "final " : "") + t.java() + " " + n.s + (n.b != null ? " = " : ""));
        w("; ");
    }

    private void params(List<Node> ps) {
        for (int i = 0; i < ps.size(); i++) {
            Node p = ps.get(i);
            T t = p.a != null ? type(p.a) : paramType.containsKey(p) ? paramType.get(p) : T.FLOAT;
            if (t == null) t = T.OBJECT;
            w((i > 0 ? ", " : "") + (reassigned.contains(p) ? "" : "final ") + t.java() + " " + p.s);
            Var v = declare(p, p.s, t);
            v.boxed = false;
        }
    }

    private void method(Node n) {
        at(n);
        T ret = n.a != null ? type(n.a) : nodeType.get(n);
        if (ret == null) ret = T.VOID;
        Cls owner = ix.user.get(cls().name);
        boolean iface = owner != null && owner.kind == 'i';
        w((iface ? "" : mods(n.mods | (n.list2 == null && !iface ? Parser.ABSTRACT : 0), true)) + ret.java() + " " + n.s + "(");
        push();
        params(n.list);
        if (n.list2 == null) { w("); "); pop(); return; }
        w(") { ");
        Node pm = method;
        int cl = closure;
        method = n;
        if (cls().anon) closure++;
        body(n.list2);
        closure = cl;
        method = pm;
        pop();
        w(" } ");
    }

    private void event(Node n) {
        at(n);
        Data.Event ev = "plain".equals(opt.kind) || classes.size() > 1 ? null : data.event(opt.kind, Data.canonPhrase(n.s));
        if (ev == null) {
            StringBuilder b = new StringBuilder();
            for (Data.Event e : data.events) if (e.kind.equals(opt.kind)) b.append(b.length() > 0 ? "، " : "").append("«").append(e.shown).append("»");
            err(n, b.length() == 0 ? "الأحداث (عند ...) تُكتب في سكربت عنصر أو سكربت مشهد فقط" : "لا يوجد حدث بهذا الاسم. الأحداث المتاحة هنا: " + b);
            w("public void _unknownEvent" + (tmp++) + "() { ");
        } else w(ev.header + " { ");
        push();
        if (ev != null) for (Map.Entry<String, String> v : ev.vars.entrySet()) declare(n, v.getKey(), ix.parse(v.getValue(), null, null)).boxed = false;
        Node pm = method;
        method = n;
        nodeType.put(n, T.VOID);
        body(n.list2);
        method = pm;
        pop();
        w(" } ");
    }

    // ------------------------------------------------------------------ statements
    private void body(List<Node> ss) {
        push();
        for (Node s : ss) stmt(s);
        pop();
    }

    private void block(List<Node> ss) {
        w("{ ");
        body(ss);
        w(" } ");
    }

    private void cond(Node e) {
        int m = sb.length();
        T t = expr(e, T.BOOL);
        if (t != null && !t.is("boolean") && !t.is("java.lang.Boolean")) {
            if (t.prim()) err(e, "الشرط يجب أن يكون صح أو خطأ، مثل: س أكبر من 5");
            else { sb.insert(m, "("); w(" != null)"); }
        }
    }

    private void stmt(Node n) {
        at(n);
        switch (n.k) {
            case "raw": w(n.s); return;
            case "var": var(n); return;
            case "assign": assign(n); w("; "); return;
            case "expr":
                if (!n.a.k.equals("call") && !n.a.k.equals("new")) { err(n, "هذه قيمة وليست أمراً. ماذا تريد أن تفعل بها؟ مثلاً: اجعل شيئاً = هذه القيمة"); return; }
                expr(n.a, null);
                w("; ");
                return;
            case "if":
                w("if (");
                cond(n.a);
                w(") ");
                block(n.list);
                if (n.list2 != null) {
                    w("else ");
                    if (n.list2.size() == 1 && n.list2.get(0).k.equals("if")) stmt(n.list2.get(0));
                    else block(n.list2);
                }
                return;
            case "while":
                w("while (");
                cond(n.a);
                w(") ");
                block(n.list);
                return;
            case "doWhile":
                w("do ");
                block(n.list);
                at(n.a);
                w("while (");
                cond(n.a);
                w("); ");
                return;
            case "repeat": {
                String i = "_i" + (tmp++);
                w("for (int " + i + " = 0, " + i + "n = (int)(");
                expr(n.a, T.INT);
                w("); " + i + " < " + i + "n; " + i + "++) { ");
                push();
                if (n.s != null) { w("final int " + n.s + " = " + i + "; "); declare(n, n.s, T.INT).boxed = false; }
                body(n.list);
                pop();
                w(" } ");
                return;
            }
            case "forRange": {
                String v = "_v" + (tmp++);
                String a = str(n.a, null);
                T ta = lastT;
                String b = str(n.b, null);
                T tb = lastT;
                String st = n.c == null ? null : str(n.c, null);
                T ts = n.c == null ? T.INT : lastT;
                T t = promote(promote(ta == null ? T.INT : ta, tb == null ? T.INT : tb), ts == null ? T.INT : ts);
                if (t == null || !t.numeric()) t = T.FLOAT;
                w("for (" + t.n + " " + v + " = " + a + ", " + v + "e = " + b + (st == null ? "" : ", " + v + "s = " + st) + "; ");
                w(st == null ? v + " <= " + v + "e; " + v + "++) { " : "(" + v + "s > 0 ? " + v + " <= " + v + "e : " + v + " >= " + v + "e); " + v + " += " + v + "s) { ");
                push();
                w("final " + t.n + " " + n.s + " = " + v + "; ");
                declare(n, n.s, t).boxed = false;
                body(n.list);
                pop();
                w(" } ");
                return;
            }
            case "forEach": {
                T want = n.x == null ? null : type((Node) n.x);
                String it = str(n.a, null);
                T tt = lastT;
                T el = want != null ? want : elemOf(tt);
                if (tt != null && tt.numeric()) { err(n.a, "«لكل ... في» تحتاج قائمة. للتكرار عدداً من المرات استخدم: كرر " + it + " مرات { }"); el = T.INT; }
                if (el == null) {
                    err(n.a, "لا أعرف نوع عناصر هذه القائمة. اكتب النوع بعد لكل، مثل: لكل نص " + n.s + " في ...");
                    el = T.OBJECT;
                }
                if (tt != null && ix.isA(tt, "java.util.Map")) it = "(" + it + ").keySet()";
                else if (tt != null && tt.is("java.lang.String")) it = "(" + it + ").toCharArray()";
                w("for (final " + el.java() + " " + n.s + " : " + it + ") { ");
                push();
                declare(n, n.s, el).boxed = false;
                body(n.list);
                pop();
                w(" } ");
                return;
            }
            case "break": w("break; "); return;
            case "continue": w("continue; "); return;
            case "return": {
                if (n.a == null) { w("return; "); return; }
                T want = method != null && method.k.equals("method") && method.a != null ? type(method.a) : (method != null ? nodeType.get(method) : null);
                w("return ");
                int m = sb.length();
                T t = expr(n.a, want);
                coerce(m, want, t);
                if (method != null && method.k.equals("method") && method.a == null && closureOwner == null) learn(method, t);
                w("; ");
                return;
            }
            case "switch": switchStmt(n); return;
            case "try": {
                w("try ");
                block(n.list);
                if (n.list2 != null) {
                    at(n.list2.isEmpty() ? n.line() : n.list2.get(0).line() - 1);
                    String name = n.s != null ? n.s : "_e" + (tmp++);
                    w("catch (final Throwable " + name + ") { ");
                    push();
                    declare(n, name, T.of("java.lang.Throwable")).boxed = false;
                    body(n.list2);
                    pop();
                    w(" } ");
                }
                if (n.x != null) {
                    w("finally ");
                    @SuppressWarnings("unchecked") List<Node> fin = (List<Node>) n.x;
                    block(fin);
                }
                return;
            }
            case "throw": {
                String s = str(n.a, null);
                if (n.x != null || (lastT != null && !ix.isA(lastT, "java.lang.Throwable"))) w("throw new RuntimeException(String.valueOf(" + s + ")); ");
                else w("throw " + s + "; ");
                return;
            }
            case "timer": {
                w("com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() { public void run() { ");
                closure++;
                Node po = closureOwner;
                closureOwner = n;
                body(n.list);
                closureOwner = po;
                closure--;
                String d = dur(n.a);
                w(" } }, " + d + ("every".equals(n.s) ? ", " + d : "") + "); ");
                return;
            }
            case "sentence": {
                Data.Tmpl tm = (Data.Tmpl) n.x;
                String j = tm.java;
                for (int i = n.list.size(); i >= 1; i--) j = j.replace("{" + i + "}", str(n.list.get(i - 1), null));
                if (j.contains("{م}")) j = j.replace("{م}", stage(n));
                w(j + "; ");
                return;
            }
            default: err(n, "لم أفهم هذا الأمر");
        }
    }

    private Node closureOwner;

    private String dur(Node d) {
        float mul = (Float) d.x;
        if (d.a == null) return mul + "f";
        String s = str(d.a, null);
        return mul == 1f ? "(float)(" + s + ")" : "(float)((" + s + ") * " + mul + "f)";
    }

    private void var(Node n) {
        T declared = n.a != null ? type(n.a) : null;
        int m = sb.length();
        T init = null;
        if (n.b != null) {
            init = expr(n.b, declared);
            coerce(m, declared, init);
        }
        T t = declared != null ? declared : init;
        if (t == T.NULL || (t != null && t.is("void"))) t = null;
        if (t == null && declared == null) t = nodeType.get(n);
        if (declared == null) learn(n, t);
        if (t == null) {
            err(n, init == T.NULL ? "لا يمكن معرفة النوع من لا_شيء. اكتب النوع بدل كلمة متغير" : "لم أستطع معرفة نوع «" + n.t.text + "». اكتب النوع بدل كلمة متغير، مثل: رقم_عشري " + n.t.text + " = ...");
            t = T.OBJECT;
        }
        if (lookupLocalHere(n.s)) err(n, "يوجد متغير آخر بالاسم «" + n.t.text + "» هنا. لتغيير قيمته اكتب: اجعل " + n.t.text + " = ...");
        Var v = declare(n, n.s, t);
        if (v.boxed) {
            sb.insert(m, "final " + t.java() + "[] " + n.s + " = { " + (n.b == null ? "" : ""));
            if (n.b == null) w(t.prim() ? (t.is("boolean") ? "false" : "0") : "null");
            w(" }; ");
            return;
        }
        boolean fin = n.b != null && (!reassigned.contains(n) || (n.mods & Parser.FINAL) != 0);
        sb.insert(m, (fin ? "final " : "") + t.java() + " " + n.s + (n.b != null ? " = " : ""));
        w("; ");
    }

    private boolean lookupLocalHere(String name) { return scopes.get(scopes.size() - 1).containsKey(name); }

    private void assign(Node n) {
        Node target = n.a;
        String op = n.s;
        if (target.k.equals("paren")) target = target.a;
        switch (target.k) {
            case "id": {
                int m0 = sb.length();
                T tt = id(target, null, true);
                if (tt != null && tt.stat) { err(target, "لا يمكن تغيير قيمة «" + target.t.text + "»"); return; }
                w(" " + op + " ");
                int m = sb.length();
                T tv = expr(n.b, tt);
                if (op.equals("=")) coerce(m, tt, tv);
                if (tt != null && tt.is("int") && tv != null && (tv.is("float") || tv.is("double")) && op.equals("=")) {
                    sb.insert(m, "(int)(");
                    w(")");
                }
                return;
            }
            case "juxta": case "member": {
                String recv = str(target.a, null);
                T rt = lastT;
                property(target, recv, rt, op, n.b);
                return;
            }
            case "index": {
                String recv = str(target.a, null);
                T rt = lastT;
                String ixs = str(target.b, null);
                boolean list = rt != null && ix.isA(rt, "java.util.List"), map = rt != null && ix.isA(rt, "java.util.Map"), garr = rt != null && ix.isA(rt, "com.badlogic.gdx.utils.Array");
                T el = map ? (ix.asSuper(rt, "java.util.Map").a.length > 1 ? ix.asSuper(rt, "java.util.Map").a[1] : null) : elemOf(rt);
                if (list || map || garr) {
                    String get = recv + ".get(" + ixs + ")";
                    w(recv + (map ? ".put(" : ".set(") + ixs + ", ");
                    value(op, get, el, n.b);
                    w(")");
                } else {
                    w(recv + "[" + ixs + "] " + op + " ");
                    expr(n.b, el);
                }
                return;
            }
            default: err(target, "لا يمكن وضع قيمة هنا. بعد «اجعل» اكتب اسم متغير أو خاصية");
        }
    }

    /** Writes the new value of "old op= v": v itself for "=", otherwise (old + v). */
    private void value(String op, String old, T type, Node v) {
        if (op.equals("=")) { int m = sb.length(); T t = expr(v, type); coerce(m, type, t); return; }
        T u = type == null ? null : type.unboxed();
        String cast = u != null && u.numeric() && !u.is("float") && !u.is("double") ? "(" + u.n + ")" : "";
        w(cast + "(" + old + " " + op.charAt(0) + " ");
        expr(v, type);
        w(")");
    }

    // ------------------------------------------------------------------ properties («س اللاعب», اللاعب.س)
    private Data.Prop prop(String name, T rt) {
        List<Data.Prop> ps = data.props(name);
        if (ps == null) return null;
        for (Data.Prop p : ps) {
            if (p.recv.equals("*")) return p;
            String fq = expand(p.recv);
            if (rt == null ? fq.equals(ITEM) : ix.isA(rt, fq)) return p;
        }
        return null;
    }

    /** Reads (op == null) or writes a property / field of recv. Returns the type when reading. */
    private T property(Node n, String recv, T rt, String op, Node value) {
        String name = n.s;
        Data.Prop p = rt != null && rt.stat ? null : prop(name, rt);
        if (p != null) {
            T pt = typeSpec(p.type, new T[]{rt});
            String get = p.getter.replace("{0}", recv);
            if (get.contains("{م}")) get = get.replace("{م}", stage(n));
            if (op == null) { w(get); return pt; }
            if (p.setter == null) { err(n, "«" + n.t.text + "» للقراءة فقط، لا يمكن تغييرها"); return pt; }
            StringBuilder save = sb;
            sb = new StringBuilder();
            value(op, get, pt, value);
            String v = sb.toString();
            sb = save;
            String set = p.setter.replace("{0}", recv).replace("{v}", v);
            if (set.contains("{م}")) set = set.replace("{م}", stage(n));
            w(set);
            return pt;
        }
        if (rt != null && rt.stat) { // Class.CONSTANT or Outer.Inner
            String jn = ix.javaName(rt, name, 0);
            if (jn != null) {
                Found f = ix.find(rt, jn, -1);
                w(recv + "." + jn);
                if (op != null) { w(" " + op + " "); expr(value, f.type(ix)); }
                return f.type(ix);
            }
            Cls inner = ix.get(rt.n + "$" + n.t.text);
            if (inner != null) { w(recv + "." + n.t.text); return T.of(inner.name).asStatic(); }
        }
        if (rt != null && !rt.stat) {
            String jf = ix.javaName(rt, name, 0);
            if (jf != null) {
                Found f = ix.find(rt, jf, -1);
                T ft = f.type(ix);
                w(recv + "." + jf);
                if (op != null) { w(" " + op + " "); int m = sb.length(); T tv = expr(value, ft); if (op.equals("=")) coerce(m, ft, tv); }
                return ft;
            }
            if (op == null) {
                String jm = ix.javaName(rt, name, 1);
                Found f = jm == null ? null : ix.find(rt, jm, 0);
                if (f != null && f.m.params.length == 0) { w(recv + "." + jm + "()"); return f.type(ix); }
            } else {
                String jm = ix.javaName(rt, "اجعل_" + name, 1);
                Found f = jm == null ? null : ix.find(rt, jm, 1);
                if (f != null && f.m.params.length == 1) {
                    T pt = f.param(ix, 0);
                    String gm = ix.javaName(rt, name, 1);
                    w(recv + "." + jm + "(");
                    value(op, recv + "." + (gm == null ? jm.replaceFirst("^set", "get") : gm) + "()", pt, value);
                    w(")");
                    return pt;
                }
            }
            if (ix.get(rt.n) != null && name.charAt(0) >= 0x80) {
                List<String> names = new ArrayList<>();
                for (String[] mm : ix.membersOf(rt)) names.add(mm[0]);
                List<Data.Prop> any = data.props(name);
                String sug = Data.closest(name, names);
                err(n, "لم أجد «" + n.t.text + "» في هذا النوع" + (sug != null ? "، هل تقصد «" + sug + "»؟" : any != null ? " (هذه الخاصية تعمل مع عناصر المشهد)" : ""));
            }
        }
        // unknown receiver type or a Latin name: trust the writer
        w(recv + "." + n.t.text);
        if (op != null) { w(" " + op + " "); expr(value, null); }
        return null;
    }

    private void switchStmt(Node n) {
        String s = "_s" + (tmp++);
        w("{ ");
        int m = sb.length();
        T t = expr(n.a, null);
        sb.insert(m, "final " + (t == null ? "Object" : t.java()) + " " + s + " = ");
        w("; ");
        boolean first = true;
        Node def = null;
        for (Node c : n.list) {
            if (c.list2 == null) { def = c; continue; }
            at(c);
            w(first ? "if (" : "else if (");
            first = false;
            for (int i = 0; i < c.list2.size(); i++) {
                if (i > 0) w(" || ");
                String v = str(c.list2.get(i), t);
                T vt = lastT;
                boolean plain = t != null && vt != null && ((t.prim() && (vt.prim() || vt == T.NULL)) || isEnum(t));
                w(plain ? s + " == " + v : N + ".eq(" + s + ", " + v + ")");
            }
            w(") ");
            block(c.list);
        }
        if (def != null) {
            at(def);
            if (!first) w("else ");
            block(def.list);
        }
        w("} ");
    }

    private boolean isEnum(T t) {
        Cls c = t == null || t.d > 0 ? null : ix.get(t.n);
        return c != null && c.kind == 'e';
    }

    // ------------------------------------------------------------------ expressions
    private T expr(Node e, T expected) {
        switch (e.k) {
            case "num": {
                String kind = (String) e.x;
                if (kind.equals("int") && expected != null && (expected.is("float") || expected.is("double"))) { w(e.s); return T.INT; }
                w(e.s);
                return T.of(kind);
            }
            case "str": return string(e);
            case "chr": w("'" + e.s + "'"); return T.CHAR;
            case "bool": w(e.s); return T.BOOL;
            case "null": w("null"); return T.NULL;
            case "this": w(closure > 0 || cls().anon ? outer().name + ".this" : "this"); return outer().self;
            case "super": w("super"); { Cls c = ix.get(cls().self.n); return c == null || c.sup == null ? null : ix.parse(c.sup, c, cls().self); }
            case "paren": { w("("); T t = expr(e.a, expected); w(")"); return t; }
            case "id": return id(e, expected, false);
            case "juxta": case "member": {
                String recv = str(e.a, null);
                return property(e, recv, lastT, null, null);
            }
            case "call": return e.a == null ? call(e, expected) : methodCall(e);
            case "index": {
                String recv = str(e.a, null);
                T rt = lastT;
                w(recv);
                if (rt != null && rt.d == 0 && rt.is("java.lang.String")) { w(".charAt("); expr(e.b, T.INT); w(")"); return T.CHAR; }
                if (rt != null && rt.d == 0 && (ix.isA(rt, "java.util.List") || ix.isA(rt, "java.util.Map") || ix.isA(rt, "com.badlogic.gdx.utils.Array"))) {
                    w(".get(");
                    expr(e.b, null);
                    w(")");
                    T m = ix.asSuper(rt, "java.util.Map");
                    return m != null ? (m.a.length > 1 ? m.a[1] : null) : elemOf(rt);
                }
                w("[");
                expr(e.b, T.INT);
                w("]");
                return rt != null && rt.d > 0 ? rt.elem() : null;
            }
            case "new": return newExpr(e);
            case "list": return listLit(e, expected);
            case "lambda": return lambda(e, expected);
            case "bin": return bin(e, expected);
            case "un": {
                w(e.s);
                int m = sb.length();
                T t = expr(e.a, e.s.equals("!") ? T.BOOL : expected);
                if (e.s.equals("!")) {
                    if (t != null && !t.is("boolean") && !t.is("java.lang.Boolean") && !t.prim()) { sb.insert(m, "("); w(" != null)"); }
                    return T.BOOL;
                }
                return t;
            }
            case "ternary": {
                w("(");
                cond(e.a);
                w(" ? ");
                T t = expr(e.b, expected);
                w(" : ");
                T u = expr(e.c, expected);
                w(")");
                return t == T.NULL || t == null ? u : (t.numeric() && u != null && u.numeric() ? promote(t, u) : t);
            }
            case "between": {
                w(N + ".between(");
                expr(e.a, null);
                w(", ");
                expr(e.b, null);
                w(", ");
                expr(e.c, null);
                w(")");
                return T.BOOL;
            }
            case "contains": {
                String a = str(e.a, null);
                T ta = lastT;
                String b = str(e.b, elemOf(ta));
                if (ta != null && ta.is("java.lang.String")) w(a + ".contains(String.valueOf(" + b + "))");
                else if (ta != null && ta.d == 0 && ix.isA(ta, "java.util.Map")) w(a + ".containsKey(" + b + ")");
                else if (ta != null && ta.d == 0 && ix.isA(ta, "java.util.Collection")) w(a + ".contains(" + b + ")");
                else w(N + ".has(" + a + ", " + b + ")");
                return T.BOOL;
            }
            case "is": {
                T t = type((Node) e.x);
                w("(");
                expr(e.a, null);
                w(" instanceof " + (t == null ? "Object" : new T(t.n, null, t.d).java()) + ")");
                return T.BOOL;
            }
            default:
                err(e, "لم أفهم هذه القيمة");
                w("null");
                return null;
        }
    }

    private Ctx outer() {
        for (int i = classes.size() - 1; i >= 0; i--) if (!classes.get(i).anon) return classes.get(i);
        return classes.get(0);
    }

    private T string(Node e) {
        List<?> parts = (List<?>) e.x;
        boolean first = true;
        if (!(parts.get(0) instanceof String)) { w("\"\""); first = false; }
        for (Object p : parts) {
            if (!first) w(" + ");
            first = false;
            if (p instanceof String) w("\"" + p + "\"");
            else {
                w("(");
                int m = sb.length();
                T t = expr((Node) p, null);
                if (t != null && (t.is("float") || t.is("double") || t.is("java.lang.Float") || t.is("java.lang.Double"))) { sb.insert(m, N + ".show("); w(")"); }
                w(")");
            }
        }
        return T.STRING;
    }

    private T id(Node e, T expected, boolean target) {
        String name = e.s;
        Var v = lookup(name);
        if (v != null) {
            if (v.level < closure) captured.add(v.decl);
            if (target) { if (!reassigned.contains(v.decl)) { reassigned.add(v.decl); changed = true; } }
            w(v.java + (v.boxed ? "[0]" : ""));
            return v.type;
        }
        for (int i = classes.size() - 1; i >= 0; i--) {
            T self = classes.get(i).self;
            String jn = ix.javaName(self, name, 0);
            if (jn != null) {
                Found f = ix.find(self, jn, -1);
                w(jn);
                return f == null ? null : f.type(ix);
            }
        }
        if (expected != null && isEnum(expected)) {
            String jn = ix.javaName(expected.asStatic(), name, 0);
            if (jn != null) { w(expected.java() + "." + jn); return expected; }
        }
        Data.Global g = data.global(name);
        if (g != null) {
            String j = g.java;
            if (j.contains("{م}")) j = j.replace("{م}", stage(e));
            w(j);
            return typeSpec(g.type, null);
        }
        String cn = className(name, false);
        if (cn != null && !(name.charAt(0) < 0x80 && Character.isLowerCase(name.charAt(0)) && name.indexOf('.') < 0 && !ix.user.containsKey(name))) {
            w(cn.replace('$', '.'));
            return T.of(cn).asStatic();
        }
        boolean latin = name.charAt(0) < 0x80;
        if (opt.elements != null) {
            for (Map.Entry<String, String> el : opt.elements.entrySet())
                if (Data.same(el.getKey(), name)) { w(el.getKey()); return ix.parse(expand(el.getValue()), null, null); }
        } else if (!"plain".equals(opt.kind)) {
            w(e.t.text.equals(name) || latin ? e.t.text : name);
            return latin && Character.isUpperCase(name.charAt(0)) ? null : ix.parse(ITEM, null, null);
        }
        if (latin) { w(e.t.text); return null; }
        List<String> cands = new ArrayList<>();
        for (HashMap<String, Var> s : scopes) cands.addAll(s.keySet());
        Cls me = ix.user.get(outer().self.n);
        if (me != null) for (Member m : me.members) cands.add(m.name);
        if (opt.elements != null) cands.addAll(opt.elements.keySet());
        cands.addAll(data.allGlobals().keySet());
        String sug = Data.closest(name, cands);
        err(e, "لم أجد شيئاً اسمه «" + e.t.text + "»" + (sug != null ? "، هل تقصد «" + sug + "»؟" : ". عرّفه أولاً: متغير " + e.t.text + " = ..."));
        w(name);
        return null;
    }

    private void args(List<Node> as, Found f) {
        for (int i = 0; i < as.size(); i++) {
            if (i > 0) w(", ");
            T want = f == null ? null : f.param(ix, i);
            int m = sb.length();
            T got = expr(as.get(i), want);
            coerce(m, want, got);
        }
    }

    /** name(args) without a receiver. */
    private T call(Node e, T expected) {
        String name = e.s;
        int argc = e.list.size();
        if (name.equals("الاب") || name.equals("super")) { w("super("); args(e.list, null); w(")"); return T.VOID; }
        // 1) a task of this class (or of an enclosing one / its super classes written in Najma or Java)
        for (int i = classes.size() - 1; i >= 0; i--) {
            T self = classes.get(i).self;
            String jn = ix.javaName(self, name, 1);
            if (jn == null) continue;
            Found f = pick(self, jn, e.list);
            if (f == null) continue;
            if (f.m.params.length != argc && data.functions(name) != null) continue;
            w(jn + "(");
            args(e.list, f);
            w(")");
            if (f.m.params.length != argc) err(e, "«" + e.t.text + "» تحتاج " + f.m.params.length + " مدخلات، وأنت كتبت " + argc);
            return f.type(ix);
        }
        // 2) ready functions of the language
        List<Data.Fn> fns = data.functions(name);
        if (fns != null) {
            Data.Fn fn = null;
            for (Data.Fn c : fns) if (c.arity == argc || (c.arity < 0 && argc >= -c.arity - 1)) { fn = c; break; }
            if (fn == null) {
                StringBuilder b = new StringBuilder();
                for (Data.Fn c : fns) b.append(b.length() > 0 ? " أو " : "").append(c.arity < 0 ? (-c.arity - 1) + " أو أكثر" : String.valueOf(c.arity));
                err(e, "«" + e.t.text + "» تحتاج " + b + " مدخلات، وأنت كتبت " + argc);
                fn = fns.get(0);
            }
            T[] at = new T[argc];
            String[] as = new String[argc];
            for (int i = 0; i < argc; i++) {
                String want = fn.argTypes.get(i + 1);
                as[i] = str(e.list.get(i), want == null ? null : typeSpec(want, at));
                at[i] = lastT;
            }
            String j = fn.java;
            if (fn.arity < 0) {
                StringBuilder rest = new StringBuilder();
                for (int i = -fn.arity - 1; i < argc; i++) rest.append(rest.length() > 0 ? ", " : "").append(as[i]);
                j = j.replace("{*}", rest.toString());
            }
            for (int i = Math.min(argc, 9); i >= 1; i--) j = j.replace("{" + i + "}", as[i - 1]);
            if (j.contains("{م}")) j = j.replace("{م}", stage(e));
            w(j);
            return typeSpec(fn.type, at);
        }
        // 3) a type name used as a function: رقم_صحيح("5") , نص(5) , متجه2(1, 2)
        String b = BUILTIN.get(name);
        if (b != null && argc == 1 && !b.startsWith("java.util") && !b.startsWith("com.")) {
            String a = str(e.list.get(0), null);
            T ta = lastT;
            boolean isStr = ta != null && ta.is("java.lang.String");
            switch (b) {
                case "java.lang.String": w("String.valueOf(" + a + ")"); return T.STRING;
                case "int": w(isStr ? N + ".toInt(" + a + ")" : ta == null || !ta.prim() ? N + ".toInt(" + a + ")" : "(int)(" + a + ")"); return T.INT;
                case "float": w(isStr || ta == null || !ta.prim() ? N + ".toFloat(" + a + ")" : "(float)(" + a + ")"); return T.FLOAT;
                case "boolean": w(isStr ? N + ".toBool(" + a + ")" : "(boolean)(" + a + ")"); return T.BOOL;
                default: w("(" + b + ")(" + a + ")"); return T.of(b);
            }
        }
        String cn = className(name, false);
        if (cn != null) {
            Node fake = new Node("new", e.t);
            fake.list = e.list;
            return construct(fake, T.of(cn));
        }
        // 4) inherited Java methods, then the scene's methods (toast, openScene ...)
        if (!"plain".equals(opt.kind)) {
            T st = T.of("com.star4droid.template.Items.StageImp");
            String jn = ix.javaName(st, name, 1);
            Found f = jn == null ? null : pick(st, jn, e.list);
            if (f != null) {
                w(stage(e) + "." + jn + "(");
                args(e.list, f);
                w(")");
                return f.type(ix);
            }
        }
        if (name.charAt(0) >= 0x80) {
            List<String> cands = new ArrayList<>(data.allFunctions().keySet());
            Cls me = ix.user.get(outer().self.n);
            if (me != null) for (Member m : me.members) if (m.method) cands.add(m.name);
            String sug = Data.closest(name, cands);
            err(e, "لا توجد مهمة باسم «" + e.t.text + "»" + (sug != null ? "، هل تقصد «" + sug + "»؟" : ". عرّفها: مهمة " + e.t.text + "() { }"));
        }
        w(e.t.text + "(");
        args(e.list, null);
        w(")");
        return null;
    }

    private T methodCall(Node e) {
        String recv = str(e.a, null);
        T rt = lastT;
        String name = e.s;
        int argc = e.list.size();
        if (rt != null && (rt.d == 0 || name.equals("length"))) {
            String jn = ix.javaName(rt, name, 1);
            Found f = jn == null ? null : pick(rt, jn, e.list);
            if (f != null) {
                w(recv + "." + jn + "(");
                args(e.list, f);
                w(")");
                T t = f.type(ix);
                if (jn.equals("getClone") || jn.equals("findItem")) return ix.parse(ITEM, null, null);
                return t;
            }
            // an array or a list asked for its size etc. with the language's own words
            List<Data.Fn> fns = data.functions(name);
            if (fns != null)
                for (Data.Fn fn : fns)
                    if (fn.arity == argc + 1) {
                        Node c = new Node("call", e.t);
                        c.s = name;
                        c.list = new ArrayList<>();
                        c.list.add(e.a);
                        c.list.addAll(e.list);
                        return call(c, null);
                    }
            if (name.charAt(0) >= 0x80 && ix.get(rt.n) != null && !rt.stat) {
                List<String> names = new ArrayList<>();
                for (String[] mm : ix.membersOf(rt)) if (!mm[2].isEmpty()) names.add(mm[0]);
                String sug = Data.closest(name, names);
                err(e, "لم أجد المهمة «" + e.t.text + "» في هذا النوع" + (sug != null ? "، هل تقصد «" + sug + "»؟" : ""));
            }
        }
        w(recv + "." + e.t.text + "(");
        args(e.list, null);
        w(")");
        return null;
    }

    private T newExpr(Node e) {
        T t = type(e.a);
        if (t == null) t = T.OBJECT;
        if (e.b != null) { // جديد رقم_صحيح[10]
            w("new " + new T(t.n, null, 0).java() + "[");
            expr(e.b, T.INT);
            w("]");
            for (int i = 0; i < t.d; i++) w("[]");
            return t.array();
        }
        return construct(e, t);
    }

    private T construct(Node e, T t) {
        w("new " + t.java() + "(");
        Found f = pick(t, "<init>", e.list);
        Cls c = ix.get(t.n);
        if (f != null && f.c != c) f = null;
        args(e.list, f);
        w(")");
        if (e.list2 != null) {
            w(" { ");
            Ctx cx = new Ctx();
            cx.name = outer().name;
            cx.self = t;
            cx.anon = true;
            classes.add(cx);
            push();
            for (Node m : e.list2) {
                if (m.k.equals("method")) {
                    // an overridden Java method written with its Arabic name
                    String jn = ix.javaName(t, m.s, 1);
                    Found sf = jn == null ? null : ix.find(t, jn, m.list.size());
                    if (sf != null && !ix.get(t.n).user) {
                        m.s = jn;
                        if (m.a == null && nodeType.get(m) == null) { T rt = sf.type(ix); if (rt != null) nodeType.put(m, rt); }
                        for (int i = 0; i < m.list.size(); i++) if (m.list.get(i).a == null) { T pt = sf.param(ix, i); if (pt != null) paramType.put(m.list.get(i), pt); }
                    }
                }
                member(m, false);
            }
            pop();
            classes.remove(classes.size() - 1);
            w(" }");
        }
        return new T(t.n, t.a, t.d);
    }

    private final IdentityHashMap<Node, T> paramType = new IdentityHashMap<>();

    private T listLit(Node e, T expected) {
        T want = expected != null && expected.d > 0 ? expected.elem() : (expected != null ? elemOf(expected) : null);
        String[] items = new String[e.list.size()];
        T[] ts = new T[items.length];
        T el = want;
        for (int i = 0; i < items.length; i++) {
            items[i] = str(e.list.get(i), want);
            ts[i] = lastT;
            if (want == null && ts[i] != null && ts[i] != T.NULL) {
                if (el == null) el = ts[i];
                else if (el.numeric() && ts[i].numeric()) el = promote(el, ts[i]);
                else if (!sig(el.boxed()).equals(sig(ts[i].boxed()))) el = ix.isA(ts[i], el.n) ? el : ix.isA(el, ts[i].n) ? ts[i] : T.OBJECT;
            }
        }
        if (el == null) el = T.OBJECT;
        T prim = el.unboxed();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) b.append(", ");
            boolean cast = prim.numeric() && ts[i] != null && ts[i].numeric() && !ts[i].n.equals(prim.n);
            b.append(cast ? "(" + prim.n + ")(" + items[i] + ")" : items[i]);
        }
        if (expected != null && expected.d > 0) {
            w("new " + expected.java() + " { " + b + " }");
            return expected;
        }
        String cn = expected != null && expected.d == 0 && ix.isA(expected, "java.util.Collection") && ix.get(expected.n) != null && ix.get(expected.n).kind == 'c' ? expected.n : "java.util.ArrayList";
        T t = new T(cn, new T[]{el.boxed()}, 0);
        w("new " + t.java() + "(" + (items.length == 0 ? "" : "java.util.Arrays.<" + el.boxed().java() + ">asList(" + b + ")") + ")");
        return t;
    }

    private T lambda(Node e, T expected) {
        Found f = expected == null ? null : ix.sam(expected);
        if (f == null) {
            err(e, "لا أعرف أين تُستخدم هذه الدالة (←). استخدمها داخل مهمة تقبل دالة، مثل: فلتر(القائمة، (ع) ← ...)");
            w("null");
            return null;
        }
        if (f.m.params.length != e.list.size()) err(e, "هذه الدالة يجب أن تأخذ " + f.m.params.length + " مدخلات");
        T ret = f.type(ix);
        boolean typeVar = f.m.type != null && f.m.type.indexOf('.') < 0 && !Index.PRIMS.containsKey(f.m.type) && !f.m.type.equals("void");
        String retJava = ret == null ? "Object" : typeVar ? ret.boxed().java() : ret.java();
        w("new " + expected.java() + "() { public " + retJava + " " + f.m.name + "(");
        closure++;
        Node po = closureOwner;
        closureOwner = e;
        push();
        for (int i = 0; i < e.list.size(); i++) {
            Node p = e.list.get(i);
            T pt = p.a != null ? type(p.a) : f.param(ix, i);
            if (pt == null) pt = T.OBJECT;
            w((i > 0 ? ", " : "") + "final " + pt.java() + " " + p.s);
            declare(p, p.s, pt).boxed = false;
        }
        w(") { ");
        boolean isVoid = ret != null && ret.is("void");
        if (e.a != null) {
            if (!isVoid) w("return ");
            if (isVoid && !e.a.k.equals("call") && !e.a.k.equals("new")) err(e.a, "هنا يُكتب أمر وليس قيمة");
            int m = sb.length();
            T got = expr(e.a, ret);
            coerce(m, ret == null ? null : ret.unboxed(), got);
            w("; ");
        } else {
            Node pm = method;
            method = e;
            if (ret != null) nodeType.put(e, ret);
            body(e.list2);
            method = pm;
        }
        pop();
        closureOwner = po;
        closure--;
        w("} }");
        return expected;
    }

    private T bin(Node e, T expected) {
        String op = e.s;
        int m = sb.length();
        if (op.equals("&&") || op.equals("||")) {
            cond(e.a);
            w(" " + op + " ");
            cond(e.b);
            sb.insert(m, "(");
            w(")");
            return T.BOOL;
        }
        boolean eq = op.equals("==") || op.equals("!=");
        T ta = expr(e.a, eq ? null : expected);
        int pa = sb.length();
        T tb = expr(e.b, eq ? ta : expected);
        if (eq) {
            if (ta != null && isEnum(ta) && tb == null) tb = ta;
            boolean plain = ta == T.NULL || tb == T.NULL || (ta != null && ta.prim()) || (tb != null && tb.prim()) || (isEnum(ta) && isEnum(tb));
            if ((ta != null && ta.prim() && tb != null && tb.is("java.lang.String")) || (tb != null && tb.prim() && ta != null && ta.is("java.lang.String"))) {
                err(e, "لا يمكن مقارنة رقم بنص. حوّل أحدهما أولاً، مثل: نص(الرقم) أو رقم_صحيح(النص)");
            }
            if (plain) { sb.insert(pa, " " + op + " "); sb.insert(m, "("); w(")"); }
            else { sb.insert(pa, ", "); sb.insert(m, (op.equals("!=") ? "!" : "") + N + ".eq("); w(")"); }
            return T.BOOL;
        }
        if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
            if (ta != null && tb != null && ta.is("java.lang.String") && tb.is("java.lang.String")) {
                sb.insert(pa, ".compareTo(");
                sb.insert(m, "(");
                w(") " + op + " 0)");
                return T.BOOL;
            }
            sb.insert(pa, " " + op + " ");
            sb.insert(m, "(");
            w(")");
            return T.BOOL;
        }
        T ua = ta == null ? null : ta.unboxed(), ub = tb == null ? null : tb.unboxed();
        if (op.equals("+") && ((ta != null && ta.is("java.lang.String")) || (tb != null && tb.is("java.lang.String")))) {
            sb.insert(pa, " + ");
            sb.insert(m, "(");
            w(")");
            return T.STRING;
        }
        if (!quiet && ((ta != null && !ua.numeric() && ta != T.NULL && ix.get(ta.n) != null && !ta.is("java.lang.Object")) || (tb != null && !ub.numeric() && ix.get(tb.n) != null && !tb.is("java.lang.Object")))
                && !(ta != null && ta.is("java.lang.String")) && !(tb != null && tb.is("java.lang.String")))
            err(e, "العملية «" + (op.equals("*") ? "×" : op) + "» تعمل مع الأرقام فقط");
        if (op.equals("÷")) {
            boolean ints = ua != null && ub != null && ua.integral() && ub.integral();
            sb.insert(pa, ints ? ") / " : " / ");
            sb.insert(m, ints ? "((float)(" : "(");
            w(")");
            if (ua == null || ub == null) return T.FLOAT;
            return ints ? T.FLOAT : promote(promote(ua, ub), T.FLOAT);
        }
        sb.insert(pa, " " + op + " ");
        sb.insert(m, "(");
        w(")");
        if (ua == null || ub == null || !ua.numeric() || !ub.numeric()) return ua != null && ua.numeric() && (ua.is("float") || ua.is("double")) ? ua : ub != null && ub.numeric() && (ub.is("float") || ub.is("double")) ? ub : null;
        return promote(ua, ub);
    }

    /** Types of untyped parameters of overridden methods (anonymous classes). */
    T paramTypeOf(Node p) { return paramType.get(p); }
}
