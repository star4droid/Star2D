package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.badlogic.gdx.graphics.Color;
import com.star4droid.star2d.editor.Utils;
import com.badlogic.gdx.graphics.g2d.Batch;
import java.io.File;
import java.util.ArrayList;

public class EditorCameraItem extends Image implements EditorItem {
	PropertySet<String,Object> propertySet;
	LibgdxEditor editor;
	ShapeRenderer shapeRenderer;
	public EditorCameraItem(final LibgdxEditor libgdxEditor){
		super(new Texture(Gdx.files.internal("images/bodies/camera.png")));
		this.editor = libgdxEditor;
		this.shapeRenderer = libgdxEditor.getShapeRenderer();
		setSize(50,50);
		libgdxEditor.addActor(this);
		setDrawable(new TextureRegionDrawable(new Texture(Gdx.files.internal("images/bodies/camera.png"))));
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
		//setSize(propertySet.getFloat("width"),propertySet.getFloat("height"));
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		float x = propertySet.getFloat("x"),
			  y = propertySet.getFloat("y");
		//Gdx.files.external("logs/box.txt").writeString("eh : "+getStage().getHeight()+", bxh : "+getHeight()+", y : "+y+" \n = "+getY()+"\n"+"_".repeat(10)+"\n",true);
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(x,y);
		setZIndex(propertySet.getInt("z"));
		setRotation(-propertySet.getFloat("rotation"));
	}
	
	@Override
	public Body getBody() {
		return null;
	}
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public EditorCameraItem setDefault() {
		propertySet = PropertySet.getDefualt(this,"camera.json");
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
		
		PropertySet<String,Object> temp = PropertySet.getDefualt(this,"camera.json");
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
		
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
	    super.draw(batch, parentAlpha);
	    batch.end();
	    shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
		Gdx.gl.glLineWidth(3f);
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
		shapeRenderer.setColor(Color.RED);
		float zoom = propertySet.getFloat("Zoom");
		float width = editor.getLogicWidth() * zoom,
		    height = editor.getLogicHeight() * zoom;
		
		shapeRenderer.rect(getX() + getWidth()*0.5f - width*0.5f,getY() + getHeight()*0.5f - height*0.5f, width, height);
		shapeRenderer.end();
		Gdx.gl.glLineWidth(1f);
		batch.begin();
	}

	@Override
	public String getTypeName() {
	    return "Camera";
	}
	
}