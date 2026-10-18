# חומר ראשי ותת-סוג - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** לחומר יש שדה "חומר ראשי" ושדה "תת-סוג". כל הקיבוץ, היעדים ובדיקות החוסר הם לפי חומר ראשי וצבע. הנתונים הקיימים עוברים אוטומטית (Room 1 ל-2).

**Architecture:** `materials` מקבלת שתי עמודות, `family` ו-`variant`. `name` נשאר שם התצוגה הייחודי, ולכן כל שאילתה שמציגה `m.name` נשארת נכונה. `stock_targets` נבנית מחדש עם מפתח `(family, colorId)`. `StockCalculator` הופך גנרי בסוג המפתח: היעדים משתמשים במפתח של משפחה, והתצרוכת ממשיכה עם `StockKey(materialId, colorId)`.

**Tech Stack:** Kotlin 2.0.21, Room 2.6.1 (+ `room-testing` 2.6.1 לבדיקות בלבד), Compose, Hilt, Robolectric 4.14.1.

**Spec:** `docs/superpowers/specs/2026-10-18-inventory-views-delete-feedback-design.md`, סעיף 7ב (וכל השאר כרקע).

## Global Constraints

- כל פקודת Gradle מ-Git Bash רצה אחרי `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- תלות חדשה יחידה: `testImplementation("androidx.room:room-testing:2.6.1")`. לא לשדרג שום גרסה.
- Room: `version = 2`, `exportSchema = true`, וקובץ `app/schemas/.../2.json` נוצר ונכנס לקומיט. אסור `fallbackToDestructiveMigration`: על הטלפון יש נתונים אמיתיים.
- שם החומר המוצג (`composeMaterialName`): תת-סוג ריק נותן את החומר הראשי; תת-סוג בלי אות או ספרה נצמד ("PLA+"); אחרת רווח ("PETG Rapid"). שם של חומר קיים לא משתנה במעבר.
- השוואת חומר ראשי תמיד בלי הבדל רישיות.
- יעד = חומר ראשי + צבע. אין לו תת-סוג. ספירת היעד כוללת את כל החומרים מאותו חומר ראשי.
- ממשק בעברית, מחרוזות בקוד, מקף רגיל בלבד, בלי חצים ובלי תווי כיווניות בלתי-נראים.
- התקנה לאמולטור בלבד: `adb -s emulator-5554 install -r`. לא לגעת בטלפון `3694e208`.
- כל קומיט מסתיים ב-`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. ענף `inventory-views`.

## Review Focus

1. **מעבר על נתונים אמיתיים:** מסד בגרסה 1 עם "PLA", "PETG", "Rapid PETG", "PLA+", מוצרים, סלילים, אירועים ויעדים, עובר לגרסה 2 בלי לאבד אף שורה, וכל חומר מקבל את המשפחה הנכונה. נבדק במשימה 10 (בדיקת מעבר) ובמשימה 11 (התקנה מעל גרסה 0.2.0 באמולטור).
2. **שני יעדים שמתאחדים** (PETG שחור מינימום 1, Rapid PETG שחור מינימום 2): נשאר יעד אחד PETG שחור עם 2. נבדק במשימה 10.
3. **התראה לפי משפחה:** יעד PETG שחור, וסליל סגור רק של Rapid PETG שחור: אין התראת חסר. נבדק במשימה 10.
4. **הסרת חומר כשנשאר אחר באותה משפחה:** הסרת Rapid PETG לא מוחקת את יעדי PETG כל עוד PETG פעיל. נבדק במשימה 10.
5. **חומר חדש שזהה לקיים** ("petg" + "rapid" כשקיים "Rapid PETG" עם PETG/Rapid): משתמשים בחומר הקיים ולא נוצר כפיל. נבדק במשימה 10.

---

### Task 9: domain - תת-סוג "+", שם מורכב, מחשבון מלאי גנרי

**Files:** Modify `domain/MaterialFamily.kt`, `domain/StockCalculator.kt`, `domain/model/DomainModels.kt`; Test `domain/MaterialFamilyTest.kt`, `domain/StockCalculatorTest.kt`.

**Interfaces (Produces):**
- `materialKind(name)`: מילה שאינה חומר מוכר, אבל בלי הסיומת שאינה אות או ספרה היא חומר מוכר, מתפצלת: `materialKind("PLA+") == MaterialKind("PLA", "+")`, `materialKind("PETG++ HF") == MaterialKind("PETG", "++ HF")`.
- `fun composeMaterialName(family: String, variant: String): String` ב-`MaterialFamily.kt` (מקבל ערכים אחרי trim).
- `StockCalculator.count<K>(spools: List<Pair<K, SpoolStatus>>): Map<K, StockCount>`, `StockCalculator.alerts<K>(counts: Map<K, StockCount>, targets: List<StockTarget<K>>): List<StockAlert<K>>`; `data class StockTarget<K>(val key: K, val minSealed: Int)`, `data class StockAlert<K>(...)`. `StockKey(materialId, colorId)` נשאר (תצרוכת).

- [ ] **Step 1: בדיקות.** ב-`MaterialFamilyTest`: להחליף את `plus has no separator` ב-`plus suffix is a variant` (`MaterialKind("PLA", "+")`), ולהוסיף:
  `composeMaterialName("PETG", "") == "PETG"`, `composeMaterialName("PLA", "+") == "PLA+"`, `composeMaterialName("PETG", "Rapid") == "PETG Rapid"`, `composeMaterialName("PETG", "2") == "PETG 2"`.
  ב-`StockCalculatorTest`: בדיקה אחת עם מפתח `String` (למשל `"PETG" to 10L` כ-`Pair<String, Long>`), שמוכיחה שהמחשבון עובד עם מפתח אחר.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*MaterialFamilyTest*" --tests "*StockCalculatorTest*"`, וצפוי כישלון.
- [ ] **Step 3:** לממש. הקוראים הקיימים (`StockRepository`, `ConsumptionViewModel`) מתקמפלים עם `StockKey` כ-`K`.
- [ ] **Step 4:** `./gradlew testDebugUnitTest assembleDebug`, וצפוי BUILD SUCCESSFUL.
- [ ] **Step 5:** קומיט `feat(domain): תת-סוג +, שם חומר מורכב, מחשבון מלאי גנרי`.

---

### Task 10: data - עמודות, מעבר 1 ל-2, יעדים לפי משפחה

**Files:** Modify `data/db/Entities.kt`, `data/db/AppDatabase.kt`, `data/db/CatalogDao.kt`, `data/db/StockDao.kt`, `data/db/SpoolDao.kt`, `data/db/Projections.kt`, `data/di/DatabaseModule.kt`, `data/CatalogRepository.kt`, `data/StockRepository.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`; Create `data/db/Migrations.kt`, `app/schemas/com.amir.filamenttracker.data.db.AppDatabase/2.json` (נוצר בבנייה); Test `data/MigrationTest.kt` (חדש), `CatalogRepositoryTest`, `StockRepositoryTest`, `DatabaseTest`, `testutil/TestDb.kt` אם צריך.

**Interfaces (Produces):**
- `MaterialEntity(id, name, family: String, variant: String = "", archived)`. אינדקס ייחודי על `name` נשאר.
- `StockTargetEntity(family: String, colorId: Long, minSealed: Int = 1)`, `primaryKeys = ["family", "colorId"]`, FK ל-`colors` עם cascade כמו היום. `family` נשמר בכתיב של החומר הראשון, וההשוואה בשאילתות היא `COLLATE NOCASE`.
- `val MIGRATION_1_2: Migration` ב-`Migrations.kt`, ונרשם ב-`DatabaseModule` (`.addMigrations(MIGRATION_1_2)`). המעבר:
  1. `ALTER TABLE materials ADD COLUMN family TEXT NOT NULL DEFAULT ''` ו-`... variant TEXT NOT NULL DEFAULT ''`.
  2. לכל חומר: `materialKind(name)`, וכתיבה של `family` ושל `variant` (`variant ?: ""`).
  3. בניית `stock_targets` מחדש (טבלה חדשה, העתקה, מחיקה, שינוי שם), עם הסכמה המדויקת ש-Room מצפה לה לפי `2.json`. העתקה: `family` של חומר היעד, `colorId`, ו-`MAX(minSealed)` בקיבוץ לפי `UPPER(family)` ו-`colorId`.
- `SEED_CALLBACK` מכניס גם `family` (PLA/PETG, variant ריק).
- `SpoolRow` ו-`ProductRow`: שדות חדשים `materialFamily: String`, `materialVariant: String` (מה-SELECT, `m.family`/`m.variant`). `materialName` נשאר `m.name`.
- `TargetRow(family: String, colorId: Long, colorName: String, colorHex: String?, minSealed: Int?)`: זוג לכל משפחה (בלי רישיות) וצבע שיש לו מוצר פעיל, או שיש לו יעד.
- `AlertRow(kind, family: String, colorId, colorName, colorHex, count, minSealed)`. אין יותר `materialId` ו-`materialName` ב-AlertRow.
- `StockRepository.setTarget(family: String, colorId: Long, minSealed: Int)`, `removeTarget(family: String, colorId: Long)`. ההתראות סופרות לפי `family.uppercase()` ו-`colorId` דרך `StockCalculator` הגנרי.
- `CatalogRepository`:
  - `addMaterial(family: String, variant: String = ""): Long`: מחפש חומר עם אותו family ו-variant (NOCASE, גם בארכיון, ומחייה). אחרת מחפש לפי השם המורכב. אחרת יוצר חדש עם `composeMaterialName`. family ריק נדחה ("חומר ראשי לא יכול להיות ריק").
  - `createProduct(brand, materialFamily: String, materialVariant: String, colorName, colorHex, weightGrams, note)`.
  - `updateMaterial(id: Long, family: String, variant: String)` מחליף את `renameMaterial`: מחשב שם מחדש, ובודק התנגשות שם כמו היום.
  - `removeMaterial`: את יעדי ה-family מוחקים רק אם לא נשאר חומר פעיל אחר (לא בארכיון) עם אותו family.
  - `val materials` ממשיך להחזיר `MaterialEntity` פעילים, ועכשיו כוללים family ו-variant.

- [ ] **Step 1: תלות ונכסים לבדיקה.** `room-testing` כ-`testImplementation`. ב-`android { sourceSets { getByName("test").assets.srcDir("$projectDir/schemas") } }`.
- [ ] **Step 2: בדיקת מעבר** ב-`data/MigrationTest.kt` (Robolectric, `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)`):
  - יוצרים DB בגרסה 1 עם SQL גולמי:
    - חומרים: PLA, PETG, "Rapid PETG", "PLA+".
    - צבעים: שחור, לבן.
    - מוצר לכל חומר, סליל אחד ואירוע יצירה למוצר אחד.
    - יעדים: PETG שחור 1, Rapid PETG שחור 2, PLA לבן 1.
  - `runMigrationsAndValidate(..., 2, true, MIGRATION_1_2)`.
  - מצפים:
    - family ו-variant של "Rapid PETG" הם PETG ו-Rapid, ושל "PLA+" הם PLA ו-+.
    - השמות לא השתנו.
    - מספר המוצרים, הסלילים והאירועים זהה.
    - היעדים הם בדיוק `{PETG שחור: 2, PLA לבן: 1}`.
  - אם `MigrationTestHelper` לא עובד ב-Robolectric, מותר ליצור קובץ v1 עם `SupportSQLiteOpenHelper`, לפתוח אותו עם Room ו-`addMigrations`, ולבדוק את אותן ציפיות. לתעד בדוח.
- [ ] **Step 3: בדיקות ריפוזיטורי** (לעדכן את הקיימות לחתימות החדשות, ולהוסיף):
  - `StockRepositoryTest`: יעד PETG שחור, וסליל SEALED של מוצר "PETG"/"Rapid" שחור: אין התראה. בלי סלילים: `MISSING` עם `family == "PETG"`.
  - `CatalogRepositoryTest`:
    - `addMaterial("petg", "rapid")` אחרי שקיים PETG/Rapid מחזיר את אותו id.
    - `addMaterial("PLA", "+")` יוצר שם "PLA+".
    - הסרת Rapid PETG (מוצר בלי סלילים, ולכן נמחק) כשקיים PETG פעיל משאירה את יעד PETG שחור. הסרת החומר האחרון במשפחה מוחקת אותו.
    - `updateMaterial` מחשב שם מחדש.
- [ ] **Step 4:** להריץ ולוודא כישלון, לממש, ו-`./gradlew testDebugUnitTest assembleDebug` ירוק. (`presentation` חייב להתקמפל: מינימום התאמות לקוראים הקיימים, כמו `createProduct` ב-ViewModels עם `variant = ""` והפרדת שם, `TargetsViewModel` לפי `family`, `AlertsCard` ו-`materialTable` לפי `AlertRow.family`. העבודה המלאה על הממשק היא במשימה 11.)
- [ ] **Step 5:** קומיט `feat(data): חומר ראשי ותת-סוג, מעבר מסד 1 ל-2, יעדים לפי חומר ראשי` (כולל `2.json`).

---

### Task 11: presentation - טופס, קטלוג, יעדים ותצוגות

**Files:** Modify `presentation/common/ProductDialogs.kt`, `presentation/catalog/CatalogScreen.kt` + `CatalogViewModel.kt`, `presentation/inventory/AddExistingViewModel.kt`, `presentation/orders/NewOrderViewModel.kt`, `presentation/targets/TargetsScreen.kt` + `TargetsViewModel.kt`, `presentation/inventory/InventoryViews.kt`, `InventoryScreen.kt`, `InventoryTables.kt`; Test `presentation/inventory/InventoryGroupingTest.kt`.

**Interfaces:**
- `NewProductInput(brand, materialFamily: String, materialVariant: String, colorName, colorHex, weightGrams, note)`.
- `NewProductDialog(materials: List<MaterialEntity>, colors, brands, onDismiss, onConfirm)`: במקום השדה "חומר" באים:
  - **"חומר ראשי":** `OutlinedTextField`, ומתחתיו צ'יפים של המשפחות הייחודיות מתוך `materials`, בלי רישיות, מהכתיב הראשון. ברירת המחדל: המשפחה הראשונה.
  - **"תת-סוג, לא חובה":** `OutlinedTextField`, ומתחתיו צ'יפים של ה-variants הלא-ריקים של המשפחה שבשדה הראשי.
  - `canSave` דורש חומר ראשי לא ריק.
- קטלוג: "חומר חדש" ו"עריכת חומר" עם שני שדות ("חומר ראשי", "תת-סוג, לא חובה"), דרך `addMaterial(family, variant)` ו-`updateMaterial`. הרשימה מציגה את `name`.
- יעדים: שורה לכל `TargetRow`, עם הכותרת `"${row.family} ${row.colorName}"`.
- `InventoryViews`:
  - המשפחה היא `SpoolRow.materialFamily` (קיבוץ לפי `uppercase()`, ומציגים את הכתיב של החומר הראשון בסדר שם). הסוג הוא `materialVariant` (ריק פירושו בלי סוג). `materialKind` כבר לא משמש כאן.
  - ב-`materialTable` "חסר" נקבע לפי `AlertRow.family` ו-`colorId`. תא חסר בלי סלילים אינו לחיץ (`materials` ריק).
  - `AlertsCard` מציג `"${family} ${colorName}"`.
- [ ] **Step 1:** לעדכן את `InventoryGroupingTest` לשדות החדשים: `row(...)` מקבל `family` ו-`variant` במקום לגזור מהשם, ו-`missing(family, color, colorId)`. להוסיף בדיקה ש-"Rapid PETG" עם family PETG ו-variant Rapid נכנס לעמודה PETG ומוצג "אפור · Rapid".
- [ ] **Step 2:** להריץ, לוודא כישלון, לממש, ולהריץ `./gradlew testDebugUnitTest assembleDebug` ירוק.
- [ ] **Step 3: אמולטור.** באמולטור מותקנת היום גרסה 0.2.0, עם מסד בגרסה 1:
  1. לפני ההתקנה ליצור בקטלוג חומר "Rapid PETG" (שם בלטינית) ומוצר איתו.
  2. להתקין עם `install -r` ולפתוח.
  3. הנתונים שרדו, וב"טבלה לפי חומר" אין עמודה Rapid.
  4. ליצור מוצר חדש עם "PETG" ו-"Rapid": משתמשים בחומר הקיים ולא נוצר כפיל.
  5. ליצור "PLA" ו-"+": נוצר "PLA+" תחת PLA.
  6. ביעדים יש שורה אחת לכל משפחה וצבע.
  7. צילום מסך לכל בדיקה.
- [ ] **Step 4:** קומיט `feat(ui): חומר ראשי ותת-סוג בטופס ובקטלוג, יעדים ותצוגות לפי חומר ראשי`.

---

### Task 12: תיעוד וגרסה

- [ ] `app/build.gradle.kts`: `versionCode = 3`, `versionName = "0.3.0"`.
- [ ] `SPEC.md`:
  - ב"מה עובד": חומר ראשי ותת-סוג, ויעדים לפי חומר ראשי.
  - ב"מה פתוח":
    - תת-סוג תלוי יצרן ("Rapid" אצל אחד הוא "מהירות גבוהה" אצל אחר). איחוד שמות כאלה הוא לעתיד.
    - התצרוכת עדיין לפי חומר מלא.
  - הפניה לסעיף 7ב.
- [ ] `current_focus.md`: בלוק עליון חדש (0.3.0 על הענף, לא מוזג, עוד לא מותקן בטלפון, ולפני התקנה צריך גיבוי של קובץ המסד).
- [ ] `./gradlew testDebugUnitTest assembleDebug` ירוק. קומיט `docs: חומר ראשי ותת-סוג (0.3.0)`.
