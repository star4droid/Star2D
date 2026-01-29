package com.star4droid.star2d.editor.ui.variables;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.StringBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import java.util.ArrayList;
import java.util.Arrays;

public class VarsManager {

  private ArrayList<VarModel> varModelList = new ArrayList<>();
  private final String ROW = "\n";
  private VarEditor varEditor;
  private VarsListTable varsListTable;

  private FileHandle fileHandle;

  public VarsManager(FileHandle fileHandle) {
    this.fileHandle = fileHandle;
    varsListTable = new VarsListTable();
    varEditor = new VarEditor();
    load();
  }

  public VarsListTable getVarsListTable() {
    return varsListTable;
  }

  public VarEditor getVarEditor() {
    return varEditor;
  }
  
  public void addItem(){
    varEditor.addItem();
  }

  public FileHandle getFileHandle() {
    return fileHandle;
  }

  public VarsManager setFileHandle(FileHandle fileHandle) {
    this.fileHandle = fileHandle;
    load();
    return this;
  }

  public void save() {
    fileHandle.writeString(new Gson().toJson(varModelList), false);
  }

  public void load() {
    try {
      String str = fileHandle.exists() ? fileHandle.readString() : "";
      if(str.equals("")){
        varModelList = new ArrayList<VarModel>();
        varsListTable.setList(varModelList);
        return;
      }
      String json = str;
      varModelList = new Gson().fromJson(json, new TypeToken<ArrayList<VarModel>>() {}.getType());
      varsListTable.setList(varModelList);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  /***
   *
   * return all parsed code
   */
  public String getCode() {
    final StringBuilder code = new StringBuilder();
    varModelList.forEach(model -> code.append(model.getCode()).append(ROW));
    return code.toString().trim();
  }

  private void hideKeyboard() {
    Gdx.input.setOnscreenKeyboardVisible(false);
  }
  /** the add / edit variable Dialog */
  class VarEditor extends VisDialog {

    private VisTextField accessModiferInput, typeInput, nameInput, valueInput;
    private VisLabel accessModiferHint, typeHint, nameHint, valueHint;
    private VisTextButton save, cancl;
    private HoriListTable typeScroll, accessModiferScroll;
    private VarModel model;
    private boolean editMode = false;
    private int itemPosition = -1;

    public VarEditor() {
      super("Custom Variable");
      reset();
      getTitleLabel().setFontScale(1.1f);
      accessModiferInput = new VisTextField();
      typeInput = new VisTextField();
      nameInput = new VisTextField();
      valueInput = new VisTextField();
      accessModiferHint = new VisLabel("Access Modifer (Optional)");
      typeHint = new VisLabel("Variable Type");
      nameHint = new VisLabel("Variable name");
      valueHint = new VisLabel("Variable Value");
      save = new VisTextButton("Save");
      cancl = new VisTextButton("Cancel");
      accessModiferScroll = new HoriListTable();
      typeScroll = new HoriListTable();
      add().pad(10).row();
      add(accessModiferHint).growX().colspan(2).row();
      add(accessModiferInput).growX().colspan(2).row();
      add(accessModiferScroll).growX().colspan(2).row();
      add(typeHint).growX().colspan(2).row();
      add(typeInput).growX().colspan(2).row();
      add(typeScroll).growX().colspan(2).row();
      add(nameHint).growX().colspan(2).row();
      add(nameInput).growX().colspan(2).row();
      add(valueHint).growX().colspan(2).row();
      add(valueInput).growX().colspan(2).padBottom(20).row();
      add(save).expandX().center();
      add(cancl).expandX().center().row();
      add().pad(10);

      Arrays.asList("public", "private", "protected")
          .forEach(
              item -> {
                VisTextButton label = new VisTextButton(item);
                label.addListener(
                    new ClickListener() {

                      @Override
                      public void clicked(InputEvent arg0, float arg1, float arg2) {
                        super.clicked(arg0, arg1, arg2);
                        accessModiferInput.setText(item);
                      }
                    });
                accessModiferScroll.getTable().add(label).pad(10);
              });
      accessModiferScroll.layout();

      Arrays.asList("int", "double", "String", "boolean")
          .forEach(
              item -> {
                VisTextButton label = new VisTextButton(item);
                label.addListener(
                    new ClickListener() {

                      @Override
                      public void clicked(InputEvent arg0, float arg1, float arg2) {
                        super.clicked(arg0, arg1, arg2);
                        typeInput.setText(item);
                      }
                    });
                typeScroll.getTable().add(label).pad(10);
              });
      typeScroll.layout();
      save.addListener(
          new ClickListener() {

            @Override
            public void clicked(InputEvent arg0, float arg1, float arg2) {
              super.clicked(arg0, arg1, arg2);
              if (typeInput.getText().trim().equals("") || nameInput.getText().trim().equals("")) {
                // you can't save an empty variable
                // Add Error Message to User
                return;
              }
              if (model == null) model = new VarModel();
              model
                  .setAccessModifer(accessModiferInput.getText().trim())
                  .setType(typeInput.getText().trim())
                  .setName(nameInput.getText().trim())
                  .setValue(valueInput.getText().trim());
              if (editMode) varModelList.set(itemPosition, model);
              else varModelList.add(model);
              varsListTable.setList(varModelList);
              close();
            }
          });
      cancl.addListener(
          new ClickListener() {
            @Override
            public void clicked(InputEvent arg0, float arg1, float arg2) {
              super.clicked(arg0, arg1, arg2);
              close();
            }
          });
    }

    public void addItem() {
      resetFields();
      try {
        show(varsListTable.getStage());
      } catch(Error | Exception ex){}
    }

    public void editItem(VarModel model, int itemPosition) {
      resetFields();
      accessModiferInput.setText(model.getAccessModifer());
      typeInput.setText(model.getType());
      nameInput.setText(model.getName());
      valueInput.setText(model.getValue());
      editMode = true;
      VarEditor.this.model = model;
      VarEditor.this.itemPosition = itemPosition;
      show(varsListTable.getStage());
    }

    @Override
    protected void close() {
      super.close();
      resetFields();
      hideKeyboard();
    }
    /** to reset dialog to reuse it instead of make new instance of it */
    public void resetFields() {
      accessModiferInput.setText("");
      typeInput.setText("");
      nameInput.setText("");
      valueInput.setText("");
      model = null;
      itemPosition = -1;
      editMode = false;
    }
  }
  /*
    Variables ScrollView
  */
  class VarsListTable extends VisTable {

    private VisScrollPane scrollPane;
    private VisTable table;
    private ArrayList<VarModel> list = new ArrayList<>();

    public VarsListTable() {
      table = new VisTable();
      setBackground(com.kotcrab.vis.ui.VisUI.getSkin().getDrawable("window-bg"));
      table.setFillParent(true);
      scrollPane = new VisScrollPane(table, new VisScrollPane.ScrollPaneStyle());
      scrollPane.setVariableSizeKnobs(true);
      scrollPane.setScrollingDisabled(true, false);
      scrollPane.setScrollbarsVisible(false);
      scrollPane.setFadeScrollBars(true);
      scrollPane.layout();
      add(scrollPane).grow();
    }

    public void setList(ArrayList<VarModel> list) {
      if (list == null) VarsListTable.this.list = new ArrayList<>();
      else VarsListTable.this.list = list;
      refresh();
    }

    public void refresh() {
      save();
      table.clear();
      for (int i = 0; i < list.size(); ++i) {
        final VarModel model = list.get(i);
        final int pos = i;
        VarTable varTable = new VarTable(model).setItemPosition(i);
        VisImageButton optons =
            new VisImageButton(com.kotcrab.vis.ui.VisUI.getSkin().getDrawable("dots"));
        optons.addListener(
            new ClickListener() {

              @Override
              public void clicked(InputEvent arg0, float arg1, float arg2) {
                super.clicked(arg0, arg1, arg2);
                new OptionsTable(model, pos).showMenu(getStage(), optons);
              }
            });
        table.add(varTable).growX().padLeft(10);
        table.add(optons).size(50, 50).padLeft(10).padRight(10).row();
        table.add().pad(5).row();
      }
      table.pack();
      scrollPane.layout();
    }
  }
  /*
    Horizontal Scroll View For keywords Suggestion in VarEditor
  */
  class HoriListTable extends VisTable {
    private VisScrollPane scrollPane;
    private VisTable table;

    public HoriListTable() {
      table = new VisTable();
      scrollPane = new VisScrollPane(table, new VisScrollPane.ScrollPaneStyle());
      scrollPane.setVariableSizeKnobs(true);
      scrollPane.setScrollingDisabled(false, true);
      scrollPane.setScrollbarsVisible(false);
      scrollPane.setFadeScrollBars(true);
      scrollPane.layout();
      add(scrollPane);
    }

    public VisTable getTable() {
      return table;
    }

    public void clear() {
      table.clear();
    }

    @Override
    public void layout() {
      super.layout();
      table.pack();
      scrollPane.layout();
    }
  }
  /*
    Variable List Item
  */
  class VarTable extends VisTextButton {
    private VisLabel label;
    private VarModel model;
    // Item index Position in ArrayList
    private int itemPosition = -1;

    public VarTable(VarModel model) {
      super("");
      this.model = model;
      setText(model.getType() + model.SPACE + model.getName());
      getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
    }

    public VarTable setItemPosition(int i) {
      itemPosition = i;
      return VarTable.this;
    }

    public int getItemPosition() {
      return itemPosition;
    }
  }
    // u know it
    class OptionsTable extends PopupMenu {
        private float fontScale = 1.1f;
    
        public OptionsTable(VarModel model, int position) {
            super();    
            // Create menu items
            MenuItem deleteItem = new MenuItem("Delete");
            MenuItem editItem = new MenuItem("Edit");
    
            // Apply font scaling
            deleteItem.getLabel().setFontScale(fontScale);
            editItem.getLabel().setFontScale(fontScale);
    
            // Add items to the popup menu
            addItem(deleteItem);
            addSeparator();
            addItem(editItem);
    
            // Listeners
            deleteItem.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    super.clicked(event, x, y);
                    varModelList.remove(model);
                    varsListTable.setList(varModelList);
                }
            });
    
            editItem.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    super.clicked(event, x, y);
                    varEditor.editItem(model, position);
                }
            });
        }
    }
}
