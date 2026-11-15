package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** Notifications of the account. What was read is kept on this phone only (a "last visit" time); the site keeps no read log. */
public class NotificationsActivity extends Activity {
    private LinearLayout list;
    private TextView state;
    private long seenAtOpen;
    private int page = 1;
    private boolean loading, more;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Tr.t("sc_notifications", "Notifications")), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        list = Ui.column(this);
        list.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 24));
        sv.addView(list);
        state = Ui.text(this, "", 14, Ui.TEXT_3);
        state.setGravity(Gravity.CENTER);
        state.setPadding(Ui.dp(this, 16), Ui.dp(this, 30), Ui.dp(this, 16), 0);
        FrameLayout body = new FrameLayout(this);
        body.addView(sv, Ui.match());
        body.addView(state, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            View c = sv.getChildAt(0);
            if (c != null && more && !loading && y + sv.getHeight() >= c.getHeight() - Ui.dp(this, 160)) load();
        });
        seenAtOpen = Social.lastSeen(this);
        if (!Suda3D.loggedIn(this)) { state.setText(Tr.t("sc_login_notif", "Sign in to see your notifications.")); return; }
        load();
    }

    private void load() {
        loading = true;
        final int p = page;
        if (p == 1) state.setText(Tr.t("loading", "Loading..."));
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = CommunityApi.notifications(this, p);
                long now = p == 1 ? (long) Suda3D.num(CommunityApi.notifCount(this, Long.MAX_VALUE / 2), "now", 0) : 0;
                Ui.ui(() -> {
                    loading = false;
                    state.setText("");
                    long base = Social.lastSeen(this) == 0 ? Long.MAX_VALUE : seenAtOpen;
                    int n = 0;
                    for (JsonElement e : CommunityApi.data(r)) { list.addView(row(e.getAsJsonObject(), base)); n++; }
                    if (p == 1 && n == 0) state.setText(Tr.t("sc_no_notifications", "Nothing here yet."));
                    more = (int) Suda3D.num(r, "page", 1) < (int) Suda3D.num(r, "last_page", 1);
                    page = p + 1;
                    if (p == 1 && now > 0) Social.setSeen(this, now);
                });
            } catch (Exception e) { Ui.ui(() -> { loading = false; state.setText(Social.err(e)); }); }
        });
    }

    private static String fill(String s, String name, int n, String project, String reason, String badge) {
        return s.replace("{name}", name).replace("{n}", String.valueOf(Math.max(0, n - 1))).replace("{project}", project)
                .replace("{reason}", reason).replace("{badge}", badge);
    }

    /** Text of one notification in the language of the app; several events of one kind are one line ("A and 3 others..."). */
    private String text(JsonObject n) {
        String type = Suda3D.str(n, "type");
        int count = (int) Suda3D.num(n, "count", 1);
        JsonObject actor = n.has("actor") && n.get("actor").isJsonObject() ? n.getAsJsonObject("actor") : null;
        JsonObject data = Suda3D.obj(n, "data");
        String name = actor == null ? Tr.t("sc_someone", "Someone") : Suda3D.str(actor, "name");
        String project = data == null ? "" : Suda3D.str(data, "name");
        String reason = data == null ? "" : Suda3D.str(data, "reason");
        String badge = data == null ? "" : Social.badgeTitle(Suda3D.str(data, "badge"));
        String key = "nt_" + type;
        String def;
        switch (type) {
            case "comment_post": def = count > 1 ? "{name} and {n} others commented on your post" : "{name} commented on your post"; break;
            case "comment_project": def = count > 1 ? "{name} and {n} others commented on {project}" : "{name} commented on {project}"; break;
            case "reply": def = count > 1 ? "{name} and {n} others replied to your comment" : "{name} replied to your comment"; break;
            case "star_project": def = count > 1 ? "{name} and {n} others starred {project}" : "{name} starred {project}"; break;
            case "editor_choice": def = "{project} was picked as Editor's Choice"; break;
            case "badge": def = "You earned a new badge: {badge}"; break;
            case "renamed": def = reason.isEmpty() ? "A moderator changed your name to {project}" : "A moderator changed your name to {project}. Reason: {reason}"; break;
            default: def = "{name}"; break;
        }
        String t = Tr.t(key + (count > 1 ? "_n" : ""), null);
        if (t == null || (type.equals("renamed") && !reason.isEmpty())) t = type.equals("renamed") && !reason.isEmpty() ? Tr.t("nt_renamed_reason", def) : def;
        return fill(t, name, count, project, reason, badge);
    }

    private View row(JsonObject n, long base) {
        boolean unread = (long) Suda3D.num(n, "ts", 0) > base;
        String type = Suda3D.str(n, "type");
        LinearLayout r = Ui.row(this);
        r.setBackground(Ui.rounded(unread ? 0xFF1E2A3A : Ui.SURFACE, Ui.dp(this, 12), unread ? Ui.ACCENT : Ui.BORDER, Ui.dp(this, 1)));
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        int icon, tone;
        switch (type) {
            case "star_project": icon = R.drawable.ic_s2_star; tone = 0xFFFFA000; break;
            case "editor_choice": icon = R.drawable.bdg_editor_choice; tone = 0xFFFB8C00; break;
            case "badge": icon = R.drawable.bdg_champion; tone = 0xFF7E57C2; break;
            case "renamed": icon = R.drawable.ic_s2_edit; tone = 0xFF78909C; break;
            case "reply": icon = R.drawable.ic_s2_reply; tone = 0xFF26A69A; break;
            default: icon = R.drawable.ic_s2_comment; tone = 0xFF1E88E5;
        }
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Ui.rounded((tone & 0x00FFFFFF) | 0x33000000, Ui.dp(this, 20)));
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        if (!type.equals("star_project")) ic.setImageTintList(android.content.res.ColorStateList.valueOf(tone));
        disc.addView(ic, new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22), Gravity.CENTER));
        r.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 10), 0, 0, 0);
        TextView t = Ui.text(this, text(n), 14, Ui.TEXT);
        if (unread) t.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(t);
        JsonObject data = Suda3D.obj(n, "data");
        String snip = data == null ? "" : Suda3D.str(data, "snippet");
        if (!snip.isEmpty() && !type.equals("star_project")) {
            TextView s = Ui.text(this, snip.replaceAll("@\\[project:\\d+\\]", "@"), 12.5f, Ui.TEXT_3);
            s.setMaxLines(2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(s);
        }
        col.addView(Ui.text(this, Social.timeAgo(Suda3D.str(n, "at")), 11, Ui.TEXT_3));
        r.addView(col, Ui.weight(1));
        if (unread) {
            View dot = new View(this);
            dot.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 5)));
            r.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10)));
        }
        r.setOnClickListener(v -> open(n));
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.bottomMargin = Ui.dp(this, 8);
        r.setLayoutParams(lp);
        return r;
    }

    private void open(JsonObject n) {
        String type = Suda3D.str(n, "type");
        long tid = (long) Suda3D.num(n, "target_id", 0);
        JsonObject data = Suda3D.obj(n, "data");
        switch (type) {
            case "comment_post": startActivity(new Intent(this, PostDetailActivity.class).putExtra("id", tid).putExtra("comments", true)); break;
            case "reply": {
                String on = data == null ? "" : Suda3D.str(data, "on");
                long onId = data == null ? 0 : (long) Suda3D.num(data, "on_id", 0);
                if ("post".equals(on)) startActivity(new Intent(this, PostDetailActivity.class).putExtra("id", onId).putExtra("comments", true));
                else if (onId > 0) Social.openProject(this, onId);
                break;
            }
            case "comment_project": case "star_project": case "editor_choice": Social.openProject(this, tid); break;
            default: Social.openProfile(this, Suda3D.userId(this));
        }
    }
}
