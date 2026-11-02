package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.ProjectOrientation;

/**
 * One plain sentence about a scene's frame, so the AI never has to guess orientation or size:
 * "scene 'level1': PORTRAIT, 720 wide x 1280 tall (x 0..720, y 0..1280, origin bottom-left, y up)".
 * Semantics (verified in StageImp.init and LibgdxEditor.getLogicWidth/Height): the stored logicWidth is the LONG side,
 * landscape width = logicWidth, portrait width = logicHeight (short side) and height = logicWidth.
 * GL thread.
 */
final class SceneInfo {
    private SceneInfo() {}

    /** width/height of the frame as the game sees it, orientation of the scene. */
    static final class Frame {
        boolean landscape = true;
        float width, height;
        String scene = "";
        /** true when the numbers come from the phone screen because the config has none */
        boolean guessed;
        /** 3D scene (config "sceneType":"3D"): the 3D elements use meters, the frame above is only the 2D layer (HUD). */
        boolean is3D;
    }

    /** 3D element kinds (positions in meters, y up, z = depth; not part of the 2D pixel frame). CAMERAVIEW is a 2D element. */
    static boolean is3DType(String type) {
        if (type == null) return false;
        String t = type.toUpperCase(java.util.Locale.ROOT);
        return t.equals("OBJ3D") || t.equals("LIGHT3D") || t.equals("CAMERA3D");
    }

    static boolean is3DElement(java.util.Map<String, String> m) {
        return m != null && is3DType(m.get("TYPE"));
    }

    /** "3D" or "2D" for a scene: live flag of the open one, else its config file. */
    static boolean is3DScene(LibgdxEditor ed, Project p, String scene) {
        if (ed != null && scene.equals(ed.getScene()) && !ed.isLoading()) return ed.is3DScene();
        try {
            FileHandle cf = Gdx.files.absolute(p.getConfig(scene));
            if (cf.exists()) {
                JsonObject c = JsonParser.parseString(cf.readString()).getAsJsonObject();
                return c.has("sceneType") && "3D".equalsIgnoreCase(c.get("sceneType").getAsString());
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Frame of a scene: live editor values when it is the open one, else its config file. */
    static Frame of(LibgdxEditor ed, Project p, String scene) {
        Frame f = new Frame();
        f.scene = scene;
        f.is3D = is3DScene(ed, p, scene);
        if (ed != null && scene.equals(ed.getScene()) && !ed.isLoading()) {
            ed.getConfig(); // reload the config first: the orientation flag follows it
            f.landscape = ed.isLandscape();
            f.width = ed.getLogicWidth();
            f.height = ed.getLogicHeight();
            return f;
        }
        float a = 0, b = 0;
        try {
            FileHandle cf = Gdx.files.absolute(p.getConfig(scene));
            if (cf.exists()) {
                JsonObject c = JsonParser.parseString(cf.readString()).getAsJsonObject();
                String or = c.has("or") ? c.get("or").getAsString() : "";
                f.landscape = or.isEmpty() || or.equals("landscape");
                if (c.has("logicWidth")) a = Float.parseFloat(c.get("logicWidth").getAsString());
                if (c.has("logicHeight")) b = Float.parseFloat(c.get("logicHeight").getAsString());
            }
        } catch (Exception ignored) {
        }
        if (a <= 0 || b <= 0) {
            f.guessed = true;
            ProjectOrientation.Info info = ProjectOrientation.read(p, scene);
            a = info.longSide > 0 ? info.longSide : Math.max(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            b = info.shortSide > 0 ? info.shortSide : Math.min(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        float longSide = Math.max(a, b), shortSide = Math.min(a, b);
        f.width = f.landscape ? longSide : shortSide;
        f.height = f.landscape ? shortSide : longSide;
        return f;
    }

    static String describe(Frame f) {
        String s = "scene '" + f.scene + "': " + (f.landscape ? "LANDSCAPE" : "PORTRAIT") + ", " + Math.round(f.width) + " wide x " + Math.round(f.height)
                + " tall (x 0.." + Math.round(f.width) + ", y 0.." + Math.round(f.height) + ", origin bottom-left, y up)";
        if (f.is3D)
            s = "3D " + s + ". This px frame is only the 2D layer (HUD, joysticks, UIHOLDER, CAMERAVIEW); OBJ3D/LIGHT3D/CAMERA3D use METERS: x right, y UP, z toward the viewer (depth), ground at y 0";
        return s;
    }

    static String describe(LibgdxEditor ed, Project p, String scene) {
        return describe(of(ed, p, scene));
    }

    /** Adds frame fields to a JSON object: "frame" (the sentence) plus orientation / width / height. */
    static void put(JsonObject o, LibgdxEditor ed, Project p, String scene) {
        Frame f = of(ed, p, scene);
        o.addProperty("frame", describe(f));
        o.addProperty("sceneType", f.is3D ? "3D" : "2D");
    }
}
