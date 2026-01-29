package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import box2dLight.Light;
import com.badlogic.gdx.math.Vector2;

public class nextCarScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    int current = 1;
    public void onClick(){
        current = current + 1;
        if(current==6) current = 1;
        getStage().setCameraX(getStage().findItem("c"+current));
        getStage().setValue("car","c"+current);
        getStage().preferences.flush();
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
        
    }
    public void onCollisionEnd(PlayerItem other){
        
    }
    
}