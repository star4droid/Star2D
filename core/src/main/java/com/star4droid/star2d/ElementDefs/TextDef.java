package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Items.TextItem;
import java.lang.reflect.Field;

public class TextDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="TEXT";
	public String name="",parentName ="",font="",Text="TextItem",Text_Color="#FFFFFF",Script="",type="UI";
	public boolean Visible=true;
	public float x=0,y=0,z=0,width=50,height=50,rotation=0,Font_Scale=1;
	
	public TextDef(){
		
	}
	
	public TextItem build(StageImp stageImp){
		if(name.equals("")) throw new RuntimeException("set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		return TextItem.create(stageImp,this,elementEvents);
	}
	
	public TextDef getClone(String newName) {
        TextDef clone = new TextDef();
        clone.elementEvents = this.elementEvents;
        clone.type = this.type;
        clone.name = newName;
        clone.parentName = (parentName != null && parentName.equals("")) ? parentName : name;
        clone.font = this.font;
        clone.Text = this.Text;
        clone.Text_Color = this.Text_Color;
        clone.Script = this.Script;
        clone.Visible = this.Visible;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.width = this.width;
        clone.height = this.height;
        clone.rotation = this.rotation;
        clone.Font_Scale = this.Font_Scale;
        return clone;
    }
}