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
import com.star4droid.star2d.ElementDefs.TextDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

public class TextItem extends Label implements PlayerItem {
	StageImp stage;
	float textY=0;
	ElementEvent elementEvent;
	TextDef textDef;
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
	
	public static TextItem create(StageImp stageImp,TextDef textDef,ElementEvent elementEvent){
		BitmapFont font = new BitmapFont(Gdx.files.internal("files/default.fnt"));
		LabelStyle labelStyle = new LabelStyle(font, Color.GOLD);
		return new TextItem(textDef.Text,labelStyle,stageImp).setElementEvent(elementEvent).setDef(textDef);
	}
	
	public TextItem setDef(TextDef textDef){
	    this.textDef = textDef;
	    setup();
	    return this;
	}
	
	@Override
	public void update() {
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
		TextDef newDef = textDef.getClone(newName);
	    PlayerItem item = create(stage,textDef,elementEvent);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
			} catch(Exception ex){}
		}
		return item;
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
		if(textDef==null) return;
		setName(textDef.name);
		boolean UI = textDef.type.equals("UI");
		float width = textDef.width,
    		height = textDef.height,
    		x = textDef.x,
    		y = textDef.y;
		setSize((UI ? 1 : StageImp.WORLD_SCALE) * width,(UI ? 1 : StageImp.WORLD_SCALE) * height);
		setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		setZIndex((int) textDef.z);
		setRotation(-textDef.rotation);
		setVisible(textDef.Visible);
		//setText(propertySet.get("Text").toString());
		//Utils.showMessage(getContext(),propertySet.get("Text").toString());
		font = textDef.font;
		if(!font.equals("")){
			getStyle().font = stage.getFont(stage.project.getPath()+"/"+font);
		}
		getStyle().fontColor = textDef.getColor(textDef.Text_Color);
		setStyle(getStyle());
		setFontScale((UI ? 1 : StageImp.WORLD_SCALE) * textDef.Font_Scale);
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
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return textDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}