package com.star4droid.star2d.ui.behaviors;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Plain data about the selected element and its project, gathered on the GL thread for the behavior UI. */
public final class BehaviorEnv {
	public String projectPath = "", imagesPath = "", soundsPath = "", scene = "", element = "";
	/** the "type" property (DYNAMIC / KINEMATIC / STATIC / UI) and the TYPE (BOX, TEXT...) */
	public String bodyType = "", elementType = "";
	/** body script file of the element / script of the scene, null when there is none */
	public String scriptFile, sceneScriptFile;

	public boolean isUi() {
		return "UI".equals(bodyType) && !is3D();
	}

	/** An element of a 3D scene's world (TYPE OBJ3D, LIGHT3D or CAMERA3D): only 3D behaviors fit it. */
	public boolean is3D() {
		return com.star4droid.star2d.behaviors.ElementTypes.is3D(elementType);
	}

	/** GL thread. */
	public static BehaviorEnv collect(String element) {
		BehaviorEnv e = new BehaviorEnv();
		e.element = element == null ? "" : element;
		LibgdxEditor ed = Bridge.editor();
		if (ed == null) return e;
		Project p = ed.getProject();
		e.projectPath = p.getPath();
		e.imagesPath = p.getImagesPath();
		e.soundsPath = p.get("sounds");
		e.scene = ed.getScene();
		File ss = new File(p.getSceneScript(e.scene));
		if (ss.isFile() && ss.length() > 0) e.sceneScriptFile = ss.getPath();
		if (element != null) {
			for (Actor a : ed.getActors()) {
				if (!element.equals(a.getName())) continue;
				PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
				if (ps == null) break;
				e.bodyType = ps.containsKey("type") ? ps.getString("type") : "";
				e.elementType = ps.containsKey("TYPE") ? ps.getString("TYPE") : "";
				String script = ps.containsKey("Script") ? ps.getString("Script") : "";
				String sc = e.scene;
				if (script.contains("/")) {
					String[] sp = script.split("/");
					sc = sp[0];
					script = sp.length > 1 ? sp[1] : "";
				}
				if (script.length() > 0) {
					File f = new File(p.getBodyScriptPath(script, sc));
					if (f.isFile() && f.length() > 0) e.scriptFile = f.getPath();
				}
				break;
			}
		}
		return e;
	}

	/** Inner elements of the UI holders of the scene as {"holder/nodeId", "UINODE", widget type}; GL thread. */
	public static List<String[]> uiNodes() {
		List<String[]> out = new ArrayList<>();
		LibgdxEditor ed = Bridge.editor();
		if (ed == null) return out;
		String project = ed.getProject().getPath();
		for (Actor a : ed.getActors()) {
			try {
				PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
				if (ps == null || !"UIHOLDER".equals(ps.getString("TYPE")) || ps.getString("ui").isEmpty()) continue;
				File f = com.star4droid.template.ui.UIView.uiFile(project, ps.getString("ui"));
				com.star4droid.template.ui.UIDocument doc = f.isFile() ? com.star4droid.template.ui.UIDocument.read(f) : null;
				if (doc != null)
					for (com.star4droid.template.ui.UINode n : doc.allNodes())
						out.add(new String[] {ps.getString("name") + "/" + n.id, "UINODE", n.type});
			} catch (RuntimeException ignored) {
			}
		}
		return out;
	}

	/** Elements of the scene as {name, TYPE}; GL thread. Lights are left out (they are not PlayerItems). */
	public static List<String[]> elements() {
		List<String[]> out = new ArrayList<>();
		LibgdxEditor ed = Bridge.editor();
		if (ed == null) return out;
		for (Actor a : ed.getActors()) {
			PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
			if (ps == null || !ps.containsKey("name")) continue;
			String type = ps.containsKey("TYPE") ? ps.getString("TYPE") : "";
			if (type.equals("LIGHT")) continue;
			out.add(new String[] {ps.getString("name"), type});
		}
		return out;
	}
}
