package com.star4droid.star2d.JointInputs;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class FloatInput extends LinearLayout implements JointInput {
	public TextView name,value;
	String defValue="";
	public FloatInput(Context ctx,String field,Object joint){
		super(ctx);
		View v = LayoutInflater.from(ctx).inflate(R.layout.float_value_joint,null);
		name = v.findViewById(R.id.name);
		value = v.findViewById(R.id.value);
		v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		//value.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
		name.setText(field);
		
		try {
			Field fld = joint.getClass().getField(field);
			fld.setAccessible(true);
			setValue(fld.get(joint).toString());
		} catch(Throwable ex){}
		addView(v);
	}
	
	public FloatInput(Context ctx,String field){
		super(ctx);
		View v = LayoutInflater.from(ctx).inflate(R.layout.float_value_joint,null);
		name = v.findViewById(R.id.name);
		value = v.findViewById(R.id.value);
		v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		value.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
		name.setText(field);
		value.setText("0");
		addView(v);
	}
	
	@Override
	public void setValue(Object v){
		value.setText(v.toString().replace("f",""));
	}
	
	@Override
	public String getValue() {
		try {
			return Utils.getFloat(value.getText().toString())+"";
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
		return "			%1$s."+getName()+"="+getValue()+"f;\n";
	}
}