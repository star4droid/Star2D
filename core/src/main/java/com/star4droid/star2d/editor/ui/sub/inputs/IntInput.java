package com.star4droid.star2d.editor.ui.sub.inputs;

import com.star4droid.template.Utils.Utils;

public class IntInput extends FloatInput {
	
	public IntInput(){
		super();
	}
	
	@Override
	public void setValue(String v){
		try {
			int i = Utils.getInt(v);
			value.setText(v);
		} catch(Exception e){}
	}
}