package com.star4droid.star2d.suda;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.star4droid.star2d.ui.Tr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/** The project Market API of the SUDA3D server. Browsing and downloading need no account; writing needs the key. */
public final class MarketApi {
    private MarketApi() {}

    public interface Progress { void on(long done, long total); }

    public static String url(Context c, String path) { return Suda3D.baseUrl(c) + Suda3D.API_PREFIX + "/market" + path; }

    public static String iconUrl(Context c, long id, String updatedAt) {
        return url(c, "/projects/" + id + "/icon") + (updatedAt == null ? "" : "?v=" + URLEncoder.encode(updatedAt));
    }

    private static Request.Builder req(Context c, String path, boolean auth) throws Exception {
        Request.Builder b = Suda3D.base(c, "/market" + path, auth);
        if (!auth && Suda3D.loggedIn(c)) b.header("Authorization", "Bearer " + Suda3D.key(c)); // lets the server mark my own projects
        return b;
    }

    public static JsonObject list(Context c, String q, String kind, String sort, int page) throws Exception {
        StringBuilder p = new StringBuilder("/projects?page=" + page + "&per_page=24");
        if (q != null && !q.trim().isEmpty()) p.append("&q=").append(URLEncoder.encode(q.trim(), "UTF-8"));
        if (kind != null && !kind.isEmpty()) p.append("&kind=").append(kind);
        if (sort != null && !sort.isEmpty()) p.append("&sort=").append(sort);
        return Suda3D.exec(req(c, p.toString(), false).get().build());
    }

    public static JsonObject mine(Context c) throws Exception { return Suda3D.exec(req(c, "/mine", true).get().build()); }

    public static JsonObject get(Context c, long id) throws Exception { return Suda3D.exec(req(c, "/projects/" + id, false).get().build()); }

    public static void delete(Context c, long id) throws Exception {
        Suda3D.exec(req(c, "/projects/" + id, true).delete().build());
    }

    /** Create (id = 0) or update (id > 0) with multipart; file/icon may be null on update. Returns the project JSON. */
    public static JsonObject save(Context c, long id, String name, String desc, String kind, File zip, File icon, Progress progress, Call[] cancel) throws Exception {
        MultipartBody.Builder m = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("name", name)
                .addFormDataPart("description", desc == null ? "" : desc)
                .addFormDataPart("kind", kind)
                .addFormDataPart("engine_version", com.star4droid.star2d.evo.BuildConfig.VERSION_NAME)
                .addFormDataPart("engine_version_code", String.valueOf(com.star4droid.star2d.evo.BuildConfig.VERSION_CODE));
        if (icon != null) m.addFormDataPart("icon", "icon" + ext(icon), RequestBody.create(icon, MediaType.parse(Suda3D.mime(icon.getName()))));
        if (zip != null) m.addFormDataPart("file", "project.zip", progressBody(RequestBody.create(zip, MediaType.parse("application/zip")), progress));
        Request r = req(c, id > 0 ? "/projects/" + id : "/projects", true).post(m.build()).build();
        Call call = Suda3D.http().newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS).readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build().newCall(r);
        if (cancel != null) cancel[0] = call;
        try (Response resp = call.execute()) {
            String txt = resp.body() == null ? "" : resp.body().string();
            com.google.gson.JsonObject o = null;
            try { o = com.google.gson.JsonParser.parseString(txt).getAsJsonObject(); } catch (Exception ignored) {}
            if (!resp.isSuccessful()) {
                JsonObject err = o == null ? null : Suda3D.obj(o, "error");
                String code = err == null ? "http_" + resp.code() : Suda3D.str(err, "code");
                String msg = err == null ? Suda3D.friendly(code, resp.code()) : Suda3D.str(err, "message");
                if (resp.code() == 413) msg = Tr.t("mk_too_big", "The file is too large for the server.");
                throw new Suda3D.ApiException(resp.code(), code, msg);
            }
            return o == null ? new JsonObject() : o;
        }
    }

    private static String ext(File f) {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        int i = n.lastIndexOf('.');
        return i < 0 ? ".png" : n.substring(i);
    }

    private static RequestBody progressBody(RequestBody b, Progress p) {
        return new RequestBody() {
            @Override public MediaType contentType() { return b.contentType(); }
            @Override public long contentLength() throws IOException { return b.contentLength(); }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                long total = contentLength();
                okio.BufferedSink counting = okio.Okio.buffer(new okio.ForwardingSink(sink) {
                    long done;
                    @Override public void write(okio.Buffer source, long byteCount) throws IOException {
                        super.write(source, byteCount);
                        done += byteCount;
                        if (p != null) p.on(done, total);
                    }
                });
                b.writeTo(counting);
                counting.flush();
            }
        };
    }

    /** Streams the zip of a project to {@code dest}. */
    public static void download(Context c, long id, File dest, Progress progress, Call[] cancel) throws Exception {
        Call call = Suda3D.http().newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req(c, "/projects/" + id + "/download", false).get().build());
        if (cancel != null) cancel[0] = call;
        try (Response r = call.execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new Suda3D.ApiException(r.code(), "http_" + r.code(), Suda3D.friendly("http", r.code()));
            long total = r.body().contentLength();
            File tmp = new File(dest.getPath() + ".part");
            dest.getParentFile().mkdirs();
            try (InputStream in = r.body().byteStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (progress != null) progress.on(done, total);
                }
            }
            dest.delete();
            if (!tmp.renameTo(dest)) throw new IOException("rename failed");
        }
    }

    public static JsonArray data(JsonObject o) {
        try { return o.getAsJsonArray("data"); } catch (Exception e) { return new JsonArray(); }
    }
}
