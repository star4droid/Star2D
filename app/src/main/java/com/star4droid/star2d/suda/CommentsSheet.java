package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Comments of a post or a store project in a bottom sheet: two levels (comment, reply), edit, delete, report, moderator tools. */
public final class CommentsSheet {
    private static final int T_COMMENT = 0, T_REPLY = 1, T_MORE_REPLIES = 2, T_MORE = 3;

    private static final class Row {
        int type;
        JsonObject c;
        long parent; // for T_MORE_REPLIES: the top-level comment
        long cursor; // for the "more" rows
        int left;
        boolean revealed; // moderator pressed "View" on an author-deleted comment
    }

    private final Activity a;
    private final String type;
    private final long targetId;
    private final IntConsumer onCount;
    private final List<Row> rows = new ArrayList<>();
    private final RecyclerView rv;
    private final TextView empty, replyChip, title;
    private final EditText input;
    private final LinearLayout chipBar;
    private final BottomSheetDialog dlg;
    private final Adapter adapter = new Adapter();
    private long replyTo, replyParent, editing;
    private boolean loading, more, mod;
    private long nextTop;
    private int total;

    public static void show(Activity a, String type, long id, int total, IntConsumer onCount) {
        new CommentsSheet(a, type, id, total, onCount);
    }

    private CommentsSheet(Activity a, String type, long id, int total, IntConsumer onCount) {
        this.a = a; this.type = type; this.targetId = id; this.onCount = onCount; this.total = total;
        mod = Suda3D.isMod(a);
        dlg = new BottomSheetDialog(a);
        LinearLayout root = Ui.column(a);
        root.setBackground(Ui.rounded(Ui.BG, Ui.dp(a, 18)));
        int h = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.82f);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, h));

        View grip = new View(a);
        grip.setBackground(Ui.rounded(Ui.BORDER, Ui.dp(a, 3)));
        root.addView(grip, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 4)), a, 0, 8, 0, 4));
        ((LinearLayout.LayoutParams) grip.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        title = Ui.title(a, "");
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 8));
        root.addView(title, Ui.matchWrap());
        root.addView(Ui.divider(a));

        android.widget.FrameLayout mid = new android.widget.FrameLayout(a);
        rv = new RecyclerView(a);
        rv.setLayoutManager(new LinearLayoutManager(a));
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) r.getLayoutManager();
                if (dy > 0 && more && !loading && lm.findLastVisibleItemPosition() >= rows.size() - 3) loadTop(false);
            }
        });
        mid.addView(rv, Ui.match());
        empty = Ui.text(a, Tr.t("sc_no_comments", "No comments yet. Be the first!"), 14, Ui.TEXT_3);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        mid.addView(empty, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        // "replying to ..." chip
        chipBar = Ui.row(a);
        chipBar.setBackgroundColor(Ui.SURFACE_2);
        chipBar.setPadding(Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        replyChip = Ui.text(a, "", 12.5f, Ui.TEXT_2);
        replyChip.setSingleLine(true);
        replyChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chipBar.addView(replyChip, Ui.weight(1));
        chipBar.addView(Ui.iconButton(a, R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), v -> clearMode()));
        chipBar.setVisibility(View.GONE);
        root.addView(chipBar, Ui.matchWrap());

        // input
        LinearLayout bar = Ui.row(a);
        bar.setBackgroundColor(Ui.SURFACE);
        bar.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
        input = Ui.input(a, "", Tr.t("sc_write_comment", "Write a comment..."));
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bar.addView(input, Ui.weight(1));
        ImageView send = Ui.iconButton(a, R.drawable.ic_s2_send, Tr.t("sc_send", "Send"), v -> send());
        bar.addView(send);
        root.addView(bar, Ui.matchWrap());

        dlg.setContentView(root);
        dlg.setOnShowListener(d -> {
            View sheet = dlg.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundColor(0);
                BottomSheetBehavior<View> bh = BottomSheetBehavior.from(sheet);
                bh.setState(BottomSheetBehavior.STATE_EXPANDED);
                bh.setSkipCollapsed(true);
            }
        });
        dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        paintTitle();
        dlg.show();
        loadTop(true);
    }

    private void paintTitle() { title.setText(Tr.t("sc_comments", "Comments") + (total > 0 ? "  ·  " + total : "")); }

    private void clearMode() {
        replyTo = 0; replyParent = 0; editing = 0;
        chipBar.setVisibility(View.GONE);
        input.setText("");
        input.setHint(Tr.t("sc_write_comment", "Write a comment..."));
    }

    // ------------------------------------------------------------------ loading
    private void loadTop(boolean first) {
        if (loading) return;
        loading = true;
        long cur = first ? 0 : nextTop;
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = CommunityApi.comments(a, type, targetId, 0, cur);
                JsonArray d = CommunityApi.data(r);
                Ui.ui(() -> {
                    loading = false;
                    if (first) rows.clear();
                    for (JsonElement e : d) addTop(e.getAsJsonObject());
                    more = Suda3D.bool(r, "has_more");
                    nextTop = (long) Suda3D.num(r, "next", 0);
                    if (r.has("total")) { total = (int) Suda3D.num(r, "total", total); paintTitle(); if (onCount != null) onCount.accept(total); }
                    adapter.notifyDataSetChanged();
                    empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                Ui.ui(() -> { loading = false; Ui.toast(a, Social.err(e)); });
            }
        });
    }

    private void addTop(JsonObject c) {
        Row r = new Row();
        r.type = T_COMMENT;
        r.c = c;
        rows.add(r);
        int n = (int) Suda3D.num(c, "replies", 0);
        if (n > 0) addMore(rows.size(), (long) Suda3D.num(c, "id", 0), 0, n);
    }

    private void addMore(int at, long parent, long cursor, int left) {
        Row m = new Row();
        m.type = T_MORE_REPLIES; m.parent = parent; m.cursor = cursor; m.left = left;
        rows.add(at, m);
    }

    private void loadReplies(Row more) {
        int idx = rows.indexOf(more);
        if (idx < 0) return;
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = CommunityApi.comments(a, type, targetId, more.parent, more.cursor);
                JsonArray d = CommunityApi.data(r);
                Ui.ui(() -> {
                    int at = rows.indexOf(more);
                    if (at < 0) return;
                    rows.remove(at);
                    long last = more.cursor;
                    for (JsonElement e : d) {
                        Row x = new Row();
                        x.type = T_REPLY; x.c = e.getAsJsonObject();
                        rows.add(at++, x);
                        last = (long) Suda3D.num(x.c, "id", last);
                    }
                    if (Suda3D.bool(r, "has_more")) addMore(at, more.parent, last, Math.max(1, more.left - d.size()));
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) { Ui.ui(() -> Ui.toast(a, Social.err(e))); }
        });
    }

    // ------------------------------------------------------------------ sending / editing
    private void send() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) return;
        Social.needLogin(a, () -> {
            final long edit = editing, to = replyTo, par = replyParent;
            input.setEnabled(false);
            Suda3D.IO.execute(() -> {
                try {
                    JsonObject r = edit > 0 ? CommunityApi.editComment(a, edit, t) : CommunityApi.addComment(a, type, targetId, t, to);
                    Ui.ui(() -> {
                        input.setEnabled(true);
                        clearMode();
                        if (edit > 0) {
                            for (Row x : rows) if (x.c != null && (long) Suda3D.num(x.c, "id", 0) == edit) x.c = r;
                        } else {
                            total++; paintTitle(); if (onCount != null) onCount.accept(total);
                            if (par > 0) insertReply(par, r); else { Row x = new Row(); x.type = T_COMMENT; x.c = r; rows.add(0, x); }
                        }
                        adapter.notifyDataSetChanged();
                        empty.setVisibility(View.GONE);
                        if (par == 0 && edit == 0) rv.scrollToPosition(0);
                    });
                } catch (Exception e) { Ui.ui(() -> { input.setEnabled(true); Ui.toast(a, Social.err(e)); }); }
            });
        });
    }

    /** New reply goes at the end of the loaded replies of its comment (before the "more" row, if any). */
    private void insertReply(long parent, JsonObject r) {
        int at = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row x = rows.get(i);
            if ((x.type == T_COMMENT && (long) Suda3D.num(x.c, "id", 0) == parent) || (x.type == T_REPLY && (long) Suda3D.num(x.c, "parent_id", 0) == parent)) at = i + 1;
            if (x.type == T_MORE_REPLIES && x.parent == parent) { at = -2; break; }
        }
        if (at == -2) return; // the replies are not all loaded; the new one appears when the person opens them
        if (at < 0) return;
        Row x = new Row(); x.type = T_REPLY; x.c = r;
        rows.add(at, x);
    }

    // ------------------------------------------------------------------ one row
    private void menu(View anchor, Row r) {
        JsonObject c = r.c;
        long id = (long) Suda3D.num(c, "id", 0);
        boolean deleted = Suda3D.bool(c, "deleted");
        PopupMenu m = new PopupMenu(a, anchor);
        if (Suda3D.bool(c, "can_edit")) m.getMenu().add(0, 1, 0, Tr.t("edit", "Edit"));
        if (Suda3D.bool(c, "can_delete")) m.getMenu().add(0, 2, 0, Tr.t("delete", "Delete"));
        if (mod) m.getMenu().add(0, 3, 0, Tr.t("sc_history", "Edit history"));
        if (!Suda3D.bool(c, "mine") && !deleted) m.getMenu().add(0, 4, 0, Tr.t("sc_report", "Report"));
        m.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1:
                    editing = id; replyTo = 0; replyParent = 0;
                    input.setText(Suda3D.str(c, "body")); input.setSelection(input.getText().length());
                    replyChip.setText(Tr.t("sc_editing", "Editing your comment"));
                    chipBar.setVisibility(View.VISIBLE);
                    input.requestFocus();
                    break;
                case 2:
                    new AlertDialog.Builder(a).setMessage(Tr.t("sc_delete_comment_q", "Delete this comment?"))
                            .setPositiveButton(Tr.t("delete", "Delete"), (d, w) -> PostView.run(a, () -> CommunityApi.deleteComment(a, id), () -> afterDelete(r)))
                            .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
                    break;
                case 3: history(id); break;
                case 4: PostView.reportDialog(a, "comment", id); break;
            }
            return true;
        });
        m.show();
    }

    private void afterDelete(Row r) {
        long id = (long) Suda3D.num(r.c, "id", 0);
        if (mod) { // removed for good, with its replies
            List<Row> gone = new ArrayList<>();
            for (Row x : rows) {
                long xid = (long) Suda3D.num(x.c == null ? new JsonObject() : x.c, "id", 0);
                if (xid == id || (x.type == T_REPLY && (long) Suda3D.num(x.c, "parent_id", 0) == id) || (x.type == T_MORE_REPLIES && x.parent == id)) gone.add(x);
            }
            rows.removeAll(gone);
            total = Math.max(0, total - Math.max(1, gone.size()));
        } else {
            r.c.addProperty("deleted", true);
            r.c.addProperty("body", "");
            total = Math.max(0, total - 1);
        }
        paintTitle();
        if (onCount != null) onCount.accept(total);
        adapter.notifyDataSetChanged();
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void history(long id) {
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = CommunityApi.history(a, id);
                Ui.ui(() -> {
                    StringBuilder sb = new StringBuilder();
                    JsonArray ed = r.has("edits") ? r.getAsJsonArray("edits") : new JsonArray();
                    if (ed.size() == 0) sb.append(Tr.t("sc_no_edits", "Never edited."));
                    for (JsonElement e : ed) {
                        JsonObject o = e.getAsJsonObject();
                        sb.append(Social.timeAgo(Suda3D.str(o, "at"))).append("\n").append(Suda3D.str(o, "body")).append("\n\n");
                    }
                    sb.append(Tr.t("sc_now_text", "Current")).append(":\n").append(Suda3D.str(r, "current"));
                    new AlertDialog.Builder(a).setTitle(Tr.t("sc_history", "Edit history")).setMessage(sb.toString()).setPositiveButton(Tr.t("ok", "OK"), null).show();
                });
            } catch (Exception e) { Ui.ui(() -> Ui.toast(a, Social.err(e))); }
        });
    }

    private void startReply(Row r) {
        Social.needLogin(a, () -> {
            JsonObject c = r.c;
            long parent = r.type == T_REPLY ? (long) Suda3D.num(c, "parent_id", 0) : (long) Suda3D.num(c, "id", 0);
            replyParent = parent;
            replyTo = (long) Suda3D.num(c, "id", 0);
            editing = 0;
            String who = Suda3D.str(Suda3D.obj(c, "user"), "name");
            replyChip.setText(Tr.t("sc_replying_to", "Replying to") + " " + who);
            chipBar.setVisibility(View.VISIBLE);
            input.setText("");
            input.requestFocus();
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(input, 0);
        });
    }

    private View bindComment(Row r, boolean reply) {
        JsonObject c = r.c;
        JsonObject u = Suda3D.obj(c, "user");
        boolean deleted = Suda3D.bool(c, "deleted");
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.TOP);
        row.setPadding(Ui.dp(a, reply ? 52 : 12), Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 4));
        ImageView av = new ImageView(a);
        Social.avatar(a, av, deleted && !mod ? null : u, reply ? 28 : 36);
        if (!deleted || mod) av.setOnClickListener(v -> Social.openProfile(a, (long) Suda3D.num(u, "id", 0)));
        row.addView(av);
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 10), 0, 0, 0);

        if (deleted && !mod) {
            TextView t = Ui.text(a, Tr.t("sc_deleted", "Deleted"), 13.5f, Ui.TEXT_3);
            t.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            col.addView(t);
            row.addView(col, Ui.weight(1));
            return row;
        }
        LinearLayout head = Ui.row(a);
        head.addView(Social.nameRow(a, u, 13.5f));
        TextView when = Ui.text(a, "  " + Social.timeAgo(Suda3D.str(c, "created_at")) + (Suda3D.bool(c, "edited") ? " · " + Tr.t("sc_edited", "(edited)") : ""), 11, Ui.TEXT_3);
        head.addView(when);
        col.addView(head);
        if (deleted && mod && !r.revealed) {
            TextView t = Ui.text(a, Tr.t("sc_deleted", "Deleted"), 13.5f, Ui.TEXT_3);
            t.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            LinearLayout line = Ui.row(a);
            line.addView(t);
            TextView view = Ui.text(a, Tr.t("sc_view", "View"), 13, 0xFF64B5F6);
            view.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6));
            view.setOnClickListener(v -> { r.revealed = true; adapter.notifyDataSetChanged(); });
            line.addView(view);
            col.addView(line);
        } else {
            LinearLayout inner = Ui.column(a);
            TextView body = Ui.text(a, "", 14, deleted ? Ui.TEXT_3 : Ui.TEXT);
            JsonObject ru = c.has("reply_to") && c.get("reply_to").isJsonObject() ? c.getAsJsonObject("reply_to") : null;
            String text = Suda3D.str(c, "body");
            if (ru != null && reply) {
                // "@name text": the name is a profile link; the rest goes through the usual link handling
                inner.addView(replyTag(ru));
            }
            Social.linkify(body, text, Suda3D.obj(c, "projects"));
            if (deleted) body.setAlpha(0.7f);
            inner.addView(body);
            col.addView(inner);
        }
        // actions
        LinearLayout act = Ui.row(a);
        if (!deleted) {
            TextView rp = Ui.text(a, Tr.t("sc_reply", "Reply"), 12.5f, Ui.TEXT_2);
            rp.setTypeface(Typeface.DEFAULT_BOLD);
            rp.setPadding(0, Ui.dp(a, 8), Ui.dp(a, 18), Ui.dp(a, 6));
            rp.setOnClickListener(v -> startReply(r));
            act.addView(rp);
        }
        ImageView more = Ui.iconButton(a, R.drawable.ic_s2_more, Tr.t("more", "More"), v -> menu(v, r));
        act.addView(more);
        col.addView(act);
        row.addView(col, Ui.weight(1));
        return row;
    }

    private View replyTag(JsonObject ru) {
        TextView t = Ui.text(a, "@" + Suda3D.str(ru, "name"), 13, 0xFF64B5F6);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setOnClickListener(v -> Social.openProfile(a, (long) Suda3D.num(ru, "id", 0)));
        return t;
    }

    private View bindMore(Row r) {
        TextView t = Ui.text(a, Tr.t("sc_view_replies", "View replies") + (r.left > 0 ? " (" + r.left + ")" : ""), 12.5f, 0xFF64B5F6);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(a, 56), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 8));
        t.setOnClickListener(v -> loadReplies(r));
        return t;
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int i) { return rows.get(i).type; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int t) {
            FrameLayout_ f = new FrameLayout_(a);
            f.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(f) {};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
            Row r = rows.get(i);
            android.widget.FrameLayout f = (android.widget.FrameLayout) h.itemView;
            f.removeAllViews();
            f.addView(r.type == T_MORE_REPLIES ? bindMore(r) : bindComment(r, r.type == T_REPLY));
        }
    }

    private static final class FrameLayout_ extends android.widget.FrameLayout {
        FrameLayout_(android.content.Context c) { super(c); }
    }
}
