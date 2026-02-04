package com.star4droid.star2d.editor;

import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.editor.Caret;
import com.tyron.editor.CharPosition;
import com.tyron.editor.Content;
import com.tyron.editor.Editor;
import com.tyron.code.ui.project.ProjectManager;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.text.Cursor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.os.Handler;
import android.os.Looper;

public class SimpleRosemoeEditor implements Editor {

    private final CodeEditor mEditor;
    private File mCurrentFile;
    private List<DiagnosticWrapper> mDiagnostics = new ArrayList<>();
    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());

    public SimpleRosemoeEditor(CodeEditor editor, File file) {
        mEditor = editor;
        mCurrentFile = file;
    }

    @Override
    public Project getProject() {
        return ProjectManager.getInstance().getCurrentProject();
    }

    @Override
    public List<DiagnosticWrapper> getDiagnostics() {
        return mDiagnostics;
    }

    @Override
    public void setDiagnostics(List<DiagnosticWrapper> diagnostics) {
        mDiagnostics = diagnostics;
        // implementation to show diagnostics in editor
    }

    @Override
    public boolean isBackgroundAnalysisEnabled() {
        return true;
    }

    @Override
    public File getCurrentFile() {
        return mCurrentFile;
    }

    @Override
    public void openFile(File file) {
        mCurrentFile = file;
        // logic to load file content into mEditor
    }

    @Override
    public CharPosition getCharPosition(int index) {
        io.github.rosemoe.sora.text.CharPosition pos = mEditor.getText().getIndexer().getCharPosition(index);
        return new CharPosition(pos.line, pos.column);
    }

    @Override
    public int getCharIndex(int line, int column) {
        if (line >= mEditor.getText().getLineCount()) {
            return mEditor.getText().length();
        }
        return mEditor.getText().getIndexer().getCharIndex(line, column);
    }

    @Override
    public boolean useTab() {
        return false; // !mEditor.getProps().useICUILib; // simplification
    }

    @Override
    public int getTabCount() {
        return mEditor.getTabWidth();
    }

    @Override
    public void insert(int line, int column, String string) {
        mEditor.getText().insert(line, column, string);
    }

    @Override
    public void insertMultilineString(int line, int column, String string) {
        mEditor.getText().insert(line, column, string);
    }

    @Override
    public void delete(int startLine, int startColumn, int endLine, int endColumn) {
        mEditor.getText().delete(startLine, startColumn, endLine, endColumn);
    }

    @Override
    public void delete(int startIndex, int endIndex) {
        // converting index to line/col is expensive without indexer access
        // logic needed
    }

    @Override
    public void replace(int startLine, int startColumn, int endLine, int endColumn, String string) {
        mEditor.getText().delete(startLine, startColumn, endLine, endColumn);
        mEditor.getText().insert(startLine, startColumn, string);
    }

    @Override
    public boolean formatCodeAsync() {
        return false;
    }

    @Override
    public boolean formatCodeAsync(int startIndex, int endIndex) {
        return false;
    }

    @Override
    public void beginBatchEdit() {
        // mEditor.getConnection().beginBatchEdit();
    }

    @Override
    public void endBatchEdit() {
        // mEditor.getConnection().endBatchEdit();
    }

    @Override
    public Caret getCaret() {
        return new CaretWrapper(mEditor.getCursor());
    }

    @Override
    public Content getContent() {
        return new ContentWrapper(mEditor.getText());
    }

    @Override
    public void setSelection(int line, int column) {
        mEditor.setSelection(line, column);
    }

    @Override
    public void setSelectionRegion(int line, int column, int endLine, int endColumn) {
        mEditor.setSelectionRegion(line, column, endLine, endColumn);
    }

    @Override
    public void setSelectionRegion(int startIndex, int endIndex) {
        // conversion needed
    }

    @Override
    public void moveSelectionUp() {
        // mEditor.moveSelectionUp();
    }

    @Override
    public void moveSelectionDown() {
        // mEditor.moveSelectionDown();
    }

    @Override
    public void moveSelectionLeft() {
        // mEditor.moveSelectionLeft();
    }

    @Override
    public void moveSelectionRight() {
        // mEditor.moveSelectionRight();
    }

    @Override
    public void setAnalyzing(boolean analyzing) {
        // show progress
    }

    @Override
    public void requireCompletion() {
        // trigget completion
    }

    private class CaretWrapper implements Caret {

        private final Cursor mCursor;

        public CaretWrapper(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public int getStart() {
            return mCursor.getLeft();
        }

        @Override
        public int getEnd() {
            return mCursor.getRight();
        }

        @Override
        public int getStartLine() {
            return mCursor.getLeftLine();
        }

        @Override
        public int getStartColumn() {
            return mCursor.getLeftColumn();
        }

        @Override
        public int getEndLine() {
            return mCursor.getRightLine();
        }

        @Override
        public int getEndColumn() {
            return mCursor.getRightColumn();
        }

        @Override
        public boolean isSelected() {
            return mCursor.isSelected();
        }
    }

    private class ContentWrapper implements Content {

        private final io.github.rosemoe.sora.text.Content mContent;

        public ContentWrapper(io.github.rosemoe.sora.text.Content content) {
            mContent = content;
        }

        @Override
        public int length() {
            return mContent.length();
        }

        @Override
        public char charAt(int index) {
            return mContent.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return mContent.subSequence(start, end);
        }

        @Override
        public boolean canRedo() {
            return mContent.canRedo();
        }

        @Override
        public void redo() {
            mContent.redo();
        }

        @Override
        public boolean canUndo() {
            return mContent.canUndo();
        }

        @Override
        public void undo() {
            mContent.undo();
        }

        @Override
        public int getLineCount() {
            return mContent.getLineCount();
        }

        @Override
        public String getLineString(int line) {
            return mContent.getLineString(line);
        }

        @Override
        public void insert(int line, int column, CharSequence text) {
            mContent.insert(line, column, text);
        }

        @Override
        public void insert(int index, CharSequence text) {
            // mContent.insert(index, text);
        }

        @Override
        public void delete(int start, int end) {
            mContent.delete(start, end);
        }

        @Override
        public void replace(int start, int end, CharSequence text) {
            mContent.replace(start, end, text);
        }
    }
}
