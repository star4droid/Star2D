package com.star4droid.star2d.suda;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.ui.Tr;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SUDA3D account + server API (see SUDA3D contract). One place for the fixed server base URL (not shown, not changeable),
 * the API key (private prefs of the app, never shown again after login), and every JSON call. All calls are blocking:
 * run them on {@link #IO}.
 */
public final class Suda3D {
    /** The SUDA3D server. Fixed: never shown in the app and not changeable. */
    public static final String BASE_URL = "https://springgreen-otter-786523.hostingersite.com";
    public static final String API_PREFIX = "/api/v1";

    private static final String PREFS = "suda3d_account";
    private static final String K_KEY = "api_key", K_BASE = "base_url", K_EMAIL = "email", K_PLAN_AI = "ai_use_plan";
    private static final String K_PLAY = "play_account_id";
    private static final String K_PAID = "paid", K_CREDITS = "credits", K_USD = "usd", K_PRICING = "pricing_cache";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static OkHttpClient http;

    private Suda3D() {}

    /** Error from the server: {@code code} is the stable error code (insufficient_balance, banned, unauthorized...). */
    public static class ApiException extends Exception {
        public final int status;
        public final String code;
        public ApiException(int status, String code, String message) {
            super(message == null || message.isEmpty() ? code : message);
            this.status = status;
            this.code = code == null ? "" : code;
        }
        public boolean noBalance() { return status == 402 || "insufficient_balance".equals(code); }
        public boolean unauthorized() { return status == 401 || "unauthorized".equals(code) || "no_key".equals(code); }
    }

    public static synchronized OkHttpClient http() {
        if (http == null) http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build();
        return http;
    }

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String baseUrl(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains(K_BASE)) p.edit().remove(K_BASE).apply(); // drop the old server override of earlier versions
        return BASE_URL;
    }

    /** Account id to pass to Google Play as obfuscatedAccountId (from GET /me), "" until known. */
    public static String playAccountId(Context c) { return prefs(c).getString(K_PLAY, ""); }
    public static String key(Context c) { return prefs(c).getString(K_KEY, ""); }
    public static boolean loggedIn(Context c) { return !key(c).isEmpty(); }
    public static String email(Context c) { return prefs(c).getString(K_EMAIL, ""); }
    public static boolean paid(Context c) { return prefs(c).getBoolean(K_PAID, false); }
    public static boolean aiUsesPlan(Context c) { return loggedIn(c) && prefs(c).getBoolean(K_PLAN_AI, false); }
    public static void setAiUsesPlan(Context c, boolean on) { prefs(c).edit().putBoolean(K_PLAN_AI, on).apply(); }

    /** Last known balance in credits (1 credit = $0.01), -1 = unknown. */
    public static double credits(Context c) { return Double.longBitsToDouble(prefs(c).getLong(K_CREDITS, Double.doubleToLongBits(-1))); }
    public static String usd(Context c) { return prefs(c).getString(K_USD, ""); }

    public static void setKey(Context c, String key, String email) {
        prefs(c).edit().putString(K_KEY, key == null ? "" : key.trim()).putString(K_EMAIL, email == null ? "" : email).apply();
    }

    public static void logout(Context c) {
        prefs(c).edit().remove(K_KEY).remove(K_EMAIL).remove(K_PAID).remove(K_CREDITS).remove(K_USD).remove(K_PLAY).putBoolean(K_PLAN_AI, false).apply();
    }

    static void storeBalance(Context c, JsonObject balance) {
        if (balance == null) return;
        prefs(c).edit().putLong(K_CREDITS, Double.doubleToLongBits(num(balance, "credits", -1))).putString(K_USD, str(balance, "usd")).apply();
        Balance.changed();
    }

    // ------------------------------------------------------------ calls

    /** POST /auth/login: stores the returned device API key. */
    public static void login(Context c, String email, String password) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("email", email);
        b.addProperty("password", password);
        b.addProperty("device_name", "Star2D " + android.os.Build.MODEL);
        JsonObject r = call(c, "POST", "/auth/login", b, false);
        String token = str(r, "token");
        if (token.isEmpty()) token = str(r, "api_key");
        if (token.isEmpty()) token = str(r, "key");
        if (token.isEmpty()) throw new ApiException(500, "bad_response", Tr.t("s3d_err_server", "Unexpected server answer"));
        setKey(c, token, email);
        me(c);
    }

    public static void register(Context c, String email, String password) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("email", email);
        b.addProperty("password", password);
        b.addProperty("device_name", "Star2D " + android.os.Build.MODEL);
        call(c, "POST", "/auth/register", b, false);
    }

    /** GET /me: refreshes paid flag + balance. */
    public static JsonObject me(Context c) throws Exception {
        JsonObject r = call(c, "GET", "/me", null, true);
        String em = str(r, "email");
        SharedPreferences.Editor e = prefs(c).edit().putBoolean(K_PAID, bool(r, "paid"));
        if (!em.isEmpty()) e.putString(K_EMAIL, em);
        String play = str(r, "play_account_id");
        if (!play.isEmpty()) e.putString(K_PLAY, play);
        e.apply();
        storeBalance(c, obj(r, "balance"));
        return r;
    }

    /** GET /pricing (public). Cached so dialogs open instantly; refreshed on every call. */
    public static JsonObject pricing(Context c) throws Exception {
        JsonObject r = call(c, "GET", "/pricing", null, loggedIn(c));
        prefs(c).edit().putString(K_PRICING, r.toString()).apply();
        return r;
    }

    public static JsonObject cachedPricing(Context c) {
        try {
            String s = prefs(c).getString(K_PRICING, "");
            if (!s.isEmpty()) return JsonParser.parseString(s).getAsJsonObject();
        } catch (Exception ignored) {}
        return null;
    }

    public static JsonObject quote(Context c, String op, JsonObject params) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("op", op);
        b.add("params", params == null ? new JsonObject() : params);
        return call(c, "POST", "/quote", b, loggedIn(c));
    }

    /** GET /billing/products → {products:[{product_id, credit_usd, credits}], fee_note_percent}. */
    public static JsonObject billingProducts(Context c) throws Exception {
        return call(c, "GET", "/billing/products", null, loggedIn(c));
    }

    /**
     * POST /billing/google/verify: the server checks the Google Play purchase, credits the account once (idempotent on
     * resubmit) and consumes it. Returns {credited:{usd,credits}, balance:{usd,credits}}; the balance is stored.
     */
    public static JsonObject verifyGooglePurchase(Context c, String productId, String token, String orderId) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("product_id", productId);
        b.addProperty("purchase_token", token);
        if (orderId != null && !orderId.isEmpty()) b.addProperty("order_id", orderId);
        JsonObject r = call(c, "POST", "/billing/google/verify", b, true);
        storeBalance(c, obj(r, "balance"));
        return r;
    }

    /** POST /uploads (multipart) → upload_id. */
    public static String upload(Context c, File f) throws Exception {
        requireKey(c);
        RequestBody file = RequestBody.create(f, MediaType.parse(mime(f.getName())));
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", f.getName(), file).build();
        Request req = base(c, "/uploads", true).post(body).build();
        return str(exec(req), "upload_id");
    }

    /** POST /jobs. */
    public static JsonObject createJob(Context c, String op, JsonObject params, JsonObject inputs, String idempotency) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("op", op);
        b.add("params", params == null ? new JsonObject() : params);
        b.add("inputs", inputs == null ? new JsonObject() : inputs);
        b.addProperty("idempotency_key", idempotency);
        return call(c, "POST", "/jobs", b, true);
    }

    public static JsonObject job(Context c, String id) throws Exception { return call(c, "GET", "/jobs/" + id, null, true); }
    public static JsonObject jobs(Context c, int page) throws Exception { return call(c, "GET", "/jobs?page=" + page, null, true); }
    public static void cancelJob(Context c, String id) throws Exception { call(c, "POST", "/jobs/" + id + "/cancel", new JsonObject(), true); }

    /** POST /ai/messages: Anthropic Messages body, charged from the plan. */
    public static JsonObject aiMessages(Context c, JsonObject body) throws Exception {
        JsonObject r = call(c, "POST", "/ai/messages", body, true);
        JsonObject s = obj(r, "suda3d");
        if (s != null) storeBalance(c, obj(s, "balance"));
        return r;
    }

    /** Absolute URL for a server path ("/api/v1/files/x" or "files/x"). */
    public static String absolute(Context c, String url) {
        if (url == null) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/")) return baseUrl(c) + url;
        return baseUrl(c) + API_PREFIX + "/" + url;
    }

    // ------------------------------------------------------------ plumbing

    static void requireKey(Context c) throws ApiException {
        if (!loggedIn(c)) throw new ApiException(401, "no_key", Tr.t("s3d_err_login", "Sign in to your SUDA3D account first"));
    }

    static Request.Builder base(Context c, String path, boolean auth) throws ApiException {
        if (auth) requireKey(c);
        Request.Builder b = new Request.Builder().url(baseUrl(c) + API_PREFIX + path)
                .header("Accept", "application/json")
                .header("Accept-Language", "ar".equals(Tr.lang()) ? "ar" : "en");
        if (auth) b.header("Authorization", "Bearer " + key(c));
        return b;
    }

    static JsonObject call(Context c, String method, String path, JsonObject body, boolean auth) throws Exception {
        Request.Builder b = base(c, path, auth);
        if ("GET".equals(method)) b.get();
        else b.method(method, RequestBody.create(body == null ? "{}" : body.toString(), JSON));
        return exec(b.build());
    }

    static JsonObject exec(Request req) throws Exception {
        try (Response r = http().newCall(req).execute()) {
            String txt = r.body() == null ? "" : r.body().string();
            JsonObject o = null;
            try {
                JsonElement el = JsonParser.parseString(txt);
                if (el.isJsonObject()) o = el.getAsJsonObject();
            } catch (Exception ignored) {}
            if (!r.isSuccessful()) {
                String code = "", msg = "";
                JsonObject err = o == null ? null : obj(o, "error");
                if (err != null) { code = str(err, "code"); msg = str(err, "message"); }
                else if (o != null) msg = str(o, "message");
                if (code.isEmpty()) code = r.code() == 402 ? "insufficient_balance" : r.code() == 401 ? "unauthorized" : "http_" + r.code();
                if (msg.isEmpty()) msg = friendly(code, r.code());
                throw new ApiException(r.code(), code, msg);
            }
            return o == null ? new JsonObject() : o;
        }
    }

    static String friendly(String code, int status) {
        switch (code) {
            case "insufficient_balance": return Tr.t("s3d_no_credits", "You don't have usage credits");
            case "unauthorized": return Tr.t("s3d_err_login", "Sign in to your SUDA3D account first");
            case "banned": return Tr.t("s3d_err_banned", "This account is suspended");
            default: return Tr.t("s3d_err_server", "Unexpected server answer") + " (" + status + ")";
        }
    }

    /** Readable message for any error. */
    public static String message(Throwable t) {
        if (t instanceof ApiException) return t.getMessage();
        if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException
                || t instanceof java.net.SocketTimeoutException)
            return Tr.t("s3d_err_network", "Can't reach the SUDA3D server. Check the connection.");
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    public static String mime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".glb")) return "model/gltf-binary";
        if (n.endsWith(".gltf")) return "model/gltf+json";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------ json helpers

    public static String str(JsonObject o, String k) {
        try {
            JsonElement e = o == null ? null : o.get(k);
            return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
        } catch (Exception ex) { return ""; }
    }

    public static double num(JsonObject o, String k, double def) {
        try {
            JsonElement e = o == null ? null : o.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsDouble();
        } catch (Exception ex) { return def; }
    }

    public static boolean bool(JsonObject o, String k) {
        try {
            JsonElement e = o == null ? null : o.get(k);
            return e != null && !e.isJsonNull() && e.getAsBoolean();
        } catch (Exception ex) { return false; }
    }

    public static JsonObject obj(JsonObject o, String k) {
        try {
            JsonElement e = o == null ? null : o.get(k);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ex) { return null; }
    }

    public static JsonArray arr(JsonObject o, String k) {
        try {
            JsonElement e = o == null ? null : o.get(k);
            return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
        } catch (Exception ex) { return new JsonArray(); }
    }

    /** "12.5" credits → "12.5", whole numbers without decimals. */
    public static String fmtCredits(double v) {
        if (v < 0) return "–";
        if (Math.abs(v - Math.rint(v)) < 1e-6) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.US, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public static String fmtSize(long b) {
        if (b < 0) return "?";
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024));
    }

    /** Balance listeners (Plan tab, workspace top bar). */
    public static final class Balance {
        private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> L = new java.util.concurrent.CopyOnWriteArrayList<>();
        public static void listen(Runnable r) { L.addIfAbsent(r); }
        public static void unlisten(Runnable r) { L.remove(r); }
        static void changed() { for (Runnable r : L) com.star4droid.star2d.ui.Ui.ui(r); }
    }
}
