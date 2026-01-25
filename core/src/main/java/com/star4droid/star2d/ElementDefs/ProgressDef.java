package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.ProgressItem;
import com.star4droid.template.Items.StageImp;
import java.lang.reflect.Field;

public class ProgressDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="PROGRESS";
	public String name="",parentName ="",Background_Color="#00FFDD",Progress_Color="#E100FF",Script="",type="UI";
	public boolean Visible=true;
	public float x=0,y=0,z=0,width=50,height=50,rotation=0,
	Progress=0,Max=0;
	
	public ProgressDef(){}
	
	public ProgressItem build(StageImp stageImp){
		if(name.equals("")) throw new RuntimeException("set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		return new ProgressItem(stageImp)
					.setElementEvent(elementEvents)
					.setDef(this);
	}
	
	public ProgressDef getClone(String newName) {
        ProgressDef clone = new ProgressDef();
        clone.elementEvents = this.elementEvents;
        clone.type = this.type;
        clone.name = newName;
        clone.parentName = (parentName != null && parentName.equals("")) ? parentName : name;
        clone.Background_Color = this.Background_Color;
        clone.Progress_Color = this.Progress_Color;
        clone.Script = this.Script;
        clone.Visible = this.Visible;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.width = this.width;
        clone.height = this.height;
        clone.rotation = this.rotation;
        clone.Progress = this.Progress;
        clone.Max = this.Max;
        return clone;
    }
}