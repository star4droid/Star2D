package com.star4droid.star2d.Adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import com.star4droid.star2d.evo.R;
import androidx.appcompat.app.AlertDialog;
import com.star4droid.star2d.Utils;

public class SPNote {
	public static void show(Context context){
		if(Utils.getRandom(1,15)==12){
			final AlertDialog dialog = new AlertDialog.Builder(context).create();
			View v = LayoutInflater.from(context).inflate(R.layout.support_dialog,null);
			dialog.setView(v);
			dialog.setCancelable(false);
			Utils.hideSystemUi(dialog.getWindow());
			final Button cancel = v.findViewById(R.id.cancel);
            v.findViewById(R.id.payeer).setOnClickListener(fv->{
                Utils.showMessage(context,"Thanks for your help.\n Payeer : P1108466613 (Copied To Clipboard)");
                ClipboardManager manager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                manager.setPrimaryClip(ClipData.newPlainText("clipboard","P1108466613"));
            });
			buttonLink(v.findViewById(R.id.yt),"https://youtube.com/channel/UCgnoNpqgejMZbqDLtr8NNUw");
			buttonLink(v.findViewById(R.id.contact),"");
			new Thread(){
				@Override
				public void run(){
					int tm=10;
					while(tm>0){
						final int t=tm;
						post(()->{
							cancel.setText("Cancel ("+t+")");
						});
						tm--;
						try {
							Thread.sleep(1000);
						} catch(InterruptedException exception){}
					}
					post(()->{
						cancel.setText("Cancel");
						cancel.setOnClickListener(v->{
							dialog.dismiss();
						});
					});
				}
			}.start();
			dialog.show();
		}
	}
	
	private static void post(Runnable runnable){
		new Handler(Looper.getMainLooper()).post(runnable);
	}
	
	private static void buttonLink(View button,String link){
		button.setOnClickListener(v->{
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setData(Uri.parse(link));
			button.getContext().startActivity(intent);
		});
	}
}