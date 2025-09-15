package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisTable;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.utils.Lang;

public class VisualScripting extends VisDialog {
	TestApp app;
	FileHandle fileHandle;
	Group nodesGroup = new Group();
	public ShapeRenderer shapeRenderer = new ShapeRenderer();
	public VisualScripting(TestApp app){
		super(Lang.getTrans("visualScripting"));
		this.app = app;
		setMovable(false);
		setFillParent(true);
		setResizable(false);
		addCloseButton();
		VisImage background = new VisImage(VisUI.getSkin().getDrawable("white"));
		addActor(background);
		background.setFillParent(true);
		background.setColor(Color.valueOf("#1E1E1E"));
		VNode node = new VNode("setLinearVelocity",this),
			node2 = new VNode("setText",this);
		nodesGroup.addActor(node);
		nodesGroup.addActor(node2);
		node2.setPosition(350,150);
		add(nodesGroup).pad(8).grow();
	}
	
	public VisualScripting load(FileHandle fileHandle){
		this.fileHandle = fileHandle;
		return this;
	}
	@Override
	public VisDialog show(Stage stage, Action action) {
		VisDialog dialog = super.show(stage, action);
		toFront();
		return dialog;
	}
}