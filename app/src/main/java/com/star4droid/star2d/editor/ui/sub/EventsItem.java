package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.util.adapter.ArrayAdapter;
import com.kotcrab.vis.ui.widget.ListView;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.Adapters.VisualScriptingDialog;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.SingleInputDialog;
import com.star4droid.template.Utils.Utils;
import java.util.HashMap;

public class EventsItem extends Table {
	TestApp app;
	ListView listView;
	LAdapter adapter;
	String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_";
	public EventsItem(TestApp testApp){
		super();
		this.app = testApp;
		adapter = new LAdapter(new Array<>());
		listView = new ListView<HashMap<String, Object>>(adapter);
		listView.setUpdatePolicy(ListView.UpdatePolicy.ON_DRAW);
		listView.getMainTable().setFillParent(true);
		add(listView.getMainTable()).grow();
		listView.getScrollPane().setFlickScroll(true);
		pushEvents();
		
		/*
		VisTextButton bodyScript = new VisTextButton("Body Script");
		bodyScript.addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				if(bodyScriptRunnable!=null)
					bodyScriptRunnable.run();
			}
		});
		*/
		
		VisTextButton addScript = new VisTextButton("Add Script");
		addScript.addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				new SingleInputDialog("Add Script","Name : ","Example",(name)->{
					FileHandle path = Gdx.files.absolute(app.getEditor().getProject().get("scripts")+name+".java");
					if(path.exists()) {
						app.toast("There is already script with this name...!");
						return;
					}
					if(name.equals("Body Script")) {
						app.toast("Not allowed name!!");
						return;
					}
					for(char c:name.toCharArray()){
						if(!chars.contains(String.valueOf(c))){
							app.toast("use A-Z a-z or _ , Not Allowed Char : "+c);
							return;
						}
					}
					path.writeString("",false);
					if(path.exists()){
						push(name,"code",false,true);
						adapter.itemsChanged();
					}
				}).show(getStage());
			}
		});
		row();
		add(addScript).height(60).pad(3).center().growX();
	}
	
	private class LAdapter extends ArrayAdapter<HashMap<String, Object>, VisTable> {
		Array<HashMap<String,Object>> array;
		public LAdapter(Array<HashMap<String,Object>> array){
			super(array);
			this.array = array;
		}
		
		@Override
		protected VisTable createView(final HashMap<String, Object> hashMap) {
			VisTable table = new VisTable();
			VisLabel label = new VisLabel(hashMap.get("name").toString());
			label.setName("label");
			VisImage image = new VisImage(Utils.getDrawable(Gdx.files.internal("images/events/"+hashMap.get("icon").toString())));
			table.add(image).size(70,70);
			table.add(label).padLeft(4).growX();
			table.addListener(new ClickListener(){
				@Override
				public void clicked (InputEvent event, float x, float y) {
					boolean bodyScript = hashMap.get("name").toString().equals("Body Script");
					/*if(bodyScript){
						VisualScriptingDialog.openCodeEditor();
					} else
					*/
					if(hashMap.get("icon").toString().equals("code.png") || bodyScript){
						PopupMenu popupMenu = new PopupMenu();
						popupMenu.addItem(new MenuItem("Open",new ChangeListener() {
							@Override
							public void changed (ChangeEvent event, Actor actor) {
							    if(bodyScript){
            						VisualScriptingDialog.openCodeEditor();
            					} else
								VisualScriptingDialog.showFor(hashMap.get("name").toString(),hashMap.get("body").toString().equals(""),hashMap.get("script").toString().equals("true"));
							}
						}));
						popupMenu.addItem(new MenuItem("Delete",new ChangeListener() {
							@Override
							public void changed (ChangeEvent event, Actor actor) {
								new ConfirmDialog("Delete","Are you sure ?",(ok)->{
									if(ok){
										if(bodyScript){
											if(app.getEditor().getSelectedActor()==null) return;
											PropertySet ps= PropertySet.getPropertySet(app.getEditor().getSelectedActor());
											FileHandle fileHandle = Gdx.files.absolute(app.getEditor().getProject().getBodyScriptPath(ps.getString("name"),app.getEditor().getScene()));
											if(fileHandle.exists())
											    fileHandle.delete();
											app.toast("Body Script Erased...."+fileHandle.name(),3);
											} else {
											String path = app.getEditor().getProject().get("scripts")+hashMap.get("name");
											if(hashMap.get("script").equals("true")) {
												delete(path+".java");
												delete(path+".visual");
												app.toast("path : "+path+".java/visual deleted");
												//FileUtil.deleteFile(path+".code");
												int pos = 0;
												for(HashMap<String,Object> hashMap2:array){
													if(!hashMap2.get("icon").toString().equals("code.png")) {
														pos++;
														continue;
													}
													if(hashMap2.get("name").equals(hashMap.get("name").toString())){
														break;
													}
													pos++;
												}
												if(pos < array.size)
												array.removeIndex(pos);
												itemsChanged();
											}
										}
									}
								}).show(getStage());
							}
						}));
						popupMenu.showMenu(getStage(),table);
					} else VisualScriptingDialog.showFor(hashMap.get("name").toString(),hashMap.get("body").toString().equals("true"),hashMap.get("script").toString().equals("true"));
				}
			});
			return table;
		}
		
		@Override
		protected void updateView(VisTable table, HashMap<String, Object> hashMap) {
			Actor label = table.findActor("label");
			if(label!=null){
				((VisLabel)label).setText(hashMap.get("name").toString());
			}
		}
	}
	
	private void delete(String path){
		FileHandle fileHandle = Gdx.files.absolute(path);
		if(fileHandle.exists())
			fileHandle.delete();
	}
	
	private void pushEvents(){
		push("OnCreate","properties",false,false);
		push("OnStep","step_icon",false,false);
		push("OnPause","pause",false,false);
		push("OnResume","ic_play_arrow_black",false,false);
		push("onCollisionStart","two_collision",false,false);
		push("onCollisionEnd","collision_end_icon",false,false);
		push("onClick","mouse_click",true,false);
		push("OnBodyCreated","body_created_icon",true,false);
		push("OnBodyUpdate","on_update_icon",true,false);
		push("onTouchStart","touch_start_icon",true,false);
		push("onTouchEnd","touch_end_icon",true,false);
		push("onBodyCollided","two_collision",true,false);
		push("onBodyCollideEnd","collision_end_icon",true,false);
		push("Body Script","code",true,true);
		
		FileHandle[] scripts = Gdx.files.absolute(app.getEditor().getProject().get("scripts")).list();
		
		for(int x=0;x<scripts.length;x++){
			String path= scripts[x].name();
			if(path.endsWith(".java")){
				push(path.replace(".java",""),"code",false,true);
			}
		}
		adapter.itemsChanged();
	}
	
	private void push(String name,String icon,boolean body,boolean script){
		HashMap<String,Object> hashMap = new HashMap<>();
		hashMap.put("name",name);
		hashMap.put("icon",icon+".png");
		hashMap.put("body",String.valueOf(body));
		hashMap.put("script",String.valueOf(script));
		adapter.array.add(hashMap);
	}
}