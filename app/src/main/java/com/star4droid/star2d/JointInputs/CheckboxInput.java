package com.star4droid.star2d.JointInputs;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatCheckBox;
import com.star4droid.star2d.Utils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import com.star4droid.star2d.evo.R;

public class CheckboxInput extends AppCompatCheckBox implements JointInput {
	String defValue="";
	public CheckboxInput(Context ctx,String field,Object joint){
		super(ctx);
		setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		setText(field);
		setTextColor(ctx.getColor(R.color.text_color));
		//setBackgroundColor(Color.BLACK);
		try {
			Field fld = joint.getClass().getField(field);
			fld.setAccessible(true);
			setValue(fld.get(joint).toString());
		} catch(Throwable ex){}
			defValue = getValue();
	}
	
	public CheckboxInput(Context ctx,String field){
		super(ctx);
		setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		setText(field);
		setTextColor(Color.WHITE);
		setValue(false);
	}
	
	@Override
	public void setValue(Object v){
		setChecked(v.toString().equals("true"));
	}
	
	@Override
	public String getValue() {
		try {
			return String.valueOf(isChecked());
		} catch(Exception ex){}
		return null;
	}
	
	@Override
	public String getName() {
		return getText().toString();
	}
	
	@Override
	public String getCode(){
		return "\n			%1$s."+getName()+"="+getValue()+";\n";
	}
	
}