package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Items.UIHolder;
import com.star4droid.template.ui.UIEvents;

/**
 * An element that shows a .ui file made with the UI Maker (project folder UI/). The inner elements belong to the .ui, not to the scene:
 * the scene only has this one block (name, position, size, z, which file, how it fits). Events of the inner elements are in
 * {@link #uiEvents}.
 */
public class UIHolderDef extends ItemDef {
	public ElementEvent elementEvents;
	/** Events of the inner nodes of the .ui (generated from Events/&lt;scene&gt;/private/&lt;holder&gt;/ui/...). */
	public UIEvents uiEvents;
	public static final String TYPE = "UIHOLDER";
	public String type = "UI";
	public String parentName = "", name = "", Script = "";
	/** File name inside the project's UI folder, with or without ".ui". */
	public String ui = "";
	/** stretch (adapts to the size), contain (keeps the design aspect, letterbox) or cover (keeps it, crops). */
	public String fit = "stretch";
	public boolean Visible = true;
	/** "free": x / y / width / height as typed. "screen": placed in the screen box by the properties below (type UI only). */
	public String layout = "free";
	/** screen layout: topleft, top, topright, left, center, right, bottomleft, bottom, bottomright. */
	public String anchor = "topleft";
	/** screen layout: fill the whole width / height (minus the margins). */
	public boolean Stretch_X = false, Stretch_Y = false;
	/** screen layout: size as percent (0-100) of the screen; 0 = use width / height. */
	public float Width_Percent = 0, Height_Percent = 0;
	/** screen layout: distance from the anchored edges (px). */
	public float Margin_X = 0, Margin_Y = 0;
	public float x = 0, y = 0, z = 100, width = 300, height = 300, rotation = 0;
	
	public UIHolderDef(){}
	
	public UIHolder build(StageImp stage){
		if(name.equals("")) throw new RuntimeException("UIHolderDef error : set name to the item..!!");
		if(parentName != null && parentName.equals(""))
			parentName = name;
		return new UIHolder(stage).setElementEvent(elementEvents).setDef(this);
	}
	
	public UIHolderDef getClone(String newName){
		UIHolderDef clone = new UIHolderDef();
		clone.elementEvents = ElementEventAdapter.copyOf(elementEvents);
		clone.uiEvents = uiEvents;
		clone.type = type;
		clone.parentName = (parentName != null && !parentName.equals("")) ? parentName : name;
		clone.Script = this.Script;
		clone.ui = this.ui;
		clone.fit = this.fit;
		clone.Visible = this.Visible;
		clone.layout = this.layout;
		clone.anchor = this.anchor;
		clone.Stretch_X = this.Stretch_X;
		clone.Stretch_Y = this.Stretch_Y;
		clone.Width_Percent = this.Width_Percent;
		clone.Height_Percent = this.Height_Percent;
		clone.Margin_X = this.Margin_X;
		clone.Margin_Y = this.Margin_Y;
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
