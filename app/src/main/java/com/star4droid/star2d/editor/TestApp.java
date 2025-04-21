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
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.sub.inputs.NumberInputDialog;
import com.star4droid.star2d.editor.utils.ThemeLoader;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.LoadingStage;
import com.star4droid.template.Utils.ProjectAssetLoader;

public class TestApp implements ApplicationListener {
	LibgdxEditor editor;
	public static TestApp currentApp;
	Project project;
	ToastManager toastManager;
	boolean landscape = true;
	Runnable whenEditorReady,whenAppReady;
	ProjectAssetLoader projectAssetLoader;
	StageImp stageImp;
	LoadingStage loadingStage;
	int width=-1,height = -1,currentEditorPos = 0;
	Stage UiStage;
	FilePicker filePicker;
	ControlLayer controlLayer;
	public Preferences preferences;
	InputMultiplexer multiplexer;
	private PropertySet<String,BitmapFont> bitmapFonts = new PropertySet<>();
	ProjectsListStage projectsListStage;
	FileBrowser fileBrowser;
	LibgdxEditor.OrienationChangeListener orienationChangeListener;
	public Array<LibgdxEditor> editors = new Array<>();
	boolean canExitFromProject = true;
	
	public TestApp(){}
	
	public TestApp(Project project){
		this.project = project;
	}
	
	@Override
	public void create() {
		//Gdx.files.external("logs/testapp.txt").writeString("test app created\n"+"_".repeat(10)+"\n",true);
		ThemeLoader.loadTheme();
		currentApp = this;
		preferences = Gdx.app.getPreferences("prefs");
		loadingStage = new LoadingStage();
		projectsListStage = new ProjectsListStage(this);
		ScreenViewport screenViewport = new ScreenViewport();
		UiStage = new Stage(screenViewport){
			@Override
			public boolean keyDown(int key){
				if(key == 4){
					new ConfirmDialog("Exit","Are you sure ?",ok->{
						if(ok)
							closeProject();
					}).show(UiStage);
					return true;
				}
				return false;
			}
		};
		
		toastManager = new ToastManager(UiStage);
		
		//screenViewport.setUnitsPerPixel(1 / 1.5f);
		
		filePicker = new FilePicker().setShowImageIcons(true);
		filePicker.setVisible(false);
		Gdx.input.setInputProcessor(projectsListStage);
		if(this.whenAppReady!=null)
			this.whenAppReady.run();
		
	}
	
	public void setOrienation(boolean isLandscape){
		this.landscape = landscape;
		this.orienationChangeListener.onChange(isLandscape);
		//Array<LibgdxEditor> editorsTemp = new Array<>();
		boolean sc1 =false;
		for(LibgdxEditor editor:editors){
			setLandscape(isLandscape);
			if(editor.getScene().equals("scene1")) sc1 = true;
		}
		PropertySet<String,Object> propertySet=null;
		if(!sc1){
			propertySet=PropertySet.getFrom(Gdx.files.absolute(project.getConfig("scene1")).readString());
			propertySet.put("or",isLandscape?"":"portrait");
			Gdx.files.absolute(project.getConfig("scene1")).writeString(propertySet.toString(),false);
		}
		closeProject(false);
		if(propertySet!=null)
			openProject(project,propertySet);
		else openProject(project);
	}
	
	public void openProject(Project project){
	    try {
		PropertySet<String,Object> propertySet=PropertySet.getFrom(Gdx.files.absolute(project.getConfig("scene1")).readString());
		    openProject(project,propertySet);
		} catch(Exception ex){
		    openProject(project,null);
		}
	}
	
	public void openProject(Project project,PropertySet<String,Object> propertySet){
	    this.project = project;
		canExitFromProject = false;
		landscape = true;
		try {
			String or = propertySet.getString("or");
			landscape = or.equals("") || or.equals("landscape");
		} catch(Exception e){}
		orienationChangeListener.onChange(landscape);
		//if the assets loaded in less than 1sec, wait 1.5sec
		//Long time = System.currentTimeMillis();
		projectAssetLoader = new ProjectAssetLoader(new com.star4droid.star2d.Helpers.Project(project.getPath()));
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
		editor.setUiStage(UiStage);
		fileBrowser = new FileBrowser(project.getPath(),projectAssetLoader).setToastManager(toastManager);
		fileBrowser.setVisible(false);
		UiStage.addActor(fileBrowser);
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
		
		editor.setControlLayer(controlLayer);
		editor.setUiStage(UiStage);
		
		/*
		try {
			landscape = editor.getConfig().getString("or").equals("");
		} catch(Exception e){}
		*/
		editor.setLandscape(landscape);
		//if(whenEditorReady!=null)
			//whenEditorReady.run();
		
		//editor.getFilePicker(false);
		if(width != -1){
			resize(width,height);
		}
		editors.add(editor);
		setCurrentEditor(editors.size - 1);
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
			multiplexer.removeProcessor(editor);
			editor = editors.get(pos);
			editor.enableAutoSave(preferences.getBoolean("AutoSave",true));
			editor.setLandscape(landscape);
			multiplexer.addProcessor(editor);
			Gdx.input.setInputProcessor(multiplexer);
			currentEditorPos = pos;
			controlLayer.tabsItem.refresh();
			controlLayer.getBodiesList().update();
		} else toast("Invalid Selection!\nsize : "+editors.size+",pos : "+pos);
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
		
		if(controlLayer!=null){
			controlLayer = null;
		}
		
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
		if(projectsListStage!=null)
			projectsListStage.getViewport().update(width,height,true);
		if(UiStage!=null)
			UiStage.getViewport().update(width,height,true);
		
		try {
			for(LibgdxEditor editor:editors)
				editor.getViewport().update(width,height);
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
		toastManager.toFront();
		toastManager.show(message,duration);
	}
	
	public void toast(String message){
		toast(message,2);
	}
	
	public boolean isPlaying(){
		return stageImp!=null;
	}
	
	public void play(String path,String scene){
		//TODO : Show error if something went wrong...
		play(StageImp.getFromDex(path,scene,null,null));
	}
	
	public void play(StageImp stageImp){
		Gdx.app.postRunnable(()->{
			if(stageImp!=null){
				stageImp.create();
				Gdx.input.setInputProcessor(stageImp.multiplexer);
				if(width!=-1 && height!=-1)
					stageImp.resize(width,height);
			} else {
			    if(this.stageImp!=null){
			        this.stageImp.pause();
					if(Gdx.app.getAudio() instanceof AndroidAudio)
						((AndroidAudio)Gdx.app.getAudio()).pause();

					this.stageImp.dispose();
				}
				Gdx.input.setCatchKey(4,true);
				Gdx.input.setInputProcessor(multiplexer);
			}
			this.stageImp = stageImp;
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
				Gdx.files.external("logs/render.error.txt").writeString(Utils.getStackTraceString(e)+"\n"+"_".repeat(10)+"\n",false);
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