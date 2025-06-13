package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;

import com.kotcrab.vis.ui.widget.VisDialog;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class SingleInputDialog extends VisDialog {
	public SingleInputDialog(String title, String message, String initialText, InputListener listener) {
		super(title);
		setResizable(true);
		VisTextField textField = new VisTextField(initialText);
		VisTextButton okButton = new VisTextButton(getTrans("ok"));
		VisTextButton cancelButton = new VisTextButton(getTrans("cancel"));
		
		getContentTable().add(message).padBottom(8).row();
		getContentTable().add(textField).minWidth(320).pad(5).row();
		
		VisTable buttons = new VisTable();
		buttons.add().growX();
		buttons.add(okButton).space(10);
		buttons.add(cancelButton).space(10);
		buttons.add().growX();
		
		getContentTable().add(buttons);
		
		okButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				listener.onInput(textField.getText());
				fadeOut();
			}
		});
		
		cancelButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				fadeOut();
			}
		});
		
		setSize(Gdx.graphics.getWidth()*0.333333f, 250);
		centerWindow();
	}
	
	public interface InputListener {
		void onInput(String result);
	}
}