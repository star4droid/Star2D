package com.star4droid.star2d.Helpers;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.evo.R;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class AndroidStatics {
    public static RecyclerView recyclerView;
    public static BodiesAdapter adapter;
    public static GridView fileGridView;
    public static FilesAdapter filesAdapter;
    public static File currentPath;
    public static File rootPath;

    public static void init(RecyclerView rv, GridView gv, EditText bodySearch, EditText fileSearch) {
        recyclerView = rv;
        adapter = new BodiesAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(rv.getContext()));
        recyclerView.setAdapter(adapter);

        fileGridView = gv;
        filesAdapter = new FilesAdapter();
        fileGridView.setAdapter(filesAdapter);

        if (bodySearch != null) {
            bodySearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (fileSearch != null) {
            fileSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filesAdapter.filter(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    public static void setPaths(String root) {
        if (root == null || root.isEmpty()) return;
        rootPath = new File(root);
        currentPath = rootPath;
        if (filesAdapter != null) filesAdapter.refresh();
    }

    public static void updateBodiesList() {
        if (adapter != null && Editor.getCurrentEditor() != null) {
            adapter.update(Editor.getCurrentEditor());
        }
    }

    public static class BodiesAdapter extends RecyclerView.Adapter<BodiesAdapter.ViewHolder> {
        private ArrayList<HashMap<String, Object>> fullList = new ArrayList<>();
        private ArrayList<HashMap<String, Object>> filteredList = new ArrayList<>();
        private String searchText = "";

        public void update(Editor editor) {
            fullList.clear();
            if (editor.getLibgdxEditor() == null) return;
            for (Actor actor : editor.getLibgdxEditor().getActors()) {
                if (!(actor instanceof EditorItem)) continue;
                PropertySet<String, Object> propertySet = PropertySet.getPropertySet(actor);
                if (propertySet == null || !propertySet.getString("parent").equals("")) continue;

                addWithChildren(actor, 0);
            }
            filter(searchText);
        }

        public void filter(String text) {
            searchText = text.toLowerCase(Locale.ROOT);
            filteredList.clear();
            if (searchText.isEmpty()) {
                filteredList.addAll(fullList);
            } else {
                for (HashMap<String, Object> item : fullList) {
                    if (item.get("name").toString().toLowerCase(Locale.ROOT).contains(searchText)) {
                        filteredList.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        private void addWithChildren(Actor actor, int depth) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("name", actor.getName());
            map.put("depth", depth);
            map.put("actor", actor);
            fullList.add(map);

            PropertySet<String, Object> propertySet = PropertySet.getPropertySet(actor);
            for (PropertySet childSet : propertySet.getChilds()) {
                Actor childActor = Editor.getCurrentEditor().getLibgdxEditor().findActor(childSet.getString("name"));
                if (childActor != null) {
                    addWithChildren(childActor, depth + 1);
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_csv, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HashMap<String, Object> item = filteredList.get(position);
            holder.itemName.setText(item.get("name").toString());
            int depth = (int) item.get("depth");
            
            ViewGroup.LayoutParams params = holder.startSpace.getLayoutParams();
            params.width = depth * (int)(20 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
            holder.startSpace.setLayoutParams(params);
            
            Actor actor = (Actor) item.get("actor");
            Actor selected = Editor.getCurrentEditor() != null ? Editor.getCurrentEditor().getSelectedView() : null;
            
            if (selected != null && selected.getName().equals(actor.getName())) {
                holder.itemView.setBackgroundColor(holder.itemView.getContext().getColor(R.color.selected_color));
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            holder.itemView.setOnClickListener(v -> {
                if (Editor.getCurrentEditor() != null) {
                    Editor.getCurrentEditor().selectView(actor);
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView itemName;
            View startSpace;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                itemName = itemView.findViewById(R.id.item_name);
                startSpace = itemView.findViewById(R.id.start_space);
            }
        }
    }

    public static class FilesAdapter extends BaseAdapter {
        private List<File> files = new ArrayList<>();
        private List<File> filteredFiles = new ArrayList<>();
        private String searchText = "";

        public void refresh() {
            files.clear();
            if (currentPath == null || !currentPath.exists()) return;
            
            if (rootPath != null && !currentPath.getAbsolutePath().equals(rootPath.getAbsolutePath())) {
                files.add(null); // Back button
            }

            File[] list = currentPath.listFiles();
            if (list != null) {
                for (File f : list) {
                    files.add(f);
                }
            }
            filter(searchText);
        }

        public void filter(String text) {
            searchText = text.toLowerCase(Locale.ROOT);
            filteredFiles.clear();
            for (File f : files) {
                if (f == null) {
                    filteredFiles.add(null);
                    continue;
                }
                if (searchText.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(searchText)) {
                    filteredFiles.add(f);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return filteredFiles.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_custom_view, parent, false);
            }
            
            ImageView icon = convertView.findViewById(R.id.fileIcon);
            TextView name = convertView.findViewById(R.id.fileName);
            
            File file = filteredFiles.get(position);
            if (file == null) {
                name.setText("...");
                icon.setImageResource(R.drawable.folder);
            } else {
                name.setText(file.getName());
                if (file.isDirectory()) {
                    icon.setImageResource(R.drawable.folder);
                } else {
                    String path = file.getAbsolutePath().toLowerCase();
                    if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                        Glide.with(convertView.getContext()).load(file).into(icon);
                    } else {
                        icon.setImageResource(R.drawable.save_icon);
                    }
                }
            }

            convertView.setOnClickListener(v -> {
                if (file == null) {
                    currentPath = currentPath.getParentFile();
                    refresh();
                } else if (file.isDirectory()) {
                    currentPath = file;
                    refresh();
                } else {
                    handleFileOpen(file, v.getContext());
                }
            });

            return convertView;
        }

        private void handleFileOpen(File file, Context context) {
            String path = file.getAbsolutePath();
            if (path.endsWith(".java")) {
                if (context instanceof com.star4droid.star2d.EditorActivity) {
                    ((com.star4droid.star2d.EditorActivity) context).openJava(path);
                }
            } else if (path.endsWith(".json") && file.getParentFile() != null && file.getParentFile().getName().equals("anims")) {
                try {
                    java.lang.reflect.Method method = context.getClass().getDeclaredMethod("openAnimation", String.class);
                    method.setAccessible(true);
                    method.invoke(context, path);
                } catch (Exception e) {}
            }
        }
    }
}
