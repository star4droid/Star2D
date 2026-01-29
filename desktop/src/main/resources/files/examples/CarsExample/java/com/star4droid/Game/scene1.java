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

public class scene1 extends StageImp {
  PlayerItem Box1,
      ground,
      txt,
      nextCar,
      c3,
      w6,
      w7,
      c2,
      c5,
      c4,
      w9,
      w10,
      w8,
      w5,
      c1,
      w4,
      w3,
      changeWheel,
      w1,
      w2,
      Text1;

  @Override
  public void onCreate() {

    BoxDef Box1_def = new BoxDef();
    Box1_def.type = "UI";
    Box1_def.Collider_Width = 189.34105f;
    Box1_def.Version = "1.0";
    Box1_def.height = 98.09115f;
    Box1_def.Script = "Box1";
    Box1_def.image = "/blue_grass.png";
    Box1_def.Collider_Height = 98.09115f;
    Box1_def.x = 230.59073f;
    Box1_def.name = "Box1";
    Box1_def.width = 189.34105f;
    Box1_def.y = 165.0133f;
    Box1_def.Tint = "690000FF";

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

    BoxDef ground_def = new BoxDef();
    ground_def.type = "STATIC";
    ground_def.Collider_Width = 3850.0f;
    ground_def.Version = "1.0";
    ground_def.height = 48.613102f;
    ground_def.Script = "Box1";
    ground_def.image = "/ground.png";
    ground_def.Collider_Height = 48.613102f;
    ground_def.friction = 1.0f;
    ground_def.restitution = 0.0f;
    ground_def.x = 1.3868829f;
    ground_def.name = "ground";
    ground_def.width = 3850.0f;
    ground_def.y = 404.18945f;
    ground_def.z = 1.0f;
    ground_def.Tint = "#FFFFFF";

    ground_def.elementEvents = new ElementEvent() {
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
        return "ground";
      }
    };
    ground = (PlayerItem) (ground_def.build(this));

    TextDef txt_def = new TextDef();
    txt_def.Script = "txt";
    txt_def.Font_Scale = 3.0f;
    txt_def.Text = "Choose Your Car";
    txt_def.Version = "1.0";
    txt_def.x = 107.91519f;
    txt_def.name = "txt";
    txt_def.width = 520.3583f;
    txt_def.y = 890.456f;
    txt_def.z = 2.0f;
    txt_def.height = 94.89146f;
    txt_def.font = "/font1.s2df";

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

    BoxDef nextCar_def = new BoxDef();
    nextCar_def.type = "UI";
    nextCar_def.Collider_Width = 149.33115f;
    nextCar_def.Version = "1.0";
    nextCar_def.height = 150.49648f;
    nextCar_def.Script = "nextCar";
    nextCar_def.image = "/btn.png";
    nextCar_def.Collider_Height = 150.49648f;
    nextCar_def.rotation = 90.0f;
    nextCar_def.x = 522.58636f;
    nextCar_def.name = "nextCar";
    nextCar_def.width = 149.33115f;
    nextCar_def.y = 659.0037f;
    nextCar_def.z = 3.0f;
    nextCar_def.Tint = "#FFFFFF";

    nextCar_def.elementEvents = new ElementEvent() {
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
        return "nextCar";
      }
    };
    nextCar = (PlayerItem) (nextCar_def.build(this));

    nextCar.setScript(
        new com.star4droid.Game.Scripts.scene1.nextCarScript().setItem(nextCar).setStage(this));

    BoxDef c3_def = new BoxDef();
    c3_def.type = "STATIC";
    c3_def.Collider_Width = 352.56934f;
    c3_def.Version = "1.0";
    c3_def.height = 158.14265f;
    c3_def.Script = "c1";
    c3_def.image = "/vehicles/v3.png";
    c3_def.Collider_Height = 158.14265f;
    c3_def.x = 1746.1423f;
    c3_def.name = "c3";
    c3_def.width = 352.56934f;
    c3_def.y = 462.85468f;
    c3_def.z = 4.0f;
    c3_def.Tint = "#FFFFFF";

    c3_def.elementEvents = new ElementEvent() {
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
        return "c3";
      }
    };
    c3 = (PlayerItem) (c3_def.build(this));

    CircleDef w6_def = new CircleDef();
    w6_def.type = "STATIC";
    w6_def.Version = "1.0";
    w6_def.radius = 23.7985f;
    w6_def.Script = "w1";
    w6_def.image = "/wheels/wheel1.png";
    w6_def.Collider_Radius = 23.7985f;
    w6_def.x = 3311.6775f;
    w6_def.name = "w6";
    w6_def.y = 454.3228f;
    w6_def.z = 5.0f;
    w6_def.Tint = "#FFFFFF";

    w6_def.elementEvents = new ElementEvent() {
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
        return "w6";
      }
    };
    w6 = (PlayerItem) (w6_def.build(this));

    CircleDef w7_def = new CircleDef();
    w7_def.type = "STATIC";
    w7_def.Version = "1.0";
    w7_def.radius = 23.7985f;
    w7_def.Script = "w1";
    w7_def.image = "/wheels/wheel1.png";
    w7_def.Collider_Radius = 23.7985f;
    w7_def.x = 3454.7715f;
    w7_def.name = "w7";
    w7_def.y = 454.3228f;
    w7_def.z = 6.0f;
    w7_def.Tint = "#FFFFFF";

    w7_def.elementEvents = new ElementEvent() {
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
        return "w7";
      }
    };
    w7 = (PlayerItem) (w7_def.build(this));

    BoxDef c2_def = new BoxDef();
    c2_def.type = "STATIC";
    c2_def.Collider_Width = 352.56934f;
    c2_def.Version = "1.0";
    c2_def.height = 158.14265f;
    c2_def.Script = "c1";
    c2_def.image = "/vehicles/v2.png";
    c2_def.Collider_Height = 158.14265f;
    c2_def.x = 942.14233f;
    c2_def.name = "c2";
    c2_def.width = 352.56934f;
    c2_def.y = 462.85468f;
    c2_def.z = 7.0f;
    c2_def.Tint = "#FFFFFF";

    c2_def.elementEvents = new ElementEvent() {
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
        return "c2";
      }
    };
    c2 = (PlayerItem) (c2_def.build(this));

    BoxDef c5_def = new BoxDef();
    c5_def.type = "STATIC";
    c5_def.Collider_Width = 253.56934f;
    c5_def.Version = "1.0";
    c5_def.height = 174.64265f;
    c5_def.Script = "c1";
    c5_def.image = "/vehicles/v5.png";
    c5_def.Collider_Height = 174.64265f;
    c5_def.x = 3286.6423f;
    c5_def.name = "c5";
    c5_def.width = 253.56934f;
    c5_def.y = 483.85468f;
    c5_def.z = 8.0f;
    c5_def.Tint = "#FFFFFF";

    c5_def.elementEvents = new ElementEvent() {
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
        return "c5";
      }
    };
    c5 = (PlayerItem) (c5_def.build(this));

    BoxDef c4_def = new BoxDef();
    c4_def.type = "STATIC";
    c4_def.Collider_Width = 352.56934f;
    c4_def.Version = "1.0";
    c4_def.height = 158.14265f;
    c4_def.Script = "c1";
    c4_def.image = "/vehicles/v4.png";
    c4_def.Collider_Height = 158.14265f;
    c4_def.x = 2521.6423f;
    c4_def.name = "c4";
    c4_def.width = 352.56934f;
    c4_def.y = 462.85468f;
    c4_def.z = 9.0f;
    c4_def.Tint = "#FFFFFF";

    c4_def.elementEvents = new ElementEvent() {
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
        return "c4";
      }
    };
    c4 = (PlayerItem) (c4_def.build(this));

    CircleDef w9_def = new CircleDef();
    w9_def.type = "STATIC";
    w9_def.Version = "1.0";
    w9_def.radius = 33.449303f;
    w9_def.Script = "w1";
    w9_def.image = "/wheels/wheel1.png";
    w9_def.Collider_Radius = 33.449303f;
    w9_def.x = 1999.2029f;
    w9_def.name = "w9";
    w9_def.y = 453.6568f;
    w9_def.z = 10.0f;
    w9_def.Tint = "#FFFFFF";

    w9_def.elementEvents = new ElementEvent() {
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
        return "w9";
      }
    };
    w9 = (PlayerItem) (w9_def.build(this));

    CircleDef w10_def = new CircleDef();
    w10_def.type = "STATIC";
    w10_def.Version = "1.0";
    w10_def.radius = 32.118153f;
    w10_def.Script = "w1";
    w10_def.image = "/wheels/wheel1.png";
    w10_def.Collider_Radius = 32.118153f;
    w10_def.x = 1773.691f;
    w10_def.name = "w10";
    w10_def.y = 448.66507f;
    w10_def.z = 11.0f;
    w10_def.Tint = "#FFFFFF";

    w10_def.elementEvents = new ElementEvent() {
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
        return "w10";
      }
    };
    w10 = (PlayerItem) (w10_def.build(this));

    CircleDef w8_def = new CircleDef();
    w8_def.type = "STATIC";
    w8_def.Version = "1.0";
    w8_def.radius = 33.1165f;
    w8_def.Script = "w1";
    w8_def.image = "/wheels/wheel1.png";
    w8_def.Collider_Radius = 33.1165f;
    w8_def.x = 2542.1829f;
    w8_def.name = "w8";
    w8_def.y = 449.3311f;
    w8_def.z = 12.0f;
    w8_def.Tint = "#FFFFFF";

    w8_def.elementEvents = new ElementEvent() {
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
        return "w8";
      }
    };
    w8 = (PlayerItem) (w8_def.build(this));

    CircleDef w5_def = new CircleDef();
    w5_def.type = "STATIC";
    w5_def.Version = "1.0";
    w5_def.radius = 32.118137f;
    w5_def.Script = "w1";
    w5_def.image = "/wheels/wheel1.png";
    w5_def.Collider_Radius = 32.118137f;
    w5_def.x = 2777.8467f;
    w5_def.name = "w5";
    w5_def.y = 453.3238f;
    w5_def.z = 13.0f;
    w5_def.Tint = "#FFFFFF";

    w5_def.elementEvents = new ElementEvent() {
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
        return "w5";
      }
    };
    w5 = (PlayerItem) (w5_def.build(this));

    BoxDef c1_def = new BoxDef();
    c1_def.type = "STATIC";
    c1_def.Collider_Width = 352.56934f;
    c1_def.Version = "1.0";
    c1_def.height = 158.14265f;
    c1_def.Script = "c1";
    c1_def.image = "/vehicles/v1.png";
    c1_def.Collider_Height = 158.14265f;
    c1_def.x = 151.85992f;
    c1_def.name = "c1";
    c1_def.width = 352.56934f;
    c1_def.y = 476.39697f;
    c1_def.z = 14.0f;
    c1_def.Tint = "#FFFFFF";
    c1_def.Scale_X = -1.0f;

    c1_def.elementEvents = new ElementEvent() {
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
        return "c1";
      }
    };
    c1 = (PlayerItem) (c1_def.build(this));

    CircleDef w4_def = new CircleDef();
    w4_def.type = "STATIC";
    w4_def.Version = "1.0";
    w4_def.radius = 23.7985f;
    w4_def.Script = "w1";
    w4_def.image = "/wheels/wheel1.png";
    w4_def.Collider_Radius = 23.7985f;
    w4_def.x = 992.83673f;
    w4_def.name = "w4";
    w4_def.y = 455.3211f;
    w4_def.z = 15.0f;
    w4_def.Tint = "#FFFFFF";

    w4_def.elementEvents = new ElementEvent() {
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
        return "w4";
      }
    };
    w4 = (PlayerItem) (w4_def.build(this));

    CircleDef w3_def = new CircleDef();
    w3_def.type = "STATIC";
    w3_def.Version = "1.0";
    w3_def.radius = 23.7985f;
    w3_def.Script = "w1";
    w3_def.image = "/wheels/wheel1.png";
    w3_def.Collider_Radius = 23.7985f;
    w3_def.x = 1167.465f;
    w3_def.name = "w3";
    w3_def.y = 455.3211f;
    w3_def.z = 16.0f;
    w3_def.Tint = "#FFFFFF";

    w3_def.elementEvents = new ElementEvent() {
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
        return "w3";
      }
    };
    w3 = (PlayerItem) (w3_def.build(this));

    BoxDef changeWheel_def = new BoxDef();
    changeWheel_def.type = "UI";
    changeWheel_def.Collider_Width = 68.799355f;
    changeWheel_def.Version = "1.0";
    changeWheel_def.height = 71.11511f;
    changeWheel_def.Script = "changeWheel";
    changeWheel_def.image = "/btn.png";
    changeWheel_def.Collider_Height = 71.11511f;
    changeWheel_def.rotation = 90.0f;
    changeWheel_def.x = 538.6927f;
    changeWheel_def.name = "changeWheel";
    changeWheel_def.width = 68.799355f;
    changeWheel_def.y = 463.42664f;
    changeWheel_def.z = 17.0f;
    changeWheel_def.Tint = "E08989FF";

    changeWheel_def.elementEvents = new ElementEvent() {
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
        return "changeWheel";
      }
    };
    changeWheel = (PlayerItem) (changeWheel_def.build(this));

    changeWheel.setScript(new com.star4droid.Game.Scripts.scene1.changeWheelScript()
        .setItem(changeWheel)
        .setStage(this));

    CircleDef w1_def = new CircleDef();
    w1_def.type = "STATIC";
    w1_def.Version = "1.0";
    w1_def.radius = 23.7985f;
    w1_def.Script = "w1";
    w1_def.image = "/wheels/wheel1.png";
    w1_def.Collider_Radius = 23.7985f;
    w1_def.x = 416.842f;
    w1_def.name = "w1";
    w1_def.y = 455.3211f;
    w1_def.z = 18.0f;
    w1_def.Tint = "#FFFFFF";

    w1_def.elementEvents = new ElementEvent() {
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
        return "w1";
      }
    };
    w1 = (PlayerItem) (w1_def.build(this));

    CircleDef w2_def = new CircleDef();
    w2_def.type = "STATIC";
    w2_def.Version = "1.0";
    w2_def.radius = 23.7985f;
    w2_def.Script = "w1";
    w2_def.image = "/wheels/wheel1.png";
    w2_def.Collider_Radius = 23.7985f;
    w2_def.x = 198.89311f;
    w2_def.name = "w2";
    w2_def.y = 455.3211f;
    w2_def.z = 19.0f;
    w2_def.Tint = "#FFFFFF";

    w2_def.elementEvents = new ElementEvent() {
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
        return "w2";
      }
    };
    w2 = (PlayerItem) (w2_def.build(this));

    TextDef Text1_def = new TextDef();
    Text1_def.Script = "Text1";
    Text1_def.Text_Color = "FFFFFFFF";
    Text1_def.Font_Scale = 3.0f;
    Text1_def.Text = "Start!";
    Text1_def.Version = "1.0";
    Text1_def.x = 233.12878f;
    Text1_def.name = "Text1";
    Text1_def.width = 188.8594f;
    Text1_def.y = 167.13025f;
    Text1_def.z = 20.0f;
    Text1_def.height = 95.93137f;

    Text1_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {
        openScene("main");
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
        return "Text1";
      }
    };
    Text1 = (PlayerItem) (Text1_def.build(this));

    Box1.getActor().setZIndex((int) (Box1_def.z));

    ground.getActor().setZIndex((int) (ground_def.z));

    txt.getActor().setZIndex((int) (txt_def.z));

    nextCar.getActor().setZIndex((int) (nextCar_def.z));

    c3.getActor().setZIndex((int) (c3_def.z));

    w6.getActor().setZIndex((int) (w6_def.z));

    w7.getActor().setZIndex((int) (w7_def.z));

    c2.getActor().setZIndex((int) (c2_def.z));

    c5.getActor().setZIndex((int) (c5_def.z));

    c4.getActor().setZIndex((int) (c4_def.z));

    w9.getActor().setZIndex((int) (w9_def.z));

    w10.getActor().setZIndex((int) (w10_def.z));

    w8.getActor().setZIndex((int) (w8_def.z));

    w5.getActor().setZIndex((int) (w5_def.z));

    c1.getActor().setZIndex((int) (c1_def.z));

    w4.getActor().setZIndex((int) (w4_def.z));

    w3.getActor().setZIndex((int) (w3_def.z));

    changeWheel.getActor().setZIndex((int) (changeWheel_def.z));

    w1.getActor().setZIndex((int) (w1_def.z));

    w2.getActor().setZIndex((int) (w2_def.z));

    Text1.getActor().setZIndex((int) (Text1_def.z));

    setScript(new com.star4droid.game.SceneScript.scene1Script().setStage(this));
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
