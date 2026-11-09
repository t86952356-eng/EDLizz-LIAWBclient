# LIZZ_HANDOFF.md — ملف تسليم عميل Lizz (يتحدّث في كل مهمة)

> ⚠️ **قاعدة دائمة من المستخدم**: هذا الملف يُحدَّث **في نهاية كل مهمة**.
> - إذا نبّه المستخدم على مشكلة في مهمة ما (برمجية كانت أو تصميمية/شكلية)، أضِف نقطة
>   تشرحها تحت «سجل الأخطاء والدروس» بحيث لا تتكرر في المهام القادمة.
> - إذا انتهت المهمة دون تنبيهات، أضِف سطراً في «سجل المهام» بعلامة: تمت دون مشاكل.
> - الـ changelog داخل إصدارات GitHub Release يكون **بالإنجليزية فقط**.
> - بعد كل مهمة: بناء السورس المعدّل ونشر Release يحتوي **source.zip + مود JAR** (الطريقة
>   الكاملة في `AI_BUILD_GUIDE.md`).

---

## 1) معلومات العميل

| البند | القيمة |
|---|---|
| اسم العميل | **Lizz** (داخل الكود: `CLIENT_NAME = "L"+"izz"`) — يعرف أيضاً بـ **LAWIB / LIAWB client** |
| الإصدار الحالي | **b12.9** (`LiquidBounce.kt` → `clientVersionText` و`gradle.properties` → `mod_version`) |
| اللعبة | Minecraft **1.8.9** — Forge `11.15.1.2318` — mappings `stable_22` |
| الأساس | **LiquidBounce Legacy** (فرع المجتمع النهائي، آخر إصدار رسمي منه b100) |
| مود id | `Lizz` — الاسم الظاهر في mcmod.info: `LAWIB` |
| المؤلفون (mcmod.info) | CCBlueX، PickBowen، bzym2، RainRuin، sunbox |
| عنوان النافذة | `LIAWB client 1.8.9 b12.9 \| 免费注册加群：815061613` (عنوان مجتمع صيني — رقم مجموعة QQ) |
| التوزيع الرسمي | عبر Discord العميل: **discord.gg/Y2U2gWyRUN** (فيديو «Lizz 7.0 (fork lb b100)») |
| المستودع | `https://github.com/t86952356-eng/EDLizz-LIAWBclient` (عام، فرع `main`) |
| سجل التسمية | الفورك بدأ بـ «LIAWB client (chat prefix rebrand)» من LiquidBounce Legacy، ثم أُضيف MusicPlayer، ثم ClickGUI بأسلوب Fatality في b12.7، ثم إصدار تخفيف الحجم في b12.8، ثم إعادة تسمية الموديولات والأوضاع (WatchdogNCP/UpdatedNCP/FastBridge/Verus/AimAssist/MoreKB) في b12.9 |

**مواقع العلامة التجارية في السورس** (عند أي إعادة تسمية مستقبلية):
- `src/main/java/net/ccbluex/liquidbounce/LiquidBounce.kt` — `CLIENT_NAME`، `clientTitle`، `clientVersionText`.
- `src/main/resources/mcmod.info` — modid/name/description/authors.
- `ui/client/clickgui` الجديد (واجهة ويب بعنوان «Lizz ClickGUI» في `web/WebServer.kt`).
- `WaterMark.kt` — أنماط من بينها `Lizz Morden`.
- حزمة `net.ccbluex.liquidbounce` نفسها **لم تُعَد تسميتها** (فورك LB قياسي).

## 2) البناء والنشر

**الطريقة الكاملة الموثقة خطوة بخطوة في `AI_BUILD_GUIDE.md`** (ملف إلزامي القراءة قبل
أي بناء). خلاصة سريعة:
- JDK **8** (ليس 17) + Gradle 6.9.4 + Kotlin 2.0.21.
- الإضافات الحرجة موثّنة في `maven-repo/` بسبب احتلال jitpack (نوفمبر 2026) — **لا تحذفها**.
- النشر: اعتماد على `main` ثم `git push origin main:release/build` ← workflow
  `.github/workflows/release.yml` يبني وينشر Release بتاغ `v{mod_version}-mc1.8.9`
  يحتوي `lizz-{mod_version}.jar` + `lizz-{mod_version}-source.zip`.

## 3) معلومات برمجية

### البنية العامة
- **السورس**: `src/main/java` (ملفات `.kt` و`.java` معاً — 670 Kotlin + 125 Java)،
  والموارد في `src/main/resources` (mcmod.info، `liquidbounce.forge.mixins.json`،
  `liquidbounce_at.cfg`، `assets/`، `instructions.html`).
- **الحزم الأساسية** تحت `net.ccbluex.liquidbounce`:
  - `LiquidBounce.kt` — نقطة الدخول (`@Mod`-style Forge mod + MixinLoader).
  - `features/module/modules/` — الموديولات مصنفة: `combat`, `exploit`, `fun`, `misc`,
    `movement`, `player`, `render`, `world`. الأنماط الفرعية في مجلدات داخلية
    (speedmodes/flymodes/longjumpmodes/disablers ...).
  - `event/` — ناقل أحداث بأسلوب `handler<EventType> { ... }` (الأحداث في `Events.kt`،
    و`async/` للأحداث غير المتزامنة).
  - `value/` — نظام إعدادات موحد: `Value<T>` + دوال مساعدة (`boolean`, `integer`,
    `float`, `text`, `choices`, ...) تُستخدم كـ `private val x by boolean("Name", false)`.
  - `injection/forge/mixins/` — مكسرات مصنفة: `block`, `client`, `entity`, `gui`, `item`,
    `network`, `packets`, `render`, `resources`, `tweaks`, `world`.
  - `ui/client/clickgui` — واجهة ClickGUI (أُعيدت هيكلتها في b12.7 بأسلوب Fatality، مع
    واجهة ويب عبر `web/WebServer.kt`).
  - `file/` — حفظ/تحميل الإعدادات (FileManager + FileConfig + configs).
  - `script/` — نظام سكربتات العميل (ScriptManager + api + remapper).
  - `auth/`, `cape/`, `tabs/`, `lang/`, `api/`, `utils/`.
- **نمط الموديول القياسي**: `object Xxx : Module("Name", Category.X, ...)` مع خصائص
  `by boolean/float/...` ومعالجات `handler<Event>`. (مثال مرجعي نظيف: `AutoTool.kt`.)

### نظام البناء (خلاصات حرجة)
- `shadowJar` ينتج الـ jar النهائي بدمج تبعيات `include` (Kotlin stdlib، coroutines،
  Mixin 0.7.11، Elixir، TheAltening، DiscordIPC، okhttp، flatlaf، xchart، jlayer،
  **وكل jars في `libs/`**: ViaVersion/ViaBackwards/ViaRewind (مقلّصة لجافا 8)، mcef،
  nashorn، filters).
- ثم `reobfShadowJar` يحوّل الأسماء إلى SRG (**إلزامي** وإلا انهار المود على اللعبة
  الحقيقية — موصول بـ `shadowJar.finalizedBy tasks.reobfShadowJar`؛ لا تلمسه).
- ثم `optimizeJar` (أضيف في b12.8): يزيل LocalVariableTable من كل classes (يحفظ
  LineNumberTable للـ stack traces) ويعيد ضغط الـ jar بمستوى 9 — موصول بـ
  `tasks.reobfShadowJar.finalizedBy tasks.optimizeJar`. قواعد الحجم الكاملة في
  `AI_BUILD_GUIDE.md` §10 — **لا تُرجع حشواً ميتاً** (lombok include / pdfbox /
  src/main.rar / أصول nextgen-clickgui / أصوات fdpclient.wav).
- `jar { enabled = false }` — الناتج الوحيد المهم: `build/libs/lizz-{version}.jar`.
- `compileKotlin` يستثني `**/clickgui/style/styles/nextgen/**` — كود ClickGUI «nextgen»
  منقول من FDPClient ويعتمد واجهات FDP غير متوفرة هنا؛ **يبقى مستثنى** حتى تُنقل تبعياته.
- `zip_include/` غير موجود في المستودع؛ مهمة `copyZipInclude` تتجاهله بصمت (طبيعي).
- `addons/` (ملفات .kt سائبة مثل GrimFly.kt وAntiVelocity.kt) **ليست جزءاً من البناء**
  — مرجع/مسودات فقط.
- معلومات `mcmod.info` تُوسَّع من `mod_version` و`minecraft_version` وقت البناء.

### ملاحظات إصدار/توافق
- التغيير في `mod_version` (`gradle.properties`) وحده يكفي لإصدار جديد (التاغ والاسم
  يتبعانه تلقائياً في الـ workflow).
- الإعدادات المحفوظة للعميل تربط الموديولات بأسمائها — إعادة تسمية موديول تحتاج alias
  للتوافق (نفس مبدأ «ملف/كامل» المعتمد في AtroNextGen).

## 4) سجل الأخطاء والدروس (محدَّث في كل مهمة)

### المهمة 1 — نوفمبر 2026 — «البناء والنشر عبر GitHub مثل Atro NextGen»
الهدف: بناء السورس إلى مود JAR عبر GitHub Actions ونشر Release يحتوي source.zip + JAR،
وإنشاء ملفي التوثيق (هذا الملف + `AI_BUILD_GUIDE.md`).

| # | الخطأ | السبب | الحل المطبّق |
|---|---|---|---|
| 1 | exit code 126 من `./gradlew` على Actions | `gradlew` مسجل بصلاحية 644 وليس 755 | `git update-index --chmod=+x gradlew` + اعتماد (ولا تنسَ `chmod +x gradlew` محلياً) |
| 2 | `could not resolve plugin artifact 'com.github.ccbluex:ForgeGradle:a3d86a59c0'` بعد ~20 ثانية من بدء Gradle | **jitpack حذف ملفات الإضافة من تخزينه** (الحالة "ok" والملفات 404). تأكد أيضاً أن تبعياتها `jAstyle` محذوفة، بينما `lzma-java` و`named-regexp` متوفرتان على Maven Central، و`SpecialSource` و`mixingradle` متوفرتان على jitpack | بناء ForgeGradle **من المصدر بنفس الـ commit** (Gradle 7.0.2 + JDK 8 + publish) وإعادة كتابة إحداثيات POM إلى صيغة jitpack، ثم **توثين** ForgeGradle + mixingradle + jAstyle في `maven-repo/` داخل المستودع وجعله أول مستودع في `settings.gradle` |
| 3 | `inconsistent module metadata: bad version` أثناء بناء ForgeGradle | وسم `<version>` داخل POM الخاص بـ SpecialSource (`1.7.4-SNAPSHOT`) لا يطابق اسم مجلد الإصدار المثبّت | مطابقة النسخة داخل الـ POM مع المسار حرفياً (`1.7.4-SpecialSource-1.6.1-gf5cd633-34`) |
| 4 | 429 Too Many Requests من repo.maven.apache.org داخل sandbox | مشكلة البيئة المحلية المعروفة (طلبات Gradle متزامنة) | مرآة `maven-central.storage-download.googleapis.com` **مؤقتاً للبناء المحلي فقط** (لا تُدفع للمستودع)؛ على Actions لا تحدث |
| 5 | عملية Gradle «تعلّق» بعد BUILD SUCCESSFUL داخل sandbox | الـ daemon أحادي الاستخدام لا يُغلق دائماً | تشغيل البناءات في الخلفية + قراءة السجل؛ نجاح البناء يستدل به من السجل |
| 6 | توقّع مبدئي بأن الجافا 17 | قياس خاطئ من مشروع آخر (AtroNextGen يستخدم 21) | الحسم من ملفات البناء: **جافا 8** (sourceCompatibility 1.8 + Gradle 6.9.4 لا يدعم 17) — موثق في AI_BUILD_GUIDE §2 |

**النتيجة**: نجاح البناء على GitHub Actions (run 42381010275) وإصدار
[`vb12.7-mc1.8.9`](https://github.com/t86952356-eng/EDLizz-LIAWBclient/releases/tag/vb12.7-mc1.8.9)
بمرفقات `lizz-b12.7.jar` (43.8MB، مُتحقق من reobf SRG وmanifest وmcmod.info)
و`lizz-b12.7-source.zip` (58.7MB).

### المهمة 2 — نوفمبر 2026 — «تخفيف حجم العميل بشكل ممتاز» (b12.8)
الهدف: تقليص حجم مرفقات الـ Release (JAR كان 43.8MB وsource.zip كان 58.7MB) دون أي
تغيير وظيفي. قواعد الحجم الجديدة موثقة في `AI_BUILD_GUIDE.md` §10.

| # | المشكلة/الدرس | التفصيل | الحل |
|---|---|---|---|
| 1 | حشو مكتشف في الـ JAR | تحليل محتوى `lizz-b12.7.jar`: pdfbox+fontbox (3.2MB) قادمة عبر xchart لتصدير PDF/SVG غير المستخدم، lombok (~2MB) مظللة بلا فائدة، أصول nextgen-clickgui (5.4MB) يعود إليها كود مستثنى من الترجمة، 4 أصوات wav (0.75MB) بلا أي مرجع، وPNGs/JPEGs غير مضغوطة (~14MB) | استثناءات تبعيات + compileOnly لحساب lombok + حذف الأصول الميتة + quantize للـ PNG (256 لون) + إعادة ترميز JPEG q70 |
| 2 | `src/main.rar` (18.5MB) داخل مجلد src | أرشيف RAR غريب معتمد في المستودع بلا أي مرجع — يضخم source.zip فقط | حُذف من المستودع (موجود في تاريخ git) |
| 3 | **`visitLocalVariable` ليس على ClassVisitor** | المحاولة الأولى لمهمة optimizeJar وضعت إزالة LVT على مستوى ClassVisitor — ASM لا يستدعيها إطلاقاً (LVT attribute على مستوى Method) وفشل صامت بدون الاختبار | اختبار مستقل بـ ASM كشف الخطأ؛ الصيغة الصحيحة: `visitMethod` يلف الـ MethodVisitor الأصلي بـ MethodVisitor يفرغ `visitLocalVariable` و`visitLocalVariableAnnotation` — مُختبر: LVT تُزال وLNT/Frames تبقى |
| 4 | **ترتيب تعريف المهام في build.gradle** | `tasks.reobfShadowJar.finalizedBy ...` يجب أن يأتي بعد بلوك `reobf { }` وإلا فشل configuration (المهمة غير موجودة بعد) | نُقلت كتلة optimizeJar كاملة إلى بعد بلوك reobf مع تعليق تحذيري |
| 5 | javap غير متوفر في sandbox (JRE فقط) | تحقّقات سابقة كانت تمر صامتة (grep -c يرجع 0 دائماً عند غياب الأمر) | مسبار مستقل DebugInfoProbe.java بـ ASM يعدّ callbacks فعلياً — النتائج الحقيقية: LNT=180 في LiquidBounce.class |
| 6 | تحميل JDK 8 من adoptium يفشل بصمت أحياناً | أول محاولة nohup+curl -s ماتت بلا أثر في السجل | إعادة المحاولة مع `-w "%{http_code}"` وظهور الأخطاء نجحت من أول مرة (98.7MB) |
| 7 | تشابك تبعيات xchart غير بديهي | xchart 3.8.8 يسحب `de.rototor.pdfbox:graphics2d` (وليس graphics2d-pdfbox) + `de.erichseifert.vectorgraphics2d:VectorGraphics2D` | تأكيد الأسماء من POM الرسمي قبل كتابة excludes — لا تخمّن أسماء الـ modules |
| 8 | **فشل configuration: `The current scope already contains a variable of the name desc`** | Groovy (عكس Java) يرفض تظليل اسم باراميتر الدالة الخارجية (`visitMethod(... String desc ...)`) داخل anonymous class متداخل — لا يعجبه تكرار `desc` في الداخل | أعد تسمية باراميترات الدوال الداخلية (`String d2`) — تذكر: Groovy أصرم من Java في قواعد scope داخل anonymous classes |
| 9 | البناء المحلي الكامل يُقتل بصمت بالذاكرة في الـ sandbox | FG setup + ترجمة 795 ملفاً تحتاج أكثر من ذاكرة 4GB المتاحة؛ العملية تختفي بلا رسالة خطأ | اكتفِ محلياً بـ configuration-phase validation (يكفي لكشف أخطاء build.gradle) ودع `gradle build` الكامل لـ GitHub Actions (7GB) — القناة الموثوقة |
| 10 | مرايا Gradle تتجمد أحياناً منتصف التحميل | huawei أوقف النقل عند 64MB ثم عاد بـ 9MB/s، وtencent بطيء (~200KB/s) | استأنف بـ `curl -C -` من huawei بعد التجمّد؛ بعد اكتمال الـ zip فكّه وشغّل `bin/gradle` مباشرة بدلاً من الـ wrapper |

**النتيجة**: نجاح البناء على GitHub Actions (run 42446020034) وإصدار
[`vb12.8-mc1.8.9`](https://github.com/t86952356-eng/EDLizz-LIAWBclient/releases/tag/vb12.8-mc1.8.9)
بمرفقات `lizz-b12.8.jar` (**25.12MB — كان 43.77MB، ‎-43%**)
و`lizz-b12.8-source.zip` (**28.43MB — كان 58.67MB، ‎-52%**)، والـ changelog بالإنجليزية من
`RELEASE_NOTES.md`. التحقق بعد النشر: zip سليم، صفر entries للحشو الميت (pdfbox/lombok/...)،
manifest وmcmod.info صحيحان (b12.8)، SRG reobf مؤكد (47 اسم SRG في MixinEntity)،
LVT مُزالة (55→0، 408→0) مع بقاء LNT (180، 1329) وFrames كاملة.

### المهمة 3 — نوفمبر 2026 — «إعادة تسمية الموديولات + نظام Mode/Custom» (b12.9)
الهدف: (1) إعادة تسمية شاملة: `Aimbot`→**AimAssist**، `SuperKnockback`→**MoreKB**، كل خيار فيه God
في Scaffold/Scaffold2/Tower→**FastBridge** (شامل GodBridgePitch→FastBridgePitch واسم الخاصية
الداخلية)، أوضاع Fly (Hypixel→**WatchdogNCP**، BoostHypixel→**WatchdogNCP-Stable**،
FreeHypixel→**WatchdogNCP-Free**، BlocksMC→**UpdatedNCP**، BlocksMC2→**UpdatedNCP2**)، أوضاع
NoFall (Hypixel→**WatchdogNCP**، HypixelTimer→**WatchdogNCP-Packet**)، أوضاع Speed
(hypixelHop→**WatchdogNCP-Hop**، HypixelLowHop→**WatchdogNCP-LowHop**، BlocksmcHop→**Verus**،
PredictionTimer2→**Watchdog-Protection**، PredictionTimer→**Watchdog-Protection2**)، ووضع
Velocity (HypixelAir→**WatchdogNCP**)؛ (2) إضافة خيار `Mode` فيه مود `Custom` يضم كل الإعدادات
الحالية ولا تظهر إلا باختياره + إظهار الوضع كـ tag في arraylist في **NoSlow** و**Disabler**؛
(3) رفع حد `Hypixel-BoostDelay` في Fly من 2000 إلى **10000**؛ (4) حذف **LiquidWalk**
و**BedGodMode** و**GodMode** بالكامل.

| # | الخطأ | السبب | الحل المطبّق |
|---|---|---|---|
| 1 | فشل البناء الأول (run 42621311296) بـ `Unresolved reference 'Blink'` و`'Liquids'` في grim.kt | عند إزالة استيرادات الموديولات المحذوفة من `grim.kt` (الملف المُستورد لكل شيء) استُبدل سطر BedGodMode بـ `exploit.Blink` وسطر LiquidWalk بـ `movement.Liquids` — لكن الملفين موجودان فعلياً في حزمتين مختلفتين: `player.Blink` و`world.Liquids` (والاستيرادان الصحيحان موجودان أصلاً في مواضعهما الأبجدية في نفس الملف!) | حذف السطرين الخاطئين فقط — **الدرس: عند حذف موديول لا تستبدل استيراده بـ «جاره»؛ إما احذف السطر نهائياً أو تحقق من مسار الملف الفعلي** (أُضيف درس مرافق في AI_BUILD_GUIDE §6-8) |
| 2 | `zh_TW.json` لا يمر فحص JSON قياسي رغم سلامة التعديلات | خطأ تاريخي موروث في الملف نفسه (فاصلة زائدة بعد `contributors`) موجود قبل هذه المهمة؛ محلّل Gson المتسامح في اللعبة يتقبله (عمل في b12.7 وb12.8) | لم يُعدل شيء — سُجل هنا حتى لا يُفاجئ فحص python json مستقبلي؛ باقي ملفات اللغة السبعة سليمة 100% |
| 3 | تعارض اسم قيمة محتمل في Disabler | كانت هناك قيمة اسمها `Mode` داخل NoRotationDisabler؛ إضافة `Mode` جديد للواجهة كان سيتعارض معها في نظام الإعدادات (الحفظ بالاسم) | إعادة تسمية القيمة الداخلية إلى **`ModifyMode`** — القيم المحفوظة القديمة لها تعود للافتراضي بأمان (ListValue يتجاهل الأسماء غير الموجودة، بلا انهيار) |

**ملاحظات تنفيذ**: أعيدت تسمية الحزم الداخلية أيضاً (`flymodes/hypixel`→`flymodes/watchdogncp`،
`flymodes/blocksmc`→`flymodes/updatedncp`، `speedmodes/hypixel`→`speedmodes/watchdogncp`)؛
حُدّثت مفاتيح اللغات السبعة (aimAssist/moreKB) وحُذفت مفاتيح الموديولات الثلاثة المحذوفة؛
حُذف هوك LiquidWalk من `MixinEntityLivingBase.java` وحُدّث اسم هوك MoreKB في
`MixinMovementInputFromOptions.java`؛ أسماء إعدادات Fly (`Hypixel-Boost`/`Hypixel-BoostDelay`/
`Hypixel-BoostTimer`) بقيت كما هي بناءً على صياغة المستخدم (طلب تغيير الحد فقط).

**النتيجة**: نجاح البناء على GitHub Actions (run 42621643210) وإصدار
[`vb12.9-mc1.8.9`](https://github.com/t86952356-eng/EDLizz-LIAWBclient/releases/tag/vb12.9-mc1.8.9)
بمرفقات `lizz-b12.9.jar` (**25.11MB** — ضمن النطاق المطلوب 20-26MB) و`lizz-b12.9-source.zip`
(**28.43MB**)، والـ changelog بالإنجليزية من `RELEASE_NOTES.md`. التحقق بعد النشر: manifest سليم
(MixinConfigs/FMLAT/TweakClass/FMLCorePlugin)، mcmod.info = b12.9، SRG reobf مؤكد (47 في
MixinEntity)، كل الكلاسات الجديدة موجودة (AimAssist/MoreKB/WatchdogNCP*/UpdatedNCP*/
WatchdogProtection*/VerusSpeed/FastBridge في Scaffold=11)، صفر كلاسات قديمة، و«Custom»
و«ModifyMode» مؤكدتان في NoSlow/Disabler.

## 5) سجل المهام

| التاريخ | المهمة | الحالة |
|---|---|---|
| نوفمبر 2026 | المهمة 1: بناء السورس إلى مود JAR عبر GitHub Actions + نشر Release (source.zip + JAR) + إنشاء ملفي التوثيق | ✅ تمت — انتهت دون تنبيهات من المستخدم على أي مشكلة إضافية (حتى لحظة كتابة هذا الملف) |
| نوفمبر 2026 | المهمة 2: تخفيف حجم العميل بشكل ممتاز (b12.8): حذف حشو ميت + ضغط أصول + optimizeJar + source.zip ضغط 9 + RELEASE_NOTES.md | ✅ تمت — انتهت دون تنبيهات من المستخدم على أي مشكلة (النتيجة: JAR أصغر بـ 43% وsource.zip أصغر بـ 52%) |
| نوفمبر 2026 | المهمة 3: إعادة تسمية الموديولات والأوضاع (AimAssist/MoreKB/WatchdogNCP family/UpdatedNCP/FastBridge/Verus/Watchdog-Protection) + نظام Mode/Custom في NoSlow وDisabler مع tag + حد BoostDelay 10000 + حذف LiquidWalk/BedGodMode/GodMode (b12.9) | ✅ تمت — فشل بناء واحد بسبب خطأ استيراد في grim.kt (وثّق أعلاه وحُل فوراً)، ثم نجح النشر (run 42621643210) |
