package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import java.lang.reflect.Field;
import box2dLight.*;
import com.badlogic.gdx.graphics.Color;
import com.star4droid.template.Utils.PlayerItem;

public class LightDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="LIGHT";
	public String name="",parentName ="",Light_Type="point",color="#FFFFFF",attach_To="",Script="";
	public boolean Active=true, Visible=true,X_Ray=false,Static_Light=false,Soft=false;
	public float x=0,y=0,z=0,Cone_Degree=90,Distance=50,rays=50,rotation=0, Softness_Length=2.5f, Offset_X=0,Offset_Y=0;
	
	public LightDef(){}
	
	public Light build(StageImp stageImp){
		if(name.equals("")) throw new RuntimeException("set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		RayHandler rayHandler=stageImp.getRayHandler();
		Light light =null;
		float height = stageImp.getViewport().getWorldHeight();
		switch(Light_Type){
		   case "directional":
		        light = new DirectionalLight(rayHandler,(int)rays, Color.valueOf(color),-rotation);
		   break;
		   case "cone":
		        light = new ConeLight(rayHandler,(int)rays, Color.valueOf(color),Distance * StageImp.WORLD_SCALE,x, y * StageImp.WORLD_SCALE,-rotation,Cone_Degree);
		   break;
	        default:
		        light = new PointLight(rayHandler,(int)rays,Color.valueOf(color),Distance * StageImp.WORLD_SCALE,x * StageImp.WORLD_SCALE, y * StageImp.WORLD_SCALE);
		}
		if(light!=null){
		    stageImp.addLight(name,light);
		    light.setActive(Active);
		    light.setXray(X_Ray);
		    light.setStaticLight(Static_Light);
		    light.setSoftnessLength(Softness_Length);
		    light.setSoft(Soft);
		    PlayerItem item=stageImp.findItem(attach_To);
		    if(item!=null && item.getBody()!=null){
		        light.attachToBody(item.getBody());
		        if(light instanceof PositionalLight)
		    ((PositionalLight)light).attachToBody(item.getBody(),Offset_X,Offset_Y);
		else light.attachToBody(item.getBody());
		    }
		}
		
		return light;
	}
	
}