import com.star4droid.star2d.behaviors.*;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.behaviors.BehaviorText;
import com.star4droid.star2d.ui.uimaker.*;
import com.star4droid.template.ui.*;
import com.star4droid.template.ui.UIWidgetRegistry.*;
import java.io.*;
import java.util.*;

/** Prints "key<TAB>english default" for every dynamically built translation key (UI Maker registry texts, built-in behaviors). */
public class KeyDump {
    public static void main(String[] a) throws Exception {
        for (Widget w : UIWidgetRegistry.all()) {
            UiTr.widgetName(w.type, w.name);
            UiTr.widgetDesc(w.type, w.desc);
            for (Prop p : UIWidgetRegistry.propsFor(w.type)) {
                UiTr.propLabel(w.type, p);
                UiTr.propDesc(w.type, p);
                for (String o : p.options) UiTr.optionLabel(o);
                if (p.group != null) UiTr.groupTitle(p.group);
            }
            UiTr.categoryTitle(w.category);
            for (String s : w.styles) UiTr.optionLabel(s);
        }
        for (Prop p : UIWidgetRegistry.commonProps()) {
            UiTr.propLabel("x", p);
            UiTr.propDesc("x", p);
            for (String o : p.options) UiTr.optionLabel(o);
        }
        for (String c : new String[]{"Layout", "Display", "Input"}) UiTr.categoryTitle(c);
        for (String g : new String[]{UIWidgetRegistry.G_TEXT, UIWidgetRegistry.G_VALUE, UIWidgetRegistry.G_LOOK, UIWidgetRegistry.G_LAYOUT, UIWidgetRegistry.G_BEHAVIOR, UIWidgetRegistry.G_ADV}) UiTr.groupTitle(g);
        for (String r : UITheme.ROLES) UiTr.roleName(r);
        for (String p : UITheme.PRESETS) UiTr.presetName(p);
        for (String t : UiTemplates.names()) { UiTr.templateName(t); UiTr.templateDesc(t); }
        for (String t : UiKit.SCREENS.keySet()) { UiTr.templateName(t); UiTr.templateDesc(t); }
        for (String e : new String[]{"the root cannot move","already first","already last","there is no node above to move into","already at the top level","cannot move a node into itself","the root cannot be wrapped","id cannot be empty","no parent"}) UiTr.err(e);
        // behaviors
        Set<String> types = new LinkedHashSet<String>(Arrays.asList("BOX","CIRCLE","CUSTOM","MAP","TEXT","PROGRESS","JOYSTICK","PARTICLE","UIHOLDER","GROUP","CAMERA","OBJ3D","LIGHT3D","CAMERA3D","CAMERAVIEW"));
        for (String t : types) BehaviorText.elementType(t);
        for (BehaviorMeta m : BehaviorCatalog.builtIns()) {
            BehaviorText.name(m); BehaviorText.desc(m); BehaviorText.category(m); BehaviorText.note(m); BehaviorText.vars(m);
            BehaviorText.requirements(m); BehaviorText.targets(m);
            for (Map.Entry<String,String> e : m.sets.entrySet()) { BehaviorText.setKey(e.getKey()); }
            for (BehaviorMeta.EventSpec e : m.events) { BehaviorText.event(m, e); Tr.t("bhv_e_" + m.id + "_" + e.name, VarSpec.humanize(e.name)); }
        }
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        for (Map.Entry<String,String> e : Tr.SEEN.entrySet()) {
            if (e.getValue() == null) continue;
            out.println(e.getKey() + "\t" + e.getValue().replace("\n", "\\n"));
        }
        out.close();
        System.out.println("keys: " + Tr.SEEN.size());
    }
}
