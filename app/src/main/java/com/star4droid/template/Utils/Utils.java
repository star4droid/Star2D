package com.star4droid.template.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.star4droid.star2d.evo.star2dApp;
import java.io.FileReader;
import java.text.ParseException;
import java.text.NumberFormat;
import java.util.Locale;

public class Utils {
		public static String error_tag="star2d_error",seperator=".star2d.Seperator.";
		
		public static Drawable getDrawable(FileHandle fileHandle){
			return new TextureRegionDrawable(new TextureRegion(new Texture(fileHandle)));
		}
		
		public static Drawable getDrawable(Texture texture){
			return new TextureRegionDrawable(new TextureRegion(texture));
		}
		
		public static FileHandle absolute(String file){
			FileHandle fileHandle = Gdx.files.absolute(file);
			return (fileHandle.exists()?fileHandle:Gdx.files.internal("images/logo.png"));
		}
		
		public static FileHandle internal(String file){
			return Gdx.files.internal(file);
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
		
		public static FileHandle external(String file){
			FileHandle fileHandle = Gdx.files.external(file);
			return fileHandle.exists()?fileHandle:Gdx.files.internal("images/logo.png");
		}
		
		public static void Log(String str){
			Gdx.app.log("message",str);
		}
		
		public static void extractAssetFile(String file,String to) throws Exception {
			FileHandle fileHandle = Gdx.files.absolute(file);
			if(!fileHandle.exists()) fileHandle.writeString("",false);
			int count;
			java.io.InputStream input= fileHandle.read();
			java.io.OutputStream output = new  java.io.FileOutputStream(to);
			byte data[] = new byte[1024];
			while ((count = input.read(data))>0) {
				output.write(data, 0, count);
			}
			output.flush();
			output.close();
			input.close();
		}
		
		public static boolean isExists(String file){
			return new java.io.File(file).exists();
		}
		
		public static void Log(String error,String string){
		/*
			Gdx.app.log(error,string);
			int n=1;
			while(Gdx.files.external("Logs"+n+".txt").exists()) n++;
			Gdx.files.external("Logs"+n+".txt").writeString(error+"\n"+string,false);
			*/
			com.star4droid.star2d.Utils.log(error+"\n"+string,star2dApp.getContext());
		}
		
		public static String getStackTraceString(Throwable throwable){
			String full=throwable.toString()+"\n";
			String space="";
			for(int x =0;x<12;x++)
				space+="_";
			for(StackTraceElement element:throwable.getStackTrace()){
				full+="class name : "+element.getClassName()+"\n file : "+element.getFileName()+
				"\n line number : "+element.getLineNumber()+"\n method : "+element.getMethodName()+"\n"+space+"\n";
			}
			return full;
		}
		
		public static String readFile(String path) {
		if(!isExists(path)) return "";
		
		StringBuilder sb = new StringBuilder();
		FileReader fr = null;
		try {
			fr = new FileReader(new java.io.File(path));
			
			char[] buff = new char[1024];
			int length = 0;
			
			while ((length = fr.read(buff)) > 0) {
				sb.append(new String(buff, 0, length));
			}
			} catch (java.io.IOException e) {
			e.printStackTrace();
			} finally {
			if (fr != null) {
				try {
					fr.close();
					} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		return sb.toString();
	}
}