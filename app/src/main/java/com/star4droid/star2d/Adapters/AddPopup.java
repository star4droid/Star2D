package com.star4droid.star2d.Adapters;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.star4droid.star2d.EditorActivity;
import android.widget.PopupMenu;
import android.view.MenuItem;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.*;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;

public class AddPopup {
	
	public static float getLastZ(Editor editor){
		return editor.getLastZ();
	}
	
	public static float getLastZ(LibgdxEditor editor){
		return editor.getActors().size;
	}
	
	public static void showFor(final EditorActivity activity,final View view,final Editor ed){
    	LibgdxEditor editor = ed.getLibgdxEditor();
		PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
    	//popupMenu.setForceShowIcon(true);
        popupMenu.getMenu().add(0, 0,0,activity.getString(R.string.box_body)).setIcon(R.drawable.box);
        popupMenu.getMenu().add(0,1,1,activity.getString(R.string.circle_body)).setIcon(R.drawable.circle);
        popupMenu.getMenu().add(0,2,2,activity.getString(R.string.text_item)).setIcon(R.drawable.txt_icon);
        popupMenu.getMenu().add(0,3,3,activity.getString(R.string.joystick_item)).setIcon(R.drawable.joystick);
        popupMenu.getMenu().add(0,4,4,activity.getString(R.string.progress_item)).setIcon(R.drawable.progress);
        popupMenu.getMenu().add(0,5,5,activity.getString(R.string.custom_body)).setIcon(R.drawable.progress);
		popupMenu.getMenu().add(0,6,6,activity.getString(R.string.particle_effect)).setIcon(R.drawable.grids_icon);
		
        popupMenu.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        com.badlogic.gdx.Gdx.app.postRunnable(()->{
                            if(item.getItemId()==0){
                                BoxItem box = new BoxItem(editor);
                				editor.addActor(box);
                				box.setName(editor.getName(box));
                				box.setDefault();
                				box.getPropertySet().put("z",ed.getLastZ());
                				ed.selectView(box);
                				box.update();
                            } else if(item.getItemId()==1){
                                CircleItem circleBody = new CircleItem(editor);
                				editor.addActor(circleBody);
                			    circleBody.setName(editor.getName(circleBody));
                				circleBody.setDefault();
                				circleBody.getPropertySet().put("z",getLastZ(editor));
                				ed.selectView(circleBody);
    
                				circleBody.update();
                            } else if(item.getItemId()==2){
                                EditorTextItem textItem = new EditorTextItem(editor);
                				editor.addActor(textItem);
                				textItem.setName(editor.getName(textItem));
                				textItem.setDefault();
                				textItem.getPropertySet().put("z",getLastZ(editor));
                				ed.selectView(textItem);
    
                				textItem.update();
                            } else if(item.getItemId()==3){
                                JoyStickItem joyStickItem = new JoyStickItem(editor);
                				editor.addActor(joyStickItem);
                				joyStickItem.setName(editor.getName(joyStickItem));
                				joyStickItem.setDefault();
                				joyStickItem.getPropertySet().put("z",getLastZ(editor));
                				ed.selectView(joyStickItem);
    
                				joyStickItem.update();
                            } else if(item.getItemId()==4){
                                EditorProgressItem progressItem = new EditorProgressItem(editor);
                				editor.addActor(progressItem);
                				progressItem.setName(editor.getName(progressItem));
                				progressItem.setDefault();
                				progressItem.getPropertySet().put("z",getLastZ(editor));
                				ed.selectView(progressItem);
    
                				progressItem.update();
                            } else if(item.getItemId() == 5){
                                CustomItem custom = new CustomItem(editor);
                				editor.addActor(custom);
                				custom.setName(editor.getName(custom));
                				custom.setDefault();
                				custom.getPropertySet().put("z",getLastZ(editor));
                				ed.selectView(custom);
    
                				custom.update();
                            } else if(item.getItemId() == 6){
								ParticleItem particleItem = new ParticleItem(editor);
								editor.addActor(particleItem);
								particleItem.setName(editor.getName(particleItem));
								particleItem.setDefault();
								particleItem.getPropertySet().put("z",getLastZ(editor));
								ed.selectView(particleItem);
							}
                        });
                        //activity.refreshBodies();
                        return true;
                    }
                });
        popupMenu.show();
	}
}