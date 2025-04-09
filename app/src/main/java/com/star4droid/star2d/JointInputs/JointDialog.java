package com.star4droid.star2d.JointInputs;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.badlogic.gdx.Gdx;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AlertDialog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.JointsHelper;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

public class JointDialog extends LinearLayout implements JointInput {
	public LinearLayout linear;
	TextView name,add;
	Object object;
	Object toSet;
	public static String allowedChars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_";
	boolean loadDone=false;
	
	public JointDialog(String joint,String nm){
		this(Editor.getCurrentEditor().getContext(),joint,nm,Editor.getCurrentEditor());
	}
	
	public JointDialog(Context ctx,final String joint,final String nm,Editor editor){
		super(ctx);
		new Handler(Looper.getMainLooper()).post(()->setup(ctx,joint,nm,editor));
	}
	public JointDialog(Context ctx,final String joint,final String nm){
		super(ctx);
		new Handler(Looper.getMainLooper()).post(()->setup(ctx,joint,nm,null));
	}
	public void setup(Context ctx,final String joint,final String nm,final Editor editor){
		final AlertDialog alertDialog = new AlertDialog.Builder(ctx).create();
		final AlertDialog dialog = Utils.showMessage(ctx,"please wait...");
		new Thread(){
		public void run(){
		Looper.prepare();
		View v = LayoutInflater.from(ctx).inflate(R.layout.joint_dialog,null);
		linear = v.findViewById(R.id.linear);
		add = v.findViewById(R.id.add);
		linear.setOrientation(LinearLayout.VERTICAL);
		name = v.findViewById(R.id.name);
		name.setText(nm);
		if(!nm.equals("")){
			name.setEnabled(false);
			add.setText(R.string.edit);
		}
		v.findViewById(R.id.cancel).setOnClickListener(view->{
			alertDialog.dismiss();
		});
		
		add.setOnClickListener(view->{
				String value = getValue();
				if(value!=null) {
					if(name.getText().toString().equals("")) return;
					for(char c:name.getText().toString().toCharArray()){
						if(!allowedChars.contains(String.valueOf(c))){
							Utils.showMessage(getContext(),"Not allowed char in the name : "+c);
							return;
						}
					}
					onDone(value,name.getText().toString());
					alertDialog.dismiss();
				}
		});
		
		v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		try {
			//constructor params...(from json file)
			int i=0;
			ArrayList<String> list= new ArrayList<>();
			String[] names=JointsHelper.get(joint,"params").split(",");
			for(String str:JointsHelper.get(joint,"types").split(",")){
				list.add(names[i].toLowerCase());
				switch(str.toLowerCase()){
					case "vector2":
					linear.addView(new Vec2Input(ctx,names[i]){
						@Override
						public void pick(final TextView tx,final TextView ty){
							alertDialog.dismiss();
							Utils.showMessage(ctx,"Select point from the screen");
							editor.setOnPick((x,y)->{
								tx.setText(x+"");
								ty.setText(y+"");
								alertDialog.show();
							});
						}
					});
					break;
					case "float":
					linear.addView(new FloatInput(ctx,names[i]));
					break;
					case "body":
					linear.addView(new BodyInput(ctx,names[i],editor));
					break;
				}
				i++;
			}
			//fields
			Class<?> clazz = Class.forName("com.badlogic.gdx.physics.box2d.joints."+joint+"Def");
			Constructor<?> cc = clazz.getConstructor();
			object = cc.newInstance();
			for(Field field:clazz.getFields()){
				if(field.toString().contains("final")) continue;
				if(list.contains(field.getType().getName().toLowerCase())) continue;
				if(field.getType().getName().toLowerCase().contains("float")){
					linear.addView(new FloatInput(ctx,field.getName(),object));
				} else if(field.getType().getName().toLowerCase().contains("vector2")){
					linear.addView(new Vec2Input(ctx,field.getName(),object){
						@Override
						public void pick(final TextView tx,final TextView ty){
							alertDialog.dismiss();
							Utils.showMessage(ctx,"Select point from the screen");
							editor.setOnPick((x,y)->{
								tx.setText(x+"");
								ty.setText(y+"");
								alertDialog.show();
							});
						}
					});
				} else if(field.getType().getName().toLowerCase().contains("boolean")){
					linear.addView(new CheckboxInput(ctx,field.getName(),object));
				}
			}
			
			alertDialog.setView(JointDialog.this);
			JointDialog.this.addView(v);
			Utils.hideSystemUi(alertDialog.getWindow());
			loadDone = true;
			if(toSet!=null) setValue(toSet);
			new Handler(Looper.getMainLooper()).post(new Runnable(){
				@Override
				public void run() {
					dialog.dismiss();
					alertDialog.setCancelable(false);
					alertDialog.show();
				}
			});
			
		} catch(final Throwable ex){
			new Handler(Looper.getMainLooper()).post(new Runnable(){
				@Override
				public void run() {
					dialog.dismiss();
					Utils.showMessage(ctx,"Error \n"+Log.getStackTraceString(ex));
				}
			});
		}
		
		}
		}.start();
	}
	
	@Override
	public void setValue(Object v){
		if((!loadDone)||linear==null){
			toSet = v;
			return;
		}
		try {
		if(v instanceof String){
			String str=v.toString();
			if(str.equals("")) return;
			HashMap<String,JointInput> jmap= new HashMap<>();
			ArrayList<HashMap<String, Object>> fields = new Gson().fromJson(str,new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
			for(int x=1;x<linear.getChildCount();x++){
				View view=linear.getChildAt(x);
				if(view instanceof JointInput){
					JointInput jn = ((JointInput)view);
					jmap.put(jn.getName(),jn);
				}
			}
			for(HashMap<String,Object> hash:fields){
				jmap.get(hash.get("name").toString()).setValue(hash.get("value"));
			}
		}
		} catch(Exception ex){
			toSet = v;
		}
	}
	
	public void onDone(String string,String name){
		
	}
	
	@Override
	public String getValue() {
		try {
			ArrayList<HashMap<String,Object>> arrayList= new ArrayList<>();
				
				for(int x=0;x<linear.getChildCount();x++){
					if(!(linear.getChildAt(x) instanceof JointInput)) continue;
					JointInput jn=(JointInput)linear.getChildAt(x);
					HashMap<String,Object> hash=new HashMap<>();
					if(jn.getValue()==null) {
						Utils.showMessage(getContext(),"Error..!");
						return null;
					}
					hash.put("value",jn.getValue());
					hash.put("name",jn.getName());
					hash.put("code",jn.getCode());
					arrayList.add(hash);
				}
			return new Gson().toJson(arrayList);
		} catch(Exception ex){}
		return null;
	}
	
	@Override
	public String getName() {
		return name.getText().toString();
	}
	
	@Override
	public String getCode() {
		return null;
	}
	
	public static void showJointListDialog(Runnable runnable){
		new Handler(Looper.getMainLooper()).post(()->showJointListDialog(Editor.getCurrentEditor().getContext(),Editor.getCurrentEditor(),runnable));
	}
	
	public static void showJointListDialog(Context context,Editor editor,Runnable runnable){
		final AlertDialog dialog = new AlertDialog.Builder(context).create();
		ScrollView scrollView = new ScrollView(context);
		scrollView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		LinearLayout linear = new LinearLayout(context);
		linear.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		linear.setOrientation(LinearLayout.VERTICAL);
		for(HashMap<String,Object> hash:JointsHelper.getJointsListMap()){
			if(hash.get("joint").toString().contains("GearJoint")) continue;
			final MaterialButton button = new MaterialButton(context);
			button.setText(hash.get("joint").toString().replace("Def",""));
			button.setTextColor(context.getColor(R.color.text_color));
			linear.addView(button);
			button.setCornerRadius(8);
			linear.setPadding(12,12,12,12);
			button.setOnClickListener(new View.OnClickListener(){
				@Override
				public void onClick(View arg0) {
					dialog.dismiss();
					new JointDialog(context,button.getText().toString(),"",editor){
						public void onDone(String string,String name){
							Gdx.files.absolute(editor.getProject().getJoints(editor.getScene())+name+"-"+button.getText().toString()).writeString(string,false);
							runnable.run();
						}
					};
				}
			});
		}
		scrollView.addView(linear);
		
		dialog.setView(scrollView);
		dialog.show();
	}
}