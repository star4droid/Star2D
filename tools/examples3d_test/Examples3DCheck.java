import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorCodeGen;
import com.star4droid.star2d.behaviors.BehaviorData;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.ElementTypes;
import com.star4droid.star2d.behaviors.ValueEmit;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.blocks.BehaviorBlocks;
import com.star4droid.star2d.ui.blocks.BlockModel;
import com.star4droid.star2d.ui.blocks.Blocks3D;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper of tools/gen_3d_examples.py (no phone needed).
 *
 *   blocks OUT.json                 dumps the 3D blocks (Blocks3D) and the blocks of the built-in behaviors
 *   project DIR PLAYER_JAVA OUT.java checks one example project and writes the Java the editor would generate for scenes/scene1
 *                                   (same rules as Helpers/CodeGenerator: element definitions, element events, behaviors with
 *                                   their event handlers, scene events, variables) so the caller can compile it.
 */
public class Examples3DCheck {
	static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	static int errors = 0;

	static void err(String m) {
		errors++;
		System.out.println("ERROR: " + m);
	}

	public static void main(String[] a) throws Exception {
		if (a[0].equals("blocks")) dumpBlocks(a[1]);
		else if (a[0].equals("project")) project(new File(a[1]), new File(a[2]), new File(a[3]));
		System.exit(errors == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------ block dump

	static void dumpBlocks(String out) throws Exception {
		final Map<String, Object> defs = new LinkedHashMap<String, Object>(), reps = new LinkedHashMap<String, Object>();
		Blocks3D.register(new Blocks3D.Sink() {
			public void category(String name, int color, String en, String ar) {}

			public void def(String cat, int color, String title, String fields, String defaults, boolean isIf, String code, int dims, String en, String ar, String ie, String ia) {
				Map<String, Object> m = new LinkedHashMap<String, Object>();
				m.put("fields", fields);
				m.put("code", code);
				m.put("color", color);
				m.put("isIf", isIf);
				m.put("category", cat);
				defs.put(title, m);
			}

			public void reporter(String group, String display, String template, char shape, String slots, int dims, String da, String ie, String ia) {
				reps.put(display, template);
			}

			public void field(String name, String en, String ar) {}

			public void choices(String type, String[] values) {}
		});
		Map<String, Object> bhv = new LinkedHashMap<String, Object>();
		for (Map.Entry<String, List<BehaviorBlocks.Spec>> e : BehaviorBlocks.builtIns().entrySet()) {
			for (BehaviorBlocks.Spec s : e.getValue()) {
				Map<String, Object> m = new LinkedHashMap<String, Object>();
				m.put("behavior", s.behaviorId);
				m.put("method", s.method);
				m.put("title", s.title);
				m.put("value", s.isValue());
				m.put("display", s.isValue() ? s.reporterDisplay(false) : "");
				m.put("code", s.isValue() ? s.reporterTemplate() : s.statementCode());
				m.put("fields", String.join(" ", s.fields()));
				bhv.put(s.behaviorId + "." + s.method, m);
				if (s.isValue()) reps.put(s.reporterDisplay(false), s.reporterTemplate());
				else {
					Map<String, Object> d = new LinkedHashMap<String, Object>();
					d.put("fields", String.join(" ", s.fields()));
					d.put("code", s.statementCode());
					d.put("color", 0);
					d.put("isIf", false);
					d.put("category", s.paletteCategory());
					defs.put(s.title, d);
				}
			}
		}
		Map<String, Object> all = new LinkedHashMap<String, Object>();
		all.put("defs", defs);
		all.put("reporters", reps);
		all.put("behaviorBlocks", bhv);
		Files.write(new File(out).toPath(), GSON.toJson(all).getBytes(StandardCharsets.UTF_8));
	}

	// ------------------------------------------------------------------ project

	static String read(File f) {
		try {
			return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
		} catch (Exception e) {
			return "";
		}
	}

	static final String[] EVENTS = {"onClick", "onTouchStart", "onTouchEnd", "OnBodyCreated", "OnBodyUpdate", "onBodyCollided", "onBodyCollideEnd"};
	static final String[] SIGS = {"onClick(PlayerItem current)", "onTouchStart(PlayerItem current, InputEvent event)",
			"onTouchEnd(PlayerItem current, InputEvent event)", "onBodyCreated(PlayerItem current)", "onBodyUpdate(PlayerItem current)",
			"onCollisionBegin(PlayerItem current, PlayerItem body2)", "onCollisionEnd(PlayerItem current, PlayerItem body2)"};

	static String defType(String type) {
		switch (type) {
			case "BOX": return "BoxDef";
			case "JOYSTICK": return "JoyStickDef";
			case "TEXT": return "TextDef";
			case "PROGRESS": return "ProgressDef";
			case "UIHOLDER": return "UIHolderDef";
			case "OBJ3D": return "Object3DDef";
			case "LIGHT3D": return "Light3DDef";
			case "CAMERA3D": return "Camera3DDef";
			case "CAMERAVIEW": return "CameraViewDef";
			default: return null;
		}
	}

	static void project(final File dir, File playerTemplate, File out) throws Exception {
		final String scene = "scene1";
		List<Map<String, Object>> els = new Gson().fromJson(read(new File(dir, "scenes/" + scene)), new TypeToken<List<Map<String, Object>>>() {}.getType());
		final Map<String, String> typeOf = new HashMap<String, String>();
		for (Map<String, Object> e : els) typeOf.put(String.valueOf(e.get("name")), String.valueOf(e.get("TYPE")));
		final File events = new File(dir, "Events/" + scene);
		BehaviorCodeGen.Env env = new BehaviorCodeGen.Env() {
			public String projectPath() {
				return dir.getPath();
			}

			public String eventCode(String body, String event) {
				return read(new File(events, "private/" + body + "/" + event + ".java"));
			}

			public String readFile(String path) {
				return read(new File(path));
			}
		};
		StringBuilder items = new StringBuilder(), behaviors = new StringBuilder();
		StringBuilder vars = new StringBuilder();
		HashSet<String> usedBehaviorFolders = new HashSet<String>();
		for (Map<String, Object> e : els) {
			String name = String.valueOf(e.get("name")), type = String.valueOf(e.get("TYPE"));
			String dt = defType(type);
			if (dt == null) {
				err(name + ": unknown TYPE " + type);
				continue;
			}
			if (!name.equals(String.valueOf(e.get("Script")))) err(name + ": Script property must equal the name (events are read from Events/<scene>/private/<Script>)");
			Class<?> cls = Class.forName("com.star4droid.star2d.ElementDefs." + dt);
			Object ins = cls.getConstructor().newInstance();
			StringBuilder init = new StringBuilder();
			for (Map.Entry<String, Object> p : e.entrySet()) {
				String k = p.getKey();
				if ("(TYPE)(Shape)(lock)(parent)(prefab)(pid)(pbase)(Script)(behaviors)(scriptVars)".contains("(" + k + ")")) continue;
				java.lang.reflect.Field f;
				try {
					f = cls.getField(k.replace(" ", "_"));
				} catch (NoSuchFieldException nsf) {
					continue;
				}
				String ft = f.getType().getName().replace("java.lang.", "");
				String v = String.valueOf(p.getValue());
				if (ft.equals("float")) {
					float fv;
					try {
						fv = Float.parseFloat(v);
					} catch (NumberFormatException ex) {
						err(name + "." + k + " is not a number: " + v);
						continue;
					}
					if (f.getFloat(ins) == fv) continue;
					init.append("\t\t").append(name).append("_def.").append(f.getName()).append("=").append(v).append("f;\n");
				} else if (ft.equals("boolean")) {
					if (!v.equals("true") && !v.equals("false")) err(name + "." + k + " is not true/false: " + v);
					if (f.getBoolean(ins) == v.equals("true")) continue;
					init.append("\t\t").append(name).append("_def.").append(f.getName()).append("=").append(v).append(";\n");
				} else if (ft.equals("String")) {
					if (String.valueOf(f.get(ins)).equals(v)) continue;
					init.append("\t\t").append(name).append("_def.").append(f.getName()).append("=").append(ValueEmit.javaLiteral(v)).append(";\n");
				}
			}
			StringBuilder evm = new StringBuilder();
			for (int i = 0; i < EVENTS.length; i++) {
				String code = read(new File(events, "private/" + name + "/" + EVENTS[i] + ".java"));
				if (code.trim().isEmpty()) continue;
				evm.append("\t\t\t@Override public void ").append(SIGS[i]).append(" {\n").append(code).append("\n\t\t\t}\n");
			}
			items.append("\n\t\t").append(dt).append(' ').append(name).append("_def = new ").append(dt).append("();\n").append(init);
			if (evm.length() > 0) items.append("\t\t").append(name).append("_def.elementEvents = new ElementEventAdapter(\"").append(name).append("\"){\n").append(evm).append("\t\t};\n");
			items.append("\t\t").append(name).append(" = (PlayerItem)(").append(name).append("_def.build(this));\n");
			String parent = e.get("parent") == null ? "" : String.valueOf(e.get("parent"));
			if (parent.length() > 0 && !parent.equals(name)) {
				if (!typeOf.containsKey(parent)) err(name + ": parent " + parent + " does not exist");
				else items.append(parent).append(".addChild(").append(name).append(");\n");
			}
			vars.append(vars.length() == 0 ? "PlayerItem " : ",").append(name);
			// behaviors: check every entry, then generate like the editor
			String bj = e.get("behaviors") == null ? "" : String.valueOf(e.get("behaviors"));
			List<BehaviorData.Entry> list = BehaviorData.parse(bj);
			if (bj.trim().length() > 0 && list.isEmpty()) err(name + ": behaviors JSON could not be read");
			HashSet<String> excl = new HashSet<String>(), seen = new HashSet<String>();
			for (BehaviorData.Entry be : list) {
				BehaviorMeta m = BehaviorCatalog.find(be.type, null);
				if (m == null) {
					err(name + ": behavior " + be.type + " not found");
					continue;
				}
				if (!seen.add(be.type)) err(name + ": behavior " + be.type + " twice");
				String why = m.misfit(String.valueOf(e.get("type")), type);
				if (why != null) err(name + ": behavior " + be.type + " does not fit a " + type + ": " + why);
				if (m.exclusive != null && m.exclusive.length() > 0 && !excl.add(m.exclusive)) err(name + ": two behaviors of the exclusive group " + m.exclusive);
				for (Map.Entry<String, Object> pv : be.params.entrySet()) {
					VarSpec spec = null;
					for (VarSpec s : m.vars) if (s.name.equals(pv.getKey())) spec = s;
					if (spec == null) {
						err(name + ": " + be.type + " has no parameter " + pv.getKey());
						continue;
					}
					if (ValueEmit.expr(spec, pv.getValue()) == null) err(name + ": " + be.type + "." + pv.getKey() + " has a wrong value " + pv.getValue() + " (" + spec.kind + ")");
					if (spec.kind == VarSpec.Kind.REF) {
						List<Object> refs = new ArrayList<Object>();
						if (spec.list) {
							if (pv.getValue() instanceof List) refs.addAll((List<?>) pv.getValue());
						} else refs.add(pv.getValue());
						for (Object r : refs) {
							String rn = String.valueOf(r);
							if (!typeOf.containsKey(rn)) {
								err(name + ": " + be.type + "." + pv.getKey() + " refers to a missing element '" + rn + "'");
								continue;
							}
							String[] ok = ElementTypes.typesOf(spec.elemType);
							if (ok != null) {
								boolean fine = false;
								for (String t : ok) if (t.equals(typeOf.get(rn))) fine = true;
								if (!fine) err(name + ": " + be.type + "." + pv.getKey() + " needs " + java.util.Arrays.toString(ok) + " but '" + rn + "' is " + typeOf.get(rn));
							}
						}
					}
				}
				File evDir = new File(events, "private/" + name + "/behaviors/" + be.type);
				usedBehaviorFolders.add(evDir.getPath());
				String[] files = evDir.list();
				if (files != null) for (String fn : files) {
					String ev = fn.replaceAll("\\.(java|visual|code)$", "");
					boolean known = false;
					for (BehaviorMeta.EventSpec es : m.events) if (es.name.equals(ev)) known = true;
					if (!known) err(name + ": " + be.type + " has no event " + ev);
				}
			}
			behaviors.append(BehaviorCodeGen.forItem(env, name, bj, e.get("scriptVars") == null ? "" : String.valueOf(e.get("scriptVars")), null));
		}
		// behavior event folders without the behavior
		File priv = new File(events, "private");
		File[] bodies = priv.listFiles();
		if (bodies != null) for (File b : bodies) {
			if (!typeOf.containsKey(b.getName())) err("event folder of a missing element: " + b.getName());
			File bf = new File(b, "behaviors");
			File[] ts = bf.listFiles();
			if (ts != null) for (File t : ts) if (!usedBehaviorFolders.contains(t.getPath())) err("behavior event folder without the behavior: " + t.getPath());
			File[] evs = b.listFiles();
			if (evs != null) for (File ev : evs) {
				if (ev.isDirectory()) continue;
				String n = ev.getName().replaceAll("\\.(java|visual)$", "");
				boolean known = false;
				for (String k : EVENTS) if (k.equals(n)) known = true;
				if (!known) err("unknown element event file " + ev.getPath());
			}
		}
		// .visual files must export exactly their .java
		checkVisuals(new File(dir, "Events"));
		// variables
		StringBuilder userVars = new StringBuilder();
		String vj = read(new File(events, "public/variables.json"));
		if (vj.trim().length() > 0) {
			List<Map<String, String>> vl = new Gson().fromJson(vj, new TypeToken<List<Map<String, String>>>() {}.getType());
			for (Map<String, String> v : vl) {
				String am = v.get("accessModifer") == null ? "" : v.get("accessModifer");
				userVars.append(am.equals("") ? " " : "").append(v.get("type")).append(' ').append(v.get("name")).append(' ')
						.append(v.get("value") == null || v.get("value").equals("") ? "" : "= " + v.get("value")).append(";\n");
			}
		}
		String pub = "public/";
		String code = items + "\n" + behaviors + read(new File(events, pub + "OnCreate.java"));
		String tpl = read(playerTemplate);
		String result = String.format(tpl, scene, userVars + vars.toString() + ";\n", code, read(new File(events, pub + "OnPause.java")),
				read(new File(events, pub + "OnResume.java")), read(new File(events, pub + "OnStep.java")), read(new File(events, pub + "onCollisionStart.java")),
				read(new File(events, pub + "onCollisionEnd.java")), "");
		out.getParentFile().mkdirs();
		Files.write(out.toPath(), result.getBytes(StandardCharsets.UTF_8));
	}

	static void checkVisuals(File f) {
		if (f.isDirectory()) {
			File[] l = f.listFiles();
			if (l != null) for (File c : l) checkVisuals(c);
			return;
		}
		String p = f.getPath();
		if (p.endsWith(".java")) {
			if (!new File(p.substring(0, p.length() - 5) + ".visual").isFile()) err("event without blocks (.visual): " + p);
			return;
		}
		if (!p.endsWith(".visual")) return;
		File java = new File(p.substring(0, p.length() - 7) + ".java");
		BlockModel m = BlockModel.load(f);
		String code = m.exportCode(false);
		if (!m.formatFailures.isEmpty()) err(p + ": blocks whose code cannot be formatted: " + m.formatFailures);
		if (!java.isFile()) err(p + ": .java missing");
		else if (!code.trim().equals(read(java).trim())) err(p + ": .visual exports other code than the .java:\n---visual---\n" + code + "\n---java---\n" + read(java));
	}
}
