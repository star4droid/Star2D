package com.star4droid.star2d.editor.ui.custom;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.StringBuilder;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.items.CustomItem;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.Utils.Utils;

/*
    Custom Collider Editor to edit custom collider shape of Custom Body
    Custom Body > collider > edit
    > Made By Mutwakil Souliman
    Modified And improved By Annas Osman (Star4Droid)
    Date : 13 April 2025 , Time : 01 : 11 PM
    Status : I've exam about programming and computer basics tomorrow 😴...
    Comments By : Annas Osman (Star4Droid)
*/

public class CustomColliderEditor extends VisDialog {
  private TestApp app;
  private CustomItem customBody;
  private VisImage colliderImage;
  private ShapeRenderer renderer = new ShapeRenderer();
  private Group pointsGroup;
  private VisTable pointsTable;
  private VisScrollPane scrollPane;
  private VisTextButton addPoint,deletePoint,save,cancel;
  private Point selectedPoint;
  private VisLabel debugLabel;
  public CustomColliderEditor(TestApp app) {
    super("Custom Collider Editor");
    this.app = app;
    
    setFillParent(true);
	
    // VisDialog is maked a default settings in add() using defaults() in it table so I use reset()
    // to remove it
    reset();
	// item image ...
	colliderImage = new VisImage();
	debugLabel = new VisLabel("Debug Text");
	// the text go to the next line when the line is full...
	debugLabel.setWrap(true);
	//colliderImage.setFillParent(true);
	// image width/height 55% of the screen...
	colliderImage.setSize(width(0.55f),height(0.55f));
	
	// table contains everything...
	VisTable mainTable = new VisTable();
	//group contains points and the image...
	Group group = new Group();
	pointsGroup = new Group();
	group.addActor(colliderImage);
	group.addActor(pointsGroup);
	
	mainTable.add(group).bottom().left().grow();
	
	// buttons...
    addPoint = new VisTextButton("Add Point");
	deletePoint = new VisTextButton("Delete Point");
	save = new VisTextButton("Save");
	cancel = new VisTextButton("Cancel");
	VisTable btnsTable = new VisTable();
	btnsTable.defaults().padLeft(5).padTop(8).padRight(5).growX();
	btnsTable.add(addPoint).row();
	btnsTable.add(deletePoint).row();
	btnsTable.add(save).row();
	btnsTable.add(cancel).row();
	//btnsTable.add(debugLabel);
	//table contains the points list selectable...
	pointsTable = new VisTable();
    scrollPane = new VisScrollPane(pointsTable);
    scrollPane.setVariableSizeKnobs(true);
    scrollPane.setScrollingDisabled(true, false);
    scrollPane.setScrollbarsVisible(false);
    scrollPane.setFadeScrollBars(true);
	mainTable.add(scrollPane).width(90).growY();
	mainTable.add(btnsTable);
	// TODO : FloatInput for x,y manually
	
	// listeners...
	group.addListener(new InputListener() {
		@Override
		public boolean touchDown(InputEvent event, float offsetX, float offsetY, int pointer, int button) {
			return true;//return true so it's touchable...
		}
		
		@Override
		public void touchDragged(InputEvent event, float offsetX, float offsetY, int pointer) {
			if (selectedPoint != null) {
				selectedPoint.dragBy(Gdx.input.getDeltaX(),- Gdx.input.getDeltaY());
			}
		}
	});
	addPoint.addListener(new ClickListener() {
		@Override
		public void clicked(InputEvent event, float x, float y) {
			addPoint(20, 20);
		}
	});
	deletePoint.addListener(new ClickListener() {
		@Override
		public void clicked(InputEvent event, float x, float y) {
			deletePoint();
		}
	});
	cancel.addListener(new ClickListener() {
		@Override
		public void clicked(InputEvent event, float x, float y) {
			new ConfirmDialog("Cancel","Do you want to cancel ?",ok->{
				if(ok) hide();
			}).show(getStage());
		}
	});
	save.addListener(new ClickListener() {
		@Override
		public void clicked(InputEvent event, float x, float y) {
			save();
			hide();
		}
	});
	add(mainTable).grow();
  }

  // load the points from the item...
  private void load() {
    customBody = (CustomItem) app.getEditor().getSelectedActor();
    String img =
        app.getEditor().getProject().getImagesPath()
            + customBody.getPropertySet().getString("image").replace(Utils.seperator, "/");
    FileHandle file = Gdx.files.absolute(img),
		logo = Gdx.files.internal("images/logo.png");
	try {
	  colliderImage.setDrawable(customBody.getDrawable()!=null ? customBody.getDrawable() : VisUI.getSkin().getDrawable("logo"));
    } catch (Exception e) {
      colliderImage.setDrawable(VisUI.getSkin().getDrawable("logo"));
    }
    setPointsFromString(customBody.getPropertySet().getString("Points"));
  }

  // save the points ...
  public void save() {
    String pointsStr = getPointsStr();
	// push undo/redo action
    EditorAction.propertiesChanged(
       app.getEditor(),
       app.getEditor().getSelectedActor().getName(),
       "custom collider points",//message...
       new String[] {"Points", pointsStr},//new points...
       new String[] {"Points", customBody.getPropertySet().getString("Points")}//old points...
	);
    customBody.getPropertySet().put("Points", getPointsStr());
    customBody.update();
  }

  @Override
  public VisDialog show(Stage stage) {
    toFront();
    load();
    return super.show(stage);
  }

  // get the points in string text..
  public String getPointsStr() {
    StringBuilder result = new StringBuilder();
	pointsGroup.getChildren().forEach(actor->{
		if(actor instanceof Point){
			Point point = (Point)actor;
			// convert to number between 0 and 1
			// read about normalizing if you don't know what is it...
			// or ask chatgpt :/
			Vector2 np = normalizePoint(point.getPosition());
			float y = 1 - np.y;
			result.append((result.toString().equals("") ? "" : "-") + Math.max(np.x,0) + "," +  Math.max(y,0));
		}
	});
    
    return result.toString();
  }
  
  // i am tired, ask chatgpt... :|
  private Vector2 normalizePoint(float[] position){
	  float x = position[0],
	  	y = position[1],
		  width = colliderImage.getWidth(),
		  height = colliderImage.getHeight();
	  return new Vector2(x/width,y/height);
  }

  public void setPointsFromString(String pointsStr) {
	pointsGroup.clear();
	pointsTable.clear();
    try {
      for (String pointStr : pointsStr.split("-")) {
     float px = point(Utils.getFloat(pointStr.split(",")[0]), colliderImage.getWidth());
       float py = point(1 - Utils.getFloat(pointStr.split(",")[1]), colliderImage.getHeight());
        addPoint(px, py);
      }
    } catch (Exception e) {
      Gdx.files.external("logs/custom-collider.log.txt").writeString("points : \n"+pointsStr+"\nError : "+Utils.getStackTraceString(e),false);
    }
  }

  public void addPoint(float x, float y) {
	Point point = new Point(false);
	point.setPosition(x,y);
    pointsGroup.addActor(point);
	point.setText((point.getZIndex()+1)+"");
	pointsTable.add(point.getTableItem()).padLeft(5).padRight(5).padTop(5).width(80).row();
	scrollPane.layout();
	if(selectedPoint==null)
		selectedPoint = point;
  }

  public void deletePoint() {
    if(selectedPoint!=null){
		selectedPoint.remove();
		selectedPoint = null;
		pointsGroup.getChildren().forEach(actor->{
			Point point = (Point)actor;
			point.setText((point.getZIndex()+1)+"");
			if(selectedPoint == null)
				selectedPoint = point;
		});
	}
  }

  public float normalize(float point, float size) {
    return point / size;
  }

  public float point(float point, float size) {
    return point * size;
  }

  public float height(float ratio) {
    return Gdx.graphics.getHeight() * ratio;
  }

  public float width(float ratio) {
    return Gdx.graphics.getWidth() * ratio;
  }

  public float size(float x, float y) {
    return Math.min(width(x), height(y));
  }


  @Override
  public void draw(Batch batch, float parentAlpha) {
    super.draw(batch, parentAlpha);
    if(pointsGroup.getChildren().size < 2) return;
    batch.end();
    renderer.setProjectionMatrix(batch.getProjectionMatrix());
    renderer.setTransformMatrix(batch.getTransformMatrix());
    renderer.begin(ShapeRenderer.ShapeType.Line);
    renderer.setColor(Color.RED);
    Gdx.gl.glLineWidth(3f);
	Actor[] points = pointsGroup.getChildren().items;
    for(int i = 1; i < points.length; i++){
		try {
			Point prev = (Point)points[i-1],
				current = (Point)points[i];
			if(prev == null || current == null) continue;
			renderer.line(
				prev.getX()/* + prev.getWidth() * 0.5f*/,
				prev.getY()/* + prev.getHeight() * 0.5f*/,
				current.getX()/* + current.getWidth() * 0.5f*/,
				current.getY()/* + current.getHeight() * 0.5f*/
			);
		} catch(Exception ex){}
	}
    renderer.end();
	Gdx.gl.glLineWidth(1f);
    batch.begin();
  }

  public void dispose() {
    renderer.dispose();
  }

  public static interface SizeObserver {
    public void onImageUpdated(float width, float height);
  }
  
  private class Point extends VisTextButton {
	  boolean isRect;
	  Point tableItem;
	  Point pointItem;
	  public Point(boolean isRect){
		  super("0");
		  TextButtonStyle style = new TextButtonStyle();
		  style.down = VisUI.getSkin().getDrawable("point-"+(isRect ? "rect" : "circle")+"-selected");
		  style.up = VisUI.getSkin().getDrawable("point-"+(isRect ? "rect" : "circle"));
		  style.fontColor = Color.WHITE;
      	style.font = VisUI.getSkin().getFont("default-font");
	  	setStyle(style);
		  this.isRect = isRect;
		  if(!isRect){
			setSize(50,50);
			tableItem = new Point(true);
			ClickListener clickListener = new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					if(selectedPoint!=null)
						selectedPoint.unselect();
					Point.this.select();
					selectedPoint = Point.this;
				}
			};
			tableItem.addListener(clickListener);
			addListener(clickListener);
		  }
		  
	  }
	  
	  @Override
	  public void setText(String text){
		  super.setText(text);
		  if(tableItem!=null)
			tableItem.setText(text);
	  }
	  
	  //item on the table which can be clicked to select this...
	  public Point getTableItem(){
		  return tableItem;
	  }
	  
	  public Point setPointItem(Point point){
		  pointItem = point;
		  return this;
	  }
	  
	  @Override
	  public boolean remove() {
		  if(tableItem!=null){
			tableItem.remove();
			scrollPane.layout();
		  }
		  return super.remove();
	  }
	  
	  public void select(){
		  getStyle().up = getStyle().down;
		  setStyle(getStyle());
		  if(tableItem!=null)
			tableItem.select();
	  }
	  
	  public void unselect(){
		  getStyle().up = VisUI.getSkin().getDrawable("point-"+(isRect ? "rect" : "circle"));
		  setStyle(getStyle());
		  if(tableItem!=null)
			tableItem.unselect();
	  }
	  
	  public void dragBy(float dx,float dy){
		  float x = getX(), y = getY();
		  float newX = Math.min(getX() + dx,colliderImage.getWidth()),
		  		newY = Math.min(getY() + dy,colliderImage.getHeight());
		  setPosition(Math.max(newX,0),Math.max(newY,0));
		  //debugLabel.setText(String.format("dx : %5$s \ndy : %6$s \nx : %1$s \ny : %2$s \nto : \nx : %3$s \ny : %4$s",x,y,getX(),getY(),dx,dy));
	  }
	  
	  public float[] getPosition(){
		  return new float[]{getX(),getY()};
	  }
  }
}
