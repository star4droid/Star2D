package com.star4droid.star2d.Helpers.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import java.util.ArrayList;

public class Project {
	private String path;
	public Project(String p){
		path = p;
	}
	public String getPath(){
		return path;
	}
	
	public String get(String name){
		return path+"/"+name+"/";
	}
	
	public String getBodiesScripts(String scene){
	    return path+"/java/com/star4droid/Game/Scripts/"+scene+"/";
	}
	
	public String getVariables(String scene){
	    return path+"/Events/"+scene+"/public/variables.json";
	}
	
	public String getBodyScriptPath(String body,String scene){
		return getBodiesScripts(scene)+body+"Script.java";
	}
	
	public String readEvent(String scene,String event){
	    String pth = getEventPath(scene,"",event);
		String result = "";
		FileHandle fileHandle = Gdx.files.absolute(pth+".java");
		try {
		    if(fileHandle.exists())
				result = fileHandle.readString();
		} catch(Exception e){}
		/*
		FileUtil.writeFile(get("log")+scene+"/"+event+".txt", 
	    pth+".java\n"+
	    result);
	    if(result.equals(""))
	        FileUtil.writeFile(pth,"//test");
	   */
		return result;
	}
	
	public String readEvent(String scene,String event,String body){
	    String scrp = getEventPath(scene,body,event);
		String result= "";
		FileHandle fileHandle = Gdx.files.absolute(scrp+".java");
		FileHandle fileHandle2 = Gdx.files.absolute(scrp+".code");
		try {
		    String res1 = fileHandle.exists() ? (fileHandle.readString()+"\n"): "";
		    String res2 = fileHandle2.exists() ? fileHandle2.readString() : "";
			result = res1+res2;
		} catch(Exception e){}
		//Log.e("eeeee","empty "+getEventPath(event,body));
	    /*
	    FileUtil.writeFile(get("log")+event+"/"+body+".txt", 
	    scrp+".java\n"+
	    result);
	    */
		return result;
	}
	
	public String getCodesPath(String scene){
		return path+"/java/com/star4droid/Game/"+scene.toLowerCase()+".java";
	}
	
	/**
	 * Writes the generated java of a scene. Creates the parent folders and replaces a stray directory left at the path
	 * (old copy bug); never throws, so switching scenes cannot crash on a bad path.
	 */
	public boolean writeCode(String scene,String code){
		try {
			FileHandle f = Gdx.files.absolute(getCodesPath(scene));
			if(f.isDirectory()) f.deleteDirectory();
			f.parent().mkdirs();
			f.writeString(code,false);
			return true;
		} catch(Throwable t){
			try { Gdx.files.absolute(path+"/logs/codewrite.error.txt").writeString(scene+": "+t+"\n",true); } catch(Throwable ignored){}
			return false;
		}
	}
	
	public void renameScene(String scene,String newScene){
		ArrayList<String> arrayList = getSceneList(newScene);
		int x=0;
		for(String file:getSceneList(scene)){
			FileHandle fileHandle = Gdx.files.absolute(file);
			if(fileHandle.exists())
				fileHandle.moveTo(Gdx.files.absolute(arrayList.get(x)));
			x++;
		}
		// scripts that extend the generated Refs base of the old scene name must follow the rename
		try {
			String from = scene.toLowerCase(), to = newScene.toLowerCase();
			ArrayList<FileHandle> scripts = new ArrayList<>();
			FileHandle[] bodies = Gdx.files.absolute(getBodiesScripts(newScene)).list();
			if(bodies != null) for(FileHandle f : bodies) scripts.add(f);
			scripts.add(Gdx.files.absolute(getSceneScript(newScene)));
			for(FileHandle f : scripts){
				if(f.isDirectory() || !f.exists() || !f.name().endsWith(".java")) continue;
				String text = f.readString();
				String fixed = text.replace("Refs."+from+"ItemRefs","Refs."+to+"ItemRefs").replace("Refs."+from+"SceneRefs","Refs."+to+"SceneRefs");
				if(!fixed.equals(text)) f.writeString(fixed,false);
			}
		} catch(Throwable ignored){}
	}
	
	public String getScriptsPath(String scene){
		return path+"/scripts/"+scene+"/";
	}
	
	public String getSceneScript(String scene){
		return path+"/java/com/star4droid/Game/SceneScript/"+scene.toLowerCase()+"Script.java";
	}
	
	/**
	 * Copies every per-scene file of {@code scene} to {@code newScene} (scene file, config, events, joints, generated java,
	 * scripts, scene script, body scripts, variables) and renames the java identifiers that carry the scene name.
	 * Index-aligned (a missing source no longer shifts the destinations), directories are copied by content, and on any
	 * failure every file of the new scene is removed again so a half-copied scene is never left behind.
	 */
	public void copyScene(String scene,String newScene){
		ArrayList<String> from = getSceneList(scene), to = getSceneList(newScene);
		try {
			for(int i=0;i<from.size();i++){
				FileHandle src = Gdx.files.absolute(from.get(i)), dst = Gdx.files.absolute(to.get(i));
				if(!src.exists()) continue;
				if(src.isDirectory()) copyDirContent(src,dst);
				else {
					if(dst.isDirectory()) dst.deleteDirectory();
					dst.parent().mkdirs();
					src.copyTo(dst);
				}
			}
			String low = scene.toLowerCase(), nlow = newScene.toLowerCase();
			ArrayList<FileHandle> javas = new ArrayList<>();
			collectJava(Gdx.files.absolute(getBodiesScripts(newScene)),javas);
			collectJava(Gdx.files.absolute(getSceneScript(newScene)),javas);
			collectJava(Gdx.files.absolute(getCodesPath(newScene)),javas);
			for(FileHandle f : javas){
				String text = f.readString();
				String fixed = text
					.replace("com.star4droid.Game.Scripts."+scene+";","com.star4droid.Game.Scripts."+newScene+";")
					.replace("com.star4droid.Game.Scripts."+scene+".","com.star4droid.Game.Scripts."+newScene+".")
					.replace("Refs."+low+"ItemRefs","Refs."+nlow+"ItemRefs")
					.replace("Refs."+low+"SceneRefs","Refs."+nlow+"SceneRefs")
					.replace("SceneScript."+low+"Script","SceneScript."+nlow+"Script")
					.replace("class "+low+"Script","class "+nlow+"Script")
					.replace("class "+scene+" extends StageImp","class "+nlow+" extends StageImp")
					.replace("class "+low+" extends StageImp","class "+nlow+" extends StageImp");
				if(!fixed.equals(text)) f.writeString(fixed,false);
			}
		} catch(RuntimeException e){
			try { deleteScene(newScene); } catch(Throwable ignored){}
			throw e;
		}
	}

	private static void copyDirContent(FileHandle src, FileHandle dst){
		if(dst.exists() && !dst.isDirectory()) dst.delete();
		dst.mkdirs();
		FileHandle[] kids = src.list();
		if(kids == null) return;
		for(FileHandle k : kids){
			FileHandle d = dst.child(k.name());
			if(k.isDirectory()) copyDirContent(k,d);
			else {
				if(d.isDirectory()) d.deleteDirectory();
				k.copyTo(d);
			}
		}
	}

	private static void collectJava(FileHandle f, ArrayList<FileHandle> out){
		if(!f.exists()) return;
		if(f.isDirectory()){
			FileHandle[] kids = f.list();
			if(kids != null) for(FileHandle k : kids) collectJava(k,out);
		} else if(f.name().endsWith(".java")) out.add(f);
	}
	
	public ArrayList<String> getSceneList(String scene){
		ArrayList<String> arrayList = new ArrayList<>();
		arrayList.add(getScenesPath()+scene);
		arrayList.add(getConfig(scene));
		arrayList.add(path+"/Events/"+scene);
		//arrayList.add(getDex(scene));
		arrayList.add(getJoints(scene));
		arrayList.add(getCodesPath(scene));
		arrayList.add(getScriptsPath(scene));
		arrayList.add(getSceneScript(scene));
		arrayList.add(getBodiesScripts(scene));
		arrayList.add(getVariables(scene));
		return arrayList;
	}
	
	ArrayList<String> importantList=new ArrayList<>();
	public ArrayList<String> getImportantList(){
		if(importantList.size()>0) return importantList;
		importantList = new ArrayList<>();
		importantList.add(get("anims").toLowerCase());
		importantList.add(get("dex").toLowerCase());
		importantList.add(get("configs").toLowerCase());
		importantList.add(get("files").toLowerCase());
		importantList.add(get("scenes").toLowerCase());
		return importantList;
	}
	
	public void deleteScene(String scene){
	    //ArrayList<String> arrayList = getSceneList(newScene);
		for(String file:getSceneList(scene)){
			FileHandle fileHandle = Gdx.files.absolute(file);
			if(!fileHandle.exists()) continue;
			if(fileHandle.isDirectory()) fileHandle.deleteDirectory();
			else fileHandle.delete();
		}
	}
	
	public void deleteBody(String scene,String body){
		FileHandle fileHandle = Gdx.files.absolute(path+"/Events/"+scene+"/private/"+body);
		if(fileHandle.exists())
			fileHandle.delete();
		FileHandle fileHandle1 = Gdx.files.absolute(getScriptsPath(scene)+body);
		if(fileHandle1.exists())
			fileHandle1.delete();
	}
	
	public String getEventPath(String scene,String body,String event){
		if(body.equals("")) return path+"/Events/"+scene+"/public/"+event;
		return path+"/Events/"+scene+"/private/"+body+"/"+event;
	}
	
	public String getConfig(String scene){
		return path+"/configs/"+scene+".json";
	}
	
	public String getEvents(String scene){
		return path+"/Events/"+scene;
	}
	
	public String getChangesJson(){
		return path+"/Events/JavaChanges.json";
	}
	
	public String getUndoRedo(String scene){
		return path+"/Events/"+scene+"/UndoRedo.json";
	}
	
	public String getJoints(String scene){
		return path+"/joints/"+scene+"/";
	}
	
	public String getDex(){
		return path+"/dex/scenes.dex";//+scene+".dex";
	}
	
	public String getName(){
		return new java.io.File(path).getName();
	}
	
	public void save(LibgdxEditor editor){
		// never write a half-loaded scene over the real file
		if(editor.isLoading()) return;
		Gdx.files.absolute(path+"/scenes/"+editor.getScene()).writeString(editor.getSaveState(),false);
		if(TestApp.getCurrentApp().preferences.getBoolean("SaveUndoRedo",true))
			Gdx.files.absolute(getUndoRedo(editor.getScene())).writeString(editor.getUndoRedoJson(),false);
	}
	
	public String getScenesPath(){
		return path+"/scenes/";
	}
	
	public String getImagesPath(){
		return path+"/images/";
	}
}