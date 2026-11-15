package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** One community post as a card: author, text (3 lines + "see more"), pictures (horizontal), star and comments. */
public final class PostView {
    private PostView() {}

    public interface Host {
        /** The post was deleted or hidden. */
        void removed(long id);
        /** The post changed (pin, edit): refresh the list. */
        void changed();
    }

    public static View build(Activity a, JsonObject p, boolean compact, boolean detail, Host host) {
        long id = (long) Suda3D.num(p, "id", 0);
        LinearLayout card = Ui.column(a);
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(a, 14), Suda3D.bool(p, "pinned") ? 0x66FFB300 : Ui.BORDER, Ui.dp(a, 1)));
        card.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 8));

        // header
        JsonObject u = Suda3D.obj(p, "user");
        LinearLayout head = Ui.row(a);
        ImageView av = new ImageView(a);
        Social.avatar(a, av, u, 38);
        av.setOnClickListener(v -> Social.openProfile(a, (long) Suda3D.num(u, "id", 0)));
        head.addView(av);
        LinearLayout who = Ui.column(a);
        who.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 6), 0);
        who.addView(Social.nameRow(a, u, 14.5f));
        StringBuilder sub = new StringBuilder(Social.timeAgo(Suda3D.str(p, "created_at")));
        if (Suda3D.bool(p, "edited")) sub.append(" · ").append(Tr.t("sc_edited", "(edited)"));
        who.addView(Ui.text(a, sub, 11.5f, Ui.TEXT_3));
        head.addView(who, Ui.weight(1));
        if (Suda3D.bool(p, "pending")) {
            TextView t = Ui.text(a, Tr.t("sc_pending", "Awaiting approval"), 10.5f, 0xFF1B1300);
            t.setPadding(Ui.dp(a, 7), Ui.dp(a, 2), Ui.dp(a, 7), Ui.dp(a, 2));
            t.setBackground(Ui.rounded(Ui.WARNING, Ui.dp(a, 9)));
            head.addView(t);
        }
        if (Suda3D.bool(p, "pinned")) {
            ImageView pin = Ui.smallIcon(a, R.drawable.ic_s2_pin, 0xFFFFB300, 18);
            head.addView(pin, Ui.margins(new LinearLayout.LayoutParams(-2, -2), a, 4, 0, 2, 0));
        }
        ImageView more = Ui.iconButton(a, R.drawable.ic_s2_more, Tr.t("more", "More"), v -> menu(a, v, p, host));
        head.addView(more);
        card.addView(head, Ui.matchWrap());

        // text
        String body = Suda3D.str(p, "body");
        if (!body.trim().isEmpty()) {
            TextView t = Ui.text(a, "", 14.5f, Ui.TEXT);
            t.setLineSpacing(0, 1.12f);
            Social.linkify(t, body, Suda3D.obj(p, "projects"));
            t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 2));
            card.addView(t, Ui.matchWrap());
            if (!detail) {
                t.setMaxLines(3);
                t.setEllipsize(TextUtils.TruncateAt.END);
                TextView mo = Ui.text(a, Tr.t("sc_see_more", "See more"), 13, 0xFF64B5F6);
                mo.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 4));
                mo.setVisibility(View.GONE);
                card.addView(mo);
                t.post(() -> {
                    android.text.Layout l = t.getLayout();
                    if (l != null && l.getLineCount() >= 3 && l.getEllipsisCount(l.getLineCount() - 1) > 0) mo.setVisibility(View.VISIBLE);
                });
                mo.setOnClickListener(v -> {
                    boolean open = t.getMaxLines() > 3;
                    t.setMaxLines(open ? 3 : Integer.MAX_VALUE);
                    t.setEllipsize(open ? TextUtils.TruncateAt.END : null);
                    mo.setText(open ? Tr.t("sc_see_more", "See more") : Tr.t("sc_see_less", "See less"));
                });
            }
        }

        // pictures (scroll sideways)
        JsonArray imgs = p.has("images") && p.get("images").isJsonArray() ? p.getAsJsonArray("images") : new JsonArray();
        if (imgs.size() > 0) {
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = Ui.row(a);
            hs.addView(row);
            int h = Ui.dp(a, compact ? 150 : 210);
            for (JsonElement e : imgs) {
                JsonObject im = e.getAsJsonObject();
                double w = Suda3D.num(im, "w", 4), hh = Suda3D.num(im, "h", 3);
                int pw = (int) Math.max(Ui.dp(a, 120), Math.min(Ui.dp(a, 320), h * (hh <= 0 ? 1.3 : w / hh)));
                ImageView iv = new ImageView(a);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(a, 10)));
                iv.setClipToOutline(true);
                iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(a, 10)); }
                });
                Glide.with(a).load(Suda3D.absolute(a, Suda3D.str(im, "url"))).into(iv);
                final String url = Suda3D.absolute(a, Suda3D.str(im, "url"));
                iv.setOnClickListener(v -> showImage(a, url));
                row.addView(iv, Ui.margins(new LinearLayout.LayoutParams(pw, h), a, 0, 0, 8, 0));
            }
            card.addView(hs, Ui.margins(Ui.matchWrap(), a, 0, 6, 0, 2));
        }

        // actions
        LinearLayout act = Ui.row(a);
        act.setPadding(0, Ui.dp(a, 6), 0, 0);
        act.addView(new StarButton(a, "post", id, Suda3D.bool(p, "starred"), (int) Suda3D.num(p, "stars", 0)));
        TextView cm = Ui.text(a, String.valueOf((int) Suda3D.num(p, "comments", 0)), 13, Ui.TEXT_2);
        cm.setTypeface(Typeface.DEFAULT_BOLD);
        cm.setGravity(Gravity.CENTER_VERTICAL);
        cm.setCompoundDrawablePadding(Ui.dp(a, 6));
        android.graphics.drawable.Drawable d = a.getDrawable(R.drawable.ic_s2_comment).mutate();
        d.setTint(Ui.TEXT_2);
        d.setBounds(0, 0, Ui.dp(a, 20), Ui.dp(a, 20));
        cm.setCompoundDrawablesRelative(d, null, null, null);
        cm.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6));
        cm.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(a, 18)));
        cm.setOnClickListener(v -> CommentsSheet.show(a, "post", id, (int) Suda3D.num(p, "comments", 0), n -> cm.setText(String.valueOf(n))));
        act.addView(cm, Ui.margins(new LinearLayout.LayoutParams(-2, -2), a, 8, 0, 0, 0));
        card.addView(act, Ui.matchWrap());
        if (!detail && !compact) card.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, PostDetailActivity.class).putExtra("id", id)));
        return card;
    }

    static void showImage(Activity a, String url) {
        ImageView iv = new ImageView(a);
        iv.setBackgroundColor(0xFF000000);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(a).load(url).into(iv);
        AlertDialog d = new AlertDialog.Builder(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen).setView(iv).create();
        iv.setOnClickListener(v -> d.dismiss());
        d.show();
    }

    private static void menu(Activity a, View anchor, JsonObject p, Host host) {
        long id = (long) Suda3D.num(p, "id", 0);
        boolean mod = Suda3D.isMod(a);
        PopupMenu m = new PopupMenu(a, anchor);
        if (Suda3D.bool(p, "can_edit")) m.getMenu().add(0, 1, 0, Tr.t("edit", "Edit"));
        if (Suda3D.bool(p, "can_delete")) m.getMenu().add(0, 2, 0, Tr.t("delete", "Delete"));
        if (mod && Suda3D.bool(p, "pinned")) m.getMenu().add(0, 4, 0, Tr.t("sc_unpin", "Unpin"));
        if (mod && !Suda3D.bool(p, "pinned") && !Suda3D.bool(p, "pending")) m.getMenu().add(0, 3, 0, Tr.t("sc_pin", "Pin"));
        if (!Suda3D.bool(p, "mine")) m.getMenu().add(0, 5, 0, Tr.t("sc_report", "Report"));
        m.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1: editDialog(a, p, host); break;
                case 2:
                    new AlertDialog.Builder(a).setMessage(Tr.t("sc_delete_post_q", "Delete this post?"))
                            .setPositiveButton(Tr.t("delete", "Delete"), (d, w) -> run(a, () -> CommunityApi.deletePost(a, id), () -> host.removed(id)))
                            .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
                    break;
                case 3: run(a, () -> CommunityApi.pin(a, id, true), host::changed); break;
                case 4: run(a, () -> CommunityApi.pin(a, id, false), host::changed); break;
                case 5: reportDialog(a, "post", id); break;
            }
            return true;
        });
        m.show();
    }

    interface Job { void go() throws Exception; }

    static void run(Activity a, Job job, Runnable done) {
        Suda3D.IO.execute(() -> {
            try { job.go(); Ui.ui(done); } catch (Exception e) { Ui.ui(() -> Ui.toast(a, Social.err(e))); }
        });
    }

    private static void editDialog(Activity a, JsonObject p, Host host) {
        long id = (long) Suda3D.num(p, "id", 0);
        android.widget.EditText e = Ui.input(a, Suda3D.str(p, "body"), "");
        e.setSingleLine(false);
        e.setMinLines(4);
        e.setGravity(Gravity.TOP);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 18), Ui.dp(a, 8), Ui.dp(a, 18), 0);
        col.addView(e, Ui.matchWrap());
        new AlertDialog.Builder(a).setTitle(Tr.t("edit", "Edit")).setView(col)
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> run(a, () -> CommunityApi.editPost(a, id, e.getText().toString().trim()), host::changed))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
    }

    /** Pick a reason, optional note, and send the report. */
    public static void reportDialog(Activity a, String type, long id) {
        Social.needLogin(a, () -> {
            String[] keys = {"spam", "abuse", "inappropriate", "other"};
            String[] names = {Tr.t("sc_r_spam", "Spam"), Tr.t("sc_r_abuse", "Abuse or harassment"), Tr.t("sc_r_inappropriate", "Inappropriate content"), Tr.t("sc_r_other", "Other")};
            final int[] sel = {0};
            new AlertDialog.Builder(a).setTitle(Tr.t("sc_report", "Report"))
                    .setSingleChoiceItems(names, 0, (d, w) -> sel[0] = w)
                    .setPositiveButton(Tr.t("sc_send", "Send"), (d, w) -> Suda3D.IO.execute(() -> {
                        try { CommunityApi.report(a, type, id, keys[sel[0]], null); Ui.ui(() -> Ui.toast(a, Tr.t("sc_reported", "Thanks, the report was sent."))); }
                        catch (Exception e) { Ui.ui(() -> Ui.toast(a, Social.err(e))); }
                    }))
                    .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
        });
    }
}
