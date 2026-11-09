package com.star4droid.star2d.suda;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.google.gson.JsonObject;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Tr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Where SUDA3D files live on the phone and how they are saved to a project / the phone / Downloads. */
public final class Store {
    private Store() {}

    public interface Done { void on(File saved); }

    /** Android/data/<pkg>/files/<name> (falls back to internal files). */
    public static File ext(Context c, String name) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        File f = new File(base, name);
        f.mkdirs();
        return f;
    }

    public static File workspacesDir(Context c) { return ext(c, "workspaces"); }
    public static File quickDir(Context c) { return ext(c, "suda3d"); }
    public static File projectsDir(Context c) { return new File(c.getFilesDir(), "projects"); }

    /** File name for an output: <op>_<job8>[_n].<format>. */
    public static String outputName(String op, String jobId, JsonObject out, int index) {
        String fmt = Suda3D.str(out, "format").toLowerCase(Locale.ROOT);
        if (fmt.isEmpty()) fmt = "image".equals(Suda3D.str(out, "kind")) ? "png" : "glb";
        String id = jobId == null ? "x" : jobId.replaceAll("[^A-Za-z0-9]", "");
        if (id.length() > 8) id = id.substring(0, 8);
        return (op == null ? "out" : op) + "_" + id + (index > 0 ? "_" + index : "") + "." + fmt;
    }

    public static boolean isModel(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".glb") || n.endsWith(".gltf") || n.endsWith(".obj") || n.endsWith(".fbx") || n.endsWith(".stl") || n.endsWith(".usdz");
    }

    public static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
    }

    /** Copies into <project>/models or <project>/images (unique name); returns the copy. */
    public static File toProject(File f, File project) throws Exception {
        File dir = new File(project, isImage(f.getName()) ? "images" : "models");
        dir.mkdirs();
        String name = f.getName().replace(' ', '_');
        File to = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; to.exists(); i++) to = new File(dir, base + "_" + i + ext);
        copy(f, to);
        return to;
    }

    /** Lets the user pick a project (or uses the given one) and saves there. */
    public static void saveToProject(Context c, File f, String currentProject, Done done) {
        if (currentProject != null) {
            run(c, () -> toProject(f, new File(currentProject)), done);
            return;
        }
        File[] list = projectsDir(c).listFiles(File::isDirectory);
        if (list == null || list.length == 0) {
            com.star4droid.star2d.ui.Ui.toast(c, Tr.t("home_no_projects", "No projects yet"));
            return;
        }
        ArrayList<File> all = new ArrayList<>(Arrays.asList(list));
        java.util.Collections.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).getName();
        Dialogs.choose(c, Tr.t("s3d_pick_project", "Save to which project?"), names, i -> run(c, () -> toProject(f, all.get(i)), done));
    }

    /** Public Downloads/SUDA3D (MediaStore on Android 10+). */
    public static void saveToDownloads(Context c, File f, Done done) {
        run(c, () -> {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, f.getName());
                v.put(MediaStore.Downloads.MIME_TYPE, Suda3D.mime(f.getName()));
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SUDA3D");
                Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("MediaStore");
                try (InputStream in = new FileInputStream(f); OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                    pipe(in, out);
                }
                return new File(Environment.DIRECTORY_DOWNLOADS + "/SUDA3D/" + f.getName());
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SUDA3D");
            dir.mkdirs();
            File to = new File(dir, f.getName());
            copy(f, to);
            return to;
        }, done);
    }

    /** Writes the file to a SAF document Uri chosen by the user. */
    public static void saveToUri(Context c, File f, Uri uri, Done done) {
        run(c, () -> {
            try (InputStream in = new FileInputStream(f); OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                pipe(in, out);
            }
            return f;
        }, done);
    }

    interface Job { File run() throws Exception; }

    static void run(Context c, Job j, Done done) {
        Suda3D.IO.execute(() -> {
            try {
                File r = j.run();
                com.star4droid.star2d.ui.Ui.ui(() -> {
                    com.star4droid.star2d.ui.Ui.toast(c, Tr.t("s3d_saved", "Saved") + ": " + r.getName());
                    if (done != null) done.on(r);
                });
            } catch (Exception e) {
                com.star4droid.star2d.ui.Ui.toast(c, Suda3D.message(e));
            }
        });
    }

    public static void copy(File from, File to) throws Exception {
        to.getParentFile().mkdirs();
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            pipe(in, out);
        }
    }

    static void pipe(InputStream in, OutputStream out) throws Exception {
        byte[] b = new byte[64 * 1024];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }
}
