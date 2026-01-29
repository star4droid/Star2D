package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import com.badlogic.gdx.math.Vector2;

public class compassScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    PlayerItem house,player,where, distance;
    public void onClick(){
        
    }
    public void onTouchStart(InputEvent event){
        
    }
    public void onTouchEnd(InputEvent event){
        
    }
    public void onBodyCreated(){
        house = getStage().findItem("house1");
        player = getStage().findItem("player");
        distance = getStage().findItem("distance");
        where = getStage().findItem("where");
    }
    public void onBodyUpdate(){
        if(house!=null && player!=null && distance!=null && where!=null){
            getPlayerItem().getActor().setRotation(player.angleDegreesTo(house) + 90);
            float dst = (float)player.distTo(house);
            distance.setMax((int)Math.max(10000,dst));
            distance.setProgress((int)dst);
            where.getActor().setVisible(dst > 10000);
        } else onBodyCreated();
    }
    
    public void onCollisionBegin(PlayerItem other){
        
    }
    public void onCollisionEnd(PlayerItem other){
        
    }
    
}