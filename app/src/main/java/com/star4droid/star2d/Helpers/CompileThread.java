package com.star4droid.star2d.Helpers;

import androidx.core.content.FileProvider;
import com.badlogic.gdx.Gdx;
import static com.star4droid.star2d.Helpers.FileUtil.*;
import com.star4droid.star2d.Utils;
import java.io.OutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import org.eclipse.jdt.internal.compiler.batch.Main;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import com.star4droid.star2d.CodeEditor.smart.BuildError;
import com.star4droid.star2d.CodeEditor.smart.SourceTrace;
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
	/** Per-project build folder (files/builds/<project>_<hash>): classes, jars and dex of THIS project only, so two projects never share them. */
	String buildDir;
	/** Only one build at a time in the whole app (play, build-only, export, remote): they used to share bin/ and java/classes.dex. */
	private static final java.util.concurrent.locks.ReentrantLock BUILD_LOCK = new java.util.concurrent.locks.ReentrantLock();
	public static boolean isBuilding(){ return BUILD_LOCK.isLocked(); }
	//Context context;
	boolean dx=false;
	public CompileThread(String path,boolean d){
		filesPath = path;
		//context = ctx;
		dataDir = Gdx.files.getExternalStoragePath();
		// outside the project folder (never zipped, exported, uploaded or committed), one folder per project
		java.io.File javaDir = new java.io.File(path);
		java.io.File projectDir = javaDir.getParentFile() != null ? javaDir.getParentFile() : javaDir;
		buildDir = dataDir + "/builds/" + projectDir.getName().replaceAll("[^A-Za-z0-9_.-]", "_") + "_" + Integer.toHexString(projectDir.getAbsolutePath().hashCode());
		//Gdx.files.external("logs.txt").writeString(dataDir,false);
		dx = d;
	}
	
	@Override
	public void run() {
		if(!BUILD_LOCK.tryLock()){
			push(CHANGE_ERROR, com.star4droid.star2d.ui.Tr.t("build_running", "A build is already running, please wait..."));
			return;
		}
		try {
			push(CHANGE_STATUS,"Compiling...");
			deleteFile(buildDir+"/classes");
			deleteFile(buildDir+"/classes.jar");
			deleteFile(buildDir+"/dex-out");
			// left by older versions (one folder shared by every project)
			deleteFile(dataDir+"/bin/classes");
			deleteFile(dataDir+"/bin/classes.jar");
			makeDir(buildDir);
			try {
				RunEcj();
			} catch(OutOfMemoryError oom){
				lastErrors = new ArrayList<>();
				com.star4droid.star2d.logs.Logs.build(projectName(), "build", "Out of memory while building\n" + com.star4droid.star2d.logs.Logs.trace(oom));
				push(CHANGE_ERROR, "Not enough memory to build this project. Close other apps and press Play again.");
			} catch(Error | Exception exception){
				com.star4droid.star2d.logs.Logs.build(projectName(), "build", com.star4droid.star2d.logs.Logs.trace(exception));
				push(CHANGE_ERROR,Utils.getStackTraceString(exception));
			}
		} finally {
			BUILD_LOCK.unlock();
		}
	}
	
	private volatile java.util.List<BuildError> lastErrors = new ArrayList<>();
	
	/** Structured errors of the last failed build (javac and ecj give the same format). */
	public java.util.List<BuildError> getErrors(){
		return lastErrors;
	}
	
	private void publishErrors(ArrayList<BuildError> found){
		try {
			SourceTrace.resolveAll(found);
		} catch(Throwable ignored){}
		// errors inside a file generated from Najma (.njm): same line of the .njm file, message in Arabic
		try {
			com.star4droid.star2d.najma.NajmaProject.mapErrors(found);
		} catch(Throwable ignored){}
		lastErrors = found;
	}
	
	private static void collectEcj(CompilationResult result, ArrayList<BuildError> into){
		CategorizedProblem[] problems = result.getErrors();
		if(problems == null) return;
		for(CategorizedProblem p : problems){
			if(p == null || !p.isError()) continue;
			BuildError e = new BuildError();
			char[] fn = p.getOriginatingFileName();
			e.file = fn == null ? null : new String(fn);
			e.line = Math.max(1, p.getSourceLineNumber());
			e.start = p.getSourceStart();
			e.end = p.getSourceEnd() + 1;
			e.message = p.getMessage();
			e.column = 1;
			// compute the column from the file text when ecj didn't (or counted differently)
			try {
				if(e.file != null && e.start >= 0){
					String text = readFile(e.file);
					if(e.start <= text.length()){
						int ls = text.lastIndexOf('\n', Math.max(0, e.start - 1)) + 1;
						e.column = e.start - ls + 1;
					}
				}
			} catch(Throwable ignored){}
			into.add(e);
		}
	}
	
	public void setOnChangeStatus(OnStatusChanged statusChanged){
		onStatusChanged=statusChanged;
	}
	
	public void RunEcj() throws Exception {
		push(CHANGE_STATUS,"Running "+EngineSettings.get().getString("compiler","ecj")+"...");
		ArrayList<String> opt = new ArrayList<>();
		String jv=readFile(filesPath+"/java.version").trim();
		boolean dxDexer = "dx".equals(EngineSettings.get().getString("dexer", "d8"));
		// -1.7 was only the old automatic default: Java 8 accepts all Java 7 code, so move those projects to 8 (lambdas etc.).
		// The old dx cannot read Java 8 lambdas, so with dx the code is compiled as Java 7.
		if(jv.equals("") || jv.equals("-1.7")) {
			jv = "-1.8";
			writeFile(filesPath+"/java.version",jv);
		}
		if(dxDexer) jv = "-1.7";
		if(!isExistFile(dataDir.concat("/bin/cp.jar"))){
			push(CHANGE_ERROR,CP_NOT_FOUND);
			return;
		}
        //Utils.extractAssetFile(context,"java/PlayerItem.java",filesPath+"/com/star4droid/star2d/player/PlayerItem.java");
        Gdx.files.internal("java/PlayerItem.java").copyTo(Gdx.files.absolute(filesPath+"/com/star4droid/star2d/player/PlayerItem.java"));
        Gdx.files.internal("java/Timer.java").copyTo(Gdx.files.absolute(filesPath+"/com/badlogic/gdx/utils/Timer.java"));
		//FileUtil.deleteFile(filesPath+"/com/star4droid/star2d/player/PlayerItem.java");
		//Utils.extractAssetFile(context,"java/game.zip",dataDir+"/bin/addition.jar");
		Gdx.files.internal("java/game.zip").copyTo(Gdx.files.absolute(buildDir+"/addition.jar"));
		// Najma (.njm) files become .java files first; their own errors stop the build here
		try {
			java.util.List<BuildError> najmaErrors = com.star4droid.star2d.najma.NajmaProject.sync(filesPath);
			if(!najmaErrors.isEmpty()){
				lastErrors = najmaErrors;
				push(CHANGE_ERROR, com.star4droid.star2d.najma.NajmaProject.describe(najmaErrors));
				return;
			}
		} catch(Throwable najmaFailure){
			push(CHANGE_ERROR, "Najma : " + Utils.getStackTraceString(najmaFailure));
			return;
		}
		if(!EngineSettings.get().getString("compiler","ecj").equals("javac")){
			opt.add(jv);
			opt.add("-nowarn");
			opt.add("-deprecation");
			opt.add("-d");
			opt.add(buildDir.concat("/classes"));
			opt.add("-cp");
			opt.add(dataDir.concat("/bin/cp.jar")+":"+buildDir.concat("/addition.jar"));
			opt.add("-proc:none");
			opt.add("-encoding");
			opt.add("UTF-8");
			opt.add("-sourcepath");
			opt.add("ignore");
			addJavaFilesFrom(filesPath,opt);
			// PrintWriter printWriter = new PrintWriter(new OutputStream() {
				// @Override
				// public void write(int p1) throws IOException
				// {
					// //do nothing
				// }
			// });
			
			final StringBuilder errs = new StringBuilder();
			
			PrintWriter printWriter2 = new PrintWriter(new OutputStream() {
				@Override
				public void write(int p1) throws IOException
				{
					errs.append((char)p1);
				}
			});
			
			final ArrayList<BuildError> found = new ArrayList<>();
			Main main = new Main(printWriter2, printWriter2, false, null, null){
				@Override
				public ICompilerRequestor getBatchRequestor() {
					final ICompilerRequestor original = super.getBatchRequestor();
					return new ICompilerRequestor(){
						@Override
						public void acceptResult(CompilationResult result) {
							try {
								collectEcj(result, found);
							} catch(Throwable ignored){}
							original.acceptResult(result);
						}
					};
				}
			};
			
			main.compile(opt.toArray(new String[0]));
			
			if(main.globalErrorsCount > 0) {
				publishErrors(found);
				push(CHANGE_ERROR,"errors : "+errs.toString());
				return;
			}
		} else {
			JavaCompiler compiler= com.sun.tools.javac.api.JavacTool.create();
			String cp_path=dataDir.concat("/bin/cp.jar"),
			addition=buildDir.concat("/addition.jar");
			//Uri uri1 = Uri.fromFile(new java.io.File(cp_path)),
			//uri2 = Uri.fromFile(new java.io.File(addition));
			String jc_v=filesPath+"/javac.version",jc_t=filesPath+"/javac.target";
			if((!FileUtil.isExistFile(jc_v))||(!FileUtil.isExistFile(jc_t))){
				FileUtil.writeFile(jc_v,"7");
				FileUtil.writeFile(jc_t,"7");
			}
			jc_t = FileUtil.readFile(jc_t);
			jc_v = FileUtil.readFile(jc_v);
			FileUtil.deleteFile(buildDir+"/classes/");
			FileUtil.makeDir(buildDir+"/classes/");
			Iterable<String> options=Arrays.asList(new String[]{"-nowarn","-source",jc_v,"-target",jc_t,"-d",buildDir+"/classes/","-classpath","\""+cp_path+"\""+":"+"\""+addition+"\""});
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
				ArrayList<BuildError> found = new ArrayList<>();
				for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics())
					try {
						if(diagnostic.getKind() != Diagnostic.Kind.ERROR) continue;
						BuildError e = new BuildError();
						JavaFileObject src = diagnostic.getSource();
						e.file = src == null ? null : new java.io.File(src.toUri()).getAbsolutePath();
						e.line = (int) Math.max(1, diagnostic.getLineNumber());
						e.column = (int) Math.max(1, diagnostic.getColumnNumber());
						e.start = (int) diagnostic.getStartPosition();
						e.end = (int) diagnostic.getEndPosition();
						if(e.start < 0) e.start = (int) diagnostic.getPosition();
						e.message = diagnostic.getMessage(null);
						found.add(e);
					} catch(Throwable ignored){}
				for (Diagnostic diagnostic : diagnostics.getDiagnostics())
						errors.append(String.format("Error on line %1$s in %2$s, message :\n %3$s\n",
							diagnostic.getLineNumber(),(diagnostic.getSource()==null?"UnknownSource":((JavaFileObject)diagnostic.getSource()).toUri()),diagnostic.getMessage(null)));
				fileManager.close();
				publishErrors(found);
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
			
			buildDir.concat("/classes/"),
			
			buildDir.concat("/classes.jar")
			
			).create();
			} catch (Exception e) {
			push(CHANGE_ERROR,"Packaging JAR failed: " + Utils.getStackTraceString(e));
			return;
		}
		compile();
	}
	
	/** Name of the project (for its logs/build/<project>/ folder). */
	String projectName(){
		java.io.File javaDir = new java.io.File(filesPath);
		java.io.File p = javaDir.getParentFile();
		return p == null ? javaDir.getName() : p.getName();
	}

	/**
	 * classes.jar -> classes.dex with the dexer chosen in Settings (D8, the default, or the old dx).
	 * The dex is made in build/dex-out/ and moved to java/classes.dex only when it is complete, so a failed or
	 * interrupted dexing never leaves an empty or half written classes.dex behind.
	 */
	public void compile(){
		String dexer = EngineSettings.get().getString("dexer", "d8");
		java.io.File out = new java.io.File(buildDir, "dex-out");
		deleteFile(out.getAbsolutePath());
		out.mkdirs();
		java.io.File jar = new java.io.File(buildDir, "classes.jar");
		java.io.File cp = new java.io.File(dataDir, "bin/cp.jar");
		java.io.File addition = new java.io.File(buildDir, "addition.jar");
		try {
			if("dx".equals(dexer)) runDx(jar, out);
			else runD8(jar, cp, addition, out);
		} catch(OutOfMemoryError oom){
			com.star4droid.star2d.logs.Logs.build(projectName(), "build", "Out of memory while dexing\n" + com.star4droid.star2d.logs.Logs.trace(oom));
			push(CHANGE_ERROR, "Not enough memory to build this project. Close other apps and press Play again.");
			return;
		} catch(Throwable e){
			String msg = e.getMessage() == null ? e.toString() : e.getMessage();
			com.star4droid.star2d.logs.Logs.build(projectName(), "build", ("dx".equals(dexer) ? "dx" : "D8") + " failed\n" + com.star4droid.star2d.logs.Logs.trace(e));
			push(CHANGE_ERROR, ("dx".equals(dexer) ? "dx" : "D8") + " failed: " + msg);
			return;
		}
		java.io.File dex = new java.io.File(out, "classes.dex");
		java.io.File[] more = out.listFiles((d, n) -> n.matches("classes\\d+\\.dex"));
		if(more != null && more.length > 0){
			push(CHANGE_ERROR, "The game code is too big for one dex file (more than 65536 methods). Remove unused code or libraries.");
			return;
		}
		if(!dex.isFile() || dex.length() == 0){
			push(CHANGE_ERROR, ("dx".equals(dexer) ? "dx" : "D8") + " finished without writing classes.dex. See logs/build/" + projectName() + "/build.txt");
			com.star4droid.star2d.logs.Logs.build(projectName(), "build", "dexer produced no classes.dex, output folder : " + java.util.Arrays.toString(out.list()));
			return;
		}
		java.io.File target = new java.io.File(filesPath, "classes.dex");
		target.delete();
		if(!dex.renameTo(target)){
			try { FileUtil.copyFile(dex.getAbsolutePath(), target.getAbsolutePath()); }
			catch(Throwable t){ push(CHANGE_ERROR, "Could not move the dex: " + t); return; }
		}
		deleteFile(out.getAbsolutePath());
		push(CHANGE_DONE,"Compiled...√");
	}

	/** D8 through its API: errors come back as messages (D8.main printed them and could end the process). */
	private void runD8(java.io.File jar, java.io.File cp, java.io.File addition, java.io.File out) throws Exception {
		push(CHANGE_STATUS,"Dexing with D8...");
		final StringBuilder problems = new StringBuilder();
		com.android.tools.r8.DiagnosticsHandler handler = new com.android.tools.r8.DiagnosticsHandler(){
			@Override public void error(com.android.tools.r8.Diagnostic d){ problems.append("Error: ").append(d.getDiagnosticMessage()).append('\n'); }
			@Override public void warning(com.android.tools.r8.Diagnostic d){ /* missing optional classes etc.: not fatal */ }
			@Override public void info(com.android.tools.r8.Diagnostic d){}
		};
		com.android.tools.r8.D8Command.Builder b = com.android.tools.r8.D8Command.builder(handler)
				.addProgramFiles(jar.toPath())
				.addLibraryFiles(cp.toPath())
				.setMinApiLevel(24)
				.setMode(com.android.tools.r8.CompilationMode.DEBUG)
				.setOutput(out.toPath(), com.android.tools.r8.OutputMode.DexIndexed);
		if(addition.isFile()) b.addClasspathFiles(addition.toPath());
		try {
			com.android.tools.r8.D8.run(b.build());
		} catch(com.android.tools.r8.CompilationFailedException e){
			throw new Exception(problems.length() > 0 ? problems.toString().trim() : String.valueOf(e.getMessage()), e);
		}
	}

	/** The old dx (1.16, its last version) through its API: dx's main() ends the process with System.exit. Needs Java 7 code. */
	private void runDx(java.io.File jar, java.io.File out) throws Exception {
		push(CHANGE_STATUS,"Dexing with dx...");
		java.io.ByteArrayOutputStream log = new java.io.ByteArrayOutputStream();
		com.android.dx.command.dexer.DxContext ctx = new com.android.dx.command.dexer.DxContext(log, log);
		com.android.dx.command.dexer.Main.Arguments a = new com.android.dx.command.dexer.Main.Arguments(ctx);
		a.parseFlags(new String[]{ "--min-sdk-version=24", "--output=" + new java.io.File(out, "classes.dex").getAbsolutePath(), jar.getAbsolutePath() });
		int r = new com.android.dx.command.dexer.Main(ctx).runDx(a);
		if(r != 0){
			String text = log.toString("UTF-8").trim();
			if(text.contains("invoke-customs") || text.contains("lambda") || text.contains("default interface methods"))
				text += "\n\ndx only understands Java 7 code (no lambdas): use D8 in Settings, or set the Java version to 7.";
			throw new Exception(text.isEmpty() ? "dx exited with code " + r : text);
		}
	}
	
	public static void addJavaFilesFrom(String path,ArrayList<String> to){
		ArrayList<String> tempList = new ArrayList<>();
		listDir(path,tempList);
		for(String str:tempList){
			if(isDirectory(str)) addJavaFilesFrom(str,to);
			else if(str.endsWith(".java")) to.add(str);
		}
	}
	
	/** true: callbacks run at once on the building thread (export), instead of being posted to the LibGDX thread. */
	boolean direct;

	/** Result of {@link #buildNow}. */
	public static final class Result {
		public boolean ok;
		public String message = "";
		public java.util.List<BuildError> errors = new ArrayList<>();
	}

	/**
	 * Generates the code of every saved scene, compiles it and puts the dex in dex/scenes.dex, on the calling thread
	 * (used by Export APK so a project is never exported without its dex or with stale code).
	 */
	public static Result buildNow(String projectPath){
		final Result r = new Result();
		try {
			com.star4droid.star2d.Helpers.editor.Project project = new com.star4droid.star2d.Helpers.editor.Project(projectPath);
			for(CodeGenerator.SceneCode sc : CodeGenerator.buildAll(project, null, null)){
				com.badlogic.gdx.files.FileHandle f = Gdx.files.absolute(sc.path);
				if(!f.exists() || !f.readString().equals(sc.code)) f.writeString(sc.code, false);
			}
			CompileThread t = new CompileThread(project.get("java"), false);
			t.direct = true;
			t.setOnChangeStatus(new OnStatusChanged(){
				@Override public void onStatus(String s){}
				@Override public void onEnd(String message){}
				@Override public void onErrors(java.util.List<BuildError> errors){ r.errors = new ArrayList<>(errors); }
				@Override public void onError(String error){ r.ok = false; r.message = error; }
				// direct mode: this runs inside the build lock, so no other build can touch java/classes.dex meanwhile
				@Override public void onSuccess(String message){
					try { installDex(projectPath); r.ok = true; }
					catch(Throwable e){ r.ok = false; r.message = String.valueOf(e); }
				}
			});
			t.run();
		} catch(Throwable e){
			r.ok = false;
			r.message = String.valueOf(e);
			com.star4droid.star2d.logs.Logs.build(new java.io.File(projectPath).getName(), "build", "Export build failed\n" + com.star4droid.star2d.logs.Logs.trace(e));
		}
		return r;
	}

	/** java/classes.dex (fresh build) -> dex/scenes.dex, replaced in one step (temp file + rename). */
	public static void installDex(String projectPath) throws java.io.IOException {
		java.io.File built = new java.io.File(projectPath, "java/classes.dex");
		if(!built.isFile() || built.length() == 0) throw new java.io.IOException("the build wrote no classes.dex");
		java.io.File target = new java.io.File(new com.star4droid.star2d.Helpers.editor.Project(projectPath).getDex());
		target.getParentFile().mkdirs();
		java.io.File tmp = new java.io.File(target.getParentFile(), "scenes.dex.tmp");
		tmp.delete();
		if(!built.renameTo(tmp)){ FileUtil.copyFile(built.getAbsolutePath(), tmp.getAbsolutePath()); built.delete(); }
		if(target.exists()){ target.setWritable(true); target.delete(); }
		if(!tmp.renameTo(target)) throw new java.io.IOException("could not rename " + tmp + " to " + target);
	}

	public void push(final String type,String m){
		try {
		byte[] utf8Bytes = m.getBytes("UTF-8");
		m = new String(utf8Bytes, "UTF-8");
		} catch(Throwable ex){}
		final String message=m;
		if(onStatusChanged!=null && direct){
			switch(type){
				case CHANGE_ERROR: onStatusChanged.onErrors(lastErrors); onStatusChanged.onError(message); onStatusChanged.onEnd(message); break;
				case CHANGE_STATUS: onStatusChanged.onStatus(message); break;
				case CHANGE_END: onStatusChanged.onEnd(message); break;
				case CHANGE_DONE: onStatusChanged.onSuccess(message); onStatusChanged.onEnd(message); break;
			}
			return;
		}
		if(onStatusChanged!=null && Gdx.app != null){
			Gdx.app.postRunnable(new Runnable(){
				@Override
				public void run() {
					switch(type){
						case CHANGE_ERROR:
						onStatusChanged.onErrors(lastErrors);
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
		/** Structured errors, called right before onError (may be empty for non-compiler failures). */
		default void onErrors(java.util.List<BuildError> errors){}
	}
	
	public static void exit(int status){
		throw new RuntimeException("exit with status code : "+status);
	}
}