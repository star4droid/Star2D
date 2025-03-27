package com.star4droid.star2d.Adapters;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.EditorActivity;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Items.*;
import android.widget.PopupMenu;
import android.view.MenuItem;
import com.star4droid.star2d.editor.items.LightItem;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;

public class AddLightPopup {
	
	public static void showFor(final EditorActivity activity,final View view,final Editor editor){
    	PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
    	//popupMenu.setForceShowIcon(true);
        popupMenu.getMenu().add(0, 0,0,activity.getString(R.string.point_light)).setIcon(R.drawable.light);
        popupMenu.getMenu().add(0,1,1,activity.getString(R.string.directional_light)).setIcon(R.drawable.light);
        popupMenu.getMenu().add(0,2,2,activity.getString(R.string.cone_light)).setIcon(R.drawable.light);
        popupMenu.getMenu().add(0,4,4,activity.getString(R.string.light_settings)).setIcon(R.drawable.light);
    
        popupMenu.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if(item.getItemId()==4){
							Gdx.app.postRunnable(()->
                            	com.star4droid.star2d.editor.ui.LightSettingsDialog.showFor(new Project(editor.getProject().getPath()),editor.getScene())
							);
                            return true;
                        }
                        com.badlogic.gdx.Gdx.app.postRunnable(()->{
                            LightItem lightItem = new LightItem(editor.getLibgdxEditor());
            				editor.getLibgdxEditor().addActor(lightItem);
            				lightItem.setName(editor.getName(lightItem));
            				lightItem.setDefault(getType(item.getItemId()));
            				lightItem.getPropertySet().put("z",getLastZ(editor));
            				editor.selectView(lightItem);
            				lightItem.update();
        				});
        				//activity.refreshBodies();
                        return true;
                    }
                });
        popupMenu.show();
	}
	
	private static String getType(int pos){
	    if(pos==0)
	        return "point";
	    else if(pos==1)
	        return "directional";
	    else return "cone";
	}
	
	private static float getLastZ(Editor editor){
		return editor.getLastZ();
	}
}