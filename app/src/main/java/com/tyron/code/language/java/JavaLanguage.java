package com.tyron.code.language.java;

import com.tyron.editor.Editor;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.util.MyCharacter;
import io.github.rosemoe.sora.widget.SymbolPairMatch;

public class JavaLanguage extends EmptyLanguage {

    private final Editor mEditor;

    public JavaLanguage(Editor editor) {
        mEditor = editor;
    }

    public boolean isAutoCompleteChar(char p1) {
        return p1 == '.' || MyCharacter.isJavaIdentifierPart(p1);
    }

    public int getIndentAdvance(String p1) {
        return 0;
    }

    public int getFormatIndent(String line) {
        return 0;
    }

    @Override
    public int getInterruptionLevel() {
        return INTERRUPTION_LEVEL_SLIGHT;
    }

    @Override
    public boolean useTab() {
        return true;
    }

    public int getTabWidth() {
        return 4;
    }

    @Override
    public SymbolPairMatch getSymbolPairs() {
        return new SymbolPairMatch.DefaultSymbolPairs();
    }

    private final NewlineHandler[] newLineHandlers
            = new NewlineHandler[]{
                new BraceHandler(), new TwoIndentHandler(), new JavaDocStartHandler(), new JavaDocHandler()
            };

    @Override
    public NewlineHandler[] getNewlineHandlers() {
        return newLineHandlers;
    }

    @Override
    public void destroy() {
    }

    @Override
    public int getIndentAdvance(ContentReference content, int line, int column) {
        return 0;
    }

    class TwoIndentHandler implements NewlineHandler {

        @Override
        public boolean matchesRequirement(Content text, CharPosition position, Styles style) {
            return false;
        }

        @Override
        public NewlineHandleResult handleNewline(Content text, CharPosition position, Styles style, int tabSize) {
            return null;
        }
    }

    class BraceHandler implements NewlineHandler {

        @Override
        public boolean matchesRequirement(Content text, CharPosition position, Styles style) {
            String line = text.getLineString(position.line);
            String beforeText = line.substring(0, position.column);
            String afterText = line.substring(position.column);
            return beforeText.endsWith("{") && afterText.startsWith("}");
        }

        @Override
        public NewlineHandleResult handleNewline(Content text, CharPosition position, Styles style, int tabSize) {
            String line = text.getLineString(position.line);
            String beforeText = line.substring(0, position.column);
            String afterText = line.substring(position.column);

            int count = TextUtils.countLeadingSpaceCount(beforeText, tabSize);
            int advanceBefore = getIndentAdvance(beforeText);
            int advanceAfter = getIndentAdvance(afterText);
            String txt;
            StringBuilder sb
                    = new StringBuilder("\n")
                            .append(TextUtils.createIndent(count + advanceBefore, tabSize, useTab()))
                            .append('\n')
                            .append(txt = TextUtils.createIndent(count + advanceAfter, tabSize, useTab()));
            int shiftLeft = txt.length() + 1;
            return new NewlineHandleResult(sb, shiftLeft);
        }
    }

    class JavaDocStartHandler implements NewlineHandler {

        private boolean shouldCreateEnd = true;

        @Override
        public boolean matchesRequirement(Content text, CharPosition position, Styles style) {
            String line = text.getLineString(position.line);
            String beforeText = line.substring(0, position.column);
            return beforeText.trim().startsWith("/**");
        }

        @Override
        public NewlineHandleResult handleNewline(Content text, CharPosition position, Styles style, int tabSize) {
            String line = text.getLineString(position.line);
            String beforeText = line.substring(0, position.column);
            String afterText = line.substring(position.column);

            int count = TextUtils.countLeadingSpaceCount(beforeText, tabSize);
            int advanceAfter = getIndentAdvance(afterText);

            StringBuilder sb = new StringBuilder();
            sb.append('\n');
            sb.append(TextUtils.createIndent(count + advanceAfter, tabSize, useTab()));
            sb.append(" * ");

            String closing = "";
            if (shouldCreateEnd) {
                sb.append('\n');
                closing = TextUtils.createIndent(count + advanceAfter, tabSize, useTab());
                sb.append(closing);
                sb.append(" */");
            }
            return new NewlineHandleResult(sb, closing.length() + 4);
        }
    }

    class JavaDocHandler implements NewlineHandler {

        @Override
        public boolean matchesRequirement(Content text, CharPosition position, Styles style) {
            String line = text.getLineString(position.line);
            String beforeText = line.substring(0, position.column);
            return beforeText.trim().startsWith("*") && !beforeText.trim().startsWith("*/");
        }

        @Override
        public NewlineHandleResult handleNewline(Content text, CharPosition position, Styles style, int tabSize) {
            String line = text.getLineString(position.line);
            String beforeText = line.substring(0, position.column);
            String afterText = line.substring(position.column);

            int count = TextUtils.countLeadingSpaceCount(beforeText, tabSize);
            int advanceAfter = getIndentAdvance(afterText);
            StringBuilder sb
                    = new StringBuilder()
                            .append("\n")
                            .append(TextUtils.createIndent(count + advanceAfter, tabSize, useTab()))
                            .append("* ");
            return new NewlineHandleResult(sb, 0);
        }
    }
}
