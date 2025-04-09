package com.star4droid.star2d.Loaders;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SpriteSheetsLoader extends Thread implements EngineLoader{
	Project project;
	Context context;
	boolean DEBUG=false;
	HashMap<String,AnimationDrawable> spritesheets= new HashMap<>();
	RequestOptions requestOptions = new RequestOptions()
	.format(DecodeFormat.PREFER_ARGB_8888)
	.diskCacheStrategy(DiskCacheStrategy.ALL);
	
	public SpriteSheetsLoader(Context ctx,Project p){
		super();
		project = p;
		context = ctx;
	}
	
	@Override
	public void run(){
		Looper.prepare();
		//animations hashMap (name,animation)
		ArrayList<String> files = new ArrayList<>();
		//get animations list
		FileUtil.listDir(project.get("anims"),files);
		if(DEBUG) Log.e("star2dXXX","animations : "+files.size());
		if(files.size()==0) {
			onLoad(spritesheets);
		}
		for(String str:files){
			spritesheets.put(Uri.parse(str).getLastPathSegment(),null);
		}
		for(String str:files){
			//loop through animations...
			String name = Uri.parse(str).getLastPathSegment();
			final AnimationDrawable drawable = new AnimationDrawable();
			ArrayList<HashMap<String,Object>> animsList = (FileUtil.readFile(str)).equals("")?new ArrayList<>():new Gson().fromJson(FileUtil.readFile(str), new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
			if(DEBUG) Log.e("star2dXXX","animation : "+name+", size : "+animsList.size());
			if(animsList.size()==0) {
				drawable.addFrame(context.getDrawable(R.drawable.icon),300);
				spritesheets.put(name,drawable);
				continue;
			}
			Drawable[] list = new Drawable[animsList.size()];
			//loop through animation pictures...
			int x=0;
			for(HashMap<String,Object> hash:animsList){
				String picture=hash.get("name").toString();
				int duration=300;
				try {
					duration = Utils.getInt(hash.get("dur").toString());
				} catch (Exception ex){}
				//picture name,list of pictures,position to set,drawable,duration
				if(DEBUG) Log.e("star2dXXX","name : "+picture+", pos : "+x+", dur : "+duration);
				load(picture,list,x,drawable,duration,name);
				x++;
			}
		}
		
	}
	@Override
	public void onLoad(Object... params) {
		
	}
	
	public boolean checkDone(HashMap<String,AnimationDrawable> hash){
		for(Map.Entry<String,AnimationDrawable> map:hash.entrySet()){
			if(hash.get(map.getKey())==null) return false;
			if(DEBUG) Log.e("star2dXXX","key : "+map.getKey()+" √");
		}
		onLoad(spritesheets);
		return true;
	}
	
	public void load(String img,final Drawable[] list,final int pos,AnimationDrawable animationDrawable,int duration,final String name){
		Glide.with(context)
		.load(Uri.fromFile(new File(project.getImagesPath()+img.replace(Utils.seperator,"/"))))
		.apply(requestOptions)
		.error(context.getDrawable(R.drawable.icon))
		.into(new CustomTarget<Drawable>() {
			@Override
			public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
				list[pos] = resource;
				checkDone(list,animationDrawable,duration,name);
			}
			
			@Override
			public void onLoadFailed(Drawable errorDrawable) {
				// Handle failed image loading
				list[pos] = errorDrawable;
				checkDone(list,animationDrawable,duration,name);
			}

			@Override
			public void onLoadCleared(Drawable arg0) {
				
			}

			
		});
	}
	
	public boolean checkDone(Drawable[] animations,AnimationDrawable animationDrawable,int dur,String name){
		for(Drawable drawable:animations){
			if(drawable==null) return false;
		}
		
		for(Drawable drawable:animations){
			animationDrawable.addFrame(drawable,dur);
		}
		if(DEBUG) Log.e("star2dXXX","add name : "+name);
		spritesheets.put(name,animationDrawable);
		checkDone(spritesheets);
		return true;
	}
}