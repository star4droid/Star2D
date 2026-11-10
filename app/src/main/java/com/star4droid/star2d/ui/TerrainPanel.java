package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.star2d.editor.three.Editor3D;
import com.star4droid.star2d.editor.three.TerrainSession;
import com.star4droid.template.d3.FoliageMeshes;
import com.star4droid.template.d3.TerrainData;
import com.star4droid.template.d3.TerrainEdit;
import com.star4droid.template.d3.TerrainTextures;

import java.util.ArrayList;

/**
 * Terrain editor (native Android, over the 3D view). Opened from the properties of a TERRAIN element: the editor bars
 * hide, a top bar (done, undo / redo, save, generate, view / brush, more) and a tool dock appear (bottom in portrait,
 * right side in landscape). Brushes are applied by {@link TerrainSession} on the GL thread.
 */
public class TerrainPanel extends FrameLayout {
	public static TerrainPanel current;

	final EditorShell shell;
	final Activity a;
	// mirror of the session settings
	int tool = TerrainEdit.RAISE;
	float radius = 4, strength = 0.5f, falloff = 0.6f;
	int layer = 1;
	String kind = "builtin:tree_round";
	float density = 1.5f, spacing = 2.5f, minScale = 0.8f, maxScale = 1.25f;
	boolean eraseKindOnly, navigate;
	String name = "";
	final String[] layerTex = new String[4];
	final ArrayList<String> kinds = new ArrayList<String>();
	boolean canUndo, canRedo;
	private boolean builtLand;
	private ImageView undoBtn, redoBtn, navBtn;
	private LinearLayout options;
	private final ArrayList<View> toolButtons = new ArrayList<View>();
	private TextView title;

	static final int[] TOOL_ICON = {R.drawable.ic_t_raise, R.drawable.ic_t_lower, R.drawable.ic_t_smooth, R.drawable.ic_t_flatten, R.drawable.ic_t_noise,
			R.drawable.ic_t_plateau, R.drawable.ic_t_paint, R.drawable.ic_t_path, R.drawable.ic_t_place, R.drawable.ic_t_erase};
	static final String[] TOOL_KEY = {"ter_raise", "ter_lower", "ter_smooth", "ter_flatten", "ter_noise", "ter_plateau", "ter_paint", "ter_path", "ter_place", "ter_erase"};
	static final String[] TOOL_EN = {"Raise", "Lower", "Smooth", "Flatten", "Noise", "Plateau", "Paint", "Path", "Place", "Erase"};

	/** Info read on the GL thread when the editor opens. */
	static final class Info {
		String error, name;
		String[] tex = new String[4];
		ArrayList<String> kinds = new ArrayList<String>();
	}

	/** Opens the terrain editor for the selected TERRAIN element. */
	public static void open(EditorShell shell) {
		if (current != null) return;
		final Context c = shell.activity();
		Bridge.query(() -> {
			Info info = new Info();
			Editor3D e = Bridge.editor().get3D();
			if (e == null) {
				info.error = "3D view not ready";
				return info;
			}
			if (e.edit2D) e.setEdit2D(false);
			e.colliderEdit = false;
			Editor3DItem it = e.selected3D();
			info.error = e.terrain.start(it);
			if (info.error != null) return info;
			readInfo(e.terrain, info);
			return info;
		}, info -> {
			if (info == null) return;
			if (info.error != null) {
				Ui.toast(c, info.error);
				return;
			}
			TerrainPanel p = new TerrainPanel(shell, info);
			current = p;
			shell.enterOverlay(p);
		});
	}

	static void readInfo(TerrainSession s, Info info) {
		info.name = s.item.getName();
		for (int i = 0; i < 4; i++) info.tex[i] = s.item.getPropertySet().getString("Layer " + (i + 1) + " Texture");
		if (s.terrain() != null) for (TerrainData.Proto p : s.terrain().data.protos) if (!info.kinds.contains(p.src)) info.kinds.add(p.src);
	}

	TerrainPanel(EditorShell shell, Info info) {
		super(shell.activity());
		this.shell = shell;
		this.a = shell.activity();
		name = info.name;
		System.arraycopy(info.tex, 0, layerTex, 0, 4);
		for (String n : FoliageMeshes.NAMES) kinds.add("builtin:" + n);
		for (String k : info.kinds) if (!kinds.contains(k)) kinds.add(k);
		Bridge.gl(() -> {
			Editor3D e = Bridge.editor().get3D();
			if (e != null) e.terrain.listener = s -> {
				final boolean u = s.canUndo(), r = s.canRedo(), gone = s.item == null;
				Ui.ui(() -> {
					if (gone) {
						if (current == TerrainPanel.this) closeUi();
						return;
					}
					canUndo = u;
					canRedo = r;
					refreshTop();
				});
			};
		});
		build();
		push();
	}

	// ------------------------------------------------------------------ layout

	private int dp(float v) {
		return Ui.dp(a, v);
	}

	@Override
	protected void onSizeChanged(int w, int h, int ow, int oh) {
		super.onSizeChanged(w, h, ow, oh);
		boolean land = w > h;
		if (ow > 0 && land != builtLand) post(this::build);
	}

	private GradientDrawable glass(float r) {
		return Ui.rounded(0xE6202328, dp(r), 0x553A4048, dp(1));
	}

	private void build() {
		removeAllViews();
		toolButtons.clear();
		boolean land = Ui.isLandscape(a);
		builtLand = land;
		// ---- top bar
		LinearLayout top = Ui.row(a);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.setPadding(dp(6), dp(4), dp(6), dp(4));
		top.setBackground(glass(14));
		top.setClickable(true);
		LinearLayout done = Ui.button(a, R.drawable.ic_s2_check, Tr.t("ter_done", "Done"), true, v -> close());
		top.addView(done);
		title = Ui.text(a, name, 15, Ui.TEXT);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setMaxLines(1);
		title.setPadding(dp(10), 0, dp(6), 0);
		title.setEllipsize(android.text.TextUtils.TruncateAt.END);
		top.addView(title, Ui.weight(1));
		undoBtn = Ui.iconButton(a, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> Bridge.gl(() -> Bridge.editor().get3D().terrain.undo()));
		redoBtn = Ui.iconButton(a, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> Bridge.gl(() -> Bridge.editor().get3D().terrain.redo()));
		navBtn = Ui.iconButton(a, R.drawable.ic_s2_hand, Tr.t("ter_view", "Move the view (one finger)"), v -> {
			navigate = !navigate;
			push();
			refreshTop();
			Ui.toast(a, navigate ? Tr.t("ter_view_on", "One finger moves the view") : Tr.t("ter_view_off", "One finger paints"));
		});
		top.addView(navBtn);
		top.addView(undoBtn);
		top.addView(redoBtn);
		top.addView(Ui.iconButton(a, R.drawable.ic_t_dice, Tr.t("ter_generate", "Generate terrain"), v -> TerrainGenDialog.show(a, this)));
		top.addView(Ui.iconButton(a, R.drawable.ic_s2_save, Tr.t("save", "Save"), v -> Bridge.query(() -> Bridge.editor().get3D().terrain.save(),
				ok -> Ui.toast(a, Boolean.TRUE.equals(ok) ? Tr.t("ter_saved", "Terrain saved") : Tr.t("ter_save_fail", "Could not save the terrain")))));
		top.addView(Ui.iconButton(a, R.drawable.ic_s2_more, Tr.t("more", "More"), v -> more()));
		Ui.fitIconRow(a, top, 150);
		FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
		tlp.setMargins(dp(8), dp(6), dp(8), 0);
		if (land) tlp.rightMargin = dp(8 + 330);
		addView(top, tlp);
		// ---- dock
		LinearLayout dock = Ui.column(a);
		dock.setBackground(glass(16));
		dock.setPadding(dp(8), dp(6), dp(8), dp(8));
		dock.setClickable(true);
		dock.setOnTouchListener((v, ev) -> true); // touches on the dock never reach the 3D view
		// tools
		View tools;
		if (land) {
			LinearLayout grid = Ui.column(a);
			LinearLayout row = null;
			for (int i = 0; i < TOOL_ICON.length; i++) {
				if (i % 5 == 0) {
					row = Ui.row(a);
					grid.addView(row, Ui.matchWrap());
				}
				View b = toolButton(i);
				row.addView(b, Ui.weight(1));
			}
			tools = grid;
		} else {
			HorizontalScrollView hs = new HorizontalScrollView(a);
			hs.setHorizontalScrollBarEnabled(false);
			LinearLayout row = Ui.row(a);
			for (int i = 0; i < TOOL_ICON.length; i++) row.addView(toolButton(i), new LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT));
			hs.addView(row);
			tools = hs;
		}
		dock.addView(tools, Ui.matchWrap());
		View sep = new View(a);
		sep.setBackgroundColor(0x333A4048);
		dock.addView(sep, Ui.margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)), a, 0, 6, 0, 4));
		options = Ui.column(a);
		ScrollView sc = new ScrollView(a);
		sc.addView(options);
		sc.setVerticalScrollBarEnabled(false);
		if (land) {
			dock.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
			FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(330), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
			dlp.setMargins(0, dp(6), dp(6), dp(6));
			addView(dock, dlp);
		} else {
			// at most ~40% of the screen: the terrain stays visible above it
			int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.40f);
			dock.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
			dlp.setMargins(dp(6), 0, dp(6), dp(6));
			addView(dock, dlp);
			sc.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
			sc.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
				if (b - t > maxH) {
					ViewGroup.LayoutParams lp = v.getLayoutParams();
					lp.height = maxH - dp(90);
					v.setLayoutParams(lp);
				}
			});
		}
		buildOptions();
		refreshTop();
	}

	private View toolButton(int i) {
		LinearLayout b = Ui.column(a);
		b.setGravity(Gravity.CENTER);
		b.setPadding(dp(2), dp(6), dp(2), dp(5));
		b.setBackground(Ui.toggleBg(a));
		ImageView ic = Ui.smallIcon(a, TOOL_ICON[i], Ui.TEXT, 24);
		b.addView(ic);
		TextView t = Ui.text(a, Tr.t(TOOL_KEY[i], TOOL_EN[i]), 11, Ui.TEXT_2);
		t.setGravity(Gravity.CENTER);
		t.setMaxLines(1);
		b.addView(t);
		b.setSelected(i == tool);
		final int k = i;
		b.setOnClickListener(v -> {
			tool = k;
			for (int j = 0; j < toolButtons.size(); j++) toolButtons.get(j).setSelected(j == tool);
			if (navigate) {
				navigate = false;
				refreshTop();
			}
			push();
			buildOptions();
		});
		toolButtons.add(b);
		return b;
	}

	private void refreshTop() {
		if (undoBtn == null) return;
		undoBtn.setEnabled(canUndo);
		undoBtn.setAlpha(canUndo ? 1f : 0.35f);
		redoBtn.setEnabled(canRedo);
		redoBtn.setAlpha(canRedo ? 1f : 0.35f);
		navBtn.setSelected(navigate);
		navBtn.setImageTintList(ColorStateList.valueOf(navigate ? Ui.ACCENT : Ui.TEXT));
	}

	// ------------------------------------------------------------------ options

	private void buildOptions() {
		options.removeAllViews();
		boolean height = tool <= TerrainEdit.PLATEAU;
		if (tool == TerrainEdit.PAINT || tool == TerrainEdit.PATH) layers();
		if (tool == TerrainEdit.PLACE || tool == TerrainEdit.ERASE) kindsRow();
		options.addView(slider(Tr.t("ter_size", "Brush size"), 0.5f, 40f, radius, " m", v -> radius = v));
		if (tool != TerrainEdit.ERASE && tool != TerrainEdit.PLACE) {
			options.addView(slider(Tr.t("ter_strength", "Strength"), 0.02f, 1f, strength, "", v -> strength = v));
			options.addView(slider(Tr.t("ter_soft", "Softness"), 0f, 1f, falloff, "", v -> falloff = v));
		}
		if (tool == TerrainEdit.PLACE) {
			options.addView(slider(Tr.t("ter_density", "Density"), 0.1f, 20f, density, "", v -> density = v));
			options.addView(slider(Tr.t("ter_spacing", "Spacing"), 0.2f, 12f, spacing, " m", v -> spacing = v));
			options.addView(slider(Tr.t("ter_min_scale", "Smallest"), 0.2f, 3f, minScale, "x", v -> {
				minScale = v;
				if (maxScale < v) maxScale = v;
			}));
			options.addView(slider(Tr.t("ter_max_scale", "Biggest"), 0.2f, 4f, maxScale, "x", v -> maxScale = Math.max(v, minScale)));
		}
		if (tool == TerrainEdit.ERASE) {
			SwitchCompat sw = new SwitchCompat(a);
			sw.setText(Tr.t("ter_erase_kind", "Only the selected kind"));
			sw.setTextColor(Ui.TEXT);
			sw.setChecked(eraseKindOnly);
			sw.setOnCheckedChangeListener((x, on) -> {
				eraseKindOnly = on;
				push();
			});
			options.addView(sw, Ui.margins(Ui.matchWrap(), a, 4, 6, 4, 2));
		}
		if (height && (tool == TerrainEdit.FLATTEN || tool == TerrainEdit.PLATEAU))
			options.addView(hint(Tr.t("ter_flat_hint", "The height where your finger starts is kept.")));
		if (tool == TerrainEdit.PATH) options.addView(hint(Tr.t("ter_path_hint", "Draw a road: flattens and paints the selected layer.")));
	}

	private TextView hint(String s) {
		TextView t = Ui.text(a, s, 12, Ui.TEXT_3);
		t.setPadding(dp(4), dp(4), dp(4), dp(2));
		return t;
	}

	interface F {
		void set(float v);
	}

	/** Label, value and slider in one compact row. */
	private View slider(String label, float min, float max, float value, String unit, F f) {
		LinearLayout row = Ui.row(a);
		row.setGravity(Gravity.CENTER_VERTICAL);
		TextView l = Ui.text(a, label, 12, Ui.TEXT_2);
		l.setMaxLines(1);
		row.addView(l, new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT));
		SeekBar sb = new SeekBar(a);
		sb.setMax(1000);
		sb.setProgress(Math.round((value - min) / (max - min) * 1000));
		sb.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
		sb.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
		row.addView(sb, Ui.weight(1));
		TextView vt = Ui.text(a, fmt(value) + unit, 12, Ui.TEXT);
		vt.setGravity(Gravity.END);
		row.addView(vt, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));
		sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar s, int p, boolean user) {
				if (!user) return;
				// size: finer at the small end
				float t = p / 1000f;
				float v = min + (max - min) * (max / Math.max(0.01f, min) > 20 ? t * t : t);
				f.set(v);
				vt.setText(fmt(v) + unit);
				push();
			}

			@Override
			public void onStartTrackingTouch(SeekBar s) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar s) {
			}
		});
		if (max / Math.max(0.01f, min) > 20) sb.setProgress(Math.round((float) Math.sqrt(Math.max(0, (value - min) / (max - min))) * 1000));
		row.setPadding(dp(2), dp(2), dp(2), dp(2));
		return row;
	}

	static String fmt(float v) {
		if (Math.abs(v) >= 10) return String.valueOf(Math.round(v));
		return String.valueOf(Math.round(v * 100) / 100f);
	}

	private void layers() {
		LinearLayout row = Ui.row(a);
		row.setGravity(Gravity.CENTER_VERTICAL);
		for (int i = 0; i < 4; i++) {
			final int l = i + 1;
			LinearLayout sw = Ui.column(a);
			sw.setGravity(Gravity.CENTER);
			sw.setPadding(dp(3), dp(3), dp(3), dp(3));
			sw.setBackground(Ui.toggleBg(a));
			sw.setSelected(layer == l);
			View chip = new View(a);
			com.badlogic.gdx.graphics.Color avg = TerrainTextures.average(layerTex[i], i);
			chip.setBackground(Ui.rounded(Color.rgb((int) (avg.r * 255), (int) (avg.g * 255), (int) (avg.b * 255)), dp(8), layer == l ? Ui.ACCENT : 0x553A4048, dp(2)));
			sw.addView(chip, new LinearLayout.LayoutParams(dp(42), dp(30)));
			TextView t = Ui.text(a, texLabel(layerTex[i], i), 10, Ui.TEXT_2);
			t.setMaxLines(1);
			sw.addView(t);
			sw.setOnClickListener(v -> {
				if (layer == l) chooseTexture(l);
				layer = l;
				push();
				buildOptions();
			});
			sw.setOnLongClickListener(v -> {
				chooseTexture(l);
				return true;
			});
			row.addView(sw, Ui.weight(1));
		}
		options.addView(row, Ui.matchWrap());
		options.addView(hint(Tr.t("ter_layer_hint", "Tap a selected layer to change its texture.")));
	}

	static String texLabel(String v, int i) {
		if (v == null || v.length() == 0) v = TerrainTextures.DEFAULT_LAYERS[i];
		if (v.startsWith("builtin:")) return Tr.t("tex_" + v.substring(8), v.substring(8));
		String n = v.replace('\\', '/');
		return n.substring(n.lastIndexOf('/') + 1);
	}

	private void chooseTexture(int l) {
		ArrayList<String> items = new ArrayList<String>();
		for (String n : TerrainTextures.NAMES) items.add(Tr.t("tex_" + n, n));
		items.add(Tr.t("ter_project_image", "Project image…"));
		Dialogs.choose(a, Tr.t("ter_layer_tex", "Layer texture") + " " + l, items.toArray(new String[0]), idx -> {
			if (idx < TerrainTextures.NAMES.length) setLayerTexture(l, "builtin:" + TerrainTextures.NAMES[idx]);
			else Bridge.query(() -> Bridge.editor().getProject().getImagesPath(), imgs ->
					FilePickerDialog.show(a, Tr.t("ter_layer_tex", "Layer texture"), imgs, new String[]{"jpg", "png", "jpeg"}, true, (file, rel) -> setLayerTexture(l, rel)));
		});
	}

	private void setLayerTexture(int l, String v) {
		layerTex[l - 1] = v;
		Bridge.gl(() -> {
			TerrainSession s = Bridge.editor().get3D().terrain;
			if (s.item != null) s.setProps(new String[]{"Layer " + l + " Texture", v}, "Layer texture");
		});
		buildOptions();
	}

	private void kindsRow() {
		HorizontalScrollView hs = new HorizontalScrollView(a);
		hs.setHorizontalScrollBarEnabled(false);
		LinearLayout row = Ui.row(a);
		for (String k : kinds) {
			TextView chip = Ui.text(a, kindLabel(k), 12, kind.equals(k) ? Ui.TEXT : Ui.TEXT_2);
			chip.setPadding(dp(10), dp(6), dp(10), dp(6));
			chip.setBackground(kind.equals(k) ? Ui.rounded(Ui.ACCENT_SOFT, dp(14), Ui.ACCENT, dp(1)) : Ui.rounded(Ui.SURFACE_2, dp(14)));
			chip.setOnClickListener(v -> {
				kind = k;
				push();
				buildOptions();
			});
			row.addView(chip, Ui.margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), a, 0, 2, 6, 2));
		}
		TextView model = Ui.text(a, "+ " + Tr.t("ter_model", "Model…"), 12, Ui.ACCENT);
		model.setPadding(dp(10), dp(6), dp(10), dp(6));
		model.setBackground(Ui.rounded(Ui.SURFACE_2, dp(14)));
		model.setOnClickListener(v -> Bridge.query(() -> Bridge.editor().getProject().getPath() + "/models", root ->
				FilePickerDialog.show(a, Tr.t("ter_model", "Model…"), root, new String[]{"glb", "gltf", "obj"}, false, (file, rel) -> {
					String p = rel.startsWith("/") ? rel : "/" + rel;
					if (!kinds.contains(p)) kinds.add(p);
					kind = p;
					push();
					buildOptions();
				})));
		row.addView(model);
		hs.addView(row);
		options.addView(hs, Ui.matchWrap());
	}

	static String kindLabel(String k) {
		if (k.startsWith("builtin:")) return Tr.t("fol_" + k.substring(8), k.substring(8).replace('_', ' '));
		return k.substring(k.lastIndexOf('/') + 1);
	}

	// ------------------------------------------------------------------ actions

	/** Sends the settings to the session. */
	void push() {
		final int t = tool, l = layer;
		final float r = radius, st = strength, fo = falloff, de = density, sp = spacing, mn = minScale, mx = maxScale;
		final String k = kind;
		final boolean ek = eraseKindOnly, nav = navigate;
		Bridge.gl(() -> {
			Editor3D e = Bridge.editor().get3D();
			if (e == null) return;
			TerrainSession s = e.terrain;
			s.tool = t;
			s.layer = l;
			s.radius = r;
			s.strength = st;
			s.falloff = fo;
			s.kind = k;
			s.density = de;
			s.spacing = sp;
			s.minScale = mn;
			s.maxScale = mx;
			s.eraseKindOnly = ek;
			s.navigate = nav;
		});
	}

	private void more() {
		String[] items = {Tr.t("ter_resolution", "Grid resolution…"), Tr.t("ter_fill", "Paint everything with the selected layer"),
				Tr.t("ter_flat_all", "Flatten everything"), Tr.t("ter_clear_kind", "Remove all objects of the selected kind"),
				Tr.t("ter_clear_all", "Remove all objects"), Tr.t("ter_props", "Terrain properties (grass, layers…)")};
		Dialogs.choose(a, Tr.t("more", "More"), items, i -> {
			if (i == 0) {
				String[] res = {"64", "96", "128", "192", "256", "384", "512"};
				Dialogs.choose(a, Tr.t("ter_resolution", "Grid resolution…"), res, k -> {
					int v = Integer.parseInt(res[k]);
					Bridge.gl(() -> Bridge.editor().get3D().terrain.setResolution(v));
				});
			} else if (i == 1) {
				final int l = layer;
				Bridge.gl(() -> Bridge.editor().get3D().terrain.fillLayer(l));
			} else if (i == 2) {
				Dialogs.confirm(a, Tr.t("ter_flat_all", "Flatten everything"), Tr.t("ter_flat_all_msg", "Make the whole terrain flat? (Undo is available.)"),
						Tr.t("ok", "OK"), () -> Bridge.gl(() -> Bridge.editor().get3D().terrain.flattenAll(0.2f)));
			} else if (i == 3) {
				Bridge.gl(() -> Bridge.editor().get3D().terrain.clearObjects(true));
			} else if (i == 4) {
				Dialogs.confirm(a, Tr.t("ter_clear_all", "Remove all objects"), Tr.t("ter_clear_all_msg", "Remove every tree, rock and object of this terrain?"),
						Tr.t("ok", "OK"), () -> Bridge.gl(() -> Bridge.editor().get3D().terrain.clearObjects(false)));
			} else if (i == 5) {
				close();
				shell.panels.show(shell.properties);
			}
		});
	}

	/** Saves, leaves the terrain editor and brings the editor bars back. */
	public void close() {
		Bridge.query(() -> {
			Editor3D e = Bridge.editor().get3D();
			if (e == null) return Boolean.TRUE;
			boolean ok = e.terrain.item == null || e.terrain.save();
			e.terrain.listener = null;
			e.terrain.stop();
			return ok;
		}, ok -> {
			if (!Boolean.TRUE.equals(ok)) Ui.toast(a, Tr.t("ter_save_fail", "Could not save the terrain"));
			closeUi();
		});
	}

	private void closeUi() {
		if (current == this) current = null;
		shell.exitOverlay(this);
	}

	/** Called by the generator dialog when a new terrain was made (layer names may have changed). */
	void reloadInfo() {
		Bridge.query(() -> {
			Info info = new Info();
			TerrainSession s = Bridge.editor().get3D().terrain;
			if (s.item == null) return null;
			readInfo(s, info);
			return info;
		}, info -> {
			if (info == null) return;
			System.arraycopy(info.tex, 0, layerTex, 0, 4);
			for (String k : info.kinds) if (!kinds.contains(k)) kinds.add(k);
			buildOptions();
		});
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return false; // empty parts: the 3D view gets the touch
	}
}
