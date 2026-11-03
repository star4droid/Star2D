package com.star4droid.template.Utils;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable;

/**
 * A drawable whose real picture is made on first draw. Animations are read on a background thread, where no
 * OpenGL texture may be created (on Android it silently becomes texture 0 and shows whatever texture was bound last,
 * e.g. a whole tileset page); the texture is created here, on the render thread.
 */
public class LazyDrawable extends BaseDrawable implements TransformDrawable {
	public interface Maker { Drawable make(); }
	private final Maker maker;
	private Drawable real;
	private boolean failed;

	public LazyDrawable(Maker maker, float minW, float minH) {
		this.maker = maker;
		setMinWidth(minW);
		setMinHeight(minH);
	}

	private Drawable real() {
		if (real == null && !failed) {
			try { real = maker.make(); } catch (Throwable t) { failed = true; }
			if (real == null) failed = true;
		}
		return real;
	}

	@Override
	public void draw(Batch batch, float x, float y, float width, float height) {
		Drawable d = real();
		if (d != null) d.draw(batch, x, y, width, height);
	}

	@Override
	public void draw(Batch batch, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
		Drawable d = real();
		if (d instanceof TransformDrawable) ((TransformDrawable) d).draw(batch, x, y, originX, originY, width, height, scaleX, scaleY, rotation);
		else if (d != null) d.draw(batch, x, y, width * scaleX, height * scaleY);
	}
}
