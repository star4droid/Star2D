package com.star4droid.star2d.ElementDefs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.star4droid.template.Items.Joystick;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PropertySet;
import java.lang.reflect.Field;

public class JoyStickDef {
	public ElementEvent elementEvents;
	PropertySet<String,Object> propertySet= new PropertySet<>();
	public static final String TYPE="JOYSTICK", type="UI";
	public String Pad_Image="",name="",Button_Image="", Script="";
	public boolean Visible=true;
	public float x=0,y=0,z=0,width=50,height=50,rotation=0;
	
	public JoyStickDef(){
		
	}
	
	public Joystick build(StageImp stageImp){
		if(name.equals("")) throw new RuntimeException("set name to the item..!!");
		propertySet = new PropertySet<>();
		for(Field field:getClass().getFields()){
			try {
				field.setAccessible(true);
				propertySet.put(field.getName().replace("_"," "),field.get(this));
			} catch(Throwable ex){
				
			}
		}
		
		return Joystick.create(stageImp,Button_Image,Pad_Image)
				.setElementEvent(elementEvents)
				.setPropertySet(propertySet);
	}
	
	public static boolean isExistsFile(String f){
		if(f.equals("")) return false;
		return new java.io.File(f).exists();
	}
}