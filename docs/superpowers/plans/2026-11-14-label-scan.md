# סריקת מדבקות - תוכנית מימוש

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** צילום של מדבקת סליל או אריזה מזהה את הסליל הרשום. הזיהוי משמש לטעינה לחריץ AMS, לסימון הגעה של משלוח ולעדכון סליל גנרי ליצרן האמיתי, ותמיד מחכה לאישור.

**Architecture:** יש שלוש שכבות.
- **פענוח הטקסט** (`domain/LabelReading.kt`): פונקציה טהורה שמקבלת טקסט ומחזירה יצרן, חומר ראשי, תת-סוג, גימור וצבעים.
- **התאמה** (`presentation/scan/ScanMatcher.kt`): לוגיקה טהורה מול `SpoolRow`.
- **סורק** (`data/scan/LabelScanner.kt`): עוטף את ML Kit ומחזיר טקסט וברקודים מתמונה.

הצילום נעשה באפליקציית המצלמה של המערכת (`TakePicture` עם FileProvider), ולכן אין צורך בהרשאת מצלמה. ברקודים נשמרים בטבלה חדשה, ולכן המסד עובר מגרסה 4 ל-5.

**Tech Stack:** Kotlin, Compose Material3, Room 2.6.1, Hilt, ML Kit Text Recognition v2 (Latin, bundled), ML Kit Barcode Scanning (bundled), JUnit4 + Robolectric. לבדיקה האחת שמריצה את ML Kit האמיתי משתמשים ב-androidTest.

**Spec:** `docs/superpowers/specs/2026-11-14-label-scan-design.md`

## Global Constraints

- **תמיד מחכים לאישור:** שום פעולה לא מתבצעת ישירות מתוצאת זיהוי. טעינה, סימון הגעה, עדכון ושמירת ברקוד קורים רק אחרי לחיצה על כפתור.
- **אין התאמה או שהטקסט לא נקרא:** חוזרים לרשימה הרגילה כמו היום. הרשימה הרגילה בחלון החריץ זמינה תמיד, גם בלי סריקה.
- **הכול בטלפון:** אין שימוש באינטרנט.
- **מסד הנתונים:** מגרסה 4 לגרסה 5 עם `MIGRATION_4_5` ובדיקת מעבר. לא משתמשים ב-`fallbackToDestructiveMigration`.
- **גרסת האפליקציה:** `versionName = "0.6.0"`, `versionCode = 10`.
- **מה נחשב גנרי:** שם היצרן, אחרי trim ובלי תלות ברישיות, הוא "גנרי" או "generic", או ריק.
- **טקסט בממשק:** בעברית, בלי חצים ובלי מקף ארוך.
- **בדיקות:** רק על האמולטור (`-s emulator-5554`). לא מתקינים על הטלפון של אמיר בלי בקשה מפורשת.
- **סביבה:** `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`, ואת adb מריצים בנתיב המלא של ה-SDK.
- **פקודת הבדיקה המלאה:** `./gradlew testDebugUnitTest assembleDebug`. בהתחלה יש 208 בדיקות ירוקות.

## Review Focus

1. **תמונה בלי טקסט קריא** (מטושטשת, חשוכה, צילום של קיר): התוצאה היא `ScanMatch.None` והרשימה הרגילה, בלי קריסה. נבדק במשימה 2 (`empty text gives None`).
2. **יצרן שנקרא לא נכון או לא קיים במסד, כשיש סלילים לא-גנריים מאותו קו וצבע:** מוצגת התאמה עם הסימון "המותג לא אומת", לא None ולא בחירה אוטומטית. נבדק במשימה 2 (`unknown brand with same line shows unconfirmed match`).
3. **התאמת יתר בגימור או בצבע:** "Silk Gold" לא מתאים ל-PLA Gold רגיל, ו-"Black" לא מתאים לסליל "Black / Red". נבדק במשימה 2 (`finish must match exactly`, `combined color needs all parts`).
4. **רעש על המדבקה** ("MADE IN CHINA", "1.75mm", "Printing Temp", "Net weight : 1KG", מספרי מק"ט): הוא לא הופך למונח, לצבע או ליצרן. נבדק במשימה 1 עם הטקסט המלא של מדבקת XZN.
5. **ביטול הצילום** (חזרה אחורה ממסך המצלמה): המצב חוזר ל-`Idle` והחלון נשאר פתוח כמו שהיה. נבדק ידנית במשימה 5, שלב 6.

---

### Task 1: פענוח טקסט של מדבקה

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/domain/LabelReading.kt`
- Modify: `app/src/main/java/com/amir/filamenttracker/domain/ColorSwatch.kt` (מוסיפים `colorKey`)
- Test: `app/src/test/java/com/amir/filamenttracker/domain/LabelReadingTest.kt`

**Interfaces:**
- Produces:
  - `fun colorKey(name: String): String`, ב-`ColorSwatch.kt`. מחזיר את מפתח הפלטה לשם מוכר: אותו ערך ל-"שחור", "black" ו-"Black". לשם לא מוכר הוא מחזיר את השם עצמו, אחרי trim ו-lowercase.
  - `fun isGenericBrand(brand: String): Boolean`
  - `data class LabelReading(val brand: String?, val family: String?, val variant: String, val finish: String, val colors: Set<String>)`. `colors` מכיל מפתחות של `colorKey`. `variant` ו-`finish` בכתיב הקנוני של `normalizeMaterialParts`.
  - `fun readLabel(text: String, knownBrands: List<String>, knownFamilies: List<String>, knownColors: List<String>): LabelReading`

**כללי הפענוח** (הבונה כותב את הגוף):
- **חומר ראשי:** המילה הראשונה בטקסט ששווה, בלי תלות ברישיות, לאחד מ-`knownFamilies`. אם המילה היא משפחה ואחריה "+" (למשל "PLA+"), התוצאה היא family "PLA" ו-variant "+", כמו ב-`materialKind`.
- **גימור ותת-סוג:** לכל מונח ב-`VARIANT_TERMS`, `COMMON_FINISH_TERMS` ו-`MORE_FINISH_TERMS` בודקים `hasTerm(text, term)`. את התוצאה מעבירים דרך `normalizeMaterialParts`. מונחים בני 2-3 אותיות ("hs", "hf", "tri", "dual") נספרים רק כמילה שלמה, וזה כבר מה ש-`hasTerm` עושה. המונח "clear" לא נחשב לגימור אם הוא מופיע אחרי "Color:".
- **צבעים:** מילים ש-`colorKey` שלהן שווה ל-`colorKey` של שם בפלטה או של אחד מ-`knownColors`.
- **יצרן:** קודם בודקים את `knownBrands`, כמילה שלמה ובלי תלות ברישיות, וכשיש כמה התאמות הארוכה מנצחת. אם אין, לוקחים את השורה הראשונה שאינה ריקה, שכולה אותיות לטיניות (2 עד 20 תווים), ושאינה משפחה, מונח או צבע. אם גם כזו אין, התוצאה `null`. יצרן גנרי לא נכלל ב-`knownBrands` (הקורא מסנן אותו).

- [ ] **Step 1: Write the failing tests**

```kotlin
private val XZN = """
    XZN
    PLA 1.75mm Color: Silk Gold
    700-001-1216 BTBG0438C6
    Printing Temp : 195°C-230°C
    Bed Temp : 30°C-60°C
    Net weight : 1KG
    MADE IN CHINA
""".trimIndent()

@Test fun `xzn label reads brand family finish and color`() {
    val r = readLabel(XZN, knownBrands = listOf("iSANMATE"), knownFamilies = listOf("PLA", "PETG"), knownColors = listOf("שחור", "זהב"))
    assertEquals(LabelReading(brand = "XZN", family = "PLA", variant = "", finish = "Silk", colors = setOf(colorKey("gold"))), r)
}
@Test fun `known brand wins over first line`() { /* "Sunlu\nPETG Black" with knownBrands=["SUNLU"] gives brand "SUNLU" (spelling from knownBrands) */ }
@Test fun `plus attaches to family`() { /* "PLA+ Matte Black": family PLA, variant "+", finish "Matte" */ }
@Test fun `hebrew and english color names share a key`() { assertEquals(colorKey("שחור"), colorKey("Black")) }
@Test fun `generic brand spellings`() { listOf("גנרי", " Generic ", "").forEach { assertTrue(isGenericBrand(it)) }; assertFalse(isGenericBrand("XZN")) }
@Test fun `empty text reads nothing`() { assertEquals(LabelReading(null, null, "", "", emptySet()), readLabel("", emptyList(), listOf("PLA"), emptyList())) }
```

- [ ] **Step 2:** מריצים `./gradlew testDebugUnitTest --tests "*LabelReadingTest*"`. Expected: FAIL (unresolved reference).
- [ ] **Step 3:** מממשים את `colorKey`, `isGenericBrand`, `LabelReading` ו-`readLabel` לפי הכללים למעלה.
- [ ] **Step 4:** מריצים שוב את אותה פקודה. Expected: PASS. אחר כך את הפקודה המלאה, ואף בדיקה קיימת לא נשברת.
- [ ] **Step 5:** קומיט `feat(domain): פענוח טקסט של מדבקת סליל`.

---

### Task 2: התאמת קריאה לסלילים

**Files:**
- Create: `app/src/main/java/com/amir/filamenttracker/presentation/scan/ScanMatcher.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/presentation/scan/ScanMatcherTest.kt`

**Interfaces:**
- Consumes: `LabelReading`, `colorKey`, `isGenericBrand`, `colorParts` (Task 1, `ColorSwatch.kt`), `SpoolRow`.
- Produces:

```kotlin
sealed interface ScanMatch {
    /** brandConfirmed=false: matched by line and color only; the label's brand is [labelBrand]. */
    data class Found(val spools: List<SpoolRow>, val brandConfirmed: Boolean, val labelBrand: String?) : ScanMatch
    data class GenericSuggestion(val labelBrand: String, val spools: List<SpoolRow>) : ScanMatch
    data class KnownProductNoSpool(val productId: Long) : ScanMatch
    data object None : ScanMatch
}
fun matchScan(reading: LabelReading, barcodeProductId: Long?, candidates: List<SpoolRow>): ScanMatch
```

**הכללים, לפי הסדר:**
1. אם יש `barcodeProductId`: מחפשים מועמדים עם אותו `productId`. אם יש כאלה, `Found(them, true, reading.brand)`. אם אין, ממשיכים לכללים 2 עד 8 לפי הטקסט (מסמך העיצוב, 6.4). אם גם הם מחזירים `None`, התוצאה `KnownProductNoSpool(barcodeProductId)` (מסמך העיצוב, 6.6).
2. אם `reading.family == null` או `reading.colors` ריק, התוצאה `None`.
3. **קו:** `materialFamily` שווה ל-family בלי תלות ברישיות, ו-`materialFinish` שווה ל-`reading.finish` בלי תלות ברישיות. גימור ריק שווה רק לגימור ריק.
4. **צבע:** קבוצת `colorKey` של `colorParts(colorName)` שווה בדיוק ל-`reading.colors`.
5. בין המועמדים שעברו את 3 ו-4: אם יש כאלה שהיצרן שלהם שווה ל-`reading.brand` (בלי תלות ברישיות), `Found(them, true, brand)`.
6. אחרת, אם `reading.brand != null` ויש ביניהם גנריים: `GenericSuggestion(reading.brand, generics)`.
7. אחרת, אם נשארו מועמדים: `Found(them, brandConfirmed = reading.brand == null, reading.brand)`.
8. אחרת, `None`.
9. **שובר שוויון:** בתוך `Found`, מועמדים שה-`materialVariant` שלהם שווה ל-`reading.variant` מופיעים קודם. מעבר לזה הסדר של `candidates` נשמר.

- [ ] **Step 1: Write the failing tests.** מכינים פונקציית עזר `row(id, brand, family, finish, color, productId = id, status = SEALED, variant = "")` שבונה `SpoolRow`. הבדיקות:
  - `barcode product with spool is confirmed found`
  - `barcode product without candidate spool`: הקריאה ריקה ו-`barcodeProductId=7` בלי מועמד, והתוצאה `KnownProductNoSpool(7)`
  - `barcode without candidate falls back to text`: ל-`barcodeProductId=7` אין מועמד, אבל הטקסט מתאים לסליל אחר, והתוצאה `Found`
  - `empty text gives None` (`readLabel("")`)
  - `brand match narrows to that brand`: מועמדים iSANMATE ו-Sunlu, שניהם PETG ו-Black. הקריאה עם brand "iSANMATE" מחזירה רק את iSANMATE, עם `brandConfirmed=true`.
  - `generic suggested when label brand missing`: מועמדים "גנרי" ו-Sunlu, שניהם PLA Silk Gold. הקריאה עם brand "XZN" מחזירה `GenericSuggestion("XZN", [generic])`.
  - `unknown brand with same line shows unconfirmed match`: מועמד יחיד Sunlu PETG Black. הקריאה עם brand "XYZ" מחזירה `Found([sunlu], false, "XYZ")`.
  - `finish must match exactly`: הקריאה PLA Silk Gold מול מועמד PLA, גימור "" ו-Gold מחזירה `None`.
  - `combined color needs all parts`: הקריאה Black מול מועמד "Black / Red" מחזירה `None`.
  - `hebrew color name matches english label`: מועמד עם הצבע "שחור" והקריאה Black נותנים `Found`.
  - `variant breaks ties`: כששני מועמדים מתאימים, זה עם variant "High Speed" מופיע ראשון כשהקריאה היא variant "High Speed".
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*ScanMatcherTest*"`. Expected: FAIL.
- [ ] **Step 3:** מממשים את `matchScan`.
- [ ] **Step 4:** מריצים שוב. Expected: PASS.
- [ ] **Step 5:** קומיט `feat(scan): התאמת קריאת מדבקה לסלילים`.

---

### Task 3: ברקודים, מעבר מסד 4 ל-5, ועדכון סליל גנרי

**Files:**
- Modify: `data/db/Entities.kt` (מוסיפים `ProductBarcodeEntity`), `data/db/AppDatabase.kt` (version 5, entity, `barcodeDao()`), `data/db/Migrations.kt` (`MIGRATION_4_5`), `data/di/DatabaseModule.kt` (`addMigrations`), `data/db/CatalogDao.kt` (`productByParts`), `data/CatalogRepository.kt`, `data/InventoryRepository.kt`
- Create: `data/db/BarcodeDao.kt`, `app/schemas/.../5.json` (נוצר בבנייה)
- Test: `MigrationTest.kt`, `CatalogRepositoryTest.kt`, `InventoryRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `@Entity(tableName = "product_barcodes", foreignKeys = [products.id, onDelete CASCADE], indices = [Index("productId")]) data class ProductBarcodeEntity(@PrimaryKey val code: String, val productId: Long)`
  - `BarcodeDao`: `@Query suspend fun productFor(code: String): Long?` ו-`@Upsert suspend fun save(entity: ProductBarcodeEntity)`
  - `CatalogDao.productByParts(brand: String, materialId: Long, colorId: Long, weightGrams: Int): ProductEntity?`. היצרן מושווה עם `COLLATE NOCASE`, ונבחרים רק מוצרים עם `archived = 0`.
  - `CatalogRepository.productForBarcode(code: String): Long?` ו-`CatalogRepository.rememberBarcode(code: String, productId: Long)`. השמירה האחרונה דורסת את הקודמת.
  - `InventoryRepository.retagSpool(spoolId: Long, brand: String): Long`. מחזירה את מזהה המוצר החדש. אם היצרן הנוכחי של הסליל לא גנרי, היא זורקת `SpoolRuleException("אפשר לעדכן רק סליל גנרי")`. היא מחפשת או יוצרת מוצר עם אותו `materialId`, `colorId` ו-`weightGrams`, וה-`note` שלו ריק. אחר כך היא קוראת ל-`spoolDao.updateProduct`, בלי אירוע (כמו `changeOrderedProduct`). יצרן ריק אחרי trim נדחה עם `SpoolRuleException("יש להזין יצרן")`.

- [ ] **Step 1: Write the failing tests:**
  - `MigrationTest`: `migration 4 to 5 adds an empty barcode table and keeps every row`. יוצרים מסד בגרסה 4 עם מוצר וסליל, מריצים `runMigrationsAndValidate(..., 5, true, MIGRATION_4_5)`, ובודקים ש-`count("product_barcodes") == 0` ושהמוצרים והסלילים נשארו.
  - `CatalogRepositoryTest`: `barcode remembered and overwritten`. אחרי `rememberBarcode("123", a)` ו-`productForBarcode("123") == a`, קוראים ל-`rememberBarcode("123", b)` ומקבלים `b`. בנוסף, `productForBarcode("999") == null`.
  - `InventoryRepositoryTest`:
    - `retag generic spool moves only that spool`: שני סלילים של "גנרי PLA זהב". אחרי `retagSpool(first, "XZN")`, ל-first יש מוצר XZN עם אותו חומר וצבע, ו-second נשאר גנרי.
    - `retag reuses existing product`: אם כבר יש XZN PLA זהב, לא נוצר מוצר נוסף.
    - `retag rejects non generic`: יצרן Sunlu גורם לזריקת `SpoolRuleException`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*MigrationTest*" --tests "*CatalogRepositoryTest*" --tests "*InventoryRepositoryTest*"`. Expected: FAIL.
- [ ] **Step 3:** מוסיפים את הטבלה, ה-DAO, `version = 5` ו-`MIGRATION_4_5`. ה-SQL של יצירת הטבלה חייב להתאים בדיוק ל-`5.json` שנוצר: בונים פעם אחת, מעתיקים מה-JSON את `createSql` ואת יצירת האינדקס, ומוסיפים הערה כמו זו שב-`MIGRATION_3_4`. אחר כך מממשים את הפונקציות במאגרים.
- [ ] **Step 4:** מריצים שוב את הבדיקות, ואחר כך את הפקודה המלאה. Expected: PASS.
- [ ] **Step 5:** קומיט `feat(data): ברקודים למוצרים, מסד 5, ועדכון סליל גנרי` (כולל `schemas/.../5.json`).

---

### Task 4: סורק ML Kit וצילום

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`, `data/scan/LabelScanner.kt`, `presentation/scan/LabelCapture.kt`
- Test: `app/src/androidTest/java/com/amir/filamenttracker/data/scan/LabelScannerTest.kt`, `app/src/androidTest/assets/xzn_label.jpg`

**Interfaces:**
- Produces:
  - `data class ScanResult(val text: String, val barcodes: List<String>)`
  - `@Singleton class LabelScanner @Inject constructor(@ApplicationContext context: Context) { suspend fun scan(uri: Uri): ScanResult }`. הוא מריץ זיהוי טקסט וסריקת ברקוד על אותה `InputImage.fromFilePath`. את ה-`Task` של ML Kit עוטפים ב-`suspendCancellableCoroutine`, כדי לא להוסיף תלות. ב-`barcodes` נכנסים `rawValue` לא ריקים, בלי כפילויות.
  - `@Composable fun rememberLabelCapture(onResult: (Uri?) -> Unit): () -> Unit`. היא יוצרת קובץ ב-`cacheDir/scans/label.jpg`, מקבלת URI דרך `FileProvider` (authority `${applicationId}.fileprovider`), ומפעילה `ActivityResultContracts.TakePicture()`. בביטול היא קוראת ל-`onResult(null)`.

**החלטות:**
- **תלויות:** `com.google.mlkit:text-recognition:16.0.1` ו-`com.google.mlkit:barcode-scanning:17.3.0`, דרך הקטלוג (`mlkit-text`, `mlkit-barcode`). אם גרסה לא נפתרת, לוקחים את הגרסה היציבה האחרונה של אותו artifact ומציינים זאת בקומיט.
- **הרשאות:** לא מוסיפים `CAMERA` למניפסט. בלי ההצהרה, `TakePicture` לא דורש הרשאה.
- **בדיקת מכשיר:** מוסיפים `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, וגם `androidTestImplementation` ל-`androidx-test-ext-junit` ול-`androidx.test:runner`. התמונה היא הצילום של אמיר מהסשן (`C:\Users\1\AppData\Local\Temp\claude\3721b731eefd9ce9\1d4b42a3-8a6c-43b2-ac79-4c360ffa6002\images\1.jpg`), מוקטנת לצלע של 1600px לכל היותר.

- [ ] **Step 1: Write the failing instrumented test** `xzn photo yields label text`. מעתיקים את הנכס לקובץ ב-cache, קוראים ל-`scan(Uri.fromFile(it))`, ובודקים שהטקסט מכיל "XZN" ו-"Silk Gold". בנוסף, `readLabel(result.text, emptyList(), listOf("PLA","PETG"), emptyList())` מחזיר family "PLA", finish "Silk" ו-brand "XZN".
- [ ] **Step 2:** מפעילים את האמולטור ומריצים `./gradlew connectedDebugAndroidTest`. Expected: FAIL, כי `LabelScanner` לא קיים.
- [ ] **Step 3:** מממשים את התלויות, ה-provider, `file_paths.xml` (`<cache-path name="scans" path="scans/"/>`), `LabelScanner` ו-`rememberLabelCapture`.
- [ ] **Step 4:** מריצים `./gradlew connectedDebugAndroidTest`, ואחר כך את הפקודה המלאה. Expected: PASS. אם המבחן נכשל רק בגלל קריאה חלקית של הטקסט, מדפיסים את הטקסט המלא ומתקנים את כללי משימה 1 (בבדיקת יחידה חדשה), לא את הבדיקה.
- [ ] **Step 5:** קומיט `feat(scan): סורק ML Kit וצילום מדבקה`.

---

### Task 5: סריקה בחלון החריץ (AMS)

**Files:**
- Create: `presentation/scan/ScanCards.kt`, `presentation/scan/ScanUiState.kt`
- Modify: `presentation/ams/AmsViewModel.kt`, `presentation/ams/AmsScreen.kt`

**Interfaces:**
- Consumes: `LabelScanner.scan`, `rememberLabelCapture`, `readLabel`, `matchScan`, `CatalogRepository.productForBarcode/rememberBarcode`, `InventoryRepository.retagSpool/loadIntoSlot`. את רשימות היצרנים, החומרים, הצבעים והמוצרים לוקחים מ-`CatalogDao.allBrands/activeMaterials/activeColors/activeProducts`, דרך `CatalogRepository`. אם חסרה שם פונקציה שמעבירה את הנתונים הלאה, מוסיפים אותה.
- Produces (ומשמש גם במשימה 6):

```kotlin
sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Reading : ScanUiState
    data class Result(val match: ScanMatch, val barcode: String?, val productTitle: String? = null) : ScanUiState
}
@Composable fun FoundCard(match: ScanMatch.Found, actionLabel: String, subtitle: (SpoolRow) -> String, onPick: (SpoolRow) -> Unit, onDismiss: () -> Unit)
@Composable fun GenericSuggestionCard(match: ScanMatch.GenericSuggestion, slot: Int, onRetagAndLoad: (spoolId: Long, brand: String) -> Unit, onLoadOnly: (spoolId: Long) -> Unit, onDismiss: () -> Unit)
@Composable fun ScanMessage(text: String)
```

- **`AmsViewModel`:** מוסיפים `scan: StateFlow<ScanUiState>`, ואת הפעולות:
  - `onPhoto(uri: Uri?)`: עבור `null` המצב חוזר ל-`Idle`.
  - `confirmLoad(spoolId, slot)`: מבצעת `loadIntoSlot`. אחריה, אם `barcode != null`, נשמר `rememberBarcode(barcode, productId of spool)`.
  - `retagAndLoad(spoolId, brand, slot)`: מבצעת `retagSpool` ואחריה `loadIntoSlot`. אם יש ברקוד, הוא נשמר למוצר החדש.
  - `clearScan()`.

  `barcode` ב-`Result` הוא `scanResult.barcodes.singleOrNull()`. ה-`knownBrands` הם `allBrands` בלי הגנריים. ה-`knownFamilies` וה-`knownColors` נלקחים מהמועמדים ומ-`activeMaterials`/`activeColors`. כשהתוצאה היא `KnownProductNoSpool`, את `productTitle` לוקחים מ-`activeProducts`.
- **`SlotSheet`:** מוסיפים `OutlinedButton("סרוק מדבקה")` מעל `SectionHeader`. מתחתיו, לפי `scan`:
  - **`Reading`:** "קורא את המדבקה..." עם `CircularProgressIndicator` קטן.
  - **`Found`:** `FoundCard`, עם הכותרת "זוהה מהמדבקה", הכפתור "טען" ושורת משנה של מצב הסליל (`status.label()`). אם `brandConfirmed=false`, מופיעה בכרטיס השורה "זוהה לפי חומר וצבע, המותג במדבקה: {labelBrand}". אם `labelBrand == null`, הטקסט הוא "זוהה לפי חומר וצבע".
  - **`GenericSuggestion`:** `GenericSuggestionCard` עם הטקסטים מהמסמך: "המדבקה: {brand} {line}", "אין מוצר של {brand}, אבל יש סליל דומה:", ושורת הסליל. אחריהם `OutlinedTextField` עם התווית "יצרן", ממולא מראש ב-{brand}, והשאלה "לעדכן את הסליל הזה ל-{brand} ולטעון לחריץ {slot}?". הכפתורים: "עדכן וטען", "טען בלי לעדכן", "לא זה".
  - **`KnownProductNoSpool`:** "זוהה: {productTitle}, אבל אין סליל שלו זמין לטעינה".
  - **`None`:** "לא נמצאה התאמה".

  בזמן שמוצג כרטיס התאמה, הרשימה הרגילה מתחתיו מעומעמת (`alpha 0.45f`) ועדיין לחיצה. "לא זה" קורא ל-`clearScan`, ואז הרשימה חוזרת להיות רגילה. סגירת החלון קוראת ל-`clearScan`.

- [ ] **Step 1:** מממשים את `ScanUiState`, את `ScanCards.kt` ואת השינויים ב-VM וב-`SlotSheet`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug`. Expected: BUILD SUCCESSFUL, וכל הבדיקות עוברות.
- [ ] **Step 3:** מתקינים על האמולטור (`adb -s emulator-5554 install -r`). פותחים חריץ ובודקים שהכפתור "סרוק מדבקה" מופיע ושהרשימה הרגילה עובדת בלי לסרוק.
- [ ] **Step 4:** לוחצים "סרוק מדבקה" ומצלמים את הסצנה הווירטואלית של האמולטור. Expected: "לא נמצאה התאמה" והרשימה הרגילה לא מעומעמת.
- [ ] **Step 5:** מחברים זמנית, ב-debug ובלי לקמט, `onPhoto` לקובץ `xzn_label.jpg` שהועתק עם `adb push`, אחרי שיוצרים באמולטור סליל "גנרי PLA Silk זהב". Expected: מופיע `GenericSuggestionCard` עם XZN. "עדכן וטען" טוען את הסליל לחריץ, ובמלאי הוא מופיע כ-XZN. מסירים את החיבור הזמני.
- [ ] **Step 6:** לוחצים "סרוק מדבקה" ואז חוזרים אחורה ממסך המצלמה. Expected: החלון נשאר פתוח, בלי הודעה ובלי קריסה.
- [ ] **Step 7:** קומיט `feat(ams): סריקת מדבקה בחלון החריץ`.

---

### Task 6: סריקת משלוח במסך ההזמנות

**Files:**
- Modify: `presentation/orders/OrdersViewModel.kt`, `presentation/orders/OrdersScreen.kt`
- Test: `app/src/test/java/com/amir/filamenttracker/presentation/orders/ArrivalCandidatesTest.kt`

**Interfaces:**
- Consumes: Task 5 (`ScanUiState`, `FoundCard`, `ScanMessage`), `InventoryRepository.markArrived`, `CatalogRepository.rememberBarcode`.
- Produces: `fun arrivalCandidates(spools: List<SpoolRow>, orders: List<OrderSummary>): List<SpoolRow>`, ב-`OrdersViewModel.kt` ברמת הקובץ. היא מחזירה סלילים במצב `ORDERED` של הזמנות שלא בארכיון, ממוינים לפי `orderedAt` עולה, עם סליל אחד בלבד לכל `productId` (מההזמנה הישנה ביותר).

- [ ] **Step 1: Write the failing tests** `ArrivalCandidatesTest`:
  - `only ordered spools of active orders`: סליל SEALED, וסליל ORDERED בהזמנה בארכיון, לא נכללים.
  - `one spool per product from the oldest order`: שתי הזמנות עם אותו מוצר מחזירות רק את הסליל מההזמנה הישנה.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*ArrivalCandidatesTest*"`. Expected: FAIL.
- [ ] **Step 3:** מממשים את `arrivalCandidates`, ואת `scan`/`onPhoto`/`confirmArrived(spoolId)`/`clearScan` ב-VM, באותה צורה כמו ב-AMS. `confirmArrived` מבצעת `markArrived(listOf(spoolId))` ושומרת ברקוד אם יש. במסך מוסיפים `OutlinedButton("סרוק משלוח")` בראש הרשימה, והתוצאה נפתחת ב-`ModalBottomSheet`:
  - `FoundCard` עם הכפתור "הגיע". שורת המשנה היא "{vendor} {orderNumber}", מ-`OrderSummary`.
  - `KnownProductNoSpool` מציג "זוהה: {productTitle}, אבל אין סליל שלו במשלוח ממתין".
  - `None` מציג "לא נמצאה התאמה".
  - `GenericSuggestion` לא אמור לקרות כאן, כי לסלילים שהוזמנו יש יצרן. אם הוא בכל זאת קורה, מטפלים בו כמו ב-`None`.
- [ ] **Step 4:** מריצים את הפקודה המלאה. Expected: PASS. על האמולטור: "סרוק משלוח" עם תמונה של סצנה מחזיר "לא נמצאה התאמה". דרך החיבור הזמני של משימה 5, הזמנה של XZN PLA Silk זהב מסומנת "הגיע" אחרי אישור. מסירים את החיבור הזמני.
- [ ] **Step 5:** קומיט `feat(orders): סריקת משלוח לסימון הגעה`.

---

### Task 7: גרסה ומסמכים

**Files:**
- Modify: `app/build.gradle.kts`, `SPEC.md`, `current_focus.md`

- [ ] **Step 1:** `versionCode = 10`, `versionName = "0.6.0"`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug` ו-`./gradlew connectedDebugAndroidTest`. Expected: כל הבדיקות עוברות. רושמים את מספר הבדיקות.
- [ ] **Step 3:** ב-`SPEC.md` מוסיפים סעיף "סריקת מדבקות (0.6.0)": מה נבנה, מסד 5, והחלטות 6.6. ב"מה פתוח" מוסיפים שהזיהוי נבדק רק על מדבקת XZN, ושהבדיקה האמיתית היא במשלוח הבא. ב-`current_focus.md` מוסיפים בלוק סשן.
- [ ] **Step 4:** קומיט `chore: גרסה 0.6.0 ועדכון מסמכים`.
