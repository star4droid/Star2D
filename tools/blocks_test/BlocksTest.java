import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.blocks.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/** Offline checks of the behavior blocks (no Android): scanner vs reflection, generated code, scene filter, descriptions. */
public class BlocksTest {
	static int failures = 0;

	static void check(boolean ok, String what) {
		if (!ok) {
			failures++;
			System.out.println("FAIL: " + what);
		}
	}

	static void eq(Object a, Object b, String what) {
		boolean ok = a == null ? b == null : a.equals(b);
		if (!ok) {
			failures++;
			System.out.println("FAIL: " + what + "  expected=[" + b + "] actual=[" + a + "]");
		}
	}

	static String read(File f) throws Exception {
		return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
	}

	static BehaviorBlocks.Spec by(List<BehaviorBlocks.Spec> l, String method) {
		for (BehaviorBlocks.Spec s : l) if (s.method.equals(method) && s.params.size() >= 0) return s;
		return null;
	}

	static BehaviorBlocks.Spec byLabel(List<BehaviorBlocks.Spec> l, String label) {
		for (BehaviorBlocks.Spec s : l) if (s.label.equals(label)) return s;
		return null;
	}

	static String kinds(BehaviorBlocks.Spec s) {
		StringBuilder sb = new StringBuilder(s.ret).append(":");
		for (BehaviorBlocks.Param p : s.params) sb.append(p.kind).append(',');
		return sb.toString();
	}

	/** Example value for a slot of a block, as the editor would hold it. */
	static String sample(BehaviorBlocks.Param p, int i) {
		switch (p.kind) {
			case "bool": return "true";
			case "text": return "\"abc\"";
			case "item": return "enemy1";
			case "color": return "FF0000FF";
			default: return String.valueOf(i + 3);
		}
	}

	public static void main(String[] argv) throws Exception {
		File root = new File(argv[0]);
		PrintWriter gen = new PrintWriter(new FileWriter(System.getProperty("gen.out")));

		// ---------------------------------------------------------------- 1. built-ins: reflection == source scanner
		Map<String, List<BehaviorBlocks.Spec>> builtIns = BehaviorBlocks.builtIns();
		check(builtIns.size() >= 6, "all six exemplar behaviors have blocks: " + builtIns.keySet());
		int total = 0;
		for (Map.Entry<String, List<BehaviorBlocks.Spec>> e : builtIns.entrySet()) {
			File src = new File(root, "app/src/main/java/com/star4droid/template/behaviors/lib/" + e.getKey() + ".java");
			List<BehaviorBlocks.Spec> fromSrc = BlockSourceScanner.scan(read(src), "x");
			eq(fromSrc.size(), e.getValue().size(), e.getKey() + ": same number of blocks (reflection vs source)");
			for (BehaviorBlocks.Spec r : e.getValue()) {
				total++;
				BehaviorBlocks.Spec s = null;
				for (BehaviorBlocks.Spec x : fromSrc) if (x.title.equals(r.title)) s = x;
				check(s != null, e.getKey() + ": source scan finds " + r.title);
				if (s == null) continue;
				eq(kinds(s), kinds(r), r.title + " kinds");
				eq(s.label, r.label, r.title + " label");
				eq(s.labelAr, r.labelAr, r.title + " labelAr");
				eq(s.desc, r.desc, r.title + " desc");
				eq(s.descAr, r.descAr, r.title + " descAr");
				eq(s.statementCode(), r.statementCode(), r.title + " statement code");
				eq(s.reporterTemplate(), r.reporterTemplate(), r.title + " reporter template");
				check(r.label.length() > 0 && r.desc.length() > 0, r.title + " has label and description");
				check(r.labelAr.length() > 0 && r.descAr.length() > 0, r.title + " has Arabic label and description");
				check(r.fqn.equals("com.star4droid.template.behaviors.lib." + e.getKey()), r.title + " fqn " + r.fqn);
			}
		}
		System.out.println("built-in blocks: " + total + " in " + builtIns.size() + " behaviors");
		check(total >= 40, "many blocks (>= 40), got " + total);

		// ---------------------------------------------------------------- 2. a project behavior: textual scanner
		File proj = new File(System.getProperty("java.io.tmpdir"), "s2_blocks_proj_" + System.nanoTime());
		File bdir = new File(proj, BehaviorBlocks.PROJECT_DIR);
		bdir.mkdirs();
		java.nio.file.Files.copy(new File(root, "tools/blocks_test/samples/Health.java").toPath(), new File(bdir, "Health.java").toPath());
		List<BehaviorBlocks.Spec> h = BehaviorBlocks.project(proj.getPath()).get("Health");
		check(h != null, "project behavior Health found");
		Set<String> titles = new TreeSet<String>();
		for (BehaviorBlocks.Spec s : h) titles.add(s.title);
		System.out.println("Health blocks: " + titles);
		eq(titles.toString(), "[Health.damage(float), Health.damage(float,float), Health.follow, Health.getAttacker, Health.getHealth, Health.getLabel, Health.heal, "
				+ "Health.isDead, Health.maxAsLong, Health.setInvincible, Health.setLabel, Health.setMaxHealth, Health.tint, Health.useJoystick]",
				"Health: exactly the supported @Block methods (comments, static, private, generic and List params are ignored; overloads get their types in the title)");
		BehaviorBlocks.Spec dmg = byLabel(h, "hurt");
		eq(dmg.label, "hurt", "label");
		eq(dmg.desc, "Takes damage. Dies at 0.", "string concatenation in desc");
		eq(dmg.descAr, "يأخذ ضرراً \"ويموت\" عند الصفر.", "escapes + Arabic in descAr");
		eq(dmg.category, "Health", "category");
		eq(dmg.params.get(0).name, "amount", "args name");
		eq(dmg.paletteCategory(), "⚙ Health", "palette category");
		eq(by(h, "heal").params.get(0).name, "amount", "final param");
		eq(by(h, "getHealth").ret, "num", "float return -> num");
		eq(by(h, "isDead").ret, "bool", "boolean return -> bool");
		eq(by(h, "getLabel").ret, "text", "String return -> text");
		eq(by(h, "getAttacker").ret, "item", "PlayerItem return -> item");
		eq(by(h, "tint").params.get(0).kind, "color", "Color param");
		eq(by(h, "useJoystick").params.get(0).kind, "item", "Joystick param -> element slot");
		eq(by(h, "follow").params.size(), 3, "follow has 3 params");
		eq(by(h, "follow").params.get(1).name, "distance", "param names from source without args");
		eq(by(h, "setMaxHealth").retType, "void", "annotation between @Block and method");
		eq(Arrays.toString(by(h, "tint").fields()), "[body(Body), color]", "color token");
		eq(Arrays.toString(by(h, "setInvincible").fields()), "[body(Body), on(Boolean)]", "boolean token");
		eq(Arrays.toString(by(h, "setLabel").fields()), "[body(Body), text(String)]", "text token");
		eq(Arrays.toString(by(h, "follow").fields()), "[body(Body), leader(Body), distance, smooth(Boolean)]", "mixed tokens");
		eq(by(h, "isDead").reporterDisplay(false), "is dead ___", "reporter display");
		eq(by(h, "isDead").reporterDisplay(true), "ميت ___", "reporter display Arabic");
		eq(by(h, "heal").descMap().toString(), "{en=Adds hit points (never above the maximum).}", "desc map");
		eq(dmg.descMap().keySet().toString(), "[en, ar]", "desc map languages");

		// scanner never throws on garbage
		check(BlockSourceScanner.scan("@Block(", "p").isEmpty(), "garbage 1");
		check(BlockSourceScanner.scan("class A { @Block(label=\"x\") }", "p").isEmpty(), "garbage 2");
		check(BlockSourceScanner.scan(null, "p").isEmpty(), "null");
		check(BlockSourceScanner.scan("class A { @Block(label = \"a\") public void f(", "p").isEmpty(), "garbage 3");
		check(BlockSourceScanner.scan("class A { @Block(desc = \"no label\") public void f() {} }", "p").isEmpty(), "label is required");
		eq(BlockSourceScanner.scan("package a.b; class A { @Block(\"short\") public void f() {} }", "p").get(0).fqn, "a.b.A", "package + lone value is the label");

		// ---------------------------------------------------------------- 3. generated code (to be compiled by the python harness)
		List<BehaviorBlocks.Spec> all = new ArrayList<BehaviorBlocks.Spec>(BehaviorBlocks.flat(proj.getPath()));
		int n = 0;
		for (BehaviorBlocks.Spec s : all) {
			Object[] args = new Object[s.params.size() + 3];
			args[0] = "player";
			for (int i = 0; i < s.params.size(); i++) args[i + 1] = sample(s.params.get(i), i);
			for (int i = s.params.size() + 1; i < args.length; i++) args[i] = "";
			gen.println("// " + s.title);
			if (!s.isValue()) {
				String code = String.format(s.statementCode(), args);
				check(code.contains("BehaviorOps.of(") && code.contains("\"" + s.behaviorId + "\")"), s.title + " calls BehaviorOps.of");
				gen.println(code);
			} else {
				String expr = String.format(s.reporterTemplate(), args);
				String type = s.ret.equals("bool") ? "boolean" : s.ret.equals("text") ? "String" : s.ret.equals("item") ? "PlayerItem" : s.retType.equals("long") ? "long" : "float";
				gen.println(type + " v" + (n++) + " = " + expr + ";");
			}
		}
		// fixed blocks and conditions of the always-visible group are in the (Android) BlockCatalog; their templates are checked by the python harness
		gen.close();

		// ---------------------------------------------------------------- 4. which behaviors a scene uses
		File scenes = new File(proj, "scenes");
		scenes.mkdirs();
		java.nio.file.Files.write(new File(scenes, "main").toPath(), ("[{\"name\":\"hero\",\"behaviors\":\"[{\\\"type\\\":\\\"Health\\\",\\\"params\\\":{},\\\"enabled\\\":true},{\\\"type\\\":\\\"Shooter\\\"}]\"},"
				+ "{\"name\":\"rock\",\"behaviors\":\"\"},{\"name\":\"x\"}]").getBytes("UTF-8"));
		java.nio.file.Files.write(new File(scenes, "menu").toPath(), "[{\"name\":\"b\",\"behaviors\":[{\"type\":\"Spin\"}]}]".getBytes("UTF-8"));
		eq(new TreeSet<String>(SceneBehaviors.inProject(proj.getPath(), "main")).toString(), "[Health, Shooter]", "scene main behaviors");
		eq(new TreeSet<String>(SceneBehaviors.inProject(proj.getPath(), "menu")).toString(), "[Spin]", "scene menu behaviors (array form)");
		eq(new TreeSet<String>(SceneBehaviors.inProject(proj.getPath(), null)).toString(), "[Health, Shooter, Spin]", "no scene: whole project");
		eq(new TreeSet<String>(SceneBehaviors.inProject(proj.getPath(), "nope")).toString(), "[Health, Shooter, Spin]", "unknown scene: whole project");
		check(SceneBehaviors.inProject(proj.getPath() + "_missing", "main").isEmpty(), "missing project");
		eq(SceneBehaviors.sceneOfEventPath("/sd/Proj/Events/main/hero/onClick.visual"), "main", "scene from event path");
		eq(SceneBehaviors.projectOfEventPath("/sd/Proj/Events/main/hero/onClick.visual"), "/sd/Proj", "project from event path");
		eq(SceneBehaviors.sceneOfEventPath("/x/y.visual"), null, "no Events in path");
		Set<String> ids = BehaviorBlocks.all(proj.getPath()).keySet();
		eq(SceneBehaviors.mentionedIn("{\"title\":\"Health.damage\"}", ids).toString(), "[Health]", "block of a behavior already in the event");
		eq(SceneBehaviors.mentionedIn("{\"title\":\"addBehavior\",\"fields\":[{\"value\":\"Shooter\"}]}", ids).toString(), "[Shooter]", "addBehavior names a behavior");
		check(SceneBehaviors.mentionedIn("{\"title\":\"Shooter\"}", ids).isEmpty(), "just the word is not enough");

		// ---------------------------------------------------------------- 5. descriptions
		eq(BlockDesc.parse(JsonParser.parseString("{\"en\":\"A\",\"AR\":\"ب\",\"fr\":\" \"}")).toString(), "{en=A, ar=ب}", "map: lowercased codes, blanks dropped");
		eq(BlockDesc.parse(JsonParser.parseString("\"plain\"")).toString(), "{en=plain}", "string = English");
		check(BlockDesc.parse(null).isEmpty(), "missing desc (old files)");
		check(BlockDesc.parse(JsonParser.parseString("[1,2]")).isEmpty(), "array ignored");
		Map<String, String> m = BlockDesc.parse(JsonParser.parseString("{\"ar\":\"عربي\",\"en\":\"english\",\"es\":\"español\"}"));
		eq(BlockDesc.pick(m, "ar"), "عربي", "pick app language");
		eq(BlockDesc.pick(m, "fr"), "english", "missing language falls back to English");
		eq(BlockDesc.pick(m, null), "english", "no language");
		Map<String, String> m2 = BlockDesc.parse(JsonParser.parseString("{\"tr\":\"türkçe\"}"));
		eq(BlockDesc.pick(m2, "ar"), "türkçe", "no English: first available");
		check(BlockDesc.pick(new LinkedHashMap<String, String>(), "en") == null, "nothing written");
		JsonObject o = BlockDesc.toJson(m);
		eq(o.get("ar").getAsString(), "عربي", "toJson round trip");

		System.out.println(failures == 0 ? "ALL OK" : failures + " FAILURES");
		System.exit(failures == 0 ? 0 : 1);
	}
}
