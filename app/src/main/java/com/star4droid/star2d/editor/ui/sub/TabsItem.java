package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;

public class TabsItem extends Table {
	TestApp app;
	Drawable scene;
	VisLabel label;
	public TabsItem(TestApp app){
		this.app = app;
		setBackground(VisUI.getSkin().getDrawable("window-bg"));
		label = new VisLabel("Scenes");
		left();
		scene = drawable("scene.png");
	}
	
	public void refresh(){
		clear();
		add().width(8);
		add(label);
		add().width(10);
		int pos = 0;
		for(LibgdxEditor editor:app.editors){
			addTab(editor.getScene(),pos);
			pos++;
		}
	}
	
	private void addTab(String name,int pos){
		Table table = new Table();
		VisImage close = new VisImage(VisUI.getSkin().getDrawable("icon-close")),
			sceneIcon = new VisImage(scene);
		table.setBackground(VisUI.getSkin().getDrawable(app.getEditor().getScene().toLowerCase().equals(name.toLowerCase())?"list-selection":"separator"));
		VisLabel label = new VisLabel(name);
		table.add().width(5);
		table.add(sceneIcon);
		table.add().width(5);
		table.add(label);
		table.add().width(5);
		table.add(close).size(45,45);
		add(table).height(50);
		add().width(5);
		table.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				//when the close button clicked, this function also called
				//so , the condition will be false in that case...
				if(app.editors.size > pos && app.editors.get(pos).getScene().toLowerCase().equals(name.toLowerCase()))
					app.setCurrentEditor(pos);
				refresh();
			}
		});
		close.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				if(app.editors.size > 1){
					for(int i = 0; i < app.editors.size; i++){
						if(app.editors.get(i).getScene().toLowerCase().equals(name.toLowerCase())){
							app.editors.get(i).dispose();
							app.editors.removeIndex(i);
							if(app.getEditor().getScene().toLowerCase().equals(name.toLowerCase()))
								app.setCurrentEditor(0);
							//table.remove();
							return;
						}
					}
					app.toast("Unknown Error!!");
				} else app.toast("you can\'t close all scenes!!");
			}
		});
	}
	
	public static Drawable drawable(String name) {
		return new TextureRegionDrawable(new Texture(Gdx.files.internal("images/" + name)));
	}
}