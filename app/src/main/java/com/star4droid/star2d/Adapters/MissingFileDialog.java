package com.star4droid.star2d.Adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import android.net.Uri;

public class MissingFileDialog {
	public static String CP="CP",TEMPLATE="TEMPLATE";
	public static void showFor(final Context context,String type){
		View cv = LayoutInflater.from(context).inflate(R.layout.missing_file_dialog,null);
		AlertDialog dialog = new AlertDialog.Builder(context).create();
		dialog.setView(cv);
		Utils.hideSystemUi(dialog.getWindow());
		TextView text = cv.findViewById(R.id.text);
		String cp_path=FileUtil.getPackageDataDir(context)+"/bin/cp.jar";
		String template_path=FileUtil.getPackageDataDir(context)+"/apk/template.zip";
		String str=Utils.readAssetFile("java/missing-"+context.getString(R.string.lang)+".txt",context);
		text.setText(String.format(str,type.equals(CP)?cp_path:template_path));
		
		cv.findViewById(R.id.download).setOnClickListener(v->{
			Intent i= new Intent();
			i.setAction(Intent.ACTION_VIEW);
			String cp_link = "https://github.com/abodinagdat16/star2d-tools/releases/download/1.0.0/cp.jar";
			i.setData(Uri.parse(type.equals(CP)?cp_link:"template.."));
			context.startActivity(i);
		});
		dialog.show();
	}
}