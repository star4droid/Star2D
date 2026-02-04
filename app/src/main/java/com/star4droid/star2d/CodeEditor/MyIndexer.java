package com.star4droid.star2d.CodeEditor;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.common.collect.ImmutableMap;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.Utils;
import com.star4droid.star2d.Helpers.EngineSettings;
// import com.tyron.javacompletion.JavaCompletions;
// import com.tyron.javacompletion.options.JavaCompletionOptionsImpl;
// import com.tyron.javacompletion.tool.Indexer;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

/*
import com.tyron.javacompletion.file.FileManager;
import com.tyron.javacompletion.file.PathUtils;
import com.tyron.javacompletion.file.SimpleFileManager;
import com.tyron.javacompletion.model.FileScope;
import com.tyron.javacompletion.model.Module;
import com.tyron.javacompletion.options.IndexOptions;
import com.tyron.javacompletion.parser.AstScanner;
import com.tyron.javacompletion.parser.ParserContext;
import com.tyron.javacompletion.parser.classfile.ClassModuleBuilder;
import com.tyron.javacompletion.project.Project;
import com.tyron.javacompletion.project.SimpleModuleManager;
import com.tyron.javacompletion.storage.IndexStore;
 */
public class MyIndexer {

    /* extends Indexer */
    private int isIndexing = 0;
    private String[] indexes;
    private Editor editor;
    // private JavaCompletions javaCompletions;
    public String editorProjectPath;
    public static MyIndexer lastIndexer;

    public MyIndexer() {
        editor = Editor.getCurrentEditor();
        /*
		javaCompletions = new JavaCompletions(){
			@Override
			public synchronized void updateFileContent(Path path,String str){
				try {
					super.updateFileContent(path,str);
				} catch(IllegalStateException ex){}
			}
		};
         */
        editorProjectPath = editor.getProject().getPath();
        if (lastIndexer != null) {
            lastIndexer.shutdown();
        }
        lastIndexer = this;
    }

    public Object getJavaCompletions() {
        return null;
    }

    public boolean isIndexing() {
        return isIndexing > 0;
    }

    //check if the user open the same last project
    public static boolean isIndexerMatch(String projectPath) {
        try {
            return lastIndexer != null && Uri.parse(projectPath).getLastPathSegment().toLowerCase().equals(Uri.parse(lastIndexer.editorProjectPath).getLastPathSegment());
        } catch (Exception e) {
            return false;
        }
    }

    // private final ParserContext parserContext = new ParserContext();
    public MyIndexer indexFiles(Editor editor) {
        return this;
    }

    public String[] getIndexsFiles() {
        return indexes;
    }

    public MyIndexer indexFiles(Editor editor, Context context) {
        return this;
    }

    public void getClasses(ArrayList<String> arrayList, String path) {
        ArrayList<String> list = new ArrayList<>();
        FileUtil.listDir(path, list);
        for (String file : list) {
            if (FileUtil.isDirectory(file)) {
                getClasses(arrayList, file); 
            }else if (file.endsWith(".class") || file.endsWith(".java")) {
                arrayList.add(file);
            }
        }
    }
    // ArrayList<SimpleModuleManager> modelsArray = new ArrayList<>();
    // ArrayList<FileManager> fileManagerArrayList = new ArrayList<>();

    public void run(List<String> a, String b, List<String> c, List<String> d, String e) {
    }

    /*
	public void run(
	List<String> inputPaths,
	String outputPath,
	List<String> ignorePaths,
	List<String> dependIndexFiles,
	String root) {
		Path rootPath = new java.io.File(root).toPath();
		// Do not initialize the project. We handle the files on our own.
		SimpleModuleManager moduleManager = new SimpleModuleManager();
		modelsArray.add(moduleManager);
		Project project = new Project(moduleManager, moduleManager.getFileManager());
		for (String inputPath : inputPaths) {
			Path path = Paths.get(inputPath);
			// Do not use module manager's file manager because we need to setup root
			// path and ignore paths per directory.
			FileManager fileManager = new SimpleFileManager(path, ignorePaths);
			fileManagerArrayList.add(fileManager);
			ClassModuleBuilder classModuleBuilder = new ClassModuleBuilder(moduleManager.getModule());
			ImmutableMap<String, Consumer<Path>> handlers =
			ImmutableMap.<String, Consumer<Path>>of(
			".class",
			classModuleBuilder::processClassFile,
			".java",
			subpath ->{addJavaFile(subpath, moduleManager.getModule(), fileManager);});
			if (Files.isDirectory(path)) {
				System.out.println("Indexing directory: " + inputPath);
				PathUtils.walkDirectory(
				path,
				handlers,
				// ignorePredicate=
 fileManager::shouldIgnorePath);
				} else if (inputPath.endsWith(".jar") || inputPath.endsWith(".srcjar")) {
				System.out.println("Indexing JAR file: " + inputPath);
				try {
					PathUtils.walkDirectory(rootPath
					//PathUtils.getRootPathForJarFile(path)
,
					handlers,
					// ignorePredicate=
 subpath -> false);
					} catch (Exception t) {
					throw new RuntimeException(t);
				}
			}
		}
		for (String dependIndexFile : dependIndexFiles) {
			project.loadTypeIndexFile(dependIndexFile);
		}
		System.out.println("Writing index file to " + outputPath);
		new IndexStore().writeModuleToFile(moduleManager.getModule(), Paths.get(outputPath));
	}
	
	private void addJavaFile(Path path, Module module, FileManager fileManager) {
		Optional<CharSequence> content = fileManager.getFileContent(path);
		if (content.isPresent()) {
			FileScope fileScope =
			new AstScanner(IndexOptions.NON_PRIVATE_BUILDER.build())
			.startScan(
			parserContext.parse(path.toString(), content.get()),
			path.toString(),
			content.get());
			module.addOrReplaceFileScope(fileScope);
		}
	}
     */

    public void shutdown() {
        /*
	    try {
    		javaCompletions.shutdown();
    		for(SimpleModuleManager manager:modelsArray)
    			manager.getFileManager().shutdown();
    		for(FileManager fileManager:fileManagerArrayList)
    			fileManager.shutdown();
		} catch(Exception ex){}
         */
        lastIndexer = null;
    }
}
