package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.template.Utils.Utils;
import java.util.HashMap;

public class PropertiesItem extends Group {
	TestApp app;
	VisScrollPane scrollPane;
	VisTable table;
	Array<SectionItem> sectionItems = new Array<>();
	public PropertiesItem(TestApp app){
		super();
		this.app = app;
		init();
	}
	
	private void init(){
		table = new VisTable();
		scrollPane = new VisScrollPane(table);
		scrollPane.setFillParent(true);
		scrollPane.setFlickScroll(true);
		addActor(scrollPane);
		//load properties
		String keys=",";
		HashMap<String,Object> map=null;
		//editor.setProperties(this);
		try {
			map = new Gson().fromJson(Utils.internal("types.json").readString(), new TypeToken<HashMap<String, Object>>(){}.getType());
		} catch(Exception ex){
			app.toast("Map init error : "+ex.toString());
			return;
		}
		
		HashMap<String,Object> propertiesMap = new HashMap<>();
		for(String s:map.keySet()){
			for(String str:map.get(s).toString().split(",")){
				if(str.equals("")) continue;
				propertiesMap.put(str,s);
			}
		}
		
		try {
			map = new Gson().fromJson(Utils.internal("map.json").readString(), new TypeToken<HashMap<String, Object>>(){}.getType());
		} catch(Exception ex){
			app.toast("editor map.json error : "+ex.toString());
			com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString("editor map.json error : "+ex.toString()+"\n",true);
		}
		
		for(String s:map.keySet()){
			if(s.equals("static")) continue;
			keys+=s+":\n";
			SectionItem section = new SectionItem(s);
			for(String str:map.get(s).toString().split(",")){
				keys+=str+",";
				if(str.equals("")) continue;
				try {
					if(propertiesMap.get(str).equals("static")) continue;
					EditorField editorField = new EditorField(app,str,propertiesMap.get(str).toString());
					section.addField(editorField);
					//Log.e(Utils.error_tag,"√ key : "+str);
				} catch(Exception ex){
					String msg = "field error : "+ex.toString()+",name : "+str+",type : "+propertiesMap.get(str);
					app.toast(msg);
					com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString(msg+"\n",true);
					//Log.e(Utils.error_tag,"key : "+str+"\n"+ex.toString());
				}
			}
			PropertySet<String,Object> propertySet = app.getEditor().getSelectedActor() != null ? PropertySet.getPropertySet(app.getEditor().getSelectedActor()) : null;
			if(propertySet!=null)
				section.update(propertySet);
			table.add(section).growX().row();
			sectionItems.add(section);
		}
		//com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString(i+"",false);
	}
	
	public void refresh(){
		PropertySet<String,Object> propertySet = (app.getEditor().getSelectedActor() == null ? null : PropertySet.getPropertySet(app.getEditor().getSelectedActor()));
		table.clear();
		for(SectionItem sectionItem:sectionItems){
			try {
				if(sectionItem.isVisible(propertySet)){
					table.add(sectionItem).growX().row();
					sectionItem.update(propertySet);
				}
			} catch(Exception exception){
				com.badlogic.gdx.Gdx.files.external("logs/app_logs.txt").writeString("properties item error : "+exception.toString()+"\n",true);
			}
		}
	}
	
}