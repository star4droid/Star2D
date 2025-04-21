package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.ToastManager;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.sub.TextShow;
import com.star4droid.star2d.editor.utils.BitmapFontEditor;
import com.star4droid.template.Utils.ProjectAssetLoader;
import java.io.File;
import java.util.HashSet;

public class FileBrowser extends VisWindow implements Disposable {
    private FileHandle currentDir;
    private Runnable pickFilesRunnable;
	private FileOpen javaOpen;
    private float uiScale = 1f;
    private boolean gridMode = false,hideWhenNoBack=true,isJavaFolder = false,isImagesFolder=false;
    private VisScrollPane scrollPane;
    private VisTable contentTable;
    private Texture folderIcon;
    private Texture fileIcon;
	private int depth = 0;
	private TextShow textShow;
    private HashSet<String> imageExtensions = new HashSet<>();
	private ProjectAssetLoader projectAssetLoader;
	private ToastManager toastManager;

    public FileBrowser(String rootAbsoulutePath,ProjectAssetLoader assetLoader) {
        super("File Browser");
		this.projectAssetLoader = assetLoader;
		setupDefaults();
        setupIcons();
        setupUI(rootAbsoulutePath);
        refreshFileList();
        //addCloseButton();
    }
	
	private void setupDefaults(){
		setSize(600, 400);
        setCenterOnAdd(true);
        padTop(42);
		closeOnEscape();
        defaults().pad(5);
		setResizable(true);
		setMovable(true);
	}

    private void setupIcons() {
        folderIcon = new Texture("images/folder.png");
        fileIcon = new Texture("images/file.png");
        imageExtensions.add("png");
        imageExtensions.add("jpg");
        imageExtensions.add("jpeg");
        imageExtensions.add("bmp");
    }

    private void setupUI(String path) {
		textShow = new TextShow("Text Viewer");
        // Header
        VisImageTextButton importBtn = new VisImageTextButton("Import",new TextureRegionDrawable(new Texture("images/download.png")));
        VisImageTextButton modeToggle = new VisImageTextButton("Switch View",new TextureRegionDrawable(new Texture("images/grid-icon.png")));
		VisImageTextButton backBtn = new VisImageTextButton("Back",new TextureRegionDrawable(new Texture("images/back_arrow.png")));
		VisImageTextButton createBtn = new VisImageTextButton("Create",new TextureRegionDrawable(new Texture("images/add.png")));
		PopupMenu createMenu = new PopupMenu();
		MenuItem fileItem = new MenuItem("File",new TextureRegionDrawable(fileIcon),new ChangeListener(){
			@Override
			public void changed(ChangeEvent arg0, Actor arg1) {
				createNew(false);
			}
		});
		MenuItem folderItem = new MenuItem("Folder",new TextureRegionDrawable(folderIcon),new ChangeListener(){
			@Override
			public void changed(ChangeEvent arg0, Actor arg1) {
				createNew(true);
			}
		});
		MenuItem bitmapFontItem = new MenuItem("Bitmap Font",new ChangeListener(){
			@Override
			public void changed(ChangeEvent arg0, Actor arg1) {
				new BitmapFontEditor(TestApp.getCurrentApp(),currentDir,null)
					.show(getStage()).toFront();
			}
		});
		fileItem.pad(10);
		folderItem.pad(10);
		createMenu.addItem(bitmapFontItem);
		createMenu.addItem(fileItem);
		createMenu.addItem(folderItem);
        
        importBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if(pickFilesRunnable != null) pickFilesRunnable.run();
            }
        });
        
        modeToggle.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                gridMode = !gridMode;
                refreshFileList();
            }
        });
		
		backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                try {
					if(depth>0){
						setCurrentDirectory(currentDir.parent());
						depth--;
					} else if(hideWhenNoBack)
						setVisible(false);
				} catch(Exception e){}
            }
        });
		
		createBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                createMenu.showMenu(getStage(),createBtn);
            }
        });

        VisTable header = new VisTable();
		header.add(createBtn).growX().padRight(10).padBottom(8);
        header.add(importBtn).growX().padRight(10).padBottom(8).row();
        header.add(modeToggle).growX().padRight(10);
		header.add(backBtn).padRight(10).growX();
        add(header).growX().row();

        // Content area
        contentTable = new VisTable();
        scrollPane = new VisScrollPane(contentTable);
        scrollPane.setFadeScrollBars(true);
		scrollPane.setOverscroll(false,false);
		scrollPane.setScrollingDisabled(false,false);
        add(scrollPane).grow().row();
		pack();
		setHeight(Gdx.graphics.getHeight()*0.75f);
        setCurrentDirectory(path); // Get root directory
    }
	
	private void createNew(boolean isDir){
		new SingleInputDialog("Create New","Enter "+(isDir?"Directory":"File")+" name :","",(input)->{
			FileHandle newFile = Gdx.files.absolute(currentDir.file().getAbsolutePath()+"/"+input);
			if(newFile.exists()){
				toast("already exists !");
			} else {
				try {
					if(isDir)
						newFile.mkdirs();
					else newFile.writeString("",false);
					refreshFileList();
				} catch(Exception e){
					toast(e.toString());
				}
			}
		}).show(getStage());
	}
	
	public void toast(String message){
		if(toastManager!=null){
			toastManager.toFront();
			toastManager.show(message,2);
		}
	}
	
	public FileHandle getCurrentDir(){
		return currentDir;
	}
	
	public FileBrowser setToastManager(ToastManager toastManager){
		this.toastManager = toastManager;
		return this;
	}
	
	public FileBrowser setHideWhenNoBack(boolean hide){
		this.hideWhenNoBack = hide;
		return this;
	}
	
    public void refreshFileList() {
        contentTable.clear();
        contentTable.defaults().space(5);

        FileHandle[] files = currentDir.list();
		int r = 0;
		if(files!=null)
        for(FileHandle file : files) {
            Actor item = createFileItem(file);
            if(gridMode) {
                contentTable.add(item).size(150 * uiScale, 150 * uiScale);
                if(r >= 4) {
					contentTable.row();
					r = 0;
				} else r++;
            } else {
                contentTable.add(item).growX().size(500,70 * uiScale).row();
            }
        }
        
        contentTable.pack();
    }

    private Actor createFileItem(FileHandle file) {
        VisImageButton btn = new VisImageButton(new TextureRegionDrawable(getTextureFor(file)));
        VisLabel label = new VisLabel(file.name());
		label.setWrap(true);
		label.setAlignment(Align.center);
		//btn.setStyle(createButtonStyle(file));
		VisTable table = new VisTable();
        if(gridMode){
     	   table.add(btn).size(90).row();
       	 table.add(label).height(60).growX();
		} else {
			table.add(btn).size(60).padLeft(8).padRight(8);
			table.add(label).growX();
		}
		table.addListener(createFileClickListener(file,table));
		return table;
    }
	
	public Texture getTextureFor(FileHandle file){
		boolean isImage = (!file.isDirectory()) && imageExtensions.contains(file.extension().toLowerCase());
		if(isImage){
		    if(!isImagesFolder){
		        return fileIcon;
		    }
			if(projectAssetLoader.isLoaded(file.toString())){
				return projectAssetLoader.get(file.toString());
			} else {
				projectAssetLoader.loadFile(file.toString(),Texture.class);
				projectAssetLoader.finishLoading();
			}
		}
		return file.isDirectory() ? folderIcon : 
                        isImage ? getAsset(file) : fileIcon;
	}

    private VisImageButton.ButtonStyle createButtonStyle(FileHandle file) {
        Texture texture = getTextureFor(file);
        
        return new VisImageButton.ButtonStyle(
                new TextureRegionDrawable(new TextureRegion(texture)),
                null,
                null
        );
    }
	
	private Texture getAsset(FileHandle asset){
		return (projectAssetLoader!=null && projectAssetLoader.contains(asset.toString())) ? projectAssetLoader.get(asset.toString() ): new Texture(asset);
	}
	
	/*private void centerOnScreen(){
		//setPosition(100,100);
		//if(getStage()!=null)
			//setPosition((getStage().getWidth() - getWidth())*0.5f,(getStage().getWidth() - getHeight())*0.5f);
	}*/
	
	public void setRootDir(String aboulutePath){
		setRootDir(Gdx.files.absolute(aboulutePath));
	}
	
	public void setRootDir(FileHandle fileHandle){
		depth=0;
		setCurrentDirectory(fileHandle);
	}
	
    private ClickListener createFileClickListener(FileHandle file, Actor item) {
        return new ClickListener() {
            long pressStartTime;
			boolean exit = false;
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                pressStartTime = System.currentTimeMillis();
				exit = false;
                return true;
            }
			
			

			/*@Override
			public void exit(InputEvent arg0, float arg1, float arg2, int arg3, Actor arg4) {
				super.exit(arg0, arg1, arg2, arg3, arg4);
				exit = true;
			}*/

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
				if(item.hit(x,y,true)==null){
					return;
				}
				if(System.currentTimeMillis() - pressStartTime > 500) {
                    showContextMenu(file, item);
                } else {
                    if(file.isDirectory()) {
						depth++;
                        setCurrentDirectory(file);
                    } else {
                        // Handle file open
						showContextMenu(file,item);
						if(depth == 1 && file.parent().name().toLowerCase().equals("anims")){
							if(animationOpen!=null)
								animationOpen.open(file.path());
						}
                    }
                }
            }
        };
    }
	
	private FileOpen animationOpen;
	public void setAnimationOpen(FileOpen open){
		this.animationOpen = open;
	}

    private void showContextMenu(FileHandle file, Actor item) {
        PopupMenu menu = new PopupMenu();
		menu.addItem(getMenuItem("Open",()->open(file)));
        menu.addItem(getMenuItem("Delete", () -> deleteFile(file)));
        menu.addItem(getMenuItem("Rename", () -> showRenameDialog(file)));
        menu.showMenu(getStage(), item);
    }
	
	private MenuItem getMenuItem(String title,Runnable runnable){
		MenuItem menuItem = new MenuItem(title,new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				runnable.run();
			}
		});
		menuItem.pad(5);
		return menuItem;
	}
	
	private void open(FileHandle fileHandle){
		if(fileHandle.isDirectory()){
			depth++;
			setCurrentDirectory(fileHandle);
			return;
		}
		if(fileHandle.name().toLowerCase().endsWith(".s2df")){
			editFont(fileHandle);
		} else if(isJavaFolder && javaOpen!=null && fileHandle.name().toLowerCase().endsWith(".java"))
			javaOpen.open(fileHandle.path());
		else textViewer(fileHandle);
	}
	
	private void editFont(FileHandle s2df){
		new BitmapFontEditor(TestApp.getCurrentApp(),currentDir,s2df)
			.show(getStage()).toFront();
	}
	
	private boolean fileIsLarge(FileHandle fileHandle){
		return fileHandle.length() >= 1024 * 1024;//1MB
	}
	
	private void textViewer(FileHandle fileHandle){
		if(fileIsLarge(fileHandle))
			toast("File Is Large!!");
		else {
			textShow.setText(fileHandle.readString());
			textShow.toFront();
			textShow.show(getStage());
		}
	}

    private void deleteFile(FileHandle file) {
		ConfirmDialog.confirmDeleteDialog((ok)->{
			if(ok){
				if(file.isDirectory())
					file.deleteDirectory();
				else file.delete();
				refreshFileList();
			}
		}).show(getStage());
    }

    private void showRenameDialog(FileHandle file) {
        new SingleInputDialog("Rename", "New name:", file.name(), result -> {
            file.moveTo(file.parent().child(result));
            refreshFileList();
        }).show(getStage());
    }

    public void setCurrentDirectory(FileHandle dir) {
        currentDir = dir;
		if(depth==1){
			isJavaFolder = dir.name().toLowerCase().equals("java");
			isImagesFolder = dir.name().toLowerCase().equals("images");
	    }
        refreshFileList();
        addAction(Actions.sequence(
                Actions.alpha(0.7f),
                Actions.fadeIn(0.3f, Interpolation.pow3)
        ));
    }
	
	public void setCurrentDirectory(String absoultePath){
		setCurrentDirectory(Gdx.files.absolute(absoultePath));
	}

    public void setPickFilesRunnable(Runnable runnable) {
        this.pickFilesRunnable = runnable;
    }
	
	public void setOpenJavaRunnable(FileOpen fileOpen){
		this.javaOpen = fileOpen;
	}

    public void setUiScale(float scale) {
        this.uiScale = scale;
        contentTable.setScale(scale);
        contentTable.invalidateHierarchy();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        scrollPane.invalidate();
    }

    @Override
    public void dispose() {
        folderIcon.dispose();
        fileIcon.dispose();
    }
	
	public interface FileOpen {
		public void open(String file);
	}
}