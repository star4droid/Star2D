package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.SingleInputDialog;
import java.util.concurrent.Executors;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class ExamplesDialog extends VisDialog {
	TestApp app;
	private final String isArabic;
	public ExamplesDialog(TestApp app){
		super(getTrans("examplesDialog"));
		this.app = app;
		isArabic = app.preferences.getString("lang","en").equals("ar") ? ".ar" : "";
		reset();
		setFillParent(true);
		for(FileHandle example:Gdx.files.internal("files/examples").list()){
			if(example.isDirectory())
				addExampleTable(example);
		}
		VisTextButton close = new VisTextButton(getTrans("close"));
		close.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				hide();
			}
		});
		add(close).growX();
	}
	
	private void addExampleTable(FileHandle fileHandle){
		VisTable table = new VisTable();
		VisImage exImg = new VisImage(VisUI.getSkin().getDrawable(fileHandle.name()));
		VisLabel label = new VisLabel(fileHandle.sibling(fileHandle.name()+isArabic+".txt").readString());
		if(isArabic.equals("")){
			VisLabel.LabelStyle style = new VisLabel.LabelStyle(label.getStyle());
			style.font = VisUI.getSkin().get("no-rtl",BitmapFont.class);;
			label.setStyle(style);
		}
		//label.setFontScale(0.85f);
		label.setWrap(true);
		//label.setAlignment(Align.center);
		table.center();
		table.add(exImg).size(120,120).padLeft(10);
		table.add(label).padLeft(10).padTop(4).grow();
		add(table).height(150).padBottom(10).growX().row();
		ClickListener clickListener = new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				new SingleInputDialog(getTrans("projectName"),getTrans("name") + " : ",fileHandle.name(),(name)->{
					if(name.isEmpty()){
						app.toast(getTrans("enterValue"));
						return;
					}
					for(char c:name.toCharArray()){
						if(!EditorField.allowedChars.contains(String.valueOf(c))){
							app.toast("use A-Z a-z or _ , Not Allowed Char : "+c);
							return;
						}
					}
					FileHandle saveDir = Gdx.files.local("projects/"+name);
					if(saveDir.exists()){
						app.toast(getTrans("projectConflict"));
						return;
					}
					hide();
					saveDir.mkdirs();
					app.toast(getTrans("importing"));
					Executors.newSingleThreadExecutor().execute(()->{
						for(FileHandle file:fileHandle.list()){
							file.copyTo(saveDir);
						}
						Gdx.app.postRunnable(()->{
							app.toast(getTrans("exampleImported")+" : "+name);
							app.getProjectsStage().refresh();
						});
					});
				}).show(getStage());
			}
		};
		label.addListener(clickListener);
		exImg.addListener(clickListener);
	}
}