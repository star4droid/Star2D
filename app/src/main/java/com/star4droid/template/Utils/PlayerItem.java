package com.star4droid.template.Utils;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Items.ProgressItem;

public interface PlayerItem {
	public void update();
	public Body getBody();
	default public void scaleEffect(float sx,float sy){}
	default public ParticleEffect getParticleEffect(){return null;};
	default public String getName(){
		return getProperties().getString("name");
	};
	default public String getParentName(){
		return getProperties().containsKey("Script")?getProperties().getString("Script"):(getProperties().containsKey("old")?getProperties().getString("old"):getName());
	}
	default public PlayerItem getChild(String child){
		ChildsHolder holder= getChildsHolder().getChild(child);
		if(holder!=null) return holder.getPlayerItem();
		return null;
	}
	default public void setScript(ItemScript script){
		getProperties().setScript(script);
	}
	default public <T extends ItemScript> T getScript(){
	    if(getProperties()==null) return null;
		return (T)getProperties().getScript();
	}
	public ChildsHolder getChildsHolder();
	default public void setParent(PlayerItem item){
		getChildsHolder().setParent(item.getChildsHolder());
	}
	default public void addChild(PlayerItem item){
		getChildsHolder().addChild(item);
	}
	public PropertySet<String,Object> getProperties();
	public Actor getClone(String newName);
	default public void setItemText(String text){};
	default public void setProgress(int progress){
		if(this instanceof ProgressItem)
			((ProgressItem)this).setProgress((float)progress);
	}
	default public int getProgress(){return 0;}
	default public void setMax(int max){}
	default public float getJoyStickX(){return 0;}
	default public float getJoyStickY(){return 0;}
	default public double getAngle(){return 0;}
	default public double getPower(){return 0;}
	default public double getAngleDegrees(){return 0;}
	default public float getBodyX(){return getBody().getPosition().x;}
	default public float getBodyY(){return getBody().getPosition().y;}
	default public float distToPoint(float x,float y){
		float xx= x-this.getBody().getPosition().x;
		float yy= y-this.getBody().getPosition().y;
		return (float)Math.sqrt(xx*xx+yy*yy);
	}
	default public float getActorX(){
	    return ((Actor)this).getX();
	}
	default public float getActorY(){
	    return ((Actor)this).getY();
	}
	default public float distTo(PlayerItem item){
		return this.distToPoint(item.getBody().getPosition().x,item.getBody().getPosition().y);
	}
	
	default public boolean isExsits(String file){
		return (!file.equals(""))&&new java.io.File(file).exists();
	}
	
	default public Actor getActor(){
		return ((Actor)this);
	}
	
	default public void setImage(FileHandle fileHandle){
		if(this instanceof Image)
			((Image)this).setDrawable(Utils.getDrawable(fileHandle));
	}
	
	default public void setImage(Texture texture){
		if(this instanceof Image)
			((Image)this).setDrawable(Utils.getDrawable(texture));
	}
	
	default public void setAnimation(String name){}
	default public void setAnimation(Animation animation){}
	default public void removeAnimation(){}
	public ElementEvent getElementEvents();
	default public void destroy(){
	    if(getActor()!=null)
	        getActor().remove();
	    if(getBody()!=null)
	        getBody().getWorld().destroyBody(getBody());
	}
}