package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.star4droid.star2d.Helpers.rtl.RtlController;
import com.star4droid.star2d.Helpers.rtl.RtlFreeTypeFontGenerator;
import com.star4droid.template.Utils.Utils;

public class ThemeLoader {
	public static void loadTheme(){
		if(!VisUI.isLoaded())
			VisUI.load(VisUI.SkinScale.X2);
		//load all images from assets...
		loadTheme(Gdx.files.internal("images"),"");
		/*
		RtlFreeTypeFontGenerator generator = new RtlFreeTypeFontGenerator(Utils.internal("files/VisOpenSans.ttf"));
		RtlFreeTypeFontGenerator.FreeTypeFontParameter parameter = new RtlFreeTypeFontGenerator.FreeTypeFontParameter();
		parameter.size = 18;
		parameter.characters += RtlController.getInstance().getAllRtlChars();
		BitmapFont font = generator.generateRtlFont(parameter);
		VisUI.getSkin().add("default-font",font,BitmapFont.class);
		*/
		
		/*
		FileHandle orange = Gdx.files.internal("files/skins/orange");
		
		// if there's Vis Resource with the same name then replace it
		log("replace","",false);
		log("remove","",false)
		for(FileHandle fh:orange.list()){
			String nameWithoutExt = fh.nameWithoutExtension().replace(".9","");
			if(fh.name().endsWith(".png") || fh.name().endsWith(".jpg"))
				if(VisUI.getSkin().has(nameWithoutExt,Drawable.class) || VisUI.getSkin().has(nameWithoutExt,NinePatch.class)){
					boolean nine = fh.name().endsWith(".9.png");
					remove(nameWithoutExt);
					VisUI.getSkin().add(nameWithoutExt,nine ? ninePatch(fh):drawable(fh),nine ? NinePatch.class : Drawable.class);
					log("replace","replace : "+fh.name()+",name : "+nameWithoutExt,true);
				} else log("replace","keep : "+fh.name(),true);
		}
		
		// replace Vis Resources by new resources based on json file
		// check-on:orange-check-on
		// this means: replace check-on from VisUI by orange-check-on from the folder
		String json = orange.sibling("orange.json").readString();
		log("json","",false);
		for(String line:json.split("\n")){
			try {
				if(line.startsWith("#") || !line.contains(":")) continue;
				String name = line.split(":")[0].trim().replace(" ","");
				if(name.equals("name")) continue;
				String value = line.split(":")[1].trim().replace(" ","");
				int start = value.contains("/") ? value.lastIndexOf("/") : 0;
				String valuePure = start == 0 ? value.substring(0,value.indexOf(".")):value.substring(start+1,value.indexOf(".",start));
				
				boolean drawable = VisUI.getSkin().has(valuePure,Drawable.class);
				boolean patch = VisUI.getSkin().has(valuePure,NinePatch.class);
				if(drawable || patch){
					remove(name);
					VisUI.getSkin().add(name,drawable ? VisUI.getSkin().getDrawable(valuePure) : (patch ? VisUI.getSkin().getPatch(valuePure) : null),drawable ? Drawable.class : NinePatch.class);
					log("json","add : "+name+", assets value : "+value,true);
				} else {
					FileHandle child = orange.child(value);
					boolean isNinePatch =  value.endsWith(".9.png");
					if(child.exists()){
						Object object = isNinePatch ? ninePatch(child) : drawable(child);
						remove(name);
						VisUI.getSkin().add(valuePure,object,isNinePatch ? NinePatch.class : Drawable.class);
						VisUI.getSkin().add(name,object, isNinePatch ? NinePatch.class : Drawable.class);
						log("json","add from assets : "+name+", asset : "+value,true);
					} else log("json","not found : "+name+", value : "+value,true);
				}
			} catch(Exception e){
				log("json","error : "+e.toString()+"\nline : "+line,true);
			}
		}
		// reload skin to apply changes.. 
		//VisUI.getSkin().load(VisUI.SkinScale.X2.getSkinFile());
		*/
	}
	
	public static void remove(String name){
		boolean remove = false;
		if(VisUI.getSkin().has(name,Drawable.class)){
			VisUI.getSkin().remove(name,Drawable.class);
			log("remove","removed drawble : "+name,true);
			remove = true;
		}
		if(VisUI.getSkin().has(name,NinePatch.class)){
			VisUI.getSkin().remove(name,NinePatch.class);
			log("remove","removed patch : "+name,true);
			remove = true;
		}
		if(!remove)
			log("remove","not found : "+name,true);
	}
	
	private static void loadTheme(FileHandle fileHandle,String dir){
		for(FileHandle fh:fileHandle.list()){
			if(fh.isDirectory()){
				loadTheme(fh,dir+fh.name()+"/");
				continue;
			}
			if(!fh.name().contains(".")){
				Gdx.files.external("logs/drawable.txt").writeString("not contains \".\" name : "+fh.name()+"\n"+"__".repeat(10)+"\n",true);
				continue;
			}
			String name = fh.name().substring(0,fh.name().indexOf("."));
			String nameL = fh.name().toLowerCase();
			if(nameL.endsWith(".png") || nameL.endsWith(".jpg")){
				boolean nine = nameL.endsWith(".9.png");
				//if(!VisUI.getSkin().has(name,nine?NinePatch.class:Drawable.class)){
					VisUI.getSkin().add(name,nine?ninePatch(fh):drawable(fh),nine?NinePatch.class:Drawable.class);
					//Gdx.files.external("logs/drawable.txt").writeString("add name : "+name+"\n"+"__".repeat(10)+"\n",true);
				//}
			}
		}
	}
	
	public static void log(String tag,String log,boolean append){
		Gdx.files.external("logs/"+tag+".txt").writeString(log+"\n",append);
	}
	
	private static NinePatch ninePatch(FileHandle fileHandle){
		return new NinePatch(new Texture(fileHandle));
	}
	
	private static Drawable drawable(FileHandle fileHandle){
		return Utils.getDrawable(fileHandle);
	}
}