package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisRadioButton;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.template.Utils.PropertySet;
import java.util.ArrayList;

public class BodyScriptSelector extends VisDialog {
	TestApp app;
	public OnScriptSelected onScriptSelected;
	private static String otherScene = "Other Scene : ",removeScript = "Remove Script";
	String checkedItem = "",currentScene = "";
	VisTable table,btnsTable;
	ButtonGroup<VisRadioButton> group;
	public BodyScriptSelector(TestApp app){
		super("Select Script");
		this.app = app;
		group = new ButtonGroup<>();
		btnsTable = new VisTable();
		table = new VisTable();
		VisTextButton select = new VisTextButton("Select"),
			cancel = new VisTextButton("Cancel");
		table.add(select).padLeft(6).growX();
		table.add(cancel).padLeft(6).growX().padRight(6);
		reset();
		VisScrollPane scrollPane = new VisScrollPane(btnsTable);
		scrollPane.setFlickScroll(true);
		scrollPane.setOverscroll(false,false);
		add(scrollPane).minHeight(350).maxHeight(600).grow().row();
		add(table).padBottom(8);
		//refresh(app.getEditor().getScene());
		cancel.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				hide();
			}
		});
		select.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				String text = group.getChecked().getText().toString();
				if(text.startsWith(otherScene)){
					refresh(text.replace(otherScene,""));
				} else if(text.equals(removeScript)){
					if(onScriptSelected!=null)
						onScriptSelected.onSelect("");
					hide();
				} else if(onScriptSelected!=null) {
					if(app.getEditor().getScene().equals(currentScene))
						onScriptSelected.onSelect(text);
					else onScriptSelected.onSelect(currentScene+"/"+text);
					hide();
				}
			}
		});
	}
	
	public BodyScriptSelector setOnSelect(OnScriptSelected scriptSelected){
		this.onScriptSelected = scriptSelected;
		return this;
	}
	
	public BodyScriptSelector setCheckedItem(String item){
		String scene = app.getEditor().getScene();
		checkedItem = item;
		if(item.contains("/")){
			scene = item.split("/")[0];
		} else if(item.equals("")){
			group.setChecked(app.getEditor().getSelectedActor().getName());
			return this;
		}
		if(scene.equals(currentScene)){
			group.setChecked(item.contains("/") ? item.split("/")[1] : item);
			if(app.getEditor().getScene().equals(scene) && !group.getChecked().getText().toString().equals(item.contains("/") ? item.split("/")[1] : item))
				group.setChecked(app.getEditor().getSelectedActor().getName());
		} else group.setChecked(otherScene+scene);
		return this;
	}
	
	public BodyScriptSelector refresh(String scene){
		group.clear();
		btnsTable.clear();
		currentScene = scene;
		//VisRadioButton remove = new VisRadioButton(removeScript);
		//remove.setChecked(checkedItem.equals(""));
		//group.add(remove);
		//btnsTable.add(remove).growX().padTop(5);
		if(scene.equals(app.getEditor().getScene())){
			for(String item:app.getEditor().getBodiesList()){
				VisRadioButton button = new VisRadioButton(item);
				//button.setChecked(checkedItem.equals(item));
				group.add(button);
				btnsTable.add(button).growX().padTop(5).row();
			}
		} else {
			ArrayList<PropertySet<String, Object>> propertySets = new Gson().fromJson(Gdx.files.absolute(app.getEditor().getProject().getScenesPath()+scene).readString(),
					new TypeToken<ArrayList<PropertySet<String, Object>>>() {
					}.getType());
			for(PropertySet<String,Object> ps:propertySets){
				VisRadioButton button = new VisRadioButton(ps.getString("name"));
				//button.setChecked(checkedItem.equals(ps.getString("name")));
				group.add(button);
				btnsTable.add(button).growX().padTop(5).row();
			}
		}
		for(FileHandle fileHandle:Gdx.files.absolute(app.getEditor().getProject().getScenesPath()).list()){
			if(!fileHandle.name().equals(scene)){
				VisRadioButton button = new VisRadioButton(otherScene+fileHandle.name());
				//button.setChecked(checkedItem.equals(ps.getString("name")));
				group.add(button);
				btnsTable.add(button).growX().padTop(5).row();
			}
		}
		if(!checkedItem.equals(""))
			setCheckedItem(checkedItem);
		return this;
	}
	
	public interface OnScriptSelected {
		public void onSelect(String script);
	}
}