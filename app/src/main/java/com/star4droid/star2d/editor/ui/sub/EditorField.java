package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.Array;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.sub.inputs.BodyInput;
import com.star4droid.star2d.editor.utils.BitmapFontEditor;
import com.star4droid.star2d.editor.utils.EditorAction;
import java.util.HashMap;
import com.star4droid.star2d.editor.ui.sub.inputs.*;
import com.star4droid.template.Utils.Utils;
import static com.star4droid.star2d.editor.utils.Lang.*;
public class EditorField {
	private InputField inputField;
	public static String allowedChars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_";
	TestApp app;
	private static String dragControlFields,disableOnPlayFields;
	private static HashMap<String,Object> spinnerMap;
	private boolean isFile, acceptValue = true;
	public EditorField(TestApp app,String name,String type){
		this.app = app;
		this.isFile = name.toLowerCase().equals("file");
		try {
			if(spinnerMap == null)
					spinnerMap = new Gson().fromJson(Utils.internal("spinners.json").readString(), new TypeToken<HashMap<String, Object>>() {}.getType());
		} catch(Exception e){
			app.toast("loading spinner map error : "+e.toString());
		}
		if(dragControlFields==null)
			dragControlFields = Gdx.files.internal("dragControl.json").readString();
		if(disableOnPlayFields==null)
			disableOnPlayFields = Gdx.files.internal("playOffProps.txt").readString();
		if(type.equals("script")){
			inputField = new DefaultInput(){
				{
					super.setValue("["+getTrans("choose")+"]");
				}
				@Override
				public void setValue(String value){}
			};
			
			((DefaultInput)inputField).value.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					/*
					app.getEditor().getFilePicker(true).setRoot(app.getEditor().getProject().getPath()+"/java/com/star4droid/Game/Scripts/").setExtensions("java").setOnPick((fHandle,path)->{
						
					});
					*/
					app.getBodyScriptSelector().refresh(app.getEditor().getScene()).setOnSelect((script)->{
						try {
							if(script.startsWith("/") && script.length() > 1)
								script = script.substring(1,script.length());
							/*
							if(!(script.contains("/") && script.contains("Script") && script.split("/").length == 2)){
								app.toast("Wrong Value!!");
								app.getEditor().getFilePicker(true);
							}
							*/
							PropertySet<String,Object> ps = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
							String old = ps.getString(getName());
							ps.put(getName(),script);
							app.getEditor().getSaveState();
							EditorAction.propertiesChanged(app.getEditor(),app.getEditor().getSelectedActor().getName(),getName(),new String[]{getName(),script},new String[]{getName(),old});
						} catch(Exception e){}
					}).setCheckedItem(PropertySet.getPropertySet(app.getEditor().getSelectedActor()).getString(getName())).show(app.getUiStage()).toFront();
				}
			});
		} else if(type.equals("body")){
			inputField = new BodyInput(app).setIsSingle(!name.equals("Collision")).setMustSelect(name.equals("Script"));
			//inputField.setValue("["+getTrans("choose")+"]");
			//acceptValue = false;
			inputField.setOnChange(()->{
				try {
					if(getName().equals("parent")){
						String result = inputField.getValue();
						if(result.equals(""))
							PropertySet.getPropertySet(app.getEditor().getSelectedActor()).setParent(null);
						if(!result.equals(""))
						for(Actor actor : app.getEditor().getActors()){
							if(actor instanceof EditorItem){
								if(actor.getName().equals(result)){
									PropertySet set=PropertySet.getPropertySet(actor);
									if(!PropertySet.getPropertySet(app.getEditor().getSelectedActor()).setParent(set)){
										app.toast("Can\'t set as parent!");
										return;
									}
									break;
								}
							}
						}
					}
					PropertySet<String,Object> ps = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
					String old = ps.getString(getName());
					ps.put(getName(),inputField.getValue());
					app.getEditor().getSaveState();
					if(getName().equals("parent"))
						app.getControlLayer().getBodiesList().update(true);
					EditorAction.propertiesChanged(app.getEditor(),app.getEditor().getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue()},new String[]{getName(),old});
				} catch(Exception e){}
			});
		} else if(type.toLowerCase().equals("file")){
			inputField = new DefaultInput();
			acceptValue = false;
			inputField.setValue("["+getTrans("choose")+"]");
			((DefaultInput)inputField).value.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
				    String root = getName().equals("font") ? app.getEditor().getProject().getPath() : app.getEditor().getProject().get("files");
				    String[] ext = getName().equals("font") ? new String[]{"s2df","ttf","otf"} : new String[]{"p","particle"};
					app.getEditor().getFilePicker(true).setRoot(root).setExtensions(ext).setOnPick((fhandle,path)->{
						PropertySet<String,Object> propertySet = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
						String old = propertySet.getString(getName());
						if(getName().equals("font") && (path.toLowerCase().endsWith(".ttf") || path.toLowerCase().endsWith(".otf") )){
							new ConfirmDialog("TTF/OTF Font","ttf/otf fonts isn\'t supported directly\ndo you want to create s2df font using this ?",ok->{
								app.getFileBrowser().getBitmapFontEditor().setData(fhandle.parent(),null).setOnSave((name,ps)->{
									if(!ok) return;
									name = (path.contains("/") ? path.substring(0,path.lastIndexOf("/")):"") + "/" + name;
									while(name.contains("//")) name = name.replace("//","/");
									propertySet.put(getName(),name);
									inputField.setValue(name);
									((EditorItem)(app.getEditor().getSelectedActor())).setProperties(propertySet);
									app.getEditor().getSaveState();
									EditorAction.propertiesChanged(app.getEditor(),app.getEditor().getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue()},new String[]{getName(),old}).setItemProperties();
								}).setTTF("/"+fhandle.name()).show(app.getUiStage());
							}).show(app.getUiStage());
							return;
						}
						propertySet.put(getName(),getName().equals("font") ? path : path.replace("/",Utils.seperator));
						inputField.setValue(path);
						((EditorItem)(app.getEditor().getSelectedActor())).setProperties(propertySet);
						app.getEditor().getSaveState();
						EditorAction.propertiesChanged(app.getEditor(),app.getEditor().getSelectedActor().getName(),getName(),new String[]{getName(),getName().equals("font")?inputField.getValue():inputField.getValue().replace("/",Utils.seperator)},new String[]{getName(),old}).setItemProperties();
					});
				}
			});
		} else if(type.toLowerCase().equals("color")){
			inputField = new ColourInput(app.getControlLayer());
			inputField.setOnChange(()->{
				LibgdxEditor editor = app.getEditor();
				PropertySet<String,Object> p = PropertySet.getPropertySet(editor.getSelectedActor());
				String old = p.getString(getName());
				p.put(inputField.getFieldName(),inputField.getValue());
				((EditorItem)(editor.getSelectedActor())).setProperties(p);
				editor.updateProperties();
				editor.getSaveState();
				EditorAction.propertiesChanged(editor,editor.getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue()},new String[]{getName(),old}).updateEditorProperties().setItemProperties();
			});
		} else if(type.equals("float")){
			inputField = new FloatInput();
			inputField.setOnChange(()->{
				try {
					LibgdxEditor editor = app.getEditor();
					float f= Utils.getFloat(inputField.getValue());
					PropertySet<String,Object> propertySet=PropertySet.getPropertySet(editor.getSelectedActor());
					String old = propertySet.getString(getName());
					propertySet.put(getName(),f);
					Array<String> oldValues = new Array<>(),
						newValues = new Array<>();
					newValues.add(getName());
					newValues.add(String.valueOf(f));
					oldValues.add(getName());
					oldValues.add(old);
					if(getName().equals("radius")&&propertySet.containsKey("Collider Radius")) {
						oldValues.add("Collider Radius");
						oldValues.add(propertySet.getString("Collider Radius"));
						propertySet.put("Collider Radius",f);
						newValues.add("Collider Radius");
						newValues.add(String.valueOf(f));
					}
					if(getName().equals("width")&&propertySet.containsKey("Collider Width")) {
						oldValues.add("Collider Width");
						oldValues.add(propertySet.getString("Collider Width"));
						propertySet.put("Collider Width",f);
						newValues.add("Collider Width");
						newValues.add(String.valueOf(f));
					}
					if(getName().equals("height")&&propertySet.containsKey("Collider Height")) {
						oldValues.add("Collider Height");
						oldValues.add(propertySet.getString("Collider Height"));
						propertySet.put("Collider Height",f);
						newValues.add("Collider Height");
						newValues.add(String.valueOf(f));
					}
					String[] oldArray = new String[oldValues.size],
					        newArray = new String[newValues.size];
					for(int i = 0; i < oldValues.size; i++){
					    oldArray[i] = oldValues.get(i);
					    newArray[i] = newValues.get(i);
					}
					//Gdx.files.external("floatUndo.txt").writeString("size old : "+oldValues.size+", new : "+newValues.size+"\n"+"_".repeat(15)+"\n",true);
					EditorAction action = EditorAction.propertiesChanged(editor,editor.getSelectedActor().getName(),getName(),newArray,oldArray);
					editor.updateProperties();
					editor.getSaveState();
					
					if(getName().equals("tileX")||getName().equals("tileY") || getName().toLowerCase().contains("progress")){
						((EditorItem)(editor.getSelectedActor())).setProperties(propertySet);
						action.setItemProperties();
					}
					else {
						((EditorItem)editor.getSelectedActor()).update();
						action.updateItemProperties();
					}
					editor.getSaveState();
				} catch(Exception ex){
				    Gdx.files.external("floatUndo.txt").writeString("error : "+Utils.getStackTraceString(ex)+"\n"+"_".repeat(15)+"\n",true);
				}
			});
		} else if(type.equals("image")){
			inputField = new DefaultInput();
			acceptValue = false;
			inputField.setValue("["+getTrans("choose")+"]");
			((DefaultInput)inputField).value.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					LibgdxEditor editor = app.getEditor();
					editor.getFilePicker(true).setRoot(editor.getProject().getImagesPath()).setShowImageIcons(true).setExtensions("jpg","png","jpeg").setOnPick((fhandle,path)->{
						PropertySet<String,Object> propertySet = PropertySet.getPropertySet(editor.getSelectedActor());
						String old = propertySet.getString(getName());
						propertySet.put(getName(),path.replace(Utils.seperator,"/"));
						inputField.setValue(propertySet.getString(getName()));
						((EditorItem)(editor.getSelectedActor())).setProperties(propertySet);
						editor.getSaveState();
						EditorAction.propertiesChanged(editor,editor.getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue().replace("/",Utils.seperator)},new String[]{getName(),old}).setItemProperties();
					});
				}
			});
		} else if(type.equals("boolean")){
			inputField = new CheckInput();
			inputField.setOnChange(()->{
				LibgdxEditor editor = app.getEditor();
				PropertySet<String,Object> propertySet = getPropertySet();
				String old = propertySet.getString(getName());
				propertySet.put(getName(),inputField.getValue());
				EditorAction action = EditorAction.propertiesChanged(app.getEditor(),app.getEditor().getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue()},new String[]{getName(),old});
				if(getName().equals("Visible")){
					((EditorItem)editor.getSelectedActor()).update();
					action.updateItemProperties();
				}
			});
		} else if(type.equals("spinner")){
			inputField = new SpinnerInput().setData(spinnerMap.get(name).toString().split(","));
			inputField.setOnChange(()->{
				String old = getPropertySet().getString(getName());
				getPropertySet().put(getName(),inputField.getValue());
				EditorAction action = EditorAction.propertiesChanged(app.getEditor(),app.getEditor().getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue()},new String[]{getName(),old});
				if(getName().equals("Light Type")||getName().equals("Shape")) 
					((EditorItem)(app.getEditor().getSelectedActor())).setProperties(
						getPropertySet()
					);
					action.setItemProperties();
				app.getEditor().getSaveState();
			});
		} else if(type.equals("points")){
			inputField = new DefaultInput();
			acceptValue = false;
			inputField.setValue("["+getTrans("choose")+"]");
			((DefaultInput)inputField).value.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					app.getControlLayer().getCustomColliderEditor().show(app.getUiStage());
				}
			});
		} else if(type.equals("string")){
			inputField = new StringInput();
			inputField.setOnChange(()->{
				LibgdxEditor editor = app.getEditor();
				if(getName().equals("name")) {
					if(editor.getBodiesList().contains(inputField.getValue())) {
						editor.toast("Name already used by anthor element..!!");
						inputField.setValue(getPropertySet().getString(getName()));
						return;
					}
				}
				if(getName().equals("name"))
				for(char c:inputField.getValue().toCharArray()){
					if(!allowedChars.contains(String.valueOf(c))){
						editor.toast("use A-Z a-z or _ , Not Allowed Char : "+c);
						inputField.setValue(getPropertySet().getString(getName()));
						return;
					}
				}
				PropertySet<String,Object> ps = getPropertySet();
				//change the script when name changed...
				String scr = ps.getString("Script");
				if((scr.contains("/") && scr.split("/")[0].equals(editor.getScene()) && scr.split("/")[1].equals(ps.getString("name"))) || (getName().equals("name") && scr.equals(ps.getString("name"))&&!ps.getString("name").equals(inputField.getValue()))){
					ps.put("Script",inputField.getValue());
				}
				String old = ps.getString(getName());
				try {
					if(getName().equals("name")){
						FileHandle newHandle = Gdx.files.absolute(editor.getProject().getBodyScriptPath(inputField.getValue(),editor.getScene()));
						Gdx.files.absolute(editor.getProject().getBodyScriptPath(ps.getString("name"),editor.getScene())).moveTo(
							newHandle
						);
						newHandle.writeString(newHandle.readString().replace(old+"Script",inputField.getValue()+"Script"),false);
						app.getControlLayer().getBodiesList().update(false);
					}
				} catch(Exception ex){}
				ps.put(getName(),inputField.getValue());
				((EditorItem)editor.getSelectedActor()).update();
				editor.getSaveState();
				if(getName().equals("name")){
					editor.getSelectedActor().setName(inputField.getValue());
					EditorAction.itemRenamed(editor,old,inputField.getValue()).updateEditorProperties();
				} else EditorAction.propertiesChanged(editor,editor.getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue()},new String[]{getName(),old}).updateItemProperties();
			});
		}
		if(inputField!=null)
			inputField.setNameText(name);
		else Gdx.files.external("logs/app_logs.txt").writeString("return null, type: " + type+", name : "+name+",spinner : "+type.equals("spinner")+"\n",true);
	}
	
	PropertySet<String,Object> getPropertySet(){
		PropertySet<String,Object> propertySet = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
		return propertySet;
	}
	
	public InputField getInputField(){
		return inputField;
	}
	
	public void setValue(String value){
	    if(acceptValue)
		    inputField.setValue(isFile ? value.replace(Utils.seperator,"/") : value);
		else inputField.setValue("["+getTrans("choose")+"]");
		if(getName().equals("parent") || getName().equals("Collision"))
			((BodyInput)inputField).ignoreBodies(true,app.getEditor().getSelectedActor().getName());
	}
	
	public void setFieldName(String name){
		this.inputField.setNameText(name);
	}
	
	public String getName(){
		return inputField.getFieldName();
	}
	
	public void refresh(){
		refresh(getPropertySet());
	}
	
	public void refresh(PropertySet<String,Object> propertySet){
		setValue(propertySet.getString(getName()));
	}
	
	private void fixParents(){
		HashMap<String,PropertySet> propsMap= new HashMap<>();
		for(Actor actor : app.getEditor().getActors()){
			if(actor instanceof EditorItem){
				PropertySet<String,Object> propertySet = PropertySet.getPropertySet(actor);
				propsMap.put(propertySet.getString("name"),propertySet);
			}
		}
		
		for(Actor actor : app.getEditor().getActors()){
			if(actor instanceof EditorItem){
				PropertySet set1= PropertySet.getPropertySet(actor);
				if(!set1.getString("parent").equals("")){
					if(propsMap.containsKey(set1.getString("parent"))){
						set1.setParent(propsMap.get(set1.getString("parent")));
					} else {
						Gdx.files.external("logs/parent.error.txt").writeString("parent not found for : "+set1.getString("name")+", parent name : "+set1.getString("parent")+"\n",true);
						set1.put("parent","");
					}
				}
			}
		}
	}
}