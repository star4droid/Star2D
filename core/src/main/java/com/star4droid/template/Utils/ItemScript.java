package com.star4droid.template.Utils;

import box2dLight.Light;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.template.Items.StageImp;
import com.badlogic.gdx.physics.box2d.Joint;

public class ItemScript {
	public PlayerItem playerItem;
	public Body body;
	public Actor actor;
	public StageImp stage;
	
	public ItemScript(){}
	
	public ItemScript(PlayerItem item){
	    playerItem = item;
	    try {
	        body = item.getBody();
		    actor = item.getActor();
		} catch(Exception e){}
	}
	
	public ItemScript(PlayerItem item,StageImp stage){
		playerItem = item;
		body = item.getBody();
		actor = item.getActor();
		this.stage = stage;
	}
	
	public StageImp getStage(){
	    return stage;
	}
	
	public final PlayerItem findItem(String name){
	    if(stage!=null)
    	    try {
    	        return stage.findItem(name);
    	    } catch(Exception ex){}
	    return null;
	}
	
	public final Joint getJoint(String name){
		return stage.getJoint(name);
	}
	
	public final Light findLight(String name){
		return (stage!=null)?stage.findLight(name):null;
	}
	
	public final void setImage(String image){
	    stage.setImage(playerItem,image);
	}
	
	public final ItemScript setStage(StageImp stage){
	    this.stage = stage;
	    return this;
	}
	
	public final ItemScript setItem(PlayerItem item){
		playerItem=item;
		body = item.getBody();
		return this;
	}
	
	public void onClick(){
		
	}
	public void onTouchStart(InputEvent event){
		
	}
	public void onTouchEnd(InputEvent event){
		
	}
	public void onBodyCreated(){
		
	}
	public void onBodyUpdate(){
		
	}
	public void onCollisionBegin(PlayerItem other){
		
	}
	public void onCollisionEnd(PlayerItem other){
		
	}
	
	public final PlayerItem getPlayerItem(){
		return playerItem;
	}
	
	public final void click(){
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onClick(playerItem);
		onClick();
	}
	
	public final void touchStart(InputEvent inputEvent){
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onTouchStart(playerItem,inputEvent);
		onTouchStart(inputEvent);
	}
	
	public final void touchEnd(InputEvent inputEvent){
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onTouchEnd(playerItem,inputEvent);
		onTouchEnd(inputEvent);
	}
	
	public void bodyCreated(){
	    onBodyCreated();
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onBodyCreated(playerItem);
	}
	public final void bodyUpdate(){
	    onBodyUpdate();
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onBodyUpdate(playerItem);
	}
	public final void collisionBegin(PlayerItem other){
	    onCollisionBegin(other);
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onCollisionBegin(playerItem,other);
	}
	public final void collisionEnd(PlayerItem other){
	    onCollisionEnd(other);
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onCollisionEnd(playerItem,other);
	}
}