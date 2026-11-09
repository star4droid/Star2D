package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;

/**
 * One job output: name, size, expiry, resumable download (progress bar, pause / resume / cancel), then Open (3D viewer
 * or image) and Save (project / phone / Downloads). Used by quick generation, history and workspace nodes.
 */
public class OutputView extends LinearLayout implements Downloads.Listener {
    public interface OnSaved { void on(File saved); }

    private final Activity a;
    private final JsonObject out;
    private final File dest;
    private final String project;
    private final TextView info;
    private final ProgressBar bar;
    private final LinearLayout buttons;
    private OnSaved onSaved;

    public OutputView(Activity a, JsonObject out, File dest, String project) {
        super(a);
        this.a = a;
        this.out = out;
        this.dest = dest;
        this.project = project;
        setOrientation(VERTICAL);
        setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(a, 10)));
        setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
        TextView name = Ui.text(a, dest.getName(), 14, Ui.TEXT);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        addView(name);
        info = Ui.text(a, "", 12, Ui.TEXT_3);
        addView(info);
        bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        addView(bar, Ui.matchWrap());
        buttons = Ui.row(a);
        addView(buttons, Ui.matchWrap());
        update(Downloads.running(dest) ? Downloads.RUNNING : null, Downloads.saved(dest), Downloads.total(dest));
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { Downloads.listen(OutputView.this); }
            @Override public void onViewDetachedFromWindow(View v) { Downloads.unlisten(OutputView.this); }
        });
    }

    public OutputView onSaved(OnSaved s) { this.onSaved = s; return this; }

    long size() { return (long) Suda3D.num(out, "size_bytes", -1); }

    @Override
    public void onProgress(File d, long done, long total, String state) {
        if (!d.getAbsolutePath().equals(dest.getAbsolutePath())) return;
        if (state.startsWith(Downloads.FAILED)) Ui.toast(a, state.substring(Math.min(state.length(), Downloads.FAILED.length() + 1)));
        update(state, done, total);
    }

    private void update(String state, long done, long total) {
        if (total <= 0) total = size();
        String exp = SudaUi.expiry(Suda3D.str(out, "expires_at"));
        boolean have = dest.exists();
        boolean running = Downloads.RUNNING.equals(state) || Downloads.running(dest);
        String sz = Suda3D.fmtSize(total > 0 ? total : (have ? dest.length() : -1));
        info.setText(have ? sz + " · " + Tr.t("s3d_downloaded", "Downloaded")
                : (done > 0 ? Suda3D.fmtSize(done) + " / " : "") + sz + (exp.isEmpty() ? "" : " · " + exp));
        bar.setVisibility(!have && (running || done > 0) ? VISIBLE : GONE);
        bar.setProgress(total > 0 ? (int) (done * 1000 / total) : 0);
        bar.setIndeterminate(running && total <= 0);
        buttons.removeAllViews();
        if (have) {
            add(R.drawable.ic_s2_view3d, Tr.t("s3d_open", "Open"), v -> open());
            add(R.drawable.ic_s2_save, Tr.t("s3d_save", "Save"), v -> saveMenu(a, dest, project, onSaved));
        } else if (running) {
            add(R.drawable.ic_s2_pause, Tr.t("s3d_pause", "Pause"), v -> Downloads.pause(dest));
            add(R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), v -> Downloads.cancel(dest));
        } else {
            add(R.drawable.ic_s2_download, done > 0 ? Tr.t("s3d_resume", "Resume") : Tr.t("s3d_download", "Download"),
                    v -> Downloads.start(a, Suda3D.str(out, "url"), dest, size()));
            if (done > 0) add(R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), v -> Downloads.cancel(dest));
        }
    }

    private void add(int icon, String label, View.OnClickListener l) {
        LinearLayout b = Ui.button(a, icon, label, false, l);
        buttons.addView(b, Ui.margins(new LayoutParams(-2, Ui.dp(a, 36)), a, 0, 6, 8, 0));
    }

    private void open() {
        if (Store.isImage(dest.getName())) {
            try {
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", dest);
                a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (Exception e) { Ui.toast(a, Suda3D.message(e)); }
            return;
        }
        ModelViewerActivity.open(a, dest, Suda3D.str(out, "url"), Suda3D.str(out, "expires_at"), size(), project);
    }

    /** Save menu: to project / phone (SAF) / Downloads. */
    public static void saveMenu(Activity a, File f, String project, OnSaved saved) {
        String[] items = {Tr.t("s3d_save_project", "Save to project"), Tr.t("s3d_save_phone", "Save to phone…"), Tr.t("s3d_save_downloads", "Save to Downloads")};
        com.star4droid.star2d.ui.Dialogs.choose(a, Tr.t("s3d_save", "Save"), items, i -> {
            if (i == 0) Store.saveToProject(a, f, project, s -> { if (saved != null) saved.on(s); });
            else if (i == 1) {
                if (a instanceof SafHost) ((SafHost) a).createDocument(f);
                else ModelViewerActivity.open(a, f, "", "", f.length(), project);
            } else Store.saveToDownloads(a, f, null);
        });
    }

    /** Activities that can run ACTION_CREATE_DOCUMENT for "Save to phone". */
    public interface SafHost { void createDocument(File f); }
}
