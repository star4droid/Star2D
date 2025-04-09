package com.star4droid.star2d.Helpers;

import android.os.Handler;
import android.os.Looper;

public class EngineTask extends Thread {
	Task task;
	public EngineTask(Task tsk){
		task = tsk;
	}
	
	public void updateProgress(int progress){
		if(task!=null) task.onProgress(progress);
	}
	
	public void sendUpdate(String... s){
		if(task!=null) task.onPublish(s);
	}
	
	@Override
	public void run(){
		if(task==null) return;
		new Handler(Looper.getMainLooper()).post(new Runnable(){
			@Override public void run(){
				task.onPreExcute();
			}
		});
		task.doInBackground();
		new Handler(Looper.getMainLooper()).post(new Runnable(){
			@Override public void run(){
				task.onExcute();
			}
		});
	}
	
	public interface Task {
	public void doInBackground();
	public void onProgress(int progress);
	public void onExcute();
	public void onPreExcute();
	public void onPublish(String... s);
	}
}