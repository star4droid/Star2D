package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.CustomBody;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.Utils;
import java.lang.reflect.Field;

public class CustomDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="CUSTOM";
	public String image="",parentName ="",Tint="",type="DYNAMIC",name="", Collision="",Points = "0,0-1,0,1,1-0,1",Script="";
	public boolean Active=true,Bullet=false,isSensor=false,Fixed_Rotation=false,Visible=true;
	public float x=0,y=0,z=0,rotation=0,Gravity_Scale=1,
	density=1,Scale_X=1,Scale_Y=1,friction=0,restitution=0.5f,
	width=50,height=50,tileX=1,tileY=1;
	
	public CustomDef(){}
	
	public CustomBody build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("CustomDef error : set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		try {
    		return new CustomBody(stage,null).setElementEvent(elementEvents).setDef(this);
		} catch(Exception ex){
		    throw new RuntimeException("CustomDef error : \n" + Utils.getStackTraceString(ex));
		}
	}
	
	public CustomDef getClone(String newName) {
        CustomDef clone = new CustomDef();
        clone.elementEvents = this.elementEvents;
        clone.image = this.image;
        clone.type = this.type;
        clone.parentName = (parentName != null && parentName.equals("")) ? parentName : name;
        clone.Collision = this.Collision;
        clone.Points = this.Points;
        clone.Script = this.Script;
        clone.Tint = this.Tint;
        clone.Active = this.Active;
        clone.Bullet = this.Bullet;
        clone.isSensor = this.isSensor;
        clone.Fixed_Rotation = this.Fixed_Rotation;
        clone.Visible = this.Visible;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.rotation = this.rotation;
        clone.Gravity_Scale = this.Gravity_Scale;
        clone.density = this.density;
        clone.Scale_X = this.Scale_X;
        clone.Scale_Y = this.Scale_Y;
        clone.friction = this.friction;
        clone.restitution = this.restitution;
        clone.width = this.width;
        clone.height = this.height;
        clone.tileX = this.tileX;
        clone.tileY = this.tileY;
        clone.name = newName;
        return clone;
    }
    
}