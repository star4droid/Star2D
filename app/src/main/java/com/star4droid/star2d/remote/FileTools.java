package com.star4droid.star2d.remote;

import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.ui.Bridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** File tools: files (batch), create_image, create_sound, import_asset. Worker thread (file IO), GL only to refresh. */
final class FileTools {
    private FileTools() {}

    private static final int MAX_READ = 60_000;
    private static final long MAX_WRITE = 2_000_000;
    private static final long MAX_DOWNLOAD = 25_000_000;

    private static Project project() throws Exception {
        return Gl.run(Proj::project);
    }

    /** Folders whose change must reach the open editor. */
    private static boolean touchesScene(String rel) {
        String r = rel.replace('\\', '/');
        return r.startsWith("scenes/") || r.startsWith("configs/") || r.startsWith("joints/") || r.startsWith("Events/") && r.endsWith("variables.json");
    }

    private static boolean touchesAssets(String rel) {
        String r = rel.replace('\\', '/');
        return r.startsWith("images/") || r.startsWith("sounds/") || r.startsWith("anims/") || r.startsWith("files/");
    }

    private static void guard(Project p, File f) throws Exception {
        File root = new File(p.getPath()).getCanonicalFile();
        String rel = Proj.rel(p, f);
        if (f.getCanonicalPath().equals(root.getPath())) throw new Gl.ToolError("The project root itself cannot be changed");
        if (rel.equals(".git") || rel.startsWith(".git/")) throw new Gl.ToolError("The .git folder is managed by the git tool");
    }

    /** Snapshot taken before changing files, applied after. */
    private static HashMap<String, String> before() throws Exception {
        return Gl.run(() -> {
            // unsaved edits of the open scene must be on disk before a reload
            if (Bridge.ready() && !Bridge.editor().isLoading()) Bridge.editor().getProject().save(Bridge.editor());
            return Bridge.app().openSceneFingerprints();
        });
    }

    private static String after(HashMap<String, String> before) throws Exception {
        return Gl.run(() -> Bridge.app().applyExternalChanges(before));
    }

    static ToolResult files(JsonObject a) throws Exception {
        final Project p = project();
        final JsonArray ops = EditTools.opsOf(a);
        boolean mutating = false;
        for (JsonElement e : ops) {
            String op = CoreTools.s(e.getAsJsonObject(), "op");
            if (op.equals("write") || op.equals("edit") || op.equals("delete") || op.equals("move") || op.equals("copy") || op.equals("mkdir")) mutating = true;
        }
        HashMap<String, String> snap = mutating ? before() : null;
        JsonArray results = new JsonArray();
        boolean reload = false;
        for (JsonElement e : ops) {
            JsonObject o = e.getAsJsonObject();
            String op = CoreTools.s(o, "op"), path = CoreTools.s(o, "path");
            JsonObject r = new JsonObject();
            r.addProperty("op", op);
            r.addProperty("path", path);
            try {
                File f = Proj.safe(p, path.isEmpty() ? "." : path);
                switch (op) {
                    case "list": {
                        if (!f.isDirectory()) throw new Gl.ToolError("not a folder: " + path);
                        boolean rec = CoreTools.b(o, "recursive", false);
                        JsonArray ents = new JsonArray();
                        list(p, f, rec, ents, 400);
                        r.add("entries", ents);
                        break;
                    }
                    case "read": {
                        if (!f.isFile()) throw new Gl.ToolError("file not found: " + path);
                        long len = f.length();
                        if (looksBinary(f)) {
                            r.addProperty("binary", true);
                            r.addProperty("size", len);
                            break;
                        }
                        String txt = new String(readBytes(f, MAX_READ * 4L), StandardCharsets.UTF_8);
                        if (o.has("from_line") || o.has("to_line")) {
                            // numbered lines: cheap way to look at one part of a big script
                            String[] lines = txt.split("\n", -1);
                            int from = Math.max(1, CoreTools.i(o, "from_line", 1));
                            int to = Math.min(lines.length, CoreTools.i(o, "to_line", from + 119));
                            if (to - from > 399) to = from + 399;
                            StringBuilder sb = new StringBuilder();
                            for (int ln = from; ln <= to; ln++) sb.append(ln).append(": ").append(lines[ln - 1]).append('\n');
                            r.addProperty("content", sb.toString());
                            r.addProperty("lines", lines.length);
                            if (to < lines.length) r.addProperty("next_from_line", to + 1);
                            break;
                        }
                        int off = Math.max(0, CoreTools.i(o, "offset", 0));
                        if (off > txt.length()) off = txt.length();
                        String part = txt.substring(off, Math.min(txt.length(), off + MAX_READ));
                        r.addProperty("content", part);
                        if (off + MAX_READ < txt.length() || len > MAX_READ * 4L) {
                            r.addProperty("truncated", true);
                            r.addProperty("next_offset", off + part.length());
                        }
                        break;
                    }
                    case "edit": {
                        guard(p, f);
                        if (!f.isFile()) throw new Gl.ToolError("file not found: " + path);
                        if (looksBinary(f)) throw new Gl.ToolError("binary file: cannot edit");
                        String text = new String(readBytes(f, MAX_WRITE), StandardCharsets.UTF_8);
                        text = applyEdits(text, o.has("edits") && o.get("edits").isJsonArray() ? o.getAsJsonArray("edits") : singleEdit(o));
                        try (OutputStream out = new FileOutputStream(f)) {
                            out.write(text.getBytes(StandardCharsets.UTF_8));
                        }
                        r.addProperty("ok", true);
                        reload |= touchesScene(Proj.rel(p, f)) || touchesAssets(Proj.rel(p, f));
                        break;
                    }
                    case "search": {
                        searchInto(p, f, o, r);
                        break;
                    }
                    case "write": {
                        guard(p, f);
                        String content = CoreTools.s(o, "content");
                        if (content.length() > MAX_WRITE) throw new Gl.ToolError("content too large (max 2 MB); split it");
                        File parent = f.getParentFile();
                        if (parent != null) parent.mkdirs();
                        try (OutputStream out = new FileOutputStream(f)) {
                            out.write(content.getBytes(StandardCharsets.UTF_8));
                        }
                        r.addProperty("ok", true);
                        reload |= touchesScene(Proj.rel(p, f)) || touchesAssets(Proj.rel(p, f));
                        break;
                    }
                    case "mkdir":
                        guard(p, f);
                        f.mkdirs();
                        r.addProperty("ok", f.isDirectory());
                        break;
                    case "delete": {
                        guard(p, f);
                        if (!f.exists()) throw new Gl.ToolError("not found: " + path);
                        reload |= touchesScene(Proj.rel(p, f)) || touchesAssets(Proj.rel(p, f));
                        deleteRec(f);
                        r.addProperty("ok", true);
                        break;
                    }
                    case "move":
                    case "copy": {
                        String to = CoreTools.s(o, "to");
                        File t = Proj.safe(p, to);
                        guard(p, f);
                        guard(p, t);
                        if (!f.exists()) throw new Gl.ToolError("not found: " + path);
                        if (t.exists() && !CoreTools.b(o, "overwrite", true)) throw new Gl.ToolError("target exists: " + to);
                        copyRec(f, t);
                        if (op.equals("move")) deleteRec(f);
                        reload |= touchesScene(Proj.rel(p, t)) || touchesScene(Proj.rel(p, f)) || touchesAssets(Proj.rel(p, t)) || touchesAssets(Proj.rel(p, f));
                        r.addProperty("ok", true);
                        break;
                    }
                    default:
                        throw new Gl.ToolError("unknown op '" + op + "' (list|read|search|edit|write|delete|move|copy|mkdir)");
                }
            } catch (Gl.ToolError t) {
                r.addProperty("error", t.getMessage());
            } catch (Exception t) {
                r.addProperty("error", String.valueOf(t));
            }
            results.add(r);
        }
        if (snap != null && reload) {
            String rep = after(snap);
            JsonObject r = new JsonObject();
            r.addProperty("editor", rep.isEmpty() ? "refreshed" : rep);
            results.add(r);
        }
        return ToolResult.text(results.toString());
    }

    // ------------------------------------------------------------------ edit / search helpers

    static JsonArray singleEdit(JsonObject o) {
        JsonArray arr = new JsonArray();
        if (o.has("old")) {
            JsonObject e = new JsonObject();
            e.addProperty("old", CoreTools.s(o, "old"));
            e.addProperty("new", CoreTools.s(o, "new"));
            if (o.has("all")) e.add("all", o.get("all"));
            arr.add(e);
        }
        return arr;
    }

    static int lineOf(String text, int index) {
        int n = 1;
        for (int i = 0; i < index && i < text.length(); i++) if (text.charAt(i) == '\n') n++;
        return n;
    }

    /** All edits in order on the in-memory text; any failure throws and nothing is written (atomic). Each {old,new,all?}: old must match exactly once unless all. */
    static String applyEdits(String text, JsonArray edits) {
        if (edits == null || edits.size() == 0) throw new Gl.ToolError("edits is empty: give [{old,new}] (old = exact text to replace)");
        for (int i = 0; i < edits.size(); i++) {
            JsonObject e = edits.get(i).getAsJsonObject();
            String old = CoreTools.s(e, "old");
            String neu = e.has("new") && !e.get("new").isJsonNull() ? e.get("new").getAsString() : "";
            if (old.isEmpty()) throw new Gl.ToolError("edit " + i + ": 'old' is empty");
            int count = 0, at = -1, from = 0;
            StringBuilder lineNos = new StringBuilder();
            while ((from = text.indexOf(old, from)) >= 0) {
                if (count == 0) at = from;
                if (count < 6) lineNos.append(lineNos.length() == 0 ? "" : ", ").append(lineOf(text, from));
                count++;
                from += old.length();
            }
            if (count == 0) throw new Gl.ToolError("edit " + i + ": 'old' text not found (nothing was changed). " + nearest(text, old));
            boolean all = e.has("all") && e.get("all").getAsBoolean();
            if (count > 1 && !all)
                throw new Gl.ToolError("edit " + i + ": 'old' matches " + count + " times (lines " + lineNos + ") - make it longer/unique or set all:true (nothing was changed)");
            text = all ? text.replace(old, neu) : text.substring(0, at) + neu + text.substring(at + old.length());
        }
        return text;
    }

    /** A hint for a failed match: the line that looks most like the first line of 'old'. */
    private static String nearest(String text, String old) {
        String key = old.trim().split("\n")[0].trim();
        if (key.length() > 60) key = key.substring(0, 60);
        if (key.isEmpty()) return "";
        String[] lines = text.split("\n", -1);
        String best = null;
        int bestAt = -1, bestScore = 0;
        String k = key.replaceAll("\\s+", " ").toLowerCase();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim().replaceAll("\\s+", " ").toLowerCase();
            if (l.isEmpty()) continue;
            int score = 0;
            if (l.contains(k) || k.contains(l)) score = 1000 + Math.min(l.length(), k.length());
            else {
                int n = Math.min(l.length(), k.length());
                while (score < n && l.charAt(score) == k.charAt(score)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = lines[i];
                bestAt = i + 1;
            }
        }
        if (best == null || bestScore < 6) return "";
        String show = best.length() > 160 ? best.substring(0, 160) + "…" : best;
        return "Closest line " + bestAt + ": " + show + "  (check spaces/tabs/line breaks; use files search or read with from_line/to_line)";
    }

    private static void searchInto(Project p, File f, JsonObject o, JsonObject r) throws Exception {
        String pattern = CoreTools.s(o, "pattern");
        if (pattern.isEmpty()) throw new Gl.ToolError("pattern is empty");
        boolean regex = CoreTools.b(o, "regex", false), ci = CoreTools.b(o, "ignore_case", true);
        int ctx = Math.max(0, Math.min(5, CoreTools.i(o, "context", 0))), max = Math.max(1, Math.min(120, CoreTools.i(o, "max", 40)));
        java.util.regex.Pattern pat;
        try {
            pat = java.util.regex.Pattern.compile(regex ? pattern : java.util.regex.Pattern.quote(pattern), ci ? java.util.regex.Pattern.CASE_INSENSITIVE : 0);
        } catch (Exception e) {
            throw new Gl.ToolError("bad regex: " + e.getMessage());
        }
        java.util.ArrayList<File> files = new java.util.ArrayList<>();
        if (f.isFile()) files.add(f);
        else if (f.isDirectory()) collectFiles(f, files, 600);
        else throw new Gl.ToolError("not found: " + CoreTools.s(o, "path"));
        JsonArray hits = new JsonArray();
        int total = 0;
        outer:
        for (File x : files) {
            if (looksBinary(x) || x.length() > 1_500_000) continue;
            String[] lines = new String(readBytes(x, 1_500_000), StandardCharsets.UTF_8).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (!pat.matcher(lines[i]).find()) continue;
                total++;
                if (hits.size() >= max) continue;
                StringBuilder sb = new StringBuilder();
                for (int k = Math.max(0, i - ctx); k <= Math.min(lines.length - 1, i + ctx); k++) {
                    String l = lines[k].length() > 200 ? lines[k].substring(0, 200) + "…" : lines[k];
                    sb.append(k == i ? "> " : "  ").append(k + 1).append(": ").append(l).append('\n');
                }
                JsonObject h = new JsonObject();
                h.addProperty("file", Proj.rel(p, x));
                h.addProperty("line", i + 1);
                h.addProperty("text", ctx == 0 ? lines[i].trim() : sb.toString().replaceAll("\\s+$", ""));
                hits.add(h);
                if (total > 5000) break outer;
            }
        }
        r.add("matches", hits);
        r.addProperty("total", total);
        if (total > hits.size()) r.addProperty("note", "more matches than shown: refine the pattern or raise max (<=120)");
    }

    private static void collectFiles(File dir, java.util.List<File> out, int max) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        for (File x : fs) {
            if (out.size() >= max) return;
            if (x.getName().equals(".git")) continue;
            if (x.isDirectory()) collectFiles(x, out, max);
            else out.add(x);
        }
    }

    private static void list(Project p, File dir, boolean rec, JsonArray out, int max) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        for (File f : fs) {
            if (out.size() >= max) return;
            if (f.getName().equals(".git")) continue;
            JsonObject e = new JsonObject();
            e.addProperty("path", Proj.rel(p, f) + (f.isDirectory() ? "/" : ""));
            if (f.isFile()) e.addProperty("size", f.length());
            out.add(e);
            if (rec && f.isDirectory()) list(p, f, true, out, max);
        }
    }

    private static boolean looksBinary(File f) throws Exception {
        String n = f.getName().toLowerCase();
        for (String ext : new String[]{".png", ".jpg", ".jpeg", ".gif", ".wav", ".mp3", ".ogg", ".dex", ".jar", ".zip", ".ttf", ".otf", ".atlas.png"})
            if (n.endsWith(ext)) return true;
        byte[] head = readBytes(f, 512);
        for (byte b : head) if (b == 0) return true;
        return false;
    }

    static byte[] readBytes(File f, long max) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0 && bo.size() < max) bo.write(buf, 0, r);
            return bo.toByteArray();
        }
    }

    static void deleteRec(File f) {
        File[] c = f.listFiles();
        if (c != null) for (File x : c) deleteRec(x);
        f.delete();
    }

    private static void copyRec(File from, File to) throws Exception {
        if (from.isDirectory()) {
            to.mkdirs();
            File[] c = from.listFiles();
            if (c != null) for (File x : c) copyRec(x, new File(to, x.getName()));
        } else {
            File parent = to.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
        }
    }

    // ================================================================== assets

    /** Accepts "images/x.png", "/images/x.png", "/x.png" and "x.png": a bare name goes into the first allowed folder. */
    private static File assetTarget(Project p, String path, String... folders) throws Exception {
        String given = path;
        String clean = path.trim().replace('\\', '/').replaceFirst("^/+", "");
        boolean inFolder = false;
        for (String d : folders) if (clean.toLowerCase().startsWith(d.toLowerCase() + "/")) inFolder = true;
        if (!inFolder && folders.length == 1) clean = folders[0] + "/" + clean;
        File f = Proj.safe(p, clean);
        guard(p, f);
        String rel = Proj.rel(p, f);
        boolean ok = false;
        for (String d : folders) if (rel.startsWith(d + "/")) ok = true;
        if (!ok) throw new Gl.ToolError("Cannot place '" + given + "' inside " + Arrays.toString(folders) + " (resolved to '" + rel
                + "'). Use a plain name like 'hero.png' or '" + folders[0] + "/hero.png'. See read_docs {topic:'assets', section:'paths'}.");
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        return f;
    }

    private static String refreshAssets() throws Exception {
        return Gl.run(() -> Bridge.app().applyExternalChanges(Bridge.app().openSceneFingerprints()));
    }

    static ToolResult createImage(JsonObject a) throws Exception {
        Project p = project();
        String path = CoreTools.s(a, "path");
        if (!path.toLowerCase().endsWith(".png")) path = path + ".png";
        File f = assetTarget(p, path, "images");
        byte[] png = ImageGen.png(a);
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(png);
        }
        refreshAssets();
        JsonObject o = new JsonObject();
        o.addProperty("path", Proj.rel(p, f));
        o.addProperty("use_as", "/" + Proj.rel(p, f).substring("images/".length()));
        o.addProperty("size_bytes", png.length);
        o.addProperty("note", "Set an element's image property to the 'use_as' value.");
        return ToolResult.text(o.toString());
    }

    static ToolResult createSound(JsonObject a) throws Exception {
        Project p = project();
        String path = CoreTools.s(a, "path");
        if (!path.toLowerCase().endsWith(".wav")) path = path + ".wav";
        File f = assetTarget(p, path, "sounds");
        byte[] wav = SoundGen.wav(a);
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(wav);
        }
        refreshAssets();
        JsonObject o = new JsonObject();
        o.addProperty("path", Proj.rel(p, f));
        o.addProperty("use_as", "/" + Proj.rel(p, f).substring("sounds/".length()));
        o.addProperty("size_bytes", wav.length);
        return ToolResult.text(o.toString());
    }

    private static final HashMap<String, HashMap<Integer, byte[]>> CHUNKS = new HashMap<>();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).followRedirects(true).build();

    static ToolResult importAsset(JsonObject a) throws Exception {
        Project p = project();
        String path = CoreTools.s(a, "path");
        File f = assetTarget(p, path, "images", "sounds", "anims", "files", "scripts", "java", "models", "shaders");
        byte[] data;
        if (!CoreTools.s(a, "url").isEmpty()) {
            String url = CoreTools.s(a, "url");
            if (!url.startsWith("https://")) throw new Gl.ToolError("only https URLs are allowed");
            try (Response r = HTTP.newCall(new Request.Builder().url(url).build()).execute()) {
                if (!r.isSuccessful() || r.body() == null) throw new Gl.ToolError("download failed: HTTP " + r.code());
                long len = r.body().contentLength();
                if (len > MAX_DOWNLOAD) throw new Gl.ToolError("file too large (max 25 MB)");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                try (InputStream in = r.body().byteStream()) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bo.write(buf, 0, n);
                        if (bo.size() > MAX_DOWNLOAD) throw new Gl.ToolError("file too large (max 25 MB)");
                    }
                }
                data = bo.toByteArray();
            }
        } else if (!CoreTools.s(a, "base64").isEmpty()) {
            byte[] part = Base64.decode(CoreTools.s(a, "base64"), Base64.DEFAULT);
            int chunks = CoreTools.i(a, "chunks", 1), chunk = CoreTools.i(a, "chunk", 0);
            if (chunks <= 1) data = part;
            else {
                synchronized (CHUNKS) {
                    HashMap<Integer, byte[]> m = CHUNKS.get(f.getAbsolutePath());
                    if (m == null || chunk == 0) {
                        m = new HashMap<>();
                        CHUNKS.put(f.getAbsolutePath(), m);
                    }
                    m.put(chunk, part);
                    if (m.size() < chunks) return ToolResult.text("chunk " + chunk + " stored (" + m.size() + "/" + chunks + "). Send the rest.");
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    for (int i = 0; i < chunks; i++) {
                        byte[] b = m.get(i);
                        if (b == null) throw new Gl.ToolError("chunk " + i + " is missing");
                        bo.write(b);
                    }
                    CHUNKS.remove(f.getAbsolutePath());
                    data = bo.toByteArray();
                }
            }
        } else throw new Gl.ToolError("give url or base64");
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
        refreshAssets();
        final String rel = Proj.rel(p, f);
        if (rel.startsWith("models/")) {
            // 3D model: drop a cached older copy; OBJ3D elements refer to it as "/name.glb" (Mesh MODEL)
            Gl.run(() -> {
                com.star4droid.template.d3.Assets3D.reload("/" + rel.substring(7));
                return null;
            });
            return ToolResult.text("saved " + rel + " (" + data.length + " bytes). Use it on an OBJ3D: props {\"Mesh\":\"MODEL\",\"Model\":\"/" + rel.substring(7) + "\"}");
        }
        return ToolResult.text("saved " + rel + " (" + data.length + " bytes)");
    }
}
