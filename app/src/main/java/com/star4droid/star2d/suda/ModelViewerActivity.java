package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

/**
 * 3D viewer page of a generated model: shows the model when it is downloaded (orbit / pinch / pan), else Download
 * with progress, Pause and Cancel. A banner warns that the server deletes the model 3 days after it was made.
 * Save → project / phone (SAF) / Downloads.
 */
public class ModelViewerActivity extends Activity implements Downloads.Listener, OutputView.SafHost {
    private static final int REQ_CREATE = 4711;
    private File file, pendingSave;
    private String url, expires, project;
    private long size;
    private FrameLayout body;
    private SimpleModelView gl;

    public static void open(Activity a, File f, String url, String expiresAt, long size, String project) {
        a.startActivity(new Intent(a, ModelViewerActivity.class).putExtra("path", f.getAbsolutePath()).putExtra("url", url)
                .putExtra("expires", expiresAt).putExtra("size", size).putExtra("project", project));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        file = new File(i.getStringExtra("path"));
        url = i.getStringExtra("url");
        expires = i.getStringExtra("expires");
        size = i.getLongExtra("size", -1);
        project = i.getStringExtra("project");
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, file.getName(),
                Ui.iconButton(this, R.drawable.ic_s2_save, Tr.t("s3d_save", "Save"), v -> {
                    if (!file.exists()) { Ui.toast(this, Tr.t("s3d_download_first", "Download the model first")); return; }
                    OutputView.saveMenu(this, file, project, null);
                })), Ui.matchWrap());
        long t = SudaUi.parseTime(expires);
        if (url != null && !url.isEmpty()) {
            TextView warn = Ui.text(this, Tr.t("s3d_viewer_warning", "This model is deleted from the server 3 days after it was made.")
                    + (t > 0 ? " " + Tr.t("s3d_expires", "Expires") + ": " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(t)) : "")
                    + (file.exists() ? "" : " " + Tr.t("s3d_viewer_warning2", "Download it to keep it.")), 12.5f, 0xFF1B1300);
            warn.setBackgroundColor(SudaUi.ORANGE);
            warn.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            root.addView(warn, Ui.matchWrap());
        }
        body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        show();
    }

    @Override protected void onResume() { super.onResume(); Downloads.listen(this); if (gl != null) gl.onResume(); }
    @Override protected void onPause() { super.onPause(); Downloads.unlisten(this); if (gl != null) gl.onPause(); }

    private void show() {
        body.removeAllViews();
        gl = null;
        if (file.exists()) {
            gl = new SimpleModelView(this);
            body.addView(gl, Ui.match());
            TextView hint = Ui.text(this, Tr.t("s3d_viewer_hint", "Drag to rotate · pinch to zoom · two fingers to move"), 11.5f, Ui.TEXT_3);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = Ui.dp(this, 10);
            body.addView(hint, lp);
            final SimpleModelView v = gl;
            Suda3D.IO.execute(() -> {
                if (!v.load(file)) runOnUiThread(() -> {
                    TextView e = Ui.text(this, v.error(), 14, Ui.TEXT_2);
                    e.setGravity(Gravity.CENTER);
                    e.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
                    body.addView(e, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
                });
            });
            return;
        }
        LinearLayout col = Ui.column(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        col.addView(Ui.smallIcon(this, R.drawable.ic_s2_view3d, SudaUi.ORANGE, 64));
        TextView sz = Ui.text(this, Suda3D.fmtSize(size), 15, Ui.TEXT);
        sz.setGravity(Gravity.CENTER);
        sz.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        col.addView(sz);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        col.addView(bar, Ui.matchWrap());
        LinearLayout btns = Ui.row(this);
        btns.setGravity(Gravity.CENTER);
        col.addView(btns, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));
        body.addView(col, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        body.setTag(new Object[]{sz, bar, btns});
        refreshDownload(Downloads.saved(file), Downloads.total(file) > 0 ? Downloads.total(file) : size,
                Downloads.running(file) ? Downloads.RUNNING : "");
    }

    private void refreshDownload(long done, long total, String state) {
        if (!(body.getTag() instanceof Object[])) return;
        Object[] v = (Object[]) body.getTag();
        TextView sz = (TextView) v[0];
        ProgressBar bar = (ProgressBar) v[1];
        LinearLayout btns = (LinearLayout) v[2];
        boolean running = Downloads.RUNNING.equals(state) || Downloads.running(file);
        sz.setText((done > 0 ? Suda3D.fmtSize(done) + " / " : "") + Suda3D.fmtSize(total));
        bar.setProgress(total > 0 ? (int) (done * 1000 / total) : 0);
        btns.removeAllViews();
        if (running) {
            btns.addView(Ui.button(this, R.drawable.ic_s2_pause, Tr.t("s3d_pause", "Pause"), false, x -> Downloads.pause(file)));
            btns.addView(Ui.button(this, R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), false, x -> Downloads.cancel(file)), Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 8, 0, 0, 0));
        } else {
            if (url == null || url.isEmpty()) return;
            btns.addView(Ui.button(this, R.drawable.ic_s2_download, done > 0 ? Tr.t("s3d_resume", "Resume") : Tr.t("s3d_download", "Download"), true,
                    x -> Downloads.start(this, url, file, size)));
            if (done > 0) btns.addView(Ui.button(this, R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), false, x -> Downloads.cancel(file)), Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 8, 0, 0, 0));
        }
    }

    @Override
    public void onProgress(File dest, long done, long total, String state) {
        if (!dest.getAbsolutePath().equals(file.getAbsolutePath())) return;
        if (Downloads.DONE.equals(state)) { show(); return; }
        if (state.startsWith(Downloads.FAILED)) Ui.toast(this, state.substring(Math.min(state.length(), Downloads.FAILED.length() + 1)));
        refreshDownload(done, total > 0 ? total : size, state);
    }

    @Override
    public void createDocument(File f) {
        pendingSave = f;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(Suda3D.mime(f.getName())).putExtra(Intent.EXTRA_TITLE, f.getName());
        try { startActivityForResult(i, REQ_CREATE); } catch (Exception e) { Ui.toast(this, Suda3D.message(e)); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_CREATE && res == RESULT_OK && data != null && data.getData() != null && pendingSave != null)
            Store.saveToUri(this, pendingSave, data.getData(), null);
    }
}
