package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisTable;

public class VNode extends VisTable {
	VisTable content,mainTable;
	Drawable background;
	ConnectRect defRect,trueRect,falseRect;
	VisualScripting visualScripting;
	public VNode(String title, VisualScripting visualScripting){
		super();
		background = VisUI.getSkin().getDrawable("window");
		this.visualScripting = visualScripting;
		defRect = new ConnectRect(visualScripting);
		trueRect = new ConnectRect(visualScripting);
		falseRect = new ConnectRect(visualScripting);
		
		content = new VisTable();
		mainTable = new VisTable();
		VisTable contentAndRect = new VisTable();
		contentAndRect.add(content).minSize(120,120).padLeft(5);
		contentAndRect.add(defRect).size(35,35);
		mainTable.add(contentAndRect).row();
		mainTable.add(trueRect).size(35,35).padBottom(35).left().row();
		mainTable.add(falseRect).size(35,35).left();
		
		VisImage backImg = new VisImage(background);
		stack(backImg,mainTable);
	}
}