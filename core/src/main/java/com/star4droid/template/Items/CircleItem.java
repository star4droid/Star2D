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
import com.star4droid.star2d.ElementDefs.CircleDef;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.Utils;

public class CircleItem extends Image implements PlayerItem {
	StageImp stage;
	ElementEvent elementEvent;
	CircleDef circleDef;
	float circleY=0;
	float[] offset = new float[]{0,0};
	Body body;
	String tint = "#FFFFFF";
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
	
	public CircleItem setDef(CircleDef def){
		circleDef = def;
		if(body!=null) {
		    body.getWorld().destroyBody(body);
		    body = null;
		}
		setup();
		return this;
	}
	
	private void setup(){
		int rx = (int) circleDef.tileX,
		    ry = (int) circleDef.tileY;
		boolean UI = circleDef.type.equals("UI");
		float width = circleDef.radius*2,
		     height = circleDef.radius*2;
		float x = circleDef.x ,
		y = circleDef.y;
		String imgPath=circleDef.image;
		
		setDrawable(Utils.getDrawable(Utils.internal("images/logo.png")));
		stage.setImage(this,circleDef.image);
		setSize((UI ? 1 : StageImp.WORLD_SCALE) * width,(UI ? 1 : StageImp.WORLD_SCALE) * height);
		setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		if(!circleDef.Tint.equals(tint)){
		    setColor(circleDef.getColor(circleDef.Tint));
		    tint = circleDef.Tint;
		}
		setZIndex((int) circleDef.z);
		setRotation(-circleDef.rotation);
		setScale(circleDef.Scale_X,circleDef.Scale_Y);
		setVisible(circleDef.Visible);
		setName(circleDef.name);
		if(!circleDef.type.equals("UI")){
			String bt= String.valueOf(circleDef.type.charAt(0)).toUpperCase();
			BodyDef.BodyType type = bt.equals("K")?BodyDef.BodyType.KinematicBody:(bt.equals("S")?BodyDef.BodyType.StaticBody:BodyDef.BodyType.DynamicBody);
			//creating the body
			offset[0] = circleDef.ColliderX *-1;
			offset[1] = circleDef.ColliderY *-1;
			if(body==null){
    			BodyDef def = new BodyDef();
    			def.type = type;
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
			shape.setRadius(StageImp.WORLD_SCALE * circleDef.Collider_Radius);
			FixtureDef fx = new FixtureDef();
			fx.shape = shape;
			fx.friction=circleDef.friction;
			fx.density=circleDef.density;
			fx.restitution=circleDef.restitution;
			fx.isSensor = circleDef.isSensor;
			body.createFixture(fx).setUserData(this);
			body.setFixedRotation(circleDef.Fixed_Rotation);
			body.setActive(circleDef.Active);
			body.setBullet(circleDef.Bullet);
			body.setGravityScale(circleDef.Gravity_Scale);
			// TODO : Need to fix (check the solution)
			Vector2 pos = new Vector2((offset[0]+x+(width/2)),(offset[1]+y+(height/2)));
			pos.x = StageImp.WORLD_SCALE * pos.x;
			pos.y = StageImp.WORLD_SCALE * pos.y;
			body.setTransform(pos ,(float)Math.toRadians(-circleDef.rotation));
			
		}
		//if it's not added to stage ....
		if(getStage()==null)
		    stage.addActor(this);
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
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
		if(!circleDef.type.equals("UI"))
		    setOrigin(getWidth()*0.5f,getHeight()*0.5f);
	}
	
	@Override
	public void update() {
		if(body!=null){
		    offset[0] = StageImp.WORLD_SCALE * circleDef.ColliderX *-1;
			offset[1] = StageImp.WORLD_SCALE * circleDef.ColliderY*-1;
			float x = body.getPosition().x,
				y = body.getPosition().y;
			setPosition(x - offset[0] - getWidth()*0.5f,
						y - offset[1] - getHeight()*0.5f);
			setRotation((float)Math.toDegrees(body.getAngle()));
		}
		if(!circleDef.Tint.equals(tint)){
		    setColor(circleDef.getColor(circleDef.Tint));
		    tint = circleDef.Tint;
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
	
	com.star4droid.template.Utils.ItemScript itemScript;
	@Override
	public void setScript(com.star4droid.template.Utils.ItemScript script){
	    this.itemScript = script;
	}
	
	@Override
	public <T extends com.star4droid.template.Utils.ItemScript> T getScript(){
	    return (T) itemScript;
	}

	@Override
	public PlayerItem getClone(String newName) {
		CircleDef newDef = circleDef.getClone(newName);
	    CircleItem item = new CircleItem(stage,null).setElementEvent(elementEvent).setDef(newDef);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
			} catch(Exception ex){}
		}
		return item;
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
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return circleDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}