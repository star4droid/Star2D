package com.star4droid.template.Utils;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffect;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.star4droid.star2d.Helpers.Project;
import java.io.File;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;

public class ProjectAssetLoader extends AssetManager {
	Project project;
	AssetsLoadListener loadListener;
	boolean isListenerCalled = false;
	public ProjectAssetLoader(Project p){
	    super(new AbsoluteFileHandleResolver());
		load(p);
	}
	
	public void load(Project p){
		this.project = p;
		setErrorListener((descriptor,error)->{
			Utils.Log("assets load error : \nfile : "+descriptor.fileName,Utils.getStackTraceString(error));
		});
		load(new File(project.getImagesPath()),Texture.class);
		//load(new File(project.get("files")),null);
		load(new File(project.get("sounds")),Music.class);
	}
	
	public ProjectAssetLoader setAssetsLoadListener(AssetsLoadListener listener){
		isListenerCalled = false;
		loadListener = listener;
		//if(update()) listener.onLoad();
		return this;
	}
	
	public void setProject(Project p){
		project = p;
	}
	
	@Override
	public boolean update() {
		boolean ends = super.update();
		if(ends && loadListener!=null && !isListenerCalled){
			loadListener.onLoad();
			isListenerCalled = true;
		}
		return ends;
	}
	
	@Override
	public void finishLoading() {
		super.finishLoading();
		if(loadListener!=null) loadListener.onLoad();
	}
	
	public void loadFile(String file,Class<?> Type){
		if(Type!=null){
			load(file,Type);
			return;
		}
		if(endsWith(file,".jpg",".png",".jpeg")){
			load(file,Texture.class);
			return;
		}
		if(endsWith(file,".fnt")){
			load(file,BitmapFont.class);
			return;
		}
		if(endsWith(file,".p",".particle")){
			load(file,ParticleEffect.class);
			return;
		}
	}
	
	public static boolean endsWith(String string,String... with){
		for(String s:with)
			if(s.toLowerCase().endsWith(s))
				return true;
		return false;
	}
	
	public void load(File dir,Class<?> Type) {
		if(dir.isFile() && !contains(dir.getAbsolutePath())){
			load(dir.getAbsolutePath(),Type);
			return;
		}
		if (!dir.exists() || dir.isFile()) return;
		
		File[] listFiles = dir.listFiles();
		if (listFiles == null || listFiles.length <= 0) return;
		
		for (File file:listFiles) {
			if(file.isDirectory())
				load(file,Type);
			else loadFile(file.getAbsolutePath(),Type);
		}
	}
	
	public interface AssetsLoadListener {
		public void onLoad();
	}
}