package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.MainActivity;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** Editor settings (same preference keys as the old settings dialog) + icon size. */
public class SettingsPanel implements PanelHost.Panel {
    private final EditorShell shell;

    private static final String[] LANG_NAMES = {"English", "العربية", "Français", "Português", "Русский", "Español"};
    private static final String[] LANG_CODES = {"en", "ar", "fr", "br", "ru", "es"};

    public SettingsPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("settings", "Settings"); }
    @Override public int icon() { return R.drawable.ic_s2_settings; }

    @Override
    public View view(Context c) {
        SharedPreferences p = Ui.prefs(c);
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 20));

        // ---- appearance
        col.addView(Ui.sectionLabel(c, Tr.t("ui_appearance", "Appearance")));
        col.addView(Ui.text(c, Tr.t("ui_icon_size", "Icon size"), 13, Ui.TEXT_2));
        LinearLayout sizes = Ui.row(c);
        String[][] opts = {{"small", Tr.t("ui_small", "Small")}, {"medium", Tr.t("ui_medium", "Medium")}, {"large", Tr.t("ui_large", "Large")}};
        String cur = Ui.iconSize(c);
        for (String[] o : opts) {
            TextView t = Ui.text(c, o[1], 14, Ui.TEXT);
            t.setGravity(android.view.Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
            t.setBackground(Ui.toggleBg(c));
            t.setSelected(o[0].equals(cur));
            t.setTypeface(o[0].equals(cur) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setOnClickListener(v -> {
                p.edit().putString(Ui.PREF_ICON_SIZE, o[0]).apply();
                shell.build();
                shell.panels.show(this); // rebuild this view with the new selection
            });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(Ui.dp(c, 3), Ui.dp(c, 6), Ui.dp(c, 3), Ui.dp(c, 6));
            sizes.addView(t, lp);
        }
        col.addView(sizes, Ui.matchWrap());

        col.addView(Ui.text(c, Tr.t("language", "Language"), 13, Ui.TEXT_2));
        String lang = p.getString("lang", "en");
        int li = 0;
        for (int i = 0; i < LANG_CODES.length; i++) if (LANG_CODES[i].equals(lang)) li = i;
        TextView langView = chooser(c, LANG_NAMES[li]);
        langView.setOnClickListener(v -> Dialogs.choose(c, Tr.t("language", "Language"), LANG_NAMES, i -> {
            if (LANG_CODES[i].equals(p.getString("lang", "en"))) return;
            p.edit().putString("lang", LANG_CODES[i]).putBoolean("langSelected", true).commit();
            Dialogs.confirm(c, Tr.t("ui_restart", "Restart required"), Tr.t("langNextTime", "The language will change after restarting."),
                    Tr.t("ui_restart_now", "Restart now"), () -> {
                        shell.save();
                        shell.postDelayed(() -> {
                            Intent in = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            c.startActivity(in);
                            shell.activity().finishAffinity();
                            Runtime.getRuntime().exit(0);
                        }, 400);
                    });
        }));
        col.addView(langView, Ui.matchWrap());

        // ---- editor
        col.addView(Ui.sectionLabel(c, Tr.t("ui_editor", "Editor")));
        col.addView(sw(c, p, "AutoSave", Tr.t("autoSave", "Automatic scene saving"), v -> Bridge.gl(() -> Bridge.editor().enableAutoSave(v))));
        col.addView(sw(c, p, "SaveUndoRedo", Tr.t("saveUndoRedo", "Persist undo/redo history"), null));
        col.addView(sw(c, p, "Auto Completion", Tr.t("codeCompletion", "Script code completion"), null));

        col.addView(Ui.text(c, Tr.t("ui_compiler", "Java compiler"), 13, Ui.TEXT_2));
        String[] compilers = {Tr.t("compiler_ecj", "ECJ (recommended, Java 8+)"), Tr.t("compiler_javac", "javac (old, Java 7)")};
        TextView comp = chooser(c, "javac".equals(p.getString("compiler", "ecj")) ? compilers[1] : compilers[0]);
        comp.setOnClickListener(v -> Dialogs.choose(c, Tr.t("ui_compiler", "Java compiler"), compilers, i -> {
            p.edit().putString("compiler", i == 1 ? "javac" : "ecj").apply();
            comp.setText(compilers[i]);
        }));
        col.addView(comp, Ui.matchWrap());

        col.addView(Ui.text(c, Tr.t("dexer", "Dex builder"), 13, Ui.TEXT_2));
        String[] dexers = {Tr.t("dexer_d8", "D8 (recommended)"), Tr.t("dexer_dx", "dx (old, Java 7 code only)")};
        TextView dex = chooser(c, "dx".equals(p.getString("dexer", "d8")) ? dexers[1] : dexers[0]);
        dex.setOnClickListener(v -> Dialogs.choose(c, Tr.t("dexer", "Dex builder"), dexers, i -> {
            p.edit().putString("dexer", i == 1 ? "dx" : "d8").apply();
            dex.setText(dexers[i]);
        }));
        col.addView(dex, Ui.matchWrap());

        // ---- scene (logic size)
        col.addView(Ui.sectionLabel(c, Tr.t("ui_scene", "Scene")));
        TextView hint = Ui.text(c, Tr.t("ui_logic_hint", "Game screen size in world units (the red frame on the canvas)."), 12, Ui.TEXT_3);
        col.addView(hint);
        LinearLayout wh = Ui.row(c);
        EditText w = Ui.input(c, "", "W");
        EditText h = Ui.input(c, "", "H");
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        w.setInputType(num);
        h.setInputType(num);
        wh.addView(w, Ui.weight(1));
        LinearLayout.LayoutParams hl = Ui.weight(1);
        hl.setMarginStart(Ui.dp(c, 8));
        wh.addView(h, hl);
        LinearLayout apply = Ui.button(c, R.drawable.ic_s2_check, Tr.t("ui_apply", "Apply"), true, v -> {
            try {
                float a = Float.parseFloat(w.getText().toString()), b = Float.parseFloat(h.getText().toString());
                // stored convention: the first number is the LONG side (a smaller one would reverse the frame and the game)
                final float fw = Math.max(a, b), fh = Math.min(a, b);
                w.setText(String.valueOf(fw));
                h.setText(String.valueOf(fh));
                Bridge.gl(() -> {
                    Bridge.editor().setLogicalWH(fw, fh);
                    Bridge.editor().saveConfig();
                });
                Ui.toast(c, Tr.t("saved", "Saved"));
            } catch (Exception e) {
                Ui.toast(c, Tr.t("ui_number", "Enter a number"));
            }
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, Ui.dp(c, 38));
        al.setMarginStart(Ui.dp(c, 8));
        wh.addView(apply, al);
        col.addView(wh, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        // when the red frame looks reversed (wide in a portrait game or the other way round): exchange the two numbers
        LinearLayout swapBtn = Ui.button(c, R.drawable.ic_s2_flip, Tr.t("ui_swap_size", "Swap width and height"), false, v -> {
            if (!Bridge.ready()) return;
            Bridge.query(() -> {
                Bridge.editor().swapLogicSize();
                PropertySet<String, Object> cfg = Bridge.editor().getConfig();
                return new String[]{cfg.getString("logicWidth"), cfg.getString("logicHeight")};
            }, r -> {
                w.setText(r[0]);
                h.setText(r[1]);
                Ui.toast(c, Tr.t("saved", "Saved"));
            });
        });
        col.addView(swapBtn, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 0, 6, 0, 0));
        if (Bridge.ready()) Bridge.query(() -> {
            PropertySet<String, Object> cfg = Bridge.editor().getConfig();
            return new String[]{cfg.getString("logicWidth"), cfg.getString("logicHeight"), String.valueOf(Bridge.editor().isLandscape())};
        }, r -> {
            w.setText(r[0]);
            h.setText(r[1]);
            boolean land = Boolean.parseBoolean(r[2]);
            // same labels as the old dialog: values are swapped in portrait
            w.setHint(land ? "Width" : "Height");
            h.setHint(land ? "Height" : "Width");
        });

        // ---- AI
        col.addView(Ui.sectionLabel(c, Tr.t("ui_ai", "AI assistant")));
        LinearLayout ai = Ui.button(c, R.drawable.ic_s2_ai, Tr.t("ui_ai_settings", "AI providers & API keys"), false, v -> {
            shell.panels.show(shell.ai);
            shell.ai.showSettings();
        });
        col.addView(ai, new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)));

        col.addView(Ui.sectionLabel(c, Tr.t("bc_title", "Block creator")));
        col.addView(Ui.button(c, R.drawable.ic_s2_wand, Tr.t("bc_open", "Make your own blocks"), false, v -> com.star4droid.star2d.ui.blocks.BlockCreatorActivity.open(shell.activity())),
                new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)));
        col.addView(Ui.sectionLabel(c, Tr.t("ui_help", "Help")));
        col.addView(Ui.button(c, R.drawable.ic_s2_help, Tr.t("ui_quick_guide", "Quick guide"), false, v -> com.star4droid.star2d.ui.Help.show(c)),
                new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)));

        ScrollView s = new ScrollView(c);
        s.addView(col);
        return s;
    }

    interface OnBool { void on(boolean v); }

    private View sw(Context c, SharedPreferences p, String key, String label, OnBool after) {
        SwitchCompat s = new SwitchCompat(c);
        s.setText(label);
        s.setTextColor(Ui.TEXT);
        s.setTextSize(14.5f);
        s.setMinHeight(Ui.dp(c, 48));
        s.setChecked(p.getBoolean(key, true));
        s.setOnCheckedChangeListener((b, v) -> {
            p.edit().putBoolean(key, v).apply();
            if (after != null) after.on(v);
        });
        return s;
    }

    private TextView chooser(Context c, String text) {
        TextView t = Ui.text(c, text, 14, Ui.TEXT);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        t.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_expand_more, 0);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.setMargins(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
        t.setLayoutParams(lp);
        return t;
    }
}
