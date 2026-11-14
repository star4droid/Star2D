package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.template.ui.UIView;

import java.io.File;

/**
 * The UIHOLDER element in the editor: shows a .ui file (project folder UI/) exactly as the game will draw it, as ONE block.
 * The inner elements cannot be touched or edited here (they belong to the .ui): tap = select the holder, edit the .ui with the
 * "Edit" button in the properties. The picture reloads by itself when the .ui file changes (UI Maker saved).
 */
public class EditorUIHolderItem extends Group implements EditorItem {
	PropertySet<String,Object> propertySet;
	LibgdxEditor editor;
	ShapeRenderer shapeRenderer;
	private static BitmapFont font;
	private UIView view;
	private String loadedUi = null, loadedFit = "";
	private long loadedStamp = -1;
	private float checkTimer = 0f;
	
	public EditorUIHolderItem(final LibgdxEditor libgdxEditor){
		super();
		this.editor = libgdxEditor;
		this.shapeRenderer = libgdxEditor.getShapeRenderer();
		setSize(300, 300);
		setTransform(true);
		libgdxEditor.addActor(this);
		addListener(new ItemClickListener(this, editor));
	}
	
	@Override
	public PropertySet<String, Object> getPropertySet() {
		return propertySet;
	}
	
	@Override
	public Body getBody() {
		return null;
	}
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public EditorUIHolderItem setDefault() {
		propertySet = PropertySet.getDefualt(this, "uiholder.json");
		return this;
	}
	
	@Override
	public String getTypeName() {
		return "UIHolder";
	}
	
	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
		this.propertySet = propertySet;
		if(propertySet == null) return;
		// add the missing keys; keys we do not know (prefab / pid / pbase ...) are kept
		PropertySet<String,Object> temp = PropertySet.getDefualt(this, "uiholder.json");
		for(String s : temp.keySet()){
			if(!propertySet.containsKey(s))
				propertySet.put(s, temp.get(s));
		}
		update();
	}
	
	private File uiFile(String ui){
		try {
			return UIView.uiFile(editor.getProject().getPath(), ui);
		} catch(Throwable t){
			return null;
		}
	}
	
	private long stampOf(String ui){
		File f = uiFile(ui);
		return f != null && f.isFile() ? f.lastModified() * 31 + f.length() : -2;
	}
	
	/** (Re)creates the view when the file name, the fit or the file content changed. */
	private void ensureView(boolean force){
		if(propertySet == null || getStage() == null) return;
		String ui = propertySet.getString("ui");
		if(ui == null) ui = "";
		String fit = propertySet.getString("fit");
		long stamp = ui.isEmpty() ? -2 : stampOf(ui);
		boolean changed = force || view == null || !ui.equals(loadedUi) || stamp != loadedStamp;
		if(changed){
			if(view != null){
				try { view.dispose(); } catch(Throwable ignored){}
				view = null;
			}
			loadedUi = ui;
			loadedStamp = stamp;
			if(!ui.isEmpty() && stamp != -2){
				try {
					view = UIView.load(editor.getProject().getPath(), ui);
					view.setPlaceholders(true);
					// nothing inside is touchable here: the whole block is selected / moved as one
					view.setTouchable(Touchable.disabled);
					addActor(view);
				} catch(Throwable t){
					view = null;
					try { com.star4droid.star2d.logs.Logs.editor("ui_holder", "load error : " + t + "\n"); } catch(Throwable ignored){}
				}
			}
			loadedFit = null;
		}
		if(view != null){
			if(loadedFit == null || !fit.equals(loadedFit)){
				view.setFit(fit);
				loadedFit = fit;
			}
			if(view.getWidth() != getWidth() || view.getHeight() != getHeight()){
				view.setPosition(0, 0);
				view.setSize(getWidth(), getHeight());
			}
		}
	}
	
	@Override
	public void update() {
		if(getStage() == null || propertySet == null) return;
		float w = propertySet.getFloat("width"), h = propertySet.getFloat("height");
		if(w <= 0) w = 300;
		if(h <= 0) h = 300;
		float px = propertySet.getFloat("x"), py = propertySet.getFloat("y");
		if(com.star4droid.template.ui.UIHolderLayout.isScreen(propertySet.getString("layout"))
				&& "UI".equals(propertySet.getString("type"))){
			// placed in the screen box (the logic frame) by anchor / stretch / percent
			float[] r = com.star4droid.template.ui.UIHolderLayout.compute(propertySet.getString("anchor"),
					"true".equals(propertySet.getString("Stretch X")), "true".equals(propertySet.getString("Stretch Y")),
					propertySet.getFloat("Width Percent"), propertySet.getFloat("Height Percent"),
					propertySet.getFloat("Margin X"), propertySet.getFloat("Margin Y"), w, h,
					editor.getLogicWidth(), editor.getLogicHeight());
			px = r[0]; py = r[1]; w = r[2]; h = r[3];
		}
		setSize(w, h);
		EditorPivot.apply(this, propertySet);
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(px, py);
		setZIndex(Math.max(0, propertySet.getInt("z")));
		setRotation(-propertySet.getFloat("rotation"));
		setVisible(!propertySet.getString("Visible").equals("false"));
		ensureView(false);
	}
	
	@Override
	public void act(float delta) {
		super.act(delta);
		checkTimer += delta;
		if(checkTimer > 0.6f){
			checkTimer = 0f;
			try { ensureView(false); } catch(Throwable ignored){}
		}
	}
	
	/** The whole rectangle can be hit (the inner elements are disabled), so a tap selects the holder. */
	@Override
	public Actor hit(float x, float y, boolean touchable) {
		if(touchable && getTouchable() != Touchable.enabled) return null;
		if(!isVisible()) return null;
		return (x >= 0 && y >= 0 && x < getWidth() && y < getHeight()) ? this : null;
	}
	
	private void dashed(float x1, float y1, float x2, float y2, float dash){
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float)Math.sqrt(dx*dx + dy*dy);
		if(len < 0.01f) return;
		int n = Math.max(1, (int)(len / dash));
		if(n > 400) n = 400;
		for(int i = 0; i < n; i += 2){
			float a = i / (float)n, b = Math.min((i + 1) / (float)n, 1f);
			shapeRenderer.line(x1 + dx*a, y1 + dy*a, x1 + dx*b, y1 + dy*b);
		}
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		if(getStage() == null || propertySet == null) return;
		super.draw(batch, parentAlpha); // the .ui
		float zoom = 1f;
		try { zoom = ((com.badlogic.gdx.graphics.OrthographicCamera)getStage().getCamera()).zoom; } catch(Throwable ignored){}
		boolean selected = editor.getSelectedActor() == this;
		batch.end();
		shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
		Gdx.gl.glLineWidth(selected ? 2f : 1f);
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
		Color c = selected ? Color.ORANGE : new Color(0.49f, 0.61f, 1f, 0.8f);
		shapeRenderer.setColor(c);
		float cx = getX() + getWidth()*0.5f, cy = getY() + getHeight()*0.5f;
		float hw = getWidth()*0.5f, hh = getHeight()*0.5f;
		float rad = getRotation() * MathUtils.degreesToRadians;
		float cos = MathUtils.cos(rad), sin = MathUtils.sin(rad);
		float[] px = new float[4], py = new float[4];
		float[][] corners = {{-hw,-hh},{hw,-hh},{hw,hh},{-hw,hh}};
		for(int i = 0; i < 4; i++){
			px[i] = cx + corners[i][0]*cos - corners[i][1]*sin;
			py[i] = cy + corners[i][0]*sin + corners[i][1]*cos;
		}
		float dash = Math.max(8f, 8f * zoom);
		for(int i = 0; i < 4; i++){
			int j = (i + 1) % 4;
			if(view == null || selected) dashed(px[i], py[i], px[j], py[j], dash);
			else shapeRenderer.line(px[i], py[i], px[j], py[j]);
		}
		shapeRenderer.end();
		Gdx.gl.glLineWidth(1f);
		batch.begin();
		try {
			if(font == null) font = new BitmapFont();
			font.getData().setScale(Math.max(0.5f, zoom));
			font.setColor(c);
			String n = propertySet.getString("name");
			float topY = Math.max(Math.max(py[0], py[1]), Math.max(py[2], py[3]));
			float leftX = Math.min(Math.min(px[0], px[1]), Math.min(px[2], px[3]));
			font.draw(batch, n, leftX, topY + 16f * Math.max(0.5f, zoom));
			if(view == null){
				String ui = propertySet.getString("ui");
				String msg = (ui == null || ui.isEmpty()) ? "UI Holder: no .ui chosen" : "UI Holder: '" + ui + ".ui' not found";
				font.draw(batch, msg, cx - 70f * Math.max(0.5f, zoom), cy);
			}
		} catch(Throwable ignored){}
	}
}
