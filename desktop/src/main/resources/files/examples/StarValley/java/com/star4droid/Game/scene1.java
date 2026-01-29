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
  PlayerItem background,
      road2,
      Text3,
      Text4,
      getHeartText,
      Text2,
      Text1,
      road3,
      road1,
      road,
      player,
      wall4,
      wall3,
      wall1,
      wall5,
      wall,
      house2,
      car,
      house4,
      house3,
      house6,
      Particle1,
      house5,
      house1,
      character,
      Joystick1,
      text,
      heart2,
      heart1,
      heart,
      fan,
      gainHeart,
      Welcome,
      light,
      Progress1,
      compass,
      distance,
      where;

  Light Light1;

  @Override
  public void onCreate() {

    BoxDef background_def = new BoxDef();
    background_def.type = "UI";
    background_def.Collider_Width = 719.8983f;
    background_def.height = 1505.3864f;
    background_def.Script = "background";
    background_def.image = "/yellow_grass.png";
    background_def.Collider_Height = 1505.3864f;
    background_def.name = "background";
    background_def.width = 719.8983f;
    background_def.Tint = "#FFFFFF";
    background_def.tileX = 25.0f;
    background_def.tileY = 25.0f;

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

    background.setScript(new com.star4droid.Game.Scripts.scene1.backgroundScript()
        .setItem(background)
        .setStage(this));

    BoxDef road2_def = new BoxDef();
    road2_def.type = "STATIC";
    road2_def.Collider_Width = 200.0f;
    road2_def.height = 180.0f;
    road2_def.Script = "road";
    road2_def.image = "/roads/road4.png";
    road2_def.Collider_Height = 180.0f;
    road2_def.rotation = 90.29609769496658f;
    road2_def.Active = false;
    road2_def.x = 357.96667f;
    road2_def.name = "road2";
    road2_def.width = 200.0f;
    road2_def.y = 1251.6082f;
    road2_def.z = 1.0f;
    road2_def.Tint = "#FFFFFF";

    road2_def.elementEvents = new ElementEvent() {
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
        return "road2";
      }
    };
    road2 = (PlayerItem) (road2_def.build(this));

    road2.setScript(
        new com.star4droid.Game.Scripts.scene1.roadScript().setItem(road2).setStage(this));

    TextDef Text3_def = new TextDef();
    Text3_def.Script = "Text1";
    Text3_def.Font_Scale = 3.0f;
    Text3_def.Text = "This fan rotate from OnBodyUpdate using the Visual Scripting System ";
    Text3_def.type = "STATIC";
    Text3_def.x = 916.63495f;
    Text3_def.name = "Text3";
    Text3_def.width = 490.45453f;
    Text3_def.y = 751.18964f;
    Text3_def.z = 2.0f;
    Text3_def.height = 364.16968f;

    Text3_def.elementEvents = new ElementEvent() {
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
        return "Text3";
      }
    };
    Text3 = (PlayerItem) (Text3_def.build(this));

    TextDef Text4_def = new TextDef();
    Text4_def.Script = "Text1";
    Text4_def.Font_Scale = 3.0f;
    Text4_def.Text = "Day/Night System using GdxTime java file (com.star4droid.Game.GdxTime)";
    Text4_def.type = "STATIC";
    Text4_def.x = -788.5659f;
    Text4_def.name = "Text4";
    Text4_def.width = 762.91626f;
    Text4_def.y = 149.1193f;
    Text4_def.z = 3.0f;
    Text4_def.height = 291.07068f;

    Text4_def.elementEvents = new ElementEvent() {
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
        return "Text4";
      }
    };
    Text4 = (PlayerItem) (Text4_def.build(this));

    TextDef getHeartText_def = new TextDef();
    getHeartText_def.Script = "Text1";
    getHeartText_def.Font_Scale = 3.0f;
    getHeartText_def.Text = "Get This Heart :)";
    getHeartText_def.type = "STATIC";
    getHeartText_def.x = 275.66174f;
    getHeartText_def.name = "getHeartText";
    getHeartText_def.width = 433.30447f;
    getHeartText_def.y = 784.25183f;
    getHeartText_def.z = 4.0f;
    getHeartText_def.height = 78.41924f;

    getHeartText_def.elementEvents = new ElementEvent() {
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
        return "getHeartText";
      }
    };
    getHeartText = (PlayerItem) (getHeartText_def.build(this));

    TextDef Text2_def = new TextDef();
    Text2_def.Script = "Text1";
    Text2_def.Font_Scale = 3.0f;
    Text2_def.Text = "Open house script by clicking on \"Body Script\" to see what is going on..";
    Text2_def.type = "STATIC";
    Text2_def.x = 661.4524f;
    Text2_def.name = "Text2";
    Text2_def.width = 557.15894f;
    Text2_def.y = -144.60553f;
    Text2_def.z = 5.0f;
    Text2_def.height = 239.8862f;

    Text2_def.elementEvents = new ElementEvent() {
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
        return "Text2";
      }
    };
    Text2 = (PlayerItem) (Text2_def.build(this));

    TextDef Text1_def = new TextDef();
    Text1_def.Script = "Text1";
    Text1_def.Font_Scale = 3.0f;
    Text1_def.Text = "Under Construction";
    Text1_def.type = "STATIC";
    Text1_def.x = -235.67157f;
    Text1_def.name = "Text1";
    Text1_def.width = 433.30447f;
    Text1_def.y = 1503.4445f;
    Text1_def.z = 6.0f;
    Text1_def.height = 78.41924f;

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

    BoxDef road3_def = new BoxDef();
    road3_def.type = "STATIC";
    road3_def.Collider_Width = 174.39789f;
    road3_def.height = 133.81668f;
    road3_def.Script = "road";
    road3_def.image = "/roads/road2.png";
    road3_def.Collider_Height = 133.81668f;
    road3_def.rotation = 359.79900535220247f;
    road3_def.Active = false;
    road3_def.x = -174.28299f;
    road3_def.name = "road3";
    road3_def.width = 174.39789f;
    road3_def.y = 1363.0f;
    road3_def.z = 7.0f;
    road3_def.Tint = "#FFFFFF";

    road3_def.elementEvents = new ElementEvent() {
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
        return "road3";
      }
    };
    road3 = (PlayerItem) (road3_def.build(this));

    road3.setScript(
        new com.star4droid.Game.Scripts.scene1.roadScript().setItem(road3).setStage(this));

    BoxDef road1_def = new BoxDef();
    road1_def.type = "STATIC";
    road1_def.Collider_Width = 174.39789f;
    road1_def.height = 133.81668f;
    road1_def.Script = "road";
    road1_def.image = "/roads/road2.png";
    road1_def.Collider_Height = 133.81668f;
    road1_def.rotation = 181.2606527276181f;
    road1_def.Active = false;
    road1_def.x = 368.31723f;
    road1_def.name = "road1";
    road1_def.width = 174.39789f;
    road1_def.y = 1363.0f;
    road1_def.z = 8.0f;
    road1_def.Tint = "#FFFFFF";

    road1_def.elementEvents = new ElementEvent() {
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
        return "road1";
      }
    };
    road1 = (PlayerItem) (road1_def.build(this));

    road1.setScript(
        new com.star4droid.Game.Scripts.scene1.roadScript().setItem(road1).setStage(this));

    BoxDef road_def = new BoxDef();
    road_def.type = "STATIC";
    road_def.Collider_Width = 415.5543f;
    road_def.height = 130.87427f;
    road_def.Script = "road";
    road_def.image = "/roads/road4.png";
    road_def.Collider_Height = 130.87427f;
    road_def.Active = false;
    road_def.x = -6.8315434f;
    road_def.name = "road";
    road_def.width = 415.5543f;
    road_def.y = 1363.0885f;
    road_def.z = 9.0f;
    road_def.Tint = "#FFFFFF";

    road_def.elementEvents = new ElementEvent() {
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
        return "road";
      }
    };
    road = (PlayerItem) (road_def.build(this));

    road.setScript(
        new com.star4droid.Game.Scripts.scene1.roadScript().setItem(road).setStage(this));

    BoxDef player_def = new BoxDef();
    player_def.Gravity_Scale = 0.0f;
    player_def.Collider_Width = 50.0f;
    player_def.height = 35.0f;
    player_def.Script = "player";
    player_def.image = "/Vehicles/car-blue-right.png";
    player_def.Collider_Height = 35.0f;
    player_def.x = 60.717503f;
    player_def.name = "player";
    player_def.y = 343.93973f;
    player_def.z = 10.0f;
    player_def.Tint = "#FFFFFF";
    player_def.Fixed_Rotation = true;

    player_def.elementEvents = new ElementEvent() {
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
        return "player";
      }
    };
    player = (PlayerItem) (player_def.build(this));

    player.setScript(
        new com.star4droid.Game.Scripts.scene1.playerScript().setItem(player).setStage(this));

    BoxDef wall4_def = new BoxDef();
    wall4_def.type = "STATIC";
    wall4_def.Collider_Width = 177.64044f;
    wall4_def.Visible = false;
    wall4_def.height = 145.53317f;
    wall4_def.Script = "wall";
    wall4_def.image = "/color.png";
    wall4_def.Collider_Height = 145.53317f;
    wall4_def.restitution = 0.0f;
    wall4_def.x = -167.09407f;
    wall4_def.name = "wall4";
    wall4_def.width = 177.64044f;
    wall4_def.y = 681.8973f;
    wall4_def.z = 11.0f;
    wall4_def.Tint = "#FFFFFF";

    wall4_def.elementEvents = new ElementEvent() {
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
        return "wall4";
      }
    };
    wall4 = (PlayerItem) (wall4_def.build(this));

    BoxDef wall3_def = new BoxDef();
    wall3_def.type = "STATIC";
    wall3_def.Collider_Width = 162.82349f;
    wall3_def.Visible = false;
    wall3_def.height = 158.27588f;
    wall3_def.Script = "wall";
    wall3_def.image = "/color.png";
    wall3_def.Collider_Height = 158.27588f;
    wall3_def.restitution = 0.0f;
    wall3_def.x = 193.77155f;
    wall3_def.name = "wall3";
    wall3_def.width = 162.82349f;
    wall3_def.y = 653.68195f;
    wall3_def.z = 12.0f;
    wall3_def.Tint = "#FFFFFF";

    wall3_def.elementEvents = new ElementEvent() {
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
        return "wall3";
      }
    };
    wall3 = (PlayerItem) (wall3_def.build(this));

    BoxDef wall1_def = new BoxDef();
    wall1_def.type = "STATIC";
    wall1_def.Collider_Width = 331.5306f;
    wall1_def.Visible = false;
    wall1_def.height = 168.17993f;
    wall1_def.Script = "wall";
    wall1_def.image = "/color.png";
    wall1_def.Collider_Height = 168.17993f;
    wall1_def.restitution = 0.0f;
    wall1_def.x = 488.2645f;
    wall1_def.name = "wall1";
    wall1_def.width = 331.5306f;
    wall1_def.y = 387.68942f;
    wall1_def.z = 13.0f;
    wall1_def.Tint = "#FFFFFF";

    wall1_def.elementEvents = new ElementEvent() {
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
        return "wall1";
      }
    };
    wall1 = (PlayerItem) (wall1_def.build(this));

    BoxDef wall5_def = new BoxDef();
    wall5_def.type = "STATIC";
    wall5_def.Collider_Width = 342.32135f;
    wall5_def.Visible = false;
    wall5_def.height = 231.1511f;
    wall5_def.Script = "wall";
    wall5_def.image = "/color.png";
    wall5_def.Collider_Height = 231.1511f;
    wall5_def.restitution = 0.0f;
    wall5_def.x = 388.61276f;
    wall5_def.name = "wall5";
    wall5_def.width = 342.32135f;
    wall5_def.y = 1042.3829f;
    wall5_def.z = 14.0f;
    wall5_def.Tint = "#FFFFFF";

    wall5_def.elementEvents = new ElementEvent() {
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
        return "wall5";
      }
    };
    wall5 = (PlayerItem) (wall5_def.build(this));

    BoxDef wall_def = new BoxDef();
    wall_def.type = "STATIC";
    wall_def.Collider_Width = 839.1089f;
    wall_def.Visible = false;
    wall_def.height = 237.98253f;
    wall_def.Script = "wall";
    wall_def.image = "/color.png";
    wall_def.Collider_Height = 237.98253f;
    wall_def.restitution = 0.0f;
    wall_def.x = -188.21837f;
    wall_def.name = "wall";
    wall_def.width = 839.1089f;
    wall_def.y = -130.81189f;
    wall_def.z = 15.0f;
    wall_def.Tint = "#FFFFFF";

    wall_def.elementEvents = new ElementEvent() {
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
        return "wall";
      }
    };
    wall = (PlayerItem) (wall_def.build(this));

    BoxDef house2_def = new BoxDef();
    house2_def.Gravity_Scale = 0.0f;
    house2_def.type = "STATIC";
    house2_def.Collider_Width = 474.69897f;
    house2_def.height = 301.02914f;
    house2_def.Script = "house1";
    house2_def.image = "/houses/house3.png";
    house2_def.Collider_Height = 301.02914f;
    house2_def.isSensor = true;
    house2_def.restitution = 0.0f;
    house2_def.x = -177.58844f;
    house2_def.name = "house2";
    house2_def.width = 474.69897f;
    house2_def.y = -204.82393f;
    house2_def.z = 16.0f;
    house2_def.Tint = "FFFFFFFF";

    house2_def.elementEvents = new ElementEvent() {
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
        return "house2";
      }
    };
    house2 = (PlayerItem) (house2_def.build(this));

    house2.setScript(
        new com.star4droid.Game.Scripts.scene1.house1Script().setItem(house2).setStage(this));

    BoxDef car_def = new BoxDef();
    car_def.Gravity_Scale = 0.0f;
    car_def.type = "STATIC";
    car_def.Collider_Width = 154.0f;
    car_def.height = 224.0f;
    car_def.Script = "car";
    car_def.image = "/Vehicles/car-black-front.png";
    car_def.Collider_Height = 224.0f;
    car_def.restitution = 0.0f;
    car_def.x = 294.7083f;
    car_def.name = "car";
    car_def.width = 154.0f;
    car_def.y = 253.49133f;
    car_def.z = 17.0f;
    car_def.Tint = "#FFFFFF";

    car_def.elementEvents = new ElementEvent() {
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
        return "car";
      }
    };
    car = (PlayerItem) (car_def.build(this));

    BoxDef house4_def = new BoxDef();
    house4_def.Gravity_Scale = 0.0f;
    house4_def.type = "STATIC";
    house4_def.Collider_Width = 141.85135f;
    house4_def.height = 147.38109f;
    house4_def.Script = "house1";
    house4_def.image = "/items/Fireplace_1.png";
    house4_def.Collider_Height = 147.38109f;
    house4_def.isSensor = true;
    house4_def.restitution = 0.0f;
    house4_def.x = 203.51483f;
    house4_def.name = "house4";
    house4_def.width = 141.85135f;
    house4_def.y = 658.801f;
    house4_def.z = 18.0f;
    house4_def.Tint = "#FFFFFF";

    house4_def.elementEvents = new ElementEvent() {
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
        return "house4";
      }
    };
    house4 = (PlayerItem) (house4_def.build(this));

    house4.setScript(
        new com.star4droid.Game.Scripts.scene1.house1Script().setItem(house4).setStage(this));

    BoxDef house3_def = new BoxDef();
    house3_def.Gravity_Scale = 0.0f;
    house3_def.type = "STATIC";
    house3_def.Collider_Width = 310.54962f;
    house3_def.height = 290.62463f;
    house3_def.Script = "house1";
    house3_def.image = "/houses/house5.png";
    house3_def.Collider_Height = 290.62463f;
    house3_def.isSensor = true;
    house3_def.restitution = 0.0f;
    house3_def.x = 498.6892f;
    house3_def.name = "house3";
    house3_def.width = 310.54962f;
    house3_def.y = 254.14668f;
    house3_def.z = 19.0f;
    house3_def.Tint = "6EFF00FF";

    house3_def.elementEvents = new ElementEvent() {
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
        return "house3";
      }
    };
    house3 = (PlayerItem) (house3_def.build(this));

    house3.setScript(
        new com.star4droid.Game.Scripts.scene1.house1Script().setItem(house3).setStage(this));

    BoxDef house6_def = new BoxDef();
    house6_def.Gravity_Scale = 0.0f;
    house6_def.type = "STATIC";
    house6_def.Collider_Width = 342.37518f;
    house6_def.height = 301.02914f;
    house6_def.Script = "house1";
    house6_def.image = "/houses/house1.png";
    house6_def.Collider_Height = 301.02914f;
    house6_def.isSensor = true;
    house6_def.restitution = 0.0f;
    house6_def.x = 385.809f;
    house6_def.name = "house6";
    house6_def.width = 342.37518f;
    house6_def.y = 964.79474f;
    house6_def.z = 20.0f;
    house6_def.Tint = "#FFFFFF";

    house6_def.elementEvents = new ElementEvent() {
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
        return "house6";
      }
    };
    house6 = (PlayerItem) (house6_def.build(this));

    house6.setScript(
        new com.star4droid.Game.Scripts.scene1.house1Script().setItem(house6).setStage(this));

    ParticleDef Particle1_def = new ParticleDef();
    Particle1_def.Script = "Particle1";
    Particle1_def.rotation = 90.0f;
    Particle1_def.type = "STATIC";
    Particle1_def.x = 713.02985f;
    Particle1_def.name = "Particle1";
    Particle1_def.y = 222.86484f;
    Particle1_def.z = 21.0f;
    Particle1_def.Scale_X = 0.8f;
    Particle1_def.Scale_Y = 0.5f;

    Particle1_def.elementEvents = new ElementEvent() {
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
        return "Particle1";
      }
    };
    Particle1 = (PlayerItem) (Particle1_def.build(this));

    BoxDef house5_def = new BoxDef();
    house5_def.Gravity_Scale = 0.0f;
    house5_def.type = "STATIC";
    house5_def.Collider_Width = 182.21657f;
    house5_def.height = 217.69464f;
    house5_def.Script = "house1";
    house5_def.image = "/items/Well_Hay_Stone.png";
    house5_def.Collider_Height = 217.69464f;
    house5_def.isSensor = true;
    house5_def.restitution = 0.0f;
    house5_def.x = -164.98003f;
    house5_def.name = "house5";
    house5_def.width = 182.21657f;
    house5_def.y = 606.7168f;
    house5_def.z = 22.0f;
    house5_def.Tint = "#FFFFFF";

    house5_def.elementEvents = new ElementEvent() {
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
        return "house5";
      }
    };
    house5 = (PlayerItem) (house5_def.build(this));

    house5.setScript(
        new com.star4droid.Game.Scripts.scene1.house1Script().setItem(house5).setStage(this));

    BoxDef house1_def = new BoxDef();
    house1_def.Gravity_Scale = 0.0f;
    house1_def.type = "STATIC";
    house1_def.Collider_Width = 342.37518f;
    house1_def.height = 301.02914f;
    house1_def.Script = "house1";
    house1_def.image = "/houses/house2.png";
    house1_def.Collider_Height = 301.02914f;
    house1_def.isSensor = true;
    house1_def.restitution = 0.0f;
    house1_def.x = 293.3598f;
    house1_def.name = "house1";
    house1_def.width = 342.37518f;
    house1_def.y = -203.87836f;
    house1_def.z = 23.0f;
    house1_def.Tint = "#FFFFFF";

    house1_def.elementEvents = new ElementEvent() {
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
        return "house1";
      }
    };
    house1 = (PlayerItem) (house1_def.build(this));

    house1.setScript(
        new com.star4droid.Game.Scripts.scene1.house1Script().setItem(house1).setStage(this));

    BoxDef character_def = new BoxDef();
    character_def.Gravity_Scale = 0.0f;
    character_def.Collider_Width = 176.99924f;
    character_def.height = 148.57979f;
    character_def.Script = "Box1";
    character_def.image = "/animations/idle-down/0.png";
    character_def.Collider_Height = 148.57979f;
    character_def.Active = false;
    character_def.x = 451.1574f;
    character_def.name = "character";
    character_def.width = 176.99924f;
    character_def.y = -0.88812375f;
    character_def.z = 24.0f;
    character_def.Tint = "#FFFFFF";

    character_def.elementEvents = new ElementEvent() {
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
        return "character";
      }
    };
    character = (PlayerItem) (character_def.build(this));

    JoyStickDef Joystick1_def = new JoyStickDef();
    Joystick1_def.Script = "Joystick1";
    Joystick1_def.x = 38.39519f;
    Joystick1_def.name = "Joystick1";
    Joystick1_def.Pad_Image = "/joystick background.png";
    Joystick1_def.width = 190.58595f;
    Joystick1_def.y = 1271.7869f;
    Joystick1_def.z = 25.0f;
    Joystick1_def.height = 173.79958f;

    Joystick1_def.elementEvents = new ElementEvent() {
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
        return "Joystick1";
      }
    };
    Joystick1 = (PlayerItem) (Joystick1_def.build(this));

    TextDef text_def = new TextDef();
    text_def.Script = "Text1";
    text_def.Text_Color = "FFFFFFFF";
    text_def.Font_Scale = 3.0f;
    text_def.Text = "Time : 00:00";
    text_def.x = 42.811054f;
    text_def.name = "text";
    text_def.width = 653.30914f;
    text_def.y = 149.81345f;
    text_def.z = 26.0f;
    text_def.height = 88.194595f;

    text_def.elementEvents = new ElementEvent() {
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
        return "text";
      }
    };
    text = (PlayerItem) (text_def.build(this));

    BoxDef heart2_def = new BoxDef();
    heart2_def.type = "UI";
    heart2_def.Collider_Width = 70.83362f;
    heart2_def.height = 72.13573f;
    heart2_def.Script = "heart";
    heart2_def.image = "/HUD/hudHeart_empty.png";
    heart2_def.Collider_Height = 72.13573f;
    heart2_def.x = 230.0f;
    heart2_def.name = "heart2";
    heart2_def.width = 70.83362f;
    heart2_def.y = 80.0f;
    heart2_def.z = 27.0f;
    heart2_def.Tint = "#FFFFFF";

    heart2_def.elementEvents = new ElementEvent() {
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
        return "heart2";
      }
    };
    heart2 = (PlayerItem) (heart2_def.build(this));

    BoxDef heart1_def = new BoxDef();
    heart1_def.type = "UI";
    heart1_def.Collider_Width = 70.83362f;
    heart1_def.height = 72.13573f;
    heart1_def.Script = "heart";
    heart1_def.image = "/HUD/hudHeart_full.png";
    heart1_def.Collider_Height = 72.13573f;
    heart1_def.x = 140.0f;
    heart1_def.name = "heart1";
    heart1_def.width = 70.83362f;
    heart1_def.y = 80.0f;
    heart1_def.z = 28.0f;
    heart1_def.Tint = "#FFFFFF";

    heart1_def.elementEvents = new ElementEvent() {
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
        return "heart1";
      }
    };
    heart1 = (PlayerItem) (heart1_def.build(this));

    BoxDef heart_def = new BoxDef();
    heart_def.type = "UI";
    heart_def.Collider_Width = 70.83362f;
    heart_def.height = 70.0f;
    heart_def.Script = "heart";
    heart_def.image = "/HUD/hudHeart_full.png";
    heart_def.Collider_Height = 70.0f;
    heart_def.x = 50.0f;
    heart_def.name = "heart";
    heart_def.width = 70.83362f;
    heart_def.y = 80.0f;
    heart_def.z = 29.0f;
    heart_def.Tint = "#FFFFFF";

    heart_def.elementEvents = new ElementEvent() {
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
        return "heart";
      }
    };
    heart = (PlayerItem) (heart_def.build(this));

    BoxDef fan_def = new BoxDef();
    fan_def.Gravity_Scale = 0.0f;
    fan_def.Collider_Width = 90.02748f;
    fan_def.height = 82.522385f;
    fan_def.Script = "fan";
    fan_def.image = "/fan.png";
    fan_def.density = 500.0f;
    fan_def.Collider_Height = 82.522385f;
    fan_def.x = 791.79333f;
    fan_def.name = "fan";
    fan_def.width = 90.02748f;
    fan_def.y = 765.5257f;
    fan_def.z = 30.0f;
    fan_def.Tint = "0A5400FF";

    fan_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {}

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {}

      @Override
      public void onBodyCreated(PlayerItem current) {}

      @Override
      public void onBodyUpdate(PlayerItem current) {
        fan.getBody().setTransform((float) (fan.getBodyX()), (float) (fan.getBodyY()), (float)
            (fan.getBody().getAngle() + 5));
      }

      @Override
      public void onCollisionBegin(PlayerItem current, PlayerItem body2) {}

      @Override
      public void onCollisionEnd(PlayerItem current, PlayerItem body2) {}

      @Override
      public String getName() {
        return "fan";
      }
    };
    fan = (PlayerItem) (fan_def.build(this));

    fan.setScript(
        new com.star4droid.Game.Scripts.scene1.fanScript().setItem(fan).setStage(this));

    BoxDef gainHeart_def = new BoxDef();
    gainHeart_def.Gravity_Scale = 0.0f;
    gainHeart_def.Collider_Width = 50f;
    gainHeart_def.Script = "gainHeart";
    gainHeart_def.image = "/HUD/hudHeart_full.png";
    gainHeart_def.Collider_Height = 50f;
    gainHeart_def.x = 426.72687f;
    gainHeart_def.name = "gainHeart";
    gainHeart_def.y = 730.52325f;
    gainHeart_def.z = 32.0f;
    gainHeart_def.Tint = "FFFFFFFF";

    gainHeart_def.elementEvents = new ElementEvent() {
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
      public void onCollisionBegin(PlayerItem current, PlayerItem body2) {
        if (body2.getName().equals("player")) {
          gainHeart.destroy();
          setImage(heart2, "/HUD/hudHeart_full.png");
          getHeartText.destroy();
          startSound("drop1.ogg");

        } else {

        }
      }

      @Override
      public void onCollisionEnd(PlayerItem current, PlayerItem body2) {}

      @Override
      public String getName() {
        return "gainHeart";
      }
    };
    gainHeart = (PlayerItem) (gainHeart_def.build(this));

    gainHeart.setScript(new com.star4droid.Game.Scripts.scene1.gainHeartScript()
        .setItem(gainHeart)
        .setStage(this));

    TextDef Welcome_def = new TextDef();
    Welcome_def.Script = "Welcome";
    Welcome_def.Font_Scale = 6.0f;
    Welcome_def.Text = "Welcome To Star Valley!";
    Welcome_def.x = 47.30651f;
    Welcome_def.name = "Welcome";
    Welcome_def.width = 722.1975f;
    Welcome_def.y = 490.9648f;
    Welcome_def.z = 33.0f;
    Welcome_def.height = 402.88098f;

    Welcome_def.elementEvents = new ElementEvent() {
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
        return "Welcome";
      }
    };
    Welcome = (PlayerItem) (Welcome_def.build(this));

    Welcome.setScript(
        new com.star4droid.Game.Scripts.scene1.WelcomeScript().setItem(Welcome).setStage(this));

    LightDef Light1_def = new LightDef();
    Light1_def.attach_To = "player";
    Light1_def.Cone_Degree = 45f;
    Light1_def.Soft = true;
    Light1_def.x = 106.03197f;
    Light1_def.name = "Light1";
    Light1_def.y = 236.22324f;
    Light1_def.Light_Type = "cone";
    Light1_def.z = 34.0f;
    Light1_def.Softness_Length = 250.0f;
    Light1_def.rays = 280f;
    Light1_def.Distance = 250f;

    Light1 = (Light1_def.build(this));

    BoxDef light_def = new BoxDef();
    light_def.type = "UI";
    light_def.Collider_Width = 154.011f;
    light_def.height = 140.91328f;
    light_def.Script = "light";
    light_def.image = "/HUD/openLight.png";
    light_def.Collider_Height = 140.91328f;
    light_def.x = 506.1868f;
    light_def.name = "light";
    light_def.width = 154.011f;
    light_def.y = 1282.0317f;
    light_def.z = 35.0f;
    light_def.Tint = "#FFFFFF";

    light_def.elementEvents = new ElementEvent() {
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
        return "light";
      }
    };
    light = (PlayerItem) (light_def.build(this));

    light.setScript(
        new com.star4droid.Game.Scripts.scene1.lightScript().setItem(light).setStage(this));

    ProgressDef Progress1_def = new ProgressDef();
    Progress1_def.Script = "Progress1";
    Progress1_def.Progress = 720.0f;
    Progress1_def.Max = 1440.0f;
    Progress1_def.x = 49.317284f;
    Progress1_def.name = "Progress1";
    Progress1_def.width = 277.8324f;
    Progress1_def.y = 223.63696f;
    Progress1_def.z = 36.0f;
    Progress1_def.height = 15.331394f;

    Progress1_def.elementEvents = new ElementEvent() {
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
        return "Progress1";
      }
    };
    Progress1 = (PlayerItem) (Progress1_def.build(this));

    Progress1.setScript(new com.star4droid.Game.Scripts.scene1.Progress1Script()
        .setItem(Progress1)
        .setStage(this));

    BoxDef compass_def = new BoxDef();
    compass_def.type = "UI";
    compass_def.Collider_Width = 130.0f;
    compass_def.height = 130.0f;
    compass_def.Script = "compass";
    compass_def.image = "/HUD/compass.png";
    compass_def.Collider_Height = 130.0f;
    compass_def.x = 563.812f;
    compass_def.name = "compass";
    compass_def.width = 130.0f;
    compass_def.y = 30.417622f;
    compass_def.z = 37.0f;
    compass_def.Tint = "#FFFFFF";

    compass_def.elementEvents = new ElementEvent() {
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
        return "compass";
      }
    };
    compass = (PlayerItem) (compass_def.build(this));

    compass.setScript(
        new com.star4droid.Game.Scripts.scene1.compassScript().setItem(compass).setStage(this));

    ProgressDef distance_def = new ProgressDef();
    distance_def.Script = "Progress2";
    distance_def.Progress = 50.0f;
    distance_def.Max = 100.0f;
    distance_def.Progress_Color = "FF4043FF";
    distance_def.x = 565.49524f;
    distance_def.name = "distance";
    distance_def.width = 132.31317f;
    distance_def.y = 169.54889f;
    distance_def.z = 38.0f;
    distance_def.height = 41.02387f;

    distance_def.elementEvents = new ElementEvent() {
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
        return "distance";
      }
    };
    distance = (PlayerItem) (distance_def.build(this));

    TextDef where_def = new TextDef();
    where_def.Script = "Text1";
    where_def.Font_Scale = 2.0f;
    where_def.Text = "where are you going sir ?";
    where_def.x = 343.2226f;
    where_def.name = "where";
    where_def.Visible = false;
    where_def.width = 596.4091f;
    where_def.y = 205.57501f;
    where_def.z = 45.0f;
    where_def.height = 77.33186f;

    where_def.elementEvents = new ElementEvent() {
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
        return "where";
      }
    };
    where = (PlayerItem) (where_def.build(this));

    Light1.attachToBody(player.getBody());

    background.getActor().setZIndex((int) (background_def.z));

    road2.getActor().setZIndex((int) (road2_def.z));

    Text3.getActor().setZIndex((int) (Text3_def.z));

    Text4.getActor().setZIndex((int) (Text4_def.z));

    getHeartText.getActor().setZIndex((int) (getHeartText_def.z));

    Text2.getActor().setZIndex((int) (Text2_def.z));

    Text1.getActor().setZIndex((int) (Text1_def.z));

    road3.getActor().setZIndex((int) (road3_def.z));

    road1.getActor().setZIndex((int) (road1_def.z));

    road.getActor().setZIndex((int) (road_def.z));

    player.getActor().setZIndex((int) (player_def.z));

    wall4.getActor().setZIndex((int) (wall4_def.z));

    wall3.getActor().setZIndex((int) (wall3_def.z));

    wall1.getActor().setZIndex((int) (wall1_def.z));

    wall5.getActor().setZIndex((int) (wall5_def.z));

    wall.getActor().setZIndex((int) (wall_def.z));

    house2.getActor().setZIndex((int) (house2_def.z));

    car.getActor().setZIndex((int) (car_def.z));

    house4.getActor().setZIndex((int) (house4_def.z));

    house3.getActor().setZIndex((int) (house3_def.z));

    house6.getActor().setZIndex((int) (house6_def.z));

    Particle1.getActor().setZIndex((int) (Particle1_def.z));

    house5.getActor().setZIndex((int) (house5_def.z));

    house1.getActor().setZIndex((int) (house1_def.z));

    character.getActor().setZIndex((int) (character_def.z));

    Joystick1.getActor().setZIndex((int) (Joystick1_def.z));

    text.getActor().setZIndex((int) (text_def.z));

    heart2.getActor().setZIndex((int) (heart2_def.z));

    heart1.getActor().setZIndex((int) (heart1_def.z));

    heart.getActor().setZIndex((int) (heart_def.z));

    fan.getActor().setZIndex((int) (fan_def.z));

    gainHeart.getActor().setZIndex((int) (gainHeart_def.z));

    Welcome.getActor().setZIndex((int) (Welcome_def.z));

    light.getActor().setZIndex((int) (light_def.z));

    Progress1.getActor().setZIndex((int) (Progress1_def.z));

    compass.getActor().setZIndex((int) (compass_def.z));

    distance.getActor().setZIndex((int) (distance_def.z));

    where.getActor().setZIndex((int) (where_def.z));
    setZoom((float) (0.25));
    setAnimation(character, "idle-down");

    setScript(new com.star4droid.game.SceneScript.scene1Script().setStage(this));
  }

  @Override
  public void onPause() {}

  @Override
  public void onResume() {}

  @Override
  public void onDraw() {
    setCameraXY(player);
    if (getZooming() < 1) {
      setZoom((float) (getZooming() + 0.009));

    } else {

    }
  }

  @Override
  public void onCollisionBegin(PlayerItem body1, PlayerItem body2) {}

  @Override
  public void onCollisionEnd(PlayerItem body1, PlayerItem body2) {}

  // scripts goes here ...

}
