package com.star4droid.star2d.ElementDefs;

public abstract class ItemDef {
    public String Version = "";
    /** Pivot (origin) of the element: center, left, right, top, bottom, topleft, topright, bottomleft, bottomright or custom (Pivot_X / Pivot_Y = 0..1). */
    public String Pivot = "center";
    public float Pivot_X = 0.5f, Pivot_Y = 0.5f;
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
        if (this instanceof GroupDef)
            return ((GroupDef) this).z;
        if (this instanceof UIHolderDef)
            return ((UIHolderDef) this).z;
        if (this instanceof CameraViewDef)
            return ((CameraViewDef) this).z;
        
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
        if (this instanceof GroupDef)
            return ((GroupDef) this).type;
        if (this instanceof UIHolderDef)
            return ((UIHolderDef) this).type;
        if (this instanceof CameraViewDef)
            return ((CameraViewDef) this).type;
        if (this instanceof Object3DDef)
            return ((Object3DDef) this).type;
        if (this instanceof Light3DDef)
            return ((Light3DDef) this).type;
        if (this instanceof Camera3DDef)
            return ((Camera3DDef) this).type;
        return "";
    }
    
    public com.badlogic.gdx.graphics.Color getColor(String color){
	    try {
    	    return com.badlogic.gdx.graphics.Color.valueOf(color);
	    } catch(Exception ex){}
	    return com.badlogic.gdx.graphics.Color.WHITE;
	}
}