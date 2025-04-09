package com.star4droid.star2d.editor.ui.sub.inputs;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.star4droid.star2d.editor.ui.SingleInputDialog;

public class StringInput extends DefaultInput {
	
	public StringInput(){
		super();
		value.setText("");
		value.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				new SingleInputDialog("Input Value","Value : ",value.getText().toString(),(vl)->{
					value.setText(vl);
					if(onChange!=null)
						onChange.run();
				}).show(getStage());
			}
		});
	}
}