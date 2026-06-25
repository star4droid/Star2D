package com.star4droid.star2d;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.android.AndroidFragmentApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Activities.AnimationActivity;
import com.star4droid.star2d.Adapters.ExportDialog;
import com.star4droid.star2d.Adapters.SPNote;
import com.star4droid.star2d.CodeEditor.MyIndexer;
import com.star4droid.star2d.Helpers.AndroidStatics;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.CompileThread;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.FilesChangeDetector;
import com.star4droid.star2d.Helpers.JointsHelper;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.UriUtils;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.Items.*;
import com.star4droid.star2d.Items.Editor;

import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.BoxItem;
import com.star4droid.star2d.editor.items.CircleItem;
import com.star4droid.star2d.editor.items.CustomItem;
import com.star4droid.star2d.editor.items.EditorCameraItem;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.items.EditorMapItem;
import com.star4droid.star2d.editor.items.EditorProgressItem;
import com.star4droid.star2d.editor.items.EditorTextItem;
import com.star4droid.star2d.editor.items.JoyStickItem;
import com.star4droid.star2d.editor.items.LightItem;
import com.star4droid.star2d.editor.items.ParticleItem;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.star2d.evo.R;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

public class EditorActivity extends AppCompatActivity implements AndroidFragmentApplication.Callbacks {
    Editor editor;
	ActivityResultLauncher<String[]> files_picker;
	ActivityResultLauncher saveFile;
    Project project;
    Uri source;
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_",
        filePickerAction = "",exported_project="",saveType="";

    // Native UI view references
    private ImageView btnBack, btnSave, btnFiles, btnLogs, btnRotateScreen, btnAdd, btnScene, btnColor, btnLock;
    private ImageView btnAddScene, btnPlay, btnGrid, btnMove, btnScale, btnRotateMode, btnDelete, btnUndo, btnRedo;
    private ImageView btnCenterCamera, btnSettings;
    private ImageView navBodies, navProperties, navJoints, navEvents, navVariables, navAi;
    private TextView indexingLabel;
    private View leftContainer, rightContainer, bottomPanel, overlayControls, topToolbar;
    private final int TINT_ACTIVE = 0xFFFFB300; // yellow
    private final int TINT_INACTIVE = 0xFFFFFFFF; // white
    private FileHandle fileBrowserDir;
    private String oldSceneConfig = "";
    private com.badlogic.gdx.graphics.Color sceneColorBeforePick = com.badlogic.gdx.graphics.Color.BLACK;

    public static ArrayAdapter getSpinnerAdapter(ArrayList<String> arrayList, Context context, final Spinner spinner) {
        return new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, arrayList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // Get the default view from the ArrayAdapter
                View view = super.getView(position, convertView, parent);

                // Cast the view to a TextView
                TextView textView = (TextView) view;
                textView.setPadding(8, 12, 8, 12);
                // Set the text color
                textView.setTextColor(getContext().getColor(R.color.text_color));
                //textView.setBackgroundColor(getContext().getColor(R.color.button_background));
				/*
				if(Build.VERSION.SDK_INT<30)
				textView.setBackgroundDrawable(getContext().getDrawable(R.drawable.section_field));
				else textView.setBackground(getContext().getDrawable(R.drawable.section_field));
				*/
                textView.setTextSize(12);
                return view;
            }

            @Override
            public View getDropDownView(final int position, View convertView, ViewGroup parent) {
                // Get the default drop-down view from the ArrayAdapter
                View view = super.getDropDownView(position, convertView, parent);

                // Cast the view to a TextView
                TextView textView = (TextView) view;
                textView.setPadding(8, 12, 8, 12);//left ,top, right, bottom

                // Set the text color
                textView.setTextColor(getContext().getColor(R.color.text_color));
                textView.setBackgroundColor(getContext().getColor(R.color.button_background));
				/*
				if(Build.VERSION.SDK_INT<30)
					textView.setBackgroundDrawable(getContext().getDrawable(R.drawable.section_field));
				else textView.setBackground(getContext().getDrawable(R.drawable.section_field));
				*/
                textView.setTextSize(12);

                return view;
            }
        };
    }

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        try {
            Utils.extractAssetFile(this, "cp.zip", FileUtil.getPackageDataDir(this) + "/bin/cp.jar");
        } catch (Exception e) {
            throw new RuntimeException("extracting cp error "+e);
        }
		files_picker =
        registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uriList -> {
				String path = editor.getApp().getFileBrowser() != null ? editor.getApp().getFileBrowser().getCurrentDir().file().getAbsolutePath() : "";
				for(Uri uri:uriList){
				    if(filePickerAction.equals("files")){
    					String last = "";
    					try {
    					    //last = Uri.fromFile(new java.io.File(FileUtil.convertUriToFilePath(EditorActivity.this,uri))).getLastPathSegment();
    					    last = Objects.requireNonNull(DocumentFile.fromSingleUri(EditorActivity.this, uri)).getName();
    					    if(last.contains("/"))
    					        last = last.substring(last.lastIndexOf("/"),last.length());
    					} catch(Exception ex){
    					    Gdx.app.postRunnable(()->editor.getApp().toast("error : "+ex.toString()));
    					    return;
    					}
    					String to = path+"/"+last;
    					FileUtil.writeFile(to,"");
    					//FileUtil.writeFile(getExternalFilesDir("logs")+"/file.txt","to : "+to);
    					UriUtils.copyUriToUri(EditorActivity.this,uri,Uri.fromFile(new java.io.File(to)));
						editor.getApp().getFileBrowser().refreshFileList();
    				} else if(filePickerAction.equals("import")){
						Gdx.app.postRunnable(()->editor.getApp().toast("Restoring..."));
    				    try {
							restoreProject(getContentResolver().openInputStream(uri));
						} catch(Exception e){}
    				}
				}
				
			});
		
		saveFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),new ActivityResultCallback<ActivityResult>(){
			@Override
			public void onActivityResult(ActivityResult result) {
				if(result==null||result.getData()==null) return;
				if(saveType.equals("export")){
					Uri target = result.getData().getData();
					ExportDialog.showFor(EditorActivity.this,exported_project,target);
					return;
				}
				Uri uri = result.getData().getData();
				try {
					Utils.saveFileToPath(source,uri,EditorActivity.this);
					FileUtil.deleteFile(source.getPath());
					Utils.showMessage(EditorActivity.this,"Saved ...");
				} catch (Exception ex){
					Utils.showMessage(EditorActivity.this,"save file error : "+ex.toString());
				}
			}
		});
		/*
		new ANRWatchDog().setANRListener(new ANRWatchDog.ANRListener() {
    		@Override
    		public void onAppNotResponding(ANRError error) {
				FileUtil.writeFile(getExternalFilesDir("logs")+"/error.txt",Utils.getStackTraceString(error));
        		//ExceptionHandler.saveException(error, new CrashManager());
    		}
		}).start();
		*/
        EngineSettings.init(this);

        setContentView(R.layout.editor);
		init();
        
        String projectPath = getIntent().getStringExtra("project");
        if(projectPath == null) projectPath = "";
        project = new Project(projectPath);
        
        editor.setProject(project);
        AndroidStatics.setPaths(project.getPath());
		
        //editor.setScene("scene1");
        //editor.loadFromPath();
		//editor.setOrienation(editor.getConfig().getString("or").equals("")?Editor.ORIENATION.PORTRAIT:Editor.ORIENATION.LANDSCAPE);
		
		editor.setEditorReadyAction(()->{
			continueInit();
		});
		editor.setWhenAppReady(()->{
			initApp();
		});

//        editor.setEditorListener(new Editor.EditorListener() {
//            @Override
//            public void onUpdateUndoRedo() {}
//
//            @Override
//            public void onBodySelected() {
//                AndroidStatics.updateBodiesList();
//            }
//        });
	}
	
	private void restoreProject(InputStream inputStream) {
		 new Thread(){
			 public void run(){
				 try {
				 Utils.unzipf(inputStream,getFilesDir()+"/projects/","");
				 Gdx.app.postRunnable(()->{
					 editor.getApp().toast("project restored...");
				 	editor.getApp().getProjectsStage().refresh();
				 });
				 } catch(Exception ex){}
			 }
		 }.start();
    }
	
	private void openAnimation(String file){
		Intent intent = new Intent();
		intent.setClass(getApplicationContext(), AnimationActivity.class);
		intent.putExtra("path", file);
		intent.putExtra("imgs", editor.getProject().getImagesPath());
		startActivity(intent);
	}
	
	public void openJava(String path){
	    Intent intent= new Intent();
		intent.putExtra("path",path);
		intent.setClass(this,com.star4droid.star2d.Activities.CodeEditorActivity.class);
		startActivity(intent);
	}
	//useless : to change your game icon, go to directory "/icon/" and put your icon there...
	// @Override
    // protected void onActivityResult(int code, int result, Intent data) {
        // super.onActivityResult(code, result, data);
        // if (result == RESULT_OK) {
            // if (code == ExportDialog.RECIEVE_ICON){
                // for(Uri uri:Utils.getUriList(code, result,data,this)){
                    // String path = getFilesDir()+"/temp/"+uri.getLastPathSegment();
                    // Uri destination = Uri.fromFile(new java.io.File(path));
                    // UriUtils.copyUriToUri(this, uri, destination);
                    // ExportDialog.imgPath = path;
                    
                    // break;//we need only the first one, which is icon...
                // }
            // }
        // }
    // }
	private void initApp(){
	    JointsHelper.init();
	    com.star4droid.star2d.Adapters.UpdateChecker.checkForUpdate(editor.getApp());
		editor.getApp().setOrienationChanger(landscape->{
			boolean isCurrentLandscape = getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
			if(isCurrentLandscape == landscape) return;
			setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			//String ex = android.util.Log.getStackTraceString(new Exception("Landscape : "+landscape+"\n"))+"\n"+"__".repeat(10)+"\n";
			//Gdx.files.external("logs/landscape.txt").writeString(ex,true);
		});
		editor.getApp().openDonate = ()->{
		    Intent intent = new Intent();
    		intent.setClass(getApplicationContext(), com.star4droid.star2d.Activities.DonateActivity.class);
    		startActivity(intent);
		};
		editor.getApp().getProjectsStage().setExportRunnable(()->{
			exported_project = editor.getApp().getProjectsStage().getSelectedProject().file().getAbsolutePath();
			saveType = "export";
			String name = editor.getApp().getProjectsStage().getSelectedProject().name();
			Utils.saveFile(name+".apk",saveFile);
		});
		editor.getApp().getProjectsStage().setBackupRunnable(()->{
			saveType = "backup";
			String name = editor.getApp().getProjectsStage().getSelectedProject().name();
			String exportPath = Gdx.files.external("Star2D/backups/"+name+".zip").file().getAbsolutePath();
			String path = editor.getApp().getProjectsStage().getSelectedProject().file().getAbsolutePath();
			editor.getApp().toast("Please wait...");
			Executors.newSingleThreadExecutor().execute(()->{
				try {
					Utils.createEmptyZipFile(exportPath);
					Utils.zipf(path,exportPath,"");
					source = Uri.fromFile(new java.io.File(exportPath));
					Utils.saveFile(name+".zip",saveFile);
				} catch(Exception e){
					
				}
			});
		});
		editor.getApp().getProjectsStage().setImportRunnable(()->{
		    filePickerAction = "import";
		    files_picker.launch(new String[] {"application/zip"});
		});

		// Register visibility callbacks so the native overlay is shown only while editing.
		// NOTE: temporarily disabled — keep ControlLayer (libgdx) controls and hide Android overlay.
		editor.getApp().onEnterEditing = () -> runOnUiThread(() -> setControlsVisible(false));
		editor.getApp().onExitEditing = () -> runOnUiThread(() -> setControlsVisible(false));
	}
	
	private void continueInit(){
        indexFiles();
		/*
		editor.getApp().getFileBrowser().setAnimationOpen(file->{
			openAnimation(file);
		});
		*/
		
		editor.getApp().getFileBrowser().setOpenJavaRunnable(file->{
			openJava(file);
		});
		
		editor.getApp().getFileBrowser().setPickFilesRunnable(()->{
		    this.filePickerAction = "files";
			files_picker.launch(new String[] {"*/*"});
		});
		//SPNote.show(this);

		// NOTE: temporarily keep ControlLayer (libgdx) controls and hide the Android overlay.
		// To re-enable native UI later, uncomment the block below:
		/*
		Gdx.app.postRunnable(() -> {
			if(editor.getApp().getControlLayer() != null){
				editor.getApp().getControlLayer().setNativeUiMode(true);
			}
		});
		*/

		// Show the project files in the bottom panel by default
		AndroidStatics.setPaths(project.getPath());

		// The editor is now ready: register the listener and refresh state-dependent UI.
		onEditorReady();
		// Native overlay visibility is handled by the onEnterEditing callback
		// (fired at the end of TestApp.onLoad) which runs on the UI thread.
    }

    public void init() {
        editor = findViewById(R.id.editor);
        RecyclerView itemsList = findViewById(R.id.items_list);
        GridView gridView = findViewById(R.id.gridView);
        EditText searchBodies = findViewById(R.id.search_for_item);
        EditText searchFiles = findViewById(R.id.searchBar);
        AndroidStatics.init(itemsList, gridView, searchBodies, searchFiles);

        leftContainer = findViewById(R.id.left_container);
        rightContainer = findViewById(R.id.right_container);
        bottomPanel = findViewById(R.id.bottom_panel);
        overlayControls = findViewById(R.id.overlay_controls);
        topToolbar = findViewById(R.id.top_toolbar);

        // Top toolbar icons
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save);
        btnFiles = findViewById(R.id.btn_files);
        btnLogs = findViewById(R.id.btn_logs);
        btnRotateScreen = findViewById(R.id.btn_rotate);
        btnAdd = findViewById(R.id.btn_add);
        btnScene = findViewById(R.id.btn_scene);
        btnColor = findViewById(R.id.btn_color);
        btnLock = findViewById(R.id.btn_lock);
        indexingLabel = findViewById(R.id.indexing_label);

        // Scene tabs
        btnAddScene = findViewById(R.id.btn_add_scene);

        // Bottom actions toolbar
        btnPlay = findViewById(R.id.btn_play);
        btnGrid = findViewById(R.id.btn_grid);
        btnMove = findViewById(R.id.btn_move);
        btnScale = findViewById(R.id.btn_scale);
        btnRotateMode = findViewById(R.id.btn_rotate_mode);
        btnDelete = findViewById(R.id.btn_delete);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnCenterCamera = findViewById(R.id.btn_center_camera);
        btnSettings = findViewById(R.id.btn_settings);

        // Navigation sidebar
        navBodies = findViewById(R.id.nav_bodies);
        navProperties = findViewById(R.id.nav_properties);
        navJoints = findViewById(R.id.nav_joints);
        navEvents = findViewById(R.id.nav_events);
        navVariables = findViewById(R.id.nav_variables);
        navAi = findViewById(R.id.nav_ai);

        // Panel toggles
        findViewById(R.id.open_left_panel).setOnClickListener(v -> {
            boolean isVisible = leftContainer.getVisibility() == View.VISIBLE;
            leftContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            v.setRotation(isVisible ? 90 : 270);
        });

        findViewById(R.id.open_right_panel).setOnClickListener(v -> {
            boolean isVisible = rightContainer.getVisibility() == View.VISIBLE;
            rightContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            v.setRotation(isVisible ? 270 : 90);
        });

        findViewById(R.id.assets_tab).setOnClickListener(v -> {
            boolean isVisible = bottomPanel.getVisibility() == View.VISIBLE;
            bottomPanel.setVisibility(isVisible ? View.GONE : View.VISIBLE);
        });

        // Wire all toolbar actions (delegating to ControlLayer / libgdx editor)
        wireBack();
        wireSave();
        wireFiles();
        wireLogs();
        wireRotateScreen();
        wireAdd();
        wireScene();
        wireColor();
        wireLock();
        wireAddScene();
        wirePlay();
        wireTouchModes();
        wireDelete();
        wireUndoRedo();
        wireCenterCamera();
        wireSettings();
        wireNavigation();

        // Start hidden: the projects list shows first, overlay appears once a project is opened.
        setControlsVisible(false);
    }

    // ============================ Action wiring ============================

    private void runOnGdx(Runnable r){
        Gdx.app.postRunnable(r);
    }

    private ControlLayer cl(){
        return editor.getApp().getControlLayer();
    }

    /** Creates a temporary actor at the given screen-fraction position so PopupMenu can anchor there. */
    private com.badlogic.gdx.scenes.scene2d.Actor menuAnchor(float xFraction, float yFraction){
        com.badlogic.gdx.scenes.scene2d.Actor anchor = new com.badlogic.gdx.scenes.scene2d.Actor();
        anchor.setPosition(
            Gdx.graphics.getWidth() * xFraction,
            Gdx.graphics.getHeight() * yFraction);
        return anchor;
    }

    private void setTint(ImageView iv, int color){
        iv.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    private void setTouchModeTint(String active){
        setTint(btnGrid, active.equals("grid") ? TINT_ACTIVE : TINT_INACTIVE);
        setTint(btnMove, active.equals("move") ? TINT_ACTIVE : TINT_INACTIVE);
        setTint(btnScale, active.equals("scale") ? TINT_ACTIVE : TINT_INACTIVE);
        setTint(btnRotateMode, active.equals("rotate") ? TINT_ACTIVE : TINT_INACTIVE);
    }

    private void wireBack(){
        btnBack.setOnClickListener(v -> {
            new com.star4droid.star2d.editor.ui.sub.ConfirmDialog(
                com.star4droid.star2d.editor.utils.Lang.getTrans("exit"),
                com.star4droid.star2d.editor.utils.Lang.getTrans("exitConfirm"),
                ok -> { if(ok) editor.getApp().closeProject(); }
            ).show(editor.getApp().getUiStage());
        });
    }

    private void wireSave(){
        btnSave.setOnClickListener(v -> {
            if(editor.getLibgdxEditor() == null){ toast("editor returns null!!"); return; }
            editor.getProject().save(editor.getLibgdxEditor());
            CodeGenerator.generateFor(editor.getLibgdxEditor(), cd -> {
                Gdx.files.absolute(editor.getProject().getCodesPath(editor.getLibgdxEditor().getScene())).writeString(cd,false);
            });
            toast(com.star4droid.star2d.editor.utils.Lang.getTrans("saved"));
        });
    }

    private void wireFiles(){
        btnFiles.setOnClickListener(v -> {
            // Toggle the bottom Assets panel which contains the native GridView file browser.
            if(bottomPanel != null){
                boolean show = bottomPanel.getVisibility() != View.VISIBLE;
                bottomPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void wireLogs(){
        btnLogs.setOnClickListener(v -> {
            if(editor.getApp().getFileBrowser() == null) return;
            if(fileBrowserDir == null)
                fileBrowserDir = editor.getApp().getFileBrowser().getCurrentDir();
            editor.getApp().getFileBrowser().setRootDir(Gdx.files.external("logs"));
            editor.getApp().getFileBrowser().setVisible(!editor.getApp().getFileBrowser().isVisible());
            if(editor.getApp().getFileBrowser().isVisible()){
                editor.getApp().getFileBrowser().toFront();
                toast(com.star4droid.star2d.editor.utils.Lang.getTrans("showLogsFolder"));
            }
        });
    }

    private void wireRotateScreen(){
        btnRotateScreen.setOnClickListener(v -> {
            new com.star4droid.star2d.editor.ui.sub.ConfirmDialog(
                com.star4droid.star2d.editor.utils.Lang.getTrans("changeOrienation"),
                com.star4droid.star2d.editor.utils.Lang.getTrans("editorRestartNote"),
                ok -> { if(ok) editor.getApp().setOrienation(!editor.getLibgdxEditor().isLandscape()); }
            ).show(editor.getApp().getUiStage());
        });
    }

    private void wireAdd(){
        btnAdd.setOnClickListener(v -> {
            if(cl() != null && cl().getCreateMenu() != null)
                cl().getCreateMenu().showMenu(editor.getApp().getUiStage(), menuAnchor(0.1f, 0.95f));
        });
    }

    private void wireScene(){
        btnScene.setOnClickListener(v -> {
            if(cl() != null && cl().getSceneActionsMenu() != null)
                cl().getSceneActionsMenu().showMenu(editor.getApp().getUiStage(), menuAnchor(0.2f, 0.95f));
        });
    }

    private void wireColor(){
        btnColor.setOnClickListener(v -> {
            if(cl() == null || editor.getLibgdxEditor() == null) return;
            com.kotcrab.vis.ui.widget.color.ColorPicker picker = cl().getColorPicker();
            com.badlogic.gdx.graphics.Color before = editor.getLibgdxEditor().backgroundColor;
            oldSceneConfig = editor.getLibgdxEditor().getConfig().toString();
            sceneColorBeforePick = before;
            picker.setListener(new com.kotcrab.vis.ui.widget.color.ColorPickerListener(){
                @Override public void canceled(com.badlogic.gdx.graphics.Color oldColor){
                    final com.badlogic.gdx.graphics.Color beforeInner = sceneColorBeforePick;
                    com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task(){
                        @Override public void run(){
                            if(editor.getLibgdxEditor()!=null)
                                editor.getLibgdxEditor().setSceneColor(beforeInner.toString().toUpperCase());
                        }
                    },800);
                }
                @Override public void changed(com.badlogic.gdx.graphics.Color color){
                    if(editor.getLibgdxEditor()==null) return;
                    editor.getLibgdxEditor().setSceneColor(color.toString().toUpperCase());
                }
                @Override public void reset(com.badlogic.gdx.graphics.Color a, com.badlogic.gdx.graphics.Color b){}
                @Override public void finished(com.badlogic.gdx.graphics.Color color){
                    if(editor.getLibgdxEditor()==null) return;
                    picker.setListener(null);
                    EditorAction.sceneConfigChanged(editor.getLibgdxEditor(), oldSceneConfig, editor.getLibgdxEditor().getConfig().toString());
                }
            });
            if(picker.getStage()!=null || picker.getParent()!=null){
                picker.remove();
                return;
            }
            editor.getApp().getUiStage().addActor(picker);
            picker.toFront();
        });
    }

    private void wireLock(){
        btnLock.setOnClickListener(v -> {
            if(editor.getLibgdxEditor()==null) return;
            com.badlogic.gdx.scenes.scene2d.Actor selected = editor.getLibgdxEditor().getSelectedActor();
            if(selected != null){
                PropertySet<String,Object> ps = PropertySet.getPropertySet(selected);
                boolean isLock = !ps.getString("lock").equals("true");
                ps.put("lock", String.valueOf(isLock));
                btnLock.setImageResource(isLock ? R.drawable.lock : R.drawable.unlock);
            }
        });
    }

    private void wireAddScene(){
        btnAddScene.setOnClickListener(v -> {
            if(cl() != null)
                cl().dialogForScene(ControlLayer.SceneAction.CREATE, "Scene");
        });
    }

    private void wirePlay(){
        btnPlay.setOnClickListener(v -> {
            if(editor.getLibgdxEditor() == null) return;
            btnPlay.setEnabled(false);
            String path = editor.getProject().getPath();
            final String projectName = path.contains("/") ? path.substring(path.lastIndexOf("/"),path.length()) : path;
            final FileHandle errorLog = Gdx.files.external("logs/"+projectName+"/compile error.txt");

            final AlertDialog dialog = Utils.showMessage(this, com.star4droid.star2d.editor.utils.Lang.getTrans("generatingCode"), false);
            final InputProcessor inputProcessor = Gdx.input.getInputProcessor();
            dialog.setCancelable(true);
            dialog.setOnCancelListener(d -> {
                // Restore input if user cancels during compilation
                Gdx.input.setInputProcessor(inputProcessor);
                btnPlay.setEnabled(true);
            });
            Gdx.input.setInputProcessor(null);

            CodeGenerator.generateFor(editor.getLibgdxEditor(), (code) -> {
                FileHandle sceneFile = Gdx.files.absolute(editor.getProject().getCodesPath(editor.getLibgdxEditor().getScene()));
                CompileThread compileThread = new CompileThread(editor.getProject().get("java"), false);
                compileThread.setOnChangeStatus(new CompileThread.OnStatusChanged(){
                    @Override public void onStatus(String s){ runOnUiThread(() -> Utils.updateMessage(dialog, s, false)); }
                    @Override public void onEnd(String message){
                        Gdx.input.setInputProcessor(inputProcessor);
                        runOnUiThread(() -> { btnPlay.setEnabled(true); });
                    }
                    @Override public void onError(String error){
                        runOnUiThread(() -> Utils.updateMessage(dialog, error, true));
                        errorLog.writeString(error, false);
                    }
                    @Override public void onSuccess(String message){
                        if(errorLog.exists()) errorLog.delete();
                        // Dismiss the compile dialog before launching the game
                        runOnUiThread(() -> dialog.dismiss());
                        FileHandle fileHandle = new FileHandle(editor.getProject().getDex());
                        if(fileHandle.exists()) fileHandle.file().setWritable(true);
                        fileHandle.writeString("", false);
                        Gdx.files.absolute(editor.getProject().getPath()+"/java/classes.dex").moveTo(fileHandle);
                        editor.getApp().play(editor.getProject().getPath(), editor.getLibgdxEditor().getScene());
                    }
                });

                if((sceneFile.exists() && !sceneFile.readString().equals(code)) || !sceneFile.exists()){
                    runOnUiThread(() -> Utils.updateMessage(dialog, com.star4droid.star2d.editor.utils.Lang.getTrans("compiling"), false));
                    compileThread.start();
                    sceneFile.writeString(code, false);
                } else {
                    runOnUiThread(() -> Utils.updateMessage(dialog, com.star4droid.star2d.editor.utils.Lang.getTrans("checkJavaFilesChanges"), false));
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            boolean filesChanged = FilesChangeDetector.detect(
                                Gdx.files.absolute(editor.getProject().get("java")).file().toPath(),
                                Gdx.files.absolute(editor.getProject().getChangesJson()).file().toPath());
                            if(!filesChanged){
                                runOnUiThread(() -> dialog.dismiss());
                                Gdx.app.postRunnable(() -> {
                                    if(errorLog.exists())
                                        runOnUiThread(() -> Utils.updateMessage(dialog, errorLog.readString(), true));
                                    else
                                        editor.getApp().play(editor.getProject().getPath(), editor.getLibgdxEditor().getScene());
                                });
                            } else compileThread.start();
                        } catch(Exception e){
                            compileThread.start();
                        }
                    });
                }
            });
        });
    }

    private void wireTouchModes(){
        // grid is active by default
        setTouchModeTint("grid");
        btnGrid.setOnClickListener(v -> {
            String current = cl() != null ? cl().getTouchMode() : "";
            if(current.equals("grid")){
                cl().getXYMenu().showMenu(editor.getApp().getUiStage(), menuAnchor(0.25f, 0.1f));
                return;
            }
            if(cl() != null) cl().setTouchMode("grid");
            else editor.setTouchMode(LibgdxEditor.TOUCHMODE.GRID);
            setTouchModeTint("grid");
        });
        btnMove.setOnClickListener(v -> {
            if(editor.getLibgdxEditor().setTouchMode(LibgdxEditor.TOUCHMODE.MOVE)){
                String current = cl() != null ? cl().getTouchMode() : "";
                if(current.equals("move")){
                    cl().getXYMenu().showMenu(editor.getApp().getUiStage(), menuAnchor(0.3f, 0.1f));
                } else {
                    if(cl() != null) cl().setTouchMode("move");
                    setTouchModeTint("move");
                }
            }
        });
        btnScale.setOnClickListener(v -> {
            if(editor.getLibgdxEditor().setTouchMode(LibgdxEditor.TOUCHMODE.SCALE)){
                String current = cl() != null ? cl().getTouchMode() : "";
                if(current.equals("scale")){
                    cl().getXYMenu().showMenu(editor.getApp().getUiStage(), menuAnchor(0.35f, 0.1f));
                } else {
                    if(cl() != null) cl().setTouchMode("scale");
                    setTouchModeTint("scale");
                }
            }
        });
        btnRotateMode.setOnClickListener(v -> {
            if(editor.getLibgdxEditor().setTouchMode(LibgdxEditor.TOUCHMODE.ROTATE)){
                if(cl() != null) cl().setTouchMode("rotate");
                setTouchModeTint("rotate");
            }
        });
    }

    private void wireDelete(){
        btnDelete.setOnClickListener(v -> {
            if(editor.getLibgdxEditor() == null || editor.getLibgdxEditor().getSelectedActor() == null) return;
            com.badlogic.gdx.scenes.scene2d.Actor actor = editor.getLibgdxEditor().getSelectedActor();
            editor.getProject().deleteBody(PropertySet.getPropertySet(actor).get("name").toString(), editor.getLibgdxEditor().getScene());
            actor.remove();
            EditorAction.itemRemoved(editor.getLibgdxEditor(), actor);
            editor.getLibgdxEditor().selectActor(null);
            if(cl() != null) cl().getBodiesList().update();
        });
    }

    private void wireUndoRedo(){
        btnUndo.setOnClickListener(v -> {
            if(editor.canUndo()){
                editor.undo();
                updateUndoRedoTint();
            }
        });
        btnRedo.setOnClickListener(v -> {
            if(editor.canRedo()){
                editor.redo();
                updateUndoRedoTint();
            }
        });
    }

    /** Called once the LibgdxEditor is ready (from continueInit, on the libgdx thread). */
    private void onEditorReady(){
        // listen for undo/redo updates and body selection from the editor.
        // setEditorListener just registers callbacks that post to the UI thread internally.
        editor.setEditorListener(new Editor.EditorListener(){
            @Override public void onUpdateUndoRedo(){ runOnUiThread(() -> updateUndoRedoTint()); }
            @Override public void onBodySelected(){ runOnUiThread(() -> {
                if(cl() != null) cl().refreshLockButton();
                AndroidStatics.updateBodiesList();
                com.badlogic.gdx.scenes.scene2d.Actor a = editor.getLibgdxEditor().getSelectedActor();
                if(a != null){
                    String isLock = PropertySet.getPropertySet(a).getString("lock");
                    btnLock.setImageResource(isLock.equals("true") ? R.drawable.lock : R.drawable.unlock);
                }
            }); }
        });
        // Refresh the tint on the UI thread (Android views require it).
        runOnUiThread(() -> updateUndoRedoTint());
    }

    private void updateUndoRedoTint(){
        boolean canUndo = editor.canUndo();
        boolean canRedo = editor.canRedo();
        setTint(btnUndo, canUndo ? TINT_ACTIVE : TINT_INACTIVE);
        setTint(btnRedo, canRedo ? TINT_ACTIVE : TINT_INACTIVE);
        btnUndo.setEnabled(canUndo);
        btnRedo.setEnabled(canRedo);
    }

    private void wireCenterCamera(){
        btnCenterCamera.setOnClickListener(v -> {
            if(editor.getLibgdxEditor() != null) editor.getLibgdxEditor().centerCamera();
        });
    }

    private void wireSettings(){
        btnSettings.setOnClickListener(v -> {
            if(editor.getApp().getProjectsStage() != null && editor.getApp().getProjectsStage().settingsDialog != null)
                editor.getApp().getProjectsStage().settingsDialog.show(editor.getApp().getUiStage());
        });
    }

    private void wireNavigation(){
        navBodies.setOnClickListener(v -> { if(cl()!=null) cl().toggleWindow("Bodies-List"); });
        navProperties.setOnClickListener(v -> { if(cl()!=null) cl().toggleWindow("Properties"); });
        navJoints.setOnClickListener(v -> { if(cl()!=null) cl().toggleWindow("Joints"); });
        navEvents.setOnClickListener(v -> { if(cl()!=null) cl().toggleWindow("Events"); });
        navVariables.setOnClickListener(v -> { if(cl()!=null) cl().toggleWindow("Variables"); });
        navAi.setOnClickListener(v -> { if(cl()!=null) cl().toggleWindow("AI"); });
    }

    private void toast(String msg){
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    /** Toggles the native editor overlay (toolbars, panels, sidebar). */
    private void setControlsVisible(boolean visible){
        if(overlayControls != null)
            overlayControls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if(topToolbar != null)
            topToolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    private static int id = 0;
    public void indexFiles() {
        if (!editor.getApp().preferences.getBoolean("Auto Completion", true)) {
            if(editor.getApp().getControlLayer()!=null) editor.getApp().getControlLayer().setIndexing(false);
            runOnUiThread(() -> { if(indexingLabel!=null) indexingLabel.setVisibility(View.GONE); });
            return;
        }
        runOnUiThread(() -> { if(indexingLabel!=null) indexingLabel.setVisibility(View.VISIBLE); });
        id++;
        int currentID = id;
        new Thread() {
            public void run() {
                Looper.prepare();
                editor.setIndexer(MyIndexer.isIndexerMatch(editor.getProject().getPath())?MyIndexer.lastIndexer:new MyIndexer().indexFiles(editor));
                if(currentID != id) return;
                if(editor.getApp().getControlLayer()!=null)
                Gdx.app.postRunnable(()->{
                    editor.getApp().toast(com.star4droid.star2d.editor.utils.Lang.getTrans("indexingFilesCompleted"));
                    try {
                    editor.getApp().getControlLayer().setIndexing(false);
                    } catch(Exception e){}
                    runOnUiThread(() -> { if(indexingLabel!=null) indexingLabel.setVisibility(View.GONE); });
                });
            }
        }.start();
    }

	@Override
	protected void onResume() {
		super.onResume();
		if(editor!=null)
			editor.setToCurrentEditor();
	}
	@Override
	public void exit() {
	    
	}
	
}