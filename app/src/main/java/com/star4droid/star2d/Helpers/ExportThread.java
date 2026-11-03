package com.star4droid.star2d.Helpers;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import axml.xml.AxmlUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.star4droid.star2d.Utils;
import com.star4droid.star2d.evo.R;
import java.util.ArrayList;
import java.util.Random;
import net.lingala.zip4j.ZipFile;
import com.star4droid.star2d.evo.BuildConfig;

public class ExportThread extends Thread {
	Context context;
	String apk_path ="/apk/template.apk";
	Uri export_path;
	String project;
	String dataDir;
	String export_temp;
	String[] manifest_values;
	AlertDialog dialog;
	String img;
	String state_prefix="";
	/** Export option: pack the small images into a few tileset pages (see TilesetPacker). */
	public static boolean mergeTilesets=false;
	/** Leave out the x86 / x86_64 libraries (phones are ARM): a smaller APK. */
	public static boolean armOnly=false;
	public static String TEMPLATE_NOT_FOUND="Template apk not found!";
	public ExportThread(Context ctx,String p,Uri savePath,String imgPath,String[] manifestV){
		context = ctx;
		dialog = new AlertDialog.Builder(context).create();
		//temp2 = FileUtil.getPackageDataDir(context)+export_path;
		apk_path = FileUtil.getPackageDataDir(context)+apk_path;
		project = p;
		export_path = savePath;
		dataDir = FileUtil.getPackageDataDir(context);
		export_temp = dataDir+"/exportTemp/";
		manifest_values = manifestV;
		try {
			java.io.File[] files = new java.io.File(project+"/icon/").listFiles();
			if(files != null && files.length > 0)
				for(java.io.File file:files){
					String name = file.getName().toLowerCase();
					if(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")){
						img = file.getAbsolutePath();
						break;
					}
				}
		} catch(Exception ex){}
		//img = imgPath;
	}
	
	@Override
	public void run(){
		Looper.prepare();
		state_prefix="";
		try {
			/*
            if(!FileUtil.isExistFile(apk_path)){
				post(new Runnable(){
					@Override
					public void run(){
						onError(TEMPLATE_NOT_FOUND);
					}
				});
				return;
			}
            */
			onProgress(com.star4droid.star2d.ui.Tr.t("export_p_prepare","Preparing the project..."));
			
			FileUtil.deleteFile(export_temp+"/assets");
			onProgress(com.star4droid.star2d.ui.Tr.t("export_p_extract","Extracting the template files..."));
			//if(!FileUtil.isExistFile(dataDir+"/export.apk")) {
				FileUtil.writeFile(dataDir+"/export.apk","");
				Utils.extractAssetFile(context,"files/template.zip",dataDir+"/export.apk");
                //Utils.unzipf(dataDir+"/export.apk",export_temp,"");
			//}
			
			onProgress(com.star4droid.star2d.ui.Tr.t("export_p_prepare","Preparing the project..."));
			
			Utils.extractAssetFile(context,"files/empty.zip",export_temp+"/assets/project.zip");
			String source = project;
			if(mergeTilesets){
				// work on a copy: the project itself is never changed
				onProgress(com.star4droid.star2d.ui.Tr.t("export_p_merge","Merging images into tilesets..."));
				// the copy keeps the project's folder name: it is the root folder of the zip and the game looks for it
				String copyRoot = dataDir+"/exportProject";
				FileUtil.deleteFile(copyRoot);
				String copy = copyRoot+"/"+new java.io.File(project).getName();
				FileUtil.copyDir(project,copy);
				try { TilesetPacker.pack(new java.io.File(copy),null); source = copy; } catch(Throwable t){ source = project; }
			}
			Utils.zipf(source,export_temp+"/assets/project.zip","tulsgskdiensl626__Xxmoishs");
			if(!source.equals(project)) FileUtil.deleteFile(dataDir+"/exportProject");
			try {
				ZipFile zipFile=new ZipFile(export_temp+"/assets/project.zip","tulsgskdiensl626__Xxmoishs".toCharArray());
				zipFile.removeFile("Events");
				zipFile.removeFile("java");
				zipFile.removeFile("scripts");
                zipFile.removeFile("log");
			} catch(Exception exception){}
			String manifest = String.format(Utils.readAssetFile("java/manifest.xml",context),(Object[])manifest_values);
			FileUtil.writeFile(export_temp+"/manifest",manifest);
			if (img != null && !img.isEmpty() && FileUtil.isExistFile(img)) {
            // The template's resources.arsc points at res/drawable/icon.jpg, so the custom icon must be stored under
            // exactly that name whatever its real format (Android decodes images by content, not by extension).
            String destinationPath = export_temp + "/res/drawable/icon.jpg";
        
            // Delete the placeholder file
            FileUtil.deleteFile(export_temp + "/res/drawable/icon.jpg");
        
            // Copy the file to the new destination with the correct extension
            FileUtil.copyFile(img, destinationPath);
        }

			FileUtil.writeFile(export_temp+"/assets/version",""+new Random().nextInt(9999999));
			addScenesDex();
			onProgress(com.star4droid.star2d.ui.Tr.t("export_p_manifest","Writing the manifest..."));
			AxmlUtil.encodeFile(export_temp+"/manifest",export_temp+"/AndroidManifest.xml");
			FileUtil.moveFile(export_temp+"/manifest",dataDir+"/manifest.debug.xml");
			onProgress(com.star4droid.star2d.ui.Tr.t("export_p_build","Building the APK..."));
			//FileUtil.copyFile(apk_path,dataDir+"/export.apk");
			
			Utils.zipFolderContents(export_temp,dataDir+"/export.apk","");
			stripNatives(dataDir+"/export.apk");
			onProgress(com.star4droid.star2d.ui.Tr.t("export_p_sign","Signing..."));
			
			FileUtil.writeFile(dataDir+"/export.sign.apk","");
			SignUtil.sign(context,dataDir+"/export.apk",dataDir+"/export.sign.apk");
			onProgress(com.star4droid.star2d.ui.Tr.t("export_p_move","Saving to the chosen place..."));
			//UriUtils.deleteUri(context,export_path);
			// an existing file chosen to overwrite is emptied first and cut to the new size (no broken APK)
			UriUtils.copyUriToUriChecked(context,Uri.fromFile(new java.io.File(dataDir+"/export.sign.apk")),export_path);
			//FileUtil.deleteFile(dataDir+"/export.apk");
			FileUtil.deleteFile(dataDir+"/export.sign.apk");
			onSuccess(com.star4droid.star2d.ui.Tr.t("export_p_done","Done")+"\n"+com.star4droid.star2d.ui.Tr.t("export_p_path","Path")+" : "+FileUtil.convertUriToFilePath(context,export_path));
		} catch(Exception ex){
			onError(Log.getStackTraceString(ex));
		}
	}
	
	/**
	 * Puts the compiled scenes (dex/scenes.dex) in the APK as classes(N+1).dex, N = the last classes*.dex of the template, so the game
	 * loads its scene classes straight from the APK. Without a dex the game keeps using the one inside project.zip.
	 */
	private void addScenesDex(){
		try {
			java.io.File dex = new java.io.File(project+"/dex/scenes.dex");
			java.io.File[] old = new java.io.File(export_temp).listFiles();
			if(old != null) for(java.io.File f : old) if(f.getName().matches("classes\\d*\\.dex")) f.delete();
			if(!dex.isFile() || dex.length() == 0) return;
			int last = 1;
			java.util.regex.Pattern pat = java.util.regex.Pattern.compile("classes(\\d*)\\.dex");
			for(Object h : new ZipFile(dataDir+"/export.apk").getFileHeaders()){
				java.util.regex.Matcher m = pat.matcher(((net.lingala.zip4j.model.FileHeader) h).getFileName());
				if(m.matches()) last = Math.max(last, m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1)));
			}
			FileUtil.copyFile(dex.getAbsolutePath(), export_temp+"/classes"+(last+1)+".dex");
			onProgress("Scenes added as classes"+(last+1)+".dex");
		} catch(Throwable t){
			onProgress("Scenes dex not added to the APK : "+t);
		}
	}
	
	/** Smaller APK: no Bullet (3D physics) library when the project has no 3D scene, no x86 libraries when asked. */
	private void stripNatives(String apk){
		try {
			boolean has3D = false;
			java.io.File[] cfgs = new java.io.File(project+"/configs").listFiles();
			if(cfgs != null) for(java.io.File f : cfgs){
				String t = FileUtil.readFile(f.getAbsolutePath());
				if(t != null && t.replace(" ","").contains("\"sceneType\":\"3D\"")) { has3D = true; break; }
			}
			ZipFile z = new ZipFile(apk);
			java.util.ArrayList<String> rm = new java.util.ArrayList<>();
			for(Object o : z.getFileHeaders()){
				String n = ((net.lingala.zip4j.model.FileHeader) o).getFileName();
				if(!n.startsWith("lib/")) continue;
				if(!has3D && n.endsWith("libgdx-bullet.so")) rm.add(n);
				else if(armOnly && (n.startsWith("lib/x86/") || n.startsWith("lib/x86_64/"))) rm.add(n);
			}
			if(!rm.isEmpty()) z.removeFiles(rm);
			if(!rm.isEmpty()) onProgress("Left out "+rm.size()+" native libraries"+(has3D?"":" (no 3D scene: no Bullet)"));
		} catch(Throwable t){
			onProgress("Native libraries kept: "+t);
		}
	}

	public void onSuccess(final String message){
		post(()->{
			if(dialog!=null&&dialog.isShowing()) dialog.dismiss();
			MaterialAlertDialogBuilder alertD=new MaterialAlertDialogBuilder(context);
			alertD.setMessage(message);
			alertD.setCancelable(false);
			alertD.setNegativeButton(com.star4droid.star2d.ui.Tr.t("export_cancel","Cancel"),(dl,which)->{
				
			});
			alertD.setPositiveButton(com.star4droid.star2d.ui.Tr.t("export_install","Install"),(dl,which)->{
			    android.os.StrictMode.setVmPolicy(new
			android.os.StrictMode.VmPolicy.Builder().build());
			if(android.os.Build.VERSION.SDK_INT>=24){
							try{
											java.lang.reflect.Method
											m=android.os.StrictMode.class.getMethod(
											"disableDeathOnFileUriExposure");
											m.invoke(null);
							}
							catch(Exception e) {
											//showMessage(e.toString());
							}
			}
				ApkInstaller.installApplication(context,export_path);
			});
			alertD.create().show();
		});
	}
	
	public void onError(final String message){
		post(()->{
			dialog = Utils.updateMessage(dialog,message,true);
		});
	}
	
	public void onProgress(final String status){
		state_prefix+=">";
		post(()->{
			dialog = Utils.updateMessage(dialog,state_prefix+" "+status,false);
		});
	}
	
	public String getApkPath(){
		return apk_path;
	}
	
	public void post(Runnable runnable){
		new Handler(Looper.getMainLooper()).post(runnable);
	}
}