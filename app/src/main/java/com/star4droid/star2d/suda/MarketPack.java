package com.star4droid.star2d.suda;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Packing for the Market. "Full project" = the whole project folder (no .git). "Game only" = what the game needs to RUN
 * (compiled dex, images, sounds, configs...) without the editable sources (java/, Events/, Nodes/ blocks, scenes/): such a
 * package cannot be opened in the editor, it is only played. The zip's single root folder is the project folder name.
 */
public final class MarketPack {
    private MarketPack() {}

    public static final String GAME_MARK = "game.json";
    private static final Set<String> GAME_SKIP = new HashSet<>(Arrays.asList("java", "events", "nodes", "scenes", ".git", "build"));
    private static final Set<String> FULL_SKIP = new HashSet<>(Arrays.asList(".git", "build"));

    public static File projectsDir(Context c) { return new File(c.getFilesDir(), "projects"); }

    public static List<File> localProjects(Context c) {
        File[] fs = projectsDir(c).listFiles(File::isDirectory);
        List<File> l = fs == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(fs));
        java.util.Collections.sort(l, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return l;
    }

    public static File gamesDir(Context c) { return new File(c.getFilesDir(), "market_games"); }

    /** A game can only be packed when it was built (dex/scenes.dex): the user presses Play once in the editor. */
    public static boolean hasBuild(File project) {
        File dex = new File(project, "dex/scenes.dex");
        return dex.isFile() && dex.length() > 0;
    }

    /** First scene to start: scene1 when present, else the first config. */
    public static String firstScene(File project) {
        File cfg = new File(project, "configs");
        if (new File(cfg, "scene1.json").isFile()) return "scene1";
        File[] l = cfg.listFiles((d, n) -> n.endsWith(".json"));
        if (l == null || l.length == 0) return "scene1";
        Arrays.sort(l);
        String n = l[0].getName();
        return n.substring(0, n.length() - 5);
    }

    /** "portrait" or "landscape" from the scene config ("or" key; empty = landscape). */
    public static String orientation(File project, String scene) {
        try (InputStream in = new FileInputStream(new File(project, "configs/" + scene + ".json"))) {
            byte[] b = new byte[(int) Math.min(in.available(), 4_000_000)];
            int n = in.read(b);
            JsonObject o = JsonParser.parseString(new String(b, 0, Math.max(0, n), "UTF-8")).getAsJsonObject();
            String or = o.has("or") ? o.get("or").getAsString() : "";
            return or.contains("portrait") ? "portrait" : "landscape";
        } catch (Throwable t) {
            return "landscape";
        }
    }

    public interface Progress { void on(int files, int total); }

    /** Zips {@code project} into {@code out} (root folder = project name). */
    public static void pack(File project, File out, boolean gameOnly, Progress p, boolean[] cancel) throws IOException {
        List<File> files = new ArrayList<>();
        collect(project, project, gameOnly, files);
        out.getParentFile().mkdirs();
        String root = project.getName();
        try (ZipOutputStream z = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(out), 64 * 1024))) {
            z.setLevel(6);
            int i = 0;
            for (File f : files) {
                if (cancel != null && cancel[0]) throw new IOException("cancelled");
                String rel = project.toURI().relativize(f.toURI()).getPath();
                z.putNextEntry(new ZipEntry(root + "/" + rel));
                try (InputStream in = new BufferedInputStream(new FileInputStream(f), 64 * 1024)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                }
                z.closeEntry();
                if (p != null) p.on(++i, files.size());
            }
            if (gameOnly) {
                String scene = firstScene(project);
                JsonObject o = new JsonObject();
                o.addProperty("scene", scene);
                o.addProperty("or", orientation(project, scene));
                o.addProperty("name", root);
                z.putNextEntry(new ZipEntry(root + "/" + GAME_MARK));
                z.write(o.toString().getBytes("UTF-8"));
                z.closeEntry();
            }
        }
    }

    private static void collect(File root, File dir, boolean gameOnly, List<File> out) {
        File[] l = dir.listFiles();
        if (l == null) return;
        Set<String> skip = gameOnly ? GAME_SKIP : FULL_SKIP;
        for (File f : l) {
            String n = f.getName();
            if (dir.equals(root) && skip.contains(n.toLowerCase(java.util.Locale.ROOT))) continue;
            if (gameOnly && (n.endsWith(".java") || n.endsWith(".blocks") || n.endsWith(".kt"))) continue;
            if (n.endsWith(".part")) continue;
            if (f.isDirectory()) collect(root, f, gameOnly, out);
            else if (f.isFile()) out.add(f);
        }
    }

    /** Extracts {@code zip} into {@code destDir} (zip-slip safe) and returns the project folder inside it. */
    public static File unpack(File zip, File destDir) throws IOException {
        destDir.mkdirs();
        String canon = destDir.getCanonicalPath() + File.separator;
        String rootName = null;
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip), 64 * 1024))) {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../")) throw new IOException("bad entry");
                File f = new File(destDir, name);
                if (!f.getCanonicalPath().startsWith(canon)) throw new IOException("bad entry");
                int s = name.indexOf('/');
                if (rootName == null && s > 0) rootName = name.substring(0, s);
                if (e.isDirectory()) { f.mkdirs(); continue; }
                f.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(f)) {
                    int n;
                    while ((n = z.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
        }
        if (rootName == null) throw new IOException("empty package");
        return new File(destDir, rootName);
    }

    /** A folder name that does not exist yet in {@code dir}. */
    public static File freeDir(File dir, String name) {
        File f = new File(dir, name);
        for (int i = 2; f.exists(); i++) f = new File(dir, name + "_" + i);
        return f;
    }

    public static boolean isGamePackage(File projectDir) { return new File(projectDir, GAME_MARK).isFile(); }

    public static void deleteRecursive(File f) {
        File[] l = f.listFiles();
        if (l != null) for (File c : l) deleteRecursive(c);
        f.delete();
    }
}
