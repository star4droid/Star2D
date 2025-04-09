package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.ProgressItem;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PropertySet;
import java.lang.reflect.Field;

public class ProgressDef {
	public ElementEvent elementEvents;
	PropertySet<String,Object> propertySet= new PropertySet<>();
	public static final String TYPE="PROGRESS";
	public String name="",Background_Color="#00FFDD",Progress_Color="#E100FF",Script="",type="UI";
	public boolean Visible=true;
	public float x=0,y=0,z=0,width=50,height=50,rotation=0,
	Progress=0,Max=0;
	
	public ProgressDef(){
		
	}
	
	public ProgressItem build(StageImp stageImp){
		if(name.equals("")) throw new RuntimeException("set name to the item..!!");
		propertySet = new PropertySet<>();
		for(Field field:getClass().getFields()){
			try {
				field.setAccessible(true);
				propertySet.put(field.getName().replace("_"," "),field.get(this));
			} catch(Throwable ex){
				
			}
		}
		
		return new ProgressItem(stageImp)
					.setElementEvent(elementEvents)
					.setPropertySet(propertySet);
	}
}