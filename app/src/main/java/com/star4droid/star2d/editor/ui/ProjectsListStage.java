package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import com.star4droid.star2d.editor.ui.sub.EditorField;
import com.star4droid.star2d.editor.ui.sub.ExamplesDialog;
import com.star4droid.template.Utils.Utils;

import static com.star4droid.star2d.editor.utils.Lang.*;

public class ProjectsListStage extends Stage {
    private VisTable rootTable, projectsTable;
    private TestApp app;
    private Runnable importRunnable, exportRunnable, backupRunnable;
    private Drawable icon;
    private FileHandle selectedProject;
    public final SettingsDialog settingsDialog;
    private final ExamplesDialog examplesDialog;
    private VisTextField searchField;

    public ProjectsListStage(TestApp app) {
        this.app = app;
        settingsDialog = new SettingsDialog(this, app);
        examplesDialog = new ExamplesDialog(app);
        icon = drawable("shine-logo.png");

        // Root container
        rootTable = new VisTable();
        rootTable.setFillParent(true);
        rootTable.top().pad(10);

        // Welcome label
        VisLabel welcomeLabel = new VisLabel(getTrans("welcome"));
        welcomeLabel.setAlignment(Align.center);
        welcomeLabel.setFontScale(1.2f);
        rootTable.add(welcomeLabel).colspan(4).padBottom(10).row();

        // Search bar
        searchField = new VisTextField();
        searchField.setMessageText(getTrans("searchProjects"));
        searchField.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                filterProjects();
            }
        });
        rootTable.add(searchField).growX().colspan(4).padBottom(8).padLeft(8).padRight(8).row();

        // Buttons row
        VisTextButton newProjectBtn = new VisTextButton(getTrans("newProject"));
        VisTextButton importBtn = new VisTextButton(getTrans("importProject"));
        VisTextButton examplesBtn = new VisTextButton(getTrans("examples"));
        //rootTable.add().growX();
        rootTable.add(newProjectBtn).padRight(5);
        rootTable.add(importBtn).padRight(5);
        rootTable.add(examplesBtn).padRight(5);
        rootTable.add().growX().row();

        // Scrollable projects grid
        projectsTable = new VisTable(true);
        projectsTable.top();
        VisScrollPane scrollPane = new VisScrollPane(projectsTable);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(false, true);
        rootTable.add(scrollPane).colspan(4).grow().padTop(10);

        addActor(rootTable);

        // Button listeners
        newProjectBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                createNewProject();
            }
        });
        importBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (importRunnable != null) importRunnable.run();
            }
        });
        examplesBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                examplesDialog.show(ProjectsListStage.this);
            }
        });

        refresh();
    }

    private void createNewProject() {
        new SingleInputDialog(getTrans("newProject"), getTrans("name"), "Project", name -> {
            name = name.replace(" ", "_");
            for (char c : name.toCharArray()) {
                if (!EditorField.allowedChars.contains(c + "")) {
                    app.toast("Invalid name! Use A-Z, a-z, _ only.");
                    return;
                }
            }
            FileHandle fileHandle = Gdx.files.local("projects/" + name);
            if (fileHandle.exists()) {
                app.toast(getTrans("projectConflict"));
                return;
            }
            fileHandle.mkdirs();
            createDefaultPaths(fileHandle);
            refresh();
            app.openProject(new Project(fileHandle.file().getAbsolutePath()));
        }).show(this);
    }

    private void createDefaultPaths(FileHandle fileHandle) {
        String[] paths = {"sounds", "anims", "images", "files", "icon"};
        for (String path : paths) {
            FileHandle child = fileHandle.child(path);
            if (!child.exists())
                child.mkdirs();
        }
    }
    
    public FileHandle getSelectedProject(){
		return selectedProject;
	}

    public void refresh() {
        projectsTable.clear();
        FileHandle[] files = Gdx.files.local("projects").list();
        int colCount = 2; // two per row like screenshot
        int col = 0;

        if (files.length == 0) {
            projectsTable.add(new VisLabel(getTrans("noProjects"))).colspan(colCount).pad(20);
            return;
        }

        for (FileHandle file : files) {
            VisTable card = createProjectCard(file);
            projectsTable.add(card).pad(8).growX();
            col++;
            if (col >= colCount) {
                col = 0;
                projectsTable.row();
            }
        }
    }

    private VisTable createProjectCard(FileHandle file) {
        VisTable card = new VisTable();
        card.setBackground(VisUI.getSkin().getDrawable("window-bg"));
        
        // Icon
        VisImage icon = new VisImage(ProjectsListStage.this.icon);
        card.add(icon).size(80).pad(25).row();

        // Name
        VisLabel nameLabel = new VisLabel(file.name());
        nameLabel.setAlignment(Align.center);
        card.add(nameLabel).padBottom(25).row();
        
        // Open project click
        icon.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                createDefaultPaths(file);
                app.openProject(new Project(file.file().getAbsolutePath()));
            }
        });
        
        // More button
        VisImageButton moreBtn = new VisImageButton(drawable("dots.png"));
        moreBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showProjectMenu(file, moreBtn);
            }
        });
        
        card.add().growX();
        card.add(moreBtn).pad(3).right().row();
        return card;
    }

    private void showProjectMenu(FileHandle file, VisImageButton anchor) {
        PopupMenu menu = new PopupMenu();
        MenuItem exportItem = new MenuItem(getTrans("export"));
        MenuItem backupItem = new MenuItem(getTrans("backup"));
        MenuItem deleteItem = new MenuItem(getTrans("delete"));

        exportItem.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedProject = file;
                if (exportRunnable != null) exportRunnable.run();
            }
        });
        backupItem.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedProject = file;
                if (backupRunnable != null) backupRunnable.run();
            }
        });
        deleteItem.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ConfirmDialog.confirmDeleteDialog(ok -> {
                    if (ok) {
                        if (file.deleteDirectory())
                            app.toast(getTrans("deleted"));
                        else app.toast(getTrans("failedToDelete"));
                        refresh();
                    }
                }).show(ProjectsListStage.this);
            }
        });

        menu.addItem(exportItem);
        menu.addItem(backupItem);
        menu.addItem(deleteItem);
        menu.showMenu(this, anchor);
    }

    private void filterProjects() {
        String query = searchField.getText().toLowerCase();
        projectsTable.clear();
        FileHandle[] files = Gdx.files.local("projects").list();
        int colCount = 2;
        int col = 0;

        for (FileHandle file : files) {
            if (file.name().toLowerCase().contains(query)) {
                projectsTable.add(createProjectCard(file)).pad(8).growX();
                col++;
                if (col >= colCount) {
                    col = 0;
                    projectsTable.row();
                }
            }
        }
    }

    private Drawable drawable(String name) {
        int start = name.contains("/") ? name.lastIndexOf("/") : 0;
        String namePure = start == 0 ? name.substring(0, name.indexOf(".")) : name.substring(start + 1, name.indexOf(".", start));
        try {
            return VisUI.getSkin().getDrawable(namePure);
        } catch (Exception e) {
            Gdx.files.external("logs/projects drawable.txt").writeString("projects drawable : " + namePure + ", full : " + name + "\n error : " + e.toString() + "\n\n", true);
            return Utils.getDrawable(Gdx.files.internal("images/" + name));
        }
    }

    public void setExportRunnable(Runnable runnable) {
        this.exportRunnable = runnable;
    }

    public void setBackupRunnable(Runnable runnable) {
        this.backupRunnable = runnable;
    }

    public void setImportRunnable(Runnable runnable) {
        this.importRunnable = runnable;
    }
}
