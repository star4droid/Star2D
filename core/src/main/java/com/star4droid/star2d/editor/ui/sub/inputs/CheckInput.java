package com.star4droid.star2d.editor.ui.sub.inputs;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class CheckInput extends VisTable implements InputField{
	VisTextButton name;
	VisCheckBox checkBox;
	private String fieldName;
	Runnable onChange;
	ChangeListener changeListener;
	boolean disableListener = false;
	public CheckInput(){
		super();
		setBackground(VisUI.getSkin().getDrawable("window-bg"));
		name = new VisTextButton("Name");
		checkBox = new VisCheckBox("");
		name.setBackground(VisUI.getSkin().getDrawable("separator"));
		checkBox.setBackground(VisUI.getSkin().getDrawable("separator"));
		if(isRTL()){
			add().width(8);
			add(checkBox).minWidth(80);
			add().width(8);
			add(name).padRight(8).growX();
		} else {
			add().width(8);
			add(name).minWidth(100).growX();
			add().width(8);
			add(checkBox).minWidth(80).padRight(8);
		}
		changeListener = new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				if(onChange!=null && !disableListener)
					onChange.run();
			}
		};
		checkBox.addListener(changeListener);
		name.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
				showDetails();
			}
		});
	}
	@Override
	public String getFieldName() {
		return fieldName;
	}
	@Override
	public void setNameText(String nm) {
		fieldName = nm;
		name.setText(getTrans(nm));
		//checkBox.setText(nm);
	}
	
	@Override
	public void setValue(String value) {
		disableListener = true;
		checkBox.setChecked(value.equals("true"));
		disableListener = false;
	}

	@Override
	public String getValue() {
	    return String.valueOf(checkBox.isChecked());
	}

	@Override
	public void setOnChange(Runnable runnable) {
		this.onChange = runnable;
	}
	
}