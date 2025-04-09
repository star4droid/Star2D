package com.star4droid.star2d.Helpers;

import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import android.content.Context;
import com.star4droid.star2d.evo.R;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.view.View;
import java.util.ArrayList;

public class ValuesEditor {
	public void show(Context context,final String key,final PropertySet<String,Object> propertySet,final ArrayList<String> hintsL){
		final AlertDialog cd = new AlertDialog.Builder(context,android.R.style.Theme_Material_Light_NoActionBar).create();
		LayoutInflater cdLI = LayoutInflater.from(context);
		View cdCV = cdLI.inflate(R.layout.values_edit_dialog, null);
		cd.setView(cdCV);
		final LinearLayout linear = (LinearLayout)
		cdCV.findViewById(R.id.linear);
		final LinearLayout hints = (LinearLayout)
		cdCV.findViewById(R.id.hints);
		final TextView text = (TextView)
		cdCV.findViewById(R.id.text);
		final ImageView hide = (ImageView)
		cdCV.findViewById(R.id.hide);
		final ImageView del = (ImageView)
		cdCV.findViewById(R.id.del);
		final ImageView clear = (ImageView)
		cdCV.findViewById(R.id.clear);
		final ImageView done = (ImageView)
		cdCV.findViewById(R.id.done);
		final EditText edittext = (EditText)
		cdCV.findViewById(R.id.edittext);
		final ImageView edit = (ImageView)
		cdCV.findViewById(R.id.edit);
		cd.show();
		text.setText(propertySet.containsKey(key)? propertySet.getString(key):"");
		edittext.setText(text.getText().toString());
		edittext.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				final String edittextnn = _param1.toString();
				if (!text.getText().toString().equals(edittext.getText().toString())) {
					text.setText(edittext.getText().toString());
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				
			}
			
			@Override
			public void afterTextChanged(Editable _param1) {
				
			}
		});
		hide.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View _view){
				cd.dismiss();
			}
		});
		done.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View _view){
				if (edittext.getVisibility() == View.GONE) {
					propertySet.put(key,text.getText().toString());
				}
				else {
					propertySet.put(key,edittext.getText().toString());
				}
				cd.dismiss();
			}
		});
		clear.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View _view){
				text.setText("");
				edittext.setText(text.getText().toString());
			}
		});
		edit.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View _view){
				edit.setVisibility(View.GONE);
				text.setVisibility(View.GONE);
				edittext.setVisibility(View.VISIBLE);
			}
		});
		del.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View _view){
				try {
					double nx = text.getText().toString().length() - 1;
					if ("abcdefghijklmnopqrstuvwxyz".contains(String.valueOf(text.getText().toString().charAt((int)(nx))).toLowerCase())) {
						while((text.getText().toString().length() > 0) && "abcdefghijklmnopqrstuvwxyz".contains(String.valueOf(text.getText().toString().charAt((int)(nx))).toLowerCase())) {
							text.setText(text.getText().toString().substring(0, text.getText().toString().length() - 1));
							nx = text.getText().toString().length() - 1;
						}
					}
					else {
						text.setText(text.getText().toString().substring(0, text.getText().toString().length() - 1));
					}
					} catch (Exception e) {
					
				}
				edittext.setText(text.getText().toString());
			}
		});
		
		for (int ix = 0; ix <  linear.getChildCount(); ix++) {
			View vix = linear.getChildAt(ix);
			for (int i = 0; i < ((ViewGroup)(vix)).getChildCount(); i++) {
				View v = ((ViewGroup)(vix)).getChildAt(i);
				if (v instanceof TextView) {
					v.setOnClickListener(new View.OnClickListener(){
						@Override
						public void onClick(View _view){
							text.setText(text.getText().toString().concat(((TextView) _view).getText().toString()));
							edittext.setText(text.getText().toString());
						}
					});
					LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.MATCH_PARENT);
					params.weight = ((float) 1);
					if (i == 0) {
						int top = 5;
						int bottom = 0;
						int right = 5;
						int left = 5;
						params.setMargins(left, top, right, bottom);
					}
					else {
						int top = 5;
						int bottom = 0;
						int right = 5;
						int left = 0;
						params.setMargins(left, top, right, bottom);
					}
					v.setLayoutParams(params);
					
				}
			}
		}
		for(String ss:hintsL){
			final TextView txt= new TextView(context);
			txt.setText(ss);
			txt.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(0,1, 0xFFFFFFFF, 0xFF0078FE));
			txt.setTextColor(0xFFFFFFFF);
			txt.setSingleLine(true);
			txt.setPadding(8,10,2,10);
			if (ss.startsWith("-")) {
				txt.setBackgroundColor(0xFF1A1A1A);
				txt.setOnClickListener(new View.OnClickListener(){
					@Override
					public void onClick(View _view){
						int x = ((ViewGroup)txt.getParent()).indexOfChild(txt) + 1;
						for (int i = x; i < ((ViewGroup) hints).getChildCount(); i++) {
							View v = ((ViewGroup)hints).getChildAt(i);
							if (v.getTag().toString().contains("p")) {
								break;
							}
							else {
								if (v.getVisibility() == View.VISIBLE) {
									v.setVisibility(View.GONE);
								}
								else {
									v.setVisibility(View.VISIBLE);
								}
							}
						}
					}
				});
				txt.setTag("p");
			}
			else {
				txt.setOnClickListener(new View.OnClickListener(){
					@Override
					public void onClick(View _view){
						text.setText(text.getText().toString().concat(((TextView) _view).getText().toString()));
						edittext.setText(text.getText().toString());
					}
				});
				txt.setVisibility(View.GONE);
				txt.setTag("n");
			}
			hints.addView(txt);
		}
	}
}