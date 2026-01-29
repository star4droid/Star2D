package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragListener;
import com.badlogic.gdx.utils.TimeUtils;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.widget.VisWindow;

import java.util.ArrayList;
import java.util.List;

public class VisualNode extends VisWindow {
  private String code;
  private final Stage nodeEditor;
  private VisualNode nextNode;
  private VisualNode trueNode;
  private VisualNode falseNode;

  private VisTable fieldsTable;
  private VisTable booleanTable;

  private VisTable nextOutputSquare;
  private VisTable trueOutputSquare;
  private VisTable falseOutputSquare;

  private boolean isConnecting = false;
  private boolean isDragged = false;
  private Vector2 connectionStartPoint = new Vector2();
  private Vector2 touchPoint = new Vector2();
  VisDialog dialog;
  private PopupMenu popup;
  private float touchDownTime = -1f;
  private final float CLICK_THRESHOLD = 0.1f;
  private boolean popupVisibile = false;
  private MenuItem copyItem, deleteItem;
  public VisTable contentTable;

  public VisualNode(String title, NodeEditor nodeEditor) {
    super(title, NodeEditorApp.orangeSkin.get("maroon", WindowStyle.class));
    this.nodeEditor = nodeEditor;
    setMovable(true);
    defaults().pad(2).minSize(150, 100);

    fieldsTable = new VisTable();
    booleanTable = new VisTable();
    setKeepWithinStage(false);

    VisTable nextTable = new VisTable();
    //nextTable.setBackground(VisUI.getSkin().getDrawable("separator"));
    nextOutputSquare = createConnectionSquare();
    add(nextTable).right().expandX().fillX().height(40).row();
    nextTable.add().growX();
    nextTable.add(nextOutputSquare).right().size(35).padRight(8);
    addDragListener(nextOutputSquare, "next");

    contentTable = new VisTable();
    //contentTable.setBackground(VisUI.getSkin().getDrawable("separator"));
    contentTable.add(fieldsTable).expand().fill().row();
    contentTable.add(booleanTable).right().row();
    add(contentTable).expand().fill().row();
    popup = new PopupMenu();
    copyItem = new MenuItem("Copy");
    deleteItem = new MenuItem("Delete");
    copyItem.addListener(
        new ClickListener() {

          @Override
          public void clicked(InputEvent arg0, float arg1, float arg2) {
            VisualNode newNode = new VisualNode(getTitleLabel().getText().toString(), nodeEditor);

            newNode.setCode(VisualNode.this.code);
            newNode.setIsBooleanNode(VisualNode.this.isBooleanNode());
            newNode.setDeletable(VisualNode.this.isDeletable());
            newNode.setPosition(getX() + 20, getY() + 20);
            newNode.setColor(VisualNode.this.getColor());

            for (Actor actor : fieldsTable.getChildren()) {
              if (actor instanceof NodeField) {
                NodeField field = (NodeField)actor;
                NodeField newField =
                    new NodeField(field.getFieldName(), field.value, (UiStage) field.stage);
                newNode.add(newField);
              }
            }
            nodeEditor.addNode(newNode);

            popup.remove();
            popupVisibile = false;
          }
        });
    deleteItem.addListener(
        new ClickListener() {

          @Override
          public void clicked(InputEvent arg0, float arg1, float arg2) {
            VisualNode.this.remove();
          }
        });
    popup.addItem(copyItem);
    popup.addItem(deleteItem);
    addListener(
        new InputListener() {

          @Override
          public void touchUp(InputEvent arg0, float x, float y, int arg3, int arg4) {
            if (isDeletable()) {
              float elapsed = (TimeUtils.nanoTime() - touchDownTime) / 1_000_000_000f;

              if (elapsed <= CLICK_THRESHOLD) {
                // Quick click → toggle popup
                if (!popupVisibile) {
                  popup.showMenu(
                      getStage(), x + VisualNode.this.getX(), y + VisualNode.this.getY());
                  popupVisibile = true;
                } else {
                  popup.remove();
                  popupVisibile = false;
                }
              } else {
                // Long press → hide popup if shown
                if (popupVisibile) {
                  popup.remove();
                  popupVisibile = false;
                }
              }
            }
          }

          @Override
          public boolean touchDown(InputEvent arg0, float arg1, float arg2, int arg3, int arg4) {
            touchDownTime = TimeUtils.nanoTime();
            return true;
          }
        });
    // Draggable functionality
    addListener(
        new DragListener() {
          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (event.getTarget() == nextOutputSquare
                || event.getTarget() == trueOutputSquare
                || event.getTarget() == falseOutputSquare) {
              isDragged = false;
              return false;
            }
            isDragged = true;
            return super.touchDown(event, x, y, pointer, button);
          }

          @Override
          public void drag(InputEvent event, float x, float y, int pointer) {
            if (isConnecting) return;
            moveBy(x - getWidth() / 2, y - getHeight() / 2);
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            isDragged = false;
            super.touchUp(event, x, y, pointer, button);
          }
        });
    pack();
  }

  private VisTable createConnectionSquare() {
    VisTable square = new VisTable();
    square.setBackground(VisUI.getSkin().getDrawable("white"));
    square.setTouchable(Touchable.enabled);
    return square;
  }

  private void addDragListener(final VisTable square, final String connectionType) {
    square.addListener(
        new DragListener() {
          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            isConnecting = true;
            if ("next".equals(connectionType)) {
              setNextNode(null);
            } else if ("true".equals(connectionType)) {
              setTrueNode(null);
            } else if ("false".equals(connectionType)) {
              setFalseNode(null);
            }
            square.setBackground(VisUI.getSkin().getDrawable("list-selection"));
            connectionStartPoint.set(
                square.localToStageCoordinates(
                    new Vector2(square.getWidth() / 2, square.getHeight() / 2)));
            return true;
          }

          @Override
          public void touchDragged(InputEvent event, float x, float y, int pointer) {
            touchPoint.set(event.getStageX(), event.getStageY());
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            isConnecting = false;
            square.setBackground(VisUI.getSkin().getDrawable("white"));
            Actor targetActor = nodeEditor.hit(event.getStageX(), event.getStageY(), true);
            VisualNode targetNode = findParentVisualNode(targetActor);

            if (targetNode != null && targetNode != VisualNode.this) {
              if ("next".equals(connectionType)) {
                setNextNode(targetNode);
              } else if ("true".equals(connectionType)) {
                setTrueNode(targetNode);
              } else if ("false".equals(connectionType)) {
                setFalseNode(targetNode);
              }
            }
          }
        });
  }

  public void popupHide() {
    if (popupVisibile) {
      popup.remove();
      popupVisibile = false;
    }
  }

  private VisualNode findParentVisualNode(Actor actor) {
    if (actor == null) return null;
    if (actor instanceof VisualNode) return (VisualNode) actor;
    return findParentVisualNode(actor.getParent());
  }

  private boolean booleanNode = false;

  public boolean isBooleanNode() {
    return booleanNode;
  }

  public void setIsBooleanNode(boolean isBoolean) {
    booleanNode = isBoolean;
    // (keep your current true/false square creation here)

    booleanTable.clearChildren();
    if (isBoolean) {
      VisTable trueOutput = new VisTable();
      trueOutput.add(new VisLabel("true")).padRight(5);
      trueOutputSquare = createConnectionSquare();
      trueOutput.add(trueOutputSquare).size(35);
      booleanTable.add(trueOutput).pad(5).row();
      addDragListener(trueOutputSquare, "true");

      VisTable falseOutput = new VisTable();
      falseOutput.add(new VisLabel("false")).padRight(5);
      falseOutputSquare = createConnectionSquare();
      falseOutput.add(falseOutputSquare).size(35);
      booleanTable.add(falseOutput).pad(5).row();
      addDragListener(falseOutputSquare, "false");
    } else {
      trueOutputSquare = null;
      falseOutputSquare = null;
    }
    pack();
  }

  public void drawConnectionLines(ShapeRenderer shapeRenderer) {
    if (isConnecting) {
      shapeRenderer.setColor(Color.RED);
      float horizontalDistance = 50;
      Vector2 middlePoint =
          new Vector2(connectionStartPoint.x + horizontalDistance, connectionStartPoint.y);
      shapeRenderer.line(
          connectionStartPoint.x, connectionStartPoint.y, middlePoint.x, middlePoint.y);
      shapeRenderer.line(middlePoint.x, middlePoint.y, touchPoint.x, touchPoint.y);
    }
    if (nextNode != null && nextNode.getParent() == null) nextNode = null;
    if (trueNode != null && trueNode.getParent() == null) trueNode = null;
    if (falseNode != null && falseNode.getParent() == null) falseNode = null;
    drawPermanentLine(shapeRenderer, nextNode, nextOutputSquare);
    drawPermanentLine(shapeRenderer, trueNode, trueOutputSquare);
    drawPermanentLine(shapeRenderer, falseNode, falseOutputSquare);
  }

  private void drawPermanentLine(
      ShapeRenderer shapeRenderer, VisualNode targetNode, VisTable outputSquare) {
    if (targetNode != null && outputSquare != null) {
      Vector2 start =
          outputSquare.localToStageCoordinates(
              new Vector2(outputSquare.getWidth() / 2, outputSquare.getHeight() / 2));
      Vector2 end = targetNode.localToStageCoordinates(new Vector2(0, targetNode.getHeight() / 2));

      shapeRenderer.setColor(Color.WHITE);
      float horizontalDistance = 50;
      Vector2 middlePoint = new Vector2(start.x + horizontalDistance, start.y);

      shapeRenderer.line(start.x, start.y, middlePoint.x, middlePoint.y);
      shapeRenderer.line(middlePoint.x, middlePoint.y, end.x, end.y);
    }
  }

  public void add(NodeField field) {
    fieldsTable.add(field).fillX().expandX().spaceBottom(5).row();
    pack();
  }

  public VisualNode getNextNode() {
    return nextNode;
  }

  public VisualNode getTrueNode() {
    return trueNode;
  }

  public VisualNode getFalseNode() {
    return falseNode;
  }

  public void setNextNode(VisualNode node) {
    this.nextNode = node;
  }

  public void setTrueNode(VisualNode node) {
    this.trueNode = node;
  }

  public void setFalseNode(VisualNode node) {
    this.falseNode = node;
  }

  public boolean isConnecting() {
    return isConnecting;
  }

  public boolean isDragged() {
    return isDragged;
  }

  // =================== CUSTOM VIRTUAL KEYBOARD ===================
  // =================== CUSTOM VIRTUAL KEYBOARD ===================
  public static class VirtualKeyboard extends VisTable {
    private final TextArea targetTextArea;
    private final Runnable onConfirm; // ✅ callback for ✓
    private int cursorPosition = 0;

    public VirtualKeyboard(TextArea textArea, Runnable onConfirm) {
      this.targetTextArea = textArea;
      this.onConfirm = onConfirm;
      this.cursorPosition = textArea.getText().length();
      createKeyboard();
    }

    private void createKeyboard() {
      // Create keyboard layout matching the image
      String[][] keyLayout = {
        {"1", "2", "3", "X", "-"},
        {"4", "5", "6", "C", "-"},
        {"7", "8", "9", "✓", "-"},
        {".", "0", "-", "▼", "-"},
        {"+", "*", "/", "(", "-"},
        {">", "<", "=", ")", "-"},
        {"||", "&&", "!", "\"", "-"}
      };

      float buttonSize = 50f;

      for (int row = 0; row < keyLayout.length; row++) {
        for (int col = 0; col < keyLayout[row].length; col++) {
          String keyText = keyLayout[row][col];

          if ("-".equals(keyText)) {
            add().size(buttonSize).pad(2);
          } else {
            VisTextButton keyButton = new VisTextButton(keyText);
            keyButton.addListener(
                new ClickListener() {
                  @Override
                  public void clicked(InputEvent event, float x, float y) {
                    try {
                        handleKeyPress(keyText);
                    } catch(Exception ex){}
                  }
                });

            // Colors
            if ("X".equals(keyText)) {
              keyButton.setColor(1f, 0.3f, 0.3f, 1f);
            } else if ("C".equals(keyText)) {
              keyButton.setColor(1f, 1f, 0.3f, 1f);
            } else if ("✓".equals(keyText)) {
              keyButton.setColor(0.3f, 1f, 0.3f, 1f);
            } else if ("▼".equals(keyText)) {
              keyButton.setColor(0.3f, 0.3f, 1f, 1f);
            }

            add(keyButton).size(buttonSize).pad(2);
          }
        }
        row();
      }
    }

    private void handleKeyPress(String key) {
      String currentText = targetTextArea.getText();
      switch (key) {
        case "X":
          targetTextArea.setText("");
          cursorPosition = 0;
          break;
        case "C":
          String newText = currentText.substring(0, currentText.length() - 1);
          targetTextArea.setText(newText);
          break;
        case "✓":
          if (onConfirm != null) onConfirm.run(); // ✅ do same as Save button
          break;
        case "▼":
          cursorPosition = targetTextArea.getText().length();
          break;
        default:
          insertTextAtCursor(key);
          break;
      }

      targetTextArea.setCursorPosition(cursorPosition);
    }

    private void insertTextAtCursor(String text) {
      String currentText = targetTextArea.getText();
      String newText = currentText + text;
      targetTextArea.setText(newText);
      /*
          newText = currentText.substring(0, cursorPosition) + text + currentText.substring(cursorPosition);
      targetTextArea.setText(newText);
      cursorPosition += text.length();*/
    }
  }

  public VisTable getFieldsTable() {
    return fieldsTable;
  }
  
  public static VisTextButton.VisTextButtonStyle getStyle(VisTextButton.VisTextButtonStyle dStyle, Color color){
    VisTextButton.VisTextButtonStyle style = new VisTextButton.VisTextButtonStyle(dStyle);
    Drawable drawable = VisUI.getSkin().newDrawable("white", color);
    style.up = drawable;
    style.down = drawable;
    style.over = drawable;
    return style;
  }
  

  // =================== NODE FIELD ===================
  public static class NodeField extends VisTable {
    public VisTextButton valueButton;
    public String value,name;
    public final Stage stage;
    private VisDialog dialog;
    private final String fieldName; // Add this field

    public NodeField(String name, String initialValue, UiStage stage) {
        this.name = name;
        if(name.contains("(")){
      this.fieldName = name.substring(name.indexOf("(")+1,name.indexOf(")"));
            }else{this.fieldName=name;}
      
      VisTextButton nameBtn = new VisTextButton("  "+fieldName + " ");
      nameBtn.clearListeners();
      nameBtn.getLabel().setFontScale(0.75f);
      add(nameBtn).padLeft(5).height(35).minWidth(140).expandX().left();
      this.stage = stage;
      this.value = initialValue;

      String displayValue =
          initialValue.length() > 7 ? initialValue.substring(0, 6) + "..." : initialValue;
      valueButton = new VisTextButton(displayValue);
      valueButton.getLabel().setFontScale(0.75f);
      valueButton.setStyle(getStyle((VisTextButton.VisTextButtonStyle)nameBtn.getStyle(), Color.valueOf("#858585")));
      add(valueButton).size(120, 35).expandX().right();

      valueButton.addListener(
          new ClickListener() {
            long startTime;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
              startTime = System.currentTimeMillis();
              return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
              if (Math.abs(System.currentTimeMillis() - startTime) <= 300){
                FieldSuggestionDialog.show(
                    stage,
                    FieldSuggestionList.provideListForType(name),
                    new FieldSuggestionDialog.OnItemClickListener() {

                      @Override
                      public void onItemClick(String item) {
                          if(item=="edit value"){
                              showValueInputDialog();
                              }else{
                                  NodeField.this.value = item;
                                  String displayValue = item.length() > 7 ? item.substring(0, 6) + "..." : item;
      valueButton.setText(displayValue);
                                  }
                      }
                    });
                    }
            }
          });
    }

    /** Get the field name for serialization */
    public String getFieldName() {
      return name;
    }
    
    // TODO : re-use instead of creating new one every tme...
    private void saveValue(TextArea textArea) {
      value = textArea.getText();
      String displayValue = value.length() > 7 ? value.substring(0, 6) + "..." : value;
      valueButton.setText(displayValue);
      dialog.hide();
    }

    private void showValueInputDialog() {
      dialog = new VisDialog("Edit Value");
      dialog.setFillParent(true);

      VisTable mainContent = new VisTable();

      final TextArea textArea = new TextArea(value, VisUI.getSkin());
      textArea.setPrefRows(5);
      ScrollPane scrollPane = new ScrollPane(textArea, VisUI.getSkin());
      scrollPane.setFadeScrollBars(false);

      // Pass saveValue() as onConfirm to VirtualKeyboard
      VirtualKeyboard keyboard = new VirtualKeyboard(textArea, () -> saveValue(textArea));

      VisTable leftPanel = new VisTable();
      leftPanel.add(scrollPane).expand().fill().growX().row();
      leftPanel.add(keyboard).fillX().padTop(10).row();

      ExpandableSideList sideList = new ExpandableSideList();
      ScrollPane sideScrollPane = new ScrollPane(sideList, VisUI.getSkin());
      sideScrollPane.setFadeScrollBars(false);
      sideScrollPane.setFlickScroll(true);
      String hints = ((UiStage)stage).getHints();
      String items = ((hints == "") ? "" : (hints + "\n")) + Gdx.files.internal("java/hints.java").readString();
      
      ExpandableSideList.Section section = null;
      for(String line : items.split("\n")){
        if(line.startsWith("- ")){
            section = sideList.addSection(line.replace("- ",""));
        } else {
            section.addItem(line, ()-> textArea.setText(textArea.getText() + line));
        }
      }

      // ExpandableSideList.Section booleanSection = sideList.addSection("boolean");
      // booleanSection.addItem("true", () -> textArea.setText("true"));
      // booleanSection.addItem("false", () -> textArea.setText("false"));

      // ExpandableSideList.Section mainFunctionsSection = sideList.addSection("main functions");
      // mainFunctionsSection.addItem("main()", () -> textArea.setText(textArea.getText() + "main"));
      // mainFunctionsSection.addItem("init()", () -> textArea.setText(textArea.getText() + "init"));

      mainContent.add(leftPanel).grow().padRight(10);
      mainContent.add(sideScrollPane).expandY().fillY().minWidth(70).growX();

      dialog.getContentTable().add(mainContent).expand().fill().row();

      VisTextButton saveButton = new VisTextButton("Save");
      saveButton.addListener(
          new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
              saveValue(textArea);
            }
          });

      VisTextButton cancelButton = new VisTextButton("Cancel");
      cancelButton.addListener(
          new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
              dialog.hide();
            }
          });

      dialog.getContentTable().add(saveButton).size(150, 50).pad(10);
      dialog.getContentTable().add(cancelButton).size(150, 50).pad(10);
      dialog.show(stage);
      dialog.toFront();
    }
  }

  public String getCode() {
    return this.code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  private boolean deletable = true;

  public boolean isDeletable() {
    return deletable;
  }

  public void setDeletable(boolean deletable) {
    this.deletable = deletable;
  }
}
