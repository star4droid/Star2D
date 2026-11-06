package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.ScrollableTextArea;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisWindow;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.CompileThread;
import com.star4droid.star2d.Helpers.FilesChangeDetector;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.Utils;
import com.star4droid.star2d.editor.items.*;
import com.star4droid.star2d.editor.ui.custom.CustomColliderEditor;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.sub.EventsItem;
import com.star4droid.star2d.editor.ui.sub.JointsList;
import com.star4droid.star2d.editor.ui.sub.PropertiesItem;
import com.star4droid.star2d.editor.ui.sub.TabsItem;
import com.star4droid.star2d.editor.ui.sub.VarsItem;
import com.star4droid.star2d.editor.ui.sub.TextShow;
import com.star4droid.star2d.editor.utils.EditorAction;
import java.util.concurrent.Executors;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class ControlLayer extends Table {
    private Table topContainer;
    private Table bottomContainer,windowsTable;
    private VisScrollPane topScrollPane;
    private VisScrollPane bottomScrollPane;
	private String clickedAction = "grid";
	//private VisImageButton closeBtn;
	String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_";
    
    private float iconSize = 70;
    private ImageButton.ImageButtonStyle buttonStyle;
	private TestApp app;
	private String oldSceneConfig = "";
	private ColorPicker colorPicker;
	private Color sceneColorBeforePick = Color.BLACK;
	BodiesList bodiesList;
	EventsItem eventsItem;
	PropertiesItem propertiesItem;
	PopupMenu xyMenu;
	VarsItem varsItem;
	VisLabel indexingLabel;
	JointsList jointsList;
	FileHandle fileBroweserDir;
	TextShow textShow;
	ControlType controlType;
	CustomColliderEditor customColliderEditor;
	EditorAI editorAI;
	public TabsItem tabsItem;
	VisImageButton gridBtn,backBtn,sceneActionsBtn,moveBtn,rotateBtn,lockBtn,scaleBtn,undoBtn,redoBtn;

	/**
	 * Native (Android) editor UI. When set, the LibGDX bars/panels are hidden and every
	 * action below is driven from Android through the public action methods.
	 * Callbacks are invoked on the GL thread.
	 */
	public interface NativeUi {
		void onSelectionChanged();
		void onUndoRedoChanged();
		void onScenesChanged();
		void onIndexing(boolean indexing);
		/** Compile/run progress. state: 0 = status text, 1 = error, 2 = finished, 3 = started playing. */
		void onPlayStatus(int state, String text);
		/** Structured compiler errors of the last build (called before the error text). */
		default void onBuildErrors(java.util.List<com.star4droid.star2d.CodeEditor.smart.BuildError> errors){}
		/** Show a long text (compiler output / errors). */
		void onShowText(String title, String text);
	}
	public static NativeUi nativeUi;

	public static boolean isNativeUi(){
		return nativeUi != null;
	}

    public ControlLayer(TestApp testApp) {
        super(VisUI.getSkin());
		this.app = testApp;
		String type = app.preferences.getString("Control Position","");
		if(type.equals(""))
			controlType = ControlType.TOP_AND_BOTTOM;
		else controlType = ControlType.valueOf(type);
		colorPicker = new ColorPicker(getTrans("chooseColor"));/*{
			@Override
			protected void setParent (Group parent) {
				super.setParent(parent);
				setScale(app.getEditor().isLandscape() ? 1f : 0.75f);
			}
		};*/
		app.setControlLayer(this);
        setupStyles();
        createLayout();
		init();
		// LibGDX bars stay alive (other code relies on them) but are hidden when the Android UI is active.
		setVisible(nativeUi == null);
    }

    private void setupStyles() {
        if (!VisUI.isLoaded()) com.star4droid.star2d.editor.utils.ThemeLoader.loadTheme();
        buttonStyle = new ImageButton.ImageButtonStyle(VisUI.getSkin().get(ImageButton.ImageButtonStyle.class));
    }
	
    private void createLayout() {
		customColliderEditor = new CustomColliderEditor(app);
        // Top section
        topContainer = new Table();
        topScrollPane = new VisScrollPane(topContainer);
        topScrollPane.setScrollingDisabled(false, true); // Vertical scroll disabled
        topScrollPane.setFadeScrollBars(true);
		topScrollPane.setScrollbarsOnTop(true);
		topScrollPane.setOverscroll(false,false);
		topScrollPane.setScrollbarsVisible(false);
		topScrollPane.setFlickScroll(true);
		topScrollPane.getStyle().background = VisUI.getSkin().getDrawable("window-bg");
        
        // Bottom section
        bottomContainer = new Table();
        bottomScrollPane = new VisScrollPane(bottomContainer);
		bottomScrollPane.setScrollbarsOnTop(true);
        bottomScrollPane.setScrollingDisabled(false, true);
		bottomScrollPane.setOverscroll(false,false);
        bottomScrollPane.setFadeScrollBars(true);
		bottomScrollPane.setScrollbarsVisible(false);
		bottomScrollPane.setFlickScroll(true);
		bottomScrollPane.getStyle().background = VisUI.getSkin().getDrawable("window-bg");

        // Main layout
        setFillParent(true);
        defaults().growX();
		Table centerTable = new Table();
		tabsItem = new TabsItem(app);
		
		//controls on the right
		VisImageButton bodiesListBtn = new VisImageButton(drawable("layer.png")),
				eventsBtn = new VisImageButton(drawable("pointer-icon.png")),
				jointsBtn = new VisImageButton(drawable("link.png")),
				propsBtn = new VisImageButton(drawable("events/properties.png")),
				varsBtn = new VisImageButton(drawable("x.png"));
		bodiesListBtn.setName("Bodies-List");
		eventsBtn.setName("Events");
		jointsBtn.setName("Joints");
		propsBtn.setName("Properties");
		varsBtn.setName("Variables");
		bodiesList = new BodiesList(app){
			@Override
			public boolean remove(){
				setVisible(false);
				return super.remove();
			}
		};
		propertiesItem = new PropertiesItem(app){
			@Override
			public boolean remove(){
				setVisible(false);
				return super.remove();
			}
		};
		jointsList = new JointsList(app){
			@Override
			public boolean remove(){
				setVisible(false);
				return super.remove();
			}
		};
		eventsItem = new EventsItem(app){
			@Override
			public boolean remove(){
				setVisible(false);
				return super.remove();
			}
		};
		

		varsItem = new VarsItem(){
		    @Override
		    public boolean remove(){
		        setVisible(false);
		        return super.remove();
		    }
		};
        
        editorAI = new EditorAI(app){
            @Override
            public boolean remove(){
                setVisible(false);
                return super.remove();
            }
        };
        
		centerTable.right();
		//centerTable.setFillParent(true);
		
		windowsTable = new Table();
		//closeBtn = new VisImageButton(VisUI.getSkin().getDrawable("icon-close"));
		Table btnsTable = new Table();
		centerTable.add(windowsTable).growX().growY();
		centerTable.add(btnsTable).width(iconSize+2);
		btnsTable.add(bodiesListBtn).size(iconSize+2).row();
		btnsTable.add(propsBtn).size(iconSize+2).padTop(2).row();
		btnsTable.add(jointsBtn).size(iconSize+2).padTop(2).row();
		btnsTable.add(eventsBtn).size(iconSize+2).padTop(2).row();
		btnsTable.add(varsBtn).size(iconSize+2).padTop(2).row();
        
        VisImageButton aiBtn = new VisImageButton(drawable("gemini.png"));
        aiBtn.setName("AI");
        btnsTable.add(aiBtn).size(iconSize+2).padTop(2);
        addListenerToWindow(editorAI, aiBtn);
		
		/*
		bodiesList.setVisible(false);
		eventsItem.setVisible(false);
		jointsList.setVisible(false);
		propertiesItem.setVisible(false);
		*/
		addListenerToWindow(bodiesList,bodiesListBtn);
		addListenerToWindow(eventsItem,eventsBtn);
		addListenerToWindow(jointsList,jointsBtn);
		addListenerToWindow(propertiesItem,propsBtn);
		addListenerToWindow(varsItem,varsBtn);

		left();
		if(controlType == ControlType.TOP_AND_BOTTOM){
        	add(topScrollPane).height(iconSize + 10).growX().padBottom(1).row();
			add(tabsItem).growX().row();
			add(centerTable).growX().growY().row();
        	add(bottomScrollPane).height(iconSize + 10).padTop(5);
		} else if(controlType == ControlType.ALL_TOP){
			add(topScrollPane).height(iconSize + 10).growX().padBottom(1).row();
			add(bottomScrollPane).height(iconSize + 10).growX().padBottom(1).row();
			add(tabsItem).growX().row();
			add(centerTable).growX().growY();
		} else {
			add(centerTable).growX().growY().row();
			add(tabsItem).growX().row();
			add(topScrollPane).height(iconSize + 10).growX().padBottom(1).row();
			add(bottomScrollPane).height(iconSize + 10).growX().padBottom(1).row();
		}
    }
	
	private void init(){
		textShow = new TextShow(getTrans("compiler"));
		xyMenu = new PopupMenu();
		xyMenu.addItem(new MenuItem(getTrans("noLock"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				app.getEditor().setLockX(false);
				app.getEditor().setLockY(false);
			}
		}));
		xyMenu.addItem(new MenuItem(getTrans("xLock"),drawable("x.png"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				app.getEditor().setLockX(true);
				app.getEditor().setLockY(false);
			}
		}));
		xyMenu.addItem(new MenuItem(getTrans("yLock"),drawable("y.png"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				app.getEditor().setLockX(false);
				app.getEditor().setLockY(true);
			}
		}));
		
		PopupMenu sceneActions = new PopupMenu();
		sceneActions.addItem(new MenuItem(getTrans("copyScene"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				dialogForScene(SceneAction.COPY,app.getEditor().getScene());
			}
		}));
		sceneActions.addItem(new MenuItem(getTrans("newScene"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				dialogForScene(SceneAction.CREATE,"Scene");
			}
		}));
		sceneActions.addItem(new MenuItem(getTrans("deleteScene"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				if(!app.getEditor().getScene().toLowerCase().equals("scene1")){
					ConfirmDialog.confirmDeleteDialog((ok)->{
						if(ok) actionDeleteCurrentScene();
					}).show(getStage());
				} else app.toast(getTrans("deleteMainScene"));
			}
		}));
		sceneActions.addItem(new MenuItem(getTrans("renameScene"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				if(!app.getEditor().getScene().toLowerCase().equals("scene1")){
					dialogForScene(SceneAction.RENAME,app.getEditor().getScene());
				} else app.toast(getTrans("renameMainScene"));
			}
		}));
		
		MenuItem openSceneItem = new MenuItem(getTrans("openScene"));
		PopupMenu scenesMenu = new PopupMenu();
		openSceneItem.addListener(new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				scenesMenu.clear();
				FileHandle scH = Gdx.files.absolute(app.getEditor().getProject().getScenesPath());
				for(FileHandle fileHandle:scH.list()){
					if(!fileHandle.isDirectory() && !com.star4droid.star2d.prefab.PrefabStore.isTempScene(fileHandle.name())){
						scenesMenu.addItem(new MenuItem(fileHandle.name(),drawable("scene.png"),new ChangeListener() {
							@Override
							public void changed (ChangeEvent event, Actor actor) {
								openScene(fileHandle.name());
							}
						}));
					}
				}
			}
		});
		openSceneItem.setSubMenu(scenesMenu);
		
		sceneActions.addItem(openSceneItem);
		
		PopupMenu bodiesMenu = createBodiesMenu(),
					lightsMenu = createLightMenu(),
					createMenu = new PopupMenu();
		
		MenuItem bodiesMenuItem = new MenuItem(getTrans("newBody"), drawable("add.png")),
				 lightsMenuItem = new MenuItem(getTrans("newLight"), drawable("add-light.png"));
		bodiesMenuItem.setSubMenu(bodiesMenu);
		lightsMenuItem.setSubMenu(lightsMenu);
		createMenu.addItem(bodiesMenuItem);
		createMenu.addItem(lightsMenuItem);
		
		addIconToTop("rotate",drawable("rotate-dev.png"),(btn)->{
			new ConfirmDialog(getTrans("changeOrienation"),getTrans("editorRestartNote"),ok->{
				if(ok)
					actionToggleOrientation();
			}).show(getStage());
		});
		
		topContainer.add().growX();
		
		addIconToTop("add-item",drawable("add.png"),(btn)->{
			createMenu.showMenu(getStage(),btn);
		});
		/*
		addIconToTop("add-light",drawable("add-light.png"),(btn)->{
			lightsMenu.showMenu(getStage(),btn);
		});
		*/
		
		sceneActionsBtn = addIconToTop("scene-actions",drawable("scene.png"),(btn)->{
			sceneActions.showMenu(getStage(),sceneActionsBtn);
		});
		
		addIconToTop("save",drawable("save.png"),(btn)->actionSave());
		addIconToTop("files",drawable("file-white.png"),(btn)->{
			if(fileBroweserDir!=null){
				app.getFileBrowser().setRootDir(app.getEditor().getProject().getPath());
				if(!fileBroweserDir.path().equals(app.getFileBrowser().getCurrentDir().path()))
					app.getFileBrowser().setCurrentDirectory(fileBroweserDir);
				fileBroweserDir = null;
			}
			this.app.getFileBrowser().setVisible(!this.app.getFileBrowser().isVisible());
		});
		addIconToTop("logs",drawable("assignment.png"),btn->{
			if(fileBroweserDir==null)
				fileBroweserDir = app.getFileBrowser().getCurrentDir();
			app.getFileBrowser().setRootDir(Gdx.files.external("logs"));
			app.getFileBrowser().setVisible(!this.app.getFileBrowser().isVisible());
			if(app.getFileBrowser().isVisible()){
				app.getFileBrowser().toFront();
				app.toast(getTrans("showLogsFolder"));
			}
		});
		
		ColorPickerListener colorPickerListener = new ColorPickerListener(){
			@Override
			public void canceled(Color oldColor) {
				if(app.getEditor()==null) return;
				Timer.schedule(new Timer.Task(){
					@Override
					public void run() {
						cancelSceneColor();
					}
				},800);
			}

			@Override
			public void changed(Color color) {
				previewSceneColor(color.toString().toUpperCase());
			}

			@Override
			public void reset(Color arg0, Color arg1) {}

			@Override
			public void finished(Color color) {
				if(app.getEditor()==null) return;
				colorPicker.setListener(null);
				finishSceneColor();
			}
		};
		
		addIconToTop("scene-color",drawable("color-pal.png"),(btn)->{
			if(app.getEditor()==null) return;
			colorPicker.setListener(colorPickerListener);
			if(colorPicker.getStage()!=null || colorPicker.getParent()!=null){
				colorPicker.remove();
				return;
			}
			beginSceneColor();
			getStage().addActor(colorPicker);
			colorPicker.toFront();
		});
		lockBtn = addIconToTop("lock-item",drawable("lock.png"),(btn)->{
			Boolean isLock = actionToggleLock();
			if(isLock != null)
				btn.getStyle().imageUp = drawable(isLock?"lock.png":"unlock.png");
		});
		indexingLabel = new VisLabel(getTrans("indexing"));
		topContainer.add(indexingLabel).padLeft(5).padRight(5).growX();
		indexingLabel.setName("indexing-lable");
		
		backBtn = addIconToTop("back",drawable("back_arrow.png"),btn->{
			//Gdx.app.exit();
			new ConfirmDialog(getTrans("exit"),getTrans("exitConfirm"),(ok)->{
			    if(ok)
				    app.closeProject();
			}).show(getStage());
		});
		
		//bottom buttons
		addIconToBottom("play",drawable("play.png"),(btn)->{
			btn.setDisabled(true);
			actionPlay();
		});
		
		gridBtn = addIconToBottom("grid",drawable("grid-icon.png"),(btn)->{
			if(clickedAction.equals("grid")){
				showXYMenu(btn,"");
				return;
			}
			actionSetTouchMode("grid");
		});
		gridBtn.setColor(Color.YELLOW);
		
		moveBtn = addIconToBottom("move",drawable("move-icon.png"),(btn)->{
			if(clickedAction.equals("move") && app.getEditor().getSelectedActor()!=null) showXYMenu(btn,"move");
			else actionSetTouchMode("move");
		});
		
		scaleBtn = addIconToBottom("scale",drawable("scale-icon.png"),(btn)->{
			if(clickedAction.equals("scale") && app.getEditor().getSelectedActor()!=null) showXYMenu(btn,"scale");
			else actionSetTouchMode("scale");
		});
		
		rotateBtn = addIconToBottom("rotate",drawable("rotate-icon.png"),(btn)->actionSetTouchMode("rotate"));
		
		addIconToBottom("delete",drawable("delete.png"),(btn)->actionDeleteSelected());
		
		undoBtn = addIconToBottom("undo",drawable("undo.png"),(btn)->actionUndo());
		
		redoBtn = addIconToBottom("redo",drawable("redo.png"),(btn)->actionRedo());
		
		addIconToBottom("center-camera",drawable("center-camera.png"),(btn)->app.getEditor().centerCamera());
		
		addIconToBottom("settings",drawable("events/properties.png"),btn->{
			app.getProjectsStage().settingsDialog.show(getStage());
		});
	}
	

	// =====================================================================
	// Actions (shared by the LibGDX bars and the native Android UI).
	// All of them must be called on the GL thread.
	// =====================================================================

	/** Switch portrait/landscape (restarts the scene editors). */
	public void actionToggleOrientation(){
		app.setOrienation(!app.getEditor().isLandscape());
	}

	/** Save the scene and regenerate its code. */
	public void actionSave(){
		if(app.getEditor()==null) {
			app.toast("editor returns null!!");
			return;
		}
		app.getEditor().getProject().save(app.getEditor());
		CodeGenerator.generateFor(app.getEditor(), cd -> {
			app.getEditor().getProject().writeCode(app.getEditor().getScene(),cd);
		});
		if(nativeUi == null) app.toast(getTrans("saved"));
	}

	/** Watches builds started by actionPlay (used by the remote/AI tools). */
	public interface BuildObserver {
		void status(int state, String text);
		void errors(java.util.List<com.star4droid.star2d.CodeEditor.smart.BuildError> errors);
	}
	public static volatile BuildObserver buildObserver;
	/** When true actionPlay compiles but does not start the game. */
	public static volatile boolean buildOnly;

	private void playStatus(int state, String text){
		BuildObserver bo = buildObserver;
		if(bo != null) try { bo.status(state, text); } catch(Throwable ignored){}
		if(nativeUi != null){
			nativeUi.onPlayStatus(state, text);
			return;
		}
		if(state == 0 || state == 1) textShow.setText(text);
		if(state == 2) textShow.setEnabled(true);
	}

	private java.util.List<com.star4droid.star2d.CodeEditor.smart.BuildError> lastBuildErrors;

	/** Time the running build started (0 = none). Two builds at once share bin/ and java/classes.dex and break each other. */
	private static volatile long compilingSince;

	/** Generate code, compile when needed, then run the current scene. */
	public void actionPlay(){
		if(app.getEditor() != null && app.getEditor().isLoading()){
			app.toast("please wait, the scene is still loading...");
			return;
		}
		if(compilingSince != 0 && System.currentTimeMillis() - compilingSince < 5 * 60 * 1000){
			app.toast(com.star4droid.star2d.ui.Tr.t("build_running", "A build is already running, please wait..."));
			return;
		}
		String path = app.getEditor().getProject().getPath();
		// get project name...
		final String projectName = path.contains("/") ? path.substring(path.lastIndexOf("/"),path.length()) : path;
		final FileHandle errorLog = Gdx.files.external("logs/"+projectName+"/compile error.txt");
		if(nativeUi == null){
			textShow.setEnabled(false);
			textShow.setText(getTrans("generatingCode"));
			textShow.toFront();
			textShow.show(getStage());
			getStage().addActor(textShow);
		} else playStatus(0, getTrans("generatingCode"));
		final InputProcessor inputProcessor = Gdx.input.getInputProcessor();
		//disable all inputs...
		Gdx.input.setInputProcessor(null);
		CodeGenerator.generateAllFor(app.getEditor(),(allScenes, anyChanged)->{
			CompileThread compileThread = new CompileThread(app.getEditor().getProject().get("java"),false);
			compileThread.setOnChangeStatus(new CompileThread.OnStatusChanged(){
				@Override
				public void onStatus(String s) {
					playStatus(0, s);
				}

				@Override
				public void onEnd(String message) {
					compilingSince = 0;
					Gdx.input.setInputProcessor(inputProcessor);
					playStatus(2, message);
				}

				@Override
				public void onErrors(java.util.List<com.star4droid.star2d.CodeEditor.smart.BuildError> errors) {
					lastBuildErrors = new java.util.ArrayList<>(errors);
					BuildObserver bo = buildObserver;
					if(bo != null) try { bo.errors(lastBuildErrors); } catch(Throwable ignored){}
					if(nativeUi != null) nativeUi.onBuildErrors(errors);
				}

				@Override
				public void onError(String error) {
					playStatus(1, error);
					errorLog.writeString(error,false);
				}

				@Override
				public void onSuccess(String message) {
					if(errorLog.exists()) errorLog.delete();
					textShow.remove();
					FileHandle fileHandle = new FileHandle(app.getEditor().getProject().getDex());
					FileHandle built = Gdx.files.absolute(app.getEditor().getProject().getPath()+"/java/classes.dex");
					if(!built.exists() || built.length() == 0){
						// the dex is missing (another build moved it, or D8 wrote nothing): keep the previous game dex, ask for a new build
						try { app.getEditor().getProject().get("java"); Gdx.files.absolute(app.getEditor().getProject().getChangesJson()).delete(); } catch(Throwable ignored){}
						playStatus(1, com.star4droid.star2d.ui.Tr.t("build_no_dex", "The build finished but its output (classes.dex) is missing. Press Play again to rebuild."));
						return;
					}
					try {
						if(fileHandle.exists()) fileHandle.file().setWritable(true);
						built.copyTo(fileHandle);
						built.delete();
					} catch(Throwable t){
						playStatus(1, "Error : " + t);
						return;
					}
					
					playStatus(3, "");
					if(!buildOnly) app.play(app.getEditor().getProject().getPath(),app.getEditor().getScene());
				}
				
			});
			
			if(anyChanged){
			    compilingSince = System.currentTimeMillis();
			    compileThread.start();
			} else {
				playStatus(0, getTrans("checkJavaFilesChanges"));
				Executors.newSingleThreadExecutor().execute(()->{
					try {
						boolean filesChanged = FilesChangeDetector.detect(Gdx.files.absolute(app.getEditor().getProject().get("java")).file().toPath(),Gdx.files.absolute(app.getEditor().getProject().getChangesJson()).file().toPath());
						if(!filesChanged){
						    textShow.remove();
							Gdx.app.postRunnable(()->{
								if(errorLog.exists()){
									Gdx.input.setInputProcessor(inputProcessor);
									BuildObserver bo2 = buildObserver;
									if(bo2 != null && lastBuildErrors != null) try { bo2.errors(lastBuildErrors); } catch(Throwable ignored){}
									if(nativeUi != null){
										if(lastBuildErrors != null && !lastBuildErrors.isEmpty()) nativeUi.onBuildErrors(lastBuildErrors);
										nativeUi.onPlayStatus(1, errorLog.readString());
										nativeUi.onPlayStatus(2, "");
									} else textShow.setText(errorLog.readString());
								} else {
									playStatus(3, "");
									if(!buildOnly) app.play(app.getEditor().getProject().getPath(),app.getEditor().getScene());
								}
							});
							
						} else compileThread.start();
					} catch(Exception e){
						compileThread.start();
					}
				});
			}
			
		});
	}

	/**
	 * Change the canvas touch mode: "grid", "move", "scale" or "rotate".
	 * Returns false when the mode needs a selected item (the editor then falls back to grid).
	 */
	public boolean actionSetTouchMode(String mode){
		com.star4droid.star2d.editor.LibgdxEditor ed = app.getEditor();
		if(ed == null) return false;
		boolean ok;
		switch(mode){
			case "grid":
				ed.setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.GRID);
				ok = true;
				clickedAction = "grid";
				break;
			case "move":
				ok = ed.setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.MOVE);
				if(ok) clickedAction = "move";
				break;
			case "scale":
				ok = ed.setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.SCALE);
				if(ok) clickedAction = "scale";
				break;
			default:
				ok = ed.setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.ROTATE);
				if(ok) clickedAction = "";
				break;
		}
		if(!ok) clickedAction = "grid";
		if(gridBtn != null){
			gridBtn.setColor(clickedAction.equals("grid") ? Color.YELLOW : Color.WHITE);
			moveBtn.setColor(clickedAction.equals("move") ? Color.YELLOW : Color.WHITE);
			scaleBtn.setColor(clickedAction.equals("scale") ? Color.YELLOW : Color.WHITE);
			rotateBtn.setColor(clickedAction.equals("") ? Color.YELLOW : Color.WHITE);
		}
		return ok;
	}

	/** Lock dragging to an axis: "none", "x" or "y". */
	public void actionSetAxisLock(String axis){
		if(app.getEditor()==null) return;
		app.getEditor().setLockX(axis.equals("x"));
		app.getEditor().setLockY(axis.equals("y"));
	}

	/** Toggle the selected item's lock. Returns the new state, or null when nothing is selected. */
	public Boolean actionToggleLock(){
		if(app.getEditor()==null || app.getEditor().getSelectedActor() == null) return null;
		PropertySet<String, Object> propertySet = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
		boolean isLock = !propertySet.getString("lock").equals("true");
		propertySet.put("lock", String.valueOf(isLock));
		return isLock;
	}

	/** Delete the selected item (undoable). */
	public void actionDeleteSelected(){
		if(app.getEditor()==null || app.getEditor().getSelectedActor()==null) return;
		if(com.star4droid.star2d.prefab.PrefabSession.isFixedRoot(app.getEditor(),app.getEditor().getSelectedActor())){
			app.toast(com.star4droid.star2d.ui.Tr.t("prefab_root_locked","The root of a prefab can't be deleted"));
			return;
		}
		app.getEditor().getProject().deleteBody(PropertySet.getPropertySet(app.getEditor().getSelectedActor()).get("name").toString(), app.getEditor().getScene());
		Actor actor = app.getEditor().getSelectedActor();
		actor.remove();
		EditorAction.itemRemoved(app.getEditor(),actor);
		app.getEditor().selectActor(null);
		bodiesList.update();
		if(nativeUi != null) nativeUi.onSelectionChanged();
	}

	public void actionUndo(){
		if(app.getEditor()!=null && app.getEditor().canUndo())
			app.getEditor().undo();
	}

	public void actionRedo(){
		if(app.getEditor()!=null && app.getEditor().canRedo())
			app.getEditor().redo();
	}

	/** Add a body: 0 Box, 1 Circle, 2 Text, 3 Joystick, 4 Progress, 5 Custom, 6 Particle, 7 Camera, 8 TiledMap, 9 Group, 10 UI Holder. */
	public void actionAddBody(int pos){
		try {
			EditorItem item = getItem(pos);
			app.getEditor().addActor((Actor)item);
			((Actor)item).setName(app.getEditor().getName((Actor)item));
			try {
				java.lang.reflect.Method method = item.getClass().getMethod("setDefault");
				Object[] objs = null;
				method.invoke(item,objs);
			} catch(Exception e){
				Throwable c = e.getCause() != null ? e.getCause() : e;
				Gdx.files.external("logs/add_body.error.txt").writeString(Utils.getStackTraceString(c)+"\n",true);
			}
			if(item.getPropertySet()==null){
				((Actor)item).remove();
				app.toast("Error : could not create the default properties of "+item.getClass().getSimpleName()+" (see logs/add_body.error.txt)");
				return;
			}
			item.getPropertySet().put("z",pos == 10 ? Math.max(100, app.getEditor().getActors().size) : app.getEditor().getActors().size);
			app.getEditor().selectActor((Actor)item);
			EditorAction.itemAdded(app.getEditor(),(Actor)item);
			item.update();
			com.star4droid.star2d.prefab.PrefabSession.afterAdd(app.getEditor(),(Actor)item);
		} catch(Error error){
			showError("Error :\n"+Utils.getStackTraceString(error));
		}
	}

	/**
	 * Add a 3D element (3D scenes): kind OBJ3D / LIGHT3D / CAMERA3D, preset = the Mesh (CUBE, SPHERE, MODEL, TERRAIN, WATER...)
	 * or the Light Kind (POINT, SPOT, DIRECTIONAL). It appears at the point the 3D view looks at.
	 */
	public void actionAdd3D(String kind,String preset){
		actionAdd3D(kind,preset,null);
	}

	/** model: project path of a .glb / .gltf ("/name.glb"): a MODEL element showing it (named after the file). */
	public void actionAdd3D(String kind,String preset,String model){
		try {
			com.star4droid.star2d.editor.LibgdxEditor ed = app.getEditor();
			com.star4droid.star2d.editor.items.Editor3DItem item = new com.star4droid.star2d.editor.items.Editor3DItem(ed,kind);
			String base = "OBJ3D".equals(kind) ? (preset == null ? "Cube" : preset.substring(0,1)+preset.substring(1).toLowerCase()) : ("LIGHT3D".equals(kind) ? "Light" : "Camera");
			if(model != null){
				String fn = model.substring(model.lastIndexOf('/')+1);
				int dot = fn.lastIndexOf('.');
				if(dot > 0) fn = fn.substring(0,dot);
				fn = fn.replaceAll("[^A-Za-z0-9_]","_");
				if(fn.isEmpty() || Character.isDigit(fn.charAt(0))) fn = "Model_" + fn;
				base = fn;
			}
			String name = base; int n = 1;
			java.util.ArrayList<String> used = ed.getBodiesList();
			while(used.contains(name)) name = base + (n++);
			item.setName(name);
			item.setDefault();
			if(item.getPropertySet()==null){ item.remove(); app.toast("Error : could not create the default properties"); return; }
			com.star4droid.star2d.Helpers.PropertySet<String,Object> ps = item.getPropertySet();
			ps.put("name",name);
			ps.put("Script",name);
			com.badlogic.gdx.math.Vector3 p = ed.get3D() != null ? ed.get3D().spawnPoint() : new com.badlogic.gdx.math.Vector3();
			float y = 0.5f;
			if("OBJ3D".equals(kind)){
				ps.put("Mesh",preset == null ? "CUBE" : preset);
				if("PLANE".equals(preset)){ ps.put("Scale X","10"); ps.put("Scale Z","10"); y = 0; }
				else if("TERRAIN".equals(preset)){ ps.put("Scale X","60"); ps.put("Scale Y","1"); ps.put("Scale Z","60"); ps.put("Terrain Noise","1"); ps.put("Color","#7CB65A"); y = 0; }
				else if("WATER".equals(preset)){ ps.put("Scale X","60"); ps.put("Scale Z","60"); ps.put("Cast Shadows","false"); y = 0; }
				else if("MODEL".equals(preset)){ y = 0; if(model != null){ ps.put("Model",model); com.star4droid.template.d3.Assets3D.reload(model); } }
				else if("QUAD".equals(preset) || "DISC".equals(preset)) y = 0.5f;
			} else if("LIGHT3D".equals(kind)){
				ps.put("Light Kind",preset == null ? "POINT" : preset);
				y = "DIRECTIONAL".equals(preset) ? 6 : 3;
				if("SPOT".equals(preset)){ ps.put("Rotation X","-90"); ps.put("Range","15"); }
			} else if("CAMERA3D".equals(kind)){
				y = 3; ps.put("z",String.valueOf(p.z + 8));
				boolean hasActive = false;
				for(com.badlogic.gdx.scenes.scene2d.Actor a : ed.getActors())
					if(a instanceof com.star4droid.star2d.editor.items.Editor3DItem && "CAMERA3D".equals(((com.star4droid.star2d.editor.items.Editor3DItem)a).kind) && a != item
						&& "true".equals(((com.star4droid.star2d.editor.items.Editor3DItem)a).getPropertySet().getString("Active"))) hasActive = true;
				ps.put("Active",String.valueOf(!hasActive));
			}
			ps.put("x",String.valueOf(com.badlogic.gdx.math.MathUtils.round(p.x*100)/100f));
			ps.put("y",String.valueOf(y));
			if(!"CAMERA3D".equals(kind)) ps.put("z",String.valueOf(com.badlogic.gdx.math.MathUtils.round(p.z*100)/100f));
			item.update();
			ed.selectActor(item);
			EditorAction.itemAdded(ed,item);
			ed.getSaveState();
		} catch(Throwable error){
			showError("Error :\n"+Utils.getStackTraceString(error));
		}
	}

	/** Add a camera view (live picture of a camera) to the 2D layer. */
	public void actionAddCameraView(){
		try {
			com.star4droid.star2d.editor.LibgdxEditor ed = app.getEditor();
			com.star4droid.star2d.editor.items.EditorCameraViewItem item = new com.star4droid.star2d.editor.items.EditorCameraViewItem(ed);
			item.setName(ed.getName(item));
			item.setDefault();
			if(item.getPropertySet()==null){ item.remove(); return; }
			item.getPropertySet().put("z",String.valueOf(Math.max(100, ed.getActors().size)));
			item.getPropertySet().put("x",String.valueOf(ed.getLogicWidth() - 360));
			item.getPropertySet().put("y",String.valueOf(ed.getLogicHeight() - 220));
			ed.selectActor(item);
			EditorAction.itemAdded(ed,item);
			item.update();
		} catch(Throwable error){
			showError("Error :\n"+Utils.getStackTraceString(error));
		}
	}

	/** Add a light: 0 point, 1 directional, 2 cone. */
	public void actionAddLight(int pos){
		try {
			LightItem lightItem = new LightItem(app.getEditor());
			app.getEditor().addActor(lightItem);
			lightItem.setName(app.getEditor().getName(lightItem));
			lightItem.setDefault(getLightType(pos));
			lightItem.getPropertySet().put("z",app.getEditor().getActors().size);
			app.getEditor().selectActor(lightItem);
			lightItem.update();
			EditorAction.itemAdded(app.getEditor(),lightItem);
		} catch(Error error){
			showError("Error :\n"+Utils.getStackTraceString(error));
		}
	}

	/** Light settings dialog (LibGDX). */
	public void actionLightSettings(){
		LightSettingsDialog.showFor(new Project(app.getEditor().getProject().getPath()),app.getEditor().getScene());
	}

	private void showError(String text){
		if(nativeUi != null){
			nativeUi.onShowText("Error", text);
			return;
		}
		textShow.setText(text);
		textShow.show(getStage());
		textShow.toFront();
	}

	// ---- scene color (live preview + one undo entry)

	public void beginSceneColor(){
		if(app.getEditor()==null) return;
		sceneColorBeforePick = app.getEditor().backgroundColor;
		oldSceneConfig = app.getEditor().getConfig().toString();
	}

	public void previewSceneColor(String hex){
		if(app.getEditor()==null) return;
		app.getEditor().setSceneColor(hex);
	}

	public void cancelSceneColor(){
		if(app.getEditor()==null) return;
		app.getEditor().setSceneColor(sceneColorBeforePick.toString().toUpperCase());
	}

	public void finishSceneColor(){
		if(app.getEditor()==null) return;
		EditorAction.sceneConfigChanged(app.getEditor(),oldSceneConfig,app.getEditor().getConfig().toString());
	}

	public String getSceneColorHex(){
		if(app.getEditor()==null) return "#263238FF";
		return app.getEditor().backgroundColor.toString().toUpperCase();
	}

	// ---- scenes

	/** Names of all scenes of the project. */
	public java.util.ArrayList<String> listScenes(){
		java.util.ArrayList<String> list = new java.util.ArrayList<>();
		FileHandle scH = Gdx.files.absolute(app.getEditor().getProject().getScenesPath());
		for(FileHandle fileHandle:scH.list())
			if(!fileHandle.isDirectory() && !com.star4droid.star2d.prefab.PrefabStore.isTempScene(fileHandle.name())) list.add(fileHandle.name());
		java.util.Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
		return list;
	}

	/** Open a scene (saves the current scene's code first). */
	public void actionOpenScene(String name){
		openScene(name);
	}

	/** Delete the current scene (scene1 can't be deleted). Returns an error or null. */
	public String actionDeleteCurrentScene(){
		if(app.getEditor().getScene().toLowerCase().equals("scene1"))
			return getTrans("deleteMainScene");
		app.getEditor().getProject().deleteScene(app.getEditor().getScene());
		for(int i = 0; i < app.editors.size; i++){
			if(app.editors.get(i).getScene().toLowerCase().equals(app.getEditor().getScene().toLowerCase())){
				app.editors.removeIndex(i);
				break;
			}
		}
		app.openSceneInNewEditor("scene1");
		bodiesList.update();
		tabsItem.refresh();
		if(nativeUi != null) nativeUi.onScenesChanged();
		return null;
	}

	/** Close an open scene tab. Returns false when it is the last one. */
	public boolean actionCloseSceneTab(int index){
		if(app.editors.size <= 1) return false;
		boolean wasCurrent = app.editors.get(index) == app.getEditor();
		app.editors.get(index).dispose();
		app.editors.removeIndex(index);
		if(wasCurrent) app.setCurrentEditor(0);
		tabsItem.refresh();
		if(nativeUi != null) nativeUi.onScenesChanged();
		return true;
	}

	public void showXYMenu(Actor actor,String action){
		xyMenu.showMenu(getStage(),actor);
	}
	
	public ColorPicker getColorPicker(){
		return colorPicker;
	}
	
	private void openScene(String name){
		CodeGenerator.generateFor(app.getEditor(), cd -> {
			app.getEditor().getProject().writeCode(app.getEditor().getScene(),cd);
			app.openSceneInNewEditor(name);
			if(nativeUi != null) nativeUi.onScenesChanged();
		});
	}
	
	public void setIndexing(boolean b){
		indexingLabel.setVisible(b);
		if(nativeUi != null) nativeUi.onIndexing(b);
	}
	
	public CustomColliderEditor getCustomColliderEditor(){
		return customColliderEditor;
	}
	
	public void editScene(String name,SceneAction sceneAction){
		String error = applySceneAction(name, sceneAction);
		if(error != null){
			app.toast(error);
			dialogForScene(sceneAction,name);
		}
	}

	/** Create / copy / rename a scene. Returns an error message or null on success. */
	public String applySceneAction(String name,SceneAction sceneAction){
		return applySceneAction(name,sceneAction,null);
	}

	/**
	 * Same, with an explicit orientation for CREATE ("landscape" / "portrait", null = the orientation of the project).
	 * The new scene's config file is written BEFORE its editor opens (orientation + the project's logic size), so the result
	 * never depends on a flag of another editor or on the phone screen size.
	 */
	public String applySceneAction(String name,SceneAction sceneAction,String orientation){
		return applySceneAction(name,sceneAction,orientation,"2D");
	}

	/** CREATE with a scene type: "3D" writes the 3D settings and adds a ground, a cube and a camera. */
	public String applySceneAction(String name,SceneAction sceneAction,String orientation,String sceneType){
		pendingSceneType = sceneType;
		try {
			return applySceneAction0(name,sceneAction,orientation);
		} finally {
			pendingSceneType = null;
		}
	}

	private String pendingSceneType;

	private String applySceneAction0(String name,SceneAction sceneAction,String orientation){
		String path = app.getEditor().getProject().getScenesPath()+name;
		if (name.equals(""))
			return getTrans("emptyError");
		for (char c : name.toCharArray()) {
			//only possible chars...to prevent writing strange chars :|
			if (!(chars.contains("" + c)))
				return "Not Allowed char : " + c;
		}
		if (Character.isDigit(name.charAt(0)))
			return "Not Allowed : a scene name can't start with a digit";
		FileHandle fhandle = Gdx.files.absolute(path);
		if (fhandle.exists())
			return getTrans("sceneConflict");
		// the generated class is the lower-case name: scene1 and Scene1 would share one java file
		FileHandle[] existing = Gdx.files.absolute(app.getEditor().getProject().getScenesPath()).list();
		if (existing != null) for (FileHandle other : existing)
			if (other.name().equalsIgnoreCase(name)) return getTrans("sceneConflict");
		if (sceneAction == SceneAction.COPY) {
			// copy what is on screen, not the last save
			try { app.getEditor().getProject().save(app.getEditor()); } catch(Throwable ignored){}
		}
		fhandle.writeString("",false);
		if (sceneAction == SceneAction.CREATE) {
			//nothing to do...
		} else if (sceneAction == SceneAction.RENAME) {
			app.getEditor().getProject().renameScene(app.getEditor().getScene(), name);
			EditorAction.renameScene(app.getEditor(),app.getEditor().getScene(),name);
			app.toast(getTrans("sceneRenamed"));
		} else if (sceneAction == SceneAction.COPY) {
			try {
				app.getEditor().getProject().copyScene(app.getEditor().getScene(),name);
			} catch(Throwable t){
				// copyScene already removed the half-copied files
				try { app.getEditor().getProject().deleteScene(name); } catch(Throwable ignored){}
				return "Copy failed : "+t.getMessage();
			}
			app.toast(getTrans("sceneCopied"));
		}
		if (fhandle.exists()) {
			if(sceneAction == SceneAction.RENAME){
				app.getEditor().setScene(name);
			} else if(sceneAction == SceneAction.COPY || sceneAction == SceneAction.CREATE){
				if(sceneAction == SceneAction.CREATE){
				    Boolean land = orientation == null || orientation.isEmpty() ? null : Boolean.valueOf(!orientation.equalsIgnoreCase("portrait"));
				    com.star4droid.star2d.editor.ProjectOrientation.seed(app.getEditor().getProject(),name,land);
				    if("3D".equals(pendingSceneType)) seed3D(name);
				}
				app.openSceneInNewEditor(name);
				if(sceneAction == SceneAction.CREATE && "3D".equals(pendingSceneType)){
					final com.star4droid.star2d.editor.LibgdxEditor ne = app.getEditor();
					ne.whenLoaded(()->{
						actionAdd3D("OBJ3D","PLANE");
						actionAdd3D("OBJ3D","CUBE");
						actionAdd3D("CAMERA3D",null);
						ne.selectActor(null);
						ne.getSaveState();
						try { ne.getProject().save(ne); } catch(Throwable ignored){}
					});
				}
			} else if(sceneAction == SceneAction.DELETE){
				app.openSceneInNewEditor("scene1");
			}
			tabsItem.refresh();
			if(nativeUi != null) nativeUi.onScenesChanged();
			return null;
		}
		return "Failed to create the path..";
	}
	
	/** Writes the 3D settings into the new scene's config. */
	private void seed3D(String name){
		try {
			com.badlogic.gdx.files.FileHandle f = Gdx.files.absolute(app.getEditor().getProject().getConfig(name));
			String txt = f.exists() ? f.readString() : Gdx.files.internal("scene.json").readString();
			com.star4droid.star2d.Helpers.PropertySet<String,Object> cfg = com.star4droid.star2d.editor.Utils.getProperty(txt);
			if(cfg == null) cfg = new com.star4droid.star2d.Helpers.PropertySet<>();
			com.star4droid.star2d.ui.Scene3DSettings.defaults(cfg);
			f.writeString(cfg.toString(),false);
		} catch(Throwable t){
			Gdx.files.external("logs/scene3d.error.txt").writeString(Utils.getStackTraceString(t)+"\n",true);
		}
	}

	public void dialogForScene(SceneAction sceneAction,String initial){
		new SingleInputDialog(sceneAction.name(),getTrans("name")+" : ",initial,(text)->{
			editScene(text,sceneAction);
		}).show(getStage());
	}
	
	public enum SceneAction {
		RENAME,DELETE,CREATE,COPY
	}
	
	public VarsItem getVarsItem(){
	    return varsItem;
	}
	
	public void updateUndoRedo(){
		undoBtn.setDisabled(!app.getEditor().canUndo());
		undoBtn.setColor(app.getEditor().canUndo()?Color.YELLOW:Color.WHITE);
		
		redoBtn.setDisabled(!app.getEditor().canRedo());
		redoBtn.setColor(app.getEditor().canRedo()?Color.YELLOW:Color.WHITE);
		if(nativeUi != null) nativeUi.onUndoRedoChanged();
	}
	
	public void bodySelected(){
		if (app.getEditor().getSelectedActor() != null && lockBtn != null && lockBtn.getStyle() != null) {
            String isLock = PropertySet.getPropertySet(app.getEditor().getSelectedActor()).getString("lock");
            lockBtn.getStyle().imageUp = (drawable(isLock.equals("true") ? "lock.png" : "unlock.png"));
        }
		bodiesList.update();
		try {
			propertiesItem.refresh();
		} catch(Exception e){
			Gdx.files.external("logs/app_logs.txt").writeString("error on control layer code for refreshing : "+e.toString()+"\n",true);
		}
		if(nativeUi != null) nativeUi.onSelectionChanged();
	}
	
	private Drawable drawable(String name){
		int start = name.contains("/") ? name.lastIndexOf("/") : 0;
		String namePure = start == 0 ? name.substring(0,name.indexOf(".")):name.substring(start+1,name.indexOf(".",start));
		try {
			return VisUI.getSkin().getDrawable(namePure);
		} catch(Exception e){
			Gdx.files.external("logs/control drawable.txt").writeString("control drawbale : "+namePure+", full : "+name+"\n error : "+e.toString()+"\n\n",true);
			return new TextureRegionDrawable(new Texture(Gdx.files.internal("images/"+name)));
		}
	}
	
	private PopupMenu createBodiesMenu(){
		PopupMenu menu = new PopupMenu();
		String[] names={"Box Body","Circle Body","Text Item","Joystick Item","Progress Bar","Custom Body","Particle Effect","Camera","TiledMap","Group","UI Holder"};
		String[] icons={"box","circle","text","joystick","progress","custom","particle","camera","map","group","uiholder"};
		for(int x = 0; x < names.length; x++){
			final String name = names[x];
			final int pos = x;
			MenuItem item = new MenuItem(getTrans(name),drawable("bodies/"+icons[x]+".png"),new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
				    /*if(pos == 8){
				        app.toast("sorry, not ready yet...");
				        return;
				    }*/
					actionAddBody(pos);
				}
			});
			item.pad(5);
			menu.addItem(item);
		}
		
		return menu;
	}
	
	private EditorItem getItem(int pos){
		switch(pos){
			case 0: return new BoxItem(app.getEditor());
			case 1: return new CircleItem(app.getEditor());
			case 2: return new EditorTextItem(app.getEditor());
			case 3: return new JoyStickItem(app.getEditor());
			case 4: return new EditorProgressItem(app.getEditor());
			case 5: return new CustomItem(app.getEditor());
			case 6: return new ParticleItem(app.getEditor());
			case 7: return new EditorCameraItem(app.getEditor());
			case 8: return new EditorMapItem(app.getEditor());
			case 9: return new EditorGroupItem(app.getEditor());
			case 10: return new EditorUIHolderItem(app.getEditor());
		}
		return null;
	}
	
	public String getTouchMode(){
		return clickedAction;
	}
	
	private PopupMenu createLightMenu(){
		PopupMenu menu = new PopupMenu();
		String[] list = {"Point Light","Directional Light","Cone Light","Light Settings"};
		for(int x = 0; x < 4; x++){
			final int pos = x;
			menu.addItem(new MenuItem(getTrans(list[x]),drawable("light.png"),new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
					if(pos == 3){
						LightSettingsDialog.showFor(new Project(app.getEditor().getProject().getPath()),app.getEditor().getScene());
					} else actionAddLight(pos);
				}
			}));
		}
		return menu;
	}
	
	private String getLightType(int pos){
		if(pos==0)
	        return "point";
	    else if(pos==1)
	        return "directional";
	    else return "cone";
	}
	
	//when the button clicked, add the item to the window...
	private void addListenerToWindow(Actor item,Actor btn){
		VisWindow dialog = new VisWindow(getTrans(btn.getName().replace("-","")) + " " + getTrans("youCanDrag"));
		dialog.setKeepWithinStage(false);
		//dialog.addCloseButton();
		dialog.reset();
		dialog.add(item).grow().pad(3).center();
		dialog.setResizable(true);
		dialog.setMovable(true);
		VisImageButton closeButton = new VisImageButton("close-window");
		closeButton.addListener(new ClickListener() {
			@Override
            public void clicked(InputEvent event, float x, float y) {
				dialog.remove();
				dialog.setUserObject("false");
			}
		});
		//dialog.getTitleTable().add(closeButton).padRight(4);
		// use user data to check dialog visibility...
		dialog.setUserObject("false");
		btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
				if(dialog.getUserObject().toString().equals("true"))
					dialog.remove();
				else {
					getStage().addActor(dialog);
					dialog.toFront();
					float width = Gdx.graphics.getWidth()*(app.getEditor().isLandscape() ? 0.4f : 0.85f),
						height = app.getEditor().isLandscape() ? windowsTable.getHeight() : Gdx.graphics.getHeight() * 0.3f;
					if(dialog.getWidth() != width || dialog.getHeight() != height){
						dialog.setSize(width,height);
						dialog.setX((getStage().getWidth() - dialog.getWidth()) / 2);
					}
				}
				dialog.setUserObject(dialog.getUserObject().toString().equals("true") ? "false" : "true");
            }
        });
	}
	
	public JointsList getJointsList(){
		return jointsList;
	}
	
	public BodiesList getBodiesList(){
		return bodiesList;
	}
	
	public Table getWindowsTable(){
		return windowsTable;
	}
	
	public PropertiesItem getPropertiesItem(){
		return propertiesItem;
	}

    public VisImageButton addIconToTop(String name, Drawable drawable, IconListener iconListener) {
        return addIcon(topContainer, name, drawable, iconListener);
    }

    public VisImageButton addIconToBottom(String name, Drawable drawable, IconListener iconListener) {
        return addIcon(bottomContainer, name, drawable, iconListener);
    }

    private VisImageButton addIcon(Table container, String name, Drawable drawable, IconListener iconListener) {
        VisImageButton button = new VisImageButton(drawable);
		
		//button.setName(name);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                iconListener.click(button);
            }
        });
        
        container.add(button).size(iconSize).padLeft(2).padRight(2).padTop(2).padBottom(10);
        container.invalidateHierarchy();
		return button;
    }

    // Optional configuration methods
    public void setIconSize(float size) {
        this.iconSize = size;
        refreshIconSizes();
    }

    private void refreshIconSizes() {
        refreshContainerSizes(topContainer);
        refreshContainerSizes(bottomContainer);
    }

    private void refreshContainerSizes(Table container) {
        for (Cell<?> cell : container.getCells()) {
            cell.size(iconSize);
        }
        container.invalidateHierarchy();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate(); // Ensure layout is updated
        super.draw(batch, parentAlpha);
    }

	interface IconListener {
		public void click(VisImageButton visImageButton);
	}
	
	public enum ControlType {
		ALL_TOP,ALL_BOTTOM,TOP_AND_BOTTOM
	}
}