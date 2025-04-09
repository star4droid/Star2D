package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.CustomBody;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.template.Utils.Utils;
import java.lang.reflect.Field;

public class CustomDef {
	public ElementEvent elementEvents;
	PropertySet<String,Object> propertySet= new PropertySet<>();
	public static final String TYPE="CUSTOM";
	public String image="",type="DYNAMIC",name="", Collision="",Points = "0,0-1,0,1,1-0,1",Script="";
	public boolean Active=true,Bullet=false,isSensor=false,Fixed_Rotation=false,Visible=true;
	public float x=0,y=0,z=0,rotation=0,Gravity_Scale=1,
	density=1,friction=0,restitution=0.5f,
	width=50,height=50,tileX=1,tileY=1;
	
	public CustomDef(){
		propertySet.put("Collision","");
	}
	
	public CustomBody build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("CustomDef error : set name to the item..!!");
		try {
    		propertySet = new PropertySet<>();
    		for(Field field:getClass().getFields()){
    			try {
    				field.setAccessible(true);
    				propertySet.put(field.getName().replace("_"," "),field.get(this));
    			} catch(Throwable ex){
    				
    			}
    		}
    		
    		return new CustomBody(stage,null).setElementEvent(elementEvents).setPropertySet(propertySet);
		} catch(Exception ex){
		    throw new RuntimeException("CustomDef error : \n" + Utils.getStackTraceString(ex));
		}
	}
}