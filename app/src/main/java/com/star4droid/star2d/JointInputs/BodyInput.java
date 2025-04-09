package com.star4droid.star2d.JointInputs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.star4droid.star2d.EditorActivity;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import java.util.ArrayList;

public class BodyInput extends LinearLayout implements JointInput {
	ArrayList<String> bodies;
	Spinner spinner;
	TextView name;
	public BodyInput(Context context,String nm,Editor editor){
		super(context);
		View v = LayoutInflater.from(context).inflate(R.layout.spinner_value,null);
		v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		spinner = v.findViewById(R.id.spinner);
		name = v.findViewById(R.id.name);
		bodies = editor.getBodiesList();
		name.setText(nm);
		spinner.setAdapter(EditorActivity.getSpinnerAdapter(bodies,context,spinner));
		addView(v);
	}
	
	@Override
	public String getValue() {
		try {
			return bodies.get(spinner.getSelectedItemPosition())+".getBody()";
		} catch(Exception ex){}
	    return null;
	}

	@Override
	public void setValue(Object object) {
		try {
			String ob=object.toString().replace(".getBody()","");
			int x=0;
			for(String str:bodies){
				if(str.equals(ob)) spinner.setSelection(x);
				x++;
			}
		} catch(Exception e){
			Utils.showMessage(getContext(),e.toString());
		}
	}

	@Override
	public String getName() {
	    return name.getText().toString();
	}
	
	@Override
	public String getCode() {
		return getValue()+".getBody()";
	}
}