package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The (i) dialog of a behavior: what it works on, what it needs, what it conflicts with, what it sets on the element,
 * its parameters (with defaults) and its events. All text goes through {@link BehaviorText}.
 */
final class BehaviorInfoDialog {
	private BehaviorInfoDialog() {}

	/** Extra buttons for project behaviors. */
	interface Actions {
		void exportS2b();

		void editCode();
	}

	/** The small round (i) button used in the rows of the Add dialog and in the header of every added behavior. */
	static ImageView button(Context c, int sizeDp, View.OnClickListener l) {
		ImageView b = new ImageView(c);
		int size = Ui.dp(c, sizeDp);
		int pad = Ui.dp(c, sizeDp / 4f);
		b.setImageResource(R.drawable.ic_s2_info);
		b.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_2));
		b.setPadding(pad, pad, pad, pad);
		b.setScaleType(ImageView.ScaleType.FIT_CENTER);
		b.setBackground(Ui.ripple(0, size / 2f));
		b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
		String tip = Tr.t("bhv_info", "Info");
		b.setContentDescription(tip);
		b.setOnClickListener(l);
		return b;
	}

	/**
	 * @param env the selected element (to say whether the behavior fits it), or null from places that have none
	 * @param actions export / edit buttons for a project behavior, or null
	 */
	static void show(Context c, BehaviorEnv env, BehaviorMeta m, Actions actions) {
		boolean rtl = Ui.isRtl(c);
		ScrollView sv = new ScrollView(c);
		LinearLayout col = Ui.column(c);
		col.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 6));
		col.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
		sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		header(c, col, m);

		String desc = BehaviorText.desc(m);
		if (desc.length() > 0) {
			TextView d = Ui.text(c, desc, 14, Ui.TEXT_2);
			d.setLineSpacing(0, 1.15f);
			d.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
			col.addView(d, Ui.matchWrap());
		}

		// does it fit the selected element?
		if (env != null && env.bodyType != null && env.bodyType.length() > 0) {
			String why = BehaviorText.misfit(m, env);
			TextView t = Ui.text(c, why != null ? "⚠  " + why : "✓  " + Tr.t("bhv_i_fits", "Fits the selected element"), 12.5f, why != null ? Ui.WARNING : Ui.SUCCESS);
			t.setPadding(Ui.dp(c, 10), Ui.dp(c, 7), Ui.dp(c, 10), Ui.dp(c, 7));
			t.setBackground(Ui.rounded(why != null ? 0x22FFB300 : 0x224CAF50, Ui.dp(c, 8)));
			LinearLayout.LayoutParams lp = Ui.matchWrap();
			lp.topMargin = Ui.dp(c, 10);
			col.addView(t, lp);
		}

		worksOn(c, col, m);
		requires(c, col, m);
		conflicts(c, col, env, m);
		sets(c, col, m);
		params(c, col, m);
		events(c, col, m);

		TextView src = Ui.text(c, (m.builtin ? Tr.t("bhv_builtin", "Built-in") : Tr.t("bhv_project", "Project") + ": " + m.fqn)
				+ "   ·   id: " + m.id, 11, Ui.TEXT_3);
		src.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
		col.addView(src);

		AlertDialog.Builder b = new AlertDialog.Builder(c).setView(sv).setPositiveButton(Tr.t("close", "Close"), null);
		if (actions != null && !m.builtin) {
			b.setNeutralButton(Tr.t("bhv_export", "Export .s2b"), (x, w) -> actions.exportS2b());
			b.setNegativeButton(Tr.t("bhv_edit_code", "Edit code"), (x, w) -> actions.editCode());
		}
		b.show();
	}

	// ------------------------------------------------------------------------------------------------ parts

	private static void header(Context c, LinearLayout col, BehaviorMeta m) {
		LinearLayout head = Ui.row(c);
		head.addView(BehaviorIcons.view(c, m.icon, 44, Ui.ACCENT));
		LinearLayout txt = Ui.column(c);
		txt.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
		TextView n = Ui.text(c, BehaviorText.name(m), 19, Ui.TEXT);
		n.setTypeface(Typeface.DEFAULT_BOLD);
		txt.addView(n);
		StringBuilder sub = new StringBuilder(BehaviorText.listGroup(m));
		sub.append("  ·  v").append(m.version);
		if (m.author != null && m.author.length() > 0) sub.append("  ·  ").append(m.author);
		txt.addView(Ui.text(c, sub.toString(), 12, Ui.TEXT_3));
		head.addView(txt, Ui.weight(1));
		col.addView(head, Ui.matchWrap());
	}

	private static TextView section(Context c, LinearLayout col, String title) {
		TextView t = Ui.sectionLabel(c, title);
		t.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 6));
		col.addView(t);
		return t;
	}

	private static TextView chip(Context c, String text, int bg, int fg) {
		TextView t = Ui.text(c, text, 12.5f, fg);
		t.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
		t.setBackground(Ui.rounded(bg, Ui.dp(c, 14)));
		return t;
	}

	private static FlowLayout flow(Context c) {
		return new FlowLayout(c, Ui.dp(c, 6));
	}

	private static TextView line(Context c, String text, int color) {
		TextView t = Ui.text(c, text, 13, color);
		t.setLineSpacing(0, 1.1f);
		t.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 2));
		return t;
	}

	private static void worksOn(Context c, LinearLayout col, BehaviorMeta m) {
		section(c, col, Tr.t("bhv_works_on", "Works on"));
		String t = m.effectiveTargets();
		String what;
		if (t.equals(BehaviorMeta.THREE_D)) what = Tr.t("bhv_i_3d_only", "3D elements only (3D scenes)");
		else if (t.equals(BehaviorMeta.UI)) what = Tr.t("bhv_i_ui_only", "UI elements only");
		else if (t.equals(BehaviorMeta.PHYSICS)) what = Tr.t("bhv_i_phys_only", "Physical elements only");
		else what = Tr.t("bhv_i_both", "Both UI and physical elements");
		TextView w = Ui.text(c, what, 14.5f, Ui.TEXT);
		w.setTypeface(Typeface.DEFAULT_BOLD);
		col.addView(w);
		String[] phys = m.physicalTypes(), ui = m.uiTypes();
		if (phys.length > 0) typeRow(c, col, Tr.t("bhv_f_physics", "Physical"), Tr.t("bhv_i_phys_hint", "has a physics body (Dynamic, Kinematic or Static)"), phys, 0x334CAF50, 0xFFA5D6A7);
		if (ui.length > 0) typeRow(c, col, Tr.t("bhv_f_ui", "UI"), Tr.t("bhv_i_ui_hint", "screen-space element (type UI)"), ui, Ui.ACCENT_SOFT, 0xFF90CAF9);
		String[] d3 = m.threeDTypes();
		if (d3.length > 0) typeRow(c, col, Tr.t("bhv_f_3d", "3D"), Tr.t("bhv_i_3d_hint", "element of the 3D world (meters, Y up)"), d3, 0x33AB47BC, 0xFFCE93D8);
	}

	private static void typeRow(Context c, LinearLayout col, String title, String hint, String[] types, int bg, int fg) {
		TextView h = Ui.text(c, title + "  ·  " + hint, 12, Ui.TEXT_3);
		h.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
		col.addView(h);
		FlowLayout f = flow(c);
		for (String ty : types) f.addView(chip(c, BehaviorText.elementType(ty), bg, fg));
		col.addView(f, Ui.matchWrap());
	}

	private static void requires(Context c, LinearLayout col, BehaviorMeta m) {
		section(c, col, Tr.t("bhv_i_needs", "Needs"));
		boolean any = false;
		if (m.reqUi) {
			col.addView(line(c, "•  " + Tr.t("bhv_i_need_ui", "A UI element (type UI)"), Ui.TEXT_2));
			any = true;
		}
		if (m.reqBody) {
			String ty = m.reqType.length() > 0 ? " (" + m.reqType + ")" : "";
			col.addView(line(c, "•  " + Tr.t("bhv_i_need_body", "A physical element with a body") + ty, Ui.TEXT_2));
			any = true;
		}
		String note = BehaviorText.note(m);
		if (note.length() > 0) {
			col.addView(line(c, "•  " + note, Ui.TEXT_2));
			any = true;
		}
		// elements chosen in the parameters (e.g. a progress bar)
		List<String> refs = new ArrayList<>();
		for (VarSpec v : m.vars) {
			if (v.kind != VarSpec.Kind.REF) continue;
			String[] types = com.star4droid.star2d.behaviors.ElementTypes.typesOf(v.elemType);
			StringBuilder tn = new StringBuilder();
			if (types == null) tn.append(Tr.t("bhv_i_any_element", "any element"));
			else for (String ty : types) tn.append(tn.length() > 0 ? ", " : "").append(BehaviorText.elementType(ty));
			refs.add(BehaviorText.param(m, v) + ": " + tn);
		}
		if (!refs.isEmpty()) {
			col.addView(line(c, "•  " + Tr.t("bhv_i_refs", "Other elements you pick in the parameters") + ":", Ui.TEXT_2));
			for (String r : refs) {
				TextView t = line(c, "      " + r, Ui.TEXT_3);
				col.addView(t);
			}
			any = true;
		}
		if (!any) col.addView(line(c, Tr.t("bhv_i_nothing", "Nothing special: it works as soon as it is added."), Ui.TEXT_3));
	}

	private static void conflicts(Context c, LinearLayout col, BehaviorEnv env, BehaviorMeta m) {
		if (m.exclusive.isEmpty()) return;
		section(c, col, Tr.t("bhv_i_conflicts", "Conflicts with"));
		String group = Tr.t("bhv_xg_" + m.exclusive, m.exclusive);
		col.addView(line(c, String.format(Tr.t("bhv_i_exclusive", "Only one behavior of the group \"%1$s\" can be on at a time. Adding another one asks to replace it or keep both (the new one off)."), group), Ui.TEXT_2));
		FlowLayout f = flow(c);
		f.setPadding(0, Ui.dp(c, 6), 0, 0);
		try {
			for (BehaviorMeta o : BehaviorCatalog.all(env == null ? null : env.projectPath))
				if (o != m && !o.id.equals(m.id) && m.exclusive.equals(o.exclusive)) f.addView(chip(c, BehaviorText.name(o), Ui.SURFACE_3, Ui.TEXT_2));
		} catch (RuntimeException ignored) {
		}
		if (f.getChildCount() > 0) col.addView(f, Ui.matchWrap());
	}

	private static void sets(Context c, LinearLayout col, BehaviorMeta m) {
		if (m.sets.isEmpty()) return;
		section(c, col, Tr.t("bhv_i_sets", "Changes on the element"));
		for (Map.Entry<String, String> e : m.sets.entrySet())
			col.addView(line(c, "•  " + BehaviorText.setKey(e.getKey()) + " = " + BehaviorText.setValue(e.getKey(), e.getValue()), Ui.TEXT_2));
		TextView h = Ui.text(c, Tr.t("bhv_i_sets_hint", "Applied while the game runs and the behavior is on. The saved properties of the element are not changed (use \"Apply to element\" to copy them)."), 11.5f, Ui.TEXT_3);
		h.setPadding(0, Ui.dp(c, 4), 0, 0);
		col.addView(h);
	}

	private static void params(Context c, LinearLayout col, BehaviorMeta m) {
		section(c, col, Tr.t("bhv_i_params", "Parameters"));
		List<VarSpec> vars = BehaviorText.vars(m);
		if (vars.isEmpty()) {
			col.addView(line(c, Tr.t("bhv_no_params", "No parameters."), Ui.TEXT_3));
			return;
		}
		String lastHeader = "";
		for (VarSpec v : vars) {
			if (v.header != null && v.header.length() > 0 && !v.header.equals(lastHeader)) {
				lastHeader = v.header;
				TextView h = Ui.text(c, v.header, 11.5f, Ui.ACCENT);
				h.setTypeface(Typeface.DEFAULT_BOLD);
				h.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 2));
				col.addView(h);
			}
			LinearLayout box = Ui.column(c);
			box.setPadding(Ui.dp(c, 10), Ui.dp(c, 7), Ui.dp(c, 10), Ui.dp(c, 7));
			box.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 8)));
			LinearLayout top = Ui.row(c);
			TextView n = Ui.text(c, v.displayLabel() + (v.unit != null && v.unit.length() > 0 ? "  (" + v.unit + ")" : ""), 13.5f, Ui.TEXT);
			n.setTypeface(Typeface.DEFAULT_BOLD);
			top.addView(n, Ui.weight(1));
			String def = defaultText(v);
			TextView dv = Ui.text(c, def.length() > 0 ? Tr.t("bhv_i_default", "default") + ": " + def : "", 12, Ui.TEXT_3);
			dv.setGravity(Gravity.END);
			dv.setPadding(Ui.dp(c, 8), 0, 0, 0);
			top.addView(dv);
			box.addView(top, Ui.matchWrap());
			TextView ty = Ui.text(c, kindText(v), 11, Ui.TEXT_3);
			box.addView(ty);
			if (v.desc != null && v.desc.length() > 0) {
				TextView d = Ui.text(c, v.desc, 12.5f, Ui.TEXT_2);
				d.setPadding(0, Ui.dp(c, 3), 0, 0);
				box.addView(d);
			}
			LinearLayout.LayoutParams lp = Ui.matchWrap();
			lp.topMargin = Ui.dp(c, 4);
			col.addView(box, lp);
		}
	}

	private static void events(Context c, LinearLayout col, BehaviorMeta m) {
		if (m.events.isEmpty()) return;
		section(c, col, Tr.t("bhv_events", "Events"));
		TextView h = Ui.text(c, Tr.t("bhv_i_events_hint", "You can attach blocks or code to these events (Events panel, or the Events button of the behavior)."), 11.5f, Ui.TEXT_3);
		h.setPadding(0, 0, 0, Ui.dp(c, 4));
		col.addView(h);
		for (BehaviorMeta.EventSpec e : m.events) {
			LinearLayout box = Ui.column(c);
			box.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
			box.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 8)));
			TextView n = Ui.text(c, BehaviorText.event(m, e), 13.5f, Ui.TEXT);
			n.setTypeface(Typeface.DEFAULT_BOLD);
			box.addView(n);
			if (!e.args.isEmpty()) {
				StringBuilder a = new StringBuilder(Tr.t("bhv_i_gives", "Gives") + ": ");
				for (int i = 0; i < e.args.size(); i++) a.append(i > 0 ? ", " : "").append(e.args.get(i)[1]).append(" (").append(e.args.get(i)[0]).append(')');
				box.addView(Ui.text(c, a.toString(), 11.5f, Ui.TEXT_3));
			}
			LinearLayout.LayoutParams lp = Ui.matchWrap();
			lp.topMargin = Ui.dp(c, 4);
			col.addView(box, lp);
		}
	}

	// ------------------------------------------------------------------------------------------------ value text

	private static String kindText(VarSpec v) {
		String k;
		switch (v.kind) {
			case INT: case LONG: case SHORT: case BYTE: k = Tr.t("bhv_k_int", "whole number"); break;
			case FLOAT: case DOUBLE: k = Tr.t("bhv_k_number", "number"); break;
			case BOOL: k = Tr.t("bhv_k_bool", "yes / no"); break;
			case STRING: k = Tr.t("bhv_k_text", "text"); break;
			case COLOR: k = Tr.t("bhv_k_color", "color"); break;
			case IMAGE: k = Tr.t("bhv_k_image", "image"); break;
			case SOUND: k = Tr.t("bhv_k_sound", "sound"); break;
			case ENUM: k = Tr.t("bhv_k_choice", "choice") + ": " + join(v.options); break;
			case REF: {
				String[] types = com.star4droid.star2d.behaviors.ElementTypes.typesOf(v.elemType);
				StringBuilder tn = new StringBuilder();
				if (types == null) tn.append(Tr.t("bhv_i_any_element", "any element"));
				else for (String ty : types) tn.append(tn.length() > 0 ? ", " : "").append(BehaviorText.elementType(ty));
				k = Tr.t("bhv_k_element", "element") + ": " + tn;
				break;
			}
			default: k = "";
		}
		if (v.list) k = Tr.t("bhv_k_list", "list of") + " " + k;
		if (v.isNumber() && (v.min > -1e8 || v.max < 1e8)) {
			String lo = v.min > -1e8 ? num(v.min) : "…", hi = v.max < 1e8 ? num(v.max) : "…";
			k += "   [" + lo + " → " + hi + "]";
		}
		return k;
	}

	private static String join(List<String> l) {
		StringBuilder sb = new StringBuilder();
		for (String s : l) sb.append(sb.length() > 0 ? ", " : "").append(s);
		return sb.toString();
	}

	private static String num(double d) {
		if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
		return String.format(Locale.ROOT, "%.3f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	private static String defaultText(VarSpec v) {
		Object d = v.def;
		if (d == null) return "";
		if (d instanceof List) {
			int n = ((List<?>) d).size();
			return n == 0 ? Tr.t("bhv_list_empty", "empty list") : String.valueOf(n) + " ×";
		}
		if (d instanceof Boolean) return ((Boolean) d) ? Tr.t("bhv_yes", "Yes") : Tr.t("bhv_no", "No");
		if (d instanceof Number) {
			double x = ((Number) d).doubleValue();
			return num(x);
		}
		String s = String.valueOf(d);
		return s.length() > 24 ? s.substring(0, 24) + "…" : s;
	}
}
