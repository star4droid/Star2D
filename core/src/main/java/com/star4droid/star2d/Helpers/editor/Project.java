package com.star4droid.star2d.Helpers.editor;

import com.star4droid.star2d.Helpers.CoreFileUtil;

/**
 * Editor-specific Project wrapper.
 * This is separate from the core Project class.
 */
public class Project {
    private String path;
    
    public Project(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getConfig(String scene) {
        return path + "/scenes/" + scene;
    }
    
    public String getSceneScript(String scene) {
        return path + "/java/com/star4droid/Game/" + scene.toLowerCase() + ".java";
    }
    
    public String getVariables(String scene) {
        return path + "/variables/" + scene + ".json";
    }
    
    public String getScriptsPath(String scene) {
        return path + "/scripts/" + scene + "/";
    }
    
    public String getBodiesScripts(String scene) {
        return path + "/java/com/star4droid/Game/Scripts/" + scene + "/";
    }
    
    public void save(Object editor) {
        // Stub - save functionality
        System.out.println("[SOON] Project.save for desktop");
    }
    
    public void save(String sceneName) {
        System.out.println("[SOON] Project.save(sceneName) for desktop");
    }
}
