package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.star4droid.star2d.Helpers.rtl.RtlController;
import com.star4droid.star2d.Helpers.rtl.RtlFreeTypeFontGenerator;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.template.Utils.Utils;
import java.util.HashMap;

public class ThemeLoader {
  public static BitmapFont noRtlFont;
  public static void loadTheme() {
    if (VisUI.isLoaded()) return;

    /*
    HashMap<String,int[]> splitsMap = new HashMap<>();
    FileHandle orange = Gdx.files.internal("files/skins/orange");
    Skin skin = new Skin(),
    	vis = new Skin();
    loadMain(vis);
    String splits = orange.child("splits.json").readString();
    for(String str:splits.split("\n")){
    	int[] v = new int[4];
    	String name = str.split(":")[0];
    	String[] values = str.split(":")[1].replace(" ","").split(",");
    	try {
    		for(int i = 0; i < 4; i++)
    			v[i] = Utils.getInt(values[i]);
    	} catch(Exception ex){}
    	splitsMap.put(name,v);
    }
    // if there's Vis Resource with the same name then replace it
    log("replace","",false);
    log("remove","",false);
    for(FileHandle fh:orange.list()){
    	String nameWithoutExt = fh.nameWithoutExtension().replace(".9","");
    	if(fh.name().endsWith(".png") || fh.name().endsWith(".jpg"))
    		if(vis.has(nameWithoutExt,Drawable.class) || vis.has(nameWithoutExt,NinePatch.class)){
    			boolean nine = fh.name().endsWith(".9.png");
    			//remove(nameWithoutExt);
    			int[] sps = nine ? splitsMap.get(nameWithoutExt) : null;
    			if(nine && sps == null)
    				log("replace","not found patch : "+nameWithoutExt,true);
    			else log("replace","patch found : "+nameWithoutExt,true);
    			skin.add(nameWithoutExt,nine ? ninePatch(fh,sps):drawable(fh),nine ? NinePatch.class : Drawable.class);
    			log("replace","replace : "+fh.name()+",name : "+nameWithoutExt,true);
    		} else log("replace","keep : "+fh.name(),true);
    }

    // replace Vis Resources by new resources based on json file
    // check-on:orange-check-on
    // this means: replace check-on from VisUI by orange-check-on from the folder
    String json = orange.sibling("orange.json").readString();
    log("json","",false);
    for(String line:json.split("\n")){
    	try {
    		if(line.startsWith("#") || !line.contains(":")) continue;
    		String name = line.split(":")[0].trim().replace(" ","");
    		if(name.equals("name")) continue;
    		String value = line.split(":")[1].trim().replace(" ","");
    		int start = value.contains("/") ? value.lastIndexOf("/") : 0;
    		String valuePure = start == 0 ? value.substring(0,value.indexOf(".")):value.substring(start+1,value.indexOf(".",start));

    		boolean drawable = vis.has(valuePure,Drawable.class);
    		boolean patch = vis.has(valuePure,NinePatch.class);
    		if(drawable || patch){
    			//remove(name);
    			skin.add(name,drawable ? vis.getDrawable(valuePure) : (patch ? vis.getPatch(valuePure) : null),drawable ? Drawable.class : NinePatch.class);
    			log("json","add : "+name+", assets value : "+value,true);
    		} else {
    			FileHandle child = orange.child(value);
    			boolean isNinePatch =  value.endsWith(".9.png");
    			if(child.exists()){
    				int[] sps = isNinePatch ? splitsMap.get(name) : null;
    				Object object = isNinePatch ? ninePatch(child,sps) : drawable(child);
    				//remove(name);
    				skin.add(valuePure,object,isNinePatch ? NinePatch.class : Drawable.class);
    				skin.add(name,object, isNinePatch ? NinePatch.class : Drawable.class);
    				log("json","add from assets : "+name+", asset : "+value,true);
    			} else log("json","not found : "+name+", value : "+value,true);
    		}
    	} catch(Exception e){
    		log("json","error : "+e.toString()+"\nline : "+line,true);
    	}
    }
    */
    Skin skin = new Skin();
    // load font
    RtlFreeTypeFontGenerator generator =
        new RtlFreeTypeFontGenerator(Utils.internal("files/ApercuArabicPro-Regular.otf"));
    RtlFreeTypeFontGenerator.FreeTypeFontParameter parameter =
        new RtlFreeTypeFontGenerator.FreeTypeFontParameter();
    parameter.size = 30; // visui => small : 24, default : 30
    parameter.characters += RtlController.getInstance().getAllRtlChars();
    BitmapFont font = generator.generateRtlFont(parameter);
    parameter.size = 24;
    BitmapFont small = generator.generateRtlFont(parameter);
    skin.add("small-font", small, BitmapFont.class);
    skin.add("default-font", font, BitmapFont.class);
	
	// load no rtl font
	loadNoRtlFont();
	skin.add("no-rtl",noRtlFont,BitmapFont.class);
	
    loadMain(skin);
    VisUI.load(skin);

    // VisUI.load(VisUI.SkinScale.X2);

    // load all images from assets...
    loadTheme(Gdx.files.internal("images"), "", null);
	Lang.loadTrans(TestApp.getCurrentApp().preferences.getString("lang","en"));
  }
  
  public static BitmapFont getNoRtlFont(){
	  return VisUI.getSkin().get("no-rtl",BitmapFont.class);
  }
  
  private static void loadNoRtlFont(){
	  FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
	  parameter.size = 30;
	  FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("files/ApercuArabicPro-Regular.otf"));
	  noRtlFont = generator.generateFont(parameter);
  }

  private static void loadMain(Skin skin) {
    FileHandle skinFile =
        Gdx.files.internal("files/skins/visui/uiskin.json"); // VisUI.SkinScale.X2.getSkinFile();
    FileHandle atlasFile = skinFile.sibling(skinFile.nameWithoutExtension() + ".atlas");
    if (atlasFile.exists()) {
      skin.addRegions(new TextureAtlas(atlasFile));
    }

    skin.load(skinFile);
  }

  public static void remove(String name) {
    boolean remove = false;
    if (VisUI.getSkin().has(name, Drawable.class)) {
      VisUI.getSkin().remove(name, Drawable.class);
      log("remove", "removed drawble : " + name, true);
      remove = true;
    }
    if (VisUI.getSkin().has(name, NinePatch.class)) {
      VisUI.getSkin().remove(name, NinePatch.class);
      log("remove", "removed patch : " + name, true);
      remove = true;
    }
    if (!remove) log("remove", "not found : " + name, true);
  }

  private static void loadTheme(
      FileHandle fileHandle, String dir, HashMap<String, int[]> splitsMap) {
    for (FileHandle fh : fileHandle.list()) {
      if (fh.isDirectory()) {
        loadTheme(fh, dir + fh.name() + "/", splitsMap);
        continue;
      }
      if (!fh.name().contains(".")) {
        Gdx.files
            .external("logs/drawable.txt")
            .writeString(
                "not contains \".\" name : " + fh.name() + "\n" + "__".repeat(10) + "\n", true);
        continue;
      }
      String name = fh.name().substring(0, fh.name().indexOf("."));
      String nameL = fh.name().toLowerCase();
      if (nameL.endsWith(".png") || nameL.endsWith(".jpg")) {
        boolean nine = nameL.endsWith(".9.png");
        int[] sps =
            (nine && splitsMap != null && splitsMap.containsKey("name"))
                ? splitsMap.get(name)
                : new int[]{8,8,8,8};
        // if(!VisUI.getSkin().has(name,nine?NinePatch.class:Drawable.class)){
        VisUI.getSkin()
            .add(
                name,
                nine ? ninePatch(fh, sps) : drawable(fh),
                nine ? NinePatch.class : Drawable.class);
        // Gdx.files.external("logs/drawable.txt").writeString("add name :
        // "+name+"\n"+"__".repeat(10)+"\n",true);
        // }
      }
    }
  }

  public static void log(String tag, String log, boolean append) {
    Gdx.files.external("logs/" + tag + ".txt").writeString(log + "\n", append);
  }

  private static NinePatch ninePatch(FileHandle fileHandle, int[] splits) {
    return new NinePatch(new Texture(fileHandle), splits[0], splits[1], splits[2], splits[3]);
  }

  private static Drawable drawable(FileHandle fileHandle) {
    return Utils.getDrawable(fileHandle);
  }
}
