package com.star4droid.star2d.Helpers;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.app.AlertDialog;

public class SwipeHelper {
	public static void useViewToSwipe(final View swipe,final View target,final SwipeType type,final int minValue,final int maxValue){
		swipe.setOnTouchListener(new View.OnTouchListener(){
			float initialTouchX=0;
			float initialTouchY=0;
			int initialX=0;
			int initialY;
			float startX=0,startY=0;
			public boolean onTouch(View view, MotionEvent motionEvent) {
				int n = motionEvent.getAction();
				if(n==MotionEvent.ACTION_DOWN){
					startX= motionEvent.getRawX();
					startY= motionEvent.getRawY();
				}
				
				if(n==MotionEvent.ACTION_UP){
					float x=startX-motionEvent.getRawX(), y=startY-motionEvent.getRawY();
					float dist = (float)Math.sqrt(x*x+y*y);
					if(dist<=50) {
						float ex = motionEvent.getX(),ey= motionEvent.getY(),vh = view.getMeasuredHeight(),vw = view.getMeasuredWidth();
						boolean bx = ((ex>=0)&&(ex<=vw));
						boolean by = ((ey>=0)&&(ey<=vh));
						if(!(bx&&by)) return false;
						final boolean left_right = type==SwipeType.LEFT_RIGHT||type==SwipeType.RIGHT_LEFT;
						//sw = start width, ew = end width
						int sw = (left_right)?target.getMeasuredWidth():target.getMeasuredHeight(),ew=(left_right)?view.getContext().getResources().getDisplayMetrics().widthPixels/4:view.getContext().getResources().getDisplayMetrics().heightPixels/3;
						if(left_right&&ew<=target.getMeasuredWidth()){
							ew=1;
						} else if(ew<=target.getMeasuredHeight()&&(!left_right)){
							ew=1;
						}
						ValueAnimator va = ValueAnimator.ofInt(sw,ew);
						va.setDuration(300);
						va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
							@Override
							public void onAnimationUpdate(ValueAnimator valueAnimator) {
								if(left_right){
								target.getLayoutParams().width = (int)valueAnimator.getAnimatedValue();
								} else target.getLayoutParams().height = (int)valueAnimator.getAnimatedValue();
								target.setLayoutParams(target.getLayoutParams());
							}
						});
						va.start();
					}
				}
				if (n != 0) {
					if (n != 2) {
						return true;
					}
					if(type==SwipeType.LEFT_RIGHT||type==SwipeType.RIGHT_LEFT){
						float div=(type==SwipeType.LEFT_RIGHT)?1:(-1);
						int xx = this.initialX + (int)((motionEvent.getRawX() - this.initialTouchX)*div);
						if(xx<minValue) xx=minValue;
						if(xx>maxValue) xx=maxValue;
						target.getLayoutParams().width = xx;
						target.setLayoutParams(target.getLayoutParams());
						} else {
						float div=(type==SwipeType.TOP_DOWN)?1:(-1);
						int yy = this.initialY + (int)((motionEvent.getRawY() - this.initialTouchY)*div);
						if(yy<minValue) yy=minValue;
						if(yy>maxValue) yy=maxValue;
						target.getLayoutParams().height = yy;
						target.setLayoutParams(target.getLayoutParams());
					}
					
					return true;
				}
				this.initialX = target.getMeasuredWidth();//start x
				this.initialY = target.getMeasuredHeight();
				this.initialTouchX = motionEvent.getRawX();
				this.initialTouchY = motionEvent.getRawY();
				return false;
			}
		});
	}
	
	public enum SwipeType {
		LEFT_RIGHT,RIGHT_LEFT,
		TOP_DOWN,DOWN_TOP
	}
	
	public static void onTouch(View view,OnChangeListener onChangeListener){
		view.setOnTouchListener(new View.OnTouchListener(){
			float startX=0,startY=0;
			@Override
			public boolean onTouch(View v, MotionEvent motionEvent) {
				int n = motionEvent.getAction();
				if(n==MotionEvent.ACTION_DOWN){
					startX= motionEvent.getRawX();
					startY= motionEvent.getRawY();
				}
				if(n==MotionEvent.ACTION_MOVE){
					int dx = (int)(motionEvent.getRawX()-startX);
					int dy = (int)(motionEvent.getRawY()-startY);
					onChangeListener.onChange(dx,dy);
					//String path = FileUtil.getPackageDataDir(v.getContext())+"/touch.txt";
					//FileUtil.writeFile(path,FileUtil.readFile(path)+"\ndx : "+dx+", dy : "+dy);
					startX = motionEvent.getRawX();
					startY = motionEvent.getRawY();
				}
			    return true;
			}
			
		});
	}
	
	public static void dragDialogBy(View view,AlertDialog dialog){
		onTouch(view,(dx,dy)->{
			try {
				setXY(dialog,getX(dialog)+dx,getY(dialog)+dy);
			} catch(Exception ex){}
		});
	}
	
	public static void scaleDialogBy(View view,AlertDialog dialog){
		onTouch(view,(dx,dy)->{
			try {
				setWH(dialog,getW(dialog)+dx,getH(dialog)+dy);
			} catch(Exception ex){}
		});
	}
	
	public static void scaleViewBy(View scaler,OnChangeListener onChangeListener,View... targets){
		onTouch(scaler,(dx,dy)->{
			for(View target:targets)
				try {
					target.getLayoutParams().width+=dx;
					if(target.getLayoutParams().width<100)
						target.getLayoutParams().width=100;
					target.getLayoutParams().height+=dy;
					if(target.getLayoutParams().height<100)
						target.getLayoutParams().height=100;
					target.setLayoutParams(target.getLayoutParams());
				} catch(Exception ex){}
				if(onChangeListener!=null) onChangeListener.onChange(dx,dy);
		});
	}
	
	public static int getX(AlertDialog dialog){
	  return dialog.getWindow().getAttributes().x;
    }
  
  public static int getY(AlertDialog dialog){
	  return dialog.getWindow().getAttributes().y;
  }
  
  public static int getW(AlertDialog dialog){
	  return dialog.getWindow().getDecorView().getMeasuredWidth();
    }
  
  public static int getH(AlertDialog dialog){
	  return dialog.getWindow().getDecorView().getMeasuredHeight();
  }
	
	public static void setXY(AlertDialog dialog,int x,int y){
	  dialog.getWindow().getAttributes().x = x;
	  dialog.getWindow().getAttributes().y = y;
	  //dialog.getWindow().getWindowManager().updateViewLayout(view,dialog.getWindow().getAttributes());
	  dialog.getWindow().setAttributes(dialog.getWindow().getAttributes());
    }
	
	public static void setWH(AlertDialog dialog,int width,int height){
	  dialog.getWindow().getAttributes().width = width;
	  dialog.getWindow().getAttributes().height = height;
	  //dialog.getWindow().getWindowManager().updateViewLayout(view,dialog.getWindow().getAttributes());
	  dialog.getWindow().setAttributes(dialog.getWindow().getAttributes());
    }
	
	public interface OnChangeListener {
		public void onChange(int xChange,int yChange);
	}
}