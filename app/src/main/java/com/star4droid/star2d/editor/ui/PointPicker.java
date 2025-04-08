package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.LibgdxEditor;

public class PointPicker extends Table {
	LibgdxEditor.OnPickListener pickListener;
	public PointPicker(){
		super();
		Image image = new Image(new Texture(Gdx.files.internal("images/pointer-icon.png")));
		add(image).width(75).height(75).center();
		row();
		VisTextButton button = new VisTextButton("Select");
		add(button).pad(5);
		button.addListener(new ClickListener(){
			@Override
            public void clicked(InputEvent event, float x, float y) {
                if(pickListener!=null)
					pickListener.onPick(getX(),getY());
				remove();
            }
		});
	}
	
	@Override
	public void act(float delta) {
    	super.act(delta);
    
    	// Get camera center position
    	float centerX = getStage().getCamera().position.x;
    	float centerY = getStage().getCamera().position.y;

    	// Get stage width & height to calculate correct centering
    	float stageWidth = getStage().getWidth();
    	float stageHeight = getStage().getHeight();

    	// Get table dimensions
    	float tableWidth = getWidth();
    	float tableHeight = getHeight();

    	// Set position to keep it centered
    	setPosition(centerX - tableWidth / 2, centerY - tableHeight / 2);
	}
	
	public void setOnPickListener(LibgdxEditor.OnPickListener onPickListener){
		this.pickListener = onPickListener;
	}
}