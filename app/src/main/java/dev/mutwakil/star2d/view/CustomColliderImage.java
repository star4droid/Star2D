package dev.mutwakil.star2d.view;

import android.graphics.NinePatch;
import android.graphics.drawable.NinePatchDrawable;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.VisImage;
import dev.mutwakil.star2d.adapter.CustomColliderEditor;

public class CustomColliderImage extends VisImage {

	private CustomColliderEditor.SizeObserver observer;
	private float targetWidth, targetHeight, imageWidth, imageHeight;
	private CustomColliderEditor editor;
	private Texture image;

	public CustomColliderImage() {
		super();
		
		//this.editor = editor;
		setScaling(Scaling.stretch);
		//setImage(new TextureRegionDrawable(new TextureRegion(new Texture("images/img.jpg"))));
		//setSize(image.getWidth(),image.getHeight());
		//setSize(Gdx.graphics.getWidth() - Gdx.graphics.getWidth() / 4.0f,Gdx.graphics.getHeight() - Gdx.graphics.getHeight() / 10f);
		
	}

	public void setImage(Texture texture) {
		setImage(new TextureRegion(texture));
	}

	public void setImage(TextureRegion region) {
		setImage(new TextureRegionDrawable(region));
	}

	public void setImage(TextureRegionDrawable d) {
		image = d.getRegion().getTexture();
		setDrawable(d);
	}

	@Override
	public void setSize(float arg0, float arg1) {
		super.setSize(arg0, arg1);
		onImageUpdated();
	}

	@Override
	public void setDrawable(Drawable arg0) {
		super.setDrawable(arg0);
		//reSize();
	}

	private void reSize() {
		if (image != null) {
			targetWidth = Gdx.graphics.getWidth() * 0.7f;
			targetHeight = Gdx.graphics.getHeight() * 0.7f;

			imageHeight = image.getHeight();
			imageWidth = image.getWidth();

			float widthRatio = targetWidth / imageWidth;
			float heightRatio = targetHeight / imageHeight;
			float scaleRatio = Math.min(widthRatio, heightRatio);
			float newWidth = imageWidth * scaleRatio;
			float newHeight = imageHeight * scaleRatio;
			setSize(newWidth, newHeight);
		}
	}
	public Vector2 normalizePoint(float x, float y) {
		float adjustedX = x / getWidth();
		float adjustedY = y / getHeight();
		return new Vector2(adjustedX, adjustedY);
	}

	public Vector2 adjustedPoint(float x, float y) {
		float adjustedX = x * getWidth();
		float adjustedY = y * getHeight();
		return new Vector2(adjustedX, adjustedY);
	}

	public void onImageUpdated() {
		if (observer != null)
			observer.onImageUpdated(getWidth(), getHeight());
	}

	public void setObserver(CustomColliderEditor.SizeObserver observer) {
		this.observer = observer;
		onImageUpdated();
	}
	

	public void dispose() {
		image.dispose();
	}

} 
