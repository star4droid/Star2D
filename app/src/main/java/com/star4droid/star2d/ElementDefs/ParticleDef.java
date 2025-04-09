package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.ParticleItem;
import com.star4droid.template.Items.StageImp;
import java.lang.reflect.Field;
import com.star4droid.template.Utils.PropertySet;

public class ParticleDef {
	public ElementEvent elementEvents;
	PropertySet<String,Object> propertySet= new PropertySet<>();
	public static final String TYPE="PARTICLE";
	public String name="",Script="",type="UI",file="";
	public boolean Visible=true,Loop=true;
	public float x=0,y=0,z=0,rotation=0,Duration=-1,
				Scale_X=1,Scale_Y=1;
	
	public ParticleDef(){
		
	}
	
	public ParticleItem build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("ParticleDef error : set name to the item..!!");
		propertySet = new PropertySet<>();
		for(Field field:getClass().getFields()){
			try {
				field.setAccessible(true);
				propertySet.put(field.getName().replace("_"," "),field.get(this));
			} catch(Throwable ex){
				ex.printStackTrace();
			}
		}
		return new ParticleItem(stage).setElementEvent(elementEvents).setPropertySet(propertySet);
	}
}