package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** "Plan" tab of the AI entry: SUDA3D account, balance, buy credits, assistant-on-plan switch, generations. */
public class SudaPlanPanel implements PanelHost.Panel {
    /** Claude models the plan offers to the assistant (server prices them). */
    public static final String[] AI_MODELS = {"claude-sonnet-4-5", "claude-opus-4-5", "claude-haiku-4-5"};
    private final Activity activity;
    private ScrollView root;
    private LinearLayout col;
    private final Runnable onBalance = this::build;

    public SudaPlanPanel(Activity activity) { this.activity = activity; }

    @Override public String title() { return Tr.t("ai_tab_plan", "Plan"); }
    @Override public int icon() { return R.drawable.ic_s2_plan; }

    @Override
    public View view(Context c) {
        if (root == null) {
            root = new ScrollView(c);
            col = Ui.column(c);
            col.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 16));
            root.addView(col);
        }
        build();
        return root;
    }

    @Override public void onShown() {
        Suda3D.Balance.listen(onBalance);
        Jobs.init(activity);
        if (Suda3D.loggedIn(activity)) Suda3D.IO.execute(() -> { try { Suda3D.me(activity); } catch (Exception ignored) {} });
    }

    @Override public void onHidden() { Suda3D.Balance.unlisten(onBalance); }

    private void build() {
        if (col == null) return;
        Context c = col.getContext();
        col.removeAllViews();
        boolean in = Suda3D.loggedIn(c);

        LinearLayout acc = SudaUi.card(c);
        LinearLayout head = Ui.row(c);
        head.addView(Ui.smallIcon(c, R.drawable.ic_s2_wand, SudaUi.ORANGE, 24));
        TextView name = Ui.title(c, "SUDA3D");
        name.setPadding(Ui.dp(c, 8), 0, 0, 0);
        head.addView(name, Ui.weight(1));
        if (in) head.addView(Ui.iconButton(c, R.drawable.ic_s2_refresh, Tr.t("s3d_refresh", "Refresh"), v -> Suda3D.IO.execute(() -> {
            try { Suda3D.me(c); } catch (Exception e) { Ui.toast(c, Suda3D.message(e)); }
        })));
        acc.addView(head);
        if (in) {
            String em = Suda3D.email(c);
            acc.addView(Ui.text(c, em.isEmpty() ? Tr.t("s3d_signed_in_key", "Signed in with an API key") : em, 13, Ui.TEXT_3));
            TextView bal = Ui.text(c, Suda3D.fmtCredits(Suda3D.credits(c)) + " " + Tr.t("s3d_credits", "credits"), 26, SudaUi.ORANGE);
            bal.setTypeface(Typeface.DEFAULT_BOLD);
            bal.setPadding(0, Ui.dp(c, 8), 0, 0);
            acc.addView(bal);
            String usd = Suda3D.usd(c);
            if (!usd.isEmpty()) acc.addView(Ui.text(c, "$" + usd, 12.5f, Ui.TEXT_3));
        } else {
            acc.addView(Ui.text(c, Tr.t("s3d_plan_intro", "Sign in to generate images and 3D models and to use the AI assistant with SUDA3D credits."), 13, Ui.TEXT_2));
        }
        LinearLayout btns = Ui.row(c);
        btns.setPadding(0, Ui.dp(c, 10), 0, 0);
        if (in) {
            btns.addView(Ui.button(c, R.drawable.ic_s2_add, Tr.t("s3d_buy", "Buy credits"), true, v -> Paywall.offer(c)), Ui.weight(1));
            View gap = new View(c);
            btns.addView(gap, new LinearLayout.LayoutParams(Ui.dp(c, 8), 1));
            btns.addView(Ui.button(c, 0, Tr.t("s3d_signout", "Sign out"), false, v -> Dialogs.confirm(c, Tr.t("s3d_signout", "Sign out"),
                    Tr.t("areYouSure", "Are you sure?"), Tr.t("s3d_signout", "Sign out"), () -> { Suda3D.logout(c); build(); })));
        } else {
            btns.addView(Ui.button(c, R.drawable.ic_s2_key, Tr.t("s3d_signin_btn", "Sign in"), true, v -> Paywall.login(c, this::build)), Ui.weight(1));
            View gap = new View(c);
            btns.addView(gap, new LinearLayout.LayoutParams(Ui.dp(c, 8), 1));
            btns.addView(Ui.button(c, 0, Tr.t("s3d_buy", "Buy credits"), false, v -> Paywall.offer(c)), Ui.weight(1));
        }
        acc.addView(btns, Ui.matchWrap());
        col.addView(acc, Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 8));

        // assistant on plan
        LinearLayout ai = SudaUi.card(c);
        SwitchCompat sw = new SwitchCompat(c);
        sw.setText(Tr.t("s3d_ai_plan", "Assistant uses my SUDA3D plan"));
        sw.setTextColor(Ui.TEXT);
        sw.setChecked(Suda3D.aiUsesPlan(c));
        sw.setOnCheckedChangeListener((b, on) -> {
            if (on && !Suda3D.loggedIn(c)) { b.setChecked(false); Paywall.login(c, this::build); return; }
            Suda3D.setAiUsesPlan(c, on);
        });
        ai.addView(sw);
        ai.addView(Ui.text(c, Tr.t("s3d_ai_plan_hint", "The API tab's assistant is charged from your credits instead of your own key. The cost of every reply is shown under it."), 12, Ui.TEXT_3));
        String model = Suda3D.prefs(c).getString("plan_ai_model", AI_MODELS[0]);
        TextView mt = Ui.text(c, Tr.t("s3d_ai_model", "Model") + ": " + model, 13, Ui.ACCENT);
        mt.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 2));
        mt.setOnClickListener(v -> Dialogs.choose(c, Tr.t("s3d_ai_model", "Model"), AI_MODELS, i -> {
            Suda3D.prefs(c).edit().putString("plan_ai_model", AI_MODELS[i]).apply();
            build();
        }));
        ai.addView(mt);
        col.addView(ai, Ui.margins(Ui.matchWrap(), c, 0, 0, 0, 8));

        // generation
        col.addView(Ui.sectionLabel(c, Tr.t("s3d_generate", "Generate")));
        col.addView(row(c, R.drawable.ic_s2_view3d, Tr.t("s3d_op_image_to_model", "Image → 3D"), v -> QuickGen.show(activity, "image_to_model", null)));
        col.addView(row(c, R.drawable.ic_s2_image, Tr.t("s3d_op_text_to_image", "Text → Image"), v -> QuickGen.show(activity, "text_to_image", null)));
        col.addView(row(c, R.drawable.ic_s2_cube, Tr.t("s3d_op_text_to_model", "Text → 3D"), v -> QuickGen.show(activity, "text_to_model", null)));
        col.addView(row(c, R.drawable.ic_s2_animation, Tr.t("m2m_entry", "M2M Motion: rig & animate (free, on this phone)"), v -> M2MActivity.open(activity, null, null)));
        col.addView(row(c, R.drawable.ic_s2_nodes, Tr.t("s3d_workspace", "AI Workspace"), v -> activity.startActivity(new Intent(activity, com.star4droid.star2d.suda.ws.WorkspacesActivity.class))));
        col.addView(row(c, R.drawable.ic_s2_progress, Tr.t("s3d_running", "Running generations"), v -> QuickGen.showRunning(activity)));
        col.addView(row(c, R.drawable.ic_s2_history, Tr.t("s3d_history", "History"), v -> activity.startActivity(new Intent(activity, HistoryActivity.class))));

        // advanced
        col.addView(Ui.sectionLabel(c, Tr.t("s3d_advanced", "Advanced")));
        col.addView(row(c, R.drawable.ic_s2_link, Tr.t("s3d_server", "Server") + ": " + Suda3D.baseUrl(c), v ->
                Dialogs.input(c, Tr.t("s3d_server", "Server"), Tr.t("s3d_server_hint", "Leave empty for the default server."), Suda3D.baseUrl(c), null, u -> {
                    Suda3D.setBaseUrl(c, u.equals(Suda3D.DEFAULT_BASE_URL) ? "" : u);
                    build();
                })));
    }

    private View row(Context c, int icon, String label, View.OnClickListener l) {
        LinearLayout r = PanelHost.listRow(c, icon, Ui.TEXT_2, label, null);
        r.setOnClickListener(l);
        return r;
    }
}
