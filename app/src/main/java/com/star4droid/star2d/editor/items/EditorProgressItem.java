package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.Utils;

public class EditorProgressItem extends Group implements EditorItem {
    private Image backgroundImg, progressImg;
    private LibgdxEditor editor;
    private PropertySet<String, Object> propertySet;
    private float max = 100, progress = 0;

    public EditorProgressItem(LibgdxEditor editor) {
        this.editor = editor;
        setupVisuals();
        setupListeners();
        editor.addActor(this);
		//debug();
    }

    private void setupVisuals() {
        Texture texture = new Texture(Gdx.files.internal("images/white.png"));
        backgroundImg = new Image(texture);
        progressImg = new Image(texture);
        addActor(backgroundImg);
        addActor(progressImg);
        backgroundImg.setColor(Color.LIGHT_GRAY);
        progressImg.setColor(Color.BLUE);
    }

    private void setupListeners() {
        addListener(new ClickListener() {
            @Override
            public void clicked (InputEvent event, float x, float y) {
                editor.selectActor(EditorProgressItem.this);
            }
        });
    }

    @Override
    public void update() {
        if (propertySet == null) return;

        // Update dimensions and position
        float width = propertySet.getFloat("width");
        float height = propertySet.getFloat("height");
        float x = propertySet.getFloat("x");
        float y = editor.getHeight() - propertySet.getFloat("y") - height;

        setSize(width, height);
        setPosition(x, y);
        setZIndex(propertySet.getInt("z"));
        setVisible(propertySet.getString("Visible").equals("true"));
        setRotation(-propertySet.getFloat("rotation"));

        // Update colors
        setBackColor(propertySet.getString("Background Color"));
        setProgressColor(propertySet.getString("Progress Color"));

        // Update progress values
        setMax(propertySet.getInt("Max"));
        setProgress(propertySet.getInt("Progress"));
    }

    @Override
    public void setProperties(PropertySet<String, Object> properties) {
        this.propertySet = properties;
        PropertySet<String, Object> defaults = PropertySet.getDefualt(this, "progress.json");

        // Merge properties
        for (String key : defaults.keySet()) {
            if (!propertySet.containsKey(key)) {
                propertySet.put(key, defaults.get(key));
            }
        }
        update();
    }

    public void setProgress(int progress) {
        this.progress = Math.min(progress, max);
        updateProgress();
    }

    public void setMax(int max) {
        this.max = max > 0 ? max : 1;
        updateProgress();
    }

    private void updateProgress() {
        float progressWidth = (progress / max) * getWidth();
        progressImg.setSize(progressWidth, getHeight());
    }

    public void setBackColor(String hex) {
        backgroundImg.setColor(Color.valueOf(hex));
    }

    public void setProgressColor(String hex) {
        progressImg.setColor(Color.valueOf(hex));
    }

    public void setBackColor(Color color) {
        backgroundImg.setColor(color);
    }

    public void setProgressColor(Color color) {
        progressImg.setColor(color);
    }

    @Override
    public PropertySet<String, Object> getPropertySet() {
        return propertySet;
    }
	
    public EditorProgressItem setDefault() {
        propertySet = PropertySet.getDefualt(this, "progress.json");
        return this;
    }

    @Override
    public String getTypeName() {
        return "Progress";
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        updateProgress();
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        backgroundImg.setSize(getWidth(), getHeight());
        updateProgress();
    }

    public LibgdxEditor getEditor() {
        return editor;
    }
}