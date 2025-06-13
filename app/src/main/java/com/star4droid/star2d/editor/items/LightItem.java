package com.star4droid.star2d.editor.items;

import box2dLight.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import java.util.ArrayList;

public class LightItem extends Image implements EditorItem {
	PropertySet<String,Object> propertySet;
	LibgdxEditor editor;
	String currentType ="";
	Light light;
	int rays = 0;
	float rotation = -1,Distance = 0,Cone_Degree = 0;
	String color = "";
	public LightItem(LibgdxEditor libgdxEditor){
		super(new Texture(Gdx.files.internal("images/light.png")));
		setSize(100,100);
		editor = libgdxEditor;
		libgdxEditor.addActor(this);
		addListener(new ItemClickListener(this, editor));
		//debug();
	}
	@Override
	public PropertySet<String, Object> getPropertySet() {
	    return propertySet;
	}

	@Override
	public void update() {
		if(getStage()==null || propertySet == null) return;
		float x = propertySet.getFloat("x"),
			  y = getStage().getHeight()-getHeight()-propertySet.getFloat("y");
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(x,y);
		setZIndex(propertySet.getInt("z"));
		int rays = propertySet.getInt("rays");
		float rotation = propertySet.getFloat("rotation"),
			Distance = propertySet.getFloat("Distance"),
			Cone_Degree = propertySet.getFloat("Cone Degree");
		String color = propertySet.getString("color"),
			lightType = propertySet.getString("Light Type");
		RayHandler rayHandler = editor.getRayHandler();
		float height = editor.getViewport().getWorldHeight(),
				lightY = propertySet.getFloat("y"),
				lightX = propertySet.getFloat("x");
		
		boolean updated = (lightType.equals(currentType) && rays == this.rays && rotation == this.rotation && Distance == this.Distance && Cone_Degree == this.Cone_Degree && color.equals(this.color));
		
		setVisible(propertySet.getString("Visible").equals("true"));
		
		if(light==null || !updated){
			if(light!=null){
				light.remove();
			}
			
			switch(lightType){
			   case "directional":
			        light = new DirectionalLight(rayHandler,rays, Color.valueOf(color),-rotation);
			   break;
		  	 case "cone":
			        light = new ConeLight(rayHandler,rays, Color.valueOf(color),Distance,lightX, height - Distance - lightY + Distance*0.5f,-rotation,Cone_Degree);
		   	break;
		   	case "point":
		   	    light = new PointLight(rayHandler,rays,Color.valueOf(color),Distance,lightX+Distance*0.5f, height - Distance - lightY + Distance*0.5f);
		   	break;
	        default:
		        light = new PointLight(rayHandler,rays,Color.valueOf(color),Distance,lightX+Distance*0.5f, height - Distance - lightY + Distance*0.5f);
			}
		}
		
		this.color = color;
		this.Cone_Degree = Cone_Degree;
		this.rays = rays;
		this.color = color;
		this.rotation = rotation;
		currentType = lightType;
		
		if(light!=null){
			if(light instanceof ConeLight){
				setPosition(lightX,height - Distance - lightY + Distance*0.5f);
			} else if(light instanceof PointLight){
				light.setPosition(lightX+Distance*0.5f, height - Distance - lightY + Distance*0.5f);
			}
			light.setActive(propertySet.getString("Active").equals("true"));
			light.setXray(propertySet.getString("X Ray").equals("true"));
			light.setStaticLight(propertySet.getString("Static Light").equals("true"));
			light.setSoftnessLength(propertySet.getFloat("Softness Length"));
			light.setSoft(propertySet.getString("Soft").equals("true"));
			Actor actor=editor.findActor(propertySet.getString("attachTo"));
			if(actor!=null && actor instanceof EditorItem){
				light.attachToBody(((EditorItem)actor).getBody());
				if(light instanceof PositionalLight)
				((PositionalLight)light).attachToBody(((EditorItem)actor).getBody(),propertySet.getFloat("Offset X"),propertySet.getFloat("Offset Y"));
				else light.attachToBody(((EditorItem)actor).getBody());
			}
		}
	}
	
	@Override
	public boolean remove() {
		if(light!=null){
		    try {
    			light.remove();
    			light.dispose();
			} catch(Error | Exception ex){}
		}
		return super.remove();
	}
	
	public LightItem setDefault(String type) {
		propertySet = PropertySet.getDefualt(this,"Lights/"+type+".json");
		if(propertySet==null) 
		    throw new RuntimeException("null light properties: "+type);
		if(propertySet.containsKey("Script"))
			propertySet.remove("Script");
		if(propertySet.containsKey("parent"))
			propertySet.remove("parent");
		return this;
	}
	
	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
		this.propertySet = propertySet;
		PropertySet<String,Object> temp = PropertySet.getDefualt(this, "Lights/"+propertySet.getString("Light Type")+".json");
		for(String key:temp.keySet()){
		    if(key.equals("Script")||key.equals("parent")) continue;
			if(!propertySet.containsKey(key)){
				propertySet.put(key,temp.get(key));
			}
		}
		
		ArrayList<String> toDel = new ArrayList<>();
	   for(String key:propertySet.keySet()){
			if(!temp.containsKey(key)){
				toDel.add(key);
			}
		}
		for(String key:toDel)
	        propertySet.remove(key);
		
		update();
	}

	@Override
	public String getTypeName() {
	    return "Light";
	}
	
}