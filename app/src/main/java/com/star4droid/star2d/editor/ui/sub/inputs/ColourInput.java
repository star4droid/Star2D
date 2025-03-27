package com.star4droid.star2d.editor.ui.sub.inputs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import com.star4droid.star2d.editor.ui.ControlLayer;

public class ColourInput extends DefaultInput {
	ColorPicker colorPicker;
	public ColourInput(ControlLayer controlLayer){
		super();
		colorPicker = controlLayer.getColorPicker();
		
		value.setText(Color.WHITE.toString().toUpperCase());
		
		ColorPickerListener colorPickerListener = new ColorPickerListener(){
			@Override
			public void canceled(Color oldColor) {
				
			}
			
			@Override
			public void changed(Color color) {
				
			}
			
			@Override
			public void reset(Color arg0, Color arg1) {}
			
			@Override
			public void finished(Color color) {
				//String hex = String.format("#%06X", (0xFFFFFF & color.toIntBits()));
				String hex = color.toString().toUpperCase();
				value.setText(hex);
				colorPicker.setListener(null);
				if(onChange!=null)
					onChange.run();
			}
			
		};
		//colorPicker.setMovable(true);
		//colorPicker.setResizable(true);
		value.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				colorPicker.setListener(colorPickerListener);
				colorPicker.setColor(Color.valueOf(getValue()));
				getStage().addActor(colorPicker);
				colorPicker.toFront();
			}
		});
	}
	
	private Color getColorValue(){
		try {
			Color color = Color.valueOf(getValue());
			return color;
		} catch(Exception exception){
			return Color.WHITE;
		}
	}
	
	@Override
	public void setValue(String value) {
		try {
			Color color = Color.valueOf(value);
			this.value.setText(color.toString().toUpperCase());
			//colorPicker.setColor(color);
		} catch(Exception e){
			this.value.setText(Color.WHITE.toString().toUpperCase());
		}
	}
}