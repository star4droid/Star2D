package com.star4droid.Game;

import com.badlogic.gdx.graphics.Color;
import java.util.Calendar;
import java.util.GregorianCalendar;
/*
	class from akatsuki project from github
*/
// TODO : modify to work as game time with save and load...
public class GdxTime {
	
	private int hour;
	private int min;
	private int sec;
	private int scale;
	private Calendar cal;
	
	private GdxTime(Calendar cal,int scale) {
		hour = cal.get(Calendar.HOUR_OF_DAY);
		min = cal.get(Calendar.MINUTE);
		sec = cal.get(Calendar.SECOND);
		this.cal = cal;
		this.scale = scale;
	}
	
	public int getHour() {
		return  hour;
	}
	
	public int getMin() {
		return min;
	}
	
	public int getSec(){
		return sec;
	}
	
	/**
	* Gets the current in game time based on the given scale
	* @param scale the scale used to calculate the in game time. For example,
	* If 1 real world hour == 3 in game hours, then the scale would be 3.
	* @return the current time in the game world
	*/
	public static GdxTime getTime(int scale) {
		Calendar cal = new GregorianCalendar();
		cal.setTimeInMillis(System.currentTimeMillis() * scale);
		
		return new GdxTime(cal,scale);
	}
	
	/***
	* Convenience method for getting the current in game time with a scale of 1. This is
	* the same as getting the current real world time.
	* @return the current time in the real world.
	*/
	public static GdxTime getTime() {
		return getTime(1);
	}
	
	public GdxTime update(){
		cal.setTimeInMillis(System.currentTimeMillis() * scale);
		hour = cal.get(Calendar.HOUR_OF_DAY);
		min = cal.get(Calendar.MINUTE);
		sec = cal.get(Calendar.SECOND);
		return this;
	}
	
	/**
	* Calculates the tint to be used for 2d rendering based on the time
	* NOTE: Assumes a 24hr day.
	* @return the tent to be used for 2d rendering.
	*/
	public Color getTint() {
		
		/*Credit goes to Ted Larue for this algorithm. Thanks Ted!*/
		double z = Math.cos((hour-14) * Math.PI / 12);
		float b = (float) (0.3f + 0.7f * (z + 1.0) / 2.0);
		
		return new Color(b, b, b, 1.0f);
	}
}