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
import com.tyron.code.util.ApkInstaller;
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
			onProgress("> Preparing the project");
			
			FileUtil.deleteFile(export_temp+"/assets");
			onProgress("Extracting template files");
			//if(!FileUtil.isExistFile(dataDir+"/export.apk")) {
				FileUtil.writeFile(dataDir+"/export.apk","");
				Utils.extractAssetFile(context,"files/template.zip",dataDir+"/export.apk");
                //Utils.unzipf(dataDir+"/export.apk",export_temp,"");
			//}
			
			onProgress("Preparing the project...");
			
			Utils.extractAssetFile(context,"files/empty.zip",export_temp+"/assets/project.zip");
			Utils.zipf(project,export_temp+"/assets/project.zip","tulsgskdiensl626__Xxmoishs");
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
            // Get the file extension from the original image path
            String ext = img.substring(img.lastIndexOf(".") + 1);
        
            // Construct the destination path with the correct extension
            String destinationPath = export_temp + "/res/drawable/icon." + ext;
        
            // Delete the placeholder file
            FileUtil.deleteFile(export_temp + "/res/drawable/icon.jpg");
        
            // Copy the file to the new destination with the correct extension
            FileUtil.copyFile(img, destinationPath);
        }

			FileUtil.writeFile(export_temp+"/assets/version",""+new Random().nextInt(9999999));
			onProgress("Manifest encoding...");
			AxmlUtil.encodeFile(export_temp+"/manifest",export_temp+"/AndroidManifest.xml");
			FileUtil.moveFile(export_temp+"/manifest",dataDir+"/manifest.debug.xml");
			onProgress("Building APK...");
			//FileUtil.copyFile(apk_path,dataDir+"/export.apk");
			
			Utils.zipFolderContents(export_temp,dataDir+"/export.apk","");
			onProgress("Signing...");
			
			FileUtil.writeFile(dataDir+"/export.sign.apk","");
			SignUtil.sign(context,dataDir+"/export.apk",dataDir+"/export.sign.apk");
			onProgress("Moving to the selected path...");
			//UriUtils.deleteUri(context,export_path);
			UriUtils.copyUriToUri(context,Uri.fromFile(new java.io.File(dataDir+"/export.sign.apk")),export_path);
			//FileUtil.deleteFile(dataDir+"/export.apk");
			FileUtil.deleteFile(dataDir+"/export.sign.apk");
			onSuccess("Done \n path : "+FileUtil.convertUriToFilePath(context,export_path));
		} catch(Exception ex){
			onError(Log.getStackTraceString(ex));
		}
	}
	
	public void onSuccess(final String message){
		post(()->{
			if(dialog!=null&&dialog.isShowing()) dialog.dismiss();
			MaterialAlertDialogBuilder alertD=new MaterialAlertDialogBuilder(context);
			alertD.setMessage(message);
			alertD.setCancelable(false);
			alertD.setNegativeButton(context.getString(R.string.cancel),(dl,which)->{
				
			});
			alertD.setPositiveButton(context.getString(R.string.install),(dl,which)->{
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