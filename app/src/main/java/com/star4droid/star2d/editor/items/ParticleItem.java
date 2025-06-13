package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.template.Utils.Utils;
import java.util.ArrayList;

public class ParticleItem extends Image implements EditorItem,Disposable {
	PropertySet<String,Object> propertySet;
	LibgdxEditor editor;
	ParticleEffect particleEffect;
	String loadPath = null;
	
	public ParticleItem(final LibgdxEditor libgdxEditor){
		//super(new Texture(Gdx.files.internal("images/logo.png")));
		setSize(75,75);
		particleEffect = new ParticleEffect();
		this.editor = libgdxEditor;
		editor.addActor(this);
		addListener(new ItemClickListener(this, editor));
		debug();
	}
	
	@Override
	public PropertySet<String, Object> getPropertySet() {
	    return propertySet;
	}

	@Override
	public void update() {
		if(getStage()==null) return;
		//setSize(propertySet.getFloat("width"),propertySet.getFloat("height"));
		//setOrigin(getWidth()*0.5f,getHeight()*0.5f);
		float x = propertySet.getFloat("x"),
			  y = getStage().getHeight()-getHeight()-propertySet.getFloat("y");
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(x,y);
		particleEffect.reset();
		setZIndex(propertySet.getInt("z"));
		//setRotation(-propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
		particleEffect.setPosition(x,y);
		particleEffect.scaleEffect(propertySet.getFloat("Scale X") * getScaleX(),propertySet.getFloat("Scale Y")*getScaleY());
		int dur = propertySet.getInt("Duration");
		if(dur>0)
			particleEffect.setDuration(dur);
	}
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public ParticleItem setDefault() {
		propertySet = PropertySet.getDefualt(this,"particle.json");
		loadDefault();
		update();
		//if(propertySet==null) Log.e(Utils.error_tag,"Null PropertySet");
		return this;
	}
	
	@Override
	public void setDebug(boolean b) {
		//always show debug bounds...
		super.setDebug(true);
	}
	
	@Override
	protected void scaleChanged() {
		super.scaleChanged();
		if(propertySet!=null)
			particleEffect.scaleEffect(propertySet.getFloat("Scale X") * getScaleX(),propertySet.getFloat("Scale Y") * getScaleY());
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		if(particleEffect!=null){
		    //particleEffect.scaleEffect(propertySet.getFloat("Scale X") * editor.getZoom(),propertySet.getFloat("Scale Y")*editor.getZoom());
			particleEffect.draw(batch,Gdx.graphics.getDeltaTime());
			if(particleEffect.isComplete())
				particleEffect.reset();
		}
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
		
		PropertySet<String,Object> temp = PropertySet.getDefualt(this,"particle.json");
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
		
		if(propertySet.containsKey("file") && !propertySet.getString("file").equals("")){
			FileHandle fileHandle = Gdx.files.absolute(editor.getProject().get("files")+propertySet.getString("file").replace(Utils.seperator,"/"));
			if(fileHandle.exists()){
				if(loadPath != null && loadPath.equals(fileHandle.path()))
					return;
				particleEffect.load(fileHandle,fileHandle.parent());
				particleEffect.scaleEffect(propertySet.getFloat("Scale X") * getScaleX(),propertySet.getFloat("Scale Y")*getScaleY());
				particleEffect.start();
				loadPath = fileHandle.path();
			} else loadDefault();
		} else loadDefault();
		
		update();
	}

	private void loadDefault(){
		if(loadPath != null && loadPath.equals("smoke/smoke.p"))
			return;
		String filesPath = editor.getProject().get("files");
		FileHandle smoke = Gdx.files.absolute(filesPath+"smoke/smoke.p"),
					particle = Gdx.files.absolute(filesPath+"smoke/particle-cloud.png");
		if(!(smoke.exists()&&particle.exists())){
			Gdx.files.internal("files/smoke.p").copyTo(smoke);
			Gdx.files.internal("files/particle-cloud.png").copyTo(particle);
		}
		particleEffect.load(smoke,smoke.parent());
		particleEffect.start();
		loadPath = "smoke/smoke.p";
	}
	
	@Override
	public String getTypeName() {
	    return "Particle";
	}
	
	@Override
	public void dispose() {
		if(particleEffect!=null)
			particleEffect.dispose();
	}
}