package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.BoxBody;
import com.star4droid.template.Items.StageImp;
import java.lang.reflect.Field;

public class BoxDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="BOX";
	public String image="",parentName ="",type="DYNAMIC",name="", Collision="",Script="",Tint="";
	/** optional touch images (path under images/, "" = none): shown while pressed / applied at touch start / applied at touch end */
	public String Pressed_Image="",Touch_Start_Image="",Touch_End_Image="";
	public boolean Active=true,Bullet=false,isSensor=false,Fixed_Rotation=false,Visible=true;
	public float ColliderX=0,x=0,y=0,z=0,rotation=0,ColliderY=0,Gravity_Scale=1,
	density=1,Scale_X=1,Scale_Y=1,friction=0,restitution=0.5f,
	width=50,height=50,tileX=1,tileY=1,
	Collider_Width=Integer.MAX_VALUE, Collider_Height=Integer.MAX_VALUE;
	
	/** Animation file of the anims folder ("walk" or "hero/walk"): when set it is shown instead of the image. */
	public String animation = "";

	public BoxDef(){}
	
	public BoxBody build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("BoxDef error : set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		return new BoxBody(stage,null).setElementEvent(elementEvents).setDef(this);
	}
	
	public BoxDef getClone(String newName){
	    BoxDef clone = new BoxDef();
		clone.Pivot = this.Pivot;
		clone.Pivot_X = this.Pivot_X;
		clone.Pivot_Y = this.Pivot_Y;
	    clone.elementEvents = ElementEventAdapter.copyOf(elementEvents);
	    clone.animation = this.animation;
	    clone.image = this.image;
	    clone.Pressed_Image = this.Pressed_Image;
	    clone.Touch_Start_Image = this.Touch_Start_Image;
	    clone.Touch_End_Image = this.Touch_End_Image;
        clone.type = this.type;
        clone.parentName = (parentName!=null&&parentName.equals("")) ? parentName : name;
        clone.Collision = this.Collision;
        clone.Script = this.Script;
        clone.Tint = this.Tint;
        clone.Active = this.Active;
        clone.Bullet = this.Bullet;
        clone.isSensor = this.isSensor;
        clone.Fixed_Rotation = this.Fixed_Rotation;
        clone.Visible = this.Visible;
        clone.ColliderX = this.ColliderX;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.rotation = this.rotation;
        clone.ColliderY = this.ColliderY;
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
        clone.Collider_Width = this.Collider_Width;
        clone.Collider_Height = this.Collider_Height;
        clone.name = newName;
        return clone;
	}
	
}