package com.star4droid.star2d.ui.panels;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Makes imported OBJ models load: unpacks .zip files (obj, mtl, textures), finds every .mtl an .obj names (relative,
 * absolute Windows paths, other case or another sub folder) and every texture an .mtl names (map_Kd, map_Bump, bump,
 * norm, ... with options), copies them next to the file that uses them under a safe name (no spaces) and rewrites the
 * .obj / .mtl lines. Runs on the import folder before the files are moved into the project.
 */
public final class ObjFix {
    private ObjFix() {}

    static final String[] ZIP_OK = {".obj", ".mtl", ".png", ".jpg", ".jpeg", ".bmp", ".webp", ".gif", ".tga", ".glb", ".gltf", ".bin"};

    /** Unpack zips, then fix every .obj below dir. Returns a short log (for errors / debugging). */
    public static String fixFolder(File dir) {
        StringBuilder log = new StringBuilder();
        try { unzipAll(dir, 0, log); } catch (Exception e) { log.append("zip: ").append(e).append('\n'); }
        List<File> objs = new ArrayList<>();
        collect(dir, ".obj", objs, 0);
        for (File o : objs) {
            try { fixObj(o, dir, log); } catch (Exception e) { log.append(o.getName()).append(": ").append(e).append('\n'); }
        }
        return log.toString();
    }

    static void unzipAll(File dir, int depth, StringBuilder log) throws Exception {
        File[] l = dir.listFiles();
        if (l == null || depth > 5) return;
        for (File f : l) {
            if (f.isDirectory()) { unzipAll(f, depth + 1, log); continue; }
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
            String base = f.getName().substring(0, f.getName().length() - 4).replace(' ', '_');
            File out = new File(f.getParentFile(), base);
            int count = 0;
            long total = 0;
            try (ZipInputStream z = new ZipInputStream(new FileInputStream(f))) {
                ZipEntry e;
                byte[] b = new byte[65536];
                while ((e = z.getNextEntry()) != null) {
                    String n = e.getName().replace('\\', '/');
                    if (e.isDirectory() || n.contains("..") || n.startsWith("/") || n.startsWith("__MACOSX")) continue;
                    String low = n.toLowerCase(Locale.ROOT);
                    boolean ok = false;
                    for (String x : ZIP_OK) if (low.endsWith(x)) ok = true;
                    if (!ok || ++count > 500) continue;
                    File to = new File(out, n);
                    if (!to.getCanonicalPath().startsWith(out.getCanonicalPath())) continue;
                    to.getParentFile().mkdirs();
                    try (FileOutputStream os = new FileOutputStream(to)) {
                        int r;
                        while ((r = z.read(b)) > 0) {
                            total += r;
                            if (total > 400L * 1024 * 1024) throw new Exception("zip too large");
                            os.write(b, 0, r);
                        }
                    }
                }
            }
            f.delete();
            log.append("unzipped ").append(f.getName()).append(" (").append(count).append(")\n");
        }
    }

    static void collect(File dir, String ext, List<File> out, int depth) {
        File[] l = dir.listFiles();
        if (l == null || depth > 6) return;
        for (File f : l) {
            if (f.isDirectory()) collect(f, ext, out, depth + 1);
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(ext)) out.add(f);
        }
    }

    /** Safe file name: spaces and odd characters become '_' (keeps the extension). */
    public static String safe(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() ? "file" : s;
    }

    /** A referenced file: relative to base, else the same name (any case) anywhere under root. */
    static File resolve(String ref, File base, File root) {
        String r = ref.trim().replace('\\', '/');
        if (r.startsWith("\"") && r.endsWith("\"") && r.length() > 1) r = r.substring(1, r.length() - 1);
        File f = new File(base, r);
        if (f.isFile()) return f;
        String name = r.substring(r.lastIndexOf('/') + 1);
        File same = new File(base, name);
        if (same.isFile()) return same;
        return find(root, name.toLowerCase(Locale.ROOT), 0);
    }

    static File find(File dir, String lowName, int depth) {
        File[] l = dir.listFiles();
        if (l == null || depth > 6) return null;
        for (File f : l) if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).equals(lowName)) return f;
        // "My Model.mtl" referenced as "My_Model.mtl" (or the other way round)
        String safeName = safe(lowName);
        for (File f : l) if (f.isFile() && safe(f.getName().toLowerCase(Locale.ROOT)).equals(safeName)) return f;
        for (File f : l) if (f.isDirectory()) { File r = find(f, lowName, depth + 1); if (r != null) return r; }
        return null;
    }

    /** Copies found into dir under a safe name (if it is not already there with that name); returns the new name. */
    static String place(File found, File dir) throws Exception {
        String safe = safe(found.getName());
        File to = new File(dir, safe);
        if (to.getCanonicalPath().equals(found.getCanonicalPath())) return safe;
        if (!to.exists() || to.length() != found.length()) com.star4droid.star2d.suda.Store.copy(found, to);
        return safe;
    }

    static void fixObj(File obj, File root, StringBuilder log) throws Exception {
        List<String> lines = readLines(obj, 400_000);
        boolean changed = false;
        if (lines == null) { // big file: only look at the mtllib lines by streaming
            changed = rewriteStreaming(obj, root, log);
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (!l.startsWith("mtllib")) continue;
            String rest = l.substring(6).trim();
            String fixed = fixMtlRefs(rest, obj.getParentFile(), root, log);
            if (!fixed.equals(rest)) { lines.set(i, "mtllib " + fixed); changed = true; }
        }
        if (changed) writeLines(obj, lines);
    }

    /** "a b.mtl" may be one name with a space or two files: try the whole name first. */
    static String fixMtlRefs(String rest, File dir, File root, StringBuilder log) throws Exception {
        File whole = resolve(rest, dir, root);
        List<String> names = new ArrayList<>();
        if (whole != null) names.add(fixMtl(whole, dir, root, log));
        else for (String part : rest.split("\\s+")) {
            File f = resolve(part, dir, root);
            if (f != null) names.add(fixMtl(f, dir, root, log));
            else log.append("missing mtl ").append(part).append('\n');
        }
        return names.isEmpty() ? rest : String.join(" ", names);
    }

    static String fixMtl(File mtl, File objDir, File root, StringBuilder log) throws Exception {
        String name = place(mtl, objDir);
        File local = new File(objDir, name);
        List<String> lines = readLines(local, 50_000);
        if (lines == null) return name;
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            String low = l.toLowerCase(Locale.ROOT);
            if (!(low.startsWith("map_") || low.startsWith("bump ") || low.startsWith("disp ") || low.startsWith("decal ") || low.startsWith("norm ") || low.startsWith("refl "))) continue;
            String[] tok = l.split("\\s+");
            if (tok.length < 2) continue;
            // skip options: -x followed by its numeric / on|off arguments
            int k = 1;
            StringBuilder opts = new StringBuilder();
            while (k < tok.length - 1 && tok[k].startsWith("-")) {
                opts.append(tok[k]).append(' ');
                k++;
                while (k < tok.length - 1 && (isNum(tok[k]) || tok[k].equalsIgnoreCase("on") || tok[k].equalsIgnoreCase("off")
                        || (tok[k - 1].equalsIgnoreCase("-imfchan") && tok[k].length() == 1))) { opts.append(tok[k]).append(' '); k++; }
            }
            StringBuilder ref = new StringBuilder();
            for (int j = k; j < tok.length; j++) ref.append(j > k ? " " : "").append(tok[j]);
            File tex = resolve(ref.toString(), local.getParentFile(), root);
            if (tex == null) { log.append("missing texture ").append(ref).append('\n'); continue; }
            String tn = place(tex, local.getParentFile());
            // the engine's MTL reader takes the token right after the keyword as the file: options are dropped
            String nl = tok[0] + " " + tn;
            if (!nl.equals(l)) { lines.set(i, nl); changed = true; }
        }
        if (changed) writeLines(local, lines);
        return name;
    }

    static boolean isNum(String s) {
        try { Double.parseDouble(s); return true; } catch (Exception e) { return false; }
    }

    static boolean rewriteStreaming(File obj, File root, StringBuilder log) throws Exception {
        File tmp = new File(obj.getPath() + ".tmp");
        boolean changed = false;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(obj), StandardCharsets.UTF_8));
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("mtllib")) {
                    String rest = l.substring(6).trim();
                    String fixed = fixMtlRefs(rest, obj.getParentFile(), root, log);
                    if (!fixed.equals(rest)) { l = "mtllib " + fixed; changed = true; }
                }
                w.write(l);
                w.write('\n');
            }
        }
        if (changed) { obj.delete(); tmp.renameTo(obj); } else tmp.delete();
        return changed;
    }

    /** All lines, or null when the file has more than max lines. */
    static List<String> readLines(File f, int max) throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                out.add(l);
                if (out.size() > max) return null;
            }
        }
        return out;
    }

    static void writeLines(File f, List<String> lines) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            for (String l : lines) { w.write(l); w.write('\n'); }
        }
    }
}
