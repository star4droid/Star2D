package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.VisUI;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.util.adapter.ArrayAdapter;
import com.kotcrab.vis.ui.widget.ListView;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.JointInputs.JointDialog;
import com.star4droid.star2d.editor.TestApp;
import java.util.HashMap;

public class JointsList extends VisTable {
	TestApp app;
	LAdapter adapter;
	ListView listView;
	public JointsList(TestApp app){
		this.app = app;
		adapter = new LAdapter(new Array<>());
		listView = new ListView<HashMap<String, Object>>(adapter);
		listView.setUpdatePolicy(ListView.UpdatePolicy.ON_DRAW);
		listView.getScrollPane().setFlickScroll(true);
		add(listView.getMainTable()).grow().row();
		VisTextButton addBtn = new VisTextButton("Add Joint");
		addBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				JointDialog.showJointListDialog(()->{
					Gdx.app.postRunnable(()->adapter.refresh());
				});
			}
		});
		add(addBtn).pad(5).growX();
		adapter.refresh();
	}
	
	public void refresh(){
		adapter.refresh();
	}
	
	private class LAdapter extends ArrayAdapter<HashMap<String, Object>, VisTable> {
    	private final Drawable bg = VisUI.getSkin().getDrawable("window-bg");
		Array<HashMap<String,Object>> array;
		
		public LAdapter(Array<HashMap<String,Object>> array){
			super(array);
			this.array = array;
		}
		
		@Override
		protected void updateView(VisTable table, HashMap<String, Object> hashMap) {
			Actor labelActor = table.findActor("label");
			if(labelActor!=null)
				((VisLabel)labelActor).setText(hashMap.get("name").toString());
		}

		@Override
		protected VisTable createView(HashMap<String, Object> hashMap) {
		    VisTable table = new VisTable();
			VisImage image = new VisImage(drawable("link.png"));
			VisLabel label = new VisLabel(hashMap.get("name").toString());
			
			image.setName("image");
			label.setName("label");
			
			table.left();
			table.add(image).padLeft(8).padRight(8);
			table.add(label);
			table.setBackground(bg);
			table.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					PopupMenu popupMenu = new PopupMenu();
					popupMenu.addItem(new MenuItem("Edit",new ChangeListener() {
						@Override
						public void changed (ChangeEvent event, Actor actor) {
							jointOpen(hashMap);
						}
					}));
					popupMenu.addItem(new MenuItem("Delete",new ChangeListener() {
						@Override
						public void changed (ChangeEvent event, Actor actor) {
							new ConfirmDialog("Delete","Are you sure ?",(ok)->{
								if(ok){
									deleteJoint(hashMap);
									refresh();
								}
							}).show(getStage());
						}
					}));
					popupMenu.showMenu(getStage(),table);
				}
			});
			return table;
		}
		
		private void deleteJoint(HashMap<String,Object> hashMap){
			try {
				String path = app.getEditor().getProject().getJoints(app.getEditor().getScene())+hashMap.get("name").toString();
				Gdx.files.absolute(path).delete();
			} catch(Exception exception){}
		}
		
		private void jointOpen(HashMap<String,Object> hashMap){
			final String nm=hashMap.get("name").toString();
			new JointDialog(nm.split("-")[1],nm.split("-")[0]){
				public void onDone(String string,String name){
					Gdx.app.postRunnable(()->{
						Gdx.files.absolute(app.getEditor().getProject().getJoints(app.getEditor().getScene())+nm).writeString(string,false);
						refresh();
					});
				}
			}.setValue(Gdx.files.absolute(app.getEditor().getProject().getJoints(app.getEditor().getScene())+nm).readString());
		}
		
		public void refresh(){
			Array<String> joints= new Array<>();
			FileHandle[] files = Gdx.files.absolute(app.getEditor().getProject().getJoints(app.getEditor().getScene())).list();
			array.clear();
			for(int x=0;x<files.length;x++){
				String path=files[x].name();
				HashMap<String,Object> hashMap = new HashMap<>();
				hashMap.put("name",path);
				array.add(hashMap);
			}
			itemsChanged();
		}
	}
	
	public static Drawable drawable(String name) {
		return new TextureRegionDrawable(new Texture(Gdx.files.internal("images/" + name)));
	}
}