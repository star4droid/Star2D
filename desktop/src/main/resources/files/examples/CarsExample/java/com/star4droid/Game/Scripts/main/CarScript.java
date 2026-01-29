package com.star4droid.Game.Scripts.main;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import box2dLight.Light;
import com.badlogic.gdx.math.Vector2;

public class CarScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    int coins = 0;
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
        if(other.getName().toLowerCase().contains("coin")){
            coins = coins + 1;
            getStage().findItem("txt").setItemText("coins : "+coins);
            other.destroy();
        }
    }
    public void onCollisionEnd(PlayerItem other){
        
    }
    
}