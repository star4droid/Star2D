package com.star4droid.star2d.Adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.star4droid.star2d.Helpers.ExportThread;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;

public class ExportDialog {
	public static ImageView icon;
	public static int RECIEVE_ICON=109;
	public static String imgPath="";
	public static void showFor(final Context context,final String project,Uri savePath){
		
		AlertDialog dialog = new AlertDialog.Builder(context).create();
		View cv=LayoutInflater.from(context).inflate(R.layout.export_dialog,null);
		dialog.setView(cv);
		imgPath="";
		final AppCompatCheckBox portrait = cv.findViewById(R.id.portrait);
		final TextInputEditText name = cv.findViewById(R.id.name)
		    ,version = cv.findViewById(R.id.version),
		version_name = cv.findViewById(R.id.version_name),
		package_name= cv.findViewById(R.id.package_name);
		icon = cv.findViewById(R.id.icon);
		SharedPreferences sh = restore(context,project,portrait,version,version_name,package_name,name);
		imgPath = sh.getString(project+"-image","");
		icon.setOnClickListener(v->{
			Intent intent= new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
			((Activity)context).startActivityForResult(intent,RECIEVE_ICON);
		});
		cv.findViewById(R.id.export).setOnClickListener(view->{
			String[] data = getText(context,project,portrait.isChecked(),version,version_name,package_name,name);
			if(data!=null){
				new ExportThread(context,project,savePath,imgPath,data).start();
				dialog.dismiss();
			}
		});
		dialog.show();
	}
	
	public static String[] getText(Context context,String project,boolean or,TextInputEditText... textInputEditTexts){
		String[] str = new String[textInputEditTexts.length+1];
		SharedPreferences sh = context.getSharedPreferences("export",Context.MODE_PRIVATE);
		SharedPreferences.Editor ed= sh.edit();
		ed.putBoolean(project+"or",or);
		int x=0;
		for(TextInputEditText text:textInputEditTexts){
			str[x]=text.getText().toString();
			if(str[x].equals("")){
				text.setError("Enter A Value..");
				return null;
			}
			ed = ed.putString(project+text.getId(),text.getText().toString());
			x++;
		}
		if(FileUtil.isExistFile(imgPath))
			ed.putString(project+"-image",imgPath);
		ed.commit();
		str[x]=or?"1":"0";
		return str;
	}
	
	public static SharedPreferences restore(Context context,String project,AppCompatCheckBox portrait,TextInputEditText... texts){
		SharedPreferences sh = context.getSharedPreferences("export",Context.MODE_PRIVATE);
		String imgPath=sh.getString(project+"-image","");
		//TODO : ...
		//if(FileUtil.isExistFile(imgPath)) Utils.setImageFromFile(icon,imgPath);
		portrait.setChecked(sh.getBoolean(project+"or",false));
		int x=0;
		for(TextInputEditText text:texts){
			String str=sh.getString(project+text.getId(),"");
			try {
				if(!str.equals("")) text.setText(x==0?(Utils.getInt(str)+""):str);
			} catch(Exception e){
				
			}
			if(x==0) x++;
		}
		return sh;
	}
	
}