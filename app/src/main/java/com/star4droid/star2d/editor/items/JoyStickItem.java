package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.Utils;
import java.io.File;

public class JoyStickItem extends Touchpad implements EditorItem {
    PropertySet<String, Object> propertySet;
    LibgdxEditor editor;
    float touchDownX, touchDownY;

    public JoyStickItem(LibgdxEditor editor) {
        super(5,createDefaultStyle(editor));
        this.editor = editor;
        setupListeners();
        editor.addActor(this);
		//debug();
    }

    private static TouchpadStyle createDefaultStyle(LibgdxEditor editor) {
        TouchpadStyle style = new TouchpadStyle();
        style.background = new TextureRegionDrawable(new Texture(Gdx.files.internal("images/joyback.png")));
        style.knob = new TextureRegionDrawable(new Texture(Gdx.files.internal("images/joybtn.png")));
        return style;
    }

    private void setupListeners() {
        addListener(new ItemClickListener(this, editor));
    }

    @Override
    public void update() {
        if (propertySet == null) return;

        // Set position and size
        float width = propertySet.getFloat("width");
        float height = propertySet.getFloat("height");
        float x = propertySet.getFloat("x");
        float y = editor.getHeight() - propertySet.getFloat("y") - height;

        setSize(width, height);
        setPosition(x, y);
        setZIndex(propertySet.getInt("z"));
        setVisible(propertySet.getString("Visible").equals("true"));
        
        // Update knob size
        getStyle().knob.setMinWidth(width * 0.35f);
        getStyle().knob.setMinHeight(height * 0.35f);
    }

    @Override
    public void setProperties(PropertySet<String, Object> properties) {
        this.propertySet = properties;
        PropertySet<String, Object> defaults = PropertySet.getDefualt(this, "joystick.json");

        // Merge properties
        for (String key : defaults.keySet()) {
            if (!propertySet.containsKey(key)) {
                propertySet.put(key, defaults.get(key));
            }
        }

        // Load images
        loadImage("Button Image", true);
        loadImage("Pad Image", false);
        update();
    }

    private void loadImage(String propertyKey, boolean isKnob) {
        String imagePath = editor.getProject().getImagesPath() + 
                         propertySet.getString(propertyKey).replace(Utils.seperator, "/");
        File file = new File(imagePath);
        if (file.exists() && !file.isDirectory()) {
            Texture texture = new Texture(Gdx.files.absolute(imagePath));
            TextureRegionDrawable drawable = new TextureRegionDrawable(texture);
            
            if (isKnob) {
                getStyle().knob = drawable;
            } else {
                getStyle().background = drawable;
            }
        }
    }

    @Override
    public PropertySet<String, Object> getPropertySet() {
        return propertySet;
    }

    public JoyStickItem setDefault() {
        propertySet = PropertySet.getDefualt(this, "joystick.json");
        return this;
    }

    @Override
    public String getTypeName() {
        return "Joystick";
    }

    public LibgdxEditor getEditor() {
        return editor;
    }
}