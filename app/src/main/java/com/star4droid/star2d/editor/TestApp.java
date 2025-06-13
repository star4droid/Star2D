package com.star4droid.star2d.editor;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.android.AndroidAudio;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.ToastManager;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.editor.ui.FileBrowser;
import com.star4droid.star2d.editor.ui.FilePicker;
import com.star4droid.star2d.editor.ui.ProjectsListStage;
import com.star4droid.star2d.editor.ui.sub.BodyScriptSelector;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.sub.LanguageDialog;
import com.star4droid.star2d.editor.ui.sub.SimpleNote;
import com.star4droid.star2d.editor.ui.sub.inputs.NumberInputDialog;
import com.star4droid.star2d.editor.utils.ThemeLoader;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.LoadingStage;
import com.star4droid.template.Utils.ProjectAssetLoader;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class TestApp implements ApplicationListener {
	LibgdxEditor editor;
	public static TestApp currentApp;
	Project project;
	ToastManager toastManager;
	SimpleNote simpleNote;
	ToastManager mainToastManager;
	boolean landscape = true;
	Runnable whenEditorReady,whenAppReady;
	ProjectAssetLoader projectAssetLoader;
	StageImp stageImp;
	LoadingStage loadingStage;
	int width=-1,height = -1,currentEditorPos = 0;
	Stage UiStage;
	FilePicker filePicker;
	ControlLayer controlLayer;
	Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
	public Preferences preferences;
	InputMultiplexer multiplexer;
	private PropertySet<String,BitmapFont> bitmapFonts = new PropertySet<>();
	ProjectsListStage projectsListStage;
	FileBrowser fileBrowser;
	LibgdxEditor.OrienationChangeListener orienationChangeListener;
	public Array<LibgdxEditor> editors = new Array<>();
	boolean canExitFromProject = true;
	BodyScriptSelector bodyScriptSelector;
	
	public TestApp(){}
	
	public TestApp(Project project){
		this.project = project;
	}
	
	@Override
	public void create() {
		//Gdx.files.external("logs/testapp.txt").writeString("test app created\n"+"_".repeat(10)+"\n",true);
		preferences = Gdx.app.getPreferences("prefs");
		currentApp = this;
		ThemeLoader.loadTheme();
		bodyScriptSelector = new BodyScriptSelector(this);
		simpleNote = new SimpleNote(getTrans("info"),"No Message");
		loadingStage = new LoadingStage();
		uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
		projectsListStage = new ProjectsListStage(this);
		ScreenViewport screenViewport = new ScreenViewport();
		UiStage = new Stage(screenViewport){
			@Override
			public boolean keyDown(int key){
				if(key == 4){
					new ConfirmDialog(getTrans("exit"),getTrans("areYouSure"),ok->{
						if(ok)
							closeProject();
					}).show(UiStage);
					return true;
				}
				return false;
			}
		};
		
		toastManager = new ToastManager(UiStage);
		mainToastManager = new ToastManager(projectsListStage);
		
		//screenViewport.setUnitsPerPixel(1 / 1.5f);
		
		filePicker = new FilePicker().setShowImageIcons(true);
		filePicker.setVisible(false);
		Gdx.input.setInputProcessor(projectsListStage);
		if(this.whenAppReady!=null)
			this.whenAppReady.run();
		if(!preferences.getBoolean("langSelected")){
			new LanguageDialog(this).show(projectsListStage).toFront();
		}
		
	}
	
	public void setOrienation(boolean isLandscape){
		this.landscape = isLandscape;
		this.orienationChangeListener.onChange(isLandscape);
		
		boolean sc1 = false;
		final Array<LibgdxEditor> cloneArray = new Array<>();
		cloneArray.addAll(editors);
		
		for(LibgdxEditor editor:editors){
			editor.setLandscape(isLandscape);
			if(editor.getScene().equals("scene1")) sc1 = true;
		}
		PropertySet<String,Object> propertySet=null;
		if(!sc1){
			propertySet=PropertySet.getFrom(Gdx.files.absolute(project.getConfig("scene1")).readString());
			propertySet.put("or",isLandscape?"":"portrait");
			Gdx.files.absolute(project.getConfig("scene1")).writeString(propertySet.toString(),false);
		}
		if(project!=null && preferences.getBoolean("AutoSave",true))
			for(LibgdxEditor editor:editors)
				project.save(editor);
		
		editors.clear();
		Timer.schedule(new Timer.Task(){
			@Override
			public void run() {
				for(LibgdxEditor editor:cloneArray){
        			openSceneInNewEditor(editor.getScene());
        			editor.dispose();
        		}
        		setCurrentEditor(currentEditorPos);
        		toast("orientation set to "+(isLandscape?"landscape":"portrait"));
			}
		},1.5f);
		
		// closeProject(false);
		// if(propertySet!=null)
			// openProject(project,propertySet);
		// else openProject(project);
	}
	
	public SimpleNote getSimpleNote(){
		return simpleNote;
	}
	
	public void openProject(Project project){
	    try {
		    PropertySet<String,Object> propertySet=PropertySet.getFrom(Gdx.files.absolute(project.getConfig("scene1")).readString());
		    openProject(project,propertySet);
		} catch(Exception ex){
		    openProject(project,null);
		}
	}
	
	public BodyScriptSelector getBodyScriptSelector(){
		return bodyScriptSelector;
	}
	
	public void openProject(Project project,PropertySet<String,Object> propertySet){
	    this.project = project;
		canExitFromProject = false;
		landscape = true;
		if(fileBrowser!=null)
			fileBrowser.setVisible(false);
		try {
			String or = propertySet.getString("or");
			landscape = or.equals("") || or.equals("landscape");
		} catch(Exception e){}
		orienationChangeListener.onChange(landscape);
		//if the assets loaded in less than 1sec, wait 1.5sec
		//Long time = System.currentTimeMillis();
		if(projectAssetLoader == null)
			projectAssetLoader = new ProjectAssetLoader(new com.star4droid.star2d.Helpers.Project(project.getPath()));
		else {
			projectAssetLoader.clear();
			projectAssetLoader.load(new com.star4droid.star2d.Helpers.Project(project.getPath()));
		}
		projectAssetLoader.setAssetsLoadListener(()->{
			projectAssetLoader.setAssetsLoadListener(null);
			if(!VisUI.isLoaded()) return;
			if(!landscape)
				onLoad(project);
			else {
				Timer.schedule(new Timer.Task(){
					@Override
					public void run() {
						onLoad(project);
					}
				},1.5f);
			}
		});
	}
	
	public ProjectsListStage getProjectsStage(){
		return projectsListStage;
	}
	
	public void setWhenAppReady(Runnable runnable){
		this.whenAppReady = runnable;
		if(filePicker!=null && whenAppReady!=null)
				whenAppReady.run();
	}
	
	private void onLoad(Project project){
		editors.clear();
		UiStage.clear();
		toastManager = new ToastManager(UiStage);
		editor = new LibgdxEditor(project,"scene1",projectAssetLoader);
		editor.enableAutoSave(preferences.getBoolean("AutoSave",true));
		editor.loadFromPath();
		//editor.setOrienationChangeListener(orienationChangeListener);
		editor.setToastManager(toastManager);
		editor.setLandscape(landscape);
		editor.setFilePicker(filePicker);
		if(controlLayer==null)
	    	controlLayer = new ControlLayer(this);
		UiStage.addActor(controlLayer);
		editor.setControlLayer(controlLayer);
		controlLayer.getJointsList().refresh();
		editor.setUiStage(UiStage);
		if(fileBrowser==null)
			fileBrowser = new FileBrowser(project.getPath(),projectAssetLoader).setToastManager(toastManager);
		fileBrowser.setAssetLoader(projectAssetLoader);
		fileBrowser.setVisible(false);
		UiStage.addActor(fileBrowser);
		fileBrowser.setRootDir(project.getPath());
		/*
		try {
			String or = editor.getConfig().getString("or");
			landscape = or.equals("") || or.contains("landscape");
		} catch(Exception e){}
		editor.setLandscape(landscape);
		*/
		if(whenEditorReady!=null)
		    whenEditorReady.run();
		multiplexer = new InputMultiplexer();
		multiplexer.addProcessor(UiStage);
		multiplexer.addProcessor(editor);
		Gdx.input.setCatchKey(4,true);
		Gdx.input.setInputProcessor(multiplexer);
		editor.getFilePicker(false);
		editor.setTouchMode(getTouchMode(controlLayer.getTouchMode()));
		controlLayer.getBodiesList().update();
		if(width != -1)
			resize(width,height);
		editors.add(editor);
		controlLayer.tabsItem.refresh();
		canExitFromProject = true;
	}
	
	public void openSceneInNewEditor(String scene){
		int pos = 0;
		for(LibgdxEditor ed:editors){
			//if the scene already opened, switch to it...
			if(ed.getScene().toLowerCase().equals(scene.toLowerCase())){
				setCurrentEditor(pos);
				return;
			}
			pos++;
		}
		LibgdxEditor editor = new LibgdxEditor(project,scene,projectAssetLoader);
		editor.enableAutoSave(preferences.getBoolean("AutoSave",true));
		editor.setEditorListener(editor.getEditorListener());
		//editor.setOrienationChangeListener(orienationChangeListener);
		editor.setProperites(this.editor.getPropertiesHolder());
		editor.loadFromPath();
		editor.setToastManager(toastManager);
		editor.setFilePicker(filePicker);
		editor.setTouchMode(getTouchMode(controlLayer.getTouchMode()));
		editor.setControlLayer(controlLayer);
		controlLayer.getJointsList().refresh();
		editor.setUiStage(UiStage);
		
		/*
		try {
			landscape = editor.getConfig().getString("or").equals("");
		} catch(Exception e){}
		*/
		editor.setLandscape(this.editor.isLandscape());
		//if(whenEditorReady!=null)
			//whenEditorReady.run();
		
		//editor.getFilePicker(false);
		if(width != -1){
			resize(width,height);
		}
		editors.add(editor);
		setCurrentEditor(editors.size - 1);
	}
	
	public LibgdxEditor.TOUCHMODE getTouchMode(String mode){
		switch(mode){
			case "grid":
				return LibgdxEditor.TOUCHMODE.GRID;
			case "move":
				return LibgdxEditor.TOUCHMODE.MOVE;
			case "scale":
				return LibgdxEditor.TOUCHMODE.SCALE;
		}
		return LibgdxEditor.TOUCHMODE.ROTATE;
	}
	
	public static TestApp getCurrentApp(){
		return currentApp;
	}
	
	public BitmapFont getFont(String path){
		while(path.contains("//")){
			path = path.replace("//","/");
		}
		if(!path.startsWith("/")) path = "/"+path;
		if(bitmapFonts.containsKey(path))
			return (BitmapFont)bitmapFonts.get(path);
		BitmapFont font = com.star4droid.template.Utils.Utils.generateFontFrom(Gdx.files.absolute(path));
		bitmapFonts.put(path,font);
		return font;
	}
	
	public void setOrienationChanger(LibgdxEditor.OrienationChangeListener changeListener){
		this.orienationChangeListener = changeListener;
		//for(LibgdxEditor editor:editors)
			//editor.setOrienationChangeListener(changeListener);
	}
	
	public ControlLayer getControlLayer(){
		return controlLayer;
	}
	
	public void setControlLayer(ControlLayer layer){
		this.controlLayer = layer;
	}
	
	public void setCurrentEditor(int pos){
		//if(currentEditorPos == pos) return;
		if(pos < editors.size){
			LibgdxEditor prev = this.editor;
			multiplexer.removeProcessor(editor);
			editor = editors.get(pos);
			editor.enableAutoSave(preferences.getBoolean("AutoSave",true));
			editor.setLandscape(prev.isLandscape());
			multiplexer.addProcessor(editor);
			Gdx.input.setInputProcessor(multiplexer);
			currentEditorPos = pos;
			controlLayer.tabsItem.refresh();
			controlLayer.getJointsList().refresh();
			controlLayer.getBodiesList().update();
		} else if(editors.size > 0) {
			toast("Invalid Selection!\nsize : "+editors.size+",pos : "+pos);
		} else toast("There\'s no editors! (this shouldn't happen)");
	}
	
	public boolean isProjectOpened(){
		return editors.size > 0;
	}
	
	public void closeProject(){
	    closeProject(true);
	}
	
	public void closeProject(boolean changeOrienation){
		if(!canExitFromProject){
			toast("please wait...");
			return;
		}
		bitmapFonts.clear();
		for(LibgdxEditor editor:editors)
			editor.dispose();
		this.editor = null;
		if(projectAssetLoader!=null){
			projectAssetLoader.setAssetsLoadListener(null);
			projectAssetLoader.dispose();
			projectAssetLoader = null;
		}
		
		/*if(controlLayer!=null){
			controlLayer = null;
		}*/
		
		editors.clear();
		Gdx.input.setCatchKey(4,false);
		Gdx.input.setInputProcessor(projectsListStage);
		if(changeOrienation) orienationChangeListener.onChange(false);
	}
	
	public void updateFont(String path){
		if(path.equals("")) return;
		while(path.contains("//"))
			path = path.replace("//","/");
		
		if(!path.startsWith("/"))
			path = "/"+path;
			
		while(path.endsWith("/"))
			path.substring(0,path.length()-1);
		
		if(bitmapFonts.containsKey(path))
			bitmapFonts.remove(path);
		else {
			String pathName = path.contains("/") ? path.substring(path.lastIndexOf("/"),path.length()) : path;
			bitmapFonts.forEach((key,value)->{
				try {
					String name = value.toString();
					name = name.contains("/") ? name.substring(name.lastIndexOf("/"),name.length()) : name;
					if(name.equals(pathName))
						bitmapFonts.remove(name);
				} catch(Exception ex){}
			});
		}
	}
	
	public void updateFonts(){
		bitmapFonts.clear();
	}
	
	public void setEditorReadyAction(Runnable runnable){
		whenEditorReady = runnable;
		//already ready, call directly
		if(editor!=null && whenEditorReady!=null)
			whenEditorReady.run();
	}

	@Override
	public void resize(int width, int height) {
		this.width = width;
		this.height = height;
		if(projectsListStage!=null){
			projectsListStage.getViewport().update(width,height,true);
			projectsListStage.getViewport().setScreenWidth(Gdx.graphics.getWidth());
	    	projectsListStage.getViewport().setScreenHeight(Gdx.graphics.getHeight());
		}
		if(UiStage!=null){
			UiStage.getViewport().update(width,height,true);
			UiStage.getViewport().setScreenWidth(Gdx.graphics.getWidth());
	    	UiStage.getViewport().setScreenHeight(Gdx.graphics.getHeight());
		}
		try {
			for(LibgdxEditor editor:editors){
				editor.getViewport().update(width,height);
				editor.getViewport().setScreenWidth(Gdx.graphics.getWidth());
	        	editor.getViewport().setScreenHeight(Gdx.graphics.getHeight());
			}
		} catch(Exception e){}
		
		if(stageImp!=null)
			stageImp.resize(width,height);
	}
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public Stage getUiStage(){
		return UiStage;
	}
	
	public FileBrowser getFileBrowser(){
		return fileBrowser;
	}
	
	public ToastManager getToastManager(){
		return toastManager;
	}
	
	public void toast(String message,int duration){
		//if(toastManager==null) return;
		ToastManager currentManager = (editors.size == 0 && projectAssetLoader == null) ? mainToastManager : toastManager;
		currentManager.toFront();
		currentManager.show(message,duration);
	}
	
	public void toast(String message){
		toast(message,2);
	}
	
	public boolean isPlaying(){
		return stageImp!=null;
	}
	
	public void play(String path,String scene){
		play(StageImp.getFromDex(path,scene,null,null));
	}
	
	public void play(StageImp stage){
		Gdx.app.postRunnable(()->{
			if(stage!=null){
				stage.create();
				Thread.setDefaultUncaughtExceptionHandler((thread,exc)->{
					Gdx.app.postRunnable(()->{
						play(null);
						fileBrowser.showText("Error :\n"+Utils.getStackTraceString(exc));
						Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
					});
				});
				Gdx.input.setInputProcessor(stage.multiplexer);
				if(width!=-1 && height!=-1)
					stage.resize(width,height);
			} else {
				// this.stageImp is the current playing stage...
			    if(this.stageImp!=null){
			        this.stageImp.pause();
					if(Gdx.app.getAudio() instanceof AndroidAudio)
						((AndroidAudio)Gdx.app.getAudio()).pause();
					
					this.stageImp.GameStage.dispose();
					this.stageImp.UiStage.dispose();
				}
				Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
				Gdx.input.setCatchKey(4,true);
				Gdx.input.setInputProcessor(multiplexer);
			}
			this.stageImp = stage;
		});
	}
	
	//this called from Editor.java which is the previous editor (useless)
	public void setLandscape(boolean isLandscape){
		if(editor == null){
			landscape = isLandscape;
		} else editor.setLandscape(isLandscape);
	}

	@Override
	public void render() {
		if(!VisUI.isLoaded())
			ThemeLoader.loadTheme();
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		Gdx.gl.glClearColor(0,0,0,0);
		if(projectAssetLoader!=null)
			if(!(projectAssetLoader.update()||canExitFromProject)){
				loadingStage.setProgress(projectAssetLoader.getProgress()*100);
				loadingStage.act();
				loadingStage.draw();
				return;
			}
		
		if(editors.size == 0 && projectAssetLoader == null){
			projectsListStage.act();
			projectsListStage.draw();
			return;
		}
		
		if(stageImp!=null){
			try {
				stageImp.render();
			} catch(Exception e){
				if(!isPlaying())
					Gdx.files.external("logs/render.error.txt").writeString(Utils.getStackTraceString(e)+"\n"+"_".repeat(10)+"\n",false);
				else {
					play(null);
					fileBrowser.showText("Error:\n"+Utils.getStackTraceString(e));
				}
			}
			return;
		} else {
			if(editor!=null){
				editor.act();
				editor.draw();
			}
			
			if(UiStage!=null){
				UiStage.act();
				UiStage.draw();
			}
			
		}
	}

	@Override
	public void pause() {}

	@Override
	public void resume() {}
	
	@Override
	public void dispose() {
		if(VisUI.isLoaded())
			VisUI.dispose();
		
		if(projectAssetLoader!=null){
			projectAssetLoader.setAssetsLoadListener(null);
			projectAssetLoader.dispose();
		}
		if(fileBrowser!=null)
			fileBrowser.dispose();
		
		if(stageImp!=null)
			stageImp.dispose();
		currentApp = null;
		NumberInputDialog.dispose();
		for(LibgdxEditor editor:editors)
			if(editor!=null)
				editor.dispose();
	}
	
}