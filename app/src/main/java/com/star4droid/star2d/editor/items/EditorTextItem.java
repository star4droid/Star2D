package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import java.util.ArrayList;

public class EditorTextItem extends Label implements EditorItem {
	LibgdxEditor editor;
	PropertySet<String,Object> propertySet;
	String font = "";
	public EditorTextItem(LibgdxEditor editor){
		super("TextItem",createDefaultStyle());
		this.editor = editor;
		setStyle(createDefaultStyle());
		setText("TextItem");
		editor.addActor(this);
		addListener(new ItemClickListener(this, editor));
		//debug();
		setWrap(true);
	}
	
	public static LabelStyle createDefaultStyle(){
		BitmapFont font = new BitmapFont(Gdx.files.internal("files/default.fnt"));
		LabelStyle labelStyle = new LabelStyle(font, Color.WHITE);
		return labelStyle;
	}
	
	@Override
	public PropertySet<String, Object> getPropertySet() {
	    return propertySet;
	}
	
	public EditorTextItem setDefault() {
		propertySet = PropertySet.getDefualt(this,"text.json");
		//if(propertySet==null) Log.e(Utils.error_tag,"Null PropertySet");
		return this;
	}
	
	@Override
	public void update() {
		setName(propertySet.getString("name"));
		float width = propertySet.getFloat("width"),
			height = propertySet.getFloat("height"),
			x = propertySet.getFloat("x"),
			y = propertySet.getFloat("y");
		setSize(width,height);
		setPosition(x,getStage().getHeight()-height-y);
		//if(!font.equals(propertySet.getString("font"))){
			font = propertySet.getString("font");
			if(!font.equals("")){
				getStyle().font = TestApp.getCurrentApp().getFont(editor.getProject().getPath()+"/"+font);
			}
		//}
		//Gdx.files.external("logs/text.txt").writeString("eh : "+getStage().getHeight()+", txh : "+height+", y : "+y+" \n = "+getY()+"\n"+"_".repeat(10)+"\n",true);
		setZIndex(propertySet.getInt("z"));
		setRotation(-propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
		setText(propertySet.get("Text").toString());
		getStyle().fontColor= propertySet.getColor("Text Color");
		setStyle(getStyle());
		if(propertySet.containsKey("Font Scale"))
			setFontScale(propertySet.getFloat("Font Scale"));
	}

	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
		this.propertySet = propertySet;
		PropertySet<String,Object> temp = PropertySet.getDefualt(this,"text.json");
		for(String s:temp.keySet()){
			if(!propertySet.containsKey(s)){
				propertySet.put(s,temp.get(s));
			}
		}
	    ArrayList<String> toDel = new ArrayList<>();
	   for(String key:propertySet.keySet()){
			if(!temp.containsKey(key)){
				toDel.add(key);
			}
		}
		for(String key:toDel)
	        propertySet.remove(key);
		update();
	}

	@Override
	public String getTypeName() {
	    return "Text";
	}
	
}