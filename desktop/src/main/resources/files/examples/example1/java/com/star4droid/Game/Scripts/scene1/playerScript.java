package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;

public class playerScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    public void onClick(){
        
    }
    public void onTouchStart(InputEvent event){
        
    }
    public void onTouchEnd(InputEvent event){
        
    }
    public void onBodyCreated(){
        
    }
    public void onBodyUpdate(){
        
    }
    public void onCollisionBegin(PlayerItem other){
        if(other.getName().contains("arrow")){
            playerItem.getProperties().put("Tint","#FF1C1CFF");
            playerItem.update();
            body.setGravityScale(other.getBody().getAngle()==0 ? -1 : 1);
        } else if(other.getName().contains("thorn")){
            getStage().world.destroyBody(body);
            getStage().openScene("LoseScene");
            getStage().finish();
        }
        //findItem("txt").setItemText("name : "+other.getName().contains("arrow")+", angle : "+other.getBody().getAngle());
    }
    public void onCollisionEnd(PlayerItem other){
        playerItem.getProperties().put("Tint","#FFFFFF");
        playerItem.update();
    }
    
}