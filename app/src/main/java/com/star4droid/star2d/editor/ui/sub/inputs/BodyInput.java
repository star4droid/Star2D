package com.star4droid.star2d.editor.ui.sub.inputs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.template.Utils.Utils;

public class BodyInput extends VisTable implements InputField {
	public VisTextButton name,value;
	String valueString = "";
	public boolean isSingle = false,mustSelect = false;
	public Array<String> ignoredBodies = new Array<>();
	Runnable onChange;
	public BodyInput(TestApp app){
		super();
		setBackground(VisUI.getSkin().getDrawable("window-bg"));
		name = new VisTextButton("Name");
		value = new VisTextButton("[Choose]");
		name.setBackground(VisUI.getSkin().getDrawable("separator"));
		value.setBackground(VisUI.getSkin().getDrawable("separator"));
		add().width(8);
		add(name).growX();
		add().width(8);
		add(value).growX();
		add().width(8);
		Array<VisCheckBox> checkBoxes = new Array<>();
		value.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                VisDialog dialog = new VisDialog("Select Body");
				dialog.setSize(Gdx.graphics.getWidth()*0.45f,Gdx.graphics.getHeight()*0.75f);
				VisTable table = new VisTable();
				VisScrollPane scrollPane = new VisScrollPane(table);
				table.setFillParent(true);
				dialog.add(scrollPane).minWidth(250).maxHeight(Gdx.graphics.getHeight()*0.7f).grow();
				checkBoxes.clear();
				for(String body:app.getEditor().getBodiesList()){
					if(ignoredBodies.contains(body,false))
						continue;
					VisCheckBox checkBox = new VisCheckBox(body,valueString.equals(body) || valueString.contains("("+body+")"));
					checkBoxes.add(checkBox);
					table.add(checkBox).growX().row();
					if(isSingle)
						checkBox.addListener(new ChangeListener() {
							@Override
							public void changed (ChangeEvent event, Actor actor) {
								if(checkBox.isChecked())
									for(VisCheckBox ch:checkBoxes){
										if(!ch.getText().equals(checkBox.getText()))
											ch.setChecked(false);
									}
							}
						});
					
				}
				VisTextButton okBtn = new VisTextButton("Select"),
					cancelBtn = new VisTextButton("Cancel");
				okBtn.addListener(new ClickListener() {
					@Override
					public void clicked(InputEvent event, float x, float y) {
						String result = "";
						int num = 0;
						for(VisCheckBox ch:checkBoxes){
							if(ch.isChecked()){
								result += "("+ch.getText().toString()+")";
								num++;
								if(isSingle) break;
							}
						}
						if(num == 0 && mustSelect){
							app.toast("Select At least One Item!");
							return;
						}
						valueString = (isSingle ? result.replace("(","").replace(")","") : result);
						if(isSingle)
		                    value.setText(valueString);
						//Gdx.files.external("logs/bodyInput.txt").writeString("set : "+isSingle+", name : "+name.getText().toString()+", value : "+valueString,true);
						dialog.hide();
						if(onChange!=null)
							onChange.run();
					}
				});
				cancelBtn.addListener(new ClickListener() {
					@Override
					public void clicked(InputEvent event, float x, float y) {
						dialog.hide();
					}
				});
				dialog.row();
				dialog.center();
				dialog.add(okBtn);
				dialog.add().width(8);
				dialog.add(cancelBtn);
				dialog.add().width(8);
				dialog.show(getStage());
				dialog.row();
            }
        });
	}
	
	public BodyInput setMustSelect(boolean b){
		this.mustSelect = b;
		return this;
	}
	
	public BodyInput setIsSingle(boolean isSingle){
		this.isSingle = isSingle;
		return this;
	}
	
	@Override
	public String getFieldName() {
		return name.getText().toString();
	}
	public BodyInput ignoreBodies(boolean clear,String... bodies){
		if(clear)
			ignoredBodies.clear();
		if(bodies!=null)
		    for(String body:bodies)
			    ignoredBodies.add(body);
		return this;
	}
	
	private Drawable getDrawable(String body,LibgdxEditor editor){
		Actor actor = editor.findActor(body);
		if(actor != null && actor instanceof Image){
			return ((Image)actor).getDrawable();
		}
		return Utils.getDrawable(Utils.internal("images/logo.png"));
	}
	
	@Override
	public void setNameText(String nm) {
		name.setText(nm);
	}

	@Override
	public void setValue(String value) {
		valueString = value;
		if(!(value.contains("(") || value.equals("")))
		    this.value.setText(value);
		//Gdx.files.external("logs/bodyInput.txt").writeString("\n value : "+value+", set : "+(!value.contains("("))+", name : "+name.getText().toString(),true);
	}

	@Override
	public String getValue() {
	    return valueString;
	}

	@Override
	public void setOnChange(Runnable runnable) {
		this.onChange = runnable;
	}
	
}