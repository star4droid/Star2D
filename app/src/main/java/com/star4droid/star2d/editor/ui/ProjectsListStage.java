package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisImageTextButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.sub.EditorField;
import com.star4droid.star2d.editor.ui.sub.ExamplesDialog;
import com.star4droid.template.Utils.Utils;

public class ProjectsListStage extends Stage {
	VisTable linearTable,projectsTable;
	TestApp app;
	Runnable importRunnable,exportRunnable,backupRunnable;
	Drawable background;
	FileHandle selectedProject;
	public final SettingsDialog settingsDialog;
	ExamplesDialog examplesDialog;
	public ProjectsListStage(TestApp app){
		super();
		this.app = app;
		settingsDialog = new SettingsDialog(this,app);
		examplesDialog = new ExamplesDialog(app);
		background = drawable("background.png");
		VisTable backTable = new VisTable();
		backTable.setBackground(VisUI.getSkin().getDrawable("window-bg"));
		backTable.setFillParent(true);
		
		linearTable = new VisTable();
		projectsTable = new VisTable();
		
		linearTable.center();
		linearTable.add().growY().row();
		String dash = "-".repeat(6);
		VisLabel label = new VisLabel(dash+" Welcome to Star2D.E "+dash);
		VisLabel projectLabel = new VisLabel(dash+" Project "+dash);
		VisImageTextButton add = new VisImageTextButton("New Project",drawable("add.png")),
				importBtn = new VisImageTextButton("Import Project",drawable("download.png")),
				settings = new VisImageTextButton("Settings",drawable("events/properties.png")),
				examples = new VisImageTextButton("Examples",drawable("menu.png"));
		label.setAlignment(Align.center);
		projectLabel.setAlignment(Align.center);
		linearTable.add(label).growX().padBottom(10).row();
		VisScrollPane scrollPane = new VisScrollPane(projects());
		VisTable openNewTable = new VisTable();
		scrollPane.setFadeScrollBars(false);
		scrollPane.setFlickScroll(true);
		scrollPane.setScrollingDisabled(false,true);
		linearTable.add(scrollPane).padBottom(15).growX().row();
		linearTable.add(projectLabel).padBottom(3).growX().row();
		openNewTable.add(add).center().padLeft(8);
		openNewTable.add(importBtn).center().padLeft(8);
		linearTable.add(openNewTable).width(350).center().row();
		linearTable.add(settings).padTop(8).width(350).center().row();
		linearTable.add(examples).padTop(8).width(350).center().row();
		linearTable.add().growY();
		linearTable.setFillParent(true);
		addActor(backTable);
		addActor(linearTable);
		
		add.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                new SingleInputDialog("Create New Project","Name : ","Project",name->{
					for(char c:name.toCharArray()){
						if(!EditorField.allowedChars.contains(c+"")){
							app.toast("the name contains not allowed char!\nuse A-Z a-z or _ only");
							return;
						}
					}
					FileHandle fileHandle = Gdx.files.local("projects/"+name);
					if(fileHandle.exists()){
						app.toast("there\'s project with the same name!");
						return;
					}
					
					fileHandle.mkdirs();
					createDefaultPaths(fileHandle);
					refresh();
					app.openProject(new Project(fileHandle.file().getAbsolutePath()));
				}).show(ProjectsListStage.this);
            }
        });
		importBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if(importRunnable!=null)
					importRunnable.run();
            }
        });
		settings.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                settingsDialog.show(ProjectsListStage.this);
            }
        });
		examples.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
				examplesDialog.show(ProjectsListStage.this);
			}
		});
	}
	
	private void createDefaultPaths(FileHandle fileHandle){
		String[] paths = {"sounds","anims","images","files","icon"};
		for(String path:paths){
			FileHandle child = fileHandle.child(path);
			if(!child.exists())
				child.mkdirs();
		}
	}

	private VisTable projects(){
		projectsTable.clear();
		projectsTable.center();
		FileHandle[] files = Gdx.files.local("projects").list();
		for(FileHandle fileHandle:files){
			if(fileHandle.name().contains(" ")){
				FileHandle newHandle = fileHandle.parent().child(fileHandle.name().replace(" ","_"));
				fileHandle.moveTo(newHandle);
				projectsTable.add(projectTable(newHandle)).pad(6);
			} else projectsTable.add(projectTable(fileHandle)).pad(6);
		}
		if(files.length == 0){
		    projectsTable.add().height(10).row();
		    VisLabel label = new VisLabel("There\'s no projects\n Create New Project by clicking the button below");
		    projectsTable.add(label).padBottom(15).row();
		    projectsTable.add().height(10);
		}
		return projectsTable;
	}
	
	public void refresh(){
		projects();
	}
	
	public FileHandle getSelectedProject(){
		return selectedProject;
	}
	
	public void setExportRunnable(Runnable runnable){
		this.exportRunnable = runnable;
	}
	
	public void setBackupRunnable(Runnable runnable){
		this.backupRunnable = runnable;
	}
	
	public void setImportRunnable(Runnable runnable){
		this.importRunnable = runnable;
	}
	
	private VisTable projectTable(FileHandle fileHandle){
		VisTable table = new VisTable(),
			btnsTable = new VisTable();
		table.setBackground(VisUI.getSkin().getDrawable("separator"));
		VisImageTextButton export = new VisImageTextButton("Export",drawable("save.png")),
			del = new VisImageTextButton("Delete",drawable("delete.png")),
			backup = new VisImageTextButton("Backup",drawable("download.png"));
		table.center();
		VisLabel label = new VisLabel(fileHandle.name());
		label.setAlignment(Align.center);
		VisImage image = new VisImage(background);
		table.add(image).pad(6).minSize(224,128).growX().row();
		table.add(label).padLeft(6).padRight(6).growX().row();
		btnsTable.add(export).height(60).padLeft(5).growX();
		btnsTable.add(del).height(60).padLeft(5).growX();
		btnsTable.add(backup).height(60).padLeft(5).growX();
		table.add(btnsTable).pad(5).growX().row();
		table.add().height(10);
		image.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
				createDefaultPaths(fileHandle);
                app.openProject(new Project(fileHandle.file().getAbsolutePath()));
            }
        });
		export.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedProject = fileHandle;
				if(exportRunnable!=null)
					exportRunnable.run();
            }
        });
		backup.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedProject = fileHandle;
				if(backupRunnable!=null)
					backupRunnable.run();
            }
        });
		del.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				ConfirmDialog.confirmDeleteDialog((ok)->{
					if(ok){
						if(fileHandle.deleteDirectory())
							app.toast("Deleted...!");
						else app.toast("failed to delete..!!");
						refresh();
					}
				}).show(ProjectsListStage.this);
			}
		});
		return table;
	}
	
	private Drawable drawable(String name){
		int start = name.contains("/") ? name.lastIndexOf("/") : 0;
		String namePure = start == 0 ? name.substring(0,name.indexOf(".")):name.substring(start+1,name.indexOf(".",start));
		try {
			return VisUI.getSkin().getDrawable(namePure);
		} catch(Exception e){
			Gdx.files.external("logs/projects drawable.txt").writeString("projects drawbale : "+namePure+", full : "+name+"\n error : "+e.toString()+"\n\n",true);
			return Utils.getDrawable(Gdx.files.internal("images/"+name));
		}
	}
}