package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Helpers.PropertySet;

public class LightSettingsDialog {

    public static void showFor(Project project, String scene) {
        if (!VisUI.isLoaded()) {
            throw new IllegalStateException("VisUI must be loaded before using this dialog.");
        }

        Skin skin = VisUI.getSkin();

        String config = FileUtil.readFile(project.getConfig(scene));
        PropertySet<String, Object> propertySet = config.isEmpty() ? new PropertySet<>() : PropertySet.getFrom(config);

        Window dialog = new Window("Light Settings", skin);
        dialog.pad(20).defaults().pad(5);

        Table contentTable = new Table();
        contentTable.defaults().fillX().expandX();

        // Add blur number field
        TextField blurNumField = new TextField(propertySet.containsKey("blur_number") ? propertySet.getString("blur_number") : "1", skin);
        blurNumField.setMessageText("Blur Number");
        contentTable.add(new CheckBox("Enable Blur", skin)).row();
        VisLabel blurNoLabel = new VisLabel("Blur Number");
		contentTable.add(blurNoLabel).growX().row();
		contentTable.add(blurNumField).row();

        // Add checkboxes for various settings
        addCheckBox(contentTable, "Use Diffuse Light", propertySet, skin);
        addCheckBox(contentTable, "Culling", propertySet, skin);
        addCheckBox(contentTable, "Gamma Correction", propertySet, skin);
        addCheckBox(contentTable, "Shadows", propertySet, skin);
		
		// Add ambient light color picker
        final String ambientKey = "ambient_light";
        String ambientColorHex = propertySet.containsKey(ambientKey) ? (String) propertySet.get(ambientKey) : "#FFFFFF";
		TextButton ambientButton = new TextButton("Ambient Light (" + ambientColorHex + ")", skin);
		
		Window colorWindow = new Window("Color Picker",skin);
            ColorPicker colorPicker = new ColorPicker("Pick Ambient Color");
            colorPicker.setColor(com.badlogic.gdx.graphics.Color.valueOf(ambientColorHex));
            colorPicker.setListener(new ColorPickerListener() {
                @Override
                public void finished(com.badlogic.gdx.graphics.Color color) {
                    String hex = color.toString().toUpperCase();//String.format("#%06X", (0xFFFFFF & color.toIntBits()));
                    propertySet.put(ambientKey, hex);
                    ambientButton.setText("Ambient Light (" + hex + ")");
					ambientButton.setVisible(true);
				}
				@Override
				public void canceled(Color arg0) {
					ambientButton.setVisible(true);
				}
				@Override
				public void changed(Color arg0) {}
				@Override
				public void reset(Color arg0, Color arg1) {}
            });
			
        colorWindow.add(colorPicker);
        
        ambientButton.addListener(event -> {
			if(!ambientButton.isVisible()) return false;
			ambientButton.setVisible(false);
            dialog.getStage().addActor(colorPicker);
            return false;
        });
        contentTable.add(ambientButton).colspan(2).row();

        // Save button
        TextButton saveButton = new TextButton("Save", skin);
        
		saveButton.addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				saveSettings(contentTable, propertySet);
            	FileUtil.writeFile(project.getConfig(scene), propertySet.toString());
            
            	if (LibgdxEditor.getCurrentEditor() != null) {
                	try {
                    	LibgdxEditor.getCurrentEditor().updateConfig();
                    	LibgdxEditor.getCurrentEditor().setupLight();
                	} catch (Exception e) {
                    	e.printStackTrace();
                	}
           	 }
            	
           	 dialog.remove();
			}
		});
		
		TextButton cancelButton = new TextButton("Cancel",skin);
		cancelButton.addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				dialog.remove();
			}
		});

        contentTable.add(saveButton).colspan(2).center().padTop(10).row();
		contentTable.add(cancelButton).colspan(2).center().padTop(10).row();

        dialog.add(contentTable).expand().fill();
        dialog.pack();
        //dialog.centerWindow();
		dialog.setKeepWithinStage(true);
        dialog.setMovable(true);
        dialog.setVisible(true);
		LibgdxEditor.getCurrentEditor().getUiStage().addActor(dialog);
		//dialog.setScale(3.5f);
		dialog.setPosition((dialog.getStage().getWidth()-dialog.getWidth())*0.5f,(dialog.getStage().getHeight()-dialog.getHeight())*0.5f);
    }

    private static void addCheckBox(Table table, String label, PropertySet<String, Object> propertySet, Skin skin) {
        CheckBox checkBox = new CheckBox(label, skin);
        checkBox.setChecked(Boolean.parseBoolean(propertySet.getOrDefault(label, "false").toString()));
        table.add(checkBox).colspan(2).row();
        propertySet.put(label, checkBox.isChecked() ? "true" : "false");
    }

    private static void saveSettings(Table table, PropertySet<String, Object> propertySet) {
        for (Actor actor : table.getChildren()) {
            if (actor instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) actor;
                propertySet.put(checkBox.getText().toString(), checkBox.isChecked() ? "true" : "false");
            } else if (actor instanceof TextField) {
                TextField textField = (TextField) actor;
                propertySet.put(textField.getMessageText(), textField.getText());
            }
        }
    }
}