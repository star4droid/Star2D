package com.star4droid.template;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ProjectAssetLoader;
import com.star4droid.template.Utils.SpriteSheetLoader;
import com.star4droid.template.Utils.PropertySet;
import com.star4droid.template.Utils.Utils;
import java.io.File;
import dalvik.system.DexClassLoader;

import com.star4droid.star2d.Helpers.Project;

public class AndroidSceneLoader implements SceneLoader {
    private final File filesDir;
    private final File codeCacheDir;
    private final Project project;
    private final ProjectAssetLoader projectAssetLoader;
    private final SpriteSheetLoader spriteSheetLoader;
    private final PropertySet set;

    public AndroidSceneLoader(File filesDir, File codeCacheDir, Project project, ProjectAssetLoader assetLoader, SpriteSheetLoader spriteLoader, PropertySet set) {
        this.filesDir = filesDir;
        this.codeCacheDir = codeCacheDir;
        this.project = project;
        this.projectAssetLoader = assetLoader;
        this.spriteSheetLoader = spriteLoader;
        this.set = set;
    }

    @Override
    public StageImp load(String sceneName, StageImp.StageLoaderParameters params) throws Exception {
        String dexPath = "";
        // Logic adapted from StageImp.getFromDex: find the path or use default
        if(params != null && params.dexPath != null && !params.dexPath.isEmpty()) {
            dexPath = params.dexPath;
            File f = new File(dexPath);
            if(f.isDirectory()){
                File target = new File(f, "dex/scenes.dex");
                if(target.exists()) dexPath = target.getAbsolutePath();
                else {
                    // fallback to game directory? Legacy support?
                    // User specific path project/dex/scenes.dex
                }
            }
        } else {
             File gameDir = new File(filesDir, "game/");
             if(gameDir.isDirectory() && gameDir.listFiles().length > 0)
                dexPath = gameDir.listFiles()[0].getAbsolutePath();
        }
        if(project.getPath().isEmpty()) {
            project.setPath(dexPath.replace("/dex/scenes.dex", ""));
        }
        System.out.println("Loading dex from: " + dexPath);
        
        if(dexPath == null || dexPath.isEmpty()) throw new RuntimeException("No dex file found");

        File optimizedDex = new File(codeCacheDir, "dex");
        optimizedDex.mkdirs();
        
        File dexFile = new File(dexPath);
        // Security: Copy dex to secure internal storage to handle "writable dex" restrictions (Android 10+)
        // if the file is on external storage, setReadOnly() might fail or be ignored.
        File secureDir = new File(filesDir, "secure_dex");
        secureDir.mkdirs();
        File internalDex = new File(secureDir, "loaded_" + System.currentTimeMillis() + ".dex");
        
        // Copy content
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(dexFile);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(internalDex);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fis.close();
            fos.close();
        } catch(java.io.IOException ex) {
            throw new RuntimeException("Failed to copy dex to internal storage: " + ex.getMessage(), ex);
        }

        // Set read-only on the INTERNAL file
        if (!internalDex.setReadOnly()) {
            System.err.println("Warning: Failed to mark internal dex as read-only.");
        }
        
        Class<?> playerClass = null;
        try {
            DexClassLoader dcl = new DexClassLoader(internalDex.getAbsolutePath(), optimizedDex.getAbsolutePath(), null, getClass().getClassLoader());
            try {
                playerClass = dcl.loadClass("com.star4droid.Game." + sceneName);
            } catch (ClassNotFoundException e) {
                playerClass = dcl.loadClass("com.star4droid.Game." + sceneName.toLowerCase());
            }
        } finally {
            // Cleanup: delete the temp file
            // We can delete after loading class? Maybe safer to keep until class usage is done if native?
            // Usually DexClassLoader keeps it open. But we can mark for delete on exit or try delete.
            // On Android, open files can typically be deleted (unlinked).
            try {
                 if(internalDex.exists()) {
                     internalDex.delete(); 
                     // Or secureDir.delete();
                 }
            } catch(Exception e){}
        }
        java.lang.reflect.Constructor<?> constructor = playerClass.getConstructor();
        
        // Build the StageImp
        // Note: StageImp setters return 'this', so we can chain them.
        StageImp imp = (StageImp) constructor.newInstance();
        imp.setAssetsLoader(projectAssetLoader)
           .setProject(project)
           .setSpriteSheetLoader(spriteSheetLoader);
           
        PropertySet properties = this.set;
        if(properties == null) {
            try {
                String scenePath = project.getScenesPath() + sceneName;
                String content = Utils.readFile(scenePath);
                if(content != null && !content.isEmpty()) {
                    properties = PropertySet.getFrom(content);
                    System.out.println("Loaded properties for scene: " + sceneName);
                } else {
                    System.err.println("Warning: Scene file empty or not found at: " + scenePath);
                }
            } catch(Exception ex) {
                System.err.println("Error loading scene properties: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        
        if (properties == null) {
            System.err.println("Failed to load properties, using default empty set.");
            properties = new PropertySet<>();
        }
        
        imp.setPropertySet(properties);
           
        return imp;
    }
}
