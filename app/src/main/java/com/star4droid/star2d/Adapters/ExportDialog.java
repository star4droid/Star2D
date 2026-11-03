package com.star4droid.star2d.Adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import com.star4droid.star2d.Helpers.ExportThread;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.FilePickerDialog;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import java.io.File;

/** Export dialog: game icon, identity fields, orientation and options. Built in code, portrait and landscape. */
public class ExportDialog {
	public static ImageView icon;
	public static int RECIEVE_ICON=109;
	public static String imgPath="";

	private static String t(String k, String d){ return Tr.t(k, d); }

	public static void showFor(final Context context,final String project,Uri savePath){
		Tr.init(context);
		final SharedPreferences sh = context.getSharedPreferences("export",Context.MODE_PRIVATE);
		final AlertDialog dialog = new AlertDialog.Builder(context).create();
		final boolean land = Ui.isLandscape(context);
		imgPath = currentIcon(project);

		// ---- header
		LinearLayout header = Ui.row(context);
		header.setPadding(Ui.dp(context,18),Ui.dp(context,14),Ui.dp(context,14),Ui.dp(context,12));
		header.setBackgroundColor(Ui.SURFACE_2);
		ImageView hi = Ui.smallIcon(context, R.drawable.ic_s2_export, Ui.ACCENT, 26);
		header.addView(hi);
		LinearLayout ht = Ui.column(context);
		ht.setPadding(Ui.dp(context,12),0,0,0);
		ht.addView(Ui.title(context,t("export_title","Export the game as APK")));
		TextView sub = Ui.text(context,new File(project).getName(),12.5f,Ui.TEXT_3);
		sub.setSingleLine(true);
		ht.addView(sub);
		header.addView(ht,Ui.weight(1));

		// ---- fields
		final EditText name = field(context,"My Game",InputType.TYPE_CLASS_TEXT);
		final EditText pkg = field(context,"com.project.MyGame",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		final EditText version = field(context,"1",InputType.TYPE_CLASS_NUMBER);
		final EditText versionName = field(context,"1.0",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		restoreField(sh,project,R.id.name,name,false);
		restoreField(sh,project,R.id.package_name,pkg,false);
		restoreField(sh,project,R.id.version,version,true);
		restoreField(sh,project,R.id.version_name,versionName,false);

		// ---- icon card
		LinearLayout iconCard = Ui.column(context);
		iconCard.setGravity(Gravity.CENTER_HORIZONTAL);
		iconCard.setPadding(Ui.dp(context,14),Ui.dp(context,14),Ui.dp(context,14),Ui.dp(context,14));
		iconCard.setBackground(Ui.rounded(Ui.SURFACE_2,Ui.dp(context,14),Ui.BORDER,Ui.dp(context,1)));
		FrameLayout iconFrame = new FrameLayout(context);
		icon = new ImageView(context);
		icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
		icon.setBackground(Ui.rounded(0xFF121417,Ui.dp(context,18)));
		icon.setClipToOutline(true);
		icon.setOutlineProvider(new android.view.ViewOutlineProvider(){
			@Override public void getOutline(View v, android.graphics.Outline o){ o.setRoundRect(0,0,v.getWidth(),v.getHeight(),Ui.dp(context,18)); }
		});
		iconFrame.addView(icon,new FrameLayout.LayoutParams(Ui.dp(context,84),Ui.dp(context,84)));
		ImageView badge = Ui.smallIcon(context,R.drawable.ic_s2_edit,Color.WHITE,14);
		badge.setBackground(Ui.rounded(Ui.ACCENT,Ui.dp(context,12)));
		badge.setPadding(Ui.dp(context,5),Ui.dp(context,5),Ui.dp(context,5),Ui.dp(context,5));
		FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(context,24),Ui.dp(context,24),Gravity.BOTTOM|Gravity.END);
		iconFrame.addView(badge,blp);
		iconCard.addView(iconFrame,new LinearLayout.LayoutParams(Ui.dp(context,84),Ui.dp(context,84)));
		TextView iconLabel = Ui.text(context,t("export_icon_change","Change the game icon"),13.5f,Ui.TEXT);
		iconLabel.setGravity(Gravity.CENTER);
		iconLabel.setPadding(0,Ui.dp(context,10),0,0);
		iconCard.addView(iconLabel);
		TextView iconHint = Ui.text(context,t("export_icon_hint","Square picture, from the project images"),11.5f,Ui.TEXT_3);
		iconHint.setGravity(Gravity.CENTER);
		iconCard.addView(iconHint);
		showIcon(project);
		iconCard.setOnClickListener(v->FilePickerDialog.show(context,t("export_icon_pick","Choose the icon"),project+"/images",new String[]{"jpg","png","jpeg"},true,(file,rel)->{
			try {
				File dir = new File(project+"/icon");
				dir.mkdirs();
				File[] old = dir.listFiles();
				if(old!=null) for(File o:old) o.delete();
				String n = file.getName().toLowerCase();
				String ext = n.endsWith(".jpg")||n.endsWith(".jpeg") ? ".jpg" : ".png";
				FileUtil.copyFile(file.getAbsolutePath(),dir.getAbsolutePath()+"/icon"+ext);
				imgPath = dir.getAbsolutePath()+"/icon"+ext;
				showIcon(project);
			} catch(Throwable ignored){}
		}));

		// ---- orientation (two choices)
		boolean portraitSaved = sh.getBoolean(project+"or",false);
		final boolean[] portrait = {portraitSaved};
		final LinearLayout seg = Ui.row(context);
		final TextView landBtn = segment(context,t("export_landscape","Landscape"));
		final TextView portBtn = segment(context,t("export_portrait","Portrait"));
		seg.addView(landBtn,Ui.weight(1));
		seg.addView(portBtn,Ui.weight(1));
		seg.setBackground(Ui.rounded(0xFF121417,Ui.dp(context,10),Ui.BORDER,Ui.dp(context,1)));
		seg.setPadding(Ui.dp(context,3),Ui.dp(context,3),Ui.dp(context,3),Ui.dp(context,3));
		final Runnable paintSeg = () -> { mark(context,landBtn,!portrait[0]); mark(context,portBtn,portrait[0]); };
		landBtn.setOnClickListener(v->{ portrait[0]=false; paintSeg.run(); });
		portBtn.setOnClickListener(v->{ portrait[0]=true; paintSeg.run(); });
		paintSeg.run();

		// ---- options
		final SwitchCompat merge = new SwitchCompat(context);
		merge.setChecked(sh.getBoolean(project+"-merge",false));
		final SwitchCompat arm = new SwitchCompat(context);
		arm.setChecked(sh.getBoolean(project+"-arm",false));
		View mergeRow = option(context,R.drawable.ic_s2_grid,t("export_merge_tilesets","Merge images into tilesets (smaller, faster)"),t("export_merge_desc","Packs small pictures into a few pages: faster loading"),merge);
		View armRow = option(context,R.drawable.ic_s2_gamepad,t("export_arm_only","Phones only (ARM): smaller APK, no emulator libraries"),t("export_arm_desc","Leaves out the PC / emulator libraries"),arm);

		LinearLayout form = Ui.column(context);
		form.addView(label(context,t("export_game_name","Game name")));
		form.addView(name,Ui.matchWrap());
		form.addView(label(context,t("export_package","Package name")));
		form.addView(pkg,Ui.matchWrap());
		LinearLayout vr = Ui.row(context);
		LinearLayout v1 = Ui.column(context), v2 = Ui.column(context);
		v1.addView(label(context,t("export_version_code","Version code")));
		v1.addView(version,Ui.matchWrap());
		v2.addView(label(context,t("export_version_name","Version name")));
		v2.addView(versionName,Ui.matchWrap());
		LinearLayout.LayoutParams l1 = Ui.weight(1), l2 = Ui.weight(1);
		l1.setMarginEnd(Ui.dp(context,6)); l2.setMarginStart(Ui.dp(context,6));
		vr.addView(v1,l1); vr.addView(v2,l2);
		form.addView(vr,Ui.matchWrap());
		form.addView(label(context,t("export_orientation","Screen orientation")));
		form.addView(seg,Ui.matchWrap());
		form.addView(label(context,t("export_options","Options")));
		form.addView(mergeRow,Ui.matchWrap());
		form.addView(armRow,Ui.matchWrap());

		LinearLayout body = new LinearLayout(context);
		body.setPadding(Ui.dp(context,16),Ui.dp(context,8),Ui.dp(context,16),Ui.dp(context,12));
		if(land){
			body.setOrientation(LinearLayout.HORIZONTAL);
			LinearLayout left = Ui.column(context);
			left.addView(iconCard,Ui.matchWrap());
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(context,190),ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin = Ui.dp(context,14); lp.setMarginEnd(Ui.dp(context,16));
			body.addView(left,lp);
			body.addView(form,Ui.weight(1));
		} else {
			body.setOrientation(LinearLayout.VERTICAL);
			LinearLayout.LayoutParams lp = Ui.matchWrap();
			lp.topMargin = Ui.dp(context,8);
			body.addView(iconCard,lp);
			body.addView(form,Ui.matchWrap());
		}
		ScrollView scroll = new ScrollView(context);
		scroll.addView(body);

		// ---- footer
		LinearLayout footer = Ui.row(context);
		footer.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
		footer.setPadding(Ui.dp(context,14),Ui.dp(context,10),Ui.dp(context,14),Ui.dp(context,12));
		footer.setBackgroundColor(Ui.SURFACE_2);
		LinearLayout cancel = Ui.button(context,0,t("export_cancel","Cancel"),false,v->dialog.dismiss());
		LinearLayout go = Ui.button(context,R.drawable.ic_s2_export,t("export_start","Export"),true,v->{
			String[] data = collect(context,project,portrait[0],version,versionName,pkg,name);
			if(data==null) return;
			sh.edit().putBoolean(project+"-arm",arm.isChecked()).putBoolean(project+"-merge",merge.isChecked()).apply();
			ExportThread.mergeTilesets = merge.isChecked();
			ExportThread.armOnly = arm.isChecked();
			new ExportThread(context,project,savePath,imgPath,data).start();
			dialog.dismiss();
		});
		LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(context,42));
		gp.setMarginStart(Ui.dp(context,10));
		footer.addView(cancel,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(context,42)));
		footer.addView(go,gp);

		LinearLayout root = Ui.column(context);
		root.setBackground(Ui.rounded(Ui.SURFACE,Ui.dp(context,16)));
		root.addView(header,Ui.matchWrap());
		root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
		root.addView(footer,Ui.matchWrap());
		dialog.setView(root);
		dialog.show();
		Window w = dialog.getWindow();
		if(w!=null){
			w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
			android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
			int width = land ? Math.min(dm.widthPixels-Ui.dp(context,48),Ui.dp(context,640)) : dm.widthPixels-Ui.dp(context,24);
			w.setLayout(width,ViewGroup.LayoutParams.WRAP_CONTENT);
			w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
			// body may need to scroll: cap the dialog height
			ViewGroup.LayoutParams rp = root.getLayoutParams();
			if(rp!=null){ rp.height = Math.min(dm.heightPixels-Ui.dp(context,40),Ui.dp(context,land?420:620)); root.setLayoutParams(rp); }
		}
	}

	private static String currentIcon(String project){
		File[] files = new File(project+"/icon/").listFiles();
		if(files!=null) for(File f:files){
			String n = f.getName().toLowerCase();
			if(n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")) return f.getAbsolutePath();
		}
		return "";
	}

	private static void showIcon(String project){
		if(icon==null) return;
		String p = currentIcon(project);
		if(!p.isEmpty()) FilePickerDialog.thumb(icon,new File(p),Ui.dp(icon.getContext(),84));
		else icon.setImageResource(R.drawable.icon);
	}

	private static EditText field(Context c,String def,int type){
		EditText e = Ui.input(c,def,"");
		e.setInputType(type);
		e.setTextSize(15);
		e.setPadding(Ui.dp(c,12),Ui.dp(c,10),Ui.dp(c,12),Ui.dp(c,10));
		return e;
	}

	private static TextView label(Context c,String s){
		TextView t = Ui.text(c,s,12.5f,Ui.TEXT_2);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		t.setPadding(Ui.dp(c,2),Ui.dp(c,12),Ui.dp(c,2),Ui.dp(c,5));
		return t;
	}

	private static TextView segment(Context c,String s){
		TextView t = Ui.text(c,s,14,Ui.TEXT_2);
		t.setGravity(Gravity.CENTER);
		t.setMinHeight(Ui.dp(c,38));
		t.setClickable(true);
		return t;
	}

	private static void mark(Context c,TextView t,boolean on){
		t.setBackground(on?Ui.rounded(Ui.ACCENT,Ui.dp(c,8)):null);
		t.setTextColor(on?Color.WHITE:Ui.TEXT_2);
		t.setTypeface(on?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
	}

	private static View option(Context c,int ic,String title,String desc,final SwitchCompat sw){
		LinearLayout row = Ui.row(c);
		row.setPadding(Ui.dp(c,12),Ui.dp(c,10),Ui.dp(c,10),Ui.dp(c,10));
		row.setBackground(Ui.rounded(Ui.SURFACE_2,Ui.dp(c,12),Ui.BORDER,Ui.dp(c,1)));
		row.addView(Ui.smallIcon(c,ic,Ui.ACCENT,22));
		LinearLayout tx = Ui.column(c);
		tx.setPadding(Ui.dp(c,12),0,Ui.dp(c,8),0);
		TextView a = Ui.text(c,title,14,Ui.TEXT);
		TextView b = Ui.text(c,desc,12,Ui.TEXT_3);
		tx.addView(a); tx.addView(b);
		row.addView(tx,Ui.weight(1));
		row.addView(sw);
		row.setClickable(true);
		row.setOnClickListener(v->sw.toggle());
		LinearLayout.LayoutParams lp = Ui.matchWrap();
		lp.bottomMargin = Ui.dp(c,8);
		row.setLayoutParams(lp);
		return row;
	}

	private static void restoreField(SharedPreferences sh,String project,int id,EditText e,boolean number){
		String s = sh.getString(project+id,"");
		if(s.isEmpty()) return;
		try { e.setText(number?(com.star4droid.star2d.Utils.getInt(s)+""):s); } catch(Exception ex){}
	}

	/** [version, versionName, package, name, orientation] or null (an empty field is marked). */
	private static String[] collect(Context context,String project,boolean portrait,EditText version,EditText versionName,EditText pkg,EditText name){
		EditText[] f = {version,versionName,pkg,name};
		int[] ids = {R.id.version,R.id.version_name,R.id.package_name,R.id.name};
		String[] out = new String[5];
		SharedPreferences.Editor ed = context.getSharedPreferences("export",Context.MODE_PRIVATE).edit();
		boolean ok = true;
		for(int i=0;i<4;i++){
			out[i] = f[i].getText().toString().trim();
			if(out[i].isEmpty()){
				f[i].setError(t("export_enter_value","Enter a value"));
				ok = false;
			} else ed.putString(project+ids[i],out[i]);
		}
		if(!ok) return null;
		ed.putBoolean(project+"or",portrait);
		if(FileUtil.isExistFile(imgPath)) ed.putString(project+"-image",imgPath);
		ed.commit();
		out[4] = portrait?"1":"0";
		return out;
	}
}
