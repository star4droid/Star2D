package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.BoxBody;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PropertySet;
import java.lang.reflect.Field;

public class BoxDef {
	public ElementEvent elementEvents;
	PropertySet<String,Object> propertySet= new PropertySet<>();
	public static final String TYPE="BOX";
	public String image="",type="DYNAMIC",name="", Collision="",Script="";
	public boolean Active=true,Bullet=false,isSensor=false,Fixed_Rotation=false,Visible=true;
	public float ColliderX=0,x=0,y=0,z=0,rotation=0,ColliderY=0,Gravity_Scale=1,
	density=1,friction=0,restitution=0.5f,
	width=50,height=50,tileX=1,tileY=1,
	Collider_Width=Integer.MAX_VALUE, Collider_Height=Integer.MAX_VALUE;
	
	public BoxDef(){
		propertySet.put("Collision","");
	}
	
	public BoxBody build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("BoxDef error : set name to the item..!!");
		propertySet = new PropertySet<>();
		for(Field field:getClass().getFields()){
			try {
				field.setAccessible(true);
				propertySet.put(field.getName().replace("_"," "),field.get(this));
			} catch(Throwable ex){
				
			}
		}
		if(Collider_Width==Integer.MAX_VALUE) propertySet.put("Collider Height",height);
		if(Collider_Height==Integer.MAX_VALUE) propertySet.put("Collider Width",width);
		return new BoxBody(stage,null).setElementEvent(elementEvents).setPropertySet(propertySet);
	}
}