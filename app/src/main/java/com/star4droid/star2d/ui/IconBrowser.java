package com.star4droid.star2d.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.icons.IconCatalog;
import com.star4droid.star2d.icons.IconImport;
import com.star4droid.star2d.icons.IconRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Icon library browser: search (English words and tags), category chips, a grid of white icons, and a detail row to choose
 * size and colour and import the icon as a PNG into the project's images folder (Replace if the name is taken).
 */
public final class IconBrowser {
    private IconBrowser() {}

    /** Called after a successful import: {@code useAs} is the value for an element's image property, e.g. "/icon_heart.png". */
    public interface OnImported { void on(String useAs, File file); }

    private static final int[] SIZES = {32, 48, 64, 96, 128, 256};
    private static final int[] COLORS = {0xFFFFFFFF, 0xFF000000, 0xFFF44336, 0xFFFF9800, 0xFFFFC107, 0xFF4CAF50, 0xFF00BCD4, 0xFF2196F3, 0xFF9C27B0, 0xFFE91E63};

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<>(600);

    /** Opens the browser. {@code closeOnImport}: close it after the first import (used when picking an image for a property). */
    public static void show(Activity a, File projectDir, boolean closeOnImport, OnImported cb) {
        if (projectDir == null) {
            Ui.toast(a, Tr.t("icn_no_project", "Open a project first"));
            return;
        }
        final IconCatalog catalog;
        try {
            catalog = IconCatalog.get(a);
        } catch (Throwable t) {
            Ui.toast(a, "Icons: " + t.getMessage());
            return;
        }
        new Browser(a, catalog, projectDir, closeOnImport, cb).open();
    }

    private static final class Browser {
        final Activity a;
        final IconCatalog catalog;
        final File project;
        final boolean closeOnImport;
        final OnImported cb;
        final Handler main = new Handler(Looper.getMainLooper());

        final ArrayList<IconCatalog.Icon> shown = new ArrayList<>();
        int category = -1;
        int set = -1;
        String query = "";
        IconCatalog.Icon selected;
        int size = 64;
        int color = 0xFFFFFFFF;
        boolean customName = false;
        boolean shadow = false;

        AlertDialog dialog;
        RecyclerView grid;
        TextView count, empty;
        LinearLayout chips, setChips, detail, sizeRow, colorRow;
        ImageView preview;
        TextView selTitle, selTags;
        EditText nameField;
        LinearLayout importBtn;
        TextView importLabel;
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
        Runnable pendingSearch;
        View detailHost;     // landscape: the scroll that holds the detail panel (hidden until an icon is chosen)
        boolean land;
        int landSpan = 6;

        Browser(Activity a, IconCatalog catalog, File project, boolean closeOnImport, OnImported cb) {
            this.a = a;
            this.catalog = catalog;
            this.project = project;
            this.closeOnImport = closeOnImport;
            this.cb = cb;
        }

        void open() {
            LinearLayout root = Ui.column(a);
            root.setPadding(Ui.dp(a, 10), Ui.dp(a, 2), Ui.dp(a, 10), 0);

            // search
            LinearLayout sr = Ui.row(a);
            sr.setBackground(Ui.rounded(0xFF121417, Ui.dp(a, 9), Ui.BORDER, 1));
            sr.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 4), 0);
            sr.setGravity(Gravity.CENTER_VERTICAL);
            sr.addView(Ui.smallIcon(a, R.drawable.ic_s2_search, Ui.TEXT_3, 18));
            EditText search = new EditText(a);
            search.setHint(Tr.t("icn_search", "Search: heart, sound, arrow, coin..."));
            search.setHintTextColor(Ui.TEXT_3);
            search.setTextColor(Ui.TEXT);
            search.setTextSize(14);
            search.setSingleLine(true);
            search.setBackground(null);
            search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            search.setPadding(Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10));
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
            land = Ui.isLandscape(a);
            if (!land) root.addView(sr, Ui.matchWrap());

            // sets (UI icons / game icons)
            setChips = land ? Ui.column(a) : Ui.row(a);
            setChips.setPadding(0, land ? 0 : Ui.dp(a, 8), 0, 0);
            if (!land) root.addView(setChips, Ui.matchWrap());
            buildSetChips();

            // categories (portrait: a row that scrolls sideways; landscape: a list at the left side)
            chips = land ? Ui.column(a) : Ui.row(a);
            if (!land) {
                HorizontalScrollView hs = new HorizontalScrollView(a);
                hs.setHorizontalScrollBarEnabled(false);
                chips.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
                hs.addView(chips);
                root.addView(hs, Ui.matchWrap());
            }
            buildChips();

            count = Ui.text(a, "", 11.5f, Ui.TEXT_3);
            count.setPadding(Ui.dp(a, 2), 0, 0, Ui.dp(a, 4));
            if (!land) root.addView(count, Ui.matchWrap());

            // grid
            int widthDp = (int) (a.getResources().getDisplayMetrics().widthPixels / a.getResources().getDisplayMetrics().density) - 40;
            if (Ui.isLandscape(a)) widthDp = Math.min(widthDp, 760);
            int span = Math.max(4, Math.min(9, widthDp / 62));
            grid = new RecyclerView(a);
            grid.setLayoutManager(new GridLayoutManager(a, span));
            grid.setHasFixedSize(true);
            grid.setItemViewCacheSize(40);
            adapter = new Adapter();
            grid.setAdapter(adapter);
            FrameLayoutHolder fh = new FrameLayoutHolder();
            LinearLayout body = Ui.row(a);
            detail = Ui.column(a);
            detail.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(a, 10), Ui.BORDER, 1));
            detail.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
            detail.setVisibility(View.GONE);
            if (land) {
                LinearLayout closeRow = Ui.row(a);
                closeRow.setGravity(Gravity.END);
                ImageView x = Ui.iconButton(a, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> closeDetail());
                closeRow.addView(x);
                detail.addView(closeRow, Ui.matchWrap());
            }
            buildDetail();
            if (land) {
                // landscape: sets + categories at the side, the grid takes all the rest, the search is a button,
                // the details of the chosen icon appear at the right only after choosing one
                int railDp = 150;
                android.widget.ScrollView rail = new android.widget.ScrollView(a);
                rail.setVerticalScrollBarEnabled(false);
                LinearLayout railCol = Ui.column(a);
                railCol.addView(setChips, Ui.matchWrap());
                View sep = new View(a);
                sep.setBackgroundColor(Ui.BORDER);
                railCol.addView(sep, Ui.margins(new LinearLayout.LayoutParams(-1, 1), a, 0, 6, 0, 6));
                railCol.addView(chips, Ui.matchWrap());
                rail.addView(railCol, Ui.matchWrap());
                body.addView(rail, new LinearLayout.LayoutParams(Ui.dp(a, railDp), -1));

                LinearLayout center = Ui.column(a);
                LinearLayout bar = Ui.row(a);
                bar.setGravity(Gravity.CENTER_VERTICAL);
                bar.setPadding(Ui.dp(a, 8), 0, 0, 0);
                bar.addView(count, Ui.weight(1));
                ImageView sb = Ui.iconButton(a, R.drawable.ic_s2_search, Tr.t("icn_search_btn", "Search"), v -> {
                    boolean show = sr.getVisibility() != View.VISIBLE;
                    sr.setVisibility(show ? View.VISIBLE : View.GONE);
                    if (show) {
                        search.requestFocus();
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(search, 0);
                    }
                });
                bar.addView(sb);
                center.addView(bar, Ui.matchWrap());
                sr.setVisibility(View.GONE);
                center.addView(sr, Ui.margins(Ui.matchWrap(), a, 8, 0, 0, 4));
                center.addView(fh.frame, new LinearLayout.LayoutParams(-1, 0, 1));
                body.addView(center, new LinearLayout.LayoutParams(0, -1, 1f));

                android.widget.ScrollView ds = new android.widget.ScrollView(a);
                ds.addView(detail, Ui.matchWrap());
                ds.setVisibility(View.GONE);
                detailHost = ds;
                body.addView(ds, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(a, 250), -1), a, 8, 0, 0, 0));
                root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
                widthDp = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.9f / a.getResources().getDisplayMetrics().density) - railDp - 40;
                landSpan = Math.max(5, Math.min(12, widthDp / 62));
                ((GridLayoutManager) grid.getLayoutManager()).setSpanCount(landSpan);
            } else {
                root.addView(fh.frame, new LinearLayout.LayoutParams(-1, 0, 1));
                root.addView(detail, Ui.margins(Ui.matchWrap(), a, 0, 6, 0, 4));
            }

            TextView credit = Ui.text(a, Tr.t("icn_credit", "Icons: Material Design Icons (Apache-2.0) and game-icons.net by Lorc, Delapouite & contributors (CC BY 3.0) · tap for licenses"), 10.5f, Ui.TEXT_3);
            credit.setPadding(Ui.dp(a, 2), Ui.dp(a, 2), 0, Ui.dp(a, 4));
            credit.setOnClickListener(v -> showLicenses());
            if (land) { credit.setSingleLine(true); credit.setEllipsize(android.text.TextUtils.TruncateAt.END); }
            root.addView(credit, Ui.matchWrap());

            refresh();

            String dlgTitle = Tr.t("icn_title", "Icons") + " (" + catalog.size() + ")";
            AlertDialog.Builder db = new AlertDialog.Builder(a).setView(root);
            if (land) {
                // landscape: no bottom Close button (saves height); an X next to the title instead
                LinearLayout tr = Ui.row(a);
                tr.setGravity(Gravity.CENTER_VERTICAL);
                tr.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 8), 0);
                TextView tt = Ui.text(a, dlgTitle, 18, Ui.TEXT);
                tt.setTypeface(Typeface.DEFAULT_BOLD);
                tr.addView(tt, Ui.weight(1));
                tr.addView(Ui.iconButton(a, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> { if (dialog != null) dialog.dismiss(); }));
                db.setCustomTitle(tr);
            } else {
                db.setTitle(dlgTitle).setPositiveButton(Tr.t("close", "Close"), null);
            }
            dialog = db.create();
            dialog.show();
            Window w = dialog.getWindow();
            if (w != null) {
                boolean ls = Ui.isLandscape(a);
                int h = (int) (a.getResources().getDisplayMetrics().heightPixels * (ls ? 0.96f : 0.86f));
                int wd = ls ? (int) (a.getResources().getDisplayMetrics().widthPixels * 0.9f) : ViewGroup.LayoutParams.MATCH_PARENT;
                w.setLayout(wd, h);
            }
        }

        /** The licence texts of both icon sets (shipped in assets/icons). */
        void showLicenses() {
            StringBuilder sb = new StringBuilder();
            for (String f : new String[]{"LICENSE-GAME-ICONS.txt", "LICENSE-MDI.txt"}) {
                try (java.io.InputStream in = a.getAssets().open("icons/" + f)) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) bo.write(buf, 0, r);
                    sb.append(bo.toString("UTF-8")).append("\n\n");
                } catch (Exception ignored) {
                }
            }
            Dialogs.longText(a, Tr.t("ui_license", "License"), sb.toString());
        }

        /** The grid and the "nothing found" text share one frame. */
        final class FrameLayoutHolder {
            final android.widget.FrameLayout frame = new android.widget.FrameLayout(a);
            FrameLayoutHolder() {
                frame.addView(grid, Ui.match());
                empty = Ui.text(a, Tr.t("icn_none", "No icon matches this search"), 13, Ui.TEXT_3);
                empty.setGravity(Gravity.CENTER);
                empty.setVisibility(View.GONE);
                frame.addView(empty, new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            }
        }

        String catLabel(String name) {
            String key = "icat_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
            return Tr.t(key, name);
        }

        void buildSetChips() {
            setChips.removeAllViews();
            String[] labels = {Tr.t("icn_all", "All"), Tr.t("icn_set_ui", "UI icons"), Tr.t("icn_set_game", "Game icons")};
            int[] values = {-1, 0, 1};
            for (int k = 0; k < labels.length; k++) {
                final int v = values[k];
                boolean on = v == set;
                TextView t = Ui.text(a, labels[k] + " (" + catalog.size(v) + ")", 12.5f, on ? Ui.TEXT : Ui.TEXT_2);
                t.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
                t.setBackground(Ui.rounded(on ? Ui.ACCENT_DARK : Ui.SURFACE_3, Ui.dp(a, 8)));
                t.setOnClickListener(x -> {
                    set = v;
                    int[] n = catalog.counts(set);
                    if (category >= 0 && n[category] == 0) category = -1;
                    buildSetChips();
                    buildChips();
                    refresh();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(land ? -1 : -2, -2);
                lp.setMarginEnd(land ? 0 : Ui.dp(a, 6));
                if (land) lp.bottomMargin = Ui.dp(a, 4);
                setChips.addView(t, lp);
            }
        }

        void buildChips() {
            chips.removeAllViews();
            addChip(Tr.t("icn_all", "All"), -1);
            List<String> cats = catalog.categories();
            int[] n = catalog.counts(set);
            for (int i = 0; i < cats.size(); i++) if (n[i] > 0) addChip(catLabel(cats.get(i)), i);
        }

        void addChip(String label, int cat) {
            TextView t = Ui.text(a, label, 12.5f, cat == category ? Ui.TEXT : Ui.TEXT_2);
            t.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
            t.setBackground(Ui.rounded(cat == category ? Ui.ACCENT : Ui.SURFACE_2, Ui.dp(a, 16), cat == category ? Ui.ACCENT : Ui.BORDER, 1));
            t.setOnClickListener(v -> {
                category = cat;
                buildChips();
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(land ? -1 : -2, -2);
            lp.setMarginEnd(land ? 0 : Ui.dp(a, 6));
            if (land) lp.bottomMargin = Ui.dp(a, 4);
            chips.addView(t, lp);
        }

        void refresh() {
            shown.clear();
            shown.addAll(catalog.search(query, category, set, 0));
            adapter.notifyDataSetChanged();
            grid.scrollToPosition(0);
            count.setText(shown.size() + " " + Tr.t("icn_count", "icons"));
            empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        }

        // ------------------------------------------------------------------ detail

        void buildDetail() {
            LinearLayout top = Ui.row(a);
            top.setGravity(Gravity.CENTER_VERTICAL);
            preview = new ImageView(a);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preview.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(a, 8)));
            int p = Ui.dp(a, 8);
            preview.setPadding(p, p, p, p);
            top.addView(preview, new LinearLayout.LayoutParams(Ui.dp(a, 72), Ui.dp(a, 72)));
            LinearLayout texts = Ui.column(a);
            texts.setPadding(Ui.dp(a, 10), 0, 0, 0);
            selTitle = Ui.text(a, "", 15, Ui.TEXT);
            selTitle.setTypeface(Typeface.DEFAULT_BOLD);
            selTitle.setMaxLines(1);
            selTags = Ui.text(a, "", 11.5f, Ui.TEXT_3);
            selTags.setMaxLines(3);
            texts.addView(selTitle);
            texts.addView(selTags);
            top.addView(texts, Ui.weight(1));
            detail.addView(top, Ui.matchWrap());

            // size chips
            detail.addView(Ui.sectionLabel(a, Tr.t("icn_size", "Size (px)")), Ui.matchWrap());
            HorizontalScrollView sh = new HorizontalScrollView(a);
            sh.setHorizontalScrollBarEnabled(false);
            sizeRow = Ui.row(a);
            sh.addView(sizeRow);
            detail.addView(sh, Ui.matchWrap());
            buildSizes();

            // colour
            detail.addView(Ui.sectionLabel(a, Tr.t("icn_color", "Colour filter")), Ui.matchWrap());
            HorizontalScrollView ch = new HorizontalScrollView(a);
            ch.setHorizontalScrollBarEnabled(false);
            colorRow = Ui.row(a);
            colorRow.setGravity(Gravity.CENTER_VERTICAL);
            ch.addView(colorRow);
            detail.addView(ch, Ui.matchWrap());
            buildColors();

            // name + import
            LinearLayout nr = Ui.row(a);
            nr.setGravity(Gravity.CENTER_VERTICAL);
            nameField = Ui.input(a, "", Tr.t("icn_name", "File name"));
            nameField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
                @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
                @Override public void afterTextChanged(Editable s) {
                    if (nameField.hasFocus()) customName = true;
                    updateImportLabel();
                }
            });
            nr.addView(nameField, Ui.weight(1));
            importBtn = Ui.button(a, R.drawable.ic_s2_download, Tr.t("import", "Import"), true, v -> doImport());
            importLabel = (TextView) importBtn.getChildAt(importBtn.getChildCount() - 1);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-2, Ui.dp(a, 38));
            il.setMarginStart(Ui.dp(a, 8));
            nr.addView(importBtn, il);
            detail.addView(nr, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 0));
        }

        void buildSizes() {
            sizeRow.removeAllViews();
            for (int s : SIZES) {
                TextView t = Ui.text(a, String.valueOf(s), 12.5f, s == size ? Ui.TEXT : Ui.TEXT_2);
                t.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
                t.setBackground(Ui.rounded(s == size ? Ui.ACCENT : Ui.SURFACE_3, Ui.dp(a, 14)));
                t.setOnClickListener(v -> {
                    size = s;
                    buildSizes();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMarginEnd(Ui.dp(a, 6));
                sizeRow.addView(t, lp);
            }
            TextView sh = Ui.text(a, Tr.t("icn_shadow", "Shadow"), 12.5f, shadow ? Ui.TEXT : Ui.TEXT_2);
            sh.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
            sh.setBackground(Ui.rounded(shadow ? Ui.ACCENT : Ui.SURFACE_3, Ui.dp(a, 14)));
            sh.setOnClickListener(v -> {
                shadow = !shadow;
                buildSizes();
                updatePreview();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginStart(Ui.dp(a, 8));
            sizeRow.addView(sh, lp);
        }

        void buildColors() {
            colorRow.removeAllViews();
            boolean preset = false;
            for (int c : COLORS) {
                if (c == color) preset = true;
                View sw = new View(a);
                sw.setBackground(Ui.rounded(c, Ui.dp(a, 15), c == color ? Ui.ACCENT : Ui.BORDER, Ui.dp(a, c == color ? 3 : 1)));
                sw.setOnClickListener(v -> {
                    color = c;
                    buildColors();
                    updatePreview();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30));
                lp.setMarginEnd(Ui.dp(a, 8));
                colorRow.addView(sw, lp);
            }
            // custom colour
            TextView custom = Ui.text(a, "…", 16, Ui.TEXT);
            custom.setGravity(Gravity.CENTER);
            custom.setBackground(Ui.rounded(preset ? Ui.SURFACE_3 : color, Ui.dp(a, 15), preset ? Ui.BORDER : Ui.ACCENT, Ui.dp(a, preset ? 1 : 3)));
            custom.setOnClickListener(v -> ColorPickerDialog.show(a, Tr.t("icn_color", "Colour filter"), ColorPickerDialog.toHex(color), null, hex -> {
                if (hex == null) return;
                color = ColorPickerDialog.parse(hex) | 0; // keeps the alpha the user chose
                buildColors();
                updatePreview();
            }));
            colorRow.addView(custom, new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30)));
        }

        /** Landscape: hide the details of the chosen icon and give the grid its columns back. */
        void closeDetail() {
            selected = null;
            detail.setVisibility(View.GONE);
            if (detailHost != null) detailHost.setVisibility(View.GONE);
            if (land) ((GridLayoutManager) grid.getLayoutManager()).setSpanCount(landSpan);
            adapter.notifyDataSetChanged();
        }

        void select(IconCatalog.Icon ic) {
            selected = ic;
            detail.setVisibility(View.VISIBLE);
            if (detailHost != null) detailHost.setVisibility(View.VISIBLE);
            if (land) ((GridLayoutManager) grid.getLayoutManager()).setSpanCount(4);
            selTitle.setText(ic.id);
            selTags.setText(ic.tags);
            if (!customName) {
                nameField.setText(ic.defaultName());
                customName = false;
            }
            updatePreview();
            updateImportLabel();
            adapter.notifyDataSetChanged();
        }

        void updatePreview() {
            if (selected == null) return;
            IconRenderer.Options o = new IconRenderer.Options().size(160).color(color).shadow(shadow);
            final IconCatalog.Icon ic = selected;
            POOL.execute(() -> {
                Bitmap b = IconRenderer.render(ic, o);
                Ui.ui(() -> {
                    if (ic == selected) preview.setImageBitmap(b);
                });
            });
        }

        String cleanName() {
            return selected == null ? "" : IconRenderer.cleanName(nameField.getText().toString(), selected);
        }

        void updateImportLabel() {
            if (selected == null) return;
            boolean exists = IconImport.exists(project, cleanName());
            importLabel.setText(exists ? Tr.t("icn_replace", "Replace") : Tr.t("import", "Import"));
            importBtn.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    Ui.rounded(exists ? Ui.WARNING : Ui.ACCENT, Ui.dp(a, 8)), null));
        }

        void doImport() {
            final IconCatalog.Icon ic = selected;
            if (ic == null) return;
            final String name = nameField.getText().toString();
            final IconRenderer.Options o = new IconRenderer.Options().size(size).color(color).shadow(shadow);
            POOL.execute(() -> {
                try {
                    IconImport.Result r = IconImport.importIcon(project, ic, name, o, true);
                    Ui.ui(() -> {
                        refreshProject();
                        Snack.show((r.replaced ? Tr.t("icn_replaced", "Replaced") : Tr.t("icn_imported", "Imported")) + ": " + r.useAs);
                        updateImportLabel();
                        if (cb != null) cb.on(r.useAs, r.file);
                        if (closeOnImport && dialog != null) dialog.dismiss();
                    });
                } catch (Throwable t) {
                    Ui.ui(() -> Ui.toast(a, "Icons: " + t.getMessage()));
                }
            });
        }

        /** New / replaced images must be seen by the open scene and the Files panel. */
        void refreshProject() {
            Bridge.gl(() -> {
                try {
                    Bridge.app().applyExternalChanges(Bridge.app().openSceneFingerprints());
                } catch (Throwable ignored) {
                }
            });
        }

        // ------------------------------------------------------------------ grid

        final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView iv = new ImageView(a);
                int pad = Ui.dp(a, 12);
                iv.setPadding(pad, pad, pad, pad);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int cell = parent.getMeasuredWidth() > 0 ? parent.getMeasuredWidth() / ((GridLayoutManager) grid.getLayoutManager()).getSpanCount() : Ui.dp(a, 56);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, Math.max(cell, Ui.dp(a, 48)));
                lp.setMargins(Ui.dp(a, 2), Ui.dp(a, 2), Ui.dp(a, 2), Ui.dp(a, 2));
                iv.setLayoutParams(lp);
                return new RecyclerView.ViewHolder(iv) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                ImageView iv = (ImageView) h.itemView;
                IconCatalog.Icon ic = shown.get(pos);
                boolean sel = ic == selected;
                iv.setBackground(Ui.ripple(sel ? Ui.ACCENT_SOFT : Ui.SURFACE_2, Ui.dp(a, 9)));
                if (sel) iv.setBackground(Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(a, 9), Ui.ACCENT, Ui.dp(a, 2)));
                iv.setContentDescription(ic.id);
                iv.setOnClickListener(v -> select(ic));
                iv.setOnLongClickListener(v -> {
                    Ui.toast(a, ic.id);
                    return true;
                });
                iv.setTag(ic.id);
                Bitmap cached = THUMBS.get(ic.id);
                if (cached != null) {
                    iv.setImageBitmap(cached);
                    return;
                }
                iv.setImageBitmap(null);
                POOL.execute(() -> {
                    Bitmap b = THUMBS.get(ic.id);
                    if (b == null) {
                        b = IconRenderer.render(ic, new IconRenderer.Options().size(96));
                        THUMBS.put(ic.id, b);
                    }
                    final Bitmap fb = b;
                    Ui.ui(() -> {
                        if (ic.id.equals(iv.getTag())) iv.setImageBitmap(fb);
                    });
                });
            }

            @Override
            public int getItemCount() {
                return shown.size();
            }
        }
    }
}
