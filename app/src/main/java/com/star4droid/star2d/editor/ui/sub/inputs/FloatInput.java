package com.star4droid.star2d.editor.ui.sub.inputs;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.template.Utils.Utils;

public class FloatInput extends DefaultInput {
	
	public FloatInput(){
		super();
		value.setText("0");
		value.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                NumberInputDialog.getInstance().setValue(getValue()).setCondition(NumberInputDialog.floatCondition).setOnInput((input)->{
					value.setText(input);
					if(onChange!=null)
						onChange.run();
				}).setCondition(NumberInputDialog.floatCondition).show(getStage());
            }
        });
	}
	
	@Override
	public void setValue(String value) {
		try {
			float f = Utils.getFloat(value);
			this.value.setText(String.valueOf(f));
		} catch(Exception e){}
	}
	
}