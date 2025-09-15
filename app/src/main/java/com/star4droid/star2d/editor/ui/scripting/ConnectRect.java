package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImage;

public class ConnectRect extends VisImage {
	Drawable down,def;
	boolean isConnector = true,drawLine = false;
	Vector2 lineTo = new Vector2();
	ShapeRenderer shapeRenderer;
	ConnectRect connectedRect;
	public ConnectRect(VisualScripting visualScripting){
		super(VisUI.getSkin().getDrawable("white"));
		this.def = getDrawable();
		this.shapeRenderer = visualScripting.shapeRenderer;
		this.down = VisUI.getSkin().getDrawable("list-selection");
		addListener(new ClickListener(){
			//Vector2 lastPoint = new Vector2();
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				setDrawable(down);
				//lastPoint.set(x,y);
				return super.touchDown(event,x,y,pointer,button);
			}
			
			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer) {
				super.touchDragged(event, x, y, pointer);
				if(isConnector && !Gdx.input.isTouched(1)){
					//float dx = x - lastPoint.x,
						//dy = y - lastPoint.y;
					//lastPoint.set(x,y);
					lineTo.set(x,y);
					drawLine = true;
				}
			}

			@Override
			public void touchUp(InputEvent arg0, float arg1, float arg2, int arg3, int arg4) {
				super.touchUp(arg0, arg1, arg2, arg3, arg4);
				setDrawable(def);
			}

		});
	}
	
	public ConnectRect setIsConnector(boolean b){
		isConnector = b;
		return this;
	}
	
	public void setSelection(boolean b){
		setDrawable(b ? down : def);
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
		if(drawLine || connectedRect != null){
			shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
			shapeRenderer.setTransformMatrix(batch.getTransformMatrix());
			shapeRenderer.setColor(Color.WHITE);
			shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
			Gdx.gl.glLineWidth(3f);
		}
		if(drawLine){
			shapeRenderer.line(getX()+getWidth()*0.5f,getY()+getHeight()*0.5f,lineTo.x,lineTo.y);
		}
		if(connectedRect!=null){
			shapeRenderer.line(getX()+getWidth()*0.5f,getY()+getHeight()*0.5f,connectedRect.getX()+connectedRect.getWidth()*0.5f,connectedRect.getHeight()*0.5f);
		}
		if(drawLine || connectedRect!=null){
			shapeRenderer.end();
			Gdx.gl.glLineWidth(1f);
		}
	}
}