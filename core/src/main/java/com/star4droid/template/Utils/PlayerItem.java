package com.star4droid.template.Utils;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.Color;
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
		try {
		    if(getActor().getName()!=null)
		        return getActor().getName();
		    java.lang.reflect.Field defName = getProperties().getClass().getDeclaredField("name");
            defName.setAccessible(true);
            Object nameV = defName.get(getProperties());
            return nameV != null ? nameV.toString() : "";
		} catch(Error | Exception ex){}
		return "";
	};
	default public void setTint(Color color){
	    getActor().setColor(color);
	}
	default public void setTint(String hexColor){
	    getActor().setColor(Color.valueOf(hexColor));
	}
	default public String getParentName() {
        String actorN = getActor().getName();
        if(actorN==null)
            actorN = "";
        try {
            java.lang.reflect.Field field = getProperties().getClass().getDeclaredField("parentName");
            field.setAccessible(true);
            Object value = field.get(getProperties());
            String fValue = value != null ? value.toString() : null;
            if(value == null || value.equals("")){
                java.lang.reflect.Field defName = getProperties().getClass().getDeclaredField("name");
                defName.setAccessible(true);
                Object nameV = defName.get(getProperties());
                return nameV != null ? nameV.toString() : actorN;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            com.badlogic.gdx.Gdx.files.external("logs/reflection.txt").writeString("Error : "+Utils.getStackTraceString(e),false);
        }
        return actorN;
    }
	default public PlayerItem getChild(String child){
		ChildsHolder holder= getChildsHolder().getChild(child);
		if(holder!=null) return holder.getPlayerItem();
		return null;
	}
	public void setScript(ItemScript script);
	public <T extends ItemScript> T getScript();
	public ChildsHolder getChildsHolder();
	default public void setParent(PlayerItem item){
		getChildsHolder().setParent(item.getChildsHolder());
	}
	default public void addChild(PlayerItem item){
		getChildsHolder().addChild(item);
	}
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties();
	public PlayerItem getClone(String newName);
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
	default public float getActorHeight(){
	    return ((Actor)this).getHeight();
	}
	default public float getActorWidth(){
	    return ((Actor)this).getWidth();
	}
	default public void setActorX(float x){
	    getActor().setX(x);
	}
	default public void setActorY(float y){
	    getActor().setX(y);
	}
	default public float angleToPoint(float x,float y){
	    float cx1 = getBodyX() + getActor().getWidth()*0.5f;
	    float cy1 = getBodyY() + getActor().getHeight()*0.5f;
	    return (float)Math.atan2(cy1 - y, cx1 - x);
	}
	default public float angleToPointDegrees(float x,float y){
	    return (float)Math.toDegrees(angleToPoint(x,y));
	}
	default public float angleTo(PlayerItem item){
	    float cx1 = getBodyX() + getActor().getWidth()*0.5f;
	    float cx2 = item.getBodyX() + item.getActor().getWidth()*0.5f;
	    float cy1 = getBodyY() + getActor().getHeight()*0.5f;
	    float cy2 = item.getBodyY() + item.getActor().getHeight()*0.5f;
	    return (float)Math.atan2(cy1 - cy2, cx1 - cx2);
	}
	default public float angleDegreesTo(PlayerItem item){
	    return (float)Math.toDegrees(angleTo(item));
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