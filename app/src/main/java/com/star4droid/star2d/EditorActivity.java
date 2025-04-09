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

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidFragmentApplication;
import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Activities.AnimationActivity;
import com.star4droid.star2d.Adapters.ExportDialog;
import com.star4droid.star2d.Adapters.SPNote;
import com.star4droid.star2d.CodeEditor.MyIndexer;
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

import com.star4droid.star2d.editor.LibgdxEditor;
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
    					    last = uri.getLastPathSegment();
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
    				} else if(filePickerAction.equals("restore")){
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
        JointsHelper.init(this);
        setContentView(R.layout.editor);
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
	
	private void initApp(){
	    com.star4droid.star2d.Adapters.UpdateChecker.checkForUpdate(this);
		editor.getApp().setOrienationChanger(landscape->{
			boolean isCurrentLandscape = getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
			if(isCurrentLandscape == landscape) return;
			setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			//String ex = android.util.Log.getStackTraceString(new Exception("Landscape : "+landscape+"\n"))+"\n"+"__".repeat(10)+"\n";
			//Gdx.files.external("logs/landscape.txt").writeString(ex,true);
		});
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
		
	}
	
	private void continueInit(){
        indexFiles();
		editor.getApp().getFileBrowser().setAnimationOpen(file->{
			openAnimation(file);
		});
		
		editor.getApp().getFileBrowser().setOpenJavaRunnable(file->{
			openJava(file);
		});
		
		editor.getApp().getFileBrowser().setPickFilesRunnable(()->{
		    this.filePickerAction = "files";
			files_picker.launch(new String[] {"*/*"});
		});
		//SPNote.show(this);
        
    }

    public void init() {
        editor = findViewById(R.id.editor);
    }
    private static int id = 0;
    public void indexFiles() {
        if (!editor.getApp().preferences.getBoolean("Auto Completion", true)) return;
         id++;
        int currentID = id;
        new Thread() {
            public void run() {
                Looper.prepare();
                editor.setIndexer(MyIndexer.isIndexerMatch(editor.getProject().getPath())?MyIndexer.lastIndexer:new MyIndexer().indexFiles(editor));
                if(currentID != id) return;
                if(editor.getApp().getControlLayer()!=null)
                Gdx.app.postRunnable(()->{
                    editor.getApp().toast("Indexing files completed!");
                    try {
                    editor.getApp().getControlLayer().setIndexing(false);
                    } catch(Exception e){}
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