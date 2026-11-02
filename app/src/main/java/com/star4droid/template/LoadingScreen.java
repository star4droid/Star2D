package com.star4droid.template;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.star4droid.template.Utils.Utils;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import net.lingala.zip4j.ZipFile;
import com.star4droid.star2d.evo.R;

public class LoadingScreen extends Activity {
	volatile boolean timerEnded = false,extracted=false,crashDone=false;
	String path="",scene="scene1";
	SharedPreferences sh;
	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.loading_screen);
		if(getIntent().hasExtra("path")&&!getIntent().getStringExtra("path").equals("")){
			extracted = true;
			path = getIntent().getStringExtra("path");
			scene = getIntent().getStringExtra("scene");
			if(scene==null||scene.equals("")) scene = "scene1";
		}
		sh = getSharedPreferences("game",MODE_PRIVATE);
		if(!extracted) CrashLog.install(this);
		final String assetVersion = readAssetFile("version",this);
		final File gameDir = new File(getFilesDir(),"game");
		String[] existing = gameDir.list();
		if((!extracted)&&sh.getString("version","").equals(assetVersion)&&existing!=null&&existing.length>0){
			extracted = true; // same build as the extracted one
		} else if(!extracted)
		new Thread(){
			public void run(){
				try {
					// a new build: everything of the old one goes (images, atlas pages, scenes, scripts of another project with the same package)
					File tmp = new File(getFilesDir(),"game.tmp");
					deleteRec(tmp);
					File zip = new File(getFilesDir(),"temp.file.zip");
					zip.delete();
					extractAssetFile(LoadingScreen.this,"project.zip",zip.getPath());
					ZipFile zipFile = new ZipFile(zip.getPath());
					zipFile.setPassword("tulsgskdiensl626__Xxmoishs".toCharArray());
					zipFile.extractAll(tmp.getPath());
					zip.delete();
					deleteRec(gameDir);
					if(!tmp.renameTo(gameDir)) throw new java.io.IOException("Could not move the extracted game into place");
					sh.edit().putString("version",assetVersion).commit();
					extracted = true;
					openApp();
				} catch(final Exception e){
					sh.edit().remove("version").commit();
					runOnUiThread(new Runnable(){ public void run(){
						((TextView)findViewById(R.id.text)).setText("error :\n"+Utils.getStackTraceString(e));
					}});
				}
			}
		}.start();
		if(getIntent().hasExtra("path")&&!getIntent().getStringExtra("path").equals("")) crashDone = true; // test run from the editor
		else CrashLog.showPrevious(this,new Runnable(){ public void run(){ crashDone = true; openApp(); }});
		new Timer().schedule(new TimerTask(){
			@Override
			public void run() {
				timerEnded = true;
				openApp();
			}
		},1500);
	}
	
	static void deleteRec(File f){
		if(f==null||!f.exists()) return;
		File[] l = f.listFiles();
		if(l!=null) for(File c : l) deleteRec(c);
		f.delete();
	}

	private boolean opened;
	private synchronized void openApp(){
		if(opened) return;
		if(extracted&&timerEnded&&crashDone){
			Intent intent = new Intent();
			intent.putExtra("path",path);
			intent.putExtra("scene",scene);
			intent.setClass(this,MainActivity.class);
			opened = true;
			startActivity(intent);
			finish();
		}
	}
	
	public static void extractAssetFile(Context context,String file,String to) throws Exception {
			if(!new File(to).exists()) new File(to).createNewFile();
			int count;
			java.io.InputStream input= context.getAssets().open(file);
			java.io.OutputStream output = new  java.io.FileOutputStream(to);
			byte data[] = new byte[1024];
			while ((count = input.read(data))>0) {
				output.write(data, 0, count);
			}
			output.flush();
			output.close();
			input.close();
	}
	
	public static String readAssetFile(String file,Context ctx){
			try{
				java.io.InputStream In = ctx.getAssets().open(file);
				int i = In.available();
				byte[] Bu = new byte[i];
				In.read(Bu);
				In.close();
				String s = new String(Bu, "UTF-8");
				return s;
				} catch(Exception e){
				Log.e("star2d",Log.getStackTraceString(e));
				return "";
			}
		}
}