package com.star4droid.star2d.Activities;

import android.Manifest;
import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Utils;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.*;
import org.json.*;
import com.star4droid.star2d.evo.R;
/*
    This Activity Made by sketchware, so it's not clean code
    TODO : re-create it as Libgdx Actor
*/
public class AnimationActivity extends AppCompatActivity {
	
	private Timer _timer = new Timer();
	
	private String path = "";
	private boolean load = false;
	private HashMap<String, Object> map = new HashMap<>();
	private double prev = 0;
	private String imgsPath = "";
	
	private ArrayList<HashMap<String, Object>> pictures = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> anims = new ArrayList<>();
	private ArrayList<String> interalPath = new ArrayList<>();
	
	private LinearLayout linear1;
	private LinearLayout linear8;
	private LinearLayout linear5;
	private LinearLayout out;
	private LinearLayout linear9;
	private TextView textview1;
	private LinearLayout linear2;
	private LinearLayout result;
	private ImageView refresh;
	private TextView textview4;
	private EditText edittext1;
	private LinearLayout linear6;
	private LinearLayout linear7;
	private TextView textview2;
	private ListView listview1;
	private TextView textview3;
	private ListView animationsList;
	
	private TimerTask tm;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Utils.setLanguage(this);
		setContentView(R.layout.animation);
		initialize(_savedInstanceState);
		
		
		if (Build.VERSION.SDK_INT >= 23) {
			if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
			||checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
				requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
				} else {
				initializeLogic();
			}
			} else {
			initializeLogic();
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == 1000) {
			initializeLogic();
		}
	}
	
	private void initialize(Bundle _savedInstanceState) {
		linear1 = findViewById(R.id.linear1);
		linear8 = findViewById(R.id.linear8);
		linear5 = findViewById(R.id.linear5);
		out = findViewById(R.id.out);
		linear9 = findViewById(R.id.linear9);
		textview1 = findViewById(R.id.textview1);
		linear2 = findViewById(R.id.linear2);
		result = findViewById(R.id.result);
		refresh = findViewById(R.id.refresh);
		textview4 = findViewById(R.id.textview4);
		edittext1 = findViewById(R.id.edittext1);
		linear6 = findViewById(R.id.linear6);
		linear7 = findViewById(R.id.linear7);
		textview2 = findViewById(R.id.textview2);
		listview1 = findViewById(R.id.listview1);
		textview3 = findViewById(R.id.textview3);
		animationsList = findViewById(R.id.animationsList);
		
		refresh.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				_refreshAnimation();
			}
		});
		
		edittext1.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				final String _charSeq = _param1.toString();
				try {
					tm.cancel();
					} catch (Exception e) {
					
				}
				tm = new TimerTask() {
					@Override
					public void run() {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								if(prev==0) prev=450;
								try {
									prev = _toNumber(edittext1.getText().toString());
									for(HashMap<String, Object> hash:anims){
										hash.put("dur",edittext1.getText().toString());
									}
									FileUtil.writeFile(path, new Gson().toJson(anims));
									_refreshAnimation();
									} catch (Exception e) {
									edittext1.setText(String.valueOf((long)(prev)));
								}
							}
						});
					}
				};
				_timer.schedule(tm, 1000);
			}
			
			@Override
			public void beforeTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				
			}
			
			@Override
			public void afterTextChanged(Editable _param1) {
				
			}
		});
	}
	
	private void initializeLogic() {
		/**/
		/*
		out.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b) { this.setCornerRadius(a); this.setColor(b); return this; } }.getIns((int)25, 0xFFFFFFFF));
		android.graphics.drawable.GradientDrawable xx = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT , new int[] {0xFF0078FE,0xFF0078FE});
		xx.setCornerRadii(new float[] { (float)(25), (float)(25), (float)(25), (float)(25), (float)(0),(float)(0),(float)(0),(float)(0) }); //LeftTop, //RightTop, //RightBottom, //LeftBottom,
		textview1.setBackground(xx);
		*/
		path = getIntent().getStringExtra("path");
		animationsList.setAdapter(new AnimationsListAdapter(anims));
		imgsPath = getIntent().getStringExtra("imgs");
		if (!FileUtil.readFile(getIntent().getStringExtra("path")).equals("")) {
			anims = new Gson().fromJson(FileUtil.readFile(getIntent().getStringExtra("path")), new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
			animationsList.setAdapter(new AnimationsListAdapter(anims));
		}
		_refresh_images();
		try {
			if (anims.size() > 0) {
				edittext1.setText(anims.get(0).get("dur").toString());
			}
			} catch (Exception e) {
			
		}
		_refreshAnimation();
	}
	
	public void _setImageFromFile(final ImageView _img, final String _path) {
		java.io.File file = new java.io.File(_path);
		Uri imageUri = Uri.fromFile(file);
		
		Glide.with(getApplicationContext ()).asBitmap().load(imageUri).into(_img);
		/**/
		/*
		
		class th extends Thread {
			@Override
			public void run(){
				final Bitmap bit = FileUtil.decodeSampleBitmapFromPath(_path, 1024, 1024);
				new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						_img.setImageBitmap(bit);
					}
				});
			}
		}
		new th().start();
		*/
	}
	
	
	public void _refreshAnimation() {
		if (!load) {
			class th extends Thread {
				@Override
				public void run(){
					load=true;
					final android.graphics.drawable.AnimationDrawable sh = new android.graphics.drawable.AnimationDrawable();
					int x=-1;
					for(HashMap<String, Object> hash:anims){
						x++;
						try {
							sh.addFrame(Drawable.createFromPath(imgsPath+"/"+hash.get("name").toString().replace(Utils.seperator,"/")),(int) prev);
							} catch (Exception e) {
							
						}
					}
					sh.setOneShot(!(true));
					new Handler(Looper.getMainLooper()).post(new Runnable() {
						@Override
						public void run() {
							if (0 == anims.size()) {
								result.setBackgroundResource(R.drawable.ic_theaters_black);
							}
							else {
								if (android.os.Build.VERSION.SDK_INT < 16) {
									result.setBackgroundDrawable(sh);
									} else {
									result.setBackground(sh);
								}
								// start animation on image
								result.post(new Runnable() {
									
									@Override
									public void run() {
										sh.start();
									}
									
								});
							}
						}
					});
					load = false;
				}
			}
			new th().start();
		}
	}
	
	
	public double _toNumber(final String _str) {
		String str = replaceNonstandardDigits(_str);
		try {
			return Double. parseDouble(str);
			} catch(Exception ex){
			String err1=Log.getStackTraceString(ex);
			try {
				NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
				return nf.parse(str).doubleValue();
				} catch(java.text.ParseException e){
				throw new RuntimeException(err1+"\n"+Log.getStackTraceString(e));
				//return 0;
			}
		}
	}
	public float _toNumberf(String _str){
		String str = replaceNonstandardDigits(_str);
		try {
			return Float. parseFloat(str);
			} catch(Exception ex){
			String err1=Log.getStackTraceString(ex);
			try {
				NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
				return nf.parse(str).floatValue();
				} catch(java.text.ParseException e){
				throw new RuntimeException(err1+"\n"+Log.getStackTraceString(e));
			}
		}
	}
	public String replaceNonstandardDigits(String input) {
		if(input.equals("")) return "0";
		if (input == null || input.isEmpty()) {
			return input;
		}
		
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char ch = input.charAt(i);
			if (isNonstandardDigit(ch)) {
				int numericValue = Character.getNumericValue(ch);
				if (numericValue >= 0) {
					builder.append(numericValue);
				}
				} else {
				builder.append(ch);
			}
		}
		return builder.toString().replace(",",".").replace("٫",".");
	}
	
	private boolean isNonstandardDigit(char ch) {
		return Character.isDigit(ch) && !(ch >= '0' && ch <= '9');
	}
	
	
	public void _refresh_images() {
		pictures.clear();
		ArrayList<String> arr= new ArrayList<>();
		String p="";
		for(String s:interalPath) p = p+"/"+s;
		FileUtil.listDir(imgsPath.concat(p), arr);
		Collections.sort(arr);
		if (interalPath.size() > 0) {
				HashMap<String, Object> _item = new HashMap<>();
				_item.put("name", "...");
				pictures.add(_item);
		}
		for(String s:arr){
				HashMap<String, Object> _item = new HashMap<>();
				_item.put("name", s);
				pictures.add(_item);
		}
		listview1.setAdapter(new Listview1Adapter(pictures));
	}
	
	public class Listview1Adapter extends BaseAdapter {
		
		ArrayList<HashMap<String, Object>> _data;
		
		public Listview1Adapter(ArrayList<HashMap<String, Object>> _arr) {
			_data = _arr;
		}
		
		@Override
		public int getCount() {
			return _data.size();
		}
		
		@Override
		public HashMap<String, Object> getItem(int _index) {
			return _data.get(_index);
		}
		
		@Override
		public long getItemId(int _index) {
			return _index;
		}
		
		@Override
		public View getView(final int _position, View _v, ViewGroup _container) {
			LayoutInflater _inflater = getLayoutInflater();
			View _view = _v;
			if (_view == null) {
				_view = _inflater.inflate(R.layout.list_csv, null);
			}
			
			final LinearLayout linear1 = _view.findViewById(R.id.linear1);
			final ImageView imageview1 = _view.findViewById(R.id.imageview1);
			final TextView name = _view.findViewById(R.id.name);
			final LinearLayout delLin = _view.findViewById(R.id.delLin);
			final LinearLayout backupLin = _view.findViewById(R.id.backupLin);
			final LinearLayout export = _view.findViewById(R.id.export);
			final ImageView imageview2 = _view.findViewById(R.id.imageview2);
			final ImageView imageview3 = _view.findViewById(R.id.imageview3);
			final ImageView imageview4 = _view.findViewById(R.id.imageview4);
			
			linear1.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(15, 0, 0xFFFFB300, 0xFF222222));
			backupLin.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(15, 0, 0xFFFFB300, 0xFF515151));
			export.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(15, 0, 0xFFFFB300, 0xFF515151));
			delLin.setBackground(new GradientDrawable() { public GradientDrawable getIns(int a, int b, int c, int d) { this.setCornerRadius(a); this.setStroke(b, c); this.setColor(d); return this; } }.getIns(15, 0, 0xFFFFB300, 0xFF515151));
			backupLin.setVisibility(View.GONE);
			if (_data.get(_position).get("name").toString().equals("...")) {
				imageview1.setImageResource(R.drawable.right_icon);
				delLin.setVisibility(View.GONE);
			}
			else {
				if (FileUtil.isDirectory(_data.get(_position).get("name").toString())) {
					imageview1.setImageResource(R.drawable.ic_folder_white);
				}
				else {
					_setImageFromFile(imageview1, _data.get(_position).get("name").toString());
				}
				delLin.setVisibility(View.VISIBLE);
			}
			name.setText(Uri.parse(_data.get(_position).get("name").toString()).getLastPathSegment());
			linear1.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View _view) {
					if (_data.get(_position).get("name").toString().equals("...")) {
						try {
							interalPath.remove(interalPath.size() - 1);
							} catch (Exception e) {
							
						}
						_refresh_images();
					}
					else {
						if (FileUtil.isDirectory(_data.get(_position).get("name").toString())) {
							interalPath.add(Uri.parse(_data.get(_position).get("name").toString()).getLastPathSegment());
							_refresh_images();
						}
						else {
							map = new HashMap<>();
							String p="";
							for(String s:interalPath) p+=Utils.seperator+s;
							map.put("name", p+Uri.parse(_data.get(_position).get("name").toString()).getLastPathSegment());
							//map.put("image_interal", p);
							map.put("dur", edittext1.getText().toString());
							anims.add(map);
							FileUtil.writeFile(path, new Gson().toJson(anims));
							_refreshAnimation();
							((BaseAdapter)animationsList.getAdapter()).notifyDataSetChanged();
						}
					}
				}
			});
			
			return _view;
		}
	}
	
	public class AnimationsListAdapter extends BaseAdapter {
		
		ArrayList<HashMap<String, Object>> _data;
		
		public AnimationsListAdapter(ArrayList<HashMap<String, Object>> _arr) {
			_data = _arr;
		}
		
		@Override
		public int getCount() {
			return _data.size();
		}
		
		@Override
		public HashMap<String, Object> getItem(int _index) {
			return _data.get(_index);
		}
		
		@Override
		public long getItemId(int _index) {
			return _index;
		}
		
		@Override
		public View getView(final int _position, View _v, ViewGroup _container) {
			LayoutInflater _inflater = getLayoutInflater();
			View _view = _v;
			if (_view == null) {
				_view = _inflater.inflate(R.layout.anim_csv, null);
			}
			
			final LinearLayout linear1 = _view.findViewById(R.id.linear1);
			final ImageView img = _view.findViewById(R.id.img);
			final ImageView down = _view.findViewById(R.id.down);
			final ImageView up = _view.findViewById(R.id.up);
			final ImageView del = _view.findViewById(R.id.del);
			
			up.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View _view) {
					if (_position > 0) {
						Collections.swap(anims,_position,_position - 1);
						FileUtil.writeFile(path, new Gson().toJson(anims));
						_refreshAnimation();
						notifyDataSetChanged();
					}
				}
			});
			down.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View _view) {
					if ((anims.size() - 1) > _position) {
						Collections.swap(anims, (_position), (_position + 1));
						FileUtil.writeFile(path, new Gson().toJson(anims));
						_refreshAnimation();
						notifyDataSetChanged();
					}
				}
			});
			_setImageFromFile(img, imgsPath+"/"+_data.get(_position).get("name").toString().replace(Utils.seperator,"/"));
			del.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View _view) {
					anims.remove(_position);
					FileUtil.writeFile(path, new Gson().toJson(anims));
					_refreshAnimation();
					notifyDataSetChanged();
				}
			});
			
			return _view;
		}
	}
}