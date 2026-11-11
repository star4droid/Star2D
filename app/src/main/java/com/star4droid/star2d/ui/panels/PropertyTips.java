package com.star4droid.star2d.ui.panels;

import java.util.HashMap;

/** Short explanations of property fields for beginners (English / Arabic). */
public final class PropertyTips {
    private PropertyTips() {}

    private static final HashMap<String, String[]> TIPS = new HashMap<>();

    private static void t(String field, String en, String ar) {
        TIPS.put(field, new String[]{en, ar});
    }

    static {
        t("layout", "free: x, y, width and height as typed. screen: placed by the screen box (anchor, stretch, percent, margins).", "free: س وص والعرض والارتفاع كما تكتبها. screen: يوضع حسب مربع الشاشة (نقطة التثبيت، التمديد، النسبة، الهوامش).");
        t("anchor", "Screen layout: which corner or edge of the screen the holder sticks to.", "تخطيط الشاشة: أي زاوية أو حافة من الشاشة يلتصق بها الحامل.");
        t("Stretch X", "Screen layout: fill the whole screen width (minus the margins).", "تخطيط الشاشة: يملأ عرض الشاشة كاملاً (ناقص الهوامش).");
        t("Stretch Y", "Screen layout: fill the whole screen height (minus the margins).", "تخطيط الشاشة: يملأ ارتفاع الشاشة كاملاً (ناقص الهوامش).");
        t("Width Percent", "Screen layout: width as percent (0-100) of the screen. 0 = use Width.", "تخطيط الشاشة: العرض كنسبة مئوية (0-100) من الشاشة. 0 = استعمل العرض.");
        t("Height Percent", "Screen layout: height as percent (0-100) of the screen. 0 = use Height.", "تخطيط الشاشة: الارتفاع كنسبة مئوية (0-100) من الشاشة. 0 = استعمل الارتفاع.");
        t("Margin X", "Screen layout: distance in px from the anchored left / right edge.", "تخطيط الشاشة: المسافة بالبكسل من الحافة اليسرى/اليمنى المثبّت عليها.");
        t("Margin Y", "Screen layout: distance in px from the anchored top / bottom edge.", "تخطيط الشاشة: المسافة بالبكسل من الحافة العلوية/السفلية المثبّت عليها.");
        t("name", "Unique name of the element. Used in blocks and code to find it.", "اسم فريد للعنصر، يُستخدم في البلوكات والكود للوصول إليه.");
        t("type", "DYNAMIC: moves and falls with gravity (players, balls).\nSTATIC: never moves (ground, walls).\nKINAMATIC: moves by code only, not affected by gravity (moving platforms).\nUI: stays fixed on the screen (buttons, score).",
                "DYNAMIC: يتحرك ويسقط بالجاذبية (لاعب، كرة).\nSTATIC: ثابت لا يتحرك (أرض، جدار).\nKINAMATIC: يتحرك بالكود فقط ولا تؤثر عليه الجاذبية (منصة متحركة).\nUI: ثابت على الشاشة (أزرار، النقاط).");
        t("lock", "Locked elements can't be moved by touch in the editor.", "العنصر المقفل لا يمكن تحريكه باللمس في المحرر.");
        t("Script", "Which body script (code) controls this element.", "أي سكربت (كود) يتحكم في هذا العنصر.");
        t("parent", "Attach this element to another one; it follows the parent.", "اربط هذا العنصر بعنصر آخر ليتبعه.");
        t("x", "Horizontal position in the world.", "الموضع الأفقي في العالم.");
        t("y", "Vertical position in the world.", "الموضع الرأسي في العالم.");
        t("z", "Drawing order: higher values are drawn on top.", "ترتيب الرسم: القيمة الأعلى تُرسم فوق غيرها.");
        t("rotation", "Angle in degrees.", "الزاوية بالدرجات.");
        t("Pivot", "The point the element rotates and scales around: center, a side, a corner or custom. For physical bodies keep center.", "النقطة التي يدور ويتكبّر العنصر حولها: المنتصف أو جانب أو زاوية أو قيمة مخصصة. للأجسام الفيزيائية اترك المنتصف.");
        t("Pivot X", "Custom pivot: 0 = left edge, 1 = right edge (used when Pivot is custom).", "نقطة ارتكاز مخصصة: 0 = الحافة اليسرى، 1 = اليمنى (تُستعمل عندما Pivot = custom).");
        t("Pivot Y", "Custom pivot: 0 = bottom edge, 1 = top edge (used when Pivot is custom).", "نقطة ارتكاز مخصصة: 0 = الحافة السفلى، 1 = العليا (تُستعمل عندما Pivot = custom).");
        t("Y Sort", "Draws everything inside this group by height: the lower one on the screen is in front (trees, characters and houses in a top-down game).", "يرسم كل ما داخل المجموعة حسب الارتفاع: الأقل على الشاشة يظهر في الأمام (أشجار وشخصيات وبيوت في لعبة من الأعلى).");
        t("animation", "Pick an animation from the anims folder: the element plays it (it has priority over the image). Reset goes back to the image.", "اختر أنيميشن من مجلد anims: يشغّله العنصر (له أولوية على الصورة). إعادة الضبط ترجع للصورة.");
        t("width", "Width of the element.", "عرض العنصر.");
        t("height", "Height of the element.", "ارتفاع العنصر.");
        t("radius", "Radius of the circle.", "نصف قطر الدائرة.");
        t("density", "How heavy the body is. Higher = heavier.", "كثافة الجسم: كلما زادت صار أثقل.");
        t("friction", "How much it slides. 0 = ice, 1 = rubber.", "الاحتكاك: 0 = جليد، 1 = مطاط.");
        t("restitution", "Bounciness. 0 = no bounce, 1 = full bounce.", "الارتداد: 0 = لا يرتد، 1 = يرتد بالكامل.");
        t("Gravity Scale", "Multiplier for gravity on this body. 0 = floats.", "مضاعف الجاذبية لهذا الجسم. 0 = يطفو.");
        t("Fixed Rotation", "Prevent the body from rotating (good for players).", "يمنع الجسم من الدوران (مناسب للاعب).");
        t("Bullet", "Better collision for very fast objects.", "تصادم أدق للأجسام السريعة جداً.");
        t("isSensor", "Detects collisions but doesn't block (triggers, coins).", "يكتشف التصادم دون أن يصد الأجسام (مناطق تفعيل، عملات).");
        t("Active", "Inactive bodies don't take part in physics.", "الجسم غير النشط لا يشارك في الفيزياء.");
        t("Visible", "Show or hide the element.", "إظهار أو إخفاء العنصر.");
        t("image", "Picture shown for this element (from the images folder).", "الصورة المعروضة للعنصر (من مجلد images).");
        t("Pressed Image", "Optional picture shown while the element is pressed (back to normal on release). Empty = none.", "صورة اختيارية تظهر أثناء الضغط على العنصر وترجع للأصل عند الرفع. فارغ = بدون.");
        t("Touch Start Image", "Optional picture applied when the touch starts (stays until changed).", "صورة اختيارية تُطبَّق عند بدء اللمس وتبقى حتى تتغير.");
        t("Touch End Image", "Optional picture applied when the touch ends (stays until changed).", "صورة اختيارية تُطبَّق عند انتهاء اللمس وتبقى حتى تتغير.");
        t("Tint", "Color multiplied over the image.", "لون يُمزج فوق الصورة.");
        t("Collision", "Elements this one should NOT collide with.", "العناصر التي لا يجب أن يتصادم معها هذا العنصر.");
        t("Points", "Custom collider polygon points.", "نقاط شكل التصادم المخصص.");
        t("Text", "Text to show.", "النص المعروض.");
        t("Font Scale", "Size of the text. Ignored while Auto Size is on (the text then fills its box).", "حجم النص. يُتجاهل عند تفعيل الحجم التلقائي (يملأ النص صندوقه).");
        t("Auto Size", "On: the text size follows the box: it never leaves it, and grows or shrinks when you resize it (Arabic and English).", "مفعّل: يتبع حجم الخط صندوق النص فلا يخرج منه ويكبر ويصغر مع تغيير حجمه (عربي وإنجليزي).");
        t("Text Align", "Where the lines sit in the box: LEFT, CENTER or RIGHT (use RIGHT or CENTER for Arabic).", "موضع الأسطر في الصندوق: LEFT أو CENTER أو RIGHT (استخدم RIGHT أو CENTER للعربية).");
        t("tileX", "Repeat the image horizontally.", "تكرار الصورة أفقياً.");
        t("tileY", "Repeat the image vertically.", "تكرار الصورة رأسياً.");
        t("Zoom", "Camera zoom. Smaller = closer.", "تقريب الكاميرا: أصغر = أقرب.");
        t("Duration", "Effect duration in seconds.", "مدة التأثير بالثواني.");
        t("Distance", "How far the light reaches.", "مدى وصول الضوء.");
        t("rays", "Light quality (more rays = smoother, slower).", "جودة الضوء (أشعة أكثر = أنعم وأبطأ).");
        t("Static Light", "Static lights are cheaper (don't move).", "الضوء الثابت أخف على الأداء.");
        t("attach To", "Attach the light to an element so it follows it.", "اربط الضوء بعنصر ليتبعه.");
        // path finding grid (NAVGRID)
        t("Cell Shape", "SQUARE: square cells (4 or 8 directions). HEX: hexagons (6 directions, rounder paths).", "SQUARE: خلايا مربعة (4 أو 8 اتجاهات). HEX: خلايا سداسية (6 اتجاهات، مسارات أنعم).");
        t("Cell Width", "Width of a cell (px in 2D, meters in 3D). Smaller cells = more precise paths but more work. Hexagons: the distance between two cells of a row.", "عرض الخلية (بكسل في 2D، متر في 3D). خلايا أصغر = مسارات أدق لكن عمل أكثر. السداسية: المسافة بين خليتين في الصف.");
        t("Cell Height", "Height of a cell. Hexagons: the distance between two rows (a regular hexagon = width x 0.866).", "ارتفاع الخلية. السداسية: المسافة بين صفين (السداسي المنتظم = العرض × 0.866).");
        t("Diagonal", "Square cells: walkers may also move diagonally (8 directions).", "الخلايا المربعة: يمكن للمشاة التحرك قطرياً أيضاً (8 اتجاهات).");
        t("Cut Corners", "Diagonal moves may pass the corner of a wall (off: they go around it).", "الحركة القطرية يمكنها المرور بزاوية الجدار (معطّل: تلتف حولها).");
        t("Agent Size", "Width of the walkers: paths keep this far from the walls. 0 = paths may touch the walls.", "عرض المشاة: تبقى المسارات بعيدة عن الجدران بهذا القدر. 0 = يمكن للمسارات ملامسة الجدران.");
        t("Obstacles", "Elements that block the cells they cover. They are followed when they move, and their cells open again when they are destroyed (doors, crates).", "عناصر تسد الخلايا التي تغطيها. تُتابَع عندما تتحرك، وتنفتح خلاياها عندما تُحذف (أبواب، صناديق).");
        t("Use Static Bodies", "When the game starts, every STATIC physical element blocks the cells it covers.", "عند بدء اللعبة، كل عنصر فيزيائي ثابت (STATIC) يسد الخلايا التي يغطيها.");
        t("Show In Game", "Draw the grid while playing (to test the paths).", "ارسم الشبكة أثناء اللعب (لاختبار المسارات).");
        // local multiplayer (NETWORK)
        t("Room Name", "Name the other players see when they search for games (empty = the device name).", "الاسم الذي يراه اللاعبون الآخرون عند البحث عن الألعاب (فارغ = اسم الجهاز).");
        t("Max Players", "Most players in one game, the host included.", "أقصى عدد للاعبين في اللعبة، والمضيف منهم.");
        t("Send Rate", "Updates sent per second: 20 is smooth on Wi-Fi, less saves battery.", "عدد التحديثات المرسلة في الثانية: 20 سلس على الواي فاي، والأقل يوفر البطارية.");
        t("Player Template", "An element copied for every player who joins; each copy is moved by its own player. Empty = make the players yourself with blocks.", "عنصر يُنسخ لكل لاعب ينضم، وكل نسخة يحركها لاعبها. فارغ = اصنع اللاعبين بنفسك بالبلوكات.");
        t("Spawn Radius", "The copies of the player template appear around it, up to this distance.", "تظهر نسخ قالب اللاعب حوله ضمن هذه المسافة.");
        t("Game Version", "Only players with the same version can join: change it when you change the game.", "ينضم فقط اللاعبون بنفس الإصدار: غيّره عندما تغيّر اللعبة.");
        t("Lobby", "Show the ready-made lobby (host, rooms, players, start) when the scene starts.", "اعرض صالة الانتظار الجاهزة (استضافة، غرف، لاعبون، ابدأ) عند بدء المشهد.");
        t("Game Scene", "The scene the lobby opens for everybody when the host presses Start.", "المشهد الذي تفتحه صالة الانتظار للجميع عندما يضغط المضيف ابدأ.");
        t("TCP Port", "Network port for reliable messages (keep the default unless another app uses it).", "منفذ الشبكة للرسائل المؤكدة (اتركه كما هو إلا إذا استعمله تطبيق آخر).");
        t("UDP Port", "Network port for fast updates and for finding games (keep the default).", "منفذ الشبكة للتحديثات السريعة والبحث عن الألعاب (اتركه كما هو).");
    }

    public static String tip(String field, boolean arabic) {
        String[] t = TIPS.get(field);
        if (t == null) return null;
        return com.star4droid.star2d.ui.Tr.t("ui_tip_" + field.replaceAll("[^A-Za-z0-9]+", "_"), t[arabic ? 1 : 0]);
    }
}
