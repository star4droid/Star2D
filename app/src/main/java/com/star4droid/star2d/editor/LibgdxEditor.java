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
import com.star4droid.star2d.editor.utils.AddPopup;
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

public class LibgdxEditor extends Stage {
	public static LibgdxEditor currentEditor = null;
	ShapeRenderer shapeRenderer;
	ControlLayer controlLayer;
	World world = new World(new Vector2(0,-9.8f),true);
	Box2DDebugRenderer debugRenderer;
	EditorListener editorListener;
	Matrix4 rendererMatrix;
	PropertySet<String,Object> editorConfig;
	boolean AUTO_SAVE = false,LANDSCAPE = true,newUndoRedoSystem = true;
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
	float prevDistance = -1,startAngle=0,ratioScale =1,gridSizeX=50,logicalWidth=1600,logicalHeight=720,gridSizeY=50;
	
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
	
	private void init(){
		debugRenderer = new Box2DDebugRenderer();
		shapeRenderer = new ShapeRenderer();
		rayHandler = new RayHandler(world);
		setToCurrentEditor();
		
		//addActor(filePicker);
		pointPicker = new PointPicker();
		rayHandler.setAmbientLight(0.1f, 0.1f, 0.1f, 1f);
		rendererMatrix = shapeRenderer.getProjectionMatrix().cpy();
		cameraController = new CameraController((OrthographicCamera)getCamera());
		updateConfig();
		setupLight();
		
		/*
		CircleItem circleItem = new CircleItem(this);
		circleItem.setName("circle1");
		circleItem.setDefault();
		circleItem.update();
		selectActor(circleItem);
		
		EditorTextItem textItem = new EditorTextItem(this);
		textItem.setName("text1");
		textItem.setDefault();
		textItem.update();
		
		CustomItem customItem = new CustomItem(this);
		customItem.setName("custom1");
		customItem.setDefault();
		customItem.update();
		AddPopup.showAddPopup(this);
		*/
		//((OrthographicCamera)getCamera()).zoom = 0.5f;
	}
	
	public void setFilePicker(FilePicker picker){
		this.filePicker = picker;
	}
	
	public void setControlLayer(ControlLayer layer){
		this.controlLayer = layer;
	}
	
	public void setUiStage(Stage stage){
		this.UiStage = stage;
	}
	
	public void setToastManager(ToastManager manager){
		this.toastManager = manager;
	}
	
	public void toast(String message){
		toast(message,2);
	}
	
	public void toast(String message,int duration){
	    //if(toastManager==null) return;
		toastManager.toFront();
		toastManager.show(message,duration);
	}
	
	public PropertiesHolder getPropertiesHolder(){
		return propertiesHolder;
	}
	
	public FilePicker getFilePicker(boolean show){
		
		if(filePicker==null) return null;
		if(filePicker.getStage()==null || filePicker.getStage() != UiStage)
			if(UiStage!=null)
				UiStage.addActor(filePicker);
					//else addActor(filePicker);
		if(show) {
			filePicker.setVisible(true);
			//filePicker.setPosition(getWidth() - filePicker.getWidth()*0.5f,getHeight() - filePicker.getHeight()*0.5f);
		}
		return filePicker;
	}
	
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
	
	@Override
	public boolean touchUp(int arg0, int arg1, int arg2, int arg3) {
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
			EditorAction.propertiesChanged(this,getSelectedActor().getName(),touch_mode == TOUCHMODE.ROTATE ? "rotation" : (touch_mode == TOUCHMODE.SCALE ? "scale" : "position"),endTransform,startTransform).updateEditorProperties().updateItemProperties();
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
	
	@Override
	public boolean touchDown(int arg0, int arg1, int arg2, int arg3) {
		if(selectedActor!=null && touch_mode == TOUCHMODE.ROTATE){
			float centerX = selectedActor.getX() + selectedActor.getWidth() / 2.0f,
					centerY = selectedActor.getY() + selectedActor.getHeight() / 2.0f;
			double angle = Math.atan2(Gdx.input.getY() - centerY, Gdx.input.getX() - centerX);
			startAngle = (float)Math.toDegrees(angle) - 90;
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
	
	public void setAssetLoader(ProjectAssetLoader assetLoader){
		this.projectAssetLoader = assetLoader;
	}
	
	public ProjectAssetLoader getAssetLoader(){
		return projectAssetLoader;
	}
	
	public World getWorld(){
		return world;
	}
	
	public void loadFromPath() {
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
		load(string);
	}
	
	public EditorListener getEditorListener(){
		return editorListener;
	}
	
	public void setEditorListener(EditorListener listener) {
		editorListener = listener;
	}
	
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
	
	public void centerCamera(){
		cameraController.moveTo(getWidth()*0.5f,getHeight()*0.5f,0.3f);
	}
	
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
				backgroundColor = editorConfig.getColor("sceneColor");
			}
			
			if(editorConfig!=null && !editorConfig.containsKey("logicHeight")){
				editorConfig.put("logicWidth",Gdx.graphics.getWidth());
				editorConfig.put("logicHeight",Gdx.graphics.getHeight());
				saveConfig();
				} else if(editorConfig!=null){
				setLogicalWH(editorConfig.getFloat("logicWidth"),editorConfig.getFloat("logicHeight"));
			}
		} catch(Exception e){
			toast("update config error : "+e.toString());
		}
	}
	
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
		List<Pair<Float, PropertySet<String,Object>>> zValues = new ArrayList<>();
		Array<Actor> childs = getActors();
		for (int x = 0; x < childs.size; x++) {
			try {
				Actor v = childs.get(x);
				if(!(v instanceof EditorItem)) continue;
				PropertySet<String,Object> propertySet = PropertySet.getPropertySet(v);
				if (propertySet == null)
					continue;
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
		
		return bodies;
	}
	
	public void updateChilds() {
		//int count = 0;
		for (int x = 0; x < getChildren().size; x++)
			if (Utils.isEditorItem(getChild(x))){
				Utils.update(getChild(x));
				//count++;
			}
		//Gdx.files.external("logs/count.txt").writeString("updated : "+count+"\n",true);
	}

	public void saveConfig(){
		if(editorConfig!=null)
			Gdx.files.absolute(project.getConfig(scene)).writeString(editorConfig.toString(),false);
	}
	
	public String getName(Actor s) {
		String name = "Item";
		if (s instanceof EditorItem)
			name = ((EditorItem)s).getTypeName();
		
		int x = 1;
		while (findActor(name + x)!=null)
			x++;
		return name + x;
	}
	
	public void setLandscape(boolean b){
		if(b == this.LANDSCAPE) return;
		this.LANDSCAPE = b;
		getConfig().put("or",b ? "":"portrait");
		for(Actor actor:getActors()){
			if(actor instanceof EditorItem)
				((EditorItem)actor).update();
		}
		saveConfig();
	}
	
	public boolean isLandscape(){
		return LANDSCAPE;
	}
	
	public void setLogicalWH(float width,float height){
		logicalWidth = width;
		logicalHeight = height;
		//getConfig().put("logicWidth",width);
		//editorConfig.put("logicHeight",height);
		//saveConfig();
	}
	
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
	
	String currentState = "";

	public String getSaveState() {
		ArrayList<PropertySet<String, Object>> items = new ArrayList<>();
		for (int x = 0; x < getChildren().size; x++) {
			if (!Utils.isEditorItem(getChild(x)))
				continue;
			items.add(PropertySet.getPropertySet(getChild(x)));
		}
		String save = new Gson().toJson(items);
		if (getChildren().size == 0)
			save = "";
		//String last = (undoList.size()>0)?undoList.elementAt(undoList.size()-1):"";
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
				}
				
				if (item == null)
					continue;
				if(propertySet==null){
					Gdx.files.external("logs/load.error.txt").writeString("\nnull props",true);
					continue;
				}
				propsMap.put(propertySet.getString("name"),propertySet);
				//addActor((Actor) item);
				((Actor)item).setName(propertySet.getString("name"));
				item.setProperties(propertySet);
				//Gdx.files.external("logs/bodies.txt").writeString("add : "+propertySet.getString("name")+",json : \n"+propertySet.toString()+"\n\n",true);
				if (selectedActor == null)
					selectActor((Actor) item);
				try {
    				for(Actor actor:getActors()){
    					if(Utils.isEditorItem(actor)){
    						PropertySet set1= PropertySet.getPropertySet(actor);
    						if(!set1.getString("parent").equals("")){
    							if(propsMap.containsKey(set1.getString("parent"))){
    								set1.setParent(propsMap.get(set1.getString("parent")));
    							} else set1.put("parent","");
    						}
    					}
    				}
				} catch(Exception exx){}
				propsMap.clear();
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
			}
		} catch(Exception e){
			e.printStackTrace();
			Gdx.files.external("logs/load.error.txt").writeString(Utils.getStackTraceString(e)+"\n",true);
		}
		//addActor(pointPicker);
	}
	
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
	
	public void showGrids(boolean show){
		SHOW_GRIDS = show;
	}
	
	public float getRatioScale(){
		return ratioScale;
	}
	
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
		handleTouch();
		return super.touchDragged(arg0, arg1, arg2);
	}
	
	private void handleTouch(){
		OrthographicCamera camera = (OrthographicCamera)getCamera();
		if(Gdx.input.isTouched() && !Gdx.input.isTouched(1)){
			PropertySet<String,Object> propertySet = (selectedActor != null && selectedActor instanceof EditorItem) ? ((EditorItem)selectedActor).getPropertySet():null;
			float deltaX = lockY ? 0 : (-Gdx.input.getDeltaX() * camera.zoom * 0.5f);
			float deltaY = lockX ? 0 : (Gdx.input.getDeltaY() * camera.zoom * 0.5f);
			boolean locked = propertySet != null && propertySet.getString("lock").equals("true");
			if(touch_mode == TOUCHMODE.GRID || selectedActor == null || locked || !(selectedActor instanceof EditorItem)){
				//movement
				camera.translate(deltaX,deltaY);
			} else if(touch_mode == TOUCHMODE.MOVE){
				propertySet.put("x",propertySet.getFloat("x")-deltaX);
				propertySet.put("y",propertySet.getFloat("y")+deltaY);
				((EditorItem)selectedActor).update();
				
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
				shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
				shapeRenderer.setColor(Color.YELLOW);
				shapeRenderer.line(ix,iy,centerX,centerY);
				shapeRenderer.end();
				
				float currentAngle = propertySet.containsKey("rotation") ? propertySet.getFloat("rotation") : 0;
				double deltaRotation = angle + startAngle; 
				startAngle = (float)angle;
				angle = currentAngle + deltaRotation;
				while (angle > 360)
					angle -= 360;
				while (angle < 0)
					angle += 360;
				propertySet.put("rotation", angle);
				((EditorItem)selectedActor).update();
				
			} else if(touch_mode == TOUCHMODE.SCALE){
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
            camera.zoom = MathUtils.clamp(camera.zoom, 0.1f, 3f);
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
		if(cameraController.isMoving())
			cameraController.update(Gdx.graphics.getDeltaTime());
		OrthographicCamera camera = (OrthographicCamera)getCamera();
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
		Gdx.gl.glLineWidth(3f);
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
		shapeRenderer.setColor(Color.RED);
		//if landscape draw border with (width,height) and reverse them if its not...
		shapeRenderer.rect(0,0,getLogicWidth(),getLogicHeight());
		shapeRenderer.end();
		Gdx.gl.glLineWidth(1f);
		
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
		
		try {
			rayHandler.setCombinedMatrix(camera.combined,0,0,1,1);
			rayHandler.updateAndRender();
		} catch(Exception e){}
		
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
	
	OnPickListener onPickListener;
	
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
}