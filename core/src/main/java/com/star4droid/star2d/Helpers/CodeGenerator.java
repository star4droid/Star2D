package com.star4droid.star2d.Helpers;

import com.star4droid.star2d.editor.LibgdxEditor;
import java.util.function.Consumer;

/**
 * Stub for CodeGenerator - desktop version.
 * Shows "SOON" for code generation features.
 */
public class CodeGenerator {
    
    public static void generate(String path) {
        System.out.println("[SOON] CodeGenerator.generate: " + path);
    }
    
    public static void generateFor(LibgdxEditor editor, Consumer<String> callback) {
        System.out.println("[SOON] Code generation for desktop coming soon");
        if (callback != null) {
            // Return empty code for now
            callback.accept("// Desktop code generation coming soon\n");
        }
    }
}
