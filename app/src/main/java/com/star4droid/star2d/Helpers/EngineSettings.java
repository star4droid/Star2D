package com.star4droid.star2d.Helpers;

import android.content.Context;
import android.content.SharedPreferences;

public class EngineSettings {
	public static SharedPreferences sharedPreferences;
	public static void init(Context context){
			sharedPreferences = context.getSharedPreferences("prefs",Context.MODE_PRIVATE);
	}
	
	public static void set(String string,String value){
		sharedPreferences.edit().putString(string,value).commit();
	}
	
	public static void set(String string,int value){
		sharedPreferences.edit().putInt(string,value).commit();
	}
	
	public static void set(String string,float value){
		sharedPreferences.edit().putFloat(string,value).commit();
	}
	
	public static void set(String string,boolean value){
		sharedPreferences.edit().putBoolean(string,value).commit();
	}
	
	public static SharedPreferences get(){
		return sharedPreferences;
	}
}