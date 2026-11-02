package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Game translations editor: strings/strings.json of the project.
 * Pick a language chip, then write the text of every key in it. "+" adds a language or a key.
 * In the game: the block "translate", a TEXT element whose text is "@key", or "Set language" switch between them.
 */
public class StringsDialog {
    private final Context c;
    private final File file;
    private String def = "en";
    private String editorLang = "";   // language the editor shows ("" = the first one)
    private final ArrayList<String> languages = new ArrayList<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> keys = new LinkedHashMap<>();
    private String shown;
    private LinearLayout chips, list;
    private AlertDialog dialog;
    private TextView editorView;

    /** Common languages offered by the "+" chip (code, name). */
    private static final String[][] KNOWN = {{"en", "English"}, {"ar", "العربية"}, {"fr", "Français"}, {"es", "Español"}, {"pt", "Português"},
            {"ru", "Русский"}, {"de", "Deutsch"}, {"tr", "Türkçe"}, {"it", "Italiano"}, {"zh", "中文"}, {"ja", "日本語"}, {"ko", "한국어"},
            {"hi", "हिन्दी"}, {"id", "Indonesia"}, {"fa", "فارسی"}, {"ur", "اردو"}};

    public static void show(Context c, File project) { new StringsDialog(c, project).open(); }

    private StringsDialog(Context c, File project) {
        this.c = c;
        file = new File(project, "strings/strings.json");
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            if (file.isFile()) {
                byte[] b = new byte[(int) file.length()];
                try (FileInputStream in = new FileInputStream(file)) { in.read(b); }
                Map<String, Object> m = new Gson().fromJson(new String(b, "UTF-8"), new TypeToken<Map<String, Object>>() {}.getType());
                if (m.get("default") != null) def = String.valueOf(m.get("default"));
                if (m.get("editor") != null) editorLang = String.valueOf(m.get("editor"));
                if (m.get("languages") instanceof java.util.List) for (Object o : (java.util.List<Object>) m.get("languages")) languages.add(String.valueOf(o));
                if (m.get("keys") instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("keys")).entrySet()) {
                    LinkedHashMap<String, String> t = new LinkedHashMap<>();
                    if (e.getValue() instanceof Map) for (Map.Entry<String, Object> x : ((Map<String, Object>) e.getValue()).entrySet()) t.put(x.getKey(), String.valueOf(x.getValue()));
                    keys.put(e.getKey(), t);
                }
            }
        } catch (Exception ignored) {}
        if (languages.isEmpty()) languages.add(def);
        shown = languages.get(0);
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("default", def);
            m.put("languages", languages);
            if (!editorLang.isEmpty() && languages.contains(editorLang)) m.put("editor", editorLang);
            m.put("keys", keys);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(m).getBytes("UTF-8"));
            }
            Snack.show(Tr.t("saved", "Saved") + " ✓");
            // texts written as @key show the new translation in the editor
            try {
                Bridge.gl(() -> {
                    for (com.badlogic.gdx.scenes.scene2d.Actor a : Bridge.editor().getActors())
                        if (a instanceof com.star4droid.star2d.editor.items.EditorTextItem) ((com.star4droid.star2d.editor.items.EditorTextItem) a).update();
                });
            } catch (Throwable ignored) {}
        } catch (Exception e) {
            Snack.show(e.toString());
        }
    }

    private void open() {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), 0);
        col.addView(Ui.text(c, Tr.t("strings_hint", "Write each text once per language. Use it with the block \"Translate\", or type @key in a TEXT element."), 12, Ui.TEXT_3));
        TextView view = Ui.text(c, "", 13, Ui.ACCENT);
        view.setPadding(0, Ui.dp(c, 8), 0, 0);
        view.setOnClickListener(x -> Dialogs.choose(c, Tr.t("strings_editor_lang", "Language shown in the editor"), languages.toArray(new String[0]), i -> {
            editorLang = languages.get(i);
            refresh();
        }));
        col.addView(view);
        editorView = view;
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        chips = Ui.row(c);
        chips.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        hs.addView(chips);
        col.addView(hs);
        list = Ui.column(c);
        ScrollView sv = new ScrollView(c);
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.5f)));
        refresh();
        dialog = new AlertDialog.Builder(c)
                .setTitle(Tr.t("strings_title", "Game translations"))
                .setView(col)
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> save())
                .setNegativeButton(Tr.t("close", "Close"), null)
                .create();
        dialog.show();
        // AlertDialog sets FLAG_ALT_FOCUSABLE_IM when the view has no EditText at show time (the list can be empty, fields come later): that hides the keyboard
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private String shownInEditor() { return !editorLang.isEmpty() && languages.contains(editorLang) ? editorLang : languages.get(0); }

    private void refresh() {
        editorView.setText("👁 " + Tr.t("strings_editor_lang", "Language shown in the editor") + ": " + shownInEditor() + "  ▾");
        chips.removeAllViews();
        for (String l : languages) {
            TextView t = Ui.text(c, l + (l.equals(def) ? " ★" : ""), 13.5f, Ui.TEXT);
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
            t.setBackground(Ui.rounded(l.equals(shown) ? Ui.ACCENT : Ui.SURFACE_2, Ui.dp(c, 16)));
            t.setOnClickListener(v -> { shown = l; refresh(); });
            t.setOnLongClickListener(v -> { langMenu(l); return true; });
            chips.addView(t, Ui.margins(new LinearLayout.LayoutParams(-2, -2), c, 0, 0, 6, 0));
        }
        TextView add = Ui.text(c, "+", 16, Ui.TEXT);
        add.setPadding(Ui.dp(c, 14), Ui.dp(c, 5), Ui.dp(c, 14), Ui.dp(c, 5));
        add.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, 16)));
        add.setOnClickListener(v -> addLanguage());
        chips.addView(add);

        list.removeAllViews();
        TextView hint = Ui.text(c, Tr.t("strings_long_press", "Hold a language to make it the default or delete it."), 11.5f, Ui.TEXT_3);
        list.addView(hint);
        for (String k : new ArrayList<>(keys.keySet())) {
            LinearLayout row = Ui.column(c);
            row.setPadding(0, Ui.dp(c, 8), 0, 0);
            LinearLayout head = Ui.row(c);
            TextView kn = Ui.text(c, "@" + k, 12.5f, Ui.TEXT_2);
            kn.setTypeface(Typeface.DEFAULT_BOLD);
            head.addView(kn, Ui.weight(1));
            TextView del = Ui.text(c, "✕", 14, Ui.DANGER);
            del.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 4), 0);
            del.setOnClickListener(v -> Dialogs.confirm(c, Tr.t("delete", "Delete"), "@" + k, null, () -> { keys.remove(k); refresh(); }));
            head.addView(del);
            row.addView(head);
            String cur = keys.get(k).get(shown);
            EditText e = Ui.input(c, cur == null ? "" : cur, l10nHint(k));
            e.setSingleLine(false);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setGravity(Gravity.START);
            e.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                public void onTextChanged(CharSequence s, int a, int b, int d) {}
                public void afterTextChanged(Editable s) { keys.get(k).put(shown, s.toString()); }
            });
            row.addView(e);
            list.addView(row);
        }
        TextView newKey = Ui.text(c, "+ " + Tr.t("strings_new_key", "New text"), 14, Ui.ACCENT);
        newKey.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 8));
        newKey.setOnClickListener(v -> Dialogs.input(c, Tr.t("strings_new_key", "New text"), Tr.t("strings_key_hint", "A short name, e.g. play or game_over (letters, digits, _)"), "", s -> {
            String x = s == null ? "" : s.trim();
            return x.matches("[A-Za-z0-9_]+") && !keys.containsKey(x) ? null : Tr.t("strings_key_bad", "Letters, digits and _ only, and not used before");
        }, s -> { keys.put(s.trim(), new LinkedHashMap<>()); refresh(); }));
        list.addView(newKey);
    }

    private String l10nHint(String k) {
        String d = keys.get(k).get(def);
        return d != null && !d.isEmpty() && !shown.equals(def) ? d : Tr.t("strings_write_here", "Text in") + " " + shown;
    }

    private void langMenu(String l) {
        Dialogs.choose(c, l, new String[]{Tr.t("strings_make_default", "Make default"), Tr.t("delete", "Delete")}, i -> {
            if (i == 0) def = l;
            else if (languages.size() > 1) {
                languages.remove(l);
                for (Map<String, String> m : keys.values()) m.remove(l);
                if (def.equals(l)) def = languages.get(0);
                if (shown.equals(l)) shown = languages.get(0);
            }
            refresh();
        });
    }

    private void addLanguage() {
        ArrayList<String> names = new ArrayList<>(), codes = new ArrayList<>();
        for (String[] k : KNOWN) if (!languages.contains(k[0])) { names.add(k[1] + " (" + k[0] + ")"); codes.add(k[0]); }
        names.add(Tr.t("strings_other", "Other code…"));
        Dialogs.choose(c, Tr.t("strings_add_language", "Add a language"), names.toArray(new String[0]), i -> {
            if (i < codes.size()) { languages.add(codes.get(i)); shown = codes.get(i); refresh(); return; }
            Dialogs.input(c, Tr.t("strings_add_language", "Add a language"), Tr.t("strings_code_hint", "Language code, e.g. nl"), "", s -> {
                String x = s == null ? "" : s.trim();
                return x.matches("[A-Za-z_\\-]{2,8}") && !languages.contains(x) ? null : Tr.t("strings_key_bad", "Letters, digits and _ only, and not used before");
            }, s -> { languages.add(s.trim()); shown = s.trim(); refresh(); });
        });
    }
}
