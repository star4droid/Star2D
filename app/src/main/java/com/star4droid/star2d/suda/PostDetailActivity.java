package com.star4droid.star2d.suda;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** One post, opened from the feed, a profile or a notification. */
public class PostDetailActivity extends Activity implements PostView.Host {
    private LinearLayout holder;
    private TextView state;
    private long id;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        id = getIntent().getLongExtra("id", 0);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Tr.t("sc_post", "Post")), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        holder = Ui.column(this);
        holder.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 24));
        sv.addView(holder);
        state = Ui.text(this, Tr.t("loading", "Loading..."), 14, Ui.TEXT_3);
        state.setPadding(Ui.dp(this, 16), Ui.dp(this, 30), Ui.dp(this, 16), 0);
        holder.addView(state);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        load(getIntent().getBooleanExtra("comments", false));
    }

    private void load(boolean openComments) {
        Suda3D.IO.execute(() -> {
            try {
                JsonObject p = CommunityApi.post(this, id);
                Ui.ui(() -> {
                    holder.removeAllViews();
                    holder.addView(PostView.build(this, p, false, true, this));
                    if (openComments) CommentsSheet.show(this, "post", id, (int) Suda3D.num(p, "comments", 0), null);
                });
            } catch (Exception e) {
                Ui.ui(() -> state.setText(e instanceof Suda3D.ApiException && ((Suda3D.ApiException) e).status == 404 ? Tr.t("sc_post_gone", "This post is not available.") : Social.err(e)));
            }
        });
    }

    @Override public void removed(long x) { CommunityActivity.dirty = true; finish(); }
    @Override public void changed() { CommunityActivity.dirty = true; holder.removeAllViews(); holder.addView(state); load(false); }
}
