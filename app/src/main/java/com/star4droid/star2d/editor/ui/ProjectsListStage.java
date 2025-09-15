package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import static com.star4droid.star2d.editor.utils.Lang.*;

public class ProjectsListStage extends Stage {
    private VisTable rootTable, projectsTable, welcomeTable, projectsContainer;
    private TestApp app;
    private Runnable importRunnable, exportRunnable, backupRunnable;
    private Drawable sparkleIcon, planetIcon, mainLogo, shineIcon;
    private FileHandle selectedProject;
    public final SettingsDialog settingsDialog;
    private final ExamplesDialog examplesDialog;
    private VisTextField searchField;

    public ProjectsListStage(TestApp app) {
        this.app = app;
        settingsDialog = new SettingsDialog(this, app);
        examplesDialog = new ExamplesDialog(app);
        sparkleIcon = drawable("sparkle-icon");
        planetIcon = drawable("planet-icon");
        mainLogo = drawable("logo");
        shineIcon = drawable("shine-logo");

        rootTable = new VisTable();
        rootTable.setBackground(drawable("bg"));
        rootTable.setFillParent(true);
        rootTable.align(Align.center);

        welcomeTable = createWelcomePanel();
        projectsContainer = createProjectsPanel();

        rootTable.add(welcomeTable).grow().pad(30).row();
        
        addActor(rootTable);

        refresh();
    }

    private VisTable createWelcomePanel() {
        VisTable table = new VisTable();
        table.setBackground(drawable("field-color"));
        table.pad(30);

        VisImage logoImg = new VisImage(mainLogo);
        logoImg.setColor(Color.WHITE);
        table.add(logoImg).size(60, 60).padBottom(10).align(Align.topLeft).row();
        VisLabel welcomeLabel = new VisLabel("Welcome to Star2D.E");
        welcomeLabel.setColor(Color.WHITE);
        welcomeLabel.setFontScale(1.4f);
        table.add(welcomeLabel).align(Align.topLeft).padBottom(40).row();

        VisTable largeButtonsTable = new VisTable();
        
        VisTable newProjectBtnContainer = new VisTable();
        newProjectBtnContainer.setBackground(drawable("button-blue"));
        newProjectBtnContainer.pad(10);
        newProjectBtnContainer.add(new VisImage(sparkleIcon)).size(32, 32).padRight(10);
        newProjectBtnContainer.add(new VisLabel("New Project")).growX();
        newProjectBtnContainer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                 createNewProject();
            }
        });

        VisTable importProjectBtnContainer = new VisTable();
        importProjectBtnContainer.setBackground(drawable("border"));
        importProjectBtnContainer.pad(10);
        importProjectBtnContainer.add(new VisImage(planetIcon)).size(32, 32).padRight(10).padLeft(5);
        importProjectBtnContainer.add(new VisLabel("Import Project")).growX();
        importProjectBtnContainer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (importRunnable != null) importRunnable.run();
            }
        });

        largeButtonsTable.add(newProjectBtnContainer).growX().height(60).row();
        largeButtonsTable.add(importProjectBtnContainer).growX().height(60).padTop(15).row();
        table.add(largeButtonsTable).growX().padBottom(40).row();
        
        VisTable smallButtonsTable = new VisTable();
        
        VisTable settingsTable = new VisTable();
        settingsTable.setBackground(drawable("border"));
        settingsTable.add(new VisImage(drawable("properties"))).size(48, 48).row();
        settingsTable.add(new VisLabel("Settings")).padTop(5);
        settingsTable.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                settingsDialog.show(ProjectsListStage.this);
            }
        });
        
        VisTable examplesTable = new VisTable();
        examplesTable.setBackground(drawable("border"));
        examplesTable.add(new VisImage(drawable("menu"))).size(48, 48).row();
        examplesTable.add(new VisLabel("Examples")).padTop(5);
        examplesTable.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                examplesDialog.show(ProjectsListStage.this);
            }
        });
        
        VisTable projectsTableBtn = new VisTable();
        projectsTableBtn.setBackground(drawable("border"));
        projectsTableBtn.add(new VisImage(drawable("menu"))).size(48, 48).row();
        projectsTableBtn.add(new VisLabel("Projects")).padTop(5);
        projectsTableBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                projectsContainer.setX(Gdx.graphics.getWidth());
                projectsContainer.setVisible(true);
                addActor(projectsContainer);
                projectsContainer.addAction(Actions.moveTo(0, projectsContainer.getY(), 0.5f));
                welcomeTable.remove();
            }
        });

        smallButtonsTable.add(settingsTable).size(150, 100).padRight(20);
        smallButtonsTable.add(examplesTable).size(150, 100).padRight(20);
        smallButtonsTable.add(projectsTableBtn).size(150, 100);
        smallButtonsTable.add().growX();
        table.add(smallButtonsTable).growX().row();

        table.add(new VisLabel("")).growY();

        return table;
    }

    private VisTable createProjectsPanel() {
        VisTable table = new VisTable();
        table.setBackground(drawable("field-color"));
        table.setFillParent(true);
        table.pad(30);

        VisTable topSection = new VisTable();
        topSection.align(Align.topLeft);

        VisImage logoImg = new VisImage(mainLogo);
        topSection.add(logoImg).size(30, 30);

        VisLabel titleLabel = new VisLabel("Star2D.E");
        titleLabel.setFontScale(1.1f);
        topSection.add(titleLabel).padLeft(10).align(Align.left).expandX();

        VisImageButton backBtn = new VisImageButton(drawable("back_arrow"));
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                welcomeTable.setX(-Gdx.graphics.getWidth());
                welcomeTable.setVisible(true);
                addActor(welcomeTable);
                welcomeTable.addAction(Actions.sequence(
                    Actions.moveTo(0, welcomeTable.getY(), 0.5f),
                    Actions.run(() -> projectsContainer.remove())
                ));
            }
        });
        
        topSection.add(backBtn).size(40, 40);

        topSection.add().growX();
        table.add(topSection).growX().padBottom(20).row();
        
        VisLabel recentProjectsLabel = new VisLabel("Recent Projects");
        recentProjectsLabel.setFontScale(1.1f);
        table.add(recentProjectsLabel).align(Align.left).padBottom(10).row();

        VisTable searchContainer = new VisTable();
        searchContainer.setBackground(drawable("button-blue"));
        searchContainer.pad(10);
        Image searchIconImg = new Image(drawable("search-icon"));
        searchIconImg.setColor(new Color(0.7f, 0.7f, 0.7f, 1f));
        searchContainer.add(searchIconImg).size(24, 24).padRight(10);
        searchField = new VisTextField();
        searchField.setMessageText("Search projects");
        searchField.setStyle(VisUI.getSkin().get("default", VisTextField.VisTextFieldStyle.class));
        searchField.getStyle().background = null;
        searchField.setFocusBorderEnabled(false);
        searchField.setColor(Color.WHITE);
        searchContainer.add(searchField).growX();
        table.add(searchContainer).growX().padBottom(20).row();
        
        searchField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                filterProjects();
            }
        });

        projectsTable = new VisTable();
        projectsTable.top();
        projectsTable.align(Align.left);
        VisScrollPane scrollPane = new VisScrollPane(projectsTable);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        table.add(scrollPane).grow();

        return table;
    }

    private VisTable createProjectCard(FileHandle file) {
        VisTable card = new VisTable();
        card.setBackground(drawable("button-blue"));
        card.pad(10);
        card.padLeft(20).padRight(20);
        card.align(Align.left);
        
        VisTable clickableArea = new VisTable();
        clickableArea.align(Align.left);
        clickableArea.add(new VisImage(shineIcon)).size(40,40).padRight(15);
        
        VisTable infoTable = new VisTable();
        infoTable.align(Align.left);
        
        VisLabel nameLabel = new VisLabel(file.name());
        nameLabel.setFontScale(1.1f);
        nameLabel.setWrap(false);
        nameLabel.setEllipsis(true);
        infoTable.add(nameLabel).left().padBottom(5).row();

        VisLabel dateLabel = new VisLabel("Created: " + getFileDateString(file));
        dateLabel.setColor(Color.LIGHT_GRAY);
        dateLabel.setFontScale(0.8f);
        infoTable.add(dateLabel).left().row();
        
        VisLabel sizeLabel = new VisLabel("Size: ...");
        sizeLabel.setColor(Color.LIGHT_GRAY);
        sizeLabel.setFontScale(0.8f);
        infoTable.add(sizeLabel).left().row();

        Executors.newSingleThreadExecutor().execute(()->{
            final String sizeText = getFileSizeString(file);
            Gdx.app.postRunnable(()->{
                sizeLabel.setText("Size: " + sizeText);
            });
        });

        clickableArea.add(infoTable).expandX().fillX();
        
        card.add(clickableArea).expandX().fillX();
        
        VisImageButton moreBtn = new VisImageButton(drawable("dots.png"));
        moreBtn.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                event.stop();
                showProjectMenu(file, moreBtn);
            }
        });
        
        card.add(moreBtn).size(40, 40).pad(10);
        
        clickableArea.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                createDefaultPaths(file);
                app.openProject(new Project(file.file().getAbsolutePath()));
            }
        });

        return card;
    }
    
    private void showProjectMenu(FileHandle file, VisImageButton anchor) {
        PopupMenu menu = new PopupMenu();
        MenuItem exportItem = new MenuItem("Export");
        MenuItem backupItem = new MenuItem("Backup");
        MenuItem deleteItem = new MenuItem("Delete");

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
                new ConfirmDialog("Confirm Deletion", "Are you sure you want to delete this project?", ok -> {
                    if (ok) {
                        if (file.deleteDirectory())
                            app.toast("Project deleted.");
                        else app.toast("Failed to delete project.");
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

    private void createNewProject() {
        new SingleInputDialog("New Project", "Enter project name:", "NewProject", name -> {
            name = name.replace(" ", "_");
            for (char c : name.toCharArray()) {
                if (!EditorField.allowedChars.contains(c + "")) {
                    app.toast("Invalid name! Use A-Z, a-z, _ only.");
                    return;
                }
            }
            FileHandle fileHandle = Gdx.files.local("projects/" + name);
            if (fileHandle.exists()) {
                app.toast("Project with this name already exists.");
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

    public void refresh() {
        filterProjects();
    }

    private void filterProjects() {
        String query = searchField.getText().toLowerCase();
        projectsTable.clear();
        FileHandle[] files = Gdx.files.local("projects").list();

        if (files.length == 0) {
            projectsTable.add(new VisLabel("No Projects")).expand().center();
            return;
        }

        for (FileHandle file : files) {
            if (file.name().toLowerCase().contains(query)) {
                VisTable card = createProjectCard(file);
                projectsTable.add(card).growX().height(100).pad(5).row();
            }
        }
    }

    private Drawable drawable(String name) {
        if(!(name.contains("/")||name.contains("."))) {
            try {
                return VisUI.getSkin().getDrawable(name);
            } catch (Exception | Error e) {
                // Fallback if not in skin by simple name
            }
        }
        int start = name.contains("/") ? name.lastIndexOf("/") : 0;
        String namePure = start == 0 ? name : name.substring(start + 1);
        if (namePure.contains(".")) {
             namePure = namePure.substring(0, namePure.indexOf("."));
        }
        
        try {
            return VisUI.getSkin().getDrawable(namePure);
        } catch (Exception | Error e) {
            Gdx.files.external("logs/projects drawable.txt").writeString("projects drawable : " + namePure + ", full : " + name + "\n error : " + e.toString() + "\n\n", true);
            return Utils.getDrawable(Gdx.files.internal("images/" + name));
        }
    }
    
    public FileHandle getSelectedProject() {
        return selectedProject;
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

    public static String getFileDateString(FileHandle file) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        return sdf.format(new Date(file.lastModified()));
    }

    public static String getFileSizeString(FileHandle file) {
        long bytes = getFileSize(file);
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format(Locale.ENGLISH, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static long getFileSize(FileHandle file) {
        if (file.isDirectory()) {
            long size = 0;
            for (FileHandle child : file.list()) {
                size += getFileSize(child);
            }
            return size;
        } else {
            return file.length();
        }
    }
}
