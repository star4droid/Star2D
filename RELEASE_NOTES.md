## Star2D 0.5.3 Beta — Market

### English
- **Market** (experimental): upload projects or "game only" packages (compiled game + assets, no blocks/events/code, played directly and not openable in the editor), browse as grid or list, download, update, edit and delete your uploads. The engine version is stored with each upload and a warning asks for confirmation when you download one made with another version. Two uploads per day by default (the admin can raise it per user).
- **Ads** (AdMob): one banner on the projects screen, a rare interstitial after closing a project, an optional rewarded video (1 hour without ads). Users who buy credits see no ads while their balance is $1 or more. Builds without ad ids show a small dismissible notice instead.
- **AI Workspace**: model versions on Rig/Texture/Segment/Retopology, Rig analyze, Polycount, multiview 2x2 grid with select/delete, bigger wire touch area, nothing starts (and no warning) when credits are not enough.
- **M2M Motion**: the screen now fits phones and tablets in portrait and landscape.

### العربية
- **المتجر** (تجريبي): ارفع مشاريع أو "لعبة فقط" (اللعبة المبنية والملفات بدون بلوكات/أحداث/كود، تُلعب مباشرة ولا تُفتح في المحرر)، عرض شبكة أو قائمة، تنزيل وتحديث وتعديل وحذف ما رفعته. يُحفظ إصدار المحرك مع كل رفع، وعند تنزيل مشروع من إصدار مختلف يظهر تنبيه للتأكيد. مشروعان في اليوم افتراضياً (والمشرف يزيدها لكل مستخدم).
- **الإعلانات** (AdMob): بانر واحد في شاشة المشاريع، وإعلان بيني نادر بعد إغلاق مشروع، وفيديو اختياري (ساعة بلا إعلانات). من يشتري رصيداً لا يرى إعلانات ما دام رصيده 1$ أو أكثر. النسخ بدون معرّفات إعلانات تعرض تنبيهاً صغيراً قابلاً للإخفاء.
- **AI Workspace**: إصدارات النماذج في Rig/Texture/Segment/Retopology، تحليل Rig، Polycount، شبكة multiview 2×2 مع اختيار/حذف، توصيلات أسهل، ولا يبدأ شيء (بلا تحذير) عند نقص الرصيد.
- **M2M Motion**: الشاشة تناسب الهواتف والأجهزة اللوحية عمودياً وأفقياً.

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
