package com.star4droid.star2d.editor;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Preferences;
import com.star4droid.template.PlatformServices;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.ToastManager;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.star2d.Helpers.Project;
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
import com.star4droid.star2d.editor.ui.scripting.NodeEditorApp;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class TestApp implements ApplicationListener {
	LibgdxEditor editor;
	public static TestApp currentApp;
	NodeEditorApp visualScripting;
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
	public Runnable openDonate;
	ControlLayer controlLayer;
	Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
	public Preferences preferences;
	InputMultiplexer multiplexer;
	private PropertySet<String,BitmapFont> bitmapFonts = new PropertySet<>();
	ProjectsListStage projectsListStage;
	FileBrowser fileBrowser;
	LibgdxEditor.OrienationChangeListener orienationChangeListener;
	public Array<LibgdxEditor> editors = new Array<>();
	boolean canExitFromProject = true, showVisual = false;
	BodyScriptSelector bodyScriptSelector;
	public TestApp(){}
	
	public TestApp(Project project){
		this.project = project;
	}
	
	@Override
	public void create() {
		//Gdx.files.external("logs/testapp.txt").writeString("test app created\n"+"_".repeat(10)+"\n",true);
		currentApp = this;
		preferences = Gdx.app.getPreferences("prefs");
		ThemeLoader.loadTheme();
		visualScripting = new NodeEditorApp();
		visualScripting.create();
		
		orienationChangeListener = new LibgdxEditor.OrienationChangeListener() {
			@Override
			public void onChange(boolean isLandscape) {
				System.out.println("Orientation changed to: " + (isLandscape ? "Landscape" : "Portrait"));
			}
		};
		
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
        System.out.println("TESTAPP_INIT: Setting InputProcessor to projectsListStage");
		Gdx.input.setInputProcessor(projectsListStage);
		if(this.whenAppReady!=null)
			this.whenAppReady.run();
		if(!preferences.getBoolean("langSelected")){
			new LanguageDialog(this).show(projectsListStage).toFront();
		}
		
	}
	
	public void loadVisual(String file, String all){
	    visualScripting.loadFrom(file);
	    visualScripting.setHints(all);
	    showVisualScripting(true);
	}
	
	public void showVisualScripting(boolean bool){
	    this.showVisual = bool;
	    if(bool)
	        visualScripting.setInput();
	    else {
	        Gdx.input.setCatchKey(4,true);
		    Gdx.input.setInputProcessor(multiplexer);
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
        		for(LibgdxEditor ed:editors)
        		    ed.setLandscape(isLandscape);
        		setCurrentEditor(currentEditorPos);
        		toast("orientation set to "+(editor.isLandscape()?"landscape":"portrait"));
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
						try {
						    onLoad(project);
						} catch(Exception ex){
						    Gdx.files.external("logs/load.error.txt").writeString(ex.toString(),false);
						}
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
		controlLayer.getVarsItem().setFileHandle(Gdx.files.absolute(project.getVariables("scene1")));
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
        System.out.println("TESTAPP_ONLOAD: Setting InputProcessor to multiplexer");
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
		controlLayer.getVarsItem().setFileHandle(Gdx.files.absolute(project.getVariables(scene)));
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
	    try {
    		while(path.contains("//")){
    			path = path.replace("//","/");
    		}
    		if(!path.startsWith("/")) path = "/"+path;
    		if(bitmapFonts.containsKey(path))
    			return (BitmapFont)bitmapFonts.get(path);
    		BitmapFont font = com.star4droid.template.Utils.Utils.generateFontFrom(Gdx.files.absolute(path));
    		bitmapFonts.put(path,font);
    		return font;
		} catch(Exception ex){
		    return null;
		}
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
        System.out.println("TESTAPP_CLOSE: Setting InputProcessor to projectsListStage");
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
		if(visualScripting!=null)
		    visualScripting.resize(width, height);
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
        System.out.println("TestApp.play(path, scene) called with: " + path + ", " + scene);
        StageImp.StageLoaderParameters params = new StageImp.StageLoaderParameters();
        params.dexPath = path;
        
        // ALWAYS create a fresh loader to ensure parameters (Project, etc.) are up to date.
        // We do NOT use StageImp.mainLoader here because it might hold stale state.
	    try {
	        String projectPath = path;
	        try {
	            if(projectPath != null && !projectPath.isEmpty()) {
	                java.io.File f = new java.io.File(projectPath);
	                if(f.exists()) {
	                    if(f.getName().endsWith(".dex")) f = f.getParentFile().getParentFile();
	                    else if(f.getName().equals("dex")) f = f.getParentFile();
	                    projectPath = f.getAbsolutePath();
	                }
	            }
	        } catch(Exception e) {
	            System.err.println("Path resolution error: " + e.getMessage());
	        }
	        
	        if(projectPath == null || projectPath.isEmpty()) {
	             if(project != null) projectPath = project.getPath();
	             else projectPath = Gdx.files.local(".").file().getAbsolutePath();
	        }
	        
	        System.out.println("Resolved Project Path: " + projectPath);
	        // Debug logging
			Gdx.files.external("pppp.txt").writeString(projectPath,false);
	        
            com.star4droid.star2d.Helpers.Project helperProject = new com.star4droid.star2d.Helpers.Project(projectPath);
            java.io.File filesDir = new java.io.File(projectPath); 
            java.io.File cacheDir = Gdx.files.local("code_cache").file();
            if(!filesDir.exists()) filesDir.mkdirs();
            if(!cacheDir.exists()) cacheDir.mkdirs();
            
            // Use the main loader from StageImp (set by platform-specific launcher)
            if(StageImp.mainLoader != null) {
                params.loader = StageImp.mainLoader;
            } else {
                throw new RuntimeException("No scene loader available. Platform launcher must set StageImp.mainLoader.");
            }
        } catch(Exception ex) {
            System.err.println("Failed to create loader: " + ex.getMessage());
            ex.printStackTrace();
            toast("Failed to initialize loader: " + ex.getMessage());
            return;
        }
		
		try {
		    StageImp stage = StageImp.loadScene(scene,null,null,params);
		    // Fix back button: callback to Editor instead of exiting app
		    stage.setFinishFunc(s -> {
		        Gdx.app.postRunnable(() -> play(null));
		    });
		    play(stage);
		} catch (Exception e) {
		    System.out.println("Error loading scene: " + e.getMessage());
		    e.printStackTrace();
		    toast("Error loading scene: " + e.getMessage());
		}
	}
	
	public void play(StageImp stage){
	    System.out.println("TestApp.play(StageImp) called with: " + (stage != null ? "valid stage" : "null"));
		Gdx.app.postRunnable(()->{
			if(stage!=null){
			    System.out.println("Starting stage...");
				stage.create();
				System.out.println("Stage UI : "+(stage.UiStage!=null)+", GameStage : "+(stage.GameStage!=null));
				if(stage.UiStage==null || stage.GameStage==null){
					toast("Stage UI or GameStage is null!");
					return;
				}
				//Gdx.input.setInputProcessor(stage.UiStage);
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
			    System.out.println("Stopping stage...");
				// this.stageImp is the current playing stage...
			    if(this.stageImp!=null){
			        this.stageImp.pause();
					// Use PlatformServices for audio control
					if(PlatformServices.Holder.get() != null)
						PlatformServices.Holder.get().pauseAudio();
					try {
						this.stageImp.GameStage.dispose();
						this.stageImp.UiStage.dispose();
						this.stageImp.assetLoader.dispose();
						com.badlogic.gdx.utils.Timer.instance().clear();
					} catch(Exception e){
						System.out.println("Error disposing stage: " + e.getMessage());
						Gdx.files.external("logs/dispose.error.txt").writeString(Utils.getStackTraceString(e)+"\n",false);
					}
				}
				Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
				Gdx.input.setCatchKey(4,true);
				Gdx.input.setInputProcessor(multiplexer);
				UiStage.getViewport().update(width, height);
			}
			this.stageImp = stage;
			System.out.println("this.stageImp set to: " + this.stageImp);
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
	    try {
	        renderIn();
	    } catch(Exception ex){
	        Gdx.files.external("logs/renderIn.error.txt").writeString(Utils.getStackTraceString(ex)+"\n",false);
	    }
	}
	
	private void renderIn(){
		if(!VisUI.isLoaded())
			ThemeLoader.loadTheme();
		if(showVisual){
		    visualScripting.render();
		    return;
		}
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
            if(Gdx.input.getInputProcessor() != multiplexer){
                 System.out.println("WARN: InputProcessor is NOT multiplexer! It is: " + Gdx.input.getInputProcessor() + " Editors: " + editors.size);
                 if(multiplexer != null) Gdx.input.setInputProcessor(multiplexer);
            }

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
	    visualScripting.dispose();
		try {
		    ThemeLoader.getOrangeSkin().dispose();
		} catch(Error | Exception ex){}
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