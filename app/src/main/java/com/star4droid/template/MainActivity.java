package com.star4droid.template;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import android.widget.TextView;
import com.star4droid.template.Items.StageImp;

public class MainActivity extends AndroidApplication {
	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		if (Build.VERSION.SDK_INT >= 23) {
			if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
			||checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
				requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
			}
		}
		AndroidApplicationConfiguration configuration= new AndroidApplicationConfiguration();
		String path = (getIntent().hasExtra("path")&&(!getIntent().getStringExtra("path").equals("")))?getIntent().getStringExtra("path"):(new java.io.File(getFilesDir()+"/game/").listFiles()[0]+"");
        String scene = (getIntent().hasExtra("scene")&&(!getIntent().getStringExtra("scene").equals("")))?getIntent().getStringExtra("scene"):"scene1";
        
        StageImp.StageLoaderParameters params = new StageImp.StageLoaderParameters();
        // Initialize the loader with necessary context/paths
        // Note: Project, AssetLoader etc need to be created here or inside the loader
        com.star4droid.star2d.Helpers.Project project = new com.star4droid.star2d.Helpers.Project(path);
        // Assuming implementation handles internal creation if null passed, or we create here
        params.loader = new AndroidSceneLoader(getFilesDir(), getCodeCacheDir(), project, null, null, null); 
        StageImp.mainLoader = params.loader; 
        
		StageImp dex = StageImp.loadScene(scene, null, null, params);
		if(dex != null)
		    initialize(dex,configuration);
		else {
		    TextView text = new TextView(this);
		    text.setText("Error....!!!");
		    setContentView(text);
			text.setTextIsSelectable(true);
		}
	}
	
	@Override
	protected void onResume(){
		super.onResume();
	}
}