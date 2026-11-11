package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.behaviors.ObjectVarsGen;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * "Object variables" card of the Properties panel (docs/SCRIPTVARS.md "Object variables"): the variables of the selected
 * element, stored in its property {@code objectVars}, plus (read only) the ones made by "object variable" blocks in its events.
 * Every event of the element uses them by name; other elements read / write them with the blocks "variable ___ of ___".
 */
public final class ObjectVarsSection {
	private static boolean collapsed = false;

	private final Context ctx;
	private final LinearLayout container;
	private String json;
	private List<ObjectVarsGen.Var> fromBlocks = new ArrayList<>();

	public ObjectVarsSection(LinearLayout content, String element, String json) {
		this.ctx = content.getContext();
		this.json = json == null ? "" : json;
		container = Ui.column(ctx);
		content.addView(container, Ui.matchWrap());
		rebuild();
		Bridge.query(ObjectVarsSection::scanBlocks, l -> {
			if (l != null) fromBlocks = l;
			rebuild();
		});
	}

	/** Variables declared by blocks in the events of the selected element (GL thread). */
	private static List<ObjectVarsGen.Var> scanBlocks() {
		List<ObjectVarsGen.Var> out = new ArrayList<>();
		try {
			PropertySet<String, Object> ps = PropertySet.getPropertySet(Bridge.editor().getSelectedActor());
			String scene = Bridge.editor().getScene();
			String body = ps.getString(ps.containsKey("Script") ? "Script" : "name");
			if (body.contains("/")) {
				scene = body.split("/")[0];
				body = body.split("/")[1];
			}
			for (String ev : new String[] {"onClick", "onTouchStart", "onTouchEnd", "OnBodyCreated", "OnBodyUpdate", "onBodyCollided", "onBodyCollideEnd"}) {
				java.io.File f = new java.io.File(Bridge.editor().getProject().getEventPath(scene, body, ev) + ".java");
				if (!f.exists()) continue;
				for (ObjectVarsGen.Var v : ObjectVarsGen.fromCode(com.star4droid.star2d.behaviors.BehaviorCatalog.readFile(f)))
					if (ObjectVarsGen.find(out, v.name) == null) out.add(v);
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	/** The property changed behind our back (undo, AI, another view). */
	public void externalChange(String value) {
		if (value == null) value = "";
		if (value.equals(json)) return;
		json = value;
		rebuild();
	}

	private void save(List<ObjectVarsGen.Var> vars) {
		json = ObjectVarsGen.toJson(vars);
		final String v = json;
		Bridge.query(() -> PropertyEditing.applyString(Bridge.app(), ObjectVarsGen.PROPERTY, v), err -> {
			if (err != null) Ui.toast(ctx, err);
		});
		rebuild();
	}

	private void rebuild() {
		container.removeAllViews();
		LinearLayout card = Ui.column(ctx);
		card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(ctx, 10), Ui.BORDER, 1));
		container.addView(card, Ui.margins(Ui.matchWrap(), ctx, 0, 6, 0, 4));
		LinearLayout head = Ui.row(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4));
		head.setBackground(Ui.ripple(0, Ui.dp(ctx, 10)));
		TextView t = Ui.text(ctx, Tr.t("ov_title", "Object variables"), 14, Ui.TEXT);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		head.addView(t, Ui.weight(1));
		head.addView(Ui.iconButton(ctx, R.drawable.ic_s2_info, Tr.t("bhv_vars_info", "About"), v ->
				Dialogs.longText(ctx, Tr.t("ov_title", "Object variables"), Tr.t("ov_help",
						"Variables of this object. Every event of the object (all its blocks) uses them by name, e.g. health = health - 1. Each copy of the object has its own values, starting from the initial value.\n\nOther objects: blocks 'set variable of object' and 'number variable ___ of ___'. Java: ObjectVars.getFloat(item, \"health\"), item.setVar(\"health\", 3), item.getVarNumber(\"health\").\n\nA 'Local variable' block can become an object variable: long-press it in the blocks editor."))));
		head.addView(Ui.iconButton(ctx, R.drawable.ic_s2_add, Tr.t("ov_add", "Add variable"), v -> edit(-1)));
		ImageView arrow = Ui.smallIcon(ctx, collapsed ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more, Ui.TEXT_2, 20);
		head.addView(arrow);
		card.addView(head);
		LinearLayout body = Ui.column(ctx);
		body.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 8));
		body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
		card.addView(body);
		head.setOnClickListener(x -> {
			collapsed = body.getVisibility() == View.VISIBLE;
			body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
			Ui.setDirIcon(arrow, collapsed ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more);
		});

		List<ObjectVarsGen.Var> vars = ObjectVarsGen.parse(json);
		boolean any = false;
		for (int i = 0; i < vars.size(); i++) {
			body.addView(row(vars.get(i), i, false), Ui.matchWrap());
			any = true;
		}
		for (ObjectVarsGen.Var v : fromBlocks) {
			if (ObjectVarsGen.find(vars, v.name) != null) continue;
			body.addView(row(v, -1, true), Ui.matchWrap());
			any = true;
		}
		if (!any) {
			TextView e = Ui.text(ctx, Tr.t("ov_empty", "None yet. Tap + to add a variable that every event of this object can use."), 12.5f, Ui.TEXT_3);
			e.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));
			body.addView(e);
		}
	}

	private View row(ObjectVarsGen.Var v, int idx, boolean blocks) {
		LinearLayout r = Ui.row(ctx);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 2), Ui.dp(ctx, 6));
		r.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(ctx, 8), Ui.BORDER, 1));
		r.setLayoutParams(Ui.margins(Ui.matchWrap(), ctx, 0, 3, 0, 3));
		LinearLayout texts = Ui.column(ctx);
		TextView n = Ui.text(ctx, v.name, 13.5f, Ui.TEXT);
		n.setTypeface(Typeface.DEFAULT_BOLD);
		texts.addView(n);
		String val = v.value == null || v.value.isEmpty() ? "—" : v.value;
		if ("PathHolder".equals(ObjectVarsGen.normType(v.type)) && v.value != null && !v.value.isEmpty())
			val = (v.value.split(",").length / 2) + " " + Tr.t("path_points", "points");
		TextView d = Ui.text(ctx, typeLabel(v.type) + " · " + val + (v.sync ? "  ·  " + Tr.t("ov_synced", "synced") : "")
				+ (blocks ? "  ·  " + Tr.t("ov_from_blocks", "made by a block") : ""), 12, Ui.TEXT_3);
		d.setMaxLines(1);
		texts.addView(d);
		r.addView(texts, Ui.weight(1));
		if (!blocks) {
			r.setOnClickListener(x -> edit(idx));
			ImageView del = Ui.iconButton(ctx, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), x ->
					Dialogs.confirm(ctx, Tr.t("delete", "Delete"), v.name + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"), () -> {
						List<ObjectVarsGen.Var> l = ObjectVarsGen.parse(json);
						if (idx >= 0 && idx < l.size()) l.remove(idx);
						save(l);
					}));
			del.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFF8A80));
			r.addView(del);
		} else {
			// a block-made variable: copy it into the list to edit its initial value here (the list wins over the block)
			r.setOnClickListener(x -> {
				List<ObjectVarsGen.Var> l = ObjectVarsGen.parse(json);
				l.add(new ObjectVarsGen.Var(v.name, v.type, v.value));
				save(l);
			});
		}
		return r;
	}

	private static String typeLabel(String type) {
		switch (ObjectVarsGen.normType(type)) {
			case "int": return Tr.t("ov_t_int", "Whole number");
			case "float": return Tr.t("ov_t_float", "Number");
			case "double": return Tr.t("ov_t_double", "Precise number");
			case "String": return Tr.t("ov_t_text", "Text");
			case "boolean": return Tr.t("ov_t_bool", "Yes / no");
			case "List": return Tr.t("ov_t_list", "List");
			case "PlayerItem": return Tr.t("ov_t_element", "Element");
			case "PathHolder": return Tr.t("ov_t_path", "Path (points)");
		}
		return type;
	}

	/** Add (idx = -1) or edit a variable: name, type, initial value. */
	private void edit(int idx) {
		final List<ObjectVarsGen.Var> l = ObjectVarsGen.parse(json);
		final ObjectVarsGen.Var cur = idx >= 0 && idx < l.size() ? l.get(idx) : new ObjectVarsGen.Var("", "int", "0");
		final String[] type = {cur.type};
		LinearLayout box = Ui.column(ctx);
		box.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 8), Ui.dp(ctx, 18), 0);
		box.addView(Ui.text(ctx, Tr.t("ov_name", "Name"), 12, Ui.TEXT_2));
		EditText name = Ui.input(ctx, cur.name, "health");
		box.addView(name, Ui.matchWrap());
		box.addView(Ui.text(ctx, Tr.t("ov_type", "Type"), 12, Ui.TEXT_2));
		TextView typeBtn = Ui.text(ctx, typeLabel(type[0]) + "  ▾", 14, Ui.TEXT);
		typeBtn.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 10), Ui.dp(ctx, 10), Ui.dp(ctx, 10));
		typeBtn.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(ctx, 8), Ui.BORDER, 1));
		box.addView(typeBtn, Ui.matchWrap());
		TextView valueLabel = Ui.text(ctx, Tr.t("ov_value", "Initial value"), 12, Ui.TEXT_2);
		box.addView(valueLabel);
		EditText value = Ui.input(ctx, cur.value, "");
		box.addView(value, Ui.matchWrap());
		TextView hint = Ui.text(ctx, "", 11.5f, Ui.TEXT_3);
		box.addView(hint);
		// path: drawn on the canvas (tap to add points)
		final AlertDialog[] dlg = new AlertDialog[1];
		LinearLayout draw = Ui.button(ctx, R.drawable.ic_s2_navgrid, Tr.t("path_draw", "Draw on the canvas"), false, x -> {
			String nm = name.getText().toString().trim();
			if (!ObjectVarsGen.validName(nm)) {
				name.setError(Tr.t("ov_bad_name", "Use letters, digits and _ (not starting with a digit, not a Java word)"));
				return;
			}
			if (dlg[0] != null) dlg[0].dismiss();
			com.star4droid.star2d.ui.EditorShell sh = com.star4droid.star2d.ui.EditorShell.current;
			if (sh == null) return;
			if (sh.panels != null) sh.panels.hide();
			final boolean sync0 = cur.sync;
			com.star4droid.star2d.ui.PathDrawPanel.open(sh, nm, value.getText().toString(), pts -> {
				List<ObjectVarsGen.Var> now = ObjectVarsGen.parse(json);
				ObjectVarsGen.Var nv = new ObjectVarsGen.Var(nm, "PathHolder", pts);
				nv.sync = sync0;
				int at = -1;
				for (int i = 0; i < now.size(); i++) if (now.get(i).name.equals(idx >= 0 && idx < l.size() ? l.get(idx).name : nm)) at = i;
				if (at >= 0) now.set(at, nv); else now.add(nv);
				save(now);
				sh.panels.show(sh.properties);
			});
		});
		box.addView(draw, Ui.margins(Ui.matchWrap(), ctx, 0, 6, 0, 2));
		// network games: the same value on every phone
		androidx.appcompat.widget.SwitchCompat sync = new androidx.appcompat.widget.SwitchCompat(ctx);
		sync.setText(Tr.t("ov_sync", "Synced over the network"));
		sync.setTextColor(Ui.TEXT);
		sync.setChecked(cur.sync);
		box.addView(sync, Ui.margins(Ui.matchWrap(), ctx, 0, 6, 0, 0));
		Runnable hintFor = () -> {
			String nt = ObjectVarsGen.normType(type[0]);
			boolean path = "PathHolder".equals(nt);
			draw.setVisibility(path ? View.VISIBLE : View.GONE);
			sync.setVisibility(path || "List".equals(nt) || "PlayerItem".equals(nt) ? View.GONE : View.VISIBLE);
			switch (nt) {
				case "List": hint.setText(Tr.t("ov_hint_list", "Items separated by commas, e.g. 1, 2, apple")); break;
				case "PlayerItem": hint.setText(Tr.t("ov_hint_element", "Name of an element of the scene (long-press to choose)")); break;
				case "boolean": hint.setText("true / false"); break;
				case "PathHolder": hint.setText(Tr.t("ov_hint_path", "Points x1,y1,x2,y2... (empty = an empty path for \"Find path\")")); break;
				default: hint.setText(Tr.t("ov_hint_sync", "Synced: the host sends its value to every phone (network games)."));
			}
		};
		hintFor.run();
		typeBtn.setOnClickListener(x -> {
			String[] labels = new String[ObjectVarsGen.TYPES.length];
			for (int i = 0; i < labels.length; i++) labels[i] = typeLabel(ObjectVarsGen.TYPES[i]);
			Dialogs.choose(ctx, Tr.t("ov_type", "Type"), labels, w -> {
				type[0] = ObjectVarsGen.TYPES[w];
				typeBtn.setText(typeLabel(type[0]) + "  ▾");
				hintFor.run();
			});
		});
		value.setOnLongClickListener(x -> {
			if (!"PlayerItem".equals(type[0])) return false;
			Bridge.query(() -> new ArrayList<>(Bridge.editor().getBodiesList()), names -> {
				if (names == null || names.isEmpty()) return;
				String[] arr = names.toArray(new String[0]);
				Dialogs.choose(ctx, Tr.t("ov_t_element", "Element"), arr, w -> value.setText(arr[w]));
			});
			return true;
		});
		android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
		sv.addView(box);
		AlertDialog d = new AlertDialog.Builder(ctx)
				.setTitle(idx < 0 ? Tr.t("ov_add", "Add variable") : Tr.t("ov_edit", "Edit variable"))
				.setView(sv)
				.setPositiveButton(Tr.t("ok", "OK"), null)
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.create();
		dlg[0] = d;
		d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
			String nm = name.getText().toString().trim();
			if (!ObjectVarsGen.validName(nm)) {
				name.setError(Tr.t("ov_bad_name", "Use letters, digits and _ (not starting with a digit, not a Java word)"));
				return;
			}
			for (int i = 0; i < l.size(); i++)
				if (i != idx && l.get(i).name.equals(nm)) {
					name.setError(Tr.t("ov_dup_name", "This object already has a variable with this name"));
					return;
				}
			ObjectVarsGen.Var nv = new ObjectVarsGen.Var(nm, type[0], value.getText().toString());
			String nt = ObjectVarsGen.normType(type[0]);
			nv.sync = sync.isChecked() && !"PathHolder".equals(nt) && !"List".equals(nt) && !"PlayerItem".equals(nt);
			if (idx >= 0 && idx < l.size()) l.set(idx, nv); else l.add(nv);
			save(l);
			d.dismiss();
		}));
		d.show();
	}
}
