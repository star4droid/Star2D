package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Community feed: one vertical scroll (composer, pinned strip, then the posts, loaded page by page). */
public class CommunityActivity extends Activity implements PostView.Host {
    private static final int T_COMPOSER = 0, T_STRIP = 1, T_POST = 2, T_FOOT = 3;

    private final List<JsonObject> posts = new ArrayList<>();
    private final List<JsonObject> pinned = new ArrayList<>();
    private RecyclerView rv;
    private LinearLayoutManager lm;
    private TextView state, bellBadge;
    private boolean loading, more;
    private long next;
    private int gen;
    private final RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new Adapter();
    public static volatile boolean dirty;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout support = Ui.row(this);
        support.setBackground(Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(this, 18), Ui.ACCENT, Ui.dp(this, 1)));
        support.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 12), Ui.dp(this, 5));
        support.addView(Ui.smallIcon(this, R.drawable.ic_s2_handshake, 0xFF64B5F6, 18));
        TextView st = Ui.text(this, Tr.t("sc_support", "Support"), 13, 0xFF90CAF9);
        st.setTypeface(Typeface.DEFAULT_BOLD);
        st.setSingleLine(true);
        st.setPadding(Ui.dp(this, 6), 0, 0, 0);
        support.addView(st);
        support.setOnClickListener(v -> startActivity(new Intent(this, SupportActivity.class)));

        FrameLayout bellWrap = new FrameLayout(this);
        bellWrap.addView(Ui.iconButton(this, R.drawable.ic_s2_bell, Tr.t("sc_notifications", "Notifications"), v -> startActivity(new Intent(this, NotificationsActivity.class))));
        bellBadge = Ui.text(this, "", 9.5f, 0xFFFFFFFF);
        bellBadge.setTypeface(Typeface.DEFAULT_BOLD);
        bellBadge.setGravity(Gravity.CENTER);
        bellBadge.setBackground(Ui.rounded(Ui.DANGER, Ui.dp(this, 9)));
        bellBadge.setMinWidth(Ui.dp(this, 16));
        bellBadge.setPadding(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        bellBadge.setVisibility(View.GONE);
        bellWrap.addView(bellBadge, new FrameLayout.LayoutParams(-2, Ui.dp(this, 16), Gravity.TOP | Gravity.END));
        ImageView refresh = Ui.iconButton(this, R.drawable.ic_s2_refresh, Tr.t("refresh", "Refresh"), v -> reload());
        LinearLayout bar = SudaUi.topBar(this, Tr.t("sc_community", "Community"), support, bellWrap, refresh);
        ((LinearLayout.LayoutParams) support.getLayoutParams()).setMarginEnd(Ui.dp(this, 4));
        root.addView(bar, Ui.matchWrap());

        FrameLayout body = new FrameLayout(this);
        rv = new RecyclerView(this);
        rv.setClipToPadding(false);
        rv.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 24));
        lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                if (dy > 0 && more && !loading && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) load(false);
            }
        });
        body.addView(rv, Ui.match());
        state = Ui.text(this, "", 14, Ui.TEXT_3);
        state.setGravity(Gravity.CENTER);
        body.addView(state, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        Social.startup(this);
        reload();
    }

    @Override protected void onResume() {
        super.onResume();
        if (dirty) { dirty = false; reload(); }
        Suda3D.IO.execute(() -> { int n = Social.unread(this); Ui.ui(() -> { bellBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE); bellBadge.setText(n > 98 ? "99+" : String.valueOf(n)); }); });
    }

    @Override public void removed(long id) {
        for (int i = 0; i < posts.size(); i++) if ((long) Suda3D.num(posts.get(i), "id", 0) == id) { posts.remove(i); break; }
        for (int i = 0; i < pinned.size(); i++) if ((long) Suda3D.num(pinned.get(i), "id", 0) == id) { pinned.remove(i); break; }
        adapter.notifyDataSetChanged();
    }

    @Override public void changed() { reload(); }

    private void reload() { gen++; loading = false; more = false; next = 0; load(true); }

    private void load(boolean first) {
        if (loading) return;
        loading = true;
        final int g = gen;
        final long before = first ? 0 : next;
        if (first && posts.isEmpty()) state.setText(Tr.t("loading", "Loading..."));
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = CommunityApi.posts(this, before, 0);
                Ui.ui(() -> {
                    if (g != gen) return;
                    loading = false;
                    state.setText("");
                    if (first) {
                        posts.clear(); pinned.clear();
                        if (r.has("pinned")) for (JsonElement e : r.getAsJsonArray("pinned")) pinned.add(e.getAsJsonObject());
                    }
                    for (JsonElement e : CommunityApi.data(r)) posts.add(e.getAsJsonObject());
                    more = Suda3D.bool(r, "has_more");
                    next = (long) Suda3D.num(r, "next_before", 0);
                    if (posts.isEmpty() && pinned.isEmpty()) state.setText(Tr.t("sc_no_posts", "No posts yet."));
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                Ui.ui(() -> { if (g != gen) return; loading = false; state.setText(posts.isEmpty() ? Social.err(e) : ""); if (!posts.isEmpty()) Ui.toast(this, Social.err(e)); });
            }
        });
    }

    private void compose() {
        Social.needLogin(this, () -> startActivity(new Intent(this, ComposeActivity.class)));
    }

    // ------------------------------------------------------------------ adapter
    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return 2 + posts.size() + (more ? 1 : 0); }
        @Override public int getItemViewType(int i) { return i == 0 ? T_COMPOSER : i == 1 ? T_STRIP : i - 2 < posts.size() ? T_POST : T_FOOT; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int t) {
            FrameLayout f = new FrameLayout(CommunityActivity.this);
            f.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(f) {};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
            FrameLayout f = (FrameLayout) h.itemView;
            f.removeAllViews();
            int t = getItemViewType(i);
            View v;
            if (t == T_COMPOSER) v = composerRow();
            else if (t == T_STRIP) v = strip();
            else if (t == T_POST) v = PostView.build(CommunityActivity.this, posts.get(i - 2), false, false, CommunityActivity.this);
            else { TextView x = Ui.text(CommunityActivity.this, Tr.t("loading", "Loading..."), 12, Ui.TEXT_3); x.setGravity(Gravity.CENTER); x.setPadding(0, Ui.dp(CommunityActivity.this, 12), 0, Ui.dp(CommunityActivity.this, 12)); v = x; }
            ViewGroup.MarginLayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(CommunityActivity.this, 10);
            f.addView(v, lp);
        }
    }

    private View composerRow() {
        LinearLayout r = Ui.row(this);
        r.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 14), Ui.BORDER, Ui.dp(this, 1)));
        r.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        ImageView av = new ImageView(this);
        JsonObject me = new JsonObject();
        if (Suda3D.loggedIn(this)) me.addProperty("avatar", "community/users/" + Suda3D.userId(this) + "/avatar");
        Social.avatar(this, av, null, 36);
        r.addView(av);
        TextView t = Ui.text(this, Tr.t("sc_compose_hint", "Share something with the community..."), 14, Ui.TEXT_3);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        r.addView(t, Ui.weight(1));
        r.addView(Ui.smallIcon(this, R.drawable.ic_s2_image, Ui.TEXT_3, 22));
        r.setOnClickListener(v -> compose());
        return r;
    }

    /** Pinned posts: a horizontal strip at the top of the same vertical scroll. */
    private View strip() {
        if (pinned.isEmpty()) { View v = new View(this); v.setLayoutParams(new ViewGroup.LayoutParams(1, 1)); return v; }
        LinearLayout col = Ui.column(this);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.smallIcon(this, R.drawable.ic_s2_pin, 0xFFFFB300, 16));
        TextView t = Ui.text(this, Tr.t("sc_pinned", "Pinned"), 13, 0xFFFFB300);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(this, 6), 0, 0, 0);
        head.addView(t);
        head.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 6));
        col.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        hs.addView(row);
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        for (JsonObject p : pinned) {
            View card = PostView.build(this, p, true, false, this);
            // the card shows only the beginning in the strip; tap opens the post
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.min(w, Ui.dp(this, 340)), -2);
            lp.setMarginEnd(Ui.dp(this, 10));
            row.addView(card, lp);
            final long id = (long) Suda3D.num(p, "id", 0);
            card.setOnClickListener(v -> startActivity(new Intent(this, PostDetailActivity.class).putExtra("id", id)));
        }
        col.addView(hs);
        return col;
    }
}
