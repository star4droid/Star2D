package com.star4droid.star2d;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.util.SparseBooleanArray;
import android.net.Uri;
import com.star4droid.star2d.evo.R;
import java.util.ArrayList;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.List;
import android.app.Activity;


public class DebugActivity extends Activity {
	
	private LinearLayout linear1;
	private ScrollView vscroll1;
	private LinearLayout linear2;
	private ImageView imageview1;
	private TextView textview1;
	private TextView textview2;
	private Button button1;
	private Button button2;
	
	private Intent intent = new Intent();
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.debug);
		initialize(_savedInstanceState);
		initializeLogic();
	}
	
	private void initialize(Bundle _savedInstanceState) {
		linear1 = findViewById(R.id.linear1);
		vscroll1 = findViewById(R.id.vscroll1);
		linear2 = findViewById(R.id.linear2);
		imageview1 = findViewById(R.id.imageview1);
		textview1 = findViewById(R.id.textview1);
		textview2 = findViewById(R.id.textview2);
		button1 = findViewById(R.id.button1);
		button2 = findViewById(R.id.button2);
		
		button1.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				intent.setAction(Intent.ACTION_VIEW);
				intent.setData(Uri.parse("https://m.me/Star4Droid2"));
				startActivity(intent);
			}
		});
		
		button2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("clipboard", textview2.getText().toString()));
				button2.setText("Copied ✅");
			}
		});
	}
	
	private void initializeLogic() {
		textview2.setText(getIntent().getStringExtra("error"));
	}
	
	
	
	}