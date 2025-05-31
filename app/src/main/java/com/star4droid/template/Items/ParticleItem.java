package com.star4droid.template.Items;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.Utils;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.star2d.ElementDefs.ElementEvent;

public class ParticleItem extends Image implements PlayerItem {
	ElementEvent elementEvent;
	PropertySet<String,Object> propertySet;
	ChildsHolder childsHolder = new ChildsHolder(this);
	StageImp stageImp;
	ParticleEffect particleEffect;
	
	public ParticleItem(StageImp stage){
		super();
		setSize(75,75);
		this.stageImp = stage;
		this.particleEffect = new ParticleEffect();
		addListener(new InputListener(){
			@Override
			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchEnd(event);
				else if(elementEvent!=null) elementEvent.onTouchEnd(ParticleItem.this,event);
			}
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchStart(event);
				else if(elementEvent!=null) elementEvent.onTouchStart(ParticleItem.this,event);
				return true;
			}
		});
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(getScript()!=null)
					getScript().click();
				else if(elementEvent!=null) elementEvent.onClick(ParticleItem.this);
			}
		});
	}
	
	@Override
	public void scaleEffect(float sx,float sy){
	    propertySet.put("Scale X",sx);
	    propertySet.put("Scale Y",sy);
	}
	
	@Override
	public void update() {
		if(getScript()!=null)
			getScript().bodyUpdate();
	}
	
	@Override
	protected void positionChanged() {
		super.positionChanged();
		if(particleEffect!=null)
			particleEffect.setPosition(getX(),getY());
	}
	
	@Override
	public ChildsHolder getChildsHolder() {
	    if(childsHolder!=null)
			return childsHolder;
		else {
			childsHolder = new ChildsHolder(this);
			return childsHolder;
		}
	}
	
	@Override
	public Body getBody() {
		return null;
	}

	@Override
	public PropertySet<String, Object> getProperties() {
	    return propertySet;
	}
	
	public ParticleItem setPropertySet(PropertySet<String,Object> set){
		this.propertySet = set;
		if(getStage()==null)
			stageImp.addActor(this);
		if(propertySet == null) return this;
		if(propertySet.containsKey("file") && !propertySet.getString("file").equals("")){
			FileHandle fileHandle = Gdx.files.absolute(stageImp.project.get("files")+propertySet.getString("file").replace(Utils.seperator,"/"));
			if(fileHandle.exists()){
				particleEffect.load(fileHandle,fileHandle.parent());
				particleEffect.start();
			} else loadDefault();
		} else loadDefault();
		
		float x = propertySet.getFloat("x"),
			  y = getStage().getHeight()-getHeight()-propertySet.getFloat("y");
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(x,y);
		setZIndex(propertySet.getInt("z"));
		//setRotation(-propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
		particleEffect.setPosition(x,y);
		particleEffect.scaleEffect(propertySet.getFloat("Scale X"),propertySet.getFloat("Scale Y"));
		int dur = propertySet.getInt("Duration");
		if(dur>0)
			particleEffect.setDuration(dur);
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
		return this;
	}
	
	@Override
	public ParticleEffect getParticleEffect(){
		return particleEffect;
	}
	
	public ParticleItem setElementEvent(ElementEvent elementEvent){
		this.elementEvent = elementEvent;
		return this;
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		//if(propertySet != null && stageImp != null)
		    //particleEffect.scaleEffect(propertySet.getFloat("Scale X") * stageImp.getZooming(),propertySet.getFloat("Scale Y")*stageImp.getZooming());
		particleEffect.draw(batch,Gdx.graphics.getDeltaTime());
		if(particleEffect.isComplete() && propertySet != null && propertySet.getString("Loop").equals("true")){
			particleEffect.reset();
		}
	}
	
	@Override
	protected void scaleChanged() {
		super.scaleChanged();
		if(propertySet!=null)
			particleEffect.scaleEffect(propertySet.getFloat("Scale X") * getScaleX(),propertySet.getFloat("Scale Y") * getScaleY());
	}

	@Override
	public PlayerItem getClone(String newName) {
	    PropertySet<String,Object> set = new PropertySet<>();
		set.putAll(propertySet);
		set.put("old",getParentName());
		set.put("name",newName);
		ParticleItem item = new ParticleItem(stageImp).setElementEvent(elementEvent).setPropertySet(set);
		if(set.getScript()!=null){
			try {
				ItemScript script = (ItemScript)(set.getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stageImp);
				item.setScript(script);
			} catch(Exception ex){}
		}
		return item;
	}
	
	private void loadDefault(){
		String filesPath = stageImp.project.get("files");
		FileHandle smoke = Gdx.files.absolute(filesPath+"smoke/smoke.p"),
					particle = Gdx.files.absolute(filesPath+"smoke/particle-cloud.png");
		if(!(smoke.exists()&&particle.exists())){
			Gdx.files.internal("files/smoke.p").copyTo(smoke);
			Gdx.files.internal("files/particle-cloud.png").copyTo(particle);
		}
		particleEffect.load(smoke,smoke.parent());
		particleEffect.start();
	}
	
	@Override
	public ElementEvent getElementEvents() {
	    return elementEvent;
	}
	
}