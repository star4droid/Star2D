package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import com.badlogic.gdx.graphics.Color;

public class house1Script extends ItemScript {
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
        Color color = com.star4droid.Game.GdxTime.getTime(1440*2).getTint();
        color.a = getPlayerItem().getActor().getColor().a;
        getPlayerItem().getActor().setColor(color);
    }
    public void onCollisionBegin(PlayerItem other){
        if(other.getName().equals("player")){
            //getPlayerItem().getActor().getColor().a = 0;
            Color c = new Color(Color.WHITE);
            c.a = 0.25f;
            getPlayerItem().getActor().setColor(c);
        }
        
    }
    public void onCollisionEnd(PlayerItem other){
        if(other.getName().equals("player")){
            getPlayerItem().getActor().getColor().a = 1f;
            getPlayerItem().getActor().setColor(other.getActor().getColor());
        }
    }
    
}