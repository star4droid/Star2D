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
	/** Called (on the GL thread) when a project starts opening / after it was closed. Used by the Android home screen. */
	public Runnable onProjectOpening, onProjectClosed;

	/** Hooks for the native Android editor UI (null = classic LibGDX UI). */
	public interface NativeHooks {
		/** The current scene editor changed (project loaded, tab switched, scene opened). GL thread. */
		void onEditorChanged();
		/** Game started (true) or stopped (false). GL thread. */
		void onPlayStateChanged(boolean playing);
		/** Open a visual script; return true when handled natively. */
		boolean openVisual(String file, String hints);
		/** Show a long text (runtime errors etc.). */
		void showText(String text);
		/** Back key pressed in the editor. */
		void onBackKey();
		/** Scene loading progress of the current editor: 0..1, or a negative value when finished. GL thread. */
		void onSceneLoading(float progress);
		/**
		 * A full-screen LibGDX tool (the font editor) opened (true) or closed (false): the native chrome should hide itself while it is open.
		 * {@code closer} (only when opening) closes the tool on the GL thread; the Back key calls it. GL thread.
		 */
		default void onFullscreenTool(boolean open, Runnable closer) {}
	}
	public static NativeHooks nativeHooks;

	/** Called (GL thread) at the very end of each frame, once. Used for screenshots. */
	public static volatile Runnable frameHook;
	/** Sees runtime error texts of the game (the remote/AI tools collect them). */
	public static volatile java.util.function.Consumer<String> errorObserver;

	/** The running game (or null). */
	public com.star4droid.template.Items.StageImp getPlayingStage(){
		return stageImp;
	}

	private void showErrorText(String text){
		java.util.function.Consumer<String> eo = errorObserver;
		if(eo != null) try { eo.accept(text); } catch(Throwable ignored){}
		if(nativeHooks != null) nativeHooks.showText(text);
		else fileBrowser.showText(text);
	}
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
		
		bodyScriptSelector = new BodyScriptSelector(this);
		
		simpleNote = new SimpleNote(getTrans("info"),"No Message");
		loadingStage = new LoadingStage();
		uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
		projectsListStage = new ProjectsListStage(this);
		ScreenViewport screenViewport = new ScreenViewport();
		UiStage = new Stage(screenViewport){
			@Override
			public boolean keyDown(int key){
				if(key == 4 && nativeHooks != null){
					nativeHooks.onBackKey();
					return true;
				}
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
	
	public void loadVisual(String file, String all){
	    if(nativeHooks != null && nativeHooks.openVisual(file, all)) return;
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
		    com.badlogic.gdx.files.FileHandle first = Gdx.files.absolute(project.getConfig("scene1"));
		    if(!first.exists()){
		        // scene1 was deleted or renamed: the orientation of the project is the one of any other scene
		        java.io.File[] all = new java.io.File(project.getConfig("scene1")).getParentFile().listFiles();
		        if(all != null) for(java.io.File f : all) if(f.isFile() && !f.getName().startsWith("__")){ first = Gdx.files.absolute(f.getAbsolutePath()); break; }
		    }
		    PropertySet<String,Object> propertySet=PropertySet.getFrom(first.readString());
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
		if(onProjectOpening!=null)
			onProjectOpening.run();
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
		cleanTempScenes(project);
		editors.clear();
		UiStage.clear();
		toastManager = new ToastManager(UiStage);
		boolean hadConfig = new java.io.File(project.getConfig("scene1")).exists();
		editor = new LibgdxEditor(project,"scene1",projectAssetLoader);
		editor.enableAutoSave(preferences.getBoolean("AutoSave",true));
		editor.loadFromPath();
		//editor.setOrienationChangeListener(orienationChangeListener);
		editor.setToastManager(toastManager);
		if(hadConfig){
			// the saved config is the truth: never push a guessed orientation (a failed read defaulted to landscape) over it
			landscape = editor.isLandscape();
			if(orienationChangeListener != null) orienationChangeListener.onChange(landscape);
		} else editor.setLandscape(landscape);
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
		Gdx.input.setInputProcessor(multiplexer);
		editor.getFilePicker(false);
		editor.setTouchMode(getTouchMode(controlLayer.getTouchMode()));
		controlLayer.getBodiesList().update();
		if(width != -1)
			resize(width,height);
		editors.add(editor);
		controlLayer.tabsItem.refresh();
		editor.checkLogicSizeHint();
		canExitFromProject = true;
		if(nativeHooks != null) nativeHooks.onEditorChanged();
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
		LibgdxEditor editor = createSceneEditor(scene);
		editors.add(editor);
		setCurrentEditor(editors.size - 1);
	}
	
	/**
	 * Closes the editor of a scene (used for the hidden prefab-edit scene) and goes back to another tab:
	 * the one of "returnScene" when it is open, else the first other one. Does nothing when it is the only editor.
	 */
	public boolean closeSceneEditorQuiet(String scene,String returnScene){
		LibgdxEditor target = null, back = null, firstOther = null;
		for(LibgdxEditor ed:editors){
			if(ed.getScene().equals(scene)) target = ed;
			else {
				if(firstOther == null) firstOther = ed;
				if(returnScene != null && ed.getScene().equals(returnScene)) back = ed;
			}
		}
		if(target == null) return false;
		if(back == null) back = firstOther;
		if(back == null) return false;
		if(this.editor == target) setCurrentEditor(editors.indexOf(back,true));
		editors.removeValue(target,true);
		try { target.dispose(); } catch(Throwable ignored){}
		currentEditorPos = Math.max(0,editors.indexOf(this.editor,true));
		setCurrentEditorQuiet();
		return true;
	}
	
	/** True when a (hidden) prefab-edit scene is open in an editor. */
	public boolean hasTempSceneOpen(){
		for(LibgdxEditor ed:editors)
			if(com.star4droid.star2d.prefab.PrefabStore.isTempScene(ed.getScene())) return true;
		return false;
	}
	
	/** Leftovers of a prefab edit that was cut short (app killed): removed when a project opens. */
	private void cleanTempScenes(Project project){
		try {
			java.io.File[] fs = new java.io.File(project.getScenesPath()).listFiles();
			if(fs == null) return;
			for(java.io.File f:fs){
				if(!com.star4droid.star2d.prefab.PrefabStore.isTempScene(f.getName())) continue;
				try { com.star4droid.star2d.prefab.PrefabOps.discardEdit(project,f.getName()); } catch(Throwable ignored){}
				if(f.exists()) project.deleteScene(f.getName());
			}
		} catch(Throwable ignored){}
	}
	
	/** A loaded (it loads in chunks) editor for a scene, set up like the ones of the other tabs. Not added to the tabs. */
	private LibgdxEditor createSceneEditor(String scene){
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
		editor.setLandscape(this.editor.isLandscape());
		if(width != -1){
			resize(width,height);
		}
		return editor;
	}
	
	// ---------------------------------------------------------------- external changes (git, remote AI)
	
	private static void crcFile(java.util.zip.CRC32 crc, java.io.File f){
		try {
			if(f.isDirectory()){
				java.io.File[] list = f.listFiles();
				if(list == null) return;
				java.util.Arrays.sort(list);
				for(java.io.File c:list){
					crc.update(c.getName().getBytes("UTF-8"));
					crcFile(crc,c);
				}
			} else if(f.isFile()){
				crc.update(java.nio.file.Files.readAllBytes(f.toPath()));
			} else crc.update(0);
		} catch(Exception ignored){}
	}
	
	/** What defines a scene on disk (scene file, config, joints, variables) as one number. */
	public String sceneFingerprint(String scene){
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crcFile(crc,new java.io.File(project.getScenesPath()+scene));
		crcFile(crc,new java.io.File(project.getConfig(scene)));
		crcFile(crc,new java.io.File(project.getJoints(scene)));
		crcFile(crc,new java.io.File(project.getVariables(scene)));
		return Long.toHexString(crc.getValue());
	}
	
	/** Fingerprints of the scenes that are open in tabs. Call after saving, before an operation that may change files. GL thread. */
	public java.util.HashMap<String,String> openSceneFingerprints(){
		java.util.HashMap<String,String> m = new java.util.HashMap<>();
		if(project == null) return m;
		for(LibgdxEditor ed:editors)
			m.put(ed.getScene(),sceneFingerprint(ed.getScene()));
		return m;
	}
	
	/**
	 * Files changed on disk behind the editor's back. Only the scenes that really changed are touched:
	 * the open one is reloaded in place, other changed tabs are closed, everything else is left alone.
	 * With no "before" snapshot every open scene counts as changed. Returns a short report for the user. GL thread.
	 */
	public String applyExternalChanges(java.util.HashMap<String,String> before){
		if(project == null || editors.size == 0 || editor == null) return "";
		// new images etc. that arrived with the change
		try { if(projectAssetLoader != null) projectAssetLoader.load(new com.star4droid.star2d.Helpers.Project(project.getPath())); } catch(Throwable ignored){}
		LibgdxEditor current = this.editor;
		java.util.ArrayList<LibgdxEditor> closed = new java.util.ArrayList<>();
		boolean currentChanged = false, currentGone = false;
		for(LibgdxEditor ed:snapshotEditors()){
			String sc = ed.getScene();
			boolean gone = !new java.io.File(project.getScenesPath()+sc).exists();
			boolean changed = gone || before == null || !before.containsKey(sc) || !before.get(sc).equals(sceneFingerprint(sc));
			if(!changed) continue;
			if(ed == current){
				currentChanged = true;
				currentGone = gone;
			} else closed.add(ed);
		}
		// other changed tabs: close them
		for(LibgdxEditor ed:closed){
			editors.removeValue(ed,true);
			ed.dispose();
		}
		String report = "";
		if(!closed.isEmpty()) report = closed.size()+" tab(s) closed";
		if(currentChanged){
			int pos = editors.indexOf(current,true);
			if(currentGone){
				// the open scene does not exist any more: go to another tab, or start over
				editors.removeValue(current,true);
				if(editors.size == 0){
					editors.add(current); // reloadProject closes (and disposes) everything itself
					reloadProject();
					return "reloaded";
				}
				multiplexer.removeProcessor(current);
				current.dispose();
				this.editor = editors.get(0);
				multiplexer.addProcessor(this.editor);
				currentEditorPos = 0;
				setCurrentEditorQuiet();
			} else {
				LibgdxEditor fresh = createSceneEditor(current.getScene());
				editors.set(pos,fresh);
				multiplexer.removeProcessor(current);
				this.editor = fresh;
				multiplexer.addProcessor(fresh);
				Gdx.input.setInputProcessor(multiplexer);
				currentEditorPos = pos;
				current.dispose();
				setCurrentEditorQuiet();
			}
			report = (report.isEmpty() ? "" : report+", ")+"scene reloaded";
		} else if(!closed.isEmpty()){
			if(!editors.contains(this.editor,true)) {
				this.editor = editors.get(0);
				multiplexer.addProcessor(this.editor);
			}
			currentEditorPos = Math.max(0,editors.indexOf(this.editor,true));
			setCurrentEditorQuiet();
		} 
		return report;
	}
	
	private java.util.ArrayList<LibgdxEditor> snapshotEditors(){
		java.util.ArrayList<LibgdxEditor> l = new java.util.ArrayList<>();
		for(LibgdxEditor e:editors) l.add(e);
		return l;
	}
	
	private void setCurrentEditorQuiet(){
		controlLayer.tabsItem.refresh();
		controlLayer.getJointsList().refresh();
		controlLayer.getBodiesList().update();
		controlLayer.getVarsItem().setFileHandle(Gdx.files.absolute(project.getVariables(editor.getScene())));
		if(nativeHooks != null) nativeHooks.onEditorChanged();
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
			controlLayer.getVarsItem().setFileHandle(Gdx.files.absolute(project.getVariables(editor.getScene())));
			if(nativeHooks != null) nativeHooks.onEditorChanged();
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

	/** Closes and opens the current project again (its files changed on disk, e.g. after a git pull). */
	public boolean reloadProject(){
		if(project == null || !canExitFromProject){
			toast("please wait...");
			return false;
		}
		String path = project.getPath();
		closeProject(false);
		openProject(new Project(path));
		return true;
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
		if(onProjectClosed!=null)
			onProjectClosed.run();
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
		// errors go to the Android snackbar (with a Copy button)
		if(com.star4droid.star2d.ui.Snack.isError(message) && com.star4droid.star2d.ui.Snack.activity()!=null){
			com.star4droid.star2d.ui.Snack.show(message);
			return;
		}
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
						showErrorText("Error :\n"+Utils.getStackTraceString(exc));
						Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
					});
				});
				// while a game is played in the editor the system Back must reach the activity (it stops the game), not be swallowed by LibGDX
				Gdx.input.setCatchKey(4,false);
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
					this.stageImp.assetLoader.dispose();
					com.badlogic.gdx.utils.Timer.instance().clear();
				}
				Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
				Gdx.input.setCatchKey(4,true);
				Gdx.input.setInputProcessor(multiplexer);
				UiStage.getViewport().update(width, height);
			}
			this.stageImp = stage;
			if(nativeHooks != null) nativeHooks.onPlayStateChanged(stage != null);
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
	    Runnable hook = frameHook;
	    if(hook != null){
	        frameHook = null;
	        try { hook.run(); } catch(Throwable ignored){}
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
					showErrorText("Error:\n"+Utils.getStackTraceString(e));
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