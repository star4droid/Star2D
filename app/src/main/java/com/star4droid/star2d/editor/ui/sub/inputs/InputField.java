package com.star4droid.star2d.editor.ui.sub.inputs;

public interface InputField {
	public void setNameText(String name);
	public void setValue(String value);
	public String getValue();
	public String getFieldName();
	public void setOnChange(Runnable runnable);
}