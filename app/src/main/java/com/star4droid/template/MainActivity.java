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
		StageImp dex = StageImp.getFromDex((getIntent().hasExtra("path")&&(!getIntent().getStringExtra("path").equals("")))?getIntent().getStringExtra("path"):(new java.io.File(getFilesDir()+"/game/").listFiles()[0]+""),
		(getIntent().hasExtra("scene")&&(!getIntent().getStringExtra("scene").equals("")))?getIntent().getStringExtra("scene"):"scene1",null,null);
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