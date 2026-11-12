package com.star4droid.najma;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * نجمة (Najma): the Arabic programming language of Star2D. This class is the whole public surface:
 * {@link #compile} turns one .njm file into one .java file (same line numbers), {@link #runtimeSource} is the small
 * helper class the generated code calls, {@link #translate} turns a Java compiler message into Arabic.
 * Pure Java, no Android and no libGDX classes: it runs in the editor only, never inside a game.
 */
public final class Najma {
    private Najma() {}

    public static final String EXT = ".njm";
    /** First line marker of every generated .java file. */
    public static final String MARK = "/*najma*/";

    /** One problem in the source. Lines and columns are 1-based; the message is Arabic. */
    public static final class Diag {
        public final int line, col, endCol;
        public final String message;

        public Diag(int line, int col, int endCol, String message) {
            this.line = line;
            this.col = col;
            this.endCol = endCol;
            this.message = message;
        }
        @Override public String toString() { return line + ":" + col + " " + message; }
    }

    public static final class Options {
        /** "item" (element script), "scene" (scene script) or "plain" (any other class). */
        public String kind = "plain";
        public String pkg = "", className = "Main";
        /** Written after "extends" (may be a class the index does not know, like the scene's generated Refs class). */
        public String superClass;
        /** A class the index knows that superClass extends (ItemScript / SceneScript): gives the inherited members. */
        public String baseType;
        /** Elements of the scene: name to Java type. null = unknown (any name is accepted). */
        public Map<String, String> elements;
    }

    public static final class Result {
        public String java = "";
        public final List<Diag> errors = new ArrayList<>();
        /** Variables of the file: name to Java type (for {@link #members}). */
        public final Map<String, String> names = new java.util.LinkedHashMap<>();
        public boolean ok() { return errors.isEmpty(); }
    }

    private static Index index;

    static synchronized Index index() {
        if (index == null) index = new Index(Data.get());
        return index;
    }

    /** Loads the vocabulary and the class index (about 0.2 s the first time); call it from a background thread. */
    public static void warmUp() { index(); }

    public static synchronized Result compile(String source, Options opt) {
        Result r = new Result();
        try {
            List<Lexer.Tok> toks = Lexer.lex(source, r.errors);
            Parser.Node file = new Parser(toks, r.errors, Data.get()).parseFile();
            Gen g = new Gen(opt, Data.get(), index());
            String out = "";
            for (int pass = 0; pass < 5; pass++) { // learn types until nothing changes
                g.changed = false;
                out = g.file(file);
                if (!g.changed && pass > 0) break;
            }
            g.quiet = false;
            out = g.file(file);
            java.util.Set<Integer> broken = new java.util.HashSet<>();
            for (Diag d : r.errors) broken.add(d.line);
            for (Diag d : g.diags) if (!broken.contains(d.line)) r.errors.add(d);
            r.java = MARK + out;
            r.names.putAll(g.names);
            if (opt.elements != null) r.names.putAll(opt.elements);
        } catch (RuntimeException e) {
            StackTraceElement at = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
            r.errors.add(new Diag(1, 1, 2, "خطأ داخلي في مترجم نجمة: " + e + (at == null ? "" : " (" + at.getFileName() + ":" + at.getLineNumber() + ")")));
        }
        java.util.Collections.sort(r.errors, (a, b) -> a.line != b.line ? a.line - b.line : a.col - b.col);
        return r;
    }

    /**
     * Options from where the file is in the project: java/com/star4droid/Game/Scripts/&lt;scene&gt;/XScript.njm is an element
     * script, java/com/star4droid/Game/SceneScript/&lt;scene&gt;Script.njm a scene script, anything else a plain class.
     * @param relative path of the .njm file relative to the project's java folder, with / separators
     */
    public static Options optionsFor(String relative) {
        Options o = new Options();
        String p = relative.replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        int slash = p.lastIndexOf('/');
        String dir = slash < 0 ? "" : p.substring(0, slash), file = p.substring(slash + 1);
        o.className = file.endsWith(EXT) ? file.substring(0, file.length() - EXT.length()) : file;
        o.pkg = dir.replace('/', '.');
        if (dir.startsWith("com/star4droid/Game/Scripts/") && o.className.endsWith("Script")) {
            String scene = dir.substring("com/star4droid/Game/Scripts/".length());
            o.kind = "item";
            o.superClass = "com.star4droid.Game.Refs." + scene.toLowerCase() + "ItemRefs";
            o.baseType = "com.star4droid.template.Utils.ItemScript";
        } else if (dir.equals("com/star4droid/Game/SceneScript") && o.className.endsWith("Script")) {
            String scene = o.className.substring(0, o.className.length() - "Script".length());
            o.kind = "scene";
            o.pkg = "com.star4droid.game.SceneScript";
            o.superClass = "com.star4droid.Game.Refs." + scene.toLowerCase() + "SceneRefs";
            o.baseType = "com.star4droid.template.Utils.SceneScript";
        }
        return o;
    }

    /** Source of najma.N, the helper class generated code calls. Written to java/najma/N.java of a project that uses Najma. */
    public static String runtimeSource() { return resource("/najma/N.java.txt"); }

    static String resource(String name) {
        try (InputStream in = Najma.class.getResourceAsStream(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException("missing resource " + name, e);
        }
    }

    // ------------------------------------------------------------------ editor services
    public static final int H_KEYWORD = 1, H_TYPE = 2, H_NUMBER = 3, H_STRING = 4, H_COMMENT = 5, H_CALL = 6, H_SYMBOL = 7, H_JAVA = 8;

    /** Colours for the editor: triples (start, end, kind) over the characters of the source. Never throws. */
    public static int[] highlight(String src) {
        List<Integer> out = new ArrayList<>();
        try {
            Data data = Data.get();
            List<Lexer.Tok> toks = Lexer.lex(src, null);
            int prevEnd = 0;
            for (int i = 0; i < toks.size(); i++) {
                Lexer.Tok t = toks.get(i);
                comments(src, prevEnd, Math.min(t.pos, src.length()), out);
                prevEnd = Math.max(prevEnd, t.endPos);
                int kind = 0;
                switch (t.type) {
                    case Lexer.NUM: kind = H_NUMBER; break;
                    case Lexer.STR: case Lexer.CHR: kind = H_STRING; break;
                    case Lexer.RAW: kind = H_JAVA; break;
                    case Lexer.SYM: kind = H_SYMBOL; break;
                    case Lexer.ID:
                        if (Parser.KEYWORDS.contains(t.norm)) kind = H_KEYWORD;
                        else if (Parser.BUILTIN_TYPES.contains(t.norm) || data.classAlias(t.norm) != null) kind = H_TYPE;
                        else if (data.functions(t.norm) != null || data.sentences(t.norm) != null || (i + 1 < toks.size() && toks.get(i + 1).is("("))) kind = H_CALL;
                        break;
                    default:
                }
                if (kind != 0 && t.endPos > t.pos) { out.add(t.pos); out.add(Math.min(t.endPos, src.length())); out.add(kind); }
            }
        } catch (RuntimeException ignored) { }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static void comments(String s, int from, int to, List<Integer> out) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c == '#' || (c == '/' && i + 1 < s.length() && (s.charAt(i + 1) == '/' || s.charAt(i + 1) == '*'))) {
                boolean blockComment = c == '/' && s.charAt(i + 1) == '*';
                int e = blockComment ? s.indexOf("*/", i + 2) : s.indexOf('\n', i);
                e = e < 0 ? s.length() : blockComment ? e + 2 : e;
                out.add(i); out.add(e); out.add(H_COMMENT);
                i = e;
            }
        }
    }

    /** One suggestion: what is shown, what is inserted (values to fill are written {like this}), a short note. */
    public static final class Suggestion {
        public final String label, insert, note;
        Suggestion(String label, String insert, String note) { this.label = label; this.insert = insert; this.note = note; }
    }

    private static final String[][] SNIPPETS = {
            {"متغير", "متغير {اسم} = {قيمة}"}, {"ثابت", "ثابت {اسم} = {قيمة}"}, {"خاصية", "خاصية {اسم} = {قيمة}"}, {"اجعل", "اجعل {اسم} = {قيمة}"},
            {"زد", "زد {اسم} بمقدار {قيمة}"}, {"انقص", "انقص {اسم} بمقدار {قيمة}"}, {"إذا", "إذا {شرط} {\n    \n}"}, {"وإلا", "وإلا {\n    \n}"},
            {"كرر", "كرر {عدد} مرات {\n    \n}"}, {"لكل", "لكل {عنصر} في {قائمة} {\n    \n}"}, {"طالما", "طالما {شرط} {\n    \n}"},
            {"مهمة", "مهمة {اسم}() {\n    \n}"}, {"ارجع", "ارجع {قيمة}"}, {"بعد", "بعد {عدد} ثواني {\n    \n}"}, {"كل", "كل {عدد} ثواني {\n    \n}"},
            {"حسب", "حسب {قيمة} {\n    حالة {قيمة}: \n    الباقي: \n}"}, {"حاول", "حاول {\n    \n} إن حدث خطأ {\n    \n}"}, {"صنف", "صنف {اسم} {\n    \n}"},
            {"تعداد", "تعداد {اسم}: {قيمة}، {قيمة}"}, {"اخرج", "اخرج"}, {"تخطى", "تخطى"}, {"صح", "صح"}, {"خطأ", "خطأ"}, {"لا_شيء", "لا_شيء"}, {"جديد", "جديد {نوع}()"},
            {"رقم_صحيح", "رقم_صحيح {اسم} = {قيمة}"}, {"رقم_عشري", "رقم_عشري {اسم} = {قيمة}"}, {"نص", "نص {اسم} = \"\""}, {"منطقي", "منطقي {اسم} = صح"},
            {"قائمة", "قائمة من {نوع} {اسم} = []"}, {"قاموس", "قاموس من {نوع} إلى {نوع} {اسم} = جديد قاموس من {نوع} إلى {نوع}()"}, {"جافا", "جافا {\n    \n}"},
    };

    /**
     * Suggestions for the word being typed. kind = "item" / "scene" / "plain"; names = variables and elements of the file.
     * An empty prefix lists the most useful starts.
     */
    public static List<Suggestion> complete(String prefix, String kind, java.util.Collection<String> names) {
        List<Suggestion> out = new ArrayList<>();
        try {
            Data data = Data.get();
            String p = Data.canon(Lexer.norm(prefix == null ? "" : prefix));
            for (Data.Event e : data.events) if (e.kind.equals(kind) && starts(e.phrase, p)) out.add(new Suggestion(e.shown, e.shown + " {\n    \n}", "حدث"));
            for (String[] s : SNIPPETS) if (starts(Data.canon(Lexer.norm(s[0])), p)) out.add(new Suggestion(s[0], s[1], ""));
            if (names != null) for (String n : names) if (!p.isEmpty() && starts(Data.canon(n), p) && !Data.canon(n).equals(p)) out.add(new Suggestion(n, n, "اسم"));
            for (Map.Entry<String, List<Data.Tmpl>> e : data.allSentences().entrySet())
                if (!p.isEmpty() && starts(e.getKey(), p)) for (Data.Tmpl t : e.getValue()) out.add(new Suggestion(t.shown, t.shown, "جملة"));
            if (!p.isEmpty()) {
                for (Map.Entry<String, List<Data.Fn>> e : data.allFunctions().entrySet())
                    if (starts(e.getKey(), p)) { Data.Fn f = e.getValue().get(0); out.add(new Suggestion(f.shown + "(" + (f.arity == 0 ? "" : "…") + ")", f.shown + "(" + holes(f.arity) + ")", "مهمة جاهزة")); }
                for (Map.Entry<String, Data.Global> e : data.allGlobals().entrySet()) if (starts(e.getKey(), p)) out.add(new Suggestion(e.getValue().name, e.getValue().name, "قيمة جاهزة"));
                for (Map.Entry<String, List<Data.Prop>> e : data.allProps().entrySet()) if (starts(e.getKey(), p)) out.add(new Suggestion(e.getKey(), e.getKey() + " {عنصر}", "خاصية"));
                for (Map.Entry<String, String> e : data.classShown.entrySet()) if (starts(Data.canon(Lexer.norm(e.getValue())), p)) out.add(new Suggestion(e.getValue(), e.getValue(), "نوع"));
            }
        } catch (RuntimeException ignored) { }
        return out.size() > 40 ? new ArrayList<>(out.subList(0, 40)) : out;
    }

    private static boolean starts(String full, String prefix) { return prefix.isEmpty() || full.startsWith(prefix); }

    private static String holes(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(i > 0 ? "، " : "").append("{قيمة}");
        return b.toString();
    }

    /** Members of a Java type (as given by {@link Result#names}) with their Arabic names, for the list after a dot. */
    public static synchronized List<Suggestion> members(String javaType, String prefix) {
        List<Suggestion> out = new ArrayList<>();
        try {
            Index ix = index();
            Index.T t = ix.parse(javaType, null, null);
            String p = Data.canon(Lexer.norm(prefix == null ? "" : prefix));
            if (t != null && ix.isA(t, "com.star4droid.template.Utils.PlayerItem"))
                for (Map.Entry<String, List<Data.Prop>> e : Data.get().allProps().entrySet())
                    if (starts(e.getKey(), p) && !e.getValue().get(0).recv.equals("*")) out.add(new Suggestion(e.getKey(), e.getKey(), "خاصية"));
            for (String[] m : ix.membersOf(t)) {
                boolean method = !m[2].isEmpty();
                if (!starts(Data.canon(m[0]), p) && !m[1].toLowerCase().startsWith(p.toLowerCase())) continue;
                int argc = method ? Integer.parseInt(m[2]) : 0;
                out.add(new Suggestion(m[0] + (method ? "(" + (argc == 0 ? "" : "…") + ")" : ""), m[0] + (method ? "(" + holes(argc) + ")" : ""), m[1]));
                if (out.size() >= 60) break;
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    // ------------------------------------------------------------------ Java compiler messages in Arabic
    private static final String[][] MESSAGES = {
            {"(\\S+) cannot be resolved to a variable", "لم أجد شيئاً اسمه «$1». هل كتبت الاسم صحيحاً؟"},
            {"(\\S+) cannot be resolved to a type", "لا يوجد نوع اسمه «$1»"},
            {"(\\S+) cannot be resolved$", "لم أجد «$1»"},
            {"cannot find symbol.*symbol:\\s+(?:variable|class|method) (\\S+).*", "لم أجد شيئاً اسمه «$1»"},
            {"The method (\\S+)\\((.*)\\) is undefined for the type (\\S+)", "لا توجد مهمة باسم «$1» في «$3»"},
            {"The method (\\S+)\\((.*)\\) in the type (\\S+) is not applicable for the arguments \\((.*)\\)", "المدخلات لا تناسب المهمة «$1». هي تحتاج ($2) وأنت أعطيتها ($4)"},
            {"Type mismatch: cannot convert from (\\S+) to (\\S+)", "النوع لا يناسب: القيمة من نوع $1 والمطلوب $2"},
            {"incompatible types: (\\S+) cannot be converted to (\\S+)", "النوع لا يناسب: القيمة من نوع $1 والمطلوب $2"},
            {"incompatible types: possible lossy conversion from (\\S+) to (\\S+)", "لا يمكن وضع $1 في $2 مباشرة. استخدم رقم_صحيح(...) أو رقم_عشري(...)"},
            {"The local variable (\\S+) may not have been initialized", "المتغير «$1» ليس له قيمة بعد. أعطه قيمة عند تعريفه"},
            {"Duplicate local variable (\\S+)", "يوجد متغير آخر بالاسم «$1»"},
            {"Duplicate field (\\S+)", "يوجد متغير آخر بالاسم «$1» في هذا الملف"},
            {"Duplicate method (\\S+)\\((.*)\\) in type (\\S+)", "توجد مهمة أو حدث آخر بنفس الاسم «$1»"},
            {"Unreachable code", "هذا الأمر لن يُنفذ أبداً (يأتي بعد ارجع أو اخرج)"},
            {"This method must return a result of type (\\S+)", "هذه المهمة يجب أن ترجع قيمة من نوع $1 في كل الحالات"},
            {"Void methods cannot return a value", "هذه المهمة لا ترجع قيمة"},
            {"The operator (\\S+) is undefined for the argument type\\(s\\) (.*)", "العملية $1 لا تعمل مع هذه الأنواع: $2"},
            {"Cannot make a static reference to the non-static (?:field|method) (\\S+)", "«$1» ليس مشتركاً، يحتاج كائناً"},
            {"The final local variable (\\S+) cannot be assigned.*", "«$1» ثابت، لا يمكن تغيير قيمته"},
            {"The final field (\\S+) cannot be assigned", "«$1» ثابت، لا يمكن تغيير قيمته"},
            {"Unhandled exception type (\\S+)", "هذا الأمر قد يسبب خطأ $1. ضعه داخل: حاول { } إن حدث خطأ { }"},
            {"The constructor (\\S+)\\((.*)\\) is undefined", "لا يمكن إنشاء «$1» بهذه المدخلات ($2)"},
            {"(\\S+) cannot be resolved or is not a field", "لم أجد «$1» في هذا النوع"},
            {"Syntax error.*", "خطأ في كتابة الجملة"},
            {"The type (\\S+) is already defined", "يوجد صنف آخر بالاسم «$1»"},
            {"Cannot cast from (\\S+) to (\\S+)", "لا يمكن تحويل $1 إلى $2"},
    };
    private static final String[][] TYPES = {{"java.lang.String", "نص"}, {"String", "نص"}, {"int", "رقم_صحيح"}, {"float", "رقم_عشري"}, {"double", "عشري_دقيق"},
            {"boolean", "منطقي"}, {"long", "رقم_طويل"}, {"char", "حرف"}, {"void", "لا_ناتج"}, {"PlayerItem", "عنصر"}, {"Object", "كائن"}};

    /** A message of ecj / javac about generated code, in Arabic (unknown messages are returned unchanged). */
    public static String translate(String message) {
        if (message == null) return "";
        String head = message.trim();
        for (String[] m : MESSAGES) {
            Matcher x = Pattern.compile(m[0], Pattern.DOTALL).matcher(head);
            if (!x.find()) continue;
            String out = m[1];
            for (int g = x.groupCount(); g >= 1; g--) {
                String v = x.group(g) == null ? "" : x.group(g);
                for (String[] t : TYPES) v = v.replaceAll("(?<![\\w.])" + Pattern.quote(t[0]) + "(?![\\w.])", t[1]);
                v = v.replaceAll("\\b[a-z][\\w]*\\.(?=[\\w.]*[A-Z])", "");
                out = out.replace("$" + g, Matcher.quoteReplacement(v).replace("\\$", "$"));
            }
            return out;
        }
        return message;
    }
}
