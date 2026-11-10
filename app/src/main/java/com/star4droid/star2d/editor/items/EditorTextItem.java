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
	boolean needFix = false;
	
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
		if(needFix) {
		    y = getStage().getHeight()-height-y;
		    propertySet.put("y", y);
		    needFix = false;
		}
		setSize(width,height);
		setPosition(x,y);
		//if(!font.equals(propertySet.getString("font"))){
			font = propertySet.getString("font");
			if(!font.equals("")){
				getStyle().font = TestApp.getCurrentApp().getFont(editor.getProject().getPath()+"/"+font);
			}
		//}
		//Gdx.files.external("logs/text.txt").writeString("eh : "+getStage().getHeight()+", txh : "+height+", y : "+y+" \n = "+getY()+"\n"+"_".repeat(10)+"\n",true);
		setZIndex(Math.max(0, propertySet.getInt("z")));
		setRotation(-propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
		String shownText = com.star4droid.star2d.Helpers.EditorStrings.show(editor.getProject().getPath(), propertySet.get("Text").toString());
		setText(shownText);
		getStyle().fontColor= propertySet.getColor("Text Color");
		setStyle(getStyle());
		String ta = String.valueOf(propertySet.get("Text Align"));
		setAlignment("CENTER".equalsIgnoreCase(ta) ? com.badlogic.gdx.utils.Align.center : "RIGHT".equalsIgnoreCase(ta) ? com.badlogic.gdx.utils.Align.right : com.badlogic.gdx.utils.Align.left);
		float base = propertySet.containsKey("Font Scale") ? propertySet.getFloat("Font Scale") : 1f;
		// Auto Size (on by default): the letters shrink until the text fits the box
		boolean auto = !"false".equals(String.valueOf(propertySet.get("Auto Size")));
		setFontScale(auto ? com.star4droid.template.Utils.TextFit.scale(getStyle().font, shownText, width, height, base) : base);
	}

	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
		this.propertySet = propertySet;
		needFix = propertySet.getString("Version").equals("");
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