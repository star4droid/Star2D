package dev.mutwakil.star2d.adapter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextButton.VisTextButtonStyle;
import dev.mutwakil.star2d.view.CustomColliderImage;
import java.util.ArrayList;
//
import com.star4droid.star2d.editor.items.CustomItem;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.star2d.Utils;
 import com.star4droid.star2d.editor.LibgdxEditor;

public class CustomColliderEditor extends VisDialog {
	private VisTable table, pointsTable, pcTable;
	private VisTextButton addPoint, deletePoint, save, cancel;
	private VisTextButtonStyle buttonStyle;
	private float fontScale = 1.7f;
	private VisScrollPane scrollPane;
	private CustomColliderImage colliderImage;
	private SizeObserver observer;
	private Group container, imageContainer, pointsContainer;
	private float pointSetX, pointSetY;
	private Point selectedPoint;
	private ArrayList<Point> points = new ArrayList<Point>();
	private TextureRegionDrawable pointRect, pointCircle, pointCircleSelected, pointRectSelected, pointCorsor;
	private ShapeRenderer renderer = new ShapeRenderer();
	public float screenWidth = 1080, screenHeight = 720;
	//position label for debuging
	private VisLabel position;
	//in Star2d 
	private LibgdxEditor editor;
	private CustomItem customBody;

	public CustomColliderEditor(LibgdxEditor editor) {
		super("");
		this.editor = editor;
		pointCircle = new TextureRegionDrawable(new TextureRegion(new Texture("images/point_circle.png")));
		pointRect = new TextureRegionDrawable(new TextureRegion(new Texture("images/point_rect.png")));
		pointCircleSelected = new TextureRegionDrawable(
				new TextureRegion(new Texture("images/point_circle_selected.png")));
		pointRectSelected = new TextureRegionDrawable(new TextureRegion(new Texture("images/point_rect_selected.png")));
		pointCorsor = new TextureRegionDrawable(new TextureRegion(new Texture("images/point_corsor.png")));
		table = new VisTable();
		pointsTable = new VisTable();
		scrollPane = new VisScrollPane(pointsTable, new VisScrollPane.ScrollPaneStyle());
		scrollPane.setVariableSizeKnobs(true);
		scrollPane.setScrollingDisabled(true, false);
		scrollPane.setScrollbarsVisible(false);
		scrollPane.setFadeScrollBars(true);
		scrollPane.layout();
		setFillParent(true);

		//VisDialog is maked a default settings in add() using defaults() in it table so I use reset() to remove it
		reset();
		//^^^

		addPoint = new VisTextButton("Add Point");
		deletePoint = new VisTextButton("Delete Point");
		save = new VisTextButton("Save");
		cancel = new VisTextButton("Cancel");

		addPoint.getLabel().setFontScale(fontScale);
		deletePoint.getLabel().setFontScale(fontScale);
		save.getLabel().setFontScale(fontScale);
		cancel.getLabel().setFontScale(fontScale);

		screenWidth = Gdx.graphics.getWidth();
		screenHeight = Gdx.graphics.getHeight();

		//adding position debuging label
		position = new VisLabel();

		//adding action buttons
		VisTable btable = new VisTable();
		btable.defaults().growX().center().padBottom(20);
		btable.add(addPoint).row();
		btable.add(deletePoint).row();
		btable.add(save).row();
		btable.add(cancel).row();
		btable.add(position);

		/*
			*you cant add Point actor using addActor
			*so I created container Group to hold ImageActor and PointsContainer Actor
			*you can add Point Actor and manage it in pointsContainer
			*good luck ^_^
			*/
		colliderImage = new CustomColliderImage();
		//colliderImage.setImage(new Texture("images/ver.jpg"));
		container = new Group();
		pointsContainer = new Group();
		imageContainer = new Group();
		container.setSize(Gdx.graphics.getWidth() - Gdx.graphics.getWidth() / 4.0f,
				Gdx.graphics.getHeight() - Gdx.graphics.getHeight() / 15f);
		observer = (w, h) -> {
			imageContainer.setSize(w, h);
			pointsContainer.setSize(w, h);
		};
		colliderImage.setSize(container.getWidth(), container.getHeight());
		imageContainer.addActor(colliderImage);
		container.addActor(imageContainer);
		container.addActor(pointsContainer);
		colliderImage.setObserver(observer);

		//I created SizeObserver interface to resize container and pointsContainer when the Image resized
		//it's will be usefull in future

		//add Contents in main table
		table.add(container).uniform().grow().center();
		table.add(scrollPane).fill().width(90).pad(10).center();
		table.add(btable).fill().pad(20).center();
		table.pack();
		add(table).grow();
		pack();
		/*
		*
		* Listeners
		* 
		*/
		container.addListener(new InputListener() {

			public boolean touchDown(InputEvent event, float offsetX, float offsetY, int pointer, int button) {
				if (selectedPoint != null) {
					pointSetX = offsetX - selectedPoint.getX() - selectedPoint.getWidth() / 2;
					pointSetY = offsetY - selectedPoint.getY() - selectedPoint.getHeight() / 2;
					float x = selectedPoint.getX() + selectedPoint.getWidth() / 2;
					float y = selectedPoint.getY() + selectedPoint.getHeight() / 2;
					selectedPoint.setPosition(x, y);
					selectedPoint.corsor();
				}
				return true;
			}

			public void touchDragged(InputEvent event, float offsetX, float offsetY, int pointer) {
				if (selectedPoint != null) {
					float containerWidth = imageContainer.getWidth();
					float containerHeight = imageContainer.getHeight();
					float actorWidth = selectedPoint.getWidth();
					float actorHeight = selectedPoint.getHeight();
					float newX = offsetX - pointSetX;
					float newY = offsetY - pointSetY;
					float negateHeightSide = 0;
					float negateWidthSize = 0;
					if (newX < negateWidthSize)
						newX = negateWidthSize;
					if (newX > containerWidth)
						newX = containerWidth;
					if (newY < negateHeightSide)
						newY = negateHeightSide;
					if (newY > containerHeight)
						newY = containerHeight;
					selectedPoint.setPosition(newX, newY);
				}
			}

			public void touchUp(InputEvent event, float offsetX, float offsetY, int pointer, int button) {
				if (selectedPoint != null) {
					selectedPoint.setPosition(selectedPoint.getX(), selectedPoint.getY(), Align.center);
					selectedPoint.selected();
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
		save.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				save();
				hide();
			}
		});
		cancel.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				hide();
			}
		});
	}

	private void load() {
		 customBody = (CustomItem)editor.getSelectedActor();
		String img = editor.getProject().getImagesPath() + customBody.getPropertySet().getString("image").replace(Utils.seperator,"/");
		try{
			colliderImage.setImage(new Texture(Gdx.files.absolute(img)));
			}catch(Exception e) {
			colliderImage.setImage(new Texture(Gdx.files.internal("images/app_icon.png")));
		}
		setPointsFromString(customBody.getPropertySet().getString("Points"));
		
	}

	public void save() {
		String pointsStr = getPointsStr();
		Gdx.app.postRunnable(()->
		EditorAction.propertiesChanged(editor,editor.getSelectedActor().getName(),"custom collider points",new String[]{"Points",pointsStr},new String[]{"Points",customBody.getPropertySet().getString("Points")})
		);
		customBody.getPropertySet().put("Points",getPointsStr());
		
	}

	@Override
	public VisDialog show(Stage stage) {
		toFront();
		load();
		return super.show(stage);
	}

	public String getPointsStr() {
		String result = "";
		for (Point point : points) {
			Vector2 np = normalizePoint(point.getCoordinator().x, point.getCoordinator().y);
			result += (result.equals("") ? "" : "-") + np.x + "," + np.y;
		}
		return result;
	}

	public void setPointsFromString(String pointsStr) {
		for (String pointStr : pointsStr.split("-")) {
			try {
				//in Star2D
					float px = point(Utils.getFloat(pointStr.split(",")[0]),colliderImage.getWidth());
					float py = point(Utils.getFloat(pointStr.split(",")[1]),colliderImage.getHeight());
					addPoint(px,py);
			} catch (Exception e) {
				position.setWrap(true);
				position.setText(e.toString());
			}
		}
	}

	public void addPoint(float x, float y) {
		//add Point in Image
		int i = points.size() + 1;
		Point p = new Point(false);
		p.setText(i);
		p.setPosition(x, y, Align.center);
		points.add(p);
		pointsContainer.addActor(p);
		if (selectedPoint != null)
			selectedPoint.normal();
		selectedPoint = p;
		//to add Rectangle Point in scrollPane
		Point rectPoint = new Point(true);
		rectPoint.setPoint(p);
		rectPoint.setSize(80, 80);
		rectPoint.setText(i);
		pointsTable.add(rectPoint).fill().size(90).padBottom(10).row();
		//to refresh scrollPane Items like recyclerView.getAdapter().notifyDataSetChanged()
		pointsTable.pack();
		scrollPane.layout();
		p.selected();
	}

	public void deletePoint() {
		if (!points.isEmpty()) {
			Point p = selectedPoint != null ? selectedPoint : points.get(points.size() - 1);
			Point rectP = p.getRectPoint();
			pointsContainer.removeActor(p);
			pointsTable.clear();
			points.remove(p);
			selectedPoint = null;
			for (int i = 0; i < points.size(); ++i) {
				Point pp = points.get(i);
				int pos = i + 1;
				pp.setText(pos);
				pp.getRectPoint().setText(pos);
				pointsTable.add(pp.getRectPoint()).fill().size(100, 100).pad(10).row();
			}
			pointsTable.pack();
			scrollPane.layout();
		}
	}

	public float normalize(float point, float size) {
		return point / size;
	}

	public float point(float point, float size) {
		return point * size;
	}

	public void debugCoor(float x, float y) {
		position.setText("x:\n" + x + "\ny:\n" + y);
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

	public Vector2 adjustedPoint(float x, float y) {
		return colliderImage.adjustedPoint(x, y);
	}

	public Vector2 normalizePoint(float x, float y) {
		return colliderImage.normalizePoint(x, y);
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
        //in Star2D prevPoint.getWidth()*1.5f
        //in my project prevPoint.getWidth()*2.0f
		renderer.setProjectionMatrix(batch.getProjectionMatrix());
		renderer.begin(ShapeRenderer.ShapeType.Line);
		renderer.setColor(Color.RED);
		Gdx.gl.glLineWidth(3f);
		if (!points.isEmpty()) {
			if (points.size() > 1) {
				for (int i = 0; i < points.size(); ++i) {
					if (i != 0) {
						Point prevPoint = points.get(i - 1);
						Point nextPoint = points.get(i);
						Vector2 prev = new Vector2();
						Vector2 next = new Vector2();
						prev = prevPoint.getCoordinator();
						next = nextPoint.getCoordinator();
						renderer.line(prev.x + prevPoint.getWidth() * 1.5f, prev.y + prevPoint.getHeight() / 3f,
								next.x + nextPoint.getWidth() * 1.5f, next.y + nextPoint.getHeight() / 3f);
					}
				}
			}
		}
		renderer.end();
	}

	public void dispose() {
		renderer.dispose();
		colliderImage.dispose();
		pointCircle.getRegion().getTexture().dispose();
		pointCircleSelected.getRegion().getTexture().dispose();
		pointRect.getRegion().getTexture().dispose();
		pointRectSelected.getRegion().getTexture().dispose();
		pointCorsor.getRegion().getTexture().dispose();
	}

	public static interface SizeObserver {
		public void onImageUpdated(float width, float height);
	}

	private class Point extends VisLabel {
		private Point instance;
		private Point rectPoint, point;
		public boolean isRect = false;
		private Vector2 coor = new Vector2();
		private VisLabel.LabelStyle ps;

		public Point(boolean isRect) {
			super();
			this.isRect = isRect;
			ps = new VisLabel.LabelStyle();
			ps.fontColor = Color.WHITE;
			ps.font = VisUI.getSkin().getFont("default-font");
			ps.background = isRect ? pointRect : pointCircle;
			setStyle(ps);
			if (isRect)
				setFontScale(1.5f);
			setAlignment(Align.center);
			float size = size(0.030f, 0.030f);
			setSize(size, size);
			if (!isRect)
				setPosition(100, 100);
			instance = this;
			addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					if (selectedPoint != null)
						selectedPoint.normal();
					selectedPoint = isRect ? getPoint() : instance;
					selectedPoint.selected();
					debugCoor(normalize(selectedPoint.coor.x, colliderImage.getWidth()),
							normalize(selectedPoint.coor.y, colliderImage.getHeight()));
				}
			});
		}

		@Override
		public void setPosition(float x, float y) {
			super.setPosition(x, y);
			setCoordinator(new Vector2(x, y));
		}

		@Override
		public void setPosition(float x, float y, int align) {
			super.setPosition(x, y, align);
			setCoordinator(new Vector2(x, y));
		}

		public void setRectPoint(Point p) {
			this.rectPoint = p;
			if (p.getPoint() != instance)
				p.setPoint(instance);
		}

		public Point getRectPoint() {
			return this.rectPoint;
		}

		public void setCoordinator(Vector2 c) {
			coor = c;
			//type position in label for debuging
			debugCoor(normalize(coor.x, colliderImage.getWidth()), normalize(coor.y, colliderImage.getHeight()));
		}

		public Vector2 getCoordinator() {
			return coor;
		}

		public void setPoint(Point p) {
			this.point = p;
			if (p.getRectPoint() != instance)
				p.setRectPoint(instance);
		}

		public Point getPoint() {
			return this.point;
		}

		public void normal() {
			ps.background = isRect ? pointRect : pointCircle;
			if (!isRect)
				getRectPoint().normal();
		}

		public void selected() {
			ps.background = isRect ? pointRectSelected : pointCircleSelected;
			if (!isRect)
				getRectPoint().selected();
		}

		public void corsor() {
			if (!isRect)
				ps.background = pointCorsor;
		}
	}
  } 
