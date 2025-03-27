package com.star4droid.star2d.Fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.star4droid.star2d.Adapters.EditorField;
import com.star4droid.star2d.Adapters.VisualScriptingDialog;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.JointsHelper;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.JointInputs.JointDialog;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import java.util.HashMap;
import java.util.ArrayList;

public class JointsFragment extends Fragment {
	ArrayList<HashMap<String, Object>> list= new ArrayList<>();
	Editor editor;
	Project project;
	LinearLayout linear;
	RecyclerView recyclerView;
	
	public JointsFragment(){}
	
	public JointsFragment(Editor ed){
		//super();
		editor = ed;
        if(ed!=null)
	    	refresh();
		
	}
	
	public void refresh(){
		if(recyclerView==null||recyclerView.getAdapter()==null) return;
		if(editor==null||editor.getProject()==null) return;
		list.clear();
		ArrayList<String> joints= new ArrayList<>();
		FileUtil.listDir(editor.getProject().getJoints(editor.getScene()),joints);
		
		for(int x=0;x<joints.size();x++){
			String path=Uri.parse(joints.get(x)).getLastPathSegment();
			push(path,R.drawable.package_variant);
		}
		push(getContext().getString(R.string.add_joint),R.drawable.ic_add_black);
		recyclerView.getAdapter().notifyDataSetChanged();
	}
	
	public void wrap(){
		try {
			if(linear!=null)
			linear.setMinimumHeight(getContext().getResources().getDisplayMetrics().heightPixels*5);
		} catch(Exception ex){}
	}
	
	public void push(String name,int icon){
		HashMap<String,Object> hashMap= new HashMap<>();
		hashMap.put("name",name);
		hashMap.put("icon",icon);
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
		refresh();
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
		public void onBindViewHolder(ViewHolder _holder, int pos) {
			View _view = _holder.itemView;
			int _position = _holder.getAdapterPosition();
			final LinearLayout linear1 = _view.findViewById(R.id.linear1);
			final ImageView icon = _view.findViewById(R.id.icon);
			final LinearLayout linear3 = _view.findViewById(R.id.linear3);
			final TextView name = _view.findViewById(R.id.name);
			
			icon.setImageDrawable(context.getDrawable((int)(_data.get(_position).get("icon"))));
			name.setText(_data.get(_position).get("name").toString());
			
			_view.setOnLongClickListener(view->{
				if(_position==_data.size()) return true;
				AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
				builder.setTitle("Delete "+list.get(_position).get("name").toString());
				builder.setMessage("Are You sure ?");
				builder.setPositiveButton("Delete",new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						String path = editor.getProject().getJoints(editor.getScene())+list.get(_position).get("name").toString();
						FileUtil.deleteFile(path);
						list.remove(_position);
						recyclerView.getAdapter().notifyDataSetChanged();
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
			});
			
			_view.setOnClickListener(view->{
				if(_position!=_data.size()-1){
					final String nm=_data.get(_position).get("name").toString();
					new JointDialog(getContext(),nm.split("-")[1],nm.split("-")[0],editor){
						public void onDone(String string,String name){
							FileUtil.writeFile(editor.getProject().getJoints(editor.getScene())+nm,string);
							refresh();
						}
					}.setValue(FileUtil.readFile(editor.getProject().getJoints(editor.getScene())+nm));
					return;
				}
				JointDialog.showJointListDialog(getContext(),editor,()->refresh());
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
	
}