package com.star4droid.star2d.Items;

import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import android.view.View;
import com.star4droid.star2d.Fragments.LibgdxFragment;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.template.Items.StageImp;
import android.util.AttributeSet;
import android.content.Context;
import android.widget.FrameLayout;
import java.util.ArrayList;
import com.star4droid.star2d.CodeEditor.MyIndexer;

public class Editor extends FrameLayout {
	private MyIndexer myIndexer;
	Runnable whenAppReady;
	private static Editor currentEditor;
	TestApp testApp;
	/*
	PropertySet.onChangeListener onChangeListener = new PropertySet.onChangeListener(){
		@Override
		public void onChange(String s, Object object) {
		//if(AUTO_SAVE) project.save(Editor.this);
		}
	};
	*/

	public Editor(Context context) {
		super(context);
	}

	public Editor(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	public Editor(Context context, AttributeSet attributeSet, int n) {
		super(context, attributeSet, n);
	}
	
	//initialize the view
	
	private void init(Project project) {
		removeAllViewsInLayout();
		currentEditor = this;
		FrameLayout frameLayout = new FrameLayout(getContext());
		testApp = new TestApp();
		testApp.setWhenAppReady(whenAppReady);
		frameLayout.setId(2);
		addView(frameLayout);
		/*
		libgdxFragment = new LibgdxFragment(testApp);
		ViewPager2 viewPager2 = new ViewPager2(getContext());
		viewPager2.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
		viewPager2.setBackgroundColor(android.graphics.Color.BLUE);
		viewPager2.setAdapter(new FragmentAdapter((AppCompatActivity)getContext(),libgdxFragment));
		addView(viewPager2);
		viewPager2.setFocusable(false);
		*/
		
		/*
		viewPager2.setOnTouchListener((v,ev)->{
			disableTouch(viewPager2);
			android.widget.Toast.makeText(getContext(),"disable work",1500).show();
			viewPager2.setOnTouchListener(null);
			return true;
		});
		*/
		//new Handler(Looper.getMainLooper()).postDelayed(()->disableTouch(viewPager2)
					//,2500);
		((AppCompatActivity)getContext()).getSupportFragmentManager().beginTransaction()
				.replace(frameLayout.getId(),new LibgdxFragment(testApp)).commit();
	}
	
	public void setWhenAppReady(Runnable runnable){
		this.whenAppReady = runnable;
		if(testApp!=null)
				testApp.setWhenAppReady(runnable);
	}
	
	public static void disableTouch(View view){
		if(view instanceof ViewGroup){
			ViewGroup viewGroup = (ViewGroup)view;
			view.setFocusable(false);
			viewGroup.requestDisallowInterceptTouchEvent(true);
			for(int x=0; x < viewGroup.getChildCount(); x++)
				disableTouch(viewGroup.getChildAt(x));
		}
	}
	
	public static void disableTouchParents(View view){
		android.view.ViewParent parent = view.getParent();
		int x=0;
		while(parent!=null&&x<=10){
			parent.requestDisallowInterceptTouchEvent(true);
			parent=parent.getParent();
			x++;
		}
	}
	
	public TestApp getApp(){
		return testApp;
	}
	
	public void setEditorReadyAction(Runnable runnable){
		testApp.setEditorReadyAction(runnable);
	}

	public void setIndexer(MyIndexer indexer){
		myIndexer = indexer;
	}

	public void setToCurrentEditor(){
		currentEditor = this;
	}
	
	public Actor getSelectedView(){
		return testApp.getEditor().getSelectedActor();
	}
	
	public void removeView(Actor actor){
		actor.remove();
	}
	
	public void selectView(Actor actor){
		testApp.getEditor().selectActor(actor);
	}
	
	public boolean selectByName(String name){
		return testApp.getEditor().selectByName(name);
	}
	
	public MyIndexer getIndexer(){
		return myIndexer;
	}
	
	public static Editor getCurrentEditor(){
		return currentEditor;
	}
		
	boolean propsSet = false;
	public enum ORIENATION {
		LANDSCAPE,PORTRAIT
	}
	
	public ORIENATION getOrienation(){
		return getLibgdxEditor().isLandscape()?ORIENATION.LANDSCAPE:ORIENATION.PORTRAIT;
	}
	
	public void setOrienation(ORIENATION or){
		testApp.setLandscape(or == ORIENATION.LANDSCAPE);
	}
	
	public String getScene() {
		return getLibgdxEditor().getScene();
	}

	public Editor setScene(String s) {
		getLibgdxEditor().setScene(s);
		return this;
	}
	
	public void centerCamera(){
		getLibgdxEditor().centerCamera();
	}
	
	String lastConfig="nothing...";
	private void updateConfig(){
		getLibgdxEditor().updateConfig();
	}
	
	public PropertySet<String,Object> getConfig(){
		return getLibgdxEditor().getConfig();
	}
	
	int tries=0;
	public void setSceneColor(String s){
		getLibgdxEditor().setSceneColor(s);
	}

	public void saveConfig(){
		getLibgdxEditor().saveConfig();
	}

	public void setProject(Project p) {
		init(p);
	}
	
	public void setProject(com.star4droid.star2d.Helpers.Project project){
		setProject(new Project(project.getPath()));
	}

	public void setScale(float sc) {
		getLibgdxEditor().setScale(sc);
	}

	public void setGridSize(float x, float y) {
		getLibgdxEditor().setGridSize(x,y);
	}

	public boolean isAutoSave() {
		return getLibgdxEditor().isAutoSave();
	}

	public void updateProperties() {}
	
	public LibgdxEditor getLibgdxEditor(){
		return testApp.getEditor();
	}
	
	public int getLastZ(){
		return getLibgdxEditor().getActors().size;
	}

	public void setLogicalWH(float width,float height){
		getLibgdxEditor().setLogicalWH(width,height);
	}

	public void showGrids(boolean b) {
		getLibgdxEditor().showGrids(b);
	}

	public void setRatioScale(float rs){
		getLibgdxEditor().setRatioScale(rs);
	}

	public float getRatioScale(){
		return getLibgdxEditor().getRatioScale();
	}

	public ArrayList<String> getBodiesList() {
		return getLibgdxEditor().getBodiesList();
	}

	public void updateChilds() {
		getLibgdxEditor().updateChilds();
	}
	
	public void setTouchMode(LibgdxEditor.TOUCHMODE mode) {
		getLibgdxEditor().setTouchMode(mode);
	}

	//public HashMap<String,String> Names = new HashMap<>();
	public String getName(Actor s) {
		return getLibgdxEditor().getName(s);
	}

	public String getSaveState() {
		return getLibgdxEditor().getSaveState();
	}

	public Project getProject() {
	    try {
	        if(getLibgdxEditor()==null) return null;
		    return getLibgdxEditor().getProject();
		} catch(Exception e){
	        return null;
	    }
	}

	public boolean canUndo() {
		return getLibgdxEditor().canUndo();
	}

	public boolean canRedo() {
		return getLibgdxEditor().canRedo();
	}

	public void undo() {
		getLibgdxEditor().undo();
	}

	public void redo() {
		getLibgdxEditor().redo();
	}

	public void load(String s) {
		getLibgdxEditor().load(s);
	}

	public void loadFromPath() {
		getLibgdxEditor().loadFromPath();
	}

	public void setEditorListener(EditorListener listener) {
		getLibgdxEditor().setEditorListener(listener == null ? null : new LibgdxEditor.EditorListener(){
			@Override
			public void onUpdateUndoRedo() {
				new Handler(Looper.getMainLooper()).post(()->{
					listener.onUpdateUndoRedo();
				});
			}

			@Override
			public void onBodySelected() {
				new Handler(Looper.getMainLooper()).post(()->{
					listener.onBodySelected();
				});
			}
			
		});
		editorListener = listener;
	}

	EditorListener editorListener;
	OnPickListener onPickListener;
	
	public void setOnPick(OnPickListener pick){
		getLibgdxEditor().setOnPick((x,y)->{
			new Handler(Looper.getMainLooper()).post(()->{
				pick.onPick(x,y);
			});
		});
	}

	public interface EditorListener {
		public void onUpdateUndoRedo();
		public void onBodySelected();
	}
	
	public interface OnPickListener {
		public void onPick(float x,float y);
	}
	
	public static class FragmentAdapter extends FragmentStateAdapter {
		Fragment fragment;
		public FragmentAdapter(AppCompatActivity ctx,Fragment fragment){
			super(ctx);
			this.fragment = fragment;
		}
		@Override
        public Fragment createFragment(int position) {
            return fragment;
        }

        @Override
        public int getItemCount() {
            return 1;
        }
	}
}