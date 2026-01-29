package com.star4droid.star2d.editor.ui.scripting;

public class FieldSuggestionList {
public static String[] bodies;
public static String[] scenes;
public static String[] sounds;
public static String[] animations;
public static String[] files;
public static String[] images;
public static String[] joints;
public static String[] provideListForType(String type){
    if(type.contains("(")){
    String parse=type.substring(type.indexOf("(")+1,type.indexOf(")"));
        switch(parse){
            case "image":
                return FieldSuggestionList.images;
            case "Body": return FieldSuggestionList.bodies;
            case "scene": return FieldSuggestionList.scenes;
            case "sound": return FieldSuggestionList.sounds;
            case "animation":return FieldSuggestionList.animations;
            case "file": return FieldSuggestionList.files;
            case "joint": return FieldSuggestionList.joints;
            case "Boolean" : return new String[] {"true","false"};
            default: return null;
        }
    } else{
        return null;
     }
    
}
}
