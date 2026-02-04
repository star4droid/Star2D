package com.star4droid.star2d.editor;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.tyron.builder.project.Project;
import com.star4droid.star2d.evo.R;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.code.language.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import java.nio.charset.StandardCharsets;

public class CodeEditorActivity extends AppCompatActivity {

    private CodeEditor mEditor;
    private Project mProject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.code_editor);

        mEditor = findViewById(R.id.editor);

        String filePath = getIntent().getStringExtra("path");
        if (filePath == null) {
            finish();
            return;
        }
        File file = new File(filePath);
        
        try {
            mEditor.setText(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

        File projectRoot = file.getParentFile(); 
        if (projectRoot != null) {
             mProject = new Project(projectRoot);
             ProjectManager.getInstance().setCurrentProject(mProject);
             try {
                mProject.open();
             } catch (Exception e) {
                 e.printStackTrace();
             }
        }

        SimpleRosemoeEditor simpleEditor = new SimpleRosemoeEditor(mEditor, file);
        JavaLanguage javaLanguage = new JavaLanguage(simpleEditor);
        mEditor.setEditorLanguage(javaLanguage);
    }
}
