package com.star4droid.star2d.Adapters;

import android.view.View;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import com.badlogic.gdx.Gdx;
import com.bumptech.glide.Glide;
import com.star4droid.star2d.Views.CustomColliderView;
import com.star4droid.star2d.editor.items.CustomItem;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.Utils;
import com.star4droid.star2d.Helpers.FileUtil;

public class CustomColliderEditor {
	
	public static void show(){
		showFor(Editor.getCurrentEditor());
	}
	
	public static void showFor(Editor editor){
		if(!isTheCurrentIsCustom(editor)) {
		    android.widget.Toast.makeText(editor.getContext(),"current isn\'t custom!!",1500).show();
		    return;
		}
		CustomItem customBody = (CustomItem)editor.getSelectedView();
		View view = LayoutInflater.from(editor.getContext()).inflate(R.layout.custom_collider_editor_dialog,null);
		LinearLayout pointsLinear = view.findViewById(R.id.points_linear);
		CustomColliderView colliderView = view.findViewById(R.id.collider_view);
		colliderView.setPointsFromString(customBody.getPropertySet().getString("Points"));
		AlertDialog alertDialog = new AlertDialog.Builder(editor.getContext(),android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen).create();
		alertDialog.setView(view);
		updatePointsLinear(pointsLinear,colliderView);
		view.findViewById(R.id.add_point).setOnClickListener(v->{
			colliderView.addPoint(0,0);
			colliderView.setSelected(colliderView.getPoints().size()-1);
			updatePointsLinear(pointsLinear,colliderView);
		});
		view.findViewById(R.id.delete_point).setOnClickListener(v->{
			colliderView.deleteSelected();
			updatePointsLinear(pointsLinear,colliderView);
		});
		view.findViewById(R.id.save).setOnClickListener(v->{
			String pointsStr = colliderView.getPointsStr();
			Gdx.app.postRunnable(()->
				EditorAction.propertiesChanged(editor.getLibgdxEditor(),editor.getLibgdxEditor().getSelectedActor().getName(),"custom collider points",new String[]{"Points",pointsStr},new String[]{"Points",customBody.getPropertySet().getString("Points")})
			);
			customBody.getPropertySet().put("Points",colliderView.getPointsStr());
			alertDialog.dismiss();
		});
		view.findViewById(R.id.cancel).setOnClickListener(v->{
			alertDialog.dismiss();
		});
		alertDialog.show();
		// android.widget.Toast.makeText(editor.getContext(),customBody.getPropertySet().getString("image"),2500).show();
		if (!customBody.getPropertySet().getString("image").equals("")) {
		    // android.widget.Toast.makeText(editor.getContext(),"request sent",2500).show();
			String img = editor.getProject().getImagesPath() + customBody.getPropertySet().getString("image").replace(Utils.seperator,"/");
			if (FileUtil.isExistFile(img)) {
			    // android.widget.Toast.makeText(editor.getContext(),"exist determined",2500).show();
			    android.graphics.Point repeat = new android.graphics.Point();
				repeat.x = customBody.getPropertySet().getInt("tileX");
				repeat.y = customBody.getPropertySet().getInt("tileY");
				Glide.with(editor.getContext()).load(img).error(R.drawable.icon).into(colliderView);
				//Utils.setImageFromFile(colliderView, img, repeat, null, null);
			}
		}
	}
	
	public static void updatePointsLinear(LinearLayout lin, CustomColliderView cv){
	    lin.removeAllViews();
	    for(int x = 0; x < cv.getPoints().size(); x++){
	        LinearLayout plin = new LinearLayout(lin.getContext());
	        int dp = Utils.convertPixelsToDp(lin.getContext(),40);
	        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp,dp);
	        params.topMargin = Utils.convertPixelsToDp(lin.getContext(),8);
	        params.leftMargin = params.topMargin;
	        params.rightMargin = Utils.convertPixelsToDp(lin.getContext(),4);
	        plin.setLayoutParams(params);
	        android.widget.TextView text = new android.widget.TextView(lin.getContext());
	        text.setText((x+1)+"");
	        text.setTextColor(android.graphics.Color.WHITE);
	        plin.setGravity(android.view.Gravity.CENTER);
	        Utils.setCornerRadius(plin,8, android.graphics.Color.RED);
	        plin.addView(text);
	        lin.addView(plin);
	        final int position= x;
	        plin.setOnClickListener(v->{
	            cv.setSelected(position);
	        });
	    }
	}
	
	public static boolean isTheCurrentIsCustom(Editor editor){
		return (editor.getSelectedView() != null && editor.getSelectedView() instanceof CustomItem);
	}
}