package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import com.badlogic.gdx.math.Vector2;

public class shootScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    float vx = 0,vy = 1;
    PlayerItem Joystick1,tank,bullet;
    public void onClick(){
        int i = 1;
        while(findItem("b"+i) != null) i++;
        bullet.getProperties().remove("time");
        PlayerItem bl = (PlayerItem)bullet.getClone("b"+i);
        bl.getBody().setTransform(tank.getBodyX(),tank.getBodyY(),tank.getBody().getAngle());
        bl.getActor().setScaleY(-1);
        Vector2 vec = new Vector2(vx*500,vy*500);
        bl.getBody().setLinearVelocity(vec);
        bl.getBody().setUserData(vec);
        //getStage().debug("vec data : "+vec);
        //getStage().debug("tank at : "+tank.getBodyX()+", y : "+tank.getBodyY()+"\nbullet : "+bl.getBodyX()+", y : "+bl.getBodyY());
    }
    public void onTouchStart(InputEvent event){
        
    }
    public void onTouchEnd(InputEvent event){
        
    }
    public void onBodyCreated(){
        Joystick1 = getStage().findItem("Joystick1");
        bullet = getStage().findItem("bullet");
         tank = getStage().findItem("tank");
        //getStage().debug("shoot btn : "+getPlayerItem().getName());
    }
    public void onBodyUpdate(){
        //getStage().debug("updated : "+getPlayerItem().getName());
        if(Joystick1.getPower()>0){
            vx = Joystick1.getJoyStickX();
            vy = Joystick1.getJoyStickY();
        }
    }
    public void onCollisionBegin(PlayerItem other){
        
    }
    public void onCollisionEnd(PlayerItem other){
        
    }
    
}