package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** "Add element" sheet with a described card per element type. */
public class AddItemPanel implements PanelHost.Panel {
    private final EditorShell shell;

    public AddItemPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("ui_add_element", "Add element"); }
    @Override public int icon() { return R.drawable.ic_s2_add; }

    // {translation key, english name, type for icon, english description, arabic description}
    private static final String[][] BODIES = {
            {"boxBody", "Box", "Box", "A rectangle with physics. Players, walls, platforms.", "مستطيل بفيزياء: لاعب، جدار، منصة."},
            {"circleBody", "Circle", "Circle", "A round body: balls, wheels, coins.", "جسم دائري: كرة، عجلة، عملة."},
            {"textItem", "Text", "Text", "Show text: score, titles, messages.", "عرض نص: النقاط، العناوين، الرسائل."},
            {"joystickItem", "Joystick", "Joystick", "On-screen joystick to control the player.", "عصا تحكم على الشاشة لتحريك اللاعب."},
            {"progressBar", "Progress bar", "Progress", "Health bars, loading bars, timers.", "شريط الصحة أو التحميل أو المؤقت."},
            {"customBody", "Custom shape", "Custom", "Body with a custom collider shape (polygon).", "جسم بشكل تصادم مخصص (مضلع)."},
            {"particleEffect", "Particles", "Particle", "Fire, smoke, sparks, rain effects.", "تأثيرات: نار، دخان، شرر، مطر."},
            {"camera", "Camera", "Camera", "Controls what part of the world is visible.", "تتحكم في الجزء الظاهر من العالم."},
            {"tiledMap", "Tiled map", "Map", "Load a level made with Tiled (.tmx).", "تحميل مرحلة مصممة ببرنامج Tiled."},
            {"groupItem", "Group", "Group", "Empty container: move, rotate, hide or delete several elements together.", "حاوية فارغة: حرّك أو دوّر أو أخفِ أو احذف عدة عناصر معاً."},
            {"uiHolder", "UI Holder", "UIHolder", "Shows a screen made in the UI Maker (menus, HUD, dialogs) as one element.", "يعرض واجهة صنعتها في UI Maker (قوائم، HUD، نوافذ) كعنصر واحد."},
    };
    private static final String[][] LIGHTS = {
            {"pointLight", "Point light", "Light", "Light that shines in all directions.", "ضوء ينير في كل الاتجاهات."},
            {"directionalLight", "Directional light", "Light", "Sun-like light from one direction.", "ضوء كالشمس من اتجاه واحد."},
            {"coneLight", "Cone light", "Light", "Flashlight / spotlight cone.", "ضوء مخروطي كالكشاف."},
    };

    // 3D scenes: {translation key, english, icon type, english description, arabic description, kind, preset}
    private static final String[][] SHAPES_3D = {
            {"s3_cube", "Cube", "Cube", "Box: walls, crates, floors, buildings.", "مكعب: جدران، صناديق، أرضيات، مبانٍ.", "OBJ3D", "CUBE"},
            {"s3_sphere", "Sphere", "Sphere", "Balls, planets, heads.", "كرات، كواكب، رؤوس.", "OBJ3D", "SPHERE"},
            {"s3_cylinder", "Cylinder", "Cylinder", "Pillars, barrels, wheels.", "أعمدة، براميل، عجلات.", "OBJ3D", "CYLINDER"},
            {"s3_capsule", "Capsule", "Cylinder", "Characters and players (best collider for walking).", "شخصيات ولاعبون (أفضل شكل تصادم للمشي).", "OBJ3D", "CAPSULE"},
            {"s3_cone", "Cone", "Cube", "Cones, roofs, trees.", "مخاريط، أسقف، أشجار.", "OBJ3D", "CONE"},
            {"s3_plane", "Plane", "Cube", "Flat ground or floor (10 x 10 m).", "أرضية مسطحة (10 × 10 م).", "OBJ3D", "PLANE"},
            {"s3_ramp", "Ramp", "Cube", "Slope to walk or drive up.", "منحدر للصعود مشياً أو بالسيارة.", "OBJ3D", "RAMP"},
            {"s3_stairs", "Stairs", "Cube", "Steps (count in Steps).", "درج (عدد الدرجات في Steps).", "OBJ3D", "STAIRS"},
            {"s3_torus", "Torus", "Sphere", "Rings, tires, donuts.", "حلقات، إطارات، كعك.", "OBJ3D", "TORUS"},
            {"s3_pyramid", "Pyramid", "Cube", "Pyramids, spikes.", "أهرامات، أشواك.", "OBJ3D", "PYRAMID"},
            {"s3_hemisphere", "Hemisphere", "Sphere", "Domes, bowls.", "قباب، أوعية.", "OBJ3D", "HEMISPHERE"},
            {"s3_tube", "Tube", "Cylinder", "Pipes, tunnels.", "أنابيب، أنفاق.", "OBJ3D", "TUBE"},
            {"s3_quad", "Quad", "Cube", "Flat picture (signs, decals, billboards).", "صورة مسطحة (لافتات، ملصقات).", "OBJ3D", "QUAD"},
            {"s3_disc", "Disc", "Sphere", "Round flat plate.", "قرص مسطح دائري.", "OBJ3D", "DISC"},
    };
    private static final String[][] SPECIAL_3D = {
            {"s3_model", "3D model", "Model", "Import a .glb / .gltf model (with animations).", "استورد نموذج ‎.glb / .gltf (مع الحركات).", "OBJ3D", "MODEL"},
            {"s3_terrain", "Terrain", "Terrain", "Hills and valleys: sculpt, paint, trees (Edit terrain).", "تلال ووديان: نحت وتلوين وأشجار (تعديل التضاريس).", "OBJ3D", "TERRAIN"},
            {"s3_gen_terrain", "Generated world", "Terrain", "Random terrain with trees, grass, a road and water.", "أرض عشوائية بأشجار وعشب وطريق وماء.", "OBJ3D", "TERRAIN_GEN"},
            {"s3_water", "Water", "Water", "Animated water with waves; things can float.", "ماء متحرك بأمواج، والأشياء تطفو عليه.", "OBJ3D", "WATER"},
            {"s3_empty", "Empty", "Cube", "Invisible point: parent, spawn point, target.", "نقطة غير مرئية: أب، نقطة ظهور، هدف.", "OBJ3D", "EMPTY"},
    };
    private static final String[][] LIGHTS_3D = {
            {"s3_point", "Point light", "LIGHT3D", "Bulb: lights all around it.", "مصباح: يضيء ما حوله.", "LIGHT3D", "POINT"},
            {"s3_spot", "Spot light", "LIGHT3D", "Flashlight, street lamp cone.", "كشاف، مخروط ضوء عمود الإنارة.", "LIGHT3D", "SPOT"},
            {"s3_sun", "Directional light", "LIGHT3D", "Extra sun / moon light.", "ضوء شمس / قمر إضافي.", "LIGHT3D", "DIRECTIONAL"},
    };
    private static final String[][] CAMERAS_3D = {
            {"s3_camera", "Camera 3D", "CAMERA3D", "What the player sees; follow, orbit, first person.", "ما يراه اللاعب: تتبع، دوران، منظور أول.", "CAMERA3D", ""},
            {"s3_cameraview", "Camera view", "CAMERAVIEW", "Shows a camera live on screen (mirror, minimap).", "يعرض كاميرا مباشرة على الشاشة (مرآة، خريطة مصغرة).", "CAMERAVIEW", ""},
    };
    /** Game systems of both scene kinds: path finding grid, local multiplayer. */
    private static final String[][] SYSTEMS = {
            {"s3_navgrid", "Path grid", "NAVGRID", "Smart enemies find their way around walls: paint where they can walk.", "الأعداء الأذكياء يجدون طريقهم حول الجدران: ارسم أين يمكنهم المشي.", "NAVGRID", ""},
            {"s3_network", "Network play", "NETWORK", "Multiplayer on the same Wi-Fi or a phone hotspot: rooms, players, sync.", "لعب جماعي على نفس الواي فاي أو نقطة اتصال الهاتف: غرف ولاعبون ومزامنة.", "NETWORK", ""},
    };
    /** 2D layer of a 3D scene: indexes of BODIES that make sense on top of 3D. */
    private static final int[] OVERLAY_2D = {2, 3, 4, 0, 10};

    @Override
    public View view(Context c) {
        boolean ar = Ui.isRtl(c);
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 12));
        com.star4droid.star2d.editor.LibgdxEditor ed = Bridge.app() == null ? null : Bridge.app().getEditor();
        if (ed != null && ed.is3DScene()) {
            col.addView(Ui.sectionLabel(c, Tr.t("s3_shapes", "3D shapes")));
            col.addView(grid3d(c, SHAPES_3D, ar));
            LinearLayout imp = Ui.button(c, R.drawable.ic_s2_download, Tr.t("s3_import_glb", "Import 3D models (.glb / .gltf / .obj)"), true, v -> importModels(c, false));
            col.addView(imp, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(c, 44)), c, 4, 4, 4, 2));
            TextView hint = Ui.text(c, Tr.t("s3_import_hint2", "Pick the model together with its .mtl / .bin and texture files, a .zip with them, or import its whole folder."), 11.5f, Ui.TEXT_3);
            hint.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
            col.addView(hint);
            LinearLayout impDir = Ui.button(c, R.drawable.ic_s2_download, Tr.t("s3_import_folder", "Import a model folder"), false, v -> importModels(c, true));
            col.addView(impDir, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(c, 40)), c, 4, 4, 4, 6));
            addAiSection(c, col, true);
            col.addView(Ui.sectionLabel(c, Tr.t("s3_special", "Models, terrain and water")));
            col.addView(grid3d(c, SPECIAL_3D, ar));
            col.addView(Ui.sectionLabel(c, Tr.t("s3_lights", "3D lights")));
            col.addView(grid3d(c, LIGHTS_3D, ar));
            col.addView(Ui.sectionLabel(c, Tr.t("s3_cameras", "Cameras")));
            col.addView(grid3d(c, CAMERAS_3D, ar));
            col.addView(Ui.sectionLabel(c, Tr.t("s3_systems", "Game systems")));
            col.addView(grid3d(c, SYSTEMS, ar));
            col.addView(Ui.sectionLabel(c, Tr.t("s3_overlay", "Screen (2D layer: HUD, buttons, menus)")));
            String[][] overlay = new String[OVERLAY_2D.length][];
            for (int i = 0; i < OVERLAY_2D.length; i++) overlay[i] = BODIES[OVERLAY_2D[i]];
            col.addView(grid(c, overlay, ar, false, OVERLAY_2D));
            ScrollView s3 = new ScrollView(c);
            s3.addView(col);
            return s3;
        }
        col.addView(Ui.sectionLabel(c, Tr.t("newBody", "Elements")));
        col.addView(grid(c, BODIES, ar, false, null));
        col.addView(grid3d(c, new String[][]{CAMERAS_3D[1]}, ar));
        col.addView(Ui.sectionLabel(c, Tr.t("s3_systems", "Game systems")));
        col.addView(grid3d(c, SYSTEMS, ar));
        col.addView(Ui.sectionLabel(c, Tr.t("newLight", "Lights")));
        col.addView(grid(c, LIGHTS, ar, true, null));
        if (!shell.inPrefabMode() && shell.projectPath() != null) {
            col.addView(Ui.sectionLabel(c, Tr.t("prefabs", "Prefabs")));
            com.star4droid.star2d.ui.PrefabUi.Browser pb = new com.star4droid.star2d.ui.PrefabUi.Browser(c, shell.projectPath(), false, p -> {
                shell.panels.hide();
                com.star4droid.star2d.ui.PrefabUi.addToScene(shell, p);
            });
            LinearLayout pcard = Ui.column(c);
            pcard.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, 1));
            pcard.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 4));
            pcard.addView(pb.view(), Ui.matchWrap());
            col.addView(pcard, Ui.margins(Ui.matchWrap(), c, 4, 0, 4, 4));
        }
        if (shell.projectPath() != null) addAiSection(c, col, false);
        LinearLayout settings = Ui.button(c, R.drawable.ic_s2_settings, Tr.t("lightSettings", "Light settings"), false,
                v -> { shell.panels.hide(); com.star4droid.star2d.ui.LightSettings.show(shell.activity()); });
        LinearLayout.LayoutParams lp = Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 4, 8, 4, 0);
        col.addView(settings, lp);
        ScrollView s = new ScrollView(c);
        s.addView(col);
        return s;
    }

    /**
     * Imports 3D models from the phone into the project's models folder and adds a MODEL element for each model file
     * chosen: .glb, .gltf (with its .bin and pictures) and .obj (with its .mtl and pictures). Several files can be picked
     * together, or a whole folder (folder = true), which keeps its sub folders so the relative names in .mtl / .gltf work.
     */
    private void importModels(Context c, boolean folder) {
        final String project = shell.projectPath();
        if (project == null) return;
        final java.io.File tmp = new java.io.File(project, ".glb_import");
        com.star4droid.star2d.Helpers.FileUtil.deleteFile(tmp.getAbsolutePath());
        tmp.mkdirs();
        Runnable done = () -> {
            final java.util.ArrayList<String> models = new java.util.ArrayList<>();
            try {
                // .zip (obj + mtl + textures) unpacked; .mtl / textures found and renamed to safe names, references rewritten
                ObjFix.fixFolder(tmp);
                java.io.File dest = new java.io.File(project, "models");
                dest.mkdirs();
                moveModels(tmp, dest, "", models);
            } catch (Throwable t) {
                Ui.toast(c, String.valueOf(t));
            }
            com.star4droid.star2d.Helpers.FileUtil.deleteFile(tmp.getAbsolutePath());
            if (models.isEmpty()) {
                Ui.toast(c, Tr.t("s3_import_none", "No .glb / .gltf / .obj model was chosen"));
                return;
            }
            importList(c, project, models);
        };
        if (folder) shell.host().importFolder(tmp.getAbsolutePath(), done);
        else shell.host().importFiles(tmp.getAbsolutePath(), done);
    }

    /** Imported models: preview each (3D viewer), tick the ones to add to the scene. */
    private void importList(Context c, String project, java.util.ArrayList<String> models) {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), 0);
        final boolean[] add = new boolean[models.size()];
        for (int i = 0; i < models.size(); i++) {
            final int k = i;
            add[i] = true;
            java.io.File f = new java.io.File(project, "models" + models.get(i));
            android.widget.CheckBox cb = new android.widget.CheckBox(c);
            cb.setChecked(true);
            cb.setText(f.getName() + "  ·  " + com.star4droid.star2d.suda.Suda3D.fmtSize(f.length()));
            cb.setTextColor(Ui.TEXT);
            cb.setOnCheckedChangeListener((b, on) -> add[k] = on);
            LinearLayout row = Ui.row(c);
            row.addView(cb, Ui.weight(1));
            row.addView(Ui.iconButton(c, R.drawable.ic_s2_view3d, Tr.t("s3d_preview", "Preview"), v -> {
                if (c instanceof android.app.Activity)
                    com.star4droid.star2d.suda.ModelViewerActivity.open((android.app.Activity) c, f, "", "", f.length(), project);
            }));
            col.addView(row, Ui.matchWrap());
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.addView(col);
        new android.app.AlertDialog.Builder(c)
                .setTitle(Tr.t("s3_import_done", "Imported") + ": " + models.size())
                .setView(sv)
                .setPositiveButton(Tr.t("s3d_add_scene", "Add to scene"), (d, w) -> {
                    shell.panels.hide();
                    Bridge.gl(() -> {
                        for (int i = 0; i < models.size(); i++) if (add[i]) Bridge.controls().actionAdd3D("OBJ3D", "MODEL", models.get(i));
                    });
                })
                .setNegativeButton(Tr.t("s3d_keep_files", "Only keep the files"), null)
                .show();
    }

    /** "Generate with SUDA3D" entries + import of downloaded workspace / generated results. */
    private void addAiSection(Context c, LinearLayout col, boolean is3d) {
        android.app.Activity a = shell.activity();
        com.star4droid.star2d.suda.EditorEnv.install(shell);
        col.addView(Ui.sectionLabel(c, Tr.t("s3d_ai_section", "AI generation (SUDA3D)")));
        String[][] items = {{"image_to_model", "Image → 3D"}, {"text_to_model", "Text → 3D"}, {"text_to_image", "Text → Image"}};
        LinearLayout row = Ui.row(c);
        for (String[] it : items) {
            LinearLayout b = Ui.button(c, 0, Tr.t("s3d_op_" + it[0], it[1]), false, v -> { shell.panels.hide(); com.star4droid.star2d.suda.QuickGen.show(a, it[0], null); });
            row.addView(b, Ui.margins(new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1), c, 3, 2, 3, 2));
        }
        col.addView(row, Ui.matchWrap());
        LinearLayout imp = Ui.button(c, R.drawable.ic_s2_nodes, is3d ? Tr.t("s3d_import_ws_model", "Import a model from the workspace")
                : Tr.t("s3d_import_ws_image", "Import a picture from the workspace"), false, v ->
                com.star4droid.star2d.suda.QuickGen.pickOutput(a, is3d, f -> {
                    String project = shell.projectPath();
                    if (project == null) return;
                    com.star4droid.star2d.suda.Store.saveToProject(a, f, project, saved -> {
                        if (is3d) {
                            shell.panels.hide();
                            Bridge.gl(() -> Bridge.controls().actionAdd3D("OBJ3D", "MODEL", "/" + saved.getName()));
                        }
                    });
                }));
        col.addView(imp, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(c, 40)), c, 4, 4, 4, 6));
    }

    static boolean isModelFile(String low) {
        return low.endsWith(".glb") || low.endsWith(".gltf") || low.endsWith(".obj");
    }

    static boolean isModelPart(String low) {
        return low.endsWith(".bin") || low.endsWith(".mtl") || low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg")
                || low.endsWith(".webp") || low.endsWith(".bmp") || low.endsWith(".gif");
    }

    /** Moves the chosen files (and sub folders) into models/, same relative names; model files are listed as "/rel/name". */
    private static void moveModels(java.io.File from, java.io.File dest, String rel, java.util.ArrayList<String> models) {
        java.io.File[] chosen = from.listFiles();
        if (chosen == null) return;
        for (java.io.File f : chosen) {
            String n = f.getName();
            if (f.isDirectory()) {
                if (rel.split("/").length < 6) moveModels(f, new java.io.File(dest, n), rel + n + "/", models);
                continue;
            }
            String low = n.toLowerCase();
            boolean model = isModelFile(low);
            if (!model && !isModelPart(low)) continue; // anything else is ignored
            // pictures / .bin / .mtl keep their exact names: the model files refer to them by name
            String outName = model ? n.replace(' ', '_') : n;
            dest.mkdirs();
            java.io.File to = new java.io.File(dest, outName);
            if (to.exists()) to.delete();
            if (!f.renameTo(to)) com.star4droid.star2d.Helpers.FileUtil.copyFile(f.getAbsolutePath(), to.getAbsolutePath());
            if (model) models.add("/" + rel + outName);
        }
    }

    private GridLayout grid3d(Context c, String[][] items, boolean ar) {
        GridLayout g = new GridLayout(c);
        g.setColumnCount(2);
        for (String[] it : items) {
            LinearLayout card = card(c, it, ar);
            card.setOnClickListener(v -> {
                shell.panels.hide();
                if ("TERRAIN_GEN".equals(it[6])) {
                    Bridge.query(() -> {
                        Bridge.controls().actionAdd3D("OBJ3D", "TERRAIN");
                        return Boolean.TRUE;
                    }, ok -> {
                        com.star4droid.star2d.ui.TerrainPanel.openGenerator = true;
                        com.star4droid.star2d.ui.TerrainPanel.open(shell);
                    });
                    return;
                }
                if ("NETWORK".equals(it[5])) {
                    // one per scene: an existing one is selected instead
                    Bridge.query(() -> Bridge.controls().actionAddNetwork(), added -> {
                        Ui.toast(c, Boolean.TRUE.equals(added) ? Tr.t("ui_added", "Added") + ": " + Tr.t(it[0], it[1])
                                : Tr.t("net_one_per_scene", "A scene has one Network element: it is selected."));
                        shell.panels.show(shell.properties);
                    });
                    return;
                }
                if ("NAVGRID".equals(it[5])) {
                    Bridge.query(() -> Bridge.controls().actionAddNavGrid() != null, ok -> {
                        if (!Boolean.TRUE.equals(ok)) return;
                        Ui.toast(c, Tr.t("nav_added_hint", "Path grid added: press \"Paint cells\" to draw the walls."));
                        shell.panels.show(shell.properties);
                    });
                    return;
                }
                Bridge.gl(() -> {
                    if ("CAMERAVIEW".equals(it[5])) Bridge.controls().actionAddCameraView();
                    else Bridge.controls().actionAdd3D(it[5], it[6].isEmpty() ? null : it[6]);
                });
                Ui.toast(c, Tr.t("ui_added", "Added") + ": " + Tr.t(it[0], it[1]));
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
            g.addView(card, lp);
        }
        g.setLayoutParams(Ui.matchWrap());
        return g;
    }

    private LinearLayout card(Context c, String[] it, boolean ar) {
        LinearLayout card = Ui.row(c);
        card.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        card.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(c, 10)));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(Ui.smallIcon(c, ElementsPanel.typeIcon(it[2]), ElementsPanel.typeColor(it[2]), 26));
        LinearLayout texts = Ui.column(c);
        texts.setPadding(Ui.dp(c, 10), 0, 0, 0);
        TextView name = Ui.text(c, Tr.t(it[0], it[1]), 14, Ui.TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        TextView d = Ui.text(c, Tr.t(it[0] + "_desc", ar ? it[4] : it[3]), 11.5f, Ui.TEXT_3);
        d.setMaxLines(3);
        texts.addView(name);
        texts.addView(d);
        card.addView(texts, Ui.weight(1));
        return card;
    }

    private GridLayout grid(Context c, String[][] items, boolean ar, boolean lights, int[] positions) {
        GridLayout g = new GridLayout(c);
        int cols = Ui.isLandscape(c) ? 2 : 2;
        g.setColumnCount(cols);
        for (int i = 0; i < items.length; i++) {
            final int pos = positions != null ? positions[i] : i;
            String[] it = items[i];
            LinearLayout card = Ui.row(c);
            card.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
            card.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(c, 10)));
            card.setGravity(Gravity.CENTER_VERTICAL);
            ImageView ic = Ui.smallIcon(c, ElementsPanel.typeIcon(it[2]), ElementsPanel.typeColor(it[2]), 26);
            card.addView(ic);
            LinearLayout texts = Ui.column(c);
            texts.setPadding(Ui.dp(c, 10), 0, 0, 0);
            TextView name = Ui.text(c, Tr.t(it[0], it[1]), 14, Ui.TEXT);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setMaxLines(1);
            TextView d = Ui.text(c, Tr.t("ui_add_desc_" + it[0], ar ? it[4] : it[3]), 11.5f, Ui.TEXT_3);
            d.setMaxLines(3);
            texts.addView(name);
            texts.addView(d);
            card.addView(texts, Ui.weight(1));
            com.star4droid.star2d.ui.tutorial.Tut.tag(card, lights ? "add:light" + pos : "add:" + it[2].toLowerCase());
            card.setOnClickListener(v -> {
                shell.panels.hide();
                Bridge.gl(() -> {
                    if (lights) Bridge.controls().actionAddLight(pos);
                    else Bridge.controls().actionAddBody(pos);
                });
                Ui.toast(c, Tr.t("ui_added", "Added") + ": " + Tr.t(it[0], it[1]));
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
            g.addView(card, lp);
        }
        g.setLayoutParams(Ui.matchWrap());
        return g;
    }

    static ColorStateList tint(int c) { return ColorStateList.valueOf(c); }
}
