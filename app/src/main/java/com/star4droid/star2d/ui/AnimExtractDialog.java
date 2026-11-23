package com.star4droid.star2d.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.utils.Array;
import com.star4droid.template.d3.AnimFile;
import com.star4droid.template.d3.Assets3D;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Files panel, long press on a .glb / .gltf: "Extract animations". Lists the model's clips (scrolling checklist, one,
 * several or all) and saves them as .animf files next to the model. An element plays them through Animation Model,
 * even after the model is deleted.
 */
public final class AnimExtractDialog {
    private AnimExtractDialog() {}

    public static boolean accepts(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return f.isFile() && (n.endsWith(".glb") || n.endsWith(".gltf"));
    }

    /** The value Assets3D understands for a project file: relative to models/ when inside it, else to the project. */
    static String valueOf(String project, File f) {
        String abs = f.getAbsolutePath(), root = new File(project).getAbsolutePath();
        if (!abs.startsWith(root + "/")) return abs;
        String rel = abs.substring(root.length());
        return rel.startsWith("/models/") ? rel.substring(7) : rel;
    }

    /** Opens the dialog; {@code done} runs (on the UI thread) after files were written. */
    public static void show(Context c, String project, File model, Runnable done) {
        if (!Bridge.ready()) {
            Ui.toast(c, Tr.t("anim_x_need_editor", "Open a scene first"));
            return;
        }
        final String path = valueOf(project, model);
        Bridge.query(() -> {
            Assets3D.reload(path);
            return Assets3D.animations(path);
        }, names -> {
            if (names == null || names.size == 0) {
                String err = Assets3D.error(path);
                Ui.toast(c, err != null ? err : Tr.t("anim_none_in_model", "This model has no animations"));
                return;
            }
            open(c, model, path, names, done);
        });
    }

    private static void open(Context c, File model, String path, Array<String> names, Runnable done) {
        LinearLayout root = Ui.column(c);
        root.setPadding(Ui.dp(c, 18), Ui.dp(c, 4), Ui.dp(c, 18), 0);

        TextView sub = Ui.text(c, model.getName() + "  ·  " + names.size + " " + Tr.t("anim_x_clips", "clips"), 12.5f, Ui.TEXT_3);
        root.addView(sub, Ui.matchWrap());

        final ArrayList<CheckBox> boxes = new ArrayList<>();
        CheckBox all = new CheckBox(c);
        all.setText(Tr.t("anim_x_all", "Select all"));
        all.setTextColor(Ui.TEXT);
        all.setTextSize(14);
        all.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
        root.addView(all, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 2));

        LinearLayout list = Ui.column(c);
        for (int i = 0; i < names.size; i++) {
            CheckBox b = new CheckBox(c);
            b.setText(names.get(i));
            b.setTextColor(Ui.TEXT);
            b.setTextSize(14);
            b.setSingleLine(true);
            b.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
            b.setMinHeight(Ui.dp(c, 44));
            b.setChecked(names.size == 1);
            boxes.add(b);
            list.addView(b, Ui.matchWrap());
        }
        list.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, 1));
        list.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
        root.addView(list, Ui.matchWrap());
        CheckBox one = new CheckBox(c);
        one.setText(Tr.t("anim_x_one_file", "Save the chosen clips in one file"));
        one.setTextColor(Ui.TEXT_2);
        one.setTextSize(13);
        one.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
        root.addView(one, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));

        TextView hint = Ui.text(c, Tr.t("anim_x_hint", "Saved as .animf next to the model. Choose it with the button beside Animation; it keeps working if the model is deleted."), 11.5f, Ui.TEXT_3);
        hint.setGravity(Gravity.START);
        root.addView(hint, Ui.margins(Ui.matchWrap(), c, 0, 2, 0, 6));

        final boolean[] syncing = {false};
        all.setOnCheckedChangeListener((v, on) -> {
            if (syncing[0]) return;
            for (CheckBox b : boxes) b.setChecked(on);
        });
        for (CheckBox b : boxes) b.setOnCheckedChangeListener((v, on) -> {
            int n = 0;
            for (CheckBox x : boxes) if (x.isChecked()) n++;
            syncing[0] = true;
            all.setChecked(n == boxes.size());
            syncing[0] = false;
            one.setEnabled(n > 1);
        });
        all.setChecked(names.size == 1);
        one.setEnabled(false);

        // one scroll for the whole content (clips and options): it fits short landscape screens, the buttons stay below
        ScrollView sv = new ScrollView(c);
        sv.addView(root, Ui.matchWrap());
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(Tr.t("anim_x_title", "Extract animations"))
                .setView(sv)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .setPositiveButton(Tr.t("anim_x_save", "Save"), null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final ArrayList<String> chosen = new ArrayList<>();
            for (CheckBox b : boxes) if (b.isChecked()) chosen.add(b.getText().toString());
            if (chosen.isEmpty()) {
                Ui.toast(c, Tr.t("anim_x_pick", "Choose at least one clip"));
                return;
            }
            final boolean single = one.isChecked() && chosen.size() > 1;
            d.dismiss();
            Bridge.query(() -> save(model, path, chosen, single), r -> {
                Ui.toast(c, r);
                if (done != null) done.run();
            });
        });
    }

    /** Writes the files (rendering thread: the model is read there); returns the message to show. */
    private static String save(File model, String path, ArrayList<String> chosen, boolean single) {
        try {
            com.badlogic.gdx.graphics.g3d.Model m = Assets3D.modelOf(path);
            if (m == null) return String.valueOf(Assets3D.error(path));
            File dir = model.getParentFile();
            String base = model.getName().substring(0, model.getName().lastIndexOf('.'));
            Array<Animation> picked = new Array<>();
            for (String n : chosen) for (Animation a : m.animations) if (n.equals(a.id)) { picked.add(a); break; }
            ArrayList<String> written = new ArrayList<>();
            if (single) {
                File out = free(dir, base + "_anims");
                AnimFile.write(out, picked);
                written.add(out.getName());
            } else {
                for (Animation a : picked) {
                    Array<Animation> one = new Array<>();
                    one.add(a);
                    File out = free(dir, picked.size == 1 && clean(a.id).isEmpty() ? base : base + "_" + clean(a.id));
                    AnimFile.write(out, one);
                    written.add(out.getName());
                }
            }
            return Tr.t("anim_x_saved", "Saved") + ": " + android.text.TextUtils.join(", ", written);
        } catch (Throwable t) {
            return "Animations: " + t.getMessage();
        }
    }

    /** A clip name usable in a file name. */
    static String clean(String s) {
        String r = s == null ? "" : s.replaceAll("[^A-Za-z0-9_\\-\\u0600-\\u06FF]+", "_").replaceAll("_+", "_");
        if (r.startsWith("_")) r = r.substring(1);
        if (r.endsWith("_")) r = r.substring(0, r.length() - 1);
        return r.length() > 40 ? r.substring(0, 40) : r;
    }

    /** NAME.animf, or NAME_2.animf... when taken. */
    static File free(File dir, String name) {
        File f = new File(dir, name + ".animf");
        int i = 2;
        while (f.exists()) f = new File(dir, name + "_" + (i++) + ".animf");
        return f;
    }
}
