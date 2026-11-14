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
            com.star4droid.template.Utils.GameLog.write("reflection", "Error : "+Utils.getStackTraceString(e));
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
	/** Makes {@code item} the parent. The element keeps its current world position and then follows the parent. */
	default public void setParent(PlayerItem item){
		if(item == null) getChildsHolder().detach();
		else getChildsHolder().setParent(item.getChildsHolder());
	}
	/** Leaves the parent without moving: the element keeps its place and stops following. */
	default public void detachFromParent(){
		getChildsHolder().detach();
	}
	/** The parent element, or null. */
	default public PlayerItem getParentItem(){
		ChildsHolder p = getChildsHolder().getParentHolder();
		return p == null ? null : p.getPlayerItem();
	}
	/** Distance (world units) between the body position and the center of the actor. {0,0} unless the element has a collider offset. */
	default public float[] getBodyOffset(){ return new float[]{0f, 0f}; }
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
	
	default public void setImage(com.badlogic.gdx.scenes.scene2d.utils.Drawable drawable){
		if(this instanceof Image)
			((Image)this).setDrawable(drawable);
	}
	
	default public void setImage(Texture texture){
		if(this instanceof Image)
			((Image)this).setDrawable(Utils.getDrawable(texture));
	}
	
	default public void setAnimation(String name){}
	default public void setAnimation(Animation animation){}
	default public void removeAnimation(){}
	public ElementEvent getElementEvents();
	// ---- object variables (Variables section of the element's properties, see ObjectVars) ----
	/** Value of an object variable of this element (null when it has none with that name). */
	default public Object getVar(String name){ return ObjectVars.get(this,name); }
	default public float getVarNumber(String name){ return ObjectVars.getFloat(this,name); }
	default public String getVarText(String name){ return ObjectVars.getString(this,name); }
	default public boolean getVarBool(String name){ return ObjectVars.getBool(this,name); }
	/** Sets an object variable of this element (numbers are converted to its type). */
	default public void setVar(String name,Object value){ ObjectVars.set(this,name,value); }
	// ---- behaviors at runtime (ids = class names: "PlatformerPlayer", "Shooter"...) ----
	/** Attaches a behavior object to this element (starts on the next frame). Returns it. */
	default public com.star4droid.template.behaviors.Behavior addBehavior(com.star4droid.template.behaviors.Behavior b){
	    return b.attach(this,com.star4droid.template.Items.StageImp.of(this));
	}
	/** Attaches a new behavior by id with its default parameters (null if the id is unknown or it is already attached). */
	default public com.star4droid.template.behaviors.Behavior addBehavior(String id){
	    if(hasBehavior(id)) return getBehavior(id);
	    com.star4droid.template.behaviors.Behavior b = com.star4droid.template.behaviors.BehaviorRegistry.create(id);
	    return b==null?null:addBehavior(b);
	}
	default public void removeBehavior(String id){
	    ItemScript s = getScript();
	    if(s!=null) s.removeBehavior(id);
	}
	default public boolean hasBehavior(String id){
	    return getBehavior(id)!=null;
	}
	default public com.star4droid.template.behaviors.Behavior getBehavior(String id){
	    ItemScript s = getScript();
	    return s==null?null:s.getBehavior(id);
	}
	/** Sets a public field of a behavior by name (numbers are converted, element fields accept an element name): setBehaviorParam("Shooter","fireRate",8). */
	default public boolean setBehaviorParam(String id,String field,Object value){
	    com.star4droid.template.behaviors.Behavior b = getBehavior(id);
	    return b!=null && b.setParam(field,value);
	}
	default public Object getBehaviorParam(String id,String field){
	    com.star4droid.template.behaviors.Behavior b = getBehavior(id);
	    return b==null?null:b.getParam(field);
	}
	/** Turns a behavior of this element on/off by class name: item.setBehaviorEnabled("Car", true). Enabling one disables the others of its exclusive group (movement...). */
	default public void setBehaviorEnabled(String behavior,boolean enabled){
	    ItemScript s = getScript();
	    if(s!=null) s.setBehaviorEnabled(behavior,enabled);
	}
	default public boolean isBehaviorEnabled(String behavior){
	    ItemScript s = getScript();
	    return s!=null && s.isBehaviorEnabled(behavior);
	}
	default public void destroy(){
	    try { if(getScript()!=null) ((ItemScript) getScript()).destroyBehaviors(); } catch(Throwable ignored){}
	    ChildsHolder holder = getChildsHolder();
	    if(holder != null){
	        if(holder.destroyed) return;
	        holder.destroyed = true;
	        // children go first, with their own children
	        java.util.ArrayList<PlayerItem> kids = new java.util.ArrayList<>(holder.childs);
	        for(PlayerItem kid : kids){
	            try { if(kid != null && kid != this) kid.destroy(); } catch(Throwable ignored){}
	        }
	        holder.childs.clear();
	        holder.detach();
	    }
	    if(getActor()!=null)
	        getActor().remove();
	    if(getBody()!=null)
	        getBody().getWorld().destroyBody(getBody());
	}
}