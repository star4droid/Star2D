package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
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
import com.star4droid.star2d.editor.utils.EditorAction;
import java.util.HashMap;
import com.star4droid.star2d.editor.ui.sub.inputs.*;
import com.star4droid.template.Utils.Utils;

public class EditorField {
	private InputField inputField;
	public static String allowedChars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_";
	TestApp app;
	private static String dragControlFields,disableOnPlayFields;
	private static HashMap<String,Object> spinnerMap;
	private boolean isFile;
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
		if(type.equals("body")){
			inputField = new BodyInput(app).setIsSingle(!name.equals("Collision")).setMustSelect(name.equals("Script"));
			inputField.setOnChange(()->{
				try {
					PropertySet<String,Object> ps = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
					String old = ps.getString(getName());
					ps.put(getName(),inputField.getValue());
					app.getEditor().getSaveState();
					EditorAction.propertiesChanged(app.getEditor(),app.getEditor().getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue()},new String[]{getName(),old});
				} catch(Exception e){}
			});
		} else if(type.toLowerCase().equals("file")){
			inputField = new DefaultInput();
			((DefaultInput)inputField).value.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					app.getEditor().getFilePicker(true).setRoot(app.getEditor().getProject().get("files")).setExtensions("p","particle").setOnPick((fhandle,path)->{
						PropertySet<String,Object> propertySet = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
						String old = propertySet.getString(getName());
						propertySet.put(getName(),path.replace("/",Utils.seperator));
						inputField.setValue(path);
						((EditorItem)(app.getEditor().getSelectedActor())).setProperties(propertySet);
						app.getEditor().getSaveState();
						EditorAction.propertiesChanged(app.getEditor(),app.getEditor().getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue().replace("/",Utils.seperator)},new String[]{getName(),old});
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
					oldValues.add(String.valueOf(f));
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
					EditorAction action = EditorAction.propertiesChanged(editor,editor.getSelectedActor().getName(),getName(),newValues.toArray(),oldValues.toArray());
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
				} catch(Exception ex){}
			});
		} else if(type.equals("image")){
			inputField = new DefaultInput();
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
						EditorAction.propertiesChanged(editor,editor.getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue().replace("/",Utils.seperator)},new String[]{getName(),old});
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
			((DefaultInput)inputField).value.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
						com.star4droid.star2d.Adapters.CustomColliderEditor.show();
					});
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
				if(getName().equals("name") && ps.getString("Script").equals(ps.getString("name"))&&!ps.getString("name").equals(inputField.getValue())){
					ps.put("Script",inputField.getValue());
					try {
						Gdx.files.absolute(editor.getProject().getBodyScriptPath(ps.getString("name"),editor.getScene())).moveTo(
							Gdx.files.absolute(editor.getProject().getBodyScriptPath(inputField.getValue(),editor.getScene()))
						);
					} catch(Exception ex){}
				}
				String old = ps.getString(getName());
				ps.put(getName(),inputField.getValue());
				((EditorItem)editor.getSelectedActor()).update();
				editor.getSaveState();
				if(getName().equals("name"))
					EditorAction.itemRenamed(editor,old,inputField.getValue()).updateEditorProperties();
				else EditorAction.propertiesChanged(editor,editor.getSelectedActor().getName(),getName(),new String[]{getName(),inputField.getValue()},new String[]{getName(),old});
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
		inputField.setValue(isFile ? value.replace(Utils.seperator,"/") : value);
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
}