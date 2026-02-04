package com.star4droid.star2d.CodeEditor;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
// import com.tyron.javacompletion.project.ModuleManager;
// import com.tyron.javacompletion.project.Project;
// import com.tyron.javacompletion.storage.IndexStore;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

public class IndexUtil {

    /*
    // ... all broken methods ...
     */
    public static Object getModule(Object project) {
        return null;
    }

    public static void loadFile(Object project, String path) {
    }

    public static void loadStream(Object project, InputStream stream) {
    }

    public static void loadJdk(Object project, Context context, String... other) throws Exception {
    }

    public static InputStreamReader getInputStream(String file) throws Exception {
        return new InputStreamReader(new FileInputStream(file));
    }
}
