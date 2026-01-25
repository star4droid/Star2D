package com.star4droid.template.Utils;

import com.star4droid.template.Items.StageImp;
import java.util.Random;

public abstract class SceneScript {
	public StageImp stage;
	private SceneScript linkedScript;
	public final SceneScript setStage(StageImp stageImp){
		this.stage = stageImp;
		return this;
	}
	public final StageImp getStage(){
		return stage;
	}
	public abstract void create();
	public abstract void draw();
	public abstract void pause();
	public abstract void resume();
	
	public final void onCreate(){
		if(linkedScript!=null)
			linkedScript.create();
		create();
	}
	
	public final void onDraw(){
		if(linkedScript!=null)
			linkedScript.onDraw();
		draw();
	}
	
	public final void onPause(){
		if(linkedScript!=null)
			linkedScript.onPause();
		pause();
	}
	
	public final void onResume(){
		if(linkedScript!=null)
			linkedScript.onResume();
		resume();
	}
	
	public SceneScript linkTo(SceneScript sceneScript){
		if(sceneScript.ID == this.ID)
			new RuntimeException("Can\'t link script to itself!");
		else linkedScript = sceneScript;
		if(linkedScript!=null)
			linkedScript.setStage(stage).onCreate();
		return this;
	}
	
	public SceneScript linkTo(Class<? extends SceneScript> cls){
		try {
			SceneScript script = (SceneScript)cls.getConstructors()[0].newInstance();
			linkTo(script);
		} catch(Exception ex){
			
		}
		return this;
	}
	
	public int ID = new Random().nextInt(Integer.MAX_VALUE);
}