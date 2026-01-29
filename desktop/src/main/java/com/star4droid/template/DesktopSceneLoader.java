package com.star4droid.template;

import com.star4droid.star2d.Helpers.Project;

/**
 * Desktop implementation of SceneLoader.
 * Stub for now - full scene loading coming soon.
 */
public class DesktopSceneLoader {
    private final Project project;

    public DesktopSceneLoader(Project project) {
        this.project = project;
    }
    
    public Project getProject() {
        return project;
    }
    
    /**
     * Load a scene by name.
     * Stub implementation - shows "SOON" for now.
     */
    public Object load(String sceneName, Object params) throws Exception {
        System.out.println("[SOON] Desktop scene loading for: " + sceneName);
        throw new UnsupportedOperationException("Scene loading on desktop coming soon");
    }
}
