package com.star4droid.star2d.editor.ui.sub.inputs;

import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.template.Utils.Utils;
import com.star4droid.template.Utils.Utils;

public class DefaultInput extends VisTable implements InputField {
	public VisTextButton name,value;
	public Runnable onChange;
	public DefaultInput(){
		setBackground(VisUI.getSkin().getDrawable("window-bg"));
		name = new VisTextButton("Name");
		value = new VisTextButton("Value");
		name.setBackground(VisUI.getSkin().getDrawable("separator"));
		value.setBackground(VisUI.getSkin().getDrawable("separator"));
		add().width(8);
		add(name).growX();
		add().width(8);
		add(value).maxWidth(150).growX();
		add().width(8);
	}
	@Override
	public String getFieldName() {
		return name.getText().toString();
	}
	@Override
	public void setOnChange(Runnable runnable) {
		this.onChange = runnable;
	}
	
	@Override
	public void setNameText(String nm) {
		this.name.setText(nm);
	}
	
	@Override
	public void setValue(String value) {
		this.value.setText(value);
	}

	@Override
	public String getValue() {
	    return value.getText().toString();
	}
}