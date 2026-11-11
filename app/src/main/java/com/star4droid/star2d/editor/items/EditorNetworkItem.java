package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;

import java.util.ArrayList;

/**
 * Editor element of the NETWORK element (local multiplayer settings, one per scene). Invisible in the game: the editor
 * shows a rounded tile with a Wi-Fi icon and the number of players.
 */
public class EditorNetworkItem extends Image implements EditorItem {
	public static final String TYPE = "NETWORK", FILE = "network.json";
	private static final String[] UNUSED = {"rotation", "Pivot", "Pivot X", "Pivot Y", "type", "behaviors", "scriptVars", "objectVars", "net"};
	PropertySet<String, Object> propertySet;
	final LibgdxEditor editor;
	private final ShapeRenderer shapes;
	private static Texture icon;
	private static Object iconApp;

	public EditorNetworkItem(LibgdxEditor editor) {
		this.editor = editor;
		this.shapes = editor.getShapeRenderer();
		if (icon == null || iconApp != Gdx.app) {
			icon = new Texture(Gdx.files.internal("images/bodies/network.png"));
			icon.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
			iconApp = Gdx.app;
		}
		setDrawable(new TextureRegionDrawable(icon));
		setSize(96, 96);
		editor.addActor(this);
		addListener(new ItemClickListener(this, editor));
	}

	public EditorNetworkItem setDefault() {
		propertySet = PropertySet.getDefualt(this, FILE);
		if (propertySet != null) for (String k : UNUSED) propertySet.remove(k);
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
		PropertySet<String, Object> temp = PropertySet.getDefualt(this, FILE);
		for (String k : UNUSED) temp.remove(k);
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
		setSize(Math.max(24, propertySet.getFloat("width")), Math.max(24, propertySet.getFloat("height")));
		setPosition(propertySet.getFloat("x"), propertySet.getFloat("y"));
		setZIndex(Math.max(0, propertySet.getInt("z")));
		setVisible(!"false".equals(propertySet.getString("Visible")));
	}

	@Override
	public String getTypeName() {
		return TYPE;
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		batch.end();
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		shapes.setProjectionMatrix(getStage().getCamera().combined);
		float w = getWidth(), h = getHeight(), r = Math.min(w, h) * 0.18f;
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		shapes.setColor(0.10f, 0.32f, 0.42f, 0.9f * parentAlpha);
		roundRect(getX(), getY(), w, h, r);
		// players: small dots under the icon
		int max = Math.max(1, Math.min(8, Math.round(propertySet == null ? 4 : propertySet.getFloat("Max Players"))));
		float dot = Math.min(w, h) * 0.05f, gap = dot * 3f, start = getX() + w * 0.5f - (max - 1) * gap * 0.5f;
		shapes.setColor(0.6f, 0.95f, 1f, parentAlpha);
		for (int i = 0; i < max; i++) shapes.circle(start + i * gap, getY() + h * 0.17f, dot, 12);
		shapes.end();
		batch.begin();
		float s = Math.min(w, h) * 0.55f;
		batch.setColor(1, 1, 1, parentAlpha);
		batch.draw(icon, getX() + w * 0.5f - s * 0.5f, getY() + h * 0.56f - s * 0.5f, s, s);
	}

	private void roundRect(float x, float y, float w, float h, float r) {
		shapes.rect(x + r, y, w - 2 * r, h);
		shapes.rect(x, y + r, r, h - 2 * r);
		shapes.rect(x + w - r, y + r, r, h - 2 * r);
		shapes.circle(x + r, y + r, r, 12);
		shapes.circle(x + w - r, y + r, r, 12);
		shapes.circle(x + r, y + h - r, r, 12);
		shapes.circle(x + w - r, y + h - r, r, 12);
	}
}
