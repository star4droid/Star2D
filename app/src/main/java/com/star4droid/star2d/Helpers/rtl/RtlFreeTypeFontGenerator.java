package com.star4droid.star2d.Helpers.rtl;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StringBuilder;


/**
 * @author Richi on 10/31/19.
 * Generate RTL support {@link BitmapFont}
 * @see FreeTypeFontGenerator
 */
public class RtlFreeTypeFontGenerator extends FreeTypeFontGenerator {
    /**
     * Creates a new generator from the given font file. Uses {@link FileHandle#length()} to determine the file size. If the file
     * length could not be determined (it was 0), an extra copy of the font bytes is performed. Throws a
     * {@link GdxRuntimeException} if loading did not succeed.
     *
     * @param fontFile
     */
    public RtlFreeTypeFontGenerator(FileHandle fontFile) {
        super(fontFile);
    }
    /**
     * Generates a new {@link BitmapFont}. The size is expressed in pixels. Throws a GdxRuntimeException if the font could not be
     * generated. Using big sizes might cause such an exception.
     *
     * @param parameter configures how the font is generated
     */
    public BitmapFont generateFont(FreeTypeFontGenerator.FreeTypeFontParameter parameter, RtlFreeTypeBitmapFontData data) {
        generateData(parameter, data);
        if (data.regions == null && parameter.packer != null)
        {
            data.regions = new Array();
            parameter.packer.updateTextureRegions(data.regions, parameter.minFilter, parameter.magFilter, parameter.genMipMaps);
        }
        BitmapFont font = newBitmapFont(data, data.regions, true);
        font.setOwnsTexture(parameter.packer == null);
        return font;
    }
    /***
     *
     * @return see {@link BitmapFont#BitmapFont(BitmapFont.BitmapFontData, Array, boolean)}
     */
    protected BitmapFont newBitmapFont(BitmapFont.BitmapFontData data, Array<TextureRegion> pageRegions, boolean integer) {
        return new BitmapFont(data, pageRegions, integer)
        {
            @Override public BitmapFontCache newFontCache() {
                return new BitmapFontCache(this, true){
                    @Override public void addText(GlyphLayout layout, float x, float y) {
                        StringBuilder fullText = RtlController.getInstance().getFullTextFromLayout(layout);
                        for (int i = 0; i < layout.runs.size; i++)
                        {
                            RtlController.getInstance().reverse(layout,i,fullText);
                        }
                        super.addText(layout, x, y);
                    }
                };
            }
        };
    }
    public BitmapFont generateRtlFont(FreeTypeFontParameter parameters) {
        return generateFont(parameters , new RtlFreeTypeFontGenerator.RtlFreeTypeBitmapFontData());
    }
    /***
     * {@link com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeBitmapFontData} + Supported RTL fonts
     * @see FreeTypeBitmapFontData
     */
    public static class RtlFreeTypeBitmapFontData extends FreeTypeBitmapFontData {
        @Override public void getGlyphs(GlyphLayout.GlyphRun run, CharSequence strRaw, int start, int end, BitmapFont.Glyph lastGlyph) {
            CharSequence str = RtlController.getInstance().getRtl(strRaw);
//            if(start > str.length()) start = str.length();
//            if(end > str.length()) end = str.length();
            super.getGlyphs(run, str, start, end, lastGlyph);
        }
    }
}
