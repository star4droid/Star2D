package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
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
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.CompileThread;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.*;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.sub.EventsItem;
import com.star4droid.star2d.editor.ui.sub.JointsList;
import com.star4droid.star2d.editor.ui.sub.PropertiesItem;
import com.star4droid.star2d.editor.ui.sub.TabsItem;
import com.star4droid.star2d.editor.ui.sub.TextShow;
import com.star4droid.star2d.editor.utils.EditorAction;

public class ControlLayer extends Table {
    private Table topContainer;
    private Table bottomContainer,windowsTable;
    private VisScrollPane topScrollPane;
    private VisScrollPane bottomScrollPane;
	private String clickedAction = "";
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
	JointsList jointsList;
	FileHandle fileBroweserDir;
	VisImageButton gridBtn,backBtn,sceneActionsBtn,moveBtn,rotateBtn,lockBtn,scaleBtn,undoBtn,redoBtn;
	/*
		for me : don't add this to be built-in in the LibgdxEditor
		because it's not a good way if we implement Tabs...
	*/

    public ControlLayer(TestApp testApp) {
        super(VisUI.getSkin());
		this.app = testApp;
		colorPicker = new ColorPicker("Choose Color");
		app.setControlLayer(this);
        setupStyles();
        createLayout();
		init();
    }

    private void setupStyles() {
        if (!VisUI.isLoaded()) VisUI.load();
        buttonStyle = new ImageButton.ImageButtonStyle(VisUI.getSkin().get(ImageButton.ImageButtonStyle.class));
    }
	
	private void init(){
		/*
		final VisDialog compileDialog = new VisDialog("Compiler");
		final ScrollableTextArea compileLabel = new ScrollableTextArea("Compiling...");
		ScrollPane scrollPane = compileLabel.createCompatibleScrollPane();
		scrollPane.setFlickScroll(true);
		scrollPane.setFillParent(true);
		compileLabel.setReadOnly(true);
		compileDialog.setFillParent(true);
		compileDialog.setResizable(true);
		scrollPane.setFadeScrollBars(false);
		scrollPane.setScrollingDisabled(false,false);
		compileDialog.row();
		compileDialog.add(scrollPane).pad(4).maxHeight(500).maxWidth(Gdx.graphics.getWidth()*0.8f).minHeight(300).growX();
		compileDialog.row();
		VisTextButton cancelBtn = new VisTextButton("Cancel");
		VisTextButton copyBtn = new VisTextButton("Copy");
		
		cancelBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                compileDialog.hide();
            }
        });
		
		copyBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				Gdx.app.getClipboard().setContents(compileLabel.getText().toString());
			}
		});
		
		compileDialog.add(cancelBtn).padRight(10).padBottom(5);
		compileDialog.add(copyBtn).padBottom(5);
		*/
		TextShow textShow = new TextShow("Compiler");
		xyMenu = new PopupMenu();
		xyMenu.addItem(new MenuItem("No Lock",new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				app.getEditor().setLockX(false);
				app.getEditor().setLockY(false);
			}
		}));
		xyMenu.addItem(new MenuItem("Lock X",drawable("x.png"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				app.getEditor().setLockX(true);
				app.getEditor().setLockY(false);
			}
		}));
		xyMenu.addItem(new MenuItem("Lock Y",drawable("y.png"),new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				app.getEditor().setLockX(false);
				app.getEditor().setLockY(true);
			}
		}));
		
		PopupMenu sceneActions = new PopupMenu();
		sceneActions.addItem(new MenuItem("Copy Scene",new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				dialogForScene(SceneAction.COPY,app.getEditor().getScene());
			}
		}));
		sceneActions.addItem(new MenuItem("New Scene",new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				dialogForScene(SceneAction.CREATE,"Scene");
			}
		}));
		sceneActions.addItem(new MenuItem("Delete Scene",new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				if(!app.getEditor().getScene().toLowerCase().equals("scene1")){
					ConfirmDialog.confirmDeleteDialog((ok)->{
						if(ok){
							app.getEditor().getProject().deleteScene(app.getEditor().getScene());
							for(int i = 0; i < app.editors.size; i++){
								if(app.editors.get(i).getScene().toLowerCase().equals(app.getEditor().getScene().toLowerCase())){
									app.editors.removeIndex(i);
									break;
								}
							}
							app.openSceneInNewEditor("scene1");
							//updateScenes();
							bodiesList.update();
							tabsItem.refresh();
						}
					}).show(getStage());
				} else app.toast("You can\'t delete the main scene!");
			}
		}));
		sceneActions.addItem(new MenuItem("Rename Scene",new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				if(!app.getEditor().getScene().toLowerCase().equals("scene1")){
					dialogForScene(SceneAction.RENAME,app.getEditor().getScene());
				} else app.toast("You can\'t rename the main scene!");
			}
		}));
		
		MenuItem openSceneItem = new MenuItem("Open Scene");
		PopupMenu scenesMenu = new PopupMenu();
		openSceneItem.addListener(new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				scenesMenu.clear();
				FileHandle scH = Gdx.files.absolute(app.getEditor().getProject().getScenesPath());
				for(FileHandle fileHandle:scH.list()){
					if(!fileHandle.isDirectory()){
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
		
		MenuItem bodiesMenuItem = new MenuItem("New Body", drawable("add.png")),
				 lightsMenuItem = new MenuItem("New Light", drawable("add-light.png"));
		bodiesMenuItem.setSubMenu(bodiesMenu);
		lightsMenuItem.setSubMenu(lightsMenu);
		createMenu.addItem(bodiesMenuItem);
		createMenu.addItem(lightsMenuItem);
		
		addIconToTop("rotate",drawable("rotate-dev.png"),(btn)->app.getEditor().setLandscape(!app.getEditor().isLandscape()));
		
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
		
		addIconToTop("save",drawable("save.png"),(btn)->{
			if(app.getEditor()==null) {
				app.toast("editor returns null!!");
				return;
			}
			app.getEditor().getProject().save(app.getEditor());
			//btn.setScale(0);
			//btn.addAction(Actions.scaleTo(1,1,300));
			app.toast("Saved...");
		});
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
			app.getFileBrowser().toFront();
			app.toast("show logs folder");
		});
		
		ColorPickerListener colorPickerListener = new ColorPickerListener(){
			@Override
			public void canceled(Color oldColor) {
				if(app.getEditor()==null) return;
				Timer.schedule(new Timer.Task(){
					@Override
					public void run() {
						app.getEditor().setSceneColor(sceneColorBeforePick.toString().toUpperCase());
					}
					
				},800);
			}

			@Override
			public void changed(Color color) {
				if(app.getEditor()==null) return;
				//String hex = String.format("#%06X", (0xFFFFFF & color.toIntBits()));
				String hex = color.toString().toUpperCase();
				app.getEditor().setSceneColor(hex);
			}

			@Override
			public void reset(Color arg0, Color arg1) {}

			@Override
			public void finished(Color color) {
				if(app.getEditor()==null) return;
				colorPicker.setListener(null);
				EditorAction.sceneConfigChanged(app.getEditor(),oldSceneConfig,app.getEditor().getConfig().toString());
				//String hex = String.format("#%06X", (0xFFFFFF & color.toIntBits()));
				//String hex = color.toString().toUpperCase();
				//app.getEditor().setSceneColor(hex);
			}
			
		};
		
		addIconToTop("scene-color",drawable("color-pal.png"),(btn)->{
			if(app.getEditor()==null) return;
			colorPicker.setListener(colorPickerListener);
			if(colorPicker.getStage()!=null || colorPicker.getParent()!=null){
				colorPicker.remove();
				return;
			}
			//app.getEditor().updateConfig();
			sceneColorBeforePick = app.getEditor().backgroundColor;//app.getEditor().getConfig().containsKey("sceneColor")?app.getEditor().getConfig().getString("sceneColor"):"#263238";
			oldSceneConfig = app.getEditor().getConfig().toString();
			getStage().addActor(colorPicker);
		});
		lockBtn = addIconToTop("lock-item",drawable("lock.png"),(btn)->{
			if(app.getEditor()==null) return;
			if (app.getEditor().getSelectedActor() != null) {
                PropertySet<String, Object> propertySet = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
                boolean isLock = propertySet.getString("lock").equals("true") ? false : true;
                btn.getStyle().imageUp = drawable(isLock?"lock.png":"unlock.png");
                propertySet.put("lock", String.valueOf(isLock));
            }
		});
		
		topContainer.add().growX();
		
		backBtn = addIconToTop("back",drawable("back_arrow.png"),btn->{
			//Gdx.app.exit();
			new ConfirmDialog("Exit","Do you want to exit ?",(ok)->{
			    if(ok)
				    app.closeProject();
			}).show(getStage());
		});
		
		//bottom buttons
		addIconToBottom("play",drawable("play.png"),(btn)->{
			btn.setDisabled(true);
			/*
			cancelBtn.setDisabled(true);
			copyBtn.setDisabled(true);
			compileDialog.show(getStage());
			*/
			textShow.setEnabled(false);
			textShow.toFront();
			textShow.show(getStage());
			getStage().addActor(textShow);
			final InputProcessor inputProcessor = Gdx.input.getInputProcessor();
			//disable all inputs...
			Gdx.input.setInputProcessor(null);
			CodeGenerator.generateFor(app.getEditor(),(code)->{
				Gdx.files.absolute(app.getEditor().getProject().getCodesPath(app.getEditor().getScene())).writeString(code,false);
				CompileThread compileThread = new CompileThread(app.getEditor().getProject().get("java"),false);
				compileThread.setOnChangeStatus(new CompileThread.OnStatusChanged(){
					@Override
					public void onStatus(String s) {
						textShow.setText(s);
						/*
						compileLabel.setText(s);
						scrollPane.pack();
						*/
					}

					@Override
					public void onEnd(String message) {
						Gdx.input.setInputProcessor(inputProcessor);
						textShow.setEnabled(true);
						/*
						cancelBtn.setDisabled(false);
						copyBtn.setDisabled(false);
						*/
					}

					@Override
					public void onError(String error) {
						textShow.setText(error);
						Gdx.files.external("logs/compile error.txt").writeString(error,false);
						/*
						compileLabel.setText(error);
						scrollPane.pack();
						*/
					}

					@Override
					public void onSuccess(String message) {
						//compileDialog.hide();
						//textShow.hide();
						textShow.remove();
						FileHandle fileHandle = new FileHandle(app.getEditor().getProject().getDex());
						
						if(fileHandle.exists())
							fileHandle.file().setWritable(true);
						fileHandle.writeString("", false);
						Gdx.files.absolute(app.getEditor().getProject().getPath()+"/java/classes.dex").moveTo(fileHandle);
						
						app.play(app.getEditor().getProject().getPath(),app.getEditor().getScene());
					}
					
				});
				compileThread.start();
			});
		});
		
		gridBtn = addIconToBottom("grid",drawable("grid-icon.png"),(btn)->{
			gridBtn.setColor(Color.YELLOW);
			moveBtn.setColor(Color.WHITE);
			scaleBtn.setColor(Color.WHITE);
			rotateBtn.setColor(Color.WHITE);
			clickedAction = "";
			app.getEditor().setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.GRID);
		});
		gridBtn.setColor(Color.YELLOW);
		
		moveBtn = addIconToBottom("move",drawable("move-icon.png"),(btn)->{
			if(app.getEditor().setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.MOVE)){
				if(clickedAction.equals("move")){
					showXYMenu(btn,"move");
				} else {
					moveBtn.setColor(Color.YELLOW);
					gridBtn.setColor(Color.WHITE);
					scaleBtn.setColor(Color.WHITE);
					rotateBtn.setColor(Color.WHITE);
					clickedAction = "move";
				}
			}
		});
		
		scaleBtn = addIconToBottom("scale",drawable("scale-icon.png"),(btn)->{
			if(app.getEditor().setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.SCALE)){
				if(clickedAction.equals("scale")){
					showXYMenu(btn,"scale");
				} else {
					scaleBtn.setColor(Color.YELLOW);
					gridBtn.setColor(Color.WHITE);
					moveBtn.setColor(Color.WHITE);
					rotateBtn.setColor(Color.WHITE);
					clickedAction = "scale";
				}
			}
		});
		
		rotateBtn = addIconToBottom("rotate",drawable("rotate-icon.png"),(btn)->{
			if(app.getEditor().setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.ROTATE)){
				rotateBtn.setColor(Color.YELLOW);
				gridBtn.setColor(Color.WHITE);
				scaleBtn.setColor(Color.WHITE);
				moveBtn.setColor(Color.WHITE);
				clickedAction = "";
			}
		});
		
		addIconToBottom("delete",drawable("delete.png"),(btn)->{
			if(app.getEditor().getSelectedActor()==null) return;
			app.getEditor().getProject().deleteBody(PropertySet.getPropertySet(app.getEditor().getSelectedActor()).get("name").toString(), app.getEditor().getScene());
            Actor actor = app.getEditor().getSelectedActor();
			actor.remove();
			EditorAction.itemRemoved(app.getEditor(),actor);
            app.getEditor().selectActor(null);
			bodiesList.update();
		});
		
		undoBtn = addIconToBottom("undo",drawable("undo.png"),(btn)->{
			if(app.getEditor().canUndo())
				app.getEditor().undo();
		});
		
		redoBtn = addIconToBottom("redo",drawable("redo.png"),(btn)->{
			if(app.getEditor().canRedo())
				app.getEditor().redo();
		});
		
		addIconToBottom("center-camera",drawable("center-camera.png"),(btn)->{
			app.getEditor().centerCamera();
		});
		
	}
	
	public void showXYMenu(Actor actor,String action){
		xyMenu.showMenu(getStage(),actor);
	}
	
	public ColorPicker getColorPicker(){
		return colorPicker;
	}
	
	private void openScene(String name){
		CodeGenerator.generateFor(app.getEditor(), cd -> {
			Gdx.files.absolute(app.getEditor().getProject().getCodesPath(app.getEditor().getScene())).writeString(cd,false);
			/*
			app.getEditor().setScene(name);
			app.getEditor().loadFromPath();
			//generate code for the new scene and save it
			CodeGenerator.generateFor(app.getEditor(), code -> {
				Gdx.files.absolute(app.getEditor().getProject().getCodesPath(app.getEditor().getScene())).writeString(code,false);
			});
			bodiesList.update();
			*/
			app.openSceneInNewEditor(name);
		});
	}
	
	public void editScene(String name,SceneAction sceneAction){
		String path = app.getEditor().getProject().getScenesPath()+name;
		if (name.equals("")) {
			app.toast("Can\'t be empty!!");
			dialogForScene(sceneAction,name);
			return;
		}
		for (char c : name.toCharArray()) {
			//only possible chars...to prevent writing strange chars :|
			if (!(chars.contains("" + c))) {
				app.toast("Not Allowed char : " + c);
				dialogForScene(sceneAction,name);
				return;
			}
		}
		FileHandle fhandle = Gdx.files.absolute(path);
		if (fhandle.exists()) {
			app.toast("There is scene with the same name..!!");
			dialogForScene(sceneAction,name);
			return;
		}
		fhandle.writeString("",false);
		if (sceneAction == SceneAction.CREATE) {
			//nothing to do...
		} else if (sceneAction == SceneAction.RENAME) {
			app.getEditor().getProject().renameScene(app.getEditor().getScene(), name);
			EditorAction.renameScene(app.getEditor(),app.getEditor().getScene(),name);
			app.toast("Scene Renamed...");
		} else if (sceneAction == SceneAction.COPY) {
			app.getEditor().getProject().copyScene(app.getEditor().getScene(),name);
			app.toast("Scene Copied...");
		}
		if (fhandle.exists()) {
			if(sceneAction == SceneAction.RENAME){
				app.getEditor().setScene(name);
				//app.getEditor().loadFromPath();
			} else if(sceneAction == SceneAction.COPY || sceneAction == SceneAction.CREATE){
				app.openSceneInNewEditor(name);
			} else if(sceneAction == SceneAction.DELETE){
				app.openSceneInNewEditor("scene1");
			}
			tabsItem.refresh();
			//updateScenes();
		} else {
			app.toast("Failed to create the path..");
			dialogForScene(sceneAction,name);
			return;
		}
	}
	
	public void dialogForScene(SceneAction sceneAction,String initial){
		new SingleInputDialog(sceneAction.name(),"Enter Name",initial,(text)->{
			editScene(text,sceneAction);
		}).show(getStage());
	}
	
	public enum SceneAction {
		RENAME,DELETE,CREATE,COPY
	}
	
	public void updateUndoRedo(){
		undoBtn.setDisabled(!app.getEditor().canUndo());
		undoBtn.setColor(app.getEditor().canUndo()?Color.YELLOW:Color.WHITE);
		
		redoBtn.setDisabled(!app.getEditor().canRedo());
		redoBtn.setColor(app.getEditor().canRedo()?Color.YELLOW:Color.WHITE);
	}
	
	public void bodySelected(){
		if (app.getEditor().getSelectedActor() != null) {
            String isLock = PropertySet.getPropertySet(app.getEditor().getSelectedActor()).getString("lock");
            lockBtn.getStyle().imageUp = (drawable(isLock.equals("true") ? "lock.png" : "unlock.png"));
        }
		bodiesList.update();
		try {
			propertiesItem.refresh();
		} catch(Exception e){
			Gdx.files.external("logs/app_logs.txt").writeString("error on control layer code for refreshing : "+e.toString()+"\n",true);
		}
	}
	
	private Drawable drawable(String name){
		return new TextureRegionDrawable(new Texture(Gdx.files.internal("images/"+name)));
	}
	
	private PopupMenu createBodiesMenu(){
		PopupMenu menu = new PopupMenu();
		String[] names={"Box Body","Circle Body","Text Item","Joystick Item","Progress Bar","Custom Body","Particle Effect"};
		String[] icons={"box","circle","text","joystick","progress","custom","particle"};
		for(int x = 0; x < names.length; x++){
			final String name = names[x];
			final int pos = x;
			MenuItem item = new MenuItem(name,drawable("bodies/"+icons[x]+".png"),new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
					EditorItem item = getItem(pos);
                	app.getEditor().addActor((Actor)item);
                	((Actor)item).setName(app.getEditor().getName((Actor)item));
                	try {
						java.lang.reflect.Method method = item.getClass().getMethod("setDefault");
						Object[] objs = null;
						method.invoke(item,objs);
					} catch(Exception e){}
                	item.getPropertySet().put("z",app.getEditor().getActors().size);
                	app.getEditor().selectActor((Actor)item);
                	item.update();
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
		}
		return null;
	}
	
	private PopupMenu createLightMenu(){
		PopupMenu menu = new PopupMenu();
		String[] list = {"Point Light","Directional Light","Cone Light","Light Settings"};
		for(int x = 0; x < 4; x++){
			final int pos = x;
			menu.addItem(new MenuItem(list[x],drawable("light.png"),new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
					if(pos == 3){
						LightSettingsDialog.showFor(new Project(app.getEditor().getProject().getPath()),app.getEditor().getScene());
					} else {
						LightItem lightItem = new LightItem(app.getEditor());
            			app.getEditor().addActor(lightItem);
            			lightItem.setName(app.getEditor().getName(lightItem));
            			lightItem.setDefault(getLightType(pos));
            			lightItem.getPropertySet().put("z",app.getEditor().getActors().size);
            			app.getEditor().selectActor(lightItem);
            			lightItem.update();
					}
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
	
	
	public TabsItem tabsItem;
	
    private void createLayout() {
        // Top section
        topContainer = new Table();
        topScrollPane = new VisScrollPane(topContainer);
        topScrollPane.setScrollingDisabled(true, false); // Vertical scroll disabled
        topScrollPane.setFadeScrollBars(false);
		topScrollPane.getStyle().background = VisUI.getSkin().getDrawable("window-bg");
        
        // Bottom section
        bottomContainer = new Table();
        bottomScrollPane = new VisScrollPane(bottomContainer);
        bottomScrollPane.setScrollingDisabled(true, false);
        bottomScrollPane.setFadeScrollBars(false);
		bottomScrollPane.getStyle().background = VisUI.getSkin().getDrawable("window-bg");

        // Main layout
        setFillParent(true);
        defaults().growX();
        
        add(topScrollPane).height(iconSize + 10).padBottom(1).row();
        Table centerTable = new Table();
		centerTable.right();
		//centerTable.setFillParent(true);
		VisImageButton bodiesListBtn = new VisImageButton(drawable("layer.png")),
				eventsBtn = new VisImageButton(drawable("pointer-icon.png")),
				jointsBtn = new VisImageButton(drawable("link.png")),
				propsBtn = new VisImageButton(drawable("events/properties.png"));
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
		tabsItem = new TabsItem(app);
		windowsTable = new Table();
		//closeBtn = new VisImageButton(VisUI.getSkin().getDrawable("icon-close"));
		Table btnsTable = new Table();
		centerTable.add(windowsTable).growX().growY();
		centerTable.add(btnsTable).width(iconSize+2);
		btnsTable.add(bodiesListBtn).size(iconSize+2).row();
		btnsTable.add(propsBtn).size(iconSize+2).padTop(2).row();
		btnsTable.add(jointsBtn).size(iconSize+2).padTop(2).row();
		btnsTable.add(eventsBtn).size(iconSize+2).padTop(2);
		
		bodiesList.setVisible(false);
		eventsItem.setVisible(false);
		jointsList.setVisible(false);
		propertiesItem.setVisible(false);
		addListenerToWindow(bodiesList,bodiesListBtn);
		addListenerToWindow(eventsItem,eventsBtn);
		addListenerToWindow(jointsList,jointsBtn);
		addListenerToWindow(propertiesItem,propsBtn);
		left();
		add(tabsItem).growX().row();
		add(centerTable).growX().growY().row();
        add(bottomScrollPane).height(iconSize + 10).padTop(5);
    }
	
	//when the button clicked, add the item to the window...
	private void addListenerToWindow(Actor item,Actor btn){
		btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                boolean isOnWindow = item.isVisible();
				if(!isOnWindow){
					windowsTable.clear();
					//windowsTable.right();
					//windowsTable.add(closeBtn).size(iconSize,iconSize).padBottom(3).row();
					windowsTable.right();
					windowsTable.add(item).width(Gdx.graphics.getWidth()*0.4f).padTop(5).growY();
					//app.toast("item added!");
				} else item.remove();
				item.setVisible(!isOnWindow);
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
		//button.setStyle(buttonStyle);
        //button.setName(name);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                iconListener.click(button);
            }
        });
        
        container.add(button).size(iconSize).pad(2);
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
}