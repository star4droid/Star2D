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
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.ElementDefs.CameraDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

public class CameraItem extends Actor implements PlayerItem {
	StageImp stage;
	ElementEvent elementEvent;
	CameraDef cameraDef;
	ChildsHolder childsHolder = new ChildsHolder(this);
	
	public CameraItem(StageImp stageImp){
		super();
		//setWrap(true);
		this.stage = stageImp;
	}
	
	public static CameraItem create(StageImp stageImp,CameraDef camDef,ElementEvent elementEvent){
		return new CameraItem(stageImp).setElementEvent(elementEvent).setDef(camDef);
	}
	
	public CameraItem setDef(CameraDef camDef){
	    this.cameraDef = camDef;
	    setup();
	    return this;
	}
	
	public CameraDef getCameraDef(){
	    return cameraDef;
	}
	
	@Override
	public void update() {
		if(getScript()!=null)
			getScript().bodyUpdate();
		else if(elementEvent!=null) elementEvent.onBodyUpdate(this);
	}
	
	@Override
	public void setItemText(String text) {}
	
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
		CameraDef newDef = cameraDef.getClone(newName);
	    PlayerItem item = create(stage,cameraDef,elementEvent);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
			} catch(Exception ex){}
		}
		return item;
	}
	
	public CameraItem setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	private void setup(){
		if(cameraDef==null) return;
		setName(cameraDef.name);
		float x = StageImp.WORLD_SCALE * cameraDef.x,
    		 y = StageImp.WORLD_SCALE * cameraDef.y;
		setPosition(x,y);
		setZIndex((int) cameraDef.z);
		setRotation(-cameraDef.rotation);
		//setText(propertySet.get("Text").toString());
		//Utils.showMessage(getContext(),propertySet.get("Text").toString());
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
		if(cameraDef.Active){
		    stage.setCamera(this);
		}
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		update();
	}
	
	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return cameraDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}