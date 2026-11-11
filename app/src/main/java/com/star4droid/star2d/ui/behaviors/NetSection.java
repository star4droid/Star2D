package com.star4droid.star2d.ui.behaviors;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.behaviors.NetSpec;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/**
 * "Network" card of the Properties panel (scenes with a NETWORK element): the element is the same on every phone of a
 * network game. Its owner moves it (physics runs there), the other phones show it smoothly; the host corrects cheaters and
 * lag. Stored in the element property {@code net} ({@link NetSpec}).
 */
public final class NetSection {
	private static boolean collapsed = false;
	private final Context ctx;
	private final LinearLayout container;
	private String json;

	public NetSection(LinearLayout content, String json) {
		this.ctx = content.getContext();
		this.json = json == null ? "" : json;
		container = Ui.column(ctx);
		content.addView(container, Ui.matchWrap());
		rebuild();
	}

	public void externalChange(String value) {
		if (value == null) value = "";
		if (value.equals(json)) return;
		json = value;
		rebuild();
	}

	private void save(NetSpec s) {
		json = s.toJson();
		final String v = json;
		Bridge.query(() -> PropertyEditing.applyString(Bridge.app(), NetSpec.PROPERTY, v), err -> {
			if (err != null) Ui.toast(ctx, err);
		});
		rebuild();
	}

	private static String ownerLabel(String owner) {
		if (owner == null || owner.equals("host")) return Tr.t("net_owner_host", "Host (player 1)");
		return Tr.t("net_owner_player", "Player") + " " + owner;
	}

	private static String fieldLabel(String f) {
		switch (f) {
			case "pos": return Tr.t("net_f_pos", "Position");
			case "rot": return Tr.t("net_f_rot", "Rotation");
			case "vel": return Tr.t("net_f_vel", "Speed");
			case "vis": return Tr.t("net_f_vis", "Visible");
			case "img": return Tr.t("net_f_img", "Image");
			case "anim": return Tr.t("net_f_anim", "Animation");
			case "text": return Tr.t("net_f_text", "Text");
			case "vars": return Tr.t("net_f_vars", "Synced variables");
		}
		return f;
	}

	private void rebuild() {
		container.removeAllViews();
		final NetSpec s = NetSpec.parse(json);
		LinearLayout card = Ui.column(ctx);
		card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(ctx, 10), s.on ? 0xFF43A047 : Ui.BORDER, 1));
		container.addView(card, Ui.margins(Ui.matchWrap(), ctx, 0, 6, 0, 4));
		LinearLayout head = Ui.row(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8));
		head.setBackground(Ui.ripple(0, Ui.dp(ctx, 10)));
		head.addView(Ui.smallIcon(ctx, R.drawable.ic_s2_network, s.on ? 0xFF81C784 : Ui.TEXT_3, 18));
		TextView t = Ui.text(ctx, Tr.t("net_card", "Network"), 14, Ui.TEXT);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		t.setPadding(Ui.dp(ctx, 8), 0, 0, 0);
		head.addView(t, Ui.weight(1));
		head.addView(Ui.iconButton(ctx, R.drawable.ic_s2_info, Tr.t("bhv_vars_info", "About"), v -> Dialogs.longText(ctx, Tr.t("net_card", "Network"),
				Tr.t("net_card_help", "A networked element is the same on every phone of the game.\n\nOwner: the phone that moves it (its joystick, its physics). Host = player 1; Player 2 = the first one who joins, and so on. The other phones show it smoothly and the host corrects it.\n\nThe player template of the Network element is copied for each player by itself (do not turn this on for it).\n\nSynced variables: variables of the element marked \"Synced over the network\"."))));
		card.addView(head);
		LinearLayout body = Ui.column(ctx);
		body.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 10));
		body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
		card.addView(body);
		head.setOnClickListener(x -> {
			collapsed = body.getVisibility() == View.VISIBLE;
			body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
		});
		SwitchCompat on = new SwitchCompat(ctx);
		on.setText(Tr.t("net_on", "Same on every phone"));
		on.setTextColor(Ui.TEXT);
		on.setChecked(s.on);
		on.setOnCheckedChangeListener((b, v) -> {
			s.on = v;
			save(s);
		});
		body.addView(on, Ui.matchWrap());
		if (!s.on) return;
		body.addView(Ui.text(ctx, Tr.t("net_owner", "Moved by"), 12, Ui.TEXT_2));
		TextView owner = Ui.text(ctx, ownerLabel(s.owner) + "  ▾", 14, Ui.TEXT);
		owner.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8));
		owner.setBackground(Ui.ripple(0xFF121417, Ui.dp(ctx, 7)));
		owner.setOnClickListener(v -> {
			String[] opts = new String[8];
			opts[0] = ownerLabel("host");
			for (int i = 2; i <= 8; i++) opts[i - 1] = ownerLabel(String.valueOf(i));
			Dialogs.choose(ctx, Tr.t("net_owner", "Moved by"), opts, i -> {
				s.owner = i == 0 ? "host" : String.valueOf(i + 1);
				save(s);
			});
		});
		body.addView(owner, Ui.margins(Ui.matchWrap(), ctx, 0, 2, 0, 6));
		body.addView(Ui.text(ctx, Tr.t("net_sync", "Kept the same"), 12, Ui.TEXT_2));
		android.widget.GridLayout g = new android.widget.GridLayout(ctx);
		g.setColumnCount(2);
		for (String f : NetSpec.FIELDS) {
			android.widget.CheckBox cb = new android.widget.CheckBox(ctx);
			cb.setText(fieldLabel(f));
			cb.setTextColor(Ui.TEXT);
			cb.setChecked(s.has(f));
			cb.setOnCheckedChangeListener((b, v) -> {
				s.set(f, v);
				save(s);
			});
			android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams(android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
					android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f));
			lp.width = 0;
			g.addView(cb, lp);
		}
		body.addView(g, Ui.matchWrap());
	}
}
