package com.star4droid.star2d.suda;

import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.EditorShell;

import java.io.File;
import java.util.function.Consumer;

/** Connects the SUDA3D dialogs to the open editor: current project, phone picker, adding results to the scene. */
public final class EditorEnv {
    private EditorEnv() {}

    public static void install(EditorShell shell) {
        QuickGen.env = new QuickGen.Env() {
            @Override public String project() { return shell.projectPath(); }

            @Override public void pickFromPhone(Consumer<File> got) {
                File tmp = new File(shell.activity().getCacheDir(), "suda_pick");
                com.star4droid.star2d.Helpers.FileUtil.deleteFile(tmp.getPath());
                tmp.mkdirs();
                shell.host().importFiles(tmp.getPath(), () -> {
                    File[] l = tmp.listFiles();
                    if (l != null) for (File f : l) if (Store.isImage(f.getName())) { got.accept(f); return; }
                    com.star4droid.star2d.ui.Ui.toast(shell.activity(), com.star4droid.star2d.ui.Tr.t("s3d_pick_image", "Choose a picture"));
                });
            }

            @Override public boolean canAddToScene(File saved) {
                return Store.isModel(saved.getName()) && Bridge.ready() && Bridge.editor() != null && Bridge.editor().is3DScene()
                        && !saved.getName().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(fbx|usdz|stl)$");
            }

            @Override public void addToScene(File saved) {
                Bridge.gl(() -> Bridge.controls().actionAdd3D("OBJ3D", "MODEL", "/" + saved.getName()));
            }
        };
    }
}
