package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Utils;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.inputs.CheckInput;
import com.star4droid.star2d.editor.ui.sub.inputs.FloatInput;
import com.star4droid.star2d.editor.ui.sub.inputs.SpinnerInput;

public class SettingsDialog extends VisDialog {
	TestApp app;
	FloatInput logicWidth,logicHeight;
	public SettingsDialog(Stage stage,TestApp app){
		super("Settings");
		this.app = app;
		Preferences preferences = app.preferences;
		VisTable table = new VisTable();
		CheckInput codeCompletion = new CheckInput();
		codeCompletion.setNameText("Code Completion");
		codeCompletion.setValue(String.valueOf(preferences.getBoolean("Auto Completion",true)));
		table.add(codeCompletion).padTop(10).row();
		
		SpinnerInput compiler = new SpinnerInput();
		compiler.setNameText("Compiler");
		compiler.setData("javac","ecj");
		compiler.setValue(preferences.getString("compiler","javac"));
		table.add(compiler).padTop(10).row();
		
		CheckInput saveUndoRedo = new CheckInput();
		saveUndoRedo.setNameText("Save Undo/Redo");
		saveUndoRedo.setValue(String.valueOf(preferences.getBoolean("SaveUndoRedo",true)));
		table.add(saveUndoRedo).padTop(10).row();
		
		//Controls of the editor position...
		SpinnerInput layoutType = new SpinnerInput();
		layoutType.setNameText("Control Position");
		ControlLayer.ControlType[] types = ControlLayer.ControlType.values();
		String[] list = new String[types.length];
		for(int x=0; x < list.length; x++)
			list[x] = types[x].name();
		layoutType.setData(list);
		layoutType.setValue(preferences.getString("Control Position",ControlLayer.ControlType.TOP_AND_BOTTOM.name()));
		table.add(layoutType).padTop(10).row();
		
		//save & cancel
		VisTextButton okBtn = new VisTextButton("Save"),
				cancel = new VisTextButton("Cancel");
		okBtn.addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				preferences.putBoolean("Auto Completion",codeCompletion.getValue().equals("true"))
					.putBoolean("SaveUndoRedo",saveUndoRedo.getValue().equals("true"))
					.putString("Control Position",layoutType.getValue())
					.putString("compiler",compiler.getValue()).flush();
				if(app.getEditor()!=null){
					try {
						app.getEditor().setLogicalWH(Utils.getFloat(logicWidth.getValue()),Utils.getFloat(logicHeight.getValue()));
						app.getEditor().saveConfig();
					} catch(Exception e){
						app.toast("logic change error : \n"+e.toString());
					}
				}
				hide();
			}
		});
		cancel.addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				hide();
			}
		});
		
		logicWidth = new FloatInput();
		logicHeight = new FloatInput();
		logicWidth.setNameText("Logic Width");
		logicHeight.setNameText("Logic Height");
		
		table.add(logicWidth).padTop(10).row();
		table.add(logicHeight).padTop(10).row();
		
		VisTable okCancel = new VisTable();
		okCancel.add(okBtn).growX().minWidth(120).pad(9);
		okCancel.add(cancel).growX().minWidth(120).pad(5);
		table.add(okCancel).growX().row();
		table.add().height(5);
		VisScrollPane scrollPane = new VisScrollPane(table);
		scrollPane.setScrollingDisabled(false,false);
		scrollPane.setScrollbarsOnTop(true);
		scrollPane.setScrollbarsVisible(false);
		add(scrollPane);
		setResizable(true);
	}
	
	private void refreshEditorFields(){
		logicHeight.setVisible(app.getEditor()!=null);
		logicWidth.setVisible(app.getEditor()!=null);
		if(app.getEditor()!=null){
			// width & height inverted on landscape mode...
			logicHeight.setNameText(app.getEditor().isLandscape() ? "Logic Height" : "Logic Width");
			logicWidth.setNameText(app.getEditor().isLandscape() ? "Logic Width" : "Logic Height");
			PropertySet<String,Object> ps = app.getEditor().getConfig();
			logicWidth.setValue(ps.getString("logicWidth"));
			logicHeight.setValue(ps.getString("logicHeight"));
		}
	}
	
	@Override
	public VisDialog show(Stage arg0, Action arg1) {
		refreshEditorFields();
		return super.show(arg0, arg1);
	}
}