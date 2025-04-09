package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;

import com.kotcrab.vis.ui.widget.VisDialog;

public class ConfirmDialog extends VisDialog {
	public VisTextButton okButton;
	public VisTextButton cancelButton;
	public ConfirmDialog(String title, String message,ActionListener actionListener) {
		super(title);
		setResizable(true);
		
		okButton = new VisTextButton("Confirm");
		cancelButton = new VisTextButton("Cancel");
		
		getContentTable().add(message).padBottom(8).row();
		
		VisTable buttons = new VisTable();
		buttons.add().growX();
		buttons.add(okButton);
		buttons.add().width(10);
		buttons.add(cancelButton);
		buttons.add().width(10);
		buttons.add().growX();
		
		getContentTable().add(buttons);
		
		okButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				actionListener.onAction(true);
				fadeOut();
			}
		});
		
		cancelButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				actionListener.onAction(false);
				fadeOut();
			}
		});
		
		setSize(Gdx.graphics.getWidth()*0.33333333f, 250);
		centerWindow();
	}
	
	@Override
	public VisDialog show(Stage stage) {
		super.show(stage);
		toFront();
		return this;
	}
	
	public static ConfirmDialog confirmDeleteDialog(ActionListener actionListener){
		return new ConfirmDialog("Delete","Are you sure ?",actionListener);
	}
	
	public interface ActionListener {
		void onAction(boolean done);
	}
}