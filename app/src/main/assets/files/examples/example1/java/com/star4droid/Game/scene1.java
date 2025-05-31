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
      ground1,
      ground2,
      ground,
      player,
      arrow1,
      arrow,
      thorn,
      right,
      left,
      Box,
      Box2,
      chain4,
      chain3,
      chain2,
      Text1,
      chain1,
      chain;
  RevoluteJoint j1;
  RevoluteJoint j2;
  RevoluteJoint j3;
  RevoluteJoint j4;
  RevoluteJoint j5;
  RevoluteJoint j6;

  @Override
  public void onCreate() {

    BoxDef background_def = new BoxDef();
    background_def.type = "UI";
    background_def.Collider_Width = 720.0f;
    background_def.height = 1506.0f;
    background_def.Script = "background";
    background_def.image = "/color.png";
    background_def.Collider_Height = 1506.0f;
    background_def.x = 2.1926732f;
    background_def.name = "background";
    background_def.width = 720.0f;
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

    background.setScript(new com.star4droid.Game.Scripts.scene1.backgroundScript()
        .setItem(background)
        .setStage(this));

    BoxDef ground1_def = new BoxDef();
    ground1_def.type = "STATIC";
    ground1_def.Collider_Width = 1480.0f;
    ground1_def.height = 90.0f;
    ground1_def.Script = "ground";
    ground1_def.image = "/color2.png";
    ground1_def.Collider_Height = 90.0f;
    ground1_def.restitution = 0.0f;
    ground1_def.x = -2.5773208f;
    ground1_def.name = "ground1";
    ground1_def.width = 1480.0f;
    ground1_def.y = 385.0345f;
    ground1_def.z = 1f;
    ground1_def.Tint = "#FFFFFF";

    ground1_def.elementEvents = new ElementEvent() {
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
        return "ground1";
      }
    };
    ground1 = (PlayerItem) (ground1_def.build(this));

    BoxDef ground2_def = new BoxDef();
    ground2_def.type = "STATIC";
    ground2_def.Collider_Width = 367.67795f;
    ground2_def.height = 90.0f;
    ground2_def.Script = "ground";
    ground2_def.image = "/color2.png";
    ground2_def.Collider_Height = 90.0f;
    ground2_def.restitution = 0.0f;
    ground2_def.x = 952.77594f;
    ground2_def.name = "ground2";
    ground2_def.width = 367.67795f;
    ground2_def.y = 773.25183f;
    ground2_def.z = 2f;
    ground2_def.Tint = "#FFFFFF";

    ground2_def.elementEvents = new ElementEvent() {
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
        return "ground2";
      }
    };
    ground2 = (PlayerItem) (ground2_def.build(this));

    BoxDef ground_def = new BoxDef();
    ground_def.type = "STATIC";
    ground_def.Collider_Width = 1480.0f;
    ground_def.height = 90.0f;
    ground_def.Script = "ground";
    ground_def.image = "/color2.png";
    ground_def.Collider_Height = 90.0f;
    ground_def.restitution = 0.0f;
    ground_def.x = 2.999578f;
    ground_def.name = "ground";
    ground_def.width = 1480.0f;
    ground_def.y = 1132.3395f;
    ground_def.z = 3f;
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

    BoxDef player_def = new BoxDef();
    player_def.Collider_Width = 103.992325f;
    player_def.height = 102.49254f;
    player_def.Script = "player";
    player_def.image = "/player.jpg";
    player_def.Collider_Height = 102.49254f;
    player_def.restitution = 0.0f;
    player_def.x = 158.13127f;
    player_def.name = "player";
    player_def.width = 103.992325f;
    player_def.y = 962.9357f;
    player_def.z = 4f;
    player_def.Tint = "FFFFFFFF";
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

    BoxDef arrow1_def = new BoxDef();
    arrow1_def.Gravity_Scale = 0.0f;
    arrow1_def.Collider_Width = 88.30214f;
    arrow1_def.height = 76.95717f;
    arrow1_def.Script = "arrow";
    arrow1_def.image = "/arrow.png";
    arrow1_def.Collider_Height = 76.95717f;
    arrow1_def.rotation = 180.0f;
    arrow1_def.isSensor = true;
    arrow1_def.x = 615.6652f;
    arrow1_def.name = "arrow1";
    arrow1_def.width = 88.30214f;
    arrow1_def.y = 500.5419f;
    arrow1_def.z = 5f;
    arrow1_def.Tint = "#FFFFFF";

    arrow1_def.elementEvents = new ElementEvent() {
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
        return "arrow1";
      }
    };
    arrow1 = (PlayerItem) (arrow1_def.build(this));

    arrow1.setScript(
        new com.star4droid.Game.Scripts.scene1.arrowScript().setItem(arrow1).setStage(this));

    BoxDef arrow_def = new BoxDef();
    arrow_def.Gravity_Scale = 0.0f;
    arrow_def.Collider_Width = 88.30214f;
    arrow_def.height = 76.95717f;
    arrow_def.Script = "arrow";
    arrow_def.image = "/arrow.png";
    arrow_def.Collider_Height = 76.95717f;
    arrow_def.isSensor = true;
    arrow_def.x = 437.20493f;
    arrow_def.name = "arrow";
    arrow_def.width = 88.30214f;
    arrow_def.y = 1015.84717f;
    arrow_def.z = 6f;
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

    BoxDef thorn_def = new BoxDef();
    thorn_def.type = "STATIC";
    thorn_def.Collider_Width = 269.0f;
    thorn_def.height = 51.5f;
    thorn_def.Script = "thorn";
    thorn_def.image = "/thorn.png";
    thorn_def.Collider_Height = 51.5f;
    thorn_def.restitution = 0.0f;
    thorn_def.x = 580.5f;
    thorn_def.name = "thorn";
    thorn_def.width = 269.0f;
    thorn_def.y = 1078.5f;
    thorn_def.z = 7f;
    thorn_def.Tint = "#FFFFFF";

    thorn_def.elementEvents = new ElementEvent() {
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
        return "thorn";
      }
    };
    thorn = (PlayerItem) (thorn_def.build(this));

    BoxDef right_def = new BoxDef();
    right_def.type = "UI";
    right_def.Collider_Width = 100.0f;
    right_def.height = 100.0f;
    right_def.Script = "right";
    right_def.image = "/btn.png";
    right_def.Collider_Height = 100.0f;
    right_def.rotation = 90.0f;
    right_def.x = 570.79297f;
    right_def.name = "right";
    right_def.width = 100.0f;
    right_def.y = 1370.0f;
    right_def.z = 8f;
    right_def.Tint = "#FFFFFF";

    right_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (100), (float) (0));
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (0), (float) (0));
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
        return "right";
      }
    };
    right = (PlayerItem) (right_def.build(this));

    BoxDef left_def = new BoxDef();
    left_def.type = "UI";
    left_def.Collider_Width = 100.0f;
    left_def.height = 100.0f;
    left_def.Script = "left";
    left_def.image = "/btn.png";
    left_def.Collider_Height = 100.0f;
    left_def.rotation = 270.0f;
    left_def.x = 49.760605f;
    left_def.name = "left";
    left_def.width = 100.0f;
    left_def.y = 1370.0f;
    left_def.z = 9f;
    left_def.Tint = "#FFFFFF";

    left_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (-100), (float) (0));
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (0), (float) (0));
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
        return "left";
      }
    };
    left = (PlayerItem) (left_def.build(this));

    BoxDef Box_def = new BoxDef();
    Box_def.type = "STATIC";
    Box_def.Collider_Width = 50f;
    Box_def.Script = "Box";
    Box_def.image = "/color2.png";
    Box_def.Collider_Height = 50f;
    Box_def.x = 311.35553f;
    Box_def.name = "Box";
    Box_def.y = 531.6651f;
    Box_def.z = 10f;
    Box_def.Tint = "#FFFFFF";

    Box_def.elementEvents = new ElementEvent() {
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
        return "Box";
      }
    };
    Box = (PlayerItem) (Box_def.build(this));

    BoxDef Box2_def = new BoxDef();
    Box2_def.Collider_Width = 50f;
    Box2_def.Script = "Box2";
    Box2_def.image = "/player.jpg";
    Box2_def.Collider_Height = 50f;
    Box2_def.x = 160.04205f;
    Box2_def.name = "Box2";
    Box2_def.y = 696.24646f;
    Box2_def.z = 11f;
    Box2_def.Tint = "8DC796FF";

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

    BoxDef chain4_def = new BoxDef();
    chain4_def.Collider_Width = 19.331636f;
    chain4_def.height = 18.071274f;
    chain4_def.Script = "chain";
    chain4_def.image = "/color2.png";
    chain4_def.Collider_Height = 18.071274f;
    chain4_def.x = 211.91104f;
    chain4_def.name = "chain4";
    chain4_def.width = 19.331636f;
    chain4_def.y = 668.1941f;
    chain4_def.z = 12f;
    chain4_def.Tint = "#FFFFFF";

    chain4_def.elementEvents = new ElementEvent() {
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
        return "chain4";
      }
    };
    chain4 = (PlayerItem) (chain4_def.build(this));

    BoxDef chain3_def = new BoxDef();
    chain3_def.Collider_Width = 19.331636f;
    chain3_def.height = 18.071274f;
    chain3_def.Script = "chain";
    chain3_def.image = "/color2.png";
    chain3_def.Collider_Height = 18.071274f;
    chain3_def.x = 230.71692f;
    chain3_def.name = "chain3";
    chain3_def.width = 19.331636f;
    chain3_def.y = 646.6228f;
    chain3_def.z = 13f;
    chain3_def.Tint = "#FFFFFF";

    chain3_def.elementEvents = new ElementEvent() {
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
        return "chain3";
      }
    };
    chain3 = (PlayerItem) (chain3_def.build(this));

    BoxDef chain2_def = new BoxDef();
    chain2_def.Collider_Width = 19.331636f;
    chain2_def.height = 18.071274f;
    chain2_def.Script = "chain";
    chain2_def.image = "/color2.png";
    chain2_def.Collider_Height = 18.071274f;
    chain2_def.x = 250.90552f;
    chain2_def.name = "chain2";
    chain2_def.width = 19.331636f;
    chain2_def.y = 626.9872f;
    chain2_def.z = 14f;
    chain2_def.Tint = "#FFFFFF";

    chain2_def.elementEvents = new ElementEvent() {
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
        return "chain2";
      }
    };
    chain2 = (PlayerItem) (chain2_def.build(this));

    TextDef Text1_def = new TextDef();
    Text1_def.Script = "Text1";
    Text1_def.Text = "Welcome to Star2D";
    Text1_def.x = 16.897074f;
    Text1_def.name = "Text1";
    Text1_def.width = 711.3322f;
    Text1_def.y = 90.76607f;
    Text1_def.z = 15f;
    Text1_def.height = 203.97075f;
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

    BoxDef chain1_def = new BoxDef();
    chain1_def.Collider_Width = 19.331636f;
    chain1_def.height = 18.071274f;
    chain1_def.Script = "chain";
    chain1_def.image = "/color2.png";
    chain1_def.Collider_Height = 18.071274f;
    chain1_def.x = 270.81757f;
    chain1_def.name = "chain1";
    chain1_def.width = 19.331636f;
    chain1_def.y = 606.5224f;
    chain1_def.z = 16f;
    chain1_def.Tint = "#FFFFFF";

    chain1_def.elementEvents = new ElementEvent() {
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
        return "chain1";
      }
    };
    chain1 = (PlayerItem) (chain1_def.build(this));

    BoxDef chain_def = new BoxDef();
    chain_def.Collider_Width = 19.331636f;
    chain_def.height = 18.071274f;
    chain_def.Script = "chain";
    chain_def.image = "/color2.png";
    chain_def.Collider_Height = 18.071274f;
    chain_def.x = 290.25497f;
    chain_def.name = "chain";
    chain_def.width = 19.331636f;
    chain_def.y = 585.29504f;
    chain_def.z = 17f;
    chain_def.Tint = "#FFFFFF";

    chain_def.elementEvents = new ElementEvent() {
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
        return "chain";
      }
    };
    chain = (PlayerItem) (chain_def.build(this));

    background.getActor().setZIndex((int) (background_def.z));

    ground1.getActor().setZIndex((int) (ground1_def.z));

    ground2.getActor().setZIndex((int) (ground2_def.z));

    ground.getActor().setZIndex((int) (ground_def.z));

    player.getActor().setZIndex((int) (player_def.z));

    arrow1.getActor().setZIndex((int) (arrow1_def.z));

    arrow.getActor().setZIndex((int) (arrow_def.z));

    thorn.getActor().setZIndex((int) (thorn_def.z));

    right.getActor().setZIndex((int) (right_def.z));

    left.getActor().setZIndex((int) (left_def.z));

    Box.getActor().setZIndex((int) (Box_def.z));

    Box2.getActor().setZIndex((int) (Box2_def.z));

    chain4.getActor().setZIndex((int) (chain4_def.z));

    chain3.getActor().setZIndex((int) (chain3_def.z));

    chain2.getActor().setZIndex((int) (chain2_def.z));

    Text1.getActor().setZIndex((int) (Text1_def.z));

    chain1.getActor().setZIndex((int) (chain1_def.z));

    chain.getActor().setZIndex((int) (chain_def.z));

    RevoluteJointDef j1_Def = new RevoluteJointDef();
    j1_Def.enableLimit = false;

    j1_Def.enableMotor = false;
    j1_Def.lowerAngle = 0.0f;
    j1_Def.maxMotorTorque = 0.0f;
    j1_Def.motorSpeed = 0.0f;
    j1_Def.referenceAngle = 0.0f;
    j1_Def.upperAngle = 0.0f;

    j1_Def.collideConnected = false;

    j1_Def.initialize(Box.getBody(), chain.getBody(), new Vector2(341.43045f, 940.2181f));
    j1 = (RevoluteJoint) (this.world.createJoint(j1_Def));

    RevoluteJointDef j2_Def = new RevoluteJointDef();
    j2_Def.enableLimit = false;

    j2_Def.enableMotor = false;
    j2_Def.lowerAngle = 0.0f;
    j2_Def.maxMotorTorque = 0.0f;
    j2_Def.motorSpeed = 0.0f;
    j2_Def.referenceAngle = 0.0f;
    j2_Def.upperAngle = 0.0f;

    j2_Def.collideConnected = false;

    j2_Def.initialize(chain.getBody(), chain1.getBody(), new Vector2(302.32047f, 910.2528f));
    j2 = (RevoluteJoint) (this.world.createJoint(j2_Def));

    RevoluteJointDef j3_Def = new RevoluteJointDef();
    j3_Def.enableLimit = false;

    j3_Def.enableMotor = false;
    j3_Def.lowerAngle = 0.0f;
    j3_Def.maxMotorTorque = 0.0f;
    j3_Def.motorSpeed = 0.0f;
    j3_Def.referenceAngle = 0.0f;
    j3_Def.upperAngle = 0.0f;

    j3_Def.collideConnected = false;

    j3_Def.initialize(chain1.getBody(), chain2.getBody(), new Vector2(263.8154f, 869.3568f));
    j3 = (RevoluteJoint) (this.world.createJoint(j3_Def));

    RevoluteJointDef j4_Def = new RevoluteJointDef();
    j4_Def.enableLimit = false;

    j4_Def.enableMotor = false;
    j4_Def.lowerAngle = 0.0f;
    j4_Def.maxMotorTorque = 0.0f;
    j4_Def.motorSpeed = 0.0f;
    j4_Def.referenceAngle = 0.0f;
    j4_Def.upperAngle = 0.0f;

    j4_Def.collideConnected = false;

    j4_Def.initialize(chain2.getBody(), chain3.getBody(), new Vector2(242.17206f, 849.47516f));
    j4 = (RevoluteJoint) (this.world.createJoint(j4_Def));

    RevoluteJointDef j5_Def = new RevoluteJointDef();
    j5_Def.enableLimit = false;

    j5_Def.enableMotor = false;
    j5_Def.lowerAngle = 0.0f;
    j5_Def.maxMotorTorque = 0.0f;
    j5_Def.motorSpeed = 0.0f;
    j5_Def.referenceAngle = 0.0f;
    j5_Def.upperAngle = 0.0f;

    j5_Def.collideConnected = false;

    j5_Def.initialize(chain3.getBody(), chain4.getBody(), new Vector2(224.3035f, 829.0904f));
    j5 = (RevoluteJoint) (this.world.createJoint(j5_Def));

    RevoluteJointDef j6_Def = new RevoluteJointDef();
    j6_Def.enableLimit = false;

    j6_Def.enableMotor = false;
    j6_Def.lowerAngle = 0.0f;
    j6_Def.maxMotorTorque = 0.0f;
    j6_Def.motorSpeed = 0.0f;
    j6_Def.referenceAngle = 0.0f;
    j6_Def.upperAngle = 0.0f;

    j6_Def.collideConnected = false;

    j6_Def.initialize(chain4.getBody(), Box2.getBody(), new Vector2(187.43404f, 785.0487f));
    j6 = (RevoluteJoint) (this.world.createJoint(j6_Def));

    setScript(new com.star4droid.game.SceneScript.scene1Script().setStage(this));
  }

  @Override
  public void onPause() {}

  @Override
  public void onResume() {}

  @Override
  public void onDraw() {
    setCameraXY(player);
  }

  @Override
  public void onCollisionBegin(PlayerItem body1, PlayerItem body2) {}

  @Override
  public void onCollisionEnd(PlayerItem body1, PlayerItem body2) {}

  // scripts goes here ...

}
