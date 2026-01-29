package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;

public class GameConfigDialog extends VisDialog {

    private VisTable content;
    private Image icon;
    private VisLabel titleLabel;
    private VisTextField appNameField;
    private VisTextField packageField;
    private VisTextField versionField;
    private VisTextField versionNameField;
    private VisCheckBox withoutOrientationCheck;
    private VisTextButton okButton, cancelButton;

    private OnSaveListener saveListener; // listener interface

    public GameConfigDialog(Drawable iconDrawable) {
        super("App Configuration");
        setModal(true);
        setResizable(false);

        content = new VisTable(true);
        content.defaults().pad(5);

        // icon + label
        icon = new Image(iconDrawable);
        content.add(icon).center().padTop(10);
        content.row();

        titleLabel = new VisLabel("Click to change app icon");
        titleLabel.setAlignment(Align.center);
        content.add(titleLabel).center();
        content.row();

        // --- fields ---
        VisTable appNameTable = new VisTable(true);
        appNameTable.add(new VisLabel("App Name:"));
        appNameField = new VisTextField();
        appNameTable.add(appNameField).growX();
        content.add(appNameTable).growX();
        content.row();

        VisTable packageTable = new VisTable(true);
        packageTable.add(new VisLabel("Package Name:"));
        packageField = new VisTextField();
        packageTable.add(packageField).growX();
        content.add(packageTable).growX();
        content.row();

        VisTable versionTable = new VisTable(true);
        versionTable.add(new VisLabel("Version:"));
        versionField = new VisTextField();
        versionTable.add(versionField).growX();
        content.add(versionTable).growX();
        content.row();

        VisTable versionNameTable = new VisTable(true);
        versionNameTable.add(new VisLabel("Version Name:"));
        versionNameField = new VisTextField();
        versionNameTable.add(versionNameField).growX();
        content.add(versionNameTable).growX();
        content.row();

        withoutOrientationCheck = new VisCheckBox("Without screen orientation");
        content.add(withoutOrientationCheck).left().padTop(5);
        content.row();

        // Buttons
        okButton = new VisTextButton("Save");
        cancelButton = new VisTextButton("Cancel");

        getButtonsTable().add(okButton);
        getButtonsTable().add(cancelButton);

        // Layout
        getContentTable().add(content).growX().pad(10);
        pack();
        centerWindow();

        // --- Event handlers ---
        okButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (saveListener != null) {
                    Data data = new Data();
                    data.name = appNameField.getText();
                    data.packageName = packageField.getText();
                    data.version = versionField.getText();
                    data.versionName = versionNameField.getText();
                    data.withoutOrientation = withoutOrientationCheck.isChecked();
                    saveListener.onSave(data);
                }
                fadeOut();
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fadeOut();
            }
        });
    }

    /**
     * Sets dialog data when opened.
     */
    public void setData(Data data) {
        if (data == null) return;
        appNameField.setText(data.name != null ? data.name : "");
        packageField.setText(data.packageName != null ? data.packageName : "");
        versionField.setText(data.version != null ? data.version : "");
        versionNameField.setText(data.versionName != null ? data.versionName : "");
        withoutOrientationCheck.setChecked(data.withoutOrientation);
    }

    /**
     * Sets listener for Save button
     */
    public void setOnSaveListener(OnSaveListener listener) {
        this.saveListener = listener;
    }

    /**
     * Show dialog helper
     */
    public static void show(Stage stage, Drawable iconDrawable, Data data, OnSaveListener listener) {
        if (!VisUI.isLoaded()) VisUI.load();
        GameConfigDialog dialog = new GameConfigDialog(iconDrawable);
        dialog.setData(data);
        dialog.setOnSaveListener(listener);
        dialog.show(stage);
    }

    // --- Data class ---
    public static class Data {
        public String packageName;
        public String name;
        public String version;
        public String versionName;
        public boolean withoutOrientation;
    }

    // --- Listener interface ---
    public interface OnSaveListener {
        void onSave(Data data);
    }
}