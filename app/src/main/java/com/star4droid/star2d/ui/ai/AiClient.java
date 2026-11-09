package com.star4droid.star2d.ui.ai;

import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * OpenAI-compatible chat client used for every provider (OpenRouter, Gemini's OpenAI endpoint,
 * OpenCode Zen, or any custom base URL). Streams tokens with Server-Sent Events.
 * Model lists are fetched live, so retired models never linger in the app.
 */
public class AiClient {

    public static class Provider {
        public final String id, name, baseUrl, keyPref, keyUrl, help;
        public final boolean needsKey;
        Provider(String id, String name, String baseUrl, String keyPref, String keyUrl, boolean needsKey, String help) {
            this.id = id; this.name = name; this.baseUrl = baseUrl; this.keyPref = keyPref; this.keyUrl = keyUrl;
            this.needsKey = needsKey; this.help = help;
        }
    }

    public static final Provider[] PROVIDERS = {
            new Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter_api_key",
                    "https://openrouter.ai/keys", true,
                    "One key for hundreds of models (many free, marked FREE). Create a key at openrouter.ai/keys."),
            new Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini_api_key",
                    "https://aistudio.google.com/apikey", true,
                    "Free tier available. Create a key in Google AI Studio."),
            new Provider("opencode", "OpenCode Zen", "https://opencode.ai/zen/v1", "zen_api_key",
                    "https://opencode.ai/zen", true,
                    "OpenCode Zen gateway (has free models)."),
            new Provider("custom", "Custom (OpenAI compatible)", "", "custom_api_key", "", false,
                    "Any OpenAI-compatible server: OpenAI, DeepSeek, Groq, Ollama, LM Studio... Set the base URL ending in /v1."),
    };

    public static Provider provider(String id) {
        for (Provider p : PROVIDERS) if (p.id.equals(id)) return p;
        return PROVIDERS[0];
    }

    public static class Model {
        public String id, name;
        public boolean free;
        @Override public String toString() { return (free ? "FREE · " : "") + (name == null || name.isEmpty() ? id : name); }
    }

    public interface Stream {
        void onToken(String text);
        void onDone(String full);
        void onError(String message);
        /** SUDA3D plan: cost line of the reply ("0.4 credits"). */
        default void onCost(String cost) {}
    }

    private final SharedPreferences prefs;
    private volatile HttpURLConnection current;
    private volatile boolean cancelled;

    private android.content.Context context;

    public AiClient(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** Needed for the SUDA3D plan (account prefs). */
    public void setContext(android.content.Context c) { this.context = c == null ? null : c.getApplicationContext(); }

    /** True when replies go through the SUDA3D plan (charged from the account's credits). */
    public boolean onPlan() { return context != null && com.star4droid.star2d.suda.Suda3D.aiUsesPlan(context); }

    /** SUDA3D plan: Anthropic Messages request via POST /ai/messages (not streamed); cost reported with onCost. */
    private void planChat(List<Msg> messages, Stream cb) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", com.star4droid.star2d.suda.Suda3D.prefs(context).getString("plan_ai_model",
                    com.star4droid.star2d.suda.SudaPlanPanel.AI_MODELS[0]));
            body.addProperty("max_tokens", 4096);
            body.addProperty("stream", false);
            StringBuilder system = new StringBuilder();
            JsonArray arr = new JsonArray();
            String lastRole = null;
            for (Msg m : messages) {
                if (m.role.equals("system")) { system.append(m.content).append('\n'); continue; }
                String role = m.role.equals("assistant") ? "assistant" : "user";
                if (role.equals(lastRole)) { // the Messages API wants alternating roles: merge
                    JsonObject prev = arr.get(arr.size() - 1).getAsJsonObject();
                    prev.addProperty("content", prev.get("content").getAsString() + "\n\n" + m.content);
                    continue;
                }
                if (arr.size() == 0 && role.equals("assistant")) continue;
                JsonObject o = new JsonObject();
                o.addProperty("role", role);
                o.addProperty("content", m.content);
                arr.add(o);
                lastRole = role;
            }
            if (system.length() > 0) body.addProperty("system", system.toString());
            body.add("messages", arr);
            JsonObject r = com.star4droid.star2d.suda.Suda3D.aiMessages(context, body);
            if (cancelled) { cb.onDone(""); return; }
            StringBuilder text = new StringBuilder();
            for (JsonElement e : r.has("content") && r.get("content").isJsonArray() ? r.getAsJsonArray("content") : new JsonArray()) {
                JsonObject b = e.getAsJsonObject();
                if ("text".equals(str(b, "type"))) text.append(str(b, "text"));
            }
            JsonObject s = r.has("suda3d") && r.get("suda3d").isJsonObject() ? r.getAsJsonObject("suda3d") : null;
            if (s != null && s.has("cost") && s.get("cost").isJsonObject()) {
                JsonObject cost = s.getAsJsonObject("cost");
                double cr = 0;
                try { cr = cost.get("credits").getAsDouble(); } catch (Exception ignored) {}
                cb.onCost(com.star4droid.star2d.ui.Tr.t("s3d_reply_cost", "Reply cost") + ": "
                        + com.star4droid.star2d.suda.Suda3D.fmtCredits(cr) + " " + com.star4droid.star2d.ui.Tr.t("s3d_credits", "credits")
                        + " · " + com.star4droid.star2d.ui.Tr.t("s3d_balance", "Balance") + ": "
                        + com.star4droid.star2d.suda.Suda3D.fmtCredits(com.star4droid.star2d.suda.Suda3D.credits(context)));
            }
            cb.onToken(text.toString());
            cb.onDone(text.toString());
        } catch (com.star4droid.star2d.suda.Suda3D.ApiException e) {
            if (e.noBalance()) cb.onError("NO_CREDITS");
            else cb.onError(e.getMessage());
        } catch (Exception e) {
            cb.onError(com.star4droid.star2d.suda.Suda3D.message(e));
        }
    }

    public String providerId() { return prefs.getString("ai_provider", "openrouter"); }
    public Provider provider() { return provider(providerId()); }
    public String key(Provider p) { return prefs.getString(p.keyPref, ""); }
    public String model() { return prefs.getString("ai_model_" + providerId(), ""); }

    public String baseUrl(Provider p) {
        if (p.id.equals("custom")) {
            String u = prefs.getString("custom_base_url", "").trim();
            while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            return u;
        }
        return p.baseUrl;
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection c = current;
        if (c != null) try { c.disconnect(); } catch (Exception ignored) {}
    }

    /** Fetch the provider's model list (blocking; call off the UI thread). */
    public List<Model> listModels(Provider p) throws Exception {
        try {
            return listModelsFrom(p, null);
        } catch (Exception e) {
            if (!p.id.equals("gemini")) throw e;
            // fallback: Gemini native model list
            return listModelsFrom(p, "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=" + key(p));
        }
    }

    private List<Model> listModelsFrom(Provider p, String url) throws Exception {
        String base = baseUrl(p);
        if (base.isEmpty()) throw new Exception("Set the base URL first");
        HttpURLConnection c = (HttpURLConnection) new URL(url != null ? url : base + "/models").openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        String key = key(p);
        if (!key.isEmpty() && url == null) c.setRequestProperty("Authorization", "Bearer " + key);
        int code = c.getResponseCode();
        String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new Exception(errorMessage(code, body));
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray data = root.has("data") ? root.getAsJsonArray("data") : (root.has("models") ? root.getAsJsonArray("models") : new JsonArray());
        ArrayList<Model> out = new ArrayList<>();
        for (JsonElement e : data) {
            JsonObject o = e.getAsJsonObject();
            Model m = new Model();
            m.id = str(o, "id");
            if (m.id.isEmpty()) m.id = str(o, "name");
            if (m.id.startsWith("models/")) m.id = m.id.substring(7);
            m.name = str(o, "name");
            if (m.name.startsWith("models/")) m.name = m.name.substring(7);
            String low = m.id.toLowerCase(Locale.ROOT);
            if (p.id.equals("gemini") && (!low.contains("gemini") || low.contains("embedding") || low.contains("image")
                    || low.contains("tts") || low.contains("audio") || low.contains("live") || low.contains("robotics"))) continue;
            if (p.id.equals("openrouter")) {
                try {
                    JsonObject pr = o.getAsJsonObject("pricing");
                    m.free = "0".equals(str(pr, "prompt")) && "0".equals(str(pr, "completion"));
                } catch (Exception ignored) {}
                try {
                    JsonArray outMods = o.getAsJsonObject("architecture").getAsJsonArray("output_modalities");
                    boolean text = false;
                    for (JsonElement om : outMods) if ("text".equals(om.getAsString())) text = true;
                    if (!text) continue;
                } catch (Exception ignored) {}
            } else if (low.endsWith("-free") || low.endsWith(":free")) m.free = true;
            out.add(m);
        }
        Collections.sort(out, (a, b) -> a.free != b.free ? (a.free ? -1 : 1) : a.toString().compareToIgnoreCase(b.toString()));
        return out;
    }

    /** One message of the conversation. role: system | user | assistant */
    public static class Msg {
        public final String role, content;
        public Msg(String role, String content) { this.role = role; this.content = content; }
    }

    /** Stream a chat completion (blocking; call off the UI thread). */
    public void chat(List<Msg> messages, Stream cb) {
        cancelled = false;
        if (onPlan()) { planChat(messages, cb); return; }
        Provider p = provider();
        String model = model();
        StringBuilder full = new StringBuilder();
        try {
            if (p.needsKey && key(p).isEmpty()) throw new Exception("NO_KEY");
            if (model.isEmpty()) throw new Exception("NO_MODEL");
            String base = baseUrl(p);
            if (base.isEmpty()) throw new Exception("Set the base URL in AI settings");
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("stream", true);
            float temp = prefs.getFloat("ai_temperature", 0.4f);
            body.addProperty("temperature", temp);
            JsonArray arr = new JsonArray();
            for (Msg m : messages) {
                JsonObject o = new JsonObject();
                o.addProperty("role", m.role);
                o.addProperty("content", m.content);
                arr.add(o);
            }
            body.add("messages", arr);

            HttpURLConnection c = (HttpURLConnection) new URL(base + "/chat/completions").openConnection();
            current = c;
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(30000);
            c.setReadTimeout(180000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "text/event-stream");
            String key = key(p);
            if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
            if (p.id.equals("openrouter")) {
                c.setRequestProperty("HTTP-Referer", "https://github.com/star4droid/Star2D");
                c.setRequestProperty("X-Title", "Star2D Engine");
            }
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception(errorMessage(code, readAll(c.getErrorStream())));
            String ctype = c.getContentType() == null ? "" : c.getContentType();
            if (!ctype.contains("event-stream")) {
                // some servers ignore stream=true
                String txt = readAll(c.getInputStream());
                JsonObject o = JsonParser.parseString(txt).getAsJsonObject();
                String content = o.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                cb.onToken(content);
                cb.onDone(content);
                return;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!cancelled && (line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith(":")) continue;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    try {
                        JsonObject o = JsonParser.parseString(data).getAsJsonObject();
                        if (o.has("error")) throw new Exception(errorMessage(0, data));
                        JsonArray ch = o.getAsJsonArray("choices");
                        if (ch == null || ch.size() == 0) continue;
                        JsonObject c0 = ch.get(0).getAsJsonObject();
                        if (c0.has("error")) throw new Exception(errorMessage(0, c0.get("error").toString()));
                        JsonObject delta = c0.has("delta") ? c0.getAsJsonObject("delta") : null;
                        if (delta != null && delta.has("content") && !delta.get("content").isJsonNull()) {
                            String t = delta.get("content").getAsString();
                            if (!t.isEmpty()) {
                                full.append(t);
                                cb.onToken(t);
                            }
                        }
                    } catch (com.google.gson.JsonParseException ignored) {}
                }
            }
            if (cancelled) cb.onDone(full.toString() + (full.length() > 0 ? "\n\n_(stopped)_" : ""));
            else cb.onDone(full.toString());
        } catch (Exception e) {
            if (cancelled) cb.onDone(full.toString());
            else cb.onError(e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            current = null;
        }
    }

    static String errorMessage(int code, String body) {
        String msg = body;
        try {
            JsonElement el = JsonParser.parseString(body);
            if (el.isJsonArray() && el.getAsJsonArray().size() > 0) el = el.getAsJsonArray().get(0);
            JsonObject o = el.getAsJsonObject();
            if (o.has("error")) {
                JsonElement e = o.get("error");
                msg = e.isJsonObject() && e.getAsJsonObject().has("message") ? e.getAsJsonObject().get("message").getAsString() : e.toString();
            } else if (o.has("message")) msg = o.get("message").getAsString();
        } catch (Exception ignored) {}
        if (code == 401 || code == 403) return "Invalid or missing API key (" + code + "): " + msg;
        if (code == 429) return "Rate limit / quota reached (429). Wait a bit or pick another model. " + msg;
        if (code == 404) return "Model or URL not found (404). Pick another model. " + msg;
        return (code > 0 ? "HTTP " + code + ": " : "") + msg;
    }

    static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            return sb.toString();
        }
    }

    static String str(JsonObject o, String k) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? "" : e.getAsString();
        } catch (Exception ex) {
            return "";
        }
    }
}
