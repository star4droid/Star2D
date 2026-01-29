package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.star4droid.star2d.editor.LibgdxEditor;

public class ItemClickListener extends ClickListener {
	Actor actor;
	private long start = 0;
	LibgdxEditor libgdxEditor;
	public ItemClickListener(Actor actor,LibgdxEditor libgdxEditor){
		this.actor = actor;
		this.libgdxEditor = libgdxEditor;
	}
	@Override
	public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
		start = System.currentTimeMillis();
		return super.touchDown(event,x,y,pointer,button);
	}
	@Override
	public void clicked (InputEvent event, float x, float y) {
		if(Math.abs(start - System.currentTimeMillis()) < 300)
			libgdxEditor.selectActor(actor);
	}
	
}