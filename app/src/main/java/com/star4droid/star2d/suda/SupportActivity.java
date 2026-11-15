package com.star4droid.star2d.suda;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.ui.CommunityLinks;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** The old community & support links page (groups, channels, donation), now behind the Support button of Community. */
public class SupportActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(SudaUi.topBar(this, Tr.t("sc_support", "Support")), Ui.matchWrap());
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 28));
        TextView h = Ui.text(this, Tr.t("home_community_title", "Community & Support"), 18, Ui.TEXT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(h);
        TextView sub = Ui.text(this, Tr.t("home_community_sub", "Connect with the Star2D developer community across the globe"), 13, Ui.TEXT_3);
        sub.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 12));
        col.addView(sub);

        LinearLayout don = Ui.column(this);
        don.setBackground(Ui.rounded(0xFF2B2418, Ui.dp(this, 14), 0xFF8A6A1F, Ui.dp(this, 1)));
        don.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        TextView dt = Ui.text(this, Tr.t("home_support", "💖 Support Engine Development"), 15, 0xFFFFCA28);
        dt.setTypeface(Typeface.DEFAULT_BOLD);
        don.addView(dt);
        TextView dd = Ui.text(this, Tr.t("home_support_sub", "Star2D is developed with passion. Help keep updates and features coming!"), 12.5f, Ui.TEXT_2);
        dd.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        don.addView(dd);
        LinearLayout btn = Ui.button(this, 0, Tr.t("home_donate", "Donate"), true, v -> startActivity(new Intent(this, com.star4droid.star2d.Activities.DonateActivity.class)));
        don.addView(btn);
        col.addView(don, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));

        for (String[] link : CommunityLinks.all()) {
            LinearLayout row = Ui.row(this);
            row.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 14), Ui.BORDER, Ui.dp(this, 1)));
            row.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
            TextView em = Ui.text(this, link[4], 24, Ui.TEXT);
            em.setGravity(Gravity.CENTER);
            row.addView(em, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
            LinearLayout tx = Ui.column(this);
            tx.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
            TextView t = Ui.text(this, Tr.t("home_c_" + link[0], link[1]), 14.5f, Ui.TEXT);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            tx.addView(t);
            tx.addView(Ui.text(this, Tr.t("home_c_" + link[0] + "_desc", link[2]), 12, Ui.TEXT_3));
            row.addView(tx, Ui.weight(1));
            row.addView(Ui.button(this, 0, Tr.t("home_join", "Join"), false, v -> Paywall.openUrl(this, link[3])));
            col.addView(row, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
        }
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        SudaUi.setup(this, root);
    }
}
