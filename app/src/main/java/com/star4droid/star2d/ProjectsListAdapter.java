package com.star4droid.star2d;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;

import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.elevation.SurfaceColors;
import com.star4droid.star2d.Adapters.ExportDialog;
import com.star4droid.star2d.Adapters.MissingFileDialog;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.evo.R;
import java.io.File;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class ProjectsListAdapter extends BaseAdapter {
	ArrayList<String> arrayList;
	AppCompatActivity context;
	Uri source;
	String type="";
	String ex_project;
	ActivityResultLauncher saveFile;
	public ProjectsListAdapter(AppCompatActivity ctx,ArrayList<String> array){
		this.arrayList= array;
		this.context= ctx;
		saveFile = ctx.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),new ActivityResultCallback<ActivityResult>(){
			@Override
			public void onActivityResult(ActivityResult result) {
				if(result==null||result.getData()==null) return;
				if(type.equals("export")){
					Uri target = result.getData().getData();
					ExportDialog.showFor(context,ex_project,target);
					return;
				}
				Uri uri = result.getData().getData();
				try {
					Utils.saveFileToPath(source,uri,context);
					FileUtil.deleteFile(source.getPath());
					Utils.showMessage(context,"Saved ...");
					} catch (Exception ex){
					Utils.showMessage(context,"save file error : "+ex.toString());
				}
			}
		});
	}
	
	@Override
	public int getCount() {
		return arrayList.size();
	}
	
	@Override
	public Object getItem(int position) {
		return arrayList.get(position);
	}
	
	@Override
	public long getItemId(int position) {
		return position;
	}
	
	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if(view==null) {
			LayoutInflater layoutInflater = LayoutInflater.from(context);
			view = layoutInflater.inflate(R.layout.project_csv,null);
		}
		//Utils.setupCorner(view);
		final TextView textView = view.findViewById(R.id.name);
		ImageView backup = view.findViewById(R.id.backup);
		MaterialCardView card=view.findViewById(R.id.project_container);
		//card.setCardBackgroundColor(SurfaceColors.SURFACE_4.getColor(card.getContext()));
		//textView.setTextColor(Utils.getColorAttr(textView.getContext(),com.google.android.material.R.attr.colorOnBackground));
		
		textView.setText(Uri.parse(arrayList.get(position)).getLastPathSegment());
		ImageView delete = view.findViewById(R.id.delete);

		view.findViewById(R.id.export).setOnClickListener(ex->{
			/*
			if(!FileUtil.isExistFile(FileUtil.getPackageDataDir(context)+"/apk/template.zip")){
				MissingFileDialog.showFor(context,MissingFileDialog.TEMPLATE);
				return;
			}
			*/
			type = "export";
			if(Build.VERSION.SDK_INT<30){
			    String out = FileUtil.getExternalStorageDir()+"/Star2D/"+textView.getText().toString()+".apk";
				java.io.File file = new java.io.File(out);
				FileUtil.writeFile(out,"");
				ExportDialog.showFor(context,arrayList.get(position),Uri.fromFile(file));
				return;
			}
			ex_project=arrayList.get(position);
			Utils.saveFile(Uri.parse(arrayList.get(position)).getLastPathSegment()+".apk",saveFile);
		});
		delete.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
				builder.setTitle("Delete "+Uri.parse(arrayList.get(position)).getLastPathSegment());
				builder.setMessage("Are You sure ?");
				builder.setPositiveButton("Delete",new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						FileUtil.deleteFile(arrayList.get(position));
						arrayList.remove(position);
						ProjectsListAdapter.this.notifyDataSetChanged();
					}
				});
				
				builder.setNegativeButton("Cancel",new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						
					}
				});
				AlertDialog dl=builder.create();
				Utils.hideSystemUi(dl.getWindow());
				dl.show();
			}
		});
		
		backup.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View view) {
				//dialog ...
				AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
				builder.setTitle("Backup");
				builder.setMessage("Do you want to backup "+Uri.parse(arrayList.get(position)).getLastPathSegment()+" ?");
				builder.setPositiveButton("Backup",new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						backup(position,view);
					}
				});
				
				builder.setNegativeButton("Cancel",new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						
					}
				});
				AlertDialog alertDialog =builder.create();
				Utils.hideSystemUi(alertDialog.getWindow());
				alertDialog.show();
				
			}
		});
		
		final LinearLayout linear = view.findViewById(R.id.linear);
		linear.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				/*
				Intent intent = new Intent();
				intent.putExtra("project",arrayList.get(position));
				intent.setClass(context,EditorActivity.class);
				context.startActivity(intent);
				*/
				Intent intent = new Intent(context, EditorActivity.class);
				intent.putExtra("project",arrayList.get(position));
				Bundle bundle =
				ActivityOptionsCompat.makeCustomAnimation(
				context, android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
				context.startActivity(intent, bundle);
				
			}
		});
		return view;
	}

	
	public void backup(final int position,final View view){
		final AlertDialog dialog = new AlertDialog.Builder(view.getContext()).create();
		dialog.setView(new ProgressBar(view.getContext()));
		dialog.setCancelable(false);
		dialog.show();
		new Thread(){
			@Override
			public void run(){
				final StringBuilder err=new StringBuilder("");
				String initial = (Build.VERSION.SDK_INT>=30)?FileUtil.getPackageDataDir(view.getContext()):FileUtil.getExternalStorageDir();
				final String exportPath = initial+"/Star2D/backups/"+Uri.parse(arrayList.get(position)).getLastPathSegment()+".zip";
				try {
					Utils.createEmptyZipFile(exportPath);
					Utils.zipf(arrayList.get(position),exportPath,"");
					} catch(Exception ex){
					err.append(ex.toString());
				}
				
				new Handler(Looper.getMainLooper()).post(new Runnable(){
					@Override
					public void run() {
						dialog.dismiss();
						type ="backup";
						AlertDialog dialog= new AlertDialog.Builder(view.getContext()).create();
						dialog.setCancelable(true);
						TextView text = new TextView(view.getContext());
						text.setPadding(8,8,8,8);
						//text.setBackgroundColor(Color.WHITE);
						text.setTextColor(Color.BLACK);
						text.setText(err.toString().equals("")?("exported to : \n"+exportPath):err.toString());
						dialog.setView(text);
						//dialog.show();
						source = Uri.fromFile(new File(exportPath));
						if(Build.VERSION.SDK_INT>=30)
						Utils.saveFile(source.getLastPathSegment(),saveFile);
						else dialog.show();
					}
				});
				
			}
		}.start();
	}
}