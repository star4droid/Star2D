package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.star2d.Helpers.PropertySet;

public interface EditorItem {
	public PropertySet<String,Object> getPropertySet();
	public void update();
	public void setProperties(PropertySet<String,Object> propertySet);
	public String getTypeName();
	public default Body getBody(){
		return null;
	}
}