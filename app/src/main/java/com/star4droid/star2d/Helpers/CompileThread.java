package com.star4droid.star2d.Helpers;

import androidx.core.content.FileProvider;
import com.android.tools.r8.D8;
import com.badlogic.gdx.Gdx;
import static com.star4droid.star2d.Helpers.FileUtil.*;
import com.star4droid.star2d.Utils;
import java.io.OutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import org.eclipse.jdt.internal.compiler.batch.Main;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.StandardJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.JavaCompiler;

public class CompileThread extends Thread {
	public static final String CHANGE_ERROR="error",
	CHANGE_DONE="done",CHANGE_STATUS="state",CHANGE_END="end",
	CP_NOT_FOUND="CP_NOT_FOUND";
	OnStatusChanged onStatusChanged;
	String filesPath="",dataDir;
	//Context context;
	boolean dx=false;
	public CompileThread(String path,boolean d){
		filesPath = path;
		//context = ctx;
		dataDir = Gdx.files.getExternalStoragePath();
		//Gdx.files.external("logs.txt").writeString(dataDir,false);
		dx = d;
	}
	
	@Override
	public void run() {
		push(CHANGE_STATUS,"Compiling...");
		long startTime=System.currentTimeMillis();
		deleteFile(dataDir+"/bin/classes");
		deleteFile(dataDir+"/bin/classes.jar");
		//writeFile(filesPath+"/StarBody.java",Utils.readAssetFile("java/StarBody.java",context));
		//writeFile(filesPath+"/UI.java",Utils.readAssetFile("java/UI.java",context));
		try {
		RunEcj();
		} catch(Exception exception){
			push(CHANGE_ERROR,Utils.getStackTraceString(exception));
		}
	}
	
	public void setOnChangeStatus(OnStatusChanged statusChanged){
		onStatusChanged=statusChanged;
	}
	
	public void RunEcj() throws Exception {
		push(CHANGE_STATUS,"Running "+EngineSettings.get().getString("compiler","javac")+"...");
		ArrayList<String> opt = new ArrayList<>();
		String jv=readFile(filesPath+"/java.version");
		if(jv.equals("")) {
			jv = "-1.7";
			writeFile(filesPath+"/java.version",jv);
		}
		if(!isExistFile(dataDir.concat("/bin/cp.jar"))){
			push(CHANGE_ERROR,CP_NOT_FOUND);
			return;
		}
        //Utils.extractAssetFile(context,"java/PlayerItem.java",filesPath+"/com/star4droid/star2d/player/PlayerItem.java");
        Gdx.files.internal("java/PlayerItem.java").copyTo(Gdx.files.absolute(filesPath+"/com/star4droid/star2d/player/PlayerItem.java"));
		//FileUtil.deleteFile(filesPath+"/com/star4droid/star2d/player/PlayerItem.java");
		//Utils.extractAssetFile(context,"java/game.zip",dataDir+"/bin/addition.jar");
		Gdx.files.internal("java/game.zip").copyTo(Gdx.files.absolute(dataDir+"/bin/addition.jar"));
		if(EngineSettings.get().getString("compiler","javac").equals("ecj")){
			opt.add(jv);
			opt.add("-nowarn");
			opt.add("-deprecation");
			opt.add("-d");
			opt.add(dataDir.concat("/bin/classes"));
			opt.add("-cp");
			opt.add(dataDir.concat("/bin/cp.jar")+":"+dataDir.concat("/bin/addition.jar"));
			opt.add("-proc:none");
			opt.add("-sourcepath");
			opt.add("ignore");
			addJavaFilesFrom(filesPath,opt);
			PrintWriter printWriter = new PrintWriter(new OutputStream() {
				@Override
				public void write(int p1) throws IOException
				{
					//do nothing
				}
			});
			
			final StringBuilder errs = new StringBuilder();
			
			PrintWriter printWriter2 = new PrintWriter(new OutputStream() {
				@Override
				public void write(int p1) throws IOException
				{
					errs.append((char)p1);
				}
			});
			
			Main main = new Main(printWriter2, printWriter2, false, null, null);
			
			main.compile(opt.toArray(new String[0]));
			
			if(main.globalErrorsCount > 0) {
				push(CHANGE_ERROR,"errors : "+errs.toString());
				return;
			}
		} else {
			JavaCompiler compiler= com.sun.tools.javac.api.JavacTool.create();
			String cp_path=dataDir.concat("/bin/cp.jar"),
			addition=dataDir.concat("/bin/addition.jar");
			//Uri uri1 = Uri.fromFile(new java.io.File(cp_path)),
			//uri2 = Uri.fromFile(new java.io.File(addition));
			String jc_v=filesPath+"/javac.version",jc_t=filesPath+"/javac.target";
			if((!FileUtil.isExistFile(jc_v))||(!FileUtil.isExistFile(jc_t))){
				FileUtil.writeFile(jc_v,"7");
				FileUtil.writeFile(jc_t,"7");
			}
			jc_t = FileUtil.readFile(jc_t);
			jc_v = FileUtil.readFile(jc_v);
			FileUtil.deleteFile(dataDir+"/bin/classes/");
			FileUtil.makeDir(dataDir+"/bin/classes/");
			Iterable<String> options=Arrays.asList(new String[]{"-nowarn","-source",jc_v,"-target",jc_t,"-d",dataDir+"/bin/classes/","-classpath","\""+cp_path+"\""+":"+"\""+addition+"\""});
			addJavaFilesFrom(filesPath,opt);
			ArrayList<JavaFileObject> javaFiles=new ArrayList<>();
			
			for(String s:opt)
				javaFiles.add(FileObj.create(s));
			StringBuilder sbt=new StringBuilder();
			PrintWriter printWriter = new PrintWriter(new OutputStream() {
				@Override
				public void write(int p1) throws IOException
				{
					//Log.e("com.star4droid.star2d.evo",String.valueOf((char)p1));
					sbt.append((char)p1);
				}
			});
			//String cp_in=context.getFilesDir()+"/jars/cp.jar",add_in=context.getFilesDir()+"/jars/addition.jar";
			
			DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
			StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, java.util.Locale.getDefault(), java.nio.charset.Charset.defaultCharset());
			fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, Arrays.asList(
			new java.io.File(cp_path),new java.io.File(addition)
			));
			fileManager.setLocation(StandardLocation.CLASS_PATH, Arrays.asList(new java.io.File(cp_path),new java.io.File(addition)));
			
			JavaCompiler.CompilationTask task=compiler.getTask(printWriter,fileManager,diagnostics,options,null,javaFiles);
			
			if(!task.call()){
				StringBuilder errors=new StringBuilder();
				for (Diagnostic diagnostic : diagnostics.getDiagnostics())
						errors.append(String.format("Error on line %1$s in %2$s, message :\n %3$s\n",
							diagnostic.getLineNumber(),(diagnostic.getSource()==null?"UnknownSource":((JavaFileObject)diagnostic.getSource()).toUri()),diagnostic.getMessage(null)));
				fileManager.close();
				push(CHANGE_ERROR,"Error : \n"+errors.toString()+"\n"+sbt.toString());
				return;
			}
		}
		/*
		packaging jar
		*/
		push(CHANGE_STATUS,"Packaging JAR...");
		try {
			new JarPackager(
			
			dataDir.concat("/bin/classes/"),
			
			dataDir.concat("/bin/classes.jar")
			
			).create();
			} catch (Exception e) {
			push(CHANGE_ERROR,"Packaging JAR failed: " + Utils.getStackTraceString(e));
			return;
		}
		compile();
	}
	
	public void compile(){
		try {
			writeFile(filesPath.concat("/classes.dex"), "");
			if (true/*EngineSettings.get().getBoolean("d8",false)*/) {
				push(CHANGE_STATUS,"Dexing with D8...");
				ArrayList<String> opt= new ArrayList<>();
				opt.add("--output");
				opt.add(filesPath);
				opt.add("--lib");
				opt.add(dataDir.concat("/bin/cp.jar"));
				opt.add(dataDir.concat("/bin/classes.jar"));
				D8.main(opt.toArray(new String[0]));
			}
			else {
				writeFile(filesPath.concat("/classes.dex"), "");
				ArrayList<String> opt=new ArrayList<>();
				opt.add("--debug");
				opt.add("--verbose");
				opt.add("--output="+filesPath+"/classes.dex");
				opt.add(dataDir+"/bin/classes.jar");
				//com.android.dx.command.dexer.Main.main(opt.toArray(new String[0]));
			}
			} catch (Exception e) {
			push(CHANGE_ERROR, "Dex failed: " + e.toString());
			return;
		}
		
		push(CHANGE_DONE,"Compiled...√");
	}
	
	public static void addJavaFilesFrom(String path,ArrayList<String> to){
		ArrayList<String> tempList = new ArrayList<>();
		listDir(path,tempList);
		for(String str:tempList){
			if(isDirectory(str)) addJavaFilesFrom(str,to);
			else if(str.endsWith(".java")) to.add(str);
		}
	}
	
	public void push(final String type,String m){
		try {
		byte[] utf8Bytes = m.getBytes("UTF-8");
		m = new String(utf8Bytes, "UTF-8");
		} catch(Throwable ex){}
		final String message=m;
		if(onStatusChanged!=null){
			Gdx.app.postRunnable(new Runnable(){
				@Override
				public void run() {
					switch(type){
						case CHANGE_ERROR:
						onStatusChanged.onError(message);
						onStatusChanged.onEnd(message);
						break;
						case CHANGE_STATUS:
						onStatusChanged.onStatus(message);
						break;
						case CHANGE_END:
						onStatusChanged.onEnd(message);
						break;
						case CHANGE_DONE:
						onStatusChanged.onSuccess(message);
						onStatusChanged.onEnd(message);
						break;
					}
				}
			});
		}
	}
	
	public interface OnStatusChanged {
		public void onStatus(String s);
		public void onEnd(String message);
		public void onError(String error);
		public void onSuccess(String message);
	}
	
	public static void exit(int status){
		throw new RuntimeException("exit with status code : "+status);
	}
}