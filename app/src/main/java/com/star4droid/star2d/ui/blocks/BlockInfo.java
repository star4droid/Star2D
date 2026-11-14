package com.star4droid.star2d.ui.blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Human labels and (!) explanations for blocks, categories and field names (English / Arabic).
 * The saved block title never changes; these are display-only.
 */
public final class BlockInfo {
    private BlockInfo() {}

    // title -> {label en, label ar, info en, info ar}
    private static final HashMap<String, String[]> B = new HashMap<>();
    private static final HashMap<String, String[]> CAT = new HashMap<>();
    private static final HashMap<String, String[]> FIELD = new HashMap<>();

    private static void b(String t, String le, String la, String ie, String ia) { B.put(t, new String[]{le, la, ie, ia}); }
    private static void c(String t, String e, String a) { CAT.put(t, new String[]{e, a}); }
    private static void f(String t, String e, String a) { FIELD.put(t, new String[]{e, a}); }

    static {
        c("main", "Control", "تحكم أساسي");
        c("Control", "Loops & logic", "التكرار والشروط");
        c("Variables", "Variables & data", "المتغيرات والبيانات");
        c("Motion", "Motion", "الحركة");
        c("Looks", "Looks", "المظهر");
        c("Input", "Input", "الإدخال واللمس");
        c("Physics", "Physics+", "فيزياء إضافية");
        c("Camera", "Camera", "الكاميرا");
        c("Configurations", "Scene", "المشهد");
        c("Debug", "Debug", "رسائل وتصحيح");
        c("Language", "Language", "اللغة والترجمة");
        c("Ui Nodes", "UI", "واجهة اللعبة");
        c("BodyModify", "Element", "تعديل العنصر");
        c("bodyControl", "Forces", "القوى والسرعة");
        c("bodyBooleans", "Body options", "خيارات الجسم");
        c("Sounds", "Sounds", "الأصوات");
        c("Joints", "Joints", "الروابط");
        c("Behaviors", "Behaviors", "السلوكيات");

        f("behavior", "behavior", "السلوك");
        f("setting", "setting", "الإعداد");
        // behaviors (always visible): add / remove / turn on-off / settings
        b("addBehavior", "Add behavior", "أضف سلوكاً", "Attaches a behavior to an element while the game runs (it starts with its default settings). Nothing happens if the element already has it.", "يضيف سلوكاً إلى عنصر أثناء تشغيل اللعبة (بإعداداته الافتراضية). لا يحدث شيء إن كان العنصر يملكه.");
        b("removeBehavior", "Remove behavior", "احذف السلوك", "Detaches a behavior from an element for good.", "يفصل سلوكاً عن العنصر نهائياً.");
        b("enableBehavior", "Turn behavior on", "فعّل السلوك", "Turns a behavior of the element on. Behaviors of the same exclusive group (movement...) turn off automatically.", "يفعّل سلوكاً للعنصر. السلوكيات من نفس المجموعة الحصرية (الحركة...) تتعطل تلقائياً.");
        b("disableBehavior", "Turn behavior off", "عطّل السلوك", "Pauses a behavior of the element; it stays attached and can be turned on again.", "يوقف سلوكاً للعنصر مؤقتاً؛ يبقى مرتبطاً ويمكن تفعيله لاحقاً.");
        b("setBehaviorEnabled", "Set behavior on/off", "فعّل/عطّل السلوك", "Turns a behavior on (true) or off (false).", "يفعّل السلوك (true) أو يعطّله (false).");
        b("setBehaviorParam", "Set behavior setting", "اضبط إعداد السلوك", "Changes a setting of a behavior by the name shown in the inspector, e.g. behavior Shooter, setting fireRate, value 8. Text needs quotes.", "يغيّر إعداداً في السلوك باسمه كما في المفتّش، مثل: السلوك Shooter والإعداد fireRate والقيمة 8. النص يحتاج علامات تنصيص.");

        f("holder", "UI holder", "حامل الواجهة");
        f("node", "UI element id", "معرّف عنصر الواجهة");
        f("property", "property", "الخاصية");
        f("anchor", "anchor", "نقطة التثبيت");
        f("marginX", "margin x", "هامش س");
        f("marginY", "margin y", "هامش ص");
        f("widthPct", "width %", "العرض %");
        f("heightPct", "height %", "الارتفاع %");
        f("stretchH", "stretch horizontally", "تمديد أفقي");
        f("stretchV", "stretch vertically", "تمديد رأسي");
        f("on", "on", "تشغيل");
        f("visible", "visible", "ظاهر");
        f("pic", "image", "الصورة");
        f("body", "element", "العنصر");
        f("body1", "element 1", "العنصر 1");
        f("body2", "element 2", "العنصر 2");
        f("item", "element", "العنصر");
        f("target", "target", "الهدف");
        f("ui", "UI element", "عنصر الواجهة");
        f("text", "text", "النص");
        f("note", "instructions", "التعليمات");
        f("value", "value", "القيمة");
        f("boolean", "condition", "الشرط");
        f("delay", "seconds", "ثوانٍ");
        f("seconds", "seconds", "ثوانٍ");
        f("name", "name", "الاسم");
        f("scene", "scene", "المشهد");
        f("image", "image", "الصورة");
        f("animation", "animation", "الحركة");
        f("Sound", "sound", "الصوت");
        f("speed", "speed", "السرعة");
        f("power", "power", "القوة");
        f("times", "times", "عدد المرات");
        f("variable", "variable", "المتغير");
        f("by", "by", "بمقدار");
        f("element", "element", "العنصر");
        f("message", "message", "الرسالة");
        f("x", "x", "س");
        f("y", "y", "ص");
        f("dx", "dx", "إزاحة س");
        f("dy", "dy", "إزاحة ص");
        f("strength", "strength", "القوة");
        f("joystick", "joystick", "عصا التحكم");
        f("newName", "new name", "الاسم الجديد");
        f("var", "var", "المتغير");
        f("joystick", "joystick", "عصا التحكم");
        f("degrees", "degrees", "درجات");
        f("visibility", "visible", "ظاهر");
        f("wake", "wake", "إيقاظ");
        f("x", "x", "x");
        f("y", "y", "y");
        f("dx", "dx", "إزاحة x");
        f("dy", "dy", "إزاحة y");
        f("zoom", "zoom", "التقريب");
        f("code", "code", "الكود");
        f("color", "color", "اللون");
        f("alpha", "opacity", "الشفافية");
        f("key", "key", "المفتاح");
        f("type", "type", "النوع");
        f("url", "link", "الرابط");


        // UI holder blocks
        b("uiSetText", "Set UI text", "اضبط نص عنصر واجهة", "Changes the text of a label or button inside a UI holder: pick the holder, type the id of the element (as in the UI Maker) and the new text. Text needs quotes.", "يغيّر نص عنصر (نص أو زر) داخل حامل واجهة: اختر الحامل واكتب معرّف العنصر كما في صانع الواجهات ثم النص الجديد. النص يحتاج علامات تنصيص.");
        b("uiSetValue", "Set UI value", "اضبط قيمة عنصر واجهة", "Sets the value of a slider or progress bar inside a UI holder.", "يضبط قيمة منزلق أو شريط تقدم داخل حامل واجهة.");
        b("uiSetChecked", "Set UI checked", "اضبط تحديد عنصر واجهة", "Turns a switch or checkbox of a UI holder on or off.", "يشغّل أو يطفئ مفتاحاً أو خانة اختيار في حامل واجهة.");
        b("uiShow", "Show UI element", "أظهر عنصر واجهة", "Shows an element of a UI holder.", "يُظهر عنصراً من حامل واجهة.");
        b("uiHide", "Hide UI element", "أخفِ عنصر واجهة", "Hides an element of a UI holder (the others move up if the layout allows).", "يخفي عنصراً من حامل واجهة.");
        b("uiSetVisible", "Set UI visible", "اضبط ظهور عنصر واجهة", "Shows (true) or hides (false) an element of a UI holder.", "يُظهر (true) أو يخفي (false) عنصراً من حامل واجهة.");
        b("uiSetEnabled", "Enable UI element", "فعّل عنصر واجهة", "Turns a button or field of a UI holder on (true) or off (false).", "يفعّل (true) أو يعطّل (false) زراً أو حقلاً في حامل واجهة.");
        b("uiSetAlpha", "Set UI opacity", "اضبط شفافية عنصر واجهة", "0 = invisible, 1 = solid.", "0 = غير مرئي، 1 = واضح.");
        b("uiSetImage", "Set UI image", "اضبط صورة عنصر واجهة", "Shows another picture of the project in an Image element of a UI holder.", "يعرض صورة أخرى من المشروع في عنصر صورة داخل حامل واجهة.");
        b("uiSetColor", "Set UI color", "اضبط لون عنصر واجهة", "Colors an element (role like primary, or #RRGGBB).", "يلوّن عنصراً (دور مثل primary أو #RRGGBB).");
        b("uiSetProperty", "Set UI property", "اضبط خاصية عنصر واجهة", "Changes any property of an element by its name in the UI Maker, e.g. property fontSize, value 40 or textAlign, value center.", "يغيّر أي خاصية لعنصر باسمها في صانع الواجهات، مثل fontSize والقيمة 40 أو textAlign والقيمة center.");
        b("uiScrollTo", "Scroll UI to", "مرّر الواجهة إلى", "Scrolls a scroll view of a UI holder to x, y.", "يمرر عنصر تمرير في حامل واجهة إلى x و y.");
        b("uiClick", "Press UI element", "اضغط عنصر واجهة", "Does what a tap on the element does (runs its onClick).", "ينفّذ ما يفعله النقر على العنصر (يشغّل onClick).");
        b("uiOnClick", "When UI element tapped", "عند نقر عنصر واجهة", "Runs the blocks inside whenever the element is tapped. Put it in OnCreate.", "ينفّذ البلوكات بداخله كلما نُقر العنصر. ضعه في OnCreate.");
        b("uiSetUi", "Show another UI file", "اعرض ملف واجهة آخر", "Replaces the .ui shown by a UI holder with another one of the project (name without .ui).", "يستبدل ملف الواجهة المعروض في الحامل بملف آخر من المشروع (الاسم بدون .ui).");
        b("uiPlace", "Place UI holder", "ضع حامل الواجهة", "Puts the holder in the screen box: anchor topleft, top, topright, left, center, right, bottomleft, bottom or bottomright, with margins in px from the anchored edges.", "يضع الحامل في مربع الشاشة: نقطة التثبيت topleft أو top أو topright أو left أو center أو right أو bottomleft أو bottom أو bottomright، مع هوامش بالبكسل.");
        b("uiSizePercent", "UI holder size %", "حجم حامل الواجهة %", "Size as percent of the screen (0 keeps the fixed size of that side).", "الحجم كنسبة مئوية من الشاشة (0 يبقي الحجم الثابت لذلك الاتجاه).");
        b("uiStretch", "Stretch UI holder", "مدّد حامل الواجهة", "Fills the whole width and / or height of the screen.", "يملأ عرض الشاشة و/أو ارتفاعها كاملاً.");
        // root
        b("First >", "When this event runs", "عند تشغيل هذا الحدث", "Every block attached below runs, top to bottom, when the event happens.", "كل البلوكات المتصلة أسفل هذا البلوك تعمل بالترتيب من الأعلى للأسفل عند حدوث الحدث.");
        b("First > ", "When this event runs", "عند تشغيل هذا الحدث", "Every block attached below runs, top to bottom, when the event happens.", "كل البلوكات المتصلة أسفل هذا البلوك تعمل بالترتيب من الأعلى للأسفل عند حدوث الحدث.");
        // main
        b("addSource", "Write code", "اكتب كوداً", "Insert your own Java code here (manual typing). Example: score++;", "أدخل كود جافا الخاص بك يدوياً هنا. مثال: score++;");
        b("try", "Try (ignore errors)", "جرّب (تجاهل الأخطاء)", "Runs the blocks inside; if they crash, the game continues.", "يشغّل البلوكات بداخله، وإذا حدث خطأ تستمر اللعبة.");
        b("void", "Define function", "عرّف دالة", "Creates a function (use in custom scripts). Name example: shoot()", "ينشئ دالة (للسكربتات المخصصة). مثال الاسم: shoot()");
        b("while", "Repeat while", "كرّر طالما", "Repeats the inside blocks while the condition is true. Careful: endless loops freeze the game.", "يكرر البلوكات بداخله طالما الشرط صحيح. احذر: التكرار اللانهائي يجمّد اللعبة.");
        b("if", "If / else", "إذا / وإلا", "If the condition is true run the first part, otherwise the second.", "إذا كان الشرط صحيحاً ينفّذ الجزء الأول، وإلا ينفّذ الجزء الثاني.");
        b("openUrl", "Open link", "افتح رابطاً", "Opens a web page in the browser.", "يفتح صفحة ويب في المتصفح.");
        b("OpenScene", "Go to scene", "انتقل إلى مشهد", "Switches to another scene (level, menu...).", "ينتقل إلى مشهد آخر (مرحلة، قائمة...).");
        b("finish", "Close game", "أغلق اللعبة", "Ends the current scene / game.", "ينهي المشهد الحالي أو اللعبة.");
        b("_DoAfter", "Do after (timer)", "نفّذ بعد (مؤقت)", "Runs the inside blocks after a delay in seconds. Give the timer a name to cancel it.", "ينفّذ البلوكات بداخله بعد عدد من الثواني. أعطِ المؤقت اسماً لتتمكن من إلغائه.");
        b("cancelTimer", "Cancel timer", "ألغِ المؤقت", "Stops a timer created by Do after / Repeat every.", "يوقف مؤقتاً أنشأته بـ نفّذ بعد أو كرّر كل.");
        b("_repeatEvery", "Repeat every (timer)", "كرّر كل (مؤقت)", "Runs the inside blocks every N seconds.", "ينفّذ البلوكات بداخله كل عدد من الثواني.");
        b("writeToFile", "Write file", "اكتب ملفاً", "Saves text to a file.", "يحفظ نصاً في ملف.");
        b("writeInternal", "Write internal file", "اكتب ملفاً داخلياً", "Saves text to the game's private storage.", "يحفظ نصاً في ذاكرة اللعبة الخاصة.");
        // camera
        b("setZoom", "Set zoom", "اضبط التقريب", "Camera zoom: smaller = closer.", "تقريب الكاميرا: أصغر = أقرب.");
        b("cameraFollow", "Camera follow", "الكاميرا تتبع", "Centers the camera on an element (use in OnStep to keep following).", "يوسّط الكاميرا على عنصر (ضعه في OnStep ليتبعه باستمرار).");
        b("setCameraXAt", "Camera X to element", "الكاميرا أفقياً إلى عنصر", "Moves the camera horizontally to an element.", "يحرك الكاميرا أفقياً إلى عنصر.");
        b("setCameraYAt", "Camera Y to element", "الكاميرا رأسياً إلى عنصر", "Moves the camera vertically to an element.", "يحرك الكاميرا رأسياً إلى عنصر.");
        b("setCameraXY", "Camera to x, y", "الكاميرا إلى x, y", "Moves the camera to a point.", "يحرك الكاميرا إلى نقطة.");
        b("setCameraCenter", "Camera center", "مركز الكاميرا", "Sets the camera center point.", "يضبط نقطة مركز الكاميرا.");
        b("setCameraX", "Camera x", "الكاميرا x", "Sets camera x.", "يضبط x للكاميرا.");
        b("setCameraY", "Camera y", "الكاميرا y", "Sets camera y.", "يضبط y للكاميرا.");
        b("setCameraOffset", "Camera offset", "إزاحة الكاميرا", "Offsets the camera from its target.", "يزيح الكاميرا عن هدفها.");
        // configurations
        b("setsplits", "Physics steps", "خطوات الفيزياء", "Physics precision per frame (advanced).", "دقة الفيزياء في كل إطار (متقدم).");
        b("ifCollision", "If touching", "إذا كان يلمس", "Checks if two elements are touching right now.", "يتحقق إذا كان عنصران يتلامسان الآن.");
        b("Pause", "Pause game", "إيقاف مؤقت", "Pauses physics and timers.", "يوقف الفيزياء والمؤقتات مؤقتاً.");
        b("Resume", "Resume game", "استئناف", "Resumes after pause.", "يستأنف بعد الإيقاف المؤقت.");
        b("saveValue", "Save value", "احفظ قيمة", "Saves a value permanently (advanced).", "يحفظ قيمة بشكل دائم (متقدم).");
        b("setGravity", "Set gravity", "اضبط الجاذبية", "World gravity. Normal: x 0, y -9.8", "جاذبية العالم. الطبيعي: x = 0 و y = -9.8");
        // debug
        b("Toast", "Show message", "أظهر رسالة", "Shows a short message. Text needs quotes: \"Hello\"", "يعرض رسالة قصيرة. النص يحتاج علامات تنصيص: \"مرحبا\"");
        b("debug", "Debug log", "سجل التصحيح", "Writes to the debug log.", "يكتب في سجل التصحيح.");
        // ui
        b("setVisibility", "Set visible", "اضبط الظهور", "Shows (true) or hides (false) an element.", "يُظهر (true) أو يخفي (false) عنصراً.");
        b("setText", "Set text", "اضبط النص", "Changes a Text element. Use quotes for text: \"Score\"", "يغيّر نص عنصر النص. استخدم علامات التنصيص: \"النقاط\"");
        b("setAlpha", "Set alpha (old)", "الشفافية (قديم)", "Not implemented; use Set opacity instead.", "غير مكتمل؛ استخدم «اضبط الشفافية» بدلاً منه.");
        b("setProgress", "Set progress", "اضبط التقدم", "Sets a progress bar value.", "يضبط قيمة شريط التقدم.");
        b("setMax", "Set max", "اضبط الحد الأقصى", "Sets a progress bar maximum.", "يضبط الحد الأقصى لشريط التقدم.");
        // body modify
        b("setScaleXY", "Set scale", "اضبط الحجم", "Scales the element's picture.", "يغيّر حجم صورة العنصر.");
        b("setScaleX", "Set scale X", "الحجم أفقياً", "Horizontal scale (-1 flips).", "الحجم الأفقي (-1 يقلب الصورة).");
        b("setScaleY", "Set scale Y", "الحجم رأسياً", "Vertical scale.", "الحجم الرأسي.");
        b("setTransform", "Set position & angle", "اضبط الموضع والزاوية", "Teleports an element (angle in radians).", "ينقل العنصر فوراً (الزاوية بالراديان).");
        b("setAnimation", "Play animation", "شغّل حركة", "Plays an animation from the anims folder.", "يشغّل حركة من مجلد anims.");
        b("removeAnimation", "Stop animation", "أوقف الحركة", "Stops the element's animation.", "يوقف حركة العنصر.");
        b("setX", "Set x", "اضبط x", "Moves an element horizontally.", "ينقل العنصر أفقياً.");
        b("setY", "Set y", "اضبط y", "Moves an element vertically.", "ينقل العنصر رأسياً.");
        b("setAngle", "Set angle", "اضبط الزاوية", "Angle in radians.", "الزاوية بالراديان.");
        b("setAnimation", "Play animation", "شغّل أنميشن", "Plays an animation from the anims folder on the element (use its name, with quotes, e.g. \"Warrior_Run\"; inner folders: \"hero/run\"). It loops until you play another one.", "يشغّل أنميشن من مجلد anims على العنصر (اكتب اسمه بعلامات تنصيص مثل \"Warrior_Run\"، وللمجلدات الداخلية \"hero/run\"). يستمر بالتكرار حتى تشغّل غيره.");
        b("removeAnimation", "Stop animation", "أوقف الأنميشن", "Stops the animation and shows the element's normal image again.", "يوقف الأنميشن ويعيد عرض صورة العنصر العادية.");
        b("setImage", "Change image", "غيّر الصورة", "Changes the element's picture.", "يغيّر صورة العنصر.");
        b("copyBody", "Clone element", "انسخ العنصر", "Creates a copy at runtime (bullets, enemies...).", "ينشئ نسخة أثناء اللعب (رصاص، أعداء...).");
        b("destroy", "Destroy", "احذف العنصر", "Removes the element from the game.", "يزيل العنصر من اللعبة.");
        // forces
        b("applyForceToCenter", "Push (force)", "ادفع (قوة)", "Pushes continuously (use in OnStep).", "يدفع باستمرار (ضعه في OnStep).");
        b("applyLinearImpulse", "Kick at point", "دفعة عند نقطة", "Instant push at a point.", "دفعة فورية عند نقطة.");
        b("applyLinearImpulseToCenter", "Kick (impulse)", "دفعة فورية", "Instant push: great for jumps.", "دفعة فورية: ممتازة للقفز.");
        b("applyForce", "Force at point", "قوة عند نقطة", "Pushes at a point (can rotate it).", "يدفع عند نقطة (قد يدوّر الجسم).");
        b("setAngularVelocity", "Spin speed", "سرعة الدوران", "Sets rotation speed.", "يضبط سرعة الدوران.");
        b("applyAngularImpulse", "Spin kick", "دفعة دوران", "Instant spin.", "دوران فوري.");
        b("setGravityScale", "Gravity scale", "مقياس الجاذبية", "0 = floats, 1 = normal.", "0 = يطفو، 1 = طبيعي.");
        b("setLinearVelocity", "Set speed", "اضبط السرعة", "Sets movement speed in x and y.", "يضبط السرعة في x و y.");
        b("applyTorque", "Torque", "عزم دوران", "Rotational force.", "قوة دوران.");
        b("setLinearDamping", "Air friction", "مقاومة الهواء", "Slows the body down over time.", "يبطئ الجسم مع الوقت.");
        b("setAwake", "Wake up", "أيقظ الجسم", "Wakes a sleeping body.", "يوقظ جسماً نائماً.");
        b("setBullet", "Bullet mode", "وضع الرصاصة", "Better collisions for fast bodies.", "تصادم أدق للأجسام السريعة.");
        b("setFixedRotation", "Fixed rotation", "منع الدوران", "true = never rotates (players).", "true = لا يدور أبداً (للاعب).");
        b("setActive", "Active", "نشط", "false = removed from physics.", "false = خارج الفيزياء.");
        b("setSleepingAllowed", "Allow sleep", "السماح بالنوم", "Performance option.", "خيار للأداء.");
        // sounds
        b("loopSound", "Loop sound", "كرّر الصوت", "Plays music in a loop (true/false).", "يشغّل الصوت بشكل متكرر (true/false).");
        b("playSound", "Play sound", "شغّل صوتاً", "Plays a sound from the sounds folder.", "يشغّل صوتاً من مجلد sounds.");
        b("pauseSound", "Pause sound", "أوقف الصوت", "Pauses a sound.", "يوقف الصوت مؤقتاً.");
        b("releaseSound", "Release sound", "حرّر الصوت", "Frees a sound from memory.", "يحرر الصوت من الذاكرة.");
        // joints
        b("setMaxMotorTorque", "Motor torque", "عزم المحرك", "Joint motor strength.", "قوة محرك الرابط.");
        b("setMotorSpeed", "Motor speed", "سرعة المحرك", "Joint motor speed (wheels).", "سرعة محرك الرابط (العجلات).");
        b("setLimits", "Joint limits", "حدود الرابط", "Min/max of the joint.", "الحد الأدنى والأعلى للرابط.");
        b("setSpringDampingRatio", "Spring damping", "تخميد النابض", "Spring bounciness control.", "تحكم في ارتداد النابض.");
        b("setSpringFrequencyHz", "Spring frequency", "تردد النابض", "Spring stiffness.", "صلابة النابض.");
        b("enableMotor", "Enable motor", "شغّل المحرك", "Turns the joint motor on/off.", "يشغّل أو يوقف محرك الرابط.");
        b("enableLimit", "Enable limit", "فعّل الحدود", "Turns joint limits on/off.", "يفعّل أو يلغي حدود الرابط.");
        // new: control
        b("repeatTimes", "Repeat N times", "كرّر N مرة", "Runs the inside blocks a number of times. 'var' is the counter name (0, 1, 2...).", "يكرر البلوكات بداخله عدداً من المرات. var هو اسم العدّاد (0، 1، 2...).");
        b("ifThen", "If", "إذا", "Runs the inside blocks only when the condition is true.", "ينفّذ البلوكات بداخله فقط عندما يكون الشرط صحيحاً.");
        b("breakLoop", "Exit loop", "اخرج من التكرار", "Stops the current loop.", "يوقف التكرار الحالي.");
        b("stopEvent", "Stop this event", "أوقف هذا الحدث", "Skips the rest of the blocks of this event.", "يتجاوز باقي بلوكات هذا الحدث.");
        b("waitSeconds", "Wait then", "انتظر ثم", "Runs the inside blocks after some seconds.", "ينفّذ البلوكات بداخله بعد عدة ثوانٍ.");
        b("everySeconds", "Every N seconds", "كل N ثانية", "Runs the inside blocks repeatedly (spawn enemies, timers...).", "ينفّذ البلوكات بداخله بشكل متكرر (إنتاج أعداء، عدّاد...).");
        b("comment", "Note", "ملاحظة", "A note for you; does nothing.", "ملاحظة لك؛ لا تفعل شيئاً.");
        b("explain", "Explain", "شرح", "Has no code. Tap (i) to read the instructions written in it; Edit changes them.", "بلا كود. اضغط (i) لقراءة التعليمات المكتوبة فيها، وتعديل لتغييرها.");
        b("explainIf", "Explain: if", "شرح: إذا", "Has no code. A box that explains a condition; the blocks inside just run. (i) shows the instructions, Edit changes them.", "بلا كود. صندوق يشرح شرطاً؛ البلوكات داخله تُنفَّذ مباشرة. (i) يعرض التعليمات وتعديل يغيّرها.");
        b("explainIfElse", "Explain: if / else", "شرح: إذا / وإلا", "Has no code. The first part just runs, the else part never runs. (i) shows the instructions, Edit changes them.", "بلا كود. الجزء الأول يُنفَّذ مباشرة وجزء وإلا لا يُنفَّذ أبداً. (i) يعرض التعليمات وتعديل يغيّرها.");
        b("codeLine", "Code line", "سطر كود", "Type any Java line, e.g. score++;", "اكتب أي سطر جافا، مثل: score++;");
        // variables
        b("setVariable", "Set variable", "اضبط متغيراً", "variable = value. Create variables in the Variables panel.", "المتغير = القيمة. أنشئ المتغيرات من لوحة المتغيرات.");
        b("changeVariable", "Change variable by", "غيّر المتغير بمقدار", "Adds to a variable (use -1 to subtract).", "يضيف إلى المتغير (استخدم -1 للطرح).");
        b("newVariable", "Local variable", "متغير محلي", "Creates a temporary variable for this event. Long-press it > Make it an object variable to use it in every event of the object.", "ينشئ متغيراً مؤقتاً لهذا الحدث. اضغط عليه مطولاً > اجعله متغير كائن لاستخدامه في كل أحداث الكائن.");
        b("objectVariable", "Object variable", "متغير الكائن", "A variable of this object: every event of the object (and its other scripts) can use it by its name. Other objects read it with 'variable ___ of ___'.", "متغير خاص بهذا الكائن: كل أحداث الكائن (وسكربتاته الأخرى) تستخدمه باسمه. الكائنات الأخرى تقرؤه بـ «متغير ___ من ___».");
        b("setObjectVariable", "Set variable of object", "اضبط متغير كائن", "Sets an object variable of another element (inside the object's own events just use its name).", "يضبط متغير كائن لعنصر آخر (داخل أحداث الكائن نفسه استخدم اسمه مباشرة).");
        b("changeObjectVariable", "Change variable of object", "غيّر متغير كائن", "Adds to an object variable of another element (use -1 to subtract).", "يضيف إلى متغير كائن لعنصر آخر (استخدم -1 للطرح).");
        b("saveData", "Save data", "احفظ بيانات", "Saves a value permanently (high score...). Read it with 'saved number'.", "يحفظ قيمة بشكل دائم (أعلى نتيجة...). اقرأها بـ «الرقم المحفوظ».");
        b("clearSaves", "Clear saves", "امسح المحفوظات", "Deletes everything this game saved with 'save data' (high scores, progress...). Other games and the engine are not touched.", "يحذف كل ما حفظته هذه اللعبة بـ «احفظ بيانات» (أعلى نتيجة، التقدم...). لا يمس الألعاب الأخرى ولا المحرك.");
        // motion
        b("moveBy", "Move by", "تحرك بمقدار", "Moves an element by dx, dy.", "يحرك العنصر بمقدار dx و dy.");
        b("goTo", "Go to x, y", "اذهب إلى x, y", "Teleports an element.", "ينقل العنصر فوراً.");
        b("goToBody", "Go to element", "اذهب إلى عنصر", "Teleports to another element's position.", "ينتقل إلى موضع عنصر آخر.");
        b("setVelocityX", "Speed X", "السرعة الأفقية", "Horizontal speed (keeps falling speed).", "السرعة الأفقية (يحافظ على سرعة السقوط).");
        b("setVelocityY", "Speed Y", "السرعة الرأسية", "Vertical speed.", "السرعة الرأسية.");
        b("stopMoving", "Stop moving", "توقف عن الحركة", "Sets speed and spin to zero.", "يجعل السرعة والدوران صفراً.");
        b("jump", "Jump", "اقفز", "Gives an upward speed. Use in onClick of a jump button.", "يعطي سرعة للأعلى. ضعه في onClick لزر القفز.");
        b("rotateBy", "Rotate by", "دوّر بمقدار", "Rotates by degrees.", "يدوّر بعدد من الدرجات.");
        b("lookAt", "Look at", "انظر إلى", "Turns toward another element.", "يتجه نحو عنصر آخر.");
        b("moveToward", "Move toward", "تحرك نحو", "Moves toward a target (enemies chasing). Use in OnStep.", "يتحرك نحو هدف (أعداء يطاردون). ضعه في OnStep.");
        b("moveWithJoystick", "Move with joystick", "تحرك بعصا التحكم", "Top-down movement. Put it in OnStep.", "حركة من الأعلى. ضعه في OnStep.");
        b("walkWithJoystick", "Walk with joystick", "امشِ بعصا التحكم", "Platformer movement (left/right only). Put it in OnStep.", "حركة ألعاب المنصات (يمين/يسار فقط). ضعه في OnStep.");
        // looks
        b("show", "Show", "أظهر", "Makes an element visible.", "يُظهر العنصر.");
        b("hide", "Hide", "أخفِ", "Hides an element.", "يخفي العنصر.");
        b("setTint", "Tint color", "لوّن", "Colors the picture (RRGGBBAA).", "يلوّن الصورة (RRGGBBAA).");
        b("setOpacity", "Set opacity", "اضبط الشفافية", "0 = invisible, 1 = solid.", "0 = غير مرئي، 1 = واضح.");
        b("flipX", "Flip horizontally", "اقلب أفقياً", "Mirrors the picture.", "يعكس الصورة.");
        b("bringToFront", "Bring to front", "إلى المقدمة", "Draws on top of others.", "يُرسم فوق الباقي.");
        b("sendToBack", "Send to back", "إلى الخلف", "Draws behind others.", "يُرسم خلف الباقي.");
        b("showText", "Show text", "اعرض نصاً", "Shows a value in a Text element (score...).", "يعرض قيمة في عنصر نص (النقاط...).");
        // input
        b("ifTouching", "If screen touched", "إذا لُمست الشاشة", "True while a finger touches the screen.", "صحيح طالما هناك إصبع على الشاشة.");
        b("ifJoystickMoved", "If joystick moved", "إذا تحركت العصا", "True when the joystick is not centered.", "صحيح عندما لا تكون العصا في المنتصف.");
        // physics
        b("setBodyType", "Set body type", "اضبط نوع الجسم", "DynamicBody, StaticBody or KinematicBody.", "DynamicBody أو StaticBody أو KinematicBody.");
        b("setGravityScaleOf", "Gravity scale", "مقياس الجاذبية", "0 = floats, 1 = normal, 2 = heavy.", "0 = يطفو، 1 = طبيعي، 2 = ثقيل.");
        b("destroyAfter", "Destroy after", "احذف بعد", "Removes the element after some seconds (bullets).", "يحذف العنصر بعد عدة ثوانٍ (الرصاص).");
        b("spawnCopy", "Spawn copy at", "أنشئ نسخة عند", "Clones an element at x, y. newName needs quotes.", "ينسخ العنصر عند x و y. الاسم الجديد يحتاج علامات تنصيص.");
        b("setWorldGravity", "Set gravity", "اضبط الجاذبية", "How fast things fall, in pixels per second² (800 = normal, 0 = space). Also makes the physics run in real time, so the speeds in blocks match pixels per second.", "سرعة سقوط الأشياء بالبكسل/ثانية² (800 = عادي، 0 = فضاء). وتجعل الفيزياء تعمل بالوقت الحقيقي لتطابق السرعات في البلوكات بكسل/ثانية.");
        b("setLanguage", "Set language", "اضبط اللغة", "Switches the game to another language (the codes you added in Game translations, like \"en\" or \"ar\"). The choice is remembered. TEXT elements written as @key update by themselves.", "يبدّل اللعبة إلى لغة أخرى (الرموز التي أضفتها في ترجمة اللعبة مثل \"en\" أو \"ar\"). يتم تذكر الاختيار، وتتحدّث النصوص المكتوبة بالشكل @key تلقائياً.");
        b("nextLanguage", "Next language", "اللغة التالية", "Goes to the next language in your list (and back to the first). Good for a language button.", "ينتقل إلى اللغة التالية في قائمتك (ثم يعود للأولى). مناسب لزر تغيير اللغة.");
        b("showTranslated", "Show translated text", "اعرض نصاً مترجماً", "Writes the translation of a key (from Game translations) into a TEXT element, in the current language. Keys need quotes.", "يكتب ترجمة مفتاح (من ترجمة اللعبة) في عنصر نص بحسب اللغة الحالية. المفتاح يحتاج علامات تنصيص.");
        b("ifLanguage", "If language is", "إذا كانت اللغة", "Runs the first part when the game language is the given code, otherwise the second part.", "ينفّذ الجزء الأول إن كانت لغة اللعبة هي الرمز المعطى، وإلا ينفّذ الجزء الثاني.");
        b("toastValue", "Show value", "أظهر قيمة", "Shows any value as a message (no quotes needed).", "يعرض أي قيمة كرسالة (بدون علامات تنصيص).");
        b("logValue", "Log value", "سجّل قيمة", "Writes any value to the debug log.", "يكتب أي قيمة في سجل التصحيح.");
    }

    // ---- built-in blocks registered from code (Blocks3D): same tables as above
    /** Value block display (en) -> {display ar, info en, info ar} of built-in value blocks registered from code. */
    private static final HashMap<String, String[]> REP_FIXED = new HashMap<>();

    static synchronized void builtinCategory(String cat, String en, String ar) { c(cat, en, ar); }

    static synchronized void builtinBlock(String title, String en, String ar, String infoEn, String infoAr) { b(title, en, ar, infoEn, infoAr); }

    static synchronized void builtinField(String name, String en, String ar) { if (!FIELD.containsKey(name)) f(name, en, ar); }

    static synchronized void builtinReporter(String display, String ar, String infoEn, String infoAr) {
        REP_FIXED.put(display, new String[]{ar, infoEn, infoAr});
    }

    private static synchronized String[] repFixed(String display) { return REP_FIXED.get(display); }

    /** Translation override from the language files (fr, pt, ru, es…), or null. */
    private static String tr(String key) {
        return com.star4droid.star2d.ui.Tr.t(key, null);
    }

    static String key(String s) {
        return s.replaceAll("[^A-Za-z0-9]+", "_");
    }

    // ---- texts of blocks that are not in the tables above: behavior blocks (generated) and the user's blocks (creator)
    private static final HashMap<String, String[]> DYN = new HashMap<>();           // key -> {label en, label ar}
    private static final HashMap<String, String> REP_AR = new HashMap<>();          // value block display (en) -> display (ar)
    private static final HashMap<String, Map<String, String>> DESC = new HashMap<>(); // key (title / value block display) -> {lang: text}
    /** Texts written in the block creator, in as many languages as wanted: action block title / value block display -> {lang: text}. */
    private static final HashMap<String, Map<String, String>> LABELS = new HashMap<>();

    /** Translations of the text on a custom block (key = block title; for a value block its English display with ___ slots). */
    public static synchronized void setLabels(String key, Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) LABELS.remove(key);
        else LABELS.put(key, labels);
    }

    /** The text in the app language only (no fallback to English), or null. */
    private static synchronized String labelExact(String key) {
        Map<String, String> m = LABELS.get(key);
        if (m == null) return null;
        String v = m.get(com.star4droid.star2d.ui.Tr.lang().toLowerCase(java.util.Locale.ROOT));
        return v == null || v.trim().isEmpty() ? null : v;
    }

    /** Forgets the texts of generated behavior blocks (they are registered again after the project's behaviors are rescanned). */
    public static synchronized void clearGenerated() {
        DYN.clear();
        REP_AR.clear();
        for (String k : GEN_DESC) DESC.remove(k);
        GEN_DESC.clear();
    }

    private static final java.util.ArrayList<String> GEN_DESC = new java.util.ArrayList<>();

    /** Label (en, ar) and description of a generated block. {@code valueDisplayAr} = Arabic display of a value block, or null. */
    public static synchronized void registerGenerated(String key, String labelEn, String labelAr, String valueDisplayAr, Map<String, String> desc) {
        DYN.put(key, new String[]{labelEn, labelAr == null || labelAr.isEmpty() ? labelEn : labelAr});
        if (valueDisplayAr != null && !valueDisplayAr.isEmpty()) REP_AR.put(key, valueDisplayAr);
        if (desc != null && !desc.isEmpty()) {
            DESC.put(key, desc);
            GEN_DESC.add(key);
        }
    }

    /** Descriptions written in the block creator (key = block title, or the display text of a value block). */
    public static synchronized void clearDescriptions() {
        DESC.clear();
        LABELS.clear();
        GEN_DESC.clear();
    }

    public static synchronized void setDescription(String key, Map<String, String> desc) {
        if (desc == null || desc.isEmpty()) DESC.remove(key);
        else DESC.put(key, desc);
    }

    /** The description in the app language, else English, else the first one written; null when the block has none. */
    public static String description(String key) {
        Map<String, String> m;
        synchronized (BlockInfo.class) {
            m = DESC.get(key);
        }
        return pickLang(m);
    }

    /** Description of a value block (key = its display): own language first, then the language file, then any written one. */
    public static String reporterInfo(String display) {
        String exact = descriptionExact(display);
        if (exact != null) return exact;
        String t = tr("blk_i_" + key(display));
        if (t != null) return t;
        String d = description(display);
        if (d != null) return d;
        String[] fx = repFixed(display);
        if (fx != null) return "ar".equals(com.star4droid.star2d.ui.Tr.lang()) ? fx[2] : fx[1];
        return null;
    }

    /** The description written in the app language itself (no fallback), or null. */
    public static String descriptionExact(String key) {
        Map<String, String> m;
        synchronized (BlockInfo.class) {
            m = DESC.get(key);
        }
        if (m == null) return null;
        String v = m.get(com.star4droid.star2d.ui.Tr.lang().toLowerCase(java.util.Locale.ROOT));
        return v == null || v.trim().isEmpty() ? null : v;
    }

    /** All the languages written for a block (never null). */
    public static synchronized Map<String, String> descriptions(String key) {
        Map<String, String> m = DESC.get(key);
        return m == null ? new java.util.LinkedHashMap<String, String>() : new java.util.LinkedHashMap<>(m);
    }

    /** App language, else "en", else the first language that has text. */
    public static String pickLang(Map<String, String> m) {
        return com.star4droid.star2d.blocks.BlockDesc.pick(m, com.star4droid.star2d.ui.Tr.lang());
    }

    public static String label(String title, boolean ar) {
        String own = labelExact(title);
        if (own != null) return own;
        String t = tr("blk_l_" + key(title));
        if (t != null) return t;
        String[] d;
        synchronized (BlockInfo.class) {
            d = DYN.get(title);
        }
        if (d != null) return d[ar ? 1 : 0];
        String[] v = B.get(title);
        return v == null ? title : v[ar ? 1 : 0];
    }

    public static String info(String title, boolean ar) {
        String exact = descriptionExact(title);
        if (exact != null) return exact;
        String tt = tr("blk_i_" + key(title));
        if (tt != null) return tt;
        String desc = description(title);
        if (desc != null) return desc;
        String t = tr("blk_i_" + key(title));
        if (t != null) return t;
        String[] v = B.get(title);
        return v == null ? null : v[ar ? 3 : 2];
    }

    public static String category(String cat, boolean ar) {
        String t = tr("blk_c_" + key(cat));
        if (t != null) return t;
        String[] v = CAT.get(cat);
        return v == null ? cat : v[ar ? 1 : 0];
    }

    private static final Map<String, Map<String, String>> FIELD_TR = new HashMap<>();

    /** Own names of a custom block's field in other languages: field name -> {language: text}. */
    public static synchronized void setFieldLabels(String field, Map<String, String> byLang) {
        if (byLang == null || byLang.isEmpty()) FIELD_TR.remove(field);
        else FIELD_TR.put(field, byLang);
    }

    private static synchronized String fieldExact(String label) {
        Map<String, String> m = FIELD_TR.get(label);
        if (m == null) return null;
        String v = m.get(com.star4droid.star2d.ui.Tr.lang().toLowerCase(java.util.Locale.ROOT));
        return v == null || v.trim().isEmpty() ? null : v;
    }

    public static String field(String label, boolean ar) {
        String own = fieldExact(label);
        if (own != null) return own;
        String t = tr("blk_f_" + key(label));
        if (t != null) return t;
        String[] v = FIELD.get(label);
        return v == null ? label : v[ar ? 1 : 0];
    }

    /** Value block text ("x of ___"), translated when the language file has it. Keeps the ___ slots. */
    public static String reporter(String display) {
        String own = labelExact(display);
        if (own != null && BlockModel.Param.count(own, "___") == BlockModel.Param.count(display, "___")) return own;
        String t = tr("blk_r_" + key(display));
        if (t == null) {
            String a;
            synchronized (BlockInfo.class) {
                a = REP_AR.get(display);
            }
            if (a != null && "ar".equals(com.star4droid.star2d.ui.Tr.lang()) && BlockModel.Param.count(a, "___") == BlockModel.Param.count(display, "___")) return a;
            String[] fx = repFixed(display);
            if (fx != null && "ar".equals(com.star4droid.star2d.ui.Tr.lang()) && BlockModel.Param.count(fx[0], "___") == BlockModel.Param.count(display, "___")) return fx[0];
            return display;
        }
        // a translation must keep the same number of slots
        if (BlockModel.Param.count(t, "___") != BlockModel.Param.count(display, "___")) return display;
        return t;
    }

    /** All built-in labels as properties lines (for translators). */
    public static String dump() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String[]> e : CAT.entrySet()) sb.append("blk_c_").append(key(e.getKey())).append('=').append(e.getValue()[0]).append('\n');
        for (java.util.Map.Entry<String, String[]> e : FIELD.entrySet()) sb.append("blk_f_").append(key(e.getKey())).append('=').append(e.getValue()[0]).append('\n');
        for (java.util.Map.Entry<String, String[]> e : B.entrySet()) {
            sb.append("blk_l_").append(key(e.getKey())).append('=').append(e.getValue()[0]).append('\n');
            sb.append("blk_i_").append(key(e.getKey())).append('=').append(e.getValue()[2]).append('\n');
        }
        return sb.toString();
    }
}
