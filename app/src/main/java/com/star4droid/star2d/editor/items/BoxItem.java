package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
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

public class BoxItem extends Image implements EditorItem {
	PropertySet<String,Object> propertySet;
	float[] offset=new float[]{0,0};
	String tint = "#FFFFFF";
	boolean needFix = false;
	Body body;
	LibgdxEditor editor;
	public BoxItem(final LibgdxEditor libgdxEditor){
		super(new Texture(Gdx.files.internal("images/logo.png")));
		this.editor = libgdxEditor;
		setSize(50,50);
		libgdxEditor.addActor(this);
		setDrawable(new TextureRegionDrawable(new Texture(Gdx.files.internal("images/logo.png"))));
		addListener(new ItemClickListener(this,editor));
		//debug();
	}
	
	@Override
	public PropertySet<String, Object> getPropertySet() {
	    return propertySet;
	}

	@Override
	public void update() {
		//Gdx.files.external("logs/box.txt").writeString(String.format("w : %1$s, h : %2$s, x : %3$s, y : %4$s\n",getWidth(),getHeight(),getX(),getY()),false);
		if(getStage()==null) return;
		setSize(propertySet.getFloat("width"),propertySet.getFloat("height"));
		EditorPivot.apply(this, propertySet);
		float x = propertySet.getFloat("x"),
			  y = needFix ? (getStage().getHeight()-getHeight()-propertySet.getFloat("y")) : propertySet.getFloat("y");
		if(needFix){
		    propertySet.put("y", y);
		    needFix = false;
		}
		//Gdx.files.external("logs/box.txt").writeString("eh : "+getStage().getHeight()+", bxh : "+getHeight()+", y : "+y+" \n = "+getY()+"\n"+"_".repeat(10)+"\n",true);
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(x,y);
		setZIndex(Math.max(0, propertySet.getInt("z")));
		setRotation(-propertySet.getFloat("rotation"));
		setScale(propertySet.getFloat("Scale X"),propertySet.getFloat("Scale Y"));
		setVisible(propertySet.getString("Visible").equals("true"));
		offset[0] = propertySet.getFloat("ColliderX")*-1;
    	offset[1] = propertySet.getFloat("ColliderY")*-1;
		if(body == null){
			BodyDef def = new BodyDef();
    		def.type = BodyDef.BodyType.StaticBody;
    		def.position.set(0,0);
    		body = getEditor().getWorld().createBody(def);
			PolygonShape shape = new PolygonShape();
			shape.setAsBox(propertySet.getFloat("Collider Width")*0.5f,propertySet.getFloat("Collider Height")*0.5f);
			FixtureDef fx = new FixtureDef();
			fx.shape = shape;
			body.createFixture(fx);
		} else {
			((PolygonShape)body.getFixtureList().get(0).getShape()).setAsBox(propertySet.getFloat("Collider Width")*0.5f,propertySet.getFloat("Collider Height")*0.5f);
		}
		body.setTransform(new Vector2((offset[0]+x+(getWidth()*0.5f)),(offset[1]+y+(getHeight()*0.5f))),(float)Math.toRadians(-propertySet.getFloat("rotation")));
		//setDrawable(new TextureRegionDrawable(new Texture(Gdx.files.internal("images/logo.png"))));
		if(!propertySet.getString("Tint").equals(tint)){
		    setColor(propertySet.getColor("Tint"));
		    tint = propertySet.getString("Tint");
		}
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
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public BoxItem setDefault() {
		propertySet = PropertySet.getDefualt(this,"box.json");
		//if(propertySet==null) Log.e(Utils.error_tag,"Null PropertySet");
		return this;
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		EditorPivot.apply(this, propertySet);
	}

	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
	    this.propertySet = propertySet;
		if(propertySet==null) return;
		needFix = propertySet.getString("Version").equals("");
		if(!propertySet.getString("Shape").equals("Box")){
		    if(editor!=null){
		        try {
		            remove();
		            EditorItem newBody = propertySet.getString("Shape").equals("Circle") ? new CircleItem(editor) : new CustomItem(editor);
            		((Actor)newBody).setName(propertySet.getString("name")); // the new item needs its name before setProperties (defaults use it)
            		editor.addActor((Actor)newBody);
            		newBody.setProperties(propertySet);
            		editor.selectActor((Actor)newBody);
            		newBody.update();
		        } catch(Exception e){ com.star4droid.star2d.logs.Logs.editor("shapes", com.star4droid.template.Utils.Utils.getStackTraceString(e)+"\n"); }
		    }
		    return;
		}
		
		PropertySet<String,Object> temp = PropertySet.getDefualt(this,"box.json");
		for(String s:temp.keySet()){
			if(!propertySet.containsKey(s)){
				propertySet.put(s,temp.get(s));
			}
		}
	    ArrayList<String> toDel = new ArrayList<>();
	   for(String key:propertySet.keySet()){
			if(!temp.containsKey(key)){
				toDel.add(key);
			}
		}
		for(String key:toDel)
	        propertySet.remove(key);
		
		update();
		applyImage();
		loadAnimation();
	}

	private void applyImage() {
		if (!this.propertySet.getString("image").equals("")) {
			String img = this.editor.getProject().getImagesPath() + this.propertySet.getString("image").replace(Utils.seperator,"/");
			File file = new File(img);
			if (file.exists()) {
				float rx = this.propertySet.getFloat("tileX");
				float ry = this.propertySet.getFloat("tileY");
				Utils.setImageFromFile(this, img, rx, ry);
			}
		} else {
			Utils.setDefaultImage(this); // image reset / cleared: back to the default picture
		}
	}

	// ---- the "animation" property: played live in the editor, with priority over the image
	private EditorAnimation anim;
	private String animName = "";
	private float animTime = 0f;

	private void loadAnimation() {
		String n = propertySet.getString("animation");
		if (n == null) n = "";
		EditorAnimation a = n.isEmpty() ? null : EditorAnimation.load(editor, n);
		boolean was = anim != null;
		anim = a;
		animName = n;
		if (a == null && was) applyImage(); // animation reset: the image shows again
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		if (propertySet == null) return;
		if (anim != null) {
			animTime += delta;
			setDrawable(anim.at(animTime));
		}
	}

	@Override
	public String getTypeName() {
	    return "Box";
	}
	
}