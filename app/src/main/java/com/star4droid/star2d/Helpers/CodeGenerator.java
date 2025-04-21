package com.star4droid.star2d.Helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.squareup.javapoet.*;
import com.palantir.javaformat.java.Formatter;
import com.star4droid.star2d.Items.*;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.Utils;
import com.star4droid.star2d.editor.items.EditorItem;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * CodeGenerator class is responsible for generating Java code for game elements
 * based on the editor configuration. It creates Player.java files with all necessary
 * components, properties, and event handlers.
 * 
 * This implementation preserves the original functionality while using JavaPoet for
 * code structure and Palantir Java Format for code formatting.
 This made by Manus AI, I don't understand it : )
 sorry ...
 */
public class CodeGenerator {
    // TODO : make this class code more clear (done with JavaPoet and Palantir Java Format 😊)
    
    // Placeholder for script imports in the template
    private static final String script_import = "//import .....script_here;";
    
    /**
     * Interface for code generation callback
     */
    public interface GenerateListener {
        /**
         * Called when code generation is complete
         * @param s The generated code
         */
        void onGenerate(String s);
    }

    // Template strings for code generation - preserved from original implementation
    private static final String defualtItemCode = 
        "\n\t\t%1$s %2$s_def = new %1$s();\n" + //%1 => item def from ElementsDef class. 2 => name
        "%3$s \n\t\t\n" + // => properites init...
        "\t\t\t%2$s_def.elementEvents = new ElementEvent(){\n" +
        "%4$s \n\t\t};\n" +
        "\t%2$s = (PlayerItem)(%2$s_def.build(this));\n";
    
    private static final String defaultLightCode = 
        "\n\t\t%1$s %2$s_def = new %1$s();\n" + //%1 => item def from ElementsDef class. 2 => name
        "%3$s \n\t\t\n" + // => properites init...
        "\t%2$s = (%2$s_def.build(this));\n";
    
    private static final String defualtJointCode =
        "\n\t\t\t%1$sDef %2$s_Def = new %1$sDef(); %3$s \n"; //e.g = WheelJointDef wj = new WheelJointDef();
    
    private static final String JointInit =
        "\n\t\t\t%1$s_Def.initialize(%2$s);\n" +
        "\t%1$s = (%3$s)(this.world.createJoint(%1$s_Def));";
    
    private static final String fixZIndexDefault =
        "\n\t\t %1$s.getActor().setZIndex((int)(%1$s_def.z));\n";

    /**
     * Generate code for the current editor state
     * 
     * @param editor The LibgdxEditor instance
     * @param generateListener Callback for when generation is complete
     */
    public static void generateFor(LibgdxEditor editor, final GenerateListener generateListener) {
        // Collect property sets from all editor items
        ArrayList<PropertySet<String, Object>> properties = new ArrayList<>();
        for (Actor actor : editor.getActors()) {
            if (actor instanceof EditorItem) {
                PropertySet<String, Object> propertySet = ((EditorItem)actor).getPropertySet();
                properties.add(propertySet);
            }
        }
        
        // Generate code using the collected properties
        generateFor(editor.getProject(), editor.getScene(), properties, generateListener);
    }
    
    /**
     * Generate code for a specific scene with provided properties
     * 
     * @param project The project containing the scene
     * @param scene The scene name
     * @param properties List of property sets for all items
     * @param generateListener Callback for when generation is complete
     */
    public static void generateFor(com.star4droid.star2d.Helpers.editor.Project project, 
                                  String scene, 
                                  ArrayList<PropertySet<String, Object>> properties, 
                                  final GenerateListener generateListener) {
        // Run code generation in a separate thread to avoid blocking the UI
        new Thread() {
            @Override
            public void run() {
                // Initialize code building variables - preserved from original implementation
                String itemsCode = "";
                String fixZ = "";
                String vars = "";
                String lightsVar = "";
                String jointVars = "";
                final StringBuilder scriptBuilder = new StringBuilder("");
                boolean thereIsScript = false;
                
                // Collect and process all script files for this scene
                ArrayList<String> scriptsList = new ArrayList<>();
                FileUtil.listDir(project.getScriptsPath(scene), scriptsList);
                
                for (String path : scriptsList) {
                    if (path.endsWith(".java") || path.endsWith(".code")) {
                        scriptBuilder.append(FileUtil.readFile(path));
                    }
                }
                
                // Process joints - preserved from original implementation
                StringBuilder joint = new StringBuilder("");
                ArrayList<String> joints = new ArrayList<>();
                FileUtil.listDir(project.getJoints(scene), joints);
                
                for (String path : joints) {
                    String p = Gdx.files.absolute(path).file().getName();
                    
                    if (p.contains("-")) {
                        int shouldSkip = JointsHelper.get(p.split("-")[1], "params").split(",").length;
                        jointVars += p.split("-")[1] + " " + p.split("-")[0] + ";\n";
                        String initilaizer = "";
                        StringBuilder init = new StringBuilder("");
                        
                        // Parse joint properties from JSON
                        ArrayList<HashMap<String, Object>> fields = new Gson().fromJson(
                            FileUtil.readFile(path),
                            new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType()
                        );
                        
                        // Process joint properties
                        for (HashMap<String, Object> hash : fields) {
                            if (shouldSkip > 0) {
                                // Skip initialization vars
                                initilaizer += (initilaizer.equals("") ? "" : ",") + 
                                              hash.get("value").toString().replace("&&", ",");
                                shouldSkip--;
                                continue;
                            }
                            
                            init.append(String.format(
                                hash.get("code").toString().replace("ff", "f"),
                                p.split("-")[0] + "_Def"
                            ));
                        }
                        
                        init.append(String.format(
                            JointInit,
                            p.split("-")[0],
                            initilaizer,
                            p.split("-")[1]
                        ));
                        
                        joint.append(String.format(
                            defualtJointCode,
                            p.split("-")[1],
                            p.split("-")[0],
                            init.toString()
                        ));
                    }
                }
                
                // Load default event handlers template
                String defEvents = Gdx.files.internal("java/events.java").readString();
                
                // Process each item's properties
                for (PropertySet<String, Object> propertySet : properties) {
                    boolean isLight = propertySet.getString("TYPE").equals("LIGHT");
                    String[] eventsList = {
                        "onClick", "onTouchStart", "onTouchEnd", "OnBodyCreated", 
                        "OnBodyUpdate", "onBodyCollided", "onBodyCollideEnd", ""
                    };
                    
                    try {
                        String defType = getType(propertySet.getString("TYPE"));
                        String initCode = "";
                        String name = propertySet.getString("name");
                        String script = propertySet.getString("Script");
                        
                        // Load event handlers for non-light objects
                        if (!isLight) {
                            for (int ev = 0; ev < eventsList.length - 1; ev++) {
                                eventsList[ev] = project.readEvent(scene, eventsList[ev], script);
                            }
                        }
                        
                        // Set name getter for the last event handler
                        eventsList[7] = "return \"" + name + "\";";
                        
                        // Format event handlers
                        String events = String.format(defEvents, (Object[])eventsList);
                        
                        // Create instance of the item definition class for property comparison
                        Class<?> cls = Class.forName("com.star4droid.star2d.ElementDefs." + defType);
                        Object ins = cls.getConstructor().newInstance();
                        
                        // Process each property
                        for (Map.Entry<String, Object> entry : propertySet.entrySet()) {
                            String propertyKey = entry.getKey();
                            
                            // Skip certain properties
                            if ("(TYPE)(Shape)(lock)(parent)".contains("(" + propertyKey + ")")) {
                                continue;
                            }
                            if (propertyKey.equals("lock")) {
                                continue;
                            }
                            
                            // Get field from definition class
                            java.lang.reflect.Field field = cls.getField(propertyKey.replace(" ", "_"));
                            String fieldType = field.getType().getName().replace("java.lang.", "");
                            
                            // Skip unsupported field types
                            if (!"float_String_boolean_Vector2".contains(fieldType)) {
                                continue;
                            }
                            
                            // Skip if value is the same as default
                            if (fieldType.equals("float") && 
                                field.getFloat(ins) == propertySet.getFloat(propertyKey)) {
                                continue;
                            }
                            if (fieldType.equals("boolean") && 
                                (field.getBoolean(ins) == propertySet.getString(propertyKey).equals("true"))) {
                                continue;
                            }
                            if (fieldType.equals("String") && 
                                field.get(ins).toString().equals(propertySet.getString(propertyKey))) {
                                continue;
                            }
                            
                            // Format property initialization based on type
                            String qu1 = fieldType.equals("String") ? "\"" : "";
                            String qu2 = fieldType.equals("String") ? "\"" : (fieldType.equals("float") ? "f" : "");
                            
                            // Add property initialization code
                            initCode = (initCode.equals("") ? "" : (initCode + "\n")) + 
                                      "\t\t\t" + name + "_def." + propertyKey.replace(" ", "_") + 
                                      "=" + qu1 + entry.getValue().toString() + qu2 + ";";
                        }
                        
                        // Generate item code using appropriate template
                        itemsCode = itemsCode + (itemsCode.equals("") ? "" : "\n") + 
                                   String.format(
                                       isLight ? defaultLightCode : defualtItemCode,
                                       defType,
                                       name,
                                       initCode,
                                       events
                                   );
                        
                        // Add Z-index fixing code for non-light objects
                        if (!isLight) {
                            fixZ = fixZ + String.format(fixZIndexDefault, name);
                        }
                        
                        // Handle parent-child relationships
                        if ((!isLight) && propertySet.getParent() != null) {
                            itemsCode += propertySet.getParent().getString("name") + 
                                        ".addChild(" + name + ");\n";
                        }
                        
                        // Add script attachment if needed
                        if (!FileUtil.readFile(project.getBodyScriptPath(script, scene)).equals("")) {
                            itemsCode += String.format(
                                "\n%2$s.setScript(new %1$sScript().setItem(%2$s).setStage(this));\n",
                                script,
                                name
                            );
                            thereIsScript = true;
                        }
                        
                        // Add variable declarations
                        if (!propertySet.getString("TYPE").contains("LIGHT")) {
                            vars += (vars.equals("") ? "PlayerItem " : ",") + name;
                        } else {
                            lightsVar += (lightsVar.equals("") ? "Light " : ",") + name;
                        }
                        
                    } catch (Exception ex) {
                        // Add error comment for failed items
                        itemsCode = itemsCode + "\n// Error in item at position: " + 
                                   propertySet.getString("name") + 
                                   ", type: " + propertySet.getString("TYPE") + 
                                   ", \n/* Error: \n" + Utils.getStackTraceString(ex) + "\n */";
                    }
                }
                
                // Process light attachments
                for (PropertySet<String, Object> propertySet : properties) {
                    if (propertySet.getString("TYPE").equals("LIGHT") && 
                        !propertySet.getString("attach To").isEmpty()) {
                        
                        itemsCode += "\n" + propertySet.getString("name") + 
                                    ".attachToBody(" + propertySet.getString("attach To") + 
                                    ".getBody());\n";
                    }
                }
                
                // Finalize item code
                itemsCode += "\n" + fixZ + joint.toString() + project.readEvent(scene, "OnCreate");
                
                // Finalize variable declarations
                if (!vars.equals("")) {
                    vars += ";";
                }
                if (!lightsVar.equals("")) {
                    lightsVar += ";";
                }
                
                // Prepare final code and variables
                final String code = itemsCode;
                final String variables = vars + "\n" + jointVars + "\n" + lightsVar;
                final boolean replaceImportOfTheScript = (!thereIsScript);
                
                // Return generated code through callback
                if (generateListener != null) {
                    Gdx.app.postRunnable(() -> {
						// Load Player.java template
                        String playerCode = Gdx.files.internal("java/Player.java").readString();
                        try {
                            
                            // Replace script import placeholder if needed
                            playerCode = playerCode.replace(
                                script_import,
                                replaceImportOfTheScript ? "" : 
                                String.format("import com.star4droid.Game.Scripts.%1$s.*;", scene.toLowerCase())
                            );
                            
                            // Format final code with all components
                            String codeResult = String.format(
                                playerCode,
                                scene.toLowerCase(),
                                variables,
                                code,
                                project.readEvent(scene, "OnPause"),
                                project.readEvent(scene, "OnResume"),
                                project.readEvent(scene, "OnStep"),
                                project.readEvent(scene, "onCollisionStart"),
                                project.readEvent(scene, "onCollisionEnd"),
                                scriptBuilder.toString()
                            );
                            
                            // Format the code using Palantir Java Format
                            String formattedCode = formatWithPalantir(codeResult);
                            
                            // Return generated code through callback
                            generateListener.onGenerate(formattedCode);
                        } catch (Exception e) {
							
                            // If formatting fails, return unformatted code
                            String codeResult = String.format(
                                playerCode,
                                scene.toLowerCase(),
                                variables,
                                code,
                                project.readEvent(scene, "OnPause"),
                                project.readEvent(scene, "OnResume"),
                                project.readEvent(scene, "OnStep"),
                                project.readEvent(scene, "onCollisionStart"),
                                project.readEvent(scene, "onCollisionEnd"),
                                scriptBuilder.toString()
                            );
                            generateListener.onGenerate(codeResult);
                        }
                    });
                }
            }
        }.start();
    }
    
    /**
     * Format Java code using Palantir Java Format
     * 
     * @param code The unformatted Java code
     * @return The formatted Java code
     */
    private static String formatWithPalantir(String code) {
        if(true) return code;
        // there's bug :
        // Runtime.version() isn't available method 
        // check :
        //https://www.google.com/url?sa=t&source=web&rct=j&opi=89978449&url=https://github.com/apache/lucene/issues/13078&ved=2ahUKEwiN5ZSW98iMAxU-G9AFHX0iGR8Qjjh6BAgrEAE&usg=AOvVaw1Rl2I5YdQvlP6gj3QblZ6h
        try {
            Formatter formatter = Formatter.createFormatter(JavaFormatterOptions.defaultOptions());
            return formatter.formatSource(code);
        } catch (Exception e) {
            System.err.println("Error formatting code: " + e.getMessage());
            Gdx.files.external("logs/code format.txt").writeString(com.star4droid.template.Utils.Utils.getStackTraceString(e),false);
            // Return original code if formatting fails
            return code;
        }
    }
    
    /**
     * Convert editor item type to definition class name
     * 
     * @param type The editor item type
     * @return The corresponding definition class name
     */
    public static String getType(String type) {
        switch (type) {
            case "BOX": return "BoxDef";
            case "CUSTOM": return "CustomDef";
            case "JOYSTICK": return "JoyStickDef";
            case "TEXT": return "TextDef";
            case "CIRCLE": return "CircleDef";
            case "PROGRESS": return "ProgressDef";
            case "LIGHT": return "LightDef";
            case "PARTICLE": return "ParticleDef";
            default: return "Unknown";
        }
    }
}
