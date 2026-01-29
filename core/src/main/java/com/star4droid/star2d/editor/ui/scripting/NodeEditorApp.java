package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.kotcrab.vis.ui.VisUI;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class NodeEditorApp implements ApplicationListener {

    private NodeEditor editorStage;   // holds nodes only
    private UiStage uiStage;          // holds buttons & UI
    String firstNodeTitle="First \u003e";
    public static Skin orangeSkin;
    @Override
    public void create() {
        //VisUI.load(VisUI.SkinScale.X2);
        orangeSkin = com.star4droid.star2d.editor.utils.ThemeLoader.getOrangeSkin();// new Skin(Gdx.files.internal("files/skins/uiskin.json"));
        editorStage = new NodeEditor();
		uiStage   = new UiStage(editorStage){
			@Override
			public boolean keyDown(int key){
				if(key == 4){
					new ConfirmDialog(getTrans("exit"),getTrans("areYouSure"),ok->{
						if(ok)
							com.star4droid.star2d.editor.TestApp.getCurrentApp().showVisualScripting(false);
					}).show(uiStage);
					return true;
				}
				return false;
			}
		};
        
        //FieldSuggestionList.bodies=new String[] {"body1","body2"};
        // Hatsune Miku 🤓🎉🎊
        //FieldSuggestionList.sounds=new String[] {"singerAnnas.mp3","loveByAnnas.mp3"};
        //FieldSuggestionList.images=new String[] {"/sdcard/annas.png","/sdcard/annasInSchool.jpeg"};
        //buildSample();
        
    }
    
    public void setInput(){
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(uiStage, editorStage));
    }
    
    public void loadFrom(String file){
        uiStage.loadFrom(file);
    }
    
    public void setHints(String hints){
        uiStage.setHints(hints);
    }

    private void buildSample() {
        VisualNode first = new VisualNode(firstNodeTitle, editorStage);
        first.setCode("%1$s");
        first.setDeletable(false);
        first.setPosition(50, 450);
        first.setColor(Color.RED);
        editorStage.addNode(first);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        editorStage.act(Gdx.graphics.getDeltaTime());
        editorStage.draw();

        uiStage.act(Gdx.graphics.getDeltaTime());
        uiStage.draw();
    }

    @Override
    public void resize(int width, int height) {
        editorStage.getViewport().update(width, height, true);
        uiStage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}
    @Override
    public void resume() {}
    @Override
    public void dispose() {
        editorStage.dispose();
        uiStage.dispose();
        //VisUI.dispose();
    }
}
