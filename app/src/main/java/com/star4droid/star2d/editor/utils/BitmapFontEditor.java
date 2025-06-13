package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.inputs.*;
import java.lang.reflect.Field;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class BitmapFontEditor extends VisDialog {
	private static Class cls = FreeTypeFontGenerator.FreeTypeFontParameter.class;
	private static FreeTypeFontGenerator.FreeTypeFontParameter instance = new FreeTypeFontGenerator.FreeTypeFontParameter();
	Array<InputField> inputFields = new Array<>();
	PropertySet<String,Object> propertySet = null;
	private StringInput name;
	private CheckInput isRtl;
	private TestApp app;
	public DefaultInput fontInput;
	OnSave onSave;
	VisTable table;
	VisTextButton save,cancel;
	/*
    	* dir = directory to save the file (no need for it if the filehandle exists)
    	* filehandle = full filepath (null means the user should enter file name)
	*/
	public BitmapFontEditor(TestApp app){
		super(getTrans("bitmapFontEditor"));
		this.app = app;
		reset();
	}
	
	public BitmapFontEditor setData(FileHandle dir,FileHandle fileHandle){
		boolean first = true;
		FileHandle defaultFont = Gdx.files.absolute(app.getEditor().getProject().getPath()+"/DroidSans.ttf");
		if(!defaultFont.exists()){
			Gdx.files.internal("files/DroidSans.ttf").copyTo(defaultFont);
		}
		if(table==null)
			table = new VisTable();
		else first = false;
		try {
			if(fileHandle!=null && fileHandle.exists())
				propertySet = PropertySet.getFrom(fileHandle.readString());
		} catch(Exception e){}
		// the name of saved font
		if(name == null)
			name = new StringInput();
		name.setNameText(getTrans("fileName"));
		String newName = "font";
		int n = 1;
		while(dir!=null && dir.child(newName+n+".s2df").exists())
			n++;
		newName = newName+n;
		name.setValue(fileHandle!=null ? fileHandle.name() : newName);
		// disable when editing file is available
		name.setDisabled(fileHandle!=null);
		// field to select ttf font...
		if(fontInput==null)
			fontInput = new DefaultInput();
		fontInput.setNameText("TTF");
		fontInput.setValue(propertySet != null ? propertySet.getString("font") : "");
		final String root = dir != null ? dir.file().getAbsolutePath() : (fileHandle != null ? fileHandle.parent().file().getAbsolutePath() : app.getEditor().getProject().getPath());
		fontInput.value.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				app.getEditor().getFilePicker(true).setRoot(root).setExtensions("ttf","otf").setOnPick((fhandle,path)->{
					fontInput.setValue(path);
				});
			}
		});
		if(isRtl==null)
			isRtl = new CheckInput();
		isRtl.setNameText(getTrans("Support RTL"));
		isRtl.setValue(propertySet!=null ? propertySet.getString("rtl") : "false");
		if(first){
			table.add(name).row();
			table.add(fontInput).row();
			table.add(isRtl).row();
		}
		for(Field field:cls.getFields()){
			try {
				boolean isEnum = false;
				try {
					isEnum = field.get(instance).getClass().isEnum();
				} catch(Exception ignored){}
				InputField input = null;
				String type = field.getType().getName().toLowerCase();
				if(!first){
					for(InputField inputField:inputFields){
						if(inputField.getFieldName().equals(field.getName()))
							input = inputField;
					}
				} else if(type.contains("float")){
					input = new FloatInput();
				} else if(type.equals("int") || type.contains("integer")){
					input = new IntInput();
				} else if(isEnum){
					input = new SpinnerInput();
					Class enm = field.get(instance).getClass();
					Field[] fields = enm.getFields();
					String[] data = new String[fields.length];
					for(int x = 0; x < data.length; x++)
						data[x] = fields[x].getName();
					((SpinnerInput)input).setData(data);
					input.setValue((propertySet==null || !propertySet.containsKey(field.getName()))?field.get(instance).toString():propertySet.getString(field.getName()));
				} else if(type.contains("color")){
					input = new ColourInput(app.getControlLayer());
				} else if(type.equals("boolean")){
					input = new CheckInput();
				} else if(type.contains("string")){
					//input = new StringInput();
				}
				if(input!=null){
					input.setNameText(field.getName());
					//((Actor)input).setUserObject(field);
					if(!isEnum)
						input.setValue(propertySet == null ? field.get(instance).toString() : propertySet.getString(field.getName()));
					if(first){
						inputFields.add(input);
						table.add((Actor)input).growX().padTop(8).row();
					}
				}// else if(!type.contains("string")) Gdx.files.external("logs/fields.txt").writeString("unknown : type : "+field.getType()+",name : "+field.getName()+",enum : "+field.getClass().isEnum()+"\n",true);
			} catch(Exception ex){
				Gdx.files.external("logs/fields.txt").writeString("Error : "+ex.toString()+",field : "+field.getName()+"\n",true);
			}
		}
		if(save==null){
			save = new VisTextButton(getTrans("save"));
			save.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					if(name.getValue().equals("") || fontInput.getValue().equals("")){
						app.toast("Name and Font can\'t be empty!!");
						return;
					}
					if(propertySet==null)
					propertySet = new PropertySet<>();
					propertySet.put("name",name.getValue());
					propertySet.put("rtl",isRtl.getValue());
					propertySet.put("font",fontInput.getValue());
					for(InputField field:inputFields){
						propertySet.put(field.getFieldName(),field.getValue());
					}
					if(fileHandle!=null){
						fileHandle.writeString(propertySet.toString(),false);
						app.updateFont(fileHandle.path());
						app.toast("saved to :\n"+fileHandle.name());
						app.getEditor().updateChilds();
						if(onSave!=null)
						onSave.save(fileHandle.name(),propertySet);
						} else try {
						FileHandle path = dir.child(name.getValue()+".s2df");
						if(path.exists()){
							app.toast("There\'s file with the same name!!");
							return;
						}
						path.writeString(propertySet.toString(),false);
						app.getEditor().updateChilds();
						app.toast("saved to :\n"+path.name());
						app.getFileBrowser().refreshFileList();
						if(onSave!=null)
						onSave.save(name.getValue()+".s2df",propertySet);
						} catch(Exception e){
						app.toast("Error : "+e.toString());
						return;
					}
					hide();
				}
			});
		}
		if(cancel==null){
			cancel = new VisTextButton(getTrans("cancel"));
			cancel.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					hide();
				}
			});
		}
		if(first){
			table.add(save).growX().padTop(8).row();
			table.add(cancel).growX().padTop(8);
			VisScrollPane scrollPane = new VisScrollPane(table);
			scrollPane.setScrollbarsVisible(false);
			add(scrollPane);
		}
		return this;
	}
	
	@Override
	public VisDialog show(Stage stage) {
		super.show(stage);
		setHeight(Gdx.graphics.getHeight()*0.75f);
		setWidth(Gdx.graphics.getWidth()*0.85f);
		toFront();
		centerWindow();
		return this;
	}
	
	public BitmapFontEditor setTTF(String name){
		fontInput.setValue(name);
		return this;
	}
	
	public BitmapFontEditor setOnSave(OnSave save){
		this.onSave = save;
		return this;
	}
	
	public interface OnSave {
		public void save(String s2fName,PropertySet<String,Object> propertySet);
	}
}