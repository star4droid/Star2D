package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.Utils;

public class CircleItem extends Image implements PlayerItem {
	StageImp stage;
	ElementEvent elementEvent;
	float circleY=0;
	float[] offset = new float[]{0,0};
	Body body;
	PropertySet<String,Object> propertySet;
	public CircleItem(StageImp stageImp,Drawable drawable){
		super(drawable);
		stage = stageImp;
		setY(0);
		addListener(new InputListener(){
			@Override
			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
				getScript().touchEnd(event);
				else if(elementEvent!=null) elementEvent.onTouchEnd(CircleItem.this,event);
			}
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
				getScript().touchStart(event);
				else if(elementEvent!=null) elementEvent.onTouchStart(CircleItem.this,event);
				return true;
			}
		});
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(getScript()!=null)
					getScript().click();
				else if(elementEvent!=null) elementEvent.onClick(CircleItem.this);
			}
		});
	}
	
	public CircleItem setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	public CircleItem setPropertySet(PropertySet<String,Object> set){
		propertySet = set;
		if(body!=null) {
		    body.getWorld().destroyBody(body);
		    body = null;
		}
		setup();
		return this;
	}
	
	private void setup(){
		int rx = propertySet.getInt("tileX"),
		ry = propertySet.getInt("tileY");
		float width = propertySet.getFloat("radius")*2,
		     height = propertySet.getFloat("radius")*2;
		float x = propertySet.getFloat("x"),
		y = propertySet.getFloat("y");
		y = stage.getViewport().getWorldHeight()-getHeight()-y;
		String imgPath=propertySet.getString("image");
		//setDrawable(Utils.getDrawable(imgPath.equals("")?Gdx.files.internal("images/logo.png"):Utils.absolute(imgPath)));
		//Utils.setImageFromFile(this,.project.getImagesPath()+propertySet.getString("image"),new Point(rx,ry),null,null);
		setDrawable(Utils.getDrawable(Utils.internal("images/logo.png")));
		stage.setImage(this,propertySet.getString("image"));
		setSize(width,height);
		setPosition(x,y);
		setZIndex(propertySet.getInt("z"));
		setRotation(-propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
		setName(propertySet.getString("name"));
		if(!propertySet.getString("type").equals("UI")){
			String bt= String.valueOf(propertySet.getString("type").charAt(0)).toUpperCase();
			BodyDef.BodyType type = bt.equals("K")?BodyDef.BodyType.KinematicBody:(bt.equals("S")?BodyDef.BodyType.StaticBody:BodyDef.BodyType.DynamicBody);
			//creating the body
			if(body==null){
    			BodyDef def = new BodyDef();
    			def.type = type;
    			offset[0] = propertySet.getFloat("ColliderX")*-1;
    			offset[1] = propertySet.getFloat("ColliderY")*-1;
    			def.position.set(0,0);
    			body = stage.world.createBody(def);
			} else {
			    try {
    			    //destroy the previous fixture if it's available...
    			    //lazy to do something to check for it ...
    			    body.destroyFixture(body.getFixtureList().get(0));
			    } catch(Exception ex){}
			}
			//define its properties
			CircleShape shape = new CircleShape();
			shape.setRadius(propertySet.getFloat("Collider Radius"));
			FixtureDef fx = new FixtureDef();
			fx.shape = shape;
			fx.friction=propertySet.getFloat("friction");
			fx.density=propertySet.getFloat("density");
			fx.restitution=propertySet.getFloat("restitution");
			fx.isSensor = propertySet.getString("isSensor").equals("true");
			body.createFixture(fx).setUserData(this);
			body.setFixedRotation(propertySet.getString("Fixed Rotation").equals("true"));
			body.setActive(propertySet.getString("Active").equals("true"));
			body.setBullet(propertySet.getString("Bullet").equals("true"));
			body.setGravityScale(propertySet.getFloat("Gravity Scale"));
			// TODO : Need to fix (check the solution)
			body.setTransform(new Vector2((offset[0]+x+(width/2)),(offset[1]+y-(height/2))),(float)Math.toRadians(-propertySet.getFloat("rotation")));
			
		}
		//if it's not added to stage ....
		if(getStage()==null)
		    stage.addActor(this);
	}
	
	@Override
	public void setY(float y) {
		super.setY(y);//stage.getViewport().getWorldHeight()-getHeight()-y);
		circleY = y;
	}

	@Override
	public void setPosition(float x, float y) {
		super.setX(x);
		setY(y);
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		//if(getStage()!=null) setY(circleY);
		if(getProperties().getString("type").equals("UI"))
		    setOrigin(getWidth()*0.5f,getHeight()*0.5f);
	}
	
	@Override
	public void update() {
	    if(propertySet!=null&&propertySet.getString("do update").equals("true")){
		    setPropertySet(propertySet);
		    propertySet.remove("do update");
		}
		if(body!=null){
		    offset[0] = propertySet.getFloat("ColliderX")*-1;
			offset[1] = propertySet.getFloat("ColliderY")*-1;
			float x = body.getPosition().x,
				y = body.getPosition().y;
			setPosition(x - offset[0] - getWidth()*0.5f,
						y - offset[1] - getHeight()*0.5f);
			setRotation((float)Math.toDegrees(body.getAngle()));
		}
		if(getScript()!=null)
			getScript().bodyUpdate();
		else if(elementEvent!=null) elementEvent.onBodyUpdate(this);
	}

	@Override
	public Body getBody() {
	    return body;
	}

	ChildsHolder childsHolder = new ChildsHolder(this);
	@Override
	public ChildsHolder getChildsHolder() {
	    return childsHolder;
	}

	@Override
	public PropertySet<String, Object> getProperties() {
	    return propertySet;
	}

	@Override
	public Actor getClone(String newName) {
		PropertySet<String,Object> set = new PropertySet<>();
		set.putAll(propertySet);
		set.put("old",getParentName());
		set.put("name",newName);
	    return new CircleItem(stage,null).setPropertySet(set).setElementEvent(elementEvent);
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		update();
	}
	
	@Override
	public boolean setZIndex(int z){
	    boolean b = true;
	    try {
	        b = super.setZIndex(z);
	    } catch(Exception e){}
	    if(stage!=null) stage.updateActors();
	    return b;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}