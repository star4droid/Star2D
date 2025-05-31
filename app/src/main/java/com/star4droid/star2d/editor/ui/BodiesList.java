package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
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
import com.star4droid.star2d.editor.ui.sub.EditorField;
import com.star4droid.star2d.editor.utils.EditorAction;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;

public class BodiesList extends Group {
  ListView<HashMap<String, Object>> listView;
  private final Vector2 tempCoords = new Vector2();
  LAdapter adapter;
  Drawable whiteDrawable;
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
	whiteDrawable = VisUI.getSkin().getDrawable("white");
    this.app = testApp;
    adapter = new LAdapter(testApp,new Array<>());
    listView = new ListView<HashMap<String, Object>>(adapter);
    listView.setUpdatePolicy(ListView.UpdatePolicy.ON_DRAW);
    listView.getMainTable().setFillParent(true);
	addActor(listView.getMainTable());
	listView.getScrollPane().setFlickScroll(true);
	listView.getScrollPane().setOverscroll(false,false);
	listView.getScrollPane().setScrollingDisabled(false,false);
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
	int start = name.contains("/") ? name.lastIndexOf("/") : 0;
	String namePure = start == 0 ? name.substring(0,name.indexOf(".")):name.substring(start+1,name.indexOf(".",start));
	try {
		return VisUI.getSkin().getDrawable(namePure);
		} catch(Exception e){
		Gdx.files.external("logs/bodies list drawable.txt").writeString("bodies list drawbale : "+namePure+", full : "+name+"\n error : "+e.toString()+"\n\n",true);
	}
    return new TextureRegionDrawable(new Texture(Gdx.files.internal("images/" + name)));
  }
  
  public void update(){
	  update(false);
  }

  public void update(boolean refresh) {
    int beforeSize = adapter.arrayList.size;
	try {
      adapter.arrayList.clear();
	  for (Actor actor : app.getEditor().getActors()) {
        if (!Utils.isEditorItem(actor)) continue;
        HashMap<String, Object> hash = new HashMap<>();
        PropertySet<String, Object> propertySet = PropertySet.getPropertySet(actor);
        if (propertySet == null) continue;
        if (!propertySet.containsKey("name")) continue;
		//Gdx.files.external("logs/bodies.txt").writeString("\nname : "+propertySet.getString("name")+", parent : ->"+propertySet.getString("parent")+"<-",true);
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
	if(adapter.arrayList.size == beforeSize && !refresh)
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
      /*if((!pst.getString("parent").equals(""))&&pst.getParent()==null){
		  Gdx.files.external("logs/parent.txt").writeString("\n\nfixing : "+pst.getString("name")+", parent : "+pst.getString("parent"),true);
		  boolean fixed = false;
		  String parents = "";
		  for(Actor actor : app.getEditor().getActors()){
			  if(actor instanceof EditorItem){
				  if(actor.getName().equals(pst.getString("parent"))){
				  	PropertySet<String,Object> prop = PropertySet.getPropertySet(actor);
					  pst.setParent(prop);
					  fixed = true;
					  break;
				  } else parents += actor.getName()+"\n";
			  }
		  }
		  Gdx.files.external("logs/parent.txt").writeString(((fixed)? "" : "not ")+"fixed : "+pst.getString("name")+", parents : "+parents,true); 
		  
	  }*/
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
	  if(true){
		  Image line = new Image(whiteDrawable);
		  Image line2 = new Image(whiteDrawable);
		  line.setName("line");
		  line2.setName("line2");
		  table.add(line2).padLeft(padding > 0 ? (22.5f + 45 * (padding - 1)) : 0).size(padding > 0 ? 5 : 1,padding > 0 ? 60 : 1);
		  table.add(line).size(padding > 0 ? 22.5f : 1,padding > 0 ? 6 : 1).padTop(10);
	  }
	  table.add(iconImg).padLeft(3).padTop(6).size(42,42);
	  table.add(label).padLeft(10).minHeight(60).growX();
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
			  actionMenu.addItem(new MenuItem("Rename",drawable("edit.png"),new ChangeListener() {
				  @Override
				  public void changed (ChangeEvent event, Actor actor) {
					  new SingleInputDialog("Input Value","Name : ",app.getEditor().getSelectedActor().getName(),(vl)->{
						  for(char c: vl.toCharArray()){
							  if(!EditorField.allowedChars.contains(String.valueOf(c))){
								  app.toast("use A-Z a-z or _ , Not Allowed Char : "+c);
								  return;
							  }
						  }
						  for(Actor act: app.getEditor().getActors())
							if(act instanceof EditorItem){
								if(act.getName().equals(vl)){
									app.toast("There\'s item with the same name!");
									return;
								}
							}
						  PropertySet<String,Object> ps = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
						  String old = ps.getString("name");
						  try {
        						FileHandle newHandle = Gdx.files.absolute(app.getEditor().getProject().getBodyScriptPath(ps.getString("name"),app.getEditor().getScene()));
								newHandle.moveTo(
        							Gdx.files.absolute(app.getEditor().getProject().getBodyScriptPath(vl,app.getEditor().getScene()))
        						);
								newHandle.writeString(newHandle.readString().replace(old+"Script",vl+"Script"),false);
        					} catch(Exception ex){}
						  ps.put("name",vl);
						  String scr = ps.getString("Script");
        				if((scr.contains("/") && scr.split("/")[0].equals(app.getEditor().getScene()) && scr.split("/")[1].equals(ps.getString("name"))) || (scr.equals(ps.getString("name"))&&!ps.getString("name").equals(ps.getString("name")))){
        					ps.put("Script",vl);
        				}
						  app.getEditor().getSelectedActor().setName(vl);
						  EditorAction.itemRenamed(app.getEditor(),old,vl).updateEditorProperties();
					  }).show(getStage());
				  }
			  }));
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
	  int padding = 0;
      PropertySet pst = getPropertyObj(hashMap.get("item"));
	  while (pst.getParent() != null) {
        pst = pst.getParent();
        padding++;
      }
	  
	  Actor lockActor = view.findActor("lock"),
	  	  visActor = view.findActor("vis"),
			iconActor = view.findActor("icon"),
			line = view.findActor("line"),
			line2 = view.findActor("line2");
	  if(line != null && line2 != null){
		  view.getCell(line2).padLeft(padding > 0 ? (22.5f + 45 * (padding - 1)) : 0).size(padding > 0 ? 5 : 1,padding > 0 ? 60 : 1);
		  view.getCell(line).size(padding > 0 ? 22.5f : 1,padding > 0 ? 6 : 1).padTop(10);
	  }
	  if(lockActor!=null){
		  ((Image)lockActor).setDrawable(pst.getString("lock").equals("true")?lock:unlock);
	  }
	  if(visActor!=null){
		  ((Image)visActor).setDrawable(pst.getString("Visible").equals("true")?visDrawable:invisDrawable);
	  }
	  if(iconActor!=null)
		((Image)iconActor).setDrawable(getBodyIcon(hashMap,icon));
	  setSelection(view,hashMap);
    }
	
	private void setSelection(VisTable view,HashMap<String,Object> hashMap){
	    try {
			if(app.getEditor()!=null)
		    	view.setBackground((app.getEditor().getSelectedActor()!=null&app.getEditor().getSelectedActor().getName().equals(hashMap.get("name").toString()))?selection:bg);
		} catch(Exception ex){}
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
