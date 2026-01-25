package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.star4droid.star2d.ElementDefs.CustomDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import java.util.ArrayList;
import com.star4droid.template.Utils.Utils;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.Texture;

public class CustomBody extends Image implements PlayerItem {
	StageImp stage;
	float boxY=0,animationTime=0;
	int tileX=1,tileY=1;
	ElementEvent elementEvent;
	CustomDef customDef;
	String tint = "#FFFFFF";
	float[] offset=new float[]{0,0};
	Body body;
	ChildsHolder childsHolder = new ChildsHolder(this);
	Animation<Drawable> animation;
	
	public CustomBody(StageImp s,Drawable drawable){
		super(drawable);
		stage = s;
		setY(0);
		addListener(new InputListener(){
			@Override
			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchEnd(event);
				else if(elementEvent!=null) elementEvent.onTouchEnd(CustomBody.this,event);
			}
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchStart(event);
				else if(elementEvent!=null) elementEvent.onTouchStart(CustomBody.this,event);
				return true;
			}
		});
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(getScript()!=null)
					getScript().click();
				else if(elementEvent!=null) elementEvent.onClick(CustomBody.this);
			}
		});
	}
	
	public CustomBody setDef(CustomDef def){
		customDef = def;
		createBody();
		return this;
	}
	
	public CustomBody setElementEvent(ElementEvent event){
		elementEvent = event;
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
		if(!customDef.type.equals("UI"))
		    setOrigin(getWidth()*0.5f,getHeight()*0.5f);
	}
	
	private void createBody(){
		int rx = (int) customDef.tileX,
			ry = (int) customDef.tileY;
		boolean UI = customDef.type.equals("UI");
		float width = customDef.width,
			height = customDef.height;
		setSize((UI ? 1 : StageImp.WORLD_SCALE) * width, (UI ? 1 : StageImp.WORLD_SCALE) * height);
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		float x = customDef.x,
		y = customDef.y;
		tileX = Math.max((int) customDef.tileX,1);
		tileY = Math.max((int) customDef.tileY,1);
		if(!stage.setImage(this,customDef.image)){
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
		
		setName(customDef.name);
		setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		if(!customDef.getColor(customDef.Tint).equals(tint)){
		    setColor(customDef.getColor(customDef.Tint));
		    tint = customDef.Tint;
		}
		setZIndex((int) customDef.z);
		setRotation(-customDef.rotation);
		setScale(customDef.Scale_X,customDef.Scale_Y);
		setVisible(customDef.Visible);
		if(!customDef.type.equals("UI")){
			String bt= String.valueOf(customDef.type.charAt(0)).toUpperCase();
			BodyDef.BodyType type = bt.equals("K")?BodyDef.BodyType.KinematicBody:(bt.equals("S")?BodyDef.BodyType.StaticBody:BodyDef.BodyType.DynamicBody);
			//creating the body
			if(body==null){
    			BodyDef def = new BodyDef();
    			def.type = type;
    			offset[0] = 0;
    			offset[1] = 0;
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
			ChainShape shape = new ChainShape();
			ArrayList<Vector2> array = new ArrayList<>();
			if(!customDef.Points.equals("")){
                String pointsStr = customDef.Points;
                for (String point : pointsStr.split("-")) {
                    String[] coords = point.split(",");
                    if (coords.length == 2) {
        				try {
                        	float px = Utils.getFloat(coords[0]) * width - width*0.5f;
                        	float py = (1 - Utils.getFloat(coords[1])) * height - height*0.5f; // Flip Y-axis
                        	array.add(new Vector2((UI ? 1 : StageImp.WORLD_SCALE) * px,(UI ? 1 : StageImp.WORLD_SCALE) *  py));
        				} catch(Exception e){}
                    }
                }
    			Vector2[] points = new Vector2[array.size()];
    			int po = 0;
    			for(Vector2 vec:array){
    			    points[po] = vec;
    			    po++;
    			}
    			shape.createLoop(points);
			}
			FixtureDef fx = new FixtureDef();
			fx.shape = shape;
			fx.friction=customDef.friction;
			fx.density=customDef.density;
			fx.restitution=customDef.restitution;
			fx.isSensor = customDef.isSensor;
			body.createFixture(fx).setUserData(this);
			body.setFixedRotation(customDef.Fixed_Rotation);
			body.setActive(customDef.Active);
			body.setBullet(customDef.Bullet);
			body.setGravityScale(customDef.Gravity_Scale);
			Vector2 pos = new Vector2((offset[0]+x+(width*0.5f)),(offset[1]+y+(height*0.5f)));
			pos.x = (UI ? 1 : StageImp.WORLD_SCALE) * pos.x;
			pos.y = (UI ? 1 : StageImp.WORLD_SCALE) * pos.y;
			body.setTransform(pos,(float)Math.toRadians(-customDef.rotation));
			
		}
		if(getStage()==null)
		    stage.addActor(this);
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
	}
	
	@Override
	public void update() {
		if(body!=null){
		    offset[0] = 0;
			offset[1] = 0;
			float x = body.getPosition().x,
				y = body.getPosition().y;
			setPosition(x - offset[0] - getWidth()*0.5f,
					y - offset[1] - getHeight()*0.5f);
			setRotation((float)Math.toDegrees(body.getAngle()));
		}
		if(!customDef.Tint.equals(tint)){
		    setColor(customDef.getColor(customDef.Tint));
		    tint = customDef.Tint;
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
	public boolean setZIndex(int z){
	    boolean b = true;
	    try {
	        b = super.setZIndex(z);
	    } catch(Exception e){}
	    if(stage!=null) stage.updateActors();
	    return b;
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
	    CustomDef newDef = customDef.getClone(newName);
		CustomBody body = new CustomBody(stage,null).setElementEvent(elementEvent).setDef(newDef);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(body));
				script.setItem(body).setStage(stage);
				body.setScript(script);
			} catch(Exception ex){}
		}
		return body;
	}
	
	@Override
	public void setDrawable(Drawable drawable) {
		removeAnimation();
		super.setDrawable(drawable);
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
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
	public void setAnimation(Animation animation) {
		this.animation = animation;
		animationTime = 0;
	}
	
	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return customDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}

}