package com.star4droid.template;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public class ProgressItem extends Group {
	Stage stage;
	float max=100,progress=0,progressY=0;
	float PADDING = 5;
	Image backgroundImg,progressImg,borderImg;
	
	/*
	    It's the same progress item in template, but it's modified to work in a stage instead of StageImp
	*/
	public ProgressItem(Stage st){
		super();
		stage = st;
		//progress main
		Texture texture = new Texture(Gdx.files.internal("images/white.png"));
		backgroundImg = new Image(texture);
		backgroundImg.setPosition(PADDING, PADDING);
		backgroundImg.setColor(Color.GRAY);
		borderImg = new Image(texture);
		borderImg.setColor(Color.valueOf("#1CD3EB"));
		progressImg = new Image(texture);
		progressImg.setColor(Color.valueOf("#1CD3EB"));
		progressImg.setPosition(PADDING * 2,PADDING * 2);
		addActor(borderImg);
		addActor(backgroundImg);
		addActor(progressImg);
		borderImg.setZIndex(0);
		backgroundImg.setZIndex(1);
		progressImg.setZIndex(2);
		setY(0);
		setSize(500,120);
	}
	
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
		float progressWidth = ((progress / max) * (getWidth() - PADDING * 4));
		progressImg.setSize(progressWidth,getHeight() - PADDING * 4);
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
	protected void sizeChanged() {
		super.sizeChanged();
		//if(getStage()!=null) setY(progressY);
		//setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		if(backgroundImg.getWidth()!=getWidth()||backgroundImg.getHeight()!=getHeight()){
			backgroundImg.setSize(getWidth() - PADDING * 2,getHeight() - PADDING * 2);
			borderImg.setSize(getWidth(),getHeight());
			updateProgress();
		}
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
	}
	
}