package com.star4droid.star2d.Helpers;

import com.star4droid.star2d.Items.Editor;
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
		String result = FileUtil.readFile(pth+".java");
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
		String result= FileUtil.readFile(scrp+".java")+"\n"+FileUtil.readFile(scrp+".code");
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
			FileUtil.moveFile(file,arrayList.get(x));
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
			if(FileUtil.isFile(file))
			    FileUtil.copyFile(file,arrayList.get(x));
			else FileUtil.copyDir(file, arrayList.get(x));
			x++;
		}
		
		ArrayList<String> temp = new ArrayList<>();
		FileUtil.listDir(getBodiesScripts(scene),temp);
		for(String file:temp){
		    if(FileUtil.isFile(file)){
		        String read = FileUtil.readFile(file);
		        FileUtil.writeFile(file, read.replace("."+scene,"."+newScene).replace("."+scene.toLowerCase(),"."+newScene.toLowerCase()));
		    }
		}
		
		String read = FileUtil.readFile(getCodesPath(newScene));
		FileUtil.writeFile(getCodesPath(newScene), read.replace("public class "+scene+ " extends StageImp","public class "+newScene+" extends StageImp"));
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
			FileUtil.deleteFile(file);
		}
	}
	
	public void deleteBody(String scene,String body){
		FileUtil.deleteFile(path+"/Events/"+scene+"/private/"+body);
		FileUtil.deleteFile(getScriptsPath(scene)+body);
	}
	
	public String getEventPath(String scene,String body,String event){
		if(body.equals("")) return path+"/Events/"+scene+"/public/"+event;
		return path+"/Events/"+scene+"/private/"+body+"/"+event;
	}
	
	public String getConfig(String scene){
		return path+"/configs/"+scene+".json";
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
	
	public void save(Editor editor){
		FileUtil.writeFile(path+"/scenes/"+editor.getScene(),editor.getSaveState());
	}
	
	public String getScenesPath(){
		return path+"/scenes/";
	}
	
	public String getImagesPath(){
		return path+"/images/";
	}
}