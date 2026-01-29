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

public class main extends StageImp {
  PlayerItem background,
      Car4,
      Box4,
      Car2,
      Car1,
      Car,
      BW4,
      BW5,
      BW2,
      Car5,
      BW1,
      FW5,
      BW,
      FW4,
      FW2,
      FW1,
      FW,
      txt,
      Box2,
      Box3,
      Custom1,
      clouds,
      back,
      front,
      coin8,
      coin7,
      coin6,
      coin5,
      coin4,
      coin3,
      coin2,
      coin1;
  WheelJoint bwj;
  WheelJoint bwj1;
  WheelJoint bwj2;
  WheelJoint bwj4;
  WheelJoint bwj5;
  WheelJoint fwj;
  WheelJoint fwj1;
  WheelJoint fwj2;
  WheelJoint fwj4;
  WheelJoint fwj5;

  @Override
  public void onCreate() {

    BoxDef background_def = new BoxDef();
    background_def.type = "UI";
    background_def.Collider_Width = 719.976f;
    background_def.Version = "1.0";
    background_def.height = 1548.6714f;
    background_def.Script = "background";
    background_def.image = "/blue_grass.png";
    background_def.Collider_Height = 1548.6714f;
    background_def.name = "background";
    background_def.width = 719.976f;
    background_def.y = -0.6713867f;
    background_def.Tint = "#FFFFFF";

    background_def.elementEvents = new ElementEvent() {
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
        return "background";
      }
    };
    background = (PlayerItem) (background_def.build(this));

    BoxDef Car4_def = new BoxDef();
    Car4_def.Collider_Width = 183.22594f;
    Car4_def.Version = "1.0";
    Car4_def.Visible = false;
    Car4_def.height = 100.22926f;
    Car4_def.Script = "Car";
    Car4_def.image = "/vehicles/v4.png";
    Car4_def.density = 0.1f;
    Car4_def.Collider_Height = 100.22926f;
    Car4_def.Active = false;
    Car4_def.x = 177.30434f;
    Car4_def.name = "Car4";
    Car4_def.width = 183.22594f;
    Car4_def.y = 459.23108f;
    Car4_def.z = 1f;
    Car4_def.Tint = "#FFFFFF";

    Car4_def.elementEvents = new ElementEvent() {
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
        return "Car4";
      }
    };
    Car4 = (PlayerItem) (Car4_def.build(this));

    Car4.setScript(
        new com.star4droid.Game.Scripts.main.CarScript().setItem(Car4).setStage(this));

    BoxDef Box4_def = new BoxDef();
    Box4_def.type = "STATIC";
    Box4_def.Collider_Width = 2800.0f;
    Box4_def.Version = "1.0";
    Box4_def.Script = "Box4";
    Box4_def.image = "/ground.png";
    Box4_def.Collider_Height = 50f;
    Box4_def.friction = 1.0f;
    Box4_def.x = 6.205269f;
    Box4_def.name = "Box4";
    Box4_def.width = 2800.0f;
    Box4_def.y = 389.91577f;
    Box4_def.z = 2f;
    Box4_def.Tint = "#FFFFFF";
    Box4_def.tileX = 1.038f;

    Box4_def.elementEvents = new ElementEvent() {
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
        return "Box4";
      }
    };
    Box4 = (PlayerItem) (Box4_def.build(this));

    BoxDef Car2_def = new BoxDef();
    Car2_def.Collider_Width = 263.57828f;
    Car2_def.Version = "1.0";
    Car2_def.Visible = false;
    Car2_def.height = 94.142f;
    Car2_def.Script = "Car";
    Car2_def.image = "/vehicles/v2.png";
    Car2_def.density = 0.1f;
    Car2_def.Collider_Height = 94.142f;
    Car2_def.Active = false;
    Car2_def.x = 177.30434f;
    Car2_def.name = "Car2";
    Car2_def.width = 263.57828f;
    Car2_def.y = 702.77527f;
    Car2_def.z = 3f;
    Car2_def.Tint = "#FFFFFF";

    Car2_def.elementEvents = new ElementEvent() {
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
        return "Car2";
      }
    };
    Car2 = (PlayerItem) (Car2_def.build(this));

    Car2.setScript(
        new com.star4droid.Game.Scripts.main.CarScript().setItem(Car2).setStage(this));

    BoxDef Car1_def = new BoxDef();
    Car1_def.Collider_Width = 263.57828f;
    Car1_def.Version = "1.0";
    Car1_def.Visible = false;
    Car1_def.height = 94.142f;
    Car1_def.Script = "Car";
    Car1_def.image = "/vehicles/v1.png";
    Car1_def.density = 0.1f;
    Car1_def.Collider_Height = 94.142f;
    Car1_def.Active = false;
    Car1_def.x = 177.30434f;
    Car1_def.name = "Car1";
    Car1_def.width = 263.57828f;
    Car1_def.y = 465.31836f;
    Car1_def.z = 4f;
    Car1_def.Tint = "#FFFFFF";
    Car1_def.Scale_X = -1.0f;

    Car1_def.elementEvents = new ElementEvent() {
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
        return "Car1";
      }
    };
    Car1 = (PlayerItem) (Car1_def.build(this));

    Car1.setScript(
        new com.star4droid.Game.Scripts.main.CarScript().setItem(Car1).setStage(this));

    BoxDef Car_def = new BoxDef();
    Car_def.Collider_Width = 263.57828f;
    Car_def.Version = "1.0";
    Car_def.Visible = false;
    Car_def.height = 94.142f;
    Car_def.Script = "Car";
    Car_def.image = "/vehicles/v3.png";
    Car_def.density = 0.1f;
    Car_def.Collider_Height = 94.142f;
    Car_def.Active = false;
    Car_def.x = 177.30434f;
    Car_def.name = "Car";
    Car_def.width = 263.57828f;
    Car_def.y = 465.31836f;
    Car_def.z = 5f;
    Car_def.Tint = "#FFFFFF";

    Car_def.elementEvents = new ElementEvent() {
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
        return "Car";
      }
    };
    Car = (PlayerItem) (Car_def.build(this));

    Car.setScript(new com.star4droid.Game.Scripts.main.CarScript().setItem(Car).setStage(this));

    CircleDef BW4_def = new CircleDef();
    BW4_def.Version = "1.0";
    BW4_def.Visible = false;
    BW4_def.radius = 25.729733f;
    BW4_def.Script = "Circle1";
    BW4_def.image = "/wheels/wheel1.png";
    BW4_def.friction = 1.0f;
    BW4_def.Active = false;
    BW4_def.Collider_Radius = 25.729733f;
    BW4_def.restitution = 0.0f;
    BW4_def.x = 183.83943f;
    BW4_def.name = "BW4";
    BW4_def.y = 440.75366f;
    BW4_def.z = 6f;
    BW4_def.Tint = "#FFFFFF";

    BW4_def.elementEvents = new ElementEvent() {
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
        return "BW4";
      }
    };
    BW4 = (PlayerItem) (BW4_def.build(this));

    CircleDef BW5_def = new CircleDef();
    BW5_def.Version = "1.0";
    BW5_def.Visible = false;
    BW5_def.radius = 18.5f;
    BW5_def.Script = "Circle1";
    BW5_def.image = "/wheels/wheel1.png";
    BW5_def.friction = 1.0f;
    BW5_def.Active = false;
    BW5_def.Collider_Radius = 18.5f;
    BW5_def.restitution = 0.0f;
    BW5_def.x = 203.82123f;
    BW5_def.name = "BW5";
    BW5_def.y = 545.5728f;
    BW5_def.z = 7f;
    BW5_def.Tint = "#FFFFFF";

    BW5_def.elementEvents = new ElementEvent() {
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
        return "BW5";
      }
    };
    BW5 = (PlayerItem) (BW5_def.build(this));

    CircleDef BW2_def = new CircleDef();
    BW2_def.Version = "1.0";
    BW2_def.Visible = false;
    BW2_def.radius = 25.729733f;
    BW2_def.Script = "Circle1";
    BW2_def.image = "/wheels/wheel1.png";
    BW2_def.friction = 1.0f;
    BW2_def.Active = false;
    BW2_def.Collider_Radius = 25.729733f;
    BW2_def.restitution = 0.0f;
    BW2_def.x = 204.85124f;
    BW2_def.name = "BW2";
    BW2_def.y = 677.51025f;
    BW2_def.z = 8f;
    BW2_def.Tint = "#FFFFFF";

    BW2_def.elementEvents = new ElementEvent() {
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
        return "BW2";
      }
    };
    BW2 = (PlayerItem) (BW2_def.build(this));

    BoxDef Car5_def = new BoxDef();
    Car5_def.Collider_Width = 190.83786f;
    Car5_def.Version = "1.0";
    Car5_def.Visible = false;
    Car5_def.height = 99.12422f;
    Car5_def.Script = "Car";
    Car5_def.image = "/vehicles/v5.png";
    Car5_def.density = 0.1f;
    Car5_def.Collider_Height = 99.12422f;
    Car5_def.Active = false;
    Car5_def.x = 185.54419f;
    Car5_def.name = "Car5";
    Car5_def.width = 190.83786f;
    Car5_def.y = 572.2842f;
    Car5_def.z = 9f;
    Car5_def.Tint = "#FFFFFF";

    Car5_def.elementEvents = new ElementEvent() {
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
        return "Car5";
      }
    };
    Car5 = (PlayerItem) (Car5_def.build(this));

    Car5.setScript(
        new com.star4droid.Game.Scripts.main.CarScript().setItem(Car5).setStage(this));

    CircleDef BW1_def = new CircleDef();
    BW1_def.Version = "1.0";
    BW1_def.Visible = false;
    BW1_def.radius = 25.729733f;
    BW1_def.Script = "Circle1";
    BW1_def.image = "/wheels/wheel1.png";
    BW1_def.friction = 1.0f;
    BW1_def.Active = false;
    BW1_def.Collider_Radius = 25.729733f;
    BW1_def.restitution = 0.0f;
    BW1_def.x = 202.9971f;
    BW1_def.name = "BW1";
    BW1_def.y = 433.54236f;
    BW1_def.z = 10f;
    BW1_def.Tint = "#FFFFFF";

    BW1_def.elementEvents = new ElementEvent() {
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
        return "BW1";
      }
    };
    BW1 = (PlayerItem) (BW1_def.build(this));

    CircleDef FW5_def = new CircleDef();
    FW5_def.Version = "1.0";
    FW5_def.Visible = false;
    FW5_def.radius = 18.519806f;
    FW5_def.Script = "Circle1";
    FW5_def.image = "/wheels/wheel1.png";
    FW5_def.friction = 1.0f;
    FW5_def.Active = false;
    FW5_def.Collider_Radius = 18.519806f;
    FW5_def.restitution = 0.0f;
    FW5_def.x = 312.92255f;
    FW5_def.name = "FW5";
    FW5_def.y = 542.8868f;
    FW5_def.z = 11f;
    FW5_def.Tint = "#FFFFFF";

    FW5_def.elementEvents = new ElementEvent() {
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
        return "FW5";
      }
    };
    FW5 = (PlayerItem) (FW5_def.build(this));

    CircleDef BW_def = new CircleDef();
    BW_def.Version = "1.0";
    BW_def.Visible = false;
    BW_def.radius = 25.729733f;
    BW_def.Script = "Circle1";
    BW_def.image = "/wheels/wheel1.png";
    BW_def.friction = 1.0f;
    BW_def.Collider_Radius = 25.729733f;
    BW_def.restitution = 0.0f;
    BW_def.x = 196.81729f;
    BW_def.name = "BW";
    BW_def.y = 440.75366f;
    BW_def.z = 12f;
    BW_def.Tint = "#FFFFFF";

    BW_def.elementEvents = new ElementEvent() {
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
        return "BW";
      }
    };
    BW = (PlayerItem) (BW_def.build(this));

    CircleDef FW4_def = new CircleDef();
    FW4_def.Version = "1.0";
    FW4_def.Visible = false;
    FW4_def.radius = 25.729733f;
    FW4_def.Script = "Circle1";
    FW4_def.image = "/wheels/wheel1.png";
    FW4_def.friction = 1.0f;
    FW4_def.Active = false;
    FW4_def.Collider_Radius = 25.729733f;
    FW4_def.restitution = 0.0f;
    FW4_def.x = 304.8703f;
    FW4_def.name = "FW4";
    FW4_def.y = 441.52686f;
    FW4_def.z = 13f;
    FW4_def.Tint = "#FFFFFF";

    FW4_def.elementEvents = new ElementEvent() {
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
        return "FW4";
      }
    };
    FW4 = (PlayerItem) (FW4_def.build(this));

    CircleDef FW2_def = new CircleDef();
    FW2_def.Version = "1.0";
    FW2_def.Visible = false;
    FW2_def.radius = 25.729733f;
    FW2_def.Script = "Circle1";
    FW2_def.image = "/wheels/wheel1.png";
    FW2_def.friction = 1.0f;
    FW2_def.Active = false;
    FW2_def.Collider_Radius = 25.729733f;
    FW2_def.restitution = 0.0f;
    FW2_def.x = 342.38058f;
    FW2_def.name = "FW2";
    FW2_def.y = 675.2754f;
    FW2_def.z = 14f;
    FW2_def.Tint = "#FFFFFF";

    FW2_def.elementEvents = new ElementEvent() {
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
        return "FW2";
      }
    };
    FW2 = (PlayerItem) (FW2_def.build(this));

    CircleDef FW1_def = new CircleDef();
    FW1_def.Version = "1.0";
    FW1_def.Visible = false;
    FW1_def.radius = 25.729733f;
    FW1_def.Script = "Circle1";
    FW1_def.image = "/wheels/wheel1.png";
    FW1_def.friction = 1.0f;
    FW1_def.Active = false;
    FW1_def.Collider_Radius = 25.729733f;
    FW1_def.restitution = 0.0f;
    FW1_def.x = 367.3062f;
    FW1_def.name = "FW1";
    FW1_def.y = 432.87415f;
    FW1_def.z = 15f;
    FW1_def.Tint = "#FFFFFF";

    FW1_def.elementEvents = new ElementEvent() {
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
        return "FW1";
      }
    };
    FW1 = (PlayerItem) (FW1_def.build(this));

    CircleDef FW_def = new CircleDef();
    FW_def.Version = "1.0";
    FW_def.Visible = false;
    FW_def.radius = 25.729733f;
    FW_def.Script = "Circle1";
    FW_def.image = "/wheels/wheel1.png";
    FW_def.friction = 1.0f;
    FW_def.Active = false;
    FW_def.Collider_Radius = 25.729733f;
    FW_def.restitution = 0.0f;
    FW_def.x = 368.13013f;
    FW_def.name = "FW";
    FW_def.y = 441.52686f;
    FW_def.z = 16f;
    FW_def.Tint = "#FFFFFF";

    FW_def.elementEvents = new ElementEvent() {
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
        return "FW";
      }
    };
    FW = (PlayerItem) (FW_def.build(this));

    TextDef txt_def = new TextDef();
    txt_def.Script = "txt";
    txt_def.Font_Scale = 5.0f;
    txt_def.Text = "coins : 0";
    txt_def.Version = "1.0";
    txt_def.name = "txt";
    txt_def.width = 495.31616f;
    txt_def.y = 1357.5875f;
    txt_def.z = 17f;
    txt_def.height = 190.41243f;

    txt_def.elementEvents = new ElementEvent() {
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
        return "txt";
      }
    };
    txt = (PlayerItem) (txt_def.build(this));

    BoxDef Box2_def = new BoxDef();
    Box2_def.type = "STATIC";
    Box2_def.Collider_Width = 50f;
    Box2_def.Version = "1.0";
    Box2_def.Script = "Box2";
    Box2_def.image = "/ground.png";
    Box2_def.Collider_Height = 50f;
    Box2_def.rotation = 160.86105800866983f;
    Box2_def.x = 500.10446f;
    Box2_def.name = "Box2";
    Box2_def.y = 391.25476f;
    Box2_def.z = 18f;
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
    Box3_def.type = "STATIC";
    Box3_def.Collider_Width = 50f;
    Box3_def.Version = "1.0";
    Box3_def.Script = "Box2";
    Box3_def.image = "/ground.png";
    Box3_def.Collider_Height = 50f;
    Box3_def.rotation = 22.46123698746362f;
    Box3_def.x = 836.4872f;
    Box3_def.name = "Box3";
    Box3_def.y = 390.9259f;
    Box3_def.z = 19f;
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

    CustomDef Custom1_def = new CustomDef();
    Custom1_def.type = "STATIC";
    Custom1_def.Points =
        "0.0025252525,0.119802654-0.09343434,0.17383134-0.21464646,0.19191921-0.28282827,0.16960305-0.3989899,0.13436693-0.59090906,0.16020674-0.6388889,0.1484614-0.7929293,0.087385535-0.85353535,0.05919665-0.92676765,0.06506932-1.0,0.107352614-0.05050505,0.9765093";
    Custom1_def.Version = "1.0";
    Custom1_def.height = 120.62603f;
    Custom1_def.Script = "Custom1";
    Custom1_def.image = "/others/SceneBG.png";
    Custom1_def.x = 539.3039f;
    Custom1_def.name = "Custom1";
    Custom1_def.width = 311.06378f;
    Custom1_def.y = 341.74432f;
    Custom1_def.z = 20f;
    Custom1_def.Tint = "#FFFFFF";

    Custom1_def.elementEvents = new ElementEvent() {
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
        return "Custom1";
      }
    };
    Custom1 = (PlayerItem) (Custom1_def.build(this));

    BoxDef clouds_def = new BoxDef();
    clouds_def.type = "STATIC";
    clouds_def.Collider_Width = 676.0719f;
    clouds_def.Version = "1.0";
    clouds_def.height = 434.0807f;
    clouds_def.Script = "clouds";
    clouds_def.image = "/others/Clouds.png";
    clouds_def.Collider_Height = 434.0807f;
    clouds_def.Active = false;
    clouds_def.x = 1.1100509f;
    clouds_def.name = "clouds";
    clouds_def.width = 676.0719f;
    clouds_def.y = 966.2818f;
    clouds_def.z = 21f;
    clouds_def.Tint = "#FFFFFF";

    clouds_def.elementEvents = new ElementEvent() {
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
        return "clouds";
      }
    };
    clouds = (PlayerItem) (clouds_def.build(this));

    BoxDef back_def = new BoxDef();
    back_def.type = "UI";
    back_def.Collider_Width = 134.36433f;
    back_def.Version = "1.0";
    back_def.height = 145.4649f;
    back_def.Script = "back";
    back_def.image = "/btn.png";
    back_def.Collider_Height = 145.4649f;
    back_def.rotation = 270.0f;
    back_def.x = 28.861483f;
    back_def.name = "back";
    back_def.width = 134.36433f;
    back_def.y = 584.4233f;
    back_def.z = 22f;
    back_def.Tint = "#FFFFFF";

    back_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        bwj.setMotorSpeed(80);
        bwj.enableMotor(true);
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        bwj.enableMotor(false);
      }

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
        return "back";
      }
    };
    back = (PlayerItem) (back_def.build(this));

    BoxDef front_def = new BoxDef();
    front_def.type = "UI";
    front_def.Collider_Width = 134.36433f;
    front_def.Version = "1.0";
    front_def.height = 145.4649f;
    front_def.Script = "front";
    front_def.image = "/btn.png";
    front_def.Collider_Height = 145.4649f;
    front_def.rotation = 90.0f;
    front_def.x = 533.93726f;
    front_def.name = "front";
    front_def.width = 134.36433f;
    front_def.y = 584.4233f;
    front_def.z = 23f;
    front_def.Tint = "#FFFFFF";

    front_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        bwj.enableMotor(true);
        bwj.setMotorSpeed(-80);
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        bwj.enableMotor(false);
      }

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
        return "front";
      }
    };
    front = (PlayerItem) (front_def.build(this));

    BoxDef coin8_def = new BoxDef();
    coin8_def.type = "STATIC";
    coin8_def.Collider_Width = 75.91301f;
    coin8_def.Version = "1.0";
    coin8_def.height = 67.568146f;
    coin8_def.Script = "coin1";
    coin8_def.image = "/others/Coin5.png";
    coin8_def.Collider_Height = 67.568146f;
    coin8_def.isSensor = true;
    coin8_def.x = 564.23206f;
    coin8_def.name = "coin8";
    coin8_def.width = 75.91301f;
    coin8_def.y = 448.34192f;
    coin8_def.z = 24f;
    coin8_def.Tint = "#FFFFFF";

    coin8_def.elementEvents = new ElementEvent() {
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
        return "coin8";
      }
    };
    coin8 = (PlayerItem) (coin8_def.build(this));

    BoxDef coin7_def = new BoxDef();
    coin7_def.type = "STATIC";
    coin7_def.Collider_Width = 75.91301f;
    coin7_def.Version = "1.0";
    coin7_def.height = 67.568146f;
    coin7_def.Script = "coin1";
    coin7_def.image = "/others/Coin5.png";
    coin7_def.Collider_Height = 67.568146f;
    coin7_def.isSensor = true;
    coin7_def.x = 1445.7921f;
    coin7_def.name = "coin7";
    coin7_def.width = 75.91301f;
    coin7_def.y = 442.74597f;
    coin7_def.z = 25f;
    coin7_def.Tint = "#FFFFFF";

    coin7_def.elementEvents = new ElementEvent() {
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
        return "coin7";
      }
    };
    coin7 = (PlayerItem) (coin7_def.build(this));

    BoxDef coin6_def = new BoxDef();
    coin6_def.type = "STATIC";
    coin6_def.Collider_Width = 75.91301f;
    coin6_def.Version = "1.0";
    coin6_def.height = 67.568146f;
    coin6_def.Script = "coin1";
    coin6_def.image = "/others/Coin5.png";
    coin6_def.Collider_Height = 67.568146f;
    coin6_def.isSensor = true;
    coin6_def.x = 1301.7496f;
    coin6_def.name = "coin6";
    coin6_def.width = 75.91301f;
    coin6_def.y = 442.74597f;
    coin6_def.z = 26f;
    coin6_def.Tint = "#FFFFFF";

    coin6_def.elementEvents = new ElementEvent() {
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
        return "coin6";
      }
    };
    coin6 = (PlayerItem) (coin6_def.build(this));

    BoxDef coin5_def = new BoxDef();
    coin5_def.type = "STATIC";
    coin5_def.Collider_Width = 75.91301f;
    coin5_def.Version = "1.0";
    coin5_def.height = 67.568146f;
    coin5_def.Script = "coin1";
    coin5_def.image = "/others/Coin5.png";
    coin5_def.Collider_Height = 67.568146f;
    coin5_def.isSensor = true;
    coin5_def.x = 1172.1495f;
    coin5_def.name = "coin5";
    coin5_def.width = 75.91301f;
    coin5_def.y = 442.74597f;
    coin5_def.z = 27f;
    coin5_def.Tint = "#FFFFFF";

    coin5_def.elementEvents = new ElementEvent() {
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
        return "coin5";
      }
    };
    coin5 = (PlayerItem) (coin5_def.build(this));

    BoxDef coin4_def = new BoxDef();
    coin4_def.type = "STATIC";
    coin4_def.Collider_Width = 75.91301f;
    coin4_def.Version = "1.0";
    coin4_def.height = 67.568146f;
    coin4_def.Script = "coin1";
    coin4_def.image = "/others/Coin5.png";
    coin4_def.Collider_Height = 67.568146f;
    coin4_def.isSensor = true;
    coin4_def.x = 1043.1292f;
    coin4_def.name = "coin4";
    coin4_def.width = 75.91301f;
    coin4_def.y = 442.74597f;
    coin4_def.z = 28f;
    coin4_def.Tint = "#FFFFFF";

    coin4_def.elementEvents = new ElementEvent() {
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
        return "coin4";
      }
    };
    coin4 = (PlayerItem) (coin4_def.build(this));

    BoxDef coin3_def = new BoxDef();
    coin3_def.type = "STATIC";
    coin3_def.Collider_Width = 75.91301f;
    coin3_def.Version = "1.0";
    coin3_def.height = 67.568146f;
    coin3_def.Script = "coin1";
    coin3_def.image = "/others/Coin5.png";
    coin3_def.Collider_Height = 67.568146f;
    coin3_def.isSensor = true;
    coin3_def.x = 916.2543f;
    coin3_def.name = "coin3";
    coin3_def.width = 75.91301f;
    coin3_def.y = 442.74597f;
    coin3_def.z = 29f;
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

    BoxDef coin2_def = new BoxDef();
    coin2_def.type = "STATIC";
    coin2_def.Collider_Width = 75.91301f;
    coin2_def.Version = "1.0";
    coin2_def.height = 67.568146f;
    coin2_def.Script = "coin1";
    coin2_def.image = "/others/Coin5.png";
    coin2_def.Collider_Height = 67.568146f;
    coin2_def.isSensor = true;
    coin2_def.x = 795.3418f;
    coin2_def.name = "coin2";
    coin2_def.width = 75.91301f;
    coin2_def.y = 465.68927f;
    coin2_def.z = 30f;
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

    BoxDef coin1_def = new BoxDef();
    coin1_def.type = "STATIC";
    coin1_def.Collider_Width = 75.91301f;
    coin1_def.Version = "1.0";
    coin1_def.height = 67.568146f;
    coin1_def.Script = "coin1";
    coin1_def.image = "/others/Coin5.png";
    coin1_def.Collider_Height = 67.568146f;
    coin1_def.isSensor = true;
    coin1_def.x = 676.84644f;
    coin1_def.name = "coin1";
    coin1_def.width = 75.91301f;
    coin1_def.y = 456.73602f;
    coin1_def.z = 31f;
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

    background.getActor().setZIndex((int) (background_def.z));

    Car4.getActor().setZIndex((int) (Car4_def.z));

    Box4.getActor().setZIndex((int) (Box4_def.z));

    Car2.getActor().setZIndex((int) (Car2_def.z));

    Car1.getActor().setZIndex((int) (Car1_def.z));

    Car.getActor().setZIndex((int) (Car_def.z));

    BW4.getActor().setZIndex((int) (BW4_def.z));

    BW5.getActor().setZIndex((int) (BW5_def.z));

    BW2.getActor().setZIndex((int) (BW2_def.z));

    Car5.getActor().setZIndex((int) (Car5_def.z));

    BW1.getActor().setZIndex((int) (BW1_def.z));

    FW5.getActor().setZIndex((int) (FW5_def.z));

    BW.getActor().setZIndex((int) (BW_def.z));

    FW4.getActor().setZIndex((int) (FW4_def.z));

    FW2.getActor().setZIndex((int) (FW2_def.z));

    FW1.getActor().setZIndex((int) (FW1_def.z));

    FW.getActor().setZIndex((int) (FW_def.z));

    txt.getActor().setZIndex((int) (txt_def.z));

    Box2.getActor().setZIndex((int) (Box2_def.z));

    Box3.getActor().setZIndex((int) (Box3_def.z));

    Custom1.getActor().setZIndex((int) (Custom1_def.z));

    clouds.getActor().setZIndex((int) (clouds_def.z));

    back.getActor().setZIndex((int) (back_def.z));

    front.getActor().setZIndex((int) (front_def.z));

    coin8.getActor().setZIndex((int) (coin8_def.z));

    coin7.getActor().setZIndex((int) (coin7_def.z));

    coin6.getActor().setZIndex((int) (coin6_def.z));

    coin5.getActor().setZIndex((int) (coin5_def.z));

    coin4.getActor().setZIndex((int) (coin4_def.z));

    coin3.getActor().setZIndex((int) (coin3_def.z));

    coin2.getActor().setZIndex((int) (coin2_def.z));

    coin1.getActor().setZIndex((int) (coin1_def.z));

    Car4.addChild(BW4);
    Car5.addChild(BW5);
    Car2.addChild(BW2);
    Car1.addChild(BW1);
    Car5.addChild(FW5);
    Car.addChild(BW);
    Car4.addChild(FW4);
    Car2.addChild(FW2);
    Car1.addChild(FW1);
    Car.addChild(FW);

    WheelJointDef bwj_Def = new WheelJointDef();
    bwj_Def.dampingRatio = 0.7f;

    bwj_Def.enableMotor = false;
    bwj_Def.frequencyHz = 10.0f;
    bwj_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    bwj_Def.maxMotorTorque = 9999.0f;
    bwj_Def.motorSpeed = -80.0f;

    bwj_Def.collideConnected = false;

    bwj_Def.initialize(
        Car.getBody(),
        BW.getBody(),
        BW.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    bwj = (WheelJoint) (this.world.createJoint(bwj_Def));
    addJoint("bwj", bwj);

    WheelJointDef bwj1_Def = new WheelJointDef();
    bwj1_Def.dampingRatio = 0.7f;

    bwj1_Def.enableMotor = false;
    bwj1_Def.frequencyHz = 10.0f;
    bwj1_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj1_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj1_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    bwj1_Def.maxMotorTorque = 9999.0f;
    bwj1_Def.motorSpeed = -80.0f;

    bwj1_Def.collideConnected = false;

    bwj1_Def.initialize(
        Car1.getBody(),
        BW1.getBody(),
        BW1.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    bwj1 = (WheelJoint) (this.world.createJoint(bwj1_Def));
    addJoint("bwj1", bwj1);

    WheelJointDef bwj2_Def = new WheelJointDef();
    bwj2_Def.dampingRatio = 0.7f;

    bwj2_Def.enableMotor = false;
    bwj2_Def.frequencyHz = 10.0f;
    bwj2_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj2_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj2_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    bwj2_Def.maxMotorTorque = 9999.0f;
    bwj2_Def.motorSpeed = -80.0f;

    bwj2_Def.collideConnected = false;

    bwj2_Def.initialize(
        Car2.getBody(),
        BW2.getBody(),
        BW2.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    bwj2 = (WheelJoint) (this.world.createJoint(bwj2_Def));
    addJoint("bwj2", bwj2);

    WheelJointDef bwj4_Def = new WheelJointDef();
    bwj4_Def.dampingRatio = 0.7f;

    bwj4_Def.enableMotor = false;
    bwj4_Def.frequencyHz = 10.0f;
    bwj4_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj4_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj4_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    bwj4_Def.maxMotorTorque = 9999.0f;
    bwj4_Def.motorSpeed = -80.0f;

    bwj4_Def.collideConnected = false;

    bwj4_Def.initialize(
        Car4.getBody(),
        BW4.getBody(),
        BW4.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    bwj4 = (WheelJoint) (this.world.createJoint(bwj4_Def));
    addJoint("bwj4", bwj4);

    WheelJointDef bwj5_Def = new WheelJointDef();
    bwj5_Def.dampingRatio = 0.7f;

    bwj5_Def.enableMotor = false;
    bwj5_Def.frequencyHz = 10.0f;
    bwj5_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj5_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    bwj5_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    bwj5_Def.maxMotorTorque = 9999.0f;
    bwj5_Def.motorSpeed = -80.0f;

    bwj5_Def.collideConnected = false;

    bwj5_Def.initialize(
        Car5.getBody(),
        BW5.getBody(),
        BW5.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    bwj5 = (WheelJoint) (this.world.createJoint(bwj5_Def));
    addJoint("bwj5", bwj5);

    WheelJointDef fwj_Def = new WheelJointDef();
    fwj_Def.dampingRatio = 0.7f;

    fwj_Def.enableMotor = false;
    fwj_Def.frequencyHz = 10.0f;
    fwj_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    fwj_Def.maxMotorTorque = 0.0f;
    fwj_Def.motorSpeed = 0.0f;

    fwj_Def.collideConnected = false;

    fwj_Def.initialize(
        Car.getBody(),
        FW.getBody(),
        FW.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    fwj = (WheelJoint) (this.world.createJoint(fwj_Def));
    addJoint("fwj", fwj);

    WheelJointDef fwj1_Def = new WheelJointDef();
    fwj1_Def.dampingRatio = 0.7f;

    fwj1_Def.enableMotor = false;
    fwj1_Def.frequencyHz = 10.0f;
    fwj1_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj1_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj1_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    fwj1_Def.maxMotorTorque = 0.0f;
    fwj1_Def.motorSpeed = 0.0f;

    fwj1_Def.collideConnected = false;

    fwj1_Def.initialize(
        Car1.getBody(),
        FW1.getBody(),
        FW1.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    fwj1 = (WheelJoint) (this.world.createJoint(fwj1_Def));
    addJoint("fwj1", fwj1);

    WheelJointDef fwj2_Def = new WheelJointDef();
    fwj2_Def.dampingRatio = 0.7f;

    fwj2_Def.enableMotor = false;
    fwj2_Def.frequencyHz = 10.0f;
    fwj2_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj2_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj2_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    fwj2_Def.maxMotorTorque = 0.0f;
    fwj2_Def.motorSpeed = 0.0f;

    fwj2_Def.collideConnected = false;

    fwj2_Def.initialize(
        Car2.getBody(),
        FW2.getBody(),
        FW2.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    fwj2 = (WheelJoint) (this.world.createJoint(fwj2_Def));
    addJoint("fwj2", fwj2);

    WheelJointDef fwj4_Def = new WheelJointDef();
    fwj4_Def.dampingRatio = 0.7f;

    fwj4_Def.enableMotor = false;
    fwj4_Def.frequencyHz = 10.0f;
    fwj4_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj4_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj4_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    fwj4_Def.maxMotorTorque = 0.0f;
    fwj4_Def.motorSpeed = 0.0f;

    fwj4_Def.collideConnected = false;

    fwj4_Def.initialize(
        Car4.getBody(),
        FW4.getBody(),
        FW4.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    fwj4 = (WheelJoint) (this.world.createJoint(fwj4_Def));
    addJoint("fwj4", fwj4);

    WheelJointDef fwj5_Def = new WheelJointDef();
    fwj5_Def.dampingRatio = 0.7f;

    fwj5_Def.enableMotor = false;
    fwj5_Def.frequencyHz = 10.0f;
    fwj5_Def.localAnchorA.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj5_Def.localAnchorB.set(new Vector2(0.0f * 0.25f, 0.0f * 0.25f));
    fwj5_Def.localAxisA.set(new Vector2(1.0f * 0.25f, 0.0f * 0.25f));
    fwj5_Def.maxMotorTorque = 0.0f;
    fwj5_Def.motorSpeed = 0.0f;

    fwj5_Def.collideConnected = false;

    fwj5_Def.initialize(
        Car5.getBody(),
        FW5.getBody(),
        FW5.getBody().getWorldCenter(),
        new Vector2(0f * 0.25f, 1f * 0.25f));
    fwj5 = (WheelJoint) (this.world.createJoint(fwj5_Def));
    addJoint("fwj5", fwj5);
    String car = getValue("car").replace("c", "");
    int cn = 3;
    if (!car.equals("")) {
      cn = toInt(car);
    }

    switch (cn) {
      case 1:
        Car = Car1;
        BW = BW1;
        FW = FW1;
        bwj = bwj1;
        break;
      case 2:
        Car = Car2;
        BW = BW2;
        FW = FW2;
        bwj = bwj2;
        break;
      case 4:
        Car = Car4;
        BW = BW4;
        FW = FW4;
        bwj = bwj4;
        break;
      case 5:
        Car = Car5;
        BW = BW5;
        FW = FW5;
        bwj = bwj5;
        break;
    }
    Car.getActor().setVisible(true);
    FW.getActor().setVisible(true);
    BW.getActor().setVisible(true);

    Car.getBody().setActive(true);
    FW.getBody().setActive(true);
    BW.getBody().setActive(true);
    String wheel = getValue("wheel");
    if (!wheel.equals("")) {
      setImage(BW, wheel);
      setImage(FW, wheel);
    }

    setScript(new com.star4droid.game.SceneScript.mainScript().setStage(this));
  }

  @Override
  public void onPause() {}

  @Override
  public void onResume() {}

  @Override
  public void onDraw() {
    setCameraXY(Car);
  }

  @Override
  public void onCollisionBegin(PlayerItem body1, PlayerItem body2) {}

  @Override
  public void onCollisionEnd(PlayerItem body1, PlayerItem body2) {}

  // scripts goes here ...

}
