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
  PlayerItem Box2,
      Box3,
      panel4,
      panel6,
      panel3,
      panel2,
      panel5,
      panel1,
      player,
      Box8,
      Box5,
      Box7,
      Box6,
      barrel,
      Box4,
      Progress1,
      coin2,
      coin3,
      coin1,
      Text1,
      edge;

  @Override
  public void onCreate() {

    BoxDef Box2_def = new BoxDef();
    Box2_def.type = "UI";
    Box2_def.Collider_Width = 1750.6769f;
    Box2_def.Version = "1.0";
    Box2_def.height = 1077.4336f;
    Box2_def.Script = "Box2";
    Box2_def.image = "/primary_Cartoon_Forest_BG_01.png";
    Box2_def.Collider_Height = 1077.4336f;
    Box2_def.name = "Box2";
    Box2_def.width = 1750.6769f;
    Box2_def.y = 2.5664062f;
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
    Box3_def.Collider_Width = 1750.6769f;
    Box3_def.Version = "1.0";
    Box3_def.height = 1077.4336f;
    Box3_def.Script = "Box2";
    Box3_def.image = "/primary_Cartoon_Forest_BG_01.png";
    Box3_def.Collider_Height = 1077.4336f;
    Box3_def.x = 1750.159f;
    Box3_def.name = "Box3";
    Box3_def.width = 1750.6769f;
    Box3_def.y = 2.5664062f;
    Box3_def.z = 1.0f;
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

    BoxDef panel4_def = new BoxDef();
    panel4_def.type = "STATIC";
    panel4_def.Collider_Width = 389.05066f;
    panel4_def.Version = "1.0";
    panel4_def.height = 94.990555f;
    panel4_def.Script = "Box1";
    panel4_def.image = "/Pad_02_1.png";
    panel4_def.Collider_Height = 80.0f;
    panel4_def.ColliderY = 10.0f;
    panel4_def.x = 199.64816f;
    panel4_def.name = "panel4";
    panel4_def.width = 418.28503f;
    panel4_def.y = 1170.503f;
    panel4_def.z = 2.0f;
    panel4_def.Tint = "#FFFFFF";

    panel4_def.elementEvents = new ElementEvent() {
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
        return "panel4";
      }
    };
    panel4 = (PlayerItem) (panel4_def.build(this));

    BoxDef panel6_def = new BoxDef();
    panel6_def.type = "STATIC";
    panel6_def.Collider_Width = 389.05066f;
    panel6_def.Version = "1.0";
    panel6_def.height = 94.990555f;
    panel6_def.Script = "Box1";
    panel6_def.image = "/Pad_02_1.png";
    panel6_def.Collider_Height = 80.0f;
    panel6_def.ColliderY = 10.0f;
    panel6_def.x = 754.95905f;
    panel6_def.name = "panel6";
    panel6_def.width = 418.28503f;
    panel6_def.y = 1267.1012f;
    panel6_def.z = 3.0f;
    panel6_def.Tint = "#FFFFFF";

    panel6_def.elementEvents = new ElementEvent() {
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
        return "panel6";
      }
    };
    panel6 = (PlayerItem) (panel6_def.build(this));

    BoxDef panel3_def = new BoxDef();
    panel3_def.type = "STATIC";
    panel3_def.Collider_Width = 389.05066f;
    panel3_def.Version = "1.0";
    panel3_def.height = 94.990555f;
    panel3_def.Script = "Box1";
    panel3_def.image = "/Pad_02_1.png";
    panel3_def.Collider_Height = 80.0f;
    panel3_def.ColliderY = 10.0f;
    panel3_def.x = 1372.6514f;
    panel3_def.name = "panel3";
    panel3_def.width = 418.28503f;
    panel3_def.y = 672.6606f;
    panel3_def.z = 4.0f;
    panel3_def.Tint = "#FFFFFF";

    panel3_def.elementEvents = new ElementEvent() {
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
        return "panel3";
      }
    };
    panel3 = (PlayerItem) (panel3_def.build(this));

    BoxDef panel2_def = new BoxDef();
    panel2_def.type = "STATIC";
    panel2_def.Collider_Width = 389.05066f;
    panel2_def.Version = "1.0";
    panel2_def.height = 94.990555f;
    panel2_def.Script = "panel1";
    panel2_def.image = "/Pad_02_1.png";
    panel2_def.Collider_Height = 80.0f;
    panel2_def.ColliderY = 10.0f;
    panel2_def.x = 823.6514f;
    panel2_def.name = "panel2";
    panel2_def.width = 418.28503f;
    panel2_def.y = 347.16058f;
    panel2_def.z = 5.0f;
    panel2_def.Tint = "#FFFFFF";

    panel2_def.elementEvents = new ElementEvent() {
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
        return "panel2";
      }
    };
    panel2 = (PlayerItem) (panel2_def.build(this));

    BoxDef panel5_def = new BoxDef();
    panel5_def.type = "STATIC";
    panel5_def.Collider_Width = 366.32474f;
    panel5_def.Version = "1.0";
    panel5_def.height = 128.64693f;
    panel5_def.Script = "Box1";
    panel5_def.image = "/Pad_04_1.png";
    panel5_def.Collider_Height = 95.7313f;
    panel5_def.ColliderY = 20.0f;
    panel5_def.x = 803.58044f;
    panel5_def.name = "panel5";
    panel5_def.width = 395.55905f;
    panel5_def.y = 819.8921f;
    panel5_def.z = 6.0f;
    panel5_def.Tint = "#FFFFFF";

    panel5_def.elementEvents = new ElementEvent() {
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
        return "panel5";
      }
    };
    panel5 = (PlayerItem) (panel5_def.build(this));

    BoxDef panel1_def = new BoxDef();
    panel1_def.type = "STATIC";
    panel1_def.Collider_Width = 500.0f;
    panel1_def.Version = "1.0";
    panel1_def.height = 142.91574f;
    panel1_def.Script = "Box1";
    panel1_def.image = "/Pad_04_1.png";
    panel1_def.Collider_Height = 110.0f;
    panel1_def.ColliderY = 20.0f;
    panel1_def.x = 186.18163f;
    panel1_def.name = "panel1";
    panel1_def.width = 529.2343f;
    panel1_def.y = 209.29352f;
    panel1_def.z = 7.0f;
    panel1_def.Tint = "#FFFFFF";

    panel1_def.elementEvents = new ElementEvent() {
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
        return "panel1";
      }
    };
    panel1 = (PlayerItem) (panel1_def.build(this));

    BoxDef player_def = new BoxDef();
    player_def.Collider_Width = 96.0f;
    player_def.Version = "1.0";
    player_def.height = 167.76567f;
    player_def.Script = "player";
    player_def.image = "/Golem_01_Idle_000.png";
    player_def.Collider_Height = 130.0f;
    player_def.ColliderX = -10.0f;
    player_def.ColliderY = 10.0f;
    player_def.x = 378.30234f;
    player_def.name = "player";
    player_def.width = 146.11212f;
    player_def.y = 350.6917f;
    player_def.z = 8.0f;
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

    BoxDef Box8_def = new BoxDef();
    Box8_def.type = "UI";
    Box8_def.Collider_Width = 116.54975f;
    Box8_def.Version = "1.0";
    Box8_def.height = 116.14653f;
    Box8_def.Script = "Box7";
    Box8_def.image = "/btn2.png";
    Box8_def.Collider_Height = 116.14653f;
    Box8_def.rotation = -90.0f;
    Box8_def.x = 1908.1836f;
    Box8_def.name = "Box8";
    Box8_def.width = 116.54975f;
    Box8_def.y = 510.00204f;
    Box8_def.z = 9.0f;
    Box8_def.Tint = "#FFFFFF";

    Box8_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        for (int i = 0; i < 7; i++) {
          PlayerItem item = findItem("panel" + i);
          if (item == null) continue;
          if (checkCollision(player, item)) {
            player
                .getBody()
                .applyLinearImpulse(
                    (float) (0),
                    (float) (30000),
                    player.getBody().getWorldCenter().x,
                    player.getBody().getWorldCenter().y,
                    true);
            setAnimation(player, "jump");
            return;

          } else {

          }
        }
        if (checkCollision(player, barrel)) {
          player
              .getBody()
              .applyLinearImpulse(
                  (float) (0),
                  (float) (30000),
                  player.getBody().getWorldCenter().x,
                  player.getBody().getWorldCenter().y,
                  true);
          setAnimation(player, "jump");
          return;

        } else {

        }
        if (checkCollision(player, Box4)) {
          player
              .getBody()
              .applyLinearImpulse(
                  (float) (0),
                  (float) (30000),
                  player.getBody().getWorldCenter().x,
                  player.getBody().getWorldCenter().y,
                  true);
          setAnimation(player, "jump");
          return;

        } else {

        }
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        player
            .getBody()
            .applyLinearImpulse(
                (float) (0),
                (float) (0),
                player.getBody().getWorldCenter().x,
                player.getBody().getWorldCenter().y,
                true);
        setAnimation(player, "idle");
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
        return "Box8";
      }
    };
    Box8 = (PlayerItem) (Box8_def.build(this));

    BoxDef Box5_def = new BoxDef();
    Box5_def.type = "UI";
    Box5_def.Collider_Width = 115.48621f;
    Box5_def.Version = "1.0";
    Box5_def.height = 121.369865f;
    Box5_def.Script = "Box6";
    Box5_def.image = "/btn1.png";
    Box5_def.Collider_Height = 121.369865f;
    Box5_def.x = 32.395226f;
    Box5_def.name = "Box5";
    Box5_def.width = 115.48621f;
    Box5_def.y = 229.75348f;
    Box5_def.z = 10.0f;
    Box5_def.Tint = "#FFFFFF";

    Box5_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        player
            .getBody()
            .applyLinearImpulse(
                (float) (-20000),
                (float) (0),
                player.getBody().getWorldCenter().x,
                player.getBody().getWorldCenter().y,
                true);
        player.getActor().setScaleX((float) (-1));
        setAnimation(player, "walk");
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (0), (float)
            (player.getBody().getLinearVelocity().y));
        setAnimation(player, "idle");
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
        return "Box5";
      }
    };
    Box5 = (PlayerItem) (Box5_def.build(this));

    BoxDef Box7_def = new BoxDef();
    Box7_def.type = "UI";
    Box7_def.Collider_Width = 108.01747f;
    Box7_def.Version = "1.0";
    Box7_def.height = 115.164406f;
    Box7_def.Script = "Box7";
    Box7_def.image = "/btn1.png";
    Box7_def.Collider_Height = 115.164406f;
    Box7_def.rotation = 90.0f;
    Box7_def.x = 26.866577f;
    Box7_def.name = "Box7";
    Box7_def.width = 108.01747f;
    Box7_def.y = 490.4883f;
    Box7_def.z = 11.0f;
    Box7_def.Tint = "#FFFFFF";

    Box7_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        for (int i = 0; i < 7; i++) {
          PlayerItem item = findItem("panel" + i);
          if (item == null) continue;
          if (checkCollision(player, item)) {
            player
                .getBody()
                .applyLinearImpulse(
                    (float) (0),
                    (float) (30000),
                    player.getBody().getWorldCenter().x,
                    player.getBody().getWorldCenter().y,
                    true);
            setAnimation(player, "jump");
            return;

          } else {

          }
        }
        if (checkCollision(player, barrel)) {
          player
              .getBody()
              .applyLinearImpulse(
                  (float) (0),
                  (float) (30000),
                  player.getBody().getWorldCenter().x,
                  player.getBody().getWorldCenter().y,
                  true);
          setAnimation(player, "jump");
          return;

        } else {

        }
        if (checkCollision(player, Box4)) {
          player
              .getBody()
              .applyLinearImpulse(
                  (float) (0),
                  (float) (30000),
                  player.getBody().getWorldCenter().x,
                  player.getBody().getWorldCenter().y,
                  true);
          setAnimation(player, "jump");
          return;

        } else {

        }
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        player
            .getBody()
            .applyLinearImpulse(
                (float) (0),
                (float) (0),
                player.getBody().getWorldCenter().x,
                player.getBody().getWorldCenter().y,
                true);
        setAnimation(player, "idle");
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
        return "Box7";
      }
    };
    Box7 = (PlayerItem) (Box7_def.build(this));

    BoxDef Box6_def = new BoxDef();
    Box6_def.type = "UI";
    Box6_def.Collider_Width = 133.8082f;
    Box6_def.Version = "1.0";
    Box6_def.height = 132.36302f;
    Box6_def.Script = "Box5";
    Box6_def.image = "/btn2.png";
    Box6_def.Collider_Height = 132.36302f;
    Box6_def.x = 1899.4443f;
    Box6_def.name = "Box6";
    Box6_def.width = 133.8082f;
    Box6_def.y = 256.4721f;
    Box6_def.z = 12.0f;
    Box6_def.Tint = "#FFFFFF";

    Box6_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        player
            .getBody()
            .applyLinearImpulse(
                (float) (20000),
                (float) (0),
                player.getBody().getWorldCenter().x,
                player.getBody().getWorldCenter().y,
                true);
        setAnimation(player, "walk");
        player.getActor().setScaleX((float) (1));
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (0), (float)
            (player.getBody().getLinearVelocity().y));
        setAnimation(player, "idle");
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
        return "Box6";
      }
    };
    Box6 = (PlayerItem) (Box6_def.build(this));

    BoxDef barrel_def = new BoxDef();
    barrel_def.type = "KINAMATIC";
    barrel_def.Collider_Width = 101.207695f;
    barrel_def.Version = "1.0";
    barrel_def.height = 130.75063f;
    barrel_def.Script = "barrel";
    barrel_def.image = "/Prop_7.png";
    barrel_def.Collider_Height = 130.75063f;
    barrel_def.x = 1142.1077f;
    barrel_def.name = "barrel";
    barrel_def.width = 101.207695f;
    barrel_def.y = 425.7821f;
    barrel_def.z = 13.0f;
    barrel_def.Tint = "#FFFFFF";

    barrel_def.elementEvents = new ElementEvent() {
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
        return "barrel";
      }
    };
    barrel = (PlayerItem) (barrel_def.build(this));

    BoxDef Box4_def = new BoxDef();
    Box4_def.type = "STATIC";
    Box4_def.Collider_Width = 147.49916f;
    Box4_def.Version = "1.0";
    Box4_def.height = 97.499596f;
    Box4_def.Script = "Box4";
    Box4_def.image = "/pad0.png";
    Box4_def.Collider_Height = 97.499596f;
    Box4_def.x = 624.22f;
    Box4_def.name = "Box4";
    Box4_def.width = 147.49916f;
    Box4_def.y = 983.53424f;
    Box4_def.z = 14.0f;
    Box4_def.Tint = "#FFFFFF";

    Box4_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        player
            .getBody()
            .applyLinearImpulse(
                (float) (player.getBody().getLinearVelocity().x),
                (float) (50),
                player.getBody().getWorldCenter().x,
                player.getBody().getWorldCenter().y,
                true);
      }

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

    ProgressDef Progress1_def = new ProgressDef();
    Progress1_def.Script = "Progress1";
    Progress1_def.Progress = 50.0f;
    Progress1_def.Max = 100.0f;
    Progress1_def.Progress_Color = "0086FFFF";
    Progress1_def.Version = "1.0";
    Progress1_def.Background_Color = "BA6000FF";
    Progress1_def.x = 861.5411f;
    Progress1_def.name = "Progress1";
    Progress1_def.width = 427.18045f;
    Progress1_def.y = 981.2753f;
    Progress1_def.z = 15.0f;
    Progress1_def.height = 40.402714f;

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

    CircleDef coin2_def = new CircleDef();
    coin2_def.type = "KINAMATIC";
    coin2_def.Version = "1.0";
    coin2_def.radius = 34.986336f;
    coin2_def.Script = "coin1";
    coin2_def.image = "/c.png";
    coin2_def.isSensor = true;
    coin2_def.Collider_Radius = 34.986336f;
    coin2_def.x = 1166.2961f;
    coin2_def.name = "coin2";
    coin2_def.y = 579.2232f;
    coin2_def.z = 16.0f;
    coin2_def.Tint = "#FFFFFF";
    coin2_def.Fixed_Rotation = true;

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

    CircleDef coin3_def = new CircleDef();
    coin3_def.type = "KINAMATIC";
    coin3_def.Version = "1.0";
    coin3_def.radius = 34.986336f;
    coin3_def.Script = "coin1";
    coin3_def.image = "/c.png";
    coin3_def.isSensor = true;
    coin3_def.Collider_Radius = 34.986336f;
    coin3_def.x = 412.30615f;
    coin3_def.name = "coin3";
    coin3_def.y = 1281.7416f;
    coin3_def.z = 17.0f;
    coin3_def.Tint = "#FFFFFF";
    coin3_def.Fixed_Rotation = true;

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

    CircleDef coin1_def = new CircleDef();
    coin1_def.type = "KINAMATIC";
    coin1_def.Version = "1.0";
    coin1_def.radius = 34.986336f;
    coin1_def.Script = "coin1";
    coin1_def.image = "/c.png";
    coin1_def.isSensor = true;
    coin1_def.Collider_Radius = 34.986336f;
    coin1_def.x = 1088.44f;
    coin1_def.name = "coin1";
    coin1_def.y = 1523.252f;
    coin1_def.z = 18.0f;
    coin1_def.Tint = "#FFFFFF";
    coin1_def.Fixed_Rotation = true;

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

    TextDef Text1_def = new TextDef();
    Text1_def.Script = "Text1";
    Text1_def.Font_Scale = 5.0f;
    Text1_def.Text = "X0";
    Text1_def.Version = "1.0";
    Text1_def.x = 40.661976f;
    Text1_def.name = "Text1";
    Text1_def.width = 120f;
    Text1_def.y = 993.717f;
    Text1_def.z = 19.0f;
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

    BoxDef edge_def = new BoxDef();
    edge_def.type = "STATIC";
    edge_def.Collider_Width = 3500.0f;
    edge_def.Version = "1.0";
    edge_def.Visible = false;
    edge_def.height = 35.694283f;
    edge_def.Script = "edge";
    edge_def.Collider_Height = 35.0f;
    edge_def.x = -326.81393f;
    edge_def.name = "edge";
    edge_def.width = 3500.0f;
    edge_def.y = 9.316406f;
    edge_def.z = 20.0f;
    edge_def.Tint = "#FFFFFF";

    edge_def.elementEvents = new ElementEvent() {
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
        return "edge";
      }
    };
    edge = (PlayerItem) (edge_def.build(this));

    Box2.getActor().setZIndex((int) (Box2_def.z));

    Box3.getActor().setZIndex((int) (Box3_def.z));

    panel4.getActor().setZIndex((int) (panel4_def.z));

    panel6.getActor().setZIndex((int) (panel6_def.z));

    panel3.getActor().setZIndex((int) (panel3_def.z));

    panel2.getActor().setZIndex((int) (panel2_def.z));

    panel5.getActor().setZIndex((int) (panel5_def.z));

    panel1.getActor().setZIndex((int) (panel1_def.z));

    player.getActor().setZIndex((int) (player_def.z));

    Box8.getActor().setZIndex((int) (Box8_def.z));

    Box5.getActor().setZIndex((int) (Box5_def.z));

    Box7.getActor().setZIndex((int) (Box7_def.z));

    Box6.getActor().setZIndex((int) (Box6_def.z));

    barrel.getActor().setZIndex((int) (barrel_def.z));

    Box4.getActor().setZIndex((int) (Box4_def.z));

    Progress1.getActor().setZIndex((int) (Progress1_def.z));

    coin2.getActor().setZIndex((int) (coin2_def.z));

    coin3.getActor().setZIndex((int) (coin3_def.z));

    coin1.getActor().setZIndex((int) (coin1_def.z));

    Text1.getActor().setZIndex((int) (Text1_def.z));

    edge.getActor().setZIndex((int) (edge_def.z));

    panel1.addChild(panel2);
    setAnimation(player, "idle");
  }

  @Override
  public void onPause() {}

  @Override
  public void onResume() {}

  @Override
  public void onDraw() {
    setCameraXY(player);
    if (checkCollision(player, coin1)) {
      coin1.destroy();
      coinCount += 1;
      Text1.setItemText("X" + String.valueOf(coinCount));
      if (coinCount == 3) {
        openScene("win");

      } else {

      }

    } else {

    }
    if (checkCollision(player, coin2)) {
      coin2.destroy();
      coinCount += 1;
      Text1.setItemText("X" + String.valueOf(coinCount));
      if (coinCount == 3) {
        openScene("win");

      } else {

      }

    } else {

    }
    if (checkCollision(player, coin3)) {
      coin3.destroy();
      coinCount += 1;
      Text1.setItemText("X" + String.valueOf(coinCount));
      if (coinCount == 3) {
        openScene("win");

      } else {

      }

    } else {

    }
  }

  @Override
  public void onCollisionBegin(PlayerItem body1, PlayerItem body2) {
    if (checkCollision(edge, player)) {
      openScene("scene1");
      finish();

    } else {

    }
  }

  @Override
  public void onCollisionEnd(PlayerItem body1, PlayerItem body2) {}

  // scripts goes here ...
  public int coinCount = 0;
}
