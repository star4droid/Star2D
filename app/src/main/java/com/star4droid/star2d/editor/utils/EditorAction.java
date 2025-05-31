package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.*;
import com.star4droid.template.Utils.Utils;

public class EditorAction {
	ACTION action;
	LibgdxEditor editor;
	PropertySet<String,Object> values = new PropertySet<>();
	
	public EditorAction(LibgdxEditor editor){
		this(editor,ACTION.NO_ACTION);
	}
	
	public EditorAction(LibgdxEditor editor,ACTION action){
		this.action = action;
		this.editor = editor;
	}
	
	public EditorAction put(String key,Object value){
		values.put(key,value);
		return this;
	}
	
	//e.g : put({"key1","value1"},{"key2","value2"})
	public EditorAction put(String[]... array){
		for(String[] set:array){
			put(set[0],set[1]);
		}
		return this;
	}
	
	public EditorAction undo(){
		try {
			PropertySet<String,Object> propertySet = getPropertySet();
			switch(action){
				case CHANGE_PROPERTY:
					PropertySet<String,Object> ps = PropertySet.getFrom(get("old"));
					propertySet.putAll(ps);
					editor.toast("property changed : "+get("message"));
					//Gdx.files.external("logs/app_logs.txt").writeString("changed to : \n"+ps.toString()+",result : \n"+propertySet.toString()+"\n"+"__".repeat(5)+"\n",true);
				break;
				case RENAME_ITEM:
					propertySet = getPropertySet(get("new"));
					if(propertySet.getString("Script").equals(propertySet.getString("name"))&&!propertySet.getString("name").equals(get("old"))){
						propertySet.put("Script",get("old"));
						try {
							/*.moveTo(
								Gdx.files.absolute(editor.getProject().getBodyScriptPath(get("old"),editor.getScene()))
							);
							*/
							String name = propertySet.getString("name");
							String code = Gdx.files.absolute(editor.getProject().getBodyScriptPath(name,editor.getScene())).readString();
							Gdx.files.absolute(editor.getProject().getBodyScriptPath(name,editor.getScene())).delete();
							// replace class name by the old class name...
							Gdx.files.absolute(editor.getProject().getBodyScriptPath(get("old"),editor.getScene())).writeString(code.replace(name+"Script",get("old")+"Script"),false);
						} catch(Exception ex){}
					}
					propertySet.put("name",get("old"));
					editor.findActor(get("new")).setName(get("old"));
					editor.toast("Rename item to : "+get("old"));
				break;
				case ITEM_ADD:
					Actor actor = getActor();
					if(actor == null)
						actor = editor.findActor(get("name"));
					if(actor!=null){
						actor.remove();
						if(editor.getSelectedActor()!=null && editor.getSelectedActor().getName().equals(actor.getName()))
							editor.selectActor(null);
						editor.toast("Item Removed : "+actor.getName());
					} else editor.toast("Item to remove not found\nname : "+get("name"));
				break;
				case SCENE_CONFIG:
					Gdx.files.absolute(editor.getProject().getConfig(editor.getScene())).writeString(get("old"),false);
					editor.updateConfig();
					editor.toast("Scene Config Changed...");
				break;
				case ITEM_DELETED:
					Actor actor1 = getActor();
						if(actor1 == null) actor1 = (Actor)createBody(PropertySet.getFrom(get("item")));
					editor.addActor(actor1);
					editor.toast("Item Added : "+actor1.getName());
				break;
				case RENAME_SCENE:
					editor.getProject().renameScene(editor.getScene(),get("old"));
					editor.setScene(get("old"));
				break;
			}
			if(get("set").toString().equals("true"))
				((EditorItem)editor.findActor(get("name"))).setProperties(propertySet);
			
			if(get("update").equals("true"))
				editor.updateProperties();
			if(get("updateItem").equals("true"))
				((EditorItem)editor.findActor(get("name"))).update();
		} catch(Exception exception){
			exception.printStackTrace();
			Gdx.files.external("logs/app_logs.txt").writeString("undo error : action :\n"+toString()+"\n"+Utils.getStackTraceString(exception)+"\n"+"__".repeat(5)+"\n",true);
			editor.toast("Undo Error : "+exception.toString());
		}
		return this;
	}
	
	public EditorAction updateEditorProperties(){
		put("update","true");
		return this;
	}
	
	public EditorAction setItemProperties(){
		put("set","true");
		return this;
	}
	
	public EditorAction updateItemProperties(){
		put("updateItem","true");
		return this;
	}
	
	public String get(String key){
		return values.containsKey(key)?values.get(key).toString():"";
	}
	
	private PropertySet<String,Object> getPropertySet(){
		return values.containsKey("name")?getPropertySet(get("name")):null;
	}
	
	private PropertySet<String,Object> getPropertySet(String name){
		Actor actor = editor.findActor(name);
		return actor != null ? PropertySet.getPropertySet(actor):null;
	}
	
	public EditorAction redo(){
		try {
			PropertySet<String,Object> propertySet = getPropertySet();
			switch(action){
				case CHANGE_PROPERTY:
					PropertySet<String,Object> ps = PropertySet.getFrom(get("new"));
					propertySet.putAll(ps);
					editor.toast("property changed : "+get("message"));
					//Gdx.files.external("logs/app_logs.txt").writeString("changed to : \n"+ps.toString()+",result : \n"+propertySet.toString(),true);
				break;
				case RENAME_ITEM:
					propertySet = getPropertySet(get("old"));
					if(propertySet.getString("Script").equals(propertySet.getString("name"))&&!propertySet.getString("name").equals(get("new"))){
						propertySet.put("Script",get("new"));
						try {
							String name = propertySet.getString("name");
							String code = Gdx.files.absolute(editor.getProject().getBodyScriptPath(name,editor.getScene())).readString();
							Gdx.files.absolute(editor.getProject().getBodyScriptPath(name,editor.getScene())).delete();
							//replace the class name with the new name...
							Gdx.files.absolute(editor.getProject().getBodyScriptPath(get("new"),editor.getScene())).writeString(code.replace(name+"Script",get("new")+"Script"),false);
						} catch(Exception ex){}
					}
					propertySet.put("name",get("new"));
					editor.findActor(get("old")).setName(get("new"));
					editor.toast("Rename Item to : "+get("new"));
				break;
				case ITEM_ADD:
					Actor actor = getActor();
						if(actor==null) actor = (Actor)(createBody(PropertySet.getFrom(get("item"))));
					editor.addActor(actor);
					editor.toast("Item Added : "+actor.getName());
				break;
				case SCENE_CONFIG:
					Gdx.files.absolute(editor.getProject().getConfig(editor.getScene())).writeString(get("new"),false);
					editor.updateConfig();
					editor.toast("Scene Config Changed...");
				break;
				case ITEM_DELETED:
					Actor actor1 = (values.containsKey("actor") && values.get("actor") instanceof Actor) ?((Actor)values.get("actor")) : editor.findActor(get("name"));
					actor1.remove();
					if(editor.getSelectedActor()!=null && editor.getSelectedActor().getName().equals(actor1.getName()))
						editor.selectActor(null);
					editor.toast("Item Removed : "+actor1.getName());
				break;
				case RENAME_SCENE:
					editor.getProject().renameScene(editor.getScene(),get("new"));
					editor.setScene(get("new"));
				break;
			}
			if(get("set").toString().equals("true"))
				((EditorItem)editor.findActor(get("name"))).setProperties(propertySet);
			if(get("update").equals("true"))
				editor.updateProperties();
			if(get("updateItem").equals("true"))
				((EditorItem)editor.findActor(get("name"))).update();
		} catch(Exception exception){
			exception.printStackTrace();
			Gdx.files.external("logs/app_logs.txt").writeString("redo err : action : \n"+toString()+"\n"+Utils.getStackTraceString(exception)+"\n"+"__".repeat(5)+"\n",true);
			editor.toast("Redo Error : "+exception.toString());
		}
		return this;
	}
	
	private Actor getActor(){
		try {
			Actor actor = (values.containsKey("actor") && values.get("actor") instanceof Actor)?(Actor)values.get("actor"):null;
			if(actor!=null &&actor.getStage()!=null&&actor.getStage() == editor)
				return actor;
		} catch(Exception e){}
		return null;
	}
	
	private EditorItem createBody(PropertySet<String,Object> propertySet){
		EditorItem item = null;
		switch(propertySet.getString("TYPE")){
			case "BOX":
			item = new BoxItem(editor);
			break;
			case "CUSTOM":
			item = new CustomItem(editor);
			break;
			case "CIRCLE":
			item = new CircleItem(editor);
			break;
			case "TEXT":
			item = new EditorTextItem(editor);
			break;
			case "PROGRESS":
			item = new EditorProgressItem(editor);
			break;
			case "JOYSTICK":
			item = new JoyStickItem(editor);
			break;
			case "LIGHT":
			item = new LightItem(editor);
			break;
			case "PARTICLE":
			item = new ParticleItem(editor);
			break;
		}
		
		if (item == null){
			editor.toast("null item in editor action...");
			return null;
		}
		if(propertySet==null){
			Gdx.files.external("logs/app_logs.txt").writeString("\nnull props at editor action",true);
		}
		
		((Actor)item).setName(propertySet.getString("name"));
		item.setProperties(propertySet);
		//Gdx.files.external("logs/bodies.txt").writeString("add : "+propertySet.getString("name")+",json : \n"+propertySet.toString()+"\n\n",true);
		if (editor.getSelectedActor() == null)
			editor.selectActor((Actor) item);
		return item;
	}
	
	public static EditorAction sceneConfigChanged(LibgdxEditor editor,String old,String newC){
		if(newC.equals(old)) return null;
		EditorAction action = new EditorAction(editor,ACTION.SCENE_CONFIG);
		action.put("new",newC);
		action.put("old",old);
		editor.pushUndoEvent(action);
		return action;
	}
	
	public static EditorAction itemAdded(LibgdxEditor editor,Actor actor){
		EditorAction action = new EditorAction(editor,ACTION.ITEM_ADD);
		action.put("name",actor.getName());
		action.put("actor",actor);
		action.put("item",PropertySet.getPropertySet(actor));
		editor.pushUndoEvent(action);
		return action;
	}
	
	public static EditorAction itemRenamed(LibgdxEditor editor,String oldName,String newName){
		EditorAction action = new EditorAction(editor,ACTION.RENAME_ITEM);
		action.put("old",oldName);
		action.put("new",newName);
		editor.pushUndoEvent(action);
		return action;
	}
	
	public static EditorAction itemRemoved(LibgdxEditor editor,Actor actor){
		EditorAction action = new EditorAction(editor,ACTION.ITEM_DELETED);
		action.put("name",actor.getName());
		action.put("actor",actor);
		action.put("item",PropertySet.getPropertySet(actor).toString());
		editor.pushUndoEvent(action);
		return action;
	}
	
	public static EditorAction propertiesChanged(LibgdxEditor editor,String name,String message,String[] values,String[] old){
		EditorAction action = new EditorAction(editor,ACTION.CHANGE_PROPERTY);
		PropertySet<String,Object> ps = new PropertySet<>(),
			oldP = new PropertySet<>();
		for(int x = 0;x < values.length; x+=2){
			if(values[x+1] == old[x+1] || values[x+1].equals("")) continue;
			ps.put(values[x],values[x+1]);
			oldP.put(old[x],old[x+1]);
		}
		if(ps.isEmpty()) {
		    return null;//no changes...
		}
		action.put("message",message);
		action.put("name",name);
		action.put("new",ps.toString());
		action.put("old",oldP.toString());
		//editor.toast("Undo Redo Pushed!");
		editor.pushUndoEvent(action);
		return action;
	}
	
	public static EditorAction renameScene(LibgdxEditor editor,String oldName,String newName){
		EditorAction action = new EditorAction(editor,ACTION.RENAME_SCENE);
		action.put("new",newName);
		action.put("old",oldName);
		editor.pushUndoEvent(action);
		return action;
	}
	
	@Override
	public String toString() {
		Object actorObj = values.get("actor");
		Actor actor = getActor();
		if(actor!=null)
			values.remove(actor);
		if(values.containsKey("actor"))
			values.put("actor","");
		values.put("ACTION_TYPE",action.name());
		String json = values.toString();
		if(actor!=null)
			values.put("actor",actorObj);
		return json;
	}
	
	@Override
	public boolean equals(Object object) {
		if(object instanceof EditorAction){
			return toString().equals(((EditorAction)object).toString());
		}
		return false;
	}
	
	public EditorAction load(String json){
		values = PropertySet.getFrom(json);
		for(ACTION act:ACTION.values())
			if(values.getString("ACTION_TYPE").equals(act.name())){
				this.action = act;
				break;
			}
		return this;
	}
	
	public static EditorAction getFrom(String json,LibgdxEditor editor){
		return new EditorAction(editor).load(json);
	}
	
	public enum ACTION {
		CHANGE_PROPERTY,RENAME_ITEM,ITEM_ADD,
		SCENE_CONFIG,ITEM_DELETED,RENAME_SCENE,NO_ACTION
	}
}