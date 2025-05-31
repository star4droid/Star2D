package com.star4droid.game.SceneScript;
import com.star4droid.template.Utils.SceneScript;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.Gdx;
import com.star4droid.template.Items.*;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.Texture;
import com.star4droid.template.Utils.PlayerItem;
/*
    Hi Coder :)
    & Thanks for using Star2D Evolution 
    example :
    - Reverse body gravity on create:
        getStage().findItem("body").getBody().setGavityScale(-1);
    - Link to the other script:
        linkTo(OtherScene.class);
        //or
        linkTo(new OtherScene());
*/
public class scene1Script extends SceneScript {
    PlayerItem joystick,player;
    public void create(){
        joystick = getStage().findItem("Joystick1");
        player = getStage().findItem("player");
    }
    public void debug(String str){
        getStage().debug(str);
    }
	public void draw(){
        if(joystick.getPower()!=0)
            player.getBody().setTransform(player.getBody().getPosition(),(float)Math.toRadians(joystick.getAngleDegrees() - 180));
	    player.getBody().setLinearVelocity(new Vector2(joystick.getJoyStickX()*250,joystick.getJoyStickY()*250));
        
	}
	public void pause(){
	    
	}
	public void resume(){
	    
	}
}