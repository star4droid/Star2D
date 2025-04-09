package com.star4droid.star2d.Helpers.rtl;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BooleanArray;
import com.badlogic.gdx.utils.ByteArray;
import com.badlogic.gdx.utils.CharArray;
import com.badlogic.gdx.utils.StringBuilder;


/**
 * @author Richi on 10/28/19.
 */
public class RtlController {
    private static final RtlGlyph[] ALL_CHARS = RtlGlyph.values();
    public static final MixedChars[] MIXED_CHARS = new MixedChars[] {
            new MixedChars(RtlGlyph.LA).isMixOf(RtlGlyph.LAM , RtlGlyph.A__SIMPLE),
            new MixedChars(RtlGlyph.LA__HAMZE_TOP).isMixOf(RtlGlyph.LAM , RtlGlyph.A__HAMZE_TOP),
            new MixedChars(RtlGlyph.LA__HAMZE_BOTTOM).isMixOf(RtlGlyph.LAM , RtlGlyph.A__HAMZE_BOTTOM),
            new MixedChars(RtlGlyph.LA__HAT_HOLDER).isMixOf(RtlGlyph.LAM , RtlGlyph.A__HAT_HOLDER),
    };
    private static final StringBuilder BRACKETS = new StringBuilder("(){}[]<>");
    private static final char EMPTY_CHARACTER = '\u1000';
    private static RtlController mInstance = null;
    private static final char SPACE_CHAR = 32;
    private boolean mIsEndLastCharacter;
    private boolean mJustUseEnglishDigit;
    //==============================================================
    // METHODS
    //==============================================================
    public CharArray getAllRtlChars() {
        CharArray array = new CharArray();
        for (RtlGlyph glyph : ALL_CHARS)
        {
            for (char character : glyph.getAllLetters().items)
            {
                if (!array.contains(character))  array.add(character);
            }
        }
        return array;
    }
    public StringBuilder getRtl(CharSequence text) {
        return getRtl(text, false);
    }
    public StringBuilder getRtl(CharSequence text , boolean justUseEnglishDigit) {
        mIsEndLastCharacter = true;
        mJustUseEnglishDigit = justUseEnglishDigit;
        StringBuilder rt = new StringBuilder();
        char rtlChar;
        char currentChar;
        char previousChar;
        char nextChar;
        StringBuilder persianSequence = new StringBuilder();
        ByteArray bracketsController = new ByteArray();
        for (int i = 0; i < text.length(); i++)
        {
            currentChar = getCharAt(text , i);
            previousChar = getCharAt(text , i-1);
            nextChar = getCharAt(text , i+1);

            rtlChar = getRtlChar(previousChar,currentChar,nextChar);
            if (isMixedChar(rtlChar))
            {
                persianSequence.setCharAt(persianSequence.length - 1, rtlChar);
            }
            else
            {
                if (!isDigitInPersianContext(i,text) && (isEnglishLetter(rtlChar) || (!isRtl(rtlChar) && persianSequence.length == 0)))
                {
                    if (persianSequence.length != 0)
                    {
                        rt.append(persianSequence);
                        persianSequence.delete(0, persianSequence.length);
                    }
                    rt.append(rtlChar);
                }
                else
                {
                    // add current rtl character to template sequence
                    rtlChar = convertEnglishDigitToPersianIfIsDigit(rtlChar);
                    persianSequence.append(rtlChar);
                }
            }
        }

        if (persianSequence.length != 0)
        {
            rt.append(persianSequence);
        }

        fix_text_length_if_exist_mixedCharacters(rt , text);
        fix_persian_parenthesis_and_brackets(rt);

        return rt;
    }
    public StringBuilder getFullTextFromLayout(GlyphLayout layout) {
        StringBuilder rt = new StringBuilder();
        for (int i = 0; i < layout.runs.size; i++)
        {
            for (int j = 0; j < layout.runs.get(i).glyphs.size; j++)
            {
                rt.append(layout.runs.get(i).glyphs.get(j));
            }
        }
        return rt;
    }
    /**
     * reverse the glyphs for rendering rtl languages (Persian/Arabic)
     */
    public void reverse(final GlyphLayout layout, int curRunIndex , final StringBuilder fullText) {
        final Array<BitmapFont.Glyph> rt = new Array<>();
        Array<BitmapFont.Glyph> persianGlyphs = new Array<>();
        final BooleanArray bracketIsEnglish = new BooleanArray();
        char c;
        int persianSequenceOffset = 0;
        BitmapFont.Glyph g;
        BitmapFont.Glyph gFinal;
        int englishOffset = -1;
        int bracketIndex;
        boolean isEnglishBracket , isPersianBracket;
        boolean isSeparatorCharacter_and_isEnglishSequence, isEnglishContext ;
        GlyphLayout.GlyphRun run = layout.runs.get(curRunIndex);

        for (int i = 0; i < run.glyphs.size; i++)
        {
            g = run.glyphs.get(i);
            c = (char) g.id;
            bracketIndex = isBracket(c);
            if (c == EMPTY_CHARACTER || c ==0) continue;
            isEnglishBracket = bracketIndex>=0 && !isPersianBracket(g,fullText, i,bracketIsEnglish);
            isPersianBracket = bracketIndex>=0 && isPersianBracket(g,fullText, i,bracketIsEnglish);
            isSeparatorCharacter_and_isEnglishSequence = isEnglishLetter(c) || (!isRtl(c) && persianGlyphs.isEmpty() && !isEnglishBracket) ;
            if (!isPersianBracket && (isEnglishBracket || isSeparatorCharacter_and_isEnglishSequence || isDigit_persian_or_english(c)))
            {
                if (!persianGlyphs.isEmpty())
                {
                    isEnglishContext = !isRtlContext(layout,fullText,curRunIndex);
                    englishOffset = appendPersian(englishOffset, rt, persianGlyphs , isEnglishContext);
                }
                englishOffset++;

                if (bracketIndex >= 0)
                {
                    if (bracketIndex % 2 == 0) // if is opened bracket
                    {
//                        bracketsGlyphs.insert(0, null);
                        bracketIsEnglish.insert(0,true);
                        //                            bracketsGlyphs.insert(0,g);
                    }
                    else
                    {
                        if (bracketIsEnglish.size > 0)
                        {
                            if (englishOffset > 0 && !bracketIsEnglish.get(0))
                            {
                                englishOffset--;
                            }
//                            bracketsGlyphs.removeIndex(0);
                            bracketIsEnglish.removeIndex(0);
                        }
                    }
                }
                rt.insert(englishOffset, g);
            }
            else // if  c  is Persian/Arabic letter then :
            {
                gFinal = g;
                bracketIndex = isBracket(c);
                if (bracketIndex >= 0)
                {
                    gFinal = findPersianBracketOf(bracketIndex, g, layout);
                    if (bracketIndex % 2 == 0) // if is opened bracket
                    {
                        bracketIsEnglish.insert(0,false);
                    }
                    else
                    {
                        if (bracketIsEnglish.size > 0 && !bracketIsEnglish.get(0))
                        {
                            bracketIsEnglish.removeIndex(0);
                        }
                    }
                }
                persianGlyphs.add(gFinal);
            }
        }

        if(!persianGlyphs.isEmpty())  appendPersian(englishOffset, rt, persianGlyphs,!isRtlContext(layout,fullText,curRunIndex));

        run.glyphs.clear();
        for (BitmapFont.Glyph glyph : rt) run.glyphs.add(glyph);

        // fix xAdvanced value.
        for (int i = 1; i < run.glyphs.size; i++)
        {
            run.xAdvances.set(i,run.glyphs.get(i-1).xadvance);
        }
        run.xAdvances.set(0,layout.width - run.width);
    }
    //==============================================================
    // Privates
    //==============================================================
    private boolean isDigitInPersianContext(int index, CharSequence text) {
        if(isEnglishDigit(text.charAt(index)))
        {
            for (int i = index +1; i < text.length(); i++)
            {
                if(isRtl(text.charAt(i))) return true; else if(isEnglishLetter(text.charAt(i))) return false;
            }
        }
        return false;
    }
    private void fix_persian_parenthesis_and_brackets(final StringBuilder rt) {

        find_index_of_exist_alone_bracket_character(rt, new OnAloneBracket() {
            @Override public void onAloneBracket(int bracketIndex) {
                int anotherBracketIndex = getAnOtherBracketIndex(bracketIndex);
                boolean isEnglishBracket;
                if(!rt.contains(BRACKETS.charAt(anotherBracketIndex) + ""))
                {
                    for (int i = 0; i < rt.length; i++)
                    {
                        if (rt.charAt(i) == BRACKETS.charAt(bracketIndex))
                        {
                            isEnglishBracket = isEnglishBracket(rt, i);
                            if (!isEnglishBracket)
                            {
                                rt.setCharAt(i,(BRACKETS.charAt(anotherBracketIndex)));
                            }
                        }
                    }
                }
            }
        });



//        CharArray brackets = new CharArray();
//        BooleanArray isEnglishBrackets = new BooleanArray();
//        boolean isEnglishBracket;
//        for (int i = 0; i < rt.length; i++)
//        {
//            for (int j = 0; j < BRACKETS.length; j++)
//            {
//                if (rt.charAt(i) == BRACKETS.charAt(j))
//                {
//                    isEnglishBracket = isEnglishBracket(rt,i);
//                    if( j % 2 ==0) // if bracket opened then
//                    {
//                        brackets.insert(0,BRACKETS.charAt(j));
//                        isEnglishBrackets.insert(0,isEnglishBracket);
//                        if (!isEnglishBracket)
//                        {
//                            rt.setCharAt(i, (char) (BRACKETS.charAt(j + 1)));
//                            //                            rt.insert(i+1, PERSIAN_BRACKETS_CHAR);
//                        }
//                    }
//                    else // if closed the bracket
//                    {
//                        if(!isEnglishBrackets.get(0))
//                        {
//                            rt.setCharAt(i, (char) (BRACKETS.charAt(j-1)));
//                            //                            rt.insert(i+1, PERSIAN_BRACKETS_CHAR);
//                        }
//                        brackets.removeIndex(0);
//                        isEnglishBrackets.removeIndex(0);
//                    }
//                    break;
//                }
//            }
//        }
    }
    private int getAnOtherBracketIndex(int bracketIndex) {
        return bracketIndex%2 ==0? bracketIndex+1:bracketIndex-1;
    }
    private void find_index_of_exist_alone_bracket_character(StringBuilder fullText , OnAloneBracket onAloneBracket) {
        int c = 0;
        for (int bracketIndex = 0; bracketIndex < BRACKETS.length; bracketIndex+=2)
        {
            if (isAloneBracket(bracketIndex,fullText))
            {
                onAloneBracket.onAloneBracket(bracketIndex);
            }
        }
    }
    private boolean isRtlContext(GlyphLayout layout, StringBuilder fulltext, int curRunIndex) {
        int curIndex = 0;
        for (int i = 0; i < curRunIndex; i++)
        {
            curIndex += layout.runs.get(i).glyphs.size;
        }
        for (int i = curIndex; i >= 0; i--)
        {
            if (fulltext.charAt(curIndex) == '\n' || i == 0)
            {
                while (i < fulltext.length)
                {
                    if(isRtl(fulltext.charAt(i)))
                    {
                        return true;
                    }
                    else if(isEnglishLetter(fulltext.charAt(i)))
                    {
                        return false;
                    }
                    i++;
                }
                break;
            }
        }

        return false;
    }
    private boolean isPersianBracket(BitmapFont.Glyph g,StringBuilder fullText , int index, BooleanArray bracketIsEnglish) {
        Array<BitmapFont.Glyph> originGlyphs;
        int bracketIndex = isBracket((char) g.id);
        if(bracketIndex >= 0) // if ( isBracket )
        {
            if(bracketIndex %2 ==0) // if (is opened bracket)
            {
                return !isEnglishBracket(fullText,index);
            }
            else
            {
                if(isAloneBracket(bracketIndex,fullText))
                {
                    return !isEnglishBracket(fullText,index);
                }
                else
                {
                    return bracketIsEnglish.size>0 && !bracketIsEnglish.get(0);
                }
            }
        }
        return false;
    }
    private boolean isAloneBracket(int bracketIndex, StringBuilder fullText) {
        int balance = 0;
        int anotherBracketIndex = getAnOtherBracketIndex(bracketIndex);
        for (int i = 0; i < fullText.length; i++)
        {
            if (fullText.charAt(i) == BRACKETS.charAt(bracketIndex))
            {
                balance++;
            }
            else if (fullText.charAt(i) == BRACKETS.charAt(anotherBracketIndex))
            {
                balance--;
            }
        }
        return balance != 0;
    }
    private BitmapFont.Glyph findPersianBracketOf(int bracketIndex, BitmapFont.Glyph g, GlyphLayout layout) {
        if(bracketIndex % 2 ==0) bracketIndex++; else  bracketIndex--;
        BitmapFont.Glyph glyph;
        for (int i = 0; i < layout.runs.size; i++)
        {
            for (int j = 0; j < layout.runs.get(i).glyphs.size; j++)
            {
                glyph = layout.runs.get(i).glyphs.get(j);
                if( glyph.id == BRACKETS.charAt(bracketIndex))
                {
                    return glyph;
                }
            }
        }
        return g;
    }
    private void fix_text_length_if_exist_mixedCharacters(StringBuilder rt, CharSequence text) {
        for (int i = rt.length; i < text.length(); i++)
        {
//            rt.append('\u0000');
            rt.append(' ');
//            rt.append(EMPTY_CHARACTER);
        }
    }
    private int isBracket(char c) {
        for (int i = 0; i < BRACKETS.length; i++)
        {
            if(c == BRACKETS.charAt(i)) return i;
        }
        return -1;
    }
    private boolean isEnglishBracket(Array<BitmapFont.Glyph>  glyphs, int index) {
        char c;
        for (int i = index-1; i >= 0; i--)
        {
            c = (char) glyphs.get(i).id;
            if(isEnglishLetter(c)) return true;
            if(isRtl(c)) return false;
        }
        for (int i = index+1; i < glyphs.size; i++)
        {
            c = (char) glyphs.get(i).id;
            if(isEnglishLetter(c)) return true;
            if(isRtl(c)) return false;
        }

        return false;
    }
    private boolean isEnglishBracket(StringBuilder rt , int index) {
        char c;
        for (int i = index-1; i > 0; i--)
        {
            c = rt.charAt(i);
            if(isEnglishLetter(c)) return true;
            if(isRtl(c)) return false;
        }
        for (int i = index+1; i < rt.length; i++)
        {
            c = rt.charAt(i);
            if(isEnglishLetter(c)) return true;
            if(isRtl(c)) return false;
        }
        return true;
    }
    private char convertEnglishDigitToPersianIfIsDigit(char rtlChar) {
        if(!mJustUseEnglishDigit && isEnglishDigit(rtlChar))
        {
            return RtlGlyph.getPersianDigit(rtlChar);
        }
        return rtlChar;
    }
    private int appendPersian(int englishOffset, Array<BitmapFont.Glyph> rtGlyphs, Array<BitmapFont.Glyph> persianGlyphs , boolean isEnglishContext) {
        int rt = -1;
        if(!isEnglishContext && rtGlyphs.size > 0 && rtGlyphs.get(englishOffset).id == SPACE_CHAR)
        {
            rtGlyphs.insert(0,rtGlyphs.get(englishOffset));
            rtGlyphs.removeIndex(englishOffset+1);
        }
        // fix space character location
        if(isEnglishContext && persianGlyphs.size > 0 && persianGlyphs.get(persianGlyphs.size-1).id == SPACE_CHAR)
        {
            persianGlyphs.insert(0,persianGlyphs.get(persianGlyphs.size-1));
            persianGlyphs.removeIndex(persianGlyphs.size-1);
        }
        int indexForAdd = 0;
        if(isEnglishContext)  indexForAdd = rtGlyphs.size;
        for (int i = 0; i < persianGlyphs.size; i++)
        {
            rtGlyphs.insert(indexForAdd,persianGlyphs.get(i));
        }
        if(isEnglishContext) rt= rtGlyphs.size-1;
        persianGlyphs.clear();

        return rt;
    }
    private boolean isPersianDigit(char c) {
        return (c >= RtlGlyph.PERSIAN_DIGIT_0.getPrimary() && c <= RtlGlyph.PERSIAN_DIGIT_9.getPrimary()) || (c >= RtlGlyph.PERSIAN_DIGIT__0.getPrimary() && c <= RtlGlyph.PERSIAN_DIGIT__9.getPrimary());
    }
    private boolean isEnglishLetter(char c) {
        return c>=65 && c<= 90 || c>=97 && c<=122;
    }
    private boolean isEnglishDigit(char rtlChar) {
        return rtlChar >= 48 && rtlChar <58;
    }
    private boolean isMixedChar(char rtlChar) {
        for (MixedChars mixedChar: MIXED_CHARS)
        {
            if(mixedChar.mixedRtlChar.getPrimary() == rtlChar) return true;
            if(mixedChar.mixedRtlChar.getStartChar() == rtlChar) return true;
            if(mixedChar.mixedRtlChar.getCenterChar() == rtlChar) return true;
            if(mixedChar.mixedRtlChar.getEndChar() == rtlChar) return true;
        }
        return false;
    }
    private char getRtlChar(char previousChar ,char currentChar ,char nextChar ) {

        if(!isRtl(nextChar)) nextChar = 0;
        if(!isRtl(previousChar)) previousChar = 0;
        RtlGlyph nextGlyph = findRtlGlyphOf(nextChar);
        RtlGlyph curGlyph = findRtlGlyphOf(currentChar);
        char rt = currentChar;
        if (curGlyph != null)
        {
            if(previousChar == 0 && nextChar == 0)
            {
                rt = ((char) curGlyph.getPrimary());
            }
            else if (previousChar !=0 && nextChar == 0)
            {
                if(isMixer(currentChar) && isMixable(previousChar))
                {
                    rt = (getMergeOf(previousChar , currentChar));
                }
                else
                {
                    if(this.mIsEndLastCharacter)
                    {
                        rt = ((char) curGlyph.getPrimary());
                    }
                    else
                    {

                        rt = ((char) curGlyph.getEndChar());
                    }
                }
            }
            else if (previousChar ==0 && nextChar != 0)
            {
//                if(this.mIsEndLastCharacter)
//                {
//                    rt = ((char) glyph.getStartChar());
//                }
//                else
                {

                    rt = ((char) curGlyph.getStartChar());
                }
            }
            else // if (beforeChar !=0 && afterChar != 0)
            {
                if(isMixer(currentChar) && isMixable(previousChar))
                {
                    rt = (getMergeOf(previousChar , currentChar));
                }
                else
                {

                    if(this.mIsEndLastCharacter)
                    {
                        rt = ((char) curGlyph.getStartChar());
                    }
                    else
                    {
                        if(nextGlyph != null && (nextGlyph.isFourCharacter() || nextGlyph.isTowCharacter()))
                        {
                            rt = ((char) curGlyph.getCenterChar());
                        }
                        else
                        {
                            rt = ((char) curGlyph.getEndChar());
                        }
                    }
                }
            }
            if (!(isMixer(nextChar) && isMixable(currentChar)))
            {
                this.mIsEndLastCharacter = !curGlyph.isFourCharacter();
            }
        }
        else
        {
            this.mIsEndLastCharacter = true;
        }
        return rt;
    }
    private RtlGlyph findRtlGlyphOf(char curChar) {
        for (RtlGlyph rtlGlyph : ALL_CHARS)
        {
            if (rtlGlyph.getPrimary() == curChar)
            {
                return rtlGlyph;
            }
        }
        return null;
    }
    private char getCharAt(CharSequence text, int position) {
        if (position >= 0 && position < text.length())
        {
            return text.charAt(position);
        }
        return 0;
    }
    /**
     * L and A is mergable with them (L is mergable   and  A is merger for example :   ﻻ    is the merge of    ل    and   ا   )
     * @param primaryChar
     * @return
     */
    private boolean isMixable(char primaryChar) {
        for (RtlGlyph rtlGlyph : RtlGlyph.MIXABLE)
        {
            if (rtlGlyph.getPrimary() == primaryChar)
            {
                return true;
            }
        }
        return false;
    }
    /**
     * L and A is mixable in Persian and Arabic language (L is mixable   and  A is mixer for example :   ﻻ    is the merge of    ل    and   ا   )
     * @param primaryCharacter check for merger (ths char must be primary character and WAS NOT converted to rtl)
     * @return true if the character was merger like ا and آ and ... (families)
     */
    private boolean isMixer(char primaryCharacter) {

        for (RtlGlyph rtlGlyph : RtlGlyph.MIXERS)
        {
            if (rtlGlyph.getPrimary() == primaryCharacter)
            {
                return true;
            }
        }
        return false;
    }
    /**
     *
     * @param beforePrimaryChar
     * @param curPrimaryChar
     * @return
     */
    private char getMergeOf(char beforePrimaryChar, char curPrimaryChar) {
        for (MixedChars mixedChar : MIXED_CHARS)
        {
            if (mixedChar.primaryMixedChars[0].getPrimary() == beforePrimaryChar && mixedChar.primaryMixedChars[1].getPrimary() == curPrimaryChar)
            {
                if (mIsEndLastCharacter)
                {
                    return (char) mixedChar.mixedRtlChar.getPrimary();
                }
                else
                {
                    return (char) mixedChar.mixedRtlChar.getEndChar();
                }
            }
        }
        return 0;
    }
    private boolean isRtl(char rtlCharacter) {
        return rtlCharacter > 255 && rtlCharacter <66000; // all character larger than 66000 is not in range of rtl languages.
    }
    private boolean isDigit_persian_or_english(char c) {
        return isPersianDigit(c) || isEnglishDigit(c);
    }
    //==============================================================
    // classes and structures
    //==============================================================
    protected static class MixedChars {
        public RtlGlyph[] primaryMixedChars;
        /**
         * ﻻ <br />  ﻼ
         * ﻵ <br />  ﻶ
         * ﻷ <br />  ﻸ
         * ﻹ <br />  ﻺ
         */
        public RtlGlyph mixedRtlChar;
        public MixedChars(RtlGlyph mixedRtlChar) {
            this.mixedRtlChar = mixedRtlChar;
        }
        public MixedChars isMixOf(RtlGlyph... primaryMixedChars) {
            this.primaryMixedChars = primaryMixedChars;
            return this;
        }
    }
    private interface OnAloneBracket { public void onAloneBracket(int bracketIndex); }
    //==============================================================
    // Statics
    //==============================================================
    public synchronized static RtlController getInstance() {
        if (mInstance == null)
        {
            mInstance = new RtlController();
        }
        return mInstance;
    }
}
