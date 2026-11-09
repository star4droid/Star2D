package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.FilePickerDialog;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Quick generation without a workspace (Image → 3D, Text → Image, Text → 3D, Make 3D from an image): inputs, model /
 * version and main options, "More" for the rest, live price (POST /quote), then a progress view that keeps running
 * after the dialog is closed (Running generations), and the outputs with resumable download + save into the project.
 */
public final class QuickGen {
    private QuickGen() {}

    /** What the editor offers to the dialogs (all optional). */
    public interface Env {
        String project();
        /** Let the user pick a picture from the phone, copied to a local file. */
        default void pickFromPhone(Consumer<File> got) {}
        /** A saved model / image was copied into the project: add it to the scene. */
        default void addToScene(File saved) {}
        default boolean canAddToScene(File saved) { return false; }
    }

    public static Env env;

    static String project() { return env == null ? null : env.project(); }

    public static final String[] QUICK = {"image_to_model", "text_to_image", "text_to_model"};

    public static void show(Activity a, String opId, File image) {
        Jobs.init(a);
        Ops.load(a, null);
        new Form(a, opId, image).open();
    }

    // ================================================================ form

    static final class Form {
        final Activity a;
        Ops.Op op;
        File image;
        final Map<String, String> values = new HashMap<>();
        AlertDialog dialog;
        LinearLayout col, params, more;
        TextView price;
        EditText prompt;
        boolean moreOpen;
        final Handler h = new Handler(Looper.getMainLooper());
        Runnable pendingQuote;
        int quoteSeq;

        Form(Activity a, String opId, File image) {
            this.a = a;
            this.op = Ops.get(opId == null ? "image_to_model" : opId);
            this.image = image;
        }

        void open() {
            ScrollView sv = new ScrollView(a);
            col = Ui.column(a);
            col.setPadding(Ui.dp(a, 18), Ui.dp(a, 6), Ui.dp(a, 18), Ui.dp(a, 6));
            sv.addView(col);
            dialog = new AlertDialog.Builder(a)
                    .setTitle(Tr.t("s3d_quick_title", "Generate with SUDA3D"))
                    .setView(sv)
                    .setPositiveButton(Tr.t("s3d_generate", "Generate"), null)
                    .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                    .setNeutralButton(Tr.t("s3d_running", "Running generations"), (d, w) -> showRunning(a))
                    .create();
            dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> generate()));
            build();
            dialog.show();
        }

        void build() {
            col.removeAllViews();
            // operation chips
            LinearLayout chips = Ui.row(a);
            for (String id : QUICK) {
                Ops.Op o = Ops.get(id);
                TextView t = Ui.text(a, o.label(), 13, o == op ? Ui.ACCENT : Ui.TEXT_2);
                t.setGravity(Gravity.CENTER);
                t.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8));
                t.setBackground(o == op ? Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(a, 10)) : null);
                t.setOnClickListener(v -> { op = o; values.clear(); build(); });
                chips.addView(t, Ui.weight(1));
            }
            col.addView(chips, Ui.matchWrap());
            if (op.input == Ops.In.IMAGE) col.addView(imageRow());
            Ops.Param pp = op.param("prompt");
            if (pp != null) {
                prompt = Ui.input(a, values.containsKey("prompt") ? values.get("prompt") : "", Tr.t("s3d_prompt_hint", "Describe what to generate…"));
                prompt.setSingleLine(false);
                prompt.setMinLines(2);
                prompt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                prompt.addTextChangedListener(watcher(s -> values.put("prompt", s)));
                col.addView(prompt, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 4));
            } else prompt = null;
            params = Ui.column(a);
            more = Ui.column(a);
            ParamForm.fill(a, op, values, params, more, this::requote);
            col.addView(params, Ui.matchWrap());
            TextView mt = Ui.text(a, (moreOpen ? "▾ " : "▸ ") + Tr.t("s3d_more", "More options"), 13.5f, Ui.ACCENT);
            mt.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 6));
            mt.setVisibility(more.getChildCount() > 0 ? View.VISIBLE : View.GONE);
            more.setVisibility(moreOpen ? View.VISIBLE : View.GONE);
            mt.setOnClickListener(v -> {
                moreOpen = !moreOpen;
                more.setVisibility(moreOpen ? View.VISIBLE : View.GONE);
                mt.setText((moreOpen ? "▾ " : "▸ ") + Tr.t("s3d_more", "More options"));
            });
            col.addView(mt);
            col.addView(more, Ui.matchWrap());
            price = Ui.text(a, "", 15, SudaUi.ORANGE);
            price.setTypeface(Typeface.DEFAULT_BOLD);
            price.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 2));
            col.addView(price);
            TextView bal = Ui.text(a, Suda3D.loggedIn(a) ? Tr.t("s3d_balance", "Balance") + ": " + Suda3D.fmtCredits(Suda3D.credits(a)) + " " + Tr.t("s3d_credits", "credits") : "", 12, Ui.TEXT_3);
            col.addView(bal);
            requote();
        }

        View imageRow() {
            LinearLayout r = Ui.row(a);
            r.setPadding(0, Ui.dp(a, 10), 0, 0);
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(a, 8)));
            if (image != null) FilePickerDialog.thumb(iv, image, Ui.dp(a, 64));
            else { iv.setImageResource(R.drawable.ic_s2_image); iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE); }
            r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 64)));
            LinearLayout texts = Ui.column(a);
            texts.setPadding(Ui.dp(a, 10), 0, 0, 0);
            texts.addView(Ui.text(a, image == null ? Tr.t("s3d_pick_image", "Choose a picture") : image.getName(), 14, Ui.TEXT));
            texts.addView(Ui.text(a, Tr.t("s3d_pick_image_hint", "One object, plain background works best"), 11.5f, Ui.TEXT_3));
            r.addView(texts, Ui.weight(1));
            r.setOnClickListener(v -> pickImage(a, f -> { image = f; build(); }));
            return r;
        }

        void requote() {
            if (pendingQuote != null) h.removeCallbacks(pendingQuote);
            price.setText(Tr.t("s3d_price", "Price") + ": …");
            final int seq = ++quoteSeq;
            pendingQuote = () -> {
                JsonObject p = Ops.toJson(op, values);
                Suda3D.IO.execute(() -> {
                    String txt;
                    try {
                        JsonObject q = Suda3D.quote(a, op.id, p);
                        double cr = Suda3D.num(q, "credits", -1);
                        txt = Tr.t("s3d_price", "Price") + ": " + (cr == 0 ? Tr.t("s3d_free", "Free") : Suda3D.fmtCredits(cr) + " " + Tr.t("s3d_credits", "credits")
                                + " ($" + Suda3D.str(q, "usd") + ")");
                    } catch (Exception e) {
                        txt = Tr.t("s3d_price", "Price") + ": " + (op.baseCredits >= 0 ? "≈ " + Suda3D.fmtCredits(op.baseCredits) + " " + Tr.t("s3d_credits", "credits") : "–");
                    }
                    final String t = txt;
                    Ui.ui(() -> { if (seq == quoteSeq) price.setText(t); });
                });
            };
            h.postDelayed(pendingQuote, 400);
        }

        void generate() {
            if (op.input == Ops.In.IMAGE && image == null) { Ui.toast(a, Tr.t("s3d_pick_image", "Choose a picture")); return; }
            if (op.param("prompt") != null && op.param("prompt").type == Ops.T.PROMPT && values.getOrDefault("prompt", "").trim().isEmpty()) {
                if (prompt != null) prompt.setError(Tr.t("s3d_prompt_hint", "Describe what to generate…"));
                return;
            }
            String title = op.label();
            if (!Paywall.gate(a, title)) { dialog.dismiss(); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            price.setText(Tr.t("s3d_starting", "Starting…"));
            JsonObject params = Ops.toJson(op, values);
            final File img = image;
            Suda3D.IO.execute(() -> {
                try {
                    JsonObject inputs = new JsonObject();
                    if (img != null && op.input == Ops.In.IMAGE) inputs.addProperty("image", Suda3D.upload(a, img));
                    JsonObject r = Suda3D.createJob(a, op.id, params, inputs, UUID.randomUUID().toString());
                    Jobs.Job j = Jobs.track(a, r, op.id, title, "quick");
                    Ui.ui(() -> { dialog.dismiss(); showJob(a, j.id); });
                } catch (Exception e) {
                    Ui.ui(() -> {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if (Paywall.handle(a, e, title)) { dialog.dismiss(); return; }
                        price.setText(Suda3D.message(e));
                    });
                }
            });
        }
    }

    static TextWatcher watcher(Consumer<String> on) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { on.accept(s.toString()); }
        };
    }

    /** Choose an input picture: project images, the phone, or a downloaded SUDA3D / workspace output. */
    public static void pickImage(Activity a, Consumer<File> got) {
        String proj = project();
        java.util.ArrayList<String> items = new java.util.ArrayList<>();
        java.util.ArrayList<Runnable> acts = new java.util.ArrayList<>();
        if (proj != null) {
            items.add(Tr.t("s3d_from_project", "From the project images"));
            acts.add(() -> FilePickerDialog.show(a, Tr.t("s3d_pick_image", "Choose a picture"), proj + "/images", new String[]{"png", "jpg", "jpeg", "webp"}, true, (f, rel) -> got.accept(f)));
        }
        if (env != null) {
            items.add(Tr.t("s3d_from_phone", "From the phone"));
            acts.add(() -> env.pickFromPhone(got));
        }
        items.add(Tr.t("s3d_from_outputs", "From generated files"));
        acts.add(() -> pickOutput(a, false, got));
        if (items.size() == 1) { acts.get(0).run(); return; }
        Dialogs.choose(a, Tr.t("s3d_pick_image", "Choose a picture"), items.toArray(new String[0]), i -> acts.get(i).run());
    }

    /** Lists downloaded outputs (quick generations + every workspace); models or images. */
    public static void pickOutput(Activity a, boolean models, Consumer<File> got) {
        java.util.ArrayList<File> files = new java.util.ArrayList<>();
        collect(Store.quickDir(a), models, files, 0);
        collect(Store.workspacesDir(a), models, files, 0);
        java.util.Collections.sort(files, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
        if (files.isEmpty()) { Ui.toast(a, Tr.t("s3d_no_outputs", "No downloaded results yet")); return; }
        String[] names = new String[files.size()];
        for (int i = 0; i < names.length; i++) names[i] = files.get(i).getName() + "  ·  " + Suda3D.fmtSize(files.get(i).length());
        Dialogs.choose(a, models ? Tr.t("s3d_import_ws_model", "Import a model from the workspace") : Tr.t("s3d_import_ws_image", "Import a picture from the workspace"),
                names, i -> got.accept(files.get(i)));
    }

    static void collect(File dir, boolean models, java.util.List<File> out, int depth) {
        File[] l = dir.listFiles();
        if (l == null || depth > 4) return;
        for (File f : l) {
            if (f.isDirectory()) collect(f, models, out, depth + 1);
            else if (!f.getName().endsWith(".part") && (models ? Store.isModel(f.getName()) : Store.isImage(f.getName()))) out.add(f);
        }
    }

    // ================================================================ progress / outputs

    /** Progress of one job (also reopened from Running generations); closing it keeps the job running. */
    public static void showJob(Activity a, String id) {
        Jobs.Job j0 = Jobs.get(id);
        if (j0 == null) return;
        ScrollView sv = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 18), Ui.dp(a, 8), Ui.dp(a, 18), Ui.dp(a, 8));
        sv.addView(col);
        TextView status = Ui.text(a, "", 14, Ui.TEXT_2);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        TextView cost = Ui.text(a, "", 12.5f, Ui.TEXT_3);
        LinearLayout outs = Ui.column(a);
        LinearLayout btns = Ui.row(a);
        col.addView(status);
        col.addView(bar, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 4));
        col.addView(cost);
        col.addView(btns, Ui.margins(Ui.matchWrap(), a, 0, 6, 0, 6));
        col.addView(outs, Ui.matchWrap());
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(j0.title == null || j0.title.isEmpty() ? Tr.t("s3d_generating", "Generating") : j0.title)
                .setView(sv)
                .setPositiveButton(Tr.t("s3d_close_keep", "Close (keeps running)"), null)
                .create();
        final boolean[] outsShown = {false};
        Jobs.Listener l = new Jobs.Listener() {
            @Override public void onJob(Jobs.Job j) {
                if (!j.id.equals(id)) return;
                Ops.Op op = Ops.get(j.op);
                status.setText(SudaUi.statusLabel(j.status) + (j.finished() ? "" : "  " + j.progress + "%")
                        + (j.error.isEmpty() || j.ok() ? "" : "\n" + j.error));
                bar.setProgress(j.ok() ? 100 : j.progress);
                bar.setIndeterminate(!j.finished() && j.progress <= 0);
                bar.setVisibility(j.finished() && !j.ok() ? View.GONE : View.VISIBLE);
                cost.setText(j.credits >= 0 ? Tr.t("s3d_cost", "Cost") + ": " + Suda3D.fmtCredits(j.credits) + " " + Tr.t("s3d_credits", "credits")
                        + (op != null && op.makesModel() && !j.finished() ? " · " + Tr.t("s3d_long_hint", "3D models take a few minutes; checked every minute") : "") : "");
                btns.removeAllViews();
                if (!j.finished()) {
                    btns.addView(Ui.button(a, R.drawable.ic_s2_refresh, Tr.t("s3d_refresh", "Refresh"), false, v -> Jobs.refresh(id)));
                    btns.addView(Ui.button(a, R.drawable.ic_s2_stop, Tr.t("s3d_cancel_job", "Cancel generation"), false, v -> Jobs.cancel(id)),
                            Ui.margins(new LinearLayout.LayoutParams(-2, -2), a, 8, 0, 0, 0));
                }
                if (j.ok() && !outsShown[0]) {
                    outsShown[0] = true;
                    fillOutputs(a, j, outs, Store.ext(a, "suda3d/" + safe(j.id)));
                }
            }
        };
        Jobs.listen(l);
        d.setOnDismissListener(x -> Jobs.unlisten(l));
        l.onJob(j0);
        d.show();
    }

    static String safe(String id) { return id == null ? "x" : id.replaceAll("[^A-Za-z0-9_-]", ""); }

    /** One OutputView per output; Save → project offers "add to the scene". */
    public static void fillOutputs(Activity a, Jobs.Job j, LinearLayout outs, File dir) {
        outs.removeAllViews();
        int i = 0;
        for (JsonElement e : j.outputs) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            File dest = new File(dir, Store.outputName(j.op, j.id, o, i++));
            OutputView ov = new OutputView(a, o, dest, project()).onSaved(saved -> {
                if (env != null && env.canAddToScene(saved))
                    Dialogs.confirm(a, Tr.t("s3d_saved", "Saved"), saved.getName() + "\n" + Tr.t("s3d_add_scene_q", "Add it to the scene now?"),
                            Tr.t("s3d_add_scene", "Add to scene"), () -> env.addToScene(saved));
            });
            outs.addView(ov, Ui.margins(Ui.matchWrap(), a, 0, 6, 0, 0));
        }
        if (i == 0) outs.addView(Ui.text(a, Tr.t("s3d_no_output", "No files in this result"), 13, Ui.TEXT_3));
    }

    /** Running generations: every tracked job; tap to reopen its progress. */
    public static void showRunning(Activity a) {
        Jobs.init(a);
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(Tr.t("s3d_running", "Running generations"))
                .setView(sv)
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .setNeutralButton(Tr.t("s3d_clear_finished", "Clear finished"), (x, w) -> Jobs.clearFinished())
                .create();
        Runnable fill = () -> {
            col.removeAllViews();
            java.util.List<Jobs.Job> all = Jobs.all();
            java.util.Collections.reverse(all);
            if (all.isEmpty()) col.addView(Ui.text(a, Tr.t("s3d_no_running", "Nothing is generating"), 13, Ui.TEXT_3));
            for (Jobs.Job j : all) {
                String sub = SudaUi.statusLabel(j.status) + (j.finished() ? "" : " · " + j.progress + "%")
                        + (j.credits >= 0 ? " · " + Suda3D.fmtCredits(j.credits) + " " + Tr.t("s3d_credits", "credits") : "");
                LinearLayout r = com.star4droid.star2d.ui.PanelHost.listRow(a, j.ok() ? R.drawable.ic_s2_check : j.finished() ? R.drawable.ic_s2_close : R.drawable.ic_s2_progress,
                        j.ok() ? Ui.SUCCESS : j.finished() ? Ui.DANGER : SudaUi.ORANGE, j.title == null ? j.op : j.title, sub);
                r.setOnClickListener(v -> showJob(a, j.id));
                col.addView(r);
            }
        };
        Jobs.Listener l = j -> fill.run();
        Jobs.listen(l);
        d.setOnDismissListener(x -> Jobs.unlisten(l));
        fill.run();
        d.show();
    }

    static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
