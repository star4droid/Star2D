package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
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
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.template.Utils.Utils;

public class Joystick extends Touchpad implements PlayerItem {
	StageImp stage;
	float joystickY=0;
	ElementEvent elementEvent;
	PropertySet<String,Object> propertySet;
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
		if(propertySet==null) return;
		float width = propertySet.getFloat("width"),
		height = propertySet.getFloat("height"),
		x = propertySet.getFloat("x"),
		y = propertySet.getFloat("y");
		setPosition(x,stage.getViewport().getWorldHeight()-getHeight()-y);
		setZIndex(propertySet.getInt("z"));
		//setRotation(propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
		setSize(width,height);
		setName(propertySet.getString("name"));
		if(getStage()==null)
		    stage.addActor(this);
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
		return create(stageImp,stageImp.getAssets().contains(btn)?stageImp.getAssets().get(btn):Utils.getDrawable(Utils.internal("images/joybtn.png")),
								stageImp.getAssets().contains(btn)?stageImp.getAssets().get(btn):Utils.getDrawable(Utils.internal("images/joyback.png")));
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
	
	public Joystick setPropertySet(PropertySet<String,Object> set){
		propertySet = set;
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
	    if(propertySet!=null&&propertySet.getString("do update").equals("true")){
		    setPropertySet(propertySet);
		    propertySet.remove("do update");
		}
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
		return Joystick.create(stage,propertySet.getString("Button Image"),propertySet.getString("Pad Image"))
				.setElementEvent(elementEvent)
				.setPropertySet(set);
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
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}