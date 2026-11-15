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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.star4droid.star2d.evo.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Particle effects browser for the "file" property of a 2D PARTICLE (.p) and the "Particle File" of a 3D particle
 * element (.p3d): the effects of the project plus the built in library (assets/fxlib: a picture of every effect),
 * sections, search, grid or list (remembered). Choosing a library effect copies it with its pictures into the project
 * files (files/fx/CATEGORY/ or files/fx3d/CATEGORY/; an existing copy is kept) and returns its path ("/fx/fire/campfire.p").
 */
public final class ParticleBrowser {
    private ParticleBrowser() {}

    /** {@code path}: the value for the property, relative to the project's files folder, starting with "/". */
    public interface OnPicked { void on(String path); }

    private static final String PREF_LIST = "fxBrowserList";
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<>(300);
    private static JSONObject index;

    /** Opens the browser. {@code threeD}: .p3d effects (3D scene) instead of .p (2D). {@code current}: the property's value. */
    public static void show(Activity a, File filesDir, boolean threeD, String current, OnPicked cb) {
        if (filesDir == null) {
            Ui.toast(a, Tr.t("icn_no_project", "Open a project first"));
            return;
        }
        try {
            new Browser(a, filesDir, threeD, current, cb).open();
        } catch (Throwable t) {
            Ui.toast(a, "Particles: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ data

    static final class Item {
        String cat, key, en, ar;   // library effect (cat = its section)
        File file;                 // effect of the project (key = path relative to the files folder)
        String rel;                // path for the property ("/fx/fire/campfire.p")
        boolean header;
        String title;              // header text
        int count;                 // header: effects in the section
    }

    static final class Section {
        String id, title;
        final ArrayList<Item> items = new ArrayList<>();
    }

    private static synchronized JSONObject index(Activity a) throws Exception {
        if (index == null) {
            try (InputStream in = a.getAssets().open("fxlib/index.json")) {
                index = new JSONObject(readAll(in));
            }
        }
        return index;
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) != -1) bo.write(b, 0, r);
        return bo.toString("UTF-8");
    }

    static boolean arabic() {
        return "ar".equals(Tr.lang());
    }

    // ------------------------------------------------------------------ dialog

    private static final class Browser {
        final Activity a;
        final File files;
        final boolean d3;
        final String kind, ext;
        final String current;
        final OnPicked cb;
        final Handler main = new Handler(Looper.getMainLooper());
        final ArrayList<Section> sections = new ArrayList<>();
        final ArrayList<Item> shown = new ArrayList<>();

        String filter = "";       // "" = all sections, else a section id
        String query = "";
        boolean list;
        boolean land;
        int gridSpan, listSpan;

        AlertDialog dialog;
        RecyclerView rv;
        GridLayoutManager glm;
        LinearLayout chips;
        TextView count, empty;
        ImageView modeBtn;
        Adapter adapter;
        Runnable pendingSearch;

        Browser(Activity a, File files, boolean d3, String current, OnPicked cb) throws Exception {
            this.a = a;
            this.files = files;
            this.d3 = d3;
            this.kind = d3 ? "3d" : "2d";
            this.ext = d3 ? ".p3d" : ".p";
            this.current = current == null ? "" : current.replace('\\', '/').replace(com.star4droid.template.Utils.Utils.seperator, "/");
            this.cb = cb;
            loadSections();
        }

        void loadSections() throws Exception {
            boolean ar = arabic();
            // the project's own effects first
            Section proj = new Section();
            proj.id = "project";
            proj.title = Tr.t("fx_in_project", "In this project");
            ArrayList<File> found = new ArrayList<>();
            collect(files, found, 0);
            for (File f : found) {
                Item it = new Item();
                it.file = f;
                String rel = f.getAbsolutePath().substring(files.getAbsolutePath().length()).replace('\\', '/');
                it.rel = rel.startsWith("/") ? rel : "/" + rel;
                String n = f.getName();
                it.en = it.ar = n.substring(0, n.lastIndexOf('.'));
                it.key = it.rel;
                proj.items.add(it);
            }
            if (!proj.items.isEmpty()) sections.add(proj);
            // the library
            JSONArray cats = index(a).getJSONArray(kind);
            for (int i = 0; i < cats.length(); i++) {
                JSONObject c = cats.getJSONObject(i);
                Section s = new Section();
                s.id = c.getString("cat");
                s.title = ar ? c.optString("ar", c.getString("en")) : c.getString("en");
                JSONArray its = c.getJSONArray("items");
                for (int k = 0; k < its.length(); k++) {
                    JSONObject o = its.getJSONObject(k);
                    Item it = new Item();
                    it.cat = s.id;
                    it.key = o.getString("key");
                    it.en = o.getString("en");
                    it.ar = o.optString("ar", it.en);
                    it.rel = "/" + (d3 ? "fx3d" : "fx") + "/" + it.cat + "/" + it.key + ext;
                    s.items.add(it);
                }
                sections.add(s);
            }
        }

        void collect(File dir, List<File> out, int depth) {
            File[] l = dir.listFiles();
            if (l == null || depth > 6) return;
            java.util.Arrays.sort(l);
            for (File f : l) {
                if (f.isDirectory()) collect(f, out, depth + 1);
                else {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (d3 ? n.endsWith(".p3d") : (n.endsWith(".p") || n.endsWith(".particle"))) out.add(f);
                }
            }
        }

        String name(Item it) {
            return arabic() ? it.ar : it.en;
        }

        String sectionTitle(String id) {
            for (Section s : sections) if (s.id.equals(id)) return s.title;
            return id;
        }

        void open() {
            land = Ui.isLandscape(a);
            list = Ui.prefs(a).getBoolean(PREF_LIST, false);
            float dens = a.getResources().getDisplayMetrics().density;
            int screenW = a.getResources().getDisplayMetrics().widthPixels;
            int dlgWdp = (int) ((land ? screenW * 0.94f : screenW) / dens) - (land ? 170 + 40 : 36);
            gridSpan = Math.max(3, Math.min(10, dlgWdp / 104));
            listSpan = Math.max(1, Math.min(3, dlgWdp / 300));

            LinearLayout root = Ui.column(a);
            root.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), Ui.dp(a, 4));

            // search field
            LinearLayout sr = Ui.row(a);
            sr.setBackground(Ui.rounded(0xFF121417, Ui.dp(a, 9), Ui.BORDER, 1));
            sr.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 4), 0);
            sr.setGravity(Gravity.CENTER_VERTICAL);
            sr.addView(Ui.smallIcon(a, R.drawable.ic_s2_search, Ui.TEXT_3, 18));
            EditText search = new EditText(a);
            search.setHint(Tr.t("fx_search", "Search: fire, rain, magic, explosion..."));
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

            // title bar: title, (landscape: the search field), count, grid/list, close
            LinearLayout bar = Ui.row(a);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Ui.dp(a, 16), Ui.dp(a, land ? 4 : 8), Ui.dp(a, 8), Ui.dp(a, land ? 2 : 4));
            TextView title = Ui.text(a, Tr.t("fx_title", "Particle effects") + (d3 ? " · 3D" : " · 2D"), land ? 16 : 18, Ui.TEXT);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            if (land) {
                bar.addView(title);
                bar.addView(sr, Ui.margins(new LinearLayout.LayoutParams(0, -2, 1f), a, 12, 0, 6, 0));
            } else {
                bar.addView(title, Ui.weight(1));
            }
            modeBtn = Ui.iconButton(a, list ? R.drawable.ic_s2_view_grid : R.drawable.ic_s2_view_list,
                    list ? Tr.t("fx_grid", "Grid") : Tr.t("fx_list", "List"), v -> toggleMode());
            bar.addView(modeBtn);
            bar.addView(Ui.iconButton(a, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> { if (dialog != null) dialog.dismiss(); }));

            // sections: a row of chips (portrait) or a list at the side (landscape)
            chips = land ? Ui.column(a) : Ui.row(a);
            buildChips();

            count = Ui.text(a, "", 11.5f, Ui.TEXT_3);
            count.setPadding(Ui.dp(a, 2), Ui.dp(a, 2), 0, Ui.dp(a, 4));

            rv = new RecyclerView(a);
            glm = new GridLayoutManager(a, list ? listSpan : gridSpan);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override public int getSpanSize(int position) {
                    return position < shown.size() && shown.get(position).header ? glm.getSpanCount() : 1;
                }
            });
            rv.setLayoutManager(glm);
            rv.setItemViewCacheSize(30);
            adapter = new Adapter();
            rv.setAdapter(adapter);
            rv.setClipToPadding(false);
            rv.setPadding(0, 0, 0, Ui.dp(a, 8));
            FrameLayout frame = new FrameLayout(a);
            frame.addView(rv, new FrameLayout.LayoutParams(-1, -1));
            empty = Ui.text(a, Tr.t("fx_none", "No effect matches this search"), 13, Ui.TEXT_3);
            empty.setGravity(Gravity.CENTER);
            empty.setVisibility(View.GONE);
            frame.addView(empty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

            if (land) {
                // landscape: the sections at the side, all the remaining height for the effects
                LinearLayout body = Ui.row(a);
                ScrollView rail = new ScrollView(a);
                rail.setVerticalScrollBarEnabled(false);
                rail.addView(chips, Ui.matchWrap());
                body.addView(rail, new LinearLayout.LayoutParams(Ui.dp(a, 160), -1));
                LinearLayout center = Ui.column(a);
                center.setPadding(Ui.dp(a, 8), 0, 0, 0);
                center.addView(count, Ui.matchWrap());
                center.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
                body.addView(center, new LinearLayout.LayoutParams(0, -1, 1f));
                root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
            } else {
                root.addView(sr, Ui.matchWrap());
                HorizontalScrollView hs = new HorizontalScrollView(a);
                hs.setHorizontalScrollBarEnabled(false);
                chips.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 6));
                hs.addView(chips);
                root.addView(hs, Ui.matchWrap());
                root.addView(count, Ui.matchWrap());
                root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
            }
            TextView note = Ui.text(a, Tr.t("fx_note", "Choosing a library effect copies it (with its pictures) into the project files."), 10.5f, Ui.TEXT_3);
            note.setSingleLine(true);
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

        void toggleMode() {
            list = !list;
            Ui.prefs(a).edit().putBoolean(PREF_LIST, list).apply();
            modeBtn.setImageResource(list ? R.drawable.ic_s2_view_grid : R.drawable.ic_s2_view_list);
            modeBtn.setContentDescription(list ? Tr.t("fx_grid", "Grid") : Tr.t("fx_list", "List"));
            int first = glm.findFirstVisibleItemPosition();
            glm.setSpanCount(list ? listSpan : gridSpan);
            adapter.notifyDataSetChanged();
            if (first > 0) glm.scrollToPositionWithOffset(first, 0);
        }

        void buildChips() {
            chips.removeAllViews();
            int total = 0;
            for (Section s : sections) total += s.items.size();
            addChip(Tr.t("icn_all", "All") + " (" + total + ")", "");
            for (Section s : sections) addChip(s.title + " (" + s.items.size() + ")", s.id);
        }

        void addChip(String label, String id) {
            boolean on = id.equals(filter);
            TextView t = Ui.text(a, label, 12.5f, on ? Ui.TEXT : Ui.TEXT_2);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(Ui.dp(a, 12), Ui.dp(a, land ? 7 : 6), Ui.dp(a, 12), Ui.dp(a, land ? 7 : 6));
            t.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_2, Ui.dp(a, land ? 9 : 16), on ? Ui.ACCENT : Ui.BORDER, 1));
            t.setOnClickListener(v -> {
                filter = id;
                buildChips();
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(land ? -1 : -2, -2);
            if (land) lp.bottomMargin = Ui.dp(a, 4);
            else lp.setMarginEnd(Ui.dp(a, 6));
            chips.addView(t, lp);
        }

        boolean matches(Item it, Section s, String[] words) {
            if (words.length == 0) return true;
            String hay = (it.en + " " + it.ar + " " + it.key + " " + s.title + " " + s.id).toLowerCase(Locale.ROOT).replace('_', ' ');
            for (String w : words) if (!hay.contains(w)) return false;
            return true;
        }

        void refresh() {
            shown.clear();
            String q = query.trim().toLowerCase(Locale.ROOT);
            String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
            int n = 0;
            for (Section s : sections) {
                if (!filter.isEmpty() && !filter.equals(s.id)) continue;
                ArrayList<Item> hit = new ArrayList<>();
                for (Item it : s.items) if (matches(it, s, words)) hit.add(it);
                if (hit.isEmpty()) continue;
                Item h = new Item();
                h.header = true;
                h.title = s.title;
                h.count = hit.size();
                shown.add(h);
                shown.addAll(hit);
                n += hit.size();
            }
            adapter.notifyDataSetChanged();
            rv.scrollToPosition(0);
            count.setText(n + " " + Tr.t("fx_count", "effects"));
            empty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        }

        // -------------------------------------------------------------- choosing

        void pick(Item it) {
            if (it.file != null) {
                done(it.rel);
                return;
            }
            final File dst = new File(files, it.rel.substring(1));
            POOL.execute(() -> {
                String err = null;
                try {
                    install(it, dst);
                } catch (Throwable t) {
                    err = t.getMessage();
                }
                final String e = err;
                main.post(() -> {
                    if (e != null) Ui.toast(a, "Particles: " + e);
                    else done(it.rel);
                });
            });
        }

        void done(String rel) {
            if (dialog != null) dialog.dismiss();
            cb.on(rel);
        }

        /** The effect and the pictures it uses, unless the project already has them (an edited copy is kept). */
        void install(Item it, File dst) throws Exception {
            String dir = "fxlib/" + kind + "/" + it.cat + "/";
            File folder = dst.getParentFile();
            if (folder != null) folder.mkdirs();
            String text;
            try (InputStream in = a.getAssets().open(dir + it.key + ext)) {
                text = readAll(in);
            }
            if (!dst.isFile()) write(text.getBytes("UTF-8"), dst);
            for (String pic : pictures(text)) {
                File f = new File(folder, pic);
                if (f.isFile()) continue;
                try (InputStream in = a.getAssets().open(dir + pic); OutputStream out = new FileOutputStream(f)) {
                    byte[] b = new byte[8192];
                    int r;
                    while ((r = in.read(b)) != -1) out.write(b, 0, r);
                } catch (java.io.FileNotFoundException ignored) {
                    // a built in picture (@glow) or one the effect does not ship
                }
            }
        }

        /** Picture files named by an effect: the "- Image Paths -" lines of a .p, the "img" names of a .p3d. */
        List<String> pictures(String text) {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            if (d3) {
                Matcher m = Pattern.compile("\"img\"\\s*:\\s*(\\[[^\\]]*\\]|\"[^\"]*\")").matcher(text);
                while (m.find()) {
                    Matcher n = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
                    while (n.find()) {
                        String s = n.group(1).trim();
                        if (s.isEmpty() || s.startsWith("@")) continue;
                        out.add(s.toLowerCase(Locale.ROOT).endsWith(".png") ? s : s + ".png");
                    }
                }
            } else {
                for (String line : text.split("\n")) {
                    String s = line.trim();
                    if (s.toLowerCase(Locale.ROOT).endsWith(".png")) out.add(s.replace('\\', '/').substring(s.replace('\\', '/').lastIndexOf('/') + 1));
                }
            }
            return new ArrayList<>(out);
        }

        void write(byte[] data, File f) throws Exception {
            try (OutputStream out = new FileOutputStream(f)) {
                out.write(data);
            }
        }

        // -------------------------------------------------------------- views

        static final int T_HEADER = 0, T_GRID = 1, T_LIST = 2;

        final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
            @Override public int getItemCount() { return shown.size(); }

            @Override public int getItemViewType(int position) {
                return shown.get(position).header ? T_HEADER : (list ? T_LIST : T_GRID);
            }

            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                View v;
                if (type == T_HEADER) {
                    TextView t = Ui.text(a, "", 13, Ui.TEXT);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                    t.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 6));
                    t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                    v = t;
                } else if (type == T_GRID) {
                    LinearLayout cell = Ui.column(a);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    cell.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 6));
                    ImageView iv = new ImageView(a);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setId(android.R.id.icon);
                    iv.setBackground(Ui.rounded(0xFF0E1118, Ui.dp(a, 8)));
                    iv.setClipToOutline(true);
                    cell.addView(iv, new LinearLayout.LayoutParams(-1, Ui.dp(a, 84)));
                    TextView t = Ui.text(a, "", 11.5f, Ui.TEXT);
                    t.setId(android.R.id.text1);
                    t.setGravity(Gravity.CENTER);
                    t.setMaxLines(2);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setPadding(0, Ui.dp(a, 4), 0, 0);
                    cell.addView(t, Ui.matchWrap());
                    RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
                    lp.setMargins(Ui.dp(a, 2), Ui.dp(a, 2), Ui.dp(a, 2), Ui.dp(a, 2));
                    cell.setLayoutParams(lp);
                    v = cell;
                } else {
                    LinearLayout row = Ui.row(a);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(a, 6), Ui.dp(a, 5), Ui.dp(a, 8), Ui.dp(a, 5));
                    ImageView iv = new ImageView(a);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setId(android.R.id.icon);
                    iv.setBackground(Ui.rounded(0xFF0E1118, Ui.dp(a, 7)));
                    iv.setClipToOutline(true);
                    row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 52)));
                    LinearLayout texts = Ui.column(a);
                    texts.setPadding(Ui.dp(a, 10), 0, 0, 0);
                    TextView t = Ui.text(a, "", 14, Ui.TEXT);
                    t.setId(android.R.id.text1);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    TextView s = Ui.text(a, "", 11.5f, Ui.TEXT_3);
                    s.setId(android.R.id.text2);
                    s.setSingleLine(true);
                    s.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    texts.addView(t);
                    texts.addView(s);
                    row.addView(texts, Ui.weight(1));
                    RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
                    lp.setMargins(Ui.dp(a, 2), Ui.dp(a, 2), Ui.dp(a, 2), Ui.dp(a, 2));
                    row.setLayoutParams(lp);
                    v = row;
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
                boolean sel = it.rel.equalsIgnoreCase(current);
                h.itemView.setBackground(Ui.ripple(sel ? Ui.ACCENT_SOFT : Ui.SURFACE_2, Ui.dp(a, 10)));
                h.itemView.setOnClickListener(v -> pick(it));
                TextView t = h.itemView.findViewById(android.R.id.text1);
                t.setText(name(it));
                t.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                TextView sub = h.itemView.findViewById(android.R.id.text2);
                if (sub != null) {
                    boolean has = it.file != null || new File(files, it.rel.substring(1)).isFile();
                    sub.setText(it.file != null ? it.rel : sectionTitle(it.cat) + (has ? "  ·  " + Tr.t("fx_added", "in the project") : ""));
                }
                ImageView iv = h.itemView.findViewById(android.R.id.icon);
                bindThumb(iv, it);
            }
        }

        void bindThumb(ImageView iv, Item it) {
            int pad = it.file != null ? Ui.dp(a, list ? 12 : 22) : 0;
            iv.setPadding(pad, pad, pad, pad);
            if (it.file != null) {
                iv.setTag(null);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageResource(R.drawable.ic_s2_particle);
                iv.setColorFilter(Ui.TEXT_2);
                return;
            }
            iv.clearColorFilter();
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            final String asset = "fxlib/" + kind + "/" + it.cat + "/" + it.key + ".webp";
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
                    if (!asset.equals(iv.getTag())) return;
                    if (fb != null) iv.setImageBitmap(fb);
                    else {
                        iv.setImageResource(R.drawable.ic_s2_particle);
                        iv.setColorFilter(Ui.TEXT_2);
                    }
                });
            });
        }
    }
}
