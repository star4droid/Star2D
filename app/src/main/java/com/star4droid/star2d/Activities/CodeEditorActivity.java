package com.star4droid.star2d.Activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.star4droid.star2d.CodeEditor.EngineLanguage;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.File;
import java.io.FileInputStream;
import org.eclipse.tm4e.core.registry.IThemeSource;
/*
    Advanced code editor using tyron java completion library and sora editor.....
*/
public class CodeEditorActivity extends AppCompatActivity {
	CodeEditor editor;
	String path;
	View save;
	TextView save_info;
	@Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);
		Utils.setLanguage(this);
		setContentView(R.layout.code_editor);
		save = findViewById(R.id.save);
		save_info = findViewById(R.id.save_info);
		path = getIntent().getStringExtra("path");
		editor = findViewById(R.id.editor);
		
		findViewById(R.id.keyboard).setOnClickListener(v->{
			if(!showKeyboard())
				hideKeyboard();
		});
		
		findViewById(R.id.back).setOnClickListener(v->{
			back();
		});
		getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
			@Override
			public void handleOnBackPressed() {
				back();
			}
		});
		//adding symbols
		LinearLayout symbolsLinear = findViewById(R.id.symbols);
		symbolsLinear.getChildAt(0).setOnClickListener(v->{
			editor.insertText("    ",4);
			//editor.moveSelectionRight();
		});
		String smbls="{};:()\"'[]%+-*/=&|!?⬿⤳↑↓←→";
		for(char c:smbls.toCharArray()){
			TextView text = new TextView(this);
			int pd = Utils.convertPixelsToDp(this,8);
			int hg = Utils.convertPixelsToDp(this,40);
			int wd = Utils.convertPixelsToDp(this,50);
			text.setTextColor(Color.WHITE);
			text.setPadding(pd,pd,pd,pd);
			text.setLayoutParams(new LinearLayout.LayoutParams(wd,hg));
			text.setText(c+"");
			text.setOnClickListener(v->{
				switch(c){
					case '→':
					editor.moveSelectionRight();
					break;
					case '←':
					editor.moveSelectionLeft();
					break;
					case '↓':
					editor.moveSelectionDown();
					break;
					case '↑':
					editor.moveSelectionUp();
					break;
					case '⬿':
					if(editor.canUndo())
						editor.undo();
					break;
					case '⤳':
					if(editor.canRedo())
						editor.redo();
					break;
					default :
						editor.insertText(""+c,1);
				//editor.moveSelectionRight();
				}
			});
			symbolsLinear.addView(text);
		}
		
		try {
		File file = new File(path);
		if(FileUtil.readFile(path).equals("") && getIntent().getBooleanExtra("body",false)){
			FileUtil.writeFile(path,String.format(Utils.readAssetFile("java/script.java",this),getIntent().getStringExtra("name"),Editor.getCurrentEditor().getScene()));
		}
		if(file.exists()){
			FileInputStream fis=new FileInputStream(file);
			Content content=ContentIO.createFrom(fis);
			editor.setText(content);
		}// else editor.setText("Hello To Star2D..");
		} catch(java.io.IOException ex){
			
		}
		findViewById(R.id.rotate).setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				setRequestedOrientation(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); 
				
			}
		});
		
		try {
		loadTMThemes(this);
		TextMateColorScheme scheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
		editor.setColorScheme(scheme);
		if(TestApp.getCurrentApp().preferences.getBoolean("Auto Completion",true)){
		TextMateLanguage language = TextMateLanguage.create(GrammarRegistry.getInstance().loadGrammars("editor/languages.json").get(0).getScopeName(), true);
		//language.setCompleterKeywords(new String[]{"hello","hii","holaaaa","idk"});
		language.setAutoCompleteEnabled(true);
		EngineLanguage eng= new EngineLanguage(editor/*,getIntent().getStringArrayListExtra("list")*/,path);
		
		editor.setEditorLanguage(eng);
		}
		} catch(Exception ex){
			//Utils.showMessage(this,"Error : \n"+Log.getStackTraceString(ex));
			//editor.setEditorLanguage();
		}
		
		save.setOnClickListener(view->{
			FileUtil.writeFile(path,editor.getText().toString());
			save_info.animate().setStartDelay(0).scaleY(1).setDuration(300).withEndAction(new Runnable(){
				@Override
				public void run() {
					save_info.animate().setStartDelay(300).setDuration(300).scaleY(0).withEndAction(null).start();
				}
			}).start();
		});
	}
	
	/*@Override
	public void onBackPressed() {
		super.onBackPressed();
		if(Build.VERSION.SDK_INT<31) back();
	}*/
	
	private void back(){
		if(!FileUtil.readFile(path).equals(editor.getText().toString())){
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setPositiveButton("Save",new DialogInterface.OnClickListener(){
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					FileUtil.writeFile(path,editor.getText().toString());
					finish();
				}
			});
			dialog.setMessage("Do you want to save edits ?");
			dialog.setNegativeButton("Cancel",(dl,i)->{
				
			});
			dialog.setNeutralButton("Exit",(dl,i)->{
				finish();
			});
			dialog.create().show();
		} else finish();
	}
	
	//by abodi Nagdat... :) 
	public static void loadTMThemes(Context context) {
		FileProviderRegistry.getInstance()
		.addFileProvider(new AssetsFileResolver(context.getAssets()));
		ThemeRegistry scheme = ThemeRegistry.getInstance();
		String light = "editor/QuietLight.tmTheme";
		for (String theme : new String[] { light}) {
			String path =  theme;
			try {
				scheme.loadTheme(
				new ThemeModel(
				IThemeSource.fromInputStream(
				FileProviderRegistry.getInstance().tryGetInputStream(path),
				path,
				null),
				theme));
				} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		scheme.setTheme(light);
	}
	
	public boolean hideKeyboard() {
		InputMethodManager _inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if(_inputMethodManager.isActive()) return false;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){
			_inputMethodManager.hideSoftInputFromWindow(editor.getWindowToken(),InputMethodManager.HIDE_IMPLICIT_ONLY);
		} else
			_inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
		return true;
	}
	
	public boolean showKeyboard() {
		InputMethodManager _inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if(!_inputMethodManager.isActive()) return false;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){
			_inputMethodManager.showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);
		} else
			_inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
		return true;
	}
}