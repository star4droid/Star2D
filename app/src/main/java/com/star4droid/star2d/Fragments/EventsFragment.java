package com.star4droid.star2d.Fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.LinearLayout;
import com.star4droid.star2d.Adapters.EditorField;
import com.star4droid.star2d.Adapters.VisualScriptingDialog;
import com.star4droid.star2d.CodeEditor.MyIndexer;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import java.util.HashMap;
import java.util.ArrayList;

public class EventsFragment extends Fragment {
	ArrayList<HashMap<String, Object>> list= new ArrayList<>();
	Editor editor;
	Project project;
	LinearLayout linear;
	RecyclerView recyclerView;
	
	public EventsFragment(){
		
	}
	
	public EventsFragment(Editor ed){
		editor = ed;
		push("OnCreate",R.drawable.properties,false,false);
		push("OnStep",R.drawable.step_icon,false,false);
		push("OnPause",R.drawable.ic_pause_black,false,false);
		push("OnResume",R.drawable.ic_play_arrow_black,false,false);
		push("onCollisionStart",R.drawable.two_collision,false,false);
		push("onCollisionEnd",R.drawable.collision_end_icon,false,false);
		push("onClick",R.drawable.mouse_click,true,false);
		push("OnBodyCreated",R.drawable.body_created_icon,true,false);
		push("OnBodyUpdate",R.drawable.on_update_icon,true,false);
		push("onTouchStart",R.drawable.touch_start_icon,true,false);
		push("onTouchEnd",R.drawable.touch_end_icon,true,false);
		push("onBodyCollided",R.drawable.two_collision,true,false);
		push("onBodyCollideEnd",R.drawable.collision_end_icon,true,false);
		push("Body Script",R.drawable.code,true,true);
		ArrayList<String> scripts= new ArrayList<>();
		FileUtil.listDir(editor.getProject().get("scripts"),scripts);
		
		for(int x=0;x<scripts.size();x++){
			String path=Uri.parse(scripts.get(x)).getLastPathSegment();
			if(path.endsWith(".java")){
				push(path.replace(".java",""),R.drawable.code,false,true);
			}
		}
		push("Add Script",R.drawable.ic_add_black,false,true);
		
	}
	
	public void wrap(){
		try {
				if(linear!=null)
					linear.setMinimumHeight(getContext().getResources().getDisplayMetrics().heightPixels*5);
			} catch(Exception ex){}
	}
	
	public void push(String name,int icon,boolean body,boolean script){
		HashMap<String,Object> hashMap= new HashMap<>();
		hashMap.put("name",name);
		hashMap.put("icon",icon);
		hashMap.put("body",String.valueOf(body));
		hashMap.put("script",String.valueOf(script));
		hashMap.put("delete",((!body)&&script)||name.equals("Body Script"));
		list.add(hashMap);
	}
	
	@Override
	public void onAttach(Context arg0) {
		super.onAttach(arg0);
		wrap();
	}
	
	@Override
	public void onStart() {
		super.onStart();
		wrap();
	}
	
	@Override
	public void onCreate(Bundle arg0) {
		super.onCreate(arg0);
	}
	
	@Override
	public View onCreateView(LayoutInflater arg0, ViewGroup arg1, Bundle arg2) {
		recyclerView= new RecyclerView(getContext());
		linear = new LinearLayout(getContext());
		recyclerView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
		linear.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,99999));
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
		if(editor!=null)
        recyclerView.setAdapter(new RecyclerAdapter(list,editor));
		linear.addView(recyclerView);
		wrap();
		/*
		recyclerView.setOnScrollChangeListener(new View.OnScrollChangeListener(){
			@Override
			public void onScrollChange(View arg0, int arg1, int arg2, int arg3, int arg4) {
				
			}
		});
		*/
		return linear;
	}
	
	public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder> {
		
		ArrayList<HashMap<String, Object>> _data;
		Context context;
		Editor editor;
		
		public RecyclerAdapter(ArrayList<HashMap<String, Object>> _arr,Editor ed) {
			_data = _arr;
			context = ed.getContext();
			editor = ed;
		}
		
		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			LayoutInflater _inflater = LayoutInflater.from(context);
			View _v = _inflater.inflate(R.layout.sections_list_csv, null);
			RecyclerView.LayoutParams _lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			_v.setLayoutParams(_lp);
			return new ViewHolder(_v);
		}
		
		@Override
		public void onBindViewHolder(ViewHolder _holder,  int pos) {
			View _view = _holder.itemView;
			int _position = _holder.getAdapterPosition();
			final LinearLayout linear1 = _view.findViewById(R.id.linear1);
			final ImageView icon = _view.findViewById(R.id.icon);
			final LinearLayout linear3 = _view.findViewById(R.id.linear3);
			final TextView name = _view.findViewById(R.id.name);
			if(_position>5) icon.clearColorFilter();
			icon.setImageDrawable(context.getDrawable((int)(_data.get(_position).get("icon"))));
			name.setText(_data.get(_position).get("name").toString());
			_view.setOnClickListener(new View.OnClickListener(){
				@Override
				public void onClick(View v) {
					if(_position==list.size()-1)
						addScript();
							else
								if(_data.get(_position).get("name").equals("Body Script")){
									if(editor.getSelectedView()==null||(editor.getIndexer()!=null&&editor.getIndexer().isIndexing())) return;
									PropertySet ps= PropertySet.getPropertySet(editor.getSelectedView());
									Intent intent= new Intent();
									intent.putExtra("path",editor.getProject().getBodyScriptPath(ps.getString("name"),editor.getScene()));
									intent.putExtra("name",ps.getString("name")+"Script");
									intent.setClass(editor.getContext(),com.star4droid.star2d.Activities.CodeEditorActivity.class);
									context.startActivity(intent);
								} else
									VisualScriptingDialog.showFor(editor,_data.get(_position).get("name").toString(),_data.get(_position).get("body").toString().equals("true"),_data.get(_position).get("script").toString().equals("true"));
								}
				
			});
			_view.setOnLongClickListener(new View.OnLongClickListener(){
				@Override
				public boolean onLongClick(final View clicked) {
					if(!_data.get(_position).get("delete").toString().equals("true")) return true;
					AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
					builder.setTitle("Delete "+list.get(_position).get("name").toString());
					builder.setMessage("Are You sure ?");
					builder.setPositiveButton("Delete",new DialogInterface.OnClickListener(){
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							
							if(_data.get(_position).get("name").equals("Body Script")) {
								if(editor.getSelectedView()==null) return;
								PropertySet ps= PropertySet.getPropertySet(editor.getSelectedView());
								FileUtil.deleteFile(editor.getProject().getBodyScriptPath(ps.getString("name"),editor.getScene()));
								return;
							}
							String path = editor.getProject().get("scripts")+list.get(_position).get("name");
							if(list.get(_position).get("script").equals("true")) {
								FileUtil.deleteFile(path+".java");
								FileUtil.deleteFile(path+".visual");
								Utils.showMessage(clicked.getContext(),"path : "+path+".java");
								//FileUtil.deleteFile(path+".code");
								list.remove(_position);
								recyclerView.getAdapter().notifyDataSetChanged();
							}
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
				    return true;
				}
			});
		}
		
		@Override
		public int getItemCount() {
			return _data.size();
		}
		
		public class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) {
				super(v);
			}
		}
	}
	
	public void addScript(){
		Context ctx = getContext();
		View dialog_cv = LayoutInflater.from(ctx).inflate(R.layout.create_dialog,null);
	    final AlertDialog alertDialog = new AlertDialog.Builder(ctx).create();
							final EditText nam = dialog_cv.findViewById(R.id.name);
							TextView add = dialog_cv.findViewById(R.id.add);
							TextView title = dialog_cv.findViewById(R.id.title);
							nam.setHint("Enter Name...");
							title.setText("Add Script");
							add.setText("Add");
							alertDialog.setView(dialog_cv);
							add.setOnClickListener(new View.OnClickListener(){
								@Override
								public void onClick(View arg0) {
									String path = editor.getProject().get("scripts")+nam.getText().toString()+".java";
									if(FileUtil.isExistFile(path)) {
										Utils.showMessage(ctx,"There is already script with this name...!");
										return;
									}
									if(nam.getText().toString().equals("Body Script")) return;
									for(char c:nam.getText().toString().toCharArray()){
										if(!EditorField.allowedChars.contains(String.valueOf(c))){
											Utils.showMessage(ctx,"use A-Z a-z or _ , Not Allowed Char : "+c);
											return;
										}
									}
									FileUtil.writeFile(path,"");
									if(FileUtil.isExistFile(path)){
										HashMap<String,Object> hash= new HashMap<>();
										hash.put("icon",R.drawable.code);
										hash.put("name",nam.getText().toString());
										hash.put("body",false);
										hash.put("script",true);
										hash.put("delete",true);
										list.add(list.size()-1,hash);
										recyclerView.getAdapter().notifyDataSetChanged();
									}
									alertDialog.dismiss();
								}
							});
							alertDialog.show();
	}
	
}