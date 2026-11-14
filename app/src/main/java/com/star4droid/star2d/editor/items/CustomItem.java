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
    boolean needFix = false;
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
        
        addListener(new ItemClickListener(this,libgdxEditor));
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
        
        // keep the collider glued to the element even when it was moved/rotated/scaled without update()
        syncBodyTransform();
        if (!points.isEmpty() && body != null) {
            com.badlogic.gdx.physics.box2d.Transform tr = body.getTransform();
            Vector2 first = tr.mul(new Vector2(points.get(0)));
            Vector2 prev = first;
            for (int i = 1; i < points.size(); i++) {
                Vector2 current = tr.mul(new Vector2(points.get(i)));
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
        EditorPivot.apply(this, propertySet);
        
        float x = propertySet.getFloat("x");
        float y = needFix ? (getStage().getHeight() - propertySet.getFloat("y") - height) : propertySet.getFloat("y");
		if(needFix){
		    propertySet.put("y", y);
		    needFix = false;
		}
        setPosition(x, y);
        
        // Common properties
        setZIndex(Math.max(0, propertySet.getInt("z")));
        setRotation(-propertySet.getFloat("rotation"));
        setScale(propertySet.getFloat("Scale X"),propertySet.getFloat("Scale Y"));
        setVisible(propertySet.getString("Visible").equals("true"));
		offset[0] = propertySet.getFloat("ColliderX")*-1;
    	offset[1] = propertySet.getFloat("ColliderY")*-1;
        
        buildPoints();
		
        // Update physics body
        updatePhysicsBody();
		syncBodyTransform();
		if(!propertySet.getString("Tint").equals(tint)){
		    setColor(propertySet.getColor("Tint"));
		    tint = propertySet.getString("Tint");
		}
    }

    private float builtScaleX = 1, builtScaleY = 1, builtW, builtH;

    /** Moves the helper body to the element's current center / rotation; rebuilds the shape when size or scale changed. */
    private void syncBodyTransform() {
        if (body == null || propertySet == null) return;
        if (getScaleX() != builtScaleX || getScaleY() != builtScaleY || getWidth() != builtW || getHeight() != builtH) {
            if (editor.getWorld().isLocked()) return;
            buildPoints();
            updatePhysicsBody();
            if (body == null) return;
        }
        float bx = offset[0] + getX() + getWidth() * 0.5f, by = offset[1] + getY() + getHeight() * 0.5f;
        float ang = (float) Math.toRadians(getRotation());
        Vector2 p = body.getPosition();
        if (p.x != bx || p.y != by || body.getAngle() != ang) {
            if (!editor.getWorld().isLocked()) body.setTransform(bx, by, ang);
        }
    }

    /** Polygon local to the element center, scaled like the picture (the body carries position + rotation). */
    private void buildPoints() {
        points.clear();
        float width = getWidth(), height = getHeight(), sx = getScaleX(), sy = getScaleY();
        builtScaleX = sx; builtScaleY = sy; builtW = width; builtH = height;
        String pointsStr = propertySet.getString("Points");
        for (String point : pointsStr.split("-")) {
            String[] coords = point.split(",");
            if (coords.length == 2) {
				try {
                	float px = (Utils.getFloat(coords[0]) * width - width*0.5f) * sx;
                	float py = ((1 - Utils.getFloat(coords[1])) * height - height*0.5f) * sy; // Flip Y-axis
                	points.add(new Vector2(px, py));
				} catch(Exception e){}
            }
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
            
            if (points.size() < 3) return; // a loop needs 3 points; keep no body instead of crashing
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
	/** The Box2D body is only a helper for the editor drawing: destroy it when the item leaves the stage (delete, shape change, reload),
	 *  otherwise its outline keeps being drawn by the debug renderer. It is recreated by update() when the item comes back (undo). */
	private boolean bodyDestroyed = false;

	@Override
	protected void setStage(com.badlogic.gdx.scenes.scene2d.Stage stage) {
		super.setStage(stage);
		if (stage == null) {
			final Body b = body;
			if (b == null) return;
			body = null;
			bodyDestroyed = true;
			try {
				if (b.getWorld().isLocked()) com.badlogic.gdx.Gdx.app.postRunnable(() -> { try { b.getWorld().destroyBody(b); } catch (Exception ignored) {} });
				else b.getWorld().destroyBody(b);
			} catch (Exception ignored) {}
		} else if (bodyDestroyed && propertySet != null) {
			bodyDestroyed = false;
			try { update(); } catch (Exception ignored) {}
		}
	}

	@Override
	public Body getBody() {
		return body;
	}

    @Override
    public void setProperties(PropertySet<String, Object> propertySet) {
        this.propertySet = propertySet;
        PropertySet<String, Object> temp = PropertySet.getDefualt(this, "custom.json");
		needFix = propertySet.getString("Version").equals("");
		if(!propertySet.getString("Shape").equals("Custom")){
		    if(editor!=null){
		        try {
		            remove();
		            EditorItem newBody = propertySet.getString("Shape").equals("Circle") ? new CircleItem(editor) : new BoxItem(editor);
            		((Actor)newBody).setName(propertySet.getString("name")); // the new item needs its name before setProperties (defaults use it)
            		editor.addActor((Actor)newBody);
            		newBody.setProperties(propertySet);
            		editor.selectActor((Actor)newBody);
            		newBody.update();
		        } catch(Exception e){ com.star4droid.star2d.logs.Logs.editor("shapes", com.star4droid.template.Utils.Utils.getStackTraceString(e)+"\n"); }
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
		} else {
			Utils.setDefaultImage(this); // image reset / cleared: back to the default picture
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
        EditorPivot.apply(this, propertySet);
    }
}