package com.star4droid.star2d.suda.viewer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.suda.OutputView;
import com.star4droid.star2d.suda.Store;
import com.star4droid.star2d.suda.Suda3D;
import com.star4droid.star2d.suda.SudaUi;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.SystemBars;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SUDA3D 3D viewer (libGDX + gdx-gltf through {@link ModelViewerCore}). Runs in its own process (":viewer" in the
 * manifest) so its GL context never meets the editor's libGDX instance. Native views over the GL surface: top bar
 * (back / title / save), expiry banner, loading progress, view-mode toolbar, animation panel and model info.
 * Saving is plain file work done here; the editor re-reads the project's folders when it comes back.
 * Opened by {@link com.star4droid.star2d.suda.ModelViewerActivity} once the file is on the phone.
 */
public class Viewer3DActivity extends AndroidApplication implements ModelViewerCore.Listener, OutputView.SafHost {
    private static final int REQ_CREATE = 4712;

    private File file, pendingSave;
    private String project;
    private ModelViewerCore core;
    private ModelViewerCore.Info info;

    private LinearLayout loading, animPanel, toolbar;
    private ProgressBar loadBar;
    private TextView loadText, errorText, infoText, animName, animTime;
    private ScrollView infoCard;
    private SeekBar seek;
    private android.widget.ImageView playBtn;
    private TextView[] modeChips;
    private TextView wireChip, gridChip, shadowChip, loopChip, speedChip, animChip, infoChip;
    private int mode = ModelViewerCore.MODE_MESH, animIndex = -1;
    private boolean wire, grid = true, shadows, loop = true, playing = true, seeking;
    private float speed = 1f, duration;
    private static final float[] SPEEDS = {0.25f, 0.5f, 1f, 1.5f, 2f};

    public static Intent intent(Context c, File f, String url, String expiresAt, long size, String project) {
        return new Intent(c, Viewer3DActivity.class).putExtra("path", f.getAbsolutePath()).putExtra("url", url)
                .putExtra("expires", expiresAt).putExtra("size", size).putExtra("project", project);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        Intent i = getIntent();
        file = new File(i.getStringExtra("path"));
        project = i.getStringExtra("project");
        String url = i.getStringExtra("url"), expires = i.getStringExtra("expires");

        core = new ModelViewerCore(file, this);
        core.setUiScale(getResources().getDisplayMetrics().density);
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        core.setMaxTextureSize(am != null && am.getMemoryClass() < 256 ? 2048 : 4096);
        AndroidApplicationConfiguration cfg = new AndroidApplicationConfiguration();
        cfg.depth = 24;
        cfg.useAccelerometer = false;
        cfg.useCompass = false;
        View gl = initializeForView(core, cfg);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(gl, Ui.match());

        // ---- top: bar + expiry banner
        LinearLayout top = Ui.column(this);
        top.addView(SudaUi.topBar(this, file.getName(),
                Ui.iconButton(this, R.drawable.ic_s2_save, Tr.t("s3d_save", "Save"), v -> OutputView.saveMenu(this, file, project, null))), Ui.matchWrap());
        long t = SudaUi.parseTime(expires);
        if (url != null && !url.isEmpty()) {
            TextView warn = Ui.text(this, Tr.t("s3d_viewer_warning", "This model is deleted from the server 3 days after it was made.")
                    + (t > 0 ? " " + Tr.t("s3d_expires", "Expires") + ": " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(t)) : ""), 12.5f, 0xFF1B1300);
            warn.setBackgroundColor(SudaUi.ORANGE);
            warn.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            top.addView(warn, Ui.matchWrap());
        }
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // ---- centre: loading / error
        loading = Ui.column(this);
        loading.setGravity(Gravity.CENTER_HORIZONTAL);
        loading.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        loading.setBackground(Ui.rounded(0xE6202328, Ui.dp(this, 14)));
        loadText = Ui.text(this, Tr.t("s3d_view_loading", "Loading model…"), 14, Ui.TEXT);
        loadText.setGravity(Gravity.CENTER);
        loading.addView(loadText, Ui.matchWrap());
        loadBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadBar.setMax(1000);
        loading.addView(loadBar, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(this, 220), -2), this, 0, 10, 0, 0));
        TextView sz = Ui.text(this, Suda3D.fmtSize(file.length()), 12, Ui.TEXT_3);
        loading.addView(sz);
        root.addView(loading, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        errorText = Ui.text(this, "", 14, Ui.TEXT);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        errorText.setBackground(Ui.rounded(0xE6202328, Ui.dp(this, 14)));
        errorText.setVisibility(View.GONE);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        elp.leftMargin = elp.rightMargin = Ui.dp(this, 24);
        root.addView(errorText, elp);

        // ---- info card (right / top under the bar)
        infoText = Ui.text(this, "", 12.5f, Ui.TEXT);
        infoText.setTypeface(Typeface.MONOSPACE);
        infoText.setTextIsSelectable(true);
        infoText.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        infoCard = new ScrollView(this);
        infoCard.setBackground(Ui.rounded(0xEE202328, Ui.dp(this, 12), Ui.BORDER, Ui.dp(this, 1)));
        infoCard.addView(infoText);
        infoCard.setVisibility(View.GONE);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(this, 290), -2, Gravity.TOP | Gravity.END);
        ilp.topMargin = Ui.dp(this, url != null && !url.isEmpty() ? 108 : 64);
        ilp.rightMargin = ilp.leftMargin = Ui.dp(this, 10);
        ilp.bottomMargin = Ui.dp(this, 120);
        root.addView(infoCard, ilp);

        // ---- bottom: animation panel + toolbar
        LinearLayout bottom = Ui.column(this);
        animPanel = buildAnimPanel();
        animPanel.setVisibility(View.GONE);
        bottom.addView(animPanel, Ui.margins(Ui.matchWrap(), this, 8, 0, 8, 6));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(Ui.SURFACE);
        toolbar = Ui.row(this);
        toolbar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        hs.addView(toolbar);
        bottom.addView(hs, Ui.matchWrap());
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        buildToolbar();

        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
        setContentView(root);
        SystemBars.apply(this);
    }

    // ------------------------------------------------------------------ toolbar

    private TextView chip(String label, View.OnClickListener l) {
        TextView c = Ui.text(this, label, 13, Ui.TEXT);
        c.setGravity(Gravity.CENTER);
        c.setMinHeight(Ui.dp(this, 34));
        c.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        c.setOnClickListener(l);
        c.setClickable(true);
        toolbar.addView(c, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(this, 34)), this, 0, 0, 6, 0));
        return c;
    }

    private void on(TextView c, boolean on) {
        c.setBackground(Ui.rounded(on ? Ui.ACCENT_SOFT : Ui.SURFACE_2, Ui.dp(this, 17), on ? Ui.ACCENT : Ui.BORDER, Ui.dp(this, 1)));
        c.setTextColor(on ? Ui.TEXT : Ui.TEXT_2);
    }

    private void buildToolbar() {
        String[] names = {Tr.t("s3d_view_mesh", "Mesh"), Tr.t("s3d_view_bones", "Bones"), Tr.t("s3d_view_both", "Mesh + bones")};
        modeChips = new TextView[3];
        for (int k = 0; k < 3; k++) {
            final int m = k;
            modeChips[k] = chip(names[k], v -> { mode = m; core.setMode(m); refreshChips(); });
        }
        wireChip = chip(Tr.t("s3d_view_wire", "Wireframe"), v -> { wire = !wire; core.setWireframe(wire); refreshChips(); });
        gridChip = chip(Tr.t("s3d_view_grid", "Grid"), v -> { grid = !grid; core.setGrid(grid); refreshChips(); });
        shadowChip = chip(Tr.t("s3d_view_shadows", "Shadows"), v -> { shadows = !shadows; core.setShadows(shadows); refreshChips(); });
        animChip = chip(Tr.t("s3d_view_anim", "Animation"), v -> { animPanel.setVisibility(animPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); refreshChips(); });
        infoChip = chip(Tr.t("s3d_view_info", "Info"), v -> { infoCard.setVisibility(infoCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); refreshChips(); });
        chip(Tr.t("s3d_view_reset", "Reset view"), v -> core.resetView());
        animChip.setVisibility(View.GONE);
        refreshChips();
    }

    private void refreshChips() {
        for (int k = 0; k < 3; k++) on(modeChips[k], mode == k);
        on(wireChip, wire);
        on(gridChip, grid);
        on(shadowChip, shadows);
        on(animChip, animPanel.getVisibility() == View.VISIBLE);
        on(infoChip, infoCard.getVisibility() == View.VISIBLE);
        if (loopChip != null) on(loopChip, loop);
        if (speedChip != null) { on(speedChip, speed != 1f); speedChip.setText(fmt(speed) + "x"); }
    }

    private LinearLayout buildAnimPanel() {
        LinearLayout p = Ui.column(this);
        p.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        p.setBackground(Ui.rounded(0xEE202328, Ui.dp(this, 12), Ui.BORDER, Ui.dp(this, 1)));
        LinearLayout r1 = Ui.row(this);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        playBtn = Ui.iconButton(this, R.drawable.ic_s2_pause, Tr.t("s3d_view_play", "Play / pause"), v -> {
            playing = !playing;
            core.setPlaying(playing);
            playBtn.setImageResource(playing ? R.drawable.ic_s2_pause : R.drawable.ic_s2_play);
        });
        r1.addView(playBtn);
        animName = Ui.text(this, "", 13.5f, Ui.TEXT);
        animName.setSingleLine(true);
        animName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        animName.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        animName.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 8), Ui.BORDER, Ui.dp(this, 1)));
        animName.setOnClickListener(v -> pickAnimation());
        r1.addView(animName, Ui.margins(Ui.weight(1), this, 4, 0, 6, 0));
        TextView[] holder = new TextView[2];
        loopChip = Ui.text(this, Tr.t("s3d_view_loop", "Loop"), 13, Ui.TEXT);
        speedChip = Ui.text(this, "1x", 13, Ui.TEXT);
        holder[0] = loopChip;
        holder[1] = speedChip;
        for (TextView c : holder) {
            c.setGravity(Gravity.CENTER);
            c.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
            r1.addView(c, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(this, 32)), this, 0, 0, 6, 0));
        }
        loopChip.setOnClickListener(v -> { loop = !loop; core.setLoop(loop); refreshChips(); });
        speedChip.setOnClickListener(v -> {
            int k = 0;
            for (int j = 0; j < SPEEDS.length; j++) if (SPEEDS[j] == speed) k = j;
            speed = SPEEDS[(k + 1) % SPEEDS.length];
            core.setSpeed(speed);
            refreshChips();
        });
        p.addView(r1, Ui.matchWrap());
        LinearLayout r2 = Ui.row(this);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) { core.seek(duration * v / 1000f); animTime.setText(time(duration * v / 1000f)); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
                if (playing) { playing = false; core.setPlaying(false); playBtn.setImageResource(R.drawable.ic_s2_play); }
            }
            @Override public void onStopTrackingTouch(SeekBar s) { seeking = false; }
        });
        r2.addView(seek, Ui.weight(1));
        animTime = Ui.text(this, "", 12, Ui.TEXT_2);
        animTime.setTypeface(Typeface.MONOSPACE);
        r2.addView(animTime);
        p.addView(r2, Ui.matchWrap());
        return p;
    }

    private void pickAnimation() {
        if (info == null || info.animationNames.isEmpty()) return;
        String[] items = new String[info.animationNames.size()];
        for (int k = 0; k < items.length; k++) items[k] = info.animationNames.get(k) + "  ·  " + fmt(info.animationDurations.get(k)) + " s";
        Dialogs.choose(this, Tr.t("s3d_view_anim", "Animation"), items, k -> {
            core.playAnimation(k);
            setAnimLabel(k);
        });
    }

    private void setAnimLabel(int k) {
        animIndex = k;
        duration = info.animationDurations.get(k);
        animName.setText(info.animationNames.get(k) + "  ▾");
    }

    // ------------------------------------------------------------------ core callbacks (GL / loader thread)

    @Override
    public void onProgress(float p, String stage) {
        runOnUiThread(() -> {
            loadBar.setProgress((int) (p * 1000));
            String s;
            switch (stage) {
                case "read": s = Tr.t("s3d_view_reading", "Reading file…"); break;
                case "parse": s = Tr.t("s3d_view_parsing", "Reading the model…"); break;
                case "textures": s = Tr.t("s3d_view_textures", "Preparing textures…"); break;
                default: s = Tr.t("s3d_view_uploading", "Sending to the GPU…");
            }
            loadText.setText(s);
        });
    }

    @Override
    public void onLoaded(ModelViewerCore.Info i) {
        runOnUiThread(() -> {
            info = i;
            loading.setVisibility(View.GONE);
            infoText.setText(infoString(i));
            boolean anims = !i.animationNames.isEmpty();
            animChip.setVisibility(anims ? View.VISIBLE : View.GONE);
            if (anims) {
                setAnimLabel(0);
                animPanel.setVisibility(View.VISIBLE);
            }
            if (i.bones == 0) for (int k = 1; k < 3; k++) modeChips[k].setVisibility(View.GONE);
            refreshChips();
        });
    }

    @Override
    public void onError(String code) {
        runOnUiThread(() -> {
            loading.setVisibility(View.GONE);
            String m;
            if ("oom".equals(code)) m = Tr.t("s3d_view_oom", "Not enough memory to show this model on this phone.");
            else if (code.startsWith("unsupported:")) m = Tr.t("s3d_view_unsupported", "This model uses a feature the viewer does not support.") + "\n" + code.substring(12);
            else m = Tr.t("s3d_view_failed", "Could not open the model.") + "\n" + code.substring(code.indexOf(':') + 1);
            errorText.setText(m);
            errorText.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onAnimation(int index, float t, float dur, boolean isPlaying) {
        runOnUiThread(() -> {
            if (seeking) return;
            duration = dur;
            seek.setProgress(dur > 0 ? (int) (t / dur * 1000) : 0);
            animTime.setText(time(t) + " / " + time(dur));
            if (isPlaying != playing && !isPlaying) { // a non-looping animation reached its end
                playing = false;
                playBtn.setImageResource(R.drawable.ic_s2_play);
            }
        });
    }

    private String infoString(ModelViewerCore.Info i) {
        StringBuilder b = new StringBuilder();
        line(b, Tr.t("s3d_info_format", "Format"), i.format);
        line(b, Tr.t("s3d_info_file", "File size"), Suda3D.fmtSize(i.fileSize));
        line(b, Tr.t("s3d_info_vertices", "Vertices"), String.format(Locale.US, "%,d", i.vertices));
        line(b, Tr.t("s3d_info_triangles", "Triangles"), String.format(Locale.US, "%,d", i.triangles));
        line(b, Tr.t("s3d_info_meshes", "Meshes"), String.valueOf(i.meshes));
        line(b, Tr.t("s3d_info_materials", "Materials"), String.valueOf(i.materials));
        line(b, Tr.t("s3d_info_textures", "Textures"), i.textures + (i.maxTextureWidth > 0 ? "  (" + Tr.t("s3d_info_max", "max") + " " + i.maxTextureWidth + "×" + i.maxTextureHeight + ")" : ""));
        line(b, Tr.t("s3d_info_bones", "Bones"), String.valueOf(i.bones));
        line(b, Tr.t("s3d_info_size", "Size"), fmt(i.sizeX) + " × " + fmt(i.sizeY) + " × " + fmt(i.sizeZ));
        line(b, Tr.t("s3d_info_anims", "Animations"), String.valueOf(i.animationNames.size()));
        for (int k = 0; k < i.animationNames.size(); k++)
            b.append("  • ").append(i.animationNames.get(k)).append("  ").append(fmt(i.animationDurations.get(k))).append(" s\n");
        return b.toString().trim();
    }

    private static void line(StringBuilder b, String k, String v) { b.append(k).append(": ").append(v).append('\n'); }

    private static String fmt(float v) {
        String s = String.format(Locale.US, Math.abs(v) >= 100 ? "%.0f" : "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private static String time(float s) { return String.format(Locale.US, "%.2f", s); }

    // ------------------------------------------------------------------ save to phone (SAF)

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
        if (req == REQ_CREATE && res == Activity.RESULT_OK && data != null && data.getData() != null && pendingSave != null)
            Store.saveToUri(this, pendingSave, data.getData(), null);
    }
}
