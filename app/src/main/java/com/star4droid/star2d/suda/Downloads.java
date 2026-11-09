package com.star4droid.star2d.suda;

import android.content.Context;

import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Resumable downloads of job outputs (HTTP Range). The bytes go to "<dest>.part" (its length IS the saved progress,
 * plus "<dest>.part.etag" to make sure a resume continues the same file); pause keeps the part, cancel deletes it,
 * a finished download is renamed to dest. Listeners get progress for any destination (nodes, viewer, quick dialog).
 */
public final class Downloads {
    private Downloads() {}

    public interface Listener { void onProgress(File dest, long done, long total, String state); }

    public static final String RUNNING = "running", PAUSED = "paused", DONE = "done", FAILED = "failed", CANCELLED = "cancelled";

    private static final Map<String, Call> CALLS = new ConcurrentHashMap<>();
    private static final Map<String, long[]> PROGRESS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Listener> L = new CopyOnWriteArrayList<>();

    public static void listen(Listener l) { L.addIfAbsent(l); }
    public static void unlisten(Listener l) { L.remove(l); }

    public static boolean running(File dest) { return CALLS.containsKey(dest.getAbsolutePath()); }
    public static File part(File dest) { return new File(dest.getPath() + ".part"); }
    /** Bytes already saved (finished or partial). */
    public static long saved(File dest) { return dest.exists() ? dest.length() : part(dest).length(); }
    /** Known total size, or -1. */
    public static long total(File dest) {
        long[] p = PROGRESS.get(dest.getAbsolutePath());
        return p == null ? -1 : p[1];
    }

    public static void start(Context c, String url, File dest, long expectedSize) {
        String key = dest.getAbsolutePath();
        if (dest.exists() || CALLS.containsKey(key)) return;
        dest.getParentFile().mkdirs();
        PROGRESS.put(key, new long[]{part(dest).length(), expectedSize});
        Suda3D.IO.execute(() -> run(c.getApplicationContext(), url, dest, expectedSize));
    }

    public static void pause(File dest) {
        Call call = CALLS.remove(dest.getAbsolutePath());
        if (call != null) call.cancel();
        emit(dest, part(dest).length(), total(dest), PAUSED);
    }

    public static void cancel(File dest) {
        Call call = CALLS.remove(dest.getAbsolutePath());
        if (call != null) call.cancel();
        part(dest).delete();
        new File(part(dest).getPath() + ".etag").delete();
        emit(dest, 0, total(dest), CANCELLED);
    }

    private static void run(Context c, String url, File dest, long expected) {
        String key = dest.getAbsolutePath();
        File part = part(dest);
        File etagFile = new File(part.getPath() + ".etag");
        long have = part.length();
        try {
            Request.Builder b = new Request.Builder().url(Suda3D.absolute(c, url));
            if (url.startsWith("/api/") || url.startsWith(Suda3D.baseUrl(c))) b.header("Authorization", "Bearer " + Suda3D.key(c));
            if (have > 0) {
                b.header("Range", "bytes=" + have + "-");
                String etag = etagFile.exists() ? com.star4droid.star2d.Helpers.FileUtil.readFile(etagFile.getPath()).trim() : "";
                if (!etag.isEmpty()) b.header("If-Range", etag);
            }
            Call call = Suda3D.http().newCall(b.build());
            CALLS.put(key, call);
            try (Response r = call.execute()) {
                if (!r.isSuccessful()) {
                    if (r.code() == 416 && expected > 0 && have >= expected) { finish(part, dest, etagFile); emit(dest, have, have, DONE); return; }
                    throw new Suda3D.ApiException(r.code(), r.code() == 404 || r.code() == 410 ? "expired" : "http_" + r.code(),
                            r.code() == 404 || r.code() == 410 ? com.star4droid.star2d.ui.Tr.t("s3d_expired", "This file was deleted from the server") : "HTTP " + r.code());
                }
                boolean append = r.code() == 206;
                if (!append) have = 0;
                String etag = r.header("ETag");
                if (etag != null) com.star4droid.star2d.Helpers.FileUtil.writeFile(etagFile.getPath(), etag);
                long len = r.body().contentLength();
                long total = len >= 0 ? have + len : expected;
                PROGRESS.put(key, new long[]{have, total});
                try (InputStream in = r.body().byteStream(); FileOutputStream out = new FileOutputStream(part, append)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    long lastEmit = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        have += n;
                        PROGRESS.put(key, new long[]{have, total});
                        long now = System.currentTimeMillis();
                        if (now - lastEmit > 250) { lastEmit = now; emit(dest, have, total, RUNNING); }
                    }
                }
                finish(part, dest, etagFile);
                emit(dest, have, have, DONE);
            }
        } catch (Exception e) {
            if (!CALLS.containsKey(key)) return; // paused / cancelled by the user
            emit(dest, part.length(), total(dest), FAILED + ":" + Suda3D.message(e));
        } finally {
            CALLS.remove(key);
        }
    }

    private static void finish(File part, File dest, File etag) {
        if (!part.renameTo(dest)) com.star4droid.star2d.Helpers.FileUtil.moveFile(part.getPath(), dest.getPath());
        etag.delete();
    }

    private static void emit(File dest, long done, long total, String state) {
        for (Listener l : L) Ui.ui(() -> l.onProgress(dest, done, total, state));
    }
}
