package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.ParticleItem;
import com.star4droid.template.Items.StageImp;
import java.lang.reflect.Field;

public class ParticleDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="PARTICLE";
	public String name="",parentName ="",Script="",type="UI",file="";
	public boolean Visible=true,Loop=true;
	public float x=0,y=0,z=0,rotation=0,Duration=-1,
				Scale_X=1,Scale_Y=1;
	
	public ParticleDef(){
		
	}
	
	public ParticleItem build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("ParticleDef error : set name to the item..!!");
		if(Version.equals(""))
		    throw new RuntimeException("Version of this scene is old, repoen the scene and click run to fix that");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		return new ParticleItem(stage).setElementEvent(elementEvents)
		                    .setDef(this);
	}
	
	public ParticleDef getClone(String newName) {
        ParticleDef clone = new ParticleDef();
        clone.elementEvents = this.elementEvents;
        clone.type = this.type;
        clone.name = newName;
        clone.parentName = (parentName != null && parentName.equals("")) ? parentName : name;
        clone.Script = this.Script;
        clone.file = this.file;
        clone.Visible = this.Visible;
        clone.Loop = this.Loop;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.rotation = this.rotation;
        clone.Duration = this.Duration;
        clone.Scale_X = this.Scale_X;
        clone.Scale_Y = this.Scale_Y;
        return clone;
    }
}