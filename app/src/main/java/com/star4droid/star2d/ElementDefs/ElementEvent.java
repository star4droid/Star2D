package com.star4droid.star2d.ElementDefs;

import android.view.View;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.template.Utils.PlayerItem;

public interface ElementEvent {
	default public String getName(){
		try {
			return ((Actor)this).getName();//PropertySet.getPropertySet((View)this).get("name").toString();
		} catch(Exception ex){
			return "Null";
		}
	}
	public void onClick(PlayerItem current);
	public void onTouchStart(PlayerItem current,InputEvent event);
	public void onTouchEnd(PlayerItem current,InputEvent event);
	public void onBodyCreated(PlayerItem current);
	public void onBodyUpdate(PlayerItem current);
	public void onCollisionBegin(PlayerItem current,PlayerItem body2);
	public void onCollisionEnd(PlayerItem curreny,PlayerItem body2);
}