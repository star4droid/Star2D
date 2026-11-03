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
	boolean loaded = false;
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
		File root = new File(path);
		if(root.listFiles()==null) {
			if(loadListener!=null)
				loadListener.onLoadComplete(true,"listFiles() return null!!");
			return;
		}
		loadDir(root, "");
		if(loadListener!=null)
			loadListener.onLoadComplete(false,null);
		loaded = true;
	}
	
	/** Reads every animation file of a folder; the ones in inner folders are named "folder/name" (and also just "name" when that is free). */
	private void loadDir(File dir, String prefix){
		File[] files = dir.listFiles();
		if(files==null) return;
		for(File file:files){
			if(file.isDirectory()) {
				loadDir(file, prefix + file.getName() + "/");
				continue;
			}
			try {
				Animation<Drawable> animation = read(file);
				if(animation==null) continue;
				animations.put(prefix + file.getName(), animation);
				if(!prefix.isEmpty() && !animations.containsKey(file.getName())) animations.put(file.getName(), animation);
			} catch(Exception ignored){}
		}
	}
	
	private Animation<Drawable> read(File file){
		String content = Gdx.files.absolute(file.getAbsolutePath()).readString();
		ArrayList<HashMap<String,Object>> animsList = content.equals("")?new ArrayList<>():new Gson().fromJson(content, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
		if(animsList==null || animsList.size()==0) return null;
		float dur=0.3f;//300ms
		try {
			dur = Utils.getFloat(animsList.get(0).get("dur").toString())*0.001f;
		} catch(Exception exception){}
		Drawable[] drawables= new Drawable[animsList.size()];
		int x=0;
		// made on the render thread: this loader runs on its own thread, where no texture can be created
		Drawable def = new LazyDrawable(new LazyDrawable.Maker(){ public Drawable make(){ return Utils.getDrawable(Gdx.files.internal("images/logo.png")); } }, 64, 64);
		for(HashMap<String,Object> hashMap:animsList){
			String img = project.getImagesPath()+hashMap.get("name").toString().replace(Utils.seperator,"/");
			Drawable d = def;
			Object reg0 = hashMap.get("region");
			int[] part = null;
			if(reg0 instanceof java.util.List && ((java.util.List<?>)reg0).size()==4){
				java.util.List<?> r = (java.util.List<?>)reg0;
				part = new int[]{((Number)r.get(0)).intValue(),((Number)r.get(1)).intValue(),((Number)r.get(2)).intValue(),((Number)r.get(3)).intValue()};
			}
			Drawable merged = AtlasIndex.isEmpty() ? null : AtlasIndex.drawable(assetLoader,project,img,part);
			if(merged!=null) d = merged;
			else if(assetLoader.contains(img)){
				Texture tex = assetLoader.get(img,Texture.class);
				Object region = hashMap.get("region"); // [x, y, w, h] of the frame inside a sprite sheet
				if(region instanceof java.util.List && ((java.util.List<?>)region).size()==4){
					java.util.List<?> r = (java.util.List<?>)region;
					d = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(tex,
						((Number)r.get(0)).intValue(),((Number)r.get(1)).intValue(),((Number)r.get(2)).intValue(),((Number)r.get(3)).intValue()));
				} else d = Utils.getDrawable(tex);
			}
			drawables[x] = d;
			x++;
		}
		Animation<Drawable> animation= new Animation<>(dur,drawables);
		animation.setPlayMode(Animation.PlayMode.LOOP);
		return animation;
	}
	@Override
	public void start() {
		if(!loaded)
			super.start();
		else loadListener.onLoadComplete(false,null);
	}
	public Animation getAnimation(String anim){
		Animation an = animations.containsKey(anim)?animations.get(anim):null;
		return an;
	}
	
	public interface LoadListener {
		public void onLoadComplete(boolean error,String message);
	}
}