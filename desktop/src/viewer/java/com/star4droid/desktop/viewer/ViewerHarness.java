package com.star4droid.desktop.viewer;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.utils.ScreenUtils;
import com.star4droid.star2d.suda.viewer.ModelViewerCore;

import java.io.File;

/**
 * Desktop check of the SUDA3D viewer core: loads a model, prints its info, then saves screenshots of the view modes
 * (mesh, bones, mesh + bones, wireframe, shadows, a later animation frame) and exits.
 * Usage: ViewerHarness <model file> <screenshot dir>
 */
public class ViewerHarness {
    public static void main(String[] args) {
        final File model = new File(args[0]);
        final File out = new File(args.length > 1 ? args[1] : "viewer-shots");
        out.mkdirs();
        final String base = model.getName().replace('.', '_');
        final boolean[] loaded = {false};
        final ModelViewerCore core = new ModelViewerCore(model, new ModelViewerCore.Listener() {
            @Override public void onProgress(float p, String stage) { System.out.printf("progress %.2f %s [%s]%n", p, stage, Thread.currentThread().getName()); }
            @Override public void onLoaded(ModelViewerCore.Info i) {
                loaded[0] = true;
                System.out.println("INFO " + i.format + " size=" + i.fileSize + " verts=" + i.vertices + " tris=" + i.triangles + " meshes=" + i.meshes
                        + " materials=" + i.materials + " textures=" + i.textures + " maxTex=" + i.maxTextureWidth + "x" + i.maxTextureHeight
                        + " bones=" + i.bones + " nodes=" + i.nodes + " anims=" + i.animationNames + " " + i.animationDurations
                        + String.format(" dims=%.3f x %.3f x %.3f", i.sizeX, i.sizeY, i.sizeZ));
            }
            @Override public void onError(String code) { System.out.println("ERROR " + code); Gdx.app.exit(); }
            @Override public void onAnimation(int index, float time, float duration, boolean playing) {}
        });
        ApplicationListener wrap = new ApplicationListener() {
            int frame, step;
            final Object[][] steps = {
                    {"mesh", (Runnable) () -> { core.setMode(ModelViewerCore.MODE_MESH); core.setWireframe(false); }},
                    {"bones", (Runnable) () -> core.setMode(ModelViewerCore.MODE_BONES)},
                    {"both", (Runnable) () -> core.setMode(ModelViewerCore.MODE_BOTH)},
                    {"wire", (Runnable) () -> { core.setMode(ModelViewerCore.MODE_BOTH); core.setWireframe(true); }},
                    {"shadows", (Runnable) () -> { core.setMode(ModelViewerCore.MODE_MESH); core.setWireframe(false); core.setShadows(true); }},
                    {"seek", (Runnable) () -> { core.setMode(ModelViewerCore.MODE_BOTH); core.setPlaying(false); core.seek(0.75f); }},
            };
            @Override public void create() { core.create(); }
            @Override public void resize(int w, int h) { core.resize(w, h); }
            @Override public void render() {
                core.render();
                if (!loaded[0]) return;
                frame++;
                if (frame == 5) ((Runnable) steps[step][1]).run();
                if (frame == 25) {
                    Pixmap p = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                    // framebuffer rows are bottom-up
                    Pixmap f = new Pixmap(p.getWidth(), p.getHeight(), p.getFormat());
                    for (int y = 0; y < p.getHeight(); y++) f.drawPixmap(p, 0, y, p.getWidth(), 1, 0, p.getHeight() - 1 - y, p.getWidth(), 1);
                    File png = new File(out, base + "_" + steps[step][0] + ".png");
                    PixmapIO.writePNG(Gdx.files.absolute(png.getAbsolutePath()), f);
                    p.dispose();
                    f.dispose();
                    System.out.println("SHOT " + png);
                    step++;
                    frame = 0;
                    if (step >= steps.length) Gdx.app.exit();
                }
            }
            @Override public void pause() {}
            @Override public void resume() {}
            @Override public void dispose() { core.dispose(); }
        };
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setWindowedMode(800, 600);
        c.setBackBufferConfig(8, 8, 8, 8, 24, 0, 0);
        c.setTitle("SUDA3D viewer harness");
        new Lwjgl3Application(wrap, c);
    }
}
