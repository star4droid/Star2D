package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.evo.BuildConfig;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;

/** Upload a new Market entry, or edit / update / delete one of mine. Everything scrolls, so any screen size works. */
public class MarketEditActivity extends Activity {
    private static final int REQ_ICON = 71;

    private JsonObject m;           // null = new upload
    private long id;
    private File project;           // chosen local project (new upload)
    private File iconFile;          // a newly picked icon
    private boolean game;
    private int limit = 2, used = 0, maxMb = 60;

    private EditText name, desc;
    private ImageView icon;
    private TextView projectLabel, quota, kindProject, kindGame, kindNote;
    private AlertDialog dialog;
    private ProgressBar bar;
    private TextView barText;
    private final Call[] call = new Call[1];
    private final boolean[] cancel = new boolean[1];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        try { String j = getIntent().getStringExtra("json"); if (j != null) m = JsonParser.parseString(j).getAsJsonObject(); } catch (Exception ignored) {}
        if (m != null) { id = (long) Suda3D.num(m, "id", 0); game = "game".equals(Suda3D.str(m, "kind")); }
        build();
        Suda3D.IO.execute(() -> {
            try {
                JsonObject o = MarketApi.mine(this);
                JsonObject q = Suda3D.obj(o, "quota");
                runOnUiThread(() -> {
                    limit = (int) Suda3D.num(q, "limit", 2); used = (int) Suda3D.num(q, "used_today", 0); maxMb = (int) Suda3D.num(o, "max_mb", 60);
                    quotaText();
                });
            } catch (Exception ignored) {}
        });
    }

    private void build() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, m == null ? Tr.t("mk_upload_title", "Upload to Market") : Tr.t("mk_edit_title", "Edit project")), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout col = Ui.column(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, Ui.dp(this, 32));

        // icon + name
        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(this, 16), Ui.BORDER, 1));
        icon.setClipToOutline(true);
        icon.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        icon.setOnClickListener(v -> pickIcon());
        int isz = Ui.dp(this, 84);
        top.addView(icon, new LinearLayout.LayoutParams(isz, isz));
        LinearLayout nc = Ui.column(this);
        nc.setPadding(Ui.dp(this, 12), 0, 0, 0);
        nc.addView(Ui.text(this, Tr.t("mk_name", "Name"), 12, Ui.TEXT_3));
        name = Ui.input(this, m == null ? "" : Suda3D.str(m, "name"), Tr.t("mk_name_hint", "Project name"));
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        nc.addView(name, Ui.matchWrap());
        TextView tap = Ui.text(this, Tr.t("mk_icon_tap", "Tap the picture to choose an icon"), 11.5f, Ui.TEXT_3);
        nc.addView(tap, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 0));
        top.addView(nc, Ui.weight(1));
        col.addView(top, Ui.matchWrap());
        showIcon();

        col.addView(Ui.sectionLabel(this, Tr.t("mk_description", "Description")));
        desc = Ui.input(this, m == null ? "" : Suda3D.str(m, "description"), Tr.t("mk_desc_hint", "What is it? How to play / use it?"));
        desc.setSingleLine(false);
        desc.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        desc.setMinLines(4);
        desc.setMaxLines(10);
        desc.setGravity(Gravity.TOP | Gravity.START);
        desc.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        col.addView(desc, Ui.matchWrap());

        // type
        col.addView(Ui.sectionLabel(this, Tr.t("mk_type", "Type")));
        LinearLayout kinds = Ui.row(this);
        kindProject = chip(Tr.t("mk_full_project", "Full project"), false);
        kindGame = chip(Tr.t("mk_game_only", "Game only (play only)"), true);
        kinds.addView(kindProject, new LinearLayout.LayoutParams(0, -2, 1));
        kinds.addView(kindGame, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1), this, 8, 0, 0, 0));
        col.addView(kinds, Ui.matchWrap());
        kindNote = Ui.text(this, "", 12, Ui.TEXT_3);
        col.addView(kindNote, Ui.margins(Ui.matchWrap(), this, 2, 6, 2, 0));
        paintKinds();
        if (m != null) { // the type of an existing entry follows its package; changing it needs a new project upload
            kindProject.setEnabled(false); kindGame.setEnabled(false); kindProject.setAlpha(game ? 0.45f : 1f); kindGame.setAlpha(game ? 1f : 0.45f);
        }

        // project file
        col.addView(Ui.sectionLabel(this, m == null ? Tr.t("mk_project_to_upload", "Project to upload") : Tr.t("mk_update_project", "Update the project file")));
        if (m == null) {
            projectLabel = Ui.text(this, Tr.t("mk_choose_project", "Choose a project"), 14, Ui.TEXT);
            projectLabel.setPadding(Ui.dp(this, 12), Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
            projectLabel.setBackground(Ui.rounded(0xFF121417, Ui.dp(this, 8), Ui.BORDER, 1));
            projectLabel.setSingleLine(true);
            projectLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
            projectLabel.setOnClickListener(v -> chooseProject(f -> { project = f; projectLabel.setText(f.getName()); if (name.getText().toString().trim().isEmpty()) name.setText(f.getName()); }));
            col.addView(projectLabel, Ui.matchWrap());
        } else {
            col.addView(Ui.text(this, Tr.t("mk_update_hint", "Pick one of your projects: it is compressed and uploaded to replace the files here. The compressed file is deleted when the upload ends."), 12.5f, Ui.TEXT_3), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
            col.addView(Ui.button(this, R.drawable.ic_s2_upload, Tr.t("mk_update_btn", "Update project"), false, v -> chooseProject(this::updateFile)), new LinearLayout.LayoutParams(-1, -2));
        }

        quota = Ui.text(this, "", 12, Ui.TEXT_3);
        col.addView(quota, Ui.margins(Ui.matchWrap(), this, 2, 14, 2, 0));
        col.addView(Ui.text(this, String.format(Locale.US, Tr.t("mk_version_saved", "The engine version (%s) is saved with the upload."), BuildConfig.VERSION_NAME), 12, Ui.TEXT_3), Ui.margins(Ui.matchWrap(), this, 2, 4, 2, 0));

        col.addView(Ui.button(this, R.drawable.ic_s2_check, m == null ? Tr.t("mk_upload_btn", "Upload") : Tr.t("mk_save", "Save changes"), true, v -> { if (m == null) uploadNew(); else saveInfo(); }),
                Ui.margins(new LinearLayout.LayoutParams(-1, -2), this, 0, 20, 0, 0));
        if (m != null) {
            LinearLayout del = Ui.button(this, R.drawable.ic_s2_delete, Tr.t("mk_delete", "Delete from Market"), false, v -> confirmDelete());
            ((TextView) del.getChildAt(1)).setTextColor(Ui.DANGER);
            col.addView(del, Ui.margins(new LinearLayout.LayoutParams(-1, -2), this, 0, 10, 0, 0));
        }
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        quotaText();
    }

    private TextView chip(String label, boolean isGame) {
        TextView c = Ui.text(this, label, 13, Ui.TEXT);
        c.setGravity(Gravity.CENTER);
        c.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        c.setOnClickListener(v -> { game = isGame; paintKinds(); });
        return c;
    }

    private void paintKinds() {
        kindProject.setBackground(!game ? Ui.rounded(Ui.ACCENT, Ui.dp(this, 9)) : Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 9), Ui.BORDER, 1));
        kindGame.setBackground(game ? Ui.rounded(Ui.ACCENT, Ui.dp(this, 9)) : Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 9), Ui.BORDER, 1));
        kindNote.setText(game
                ? Tr.t("mk_kind_game_note", "Only what the game needs to run is uploaded (images, sounds, built code). No blocks, events or scripts. People can play it directly but cannot open it in the editor. Press Play once in the editor before uploading, so the game is built.")
                : Tr.t("mk_kind_project_note", "The whole project is uploaded. People can download it and edit it."));
    }

    private void quotaText() {
        if (quota == null) return;
        quota.setText(m == null ? String.format(Locale.US, Tr.t("mk_quota", "Uploads today: %1$d of %2$d  •  max size %3$d MB"), used, limit, maxMb)
                : String.format(Locale.US, Tr.t("mk_maxsize", "Max size %d MB"), maxMb));
    }

    // ------------------------------------------------------------ icon

    private void pickIcon() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(i, REQ_ICON); } catch (Exception e) { Ui.toast(this, e.getMessage()); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ICON || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            iconFile = squareIcon(data.getData());
            showIcon();
        } catch (Exception e) { Ui.toast(this, Suda3D.message(e)); }
    }

    /** Center-cropped 512x512 PNG (or JPEG when it would be heavy) in the cache. */
    private File squareIcon(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        int s = 1;
        while (o.outWidth / (s * 2) >= 512 && o.outHeight / (s * 2) >= 512) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        Bitmap bm;
        try (InputStream in = getContentResolver().openInputStream(uri)) { bm = BitmapFactory.decodeStream(in, null, o); }
        if (bm == null) throw new java.io.IOException(Tr.t("mk_bad_image", "This image can't be read."));
        int side = Math.min(bm.getWidth(), bm.getHeight());
        Bitmap sq = Bitmap.createBitmap(bm, (bm.getWidth() - side) / 2, (bm.getHeight() - side) / 2, side, side);
        Bitmap out = Bitmap.createScaledBitmap(sq, Math.min(512, side), Math.min(512, side), true);
        File f = new File(getCacheDir(), "market_icon.png");
        try (FileOutputStream fo = new FileOutputStream(f)) { out.compress(Bitmap.CompressFormat.PNG, 100, fo); }
        if (f.length() > 900 * 1024) {
            f = new File(getCacheDir(), "market_icon.jpg");
            try (FileOutputStream fo = new FileOutputStream(f)) { out.compress(Bitmap.CompressFormat.JPEG, 88, fo); }
        }
        return f;
    }

    private void showIcon() {
        if (iconFile != null) { icon.setScaleType(ImageView.ScaleType.CENTER_CROP); icon.setImageTintList(null); icon.setPadding(0, 0, 0, 0); Glide.with(this).load(iconFile).skipMemoryCache(true).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).centerCrop().into(icon); return; }
        if (m != null && m.has("has_icon") && m.get("has_icon").getAsBoolean()) {
            Glide.with(this).load(MarketApi.iconUrl(this, id, Suda3D.str(m, "updated_at"))).centerCrop().into(icon);
            return;
        }
        int isz = Ui.dp(this, 84);
        icon.setImageResource(R.drawable.ic_s2_image);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(isz / 4, isz / 4, isz / 4, isz / 4);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_3));
    }

    // ------------------------------------------------------------ actions

    private interface Pick { void on(File f); }

    private void chooseProject(Pick onPick) {
        List<File> l = MarketPack.localProjects(this);
        if (l.isEmpty()) { Ui.toast(this, Tr.t("mk_no_projects", "You have no projects yet.")); return; }
        String[] names = new String[l.size()];
        for (int i = 0; i < names.length; i++) names[i] = l.get(i).getName();
        new AlertDialog.Builder(this).setTitle(Tr.t("mk_choose_project", "Choose a project")).setItems(names, (d, w) -> onPick.on(l.get(w)))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
    }

    private String checkFields() {
        if (name.getText().toString().trim().length() < 2) return Tr.t("mk_err_name", "Enter a name (at least 2 characters).");
        return null;
    }

    private void uploadNew() {
        String err = checkFields();
        if (err != null) { Ui.toast(this, err); return; }
        if (project == null) { Ui.toast(this, Tr.t("mk_choose_project", "Choose a project")); return; }
        if (used >= limit) { Ui.toast(this, String.format(Locale.US, Tr.t("mk_limit", "You can upload %d project(s) per day. Try again tomorrow."), limit)); return; }
        if (game && !MarketPack.hasBuild(project)) { Ui.toast(this, Tr.t("mk_not_built", "Press Play once in the editor first, so the game is built.")); return; }
        packAndSend(project, 0);
    }

    private void updateFile(File proj) {
        if (game && !MarketPack.hasBuild(proj)) { Ui.toast(this, Tr.t("mk_not_built", "Press Play once in the editor first, so the game is built.")); return; }
        packAndSend(proj, id);
    }

    private void saveInfo() {
        String err = checkFields();
        if (err != null) { Ui.toast(this, err); return; }
        packAndSend(null, id);
    }

    /** Compress (if a project is given), upload, then delete the compressed file in every case. */
    private void packAndSend(File proj, long targetId) {
        String nm = name.getText().toString().trim(), ds = desc.getText().toString().trim();
        File zip = proj == null ? null : new File(getCacheDir(), "market_up/" + System.currentTimeMillis() + ".zip");
        cancel[0] = false;
        showDialog(proj == null ? Tr.t("mk_saving", "Saving…") : Tr.t("mk_compressing", "Compressing…"));
        Suda3D.IO.execute(() -> {
            try {
                if (zip != null) {
                    MarketPack.pack(proj, zip, game, (f, t) -> runOnUiThread(() -> setBar(f, t, Tr.t("mk_compressing", "Compressing…"))), cancel);
                    if (zip.length() > (long) maxMb * 1024 * 1024) {
                        throw new Exception(String.format(Locale.US, Tr.t("mk_too_big_n", "The project is %1$s after compression; the limit is %2$d MB."), MarketActivity.sizeText(zip.length()), maxMb));
                    }
                    runOnUiThread(() -> setTitle2(Tr.t("mk_uploading", "Uploading…")));
                }
                JsonObject r = MarketApi.save(this, targetId, nm, ds, game ? "game" : "project", zip, iconFile,
                        (d, t) -> runOnUiThread(() -> setBar(d, t, Tr.t("mk_uploading", "Uploading…"))), call);
                MarketActivity.dirty = true;
                runOnUiThread(() -> { hideDialog(); Ui.toast(this, Tr.t("mk_done", "Done")); finish(); });
            } catch (Exception e) {
                String msg = Suda3D.message(e);
                boolean cancelled = cancel[0] || "Canceled".equals(e.getMessage()) || "Socket closed".equals(e.getMessage()) || "cancelled".equals(e.getMessage());
                runOnUiThread(() -> { hideDialog(); if (!cancelled) new AlertDialog.Builder(this).setTitle(Tr.t("mk_failed", "Upload failed")).setMessage(msg).setPositiveButton(Tr.t("ok", "OK"), null).show(); });
            } finally {
                if (zip != null) zip.delete(); // the compressed file is removed as soon as the upload is over
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle(Tr.t("mk_delete", "Delete from Market"))
                .setMessage(Tr.t("mk_delete_q", "Delete this project from the Market for everyone? This cannot be undone."))
                .setPositiveButton(Tr.t("delete", "Delete"), (d, w) -> {
                    showDialog(Tr.t("mk_saving", "Saving…"));
                    Suda3D.IO.execute(() -> {
                        try {
                            MarketApi.delete(this, id);
                            MarketActivity.dirty = true;
                            runOnUiThread(() -> { hideDialog(); finish(); });
                        } catch (Exception e) { runOnUiThread(() -> { hideDialog(); Ui.toast(this, Suda3D.message(e)); }); }
                    });
                }).setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
    }

    // ------------------------------------------------------------ progress

    private void showDialog(String title) {
        LinearLayout l = Ui.column(this);
        l.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 8));
        barText = Ui.text(this, "", 13, Ui.TEXT_2);
        l.addView(barText, Ui.matchWrap());
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        l.addView(bar, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
        dialog = new AlertDialog.Builder(this).setTitle(title).setView(l).setCancelable(false)
                .setNegativeButton(Tr.t("cancel", "Cancel"), (d, w) -> { cancel[0] = true; if (call[0] != null) call[0].cancel(); }).show();
    }

    private void setTitle2(String t) { if (dialog != null) dialog.setTitle(t); if (bar != null) bar.setIndeterminate(true); }

    private void setBar(long done, long total, String what) {
        if (dialog == null || bar == null) return;
        dialog.setTitle(what);
        if (total > 0) { bar.setIndeterminate(false); bar.setMax(1000); bar.setProgress((int) (done * 1000 / total)); }
        barText.setText(what.equals(Tr.t("mk_uploading", "Uploading…")) ? MarketActivity.sizeText(done) + " / " + MarketActivity.sizeText(total) : done + " / " + total);
    }

    private void hideDialog() { if (dialog != null) { try { dialog.dismiss(); } catch (Exception ignored) {} dialog = null; } }

    @Override protected void onDestroy() { hideDialog(); super.onDestroy(); }
}
