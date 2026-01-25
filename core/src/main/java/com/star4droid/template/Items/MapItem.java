package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.star4droid.star2d.ElementDefs.MapDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;

public class MapItem extends Actor implements PlayerItem {
	StageImp stage;
	float textY=0;
	ElementEvent elementEvent;
	MapDef mapDef;
	OrthogonalTiledMapRenderer mapRenderer;
	String mapStr = "";
	Matrix4 matrix = new Matrix4();
	
	ChildsHolder childsHolder = new ChildsHolder(this);
	
	public MapItem(StageImp stageImp){
		super();
		//setWrap(true);
		this.stage = stageImp;
		setY(0);
		
		addListener(new InputListener(){
			@Override
			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchEnd(event);
				else if(elementEvent!=null) elementEvent.onTouchEnd(MapItem.this,event);
			}
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchStart(event);
				else if(elementEvent!=null) elementEvent.onTouchStart(MapItem.this,event);
				return true;
			}
		});
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(getScript()!=null)
					getScript().click();
				else if(elementEvent!=null) elementEvent.onClick(MapItem.this);
			}
		});
	}
	
	public static MapItem create(StageImp stageImp,MapDef mapDef,ElementEvent elementEvent){
		return new MapItem(stageImp).setElementEvent(elementEvent).setDef(mapDef);
	}
	
	public MapItem setDef(MapDef mapDef){
	    this.mapDef = mapDef;
	    setup();
	    return this;
	}
	
	@Override
	public void update() {
		if(getScript()!=null)
			getScript().bodyUpdate();
		else if(elementEvent!=null) elementEvent.onBodyUpdate(this);
	}
	
	@Override
	public void setItemText(String text) {
		
	}
	
	@Override
	public Body getBody() {
	    return null;
	}
	
	/*
	@Override
	public boolean setZIndex(int z){
	    boolean b = true;
	    try {
	        b = super.setZIndex(z);
	    } catch(Exception e){}
	    if(stage!=null) stage.updateActors();
	    return b;
	}
	*/

	@Override
	public ChildsHolder getChildsHolder() {
		if(childsHolder!=null)
			return childsHolder;
		else {
			childsHolder = new ChildsHolder(this);
			return childsHolder;
		}
	}
	
	com.star4droid.template.Utils.ItemScript itemScript;
	@Override
	public void setScript(com.star4droid.template.Utils.ItemScript script){
	    this.itemScript = script;
	}
	
	@Override
	public <T extends com.star4droid.template.Utils.ItemScript> T getScript(){
	    return (T) itemScript;
	}
	
	@Override
	public PlayerItem getClone(String newName) {
		MapDef newDef = mapDef.getClone(newName);
	    PlayerItem item = create(stage,mapDef,elementEvent);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
			} catch(Exception ex){}
		}
		return item;
	}
	
	public MapItem setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	@Override
	public void setY(float y) {
		super.setY(y);//stage.getViewport().getWorldHeight()-getHeight()-y);
		textY = y;
	}

	@Override
	public void setPosition(float x, float y) {
		super.setX(x);
		setY(y);
	}
	
	private void setup(){
		if(mapDef==null) return;
		setName(mapDef.name);
		boolean UI = mapDef.type.equals("UI");
		float x = mapDef.x,
    		y = mapDef.y;
		//setSize((UI ? 1 : StageImp.WORLD_SCALE) * width,(UI ? 1 : StageImp.WORLD_SCALE) * height);
		setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		setZIndex((int) mapDef.z);
		setRotation(-mapDef.rotation);
		setVisible(mapDef.Visible);
		//setText(propertySet.get("Text").toString());
		//Utils.showMessage(getContext(),propertySet.get("Text").toString());
		mapStr = mapDef.Map;
		if(!mapStr.equals("")){
			String path = (stage.getProject().getImagesPath()+mapStr).replace(com.star4droid.template.Utils.Utils.seperator,"/").replace("//","/");
			try {
		        TiledMap map = (stage.getAssets()!=null&&stage.getAssets().contains(path))? stage.getAssets().get(path):null;
		        if(map == null){
		            try {
		                stage.getAssets().load(path, TiledMap.class);
		                stage.getAssets().finishLoading();
		                map = stage.getAssets().contains(path) ? stage.getAssets().get(path) : new com.badlogic.gdx.maps.tiled.TmxMapLoader().load(path);
		            } catch(Exception | Error err){
		                map = new com.badlogic.gdx.maps.tiled.TmxMapLoader().load(Gdx.files.absolute(path).path());
		            }
		            //if(!stage.getAssets().contains(path))
		                //stage.getAssets().addAsset(path, TiledMap.class,map);
		        }
		        mapRenderer = new OrthogonalTiledMapRenderer(map, StageImp.WORLD_SCALE);
			} catch(Exception | Error er){}
		}
		
		if(getStage()==null)
		    stage.addActor(this);
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		//super.draw(batch, parentAlpha);
		if(mapRenderer==null) return;
		batch.end();
		float x = getX(), y = getY();
        boolean isMoved = (x != 0 && y != 0);
        if(isMoved){
            matrix.set(getStage().getCamera().combined);
            //matrix.setTranslation(x, y, 0);
            matrix.translate(x, y, 0);
        }
        mapRenderer.setView((com.badlogic.gdx.graphics.OrthographicCamera)(getStage().getCamera()));
        if(isMoved)
            mapRenderer.getBatch().setProjectionMatrix(matrix);
        mapRenderer.render();
        mapRenderer.getBatch().setProjectionMatrix(getStage().getCamera().combined);
		batch.begin();
		update();
	}
	
	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return mapDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}