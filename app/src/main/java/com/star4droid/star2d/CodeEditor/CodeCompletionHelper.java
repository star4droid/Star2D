package com.star4droid.star2d.CodeEditor;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.editor.items.EditorItem;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.completion.CompletionParameters;
import com.tyron.completion.java.JavaCompletionProvider;
import com.tyron.completion.model.CompletionItem;
import com.tyron.completion.model.CompletionList;
import com.tyron.completion.model.DrawableKind;
import io.github.rosemoe.sora.event.EventReceiver;
import io.github.rosemoe.sora.event.Unsubscribe;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.completion.Comparators;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.completion.Filters;
import io.github.rosemoe.sora.lang.completion.FuzzyScore;
import io.github.rosemoe.sora.lang.completion.FuzzyScoreOptions;
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.util.MyCharacter;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.net.URI;
import java.nio.file.Path;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.Locale;

public class CodeCompletionHelper implements EventReceiver<SelectionChangeEvent> {

    final ArrayList<io.github.rosemoe.sora.lang.completion.CompletionItem> items = new ArrayList<>();

    private static class KeywordsHolder {

        String type;
        ArrayList<String> keywords;

        public KeywordsHolder(String type, ArrayList<String> keywords) {
            this.type = type;
            this.keywords = keywords;
        }
    }

    final ArrayList<KeywordsHolder> keywords = new ArrayList<>();
    final HashMap<String, Drawable> drawablesMap = new HashMap<>();
    public boolean proAutoCompletion = true;
    private final File file;
    private JavaCompletionProvider completionProvider;
    private final CodeEditor editor;

    // Performance optimization: cache last update
    private String lastContent = "";
    private long lastUpdateTime = 0;
    private static final long UPDATE_THROTTLE_MS = 300; // تحديث كل 300ms فقط

    public CodeCompletionHelper(String filePath, CodeEditor codeEditor) {
        file = new File(filePath);
        MyIndexer indexer = Editor.getCurrentEditor().getIndexer();
        if (indexer != null) {
            completionProvider = indexer.getCompletionProvider();
        }

        codeEditor.subscribeEvent(SelectionChangeEvent.class, this);

        editor = codeEditor;
    }

    public void add(String keyword, String type) {
        for (KeywordsHolder holder : keywords) {
            if (holder.type.equals(type)) {
                holder.keywords.add(keyword);
                return;
            }
        }
        KeywordsHolder holder = new KeywordsHolder(type, new ArrayList<>());
        holder.keywords.add(keyword);
        keywords.add(holder);
    }

    public static URI getURI(String file) {
        return URI.create("file://" + file);
    }

    /**
     * استخراج الـ prefix الحالي (الكلمة التي يكتبها المستخدم)
     */
    private String getCurrentPrefix(ContentReference contentReference, CharPosition charPosition) {
        String line = contentReference.getLine(charPosition.line).toString();
        if (charPosition.column == 0) {
            return "";
        }

        int start = charPosition.column - 1;
        while (start >= 0 && MyCharacter.isJavaIdentifierPart(line.charAt(start))) {
            start--;
        }
        start++; // نرجع خطوة للأمام

        return line.substring(start, charPosition.column);
    }

    public void requireAutoComplete(ContentReference contentReference, CharPosition charPosition, CompletionPublisher completionPublisher) {
        try {
            if (completionProvider == null) {
                MyIndexer indexer = Editor.getCurrentEditor().getIndexer();
                if (indexer != null) {
                    completionProvider = indexer.getCompletionProvider();
                }
                if (completionProvider == null) {
                    return;
                }
            }

            String prefix = getCurrentPrefix(contentReference, charPosition);

            MyIndexer indexer = Editor.getCurrentEditor().getIndexer();
            com.tyron.builder.project.Project project = indexer != null ? indexer.getProject() : null;

            if (project == null) {
                return;
            }

            Module module = project.getMainModule();

            CompletionParameters params = CompletionParameters.builder()
                    .setProject(project)
                    .setModule(module)
                    .setFile(file)
                    .setContents(editor.getText().toString())
                    .setLine(charPosition.line)
                    .setColumn(charPosition.column)
                    .setPrefix(prefix) // Optional, provider might calculat it
                    .setIndex(charPosition.index)
                    .build();

            CompletionList result = completionProvider.complete(params);
            List<CompletionItem> candidates = result.items;

            items.clear();

            for (CompletionItem candidate : candidates) {
                items.add(getCompletion(candidate.label, "Java", prefix, candidate.iconKind));
            }

            // Add keywords from KeywordsHolder
            for (KeywordsHolder holder : keywords) {
                for (String keyword : holder.keywords) {
                    if (keyword.startsWith(prefix)) {
                        items.add(getCompletion(keyword, holder.type, prefix, DrawableKind.Keyword));
                    }
                }
            }

            // completionPublisher.setComparator(Comparators.byRelevance());
            // completionPublisher.publish(new ArrayList<>(items)); // Disabled pending signature fix
        } catch (Exception e) {
            Log.e("CodeCompletionHelper", "Error getting completions", e);
        }
    }

    private CompletionItemKind getKind(DrawableKind candKind) {
        CompletionItemKind kind;
        if (candKind == null) {
            return CompletionItemKind.Text;
        }
        switch (candKind) {
            case Class:
                kind = CompletionItemKind.Class;
                break;
            case Interface:
                kind = CompletionItemKind.Interface;
                break;
            case Method:
                kind = CompletionItemKind.Method;
                break;
            case Field:
                kind = CompletionItemKind.Field;
                break;
            case LocalVariable:
                kind = CompletionItemKind.Variable;
                break;
            case Package:
                kind = CompletionItemKind.Module;
                break;
            case Keyword:
                kind = CompletionItemKind.Keyword;
                break;
            case Snippet:
                kind = CompletionItemKind.Snippet;
                break;
            default:
                kind = CompletionItemKind.Text;
                break;
        }
        return kind;
    }

    private io.github.rosemoe.sora.lang.completion.CompletionItem getCompletion(String keyword, String type, String prefix, DrawableKind kind) {
        io.github.rosemoe.sora.lang.completion.SimpleCompletionItem completionItem = new SimpleCompletionItem(keyword, type, prefix.length(), keyword);
        completionItem.kind(kind == null ? CompletionItemKind.Keyword : getKind(kind));

        if (drawablesMap.containsKey(keyword)) {
            // completionItem.icon(drawablesMap.get(keyword));
        }
        return completionItem;
    }

    @Override
    public void onReceive(SelectionChangeEvent arg0, Unsubscribe arg1) {
    }
}
