package com.star4droid.star2d.ui.blocks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.ColorPickerDialog;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.IconMenu;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block creator: make your own action blocks, C-blocks (with an inside) and value blocks without
 * knowing the placeholder format. The code is written with {fieldName}, {inside} and {else};
 * it is converted to the engine format (%1$s…) and saved in the same user library files the
 * engine already reads (Nodes/nodes.json for blocks, Nodes/blocks.json for value blocks).
 */
public class BlockCreatorActivity extends AppCompatActivity {

    public static void open(Activity a) {
        a.startActivity(new Intent(a, BlockCreatorActivity.class));
    }

    static final int ACTION = 0, CBLOCK = 1, VALUE = 2;
    static final String[] TYPES = {"number", "text", "element", "condition", "image", "sound", "animation", "scene", "any"};

    /** A block being edited. */
    static class Spec {
        int kind = ACTION;
        String label = "", category = "My blocks", code = "";
        boolean hasElse = false;
        int color = 0xFF5D4037;
        /** For which scenes: SceneDims.D2, D3 or BOTH (the default; blocks saved before keep BOTH). */
        int dims = com.star4droid.star2d.blocks.SceneDims.BOTH;
        final ArrayList<String[]> fields = new ArrayList<>(); // {name, type, default}
        /** Description shown by the block's (i) button, one row per language: {language code, text}. */
        final ArrayList<String[]> desc = new ArrayList<>();
        /** The text on the block in other languages: {language code, text} (value blocks keep {field} places). */
        final ArrayList<String[]> labels = new ArrayList<>();
        /** The names of the fields in other languages: {language code, field name, text}. */
        final ArrayList<String[]> fieldTr = new ArrayList<>();
        /** Languages that have a tab in the editor (even while still empty). */
        final ArrayList<String> tabs = new ArrayList<>();
        // where it is stored (for editing): section name + index, or -1
        String section;
        int index = -1;
        int originalKind = ACTION;

        Spec copy() {
            Spec s = new Spec();
            s.kind = kind;
            s.label = label;
            s.category = category;
            s.code = code;
            s.hasElse = hasElse;
            s.color = color;
            s.dims = dims;
            s.originalKind = originalKind;
            for (String[] f : fields) s.fields.add(f.clone());
            for (String[] d : desc) s.desc.add(d.clone());
            for (String[] d : labels) s.labels.add(d.clone());
            for (String[] d : fieldTr) s.fieldTr.add(d.clone());
            s.tabs.addAll(tabs);
            return s;
        }
    }

    private boolean rtl;
    private Spec editing;
    private FrameLayout previewBox;
    private TextView codePreview, errorView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        rtl = Ui.isRtl(this);
        BlockCatalog.load(this);
        showList();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (editing != null) showEditor(editing);
        else showList();
    }

    static String t(String k, String d) { return Tr.t("bc_" + k, d); }

    // ================================================================ storage

    private File nodesFile() { return new File(getExternalFilesDir(null), "Nodes/nodes.json"); }
    private File blocksFile() { return new File(getExternalFilesDir(null), "Nodes/blocks.json"); }

    private static JsonArray readArray(File f) {
        try {
            if (f.exists()) {
                JsonElement e = JsonParser.parseString(BlockModel.read(f));
                if (e.isJsonArray()) return e.getAsJsonArray();
            }
        } catch (Exception ignored) {}
        return new JsonArray();
    }

    private static void writeArray(File f, JsonArray a) throws Exception {
        BlockModel.write(f, new GsonBuilder().setPrettyPrinting().create().toJson(a));
    }

    /** All blocks made in the creator (and other custom blocks, shown read-only when not made here). */
    private ArrayList<Spec> loadAll() {
        ArrayList<Spec> out = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            JsonArray secs = readArray(pass == 0 ? nodesFile() : blocksFile());
            for (JsonElement se : secs) {
                if (!se.isJsonObject()) continue;
                JsonObject s = se.getAsJsonObject();
                String sec = BlockModel.str(s, "name", "Custom");
                String key = pass == 0 ? "nodes" : "blocks";
                if (!s.has(key)) continue;
                JsonArray arr = s.getAsJsonArray(key);
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject n = arr.get(i).getAsJsonObject();
                    Spec sp = fromJson(n, pass == 0);
                    sp.section = sec;
                    sp.category = sec;
                    if (sp.index != -2) sp.index = i;
                    sp.originalKind = sp.kind;
                    out.add(sp);
                }
            }
        }
        return out;
    }

    private static Spec fromJson(JsonObject n, boolean node) {
        Spec s = new Spec();
        if (n.has("s2")) {
            JsonObject m = n.getAsJsonObject("s2");
            s.kind = m.get("kind").getAsInt();
            s.label = BlockModel.str(m, "label", "");
            for (java.util.Map.Entry<String, String> e : BlockCatalog.parseDesc(m.get("labels")).entrySet()) s.labels.add(new String[]{e.getKey(), e.getValue()});
            try {
                if (m.has("fieldLabels"))
                    for (java.util.Map.Entry<String, JsonElement> fe : m.getAsJsonObject("fieldLabels").entrySet())
                        for (java.util.Map.Entry<String, String> e : BlockCatalog.parseDesc(fe.getValue()).entrySet()) s.fieldTr.add(new String[]{e.getKey(), fe.getKey(), e.getValue()});
            } catch (Exception ignored) {}
            s.code = BlockModel.str(m, "code", "");
            s.hasElse = m.has("else") && m.get("else").getAsBoolean();
            if (m.has("fields"))
                for (JsonElement fe : m.getAsJsonArray("fields")) {
                    JsonObject f = fe.getAsJsonObject();
                    s.fields.add(new String[]{BlockModel.str(f, "name", "value"), BlockModel.str(f, "type", "any"), BlockModel.str(f, "value", "")});
                }
        } else {
            // a block made somewhere else: show its raw template
            s.kind = node ? (n.has("isBooleanNode") && n.get("isBooleanNode").getAsBoolean() ? CBLOCK : ACTION) : VALUE;
            s.label = BlockModel.str(n, "title", BlockModel.str(n, "name", "custom"));
            s.code = BlockModel.str(n, "code", "");
            s.index = -2; // not editable here
        }
        try { if (n.has("color")) s.color = n.get("color").getAsInt(); } catch (Exception ignored) {}
        s.dims = com.star4droid.star2d.blocks.SceneDims.of(n, com.star4droid.star2d.blocks.SceneDims.BOTH);
        for (java.util.Map.Entry<String, String> e : BlockCatalog.parseDesc(n.get("desc")).entrySet()) s.desc.add(new String[]{e.getKey(), e.getValue()});
        return s;
    }

    /** Field token as the engine reads it: "speed", "target(Body)", "on(Boolean)", "pic(image)"… */
    static String token(String[] f) {
        switch (f[1]) {
            case "element": return f[0] + "(Body)";
            case "condition": return f[0] + "(Boolean)";
            case "image": return f[0] + "(image)";
            case "sound": return f[0] + "(sound)";
            case "animation": return f[0] + "(animation)";
            case "scene": return f[0] + "(scene)";
            case "text": return f[0] + "(String)";
            default: return f[0];
        }
    }

    /** {name} / {inside} / {else} → %n$s (and % → %%), plus the "next blocks" placeholder. */
    static String convert(Spec s) {
        String c = s.code.replace("%", "%%");
        int n = s.fields.size();
        for (int i = 0; i < n; i++) c = c.replace("{" + s.fields.get(i)[0] + "}", "%" + (i + 1) + "$s");
        if (s.kind == VALUE) return c;
        if (s.kind == CBLOCK) {
            c = c.replace("{inside}", "%" + (n + 1) + "$s");
            c = c.replace("{else}", s.hasElse ? "%" + (n + 2) + "$s" : "");
            return c + "\n%" + (n + 3) + "$s";
        }
        return c + "\n%" + (n + 1) + "$s";
    }

    /** Value block text: "{a} plus {b}" → "___ plus ___" (fields without a place go at the end). */
    static String display(Spec s) {
        String d = s.label;
        for (String[] f : s.fields) {
            if (d.contains("{" + f[0] + "}")) d = d.replace("{" + f[0] + "}", "___");
            else d = d + " ___";
        }
        return d.trim();
    }

    private String validate(Spec s) {
        if (s.label.trim().isEmpty()) return t("err_label", "Write the text of the block.");
        if (s.code.trim().isEmpty()) return t("err_code", "Write the code of the block.");
        java.util.HashSet<String> names = new java.util.HashSet<>();
        for (String[] f : s.fields) {
            if (!f[0].matches("[A-Za-z_][A-Za-z0-9_]*")) return t("err_field", "Field names use letters, numbers and _ (no spaces):") + " " + f[0];
            if (!names.add(f[0])) return t("err_dup", "Two fields have the same name:") + " " + f[0];
            if (f[0].equals("inside") || f[0].equals("else")) return t("err_reserved", "\"inside\" and \"else\" are reserved names.");
        }
        java.util.HashSet<String> langs = new java.util.HashSet<>();
        for (String[] d : s.desc) {
            if (d[1].trim().isEmpty()) continue;
            String lg = d[0].trim().toLowerCase(Locale.ROOT);
            if (lg.isEmpty() || !lg.matches("[a-z]{2,3}([-_][A-Za-z0-9]{2,8})?")) return t("err_desc_lang", "A description has no valid language code (like en, ar, fr):") + " " + d[0];
            if (!langs.add(lg)) return t("err_desc_dup", "Two descriptions use the same language:") + " " + lg;
        }
        java.util.HashSet<String> llangs = new java.util.HashSet<>();
        for (String[] d : s.labels) {
            if (d[1].trim().isEmpty()) continue;
            String lg = d[0].trim().toLowerCase(Locale.ROOT);
            if (lg.isEmpty() || !lg.matches("[a-z]{2,3}([-_][A-Za-z0-9]{2,8})?")) return t("err_desc_lang", "A description has no valid language code (like en, ar, fr):") + " " + d[0];
            if (!llangs.add(lg)) return t("err_label_dup", "Two translations of the text use the same language:") + " " + lg;
        }
        if (s.kind == CBLOCK && !s.code.contains("{inside}")) return t("err_inside", "A C-block needs {inside} in its code: that's where the blocks inside it go.");
        Matcher m = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}").matcher(s.code);
        while (m.find()) {
            String nm = m.group(1);
            if (!names.contains(nm) && !nm.equals("inside") && !nm.equals("else")) return t("err_unknown", "The code uses a field that doesn't exist:") + " {" + nm + "}";
        }
        try {
            BlockCatalog.Def d = toDef(s);
            BlockModel.Node node = d.create();
            String[] args = new String[node.fields.size() + 3];
            for (int i = 0; i < args.length; i++) args[i] = "x";
            String.format(s.kind == VALUE ? convert(s) : d.code, (Object[]) args);
        } catch (Exception e) {
            return t("err_format", "The code can't be used:") + " " + e.getMessage();
        }
        return null;
    }

    private void save(Spec s) {
        String err = validate(s);
        if (err != null) {
            showError(err);
            return;
        }
        try {
            boolean node = s.kind != VALUE;
            File f = node ? nodesFile() : blocksFile();
            JsonArray secs = readArray(f);
            // remove the old version (it may move to another category / file)
            if (s.index >= 0 && s.section != null) removeStored(s);
            secs = readArray(f);
            JsonObject sec = null;
            for (JsonElement se : secs)
                if (se.isJsonObject() && BlockModel.str(se.getAsJsonObject(), "name", "").equals(s.category)) sec = se.getAsJsonObject();
            if (sec == null) {
                sec = new JsonObject();
                sec.addProperty("name", s.category);
                sec.add(node ? "nodes" : "blocks", new JsonArray());
                secs.add(sec);
            }
            String key = node ? "nodes" : "blocks";
            if (!sec.has(key)) sec.add(key, new JsonArray());
            sec.getAsJsonArray(key).add(toJson(s));
            writeArray(f, secs);
            BlockCatalog.reload(this);
            Snack.show(t("saved", "Block saved. Find it in the palette under ★") + " " + s.category);
            editing = null;
            openSection = s.category;
            showList();
        } catch (Exception e) {
            showError(e.toString());
        }
    }

    private void removeStored(Spec s) throws Exception {
        // the stored kind may differ from the edited one: look in both files
        for (int pass = 0; pass < 2; pass++) {
            File f = pass == 0 ? nodesFile() : blocksFile();
            String key = pass == 0 ? "nodes" : "blocks";
            JsonArray secs = readArray(f);
            boolean changed = false;
            for (JsonElement se : secs) {
                if (!se.isJsonObject()) continue;
                JsonObject sec = se.getAsJsonObject();
                if (!BlockModel.str(sec, "name", "").equals(s.section) || !sec.has(key)) continue;
                JsonArray arr = sec.getAsJsonArray(key);
                for (int i = arr.size() - 1; i >= 0; i--) {
                    JsonObject n = arr.get(i).getAsJsonObject();
                    if (i == s.index && wasStoredIn(s, pass)) {
                        arr.remove(i);
                        changed = true;
                    }
                }
            }
            if (changed) writeArray(f, secs);
        }
    }

    /** The original kind decides which file it was in. */
    private boolean wasStoredIn(Spec s, int pass) {
        return (pass == 1) == (s.originalKind == VALUE);
    }

    private JsonObject toJson(Spec s) {
        JsonObject n = new JsonObject();
        String code = convert(s);
        if (s.kind == VALUE) {
            n.addProperty("title", display(s));
            n.addProperty("code", code);
        } else {
            n.addProperty("title", s.label.trim());
            n.addProperty("name", s.label.trim());
            n.addProperty("code", code);
            n.addProperty("isBooleanNode", s.kind == CBLOCK);
            JsonArray fs = new JsonArray();
            for (String[] f : s.fields) {
                JsonObject fo = new JsonObject();
                fo.addProperty("name", token(f));
                fo.addProperty("value", f[2]);
                fs.add(fo);
            }
            n.add("fields", fs);
        }
        n.addProperty("color", s.color);
        n.addProperty("dims", com.star4droid.star2d.blocks.SceneDims.name(s.dims));
        JsonObject m = new JsonObject();
        m.addProperty("kind", s.kind);
        m.addProperty("label", s.label);
        m.addProperty("code", s.code);
        m.addProperty("else", s.hasElse);
        JsonArray mf = new JsonArray();
        for (String[] f : s.fields) {
            JsonObject fo = new JsonObject();
            fo.addProperty("name", f[0]);
            fo.addProperty("type", f[1]);
            fo.addProperty("value", f[2]);
            mf.add(fo);
        }
        m.add("fields", mf);
        JsonObject lj = labelsJson(s, false);
        if (lj.size() > 0) m.add("labels", lj);
        n.add("s2", m);
        JsonObject nl = labelsJson(s, true);
        if (nl.size() > 0) n.add("labels", nl);
        JsonObject fl = fieldLabelsJson(s);
        if (fl.size() > 0) { n.add("fieldLabels", fl); m.add("fieldLabels", fl); }
        JsonObject dj = descJson(s);
        if (dj.size() > 0) n.add("desc", dj);
        return n;
    }

    /** Translations of the text on the block; {@code forEngine}: value blocks with ___ places (as the palette shows them). */
    static JsonObject labelsJson(Spec s, boolean forEngine) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (String[] d : s.labels) {
            String lang = d[0].trim().toLowerCase(Locale.ROOT), text = d[1].trim();
            if (lang.isEmpty() || text.isEmpty()) continue;
            if (forEngine && s.kind == VALUE) {
                Spec tmp = s.copy();
                tmp.label = text;
                text = display(tmp);
            }
            m.put(lang, text);
        }
        return com.star4droid.star2d.blocks.BlockDesc.toJson(m);
    }

    /** {"field": {"ar": "..", "fr": ".."}} : the names of the fields in other languages. */
    static JsonObject fieldLabelsJson(Spec s) {
        JsonObject out = new JsonObject();
        for (String[] f : s.fieldTr) {
            String lang = f[0].trim().toLowerCase(Locale.ROOT), text = f[2].trim();
            if (lang.isEmpty() || text.isEmpty()) continue;
            if (!out.has(f[1])) out.add(f[1], new JsonObject());
            out.getAsJsonObject(f[1]).addProperty(lang, text);
        }
        return out;
    }

    /** {"en":"..","ar":".."}: rows with a language and a text (a language written twice keeps the last). */
    static JsonObject descJson(Spec s) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (String[] d : s.desc) {
            String lang = d[0].trim().toLowerCase(Locale.ROOT), text = d[1].trim();
            if (!lang.isEmpty() && !text.isEmpty()) m.put(lang, text);
        }
        return com.star4droid.star2d.blocks.BlockDesc.toJson(m);
    }

    /** Languages offered for descriptions (code, name); any other code can be typed. */
    static final String[][] LANGS = {{"en", "English"}, {"ar", "العربية"}, {"fr", "Français"}, {"es", "Español"}, {"tr", "Türkçe"},
            {"pt", "Português"}, {"ru", "Русский"}, {"de", "Deutsch"}};

    static String langName(String code) {
        for (String[] l : LANGS) if (l[0].equalsIgnoreCase(code)) return l[1] + " · " + l[0];
        return code.isEmpty() ? "?" : code;
    }

    BlockCatalog.Def toDef(Spec s) {
        BlockCatalog.Def d = new BlockCatalog.Def();
        d.category = "★ " + s.category;
        d.title = s.label.trim().isEmpty() ? t("untitled", "my block") : s.label.trim();
        d.code = convert(s);
        d.isIf = s.kind == CBLOCK;
        d.color = s.color;
        d.dims = s.dims;
        d.fields = new String[s.fields.size()];
        d.defaults = new String[s.fields.size()];
        for (int i = 0; i < s.fields.size(); i++) {
            d.fields[i] = token(s.fields.get(i));
            d.defaults[i] = s.fields.get(i)[2];
        }
        return d;
    }

    // ================================================================ examples

    static ArrayList<Spec> examples() {
        ArrayList<Spec> l = new ArrayList<>();
        String ws = "com.star4droid.template.Items.StageImp.WORLD_SCALE";
        Spec a = new Spec();
        a.kind = ACTION;
        a.label = t("ex_up", "Move up");
        a.fields.add(new String[]{"element", "element", "current"});
        a.fields.add(new String[]{"pixels", "number", "50"});
        a.code = "{element}.getBody().setTransform({element}.getBody().getPosition().x,\n    {element}.getBody().getPosition().y + (float)({pixels}) * " + ws + ",\n    {element}.getBody().getAngle());";
        a.color = 0xFF2E7D32;
        l.add(a);
        Spec b = new Spec();
        b.kind = ACTION;
        b.label = t("ex_say", "Say");
        b.fields.add(new String[]{"message", "text", "\"Hello!\""});
        b.code = "toast(String.valueOf({message}));";
        b.color = 0xFF6D4C41;
        l.add(b);
        Spec c = new Spec();
        c.kind = CBLOCK;
        c.label = t("ex_while", "Repeat while");
        c.fields.add(new String[]{"condition", "condition", ""});
        c.code = "while ({condition}) {\n{inside}\n}";
        c.color = 0xFFE6A100;
        l.add(c);
        Spec d = new Spec();
        d.kind = CBLOCK;
        d.label = t("ex_chance", "With a chance of");
        d.hasElse = true;
        d.fields.add(new String[]{"percent", "number", "50"});
        d.code = "if (random(0, 99) < (int)({percent})) {\n{inside}\n} else {\n{else}\n}";
        d.color = 0xFF8E24AA;
        l.add(d);
        Spec e = new Spec();
        e.kind = VALUE;
        e.label = t("ex_double", "double of {n}");
        e.fields.add(new String[]{"n", "number", ""});
        e.code = "((float)({n}) * 2)";
        e.color = 0xFF2E7D32;
        l.add(e);
        Spec f = new Spec();
        f.kind = VALUE;
        f.label = t("ex_far", "{a} is far from {b}");
        f.fields.add(new String[]{"a", "element", ""});
        f.fields.add(new String[]{"b", "element", ""});
        f.code = "({a}.distTo({b}) / " + ws + " > 300)";
        f.color = 0xFF3F51B5;
        l.add(f);
        return l;
    }

    // ================================================================ list screen

    private LinearLayout topBar(String title, View.OnClickListener back, View... actions) {
        Context c = this;
        LinearLayout top = Ui.row(c);
        top.setBackgroundColor(0xFF181A1E);
        top.setPadding(Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3));
        ImageView b = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("close", "Close"), back);
        b.setScaleX(rtl ? -1 : 1);
        top.addView(b);
        TextView t = Ui.text(c, title, 16, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        t.setMaxLines(1);
        top.addView(t, Ui.weight(1));
        for (View v : actions) top.addView(v);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.fitIconRow(c, top, 90);
        return top;
    }

    /** null = the sections screen; a category name = that section's blocks; EXAMPLES = the example blocks. */
    private String openSection;
    private static final String EXAMPLES = "\u0001examples";

    private void showList() {
        editing = null;
        if (openSection == null) showSections();
        else showSection(openSection);
    }

    /** First screen: one card per section (category) with the number of blocks, plus the examples. */
    private void showSections() {
        Context c = this;
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        ImageView add = Ui.iconButton(c, R.drawable.ic_s2_add, t("new", "New block"), v -> chooseKind());
        add.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        root.addView(topBar(t("title", "Block creator"), v -> finish(), add), Ui.matchWrap());
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 24));

        TextView intro = Ui.text(c, t("intro", "Make your own blocks: an action, a C-block that holds other blocks, or a value that goes inside fields. Your blocks appear in the palette under ★ "), 13, Ui.TEXT_2);
        intro.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), Ui.dp(c, 6));
        col.addView(intro);

        java.util.LinkedHashMap<String, ArrayList<Spec>> groups = new java.util.LinkedHashMap<>();
        for (Spec s : loadAll()) {
            ArrayList<Spec> g = groups.get(s.category);
            if (g == null) groups.put(s.category, g = new ArrayList<>());
            g.add(s);
        }
        col.addView(Ui.sectionLabel(c, t("sections", "Sections")));
        if (groups.isEmpty()) {
            TextView e = Ui.text(c, t("none", "No blocks yet. Tap + or start from an example below."), 13, Ui.TEXT_3);
            e.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, Ui.dp(c, 8));
            col.addView(e);
        }
        for (java.util.Map.Entry<String, ArrayList<Spec>> en : groups.entrySet())
            col.addView(sectionCard(en.getKey(), "★ " + en.getKey(), en.getValue().size(), en.getValue().get(0).color), Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 4));

        col.addView(Ui.sectionLabel(c, t("examples", "Examples (tap to start from one)")));
        ArrayList<Spec> ex = examples();
        col.addView(sectionCard(EXAMPLES, t("examples_title", "Examples"), ex.size(), 0xFF455A64), Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 4));

        ScrollView sv = new ScrollView(c);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        Ui.insets(this, 0xFF181A1E);
    }

    private View sectionCard(String key, String title, int count, int color) {
        Context c = this;
        LinearLayout row = Ui.row(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14));
        row.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(c, 12)));
        row.setForeground(Ui.rounded(0, Ui.dp(c, 12), (color & 0x00FFFFFF) | 0x88000000, Ui.dp(c, 1.5f)));
        ImageView ic = Ui.smallIcon(c, R.drawable.ic_s2_blocks, 0xFFFFFFFF, 18);
        ic.setScaleType(ImageView.ScaleType.CENTER);
        ic.setBackground(Ui.rounded(color, Ui.dp(c, 10)));
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        LinearLayout texts = Ui.column(c);
        texts.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 6), 0);
        TextView n = Ui.text(c, title, 15, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setMaxLines(2);
        texts.addView(n);
        texts.addView(Ui.text(c, count + " " + t("blocks_n", "blocks"), 12, Ui.TEXT_3));
        row.addView(texts, Ui.weight(1));
        ImageView go = Ui.smallIcon(c, R.drawable.ic_s2_chevron_right, Ui.TEXT_3, 18);
        if (rtl) go.setScaleX(-1f);
        row.addView(go, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
        row.setOnClickListener(v -> { openSection = key; showList(); });
        return row;
    }

    /** One section's blocks (or the examples). */
    private void showSection(String key) {
        editing = null;
        Context c = this;
        boolean examples = EXAMPLES.equals(key);
        ArrayList<Spec> items = new ArrayList<>();
        if (examples) items.addAll(examples());
        else for (Spec s : loadAll()) if (s.category.equals(key)) items.add(s);
        if (!examples && items.isEmpty()) { // the last block of the section was deleted
            openSection = null;
            showSections();
            return;
        }
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        ImageView add = Ui.iconButton(c, R.drawable.ic_s2_add, t("new", "New block"), v -> chooseKind());
        add.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        root.addView(topBar(examples ? t("examples_title", "Examples") : "★ " + key, v -> { openSection = null; showList(); }, add), Ui.matchWrap());
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 24));
        if (examples) {
            TextView hint = Ui.text(c, t("examples", "Examples (tap to start from one)"), 13, Ui.TEXT_2);
            hint.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), Ui.dp(c, 6));
            col.addView(hint);
        }
        for (Spec s : items) col.addView(card(s, examples), Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 4));
        ScrollView sv = new ScrollView(c);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        Ui.insets(this, 0xFF181A1E);
    }

    private int kindIcon(int k) {
        return k == CBLOCK ? R.drawable.ic_s2_nodes : (k == VALUE ? R.drawable.ic_s2_code : R.drawable.ic_s2_blocks);
    }

    private String kindName(int k) {
        return k == CBLOCK ? t("k_c", "C-block") : (k == VALUE ? t("k_value", "Value") : t("k_action", "Action"));
    }

    private View card(Spec s, boolean example) {
        Context c = this;
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        row.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(c, 12)));
        row.setForeground(Ui.rounded(0, Ui.dp(c, 12), (s.color & 0x00FFFFFF) | 0x88000000, Ui.dp(c, 1.5f)));
        ImageView ic = Ui.smallIcon(c, kindIcon(s.kind), 0xFFFFFFFF, 18);
        ic.setScaleType(ImageView.ScaleType.CENTER);
        ic.setBackground(Ui.rounded(s.color, Ui.dp(c, 10)));
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        LinearLayout texts = Ui.column(c);
        texts.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 6), 0);
        TextView n = Ui.text(c, s.kind == VALUE ? display(s).replace("___", "◯") : s.label, 14.5f, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setMaxLines(2);
        texts.addView(n);
        StringBuilder sub = new StringBuilder(kindName(s.kind));
        if (s.dims != com.star4droid.star2d.blocks.SceneDims.BOTH) sub.append(" · ").append(com.star4droid.star2d.blocks.SceneDims.name(s.dims));
        if (!example) sub.append(" · ★ ").append(s.category);
        if (!s.fields.isEmpty()) {
            sub.append(" · ");
            for (int i = 0; i < s.fields.size(); i++) {
                if (i > 0) sub.append(", ");
                sub.append(s.fields.get(i)[0]);
            }
        }
        TextView st = Ui.text(c, sub.toString(), 11.5f, Ui.TEXT_3);
        st.setMaxLines(1);
        texts.addView(st);
        row.addView(texts, Ui.weight(1));
        if (example) {
            TextView use = Ui.text(c, t("use", "Use"), 13, Ui.ACCENT);
            use.setTypeface(Typeface.DEFAULT_BOLD);
            use.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            row.addView(use);
            row.setOnClickListener(v -> {
                Spec cp = s.copy();
                cp.originalKind = cp.kind;
                showEditor(cp);
            });
        } else if (s.index == -2) {
            TextView ro = Ui.text(c, t("readonly", "made elsewhere"), 11, Ui.TEXT_3);
            row.addView(ro);
        } else {
            ImageView more = Ui.iconButton(c, R.drawable.ic_s2_more, Tr.t("more", "More"), null);
            more.setOnClickListener(v -> {
                IconMenu m = new IconMenu(c);
                m.add(R.drawable.ic_s2_edit, Ui.ACCENT, Tr.t("edit", "Edit"), () -> edit(s));
                m.add(R.drawable.ic_s2_copy, Tr.t("ui_duplicate", "Duplicate"), () -> {
                    Spec cp = s.copy();
                    cp.label = cp.label + " 2";
                    cp.originalKind = cp.kind;
                    showEditor(cp);
                });
                m.add(R.drawable.ic_s2_delete, 0xFFFF8A80, Tr.t("delete", "Delete"), () ->
                        Dialogs.confirm(c, Tr.t("delete", "Delete"), s.label + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"), () -> {
                            try {
                                s.originalKind = s.kind;
                                removeStored(s);
                                BlockCatalog.reload(this);
                            } catch (Exception e) {
                                Snack.show(e.toString());
                            }
                            showList();
                        }));
                m.show(more);
            });
            row.addView(more);
            row.setOnClickListener(v -> edit(s));
        }
        return row;
    }

    private void edit(Spec s) {
        Spec cp = s.copy();
        cp.section = s.section;
        cp.index = s.index;
        cp.originalKind = s.kind;
        showEditor(cp);
    }

    private void chooseKind() {
        Context c = this;
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 6));
        AlertDialog[] d = new AlertDialog[1];
        int[] kinds = {ACTION, CBLOCK, VALUE};
        String[] desc = {t("k_action_d", "Does something: move, play a sound, show a message…"),
                t("k_c_d", "Holds other blocks inside it: loops, conditions, timers…"),
                t("k_value_d", "Gives a value to put in fields: a number, a text, true / false…")};
        int[] colors = {0xFF1E88E5, 0xFFE6A100, 0xFF2E7D32};
        for (int i = 0; i < 3; i++) {
            final int k = kinds[i];
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            row.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(c, 12)));
            row.setForeground(Ui.rounded(0, Ui.dp(c, 12), (colors[i] & 0x00FFFFFF) | 0x99000000, Ui.dp(c, 1.5f)));
            ImageView ic = Ui.smallIcon(c, kindIcon(k), 0xFFFFFFFF, 20);
            ic.setScaleType(ImageView.ScaleType.CENTER);
            ic.setBackground(Ui.rounded(colors[i], Ui.dp(c, 12)));
            row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42)));
            LinearLayout tx = Ui.column(c);
            tx.setPadding(Ui.dp(c, 12), 0, 0, 0);
            TextView n = Ui.text(c, kindName(k), 15, Ui.TEXT);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            tx.addView(n);
            tx.addView(Ui.text(c, desc[i], 12, Ui.TEXT_3));
            row.addView(tx, Ui.weight(1));
            row.setOnClickListener(v -> {
                d[0].dismiss();
                Spec s = new Spec();
                s.kind = k;
                s.originalKind = k;
                s.color = colors[k == ACTION ? 0 : (k == CBLOCK ? 1 : 2)];
                if (k == CBLOCK) s.code = "if (true) {\n{inside}\n}";
                showEditor(s);
            });
            col.addView(row, Ui.margins(Ui.matchWrap(), c, 0, 5, 0, 5));
        }
        d[0] = new AlertDialog.Builder(c).setTitle(t("choose_kind", "What kind of block?")).setView(col)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
    }

    // ================================================================ editor screen

    /** Three chips 2D / 3D / Both (Both = default). */
    private View dimsRow(Spec s) {
        Context c = this;
        LinearLayout row = Ui.row(c);
        int[] values = {com.star4droid.star2d.blocks.SceneDims.D2, com.star4droid.star2d.blocks.SceneDims.D3, com.star4droid.star2d.blocks.SceneDims.BOTH};
        String[] names = {t("dims_2d", "2D scenes"), t("dims_3d", "3D scenes"), t("dims_both", "Both")};
        ArrayList<TextView> chips = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            final int v = values[i];
            TextView ch = Ui.text(c, names[i], 13, Ui.TEXT);
            ch.setGravity(Gravity.CENTER);
            ch.setPadding(Ui.dp(c, 8), Ui.dp(c, 9), Ui.dp(c, 8), Ui.dp(c, 9));
            ch.setBackground(Ui.toggleBg(c));
            ch.setSelected(s.dims == v);
            ch.setTypeface(s.dims == v ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            ch.setTag(v);
            chips.add(ch);
            ch.setOnClickListener(x -> {
                s.dims = v;
                for (TextView o : chips) {
                    boolean sel = (Integer) o.getTag() == v;
                    o.setSelected(sel);
                    o.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                }
            });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(Ui.dp(c, 3), 0, Ui.dp(c, 3), 0);
            row.addView(ch, lp);
        }
        return row;
    }

    private void showEditor(Spec s) {
        editing = s;
        Context c = this;
        boolean land = Ui.isLandscape(c);
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        ImageView save = Ui.iconButton(c, R.drawable.ic_s2_save, Tr.t("save", "Save"), v -> save(s));
        save.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        ImageView help = Ui.iconButton(c, R.drawable.ic_s2_help, Tr.t("ui_help", "Help"), v -> Dialogs.longText(c, t("title", "Block creator"), t("help",
                "1. Choose the kind: an action, a C-block (holds blocks inside) or a value.\n"
                        + "2. Write the text people see on the block.\n"
                        + "3. Add fields: each one becomes a white slot on the block (a number, a text, an element, a condition, an image…).\n"
                        + "4. Write the Java code. Use {fieldName} where a field's value goes. A C-block puts its inner blocks at {inside} (and {else}).\n"
                        + "Tip: \"current\" is the element that owns the event; toast(\"…\") shows a message.")));
        root.addView(topBar(s.index >= 0 ? Tr.t("edit", "Edit") : t("new", "New block"), v -> confirmLeave(), help, save), Ui.matchWrap());

        LinearLayout form = Ui.column(c);
        form.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 24));

        // kind chips
        LinearLayout kinds = Ui.row(c);
        for (int k : new int[]{ACTION, CBLOCK, VALUE}) {
            TextView ch = Ui.text(c, kindName(k), 13, Ui.TEXT);
            ch.setGravity(Gravity.CENTER);
            ch.setPadding(Ui.dp(c, 8), Ui.dp(c, 9), Ui.dp(c, 8), Ui.dp(c, 9));
            ch.setBackground(Ui.toggleBg(c));
            ch.setSelected(s.kind == k);
            ch.setTypeface(s.kind == k ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            ch.setCompoundDrawablesRelativeWithIntrinsicBounds(kindIcon(k), 0, 0, 0);
            ch.setCompoundDrawablePadding(Ui.dp(c, 6));
            ch.setCompoundDrawableTintList(ColorStateList.valueOf(Ui.TEXT_2));
            ch.setOnClickListener(v -> {
                if (s.kind == k) return;
                s.kind = k;
                if (k == CBLOCK && !s.code.contains("{inside}")) s.code = s.code.isEmpty() ? "if (true) {\n{inside}\n}" : s.code + "\n{inside}";
                showEditor(s);
            });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(Ui.dp(c, 3), 0, Ui.dp(c, 3), 0);
            kinds.addView(ch, lp);
        }
        form.addView(kinds, Ui.matchWrap());

        // label
        form.addView(Ui.sectionLabel(c, s.kind == VALUE ? t("label_value", "Text (use {field} to place a field, e.g. \"double of {n}\")") : t("label", "Text on the block")));
        EditText label = Ui.input(c, s.label, s.kind == VALUE ? "double of {n}" : t("label_hint", "e.g. Move up"));
        label.addTextChangedListener(watch(() -> { s.label = label.getText().toString(); refreshPreview(s); }));
        form.addView(label, Ui.matchWrap());

        // translations: one tab per language (name, description and field names); the code is the same for all
        form.addView(Ui.sectionLabel(c, t("label_tr", "Translations (shown when the app is in that language)")), Ui.matchWrap());
        form.addView(translationsView(s), Ui.matchWrap());

        // category + color
        LinearLayout cat = Ui.row(c);
        EditText category = Ui.input(c, s.category, t("category", "Category"));
        category.addTextChangedListener(watch(() -> { s.category = category.getText().toString().trim().isEmpty() ? "My blocks" : category.getText().toString().trim(); }));
        cat.addView(category, Ui.weight(1));
        View sw = new View(c);
        sw.setBackground(Ui.rounded(s.color, Ui.dp(c, 8), Ui.BORDER, Ui.dp(c, 1)));
        sw.setOnClickListener(v -> ColorPickerDialog.show(c, t("color", "Block color"), String.format("%08X", (s.color << 8) | 0xFF), null, hex -> {
            if (hex == null) return;
            int col = ColorPickerDialog.parse(hex) | 0xFF000000;
            s.color = col;
            sw.setBackground(Ui.rounded(col, Ui.dp(c, 8), Ui.BORDER, Ui.dp(c, 1)));
            refreshPreview(s);
        }));
        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 40));
        swl.setMarginStart(Ui.dp(c, 8));
        cat.addView(sw, swl);
        form.addView(Ui.sectionLabel(c, t("category_color", "Category and color")));
        form.addView(cat, Ui.matchWrap());

        // 2D / 3D / both: in which scenes the palette shows the block
        form.addView(Ui.sectionLabel(c, t("dims", "Show in scenes")));
        form.addView(dimsRow(s), Ui.matchWrap());

        // fields
        form.addView(Ui.sectionLabel(c, t("fields", "Fields (white slots on the block)")));
        LinearLayout fieldsBox = Ui.column(c);
        form.addView(fieldsBox, Ui.matchWrap());
        Runnable[] rebuildFields = new Runnable[1];
        rebuildFields[0] = () -> {
            fieldsBox.removeAllViews();
            for (int i = 0; i < s.fields.size(); i++) fieldsBox.addView(fieldRow(s, i, rebuildFields[0]), Ui.margins(Ui.matchWrap(), c, 0, 3, 0, 3));
            refreshPreview(s);
        };
        rebuildFields[0].run();
        LinearLayout addField = Ui.button(c, R.drawable.ic_s2_add, t("add_field", "Add field"), false, v -> {
            String base = "value";
            int n = s.fields.size() + 1;
            String nm = n == 1 ? base : base + n;
            s.fields.add(new String[]{nm, "number", ""});
            rebuildFields[0].run();
        });
        form.addView(addField, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 0, 4, 0, 0));

        // C-block else
        if (s.kind == CBLOCK) {
            SwitchCompat el = new SwitchCompat(c);
            el.setText(t("has_else", "Second opening (else), use {else} in the code"));
            el.setTextColor(Ui.TEXT);
            el.setMinHeight(Ui.dp(c, 44));
            el.setChecked(s.hasElse);
            el.setOnCheckedChangeListener((b, on) -> {
                s.hasElse = on;
                if (on && !s.code.contains("{else}")) {
                    s.code = s.code.replace("{inside}\n}", "{inside}\n} else {\n{else}\n}");
                    showEditor(s);
                    return;
                }
                refreshPreview(s);
            });
            form.addView(el, Ui.matchWrap());
        }

        // code
        form.addView(Ui.sectionLabel(c, t("code", "Java code")));
        EditText code = Ui.input(c, s.code, s.kind == VALUE ? "((float)({n}) * 2)" : "toast(\"Hi\");");
        code.setSingleLine(false);
        code.setMinLines(4);
        code.setTypeface(Typeface.MONOSPACE);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setTextDirection(View.TEXT_DIRECTION_LTR);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.addTextChangedListener(watch(() -> { s.code = code.getText().toString(); refreshPreview(s); }));
        // insert chips
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(c);
        ArrayList<String> ins = new ArrayList<>();
        for (String[] f : s.fields) ins.add("{" + f[0] + "}");
        if (s.kind == CBLOCK) {
            ins.add("{inside}");
            if (s.hasElse) ins.add("{else}");
        }
        ins.add("current");
        ins.add("toast(\"\");");
        for (String v : ins) {
            TextView ch = Ui.text(c, v, 12.5f, v.startsWith("{") ? 0xFF80DEEA : Ui.TEXT);
            ch.setTypeface(Typeface.MONOSPACE);
            ch.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            ch.setBackground(Ui.ripple(Ui.SURFACE_3, Ui.dp(c, 14)));
            ch.setOnClickListener(x -> {
                int st = Math.max(0, code.getSelectionStart()), en = Math.max(0, code.getSelectionEnd());
                code.getText().replace(Math.min(st, en), Math.max(st, en), v);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(c, 6));
            chips.addView(ch, lp);
        }
        hs.addView(chips);
        form.addView(hs, Ui.margins(Ui.matchWrap(), c, 0, 2, 0, 6));
        form.addView(code, Ui.matchWrap());
        errorView = Ui.text(c, "", 12.5f, 0xFFFF8A80);
        errorView.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), 0);
        form.addView(errorView);

        // preview
        LinearLayout prev = Ui.column(c);
        prev.addView(Ui.sectionLabel(c, t("preview", "Preview")));
        previewBox = new FrameLayout(c);
        previewBox.setBackground(Ui.rounded(0xFF15181C, Ui.dp(c, 12), Ui.BORDER, Ui.dp(c, 1)));
        prev.addView(previewBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, land ? 200 : 170)));
        prev.addView(Ui.sectionLabel(c, t("generated", "Generated code (with example values)")));
        codePreview = Ui.text(c, "", 12, Ui.TEXT_2);
        codePreview.setTypeface(Typeface.MONOSPACE);
        codePreview.setTextDirection(View.TEXT_DIRECTION_LTR);
        codePreview.setTextIsSelectable(true);
        codePreview.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        codePreview.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 10), Ui.BORDER, Ui.dp(c, 1)));
        prev.addView(codePreview, Ui.matchWrap());

        if (land) {
            LinearLayout body = Ui.row(c);
            ScrollView left = new ScrollView(c);
            left.addView(form);
            body.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            ScrollView right = new ScrollView(c);
            prev.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 12));
            right.addView(prev);
            body.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        } else {
            form.addView(prev, Ui.matchWrap());
            ScrollView sv = new ScrollView(c);
            sv.addView(form);
            root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        setContentView(root);
        Ui.insets(this, 0xFF181A1E);
        refreshPreview(s);
    }

    private View fieldRow(Spec s, int i, Runnable rebuild) {
        Context c = this;
        String[] f = s.fields.get(i);
        LinearLayout box = Ui.column(c);
        box.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, Ui.dp(c, 1)));
        box.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
        LinearLayout r1 = Ui.row(c);
        EditText name = Ui.input(c, f[0], t("field_name", "name"));
        name.setTextDirection(View.TEXT_DIRECTION_LTR);
        name.addTextChangedListener(watch(() -> {
            String old = f[0], nw = name.getText().toString().trim();
            if (nw.equals(old) || nw.isEmpty()) return;
            // keep the code in sync with the new name
            s.code = s.code.replace("{" + old + "}", "{" + nw + "}");
            s.label = s.label.replace("{" + old + "}", "{" + nw + "}");
            f[0] = nw;
            refreshPreview(s);
        }));
        r1.addView(name, Ui.weight(1));
        TextView type = Ui.text(c, typeName(f[1]), 13, Ui.TEXT);
        type.setPadding(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 10), Ui.dp(c, 9));
        type.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 8), Ui.BORDER, Ui.dp(c, 1)));
        type.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_expand_more, 0);
        type.setCompoundDrawableTintList(ColorStateList.valueOf(Ui.TEXT_2));
        type.setOnClickListener(v -> {
            IconMenu m = new IconMenu(c);
            for (String tp : TYPES) m.add(tp.equals(f[1]) ? R.drawable.ic_s2_check : 0, Ui.ACCENT, typeName(tp), () -> {
                f[1] = tp;
                type.setText(typeName(tp));
                refreshPreview(s);
            });
            m.show(type);
        });
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(Ui.dp(c, 6));
        r1.addView(type, tl);
        ImageView del = Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> {
            s.fields.remove(i);
            rebuild.run();
        });
        del.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
        r1.addView(del);
        box.addView(r1, Ui.matchWrap());
        if (s.kind != VALUE) {
            EditText dv = Ui.input(c, f[2], t("field_default", "Default value (optional)"));
            dv.setTextDirection(View.TEXT_DIRECTION_LTR);
            dv.addTextChangedListener(watch(() -> { f[2] = dv.getText().toString(); refreshPreview(s); }));
            box.addView(dv, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        }
        return box;
    }

    // ---------------------------------------------------------------- translation tabs

    private static String rowGet(ArrayList<String[]> rows, String lang) {
        for (String[] r : rows) if (r[0].equalsIgnoreCase(lang)) return r[r.length - 1];
        return "";
    }

    private static String rowGet(ArrayList<String[]> rows, String lang, String field) {
        for (String[] r : rows) if (r[0].equalsIgnoreCase(lang) && r[1].equals(field)) return r[2];
        return "";
    }

    private static void rowSet(ArrayList<String[]> rows, String lang, String text) {
        for (String[] r : rows) if (r[0].equalsIgnoreCase(lang)) { r[1] = text; return; }
        rows.add(new String[]{lang, text});
    }

    private static void rowSet(ArrayList<String[]> rows, String lang, String field, String text) {
        for (String[] r : rows) if (r[0].equalsIgnoreCase(lang) && r[1].equals(field)) { r[2] = text; return; }
        rows.add(new String[]{lang, field, text});
    }

    private static void syncTabs(Spec s) {
        for (ArrayList<String[]> rows : java.util.Arrays.asList(s.labels, s.desc, s.fieldTr))
            for (String[] r : rows) {
                String lg = r[0].trim().toLowerCase(Locale.ROOT);
                if (!lg.isEmpty() && !s.tabs.contains(lg)) s.tabs.add(lg);
            }
    }

    /** Scrollable language tabs with a "+" button; each tab edits the block name, description and field names in that language. */
    private View translationsView(Spec s) {
        Context c = this;
        syncTabs(s);
        LinearLayout box = Ui.column(c);
        final String[] cur = {s.tabs.isEmpty() ? "" : s.tabs.get(0)};
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            box.removeAllViews();
            LinearLayout bar = Ui.row(c);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            for (String lg : s.tabs) {
                LinearLayout tab = Ui.row(c);
                tab.setGravity(Gravity.CENTER_VERTICAL);
                tab.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
                tab.setBackground(Ui.toggleBg(c));
                tab.setSelected(lg.equals(cur[0]));
                TextView name = Ui.text(c, langName(lg), 13, Ui.TEXT);
                name.setTypeface(lg.equals(cur[0]) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                tab.addView(name);
                ImageView x = Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> {
                    for (ArrayList<String[]> rows : java.util.Arrays.asList(s.labels, s.desc, s.fieldTr))
                        for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i)[0].equalsIgnoreCase(lg)) rows.remove(i);
                    s.tabs.remove(lg);
                    if (lg.equals(cur[0])) cur[0] = s.tabs.isEmpty() ? "" : s.tabs.get(0);
                    refreshPreview(s);
                    render[0].run();
                });
                x.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
                tab.addView(x, new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
                name.setOnClickListener(v -> { cur[0] = lg; render[0].run(); });
                tab.setOnClickListener(v -> { cur[0] = lg; render[0].run(); });
                bar.addView(tab, Ui.margins(new LinearLayout.LayoutParams(-2, -2), c, 0, 0, 6, 0));
            }
            TextView plus = Ui.text(c, "+", 18, Ui.TEXT);
            plus.setGravity(Gravity.CENTER);
            plus.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 4));
            plus.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, 8)));
            plus.setOnClickListener(v -> {
                IconMenu m = new IconMenu(c);
                for (String[] l : LANGS) if (!s.tabs.contains(l[0]))
                    m.add(0, Ui.ACCENT, l[1] + " · " + l[0], () -> { s.tabs.add(l[0]); cur[0] = l[0]; render[0].run(); });
                m.add(0, Ui.ACCENT, t("desc_other", "Other language code…"), () -> Dialogs.input(c, t("desc_other", "Other language code…"), t("desc_other_msg", "Two or three letters, like de or id."), "", false,
                        val -> val.trim().toLowerCase(Locale.ROOT).matches("[a-z]{2,3}([-_][A-Za-z0-9]{2,8})?") ? (s.tabs.contains(val.trim().toLowerCase(Locale.ROOT)) ? t("err_label_dup", "Two translations of the text use the same language:") : null) : t("err_desc_lang", "A description has no valid language code (like en, ar, fr):"),
                        val -> { String lg = val.trim().toLowerCase(Locale.ROOT); s.tabs.add(lg); cur[0] = lg; render[0].run(); }));
                m.show(plus);
            });
            bar.addView(plus, new LinearLayout.LayoutParams(-2, -2));
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(bar);
            box.addView(hs, Ui.matchWrap());
            if (cur[0].isEmpty()) return;
            final String lg = cur[0];
            LinearLayout page = Ui.column(c);
            page.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, Ui.dp(c, 1)));
            page.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 10));
            LinearLayout head = Ui.row(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(Ui.sectionLabel(c, t("tr_name", "Block name")), Ui.weight(1));
            ImageView del = Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> {
                for (ArrayList<String[]> rows : java.util.Arrays.asList(s.labels, s.desc, s.fieldTr))
                    for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i)[0].equalsIgnoreCase(lg)) rows.remove(i);
                s.tabs.remove(lg);
                cur[0] = s.tabs.isEmpty() ? "" : s.tabs.get(0);
                refreshPreview(s);
                render[0].run();
            });
            del.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
            head.addView(del);
            page.addView(head, Ui.matchWrap());
            EditText name = Ui.input(c, rowGet(s.labels, lg), s.kind == VALUE ? "double of {n}" : t("label_hint", "e.g. Move up"));
            name.addTextChangedListener(watch(() -> { rowSet(s.labels, lg, name.getText().toString()); refreshPreview(s); }));
            page.addView(name, Ui.matchWrap());
            page.addView(Ui.sectionLabel(c, t("tr_desc", "Description")), Ui.matchWrap());
            EditText desc = Ui.input(c, rowGet(s.desc, lg), t("desc_hint", "What does this block do?"));
            desc.setSingleLine(false);
            desc.setMinLines(2);
            desc.setGravity(Gravity.TOP | Gravity.START);
            desc.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            desc.addTextChangedListener(watch(() -> rowSet(s.desc, lg, desc.getText().toString())));
            page.addView(desc, Ui.matchWrap());
            if (!s.fields.isEmpty() && s.kind != VALUE) {
                page.addView(Ui.sectionLabel(c, t("tr_fields", "Field names")), Ui.matchWrap());
                for (String[] f : s.fields) {
                    final String fname = f[0];
                    EditText fe = Ui.input(c, rowGet(s.fieldTr, lg, fname), fname);
                    fe.addTextChangedListener(watch(() -> rowSet(s.fieldTr, lg, fname, fe.getText().toString())));
                    page.addView(fe, Ui.margins(Ui.matchWrap(), c, 0, 3, 0, 3));
                }
            }
            TextView note = Ui.text(c, t("tr_code_same", "The code is the same for every language."), 12, Ui.TEXT_2);
            page.addView(note, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
            box.addView(page, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        };
        render[0].run();
        return box;
    }

    /** A language that the list does not use yet: the app language first, then the known ones. */
    private static String freeLang(ArrayList<String[]> rows) {
        String pick = null;
        java.util.HashSet<String> used = new java.util.HashSet<>();
        for (String[] d : rows) used.add(d[0].trim().toLowerCase(Locale.ROOT));
        String appLang = Tr.lang();
        if (!used.contains(appLang)) pick = appLang;
        for (int k = 0; pick == null && k < LANGS.length; k++) if (!used.contains(LANGS[k][0])) pick = LANGS[k][0];
        return pick == null ? "en" : pick;
    }

    /** One row {language, text} of a list of translations (description or text of the block). */
    private View langRow(Spec s, ArrayList<String[]> rows, int i, Runnable rebuild, String hint, boolean multiline) {
        Context c = this;
        String[] d = rows.get(i);
        LinearLayout box = Ui.column(c);
        box.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, Ui.dp(c, 1)));
        box.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
        LinearLayout r1 = Ui.row(c);
        TextView lang = Ui.text(c, langName(d[0]), 13, Ui.TEXT);
        lang.setPadding(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 10), Ui.dp(c, 9));
        lang.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 8), Ui.BORDER, Ui.dp(c, 1)));
        lang.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_expand_more, 0);
        lang.setCompoundDrawableTintList(ColorStateList.valueOf(Ui.TEXT_2));
        lang.setOnClickListener(v -> {
            IconMenu m = new IconMenu(c);
            for (String[] l : LANGS) m.add(l[0].equalsIgnoreCase(d[0]) ? R.drawable.ic_s2_check : 0, Ui.ACCENT, l[1] + " · " + l[0], () -> {
                d[0] = l[0];
                lang.setText(langName(d[0]));
            });
            m.add(0, Ui.ACCENT, t("desc_other", "Other language code…"), () -> Dialogs.input(c, t("desc_other", "Other language code…"), t("desc_other_msg", "Two or three letters, like de or id."), d[0], false,
                    val -> val.trim().toLowerCase(Locale.ROOT).matches("[a-z]{2,3}([-_][A-Za-z0-9]{2,8})?") ? null : t("err_desc_lang", "A description has no valid language code (like en, ar, fr):"),
                    val -> {
                        d[0] = val.trim().toLowerCase(Locale.ROOT);
                        lang.setText(langName(d[0]));
                    }));
            m.show(lang);
        });
        r1.addView(lang, Ui.weight(1));
        ImageView del = Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> {
            rows.remove(i);
            rebuild.run();
        });
        del.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
        r1.addView(del);
        box.addView(r1, Ui.matchWrap());
        EditText text = Ui.input(c, d[1], hint);
        if (multiline) {
            text.setSingleLine(false);
            text.setMinLines(2);
            text.setGravity(Gravity.TOP | Gravity.START);
            text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        text.addTextChangedListener(watch(() -> { d[1] = text.getText().toString(); if (!multiline) refreshPreview(s); }));
        box.addView(text, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        return box;
    }

    private String typeName(String tp) {
        switch (tp) {
            case "number": return t("ty_number", "Number");
            case "text": return t("ty_text", "Text");
            case "element": return t("ty_element", "Element");
            case "condition": return t("ty_condition", "Condition");
            case "image": return t("ty_image", "Image");
            case "sound": return t("ty_sound", "Sound");
            case "animation": return t("ty_animation", "Animation");
            case "scene": return t("ty_scene", "Scene");
            default: return t("ty_any", "Anything");
        }
    }

    private static TextWatcher watch(Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) { r.run(); }
        };
    }

    private void showError(String e) {
        if (errorView != null) errorView.setText(e == null ? "" : "⚠ " + e);
        if (e != null) Snack.show(e);
    }

    /** Sample value for a field, used in the code preview. */
    private static String sample(String[] f) {
        if (!f[2].isEmpty()) return f[2];
        switch (f[1]) {
            case "element": return "current";
            case "condition": return "true";
            case "text": return "\"text\"";
            case "image": return "/player.png";
            case "sound": return "/jump.mp3";
            case "animation": return "walk";
            case "scene": return "scene1";
            default: return "10";
        }
    }

    private void refreshPreview(Spec s) {
        if (previewBox == null || codePreview == null) return;
        // code with example values
        String err = s.label.trim().isEmpty() && s.code.trim().isEmpty() ? null : validate(s);
        errorView.setText(err == null ? "" : "⚠ " + err);
        try {
            BlockModel m = new BlockModel();
            BlockModel.Node root = BlockModel.newRoot();
            root.x = 16;
            root.y = 16;
            m.heads.add(root);
            if (s.kind == VALUE) {
                BlockCatalog.Def toast = BlockCatalog.find("toastValue");
                BlockModel.Node n = toast != null ? toast.create() : null;
                if (n != null && !n.fields.isEmpty()) {
                    BlockModel.Param p = new BlockModel.Param(convert(s), display(s));
                    for (int i = 0; i < p.inner.size() && i < s.fields.size(); i++) p.inner.get(i).value = sample(s.fields.get(i));
                    n.fields.get(0).block = p;
                    root.next = n;
                }
            } else {
                BlockModel.Node n = toDef(s).create();
                for (int i = 0; i < n.fields.size(); i++) if (n.fields.get(i).value.isEmpty()) n.fields.get(i).value = sample(s.fields.get(i));
                if (s.kind == CBLOCK) {
                    BlockCatalog.Def inner = BlockCatalog.find("toastValue");
                    if (inner != null) {
                        n.yes = inner.create();
                        n.yes.fields.get(0).value = "\"inside\"";
                        if (s.hasElse) {
                            n.no = inner.create();
                            n.no.fields.get(0).value = "\"else\"";
                        }
                    }
                }
                root.next = n;
            }
            codePreview.setText(err == null ? m.exportCode().trim() : "—");
            BlockCanvas cv = new BlockCanvas(this, new NoListener(), rtl);
            cv.setModel(m, "▶ " + t("preview_event", "When the event runs"));
            previewBox.removeAllViews();
            previewBox.addView(cv, Ui.match());
            cv.post(cv::home);
        } catch (Exception e) {
            codePreview.setText("—");
        }
    }

    private void confirmLeave() {
        new AlertDialog.Builder(this)
                .setTitle(t("leave", "Leave without saving?"))
                .setPositiveButton(t("leave_ok", "Leave"), (d, w) -> showList())
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (editing != null) confirmLeave();
        else if (openSection != null) { openSection = null; showList(); }
        else super.onBackPressed();
    }

    /** The preview canvas only shows the block. */
    static class NoListener implements BlockCanvas.Listener {
        @Override public void onFieldTap(BlockModel.Node node, BlockModel.Field field) {}
        @Override public void onInfo(BlockModel.Node node) {}
        @Override public void onSelect(BlockModel.Node node) {}
        @Override public void onLongPress(BlockModel.Node node, float rawX, float rawY) {}
        @Override public void beforeChange() {}
        @Override public void afterChange() {}
        @Override public void onDropHint() {}
        @Override public void onRootParam(String name) {}
    }

    static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
