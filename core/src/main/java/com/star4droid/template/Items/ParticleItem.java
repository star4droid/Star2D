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
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.star2d.ElementDefs.ParticleDef;

public class ParticleItem extends Image implements PlayerItem {
	ParticleDef particleDef;
	ElementEvent elementEvent;
	ChildsHolder childsHolder = new ChildsHolder(this);
	StageImp stage;
	ParticleEffect particleEffect;
	
	public ParticleItem(StageImp stage){
		super();
		setSize(75,75);
		this.stage = stage;
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
	    particleDef.Scale_X = sx;
	    particleDef.Scale_Y = sy;
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
	public ParticleEffect getParticleEffect(){
		return particleEffect;
	}
	
	public ParticleItem setElementEvent(ElementEvent elementEvent){
		this.elementEvent = elementEvent;
		return this;
	}
	
	public ParticleItem setDef(ParticleDef def){
		this.particleDef = def;
		if(getStage()==null)
			stage.addActor(this);
		if(particleDef == null) return this;
		if(!particleDef.file.equals("")){
			FileHandle fileHandle = Gdx.files.absolute(stage.project.get("files")+particleDef.file.replace(Utils.seperator,"/"));
			if(fileHandle.exists()){
				particleEffect.load(fileHandle,fileHandle.parent());
				particleEffect.start();
			} else loadDefault();
		} else loadDefault();
		boolean UI = particleDef.type.equals("UI");
		float x = particleDef.x,
			  y = particleDef.y;
		String name = particleDef.name;
		if(!name.equals(""))
			setName(name);
		setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		setZIndex((int) particleDef.z);
		setVisible(particleDef.Visible);
		particleEffect.setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		particleEffect.scaleEffect((UI ? 1 : StageImp.WORLD_SCALE) * particleDef.Scale_X,(UI ? 1 : StageImp.WORLD_SCALE) * particleDef.Scale_Y);
		int dur = (int) particleDef.Duration;
		if(dur>0)
			particleEffect.setDuration(dur);
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
		return this;
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		particleEffect.draw(batch,Gdx.graphics.getDeltaTime());
		if(particleEffect.isComplete() && particleDef != null && particleDef.Loop){
			particleEffect.reset();
		}
	}
	
	@Override
	protected void scaleChanged() {
		super.scaleChanged();
		if(particleDef!=null)
			particleEffect.scaleEffect(particleDef.Scale_X * getScaleX(),particleDef.Scale_Y * getScaleY());
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
	    ParticleDef newDef = particleDef.getClone(newName);
		ParticleItem item = new ParticleItem(stage).setElementEvent(elementEvent).setDef(newDef);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
			} catch(Exception ex){}
		}
		return item;
	}
	
	private void loadDefault(){
		String filesPath = stage.project.get("files");
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
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return particleDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
	    return elementEvent;
	}
	
}