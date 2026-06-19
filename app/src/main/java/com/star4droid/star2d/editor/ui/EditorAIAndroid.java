package com.star4droid.star2d.editor.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.evo.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class EditorAIAndroid extends FrameLayout {

    private static final String PREF_GEMINI_API_KEY = "gemini_api_key_android";
    private static final String PREF_ZEN_API_KEY = "zen_api_key_android";
    private static final String PREF_MODEL = "gemini_model_android";
    private static final String ZEN_API_BASE_URL = "https://opencode.ai/zen/v1/chat/completions";

    private static int loadingViewId = -1;

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

    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText inputField;
    private Spinner modelSpinner;
    private View resizeHandle, dragHandle;
    private int panelHeight;
    private boolean isResizing = false;
    private float startY, startHeight;

    private List<ChatMessage> messages = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean visible = false;

    public EditorAIAndroid(Context context) {
        super(context);
        init(context);
    }

    public EditorAIAndroid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EditorAIAndroid(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        inflate(context, R.layout.editor_ai_panel, this);

        chatContainer = findViewById(R.id.chat_container);
        chatScroll = findViewById(R.id.chat_scroll);
        inputField = findViewById(R.id.input_field);
        modelSpinner = findViewById(R.id.model_spinner);
        resizeHandle = findViewById(R.id.resize_handle);
        dragHandle = findViewById(R.id.drag_handle);

        setupModelSpinner();
        setupListeners();

        setVisibility(GONE);
    }

    private void setupModelSpinner() {
        String[] displayNames = new String[MODELS.length];
        for (int i = 0; i < MODELS.length; i++) {
            displayNames[i] = MODELS[i].displayName;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(adapter);

        SharedPreferences prefs = getPrefs();
        String saved = prefs.getString(PREF_MODEL, "Big Pickle");
        for (int i = 0; i < displayNames.length; i++) {
            if (displayNames[i].equals(saved)) {
                modelSpinner.setSelection(i);
                break;
            }
        }
    }

    private void setupListeners() {
        findViewById(R.id.send_btn).setOnClickListener(v -> sendMessage());
        findViewById(R.id.close_btn).setOnClickListener(v -> hide());
        findViewById(R.id.new_chat_btn).setOnClickListener(v -> startNewChat());
        findViewById(R.id.history_btn).setOnClickListener(v -> showHistoryDialog());
        findViewById(R.id.settings_btn).setOnClickListener(v -> showSettingsDialog());

        inputField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        setupResizeHandle();
        setupDragHandle();
    }

    private void setupResizeHandle() {
        resizeHandle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isResizing = true;
                    startY = event.getRawY();
                    startHeight = getHeight();
                    v.setBackgroundColor(0xFF2D3A4A);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isResizing) {
                        float delta = event.getRawY() - startY;
                        int newHeight = (int) (startHeight - delta);
                        int maxHeight = ((View) getParent()).getHeight();
                        int minHeight = Math.round(144f * getResources().getDisplayMetrics().density);
                        newHeight = Math.max(minHeight, Math.min(newHeight, maxHeight));
                        ViewGroup.LayoutParams lp = getLayoutParams();
                        lp.height = newHeight;
                        setLayoutParams(lp);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isResizing = false;
                    v.setBackgroundColor(0xFF1A2332);
                    return true;
            }
            return false;
        });
    }

    private void setupDragHandle() {
        dragHandle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getRawY();
                    startHeight = getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float delta = event.getRawY() - startY;
                    setY(Math.max(0, startHeight + delta));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return false;
        });
    }

    private SharedPreferences getPrefs() {
        return getContext().getSharedPreferences("EditorAIPrefs", Context.MODE_PRIVATE);
    }

    private File getHistoryFile() {
        try {
            String path = Editor.getCurrentEditor().getProject().getPath();
            return new File(path + "/ai_history.json");
        } catch (Exception e) {
            return new File(getContext().getFilesDir(), "ai_history.json");
        }
    }

    public void toggle() {
        if (visible) hide();
        else show();
    }

    public void show() {
        visible = true;
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(250).start();
        inputField.requestFocus();
    }

    public void hide() {
        visible = false;
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            setVisibility(GONE);
            setY(0);
            ViewGroup.LayoutParams lp = getLayoutParams();
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            setLayoutParams(lp);
        }).start();
    }

    private void sendMessage() {
        String text = inputField.getText().toString().trim();
        if (text.isEmpty()) return;

        ModelEntry entry = getSelectedModel();
        SharedPreferences prefs = getPrefs();
        String apiKey = "";
        if (entry.provider.equals("opencode")) {
            apiKey = prefs.getString(PREF_ZEN_API_KEY, "");
            if (apiKey.isEmpty()) {
                Toast.makeText(getContext(), "Please set OpenCode Zen API Key first!", Toast.LENGTH_SHORT).show();
                showApiKeyDialog("opencode");
                return;
            }
        } else {
            apiKey = prefs.getString(PREF_GEMINI_API_KEY, "");
            if (apiKey.isEmpty()) {
                Toast.makeText(getContext(), "Please set Gemini API Key first!", Toast.LENGTH_SHORT).show();
                showApiKeyDialog("gemini");
                return;
            }
        }

        ChatMessage userMsg = new ChatMessage(text, true);
        messages.add(userMsg);
        saveHistory();
        addMessageToUI(userMsg);
        inputField.setText("");

        prefs.edit().putString(PREF_MODEL, entry.displayName).apply();

        addLoadingIndicator();

        if (entry.provider.equals("opencode")) {
            sendOpenCodeRequest(apiKey, text, entry.apiModelId);
        } else {
            sendGeminiRequest(apiKey, buildContext() + "\n\nUser Request: " + text, entry.apiModelId);
        }
    }

    private void sendOpenCodeRequest(String apiKey, String userText, String modelId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONArray messagesJson = new JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", buildContext());
                messagesJson.put(systemMsg);

                for (ChatMessage msg : messages) {
                    JSONObject m = new JSONObject();
                    m.put("role", msg.isUser ? "user" : "assistant");
                    m.put("content", msg.text);
                    messagesJson.put(m);
                }

                JSONObject body = new JSONObject();
                body.put("model", modelId);
                body.put("messages", messagesJson);

                String result = httpPost(ZEN_API_BASE_URL,
                        "Authorization: Bearer " + apiKey,
                        body.toString());

                mainHandler.post(() -> {
                    removeLoadingIndicator();
                    parseOpenCodeResponse(result);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    removeLoadingIndicator();
                    addMessageToUI(new ChatMessage("Error: " + e.getMessage(), false));
                });
            }
        });
    }

    private void sendGeminiRequest(String apiKey, String prompt, String modelId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + modelId + ":generateContent?key=" + apiKey;

                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt);
                JSONArray parts = new JSONArray();
                parts.put(textPart);
                JSONObject content = new JSONObject();
                content.put("parts", parts);
                JSONObject body = new JSONObject();
                body.put("contents", new JSONArray().put(content));

                String result = httpPost(url, null, body.toString());

                mainHandler.post(() -> {
                    removeLoadingIndicator();
                    parseGeminiResponse(result);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    removeLoadingIndicator();
                    addMessageToUI(new ChatMessage("Error: " + e.getMessage(), false));
                });
            }
        });
    }

    private String httpPost(String urlStr, String authHeader, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (authHeader != null && !authHeader.isEmpty()) {
            String[] parts = authHeader.split(": ", 2);
            if (parts.length == 2) {
                conn.setRequestProperty(parts[0], parts[1]);
            }
        }
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        OutputStream os = conn.getOutputStream();
        os.write(jsonBody.getBytes("UTF-8"));
        os.close();

        int responseCode = conn.getResponseCode();
        BufferedReader reader;
        if (responseCode >= 200 && responseCode < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    private void parseOpenCodeResponse(String jsonResult) {
        try {
            JSONObject root = new JSONObject(jsonResult);
            if (root.has("error")) {
                String errMsg = root.getJSONObject("error").optString("message", "Unknown error");
                addMessageToUI(new ChatMessage("API Error: " + errMsg, false));
                return;
            }
            if (root.has("choices") && root.getJSONArray("choices").length() > 0) {
                JSONObject choice = root.getJSONArray("choices").getJSONObject(0);
                if (choice.has("message") && choice.getJSONObject("message").has("content")) {
                    String responseText = choice.getJSONObject("message").getString("content");
                    if (!responseText.isEmpty()) {
                        ChatMessage aiMsg = new ChatMessage(responseText, false);
                        messages.add(aiMsg);
                        saveHistory();
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
            JSONObject root = new JSONObject(jsonResult);
            if (root.has("candidates") && root.getJSONArray("candidates").length() > 0) {
                JSONObject candidate = root.getJSONArray("candidates").getJSONObject(0);
                if (candidate.has("content") && candidate.getJSONObject("content").has("parts")) {
                    String responseText = candidate.getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0).getString("text");
                    ChatMessage aiMsg = new ChatMessage(responseText, false);
                    messages.add(aiMsg);
                    saveHistory();
                    addMessageToUI(aiMsg);
                }
            } else {
                addMessageToUI(new ChatMessage("Error Parsing Response: " + jsonResult, false));
            }
        } catch (Exception e) {
            addMessageToUI(new ChatMessage("Error: " + e.getMessage(), false));
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
        sb.append("9. ItemScript Input/Events (MUST use EXACT signatures):\n");
        sb.append("   - `public void onClick()`\n");
        sb.append("   - `public void onTouchStart(InputEvent event)`\n");
        sb.append("   - `public void onTouchEnd(InputEvent event)`\n");
        sb.append("   - `public void onCollisionBegin(PlayerItem other)`\n");
        sb.append("   - `public void onCollisionEnd(PlayerItem other)`\n");
        sb.append("   - `public void onBodyUpdate()`\n");
        sb.append("   - `public void onBodyCreated()`\n");
        sb.append("10. available assets are in `images/` directory, used via `setImage(\"name.png\")`.\n");
        sb.append("\nCurrent Scene: ").append(getCurrentScene()).append("\n");
        sb.append("Available assets and scene items will be included at runtime.\n");
        return sb.toString();
    }

    private String getCurrentScene() {
        try {
            return Editor.getCurrentEditor().getScene();
        } catch (Exception e) {
            return "scene1";
        }
    }

    private void addLoadingIndicator() {
        TextView loading = new TextView(getContext());
        loading.setText("AI is thinking...");
        loading.setTextColor(0xFFFFFFFF);
        loading.setPadding(16, 12, 16, 12);
        loading.setBackgroundResource(R.drawable.field_background);
        loading.setGravity(Gravity.CENTER);
        if (loadingViewId == -1) loadingViewId = View.generateViewId();
        loading.setId(loadingViewId);
        chatContainer.addView(loading);
        scrollToBottom();
    }

    private void removeLoadingIndicator() {
        if (loadingViewId != -1) {
            View loading = chatContainer.findViewById(loadingViewId);
            if (loading != null) chatContainer.removeView(loading);
        }
    }

    private void addMessageToUI(ChatMessage msg) {
        LinearLayout msgLayout = new LinearLayout(getContext());
        msgLayout.setOrientation(LinearLayout.VERTICAL);
        msgLayout.setPadding(12, 10, 12, 10);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(msg.isUser ? 48 : 0, 4, msg.isUser ? 0 : 48, 4);
        msgLayout.setLayoutParams(params);

        if (msg.isUser) {
            msgLayout.setBackgroundResource(R.drawable.button_gradient);
        } else {
            msgLayout.setBackgroundResource(R.drawable.field_background);
        }

        if (!msg.isUser && msg.text.contains("```")) {
            String[] parts = msg.text.split("```");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i % 2 == 0) {
                    if (!part.trim().isEmpty()) {
                        addTextPart(msgLayout, part);
                    }
                } else {
                    addCodeBlock(msgLayout, part);
                }
            }
        } else {
            addTextPart(msgLayout, msg.text);
        }

        chatContainer.addView(msgLayout);
        scrollToBottom();
    }

    private void addTextPart(LinearLayout parent, String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(14);
        tv.setLineSpacing(4f, 1f);
        tv.setPadding(4, 4, 4, 4);
        parent.addView(tv);
    }

    private void addCodeBlock(LinearLayout parent, String codeBlock) {
        String language = "java";
        String code = codeBlock;
        int firstNewLine = codeBlock.indexOf('\n');
        if (firstNewLine > 0) {
            language = codeBlock.substring(0, firstNewLine).trim();
            if (firstNewLine + 1 < codeBlock.length())
                code = codeBlock.substring(firstNewLine + 1);
        }

        String finalCode = code;
        String finalLang = language;

        View codeView = inflate(getContext(), R.layout.code_block_view, null);
        TextView langLabel = codeView.findViewById(R.id.code_lang_label);
        langLabel.setText("Code Snippet (" + language + ")");

        codeView.findViewById(R.id.show_script_btn).setOnClickListener(v ->
                showCodeDialog(finalCode, finalLang));

        parent.addView(codeView);
    }

    private void showCodeDialog(String code, String lang) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Generated Script (" + lang + ")");

        EditText editText = new EditText(getContext());
        editText.setText(code);
        editText.setTextSize(12);
        editText.setTypeface(android.graphics.Typeface.MONOSPACE);
        editText.setBackgroundResource(R.drawable.field_background);
        editText.setTextColor(0xFFFFFFFF);
        editText.setPadding(16, 16, 16, 16);
        int lines = code.split("\n").length;
        editText.setMinLines(Math.min(lines + 2, 30));
        editText.setMaxLines(Math.min(lines + 2, 40));

        builder.setView(editText, 24, 16, 24, 16);
        builder.setPositiveButton("Apply Script", (dialog, which) -> {
            applyCode(editText.getText().toString(), lang);
        });
        builder.setNegativeButton("Copy", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("code", code);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void applyCode(String code, String lang) {
        try {
            Editor editor = Editor.getCurrentEditor();
            String scene = editor.getScene();
            String projectPath = editor.getProject().getPath();

            String className = "";
            int classIndex = code.indexOf("class ");
            if (classIndex != -1) {
                int end = code.indexOf(" ", classIndex + 6);
                int brace = code.indexOf("{", classIndex + 6);
                int open = (end != -1 && end < brace) ? end : brace;
                if (open != -1)
                    className = code.substring(classIndex + 6, open).trim();
            }

            if (className.isEmpty()) {
                Toast.makeText(getContext(), "Could not find class name in script.", Toast.LENGTH_SHORT).show();
                return;
            }

            File file;
            if (code.contains("extends SceneScript")) {
                file = new File(projectPath + "/java/com/star4droid/Game/SceneScript/" + scene + "Script.java");
            } else {
                String dirPath = projectPath + "/java/com/star4droid/Game/Scripts/" + scene + "/";
                new File(dirPath).mkdirs();
                file = new File(dirPath + className + ".java");
            }

            FileWriter writer = new FileWriter(file);
            writer.write(code);
            writer.close();
            Toast.makeText(getContext(), "Applied to: " + file.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollToBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void startNewChat() {
        if (!messages.isEmpty()) saveHistory();
        messages.clear();
        chatContainer.removeAllViews();
    }

    private void showHistoryDialog() {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Chat History");

        List<ChatHistoryItem> history = loadHistory();
        if (history.isEmpty()) {
            builder.setMessage("No chat history.");
            builder.setPositiveButton("OK", null);
            builder.show();
            return;
        }

        String[] titles = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            String t = history.get(i).title;
            titles[i] = t.length() > 40 ? t.substring(0, 40) + "..." : t;
        }

        builder.setItems(titles, (dialog, which) -> {
            loadChatSession(history.get(which));
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showSettingsDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Settings");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        TextView geminiLabel = new TextView(getContext());
        geminiLabel.setText("Gemini API Key:");
        geminiLabel.setTextColor(0xFFFFFFFF);
        layout.addView(geminiLabel);

        EditText geminiField = new EditText(getContext());
        geminiField.setText(getPrefs().getString(PREF_GEMINI_API_KEY, ""));
        geminiField.setHint("Enter Gemini API Key");
        geminiField.setBackgroundResource(R.drawable.field_background);
        geminiField.setTextColor(0xFFFFFFFF);
        geminiField.setHintTextColor(0x80FFFFFF);
        layout.addView(geminiField);

        TextView zenLabel = new TextView(getContext());
        zenLabel.setText("OpenCode Zen API Key:");
        zenLabel.setTextColor(0xFFFFFFFF);
        zenLabel.setPadding(0, 16, 0, 0);
        layout.addView(zenLabel);

        EditText zenField = new EditText(getContext());
        zenField.setText(getPrefs().getString(PREF_ZEN_API_KEY, ""));
        zenField.setHint("Enter OpenCode Zen API Key");
        zenField.setBackgroundResource(R.drawable.field_background);
        zenField.setTextColor(0xFFFFFFFF);
        zenField.setHintTextColor(0x80FFFFFF);
        layout.addView(zenField);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            getPrefs().edit()
                    .putString(PREF_GEMINI_API_KEY, geminiField.getText().toString())
                    .putString(PREF_ZEN_API_KEY, zenField.getText().toString())
                    .apply();
            Toast.makeText(getContext(), "Settings Saved", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showApiKeyDialog(String provider) {
        String title = provider.equals("opencode") ? "OpenCode Zen API Key" : "Gemini API Key";
        String prefKey = provider.equals("opencode") ? PREF_ZEN_API_KEY : PREF_GEMINI_API_KEY;

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        EditText field = new EditText(getContext());
        field.setText(getPrefs().getString(prefKey, ""));
        field.setHint("Enter API Key");
        field.setBackgroundResource(R.drawable.field_background);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0x80FFFFFF);
        layout.addView(field);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            getPrefs().edit().putString(prefKey, field.getText().toString()).apply();
            Toast.makeText(getContext(), "API Key Saved", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private ModelEntry getSelectedModel() {
        int pos = modelSpinner.getSelectedItemPosition();
        if (pos >= 0 && pos < MODELS.length) return MODELS[pos];
        return MODELS[0];
    }

    // --- History Management ---

    private static class ChatMessage {
        String text;
        boolean isUser;

        ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("text", text);
            obj.put("isUser", isUser);
            return obj;
        }

        static ChatMessage fromJson(JSONObject obj) throws Exception {
            return new ChatMessage(obj.getString("text"), obj.getBoolean("isUser"));
        }
    }

    private static class ChatHistoryItem {
        String id;
        String title;
        List<ChatMessage> messages = new ArrayList<>();
    }

    private void saveHistory() {
        try {
            JSONArray arr = new JSONArray();
            JSONObject entry = new JSONObject();
            entry.put("id", String.valueOf(System.currentTimeMillis()));
            String title = "New Chat";
            if (!messages.isEmpty()) {
                title = messages.get(0).text;
                if (title.length() > 20) title = title.substring(0, 20) + "...";
            }
            entry.put("title", title);
            JSONArray msgs = new JSONArray();
            for (ChatMessage m : messages) {
                msgs.put(m.toJson());
            }
            entry.put("messages", msgs);
            arr.put(entry);

            FileWriter writer = new FileWriter(getHistoryFile(), false);
            writer.write(arr.toString(2));
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<ChatHistoryItem> loadHistory() {
        List<ChatHistoryItem> list = new ArrayList<>();
        try {
            File file = getHistoryFile();
            if (!file.exists()) return list;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                ChatHistoryItem item = new ChatHistoryItem();
                item.id = obj.getString("id");
                item.title = obj.optString("title", "New Chat");
                JSONArray msgs = obj.getJSONArray("messages");
                for (int j = 0; j < msgs.length(); j++) {
                    item.messages.add(ChatMessage.fromJson(msgs.getJSONObject(j)));
                }
                list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void loadChatSession(ChatHistoryItem session) {
        messages.clear();
        chatContainer.removeAllViews();
        for (ChatMessage msg : session.messages) {
            messages.add(msg);
            addMessageToUI(msg);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return visible;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return visible;
    }

    public boolean isPanelVisible() {
        return visible;
    }
}
