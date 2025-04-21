package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.ScrollableTextArea;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisWindow;

public class TextShow extends VisTable {
	VisTextButton okBtn,copyBtn;
	ScrollableTextArea textArea;
	ScrollPane scrollPane;
	public TextShow(String title){
		super();
		//setResizable(true);
		setFillParent(true);
		//VisTable table = new VisTable();
		VisTable btnsTable = new VisTable();
		textArea = new ScrollableTextArea("Text");
		textArea.setReadOnly(true);
		setBackground(VisUI.getSkin().getDrawable("window"));
		textArea.setWidth(400);
		scrollPane = textArea.createCompatibleScrollPane();
		scrollPane.setFlickScroll(true);
		okBtn = new VisTextButton("OK");
		copyBtn = new VisTextButton("Copy");
		left();
		add(scrollPane).padLeft(10).minSize(450,350).pad(10).grow().row();
		
		btnsTable.add(okBtn).size(200,60).padBottom(5).padRight(5);
		btnsTable.add(copyBtn).size(200,60).padBottom(5);
		
		add(btnsTable).padLeft(10).growX();
		//table.setFillParent(true);
		//add(table).minHeight(350).minWidth(350).grow();
		okBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                remove();
            }
        });
		copyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.getClipboard().setContents(textArea.getText());
            }
        });
		addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                
            }
        });
	}
	
	public void show(Stage stage){
		stage.addActor(this);
	}
	
	public void setEnabled(boolean b){
		copyBtn.setDisabled(!b);
		okBtn.setDisabled(!b);
	}
	
	public TextShow setText(String text){
		textArea.setText("\n\n"+text);
		textArea.pack();
		scrollPane.layout();
		return this;
	}
	
	public TextShow setOkText(String text){
		okBtn.setText(text);
		return this;
	}
}