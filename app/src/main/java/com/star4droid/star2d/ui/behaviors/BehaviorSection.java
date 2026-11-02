package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorData;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.ScriptVarSync;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bottom of the Properties panel for the selected element:
 * 1. "Script variables": public variables of its Body Script (inspector values override the code's defaults),
 * 2. "Additional behaviors": names with an info icon, an add button, and the parameters of each behavior.
 * Everything is stored in the element's properties: {@code behaviors} and {@code scriptVars} (JSON, see BehaviorData).
 */
public final class BehaviorSection implements VarEditors.Host {
	private static final HashSet<String> COLLAPSED = new HashSet<>();
	private static final HashSet<String> EXPANDED = new HashSet<>();

	private final EditorShell shell;
	private final Context ctx;
	private final LinearLayout container;
	private final String element;
	private String bjson, vjson;
	private BehaviorEnv env;

	public BehaviorSection(EditorShell shell, LinearLayout content, String element, String behaviorsJson, String varsJson) {
		this.shell = shell;
		this.ctx = content.getContext();
		this.element = element;
		this.bjson = behaviorsJson == null ? "" : behaviorsJson;
		this.vjson = varsJson == null ? "" : varsJson;
		container = Ui.column(ctx);
		content.addView(container, Ui.matchWrap());
		Bridge.query(() -> BehaviorEnv.collect(element), e -> {
			env = e;
			// the built-in library is read on a background thread the first time (never on the UI thread)
			BehaviorText.whenReady(ctx, this::rebuild);
		});
	}

	@Override public Context ctx() { return ctx; }

	@Override public BehaviorEnv env() { return env; }

	/** The script's code changed (code editor closed): scan it again and redraw. */
	public void rescan() {
		Bridge.query(() -> new Object[] {BehaviorEnv.collect(element), ScriptVarWatcher.readVars(element)}, r -> {
			env = (BehaviorEnv) r[0];
			if (r[1] != null) vjson = (String) r[1];
			rebuild();
		});
	}

	/** The property changed behind our back (undo, AI...). */
	public void externalChange(String key, String value) {
		if (value == null) value = "";
		if (key.equals("behaviors") && !value.equals(bjson)) {
			bjson = value;
			refreshBehaviors();
		} else if (key.equals("scriptVars") && !value.equals(vjson)) {
			vjson = value;
			rebuild();
		}
	}

	// ------------------------------------------------------------------------------------------------ storage

	private void commit(String key, String json) {
		Bridge.query(() -> PropertyEditing.applyString(Bridge.app(), key, json), err -> {
			if (err != null) Ui.toast(ctx, err);
		});
	}

	/** Saves the list and redraws only the behaviors card (the script variables card is left alone). */
	private void saveBehaviors(List<BehaviorData.Entry> entries) {
		bjson = BehaviorData.toJson(entries);
		commit("behaviors", bjson);
		refreshBehaviors();
		shell.events.onSelectionChanged();
	}

	/** A parameter or the on / off switch changed: the list of events is the same, so the Events panel is not touched. */
	private void saveBehaviorValues(List<BehaviorData.Entry> entries, boolean redraw) {
		bjson = BehaviorData.toJson(entries);
		commit("behaviors", bjson);
		if (redraw) refreshBehaviors();
	}

	private void saveVars(Map<String, Object> vars) {
		vjson = BehaviorData.varsToJson(vars);
		commit("scriptVars", vjson);
		rebuild();
	}

	// ------------------------------------------------------------------------------------------------ cards

	private LinearLayout card(String title, String key, View... actions) {
		LinearLayout card = Ui.column(ctx);
		card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(ctx, 10), Ui.BORDER, 1));
		container.addView(card, Ui.margins(Ui.matchWrap(), ctx, 0, 6, 0, 4));
		LinearLayout head = Ui.row(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4));
		head.setBackground(Ui.ripple(0, Ui.dp(ctx, 10)));
		TextView t = Ui.text(ctx, title, 14, Ui.TEXT);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		head.addView(t, Ui.weight(1));
		for (View a : actions) head.addView(a);
		ImageView arrow = Ui.smallIcon(ctx, COLLAPSED.contains(key) ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more, Ui.TEXT_2, 20);
		head.addView(arrow);
		card.addView(head);
		LinearLayout body = Ui.column(ctx);
		body.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 8));
		body.setVisibility(COLLAPSED.contains(key) ? View.GONE : View.VISIBLE);
		card.addView(body);
		head.setOnClickListener(x -> {
			boolean col = body.getVisibility() == View.VISIBLE;
			body.setVisibility(col ? View.GONE : View.VISIBLE);
			Ui.setDirIcon(arrow, col ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more);
			if (col) COLLAPSED.add(key); else COLLAPSED.remove(key);
		});
		return body;
	}

	private void rebuild() {
		if (env == null) return;
		container.removeAllViews();
		buildScriptVars();
		buildBehaviors();
	}

	// ------------------------------------------------------------------------------------------------ script variables

	private void buildScriptVars() {
		if (env.scriptFile == null) return;
		SourceScanner.Result r = SourceScanner.scanScript(BehaviorCatalog.readFile(new File(env.scriptFile)));
		if (r.complete) { // bring the stored list of code-declared fields up to date (old projects, edits made outside the code editor)
			String synced = ScriptVarSync.sync(vjson, r.vars);
			if (!ScriptVarSync.canonical(vjson).equals(synced)) {
				vjson = synced;
				ScriptVarWatcher.store(element, synced);
			}
		}
		if (r.vars.isEmpty()) return;
		Map<String, Object> ov = BehaviorData.parseVars(vjson);
		ImageView info = Ui.iconButton(ctx, R.drawable.ic_s2_info, Tr.t("bhv_vars_info", "About"), v ->
				Dialogs.longText(ctx, Tr.t("bhv_vars_title", "Script variables"), Tr.t("bhv_vars_help",
						"Every public variable of this element's Body Script is listed here. A value set here replaces the one written in the code (reset brings the code's value back). Elements can be chosen from the scene: declare e.g. public ProgressItem hpBar; in the script and pick it here.")));
		LinearLayout body = card(Tr.t("bhv_vars_title", "Script variables") + " · " + r.className, "vars", info);
		for (VarSpec v : r.vars) {
			body.addView(VarEditors.field(this, v, ov.get(v.name), val -> {
				Map<String, Object> m = BehaviorData.parseVars(vjson);
				if (val == null) m.remove(v.name); else m.put(v.name, val);
				saveVars(m);
			}));
		}
	}

	// ------------------------------------------------------------------------------------------------ behaviors

	/** Body of the "Additional behaviors" card: its rows are redrawn on their own, without touching the rest of the panel. */
	private LinearLayout behaviorsBody;

	private void buildBehaviors() {
		ImageView add = Ui.iconButton(ctx, R.drawable.ic_s2_add, Tr.t("bhv_add_title", "Add behavior"), v -> {
			HashSet<String> has = new HashSet<>();
			for (BehaviorData.Entry e : BehaviorData.parse(bjson)) has.add(e.type);
			BehaviorPickerDialog.show(ctx, env, has, this::add);
		});
		behaviorsBody = card(Tr.t("bhv_title", "Additional behaviors"), "behaviors", add);
		fillBehaviors();
	}

	/** Redraws the behavior rows from {@code bjson}; collapsed rows are cheap (their parameters are built when first opened). */
	private void refreshBehaviors() {
		if (env == null) return;
		if (behaviorsBody == null || behaviorsBody.getParent() == null) {
			rebuild();
			return;
		}
		fillBehaviors();
	}

	/** Metadata of the behaviors of the card being drawn: looked up once per redraw (a lookup can touch the project's files). */
	private final Map<String, BehaviorMeta> metaMemo = new HashMap<>();

	private BehaviorMeta metaOf(String type) {
		if (metaMemo.containsKey(type)) return metaMemo.get(type);
		BehaviorMeta m = BehaviorCatalog.find(type, env.projectPath);
		metaMemo.put(type, m);
		return m;
	}

	private void fillBehaviors() {
		LinearLayout body = behaviorsBody;
		metaMemo.clear();
		body.removeAllViews();
		List<BehaviorData.Entry> entries = BehaviorData.parse(bjson);
		if (entries.isEmpty()) {
			TextView t = Ui.text(ctx, Tr.t("bhv_empty", "None yet. Tap + to add ready-made behaviors (movement, shooting, effects...)."), 12.5f, Ui.TEXT_3);
			t.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));
			body.addView(t);
			return;
		}
		for (int i = 0; i < entries.size(); i++) body.addView(row(entries, i), Ui.matchWrap());
	}

	/** Why the later of two behaviors that set the same physics value wins. */
	private String setsWarning(List<BehaviorData.Entry> entries, int idx, BehaviorMeta m) {
		if (m.sets.isEmpty() || !entries.get(idx).enabled) return null;
		for (int j = 0; j < idx; j++) {
			BehaviorData.Entry o = entries.get(j);
			if (!o.enabled) continue;
			BehaviorMeta om = metaOf(o.type);
			if (om == null) continue;
			for (Map.Entry<String, String> e : m.sets.entrySet()) {
				String ov = om.sets.get(e.getKey());
				if (ov != null && !ov.equals(e.getValue()))
					return BehaviorText.name(om) + " " + Tr.t("bhv_sets_also", "also sets") + " " + BehaviorText.setKey(e.getKey()) + " = " + BehaviorText.setValue(e.getKey(), ov)
							+ " → " + Tr.t("bhv_sets_wins", "this one wins") + " (" + BehaviorText.setKey(e.getKey()) + " = " + BehaviorText.setValue(e.getKey(), e.getValue()) + ")";
			}
		}
		return null;
	}

	private View row(List<BehaviorData.Entry> entries, int idx) {
		final BehaviorData.Entry e = entries.get(idx);
		final BehaviorMeta m = metaOf(e.type);
		final LinearLayout box = Ui.column(ctx);
		box.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(ctx, 8), e.enabled ? ((Ui.ACCENT & 0x00FFFFFF) | 0xAA000000) : Ui.BORDER, Math.max(1, Ui.dp(ctx, 1.5f))));
		box.setLayoutParams(Ui.margins(Ui.matchWrap(), ctx, 0, 3, 0, 3));
		final String key = element + "|" + e.type;
		final boolean canOpen = m != null;

		LinearLayout head = Ui.row(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 4), Ui.dp(ctx, 2));
		head.setMinimumHeight(Ui.dp(ctx, 44));
		head.setBackground(Ui.ripple(0, Ui.dp(ctx, 8)));
		head.addView(m == null ? Ui.smallIcon(ctx, R.drawable.ic_s2_bug, Ui.DANGER, 26) : BehaviorIcons.view(ctx, m.icon, 26, e.enabled ? Ui.ACCENT : Ui.TEXT_3));
		TextView name = Ui.text(ctx, BehaviorText.name(m, e.type), 14, e.enabled ? Ui.TEXT : Ui.TEXT_3);
		name.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 6), 0);
		head.addView(name, Ui.weight(1));

		List<String> warns = new ArrayList<>();
		if (m == null) warns.add(Tr.t("bhv_missing", "This behavior no longer exists (its file was deleted). Remove it."));
		else {
			String misfit = BehaviorText.misfit(m, env);
			if (misfit != null) warns.add(misfit);
			String sw = setsWarning(entries, idx, m);
			if (sw != null) warns.add(sw);
		}
		if (!warns.isEmpty()) head.addView(Ui.smallIcon(ctx, R.drawable.ic_s2_bug, Ui.WARNING, 20));
		if (m != null) {
			SwitchCompat sw = new SwitchCompat(ctx);
			sw.setChecked(e.enabled);
			sw.setOnCheckedChangeListener((b, on) -> {
				e.enabled = on;
				// redraw after the switch finished its own animation (other rows may warn about the order of @Sets)
				box.post(() -> saveBehaviorValues(entries, true));
			});
			head.addView(sw);
			head.addView(BehaviorInfoDialog.button(ctx, 36, v -> BehaviorInfoDialog.show(ctx, env, m, m.builtin ? null : new BehaviorInfoDialog.Actions() {
				@Override public void exportS2b() { BehaviorShare.export(ctx, env.projectPath, m); }

				@Override public void editCode() {
					shell.panels.hide();
					shell.host().openJava(m.file);
				}
			})));
		}
		// chevron: turns when the parameters open / close
		final ImageView chevron = Ui.smallIcon(ctx, R.drawable.ic_s2_expand_more, Ui.TEXT_2, 20);
		chevron.setLayoutParams(Ui.margins(new LinearLayout.LayoutParams(Ui.dp(ctx, 20), Ui.dp(ctx, 20)), ctx, 4, 0, 6, 0));
		chevron.setRotation(canOpen && EXPANDED.contains(key) ? 180f : 0f);
		if (canOpen) head.addView(chevron);

		// the parameters: not even built until the row is opened (the common case is a list of closed rows)
		final LinearLayout inner = Ui.column(ctx);
		inner.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10), Ui.dp(ctx, 8));
		final boolean[] built = {false};
		final Runnable build = () -> {
			if (built[0] || m == null) return;
			built[0] = true;
			buildInner(inner, entries, idx, e, m, key);
		};
		head.setOnClickListener(v -> {
			if (!canOpen) return;
			if (EXPANDED.contains(key)) {
				EXPANDED.remove(key);
				chevron.animate().rotation(0f).setDuration(Collapse.DURATION).setInterpolator(Collapse.INTERPOLATOR).start();
				Collapse.hide(inner);
			} else {
				EXPANDED.add(key);
				build.run();
				chevron.animate().rotation(180f).setDuration(Collapse.DURATION).setInterpolator(Collapse.INTERPOLATOR).start();
				Collapse.show(inner, box.getWidth() > 0 ? box.getWidth() : container.getWidth());
			}
		});
		box.addView(head);

		for (String w : warns) {
			TextView t = Ui.text(ctx, "⚠ " + w, 11.5f, Ui.WARNING);
			t.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 8), Ui.dp(ctx, 4));
			box.addView(t);
		}
		if (m != null && !m.exclusive.isEmpty() && !e.enabled) {
			TextView t = Ui.text(ctx, Tr.t("bhv_off_group", "Off. Turn on from code with") + ": item.setBehaviorEnabled(\"" + m.id + "\", true)", 11, Ui.TEXT_3);
			t.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 8), Ui.dp(ctx, 4));
			box.addView(t);
		}

		box.addView(inner, Ui.matchWrap());
		if (canOpen && EXPANDED.contains(key)) build.run();
		else inner.setVisibility(View.GONE);
		return box;
	}

	/** The parameters and tools of one behavior row (built the first time the row is opened). */
	private void buildInner(LinearLayout inner, List<BehaviorData.Entry> entries, int idx, BehaviorData.Entry e, BehaviorMeta m, String key) {
		String req = BehaviorText.requirements(m);
		if (!req.isEmpty()) {
			TextView t = Ui.text(ctx, req, 11.5f, Ui.TEXT_3);
			t.setPadding(0, 0, 0, Ui.dp(ctx, 4));
			inner.addView(t);
		}
		final View[] resetAll = {null};
		final boolean car = "Car".equals(m.id);
		for (VarSpec v : BehaviorText.vars(m)) {
			if (car && (v.name.equals("frontJoint") || v.name.equals("rearJoint"))) continue; // edited with the pencil button
			addField(inner, inner.getChildCount(), v, entries, e, resetAll);
			if (car && v.name.equals("rearWheel")) addWheelJointRow(inner, entries, e);
		}
		if (m.vars.isEmpty()) inner.addView(Ui.text(ctx, Tr.t("bhv_no_params", "No parameters."), 12, Ui.TEXT_3));

		LinearLayout tools = Ui.row(ctx);
		tools.setGravity(Gravity.CENTER_VERTICAL);
		if (idx > 0) tools.addView(Ui.iconButton(ctx, R.drawable.ic_s2_arrow_up, Tr.t("bhv_up", "Move up (runs earlier)"), v -> {
			entries.add(idx - 1, entries.remove(idx));
			saveBehaviors(entries);
		}));
		if (idx < entries.size() - 1) tools.addView(Ui.iconButton(ctx, R.drawable.ic_s2_arrow_down, Tr.t("bhv_down", "Move down (runs later)"), v -> {
			entries.add(idx + 1, entries.remove(idx));
			saveBehaviors(entries);
		}));
		if (!m.events.isEmpty())
			tools.addView(Ui.button(ctx, R.drawable.ic_s2_events, Tr.t("bhv_events", "Events"), false, v -> eventsDialog(m)));
		if (!m.sets.isEmpty() && !env.isUi() && !env.is3D())
			tools.addView(Ui.button(ctx, R.drawable.ic_s2_check, Tr.t("bhv_apply", "Apply to element"), false, v -> applySets(m)));
		ImageView reset = Ui.iconButton(ctx, R.drawable.ic_s2_refresh, Tr.t("bhv_reset_all", "Reset all parameters"), v -> {
			e.params.clear();
			saveBehaviorValues(entries, true);
		});
		reset.setVisibility(e.params.isEmpty() ? View.GONE : View.VISIBLE);
		resetAll[0] = reset;
		tools.addView(reset);
		tools.addView(Ui.iconButton(ctx, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v ->
				Dialogs.confirm(ctx, Tr.t("delete", "Delete"), BehaviorText.name(m) + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"), () -> {
					entries.remove(idx);
					EXPANDED.remove(key);
					saveBehaviors(entries);
				})));
		inner.addView(tools, Ui.matchWrap());
	}


	// ------------------------------------------------------------------------------------------------ car wheel joints

	/** Car: "Wheel joints" row with a pencil. Pick the front or rear wheel, then edit its joint values (or reset them). */
	private void addWheelJointRow(LinearLayout parent, List<BehaviorData.Entry> entries, BehaviorData.Entry e) {
		LinearLayout r = Ui.row(ctx);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
		r.addView(Ui.text(ctx, Tr.t("bhv_wj_title", "Wheel joints"), 13, Ui.TEXT), Ui.weight(1));
		r.addView(Ui.iconButton(ctx, R.drawable.ic_s2_edit, Tr.t("bhv_wj_edit", "Edit the wheel joints"), v ->
				Dialogs.choose(ctx, Tr.t("bhv_wj_pick", "Which wheel?"), new String[]{Tr.t("bhv_wj_front", "Front wheel"), Tr.t("bhv_wj_rear", "Rear wheel")},
						which -> editWheelJoint(entries, e, which == 0))));
		parent.addView(r, Ui.matchWrap());
	}

	private void editWheelJoint(List<BehaviorData.Entry> entries, BehaviorData.Entry e, boolean front) {
		final String key = front ? "frontJoint" : "rearJoint";
		Object cur = e.params.get(key);
		final float[] vals = com.star4droid.template.behaviors.lib.Car.parseJoint(cur == null ? "" : String.valueOf(cur), front);
		final String[] labels = {Tr.t("bhv_wj_freq", "Frequency (Hz)"), Tr.t("bhv_wj_damp", "Damping ratio"), Tr.t("bhv_wj_torque", "Max motor torque"), Tr.t("bhv_wj_motor", "Enable motor"),
				Tr.t("bhv_wj_speed", "Motor speed (rad/s)"), Tr.t("bhv_wj_axisx", "Axis X"), Tr.t("bhv_wj_axisy", "Axis Y"), Tr.t("bhv_wj_ax", "Anchor offset X (px)"),
				Tr.t("bhv_wj_ay", "Anchor offset Y (px)"), Tr.t("bhv_wj_collide", "Collide connected")};
		final int n = labels.length;
		final View[] fields = new View[n];
		LinearLayout col = Ui.column(ctx);
		col.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 4));
		for (int i = 0; i < n; i++) {
			boolean bool = i == 3 || i == 9;
			if (bool) {
				SwitchCompat sw = new SwitchCompat(ctx);
				sw.setText(labels[i]);
				sw.setTextColor(Ui.TEXT);
				sw.setChecked(vals[i] != 0);
				fields[i] = sw;
				col.addView(sw, Ui.margins(Ui.matchWrap(), ctx, 0, 6, 0, 2));
			} else {
				col.addView(Ui.text(ctx, labels[i], 12, Ui.TEXT_3), Ui.margins(Ui.matchWrap(), ctx, 0, 6, 0, 0));
				android.widget.EditText et = Ui.input(ctx, fmt(vals[i]), "");
				et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
				fields[i] = et;
				col.addView(et, Ui.matchWrap());
			}
		}
		ScrollView sv = new ScrollView(ctx);
		sv.addView(col);
		final String title = Tr.t("bhv_wj_title", "Wheel joints") + " · " + (front ? Tr.t("bhv_wj_front", "Front wheel") : Tr.t("bhv_wj_rear", "Rear wheel"));
		new AlertDialog.Builder(ctx).setTitle(title).setView(sv)
				.setPositiveButton(Tr.t("save", "Save"), (d, w) -> {
					float[] out = new float[n];
					for (int i = 0; i < n; i++) {
						if (fields[i] instanceof SwitchCompat) out[i] = ((SwitchCompat) fields[i]).isChecked() ? 1f : 0f;
						else {
							try {
								out[i] = Float.parseFloat(((android.widget.EditText) fields[i]).getText().toString().trim());
							} catch (NumberFormatException ex) {
								out[i] = com.star4droid.template.behaviors.lib.Car.jointDefaults(front)[i];
							}
						}
					}
					String str = com.star4droid.template.behaviors.lib.Car.formatJoint(out, front);
					if (str.isEmpty()) e.params.remove(key); else e.params.put(key, str);
					saveBehaviorValues(entries, true);
				})
				.setNeutralButton(Tr.t("bhv_wj_reset", "Reset"), (d, w) -> {
					e.params.remove(key);
					saveBehaviorValues(entries, true);
				})
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.show();
	}

	private static String fmt(float v) {
		return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
	}

	/** One parameter editor. A change is saved at once and only this field is drawn again (no redraw of the card, the open row stays open). */
	private void addField(LinearLayout parent, int at, VarSpec v, List<BehaviorData.Entry> entries, BehaviorData.Entry e, View[] resetAll) {
		final View[] self = {null};
		self[0] = VarEditors.field(this, v, e.params.get(v.name), val -> {
			if (val == null) e.params.remove(v.name); else e.params.put(v.name, val);
			saveBehaviorValues(entries, false);
			if (resetAll[0] != null) resetAll[0].setVisibility(e.params.isEmpty() ? View.GONE : View.VISIBLE);
			parent.post(() -> {
				int i = parent.indexOfChild(self[0]);
				if (i < 0) return;
				parent.removeViewAt(i);
				addField(parent, i, v, entries, e, resetAll);
			});
		});
		parent.addView(self[0], at);
	}

	// ------------------------------------------------------------------------------------------------ adding

	private void add(BehaviorMeta m) {
		List<BehaviorData.Entry> entries = BehaviorData.parse(bjson);
		if (BehaviorData.find(entries, m.id) != null) {
			Ui.toast(ctx, Tr.t("bhv_already", "Already added to this element"));
			return;
		}
		List<BehaviorData.Entry> clash = new ArrayList<>();
		if (!m.exclusive.isEmpty())
			for (BehaviorData.Entry e : entries) {
				BehaviorMeta o = BehaviorCatalog.find(e.type, env.projectPath);
				if (o != null && o.exclusive.equals(m.exclusive)) clash.add(e);
			}
		if (clash.isEmpty()) {
			entries.add(new BehaviorData.Entry(m.id));
			saveBehaviors(entries);
			return;
		}
		StringBuilder names = new StringBuilder();
		for (BehaviorData.Entry e : clash) {
			BehaviorMeta o = BehaviorCatalog.find(e.type, env.projectPath);
			names.append(names.length() > 0 ? ", " : "").append(BehaviorText.name(o, e.type));
		}
		String msg = names + " " + Tr.t("bhv_and", "and") + " " + BehaviorText.name(m) + " " + Tr.t("bhv_both_control", "both control") + " \"" + Tr.t("bhv_xg_" + m.exclusive, m.exclusive) + "\".\n\n"
				+ Tr.t("bhv_conflict_help", "Replace: the old one is removed. Keep both: the new one is added switched OFF; switch with item.setBehaviorEnabled(\"Name\", true) (it turns the others of the group off).");
		AlertDialog d = new AlertDialog.Builder(ctx)
				.setTitle(Tr.t("bhv_conflict", "Conflict"))
				.setMessage(msg)
				.setPositiveButton(Tr.t("bhv_replace", "Replace"), (x, w) -> {
					int at = entries.indexOf(clash.get(0));
					entries.removeAll(clash);
					entries.add(Math.min(at, entries.size()), new BehaviorData.Entry(m.id));
					saveBehaviors(entries);
				})
				.setNeutralButton(Tr.t("bhv_keep_both", "Keep both (only one enabled)"), (x, w) -> {
					BehaviorData.Entry n = new BehaviorData.Entry(m.id);
					n.enabled = false;
					entries.add(n);
					saveBehaviors(entries);
				})
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.create();
		d.show();
	}

	/** Bakes the physics settings of a behavior (@Sets) into the saved element properties. */
	private void applySets(BehaviorMeta m) {
		Bridge.query(() -> {
			PropertySet<String, Object> ps = PropertySet.getPropertySet(Bridge.editor().getSelectedActor());
			int n = 0;
			for (Map.Entry<String, String> e : m.sets.entrySet()) {
				String prop = BehaviorMeta.propertyOf(e.getKey());
				if (prop == null || ps == null || !ps.containsKey(prop)) continue;
				String v = e.getValue();
				if (e.getKey().equals("type")) PropertyEditing.applySpinner(Bridge.app(), prop, v.toUpperCase());
				else if (e.getKey().equals("gravityScale")) {
					try { PropertyEditing.applyFloat(Bridge.app(), prop, Float.parseFloat(v)); } catch (NumberFormatException ex) { continue; }
				} else PropertyEditing.applyBoolean(Bridge.app(), prop, String.valueOf(Boolean.parseBoolean(v)));
				n++;
			}
			return n;
		}, n -> Ui.toast(ctx, Tr.t("bhv_applied", "Properties updated") + ": " + n));
	}

	// ------------------------------------------------------------------------------------------------ dialogs

	/** The events of a behavior (translated names, the code's signature under each): tap one to open its blocks. */
	private void eventsDialog(BehaviorMeta m) {
		LinearLayout col = Ui.column(ctx);
		col.setLayoutDirection(Ui.isRtl(ctx) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
		col.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12), 0);
		Bridge.query(() -> {
			List<String[]> l = BehaviorEvents.listFor(element, bjson);
			List<String[]> mine = new ArrayList<>();
			for (String[] r : l) if (r[0].equals(m.id)) mine.add(r);
			return mine;
		}, rows -> {
			AlertDialog[] dlg = new AlertDialog[1];
			for (String[] r : rows) {
				LinearLayout line = Ui.row(ctx);
				line.setGravity(Gravity.CENTER_VERTICAL);
				line.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8));
				line.setBackground(Ui.ripple(0, Ui.dp(ctx, 8)));
				LinearLayout txt = Ui.column(ctx);
				TextView t = Ui.text(ctx, r[3], 14.5f, Ui.TEXT);
				t.setTypeface(Typeface.DEFAULT_BOLD);
				txt.addView(t);
				TextView sig = Ui.text(ctx, r[5], 11, Ui.TEXT_3);
				sig.setTypeface(Typeface.MONOSPACE);
				txt.addView(sig);
				line.addView(txt, Ui.weight(1));
				if (!r[4].isEmpty()) line.addView(Ui.text(ctx, "●", 12, Ui.SUCCESS));
				line.setOnClickListener(v -> {
					if (dlg[0] != null) dlg[0].dismiss();
					shell.panels.hide();
					BehaviorEvents.open(element, m.id, r[2]);
				});
				col.addView(line, Ui.matchWrap());
			}
			dlg[0] = new AlertDialog.Builder(ctx).setTitle(BehaviorText.name(m) + " · " + Tr.t("bhv_events", "Events")).setView(col)
					.setNegativeButton(Tr.t("close", "Close"), null).show();
		});
	}
}
