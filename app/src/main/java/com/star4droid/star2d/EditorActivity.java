package com.star4droid.star2d;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import java.util.Objects;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidFragmentApplication;
import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Activities.AnimationActivity;
import com.star4droid.star2d.Adapters.ExportDialog;
import com.star4droid.star2d.Adapters.SPNote;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.CompileThread;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.JointsHelper;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.UriUtils;
import com.star4droid.star2d.Items.*;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.Views.ProjectsHomeView;
import com.star4droid.star2d.editor.TestApp;

import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.evo.R;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

public class EditorActivity extends AppCompatActivity implements AndroidFragmentApplication.Callbacks, ProjectsHomeView.Host {
    Editor editor;
    ProjectsHomeView homeView;
    com.star4droid.star2d.ui.EditorShell shell;
    String importTargetDir = null;
    Runnable importDone = null;
    volatile boolean appReady = false;
	ActivityResultLauncher<String[]> files_picker;
	ActivityResultLauncher saveFile;
    Project project;
	Uri source;
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_",
        filePickerAction = "",exported_project="",saveType="";

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
    				} else if(filePickerAction.equals("native") && importTargetDir != null){
						try {
							String last = Objects.requireNonNull(DocumentFile.fromSingleUri(EditorActivity.this, uri)).getName();
							if(last == null) last = "file";
							if(last.contains("/")) last = last.substring(last.lastIndexOf("/")+1);
							java.io.File target = new java.io.File(importTargetDir, last);
							FileUtil.writeFile(target.getAbsolutePath(),"");
							UriUtils.copyUriToUri(EditorActivity.this,uri,Uri.fromFile(target));
						} catch(Exception ex){
							com.star4droid.star2d.ui.Ui.toast(EditorActivity.this,"Import error : "+ex);
						}
    				} else if(filePickerAction.equals("import")){
						if(homeView!=null) homeView.toast("Restoring...");
    				    try {
							restoreProject(getContentResolver().openInputStream(uri));
						} catch(Exception e){}
    				}
				}
				if(filePickerAction.equals("native") && importDone != null){
					importDone.run();
					com.star4droid.star2d.ui.Ui.toast(EditorActivity.this, uriList.size()+" file(s) imported");
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
        installSafeInsets(findViewById(R.id.frame).getParent() instanceof View ? (View) findViewById(R.id.frame).getParent().getParent() : null);
		init();
        
        project = new Project(""/*getIntent().getStringExtra("project")*/);
        
        editor.setProject(project);
		
        //editor.setScene("scene1");
        //editor.loadFromPath();
		//editor.setOrienation(editor.getConfig().getString("or").equals("")?Editor.ORIENATION.PORTRAIT:Editor.ORIENATION.LANDSCAPE);
		
		editor.setEditorReadyAction(()->{
			continueInit();
		});
		editor.setWhenAppReady(()->{
			initApp();
		});
	}
	
	private void restoreProject(InputStream inputStream) {
		 new Thread(){
			 public void run(){
				 try {
				 Utils.unzipf(inputStream,getFilesDir()+"/projects/","");
				 if(homeView!=null){
					 homeView.toast("Project restored.");
					 homeView.refresh();
				 }
				 Gdx.app.postRunnable(()->editor.getApp().getProjectsStage().refresh());
				 } catch(Exception ex){
					 if(homeView!=null) homeView.toast("Restore failed : "+ex);
				 }
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
		intent.setClass(this,com.star4droid.star2d.Activities.CodeEditorActivity.forScene());
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
		TestApp app = editor.getApp();
		app.openDonate = this::openDonate;
		// show / hide the Android home screen together with the LibGDX editor
		app.onProjectOpening = ()->runOnUiThread(()->homeView.setVisibility(View.GONE));
		app.onProjectClosed = ()->runOnUiThread(()->{
			shell.onProjectClosed();
			homeView.setVisibility(View.VISIBLE);
			homeView.refresh();
		});
		app.getProjectsStage().setExportRunnable(()->exportProject(app.getProjectsStage().getSelectedProject().file()));
		app.getProjectsStage().setBackupRunnable(()->backupProject(app.getProjectsStage().getSelectedProject().file()));
		app.getProjectsStage().setImportRunnable(this::importProject);
		appReady = true;
	}

	// ---- ProjectsHomeView.Host ----

	@Override
	public void openProject(java.io.File projectDir) {
		TestApp app = editor == null ? null : editor.getApp();
		if(!appReady || app == null){
			homeView.toast("Engine is still loading, try again in a moment...");
			return;
		}
		homeView.setVisibility(View.GONE);
		String path = projectDir.getAbsolutePath();
		Gdx.app.postRunnable(()->app.openProject(new Project(path)));
	}

	@Override
	public void exportProject(java.io.File projectDir) {
		exported_project = projectDir.getAbsolutePath();
		saveType = "export";
		Utils.saveFile(projectDir.getName()+".apk",saveFile);
	}

	@Override
	public void backupProject(java.io.File projectDir) {
		saveType = "backup";
		String name = projectDir.getName();
		java.io.File backups = new java.io.File(getExternalFilesDir(null),"Star2D/backups");
		backups.mkdirs();
		String exportPath = new java.io.File(backups,name+".zip").getAbsolutePath();
		String path = projectDir.getAbsolutePath();
		homeView.toast("Please wait...");
		Executors.newSingleThreadExecutor().execute(()->{
			try {
				Utils.createEmptyZipFile(exportPath);
				Utils.zipf(path,exportPath,"");
				source = Uri.fromFile(new java.io.File(exportPath));
				Utils.saveFile(name+".zip",saveFile);
			} catch(Exception e){
				homeView.toast("Backup failed : "+e);
			}
		});
	}

	@Override
	public void importProject() {
		filePickerAction = "import";
		files_picker.launch(new String[] {"application/zip"});
	}

	@Override
	public void openDonate() {
		Intent intent = new Intent();
		intent.setClass(getApplicationContext(), com.star4droid.star2d.Activities.DonateActivity.class);
		startActivity(intent);
	}
	
	private void continueInit(){
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
        
    }

    /**
     * The editor root used fitsSystemWindows, which pads by EVERY inset the window reports (keyboard, caption bar, split-screen
     * or vendor-specific bars). Some devices report a huge top inset, which pushed the whole editor down and cut its bottom bars.
     * Use only the status / navigation bars and the cutout, never the keyboard, and never more than 96dp per side.
     */
    private void installSafeInsets(View root) {
        if (root == null) return;
        root.setFitsSystemWindows(false);
        final int cap = (int) (96 * getResources().getDisplayMetrics().density);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l = 0, t = 0, r = 0, b = 0;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                l = in.left; t = in.top; r = in.right; b = in.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = Math.min(insets.getStableInsetBottom(), insets.getSystemWindowInsetBottom());
            }
            v.setPadding(Math.min(l, cap), Math.min(t, cap), Math.min(r, cap), Math.min(b, cap));
            return android.os.Build.VERSION.SDK_INT >= 30 ? android.view.WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    public void init() {
        editor = findViewById(R.id.editor);
        // Native Android editor UI (bars, panels, dialogs) over the LibGDX canvas
        shell = new com.star4droid.star2d.ui.EditorShell(this, new com.star4droid.star2d.ui.EditorShell.Host(){
            @Override public void openJava(String path){ EditorActivity.this.openJava(path); }
            @Override public void importFiles(String targetDir, Runnable done){
                importTargetDir = targetDir;
                importDone = done;
                filePickerAction = "native";
                files_picker.launch(new String[] {"*/*"});
            }
            @Override public void openBlocksAt(String file, int block){
                com.star4droid.star2d.ui.blocks.BlockEditorActivity.open(EditorActivity.this, file, "", block);
            }
            @Override public void openJavaAt(String path, int line, int col, int endCol, String message){
                Intent intent = new Intent();
                intent.putExtra("path", path);
                intent.putExtra("goto_line", line);
                intent.putExtra("goto_col", col);
                intent.putExtra("goto_end", endCol);
                intent.putExtra("goto_msg", message);
                intent.setClass(EditorActivity.this, com.star4droid.star2d.Activities.CodeEditorActivity.forScene());
                startActivity(intent);
            }
            @Override public boolean openBlocks(String file, String hints){
                return com.star4droid.star2d.ui.blocks.BlockEditorActivity.open(EditorActivity.this, file, hints);
            }
        });
        ((ViewGroup) findViewById(R.id.frame)).addView(shell,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Native Android projects screen on top of the LibGDX surface
        homeView = new ProjectsHomeView(this,this);
        ((ViewGroup) findViewById(R.id.frame)).addView(homeView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Occasional (rare, snoozable) support dialog, only while the projects screen is showing
        homeView.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && homeView.isShown())
                com.star4droid.star2d.ui.SupportPrompt.maybeShow(this, this::openDonate);
        }, 3500);
    }
	@Override
	protected void onStart() {
		super.onStart();
		com.star4droid.star2d.remote.RemoteClient.get().onForeground(this);
	}
	@Override
	protected void onStop() {
		com.star4droid.star2d.remote.RemoteClient.get().onBackground(this);
		super.onStop();
	}
	@Override
	protected void onResume() {
		super.onResume();
		if(editor!=null)
			editor.setToCurrentEditor();
	}
	@Override
	public void onBackPressed() {
		if(shell != null && shell.onBack()) return;
		super.onBackPressed();
	}

	@Override
	public void exit() {
	    
	}
	
}