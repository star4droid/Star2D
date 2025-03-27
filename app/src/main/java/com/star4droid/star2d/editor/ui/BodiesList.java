package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.adapter.ArrayAdapter;
import com.kotcrab.vis.ui.widget.ListView;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.Utils;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.utils.EditorAction;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;

public class BodiesList extends Group {
  ListView<HashMap<String, Object>> listView;
  private final Vector2 tempCoords = new Vector2();
  LAdapter adapter;
  /*InputListener stageListener = new InputListener() {
		@Override
		public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				getStage().screenToStageCoordinates(tempCoords.set(x, y));
				if(listView.getMainTable().hit(tempCoords.x,tempCoords.y,true)!=null){
					setVisible(false);
				}
			return true;
		}
	};*/
  TestApp app;

  public BodiesList(TestApp testApp) {
    super();
    this.app = testApp;
    adapter = new LAdapter(testApp,new Array<>());
    listView = new ListView<HashMap<String, Object>>(adapter);
    listView.setUpdatePolicy(ListView.UpdatePolicy.ON_DRAW);
    listView.getMainTable().setFillParent(true);
	addActor(listView.getMainTable());
	listView.getScrollPane().setFlickScroll(true);
  }
  
  @Override
  protected void sizeChanged() {
	  super.sizeChanged();
	  listView.getMainTable().setSize(getWidth(),getHeight());
  }
/*  
  @Override
  protected void setStage(Stage stage) {
	  super.setStage(stage);
	  //if(stage!=null)
	  	//stage.addListener(stageListener);
  }
  
  @Override
  public boolean remove() {
	  //setVisible(false);
	  //if(getStage()!=null)
	  	//getStage().removeListener(stageListener);
	  return super.remove();
  }
*/
  public static Drawable drawable(String name) {
    return new TextureRegionDrawable(new Texture(Gdx.files.internal("images/" + name)));
  }

  public void update() {
    int beforeSize = adapter.arrayList.size;
	try {
      adapter.arrayList.clear();
	  for (Actor actor : app.getEditor().getActors()) {
        if (!Utils.isEditorItem(actor)) continue;
        HashMap<String, Object> hash = new HashMap<>();
        PropertySet<String, Object> propertySet = PropertySet.getPropertySet(actor);
        if (propertySet == null) continue;
        if (!propertySet.containsKey("name")) continue;
        if (!propertySet.getString("parent").equals("")) continue;
        hash.put("name", propertySet.getString("name"));
        hash.put("item", actor);
        adapter.arrayList.add(hash);
        // add item childs after it...
        for (PropertySet set : getChilds(propertySet, null)) {
          HashMap<String, Object> hashMap = new HashMap<>();
          hashMap.put("name", set.getString("name"));
		  Actor actorByName = app.getEditor().findActor(set.getString("name"));
          hashMap.put("item", actorByName != null ? actorByName : set);
          adapter.arrayList.add(hashMap);
        }
      }
    } catch (Exception exc) {
		app.toast("update error : "+exc.toString());
	}
	int selected = 0;
	if(adapter.arrayList.size == beforeSize)
		adapter.itemsDataChanged();
	else adapter.itemsChanged();
  }

  private Array<PropertySet> getChilds(PropertySet propertySet, Array<PropertySet> arrayList) {
    if (arrayList == null) arrayList = new Array<>();
    for (int x = 0; x < propertySet.getChilds().size(); x++) {
      arrayList.add((PropertySet) propertySet.getChilds().get(x));
      getChilds((PropertySet) propertySet.getChilds().get(x), arrayList);
    }
    return arrayList;
  }

  private class LAdapter extends ArrayAdapter<HashMap<String, Object>, VisTable> {
    private final Drawable bg = VisUI.getSkin().getDrawable("window-bg");
    private final Drawable selection = VisUI.getSkin().getDrawable("list-selection");
    private final Drawable visDrawable, invisDrawable, icon, lock, unlock, dots;
    Array<HashMap<String, Object>> arrayList;
	PopupMenu actionMenu = new PopupMenu();
	TestApp app;

    public LAdapter(TestApp app,Array<HashMap<String, Object>> array) {
      super(array);
	  this.app = app;
      visDrawable = drawable("visible.png");
      invisDrawable = drawable("invisible.png");
      lock = drawable("lock.png");
      unlock = drawable("unlock.png");
      dots = drawable("dots.png");
	  icon = drawable("logo.png");
      setSelectionMode(SelectionMode.SINGLE);
	  this.arrayList = array;
    }

    public Array<HashMap<String, Object>> getArray() {
      return arrayList;
    }
	
	@Override
	public void itemsChanged() {
		super.itemsChanged();
		
	}

	@Override
	public void itemsDataChanged() {
		super.itemsDataChanged();
	}

    @Override
    protected VisTable createView(final HashMap<String, Object> hashMap) {
      int padding = 0;
      PropertySet pst = getPropertyObj(hashMap.get("item"));
      while (pst.getParent() != null) {
        pst = pst.getParent();
        padding++;
      }
	  VisLabel label = new VisLabel(hashMap.get("name").toString());
      VisTable table = new VisTable();
      Image dotsImg = new Image(dots),
	  	iconImg = new Image(icon),
		  lockImg = new Image(getPropertyObj(hashMap.get("item")).getString("lock").equals("true")?lock:unlock), 
	  	visImg = new Image(getPropertyObj(hashMap.get("item")).getString("Visible").equals("true")?visDrawable:invisDrawable);
	  table.add(iconImg).pad(3).size(42,42);
	  table.add(label).padLeft(10 + 2*padding).minHeight(60).growX();
      table.add(visImg).size(45, 45).pad(2).space(2);
      table.add(lockImg).size(45, 45).pad(2).space(2);
      table.add(dotsImg).size(45, 45).pad(2);
      visImg.setName("vis");
      lockImg.setName("lock");
	  iconImg.setName("icon");
	  
	  iconImg.setDrawable(getBodyIcon(hashMap,icon));
	  
	  table.addListener(new ClickListener(){
		  @Override
		  public void clicked (InputEvent event, float x, float y) {
			  Object actorObj = hashMap.get("item");
			  if(actorObj != null && actorObj instanceof Actor){
				  app.getEditor().selectActor((Actor)actorObj);
			  }
		  }
	  });
	  
	  visImg.addListener(new ClickListener(){
		  @Override
		  public void clicked (InputEvent event, float x, float y) {
			  switchVisibility(hashMap,visImg);
		  }
	  });
	  lockImg.addListener(new ClickListener(){
		  @Override
		  public void clicked (InputEvent event, float x, float y) {
			  switchLock(hashMap,lockImg);
		  }
	  });
	  dotsImg.addListener(new ClickListener(){
		  @Override
		  public void clicked (InputEvent event, float x, float y) {
			  actionMenu.clear();
			  MenuItem delItem = new MenuItem("Delete",drawable("delete.png"),new ChangeListener() {
				  @Override
				  public void changed (ChangeEvent event, Actor actor) {
					  new ConfirmDialog("Delete","Are you sure ?",(ok)->{
						  if(ok && hashMap.get("item") instanceof Actor){
							Actor ac = ((Actor)hashMap.get("item"));
							ac.remove();
							update();
							EditorAction.itemRemoved(app.getEditor(),ac);
						  }
					  }).show(getStage());
				  }
			  });
			  actionMenu.addItem(delItem);
			  MenuItem copyItem = new MenuItem("Copy",drawable("copy.png"),new ChangeListener() {
				  @Override
				  public void changed (ChangeEvent event, Actor actor) {
					  try {
						  Constructor<?> cc =
                              hashMap.get("item").getClass().getConstructor(LibgdxEditor.class);
                          EditorItem i = (EditorItem) cc.newInstance(app.getEditor());
                          PropertySet<String, Object> ps = new PropertySet<>();
                          ps.putAll(getPropertyObj(hashMap.get("item")));
                          String name = getRealNameAndNum(getPropertyObj(hashMap.get("item")).getString("name"));
                          String result = name.split(" ")[0];
                          int num = Utils.getInt(name.split(" ")[1]);
                          ArrayList<String> bodies = app.getEditor().getBodiesList();
                          while (bodies.contains(result + num)) num++;
                          ps.put("name", result + num);
						  ((Actor)i).setName(result + num);
						  i.setProperties(ps);
						  i.update();
						  app.getEditor().selectActor((Actor)i);
						  EditorAction.itemAdded(app.getEditor(),(Actor)i);
					  } catch(Exception exception){
						  app.toast("copy error : "+exception.toString());
						  exception.printStackTrace();
					  }
				  }
			  });
			  actionMenu.addItem(copyItem);
			  actionMenu.showMenu(app.getUiStage(),dotsImg);
			  
		  }
	  });
	  setSelection(table,hashMap);
      return table;
    }
	
	private Drawable getBodyIcon(HashMap<String,Object> hashMap,Drawable elseDrawable){
		Drawable drawable = elseDrawable;
		if(hashMap.get("item") instanceof Image){
			drawable = ((Image)hashMap.get("item")).getDrawable();
		}
		return drawable;
	}
	
	private Object getItem(int pos){
		return arrayList.get(pos).get("item");
	}
	
	public PropertySet<String, Object> getPropertyObj(Object obj) {
		if (obj instanceof PropertySet)
        return (PropertySet<String, Object>) obj;
      return ((EditorItem) obj).getPropertySet();
	}
	
	public PropertySet<String, Object> getProperty(int position) {
      return getPropertyObj(getItem(position));
    }

    private void switchLock(HashMap<String,Object> hashMap, Image image) {
      String from = getPropertyObj(hashMap.get("item")).getString("lock");
	  String to = from.equals("true") ? "false" : "true";
      getPropertyObj(hashMap.get("item")).put("lock", to);
      if(hashMap.get("item") instanceof EditorItem)
	  	((EditorItem) hashMap.get("item")).update();
      if (image == null) {
		  itemsDataChanged();
      } else image.setDrawable(to.equals("true") ? lock : unlock);
	  app.getEditor().updateProperties();
	  EditorAction.propertiesChanged(app.getEditor(),hashMap.get("name").toString(),"lock",new String[]{"lock",to},new String[]{"lock",from}).updateItemProperties().updateEditorProperties();
    }

    private void switchVisibility(HashMap<String,Object> hashMap, Image image) {
      String from = getPropertyObj(hashMap.get("item")).getString("Visible");
	  String to = from.equals("true") ? "false" : "true";
      getPropertyObj(hashMap.get("item")).put("Visible", to);
	  EditorAction.propertiesChanged(app.getEditor(),hashMap.get("name").toString(),"Visible",new String[]{"Visible",to},new String[]{"Visible",from}).updateItemProperties().updateEditorProperties();
      if(hashMap.get("item") instanceof EditorItem)
	  	((EditorItem) hashMap.get("item")).update();
      if (image == null) {
		  itemsDataChanged();
	  } else image.setDrawable(to.equals("true") ? visDrawable : invisDrawable);
	   EditorAction.propertiesChanged(app.getEditor(),hashMap.get("name").toString(),"Visible",new String[]{"Visible",to},new String[]{"Visible",from}).updateItemProperties().updateEditorProperties();
    }

    @Override
    protected void updateView(VisTable view, HashMap<String, Object> hashMap) {
      super.updateView(view, hashMap);
	  Actor lockActor = view.findActor("lock"),
	  	  visActor = view.findActor("vis"),
			iconActor = view.findActor("icon");
	  if(lockActor!=null){
		  ((Image)lockActor).setDrawable(getPropertyObj(hashMap.get("item")).getString("lock").equals("true")?lock:unlock);
	  }
	  if(visActor!=null){
		  ((Image)visActor).setDrawable(getPropertyObj(hashMap.get("item")).getString("Visible").equals("true")?visDrawable:invisDrawable);
	  }
	  if(iconActor!=null)
		((Image)iconActor).setDrawable(getBodyIcon(hashMap,icon));
	  setSelection(view,hashMap);
    }
	
	private void setSelection(VisTable view,HashMap<String,Object> hashMap){
		view.setBackground((app.getEditor().getSelectedActor()!=null&app.getEditor().getSelectedActor().getName().equals(hashMap.get("name").toString()))?selection:bg);
	}

    @Override
    protected void selectView(VisTable view) {
      //view.setBackground(selection);
    }

    @Override
    protected void deselectView(VisTable view) {
      //view.setBackground(bg);
    }
  }
  
  public static String getRealNameAndNum(final String _str) {
    try {
      String str = _str;
      String nums = "1234567890";
      String out = "";
      while (nums.contains("" + str.charAt(str.length() - 1))) {
        out = str.charAt(str.length() - 1) + out;
        str = str.substring(0, str.length() - 1);
      }
      if (out.equals("")) out = "1";
      return str + " " + out;
    } catch (Exception e) {
      return (_str);
    }
  }
}
