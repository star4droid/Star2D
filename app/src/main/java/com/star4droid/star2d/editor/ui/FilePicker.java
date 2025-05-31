package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisWindow;
import java.util.Arrays;
import java.util.function.BiConsumer;

public class FilePicker extends VisWindow implements Disposable {
    public enum Target { FILE, FOLDER }
    
    private Target target = Target.FILE;
    private Array<String> extensions = new Array<>();
    private boolean showImageIcons = true;
    private FileHandle rootDir;
    private FileHandle currentDir;
	private Texture folderIcon,fileIcon;
    private BiConsumer<FileHandle, String> onPick;
    private Runnable onCancel;
    private java.util.Stack<FileHandle> history = new java.util.Stack<>();
    private Table fileTable;
    private TextButton backButton;
    private FileHandle selectedFile;
    private Color SELECTED_COLOR = new Color(0.25f, 0.55f, 0.8f, 0.5f);
	
    public FilePicker() {
        super("Select File");
        setupDefaults();
        buildUI();
    }

    private void setupDefaults() {
        setSize(600, 400);
        setResizable(true);
        setMovable(true);
        setCenterOnAdd(true);
        padTop(42);
        defaults().pad(5);
		folderIcon = new Texture(Gdx.files.internal("images/folder-white.png"));
		fileIcon = new Texture(Gdx.files.internal("images/file-white.png"));
    }

    private void buildUI() {
        backButton = new TextButton("Back", VisUI.getSkin());
        backButton.setDisabled(true);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                goBack();
            }
        });

        fileTable = new Table();
        VisScrollPane scrollPane = new VisScrollPane(fileTable);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(false, false);

        TextButton okButton = new TextButton("OK", VisUI.getSkin());
        okButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                confirmSelection();
				//setVisible(false);
            }
        });

        TextButton cancelButton = new TextButton("Cancel", VisUI.getSkin());
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onCancel != null) onCancel.run();
                //remove();
				setVisible(false);
            }
        });

        add(backButton).colspan(2).growX();
        row();
        add(scrollPane).colspan(2).grow();
        row();
        add(okButton).growX();
        add(cancelButton).growX();
    }

    private void navigateTo(FileHandle dir) {
        history.push(currentDir);
        currentDir = dir;
        rebuildFileTable();
        backButton.setDisabled(history.isEmpty());
    }

    private void goBack() {
        if (!history.isEmpty()) {
            currentDir = history.pop();
            rebuildFileTable();
            backButton.setDisabled(history.isEmpty());
        }
    }

    private void rebuildFileTable() {
		if(currentDir==null) return;
        fileTable.clear();
        FileHandle[] children = currentDir.list();
        
        Arrays.sort(children, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.name().compareToIgnoreCase(f2.name());
        });

        for (final FileHandle file : children) {
            if (file.isDirectory() || isExtensionAllowed(file)) {
                final Table row = createFileRow(file);
                fileTable.add(row).growX().row();
                addRowInteraction(row, file);
            }
        }
    }

    private Table createFileRow(final FileHandle file) {
        Table row = new Table();
        row.setTouchable(Touchable.enabled);
        row.setBackground(VisUI.getSkin().getDrawable("menu-bg"));
        row.getColor().a = 0.7f;

        Image icon = new Image(getFileIcon(file));
        icon.setScaling(Scaling.fit);
        Label nameLabel = new Label(file.name(), VisUI.getSkin());
        nameLabel.setFontScale(1.1f);

        row.add(icon).size(32, 32).pad(5);
        row.add(nameLabel).padLeft(10).growX().left();
        return row;
    }

    private void addRowInteraction(final Table row, final FileHandle file) {
        row.addListener(new ClickListener() {
            private float lastClickTime;

            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (file.isDirectory() && target == Target.FILE) {
                    if (getTapCount() == 2) {
                        navigateTo(file);
                    } else {
						selectedFile = file;
                        selectRow(row);
                    }
                } else {
					selectedFile = file;
                    selectRow(row);
                }

                if (getTapCount() == 2) handleDoubleClick(file);
                else handleSingleClick(file);
            }

            private void handleDoubleClick(FileHandle file) {
                if (file.isDirectory() && target == Target.FILE) {
                    navigateTo(file);
                } else if (target == Target.FILE && isValidSelection(file)) {
                    confirmSelection();
                }
            }

            private void handleSingleClick(FileHandle file) {
                if (file.isDirectory() && target == Target.FOLDER) {
                    confirmSelection();
                }
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                row.getColor().a = 1f;
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                row.getColor().a = 0.7f;
                super.touchUp(event, x, y, pointer, button);
            }
        });
    }

    private void selectRow(Table row) {
        fileTable.getChildren().forEach(actor -> {
            if (actor instanceof Table) {
                ((Table)actor).setBackground(VisUI.getSkin().getDrawable("menu-bg"));
                ((Table) actor).getColor().a = 0.7f;
            }
        });
        row.setBackground(VisUI.getSkin().getDrawable("window-bg"));
        row.getColor().a = 1f;
    }

    private Texture getFileIcon(FileHandle file) {
        if (file.isDirectory()) return folderIcon;
        if (!showImageIcons) return fileIcon;
        
        if (isParticleFile(file)) {
            FileHandle imageFile = file.parent().child("images").child(file.nameWithoutExtension() + ".png");
            if (imageFile.exists()) {
                try {
                    return new Texture(imageFile);
                } catch (Exception e) {
                    // Fallback to default
                }
            }
        }
		String ext = file.extension().toLowerCase();
		if(ext.contains("png") || ext.contains("jpeg") || ext.contains("jpg"))
			return new Texture(file);
        return new Texture(Gdx.files.internal("images/file.png"));
    }

    private boolean isParticleFile(FileHandle file) {
        String ext = file.extension();
        return ext.equalsIgnoreCase("p") || ext.equalsIgnoreCase("particle");
    }

    private boolean isExtensionAllowed(FileHandle file) {
        if (file.isDirectory()) return true;
		//Gdx.files.external("logs/ext.txt").writeString("ext : "+extensions.contains("*",false)+"\n",true);
        if (extensions.contains("*", false)) return true;
        String ext = file.extension().toLowerCase();
        return extensions.contains(ext, false);
    }

    private void confirmSelection() {
		//Gdx.files.external("logs/confirm.txt").writeString("file : "+(selectedFile!=null)+", valid : "+isValidSelection(selectedFile)+"\n",true);
        if (selectedFile == null || !isValidSelection(selectedFile)) return;
        
        if (onPick != null) {
            String path = getRelativePath(selectedFile);
            onPick.accept(selectedFile, path);
        }
        //remove();
		setVisible(false);
    }
	
	private String getRelativePath(FileHandle file) {
        return file.path().substring(rootDir.path().length());
    }

    private boolean isValidSelection(FileHandle file) {
		if(file==null) return false;
        if (target == Target.FILE) 
            return (!file.isDirectory()) && isExtensionAllowed(file);
        return file.isDirectory();
    }

    public FilePicker setTarget(Target target) {
        this.target = target;
        return this;
    }

    public FilePicker setExtensions(String... extensions) {
        this.extensions.clear();
        this.extensions.addAll(extensions);
		rebuildFileTable();
        return this;
    }

    public FilePicker setShowImageIcons(boolean show) {
        this.showImageIcons = show;
        rebuildFileTable();
        return this;
    }

    public FilePicker setRoot(FileHandle root) {
        this.rootDir = root;
        this.currentDir = root;
        history.clear();
        rebuildFileTable();
        return this;
    }

    public FilePicker setRoot(String rootPath) {
        return setRoot(Gdx.files.absolute(rootPath));
    }

    public FilePicker setOnPick(BiConsumer<FileHandle, String> onPick) {
        this.onPick = onPick;
        return this;
    }

    public FilePicker setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }
	
	/*
	@Override
	public void act(float arg0) {
		super.act(arg0);
		if(getX()!=0 || getY()!=0)
			setPosition(0,0);
		Gdx.files.external("logs/filepicker.txt").writeString(" w : "+getWidth()+",h : "+getHeight()+",x : "+getX()+", y : "+getY(),false);
	}
	*/

    @Override
    public float getPrefWidth() {
        return 600;
    }

    @Override
    public float getPrefHeight() {
        return 400;
    }
	
	@Override
	public void dispose() {
		
	}
}