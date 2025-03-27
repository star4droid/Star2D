package com.star4droid.star2d.Helpers.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.star4droid.star2d.editor.LibgdxEditor;
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
	
	public void renameScene(String scene,String newScene){
		ArrayList<String> arrayList = getSceneList(newScene);
		int x=0;
		for(String file:getSceneList(scene)){
			FileHandle fileHandle = Gdx.files.absolute(file);
			if(fileHandle.exists())
				fileHandle.moveTo(Gdx.files.absolute(arrayList.get(x)));
			x++;
		}
	}
	
	public String getScriptsPath(String scene){
		return path+"/scripts/"+scene+"/";
	}
	
	public void copyScene(String scene,String newScene){
		ArrayList<String> arrayList = getSceneList(newScene);
		int x=0;
		for(String file:getSceneList(scene)){
			FileHandle fh = Gdx.files.absolute(file);
			if(!fh.exists()) continue;
			if(!fh.isDirectory())
			    fh.copyTo(Gdx.files.absolute(arrayList.get(x)));
			x++;
		}
		
		FileHandle[] list = Gdx.files.absolute(getBodiesScripts(scene)).list();
		for(FileHandle fileHandle:list){
		    if(!fileHandle.isDirectory()){
		        String read = "";
		        try {
		            if(fileHandle.exists())
						read = fileHandle.readString();
		        } catch(Exception ex){}
		        fileHandle.writeString(read.replace("."+scene,"."+newScene).replace("."+scene.toLowerCase(),"."+newScene.toLowerCase()),false);
		    }
		}
		
		String read = "";
		try {
			FileHandle fileHandle = Gdx.files.absolute(getCodesPath(newScene));
		    if(fileHandle.exists())
				read = fileHandle.readString();
		} catch(Exception ex){}
		Gdx.files.absolute(getCodesPath(newScene)).writeString(read.replace("public class "+scene+ " extends StageImp","public class "+newScene+" extends StageImp"),false);
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
		arrayList.add(getBodiesScripts(scene));
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
			if(fileHandle.exists())
				fileHandle.delete();
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
		Gdx.files.absolute(path+"/scenes/"+editor.getScene()).writeString(editor.getSaveState(),false);
		Gdx.files.absolute(getUndoRedo(editor.getScene())).writeString(editor.getUndoRedoJson(),false);
	}
	
	public String getScenesPath(){
		return path+"/scenes/";
	}
	
	public String getImagesPath(){
		return path+"/images/";
	}
}