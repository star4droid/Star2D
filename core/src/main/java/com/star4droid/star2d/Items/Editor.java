package com.star4droid.star2d.Items;

import com.star4droid.template.Utils.ProjectAssetLoader;

/**
 * Interface for the Editor.
 * Desktop stub implementation.
 */
public interface Editor {
    
    static Editor currentEditor = null;
    
    void refresh();
    void save();
    
    /**
     * Get the asset loader.
     */
    default ProjectAssetLoader getAssetLoader() {
        return null;
    }
    
    /**
     * Get the libgdx editor.
     */
    default com.star4droid.star2d.editor.LibgdxEditor getLibgdxEditor() {
        return com.star4droid.star2d.editor.LibgdxEditor.getCurrentEditor();
    }
    
    /**
     * Get current editor.
     */
    static Editor getCurrentEditor() {
        return currentEditor;
    }
    
    /**
     * Set current editor.
     */
    static void setCurrentEditor(Editor editor) {
        // currentEditor = editor;
    }
}
