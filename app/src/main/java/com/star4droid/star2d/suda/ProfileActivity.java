package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Profile of a person: picture, name, numbers, badges, their store projects and posts. */
public class ProfileActivity extends Activity implements PostView.Host {
    private static final int REQ_AVATAR = 51;

    private long id;
    private JsonObject prof;
    private LinearLayout holder, listBox;
    private boolean showPosts;
    private long postsNext;
    private boolean postsMore, projMore, loading;
    private int projPage = 1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        id = getIntent().getLongExtra("id", 0);
        if (id == 0) id = Suda3D.userId(this);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Tr.t("sc_profile", "Profile")), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        holder = Ui.column(this);
        holder.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 28));
        sv.addView(holder);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            View c = sv.getChildAt(0);
            if (c != null && !loading && y + sv.getHeight() >= c.getHeight() - Ui.dp(this, 160)) loadMore();
        });
        Social.startup(this);
        load();
    }

    private void load() {
        holder.removeAllViews();
        TextView t = Ui.text(this, Tr.t("loading", "Loading..."), 14, Ui.TEXT_3);
        holder.addView(t);
        Suda3D.IO.execute(() -> {
            try {
                JsonObject p = CommunityApi.profile(this, id);
                Ui.ui(() -> { prof = p; draw(); });
            } catch (Exception e) {
                Ui.ui(() -> t.setText(e instanceof Suda3D.ApiException && ((Suda3D.ApiException) e).status == 404 ? Tr.t("sc_user_gone", "This account is not available.") : Social.err(e)));
            }
        });
    }

    private void draw() {
        holder.removeAllViews();
        boolean mine = Suda3D.bool(prof, "mine");
        // header
        LinearLayout head = Ui.column(this);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 16), Ui.BORDER, Ui.dp(this, 1)));
        head.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 14));
        ImageView av = new ImageView(this);
        Social.avatar(this, av, prof, 92);
        if (mine) av.setOnClickListener(v -> avatarMenu());
        head.addView(av);
        LinearLayout nm = Social.nameRow(this, prof, 19);
        nm.setGravity(Gravity.CENTER);
        nm.setOnClickListener(null);
        head.addView(nm, Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 0, 10, 0, 0));
        String joined = Suda3D.str(prof, "joined_at");
        if (joined.length() >= 10) head.addView(Ui.text(this, Tr.t("sc_joined", "Joined") + " " + joined.substring(0, 10), 12, Ui.TEXT_3));
        if (mine) {
            LinearLayout acts = Ui.row(this);
            acts.setGravity(Gravity.CENTER);
            acts.addView(Ui.button(this, R.drawable.ic_s2_edit, Tr.t("sc_change_name", "Change name"), false, v -> nameDialog()));
            acts.addView(Ui.button(this, R.drawable.ic_s2_bell, Tr.t("sc_notif_lang", "Notification language"), false, v -> Social.askNotifLang(this, null)),
                    Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 8, 0, 0, 0));
            head.addView(acts, Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 0, 10, 0, 0));
        } else {
            LinearLayout acts = Ui.row(this);
            acts.setGravity(Gravity.CENTER);
            if (Suda3D.isMod(this)) acts.addView(Ui.button(this, R.drawable.ic_s2_shield, Tr.t("sc_give_badge", "Give a badge"), false, v -> giveBadge()));
            acts.addView(Ui.button(this, R.drawable.ic_s2_flag, Tr.t("sc_report", "Report"), false, v -> PostView.reportDialog(this, "user", id)),
                    Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 8, 0, 0, 0));
            head.addView(acts, Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 0, 10, 0, 0));
        }
        holder.addView(head, Ui.matchWrap());

        // numbers
        LinearLayout stats = Ui.row(this);
        stats.addView(stat((int) Suda3D.num(prof, "projects", 0), Tr.t("sc_projects", "Projects")), Ui.weight(1));
        stats.addView(stat((int) Suda3D.num(prof, "games", 0), Tr.t("sc_games", "Games")), Ui.weight(1));
        stats.addView(stat((int) Suda3D.num(prof, "posts", 0), Tr.t("sc_posts", "Posts")), Ui.weight(1));
        stats.addView(stat((int) Suda3D.num(prof, "stars", 0), Tr.t("sc_stars", "Stars")), Ui.weight(1));
        holder.addView(stats, Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));

        // badges
        JsonArray bd = prof.has("badges") ? prof.getAsJsonArray("badges") : new JsonArray();
        holder.addView(Ui.sectionLabel(this, Tr.t("sc_badges", "Badges")), Ui.margins(Ui.matchWrap(), this, 4, 16, 0, 6));
        if (bd.size() == 0) holder.addView(Ui.text(this, Tr.t("sc_no_badges", "No badges yet."), 13, Ui.TEXT_3));
        else {
            android.widget.GridLayout g = new android.widget.GridLayout(this);
            g.setColumnCount(4);
            for (JsonElement e : bd) {
                String key = Suda3D.str(e.getAsJsonObject(), "key");
                LinearLayout cell = Ui.column(this);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 6));
                cell.addView(Social.badgeView(this, key, 52));
                TextView t = Ui.text(this, Social.badgeTitle(key), 10.5f, Ui.TEXT_2);
                t.setGravity(Gravity.CENTER);
                t.setMaxLines(2);
                cell.addView(t, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(this, 74), -2), this, 0, 4, 0, 0));
                cell.setOnClickListener(v -> Ui.toast(this, Social.badgeTitle(key)));
                android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
                lp.width = (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24)) / 4;
                g.addView(cell, lp);
            }
            holder.addView(g, Ui.matchWrap());
        }

        // tabs
        LinearLayout tabs = Ui.row(this);
        TextView t1 = tab(Tr.t("sc_projects", "Projects"), !showPosts);
        TextView t2 = tab(Tr.t("sc_posts", "Posts"), showPosts);
        t1.setOnClickListener(v -> { showPosts = false; draw(); });
        t2.setOnClickListener(v -> { showPosts = true; draw(); });
        tabs.addView(t1, Ui.weight(1));
        tabs.addView(t2, Ui.weight(1));
        holder.addView(tabs, Ui.margins(Ui.matchWrap(), this, 0, 16, 0, 8));
        listBox = Ui.column(this);
        holder.addView(listBox, Ui.matchWrap());
        projPage = 1; postsNext = 0; projMore = false; postsMore = false;
        loadMore();
    }

    private TextView tab(String s, boolean on) {
        TextView t = Ui.text(this, s, 14, on ? 0xFFFFFFFF : Ui.TEXT_2);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        t.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_2, Ui.dp(this, 10)));
        return t;
    }

    private View stat(int n, String label) {
        LinearLayout c = Ui.column(this);
        c.setGravity(Gravity.CENTER);
        TextView a = Ui.text(this, String.valueOf(n), 18, Ui.TEXT);
        a.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(a);
        TextView b = Ui.text(this, label, 11.5f, Ui.TEXT_3);
        b.setSingleLine(true);
        c.addView(b);
        return c;
    }

    // ------------------------------------------------------------------ lists
    private void loadMore() {
        if (loading || prof == null || listBox == null) return;
        if (showPosts) {
            if (postsNext == 0 && listBox.getChildCount() > 0) { if (!postsMore) return; }
            loadPosts();
        } else loadProjects();
    }

    private void loadProjects() {
        if (projPage > 1 && !projMore) return;
        loading = true;
        final int page = projPage;
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = MarketApi.list(this, "", "", "", page, id, false);
                JsonArray d = MarketApi.data(r);
                Ui.ui(() -> {
                    loading = false;
                    if (showPosts) return;
                    for (JsonElement e : d) listBox.addView(projectRow(e.getAsJsonObject()), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
                    if (page == 1 && d.size() == 0) listBox.addView(Ui.text(this, Tr.t("sc_no_projects", "No published projects."), 13, Ui.TEXT_3));
                    projMore = d.size() >= 24;
                    projPage = page + 1;
                });
            } catch (Exception e) { Ui.ui(() -> { loading = false; Ui.toast(this, Social.err(e)); }); }
        });
    }

    private View projectRow(JsonObject p) {
        LinearLayout r = Ui.row(this);
        r.setBackground(Ui.rounded(Suda3D.bool(p, "editor_choice") ? 0xFF3A2F12 : Ui.SURFACE, Ui.dp(this, 12), Ui.BORDER, Ui.dp(this, 1)));
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout col = Ui.column(this);
        TextView n = Ui.text(this, Suda3D.str(p, "name"), 14.5f, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(n);
        col.addView(Ui.text(this, Tr.t("mk_f_" + ("game".equals(Suda3D.str(p, "kind")) ? "games" : "projects"), "game".equals(Suda3D.str(p, "kind")) ? "Game" : "Project")
                + "  ·  ★ " + (int) Suda3D.num(p, "stars", 0) + "  ·  💬 " + (int) Suda3D.num(p, "comments", 0), 12, Ui.TEXT_3));
        r.addView(col, Ui.weight(1));
        if (Suda3D.bool(p, "editor_choice")) r.addView(Social.plainIcon(this, R.drawable.ic_s2_star_badge, 22));
        long pid = (long) Suda3D.num(p, "id", 0);
        r.setOnClickListener(v -> Social.openProject(this, pid));
        return r;
    }

    private void loadPosts() {
        loading = true;
        final long before = postsNext;
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = CommunityApi.posts(this, before, id);
                JsonArray d = CommunityApi.data(r);
                Ui.ui(() -> {
                    loading = false;
                    if (!showPosts) return;
                    for (JsonElement e : d) listBox.addView(PostView.build(this, e.getAsJsonObject(), false, false, this), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
                    if (before == 0 && d.size() == 0) listBox.addView(Ui.text(this, Tr.t("sc_no_posts", "No posts yet."), 13, Ui.TEXT_3));
                    postsMore = Suda3D.bool(r, "has_more");
                    postsNext = postsMore ? (long) Suda3D.num(r, "next_before", 0) : 0;
                });
            } catch (Exception e) { Ui.ui(() -> { loading = false; Ui.toast(this, Social.err(e)); }); }
        });
    }

    @Override public void removed(long x) { CommunityActivity.dirty = true; draw(); }
    @Override public void changed() { draw(); }

    // ------------------------------------------------------------------ editing (own profile)
    private void nameDialog() {
        EditText e = Ui.input(this, Suda3D.str(prof, "name"), Tr.t("sc_name_hint", "3-20 letters, numbers or _"));
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        col.addView(e);
        new AlertDialog.Builder(this).setTitle(Tr.t("sc_change_name", "Change name")).setView(col)
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> Suda3D.IO.execute(() -> {
                    try {
                        JsonObject me = CommunityApi.updateMe(this, e.getText().toString().trim(), null);
                        Suda3D.setIdentity(this, me);
                        MarketActivity.dirty = true;
                        Ui.ui(this::load);
                    } catch (Exception ex) { Ui.ui(() -> Ui.toast(this, Social.err(ex))); }
                }))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
    }

    private void avatarMenu() {
        new AlertDialog.Builder(this).setItems(new String[]{Tr.t("sc_change_photo", "Change picture"), Tr.t("sc_remove_photo", "Remove picture")}, (d, w) -> {
            if (w == 0) startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_AVATAR);
            else Suda3D.IO.execute(() -> { try { CommunityApi.clearAvatar(this); Ui.ui(this::load); } catch (Exception e) { Ui.ui(() -> Ui.toast(this, Social.err(e))); } });
        }).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_AVATAR || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        Suda3D.IO.execute(() -> {
            try {
                File f = Social.compress(this, u, 300, 512, "avatar");
                CommunityApi.setAvatar(this, f);
                MarketActivity.dirty = true;
                Ui.ui(this::load);
            } catch (Exception e) { Ui.ui(() -> Ui.toast(this, Social.err(e))); }
        });
    }

    // ------------------------------------------------------------------ moderator
    private void giveBadge() {
        Set<String> have = new HashSet<>();
        for (JsonElement e : prof.getAsJsonArray("badges")) have.add(Suda3D.str(e.getAsJsonObject(), "key"));
        String[] names = new String[Social.MANUAL.length];
        boolean[] on = new boolean[names.length];
        for (int i = 0; i < names.length; i++) { names[i] = Social.badgeTitle(Social.MANUAL[i]); on[i] = have.contains(Social.MANUAL[i]); }
        final boolean[] was = on.clone();
        new AlertDialog.Builder(this).setTitle(Tr.t("sc_give_badge", "Give a badge"))
                .setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> Suda3D.IO.execute(() -> {
                    try {
                        for (int i = 0; i < on.length; i++) if (on[i] != was[i]) CommunityApi.badge(this, id, Social.MANUAL[i], on[i]);
                        Ui.ui(this::load);
                    } catch (Exception e) { Ui.ui(() -> Ui.toast(this, Social.err(e))); }
                }))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
    }
}
