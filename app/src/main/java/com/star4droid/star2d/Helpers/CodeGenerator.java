package com.star4droid.star2d.Helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Items.*;
import com.star4droid.star2d.CodeEditor.smart.SourceTrace;
import com.badlogic.gdx.files.FileHandle;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.Utils;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.variables.VarModel;
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
    // An element with no event code gets no elementEvents at all (the runtime null-checks it).
    private static final String defualtItemCode = 
        "\n\t\t%1$s %2$s_def = new %1$s();\n" + //%1 => item def from ElementsDef class. 2 => name
        "%3$s\n" + // => properites init...
        "\t\t%2$s = (PlayerItem)(%2$s_def.build(this));\n";
    
    // An element with events overrides ONLY the events that have code (ElementEventAdapter keeps getName()).
    private static final String itemWithEventsCode = 
        "\n\t\t%1$s %2$s_def = new %1$s();\n" +
        "%3$s\n" +
        "\t\t%2$s_def.elementEvents = new ElementEventAdapter(\"%2$s\"){\n" +
        "%4$s" +
        "\t\t};\n" +
        "\t\t%2$s = (PlayerItem)(%2$s_def.build(this));\n";
    
    /** Signature of each event method, in the order of the event files (onClick, onTouchStart, onTouchEnd, OnBodyCreated, OnBodyUpdate, onBodyCollided, onBodyCollideEnd). */
    private static final String[] EVENT_SIGNATURES = {
        "onClick(PlayerItem current)",
        "onTouchStart(PlayerItem current, InputEvent event)",
        "onTouchEnd(PlayerItem current, InputEvent event)",
        "onBodyCreated(PlayerItem current)",
        "onBodyUpdate(PlayerItem current)",
        "onCollisionBegin(PlayerItem current, PlayerItem body2)",
        "onCollisionEnd(PlayerItem current, PlayerItem body2)"
    };
    
    private static final String defaultLightCode = 
        "\n\t\t%1$s %2$s_def = new %1$s();\n" + //%1 => item def from ElementsDef class. 2 => name
        "%3$s \n\t\t\n" + // => properites init...
        "\t%2$s = (%2$s_def.build(this));\n";
    
    private static final String defualtJointCode =
        "\n\t\t\t%1$sDef %2$s_Def = new %1$sDef(); %3$s \n"; //e.g = WheelJointDef wj = new WheelJointDef();
    
    private static final String JointInit =
        "\n\t\t\t%1$s_Def.initialize(%2$s);\n" +
        "\t%1$s = (%3$s)(this.world.createJoint(%1$s_Def));\n"+
        "\taddJoint(\"%1$s\",%1$s);";
    
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
    
    public interface AllListener {
        /** {@code all} has the Java of every scene (already written to the project); {@code changed} tells whether any file differs from before. */
        void onGenerate(ArrayList<SceneCode> all, boolean changed);
    }

    /**
     * Generate and write the Java of ALL scenes of the editor's project (the open one from its live items),
     * off the GL thread; the listener runs on the GL thread.
     */
    public static void generateAllFor(LibgdxEditor editor, final AllListener listener) {
        final ArrayList<PropertySet<String, Object>> properties = new ArrayList<>();
        for (Actor actor : editor.getActors())
            if (actor instanceof EditorItem) properties.add(((EditorItem) actor).getPropertySet());
        final com.star4droid.star2d.Helpers.editor.Project project = editor.getProject();
        final String scene = editor.getScene();
        new Thread(() -> {
            ArrayList<SceneCode> all = null;
            boolean changed = false;
            try {
                all = buildAll(project, scene, properties);
                java.util.HashSet<String> keep = new java.util.HashSet<>();
                for (SceneCode sc : all) {
                    keep.add(new java.io.File(sc.path).getPath());
                    FileHandle f = Gdx.files.absolute(sc.path);
                    if (!f.exists() || !f.readString().equals(sc.code)) {
                        changed = true;
                        f.writeString(sc.code, false);
                    }
                }
                if (deleteStaleRefs(project, keep)) changed = true;
            } catch (Throwable t) {
                com.star4droid.star2d.logs.Logs.write(com.star4droid.star2d.logs.Logs.BUILD, "code_generation", Utils.getStackTraceString(t));
            }
            final ArrayList<SceneCode> res = all;
            final boolean ch = changed;
            // null = code generation failed (see logs/build/code_generation.txt): the caller must still end its "building" state
            Gdx.app.postRunnable(() -> listener.onGenerate(res, ch));
        }).start();
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
        new Thread(() -> {
            String result = null;
            try {
                result = build(project, scene, properties);
                writeRefs(project, scene, properties);
            } catch (Throwable t) {
                com.star4droid.star2d.logs.Logs.write(com.star4droid.star2d.logs.Logs.BUILD, "code_generation", Utils.getStackTraceString(t));
            }
            final String code = result;
            if (generateListener != null && code != null)
                Gdx.app.postRunnable(() -> generateListener.onGenerate(code));
        }).start();
    }

    /**
     * Generate the Java of one scene synchronously (no UI thread needed). User-written pieces are
     * wrapped in marker comments and recorded in a {@link SourceTrace} so compiler errors can be
     * traced back to the block / line (see {@link SourceTrace#of(String)}).
     */
    public static String build(final com.star4droid.star2d.Helpers.editor.Project project,
                               final String scene,
                               final ArrayList<PropertySet<String, Object>> properties) {
        return build(project, scene, properties, true);
    }

    /** File path -> text to use instead of the file's content (only for the generating thread). Used by the editor. */
    private static final ThreadLocal<Map<String, String>> OVERRIDES = new ThreadLocal<>();

    /** Like {@link #build(com.star4droid.star2d.Helpers.editor.Project, String, ArrayList)} but with some files replaced by the given text and no re-formatting, so offsets inside the replaced text stay exact. */
    public static String buildWithOverrides(com.star4droid.star2d.Helpers.editor.Project project, String scene,
                                            ArrayList<PropertySet<String, Object>> properties, Map<String, String> overrides) {
        OVERRIDES.set(overrides);
        try {
            return build(project, scene, properties, false);
        } finally {
            OVERRIDES.remove();
        }
    }

    private static final ThreadLocal<SourceTrace> LAST_TRACE = new ThreadLocal<>();

    /** Trace of the last {@code build*} call made on this thread. */
    public static SourceTrace lastTrace() {
        return LAST_TRACE.get();
    }

    private static String fileText(String path) {
        Map<String, String> o = OVERRIDES.get();
        if (o != null && o.containsKey(path)) return o.get(path);
        return null;
    }

    public static String build(final com.star4droid.star2d.Helpers.editor.Project project,
                               final String scene,
                               final ArrayList<PropertySet<String, Object>> properties,
                               final boolean format) {
        final SourceTrace trace = new SourceTrace(project.getCodesPath(scene));
        // an element whose default properties could not be created has no property set: it is left out instead of failing the whole build
        properties.removeIf(java.util.Objects::isNull);
        {
            {
                // Initialize code building variables - preserved from original implementation
                String itemsCode = "";
                String fixZ = "";
                String addChilds = "";
                String vars = "";
                String lightsVar = "";
                String jointVars = "";
				final ArrayList<String> neededScenes = new ArrayList<>();
				neededScenes.add(scene);
                final StringBuilder scriptBuilder = new StringBuilder("");
                boolean thereIsScript = false;
                // behaviors + public-variable overrides of every element (emitted once all elements exist)
                final StringBuilder behaviorCode = new StringBuilder();
                final com.star4droid.star2d.behaviors.BehaviorCodeGen.Env behaviorEnv = new com.star4droid.star2d.behaviors.BehaviorCodeGen.Env() {
                    @Override public String projectPath() { return project.getPath(); }
                    @Override public String eventCode(String body, String event) {
                        return readTraced(trace, project, scene, body, event, scene + " › " + body + " › " + event);
                    }
                    @Override public String readFile(String path) { return FileUtil.readFile(path); }
                };
                
                // Collect and process all script files for this scene
                ArrayList<String> scriptsList = new ArrayList<>();
                FileUtil.listDir(project.getScriptsPath(scene), scriptsList);
                
                for (String path : scriptsList) {
                    if (path.endsWith(".java") || path.endsWith(".code")) {
                        String body = fileText(path) != null ? fileText(path) : FileUtil.readFile(path);
                        SourceTrace.Segment seg = trace.add(path, null, body, scene + " › " + new java.io.File(path).getName());
                        scriptBuilder.append(SourceTrace.wrap(seg, body));
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
                        StringBuilder fieldsInit = new StringBuilder("");
                        
                        // Parse joint properties from JSON
                        ArrayList<HashMap<String, Object>> fields = new Gson().fromJson(
                            FileUtil.readFile(path),
                            new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType()
                        );
                        
                        float worldScale = com.star4droid.template.Items.StageImp.WORLD_SCALE;
                        // Process joint properties
                        for (HashMap<String, Object> hash : fields) {
                            if (shouldSkip > 0) {
                                // Skip initialization vars (constructor)
                                initilaizer += (initilaizer.equals("") ? "" : ",") + 
                                              hash.get("value").toString().replace("&&", ",").replace("f,","f*"+worldScale+"f,").replace("f)","f*"+worldScale+"f)");
                                shouldSkip--;
                                continue;
                            }
                            
                            fieldsInit.append(String.format(
                                hash.get("code").toString().replace("ff", "f").replace("f,","f*"+worldScale+"f,").replace("f)","f*"+worldScale+"f)"),
                                p.split("-")[0] + "_Def"
                            ));
                        }
                        
                        init.append(fieldsInit);
                        
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
						String[] scriptSceneList = script.contains("/") ? script.split("/") : null;
                        String scriptScene = scriptSceneList == null ? scene : scriptSceneList[0];
						if(scriptSceneList!=null && !neededScenes.contains(scriptScene))
							neededScenes.add(scriptScene);
						if(scriptSceneList!=null)
							script = scriptSceneList[1];//.substring(0,scriptSceneList[1].lastIndexOf("Script"));
						//Gdx.files.external("logs/script.txt").writeString(String.format("script : %1$s, scene : %2$s, path : %3$s",script,scriptScene,project.getBodyScriptPath(script,scriptScene))+"\n",true);
                        // Load event handlers for non-light objects
                        if (!isLight) {
                            for (int ev = 0; ev < eventsList.length - 1; ev++) {
                                eventsList[ev] = readTraced(trace, project, scriptScene, script, eventsList[ev], scene + " › " + name + " › " + eventsList[ev]);
                            }
                        }
                        
                        // Only the events that have code are overridden (marker wrappers count as code, so
                        // the editor's trace regions survive); no events at all -> no elementEvents.
                        StringBuilder eventMethods = new StringBuilder();
                        for (int ev = 0; ev < EVENT_SIGNATURES.length; ev++) {
                            String code = eventsList[ev];
                            if (code == null || code.isEmpty()) continue;
                            eventMethods.append("\t\t\t@Override public void ").append(EVENT_SIGNATURES[ev]).append(" {\n")
                                    .append(code).append("\n\t\t\t}\n");
                        }
                        String events = eventMethods.toString();
                        // object variables: the element's Variables section + "object variable" blocks of any of its events
                        java.util.List<com.star4droid.star2d.behaviors.ObjectVarsGen.Var> objectVars = new java.util.ArrayList<>();
                        if (!isLight) {
                            StringBuilder allEvents = new StringBuilder();
                            for (int ev = 0; ev < eventsList.length - 1; ev++) if (eventsList[ev] != null) allEvents.append(eventsList[ev]).append('\n');
                            objectVars = com.star4droid.star2d.behaviors.ObjectVarsGen.merge(
                                    com.star4droid.star2d.behaviors.ObjectVarsGen.parse(propertySet.getString(com.star4droid.star2d.behaviors.ObjectVarsGen.PROPERTY)),
                                    com.star4droid.star2d.behaviors.ObjectVarsGen.fromCode(allEvents.toString()));
                        }
                        
                        // Create instance of the item definition class for property comparison
                        Class<?> cls = Class.forName("com.star4droid.star2d.ElementDefs." + defType);
                        Object ins = cls.getConstructor().newInstance();
                        
                        // Process each property
                        for (Map.Entry<String, Object> entry : propertySet.entrySet()) {
                            String propertyKey = entry.getKey();
                            
                            // Skip certain properties
                            // Script: the runtime never reads ItemDef.Script (the script object is attached by the generated setScript below)
                            if ("(TYPE)(Shape)(lock)(parent)(prefab)(pid)(pbase)(Script)(behaviors)(scriptVars)(objectVars)(net)".contains("(" + propertyKey + ")")) {
                                continue;
                            }
                            if (propertyKey.equals("lock")) {
                                continue;
                            }
                            
                            // Get field from definition class
                            java.lang.reflect.Field field;
                            try {
                                field = cls.getField(propertyKey.replace(" ", "_"));
                            } catch (NoSuchFieldException nsf) {
                                continue; // key this element type doesn't have (e.g. Collider* on a GROUP): ignore
                            }
                            // constants of a definition (CameraDef.type is "static final"): the property can't be assigned in code
                            if (java.lang.reflect.Modifier.isFinal(field.getModifiers()) || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                                continue;
                            }
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
                            //String qu1 = fieldType.equals("String") ? "\"" : "";
                            String qu2 = fieldType.equals("float") ? "f" : "";
                            
                            // Add property initialization code
                            // replace (") from string by (\")
                            initCode = (initCode.equals("") ? "" : (initCode + "\n")) + 
                                      "\t\t\t" + name + "_def." + propertyKey.replace(" ", "_") + 
                                      "=" + (fieldType.equals("String") ? com.star4droid.star2d.behaviors.ValueEmit.javaLiteral(entry.getValue().toString()) : entry.getValue().toString()) + qu2 + ";";
                        }
                        
                        // UIHolder: events of the inner UI elements (Events/<scene>/private/<holder>/ui/<node>/<event>)
                        if (!isLight && "UIHOLDER".equals(propertySet.getString("TYPE"))) {
                            String uiEv = UiEventsGen.generate(trace, project, scene, name, propertySet.getString("ui"));
                            if (!uiEv.isEmpty()) initCode = (initCode.equals("") ? "" : (initCode + "\n")) + uiEv;
                        }
                        // Generate item code using appropriate template
                        if (!objectVars.isEmpty()) {
                            itemsCode = itemsCode + (itemsCode.equals("") ? "" : "\n")
                                    + com.star4droid.star2d.behaviors.ObjectVarsGen.itemCode(defType, name, initCode, events, objectVars);
                            behaviorCode.append(com.star4droid.star2d.behaviors.ObjectVarsGen.afterBuild(name, objectVars));
                        } else
                        itemsCode = itemsCode + (itemsCode.equals("") ? "" : "\n") + 
                                   String.format(
                                       isLight ? defaultLightCode : (events.isEmpty() ? defualtItemCode : itemWithEventsCode),
                                       defType,
                                       name,
                                       initCode,
                                       events
                                   );
                        
                        // Add Z-index fixing code for non-light objects
                        // (3D elements: z is a position in meters, not a drawing order)
                        if (!isLight && !java.util.Arrays.asList("OBJ3D", "LIGHT3D", "CAMERA3D", "NAVGRID", "NETWORK").contains(propertySet.getString("TYPE"))) {
                            fixZ = fixZ + String.format(fixZIndexDefault, name);
                        }

                        // A TEXT / PROGRESS that reacts to no touch lets the touch pass to what lies below it (label on a button).
                        if (!isLight) {
                            String tp = propertySet.getString("TYPE");
                            if (("TEXT".equals(tp) || "PROGRESS".equals(tp)) && !reactsToTouch(project, eventsList, script, scriptScene)) {
                                fixZ = fixZ + "\n" + name + ".getActor().setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);\n";
                            }
                        }
                        
                        // Handle parent-child relationships
                        // (the "parent" name is the truth; the PropertySet link is only a fallback for sets without it)
                        if (!isLight) {
                            String parentOfItem = propertySet.containsKey("parent") ? propertySet.getString("parent")
                                    : (propertySet.getParent() != null ? propertySet.getParent().getString("name") : "");
                            if (!parentOfItem.equals("") && !parentOfItem.equals(name)) {
                                boolean parentOk = false;
                                for (PropertySet<String, Object> other : properties) {
                                    if (parentOfItem.equals(other.getString("name")) && !other.getString("TYPE").equals("LIGHT")) {
                                        parentOk = true;
                                        break;
                                    }
                                }
                                if (parentOk)
                                    addChilds += parentOfItem + ".addChild(" + name + ");\n";
                            }
                        }
                        
                        // Add script attachment if needed
                        if (!FileUtil.readFile(project.getBodyScriptPath(script, scriptScene)).equals("")) {
                            itemsCode += String.format(
                                "\n%2$s.setScript(new com.star4droid.Game.Scripts.%3$s.%1$sScript().setItem(%2$s).setStage(this));\n",
                                script,
                                name,
								scriptScene.toLowerCase()
                            );
                            thereIsScript = true;
                        }
                        
                        // Behaviors and public-variable overrides of this element
                        try {
                            String bodyScriptFile = project.getBodyScriptPath(script, scriptScene);
                            String bhv = com.star4droid.star2d.behaviors.BehaviorCodeGen.forItem(behaviorEnv, name,
                                    propertySet.getString("behaviors"), propertySet.getString("scriptVars"),
                                    FileUtil.readFile(bodyScriptFile).equals("") ? null : bodyScriptFile);
                            if (bhv != null && !bhv.isEmpty()) {
                                behaviorCode.append(bhv);
                                thereIsScript = true;
                            }
                        } catch (Exception behaviorEx) {
                            behaviorCode.append("\n// behaviors of ").append(name).append(": ").append(behaviorEx).append("\n");
                        }
                        // Network card of the element: the same on every phone of a network game
                        if (!isLight) behaviorCode.append(com.star4droid.star2d.behaviors.NetSpec.register(name, propertySet.getString(com.star4droid.star2d.behaviors.NetSpec.PROPERTY)));
                        
                        // Add variable declarations
                        if (!propertySet.getString("TYPE").equals("LIGHT")) {
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
                boolean hasSceneScript = FileUtil.isExistFile(project.getSceneScript(scene));
                // Scripts that extend the generated <scene>ItemRefs / <scene>SceneRefs get the elements once ALL of them exist
                String bindRefs = (thereIsScript || hasSceneScript) ? "\nbindScriptRefs();\n" : "";
                itemsCode += "\n" + fixZ + (addChilds.equals("")?"":("\n"+addChilds)) + joint.toString() + bindRefs + behaviorCode + readTraced(trace, project, scene, "", "OnCreate", scene + " › OnCreate");
                
                // Finalize variable declarations
                if (!vars.equals("")) {
                    vars += ";";
                }
                if (!lightsVar.equals("")) {
                    lightsVar += ";";
                }
                String sceneScript = hasSceneScript ? ("\nsetScript(new com.star4droid.game.SceneScript."+scene.toLowerCase()+"Script().setStage(this));\nbindScriptRefs();" + sceneScriptVarsCode(project, scene, behaviorEnv)) : "";
                // Prepare final code and variables
                final String code = itemsCode + sceneScript;
                final String userVarsJsonPath = project.getVariables(scene);
                final StringBuilder userVars = new StringBuilder();
                try {
                    String json = Gdx.files.absolute(userVarsJsonPath).readString();
                    java.util.ArrayList<VarModel> varModelList = new Gson().fromJson(json, new TypeToken<ArrayList<VarModel>>() {}.getType());
                    varModelList.forEach(model -> userVars.append(model.getCode()).append("\n"));
                } catch(Error | Exception ex){}
                final String variables = userVars.toString() + vars + "\n" + jointVars + "\n" + lightsVar;
                final boolean replaceImportOfTheScript = (!thereIsScript);
                
                // Load Player.java template
                String playerCode = Gdx.files.internal("java/Player.java").readString();
                String onPause = readTraced(trace, project, scene, "", "OnPause", scene + " › OnPause"),
                        onResume = readTraced(trace, project, scene, "", "OnResume", scene + " › OnResume"),
                        onStep = readTraced(trace, project, scene, "", "OnStep", scene + " › OnStep"),
                        onCollStart = readTraced(trace, project, scene, "", "onCollisionStart", scene + " › onCollisionStart"),
                        onCollEnd = readTraced(trace, project, scene, "", "onCollisionEnd", scene + " › onCollisionEnd");
                // scene events of the path finding / animation / network systems (overrides of StageImp hooks)
                final SourceTrace fTrace = trace;
                final com.star4droid.star2d.Helpers.editor.Project fProject = project;
                final String fScene = scene;
                String extraEvents = com.star4droid.star2d.behaviors.SceneEventsExt.overrides(new com.star4droid.star2d.behaviors.SceneEventsExt.Reader() {
                    @Override
                    public String code(String ev) {
                        return readTraced(fTrace, fProject, fScene, "", ev, fScene + " › " + ev);
                    }
                });
                String codeResult = String.format(
                        playerCode,
                        scene.toLowerCase(),
                        variables,
                        code,
                        onPause,
                        onResume,
                        onStep,
                        onCollStart,
                        onCollEnd,
                        scriptBuilder.toString() + extraEvents);
                // Format the code using Palantir Java Format (returns the input when it fails)
                String finalCode = format ? formatWithPalantir(codeResult) : codeResult;
                trace.finish(finalCode);
                LAST_TRACE.set(trace);
                if (format) SourceTrace.remember(trace);
                return finalCode;
            }
        }
    }

    // ------------------------------------------------------------------ generated Refs base classes

    /** Folder (inside the project's java folder) of the generated <scene>ItemRefs / <scene>SceneRefs classes. */
    public static String refsDir(com.star4droid.star2d.Helpers.editor.Project project) {
        return project.getPath() + "/java/com/star4droid/Game/Refs/";
    }

    private static final java.util.HashSet<String> JAVA_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do",
            "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "_"));
    /** Members of ItemScript / SceneScript a generated field must not hide. */
    private static final java.util.HashSet<String> REFS_RESERVED = new java.util.HashSet<>(java.util.Arrays.asList(
            "playerItem", "body", "actor", "stage", "ID", "linkedScript", "bindRefs"));

    private static boolean isJavaName(String n) {
        return n != null && n.matches("[A-Za-z_][A-Za-z0-9_]*") && !JAVA_WORDS.contains(n);
    }

    /** Class-name prefix of a scene's Refs classes (lower case, like the generated scene class). */
    public static String refsName(String scene) {
        return scene.toLowerCase();
    }

    /**
     * The two generated base classes of a scene: {@code <scene>ItemRefs extends ItemScript} and
     * {@code <scene>SceneRefs extends SceneScript}. Every element of the scene is a public field
     * (PlayerItem, or Light for lights), filled by {@code bindRefs} once all elements exist, so a script
     * that extends one of them can write {@code player.getBody()} without declaring or finding anything.
     * Returns an empty list when the scene name is not a valid Java name or is a temporary prefab scene.
     */
    public static ArrayList<SceneCode> refsFor(com.star4droid.star2d.Helpers.editor.Project project, String scene,
                                               ArrayList<PropertySet<String, Object>> properties) {
        ArrayList<SceneCode> out = new ArrayList<>();
        if (scene == null || com.star4droid.star2d.prefab.PrefabStore.isTempScene(scene) || !isJavaName(refsName(scene))) return out;
        java.util.Map<String, java.util.List<String>> uiNodes = uiHolderNodes(project, properties);
        for (int kind = 0; kind < 2; kind++) {
            boolean item = kind == 0;
            String cls = refsName(scene) + (item ? "ItemRefs" : "SceneRefs");
            StringBuilder items = new StringBuilder(), lights = new StringBuilder(), skipped = new StringBuilder();
            StringBuilder bindItems = new StringBuilder(), bindLights = new StringBuilder();
            StringBuilder holders = new StringBuilder(), nodeFields = new StringBuilder(), bindUi = new StringBuilder();
            java.util.HashSet<String> seen = new java.util.HashSet<>(), holderNames = new java.util.HashSet<>();
            for (PropertySet<String, Object> ps : properties) {
                String name = ps.getString("name");
                if (name == null || !seen.add(name)) continue;
                if (!isJavaName(name) || REFS_RESERVED.contains(name)) {
                    skipped.append(skipped.length() == 0 ? "" : ", ").append(name);
                    continue;
                }
                if (ps.getString("TYPE").equals("UIHOLDER")) {
                    holders.append(holders.length() == 0 ? "" : ", ").append(name);
                    holderNames.add(name);
                    bindItems.append("        ").append(name).append(" = (UIHolder) items.get(\"").append(name).append("\");\n");
                    continue;
                }
                if (ps.getString("TYPE").equals("LIGHT")) {
                    lights.append(lights.length() == 0 ? "" : ", ").append(name);
                    bindLights.append("        ").append(name).append(" = findLight(\"").append(name).append("\");\n");
                } else {
                    items.append(items.length() == 0 ? "" : ", ").append(name);
                    bindItems.append("        ").append(name).append(" = items.get(\"").append(name).append("\");\n");
                }
            }
            // inner elements of the UI holders: hud_btnPlay = hud.node("btnPlay")
            for (java.util.Map.Entry<String, java.util.List<String>> h : uiNodes.entrySet()) {
                if (!holderNames.contains(h.getKey())) continue;
                for (String id : h.getValue()) {
                    String field = h.getKey() + "_" + id;
                    if (!isJavaName(id) || !isJavaName(field) || !seen.add(field)) {
                        skipped.append(skipped.length() == 0 ? "" : ", ").append(field);
                        continue;
                    }
                    nodeFields.append(nodeFields.length() == 0 ? "" : ", ").append(field);
                    bindUi.append("        ").append(field).append(" = ").append(h.getKey()).append(" == null ? null : ")
                          .append(h.getKey()).append(".node(\"").append(id).append("\");\n");
                }
            }
            StringBuilder src = new StringBuilder();
            src.append("package com.star4droid.Game.Refs;\n\n")
               .append("import com.star4droid.template.Utils.PlayerItem;\n")
               .append(holders.length() > 0 ? "import com.star4droid.template.Items.UIHolder;\nimport com.star4droid.template.ui.UINodeRef;\n" : "")
               .append(item ? "import com.star4droid.template.Utils.ItemScript;\n" : "import com.star4droid.template.Utils.SceneScript;\n")
               .append("import box2dLight.Light;\n\n")
               .append("/**\n * Generated by Star2D for scene \"").append(scene.replace("*/", "")).append("\" - rewritten on every build, do not edit.\n")
               .append(" * Extend it instead of ").append(item ? "ItemScript" : "SceneScript")
               .append(" and every element of the scene is a field: player.getBody(), coin1.destroy() ...\n");
            if (skipped.length() > 0) src.append(" * Not available here (invalid or clashing names): ").append(skipped.toString().replace("*/", "")).append("\n");
            src.append(" */\n")
               .append("public abstract class ").append(cls).append(" extends ").append(item ? "ItemScript" : "SceneScript").append(" {\n");
            if (items.length() > 0) src.append("    public PlayerItem ").append(items).append(";\n");
            if (lights.length() > 0) src.append("    public Light ").append(lights).append(";\n");
            if (holders.length() > 0) src.append("    public UIHolder ").append(holders).append(";\n");
            if (nodeFields.length() > 0) src.append("    /** Inner elements of the UI holders: <holder>_<nodeId>. */\n    public UINodeRef ").append(nodeFields).append(";\n");
            src.append("\n    @Override\n    public void bindRefs(java.util.Map<String, PlayerItem> items) {\n")
               .append("        super.bindRefs(items);\n")
               .append(bindItems).append(bindLights).append(bindUi)
               .append("    }\n}\n");
            SceneCode sc = new SceneCode();
            sc.scene = scene;
            sc.path = refsDir(project) + cls + ".java";
            sc.code = src.toString();
            out.add(sc);
        }
        return out;
    }

    /** UI holder name -> ids of the nodes of its .ui file (the file may be in a sub folder: "folder/name"); unreadable files give no entry. */
    private static java.util.Map<String, java.util.List<String>> uiHolderNodes(com.star4droid.star2d.Helpers.editor.Project project,
                                                                                ArrayList<PropertySet<String, Object>> properties) {
        java.util.Map<String, java.util.List<String>> out = new java.util.LinkedHashMap<>();
        for (PropertySet<String, Object> ps : properties) {
            try {
                String name = ps.getString("name"), ui = ps.getString("ui");
                if (!"UIHOLDER".equals(ps.getString("TYPE")) || name == null || out.containsKey(name) || ui == null || ui.trim().isEmpty()) continue;
                java.io.File f = com.star4droid.template.ui.UIView.uiFile(project.getPath(), ui);
                if (!f.isFile()) continue;
                com.star4droid.template.ui.UIDocument doc = com.star4droid.template.ui.UIDocument.read(f);
                java.util.List<String> ids = new ArrayList<>();
                if (doc != null) for (com.star4droid.template.ui.UINode n : doc.allNodes()) if (n.id != null && !ids.contains(n.id)) ids.add(n.id);
                out.put(name, ids);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** Writes the Refs classes of a scene when their text changed. True when a file was written. */
    public static boolean writeRefs(com.star4droid.star2d.Helpers.editor.Project project, String scene,
                                    ArrayList<PropertySet<String, Object>> properties) {
        boolean changed = false;
        try {
            for (SceneCode sc : refsFor(project, scene, properties)) {
                FileHandle f = Gdx.files.absolute(sc.path);
                if (!f.exists() || !f.readString().equals(sc.code)) {
                    f.writeString(sc.code, false);
                    changed = true;
                }
            }
        } catch (Throwable t) {
            com.star4droid.star2d.logs.Logs.write(com.star4droid.star2d.logs.Logs.BUILD, "code_generation", Utils.getStackTraceString(t));
        }
        return changed;
    }

    /** Writes the Refs classes of a scene from its SAVED file (used when a script is created). */
    public static boolean writeRefs(com.star4droid.star2d.Helpers.editor.Project project, String scene) {
        return writeRefs(project, scene, loadProperties(project, scene));
    }

    /** Deletes Refs files that no scene needs any more (deleted / renamed scenes). True when something was deleted. */
    private static boolean deleteStaleRefs(com.star4droid.star2d.Helpers.editor.Project project, java.util.Set<String> keepPaths) {
        boolean deleted = false;
        try {
            java.io.File[] fs = new java.io.File(refsDir(project)).listFiles();
            if (fs == null) return false;
            for (java.io.File f : fs) {
                String n = f.getName();
                if (f.isFile() && (n.endsWith("ItemRefs.java") || n.endsWith("SceneRefs.java")) && !keepPaths.contains(f.getPath())) {
                    if (f.delete()) deleted = true;
                }
            }
        } catch (Throwable ignored) {
        }
        return deleted;
    }

    /** Generated Java of one scene. */
    public static final class SceneCode {
        public String scene, path, code;
    }

    /** Names of all scenes of the project (files in the scenes folder). */
    public static ArrayList<String> sceneNames(com.star4droid.star2d.Helpers.editor.Project project) {
        ArrayList<String> out = new ArrayList<>();
        java.io.File[] fs = new java.io.File(project.getScenesPath()).listFiles();
        if (fs != null) for (java.io.File f : fs) if (f.isFile() && !com.star4droid.star2d.prefab.PrefabStore.isTempScene(f.getName())) out.add(f.getName());
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** True when the element has code for onClick / onTouchStart / onTouchEnd (events or a Java script that mentions them). */
    private static boolean reactsToTouch(com.star4droid.star2d.Helpers.editor.Project project, String[] events, String script, String scriptScene) {
        for (int i = 0; i < 3; i++) {
            String e = events[i] == null ? "" : events[i].replaceAll("(?s)/\\*.*?\\*/|//[^\n]*", "").trim();
            if (!e.isEmpty()) return true;
        }
        try {
            String code = FileUtil.readFile(project.getBodyScriptPath(script, scriptScene));
            if (code != null && (code.contains("touchStart") || code.contains("touchEnd") || code.contains("click"))) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** The saved items of a scene (what the scene editor wrote with Save), parents re-linked. */
    public static ArrayList<PropertySet<String, Object>> loadProperties(com.star4droid.star2d.Helpers.editor.Project project, String scene) {
        ArrayList<PropertySet<String, Object>> list = new ArrayList<>();
        try {
            String json = FileUtil.readFile(project.getScenesPath() + scene);
            if (json == null || json.trim().isEmpty()) return list;
            ArrayList<PropertySet<String, Object>> loaded = new Gson().fromJson(json,
                    new TypeToken<ArrayList<PropertySet<String, Object>>>() {}.getType());
            if (loaded == null) return list;
            HashMap<String, PropertySet<String, Object>> byName = new HashMap<>();
            for (PropertySet<String, Object> ps : loaded) {
                ps.init();
                byName.put(ps.getString("name"), ps);
            }
            for (PropertySet<String, Object> ps : loaded) {
                String parent = ps.getString("parent");
                if (parent != null && !parent.isEmpty() && byName.containsKey(parent)) ps.setParent(byName.get(parent));
            }
            list.addAll(loaded);
        } catch (Throwable ignored) {
        }
        return list;
    }

    /**
     * Java for every scene of the project: the scene being edited ({@code liveScene}, with its
     * unsaved {@code liveProperties}) plus all the others from their saved files. Synchronous.
     */
    public static ArrayList<SceneCode> buildAll(com.star4droid.star2d.Helpers.editor.Project project,
                                                String liveScene, ArrayList<PropertySet<String, Object>> liveProperties) {
        ArrayList<SceneCode> out = new ArrayList<>();
        ArrayList<String> names = sceneNames(project);
        if (liveScene != null && !names.contains(liveScene)) names.add(liveScene);
        for (String scene : names) {
            SceneCode sc = new SceneCode();
            sc.scene = scene;
            sc.path = project.getCodesPath(scene);
            ArrayList<PropertySet<String, Object>> props = scene.equals(liveScene) && liveProperties != null
                    ? liveProperties : loadProperties(project, scene);
            try {
                sc.code = build(project, scene, props);
            } catch (Throwable t) {
                com.star4droid.star2d.logs.Logs.write(com.star4droid.star2d.logs.Logs.BUILD, "code_generation", Utils.getStackTraceString(t));
                continue;
            }
            out.add(sc);
            out.addAll(refsFor(project, scene, props));
        }
        return out;
    }

    /**
     * Event code of a piece (a body's event, or a public event when {@code body} is empty), wrapped in
     * trace markers. Block files get one marker per block when their generated code still matches the .java.
     */
    /** Public variables of the scene script set from the inspector (Events/<scene>/public/scriptvars.json). */
    private static String sceneScriptVarsCode(com.star4droid.star2d.Helpers.editor.Project project, String scene,
                                              com.star4droid.star2d.behaviors.BehaviorCodeGen.Env env) {
        try {
            String json = FileUtil.readFile(project.getPath() + "/Events/" + scene + "/public/scriptvars.json");
            if (json == null || json.trim().isEmpty()) return "";
            String c = com.star4droid.star2d.behaviors.BehaviorCodeGen.forSceneScript(env, project.getSceneScript(scene), json);
            return c == null || c.isEmpty() ? "" : "\n" + c;
        } catch (Exception ex) {
            return "";
        }
    }

    static String readTraced(SourceTrace trace, com.star4droid.star2d.Helpers.editor.Project project,
                                     String scene, String body, String event, String origin) {
        String base = project.getEventPath(scene, body, event);
        StringBuilder out = new StringBuilder();
        String javaText = "", codeText = "";
        try {
            FileHandle jf = Gdx.files.absolute(base + ".java");
            if (fileText(base + ".java") != null) javaText = fileText(base + ".java");
            else if (jf.exists()) javaText = jf.readString();
        } catch (Exception ignored) {
        }
        if (!body.equals("")) {
            try {
                FileHandle cf = Gdx.files.absolute(base + ".code");
                if (fileText(base + ".code") != null) codeText = fileText(base + ".code");
                else if (cf.exists()) codeText = cf.readString();
            } catch (Exception ignored) {
            }
        }
        if (javaText.length() > 0) {
            String visual = null, text = javaText;
            java.io.File vf = new java.io.File(base + ".visual");
            if (vf.exists()) {
                try {
                    com.star4droid.star2d.ui.blocks.BlockModel m = com.star4droid.star2d.ui.blocks.BlockModel.load(vf);
                    if (m.exportCode(false).trim().equals(javaText.trim())) {
                        text = m.exportCode(true);
                        visual = vf.getPath();
                    }
                } catch (Throwable ignored) {
                }
            }
            SourceTrace.Segment seg = trace.add(base + ".java", visual, javaText, origin);
            text = FinalLocals.apply(text, codeText);
            out.append(SourceTrace.wrap(seg, text)).append(body.equals("") ? "" : "\n");
        }
        if (codeText.length() > 0) {
            SourceTrace.Segment seg = trace.add(base + ".code", null, codeText, origin);
            out.append(SourceTrace.wrap(seg, FinalLocals.apply(codeText, javaText)));
        }
        return out.toString();
    }

    /**
     * The generated code is used as it is. (The Palantir formatter needed the JDK compiler classes, which Android does
     * not have, so it never ran on phones; it was removed with its ~4.8 MB of libraries.)
     */
    private static String formatWithPalantir(String code) {
        return code;
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
            case "CAMERA": return "CameraDef";
            case "MAP": return "MapDef";
            case "GROUP": return "GroupDef";
            case "UIHOLDER": return "UIHolderDef";
            case "OBJ3D": return "Object3DDef";
            case "LIGHT3D": return "Light3DDef";
            case "CAMERA3D": return "Camera3DDef";
            case "CAMERAVIEW": return "CameraViewDef";
            case "NAVGRID": return "NavGridDef";
            case "NETWORK": return "NetworkDef";
            default: return "Unknown";
        }
    }
}
