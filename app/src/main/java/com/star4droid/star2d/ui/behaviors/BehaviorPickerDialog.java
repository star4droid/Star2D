package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Add behavior" dialog: grid or list with icon + description + (i) button, search, filter (All / UI / Physical / Project;
 * a 3D element gets 3D / All / Project and starts on 3D, a 2D element never sees 3D behaviors).
 * The dialog appears at once: the rows live in a RecyclerView (only the visible ones exist), icons come from a cache that is
 * filled in the background, and the catalog is read off the UI thread when it is not ready yet.
 */
public final class BehaviorPickerDialog {
	private BehaviorPickerDialog() {}

	public interface Pick {
		void on(BehaviorMeta meta);
	}

	private static boolean grid = true;

	private static final int T_HEADER = 0, T_CARD = 1, T_ROW = 2, T_TEXT = 3;

	private static final class Row {
		final int type;
		final String text;
		final BehaviorMeta meta;

		Row(int type, String text, BehaviorMeta meta) {
			this.type = type;
			this.text = text;
			this.meta = meta;
		}
	}

	private static final class VH extends RecyclerView.ViewHolder {
		TextView title, desc, note;
		ImageView icon, info;

		VH(View v) {
			super(v);
		}
	}

	/** Everything the adapter and the filter share. */
	private static final class State {
		final Context c;
		final BehaviorEnv env;
		final Set<String> already;
		final Pick pick;
		AlertDialog dialog;
		List<BehaviorMeta> all = new ArrayList<>();
		boolean loading = true;
		final List<Row> rows = new ArrayList<>();
		int filter;
		String query = "";

		State(Context c, BehaviorEnv env, Set<String> already, Pick pick) {
			this.c = c;
			this.env = env;
			this.already = already;
			this.pick = pick;
		}
	}

	public static void show(Context c, BehaviorEnv env, Set<String> already, Pick pick) {
		final State st = new State(c, env, already, pick);
		// a 3D element sees the 3D behaviors first (All also lists the 2D ones, flagged); a 2D / UI element never sees 3D ones
		final String[] filters, titles;
		if (env.is3D()) {
			filters = new String[] {"3d", "all", "project"};
			titles = new String[] {Tr.t("bhv_f_3d", "3D"), Tr.t("bhv_f_all", "All"), Tr.t("bhv_f_project", "Project")};
			st.filter = 0;
		} else if (env.elementType == null || env.elementType.isEmpty()) {
			filters = new String[] {"all", "ui", "physics", "3d", "project"};
			titles = new String[] {Tr.t("bhv_f_all", "All"), Tr.t("bhv_f_ui", "UI"), Tr.t("bhv_f_physics", "Physical"), Tr.t("bhv_f_3d", "3D"), Tr.t("bhv_f_project", "Project")};
			st.filter = env.isUi() ? 1 : 0;
		} else {
			filters = new String[] {"all", "ui", "physics", "project"};
			titles = new String[] {Tr.t("bhv_f_all", "All"), Tr.t("bhv_f_ui", "UI"), Tr.t("bhv_f_physics", "Physical"), Tr.t("bhv_f_project", "Project")};
			st.filter = env.isUi() ? 1 : 0;
		}

		LinearLayout col = Ui.column(c);
		col.setLayoutDirection(Ui.isRtl(c) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
		col.setPadding(Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10), 0);

		LinearLayout top = Ui.row(c);
		top.setGravity(Gravity.CENTER_VERTICAL);
		final EditText search = Ui.input(c, "", Tr.t("search", "Search"));
		top.addView(search, Ui.weight(1));
		final ImageView toggle = Ui.iconButton(c, grid ? R.drawable.ic_s2_view_list : R.drawable.ic_s2_view_grid, Tr.t("bhv_toggle_view", "Grid / list"), null);
		top.addView(toggle);
		col.addView(top, Ui.matchWrap());

		HorizontalScrollView hs = new HorizontalScrollView(c);
		hs.setHorizontalScrollBarEnabled(false);
		final LinearLayout chips = Ui.row(c);
		hs.addView(chips);
		col.addView(hs, Ui.matchWrap());

		final RecyclerView rv = new RecyclerView(c);
		final GridLayoutManager lm = new GridLayoutManager(c, grid ? 2 : 1);
		rv.setLayoutManager(lm);
		rv.setItemAnimator(null);
		rv.setItemViewCacheSize(10);
		rv.setClipToPadding(false);
		rv.setPadding(0, 0, 0, Ui.dp(c, 8));
		col.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.55f)));

		final RecyclerView.Adapter<VH> adapter = new RecyclerView.Adapter<VH>() {
			@Override public int getItemCount() { return st.rows.size(); }

			@Override public int getItemViewType(int pos) { return st.rows.get(pos).type; }

			@Override public VH onCreateViewHolder(ViewGroup parent, int type) {
				return create(st, type);
			}

			@Override public void onBindViewHolder(VH h, int pos) {
				bind(st, h, st.rows.get(pos));
			}
		};
		lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override public int getSpanSize(int pos) {
				if (pos < 0 || pos >= st.rows.size()) return 1;
				int t = st.rows.get(pos).type;
				return t == T_CARD ? 1 : lm.getSpanCount();
			}
		});
		rv.setAdapter(adapter);

		st.dialog = new AlertDialog.Builder(c)
				.setTitle(Tr.t("bhv_add_title", "Add behavior"))
				.setView(col)
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.create();

		final Runnable refill = new Runnable() {
			@Override public void run() {
				for (int i = 0; i < chips.getChildCount(); i++) styleChip(c, (TextView) chips.getChildAt(i), i == st.filter);
				fill(st, filters[st.filter]);
				adapter.notifyDataSetChanged();
				rv.scrollToPosition(0);
			}
		};
		for (int i = 0; i < filters.length; i++) {
			final int idx = i;
			TextView t = Ui.text(c, titles[i], 13, Ui.TEXT_2);
			t.setPadding(Ui.dp(c, 14), Ui.dp(c, 7), Ui.dp(c, 14), Ui.dp(c, 7));
			LinearLayout.LayoutParams lp = Ui.matchWrap();
			lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
			lp.setMarginEnd(Ui.dp(c, 6));
			lp.topMargin = Ui.dp(c, 4);
			lp.bottomMargin = Ui.dp(c, 4);
			t.setOnClickListener(v -> {
				st.filter = idx;
				refill.run();
			});
			chips.addView(t, lp);
		}
		toggle.setOnClickListener(v -> {
			grid = !grid;
			toggle.setImageResource(grid ? R.drawable.ic_s2_view_list : R.drawable.ic_s2_view_grid);
			lm.setSpanCount(grid ? 2 : 1);
			fill(st, filters[st.filter]);
			adapter.notifyDataSetChanged();
		});
		final Handler h = new Handler(Looper.getMainLooper());
		final Runnable typed = new Runnable() {
			@Override public void run() {
				String q = search.getText().toString().trim().toLowerCase();
				if (q.equals(st.query)) return;
				st.query = q;
				fill(st, filters[st.filter]);
				adapter.notifyDataSetChanged();
				rv.scrollToPosition(0);
			}
		};
		search.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int x) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int x) {
				h.removeCallbacks(typed);
				h.postDelayed(typed, 120);
			}
			@Override public void afterTextChanged(Editable s) {}
		});

		for (int i = 0; i < chips.getChildCount(); i++) styleChip(c, (TextView) chips.getChildAt(i), i == st.filter);
		st.dialog.show();

		// the data: at once when the catalog is warm, else read off the UI thread
		final Runnable loaded = new Runnable() {
			@Override public void run() {
				if (st.dialog == null || !st.dialog.isShowing()) return;
				st.all = BehaviorCatalog.all(env.projectPath);
				for (BehaviorMeta m : st.all) BehaviorText.search(m);
				st.loading = false;
				fill(st, filters[st.filter]);
				adapter.notifyDataSetChanged();
			}
		};
		if (BehaviorCatalog.isWarm()) loaded.run();
		else {
			fill(st, filters[st.filter]);
			adapter.notifyDataSetChanged();
			BehaviorText.background(() -> {
				List<BehaviorMeta> l = BehaviorCatalog.all(env.projectPath);
				for (BehaviorMeta m : l) BehaviorText.search(m);
				Ui.ui(loaded);
			});
		}
	}

	private static void styleChip(Context c, TextView t, boolean selected) {
		t.setTextColor(selected ? Ui.TEXT : Ui.TEXT_2);
		t.setBackground(Ui.rounded(selected ? Ui.ACCENT_DARK : Ui.SURFACE_3, Ui.dp(c, 16)));
	}

	/** Rebuilds the visible list (headers + cards) from the catalog, the filter chip and the search text. */
	private static void fill(State st, String f) {
		st.rows.clear();
		if (st.loading) {
			st.rows.add(new Row(T_TEXT, Tr.t("bhv_loading", "Loading…"), null));
			return;
		}
		String cat = null;
		int n = 0;
		boolean el2d = st.env.elementType != null && !st.env.elementType.isEmpty() && !st.env.is3D();
		for (BehaviorMeta m : st.all) {
			if (el2d && m.is3D()) continue; // never offer a 3D behavior to a 2D / UI element
			if (f.equals("3d") && !m.fits3D(st.env.is3D() ? st.env.elementType : "")) continue;
			if (f.equals("ui") && !(m.targets.equals(BehaviorMeta.UI) || m.targets.equals(BehaviorMeta.BOTH))) continue;
			if (f.equals("physics") && !(m.targets.equals(BehaviorMeta.PHYSICS) || m.targets.equals(BehaviorMeta.BOTH))) continue;
			if (f.equals("project") && m.builtin) continue;
			if (!st.query.isEmpty() && !BehaviorText.search(m).contains(st.query)) continue;
			String group = BehaviorText.listGroup(m);
			if (!group.equals(cat)) {
				cat = group;
				st.rows.add(new Row(T_HEADER, group, null));
			}
			st.rows.add(new Row(grid ? T_CARD : T_ROW, null, m));
			n++;
		}
		if (n == 0)
			st.rows.add(new Row(T_TEXT, f.equals("project") ? Tr.t("bhv_no_project", "No project behaviors yet. Create one from the Files panel (New > Behavior).") : Tr.t("bhv_none_found", "Nothing found"), null));
	}

	// ------------------------------------------------------------------------------------------------ views

	private static VH create(final State st, int type) {
		Context c = st.c;
		switch (type) {
			case T_HEADER: {
				TextView t = Ui.sectionLabel(c, "");
				t.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				VH h = new VH(t);
				h.title = t;
				return h;
			}
			case T_TEXT: {
				TextView t = Ui.text(c, "", 13, Ui.TEXT_3);
				t.setPadding(Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 8), 0);
				t.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				VH h = new VH(t);
				h.title = t;
				return h;
			}
			case T_CARD: return card(st);
			default: return listRow(st);
		}
	}

	private static VH card(State st) {
		Context c = st.c;
		FrameLayout root = new FrameLayout(c);
		RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		rlp.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
		root.setLayoutParams(rlp);
		LinearLayout card = Ui.column(c);
		card.setGravity(Gravity.CENTER_HORIZONTAL);
		card.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
		card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, 1));
		root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		VH h = new VH(root);
		h.icon = new ImageView(c);
		h.icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
		card.addView(h.icon);
		h.title = Ui.text(c, "", 13.5f, Ui.TEXT);
		h.title.setTypeface(Typeface.DEFAULT_BOLD);
		h.title.setGravity(Gravity.CENTER);
		h.title.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
		card.addView(h.title);
		h.desc = Ui.text(c, "", 11, Ui.TEXT_3);
		h.desc.setGravity(Gravity.CENTER);
		h.desc.setMaxLines(3);
		h.desc.setEllipsize(TextUtils.TruncateAt.END);
		card.addView(h.desc);
		h.note = Ui.text(c, "", 11, Ui.TEXT_3);
		h.note.setGravity(Gravity.CENTER);
		card.addView(h.note);
		h.info = BehaviorInfoDialog.button(c, 30, null);
		FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30), Gravity.TOP | Gravity.END);
		ilp.setMargins(0, Ui.dp(c, 2), Ui.dp(c, 2), 0);
		root.addView(h.info, ilp);
		return h;
	}

	private static VH listRow(State st) {
		Context c = st.c;
		LinearLayout row = Ui.row(c);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
		row.setBackground(Ui.ripple(0, Ui.dp(c, 8)));
		row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		VH h = new VH(row);
		h.icon = new ImageView(c);
		h.icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34)));
		row.addView(h.icon);
		LinearLayout txt = Ui.column(c);
		txt.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 6), 0);
		h.title = Ui.text(c, "", 14.5f, Ui.TEXT);
		h.title.setTypeface(Typeface.DEFAULT_BOLD);
		txt.addView(h.title);
		h.desc = Ui.text(c, "", 12, Ui.TEXT_3);
		h.desc.setMaxLines(3);
		h.desc.setEllipsize(TextUtils.TruncateAt.END);
		txt.addView(h.desc);
		h.note = Ui.text(c, "", 11, Ui.TEXT_3);
		txt.addView(h.note);
		row.addView(txt, Ui.weight(1));
		h.info = BehaviorInfoDialog.button(c, 36, null);
		row.addView(h.info);
		return h;
	}

	private static void bind(final State st, VH h, Row r) {
		if (r.meta == null) {
			h.title.setText(r.text);
			return;
		}
		final BehaviorMeta m = r.meta;
		boolean has = st.already.contains(m.id);
		BehaviorIcons.set(h.icon, m.icon, has ? Ui.TEXT_3 : Ui.ACCENT);
		h.title.setText(BehaviorText.name(m));
		h.desc.setText(BehaviorText.desc(m));
		String why = has ? null : BehaviorText.misfit(m, st.env);
		if (has) {
			h.note.setText(Tr.t("bhv_added", "Already added"));
			h.note.setTextColor(Ui.TEXT_3);
			h.note.setVisibility(View.VISIBLE);
		} else if (why != null) {
			h.note.setText("⚠ " + why);
			h.note.setTextColor(Ui.WARNING);
			h.note.setVisibility(View.VISIBLE);
		} else h.note.setVisibility(View.GONE);
		h.info.setOnClickListener(v -> BehaviorInfoDialog.show(st.c, st.env, m, null));
		h.itemView.setOnClickListener(v -> {
			st.dialog.dismiss();
			st.pick.on(m);
		});
	}
}
