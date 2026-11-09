package com.star4droid.star2d.suda;

import android.app.Activity;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Ui;

import java.util.Map;

/** Builds the parameter rows of an operation (main ones + "More") bound to a key → value map. */
public final class ParamForm {
    private ParamForm() {}

    public static void fill(Activity a, Ops.Op op, Map<String, String> values, LinearLayout main, LinearLayout more, Runnable changed) {
        for (Ops.Param p : op.params) {
            if (p.type == Ops.T.PROMPT) continue; // the prompt has its own big box
            LinearLayout target = p.main ? main : more;
            target.addView(row(a, p, values, changed), Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 0));
        }
    }

    static String value(Ops.Param p, Map<String, String> values) {
        String v = values.get(p.key);
        if (v == null) v = p.def;
        if ((v == null || v.isEmpty()) && p.type == Ops.T.CHOICE && p.choices != null && p.choices.length > 0) v = p.choices[0];
        return v == null ? "" : v;
    }

    static LinearLayout row(Activity a, Ops.Param p, Map<String, String> values, Runnable changed) {
        LinearLayout r = Ui.row(a);
        r.setMinimumHeight(Ui.dp(a, 44));
        TextView label = Ui.text(a, p.label(), 14, Ui.TEXT);
        r.addView(label, Ui.weight(1));
        switch (p.type) {
            case BOOL: {
                SwitchCompat s = new SwitchCompat(a);
                s.setChecked(Boolean.parseBoolean(value(p, values)));
                s.setOnCheckedChangeListener((b, on) -> { values.put(p.key, String.valueOf(on)); changed.run(); });
                r.addView(s);
                break;
            }
            case CHOICE: {
                TextView v = Ui.text(a, "", 14, Ui.ACCENT);
                v.setGravity(Gravity.END);
                v.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
                String cur = value(p, values);
                v.setText(cur.isEmpty() ? "–" : SudaUi.choiceLabel(cur) + " ▾");
                v.setOnClickListener(x -> {
                    if (p.choices == null || p.choices.length == 0) return;
                    String[] labels = new String[p.choices.length];
                    for (int i = 0; i < labels.length; i++) labels[i] = SudaUi.choiceLabel(p.choices[i]);
                    Dialogs.choose(a, p.label(), labels, i -> {
                        values.put(p.key, p.choices[i]);
                        v.setText(labels[i] + " ▾");
                        changed.run();
                    });
                });
                r.addView(v);
                break;
            }
            default: {
                EditText e = Ui.input(a, value(p, values), p.type == Ops.T.INT ? "auto" : "");
                if (p.type == Ops.T.INT) e.setInputType(InputType.TYPE_CLASS_NUMBER);
                e.addTextChangedListener(QuickGen.watcher(s -> { values.put(p.key, s); changed.run(); }));
                if (p.type == Ops.T.TEXT) {
                    LinearLayout col = Ui.column(a);
                    col.addView(label);
                    r.removeView(label);
                    col.addView(e, Ui.matchWrap());
                    r.addView(col, Ui.weight(1));
                } else r.addView(e, new LinearLayout.LayoutParams(Ui.dp(a, 110), -2));
            }
        }
        return r;
    }
}
