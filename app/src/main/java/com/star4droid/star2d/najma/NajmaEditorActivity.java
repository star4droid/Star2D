package com.star4droid.star2d.najma;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.star4droid.najma.Najma;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The editor of Najma (.njm) files: made for right-to-left code. Native Android text (so Arabic shaping, mixed
 * directions and the cursor are the system's), every line takes its direction from its first letter (Arabic lines
 * right-to-left, Java lines left-to-right), colours, live errors in Arabic, suggestions with values to fill in,
 * a symbols bar, automatic indentation. Extras: "path" (.njm file), optional "goto_line" / "goto_msg".
 */
public class NajmaEditorActivity extends AppCompatActivity {
    private static final int BG = 0xFF1E1F22, FG = 0xFFE6E6E6, BAR = 0xFF2B2D30, ACCENT = 0xFFFFB454, ERR = 0xFFFF6B68;
    static final int[] COLORS = {0, 0xFFFFB454, 0xFF56C8D8, 0xFFB5CEA8, 0xFF9CDC8C, 0xFF7A7E85, 0xFF82AAFF, 0xFFBBBBBB, 0xFF9AA0A6};

    private String path, javaRoot, saved = "";
    private Code edit;
    private TextView status, title;
    private android.widget.PopupWindow pop;
    private LinearLayout popList;
    private ScrollView popScroll;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private Najma.Options options;
    private volatile Najma.Result last;
    private int checkId;
    private boolean editing;

    /** Opens the Najma editor when the path is a .njm file or a .java file generated from one. True = opened. */
    public static boolean openInstead(android.app.Activity from, String path, android.content.Intent original) {
        try {
            String njm = NajmaProject.isNajma(path) ? path : NajmaProject.sourceOf(path);
            if (njm == null) return false;
            android.content.Intent i = new android.content.Intent(from, NajmaEditorActivity.class);
            i.putExtra("path", njm);
            if (original != null && original.hasExtra("goto_line")) {
                i.putExtra("goto_line", original.getIntExtra("goto_line", 1));
                i.putExtra("goto_col", original.getIntExtra("goto_col", 1));
            }
            from.startActivity(i);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * A new element / scene script (the file does not exist yet): asks "Java or Najma". True = the question is on screen
     * and the caller must stop; it is recreated for Java, or replaced by the Najma editor.
     */
    public static boolean askLanguage(final android.app.Activity from, final String path, final android.content.Intent it) {
        try {
            final boolean body = it.getBooleanExtra("body", false);
            final boolean scene = it.hasExtra("scene") && !"".equals(it.getStringExtra("scene"));
            if (path == null || !path.endsWith(".java") || (!body && !scene) || it.getBooleanExtra("najma_asked", false)) return false;
            File f = new File(path);
            // an existing script is asked about only while it is still the untouched Java template
            final android.content.SharedPreferences sp = from.getSharedPreferences("najma", Context.MODE_PRIVATE);
            if (f.isFile() && f.length() > 0 && (sp.getBoolean("java:" + path, false) || !pristine(NajmaProject.read(f)))) return false;
            new AlertDialog.Builder(from).setTitle("لغة السكربت  ·  Script language")
                    .setItems(new CharSequence[]{"نجمة (عربي)", "Java"}, (d, which) -> {
                        if (which == 1) {
                            sp.edit().putBoolean("java:" + path, true).apply();
                            it.putExtra("najma_asked", true);
                            from.recreate();
                            return;
                        }
                        String njm = path.substring(0, path.length() - 5) + Najma.EXT;
                        NajmaProject.write(new File(njm), NajmaProject.template(scene ? "scene" : "item"));
                        try { String root = NajmaProject.javaRoot(njm); if (root != null) NajmaProject.compileFile(root, njm); } catch (Throwable ignored) { }
                        openInstead(from, njm, null);
                        from.finish();
                    })
                    .setOnCancelListener(d -> from.finish()).show();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** True for a Java script that has no code of the user yet (only the empty methods of the template). */
    static boolean pristine(String java) {
        String t = java.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
        t = t.replaceAll("(?m)^\\s*(package|import)\\s[^;]*;", "");
        return t.indexOf(';') < 0 && t.indexOf('=') < 0;
    }

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        javaRoot = NajmaProject.javaRoot(path);
        if (javaRoot == null) javaRoot = new File(path).getParent();
        try { options = NajmaProject.options(javaRoot, path); } catch (Throwable e) { options = new Najma.Options(); }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(BAR);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(button("✕", v -> back()));
        title = new TextView(this);
        title.setTextColor(FG);
        title.setTextSize(15);
        title.setSingleLine(true);
        title.setText(new File(path).getName());
        title.setPadding(dp(8), 0, dp(8), 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(button("؟  الشرح", v -> help()));
        top.addView(button("Java", v -> showJava()));
        top.addView(button("⌨", v -> toggleKeyboard()));
        TextView save = button("حفظ", v -> save(true));
        save.setTextColor(ACCENT);
        top.addView(save);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        edit = new Code(rtl(this));
        root.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(dp(10), dp(6), dp(10), dp(6));
        status.setBackgroundColor(BAR);
        status.setMaxLines(3);
        status.setTextDirection(View.TEXT_DIRECTION_RTL);
        status.setOnClickListener(v -> { Najma.Result r = last; if (r != null && !r.errors.isEmpty()) goTo(r.errors.get(0).line, r.errors.get(0).col); });
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout symbols = new LinearLayout(this);
        symbols.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        symbols.setBackgroundColor(BAR);
        symbols.addView(key("⇥", v -> insert("    ", 4)));
        symbols.addView(key("{ }", v -> block()));
        for (String s : new String[]{"(", ")", "\"", "=", "،", ".", "_", "[", "]", ":", "+", "-", "×", "÷", "<", ">", "←", "؛", "#", "!", "٪"}) {
            final String sym = s;
            symbols.addView(key(s, v -> insert(sym.equals("\"") ? "\"\"" : sym, 1)));
        }
        symbols.addView(key("◀", v -> moveCursor(1)));
        symbols.addView(key("▶", v -> moveCursor(-1)));
        symbols.addView(key("↶", v -> edit.undo()));
        symbols.addView(key("↷", v -> edit.redo()));
        HorizontalScrollView symScroll = new HorizontalScrollView(this);
        symScroll.setHorizontalScrollBarEnabled(false);
        symScroll.setBackgroundColor(BAR);
        symScroll.addView(symbols);
        root.addView(symScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        setContentView(root);
        // bars stay above the keyboard (edge-to-edge ignores adjustResize)
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        File f = new File(path);
        if (!f.isFile() || f.length() == 0) NajmaProject.write(f, NajmaProject.template(options.kind));
        saved = NajmaProject.read(f).replace("\r\n", "\n");
        editing = true;
        edit.setText(saved);
        editing = false;
        edit.clearHistory();
        recolor();
        check();
        bg.execute(() -> { try { Najma.warmUp(); } catch (Throwable ignored) { } });

        edit.addTextChangedListener(new TextWatcher() {
            int at, added;
            String before = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { if (!editing) before = s.subSequence(start, start + count).toString(); }
            @Override public void onTextChanged(CharSequence s, int start, int b, int count) { at = start; added = count; }
            @Override public void afterTextChanged(Editable s) {
                if (editing) return;
                edit.record(at, before, s.subSequence(at, Math.min(s.length(), at + added)).toString());
                if (!edit.replaying && added == 1 && before.isEmpty() && at < s.length() && s.charAt(at) == '\n') indent(s, at);
                ui.removeCallbacks(recolorLater);
                ui.postDelayed(recolorLater, 120);
                ui.removeCallbacks(checkLater);
                ui.postDelayed(checkLater, 600);
                suggest();
            }
        });
        edit.onCursor = this::suggest;

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        if (getIntent().hasExtra("goto_line")) {
            final int line = getIntent().getIntExtra("goto_line", 1), col = getIntent().getIntExtra("goto_col", 1);
            edit.post(() -> goTo(line, col));
        }
    }

    private final Runnable recolorLater = this::recolor, checkLater = this::check;

    private TextView button(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(FG);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setMinWidth(dp(44));
        t.setOnClickListener(l);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return t;
    }

    private TextView key(String text, View.OnClickListener l) {
        TextView t = button(text, l);
        t.setTextSize(18);
        t.setMinWidth(dp(46));
        return t;
    }

    // ------------------------------------------------------------------ editing helpers
    private void insert(String s, int cursorAfter) {
        int a = Math.max(0, edit.getSelectionStart()), b = Math.max(a, edit.getSelectionEnd());
        edit.getText().replace(Math.min(a, b), Math.max(a, b), s);
        edit.setSelection(Math.min(edit.length(), Math.min(a, b) + cursorAfter));
    }

    /** { } on three lines with the cursor inside, indented like the current line. */
    private void block() {
        Editable t = edit.getText();
        int a = Math.max(0, edit.getSelectionStart());
        String ind = indentOf(t, a);
        boolean space = a > 0 && t.charAt(a - 1) != ' ' && t.charAt(a - 1) != '\n';
        String s = (space ? " " : "") + "{\n" + ind + "    \n" + ind + "}";
        editing = true;
        t.replace(a, Math.max(a, edit.getSelectionEnd()), s);
        editing = false;
        edit.record(a, "", s);
        edit.setSelection(a + (space ? 1 : 0) + 2 + ind.length() + 4);
        recolor();
        ui.postDelayed(checkLater, 300);
    }

    private static String indentOf(CharSequence t, int pos) {
        int ls = pos;
        while (ls > 0 && t.charAt(ls - 1) != '\n') ls--;
        int e = ls;
        while (e < t.length() && (t.charAt(e) == ' ' || t.charAt(e) == '\t')) e++;
        return t.subSequence(ls, Math.min(e, pos)).toString();
    }

    /** After Enter: the new line starts like the previous one, one step deeper after an opening brace. */
    private void indent(Editable s, int nl) {
        String ind = indentOf(s, nl);
        int k = nl - 1;
        while (k >= 0 && s.charAt(k) == ' ') k--;
        if (k >= 0 && (s.charAt(k) == '{' || s.charAt(k) == ':')) ind += "    ";
        if (ind.isEmpty()) return;
        editing = true;
        s.insert(nl + 1, ind);
        editing = false;
        edit.record(nl + 1, "", ind);
    }

    private void moveCursor(int d) {
        int p = Math.max(0, Math.min(edit.length(), edit.getSelectionStart() + d));
        edit.setSelection(p);
    }

    private void toggleKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        boolean shown = ViewCompat.getRootWindowInsets(edit) != null && ViewCompat.getRootWindowInsets(edit).isVisible(WindowInsetsCompat.Type.ime());
        if (shown) imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        else { edit.requestFocus(); imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT); }
    }

    private void goTo(int line, int col) {
        CharSequence t = edit.getText();
        int pos = 0;
        for (int l = 1; l < line && pos < t.length(); pos++) if (t.charAt(pos) == '\n') l++;
        pos = Math.min(t.length(), pos + Math.max(0, col - 1));
        edit.requestFocus();
        edit.setSelection(pos);
    }

    // ------------------------------------------------------------------ colours, errors
    static final class Tint extends ForegroundColorSpan { Tint(int c) { super(c); } }

    private void recolor() {
        Editable t = edit.getText();
        if (t == null) return;
        int[] hs = Najma.highlight(t.toString());
        for (Tint old : t.getSpans(0, t.length(), Tint.class)) t.removeSpan(old);
        for (int i = 0; i + 2 < hs.length; i += 3) {
            int a = hs[i], b = Math.min(hs[i + 1], t.length());
            if (a < b && hs[i + 2] < COLORS.length) t.setSpan(new Tint(COLORS[hs[i + 2]]), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void check() {
        final String src = edit.getText().toString();
        final int id = ++checkId;
        bg.execute(() -> {
            Najma.Result r;
            try { r = Najma.compile(src, options); } catch (Throwable e) { return; }
            ui.post(() -> {
                if (id != checkId || isFinishing()) return;
                last = r;
                Set<Integer> lines = new LinkedHashSet<>();
                for (Najma.Diag d : r.errors) lines.add(d.line);
                edit.errorLines = lines;
                edit.invalidate();
                if (r.errors.isEmpty()) {
                    status.setTextColor(0xFF8BC34A);
                    status.setText("✓ لا توجد أخطاء");
                } else {
                    Najma.Diag d = r.errors.get(0);
                    status.setTextColor(ERR);
                    status.setText("سطر " + d.line + ": " + d.message + (r.errors.size() > 1 ? "  (+" + (r.errors.size() - 1) + " أخرى)" : ""));
                }
            });
        });
    }

    // ------------------------------------------------------------------ suggestions (a bubble at the cursor)
    /**
     * The views of this editor behave as in an app with right-to-left support (the editor app itself declares
     * supportsRtl=false, which would force every line without letters, like a lone brace, to the left).
     */
    static Context rtl(Context base) {
        return new android.content.ContextWrapper(base) {
            android.content.pm.ApplicationInfo info;
            @Override public android.content.pm.ApplicationInfo getApplicationInfo() {
                if (info == null) {
                    info = new android.content.pm.ApplicationInfo(super.getApplicationInfo());
                    info.flags |= android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL;
                }
                return info;
            }
        };
    }

    private void suggest() {
        Editable t = edit.getText();
        int c = edit.getSelectionStart();
        if (t == null || c < 0 || c != edit.getSelectionEnd() || !edit.hasFocus()) { hidePopup(); return; }
        int s = c;
        while (s > 0 && isWord(t.charAt(s - 1))) s--;
        Najma.Result r = last;
        List<Najma.Suggestion> list;
        try {
            list = Najma.completeAt(t.toString(), c, options.kind, r == null ? null : r.names);
        } catch (Throwable e) {
            list = new ArrayList<>();
        }
        showPopup(list, s, c);
    }

    private void hidePopup() {
        if (pop != null && pop.isShowing()) try { pop.dismiss(); } catch (Throwable ignored) { }
    }

    private void showPopup(List<Najma.Suggestion> list, final int from, int cursor) {
        Layout l = edit.getLayout();
        if (list.isEmpty() || l == null || isFinishing()) { hidePopup(); return; }
        if (pop == null) {
            popList = new LinearLayout(this);
            popList.setOrientation(LinearLayout.VERTICAL);
            popScroll = new ScrollView(this);
            popScroll.addView(popList);
            android.graphics.drawable.GradientDrawable bgd = new android.graphics.drawable.GradientDrawable();
            bgd.setColor(0xFF2F3136);
            bgd.setCornerRadius(dp(12));
            bgd.setStroke(dp(1), 0xFF4A4D55);
            popScroll.setBackground(bgd);
            popScroll.setPadding(dp(4), dp(4), dp(4), dp(4));
            popScroll.setClipToOutline(true);
            pop = new android.widget.PopupWindow(popScroll, dp(280), dp(200), false);
            pop.setTouchable(true);
            pop.setOutsideTouchable(false);
            pop.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NEEDED);
            pop.setElevation(dp(10));
            pop.setAnimationStyle(0);
        }
        popList.removeAllViews();
        for (final Najma.Suggestion sg : list) {
            LinearLayout row = new LinearLayout(this);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), 0, dp(10), 0);
            TextView note = new TextView(this);
            note.setText(sg.note == null ? "" : sg.note);
            note.setTextColor(0xFF8A8F98);
            note.setTextSize(11);
            note.setSingleLine(true);
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            note.setMaxWidth(dp(120));
            TextView label = new TextView(this);
            label.setText(sg.label);
            label.setTextColor(FG);
            label.setTextSize(15);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.START);
            label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = dp(8);
            row.addView(label, lp);
            row.setOnClickListener(v -> accept(from, sg));
            popList.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        }
        popScroll.scrollTo(0, 0);
        int rows = Math.min(list.size(), 5);
        int w = Math.min(dp(290), edit.getWidth() - dp(16)), h = rows * dp(38) + dp(8);
        int line = l.getLineForOffset(Math.min(cursor, edit.length()));
        int[] loc = new int[2];
        edit.getLocationInWindow(loc);
        int cx = loc[0] + edit.getTotalPaddingLeft() + (int) l.getPrimaryHorizontal(Math.min(cursor, edit.length())) - edit.getScrollX();
        int below = loc[1] + edit.getTotalPaddingTop() + l.getLineBottom(line) - edit.getScrollY() + dp(4);
        int above = loc[1] + edit.getTotalPaddingTop() + l.getLineTop(line) - edit.getScrollY() - h - dp(4);
        int y = below + h <= loc[1] + edit.getHeight() || above < loc[1] ? below : above;
        int x = Math.max(loc[0] + dp(8), Math.min(cx - w / 2, loc[0] + edit.getWidth() - w - dp(8)));
        try {
            if (pop.isShowing()) pop.update(x, y, w, h);
            else {
                pop.setWidth(w);
                pop.setHeight(h);
                pop.showAtLocation(edit, Gravity.NO_GRAVITY, x, y);
            }
        } catch (Throwable ignored) { }
    }

    private static boolean isWord(char c) { return Character.isLetterOrDigit(c) || c == '_' || (c >= 'ً' && c <= 'ْ'); }

    private static boolean sameName(String a, String b) {
        String x = a.startsWith("ال") && a.length() > 3 ? a.substring(2) : a, y = b.startsWith("ال") && b.length() > 3 ? b.substring(2) : b;
        return x.equals(y) || x.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').equals(y.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا'));
    }

    /** Puts the suggestion in place of the typed word; the first value to fill in ({like this}) is selected. */
    private void accept(int from, Najma.Suggestion s) {
        Editable t = edit.getText();
        int c = Math.max(from, edit.getSelectionStart());
        String ind = indentOf(t, from);
        String text = s.insert.replace("\n", "\n" + ind);
        t.replace(from, c, text);
        int open = text.indexOf('{'), close = open < 0 ? -1 : text.indexOf('}', open);
        boolean hole = open >= 0 && close > open + 1 && text.indexOf('\n', open) != open + 1 && text.charAt(open + 1) != ' ';
        if (hole) edit.setSelection(from + open, from + close + 1);
        else {
            int inner = text.indexOf("\n" + ind + "    \n");
            int paren = text.endsWith("()") ? text.length() - 1 : -1;
            edit.setSelection(inner >= 0 ? from + inner + 1 + ind.length() + 4 : paren >= 0 ? from + paren : from + text.length());
        }
    }

    // ------------------------------------------------------------------ save, dialogs
    private void save(boolean show) {
        String text = edit.getText().toString();
        NajmaProject.write(new File(path), text);
        saved = text;
        bg.execute(() -> {
            final Najma.Result r;
            try { r = NajmaProject.compileFile(javaRoot, path); } catch (Throwable e) { return; }
            if (show) ui.post(() -> {
                if (isFinishing()) return;
                title.setText(new File(path).getName() + (r.ok() ? "  ✓ حُفظ" : "  حُفظ (فيه أخطاء)"));
                ui.postDelayed(() -> title.setText(new File(path).getName()), 1800);
            });
        });
    }

    private void back() {
        hidePopup();
        if (edit == null || saved.equals(edit.getText().toString())) { finish(); return; }
        new AlertDialog.Builder(this).setMessage("هل تريد حفظ التعديلات؟")
                .setPositiveButton("حفظ", (d, w) -> { save(false); finish(); })
                .setNeutralButton("خروج بدون حفظ", (d, w) -> finish())
                .setNegativeButton("إلغاء", null).show();
    }

    private void showJava() {
        hidePopup();
        Najma.Result r = last;
        TextView tv = new TextView(this);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setTextIsSelectable(true);
        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        tv.setText(r == null ? "" : r.java.replace(Najma.MARK, "").replaceAll("\n{3,}", "\n\n"));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle("كود Java الناتج").setView(sv).setPositiveButton("إغلاق", null).show();
    }

    private void help() {
        hidePopup();
        startActivity(new android.content.Intent(this, NajmaGuideActivity.class));
    }

    @Override
    protected void onPause() {
        super.onPause();
        hidePopup();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // variables written in the script show up in Properties: the scanner reads the generated .java file
        try {
            if (new File(NajmaProject.javaOf(path)).isFile())
                com.star4droid.star2d.ui.behaviors.ScriptVarWatcher.onEditorClosed(NajmaProject.javaOf(path));
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hidePopup();
        ui.removeCallbacksAndMessages(null);
        bg.shutdown();
    }

    // ------------------------------------------------------------------ the text view
    /** Multi-line code field: direction per line from its first letter, line numbers at the start edge, undo / redo. */
    static final class Code extends AppCompatEditText {
        Set<Integer> errorLines = new LinkedHashSet<>();
        Runnable onCursor;
        private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG), errBg = new Paint();
        private final ArrayList<String[]> undo = new ArrayList<>(), redo = new ArrayList<>();
        boolean replaying;
        private final int gutter;

        Code(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            gutter = (int) (38 * d);
            setBackgroundColor(BG);
            setTextColor(FG);
            setTextSize(16);
            setTypeface(Typeface.DEFAULT);
            setGravity(Gravity.TOP | Gravity.START);
            setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
            setHorizontallyScrolling(false);
            setLineSpacing(4 * d, 1f);
            // no large bottom padding: a text view does not draw inside its padding (lines would look cut)
            setPaddingRelative(gutter + (int) (6 * d), (int) (8 * d), (int) (10 * d), (int) (10 * d));
            setVerticalScrollBarEnabled(true);
            num.setColor(0xFF6E737A);
            num.setTextSize(11 * d);
            num.setTextAlign(Paint.Align.CENTER);
            errBg.setColor(0x33FF5252);
        }

        @Override
        protected void onSelectionChanged(int s, int e) {
            super.onSelectionChanged(s, e);
            if (onCursor != null) onCursor.run();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Layout l = getLayout();
            CharSequence t = getText();
            if (l != null && t != null) {
                int top = getScrollY() - getTotalPaddingTop(), first = l.getLineForVertical(Math.max(0, top)), lastLine = l.getLineForVertical(top + getHeight());
                int para = 1;
                int startOff = l.getLineStart(first);
                for (int i = 0; i < startOff && i < t.length(); i++) if (t.charAt(i) == '\n') para++;
                if (startOff > 0 && startOff <= t.length() && t.charAt(startOff - 1) != '\n') para++; // first visible line continues a wrapped line
                boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                float x = rtl ? getWidth() - gutter / 2f : gutter / 2f;
                canvas.save();
                canvas.translate(getScrollX(), getTotalPaddingTop());
                for (int ln = first; ln <= lastLine; ln++) {
                    int ls = l.getLineStart(ln);
                    boolean startOfPara = ls == 0 || t.charAt(ls - 1) == '\n';
                    if (errorLines.contains(para - (startOfPara ? 0 : 1)))
                        canvas.drawRect(0, l.getLineTop(ln), getWidth(), l.getLineBottom(ln), errBg);
                    if (startOfPara) {
                        num.setColor(errorLines.contains(para) ? ERR : 0xFF6E737A);
                        canvas.drawText(String.valueOf(para), x, l.getLineBaseline(ln), num);
                        para++;
                    }
                }
                canvas.restore();
            }
            super.onDraw(canvas);
        }

        // undo / redo: one entry per edit {position, removed text, inserted text}; typing in a row is merged
        void record(int at, String removed, String added) {
            if (replaying || (removed.isEmpty() && added.isEmpty())) return;
            redo.clear();
            if (!undo.isEmpty() && removed.isEmpty() && added.length() == 1 && !added.equals("\n") && !added.equals(" ")) {
                String[] p = undo.get(undo.size() - 1);
                if (p[1].isEmpty() && !p[2].endsWith("\n") && Integer.parseInt(p[0]) + p[2].length() == at) { p[2] += added; return; }
            }
            undo.add(new String[]{String.valueOf(at), removed, added});
            if (undo.size() > 300) undo.remove(0);
        }

        void clearHistory() { undo.clear(); redo.clear(); }

        void undo() { step(undo, redo, true); }
        void redo() { step(redo, undo, false); }

        private void step(ArrayList<String[]> from, ArrayList<String[]> to, boolean back) {
            if (from.isEmpty() || getText() == null) return;
            String[] e = from.remove(from.size() - 1);
            int at = Integer.parseInt(e[0]);
            String out = back ? e[2] : e[1], in = back ? e[1] : e[2];
            if (at + out.length() > length()) return;
            replaying = true;
            getText().replace(at, at + out.length(), in);
            replaying = false;
            setSelection(Math.min(length(), at + in.length()));
            to.add(e);
        }
    }
}
