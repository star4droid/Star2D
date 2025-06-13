package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.I18NBundle;
import com.star4droid.star2d.editor.TestApp;
import java.util.HashMap;
import java.util.Locale;

public class Lang {
	/*
		Language Manager Class...
	*/
	private static I18NBundle currentLang = null;
	private static HashMap<String,I18NBundle> map = new HashMap<>();
	public static void loadTrans(String lang){
		String key = lang.equals("ar") ? lang : "en";
		if(map.containsKey(key)){
			currentLang = map.get(key);
			return;
		}
		currentLang = I18NBundle.createBundle(Gdx.files.internal("i18n/"+(lang.equals("ar") ? "strings_ar_SD" : "strings_en_GB")),lang.equals("ar") ? new Locale("ar","SD") : Locale.UK);
		map.put(key,currentLang);
	}
	
	public static String getTrans(String name){
		try {
			String firstChar = String.valueOf(name.charAt(0));
    	    boolean needModify = (firstChar.equals(firstChar.toUpperCase()) && !firstChar.equals("")) || name.contains(" ");
    	    if(!needModify)
    	        return currentLang.get(name);
    		String newName = name.replace(" ","");
    		newName = String.valueOf(newName.charAt(0)).toLowerCase() + newName.substring(1,newName.length());
			return currentLang.get(newName);
		} catch(Exception | Error e){
			return name;
		}
	}
	
	public static boolean isRTL(){
		return TestApp.getCurrentApp().preferences.getString("lang","en").equals("ar");
	}
}