package com.star4droid.star2d.Fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.android.material.elevation.SurfaceColors;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.Utils;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;

public class BodiesFragment extends Fragment {
  GridView grid;
  GridViewAdapter gridViewAdapter;
  Editor editor;

  public BodiesFragment() {}

  public BodiesFragment(Editor ed) {
    editor = ed;
    if (gridViewAdapter != null) update();
  }

  @Override
  public View onCreateView(LayoutInflater arg0, ViewGroup arg1, Bundle arg2) {
    grid = new GridView(getContext());
    // grid.setLayoutParams(new
    // LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,999999));
    grid.setHorizontalSpacing(1);
    grid.setVerticalSpacing(1);
    grid.setNumColumns(1);
    ArrayList<HashMap<String, Object>> arrayList = new ArrayList<>();
    gridViewAdapter = new GridViewAdapter(arrayList);
    grid.setAdapter(gridViewAdapter);
    if (editor != null) update();
    return grid;
  }

  public void update() {
    if (gridViewAdapter != null && editor != null) {
      gridViewAdapter.getArrayList().clear();
      try {
    	  for(Actor actor:editor.getLibgdxEditor().getActors()){
    		  if (!Utils.isEditorItem(actor)) continue;
    		  HashMap<String, Object> hash = new HashMap<>();
            PropertySet<String, Object> propertySet = PropertySet.getPropertySet(actor);
            if(propertySet==null) continue;
    		if (!propertySet.containsKey("name")) continue;
            if (!propertySet.getString("parent").equals("")) continue;
            hash.put("name", propertySet.getString("name"));
            hash.put("item", actor);
            gridViewAdapter.getArrayList().add(hash);
			//add item childs after it...
            for (PropertySet set : getChilds(propertySet, null)) {
              HashMap<String, Object> hashMap = new HashMap<>();
              hashMap.put("name", set.getString("name"));
              hashMap.put("item", set);
              gridViewAdapter.getArrayList().add(hashMap);
            }
    	  }
	  } catch(Exception exc){}
      gridViewAdapter.notifyDataSetChanged();
    }
  }

  private ArrayList<PropertySet> getChilds(
      PropertySet propertySet, ArrayList<PropertySet> arrayList) {
    if (arrayList == null) arrayList = new ArrayList<>();
    for (int x = 0; x < propertySet.getChilds().size(); x++) {
      arrayList.add((PropertySet) propertySet.getChilds().get(x));
      getChilds((PropertySet) propertySet.getChilds().get(x), arrayList);
    }
    return arrayList;
  }

  private class GridViewAdapter extends BaseAdapter {
    ArrayList<HashMap<String, Object>> arrayList;

    public GridViewAdapter(ArrayList<HashMap<String, Object>> list) {
      arrayList = list;
    }

    public ArrayList<HashMap<String, Object>> getArrayList() {
      return arrayList;
    }

    @Override
    public int getCount() {
      return arrayList.size();
    }

    @Override
    public Object getItem(int position) {
      return arrayList.get(position).get("item");
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    public PropertySet<String, Object> getProperty(int position) {
      if (getItem(position) instanceof PropertySet)
        return (PropertySet<String, Object>) getItem(position);
      return ((EditorItem) getItem(position)).getPropertySet();
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
      convertView = LayoutInflater.from(getContext()).inflate(R.layout.body_image_card, null);
	  
      int padding = 0;
      PropertySet pst = getProperty(position);
      while (pst.getParent() != null) {
        pst = pst.getParent();
        padding++;
      }
      convertView
          .findViewById(R.id.linear)
          .setPadding(Utils.convertPixelsToDp(getContext(), 20) * padding, 0, 0, 0);
      if (getProperty(position)
          .getString("name")
          .equals(editor.getSelectedView()==null?"":PropertySet.getPropertySet(editor.getSelectedView()).getString("name")))
        convertView
            .findViewById(R.id.linear)
            .setBackgroundColor(/*SurfaceColors.SURFACE_4.getColor(getContext())*/android.graphics.Color.BLACK);
      ImageView lock = convertView.findViewById(R.id.lock);
      ImageView hide = convertView.findViewById(R.id.hide);
      lock.setImageResource(
          getProperty(position).getString("lock").equals("true")
              ? R.drawable.lock
              : R.drawable.unlock);
      hide.setImageResource(
          getProperty(position).getString("Visible").equals("true")
              ? R.drawable.ic_visible
              : R.drawable.ic_invisible);

      lock.setOnClickListener(
          v -> {
            switchLock(position, lock);
          });
      hide.setOnClickListener(
          v -> {
            switchVisibility(position, hide);
          });

      convertView
          .findViewById(R.id.menu_btn)
          .setOnClickListener(
              view -> {
                final PopupMenu popupMenu = new PopupMenu(getContext(), view);
                popupMenu.getMenuInflater().inflate(R.menu.body_events_menu, popupMenu.getMenu());

                popupMenu.setOnMenuItemClickListener(
                    item -> {
                      int itemId = item.getItemId();

                      if (itemId == R.id.copy) {
                        Utils.showMessage(getContext(),"Copy Not Supported For Now...");
                      } else if (itemId == R.id.delete) {
                        ((Actor)getItem(position)).remove();
                      } else if (itemId == R.id.lock) {
                        switchLock(position, lock);
                      } else {
                        switchVisibility(position, hide);
                      }

                      popupMenu.dismiss();
                      return true;
                    });
                // Utils.hideSystemUi(popupMenu.getMenu().getItem(0).getActionView());
                popupMenu.show();
              });
      ImageView image = (ImageView) convertView.findViewById(R.id.image);
      image.setImageDrawable(getContext().getDrawable(R.drawable.icon));
      //if (getProperty(position).containsKey("image"))
        //Utils.setImageFromFile(image, editor.getProject().getImagesPath() + getProperty(position).getString("image"));
      TextView name = (TextView) convertView.findViewById(R.id.name);
      name.setText(getProperty(position).getString("name"));
      convertView
          .findViewById(R.id.linear)
          .setOnClickListener(
              view -> {
                if (getItem(position) instanceof PropertySet) {
                  editor.selectByName(((PropertySet)getItem(position)).getString("name"));
                } else if(getItem(position) instanceof Actor) editor.selectView((Actor) getItem(position));
                notifyDataSetChanged();
              });
      return convertView;
    }

    private void switchLock(int position, ImageView imageView) {
      String to = getProperty(position).getString("lock").equals("true") ? "false" : "true";
      getProperty(position).put("lock", to);
      ((EditorItem) getItem(position)).update();
      if (imageView == null) notifyDataSetChanged();
      else imageView.setImageResource(to.equals("true") ? R.drawable.lock : R.drawable.unlock);
    }

    private void switchVisibility(int position, ImageView imageView) {
      String to = getProperty(position).getString("Visible").equals("true") ? "false" : "true";
      getProperty(position).put("Visible", to);
      ((EditorItem) getItem(position)).update();
      if (imageView == null) notifyDataSetChanged();
      else
        imageView.setImageResource(
            to.equals("true") ? R.drawable.ic_visible : R.drawable.ic_invisible);
    }
  }

  public static String getRealNameAndNum(final String _str) {
    try {
      String str = _str;
      String nums = "1234567890";
      String out = "";
      while (nums.contains("" + str.charAt(str.length() - 1))) {
        out = str.charAt(str.length() - 1) + out;
        str = str.substring(0, str.length() - 1);
      }
      if (out.equals("")) out = "1";
      return str + " " + out;
    } catch (Exception e) {
      return (_str);
    }
  }
}
