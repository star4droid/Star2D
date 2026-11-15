package com.star4droid.star2d.suda;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Ui;

/** Star reaction: outlined gold star that pops (scale up, then back down) when it is given; count next to it. */
public class StarButton extends LinearLayout {
    private final ImageView icon;
    private final TextView count;
    private final String type;
    private final long id;
    private boolean on, busy;
    private int n;

    public StarButton(Context c, String type, long id, boolean on, int n) {
        super(c);
        this.type = type; this.id = id; this.on = on; this.n = n;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        icon = new ImageView(c);
        addView(icon, new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        count = Ui.text(c, "", 13, Ui.TEXT_2);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        LayoutParams lp = new LayoutParams(-2, -2);
        lp.setMarginStart(Ui.dp(c, 6));
        addView(count, lp);
        paint();
        setOnClickListener(v -> toggle());
    }

    private void paint() {
        Context c = getContext();
        if (on) { icon.setImageResource(R.drawable.ic_s2_star); icon.setImageTintList(null); }
        else { icon.setImageResource(R.drawable.ic_s2_star_border); icon.setImageTintList(ColorStateList.valueOf(Ui.TEXT_2)); }
        count.setText(String.valueOf(n));
        count.setTextColor(on ? 0xFFFFC107 : Ui.TEXT_2);
        setBackground(Ui.rounded(on ? 0x22FFC107 : Ui.SURFACE_2, Ui.dp(c, 18)));
    }

    private void pop() {
        icon.animate().cancel();
        icon.animate().scaleX(1.45f).scaleY(1.45f).setDuration(140).setInterpolator(new OvershootInterpolator(2f))
                .withEndAction(() -> icon.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
    }

    private void toggle() {
        Context c = getContext();
        if (busy) return;
        Social.needLogin(c, () -> {
            final boolean want = !on;
            on = want; n = Math.max(0, n + (want ? 1 : -1)); busy = true; paint();
            if (want) pop();
            Suda3D.IO.execute(() -> {
                try {
                    JsonObject r = CommunityApi.star(c, type, id, want);
                    Ui.ui(() -> { busy = false; if (r.has("stars")) { n = (int) Suda3D.num(r, "stars", n); paint(); } });
                } catch (Exception e) {
                    Ui.ui(() -> { busy = false; on = !want; n = Math.max(0, n + (want ? -1 : 1)); paint(); Ui.toast(c, Social.err(e)); });
                }
            });
        });
    }
}
