package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.ui.sub.inputs.InputField;
import com.star4droid.template.Utils.Utils;

public class SectionItem extends VisTable {
	VisTable content;
	VisTextButton label;
	Array<EditorField> inputFields = new Array<>();
	public SectionItem(String title){
		super();
		content = new VisTable();
		label = new VisTextButton(title);
		VisImageButton imageButton = new VisImageButton(drawable("down.png"));
		imageButton.setTouchable(Touchable.disabled);
		label.setTouchable(Touchable.disabled);
		
		VisTable table = new VisTable();
		table.left();
		table.add(imageButton).size(60,60);
		
		table.add(label).height(60).growX();
		add(table).growX().padLeft(8);
		
		setBackground(VisUI.getSkin().getDrawable("window-bg"));
		
		row();
		add(content).padTop(8).growX();
	}
	
	public void addField(EditorField editorField){
		inputFields.add(editorField);
	}
	
	public boolean isVisible(PropertySet<String,Object> pr){
		if(pr!=null){
			for(EditorField field:inputFields){
				if(field.getInputField()!=null && pr.containsKey(field.getInputField().getFieldName()))
					return true;
				//com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString("not visible : key : "+field.getInputField().getFieldName()+", ps : "+pr.toString()+"\n",true);
			}
		}
		return false;
	}
	
	public void update(PropertySet<String,Object> pr){
		content.clear();
		
		//com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString("update called\n",true);
		int fieldsNo = 0;
		for(EditorField field:inputFields){
			if(pr==null) break;
			try {
				if(field.getInputField()!=null && !pr.containsKey(field.getInputField().getFieldName()))
					continue;
				field.refresh(pr);
				fieldsNo++;
				content.add((Actor)field.getInputField()).growX().padBottom(15).row();
			} catch(Exception e){
				com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString("Error on refreshing : "+e.toString()+"\n"+"__".repeat(5)+"\n",true);
			}
		}
		//setVisible(fieldsNo>0);
		//com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString("section : "+label.getText()+",refresh count : "+i,false);
		//pack();
	}
	
	public SectionItem setSectionName(String name){
		label.setText(name);
		return this;
	}
	
	public VisTable getContentTable(){
		return content;
	}
	
	private Drawable drawable(String nm){
		return Utils.getDrawable(Utils.internal("images/"+nm));
	}
}