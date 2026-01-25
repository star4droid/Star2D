package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.star2d.ElementDefs.JoyStickDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.Utils;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class Joystick extends Touchpad implements PlayerItem {
	StageImp stage;
	float joystickY=0;
	ElementEvent elementEvent;
	JoyStickDef joystickDef;
	ChildsHolder childsHolder = new ChildsHolder(this);
	
	public Joystick(TouchpadStyle style,StageImp st){
		super(5,style);
		stage = st;
		setY(0);
		addListener(new InputListener(){
			@Override
			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchEnd(event);
				else if(elementEvent!=null) elementEvent.onTouchEnd(Joystick.this,event);
			}
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchStart(event);
				else if(elementEvent!=null) elementEvent.onTouchStart(Joystick.this,event);
				return true;
			}
		});
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(getScript()!=null)
					getScript().click();
				else if(elementEvent!=null) elementEvent.onClick(Joystick.this);
			}
		});
	}
	
	private void setup(){
		if(joystickDef==null) return;
		boolean UI = joystickDef.type.equals("UI");
		float width = joystickDef.width,
			height = joystickDef.height,
		x = joystickDef.x,
		y = joystickDef.y;
		setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		setZIndex((int) joystickDef.z);
		setVisible(joystickDef.Visible);
		setSize((UI ? 1 : StageImp.WORLD_SCALE) * width,(UI ? 1 : StageImp.WORLD_SCALE) * height);
		setName(joystickDef.name);
		if(getStage()==null)
		    stage.addActor(this);
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
	}
	
	public static Joystick create(StageImp stageImp,Texture button,Texture background){
		TouchpadStyle ts = new TouchpadStyle();
		ts.background = new TextureRegionDrawable(new TextureRegion(background));
		ts.knob = new TextureRegionDrawable(new TextureRegion(button));
		return new Joystick(ts,stageImp);
	}
	
	public static Joystick create(StageImp stageImp,Drawable btn,Drawable back){
		TouchpadStyle ts = new TouchpadStyle();
		ts.background = back;
		ts.knob = btn;
		return new Joystick(ts,stageImp);
	}
	
	public static Joystick create(StageImp stageImp,String btn,String background){
		while(btn.contains("//")) btn = btn.replace("//","/");
		while(background.contains("//")) background = background.replace("//","/");
		FileHandle btnHandle = Gdx.files.absolute(btn),
			bgHandle = Gdx.files.absolute(background);
		//stageImp.debug("\nbtn : "+btn+"\nbg : "+background+"\n");
		
		return create(stageImp,stageImp.getAssets().contains(btn)?new TextureRegionDrawable(new TextureRegion((Texture)stageImp.getAssets().get(btn))) : Utils.getDrawable((btnHandle.exists() && !btnHandle.isDirectory()) ? btnHandle : Utils.internal("images/joybtn.png")),
								stageImp.getAssets().contains(btn)?new TextureRegionDrawable(new TextureRegion((Texture)stageImp.getAssets().get(background))): Utils.getDrawable((bgHandle.exists() && !bgHandle.isDirectory()) ? bgHandle : Utils.internal("images/joyback.png")));
	}
	
	@Override
	public void setY(float y) {
		super.setY(y);//stage.getViewport().getWorldHeight()-getHeight()-y);
		joystickY = y;
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
    
	public Joystick setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	public Joystick setDef(JoyStickDef def){
		joystickDef = def;
		setup();
		return this;
	}
	
	@Override
	public void setPosition(float x, float y) {
		super.setX(x);
		setY(y);
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		//if(getStage()!=null) setY(joystickY);
		//setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		if(getStyle()!=null&&getStyle().knob!=null){
			getStyle().knob.setMinHeight(getHeight()*0.35f);
			getStyle().knob.setMinWidth(getWidth()*0.35f);
		}
	}
	
	@Override
	public void update() {
		if(getScript()!=null)
			getScript().bodyUpdate();
		else if(elementEvent!=null) elementEvent.onBodyUpdate(this);
	}

	@Override
	public Body getBody() {
	    return null;
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
	    JoyStickDef newDef = joystickDef.getClone(newName);
		Joystick joystick = Joystick.create(stage,joystickDef.Button_Image,joystickDef.Pad_Image)
				.setElementEvent(elementEvent).setDef(newDef);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(joystick));
				script.setItem(joystick).setStage(stage);
				joystick.setScript(script);
			} catch(Exception ex){}
		}
		return joystick;
	}
	
	@Override
	public double getPower() {
		// float x = getX();
		// float y = getY();
		// float w = getWidth();
		// float h = getHeight();

		if (getStyle() != null&&getStyle().knob!=null) {
		    //calculate pad x , y (useless)
			// float jx = x + getKnobX() - getStyle().knob.getMinWidth() / 2f;
			// float jy = y + getKnobY() - getStyle().knob.getMinHeight() / 2f;
			
			//distance between center and x,y of pad
			float dst = Vector2.dst2(0,0,getKnobPercentX(),getKnobPercentY());
			return (double)dst;
		}
		return 0;
	}
	
	@Override
	public float getJoyStickX() {
		return getKnobPercentX();
	}
	
	@Override
	public float getJoyStickY() {
		return getKnobPercentY();
	}
	
	@Override
	public double getAngle() {
		return Math.atan2(-getKnobPercentY(),-getKnobPercentX());
	}
	
	
	@Override
	public double getAngleDegrees() {
		return Math.toDegrees(getAngle());
	}
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		update();
	}
	
	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return joystickDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}