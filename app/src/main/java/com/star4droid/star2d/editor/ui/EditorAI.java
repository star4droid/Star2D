package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.StringBuilder;

import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.star2d.editor.ui.sub.EditorField;
import com.star4droid.star2d.editor.ui.SingleInputDialog;

import java.util.ArrayList;
import java.util.List;
import com.star4droid.star2d.evo.BuildConfig;

public class EditorAI extends VisTable {
    private TestApp app;
    private VisTable chatTable;
    private VisScrollPane chatScroll;
    private VisTextArea inputField;
    private VisTextButton sendBtn;
    private VisSelectBox<String> modelSelector;
    private VisLabel statusLabel;
    
    private ChatHistoryManager historyManager;
    private ChatSession currentSession;
    private static final String PREF_READ_FILES = "ai_read_files";
    
    private static final String PREF_GEMINI_API_KEY = "gemini_api_key";
    private static final String PREF_MODEL = "gemini_model";
    private static final String PREF_ZEN_API_KEY = "zen_api_key";
    private static final String ZEN_API_BASE_URL = "https://opencode.ai/zen/v1/chat/completions";
    
    private static class ModelEntry {
        String displayName;
        String apiModelId;
        String provider;
        ModelEntry(String displayName, String apiModelId, String provider) {
            this.displayName = displayName;
            this.apiModelId = apiModelId;
            this.provider = provider;
        }
    }
    
    private static final ModelEntry[] MODELS = {
        new ModelEntry("Big Pickle", "big-pickle", "opencode"),
        new ModelEntry("DeepSeek V4 Flash Free", "deepseek-v4-flash-free", "opencode"),
        new ModelEntry("MiMo-V2.5 Free", "mimo-v2.5-free", "opencode"),
        new ModelEntry("North Mini Code Free", "north-mini-code-free", "opencode"),
        new ModelEntry("Nemotron 3 Ultra Free", "nemotron-3-ultra-free", "opencode"),
        new ModelEntry("gemini-1.5-flash", "gemini-1.5-flash", "gemini"),
        new ModelEntry("gemini-2.0-flash", "gemini-2.0-flash", "gemini"),
        new ModelEntry("gemini-2.0-flash-lite", "gemini-2.0-flash-lite", "gemini"),
        new ModelEntry("gemini-2.5-pro", "gemini-2.5-pro", "gemini"),
        new ModelEntry("gemini-2.5-flash", "gemini-2.5-flash", "gemini"),
        new ModelEntry("gemini-2.5-flash-lite", "gemini-2.5-flash-lite", "gemini")
    };
    
    private static final String[] MODEL_DISPLAY_NAMES;
    static {
        MODEL_DISPLAY_NAMES = new String[MODELS.length];
        for (int i = 0; i < MODELS.length; i++) {
            MODEL_DISPLAY_NAMES[i] = MODELS[i].displayName;
        }
    }
    
    public EditorAI(TestApp app) {
        this.app = app;
        String projectPath = app.getEditor().getProject().getPath();
        this.historyManager = new ChatHistoryManager(Gdx.files.absolute(projectPath + "/ai_history.json"));
        this.currentSession = historyManager.createNewSession();
        
        setBackground(drawable("window-bg"));
        
        createUI();
        
        Gdx.app.postRunnable(() -> promptForApiKeyIfNeeded());
    }
    
    // --- History Classes ---
    public static class ChatHistoryManager {
        private List<ChatSession> sessions;
        private FileHandle file;
        
        public ChatHistoryManager(FileHandle file) {
            this.file = file;
            load();
        }
        
        public void load() {
             try {
                 if (file.exists()) {
                     Json json = new Json();
                     ArrayList<ChatSession> list = json.fromJson(ArrayList.class, ChatSession.class, file);
                     sessions = (list != null) ? list : new ArrayList<>();
                 } else {
                     sessions = new ArrayList<>();
                 }
             } catch(Exception e) {
                 sessions = new ArrayList<>();
             }
        }
        
        public void saveSession(ChatSession session) {
            if (!sessions.contains(session)) sessions.add(0, session); // Add to top if new
            // Ensure unique
            for(int i=0;i<sessions.size();i++) {
                if(sessions.get(i).id.equals(session.id) && sessions.get(i)!=session) {
                    sessions.set(i, session);
                    break;
                }
            }
            save();
        }
        
        public void deleteSession(ChatSession session) {
            sessions.remove(session);
            save();
        }
        
        private void save() {
            try {
                Json json = new Json();
                file.writeString(json.toJson(sessions), false);
            } catch(Exception e) {}
        }
        
        public ChatSession createNewSession() {
            ChatSession s = new ChatSession();
            s.id = String.valueOf(System.currentTimeMillis());
            return s;
        }
        
        public List<ChatSession> getSessions() { return sessions; }
    }
    
    public static class ChatSession {
        public String id;
        public String title = "New Chat";
        public ArrayList<ChatMessage> messages = new ArrayList<>();
        
        public void addMessage(ChatMessage msg) {
            messages.add(msg);
            if (messages.size() == 1 && title.equals("New Chat")) {
                title = msg.text.length() > 20 ? msg.text.substring(0, 20) + "..." : msg.text;
            }
        }
        
        // Equals based on ID
        @Override
        public boolean equals(Object o) {
            if(o instanceof ChatSession) return ((ChatSession)o).id.equals(id);
            return false;
        }
    }
    
    public static class ChatMessage {
        public String text;
        public boolean isUser;
        public ChatMessage() {}
        public ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }
    }
    
    private void createUI() {
        pad(10);
        
        // --- Header ---
        VisTable header = new VisTable();
        
        // Menu / History Button
        VisImageButton menuBtn = new VisImageButton(drawable("history")); // Using 'list' (ensure exists or fallback)
        menuBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showHistoryDialog();
            }
        });
        header.add(menuBtn).size(48, 48).padRight(5);
        
        header.add(new VisLabel("Star2D AI")).expandX().align(Align.left);
        
        modelSelector = new VisSelectBox<>();
        modelSelector.setItems(MODEL_DISPLAY_NAMES);
        try {
             String saved = app.preferences.getString(PREF_MODEL, "Big Pickle");
             boolean valid = false;
             for (String name : MODEL_DISPLAY_NAMES) {
                 if (name.equals(saved)) { valid = true; break; }
             }
             modelSelector.setSelected(valid ? saved : "Big Pickle");
        } catch(Exception e){}
        
        header.add(modelSelector).width(160).padRight(5);
        
        // New Chat
        VisImageButton newChatBtn = new VisImageButton(drawable("add"));
        newChatBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startNewChat();
            }
        });
        header.add(newChatBtn).size(48, 48).padRight(5);

        // Settings
        VisImageButton settingsBtn = new VisImageButton(drawable("events/properties")); 
        settingsBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showSettingsDialog();
            }
        });
        header.add(settingsBtn).size(48, 48).row();
        
        header.add(new Separator()).growX().colspan(5).padTop(5).padBottom(5);
        
        add(header).growX().top().row();
        
        // --- Chat Area ---
        chatTable = new VisTable();
        chatTable.top().left();
        chatScroll = new VisScrollPane(chatTable);
        chatScroll.setFadeScrollBars(false);
        chatScroll.setScrollingDisabled(true, false);
        
        add(chatScroll).grow().padBottom(10).row();
        
        // --- Input Area ---
        VisTable inputTable = new VisTable();
        
        inputField = new VisTextArea("");
        inputField.setMessageText("Ask the AI to create scripts or items...");
        inputField.setPrefRows(3);
        
        VisScrollPane inputScroll = new VisScrollPane(inputField);
        inputScroll.setScrollingDisabled(true, false);
        
        sendBtn = new VisTextButton("Send");
        sendBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                sendMessage();
            }
        });
        
        inputTable.add(inputScroll).growX().height(80).padRight(10);
        inputTable.add(sendBtn).width(80).height(80);
        
        add(inputTable).growX().bottom();
    }
    
    private Drawable drawable(String name) {
        try {
            return VisUI.getSkin().getDrawable(name);
        } catch (Exception e) {
            try {
                return new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new com.badlogic.gdx.graphics.Texture(Gdx.files.internal("images/" + name + ".png")));
            } catch(Exception ex) {
                return VisUI.getSkin().getDrawable("white"); 
            }
        }
    }
    
    private String getApiKey(String provider) {
        if ("opencode".equals(provider)) {
            String key = app.preferences.getString(PREF_ZEN_API_KEY, "");
            if (key.isEmpty() && BuildConfig.ZEN_API_KEY != null && !BuildConfig.ZEN_API_KEY.isEmpty() && !BuildConfig.ZEN_API_KEY.startsWith("your_")) {
                key = BuildConfig.ZEN_API_KEY;
            }
            return key;
        } else {
            String key = app.preferences.getString(PREF_GEMINI_API_KEY, "");
            if (key.isEmpty() && BuildConfig.GEMINI_API_KEY != null && !BuildConfig.GEMINI_API_KEY.isEmpty() && !BuildConfig.GEMINI_API_KEY.startsWith("your_")) {
                key = BuildConfig.GEMINI_API_KEY;
            }
            return key;
        }
    }

    private void showApiKeyDialog(String provider) {
        String prefKey = provider.equals("opencode") ? PREF_ZEN_API_KEY : PREF_GEMINI_API_KEY;
        String title = provider.equals("opencode") ? "OpenCode Zen API Key" : "Gemini API Key";
        String message = provider.equals("opencode") ? "Enter OpenCode Zen API Key:" : "Enter Gemini API Key:";
        String currentKey = getApiKey(provider);
        new SingleInputDialog(title, message, currentKey, key -> {
            app.preferences.putString(prefKey, key);
            app.preferences.flush();
            app.toast("API Key Saved");
        }).show(app.getUiStage());
    }
    
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        
        String displayName = modelSelector.getSelected();
        ModelEntry entry = getModelEntry(displayName);
        
        String apiKey = getApiKey(entry.provider);
        if (apiKey.isEmpty()) {
            if (entry.provider.equals("opencode")) {
                app.toast("Please set OpenCode Zen API Key first!");
                showApiKeyDialog("opencode");
            } else {
                app.toast("Please set Gemini API Key first!");
                showApiKeyDialog("gemini");
            }
            return;
        }
        
        // User Message
        ChatMessage userMsg = new ChatMessage(text, true);
        currentSession.addMessage(userMsg);
        historyManager.saveSession(currentSession);
        addMessageToUI(userMsg);
        
        inputField.setText("");
        
        // Build Context
        String context = buildContext();
        
        // Save model preference
        app.preferences.putString(PREF_MODEL, displayName);
        app.preferences.flush();
        
        // Send based on provider
        if (entry.provider.equals("opencode")) {
            sendOpenCodeRequest(apiKey, context, text, entry.apiModelId);
        } else {
            sendGeminiRequest(apiKey, context + "\n\nUser Request: " + text, entry.apiModelId);
        }
    }
    
    private void sendOpenCodeRequest(String apiKey, String context, String userText, String modelId) {
        sendBtn.setDisabled(true);
        VisTable statusTable = new VisTable();
        
        VisLabel loadingLabel = new VisLabel("AI is thinking...");
        statusTable.add(loadingLabel).left();
        
        chatTable.add(statusTable).left().pad(5).row();
        final VisTable finalStatusTable = statusTable;
        
        StringBuilder messagesJson = new StringBuilder();
        messagesJson.append("[");
        messagesJson.append("{\"role\":\"system\",\"content\":").append(escapeJson(context)).append("}");
        for (ChatMessage msg : currentSession.messages) {
            messagesJson.append(",");
            String role = msg.isUser ? "user" : "assistant";
            messagesJson.append("{\"role\":\"").append(role).append("\",\"content\":").append(escapeJson(msg.text)).append("}");
        }
        messagesJson.append("]");
        
        String jsonBody = "{"
            + "\"model\": \"" + modelId + "\","
            + "\"messages\": " + messagesJson.toString()
            + "}";
        
        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(ZEN_API_BASE_URL);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Authorization", "Bearer " + apiKey);
        request.setContent(jsonBody);
        request.setTimeOut(120000);
        
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                String result = httpResponse.getResultAsString();
                Gdx.app.postRunnable(() -> {
                    sendBtn.setDisabled(false);
                    finalStatusTable.remove();
                    parseOpenCodeResponse(result);
                });
            }
            
            @Override
            public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> {
                    sendBtn.setDisabled(false);
                    finalStatusTable.remove();
                    addMessageToUI(new ChatMessage("Error: " + t.getMessage(), false));
                });
            }
            
            @Override
            public void cancelled() {
                Gdx.app.postRunnable(() -> {
                    sendBtn.setDisabled(false);
                    finalStatusTable.remove();
                });
            }
        });
    }
    
    private void sendGeminiRequest(String apiKey, String prompt, String modelId) {
        sendBtn.setDisabled(true);
        VisTable statusTable = new VisTable();
        
        VisLabel loadingLabel = new VisLabel("AI is thinking...");
        statusTable.add(loadingLabel).left();
        
        chatTable.add(statusTable).left().pad(5).row();
        final VisTable finalStatusTable = statusTable;
        
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent?key=" + apiKey;
        String jsonBody = "{ \"contents\": [{ \"parts\": [{ \"text\": " + escapeJson(prompt) + " }] }] }";
        
        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(url);
        request.setHeader("Content-Type", "application/json");
        request.setContent(jsonBody);
        request.setTimeOut(120000);
        
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                String result = httpResponse.getResultAsString();
                Gdx.app.postRunnable(() -> {
                    sendBtn.setDisabled(false);
                    finalStatusTable.remove();
                    parseGeminiResponse(result);
                });
            }
            
            @Override
            public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> {
                    sendBtn.setDisabled(false);
                    finalStatusTable.remove();
                    addMessageToUI(new ChatMessage("Error: " + t.getMessage(), false));
                });
            }
            
            @Override
            public void cancelled() {
                Gdx.app.postRunnable(() -> {
                    sendBtn.setDisabled(false);
                    finalStatusTable.remove();
                });
            }
        });
    }
    
    private void parseOpenCodeResponse(String jsonResult) {
        try {
            JsonValue root = new JsonReader().parse(jsonResult);
            if (root.has("error")) {
                String errMsg = root.get("error").getString("message", "Unknown error");
                addMessageToUI(new ChatMessage("API Error: " + errMsg, false));
                return;
            }
            if (root.has("choices") && root.get("choices").size > 0) {
                JsonValue choice = root.get("choices").get(0);
                if (choice.has("message") && choice.get("message").has("content")) {
                    String responseText = choice.get("message").getString("content", "");
                    if (!responseText.isEmpty()) {
                        ChatMessage aiMsg = new ChatMessage(responseText, false);
                        currentSession.addMessage(aiMsg);
                        historyManager.saveSession(currentSession);
                        addMessageToUI(aiMsg);
                        return;
                    }
                }
            }
            addMessageToUI(new ChatMessage("Unexpected response format: " + jsonResult, false));
        } catch (Exception e) {
            addMessageToUI(new ChatMessage("Error: " + e.getMessage(), false));
        }
    }
    
    private void parseGeminiResponse(String jsonResult) {
        try {
            JsonValue root = new JsonReader().parse(jsonResult);
            if (root.has("candidates") && root.get("candidates").size > 0) {
                JsonValue candidate = root.get("candidates").get(0);
                if (candidate.has("content") && candidate.get("content").has("parts")) {
                    String responseText = candidate.get("content").get("parts").get(0).getString("text");
                    
                    ChatMessage aiMsg = new ChatMessage(responseText, false);
                    currentSession.addMessage(aiMsg);
                    historyManager.saveSession(currentSession);
                    
                    addMessageToUI(aiMsg);
                }
            } else {
                 addMessageToUI(new ChatMessage("Error Parsing Response or Blocked: " + jsonResult, false));
            }
        } catch (Exception e) {
             addMessageToUI(new ChatMessage("Error: " + e.getMessage(), false));
        }
    }
    
    private void createCodeBlock(VisTable table, String codeBlock) {
        // Extract language if present
        String language = "java";
        String code = codeBlock;
        int firstNewLine = codeBlock.indexOf('\n');
        if (firstNewLine > 0) {
            language = codeBlock.substring(0, firstNewLine).trim();
            if (firstNewLine + 1 < codeBlock.length())
                code = codeBlock.substring(firstNewLine + 1);
        }
        
        final String finalCode = code;
        final String finalLang = language;
        
        VisTable codeTable = new VisTable();
        codeTable.setBackground(drawable("window-bg")); 
        codeTable.pad(5);
        
        VisLabel label = new VisLabel("Code Snippet (" + language + ")");
        codeTable.add(label).expandX().left();
        
        VisTextButton viewBtn = new VisTextButton("Show Script");
        viewBtn.addListener(new ClickListener() {
             @Override
             public void clicked(InputEvent event, float x, float y) {
                 event.stop(); // Prevent context menu from popping up
                 showCodeDialog(finalCode, finalLang);
             }
        });
        
        codeTable.add(viewBtn).right();
        
        table.add(codeTable).growX().padTop(5).padBottom(5).row();
    }

    private void showCodeDialog(final String code, final String lang) {
        final VisDialog dialog = new VisDialog("Generated Script");
        dialog.setResizable(true);
        dialog.setMovable(true);
        dialog.addCloseButton();
        
        VisTextArea textArea = new VisTextArea(code);
        // Ensure standard font
        textArea.setStyle(new VisTextField.VisTextFieldStyle(textArea.getStyle()));
        try {
            textArea.getStyle().font = VisUI.getSkin().getFont("small-font");
        } catch(Exception e){}
        
        // Dynamically set rows based on code length, with a minimum of 10 and max of 30 (or just let it grow)
        int lines = code.split("\n").length;
        textArea.setPrefRows(Math.min( lines + 2, 40)); // Cap it reasonable to avoid massive dialogs
        VisScrollPane scrollPane = new VisScrollPane(textArea);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(false, false); // Allow both
        
        dialog.getContentTable().add(scrollPane).grow().width(700).height(500);
        
        VisTextButton applyBtn = new VisTextButton("Apply Script");
        applyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                applyCode(code, lang);
                dialog.fadeOut();
            }
        });
        
        VisTextButton copyBtn = new VisTextButton("Copy to Clipboard");
        copyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.getClipboard().setContents(code);
                app.toast("Copied to clipboard");
            }
        });
        
        dialog.getButtonsTable().add(applyBtn).pad(5);
        dialog.getButtonsTable().add(copyBtn).pad(5);
        
        dialog.show(app.getUiStage());
        dialog.setSize(700, 600);
        dialog.centerWindow();
    }
    
    private void applyCode(String code, String lang) {
        String scene = app.getEditor().getScene();
        String projectPath = app.getEditor().getProject().getPath();
        
        // Try to identify class name
        String className = "";
        try {
            int classIndex = code.indexOf("class ");
            if (classIndex != -1) {
                int end = code.indexOf(" ", classIndex + 6);
                int brace = code.indexOf("{", classIndex + 6);
                int open = (end != -1 && end < brace) ? end : brace;
                if(open != -1)
                    className = code.substring(classIndex + 6, open).trim();
            }
        } catch(Exception e) {}
        
        if (className.isEmpty()) {
            app.toast("Could not find class name in script.");
            return;
        }

        FileHandle file = null;
        
        if (code.contains("extends SceneScript")) {
            // Scene Script Path: project/java/com/star4droid/Game/SceneScript/{sceneName}Script.java
            String path = projectPath + "/java/com/star4droid/Game/SceneScript/" + scene + "Script.java";
            file = Gdx.files.absolute(path);
        } else {
             // Item Script
             // User path: project/java/com/star4droid/Game/Scripts/scene/{itemName}Script.java
             // We use 'scene' generic folder or actual scene name? 
             // "project/java/com/star4droid/Game/Scripts/scene/{itemName}Script.java" -> suggesting 'scene' might be literal or variable?
             // Based on context, it usually organizes by scene name. I will use the scene name directory.
             
             String path = projectPath + "/java/com/star4droid/Game/Scripts/" + scene + "/" + className + ".java";
             file = Gdx.files.absolute(path);
        }
        
        if (file != null) {
            file.writeString(code, false);
            app.toast("Applied to: " + file.name());
        } else {
            app.toast("Could not determine file path.");
        }
    }

    private String buildContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert Game Dev AI for the Star2D Engine (LibGDX based). API RULES:\n");
        sb.append("1. ALWAYS generate JAVA code using LibGDX and Star2D APIs.\n");
        sb.append("2. DO NOT use JavaScript, Python, or others.\n");
        sb.append("3. For Items, extend 'ItemScript' (com.star4droid.template.Utils.ItemScript).\n");
        sb.append("   - PUBLIC FIELDS AVAILABLE (DO NOT REDEFINE): Body body, Actor actor, StageImp stage, PlayerItem playerItem.\n");
        sb.append("   - Example: usage 'body.setLinearVelocity(...)' directly. DO NOT use 'getBody()' inside onBodyUpdate (use the field).\n"); 
        sb.append("   - MUST OVERRIDE: public void onBodyUpdate()\n");
        sb.append("   - Optional: onTouchStart(InputEvent), onTouchEnd(InputEvent), onCollisionBegin(PlayerItem), onCollisionEnd(PlayerItem), onClick(), onBodyCreated().\n");
        sb.append("4. For Scene logic, extend 'SceneScript' (com.star4droid.template.Utils.SceneScript).\n");
        sb.append("   - Abstract Methods: create(), draw(), pause(), resume().\n");
        sb.append("5. Class Name Format: [ItemName]Script or [SceneName]Script.\n");
        sb.append("6. Package: com.star4droid.Game.Scripts.[SceneName] (for items) or com.star4droid.Game.SceneScript (for scene).\n");
        sb.append("7. IMPORTS: Use 'com.badlogic.gdx.*'. use 'com.star4droid.template.Nodes.*' if needed. \n");
        sb.append("   - DO NOT import 'android.*'.\n");
        sb.append("   - DO NOT import 'java.awt.*'.\n");
        sb.append("   - ENSURE you import 'com.star4droid.template.Utils.ItemScript'.\n");
        sb.append("   - ENSURE you import 'com.star4droid.template.Utils.PlayerItem'.\n");
        sb.append("   - ITEM TYPES: 'BoxBody', 'CircleItem', 'TextItem', 'CustomBody', 'Joystick', 'MapItem', 'ParticleItem', 'ProgressItem', 'CameraItem' are in 'com.star4droid.template.Items'. IMPORT THEM: `import com.star4droid.template.Items.*;`.\n");
        sb.append("   - If generating for MULTIPLE items, create separate code blocks for EACH item script.\n");
        sb.append("8. StageImp API (`stage` field, type: `com.star4droid.template.Items.StageImp`) to control the game:\n");
        sb.append("   - `findItem(String name)` -> returns PlayerItem (Actor). Use this to find other items.\n");
        sb.append("   - `findLight(String name)` -> returns box2dLight.Light.\n");
        sb.append("   - `checkCollision(PlayerItem p1, PlayerItem p2)` -> boolean.\n");
        sb.append("   - `cameraFollowX(PlayerItem)`, `cameraFollowY(PlayerItem)`.\n");
        sb.append("   - `setImage(PlayerItem, String imageName)`.\n");
        sb.append("   - `openUrl(String url)`.\n");
        sb.append("   - `finish()` -> Close the stage/game.\n");
        sb.append("   - `getGameStage()`, `getUiStage()` -> LibGDX Stages.\n");
        sb.append("   - `spawn(String prefabName)` -> NOT DIRECTLY AVAILABLE. create actors manually if needed or use hidden methods if known.\n");
        sb.append("9. ItemScript Input/Events (MUST use EXACT signatures):\n");
        sb.append("   - `public void onClick()`\n");
        sb.append("   - `public void onTouchStart(InputEvent event)`\n");
        sb.append("   - `public void onTouchEnd(InputEvent event)`\n");
        sb.append("   - `public void onCollisionBegin(PlayerItem other)`\n");
        sb.append("   - `public void onCollisionEnd(PlayerItem other)`\n");
        sb.append("   - `public void onBodyUpdate()`\n");
        sb.append("   - `public void onBodyCreated()`\n");
        sb.append("10. available assets are in `images/` directory, used via `setImage(\"name.png\")`.\n");
        
        sb.append("\nCurrent Scene: ").append(app.getEditor().getScene()).append("\n");
        
        // Items
        sb.append("Scene Items (Actors):\n");
        for (Actor actor : app.getEditor().getActors()) {
            sb.append(" - Name: ").append(actor.getName())
              .append(" (Type: ").append(actor.getClass().getSimpleName()).append(")\n");
        }
        
        // Assets
        sb.append("Available Images:\n");
        try {
            FileHandle imgs = Gdx.files.absolute(app.getEditor().getProject().getImagesPath());
            if (imgs.exists()) {
                for (FileHandle img : imgs.list()) {
                    sb.append(" - ").append(img.name()).append("\n");
                }
            }
        } catch(Exception e){}
        
        return sb.toString();
    }
    
    private String escapeJson(String raw) {
        return "\"" + raw.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\b", "\\b")
                        .replace("\f", "\\f")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t") + "\"";
    }
    
    private void startNewChat() {
        if (currentSession != null && !currentSession.messages.isEmpty()) {
            historyManager.saveSession(currentSession);
        }
        currentSession = historyManager.createNewSession();
        chatTable.clear();
    }

    private void showHistoryDialog() {
        final VisDialog dialog = new VisDialog("Chat History");
        dialog.addCloseButton();
        dialog.setResizable(true);
        
        VisTable listTable = new VisTable();
        listTable.top();
        
        for (final ChatSession session : historyManager.getSessions()) {
            VisTable row = new VisTable();
            row.setBackground(drawable("window-bg"));
            
            String title = (session.title == null || session.title.isEmpty()) ? "Untitled Chat" : session.title;
            VisTextButton loadBtn = new VisTextButton(title);
            loadBtn.align(Align.left);
            loadBtn.getLabelCell().expandX();
            loadBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    loadSession(session);
                    dialog.fadeOut();
                }
            });
            
            VisImageButton deleteBtn = new VisImageButton(drawable("delete"));
            deleteBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    historyManager.deleteSession(session);
                    dialog.fadeOut();
                    showHistoryDialog();
                }
            });
            
            row.add(loadBtn).growX().pad(2);
            row.add(deleteBtn).size(30).pad(2);
            listTable.add(row).growX().padBottom(2).row();
        }
        
        VisScrollPane scroll = new VisScrollPane(listTable);
        dialog.getContentTable().add(scroll).grow().width(400).height(500);
        dialog.show(app.getUiStage());
        dialog.centerWindow();
    }
    
    private void loadSession(ChatSession session) {
        currentSession = session;
        chatTable.clear();
        for (ChatMessage msg : session.messages) {
            restoreMessageUI(msg);
        }
        Gdx.app.postRunnable(() -> chatScroll.setScrollPercentY(100));
    }

    private void showSettingsDialog() {
        final VisDialog dialog = new VisDialog("Settings");
        dialog.addCloseButton();
        
        Table content = dialog.getContentTable();
        content.pad(20);
        
        // Gemini API Key
        content.add(new VisLabel("Gemini API Key:")).left();
        final VisTextField geminiKeyField = new VisTextField(getApiKey("gemini"));
        content.add(geminiKeyField).width(250).row();
        
        // OpenCode Zen API Key
        content.add(new VisLabel("OpenCode Zen API Key:")).left();
        final VisTextField zenKeyField = new VisTextField(getApiKey("opencode"));
        content.add(zenKeyField).width(250).row();
        
        // Read Files
        final VisCheckBox readCheck = new VisCheckBox("Read relevant files for context");
        readCheck.setChecked(app.preferences.getBoolean(PREF_READ_FILES, true));
        content.add(readCheck).colspan(2).left().padTop(10).row();
        
        VisTextButton saveBtn = new VisTextButton("Save");
        saveBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                app.preferences.putString(PREF_GEMINI_API_KEY, geminiKeyField.getText());
                app.preferences.putString(PREF_ZEN_API_KEY, zenKeyField.getText());
                app.preferences.putBoolean(PREF_READ_FILES, readCheck.isChecked());
                app.preferences.flush();
                dialog.fadeOut();
                app.toast("Settings Saved");
            }
        });
        
        dialog.getButtonsTable().add(saveBtn).pad(10);
        dialog.show(app.getUiStage());
        dialog.centerWindow();
    }
    
    private void restoreMessageUI(ChatMessage msg) {
        addMessageToUI(msg);
    }

    private void addMessageToUI(final ChatMessage msg) {
        String text = msg.text;
        boolean isUser = msg.isUser;
        VisTable msgTable = new VisTable();
        msgTable.setBackground(drawable(isUser ? "button-blue" : "field-color")); 
        msgTable.pad(10);
        
        // Context Menu Listener
        msgTable.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                 showMessageMenu(msg);
            }
        });
        
        if (!isUser) {
            String[] parts = text.split("```");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i % 2 == 0) {
                    if (!part.trim().isEmpty()) {
                        VisLabel label = new VisLabel(part);
                        label.setWrap(true);
                        msgTable.add(label).growX().row();
                    }
                } else {
                    createCodeBlock(msgTable, part);
                }
            }
        } else {
             VisLabel label = new VisLabel(text);
             label.setWrap(true);
             msgTable.add(label).growX();
        }
        
        chatTable.add(msgTable).growX().padBottom(5).padLeft(isUser ? 50 : 0).padRight(isUser ? 0 : 50).row();
        chatScroll.layout();
        Gdx.app.postRunnable(() -> chatScroll.setScrollPercentY(100)); 
    }
    
    private void showMessageMenu(final ChatMessage msg) {
        final VisDialog dialog = new VisDialog("Message Options");
        dialog.addCloseButton();
        dialog.setResizable(false);
        
        VisTextButton copyBtn = new VisTextButton("Copy");
        copyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.getClipboard().setContents(msg.text);
                app.toast("Copied!");
                dialog.fadeOut();
            }
        });
        
        VisTextButton editBtn = new VisTextButton("Edit");
        editBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.fadeOut();
                showEditMessageDialog(msg);
            }
        });
        
        VisTextButton deleteBtn = new VisTextButton("Delete");
        deleteBtn.setColor(Color.RED);
        deleteBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                currentSession.messages.remove(msg);
                historyManager.saveSession(currentSession);
                reloadChatUI(); // Reload to reflect deletion
                dialog.fadeOut();
                app.toast("Message Deleted");
            }
        });
        
        Table t = dialog.getContentTable();
        t.pad(10);
        t.add(copyBtn).growX().pad(5).row();
        t.add(editBtn).growX().pad(5).row();
        t.add(deleteBtn).growX().pad(5).row();
        
        dialog.show(app.getUiStage());
        dialog.centerWindow();
    }
    
    private void showEditMessageDialog(final ChatMessage msg) {
        final VisDialog dialog = new VisDialog("Edit Message");
        dialog.addCloseButton();
        dialog.setResizable(true);
        
        final VisTextArea textArea = new VisTextArea(msg.text);
        textArea.setPrefRows(10);
        
        VisScrollPane scroll = new VisScrollPane(textArea);
        dialog.getContentTable().add(scroll).grow().width(500).height(300);
        
        VisTextButton saveBtn = new VisTextButton("Save");
        saveBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                msg.text = textArea.getText();
                historyManager.saveSession(currentSession);
                reloadChatUI();
                dialog.fadeOut();
                app.toast("Message Updated");
            }
        });
        
        dialog.getButtonsTable().add(saveBtn).pad(10);
        dialog.show(app.getUiStage());
        dialog.centerWindow();
    }

    private void reloadChatUI() {
        chatTable.clear();
        for(ChatMessage m : currentSession.messages) {
            addMessageToUI(m);
        }
        Gdx.app.postRunnable(() -> chatScroll.setScrollPercentY(100));
    }
    
    private ModelEntry getModelEntry(String displayName) {
        for (ModelEntry m : MODELS) {
            if (m.displayName.equals(displayName)) return m;
        }
        return MODELS[0];
    }
    
    private void promptForApiKeyIfNeeded() {
        String displayName = modelSelector.getSelected();
        ModelEntry entry = getModelEntry(displayName);
        String key = getApiKey(entry.provider);
        if (key.isEmpty()) {
            showApiKeyDialog(entry.provider);
        }
    }
}
