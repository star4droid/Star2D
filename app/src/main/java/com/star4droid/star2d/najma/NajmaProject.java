package com.star4droid.star2d.najma;

import com.star4droid.najma.Najma;
import com.star4droid.star2d.CodeEditor.smart.BuildError;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Najma (.njm, the Arabic language) inside a project. A .njm file lives where its Java class would live
 * (java/.../XScript.njm) and the editor writes XScript.java next to it: generated, same line numbers, first
 * characters {@link Najma#MARK}. Everything else in the engine (script attach, variables in Properties, compile,
 * export) keeps working on the .java file. Pure Java, no Android classes.
 */
public final class NajmaProject {
    private NajmaProject() {}

    public static boolean isNajma(String path) { return path != null && path.endsWith(Najma.EXT); }

    /** X.java -> X.njm when that file exists, else null. */
    public static String sourceOf(String javaPath) {
        if (javaPath == null || !javaPath.endsWith(".java")) return null;
        File f = new File(javaPath.substring(0, javaPath.length() - 5) + Najma.EXT);
        return f.isFile() ? f.getAbsolutePath() : null;
    }

    public static String javaOf(String njmPath) { return njmPath.substring(0, njmPath.length() - Najma.EXT.length()) + ".java"; }

    /** The project's "java" folder that contains the file, or null. */
    public static String javaRoot(String path) {
        String p = path.replace('\\', '/');
        int i = p.lastIndexOf("/java/com/");
        if (i < 0) i = p.lastIndexOf("/java/");
        return i < 0 ? null : p.substring(0, i + 5);
    }

    public static String read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static void write(File f, String text) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(text.getBytes("UTF-8")); }
        } catch (Exception ignored) { }
    }

    /** Options of a .njm file: kind and class from its path, element names from the scene's generated Refs class. */
    public static Najma.Options options(String javaRoot, String njmPath) {
        String rel = njmPath.replace('\\', '/').substring(javaRoot.replace('\\', '/').length());
        Najma.Options o = Najma.optionsFor(rel);
        if (o.superClass != null) {
            File refs = new File(javaRoot, o.superClass.replace('.', '/') + ".java");
            if (refs.isFile()) {
                Map<String, String> els = new LinkedHashMap<>();
                Matcher m = Pattern.compile("public\\s+([\\w.]+)\\s+([^\\s;=(]+)\\s*;").matcher(read(refs));
                while (m.find()) els.put(m.group(2), m.group(1));
                // names the base script class already uses are not fields of the Refs class but stay reachable
                o.elements = els;
            } else { // the scene was never built: do not fail the script because of it
                o.superClass = o.baseType;
            }
        }
        return o;
    }

    /** Compiles one .njm file and (when it has no errors) writes its .java file. */
    public static Najma.Result compileFile(String javaRoot, String njmPath) {
        Najma.Result r = Najma.compile(read(new File(njmPath)), options(javaRoot, njmPath));
        if (r.ok()) {
            File out = new File(javaOf(njmPath));
            if (!out.isFile() || !read(out).equals(r.java)) write(out, r.java);
            ensureRuntime(javaRoot);
        }
        return r;
    }

    /** najma/N.java (the helper class generated code calls) is part of every project that has a .njm file. */
    public static void ensureRuntime(String javaRoot) {
        File n = new File(javaRoot, "najma/N.java");
        String src = Najma.runtimeSource();
        if (!n.isFile() || !read(n).equals(src)) write(n, src);
    }

    /**
     * Before a build: every .njm file under the java folder is turned into its .java file. Generated .java files whose
     * .njm is gone are deleted. Returns the errors (empty = fine).
     */
    public static List<BuildError> sync(String javaRoot) {
        List<BuildError> errors = new ArrayList<>();
        List<File> njm = new ArrayList<>(), java = new ArrayList<>();
        collect(new File(javaRoot), njm, java);
        for (File j : java) {
            if (j.getPath().replace('\\', '/').endsWith("/najma/N.java")) continue;
            if (sourceOf(j.getAbsolutePath()) != null || !isGenerated(j)) continue;
            // a generated file without its .njm: the element (and its script file) was renamed, so the single .njm of
            // that folder that lost its .java follows it; otherwise the .njm was deleted and the .java goes too
            File moved = null;
            int lost = 0;
            for (File s : njm) if (s.getParentFile().equals(j.getParentFile()) && !new File(javaOf(s.getAbsolutePath())).isFile()) { moved = s; lost++; }
            if (lost == 1) {
                File to = new File(j.getAbsolutePath().substring(0, j.getAbsolutePath().length() - 5) + Najma.EXT);
                if (moved.renameTo(to)) { njm.remove(moved); njm.add(to); continue; }
            }
            j.delete();
        }
        if (njm.isEmpty()) return errors;
        ensureRuntime(javaRoot);
        for (File f : njm) {
            Najma.Result r = compileFile(javaRoot, f.getAbsolutePath());
            for (Najma.Diag d : r.errors) {
                BuildError e = new BuildError(f.getAbsolutePath(), d.line, d.col, d.message);
                e.target = BuildError.TARGET_JAVA;
                e.targetFile = f.getAbsolutePath();
                e.targetLine = d.line;
                e.targetColumn = d.col;
                e.targetEndColumn = d.endCol;
                e.origin = f.getName();
                errors.add(e);
            }
        }
        return errors;
    }

    private static void collect(File dir, List<File> njm, List<File> java) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, njm, java);
            else if (f.getName().endsWith(Najma.EXT)) njm.add(f);
            else if (f.getName().endsWith(".java")) java.add(f);
        }
    }

    public static boolean isGenerated(File javaFile) {
        try (FileInputStream in = new FileInputStream(javaFile)) {
            byte[] b = new byte[Najma.MARK.length()];
            return in.read(b) == b.length && new String(b, "UTF-8").equals(Najma.MARK);
        } catch (Exception e) {
            return false;
        }
    }

    /** Java compiler errors on a generated file point at the .njm file: same line, Arabic message. */
    public static void mapErrors(List<BuildError> errors) {
        for (BuildError e : errors) {
            String src = sourceOf(e.file);
            if (src == null) continue;
            e.target = BuildError.TARGET_JAVA;
            e.targetFile = src;
            e.targetLine = e.line;
            e.targetColumn = 1;
            e.targetEndColumn = -1;
            e.message = Najma.translate(e.headline());
            e.origin = new File(src).getName();
        }
    }

    public static String describe(List<BuildError> errors) {
        StringBuilder b = new StringBuilder("نجمة: ").append(errors.size()).append(errors.size() == 1 ? " خطأ" : " أخطاء").append('\n');
        for (BuildError e : errors) b.append(new File(e.file).getName()).append(" سطر ").append(e.line).append(": ").append(e.message).append('\n');
        return b.toString();
    }

    /** First text of a new script in Najma. kind: "item" or "scene". */
    public static String template(String kind) {
        if ("scene".equals(kind))
            return "# سكربت المشهد بلغة نجمة\n# كل عنصر في المشهد تكتبه باسمه مباشرة\n\nمتغير النقاط = ٠\n\nعند البدء {\n    \n}\n\nكل إطار {\n    \n}\n";
        return "# سكربت العنصر بلغة نجمة\n# «نفسي» هو هذا العنصر، وبقية العناصر تكتبها بأسمائها\n\nخاصية السرعة = ٢٠٠\n\nعند البدء {\n    \n}\n\nكل إطار {\n    \n}\n\nعند النقر {\n    \n}\n\nعند الاصطدام {\n    # «الآخر» هو العنصر الذي اصطدمت به\n    \n}\n";
    }
}
