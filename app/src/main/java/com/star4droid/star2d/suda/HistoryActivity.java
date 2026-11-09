package com.star4droid.star2d.suda;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/**
 * History of SUDA3D operations (GET /jobs): status, credits spent, outputs with expiry and download / open / save.
 * Results of deleted workspace nodes are here too.
 */
public class HistoryActivity extends Activity implements OutputView.SafHost {
    private LinearLayout list;
    private TextView more, empty;
    private int page = 1;
    private java.io.File pendingSave;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Tr.t("s3d_history", "History"), SudaUi.balanceChip(this),
                Ui.iconButton(this, R.drawable.ic_s2_refresh, Tr.t("s3d_refresh", "Refresh"), v -> reload())), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 16));
        list = Ui.column(this);
        col.addView(list, Ui.matchWrap());
        empty = Ui.text(this, "", 14, Ui.TEXT_3);
        empty.setPadding(Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8), 0);
        col.addView(empty);
        more = Ui.text(this, Tr.t("s3d_load_more", "Load more"), 14, Ui.ACCENT);
        more.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14));
        more.setOnClickListener(v -> load(page + 1));
        more.setVisibility(View.GONE);
        col.addView(more);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
        reload();
    }

    private void reload() {
        list.removeAllViews();
        load(1);
    }

    private void load(int p) {
        if (!Suda3D.loggedIn(this)) {
            empty.setText(Tr.t("s3d_err_login", "Sign in to your SUDA3D account first"));
            empty.setOnClickListener(v -> Paywall.login(this, this::reload));
            return;
        }
        empty.setText(Tr.t("s3d_loading", "Loading…"));
        Suda3D.IO.execute(() -> {
            try {
                JsonObject r = Suda3D.jobs(this, p);
                JsonArray data = r.has("data") ? Suda3D.arr(r, "data") : Suda3D.arr(r, "jobs");
                boolean hasMore = data.size() > 0 && (Suda3D.num(r, "last_page", p) > p || !Suda3D.str(r, "next_page_url").isEmpty());
                runOnUiThread(() -> {
                    page = p;
                    for (JsonElement e : data) if (e.isJsonObject()) list.addView(row(e.getAsJsonObject()), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
                    empty.setText(list.getChildCount() == 0 ? Tr.t("s3d_history_empty", "No generations yet") : "");
                    more.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> empty.setText(Suda3D.message(e)));
            }
        });
    }

    private View row(JsonObject j) {
        LinearLayout card = SudaUi.card(this);
        String id = Suda3D.str(j, "job_id").isEmpty() ? Suda3D.str(j, "id") : Suda3D.str(j, "job_id");
        String opId = Suda3D.str(j, "op");
        Ops.Op op = Ops.get(opId);
        TextView t = Ui.text(this, op == null ? opId : op.label(), 15, Ui.TEXT);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(t);
        JsonObject cost = Suda3D.obj(j, "cost");
        double cr = cost != null ? Suda3D.num(cost, "credits", -1) : Suda3D.num(j, "credits", -1);
        String st = Suda3D.str(j, "status");
        String date = Suda3D.str(j, "created_at");
        long tm = SudaUi.parseTime(date);
        if (tm > 0) date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new java.util.Date(tm));
        TextView sub = Ui.text(this, SudaUi.statusLabel(st) + (cr >= 0 ? " · " + Suda3D.fmtCredits(cr) + " " + Tr.t("s3d_credits", "credits") : "") + " · " + date, 12.5f,
                "success".equals(st) ? Ui.SUCCESS : "failed".equals(st) ? Ui.DANGER : Ui.TEXT_3);
        card.addView(sub);
        JsonArray outs = Suda3D.arr(j, "outputs");
        if (outs.size() > 0) {
            Jobs.Job job = new Jobs.Job();
            job.id = id;
            job.op = opId;
            job.outputs = outs;
            LinearLayout box = Ui.column(this);
            QuickGen.fillOutputs(this, job, box, Store.ext(this, "suda3d/" + QuickGen.safe(id)));
            card.addView(box, Ui.matchWrap());
        }
        return card;
    }

    @Override
    public void createDocument(java.io.File f) {
        pendingSave = f;
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE)
                .setType(Suda3D.mime(f.getName())).putExtra(android.content.Intent.EXTRA_TITLE, f.getName());
        try { startActivityForResult(i, 4712); } catch (Exception e) { Ui.toast(this, Suda3D.message(e)); }
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 4712 && res == RESULT_OK && data != null && data.getData() != null && pendingSave != null)
            Store.saveToUri(this, pendingSave, data.getData(), null);
    }
}
