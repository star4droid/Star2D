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

public class win extends StageImp {
  PlayerItem Box1, Box2, Box3, Text1, coin2, coin3, coin1;

  @Override
  public void onCreate() {

    BoxDef Box1_def = new BoxDef();
    Box1_def.type = "UI";
    Box1_def.Collider_Width = 1750.0f;
    Box1_def.Version = "1.0";
    Box1_def.height = 1077.0f;
    Box1_def.Script = "Box1";
    Box1_def.image = "/primary_Cartoon_Forest_BG_01.png";
    Box1_def.Collider_Height = 1077.0f;
    Box1_def.name = "Box1";
    Box1_def.width = 1750.0f;
    Box1_def.y = 3.0f;
    Box1_def.Tint = "#FFFFFF";

    Box1_def.elementEvents = new ElementEvent() {
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
        return "Box1";
      }
    };
    Box1 = (PlayerItem) (Box1_def.build(this));

    BoxDef Box2_def = new BoxDef();
    Box2_def.type = "UI";
    Box2_def.Collider_Width = 1750.0f;
    Box2_def.Version = "1.0";
    Box2_def.height = 1077.0f;
    Box2_def.Script = "Box1";
    Box2_def.image = "/primary_Cartoon_Forest_BG_01.png";
    Box2_def.Collider_Height = 1077.0f;
    Box2_def.x = 1751.4353f;
    Box2_def.name = "Box2";
    Box2_def.width = 1750.0f;
    Box2_def.y = 3.0f;
    Box2_def.z = 1.0f;
    Box2_def.Tint = "#FFFFFF";

    Box2_def.elementEvents = new ElementEvent() {
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
        return "Box2";
      }
    };
    Box2 = (PlayerItem) (Box2_def.build(this));

    BoxDef Box3_def = new BoxDef();
    Box3_def.type = "UI";
    Box3_def.Collider_Width = 602.4499f;
    Box3_def.Version = "1.0";
    Box3_def.height = 412.67096f;
    Box3_def.Script = "Box3";
    Box3_def.image = "/f.png";
    Box3_def.Collider_Height = 412.67096f;
    Box3_def.x = 752.04645f;
    Box3_def.name = "Box3";
    Box3_def.width = 602.4499f;
    Box3_def.y = 359.34015f;
    Box3_def.z = 2.0f;
    Box3_def.Tint = "#FFFFFF";

    Box3_def.elementEvents = new ElementEvent() {
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
        return "Box3";
      }
    };
    Box3 = (PlayerItem) (Box3_def.build(this));

    TextDef Text1_def = new TextDef();
    Text1_def.Script = "Text1";
    Text1_def.Font_Scale = 12.0f;
    Text1_def.Text = "You win!";
    Text1_def.Version = "1.0";
    Text1_def.x = 649.71674f;
    Text1_def.name = "Text1";
    Text1_def.width = 801.1141f;
    Text1_def.y = 777.4974f;
    Text1_def.z = 3.0f;
    Text1_def.height = 292.85284f;
    Text1_def.font = "/files/font1.s2df";

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

    BoxDef coin2_def = new BoxDef();
    coin2_def.type = "UI";
    coin2_def.Collider_Width = 119.15677f;
    coin2_def.Version = "1.0";
    coin2_def.height = 127.19824f;
    coin2_def.Script = "Box4";
    coin2_def.image = "/c.png";
    coin2_def.Collider_Height = 127.19824f;
    coin2_def.x = 818.3189f;
    coin2_def.name = "coin2";
    coin2_def.width = 119.15677f;
    coin2_def.y = 573.2435f;
    coin2_def.z = 4.0f;
    coin2_def.Tint = "#FFFFFF";

    coin2_def.elementEvents = new ElementEvent() {
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
        return "coin2";
      }
    };
    coin2 = (PlayerItem) (coin2_def.build(this));

    BoxDef coin3_def = new BoxDef();
    coin3_def.type = "UI";
    coin3_def.Collider_Width = 119.15677f;
    coin3_def.Version = "1.0";
    coin3_def.height = 127.19824f;
    coin3_def.Script = "Box4";
    coin3_def.image = "/c.png";
    coin3_def.Collider_Height = 127.19824f;
    coin3_def.x = 1171.5774f;
    coin3_def.name = "coin3";
    coin3_def.width = 119.15677f;
    coin3_def.y = 573.2435f;
    coin3_def.z = 5.0f;
    coin3_def.Tint = "#FFFFFF";

    coin3_def.elementEvents = new ElementEvent() {
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
        return "coin3";
      }
    };
    coin3 = (PlayerItem) (coin3_def.build(this));

    BoxDef coin1_def = new BoxDef();
    coin1_def.type = "UI";
    coin1_def.Collider_Width = 119.15677f;
    coin1_def.Version = "1.0";
    coin1_def.height = 127.19824f;
    coin1_def.Script = "Box4";
    coin1_def.image = "/c.png";
    coin1_def.Collider_Height = 127.19824f;
    coin1_def.x = 993.8954f;
    coin1_def.name = "coin1";
    coin1_def.width = 119.15677f;
    coin1_def.y = 573.2435f;
    coin1_def.z = 5.0f;
    coin1_def.Tint = "#FFFFFF";

    coin1_def.elementEvents = new ElementEvent() {
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
        return "coin1";
      }
    };
    coin1 = (PlayerItem) (coin1_def.build(this));

    Box1.getActor().setZIndex((int) (Box1_def.z));

    Box2.getActor().setZIndex((int) (Box2_def.z));

    Box3.getActor().setZIndex((int) (Box3_def.z));

    Text1.getActor().setZIndex((int) (Text1_def.z));

    coin2.getActor().setZIndex((int) (coin2_def.z));

    coin3.getActor().setZIndex((int) (coin3_def.z));

    coin1.getActor().setZIndex((int) (coin1_def.z));
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
