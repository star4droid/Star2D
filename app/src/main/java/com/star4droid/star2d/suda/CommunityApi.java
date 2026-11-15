package com.star4droid.star2d.suda;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

/** Community endpoints of the SUDA3D server (/community/*, plus the Editor's Choice calls of /market). All blocking: run on Suda3D.IO. */
public final class CommunityApi {
    private CommunityApi() {}

    /** Reading is public; a key (if we have one) lets the server mark own posts, stars and moderator buttons. */
    private static Request.Builder rd(Context c, String path) throws Exception {
        Request.Builder b = Suda3D.base(c, path, false);
        if (Suda3D.loggedIn(c)) b.header("Authorization", "Bearer " + Suda3D.key(c));
        return b;
    }

    private static JsonObject json(Context c, String method, String path, JsonObject body) throws Exception {
        return Suda3D.call(c, method, path, body, true);
    }

    public static JsonArray data(JsonObject o) { return MarketApi.data(o); }

    // ------------------------------------------------------------------ config / me
    public static JsonObject config(Context c) throws Exception { return Suda3D.exec(rd(c, "/community/config").get().build()); }
    public static JsonObject me(Context c) throws Exception { return json(c, "GET", "/community/me", null); }

    public static JsonObject updateMe(Context c, String displayName, String notifLang) throws Exception {
        JsonObject b = new JsonObject();
        if (displayName != null) b.addProperty("display_name", displayName);
        if (notifLang != null) b.addProperty("notif_lang", notifLang);
        return json(c, "POST", "/community/me", b);
    }

    public static JsonObject setAvatar(Context c, File img) throws Exception {
        RequestBody m = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("avatar", "avatar.jpg", RequestBody.create(img, MediaType.parse("image/jpeg"))).build();
        return Suda3D.exec(Suda3D.base(c, "/community/me/avatar", true).post(m).build());
    }

    public static JsonObject clearAvatar(Context c) throws Exception { return json(c, "DELETE", "/community/me/avatar", null); }
    public static JsonObject profile(Context c, long id) throws Exception { return Suda3D.exec(rd(c, "/community/users/" + id).get().build()); }
    public static void badge(Context c, long uid, String key, boolean on) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("badge", key);
        b.addProperty("on", on);
        json(c, "POST", "/community/users/" + uid + "/badges", b);
    }

    // ------------------------------------------------------------------ posts
    public static JsonObject posts(Context c, long before, long userId) throws Exception {
        StringBuilder p = new StringBuilder("/community/posts?per_page=15");
        if (before > 0) p.append("&before=").append(before);
        if (userId > 0) p.append("&user=").append(userId);
        return Suda3D.exec(rd(c, p.toString()).get().build());
    }

    public static JsonObject post(Context c, long id) throws Exception { return Suda3D.exec(rd(c, "/community/posts/" + id).get().build()); }

    public static JsonObject createPost(Context c, String body, List<File> images) throws Exception {
        MultipartBody.Builder m = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("body", body == null ? "" : body);
        for (File f : images) m.addFormDataPart("images[]", f.getName(), RequestBody.create(f, MediaType.parse("image/jpeg")));
        Request r = Suda3D.base(c, "/community/posts", true).post(m.build()).build();
        okhttp3.OkHttpClient cl = Suda3D.http().newBuilder().writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build();
        return Suda3D.exec(cl, r);
    }

    public static JsonObject editPost(Context c, long id, String body) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("body", body);
        return json(c, "POST", "/community/posts/" + id, b);
    }

    public static void deletePost(Context c, long id) throws Exception { json(c, "DELETE", "/community/posts/" + id, null); }
    public static void pin(Context c, long id, boolean on) throws Exception { json(c, on ? "POST" : "DELETE", "/community/posts/" + id + "/pin", null); }

    // ------------------------------------------------------------------ stars / reports
    /** @return {starred, stars} */
    public static JsonObject star(Context c, String type, long id, boolean on) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("type", type);
        b.addProperty("id", id);
        b.addProperty("on", on);
        return json(c, "POST", "/community/star", b);
    }

    public static void report(Context c, String type, long id, String reason, String note) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("type", type);
        b.addProperty("id", id);
        b.addProperty("reason", reason);
        if (note != null && !note.isEmpty()) b.addProperty("note", note);
        json(c, "POST", "/community/reports", b);
    }

    // ------------------------------------------------------------------ comments
    /** Top-level comments (newest first, {@code cursor} = next id) or, with parent > 0, its replies (oldest first, cursor = after id). */
    public static JsonObject comments(Context c, String type, long id, long parent, long cursor) throws Exception {
        StringBuilder p = new StringBuilder("/community/comments?type=" + type + "&id=" + id + "&per_page=15");
        if (parent > 0) { p.append("&parent=").append(parent); if (cursor > 0) p.append("&after=").append(cursor); }
        else if (cursor > 0) p.append("&before=").append(cursor);
        return Suda3D.exec(rd(c, p.toString()).get().build());
    }

    public static JsonObject addComment(Context c, String type, long id, String body, long replyTo) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("type", type);
        b.addProperty("id", id);
        b.addProperty("body", body);
        if (replyTo > 0) b.addProperty("reply_to", replyTo);
        return json(c, "POST", "/community/comments", b);
    }

    public static JsonObject editComment(Context c, long id, String body) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("body", body);
        return json(c, "POST", "/community/comments/" + id, b);
    }

    public static void deleteComment(Context c, long id) throws Exception { json(c, "DELETE", "/community/comments/" + id, null); }
    public static JsonObject history(Context c, long id) throws Exception { return json(c, "GET", "/community/comments/" + id + "/history", null); }

    // ------------------------------------------------------------------ notifications / push
    public static JsonObject notifications(Context c, int page) throws Exception { return json(c, "GET", "/community/notifications?page=" + page, null); }
    public static JsonObject notifCount(Context c, long since) throws Exception { return json(c, "GET", "/community/notifications/count?since=" + since, null); }

    public static void addDevice(Context c, String token) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("token", token);
        json(c, "POST", "/community/devices", b);
    }

    public static void removeDevice(Context c, String token) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("token", token);
        json(c, "DELETE", "/community/devices", b);
    }

    // ------------------------------------------------------------------ market extras
    public static JsonObject featured(Context c) throws Exception { return Suda3D.exec(rd(c, "/market/featured").get().build()); }

    public static JsonObject setFeatured(Context c, long projectId, boolean on) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("on", on);
        return json(c, "POST", "/market/projects/" + projectId + "/featured", b);
    }

    public static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
