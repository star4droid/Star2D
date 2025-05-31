package com.star4droid.Game.Scripts.scene1;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Items.*;
import com.star4droid.Game.*;
import com.star4droid.Game.GdxTime;

public class backgroundScript extends ItemScript {
/*
    Hi coder... :)
    the engine still in it's beta and you may face so many bugs,
    so please contact us without hesitation...
*/
    PlayerItem text;
    public void onClick(){
        
    }
    public void onTouchStart(InputEvent event){
        
    }
    public void onTouchEnd(InputEvent event){
        
    }
    public void onBodyCreated(){
        text = getStage().findItem("text");
    }
    public void onBodyUpdate(){
        GdxTime time = GdxTime.getTime(1440*2);
        getPlayerItem().getActor().setColor(time.getTint());
        if(text!=null)
            text.setItemText("Time : "+two(time.getHour())+":"+two(time.getMin()));
         else text = getStage().findItem("text");
    }
    
    private String two(int i){
        String str = String.valueOf(i);
        if(str.length()==1) str = "0"+str;
        return str;
    }
    
    public void onCollisionBegin(PlayerItem other){
        
    }
    public void onCollisionEnd(PlayerItem other){
        
    }
    
}