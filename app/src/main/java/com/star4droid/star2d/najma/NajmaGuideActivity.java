package com.star4droid.star2d.najma;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.star4droid.najma.Najma;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The guide of the Najma language (the "؟" button of its editor): sections from "hello world" to classes and libGDX
 * plus a large examples section. The text is the asset najma/guide.md:
 * "# section", "> one line shown in the index", "## heading", "- item", paragraphs and code fences. Code is shown in
 * rounded cards with the editor's colours and a copy button. Every code block of that file is compiled by
 * najma/tools/test_najma.py, so the examples stay correct.
 */
public class NajmaGuideActivity extends AppCompatActivity {
    private static final int BG = 0xFF1E1F22, FG = 0xFFE6E6E6, BAR = 0xFF2B2D30, ACCENT = 0xFFFFB454, DIM = 0xFF9AA0A6, CARD = 0xFF15171B;

    private static final class Section {
        String title = "", desc = "";
        final List<String[]> blocks = new ArrayList<>(); // {kind, text[, code kind]}
    }

    private final List<Section> sections = new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private TextView title;
    private int open = -1;
    private Context rtl;

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        rtl = NajmaEditorActivity.rtl(this);
        load();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(BAR);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = text("✕", 18, FG, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> back());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT));
        title = text("", 16, FG, true);
        title.setSingleLine(true);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12), 0, dp(14), 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content = new LinearLayout(rtl);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(14), dp(12), dp(14), dp(40));
        scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        showIndex();
    }

    private void back() {
        if (open >= 0) showIndex();
        else finish();
    }

    private void load() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("najma/guide.md"), "UTF-8"))) {
            String line;
            Section cur = null;
            StringBuilder code = null, para = new StringBuilder();
            String codeKind = "";
            while ((line = r.readLine()) != null) {
                if (code != null) {
                    if (line.trim().equals("```")) {
                        if (cur != null) cur.blocks.add(new String[]{"code", code.toString().replaceAll("\\n+$", ""), codeKind});
                        code = null;
                    } else code.append(line).append('\n');
                    continue;
                }
                String t = line.trim();
                if (t.startsWith("```")) { flush(cur, para); code = new StringBuilder(); codeKind = t.substring(3).trim(); continue; }
                if (t.startsWith("# ")) { flush(cur, para); cur = new Section(); cur.title = t.substring(2).trim(); sections.add(cur); continue; }
                if (cur == null) continue;
                if (t.startsWith("> ")) { cur.desc = t.substring(2).trim(); continue; }
                if (t.startsWith("## ")) { flush(cur, para); cur.blocks.add(new String[]{"h", t.substring(3).trim()}); continue; }
                if (t.startsWith("- ")) { flush(cur, para); cur.blocks.add(new String[]{"li", t.substring(2).trim()}); continue; }
                if (t.startsWith("! ")) { flush(cur, para); cur.blocks.add(new String[]{"note", t.substring(2).trim()}); continue; }
                if (t.isEmpty()) { flush(cur, para); continue; }
                para.append(para.length() > 0 ? " " : "").append(t);
            }
            flush(cur, para);
        } catch (Exception e) {
            Section s = new Section();
            s.title = "الشرح غير متاح";
            s.desc = String.valueOf(e);
            sections.add(s);
        }
    }

    private static void flush(Section cur, StringBuilder para) {
        if (cur != null && para.length() > 0) cur.blocks.add(new String[]{"p", para.toString()});
        para.setLength(0);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(rtl);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    private GradientDrawable round(int color, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams wide(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        return lp;
    }

    private void showIndex() {
        open = -1;
        title.setText("شرح لغة نجمة");
        content.removeAllViews();
        TextView intro = text("من أول سطر حتى الاحتراف. اختر قسماً، وكل كود يمكن نسخه ولصقه في المحرر.", 14, DIM, false);
        content.addView(intro, wide(0));
        for (int i = 0; i < sections.size(); i++) {
            final int idx = i;
            Section s = sections.get(i);
            LinearLayout card = new LinearLayout(rtl);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(round(BAR, 0xFF3A3D44, 14));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView num = text(String.valueOf(i + 1), 15, 0xFF1E1F22, true);
            num.setGravity(Gravity.CENTER);
            num.setBackground(round(ACCENT, 0, 16));
            card.addView(num, new LinearLayout.LayoutParams(dp(32), dp(32)));
            LinearLayout col = new LinearLayout(rtl);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            col.setPadding(dp(12), 0, dp(12), 0);
            col.addView(text(s.title, 16, FG, true));
            if (!s.desc.isEmpty()) col.addView(text(s.desc, 13, DIM, false));
            card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.setOnClickListener(v -> showSection(idx));
            content.addView(card, wide(10));
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void showSection(int idx) {
        open = idx;
        Section s = sections.get(idx);
        title.setText(s.title);
        content.removeAllViews();
        if (!s.desc.isEmpty()) content.addView(text(s.desc, 14, DIM, false), wide(0));
        for (String[] b : s.blocks) {
            switch (b[0]) {
                case "h": content.addView(text(b[1], 17, ACCENT, true), wide(18)); break;
                case "p": { TextView t = text(b[1], 15, FG, false); t.setLineSpacing(dp(3), 1); content.addView(t, wide(8)); break; }
                case "li": { TextView t = text("•  " + b[1], 15, FG, false); t.setLineSpacing(dp(3), 1); content.addView(t, wide(6)); break; }
                case "note": {
                    TextView t = text(b[1], 14, 0xFFFFE0A6, false);
                    t.setBackground(round(0xFF3A3222, 0xFF6B5A2E, 10));
                    t.setPadding(dp(12), dp(8), dp(12), dp(8));
                    t.setLineSpacing(dp(3), 1);
                    content.addView(t, wide(10));
                    break;
                }
                default: content.addView(codeCard(b[1], b.length > 2 ? b[2] : ""), wide(10));
            }
        }
        LinearLayout nav = new LinearLayout(rtl);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView index = text("الفهرس", 15, FG, true);
        index.setGravity(Gravity.CENTER);
        index.setBackground(round(BAR, 0xFF3A3D44, 12));
        index.setOnClickListener(v -> showIndex());
        nav.addView(index, new LinearLayout.LayoutParams(0, dp(46), 1));
        if (idx + 1 < sections.size()) {
            TextView next = text("التالي: " + sections.get(idx + 1).title + "  ←", 15, 0xFF1E1F22, true);
            next.setGravity(Gravity.CENTER);
            next.setSingleLine(true);
            next.setBackground(round(ACCENT, 0, 12));
            next.setOnClickListener(v -> showSection(idx + 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 2);
            lp.setMarginStart(dp(10));
            nav.addView(next, lp);
        }
        content.addView(nav, wide(24));
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private static String kindLabel(String kind) {
        switch (kind) {
            case "عنصر": return "سكربت عنصر";
            case "مشهد": return "سكربت مشهد";
            case "صنف": return "ملف مستقل";
            case "ناتج": return "الناتج";
            default: return "كود";
        }
    }

    private View codeCard(final String code, String kind) {
        LinearLayout card = new LinearLayout(rtl);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setBackground(round(CARD, 0xFF2E3138, 12));
        card.setPadding(dp(12), dp(6), dp(12), dp(12));
        LinearLayout head = new LinearLayout(rtl);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(kindLabel(kind), 12, DIM, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!kind.equals("ناتج")) {
            final TextView copy = text("نسخ", 13, ACCENT, true);
            copy.setPadding(dp(12), dp(6), dp(12), dp(6));
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("najma", code));
                copy.setText("تم النسخ ✓");
                copy.postDelayed(() -> copy.setText("نسخ"), 1500);
            });
            head.addView(copy);
        }
        card.addView(head);
        TextView tv = text("", 14.5f, FG, false);
        tv.setLineSpacing(dp(4), 1);
        tv.setTextIsSelectable(true);
        SpannableString sp = new SpannableString(code);
        if (!kind.equals("ناتج")) {
            int[] hs = Najma.highlight(code);
            for (int i = 0; i + 2 < hs.length; i += 3) {
                int a = hs[i], b = Math.min(hs[i + 1], code.length());
                if (a < b && hs[i + 2] < NajmaEditorActivity.COLORS.length)
                    sp.setSpan(new NajmaEditorActivity.Tint(NajmaEditorActivity.COLORS[hs[i + 2]]), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else tv.setTextColor(0xFF9CDC8C);
        tv.setText(sp);
        card.addView(tv, wide(2));
        return card;
    }
}
