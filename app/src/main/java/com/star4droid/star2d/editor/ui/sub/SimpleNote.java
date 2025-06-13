package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.utils.Lang;

public class SimpleNote extends VisDialog {
	VisLabel label;
	public Runnable okBtnClick;
	VisTextButton okBtn;
	BitmapFont arabicFont;
	public SimpleNote(String title,String message){
		super(title);
		reset();
		label = new VisLabel(message);
		arabicFont = label.getStyle().font;
		if(!Lang.isRTL()){
			VisLabel.LabelStyle style = new VisLabel.LabelStyle(label.getStyle());
			style.font = VisUI.getSkin().get("no-rtl",BitmapFont.class);
			label.setStyle(style);
		}
		label.setAlignment(Align.center);
		label.setWrap(true);
		add(label).padBottom(5).center().growX().row();
		okBtn = new VisTextButton(Lang.getTrans("ok"));
		okBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
				if(okBtnClick!=null)
					okBtnClick.run();
				else hide();
			}
		});
		add(okBtn).minWidth(80).center();
	}
	
	public SimpleNote setMessage(String msg){
		label.setText(msg);
		return this;
	}
	
	public SimpleNote setOkText(String txt){
		okBtn.setText(txt);
		return this;
	}
	
	@Override
	public VisDialog show(Stage stage, Action action) {
		VisDialog dialog = super.show(stage,action);
		setWidth(Gdx.graphics.getWidth()*0.85f);
		label.setScale(Lang.isRTL() ? 0.8f : 1f);
		return dialog;
	}
}