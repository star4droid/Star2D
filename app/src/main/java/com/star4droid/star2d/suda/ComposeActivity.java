package com.star4droid.star2d.suda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** New post: text, one of your own store projects with @, and pictures (each shrunk to the limit set on the site). */
public class ComposeActivity extends Activity {
    private static final int REQ_IMG = 41;

    private EditText text;
    private TextView counter, postBtn;
    private LinearLayout thumbs;
    private HorizontalScrollView thumbScroll;
    private final List<File> images = new ArrayList<>();
    private final List<long[]> mentionIds = new ArrayList<>();
    private final List<String> mentionNames = new ArrayList<>();
    private boolean sending, working;
    private boolean ignoreAt;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        postBtn = Ui.text(this, Tr.t("sc_publish", "Post"), 14, 0xFFFFFFFF);
        postBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        postBtn.setPadding(Ui.dp(this, 16), Ui.dp(this, 7), Ui.dp(this, 16), Ui.dp(this, 7));
        postBtn.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 16)));
        postBtn.setOnClickListener(v -> send());
        root.addView(SudaUi.topBar(this, Tr.t("sc_new_post", "New post"), postBtn), Ui.matchWrap());

        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 24));
        text = Ui.input(this, "", Tr.t("sc_compose_hint", "Share something with the community..."));
        text.setSingleLine(false);
        text.setMinLines(6);
        text.setGravity(Gravity.TOP);
        text.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setTextSize(15);
        text.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int cnt) {
                if (!ignoreAt && cnt == 1 && before == 0 && s.charAt(st) == '@') text.post(() -> pickProject(true));
            }
            @Override public void afterTextChanged(Editable s) { count(); }
        });
        col.addView(text, Ui.matchWrap());

        LinearLayout tools = Ui.row(this);
        tools.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        tools.addView(tool(R.drawable.ic_s2_image, Tr.t("sc_add_images", "Pictures"), v -> pickImages()));
        tools.addView(tool(R.drawable.ic_s2_mention, Tr.t("sc_mention_project", "Mention a project"), v -> pickProject(false)), Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 8, 0, 0, 0));
        counter = Ui.text(this, "", 12, Ui.TEXT_3);
        counter.setGravity(Gravity.END);
        tools.addView(counter, Ui.weight(1));
        col.addView(tools, Ui.matchWrap());

        thumbScroll = new HorizontalScrollView(this);
        thumbScroll.setHorizontalScrollBarEnabled(false);
        thumbs = Ui.row(this);
        thumbScroll.addView(thumbs);
        thumbScroll.setVisibility(View.GONE);
        col.addView(thumbScroll, Ui.matchWrap());
        TextView hint = Ui.text(this, Tr.t("sc_images_note", "Pictures are shrunk automatically to the allowed size."), 11.5f, Ui.TEXT_3);
        hint.setPadding(0, Ui.dp(this, 8), 0, 0);
        col.addView(hint);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        count();
        Suda3D.IO.execute(() -> { try { Social.setConfig(CommunityApi.config(this)); Ui.ui(this::count); } catch (Exception ignored) {} });
    }

    private View tool(int icon, String label, View.OnClickListener l) {
        LinearLayout r = Ui.row(this);
        r.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(this, 16)));
        r.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        r.addView(Ui.smallIcon(this, icon, Ui.TEXT_2, 18));
        TextView t = Ui.text(this, label, 12.5f, Ui.TEXT_2);
        t.setPadding(Ui.dp(this, 6), 0, 0, 0);
        r.addView(t);
        r.setOnClickListener(l);
        return r;
    }

    private void count() {
        int max = Social.cfgInt("max_post_len", 2000);
        int n = text.getText().length();
        counter.setText(n + " / " + max);
        counter.setTextColor(n > max ? Ui.DANGER : Ui.TEXT_3);
    }

    // ------------------------------------------------------------------ pictures
    private void pickImages() {
        int max = Social.cfgInt("max_images", 5);
        if (images.size() >= max) { Ui.toast(this, Social.err(new Suda3D.ApiException(422, "too_many_images", ""))); return; }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, Tr.t("sc_add_images", "Pictures")), REQ_IMG);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMG || res != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        int room = Social.cfgInt("max_images", 5) - images.size();
        if (uris.size() > room) { uris = uris.subList(0, Math.max(0, room)); Ui.toast(this, Social.err(new Suda3D.ApiException(422, "too_many_images", ""))); }
        final List<Uri> pick = uris;
        working = true;
        final int kb = Social.cfgInt("max_image_kb", 1024);
        Suda3D.IO.execute(() -> {
            for (Uri u : pick) {
                try {
                    File f = Social.compress(this, u, kb, 1600, "p" + System.nanoTime());
                    Ui.ui(() -> { images.add(f); addThumb(f); });
                } catch (Exception e) { Ui.ui(() -> Ui.toast(this, Tr.t("sc_image_fail", "Could not read this picture."))); }
            }
            Ui.ui(() -> working = false);
        });
    }

    private void addThumb(File f) {
        thumbScroll.setVisibility(View.VISIBLE);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this).load(f).into(iv);
        box.addView(iv, new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)));
        ImageView x = Ui.iconButton(this, R.drawable.ic_s2_close, Tr.t("delete", "Delete"), v -> {
            images.remove(f); thumbs.removeView(box);
            if (images.isEmpty()) thumbScroll.setVisibility(View.GONE);
        });
        x.setBackground(Ui.rounded(0xAA000000, Ui.dp(this, 14)));
        box.addView(x, new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28), Gravity.TOP | Gravity.END));
        thumbs.addView(box, Ui.margins(new LinearLayout.LayoutParams(-2, -2), this, 0, 8, 8, 0));
    }

    // ------------------------------------------------------------------ @project
    private void pickProject(boolean typedAt) {
        Social.needLogin(this, () -> Suda3D.IO.execute(() -> {
            try {
                List<JsonObject> mine = new ArrayList<>();
                for (JsonElement e : MarketApi.data(MarketApi.mine(this))) {
                    JsonObject o = e.getAsJsonObject();
                    if ("published".equals(Suda3D.str(o, "status"))) mine.add(o);
                }
                Ui.ui(() -> {
                    if (mine.isEmpty()) { Ui.toast(this, Tr.t("sc_no_published", "You have no published projects in the store.")); return; }
                    String[] names = new String[mine.size()];
                    for (int i = 0; i < names.length; i++) names[i] = Suda3D.str(mine.get(i), "name");
                    new AlertDialog.Builder(this).setTitle(Tr.t("sc_mention_project", "Mention a project"))
                            .setItems(names, (d, w) -> insertMention(typedAt, Suda3D.str(mine.get(w), "name"), (long) Suda3D.num(mine.get(w), "id", 0)))
                            .setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
                });
            } catch (Exception e) { Ui.ui(() -> Ui.toast(this, Social.err(e))); }
        }));
    }

    private void insertMention(boolean typedAt, String name, long id) {
        mentionNames.add(name);
        mentionIds.add(new long[]{id});
        ignoreAt = true;
        Editable ed = text.getText();
        int pos = Math.max(0, text.getSelectionStart());
        if (typedAt && pos > 0 && ed.charAt(pos - 1) == '@') { ed.delete(pos - 1, pos); pos--; }
        ed.insert(pos, "@" + name + " ");
        ignoreAt = false;
    }

    /** "@Name" the person inserted becomes the @[project:ID] token the site understands. */
    private String body() {
        String s = text.getText().toString();
        for (int i = 0; i < mentionNames.size(); i++) s = s.replace("@" + mentionNames.get(i), "@[project:" + mentionIds.get(i)[0] + "]");
        return s.trim();
    }

    // ------------------------------------------------------------------ send
    private void send() {
        if (sending) return;
        if (working) { Ui.toast(this, Tr.t("sc_wait_images", "Preparing pictures, one moment...")); return; }
        String b = body();
        if (b.isEmpty() && images.isEmpty()) return;
        if (text.getText().length() > Social.cfgInt("max_post_len", 2000)) { Ui.toast(this, Tr.t("err_too_long", "The text is too long.")); return; }
        Social.needLogin(this, () -> {
            sending = true;
            postBtn.setAlpha(0.5f);
            Suda3D.IO.execute(() -> {
                try {
                    JsonObject r = CommunityApi.createPost(this, b, new ArrayList<>(images));
                    CommunityActivity.dirty = true;
                    Ui.ui(() -> {
                        if (Suda3D.bool(r, "pending")) Ui.toast(this, Tr.t("sc_pending_note", "Your post will appear after a moderator approves it."));
                        finish();
                    });
                } catch (Exception e) {
                    Ui.ui(() -> { sending = false; postBtn.setAlpha(1f); Ui.toast(this, Social.err(e)); });
                }
            });
        });
    }
}
