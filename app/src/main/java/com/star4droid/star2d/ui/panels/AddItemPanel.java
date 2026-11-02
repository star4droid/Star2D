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
            {"s3_terrain", "Terrain", "Terrain", "Hills and valleys (noise or height image).", "تلال ووديان (ضوضاء أو صورة ارتفاعات).", "OBJ3D", "TERRAIN"},
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
            col.addView(Ui.sectionLabel(c, Tr.t("s3_special", "Models, terrain and water")));
            col.addView(grid3d(c, SPECIAL_3D, ar));
            col.addView(Ui.sectionLabel(c, Tr.t("s3_lights", "3D lights")));
            col.addView(grid3d(c, LIGHTS_3D, ar));
            col.addView(Ui.sectionLabel(c, Tr.t("s3_cameras", "Cameras")));
            col.addView(grid3d(c, CAMERAS_3D, ar));
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
        LinearLayout settings = Ui.button(c, R.drawable.ic_s2_settings, Tr.t("lightSettings", "Light settings"), false,
                v -> { shell.panels.hide(); com.star4droid.star2d.ui.LightSettings.show(shell.activity()); });
        LinearLayout.LayoutParams lp = Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 4, 8, 4, 0);
        col.addView(settings, lp);
        ScrollView s = new ScrollView(c);
        s.addView(col);
        return s;
    }

    private GridLayout grid3d(Context c, String[][] items, boolean ar) {
        GridLayout g = new GridLayout(c);
        g.setColumnCount(2);
        for (String[] it : items) {
            LinearLayout card = card(c, it, ar);
            card.setOnClickListener(v -> {
                shell.panels.hide();
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
