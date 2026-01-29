package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisImageTextButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisWindow;
import com.star4droid.star2d.editor.ui.sub.inputs.FloatInput;
import com.star4droid.template.Utils.ProjectAssetLoader;
import com.star4droid.template.Utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import static com.star4droid.star2d.editor.utils.Lang.*;
/*
	TODO : Undo/Redo...
	TODO : Cancel (don't save changes automatically)
	TODO : Don't refresh if the same animation opened...
*/
public class AnimationEditor extends VisWindow {
	float animationTime = 0;
	int depth = 0;
	Animation<Drawable> animation;
	String jsonPath,imagesPath,jsonOnStart;
	FileHandle currentDir;
	ProjectAssetLoader assetLoader;
	Drawable folder;
	VisImage image;
	FloatInput durationInput;
	VisTextButton imagesTxt,animTxt;// text disbalyed on the top of the lists...
	VisTable imagesTable,animationTable;
	ArrayList<HashMap<String,Object>> animationArray = new ArrayList<>();
	public AnimationEditor(){
		super(getTrans("animationEditor"));
		folder = VisUI.getSkin().getDrawable("folder-white");
		imagesTxt = new VisTextButton(getTrans("images"));
		//imagesTxt.setDisabled(true);
		animTxt = new VisTextButton(getTrans("animation"));
		//animTxt.setDisabled(true);
		setFillParent(true);
		VisTable imgTable = new VisTable();
		image = new VisImage();
		imgTable.add().growX();
		imgTable.add(image).size(50,50).pad(5);
		durationInput = new FloatInput();
		durationInput.setValue("300");
		durationInput.setNameText(getTrans("duration"));
		durationInput.setOnChange(()->{
			try {
				if(animation!=null)
					animation.setFrameDuration(Utils.getFloat(durationInput.getValue())*0.001f);
				for(HashMap<String,Object> hashMap:animationArray)
					hashMap.put("dur",durationInput.getValue());
				save();
			} catch(Exception e){
				
			}
		});
		imgTable.add(durationInput).pad(5);
		imgTable.add().growX();
		add(imgTable).padTop(5).row();
		imagesTable = new VisTable();
		animationTable = new VisTable();
		VisScrollPane imagesScroll = new VisScrollPane(imagesTable),
				animationScroll = new VisScrollPane(animationTable);
		imagesScroll.setFlickScroll(true);
		animationScroll.setFlickScroll(true);
		
		imagesScroll.setOverscroll(false,false);
		animationScroll.setOverscroll(false,false);
		VisSplitPane pane = new VisSplitPane(imagesScroll,animationScroll,false);
		add(pane).grow().pad(7).row();
		VisImageTextButton cancel = new VisImageTextButton(getTrans("cancel"),VisUI.getSkin().getDrawable("clear-icon")),
			save = new VisImageTextButton(getTrans("save"),VisUI.getSkin().getDrawable("save"));
		VisTable bottom = new VisTable();
		bottom.add(save).padRight(5).padLeft(5).growX();
		bottom.add(cancel).padRight(5).growX();
		add(bottom);
		
		save.addListener(new ClickListener(){
			@Override
			public void clicked(InputEvent arg0, float arg1, float arg2) {
				jsonOnStart = null;
				remove();
			}
		});
		cancel.addListener(new ClickListener(){
			@Override
			public void clicked(InputEvent arg0, float arg1, float arg2) {
				Gdx.files.absolute(jsonPath).writeString(jsonOnStart,false);
				jsonOnStart = null;
				remove();
			}
		});
	}
	
	private void save(){
		try {
			String json = new Gson().toJson(animationArray);
			Gdx.files.absolute(jsonPath).writeString(json,false);
		} catch(Exception e){
			
		}
	}
	
	private void refreshImages(){
		imagesTable.clear();
		imagesTable.add(imagesTxt).growX().row();
		FileHandle[] files = currentDir.list();
		if(depth > 0){
			FileHandle[] temp = new FileHandle[files.length + 1];
			temp[0] = null;
			for(int i = 0; i < files.length; i++){
				temp[i + 1] = files[i];
			}
			files = temp;
		}
		for(FileHandle fileHandle:files){
			try {
				VisTable table = new VisTable();
				VisImage image = (fileHandle == null || fileHandle.isDirectory()) ? new VisImage(folder) : new VisImage(assetLoader.contains(fileHandle.path())?assetLoader.get(fileHandle.path()) : new Texture(fileHandle));
				VisLabel label = new VisLabel(fileHandle == null ? "..." : fileHandle.name());
				table.add(image).size(35,35).pad(5);
				table.add(label).growX();
				imagesTable.add(table).growX().padBottom(5).row();
				ClickListener clickListener = new ClickListener(){
					@Override
					public void clicked(InputEvent arg0, float arg1, float arg2) {
						try {
							if(fileHandle==null){
								if(depth > 0){
									currentDir = currentDir.parent();
									depth --;
									refreshImages();
									return;
								}
							} else if(fileHandle.isDirectory()){
								currentDir = fileHandle;
								depth++;
								refreshImages();
								return;
							}
							HashMap<String,Object> hashMap = new HashMap<>();
							String name = fileHandle.name();
							FileHandle temp = fileHandle;
							if(depth > 0)
								for(int i = 0; i < depth; i++){
									name = "/"+temp.parent().name()+(name.startsWith("/") ? "" : "/")+name;
									temp = temp.parent();
								}
							hashMap.put("name",name.startsWith("/") ? name.substring(1,name.length()) : name);
							hashMap.put("dur",durationInput.getValue());
							animationArray.add(hashMap);
							/*
							Drawable[] frames = animation.getKeyFrames();
							Drawable[] newFrames = new Drawable[frames.length + 1];
							for(int i = 0; i < frames.length; i++)
								newFrames[i] = frames[i];
							newFrames[newFrames.length > 0 ? (newFrames.length-1) : 0] = image.getDrawable();
							setFrames(newFrames);
							*/
							save();
							addAnimationImages();
						} catch(Exception ex){
							
						}
					}
				};
				image.addListener(clickListener);
				label.addListener(clickListener);
			} catch(Exception e){}
		}
	}
	
	private void setFrames(Drawable[] frames){
		animation = new Animation<>(animation.getFrameDuration(),frames);
		animation.setPlayMode(Animation.PlayMode.LOOP);
	}
	
	private void log(String log){
		Gdx.files.external("logs/animationEditor.txt").writeString(log+"\n"+"_".repeat(10)+"\n",true);
	}
	
	private void refreshList(){
		try {
			FileHandle fileHandle = Gdx.files.absolute(jsonPath);
			if(fileHandle.exists()){
				String content = fileHandle.readString();
				if(!content.equals(""))
					animationArray = new Gson().fromJson(content, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
				else animationArray = new ArrayList<>();
			}
		} catch(Exception e){}
	}
	
	private void addAnimationImages(){
		animationTable.clear();
		animationTime = 0;
		Drawable def = VisUI.getSkin().getDrawable("logo");
		image.setDrawable(def);
		animationTable.add(animTxt).growX().row();
		try {
			if(animationArray.size() > 0){
				//log("animation array length : "+animationArray.size());
				float dur=0.3f;//300ms
				try {
					dur = Utils.getFloat(animationArray.get(0).get("dur").toString())*0.001f;
				} catch(Exception exception){}
				if(animationArray.size() > 0)
				durationInput.setValue(animationArray.get(0).get("dur").toString());
				Drawable[] drawables= new Drawable[animationArray.size()];
				int x=0;
				VisTable[] tables = new VisTable[animationArray.size()];
				for(HashMap<String,Object> hashMap:animationArray){
					String img = (imagesPath+"/"+hashMap.get("name").toString().replace(Utils.seperator,"/")).replace("//","/");
					while(img.contains("//")) img = img.replace("//","/");
					drawables[x] = assetLoader.contains(img)?Utils.getDrawable(assetLoader.get(img,Texture.class)):def;
					VisTable table = new VisTable();
					VisLabel label = new VisLabel(hashMap.get("name").toString().replace(Utils.seperator,"/"));
					VisImage image = new VisImage(drawables[x]);
					VisImageButton up = new VisImageButton(VisUI.getSkin().getDrawable("up")),
						del = new VisImageButton(VisUI.getSkin().getDrawable("delete"));
					
					up.addListener(new ClickListener(){
						@Override
						public void clicked(InputEvent arg0, float arg1, float arg2) {
							int pos = getPos(hashMap,animationArray);
							try {
								if(animationArray.size() < 2 || pos == 0) return;
								HashMap<String,Object> temp = animationArray.get(pos - 1);
								animationArray.set(pos - 1,animationArray.get(pos));
								animationArray.set(pos, temp);
								/*
								Drawable[] frames = animation.getKeyFrames();
								Drawable frame = frames[pos - 1];
								frames[pos - 1] = frames[pos];
								frames[pos] = frame;
								float dur=0.3f;//300ms
								try {
									dur = Utils.getFloat(animationArray.get(0).get("dur").toString())*0.001f;
								} catch(Exception exception){}
								animation = new Animation<>(dur,frames);
								
								animationTable.getCells().get(pos).setActor(tables[pos - 1]);
								animationTable.getCells().get(pos - 1).setActor(tables[pos]);
								VisTable tmp = tables[pos - 1];
								tables[pos - 1] = tables[pos];
								tables[pos] = tmp;
								*/
								save();
								addAnimationImages();
							} catch(Exception ex){
								
							}
						}
					});
					
					del.addListener(new ClickListener(){
						@Override
						public void clicked(InputEvent arg0, float arg1, float arg2) {
							int pos = getPos(hashMap,animationArray);
							try {
								if(animationArray.size() <= 0) return;
								animationArray.remove(hashMap);
								if(animationArray.size() > 0){
									Drawable[] frames = animation.getKeyFrames();
									frames[pos] = null;
									Drawable[] newFrames = new Drawable[frames.length-1];
									for(int i = 0,j = 0; i < frames.length; i++){
										if(frames[i]!=null){
											newFrames[j] = frames[i];
											j++;
										}
									}
									setFrames(newFrames);
								} else image.setDrawable(def);
								table.remove();
								
								save();
								//addAnimationImages();
							} catch(Exception ex){
								log("Delete Error : "+ex.toString());
							}
						}
					});
					table.add(image).size(35,35);
					table.add(label).growX();
					table.add(up).size(50,35).pad(5);
					table.add(del).size(50,35).padRight(5);
					if(x > 0) animationTable.row();
					animationTable.add(table).growX();
					x++;
				}
				//log("frame duration : "+String.format("%.5g%n", dur));
				animation = new Animation<>(dur,drawables);
				animation.setPlayMode(Animation.PlayMode.LOOP);
			}
		} catch(Exception exception){
			log("Error add animation images : "+exception.toString());
		}
	}
	
	private int getPos(HashMap<String,Object> hashMap,ArrayList<HashMap<String,Object>> arrayList){
		hashMap.put("id",new java.util.Random().nextInt(Integer.MAX_VALUE));
		for(int x = 0; x < arrayList.size(); x++)
			if(arrayList.get(x).containsKey("id") && arrayList.get(x).get("id").equals(hashMap.get("id"))){
				hashMap.remove("id");
				return x;
			}
		hashMap.remove("id");
		return -1;
	}
	
	public AnimationEditor setPaths(String imagesPath,String jsonPath){
		this.imagesPath = imagesPath;
		this.jsonPath = jsonPath;
		this.currentDir = Gdx.files.absolute(imagesPath);
		return this;
	}
	
	public AnimationEditor setAssetLoader(ProjectAssetLoader assetLoader){
		this.assetLoader = assetLoader;
		return this;
	}
	
	public AnimationEditor refresh(){
		if(assetLoader==null)
			throw new RuntimeException("Asset Loader can\'t be null!!");
		if(!(imagesPath!=null && jsonPath!=null && !(imagesPath.equals("")|| jsonPath.equals(""))))
			throw new RuntimeException("images path and json path is not sst correctly!");
		currentDir = Gdx.files.absolute(imagesPath);
		depth = 0;
		FileHandle fileHandle = Gdx.files.absolute(jsonPath);
		jsonOnStart = fileHandle.exists() ? fileHandle.readString() : "";
		refreshImages();
		refreshList();
		addAnimationImages();
		return this;
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		if(animation!=null){
			//if(animation.isAnimationFinished(animationTime))
				//animationTime = 0;
			image.setDrawable(animation.getKeyFrame(animationTime,true));
			animationTime += Gdx.graphics.getDeltaTime();
		}
	}
	
	public void show(Stage stage){
		stage.addActor(this);
		toFront();
	}
}