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
import com.tyron.javacompletion.JavaCompletions;
import com.tyron.javacompletion.completion.CompletionCandidate;
import com.tyron.javacompletion.completion.CompletionResult;
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
	
	final ArrayList<CompletionItem> items=new ArrayList<>();
	final ArrayList<KeywordsHolder> keywords= new ArrayList<>();
	final HashMap<String,Drawable> drawablesMap=new HashMap<>();
	public boolean proAutoCompletion=true;
	private final Path path;
	private final JavaCompletions completions;
	private final CodeEditor editor;
	//private FileSystemModuleManager moduleManager;
	public CodeCompletionHelper(String file,CodeEditor codeEditor){
		path = new java.io.File(file).toPath();
		completions = Editor.getCurrentEditor().getIndexer().getJavaCompletions();
		codeEditor.subscribeEvent(SelectionChangeEvent.class,this);
		/*
		IndexOptions indexOptions=new IndexOptions(){
			@Override
			public boolean shouldIndexPrivate() {
			    return true;
			}

			@Override
			public boolean shouldIndexMethodContent() {
			    return true;
			}
			
		};
		*/
		//moduleManager = new FileSystemModuleManager(completions.getFileManager(),new java.io.File(Editor.getCurrentEditor().getProject().get("java")).toPath(),indexOptions);
		
		Project project = new Project(Editor.getCurrentEditor().getProject().getPath());
		editor = codeEditor;
		try {
		//moduleManager.initialize();
		//completions.openFile(path, FileUtil.readFile(file));
		completions.getFileManager().openFileForSnapshot(getURI(file),/*completions.getFileManager().getFileContent(path).orElse(*/FileUtil.readFile(file)/*).toString()*/);
		if(Editor.getCurrentEditor()!=null)
			for(Actor actor:Editor.getCurrentEditor().getLibgdxEditor().getActors()){
				if(actor instanceof Image && actor instanceof EditorItem){
					String name = ((EditorItem)actor).getPropertySet().getString("name");
					//TODO : ...
				}
			}
		} catch(Exception exception){}
	}
	public void add(String keyword,String type){
		for(KeywordsHolder holder:keywords){
			if(holder.type.equals(type)){
				holder.keywords.add(keyword);
				return;
			}
		}
		KeywordsHolder holder = new KeywordsHolder(type,new ArrayList<>());
		holder.keywords.add(keyword);
		keywords.add(holder);
	}
	
	public static URI getURI(String file){
		return URI.create("file://"+file);
	}
	
	public void requireAutoComplete(ContentReference contentReference, CharPosition charPosition, CompletionPublisher completionPublisher) {
		completions.updateFileContent(path,editor.getText().toString());
		CompletionResult result = completions.getCompletions(path,charPosition.line,charPosition.column);
		//CompletionResult result = new Completor(completions.getFileManager()).getCompletionResult(null,path,charPosition.line,charPosition.column);
		
		for (CompletionCandidate candidate : result.getCompletionCandidates()) {
			if (!"<error>".equals(candidate.getName())) {
				CompletionItem item = getCompletion(candidate.getName(),candidate.getDetail().orElse(candidate.getKind().name()),result.getPrefix(),candidate.getKind());
				completionPublisher.addItem(item);
			}
		}
		Log.e("completion_pos",charPosition.column+","+charPosition.line);
	}
	
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
		if(/*type.equals("PlayerItem")&&*/drawablesMap.containsKey(keyword)){
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
		completions.updateFileContent(path,editor.getText().toString());
	}
	
}