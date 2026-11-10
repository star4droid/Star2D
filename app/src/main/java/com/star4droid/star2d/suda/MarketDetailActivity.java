package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
import java.util.Locale;

import okhttp3.Call;

/** One Market entry: info, Download (project) or Play (game only), and Edit for the owner. */
public class MarketDetailActivity extends Activity {
    private JsonObject m;
    private long id;
    private boolean game, mine;
    private Call[] call = new Call[1];
    private AlertDialog progress;
    private ProgressBar bar;
    private TextView progressText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        try { m = JsonParser.parseString(getIntent().getStringExtra("json")).getAsJsonObject(); } catch (Exception e) { finish(); return; }
        id = (long) Suda3D.num(m, "id", 0);
        game = "game".equals(Suda3D.str(m, "kind"));
        mine = m.has("mine") && m.get("mine").getAsBoolean();
        build();
        refreshFromServer();
    }

    private void build() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Suda3D.str(m, "name")), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 28));

        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(this, 16)));
        icon.setClipToOutline(true);
        icon.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        int isz = Ui.dp(this, 92);
        if (m.has("has_icon") && m.get("has_icon").getAsBoolean())
            Glide.with(this).load(MarketApi.iconUrl(this, id, Suda3D.str(m, "updated_at"))).centerCrop().into(icon);
        else { icon.setImageResource(R.drawable.ic_s2_gamepad); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE); icon.setPadding(isz / 4, isz / 4, isz / 4, isz / 4); icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_3)); }
        head.addView(icon, new LinearLayout.LayoutParams(isz, isz));
        LinearLayout hs = Ui.column(this);
        hs.setPadding(Ui.dp(this, 14), 0, 0, 0);
        TextView name = Ui.title(this, Suda3D.str(m, "name"));
        name.setTextSize(20);
        hs.addView(name, Ui.matchWrap());
        hs.addView(Ui.text(this, (game ? Tr.t("mk_game_only", "Game only (play only)") : Tr.t("mk_full_project", "Full project")), 13, SudaUi.ORANGE), Ui.matchWrap());
        hs.addView(Ui.text(this, meta(), 12.5f, Ui.TEXT_3), Ui.matchWrap());
        head.addView(hs, Ui.weight(1));
        col.addView(head, Ui.matchWrap());

        String d = Suda3D.str(m, "description");
        if (!d.isEmpty()) {
            col.addView(Ui.sectionLabel(this, Tr.t("mk_description", "Description")));
            TextView dt = Ui.text(this, d, 14, Ui.TEXT_2);
            dt.setTextIsSelectable(true);
            dt.setLineSpacing(0, 1.15f);
            col.addView(dt, Ui.matchWrap());
        }
        if (game) {
            TextView note = Ui.text(this, Tr.t("mk_game_note", "This is a game only: it opens and plays directly. Its code and scenes are not included, so it cannot be opened in the editor."), 12.5f, Ui.TEXT_3);
            col.addView(note, Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 0));
        }
        LinearLayout actions = Ui.column(this);
        actions.addView(Ui.button(this, game ? R.drawable.ic_s2_play : R.drawable.ic_s2_download,
                game ? Tr.t("mk_play", "Play") : Tr.t("mk_download", "Download to my projects"), true, v -> start()), Ui.margins(fullW(), this, 0, 0, 0, 0));
        if (mine) {
            actions.addView(Ui.button(this, R.drawable.ic_s2_edit, Tr.t("mk_edit", "Edit / update"), false, v -> {
                Intent i = new Intent(this, MarketEditActivity.class);
                i.putExtra("json", m.toString());
                startActivity(i);
            }), Ui.margins(fullW(), this, 0, 10, 0, 0));
        }
        col.addView(actions, Ui.margins(Ui.matchWrap(), this, 0, 20, 0, 0));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
    }

    private static LinearLayout.LayoutParams fullW() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private String meta() {
        String v = Suda3D.str(m, "engine_version");
        return "↓ " + (int) Suda3D.num(m, "downloads", 0) + "  •  " + MarketActivity.sizeText(Suda3D.num(m, "size_bytes", 0))
                + (v.isEmpty() ? "" : "  •  " + Tr.t("mk_made_with", "Star2D") + " " + v);
    }

    /** The list row may be old: fetch the current data (version, description) quietly. */
    private void refreshFromServer() {
        Suda3D.IO.execute(() -> {
            try {
                JsonObject fresh = MarketApi.get(this, id);
                runOnUiThread(() -> { if (!isFinishing()) { m = fresh; mine = fresh.has("mine") && fresh.get("mine").getAsBoolean(); } });
            } catch (Exception ignored) {}
        });
    }

    private void start() {
        int code = (int) Suda3D.num(m, "engine_version_code", 0);
        if (code != 0 && code != BuildConfig.VERSION_CODE) {
            String msg = String.format(Locale.US, Tr.t("mk_version_warn", "This %1$s was made with Star2D %2$s, you have %3$s. It may not work correctly. Continue?"),
                    game ? Tr.t("mk_game", "Game").toLowerCase(Locale.ROOT) : Tr.t("mk_project", "Project").toLowerCase(Locale.ROOT),
                    Suda3D.str(m, "engine_version"), BuildConfig.VERSION_NAME);
            new AlertDialog.Builder(this).setTitle(Tr.t("mk_other_version", "Different version")).setMessage(msg)
                    .setPositiveButton(Tr.t("mk_continue", "Continue"), (d, w) -> run())
                    .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
        } else run();
    }

    private void run() {
        File zip = new File(getCacheDir(), "market_dl/" + id + ".zip");
        showProgress(game ? Tr.t("mk_preparing", "Preparing the game…") : Tr.t("mk_downloading", "Downloading…"));
        Suda3D.IO.execute(() -> {
            try {
                MarketApi.download(this, id, zip, (done, total) -> runOnUiThread(() -> progress(done, total)), call);
                if (game) {
                    File base = new File(MarketPack.gamesDir(this), String.valueOf(id));
                    MarketPack.deleteRecursive(base); // always the newest upload
                    File dir = MarketPack.unpack(zip, base);
                    zip.delete();
                    runOnUiThread(() -> { hideProgress(); play(dir); });
                } else {
                    File tmp = new File(getCacheDir(), "market_unpack/" + id);
                    MarketPack.deleteRecursive(tmp);
                    File dir = MarketPack.unpack(zip, tmp);
                    File target = MarketPack.freeDir(MarketPack.projectsDir(this), dir.getName());
                    MarketPack.projectsDir(this).mkdirs();
                    if (!dir.renameTo(target)) throw new java.io.IOException("move failed");
                    MarketPack.deleteRecursive(tmp);
                    zip.delete();
                    runOnUiThread(() -> {
                        hideProgress();
                        new AlertDialog.Builder(this).setTitle(Tr.t("mk_saved", "Downloaded"))
                                .setMessage(String.format(Locale.US, Tr.t("mk_saved_msg", "Saved to your projects as \"%s\"."), target.getName()))
                                .setPositiveButton(Tr.t("ok", "OK"), (d, w) -> finish()).show();
                    });
                }
            } catch (Exception e) {
                zip.delete();
                runOnUiThread(() -> { hideProgress(); if (!"Canceled".equals(e.getMessage()) && !"Socket closed".equals(e.getMessage())) Ui.toast(this, Suda3D.message(e)); });
            }
        });
    }

    private void play(File dir) {
        if (!MarketPack.isGamePackage(dir)) { Ui.toast(this, Tr.t("mk_bad_game", "This package is not a playable game.")); return; }
        String scene = "scene1", or = "landscape";
        try {
            JsonObject o = JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(new File(dir, MarketPack.GAME_MARK).toPath()), "UTF-8")).getAsJsonObject();
            scene = Suda3D.str(o, "scene").isEmpty() ? scene : Suda3D.str(o, "scene");
            or = Suda3D.str(o, "or");
        } catch (Exception ignored) {}
        if (!MarketPack.hasBuild(dir)) { Ui.toast(this, Tr.t("mk_bad_game", "This package is not a playable game.")); return; }
        Class<?> c = "portrait".equals(or) ? com.star4droid.star2d.PortraitPlayer.class : com.star4droid.star2d.LandscapePlayer.class;
        startActivity(new Intent(this, c).putExtra("path", dir.getAbsolutePath()).putExtra("scene", scene));
    }

    // ------------------------------------------------------------ progress dialog

    private void showProgress(String title) {
        LinearLayout l = Ui.column(this);
        l.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 8));
        progressText = Ui.text(this, "", 13, Ui.TEXT_2);
        l.addView(progressText, Ui.matchWrap());
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        l.addView(bar, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
        progress = new AlertDialog.Builder(this).setTitle(title).setView(l).setCancelable(false)
                .setNegativeButton(Tr.t("cancel", "Cancel"), (d, w) -> { if (call[0] != null) call[0].cancel(); }).show();
    }

    private void progress(long done, long total) {
        if (bar == null) return;
        if (total > 0) { bar.setIndeterminate(false); bar.setMax(1000); bar.setProgress((int) (done * 1000 / total)); }
        progressText.setText(MarketActivity.sizeText(done) + (total > 0 ? " / " + MarketActivity.sizeText(total) : ""));
    }

    private void hideProgress() { if (progress != null) { try { progress.dismiss(); } catch (Exception ignored) {} progress = null; } }

    @Override protected void onDestroy() { hideProgress(); super.onDestroy(); }
}
