package com.tyron.code.ui.project;

import com.tyron.builder.project.Project;
import java.io.File;

/**
 * Simplified ProjectManager for Star2D integration.
 */
public class ProjectManager {

    private static volatile ProjectManager INSTANCE = null;
    private Project mCurrentProject;

    public static synchronized ProjectManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ProjectManager();
        }
        return INSTANCE;
    }

    private ProjectManager() {}

    public synchronized Project getCurrentProject() {
        return mCurrentProject;
    }

    public void setCurrentProject(Project project) {
        mCurrentProject = project;
    }
    
    // Add other methods if referenced, but for now this covers JavaAutoCompleteProvider usage.
    
    public interface OnProjectOpenListener {
        void onProjectOpen(Project project);
    }
    
    public void addOnProjectOpenListener(OnProjectOpenListener listener) {
        // Implementation if needed
    }

    public void removeOnProjectOpenListener(OnProjectOpenListener listener) {
         // Implementation if needed
    }
}
