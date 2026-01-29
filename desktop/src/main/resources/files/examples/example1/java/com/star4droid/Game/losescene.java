package com.star4droid.Game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.Timer;
import com.star4droid.star2d.ElementDefs.*;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.template.Items.*;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ProjectAssetLoader;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import box2dLight.*;
import box2dLight.RayHandler;
// import .....script_here;

public class losescene extends StageImp {
  PlayerItem background, Text1, arrow;

  @Override
  public void onCreate() {

    BoxDef background_def = new BoxDef();
    background_def.type = "UI";
    background_def.Collider_Width = 725.13245f;
    background_def.height = 1504.6477f;
    background_def.Script = "background";
    background_def.image = "/color.png";
    background_def.Collider_Height = 1504.6477f;
    background_def.name = "background";
    background_def.width = 725.13245f;
    background_def.Tint = "#FFFFFF";

    background_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {
        openScene("scene1");
        finish();
      }

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {}

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {}

      @Override
      public void onBodyCreated(PlayerItem current) {}

      @Override
      public void onBodyUpdate(PlayerItem current) {}

      @Override
      public void onCollisionBegin(PlayerItem current, PlayerItem body2) {}

      @Override
      public void onCollisionEnd(PlayerItem current, PlayerItem body2) {}

      @Override
      public String getName() {
        return "background";
      }
    };
    background = (PlayerItem) (background_def.build(this));

    TextDef Text1_def = new TextDef();
    Text1_def.Script = "Text1";
    Text1_def.Font_Scale = 1.75f;
    Text1_def.Text = "You Lose!";
    Text1_def.x = 56.479733f;
    Text1_def.name = "Text1";
    Text1_def.width = 648.58673f;
    Text1_def.y = 713.12823f;
    Text1_def.z = 1f;
    Text1_def.height = 370.0865f;
    Text1_def.font = "/font1.s2df";

    Text1_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {}

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {}

      @Override
      public void onBodyCreated(PlayerItem current) {}

      @Override
      public void onBodyUpdate(PlayerItem current) {}

      @Override
      public void onCollisionBegin(PlayerItem current, PlayerItem body2) {}

      @Override
      public void onCollisionEnd(PlayerItem current, PlayerItem body2) {}

      @Override
      public String getName() {
        return "Text1";
      }
    };
    Text1 = (PlayerItem) (Text1_def.build(this));

    BoxDef arrow_def = new BoxDef();
    arrow_def.Gravity_Scale = 0.0f;
    arrow_def.Collider_Width = 143.14821f;
    arrow_def.height = 164.9118f;
    arrow_def.Script = "scene1/arrowScript";
    arrow_def.image = "/arrow.png";
    arrow_def.Collider_Height = 164.9118f;
    arrow_def.isSensor = true;
    arrow_def.x = 282.92676f;
    arrow_def.name = "arrow";
    arrow_def.width = 143.14821f;
    arrow_def.y = 562.3713f;
    arrow_def.z = 2f;
    arrow_def.Tint = "#FFFFFF";

    arrow_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {}

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {}

      @Override
      public void onBodyCreated(PlayerItem current) {}

      @Override
      public void onBodyUpdate(PlayerItem current) {}

      @Override
      public void onCollisionBegin(PlayerItem current, PlayerItem body2) {}

      @Override
      public void onCollisionEnd(PlayerItem current, PlayerItem body2) {}

      @Override
      public String getName() {
        return "arrow";
      }
    };
    arrow = (PlayerItem) (arrow_def.build(this));

    arrow.setScript(
        new com.star4droid.Game.Scripts.scene1.arrowScript().setItem(arrow).setStage(this));

    background.getActor().setZIndex((int) (background_def.z));

    Text1.getActor().setZIndex((int) (Text1_def.z));

    arrow.getActor().setZIndex((int) (arrow_def.z));
  }

  @Override
  public void onPause() {}

  @Override
  public void onResume() {}

  @Override
  public void onDraw() {}

  @Override
  public void onCollisionBegin(PlayerItem body1, PlayerItem body2) {}

  @Override
  public void onCollisionEnd(PlayerItem body1, PlayerItem body2) {}

  // scripts goes here ...

}
