package com.star4droid.star2d.JointInputs;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.badlogic.gdx.math.Vector2;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class Vec2Input extends LinearLayout implements JointInput {
	public TextView name,x,y;
	public Vec2Input(Context ctx,String field,Object joint){
		super(ctx);
		View v = LayoutInflater.from(ctx).inflate(R.layout.vector2_value,null);
		name = v.findViewById(R.id.name);
		x = v.findViewById(R.id.x);
		y = v.findViewById(R.id.y);
		v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		name.setText(field);
		
		//x.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
		//y.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
		try {
			Field fld = joint.getClass().getField(field);
			fld.setAccessible(true);
			
			setValue(fld.get(joint));
		} catch(Throwable ex){}
		addView(v);
	}
	
	public Vec2Input(Context context,String field){
		super(context);
		View v = LayoutInflater.from(context).inflate(R.layout.vector2_value,null);
		name = v.findViewById(R.id.name);
		x = v.findViewById(R.id.x);
		y = v.findViewById(R.id.y);
		v.findViewById(R.id.pick).setOnClickListener(vv->{
			pick(x,y);
		});
		v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		name.setText(field);
		
		x.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
		y.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
		x.setText("0");
		y.setText("0");
		addView(v);
	}
	
	public void pick(TextView xt,TextView ty){
		
	}
	
	@Override
	public void setValue(Object v){
		if(v instanceof Vector2){
			Vector2 vec2= (Vector2)v;
			x.setText(vec2.x+"");
			y.setText(vec2.y+"");
		} else {
			String str=v.toString().replace("f","").replace("new Vector2(","").replace(")","");
			x.setText(str.split("&&")[0]);
			y.setText(str.split("&&")[1]);
		}
	}
	
	@Override
	public String getValue() {
		try {
			Utils.getFloat(x.getText().toString());
			Utils.getFloat(y.getText().toString());
			return "new Vector2("+x.getText().toString()+"f&&"+y.getText().toString()+"f)";
		} catch(Exception ex){
			Utils.showMessage(getContext(),"Error \n"+ex.toString());
		}
		return null;
	}
	
	@Override
	public String getName() {
		return name.getText().toString();
	}
	
	@Override
	public String getCode() {
		String v=getValue().replace("&&",",");
		return "			%1$s."+getName()+"="+v+";\n";
	}
}