package com.star4droid.star2d.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/** Small Android dialogs used by the editor (input, confirm, choose, long text). */
public final class Dialogs {
    private Dialogs() {}

    public interface Text { void on(String value); }
    public interface Confirm { void on(); }
    public interface Choice { void on(int index); }
    /** Validate input: return an error message or null when OK. */
    public interface Validator { String check(String value); }

    public static void input(Context c, String title, String message, String initial, Validator validator, Text onOk) {
        input(c, title, message, initial, false, validator, onOk);
    }

    public static void input(Context c, String title, String message, String initial, boolean multiline, Validator validator, Text onOk) {
        EditText e = Ui.input(c, initial == null ? "" : initial, "");
        if (multiline) {
            e.setSingleLine(false);
            e.setMinLines(3);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        e.selectAll();
        FrameLayout box = new FrameLayout(c);
        int p = Ui.dp(c, 20);
        box.setPadding(p, Ui.dp(c, 6), p, 0);
        box.addView(e);
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(message)
                .setView(box)
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = e.getText().toString();
            String err = validator == null ? null : validator.check(value);
            if (err != null) {
                e.setError(err);
                return;
            }
            d.dismiss();
            onOk.on(value);
        }));
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        e.requestFocus();
    }

    public static void confirm(Context c, String title, String message, String okLabel, Confirm onOk) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(okLabel == null ? Tr.t("ok", "OK") : okLabel, (d, w) -> onOk.on())
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    public static void choose(Context c, String title, String[] items, Choice onPick) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setItems(items, (d, w) -> onPick.on(w))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    /** Scrollable, selectable monospace text with a Copy button. */
    public static AlertDialog longText(Context c, String title, String text) {
        // a huge text laid out in one TextView freezes the app (ANR): show the start, Copy still gives everything
        final int cap = 20000;
        String shown = text == null ? "" : text;
        if (shown.length() > cap) shown = shown.substring(0, cap) + "\n\n… (" + (text.length() - cap) + " " + Tr.t("pg_more_chars", "more characters, tap Copy to get all of it") + ")";
        TextView t = Ui.text(c, shown, 12.5f, Ui.TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.addView(t);
        ScrollView s = new ScrollView(c);
        s.addView(h);
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(s)
                .setPositiveButton(Tr.t("close", "Close"), null)
                .setNeutralButton(Tr.t("copy", "Copy"), (x, w) -> copy(c, text))
                .create();
        d.show();
        return d;
    }

    public static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("star2d", text));
        Ui.toast(c, Tr.t("ui_copied", "Copied"));
    }

    /** A dialog hosting any view, with optional title and buttons. */
    public static AlertDialog custom(Context c, String title, View content, String ok, Confirm onOk) {
        AlertDialog.Builder b = new AlertDialog.Builder(c).setView(content);
        if (title != null) b.setTitle(title);
        if (ok != null) b.setPositiveButton(ok, (d, w) -> { if (onOk != null) onOk.on(); });
        b.setNegativeButton(Tr.t("close", "Close"), null);
        AlertDialog d = b.create();
        d.show();
        // a tall dialog (a form in a ScrollView) must keep its buttons on screen: cap the window to 85% of the screen height
        try {
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            android.view.Window w = d.getWindow();
            if (w != null) {
                w.setLayout(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                final int maxH = (int) (dm.heightPixels * 0.85f);
                w.getDecorView().post(() -> {
                    if (w.getDecorView().getHeight() > maxH) w.setLayout(w.getDecorView().getWidth(), maxH);
                });
            }
        } catch (Throwable ignored) {
        }
        return d;
    }

    /** Standard name validation used by the engine (A-Z a-z 0-9 _). */
    public static String checkName(String v) {
        if (v.trim().isEmpty()) return Tr.t("emptyError", "Enter a name");
        for (char ch : v.toCharArray())
            if ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_".indexOf(ch) < 0)
                return Tr.t("ui_bad_char", "Not allowed") + ": " + ch;
        return null;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }
}
