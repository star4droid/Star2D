package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.Utils;
import java.util.ArrayList;

public class CustomItem extends Image implements EditorItem {
    PropertySet<String, Object> propertySet;
    LibgdxEditor editor;
    Body body;
    String tint = "#FFFFFF";
    ShapeRenderer shapeRenderer;
	float[] offset=new float[]{0,0};
    ArrayList<Vector2> points = new ArrayList<>();

    public CustomItem(final LibgdxEditor libgdxEditor) {
        super(new Texture(Gdx.files.internal("images/logo.png")));
        this.editor = libgdxEditor;
        try {
            shapeRenderer = new ShapeRenderer();
        } catch(Exception e){}
        setSize(250, 250);
        libgdxEditor.addActor(this);
        
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                libgdxEditor.selectActor(CustomItem.this);
            }
        });
		//debug();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        batch.end();
        if(shapeRenderer == null)
            shapeRenderer = new ShapeRenderer();
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.setTransformMatrix(batch.getTransformMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.GREEN);
        
        if (!points.isEmpty()) {
            Vector2 first = points.get(0);
            Vector2 prev = first;
            
            for (int i = 1; i < points.size(); i++) {
                Vector2 current = points.get(i);
                shapeRenderer.line(prev.x, prev.y, current.x, current.y);
                prev = current;
            }
            shapeRenderer.line(prev.x, prev.y, first.x, first.y);
        }
        
        shapeRenderer.end();
        batch.begin();
    }

    @Override
    public void update() {
        if (getStage() == null) return;

        // Update dimensions and position
        float width = propertySet.getFloat("width");
        float height = propertySet.getFloat("height");
        setSize(width, height);
        setOrigin(width * 0.5f, height * 0.5f);
        
        float x = propertySet.getFloat("x");
        float y = getStage().getHeight() - propertySet.getFloat("y") - height;
        setPosition(x, y);
        
        // Common properties
        setZIndex(propertySet.getInt("z"));
        setRotation(-propertySet.getFloat("rotation"));
        setVisible(propertySet.getString("Visible").equals("true"));
		offset[0] = propertySet.getFloat("ColliderX")*-1;
    	offset[1] = propertySet.getFloat("ColliderY")*-1;
        
        // Parse and scale points
        points.clear();
        String pointsStr = propertySet.getString("Points");
        for (String point : pointsStr.split("-")) {
            String[] coords = point.split(",");
            if (coords.length == 2) {
				try {
                	float px = Utils.getFloat(coords[0]) * width - width*0.5f;
                	float py = (1 - Utils.getFloat(coords[1])) * height - height*0.5f; // Flip Y-axis
                	points.add(new Vector2(px, py));
				} catch(Exception e){}
            }
        }
		
        // Update physics body
        updatePhysicsBody();
		body.setTransform(new Vector2((offset[0]+x+(getWidth()*0.5f)),(offset[1]+y+(getHeight()*0.5f))),(float)Math.toRadians(-propertySet.getFloat("rotation")));
		if(!propertySet.getString("Tint").equals(tint)){
		    setColor(propertySet.getColor("Tint"));
		    tint = propertySet.getString("Tint");
		}
    }

    private void updatePhysicsBody() {
        //the else part disabled...
        if(body!=null){
            body.getWorld().destroyBody(body);
            body = null;
        }
        if (body == null) {
            BodyDef def = new BodyDef();
            def.type = BodyDef.BodyType.StaticBody;
            def.position.set(getX(), getY());
            body = editor.getWorld().createBody(def);
            
            ChainShape shape = new ChainShape();
            Vector2[] vertices = points.toArray(new Vector2[0]);
            shape.createLoop(vertices);
            
            FixtureDef fx = new FixtureDef();
            fx.shape = shape;
            body.createFixture(fx);
            shape.dispose();
        } else {
            ChainShape shape = (ChainShape) body.getFixtureList().get(0).getShape();
            Vector2[] vertices = points.toArray(new Vector2[0]);
            shape.createLoop(vertices);
        }
    }

    @Override
    public PropertySet<String, Object> getPropertySet() {
        return propertySet;
    }
	@Override
	public Body getBody() {
		return body;
	}

    @Override
    public void setProperties(PropertySet<String, Object> propertySet) {
        this.propertySet = propertySet;
        PropertySet<String, Object> temp = PropertySet.getDefualt(this, "custom.json");
		
		if(!propertySet.getString("Shape").equals("Custom")){
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
		
		if (!this.propertySet.getString("image").equals("")) {
			String img = this.editor.getProject().getImagesPath() + this.propertySet.getString("image").replace(Utils.seperator,"/");
			if (new java.io.File(img).exists()) {
				float rx = this.propertySet.getFloat("tileX");
				float ry = this.propertySet.getFloat("tileY");
				Utils.setImageFromFile(this, img, rx, ry);
			}
		}
        update();
    }

    @Override
    public String getTypeName() {
        return "Custom";
    }

    public CustomItem setDefault() {
        propertySet = PropertySet.getDefualt(this, "custom.json");
        return this;
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        setOrigin(getWidth() * 0.5f, getHeight() * 0.5f);
    }
}