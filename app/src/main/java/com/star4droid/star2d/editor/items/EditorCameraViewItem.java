package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;

import java.util.ArrayList;

/** Editor element of a CAMERAVIEW (live picture of a camera): a framed rectangle with a camera icon. */
public class EditorCameraViewItem extends Image implements EditorItem {
	PropertySet<String, Object> propertySet;
	final LibgdxEditor editor;
	private final ShapeRenderer shapes;
	private static Texture icon;
	private static Object iconApp;

	public EditorCameraViewItem(LibgdxEditor editor) {
		this.editor = editor;
		this.shapes = editor.getShapeRenderer();
		// static: rebuilt for a new editor activity (its GL context does not know the old texture)
		if (icon == null || iconApp != Gdx.app) {
			icon = new Texture(Gdx.files.internal("images/bodies/camera.png"));
			iconApp = Gdx.app;
		}
		setDrawable(new TextureRegionDrawable(icon));
		setSize(320, 180);
		editor.addActor(this);
		addListener(new ItemClickListener(this, editor));
	}

	public EditorCameraViewItem setDefault() {
		propertySet = PropertySet.getDefualt(this, "cameraview.json");
		if (propertySet != null) propertySet.put("z", "100");
		return this;
	}

	@Override
	public PropertySet<String, Object> getPropertySet() {
		return propertySet;
	}

	@Override
	public void setProperties(PropertySet<String, Object> ps) {
		propertySet = ps;
		if (ps == null) return;
		PropertySet<String, Object> temp = PropertySet.getDefualt(this, "cameraview.json");
		for (String s : temp.keySet()) if (!ps.containsKey(s)) ps.put(s, temp.get(s));
		ArrayList<String> del = new ArrayList<>();
		for (String k : ps.keySet()) if (!temp.containsKey(k)) del.add(k);
		for (String k : del) ps.remove(k);
		update();
	}

	@Override
	public void update() {
		if (propertySet == null || getStage() == null) return;
		String name = propertySet.getString("name");
		if (!name.isEmpty()) setName(name);
		setSize(Math.max(8, propertySet.getFloat("width")), Math.max(8, propertySet.getFloat("height")));
		setOrigin(getWidth() * 0.5f, getHeight() * 0.5f);
		setPosition(propertySet.getFloat("x"), propertySet.getFloat("y"));
		setZIndex(Math.max(0, propertySet.getInt("z")));
		setRotation(-propertySet.getFloat("rotation"));
	}

	@Override
	public String getTypeName() {
		return "CAMERAVIEW";
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		batch.end();
		shapes.setProjectionMatrix(getStage().getCamera().combined);
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		shapes.setColor(0.08f, 0.09f, 0.12f, 0.85f * parentAlpha);
		shapes.rect(getX(), getY(), getOriginX(), getOriginY(), getWidth(), getHeight(), 1, 1, getRotation());
		shapes.end();
		Gdx.gl.glLineWidth(2f);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		Color c = Color.WHITE;
		try {
			c = Color.valueOf(propertySet.getString("Border Color"));
		} catch (Exception ignored) {
		}
		shapes.setColor(c);
		shapes.rect(getX(), getY(), getOriginX(), getOriginY(), getWidth(), getHeight(), 1, 1, getRotation());
		shapes.end();
		Gdx.gl.glLineWidth(1f);
		batch.begin();
		float s = Math.min(getWidth(), getHeight()) * 0.4f;
		batch.draw(icon, getX() + getWidth() * 0.5f - s * 0.5f, getY() + getHeight() * 0.5f - s * 0.5f, s, s);
	}
}
