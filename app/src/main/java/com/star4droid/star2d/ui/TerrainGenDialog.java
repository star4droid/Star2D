package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.star2d.editor.three.TerrainSession;
import com.star4droid.template.d3.TerrainGen;

/**
 * Random terrain: size, height, shape, biome, roughness, water, road, flat start area, how many trees / bushes /
 * rocks / flowers, grass, seed. Replaces the terrain being edited (one undo step) and can add a water surface.
 */
public final class TerrainGenDialog {
	private TerrainGenDialog() {
	}

	private static TerrainGen.Settings last;

	public static void show(Activity a, TerrainPanel panel) {
		final TerrainGen.Settings s = last != null ? last : new TerrainGen.Settings();
		LinearLayout box = Ui.column(a);
		box.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 4));
		EditText w = num(a, box, Tr.t("tg_width", "Width (m)"), s.width);
		EditText l = num(a, box, Tr.t("tg_length", "Length (m)"), s.length);
		EditText h = num(a, box, Tr.t("tg_height", "Highest point (m)"), s.height);
		Spinner shape = spinner(a, box, Tr.t("tg_shape", "Shape"), TerrainGen.SHAPES, "tgs_", s.shape);
		Spinner biome = spinner(a, box, Tr.t("tg_biome", "Biome"), TerrainGen.BIOMES, "tgb_", s.biome);
		String[] resv = {"64", "96", "128", "192", "256"};
		Spinner res = spinner(a, box, Tr.t("tg_res", "Detail (grid)"), resv, null, String.valueOf(s.resolution));
		SeekBar rough = seek(a, box, Tr.t("tg_rough", "Roughness"), s.roughness);
		SwitchCompat water = sw(a, box, Tr.t("tg_water", "Water (lake / sea)"), s.waterLevel > 0);
		SeekBar wl = seek(a, box, Tr.t("tg_water_level", "Water level"), s.waterLevel > 0 ? s.waterLevel / 0.6f : 0.25f);
		SwitchCompat road = sw(a, box, Tr.t("tg_road", "Winding road"), s.road);
		SwitchCompat flat = sw(a, box, Tr.t("tg_flat", "Flat start area in the middle"), s.flatCenter > 0);
		EditText trees = num(a, box, Tr.t("tg_trees", "Trees"), s.trees);
		EditText bushes = num(a, box, Tr.t("tg_bushes", "Bushes"), s.bushes);
		EditText rocks = num(a, box, Tr.t("tg_rocks", "Rocks"), s.rocks);
		EditText flowers = num(a, box, Tr.t("tg_flowers", "Flowers"), s.flowers);
		SeekBar grass = seek(a, box, Tr.t("tg_grass", "Grass"), s.grass / 5f);
		EditText seed = num(a, box, Tr.t("tg_seed", "Seed (same seed = same terrain)"), s.seed);
		LinearLayout dice = Ui.button(a, com.star4droid.star2d.evo.R.drawable.ic_t_dice, Tr.t("tg_random", "Random seed"), false,
				v -> seed.setText(String.valueOf(1 + new java.util.Random().nextInt(99999))));
		box.addView(dice, Ui.margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), a, 0, 4, 0, 8));
		ScrollView sv = new ScrollView(a);
		sv.addView(box);
		Dialogs.custom(a, Tr.t("ter_generate", "Generate terrain"), sv, Tr.t("tg_go", "Generate"), () -> {
			s.width = clamp(f(w, s.width), 10, 2000);
			s.length = clamp(f(l, s.length), 10, 2000);
			s.height = clamp(f(h, s.height), 0.5f, 400);
			s.shape = TerrainGen.SHAPES[shape.getSelectedItemPosition()];
			s.biome = TerrainGen.BIOMES[biome.getSelectedItemPosition()];
			s.resolution = Integer.parseInt(resv[res.getSelectedItemPosition()]);
			s.roughness = rough.getProgress() / 100f;
			s.waterLevel = water.isChecked() ? Math.max(0.03f, wl.getProgress() / 100f * 0.6f) : 0;
			s.road = road.isChecked();
			s.flatCenter = flat.isChecked() ? 8 : 0;
			s.trees = (int) clamp(f(trees, s.trees), 0, 20000);
			s.bushes = (int) clamp(f(bushes, s.bushes), 0, 20000);
			s.rocks = (int) clamp(f(rocks, s.rocks), 0, 20000);
			s.flowers = (int) clamp(f(flowers, s.flowers), 0, 40000);
			s.grass = grass.getProgress() / 100f * 5f;
			s.seed = (int) f(seed, s.seed);
			last = s;
			Ui.toast(a, Tr.t("tg_working", "Generating…"));
			final boolean wantWater = s.waterLevel > 0 || "ISLAND".equals(s.shape);
			Bridge.query(() -> {
				TerrainSession ts = Bridge.editor().get3D().terrain;
				if (ts.item == null) return "no terrain";
				ts.generate(s);
				if (wantWater && !Float.isNaN(ts.lastWaterY)) addWater(ts);
				return null;
			}, err -> {
				if (err != null) Ui.toast(a, err);
				else if (panel != null) panel.reloadInfo();
			});
		});
	}

	/** A water surface over the terrain (an existing WATER element at the terrain's x / z is reused). */
	static void addWater(TerrainSession ts) {
		Editor3DItem terrain = ts.item;
		PropertySet<String, Object> tp = terrain.getPropertySet();
		Editor3DItem w = null;
		for (com.badlogic.gdx.scenes.scene2d.Actor act : Bridge.editor().getActors()) {
			if (!(act instanceof Editor3DItem) || act == terrain) continue;
			PropertySet<String, Object> p = ((Editor3DItem) act).getPropertySet();
			if (p != null && "WATER".equalsIgnoreCase(p.getString("Mesh")) && Math.abs(p.getFloat("x") - tp.getFloat("x")) < 0.01f
					&& Math.abs(p.getFloat("z") - tp.getFloat("z")) < 0.01f) w = (Editor3DItem) act;
		}
		if (w == null) {
			Bridge.controls().actionAdd3D("OBJ3D", "WATER");
			com.badlogic.gdx.scenes.scene2d.Actor sel = Bridge.editor().getSelectedActor();
			if (!(sel instanceof Editor3DItem) || sel == terrain) return;
			w = (Editor3DItem) sel;
		}
		PropertySet<String, Object> ps = w.getPropertySet();
		ps.put("x", tp.getString("x"));
		ps.put("z", tp.getString("z"));
		float y = tp.getFloat("y") + ts.lastWaterY;
		ps.put("y", String.valueOf(Math.round(y * 100) / 100f));
		ps.put("Scale X", tp.getString("Scale X"));
		ps.put("Scale Z", tp.getString("Scale Z"));
		ps.put("Cast Shadows", "false");
		w.setProperties(ps);
		Bridge.editor().getSaveState();
		Bridge.editor().selectActor(terrain);
	}

	static float clamp(float v, float a, float b) {
		return Math.max(a, Math.min(b, v));
	}

	static float f(EditText e, float def) {
		try {
			return Float.parseFloat(e.getText().toString().trim());
		} catch (Exception x) {
			return def;
		}
	}

	private static TextView label(Activity a, LinearLayout box, String s) {
		TextView t = Ui.text(a, s, 13, Ui.TEXT_2);
		t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 2));
		box.addView(t);
		return t;
	}

	static EditText num(Activity a, LinearLayout box, String lab, float v) {
		label(a, box, lab);
		EditText e = Ui.input(a, TerrainPanel.fmt(v).replace(".0", ""), "");
		e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		box.addView(e, Ui.matchWrap());
		return e;
	}

	static Spinner spinner(Activity a, LinearLayout box, String lab, String[] values, String trPrefix, String sel) {
		label(a, box, lab);
		String[] shown = new String[values.length];
		int idx = 0;
		for (int i = 0; i < values.length; i++) {
			shown[i] = trPrefix == null ? values[i] : Tr.t(trPrefix + values[i].toLowerCase(), values[i].charAt(0) + values[i].substring(1).toLowerCase());
			if (values[i].equals(sel)) idx = i;
		}
		Spinner sp = new Spinner(a);
		ArrayAdapter<String> ad = new ArrayAdapter<String>(a, android.R.layout.simple_spinner_dropdown_item, shown);
		sp.setAdapter(ad);
		sp.setSelection(idx);
		box.addView(sp, Ui.matchWrap());
		return sp;
	}

	static SeekBar seek(Activity a, LinearLayout box, String lab, float v01) {
		label(a, box, lab);
		SeekBar sb = new SeekBar(a);
		sb.setMax(100);
		sb.setProgress(Math.round(clamp(v01, 0, 1) * 100));
		sb.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
		sb.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
		box.addView(sb, Ui.matchWrap());
		return sb;
	}

	static SwitchCompat sw(Activity a, LinearLayout box, String lab, boolean on) {
		SwitchCompat s = new SwitchCompat(a);
		s.setText(lab);
		s.setTextColor(Ui.TEXT);
		s.setChecked(on);
		s.setGravity(Gravity.CENTER_VERTICAL);
		box.addView(s, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 0));
		return s;
	}
}
