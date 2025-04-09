package com.star4droid.star2d.CodeEditor;
import android.os.Bundle;
import android.view.View;

//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;

import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Utils;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.util.ArrayList;
import org.eclipse.tm4e.languageconfiguration.model.IndentationRules;

import java.lang.ref.WeakReference;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateSymbolPairMatch;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import org.eclipse.tm4e.languageconfiguration.model.LanguageConfiguration;

public class EngineLanguage extends EmptyLanguage {
	private final TextMateLanguage mTextMateLanguage;
	private final IndentationRules mIndentationRules;
	private final CodeEditor mEditor;
	private final CodeCompletionHelper codeCompletionHelper;
	
	public EngineLanguage(CodeEditor editor/*,ArrayList<String> playerItems*/,String file) {
		mEditor = editor;
		codeCompletionHelper = new CodeCompletionHelper(file,editor);
		//mTextMateLanguage = TextMateLanguage.create("source.java", true);
		String scope=GrammarRegistry.getInstance().loadGrammars("editor/languages.json").get(0).getScopeName();
		mTextMateLanguage = TextMateLanguage.create(scope, true);
		mIndentationRules = GrammarRegistry.getInstance().findLanguageConfiguration(scope).getIndentationRules();
		//mIndentationRules = LanguageConfiguration.load(new ContentReference(new Content(Utils.readAssetFile("editor/language-configuration.json",editor.getContext()))).createReader()).getIndentationRules();
		/*
		String[] ks = {
			"and", "break", "do", "else", "elseif",
			"end", "false", "for", "function", "if",
			"in", "local", "nil", "not", "or",
			"repeat", "return", "then", "true", "until", "while"
		};
		
		String[] keywords = new String[playerItems.size()];
		int x=0;
		for(String keyword:playerItems){
			keywords[x++]=keyword;
			codeCompletionHelper.add(keyword,"PlayerItem");
		}
		
		for(String keyword:ks){
			keywords[x++]=keyword;
			codeCompletionHelper.add(keyword,"Keyword");
		}
		*/
		//mTextMateLanguage.setCompleterKeywords(keywords);
		mTextMateLanguage.setAutoCompleteEnabled(true);
		mTextMateLanguage.setTabSize(editor.getTabWidth());
		((TextMateSymbolPairMatch)mTextMateLanguage.getSymbolPairs()).setEnabled(true);
	}
	
	@Override
	public int getIndentAdvance(/*@NonNull*/ ContentReference content, int line, int column) {
		return getIndentAdvance(content.getLine(line).substring(0, column));
	}
	
	public int getIndentAdvance(String line) {
		return line.matches(mIndentationRules.increaseIndentPattern.pattern()) ? mEditor.getTabWidth() : 0;
	}
	
	private final NewlineHandler[] mNewlineHandlers = new NewlineHandler[]{new EndwiseNewlineHandler()};
	
	@Override
	public NewlineHandler[] getNewlineHandlers() {
		return mNewlineHandlers;
	}
	
	@Override
	public SymbolPairMatch getSymbolPairs() {
		return mTextMateLanguage.getSymbolPairs();
	}
	
	@Override
	///*@NonNull*/
	public AnalyzeManager getAnalyzeManager() {
		return mTextMateLanguage.getAnalyzeManager();
	}
	
	@Override
	public void requireAutoComplete(/*@NonNull*/ ContentReference content, /*@NonNull*/ CharPosition position, /*@NonNull*/ CompletionPublisher publisher, /*@NonNull*/ Bundle extraArguments) {
		//mTextMateLanguage.requireAutoComplete(content, position, publisher, extraArguments);
		codeCompletionHelper.requireAutoComplete(content,position,publisher);
	}
	
	public class EndwiseNewlineHandler implements NewlineHandler {
		private static final String ENDWISE_PATTERN = "^((?!(--)).)*(\\b(else|function|then|do|repeat)\\b((?!\\b(end|until)\\b).)*)$";
		
		private final StringBuilder mStringBuilder = new StringBuilder();
		
		@Override
		public boolean matchesRequirement(/*@NonNull*/ Content text, /*@NonNull*/ CharPosition position, /*@Nullable */Styles style) {
			String line = text.getLineString(position.line);
			String beforeText = line.substring(0, position.column);
			
			return beforeText.matches(ENDWISE_PATTERN);
		}
		
		///*@NonNull*/
		@Override
		public NewlineHandleResult handleNewline(/*@NonNull*/ Content text, /*@NonNull*/ CharPosition position, /*@Nullable */Styles style, int tabSize) {
		//result,shift
			return new NewlineHandleResult("",0);
		}
		
	}
	
	public static String repeat(String str, int count) {
		if (str == null || str.isEmpty() || count <= 0) {
			return "";
		}
		
		StringBuilder sb = new StringBuilder(str.length() * count);
		for (int i = 0; i < count; i++) {
			sb.append(str);
		}
		
		return sb.toString();
	}
	
	public static boolean isOnlySpaces(String str) {
		if (str == null || str.isEmpty()) {
			return false;
		}
		
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isWhitespace(str.charAt(i))) {
				return false;
			}
		}
		
		return true;
	}
	
	public static int leadingSpaceCount(String str) {
		int count = 0;
		int index = 0;
		
		while (index < str.length() && str.charAt(index) == ' ') {
			count++;
			index++;
		}
		
		return count;
	}
	
}