package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.PropertySet;

public class TextItem extends Label implements PlayerItem {
	StageImp stage;
	float textY=0;
	ElementEvent elementEvent;
	PropertySet<String,Object> propertySet;
	String font = "";
	ChildsHolder childsHolder = new ChildsHolder(this);
	
	public TextItem(CharSequence c,LabelStyle style,StageImp stageImp){
		super(c,style);
		//setWrap(true);
		this.stage = stageImp;
		setY(0);
		setWrap(true);
		addListener(new InputListener(){
			@Override
			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchEnd(event);
				else if(elementEvent!=null) elementEvent.onTouchEnd(TextItem.this,event);
			}
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchStart(event);
				else if(elementEvent!=null) elementEvent.onTouchStart(TextItem.this,event);
				return true;
			}
		});
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(getScript()!=null)
					getScript().click();
				else if(elementEvent!=null) elementEvent.onClick(TextItem.this);
			}
		});
	}
	
	public static TextItem create(StageImp stageImp,PropertySet<String,Object> propertySet,ElementEvent elementEvent){
		BitmapFont font = new BitmapFont(Gdx.files.internal("files/default.fnt"));
		LabelStyle labelStyle = new LabelStyle(font, Color.GOLD);
		return new TextItem(propertySet.getString("Text"),labelStyle,stageImp).setPropertySet(propertySet).setElementEvent(elementEvent);
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
	public void setItemText(String text) {
		setText(text);
	}
	
	@Override
	public Body getBody() {
	    return null;
	}
	
	/*
	@Override
	public boolean setZIndex(int z){
	    boolean b = true;
	    try {
	        b = super.setZIndex(z);
	    } catch(Exception e){}
	    if(stage!=null) stage.updateActors();
	    return b;
	}
	*/

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
	    return create(stage,set,elementEvent);
	}
	
	public TextItem setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		//if(getStage()!=null) setY(textY);
		//setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		// auto text size
		// capHeight * scale =  height 
		//  scale = height / capHeight
		// capHeight*x = height => scale = height/capH
		//setFontScale(getHeight()/getStyle().font.getCapHeight());
		
	}
	
	public TextItem setPropertySet(PropertySet<String,Object> set){
		propertySet = set;
		setup();
		return this;
	}
	
	@Override
	public void setY(float y) {
		super.setY(y);//stage.getViewport().getWorldHeight()-getHeight()-y);
		textY = y;
	}

	@Override
	public void setPosition(float x, float y) {
		super.setX(x);
		setY(y);
	}
	
	private void setup(){
		if(propertySet==null) return;
		setName(propertySet.getString("name"));
		float width = propertySet.getFloat("width"),
		height = propertySet.getFloat("height"),
		x = propertySet.getFloat("x"),
		y = propertySet.getFloat("y");
		setSize(width,height);
		setPosition(x,stage.getViewport().getWorldHeight()-getHeight()-y);
		setZIndex(propertySet.getInt("z"));
		setRotation(-propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
		//setText(propertySet.get("Text").toString());
		//Utils.showMessage(getContext(),propertySet.get("Text").toString());
		font = propertySet.getString("font");
		if(!font.equals("")){
			getStyle().font = stage.getFont(stage.project.getPath()+"/"+font);
		}
		getStyle().fontColor = propertySet.getColor("Text Color");
		setStyle(getStyle());
		if(propertySet.containsKey("Font Scale"))
			setFontScale(propertySet.getFloat("Font Scale"));
		if(getStage()==null)
		    stage.addActor(this);
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
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