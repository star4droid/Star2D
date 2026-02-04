package com.star4droid.star2d.CodeEditor;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.editor.items.EditorItem;
// import com.tyron.javacompletion.JavaCompletions;
// import com.tyron.javacompletion.completion.CompletionCandidate;
// import com.tyron.javacompletion.completion.CompletionResult;
import io.github.rosemoe.sora.event.EventReceiver;
import io.github.rosemoe.sora.event.Unsubscribe;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.completion.Comparators;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.lang.completion.CompletionItem;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.Locale;

public class CodeCompletionHelper implements EventReceiver<SelectionChangeEvent> {

    final ArrayList<CompletionItem> items = new ArrayList<>();

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
    private final Path path;
    // private final JavaCompletions completions; // Commented out due to missing class
    private final CodeEditor editor;

    // Performance optimization: cache last update
    private String lastContent = "";
    private long lastUpdateTime = 0;
    private static final long UPDATE_THROTTLE_MS = 300; // تحديث كل 300ms فقط

    public CodeCompletionHelper(String file, CodeEditor codeEditor) {
        path = new java.io.File(file).toPath();
        // completions = Editor.getCurrentEditor().getIndexer().getJavaCompletions(); // Commented out due to missing class
        codeEditor.subscribeEvent(SelectionChangeEvent.class, this);

        Project project = new Project(Editor.getCurrentEditor().getProject().getPath());
        editor = codeEditor;
        try {
            // completions.getFileManager().openFileForSnapshot(getURI(file),FileUtil.readFile(file)); // Commented out due to missing class
            if (Editor.getCurrentEditor() != null) {
                for (Actor actor : Editor.getCurrentEditor().getLibgdxEditor().getActors()) {
                    if (actor instanceof Image && actor instanceof EditorItem) {
                        String name = ((EditorItem) actor).getPropertySet().getString("name");
                        //TODO : ...
                    }
                }
            }
        } catch (Exception exception) {
        }
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
     * فحص ما إذا كان يجب عرض الإكمال التلقائي في السياق الحالي
     */
    private boolean shouldShowCompletion(ContentReference contentReference, CharPosition charPosition) {
        if (charPosition.column == 0) {
            return false; // لا تعرض في بداية السطر
        }

        String line = contentReference.getLine(charPosition.line).toString();
        if (charPosition.column > line.length()) {
            return false;
        }

        // احصل على الحرف قبل المؤشر
        char prevChar = line.charAt(charPosition.column - 1);

        // لا تعرض بعد المسافة أو الفاصلة المنقوطة
        if (prevChar == ' ' || prevChar == ';' || prevChar == '{' || prevChar == '}') {
            return false;
        }

        // لا تعرض داخل التعليقات
        String beforeCursor = line.substring(0, charPosition.column);
        if (beforeCursor.trim().startsWith("//") || beforeCursor.contains("/*")) {
            return false;
        }

        // لا تعرض داخل النصوص (strings)
        int quoteCount = 0;
        for (int i = 0; i < charPosition.column; i++) {
            if (line.charAt(i) == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                quoteCount++;
            }
        }
        if (quoteCount % 2 != 0) {
            return false; // داخل نص
        }

        // اعرض فقط بعد . أو إذا كان يكتب identifier
        return prevChar == '.' || MyCharacter.isJavaIdentifierPart(prevChar);
    }

    @Override
    public void onReceive(SelectionChangeEvent event, Unsubscribe unsubscribe) {
        // validation logic
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
        // Stub
    }

    /*
	// Commented out due to missing com.tyron.javacompletion classes
	private CompletionItemKind getKind(CompletionCandidate.Kind candKind){
		CompletionItemKind kind;
		switch (candKind) {
			case CLASS:
				kind = CompletionItemKind.Class;
				break;
			case INTERFACE:
				kind = CompletionItemKind.Interface;
				break;
			case ENUM:
				kind = CompletionItemKind.Enum;
				break;
			case METHOD:
				kind = CompletionItemKind.Method;
				break;
			case FIELD:
				kind = CompletionItemKind.Field;
				break;
			case VARIABLE:
				kind = CompletionItemKind.Variable;
				break;
			case PACKAGE:
				kind = CompletionItemKind.Module;
				break;
			case KEYWORD:
				kind = CompletionItemKind.Keyword;
				break;
			default:
				kind = CompletionItemKind.Text;
				break;
		}
		return kind;
	}
	
	private boolean checkAggressive(FuzzyScore fuzzyScore,String word,String keyword){
		if(keyword.toLowerCase().startsWith(word.toLowerCase())) return true;
		return (fuzzyScore!=null&&fuzzyScore.getScore() < -20);
	}
	
	private class Checker implements CompletionHelper.PrefixChecker {
		@Override
		public boolean check(char c) {
			return MyCharacter.isJavaIdentifierPart(c);
		}
	}
	
	private CompletionItem getCompletion(String keyword,String type,String prefix,CompletionCandidate.Kind kind){
		CompletionItem completionItem = new JavaCompletionItem(keyword,type,prefix,keyword);
		if(drawablesMap.containsKey(keyword)){
			return completionItem.icon(drawablesMap.get(keyword));
		}
		return completionItem.kind(kind==null?CompletionItemKind.Keyword:getKind(kind));
	}
	
	private class KeywordsHolder {
		public final String type;
		public final ArrayList<String> keywords;
		public KeywordsHolder(String tp,ArrayList<String> ks){
			type = tp;
			keywords = ks;
		}
	}
	
	@Override
	public void onReceive(SelectionChangeEvent arg0, Unsubscribe arg1) {
		// حدّث المحتوى بشكل async لتجنب التأخير
		new Thread(() -> {
			try {
				String content = editor.getText().toString();
				if (!content.equals(lastContent)) {
					// completions.updateFileContent(path, content); // Commented out due to missing class
					lastContent = content;
					lastUpdateTime = System.currentTimeMillis();
				}
			} catch (Exception e) {
				// Ignore errors during background update
			}
		}).start();
	}
     */
}
