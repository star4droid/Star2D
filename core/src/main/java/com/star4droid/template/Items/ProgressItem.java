package com.star4droid.template.Items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.star2d.ElementDefs.ProgressDef;
import com.star4droid.star2d.ElementDefs.ElementEvent;

public class ProgressItem extends Group implements PlayerItem {
	StageImp stage;
	float max=100,progress=0,progressY=0;
	Image backgroundImg,progressImg;
	ProgressDef progressDef;
	ElementEvent elementEvent;
	
	public ProgressItem(StageImp st){
		super();
		stage = st;
		//progress main
		Texture texture = new Texture(Gdx.files.internal("images/white.png"));
		backgroundImg = new Image(texture);
		backgroundImg.setColor(Color.SKY);
		progressImg = new Image(texture);
		progressImg.setColor(Color.BLUE);
		addActor(backgroundImg);
		addActor(progressImg);
		backgroundImg.setZIndex(0);
		progressImg.setZIndex(1);
		setY(0);
		setSize(500,120);
		addListener(new InputListener(){
			@Override
			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchEnd(event);
				else if(elementEvent!=null) elementEvent.onTouchEnd(ProgressItem.this,event);
			}
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if(getScript()!=null)
					getScript().touchStart(event);
				else if(elementEvent!=null) elementEvent.onTouchStart(ProgressItem.this,event);
				return true;
			}
		});
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(getScript()!=null)
					getScript().click();
				else if(elementEvent!=null) elementEvent.onClick(ProgressItem.this);
			}
		});
	}
	
	private void setup(){
	    boolean UI = progressDef.type.equals("UI");
		float width = progressDef.width,
		height = progressDef.height,
		x = progressDef.x,
		y = progressDef.y;
		setZIndex((int) progressDef.z);
		setVisible(progressDef.Visible);
		setSize((UI ? 1 : StageImp.WORLD_SCALE) * width,(UI ? 1 : StageImp.WORLD_SCALE) * height);
		setPosition((UI ? 1 : StageImp.WORLD_SCALE) * x,(UI ? 1 : StageImp.WORLD_SCALE) * y);
		setRotation(-progressDef.rotation);
		setBackColor(progressDef.Background_Color);
		setProgressColor(progressDef.Progress_Color);
		setMax(progressDef.Max);
		setProgress(progressDef.Progress);
		setName(progressDef.name);
		if(getStage()==null)
		    stage.addActor(this);
		if(elementEvent!=null)
			elementEvent.onBodyCreated(this);
		if(getScript()!=null)
			getScript().bodyCreated();
	}
	
	public ProgressItem setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	public ProgressItem setDef(ProgressDef def){
		progressDef = def;
		setup();
		return this;
	}
	
	@Override
	public boolean setZIndex(int z){
	    boolean b = true;
	    try {
	        b = super.setZIndex(z);
	    } catch(Exception e){}
	    if(stage!=null) stage.updateActors();
	    return b;
	}
	
	@Override
	public void setProgress(int prog){
	    progress = prog;
		updateProgress();
	}
	
	public ProgressItem setProgress(float prog){
		//if(max<prog) return this;
		progress = prog;
		updateProgress();
		return this;
	}
	
	public int getProgress(){
		return (int)progress;
	}
	
	public int getMax(){
		return (int)max;
	}
	
	public float getProgressF(){
		return progress;
	}
	
	public float getMaxF(){
		return max;
	}
	
	public void setMax(int max){
	    this.max = max;
	}
	
	public ProgressItem setMax(float mx){
		max = mx;
		updateProgress();
		return this;
	}
	
	//not added yet...
	//public void setMin(float mn){
		//min = mn;
	//}
	
	public void updateProgress(){
		if(progress>max) progress = max;
		float progressWidth = ((progress / max) * getWidth());
		progressImg.setSize(progressWidth,getHeight());
	}
	
	public ProgressItem setBackColor(String hex){
		backgroundImg.setColor(Color.valueOf(hex));
		return this;
	}
	
	public ProgressItem setProgressColor(String hex){
		progressImg.setColor(Color.valueOf(hex));
		return this;
	}
	
	public ProgressItem setBackColor(Color color){
		backgroundImg.setColor(color);
		return this;
	}
	
	public ProgressItem setProgressColor(Color color){
		progressImg.setColor(color);
		return this;
	}
	
	public ProgressItem setBackColor(int color){
		backgroundImg.setColor(new Color(color));
		return this;
	}
	
	public ProgressItem setProgressColor(int color){
		progressImg.setColor(new Color(color));
		return this;
	}
	
	@Override
	public void setY(float y) {
		if(stage!=null) super.setY(y);//stage.getViewport().getWorldHeight()-getHeight()-y);
		progressY = y;
	}

	@Override
	public void setPosition(float x, float y) {
		super.setX(x);
		setY(y);
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		//if(getStage()!=null) setY(progressY);
		//setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		if(backgroundImg.getWidth()!=getWidth()||backgroundImg.getHeight()!=getHeight()){
			backgroundImg.setSize(getWidth(),getHeight());
			updateProgress();
		}
	}
	
	@Override
	public void update() {
		if(getScript()!=null)
			getScript().bodyUpdate();
		else if(elementEvent!=null) elementEvent.onBodyUpdate(this);
	}

	@Override
	public Body getBody() {
	    return null;
	}
	
	ChildsHolder childsHolder = new ChildsHolder(this);
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
		ProgressDef newDef = progressDef.getClone(newName);
	    ProgressItem item = new ProgressItem(stage).setElementEvent(elementEvent).setDef(newDef);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
			} catch(Exception ex){}
		}
		return item;
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		update();
	}
	
	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return progressDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}