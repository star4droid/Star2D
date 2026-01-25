package com.star4droid.template.Utils;

public class FPSCalc {
    //useless 
    //libgdx already have something to calculate the fps , I made this class because I don't have internet to search and I don't find the method...
	Long time=0L,fps=0L,frames=0L;
	public FPSCalc(){}
	public void update(){
		if(time==0L||Math.abs(time-System.currentTimeMillis())>=1000L){
			time=System.currentTimeMillis();
			fps=frames;
			frames = 0L;
		}
		frames++;
	}
	
	public Long getFPS(){
		return fps;
	}
	
	public float getFloatFPS(){
		return fps.floatValue();
	}
}