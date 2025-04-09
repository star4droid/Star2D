package com.star4droid.star2d.Helpers;

import android.content.Context;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.star4droid.star2d.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JointsHelper {
	private static JsonArray jointDefs;
	private static ArrayList<String> jointsList;
	private static HashMap<String,Object> getMap= new HashMap<>();
	private static ArrayList<HashMap<String,Object>> jointsListmap;
	public static void init(Context context){
		jointDefs = JsonParser.parseString(Utils.readAssetFile("java/joints.json",context)).getAsJsonArray();
		
	}
	
	public static String get(String joint, String target) {
		if(!joint.contains("Def")) joint+="Def";
		if(getMap.containsKey(joint+","+target)) return getMap.get(joint+","+target).toString();
		for (int i = 0; i < jointDefs.size(); i++) {
			JsonObject jointDef = jointDefs.get(i).getAsJsonObject();
			if (jointDef.get("joint").getAsString().equals(joint)) {
				String params = jointDef.get(target).getAsString();
				getMap.put(joint+","+target,params.replace(" ",""));
				return params.replace(" ","");
			}
		}
		return null;
	}
	
	public static ArrayList<String> getJointsList() {
		if(jointsList!=null) return jointsList;
		jointsList = new ArrayList<>();
		for (int i = 0; i < jointDefs.size(); i++) {
			JsonObject jointDef = jointDefs.get(i).getAsJsonObject();
			jointsList.add(jointDef.get("joint").getAsString());
		}
		return jointsList;
	}
	
	public static ArrayList<HashMap<String,Object>> getJointsListMap() {
		if(jointsListmap!=null) return jointsListmap;
		jointsListmap = new ArrayList<>();
		for (int i = 0; i < jointDefs.size(); i++) {
			JsonObject jointDef = jointDefs.get(i).getAsJsonObject();
			HashMap<String,Object> hash= new HashMap<>();
			hash.put("joint",jointDef.get("joint").getAsString());
			hash.put("params",jointDef.get("params").getAsString());
			hash.put("types",jointDef.get("types").getAsString());
			jointsListmap.add(hash);
		}
		return jointsListmap;
	}
	
	public static Class[] getClasses(String input) {
		input = input.replace(" ","");
		String[] classNames = input.split(",");
		List<Class> classList = new ArrayList<>();
		
		for (String className : classNames) {
			Class clazz = null;
			if (className.equals("Vector2")) {
				clazz = Vector2.class;
				} else if (className.equals("Body")) {
				clazz = Body.class;
				} else if(className.equals("float")){
					clazz = float.class;
				} else {
				try {
					clazz = Class.forName(className);
					} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
			
			if (clazz != null) {
				classList.add(clazz);
			}
		}
		
		Class[] classes = new Class[classList.size()];
		return classList.toArray(classes);
	}
	
}