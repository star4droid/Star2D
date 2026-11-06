import com.star4droid.star2d.behaviors.ObjectVarsGen;
import java.nio.file.*;
import java.util.*;

/** Unit checks of ObjectVarsGen + writes the generated element code (args[0]) for tools/test_objectvars.py. */
public class ObjectVarsTest {
    static int failures = 0;
    static void check(boolean ok, String what) { if (!ok) { failures++; System.out.println("FAIL " + what); } else System.out.println("ok   " + what); }

    public static void main(String[] a) throws Exception {
        check(ObjectVarsGen.parse(null).isEmpty() && ObjectVarsGen.parse("").isEmpty() && ObjectVarsGen.parse("{broken").isEmpty(), "absent / broken property = no variables");
        String json = "[{\"name\":\"health\",\"type\":\"int\",\"value\":\"3\"},{\"name\":\"speed\",\"type\":\"float\",\"value\":\"2.5\"},"
                + "{\"name\":\"title\",\"type\":\"String\",\"value\":\"Hi \\\"you\\\"\"},{\"name\":\"alive\",\"type\":\"boolean\",\"value\":\"true\"},"
                + "{\"name\":\"bag\",\"type\":\"List\",\"value\":\"1, 2.5, apple\"},{\"name\":\"target\",\"type\":\"PlayerItem\",\"value\":\"enemy\"},"
                + "{\"name\":\"1bad\",\"type\":\"int\",\"value\":\"0\"},{\"name\":\"class\",\"type\":\"int\"},{\"name\":\"health\",\"type\":\"float\"}]";
        List<ObjectVarsGen.Var> props = ObjectVarsGen.parse(json);
        check(props.size() == 6, "invalid names and duplicates dropped (" + props.size() + ")");
        check(ObjectVarsGen.parse(ObjectVarsGen.toJson(props)).size() == 6, "toJson round trip");
        check(ObjectVarsGen.toJson(new ArrayList<>()).isEmpty(), "no variables -> empty property");
        String events = "/*@objvar int|coins|5*/\nint x = 1;\n/*@objvar float|health|9*/\n/*@objvar String|msg|hello*/\n";
        List<ObjectVarsGen.Var> blocks = ObjectVarsGen.fromCode(events);
        check(blocks.size() == 3 && blocks.get(0).name.equals("coins") && blocks.get(0).fromBlocks, "block markers read");
        List<ObjectVarsGen.Var> all = ObjectVarsGen.merge(props, blocks);
        check(all.size() == 8 && ObjectVarsGen.find(all, "health").type.equals("int"), "properties win over blocks");
        check(ObjectVarsGen.toJson(all).equals(ObjectVarsGen.toJson(props)), "block variables are not stored in the property");
        check(ObjectVarsGen.literal(new ObjectVarsGen.Var("a", "float", "")).equals("0f"), "empty float = 0f");
        check(ObjectVarsGen.literal(new ObjectVarsGen.Var("a", "int", "2.7")).equals("2"), "int literal");
        check(ObjectVarsGen.literal(new ObjectVarsGen.Var("a", "number", "4")).equals("4.0f"), "type alias number -> float");
        String evCode = "\t\t\t@Override public void onClick(PlayerItem current) {\n" + events
                + "health = health - 1; speed += 1; coins++; msg = msg + \"!\"; bag.add(\"x\"); if (alive) title = title + \"?\";\n\t\t\t}\n"
                + "\t\t\t@Override public void onBodyUpdate(PlayerItem current) {\nhealth += 10;\n\t\t\t}\n";
        String code = ObjectVarsGen.itemCode("FakeDef", "hero", "\t\thero_def.x = 1f;", evCode, all) + ObjectVarsGen.afterBuild("hero", all);
        Files.write(Paths.get(a[0]), code.getBytes("UTF-8"));
        System.exit(failures == 0 ? 0 : 1);
    }
}
