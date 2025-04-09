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
	boolean timerEnded = false,extracted=false;
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
		if((!extracted)&&sh.getString("version","").equals(readAssetFile("version",this))){
			extracted = true;
		} else if(!extracted)
		new Thread(){
			public void run(){
				try {
					extractAssetFile(LoadingScreen.this,"project.zip",getFilesDir()+"/temp.file.zip");
					ZipFile zipFile = new ZipFile(getFilesDir()+"/temp.file.zip");
					zipFile.setPassword("tulsgskdiensl626__Xxmoishs".toCharArray());
					zipFile.extractAll(getFilesDir()+"/game/");
					extracted = true;
					openApp();
				} catch(Exception e){
					((TextView)findViewById(R.id.text)).setText("error :\n"+Utils.getStackTraceString(e));
				}
			}
		}.start();
		new Timer().schedule(new TimerTask(){
			@Override
			public void run() {
				timerEnded = true;
				openApp();
			}
		},1500);
	}
	
	private void openApp(){
		if(extracted&&timerEnded){
			Intent intent = new Intent();
			intent.putExtra("path",path);
			intent.putExtra("scene",scene);
			intent.setClass(this,MainActivity.class);
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