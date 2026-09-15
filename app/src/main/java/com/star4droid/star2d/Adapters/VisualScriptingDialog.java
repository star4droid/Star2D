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
import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.Utils;
import com.star4droid.star2d.Views.VisualScriptingView;
import java.util.ArrayList;
import com.star4droid.star2d.editor.ui.scripting.FieldSuggestionList;

public class VisualScriptingDialog {
	
	public static void showFor(String event,boolean isBody,boolean isScript){
		new Handler(Looper.getMainLooper()).post(()->{
			showFor(Editor.getCurrentEditor(),event,isBody,isScript);
		});
	}
	
	public static void openSceneScript(String scene,String path){
		Intent intent = new Intent();
		intent.putExtra("scene",scene.toLowerCase());
		intent.putExtra("path",path);
		intent.setClass(Editor.getCurrentEditor().getContext(),com.star4droid.star2d.Activities.CodeEditorActivity.class);
		Editor.getCurrentEditor().getContext().startActivity(intent);
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
		String body="",scene = editor.getScene();
		if(isBody){
			if(editor.getSelectedView()==null) return;
			PropertySet ps = PropertySet.getPropertySet(editor.getSelectedView());
			body = ps.getString(ps.containsKey("Script")?"Script":"name");
			if(body.contains("/")) {
				String[] values = body.split("/");
				scene = values[0];
				body = values[1];
			}
		}
		
		final String codePath = isScript?project.getScriptsPath(scene)+event:project.getEventPath(scene,body,event);
		/*PopupMenu popupMenu = new PopupMenu(context,view);
		popupMenu.getMenuInflater().inflate(R.menu.coding_choices_menu,popupMenu.getMenu());
		popupMenu.setOnMenuItemClickListener(item->{
			if(item.getTitle().toString().toLowerCase().contains("visual")){
				visual(editor,event,codePath);
			} else {
				Intent i= new Intent();
				editor.setToCurrentEditor();
				i.setClass(context,CodeEditorActivity.class);
				//i.putExtra("list",editor.getBodiesList());
				i.putExtra("path",codePath+".code");
				context.startActivity(i);
			}
			popupMenu.dismiss();
			return true;
		});
		
		popupMenu.show();
		*/
		visual(editor,event,codePath,!scene.equals(editor.getScene()));
	}
	public static void visual(Editor editor,String event,String codePath){
		visual(editor,event,codePath,false);
	}
	public static void visual(Editor editor,String event,String codePath,boolean fromOther){
		/*
		Gdx.app.postRunnable(()->{
			TestApp app = TestApp.getCurrentApp();
			app.visualScripting.show(app.getUiStage());
		});
		if(true) return;
		*/
		Context context= editor.getContext();
		ArrayList<String> hintsList= new ArrayList<>();
		if(!fromOther)
			hintsList.addAll(editor.getBodiesList());
		if(hintsList.size() > 0 && !fromOther)
			hintsList.add(0,"- Items");
		String[] bodies = new String[hintsList.size()];
		for(int i = 1; i < hintsList.size(); i++){
		    bodies [i - 1] = hintsList.get(i);
		}
		FieldSuggestionList.bodies = bodies;
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
		FieldSuggestionList.images = new String[files.size()];
		for(int i = 1; i < files.size(); i++){
		    FieldSuggestionList.images[i - 1] = files.get(i);
		}
		
		files.clear();
		FileUtil.listDir(editor.getProject().get("scenes"),files);
		FieldSuggestionList.scenes = new String[files.size()];
		for(int pos=0;pos<files.size();pos++){
		    files.set(pos,Uri.parse(files.get(pos)).getLastPathSegment());
		    FieldSuggestionList.scenes[pos] = files.get(pos);
		}
		if(files.size()>0)
		    hintsList.add("- Scenes");
		hintsList.addAll(files);
		
		//joints
		files.clear();
		FileUtil.listDir(editor.getProject().get("joints"),files);
		FieldSuggestionList.joints = new String[files.size()];
		for(int pos=0;pos<files.size();pos++){
		    String jName = Uri.parse(files.get(pos)).getLastPathSegment();
		    files.set(pos,jName.contains("-") ? jName.split("-")[0] : jName);
		    FieldSuggestionList.joints[pos] = files.get(pos);
		}
		if(files.size()>0)
		    hintsList.add("- Joints");
		hintsList.addAll(files);
		
		files.clear();
		//files
		FileUtil.listDir(editor.getProject().get("files"),files);
		FieldSuggestionList.files = new String[files.size()];
		for(int i=0;i<files.size();i++) {
			files.set(i,Uri.parse(files.get(i)).getLastPathSegment());
			FieldSuggestionList.files[i] = files.get(i);
		}
		if(files.size()>0) hintsList.add("- Files");
		hintsList.addAll(files);
		
		//animations
		files.clear();
		FileUtil.listDir(editor.getProject().get("anims"),files);
		FieldSuggestionList.animations = new String[files.size()];
		for(int i=0;i<files.size();i++) {
			files.set(i,Uri.parse(files.get(i)).getLastPathSegment());
			FieldSuggestionList.animations[i] = files.get(i);
		}
		if(files.size()>0) hintsList.add("- Animations");
		hintsList.addAll(files);
		
		//sounds
		files.clear();
		FileUtil.listDir(editor.getProject().get("sounds"),files);
		FieldSuggestionList.sounds = new String[files.size()];
		for(int i=0;i<files.size();i++) {
			files.set(i,Uri.parse(files.get(i)).getLastPathSegment());
			FieldSuggestionList.sounds[i] = files.get(i);
		}
		if(files.size()>0) hintsList.add("- Sounds");
		hintsList.addAll(files);
		
		final AlertDialog dl = Utils.showMessage(context,"please wait...");
		
		VisualScriptingView vs = new VisualScriptingView(context,codePath+".java",codePath+".visual",new Gson().toJson(hintsList),editor.getProject().getPath()){
			public void onDone(final VisualScriptingView vs){
				vs.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
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