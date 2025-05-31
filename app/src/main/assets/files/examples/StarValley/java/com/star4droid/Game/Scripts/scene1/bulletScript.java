package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import com.badlogic.gdx.math.Vector2;

public class bulletScript extends ItemScript {
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
        getStage().debug("created : "+(body.getUserData()!=null));
    }
    public void onBodyUpdate(){
        getStage().debug("null : "+(body.getUserData()!=null));
        if(body.getUserData()!=null){
            body.setLinearVelocity((Vector2)body.getUserData());
            getStage().debug("set from user data");
        }
    }
    public void onCollisionBegin(PlayerItem other){
        
    }
    public void onCollisionEnd(PlayerItem other){
        
    }
    
}