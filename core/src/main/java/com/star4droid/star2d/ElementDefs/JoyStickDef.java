package com.star4droid.star2d.ElementDefs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.star4droid.template.Items.Joystick;
import com.star4droid.template.Items.StageImp;
import java.lang.reflect.Field;

public class JoyStickDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="JOYSTICK";
	public String Pad_Image="",type="UI",parentName ="",name="",Button_Image="", Script="";
	public boolean Visible=true;
	public float x=0,y=0,z=0,width=50,height=50,rotation=0;
	
	public JoyStickDef(){}
	
	public Joystick build(StageImp stageImp){
		if(name.equals("")) throw new RuntimeException("set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		String imgsPath = stageImp.getProject().getImagesPath();
		return Joystick.create(stageImp,Button_Image.equals("")? "" : (imgsPath + Button_Image),Pad_Image.equals("") ? "" : (imgsPath + Pad_Image))
				.setElementEvent(elementEvents).setDef(this);
	}
	
	public static boolean isExistsFile(String f){
		if(f.equals("")) return false;
		return new java.io.File(f).exists();
	}
	
	public JoyStickDef getClone(String newName) {
        JoyStickDef clone = new JoyStickDef();
        clone.elementEvents = this.elementEvents;
        clone.Pad_Image = this.Pad_Image;
        clone.parentName = (parentName != null && parentName.equals("")) ? parentName : name;
        clone.name = newName;
        clone.Button_Image = this.Button_Image;
        clone.Script = this.Script;
        clone.Visible = this.Visible;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.width = this.width;
        clone.height = this.height;
        clone.rotation = this.rotation;
        return clone;
    }
    
}