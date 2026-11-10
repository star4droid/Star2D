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
    }

    public static String tip(String field, boolean arabic) {
        String[] t = TIPS.get(field);
        if (t == null) return null;
        return com.star4droid.star2d.ui.Tr.t("ui_tip_" + field.replaceAll("[^A-Za-z0-9]+", "_"), t[arabic ? 1 : 0]);
    }
}
