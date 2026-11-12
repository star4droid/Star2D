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
import com.star4droid.star2d.CodeEditor.SmartEditor;
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
import io.github.rosemoe.sora.widget.SelectionMovement;
import java.io.File;
import java.io.FileInputStream;
import org.eclipse.tm4e.core.registry.IThemeSource;
/*
    Advanced code editor using tyron java completion library and sora editor.....
*/
public class CodeEditorActivity extends AppCompatActivity {

	/** The editor class that matches the orientation of the scene being edited (landscape / portrait). */
	public static Class<? extends CodeEditorActivity> forScene(){
		try {
			com.star4droid.star2d.editor.LibgdxEditor e = com.star4droid.star2d.editor.LibgdxEditor.getCurrentEditor();
			if(e != null) return e.isLandscape() ? CodeEditorLandscapeActivity.class : CodeEditorPortraitActivity.class;
		} catch(Throwable ignored){}
		return CodeEditorActivity.class;
	}

	/** The new script extends the scene's generated Refs base class: make sure that class exists (from the saved scene). */
	private void writeSceneRefs(String scene){
		try {
			String root = com.star4droid.star2d.CodeEditor.SmartProject.rootOf(path);
			if(root != null)
				com.star4droid.star2d.Helpers.CodeGenerator.writeRefs(new com.star4droid.star2d.Helpers.editor.Project(root), scene);
		} catch(Throwable ignored){}
	}

	CodeEditor editor;
	SmartEditor smart;
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
		// Najma (the Arabic language): X.njm is the source and has its own editor, X.java beside it is generated
		if(com.star4droid.star2d.najma.NajmaEditorActivity.openInstead(this, path, getIntent())){
			finish();
			return;
		}
		// a new element / scene script: Java or Najma? (the scene's Refs class must exist first: both extend it)
		try {
			if(!new File(path).exists()){
				if(getIntent().getBooleanExtra("body",false)) writeSceneRefs(Editor.getCurrentEditor().getScene());
				else if(getIntent().hasExtra("scene") && !getIntent().getStringExtra("scene").equals("")) writeSceneRefs(getIntent().getStringExtra("scene"));
			}
		} catch(Throwable ignored){}
		if(com.star4droid.star2d.najma.NajmaEditorActivity.askLanguage(this, path, getIntent())) return;
		// a binary or huge file would freeze / crash the editor: show it by pages instead
		try {
			File big = new File(path);
			if(big.isFile() && (com.star4droid.star2d.ui.PagedFileDialog.looksBinary(big) || big.length() > 1500 * 1024)){
				androidx.appcompat.app.AlertDialog pd = com.star4droid.star2d.ui.PagedFileDialog.show(this, big, null);
				if(pd != null) pd.setOnDismissListener(x -> finish()); else finish();
				return;
			}
		} catch(Throwable ignored){}
		// the bars (symbols + save) must stay above the keyboard (edge-to-edge ignores adjustResize)
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
			androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
			androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
			v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
			return androidx.core.view.WindowInsetsCompat.CONSUMED;
		});
		// no keyboard suggestions / auto-correct while coding
		editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
		try {
			io.github.rosemoe.sora.widget.component.EditorAutoCompletion ac = editor.getComponent(io.github.rosemoe.sora.widget.component.EditorAutoCompletion.class);
			ac.setEnabledAnimation(true);
			ac.setHighlightMatchedLabel(true);
		} catch(Throwable ignored){}
		if(android.text.TextUtils.getLayoutDirectionFromLocale(java.util.Locale.getDefault()) == android.view.View.LAYOUT_DIRECTION_RTL)
			findViewById(R.id.back).setScaleX(-1f);
		
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
					editor.moveSelection(SelectionMovement.RIGHT);
					break;
					case '←':
					editor.moveSelection(SelectionMovement.LEFT);
					break;
					case '↓':
					editor.moveSelection(SelectionMovement.DOWN);
					break;
					case '↑':
					editor.moveSelection(SelectionMovement.UP);
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
				}
			});
			symbolsLinear.addView(text);
		}
		
		try {
		File file = new File(path);
		if(FileUtil.readFile(path).equals("")){
			if(getIntent().getBooleanExtra("body",false)){
				String sceneName = Editor.getCurrentEditor().getScene();
				writeSceneRefs(sceneName);
				FileUtil.writeFile(path,String.format(Utils.readAssetFile("java/script.java",this),getIntent().getStringExtra("name"),sceneName,sceneName.toLowerCase()));
			}
			if(getIntent().hasExtra("scene") && !getIntent().getStringExtra("scene").equals("")){
				String sceneName = getIntent().getStringExtra("scene");
				writeSceneRefs(sceneName);
				FileUtil.writeFile(path,String.format(Utils.readAssetFile("java/SceneScript.java",this),sceneName,sceneName.toLowerCase()));
			}
		}
		if(file.exists()){
			FileInputStream fis=new FileInputStream(file);
			Content content=ContentIO.createFrom(fis);
			editor.setText(content);
		}// else editor.setText("Hello To Star2D..");
		} catch(Error | Exception ex){
			
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
		smart = new SmartEditor(this, editor, path, (TextView) findViewById(R.id.hint_bar), (TextView) findViewById(R.id.error_badge));
		EngineLanguage eng= new EngineLanguage(editor, smart);
		eng.setAutoCompletionEnabled(TestApp.getCurrentApp().preferences.getBoolean("Auto Completion",true));
		editor.setEditorLanguage(eng);
		smart.setBusyView(findViewById(R.id.init_progress));
		smart.start();
		if(getIntent().hasExtra("goto_line")){
			final int gl = getIntent().getIntExtra("goto_line", 1), gc = getIntent().getIntExtra("goto_col", 1), ge = getIntent().getIntExtra("goto_end", -1);
			final String gm = getIntent().getStringExtra("goto_msg");
			editor.post(() -> {
				smart.showExternalAt(gl, gc, ge, gm == null ? "" : gm);
				smart.goTo(gl, gc, ge);
			});
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
	
	@Override
	protected void onStop() {
		super.onStop();
		// leaving the editor: variables declared in a Body / Scene script show up in Properties (background scan, never blocks)
		try {
			com.star4droid.star2d.ui.behaviors.ScriptVarWatcher.onEditorClosed(path);
		} catch(Throwable ignored){}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(smart != null) smart.destroy();
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
				} catch(Error | Exception e) {
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