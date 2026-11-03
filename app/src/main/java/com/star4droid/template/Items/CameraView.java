package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.star4droid.star2d.ElementDefs.CameraViewDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

/**
 * Shows live what a camera sees (a 2D CAMERA element or a CAMERA3D element) inside a rectangle of the screen: mirrors,
 * security cameras, minimaps, picture in picture. With Touch on, touches on the view act on the filmed place.
 */
public class CameraView extends Actor implements PlayerItem {
	StageImp stage;
	CameraViewDef def;
	ElementEvent elementEvent;
	ChildsHolder childsHolder = new ChildsHolder(this);
	ItemScript itemScript;
	private FrameBuffer fbo;
	private TextureRegion region;
	private float refreshLeft;
	private final OrthographicCamera cam2d = new OrthographicCamera();
	private final PerspectiveCamera cam3d = new PerspectiveCamera();
	private Texture white;
	public float zoom = 1;
	public String cameraName = "";
	public boolean touch;

	public CameraView(StageImp s) {
		stage = s;
	}

	public static CameraView create(StageImp stage, CameraViewDef def, ElementEvent ev) {
		CameraView v = new CameraView(stage);
		v.def = def;
		v.elementEvent = ev;
		v.setup();
		return v;
	}

	private void setup() {
		setName(def.name);
		setBounds(def.x, def.y, def.width, def.height);
		setRotation(-def.rotation);
		setVisible(def.Visible);
		zoom = def.Zoom;
		cameraName = def.Camera == null ? "" : def.Camera;
		touch = def.Touch;
		addListener(new InputListener() {
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if (getScript() != null) getScript().touchStart(event);
				else if (elementEvent != null) elementEvent.onTouchStart(CameraView.this, event);
				if (touch) forward(x, y, pointer, true);
				return true;
			}

			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
				if (getScript() != null) getScript().touchEnd(event);
				else if (elementEvent != null) elementEvent.onTouchEnd(CameraView.this, event);
				if (touch) forward(x, y, pointer, false);
			}
		});
		if (stage != null) {
			stage.addActor(this);
			if (stage.onCreateCalled()) {
				if (elementEvent != null) elementEvent.onBodyCreated(this);
				if (getScript() != null) getScript().bodyCreated();
			}
		}
	}

	private Object3D pressed3d;
	private Actor pressed2d;

	/** A touch on the view goes to the filmed place. */
	private void forward(float x, float y, int pointer, boolean down) {
		PlayerItem src = source();
		float u = x / Math.max(1, getWidth()), v = y / Math.max(1, getHeight());
		if (src instanceof Camera3D && stage.hasWorld3D()) {
			World3D w = stage.world3d();
			prepare3d((Camera3D) src);
			// pick ray: Camera.unproject flips y with the SCREEN height (Gdx.graphics), not the viewport's, so pass
			// screenHeight - (pixels from the bottom of the view)
			Object3D o = w.pick(cam3d, u * cam3d.viewportWidth, Gdx.graphics.getHeight() - v * cam3d.viewportHeight, 0, 0, cam3d.viewportWidth, cam3d.viewportHeight, null);
			if (down) {
				pressed3d = o;
				if (o != null) w.touchStart(o, x, y, pointer);
			} else if (pressed3d != null) {
				w.touchEnd(pressed3d, x, y, pointer);
				if (o == pressed3d) w.click(pressed3d);
				pressed3d = null;
			}
		} else if (src instanceof CameraItem) {
			prepare2d((CameraItem) src);
			Vector3 p = new Vector3(u * cam2d.viewportWidth, (1 - v) * cam2d.viewportHeight, 0);
			cam2d.unproject(p, 0, 0, cam2d.viewportWidth, cam2d.viewportHeight);
			Stage gs = stage.getGameStage();
			Actor hit = gs.hit(p.x, p.y, true);
			if (hit == this) return;
			InputEvent e = new InputEvent();
			e.setStage(gs);
			e.setStageX(p.x);
			e.setStageY(p.y);
			e.setPointer(pointer);
			if (down) {
				pressed2d = hit;
				if (hit == null) return;
				e.setType(InputEvent.Type.touchDown);
				hit.fire(e);
			} else {
				Actor t = pressed2d != null ? pressed2d : hit;
				pressed2d = null;
				if (t == null) return;
				e.setType(InputEvent.Type.touchUp);
				t.fire(e);
			}
		}
	}

	/** The camera element shown ("" = the main camera). */
	public PlayerItem source() {
		if (stage == null) return null;
		if (cameraName != null && cameraName.length() > 0) {
			PlayerItem p = stage.findItem(cameraName);
			if (p != null) return p;
		}
		if (stage.hasWorld3D() && stage.world3d().activeCamera != null) return stage.world3d().activeCamera;
		return null;
	}

	private void prepare3d(Camera3D c) {
		int fw = fboW(), fh = fboH();
		cam3d.viewportWidth = fw;
		cam3d.viewportHeight = fh;
		cam3d.position.set(c.cam.position);
		cam3d.direction.set(c.cam.direction);
		cam3d.up.set(c.cam.up);
		cam3d.near = c.cam.near;
		cam3d.far = c.cam.far;
		cam3d.fieldOfView = c.persp.fieldOfView / Math.max(0.05f, zoom);
		cam3d.update();
	}

	private void prepare2d(CameraItem c) {
		Stage gs = stage.getGameStage();
		float ww = gs.getViewport().getWorldWidth(), wh = gs.getViewport().getWorldHeight();
		cam2d.setToOrtho(false, ww, wh);
		float z = StageImp.WORLD_SCALE * c.getCameraDef().Zoom / Math.max(0.05f, zoom);
		cam2d.zoom = z;
		// keep the view's aspect
		float aspect = getWidth() / Math.max(1, getHeight());
		cam2d.viewportWidth = wh * aspect;
		cam2d.position.set(c.getX(), c.getY(), 0);
		cam2d.update();
	}

	private int fboW() {
		return Math.max(16, (int) (getWidth() * Math.max(0.1f, Math.min(1f, def.Resolution)) * Gdx.graphics.getWidth() / Math.max(1f, stage.getUiStage().getViewport().getWorldWidth())));
	}

	private int fboH() {
		return Math.max(16, (int) (getHeight() * Math.max(0.1f, Math.min(1f, def.Resolution)) * Gdx.graphics.getHeight() / Math.max(1f, stage.getUiStage().getViewport().getWorldHeight())));
	}

	private void renderSource(Batch batch) {
		PlayerItem src = source();
		if (src == null) return;
		int fw = Math.min(2048, fboW()), fh = Math.min(2048, fboH());
		if (fbo == null || fbo.getWidth() != fw || fbo.getHeight() != fh) {
			if (fbo != null) fbo.dispose();
			fbo = new FrameBuffer(Pixmap.Format.RGBA8888, fw, fh, true);
			region = new TextureRegion(fbo.getColorBufferTexture());
			region.flip(false, true);
		}
		boolean drawing = batch.isDrawing();
		if (drawing) batch.end();
		StageImp.secondaryPass = true;
		try {
			fbo.begin();
			Color bg = def.Background_Color == null ? Color.BLACK : def.getColor(def.Background_Color);
			Gdx.gl.glClearColor(bg.r, bg.g, bg.b, 1);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
			if (src instanceof Camera3D && stage.hasWorld3D()) {
				prepare3d((Camera3D) src);
				stage.world3d().render(cam3d, fw, fh, false);
			} else if (src instanceof CameraItem) {
				prepare2d((CameraItem) src);
				Stage gs = stage.getGameStage();
				Batch b = gs.getBatch();
				b.setProjectionMatrix(cam2d.combined);
				b.begin();
				Array<Actor> list = stage.orderBodies();
				for (int i = 0; i < list.size; i++) {
					Actor a = list.get(i);
					if (a == this || a.getStage() != gs || !a.isVisible() || a instanceof CameraView) continue;
					a.draw(b, 1);
				}
				b.end();
			}
			fbo.end();
		} catch (Throwable t) {
			try {
				fbo.end();
			} catch (Throwable ignored) {
			}
		} finally {
			StageImp.secondaryPass = false;
		}
		// back to the stage's viewport
		if (getStage() != null) getStage().getViewport().apply();
		if (drawing) {
			if (getStage() != null) batch.setProjectionMatrix(getStage().getCamera().combined);
			batch.begin();
		}
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		if (!StageImp.secondaryPass) update();
		if (StageImp.secondaryPass) return;
		if (def.Active) {
			refreshLeft -= Gdx.graphics.getDeltaTime();
			if (def.Refresh_Rate <= 0 || refreshLeft <= 0 || region == null) {
				refreshLeft = def.Refresh_Rate <= 0 ? 0 : 1f / def.Refresh_Rate;
				renderSource(batch);
			}
		}
		Color old = batch.getColor().cpy();
		if (def.Border_Width > 0) {
			if (white == null) {
				Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
				pm.setColor(Color.WHITE);
				pm.fill();
				white = new Texture(pm);
				pm.dispose();
			}
			Color bc = def.getColor(def.Border_Color);
			batch.setColor(bc.r, bc.g, bc.b, bc.a * parentAlpha * def.Opacity);
			float bw = def.Border_Width;
			batch.draw(white, getX() - bw, getY() - bw, getOriginX() + bw, getOriginY() + bw, getWidth() + bw * 2, getHeight() + bw * 2, 1, 1, getRotation(), 0, 0, 1, 1, false, false);
		}
		batch.setColor(1, 1, 1, parentAlpha * def.Opacity);
		if (region != null)
			batch.draw(region, getX(), getY(), getOriginX(), getOriginY(), getWidth(), getHeight(), 1, 1, getRotation());
		batch.setColor(old);
	}

	// ------------------------------------------------------------------ API

	public void setCamera(String name) {
		cameraName = name == null ? "" : name;
	}

	public void setZoom(float z) {
		zoom = z;
	}

	public void setTouchEnabled(boolean on) {
		touch = on;
	}

	public void setActive(boolean on) {
		def.Active = on;
	}

	// ------------------------------------------------------------------ PlayerItem

	@Override
	public void update() {
		if (getScript() != null) getScript().bodyUpdate();
		else if (elementEvent != null) elementEvent.onBodyUpdate(this);
	}

	@Override
	public Body getBody() {
		return null;
	}

	@Override
	public ChildsHolder getChildsHolder() {
		if (childsHolder == null) childsHolder = new ChildsHolder(this);
		return childsHolder;
	}

	@Override
	public void setScript(ItemScript script) {
		itemScript = script;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends ItemScript> T getScript() {
		return (T) itemScript;
	}

	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties() {
		return def;
	}

	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}

	@Override
	public PlayerItem getClone(String newName) {
		return create(stage, def.getClone(newName), elementEvent);
	}

	@Override
	public boolean remove() {
		if (fbo != null) {
			fbo.dispose();
			fbo = null;
		}
		return super.remove();
	}
}
