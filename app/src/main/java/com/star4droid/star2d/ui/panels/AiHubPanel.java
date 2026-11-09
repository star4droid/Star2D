package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/**
 * One AI entry in the rail with three tabs ("Plan" = SUDA3D account and credits): "Remote" (an AI chat such as Claude controls the editor through the
 * server; the default) and "API" (the in-editor assistant that talks to a model with the user's own key).
 */
public class AiHubPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private final PanelHost.Panel remote, api, plan;
    private PanelHost.Panel active;
    private LinearLayout root, tabs;
    private FrameLayout body;
    private TextView tabRemote, tabApi, tabPlan;

    public AiHubPanel(EditorShell shell, PanelHost.Panel remote, PanelHost.Panel api) {
        this.shell = shell;
        this.remote = remote;
        this.api = api;
        this.plan = new com.star4droid.star2d.suda.SudaPlanPanel(shell.activity());
        com.star4droid.star2d.suda.EditorEnv.install(shell);
        this.active = remote;
    }

    @Override public String title() { return Tr.t("ui_ai", "AI"); }
    @Override public int icon() { return R.drawable.ic_s2_ai; }

    @Override
    public View[] headerActions(Context c) {
        return active.headerActions(c);
    }

    public boolean showing(PanelHost.Panel p) { return active == p; }

    /** The SUDA3D "Plan" tab. */
    public PanelHost.Panel plan() { return plan; }

    /** Switch to the "API" or the "Remote" tab. */
    public void select(PanelHost.Panel p) {
        if (p == active && body != null && body.getChildCount() > 0) return;
        if (active != null && body != null) active.onHidden();
        active = p;
        if (root == null) return;
        body.removeAllViews();
        View v = active.view(root.getContext());
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        body.addView(v, Ui.match());
        styleTabs();
        shell.panels.refreshHeader();
        active.onShown();
    }

    @Override
    public View view(Context c) {
        if (root == null) {
            root = Ui.column(c);
            tabs = Ui.row(c);
            tabs.setGravity(Gravity.CENTER);
            tabs.setPadding(Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 6));
            tabRemote = tab(c, Tr.t("ai_tab_remote", "Remote"), remote);
            tabApi = tab(c, Tr.t("ai_tab_api", "API"), api);
            tabs.addView(tabRemote, Ui.weight(1));
            tabs.addView(tabApi, Ui.weight(1));
            tabPlan = tab(c, Tr.t("ai_tab_plan", "Plan"), plan);
            tabs.addView(tabPlan, Ui.weight(1));
            root.addView(tabs, Ui.matchWrap());
            body = new FrameLayout(c);
            root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        body.removeAllViews();
        View v = active.view(c);
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        body.addView(v, Ui.match());
        styleTabs();
        return root;
    }

    private TextView tab(Context c, String label, PanelHost.Panel target) {
        TextView t = Ui.text(c, label, 14f, Ui.TEXT_2);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(c, 9), 0, Ui.dp(c, 9));
        t.setOnClickListener(v -> select(target));
        return t;
    }

    private void styleTabs() {
        Context c = root.getContext();
        for (TextView t : new TextView[]{tabRemote, tabApi, tabPlan}) {
            boolean on = t == tabRemote ? active == remote : t == tabApi ? active == api : active == plan;
            t.setBackground(on ? Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(c, 10)) : null);
            t.setTextColor(on ? Ui.ACCENT : Ui.TEXT_2);
            t.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        }
    }

    @Override public void onShown() { active.onShown(); }
    @Override public void onHidden() { active.onHidden(); }
}
