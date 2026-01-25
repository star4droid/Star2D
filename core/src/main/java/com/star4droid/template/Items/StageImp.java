package com.star4droid.template.Items;

import box2dLight.Light;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
//import com.star4droid.star2d.evo.star2dApp;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.ContactFilter;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.utils.Array;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.template.LoadingStage;
import com.kotcrab.vis.ui.util.ToastManager;
import com.star4droid.template.Utils.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import com.star4droid.template.SceneLoader;
import java.util.HashMap;
import com.star4droid.star2d.Helpers.Pair;
import box2dLight.RayHandler;

public class StageImp extends ApplicationAdapter {
	public static float WORLD_SCALE = 0.25f;
	public World world = new World(new Vector2(0, -9.8f), true);
	public boolean playing = true, debugBox2d = false;
	public Stage UiStage, GameStage;
	int steps = 6, backgroundColor = 0xFFFFFF;
	public Box2DDebugRenderer debugRenderer;
	SceneScript sceneScript;
	ToastManager toastManager;
	Color backgroundColorGdx = Color.WHITE;
	public Project project;
	public PropertySet<String, Object> propertySet;
	public java.util.LinkedHashSet<StageImp> previousStages = new java.util.LinkedHashSet<>();
	HashMap<String, Object> collisionMap = new HashMap<>();
	// Image background;
	StagePair stagePair;// useless...
	public ProjectAssetLoader assetLoader;
	LoadingStage loadingStage;
	public Preferences preferences;
	PlayerItem followX, followY, followRotation;
	float[] cameraOffset = new float[] { 0, 0 };
	FPSCalc fPSCalc = new FPSCalc();
	StageImp currentStage;

	private RayHandler rayHandler;
	Viewport viewport;
	boolean loadComplete = false, onCreateCalled = false;
	public SpriteSheetLoader spriteSheetLoader;
	public InputMultiplexer multiplexer;
	CameraItem cameraItem;
	private PropertySet<String, BitmapFont> bitmapFonts = new PropertySet<>();
	ArrayList<LightInfo> lights = new ArrayList<>();
	HashMap<String, Joint> joints = new HashMap<>();
	int viewportWidth = -1, viewportHeight = -1;

	public StageImp() {
		viewport = new FitViewport(720, 1560);
		fixCamera();
	}

	public StageImp(Viewport port) {
		viewport = port;
		fixCamera();
	}

	public StageImp(ProjectAssetLoader loader) {
		this(new FitViewport(720, 1560), loader);
		fixCamera();
	}

	public StageImp(Viewport port, ProjectAssetLoader projectAssetLoader) {
		super();
		assetLoader = projectAssetLoader;
		viewport = port;
		fixCamera();
	}

	private void fixCamera() {
		try {
			OrthographicCamera cam = (OrthographicCamera) getCamera();
			cam.zoom = WORLD_SCALE;
			cam.position.set(
					cam.viewportWidth * cam.zoom / 2f,
					cam.viewportHeight * cam.zoom / 2f,
					0);
		} catch (Exception ex) {
		}
	}

	boolean needsUpdate = true;

	public void init(Viewport viewport) {
		System.out.println("init called..");
		if (propertySet == null || Gdx.app == null || Gdx.files == null) {
			Gdx.files.external("init.null.error.txt")
					.writeString(String.format("property: %1$s, app : %2$s, files : %3$s\n\n", propertySet != null,
							Gdx.app != null, Gdx.files != null), true);
			return;
		}
		boolean landscape = propertySet.getString("or").equals("") || propertySet.getString("or").equals("landscape");
		float height = propertySet == null ? 0 : propertySet.getFloat("logicHeight"),
				width = propertySet == null ? 0 : propertySet.getFloat("logicWidth");
		if (height == 0)
			height = 1560;
		if (width == 0)
			width = 720;
		if (landscape) {
			// standard logic: properties are often portrait (width < height). 
			// If landscape, we want width > height.
			// So if logicWidth < logicHeight, swap them.
			if(width < height) {
    			float temp = height;
    			height = width;
    			width = temp;
			}
		} else {
		    // if portrait, ensure width < height
		    if(width > height) {
		        float temp = height;
    			height = width;
    			width = temp;
		    }
		}
		float ratio = width / height;
		// viewport = new FitViewport(10,10/ratio);
		/* if(true || viewport==null || needsUpdate) */
		viewport = new FitViewport(width, height);
		// else viewport.setWorldSize(width,height);
		needsUpdate = (propertySet == null);
		preferences = Gdx.app.getPreferences("prefs");
		Gdx.input.setCatchKey(4, true);// back key
		UiStage = new Stage(new FitViewport(width, height));
		toastManager = new ToastManager(UiStage);
		GameStage = new Stage(viewport) {
			@Override
			public boolean keyDown(int key) {
				if (key == 4) {
					(currentStage == null ? StageImp.this : currentStage).finish();
					return true;
				}
				return false;
			}
		};

		loadingStage = new LoadingStage();
		stagePair = new StagePair(UiStage, GameStage);

		if (project == null)
			project = new Project(Gdx.files.getExternalStoragePath());
		initDone = true;
		if (spriteSheetLoader != null && assetLoader != null && !isMain()) {
			onCreate();
			if (sceneScript != null)
				sceneScript.onCreate();
			onCreateCalled = true;
		}

		rayHandler = new RayHandler(world);
		rayHandler.setAmbientLight(0.1f, 0.1f, 0.1f, 1f);
		setupLight();
		setupCollision();
		setupCollision();
		multiplexer = new InputMultiplexer();
		multiplexer.addProcessor(getInputProcessor());
		// multiplexer.addProcessor(UiStage);
		// multiplexer.addProcessor(GameStage);
		Gdx.input.setInputProcessor(multiplexer);

		if (assetLoader == null) {
			assetLoader = new ProjectAssetLoader(project);
			if (spriteSheetLoader == null) {
				spriteSheetLoader = new SpriteSheetLoader(assetLoader, project, (errorHappend, message) -> {
					loadComplete = true;
				});
			}
		} else if (spriteSheetLoader == null) {
			spriteSheetLoader = new SpriteSheetLoader(assetLoader, project, (errorHappend, message) -> {
				loadComplete = true;
			});
		} else {
			loadComplete = true;
			onCreateCalled = true;
			if (assetLoader.isFinished()) {
				onCreate();
				if (sceneScript != null)
					sceneScript.onCreate();
			} else {
				onCreateCalled = false;
				loadComplete = false;
			}
		}
		assetLoader.setAssetsLoadListener(() -> {
			spriteSheetLoader.start();
		});
		this.viewport = viewport;
		updateViewport();
		fixCamera();
	}

	public StageImp updateViewport() {
		if (viewportWidth != -1 && getGameStage() != null && getUiStage() != null) {
			getGameStage().getViewport().update(viewportWidth, viewportHeight);
			getUiStage().getViewport().update(viewportWidth, viewportHeight);
		}
		return this;
	}

	public StageImp updateViewport(int width, int height) {
		viewportWidth = width;
		viewportHeight = height;
		updateViewport();
		return this;
	}

	public StageImp setScript(SceneScript sceneScript) {
		this.sceneScript = sceneScript;
		return this;
	}

	public BitmapFont getFont(String path) {
		while (path.contains("//")) {
			path = path.replace("//", "/");
		}
		if (!path.startsWith("/"))
			path = "/" + path;
		if (bitmapFonts.containsKey(path))
			return (BitmapFont) bitmapFonts.get(path);
		BitmapFont font = com.star4droid.template.Utils.Utils.generateFontFrom(Gdx.files.absolute(path));
		bitmapFonts.put(path, font);
		return font;
	}

	public RayHandler getRayHandler() {
		return rayHandler;
	}

	public void addJoint(String name, Joint joint) {
		joints.put(name, joint);
	}

	public Joint getJoint(String name) {
		return joints.get(name);
	}

	ArrayList<Actor> actors = null;

	public void updateActors() {
		actors = null;
		orderBodies();
	}

	public void setCamera(PlayerItem item) {
		if (item instanceof CameraItem)
			this.cameraItem = (CameraItem) item;
	}

	public void setCamera(CameraItem camera) {
		this.cameraItem = camera;
		setCameraXY(camera);
		cameraFollowX(camera);
		cameraFollowY(camera);
		setZoom(1f / (camera.getCameraDef().Zoom * WORLD_SCALE));
	}

	com.badlogic.gdx.utils.Array<Actor> orderedActors = new com.badlogic.gdx.utils.Array<>();

	public com.badlogic.gdx.utils.Array<Actor> orderBodies() {
		orderedActors.clear();
		if (GameStage != null)
			orderedActors.addAll(GameStage.getActors());
		if (UiStage != null)
			orderedActors.addAll(UiStage.getActors());

		orderedActors.sort(new Comparator<Actor>() {
			@Override
			public int compare(Actor a1, Actor a2) {
				int z1 = getZ(a1);
				int z2 = getZ(a2);
				return Integer.compare(z1, z2);
			}

			private int getZ(Actor actor) {
				if (actor instanceof PlayerItem) {
					try {
						com.star4droid.star2d.ElementDefs.ItemDef def = ((PlayerItem) actor).getProperties();
						if (def != null)
							return def.getZ();
					} catch (Exception e) {
					}
				}
				return actor.getZIndex();
			}
		});
		return orderedActors;
	}

	public void addLight(String name, Light light) {
		lights.add(new LightInfo(name, light));
	}

	public Light findLight(String name) {
		for (LightInfo lightInfo : lights)
			if (lightInfo.name.equals(name))
				return lightInfo.light;
		return null;
	}

	public void toast(String message, int duration) {
		toastManager.toFront();
		toastManager.show(message, duration);
	}

	public void toast(String message) {
		toast(message, 2);
	}

	public Project getProject() {
		return project;
	}

	public void drawDebug() {
		try {
			debugRenderer.render(world, GameStage.getCamera().combined);
		} catch (Exception ex) {
		}
	}

	public int toInt(String string, int onError) {
		try {
			return Utils.getInt(string);
		} catch (Exception ex) {
			return onError;
		}
	}

	public float toFloat(String string, float onError) {
		try {
			return Utils.getFloat(string);
		} catch (Exception ex) {
			return onError;
		}
	}

	public int toInt(String string) {
		return toInt(string, 0);
	}

	public float toFloat(String string) {
		return toFloat(string, 0);
	}

	public int getRealNumber(String key) {
		return toInt(getValue(key));
	}

	public int getNumber(String key) {
		return toInt(getValue(key));
	}

	public StageImp setSpriteSheetLoader(SpriteSheetLoader spriteSheetLoader) {
		this.spriteSheetLoader = spriteSheetLoader;
		return this;
	}

	public SpriteSheetLoader getSpriteSheetLoader() {
		return spriteSheetLoader;
	}

	private boolean initDone = false;

	public boolean initComplete() {
		return initDone;
	}

	public PlayerItem findItem(String name) {
		// String names="";
		try {
			for (Actor actor : GameStage.getActors()) {
				if (actor == null)
					continue;
				String actorName = (actor.getName() == null) ? "" : actor.getName();
				// names+=", "+actorName;
				if (actorName.equals(name)) {
					if (actor instanceof PlayerItem)
						return (PlayerItem) actor;
				}
			}
			for (Actor actor : UiStage.getActors()) {
				// if(actor!=null&&(actor instanceof
				// PlayerItem)&&actor.getName()!=null&&actor.getName().equals(name))
				// return (PlayerItem)actor;
				if (actor == null)
					continue;
				String actorName = (actor.getName() == null) ? "" : actor.getName();
				// names+=", "+actorName;
				if (actorName.equals(name)) {
					if (actor instanceof PlayerItem)
						return (PlayerItem) actor;
				}
			}
		} catch (Exception ex) {
			// throw new RuntimeException(ex);
		}
		// throw new RuntimeException("All :\n"+names);
		return null;
	}

	@Override
	public final void create() {
		super.create();
		System.out.println("oncreate called!");
		debugRenderer = new Box2DDebugRenderer();
		// try {
		init(viewport);
		System.out.println("stages after init : " + (UiStage != null) + "," + (GameStage != null));
		// } catch(Exception ex){
		// throw new RuntimeException("error : " + ex.toString()+"\n full:\n"
		// +Utils.getStackTraceString(ex));
		// }
	}

	@Override
	public void resize(int width, int height) {
		getGameStage().getViewport().update(width, height);
		getUiStage().getViewport().update(width, height);
		viewportWidth = width;
		viewportHeight = height;
		if (currentStage != null) {
			currentStage.getGameStage().getViewport().update(width, height);
			currentStage.getUiStage().getViewport().update(width, height);
		}
	}

	public void onCreate() {

	}

	public void setupLight() {
		if (!(rayHandler == null || propertySet == null)) {
			rayHandler.setBlur(propertySet.getString("Enable Blur").toString().equals("true"));
			rayHandler.setBlurNum(propertySet.getInt("Blur Number"));
			rayHandler.setCulling(propertySet.getString("Enable Culling").toString().equals("true"));
			RayHandler.setGammaCorrection(propertySet.getString("Gamma Correction").toString().equals("true"));
			rayHandler.setShadows(propertySet.getString("Enable Shadows").toString().equals("true"));
			RayHandler.useDiffuseLight(propertySet.getString("Use Diffuse Light").toString().equals("true"));
			try {
				if (!propertySet.getString("Ambient Light").equals(""))
					rayHandler.setAmbientLight(Color.valueOf(propertySet.getString("Ambient Light")));
			} catch (Exception ex) {
			}
		}
	}

	@Override
	public final void resume() {
		super.resume();
		if (!loadComplete)
			return;
		if (currentStage == null) {
			onResume();
			if (sceneScript != null)
				sceneScript.onResume();
		} else
			currentStage.onResume();
	}

	@Override
	public final void pause() {
		super.pause();
		if (!loadComplete)
			return;
		if (currentStage == null) {
			onPause();
			if (sceneScript != null)
				sceneScript.onPause();
		} else
			currentStage.onPause();
	}

	public void Pause() {
		playing = false;
	}

	public void Resume() {
		playing = true;
	}

	// idk why this ... :)
	public boolean onCreateCalled() {
		return onCreateCalled;
	}

	private int finishTime = 0;

	@Override
	public final void render() {
		super.render();
		if (assetLoader != null)
			assetLoader.update();
		if (needsUpdate)
			return;
		if (preferences == null)
			preferences = Gdx.app.getPreferences("prefs");
		// PlayerItem item= findItem("Box1");
		// if(item!=null){
		// GameStage.getCamera().position.x = item.getActorX();
		// GameStage.getCamera().position.y = item.getActorY();
		// }
		fPSCalc.update();
		Color bg = (currentStage == null) ? backgroundColorGdx : currentStage.getBackgroundColor();
		Gdx.gl.glClearColor(bg.r, bg.g, bg.b, bg.a);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		if (loadComplete && (!onCreateCalled)) {
			onCreate();
			if (sceneScript != null)
				sceneScript.onCreate();
			onCreateCalled = true;
		}

		if (!loadComplete) {
			if (loadingStage != null) {
				loadingStage.setProgress(assetLoader.getProgress() * 75);
				loadingStage.act();
				loadingStage.draw();
			}
		} else if (currentStage == null) {
			if (!finished) {
				act();
				draw();
			} else {
				finishTime++;
				if (finishTime > 10)
					Gdx.app.exit();
			}
		} else {
			if (!currentStage.initComplete())
				currentStage.init(null);
			currentStage.act();
			currentStage.draw();
			// if(!currentStage.draw())
			// throw new RuntimeException("game not drawn for unknown reason...");
		}

	}

	public void onDraw() {

	}

	public StagePair getPair() {
		return stagePair;
	}

	public Color getBackgroundColor() {
		return backgroundColorGdx;
	}

	public StageImp setBackgroundColor(String hex) {
		backgroundColorGdx = Color.valueOf(hex);
		backgroundColor = Color.argb8888(backgroundColorGdx.a, backgroundColorGdx.r, backgroundColorGdx.g,
				backgroundColorGdx.b);
		return this;
	}

	public StageImp setBackgroundColor(int color) {
		// background.setColor(new Color(color));
		backgroundColor = color;
		backgroundColorGdx = new Color(color);
		return this;
	}

	public StageImp setProject(Project p) {
		project = p;
		Gdx.files.external("project.txt").writeString(project.getPath(), false);
		if (assetLoader == null && p != null)
			assetLoader = new ProjectAssetLoader(p);
		else if (p != null && assetLoader != null)
			assetLoader.setProject(p);
		return this;
	}

	public float getWidth() {
		return GameStage.getWidth();
	}

	public float getHeight() {
		return GameStage.getHeight();
	}

	public World getWorld() {
		return world;
	}

	public StageImp setSteps(int x) {
		steps = Math.max(x, 1);
		return this;
	}

	public void cameraFollowX(PlayerItem playerItem) {
		followX = playerItem;
		cameraItem = null;
	}

	public void cameraFollowRotation(PlayerItem playerItem) {
		followRotation = playerItem;
		cameraItem = null;
	}

	public void cameraFollowY(PlayerItem playerItem) {
		followY = playerItem;
		cameraItem = null;
	}

	public void openUrl(String url) {
		Gdx.net.openURI(url);
	}

	public boolean finished = false;

	public void finish() {
		if (finished) {
			debug("already finished\n" + System.currentTimeMillis() + ", main finished : " + StageImp.this.finished
					+ ", size : " + previousStages.size() + "\n");
			return;
		}
		if (finishFunc != null) {
			finishFunc.onFinish(this);
			finished = true;
			GameStage.dispose();
			UiStage.dispose();
			return;
		}
		if (currentStage == null || currentStage.equals(this))
			Gdx.app.exit();
		else {
			if (previousStages.contains(this))
				previousStages.remove(this);
			finished = true;
			// GameStage.dispose();
			// UiStage.dispose();
		}
	}

	public boolean setImage(PlayerItem playerItem, String image) {
		String imgPath = (project.getImagesPath() + image).replace("//", "/");
		// if(!assetLoader.isFinished())
		// assetLoader.finishLoading();
		if (assetLoader.isLoaded(imgPath)) {
			playerItem.setImage(assetLoader.get(imgPath, Texture.class));
			return true;
		}
		return false;
	}

	public ProjectAssetLoader getAssets() {
		return assetLoader;
	}

	public StageImp setAssetsLoader(ProjectAssetLoader loader) {
		assetLoader = loader;
		return this;
	}

	public void setGravity(float x, float y) {
		world.setGravity(new Vector2(x, y));
	}

	public Stage getGameStage() {
		return GameStage;
	}

	public Stage getUiStage() {
		return GameStage;
	}

	public StageImp setPropertySet(PropertySet<String, Object> set) {
		propertySet = set;
		Gdx.files.external("ps.txt").writeString("propertySet : " + propertySet.toString(), false);
		try {
			if (propertySet != null) {
				setBackgroundColor(propertySet.getString("sceneColor"));
				float height = propertySet.getInt("logicHeight"),
						width = propertySet.getInt("logicWidth");
				if (viewport != null)
					viewport.setWorldSize(width, height);
				// background.setSize(width,height);
				setupLight();
				if (needsUpdate && Gdx.app != null) {
					init(null);
				}
			}
		} catch (Exception e) {
			Gdx.files.external("setpr.err.txt").writeString("setPropertySet : " + propertySet.toString(), false);
		}
		return this;
	}

	public float toStageY(float worldY) {
		if (GameStage == null)
			return worldY;
		return GameStage.getViewport().getWorldHeight() - worldY;
	}

	public Viewport getViewport() {
		return (GameStage == null) ? null : GameStage.getViewport();
	}

	public Camera getCamera() {
		return GameStage.getCamera();
	}

	public boolean isPlaying() {
		return playing;
	}

	public boolean checkCollision(PlayerItem p1, PlayerItem p2) {
		return (collisionMap.containsKey(p1.getName() + "," + p2.getName())
				&& collisionMap.get(p1.getName() + "," + p2.getName()).equals("true"));
	}

	public float getDelta() {
		return Gdx.graphics.getDeltaTime();
	}

	private void setupCollision() {
		this.world.setContactFilter(new ContactFilter() {
			@Override
			public boolean shouldCollide(Fixture fixture1, Fixture fixture2) {
				try {
					PlayerItem body1 = (PlayerItem) fixture1.getUserData();
					PlayerItem body2 = (PlayerItem) fixture2.getUserData();
					boolean b1 = body1.getProperties().getCollision().contains(body2.getParentName());
					boolean b2 = body2.getProperties().getCollision().contains(body1.getParentName());
					return !(b1 || b2);
					/*
					 * debug(
					 * "\n1 : "+body1.getProperties().getString("Collision")
					 * + "\np name : "+ body1.getParentName()
					 * +"\n contains: " + b1
					 * + "\n2 : "+body2.getProperties().getString("Collision")
					 * + "\np2 name : "+ body2.getParentName()
					 * +"\n contains: " + b2
					 * +"\n------------------------------------------"
					 * ,true
					 * );
					 * return true;
					 */
				} catch (Exception ex) {
				}
				return false;
			}

		});
		this.world.setContactListener(new ContactListener() {
			@Override
			public void beginContact(Contact contact) {
				if (finished)
					return;
				PlayerItem body1 = null, body2 = null;
				try {
					body1 = (PlayerItem) contact.getFixtureA().getUserData();
					body2 = (PlayerItem) contact.getFixtureB().getUserData();
					collisionMap.put(body1.getName() + "," + body2.getName(), "true");
					collisionMap.put(body2.getName() + "," + body1.getName(), "true");
				} catch (Exception ex) {
					// Utils.showMessage(getContext(),ex.toString());
					// Utils.Log("star2dXXX",ex.toString());
				}
				if (body1 != null && body2 != null) {
					if (body1.getScript() != null)
						body1.getScript().collisionBegin(body2);
					else
						body1.getElementEvents().onCollisionBegin(body1, body2);

					if (body2.getScript() != null)
						body2.getScript().collisionBegin(body1);
					else
						body1.getElementEvents().onCollisionBegin(body2, body1);
					onCollisionBegin(body1, body2);
				}
			}

			@Override
			public void endContact(Contact contact) {
				PlayerItem body1 = null, body2 = null;
				try {
					body1 = (PlayerItem) contact.getFixtureA().getUserData();
					body2 = (PlayerItem) contact.getFixtureB().getUserData();
					collisionMap.remove(body1.getName() + "," + body2.getName());
					collisionMap.remove(body2.getName() + "," + body1.getName());
				} catch (Exception ex) {
					// Utils.Log("star2dXXX",ex.toString());
				}
				if (body1 != null && body2 != null) {
					if (body1.getScript() != null)
						body1.getScript().collisionEnd(body2);
					else
						body1.getElementEvents().onCollisionEnd(body1, body2);

					if (body2.getScript() != null)
						body2.getScript().collisionEnd(body1);
					else
						body1.getElementEvents().onCollisionEnd(body2, body1);
					onCollisionEnd(body1, body2);
				}
			}

			@Override
			public void postSolve(Contact arg0, ContactImpulse arg1) {
			}

			@Override
			public void preSolve(Contact arg0, Manifold arg1) {
			}
		});
	}

	public void onCollisionBegin(PlayerItem body1, PlayerItem body2) {
	}

	public void onCollisionEnd(PlayerItem body1, PlayerItem body2) {
	}

	// Simplified loader logic delegating to SceneLoader implementation
	public static class StageLoaderParameters {
		public String dexPath;
		public SceneLoader loader;
	}

	public static StageImp loadScene(String scene, ProjectAssetLoader projectAssetLoader,
			SpriteSheetLoader spriteSheetLoader, StageLoaderParameters params) {
		try {
			if (params == null || params.loader == null)
				throw new RuntimeException("SceneLoader is required");
			return params.loader.load(scene, params).setAssetsLoader(projectAssetLoader)
					.setSpriteSheetLoader(spriteSheetLoader);
		} catch (Throwable e) {
			String err = "scene : " + scene + "\n error : " + e.toString() + "\n cause : " + e.getCause() + "\n"
					+ Utils.getStackTraceString(e);
			Gdx.files.external("load.scene.txt").writeString(err, false);
			throw new RuntimeException(err);
		}
	}

	private Music getMusic(String sound) {
		return assetLoader.get((project.get("sounds") + sound).replace("//", "/"), Music.class);
	}

	public void loopSound(String sound, boolean loop) {
		getMusic(sound).setLooping(loop);
	}

	public void releaseSound(String sound) {
		Music music = getMusic(sound);
		if (music.isPlaying())
			music.stop();
	}

	public void pauseSound(String sound) {
		if (getMusic(sound).isPlaying())
			getMusic(sound).pause();
	}

	public void setAnimation(PlayerItem playerItem, String anim) {
		try {
			playerItem.setAnimation(spriteSheetLoader.getAnimation(anim));
		} catch (Exception ex) {
			// toast("animation : "+ anim + ", player : " + playerItem.getName() + ", error
			// : "+ex.toString());
		}
	}

	public void createSound(String sound, String id) {
	}

	public void startSound(String sound) {
		getMusic(sound).play();
	}

	public static SceneLoader mainLoader;

	public void openScene(String scene) {
		if (openSceneFunc != null) {
			openSceneFunc.openScene(scene, this);
			return;
		}
		if (currentStage != null && currentStage != this)
			return;

		StageLoaderParameters params = new StageLoaderParameters();
		params.dexPath = project.getPath();
		params.loader = mainLoader;
		if (mainLoader == null)
			throw new RuntimeException("StageImp.mainLoader is not set!");

		StageImp newStage = loadScene(scene, assetLoader, spriteSheetLoader, params).setPrev(this);
		if (newStage == null) {
			// TODO : show error when scene is null ...
			debug("scene is null!!\n" + System.currentTimeMillis() + " , scene not found!\nscene : " + scene + "\n",
					true);
			return;
		}

		newStage.setOpenSceneFunc((sc, stageImp) -> {
			// if the stage isn't the current, it means it's previous one or finished
			// stage...
			if (currentStage == stageImp) {
				previousStages.add(stageImp);
				currentStage = loadScene(sc, assetLoader, spriteSheetLoader, params)
						.setOpenSceneFunc(newStage.openSceneFunc).setFinishFunc(newStage.finishFunc).setPrev(stageImp)
						.updateViewport(viewportWidth, viewportHeight);
			}
		}).setFinishFunc((st) -> {
			if (currentStage != null && !st.equals(currentStage))
				return;
			StageImp temp = st;
			// check the previous stage opened before this
			// if there previous is finished then check the previous of it
			while (temp.prevStage != null) {
				temp = temp.prevStage;
				if (temp == null) {
					previousStages.clear();
					currentStage = null;
					// Gdx.app.exit();
					return;
				} else if (!temp.finished) {
					currentStage = temp;
					Gdx.input.setInputProcessor(currentStage.multiplexer);
					// debug("scene finished without any problem\n");
					return;
				} // if current != null && finished => check the previous stage
			}
			// debug("no scene opened, finish the game\n");
			currentStage = null;
			previousStages.clear();
			// Gdx.app.exit();
		});
		if (currentStage == null) {
			previousStages.add(this);
		} else {
			previousStages.add(currentStage);
		}
		currentStage = newStage;
		if (currentStage != null)
			Gdx.input.setInputProcessor(currentStage.multiplexer);
	}

	public void setZoom(float zoom) {
		((OrthographicCamera) getCamera()).zoom = 1 / zoom;
		if (cameraItem != null)
			cameraItem.getCameraDef().Zoom = 1f / zoom;
		/*
		 * for(Actor actor:GameStage.getActors()){
		 * if(actor==null) continue;
		 * String actorName=(actor.getName()==null)?"":actor.getName();
		 * //names+=", "+actorName;
		 * if(actor instanceof
		 * PlayerItem&&((PlayerItem)actor).getProperties()!=null&&((PlayerItem)actor).
		 * getProperties().containsKey("type")&&((PlayerItem)actor).getProperties().
		 * getString("type").equals("UI")){
		 * //keep the UI items in the same size....
		 * actor.setOrigin(actor.getWidth(),0);
		 * actor.setScale(1/zoom);
		 * //actor.setOrigin(actor.getWidth()*0.5f,actor.getHeight()*0.5f);
		 * }
		 * }
		 */
	}

	public Vector2 getTouch() {
		Vector2 touchPoint = new Vector2(Gdx.input.getX(), Gdx.input.getY());
		return GameStage.screenToStageCoordinates(touchPoint);
	}

	public void debug(String string) {
		debug(string, true);
	}

	public void debug(String string, boolean append) {
		Gdx.files.external("game-logs/log.txt").writeString(string, append);
	}

	public StageImp prevStage = null;

	public StageImp setPrev(StageImp previous) {
		this.prevStage = previous;
		return this;
	}

	public float getZooming() {
		return 1 / ((OrthographicCamera) getCamera()).zoom;
	}

	public static String read(String file) {
		return Gdx.files.absolute(file).readString();
	}

	public static void writeInternal(String file, String content) {
		Gdx.files.internal("internal/" + file).writeString(content, false);
	}

	public static void readInternal(String file, String content) {
		Gdx.files.internal("internal/" + file).readString();
	}

	public static void write(String file, String content) {
		Gdx.files.absolute(file).writeString(content, false);
	}

	public void setCameraX(float x) {
		getCamera().position.x = x;
		cameraItem = null;
	}

	public void setCameraXY(float x, float y) {
		getCamera().position.x = x;
		getCamera().position.y = y;
		cameraItem = null;
	}

	public void setCameraXY(PlayerItem playerItem) {
		getCamera().position.set(playerItem.getActorX() + playerItem.getActor().getWidth() * 0.5f,
				playerItem.getActorY() + playerItem.getActor().getHeight() * 0.5f, 0);
		cameraItem = null;
	}

	public void setCameraY(float y) {
		getCamera().position.y = y;
		cameraItem = null;
	}

	public void setCameraX(PlayerItem playerItem) {
		getCamera().position.x = playerItem.getActorX() + playerItem.getActor().getWidth() * 0.5f;
		cameraItem = null;
	}

	public void setCameraY(PlayerItem playerItem) {
		getCamera().position.y = playerItem.getActorY() + playerItem.getActor().getHeight() * 0.5f;
		cameraItem = null;
	}

	// boolean isDrawingUi=false;
	// public void ui(com.badlogic.gdx.graphics.g2d.Batch batch){
	// if(!isDrawingUi){
	// UiStage.getViewport().apply();
	// UiStage.getCamera().update();
	// batch.setProjectionMatrix(UiStage.getCamera().combined);
	// isDrawingUi = true;
	// }
	// }

	// public void body(com.badlogic.gdx.graphics.g2d.Batch batch){
	// if(isDrawingUi){
	// GameStage.getViewport().apply();
	// GameStage.getCamera().update();
	// batch.setProjectionMatrix(GameStage.getCamera().combined);
	// isDrawingUi = false;
	// }
	// }

	public boolean draw() {
		if (preferences == null)
			return false;
		if (followX != null && followY != null) {
			getCamera().position.set(followX.getActorX() + followX.getActor().getWidth() * 0.5f + cameraOffset[0],
					followY.getActorY() + followY.getActor().getHeight() * 0.5f + cameraOffset[1], 0);
		} else if (followX != null) {
			getCamera().position.x = followX.getActorX() + followX.getActor().getWidth() * 0.5f + cameraOffset[0];
		} else if (followY != null) {
			getCamera().position.y = followY.getActorY() + followY.getActor().getWidth() * 0.5f + cameraOffset[1];
		}
		if (followRotation != null) {
			((OrthographicCamera) getCamera()).rotate(followRotation.getActor().getRotation());
		}
		if (cameraItem != null)
			setZoom(cameraItem.getCameraDef().Zoom);
		// GameStage.draw();
		// UiStage.draw();
		// TODO : This can cause bad performance, need to fix..
		// suggestion : override setZIndex of the bodies...
		Stage current = null;
		GameStage.getCamera().update();
		UiStage.getCamera().update();

		com.badlogic.gdx.utils.Array<Actor> bodies = orderBodies();
		for (int i = 0; i < bodies.size; i++) {
			Actor actor = bodies.get(i);
			if (actor.getStage() == null || !actor.isVisible())
				continue;
			Stage st = actor.getStage();

			// Only switch batch if necessary
			if (current != st) {
				if (current != null && current.getBatch().isDrawing())
					current.getBatch().end();
				current = st;
				if (!current.getBatch().isDrawing()) {
					current.getBatch().begin();
					current.getBatch().setProjectionMatrix(current.getCamera().combined);
				}
			}

			actor.draw(current.getBatch(), 1);
		}

		if (current != null && current.getBatch().isDrawing())
			current.getBatch().end();
		if (debugBox2d)
			drawDebug();
		if (playing)
			for (int x = 0; x < steps; x++)
				world.step(getDelta(), 8, 3);
		rayHandler.setCombinedMatrix(GameStage.getViewport().getCamera().combined);
		rayHandler.updateAndRender();
		onDraw();
		if (sceneScript != null)
			sceneScript.onDraw();
		return true;
	}

	public void setCameraCenter(float x, float y) {
		getCamera().position.set(x, y, 0);
	}

	public void setValue(String name, String value) {
		preferences.putString(name, value).flush();
	}

	public String getValue(String name) {
		return preferences.getString(name, "");
	}

	public void setCameraCenterX(float x) {
		getCamera().position.x = x;
		cameraItem = null;
	}

	public void setCameraCenterY(float y) {
		getCamera().position.y = y;
		cameraItem = null;
	}

	public void setCameraOffset(float x, float y) {
		cameraOffset[0] = x;
		cameraOffset[1] = y;
	}

	public float getCameraX() {
		return getCamera().position.x;
	}

	public float getCameraY() {
		return getCamera().position.y;
	}

	public void act() {
		// if(finished) return;
		if (preferences == null) {
			preferences = Gdx.app.getPreferences("prefs");
			if (preferences == null)
				return;
		}
		UiStage.act();
		GameStage.act();
	}

	public void addActor(Actor actor) {
		boolean isUi = false;
		if (actor instanceof PlayerItem) {
			try {
				com.star4droid.star2d.ElementDefs.ItemDef props = ((PlayerItem) actor).getProperties();
				if (props != null && props.getType().equals("UI"))
					isUi = true;
			} catch (Exception e) {
			}
		}

		if (isUi)
			UiStage.addActor(actor);
		else
			GameStage.addActor(actor);
	}

	public void dispose() {
		try {
			com.kotcrab.vis.ui.VisUI.dispose();
			UiStage.dispose();
			GameStage.dispose();
		} catch (Exception e) {
		}
		assetLoader.dispose();
	}

	public void onPause() {

	}

	public void onResume() {

	}

	public boolean isMain() {
		return openSceneFunc == null;
	}

	FinishFunc finishFunc;
	OpenSceneFunc openSceneFunc;

	public StageImp setFinishFunc(FinishFunc func) {
		finishFunc = func;
		return this;
	}

	public StageImp setOpenSceneFunc(OpenSceneFunc func) {
		openSceneFunc = func;
		return this;
	}

	public interface FinishFunc {
		public void onFinish(StageImp stageImp);
	}

	public interface OpenSceneFunc {
		public void openScene(String scene, StageImp stageImp);
	}

	public class StagePair {
		public Stage UiStage, GameStage;

		public StagePair(Stage ui, Stage game) {
			UiStage = ui;
			GameStage = game;
		}
	}

	public final int random(int min, int max) {
		return new java.util.Random().nextInt(max - min + 1) + min;
	}

	public class LightInfo {
		Light light;
		String name;

		public LightInfo(String name, Light light) {
			this.light = light;
			this.name = name;
		}
	}

	// Input Handling
	public com.badlogic.gdx.InputProcessor getInputProcessor() {
		return new ZOrderedInputProcessor();
	}

	private class ZOrderedInputProcessor implements com.badlogic.gdx.InputProcessor {
		private com.badlogic.gdx.math.Vector2 tempCoords = new com.badlogic.gdx.math.Vector2();

		private Actor getHitActor(Stage stage, int screenX, int screenY) {
			// Use a fresh vector to avoid sharing issues if any
			com.badlogic.gdx.math.Vector2 v = new com.badlogic.gdx.math.Vector2(screenX, screenY);
			stage.screenToStageCoordinates(v);
			return stage.hit(v.x, v.y, true);
		}

		private int getEffectiveZ(Actor actor) {
			if (actor == null)
				return Integer.MIN_VALUE;
			Actor current = actor;
			while (current != null) {
				if (current instanceof PlayerItem) {
					try {
						com.star4droid.star2d.ElementDefs.ItemDef props = ((PlayerItem) current).getProperties();
						if (props != null)
							return props.getZ();
					} catch (Exception e) {
					}
				}
				current = current.getParent();
			}
			return actor.getZIndex();
		}

		private Stage getTargetStage(int screenX, int screenY) {
			Actor uiActor = getHitActor(UiStage, screenX, screenY);
			Actor gameActor = getHitActor(GameStage, screenX, screenY);

			// If nothing hit, default to UI then Game (standard order)
			// But if checking for empty space click, we usually want GameStage to handle
			// camera etc.
			if (uiActor == null && gameActor == null)
				return UiStage;

			if (uiActor == null)
				return GameStage;
			if (gameActor == null)
				return UiStage;

			int uiZ = getEffectiveZ(uiActor);
			int gameZ = getEffectiveZ(gameActor);

			return (uiZ >= gameZ) ? UiStage : GameStage;
		}

		@Override
		public boolean keyDown(int keycode) {
			if (keycode == 4) { // Back Key
				// GameStage has the override for Back key, so prioritize it or ensure it gets
				// it
				if (GameStage.keyDown(keycode))
					return true;
				return UiStage.keyDown(keycode);
			}
			if (UiStage.keyDown(keycode))
				return true;
			return GameStage.keyDown(keycode);
		}

		@Override
		public boolean keyUp(int keycode) {
			if (UiStage.keyUp(keycode))
				return true;
			return GameStage.keyUp(keycode);
		}

		@Override
		public boolean keyTyped(char character) {
			if (UiStage.keyTyped(character))
				return true;
			return GameStage.keyTyped(character);
		}

		@Override
		public boolean touchDown(int screenX, int screenY, int pointer, int button) {
			Stage target = getTargetStage(screenX, screenY);
			// First try the target stage
			if (target.touchDown(screenX, screenY, pointer, button))
				return true;

			// If not handled, try the other stage (fallback)
			Stage other = (target == UiStage) ? GameStage : UiStage;
			return other.touchDown(screenX, screenY, pointer, button);
		}

		@Override
		public boolean touchUp(int screenX, int screenY, int pointer, int button) {
			// Dispatch to both to ensure release events are caught by whoever has focus
			boolean ui = UiStage.touchUp(screenX, screenY, pointer, button);
			boolean game = GameStage.touchUp(screenX, screenY, pointer, button);
			return ui || game;
		}

		@Override
		public boolean touchDragged(int screenX, int screenY, int pointer) {
			// Dispatch to both; Stage handles focus internally
			boolean ui = UiStage.touchDragged(screenX, screenY, pointer);
			boolean game = GameStage.touchDragged(screenX, screenY, pointer);
			return ui || game;
		}

		@Override
		public boolean mouseMoved(int screenX, int screenY) {
			boolean ui = UiStage.mouseMoved(screenX, screenY);
			boolean game = GameStage.mouseMoved(screenX, screenY);
			return ui || game;
		}

		@Override
		public boolean scrolled(float amountX, float amountY) {
			if (UiStage.scrolled(amountX, amountY))
				return true;
			return GameStage.scrolled(amountX, amountY);
		}

		@Override
		public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
			boolean ui = UiStage.touchCancelled(screenX, screenY, pointer, button);
			boolean game = GameStage.touchCancelled(screenX, screenY, pointer, button);
			return ui || game;
		}
	}
}