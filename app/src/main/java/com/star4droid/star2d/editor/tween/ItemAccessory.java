package com.star4droid.star2d.editor.tween;

import aurelienribon.tweenengine.TweenAccessor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.items.EditorItem;

public class ItemAccessory implements TweenAccessor<EditorItem> {
	
	public static final int POS_X = 0;
	public static final int POS_Y = 1;
	public static final int WIDTH = 2;
	public static final int HEIGHT = 3;
	public static final int ROTATION = 4;
	public static final int COLLIDER_X = 5;
	public static final int COLLIDER_Y = 6;
	public static final int COLLIDER_WIDTH = 7;
	public static final int COLLIDER_HEIGHT = 8;
	public static final int TINT = 9;
	
	@Override
	public int getValues(EditorItem item, int type, float[] returnValues) {
		switch(type){
			case POS_X:
				returnValues[0] = item.getPropertySet().getFloat("x");
				return 1;
			case POS_Y:
				returnValues[0] = item.getPropertySet().getFloat("y");
				return 1;
			case WIDTH:
				boolean isBoxBody = item.getPropertySet().containsKey("width");
				returnValues[0] = item.getPropertySet().getFloat(isBoxBody ? "width" : "radius");
				return 1;
			case HEIGHT:
			    boolean isBox = item.getPropertySet().containsKey("width");
				returnValues[0] = item.getPropertySet().getFloat(isBox ? "height" : "radius");
				return 1;
			case ROTATION:
				returnValues[0] = item.getPropertySet().getFloat("rotation");
				return 1;
			case COLLIDER_X:
				returnValues[0] = item.getPropertySet().getFloat("ColliderX");
				return 1;
			case COLLIDER_Y:
				returnValues[0] = item.getPropertySet().getFloat("ColliderY");
				return 1;
			case TINT:
				returnValues[0] = item.getPropertySet().getFloat("Tint");
				return 1;
		}
		return 0;
	}

	@Override
	public void setValues(EditorItem item, int type, float[] newValues) {
		switch(type){
			case POS_X:
				item.getPropertySet().put("x",newValues[0]);
				item.update();
				break;
			case POS_Y:
				item.getPropertySet().put("y",newValues[0]);
				item.update();
				break;
			case WIDTH:
				PropertySet prst = item.getPropertySet();
				boolean isBoxBody = prst.containsKey("width");
				boolean isWidthSame = prst.getString(isBoxBody ? "width" : "radius").equals(prst.getFloat(isBoxBody ? "Collider Width" : "Collider Radius"));
				boolean isHeightSame = prst.getString(isBoxBody ? "height" : "radius").equals(prst.getFloat(isBoxBody ? "Collider Height" : "Collider Radius"));
				if(isBoxBody){
					prst.put("width",newValues[0]);
					if(isWidthSame && isHeightSame){
						prst.put("Collider Width",newValues[0]);
					}
				} else {
					prst.put("radius",newValues[0]);
					if(isWidthSame)
						prst.put("Collider Radius",newValues[0]);
				}
				item.update();
				break;
			case HEIGHT:
				PropertySet pst = item.getPropertySet();
				boolean isBox = pst.containsKey("width");
				boolean widthSame = pst.getString(isBox ? "width" : "radius").equals(pst.getFloat(isBox ? "Collider Width" : "Collider Radius"));
				boolean heightSame = pst.getString(isBox ? "height" : "radius").equals(pst.getFloat(isBox ? "Collider Height" : "Collider Radius"));
				if(isBox){
					pst.put("height",newValues[0]);
					if(widthSame && heightSame){
						pst.put("Collider Height",newValues[0]);
					}
				} else {
					pst.put("radius",newValues[0]);
					if(widthSame)
						pst.put("Collider Radius",newValues[0]);
				}
				item.update();
				break;
			case ROTATION:
				item.getPropertySet().put("rotation",newValues[0]);
				item.update();
				break;
			case COLLIDER_X:
			    item.getPropertySet().put("ColliderX",newValues[0]);
				item.update();
			    break;
			case COLLIDER_Y:
			    item.getPropertySet().put("ColliderY",newValues[0]);
				item.update();
			    break;
			case TINT:
			    item.getPropertySet().put("Tint",newValues[0]);
				item.update();
			    break;
		}
	}
	
}