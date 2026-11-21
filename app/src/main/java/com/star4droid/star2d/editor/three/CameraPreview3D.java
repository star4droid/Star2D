package com.star4droid.star2d.editor.three;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Picture of a scene camera in a small window of the 3D editor (the camera button of the scene tools).
 * The window itself (frame, camera menu, drag, resize) is an Android view over the canvas: it only hands the GL side its
 * rectangle and the chosen camera, here, through volatile fields. Each frame the world is drawn once more from that camera
 * into a frame buffer the size of the window and the frame buffer is drawn into the rectangle, so moving the camera or
 * changing its properties shows at once. No camera, a deleted one, or a GPU without frame buffers: the window is black.
 */
public final class CameraPreview3D {
	/** Rectangle in GL pixels (origin bottom left); w or h 0 = hidden. */
	public static volatile int x, y, w, h;
	/** Name of the camera to show ("" = the Active camera, else the first one). */
	public static volatile String camera = "";

	private static FrameBuffer fbo;
	private static boolean failed;
	private static SpriteBatch batch;
	private static ShapeRenderer shapes;
	private static final Matrix4 proj = new Matrix4();
	private static final Quaternion q = new Quaternion();

	private CameraPreview3D() {}

	public static boolean visible() {
		return w > 8 && h > 8;
	}

	/** GL thread: the names of the cameras of the scene, the Active one first. */
	public static List<String> cameras(Editor3D e) {
		List<String> out = new ArrayList<>();
		if (e == null) return out;
		for (Actor a : e.editor.getActors()) {
			if (!(a instanceof Editor3DItem) || ((Editor3DItem) a).camera == null) continue;
			Camera3D c = ((Editor3DItem) a).camera;
			if (c.getDef() != null && c.getDef().Active) out.add(0, a.getName());
			else out.add(a.getName());
		}
		return out;
	}

	/** The camera to show: the chosen one, else the Active one, else the first one. */
	static Editor3DItem pick(Editor3D e) {
		Editor3DItem first = null, active = null;
		String want = camera;
		for (Actor a : e.editor.getActors()) {
			if (!(a instanceof Editor3DItem) || ((Editor3DItem) a).camera == null) continue;
			Editor3DItem it = (Editor3DItem) a;
			if (want != null && !want.isEmpty() && want.equals(it.getName())) return it;
			if (first == null) first = it;
			if (active == null && it.camera.getDef() != null && it.camera.getDef().Active) active = it;
		}
		return active != null ? active : first;
	}

	/** GL thread, after the editor picture: draws the window (or nothing when it is closed). */
	public static void render(Editor3D e) {
		if (!visible() || e == null) return;
		int rx = x, ry = y, rw = w, rh = h, sw = Gdx.graphics.getWidth(), sh = Gdx.graphics.getHeight();
		if (rw <= 8 || rh <= 8) return;
		boolean drawn = false;
		Editor3DItem it = pick(e);
		if (it != null && !failed) {
			try {
				drawn = draw(e, it, rw, rh);
			} catch (Throwable t) {
				Gdx.app.error("Star2D-3D", "camera preview: " + t);
				drawn = false;
			}
		}
		Gdx.gl.glViewport(0, 0, sw, sh);
		Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
		Gdx.gl.glDisable(GL20.GL_CULL_FACE);
		proj.setToOrtho2D(0, 0, sw, sh);
		if (drawn) {
			if (batch == null) batch = new SpriteBatch(4);
			batch.setProjectionMatrix(proj);
			batch.disableBlending();
			batch.begin();
			batch.setColor(1, 1, 1, 1);
			Texture t = fbo.getColorBufferTexture();
			// a frame buffer is upside down
			batch.draw(t, rx, ry, rw, rh, 0, 0, t.getWidth(), t.getHeight(), false, true);
			batch.end();
			batch.enableBlending();
		} else {
			if (shapes == null) shapes = new ShapeRenderer();
			shapes.setProjectionMatrix(proj);
			shapes.begin(ShapeRenderer.ShapeType.Filled);
			shapes.setColor(0, 0, 0, 1);
			shapes.rect(rx, ry, rw, rh);
			shapes.end();
		}
		Gdx.gl.glEnable(GL20.GL_BLEND);
	}

	private static boolean draw(Editor3D e, Editor3DItem it, int rw, int rh) {
		// a frame buffer the size of the window (at most 1024 on its long side)
		float k = Math.min(1f, 1024f / Math.max(rw, rh));
		int fw = Math.max(16, Math.round(rw * k)), fh = Math.max(16, Math.round(rh * k));
		if (fbo == null || fbo.getWidth() != fw || fbo.getHeight() != fh) {
			if (fbo != null) fbo.dispose();
			fbo = null;
			try {
				fbo = new FrameBuffer(Pixmap.Format.RGB888, fw, fh, true);
				fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
			} catch (Throwable t) {
				Gdx.app.error("Star2D-3D", "camera preview frame buffer: " + t);
				failed = true;
				fbo = null;
				return false;
			}
		}
		// the camera as the game sees it at start: its position, angles, field of view (or orthographic size)
		Camera3D c = it.camera;
		c.resize(fw, fh);
		c.cam.position.set(c.position);
		q.setEulerAngles(c.yaw, c.pitch, c.roll);
		c.cam.direction.set(0, 0, -1).mul(q);
		c.cam.up.set(0, 1, 0).mul(q);
		c.cam.update();
		// no selection outline in the camera picture
		Object3D sel = null;
		for (int i = 0; i < e.world.objects.size; i++) {
			Object3D o = e.world.objects.get(i);
			if (o.selected) { sel = o; o.selected = false; }
		}
		fbo.begin();
		try {
			e.world.render(c.cam, fw, fh, false);
		} finally {
			fbo.end();
			if (sel != null) sel.selected = true;
		}
		return true;
	}

	/** Context lost / editor closed. */
	public static void dispose() {
		w = 0;
		h = 0;
		if (fbo != null) fbo.dispose();
		fbo = null;
		failed = false;
		if (batch != null) batch.dispose();
		batch = null;
		if (shapes != null) shapes.dispose();
		shapes = null;
	}
}
