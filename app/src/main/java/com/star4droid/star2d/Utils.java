package com.star4droid.star2d;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowMetrics;
import android.view.Window;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.transition.Transition;
import android.widget.ImageView;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.evo.star2dApp;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import android.app.Activity;
import java.util.Random;
import java.util.zip.ZipOutputStream;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.EncryptionMethod;

public class Utils {

		public static final String error_tag="star2d_Error";
		public static HashMap<String,Object> propertiesMap;
		public static String seperator=".star2d.Seperator.";
		//useless...
		public static boolean isImplementingInterface(Object object, Class<?> interfaceClass) {
			Class<?>[] interfaces = object.getClass().getInterfaces();
			for (Class<?> implementedInterface : interfaces) {
				if (implementedInterface == interfaceClass) {
					return true;
				}
			}
			
			return false;
		}
		
		public static int getRandom(int _min, int _max) {
			Random random = new Random();
			return random.nextInt(_max - _min + 1) + _min;
		}
		
		public static int convertPixelsToDp(Context context,int pixels) {
		    /*
			float density = context.getResources().getDisplayMetrics().density;
		    int dp = (int) (pixels / density);
		    return dp;
			*/
			/*
			DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
			int dp = Math.round(pixels / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
			return dp;
			*/
			return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, pixels, context.getResources().getDisplayMetrics());
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
		
		public static String readAssetFile(String file,Context ctx){
			
			try {
				
				java.io.InputStream In = ctx.getAssets().open(file);
				int i = In.available();
				byte[] Bu = new byte[i];
				In.read(Bu);
				In.close();
				String s = new String(Bu, "UTF-8");
				return s;
				} catch(Exception e){
				Log(error_tag,getStackTraceString(e));
				return "";
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
		
		public static void log(String s,Context ctx){
		    int x=1;
		    while(FileUtil.isExistFile(FileUtil.getPackageDataDir(ctx)+"/logs/log"+x+".txt")){
					x++;
				}
			FileUtil.writeFile(FileUtil.getPackageDataDir(ctx)+"/logs/log"+x+".txt",s);
			Log(error_tag,s);
		}
		
		private static boolean isNonstandardDigit(char ch) {
			return Character.isDigit(ch) && !(ch >= '0' && ch <= '9');
			}
		
		public static class BodyType {
			public static String STATIC, DYNAMIC, UI, KINAMATIC;
		}
		
		public static void setupButton(View view){
			GradientDrawable ix3 = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT , new int[] {0xFFFF9D08 ,0xFFF15451});
			ix3.setCornerRadii(new float[] { 15, 15, 15, 15, 15,15,15,15 }); //LeftTop, //RightTop, //RightBottom, //LeftBottom,
			view.setBackground(ix3);
		}
		
		public static void setupIcon(View v){
			v.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(15, 0, 0xFFFFB300, 0xFF515151));
		}
		
		public static void setupCorner(View v){
			v.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(25, 0, 0xFFFFB300, 0xFF222222));
		}
		
		public static void update(Actor actor){
			if(actor instanceof EditorItem) ((EditorItem)actor).update();
		}
		
		public static void setCornerRadius(View v,int radius,int color){
			v.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b) { this.setCornerRadius(a); this.setColor(b); return this; } }.getIns(radius, color));
		}
		
		public static void setCornerRadiusWithStroke(View v,int radius,int stroke,int strokeColor,int bgcolor){
			v.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(radius, stroke, strokeColor, bgcolor));
		}
		
		public static boolean isEditorItem(Actor v){
			return (v instanceof EditorItem);
		}
		
		/*
		public static void setImageFromFile(ImageView imageView,String path){
			setImageFromFile(imageView,path,null,null,null);
		}
		*/
		
		public static void extractAssetFile(Context context,String file,String to) throws Exception {
			if(!FileUtil.isExistFile(to)) FileUtil.writeFile(to,"");
			int count;
			java.io.InputStream input= context.getAssets().open(file);
			java.io.OutputStream output = new  java.io.FileOutputStream(to);
			byte data[] = new byte[1024];
			while ((count = input.read(data))>0) {
				output.write(data, 0, count);
			}
			output.flush();
			output.close();
			input.close();
		}
		
		/*
		public static void setImageFromFile(final ImageView imageView,String _path,final Point repeat,final Point cut1,Point cut2){
			if(_path.equals("")) {
				imageView.setImageDrawable(imageView.getContext().getDrawable(R.drawable.icon));
				return;
			}
			while(_path.contains("//")) _path=_path.replace("//","/");
			if(_path.contains(seperator)) _path = _path.replace(seperator,"/");
			//Log.i(error_tag,"path : "+path);
			final String path =_path;
			//showMessage(imageView.getContext(),path);
			if(!FileUtil.isFile(path)) {
				imageView.setImageResource(R.drawable.icon);
				return;
			}
			//android.widget.Toast.makeText(imageView.getContext(),"exist : " + _path,2500).show();
			if(repeat==null&&cut1==null){
				java.io.File file = new java.io.File(path);
				Uri imageUri = Uri.fromFile(file);
				Glide.with(imageView.getContext()).asBitmap()
				.override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL, com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
				.priority(com.bumptech.glide.Priority.HIGH)
				.error(imageView.getContext().getDrawable(R.drawable.icon))
				.load(imageUri)
				.addListener(new RequestListener<Bitmap>(){
					@Override
					public boolean onLoadFailed(GlideException ex, Object arg1, Target<Bitmap> arg2, boolean arg3) {
						Log(error_tag,ex.getMessage());
					    return false;
					}

					@Override
					public boolean onResourceReady(Bitmap arg0, Object arg1, Target<Bitmap> arg2, DataSource arg3, boolean arg4) {
					    return false;
					}
				})
				.into(imageView);
			} else {
				Glide.with(imageView.getContext())
				.asBitmap()
				.addListener(new RequestListener<Bitmap>(){
					@Override
					public boolean onLoadFailed(GlideException ex, Object arg1, Target<Bitmap> arg2, boolean arg3) {
						Log(error_tag,ex.getMessage());
						return false;
					}
					
					@Override
					public boolean onResourceReady(Bitmap arg0, Object arg1, Target<Bitmap> arg2, DataSource arg3, boolean arg4) {
						return false;
					}
				})
				.override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL, com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
				.priority(com.bumptech.glide.Priority.HIGH)
				.load(Uri.fromFile(new java.io.File(path)))
				.into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
					@Override
					public void onLoadFailed(Drawable arg0) {
						imageView.setImageDrawable(imageView.getContext().getDrawable(R.drawable.icon));
						//android.widget.Toast.makeText(imageView.getContext(),"error",2500).show();
					}

					@Override
					public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> arg1) {
					if(bitmap == null) throw new RuntimeException("Bitmap returns null!!");
					//android.widget.Toast.makeText(imageView.getContext(),"ready : " + path,2500).show();
						if(cut1!=null){
							bitmap = cutBitmap(bitmap,cut1.x,cut1.y,cut2.x,cut2.y);
						}
						if(isEditorItem(imageView)){
							repeat.x = PropertySet.getPropertySet(imageView).getInt("tileX");
							repeat.y = PropertySet.getPropertySet(imageView).getInt("tileY");
						}
						if(repeat!=null){
							//Log("repeat_tag","rx : "+repeat.x+", ry : "+repeat.y+", path : "+Uri.parse(path).getLastPathSegment());
							if(repeat.x!=1 || repeat.y!=1){
								final Bitmap bm=bitmap;
								new Thread(){
									public void run(){
										final Bitmap bitmap=getRepeatedBitmap(bm,repeat.x,repeat.y);
										new Handler(Looper.getMainLooper()).post(()->{
											imageView.setImageBitmap(bitmap);
										});
									}
								}.start();
								return;
							}
						}
						//android.widget.Toast.makeText(imageView.getContext(),"set : " + path,2500).show();
						imageView.setImageBitmap(bitmap);
					}

					@Override
					public void onLoadCleared(Drawable arg0) {
					}
					
				});
			}
		}
		
		public static Bitmap cutBitmap(Bitmap bitmap,int x1, int y1,int x2,int y2){
			if(y2>bitmap.getHeight()) y2= bitmap.getHeight();
			if(x2>bitmap.getWidth()) x2=bitmap.getWidth();
			if(x1<0) x1=0;
			if(y1<0) y1=0;
			// Get the applicable width and height int width = x2 - x1;
			int height = Math.abs(y2 - y1);
			int width = Math.abs(x2-x1);
			if(height==0||width==0) return bitmap;
			if(height==bitmap.getHeight()&&width==bitmap.getWidth()) return bitmap;
			// Create a new bitmap from the original
			return Bitmap.createBitmap(bitmap, x1, y1, width, height);
		}
		
		public static Bitmap getRepeatedBitmap(Bitmap bitmap, int repeatX, int repeatY) {
			if(repeatX<=0) repeatX=1;
			if(repeatY<=0) repeatY=1;
			if(repeatX==1&&repeatY==1) return bitmap;
			int width = bitmap.getWidth() * repeatX;
			int height = bitmap.getHeight() * repeatY;
			int chunkWidth = bitmap.getWidth();
			int chunkHeight = bitmap.getHeight();
			
			Bitmap repeatedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			
			Canvas canvas = new Canvas(repeatedBitmap);
			Paint paint = new Paint();
			long tm=System.currentTimeMillis();
			for (int i = 0; i < repeatX; i++) {
				for (int j = 0; j < repeatY; j++) {
					int startX = i * chunkWidth;
					int startY = j * chunkHeight;
					int endX = startX + chunkWidth;
					int endY = startY + chunkHeight;
					
					Rect srcRect = new Rect(0, 0, chunkWidth, chunkHeight);
					Rect destRect = new Rect(startX, startY, endX, endY);
					
					canvas.drawBitmap(bitmap, srcRect, destRect, paint);
					
					if(repeatedBitmap.getByteCount()>50*1048576){
						//Log(error_tag,"too large : "+repeatedBitmap.getByteCount());
						return repeatedBitmap;
					}
				}
			}
			
			return repeatedBitmap;
		}
		*/
		
		public static String getTag(View view){
			if(view.getTag()==null) return "";
			try {
			if(view.getTag() instanceof String){
				return view.getTag().toString();
			}
			} catch (Exception exception){}
				return "";
		}
		
		
		public static AlertDialog showMessage(Context context,String message){
			AlertDialog dialog= showMessage(context,message,true);
			return dialog;
		}
		
		public static AlertDialog showMessage(Context context,String message,boolean show){
			TextView text = new TextView(context);
			text.setText(message);
			text.setTextColor(Color.WHITE);
			text.setPadding(8,8,8,8);
			ScrollView scroll = new ScrollView(context);
			text.setTextIsSelectable(true);
			scroll.addView(text);
			//setCornerRadiusWithStroke(text,0,2, 0xFFFFB300, 0xFF3C3C3C);
			AlertDialog dialog = new AlertDialog.Builder(context).create();
			dialog.getWindow().setBackgroundDrawable(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(0, 3, 0xFFFFB300, 0xFF3C3C3C));
			dialog.setView(scroll);
			dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			hideSystemUi(dialog.getWindow());
			if(show) dialog.show();
			return dialog;
		}
		
		public static AlertDialog updateMessage(AlertDialog dialog,String message,boolean cancel){
			if(dialog!=null){
				dialog.setCancelable(cancel);
				if(dialog.isShowing()) dialog.dismiss();
			}
			dialog = showMessage(dialog.getContext(),message,false);
			dialog.setCancelable(cancel);
			dialog.show();
			return dialog;
		}
		
		public static void zipf(String path,String to,String password) throws ZipException {
			//FileUtil.deleteFile(to);
			if(!FileUtil.isExistFile(to)) createEmptyZipFile(to);
			ZipParameters zipParameters = new ZipParameters();
			zipParameters.setEncryptFiles(true);
			zipParameters.setCompressionLevel(CompressionLevel.FASTER);
			zipParameters.setEncryptionMethod(EncryptionMethod.AES);
			zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
			zipParameters.setCompressionMethod(CompressionMethod.STORE);
			ZipFile zipFile=null;
			if(password.equals(""))
				zipFile = new ZipFile(to);
					else
						zipFile = new ZipFile(to, password.toCharArray());
			if(FileUtil.isDirectory(path))
				zipFile.addFolder(new java.io.File(path));
					else if(password.equals("")) 
						zipFile.addFile(new java.io.File(path));
							else zipFile.addFile(new java.io.File(path), zipParameters);
		}
		
		public static void createEmptyZipFile(String filePath) {
		    FileUtil.writeFile(filePath,"");
			try {
		    	Utils.extractAssetFile(com.star4droid.star2d.evo.star2dApp.getContext(),"files/empty.zip",filePath);
		    } catch (Exception ex){
				
			}
		    /*
		    try {
		        extractAssetFile(getContext(),"files/empty.zip",filePath);
		    } catch(Exception ex){
		        Log("empty zip creation error, "+ex.toString(),getStackTraceString(ex));
		    }
			*/
			
			/*
			try {
				java.io.File file = new java.io.File(filePath);
				
				// Create parent directories if they don't exist
				file.getParentFile().mkdirs();
				
				// Create a new empty zip file
				ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(file));
				zipOutputStream.close();
				} catch (java.io.IOException e) {
				e.printStackTrace();
				Log("empty zip creation error, "+e.toString(),getStackTraceString(e));
			}
			*/
		}
		
		public static void zipFolderContents(String folder,String target,String password) throws Exception {
			ArrayList<String> arrayList= new ArrayList<>();
			FileUtil.listDir(folder,arrayList);
            //FileUtil.deleteFile(target);
			for(String path:arrayList){
				zipf(path,target,password);
			}
		}
		
		public static void unzipf(String path,String to,String password) throws Exception {
			if(new ZipFile(path).isEncrypted()) new ZipFile(path,password.toCharArray()).extractAll(to);
				else new ZipFile(path).extractAll(to);
		}
		
		public void removeEntryFromZip(String entry,String zip,String password) throws Exception {
				if(password.equals(""))
					new ZipFile(zip).removeFile(entry);
						else new ZipFile(zip,password.toCharArray()).removeFile(entry);
		}
		
		public static ArrayList<String> getArrayList(int code, int result, Intent data,Context context) {
					ArrayList<String> _filePath = new ArrayList<>();
					if (data != null) {
						if (data.getClipData() != null) {
							for (int _index = 0; _index < data.getClipData().getItemCount(); _index++) {
								ClipData.Item _item = data.getClipData().getItemAt(_index);
								_filePath.add(FileUtil.convertUriToFilePath(context, _item.getUri()));
							}
						}
						else {
							_filePath.add(FileUtil.convertUriToFilePath(context, data.getData()));
						}
					}
					return _filePath;
		}
		
		public static void unzipf(InputStream inputStream, String destinationPath,String password) throws Exception {
				// Create a temporary file to write the input stream
				File tempFile = File.createTempFile("temp", ".zip");
				
				// Write the input stream to the temporary file
				FileOutputStream fos = new FileOutputStream(tempFile);
				byte[] buffer = new byte[1024];
				int length;
				while ((length = inputStream.read(buffer)) > 0) {
					fos.write(buffer, 0, length);
				}
				fos.close();
				
				//unzip
				unzipf(tempFile.getAbsolutePath(),destinationPath,password);
				// Delete the temporary file
				tempFile.delete();
		}
		
		public static void saveFileToPath(Uri sourceUri, Uri target,Context context) throws Exception {
			// Copy the file from the source URI to the target path
			InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
			OutputStream outputStream = context.getContentResolver().openOutputStream(target);//new FileOutputStream(targetPath);
			FileUtil.writeFile(target.getPath(),"");
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			
			outputStream.close();
			inputStream.close();
			// File saved successfully
		}
		
		public static void saveFile(String fileName,ActivityResultLauncher saveFile) {
			Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("*/*");
			intent.putExtra(Intent.EXTRA_TITLE, fileName);
			
			saveFile.launch(intent);
		}
		
		public static void hideSystemUi(Window window){
			if(window==null||Build.VERSION.SDK_INT<=29) return;
			View decorView = window.getDecorView();
			if(Build.VERSION.SDK_INT>=30){
				WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(window, decorView);
				windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
				windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			} else hideSystemUi(decorView);
			
		}
		
		public static void hideSystemUi(View decorView){
			if(Build.VERSION.SDK_INT<=29) return;
			if(Build.VERSION.SDK_INT<30){
				int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
				decorView.setSystemUiVisibility(uiOptions);
				} else {
				WindowInsetsController windowInsetsController= decorView.getWindowInsetsController();
				windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
				windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			}
		}
		
		public static String removeLastFromPath(final String _path) {
			String st="";
			ArrayList<String> lsts = new ArrayList<>();
			try {
				String lines[] = _path.concat("/").split("/");
				for(String line: lines) {
					lsts.add(line);
				}
			lsts.remove(lsts.size() - 1);
			for (String hh:lsts)   {
				if (FileUtil.isDirectory(hh)) {
					if (st=="")  {
						st = hh;
						} else {
						st = st +"/"+hh;
					}
				}
				else {
					if (st=="")  {
						st = hh;
						} else {
						st = st +"/"+hh;
					}
				}
			}
			return st;
			} catch(Exception ex){}
			return _path;
		}
		
		public String getApkPath(Context context){
			try {
			android.content.pm.PackageInfo pi = context.getPackageManager().getPackageInfo(context.getApplicationContext().getPackageName(), android.content.pm.PackageManager.GET_ACTIVITIES);
			return pi.applicationInfo.publicSourceDir;
			} catch(Exception ex){
				showMessage(context,"Error getting apk path : "+ex.toString());
			}
			return "";
		}
		
		public static int getScreenWidth(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above, use WindowMetrics to get accurate screen width
            WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
            Insets insets =
                    windowMetrics
                            .getWindowInsets()
                            .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            return windowMetrics.getBounds().width() - insets.left - insets.right;
        } else {
            // For older Android versions, use DisplayMetrics
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.widthPixels;
        }
    }
      public static int getColorAttr(Context c,int attr){
    TypedValue tv=new TypedValue();
    c.getTheme().resolveAttribute(attr,tv,true);
    return tv.data;
  }
 	public boolean isDarkModeEnabled(Context context) {
		 // Check if the device supports dark mode
		 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			 // Get the current configuration
			 Configuration configuration = context.getResources().getConfiguration();
			 
			 // Check if night mode is enabled
			 int nightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
			 return nightMode == Configuration.UI_MODE_NIGHT_YES;
			 } else {
			 // Devices older than Android Q (API 29) don't support dark mode
			 return false;
		 }
	 }
	
	public static void setLanguage(Context context){
		if(EngineSettings.get()==null) EngineSettings.init(context);
		String lng=EngineSettings.get().getString("lang",""),
		local=EngineSettings.get().getString("local","");
		if(!lng.equals("")){
			java.util.Locale locale = new java.util.Locale(lng);
			java.util.Locale.setDefault(locale);
			android.content.res.Resources resources = context.getResources();
			android.content.res.Configuration config = resources.getConfiguration();
			config.setLocale(locale);
			resources.updateConfiguration(config, resources.getDisplayMetrics());
		}
		AppCompatDelegate.setDefaultNightMode(EngineSettings.get().getBoolean("night",false)?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);
	}
	
	public static void Log(String error,String string){
		int x=0;
		String str=star2dApp.getContext().getExternalFilesDir(null)+"/logs/log"+"%1$s"+".txt";
		while(FileUtil.isExistFile(star2dApp.getContext().getExternalFilesDir(null)+"/logs/log"+x+".txt")){
		    if(FileUtil.readFile(String.format(str,x+"")).equals(error+" :\n"+string))
		    break;
			x++;
		}
		FileUtil.writeFile(String.format(str,x+""),error+" :\n"+string);
	}
		
		public static String getStackTraceString2(Throwable throwable){
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
		
	public static String getStackTraceString(Throwable throwable){
			//disabled, because I think it lag the app....
			if(true) return throwable.toString();
			String full="";
			String space="";
			for(int x =0;x<12;x++)
				space+="_";
			for(StackTraceElement element:throwable.getStackTrace()){
				full+="class name : "+element.getClassName()+"\n file : "+element.getFileName()+
				"\n line number : "+element.getLineNumber()+"\n method : "+element.getMethodName()+"\n"+space+"\n";
			}
			return full;
	}
	
	/**
     * Moves a folder from sourcePath to destinationPath.
     *
     * @param sourcePath      The path of the folder to be moved.
     * @param destinationPath The path to move the folder to.
     * @return true if the move operation was successful, false otherwise.
     */
    public static boolean moveFolder(String sourcePath, String destinationPath) {
        File sourceFolder = new File(sourcePath);
        File destinationFolder = new File(destinationPath);

        // Validate source folder
        if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
            return false; // Source folder doesn't exist or isn't a folder
        }

        // Create destination folder if it doesn't exist
        if (!destinationFolder.exists()) {
            if (!destinationFolder.mkdirs()) {
                return false; // Failed to create destination folder
            }
        }

        // Move files and subfolders recursively
        File[] files = sourceFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(destinationFolder, file.getName());
                if (file.isDirectory()) {
                    // Recursive call for subdirectories
                    if (!moveFolder(file.getAbsolutePath(), destFile.getAbsolutePath())) {
                        return false;
                    }
                } else {
                    // Move file
                    if (!file.renameTo(destFile)) {
                        return false;
                    }
                }
            }
        }

        // Delete the source folder after moving its contents
        return true;// sourceFolder.delete();
    }
}