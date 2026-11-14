package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.*;
import com.star4droid.template.Utils.Utils;
import java.util.ArrayList;
import java.util.List;

public class EditorAction {
	ACTION action;
	LibgdxEditor editor;
	PropertySet<String,Object> values = new PropertySet<>();
	/** sub actions of a BATCH (one undo step made of several), built from the "subs" json when needed. */
	private List<EditorAction> subs;
	
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
					if(propertySet == null){ editor.toast("Item not found, step skipped : "+get("name")); return this; }
					propertySet.putAll(ps);
					if(ps.containsKey("parent")) { com.star4droid.star2d.editor.Hierarchy.relink(editor); com.star4droid.star2d.editor.Hierarchy.refreshLists(editor); }
					editor.toast("property changed : "+get("message"));
					//Gdx.files.external("logs/app_logs.txt").writeString("changed to : \n"+ps.toString()+",result : \n"+propertySet.toString()+"\n"+"__".repeat(5)+"\n",true);
				break;
				case RENAME_ITEM:
					propertySet = getPropertySet(get("new"));
					if(propertySet == null || editor.findActor(get("new")) == null){ editor.toast("Item not found, step skipped : "+get("new")); return this; }
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
					editor.getControlLayer().getBodiesList().update(true);
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
					com.star4droid.star2d.editor.Hierarchy.relink(editor);
					editor.toast("Item Added : "+actor1.getName());
				break;
				case RENAME_SCENE:
					editor.getProject().renameScene(editor.getScene(),get("old"));
					editor.setScene(get("old"));
				break;
				case BATCH:
					List<EditorAction> list = subs();
					for(int i = list.size() - 1; i >= 0; i--)
						list.get(i).undo();
					com.star4droid.star2d.editor.Hierarchy.relink(editor);
					com.star4droid.star2d.editor.Hierarchy.refreshLists(editor);
				break;
			}
			if(get("set").toString().equals("true") && editor.findActor(get("name")) instanceof EditorItem)
				((EditorItem)editor.findActor(get("name"))).setProperties(propertySet);
			
			if(get("update").equals("true"))
				editor.updateProperties();
			if(get("updateItem").equals("true") && editor.findActor(get("name")) instanceof EditorItem)
				((EditorItem)editor.findActor(get("name"))).update();
		} catch(Exception exception){
			exception.printStackTrace();
			com.star4droid.star2d.logs.Logs.editor("undo", "undo error : action :\n"+toString()+"\n"+Utils.getStackTraceString(exception)+"\n"+"__".repeat(5)+"\n");
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
					if(propertySet == null){ editor.toast("Item not found, step skipped : "+get("name")); return this; }
					propertySet.putAll(ps);
					if(ps.containsKey("parent")) { com.star4droid.star2d.editor.Hierarchy.relink(editor); com.star4droid.star2d.editor.Hierarchy.refreshLists(editor); }
					editor.toast("property changed : "+get("message"));
					//Gdx.files.external("logs/app_logs.txt").writeString("changed to : \n"+ps.toString()+",result : \n"+propertySet.toString(),true);
				break;
				case RENAME_ITEM:
					propertySet = getPropertySet(get("old"));
					if(propertySet == null || editor.findActor(get("old")) == null){ editor.toast("Item not found, step skipped : "+get("old")); return this; }
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
					editor.getControlLayer().getBodiesList().update(true);
				break;
				case ITEM_ADD:
					Actor actor = getActor();
						if(actor==null) actor = (Actor)(createBody(PropertySet.getFrom(get("item"))));
					editor.addActor(actor);
					com.star4droid.star2d.editor.Hierarchy.relink(editor);
					editor.toast("Item Added : "+actor.getName());
				break;
				case SCENE_CONFIG:
					Gdx.files.absolute(editor.getProject().getConfig(editor.getScene())).writeString(get("new"),false);
					editor.updateConfig();
					editor.toast("Scene Config Changed...");
				break;
				case ITEM_DELETED:
					Actor actor1 = (values.containsKey("actor") && values.get("actor") instanceof Actor) ?((Actor)values.get("actor")) : editor.findActor(get("name"));
					if(actor1 == null){ editor.toast("Item not found, step skipped : "+get("name")); return this; }
					actor1.remove();
					if(editor.getSelectedActor()!=null && editor.getSelectedActor().getName().equals(actor1.getName()))
						editor.selectActor(null);
					editor.toast("Item Removed : "+actor1.getName());
				break;
				case RENAME_SCENE:
					editor.getProject().renameScene(editor.getScene(),get("new"));
					editor.setScene(get("new"));
				break;
				case BATCH:
					for(EditorAction sub : subs())
						sub.redo();
					com.star4droid.star2d.editor.Hierarchy.relink(editor);
					com.star4droid.star2d.editor.Hierarchy.refreshLists(editor);
				break;
			}
			if(get("set").toString().equals("true") && editor.findActor(get("name")) instanceof EditorItem)
				((EditorItem)editor.findActor(get("name"))).setProperties(propertySet);
			if(get("update").equals("true"))
				editor.updateProperties();
			if(get("updateItem").equals("true") && editor.findActor(get("name")) instanceof EditorItem)
				((EditorItem)editor.findActor(get("name"))).update();
		} catch(Exception exception){
			exception.printStackTrace();
			com.star4droid.star2d.logs.Logs.editor("undo", "redo err : action : \n"+toString()+"\n"+Utils.getStackTraceString(exception)+"\n"+"__".repeat(5)+"\n");
			editor.toast("Redo Error : "+exception.toString());
		}
		return this;
	}
	
	private List<EditorAction> subs(){
		if(subs == null){
			subs = new ArrayList<>();
			try {
				String[] list = new com.google.gson.Gson().fromJson(get("subs"), String[].class);
				if(list != null)
					for(String json : list)
						subs.add(getFrom(json, editor));
			} catch(Exception ex){
				com.star4droid.star2d.logs.Logs.editor("undo", "batch parse error : "+ex+"\n");
			}
		}
		return subs;
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
			case "CAMERA":
			item = new EditorCameraItem(editor);
			break;
			case "MAP":
			item = new EditorMapItem(editor);
			break;
			case "GROUP":
			item = new EditorGroupItem(editor);
			break;
			case "UIHOLDER":
			item = new EditorUIHolderItem(editor);
			break;
		}
		
		if (item == null){
			editor.toast("null item in editor action...");
			return null;
		}
		if(propertySet==null){
			com.star4droid.star2d.logs.Logs.editor("undo", "\nnull props at editor action");
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
		EditorAction action = propertiesChangedQuiet(editor,name,message,values,old);
		if(action != null)
			editor.pushUndoEvent(action);
		// nothing changed: a harmless action that is never pushed, so callers can chain .setItemProperties() etc. without a NullPointerException
		return action != null ? action : new EditorAction(editor);
	}
	
	/** Like {@link #propertiesChanged} but the action is only returned (not pushed), to be part of a {@link #batch}. */
	public static EditorAction propertiesChangedQuiet(LibgdxEditor editor,String name,String message,String[] values,String[] old){
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
		return action;
	}
	
	/** One property changed, the new or the old value may be empty (the normal factory skips empty values). Pushed. */
	public static EditorAction propertyChangedRaw(LibgdxEditor editor,String name,String message,String key,String newValue,String oldValue){
		EditorAction action = propertyChangedQuiet(editor,name,message,key,newValue,oldValue);
		if(action != null)
			editor.pushUndoEvent(action);
		return action;
	}
	
	/** Like {@link #propertyChangedRaw} but only returned (for a {@link #batch}). */
	public static EditorAction propertyChangedQuiet(LibgdxEditor editor,String name,String message,String key,String newValue,String oldValue){
		if(newValue.equals(oldValue)) return null;
		EditorAction action = new EditorAction(editor,ACTION.CHANGE_PROPERTY);
		PropertySet<String,Object> ps = new PropertySet<>(),
			oldP = new PropertySet<>();
		ps.put(key,newValue);
		oldP.put(key,oldValue);
		action.put("message",message);
		action.put("name",name);
		action.put("new",ps.toString());
		action.put("old",oldP.toString());
		return action;
	}
	
	/** Several actions as ONE undo step (undone in reverse order). The sub actions must not be pushed themselves. */
	public static EditorAction batch(LibgdxEditor editor,String message,List<EditorAction> list){
		EditorAction action = new EditorAction(editor,ACTION.BATCH);
		action.subs = new ArrayList<>(list);
		ArrayList<String> json = new ArrayList<>();
		for(EditorAction sub : list)
			json.add(sub.toString());
		action.put("message",message);
		action.put("subs",new com.google.gson.Gson().toJson(json));
		return action;
	}
	
	/** Item added, returned only (for a {@link #batch}). */
	public static EditorAction itemAddedQuiet(LibgdxEditor editor,Actor actor){
		EditorAction action = new EditorAction(editor,ACTION.ITEM_ADD);
		action.put("name",actor.getName());
		action.put("actor",actor);
		action.put("item",PropertySet.getPropertySet(actor));
		return action;
	}
	
	/** Item removed, returned only (for a {@link #batch}). */
	public static EditorAction itemRemovedQuiet(LibgdxEditor editor,Actor actor){
		EditorAction action = new EditorAction(editor,ACTION.ITEM_DELETED);
		action.put("name",actor.getName());
		action.put("actor",actor);
		action.put("item",PropertySet.getPropertySet(actor).toString());
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
		SCENE_CONFIG,ITEM_DELETED,RENAME_SCENE,BATCH,NO_ACTION
	}
}