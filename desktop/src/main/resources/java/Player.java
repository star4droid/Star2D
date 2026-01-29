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
//import .....script_here;

public class %1$s extends StageImp {
    %2$s
    @Override
    public void onCreate(){
%3$s
    }
    @Override
    public void onPause(){
%4$s
    }
    @Override
    public void onResume(){
%5$s
    }
    
    @Override
    public void onDraw(){
%6$s
    }
    @Override
    public void onCollisionBegin(PlayerItem body1,PlayerItem body2){
%7$s
	}
	@Override
	public void onCollisionEnd(PlayerItem body1,PlayerItem body2){
%8$s
	}
	//scripts goes here ...
	%9$s
}