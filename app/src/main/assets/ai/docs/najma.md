# Najma (نجمة): the Arabic scripting language

Use it only when the user asks for Arabic code or the project already has `.njm` files. Blocks stay the first choice; Java (`java-api`) stays available.

A `.njm` file sits where its Java class would be and the editor writes the `.java` next to it (generated, **same line numbers**, never edit it; it starts with `/*najma*/`). `build` converts every `.njm` first and reports Najma errors in Arabic with the `.njm` line. Write `.njm` files with `files`.

| Kind | Path | Becomes |
|---|---|---|
| Element script | `java/com/star4droid/Game/Scripts/<scene>/<Script>Script.njm` | class extending the scene's `ItemRefs` (every element is a name) |
| Scene script | `java/com/star4droid/Game/SceneScript/<scene>Script.njm` | class extending `SceneRefs` |
| Any class | `java/<pkg>/<Name>.njm` | top-level `صنف` / loose members wrapped in class `<Name>` |

## Syntax (one statement per line, blocks in `{ }`, `#` comments; Arabic or Latin digits; `،` or `,`)

```
متغير النقاط = 0                 # type inferred (int). 2.5 is float. خاصية = shown in Properties. ثابت = final
رقم_صحيح ع = 5   رقم_عشري س = 1.5   نص اسم = "x"   منطقي حي = صح   قائمة من نص أسماء = ["a"، "b"]
قاموس من نص إلى رقم_صحيح م = جديد قاموس من نص إلى رقم_صحيح()     مصفوفة من رقم_صحيح أ = [1، 2]
اجعل النقاط = 10      زد النقاط بمقدار 1      انقص النقاط      اجعل أسماء[0] = "z"
إذا النقاط أكبر من 10 و حي { } وإلا إذا النقاط == 3 { } وإلا { }      # و / او / ليس ; == != < > <= >= ; يساوي ، أكبر من ، أصغر من ، بين أ و ب ، تحتوي
كرر 5 مرات باستخدام ع { }      لكل س في أسماء { }      لكل ع من 1 إلى 10 بخطوة 2 { }      طالما شرط { }      اخرج / تخطى
حسب قيمة { حالة 1: ...  حالة 2، 3: ...  الباقي: ... }          # no fall-through
حاول { } إن حدث خطأ خ { } أخيراً { }      ارمِ خطأ "msg"
بعد 3 ثواني { }      كل ثانيتين { }      بعد 200 ملي ثانية { }      # Timer tasks; outer variables stay usable inside
مهمة اقفز(رقم_عشري قوة): منطقي { ارجع صح }     # untyped parameter = float; return type inferred when omitted
صنف عدو يرث شخصية ينفذ واجهة { عند_الانشاء(نص ا) { الأب(ا) } }      تعداد حالة_العدو: يتجول، يطارد      واجهة ...
"النقاط: {النقاط}"   # interpolation      (ع) ← ع > 5   # function value (for فلتر، جد، عد، لكل_عنصر)
5 ÷ 2 = 2.5 (÷ is real division; / is Java division)      30٪ = 0.3      == on text compares content
جافا { any Java statements or members }      استورد a.b.C باسم اسم
```

Events (element script): `عند البدء` `كل إطار` `عند النقر` `عند بداية اللمس` `عند نهاية اللمس` `عند الاصطدام` `عند نهاية الاصطدام` (other element = `الآخر`). Scene script: `عند البدء` `كل إطار` `عند الإيقاف` `عند الاستئناف`. In an element script the element itself is `نفسي`; elements of the scene are used by their names.

Element properties, read or set as `اجعل س player = 100` or `player.س` (pixels, y up, degrees): `س ص عرض ارتفاع دوران مرئي شفافية حجم سرعة_س سرعة_ص اسم نص لون صورة تقدم حي جسم`.

Sentences: `حرك X يميناً|يساراً|للأعلى|للأسفل بسرعة S` · `حرك X نحو Y بسرعة S` · `انقل X إلى A و B` · `ادفع X للأعلى بقوة P` · `أوقف X` · `احذف X` (safe in collisions) · `أخفِ X` / `أظهر X` · `دوّر X بزاوية D` · `اكتب T في X` · `اكتب T` (toast) · `شغل صوت "s"` · `افتح المشهد "s"` · `غير صورة X إلى "/a.png"` · `اجعل الكاميرا تتبع X` · `هز الكاميرا بقوة P` · `أضف V إلى L` · `احذف V من L` · `رتب L [تنازلياً]`.

Functions: `عشوائي_صحيح(a، b)` `عشوائي_عشري` `احتمال(30٪)` `اختر_عشوائيا(L)` `قرب` `مطلق` `جذر` `اس` `الأكبر` `الأصغر` `حدد(v، lo، hi)` `جيب` `جيب_تمام` `المسافة_بين(a، b)` `الزاوية_نحو(a، b)` `يلمس(a، b)` `نسخة(X، x، y)` `عنصر("name")` `يبدا_اسمه_ب(X، "coin")` `مفتاح_مضغوط("يمين")` `لمسة_جديدة()` `الشاشة_ملموسة()` `طول(x)` `قسم` `ادمج` `استبدل` `احفظ("k"، v)` `استرجع("k"، default)` `فلتر(L، (ع) ← ...)` `جد` `عد` `مجموع` `رتب` `اهتزاز(ms)` `جاذبية(x، y)` `تقريب_الكاميرا(z)`. Values: `لمس_س` `لمس_ص` `الزمن` `زمن_الإطار` `عرض_الشاشة` colors `أحمر أزرق ...`. Conversions: `رقم_صحيح(x)` `رقم_عشري(x)` `نص(x)`.

**Java / libGDX**: every class and member works by its Java name (`player.getBody().setBullet(صح)`, `Gdx.input.isTouched()`, `جديد Vector2(1, 2)`), and most have Arabic names made from the word list in `najma/src/main/resources/najma/lang.txt` (`الإدخال.ملموس()`, `متجه2(1، 2)`, `الراسم.دائرة(...)`, `ل.أضف(x)`, `ل.حجم()`). All scene (StageImp) methods can be called bare: `toast("hi")`, `openScene("m")`. When a `متغير` type cannot be inferred the error says so: write the type instead (`رقم_عشري س = ...`, or a Java type `Vector2 v = ...`).

```
# java/com/star4droid/Game/Scripts/main/playerScript.njm
خاصية السرعة = 200
متغير النقاط = 0
كل إطار {
    إذا مفتاح_مضغوط("يمين") { حرك نفسي يميناً بسرعة السرعة } وإلا { اجعل سرعة_س نفسي = 0 }
    إذا لمسة_جديدة() { ادفع نفسي للأعلى بقوة 500 }
}
عند الاصطدام {
    إذا يبدا_اسمه_ب(الآخر، "coin") { زد النقاط بمقدار 10؛ احذف الآخر؛ اكتب "النقاط: {النقاط}" في scoreText }
}
```
