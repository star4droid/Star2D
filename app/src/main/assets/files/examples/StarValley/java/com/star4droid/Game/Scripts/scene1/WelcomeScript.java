package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import com.badlogic.gdx.graphics.Color;

public class WelcomeScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    float alpha = 1;
    public void onClick(){
        
    }
    public void onTouchStart(InputEvent event){
        
    }
    public void onTouchEnd(InputEvent event){
        
    }
    public void onBodyCreated(){
        getPlayerItem().getActor().setColor(new Color(Color.WHITE));
    }
    public void onBodyUpdate(){
        if(alpha > 0){
            Color color = getPlayerItem().getActor().getColor();
            color.a = alpha;
            getPlayerItem().getActor().setColor(color);
            alpha -= 0.0075;
        } else getPlayerItem().getActor().remove();
    }
    public void onCollisionBegin(PlayerItem other){
        
    }
    public void onCollisionEnd(PlayerItem other){
        
    }
    
}