package com.star4droid.desktop.terrain;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.utils.ScreenUtils;
import com.star4droid.star2d.ElementDefs.Object3DDef;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.template.d3.Assets3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.Terrain3D;
import com.star4droid.template.d3.TerrainData;
import com.star4droid.template.d3.TerrainGen;
import com.star4droid.template.d3.TerrainStore;
import com.star4droid.template.d3.World3D;

import java.io.File;
import java.lang.reflect.Field;

/**
 * Desktop check of painted terrains: generate -> save -> load, splat + foliage + grass shaders, heightfield collisions
 * (balls dropped on the ground and on trees), draw calls / frame time, screenshots.
 * Usage: TerrainHarness <out dir> [biome] [shape]
 */
public class TerrainHarness {
	public static void main(String[] args) {
		final File out = new File(args.length > 0 ? args[0] : "terrain-shots");
		out.mkdirs();
		final String biome = args.length > 1 ? args[1] : "MEADOW", shape = args.length > 2 ? args[2] : "HILLS";
		ApplicationListener app = new ApplicationListener() {
			World3D w;
			Object3D terrain;
			Object3D[] balls = new Object3D[6];
			PerspectiveCamera cam;
			GLProfiler prof;
			int frame;
			long t0;
			int step;
			float[][] views = {{0, 60, 95, 0, 0, 0}, {3, 1.8f, 14, 0, 0.8f, 0}, {-30, 6, -10, 0, 0, 30}};

			@Override
			public void create() {
				Bullet.init();
				prof = new GLProfiler(Gdx.graphics);
				prof.enable();
				File proj = new File(out, "proj");
				proj.mkdirs();
				Assets3D.projectPath = proj.getAbsolutePath();
				TerrainGen.Settings s = new TerrainGen.Settings();
				s.width = 150;
				s.length = 150;
				s.height = 14;
				s.biome = biome;
				s.shape = shape;
				s.trees = 350;
				s.bushes = 200;
				s.rocks = 90;
				s.flowers = 400;
				s.grass = 2.5f;
				s.waterLevel = 0.15f;
				long g0 = System.nanoTime();
				TerrainGen.Result r = TerrainGen.generate(s);
				System.out.println("GEN ms=" + (System.nanoTime() - g0) / 1000000 + " inst=" + r.data.inst.size + " protos=" + r.data.protos.size + " res=" + r.data.res + " splat=" + r.data.splatRes);
				TerrainStore.put("/test.s2t", r.data);
				System.out.println("SAVE " + TerrainStore.save("/test.s2t") + " bytes=" + TerrainStore.fileOf("/test.s2t").length());
				TerrainStore.clear();
				TerrainData back = TerrainStore.get("/test.s2t");
				System.out.println("LOAD ok=" + (back != null) + " same=" + (back != null && back.inst.size == r.data.inst.size && back.h.length == r.data.h.length && back.h[777] == r.data.h[777]));
				w = new World3D(null);
				PropertySet<String, Object> cfg = new PropertySet<String, Object>();
				cfg.put("Shadows", "true");
				cfg.put("Shadow Distance", "45");
				cfg.put("Lighting Preset", "CUSTOM");
				cfg.put("Fog", "true");
				cfg.put("Fog Near", "60");
				cfg.put("Fog Far", "180");
				w.configure(cfg);
				Object3DDef d = new Object3DDef();
				d.name = "ground";
				d.Mesh = "TERRAIN";
				d.Terrain_Data = "/test.s2t";
				d.Body = "STATIC";
				d.Cast_Shadows = false;
				d.Static_Object = true;
				for (com.badlogic.gdx.utils.ObjectMap.Entry<String, String> e : r.props.entries()) set(d, e.key, e.value);
				terrain = Object3D.forWorld(w, d);
				// water
				Object3DDef wd = new Object3DDef();
				wd.name = "water";
				wd.Mesh = "WATER";
				wd.y = r.waterY;
				wd.Scale_X = 150;
				wd.Scale_Z = 150;
				wd.Cast_Shadows = false;
				Object3D.forWorld(w, wd);
				// balls: on open ground and right above trees
				int k = 0;
				for (TerrainData.Inst in : back.inst) {
					if (k >= 3) break;
					if (back.protos.get(in.p).src.contains("tree")) {
						Vector3 p = terrain.terrain().worldAt(terrain, in.u, in.v, new Vector3());
						balls[k++] = ball("treeBall" + k, p.x + 0.1f, p.y + 8, p.z);
					}
				}
				balls[3] = ball("open1", 0, 20, 0);
				balls[4] = ball("open2", 30, 25, -20);
				balls[5] = ball("open3", -40, 25, 35);
				cam = new PerspectiveCamera(60, 1280, 720);
				cam.near = 0.1f;
				cam.far = 600;
				w.editorCameraLocked = true;
				t0 = System.nanoTime();
			}

			Object3D ball(String n, float x, float y, float z) {
				Object3DDef b = new Object3DDef();
				b.name = n;
				b.Mesh = "SPHERE";
				b.x = x;
				b.y = y;
				b.z = z;
				b.Body = "DYNAMIC";
				b.Color = "#FF3030";
				b.Restitution = 0;
				b.Linear_Damping = 0.3f;
				return Object3D.forWorld(w, b);
			}

			void set(Object3DDef d, String key, String v) {
				try {
					Field f = Object3DDef.class.getField(key.replace(' ', '_'));
					if (f.getType() == float.class) f.setFloat(d, Float.parseFloat(v));
					else if (f.getType() == boolean.class) f.setBoolean(d, Boolean.parseBoolean(v));
					else f.set(d, v);
				} catch (Exception e) {
					System.out.println("prop? " + key);
				}
			}

			@Override
			public void resize(int width, int height) {
			}

			@Override
			public void render() {
				frame++;
				float dt = 1 / 60f;
				w.camera = cam;
				float[] v = views[Math.min(step, views.length - 1)];
				float gy = step == 0 ? 0 : terrain.terrain().heightAt(terrain, v[0], v[2]);
				cam.position.set(v[0], v[1] + gy, v[2]);
				com.star4droid.template.d3.Foliage3D.bendPos.set(v[3], terrain.terrain().heightAt(terrain, v[3], v[5]), v[5]);
				cam.viewportWidth = Gdx.graphics.getWidth();
				cam.viewportHeight = Gdx.graphics.getHeight();
				cam.lookAt(v[3], v[4] + (step == 0 ? 0 : terrain.terrain().heightAt(terrain, v[3], v[5])), v[5]);
				cam.up.set(0, 1, 0);
				cam.update();
				w.update(dt, true);
				prof.reset();
				long f0 = System.nanoTime();
				w.render(cam, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
				Gdx.gl.glFinish();
				long ft = System.nanoTime() - f0;
				if (frame % 60 == 0 || frame == 5)
					System.out.println("FRAME " + frame + " view=" + step + " drawCalls=" + prof.getDrawCalls() + " shaderSwitches=" + prof.getShaderSwitches() + " verts=" + (int) prof.getVertexCount().total + " ms=" + ft / 1000000.0);
				if (frame == 240) {
					Terrain3D t = terrain.terrain();
					for (Object3D b : balls) {
						if (b == null) continue;
						float gh = t.heightAt(terrain, b.position.x, b.position.z);
						System.out.printf("BALL %s pos=(%.2f, %.2f, %.2f) ground=%.2f gap=%.3f%n", b.getName(), b.position.x, b.position.y, b.position.z, gh, b.position.y - 0.5f - gh);
					}
				}
				if (frame % 120 == 100) {
					shot("view" + step);
					step++;
					if (step >= views.length) {
						System.out.println("TOTAL s=" + (System.nanoTime() - t0) / 1e9);
						Gdx.app.exit();
					}
				}
			}

			void shot(String name) {
				Pixmap p = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
				Pixmap f = new Pixmap(p.getWidth(), p.getHeight(), p.getFormat());
				for (int y = 0; y < p.getHeight(); y++) f.drawPixmap(p, 0, y, p.getWidth(), 1, 0, p.getHeight() - 1 - y, p.getWidth(), 1);
				File png = new File(out, biome.toLowerCase() + "_" + name + ".png");
				PixmapIO.writePNG(Gdx.files.absolute(png.getAbsolutePath()), f);
				p.dispose();
				f.dispose();
				System.out.println("SHOT " + png);
			}

			@Override
			public void pause() {
			}

			@Override
			public void resume() {
			}

			@Override
			public void dispose() {
			}
		};
		Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
		c.setWindowedMode(960, 540);
		c.setBackBufferConfig(8, 8, 8, 8, 24, 0, 0);
		c.setForegroundFPS(0);
		c.useVsync(false);
		c.setTitle("terrain harness");
		new Lwjgl3Application(app, c);
	}
}
