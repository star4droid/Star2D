package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The project Market: community projects and games, browsed as a grid or a list. */
public class MarketActivity extends Activity {
    private static final String PREF_GRID = "market_grid";

    private final List<JsonObject> items = new ArrayList<>();
    private final List<JsonObject> featured = new ArrayList<>();
    private volatile boolean excl;
    private RecyclerView rv;
    private GridLayoutManager lm;
    private Adapter adapter;
    private TextView state;
    private EditText search;
    private ImageView viewToggle;
    private LinearLayout chips;
    private boolean grid = true, loading, more, mineMode;
    private String kind = "", sort = "";
    private int page = 1, gen;
    private TextView[] chipViews;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        grid = Ui.prefs(this).getBoolean(PREF_GRID, true);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        viewToggle = Ui.iconButton(this, grid ? R.drawable.ic_s2_view_list : R.drawable.ic_s2_view_grid, Tr.t("mk_view", "Grid / List"), v -> toggleView());
        ImageView upload = Ui.iconButton(this, R.drawable.ic_s2_upload, Tr.t("mk_upload", "Upload a project"), v -> upload());
        LinearLayout bar = SudaUi.topBar(this, Tr.t("mk_title", "Market"), viewToggle, upload);
        // "Experimental" tag next to the title
        TextView exp = Ui.text(this, Tr.t("mk_experimental", "Experimental"), 10.5f, 0xFF1B1300);
        exp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        exp.setPadding(Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        exp.setBackground(Ui.rounded(SudaUi.ORANGE, Ui.dp(this, 9)));
        bar.addView(exp, 2, Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 0, 0, 6, 0));
        Ui.fitIconRow(this, bar, 150);
        root.addView(bar, Ui.matchWrap());

        // search
        search = Ui.input(this, "", Tr.t("mk_search", "Search projects"));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v, a, e) -> { reload(); return true; });
        search.addTextChangedListener(new TextWatcher() {
            Runnable r;
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) {
                search.removeCallbacks(r);
                r = MarketActivity.this::reload;
                search.postDelayed(r, 450);
            }
        });
        LinearLayout sb = Ui.column(this);
        sb.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 4));
        sb.addView(search, Ui.matchWrap());
        // filters
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chips = Ui.row(this);
        hs.addView(chips);
        String[][] defs = {{"", Tr.t("mk_f_all", "All")}, {"project", Tr.t("mk_f_projects", "Projects")}, {"game", Tr.t("mk_f_games", "Games")},
                {"popular", Tr.t("mk_f_popular", "Popular")}, {"mine", Tr.t("mk_f_mine", "Mine")}};
        chipViews = new TextView[defs.length];
        for (int i = 0; i < defs.length; i++) {
            final String key = defs[i][0];
            TextView c = Ui.text(this, defs[i][1], 13, Ui.TEXT);
            c.setGravity(Gravity.CENTER);
            c.setSingleLine(true);
            c.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
            c.setOnClickListener(v -> pick(key));
            chipViews[i] = c;
            chips.addView(c, Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 0, 6, 8, 6));
        }
        sb.addView(hs, Ui.matchWrap());
        root.addView(sb, Ui.matchWrap());

        // list
        FrameLayout body = new FrameLayout(this);
        rv = new RecyclerView(this);
        rv.setClipToPadding(false);
        rv.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 20));
        adapter = new Adapter();
        lm = new GridLayoutManager(this, spans());
        lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return stripOn() && position == 0 ? lm.getSpanCount() : 1; }
        });
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                if (dy > 0 && more && !loading && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 4) load(false);
            }
        });
        body.addView(rv, Ui.match());
        state = Ui.text(this, "", 14, Ui.TEXT_3);
        state.setGravity(Gravity.CENTER);
        state.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        body.addView(state, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        paintChips();
        reload();
    }

    @Override protected void onResume() { super.onResume(); if (!items.isEmpty() || !loading) { /* refresh after edits/uploads */ if (stale) { stale = false; reload(); } } }
    private boolean stale;
    /** Set by the detail / edit screens when something changed. */
    public static volatile boolean dirty;
    @Override protected void onStart() { super.onStart(); if (dirty) { dirty = false; stale = true; } }

    @Override public void onConfigurationChanged(android.content.res.Configuration c) { super.onConfigurationChanged(c); lm.setSpanCount(spans()); }

    private int spans() {
        if (!grid) return 1;
        float w = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        return Math.max(2, (int) (w / 165));
    }

    private void toggleView() {
        grid = !grid;
        Ui.prefs(this).edit().putBoolean(PREF_GRID, grid).apply();
        viewToggle.setImageResource(grid ? R.drawable.ic_s2_view_list : R.drawable.ic_s2_view_grid);
        lm.setSpanCount(spans());
        adapter.notifyDataSetChanged();
    }

    private void pick(String key) {
        mineMode = "mine".equals(key);
        sort = "popular".equals(key) ? "popular" : "";
        kind = "project".equals(key) || "game".equals(key) ? key : "";
        if (mineMode && !Suda3D.loggedIn(this)) { Paywall.login(this, () -> pick("mine")); return; }
        paintChips();
        reload();
    }

    private void paintChips() {
        String cur = mineMode ? "mine" : !sort.isEmpty() ? "popular" : kind;
        String[] keys = {"", "project", "game", "popular", "mine"};
        for (int i = 0; i < chipViews.length; i++) {
            boolean on = keys[i].equals(cur);
            chipViews[i].setBackground(on ? Ui.rounded(Ui.ACCENT, Ui.dp(this, 16)) : Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 16), Ui.BORDER, 1));
        }
    }

    private void upload() {
        if (!Suda3D.loggedIn(this)) { Paywall.login(this, this::upload); return; }
        startActivity(new Intent(this, MarketEditActivity.class));
    }

    private void reload() { load(true); }

    private void load(boolean fresh) {
        if (fresh) { page = 1; more = false; items.clear(); adapter.notifyDataSetChanged(); }
        final int my = ++gen;
        loading = true;
        state.setText(fresh ? Tr.t("mk_loading", "Loading…") : "");
        final String q = search.getText().toString(), k = kind, so = sort;
        final int pg = page;
        final boolean mineReq = mineMode;
        final boolean plain = k.isEmpty() && so.isEmpty() && !mineReq && q.trim().isEmpty();
        Suda3D.IO.execute(() -> {
            try {
                if (plain && pg == 1) {
                    // Editor's Choice (own strip on top); the older server answers without it and the list then keeps them in place
                    try {
                        List<JsonObject> f = new ArrayList<>();
                        for (JsonElement e : MarketApi.data(CommunityApi.featured(this))) f.add(e.getAsJsonObject());
                        excl = !f.isEmpty();
                        runOnUiThread(() -> { featured.clear(); featured.addAll(f); });
                    } catch (Exception ignored) { excl = false; runOnUiThread(featured::clear); }
                } else if (!plain) { excl = false; runOnUiThread(featured::clear); }
                JsonObject o = mineReq ? MarketApi.mine(this) : MarketApi.list(this, q, k, so, pg, 0, plain && excl);
                JsonArray arr = MarketApi.data(o);
                int last = (int) Suda3D.num(o, "last_page", 1);
                runOnUiThread(() -> {
                    if (my != gen || isFinishing()) return;
                    for (JsonElement e : arr) items.add(e.getAsJsonObject());
                    adapter.notifyDataSetChanged();
                    page = pg + 1;
                    more = !mineReq && pg < last;
                    loading = false;
                    state.setText(items.isEmpty() ? Tr.t(mineReq ? "mk_empty_mine" : "mk_empty", mineReq ? "You have not uploaded anything yet." : "Nothing here yet.") : "");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (my != gen || isFinishing()) return;
                    loading = false;
                    state.setText(items.isEmpty() ? Suda3D.message(e) + "\n\n" + Tr.t("mk_tap_retry", "Tap to retry") : "");
                    state.setOnClickListener(v -> { state.setOnClickListener(null); reload(); });
                });
            }
        });
    }

    static String sizeText(double bytes) {
        return bytes >= 1048576 ? String.format(Locale.US, "%.1f MB", bytes / 1048576) : String.format(Locale.US, "%.0f KB", Math.max(1, bytes / 1024));
    }

    // ------------------------------------------------------------ adapter

    /** The Editor's Choice strip shows only in the plain "All" view (no search, filter or sort). */
    private boolean stripOn() { return !featured.isEmpty() && kind.isEmpty() && sort.isEmpty() && !mineMode && search.getText().toString().trim().isEmpty(); }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private int off() { return stripOn() ? 1 : 0; }
        @Override public int getItemCount() { return items.size() + off(); }
        @Override public int getItemViewType(int pos) { return pos < off() ? 1 : 0; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int type) {
            FrameLayout f = new FrameLayout(MarketActivity.this);
            f.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(f) {};
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            FrameLayout f = (FrameLayout) h.itemView;
            f.removeAllViews();
            if (getItemViewType(pos) == 1) { f.addView(strip()); return; }
            f.addView(card(items.get(pos - off()), grid, false));
        }
    }

    /** "Editor's Choice": a horizontal strip, part of the same vertical scroll. */
    private View strip() {
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), 0, Ui.dp(this, 6));
        LinearLayout head = Ui.row(this);
        head.addView(Social.plainIcon(this, R.drawable.ic_s2_star_badge, 20));
        TextView t = Ui.text(this, Tr.t("sc_editor_choice", "Editor's Choice"), 14, 0xFFFFB300);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(this, 6), 0, 0, 0);
        head.addView(t);
        col.addView(head);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        hs.addView(row);
        for (JsonObject m : featured) {
            View c = card(m, true, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 150), -2);
            row.addView(c, lp);
        }
        col.addView(hs, Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 0));
        return col;
    }

    /** One store card. Editor's Choice cards have their own colour and a small outlined-star badge in the corner (never over the icon). */
    private View card(JsonObject m, boolean gridMode, boolean inStrip) {
        boolean ed = Suda3D.bool(m, "editor_choice");
        FrameLayout wrap = new FrameLayout(this);
        int mg = Ui.dp(this, 5);
        wrap.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        LinearLayout card = Ui.column(this);
        int pad = Ui.dp(this, 10);
        card.setOrientation(gridMode ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        card.setGravity(gridMode ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                ed ? Ui.rounded(0xFF332A14, Ui.dp(this, 12), 0xFF9A7418, Ui.dp(this, 1)) : Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 12), Ui.BORDER, 1), null));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -2);
        clp.setMargins(mg, mg + Ui.dp(this, ed ? 6 : 0), mg + Ui.dp(this, ed ? 4 : 0), mg);
        wrap.addView(card, clp);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(this, 12)));
        icon.setClipToOutline(true);
        icon.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        int isz = Ui.dp(this, gridMode ? 88 : 60);
        long id = (long) Suda3D.num(m, "id", 0);
        if (m.has("has_icon") && m.get("has_icon").getAsBoolean())
            Glide.with(this).load(MarketApi.iconUrl(this, id, Suda3D.str(m, "updated_at"))).centerCrop().into(icon);
        else { icon.setImageResource(R.drawable.ic_s2_gamepad); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE); icon.setPadding(isz / 4, isz / 4, isz / 4, isz / 4); icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_3)); }
        card.addView(icon, new LinearLayout.LayoutParams(isz, isz));

        LinearLayout texts = Ui.column(this);
        texts.setGravity(gridMode ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        TextView name = Ui.text(this, Suda3D.str(m, "name"), 14.5f, Ui.TEXT);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(gridMode ? Gravity.CENTER : Gravity.START);
        texts.addView(name, Ui.matchWrap());
        String author = Suda3D.str(m, "author");
        if (!author.isEmpty()) {
            TextView au = Ui.text(this, Tr.t("sc_by", "by") + " " + author, 11.5f, 0xFF64B5F6);
            au.setMaxLines(1);
            au.setEllipsize(android.text.TextUtils.TruncateAt.END);
            au.setGravity(gridMode ? Gravity.CENTER : Gravity.START);
            long aid = (long) Suda3D.num(m, "author_id", 0);
            if (aid > 0) au.setOnClickListener(v -> Social.openProfile(this, aid));
            texts.addView(au, Ui.matchWrap());
        }
        boolean game = "game".equals(Suda3D.str(m, "kind"));
        String meta = (game ? Tr.t("mk_game", "Game") : Tr.t("mk_project", "Project")) + "  •  ↓ " + (int) Suda3D.num(m, "downloads", 0) + "  •  " + sizeText(Suda3D.num(m, "size_bytes", 0));
        TextView mt = Ui.text(this, meta, 11.5f, Ui.TEXT_3);
        mt.setMaxLines(1);
        mt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mt.setGravity(gridMode ? Gravity.CENTER : Gravity.START);
        texts.addView(mt, Ui.matchWrap());
        TextView sc = Ui.text(this, "★ " + (int) Suda3D.num(m, "stars", 0) + "   💬 " + (int) Suda3D.num(m, "comments", 0), 11.5f, 0xFFFFC107);
        sc.setGravity(gridMode ? Gravity.CENTER : Gravity.START);
        texts.addView(sc, Ui.matchWrap());
        if (!gridMode) {
            String d = Suda3D.str(m, "description");
            if (!d.isEmpty()) {
                TextView dt = Ui.text(this, d, 12.5f, Ui.TEXT_2);
                dt.setMaxLines(2);
                dt.setEllipsize(android.text.TextUtils.TruncateAt.END);
                texts.addView(dt, Ui.matchWrap());
            }
        }
        boolean mine = m.has("mine") && m.get("mine").getAsBoolean();
        if (mine || "hidden".equals(Suda3D.str(m, "status"))) {
            TextView tag = Ui.text(this, "hidden".equals(Suda3D.str(m, "status")) ? Tr.t("mk_hidden", "Hidden by admin") : Tr.t("mk_yours", "Yours"), 11, SudaUi.ORANGE);
            tag.setGravity(gridMode ? Gravity.CENTER : Gravity.START);
            texts.addView(tag, Ui.matchWrap());
        }
        LinearLayout.LayoutParams tl = gridMode ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1);
        tl.topMargin = gridMode ? Ui.dp(this, 8) : 0;
        tl.setMarginStart(gridMode ? 0 : Ui.dp(this, 12));
        card.addView(texts, tl);
        card.setOnClickListener(v -> startActivity(new Intent(this, MarketDetailActivity.class).putExtra("json", m.toString())));
        if (ed) {
            // the badge sits on the card's corner, away from the icon
            ImageView badge = new ImageView(this);
            badge.setImageResource(R.drawable.ic_s2_star_badge);
            badge.setBackgroundResource(R.drawable.editor_badge_bg);
            int p2 = Ui.dp(this, 4);
            badge.setPadding(p2, p2, p2, p2);
            wrap.addView(badge, new FrameLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26), Gravity.TOP | Gravity.END));
        }
        return wrap;
    }
}
