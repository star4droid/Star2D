package com.star4droid.star2d.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.three.Editor3D;
import com.star4droid.star2d.editor.utils.EditorAction;

import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Settings of a 3D scene (configs/scene.json): lighting mode and preset, sky, sun, ambient, fog, shadows, physics
 * gravity, default graphics quality and screen effect; plus the editor's 3D viewport options.
 */
public final class Scene3DSettings {
	private Scene3DSettings() {
	}

	/** {key, kind, default, options} kind: choice, color, float, bool, text (also read by the AI tools: scenes configure). */
	public static final String[][] FIELDS = {
			{"Render Mode", "choice", "SIMPLE", "SIMPLE,SOFT,TOON,PBR,UNLIT"},
			{"Lighting Preset", "choice", "DAY", "DAY,SUNSET,NIGHT,OVERCAST,STUDIO,HORROR,INDOOR,CARTOON,CUSTOM"},
			{"Toon Bands", "float", "3", ""},
			{"Sky", "choice", "GRADIENT", "GRADIENT,COLOR,IMAGE"},
			{"Sky Top", "color", "#5C9EF2", ""},
			{"Sky Bottom", "color", "#C7E0FA", ""},
			{"Sky Image", "text", "", ""},
			{"Ambient Color", "color", "#737882", ""},
			{"Ambient Intensity", "float", "1", ""},
			{"Sun", "bool", "true", ""},
			{"Sun Color", "color", "#FFF5E0", ""},
			{"Sun Intensity", "float", "1", ""},
			{"Sun Pitch", "float", "-55", ""},
			{"Sun Yaw", "float", "35", ""},
			{"Shadows", "bool", "true", ""},
			{"Shadow Distance", "float", "40", ""},
			{"Fog", "bool", "false", ""},
			{"Fog Color", "color", "#C7DBF2", ""},
			{"Fog Near", "float", "40", ""},
			{"Fog Far", "float", "160", ""},
			{"Gravity X", "float", "0", ""},
			{"Gravity Y", "float", "-9.81", ""},
			{"Gravity Z", "float", "0", ""},
			{"Quality", "choice", "MEDIUM", "LOW,MEDIUM,HIGH,ULTRA"},
			{"Post Effect", "choice", "NONE", "NONE,VIGNETTE,NIGHT_VISION,GRAYSCALE,SEPIA,HORROR,CHROMATIC,PIXELATE,BLOOM_LITE,UNDERWATER,DREAM"},
	};

	/** Values a new 3D scene starts with. */
	public static void defaults(PropertySet<String, Object> cfg) {
		cfg.put("sceneType", "3D");
		for (String[] f : FIELDS) if (!cfg.containsKey(f[0])) cfg.put(f[0], f[2]);
	}

	public static void show(Activity a) {
		Bridge.query(() -> {
			LibgdxEditor ed = Bridge.editor();
			PropertySet<String, Object> cfg = ed.getConfig();
			LinkedHashMap<String, String> v = Bridge.snapshot(cfg);
			v.put("__old", cfg.toString());
			Editor3D e = ed.get3D();
			if (e != null) {
				v.put("__snap", String.valueOf(e.snap));
				v.put("__local", String.valueOf(e.localAxes));
				v.put("__grid", String.valueOf(e.showGrid));
				v.put("__2d", String.valueOf(e.edit2D));
			}
			return v;
		}, v -> build(a, v));
	}

	private static String label(String key) {
		return Tr.t("s3cfg_" + key.toLowerCase().replace(' ', '_'), key);
	}

	private static void build(Activity a, LinkedHashMap<String, String> v) {
		LinearLayout col = Ui.column(a);
		col.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 8));
		col.addView(Ui.text(a, Tr.t("s3cfg_hint", "Look and physics of this 3D scene. CUSTOM lighting uses the colors below; the other presets set them for you."), 12.5f, Ui.TEXT_3));
		final HashMap<String, String> out = new HashMap<>();
		final HashMap<String, View> editors = new HashMap<>();
		for (String[] f : FIELDS) {
			final String key = f[0];
			String cur = v.containsKey(key) && v.get(key) != null && !v.get(key).isEmpty() ? v.get(key) : f[2];
			out.put(key, cur);
			if ("bool".equals(f[1])) {
				SwitchCompat sw = new SwitchCompat(a);
				sw.setText(label(key));
				sw.setTextColor(Ui.TEXT);
				sw.setChecked("true".equals(cur));
				sw.setMinHeight(Ui.dp(a, 44));
				sw.setOnCheckedChangeListener((b, on) -> out.put(key, String.valueOf(on)));
				col.addView(sw, Ui.matchWrap());
				continue;
			}
			col.addView(Ui.sectionLabel(a, label(key)));
			if ("choice".equals(f[1])) {
				final String[] opts = f[3].split(",");
				TextView t = Ui.text(a, Tr.t(cur, cur), 14, Ui.TEXT);
				t.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
				t.setBackground(Ui.ripple(0xFF121417, Ui.dp(a, 7)));
				t.setOnClickListener(x -> {
					String[] shown = new String[opts.length];
					for (int i = 0; i < opts.length; i++) shown[i] = Tr.t(opts[i], opts[i]);
					Dialogs.choose(a, label(key), shown, i -> {
						out.put(key, opts[i]);
						t.setText(shown[i]);
					});
				});
				col.addView(t, Ui.matchWrap());
			} else if ("color".equals(f[1])) {
				LinearLayout row = Ui.row(a);
				View sw = new View(a);
				sw.setBackground(Ui.rounded(ColorPickerDialog.parse(cur), Ui.dp(a, 6), Ui.BORDER, 1));
				row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 30)));
				TextView hex = Ui.text(a, cur, 14, Ui.TEXT);
				hex.setTypeface(Typeface.MONOSPACE);
				hex.setPadding(Ui.dp(a, 10), 0, 0, 0);
				row.addView(hex, Ui.weight(1));
				row.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
				row.setBackground(Ui.ripple(0xFF121417, Ui.dp(a, 7)));
				row.setOnClickListener(x -> ColorPickerDialog.show(a, label(key), out.get(key), null, c -> {
					if (c == null) return;
					out.put(key, c);
					hex.setText(c);
					sw.setBackground(Ui.rounded(ColorPickerDialog.parse(c), Ui.dp(a, 6), Ui.BORDER, 1));
				}));
				col.addView(row, Ui.matchWrap());
			} else {
				EditText e = Ui.input(a, cur, f[2]);
				if ("float".equals(f[1])) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
				editors.put(key, e);
				col.addView(e, Ui.matchWrap());
			}
		}
		// editor viewport
		col.addView(Ui.sectionLabel(a, Tr.t("s3cfg_editor", "Editor 3D view")));
		final SwitchCompat snap = sw(a, Tr.t("s3cfg_snap", "Snap (0.25 m, 15°)"), "true".equals(v.get("__snap")));
		final SwitchCompat local = sw(a, Tr.t("s3cfg_local", "Gizmo on the element's own axes"), "true".equals(v.get("__local")));
		final SwitchCompat grid = sw(a, Tr.t("s3cfg_grid", "Show the grid"), !"false".equals(v.get("__grid")));
		final SwitchCompat two = sw(a, Tr.t("s3cfg_2d", "Edit the 2D layer (HUD, buttons) instead of the 3D world"), "true".equals(v.get("__2d")));
		col.addView(snap, Ui.matchWrap());
		col.addView(local, Ui.matchWrap());
		col.addView(grid, Ui.matchWrap());
		col.addView(two, Ui.matchWrap());
		final String[] eq = {Editor3D.editorQuality};
		TextView q = Ui.text(a, Tr.t("s3cfg_editor_quality", "Editor quality") + ": " + Tr.t(eq[0], eq[0]), 14, Ui.TEXT);
		q.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
		q.setBackground(Ui.ripple(0xFF121417, Ui.dp(a, 7)));
		final String[] levels = {"LOW", "MEDIUM", "HIGH"};
		q.setOnClickListener(x -> Dialogs.choose(a, Tr.t("s3cfg_editor_quality", "Editor quality"), new String[]{Tr.t("LOW", "LOW"), Tr.t("MEDIUM", "MEDIUM"), Tr.t("HIGH", "HIGH")}, i -> {
			eq[0] = levels[i];
			q.setText(Tr.t("s3cfg_editor_quality", "Editor quality") + ": " + Tr.t(eq[0], eq[0]));
		}));
		col.addView(q, Ui.matchWrap());

		ScrollView s = new ScrollView(a);
		s.addView(col);
		new AlertDialog.Builder(a)
				.setTitle(Tr.t("s3cfg_title", "3D scene settings"))
				.setView(s)
				.setPositiveButton(Tr.t("save", "Save"), (d, w) -> {
					for (java.util.Map.Entry<String, View> e : editors.entrySet())
						out.put(e.getKey(), ((EditText) e.getValue()).getText().toString().trim());
					final String old = v.get("__old");
					final boolean bSnap = snap.isChecked(), bLocal = local.isChecked(), bGrid = grid.isChecked(), b2d = two.isChecked();
					Bridge.gl(() -> {
						LibgdxEditor ed = Bridge.editor();
						PropertySet<String, Object> cfg = ed.getConfig();
						cfg.put("sceneType", "3D");
						for (java.util.Map.Entry<String, String> e : out.entrySet()) cfg.put(e.getKey(), e.getValue());
						ed.saveConfig();
						Editor3D e3 = ed.get3D();
						if (e3 != null) {
							e3.configure(cfg);
							e3.snap = bSnap;
							e3.localAxes = bLocal;
							e3.showGrid = bGrid;
							e3.edit2D = b2d;
						}
						Editor3D.editorQuality = eq[0];
						try {
							EditorAction.sceneConfigChanged(ed, old, cfg.toString());
						} catch (Throwable ignored) {
						}
					});
					Snack.show(Tr.t("saved", "Saved"));
				})
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.show();
	}

	private static SwitchCompat sw(Activity a, String text, boolean on) {
		SwitchCompat s = new SwitchCompat(a);
		s.setText(text);
		s.setTextColor(Ui.TEXT);
		s.setChecked(on);
		s.setMinHeight(Ui.dp(a, 44));
		return s;
	}

	/** Quick menu of the 3D view (rail button). */
	public static void viewMenu(Activity a, View anchor) {
		String[] items = {Tr.t("s3v_persp", "Perspective view"), Tr.t("s3v_top", "Top view"), Tr.t("s3v_front", "Front view"), Tr.t("s3v_side", "Side view"),
				Tr.t("s3v_focus", "Focus the selected element"), Tr.t("s3v_layer", "Switch 3D world / 2D layer editing"), Tr.t("s3v_snap", "Snap on / off"),
				Tr.t("s3v_anim", "Preview animations on / off"), Tr.t("s3cfg_title", "3D scene settings")};
		Dialogs.choose(a, Tr.t("s3v_title", "3D view"), items, i -> {
			if (i == 8) {
				show(a);
				return;
			}
			Bridge.query(() -> {
				Editor3D e = Bridge.editor().get3D();
				if (e == null) return Tr.t("s3v_only3d", "Only in 3D scenes");
				switch (i) {
					case 0: e.viewPerspective(); break;
					case 1: e.viewTop(); break;
					case 2: e.viewFront(); break;
					case 3: e.viewSide(); break;
					case 4: e.focusSelected(); break;
					case 5: e.edit2D = !e.edit2D; return e.edit2D ? Tr.t("s3v_layer2d", "Editing the 2D layer") : Tr.t("s3v_layer3d", "Editing the 3D world");
					case 6: e.snap = !e.snap; return e.snap ? Tr.t("s3v_snap_on", "Snap on") : Tr.t("s3v_snap_off", "Snap off");
					case 7: e.world.editorAnimate = !e.world.editorAnimate; return e.world.editorAnimate ? Tr.t("s3v_anim_on", "Animations playing") : Tr.t("s3v_anim_off", "Animations stopped");
				}
				return null;
			}, msg -> {
				if (msg != null) Ui.toast(a, msg);
			});
		});
	}
}
