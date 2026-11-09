package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.ai.AiActions;
import com.star4droid.star2d.ui.ai.AiClient;
import com.star4droid.star2d.ui.ai.AiContext;
import com.star4droid.star2d.ui.ai.Markdown;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Star2D AI assistant: streaming chat (OpenRouter / Gemini / OpenCode / custom), engine-aware
 * project context, Markdown replies, and reviewed editor actions.
 */
public class AiPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    /** Runs the assistant's actions: tool actions (templates, buttons, icons...) must not run on the GL thread. */
    private final ExecutorService actionsExec = Executors.newSingleThreadExecutor();
    private AiClient client;
    private LinearLayout root, messagesBox;
    private ScrollView scroll;
    private EditText input;
    private ImageView sendBtn;
    private TextView modelChip;
    private boolean busy = false;
    private int autoRounds = 0;

    /** Stored chat (saved to <project>/ai_chats.json). */
    static class Message { String role, text; long time; Message(String r, String t) { role = r; text = t; time = System.currentTimeMillis(); } }
    static class Session { String id, title = ""; long time; ArrayList<Message> messages = new ArrayList<>(); }

    private Session session = new Session();
    private String projectPath;

    public AiPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("ui_ai", "AI assistant"); }
    @Override public int icon() { return R.drawable.ic_s2_ai; }

    private SharedPreferences prefs() { return Ui.prefs(shell.activity()); }

    private AiClient client() {
        if (client == null) { client = new AiClient(prefs()); client.setContext(shell.activity()); }
        return client;
    }

    @Override
    public View[] headerActions(Context c) {
        return new View[]{
                Ui.iconButton(c, R.drawable.ic_s2_history, Tr.t("ui_history", "History"), v -> showHistory(c)),
                Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("ui_new_chat", "New chat"), v -> newChat()),
                Ui.iconButton(c, R.drawable.ic_s2_key, Tr.t("ui_ai_settings", "AI settings"), v -> showSettings())
        };
    }

    @Override
    public View view(Context c) {
        if (root != null) {
            refreshModelChip();
            return root;
        }
        root = Ui.column(c);
        // model chip row
        LinearLayout top = Ui.row(c);
        top.setPadding(Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 4));
        modelChip = Ui.text(c, "", 12.5f, Ui.TEXT);
        modelChip.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
        modelChip.setBackground(Ui.ripple(Ui.SURFACE_3, Ui.dp(c, 14)));
        modelChip.setMaxLines(1);
        modelChip.setOnClickListener(v -> pickModel(c, null));
        top.addView(modelChip, new LinearLayout.LayoutParams(0, -2, 1));
        SwitchCompat ctx = new SwitchCompat(c);
        ctx.setText(Tr.t("ui_ai_context", "Project"));
        ctx.setTextColor(Ui.TEXT_2);
        ctx.setTextSize(12);
        ctx.setChecked(prefs().getBoolean("ai_send_context", true));
        ctx.setOnCheckedChangeListener((b, on) -> prefs().edit().putBoolean("ai_send_context", on).apply());
        top.addView(ctx);
        root.addView(top, Ui.matchWrap());

        scroll = new ScrollView(c);
        messagesBox = Ui.column(c);
        messagesBox.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 10));
        scroll.addView(messagesBox);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bar = Ui.row(c);
        bar.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 8));
        bar.setGravity(Gravity.BOTTOM);
        input = Ui.input(c, "", Tr.t("ui_ai_hint", "Ask anything… e.g. make the player jump when I tap"));
        input.setSingleLine(false);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bar.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        sendBtn = Ui.iconButton(c, R.drawable.ic_s2_send, Tr.t("ui_send", "Send"), v -> {
            if (busy) client().cancel();
            else send(input.getText().toString(), true);
        });
        sendBtn.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 22)));
        LinearLayout.LayoutParams sl = (LinearLayout.LayoutParams) sendBtn.getLayoutParams();
        sl.setMarginStart(Ui.dp(c, 6));
        bar.addView(sendBtn);
        root.addView(bar, Ui.matchWrap());
        refreshModelChip();
        return root;
    }

    @Override
    public void onShown() {
        if (!Bridge.ready()) return;
        Bridge.query(() -> Bridge.editor().getProject().getPath(), p -> {
            if (!p.equals(projectPath)) {
                projectPath = p;
                session = new Session();
                session.id = String.valueOf(System.currentTimeMillis());
                renderAll();
            } else if (messagesBox.getChildCount() == 0) renderAll();
        });
        if (!client().onPlan() && client().key(client().provider()).isEmpty() && client().provider().needsKey) showWelcomeSetup();
    }

    // ================================================================ rendering

    private void renderAll() {
        if (messagesBox == null) return;
        messagesBox.removeAllViews();
        if (session.messages.isEmpty()) {
            renderWelcome();
            return;
        }
        for (Message m : session.messages) addBubble(m);
        scrollDown();
    }

    private void renderWelcome() {
        Context c = messagesBox.getContext();
        boolean ar = Ui.isRtl(c);
        TextView t = Ui.title(c, ar ? "✨ مساعد Star2D" : "✨ Star2D Assistant");
        t.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));
        messagesBox.addView(t);
        messagesBox.addView(Ui.text(c, ar
                ? "أفهم مشروعك (العناصر، الأحداث، المتغيرات) وأستطيع إضافة عناصر وتعديل خصائص وكتابة الأحداث بعد موافقتك."
                : "I understand your project (elements, events, variables) and can add elements, change properties and write events after you approve.", 13.5f, Ui.TEXT_2));
        String[] ideas = ar ? new String[]{
                "اجعل اللاعب يقفز عند الضغط على زر",
                "أضف عملات تُجمع وعدّاد نقاط",
                "اشرح لي مشهدي الحالي",
                "حرّك اللاعب بعصا التحكم",
                "اجعل العدو يطارد اللاعب"} : new String[]{
                "Make the player jump when I tap a button",
                "Add collectible coins and a score counter",
                "Explain my current scene",
                "Move the player with a joystick",
                "Make the enemy chase the player"};
        for (String idea : ideas) {
            TextView chip = Ui.text(c, "💡 " + idea, 13.5f, Ui.TEXT);
            chip.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 12), Ui.dp(c, 9));
            chip.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(c, 10)));
            chip.setOnClickListener(v -> send(idea, true));
            messagesBox.addView(chip, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        }
    }

    private View addBubble(Message m) {
        Context c = messagesBox.getContext();
        LinearLayout wrap = Ui.column(c);
        wrap.setTag("msg");
        boolean user = m.role.equals("user");
        boolean tool = m.role.equals("tool");
        LinearLayout bubble = Ui.column(c);
        bubble.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        if (user) {
            bubble.setBackground(Ui.rounded(0xFF1E3A5F, Ui.dp(c, 14)));
            TextView t = Ui.text(c, m.text, 14, Ui.TEXT);
            t.setTextIsSelectable(true);
            bubble.addView(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.gravity = Gravity.END;
            lp.setMargins(Ui.dp(c, 40), Ui.dp(c, 8), 0, 0);
            wrap.addView(bubble, lp);
        } else if (tool) {
            TextView t = Ui.text(c, "🔧 " + firstLine(m.text), 12, Ui.TEXT_3);
            t.setOnClickListener(v -> Dialogs.longText(c, "Tool result", m.text));
            wrap.addView(t, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        } else {
            bubble.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 14)));
            Markdown.render(c, bubble, m.text, this::saveCode);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 16), 0);
            wrap.addView(bubble, lp);
            JsonArray actions = AiActions.parse(m.text);
            ArrayList<JsonObject> changes = new ArrayList<>();
            for (JsonElement e : actions) if (e.isJsonObject() && !AiActions.isReadOnly(e.getAsJsonObject())) changes.add(e.getAsJsonObject());
            if (!changes.isEmpty()) wrap.addView(actionCard(c, changes));
            bubble.setOnLongClickListener(v -> { Dialogs.copy(c, m.text); return true; });
        }
        messagesBox.addView(wrap);
        return wrap;
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    private View actionCard(Context c, ArrayList<JsonObject> actions) {
        boolean ar = Ui.isRtl(c);
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.rounded(0xFF1D2A22, Ui.dp(c, 12), 0xFF2E7D32, Ui.dp(c, 1)));
        card.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        TextView h = Ui.text(c, ar ? "تغييرات مقترحة على المشروع" : "Proposed project changes", 13.5f, Ui.TEXT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(h);
        for (JsonObject a : actions) {
            TextView t = Ui.text(c, AiActions.describe(a, ar), 13, Ui.TEXT_2);
            t.setPadding(0, Ui.dp(c, 4), 0, 0);
            card.addView(t);
        }
        LinearLayout btns = Ui.row(c);
        btns.setPadding(0, Ui.dp(c, 8), 0, 0);
        TextView result = Ui.text(c, "", 12, Ui.SUCCESS);
        LinearLayout apply = Ui.button(c, R.drawable.ic_s2_check, ar ? "تطبيق" : "Apply", true, null);
        apply.setOnClickListener(v -> {
            apply.setEnabled(false);
            apply.setAlpha(0.5f);
            // worker thread: tool actions (templates, buttons, icons, scenes) wait for the GL thread, GL actions are posted to it
            actionsExec.execute(() -> {
                StringBuilder sb = new StringBuilder();
                for (JsonObject a : actions) sb.append("• ").append(AiActions.runAny(a)).append("\n");
                final String applied = sb.toString().trim();
                Bridge.query(() -> {
                    Bridge.controls().actionSave();
                    return applied;
                }, res -> {
                    result.setText(res);
                    shell.refreshAll();
                    session.messages.add(new Message("tool", "Applied:\n" + res));
                    persist();
                });
            });
        });
        btns.addView(apply);
        LinearLayout copy = Ui.button(c, R.drawable.ic_s2_code, ar ? "عرض" : "Details", false, v -> {
            StringBuilder sb = new StringBuilder();
            for (JsonObject a : actions) sb.append(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(a)).append("\n");
            Dialogs.longText(c, "Actions", sb.toString());
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, Ui.dp(c, 38));
        cl.setMarginStart(Ui.dp(c, 8));
        btns.addView(copy, cl);
        card.addView(btns);
        card.addView(result);
        LinearLayout.LayoutParams lp = Ui.margins(Ui.matchWrap(), c, 0, 6, 16, 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void scrollDown() {
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // ================================================================ chat

    /** Called from the shell after a failed build / runtime error. */
    public void askToFix(String error) {
        boolean ar = Ui.isRtl(shell.activity());
        String msg = (ar ? "حدث هذا الخطأ عند تشغيل اللعبة. اشرحه ببساطة وأصلحه:\n```\n" : "This error happened when running the game. Explain it simply and fix it:\n```\n")
                + AiContext_trim(error, 6000) + "\n```";
        if (root == null) view(shell.activity());
        send(msg, true);
    }

    private static String AiContext_trim(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private void send(String text, boolean fromUser) {
        if (text.trim().isEmpty() || busy) return;
        Context c = shell.activity();
        AiClient.Provider p = client().provider();
        if (!client().onPlan() && p.needsKey && client().key(p).isEmpty()) {
            Ui.toast(c, Tr.t("ui_need_key", "Add your API key first"));
            showSettings();
            return;
        }
        if (!client().onPlan() && client().model().isEmpty()) {
            pickModel(c, () -> send(text, fromUser));
            return;
        }
        if (fromUser) {
            input.setText("");
            autoRounds = 0;
        }
        Message um = new Message(fromUser ? "user" : "tool", text);
        if (session.messages.isEmpty()) session.title = text.length() > 40 ? text.substring(0, 40) + "…" : text;
        session.messages.add(um);
        if (session.messages.size() == 1) messagesBox.removeAllViews();
        addBubble(um);

        // streaming bubble
        LinearLayout bubble = Ui.column(c);
        bubble.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 14)));
        bubble.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        TextView live = Ui.text(c, "●●●", 14, Ui.TEXT_2);
        bubble.addView(live);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 16), 0);
        messagesBox.addView(bubble, lp);
        scrollDown();
        setBusy(true);

        boolean withContext = prefs().getBoolean("ai_send_context", true);
        Bridge.query(() -> withContext ? AiContext.snapshot(true) : "", snapshot -> net.execute(() -> {
            List<AiClient.Msg> msgs = new ArrayList<>();
            boolean ar = Ui.isRtl(c);
            msgs.add(new AiClient.Msg("system", AiContext.ENGINE_GUIDE
                    + (prefs().getBoolean(AiContext.PRO_PREF, true) ? "\n" + AiContext.proGuide() + "\n" : "")
                    + (ar ? "\nThe user interface is in Arabic: reply in Arabic unless asked otherwise.\n" : "")
                    + (snapshot.isEmpty() ? "" : "\n" + snapshot)));
            int from = Math.max(0, session.messages.size() - 24);
            for (int i = from; i < session.messages.size(); i++) {
                Message m = session.messages.get(i);
                msgs.add(new AiClient.Msg(m.role.equals("assistant") ? "assistant" : "user",
                        m.role.equals("tool") ? "[Result of your actions]\n" + m.text : m.text));
            }
            final StringBuilder acc = new StringBuilder();
            final long[] lastUi = {0};
            client().chat(msgs, new AiClient.Stream() {
                @Override public void onToken(String t) {
                    acc.append(t);
                    long now = System.currentTimeMillis();
                    if (now - lastUi[0] > 80) {
                        lastUi[0] = now;
                        String snap = acc.toString();
                        Ui.ui(() -> { live.setText(Markdown.inline(snap)); scrollDown(); });
                    }
                }

                @Override public void onCost(String cost) {
                    Ui.ui(() -> {
                        TextView ct = Ui.text(c, cost, 11.5f, Ui.TEXT_3);
                        ct.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), 0);
                        messagesBox.addView(ct);
                    });
                }

                @Override public void onDone(String full) {
                    Ui.ui(() -> {
                        messagesBox.removeView(bubble);
                        setBusy(false);
                        if (full.trim().isEmpty()) return;
                        Message am = new Message("assistant", full);
                        session.messages.add(am);
                        addBubble(am);
                        persist();
                        scrollDown();
                        runReadOnlyActions(full);
                    });
                }

                @Override public void onError(String message) {
                    Ui.ui(() -> {
                        messagesBox.removeView(bubble);
                        setBusy(false);
                        if (message.equals("NO_CREDITS")) { com.star4droid.star2d.suda.Paywall.fakeProgress(c, Tr.t("ui_ai", "AI assistant")); return; }
                        showError(c, message);
                    });
                }
            });
        }));
    }

    /** Auto-run read_file / list_files / get_element and send results back (max 4 rounds). */
    private void runReadOnlyActions(String reply) {
        JsonArray actions = AiActions.parse(reply);
        ArrayList<JsonObject> ro = new ArrayList<>();
        for (JsonElement e : actions) if (e.isJsonObject() && AiActions.isReadOnly(e.getAsJsonObject())) ro.add(e.getAsJsonObject());
        if (ro.isEmpty() || autoRounds >= 4) return;
        autoRounds++;
        actionsExec.execute(() -> {
            StringBuilder sb = new StringBuilder();
            for (JsonObject a : ro) sb.append(AiActions.runAny(a)).append("\n\n");
            final String res = sb.toString();
            Ui.ui(() -> send(res, false));
        });
    }

    private void showError(Context c, String message) {
        boolean ar = Ui.isRtl(c);
        String friendly = message;
        if (message.equals("NO_KEY")) friendly = ar ? "أضف مفتاح API من الإعدادات." : "Add your API key in settings.";
        else if (message.equals("NO_MODEL")) friendly = ar ? "اختر موديلاً أولاً." : "Pick a model first.";
        TextView t = Ui.text(c, "⚠ " + friendly, 13, 0xFFFF8A80);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        t.setBackground(Ui.rounded(0x33EF5350, Ui.dp(c, 10)));
        t.setTextIsSelectable(true);
        messagesBox.addView(t, Ui.margins(Ui.matchWrap(), c, 0, 8, 0, 0));
        LinearLayout row = Ui.row(c);
        row.addView(Ui.button(c, R.drawable.ic_s2_key, ar ? "الإعدادات" : "Settings", false, v -> showSettings()));
        View sp = new View(c);
        row.addView(sp, new LinearLayout.LayoutParams(Ui.dp(c, 8), 1));
        row.addView(Ui.button(c, R.drawable.ic_s2_ai, ar ? "موديل آخر" : "Other model", false, v -> pickModel(c, null)));
        messagesBox.addView(row, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        scrollDown();
    }

    private void setBusy(boolean b) {
        busy = b;
        if (sendBtn != null) {
            sendBtn.setImageResource(b ? R.drawable.ic_s2_stop : R.drawable.ic_s2_send);
            sendBtn.setBackground(Ui.ripple(b ? Ui.DANGER : Ui.ACCENT, Ui.dp(sendBtn.getContext(), 22)));
        }
    }

    private void newChat() {
        if (busy) client().cancel();
        persist();
        session = new Session();
        session.id = String.valueOf(System.currentTimeMillis());
        renderAll();
    }

    /** Save a code block: body script / scene script / any file (asks for the path). */
    private void saveCode(String lang, String code) {
        Context c = shell.activity();
        Bridge.query(() -> {
            String scene = Bridge.editor().getScene();
            String cls = null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("class\\s+(\\w+)").matcher(code);
            if (m.find()) cls = m.group(1);
            if (code.contains("extends SceneScript") || code.contains("SceneRefs")) return "java/com/star4droid/Game/SceneScript/" + scene.toLowerCase(Locale.ROOT) + "Script.java";
            if (cls != null) return "java/com/star4droid/Game/Scripts/" + scene + "/" + cls + ".java";
            return "scripts/NewScript.java";
        }, suggested -> Dialogs.input(c, Tr.t("ui_save_to_project", "Save to project"), Tr.t("ui_path", "Path in the project"), suggested, v -> v.contains("..") ? "Invalid" : null,
                path -> Bridge.query(() -> {
                    JsonObject a = new JsonObject();
                    a.addProperty("action", "write_file");
                    a.addProperty("path", path);
                    a.addProperty("content", code);
                    return AiActions.run(a);
                }, res -> Ui.toast(c, res))));
    }

    // ================================================================ history

    private File historyFile() {
        return projectPath == null ? null : new File(projectPath, "ai_chats.json");
    }

    private ArrayList<Session> loadHistory() {
        File f = historyFile();
        if (f == null || !f.exists()) return new ArrayList<>();
        try {
            ArrayList<Session> l = new Gson().fromJson(com.star4droid.star2d.ui.blocks.BlockModel.read(f), new TypeToken<ArrayList<Session>>() {}.getType());
            return l == null ? new ArrayList<>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void persist() {
        File f = historyFile();
        if (f == null || session.messages.isEmpty()) return;
        session.time = System.currentTimeMillis();
        if (session.id == null) session.id = String.valueOf(session.time);
        net.execute(() -> {
            try {
                ArrayList<Session> all = loadHistory();
                boolean found = false;
                for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(session.id)) { all.set(i, session); found = true; }
                if (!found) all.add(0, session);
                while (all.size() > 40) all.remove(all.size() - 1);
                com.star4droid.star2d.ui.blocks.BlockModel.write(f, new Gson().toJson(all));
            } catch (Exception ignored) {}
        });
    }

    private void showHistory(Context c) {
        ArrayList<Session> all = loadHistory();
        if (all.isEmpty()) {
            Ui.toast(c, Tr.t("ui_no_history", "No previous chats"));
            return;
        }
        String[] items = new String[all.size()];
        SimpleDateFormat f = new SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH);
        for (int i = 0; i < items.length; i++) items[i] = (all.get(i).title.isEmpty() ? "Chat" : all.get(i).title) + "\n" + f.format(new Date(all.get(i).time));
        new AlertDialog.Builder(c)
                .setTitle(Tr.t("ui_history", "History"))
                .setItems(items, (d, w) -> {
                    session = all.get(w);
                    renderAll();
                })
                .setNeutralButton(Tr.t("ui_clear_all", "Clear all"), (d, w) -> {
                    File hf = historyFile();
                    if (hf != null) hf.delete();
                    newChat();
                })
                .setNegativeButton(Tr.t("close", "Close"), null)
                .show();
    }

    // ================================================================ settings

    private void refreshModelChip() {
        if (modelChip == null) return;
        AiClient.Provider p = client().provider();
        String m = client().model();
        modelChip.setText("✨ " + p.name + " · " + (m.isEmpty() ? Tr.t("ui_choose_model", "choose a model") : m) + "  ▾");
    }

    private void showWelcomeSetup() {
        Context c = shell.activity();
        boolean ar = Ui.isRtl(c);
        new AlertDialog.Builder(c)
                .setTitle(ar ? "إعداد المساعد الذكي" : "Set up the AI assistant")
                .setMessage(ar ? "للبدء تحتاج مفتاح API مجاني.\n\nالأسهل: OpenRouter — مفتاح واحد لمئات الموديلات، وكثير منها مجاني (مكتوب بجانبه FREE).\n\n1) افتح openrouter.ai/keys وأنشئ مفتاحاً\n2) الصقه هنا\n3) اختر موديلاً مجانياً"
                        : "You need a free API key to start.\n\nEasiest: OpenRouter — one key for hundreds of models, many FREE.\n\n1) Open openrouter.ai/keys and create a key\n2) Paste it here\n3) Pick a FREE model")
                .setPositiveButton(ar ? "إعداد الآن" : "Set up now", (d, w) -> showSettings())
                .setNegativeButton(Tr.t("cancel", "Later"), null)
                .show();
    }

    public void showSettings() {
        Context c = shell.activity();
        boolean ar = Ui.isRtl(c);
        SharedPreferences p = prefs();
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 18), Ui.dp(c, 4), Ui.dp(c, 18), Ui.dp(c, 6));

        col.addView(Ui.sectionLabel(c, ar ? "المزوّد" : "Provider"));
        final String[] chosen = {p.getString("ai_provider", "openrouter")};
        LinearLayout provBox = Ui.column(c);
        col.addView(provBox);
        TextView help = Ui.text(c, "", 12.5f, Ui.TEXT_3);
        EditText key = Ui.input(c, "", "API key");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText base = Ui.input(c, p.getString("custom_base_url", ""), "https://api.example.com/v1");
        TextView baseLabel = Ui.text(c, ar ? "رابط الخادم (Base URL)" : "Base URL", 12.5f, Ui.TEXT_2);
        LinearLayout getKey = Ui.button(c, R.drawable.ic_s2_open_in_new, ar ? "احصل على مفتاح" : "Get a key", false, null);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            provBox.removeAllViews();
            for (AiClient.Provider pr : AiClient.PROVIDERS) {
                TextView t = Ui.text(c, (pr.id.equals(chosen[0]) ? "◉  " : "○  ") + pr.name, 14.5f, Ui.TEXT);
                t.setPadding(Ui.dp(c, 8), Ui.dp(c, 9), Ui.dp(c, 8), Ui.dp(c, 9));
                t.setBackground(pr.id.equals(chosen[0]) ? Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(c, 8), Ui.ACCENT, 1) : Ui.ripple(0, Ui.dp(c, 8)));
                t.setOnClickListener(v -> {
                    p.edit().putString(pr.keyPref, key.getText().toString().trim()).apply();
                    chosen[0] = pr.id;
                    refresh[0].run();
                });
                provBox.addView(t, Ui.margins(Ui.matchWrap(), c, 0, 2, 0, 2));
            }
            AiClient.Provider pr = AiClient.provider(chosen[0]);
            help.setText(pr.help);
            key.setText(p.getString(pr.keyPref, ""));
            key.setHint(pr.needsKey ? "API key" : "API key (optional)");
            boolean custom = pr.id.equals("custom");
            base.setVisibility(custom ? View.VISIBLE : View.GONE);
            baseLabel.setVisibility(custom ? View.VISIBLE : View.GONE);
            getKey.setVisibility(pr.keyUrl.isEmpty() ? View.GONE : View.VISIBLE);
            getKey.setOnClickListener(v -> {
                try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pr.keyUrl))); } catch (Exception ignored) {}
            });
        };
        help.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        col.addView(help);
        col.addView(Ui.sectionLabel(c, ar ? "مفتاح API" : "API key"));
        col.addView(key, Ui.matchWrap());
        TextView privacy = Ui.text(c, ar ? "يُحفظ المفتاح على جهازك فقط ويُرسل للمزوّد مباشرة." : "Stored only on this device and sent directly to the provider.", 11.5f, Ui.TEXT_3);
        col.addView(privacy);
        col.addView(getKey, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 36)), c, 0, 6, 0, 0));
        col.addView(baseLabel);
        col.addView(base, Ui.matchWrap());

        col.addView(Ui.sectionLabel(c, ar ? "الإبداع (Temperature)" : "Creativity (temperature)"));
        TextView tv = Ui.text(c, String.valueOf(p.getFloat("ai_temperature", 0.4f)), 13, Ui.TEXT_2);
        SeekBar sb = new SeekBar(c);
        sb.setMax(10);
        sb.setProgress(Math.round(p.getFloat("ai_temperature", 0.4f) * 10));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { tv.setText(String.valueOf(v / 10f)); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout tr = Ui.row(c);
        tr.addView(sb, Ui.weight(1));
        tr.addView(tv);
        col.addView(tr, Ui.matchWrap());
        col.addView(Ui.sectionLabel(c, ar ? "تعليمات احترافية" : "Professional instructions"));
        SwitchCompat pro = new SwitchCompat(c);
        pro.setText(ar ? "تمرير التعليمات الاحترافية للذكاء الاصطناعي" : "Send professional guidance to the AI");
        pro.setTextColor(Ui.TEXT);
        pro.setTextSize(13.5f);
        pro.setChecked(p.getBoolean(AiContext.PRO_PREF, true));
        pro.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean(AiContext.PRO_PREF, on).apply());
        col.addView(pro, Ui.matchWrap());
        int proTokens = AiContext.proGuideTokens();
        TextView proHelp = Ui.text(c, ar
                ? "الاتجاه والأبعاد، أرقام الفيزياء والقفز، ترتيب z، القوالب الجاهزة، الأزرار والأيقونات. \u26A0 يضيف حوالي " + proTokens + " توكن لكل رسالة، فقد يستهلك رصيداً أكثر أو لا يناسب الموديلات المجانية المحدودة. أطفئه إن احتجت."
                : "Orientation and frame, physics and jump numbers, z order, ready templates, buttons and icons. \u26A0 Adds about " + proTokens + " tokens to every request, so it may use more credit or not suit small free models. Turn it off if needed.", 12, Ui.TEXT_3);
        proHelp.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 4));
        col.addView(proHelp);
        TextView how = Ui.text(c, ar ? "\nنصائح: اختر موديلاً قوياً في البرمجة. إذا ظهر خطأ 429 فهذا حد الاستخدام للموديل المجاني، جرّب موديلاً آخر أو انتظر قليلاً."
                : "\nTips: pick a model that's good at coding. Error 429 means a free model's rate limit — try another model or wait a bit.", 12, Ui.TEXT_3);
        col.addView(how);
        refresh[0].run();

        ScrollView sv = new ScrollView(c);
        sv.addView(col);
        new AlertDialog.Builder(c)
                .setTitle(ar ? "إعدادات المساعد الذكي" : "AI assistant settings")
                .setView(sv)
                .setPositiveButton(ar ? "حفظ واختيار موديل" : "Save & pick model", (d, w) -> {
                    AiClient.Provider pr = AiClient.provider(chosen[0]);
                    p.edit().putString("ai_provider", chosen[0])
                            .putString(pr.keyPref, key.getText().toString().trim())
                            .putString("custom_base_url", base.getText().toString().trim())
                            .putFloat("ai_temperature", sb.getProgress() / 10f)
                            .apply();
                    refreshModelChip();
                    pickModel(c, null);
                })
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    /** Fetch and choose a model (searchable), with manual entry. */
    private void pickModel(Context c, Runnable after) {
        boolean ar = Ui.isRtl(c);
        AiClient.Provider p = client().provider();
        AlertDialog loading = new AlertDialog.Builder(c).setMessage(ar ? "جاري جلب الموديلات…" : "Loading models…").show();
        net.execute(() -> {
            List<AiClient.Model> models;
            String err = null;
            try { models = client().listModels(p); } catch (Exception e) { models = new ArrayList<>(); err = e.getMessage(); }
            final List<AiClient.Model> fm = models;
            final String ferr = err;
            Ui.ui(() -> {
                loading.dismiss();
                LinearLayout col = Ui.column(c);
                col.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), 0);
                if (ferr != null) {
                    TextView t = Ui.text(c, "⚠ " + ferr, 12.5f, 0xFFFF8A80);
                    col.addView(t);
                }
                EditText search = Ui.input(c, "", ar ? "ابحث أو اكتب اسم الموديل" : "Search or type a model id");
                col.addView(search, Ui.matchWrap());
                LinearLayout list = Ui.column(c);
                ScrollView sv = new ScrollView(c);
                sv.addView(list);
                col.addView(sv, new LinearLayout.LayoutParams(-1, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.5f)));
                AlertDialog[] dlg = new AlertDialog[1];
                Runnable fill = () -> {
                    list.removeAllViews();
                    String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                    int shown = 0;
                    for (AiClient.Model m : fm) {
                        if (!q.isEmpty() && !m.toString().toLowerCase(Locale.ROOT).contains(q) && !m.id.toLowerCase(Locale.ROOT).contains(q)) continue;
                        if (++shown > 300) break;
                        TextView t = Ui.text(c, m.toString(), 14, m.free ? 0xFF81C784 : Ui.TEXT);
                        t.setPadding(Ui.dp(c, 6), Ui.dp(c, 9), Ui.dp(c, 6), Ui.dp(c, 9));
                        t.setBackground(Ui.ripple(m.id.equals(client().model()) ? Ui.ACCENT_SOFT : 0, Ui.dp(c, 6)));
                        t.setOnClickListener(v -> {
                            prefs().edit().putString("ai_model_" + p.id, m.id).apply();
                            refreshModelChip();
                            dlg[0].dismiss();
                            if (after != null) after.run();
                        });
                        list.addView(t, Ui.matchWrap());
                    }
                };
                search.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
                    @Override public void afterTextChanged(android.text.Editable s) { fill.run(); }
                });
                fill.run();
                dlg[0] = new AlertDialog.Builder(c)
                        .setTitle((ar ? "اختر موديل · " : "Choose model · ") + p.name)
                        .setView(col)
                        .setPositiveButton(ar ? "استخدم المكتوب" : "Use typed id", (d, w) -> {
                            String id = search.getText().toString().trim();
                            if (id.isEmpty()) return;
                            prefs().edit().putString("ai_model_" + p.id, id).apply();
                            refreshModelChip();
                            if (after != null) after.run();
                        })
                        .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                        .show();
            });
        });
    }
}
