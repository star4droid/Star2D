## Star2D 0.5.7 Beta

### What's new

**Najma (نجمة): code your game in Arabic**
- A new way to write scripts: plain Arabic sentences such as "حرك نفسي يميناً بسرعة ٢٠٠" (move me right at speed 200). Easy enough for children, complete enough for experienced developers.
- When you create an element script or a scene script you choose: Najma (Arabic) or Java. Both can live in the same project.
- Its own editor made for Arabic: colors, mistakes explained in Arabic as you type, and smart suggestions that know what comes next.
- A full guide inside the editor (the "؟" button): from "Hello world" to advanced topics, with more than 20 ready examples you can copy (player movement, coins, enemies, shooting, timers, saving the best score...).
- Games run exactly as fast as before and are not bigger.

**Path finding**
- New element "Path grid" (NAVGRID) in Add > Game systems, for 2D and 3D scenes: square or hexagon cells.
- Paint walls and slow ground (mud, water) on the canvas, or make walls from the static elements in one tap. Obstacles (doors, crates) are followed when they move and open their cells when they are destroyed.
- "Smart Chase" behaviors (2D and 3D): enemies run after the player along the shortest way, find a new way when a door closes, or run away.
- New variable type PathHolder: draw patrol paths on the canvas, or fill them with "Find path".
- 21 Navigation blocks (walk to, chase, flee, follow path, obstacles, cells...) and path events.

**Timeline**
- Timeline panel: keyframe animations of position, rotation, size, color, opacity, image, text and variables, for 2D and 3D elements.
- Record mode: move the playhead and change the element, keys are added by themselves. 10 transitions with a preview, markers, loop / ping-pong, relative timelines, play on another element (@self).
- Timeline blocks and quick Tween blocks (with delay, repeat and back-and-forth).

**Local multiplayer (Wi-Fi / hotspot)**
- New element "Network play" (NETWORK): rooms, max players, player template copied for each player, ready-made lobby.
- "Network" card on every element: the same on every phone, moved by the host or by a player.
- Network blocks: host, search, join, start for everybody, messages, spawn, ownership, and 20 value blocks; network events.

**Examples**
- Maze Chase: tap to walk, smart ghosts, a guard on a patrol path, a key that opens a door, and a hexagon maze.
- Wi-Fi Stars: lobby + arena, each phone moves its own ball, the host spawns the stars and counts the points.

### العربية
- **لغة نجمة، اكتب كود لعبتك بالعربية:** جمل عربية بسيطة مثل «حرك نفسي يميناً بسرعة ٢٠٠»، سهلة للأطفال وكاملة للمحترفين. عند إنشاء سكربت لعنصر أو مشهد تختار: نجمة (عربي) أو Java. لها محرر خاص بالعربية بألوان واقتراحات ذكية وأخطاء مشروحة بالعربية، وشرح كامل داخل المحرر (زر «؟») من «مرحباً بالعالم» حتى الاحتراف مع أكثر من ٢٠ مثالاً جاهزاً للنسخ. والألعاب تعمل بنفس السرعة والحجم.
- **إيجاد المسار:** عنصر شبكة المسارات (مربعة أو سداسية) لـ 2D و 3D، رسم الجدران والأرض البطيئة، عوائق متحركة، سلوك المطاردة الذكية، نوع متغير PathHolder لمسارات الدوريات، و21 بلوك ملاحة.
- **الخط الزمني:** تحريك بالإطارات المفتاحية مع وضع التسجيل، 10 انتقالات، علامات، تكرار وذهاب وإياب، وبلوكات Tween.
- **اللعب الجماعي عبر الواي فاي:** عنصر الشبكة، صالة انتظار جاهزة، بطاقة الشبكة لكل عنصر، وبلوكات وأحداث الشبكة.
- **أمثلة جديدة:** Maze Chase و Wi-Fi Stars.

## Star2D 0.5.6 Beta

### What's new

**Market (experimental)**
- Share your projects with everyone and download projects made by others.
- You can also share a "game only" version: people just play it, they can't open or copy your blocks and code.
- Browse as a grid or a list, update or delete what you shared, and edit its description and icon.
- If a project was made with a different version of the engine, you get a warning before downloading it.
- Up to 2 uploads per day.

**Ads**
- A small banner now appears on the projects screen and inside the editor.
- A short ad may appear after you close a project.
- You can watch a video to remove ads for 1 hour (Support page).
- No ads at all while your credit balance is $1 or more.

**Fixes**
- Fixed a crash when opening the editor ("Error reading file: files/skins/uiskin.json"): the app now has a second way to read its own files, and reports exactly what is wrong if it still fails.
- The APK is now signed in the old and the new way, so every installer accepts it (some said "not signed" or "package invalid").
- M2M Motion: if your model already has a skeleton, it is detected and used as it is (animations are moved onto it; bones are mapped automatically and you are told how many matched, or map them by hand). A button at the top switches between "build a new skeleton" and "use the model's skeleton". The Download button is now at the top of the animation list.
- Rig block: choose the skeleton standard (Mixamo, ActorCore, Unity, Unreal, VRM) and the "Other" type.
- The Mesh2Motion screen now fits phones and tablets.
- AI Workspace: model versions can be chosen, a 2x2 view for multi-angle results, easier wire connections, and nothing starts if your credits are not enough.

### العربية

**المتجر (تجريبي)**
- شارك مشاريعك مع الجميع ونزّل مشاريع صنعها الآخرون.
- يمكنك أيضاً مشاركة "اللعبة فقط": يلعبها الناس مباشرة، ولا يستطيعون فتحها أو نسخ البلوكات والكود.
- اعرض المشاريع كشبكة أو كقائمة، وحدّث أو احذف ما شاركته، وعدّل وصفه وأيقونته.
- إذا كان المشروع مصنوعاً بإصدار مختلف من المحرك يظهر لك تنبيه قبل التنزيل.
- حتى مشروعين في اليوم.

**الإعلانات**
- يظهر الآن شريط إعلان صغير في شاشة المشاريع وداخل المحرر.
- قد يظهر إعلان قصير بعد إغلاق مشروع.
- يمكنك مشاهدة فيديو لإزالة الإعلانات ساعة كاملة (من صفحة الدعم).
- لا إعلانات إطلاقاً ما دام رصيدك دولاراً واحداً أو أكثر.

**إصلاحات**
- إصلاح انهيار عند فتح المحرر (رسالة "Error reading file: files/skins/uiskin.json"): صار للتطبيق طريقة ثانية لقراءة ملفاته، ويذكر السبب بدقة إن فشلت.
- توقيع الـ APK صار بالطريقتين القديمة والجديدة ليقبله كل مثبّت (بعضها كان يقول "غير موقّع" أو "الحزمة غير صالحة").
- M2M Motion: إذا كان نموذجك يحتوي هيكلاً عظمياً يُكتشف تلقائياً ويُستخدم كما هو (تُنقل الأنيميشن عليه؛ تُربط العظام تلقائياً ويظهر لك عدد المربوط، أو اربطها يدوياً). زر في الأعلى يبدّل بين "بناء هيكل جديد" و"استخدام هيكل النموذج". وزر التنزيل صار في أعلى قائمة الأنيميشن.
- بلوكة Rig: اختيار معيار الهيكل (Mixamo وActorCore وUnity وUnreal وVRM) ونوع "أخرى".
- شاشة Mesh2Motion صارت تناسب الهواتف والأجهزة اللوحية.
- مساحة عمل الذكاء الاصطناعي: اختيار إصدار النموذج، وعرض 2×2 للنتائج من عدة زوايا، وتوصيل أسهل بين العناصر، ولا يبدأ شيء إذا كان رصيدك غير كافٍ.

## 🎉 Star2D 0.5.2 Beta — 3D

### English
**3D engine**
- 3D scenes next to 2D ones (choose 2D/3D for a new project or scene)
- Objects, primitives and GLB / glTF models (import from the Add panel), lights (point, spot, directional), shadows, sky, fog, water, terrain, post effects
- Bullet physics: colliders (box, sphere, capsule, mesh), triggers, rays, vehicles
- 35 ready 3D behaviors (car, first/third-person controllers, flashlight, path follower…) and 3D blocks (126 blocks + 80 values)
- Examples: City 3D, Horror House 3D, Endless Road 3D, Sea Dock 3D

**3D editor**
- Viewport with orbit camera, move / rotate / scale gizmos, pinch zoom from the current zoom
- Collider view and editor, 2D layer button, 3D view settings, per-element properties

**Export**
- New export dialog: game icon picker, clear fields, orientation, options, landscape layout, fully translated
- "Merge images into tilesets" fixed (merged images and animations now show correctly)
- "Phones only (ARM)" option for a smaller APK
- Overwriting an existing APK no longer breaks it

**Editor**
- Redesigned custom collider editor: tap to add points, drag to move, undo, portrait and landscape
- Many fixes; 6 languages (English, Arabic, French, Portuguese, Russian, Spanish)

### العربية
**محرك ثلاثي الأبعاد**
- مشاهد 3D بجانب مشاهد 2D (اختر 2D أو 3D عند إنشاء مشروع أو مشهد)
- عناصر وأشكال أساسية ومجسمات GLB / glTF (استيراد من لوحة الإضافة)، إضاءة (نقطية، كشاف، اتجاهية)، ظلال، سماء، ضباب، ماء، تضاريس، مؤثرات
- فيزياء Bullet: أشكال تصادم (صندوق، كرة، كبسولة، مجسم)، مناطق تفعيل، أشعة، سيارات
- 35 سلوكًا جاهزًا للـ 3D (سيارة، تحكم بمنظور أول وثالث، كشاف، تتبع مسار…) وبلوكات 3D (126 بلوك + 80 قيمة)
- أمثلة: المدينة 3D، بيت الرعب 3D، الطريق اللانهائي 3D، رصيف البحر 3D

**محرر 3D**
- عرض بكاميرا دوّارة، أدوات تحريك / تدوير / تحجيم، تكبير بإصبعين يبدأ من التكبير الحالي
- عرض وتعديل أشكال التصادم، زر طبقة 2D، إعدادات العرض، خصائص لكل عنصر

**التصدير**
- نافذة تصدير جديدة: اختيار أيقونة اللعبة، حقول واضحة، الاتجاه، خيارات، تصميم أفقي، مترجمة بالكامل
- إصلاح "دمج الصور في tilesets" (الصور والأنيميشن تظهر بشكل صحيح)
- خيار "للهواتف فقط (ARM)" لحجم أصغر
- الكتابة فوق ملف APK موجود لم تعد تفسده

**المحرر**
- محرر جديد للعنصر المخصص: المس لإضافة نقطة، اسحب للتحريك، تراجع، عمودي وأفقي
- إصلاحات كثيرة؛ 6 لغات (العربية، الإنجليزية، الفرنسية، البرتغالية، الروسية، الإسبانية)
