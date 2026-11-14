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
  private static Skin orangeSkin;
  public static void loadTheme() {
    if (VisUI.isLoaded()) return;
    orangeSkin = new Skin(skinFile("files/skins/uiskin.json"));
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
  
  /**
   * Internal skin file. If the activity's AssetManager cannot see it (seen after an in-place app update, or a stale
   * AssetManager: FileNotFoundException files/skins/uiskin.json), re-read the skins from the application's own
   * assets into the cache folder and load them from there.
   */
  private static FileHandle skinFile(String path) {
    FileHandle in = Gdx.files.internal(path);
    try {
      in.read().close();
      return in;
    } catch (Throwable ignored) {}
    android.content.Context ctx = null;
    java.io.File root = null, out = null;
    try {
      ctx = ((android.content.Context) Gdx.app).getApplicationContext();
      root = new java.io.File(ctx.getCacheDir(), "skins_fallback_" + versionOf(ctx));
      out = new java.io.File(root, path);
      if (!out.exists()) {
        // 1) the application's own AssetManager
        try { copyAssets(ctx.getAssets(), "files/skins", new java.io.File(root, "files/skins")); } catch (Throwable ignored) {}
        // 2) bypass AssetManager completely: read the entries straight from the installed APK
        if (!out.exists()) unzipFromApk(ctx.getPackageCodePath(), "assets/files/skins/", new java.io.File(root, "files/skins"));
      }
      if (out.exists()) return Gdx.files.absolute(out.getAbsolutePath());
    } catch (Throwable ignored) {}
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot read " + path + " | " + diagnose(ctx));
  }

  private static long versionOf(android.content.Context c) {
    try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode(); } catch (Throwable t) { return 0; }
  }

  private static void unzipFromApk(String apk, String prefix, java.io.File to) throws java.io.IOException {
    try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(apk)) {
      java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries();
      while (en.hasMoreElements()) {
        java.util.zip.ZipEntry e = en.nextElement();
        if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
        java.io.File f = new java.io.File(to, e.getName().substring(prefix.length()));
        f.getParentFile().mkdirs();
        try (java.io.InputStream is = z.getInputStream(e); java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
          byte[] b = new byte[16384]; int r;
          while ((r = is.read(b)) > 0) os.write(b, 0, r);
        }
      }
    }
  }

  /** One line for the crash report: what is wrong with the installed package. */
  private static String diagnose(android.content.Context c) {
    StringBuilder sb = new StringBuilder();
    try {
      java.io.File apk = new java.io.File(c.getPackageCodePath());
      sb.append("apk=").append(apk).append(" exists=").append(apk.exists()).append(" size=").append(apk.length());
      sb.append(" ver=").append(versionOf(c));
      String[] l = c.getAssets().list("files");
      sb.append(" assets/files=").append(l == null ? -1 : l.length);
      try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(apk)) {
        sb.append(" zipEntry=").append(z.getEntry("assets/files/skins/uiskin.json") != null);
        sb.append(" zipEntries=").append(z.size());
      }
      sb.append(" splits=").append(java.util.Arrays.toString(c.getApplicationInfo().splitSourceDirs));
    } catch (Throwable t) { sb.append(" diag-failed=").append(t); }
    return sb.toString();
  }

  private static void copyAssets(android.content.res.AssetManager am, String dir, java.io.File to) throws java.io.IOException {
    String[] list = am.list(dir);
    if (list == null || list.length == 0) return;
    to.mkdirs();
    for (String n : list) {
      String sub = dir + "/" + n;
      String[] kids = am.list(sub);
      if (kids != null && kids.length > 0) { copyAssets(am, sub, new java.io.File(to, n)); continue; }
      try (java.io.InputStream is = am.open(sub); java.io.FileOutputStream os = new java.io.FileOutputStream(new java.io.File(to, n))) {
        byte[] b = new byte[16384]; int r;
        while ((r = is.read(b)) > 0) os.write(b, 0, r);
      }
    }
  }

  public static Skin getOrangeSkin(){
    return orangeSkin;
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
        skinFile("files/skins/visui/uiskin.json"); // VisUI.SkinScale.X2.getSkinFile();
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
        com.star4droid.star2d.logs.Logs.editor("drawables", "not contains \".\" name : " + fh.name() + "\n" + "__".repeat(10) + "\n");
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
    com.star4droid.star2d.logs.Logs.editor(tag, log);
  }

  private static NinePatch ninePatch(FileHandle fileHandle, int[] splits) {
    return new NinePatch(new Texture(fileHandle), splits[0], splits[1], splits[2], splits[3]);
  }

  private static Drawable drawable(FileHandle fileHandle) {
    return Utils.getDrawable(fileHandle);
  }
}
