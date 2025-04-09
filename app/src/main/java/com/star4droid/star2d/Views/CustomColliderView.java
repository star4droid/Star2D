package com.star4droid.star2d.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;
import com.star4droid.star2d.Helpers.SwipeHelper;
import com.star4droid.star2d.Utils;
import java.util.ArrayList;

public class CustomColliderView extends AppCompatImageView {
	Paint paint = new Paint(){
		{
	        setColor(Color.GREEN);
            setStrokeWidth(2);
            setStyle(Paint.Style.STROKE);
            setStrokeCap(Paint.Cap.ROUND);
            setTextSize(30);
            setAntiAlias(true);
            setStrokeJoin(Paint.Join.ROUND);
            setDither(true);
            setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
		}
	};
	Paint whitePaint = new Paint(){
		{
			setColor(Color.WHITE);
            setStrokeWidth(2);
            setAntiAlias(true);
            setStyle(Paint.Style.STROKE);
            setStrokeCap(Paint.Cap.ROUND);
            setTextSize(30);
            setStrokeJoin(Paint.Join.ROUND);
            setDither(true);
            setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
		}
	};
	int selected = 0;
	Path path = new Path();
	ArrayList<PointF> points = new ArrayList<>();
	public CustomColliderView(Context context){
		super(context);
		init();
	}
	public CustomColliderView(Context context,AttributeSet attr){
		super(context,attr);
		init();
	}
	public CustomColliderView(Context context,AttributeSet attr, int i){
		super(context,attr,i);
		init();
	}
	
	private void init(){
	    setScaleType(ScaleType.FIT_XY);
		SwipeHelper.onTouch(this,(dx,dy)->{
			try {
				float px = points.get(selected).x, py = points.get(selected).y;
				float x = px * getMeasuredWidth(), y = py * getMeasuredHeight();
				float resultX = (x + dx)/getMeasuredWidth(), resultY = (y + dy)/getMeasuredHeight();
				points.get(selected).set(Math.max(0,resultX),Math.max(0,resultY));
				invalidate();
			} catch(Exception ex){}
		});
	}
	
	@Override
	protected void onDraw(Canvas cv) {
		super.onDraw(cv);
		//cv.setAntiAlias(true);
		if(points.size()==0) return;
		path.reset();
		for(int i = 0; i < points.size(); i++){
			float x = points.get(i).x * getMeasuredWidth(), y = points.get(i).y * getMeasuredHeight();
			
			if(i == selected)
				cv.drawCircle(x,y,30,paint);
			if(i == 0)
				path.moveTo(x,y);
			else path.lineTo(x,y);
			cv.drawText((i+1) +"",x - 5,y - 5,whitePaint);
		}
		//if(points.size() >= 3) path.lineTo(points.get(0).x * getMeasuredWidth(), points.get(0).y * getMeasuredHeight());
		cv.drawPath(path,paint);
		invalidate();
		//draw grids...
		/*
		for(int x = 0; x < getMeasuredWidth(); x+=35)
		    cv.drawLine(x,0,x,getMeasuredHeight(),paint);
		for(int y = 0; y < getMeasuredHeight(); y+=35)
		    cv.drawLine(0,y,getMeasuredWidth(),y,paint);
		*/
	}
	
	public void setSelected(int pos){
		this.selected = pos;
		invalidate();
	}
	
	public ArrayList<PointF> getPoints(){
		return points;
	}
	
	public void deletePoint(int pos){
		points.remove(pos);
		if(selected == pos)
			selected = 0;
		invalidate();
	}
	
	public void deleteSelected(){
		deletePoint(selected);
	}
	
	public void addPoint(float x,float y){
		points.add(new PointF(x,y));
		invalidate();
	}
	
	public void setPointsFromString(String pointsStr){
		for(String pointStr:pointsStr.split("-")){
    	    try {
				float px = Utils.getFloat(pointStr.split(",")[0]);
    	    	float py = Utils.getFloat(pointStr.split(",")[1]);
				addPoint(px,py);
			} catch(Exception e){}
		}
	}
	
	public String getPointsStr(){
		String result="";
		for(PointF pointF:points){
			result += (result.equals("")?"":"-")+pointF.x+","+pointF.y;
		}
		return result;
	}
	
	@Override
	public String toString() {
		return getPointsStr();
	}
}