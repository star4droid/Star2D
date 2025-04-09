package com.star4droid.template.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import com.badlogic.gdx.graphics.Texture;

public class SpriteSheetLoader extends Thread {
	HashMap<String,Animation> animations = new HashMap<>();
	String path;
	LoadListener loadListener;
	ProjectAssetLoader assetLoader;
	Project project;
	public SpriteSheetLoader(ProjectAssetLoader assetLoader,Project project,LoadListener loadListener){
		super();
		this.project = project;
		this.path = project.get("anims");
		this.loadListener = loadListener;
		this.assetLoader = assetLoader;
	}
	
	@Override
	public void run(){
		File[] files = new File(path).listFiles();
		if(files==null) {
			if(loadListener!=null)
				loadListener.onLoadComplete(true,"listFiles() return null!!");
			return;
		}
		for(File file:files){
			if(file.isDirectory()) continue;
			String content = Gdx.files.absolute(file.getAbsolutePath()).readString();
			ArrayList<HashMap<String,Object>> animsList = content.equals("")?new ArrayList<>():new Gson().fromJson(content, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
			if(animsList.size()==0) continue;
			float dur=0.3f;//300ms
			try {
				dur = Utils.getFloat(animsList.get(0).get("dur").toString())*0.001f;
			} catch(Exception exception){}
			Drawable[] drawables= new Drawable[animsList.size()];
			int x=0;
			Drawable def = Utils.getDrawable(Gdx.files.internal("images/logo.png"));
			for(HashMap<String,Object> hashMap:animsList){
				String img = project.getImagesPath()+hashMap.get("name").toString().replace(Utils.seperator,"/");
				drawables[x] = assetLoader.contains(img)?Utils.getDrawable(assetLoader.get(img,Texture.class)):def;
				x++;
			}
			Animation<Drawable> animation= new Animation<>(dur,drawables);
			animation.setPlayMode(Animation.PlayMode.LOOP);
			animations.put(file.getName(),animation);
		}
		if(loadListener!=null)
			loadListener.onLoadComplete(false,null);
	}
	
	public Animation getAnimation(String anim){
		Animation an = animations.containsKey(anim)?animations.get(anim):null;
		return an;
	}
	
	public interface LoadListener {
		public void onLoadComplete(boolean error,String message);
	}
}