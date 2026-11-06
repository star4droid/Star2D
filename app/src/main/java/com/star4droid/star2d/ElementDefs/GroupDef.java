package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.GroupItem;
import com.star4droid.template.Items.StageImp;

/** An empty container element: it draws nothing, its children follow it (move, rotate, hide, destroy). */
public class GroupDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE="GROUP";
	public String type="STATIC";
	public String parentName = "",name="", Script="";
	public boolean Visible = true;
	/** Draws the descendants by height (lower on the screen = in front), like top-down games need. */
	public boolean Y_Sort = false;
	public float x=0,y=0,z=0,width=100,height=100,rotation=0;
	
	public GroupDef(){}
	
	public GroupItem build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("GroupDef error : set name to the item..!!");
		if(parentName!=null && parentName.equals(""))
		    parentName = name;
		return new GroupItem(stage).setElementEvent(elementEvents).setDef(this);
	}
	
	public GroupDef getClone(String newName){
	    GroupDef clone = new GroupDef();
	    clone.elementEvents = ElementEventAdapter.copyOf(elementEvents);
	    clone.type = type;
        clone.parentName = (parentName!=null&&parentName.equals("")) ? parentName : name;
        clone.Script = this.Script;
        clone.Visible = this.Visible;
        clone.Y_Sort = this.Y_Sort;
        clone.x = this.x;
        clone.y = this.y;
        clone.z = this.z;
        clone.width = this.width;
        clone.height = this.height;
        clone.rotation = this.rotation;
        clone.Version = this.Version;
		clone.Pivot = this.Pivot;
		clone.Pivot_X = this.Pivot_X;
		clone.Pivot_Y = this.Pivot_Y;
        clone.name = newName;
        return clone;
	}
	
}
