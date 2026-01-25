package com.star4droid.star2d.ElementDefs;

public abstract class ItemDef {
    public String Version = "";
    public float getZF() {
        if (this instanceof BoxDef)
            return ((BoxDef) this).z;
        if (this instanceof CircleDef)
            return ((CircleDef) this).z;
        if (this instanceof CustomDef)
            return ((CustomDef) this).z;
        if (this instanceof JoyStickDef)
            return ((JoyStickDef) this).z;
        if (this instanceof ParticleDef)
            return ((ParticleDef) this).z;
        if (this instanceof ProgressDef)
            return ((ProgressDef) this).z;
        if (this instanceof TextDef)
            return ((TextDef) this).z;
        
        return 0; // default if no match
    }
    
    public int getZ(){
        return (int)getZF();
    }
    
    public String getCollision(){
                if (this instanceof BoxDef)
            return ((BoxDef) this).Collision;
        if (this instanceof CircleDef)
            return ((CircleDef) this).Collision;
        if (this instanceof CustomDef)
            return ((CustomDef) this).Collision;
        return "";
    }
    
    public String getType(){
        if (this instanceof BoxDef)
            return ((BoxDef) this).type;
        if (this instanceof CircleDef)
            return ((CircleDef) this).type;
        if (this instanceof CustomDef)
            return ((CustomDef) this).type;
        if (this instanceof JoyStickDef)
            return ((JoyStickDef) this).type;
        if (this instanceof ParticleDef)
            return ((ParticleDef) this).type;
        if (this instanceof ProgressDef)
            return ((ProgressDef) this).type;
        if (this instanceof TextDef)
            return ((TextDef) this).type;
        return "";
    }
    
    public com.badlogic.gdx.graphics.Color getColor(String color){
	    try {
    	    return com.badlogic.gdx.graphics.Color.valueOf(color);
	    } catch(Exception ex){}
	    return com.badlogic.gdx.graphics.Color.WHITE;
	}
}