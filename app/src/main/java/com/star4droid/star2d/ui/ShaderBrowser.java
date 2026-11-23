package com.star4droid.star2d.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.star4droid.star2d.evo.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shader library of the 3D "Shader" property: the element shaders of World Builder (assets/wb/fx, same snippet format
 * as the engine), with a picture of each (assets/shaderlib), sections and search. Choosing one copies its code into the
 * project (shaders/NAME.glsl; an existing, maybe edited, copy is kept), so it works in the editor and in exported games
 * and can be edited like any project shader. Returns the value for the property ("shaders/NAME").
 */
public final class ShaderBrowser {
    private ShaderBrowser() {}

    public interface OnPicked { void on(String value); }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<>(80);
    private static final String[] SECTIONS = {"Shatter", "Evaporate", "Glass", "Fantasy"};

    static final class Item {
        String id, cat, en, ar, desc, descAr;
        boolean header;
        String title;
        int count;
    }

    /** Opens the library. {@code project}: the project folder; {@code current}: the property's value. */
    public static void show(Activity a, String project, String current, OnPicked cb) {
        if (project == null) {
            Ui.toast(a, Tr.t("icn_no_project", "Open a project first"));
            return;
        }
        try {
            new Browser(a, new File(project), current, cb).open();
        } catch (Throwable t) {
            Ui.toast(a, "Shaders: " + t.getMessage());
        }
    }

    static String sectionTitle(String id) {
        switch (id) {
            case "Shatter": return Tr.t("sl_shatter", "Shatter");
            case "Evaporate": return Tr.t("sl_evaporate", "Evaporate");
            case "Glass": return Tr.t("sl_glass", "Glass and hologram");
            case "Fantasy": return Tr.t("sl_fantasy", "Fantasy");
        }
        return id;
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) != -1) bo.write(b, 0, r);
        return bo.toString("UTF-8");
    }

    private static final class Browser {
        final Activity a;
        final File project;
        final String current;
        final OnPicked cb;
        final boolean ar = "ar".equals(Tr.lang());
        final Handler main = new Handler(Looper.getMainLooper());
        final ArrayList<Item> all = new ArrayList<>();
        final ArrayList<Item> shown = new ArrayList<>();
        String filter = "", query = "";
        AlertDialog dialog;
        RecyclerView rv;
        GridLayoutManager glm;
        LinearLayout chips;
        TextView count, empty;
        Adapter adapter;
        Runnable pendingSearch;

        Browser(Activity a, File project, String current, OnPicked cb) throws Exception {
            this.a = a;
            this.project = project;
            this.current = current == null ? "" : current.trim();
            this.cb = cb;
            String text;
            try (InputStream in = a.getAssets().open("shaderlib/index.txt")) {
                text = readAll(in);
            }
            for (String line : text.split("\n")) {
                if (line.startsWith("#")) continue;
                String[] p = line.trim().split("\\|");
                if (p.length < 6) continue;
                Item it = new Item();
                it.id = p[0];
                it.en = p[1];
                it.cat = p[2];
                it.desc = p[3];
                it.ar = p[4];
                it.descAr = p[5];
                all.add(it);
            }
        }

        String value(Item it) {
            return "shaders/" + it.id;
        }

        void open() {
            boolean land = Ui.isLandscape(a);
            float dens = a.getResources().getDisplayMetrics().density;
            int screenW = a.getResources().getDisplayMetrics().widthPixels;
            int dlgWdp = (int) ((land ? screenW * 0.94f : screenW) / dens) - 36;
            int span = Math.max(2, Math.min(8, dlgWdp / 118));

            LinearLayout root = Ui.column(a);
            root.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), Ui.dp(a, 4));

            LinearLayout sr = Ui.row(a);
            sr.setBackground(Ui.rounded(0xFF121417, Ui.dp(a, 9), Ui.BORDER, 1));
            sr.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 4), 0);
            sr.setGravity(Gravity.CENTER_VERTICAL);
            sr.addView(Ui.smallIcon(a, R.drawable.ic_s2_search, Ui.TEXT_3, 18));
            EditText search = new EditText(a);
            search.setHint(Tr.t("sl_search", "Search: fire, glass, dissolve, hologram..."));
            search.setHintTextColor(Ui.TEXT_3);
            search.setTextColor(Ui.TEXT);
            search.setTextSize(14);
            search.setSingleLine(true);
            search.setBackground(null);
            search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            search.setPadding(Ui.dp(a, 8), Ui.dp(a, land ? 7 : 10), Ui.dp(a, 8), Ui.dp(a, land ? 7 : 10));
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
                @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
                @Override public void afterTextChanged(Editable s) {
                    query = s.toString();
                    if (pendingSearch != null) main.removeCallbacks(pendingSearch);
                    pendingSearch = Browser.this::refresh;
                    main.postDelayed(pendingSearch, 140);
                }
            });
            sr.addView(search, Ui.weight(1));

            LinearLayout bar = Ui.row(a);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Ui.dp(a, 16), Ui.dp(a, land ? 4 : 8), Ui.dp(a, 8), Ui.dp(a, land ? 2 : 4));
            TextView title = Ui.text(a, Tr.t("sl_title", "Shader library"), land ? 16 : 18, Ui.TEXT);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            if (land) {
                bar.addView(title);
                bar.addView(sr, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1f), a, 12, 0, 6, 0));
            } else bar.addView(title, Ui.weight(1));
            bar.addView(Ui.iconButton(a, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> { if (dialog != null) dialog.dismiss(); }));

            if (!land) root.addView(sr, Ui.matchWrap());
            chips = Ui.row(a);
            chips.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 6));
            buildChips(land);
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(chips);
            root.addView(hs, Ui.matchWrap());
            count = Ui.text(a, "", 11.5f, Ui.TEXT_3);
            count.setPadding(Ui.dp(a, 2), 0, 0, Ui.dp(a, 4));
            root.addView(count, Ui.matchWrap());

            rv = new RecyclerView(a);
            glm = new GridLayoutManager(a, span);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override public int getSpanSize(int position) {
                    return position < shown.size() && shown.get(position).header ? glm.getSpanCount() : 1;
                }
            });
            rv.setLayoutManager(glm);
            adapter = new Adapter();
            rv.setAdapter(adapter);
            rv.setClipToPadding(false);
            rv.setPadding(0, 0, 0, Ui.dp(a, 8));
            FrameLayout frame = new FrameLayout(a);
            frame.addView(rv, new FrameLayout.LayoutParams(-1, -1));
            empty = Ui.text(a, Tr.t("sl_none", "No shader matches this search"), 13, Ui.TEXT_3);
            empty.setGravity(Gravity.CENTER);
            empty.setVisibility(View.GONE);
            frame.addView(empty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));

            TextView note = Ui.text(a, Tr.t("sl_note", "Choosing a shader copies it into the project (shaders folder). Tune it with Shader Params: amount, speed, tint."), 10.5f, Ui.TEXT_3);
            note.setMaxLines(2);
            note.setEllipsize(TextUtils.TruncateAt.END);
            note.setPadding(Ui.dp(a, 2), Ui.dp(a, 2), 0, 0);
            root.addView(note, Ui.matchWrap());

            refresh();
            dialog = new AlertDialog.Builder(a).setCustomTitle(bar).setView(root).create();
            dialog.show();
            Window w = dialog.getWindow();
            if (w != null) {
                int sh = a.getResources().getDisplayMetrics().heightPixels;
                w.setLayout(land ? (int) (screenW * 0.94f) : ViewGroup.LayoutParams.MATCH_PARENT, (int) (sh * (land ? 0.97f : 0.88f)));
            }
        }

        void buildChips(boolean land) {
            chips.removeAllViews();
            addChip(Tr.t("icn_all", "All") + " (" + all.size() + ")", "", land);
            for (String s : SECTIONS) {
                int n = 0;
                for (Item it : all) if (it.cat.equals(s)) n++;
                addChip(sectionTitle(s) + " (" + n + ")", s, land);
            }
        }

        void addChip(String label, String id, boolean land) {
            boolean on = id.equals(filter);
            TextView t = Ui.text(a, label, 12.5f, on ? Ui.TEXT : Ui.TEXT_2);
            t.setSingleLine(true);
            t.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
            t.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_2, Ui.dp(a, 16), on ? Ui.ACCENT : Ui.BORDER, 1));
            t.setOnClickListener(v -> {
                filter = id;
                buildChips(land);
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(a, 6));
            chips.addView(t, lp);
        }

        void refresh() {
            shown.clear();
            String q = query.trim().toLowerCase(Locale.ROOT);
            String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
            int n = 0;
            for (String s : SECTIONS) {
                if (!filter.isEmpty() && !filter.equals(s)) continue;
                ArrayList<Item> hit = new ArrayList<>();
                for (Item it : all) {
                    if (!it.cat.equals(s)) continue;
                    String hay = (it.id + " " + it.en + " " + it.ar + " " + it.desc + " " + it.descAr + " " + sectionTitle(s)).toLowerCase(Locale.ROOT).replace('_', ' ');
                    boolean ok = true;
                    for (String w : words) if (!hay.contains(w)) { ok = false; break; }
                    if (ok) hit.add(it);
                }
                if (hit.isEmpty()) continue;
                Item h = new Item();
                h.header = true;
                h.title = sectionTitle(s);
                h.count = hit.size();
                shown.add(h);
                shown.addAll(hit);
                n += hit.size();
            }
            adapter.notifyDataSetChanged();
            rv.scrollToPosition(0);
            count.setText(n + " " + Tr.t("sl_count", "shaders"));
            empty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        }

        void pick(Item it) {
            File dst = new File(project, "shaders/" + it.id + ".glsl");
            POOL.execute(() -> {
                String err = null;
                try {
                    if (!dst.isFile()) {
                        File dir = dst.getParentFile();
                        if (dir != null) dir.mkdirs();
                        try (InputStream in = a.getAssets().open("wb/fx/" + it.id + ".glsl"); OutputStream out = new FileOutputStream(dst)) {
                            byte[] b = new byte[8192];
                            int r;
                            while ((r = in.read(b)) != -1) out.write(b, 0, r);
                        }
                    }
                } catch (Throwable t) {
                    err = t.getMessage();
                }
                final String e = err;
                main.post(() -> {
                    if (e != null) {
                        Ui.toast(a, "Shaders: " + e);
                        return;
                    }
                    if (dialog != null) dialog.dismiss();
                    cb.on(value(it));
                });
            });
        }

        final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
            @Override public int getItemCount() { return shown.size(); }

            @Override public int getItemViewType(int position) { return shown.get(position).header ? 0 : 1; }

            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                View v;
                if (type == 0) {
                    TextView t = Ui.text(a, "", 13, Ui.TEXT);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                    t.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 6));
                    t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                    v = t;
                } else {
                    LinearLayout cell = Ui.column(a);
                    cell.setPadding(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 7));
                    ImageView iv = new ImageView(a);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setId(android.R.id.icon);
                    iv.setBackground(Ui.rounded(0xFF0E1118, Ui.dp(a, 8)));
                    iv.setClipToOutline(true);
                    iv.setAdjustViewBounds(true);
                    cell.addView(iv, new LinearLayout.LayoutParams(-1, Ui.dp(a, 96)));
                    TextView t = Ui.text(a, "", 12.5f, Ui.TEXT);
                    t.setId(android.R.id.text1);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setPadding(Ui.dp(a, 2), Ui.dp(a, 5), Ui.dp(a, 2), 0);
                    cell.addView(t, Ui.matchWrap());
                    TextView d = Ui.text(a, "", 10.5f, Ui.TEXT_3);
                    d.setId(android.R.id.text2);
                    d.setMaxLines(2);
                    d.setEllipsize(TextUtils.TruncateAt.END);
                    d.setPadding(Ui.dp(a, 2), Ui.dp(a, 1), Ui.dp(a, 2), 0);
                    cell.addView(d, Ui.matchWrap());
                    RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
                    lp.setMargins(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
                    cell.setLayoutParams(lp);
                    v = cell;
                }
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                Item it = shown.get(position);
                if (it.header) {
                    ((TextView) h.itemView).setText(it.title + "  ·  " + it.count);
                    return;
                }
                boolean sel = value(it).equals(current);
                h.itemView.setBackground(Ui.ripple(sel ? Ui.ACCENT_SOFT : Ui.SURFACE_2, Ui.dp(a, 10)));
                h.itemView.setOnClickListener(v -> pick(it));
                TextView t = h.itemView.findViewById(android.R.id.text1);
                t.setText(ar ? it.ar : it.en);
                t.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                ((TextView) h.itemView.findViewById(android.R.id.text2)).setText(ar ? it.descAr : it.desc);
                ImageView iv = h.itemView.findViewById(android.R.id.icon);
                final String asset = "shaderlib/" + it.id + ".webp";
                iv.setTag(asset);
                Bitmap b = THUMBS.get(asset);
                if (b != null) {
                    iv.setImageBitmap(b);
                    return;
                }
                iv.setImageDrawable(null);
                POOL.execute(() -> {
                    Bitmap bm = null;
                    try (InputStream in = a.getAssets().open(asset)) {
                        bm = BitmapFactory.decodeStream(in);
                    } catch (Throwable ignored) {
                    }
                    final Bitmap fb = bm;
                    if (fb != null) THUMBS.put(asset, fb);
                    main.post(() -> {
                        if (asset.equals(iv.getTag()) && fb != null) iv.setImageBitmap(fb);
                    });
                });
            }
        }
    }
}
