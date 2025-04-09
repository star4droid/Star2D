package com.star4droid.star2d.Helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.star4droid.star2d.editor.Utils;
import com.star4droid.star2d.editor.items.EditorItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
/*
	property for the editor elements and player...
*/
public class PropertySet<K, V> extends HashMap<String,Object> {
	private Matrix4 bodyMatrix=new Matrix4(),finalMatrix=new Matrix4();
	private PropertySet parent;
	private boolean printed=false;
	private ArrayList<PropertySet> childs=new ArrayList<>();
	public PropertySet(){
		
	}
	
	public static PropertySet<String,Object> getFrom(String s){
		PropertySet<String,Object> propertySet= Utils.getProperty(s);
		propertySet.init();
		return propertySet;
	}
	
	public ArrayList<PropertySet> getChilds(){
		if(childs==null)
			childs=new ArrayList<>();
		return childs;
	}
	
	public void init(){
		//idk why, it return null sometimes...
		if(childs==null) childs =new ArrayList<>();
		if(finalMatrix==null) finalMatrix = new Matrix4();
		if(bodyMatrix==null) bodyMatrix = new Matrix4();
	}
	
	public boolean setParent(PropertySet propertySet){
		if(propertySet==parent) return true;
		if(propertySet==null){
			if(parent!=null)
				try {
					parent.childs.remove(this);
				} catch(Exception exception){
					for(int x=0;x<parent.childs.size();x++){
						PropertySet set=(PropertySet)parent.childs.get(x);
						if(set.getString("name").equals(getString("name"))){
							parent.childs.remove(x);
							break;
						}
					}
				}
			parent=null;
			return true;
		}
		if(getString("name").equals(propertySet.getString("name"))) return false;
		if(containsChild(propertySet)) return false;
		propertySet.childs.add(this);
		parent = propertySet;
		return true;
	}
	
	public PropertySet getParent(){
		return parent;
	}
	
	public boolean containsChild(PropertySet propertySet){
		if(childs.contains(propertySet)) return true;
		for(PropertySet set:childs){
			if(set.containsChild(propertySet))
				return true;
		}
		return false;
	}
	
	public void setPosition(float x,float y){
		put("x",x);
		put("y",y);
	}
	
	public static PropertySet<String,Object> getDefualt(Actor actor,String addition){
		String add=(addition==""?"":(","+Gdx.files.internal("items/"+addition).readString())),
				json = Gdx.files.internal("items/default.json").readString();
		try {
			json = json.replace("--other--",add).replace("ItemName",actor.getName());
		} catch(Exception e){
			Gdx.files.external("logs/getDefault.error.txt").writeString(Utils.getStackTraceString(e)+"\n",true);
			e.printStackTrace();
			return null;
		}
		return getFrom(json);
	}
	
	@Override
	public Object get(Object key) {
		if(key.toString().contains(" ")&&!containsKey(key)&&containsKey(key.toString().replace(" ","")))
			return super.get(key.toString().replace(" ",""));
		return super.get(key);
	}

	public float getFloat(String key){
		try {
		return Utils.getFloat(get(key).toString());
		} catch(Exception exception){
			//Log.e(Utils.error_tag,"getting key error : "+key+", error : \n"+Log.getStackTraceString(exception));
			//if(!printed) {
			    //Log.e(Utils.error_tag,"missing : "+key+", full :\n"+toString());
			    //printed=true;
			//}
			
		}
		return 0;
	}
	
	public Color getColor(String key){
		try {
			return Color.valueOf(getString(key));
		} catch (Exception ex){
		    // Log.e(Utils.error_tag,"getting key error "+key+" :\n"+Log.getStackTraceString(ex));
		    // if(!printed) {
			    // Log.e(Utils.error_tag,"missing : "+key+", full :\n"+toString());
			    // printed=true;
			// }
		}
		return Color.WHITE;
	}
	
	public int getInt(String key){
		try {
			return Utils.getInt(get(key).toString());
		} catch(Exception exception){
		    // Log.e(Utils.error_tag,"getting key error : "+key+" \n"+Log.getStackTraceString(exception));
		    // if(!printed) {
			    // Log.e(Utils.error_tag,"missing : "+key+", full :\n"+toString());
			    // printed=true;
			// }
		}
		return 0;
	}
	
	@Override
	public Object put(String s,Object object){
		if(onChangeListener!=null) onChangeListener.onChange(s,object);
		return super.put(s,object);
	}
	
	public String getString(String key){
		try {
			return get(key).toString();
		} catch (Exception exception){
			// Log.e(Utils.error_tag,"getting key error : "+key+", error :"+Log.getStackTraceString(exception));
			// if(!printed) {
			    // Log.e(Utils.error_tag,"missing : "+key+", full :\n"+toString());
			    // printed=true;
			// }
		}
		return "";
	}
	
	public String toString(){
		return new Gson().toJson(this);
	}
	
	public static PropertySet<String,Object> empty = new PropertySet<>();
	public static PropertySet<String,Object> getPropertySet(Actor actor){
		if(actor==null || !(actor instanceof EditorItem)) return empty;
		try {
			PropertySet<String,Object> propertySet =((EditorItem)actor).getPropertySet();
			return propertySet;
		} catch (Exception ex){}
		return empty;
	}
	
	public void setOnChangeListener(onChangeListener listener){
		onChangeListener = listener;
	}
	
	onChangeListener onChangeListener;
	public interface onChangeListener {
		public void onChange(String s,Object object);
	}
}