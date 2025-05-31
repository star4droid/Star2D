package com.star4droid.Game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.Timer;
import com.star4droid.star2d.ElementDefs.*;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.template.Items.*;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ProjectAssetLoader;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import box2dLight.*;
import box2dLight.RayHandler;


public class scene2 extends StageImp {
    PlayerItem Box1;


    @Override
    public void onCreate(){

		BoxDef Box1_def = new BoxDef();
			Box1_def.Collider_Width=50f;
			Box1_def.Script="Box1";
			Box1_def.Collider_Height=50f;
			Box1_def.x=196.28128f;
			Box1_def.name="Box1";
			Box1_def.y=135.88702f; 
		
			Box1_def.elementEvents = new ElementEvent(){
            @Override
			public void onClick(PlayerItem current) {
			
			}

			@Override
			public void onTouchStart(PlayerItem current,InputEvent event) {

			}

			@Override
			public void onTouchEnd(PlayerItem current, InputEvent event) {

			}

			@Override
			public void onBodyCreated(PlayerItem current) {

			}

            @Override
            public void onBodyUpdate(PlayerItem current){

            }
            
			@Override
			public void onCollisionBegin(PlayerItem current, PlayerItem body2) {

           }
            
			@Override
			public void onCollisionEnd(PlayerItem current, PlayerItem body2) {

			}
			
			@Override
			public String getName(){
			    return "Box1";
			} 
};
	Box1 = (PlayerItem)(Box1_def.build(this));


         Box1.getActor().setZIndex((int)(Box1_def.z));

    }
    @Override
    public void onPause(){

    }
    @Override
    public void onResume(){

    }
    
    @Override
    public void onDraw(){

    }
    @Override
    public void onCollisionBegin(PlayerItem body1,PlayerItem body2){

	}
	@Override
	public void onCollisionEnd(PlayerItem body1,PlayerItem body2){

	}
	//scripts goes here ...
	
}