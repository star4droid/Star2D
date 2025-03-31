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
import com.star4droid.template.Utils.Utils;

public class ProjectsListStage extends Stage {
	VisTable linearTable,projectsTable;
	TestApp app;
	Runnable importRunnable,exportRunnable,backupRunnable;
	Drawable background;
	FileHandle selectedProject;
	public ProjectsListStage(TestApp app){
		super();
		this.app = app;
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
				importBtn = new VisImageTextButton("Import Project",drawable("download.png"));
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
		linearTable.add(openNewTable).width(350).center();
		linearTable.add().growY();
		linearTable.setFillParent(true);
		addActor(backTable);
		addActor(linearTable);
		
		add.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                new SingleInputDialog("Create New Project","Name : ","Project",name->{
					FileHandle fileHandle = Gdx.files.local("projects/"+name);
					fileHandle.mkdirs();
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
	}
	
	private VisTable projects(){
		projectsTable.clear();
		projectsTable.center();
		FileHandle[] files = Gdx.files.local("projects").list();
		for(FileHandle fileHandle:files){
			projectsTable.add(projectTable(fileHandle)).pad(6);
		}
		if(files.length == 0){
		    VisLabel label = new VisLabel("There\'s no projects\n Create New Project by clicking on the button below");
		    projectsTable.add(label).pad(8);
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
		return Utils.getDrawable(Utils.internal("images/"+name));
	}
}