package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Items.MapItem;
import java.lang.reflect.Field;

public class MapDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="MAP";
	public String name="",Map="",Tint="",parentName ="",Script="",type="DYNAMIC";
	public boolean Visible=true;
	public float x=0,y=0,z=0,rotation=0;
	
	public MapDef(){
		
	}
	
	public MapItem build(StageImp stageImp){
		if(name.equals("")) throw new RuntimeException("set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		return MapItem.create(stageImp,this,elementEvents);
	}
	
	public MapDef getClone(String newName) {
        MapDef clone = new MapDef();
        clone.elementEvents = this.elementEvents;
        clone.type = this.type;
        clone.name = newName;
        clone.parentName = (parentName != null && parentName.equals("")) ? parentName : name;
        clone.Script = this.Script;
        clone.Visible = this.Visible;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.rotation = this.rotation;
        return clone;
    }
}