package com.star4droid.star2d.CodeEditor;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.Utils;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.completion.java.JavaCompletionProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyIndexer {

    private int isIndexing = 0;
    private String[] indexes;
    private Editor editor;
    private JavaCompletionProvider javaCompletionProvider;
    private Project project;
    public String editorProjectPath;
    public static MyIndexer lastIndexer;
    private final ExecutorService paddingService = Executors.newSingleThreadExecutor();

    public MyIndexer() {
        editor = Editor.getCurrentEditor();
        // Use the path from the Editor's project
        editorProjectPath = editor.getProject().getPath();

        javaCompletionProvider = new JavaCompletionProvider();

        if (lastIndexer != null) {
            lastIndexer.shutdown();
        }
        lastIndexer = this;
    }

    public JavaCompletionProvider getCompletionProvider() {
        return javaCompletionProvider;
    }

    public Project getProject() {
        return project;
    }

    public boolean isIndexing() {
        return isIndexing > 0;
    }

    public static boolean isIndexerMatch(String projectPath) {
        try {
            return lastIndexer != null && Uri.parse(projectPath).getLastPathSegment().toLowerCase().equals(Uri.parse(lastIndexer.editorProjectPath).getLastPathSegment());
        } catch (Exception e) {
            return false;
        }
    }

    public MyIndexer indexFiles(Editor editor) {
        return indexFiles(editor, editor.getContext());
    }

    public String[] getIndexsFiles() {
        return indexes;
    }

    public MyIndexer indexFiles(Editor editor, Context context) {
        if (isIndexing > 0) {
            return this;
        }
        isIndexing++;

        paddingService.execute(() -> {
            String data = FileUtil.getPackageDataDir(editor.getContext());
            try {
                // Ensure Project is initialized
                if (project == null) {
                    project = new Project(new File(editorProjectPath));
                }

                JavaModule mainModule = (JavaModule) project.getMainModule();

                String idx2 = data + "/bin/index2.json";
                String idx3 = data + "/bin/index3.json";

                // 1. Handle addition.jar (Game Libraries)
                File additionJar = new File(data + "/bin/addition.jar");
                if ((!FileUtil.isExistFile(idx2)) || new java.io.File(idx2).length() == 0 || !EngineSettings.get().getString("JAR_FILE_VERSION", "").equals("2.0") || !additionJar.exists()) {
                    FileUtil.writeFile(idx2, "");
                    // Extract assets
                    Utils.extractAssetFile(editor.getContext(), "java/game.zip", additionJar.getAbsolutePath());
                    EngineSettings.set("JAR_FILE_VERSION", "2.0");
                }

                if (additionJar.exists()) {
                    mainModule.addLibrary(additionJar);
                }

                // 2. Handle cp.jar (Classpath / Android.jar)
                File cpJar = new File(data + "/bin/cp.jar");
                if (!cpJar.exists()) {
                    try {
                        Utils.extractAssetFile(editor.getContext(), "bin/cp.jar", cpJar.getAbsolutePath());
                    } catch (Exception e) {
                        try {
                            Utils.extractAssetFile(editor.getContext(), "cp.jar", cpJar.getAbsolutePath());
                        } catch (Exception ex) {
                        }
                    }
                }

                if (cpJar.exists()) {
                    mainModule.addLibrary(cpJar);
                }

                // 3. User Source Files
                String javaSourcePath = editor.getProject().get("java");
                File srcDir = new File(javaSourcePath);
                if (srcDir.exists()) {
                    scanAndAddFiles(srcDir, mainModule);
                }

                indexes = new String[]{idx2, idx3};

            } catch (Exception exception) {
                FileUtil.writeFile(data + "/error.txt", "Failed To Index Files : \n" + Log.getStackTraceString(exception));
                Log.e("MyIndexer", "Indexing error", exception);
            } finally {
                isIndexing--;
            }
        });

        return this;
    }

    private void scanAndAddFiles(File dir, JavaModule module) {
        if (!dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                scanAndAddFiles(f, module);
            } else {
                if (f.getName().endsWith(".java")) {
                    module.addJavaFile(f);
                } else if (f.getName().endsWith(".jar")) {
                    module.addLibrary(f);
                }
            }
        }
    }

    public void run(List<String> a, String b, List<String> c, List<String> d, String e) {
    }

    public void shutdown() {
        lastIndexer = null;
        if (paddingService != null) {
            paddingService.shutdown();
        }
        // project = null;
    }
}
