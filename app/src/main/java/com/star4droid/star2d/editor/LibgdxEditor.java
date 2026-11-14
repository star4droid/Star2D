package com.star4droid.star2d.editor;

import box2dLight.RayHandler;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.ToastManager;
import com.star4droid.star2d.Helpers.Pair;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.items.*;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.editor.ui.FilePicker;
import com.star4droid.star2d.editor.ui.PointPicker;
import com.star4droid.star2d.editor.utils.CameraController;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.star2d.editor.utils.PropertiesHolder;
import com.star4droid.template.Utils.ProjectAssetLoader;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
/*
    The main editor which contains items and contains methods for various tasks
*/
public class LibgdxEditor extends Stage {
	public static LibgdxEditor currentEditor = null;
	ShapeRenderer shapeRenderer;
	ControlLayer controlLayer;
	World world = new World(new Vector2(0,-9.8f),true);
	Box2DDebugRenderer debugRenderer;
	EditorListener editorListener;
	Matrix4 rendererMatrix;
	PropertySet<String,Object> editorConfig;
	boolean AUTO_SAVE = false,LANDSCAPE = false,newUndoRedoSystem = true;
	Project project;
	public Color backgroundColor = Color.BLACK;
	String scene;
	PropertiesHolder propertiesHolder;
	CameraController cameraController;
	PointPicker pointPicker;
	Actor selectedActor;
	ProjectAssetLoader projectAssetLoader;
	Stage UiStage;
	RayHandler rayHandler;
	FilePicker filePicker;
	TOUCHMODE touch_mode=TOUCHMODE.GRID;
	Stack<String> undoList = new Stack<>(), redoList = new Stack<>();
	Stack<EditorAction> undoNStack = new Stack<>(),redoNStack = new Stack<>();
	ToastManager toastManager;
	boolean lockX = false,lockY = false;
	String[] startTransform;
	// the descendants of the selected element move / rotate with it: they and their start pose (for one undo step)
	private final ArrayList<Actor> dragKids = new ArrayList<>();
	private ArrayList<String[]> dragKidsStart = new ArrayList<>();
	/** Painting the cells of a NavGrid (path finding), off while its item is null. */
	public final NavPaint navPaint = new NavPaint(this);
	/** Drawing the points of a path (path variables), off while not active. */
	public final PathDraw pathDraw = new PathDraw(this);

	/** The canvas tool that takes the touches now (NavGrid painter, path drawing), or null. */
	public CanvasTool activeTool(){
		if(pathDraw.active()) return pathDraw;
		if(navPaint.active()) return navPaint;
		return null;
	}
	/** Where / when the last single touch started (a tap on a NavGrid badge selects it). */
	private float tapX, tapY;
	private long tapTime;
	float prevDistance = -1,startAngle=0,ratioScale =1,gridSizeX=50,logicalWidth=1600,logicalHeight=720,gridSizeY=50;
	
	// ------------------------------------------------------------------ 3D scenes
	/** 3D viewport of a 3D scene (null in 2D scenes). */
	com.star4droid.star2d.editor.three.Editor3D e3d;

	public com.star4droid.star2d.editor.three.Editor3D get3D(){
		return e3d;
	}

	public boolean is3DScene(){
		return editorConfig != null && "3D".equalsIgnoreCase(String.valueOf(editorConfig.get("sceneType")));
	}

	/** MOVE / SCALE / ROTATE / GRID (= camera only). */
	public String getTouchModeName(){
		return touch_mode == null ? "GRID" : touch_mode.name();
	}

	public boolean isLocked3D(){
		if(com.star4droid.star2d.remote.RemoteSession.isLocked()) return true;
		if(selectedActor instanceof EditorItem){
			PropertySet<String,Object> ps = ((EditorItem)selectedActor).getPropertySet();
			return ps != null && Hierarchy.isLocked(ps);
		}
		return false;
	}

	/** Tap on empty 3D space: nothing selected. */
	public void deselect3D(){
		selectedActor = null;
		if(controlLayer!=null) controlLayer.bodySelected();
		if(editorListener!=null) editorListener.onBodySelected();
		try { if(TestApp.nativeHooks != null) TestApp.nativeHooks.onEditorChanged(); } catch(Throwable ignored){}
	}

	/** After a gizmo edit: undo buttons and auto save. */
	public void afterEdit3D(){
		if(controlLayer!=null) controlLayer.updateUndoRedo();
		if(editorListener!=null) editorListener.onUpdateUndoRedo();
		if(AUTO_SAVE) project.save(this);
	}

	private void ensure3D(){
		if(is3DScene()){
			if(e3d==null) e3d = new com.star4droid.star2d.editor.three.Editor3D(this);
			e3d.configure(editorConfig);
		} else if(e3d!=null){
			// the scene became 2D (or another scene opened in this editor): drop the 3D viewport
			try { e3d.dispose(); } catch(Throwable ignored){}
			e3d = null;
		}
	}

	public LibgdxEditor(Project project){
		super();
		this.project = project;
		this.scene = "scene1";
		init();
	}
	
	public LibgdxEditor(Project project,String scene,ProjectAssetLoader assetLoader){
		super();
		this.project = project;
		this.projectAssetLoader = assetLoader;
		this.scene = scene;
		init();
	}
	
	// init the main editor items...
	private void init(){
	    // used to debug bounds of physics bodies..m
		debugRenderer = new Box2DDebugRenderer();
		// used to draw square bounds and many things...
		shapeRenderer = new ShapeRenderer();
		
		// used for lights...
		rayHandler = new RayHandler(world);
		setToCurrentEditor();
		
		//addActor(filePicker);
		// used by the joints dialog to pick point from the editor ..
		pointPicker = new PointPicker();
		rayHandler.setAmbientLight(0.1f, 0.1f, 0.1f, 1f);
		rendererMatrix = shapeRenderer.getProjectionMatrix().cpy();
		
		// used to animate the camera smoothly...
		cameraController = new CameraController((OrthographicCamera)getCamera());
		
		// the config which contains width/height and background color 
		updateConfig();
		setupLight();
		//((OrthographicCamera)getCamera()).zoom = 0.5f;
	}

	
	public ShapeRenderer getShapeRenderer(){
	    return shapeRenderer;
	}
	
	// file picker
	public void setFilePicker(FilePicker picker){
		this.filePicker = picker;
	}
	
	// contains the editor actions layout...
	public void setControlLayer(ControlLayer layer){
		this.controlLayer = layer;
	}
	
	public ControlLayer getControlLayer(){
		return this.controlLayer;
	}
	
	// UI Stage which is drawn on the top of the editor
	public void setUiStage(Stage stage){
		this.UiStage = stage;
	}
	

	
	// toast manager to show popup messages...
	public void setToastManager(ToastManager manager){
		this.toastManager = manager;
	}
	
	// show popup message for 2 seconds
	public void toast(String message){
		toast(message,2);
	}
	
	public void toast(String message,int duration){
		if(com.star4droid.star2d.ui.Snack.isError(message) && com.star4droid.star2d.ui.Snack.activity()!=null){
			com.star4droid.star2d.ui.Snack.show(message);
			return;
		}
	    if(toastManager==null) return;
		toastManager.toFront();
		toastManager.show(message,duration);
	}
	
	// useless 
	public PropertiesHolder getPropertiesHolder(){
		return propertiesHolder;
	}
	
	// get the filePicker, if the boolean is true, show it..
	public FilePicker getFilePicker(boolean show){
		
		if(filePicker==null) return null;
		if(filePicker.getStage()==null || filePicker.getStage() != UiStage)
			if(UiStage!=null)
				UiStage.addActor(filePicker);
					//else addActor(filePicker);
		if(show) {
			filePicker.setVisible(true);
			filePicker.toFront();
			//filePicker.setPosition(getWidth() - filePicker.getWidth()*0.5f,getHeight() - filePicker.getHeight()*0.5f);
		}
		return filePicker;
	}
	
	// set the light settings based on the config...
	public void setupLight(){
		if(!(rayHandler==null||editorConfig==null)){
			rayHandler.setBlur(editorConfig.getString("Enable Blur").toString().equals("true"));
			rayHandler.setBlurNum(editorConfig.getInt("Blur Number"));
			rayHandler.setCulling(editorConfig.getString("Enable Culling").toString().equals("true"));
			RayHandler.setGammaCorrection(editorConfig.getString("Gamma Correction").toString().equals("true"));
			rayHandler.setShadows(editorConfig.getString("Enable Shadows").toString().equals("true"));
			RayHandler.useDiffuseLight(editorConfig.getString("Use Diffuse Light").toString().equals("true"));
			try {
			    if(!editorConfig.getString("Ambient Light").equals(""))
			        rayHandler.setAmbientLight(Color.valueOf(editorConfig.getString("Ambient Light")));
			} catch(Exception ex){}
		}
	}
	
	// when the touch of the editor ends
	@Override
	public boolean touchUp(int arg0, int arg1, int arg2, int arg3) {
		if(e3d!=null && !e3d.edit2D && !loadingScene) return e3d.touchUp(arg0,arg1,arg2);
		if(loadingScene || com.star4droid.star2d.remote.RemoteSession.isLocked()) return true;
		CanvasTool tool = activeTool();
		if(tool != null){
			// painting a NavGrid / drawing a path: the finger uses the tool, two fingers move / zoom the view
			if(!tool.navigate() && arg2 == 0){
				Vector2 sp = screenToStageCoordinates(new Vector2(arg0,arg1));
				tool.up(sp.x,sp.y);
			}
			if(!Gdx.input.isTouched(1)) prevDistance = -1;
			return true;
		}
		if(arg2 == 0 && Math.abs(arg0 - tapX) < 14 && Math.abs(arg1 - tapY) < 14 && System.currentTimeMillis() - tapTime < 350){
			// a tap on the badge of a NavGrid selects it (its area lets the taps through to the elements below)
			Vector2 sp = screenToStageCoordinates(new Vector2(arg0,arg1));
			EditorNavGridItem nav = EditorNavGridItem.badgeAt(this,sp.x,sp.y);
			if(nav != null && nav != selectedActor){
				selectActor(nav);
				return true;
			}
		}
	    // save the end position if touch type is grid and push it to the events for undo redo.. 
		if(touch_mode!=TOUCHMODE.GRID && getSelectedActor()!=null){
			PropertySet<String,Object> ps = PropertySet.getPropertySet(selectedActor);
			String[] endTransform = new String[]{
				"x",ps.getString("x"),
				"y",ps.getString("y"),
				"rotation",ps.getString("rotation"),
				"radius",ps.getString("radius"),
				"width",ps.getString("width"),
				"height",ps.getString("height"),
				"Collider Radius",ps.getString("Collider Radius"),
				"Collider Width",ps.getString("Collider Width"),
				"Collider Height",ps.getString("Collider Height")
			};
			try {
				String msg = touch_mode == TOUCHMODE.ROTATE ? "rotation" : (touch_mode == TOUCHMODE.SCALE ? "scale" : "position");
				if(dragKids.isEmpty()){
			    	EditorAction.propertiesChanged(this,getSelectedActor().getName(),msg,endTransform,startTransform).updateEditorProperties().updateItemProperties();
				} else {
					// the group and everything inside it: one undo step
					EditorAction main = EditorAction.propertiesChangedQuiet(this,getSelectedActor().getName(),msg,endTransform,startTransform);
					if(main != null) main.updateEditorProperties().updateItemProperties();
					Hierarchy.pushMove(this,main,dragKidsStart,msg);
				}
			} catch(Exception ex){}
			dragKids.clear();
			dragKidsStart = new ArrayList<>();
			getSaveState();
			if(controlLayer!=null)
				controlLayer.updateUndoRedo();
			if(editorListener!=null)
				editorListener.onUpdateUndoRedo();
			if(AUTO_SAVE)
				project.save(LibgdxEditor.this);
		}
		return super.touchUp(arg0, arg1, arg2, arg3);
	}
	
	// when the editor touch started 
	@Override
	public boolean touchDown(int arg0, int arg1, int arg2, int arg3) {
		if(e3d!=null && !e3d.edit2D && !loadingScene) return e3d.touchDown(arg0,arg1,arg2);
		if(loadingScene || com.star4droid.star2d.remote.RemoteSession.isLocked()) return true; // locked: an AI is editing, the camera can still be moved
		if(arg2 == 0){
			tapX = arg0;
			tapY = arg1;
			tapTime = System.currentTimeMillis();
		}
		CanvasTool tool = activeTool();
		if(tool != null){
			if(tool.navigate()) return true;
			if(arg2 == 0 && !Gdx.input.isTouched(1)){
				Vector2 sp = screenToStageCoordinates(new Vector2(arg0,arg1));
				tool.down(sp.x,sp.y);
			} else tool.cancel();
			return true;
		}
		if(selectedActor == null){
		    ArrayList<String> bodies = getBodiesList();
		    if(bodies.size()>0)
			    selectByName(bodies.get(0));
		}
		if(selectedActor!=null && touch_mode == TOUCHMODE.ROTATE){
			float centerX = selectedActor.getX() + selectedActor.getWidth() / 2.0f,
					centerY = selectedActor.getY() + selectedActor.getHeight() / 2.0f;
			double angle = Math.atan2(Gdx.input.getY() - centerY, Gdx.input.getX() - centerX);
			startAngle = (float)Math.toDegrees(angle) - 90;
		}
		dragKids.clear();
		dragKidsStart = new ArrayList<>();
		if(selectedActor!=null && (touch_mode == TOUCHMODE.MOVE || touch_mode == TOUCHMODE.ROTATE)){
			try {
				dragKids.addAll(Hierarchy.descendantsOf(this,selectedActor.getName()));
				dragKidsStart = Hierarchy.snapshot(dragKids);
			} catch(Throwable ignored){}
		}
		if(selectedActor!=null){
			PropertySet<String,Object> ps = PropertySet.getPropertySet(selectedActor);
			startTransform = new String[]{
				"x",ps.getString("x"),
				"y",ps.getString("y"),
				"rotation",ps.getString("rotation"),
				"radius",ps.getString("radius"),
				"width",ps.getString("width"),
				"height",ps.getString("height"),
				"Collider Radius",ps.getString("Collider Radius"),
				"Collider Width",ps.getString("Collider Width"),
				"Collider Height",ps.getString("Collider Height")
			};
		}
		return super.touchDown(arg0, arg1, arg2, arg3);
	}
	
	@Override
	public Array<Actor> getActors() {
		//this isn't good because it add more resources to load
		//but I use it because libgdx Array iterator throw Exception
		//when I call array.iterator().hasNext() and anthor thread tried 
		//to call it in the same time, it throw error :
		// => cannot be used nested.
		Array<Actor> actorArray = new Array<>();
		actorArray.addAll(super.getActors());
		return actorArray;
	}
	
	public RayHandler getRayHandler(){
		return rayHandler;
	}
	
	// use new undo/redo system...
	public boolean isUsingNewUndoRedoSystem(){
		return newUndoRedoSystem;
	}
	
	public void pushUndoEvent(EditorAction editorAction){
		if(newUndoRedoSystem){
			undoNStack.push(editorAction);
			redoNStack.clear();
			//refreshUndoRedo();
			undoRedoUpdated();
		}
	}
	
	// remove the repeated undo/redo events....
	private void refreshUndoRedo(){
		//String list = "undo :\n";
		for(int x = 0; x < undoNStack.size(); x++){
			int pos = x+1;
			if(pos < undoNStack.size()){
				if(undoNStack.elementAt(pos).equals(undoNStack.elementAt(x))){
					undoNStack.removeElementAt(pos);
					//toast("undo item removed at : "+pos);
				}
			}
			//list += undoNStack.elementAt(x).toString()+"\n";
		}
		//list += "__".repeat(5)+"\n redo : \n";
		for(int x = 0; x < redoNStack.size(); x++){
			int pos = x+1;
			if(pos < redoNStack.size()){
				if(redoNStack.elementAt(pos).equals(redoNStack.elementAt(x))){
					redoNStack.removeElementAt(pos);
					//toast("redo item removed at : "+pos);
				}
			}
			//list += redoNStack.elementAt(x).toString()+"\n";
		}
		//Gdx.files.external("logs/events.txt").writeString(list,false);
	}
	
	// set the assets loader for images loading....
	public void setAssetLoader(ProjectAssetLoader assetLoader){
		this.projectAssetLoader = assetLoader;
	}
	
	public ProjectAssetLoader getAssetLoader(){
		return projectAssetLoader;
	}
	
	public World getWorld(){
		return world;
	}
	
	// load the scene from the project path
	public void loadFromPath() {
		// the load runs on a later frame: callbacks given meanwhile (whenLoaded, e.g. the default cube and plane of a new 3D
		// scene) must wait for it, else they ran first and the scene was then loaded again on top of them
		loadingScene = true;
		Gdx.app.postRunnable(()->loadFromPathInternal());
	}
	
	private void loadFromPathInternal(){
		undoList.clear();
		redoList.clear();
		undoNStack.clear();
		redoNStack.clear();
		String string = "";
		try {
			com.badlogic.gdx.files.FileHandle fileHandle = Gdx.files.absolute(project.getScenesPath() + scene);
			string = fileHandle.readString();
		} catch(Exception e){}
		updateConfig();
		loadChunked(string);
	}
	
	// ---------------------------------------------------------------- chunked loading
	// Big scenes used to freeze the app while every item was created in one go. The items are now
	// created a few milliseconds per frame, with a progress report for the UI.
	private volatile int loadToken = 0;
	private boolean loadingScene = false;
	private float loadProgress = 0f;
	private final ArrayList<Runnable> loadedCallbacks = new ArrayList<>();
	
	/** True while the scene file is still being turned into items (saving is blocked meanwhile). */
	public boolean isLoading(){
		return loadingScene;
	}
	
	/** 0..1 while loading. */
	public float getLoadProgress(){
		return loadProgress;
	}
	
	/** Run when the scene finished loading (now, if it already did). GL thread. */
	public void whenLoaded(Runnable r){
		if(!loadingScene) r.run();
		else loadedCallbacks.add(r);
	}
	
	private void runLoadedCallbacks(){
		ArrayList<Runnable> cbs = new ArrayList<>(loadedCallbacks);
		loadedCallbacks.clear();
		for(Runnable r:cbs){
			try { r.run(); } catch(Throwable t){ com.star4droid.star2d.logs.Logs.editor("scene_loading", Utils.getStackTraceString(t)+"\n"); }
		}
	}
	
	private void reportLoading(float p){
		loadProgress = p;
		try {
			TestApp app = TestApp.getCurrentApp();
			if(TestApp.nativeHooks != null && app != null && app.getEditor() == this)
				TestApp.nativeHooks.onSceneLoading(p);
		} catch(Throwable ignored){}
	}
	
	private EditorItem createItem(PropertySet<String, Object> propertySet){
		switch(propertySet.getString("TYPE")){
			case "BOX": return new BoxItem(this);
			case "CUSTOM": return new CustomItem(this);
			case "CIRCLE": return new CircleItem(this);
			case "TEXT": return new EditorTextItem(this);
			case "PROGRESS": return new EditorProgressItem(this);
			case "JOYSTICK": return new JoyStickItem(this);
			case "LIGHT": return new LightItem(this);
			case "PARTICLE": return new ParticleItem(this);
			case "CAMERA": return new EditorCameraItem(this);
			case "MAP": return new EditorMapItem(this);
			case "GROUP": return new EditorGroupItem(this);
			case "UIHOLDER": return new EditorUIHolderItem(this);
			case "OBJ3D": case "LIGHT3D": case "CAMERA3D": ensure3D(); return new Editor3DItem(this,propertySet.getString("TYPE"));
			case "CAMERAVIEW": return new EditorCameraViewItem(this);
			case "NAVGRID": return new EditorNavGridItem(this);
			case "NETWORK": return new EditorNetworkItem(this);
		}
		return null;
	}
	
	public void loadChunked(String jsonSave){
		final int token = ++loadToken;
		currentState = jsonSave;
		clear();
		world = new World(new Vector2(0,-9.8f),true);
		if(jsonSave.equals("")){
			loadingScene = false;
			runLoadedCallbacks();
			return;
		}
		loadingScene = true;
		reportLoading(0f);
		try {
			loadUndoRedo();
		} catch(Exception ex){
			toast("loading undo redo lists error : "+ex.toString(),9);
			com.star4droid.star2d.logs.Logs.editor("scene_loading", ex.toString()+"\n");
		}
		// parsing a big file is slow too, so it runs off the GL thread
		new Thread(()->{
			ArrayList<PropertySet<String, Object>> sets = null;
			try {
				sets = new Gson().fromJson(jsonSave,
					new TypeToken<ArrayList<PropertySet<String, Object>>>() {}.getType());
			} catch(Throwable t){
				com.star4droid.star2d.logs.Logs.editor("scene_loading", Utils.getStackTraceString(t)+"\n");
			}
			final ArrayList<PropertySet<String, Object>> parsed = sets == null ? new ArrayList<>() : sets;
			Gdx.app.postRunnable(()->{
				if(token != loadToken) return;
				loadStep(token, parsed, new HashMap<String,PropertySet>(), 0);
			});
		},"scene-parse").start();
	}
	
	private void loadStep(int token, ArrayList<PropertySet<String, Object>> sets, HashMap<String,PropertySet> propsMap, int index){
		if(token != loadToken) return;
		long end = System.nanoTime() + 8_000_000L; // ~8ms of work per frame
		int total = Math.max(1, sets.size());
		try {
			while(index < sets.size()){
				PropertySet<String, Object> propertySet = sets.get(index++);
				if(propertySet != null){
					EditorItem item = createItem(propertySet);
					if(item != null){
						propsMap.put(propertySet.getString("name"),propertySet);
						((Actor)item).setName(propertySet.getString("name"));
						item.setProperties(propertySet);
						if(selectedActor == null)
							selectActor((Actor) item);
					}
				}
				if(System.nanoTime() > end) break;
			}
		} catch(Exception e){
			com.star4droid.star2d.logs.Logs.editor("scene_loading", Utils.getStackTraceString(e)+"\n");
		}
		if(index < sets.size()){
			reportLoading(index / (float) total);
			final int next = index;
			Gdx.app.postRunnable(()->loadStep(token, sets, propsMap, next));
			return;
		}
		// everything is created: link the parents and tell the UI
		try {
			for(Actor actor:getActors()){
				if(Utils.isEditorItem(actor)){
					PropertySet set1= PropertySet.getPropertySet(actor);
					if(!set1.getString("parent").equals("")){
						if(propsMap.containsKey(set1.getString("parent"))){
							set1.setParent(propsMap.get(set1.getString("parent")));
						} else {
							com.star4droid.star2d.logs.Logs.editor("parents", "parent not found for : "+set1.getString("name")+", parent name : "+set1.getString("parent")+"\n");
							set1.put("parent","");
						}
					}
				}
			}
		} catch(Exception exx){}
		propsMap.clear();
		try { Hierarchy.relink(this); } catch(Throwable ignored){}
		loadingScene = false;
		if(controlLayer!=null){
			if(selectedActor!=null)
				controlLayer.bodySelected();
			controlLayer.updateUndoRedo();
		}
		if(editorListener!=null){
			editorListener.onUpdateUndoRedo();
			if(selectedActor!=null)
				editorListener.onBodySelected();
		}
		reportLoading(-1f);
		runLoadedCallbacks();
		try {
			if(TestApp.nativeHooks != null && TestApp.getCurrentApp() != null && TestApp.getCurrentApp().getEditor() == this)
				TestApp.nativeHooks.onEditorChanged();
		} catch(Throwable ignored){}
	}
	
	// editor listener to listen for body selection and undo/redo events change
	public EditorListener getEditorListener(){
		return editorListener;
	}
	
	public void setEditorListener(EditorListener listener) {
		editorListener = listener;
	}
	
	// set the project which contains the main path and important methods (see Project.java)
	public void setProject(Project project){
		this.project = project;
	}
	
	public void enableAutoSave(boolean b){
		AUTO_SAVE = b;
	}
	
	public void setToCurrentEditor(){
		currentEditor = this;
	}
	
	public static LibgdxEditor getCurrentEditor(){
		return currentEditor;
	}
	
	// center the camera ...
	public void centerCamera(){
		if(e3d!=null && !e3d.edit2D){ e3d.focusSelected(); return; }
		cameraController.moveTo(getWidth()*0.5f,getHeight()*0.5f,0.3f);
	}
	
	// update the config from the project config path ...
	String lastConfig = "...";
	public void updateConfig(){
		try {
			String cfg = "";
			com.badlogic.gdx.files.FileHandle file = Gdx.files.absolute(project.getConfig(scene));
			if(file.exists())
			cfg = file.readString();
			if(lastConfig.equals(cfg)) return;
			editorConfig = null;
			if(!cfg.equals("")){
				editorConfig = Utils.getProperty(cfg);
				backgroundColor = editorConfig.getColor("sceneColor");
			}
			
			if(editorConfig == null){
				lastConfig="nothing...";
				Gdx.files.absolute(project.getConfig(scene)).writeString(Gdx.files.internal("scene.json").readString(),false);
				cfg=Gdx.files.absolute(project.getConfig(scene)).readString();
				editorConfig = Utils.getProperty(cfg);
				inheritProjectOrientation();
				backgroundColor = editorConfig.getColor("sceneColor");
			}
			
			if(editorConfig!=null && !editorConfig.containsKey("logicHeight")){
				int wd = Gdx.graphics.getWidth(),
					hg = Gdx.graphics.getHeight();
				editorConfig.put("logicWidth",wd > hg ? wd : hg);
				editorConfig.put("logicHeight",hg < wd ? hg : wd);
				saveConfig();
			} else if(editorConfig!=null){
				setLogicalWH(editorConfig.getFloat("logicWidth"),editorConfig.getFloat("logicHeight"));
			}
			LANDSCAPE = editorConfig.getString("or").equals("") || editorConfig.getString("or").equals("landscape");
			if(orienationChangeListener!=null)
				orienationChangeListener.onChange(LANDSCAPE);
			ensure3D();
		} catch(Exception e){
			toast("update config error : "+e.toString());
		}
	}
	
	/** A scene created with the default config takes the orientation of the project (its other scenes), not always landscape. */
	private void inheritProjectOrientation(){
		try {
			java.io.File mine = new java.io.File(project.getConfig(scene));
			java.io.File dir = mine.getParentFile();
			java.io.File[] all = dir == null ? null : dir.listFiles();
			if(all == null) return;
			java.io.File pick = null;
			for(java.io.File f : all){
				if(!f.isFile() || f.getName().equals(mine.getName()) || f.getName().startsWith("__")) continue;
				if(pick == null || f.getName().startsWith("scene1")) pick = f;
			}
			if(pick == null) return;
			String or = Utils.getProperty(Gdx.files.absolute(pick.getAbsolutePath()).readString()).getString("or");
			if(or.equals("portrait") || or.equals("landscape") || or.equals("")){
				editorConfig.put("or", or.equals("portrait") ? "portrait" : "");
				Gdx.files.absolute(project.getConfig(scene)).writeString(editorConfig.toString(), false);
				lastConfig = Gdx.files.absolute(project.getConfig(scene)).readString();
			}
		} catch(Exception ignored){}
	}

	// get logical width/height of the editor 
	public float getLogicWidth(){
	    String key = LANDSCAPE ? "logicWidth":"logicHeight";
		return editorConfig != null && editorConfig.containsKey(key)?editorConfig.getFloat(key):(LANDSCAPE ? Gdx.graphics.getWidth() : Gdx.graphics.getHeight());
	}
	
	public float getLogicHeight(){
	    String key = LANDSCAPE ? "logicHeight":"logicWidth";
		return editorConfig != null && editorConfig.containsKey(key)?editorConfig.getFloat(key):(LANDSCAPE ? Gdx.graphics.getHeight() : Gdx.graphics.getWidth());
	}
	
	public PropertySet<String,Object> getConfig(){
		updateConfig();
		return editorConfig;
	}
	
	public void setScene(String s){
		this.scene = s;
	}
	
	int tries=0;
	public void setSceneColor(String s){
		while(editorConfig==null&&tries<99999){
		 updateConfig();
		 tries++;
		 }
		if(editorConfig!=null){
			String prev = editorConfig.containsKey("sceneColor")?editorConfig.getString("sceneColor"):"";
			try {
				editorConfig.put("sceneColor",s);
				backgroundColor = editorConfig.getColor("sceneColor");
				Gdx.files.absolute(project.getConfig(scene)).writeString(editorConfig.toString(),false);
			} catch(Exception ex){
				//Utils.showMessage(getContext(),"Error : "+ex.toString());
				editorConfig.put("sceneColor",prev);
			}
		}//else Utils.showMessage(getContext(),"Null Config.!!");
	}
	
	public Array<Actor> getChildren(){
		return getActors();
	}
	
	public void undoRedoUpdated(){
		if(editorListener!=null)
			editorListener.onUpdateUndoRedo();
		if(controlLayer!=null)
			controlLayer.updateUndoRedo();
	}
	
	public Actor findActor(String name){
		if(name == null) return null;
		for(Actor actor:getActors())
			if(actor.getName()!=null && actor.getName().equals(name))
				return actor;
		return null;
	}
	
	public Actor getChild(int i){
		return getActors().get(i);
	}
	
	public ArrayList<String> getBodiesList() {
		ArrayList<String> bodies = new ArrayList<>();
		ArrayList<String> fixed = new ArrayList<>();
		List<Pair<Float, PropertySet<String,Object>>> zValues = new ArrayList<>();
		Array<Actor> childs = getActors();
		for (int x = 0; x < childs.size; x++) {
			try {
				Actor v = childs.get(x);
				if(!(v instanceof EditorItem)) continue;
				PropertySet<String,Object> propertySet = PropertySet.getPropertySet(v);
				if (propertySet == null)
					continue;
				if (v instanceof Editor3DItem || (v instanceof EditorNavGridItem && is3DScene())) {
					// "z" of a 3D element is a position (meters), not a drawing order: listed, never renumbered
					if (propertySet.containsKey("name")) fixed.add(propertySet.getString("name"));
					continue;
				}
				if (propertySet.containsKey("name")) {
					float z = propertySet.getFloat("z");
					String name = propertySet.getString("name");
					zValues.add(new Pair<>(z, propertySet));
				}
			} catch(Exception ex){}
		}
		
		// Sort the zValues list based on the z values
		Collections.sort(zValues, new Comparator<Pair<Float, PropertySet<String,Object>>>() {
			@Override
			public int compare(Pair<Float, PropertySet<String,Object>> pair1, Pair<Float, PropertySet<String,Object>> pair2) {
				return Float.compare(pair1.first, pair2.first);
			}
		});
		
		// Add the names to the bodies list in the sorted order
		int z = 0;
		for (Pair<Float, PropertySet<String,Object>> pair : zValues) {
			bodies.add(pair.second.getString("name"));
			pair.second.put("z",z);
			z++;
		}
		bodies.addAll(fixed);
		
		return bodies;
	}
	
	// update the childs of the editor (I forget this method)
	public void updateChilds() {
		//int count = 0;
		for (int x = 0; x < getChildren().size; x++)
			if (Utils.isEditorItem(getChild(x))){
				Utils.update(getChild(x));
				//count++;
			}
		//Gdx.files.external("logs/count.txt").writeString("updated : "+count+"\n",true);
	}
    
    // save editor config ...
	public void saveConfig(){
		if(editorConfig!=null)
			Gdx.files.absolute(project.getConfig(scene)).writeString(editorConfig.toString(),false);
	}
	
	// find proper name for actor based on the actor type ...
	public String getName(Actor s) {
		String name = "Item";
		if (s instanceof EditorItem)
			name = ((EditorItem)s).getTypeName();
		int x = 1;
		while (findActor(name + x)!=null)
			x++;
		return name + x;
	}
	
	// set editor to landscape mode (Invert logic width/height)
	public void setLandscape(boolean b){
		try {
			// the flag alone can be stale (a scene editor starts with LANDSCAPE=false before its config is read):
			// skip only when BOTH the flag and the stored config already say so
			PropertySet<String,Object> cur = getConfig();
			String stored = cur == null ? null : cur.getString("or");
			boolean storedLandscape = stored == null || stored.equals("") || stored.equals("landscape");
			if(b == this.LANDSCAPE && b == storedLandscape) return;
		} catch(Exception ignored){
			if(b == this.LANDSCAPE) return;
		}
		try {
    		getConfig().put("or",b ? "":"portrait");
    		for(Actor actor:getActors()){
    			if(actor instanceof EditorItem)
    				((EditorItem)actor).update();
    		}
    		saveConfig();
    		// the flag must follow the config at once: other code (AI status, new scene editors, the toggle)
    		// reads isLandscape() right after this and used to get the OLD value until the next config reload
    		LANDSCAPE = b;
    		if(orienationChangeListener!=null)
    			orienationChangeListener.onChange(b);
	    } catch(Exception ex){
	        com.star4droid.star2d.logs.Logs.editor("scene_loading", ex.toString());
	    }
	}
	
	public boolean isLandscape(){
		return LANDSCAPE;
	}
	
	/** The two numbers as stored in the config. Convention: logicWidth = the LONG side, logicHeight = the SHORT side, in both orientations (portrait swaps them when used). */
	public float getRawLogicWidth(){
		PropertySet<String,Object> c = getConfig();
		return c != null && c.containsKey("logicWidth") ? c.getFloat("logicWidth") : Math.max(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
	}
	
	public float getRawLogicHeight(){
		PropertySet<String,Object> c = getConfig();
		return c != null && c.containsKey("logicHeight") ? c.getFloat("logicHeight") : Math.min(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
	}
	
	/** True when the stored size breaks the convention (logicWidth shorter than logicHeight): the frame (and the game) then looks reversed. */
	public boolean logicSizeLooksSwapped(){
		return getRawLogicWidth() < getRawLogicHeight();
	}
	
	/** Explicit user action: exchange the two stored sizes (fixes a reversed frame). */
	public void swapLogicSize(){
		float w = getRawLogicWidth(), h = getRawLogicHeight();
		setLogicalWH(h,w);
		saveConfig();
		for(Actor actor:getActors())
			if(actor instanceof EditorItem)
				((EditorItem)actor).update();
	}
	
	private boolean swapHintShown = false;
	/** Once per editor: tells the user when the size looks reversed and where the fix is. */
	public void checkLogicSizeHint(){
		if(swapHintShown) return;
		try {
			if(logicSizeLooksSwapped()){
				swapHintShown = true;
				toast(com.star4droid.star2d.ui.Tr.t("logic_swapped_hint","The game size looks reversed (width < height). Settings > Scene > Swap width and height."),4);
			}
		} catch(Throwable ignored){}
	}
	
	public void setLogicalWH(float width,float height){
		logicalWidth = width;
		logicalHeight = height;
		if(editorConfig!=null){
		    editorConfig.put("logicWidth", width);
		    editorConfig.put("logicHeight", height);
		}
		//getConfig().put("logicWidth",width);
		//editorConfig.put("logicHeight",height);
		//saveConfig();
	}
	
	// set the zooming for the editor
	public void setScale(float zoom){
		((OrthographicCamera)getCamera()).zoom = zoom;
	}
	
	public float getZoom(){
	    return ((OrthographicCamera)getCamera()).zoom;
	}
	
	public void selectActor(Actor actor){
		selectedActor = actor;
		if(actor == null){
			for(Actor a:getActors()){
				if(actor instanceof EditorItem)
					selectedActor = a;
			}
		}
		if(selectedActor==null) return;
		updateProperties();
		
		if(controlLayer!=null){
				controlLayer.bodySelected();
		}
		
		if (editorListener != null){
			editorListener.onBodySelected();
		}
	}
	
	public String getScene(){
		return scene;
	}
	
	public Project getProject(){
		return project;
	}
	// useless 
	public void setProperites(PropertiesHolder propertiesHolder){
		//disabled...
		//this.propertiesHolder = propertiesHolder;
	}
	
	public void setRatioScale(float ratio){
		this.ratioScale = ratio;
	}
	
	public void updateProperties(){
		//Gdx.files.external("logs/props.txt").writeString("\nprops : "+(propertiesHolder!=null),true);
		if(propertiesHolder!=null)
			propertiesHolder.updateProperties();
		if(controlLayer!=null && controlLayer.getPropertiesItem()!=null)
			controlLayer.getPropertiesItem().refresh();
	}
	
	// current state variable used by the previous undo/redo system 
	String currentState = "";
    
    // this method get the properties of items then added them to json and return it...
	/** Values to save instead of the shown ones (a timeline being previewed shows other values than the scene's). */
	public interface SaveFilter {
		PropertySet<String,Object> forSave(Actor actor, PropertySet<String,Object> shown);
	}
	public SaveFilter saveFilter;
	/** The timeline being edited (Timeline panel), null when none. */
	public TimelineSession timeline;

	public String getSaveState() {
		ArrayList<PropertySet<String, Object>> items = new ArrayList<>();
		SaveFilter filter = saveFilter;
		for (int x = 0; x < getChildren().size; x++) {
			if (!Utils.isEditorItem(getChild(x)))
				continue;
			PropertySet<String, Object> ps = PropertySet.getPropertySet(getChild(x));
			if (filter != null) {
				try { ps = filter.forSave(getChild(x), ps); } catch (Throwable ignored) {}
			}
			items.add(ps);
		}
		String save = new Gson().toJson(items);
		if (getChildren().size == 0)
			save = "";
		//String last = (undoList.size()>0)?undoList.elementAt(undoList.size()-1):"";
		// the previous undo/redo system load all items from json 
		// so , it push json data to stack, then load it when undo/redo
		if (!(save.equals(currentState) || newUndoRedoSystem)) {
			undoList.push(currentState);
			currentState = save;
			redoList.clear();
			//Log.e("error_of_star2d","prev :\n"+(undoList.elementAt(undoList.size()-1)+"\n prev : \n"+save));
		}
		if (editorListener != null && !newUndoRedoSystem){
			editorListener.onUpdateUndoRedo();
		}
		if(controlLayer!=null && !newUndoRedoSystem)
			controlLayer.updateUndoRedo();
		return save;
	}

	public boolean canUndo() {
		return ((newUndoRedoSystem ? undoNStack : undoList).size() > 0);
	}

	public boolean canRedo() {
		return ((newUndoRedoSystem ? redoNStack : redoList).size() > 0);
	}

	public void undo() {
		if (canUndo()) {
			if(newUndoRedoSystem){
				EditorAction action = undoNStack.pop();
				redoNStack.push(action.undo());
				//remove repeated items...
				while(undoNStack.size() > 0 && undoNStack.elementAt(undoNStack.size()-1).equals(action))
					undoNStack.removeElementAt(undoNStack.size()-1);
				//refreshUndoRedo();
			} else {
				String currentItem = selectedActor!=null?PropertySet.getPropertySet(selectedActor).getString("name"):"";
				String state = currentState;// getSaveState();
				String element = undoList.pop();
				load(element);
				redoList.push(state);
				if(currentItem.equals("")) return;
				selectActor(findActor(currentItem));
			}
			if(editorListener!=null)
				editorListener.onUpdateUndoRedo();
			if(controlLayer!=null)
				controlLayer.updateUndoRedo();
		}
	}

	public void redo() {
		if (canRedo()) {
			if(newUndoRedoSystem){
				EditorAction action = redoNStack.pop();
				undoNStack.push(action.redo());
				//remove repeated items...
				while(redoNStack.size() > 0 && redoNStack.elementAt(redoNStack.size()-1).equals(action))
					redoNStack.removeElementAt(redoNStack.size()-1);
				//refreshUndoRedo();
			} else {
				String currentItem = selectedActor!=null?PropertySet.getPropertySet(selectedActor).getString("name"):"";
				String state = currentState;
				String element = redoList.pop();
				load(element);
				undoList.push(state);
				if(currentItem.equals("")) return;
				selectActor(findActor(currentItem));
			}
			if(editorListener!=null)
				editorListener.onUpdateUndoRedo();
			if(controlLayer!=null)
				controlLayer.updateUndoRedo();
		}
	}
	
	public void setUseNewUndoRedoSystem(boolean use){
		if(use != newUndoRedoSystem){
			undoList.clear();
			redoList.clear();
			undoNStack.clear();
			redoNStack.clear();
		}
	}
	
	// load json data of items ...
	public void load(String jsonSave){
		try {
			currentState = jsonSave;
			//Gdx.files.external("logs/log.txt").writeString("\nloading : "+(jsonSave.length()>20?jsonSave.substring(0,20):jsonSave),true);
			clear();
			world = new World(new Vector2(0,-9.8f),true);
			if(jsonSave.equals("")) return;
			HashMap<String,PropertySet> propsMap= new HashMap<>();
			try {
				loadUndoRedo();
			} catch(Exception ex){
				toast("loading undo redo lists error : "+ex.toString(),9);
				com.star4droid.star2d.logs.Logs.editor("scene_loading", ex.toString()+"\n");
			}
			ArrayList<PropertySet<String, Object>> propertySets = new Gson().fromJson(jsonSave,
					new TypeToken<ArrayList<PropertySet<String, Object>>>() {
					}.getType());
			//Gdx.files.external("logs/count.txt").writeString("\nsize : "+propertySets.size(),true);
			for (PropertySet<String, Object> propertySet : propertySets) {
				EditorItem item = null;
				switch(propertySet.getString("TYPE")){
					case "BOX":
					item = new BoxItem(this);
					break;
					case "CUSTOM":
					item = new CustomItem(this);
					break;
					case "CIRCLE":
					item = new CircleItem(this);
					break;
					case "TEXT":
					item = new EditorTextItem(this);
					break;
					case "PROGRESS":
					item = new EditorProgressItem(this);
					break;
					case "JOYSTICK":
					item = new JoyStickItem(this);
					break;
					case "LIGHT":
					item = new LightItem(this);
					break;
					case "PARTICLE":
					item = new ParticleItem(this);
					break;
					case "CAMERA":
					item = new EditorCameraItem(this);
					break;
					case "MAP":
					item = new EditorMapItem(this);
					break;
					case "GROUP":
					item = new EditorGroupItem(this);
					break;
					case "UIHOLDER":
					item = new EditorUIHolderItem(this);
					break;
				}
				
				if (item == null)
					continue;
				if(propertySet==null){
					com.star4droid.star2d.logs.Logs.editor("scene_loading", "\nnull props : "+propertySet.toString()+"\n");
					continue;
				}
				propsMap.put(propertySet.getString("name"),propertySet);
				//addActor((Actor) item);
				((Actor)item).setName(propertySet.getString("name"));
				item.setProperties(propertySet);
				//Gdx.files.external("logs/bodies.txt").writeString("add : "+propertySet.getString("name")+",json : \n"+propertySet.toString()+"\n\n",true);
				if (selectedActor == null)
					selectActor((Actor) item);
			}
			try {
				for(Actor actor:getActors()){
					if(Utils.isEditorItem(actor)){
						PropertySet set1= PropertySet.getPropertySet(actor);
						if(!set1.getString("parent").equals("")){
							if(propsMap.containsKey(set1.getString("parent"))){
								set1.setParent(propsMap.get(set1.getString("parent")));
							} else {
							    com.star4droid.star2d.logs.Logs.editor("parents", "parent not found for : "+set1.getString("name")+", parent name : "+set1.getString("parent")+"\n");
							    set1.put("parent","");
							}
						}
					}
				}
			} catch(Exception exx){}
			propsMap.clear();
			try { Hierarchy.relink(this); } catch(Throwable ignored){}
			if(controlLayer!=null){
				if(selectedActor!=null)
					controlLayer.bodySelected();
				controlLayer.updateUndoRedo();
			}
			
			if(editorListener!=null){
				editorListener.onUpdateUndoRedo();
				if(selectedActor!=null)
					editorListener.onBodySelected();
			}
		} catch(Exception e){
			e.printStackTrace();
			com.star4droid.star2d.logs.Logs.editor("scene_loading", Utils.getStackTraceString(e)+"\n");
		}
		//addActor(pointPicker);
	}
	
	// load editor undo/redo data...
	public void loadUndoRedo(){
		String file = project.getUndoRedo(scene);
		FileHandle fh = Gdx.files.absolute(file);
		undoNStack.clear();
		redoNStack.clear();
		if(fh.exists() && !fh.isDirectory()){
			String json = fh.readString();
			PropertySet<String,Object> ps = PropertySet.getFrom(json);
			ArrayList<String> undo = new Gson().fromJson(ps.getString("undo"),new TypeToken<ArrayList<String>>() {}.getType()),
				redo = new Gson().fromJson(ps.getString("redo"),new TypeToken<ArrayList<String>>() {}.getType());
			for(String str:undo)
				undoNStack.push(EditorAction.getFrom(str,this));
			for(String str:redo)
				redoNStack.push(EditorAction.getFrom(str,this));
		}
	}
	
	@Override
	public void addActor(Actor actor) {
		super.addActor(actor);
		if(actor instanceof EditorItem && selectedActor == null){
			selectedActor = actor;
		}
	}
	
	public Actor getSelectedActor(){
		return selectedActor;
	}
	
	public boolean selectByName(String name){
		for(Actor actor:getActors()){
			try {
				if(actor.getName()!=null && actor instanceof EditorItem && actor.getName().equals(name)){
					selectedActor = actor;
					return true;
				}
			} catch(Exception e){}
		}
		return false;
	}
	
	public boolean isAutoSave(){
		return AUTO_SAVE;
	}
	
	// show editor grids ?
	public void showGrids(boolean show){
		SHOW_GRIDS = show;
	}
	
	public float getRatioScale(){
		return ratioScale;
	}
	
	// get undo/redo json data (the new system)
	public String getUndoRedoJson(){
		ArrayList<String> undo = new ArrayList<>(),
			redo = new ArrayList<>();
		for(EditorAction action:undoNStack)
			undo.add(action.toString());
		for(EditorAction action:redoNStack)
			redo.add(action.toString());
		PropertySet<String,Object> ps = new PropertySet<>();
		ps.put("undo",new Gson().toJson(undo));
		ps.put("redo",new Gson().toJson(redo));
		return ps.toString();
	}
	
	@Override
	public boolean touchDragged(int arg0, int arg1, int arg2) {
		if(e3d!=null && !e3d.edit2D && !loadingScene) return e3d.touchDragged(arg0,arg1,arg2);
		if(loadingScene) return true;
		CanvasTool tool = activeTool();
		if(tool != null){
			if(!tool.navigate() && arg2 == 0 && !Gdx.input.isTouched(1)){
				Vector2 sp = screenToStageCoordinates(new Vector2(arg0,arg1));
				tool.drag(sp.x,sp.y);
				return true;
			}
			tool.cancel();
			handleTouch(); // one finger with "move the view" on, or two fingers: pan / zoom
			return true;
		}
		if(com.star4droid.star2d.remote.RemoteSession.isLocked()){
			handleTouch();
			return true;
		}
	    // handle the touch with movement...
		handleTouch();
		return super.touchDragged(arg0, arg1, arg2);
	}
	
	private void handleTouch(){
		OrthographicCamera camera = (OrthographicCamera)getCamera();
		if(Gdx.input.isTouched() && !Gdx.input.isTouched(1)){
			PropertySet<String,Object> propertySet = (selectedActor != null && selectedActor instanceof EditorItem) ? ((EditorItem)selectedActor).getPropertySet():null;
			float deltaX = lockY ? 0 : (-Gdx.input.getDeltaX() * camera.zoom * 0.5f);
			float deltaY = lockX ? 0 : (Gdx.input.getDeltaY() * camera.zoom * 0.5f);
			boolean locked = propertySet != null && Hierarchy.isLocked(propertySet);
			if(touch_mode == TOUCHMODE.GRID || activeTool() != null || com.star4droid.star2d.remote.RemoteSession.isLocked() || selectedActor == null || locked || !(selectedActor instanceof EditorItem)){
				//movement
				camera.translate(deltaX,deltaY);
			} else if(touch_mode == TOUCHMODE.MOVE){
				propertySet.put("x",propertySet.getFloat("x") - deltaX);
				propertySet.put("y",propertySet.getFloat("y") - deltaY);
				((EditorItem)selectedActor).update();
				if(!dragKids.isEmpty()) Hierarchy.move(dragKids,-deltaX,-deltaY);
				
				//selectedActor.setPosition(selectedActor.getX()+deltaX,selectedActor.getY()+deltaX);
			} else if(touch_mode == TOUCHMODE.ROTATE){
				Vector2 input = screenToStageCoordinates(new Vector2(Gdx.input.getX(),Gdx.input.getY()));
				float ix = input.x,
					iy = input.y;
				float centerX = selectedActor.getX() + selectedActor.getWidth() * 0.5f,
						centerY = selectedActor.getY() + selectedActor.getHeight() * 0.5f;
				double angle = Math.atan2(iy - centerY, ix - centerX);
				angle = Math.toDegrees(angle) - 90;
				//float dy = centerY - iy;
				/*
				shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
				shapeRenderer.setColor(Color.YELLOW);
				shapeRenderer.line(ix,iy,centerX,centerY);
				shapeRenderer.end();
				*/
				float currentAngle = propertySet.containsKey("rotation") ? propertySet.getFloat("rotation") : 0;
				double deltaRotation = startAngle - angle;
				startAngle = (float)angle;
				angle = currentAngle + deltaRotation;
				while (angle > 360)
					angle -= 360;
				while (angle < 0)
					angle += 360;
				propertySet.put("rotation", angle);
				((EditorItem)selectedActor).update();
				if(!dragKids.isEmpty()) Hierarchy.rotate(dragKids,centerX,centerY,(float)deltaRotation);
				
			} else if(touch_mode == TOUCHMODE.SCALE){
			    deltaY = -deltaY;
				if(propertySet.containsKey("width")){
					float x = propertySet.getFloat("width") - deltaX;
					float y = propertySet.getFloat("height") + deltaY;
					float colx = propertySet.getFloat("Collider Width") - deltaX;
					float coly = propertySet.getFloat("Collider Height") + deltaY;
					propertySet.put("width", Math.max(x, 1f));
					propertySet.put("height", Math.max(y, 1f));
					if(propertySet.containsKey("Collider Width")) propertySet.put("Collider Width", Math.max(1f,colx));
					if(propertySet.containsKey("Collider Height")) propertySet.put("Collider Height", Math.max(1f,coly));
				} else if(propertySet.containsKey("radius")){
					float rad = propertySet.getFloat("radius") - deltaX;
					float cRad = propertySet.getFloat("Collider Radius") - deltaX;
					propertySet.put("radius", Math.max(rad, 1f));
					propertySet.put("Collider Radius", Math.max(cRad,1f));
				}
				if(propertySet.containsKey("radius") || propertySet.containsKey("width")){
					((EditorItem)selectedActor).update();
				}
			}
		}
		
		// zoom
		float deltaX = Gdx.input.getX(0) - Gdx.input.getX(1);
        float deltaY = Gdx.input.getY(0) - Gdx.input.getY(1);
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (Gdx.input.isTouched(1) && prevDistance != -1) {
            camera.zoom -= 0.001f * (distance - prevDistance);
            camera.zoom = MathUtils.clamp(camera.zoom, 0.01f, 6f);
        }
		
		//two finger movement
		if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1)) {
			// Get deltas for both pointers (0 and 1)
			float deltaX0 = -Gdx.input.getDeltaX(0) * camera.zoom * 0.5f;
			float deltaY0 = Gdx.input.getDeltaY(0) * camera.zoom * 0.5f;
			float deltaX1 = -Gdx.input.getDeltaX(1) * camera.zoom * 0.5f;
			float deltaY1 = Gdx.input.getDeltaY(1) * camera.zoom * 0.5f;
			
			// Determine direction vectors
			float dirX0 = Math.signum(deltaX0);
			float dirY0 = Math.signum(deltaY0);
			float dirX1 = Math.signum(deltaX1);
			float dirY1 = Math.signum(deltaY1);
			
			boolean update = false;
			
			// Combine X movement if both fingers move in the same X direction
			if (dirX0 == dirX1 && dirX0 != 0) {
				camera.position.x += (deltaX0 + deltaX1);
				update = true;
			}
			
			// Combine Y movement if both fingers move in the same Y direction
			if (dirY0 == dirY1 && dirY0 != 0) {
				camera.position.y += (deltaY0 + deltaY1);
				update = true;
			}
			
			/*if (update) {
				camera.update();
			}*/
		}
		
        prevDistance = distance;
    
        if ((!Gdx.input.isTouched(1)) && prevDistance != -1) {
            prevDistance = -1;
        }
		camera.update();
	}
	
	boolean childsUpdated = false;
	boolean SHOW_GRIDS = true;
	
	@Override
	public void draw() {
		if(!childsUpdated){
			updateChilds();
			childsUpdated = true;
		}
		// timeline being edited: preview of the playhead, keys of the recorded changes
		if(timeline != null && !loadingScene) try { timeline.frame(Math.min(0.05f, Gdx.graphics.getDeltaTime())); } catch(Throwable t){ Gdx.app.error("Star2D","timeline: "+t); }
		if(cameraController.isMoving())
			cameraController.update(Gdx.graphics.getDeltaTime());
		OrthographicCamera camera = (OrthographicCamera)getCamera();
		if(e3d!=null && !(e3d.edit2D && !e3d.show3DBehind)){
			// 3D scene: the 3D viewport, then the 2D layer (HUD / UI elements) on top
			// (editing the 2D layer without "show 3D behind" falls through to the normal 2D canvas: grid, frame)
			try { e3d.render(); } catch(Throwable t){ Gdx.app.error("Star2D-3D", "editor render: "+t); }
			shapeRenderer.setProjectionMatrix(camera.combined);
			if(e3d.edit2D) drawSceneFrame(camera,getLogicWidth(),getLogicHeight());
			try { Hierarchy.applyVisibility(this); } catch(Throwable ignored){}
			// the HUD / UI elements (2D layer) are hidden while the 3D world is edited: they only get in the way
			// (they are drawn, with a grid-less frame, when "2D layer" editing is on and the 3D world is shown behind)
			if(e3d.edit2D){
				getBatch().setColor(1,1,1,1);
				super.draw();
			}
			updateProperties();
			if(selectedActor != null && !(selectedActor instanceof Editor3DItem) && e3d.edit2D){
				Gdx.gl.glLineWidth(3f);
				shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
				selectedActor.setDebug(true);
				selectedActor.drawDebug(shapeRenderer);
				shapeRenderer.end();
				selectedActor.setDebug(false);
				Gdx.gl.glLineWidth(1f);
			}
			return;
		}
		shapeRenderer.setProjectionMatrix(rendererMatrix);
		//draw background
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		shapeRenderer.setColor(backgroundColor);
		shapeRenderer.rect(-50,-50,super.getWidth()+50,super.getHeight()+50);
		shapeRenderer.end();
		shapeRenderer.setProjectionMatrix(camera.combined);
		if(SHOW_GRIDS){
			//draw grids
			shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
			shapeRenderer.setColor(Color.DARK_GRAY);
			float h = getHeight(),
				w = getWidth(),
				zoom = camera.zoom,
				cx = camera.position.x,
				cy = camera.position.y;
		
        	// Get camera parameters
        	float viewportWidth = camera.viewportWidth * camera.zoom;
        	float viewportHeight = camera.viewportHeight * camera.zoom;
        
        	// Calculate visible world boundaries
        	float visibleLeft = camera.position.x - viewportWidth/2;
        	float visibleRight = camera.position.x + viewportWidth/2;
        	float visibleBottom = camera.position.y - viewportHeight/2;
        	float visibleTop = camera.position.y + viewportHeight/2;
        
        	// Calculate grid starting and ending points with padding
        	float startX = MathUtils.floor(visibleLeft / gridSizeX) * gridSizeX;
        	float endX = MathUtils.ceil(visibleRight / gridSizeX) * gridSizeX;
        	float startY = MathUtils.floor(visibleBottom / gridSizeY) * gridSizeY;
        	float endY = MathUtils.ceil(visibleTop / gridSizeY) * gridSizeY;
        
        	// Draw vertical grid lines
        	for (float x = startX; x <= endX; x += gridSizeX) {
            	shapeRenderer.line(x, visibleBottom, x, visibleTop);
       	 }
        
        	// Draw horizontal grid lines
        	for (float y = startY; y <= endY; y += gridSizeY) {
           	 shapeRenderer.line(visibleLeft, y, visibleRight, y);
        	}
		
			shapeRenderer.end();
		}
		
		//draw border and selector
		shapeRenderer.setProjectionMatrix(camera.combined);
		//scene frame: light grey stroke under dashed orange lines (readable on dark and light scene colors)
		//if landscape the frame is (width,height) and reversed if its not (the getters do that)...
		drawSceneFrame(camera,getLogicWidth(),getLogicHeight());
		
		try { Hierarchy.applyVisibility(this); } catch(Throwable ignored){}
		super.draw();
		updateProperties();
		try {
			debugRenderer.render(world,camera.combined);
		} catch(Exception e){}
		if(selectedActor != null){
			Gdx.gl.glLineWidth(3f);
			shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
			selectedActor.setDebug(true);
			selectedActor.drawDebug(shapeRenderer);
			shapeRenderer.end();
			selectedActor.setDebug(false);
			Gdx.gl.glLineWidth(1f);
		}
		
		if(e3d==null) try {
			rayHandler.setCombinedMatrix(camera.combined,0,0,1,1);
			rayHandler.updateAndRender();
		} catch(Exception e){}
		// path finding grids on top of everything (outline + badge, cells of the selected one)
		if(e3d==null) try { EditorNavGridItem.drawAll(this,shapeRenderer,getBatch()); } catch(Throwable t){ Gdx.app.error("Star2D","navgrid draw: "+t); }
		if(e3d==null) try { pathDraw.draw2D(shapeRenderer,camera); } catch(Throwable t){ Gdx.app.error("Star2D","path draw: "+t); }
	}
	
	// ---- scene frame (dashed orange + light grey stroke), constant on screen: widths and dash length are in screen pixels ----
	private static final Color FRAME_ORANGE = new Color(1f,0.60f,0.05f,1f);
	private static final Color FRAME_STROKE = new Color(0.80f,0.82f,0.85f,0.50f);
	private static final float FRAME_DASH_PX = 14f, FRAME_GAP_PX = 8f;

	private void drawSceneFrame(OrthographicCamera camera,float w,float h){
		Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
		// world units per screen pixel (zoom-aware)
		float upp = camera.viewportWidth * camera.zoom / Math.max(1,Gdx.graphics.getWidth());
		float dash = FRAME_DASH_PX * upp, period = (FRAME_DASH_PX + FRAME_GAP_PX) * upp;
		float vl = camera.position.x - camera.viewportWidth*camera.zoom*0.5f - period,
			vr = camera.position.x + camera.viewportWidth*camera.zoom*0.5f + period,
			vb = camera.position.y - camera.viewportHeight*camera.zoom*0.5f - period,
			vt = camera.position.y + camera.viewportHeight*camera.zoom*0.5f + period;
		// 1) soft grey stroke (solid, a little wider)
		Gdx.gl.glLineWidth(5f);
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
		shapeRenderer.setColor(FRAME_STROKE);
		shapeRenderer.rect(0,0,w,h);
		shapeRenderer.end();
		// 2) dashed orange on top
		Gdx.gl.glLineWidth(2.5f);
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
		shapeRenderer.setColor(FRAME_ORANGE);
		dashedLine(0,0,w,0,true,dash,period,vl,vr);
		dashedLine(0,h,w,h,true,dash,period,vl,vr);
		dashedLine(0,0,0,h,false,dash,period,vb,vt);
		dashedLine(w,0,w,h,false,dash,period,vb,vt);
		shapeRenderer.end();
		Gdx.gl.glLineWidth(1f);
	}

	/** Dashes of an axis-aligned edge, only the part inside [visLo,visHi]; no allocation. Needs an open Line renderer. */
	private void dashedLine(float x1,float y1,float x2,float y2,boolean horizontal,float dash,float period,float visLo,float visHi){
		float a = horizontal ? x1 : y1, b = horizontal ? x2 : y2;
		float lo = Math.max(a,visLo), hi = Math.min(b,visHi);
		if(hi <= lo || period <= 0f) return;
		float d = a + (float)Math.floor((lo - a) / period) * period;
		for(; d < hi; d += period){
			float s = Math.max(d,a), e = Math.min(d + dash,b);
			if(e <= s) continue;
			if(horizontal) shapeRenderer.line(s,y1,e,y1);
			else shapeRenderer.line(x1,s,x1,e);
		}
	}

	public Stage getUiStage(){
		return UiStage;
	}
	
	public void setGridSize(float sizeX,float sizeY){
		this.gridSizeX = sizeX;
		this.gridSizeY = sizeY;
	}
	
	@Override
	public float getWidth() {
		return getLogicWidth();
	}
	
	@Override
	public float getHeight() {
		return getLogicHeight();
	}
	
	public boolean setTouchMode(TOUCHMODE mode) {
		lockX = false;
		lockY = false;
		if(touch_mode == mode) return true;
		if (selectedActor == null) {
			touch_mode = TOUCHMODE.GRID;
			return false;
		}
		touch_mode = mode;
		return true;
	}
	
	public void setLockX(boolean b){
		lockX = b;
	}
	
	public void setLockY(boolean b){
		lockY = b;
	}
	
	public enum TOUCHMODE {
		MOVE, SCALE, GRID, ROTATE
	}
	
	public OnPickListener onPickListener;
	public OrienationChangeListener orienationChangeListener;
	
	public void setOrienationChangeListener(OrienationChangeListener changeListener){
		this.orienationChangeListener = changeListener;
	}
	
	public void setOnPick(OnPickListener pick){
		if(pick == null) return;
		pointPicker.setOnPickListener(pick);
		addActor(pointPicker);
		onPickListener = pick;
	}

	public interface EditorListener {
		public void onUpdateUndoRedo();
		public void onBodySelected();
	}
	
	@Override
	public void dispose() {
		if(e3d!=null){ try { e3d.dispose(); } catch(Throwable ignored){} e3d = null; }
		loadToken++;
		loadingScene = false;
		super.dispose();
		debugRenderer.dispose();
		rayHandler.dispose();
		for(Actor actor:getActors())
			if(actor instanceof Disposable)
				((Disposable)actor).dispose();
	}
	
	public interface OnPickListener {
		public void onPick(float x,float y);
	}
	
	public interface OrienationChangeListener {
		public void onChange(boolean isLandscape);
	}
}