package com.star4droid.star2d.editor.ui.sub.inputs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;

public class SpinnerInput extends DefaultInput {
	
	VisDialog dialog;
	VisTable table;
	VisTextButton cancelBtn;
	VisScrollPane scrollPane;
	public SpinnerInput(){
		super();
		table = new VisTable();
		cancelBtn = new VisTextButton("Cancel");
		cancelBtn.setBackground(VisUI.getSkin().getDrawable("list-selection"));
		scrollPane = new VisScrollPane(table);
		dialog = new VisDialog("Choose Value");
		table.setFillParent(true);
		scrollPane.setFillParent(true);
		scrollPane.setFlickScroll(true);
		dialog.add(scrollPane).pad(8);
		dialog.setResizable(true);
		//cancelBtn.setBackground(VisUI.getSkin().getDrawable("list-selection"));
		cancelBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				dialog.hide();
			}
		});
		value.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				showDialog();
			}
		});
	}
	
	public void showDialog(){
		//dialog.setSize(Gdx.graphics.getWidth()*0.333333f,Gdx.graphics.getHeight()*0.75f);
		if(getStage()==null) return;
		dialog.show(getStage());
		dialog.toFront();
	}
	
	public SpinnerInput setData(String... data){
		table.clear();
		table.add().padLeft(40).height(50).row();
		for(String str:data){
			addChoice(str);
		}
		table.add(cancelBtn).padRight(40).height(50).growX().padTop(45);
		return this;
	}
	
	private void addChoice(String choice){
		VisTextButton textButton = new VisTextButton(choice);
		textButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				value.setText(choice);
				if(onChange!=null)
					onChange.run();
				dialog.hide();
			}
		});
		table.add(textButton).height(50).padRight(40).padBottom(5).growX().row();
	}
}