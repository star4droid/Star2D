package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
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
import com.star4droid.star2d.ElementDefs.BoxDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.Utils;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.Texture;

public class BoxBody extends Image implements PlayerItem {
	StageImp stage;
	float boxY=0,animationTime=0;
	int tileX=1,tileY=1;
	String tint = "#FFFFFF";
	ElementEvent elementEvent;
	com.star4droid.star2d.ElementDefs.BoxDef boxDef;
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
	
	public BoxBody setDef(com.star4droid.star2d.ElementDefs.BoxDef def){
		boxDef = def;
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
		if(!boxDef.type.equals("UI"))
		    setOrigin(getWidth()*0.5f,getHeight()*0.5f);
	}
	
	private void createBody(){
	    boolean UI = boxDef.type.equals("UI");
		int rx = (int)boxDef.tileX,
			ry = (int)boxDef.tileY;
		float width = boxDef.width,
		    height = boxDef.height;
		setSize((UI ? 1 : StageImp.WORLD_SCALE) * width,(UI ? 1 : StageImp.WORLD_SCALE) * height);
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		float x = boxDef.x,
		    y = boxDef.y;
		tileX = Math.max(rx ,1);
		tileY = Math.max(ry, 1);
		if(!stage.setImage(this,boxDef.image)){
		    setDrawable(Utils.getDrawable(Utils.internal("images/logo.png")));
		}
		
		if(tileX!=1||tileY!=1){
		try {
		    Texture texture = ((TextureRegionDrawable)getDrawable()).getRegion().getTexture();
		    texture.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
		    TextureRegion textureRegion = new TextureRegion(texture);
		    textureRegion.setRegion(0,0,texture.getWidth()*tileX,texture.getHeight()*tileY);
		    setDrawable(new TextureRegionDrawable(textureRegion));
		    //com.badlogic.gdx.Gdx.files.external("logs/box.txt").writeString("\nsetting repeated texture done"+"\n",true);
		    } catch(Exception ex){
		        //com.badlogic.gdx.Gdx.files.external("logs/box.txt").writeString("\nsetting repeated texture error : "+ex.toString()+"\n",true);
		    }
		}
		
		setName(boxDef.name);
		setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		if(!boxDef.Tint.equals(tint)){
		    setColor(boxDef.getColor(boxDef.Tint));
		    tint = boxDef.Tint;
		}
		setZIndex((int) boxDef.z);
		setRotation(-boxDef.rotation);
		setScale(boxDef.Scale_X,boxDef.Scale_Y);
		setVisible(boxDef.Visible);
		if(!boxDef.type.equals("UI")){
			String bt = String.valueOf(boxDef.type.charAt(0)).toUpperCase();
			BodyDef.BodyType type = bt.equals("K")?BodyDef.BodyType.KinematicBody:(bt.equals("S")?BodyDef.BodyType.StaticBody:BodyDef.BodyType.DynamicBody);
			//creating the body
			offset[0] = boxDef.ColliderX*-1;
			offset[1] = boxDef.ColliderY*-1;
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
			PolygonShape shape = new PolygonShape();
			shape.setAsBox( StageImp.WORLD_SCALE * boxDef.Collider_Width *0.5f, StageImp.WORLD_SCALE * boxDef.Collider_Height *0.5f);
			FixtureDef fx = new FixtureDef();
			fx.shape = shape;
			fx.friction= boxDef.friction;
			fx.density= boxDef.density;
			fx.restitution= boxDef.restitution;
			fx.isSensor = boxDef.isSensor;
			body.createFixture(fx).setUserData(this);
			body.setFixedRotation(boxDef.Fixed_Rotation);
			body.setActive(boxDef.Active);
			body.setBullet(boxDef.Bullet);
			body.setGravityScale(boxDef.Gravity_Scale);
			Vector2 pos = new Vector2(offset[0]+x+(width*0.5f),offset[1]+y+(height*0.5f));
			pos.x = StageImp.WORLD_SCALE * pos.x;
			pos.y = StageImp.WORLD_SCALE * pos.y;
			body.setTransform( pos ,(float)Math.toRadians(-boxDef.rotation));
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
		    offset[0] = StageImp.WORLD_SCALE * boxDef.ColliderX*-1;
			offset[1] = StageImp.WORLD_SCALE * boxDef.ColliderY*-1;
			float x = body.getPosition().x,
				y = body.getPosition().y;
			setPosition(x - offset[0] - getWidth()*0.5f,
					y - offset[1] - getHeight()*0.5f);
			setRotation((float)Math.toDegrees(body.getAngle()));
		}
		if(!boxDef.Tint.equals(tint)){
		    setColor(boxDef.getColor(boxDef.Tint));
		    tint = boxDef.Tint;
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
		BoxDef newDef = boxDef.getClone(newName);
		BoxBody body = new BoxBody(stage,null).setElementEvent(elementEvent).setDef(newDef);
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
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return boxDef;
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