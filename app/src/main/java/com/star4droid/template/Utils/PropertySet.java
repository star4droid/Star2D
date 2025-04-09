package com.star4droid.template.Utils;

import com.badlogic.gdx.graphics.Color;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
/*
	property for the editor elements and player...
*/
public class PropertySet<K, V> extends HashMap<String,Object> {
	private PropertySet parent;
	private ItemScript script;
	public static final String error_tag="star2d_Error";
	private boolean printed=false;
	private ArrayList<PropertySet> childs=new ArrayList<>();
	public PropertySet(){
		
	}
	
	public static PropertySet<String,Object> getFrom(String s){
		if(s==null||s.equals("")) throw new RuntimeException("no data provided to get the property!!");
		PropertySet<String,Object> propertySet= new Gson().fromJson(s, new TypeToken<PropertySet<String, Object>>(){}.getType());
		if(propertySet==null) 
		    throw new RuntimeException("json return null property!!");
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
	}
	
	public void setScript(ItemScript itemScript){
		script = itemScript;
		if(script!=null)
		    script.bodyCreated();
	}
	
	public ItemScript getScript(){
		return script;
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
			// Utils.Log(error_tag,"getting key error : "+key+", error : \n"+Utils.getStackTraceString(exception));
			// if(!printed) {
			    // Utils.Log(error_tag,"missing : "+key+", full :\n"+toString());
			    // printed=true;
			// }
			
		}
		return 0;
	}
	
	public Color getColor(String key){
		try {
			return Color.valueOf(getString(key));
		} catch (Exception ex){
		    // Utils.Log(error_tag,"getting key error "+key+" :\n"+Utils.getStackTraceString(ex));
		    // if(!printed) {
			    // Utils.Log(error_tag,"missing : "+key+", full :\n"+toString());
			    // printed=true;
			// }
		}
			return Color.WHITE;
	}
	
	public int getInt(String key){
		try {
			return Utils.getInt(get(key).toString());
		} catch(Exception exception){
		    // Utils.Log(error_tag,"getting key error : "+key+" \n"+Utils.getStackTraceString(exception));
		    // if(!printed) {
			    // Utils.Log(error_tag,"missing : "+key+", full :\n"+toString());
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
			// Utils.Log(error_tag,"getting key error : "+key+", error :"+Utils.getStackTraceString(exception));
			// if(!printed) {
			    // Utils.Log(error_tag,"missing : "+key+", full :\n"+toString());
			    // printed=true;
			// }
		}
		return "";
	}
	
	public String toString(){
		return new Gson().toJson(this);
	}
	
	public static PropertySet<String,Object> empty = new PropertySet<>();
	
	public void setOnChangeListener(onChangeListener listener){
		onChangeListener = listener;
	}
	
	onChangeListener onChangeListener;
	public interface onChangeListener {
		public void onChange(String s,Object object);
	}
}