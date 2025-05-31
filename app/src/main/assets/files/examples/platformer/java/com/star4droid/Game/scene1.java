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
  PlayerItem background, player, Box1, ground, jump, right, coin, coin1, left, text, coin2, Box2;

  @Override
  public void onCreate() {

    BoxDef background_def = new BoxDef();
    background_def.type = "UI";
    background_def.Collider_Width = 1419.1006f;
    background_def.height = 720.31305f;
    background_def.Script = "background";
    background_def.image = "/blue_grass.png";
    background_def.Collider_Height = 720.31305f;
    background_def.name = "background";
    background_def.width = 1419.1006f;
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

    BoxDef player_def = new BoxDef();
    player_def.Collider_Width = 112.496086f;
    player_def.height = 152.1776f;
    player_def.Script = "player";
    player_def.image = "/alienPink_stand.png";
    player_def.Collider_Height = 152.1776f;
    player_def.restitution = 0.0f;
    player_def.x = 338.59988f;
    player_def.name = "player";
    player_def.width = 112.496086f;
    player_def.y = 373.95288f;
    player_def.z = 1.0f;
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

    BoxDef Box1_def = new BoxDef();
    Box1_def.type = "STATIC";
    Box1_def.Collider_Width = 99.90719f;
    Box1_def.height = 96.14049f;
    Box1_def.Script = "Box1";
    Box1_def.image = "/box.png";
    Box1_def.Collider_Height = 96.14049f;
    Box1_def.restitution = 0.0f;
    Box1_def.x = 1037.6382f;
    Box1_def.name = "Box1";
    Box1_def.width = 99.90719f;
    Box1_def.y = 430.22916f;
    Box1_def.z = 2.0f;
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

    Box1.setScript(
        new com.star4droid.Game.Scripts.scene1.Box1Script().setItem(Box1).setStage(this));

    BoxDef ground_def = new BoxDef();
    ground_def.type = "STATIC";
    ground_def.Collider_Width = 1424.8556f;
    ground_def.height = 189.51807f;
    ground_def.Script = "ground";
    ground_def.image = "/ground.png";
    ground_def.Collider_Height = 189.51807f;
    ground_def.restitution = 0.0f;
    ground_def.name = "ground";
    ground_def.width = 1424.8556f;
    ground_def.y = 530.23505f;
    ground_def.z = 3.0f;
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

    BoxDef jump_def = new BoxDef();
    jump_def.type = "UI";
    jump_def.Collider_Width = 221.48987f;
    jump_def.height = 130.4881f;
    jump_def.Script = "jump";
    jump_def.image = "/btn.png";
    jump_def.Collider_Height = 130.4881f;
    jump_def.x = 1153.6604f;
    jump_def.name = "jump";
    jump_def.width = 221.48987f;
    jump_def.y = 295.9888f;
    jump_def.z = 4.0f;
    jump_def.Tint = "#FFFFFF";

    jump_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {
        if (checkCollision(player, ground)) {
          player.getBody().applyForceToCenter((float) (0), (float) (45000000), true);

        } else {

        }
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
        return "jump";
      }
    };
    jump = (PlayerItem) (jump_def.build(this));

    jump.setScript(
        new com.star4droid.Game.Scripts.scene1.jumpScript().setItem(jump).setStage(this));

    BoxDef right_def = new BoxDef();
    right_def.type = "UI";
    right_def.Collider_Width = 185.40819f;
    right_def.height = 143.67416f;
    right_def.Script = "right";
    right_def.image = "/btn.png";
    right_def.Collider_Height = 143.67416f;
    right_def.rotation = 90.0f;
    right_def.x = 163.75273f;
    right_def.name = "right";
    right_def.width = 185.40819f;
    right_def.y = 530.6951f;
    right_def.z = 5.0f;
    right_def.Tint = "#FFFFFF";

    right_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (250), (float) (0));
        setAnimation(player, "walk");
        player.getActor().setScaleX((float) (1));
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (0), (float) (0));
        setImage(player, "/alienPink_stand.png");
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

    BoxDef coin_def = new BoxDef();
    coin_def.type = "KINAMATIC";
    coin_def.Collider_Width = 120.15493f;
    coin_def.height = 104.52431f;
    coin_def.Script = "coin";
    coin_def.image = "/coin.png";
    coin_def.Collider_Height = 104.52431f;
    coin_def.isSensor = true;
    coin_def.x = 842.62f;
    coin_def.name = "coin";
    coin_def.width = 120.15493f;
    coin_def.y = 425.80853f;
    coin_def.z = 6.0f;
    coin_def.Tint = "#FFFFFF";

    coin_def.elementEvents = new ElementEvent() {
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
        return "coin";
      }
    };
    coin = (PlayerItem) (coin_def.build(this));

    coin.setScript(
        new com.star4droid.Game.Scripts.scene1.coinScript().setItem(coin).setStage(this));

    BoxDef coin1_def = new BoxDef();
    coin1_def.type = "KINAMATIC";
    coin1_def.Collider_Width = 120.15493f;
    coin1_def.height = 104.52431f;
    coin1_def.Script = "coin";
    coin1_def.image = "/coin.png";
    coin1_def.Collider_Height = 104.52431f;
    coin1_def.isSensor = true;
    coin1_def.x = 706.3846f;
    coin1_def.name = "coin1";
    coin1_def.width = 120.15493f;
    coin1_def.y = 420.36316f;
    coin1_def.z = 7.0f;
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

    coin1.setScript(
        new com.star4droid.Game.Scripts.scene1.coinScript().setItem(coin1).setStage(this));

    BoxDef left_def = new BoxDef();
    left_def.type = "UI";
    left_def.Collider_Width = 185.40819f;
    left_def.height = 143.67416f;
    left_def.Script = "left";
    left_def.image = "/btn.png";
    left_def.Collider_Height = 143.67416f;
    left_def.rotation = 270.0f;
    left_def.x = 7.8352227f;
    left_def.name = "left";
    left_def.width = 185.40819f;
    left_def.y = 535.5885f;
    left_def.z = 8.0f;
    left_def.Tint = "#FFFFFF";

    left_def.elementEvents = new ElementEvent() {
      @Override
      public void onClick(PlayerItem current) {}

      @Override
      public void onTouchStart(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (-25), (float) (0));
        setAnimation(player, "walk");
        player.getActor().setScaleX((float) (-1));
      }

      @Override
      public void onTouchEnd(PlayerItem current, InputEvent event) {
        player.getBody().setLinearVelocity((float) (0), (float) (0));
        setImage(player, "/alienPink_stand.png");
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

    TextDef text_def = new TextDef();
    text_def.Script = "coins : 0";
    text_def.Font_Scale = 5.0f;
    text_def.Text = "coins : 0";
    text_def.x = 509.5199f;
    text_def.name = "text";
    text_def.width = 462.42343f;
    text_def.y = 571.7612f;
    text_def.z = 9.0f;
    text_def.height = 122.9747f;

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

    BoxDef coin2_def = new BoxDef();
    coin2_def.type = "KINAMATIC";
    coin2_def.Collider_Width = 120.15493f;
    coin2_def.height = 104.52431f;
    coin2_def.Script = "coin";
    coin2_def.image = "/coin.png";
    coin2_def.Collider_Height = 104.52431f;
    coin2_def.isSensor = true;
    coin2_def.x = 572.87415f;
    coin2_def.name = "coin2";
    coin2_def.width = 120.15493f;
    coin2_def.y = 414.91776f;
    coin2_def.z = 10.0f;
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

    coin2.setScript(
        new com.star4droid.Game.Scripts.scene1.coinScript().setItem(coin2).setStage(this));

    BoxDef Box2_def = new BoxDef();
    Box2_def.type = "STATIC";
    Box2_def.Collider_Width = 1280.6168f;
    Box2_def.height = 183.70337f;
    Box2_def.Script = "Box2";
    Box2_def.image = "/ground.png";
    Box2_def.Collider_Height = 183.70337f;
    Box2_def.ColliderY = 0.9094168f;
    Box2_def.x = 1428.8195f;
    Box2_def.name = "Box2";
    Box2_def.width = 1280.6168f;
    Box2_def.y = 533.5147f;
    Box2_def.z = 11.0f;
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

    background.getActor().setZIndex((int) (background_def.z));

    player.getActor().setZIndex((int) (player_def.z));

    Box1.getActor().setZIndex((int) (Box1_def.z));

    ground.getActor().setZIndex((int) (ground_def.z));

    jump.getActor().setZIndex((int) (jump_def.z));

    right.getActor().setZIndex((int) (right_def.z));

    coin.getActor().setZIndex((int) (coin_def.z));

    coin1.getActor().setZIndex((int) (coin1_def.z));

    left.getActor().setZIndex((int) (left_def.z));

    text.getActor().setZIndex((int) (text_def.z));

    coin2.getActor().setZIndex((int) (coin2_def.z));

    Box2.getActor().setZIndex((int) (Box2_def.z));
    setCameraXY(player);
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
