package com.star4droid.star2d.Adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import com.google.gson.Gson;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.Utils;
import com.star4droid.star2d.Views.VisualScriptingView;
import java.util.ArrayList;

public class VisualScriptingDialog {
	
	public static void showFor(String event,boolean isBody,boolean isScript){
		new Handler(Looper.getMainLooper()).post(()->{
			showFor(Editor.getCurrentEditor(),event,isBody,isScript);
		});
	}
	
	public static void openCodeEditor(){
		Editor editor = Editor.getCurrentEditor();
		if(editor.getSelectedView()==null||(editor.getIndexer()!=null&&editor.getIndexer().isIndexing())) return;
		PropertySet ps= PropertySet.getPropertySet(editor.getSelectedView());
		Intent intent= new Intent();
		intent.putExtra("path",editor.getProject().getBodyScriptPath(ps.getString("name"),editor.getScene()));
		intent.putExtra("name",ps.getString("name")+"Script");
		intent.putExtra("body",true);
		intent.setClass(editor.getContext(),com.star4droid.star2d.Activities.CodeEditorActivity.class);
		editor.getContext().startActivity(intent);
	}
	
	public static void showFor(final Editor editor,final String event,boolean isBody,boolean isScript){
		final Context context = editor.getContext();
		Project project = editor.getProject();
		String body="";
		if(isBody){
			if(editor.getSelectedView()==null) return;
			PropertySet ps = PropertySet.getPropertySet(editor.getSelectedView());
			body = ps.getString(ps.containsKey("Script")?"Script":"name");
		}
		
		final String cp = isScript?project.getScriptsPath(editor.getScene())+event:project.getEventPath(editor.getScene(),body,event);
		/*PopupMenu popupMenu = new PopupMenu(context,view);
		popupMenu.getMenuInflater().inflate(R.menu.coding_choices_menu,popupMenu.getMenu());
		popupMenu.setOnMenuItemClickListener(item->{
			if(item.getTitle().toString().toLowerCase().contains("visual")){
				visual(editor,event,cp);
			} else {
				Intent i= new Intent();
				editor.setToCurrentEditor();
				i.setClass(context,CodeEditorActivity.class);
				//i.putExtra("list",editor.getBodiesList());
				i.putExtra("path",cp+".code");
				context.startActivity(i);
			}
			popupMenu.dismiss();
			return true;
		});
		
		popupMenu.show();
		*/
		visual(editor,event,cp);
	}
	public static void visual(Editor editor,String event,String cp){
		Context context= editor.getContext();
		ArrayList<String> hintsList= new ArrayList<>();
		hintsList.addAll(editor.getBodiesList());
		if(hintsList.size()>0) hintsList.add(0,"- Items");
		ArrayList<String> files = new ArrayList<>();
		FileUtil.listDir(editor.getProject().getImagesPath(),files);
		int x=0;
		while(x<files.size()){
			if(FileUtil.isDirectory(files.get(x))){
				files.remove(x);
			} else {
				files.set(x,Uri.parse(files.get(x)).getLastPathSegment());
				x++;
			}
		}
		if(files.size()>0) files.add(0,"- Images");
		hintsList.addAll(files);
		files.clear();
		FileUtil.listDir(editor.getProject().get("scenes"),files);
		for(int pos=0;pos<files.size();pos++){
		    files.set(pos,Uri.parse(files.get(pos)).getLastPathSegment());
		}
		if(files.size()>0)
		    hintsList.add("- Scenes");
		hintsList.addAll(files);
		files.clear();
		//files
		FileUtil.listDir(editor.getProject().get("files"),files);
		for(int i=0;i<files.size();i++) {
			files.set(i,Uri.parse(files.get(i)).getLastPathSegment());
		}
		if(files.size()>0) hintsList.add("- Files");
		hintsList.addAll(files);
		//animations
		files.clear();
		FileUtil.listDir(editor.getProject().get("anims"),files);
		for(int i=0;i<files.size();i++) {
			files.set(i,Uri.parse(files.get(i)).getLastPathSegment());
		}
		if(files.size()>0) hintsList.add("- Animations");
		hintsList.addAll(files);
		//sounds
		files.clear();
		FileUtil.listDir(editor.getProject().get("sounds"),files);
		for(int i=0;i<files.size();i++) {
			files.set(i,Uri.parse(files.get(i)).getLastPathSegment());
		}
		if(files.size()>0) hintsList.add("- Sounds");
		hintsList.addAll(files);
		
		final AlertDialog dl = Utils.showMessage(context,"please wait...");
		
		VisualScriptingView vs = new VisualScriptingView(context,cp+".java",cp+".visual",new Gson().toJson(hintsList),editor.getProject().getPath()){
			public void onDone(final VisualScriptingView vs){
				dl.dismiss();
				//init done , add view's and show dialog
				View view = LayoutInflater.from(context).inflate(R.layout.visual_dialog,null);
				view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
				LinearLayout linear = view.findViewById(R.id.linear);
				ImageView close = view.findViewById(R.id.close);
				ImageView show = view.findViewById(R.id.show_code);
				ImageView save = view.findViewById(R.id.save_code);
				TextView vpath= view.findViewById(R.id.script_path);
				vpath.setText("path : "+vs.code_path.replace(FileUtil.getPackageDataDir(context),""));
				linear.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
				
				final AlertDialog dialog = new AlertDialog.Builder(context,android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen).create();
				
				dialog.setView(view);
				Utils.hideSystemUi(dialog.getWindow());
				linear.addView(vs);
				dialog.show();
				save.setOnClickListener(new View.OnClickListener(){
					@Override
					public void onClick(View arg0) {
						vs.save();
						Utils.showMessage(context,"Saved √");
					}
				});
				close.setOnClickListener(new View.OnClickListener(){
					@Override
					public void onClick(View v){
						dialog.dismiss();
					}
				});
				show.setOnClickListener(new View.OnClickListener(){
					@Override
					public void onClick(View v){
						vs.showCode();
					}
				});
				
			}
		};
		
	}
}