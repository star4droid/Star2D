package com.star4droid.star2d.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.editor.items.EditorItem;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.template.Utils.ProjectAssetLoader;
import java.util.Locale;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.PropertySet;
import java.text.ParseException;
import java.text.NumberFormat;
import java.util.Random;

public class Utils {

		public static final String error_tag="star2d_Error";
		public static String seperator=".star2d.Seperator.";
		
		public static int getRandom(int _min, int _max) {
			Random random = new Random();
			return random.nextInt(_max - _min + 1) + _min;
		}
		
		public static double getDouble(String s) throws Exception {
			String str = replaceNonstandardDigits(s);
			try {
			return Double.parseDouble(str);
			} catch(Exception ex){
				String err1=getStackTraceString(ex);
				try {
				NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
				return nf.parse(str).doubleValue();
				} catch(ParseException e){
					throw new Exception(err1+"\n"+getStackTraceString(e));
					//return 0;
				}
			}
		}
		
		public static float getFloat(String s) throws Exception {
			String str = replaceNonstandardDigits(s);
			try {
				return Float.parseFloat(str);
				} catch(Exception ex){
					String err1=getStackTraceString(ex);
					try {
					NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
					return nf.parse(str).floatValue();
					} catch(ParseException e){
						throw new Exception(err1+"\n"+getStackTraceString(e));
					}
			}
		}
		
		public static int getInt(String s) throws Exception {
			String str = replaceNonstandardDigits(s);
			try {
				return Integer.parseInt(str);
				} catch(Exception ex){
					String err1=getStackTraceString(ex);
					try {
					NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
					return nf.parse(str).intValue();
					} catch (ParseException e){
						throw new Exception(err1+"\n"+getStackTraceString(e));
					}
			}
		}
		
		public static PropertySet<String,Object> getProperty(String s){
			PropertySet<String,Object> hash= new Gson().fromJson(s, new TypeToken<PropertySet<String, Object>>(){}.getType());
			return hash;
		}
		
		public static String replaceNonstandardDigits(String input) {
			if (input == null || input.isEmpty()) {
				//Log(error_tag,"empty string");
				return "0";
			}
			
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < input.length(); i++) {
				char ch = input.charAt(i);
				if (isNonstandardDigit(ch)) {
					int numericValue = Character.getNumericValue(ch);
					if (numericValue >= 0) {
						builder.append(numericValue);
					}
					} else {
					builder.append(ch);
				}
			}
			return builder.toString().replace(",",".").replace("٫",".");
		}
		
		private static boolean isNonstandardDigit(char ch) {
			return Character.isDigit(ch) && !(ch >= '0' && ch <= '9');
		}
		
		public static void update(Actor actor){
			Gdx.app.postRunnable(()->{
				if(actor instanceof EditorItem) ((EditorItem)actor).update();
			});
		}
		
		public static boolean isEditorItem(Actor v){
			return (v instanceof EditorItem);
		}
		
		public static String getStackTraceString(Throwable throwable){
		String full=throwable.toString();
			String space="";
			for(int x =0;x<12;x++)
				space+="_";
			for(StackTraceElement element:throwable.getStackTrace()){
				full+="class name : "+element.getClassName()+"\n file : "+element.getFileName()+
				"\n line number : "+element.getLineNumber()+"\n method : "+element.getMethodName()+"\n"+space+"\n";
			}
			return full;
	}
	
	public static void setImageFromFile(Image image,String path){
		setImageFromFile(image,path,-1,-1);
	}
	
	public static void setImageFromFile(Image image,String path,float rx,float ry){
		Gdx.app.postRunnable(()->{
			ProjectAssetLoader loader = (Editor.getCurrentEditor()!=null && Editor.getCurrentEditor().getLibgdxEditor()!=null)?Editor.getCurrentEditor().getLibgdxEditor().getAssetLoader():null;
			Texture editorTexture = (loader!=null&&loader.contains(path))?loader.get(path):null;
			FileHandle fileHandle = Gdx.files.absolute(path);
			TextureRegionDrawable drawable = new TextureRegionDrawable(editorTexture!=null?editorTexture:new Texture(fileHandle.exists()?fileHandle:Gdx.files.internal("images/logo.png")));
			if(rx > 1|| ry > 1){
				try {
		   	 	Texture texture = drawable.getRegion().getTexture();
		    		texture.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
		    		TextureRegion textureRegion = new TextureRegion(texture);
		    		textureRegion.setRegion(0,0,texture.getWidth()*rx,texture.getHeight()*ry);
		    		
					image.setDrawable(new TextureRegionDrawable(textureRegion));
		    	} catch(Exception ex){}
			} else {
				image.setDrawable(drawable);
			}
		});
	}
	
}