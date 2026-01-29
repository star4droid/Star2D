package com.star4droid.template;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.editor.TestApp;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Star2D Editor");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        
        // Parse command line args for project path
        String projectPath = null;
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--project") && i + 1 < args.length) {
                projectPath = args[i + 1];
            }
        }
        
        // Initialize platform services
        DesktopPlatformServices platformServices = new DesktopPlatformServices();
        PlatformServices.Holder.set(platformServices);
        
        // Create and run TestApp (the full editor)
        if (projectPath != null) {
            Project project = new Project(projectPath);
            TestApp app = new TestApp(project);
            new Lwjgl3Application(app, config);
        } else {
            // Start without project - show project selection
            TestApp app = new TestApp();
            new Lwjgl3Application(app, config);
        }
    }
}
