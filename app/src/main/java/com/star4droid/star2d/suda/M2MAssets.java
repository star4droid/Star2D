package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Mesh2Motion rigs (skeleton + reference model + animations) for the "M2M Motion" tool. Nothing is bundled: the first
 * use of a skeleton type downloads it from the SUDA3D server (public GET /api/v1/m2m/manifest + /m2m/files/{type}/{path},
 * resumable with Range, SHA-256 checked) into files/m2m/<type>/, which the M2M page reads at /m2mdata/<type>/.
 * A type is usable once files/m2m/<type>/.type.json (its manifest entry) exists and all its files are there, so cached
 * types keep working when the server is unreachable or has downloads switched off.
 */
public final class M2MAssets {
    private M2MAssets() {}

    public interface Done { void on(boolean ok); }

    /** Types the server may offer (Mesh2Motion rig keys). */
    public static final String[] TYPES = {"human", "fox", "bird", "dragon", "horse", "kaiju", "shark", "snake", "spider"};
    private static final Pattern SAFE_TYPE = Pattern.compile("^[a-z0-9_-]{1,32}$");
    private static final Pattern SAFE_PATH = Pattern.compile("^[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-][A-Za-z0-9_.\\-]*)*\\.glb$");

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "m2m");
        d.mkdirs();
        return d;
    }

    /** Server-relative file path that may be written under files/m2m/<type>/ (no traversal, .glb only). */
    static boolean safePath(String p) {
        return p != null && p.length() < 200 && !p.contains("..") && SAFE_PATH.matcher(p).matches();
    }

    /** Is this type fully on the phone? */
    public static boolean ready(Context c, String type) {
        if (type == null || !SAFE_TYPE.matcher(type).matches()) return false;
        File td = new File(dir(c), type), meta = new File(td, ".type.json");
        if (!meta.exists()) return false;
        try {
            JsonObject t = JsonParser.parseString(FileUtil.readFile(meta.getPath())).getAsJsonObject();
            for (JsonElement e : Suda3D.arr(t, "files")) {
                JsonObject f = e.getAsJsonObject();
                File x = new File(td, Suda3D.str(f, "path"));
                if (!x.isFile() || x.length() != (long) Suda3D.num(f, "size", -1)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String unavailable() { return Tr.t("m2m_download_unavailable", "Download is not available right now"); }

    /** Makes sure a type is on the phone (progress dialog while downloading), then calls done on the UI thread. */
    public static void ensure(Activity a, String type, Done done) {
        if (ready(a, type)) { done.on(true); return; }
        if (type == null || !SAFE_TYPE.matcher(type).matches()) { done.on(false); return; }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Call[] current = new Call[1];
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 12), Ui.dp(a, 20), Ui.dp(a, 4));
        TextView msg = Ui.text(a, Tr.t("m2m_dl_prepare", "Preparing the download…"), 14, Ui.TEXT_2);
        col.addView(msg);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(true);
        col.addView(bar, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 0));
        AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle(Tr.t("m2m_dl_title", "Downloading skeleton") + ": " + label(type))
                .setView(col)
                .setCancelable(false)
                .setNegativeButton(Tr.t("cancel", "Cancel"), (d, w) -> {
                    cancelled.set(true);
                    Call call = current[0];
                    if (call != null) call.cancel();
                })
                .create();
        dlg.show();
        Suda3D.IO.execute(() -> {
            String error = null;
            try {
                download(a, type, cancelled, current, (done1, total) -> Ui.ui(() -> {
                    bar.setIndeterminate(total <= 0);
                    if (total > 0) bar.setProgress((int) (done1 * 1000 / total));
                    msg.setText(Suda3D.fmtSize(done1) + (total > 0 ? " / " + Suda3D.fmtSize(total) : ""));
                }));
            } catch (Exception e) {
                error = cancelled.get() ? "" : (e instanceof Unavailable && e.getMessage() != null ? e.getMessage() : unavailable());
            }
            String err = error;
            Ui.ui(() -> {
                try { dlg.dismiss(); } catch (Exception ignored) {}
                if (err != null && !err.isEmpty() && !a.isFinishing())
                    new AlertDialog.Builder(a).setTitle(label(type)).setMessage(err).setPositiveButton(Tr.t("ok", "OK"), null).show();
                done.on(err == null);
            });
        });
    }

    public static String label(String type) {
        return Tr.t("m2m_type_" + type, type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1));
    }

    /** Server refused (downloads disabled / type unknown): message to show as is. */
    static class Unavailable extends Exception { Unavailable(String m) { super(m); } }

    interface Progress { void on(long done, long total); }

    static JsonObject manifest(Context c) throws Exception {
        Request r = new Request.Builder().url(Suda3D.baseUrl(c) + Suda3D.API_PREFIX + "/m2m/manifest")
                .header("Accept", "application/json").header("Accept-Language", lang()).build();
        try (Response resp = Suda3D.http().newCall(r).execute()) {
            String body = resp.body() == null ? "" : resp.body().string();
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            if (!resp.isSuccessful()) {
                JsonObject err = Suda3D.obj(o, "error");
                throw new Unavailable(err != null && !Suda3D.str(err, "message").isEmpty() ? Suda3D.str(err, "message") : unavailable());
            }
            return o;
        }
    }

    static String lang() { return "ar".equals(Locale.getDefault().getLanguage()) ? "ar" : "en"; }

    private static void download(Context c, String type, AtomicBoolean cancelled, Call[] current, Progress p) throws Exception {
        JsonObject m = manifest(c);
        if (!Suda3D.bool(m, "enabled")) {
            String s = Suda3D.str(m, "message");
            throw new Unavailable(s.isEmpty() ? unavailable() : s);
        }
        JsonObject entry = null;
        for (JsonElement e : Suda3D.arr(m, "types"))
            if (e.isJsonObject() && type.equals(Suda3D.str(e.getAsJsonObject(), "type"))) entry = e.getAsJsonObject();
        if (entry == null) throw new Unavailable(unavailable());
        File td = new File(dir(c), type);
        td.mkdirs();
        JsonArray files = Suda3D.arr(entry, "files");
        long sum = 0, done = 0;
        for (JsonElement e : files) sum += (long) Suda3D.num(e.getAsJsonObject(), "size", 0);
        final long total = sum;
        // a new version replaces the old files (stale ones are left out of .type.json and so ignored)
        new File(td, ".type.json").delete();
        for (JsonElement e : files) {
            if (cancelled.get()) throw new InterruptedException();
            JsonObject f = e.getAsJsonObject();
            String path = Suda3D.str(f, "path"), sha = Suda3D.str(f, "sha256").toLowerCase(Locale.ROOT);
            long size = (long) Suda3D.num(f, "size", -1);
            if (!safePath(path) || size < 0 || sha.length() != 64) throw new Unavailable(unavailable());
            File dest = new File(td, path);
            if (!dest.getCanonicalPath().startsWith(td.getCanonicalPath() + File.separator)) throw new Unavailable(unavailable());
            if (dest.isFile() && dest.length() == size && sha.equals(sha256(dest))) { done += size; p.on(done, total); continue; }
            dest.delete();
            dest.getParentFile().mkdirs();
            long base = done;
            fetch(c, type, path, dest, size, cancelled, current, got -> p.on(base + got, total));
            if (!sha.equals(sha256(dest))) {
                dest.delete();
                throw new Exception("sha256");
            }
            done += size;
            p.on(done, total);
        }
        FileUtil.writeFile(new File(td, ".type.json").getPath(), entry.toString());
    }

    interface Got { void on(long got); }

    /** One file, resumable: bytes go to <dest>.part (+ .etag for If-Range), renamed when complete. */
    private static void fetch(Context c, String type, String path, File dest, long size, AtomicBoolean cancelled, Call[] current, Got got) throws Exception {
        File part = new File(dest.getPath() + ".part"), etagFile = new File(dest.getPath() + ".etag");
        if (part.length() > size) { part.delete(); etagFile.delete(); }
        long have = part.length();
        StringBuilder enc = new StringBuilder();
        for (String seg : path.split("/")) enc.append('/').append(URLEncoder.encode(seg, "UTF-8").replace("+", "%20"));
        Request.Builder b = new Request.Builder().url(Suda3D.baseUrl(c) + Suda3D.API_PREFIX + "/m2m/files/" + type + enc)
                .header("Accept-Language", lang());
        if (have > 0 && have < size) {
            b.header("Range", "bytes=" + have + "-");
            if (etagFile.exists()) b.header("If-Range", FileUtil.readFile(etagFile.getPath()).trim());
        }
        if (have < size || size == 0) {
            Call call = Suda3D.http().newCall(b.build());
            current[0] = call;
            try (Response r = call.execute()) {
                if (r.code() == 503) throw new Unavailable(unavailable());
                if (!r.isSuccessful()) throw new Exception("HTTP " + r.code());
                boolean append = r.code() == 206;
                if (!append) have = 0;
                String etag = r.header("ETag");
                if (etag != null) FileUtil.writeFile(etagFile.getPath(), etag);
                try (InputStream in = r.body().byteStream(); FileOutputStream out = new FileOutputStream(part, append)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    long last = 0;
                    while ((n = in.read(buf)) > 0) {
                        if (cancelled.get()) throw new InterruptedException();
                        out.write(buf, 0, n);
                        have += n;
                        long now = System.currentTimeMillis();
                        if (now - last > 200) { last = now; got.on(have); }
                    }
                }
            } finally {
                current[0] = null;
            }
        }
        if (part.length() != size) throw new Exception("size");
        if (!part.renameTo(dest)) FileUtil.moveFile(part.getPath(), dest.getPath());
        etagFile.delete();
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }
}
