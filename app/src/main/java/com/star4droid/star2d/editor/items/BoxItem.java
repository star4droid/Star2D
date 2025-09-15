package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.Utils;
import java.io.File;
import java.util.ArrayList;

public class BoxItem extends Image implements EditorItem {
	PropertySet<String,Object> propertySet;
	float[] offset=new float[]{0,0};
	String tint = "#FFFFFF";
	Body body;
	LibgdxEditor editor;
	public BoxItem(final LibgdxEditor libgdxEditor){
		super(new Texture(Gdx.files.internal("images/logo.png")));
		this.editor = libgdxEditor;
		setSize(50,50);
		libgdxEditor.addActor(this);
		setDrawable(new TextureRegionDrawable(new Texture(Gdx.files.internal("images/logo.png"))));
		addListener(new ItemClickListener(this,editor));
		//debug();
	}
	
	@Override
	public PropertySet<String, Object> getPropertySet() {
	    return propertySet;
	}

	@Override
	public void update() {
		//Gdx.files.external("logs/box.txt").writeString(String.format("w : %1$s, h : %2$s, x : %3$s, y : %4$s\n",getWidth(),getHeight(),getX(),getY()),false);
		if(getStage()==null) return;
		setSize(propertySet.getFloat("width"),propertySet.getFloat("height"));
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		float x = propertySet.getFloat("x"),
			  y = getStage().getHeight()-getHeight()-propertySet.getFloat("y");
		//Gdx.files.external("logs/box.txt").writeString("eh : "+getStage().getHeight()+", bxh : "+getHeight()+", y : "+y+" \n = "+getY()+"\n"+"_".repeat(10)+"\n",true);
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(x,y);
		setZIndex(propertySet.getInt("z"));
		setRotation(-propertySet.getFloat("rotation"));
		setScale(propertySet.getFloat("Scale X"),propertySet.getFloat("Scale Y"));
		setVisible(propertySet.getString("Visible").equals("true"));
		offset[0] = propertySet.getFloat("ColliderX")*-1;
    	offset[1] = propertySet.getFloat("ColliderY")*-1;
		if(body == null){
			BodyDef def = new BodyDef();
    		def.type = BodyDef.BodyType.StaticBody;
    		def.position.set(0,0);
    		body = getEditor().getWorld().createBody(def);
			PolygonShape shape = new PolygonShape();
			shape.setAsBox(propertySet.getFloat("Collider Width")*0.5f,propertySet.getFloat("Collider Height")*0.5f);
			FixtureDef fx = new FixtureDef();
			fx.shape = shape;
			body.createFixture(fx);
		} else {
			((PolygonShape)body.getFixtureList().get(0).getShape()).setAsBox(propertySet.getFloat("Collider Width")*0.5f,propertySet.getFloat("Collider Height")*0.5f);
		}
		body.setTransform(new Vector2((offset[0]+x+(getWidth()*0.5f)),(offset[1]+y+(getHeight()*0.5f))),(float)Math.toRadians(-propertySet.getFloat("rotation")));
		//setDrawable(new TextureRegionDrawable(new Texture(Gdx.files.internal("images/logo.png"))));
		if(!propertySet.getString("Tint").equals(tint)){
		    setColor(propertySet.getColor("Tint"));
		    tint = propertySet.getString("Tint");
		}
	}
	
	@Override
	public Body getBody() {
		return body;
	}
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public BoxItem setDefault() {
		propertySet = PropertySet.getDefualt(this,"box.json");
		//if(propertySet==null) Log.e(Utils.error_tag,"Null PropertySet");
		return this;
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
	}

	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
	    this.propertySet = propertySet;
		if(propertySet==null) return;
		if(!propertySet.getString("Shape").equals("Box")){
		    if(editor!=null){
		        try {
		            remove();
		            EditorItem newBody = propertySet.getString("Shape").equals("Circle") ? new CircleItem(editor) : new CustomItem(editor);
            		editor.addActor((Actor)newBody);
            		newBody.setProperties(propertySet);
            		editor.selectActor((Actor)newBody);
            		newBody.update();
		        } catch(Exception e){}
		    }
		    return;
		}
		
		PropertySet<String,Object> temp = PropertySet.getDefualt(this,"box.json");
		for(String s:temp.keySet()){
			if(!propertySet.containsKey(s)){
				propertySet.put(s,temp.get(s));
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
		
		if (!this.propertySet.getString("image").equals("")) {
			String img = this.editor.getProject().getImagesPath() + this.propertySet.getString("image").replace(Utils.seperator,"/");
			File file = new File(img);
			//Gdx.files.external("logs/image.error.txt").writeString("ex : "+file.exists()+",img : "+file.getAbsolutePath()+"\n",true);
			if (file.exists()) {
				float rx = this.propertySet.getFloat("tileX");
				float ry = this.propertySet.getFloat("tileY");
				Utils.setImageFromFile(this, img, rx, ry);
			}
		}
	}

	@Override
	public String getTypeName() {
	    return "Box";
	}
	
}