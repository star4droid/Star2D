package com.star4droid.template;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
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
		configuration.depth = 24; // 3D scenes: no z-fighting between close surfaces
		final String path = (getIntent().hasExtra("path")&&(!getIntent().getStringExtra("path").equals("")))?getIntent().getStringExtra("path"):firstGameDir();
		final String scene = (getIntent().hasExtra("scene")&&(!getIntent().getStringExtra("scene").equals("")))?getIntent().getStringExtra("scene"):"scene1";
		// The scene makes its SpriteBatch/shaders while it is built, so build it in create() on the GL thread:
		// building it here in onCreate (no OpenGL context yet) failed with "Error compiling shader" and an empty log.
		initialize(new com.badlogic.gdx.ApplicationListener() {
			StageImp game;
			@Override public void create() {
				game = StageImp.getFromDex(path, scene, null, null);
				game.create();
			}
			@Override public void resize(int w, int h) { if (game != null) game.resize(w, h); }
			@Override public void render() { if (game != null) game.render(); }
			@Override public void pause() { if (game != null) game.pause(); }
			@Override public void resume() { if (game != null) game.resume(); }
			@Override public void dispose() { if (game != null) game.dispose(); }
		}, configuration);
	}
	
	private String firstGameDir() {
		java.io.File[] l = new java.io.File(getFilesDir() + "/game/").listFiles();
		if (l == null || l.length == 0) return "";
		java.util.Arrays.sort(l);
		for (java.io.File f : l) if (f.isDirectory()) return f.getPath();
		return l[0].getPath();
	}

	@Override
	protected void onResume(){
		super.onResume();
	}
}