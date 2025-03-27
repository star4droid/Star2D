package com.star4droid.template;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.star4droid.template.Utils.Utils;

public class LoadingStage extends Stage {
	ProgressItem progressItem;
	VisLabel label;
	public LoadingStage(){
		super();
		VisDialog dialog = new VisDialog("Please Wait...");
		VisTable table = new VisTable(),
			 //imgTable = new VisTable(),
			 fillTable = new VisTable();
		fillTable.setFillParent(true);
		dialog.setKeepWithinStage(true);
		//dialog.setFillParent(true);
		table.setFillParent(true);
		addActor(fillTable);
		fillTable.setBackground(VisUI.getSkin().getDrawable("grey"));
		progressItem = new ProgressItem(this);
		VisImageButton image = new VisImageButton(Utils.getDrawable(Utils.internal("images/logo.png")));
		//image.setScaling(Scaling.stretch);
		image.setSize(120,120);
		label = new VisLabel("Loading...");
		label.setAlignment(Align.center);
		table.center();
		
		//imgTable.add().growX();
		//imgTable.add(image).padTop(10).size(90);
		//imgTable.add().growX();
		
		table.add().growY().minHeight(5).row();
		table.add(image).center().size(300,130).padBottom(10).padRight(10).row();
		table.add(label).center().padBottom(10).padRight(10).size(300,75).row();
		table.add(progressItem).size(300,100).padRight(10).row();
		table.add().growY().minHeight(5);
		
		//dialog.add(table).pad(6).center();
		//dialog.pack();
		//dialog.show(this);
		//dialog.centerWindow();
		addActor(table);
	}
	
	public boolean isLoaded(){
		return progressItem.getProgress()==progressItem.getMax();
	}
	
	public void setProgress(float progress){
		progressItem.setProgress(progress);
		label.setText("Loading...("+((int)progress)+"%)");
	}
	
	public ProgressItem getProgressItem(){
		return progressItem;
	}
}