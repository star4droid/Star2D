package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;

public class FieldSuggestionDialog {
    public interface OnItemClickListener {
        void onItemClick(String item);
    }

    public static void show(Stage stage, String[] list, OnItemClickListener listener) {
        if (!VisUI.isLoaded()) VisUI.load();
        if (list == null) {
            list = new String[]{"edit value"};
        } else {
            
            String[] newList = new String[list.length + 1];
            newList[0] = "edit value";
            System.arraycopy(list, 0, newList, 1, list.length);
            list = newList;
        }

        
        VisDialog dialog = new VisDialog("Select Item");
        VisTable listTable = new VisTable(true);
        listTable.defaults().growX().pad(4);

        for (String item : list) {
            if(item == null || item.trim().equals("")) continue;
            final VisLabel label = new VisLabel(item);
            VisTable row = new VisTable();
            row.setBackground("list-selection");
            row.add(label).expandX().left().pad(6);

            
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    
                    if (listener != null) {
                        listener.onItemClick(item);
                    }
                    dialog.hide();
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    row.setColor(1, 1, 1, 0.2f); // hover effect
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    row.setColor(Color.WHITE);
                }
            });

            listTable.add(row).growX().row();
        }

        
        ScrollPane scrollPane = new VisScrollPane(listTable);
        scrollPane.setFadeScrollBars(false);

        dialog.getContentTable().add(scrollPane).width(300).height(250);
        dialog.button("Cancel").pad(5);
        dialog.show(stage);
    }
}