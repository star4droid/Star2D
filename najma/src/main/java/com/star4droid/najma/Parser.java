package com.star4droid.najma;

import com.star4droid.najma.Lexer.Tok;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Recursive descent parser: tokens to a tree of {@link Node}. Never throws out; problems go to the diagnostics. */
final class Parser {
    /** One tree node. Which fields are used depends on {@link #k} (see the parse methods). */
    static final class Node {
        String k, s;
        Tok t;
        Node a, b, c;
        List<Node> list, list2;
        Object x;
        int mods;

        Node(String k, Tok t) { this.k = k; this.t = t; }
        int line() { return t == null ? 1 : t.line; }
    }

    static final int PUBLIC = 1, PRIVATE = 2, PROTECTED = 4, STATIC = 8, FINAL = 16, ABSTRACT = 32, PROP = 64, INFER = 128;

    static final class ParseError extends RuntimeException {
        final Tok tok;
        ParseError(Tok tok, String msg) { super(msg, null, false, false); this.tok = tok; }
    }

    static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(("متغير ثابت خاصية اذا والا طالما كرر مرة مرات لكل في من الي بخطوة باستخدام افعل اخرج تخطي ارجع مهمة صنف "
            + "يرث ينفذ واجهة تعداد جديد انا الاب حاول ان اخيرا ارم صح خطا لا_شيء و او ليس يساوي اكبر اصغر اقل بين يحتوي تحتوي اجعل زد انقص بمقدار حسب حالة الباقي "
            + "بعد كل استورد باسم عند عام خاص محمي مشترك مجرد وجد موجود موجودة").split(" ")));
    static final Set<String> BUILTIN_TYPES = new HashSet<>(Arrays.asList(("رقم_صحيح رقم_طويل رقم_قصير بايت رقم_عشري عشري_دقيق منطقي حرف نص لا_ناتج كائن رقم "
            + "قائمة قائمة_مرتبطة قاموس قاموس_مرتب مجموعة طابور مكدس مصفوفة قائمة_سريعة قاموس_سريع int long short byte float double boolean char void").split(" ")));

    private final List<Tok> toks;
    private final List<Najma.Diag> diags;
    private final Data data;
    private int p;
    private final Set<String> userTypes = new HashSet<>();
    private final Set<String> declared = new HashSet<>();
    private final ArrayList<Set<String>> stops = new ArrayList<>();

    Parser(List<Tok> toks, List<Najma.Diag> diags, Data data) {
        this.toks = toks;
        this.diags = diags;
        this.data = data;
        for (int k = 0; k + 1 < toks.size(); k++) {
            Tok t = toks.get(k);
            if (t.type == Lexer.ID && (t.norm.equals("صنف") || t.norm.equals("واجهة") || t.norm.equals("تعداد")) && toks.get(k + 1).type == Lexer.ID)
                userTypes.add(Data.canon(toks.get(k + 1).norm));
            if (t.type == Lexer.ID && t.norm.equals("باسم") && toks.get(k + 1).type == Lexer.ID) userTypes.add(Data.canon(toks.get(k + 1).norm));
        }
    }

    // ------------------------------------------------------------------ token helpers
    private Tok cur() { return toks.get(p); }
    private Tok peek(int n) { return toks.get(Math.min(p + n, toks.size() - 1)); }
    private Tok next() { Tok t = toks.get(p); if (p < toks.size() - 1) p++; return t; }
    private boolean isSym(String s) { return cur().is(s); }
    private boolean isWord(String w) { return cur().word(w); }
    private boolean acceptSym(String s) { if (isSym(s)) { p++; return true; } return false; }
    private boolean acceptWord(String w) { if (isWord(w)) { p++; return true; } return false; }
    private void skipNL() { while (cur().type == Lexer.NL) p++; }
    private boolean atEnd() { return cur().type == Lexer.NL || cur().type == Lexer.EOF || isSym("}"); }

    private ParseError fail(Tok t, String msg) { return new ParseError(t, msg); }

    private Tok expectSym(String s, String why) {
        if (!isSym(s)) throw fail(cur(), "توقعت «" + s + "» " + why + "، لكن وجدت «" + cur() + "»");
        return next();
    }

    private Tok expectWord(String w, String why) {
        if (!isWord(w)) throw fail(cur(), "توقعت كلمة «" + w + "» " + why + "، لكن وجدت «" + cur() + "»");
        return next();
    }

    private Tok ident(String what) {
        Tok t = cur();
        if (t.type != Lexer.ID) throw fail(t, "توقعت " + what + "، لكن وجدت «" + t + "»");
        if (KEYWORDS.contains(t.norm)) throw fail(t, "«" + t.text + "» كلمة محجوزة في اللغة، اختر اسماً آخر ل" + what);
        return next();
    }

    private static boolean latin(String s) { return !s.isEmpty() && s.charAt(0) < 0x80; }

    // ------------------------------------------------------------------ file and members
    Node parseFile() {
        Node f = new Node("file", cur());
        f.list = new ArrayList<>();
        skipNL();
        while (cur().type != Lexer.EOF) {
            if (isSym("}")) { diags.add(new Najma.Diag(cur().line, cur().col, cur().end, "قوس إغلاق } زائد، لا يوجد قوس { مفتوح يقابله")); p++; skipNL(); continue; }
            member(f.list, true);
            skipNL();
        }
        return f;
    }

    private void member(List<Node> into, boolean top) {
        int start = p;
        try {
            Node m = parseMember(top);
            if (m != null) into.add(m);
            if (!atEnd()) throw fail(cur(), "كلام زائد لم أفهمه: «" + cur() + "». كل أمر يكون في سطر لوحده");
        } catch (ParseError e) {
            report(e);
            recover(start);
        }
    }

    private void report(ParseError e) {
        Tok t = e.tok;
        diags.add(new Najma.Diag(t.line, t.col, Math.max(t.end, t.col + 1), e.getMessage()));
    }

    /** After an error: skip to the end of the broken statement (balanced braces) so the rest is still checked. */
    private void recover(int start) {
        if (p == start) p++;
        int d = 0;
        while (cur().type != Lexer.EOF) {
            if (isSym("{")) d++;
            else if (isSym("}")) { if (d == 0) return; d--; if (d == 0) { p++; return; } }
            else if (cur().type == Lexer.NL && d == 0) return;
            p++;
        }
    }

    private int modifiers() {
        int m = 0;
        while (true) {
            if (isWord("عام")) m |= PUBLIC;
            else if (isWord("خاص")) m |= PRIVATE;
            else if (isWord("محمي")) m |= PROTECTED;
            else if (isWord("مشترك")) m |= STATIC;
            else if (isWord("مجرد")) m |= ABSTRACT;
            else if (isWord("ثابت") && (peek(1).type == Lexer.ID && !peek(2).is("=") || (m != 0 && peek(2).type == Lexer.ID))) m |= FINAL;
            else return m;
            p++;
        }
    }

    private Node parseMember(boolean top) {
        Tok t = cur();
        if (t.type == Lexer.RAW) { next(); Node n = new Node("raw", t); n.s = t.text; return n; }
        if (isWord("استورد")) return parseImport();
        int mods = modifiers();
        t = cur();
        if (isWord("صنف") || isWord("واجهة")) return parseClass(mods);
        if (isWord("تعداد")) return parseEnum(mods);
        if (isWord("مهمة")) return parseMethod(mods);
        if (t.word("عند_الانشاء") && peek(1).is("(")) {
            next();
            Node n = new Node("ctor", t);
            n.mods = mods;
            n.list = params();
            n.list2 = block("بعد عند_الانشاء");
            return n;
        }
        if (isWord("عند") || (isWord("كل") && peek(1).word("اطار"))) {
            next();
            Node n = new Node("event", t);
            StringBuilder b = new StringBuilder(t.norm);
            while (cur().type == Lexer.ID) b.append(' ').append(next().norm);
            n.s = b.toString();
            n.list2 = block("بعد اسم الحدث");
            return n;
        }
        if (isWord("متغير") || isWord("خاصية") || isWord("ثابت")) {
            Tok kw = next();
            Node n = new Node("field", ident("اسم المتغير"));
            n.s = n.t.norm;
            n.mods = mods | INFER | (kw.norm.equals("ثابت") ? FINAL : 0) | (kw.norm.equals("خاصية") ? PROP : 0);
            declared.add(Data.canon(n.s));
            if (acceptSym("=")) n.b = expr();
            else throw fail(cur(), "اكتب قيمة البداية بعد الاسم، مثل: متغير " + n.t.text + " = 0");
            return n;
        }
        if (typeAhead()) {
            Node ty = type();
            Node n = new Node("field", ident("اسم المتغير"));
            n.s = n.t.norm;
            n.a = ty;
            n.mods = mods;
            declared.add(Data.canon(n.s));
            if (acceptSym("=")) n.b = expr();
            return n;
        }
        if (top) throw fail(t, "هذا الأمر يجب أن يكون داخل حدث أو مهمة، مثل: عند البدء { ... }");
        throw fail(t, "لم أفهم «" + t + "» هنا. داخل الصنف يُكتب: متغير، مهمة، أو حدث");
    }

    private Node parseImport() {
        Tok t = next();
        Node n = new Node("import", t);
        StringBuilder b = new StringBuilder(ident("اسم الصنف").text);
        while (acceptSym(".")) b.append('.').append(isSym("*") ? next().text : ident("اسم الصنف").text);
        n.s = b.toString();
        if (acceptWord("باسم")) n.x = ident("الاسم الجديد").norm;
        return n;
    }

    private Node parseClass(int mods) {
        Tok kw = next();
        Node n = new Node("class", ident("اسم الصنف"));
        n.s = n.t.norm;
        n.mods = mods;
        n.x = kw.norm.equals("واجهة") ? "interface" : "class";
        n.list = new ArrayList<>();
        if (acceptWord("يرث")) {
            n.a = type();
            if (n.x.equals("interface")) { n.list.add(n.a); n.a = null; }
        }
        if (acceptWord("ينفذ")) {
            do n.list.add(type()); while (acceptSym(","));
        }
        n.list2 = members("بعد اسم الصنف");
        return n;
    }

    private List<Node> members(String why) {
        skipNL();
        expectSym("{", why);
        List<Node> ms = new ArrayList<>();
        skipNL();
        while (!isSym("}")) {
            if (cur().type == Lexer.EOF) throw fail(cur(), "نسيت إغلاق القوس { بقوس }");
            member(ms, false);
            skipNL();
        }
        next();
        return ms;
    }

    private Node parseEnum(int mods) {
        next();
        Node n = new Node("enum", ident("اسم التعداد"));
        n.s = n.t.norm;
        n.mods = mods;
        n.list = new ArrayList<>();
        boolean brace = false;
        if (acceptSym(":")) { /* تعداد لون: احمر، ازرق */ } else { skipNL(); expectSym("{", "أو «:» بعد اسم التعداد"); brace = true; skipNL(); }
        while (cur().type == Lexer.ID) {
            Node c = new Node("id", next());
            c.s = c.t.norm;
            n.list.add(c);
            if (brace) skipNL();
            if (!acceptSym(",")) break;
            if (brace) skipNL();
        }
        if (brace) { skipNL(); expectSym("}", "في نهاية التعداد"); }
        if (n.list.isEmpty()) throw fail(cur(), "التعداد يحتاج قيمة واحدة على الأقل، مثل: تعداد لون: احمر، ازرق");
        return n;
    }

    private List<Node> params() {
        expectSym("(", "قبل المدخلات");
        List<Node> ps = new ArrayList<>();
        while (!isSym(")")) {
            Node prm;
            if (cur().type == Lexer.ID && (peek(1).is(",") || peek(1).is(")"))) {
                prm = new Node("param", ident("اسم المدخل")); // no type = a number
            } else {
                Node ty = type();
                prm = new Node("param", ident("اسم المدخل"));
                prm.a = ty;
            }
            prm.s = prm.t.norm;
            ps.add(prm);
            if (!acceptSym(",")) break;
        }
        expectSym(")", "بعد المدخلات");
        return ps;
    }

    private Node parseMethod(int mods) {
        next();
        mods |= modifiers();
        Node n = new Node("method", ident("اسم المهمة"));
        n.s = n.t.norm;
        n.mods = mods;
        // «مهمة اقفز بقوة (ق)» : extra words before the parenthesis join the name
        while (cur().type == Lexer.ID && !KEYWORDS.contains(cur().norm)) n.s += "_" + next().norm;
        n.list = isSym("(") ? params() : new ArrayList<Node>();
        if (acceptSym(":") || acceptWord("ارجع") || (cur().word("يرجع") && p++ >= 0)) n.a = type();
        else if (cur().type == Lexer.ID && typeStart(cur()) && !peek(1).is("(")) n.a = type();
        skipNLBeforeBrace();
        if (isSym("{")) n.list2 = block("بعد اسم المهمة");
        return n;
    }

    private void skipNLBeforeBrace() {
        int s = p;
        skipNL();
        if (!isSym("{")) p = s;
    }

    // ------------------------------------------------------------------ types
    private boolean typeStart(Tok t) {
        if (t.type != Lexer.ID) return false;
        if (BUILTIN_TYPES.contains(t.norm) || userTypes.contains(Data.canon(t.norm)) || data.classAlias(t.norm) != null) return true;
        return latin(t.text) && (Character.isUpperCase(t.text.charAt(0)) || peek(1).is("."));
    }

    /** True when a declaration «Type name [= ...]» starts here. */
    private boolean typeAhead() {
        if (!typeStart(cur())) return false;
        int s = p;
        try {
            type();
            Tok n = cur();
            if (n.type != Lexer.ID || KEYWORDS.contains(n.norm)) return false;
            Tok after = peek(1);
            if (!(after.is("=") || after.type == Lexer.NL || after.type == Lexer.EOF || after.is("}"))) return false;
            return !declared.contains(Data.canon(n.norm)) || BUILTIN_TYPES.contains(toks.get(s).norm);
        } catch (ParseError e) {
            return false;
        } finally {
            p = s;
        }
    }

    Node type() {
        Tok t = cur();
        if (t.type != Lexer.ID) throw fail(t, "توقعت اسم نوع (مثل رقم_صحيح أو نص)، لكن وجدت «" + t + "»");
        next();
        Node n = new Node("type", t);
        n.s = t.norm;
        n.list = new ArrayList<>();
        if (latin(t.text)) {
            StringBuilder b = new StringBuilder(t.text);
            while (isSym(".") && peek(1).type == Lexer.ID) { next(); b.append('.').append(next().text); }
            n.s = b.toString();
        }
        if (isWord("من") && !peek(1).word("نوع")) { // قائمة من نص ، قاموس من نص الي رقم_صحيح
            next();
            n.list.add(type());
            if (acceptWord("الي")) n.list.add(type());
        } else if (isSym("<")) {
            next();
            if (!isSym(">")) do n.list.add(type()); while (acceptSym(","));
            expectSym(">", "لإغلاق النوع");
        }
        int dims = 0;
        while (isSym("[") && peek(1).is("]")) { next(); next(); dims++; }
        n.x = dims;
        return n;
    }

    // ------------------------------------------------------------------ statements
    List<Node> block(String why) {
        skipNLBeforeBrace();
        Tok open = cur();
        expectSym("{", why);
        List<Node> body = new ArrayList<>();
        skipNL();
        while (!isSym("}")) {
            if (cur().type == Lexer.EOF) throw fail(open, "نسيت إغلاق هذا القوس { بقوس }");
            int start = p;
            try {
                Node s = statement();
                if (s != null) body.add(s);
                if (!atEnd()) throw fail(cur(), "كلام زائد لم أفهمه: «" + cur() + "». كل أمر يكون في سطر لوحده");
            } catch (ParseError e) {
                report(e);
                recover(start);
            }
            skipNL();
        }
        next();
        return body;
    }

    private Node statement() {
        Tok t = cur();
        if (t.type == Lexer.RAW) { next(); Node n = new Node("raw", t); n.s = t.text; return n; }
        if (t.type == Lexer.ID) {
            switch (t.norm) {
                case "متغير": case "ثابت": {
                    if (t.norm.equals("ثابت") && typeStart(peek(1)) && peek(2).type == Lexer.ID && !peek(2).is("=")) {
                        next();
                        Node d = declTyped();
                        d.mods |= FINAL;
                        return d;
                    }
                    next();
                    Node n = new Node("var", ident("اسم المتغير"));
                    n.s = n.t.norm;
                    n.mods = INFER | (t.norm.equals("ثابت") ? FINAL : 0);
                    declared.add(Data.canon(n.s));
                    if (!acceptSym("=")) throw fail(cur(), "اكتب قيمة البداية بعد الاسم، مثل: متغير " + n.t.text + " = 0");
                    n.b = expr();
                    return n;
                }
                case "اجعل": {
                    if (data.sentences("اجعل") != null && peek(1).type == Lexer.ID && !peek(2).is("=")) {
                        Node s = sentence(true);
                        if (s != null) return s;
                    }
                    next();
                    Node n = new Node("assign", t);
                    n.s = "=";
                    pushStops("يساوي");
                    try { n.a = postfixOrJuxta(); } finally { popStops(); }
                    if (!acceptSym("=") && !acceptWord("يساوي")) throw fail(cur(), "بعد «اجعل " + "...» اكتب = ثم القيمة، مثل: اجعل النقاط = 0");
                    n.b = expr();
                    return n;
                }
                case "زد": case "انقص": {
                    next();
                    Node n = new Node("assign", t);
                    n.s = t.norm.equals("زد") ? "+=" : "-=";
                    n.a = postfixOrJuxta();
                    if (acceptWord("بمقدار")) n.b = expr();
                    else { n.b = new Node("num", t); n.b.s = "1"; n.b.x = "int"; }
                    return n;
                }
                case "اذا": return parseIf();
                case "طالما": {
                    next();
                    Node n = new Node("while", t);
                    n.a = condition();
                    n.list = block("بعد شرط طالما");
                    return n;
                }
                case "افعل": {
                    next();
                    Node n = new Node("doWhile", t);
                    n.list = block("بعد افعل");
                    skipNL();
                    expectWord("طالما", "بعد كتلة افعل");
                    n.a = condition();
                    return n;
                }
                case "كرر": {
                    if (peek(1).word("صوت")) { // «كرر صوت ...» is a sentence, not a loop
                        Node s = sentence(true);
                        if (s != null) return s;
                    }
                    return parseRepeat();
                }
                case "لكل": return parseFor();
                case "اخرج": next(); return new Node("break", t);
                case "تخطي": next(); return new Node("continue", t);
                case "ارجع": {
                    next();
                    Node n = new Node("return", t);
                    if (!atEnd()) n.a = expr();
                    return n;
                }
                case "حسب": return parseSwitch();
                case "حاول": return parseTry();
                case "ارم": {
                    next();
                    Node n = new Node("throw", t);
                    if (acceptWord("خطا")) n.x = "msg";
                    n.a = expr();
                    return n;
                }
                case "بعد": case "كل": {
                    next();
                    if (t.norm.equals("كل") && isWord("اطار")) throw fail(t, "«كل إطار» حدث يُكتب خارج الأحداث الأخرى، وليس داخلها");
                    Node n = new Node("timer", t);
                    n.s = t.norm.equals("بعد") ? "after" : "every";
                    n.a = duration();
                    n.list = block("بعد المدة");
                    return n;
                }
                case "مهمة": case "صنف": case "عند": case "تعداد": case "واجهة":
                    throw fail(t, "«" + t.text + "» لا تُكتب داخل حدث أو مهمة. أغلق القوس } أولاً ثم اكتبها في الخارج");
                default:
            }
            if (typeAhead()) return declTyped();
            Node s = sentence(false);
            if (s != null) return s;
        }
        int start = p;
        Node e;
        try {
            e = expr();
        } catch (ParseError err) {
            p = start;
            String hint = data.suggestSentence(cur());
            if (hint != null && cur().type == Lexer.ID) throw fail(cur(), "لم أفهم هذه الجملة. هل تقصد: " + hint);
            throw err;
        }
        if (isSym("=") || isSym("+=") || isSym("-=") || isSym("*=") || isSym("/=")) {
            Node n = new Node("assign", next());
            n.s = n.t.text;
            n.a = e;
            n.b = expr();
            return n;
        }
        if (isSym("++") || isSym("--")) {
            Node n = new Node("assign", next());
            n.s = n.t.text.equals("++") ? "+=" : "-=";
            n.a = e;
            n.b = new Node("num", n.t);
            n.b.s = "1";
            n.b.x = "int";
            return n;
        }
        if (e.k.equals("juxta") || e.k.equals("id")) {
            String hint = data.suggestSentence(e.t);
            throw fail(e.t, "لم أفهم هذا الأمر" + (hint != null ? ". هل تقصد: " + hint : ". الأمر يبدأ بفعل مثل: اجعل، زد، حرك، أو باسم مهمة بعده قوسان ()"));
        }
        Node n = new Node("expr", e.t);
        n.a = e;
        return n;
    }

    private Node declTyped() {
        Node ty = type();
        Node n = new Node("var", ident("اسم المتغير"));
        n.s = n.t.norm;
        n.a = ty;
        declared.add(Data.canon(n.s));
        if (acceptSym("=")) n.b = expr();
        return n;
    }

    private Node condition() {
        if (isWord("كان") || isWord("كانت")) next();
        pushStops();
        try { return expr(); } finally { popStops(); }
    }

    private Node parseIf() {
        Tok t = next();
        Node n = new Node("if", t);
        n.a = condition();
        n.list = block("بعد شرط إذا");
        int s = p;
        skipNL();
        if (acceptWord("والا")) {
            if (isWord("اذا")) {
                n.list2 = new ArrayList<>();
                n.list2.add(parseIf());
            } else n.list2 = block("بعد وإلا");
        } else p = s;
        return n;
    }

    private Node parseRepeat() {
        Tok t = next();
        Node n = new Node("repeat", t);
        pushStops("مرة", "مرات", "مره", "باستخدام");
        try { n.a = expr(); } finally { popStops(); }
        if (!(acceptWord("مرة") || acceptWord("مرات") || cur().word("مره") && p++ >= 0)) throw fail(cur(), "بعد العدد اكتب «مرة» أو «مرات»، مثل: كرر 10 مرات { }");
        if (acceptWord("باستخدام")) { n.s = ident("اسم العدّاد").norm; declared.add(Data.canon(n.s)); }
        n.list = block("بعد كرر");
        return n;
    }

    private Node parseFor() {
        Tok t = next();
        Node ty = null;
        if (typeStart(cur()) && peek(1).type == Lexer.ID && !KEYWORDS.contains(peek(1).norm)) ty = type();
        Tok v = ident("اسم المتغير بعد لكل");
        declared.add(Data.canon(v.norm));
        Node n;
        if (acceptWord("في")) {
            n = new Node("forEach", t);
            n.a = condition();
        } else if (acceptWord("من")) {
            n = new Node("forRange", t);
            n.a = expr();
            expectWord("الي", "بعد قيمة البداية");
            n.b = expr();
            if (acceptWord("بخطوة")) n.c = expr();
        } else throw fail(cur(), "بعد «لكل " + v.text + "» اكتب «في» ثم القائمة، أو «من» ثم البداية «إلى» النهاية");
        n.s = v.norm;
        n.x = ty;
        n.list = block("بعد لكل");
        return n;
    }

    private Node parseSwitch() {
        Tok t = next();
        Node n = new Node("switch", t);
        n.a = expr();
        n.list = new ArrayList<>();
        skipNL();
        expectSym("{", "بعد حسب");
        skipNL();
        while (!isSym("}")) {
            Tok ct = cur();
            Node c = new Node("case", ct);
            if (acceptWord("حالة")) {
                c.list2 = new ArrayList<>();
                do c.list2.add(expr()); while (acceptSym(","));
            } else if (!acceptWord("الباقي")) throw fail(ct, "داخل «حسب» اكتب «حالة قيمة:» أو «الباقي:»");
            expectSym(":", "بعد الحالة");
            c.list = new ArrayList<>();
            skipNL();
            if (isSym("{")) c.list = block("");
            else
                while (!isWord("حالة") && !isWord("الباقي") && !isSym("}")) {
                    if (cur().type == Lexer.EOF) throw fail(t, "نسيت إغلاق «حسب» بقوس }");
                    Node s = statement();
                    if (s != null) c.list.add(s);
                    if (!atEnd() && !isWord("حالة") && !isWord("الباقي")) throw fail(cur(), "كلام زائد لم أفهمه: «" + cur() + "»");
                    skipNL();
                }
            n.list.add(c);
            skipNL();
        }
        next();
        return n;
    }

    private Node parseTry() {
        Tok t = next();
        Node n = new Node("try", t);
        n.list = block("بعد حاول");
        int s = p;
        skipNL();
        if (acceptWord("ان")) {
            expectWord("حدث", "بعد إن");
            expectWord("خطا", "بعد حدث");
            if (cur().type == Lexer.ID && !KEYWORDS.contains(cur().norm)) { n.s = next().norm; declared.add(Data.canon(n.s)); }
            n.list2 = block("بعد إن حدث خطأ");
            s = p;
            skipNL();
        }
        if (acceptWord("اخيرا")) n.x = block("بعد أخيراً");
        else p = s;
        if (n.list2 == null && n.x == null) throw fail(t, "بعد «حاول { }» اكتب: إن حدث خطأ { }");
        return n;
    }

    private Node duration() {
        Tok t = cur();
        Node n = new Node("dur", t);
        float mul = 1;
        if (t.type == Lexer.ID) {
            Float one = unit(t.norm, true);
            if (one != null) { next(); n.x = one; return n; }
            if ((t.norm.equals("نصف") || t.norm.equals("ربع")) && unit(peek(1).norm, false) != null) {
                next();
                n.x = (t.norm.equals("نصف") ? 0.5f : 0.25f) * unit(next().norm, false);
                return n;
            }
        }
        pushStops("ثانية", "ثواني", "ثوان", "ثانيه", "دقيقة", "دقائق", "دقيقه", "ملي", "مللي", "ملي_ثانية");
        try { n.a = expr(); } finally { popStops(); }
        if (cur().type == Lexer.ID) {
            if ((isWord("ملي") || isWord("مللي")) && unit(peek(1).norm, false) != null) { next(); next(); mul = 0.001f; }
            else {
                Float u = unit(cur().norm, false);
                if (u != null) { next(); mul = u; }
            }
        }
        n.x = mul;
        return n;
    }

    private static Float unit(String w, boolean alone) {
        switch (w) {
            case "ثانيتين": case "ثانيتان": return alone ? 2f : null;
            case "دقيقتين": case "دقيقتان": return alone ? 120f : null;
            case "ثانية": case "ثانيه": return 1f;
            case "ثواني": case "ثوان": return alone ? null : 1f;
            case "دقيقة": case "دقيقه": return 60f;
            case "دقائق": return alone ? null : 60f;
            case "ملي_ثانية": return alone ? null : 0.001f;
            default: return null;
        }
    }

    // ------------------------------------------------------------------ sentences (templates)
    private Node sentence(boolean soft) {
        List<Data.Tmpl> cands = data.sentences(cur().norm);
        if (cands == null) return null;
        int start = p;
        ParseError best = null;
        for (Data.Tmpl tm : cands) {
            p = start;
            try {
                Node n = new Node("sentence", cur());
                n.x = tm;
                n.list = new ArrayList<>();
                for (int k = 0; k < tm.parts.length; k++) {
                    String lit = tm.parts[k];
                    if (lit != null) {
                        if (cur().type != Lexer.ID || !Data.same(cur().norm, lit)) throw fail(cur(), "توقعت «" + lit + "»");
                        next();
                        continue;
                    }
                    String nextLit = k + 1 < tm.parts.length ? tm.parts[k + 1] : null;
                    if (k + 1 < tm.parts.length && nextLit == null) n.list.add(postfix()); // two values in a row: simple values only
                    else {
                        if (nextLit != null) pushStops(nextLit); else pushStops();
                        try { n.list.add(expr()); } finally { popStops(); }
                    }
                }
                if (!atEnd()) throw fail(cur(), "كلام زائد");
                return n;
            } catch (ParseError e) {
                if (best == null || e.tok.pos > best.tok.pos) best = e;
            }
        }
        p = start;
        // not a sentence after all (maybe a call or an assignment that starts with the same word)
        if (soft || peek(1).is("(") || peek(1).is("=") || peek(1).is(".")) return null;
        throw fail(best.tok, "لم أفهم هذه الجملة. الصيغة الصحيحة: " + cands.get(0).shown);
    }

    private void pushStops(String... words) { stops.add(new HashSet<>(Arrays.asList(words))); }
    private void popStops() { stops.remove(stops.size() - 1); }

    private boolean stopped(Tok t) {
        if (t.type != Lexer.ID || stops.isEmpty()) return false;
        for (String s : stops.get(stops.size() - 1)) if (Data.same(t.norm, s)) return true;
        return false;
    }

    // ------------------------------------------------------------------ expressions
    Node expr() {
        Node c = or();
        if (isSym("?")) {
            Node n = new Node("ternary", next());
            n.a = c;
            n.b = expr();
            expectSym(":", "بين القيمتين");
            n.c = expr();
            return n;
        }
        return c;
    }

    private Node bin(String op, Tok t, Node a, Node b) {
        Node n = new Node("bin", t);
        n.s = op;
        n.a = a;
        n.b = b;
        return n;
    }

    private Node or() {
        Node a = and();
        while (isSym("||") || (isWord("او") && !stopped(cur()))) { Tok t = next(); a = bin("||", t, a, and()); }
        return a;
    }

    private Node and() {
        Node a = not();
        while (isSym("&&") || (isWord("و") && !stopped(cur()))) { Tok t = next(); a = bin("&&", t, a, not()); }
        return a;
    }

    private Node not() {
        if (isWord("ليس") || isWord("ليست")) {
            Node n = new Node("un", next());
            n.s = "!";
            n.a = not();
            return n;
        }
        return cmp();
    }

    private Node cmp() {
        Node a = add();
        while (true) {
            Tok t = cur();
            if (t.type == Lexer.SYM && (t.text.equals("==") || t.text.equals("!=") || t.text.equals("<") || t.text.equals(">") || t.text.equals("<=") || t.text.equals(">="))) {
                next();
                a = bin(t.text, t, a, add());
            } else if (t.type == Lexer.ID && !stopped(t)) {
                boolean neg = t.norm.equals("لا") && (peek(1).word("يساوي") || peek(1).word("يحتوي") || peek(1).word("تحتوي"));
                Tok w = neg ? peek(1) : t;
                if (w.word("يساوي")) {
                    p += neg ? 2 : 1;
                    a = bin(neg ? "!=" : "==", t, a, add());
                } else if (w.word("اكبر") || w.word("اصغر") || w.word("اقل")) {
                    next();
                    acceptWord("من");
                    String op = w.norm.equals("اكبر") ? ">" : "<";
                    if (isWord("او") && peek(1).word("يساوي")) { next(); next(); op += "="; }
                    a = bin(op, t, a, add());
                } else if (w.word("بين")) {
                    next();
                    Node n = new Node("between", t);
                    n.a = a;
                    pushStops("و");
                    try { n.b = add(); } finally { popStops(); }
                    expectWord("و", "بين الحدين");
                    n.c = add();
                    a = n;
                } else if (w.word("يحتوي") || w.word("تحتوي")) {
                    p += neg ? 2 : 1;
                    if (isWord("علي")) next();
                    Node n = new Node("contains", t);
                    n.a = a;
                    n.b = add();
                    if (neg) { Node u = new Node("un", t); u.s = "!"; u.a = n; n = u; }
                    a = n;
                } else if (w.word("من") && peek(1).word("نوع")) {
                    next(); next();
                    Node n = new Node("is", t);
                    n.a = a;
                    n.x = type();
                    a = n;
                } else if (w.word("موجود") || w.word("موجودة")) {
                    next();
                    a = bin("!=", t, a, new Node("null", t));
                } else return a;
            } else return a;
        }
    }

    private Node add() {
        Node a = mul();
        while (isSym("+") || isSym("-")) { Tok t = next(); a = bin(t.text, t, a, mul()); }
        return a;
    }

    private Node mul() {
        Node a = unary();
        while (isSym("*") || isSym("/") || isSym("%") || isSym("÷") || (isWord("باقي") && !stopped(cur()))) {
            Tok t = next();
            a = bin(t.type == Lexer.ID ? "%" : t.text, t, a, unary());
        }
        return a;
    }

    private Node unary() {
        if (isSym("-") || isSym("!") || isSym("+")) {
            Tok t = next();
            Node n = new Node("un", t);
            n.s = t.text;
            n.a = unary();
            return n;
        }
        if (isWord("وجد")) { // إذا وُجد الهدف
            Tok t = next();
            return bin("!=", t, unary(), new Node("null", t));
        }
        return postfixOrJuxta();
    }

    /** «سرعة اللاعب» : a name followed by a value = that property of the value. */
    private Node postfixOrJuxta() {
        Tok t = cur();
        if (t.type == Lexer.ID && !KEYWORDS.contains(t.norm) && !stopped(t) && !peek(1).is("(") && !peek(1).is(".") && !peek(1).is("[")) {
            Tok n = peek(1);
            boolean operand = (n.type == Lexer.ID && (!KEYWORDS.contains(n.norm) || n.norm.equals("انا")) && !stopped(n) && !isCmpWord(n));
            if (operand) {
                next();
                Node j = new Node("juxta", t);
                j.s = t.norm;
                j.a = postfixOrJuxta();
                return j;
            }
        }
        return postfix();
    }

    private static boolean isCmpWord(Tok t) {
        return t.norm.equals("باقي") || t.norm.equals("لا") || t.norm.equals("علي") || t.norm.equals("كان") || t.norm.equals("كانت") || t.norm.equals("يرجع");
    }

    private List<Node> args() {
        expectSym("(", "قبل المدخلات");
        List<Node> as = new ArrayList<>();
        pushStops();
        try {
            while (!isSym(")")) {
                as.add(expr());
                if (!acceptSym(",")) break;
            }
        } finally { popStops(); }
        expectSym(")", "لإغلاق القوس (");
        return as;
    }

    private Node postfix() {
        Node e = primary();
        while (true) {
            if (isSym(".") && peek(1).type == Lexer.ID) {
                next();
                Tok name = next();
                if (isSym("(")) {
                    Node c = new Node("call", name);
                    c.a = e;
                    c.s = name.norm;
                    c.list = args();
                    e = c;
                } else {
                    Node m = new Node("member", name);
                    m.a = e;
                    m.s = name.norm;
                    e = m;
                }
            } else if (isSym("[")) {
                Node ix = new Node("index", next());
                ix.a = e;
                pushStops();
                try { ix.b = expr(); } finally { popStops(); }
                expectSym("]", "لإغلاق القوس [");
                e = ix;
            } else return e;
        }
    }

    private boolean lambdaAhead() {
        int k = p + 1, d = 1;
        while (k < toks.size() && d > 0) {
            Tok t = toks.get(k);
            if (t.is("(")) d++;
            else if (t.is(")")) d--;
            else if (t.type == Lexer.EOF || t.type == Lexer.NL || t.is("{")) return false;
            k++;
        }
        return k < toks.size() && toks.get(k).is("←");
    }

    private Node lambda(List<Node> ps, Tok t) {
        Node n = new Node("lambda", t);
        n.list = ps;
        for (Node q : ps) declared.add(Data.canon(q.s));
        if (isSym("{")) n.list2 = block("");
        else n.a = expr();
        return n;
    }

    private Node primary() {
        Tok t = cur();
        switch (t.type) {
            case Lexer.NUM: { next(); Node n = new Node("num", t); n.s = t.text; n.x = t.val; return n; }
            case Lexer.CHR: { next(); Node n = new Node("chr", t); n.s = t.text; return n; }
            case Lexer.STR: {
                next();
                Node n = new Node("str", t);
                n.list = new ArrayList<>();
                List<Object> parts = new ArrayList<>();
                for (Object part : (List<?>) t.val) {
                    if (part instanceof String) { parts.add(part); continue; }
                    @SuppressWarnings("unchecked") Parser sub = new Parser((List<Tok>) part, diags, data);
                    sub.declared.addAll(declared);
                    sub.userTypes.addAll(userTypes);
                    try {
                        Node e = sub.expr();
                        if (sub.cur().type != Lexer.EOF) throw sub.fail(sub.cur(), "كلام زائد داخل { } في النص: «" + sub.cur() + "»");
                        parts.add(e);
                    } catch (ParseError e) { report(e); parts.add(""); }
                }
                n.x = parts;
                return n;
            }
            case Lexer.SYM:
                if (t.text.equals("(")) {
                    if (lambdaAhead()) {
                        next();
                        List<Node> ps = new ArrayList<>();
                        while (!isSym(")")) {
                            Node prm;
                            if (cur().type == Lexer.ID && (peek(1).is(",") || peek(1).is(")"))) prm = new Node("param", next());
                            else { Node ty = type(); prm = new Node("param", ident("اسم المدخل")); prm.a = ty; }
                            prm.s = prm.t.norm;
                            ps.add(prm);
                            if (!acceptSym(",")) break;
                        }
                        expectSym(")", "بعد مدخلات الدالة");
                        Tok arrow = next();
                        return lambda(ps, arrow);
                    }
                    next();
                    pushStops();
                    Node e;
                    try { e = expr(); } finally { popStops(); }
                    expectSym(")", "لإغلاق القوس (");
                    Node n = new Node("paren", t);
                    n.a = e;
                    return n;
                }
                if (t.text.equals("[")) {
                    next();
                    Node n = new Node("list", t);
                    n.list = new ArrayList<>();
                    pushStops();
                    try {
                        while (!isSym("]")) {
                            n.list.add(expr());
                            if (!acceptSym(",")) break;
                        }
                    } finally { popStops(); }
                    expectSym("]", "لإغلاق القائمة [");
                    return n;
                }
                break;
            case Lexer.ID:
                switch (t.norm) {
                    case "صح": { next(); Node n = new Node("bool", t); n.s = "true"; return n; }
                    case "خطا": { next(); Node n = new Node("bool", t); n.s = "false"; return n; }
                    case "لا_شيء": next(); return new Node("null", t);
                    case "انا": next(); return new Node("this", t);
                    case "الاب": {
                        next();
                        if (isSym("(")) { Node c = new Node("call", t); c.s = "الاب"; c.list = args(); return c; }
                        return new Node("super", t);
                    }
                    case "جديد": {
                        next();
                        Node n = new Node("new", t);
                        n.a = type();
                        if (isSym("[")) { // جديد رقم_صحيح[10]
                            next();
                            n.b = expr();
                            expectSym("]", "بعد حجم المصفوفة");
                            return n;
                        }
                        n.list = isSym("(") ? args() : new ArrayList<Node>();
                        int s = p;
                        if (isSym("{")) {
                            try { n.list2 = members(""); } catch (ParseError e) { p = s; }
                        }
                        return n;
                    }
                    default:
                }
                if (KEYWORDS.contains(t.norm)) break;
                next();
                if (isSym("←")) {
                    Tok arrow = next();
                    Node prm = new Node("param", t);
                    prm.s = t.norm;
                    List<Node> ps = new ArrayList<>();
                    ps.add(prm);
                    return lambda(ps, arrow);
                }
                if (isSym("(")) {
                    Node c = new Node("call", t);
                    c.s = t.norm;
                    c.list = args();
                    return c;
                }
                Node n = new Node("id", t);
                n.s = t.norm;
                return n;
            default:
        }
        if (t.type == Lexer.NL || t.type == Lexer.EOF || t.is("}") || t.is(")")) throw fail(t, "الجملة ناقصة، توقعت قيمة هنا");
        throw fail(t, "لم أتوقع «" + t + "» هنا، توقعت قيمة (رقم، نص، أو اسم)");
    }
}
