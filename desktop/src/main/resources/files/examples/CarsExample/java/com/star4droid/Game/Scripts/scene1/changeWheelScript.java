package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import box2dLight.Light;
import com.badlogic.gdx.math.Vector2;

public class changeWheelScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    PlayerItem[] wheels = new PlayerItem[10];
    int current = 1;
    public void onClick(){
        current = current+1;
        if(current>7) current=1;
        if(wheels[0] == null)
            init();
        getStage().setValue("wheel","/wheels/wheel"+current+".png");
        getStage().preferences.flush();
        for(PlayerItem wheel : wheels){
            if(wheel != null)
                getStage().setImage(wheel,"/wheels/wheel"+current+".png");
        }
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
    
    private void init(){
        for(int x = 0; x < 10; x++){
            wheels[x] = getStage().findItem("w"+(x+1));
        }
    }
    
}