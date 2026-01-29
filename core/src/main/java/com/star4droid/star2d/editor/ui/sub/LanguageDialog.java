package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.utils.Lang;

public class LanguageDialog extends VisDialog {
	TestApp app;
	public LanguageDialog(TestApp app){
		super("Language");
		this.app = app;
		setMovable(false);
		setResizable(false);
		reset();
		addLang("العربيه","ar");
		addLang("English","en");
	}
	
	private void addLang(String name,String key){
		VisTextButton lang = new VisTextButton(name);
		lang.addListener(new ClickListener() {
			@Override
            public void clicked(InputEvent event, float x, float y) {
				app.preferences.putString("lang",key).putBoolean("langSelected",true).flush();
				Lang.loadTrans(key);
				app.toast(Lang.getTrans("langNextTime"));
				hide();
			}
		});
		add(lang).size(170,100).growX().pad(5).row();
	}
}