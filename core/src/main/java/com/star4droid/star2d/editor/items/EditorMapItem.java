package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.badlogic.gdx.graphics.Color;
import com.star4droid.star2d.editor.Utils;
import com.badlogic.gdx.graphics.g2d.Batch;
import java.util.ArrayList;
import com.star4droid.star2d.editor.Utils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;

public class EditorMapItem extends Image implements EditorItem {
	PropertySet<String,Object> propertySet;
	LibgdxEditor editor;
	OrthogonalTiledMapRenderer mapRenderer;
	String mapStr = "";
	Matrix4 matrix = new Matrix4();
	public EditorMapItem(final LibgdxEditor libgdxEditor){
		super();
		this.editor = libgdxEditor;
		setSize(50,50);
		libgdxEditor.addActor(this);
		setDrawable(new TextureRegionDrawable(new Texture(Gdx.files.internal("images/bodies/map.png"))));
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
		//setSize(propertySet.getFloat("width"),propertySet.getFloat("height"));
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		float x = propertySet.getFloat("x"),
			  y = propertySet.getFloat("y");
		//Gdx.files.external("logs/box.txt").writeString("eh : "+getStage().getHeight()+", bxh : "+getHeight()+", y : "+y+" \n = "+getY()+"\n"+"_".repeat(10)+"\n",true);
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(x,y);
		setZIndex(propertySet.getInt("z"));
		//setRotation(-propertySet.getFloat("rotation"));
		String mp = propertySet.getString("Map");
		if(!mp.equals(mapStr)){
		    mapStr = mp;
		    if(mapRenderer != null)
		        mapRenderer.dispose();
		    mapRenderer = null;
		    try {
		        String path = this.editor.getProject().getImagesPath() + this.propertySet.getString("Map").replace(Utils.seperator,"/").replace("//","/");
		        TiledMap map = (editor.getAssetLoader()!=null&&editor.getAssetLoader().contains(path))? editor.getAssetLoader().get(path):null;
		        if(map == null){
		            editor.getAssetLoader().load(path, TiledMap.class);
		            editor.getAssetLoader().finishLoading();
		            try {
		                map = editor.getAssetLoader().contains(path) ? editor.getAssetLoader().get(path) : new com.badlogic.gdx.maps.tiled.TmxMapLoader().load(path);
		            } catch(Exception | Error err){
		                map = new com.badlogic.gdx.maps.tiled.TmxMapLoader().load(Gdx.files.absolute(path).path());
		            }
		            //if(!editor.getAssetLoader().contains(path))
		                //editor.getAssetLoader().addAsset(path, TiledMap.class,map);
		        }
		        mapRenderer = new OrthogonalTiledMapRenderer(map);
		    } catch(Error | Exception ex){}
		    
		}
	}
	
	@Override
	public Body getBody() {
		return null;
	}
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public EditorMapItem setDefault() {
		propertySet = PropertySet.getDefualt(this,"map.json");
		//if(propertySet==null) Log.e(Utils.error_tag,"Null PropertySet");
		return this;
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
	}

	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
	    this.propertySet = propertySet;
		if(propertySet==null) return;
		
		PropertySet<String,Object> temp = PropertySet.getDefualt(this,"map.json");
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
		
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
	    if(mapRenderer!=null){
	        batch.end();
	        float x = getX(), y = getY();
	        boolean isMoved = (x != 0 && y != 0);
	        if(isMoved){
	            matrix.set(getStage().getCamera().combined);
	            matrix.translate(x, y, 0);
	        }
	        mapRenderer.setView((com.badlogic.gdx.graphics.OrthographicCamera)(getStage().getCamera()));
	        if(isMoved)
	            mapRenderer.getBatch().setProjectionMatrix(matrix);
            mapRenderer.render();
            mapRenderer.getBatch().setProjectionMatrix(getStage().getCamera().combined);
            batch.begin();
	    } else super.draw(batch, parentAlpha);
	}

	@Override
	public String getTypeName() {
	    return "Map";
	}
	
}