package com.star4droid.worldbuilder;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.star4droid.worldbuilder.editor.Editor;
import com.star4droid.worldbuilder.engine.SceneObject;
import com.star4droid.worldbuilder.engine.Settings;

/**
 * Desktop testing only (--script="..."): plays actions and saves screenshots, so every screen can be checked without a phone.
 * Commands, separated by ';': wait N | shot NAME | tap X Y | drag X1 Y1 X2 Y2 [FRAMES] | select DEF | tool N | mode N | tab N |
 * sheet N | cat N | place DEF | scale X Y Z | fx NAME | time H | cam X Y Z YAW PITCH DIST | dialog NAME | save | open NAME | quality N | fresh
 * Positions are fractions of the window (0..1).
 */
final class TestScript {
    final WorldBuilder app; final String[] cmds; final String dir;
    boolean fresh;
    int at, wait = 20, dragLeft; float dx0, dy0, dx1, dy1; int dragTotal;

    TestScript(WorldBuilder app, String script, String dir) {
        this.app = app; this.dir = dir; cmds = script.split(";");
        fresh = script.contains("fresh");
    }

    float fx(String s) { return Float.parseFloat(s) * Gdx.graphics.getWidth(); }
    float fy(String s) { return Float.parseFloat(s) * Gdx.graphics.getHeight(); }

    int loadFrames;
    void shot(String name) {
        Pixmap p = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        java.nio.ByteBuffer bb = p.getPixels(); for (int i = 3; i < bb.limit(); i += 4) bb.put(i, (byte) 255);
        PixmapIO.writePNG(Gdx.files.absolute(dir + "/" + name + ".png"), p, 6, true); p.dispose();
    }

    void frame() {
        if (app.ui.loading) { if (++loadFrames == 6 && cmds[0].trim().equals("loadshot")) shot("loading"); return; }
        if (dragLeft > 0) {
            dragLeft--;
            float t = 1 - dragLeft / (float) dragTotal;
            app.input().touchDragged((int) (dx0 + (dx1 - dx0) * t), (int) (dy0 + (dy1 - dy0) * t), 0);
            if (dragLeft == 0) app.input().touchUp((int) dx1, (int) dy1, 0, 0);
            return;
        }
        if (wait-- > 0) return;
        if (at >= cmds.length) { Gdx.app.exit(); return; }
        String[] c = cmds[at++].trim().split(" ");
        Editor ed = app.editor;
        wait = 3;
        try {
            switch (c[0]) {
                case "wait": wait = Integer.parseInt(c[1]); break;
                case "shot": {
                    Pixmap p = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                    java.nio.ByteBuffer bb = p.getPixels(); for (int i = 3; i < bb.limit(); i += 4) bb.put(i, (byte) 255);
                    PixmapIO.writePNG(Gdx.files.absolute(dir + "/" + c[1] + ".png"), p, 6, true); p.dispose();
                    System.out.println("shot " + c[1] + " fps~" + Gdx.graphics.getFramesPerSecond() + " objs " + app.renderer.drawnObjects + " tris " + app.renderer.drawnTris);
                    break;
                }
                case "tap": app.input().touchDown((int) fx(c[1]), (int) fy(c[2]), 0, 0); app.input().touchUp((int) fx(c[1]), (int) fy(c[2]), 0, 0); break;
                case "drag": dx0 = fx(c[1]); dy0 = fy(c[2]); dx1 = fx(c[3]); dy1 = fy(c[4]); dragTotal = dragLeft = c.length > 5 ? Integer.parseInt(c[5]) : 12; app.input().touchDown((int) dx0, (int) dy0, 0, 0); break;
                case "select": { boolean add = c.length > 2; if (!add) ed.selection.clear(); for (SceneObject o : ed.scene.objects) if (o.def.id.equals(c[1])) { ed.selection.add(o); if (!add) break; } ed.onSelection.run(); ed.focusSelection(); break; }
                case "tool": ed.tool = Integer.parseInt(c[1]); ed.onSelection.run(); break;
                case "mode": ed.mode = Integer.parseInt(c[1]); app.ui.build(); break;
                case "tab": setField("tab", Integer.parseInt(c[1])); app.ui.build(); break;
                case "sheet": setField("sheetTab", Integer.parseInt(c[1])); app.ui.build(); break;
                case "cat": setField("cat", Integer.parseInt(c[1])); app.ui.build(); break;
                case "place": ed.placing = c[1]; ed.onSelection.run(); break;
                case "scale": for (SceneObject o : ed.selection) { o.scale.set(Float.parseFloat(c[1]), Float.parseFloat(c[2]), Float.parseFloat(c[3])); o.markDirty(); } ed.onValues.run(); break;
                case "fx": for (SceneObject o : ed.selection) o.fx = c[1]; ed.onSelection.run(); break;
                case "time": ed.scene.env.time = Float.parseFloat(c[1]); break;
                case "cam": { float[] v = new float[6]; for (int i = 0; i < 6; i++) v[i] = Float.parseFloat(c[i + 1]); ed.rig.set(v); break; }
                case "quality": Settings.preset(Integer.parseInt(c[1])); break;
                case "save": app.save(true); break;
                case "fxcheck": { int bad = 0; for (String n : com.star4droid.worldbuilder.engine.Assets.fxNames) if (!app.renderer.fxWorks(n)) { bad++; System.out.println("FX FAIL " + n); } System.out.println("fx checked " + com.star4droid.worldbuilder.engine.Assets.fxNames.size + " bad " + bad); break; }
                case "new": app.newWorld(Integer.parseInt(c[1])); break;
                case "rot": for (SceneObject o : ed.selection) { o.rotY = Float.parseFloat(c[1]); o.markDirty(); } break;
                case "put": { SceneObject o = ed.scene.create(c[1], Float.parseFloat(c[2]), ed.scene.terrain.height(Float.parseFloat(c[2]), Float.parseFloat(c[3])), Float.parseFloat(c[3])); if (c.length > 6) o.scale.set(Float.parseFloat(c[4]), Float.parseFloat(c[5]), Float.parseFloat(c[6])); o.rotY = c.length > 7 ? Float.parseFloat(c[7]) : 0; wait = 0; break; }
                case "showcol": Settings.showColliders = c[1].equals("1"); break;
                case "settool": ed.setTool(Integer.parseInt(c[1])); break;
                case "scrollend": { java.lang.reflect.Field fp = app.ui.getClass().getDeclaredField("tabPane"); fp.setAccessible(true); com.badlogic.gdx.scenes.scene2d.ui.ScrollPane sp = (com.badlogic.gdx.scenes.scene2d.ui.ScrollPane) fp.get(app.ui); if (sp != null) { sp.layout(); sp.setScrollY(sp.getMaxY()); sp.updateVisualScroll(); } break; }
                case "coldrag": { float[] p = ed.colHandleScreen(Integer.parseInt(c[1])); if (p != null) { dx0 = p[0]; dy0 = p[1]; dx1 = dx0 + fx(c[2]); dy1 = dy0 + fy(c[3]); dragTotal = dragLeft = 12; app.input().touchDown((int) dx0, (int) dy0, 0, 0); } break; }
                case "embed": { app.save(false); app.embedTest = new com.star4droid.worldbuilder.engine.WorldView(com.star4droid.worldbuilder.engine.WorldIO.file(ed.scene.name)); app.embedTransform.idt().translate(Float.parseFloat(c[1]), 0, 0).rotate(0, 1, 0, Float.parseFloat(c[2])).scale(Float.parseFloat(c[3]), Float.parseFloat(c[3]), Float.parseFloat(c[3])); app.ui.stage.clear(); break; }
                case "embedinfo": System.out.println("embed ready " + app.embedTest.isReady() + " err " + app.embedTest.error() + " colliders " + app.embedTest.colliders().size / 11 + " ground " + app.embedTest.groundHeights().length); break;
                case "undo": ed.doUndo(); break;
                case "redo": ed.doRedo(); break;
                case "info": System.out.println("objects " + ed.scene.objects.size + " selected " + ed.selection.size + (ed.selection.size > 0 ? " " + ed.selection.first().def.id + " pos " + ed.selection.first().pos + " scale " + ed.selection.first().scale + " rotY " + ed.selection.first().rotY : "") + " undo " + ed.undo.canUndo() + " dirty " + ed.dirty); break;
                case "open": app.openWorld(c[1].replace('_', ' ')); break;
                case "dialog":
                    switch (c[1]) {
                        case "settings": app.ui.dialogs.settings(); break;
                        case "worlds": app.ui.dialogs.worlds(); break;
                        case "help": app.ui.dialogs.help(); break;
                        case "number": app.ui.dialogs.number("Position X", 27.55f, v -> System.out.println("number " + v)); break;
                        case "new": app.ui.dialogs.newWorld(); break;
                        case "close": app.ui.dialogs.closeAll(); break;
                        case "shaders": app.ui.dialogs.shaders("", s -> { for (SceneObject o : ed.selection) o.fx = s; }); break;
                        case "color": { float[] rgb = ed.selection.first().tintA; app.ui.dialogs.color("Walls", rgb, rgb.clone(), (r, g, b) -> { }, null, null); break; }
                    }
                    break;
            }
        } catch (Exception e) { System.out.println("script error at " + c[0] + ": " + e); e.printStackTrace(); }
    }

    private void setField(String name, int v) throws Exception {
        java.lang.reflect.Field f = app.ui.getClass().getDeclaredField(name); f.setAccessible(true); f.setInt(app.ui, v);
    }
}
