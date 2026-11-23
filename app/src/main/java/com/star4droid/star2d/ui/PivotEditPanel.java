package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.editor.PivotEdit;
import com.star4droid.star2d.evo.R;

/**
 * Over the editor while the pivot (2D) / anchor (3D) of the selected element is edited: Done, Cancel, the side presets
 * (left / center / right on each axis) and "Bounds" (keeps the point inside the element). The other bars and panels are
 * hidden meanwhile so nothing else changes by accident. Works in both orientations (one top bar that shrinks its labels,
 * the values at the bottom).
 */
public class PivotEditPanel extends FrameLayout {
    public static PivotEditPanel current;
    final EditorShell shell;
    final Activity a;
    final boolean d3;
    boolean bounds;
    private TextView info;
    private ImageView boundsBtn;
    private final Runnable onDone;

    /** Starts editing the selected element; {@code onDone} runs on the UI thread when editing ends (Done or Cancel). */
    public static void open(EditorShell shell, Runnable onDone) {
        if (current != null || PathDrawPanel.current != null || NavPaintPanel.current != null || TerrainPanel.current != null) return;
        Bridge.query(() -> {
            com.star4droid.star2d.editor.LibgdxEditor ed = Bridge.editor();
            if (ed == null) return null;
            if (ed.pathDraw.active()) ed.pathDraw.stop();
            if (ed.navPaint.active()) ed.navPaint.stop();
            return ed.pivotEdit.start();
        }, r -> {
            if (r == null) {
                Ui.toast(shell.activity(), Tr.t("pv_none", "Select an element first"));
                return;
            }
            PivotEditPanel p = new PivotEditPanel(shell, r[0], r[1], onDone);
            current = p;
            shell.enterOverlay(p);
        });
    }

    PivotEditPanel(EditorShell shell, boolean d3, boolean bounds, Runnable onDone) {
        super(shell.activity());
        this.shell = shell;
        this.a = shell.activity();
        this.d3 = d3;
        this.bounds = bounds;
        this.onDone = onDone;
        build();
        Bridge.query(() -> {
            PivotEdit pe = Bridge.editor().pivotEdit;
            pe.listener = s -> Ui.ui(() -> setInfo(s));
            return pe.info();
        }, this::setInfo);
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    private GradientDrawable glass(float r) {
        return Ui.rounded(0xE6202328, dp(r), 0x553A4048, dp(1));
    }

    private void build() {
        LinearLayout top = Ui.row(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(4), dp(6), dp(4));
        top.setBackground(glass(14));
        top.setClickable(true);
        top.addView(Ui.button(a, R.drawable.ic_s2_check, Tr.t("ter_done", "Done"), true, v -> finish(true)));
        TextView t = Ui.text(a, d3 ? Tr.t("pv_title_3d", "Anchor") : Tr.t("pv_title_2d", "Pivot"), 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        t.setPadding(dp(10), 0, dp(6), 0);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(t, Ui.weight(1));
        top.addView(Ui.iconButton(a, R.drawable.ic_s2_center_focus, Tr.t("pv_presets", "Sides and center"), v -> presets()));
        boundsBtn = Ui.iconButton(a, R.drawable.ic_s2_crop_free, Tr.t("pv_bounds", "Keep inside the element"), v -> {
            bounds = !bounds;
            final boolean on = bounds;
            paintBounds();
            Bridge.gl(() -> Bridge.editor().pivotEdit.setBounds(on));
            Ui.toast(a, on ? Tr.t("pv_bounds_on", "The point stays inside the element") : Tr.t("pv_bounds_off", "The point can go outside the element"));
        });
        top.addView(boundsBtn);
        top.addView(Ui.iconButton(a, R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), v -> finish(false)));
        Ui.fitIconRow(a, top, 150);
        paintBounds();
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        tlp.setMargins(dp(8), dp(6), dp(8), 0);
        addView(top, tlp);

        info = Ui.text(a, "", 12.5f, Ui.TEXT_2);
        info.setPadding(dp(12), dp(8), dp(12), dp(8));
        info.setBackground(glass(12));
        info.setGravity(Gravity.CENTER);
        info.setMaxWidth(dp(520));   // landscape: two short lines in the middle instead of one across the screen
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ilp.setMargins(dp(8), 0, dp(8), dp(10));
        addView(info, ilp);
    }

    private void paintBounds() {
        boundsBtn.setImageTintList(ColorStateList.valueOf(bounds ? Ui.ACCENT : Ui.TEXT_3));
        boundsBtn.setBackground(bounds ? Ui.rounded(Ui.ACCENT_SOFT, dp(8)) : null);
    }

    private void setInfo(String values) {
        String hint = d3 ? Tr.t("pv_hint_3d", "Drag an arrow to move the anchor. One finger elsewhere turns the view, two fingers move and zoom.")
                : Tr.t("pv_hint_2d", "Drag with one finger to move the pivot. Two fingers move and zoom the view.");
        info.setText(hint + "\n" + values);
    }

    /** Left / center / right (and bottom / top, back / front) for each axis; an axis left unset keeps its value. */
    private void presets() {
        Bridge.query(() -> {
            PivotEdit pe = Bridge.editor().pivotEdit;
            return new int[]{pe.presetOf(0), pe.presetOf(1), pe.presetOf(2)};
        }, cur -> {
            LinearLayout col = Ui.column(a);
            col.setPadding(dp(18), dp(6), dp(18), dp(4));
            int axes = d3 ? 3 : 2;
            String[][] labels = {
                    {Tr.t("pv_left", "Left"), Tr.t("pv_center", "Center"), Tr.t("pv_right", "Right")},
                    {Tr.t("pv_bottom", "Bottom"), Tr.t("pv_center", "Center"), Tr.t("pv_top", "Top")},
                    {Tr.t("pv_back", "Back"), Tr.t("pv_center", "Center"), Tr.t("pv_front", "Front")}};
            int[] colors = {0xFFF24D4D, 0xFF59D959, 0xFF4D8CFF};
            final RadioGroup[] groups = new RadioGroup[axes];
            for (int ax = 0; ax < axes; ax++) {
                TextView l = Ui.text(a, (ax == 0 ? "X" : (ax == 1 ? "Y" : "Z")) + "  ·  " + (ax == 0 ? Tr.t("pv_axis_x", "Left - right") : (ax == 1 ? Tr.t("pv_axis_y", "Bottom - top") : Tr.t("pv_axis_z", "Back - front"))), 13, colors[ax]);
                l.setTypeface(Typeface.DEFAULT_BOLD);
                l.setPadding(0, dp(ax == 0 ? 4 : 12), 0, dp(2));
                col.addView(l, Ui.matchWrap());
                RadioGroup g = new RadioGroup(a);
                g.setOrientation(RadioGroup.HORIZONTAL);
                for (int k = 0; k < 3; k++) {
                    RadioButton rb = new RadioButton(a);
                    rb.setId(View.generateViewId());
                    rb.setTag(k);
                    rb.setText(labels[ax][k]);
                    rb.setTextColor(Ui.TEXT);
                    rb.setTextSize(13.5f);
                    rb.setButtonTintList(ColorStateList.valueOf(colors[ax]));
                    rb.setPadding(0, 0, dp(6), 0);
                    g.addView(rb, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    if (cur[ax] == k) rb.setChecked(true);
                }
                groups[ax] = g;
                col.addView(g, Ui.matchWrap());
            }
            TextView note = Ui.text(a, Tr.t("pv_presets_note", "An axis you leave unchosen keeps its value."), 11.5f, Ui.TEXT_3);
            note.setPadding(0, dp(10), 0, 0);
            col.addView(note, Ui.matchWrap());
            ScrollView sv = new ScrollView(a);
            sv.addView(col, Ui.matchWrap());
            new AlertDialog.Builder(a)
                    .setTitle(Tr.t("pv_presets", "Sides and center"))
                    .setView(sv)
                    .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                    .setPositiveButton(Tr.t("ok", "OK"), (d, w) -> {
                        final int[] pick = new int[axes];
                        for (int ax = 0; ax < axes; ax++) {
                            int id = groups[ax].getCheckedRadioButtonId();
                            View rb = id == -1 ? null : groups[ax].findViewById(id);
                            pick[ax] = rb == null ? -1 : (Integer) rb.getTag();
                        }
                        Bridge.gl(() -> {
                            PivotEdit pe = Bridge.editor().pivotEdit;
                            for (int ax = 0; ax < pick.length; ax++) if (pick[ax] >= 0) pe.preset(ax, pick[ax]);
                        });
                    })
                    .show();
        });
    }

    /** Done (apply, one undo step) or Cancel (nothing changes). */
    public void finish(boolean save) {
        Bridge.query(() -> {
            PivotEdit pe = Bridge.editor().pivotEdit;
            if (save) return pe.commit();
            pe.stop();
            return null;
        }, err -> {
            if (current == this) current = null;
            shell.exitOverlay(this);
            if (err != null) Ui.toast(a, err);
            if (onDone != null) onDone.run();
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return false;
    }
}
