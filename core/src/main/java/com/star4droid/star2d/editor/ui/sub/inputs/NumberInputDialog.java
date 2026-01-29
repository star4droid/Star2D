package com.star4droid.star2d.editor.ui.sub.inputs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.template.Utils.Utils;

public class NumberInputDialog extends VisDialog {
	
	public Condition condition;
	
	private static NumberInputDialog instance;
	
	public static NumberInputDialog getInstance(){
		if(instance == null) instance = new NumberInputDialog();
		return instance;
	}
	
	private OnInput onInput;
	VisTable table;
	
	VisTextButton text;
	private NumberInputDialog(){
		super("Number Input");
		condition = stringCondition;
		VisTable mainTable = new VisTable();
		table = new VisTable();
		text = new VisTextButton("0");
		setResizable(true);
		mainTable.add(text).pad(8).growX().row();
		addNumbers("1","2","3");
		addButton("clear-icon.png",()->{
			text.setText("");
		});
		table.padRight(8).row();
		
		addNumbers("4","5","6");
		addButton("del-icon.png",()->{
			if(text.getText().length() > 0){
				String txt = text.getText().toString();
				text.setText(txt.length() == 1 ? "" : txt.substring(0,txt.length()-1));
			}
		});
		table.padRight(8).row();
		
		addNumbers("7","8","9");
		addButton("check-icon.png",()->{
			if(onInput!= null && condition.Check(text.getText().toString())){
				onInput.OnInput(text.getText().toString());
				hide();
			}
		});
		table.padRight(8).row();
		
		addNumbers(".","0","-");
		addButton("back_arrow.png",()->{
			hide();
		});
		table.padBottom(30).row();
		
		mainTable.add(table).minSize(250,250).grow();
		
		add(mainTable).grow();
		//debug();
		//VisSplitPane splitPane = new VisSplitPane(text,table,true);
		//splitPane.setSplitAmount(0.2f);
		//splitPane.getListeners().clear();
		//add(splitPane).pad(10).fill();
	}
	
	public NumberInputDialog setOnInput(OnInput onInput){
		this.onInput = onInput;
		return this;
	}
	
	public NumberInputDialog setCondition(Condition condition){
		this.condition = condition;
		return this;
	}
	
	public NumberInputDialog setValue(String value){
		text.setText(value);
		return this;
	}
	
	private void addNumbers(String... inputs){
		for(String input:inputs){
			VisTextButton button = new VisTextButton(input);
			table.add((Actor)button).minSize(80,80).grow().padRight(6).padBottom(6);	
			button.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					text.setText(text.getText()+input);
				}
			});
		}
	}
	
	private void addButton(String icon,Runnable click){
		VisImageButton button = new VisImageButton(drawable(icon));
		button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                click.run();
            }
        });
		table.add(button).grow().padRight(6).padBottom(6);
	}
	
	private Drawable drawable(String name){
		int start = name.contains("/") ? name.lastIndexOf("/") : 0;
		String namePure = start == 0 ? name.substring(0,name.indexOf(".")):name.substring(start+1,name.indexOf(".",start));
		try {
			return VisUI.getSkin().getDrawable(namePure);
		} catch(Exception e){
			Gdx.files.external("logs/numbers dialog drawable.txt").writeString("numbers input drawable : "+namePure+", full : "+name+"\n error : "+e.toString()+"\n\n",true);
			return Utils.getDrawable(Gdx.files.internal("images/"+name));
		}
	}
	
	public static void dispose(){
		instance = null;
	}
	
	public interface OnInput {
		public void OnInput(String string);
	}
	
	public interface Condition {
		public boolean Check(String string);
	}
	
	public static final Condition floatCondition = new Condition(){
		@Override
		public boolean Check(String string) {
			try {
				float f = Utils.getFloat(string);
				return true;
			} catch(Exception e){
				return false;
			}
		}
	};
	
	public static final Condition IntCondition = new Condition(){
		@Override
		public boolean Check(String string) {
			try {
				float f = Utils.getInt(string);
				return true;
			} catch(Exception e){
				return false;
			}
		}
	};
	
	public static final Condition stringCondition = new Condition(){
		@Override
		public boolean Check(String string) {
			return true;
		}
	};
}