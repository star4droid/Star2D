package com.star4droid.star2d.editor.ui.sub.inputs;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;

public class CheckInput extends VisTable implements InputField{
	VisTextButton name;
	VisCheckBox checkBox;
	Runnable onChange;
	ChangeListener changeListener;
	boolean disableListener = false;
	public CheckInput(){
		super();
		setBackground(VisUI.getSkin().getDrawable("window-bg"));
		name = new VisTextButton("Name");
		checkBox = new VisCheckBox("Value");
		name.setBackground(VisUI.getSkin().getDrawable("separator"));
		checkBox.setBackground(VisUI.getSkin().getDrawable("separator"));
		add().width(8);
		add(name).minWidth(100).growX();
		add().width(8);
		add(checkBox).minWidth(100).growX();
		changeListener = new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				if(onChange!=null && !disableListener)
					onChange.run();
			}
		};
		checkBox.addListener(changeListener);
	}
	@Override
	public String getFieldName() {
		return name.getText().toString();
	}
	@Override
	public void setNameText(String nm) {
		name.setText(nm);
		checkBox.setText(nm);
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