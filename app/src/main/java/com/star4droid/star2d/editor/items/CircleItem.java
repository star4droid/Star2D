package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.Utils;
import java.io.File;
import java.util.ArrayList;

public class CircleItem extends Image implements EditorItem {
    PropertySet<String, Object> propertySet;
    float[] offset = new float[]{0, 0};
    Body body;
    String tint = "#FFFFFF";
    LibgdxEditor editor;

    public CircleItem(final LibgdxEditor libgdxEditor) {
        super(new Texture(Gdx.files.internal("images/logo.png")));
        this.editor = libgdxEditor;
        setSize(250, 250);
        libgdxEditor.addActor(this);
        setDrawable(new TextureRegionDrawable(new Texture(Gdx.files.internal("images/logo.png"))));
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                libgdxEditor.selectActor(CircleItem.this);
            }
        });
		//debug();
    }

    @Override
    public PropertySet<String, Object> getPropertySet() {
        return propertySet;
    }

    @Override
    public void update() {
        if (getStage() == null) return;
        
        // Set size based on radius
        float radius = propertySet.getFloat("radius");
        setSize(radius * 2, radius * 2);
        setOrigin(getWidth() * 0.5f, getHeight() * 0.5f);
        
        // Position calculation
        float x = propertySet.getFloat("x");
        float y = getStage().getHeight() - propertySet.getFloat("y") - getHeight();
        setPosition(x, y);
        
        // Common properties
        setZIndex(propertySet.getInt("z"));
        setRotation(-propertySet.getFloat("rotation"));
        setVisible(propertySet.getString("Visible").equals("true"));
        
        // Physics setup
        offset[0] = propertySet.getFloat("ColliderX") * -1;
        offset[1] = propertySet.getFloat("ColliderY") * -1;

        if (body == null) {
            BodyDef def = new BodyDef();
            def.type = BodyDef.BodyType.StaticBody;
            def.position.set(0, 0);
            body = editor.getWorld().createBody(def);
            
            CircleShape shape = new CircleShape();
            shape.setRadius(radius);
            
            FixtureDef fx = new FixtureDef();
            fx.shape = shape;
            body.createFixture(fx);
            shape.dispose();
        } else {
            ((CircleShape) body.getFixtureList().get(0).getShape()).setRadius(radius);
        }
        
        // Update body position/rotation
        Vector2 position = new Vector2(
            offset[0] + x + radius,
            offset[1] + y + radius
        );
        body.setTransform(position, (float) Math.toRadians(-propertySet.getFloat("rotation")));
		if(!propertySet.getString("Tint").equals(tint)){
		    setColor(propertySet.getColor("Tint"));
		    tint = propertySet.getString("Tint");
		}
    }

    public LibgdxEditor getEditor() {
        return editor;
    }

    public CircleItem setDefault() {
        propertySet = PropertySet.getDefualt(this, "circle.json");
        return this;
    }
	
	@Override
	public Body getBody() {
		return body;
	}

    @Override
    public void setProperties(PropertySet<String, Object> propertySet) {
        this.propertySet = propertySet;
        PropertySet<String, Object> temp = PropertySet.getDefualt(this, "circle.json");
		
		if(!propertySet.getString("Shape").equals("Circle")){
			if(editor!=null){
				try {
					remove();
					EditorItem newBody = propertySet.getString("Shape").equals("Circle") ? new CircleItem(editor) : new BoxItem(editor);
					editor.addActor((Actor)newBody);
					newBody.setProperties(propertySet);
					editor.selectActor((Actor)newBody);
					newBody.update();
				} catch(Exception e){}
			}
			return;
		}
		
        // Merge properties
        for (String s : temp.keySet()) {
            if (!propertySet.containsKey(s)) {
                propertySet.put(s, temp.get(s));
            }
        }

        // Clean up unused properties
        ArrayList<String> toDel = new ArrayList<>();
        for (String key : propertySet.keySet()) {
            if (!temp.containsKey(key)) {
                toDel.add(key);
            }
        }
        for (String key : toDel) propertySet.remove(key);

        update();

        // Handle image texture
        if (!this.propertySet.getString("image").equals("")) {
            String img = this.editor.getProject().getImagesPath() + 
                        this.propertySet.getString("image").replace(Utils.seperator, "/");
            if (new File(img).exists()) {
                float rx = this.propertySet.getFloat("tileX");
                float ry = this.propertySet.getFloat("tileY");
                Utils.setImageFromFile(this, img, rx, ry);
            }
        }
    }

    @Override
    public String getTypeName() {
        return "Circle";
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        setOrigin(getWidth() * 0.5f, getHeight() * 0.5f);
    }

}