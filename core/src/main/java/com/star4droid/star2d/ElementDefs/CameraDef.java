package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.CameraItem;
import com.star4droid.template.Items.StageImp;

public class CameraDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="CAMERA", type="STATIC";
	public String parentName = "",name="", Script="";
	public boolean Active=false, Visible = true;
	public float x=0,y=0,z=0,rotation=0,Zoom = 1f;
	
	public CameraDef(){}
	
	public CameraItem build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("CameraDef error : set name to the item..!!");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		return new CameraItem(stage).setElementEvent(elementEvents).setDef(this);
	}
	
	public CameraDef getClone(String newName){
	    CameraDef clone = new CameraDef();
	    clone.elementEvents = elementEvents;
        clone.parentName = (parentName!=null&&parentName.equals("")) ? parentName : name;
        clone.Script = this.Script;
        clone.Active = this.Active;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.Zoom = this.Zoom;
        clone.rotation = this.rotation;
        clone.name = newName;
        return clone;
	}
	
}