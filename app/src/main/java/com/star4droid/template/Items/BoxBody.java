package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.template.Utils.Utils;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.Texture;

public class BoxBody extends Image implements PlayerItem {
	StageImp stage;
	float boxY=0,animationTime=0;
	int tileX=1,tileY=1;
	ElementEvent elementEvent;
	PropertySet<String,Object> propertySet;
	float[] offset=new float[]{0,0};
	Body body;
	ChildsHolder childsHolder = new ChildsHolder(this);
	Animation<Drawable> animation;
	
	public BoxBody(StageImp s,Drawable drawable){
		super(drawable);
		stage = s;
		setY(0);
		addListener(new InputListener(){
			@Override
			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchEnd(event);
				else if(elementEvent!=null) elementEvent.onTouchEnd(BoxBody.this,event);
			}
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchStart(event);
				else if(elementEvent!=null) elementEvent.onTouchStart(BoxBody.this,event);
				return true;
			}
		});
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(getScript()!=null)
					getScript().click();
				else if(elementEvent!=null) elementEvent.onClick(BoxBody.this);
			}
		});
	}
	
	public BoxBody setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	public BoxBody setPropertySet(PropertySet<String,Object> set){
		propertySet = set;
		/*if(body!=null) {
		    body.getWorld().destroyBody(body);
		    body = null;
		}*/
		createBody();
		return this;
	}
	
	@Override
	public void setY(float y) {
		super.setY(y);//stage.getViewport().getWorldHeight()-getHeight()-y);
		boxY = y;
	}

	@Override
	public void setPosition(float x, float y) {
		super.setX(x);
		setY(y);
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		//if(getStage()!=null) setY(boxY);
		if(!getProperties().getString("type").equals("UI"))
		    setOrigin(getWidth()*0.5f,getHeight()*0.5f);
	}
	
	private void createBody(){
		int rx = propertySet.getInt("tileX"),
			ry = propertySet.getInt("tileY");
		setSize(propertySet.getFloat("width"),propertySet.getFloat("height"));
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		float x = propertySet.getFloat("x"),
		y = propertySet.getFloat("y");
		y = stage.getViewport().getWorldHeight()-getHeight()-y;
		//setDrawable(Utils.getDrawable(Utils.absolute(propertySet.getString("image"))));
		tileX = Math.max(propertySet.getInt("tileX"),1);
		tileY = Math.max(propertySet.getInt("tileY"),1);
		if(!stage.setImage(this,propertySet.getString("image"))){
		    setDrawable(Utils.getDrawable(Utils.internal("images/logo.png")));
		}
		
		if(tileX!=1||tileY!=1){
		try {
		    Texture texture = ((TextureRegionDrawable)getDrawable()).getRegion().getTexture();
		    texture.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
		    TextureRegion textureRegion = new TextureRegion(texture);
		    textureRegion.setRegion(0,0,texture.getWidth()*tileX,texture.getHeight()*tileY);
		    setDrawable(new TextureRegionDrawable(textureRegion));
		    } catch(Exception ex){}
		}
		
		//Utils.setImageFromFile(this,player.project.getImagesPath()+propertySet.getString("image"),new Point(rx,ry),null,null);
		setName(propertySet.getString("name"));
		setPosition(x,y);
		setZIndex(propertySet.getInt("z"));
		setRotation(-propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
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
			PolygonShape shape = new PolygonShape();
			shape.setAsBox(propertySet.getFloat("Collider Width")*0.5f,propertySet.getFloat("Collider Height")*0.5f);
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
			body.setTransform(new Vector2((offset[0]+x+(getWidth()*0.5f)),(offset[1]+y+(getHeight()*0.5f))),(float)Math.toRadians(-propertySet.getFloat("rotation")));
			
		}
		if(getStage()==null)
		    stage.addActor(this);
	}
	@Override
	public void update() {
		//body_x = offset + x + (width/2)
		//x = body_x - offset - (width/2)
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

	
	@Override
	public ChildsHolder getChildsHolder() {
		if(childsHolder!=null)
		return childsHolder;
		else {
			childsHolder = new ChildsHolder(this);
			return childsHolder;
		}
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
		return new BoxBody(stage,null).setPropertySet(set).setElementEvent(elementEvent);
	}
	
	@Override
	public void setDrawable(Drawable drawable) {
		removeAnimation();
		super.setDrawable(drawable);
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
	    /*
	    if(propertySet.getString("type").equals("UI"))
	        stage.ui(batch);
	    else stage.body(batch);
	    */
		super.draw(batch, parentAlpha);
		if(animation!=null)
			super.setDrawable(animation.getKeyFrame(animationTime,true));
		update();
		if(animation!=null)
			animationTime+=Gdx.graphics.getDeltaTime();
	}
	
	@Override
	public void removeAnimation() {
		animation = null;
	}
	
	@Override
	public void setAnimation(String name) {
		stage.setAnimation(this,name);
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
	public void setAnimation(Animation animation) {
		this.animation = animation;
		animationTime = 0;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}

}