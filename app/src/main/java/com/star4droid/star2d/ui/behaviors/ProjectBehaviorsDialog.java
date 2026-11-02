package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorInfoEdit;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.icons.IconCatalog;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The behaviors written in this project: icon, name, description and a button to edit their basic properties (name,
 * description, category, target, icon) or open the code. Saving rewrites only the {@code @BehaviorInfo} header.
 */
public final class ProjectBehaviorsDialog {
	private ProjectBehaviorsDialog() {}

	public interface OpenCode { void open(String path); }

	public static void show(final Context c, final String projectPath, final OpenCode code, final Runnable changed) {
		final LinearLayout col = Ui.column(c);
		col.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), 0);
		final ScrollView sv = new ScrollView(c);
		sv.addView(col);
		final AlertDialog d = new AlertDialog.Builder(c).setTitle(Tr.t("pbh_title", "Project behaviors")).setView(sv)
				.setPositiveButton(Tr.t("close", "Close"), null).create();
		final Runnable[] fill = new Runnable[1];
		fill[0] = new Runnable() {
			@Override public void run() {
				col.removeAllViews();
				List<BehaviorMeta> list = BehaviorCatalog.project(projectPath);
				if (list.isEmpty()) {
					TextView t = Ui.text(c, Tr.t("pbh_empty", "This project has no behaviors yet. Create one from Files > + > New behavior."), 13.5f, Ui.TEXT_3);
					t.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12));
					col.addView(t);
					return;
				}
				for (final BehaviorMeta m : list) col.addView(row(c, m, code, fill[0], changed), Ui.margins(Ui.matchWrap(), c, 0, 3, 0, 3));
			}
		};
		fill[0].run();
		d.show();
	}

	private static View row(final Context c, final BehaviorMeta m, final OpenCode code, final Runnable refresh, final Runnable changed) {
		LinearLayout r = Ui.row(c);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 12), Ui.BORDER, Ui.dp(c, 1)));
		r.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
		r.addView(BehaviorIcons.view(c, m.icon, 34, Ui.ACCENT));
		LinearLayout tx = Ui.column(c);
		tx.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
		TextView name = Ui.text(c, m.name.isEmpty() ? m.id : m.name, 14.5f, Ui.TEXT);
		name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		tx.addView(name);
		String sub = (m.desc.isEmpty() ? m.id : m.desc) + "  ·  " + m.category + "  ·  " + m.targets;
		TextView desc = Ui.text(c, sub, 11.5f, Ui.TEXT_3);
		desc.setMaxLines(2);
		tx.addView(desc);
		r.addView(tx, Ui.weight(1));
		r.addView(Ui.iconButton(c, R.drawable.ic_s2_code, Tr.t("pbh_code", "Open code"), v -> code.open(m.file)));
		r.addView(Ui.iconButton(c, R.drawable.ic_s2_edit, Tr.t("pbh_edit", "Edit"), v -> edit(c, m, refresh, changed)));
		return r;
	}

	private static void edit(final Context c, final BehaviorMeta m, final Runnable refresh, final Runnable changed) {
		final File f = new File(m.file);
		final String[] icon = {m.icon};
		final String[] targets = {m.targets};
		LinearLayout form = Ui.column(c);
		form.setPadding(Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 18), 0);
		final EditText name = Ui.input(c, m.name, Tr.t("name", "Name"));
		form.addView(Ui.sectionLabel(c, Tr.t("name", "Name")));
		form.addView(name, Ui.matchWrap());
		final EditText desc = Ui.input(c, m.desc, Tr.t("pbh_desc", "One line: what it does"));
		form.addView(Ui.sectionLabel(c, Tr.t("pbh_desc_l", "Description")));
		form.addView(desc, Ui.matchWrap());
		final EditText cat = Ui.input(c, m.category, Tr.t("category", "Category"));
		form.addView(Ui.sectionLabel(c, Tr.t("category", "Category")));
		form.addView(cat, Ui.matchWrap());
		// target
		form.addView(Ui.sectionLabel(c, Tr.t("pbh_target", "Works on")));
		final TextView tg = Ui.text(c, targetName(targets[0]), 14, Ui.TEXT);
		tg.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
		tg.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 8), Ui.BORDER, Ui.dp(c, 1)));
		tg.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_expand_more, 0);
		tg.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_2));
		final String[] codes = {BehaviorMeta.BOTH, BehaviorMeta.UI, BehaviorMeta.PHYSICS, BehaviorMeta.THREE_D};
		tg.setOnClickListener(v -> Dialogs.choose(c, Tr.t("pbh_target", "Works on"),
				new String[]{targetName(codes[0]), targetName(codes[1]), targetName(codes[2]), targetName(codes[3])}, i -> {
					targets[0] = codes[i];
					tg.setText(targetName(codes[i]));
				}));
		form.addView(tg, Ui.matchWrap());
		// icon
		form.addView(Ui.sectionLabel(c, Tr.t("pbh_icon", "Icon")));
		LinearLayout ir = Ui.row(c);
		ir.setGravity(Gravity.CENTER_VERTICAL);
		final ImageView prev = BehaviorIcons.view(c, icon[0], 40, Ui.ACCENT);
		ir.addView(prev);
		final TextView iname = Ui.text(c, icon[0], 13, Ui.TEXT_2);
		iname.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
		ir.addView(iname, Ui.weight(1));
		ir.addView(Ui.button(c, R.drawable.ic_s2_search, Tr.t("pbh_pick_icon", "Choose icon"), false, v ->
				pickIcon(c, icon[0], id -> {
					icon[0] = id;
					iname.setText(id);
					BehaviorIcons.set(prev, id, Ui.ACCENT);
				})));
		form.addView(ir, Ui.matchWrap());
		ScrollView sv = new ScrollView(c);
		sv.addView(form);
		new AlertDialog.Builder(c).setTitle(m.name.isEmpty() ? m.id : m.name).setView(sv)
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.setPositiveButton(Tr.t("save", "Save"), (dd, w) -> {
					try {
						String src = BehaviorCatalog.readFile(f);
						String out = src;
						out = apply(out, "name", BehaviorInfoEdit.quote(name.getText().toString().trim()));
						out = apply(out, "icon", BehaviorInfoEdit.quote(icon[0]));
						out = apply(out, "desc", BehaviorInfoEdit.quote(desc.getText().toString().trim()));
						out = apply(out, "category", BehaviorInfoEdit.quote(cat.getText().toString().trim().isEmpty() ? "My behaviors" : cat.getText().toString().trim()));
						out = apply(out, "targets", "BehaviorInfo.Target." + targets[0]);
						if (out == null) {
							Ui.toast(c, Tr.t("pbh_no_header", "This file has no @BehaviorInfo header: edit it in the code."));
							return;
						}
						java.io.FileOutputStream os = new java.io.FileOutputStream(f);
						os.write(out.getBytes("UTF-8"));
						os.close();
						if (changed != null) changed.run();
						refresh.run();
					} catch (Exception e) {
						Ui.toast(c, "" + e);
					}
				}).show();
	}

	private static String apply(String src, String key, String value) {
		return src == null ? null : BehaviorInfoEdit.set(src, key, value);
	}

	private static String targetName(String t) {
		if (BehaviorMeta.UI.equals(t)) return Tr.t("pbh_t_ui", "UI elements");
		if (BehaviorMeta.PHYSICS.equals(t)) return Tr.t("pbh_t_physics", "Physical elements");
		if (BehaviorMeta.THREE_D.equals(t)) return Tr.t("pbh_t_3d", "3D elements (3D scenes)");
		return Tr.t("pbh_t_both", "Both");
	}

	interface IconPick { void on(String id); }

	/** Search the icon library and pick an id. */
	static void pickIcon(final Context c, String current, final IconPick pick) {
		final IconCatalog cat;
		try {
			cat = IconCatalog.get(c);
		} catch (Throwable t) {
			Ui.toast(c, "Icons: " + t.getMessage());
			return;
		}
		LinearLayout col = Ui.column(c);
		col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), 0);
		final EditText q = Ui.input(c, "", Tr.t("search", "Search"));
		col.addView(q, Ui.matchWrap());
		final ArrayList<IconCatalog.Icon> shown = new ArrayList<>();
		final RecyclerView rv = new RecyclerView(c);
		rv.setLayoutManager(new GridLayoutManager(c, 5));
		rv.setItemAnimator(null);
		col.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.5f)));
		final AlertDialog dlg = new AlertDialog.Builder(c).setTitle(Tr.t("pbh_pick_icon", "Choose icon")).setView(col)
				.setNegativeButton(Tr.t("cancel", "Cancel"), null).create();
		rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			@Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int t) {
				ImageView iv = new ImageView(c);
				int s = Ui.dp(c, 56);
				iv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s));
				iv.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
				return new RecyclerView.ViewHolder(iv) {};
			}

			@Override public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
				final IconCatalog.Icon ic = shown.get(i);
				ImageView iv = (ImageView) h.itemView;
				BehaviorIcons.set(iv, ic.id, Ui.TEXT);
				iv.setOnClickListener(v -> {
					dlg.dismiss();
					pick.on(ic.id);
				});
			}

			@Override public int getItemCount() { return shown.size(); }
		});
		final Runnable search = () -> {
			String s = q.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
			shown.clear();
			for (IconCatalog.Icon ic : cat.all()) {
				if (s.isEmpty() || ic.id.contains(s) || (ic.tags != null && ic.tags.toLowerCase(java.util.Locale.ROOT).contains(s))) shown.add(ic);
				if (shown.size() >= 250) break;
			}
			rv.getAdapter().notifyDataSetChanged();
		};
		q.addTextChangedListener(new android.text.TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
			@Override public void afterTextChanged(android.text.Editable e) { search.run(); }
		});
		search.run();
		dlg.show();
	}
}
