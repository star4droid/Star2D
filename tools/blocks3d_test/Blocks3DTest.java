import com.star4droid.star2d.blocks.SceneDims;
import com.star4droid.star2d.ui.blocks.Blocks3D;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks of the 3D blocks (tools/test_blocks3d.py): texts of every block, slot counts, Java 7 only, the 2D/3D filter
 * (SceneDims), and writes the Java of every block filled with its defaults (args[1]) and the translation keys (args[2]).
 */
public class Blocks3DTest {
	static int fails = 0;

	static void check(boolean ok, String msg) {
		if (!ok) {
			fails++;
			System.out.println("FAIL: " + msg);
		}
	}

	static String key(String s) {
		return s.replaceAll("[^A-Za-z0-9]+", "_");
	}

	static int count(String s, String sub) {
		int c = 0, i = 0;
		while ((i = s.indexOf(sub, i)) >= 0) {
			c++;
			i += sub.length();
		}
		return c;
	}

	static final Pattern PLACE = Pattern.compile("%(\\d+)\\$s");

	static String fill(String tpl, List<String> vals) {
		Object[] a = new Object[30];
		for (int i = 0; i < a.length; i++) a[i] = i < vals.size() ? vals.get(i) : "";
		return String.format(tpl, a);
	}

	static String esc(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public static void main(String[] args) throws Exception {
		final StringBuilder code = new StringBuilder();
		final LinkedHashMap<String, String[]> keys = new LinkedHashMap<String, String[]>(); // key -> {en, ar}
		final HashSet<String> titles = new HashSet<String>(), displays = new HashSet<String>(), cats = new HashSet<String>();
		final HashSet<String> fieldNames = new HashSet<String>();
		final int[] n = new int[3];
		final LinkedHashMap<String, int[]> perCat = new LinkedHashMap<String, int[]>();
		Blocks3D.register(new Blocks3D.Sink() {
			public void category(String name, int color, String en, String ar) {
				check(cats.add(name), "category twice: " + name);
				check((color & 0xFF000000) == 0xFF000000, "color alpha " + name);
				keys.put("blk_c_" + key(name), new String[] {en, ar});
				keys.put("blk_group_" + name.toLowerCase().replace(" / ", "_").replace(' ', '_'), new String[] {en, ar});
				perCat.put(name, new int[3]);
			}

			public void def(String cat, int color, String title, String fields, String defaults, boolean isIf, String tpl, int dims,
					String en, String ar, String ie, String ia) {
				n[0]++;
				perCat.get(cat)[isIf ? 1 : 0]++;
				check(titles.add(title), "title twice: " + title);
				check(!en.isEmpty() && !ar.isEmpty() && !ie.isEmpty() && !ia.isEmpty(), "texts missing: " + title);
				check(!tpl.contains("->") && !tpl.contains("::"), "lambda / method ref (not Java 7): " + title);
				check(!tpl.contains("net.mgsx") && !tpl.contains("physics.bullet"), "Bullet / glTF class used: " + title);
				String[] fs = fields.isEmpty() ? new String[0] : fields.split(" ");
				String[] dv = defaults == null || defaults.isEmpty() ? new String[0] : defaults.split("\\|", -1);
				check(fs.length == dv.length, "fields / defaults count differ: " + title);
				int max = 0;
				Matcher m = PLACE.matcher(tpl);
				while (m.find()) max = Math.max(max, Integer.parseInt(m.group(1)));
				check(max == fs.length + (isIf ? 3 : 1), "placeholders " + max + " vs fields " + fs.length + ": " + title);
				for (String f : fs) fieldNames.add(f.indexOf('(') > 0 ? f.substring(0, f.indexOf('(')) : f);
				List<String> vals = new ArrayList<String>();
				for (int i = 0; i < fs.length; i++) {
					String v = i < dv.length ? dv[i] : "";
					check(!v.isEmpty() || fs[i].contains("(image)") || fs[i].contains("(sound)"), "empty default: " + title + " " + fs[i]);
					vals.add(v);
				}
				for (int i = 0; i < 3; i++) vals.add("");
				code.append("    // ").append(title).append("\n    {\n").append(fill(tpl, vals)).append("\n    }\n");
				keys.put("blk_l_" + key(title), new String[] {en, ar});
				keys.put("blk_i_" + key(title), new String[] {ie, ia});
			}

			public void reporter(String group, String display, String tpl, char shape, String slots, int dims, String ar, String ie, String ia) {
				n[1]++;
				perCat.get(group)[2]++;
				check(displays.add(display), "value block twice: " + display);
				int k = count(display, "___");
				check(k == slots.length(), "slots " + slots + " vs ___ in " + display);
				check(k == count(ar, "___"), "Arabic ___ count: " + display);
				check("bnt".indexOf(shape) >= 0, "shape " + display);
				check(!tpl.contains("->") && !tpl.contains("::"), "lambda: " + display);
				List<String> vals = new ArrayList<String>();
				for (char c : slots.toCharArray()) vals.add(c == 'e' ? "current" : c == 't' ? "\"coin\"" : c == 'b' ? "true" : "1");
				String type = shape == 'b' ? "boolean" : shape == 't' ? "String" : "Object";
				code.append("    { ").append(type).append(" v = ").append(fill(tpl, vals)).append("; } // ").append(display).append("\n");
				if (shape == 'n' && !display.contains("element") && !display.contains("camera\"") && !display.startsWith("3D copy") && !display.equals("3D main camera"))
					code.append("    { float f = ").append(fill(tpl, vals)).append("; }\n");
				keys.put("blk_r_" + key(display), new String[] {display, ar});
				keys.put("blk_i_" + key(display), new String[] {ie, ia});
			}

			public void field(String name, String en, String ar) {
				keys.put("blk_f_" + key(name), new String[] {en, ar});
			}

			public void choices(String type, String[] values) {
				check(values.length > 1, "choices " + type);
			}
		});
		// every new field name has a label (built-in ones are labeled in BlockInfo)
		String[] known = {"body", "target", "x", "y", "dx", "dy", "seconds", "speed", "degrees", "color", "pic", "alpha", "on", "visible", "type",
				"var", "newName", "strength", "code", "value"};
		HashSet<String> ok = new HashSet<String>(java.util.Arrays.asList(known));
		for (String f : fieldNames) check(ok.contains(f) || keys.containsKey("blk_f_" + key(f)), "field without label: " + f);

		// ---- 2D / 3D filter
		check(SceneDims.parse((String) null) == SceneDims.BOTH, "parse null");
		check(SceneDims.parse("2d") == SceneDims.D2 && SceneDims.parse("3D") == SceneDims.D3 && SceneDims.parse("Both") == SceneDims.BOTH, "parse");
		check(SceneDims.visible(SceneDims.D3, 0) && SceneDims.visible(SceneDims.D2, 0), "no filter shows all");
		check(!SceneDims.visible(SceneDims.D3, SceneDims.D2) && SceneDims.visible(SceneDims.BOTH, SceneDims.D2) && SceneDims.visible(0, SceneDims.D3), "visible");
		check(!SceneDims.visible(SceneDims.D2, SceneDims.D3) && SceneDims.visible(SceneDims.D3, SceneDims.D3), "visible 3D");
		File proj = new File(System.getProperty("java.io.tmpdir"), "s2_dims_" + System.nanoTime());
		new File(proj, "configs").mkdirs();
		new File(proj, "Events/level3d").mkdirs();
		write(new File(proj, "configs/level3d.json"), "{\"or\":\"landscape\",\"sceneType\":\"3D\"}");
		write(new File(proj, "configs/scene1.json"), "{\"or\":\"landscape\"}");
		check(SceneDims.is3D(proj.getPath(), "level3d"), "3D scene");
		check(!SceneDims.is3D(proj.getPath(), "scene1") && !SceneDims.is3D(proj.getPath(), "missing"), "2D scene");
		check(SceneDims.modeOfEventPath(proj.getPath() + "/Events/level3d/OnCreate.visual") == SceneDims.D3, "event path 3D");
		check(SceneDims.modeOfEventPath(proj.getPath() + "/Events/scene1/player/onClick.visual") == SceneDims.D2, "event path 2D");
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		check(SceneDims.of(o, SceneDims.D3) == SceneDims.D3, "of fallback (old files)");
		o.addProperty("dims", "2D");
		check(SceneDims.of(o, SceneDims.BOTH) == SceneDims.D2, "of dims");

		write(new File(args[0]), code.toString());
		StringBuilder kj = new StringBuilder("{\n");
		int i = 0;
		for (Map.Entry<String, String[]> e : keys.entrySet()) {
			kj.append("  \"").append(esc(e.getKey())).append("\": [\"").append(esc(e.getValue()[0])).append("\", \"").append(esc(e.getValue()[1])).append("\"]");
			kj.append(++i < keys.size() ? ",\n" : "\n");
		}
		write(new File(args[1]), kj.append("}\n").toString());
		for (Map.Entry<String, int[]> e : perCat.entrySet())
			System.out.println(String.format("  %-22s %3d actions, %2d C-blocks, %2d values", e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
		System.out.println("3D blocks: " + n[0] + " action / C blocks, " + n[1] + " value blocks, " + keys.size() + " translation keys");
		if (fails > 0) {
			System.out.println(fails + " FAILURES");
			System.exit(1);
		}
	}

	static void write(File f, String s) throws Exception {
		Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
		w.write(s);
		w.close();
	}
}
