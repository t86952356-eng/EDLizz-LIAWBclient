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
| الإصدار الحالي | **b12.12** (`LiquidBounce.kt` → `clientVersionText` و`gradle.properties` → `mod_version`) |
| اللعبة | Minecraft **1.8.9** — Forge `11.15.1.2318` — mappings `stable_22` |
| الأساس | **LiquidBounce Legacy** (فرع المجتمع النهائي، آخر إصدار رسمي منه b100) |
| مود id | `Lizz` — الاسم الظاهر في mcmod.info: `LAWIB` |
| المؤلفون (mcmod.info) | CCBlueX، PickBowen، bzym2، RainRuin، sunbox |
| عنوان النافذة | `LIAWB client 1.8.9 b12.12 \| 免费注册加群：815061613` (عنوان مجتمع صيني — رقم مجموعة QQ) |
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

### المهمة 4 — نوفمبر 2026 — «استخراج دفعة أوضاع من عميل 1.8.9 آخر» (b12.10)
الهدف: استخراج الأوضاع المطلوبة من عميل 1.8.9 آخر (مرفوع من المستخدم كـ client.zip، مصدر مرابط جزئياً) — المطلوب 21 وضعاً ونُفّذ 20 (Polar velocity تخطّي، انظر أدناه)
إلى Lizz بمنطق مطابق قدر الإمكان مع كل الإعدادات:
- **NoSlow** (6): WatchdogNCP / Intave / Matrix / -Watchdog Prediction / Grim / Grim 1.9 — مع إعدادات
  مشتركة Food/Potion/Sword/Bow (افتراضياً مغلقة كما في المصدر) وإعدادات خاصة لكل وضع.
- **Velocity** (8): WatchdogNCP-New / Matrix / Intave / Vulcan-New / Grim-New / Grim 2 / -Watchdog Prediction / MMC
  (+ إعداد On Swing مشترك لـ Intave/MMC). **Polar لم يُستخرج**: الوضع في المصدر مجرد إعادة تسمية
  لوضع Legit نفسه، وLizz يملك Polar/Legit/Jump أصلاً.
- **Speed** (4): Matrix / WatchdogNCP-New (بثلاثة أنواع فرعية Strafe/Low Strafe/NCP Strafe) / Polar / Old NCP Y-port.
- **Fly** (3): WatchdogNCP-New (من Funcraft) / Matrix / Matrix Damage (بـ 11 إعداداً).

**قرارات تسمية عند التصادم** (أسماء محجوزة مسبقاً في Lizz):
- Velocity: اسم «WatchdogNCP» محجوز لوضع HypixelAir القديم (من b12.9) ← الوضع المستخرج باسم
  **WatchdogNCP-New**؛ و«Vulcan»/«Grim» موجودان ← **Vulcan-New**/**Grim-New** — اتباعاً لاصطلاح
  «-New» الذي استخدمه المستخدم نفسه في نفس الطلب لأوضاع Speed/Fly.

| # | الدرس / الخلل | التفصيل | الحل المطبّق |
|---|---|---|---|
| 1 | **مصدر مشوّش جزئياً (obfuscated)** | حقول MC مُرقّعة بأسماء مشوشة: `tR` (ticks-of-item-use صاعد)، `cqL` (itemInUseCount هابط)، `ae` (ticksSinceVelocity)، `Zl` (ticksSinceTeleport)، `crI/crK` (السرعة المتوقعة من S12)، `pl/pp` (rotationYaw/prevRotationYaw)، `bjQ` (serverSprintState تقريباً)، `crd` (SafeWalk)، وحزم مشوشة: `play.client.l`=C09HeldItemChange، `play.server.a`=S00KeepAlive، `ad`=S1CMetadata، `aa`=S19EntityHeadLook، `z`=S18EntityTeleport | فك الدلالات من السياق + من ملفات Script API في المصدر (`PlayerAPI.getTicksSinceVelocity/getTicksSinceTeleport`، `EntityPositionPacket`، `ScriptPacketServerKeepAlive`) ثم إعادة تنفيذها مركزياً في `utils/movement/PortedUtils.kt` (PortedTrackers/PortedMove/PortedPacketQueue/PortedBlink) |
| 2 | **تسميات decompiled المعقدة** | بنية `label1224/label1264` في WatchdogSpeed غامضة (أي بلوك يخص أي نوع فرعي؟) | عدّ الأقواس برمجياً (سكربت python على المصدر) حسم: بلوك A = NCP Strafe، بلوك B = Low Strafe، وبلوك C (الجليدي + Friction Full Disabler) يتبع كل الأنواع |
| 3 | **فروقات API الأحداث** | Lizz: PacketEvent فيه `eventType` وليس eventState؛ StrafeEvent قابل للإلغاء فقط (لا setSpeed)؛ لا يوجد SprintEvent/RightClickEvent/PostStrafeEvent/KeyboardInputEvent | إعادة توجيه: Sprint→فرض isSprinting في UpdateEvent؛ RightClick→اعتراض C08 الصادر + قفز؛ PostStrafe→أول onPreMotion؛ setSpeed→إلغاء StrafeEvent + strafe(speed) (قطري ×0.98) |
| 4 | **`handler<>` يتطلب Listenable** | `object PortedTrackers : MinecraftInstance` فقط — دوال handler<T> امتدادات على Listenable | إضافة Listenable للكائن (مثل MovementUtils) |
| 5 | **خصائص الامتداد تحتاج استيراداً** | `packet.realMotionX/realX` خصائص امتداد في utils/client/PacketUtils.kt لا تُرى خارج حزمتها | استيراد realMotionX/Y/Z وrealX/Y/Z صراحةً |
| 6 | **API دقائق** | `Entity.inWater` حقل محمي (البديل: `isInWater`)؛ `roundToLong` امتداد وليست دالة عادية؛ VIA: فرع 1.19 يرسل عبر `Protocol1_19To1_18_2` وليس `Protocol1_9To1_8` | تصحيحات مباشرة |
| 7 | **الترجمة المحلية الجزئية ممكنة وفعالة** | محاولة compileKotlin بـ 1.8GB ماتت بصمت؛ بـ 1.4GB نجحت في ~2 دقيقة — تلتقط كل أخطاء Kotlin قبل Actions (رصدت 35 خطأ وأصلحتها في جولة واحدة) | شغّل `JAVA_HOME=$HOME/jdk8u512-b09 ./gradlew compileKotlin --no-daemon -Dorg.gradle.jvmargs="-Xmx1400m ..."` قبل كل دفعة؛ البناء الكامل يبقى على Actions (7GB) |
| 8 | **دمج الأوضاع دون كسر القديمة** | NoSlow/Velocity ملفات ضخمة بمنطق Custom/القديم | نمط «مجلد أوضاع + فئة أساس + توزيع أعلى كل handler ثم return@handler» (نفس نمط speedmodes/flymodes): `movement/noslowmodes/` (6 ملفات)، `combat/velocitymodes/` (8)، وspeedmodes/flymodes في مجلداتها |

**تبسيطات موثّقة** (فروقات مقصودة عن المصدر لعدم توفر مكافئ في Lizz): شرط Jesus-Dolphin
(وضع غير موجود) اختُصر إلى `!Jesus.handleEvents()`؛ شروط Breaker.bedBroken و
WatchdogBypass.inventoryClicking أُسقطت/قُرّبت؛ KeyboardInputEvent (منع تبديل الموديول بالمفتاح)
لم يُنفذ لأن KeyEvent غير قابل للإلغاء في Lizz (pendingToggle يبقى يعمل من فرع GuChest)؛
ألوان رسم صندوق الـbacktrack ثابتة بدل ألوان theme؛ WatchdogSpeed's Scaffold sameY وfastFall
غير موجودة في Lizz فأسقطت شروطهما؛ `BlinkComponent.dispatch()` في Grim velocity = no-op
(القناة لا تُفعّل من هذا الوضع في المصدر أصلاً) فحُذف.

**النتيجة**: compileKotlin محلي = **BUILD SUCCESSFUL** (35 خطأ في الجولة الأولى أُصلحت كلها)،
ثم نجح البناء والنشر على GitHub Actions (run **42653938466**) وإصدار
[`vb12.10-mc1.8.9`](https://github.com/t86952356-eng/EDLizz-LIAWBclient/releases/tag/vb12.10-mc1.8.9)
بمرفقات `lizz-b12.10.jar` (**25.22MB** — ضمن النطاق 20-26MB) و`lizz-b12.10-source.zip` (**28.49MB**)،
والـ changelog بالإنجليزية من `RELEASE_NOTES.md`. التحقق بعد النشر: manifest سليم (MixinConfigs/FMLAT/
TweakClass/FMLCorePlugin)، mcmod.info = b12.10، SRG reobf مؤكد (47 اسماً في MixinEntity)، كل كلاسات
الأوضاع الجديدة الـ 20 موجودة (velocitymodes=8 + noslowmodes=6 + MatrixSpeed/PolarSpeed/OldNCPYPort/
WatchdogNCPNewSpeed + fly: WatchdogNCPNew/Matrix/MatrixDamage) + كلاسات PortedUtils الخمسة، وسلاسل
الأوضاع والإعدادات الجديدة مؤكدة داخل Velocity.class وNoSlow.class.

### المهمة 5 — نوفمبر 2026 — «إصلاحات b12.10 + الدفعة الثانية من الاستخراج» (b12.11)
الهدف: (1) أربع إصلاحات طلبها المستخدم على b12.10: إعادة تسمية «-Watchdog Prediction» إلى
**Watchdog-Protection** في NoSlow وVelocity، جعل سبرنت Speed/Polar **legit**، دعم **Sword NoSlow مع KillAura**
في المودات المستخرجة الست (بتعلّم نظام Custom نفسه — usingItemFunc)، وإصلاح خيار **damageboost** الميت في
Velocity/WatchdogNCP-New (وVulcan-New أيضاً — نفس الخلل). (2) الدفعة الثانية من الاستخراج: Criticals
(WatchdogNCP/Vulcan/Verus) + LongJump (Watchdog Fire Ball/Grim/Vulcan) + Phase (WatchdogNCP-Blink/Vulcan/Grim)
+ Step (Vulcan/WatchdogNCP) + InventoryMove (نظام **Mode/Custom** كامل + WatchdogNCP/Grim) + AntiVoid
(WatchdogNCP/Vulcan) = **15 وضعاً جديداً**.

| # | الدرس / الخلل | التفصيل | الحل المطبّق |
|---|---|---|---|
| 1 | **خيارات ميتة في المصدر نفسه** | damageBoost في WatchdogVelocity/VulcanVelocity المصدر معرّف ولا يُستخدم (كذلك Boost في WatchdogFireBallLongJump) — نُقلت في b12.10 حرفياً فبقيت ميتة | للمودات المطلوبة نُفّذ سلوك الخيار فعلياً (تحويل قوة ال knockback الأفقية لتسريع باتجاه الحركة × Damage Boost Speed)؛ Boost الخاص بـ FireBall بقي كما في المصدر مع تعليق. **الدرس: عند نقل خيار، تحقق أنه مُستخدم فعلاً في منطق المصدر** |
| 2 | **واجهة KillAura في المودات المنقولة** | فروع السيف في المودات المستخرجة تفحص `player.isUsingItem` فقط، وKillAura auto-block لا يضبطها | استبدال الفحص بـ `NoSlow.usingItemFunc()` في فروع السيف (نفس نظام Custom حرفياً — لا نظام جديد). **الدرس: أي فحص «استخدام عنصر» في وضع منقول يجب أن يمر بواجهة الموديول نفسها** |
| 3 | **حقول مشوّشة جديدة غير قابلة للفك** | `csk` (WatchdogCriticals — استُخدمت كشرط استعادة مبكر للـ stepHeight)، `cuz` (VulcanStep — بوابة رفع stepHeight)، `adr` (WatchdogBypass NPC-check) — مرجع واحد فقط في المصدر كله لكل منها | csk→متابع محلي «ticksSinceCritAttack»؛ cuz→متابع محلي «ticksSinceFullStep»؛ adr→أسقط الشرط (موثّق). **الدرس: الحقل المشوش ذو المرجع الوحيد لا يمكن فكه — سوّد تقريباً موثقاً أو أسقطه إن كان هامشياً** |
| 4 | **تعارض أسماء القيم بين المودات الجديدة والقديمة** | Criticals الجديدة تريد «Delay» (0..1000) والموديول فيه «Delay» (0..500)؛ AntiVoid الجديدة تريد «Distance» مرتين بقيمتين مختلفتين | أسماء مميزة: «WatchdogNCP Delay»/«Verus Delay» و«WatchdogNCP Distance»/«Vulcan Distance». **الدرس: افحص أسماء القيم الموجودة قبل تسمية إعدادات الوضع الجديد** |
| 5 | **val تُقيّم مرة واحدة بدل get()** | InventoryMove.isIntave كان `private val isIntave = (mc.currentScreen ...) && intave` — يُقيّم عند تحميل الكلاس (currentScreen=null) فلا يعمل الإعداد Intave أبداً | تحويل إلى get() مع قيد Custom. **الدرس: أي شرط يعتمد على حالة لحظية (شاشة/موضع) يجب أن يكون getter لا val** |
| 6 | **PacketQueueComponent.a(...) استدعاء ميت** | في WatchdogAntiVoid المصدر يستدعي تحديد أنواع الحزم لكن علم التفعيل cR لا يضبطه إلا MMCFlight/LegitNoSlow — الاستدعاء بلا أثر | أسقط الاستدعاء (موثّق). **الدرس: تتبّع أعلام التفعيل قبل نقل استدعاءات مكونات المصدر** |
| 7 | **StrafeEvent.setSpeed/setForward غير موجودة في Lizz** | أوضاع Vulcan (Phase/Step/AntiVoid) تعدّل السرعة/الاتجاه داخل الحدث | setSpeed→إلغاء الحدث + PortedMove.strafe (كما b12.10)؛ setForward→إلغاء الحدث + إعادة تطبيق صيغة moveFlying الأصلية بـ forward مُعطّى (moveFlyingWithForward) |
| 8 | **تحرير هيكلي بالـ MultiEdit يكسر الملف** | تعديل Phase.kt أدخل onUpdate ثانياً مفتوح الأقواس (الكشف بقص الأقواس برمجياً) | إصلاح + فحص توازن الأقواس لكل ملف معدّل قبل الدفع. **الدرس: بعد كل تعديل هيكلي كبير افحص `count('{')-count('}')` ومكررات الأسماء برمجياً** |

**تبسيطات موثّقة**: RotationComponent.setRotations → حقن `RotationUtils.currentRotation` مباشرة (الـ Mixin يحملها للحزم ويصحح المدخلات — يكافئ MovementFix)؛ SmoothCameraComponent.cn → متابعة محلية 80ms تُصفّر cameraYaw/cameraPitch؛ SprintEvent → UpdateEvent + isSprinting=false؛ WatchdogBlinkPhase يطلق blink عند رسائل فتح الأقفاص بدل انتظار مهلة المصدر؛ PacketlessDamageComponent/«Watchdog Fire Ball 2»/Watchdog2LongJump.launched أسقطت (غير موجودة)؛ exclude شرط adr (غير قابل للفك).

**النتيجة**: compileKotlin محلي = BUILD SUCCESSFUL من الجولة الثانية (خطأ واحد فقط: بقايا دالة محذوفة)،
ثم نجح البناء على GitHub Actions (run **42756010821**) وإصدار
[`vb12.11-mc1.8.9`](https://github.com/t86952356-eng/EDLizz-LIAWBclient/releases/tag/vb12.11-mc1.8.9)
بمرفقات `lizz-b12.11.jar` (**25.3MB** — ضمن النطاق 20-26MB) و`lizz-b12.11-source.zip` (**28.5MB**)، والـ changelog
بالإنجليزية من `RELEASE_NOTES.md` (كل الأوضاع مكتوبة كإضافات جديدة دون ذكر الاستخراج). التحقق بعد النشر:
manifest سليم (MixinConfigs/FMLAT/TweakClass/FMLCorePluginContainsFMLMod)، mcmod.info = b12.11،
SRG reobf مؤكد (47 اسماً في MixinEntity)، كل كلاسات الأوضاع الـ 15 موجودة، وقيم Mode في
InventoryMove (Custom/WatchdogNCP/Grim) وأسماء Watchdog-Protection مؤكدة داخل الكلاسات المبنية.

### المهمة 6 — نوفمبر 2026 — «ترقية ميزات + الدفعة الثالثة من الاستخراج» (b12.12)
الهدف: (1) ثلاث ترقيات طلبها المستخدم: خيارا **OnlyDamage/AutoDamage** لوضع Fly/WatchdogNCP-New، إظهار **tag الوضع في
arraylist لموديول AntiVoid**، وخيار **StyleSettings (Normal/Best)** في ClickGUI للتخطيط Classic (Best = لوحة إعدادات
الوحدة تنفتح أسفل الوحدة بشكل «درج» مع إزاحة الوحدات الأسفل تلقائياً وعدم تجاوز حدود الفئة). (2) الدفعة الثالثة من الاستخراج من
**عميل 1.12.2 جديد** (loftily): KillAura AutoBlock (Matrix/Verus/HoldKey) + Disabler (WatchdogNCP/MatrixFlag/GrimAC — مع
دمج MatrixDisabler.java كاملاً بإعداداته الـ17 داخل وضع MatrixFlag) + Phase (Intave) + Fly (TimerGlide/Intave/Matrix من
MatrixTest مع إعادة تسمية القديم إلى OldMatrix) + Velocity (MatrixBoost/IntaveNew) + Speed (MatrixNew/MatrixHighSpeed2)
+ NoSlow (MatrixNew/MatrixSemi/GrimSemi — فقط Food للوضعين Semi) + Scaffold Tower (MatrixNew) = **21 وضعاً جديداً**.

**قرارات تسمية عند التصادم** (أسماء محجوزة مسبقاً في Lizz):
- Fly: اسم «Matrix» محجوز لوضع b12.10 — المستخدم طلب صراحة أن يحمل المستخرج (MatrixTest) اسم **Matrix** ← أُعيد تسمية
  الوضع القديم إلى **OldMatrix** (سلوكه لم يتغير) وحصل المستخرج على اسم Matrix.
- Speed: «Matrix» محجوز (b12.10) ولا تسمية من المستخدم ← اتباع اصطلاح المستخدم في نفس الطلب (Matrix→MatrixNew):
  الوضع المستخرج اسمه **MatrixNew**. MatrixHighSpeed2 باسم **MatrixHighSpeed2**.
- Velocity: matrix→**MatrixBoost** و Intave→**IntaveNew** (تسمية المستخدم الصريحة).
- NoSlow: Matrix→**MatrixNew** (دمج فرعي Sword من BlockingPacketMode وFood من FoodMode)، MatrixSemi وGrimSemi
  بأسمائها (فقط فرع Food يعمل — فرع Sword لا يعمل كما في المصدر).
- Tower: Matrix→**MatrixNew** (اسم «MatrixNew-WaitBlocks» للإعداد).
- Disabler: WatchDogMovement→**WatchdogNCP** (تسمية المستخدم)، OldMatrixFlag→**MatrixFlag**، GrimAimFix+Grim→**GrimAC**.

| # | الدرس / الخلل | التفصيل | الحل المطبّق |
|---|---|---|---|
| 1 | **خطأ decompiler في مقارنة بدل إسناد** | `return this.lastResult == result;` في getGrimPost لدى المصدر — لكن lastResult لا يصبح true أبداً فيظهر أن الدالة منطقها مقلوب؛ هذا **أثر decompiler** (الأصل إسناد `=`) | نُفذت بالمعنى الصحيح (إسناد + return lastResult) مع تعليق يوثق التحليل |
| 2 | **تسميات JVM clash** | خاصية `grimPost` ودالة `getGrimPost()` في نفس object → «Platform declaration clash (getGrimPost()Z)» | إعادة تسمية الدالة إلى isPostDelayActive() |
| 3 | **حقول خاصة في 1.8.9** | `EntityPlayerSP.lastReportedYaw/Pitch` خاصة — مرجع GrimAimFix يعتمدها | الاستبدال بـ RotationUtils.serverRotation (آخر دوران مُرسل — مكافئ تماماً) |
| 4 | **أسماء حزم مختلفة بين 1.12.2 و1.8.9** | S16PacketSetSlot (1.12) = S2FPacketSetSlot، وS00PacketServerInfo/S01PacketPong في حزمة status.server وS01PacketEncryptionRequest في login.server وليست play.server | استيراد كل من حزمته الصحيحة |
| 5 | **امتداد airTicks يحتاج استيراداً** | `player.airTicks` ليس حقلاً أصلياً بل خاصية امتداد في PlayerExtension.kt | استيراد صريح في كل ملف جديد يستخدمه |
| 6 | **getRotating() لا تُصبح isRotating** | عادة Kotlin: getRotating() → `.rotating` وليس `.isRotating` (غير مسبوقة بـ is) | packet.rotating |
| 7 | **wrapAngleTo180 غير موجود** | 1.8.9 MCP الاسم `wrapAngleTo180_float` | تصحيح مباشر |
| 8 | **FlyMode بلا onWorld** | وضع Matrix الجديد يفرغ مخزن S08 عند تغيير العالم لكن الفئة الأساسية بلا هوك | تتبع مرجع العالم داخل onUpdate ومقارنته (تغيير = تفريغ) |
| 9 | **قيم PreUpdateEvent عند المصدر** | المصدر يستخدم PreUpdateEvent — في Lizz يقابلها UpdateEvent (تُطلق في onLivingUpdate HEAD بنفس الإيقاع) وليس MotionEvent PRE دائماً | ربط كل منطق المصدر بـ onUpdate ما لم يكن حدث Motion صريح |
| 10 | **فحص الأقواس البرمجي يعطي إنذارات كاذبة** | قص السلاسل/التعليقات بـ regex يفسد مع string templates `"${...}"` — لكن compileKotlin الناجح هو الحكم الفعلي | الاعتماد على نجاح الترجمة كدليل التوازن (السبق b12.11 كان لملف كسرته MultiEdit فعلاً) |

**تبسيطات موثّقة**: (1) KillAura blockingPacket: حزمة استخدام OFF_HAND غير موجودة في 1.8.9 فأُسقطت (بقي C08×2 + INTERACT_AT×2 اختيارياً بإعداد InteractPacket افتراضياً مغلقاً كما في المصدر). (2) Intave Phase: mc.objectMouseOver.getBlockPos() مباشرة. (3) MatrixNew NoSlow فرع الطعام: `!isJumping` (حقل محمي) قُرِّب بـ `!movementInput.jump`. (4) MatrixSemi: jumpTicks (خاص) قُرِّب بامتداد tryJump، وonGroundTicks يُتابع محلياً. (5) WatchdogNCP Disabler: displayedTitle (خاص) قُرِّب بـ isDead في فحص spectator. (6) IntaveNew Velocity: فرع Strafe يضغط Forward فقط (moveFix=STRICT يقابله في Lizz إصلاح المدخلات التلقائي عند تفعيل currentRotation). (7) GrimAC: smoothRotation بحد 360° = تمرير مباشر (خطوة واحدة). (8) MatrixBoost Velocity: إعدادا النطاق StrafeHurtTime/HurtTime أصبحا Min/Max منفصلين (لا يوجد نوع نطاق في Lizz). (9) MatrixDisabler الدمجي: قيمة Version داخل الوضع تعمل مع is7x والكشف التلقائي. (10) ClickGUI Best: الزر الأيسر داخل منطقة الدراج لا يفعّل الوحدة (isHovered محصور في صف الوحدة نفسه 16px).

**النتيجة**: compileKotlin محلي = **BUILD SUCCESSFUL** (3 جولات: 21 خطأ ثم 2 ثم 0)، ثم البناء والنشر عبر GitHub Actions وإصدار
vb12.12-mc1.8.9 مع lizz-b12.12.jar وlizz-b12.12-source.zip، والـ changelog بالإنجليزية من RELEASE_NOTES.md (كل الأوضاع
مكتوبة كإضافات جديدة دون ذكر الاستخراج).

### المهمة 7 — نوفمبر 2026 — «ملاحظات المستخدم بعد b12.12 + الدفعة الرابعة من عميل 1.8.9 (fork من LiquidBounce Legacy)» (b12.13)

الهدف: (1) **ست ملاحظات/إصلاحات**: OnlyDamage في Fly/WatchdogNCP-New يجب أن يلغي الطيران **بالكامل** (السابق كان يلغي الـ boost
فقط ويُبقي الطيران/التحويم العامل = «مشغل نصف»)؛ درج ClickGUI Classic/Best يحتاج (أ) لون خلفية مميزاً و(ب) إصلاح خروج الدراج
عن حدود طول الفئة؛ وضع **Backward** ضمن Rotations في Scaffold2 (نظام ReverseYaw لكن yaw+180 — rotation يتحدد من تغيّر
الموقع فقط)؛ خيار **MatrixNew-Timer** (0.2..1.1 افتراضي 0.90) لوضع Tower/MatrixNew؛ خيار **Size** لموديول WaterMark؛ نقل
موديول **GrimCritical** كوضع **GrimAC** داخل Criticals (نقل حقيقي: الموديول القديم حُذف، الإعدادات كما هي، tag في arraylist).
(2) **الدفعة الرابعة** من عميل FireBounce (1.8.9، fork من liquidbounce legacy — نفس العائلة): Velocity: دمج
PolarJump+Intave+Intave2+AirJumpReset+Prediction في وضع واحد **Watchdog-ProtectionNew** (بمفاتيح فرعية لكل مكوّن) +
**AttackReduce** (من NewVelocity) + **MatrixReduce2**/**MatrixReduce3** (من Velocity)؛ Speed: Matrix3→**MatrixLates**،
Intave3→**IntaveTimer**، **IntaveNew**، Intave14.8.4→**Intave14New**؛ NoWeb: **Matrix**/**MatrixNew**/**Intave14**؛
NoSlow: دمج IntaveBlink/Intave14/GrimNew/GrimTick من قوائم SwordMode/ConsumeMode/BowMode الثلاث إلى أوضاع موحدة
**IntaveBlink**/**Intave14New**/**GrimNew**/**GrimTick** (بوابات Sword/Food/Potion/Bow المشتركة)؛ FastUse: **Matrix**.

**بنية تحتية جديدة لهذه الدفعة** (تُستخدم في الدفعات القادمة من نفس العائلة):
- `utils/firefly/general/SomeUtil.kt` — منفذ حرفياً بنفس مسار حزمة المصدر: roundToPlacesIfNeeded/reduceXZ/reduceY/setMotion/
  changeSprint/changeTimer/calculateAngleDifference/isHurting/bps/bpt/setBPSTo/safeJump/runAttack. ملاحظة: شرط
  attackChance في runAttack عند المصدر **معكوس** (nextFloat() <= chance يهرب دائماً عند chance=1) فصُحح إلى `>` مع تعليق.
- `utils/attack/SilentAttackManager.kt` + هوك في MixinPlayerControllerMP (الهجوم الصامت لا يطلق AttackEvent).
- `setSprintWithSync` أُضيف لـ PlayerExtension.kt (changeSprint تحتاجه)، و`setSpeed`/`getPlayerDirection` لـ MovementUtils.
- **canCancelHitSlow**: حقل JvmField في WatchdogProtectionNewVelocityMode يقرؤه MixinEntityPlayer (injectKeepSprintA)
  لإلغاء HitSlow مرة واحدة عندما يقرر فرع Intave أن الرد قد انعكس. KillAura.keepSprint أصبح عاماً + خاصية
  shouldKeepSprint تقريبية، وKeepSprint.shouldKeepSprint تقريبية (handleEvents && isSprinting) — تعليقات موثقة في الملفين.

**قرارات التصميم في هذه الدفعة**:
- **Watchdog-ProtectionNew** = دمج فعلي: كل مكوّن من الخمسة له مفتاح خاص (PolarJump/Intave/Intave2/AirJumpReset/Prediction،
  افتراضياً كلها مفتوحة = الجسم المدمج كما طلب المستخدم) — لأن Intave وPrediction كليهما يستخدمان BlinkUtils العام
  (قائمة انتظار واحدة) فالتشغيل المتوازي مشوّش؛ المفاتيح تتيح للمستخدم ضبط التداخل. إعدادات المصدر كلها منقولة بأسمائها
  وقيمها الافتراضية. آليات JumpReset العامة (jumpReset/SmartJumpReset/CooldownMode multiChoices) سُقطت: لا يقرؤها أي مكوّن
  من الخمسة (resetJumpCooldownCounter لا يؤثر إلا في extraJumpReset)، وmultiChoices غير موجودة في Lizz —
  AllowWorkWhen استُبدلت بـ WorkOnGround/WorkInAir (سلوك canWorkNow نفسه). أُضيف unblink احتياطي في onDisable (المصدر لا
  ينظف — خطر بقاء الحزم معلقة).
- **دمج NoSlow**: المنطق في المصدر متطابق حرفياً بين الفروع الثلاثة (SwordMode/ConsumeMode/BowMode تستدعي نفس الدوال)،
  فالوضع الموحد يطبّق المنطق نفسه عندما يطابق نوع العنصر بوابةً مفتوحة (Sword/Food/Potion/Bow) مع usingItemFunc() (يدعم
  KillAura blockStatus)، وonSlowDown يعطي 1f/1f للعنصر المطابق (مضاعفات المصدر blockForwardMultiplier افتراضياً 1f).
  hasMotion (بوابة onMotion عند المصدر) مُطبقة داخل كل وضع.
- **MatrixReduce3**: خيار CancelSpecialVelocity العام عند المصدر مخصص لكل الأوضاع؛ هنا سلوك الإلغاء متأصل داخل الوضع
  (S12 صفري XZ يُلغى مع KeepMotionYVelocity)، وخيار BoostCooldown وحدة suffix "ms".
- **MatrixReduce2**: تفعيل hasReceivedVelocity عند المصدر شرطه الخارجي motionY>0 && (X|Z)!=0 (وليس isValidS12Packet) — نُقل كما هو.
- **Intave14New Speed**: اسم الوضع في المصدر «Intave14.8.4» — المستخدم سماه Intave14New في الطلب فاعتُمد اسمه (لا تصادم).
- **Scaffold2 Backward**: البحث/raycast يبقى بالاتجاه الحقيقي المُقرَّب (RoundYaw90/45) والـ rotation المُرسل للسيرفر فقط
  ينعكس +180 عند إنشاء PlaceRotation (وإلا فشل raytrace) — canUpdateRotation يقبل فرق 180° (يفحص الفرق الأدنى فقط).
- **ClickGUI تجاوز الدراج**: سببان: (1) updateElementsHeight كان يعدّ أول maxElements عنصراً بدل «النطاق المرئي» — بعد
  التمرير لا يدخل ارتفاع الدراج فيfade فتخرج الدراج أسفل الفئة؛ (2) fade يتحرك ببطء أثناء فتح الدراج (تأخير إطارين +
  تهدئة) فيظهر الخروج حتى في الفئات القصيرة. الحل: العد بالنطاق المرئي + إعادة حساب ارتفاع المحتوى نهاية كل رسم + snap
  فوري لـ fade عند تغيّر ارتفاع المحتوى والفئة مفتوحة (فتح/إغلاق الفئة يبقى بالتهدئة عبر lastContentTarget=0) + لون درج
  LB-SettingsDrawerColor (افتراضي 40,40,40,235) يرسم مستطيلاً دائرياً بحدود قبل عناصر الإعداد في LiquidBounce وFatality.
- **WaterMark Size**: glPushMatrix + مقياس حول نقطة (width/2, 0) — يبقى التوسيط. ملاحظة موثقة: BlurUtils يأخذ عيناته
  بإحداثيات الشاشة المطلقة فالضبابية تتصادم مع Size≠1 (الضباب افتراضياً مغلق).
- **Tower MatrixNew-Timer**: يُطبق طوال isTowering (PRE وPOST — POST يكتب أخيراً فيفوق Timer الخاص بـScaffold)، والعودة
  1f في مسار الخروج المبكر (إفلات المسافة/الحركة) بعلم matrixTimerActive.
- **GrimAC Criticals**: فئة CriticalsMode امتدت بـ onUpdate/onPacket/onStrafe + موزعون جدد في Criticals.kt. اسم إعداد
  الوضع الفرعي «GrimACMode» (الاسم «Mode» محجوز لموديول Criticals) وبقية الإعدادات بأسمائها (OnlyHorSpeed/FreezePercent).

| # | الدرس / الخلل | التفصيل | الحل المطبّق |
|---|---|---|---|
| 11 | **S01PacketPong حزمة status وليست play** | `net.minecraft.network.status.server.S01PacketPong` (الدرس 4 في السابق من 1.12.2، انعكس هنا من مصدر 1.8.9 وصف import play.server) | استيراد من status.server |
| 12 | **CPSCounter حزمة utils.attack وليست modules.combat** | SomeUtil استوردها من مسار المصدر الخاطئ | استيراد net.ccbluex.liquidbounce.utils.attack.CPSCounter |
| 13 | **@JvmField لا يولّد getter/setter** | MixinEntityPlayer (Java) لا يمكنه استدعاء getCanCancelHitSlow لحقل JvmField | وصول مباشر للحقل: `INSTANCE.canCancelHitSlow` |
| 14 | **getDistanceToEntityBox امتداد ويحتاج استيراداً صريحاً** | ليس عضواً في Entity | import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox |
| 15 | **عمليات الخلفية تُقتل بين نداءات الأدوات** | nohup/setsid لا ينفعان في الـ sandbox — العملية تُقتل بنهاية نداء Bash | تشغيل الترجمة في المقدمة مع ملف سجل (الـ JVM قد يعلق بعد BUILD SUCCESSFUL — اقرأ السجل بدل انتظار الخروج) |
| 16 | **ذاكرة Gradle cache تبقى بين الجلسات** | ~/.gradle (384MB مع minecraft) نجت من إعادة تدوير الـ sandbox — الترجمة المحلية ممكنة مباشرة بعد تحميل JDK8 (103MB من adoptium) | JAVA_HOME=/tmp/tools/jdk8u512-b09 مع -Xmx1800m؛ compileKotlin+compileJava = نجاح |

**النتيجة**: compileKotlin وcompileJava محلياً = **BUILD SUCCESSFUL** (جولة واحدة للإصلاح: 6 أخطاء استيراد ثم 0)، وترقية
mod_version إلى b12.13، ثم البناء والنشر عبر GitHub Actions (push إلى main ثم release/build) وإصدار vb12.13-mc1.8.9،
والـ changelog بالإنجليزية من RELEASE_NOTES.md (كل الأوضاع مكتوبة كإضافات جديدة دون ذكر الاستخراج).

### المهمة 8 — نوفمبر 2026 — «ملاحظتا المستخدم + الدفعة الخامسة من نفس عميل الدفعة الأولى (client.zip المحدَّث)» (b12.14)

الهدف: (1) **ملاحظتان**: وضع **Backward** في Scaffold2/Rotations كان يجعل الـ rotation يواجه الأمام (مسار الإرجاع المبكر في
findTargetPlace يعيد currRotation كما هو عندما يكون الاتجاه الحالي يصيب الهدف — وهو الوضع الغالب أثناء البناء) ← أصبح Backward
يتخطى مسار الإرجاع المبكر كلياً فيمر دائماً بمسار الحساب (yaw الهدف المقرب +180 = الخلف)؛ ودرج ClickGUI Best ما زال يتخطى حدود
الصنف بالطول ← إصلاح جذري ثلاثي: (أ) نمو فوري لـ fade قبل drawPanel عندما يزيد ارتفاع المحتوى عن الهدف المستقر الأخير
(lastContentTarget — لا يمس أنيميشن فتح الصنف)، (ب) scissor للدرج كاملاً (خلفية + عناصر) إلى منطقة محتوى الفئة عبر clipTop/clipBottom
يكتبهما Panel لكل ModuleElement، (ج) الشبكة: ModuleElement.drawScreenAndClick يغلق الـ scissor بعد كل عنصر (يغطي كل مسارات
return داخل الأسلوب؛ Zenith لا يمر بهذه الدوّار فهو يستدعي style مباشرة + drawerStyle=false يُعاد ضبطه في drawZenith).
(2) **الدفعة الخامسة**: العميل = نفس عميل الدفعة الأولى (client.zip قديم → clientnew.zip محدَّث، 1.8.9، Rise مبني على
liquidbounce legacy) بقاعدة المستخدم: أي وضع موجود بنفس الاسم (الاسم الجديد عند إعادة التسمية) يُحذف القديم كلياً ويُضاف المستخرج:
- **Velocity**: **GrimReduce** (جديد) + **WatchdogPrediction** (كانت مسجلة «Watchdog-Protection» — أعيدت تسميتها؛ المنطق متطابق بين
  القديم والمحدث، تحقق سطراً سطراً).
- **Fly**: **Watchdog-ProtectionBoat** (WatchdogBoat الجديد، مشوّش: فُكّت الأسماء يدوياً — aER.bgK=thePlayer, bgR=currentScreen,
  bgF=theWorld, aIY=gameSettings, chq=jump/chr=sneak/chs=sprint, gW/gX/gY=posX/Y/Z, ud/ue/uf=motion, aO=onGround, crx=itemInUseCount,
  Zz=ticksSinceTeleport, ae=ticksSinceVelocity, aRg=ticksExisted, cuU=isJumping, cuW=moveForward, csb=isDead (تأكد عبر Tracers/TeleportAura),
  C02=play.client.c مع Action d.daU=ATTACK (تأكد عبر مقارنة inventorymove)، bc=S32, t=S12, n/o=C0BPacketEntityAction/Action مع
  dbp=START_SNEAKING (استنتاج دلالي)، fg=WorldChangeEvent، i=MoveUtil, aik=PlayerUtil, ahn=PacketUtil, bm=BlinkComponent,
  bc=PacketQueueComponent) + **MMC** (من المصدر النظيف القديم — المطابق للمحدث؛ isFullBlock→isFullCube، PacketQueueComponent.cR لا
  مقابل) + **AirJump** (نظيف قديم مطابق).
- **InventoryMove**: **Grim** (استُبدل بورت b12.11 من عميل 1.12.2 — حُذف كلياً وأضيف بورت هذا العميل مع خيار Manager Extra Sprint
  Ticks) + **Watchdog-Protection** (جديد — مطابق بنيوياً لبورت WatchdogNCP من 1.12.2 + فرق aec≈!isDead في فحص NPC؛ الخيارات
  الثلاث Prediction Mode/Ticks/Measure Chest Open).
- **NoSlow**: **Grim 3.0** (جديد من المصدر النظيف القديم — منطق المحدث غير مفكوك التجميع؛ RightClickEvent→C08 SEND إلغاء تقريبي،
  SprintEvent→onUpdate) + **WatchdogPrediction** (إعادة تسمية من «Watchdog-Protection» — المنطق متطابق بين القديم والمحدث).
- **Speed**: **MineMenClub** (جديد من المصدر النظيف — المطابق للمحدث، مع simulateStrafe الأصلي) + Grim2 (الموجود من الدفعة الأولى هو
  بقاء نفس المصدر النظيف؛ منطق المحدث غير مفكوك التجميع — تحقق مطابقة، لا تغيير).

**قرار فشل فك التجميع**: المحدث clientnew (CFR 0.152 مع تشويش مخصص) فشل فك منطق 3 ملفات كلياً (velocity/GrimReduceMode و
speed/Grim2Mode و noslow/Grim30Mode: أجسام listener methods غير موجودة + سلاسل مشفّرة XOR عبر decryptor مع
علامات «Decompilation failed/**»): استُخدم **المصدر النظيف القديم** (client.zip من الدفعة الأولى) لنفس الأوضاع، وقورن المفكوك
من المحدث بما تيسر (كل الأوضاع المقروءة كانت متطابقة مع القديم عدا aec في inventorymove). خرائط العميل مخصصة (وليست notch
الرسمية — تأكد عبر joined.srg الرسمي: Entity=pk وليس c) لذا استُخدم الاستدلال الدلالي المقارن.

**بنية تحتية جديدة**: **StrafeEvent أصبح var** (strafe/forward/friction قابلة للتعديل) + MixinEntity.moveFlying يعيد استدعاء
الأصل بالمعاملات المعدلة (replay مع حارس @Unique strafeEventReplaying) — لازمة لوضع Watchdog-ProtectionBoat (friction=Speed
وتصفير المدخلات أثناء التجميد).

| # | الدرس / الخلل | التفصيل | الحل المطبّق |
|---|---|---|---|
| 17 | **خصائص Kotlin var في constructor = حقول خاصة من Java** | MixinEntity (Java) لا يستطيع strafeEvent.strafe مباشرة | استخدام getStrafe()/getForward()/getFriction() |
| 18 | **when على object لا يعمل smart-cast** | `when (extra) { WatchdogNCPInventoryMove -> extra.onPreMotionMedium(...) }` لا يجيز استدعاء دالة غير موجودة في الفئة الأساسية | مقارنة === صريحة مع استدعاء باسم الكائن |
| 19 | **Math.* مع Float يحتاج toDouble()** | Math.toRadians(strafeAngle) مع Float لا يُحوَّل ضمنياً في Kotlin | strafeAngle.toDouble() |
| 20 | **خرائط العميل المحدث مخصصة وليست notch** | فك الاسم عبر joined.srg الرسمي فشل (Entity=pk وليس c) | استدلال دلالي + مقارنة مع المصدر النظيف القديم + الملفات الأخرى |
| 21 | **المستودع يُحذف من الـ sandbox بين الجلسات** | lizz-src وfirebounce-src اختفيا (المحفوظ فقط my-project الدائم) | re-clone من GitHub بالتوكن قبل العمل |
| 22 | **غرادلا يعلّق بعد BUILD في بعض الجولات** | الـ daemon لا يخرج رغم النجاح والـ timeout يقتل الأمر | قراءة ملف السجل بدل الاعتماد على كود الخروج (BUILD SUCCESSFUL ظاهرة في السجل) |

**النتيجة**: compileKotlin وcompileJava محلياً = **BUILD SUCCESSFUL** (جولات: 3+6 أخطاء ثم 0)، وترقية mod_version إلى b12.14،
ثم البناء والنشر عبر GitHub Actions وإصدار vb12.14-mc1.8.9، والـ changelog بالإنجليزية من RELEASE_NOTES.md (كل الأوضاع
مكتوبة كإضافات جديدة دون ذكر الاستخراج).

## 5) سجل المهام

| التاريخ | المهمة | الحالة |
|---|---|---|
| نوفمبر 2026 | المهمة 1: بناء السورس إلى مود JAR عبر GitHub Actions + نشر Release (source.zip + JAR) + إنشاء ملفي التوثيق | ✅ تمت — انتهت دون تنبيهات من المستخدم على أي مشكلة إضافية (حتى لحظة كتابة هذا الملف) |
| نوفمبر 2026 | المهمة 2: تخفيف حجم العميل بشكل ممتاز (b12.8): حذف حشو ميت + ضغط أصول + optimizeJar + source.zip ضغط 9 + RELEASE_NOTES.md | ✅ تمت — انتهت دون تنبيهات من المستخدم على أي مشكلة (النتيجة: JAR أصغر بـ 43% وsource.zip أصغر بـ 52%) |
| نوفمبر 2026 | المهمة 3: إعادة تسمية الموديولات والأوضاع (AimAssist/MoreKB/WatchdogNCP family/UpdatedNCP/FastBridge/Verus/Watchdog-Protection) + نظام Mode/Custom في NoSlow وDisabler مع tag + حد BoostDelay 10000 + حذف LiquidWalk/BedGodMode/GodMode (b12.9) | ✅ تمت — فشل بناء واحد بسبب خطأ استيراد في grim.kt (وثّق أعلاه وحُل فوراً)، ثم نجح النشر (run 42621643210) |
| نوفمبر 2026 | المهمة 4 (b12.10): استخراج الأوضاع من عميل 1.8.9 آخر — NoSlow 6 + Velocity 7 من 8 (Polar لم يُستخرج: هو Legit مُعاد تسميته في المصدر) + Speed 4 + Fly 3 = 20 وضعاً + بنية PortedUtils مشتركة + ترقية b12.10 | ✅ تمت — compileKotlin محلي نجح (بعد إصلاح 35 خطأ) ونجح النشر على Actions (run 42653938466) والإصدار vb12.10-mc1.8.9 |
| نوفمبر 2026 | المهمة 5 (b12.11): إصلاحات b12.10 الأربعة (تسمية Watchdog-Protection + Polar legit sprint + Sword/KillAura عبر usingItemFunc + إصلاح damageBoost الميت) + الدفعة الثانية: Criticals 3 + LongJump 3 + Phase 3 + Step 2 + InventoryMove (نظام Mode/Custom + وضعان) + AntiVoid 2 = 15 وضعاً جديداً + ترقية b12.11 | ✅ تمت — compileKotlin محلي نجح (خطأ واحد أُصلح) ونجح النشر على Actions (run 42756010821) والإصدار vb12.11-mc1.8.9 |
| نوفمبر 2026 | المهمة 6 (b12.12): ترقيات OnlyDamage/AutoDamage (Fly/WatchdogNCP-New) + tag لـ AntiVoid + StyleSettings درج (ClickGUI Classic) + الدفعة الثالثة من عميل 1.12.2: KillAura AutoBlock 3 + Disabler 3 (Mode system + دمج MatrixDisabler) + Phase 1 + Fly 3 + Velocity 2 + Speed 2 + NoSlow 3 + Tower 1 = 21 وضعاً جديداً + ترقية b12.12 | ✅ تمت — compileKotlin محلي نجح (3 جولات: 21/2/0 خطأ) ونجح النشر على Actions والإصدار vb12.12-mc1.8.9 |
| نوفمبر 2026 | المهمة 7 (b12.13): ملاحظات المستخدم الست (OnlyDamage كامل الإلغاء + لون/تجاوز درج ClickGUI + Backward rotations + MatrixNew-Tower-Timer + WaterMark Size + نقل GrimCritical→Criticals/GrimAC مع حذف الموديول) + الدفعة الرابعة من عميل 1.8.9 (fork من Legacy): Velocity 4 (Watchdog-ProtectionNew دمج 5 + AttackReduce + MatrixReduce2/3) + Speed 4 (MatrixLates/IntaveTimer/IntaveNew/Intave14New) + NoWeb 3 (Matrix/MatrixNew/Intave14) + NoSlow 4 (IntaveBlink/Intave14New/GrimNew/GrimTick — دمج Sword/Consume/Bow) + FastUse 1 (Matrix) = 16 وضعاً جديداً + بنية SomeUtil/SilentAttackManager + ترقية b12.13 | ✅ تمت — compileKotlin وcompileJava محلياً نجحا (6 أخطاء استيراد أُصلحت بجولة واحدة) ونجح النشر على Actions والإصدار vb12.13-mc1.8.9 |
| نوفمبر 2026 | المهمة 8 (b12.14): ملاحظتا المستخدم (Backward يواجه الخلف فعلاً + درج ClickGUI Best لا يتخطى حدود الصنف أبداً: نمو فوري + scissor) + الدفعة الخامسة من عميل الدفعة الأولى المحدَّث (clientnew.zip) بقاعدة الحذف عند تطابق الاسم: Velocity 2 (GrimReduce + WatchdogPrediction إعادة تسمية) + Fly 3 (Watchdog-ProtectionBoat/ MMC/AirJump) + InventoryMove 2 (Grim استبدال + Watchdog-Protection) + NoSlow 2 (Grim 3.0 + WatchdogPrediction إعادة تسمية) + Speed 1 (MineMenClub؛ Grim2 الموجود مطابق للمصدر) = 11 وضعاً جديداً/مستبدلاً + StrafeEvent قابل للتعديل مع replay في mixin + ترقية b12.14 | ✅ تمت — compileKotlin وcompileJava محلياً نجحا ونجح النشر على Actions والإصدار vb12.14-mc1.8.9 |
