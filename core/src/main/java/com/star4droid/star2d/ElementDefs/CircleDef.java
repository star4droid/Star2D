package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.CircleItem;
import com.star4droid.template.Items.StageImp;
import java.lang.reflect.Field;

public class CircleDef extends ItemDef {
	public static final String TYPE="CIRCLE";
	public ElementEvent elementEvents;
	public String image="",parentName = "",Tint="",type="DYNAMIC",name="", Collision="",Script="";
	public boolean Active=true,Bullet=false,isSensor=false,Fixed_Rotation=false,Visible=true;
	public float ColliderX=0,x=0,y=0,z=0,rotation=0,ColliderY=0,Scale_X=1,Scale_Y=1,Gravity_Scale=1,tileX=1,tileY=1, Collider_Radius=Integer.MAX_VALUE,
	density=1,friction=0,restitution=0.5f,radius=50;
	
	public CircleDef(){}
	
	public CircleItem build(StageImp stageImp){
		if(name.equals("")) throw new RuntimeException("set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		return new CircleItem(stageImp,null).setElementEvent(elementEvents).setDef(this);
	}
	
	public CircleDef getClone(String newName) {
        CircleDef clone = new CircleDef();
        clone.elementEvents = elementEvents;
        clone.image = this.image;
        clone.type = this.type;
        clone.parentName = (parentName != null && parentName.equals("")) ? parentName : name;
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
        clone.Scale_X = this.Scale_X;
        clone.Scale_Y = this.Scale_Y;
        clone.Gravity_Scale = this.Gravity_Scale;
        clone.tileX = this.tileX;
        clone.tileY = this.tileY;
        clone.Collider_Radius = this.Collider_Radius;
        clone.density = this.density;
        clone.friction = this.friction;
        clone.restitution = this.restitution;
        clone.radius = this.radius;
        clone.name = newName;
        return clone;
    }
    
}