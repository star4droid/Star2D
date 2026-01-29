package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.star4droid.star2d.editor.ui.variables.VarsManager;
import com.badlogic.gdx.files.FileHandle;

public class VarsItem extends VisTable {

    private final VarsManager varsManager;
    private final VisTextButton addButton;

    public VarsItem() {
        varsManager = new VarsManager(Gdx.files.local("test.json"));

        // Configure layout
        setFillParent(true);
        top().pad(10f);

        // Fill Y axis
        defaults().expandX().fillX();

        // Add content area placeholder (for variables display)
        //VisTable contentTable = new VisTable();
        //contentTable.defaults().expandX().fillX().space(5f);
        
        add(varsManager.getVarsListTable()).expand().fill().row();
        
        // Create "Add Variable" button
        addButton = new VisTextButton("Add Variable");

        // Add click listener to trigger variable creation
        addButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                varsManager.addItem();
            }
        });

        // Add the button anchored at the bottom
        add(addButton).expandX().fillX().bottom().padTop(10f);
    }

    public VarsManager getVarsManager() {
        return varsManager;
    }
    
    public String getCode(){
        return varsManager.getCode();
    }
    
    public VisTextButton getAddButton() {
        return addButton;
    }
    
    public VarsItem setFileHandle(FileHandle fileHandle){
        varsManager.setFileHandle(fileHandle);
        return this;
    }
}