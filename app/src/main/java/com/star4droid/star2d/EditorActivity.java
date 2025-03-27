package com.star4droid.star2d;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidFragmentApplication;
import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Activities.AnimationActivity;
import com.star4droid.star2d.Activities.FilesManagerActivity;
import com.star4droid.star2d.Adapters.AddPopup;
import com.star4droid.star2d.Adapters.AddLightPopup;
import com.star4droid.star2d.Adapters.ColourSelector;
import com.star4droid.star2d.Adapters.ExportDialog;
import com.star4droid.star2d.Adapters.EditorField;
import com.star4droid.star2d.Adapters.MissingFileDialog;
import com.star4droid.star2d.Adapters.Properties;
import com.star4droid.star2d.Adapters.SPNote;
import com.star4droid.star2d.CodeEditor.MyIndexer;
import com.star4droid.star2d.Fragments.*;
import com.star4droid.star2d.Fragments.BodiesFragment;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.CompileThread;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.JointsHelper;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.SwipeHelper;
import com.star4droid.star2d.Helpers.UriUtils;
import com.star4droid.star2d.Items.*;
import com.star4droid.star2d.Items.Editor;

import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.evo.R;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

public class EditorActivity extends AppCompatActivity implements AndroidFragmentApplication.Callbacks {
    public String ADD_SCENE = "Add Scene";
    ImageView play, playFloat, grid, move, rotate, scale, rotateScreen, openFiles, center_camera,
            addBody, deleteBody, save, deleteScene, undo, redo, sceneColor, lock, copyScene, renameScene;
    Spinner scenesSpinner, bodiesSpinner;
    Editor editor;
	ActivityResultLauncher<String[]> files_picker;
	ActivityResultLauncher saveFile;
    LinearLayout sceneLinear, propsLinear, right_linear;
    Project project;
    Properties properties;
    ArrayList<String> scenesList = new ArrayList<>();
    ViewPager2 right_viewPager;
	Uri source;
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_",
        filePickerAction = "",exported_project="",saveType="";
    ArrayList<String> bodiesList;
    BodiesFragment bodiesFragment;
	android.app.AlertDialog playerDialog;

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
		files_picker =
        registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uriList -> {
				String path = editor.getApp().getFileBrowser() != null ? editor.getApp().getFileBrowser().getCurrentDir().file().getAbsolutePath() : "";
				for(Uri uri:uriList){
				    if(filePickerAction.equals("files")){
    					String last = Uri.fromFile(new java.io.File(FileUtil.convertUriToFilePath(EditorActivity.this,uri))).getLastPathSegment();
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
        		// Handle the error. For example, log it to HockeyApp:
				FileUtil.writeFile(getExternalFilesDir("logs")+"/error.txt",Utils.getStackTraceString(error));
        		//ExceptionHandler.saveException(error, new CrashManager());
    		}
		}).start();
		*/
        EngineSettings.init(this);
        com.star4droid.star2d.Adapters.UpdateChecker.checkForUpdate(this);
        JointsHelper.init(this);
        Utils.setLanguage(this);
        setContentView(R.layout.editor);
		init();
        // Hide system UI
		//Utils.hideSystemUi(getWindow());
        HashMap<String, Object> map;

        try {
            EditorField.spinnerMap = new Gson().fromJson(Utils.readAssetFile("spinners.json", this), new TypeToken<HashMap<String, Object>>() {
            }.getType());
        } catch (Exception ex) {
            Utils.showMessage(this, "spinner map init error : " + ex);
            return;
        }
        
        project = new Project(""/*getIntent().getStringExtra("project")*/);
        
        editor.setProject(project);
		
        //editor.setScene("scene1");
        //editor.loadFromPath();
		//editor.setOrienation(editor.getConfig().getString("or").equals("")?Editor.ORIENATION.PORTRAIT:Editor.ORIENATION.LANDSCAPE);
		
		editor.setEdtitorReadyAction(()->{
			new Handler(Looper.getMainLooper()).post(()->continueInit());
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
	
	private void initApp(){
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
		
		editor.getApp().getFileBrowser().setPickFilesRunnable(()->{
		    this.filePickerAction = "files";
			files_picker.launch(new String[] {"*/*"});
		});
		
		//SPNote.show(this);
		
        bodiesFragment = new BodiesFragment(editor);
        properties = new Properties(this);
        //propsLinear.addView(properties.getView());
		
        properties.getViewPager().setAdapter(new FragmentAdapter(this, editor));
        right_viewPager.setAdapter(new RightFragmentAdapter(this, editor));
        //disable viewpager touch because we don't need it currently...
        right_viewPager.requestDisallowInterceptTouchEvent(true);
        
    }

    public void init() {
        playFloat = findViewById(R.id.play_float);
        addBody = findViewById(R.id.addBody);
        play = findViewById(R.id.play);
        grid = findViewById(R.id.grid);
        move = findViewById(R.id.move);
        scale = findViewById(R.id.scale);
        rotate = findViewById(R.id.rotate);
        bodiesSpinner = findViewById(R.id.bodiesSpinner);
        editor = findViewById(R.id.editor);
        scenesSpinner = findViewById(R.id.scenesSpinner);
        deleteBody = findViewById(R.id.deleteBody);
        save = findViewById(R.id.save);
        deleteScene = findViewById(R.id.deleteScene);
        sceneLinear = findViewById(R.id.sceneLinear);
        propsLinear = findViewById(R.id.propsLinear);
        undo = findViewById(R.id.undo);
        redo = findViewById(R.id.redo);
        rotateScreen = findViewById(R.id.rotateScreen);
        sceneColor = findViewById(R.id.sceneColor);
        openFiles = findViewById(R.id.files_manager);
        lock = findViewById(R.id.lock);
        center_camera = findViewById(R.id.center_camera);
        renameScene = findViewById(R.id.renameScene);
        copyScene = findViewById(R.id.copyScene);
        right_viewPager = findViewById(R.id.right_vp);
        right_linear = findViewById(R.id.right_linear);
    }

    public void indexFiles() {
        if (!EngineSettings.get().getBoolean("AutoComp", false)) return;
        findViewById(R.id.progress).setVisibility(View.VISIBLE);
        new Thread() {
            public void run() {
                Looper.prepare();
                editor.setIndexer(new MyIndexer().indexFiles(editor));
                new Handler(Looper.getMainLooper()).post(() -> {
                    findViewById(R.id.progress).setVisibility(View.GONE);
                });
            }
        }.start();
    }

    private class RightFragmentAdapter extends FragmentStateAdapter {
        Editor editor;

        public RightFragmentAdapter(AppCompatActivity activity, Editor ed) {
            super(activity);
            editor = ed;
        }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return bodiesFragment;

            }
            return null;
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }

    private class FragmentAdapter extends FragmentStateAdapter {
        Editor editor;

        public FragmentAdapter(AppCompatActivity activity, Editor ed) {
            super(activity);
            editor = ed;
        }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new SectionsListFragment(properties.getViewPager());
                case 1:
                    return new PropertiesFragment(editor);
                case 2:
                    return new EventsFragment(editor);
                case 3:
                    return new JointsFragment(editor);
            }
            return null;
        }

        @Override
        public int getItemCount() {
            return 4;
        }

    }
    
    private void switchFor(boolean enable,View... views){
        for(View v:views)
            v.setEnabled(enable);
    }
    
	@Override
	protected void onResume() {
		super.onResume();
		if(editor!=null)
			editor.setToCurrentEditor();
	}
	@Override
	public void exit() {
	    try {
    	    if(editor.getLink()!=null&&editor.getLink().getStage()!=null)
    	        editor.getLink().getStage().pause();
    		editor.linkTo(null);
    		editor.updateProperties();
    		if(playerDialog!=null){
    			if(playerDialog.isShowing())
    				playerDialog.dismiss();
    			playerDialog = null;
    			scenesSpinner.setEnabled(true);
    			playFloat.setVisibility(View.VISIBLE);
    			play.setImageResource(R.drawable.play_icon);
    			//play.setVisibilty(View.VISIBLE);
		    }
		} catch(Exception ex){}
		switchFor(true,scenesSpinner,copyScene,renameScene,addBody,deleteBody,findViewById(R.id.add_light));
	}
	
}