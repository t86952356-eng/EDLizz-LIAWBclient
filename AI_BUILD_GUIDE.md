# AI_BUILD_GUIDE.md — دليل بناء مشروع Lizz (للذكاء الاصطناعي)

> هذا الملف مكتوب ليقرأه وكيل ذكاء اصطناعي (AI Agent) في محادثة جديدة، ليتمكن من بناء
> مشروع Lizz ونشر إصدار (Release) بنجاح من أول مرة، دون تكرار الأخطاء التي حُلّت مسبقاً.
> آخر تحديث: نوفمبر 2026. إصدار مُختبر ناجح: `lizz-b12.7.jar` لـ Minecraft 1.8.9 (Forge)
> عبر Release بتاغ `vb12.7-mc1.8.9`.
> ملف مرافق إلزامي: `LIZZ_HANDOFF.md` (معلومات العميل + البرمجة + سجل الأخطاء).

---

## 1) نظرة عامة على المشروع

- المشروع: **Lizz** (يعرف أيضاً بـ **LAWIB / LIAWB client**) — عميل مود Minecraft مبني على
  **LiquidBounce Legacy** (الفرع المجتمعي الأخير b100)، يعمل على **Minecraft 1.8.9 + Forge**.
- المستودع: `https://github.com/t86952356-eng/EDLizz-LIAWBclient` — الفرع الرئيسي `main`
  (**المستودع عام / Public**).
- السورس كامل في **جذر المستودع**: `build.gradle`, `settings.gradle`, `gradle.properties`,
  `gradlew`, `src/`, `libs/`, `addons/`, `maven-repo/`.
- لغة السورس: **Kotlin 2.0.21** (ملفات `.kt` موضوعة تحت `src/main/java` — ليس src/main/kotlin!)
  مع ملفات Java قليلة (125 ملف مقابل 670 ملف Kotlin).

## 2) الإصدارات الأساسية (من ملفات المشروع — لا تخترع قيماً)

| العنصر | القيمة | المصدر |
|---|---|---|
| Minecraft | `1.8.9` | `gradle.properties` → `minecraft_version` |
| Forge | `11.15.1.2318` | `gradle.properties` → `forge_version` |
| mappings | `stable_22` (FG 2.x MCP) | `build.gradle` → `minecraft { }` |
| **JDK للبناء** | **`8`** | `build.gradle` (`sourceCompatibility = 1.8`) + CI الأصلي |
| Gradle Wrapper | `6.9.4` (مرآة huaweicloud) | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | `2.0.21` | `gradle.properties` → `kotlin_version` |
| إصدار المود | `b12.7` | `gradle.properties` → `mod_version` |
| اسم الـ artifact | `lizz` | `gradle.properties` → `archives_base_name` |
| ناتج البناء | `build/libs/lizz-b12.7.jar` | — |

> ⚠️ **جافا هي 8 وليست 17.** Gradle 6.9.4 لا يعمل أصلاً على JDK 17+ (أول دعم لـ17 كان
> Gradle 7.3). الـ CI الرسمي لـ LiquidBounce Legacy يستخدم `java-version: 8` حرفياً.

## 3) ⚠️ maven-repo/ — إضافات Gradle الموثّنة (أهم قسم في هذا الدليل)

في **نوفمبر 2026** قام jitpack.io بحذف ملفات `com.github.ccbluex:ForgeGradle:a3d86a59c0`
من تخزينه (حالة البناء تبقى "ok" لكن الملفات ترجع **HTTP 404**)، فتفشل أي عملية بناء نظيفة
برسالة `Plugin Repositories (could not resolve plugin artifact ...)`.

**الحل المعمول به**: مجلد `maven-repo/` داخل المستودع يحتوي نسخة موثّنة من:

1. `com.github.ccbluex:ForgeGradle:a3d86a59c0` — **أُعيد بناؤها من المصدر** من نفس الـ commit
   في مستودع `CCBlueX/ForgeGradle` (Gradle 7.0.2 + JDK 8 + `publish`) ثم أُعيدت كتابة
   إحداثيات الـ POM من `net.minecraftforge.gradle:ForgeGradle:2.1` إلى إحداثيات jitpack.
2. `com.github.xcfrg:mixingradle:ae2a80e` — نُزّلت من jitpack بينما كانت متاحة.
3. `com.github.abrarsyed.jastyle:jAstyle:1.3` — أُعيد تجميعها بـ `javac` من مستودع المصدر.

و`settings.gradle` يضع هذا المستودع **أولاً** قبل jitpack:

```groovy
maven { url = new File(settingsDir, 'maven-repo').toURI() }
```

**قواعد صارمة**: لا تحذف `maven-repo/`، ولا تعدّل إحداثياته، ولا تحذف سطر المستودع المحلي
من `settings.gradle`. تفاصيل إعادة البناء بالكامل في `maven-repo/README.md`.
تبعيات ForgeGradle المتبقية (lzma-java, named-regexp من Maven Central؛ fernflower من
maven.minecraftforge.net) تعمل بشكل طبيعي.

## 4) البناء المحلي (اختياري — الأصل هو GitHub Actions)

البيئة السحابية (sandbox) فيها JDK 21 فقط. لتحميل JDK 8 بدون sudo:

```bash
curl -sL -o /tmp/jdk8.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf /tmp/jdk8.tar.gz -C $HOME        # ينتج $HOME/jdk8u512-b09
export JAVA_HOME=$HOME/jdk8u512-b09 && export PATH=$JAVA_HOME/bin:$PATH
```

أوامر البناء المحلي (ذاكرة الـ sandbox محدودة ~4GB — لا تستخدم -Xmx3g الافتراضية):

```bash
cd <مسار المستودع>
chmod +x gradlew   # إذا كانت صلاحية التنفيذ مفقودة من القرص (انظر §6-1)
JAVA_HOME=$HOME/jdk8u512-b09 ./gradlew help --no-daemon --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1500m -XX:MaxMetaspaceSize=512m"   # اختبار حلّ الإضافات
JAVA_HOME=$HOME/jdk8u512-b09 ./gradlew build --no-daemon --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1500m -XX:MaxMetaspaceSize=512m"   # بناء كامل (قد يُقتل بالذاكرة)
```

- `./gradlew help` يكفي للتحقق من صحة حلّ الإضافات (settings.gradle + maven-repo) — يستغرق ~1.5 دقيقة.
- البناء الكامل محلياً محفوف بالمخاطر بسبب الذاكرة؛ **القناة الموثوقة هي GitHub Actions** (§5).
- خطوة تحضير Minecraft الأولى (تنزيل + فك التحويل) تستهلك وقتاً وذاكرة — على Actions متاحة لها 7GB.

## 5) البناء والنشر عبر GitHub Actions (الطريقة الأساسية)

الملف `.github/workflows/release.yml` (نفس نمط AtroNextGen) يقوم بكل شيء تلقائياً.

**محفّزات الـ workflow** (أياً منها يكفي):
- دفع أي اعتماد إلى فرع **`release/build`** (الطريقة المفضلة).
- `workflow_dispatch` (زر يدوي من تبويب Actions).
- دفع تاغ يبدأ بـ `v`.

**الخطوات المُختبرة (مع توكن PAT متاح في الجلسة):**

```bash
# 0) git add + git commit على main (إعداد git مرة واحدة: user.name=Lizz, email=t86952356-eng@users.noreply.github.com)
git push origin main                     # 1) مزامنة main
git push origin main:release/build       # 2) دفع نفس الـ commit إلى فرع البناء ← ينطلق الـ workflow
# 3) المراقبة عبر API (البناء النظيف يستغرق ~3-15 دقيقة):
curl -s -H "Authorization: token $GH_TOKEN" \
  "https://api.github.com/repos/t86952356-eng/EDLizz-LIAWBclient/actions/runs?per_page=1"
```

**ما ينتج تلقائياً عند النجاح:**
- Release بعنوان: `Lizz [v{mod_version}] — Minecraft 1.8.9 (Forge)`
- تاغ: `v{mod_version}-mc1.8.9` (مثال: `vb12.7-mc1.8.9`) يشير إلى نفس الـ commit المبني.
- المرفقات:
  - `lizz-{mod_version}.jar` — مود JAR نهائي (مضغوط بـ shadow + مُعاد تحويله إلى أسماء SRG
    عبر `reobfShadowJar` — يعمل على اللعبة الحقيقية).
  - `lizz-{mod_version}-source.zip` — السورس الكامل للـ commit المبني (`git archive`).
- الـ changelog/ملاحظات الإصدار **بالإنجليزية فقط** (قاعدة مستخدم دائمة).

**إعادة تشغيل نفس الإصدار (مثلاً بعد إضافة ملفات توثيق):** ادفع commit جديداً إلى
`main` ثم `release/build`. يُنشئ الـ workflow نفس التاغ؛ `gh release create` سيفشل لوجود
التاغ فيتجاوزه الـ workflow تلقائياً بـ `gh release upload "$TAG" dist/* --clobber`
(تحديث المرفقات واستبدالها). **ملاحظة**: نص ملاحظات الـ Release يبقى من الإنشاء الأول؛
لتحديثه استخدم `gh release edit` أو API `/releases/{id}` مع حقل `body`.

**لترقية إصدار المود:** غيّر `mod_version` في `gradle.properties` (مثلاً `b12.8`)، اعتمد،
وادفع إلى `main` ثم `release/build` — يصدر Release وتاغ جديدان تلقائياً.

## 6) مشاكل معروفة وحلولها (وُاجهت فعلياً — لا تتفاجأ منها)

1. **exit code 126 عند تشغيل `./gradlew` على Actions** → ملف `gradlew` مسجل في git بصلاحية
   `100644` بدل `100755`. الإصلاح الدائم (تم):
   `git update-index --chmod=+x gradlew` ثم اعتماد ودفع. ملاحظة: تغيير الفهرس لا يغيّر
   صلاحية الملف على القرص في النسخة الحالية — نفّذ `chmod +x gradlew` يدوياً قبل أي بناء محلي.
2. **`could not resolve plugin artifact 'com.github.ccbluex:ForgeGradle:a3d86a59c0'`** →
   احتلال jitpack. الحل الدائم منفّذ: `maven-repo/` الموثّن (انظر §3). لا تحاول حلها من jitpack —
   طلب إعادة البناء لديهم لا يستجيب لأن حالة البناء مخزنة "ok".
3. **`inconsistent module metadata ... bad version` عند توثين SpecialSource** → إذا أعدت بناء
   ForgeGradle يوماً ما: النسخة داخل الـ POM يجب أن تطابق اسم مجلد الإصدار **حرفياً**
   (لا تضع POM فيه `1.7.4-SNAPSHOT` بينما المجلد `1.7.4-SpecialSource-1.6.1-gf5cd633-34`).
   أعد كتابة وسم `<version>` ليطابق المسار.
4. **429 Too Many Requests من repo.maven.apache.org داخل الـ sandbox** → مشكلة معروفة في
   البيئة المحلية فقط (الطلبات المتزامنة). المرآة: ضع
   `maven { url "https://maven-central.storage-download.googleapis.com/maven2" }` أولاً في
   المستودعات — **للاستخدام المحلي فقط ثم يُسترجع قبل الاعتماد**. على GitHub Actions لا
   تحدث المشكلة أصلاً.
5. **الـ Gradle process "يعلق" بعد BUILD SUCCESSFUL داخل الـ sandbox** → الـ daemon أحادي
   الاستخدام (المُشتق بسبب `--no-daemon` مع jvmargs) قد لا يُغلق. شغّل البناءات الطويلة في
   الخلفية (`nohup ... > /tmp/build.log 2>&1 &`) وراقب السجل — اكتمال BUILD SUCCESSFUL في
   السجل دليل النجاح حتى لو لم تعد العملية.
6. **`zip_include/` غير موجود** → مهمة `copyZipInclude` في `build.gradle` تنسخ من مجلد غير
   موجود؛ Gradle يتجاهله بلا فشل. سلوك طبيعي — لا تعالجه.
7. **توقّع جافا 17**: أي مرجع قديم يقول JDK 17 هو خطأ؛ هذا مشروع 1.8.9/Gradle 6.9.4 ويتطلب
   JDK 8 (راجع §2). (AtroNextGen هو الذي يستخدم JDK 21 — لا تخلط بين المشروعين.)

## 7) التحقق من صحة مود JAR قبل الإعلان عنه (مُختبر)

```bash
unzip -p build/libs/lizz-b12.7.jar META-INF/MANIFEST.MF   # يجب أن يحوي: FMLCorePlugin,
# MixinConfigs: liquidbounce.forge.mixins.json, TweakClass, FMLAT: liquidbounce_at.cfg
unzip -p build/libs/lizz-b12.7.jar mcmod.info              # "version": "b12.7" موسّعة
unzip -p build/libs/lizz-b12.7.jar net/ccbluex/liquidbounce/injection/forge/mixins/entity/MixinEntity.class \
  | strings | grep -cE "field_[0-9]+_|func_[0-9]+_"        # > 0 يعني أن reobf (SRG) تم بنجاح
```

إذا كان العدد 0: الـ jar بأسماء MCP (بيئة تطوير فقط) ولن يعمل على اللعبة — راجع
`shadowJar.finalizedBy tasks.reobfShadowJar` في `build.gradle`.

## 8) قائمة تحقق سريعة عند بدء محادثة جديدة

1. المستودع عام؛ استخدم PAT إذا كان متاحاً في الجلسة، وإلا اطلب من المستخدم متابعة تبويب
   Actions بعد الدفع إلى `release/build`.
2. `maven-repo/` موجود و`settings.gradle` يستشهد به أولاً — إن فُقد أحد الإضافات، أعد
   توثينها بالرجوع إلى §3 و`maven-repo/README.md`.
3. لا تبدأ البناء إن كان `build/libs/lizz-*.jar` موجوداً مسبقاً بالنسخة المطلوبة.
4. عند كل مهمة تعديل: اعتمد على `main` ثم ادفع `main:release/build` = دورة بناء + Release
   واحدة نظيفة (لا تدفع ملفات متعددة بعمليات متزامنة).
5. بعد أي إصدار: حدّث `LIZZ_HANDOFF.md` (سجل المهام والأخطاء) وادفعه ضمن نفس الاعتماد أو
   اعتماد متابعة.
6. الـ changelog في الـ Release بالإنجليزية فقط.

## 9) ملفات مرتبطة من المشروع

- `gradle.properties` — كل الإصدارات (`mod_version` هنا هو مفتاح الترقية).
- `build.gradle` — إعداد FG + shadow + reobf + استثناء `**/clickgui/style/styles/nextgen/**`
  من الترجمة (كود FDP غير مُهيأ — لا تفعّله).
- `settings.gradle` — ترتيب مستودعات الإضافات (المحلي أولاً).
- `maven-repo/README.md` — توثيق كامل لمصدر كل إضافة موثّنة وكيفية إعادة بنائها.
- `LIZZ_HANDOFF.md` — معلومات العميل + البرمجة + سجل الأخطاء والمهام.
- `.github/workflows/release.yml` — الـ Release workflow.
- `.github/workflows/build.yml` — CI قديم موروث من upstream (يستهدف فرع `legacy` غير
  موجود؛ لا يُشغّل أبداً — اتركه كما هو).
