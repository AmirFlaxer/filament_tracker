# תצוגות מלאי, מחיקת סליל ומשוב בהוספה - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** מחיקת כל סליל ממסך הסליל, כרטיס אישור ברור אחרי "הוסף" בלי אזהרה שנראית כמו שגיאה, וחמש תצוגות מלאי לבחירה שנשמרות בין הפעלות.

**Architecture:** כל הלוגיקה החדשה היא פונקציות טהורות שנבדקות ב-JVM: משפחת חומר ב-`domain`, קיבוץ חמש התצוגות ב-`presentation/inventory/InventoryViews.kt`, וטקסטים (אישור מחיקה, אישור הוספה) ליד ה-ViewModel שלהם. הריפוזיטורי מקבל `deleteSpool` במקום `deleteIfUntouched`. הבחירה בתצוגה נשמרת ב-DataStore Preferences דרך מחלקה קטנה ב-`data`.

**Tech Stack:** Kotlin 2.0.21, Compose (BOM 2024.12.01, Material 3), Hilt 2.54, Room 2.6.1, DataStore Preferences 1.1.1 (חדש), JUnit 4, Robolectric 4.14.1.

**Spec:** `docs/superpowers/specs/2026-10-18-inventory-views-delete-feedback-design.md`

## Global Constraints

- כל פקודת Gradle מ-Git Bash רצה אחרי `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- לא לשדרג גרסאות קיימות ב-`libs.versions.toml`. התלות החדשה היחידה: `androidx.datastore:datastore-preferences:1.1.1`.
- אין שינוי בסכמת Room (אין מיגרציה, אין עלייה ב-version של המסד).
- ממשק בעברית, מחרוזות ישירות בקוד. מקף רגיל בלבד, בלי חצים, בלי תווי כיווניות בלתי-נראים.
- כל התצוגות סופרות `IN_USE`, `OPEN_STORED`, `SEALED`, `ORDERED` כל אחד בנפרד, ומתעלמות מ-`FINISHED`. "סה"כ" = סכום הארבעה. "פתוח" = `OPEN_STORED` בלבד.
- התקנה רק לאמולטור: `adb -s emulator-5554 install -r ...`. לא לגעת בטלפון הפיזי (`3694e208`).
- כל קומיט מסתיים בשורה `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- עבודה בענף `inventory-views` (לא על `main`).

## Review Focus

1. **מחיקת סליל `IN_USE`**: החריץ חייב להתפנות מיד, כלומר אפשר לטעון אליו סליל אחר בלי `SlotOccupiedException`. נבדק במשימה 1.
2. **האזהרה על חריץ תפוס אחרי הוספה מוצלחת**: אחרי "הוסף" עם חריץ, החריץ מתאפס, ולכן האזהרה לא מתארת את הסליל שנוסף הרגע. נבדק במשימה 2 (ידני) ובסעיף האימות במשימה 6.
3. **שמות חומר עם רישיות ורווחים שונים** ("pla matte", "PLA  Matte", "PLA-CF"): נכנסים לאותה משפחה PLA ולא פותחים עמודה חדשה. נבדק במשימה 3.
4. **אותו צבע בשני סוגים מאותה משפחה** (PLA לבן ו-PLA Matte לבן): בטבלה לפי חומר הם תא אחד שסופר את שניהם ומכיל את שני החומרים לבחירה. נבדק במשימה 4.
5. **ערך שמור לא מוכר ב-DataStore** (למשל שם תצוגה שנמחק בגרסה עתידית): נופל ל"לפי חומר" ולא קורס. נבדק במשימה 5.

## File Map

```
app/src/main/java/com/amir/filamenttracker/
  domain/MaterialFamily.kt                              (חדש, משימה 3)
  domain/model/InventoryView.kt                         (חדש, משימה 4)
  data/InventoryRepository.kt                           (שינוי, משימה 1)
  data/InventoryViewPreference.kt                       (חדש, משימה 5)
  data/di/DatabaseModule.kt                             (שינוי, משימה 5)
  presentation/spool/SpoolDetailViewModel.kt, SpoolDetailScreen.kt   (שינוי, משימה 1)
  presentation/inventory/AddExistingViewModel.kt, AddExistingScreen.kt (שינוי, משימה 2)
  presentation/inventory/InventoryViews.kt              (חדש, משימה 4; מחליף את groupSpools)
  presentation/inventory/InventoryViewModel.kt          (שינוי, משימות 4 ו-6)
  presentation/inventory/InventoryScreen.kt             (שינוי, משימה 6)
  presentation/inventory/InventoryLists.kt, InventoryTables.kt (חדשים, משימה 6)
app/src/test/java/com/amir/filamenttracker/
  data/InventoryRepositoryTest.kt                       (שינוי, משימה 1)
  presentation/spool/DeleteConfirmTextTest.kt           (חדש, משימה 1)
  presentation/inventory/AddedTextTest.kt               (חדש, משימה 2)
  domain/MaterialFamilyTest.kt                          (חדש, משימה 3)
  presentation/inventory/InventoryGroupingTest.kt       (שכתוב, משימה 4)
  data/InventoryViewPreferenceTest.kt                   (חדש, משימה 5)
```

---

### Task 1: מחיקת כל סליל

**Files:**
- Modify: `data/InventoryRepository.kt` (להחליף את `deleteIfUntouched`)
- Modify: `presentation/spool/SpoolDetailViewModel.kt`, `presentation/spool/SpoolDetailScreen.kt`
- Test: `data/InventoryRepositoryTest.kt`, `presentation/spool/DeleteConfirmTextTest.kt`

**Interfaces:**
- Produces: `suspend fun InventoryRepository.deleteSpool(spoolId: Long)`; `fun deleteConfirmText(status: SpoolStatus, slot: Int?, orderId: Long?): String` (top-level ב-`SpoolDetailViewModel.kt`); `fun SpoolDetailViewModel.deleteSpool(onDone: () -> Unit)`.

- [ ] **Step 1: לשכתב את הבדיקות ב-`InventoryRepositoryTest`**

למחוק את שלוש הבדיקות של `deleteIfUntouched` (שורות 127-157 בערך) ולהוסיף:

```kotlin
@Test
fun `any spool can be deleted with its events, whatever happened to it`() = runTest {
    val id = repo.addExisting(black, 1, SEALED, slot = null).single()
    repo.loadIntoSlot(id, 1)
    repo.unload(id)
    repo.deleteSpool(id)
    assertNull(db.spoolDao().spool(id))
    assertTrue(events(id).isEmpty())
}

@Test
fun `deleting an in-use spool frees its slot`() = runTest {
    val first = repo.addExisting(black, 1, IN_USE, slot = 1).single()
    repo.deleteSpool(first)
    val second = repo.addExisting(white, 1, SEALED, slot = null).single()
    repo.loadIntoSlot(second, 1)
    assertEquals(IN_USE, spool(second).status)
    assertEquals(1, spool(second).amsSlot)
}

@Test
fun `an arrived spool from an order can be deleted and leaves the order`() = runTest {
    val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 2))
    repo.markOrderArrived(orderId)
    val id = repo.orderSpools(orderId).first().first().spoolId
    repo.deleteSpool(id)
    assertEquals(1, repo.orderSpools(orderId).first().size)
}

@Test
fun `a finished spool can be deleted`() = runTest {
    val id = repo.addExisting(black, 1, SEALED, slot = null).single()
    repo.loadIntoSlot(id, 1)
    repo.finish(id)
    repo.deleteSpool(id)
    assertNull(db.spoolDao().spool(id))
}
```

- [ ] **Step 2: בדיקה לטקסט האישור** ב-`presentation/spool/DeleteConfirmTextTest.kt` (JUnit רגיל, בלי Robolectric):

```kotlin
private const val BASE = "למחוק את הסליל? כל ההיסטוריה שלו תימחק, והוא לא ייספר בתצרוכת. אי אפשר לבטל את זה."

@Test fun `plain stored spool`() =
    assertEquals(BASE, deleteConfirmText(OPEN_STORED, slot = null, orderId = null))
@Test fun `in use adds the slot`() =
    assertEquals("$BASE חריץ 2 יתפנה.", deleteConfirmText(IN_USE, slot = 2, orderId = null))
@Test fun `from an order adds the order line`() =
    assertEquals("$BASE הוא יוסר גם מההזמנה.", deleteConfirmText(SEALED, slot = null, orderId = 7))
@Test fun `in use from an order has both, slot first`() =
    assertEquals("$BASE חריץ 1 יתפנה. הוא יוסר גם מההזמנה.", deleteConfirmText(IN_USE, slot = 1, orderId = 7))
```

- [ ] **Step 3: להריץ ולוודא כישלון**

Run: `./gradlew testDebugUnitTest --tests "*InventoryRepositoryTest*" --tests "*DeleteConfirmTextTest*"`
Expected: FAIL בקומפילציה (`deleteSpool`, `deleteConfirmText` לא קיימים).

- [ ] **Step 4: לממש**

- `InventoryRepository.deleteSpool(spoolId: Long)`: בטרנזקציה, `spoolDao.spool(spoolId) ?: throw SpoolRuleException("הסליל לא נמצא")`, ואז `spoolDao.deleteSpool(spoolId)` (האירועים נמחקים ב-cascade). למחוק את `deleteIfUntouched`.
- `deleteConfirmText` כמו בבדיקה: הבסיס, ואז " חריץ N יתפנה." אם `status == IN_USE && slot != null`, ואז " הוא יוסר גם מההזמנה." אם `orderId != null`.
- ב-`SpoolDetailState`: להחליף את `canDelete` ב-`val canDelete: Boolean get() = spool != null && spool.status != SpoolStatus.ORDERED` (במצב `ORDERED` נשאר רק "מחק, לא יגיע").
- ב-`SpoolDetailViewModel`: `deleteUntouched` הופך ל-`deleteSpool(onDone)` שקורא ל-`inventory.deleteSpool(spoolId)`.
- ב-`SpoolDetailScreen`: הדיאלוג משתמש ב-`deleteConfirmText(spool.status, spool.amsSlot, spool.orderId)`. ב-`onDone`: `Toast.makeText(context, "הסליל נמחק", Toast.LENGTH_SHORT).show()` (עם `LocalContext.current`) ואז `onBack()`.

- [ ] **Step 5: להריץ את כל הבדיקות**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, כל הבדיקות עוברות.

- [ ] **Step 6: Commit**

```bash
git add -A app/src
git commit -m "feat(spool): מחיקת כל סליל ממסך הסליל, עם אישור שמסביר מה יקרה"
```

---

### Task 2: כרטיס אישור אחרי הוספת סליל קיים

**Files:**
- Modify: `presentation/inventory/AddExistingViewModel.kt`, `presentation/inventory/AddExistingScreen.kt`
- Test: `presentation/inventory/AddedTextTest.kt`

**Interfaces:**
- Produces: `fun addedText(title: String, count: Int, status: SpoolStatus, slot: Int?): String` (top-level ב-`AddExistingViewModel.kt`); `val AddExistingViewModel.lastAdded: StateFlow<String?>`.

- [ ] **Step 1: בדיקה** ב-`AddedTextTest.kt`:

```kotlin
@Test fun `one in use names the slot`() =
    assertEquals("נוסף: גנרי PETG לבן, בחריץ 1", addedText("גנרי PETG לבן", 1, IN_USE, 1))
@Test fun `one sealed`() =
    assertEquals("נוסף: גנרי PLA שחור, סגור", addedText("גנרי PLA שחור", 1, SEALED, null))
@Test fun `one open stored`() =
    assertEquals("נוסף: גנרי PLA שחור, פתוח באחסון", addedText("גנרי PLA שחור", 1, OPEN_STORED, null))
@Test fun `several use the count label`() =
    assertEquals("נוספו 3: גנרי PLA שחור, 3 סגורים", addedText("גנרי PLA שחור", 3, SEALED, null))
```

- [ ] **Step 2: להריץ ולוודא כישלון**

Run: `./gradlew testDebugUnitTest --tests "*AddedTextTest*"`
Expected: FAIL בקומפילציה.

- [ ] **Step 3: לממש**

- `addedText`: לסליל אחד `"נוסף: $title, " + (if (status == IN_USE && slot != null) "בחריץ $slot" else status.label())`; לכמה `"נוספו $count: $title, ${countLabel(status, count)}"`.
- ב-ViewModel: `private val _lastAdded = MutableStateFlow<String?>(null)`, חשוף כ-`lastAdded`. ב-`add`, אחרי הצלחה: `_lastAdded.value = addedText(selected.title, ids.size, status, slot)` ולהחזיר `null` מ-`launchAction` (בלי snackbar להצלחה). צריך את ה-`ProductRow` הנבחר: `selected.value`. `select()` מאפס את `_lastAdded` ל-`null`.
- ב-`AddExistingScreen`:
  - בראש העמודה, אם `lastAdded != null`: `Card` עם `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)`, אייקון `Icons.Default.CheckCircle` וטקסט `lastAdded`.
  - ה-`onSuccess` של `vm.add` מאפס `quantity = 1` וגם `slot = null`.
  - האזהרה על חריץ תפוס: `Row` עם `Icons.Outlined.Info` וטקסט `"בחריץ $n נמצא $title. הוא יעבור לאחסון כשתוסיף."` בצבע `MaterialTheme.colorScheme.onSurfaceVariant` (לא `error`).

- [ ] **Step 4: להריץ את כל הבדיקות**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A app/src
git commit -m "feat(inventory): כרטיס אישור אחרי הוספה, איפוס חריץ, אזהרת חריץ ניטרלית"
```

---

### Task 3: משפחת חומר

**Files:**
- Create: `domain/MaterialFamily.kt`
- Test: `domain/MaterialFamilyTest.kt`

**Interfaces:**
- Produces: `data class MaterialKind(val family: String, val variant: String?)`; `fun materialKind(name: String): MaterialKind`; `fun familyKey(name: String): String` (= `materialKind(name).family.uppercase()`, למפתח השוואה).

- [ ] **Step 1: בדיקות**

```kotlin
@Test fun `plain name is its own family`() = assertEquals(MaterialKind("PLA", null), materialKind("PLA"))
@Test fun `space separates the variant`() = assertEquals(MaterialKind("PLA", "Matte"), materialKind("PLA Matte"))
@Test fun `hyphen separates the variant`() = assertEquals(MaterialKind("PETG", "CF"), materialKind("PETG-CF"))
@Test fun `extra spaces are trimmed`() = assertEquals(MaterialKind("PLA", "Silk"), materialKind("  PLA   Silk "))
@Test fun `plus has no separator`() = assertEquals(MaterialKind("PLA+", null), materialKind("PLA+"))
@Test fun `family key ignores case`() = assertEquals(familyKey("PLA"), familyKey("pla matte"))
```

- [ ] **Step 2: להריץ ולוודא כישלון**

Run: `./gradlew testDebugUnitTest --tests "*MaterialFamilyTest*"`
Expected: FAIL בקומפילציה.

- [ ] **Step 3: לממש** ב-`domain/MaterialFamily.kt`: `trim()`, פיצול במפריד הראשון `Regex("[\\s-]+")` למקסימום שני חלקים; חלק שני ריק נהיה `null`.

- [ ] **Step 4: להריץ**

Run: `./gradlew testDebugUnitTest --tests "*MaterialFamilyTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A app/src
git commit -m "feat(domain): משפחת חומר וסוג משם החומר"
```

---

### Task 4: קיבוץ חמש התצוגות

**Files:**
- Create: `domain/model/InventoryView.kt`, `presentation/inventory/InventoryViews.kt`
- Modify: `presentation/inventory/InventoryViewModel.kt` (להסיר את `groupSpools`, `ColorGroup`, `MaterialSection`)
- Test: `presentation/inventory/InventoryGroupingTest.kt` (שכתוב)

**Interfaces:**
- Consumes: `materialKind`, `familyKey` (משימה 3); `AlertRow`, `AlertKind.MISSING` (קיימים).
- Produces (ב-`InventoryViews.kt` אלא אם צוין אחרת):

```kotlin
// domain/model/InventoryView.kt
enum class InventoryView { BY_MATERIAL, BY_COLOR, BY_STATUS, TABLE_STATUS, TABLE_MATERIAL }

data class ListRow(
    val materialId: Long, val colorId: Long,
    val label: String,            // מה שמוצג כשם השורה, לפי התצוגה
    val colorHex: String?,
    val counts: Map<SpoolStatus, Int>,  // רק מצבים עם ספירה > 0
    val slots: List<Int> = emptyList(), // רק בתצוגה לפי מצב, תחת "בשימוש"
)
data class ListSection(val title: String, val colorHex: String?, val rows: List<ListRow>)

fun byMaterial(spools: List<SpoolRow>): List<ListSection>
fun byColor(spools: List<SpoolRow>): List<ListSection>
fun byStatus(spools: List<SpoolRow>): List<ListSection>

data class StatusTableRow(
    val materialId: Long, val colorId: Long, val label: String, val colorHex: String?,
    val inUse: Int, val open: Int, val sealed: Int, val ordered: Int,
) { val total: Int get() = inUse + open + sealed + ordered }
data class StatusTableSection(val family: String, val rows: List<StatusTableRow>)
fun statusTable(spools: List<SpoolRow>): List<StatusTableSection>

data class CellMaterial(val materialId: Long, val materialName: String)
data class MaterialCell(val counts: Map<SpoolStatus, Int>, val missing: Boolean, val materials: List<CellMaterial>) {
    val total: Int get() = counts.values.sum()
}
data class MaterialTableRow(val colorId: Long, val colorName: String, val colorHex: String?, val cells: List<MaterialCell?>)
data class MaterialTable(val families: List<String>, val rows: List<MaterialTableRow>)
fun materialTable(spools: List<SpoolRow>, alerts: List<AlertRow>): MaterialTable
```

כללים שהבדיקות קובעות:
- כל הפונקציות מסננות `FINISHED` ראשונות.
- `byMaterial`: סקשן לכל משפחה (`title` = כתיב המשפחה מהחומר הראשון בסדר אלפביתי של שם החומר, `colorHex = null`), ממוין לפי `familyKey`. שורה לכל `materialId`+`colorId`; `label` = שם הצבע, ועם סוג: `"$colorName · $variant"`. מיון שורות לפי `label`.
- `byColor`: סקשן לכל `colorId` (`title` = שם הצבע, `colorHex` = הגוון), ממוין לפי שם. שורה לכל חומר; `label` = שם החומר המלא.
- `byStatus`: סקשנים בסדר `IN_USE`, `OPEN_STORED`, `SEALED`, `ORDERED` עם הכותרות "בשימוש", "פתוחים באחסון", "סגורים", "בדרך"; סקשן ריק לא נכלל. שורה לכל חומר וצבע; `label` = `"$materialName $colorName"`; `counts` = רק המצב של הסקשן; `slots` = החריצים הממוינים (רק ב-`IN_USE`).
- `statusTable`: סקשן לכל משפחה כמו `byMaterial`, ושורה עם אותו `label`.
- `materialTable`: `families` = כל המשפחות שיש להן סליל או התראת `MISSING`, ממוינות לפי `familyKey`. שורה לכל צבע שיש לו סליל או התראת `MISSING`, ממוינת לפי שם. `cells[i]` מתאים ל-`families[i]`: `null` אם אין סלילים ואין `MISSING`; אחרת `MaterialCell` עם הספירה המצורפת של כל החומרים במשפחה, `missing = total == 0 && יש MISSING למשפחה ולצבע`, ו-`materials` = החומרים שתורמים לתא (או החומר עם ההתראה, אם התא ריק), ממוינים לפי שם.

- [ ] **Step 1: לשכתב את `InventoryGroupingTest`**

עזר: `row(material, materialId, color, colorId, status, slot = null)` כמו היום, עם `amsSlot = slot`. עזר `missing(material, materialId, color, colorId)` שבונה `AlertRow(kind = AlertKind.MISSING, ...)` (את שאר השדות למלא בערכים כלשהם). נתוני בסיס משותפים:

```kotlin
private val sample = listOf(
    row("PLA", 1, "לבן", 10, IN_USE, slot = 2),
    row("PLA", 1, "לבן", 10, SEALED),
    row("PLA Matte", 3, "לבן", 10, OPEN_STORED),
    row("PETG", 2, "לבן", 10, ORDERED),
    row("PETG", 2, "שחור", 11, SEALED),
    row("PETG", 2, "שחור", 11, FINISHED),
)
```

בדיקות:
- `by material groups variants under their family`: כותרות `["PETG", "PLA"]`; תחת PLA שתי שורות עם `label` `"לבן"` ו-`"לבן · Matte"`; הספירה של PLA לבן היא `{IN_USE=1, SEALED=1}`.
- `by color puts every material of a color together`: כותרות `["לבן", "שחור"]`; תחת לבן שלוש שורות, `label`-ים `["PETG", "PLA", "PLA Matte"]`.
- `by status orders sections and lists slots`: כותרות `["בשימוש", "פתוחים באחסון", "סגורים", "בדרך"]`; השורה תחת "בשימוש" היא `"PLA לבן"` עם `slots == [2]` ו-`counts == {IN_USE=1}`.
- `status table totals include on the way`: בשורת PETG לבן `ordered == 1` ו-`total == 1`; בשורת PLA לבן `inUse == 1`, `sealed == 1`, `total == 2`.
- `material table merges variants into one cell`: `families == ["PETG", "PLA"]`; בשורת לבן, תא PLA עם `total == 3` ו-`materials` של שני החומרים (ids 1 ו-3).
- `material table shows missing only for an empty cell`: עם `missing("PETG", 2, "כחול", 12)` מתווספת שורת כחול שבה תא PETG הוא `missing == true` ו-`total == 0`, ותא PLA הוא `null`. עם `missing("PLA Matte", 3, "לבן", 10)`, התא PLA לבן נשאר `missing == false` (יש בו סלילים).
- `every view counts the same spools`: על `sample`, סכום הספירות ב-`byMaterial`, `byColor`, `byStatus`, `statusTable` (סכום `total`) ו-`materialTable` (סכום `total` של התאים) הוא 5 בכולם.

- [ ] **Step 2: להריץ ולוודא כישלון**

Run: `./gradlew testDebugUnitTest --tests "*InventoryGroupingTest*"`
Expected: FAIL בקומפילציה.

- [ ] **Step 3: לממש את `InventoryViews.kt` ואת `InventoryView`.** ב-`InventoryViewModel` להסיר את `groupSpools`, `ColorGroup` ו-`MaterialSection`. כדי שהמסך ימשיך להתקמפל עד משימה 6, `InventoryUiState.sections` הופך ל-`List<ListSection>` שמחושב ב-`byMaterial(spools)`, ו-`InventoryScreen` מצייר `ListSection`/`ListRow` במקום `MaterialSection`/`ColorGroup` (שורה: `label`, `counts` דרך טקסט הספירה הקיים).

- [ ] **Step 4: להריץ את כל הבדיקות**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A app/src
git commit -m "feat(inventory): קיבוץ לחמש תצוגות, עם משפחות חומר"
```

---

### Task 5: שמירת התצוגה הנבחרת

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `data/di/DatabaseModule.kt`
- Create: `data/InventoryViewPreference.kt`
- Test: `data/InventoryViewPreferenceTest.kt`

**Interfaces:**
- Consumes: `InventoryView` (משימה 4).
- Produces: `class InventoryViewPreference @Inject constructor(private val store: DataStore<Preferences>)` עם `val view: Flow<InventoryView>` ו-`suspend fun set(view: InventoryView)`. ב-`DatabaseModule`: `@Provides @Singleton fun preferences(@ApplicationContext context: Context): DataStore<Preferences>` דרך `PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }`.

- [ ] **Step 1: תלות.** ב-`libs.versions.toml`: `datastore = "1.1.1"` ו-`androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }`. ב-`app/build.gradle.kts`: `implementation(libs.androidx.datastore.preferences)`.

- [ ] **Step 2: בדיקות** (Robolectric, `@Config(sdk = [34])`, DataStore על קובץ ב-`TemporaryFolder` עם `scope = TestScope(...)` או `backgroundScope` של `runTest`):
- `defaults to by material`: `view.first() == BY_MATERIAL` בקובץ ריק.
- `remembers the chosen view`: `set(TABLE_MATERIAL)` ואז `view.first() == TABLE_MATERIAL`.
- `unknown stored value falls back to by material`: לכתוב ישירות למפתח `"inventory_view"` את `"NO_SUCH_VIEW"`, ואז `view.first() == BY_MATERIAL`.

- [ ] **Step 3: להריץ ולוודא כישלון**

Run: `./gradlew testDebugUnitTest --tests "*InventoryViewPreferenceTest*"`
Expected: FAIL בקומפילציה.

- [ ] **Step 4: לממש.** מפתח `stringPreferencesKey("inventory_view")`; קריאה עם `InventoryView.entries.firstOrNull { it.name == stored } ?: BY_MATERIAL`.

- [ ] **Step 5: להריץ את כל הבדיקות**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A gradle app
git commit -m "feat(data): שמירת תצוגת המלאי הנבחרת ב-DataStore"
```

---

### Task 6: מסך המלאי עם בורר וחמש תצוגות

**Files:**
- Modify: `presentation/inventory/InventoryViewModel.kt`, `presentation/inventory/InventoryScreen.kt`
- Create: `presentation/inventory/InventoryLists.kt`, `presentation/inventory/InventoryTables.kt`

**Interfaces:**
- Consumes: כל מה שמשימות 4 ו-5 מייצרות; `onColorClick(materialId: Long, colorId: Long)` הקיים.
- Produces: `InventoryUiState(loaded: Boolean, alerts: List<AlertRow>, view: InventoryView?, spools: List<SpoolRow>)` (`view == null` עד שה-DataStore נקרא); `fun InventoryViewModel.select(view: InventoryView)`.

אין כאן לוגיקה חדשה שאפשר לבדוק ב-JVM. הקיבוץ נבדק במשימה 4, והבדיקה כאן ידנית.

- [ ] **Step 1: ViewModel.** `combine(inventory.spools, stock.alerts, preference.view)`. הקיבוץ עצמו נעשה במסך עם `remember(spools, alerts, view)`, או ב-ViewModel. שתי הדרכים בסדר, העיקר שלא יחושב בכל recomposition. `select` קורא ל-`preference.set` בתוך `viewModelScope.launch`.

- [ ] **Step 2: בורר.** מתחת ל-`AlertsCard`: `Row` עם `horizontalScroll(rememberScrollState())` ו-`FilterChip` לכל תצוגה, בסדר ובשמות: "לפי חומר", "לפי צבע", "לפי מצב", "טבלה לפי מצב", "טבלה לפי חומר". כל עוד `view == null` לא מוצגים הבורר והרשימה.

- [ ] **Step 3: שלוש תצוגות הרשימה** ב-`InventoryLists.kt`: `LazyListScope.listSections(sections: List<ListSection>, onRowClick: (ListRow) -> Unit)`. כותרת סקשן: `SectionHeader`, ועם `ColorDot` כש-`colorHex != null` (לפי צבע). שורה: `ListItem` עם `ColorDot` של השורה, `label`, ובתור `supportingContent` טקסט הספירה (`displayOrder` ו-`countLabel` הקיימים), ואחריו `" · חריץ 1, 3"` כש-`slots` לא ריק.

- [ ] **Step 4: טבלה לפי מצב** ב-`InventoryTables.kt`: שורת כותרת (שם, בשימוש, פתוח, סגור, בדרך, סה"כ), שורה מודגשת לכל משפחה, ושורה לכל `StatusTableRow` שלחיצה עליה פותחת את סלילי הצבע. עמודת השם `Modifier.weight(1f)`, עמודות המספרים ברוחב קבוע (בערך 44dp), ו-0 מוצג כ-"-" ב-`onSurfaceVariant` עם alpha נמוך.

- [ ] **Step 5: טבלה לפי חומר**: שורת כותרת עם המשפחות, ושורה לכל צבע (גוון ושם). תא: `null` מוצג "-"; תא עם `missing` מוצג "0" ומתחתיו "חסר" ב-`colorScheme.error`; אחרת הסה"כ מודגש ומתחתיו פירוט קטן (`labelSmall`). בלחיצה על תא: אם יש חומר אחד, פותחים את סלילי הצבע. אם יש כמה, נפתח `AlertDialog` עם רשימת `materialName` לבחירה. אם יש יותר משלוש משפחות, הטבלה עטופה ב-`horizontalScroll`.

- [ ] **Step 6: בנייה והתקנה לאמולטור**

Run: `./gradlew testDebugUnitTest assembleDebug && adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: BUILD SUCCESSFUL ו-`Success`.

- [ ] **Step 7: אימות ידני על האמולטור** (שמות חדשים בלטינית, כי ADB לא מקליד עברית):
1. להוסיף לקטלוג חומר `PLA Matte`, ולהוסיף סלילים כך שיהיו PLA לבן, PLA Matte לבן ו-PETG לבן.
2. לעבור על חמש התצוגות. ב"לפי חומר", PLA Matte נמצא תחת PLA. ב"טבלה לפי חומר", התא PLA לבן סופר את שניהם, ולחיצה עליו פותחת בחירת סוג.
3. לבחור "טבלה לפי מצב", לסגור את האפליקציה (`adb -s emulator-5554 shell am force-stop com.amir.filamenttracker`) ולפתוח שוב. התצוגה נשמרה ואין הבהוב.
4. התרחיש מהמסמך: להוסיף סליל לחריץ 1, ולוודא שהכרטיס הירוק מופיע ושהחריץ התאפס בלי אזהרה. להוסיף עוד סליל לחריץ 1, להיכנס לסליל הראשון (עכשיו פתוח באחסון), ללחוץ "מחק סליל" ולאשר. מוצג Toast והסליל נעלם.
5. מצב כהה ומצב אופקי בשתי הטבלאות: אין גלישה של כל המסך הצידה.

- [ ] **Step 8: Commit**

```bash
git add -A app/src
git commit -m "feat(inventory): בורר תצוגות, שלוש רשימות ושתי טבלאות"
```

---

### Task 8: בחירת מותג קיים בדיאלוג "מוצר חדש"

(בוצע לפני משימה 7, כדי שהתיעוד יכלול אותו. מסמך העיצוב, סעיף 7א.)

**Files:**
- Modify: `data/db/CatalogDao.kt`, `data/CatalogRepository.kt`, `presentation/common/ProductDialogs.kt`
- Modify (קריאות ל-`NewProductDialog`): `presentation/catalog/CatalogScreen.kt` + `CatalogViewModel.kt`, `presentation/inventory/AddExistingScreen.kt` + `AddExistingViewModel.kt`, `presentation/orders/NewOrderScreen.kt` + `NewOrderViewModel.kt`
- Test: `data/CatalogRepositoryTest.kt`

**Interfaces:**
- Produces: `@Query("SELECT DISTINCT brand FROM products") fun allBrands(): Flow<List<String>>` ב-`CatalogDao`; `val brands: Flow<List<String>>` ב-`CatalogRepository` (ייחודי בלי הבדל רישיות, כתיב ראשון נשמר, ממוין עם `String.CASE_INSENSITIVE_ORDER`); פרמטר חדש `brands: List<String>` ל-`NewProductDialog`, לפני `onDismiss`.

- [ ] **Step 1: בדיקה** ב-`CatalogRepositoryTest`:

```kotlin
@Test
fun `brands are distinct ignoring case, sorted, and include archived products`() = runTest {
    repo.createProduct("Sunlu", "PLA", "שחור", "", 1000, "")
    repo.createProduct("sunlu", "PLA", "לבן", "", 1000, "")
    val archived = repo.createProduct("Bambu", "PETG", "לבן", "", 1000, "")
    repo.createProduct("Elegoo", "PLA", "אדום", "", 1000, "")
    db.spoolDao().insertSpool(SpoolEntity(productId = archived, orderId = null, status = SpoolStatus.SEALED, amsSlot = null))
    assertEquals(RemoveResult.ARCHIVED, repo.removeProduct(archived))
    assertEquals(listOf("Bambu", "Elegoo", "Sunlu"), repo.brands.first())
}
```

- [ ] **Step 2: להריץ ולוודא כישלון**

Run: `./gradlew testDebugUnitTest --tests "*CatalogRepositoryTest*"`
Expected: FAIL בקומפילציה (`brands` לא קיים).

- [ ] **Step 3: לממש** את השאילתה ואת `brands` (`distinctBy { it.lowercase() }` ואז `sortedWith(String.CASE_INSENSITIVE_ORDER)`). ב-`NewProductDialog`: `FlowRow` של `FilterChip` מתחת לשדה "מותג", `selected = brand == b`, ולחיצה `brand = b`, בדיוק כמו החומרים. כל ViewModel שמציג את הדיאלוג חושף `val brands = catalog.brands.asState(emptyList())`, והמסך מעביר אותו.

- [ ] **Step 4: להריץ את כל הבדיקות**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A app/src
git commit -m "feat(catalog): בחירת מותג קיים בדיאלוג מוצר חדש"
```

---

### Task 7: תיעוד וגרסה

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 2`, `versionName = "0.2.0"`), `SPEC.md`, `README.md` (אם הוא מתאר את מסך המלאי), `current_focus.md`

- [ ] **Step 1:** ב-`SPEC.md`, תחת "מה עובד": חמש התצוגות, מחיקת כל סליל, כרטיס האישור. ב"מה פתוח": להוסיף שמסכי הוספה אחרים עדיין עם snackbar (מחוץ להיקף, לפי המסמך). להפנות למסמך העיצוב החדש.
- [ ] **Step 2:** בלוק חדש בראש `current_focus.md` עם מה נבנה, מספר הבדיקות ומה לסשן הבא.
- [ ] **Step 3:** `./gradlew testDebugUnitTest assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit**

```bash
git add -A app/build.gradle.kts SPEC.md README.md current_focus.md
git commit -m "docs: תצוגות מלאי, מחיקה ומשוב (0.2.0)"
```
